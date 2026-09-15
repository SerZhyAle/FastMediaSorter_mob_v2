#requires -Version 7.0
<#
.SYNOPSIS
    The one start step of a MONO run (S3158): read the agent chat once, drop every leftover lease,
    lock and queue, post one journal note.

.DESCRIPTION
    MONO (`/spec-all -m`, `/spec-code -m`, `.\a.ps1 r0`) is the owner's declaration that exactly one
    agent works on the project. The run therefore makes none of the coordination calls that exist
    only to order it against siblings - no ticket lease, no code or build domain, no device lease, no
    chat line during the work - and never checks or waits for anyone.

    Two things still need doing once, and this script is where they happen:

    - The history is read at the start and never again, because nobody else writes to the chat while
      the run lasts.
    - Leftover state is dropped unconditionally. The canon harness is consumed rather than authored in
      this repository (CLAUDE.md section 8), so a gradle wrapper still takes its build domain through
      Enter-BuildLockOrExit and a closure still releases through its backstop. Uncontended that costs
      milliseconds; behind a lock left by a dead session it is a wait, which is exactly what the mode
      rules out. The mode declares that no holder is live, so nothing here judges liveness - that
      judgement is the check MONO removes.

    Not a marker and not a refusal of other sessions: nothing is written that a sibling would have to
    respect, because the declaration is that there is no sibling.

.PARAMETER Verb
    Start - read the history, drop leftovers, post the note.

.PARAMETER Ticket
    The ticket the run is on, carried into the journal note. Empty for a runner start.

.PARAMETER Note
    The journal note text. Default 'MONO start'.

.PARAMETER Stores
    Which leftovers to drop: Leases, Locks, or both (the default). The contract suite passes Leases
    alone because the harness offers a fixture root for the lease store and none for the lock files.

.PARAMETER DryRun
    Print what is held and would be dropped; drop nothing and post nothing.

.PARAMETER Last
    How many chat lines the history shows. Default 20.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/mono-mode.ps1 -Verb Start -Ticket S3158

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/mono-mode.ps1 -Verb Start -DryRun

Exit codes (CLAUDE.md Rule 7):
  0  started - leftovers dropped (or listed under -DryRun).
  2  a store could not be read or cleared - the forwarder named on the line before failed.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Start')]
    [string] $Verb,

    [string] $Ticket = '',

    [string] $Note = 'MONO start',

    [ValidateSet('Leases', 'Locks')]
    [string[]] $Stores = @('Leases', 'Locks'),

    [switch] $DryRun,

    [ValidateRange(1, 400)]
    [int] $Last = 20
)

$ErrorActionPreference = 'Stop'

$chatScript = Join-Path $PSScriptRoot 'agent-chat.ps1'
$lockStatusScript = Join-Path $PSScriptRoot 'lock-status.ps1'
$clearLockScript = Join-Path $PSScriptRoot 'clear-agent-lock.ps1'
$leaseScript = Join-Path (Split-Path -Parent $PSScriptRoot) 'spec_catalog\ticket-lease.ps1'

$pwshExe = (Get-Process -Id $PID).Path

# Every store is reached through its forwarder in a child process: the forwarders end in `exit`, and
# a dot-sourced or &-invoked one would end this script with it.
function Invoke-Forwarder {
    param([string] $Path, [string[]] $Arguments, [switch] $Capture)

    if ($Capture) {
        $output = & $pwshExe -NoProfile -File $Path @Arguments 2>$null
        return [pscustomobject]@{ Code = $LASTEXITCODE; Output = ($output | Out-String).Trim() }
    }
    # Out-Host, not the pipeline: a child's lines left on the pipeline become this function's return
    # value, so the history vanished into the result object and .Code read an array.
    & $pwshExe -NoProfile -File $Path @Arguments | Out-Host
    return [pscustomobject]@{ Code = $LASTEXITCODE; Output = '' }
}

$failed = $false

Write-Host '--- MONO: agent chat history (read once) ---' -ForegroundColor Cyan
$history = Invoke-Forwarder -Path $chatScript -Arguments @('-Verb', 'Read', '-Last', "$Last")
if ($history.Code -ne 0) {
    Write-Host "mono-mode: chat history unreadable (exit $($history.Code)) - continuing without it." -ForegroundColor DarkYellow
}

Write-Host '--- MONO: leftover coordination state ---' -ForegroundColor Cyan
if ($Stores -contains 'Leases') {
    $leases = Invoke-Forwarder -Path $leaseScript -Arguments @('-Verb', 'List', '-Json') -Capture
    if ($leases.Code -ne 0) {
        Write-Host "mono-mode: ticket-lease.ps1 -Verb List failed (exit $($leases.Code))." -ForegroundColor Red
        $failed = $true
    }
    else {
        $ids = @()
        if ($leases.Output) { $ids = @($leases.Output | ConvertFrom-Json) }
        $leaseLine = if ($ids.Count -gt 0) { $ids -join ', ' } else { 'none' }
        Write-Host "  leases: $leaseLine"
    }
}
if ($Stores -contains 'Locks') {
    foreach ($type in 'Build', 'Code') {
        $status = Invoke-Forwarder -Path $lockStatusScript -Arguments @('-Name', $type, '-Json') -Capture
        if ($status.Code -ne 0 -or -not $status.Output) {
            Write-Host "mono-mode: lock-status.ps1 -Name $type failed (exit $($status.Code))." -ForegroundColor Red
            $failed = $true
            continue
        }
        $held = @(($status.Output | ConvertFrom-Json).sections | Where-Object { $_.held })
        $lockLine = if ($held.Count -gt 0) {
            ($held | ForEach-Object { "$($_.Name) ($($_.Reason))" }) -join ', '
        }
        else { 'none held' }
        Write-Host "  $type locks: $lockLine"
    }
}

if ($DryRun) {
    Write-Host 'mono-mode: -DryRun - nothing dropped, nothing posted.' -ForegroundColor Yellow
    exit $(if ($failed) { 2 } else { 0 })
}

# -Force on every store: the mode declares that no holder is live, so the liveness judgement the
# unforced verbs make is the check MONO exists to remove.
if ($Stores -contains 'Leases') {
    $clean = Invoke-Forwarder -Path $leaseScript -Arguments @('-Verb', 'Clean', '-Force')
    if ($clean.Code -ne 0) {
        Write-Host "mono-mode: ticket-lease.ps1 -Verb Clean -Force failed (exit $($clean.Code))." -ForegroundColor Red
        $failed = $true
    }
}
if ($Stores -contains 'Locks') {
    foreach ($type in 'Build', 'Code') {
        $clear = Invoke-Forwarder -Path $clearLockScript -Arguments @('-Name', $type, '-Force')
        if ($clear.Code -ne 0) {
            Write-Host "mono-mode: clear-agent-lock.ps1 -Name $type -Force failed (exit $($clear.Code))." -ForegroundColor Red
            $failed = $true
        }
    }
}

$postArgs = @('-Verb', 'Post', '-Kind', 'note', '-Note', $Note)
if ($Ticket) { $postArgs += @('-Ticket', $Ticket) }
$post = Invoke-Forwarder -Path $chatScript -Arguments $postArgs -Capture
if ($post.Code -ne 0) {
    Write-Host "mono-mode: journal note not posted (exit $($post.Code)) - the run continues." -ForegroundColor DarkYellow
}

if ($failed) { exit 2 }
Write-Host 'mono-mode: started - the project is this run''s alone.' -ForegroundColor Green
exit 0
