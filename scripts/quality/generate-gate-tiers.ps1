#requires -Version 7.0
<#
.SYNOPSIS
    S4488: derive scripts/quality/gate-tiers.json - the fast/release class of every closure gate - from the
    gate telemetry journal.

.DESCRIPTION
    Between releases only checks of FastSeconds or less run (canon TESTING_AND_QA section 2). This script is
    the one place that class is decided, from records that already exist, so no check is run to be timed:

      gates    post-change.ps1 step labels, from the runner 'post-change' records
      scripts  assert-*.ps1 names in the fast-gate batch, from the runner 'assert-fast-gates' records
      targets  a.ps1 targets, from the measured table in docs/BUILD_TEST_FAST_PATH.md plus the rows below

    A record counts when its status is PASS or FAIL (the check actually answered). The class is 'fast' when
    the MEDIAN elapsed time is FastSeconds or less over at least MinRuns executed runs inside the window;
    anything else, and anything with no record at all, is 'release' until a release run measures it. The
    median is the same statistic measure-gate-frequency.ps1 -Placement uses, for the same reason: one stalled
    run must not be able to promote or demote a gate.

    The journal's elapsed time includes any wait for a build lock, so a gradle-backed gate reads slower than
    it computes. That error only ever pushes a gate towards 'release', which is the safe direction.

    Re-run it at each release: the release run is what measures the checks that had no record.

.PARAMETER Telemetry
    The gate journal. Defaults to temp/metrics/gate-executions.jsonl.

.PARAMETER Out
    The registry to write. Defaults to scripts/quality/gate-tiers.json.

.PARAMETER FastSeconds
    The line. Defaults to 5.

.PARAMETER MinRuns
    The fewest executed runs a gate needs before its median is believed. Defaults to 5.

.PARAMETER WindowDays
    Only records newer than this count. Defaults to 30, so a gate that was made faster or slower is judged on
    its current shape.

.PARAMETER Check
    Write nothing; exit 1 when the registry on disk differs from what the journal now says.

.NOTES
    Exit codes: 0 registry written (or, with -Check, unchanged); 1 -Check found the registry stale;
    2 the journal is missing or empty, so nothing was derived.
#>
[CmdletBinding()]
param(
    [string]$Telemetry,
    [string]$Out,
    [double]$FastSeconds = 5.0,
    [int]$MinRuns = 5,
    [int]$WindowDays = 30,
    [switch]$Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $Telemetry) { $Telemetry = Join-Path $root 'temp/metrics/gate-executions.jsonl' }
if (-not $Out) { $Out = Join-Path $root 'scripts/quality/gate-tiers.json' }

if (-not (Test-Path -LiteralPath $Telemetry)) {
    Write-Host "generate-gate-tiers: COULD NOT VERIFY (no journal at $Telemetry)" -ForegroundColor Red
    exit 2
}

$cutoff = (Get-Date).ToUniversalTime().AddDays(-1 * $WindowDays)
$rx = [regex]'^\{"timestampUtc":"(?<t>[^"]+)","runner":"(?<r>[^"]+)"(?:,"runId":"[^"]*")?,"gate":"(?<g>[^"]*)","status":"(?<s>[A-Z]+)","exitCode":-?\d+,"elapsedMs":(?<ms>\d+)'
$samples = @{ 'post-change' = @{}; 'assert-fast-gates' = @{} }
$cutoffText = $cutoff.ToString('yyyy-MM-ddTHH:mm:ss')
$counted = 0
foreach ($line in [System.IO.File]::ReadLines($Telemetry)) {
    $m = $rx.Match($line)
    if (-not $m.Success) { continue }
    $runner = $m.Groups['r'].Value
    if (-not $samples.ContainsKey($runner)) { continue }
    $status = $m.Groups['s'].Value
    if ($status -ne 'PASS' -and $status -ne 'FAIL') { continue }
    if ([string]::CompareOrdinal($m.Groups['t'].Value, $cutoffText) -lt 0) { continue }
    $gate = $m.Groups['g'].Value
    if (-not $samples[$runner].ContainsKey($gate)) { $samples[$runner][$gate] = [System.Collections.Generic.List[int]]::new() }
    $samples[$runner][$gate].Add([int]$m.Groups['ms'].Value)
    $counted++
}

if ($counted -eq 0) {
    Write-Host "generate-gate-tiers: COULD NOT VERIFY (no executed record inside the last $WindowDays days)" -ForegroundColor Red
    exit 2
}

function Get-Median([System.Collections.Generic.List[int]]$Values) {
    $sorted = @($Values | Sort-Object)
    $n = $sorted.Count
    if ($n % 2 -eq 1) { return [double]$sorted[($n - 1) / 2] }
    return ([double]$sorted[$n / 2 - 1] + [double]$sorted[$n / 2]) / 2
}

function ConvertTo-TierTable([hashtable]$ByGate) {
    $table = [ordered]@{}
    foreach ($name in ($ByGate.Keys | Sort-Object)) {
        $runs = $ByGate[$name].Count
        $median = [Math]::Round((Get-Median $ByGate[$name]) / 1000.0, 2)
        $tier = if ($runs -ge $MinRuns -and $median -le $FastSeconds) { 'fast' } else { 'release' }
        $table[$name] = [ordered]@{ tier = $tier; medianSec = $median; runs = $runs }
    }
    return $table
}

# a.ps1 targets. Seconds are the figures docs/BUILD_TEST_FAST_PATH.md records (warm daemon, run alone); a
# target with no figure is 'release' by the rule above. 'ss' was timed with one direct call on 2026-10-29.
$targetRows = [ordered]@{
    'ss'  = @{ sec = 0.82;  src = 'one direct call, 2026-10-29' }
    'ff'  = @{ sec = $null; src = 'the fast-tier command; its own timed run is recorded in S4488' }
    'fk'  = @{ sec = 14.1;  src = 'docs/BUILD_TEST_FAST_PATH.md' }
    'fc'  = @{ sec = 18.6;  src = 'docs/BUILD_TEST_FAST_PATH.md' }
    'dq'  = @{ sec = 18.4;  src = 'docs/BUILD_TEST_FAST_PATH.md' }
    'faw' = @{ sec = 15.0;  src = 'docs/BUILD_TEST_FAST_PATH.md (approximate)' }
    'fg'  = @{ sec = 79.0;  src = 'docs/BUILD_TEST_FAST_PATH.md (whole batch at the Release tier; in an agent shell it runs only its fast members)' }
    'fs'  = @{ sec = 215.4; src = 'docs/BUILD_TEST_FAST_PATH.md' }
    'fl'  = @{ sec = 240.0; src = 'docs/BUILD_TEST_FAST_PATH.md (about 4 min on the CI runner)' }
    'fll' = @{ sec = 628.0; src = 'docs/BUILD_TEST_FAST_PATH.md (10m 28s)' }
}
foreach ($unmeasured in 'r', 'nl', 'nd', 'd', 'db', 'dav', 'dc', 'cd', 'cdb', 'fkn', 'fr', 'fu', 'fa', 'fam', 'fst',
    'fw', 'fwn', 'fwr', 'fwrn', 'fwu', 'fwun', 'fwm', 'flr', 'flw', 'ch', 'vr', 'wd', 'iw', 'wfr', 'wfd', 'mb', 'gbp', 'bp') {
    $targetRows[$unmeasured] = @{ sec = $null; src = 'no recorded time' }
}
$targets = [ordered]@{}
foreach ($name in $targetRows.Keys) {
    $row = $targetRows[$name]
    $tier = if ($null -ne $row.sec -and $row.sec -le $FastSeconds) { 'fast' } else { 'release' }
    # ff and fg resolve their own members by tier, so the launcher must let them through: ff is the fast
    # tier itself, and fg in an agent shell is the batch's fast members only (-Tier Release for all of it).
    if ($name -in @('ff', 'fg')) { $tier = 'fast' }
    $targets[$name] = [ordered]@{ tier = $tier; medianSec = $row.sec; source = $row.src }
}

$gateTable = ConvertTo-TierTable $samples['post-change']
$scriptTable = ConvertTo-TierTable $samples['assert-fast-gates']

# post-change.ps1 starts its gate children speculatively (Start-PooledGate) before the call site that
# knows the label, so a skipped release-tier gate must also be recognised by its SCRIPT. The pairing is read
# from the facade's own text: `$argvX = @(.. "scripts/.../x.ps1" ..)` names the script,
# `Invoke-Gate "label" { Invoke-GateChild @argvX }` and `Invoke-FixedInputGate "label" $argvX '..'` name the
# label. A script reached by several labels is release when any of them is, and a script the fast batch has
# also measured keeps the stricter of the two answers.
$argvScript = @{}
$argvLabels = @{}
$facadeFiles = @(Join-Path $root 'scripts/post-change.ps1') + @(Get-ChildItem -LiteralPath (Join-Path $root 'scripts/quality/lib') -Filter 'post-change-*.ps1' | ForEach-Object { $_.FullName })
foreach ($facadeFile in $facadeFiles) {
    foreach ($facadeLine in [System.IO.File]::ReadLines($facadeFile)) {
        $defMatch = [regex]::Match($facadeLine, '^\$(argv\w+)\s*=\s*@\(.*?["'']scripts/(?:[\w.-]+/)*([\w.-]+\.ps1)["'']')
        if ($defMatch.Success) { $argvScript[$defMatch.Groups[1].Value] = $defMatch.Groups[2].Value; continue }
        $useMatch = [regex]::Match($facadeLine, 'Invoke-(?:Gate|AdvisoryStep)\s+"([^"]+)"\s*\{[^{}]*?@(argv\w+)|Invoke-FixedInputGate\s+"([^"]+)"\s+\$(argv\w+)')
        if ($useMatch.Success) {
            $label = if ($useMatch.Groups[1].Success) { $useMatch.Groups[1].Value } else { $useMatch.Groups[3].Value }
            $var = if ($useMatch.Groups[2].Success) { $useMatch.Groups[2].Value } else { $useMatch.Groups[4].Value }
            if (-not $argvLabels.ContainsKey($var)) { $argvLabels[$var] = @() }
            $argvLabels[$var] += $label
        }
    }
}
foreach ($var in $argvLabels.Keys) {
    if (-not $argvScript.ContainsKey($var)) { continue }
    $scriptName = $argvScript[$var]
    $labelRows = @($argvLabels[$var] | ForEach-Object { if ($gateTable.Contains($_)) { $gateTable[$_] } else { $null } })
    $anyRelease = (@($labelRows | Where-Object { $null -eq $_ -or $_.tier -ne 'fast' }).Count -gt 0)
    if ($scriptTable.Contains($scriptName) -and $scriptTable[$scriptName].tier -eq 'release') { $anyRelease = $true }
    $measured = @($labelRows | Where-Object { $null -ne $_ } | ForEach-Object { $_.medianSec })
    $sec = if ($scriptTable.Contains($scriptName)) { $scriptTable[$scriptName].medianSec } elseif ($measured.Count -gt 0) { ($measured | Measure-Object -Maximum).Maximum } else { $null }
    $runs = if ($scriptTable.Contains($scriptName)) { $scriptTable[$scriptName].runs } else { 0 }
    $scriptTable[$scriptName] = [ordered]@{ tier = $(if ($anyRelease) { 'release' } else { 'fast' }); medianSec = $sec; runs = $runs }
}

$registry = [ordered]@{
    _comment       = @(
        'S4488. Generated by scripts/quality/generate-gate-tiers.ps1 - never hand-edit; re-run it at each release.',
        'tier fast = median at or under fastLineSeconds over at least minRuns executed runs; every other check, and any check with no record, is release.',
        'Between releases only fast checks run (canon TESTING_AND_QA section 2). A release run (tier Release) runs every check.',
        'gates = post-change.ps1 step labels; scripts = assert-fast-gates.ps1 batch members; targets = a.ps1 launcher targets.'
    )
    fastLineSeconds = $FastSeconds
    minRuns         = $MinRuns
    windowDays      = $WindowDays
    measured        = (Get-Date -Format 'yyyy-MM-dd')
    source          = 'temp/metrics/gate-executions.jsonl, executed runs (PASS/FAIL), median of elapsedMs; runner post-change -> gates, runner assert-fast-gates -> scripts'
    gates           = $gateTable
    scripts         = $scriptTable
    targets         = $targets
}
$json = $registry | ConvertTo-Json -Depth 6

if ($Check) {
    $current = if (Test-Path -LiteralPath $Out) { Get-Content -LiteralPath $Out -Raw } else { '' }
    $strip = { param($t) ($t -replace '"measured":\s*"[^"]*",?', '') -replace '\s+', '' }
    if ((& $strip $current) -ne (& $strip $json)) {
        Write-Host 'generate-gate-tiers: FAIL (registry differs from the journal; re-run without -Check)' -ForegroundColor Red
        exit 1
    }
    Write-Host 'generate-gate-tiers: PASS (registry matches the journal)'
    exit 0
}

[System.IO.File]::WriteAllText($Out, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
$fastGates = @($registry.gates.Values | Where-Object { $_.tier -eq 'fast' }).Count
$fastScripts = @($registry.scripts.Values | Where-Object { $_.tier -eq 'fast' }).Count
Write-Host ("generate-gate-tiers: PASS ({0} records read; gates {1} fast of {2}; scripts {3} fast of {4}; {5})" -f `
        $counted, $fastGates, $registry.gates.Count, $fastScripts, $registry.scripts.Count, $Out)
exit 0
