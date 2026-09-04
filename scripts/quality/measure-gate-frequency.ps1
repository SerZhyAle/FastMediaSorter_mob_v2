#requires -Version 7.0
<#
.SYNOPSIS
    Reports actual quality-gate frequency from machine-readable gate telemetry.

.DESCRIPTION
    Aggregates records emitted by post-change.ps1 and assert-fast-gates.ps1. It refuses
    with exit 2 when no records exist, because an empty telemetry source is not evidence
    that every gate has zero invocations or zero failures.

    Columns: Executed counts the records where the gate actually ran; Skipped counts the ones
    where it did not apply; Failures counts FAIL and MISSING only. FailureRatePct and AverageMs
    are per EXECUTED run, so a gate that skips on most closures is not flattered by its skips.
    Runners names which runner produced the records - a gate showing only one runner is enforced
    on that runner's cadence alone.

    S1937: until 2026-08-22 only assert-fast-gates.ps1 wrote records, so the journal saw
    roughly 11 runs a day and missed the ~61 daily closures that execute the same gates.
    A report over that journal answered a different question than the one it was asked.

    S2537 -Placement: the frequency view answers "how often" but not "at what price", and the
    price is what decides where a gate belongs (CLAUDE.md Rule 33 - per ticket or release scope).
    The placement view adds the MEDIAN run, the typical total that median implies, the findings
    and the cost per finding, and marks candidates for review.

    It is median-based on purpose, and the reason is the measurement that opened S2537. Ranked by
    the mean, detekt-gate was the largest cost in the repository: 86 398 seconds across 751 runs,
    115 s each, 54% of all closure gate time. Ranked by the median it costs 11 MILLISECONDS - the
    clean-verdict cache answers almost every call - and 88.9% of its recorded time came from TEN
    runs, one of which was journalled at 34 711 s (9.6 hours) and is a stalled process, not
    analysis. A plan built on that mean would have relocated the cheapest gate in the closure and
    left the genuinely slow ones - settings-doc-sync at a 42.5 s median, catalog-sync at 10.6 s -
    exactly where they were.

    So the cost floor is applied to median x executions, never to the observed sum: one stall
    must not be able to nominate a gate, and must not be able to hide one either. The observed
    sum is still reported beside it, because the gap between the two IS the stall signal.

    The view PROPOSES and never moves anything. A candidate is a row a human then judges by the
    four-part Rule 33 test, whose exceptions (later work builds on the defect; the evidence exists
    only at the moment of the change; agents read the artifact between releases) no arithmetic
    over this journal can see.

.PARAMETER Filter
    Restrict the report to gate names matching this regular expression.

.PARAMETER Placement
    Emit the placement view - cost per finding per gate, sorted by total time spent, with
    placement candidates marked. Sorted by cost rather than by frequency because the decision
    it feeds is about time, and one expensive gate outweighs the whole tail of cheap ones.

.PARAMETER MinExecutions
    Placement view: the smallest number of executed runs a gate needs before it can be marked a
    candidate. Below it the sample says nothing - a gate that ran twice and found nothing is not
    evidence of a gate that finds nothing.

.PARAMETER MinTotalSeconds
    Placement view: the TYPICAL total (median run x executions) a gate must reach before it can be
    marked a candidate. A gate that has never found anything but costs fractions of a second stays
    where it is - relocating it costs more (two runners, the docs and the recovery hints) than it
    saves. Judged on the typical total rather than the observed sum so a single stalled run cannot
    nominate a gate that is free on every ordinary closure.

.PARAMETER MaxSecondsPerCatch
    Placement view: the cost per finding above which a gate that DOES find things is still marked
    a candidate.

.PARAMETER Journal
    Read this telemetry journal instead of the repository's own. Exists so the regression suite can
    judge fixed input: a suite reading live telemetry would pass or fail by whatever ran on the host
    that hour, which is the opposite of a contract.

.PARAMETER Json
    Emit the report as JSON.

.PARAMETER Help
    Show help documentation and usage.

.NOTES
    Exit codes:
      0  the report was produced.
      2  could not verify - the telemetry journal is absent, or holds no record matching -Filter.
#>
[CmdletBinding()]
param(
    [string]$Filter = '',
    [switch]$Placement,
    [int]$MinExecutions = 20,
    [int]$MinTotalSeconds = 600,
    [int]$MaxSecondsPerCatch = 300,
    [string]$Journal = '',
    [switch]$Json,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$telemetryPath = if ($Journal) { $Journal } else { Get-GateTelemetryPath }
if (-not (Test-Path -LiteralPath $telemetryPath)) {
    $message = "Gate telemetry is unavailable: $telemetryPath. Run post-change.ps1 or assert-fast-gates.ps1 first."
    if ($Json) {
        [ordered]@{ status = 'unavailable'; source = $telemetryPath; reason = $message } | ConvertTo-Json -Depth 3
    }
    Write-Error $message -ErrorAction Continue
    exit 2
}

$records = @(
    Get-Content -LiteralPath $telemetryPath | ForEach-Object {
        try { $_ | ConvertFrom-Json -ErrorAction Stop }
        catch { $null }
    } | Where-Object { $_ }
)
if ($Filter) {
    $records = @($records | Where-Object { $_.gate -match $Filter })
}

if ($records.Count -eq 0) {
    $message = "Gate telemetry contains no records matching '$Filter'."
    if ($Json) {
        [ordered]@{ status = 'unavailable'; source = $telemetryPath; reason = $message } | ConvertTo-Json -Depth 3
    }
    Write-Error $message -ErrorAction Continue
    exit 2
}

# S2537: two totals from different periods are not comparable, so every consumer of the placement
# view needs the window the numbers came from. A record without a parseable timestamp is dropped
# from the window rather than defaulting to now, which would silently widen it.
$stamps = @(
    $records | ForEach-Object {
        $raw = $null
        if ($_.PSObject.Properties.Name -contains 'timestampUtc') { $raw = $_.timestampUtc }
        if ($null -eq $raw) { return }
        try { [datetime]$raw } catch { return }
    }
)
$windowFrom = if ($stamps.Count -gt 0) { ($stamps | Measure-Object -Minimum).Minimum } else { $null }
$windowTo = if ($stamps.Count -gt 0) { ($stamps | Measure-Object -Maximum).Maximum } else { $null }

$report = @(
    $records | Group-Object gate | ForEach-Object {
        $group = @($_.Group)
        # S1937: a SKIP is not a failure. post-change.ps1 now journals its skips too - a gate
        # that did not apply to a change used to be indistinguishable here from one that found
        # a defect, which would have turned the report's own answer upside down: the gates that
        # skip most often are exactly the ones this report exists to identify as never firing.
        $skips = @($group | Where-Object { $_.status -eq 'SKIP' }).Count
        $failures = @($group | Where-Object { $_.status -eq 'FAIL' -or $_.status -eq 'MISSING' }).Count
        $executed = $group.Count - $skips
        $totalMs = [int]($group | Measure-Object -Property elapsedMs -Sum).Sum
        $totalSec = [Math]::Round($totalMs / 1000.0, 1)
        # S2537: the median is the gate's real per-closure price and the mean is not. Taken over
        # EXECUTED runs only - including the skips would report the cost of not running.
        $ranEntries = @($group | Where-Object { $_.status -ne 'SKIP' } | ForEach-Object { [int]$_.elapsedMs } | Sort-Object)
        $medianMs = if ($ranEntries.Count -gt 0) { $ranEntries[[int]($ranEntries.Count / 2)] } else { 0 }
        $p90Ms = if ($ranEntries.Count -gt 0) { $ranEntries[[Math]::Min($ranEntries.Count - 1, [int]($ranEntries.Count * 0.9))] } else { 0 }
        $medianSec = [Math]::Round($medianMs / 1000.0, 2)
        # What the gate costs across the window when nothing stalls. The gap between this and
        # TotalSec is the stall signal, which is why both are reported.
        $typicalSec = [Math]::Round(($medianMs * $executed) / 1000.0, 1)
        # S2537: a gate that found nothing reports no cost per finding at all. A sentinel number
        # would sort beside real ones and read as "the most expensive finding in the repo", which
        # is the opposite of what an empty column means.
        $secPerCatch = if ($failures -gt 0) { [Math]::Round($typicalSec / $failures, 1) } else { $null }
        $candidate = ($executed -ge $MinExecutions) -and
                     ($typicalSec -ge $MinTotalSeconds) -and
                     (($failures -eq 0) -or ($secPerCatch -gt $MaxSecondsPerCatch))
        [pscustomobject]@{
            Gate           = $_.Name
            Executed       = $executed
            Skipped        = $skips
            Failures       = $failures
            FailureRatePct = if ($executed -gt 0) { [Math]::Round(($failures * 100.0) / $executed, 2) } else { 0 }
            TotalMs        = $totalMs
            TotalSec       = $totalSec
            MedianMs       = $medianMs
            MedianSec      = $medianSec
            P90Ms          = $p90Ms
            TypicalSec     = $typicalSec
            SecPerCatch    = $secPerCatch
            Candidate      = $candidate
            AverageMs      = if ($executed -gt 0) { [Math]::Round($totalMs / $executed, 2) } else { 0 }
            Runners        = (@($group | Select-Object -ExpandProperty runner -Unique) | Sort-Object) -join '+'
        }
    }
)

$report = if ($Placement) {
    # Ranked by the stall-proof total, so the order matches the decision the view feeds.
    @($report | Sort-Object -Property TypicalSec -Descending)
} else {
    @($report | Sort-Object -Property Executed, Skipped, Gate -Descending)
}

if ($Json) {
    [ordered]@{
        status     = 'ok'
        source     = $telemetryPath
        records    = $records.Count
        windowFrom = if ($windowFrom) { $windowFrom.ToString('o') } else { $null }
        windowTo   = if ($windowTo) { $windowTo.ToString('o') } else { $null }
        thresholds = [ordered]@{
            minExecutions      = $MinExecutions
            minTotalSeconds    = $MinTotalSeconds
            maxSecondsPerCatch = $MaxSecondsPerCatch
        }
        gates      = $report
    } | ConvertTo-Json -Depth 4
    exit 0
}

Write-Host "Gate telemetry source: $telemetryPath" -ForegroundColor Cyan
Write-Host "Records: $($records.Count); gates: $($report.Count)" -ForegroundColor Cyan
if ($windowFrom -and $windowTo) {
    $span = $windowTo - $windowFrom
    Write-Host ("Journal window: {0:yyyy-MM-dd HH:mm} .. {1:yyyy-MM-dd HH:mm} UTC ({2:N1} days)" -f $windowFrom, $windowTo, $span.TotalDays) -ForegroundColor Cyan
}

if ($Placement) {
    Write-Host ("Placement thresholds: executions >= {0}, typical total >= {1}s, cost per finding > {2}s" -f $MinExecutions, $MinTotalSeconds, $MaxSecondsPerCatch) -ForegroundColor Cyan
    Write-Host "TypicalSec = median run x executions. A TotalSec far above it means the gate stalled, not that it is slow." -ForegroundColor DarkGray
    # The full runner name is 26 characters and pushes the numeric columns off an ordinary console,
    # where a truncated number reads as a different number. It stays intact in -Json.
    $report |
        Select-Object Gate,
            @{ Name = 'Runner'; Expression = { ($_.Runners -replace 'assert-', '' -replace '-gates', '' -replace '\.ps1', '') } },
            Executed, Failures, MedianSec, TypicalSec, TotalSec, SecPerCatch, Candidate |
        Format-Table -AutoSize
    $flagged = @($report | Where-Object { $_.Candidate })
    $flaggedSec = [Math]::Round((@($flagged | Measure-Object -Property TypicalSec -Sum).Sum), 1)
    Write-Host ("Placement candidates: {0} gate(s), {1}s of the window's typical gate time. Judge each by the CLAUDE.md Rule 33 four-part test - this view proposes, it moves nothing." -f $flagged.Count, $flaggedSec) -ForegroundColor Yellow
    exit 0
}

$report | Format-Table -AutoSize -Property Gate, Executed, Skipped, Failures, FailureRatePct, TotalMs, AverageMs, Runners
exit 0
