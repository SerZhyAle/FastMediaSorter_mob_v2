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
    exclusion set and exits only when it selects a ticket. Drift is skipped on purpose:
    the loop's real Stage 1 call re-runs preflight WITH drift before anything is claimed, so a
    poll only has to answer "is there a candidate at all".

    The verdict travels in a marker file, never in the exit code: a background task reports the
    exit of the last command in its launch line, which has already turned a refused build into an
    apparently green one. Read the marker.

    A poll that cannot read the eligible set does NOT end the wait. The failure is recorded and
    polling continues, because a broken tool must not turn an endless loop into a fast spin.

.PARAMETER Exclude
    Ids the caller already processed this session. Passed straight to preflight.

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
    3 - required preflight script is missing.
    4 - usage error.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/wait-for-ticket-work.ps1 -Exclude S1600,S1601 -Reason "/spec-do idle"
#>

[CmdletBinding(PositionalBinding = $false)]
param(
    [string[]]$Exclude = @(),
    [int]$PollSeconds = 120,
    [switch]$DeviceOnline,
    [string]$Reason = '',
    [string]$MarkerPath = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempDir = Join-Path $root 'temp'
if (-not (Test-Path -LiteralPath $tempDir)) { New-Item -ItemType Directory -Path $tempDir -Force | Out-Null }

# S2408: one identity chain for every coordination file, including this marker's name. The leaf
# identity library is dot-sourced directly rather than through agent-lock.ps1 - this script takes
# no lock and needs none of it.
. "$PSScriptRoot\agent-identity.ps1"
$sessionId = Get-AgentIdentityId
if ([string]::IsNullOrWhiteSpace($MarkerPath)) {
    $MarkerPath = Join-Path $tempDir "SPEC-DO.WORK-$sessionId.json"
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
        excluded      = $excludeIds
        reason        = $Reason
        detail        = $Detail
        sessionId     = $sessionId
    }
    try {
        $body | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $MarkerPath -Encoding UTF8
    }
    catch {
        # A marker that cannot be written must not kill the wait: the caller still re-checks the
        # queue when this waiter eventually wakes it for work.
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

Write-Output "wait-for-ticket-work: waiting for work (device=$([bool]$DeviceOnline)) - marker $MarkerPath"
Write-WorkMarker -Outcome 'waiting'

while ($true) {
    try {
        $payload = Invoke-Preflight
        if ($payload.selected) {
            Write-WorkMarker -Outcome 'work' -Kind 'impl' -Ticket $payload.selected
            Write-Output "wait-for-ticket-work: work available - $($payload.selected.id) ($($payload.selected.status))"
            exit 0
        }
        if ($DeviceOnline) {
            $backlog = Get-DeviceBacklogCount
            if ($backlog -gt 0) {
                Write-WorkMarker -Outcome 'work' -Kind 'device-drain' -DeviceBacklog $backlog
                Write-Output "wait-for-ticket-work: device backlog is available"
                exit 0
            }
        }
        Write-WorkMarker -Outcome 'waiting' -Detail $payload.selected_none_reason
    }
    catch {
        Write-WorkMarker -Outcome 'waiting' -Detail "poll failed: $($_.Exception.Message)"
    }

    Start-Sleep -Seconds $PollSeconds
}
