#requires -Version 7.0
<#
.SYNOPSIS
    Prints a queue runner's progress to its own console, live, from the agent chat progress stream.

.DESCRIPTION
    An unattended runner console (`.\a.ps1 r0`..`r3`) used to stay silent for the whole length of a
    ticket: the child is `claude -p`, which emits its one final line when the run ENDS, and
    .claude/runner/silent-mode.md forbids the child any narration before that. So a 30-60 minute
    pipeline looked identical to a hang.

    The phases already announce themselves - to the agent chat, not to the console. Every status
    flip, closure verdict, phase post and abandon is one JSON file under temp/AGENT-CHAT/progress/,
    written the moment it happens by the scripts the child calls. This watcher tails that directory
    beside the runner and prints the few kinds that read as progress, so the console gets a line per
    real event instead of a spinner or a flood.

    Scoped by instance, not by process tree. a.ps1 exports FMS_QUEUE_INSTANCE before launching the
    runner, every descendant inherits it, and agent-identity.ps1 stamps it into each chat record as
    `agent.instance` - so three parallel runners each print their own work and none prints a
    sibling's. Without -Instance every agent's events are shown, which is what a bare invocation in
    a spare window wants.

    Deliberately read-only and best-effort: it opens nothing but the chat files, holds no lock,
    writes nothing, and a parse failure on one record skips that record rather than ending the
    watch. Nothing may depend on its output - it describes, it decides nothing (Rule 34).

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/watch-agent-progress.ps1 -Instance a -ParentPid $PID

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/watch-agent-progress.ps1 -Once -Since 120
    The last two hours of progress in one pass, no watching.

Exit codes: 0 = watch ended normally (parent gone, -Once pass finished, or Ctrl+C),
            2 = the agent chat progress directory cannot be located.
#>
[CmdletBinding()]
param(
    # Show only agents carrying this FMS_QUEUE_INSTANCE stamp ('a', 'b', 'c', 'mono'). Empty = all.
    [string] $Instance = '',

    # Seconds between passes. The stream is files on disk, so a short interval costs a directory
    # listing and nothing else; 15 s keeps a console feeling live without turning it into a log.
    [int] $IntervalSeconds = 15,

    # Which chat kinds count as progress. 'lock' and 'session' are excluded by default: they fire
    # several times per step and say nothing about where the pipeline is.
    [string[]] $Kinds = @('status', 'verdict', 'phase', 'ticket', 'abandon', 'note'),

    # Exit when this process is gone. 0 = watch until killed.
    [int] $ParentPid = 0,

    # Also print what was already there, this many minutes back. 0 = only what happens from now on.
    [int] $Since = 0,

    # Never print more than this many lines in one pass; the rest is summarised as a count. A batch
    # closure can post a dozen records in one second and the console is a progress view, not a log.
    [int] $MaxLinesPerPass = 8,

    # Minutes of silence after which one line says the watch is still up. A ticket can spend twenty
    # minutes inside a single build with nothing to post, and a console that says nothing at all for
    # that long is indistinguishable from a dead one. 0 = never.
    [int] $HeartbeatMinutes = 10,

    # One pass and exit - what the contract suite runs.
    [switch] $Once,

    [string] $RepoRoot = '',

    [switch] $Help
)

$ErrorActionPreference = 'Stop'

if ($Help) { Get-Help $PSCommandPath -Detailed; exit 0 }

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}

$progressDir = if ($env:FMS_AGENT_CHAT_ROOT) {
    Join-Path $env:FMS_AGENT_CHAT_ROOT 'progress'
} else {
    Join-Path $RepoRoot 'temp/AGENT-CHAT/progress'
}

if (-not (Test-Path -LiteralPath $progressDir)) {
    # Created by the first chat post of the session; a runner started on a clean tree beats it there.
    try { New-Item -ItemType Directory -Path $progressDir -Force -ErrorAction Stop | Out-Null }
    catch {
        Write-Host "watch-agent-progress: no agent chat progress directory - $progressDir" -ForegroundColor Red
        exit 2
    }
}

$label = if ($Instance) { $Instance.ToUpperInvariant() } else { 'ALL' }
$kindColors = @{
    status  = 'Cyan'
    verdict = 'Green'
    phase   = 'Yellow'
    ticket  = 'DarkCyan'
    abandon = 'Red'
    note    = 'Gray'
}

function Write-ProgressLine {
    param([object] $Record, [string] $FallbackKind)

    $kind = if ($Record.kind) { [string]$Record.kind } else { $FallbackKind }
    $at = Get-Date
    if ($Record.at) { try { $at = [datetime]::Parse([string]$Record.at).ToLocalTime() } catch { $at = Get-Date } }
    $ticket = if ($Record.ticket) { [string]$Record.ticket } else { '-' }
    $note = ([string]$Record.note) -replace '\s+', ' '
    # The note carries a whole closure description; the console wants the head of it, not the file set.
    if ($note.Length -gt 110) { $note = $note.Substring(0, 107) + '..' }
    $who = if ($Record.agent -and $Record.agent.name) { [string]$Record.agent.name } else { '?' }
    $color = if ($kindColors.ContainsKey($kind)) { $kindColors[$kind] } else { 'Gray' }

    Write-Host ("  [{0}] {1}  {2,-6} {3,-8} {4}" -f `
            $label, $at.ToString('HH:mm:ss'), $ticket, $kind, $note) -ForegroundColor $color
    if ($Instance -eq '' -and $who -ne '?') {
        Write-Host ("           by {0}" -f $who) -ForegroundColor DarkGray
    }
}

$script:LastEventAt = Get-Date
$script:StartedAt = Get-Date
$seen = [System.Collections.Generic.HashSet[string]]::new()
$cutoff = if ($Since -gt 0) { (Get-Date).ToUniversalTime().AddMinutes(-$Since) } else { (Get-Date).ToUniversalTime() }

# Everything already on disk older than the cutoff is pre-seeded as seen, so the first pass prints
# the window the caller asked for and never the whole 250-file backlog.
foreach ($f in @(Get-ChildItem -LiteralPath $progressDir -Filter '*.json' -File -ErrorAction SilentlyContinue)) {
    if ($f.LastWriteTimeUtc -lt $cutoff) { [void]$seen.Add($f.Name) }
}

function Invoke-Pass {
    $fresh = @(Get-ChildItem -LiteralPath $progressDir -Filter '*.json' -File -ErrorAction SilentlyContinue |
        Where-Object { -not $seen.Contains($_.Name) } | Sort-Object Name)
    if ($fresh.Count -eq 0) { return }

    $printed = 0
    $matched = @()
    foreach ($f in $fresh) {
        [void]$seen.Add($f.Name)
        $record = $null
        try { $record = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop }
        catch { continue }   # half-written or malformed: the next pass will not retry it, and one lost progress line is not worth a retry queue
        if (-not $record) { continue }

        $kind = [string]$record.kind
        if ($Kinds -notcontains $kind) { continue }
        if ($Instance) {
            $recInstance = if ($record.agent) { [string]$record.agent.instance } else { '' }
            if ($recInstance -ne $Instance) { continue }
        }
        $matched += , $record
    }

    foreach ($record in $matched) {
        if ($printed -ge $MaxLinesPerPass) { break }
        Write-ProgressLine -Record $record
        $printed++
    }
    if ($matched.Count -gt 0) { $script:LastEventAt = Get-Date }
    if ($matched.Count -gt $printed) {
        Write-Host ("  [{0}] .. and {1} more event(s) this pass" -f $label, ($matched.Count - $printed)) -ForegroundColor DarkGray
    }
}

Invoke-Pass
if ($Once) { exit 0 }

Write-Host ("  [{0}] progress watch on - {1} (kinds: {2})" -f `
        $label, $progressDir, ($Kinds -join ', ')) -ForegroundColor DarkGray

while ($true) {
    if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { break }
    Start-Sleep -Seconds ([Math]::Max(2, $IntervalSeconds))
    Invoke-Pass

    if ($HeartbeatMinutes -gt 0 -and ((Get-Date) - $script:LastEventAt).TotalMinutes -ge $HeartbeatMinutes) {
        Write-Host ("  [{0}] {1}  .. still working, {2:N0} min in, nothing posted for {3:N0} min" -f `
                $label, (Get-Date).ToString('HH:mm:ss'), ((Get-Date) - $script:StartedAt).TotalMinutes, `
            ((Get-Date) - $script:LastEventAt).TotalMinutes) -ForegroundColor DarkGray
        $script:LastEventAt = Get-Date
    }
}

exit 0
