#requires -Version 7.0
<#
.SYNOPSIS
    S1344 pilot instrument: benchmarks the sector map against full -Search, classifies gate
    refusals, and computes the adopt/reject verdict.

.DESCRIPTION
    Strategic section 11.2 claims the sector map answers faster than a full -Search over 2398
    records, and section 11.3 makes "no false blocks over N real Kotlin-search tasks" an
    observable test rather than an architectural assertion. Both are measured here, into
    dev/sector-gate-pilot.jsonl, which also receives one row per refusal from
    .claude/hooks/guard-catalog-before-kt-search.ps1.

    Per-sector medians are never averaged together: section 11.2 is a per-query claim, and one
    fast sector must not hide a slow one.

    Both arms of the benchmark run query.ps1 as a child process, so both pay the same pwsh
    startup cost and the comparison stays fair even though neither number is the bare filter time.

    Exit codes:
      0 - the requested action completed.
      2 - the catalogue or the sector file is unreadable, or the arguments are unusable.
      3 - -Action summary with fewer than 3 classified refusals: not enough evidence for a verdict.
#>

param(
    [Parameter(Mandatory)][ValidateSet('benchmark', 'classify', 'summary')][string]$Action,
    [int]$Index,
    [ValidateSet('correct', 'false-block')][string]$Classification,
    [int]$Runs = 5
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$SectorFile = Join-Path $Root 'dev\CATALOG\sectors.json'
$QueryScript = Join-Path $Root 'dev\CATALOG\scripts\query.ps1'
$PilotLog = Join-Path $Root 'dev\sector-gate-pilot.jsonl'
# Owner ruling 2026-08-03: lowered from 10. The gate accrued zero real refusals across the first
# 1.5 days it was live, so a threshold of 10 made the verdict unreachable rather than rigorous.
$MinClassified = 3

function Read-Rows {
    if (-not (Test-Path -LiteralPath $PilotLog)) { return @() }
    $rows = @()
    foreach ($line in (Get-Content -LiteralPath $PilotLog -Encoding UTF8)) {
        if ($line.Trim()) { $rows += ($line | ConvertFrom-Json) }
    }
    return $rows
}

function Write-Rows([object[]]$rows) {
    $lines = @($rows | ForEach-Object { $_ | ConvertTo-Json -Depth 5 -Compress })
    Set-Content -LiteralPath $PilotLog -Value $lines -Encoding UTF8
}

function Get-Median([double[]]$values) {
    $sorted = @($values | Sort-Object)
    if ($sorted.Count -eq 0) { return $null }
    $mid = [math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return $sorted[$mid] }
    return [math]::Round((($sorted[$mid - 1] + $sorted[$mid]) / 2), 1)
}

function Measure-Query([string[]]$queryArgs) {
    $times = @()
    $count = 0
    for ($i = 0; $i -lt $Runs; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $out = & pwsh -NoProfile -File $QueryScript @queryArgs -Json 2>$null
        $sw.Stop()
        $times += $sw.Elapsed.TotalMilliseconds
        $count = @($out).Count
    }
    return [PSCustomObject]@{ MedianMs = (Get-Median $times); Count = $count }
}

if ($Action -eq 'benchmark') {
    if (-not (Test-Path -LiteralPath $SectorFile)) {
        Write-Error "sector-gate-pilot: sector definitions not found at $SectorFile" -ErrorAction Continue
        exit 2
    }
    if (-not (Test-Path -LiteralPath $QueryScript)) {
        Write-Error "sector-gate-pilot: query.ps1 not found at $QueryScript" -ErrorAction Continue
        exit 2
    }
    try {
        $sectors = @((Get-Content -LiteralPath $SectorFile -Raw -Encoding UTF8 | ConvertFrom-Json).sectors)
    }
    catch {
        Write-Error "sector-gate-pilot: sector definitions are malformed - $_" -ErrorAction Continue
        exit 2
    }

    $rows = @(Read-Rows | Where-Object { $_.kind -ne 'benchmark' })
    $table = @()
    foreach ($sector in $sectors) {
        $sectorArm = Measure-Query @('-Sector', $sector.name)
        $searchArm = Measure-Query @('-Search', $sector.name)
        $rows += [PSCustomObject]@{
            kind           = 'benchmark'
            at             = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')
            sector         = $sector.name
            runs           = $Runs
            sectorMedianMs = $sectorArm.MedianMs
            searchMedianMs = $searchArm.MedianMs
            sectorRecords  = $sectorArm.Count
            searchRecords  = $searchArm.Count
        }
        $table += [PSCustomObject]@{
            sector       = $sector.name
            sectorMedian = $sectorArm.MedianMs
            searchMedian = $searchArm.MedianMs
            faster       = if ($sectorArm.MedianMs -lt $searchArm.MedianMs) { 'sector' } else { 'search' }
            sectorRows   = $sectorArm.Count
            searchRows   = $searchArm.Count
        }
    }
    Write-Rows $rows
    $table | Format-Table -AutoSize
    Write-Host "sector-gate-pilot: expected: $($sectors.Count) benchmark row(s) | actual: $(@($table).Count)"
    exit 0
}

if ($Action -eq 'classify') {
    $rows = @(Read-Rows)
    $refusals = @($rows | Where-Object { $_.kind -eq 'refusal' })
    if ($refusals.Count -eq 0) {
        Write-Host 'sector-gate-pilot: no refusals recorded yet.'
        exit 0
    }
    if (-not $PSBoundParameters.ContainsKey('Index')) {
        for ($i = 0; $i -lt $refusals.Count; $i++) {
            $mark = if ($refusals[$i].classification) { $refusals[$i].classification } else { '<unclassified>' }
            Write-Host ("{0,3}  {1}  {2}  {3}  {4}" -f $i, $refusals[$i].at, $refusals[$i].tool, $mark, $refusals[$i].pattern)
        }
        exit 0
    }
    if ($Index -lt 0 -or $Index -ge $refusals.Count) {
        Write-Error "sector-gate-pilot: -Index $Index is outside 0..$($refusals.Count - 1)" -ErrorAction Continue
        exit 2
    }
    if (-not $Classification) {
        Write-Error 'sector-gate-pilot: -Classification is required with -Index (correct | false-block)' -ErrorAction Continue
        exit 2
    }
    $target = $refusals[$Index]
    foreach ($row in $rows) {
        if ($row.kind -eq 'refusal' -and $row.at -eq $target.at -and $row.pattern -eq $target.pattern) {
            $row.classification = $Classification
        }
    }
    Write-Rows $rows
    Write-Host "sector-gate-pilot: refusal $Index classified as $Classification"
    exit 0
}

# summary
$rows = @(Read-Rows)
$refusals = @($rows | Where-Object { $_.kind -eq 'refusal' })
$classified = @($refusals | Where-Object { $_.classification })
$benchmarks = @($rows | Where-Object { $_.kind -eq 'benchmark' })

if ($classified.Count -lt $MinClassified) {
    Write-Error ("sector-gate-pilot: {0} classified refusal(s), {1} required - the gate needs more real tasks before a verdict means anything." -f $classified.Count, $MinClassified) -ErrorAction Continue
    exit 3
}

$falseBlocks = @($classified | Where-Object { $_.classification -eq 'false-block' }).Count
$slowSectors = @($benchmarks | Where-Object { $null -eq $_.sectorMedianMs -or $_.sectorMedianMs -ge $_.searchMedianMs })
$verdict = if ($falseBlocks -eq 0 -and $benchmarks.Count -gt 0 -and $slowSectors.Count -eq 0) { 'adopt' } else { 'reject' }

if (-not @($rows | Where-Object { $_.kind -eq 'verdict' })) {
    $rows += [PSCustomObject]@{
        kind             = 'verdict'
        at               = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')
        verdict          = $verdict
        classifiedCount  = $classified.Count
        falseBlockCount  = $falseBlocks
        slowSectorCount  = $slowSectors.Count
    }
    Write-Rows $rows
}

Write-Host ("sector-gate-pilot: {0} classified refusal(s), {1} false block(s), {2} sector(s) not faster than -Search" -f $classified.Count, $falseBlocks, $slowSectors.Count)
Write-Host $verdict
exit 0
