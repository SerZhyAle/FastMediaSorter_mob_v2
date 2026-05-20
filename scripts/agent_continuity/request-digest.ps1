[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [int] $Window = 30,

    [Parameter(Mandatory = $false)]
    [string] $LogPath = '',

    [Parameter(Mandatory = $false)]
    [string] $FunctionalityPath = '',

    [Parameter(Mandatory = $false)]
    [string] $PlanGlob = ''
)

# Agent Continuity Layer (S0268) - Request digest.
# Reads requests.jsonl + dev/FUNCTIONALITY.log + PLAN/S*.md and prints a ranked profile.
# Tolerates an empty / absent request log - falls back to the other sources.
# See: PLAN/S0268_agent_continuity_layer.md  §5.4

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $LogPath)           { $LogPath           = Join-Path $repoRoot 'dev\agent-continuity\requests.jsonl' }
if (-not $FunctionalityPath) { $FunctionalityPath = Join-Path $repoRoot 'dev\FUNCTIONALITY.log' }
if (-not $PlanGlob)          { $PlanGlob          = Join-Path $repoRoot 'PLAN\S*.md' }

$cutoff = (Get-Date).AddDays(-$Window)
$fallbackNote = '(no request log - using PLAN/* and FUNCTIONALITY.log only)'

# ----- Load JSONL records (best effort) -----
$records = @()
$logExists = Test-Path $LogPath
if ($logExists) {
    try {
        $lines = Get-Content -Path $LogPath -ErrorAction Stop
        foreach ($l in $lines) {
            if ([string]::IsNullOrWhiteSpace($l)) { continue }
            try { $records += ($l | ConvertFrom-Json) } catch { continue }
        }
    } catch {
        $logExists = $false
    }
}

# ----- Load FUNCTIONALITY entries within window -----
$funcEntries = @()
if (Test-Path $FunctionalityPath) {
    try {
        $funcLines = Get-Content -Path $FunctionalityPath -ErrorAction Stop
        foreach ($f in $funcLines) {
            if ($f -match '^\[(\d{4}-\d{2}-\d{2})\s+\d{2}:\d{2}\]') {
                try {
                    $when = [datetime]::ParseExact($Matches[1], 'yyyy-MM-dd', $null)
                    if ($when -ge $cutoff) { $funcEntries += $f }
                } catch { continue }
            }
        }
    } catch { }
}

# ----- Count PLAN/S*.md files modified within window -----
$planCount = 0
try {
    $planFiles = Get-ChildItem -Path $PlanGlob -File -ErrorAction Stop
    foreach ($p in $planFiles) {
        if ($p.LastWriteTime -ge $cutoff) { $planCount++ }
    }
} catch { }

# ----- Header -----
$logCount = if ($logExists) { $records.Count } else { 'absent' }
$funcCount = if (Test-Path $FunctionalityPath) { $funcEntries.Count } else { 'absent' }

Write-Output '# Request Digest'
Write-Output "Window: $Window days"
Write-Output 'Sources:'
Write-Output "  - requests.jsonl: $logCount lines"
Write-Output "  - FUNCTIONALITY.log: $funcCount entries (last $Window days)"
Write-Output "  - PLAN/S*.md: $planCount files (modified within window)"

# ----- Section 1: top-routes -----
Write-Output ''
Write-Output '## top-routes'
if (-not $logExists -or $records.Count -eq 0) {
    Write-Output $fallbackNote
} else {
    $records | Where-Object { $_.route } | Group-Object route | Sort-Object Count -Descending | ForEach-Object {
        Write-Output ("  {0,4}  {1}" -f $_.Count, $_.Name)
    }
}

# ----- Section 2: top-modules -----
Write-Output ''
Write-Output '## top-modules'
if (-not $logExists -or $records.Count -eq 0) {
    Write-Output $fallbackNote
} else {
    $records | Where-Object { $_.module } | Group-Object module | Sort-Object Count -Descending | ForEach-Object {
        Write-Output ("  {0,4}  {1}" -f $_.Count, $_.Name)
    }
}

# ----- Section 3: validation-cost -----
Write-Output ''
Write-Output '## validation-cost'
if (-not $logExists -or $records.Count -eq 0) {
    Write-Output $fallbackNote
} else {
    $records | Where-Object { $_.validation_kind } | Group-Object validation_kind | ForEach-Object {
        $kind = $_.Name
        $nonZero = ($_.Group | Where-Object { $_.validation_exit -ne 0 }).Count
        $total = $_.Count
        Write-Output ("  {0,-20}  total={1}  failures={2}" -f $kind, $total, $nonZero)
    }
}

# ----- Section 4: interruptions -----
Write-Output ''
Write-Output '## interruptions'
if (-not $logExists -or $records.Count -eq 0) {
    Write-Output $fallbackNote
} else {
    $marked = $records | Where-Object { $_.interruption_marker }
    if (-not $marked -or $marked.Count -eq 0) {
        Write-Output '  (no interruption markers recorded)'
    } else {
        $marked | Group-Object interruption_marker | Sort-Object Count -Descending | ForEach-Object {
            Write-Output ("  {0,4}  {1}" -f $_.Count, $_.Name)
        }
    }
}

# ----- Section 5: ux-volatility -----
Write-Output ''
Write-Output '## ux-volatility'
if ($funcEntries.Count -eq 0) {
    Write-Output '  (no CHANGE/FIX entries within window)'
} else {
    $tail = $funcEntries | Where-Object { $_ -match 'CHANGE|FIX' } | Select-Object -Last 20
    foreach ($l in $tail) { Write-Output ("  " + $l) }
}

exit 0
