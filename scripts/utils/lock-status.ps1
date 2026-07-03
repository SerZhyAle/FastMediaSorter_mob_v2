<#
.SYNOPSIS
    Fast, read-only check of temp/BUILD.LOCK or temp/CODE.LOCK - existence, age, holder.

.DESCRIPTION
    Never mutates the lock file (staleness is only ever reclaimed inside Enter-AgentLock, at
    the moment a caller actually wants the lock). Use this before starting a gradle-backed
    command or a multi-file code edit, to see whether another agent session currently holds
    the lock and how stale it looks.

    Exit code: 0 = free (no lock, or a stale one that a real acquire would reclaim).
               1 = held by a fresh lock.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build
    pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Json
#>
param(
    [Parameter(Mandatory)][ValidateSet('Build', 'Code')][string]$Name,
    [switch]$Json
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

$status = Get-AgentLockStatus -Name $Name

if ($Json) {
    $status | ConvertTo-Json -Compress
}
else {
    if (-not $status.Exists) {
        Write-Host "$Name.LOCK: absent (free)" -ForegroundColor Green
    }
    else {
        $label = if ($status.Stale) { "STALE (reclaimable)" } else { "HELD" }
        $color = if ($status.Stale) { "Yellow" } else { "Red" }
        Write-Host "$Name.LOCK: $label" -ForegroundColor $color
        Write-Host "  path:       $($status.Path)"
        Write-Host "  pid:        $($status.Pid)"
        if ($Name -eq 'Build') {
            Write-Host "  processAlive: $($status.ProcessAlive)"
        }
        Write-Host "  age:        $([int]$status.AgeSeconds)s"
        Write-Host "  acquiredAt: $($status.AcquiredAtIso)"
        Write-Host "  reason:     $($status.Reason)"
        Write-Host "  host:       $($status.Host)"
    }
}

if ($status.Exists -and -not $status.Stale) {
    exit 1
}
exit 0
