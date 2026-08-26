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
    pwsh -NoProfile -File scripts/utils/clear-agent-lock.ps1 -Name Code -Force
#>
param(
    [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

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
    if ($Name -eq 'Build' -and $status.ProcessAlive) {
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
    $shortcut = if ($Name -eq 'Build') { 'ub' } else { 'uc' }
    Write-Host "  Override once the holder is confirmed gone:  .\a.ps1 $shortcut -Force" -ForegroundColor Gray
    exit 1
}

Remove-Item -LiteralPath $status.Path -Force -ErrorAction SilentlyContinue
Write-Host "$Name.LOCK cleared." -ForegroundColor Green
exit 0
