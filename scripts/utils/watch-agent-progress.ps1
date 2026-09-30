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
    flip, closure verdict, phase post, build-lock hold and abandon is one JSON file under
    temp/AGENT-CHAT/progress/, written the moment it happens by the scripts the child calls. This
    watcher tails that directory beside the runner and prints the kinds that read as progress, so the
    console gets a line per real event instead of a spinner or a flood.

    Ticket title. The runner's header names the ticket by id only, and that header is printed by the
    canon harness, which a project session does not edit. So the watcher prints the ticket's full
    title - the spec file's first heading - as a `>>` line right under that header: every live pass
    looks for the runner's `/spec-all .. Sxxxx` child among the descendants of -ParentPid, and the
    first chat record naming a ticket not yet announced is the fallback.

    Problem summary. The title alone is often just the ticket's slug reworded - every compact-bugfix
    ticket cut from an audit slice repeats its file name verbatim as its heading - and says nothing
    about what the ticket actually fixes. So under the `>>` banner the watcher prints the first of
    these that carries text: a filled "## 1. Проблема" section, the "**Текст:**" capture, an audit
    slice's "## 1. Цель". Markdown, list markers, audit severity codes and directory prefixes are
    dropped, an audit finding's "Slice NNN (Sxxxx)" lead-in gives way to the findings under it, and
    the result is capped at 220 characters, word-wrapped at 110. An unfilled template placeholder
    prints nothing, same as a ticket with no PLAN/ file.

    Names, not numbers. The banner reads `>> <title>  (Sxxxx)`, and every event line names the
    ticket by its title cut to a 34-character column instead of by id; the id repeated inside the
    note is dropped. A ticket with no spec file keeps its id.

    Build stages. A `lock` record is printed only for a Build.* domain, rendered as the stage it is -
    compile check, unit tests with their filter, debug or device build - and its release as the same
    stage with its duration. Code.* holds stay out: they fire several times per edit and say nothing
    about where the pipeline is. A closure verdict and a lock carry no ticket field, so the line is
    attributed to the id in the note, else to the ticket in hand.

    Scoped by instance, not by process tree. a.ps1 exports FMS_QUEUE_INSTANCE before launching the
    runner, every descendant inherits it, and agent-identity.ps1 stamps it into each chat record as
    `agent.instance` - so three parallel runners each print their own work and none prints a
    sibling's. Without -Instance every agent's events are shown, which is what a bare invocation in
    a spare window wants.

    Deliberately read-only and best-effort: it opens nothing but the chat files, the spec headings
    and the process table, holds no lock, writes nothing, and a parse failure on one record skips
    that record rather than ending the watch. Nothing may depend on its output - it describes, it
    decides nothing (Rule 34).

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

    # Seconds between passes. Short enough that the ticket title lands under the runner's header
    # before the child's first chat record does; a pass is a directory listing and one process query.
    [int] $IntervalSeconds = 5,

    # Which chat kinds count as progress. 'session' is excluded by default; 'lock' is read, and
    # printed for Build.* domains only.
    [string[]] $Kinds = @('status', 'verdict', 'phase', 'ticket', 'abandon', 'note', 'lock'),

    # Exit when this process is gone, and look for the runner's child among its descendants.
    # 0 = watch until killed, with no process scan.
    [int] $ParentPid = 0,

    # Also print what was already there, this many minutes back. 0 = only what happens from now on.
    [int] $Since = 0,

    # Never print more than this many lines in one pass; the rest is summarised as a count. A batch
    # closure can post a dozen records in one second and the console is a progress view, not a log.
    [int] $MaxLinesPerPass = 8,

    # Minutes of silence after which one line says the ticket is still in work and for how long. A
    # ticket can spend twenty minutes inside a single build with nothing to post. 0 = never.
    [int] $HeartbeatMinutes = 5,

    # One pass and exit, with no process scan - what the contract suite runs.
    [switch] $Once,

    # Repository root: the PLAN/ spec headings and the default chat directory.
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
    build   = 'DarkYellow'
}

# The event-line column that names the ticket, and the cap on the problem summary under the banner.
$LabelWidth = 34
$SummaryMaxChars = 220

$script:CurrentTicket = ''
$script:TicketStartedAt = $null
$script:TitleCache = @{}
$script:ProblemCache = @{}
$script:BuildHolds = @{}

function Get-TicketTitle {
    param([string] $Id)
    if ($script:TitleCache.ContainsKey($Id)) { return $script:TitleCache[$Id] }
    $title = ''
    try {
        $spec = Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'PLAN') -Filter "${Id}_*.md" -File -ErrorAction Stop |
            Select-Object -First 1
        if ($spec) {
            $head = [string](Get-Content -LiteralPath $spec.FullName -TotalCount 1 -ErrorAction Stop)
            if ($head -match "\b$Id\s*[-:]\s*(.+)$") { $title = $Matches[1].Trim() }
            if (-not $title) { $title = $spec.BaseName.Substring($Id.Length).TrimStart('_') }
            # A heading that is only the file slug reads better as words.
            if ($title -notmatch '\s') { $title = $title -replace '-', ' ' }
        }
    }
    catch { $title = '' }   # no PLAN/ or an unreadable spec: the banner still names the id
    $script:TitleCache[$Id] = $title
    return $title
}

function Get-SpecSectionBody {
    # The lines of one spec section: from the line after the one matching -StartPattern up to the next
    # rule, heading or bold field - whichever comes first.
    param([string[]] $Lines, [string] $StartPattern)
    $body = [System.Collections.Generic.List[string]]::new()
    $inside = $false
    foreach ($line in $Lines) {
        if (-not $inside) {
            if ($line -match $StartPattern) { $inside = $true }
            continue
        }
        $trimmed = $line.Trim()
        if ($trimmed -eq '---' -or $trimmed.StartsWith('#') -or $trimmed -match '^\*\*[^*]+:\*\*') { break }
        $body.Add($trimmed)
    }
    return , $body.ToArray()
}

function ConvertTo-PlainSummary {
    # Paragraphs of a spec section as one plain-text line: no markdown, no list markers, no audit
    # severity codes, paths cut to the file name - the console has room for the defect, not for noise.
    param([string[]] $Body)
    $paragraphs = [System.Collections.Generic.List[string]]::new()
    $current = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @($Body) + @('')) {
        if ($line) { $current.Add($line); continue }
        if ($current.Count -gt 0) { $paragraphs.Add(($current -join ' ')); $current.Clear() }
    }
    $kept = [System.Collections.Generic.List[string]]::new()
    $leadIns = [System.Collections.Generic.List[string]]::new()
    foreach ($para in $paragraphs) {
        $text = $para.Trim()
        # The template's own unfilled placeholder, left as-is by a draft nobody has triaged yet.
        if ($text -match '^<.*>$' -or $text -eq 'нет текста' -or $text -eq '{{TEXT}}') { continue }
        $text = $text -replace '^(\d+\.|[-*])\s+', ''
        $text = $text -replace '^P\d\s+(L\d+\s+)?[-·]\s+', ''
        $text = $text -replace '`', ''
        $text = $text -replace '(?:[\w.-]+/)+([\w.-]+)', '$1'
        $text = ($text -replace '\s+', ' ').Trim()
        if (-not $text) { continue }
        # An audit-slice finding opens with "Slice NNN (Sxxxx) ..." - where it came from, not what is
        # wrong. The findings under it say what is wrong, so the lead-in is used only when alone.
        if ($text -match '^Slice \d+ \(S\d{4,}\)') { $leadIns.Add($text); continue }
        $kept.Add($text)
    }
    if ($kept.Count -eq 0) { $kept = $leadIns }
    return ($kept -join ' | ')
}

function Get-TicketProblemSummary {
    param([string] $Id)
    if ($script:ProblemCache.ContainsKey($Id)) { return $script:ProblemCache[$Id] }
    $summary = ''
    try {
        $spec = Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'PLAN') -Filter "${Id}_*.md" -File -ErrorAction Stop |
            Select-Object -First 1
        if ($spec) {
            $lines = @(Get-Content -LiteralPath $spec.FullName -TotalCount 200 -ErrorAction Stop)
            # Most informative first: a filled problem section is the triaged statement; the reporter's
            # own capture is next; an audit slice has neither and states its goal instead.
            foreach ($pattern in @('^##\s*1\.\s*Проблема', '^\*\*Текст:\*\*\s*$', '^##\s*1\.\s*Цель')) {
                $summary = ConvertTo-PlainSummary -Body (Get-SpecSectionBody -Lines $lines -StartPattern $pattern)
                if ($summary) { break }
            }
        }
    }
    catch { $summary = '' }   # no PLAN/ or an unreadable spec: the banner still stands on its own
    if ($summary.Length -gt $SummaryMaxChars) { $summary = $summary.Substring(0, $SummaryMaxChars - 3) + '..' }
    $script:ProblemCache[$Id] = $summary
    return $summary
}

function Get-TicketLabel {
    # What an event line calls a ticket: its name, cut to the column, else the id when it has no spec.
    param([string] $Id)
    if (-not $Id) { return '-' }
    $title = Get-TicketTitle -Id $Id
    if (-not $title) { return $Id }
    if ($title.Length -gt $LabelWidth) { return $title.Substring(0, $LabelWidth - 2).TrimEnd() + '..' }
    return $title
}

function Split-ForConsole {
    # Word-wrapped lines of at most -Width characters, so a long summary does not run off the window.
    param([string] $Text, [int] $Width)
    $out = [System.Collections.Generic.List[string]]::new()
    $line = ''
    foreach ($word in ($Text -split ' ')) {
        if ($line -and ($line.Length + 1 + $word.Length) -gt $Width) { $out.Add($line); $line = $word }
        elseif ($line) { $line = "$line $word" }
        else { $line = $word }
    }
    if ($line) { $out.Add($line) }
    return , $out.ToArray()
}

function Set-TicketFocus {
    param([string] $Id)
    if (-not $Id -or $Id -eq $script:CurrentTicket) { return }
    $script:CurrentTicket = $Id
    $script:TicketStartedAt = Get-Date
    $title = Get-TicketTitle -Id $Id
    $named = if ($title) { "$title  ($Id)" } else { $Id }
    Write-Host ("  [{0}] {1}  >> {2}" -f $label, (Get-Date).ToString('HH:mm:ss'), $named) -ForegroundColor White
    $problem = Get-TicketProblemSummary -Id $Id
    if ($problem) {
        $indent = ' ' * ($label.Length + 15)
        foreach ($chunk in (Split-ForConsole -Text $problem -Width 110)) {
            Write-Host ("  {0}{1}" -f $indent, $chunk) -ForegroundColor DarkGray
        }
    }
}

function Find-RunnerChildTicket {
    # Only the live watch scans: -Once is the contract suite, and a bare watch has no runner of its own.
    if ($Once -or $ParentPid -le 0) { return $null }
    try {
        $candidates = @(Get-CimInstance Win32_Process -Filter "CommandLine LIKE '%/spec-all%'" -ErrorAction Stop)
        if ($candidates.Count -eq 0) { return $null }
        $parentOf = @{}
        foreach ($p in @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId -ErrorAction Stop)) {
            $parentOf[[int]$p.ProcessId] = [int]$p.ParentProcessId
        }
        foreach ($c in @($candidates | Sort-Object CreationDate -Descending)) {
            # The runner's own command line carries the '{id}' template, never an id, so it never matches.
            if ([string]$c.CommandLine -notmatch '/spec-all\b.*?\b(S\d{4,})\b') { continue }
            $id = $Matches[1]
            $walk = [int]$c.ProcessId
            for ($depth = 0; $depth -lt 10 -and $parentOf.ContainsKey($walk); $depth++) {
                $walk = $parentOf[$walk]
                if ($walk -eq $ParentPid) { return $id }
            }
        }
    }
    catch { return $null }   # the process table only speeds the banner up; the chat records still name the ticket
    return $null
}

function Format-BuildStage {
    param([string] $Reason)
    if ($Reason -match '-Mode Unit\b.*?filtered:\s*([^)]+)\)') {
        $names = @($Matches[1] -split ',' | ForEach-Object { ($_.Trim() -split '\.')[-1].Trim('*') } | Where-Object { $_ })
        return 'unit tests: ' + ($names -join ', ')
    }
    if ($Reason -match '-Mode Unit\b') { return 'unit tests, whole suite' }
    if ($Reason -match '-Mode Code\b') { return 'compile check' }
    if ($Reason -match '-Mode Resources\b') { return 'resource check' }
    if ($Reason -match '-Mode (\w+)') { return "fast check ($($Matches[1]))" }
    if ($Reason -match 'build-standard-device') { return 'device build' }
    if ($Reason -match 'build-debug') { return 'debug build' }
    if ($Reason -match 'detekt') { return 'detekt' }
    return (($Reason -split '\s+')[0])
}

function Format-Duration {
    param([timespan] $Span)
    if ($Span.TotalSeconds -lt 60) { return ('{0:N0} s' -f [Math]::Max(0, $Span.TotalSeconds)) }
    return ('{0}m{1:D2}s' -f [int][Math]::Floor($Span.TotalMinutes), $Span.Seconds)
}

function Write-EventLine {
    param([datetime] $At, [string] $Ticket, [string] $Kind, [string] $Note, [string] $Who)
    # The column already names the ticket, so its id repeated in the note ("S1234 - S1234: ..",
    # "claimed S1234: ..", "released S1234") is noise.
    if ($Ticket) {
        $escaped = [regex]::Escape($Ticket)
        $Note = $Note -replace ("\b{0} - {0}:\s*" -f $escaped), ''
        $Note = ($Note -replace ("\b{0}:?\s*" -f $escaped), '').Trim()
    }
    # The note carries a whole closure description; the console wants the head of it, not the file set.
    if ($Note.Length -gt 110) { $Note = $Note.Substring(0, 107) + '..' }
    $color = if ($kindColors.ContainsKey($Kind)) { $kindColors[$Kind] } else { 'Gray' }
    $shownTicket = (Get-TicketLabel -Id $Ticket).PadRight($LabelWidth)
    Write-Host ("  [{0}] {1}  {2} {3,-8} {4}" -f $label, $At.ToString('HH:mm:ss'), $shownTicket, $Kind, $Note) -ForegroundColor $color
    if ($Instance -eq '' -and $Who -and $Who -ne '?') {
        Write-Host ("           by {0}" -f $Who) -ForegroundColor DarkGray
    }
}

function Write-RecordLine {
    # Returns whether a line was printed: a Code.* hold or an unmatched release prints nothing.
    param([object] $Record)

    $kind = [string]$Record.kind
    $at = Get-Date
    if ($Record.at) { try { $at = [datetime]::Parse([string]$Record.at).ToLocalTime() } catch { $at = Get-Date } }
    $note = ([string]$Record.note) -replace '\s+', ' '
    $who = if ($Record.agent -and $Record.agent.name) { [string]$Record.agent.name } else { '?' }

    $ticket = [string]$Record.ticket
    # Only the record's own field moves the focus: a note may name a parked or related ticket too.
    if ($Instance) { Set-TicketFocus -Id $ticket }
    if (-not $ticket -and $note -match '\b(S\d{4,})\b') { $ticket = $Matches[1] }
    if (-not $ticket -and $Instance) { $ticket = $script:CurrentTicket }

    if ($kind -ne 'lock') {
        Write-EventLine -At $at -Ticket $ticket -Kind $kind -Note $note -Who $who
        return $true
    }
    if ($note -match '^acquired (Build\.\w+):\s*(.*)$') {
        $domain = $Matches[1]
        $stage = Format-BuildStage -Reason $Matches[2]
        if ($domain -ne 'Build.Phone') { $stage = "$stage ($domain)" }
        $script:BuildHolds[$domain] = @{ At = $at; Stage = $stage }
        Write-EventLine -At $at -Ticket $ticket -Kind 'build' -Note ("{0} .." -f $stage) -Who $who
        return $true
    }
    if ($note -notmatch '^released (Build\.\w+)') { return $false }
    $domain = $Matches[1]
    $hold = $script:BuildHolds[$domain]
    if (-not $hold) { return $false }
    $script:BuildHolds.Remove($domain)
    $doneNote = "{0} - done, {1}" -f $hold.Stage, (Format-Duration ($at - $hold.At))
    Write-EventLine -At $at -Ticket $ticket -Kind 'build' -Note $doneNote -Who $who
    return $true
}

$script:LastEventAt = Get-Date
$seen = [System.Collections.Generic.HashSet[string]]::new()
$cutoff = if ($Since -gt 0) { (Get-Date).ToUniversalTime().AddMinutes(-$Since) } else { (Get-Date).ToUniversalTime() }

# Everything already on disk older than the cutoff is pre-seeded as seen, so the first pass prints
# the window the caller asked for and never the whole 250-file backlog.
foreach ($f in @(Get-ChildItem -LiteralPath $progressDir -Filter '*.json' -File -ErrorAction SilentlyContinue)) {
    if ($f.LastWriteTimeUtc -lt $cutoff) { [void]$seen.Add($f.Name) }
}

function Invoke-Pass {
    if ($Instance) { Set-TicketFocus -Id (Find-RunnerChildTicket) }

    $fresh = @(Get-ChildItem -LiteralPath $progressDir -Filter '*.json' -File -ErrorAction SilentlyContinue |
        Where-Object { -not $seen.Contains($_.Name) } | Sort-Object Name)
    if ($fresh.Count -eq 0) { return }

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

    $printed = 0
    $skipped = 0
    foreach ($record in $matched) {
        if ($printed -ge $MaxLinesPerPass) { $skipped++; continue }
        if (Write-RecordLine -Record $record) { $printed++ }
    }
    if ($printed -gt 0) { $script:LastEventAt = Get-Date }
    if ($skipped -gt 0) {
        Write-Host ("  [{0}] .. and {1} more event(s) this pass" -f $label, $skipped) -ForegroundColor DarkGray
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
        $onTicket = if ($script:CurrentTicket -and $script:TicketStartedAt) {
            '{0} still in work, {1:N0} min on it' -f (Get-TicketLabel -Id $script:CurrentTicket), ((Get-Date) - $script:TicketStartedAt).TotalMinutes
        } else { 'still working' }
        Write-Host ("  [{0}] {1}  .. {2}, nothing new for {3:N0} min" -f `
                $label, (Get-Date).ToString('HH:mm:ss'), $onTicket, ((Get-Date) - $script:LastEventAt).TotalMinutes) -ForegroundColor DarkGray
        $script:LastEventAt = Get-Date
    }
}

exit 0
