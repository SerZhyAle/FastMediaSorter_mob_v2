# Compares two macrobenchmark result files against the committed budgets and fails on a regression.
#
# S3322: the thresholds in docs/PERFETTO_PLAYBOOK.md were prose a human compared by eye, so a
# regression could only be noticed by someone who already suspected one. androidx.benchmark has no API
# that fails a test on a threshold, so the budget is applied after the run, to the JSON it writes.
#
# Inputs are the `*-benchmarkData.json` files under
# benchmark/build/outputs/connected_android_test_additional_output/<variant>/connected/<device>/.
# Compare runs from the SAME device: a budget crossed between two different devices measures the
# devices.
#
# Exit codes:
#   0 - every budgeted record is within its budget
#   1 - at least one record regressed beyond its budget
#   2 - cannot verify: a file is missing or unparseable, the budget file is invalid, or no budgeted
#       record is present in both runs

param(
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$Candidate,
    [string]$Budgets = "$PSScriptRoot\macrobenchmark-budgets.json",
    [switch]$Json
)

$ErrorActionPreference = "Stop"

function Read-JsonFile {
    param([string]$Path, [string]$Label)

    # Cannot-verify diagnostics go to stderr so that -Json keeps stdout parseable on every path.
    if (-not (Test-Path -LiteralPath $Path)) {
        [Console]::Error.WriteLine("compare-macrobenchmark-runs: $Label not found: $Path")
        exit 2
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        [Console]::Error.WriteLine("compare-macrobenchmark-runs: $Label is not valid JSON: $Path")
        exit 2
    }
}

function Get-MetricValue {
    param($Run, [string]$Benchmark, [string]$Class, [string]$Metric, [string]$Statistic)

    $entries = @($Run.benchmarks | Where-Object { $_.name -eq $Benchmark })
    if ($Class) {
        # className is fully qualified; the budget names the simple class so one record survives a
        # package move.
        $entries = @($entries | Where-Object { $_.className -and $_.className.EndsWith(".$Class") })
    }
    if ($entries.Count -eq 0) { return $null }

    $entry = $entries[0]
    # FrameTimingMetric lands under sampledMetrics with percentile keys; StartupTimingMetric and
    # TraceSectionMetric land under metrics with median/minimum/maximum.
    foreach ($bucket in @($entry.metrics, $entry.sampledMetrics)) {
        if (-not $bucket) { continue }
        $holder = $bucket.PSObject.Properties[$Metric]
        if (-not $holder) { continue }
        $stat = $holder.Value.PSObject.Properties[$Statistic]
        if (-not $stat) { continue }
        return [double]$stat.Value
    }
    return $null
}

$budgetDoc = Read-JsonFile -Path $Budgets -Label "budget file"
if (-not $budgetDoc.budgets) {
    [Console]::Error.WriteLine("compare-macrobenchmark-runs: budget file holds no 'budgets' array: $Budgets")
    exit 2
}

$baselineRun = Read-JsonFile -Path $Baseline -Label "baseline run"
$candidateRun = Read-JsonFile -Path $Candidate -Label "candidate run"

$results = New-Object System.Collections.Generic.List[object]
foreach ($budget in $budgetDoc.budgets) {
    $before = Get-MetricValue -Run $baselineRun -Benchmark $budget.benchmark -Class $budget.class `
        -Metric $budget.metric -Statistic $budget.statistic
    $after = Get-MetricValue -Run $candidateRun -Benchmark $budget.benchmark -Class $budget.class `
        -Metric $budget.metric -Statistic $budget.statistic

    if ($null -eq $before -or $null -eq $after) {
        $null = $results.Add([pscustomobject]@{
            benchmark = $budget.benchmark
            metric    = "$($budget.metric).$($budget.statistic)"
            verdict   = "SKIP"
            baseline  = $before
            candidate = $after
            reason    = "not measured in both runs"
        })
        continue
    }

    $absoluteIncrease = $after - $before
    $relativeIncrease = if ($before -gt 0) { $absoluteIncrease / $before } else { 0 }
    # Both allowances must be exceeded, so a large relative jump on a tiny number is not a regression
    # and neither is a small absolute drift on a large one.
    $regressed = ($absoluteIncrease -gt $budget.maxAbsoluteIncreaseMs) -and
        ($relativeIncrease -gt $budget.maxRelativeIncrease)

    $reason = "{0:+0.000;-0.000;0.000} ms ({1:P1}), budget {2} ms and {3:P0}" -f $absoluteIncrease,
        $relativeIncrease, $budget.maxAbsoluteIncreaseMs, $budget.maxRelativeIncrease

    $null = $results.Add([pscustomobject]@{
        benchmark = $budget.benchmark
        metric    = "$($budget.metric).$($budget.statistic)"
        verdict   = if ($regressed) { "FAIL" } else { "PASS" }
        baseline  = [math]::Round($before, 3)
        candidate = [math]::Round($after, 3)
        reason    = $reason
    })
}

$compared = @($results | Where-Object { $_.verdict -ne "SKIP" })
$failed = @($results | Where-Object { $_.verdict -eq "FAIL" })

if ($Json) {
    [pscustomobject]@{
        baseline = (Resolve-Path -LiteralPath $Baseline).Path
        candidate = (Resolve-Path -LiteralPath $Candidate).Path
        compared = $compared.Count
        failed = $failed.Count
        records = $results
    } | ConvertTo-Json -Depth 5
} else {
    foreach ($record in $results) {
        $color = switch ($record.verdict) {
            "FAIL" { "Red" }
            "PASS" { "Green" }
            default { "DarkGray" }
        }
        Write-Host ("{0,-4} {1} {2} - {3}" -f $record.verdict, $record.benchmark, $record.metric, $record.reason) `
            -ForegroundColor $color
    }
}

function Write-Summary {
    param([string]$Message, [string]$Color)

    # -Json puts the machine-readable document alone on stdout: a Write-Host line reaches a calling
    # process's stdout too, and one summary line makes the whole document unparseable.
    if (-not $Json) { Write-Host $Message -ForegroundColor $Color }
}

if ($compared.Count -eq 0) {
    Write-Summary -Message "compare-macrobenchmark-runs: no budgeted record is present in both runs" -Color Red
    exit 2
}
if ($failed.Count -gt 0) {
    Write-Summary -Message "compare-macrobenchmark-runs: FAIL - $($failed.Count) of $($compared.Count) budgeted record(s) regressed" -Color Red
    exit 1
}

Write-Summary -Message "compare-macrobenchmark-runs: PASS - $($compared.Count) budgeted record(s) within budget" -Color Green
exit 0
