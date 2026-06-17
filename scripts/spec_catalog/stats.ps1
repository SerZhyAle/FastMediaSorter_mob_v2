[CmdletBinding()]
param(
    [ValidateSet('table','json')]
    [string] $Format = 'table'
)

. (Join-Path $PSScriptRoot '_lib.ps1')

# Full-catalog overview: include the archive so the status distribution still
# reports the Archived bucket (matches pre-split behaviour).
$all = Read-Catalog -IncludeArchived

# 1. by_status
$byStatus = @{}
foreach ($r in $all) {
    $s = $r.status
    if (-not $byStatus.ContainsKey($s)) { $byStatus[$s] = 0 }
    $byStatus[$s]++
}
$byStatusList = $byStatus.Keys | Sort-Object | ForEach-Object {
    [pscustomobject]@{ status = $_; count = $byStatus[$_] }
}

# 2. stale
$warnCount  = 0
$alertCount = 0
foreach ($r in $all) {
    $days  = Get-DaysSinceUpdated -Updated $r.updated
    $level = Get-StaleLevel -Status $r.status -Days $days
    if ($level -eq 'warn')  { $warnCount++ }
    if ($level -eq 'alert') { $alertCount++ }
}
$stale = [pscustomobject]@{ warn = $warnCount; alert = $alertCount }

# 3. recent (last 7 days, max 10)
$cutoff = (Get-Date).AddDays(-7).ToString('yyyy-MM-dd')
$recent = @($all | Where-Object { $_.updated -ge $cutoff } |
    Sort-Object -Property updated -Descending |
    Select-Object -First 10)

# 4. top_priority (top 5, non-Archived, priority desc then id asc)
$top_priority = @($all | Where-Object { $_.status -ne 'Archived' } |
    Sort-Object -Property @{ Expression = { [int]$_.priority }; Descending = $true },
                           @{ Expression = { $_.id };           Descending = $false } |
    Select-Object -First 5)

switch ($Format) {
    'json' {
        [pscustomobject]@{
            by_status    = $byStatusList
            stale        = $stale
            recent       = $recent
            top_priority = $top_priority
        } | ConvertTo-Json -Depth 5 -Compress
    }
    default {
        Write-Host '--- By Status ---' -ForegroundColor Cyan
        $byStatusList | Format-Table -AutoSize

        Write-Host '--- Stale ---' -ForegroundColor Cyan
        Write-Host ("  warn (>= {0}d): {1}   alert (>= {2}d): {3}" -f
            $script:StaleWarnDays, $warnCount, $script:StaleAlertDays, $alertCount)

        Write-Host ''
        Write-Host '--- Recent (last 7 days) ---' -ForegroundColor Cyan
        if ($recent) {
            $recent | Select-Object id, name, status, updated | Format-Table -AutoSize
        } else {
            Write-Host '  (none)'
        }

        Write-Host '--- Top Priority ---' -ForegroundColor Cyan
        if ($top_priority) {
            $top_priority | Select-Object id, name, status, priority | Format-Table -AutoSize
        } else {
            Write-Host '  (none)'
        }
    }
}
