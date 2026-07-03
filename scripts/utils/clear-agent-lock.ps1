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
    Write-Host "  pid: $($status.Pid)  age: $([int]$status.AgeSeconds)s  reason: '$($status.Reason)'  host: $($status.Host)" -ForegroundColor Yellow
    exit 1
}

Remove-Item -LiteralPath $status.Path -Force -ErrorAction SilentlyContinue
Write-Host "$Name.LOCK cleared." -ForegroundColor Green
exit 0
