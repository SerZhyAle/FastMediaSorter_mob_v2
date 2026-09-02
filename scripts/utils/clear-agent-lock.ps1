<#
.SYNOPSIS
    Remove a stale agent lock, or forcibly clear a live one when explicitly requested.

.DESCRIPTION
    Default mode is conservative:
      - stale / dead / corrupt lock -> removed, exit 0
      - fresh live lock            -> refused, exit 1

    Use -Force only when you have confirmed the holder is gone and want to override the safety
    checks manually.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/clear-agent-lock.ps1 -Name Build

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/clear-agent-lock.ps1 -Name Code.Wear -Force

.NOTES
    Exit codes:
      0 - the named domain(s) are free: already free, or cleared here.
      1 - refused: a live holder was found and -Force was not given. Its pid, age, reason and
          session id are printed instead, because clearing a live lock hands the turn to the next
          agent mid-edit.
      2 - the resource name is not an accepted domain or bare type; nothing was inspected.
    With a bare Build or Code the worst per-domain code is returned, so one refused domain is
    still visible when its neighbours were free.
#>
param(
    [Parameter(Mandatory)][string]$Name,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

# S2109: clear only the named domains. A bare name still clears the whole set, so the emergency
# escape hatch is as broad as it ever was; a concrete domain leaves every neighbour alone, which
# is the point - clearing a live sibling's lock is the one thing this script must not do by
# accident.
try {
    $domains = @(Resolve-AgentLockDomains -Name $Name)
}
catch {
    Write-Error "clear-agent-lock: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if ($domains.Count -gt 1) {
    $worst = 0
    foreach ($domain in $domains) {
        $argumentList = @('-NoProfile', '-File', $PSCommandPath, '-Name', $domain)
        if ($Force) { $argumentList += '-Force' }
        & pwsh @argumentList
        if ($LASTEXITCODE -gt $worst) { $worst = $LASTEXITCODE }
    }
    exit $worst
}

# S1432: the queue behind the lock is cleared alongside it - a ticket stranded behind a cleared
# lock would keep its session at the head and stall everyone else.
$queueDir = Get-AgentLockQueueDir -Name $Name
if ($Force) {
    $dropped = @(Get-ChildItem -LiteralPath $queueDir -Filter '*.json' -ErrorAction SilentlyContinue).Count
    Get-ChildItem -LiteralPath $queueDir -Filter '*.json' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    Write-Host "$Name queue: $dropped ticket(s) removed (-Force)." -ForegroundColor Yellow
}
else {
    $evicted = Remove-StaleAgentLockTickets -Name $Name
    $surviving = @(Get-AgentLockQueue -Name $Name).Count
    Write-Host "$Name queue: $evicted stale ticket(s) evicted, $surviving still waiting." -ForegroundColor Gray
}

$status = Get-AgentLockStatus -Name $Name
if (-not $status.Exists) {
    Write-Host "$Name.LOCK is already free." -ForegroundColor Green
    exit 0
}

if (-not $Force -and -not $status.Stale) {
    if ($Name -like 'Build*' -and $status.ProcessAlive) {
        Write-Host "$Name.LOCK is held by a live process - refusing to clear it." -ForegroundColor Red
    }
    else {
        Write-Host "$Name.LOCK is fresh - refusing to clear it without -Force." -ForegroundColor Red
    }
    # Minutes first: "1811s" is the number the file carries, "30 min" is the number the operator is
    # comparing against the monitor's held-time column. The session id matters more than the pid on
    # the Code lock - that pid can be recycled by Windows and then names an unrelated process.
    $ageText = "{0:N0} min ({1}s)" -f (($status.AgeSeconds) / 60), [int]$status.AgeSeconds
    Write-Host "  pid: $($status.Pid)  age: $ageText  reason: '$($status.Reason)'  host: $($status.Host)" -ForegroundColor Yellow
    if ($status.SessionId) {
        Write-Host "  session: $($status.SessionId)" -ForegroundColor Yellow
    }
    $shortcut = if ($Name -like 'Build*') { 'ub' } else { 'uc' }
    Write-Host "  Override once the holder is confirmed gone:  .\a.ps1 $shortcut -Force" -ForegroundColor Gray
    exit 1
}

Remove-Item -LiteralPath $status.Path -Force -ErrorAction SilentlyContinue
Write-Host "$Name.LOCK cleared." -ForegroundColor Green
exit 0
