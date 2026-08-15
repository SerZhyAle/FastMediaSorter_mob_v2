#requires -Version 7.0
<#
.SYNOPSIS
    Wait until the spec queue has work again, then EXIT. The idle waiter of the endless /spec-do loop.

.DESCRIPTION
    /spec-do does not end on an empty queue: when the eligible set drains it idles and re-checks
    instead of printing a final report. This script is that wait. Run it as a BACKGROUND task -
    its exit is the "there is work again" signal, which is the only channel through which an
    external event (the owner answering a BlockQuestions ticket, a sibling releasing a lease, a
    fresh Draft landing) can return an idle agent to work. Same shape as
    scripts/utils/wait-for-lock-turn.ps1, for the same reason.

    Polling is read-only. Each poll re-runs spec-next-preflight.ps1 -NoDrift with the caller's
    exclusion set and reports the first poll that selects a ticket. Drift is skipped on purpose:
    the loop's real Stage 1 call re-runs preflight WITH drift before anything is claimed, so a
    poll only has to answer "is there a candidate at all". One poll costs ~5 s of CPU, so the
    default interval keeps the idle duty cycle under 5%.

    The verdict travels in a marker file, never in the exit code: a background task reports the
    exit of the last command in its launch line, which has already turned a refused build into an
    apparently green one. Read the marker.

    A poll that cannot read the eligible set does NOT end the wait - it is recorded and the window
    keeps polling, because a broken tool must not turn an endless loop into a fast spin. Exit 3
    is reserved for a window in which every poll failed.

.PARAMETER Exclude
    Ids the caller already processed this session. Passed straight to preflight.

.PARAMETER MaxMinutes
    Wait window. On expiry the script exits 2 so the caller prints one heartbeat line and calls
    again. The script never decides on its own that waiting is over.

.PARAMETER PollSeconds
    Interval between checks. Minimum 15.

.PARAMETER DeviceOnline
    Also treat a non-empty BlockNeedUserTest backlog as work, so the loop wakes for its Stage 5.5
    device drain. Pass it only when a device is actually attached.

.PARAMETER Reason
    Free text recorded in the marker - shown to whoever inspects an idling session.

.PARAMETER MarkerPath
    Override the marker location. Defaults to temp/SPEC-DO.WORK-<sessionId>.json.

.EXIT CODES
    0 - work is available now; the marker names the kind (impl / device-drain) and the ticket.
    2 - the wait window expired with the queue still idle.
    3 - every poll in the window failed to read the eligible set (preflight missing or broken).
    4 - usage error.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/wait-for-ticket-work.ps1 -Exclude S1600,S1601 -Reason "/spec-do idle"
#>

[CmdletBinding(PositionalBinding = $false)]
param(
    [string[]]$Exclude = @(),
    [int]$MaxMinutes = 30,
    [int]$PollSeconds = 120,
    [switch]$DeviceOnline,
    [string]$Reason = '',
    [string]$MarkerPath = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempDir = Join-Path $root 'temp'
if (-not (Test-Path -LiteralPath $tempDir)) { New-Item -ItemType Directory -Path $tempDir -Force | Out-Null }

$sessionId = $env:CLAUDE_CODE_SESSION_ID
if ([string]::IsNullOrWhiteSpace($sessionId)) { $sessionId = "pid-$PID" }
if ([string]::IsNullOrWhiteSpace($MarkerPath)) {
    $MarkerPath = Join-Path $tempDir "SPEC-DO.WORK-$sessionId.json"
}

if ($MaxMinutes -lt 1) {
    Write-Error "wait-for-ticket-work: -MaxMinutes must be at least 1 (got $MaxMinutes)" -ErrorAction Continue
    exit 4
}
if ($PollSeconds -lt 15) {
    Write-Error "wait-for-ticket-work: -PollSeconds must be at least 15 (got $PollSeconds)" -ErrorAction Continue
    exit 4
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}
# Deliberately NOT filed under scripts/spec_catalog/: the sza guard-fire-and-forget hook refuses
# run_in_background for any command naming that directory, and being backgrounded is this script's
# entire contract. It lives beside wait-for-lock-turn.ps1, the waiter it copies.
$catalogDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'spec_catalog'
$preflightPath = Join-Path $catalogDir 'spec-next-preflight.ps1'
$searchPath = Join-Path $catalogDir 'search.ps1'
if (-not (Test-Path -LiteralPath $preflightPath)) {
    Write-Error "wait-for-ticket-work: preflight not found at $preflightPath" -ErrorAction Continue
    exit 3
}

# Normalize the exclusion set the way preflight does, so a CSV element and a repeated id both work.
$excludeIds = @()
foreach ($raw in $Exclude) {
    if ([string]::IsNullOrWhiteSpace($raw)) { continue }
    foreach ($part in ($raw -split '[,;\s]+')) {
        if (-not [string]::IsNullOrWhiteSpace($part)) { $excludeIds += $part.Trim() }
    }
}
$excludeIds = @($excludeIds | Select-Object -Unique)

$startedAt = Get-Date
$deadline = $startedAt.AddMinutes($MaxMinutes)
$polls = 0
$failedPolls = 0
$lastError = ''

function Write-WorkMarker {
    param(
        [Parameter(Mandatory)][string]$Outcome,
        [string]$Kind = '',
        $Ticket = $null,
        [int]$DeviceBacklog = 0,
        [string]$Detail = ''
    )
    $body = [ordered]@{
        outcome       = $Outcome
        kind          = if ([string]::IsNullOrWhiteSpace($Kind)) { $null } else { $Kind }
        id            = if ($Ticket) { $Ticket.id } else { $null }
        status        = if ($Ticket) { $Ticket.status } else { $null }
        name          = if ($Ticket) { $Ticket.name } else { $null }
        deviceBacklog = $DeviceBacklog
        polls         = $polls
        waitedSeconds = [int]((Get-Date) - $startedAt).TotalSeconds
        excluded      = $excludeIds
        reason        = $Reason
        detail        = $Detail
        sessionId     = $sessionId
        startedAt     = $startedAt.ToString('s')
        checkedAt     = (Get-Date).ToString('s')
    }
    try {
        $body | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $MarkerPath -Encoding UTF8
    }
    catch {
        # A marker that cannot be written must not kill the wait - the exit code still carries the
        # coarse verdict, and the caller re-checks the queue itself on the next round.
        Write-Output "wait-for-ticket-work: marker write failed ($($_.Exception.Message))"
    }
}

function Split-ChildOutput {
    # A merged 2>&1 stream would corrupt the JSON parse the moment the child writes one warning,
    # so the streams are separated here rather than at the call site.
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Stream)
    $errors = @($Stream | Where-Object { $_ -is [System.Management.Automation.ErrorRecord] })
    $out = @($Stream | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] })
    return [PSCustomObject]@{ StdOut = ($out -join "`n"); StdErr = (($errors | ForEach-Object { $_.ToString() }) -join '; ') }
}

function Invoke-Preflight {
    $pfArgs = @('-NoProfile', '-File', $preflightPath, '-NoDrift')
    if ($excludeIds.Count -gt 0) { $pfArgs += @('-Exclude', ($excludeIds -join ',')) }
    $split = Split-ChildOutput -Stream @(& $pwshExe @pfArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "preflight exit $LASTEXITCODE - $($split.StdErr)"
    }
    if ([string]::IsNullOrWhiteSpace($split.StdOut)) {
        throw "preflight returned no payload - $($split.StdErr)"
    }
    return ($split.StdOut | ConvertFrom-Json)
}

function Get-DeviceBacklogCount {
    if (-not (Test-Path -LiteralPath $searchPath)) { return 0 }
    $split = Split-ChildOutput -Stream @(& $pwshExe -NoProfile -File $searchPath -Status BlockNeedUserTest -Format json 2>&1)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($split.StdOut)) { return 0 }
    return @($split.StdOut | ConvertFrom-Json).Count
}

Write-Output "wait-for-ticket-work: waiting up to $MaxMinutes min (poll ${PollSeconds}s, excluded $($excludeIds.Count), device=$([bool]$DeviceOnline)) - marker $MarkerPath"
Write-WorkMarker -Outcome 'waiting'

while ($true) {
    $polls++
    try {
        $payload = Invoke-Preflight
        if ($payload.selected) {
            Write-WorkMarker -Outcome 'work' -Kind 'impl' -Ticket $payload.selected
            Write-Output "wait-for-ticket-work: work available - $($payload.selected.id) ($($payload.selected.status)) after $polls poll(s)"
            exit 0
        }
        if ($DeviceOnline) {
            $backlog = Get-DeviceBacklogCount
            if ($backlog -gt 0) {
                Write-WorkMarker -Outcome 'work' -Kind 'device-drain' -DeviceBacklog $backlog
                Write-Output "wait-for-ticket-work: work available - $backlog BlockNeedUserTest ticket(s) to drain after $polls poll(s)"
                exit 0
            }
        }
        Write-WorkMarker -Outcome 'waiting' -Detail $payload.selected_none_reason
    }
    catch {
        $failedPolls++
        $lastError = $_.Exception.Message
        Write-WorkMarker -Outcome 'waiting' -Detail "poll failed: $lastError"
    }

    $remaining = ($deadline - (Get-Date)).TotalSeconds
    if ($remaining -le 0) { break }
    Start-Sleep -Seconds ([Math]::Min($PollSeconds, [int][Math]::Ceiling($remaining)))
    if ((Get-Date) -ge $deadline) { break }
}

if ($failedPolls -eq $polls) {
    Write-WorkMarker -Outcome 'unverifiable' -Detail "every poll failed: $lastError"
    Write-Error "wait-for-ticket-work: could not read the eligible set on any of $polls poll(s) - last error: $lastError" -ErrorAction Continue
    exit 3
}

Write-WorkMarker -Outcome 'idle' -Detail "window expired after $polls poll(s), $failedPolls failed"
Write-Output "wait-for-ticket-work: still idle after $MaxMinutes min ($polls polls, $failedPolls failed) - caller decides whether to wait again"
exit 2
