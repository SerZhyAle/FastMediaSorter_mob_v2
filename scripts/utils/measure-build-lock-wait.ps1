#requires -Version 7.0
<#
.SYNOPSIS
  Reconstructs how long fast checks waited for a Build.* domain, from the logs already in temp/.

.DESCRIPTION
  S2606. Nothing journals the build queue: a ticket is deleted the moment its turn comes, and
  lock-status.ps1 answers only for the present second. The history is recoverable anyway, because
  check-standard-fast.ps1 stamps its log file name BEFORE Enter-BuildLockOrExit and writes the
  'Date:' header line right AFTER the acquire - so the difference between the two is the time that
  run spent in the queue, recorded without any instrumentation existing for it.

  Reported: waits per mode, the split between the short and long hold classes (the same mode list
  check-standard-fast.ps1 uses for its queue reason), which class was holding the domain while a
  short check waited, runs that acquired the domain and then wrote nothing but their header, and
  any pair of runs that held one domain at the same time.

  Two blind spots, both from the method rather than from the queue. A holder that writes no
  fast-check log - assert-detekt.ps1, build-debug.PS1, a gradle assemble - can be waited for but
  never seen, so the waiting it caused is reported as unattributed rather than charged to it. And
  the end of a hold is read from the log's last write, which under-reports exactly the run that
  hangs after its last line: a hold is never over-reported, so a detected overlap is real while an
  absent one is only probably absent.

  S2612 added two things. The short class's wait is bucketed above a set of thresholds, because that
  is the question a foreground wait budget is chosen from and it was previously answered by
  re-deriving this corpus outside the tool; the operative budget is read out of
  scripts/builders/build-queue-refusal.ps1 rather than restated, so the marked row cannot drift from
  the number the refusal applies. And a run REFUSED for queue reasons is counted separately: it
  writes a log whose header carries `Queued:` where an acquired run carries `Date:`, so a refusal is
  distinguishable from both a completed run and a run killed while waiting. A refusal is never folded
  into the wait percentiles - it held the domain for no time at all, and averaging it in would make
  the queue look calmer the more often checks were turned away.

  Manual tool: run by hand when a change to the queue is being proposed, or when a stall is being
  diagnosed after the fact. Nothing calls it, it reads only temp/ and it writes nothing.

.PARAMETER LogDir
  Directory holding check_fast_*.log. Default: temp/ at the repository root.

.PARAMETER Since
  Ignore runs requested before this moment. Default: no lower bound.

.PARAMETER Top
  How many rows to print in each of the two detail lists. Default 10.

.PARAMETER Json
  Emit the aggregates as one JSON object instead of the tables.

.NOTES
  Exit codes:
    0 - measured
    1 - two runs held one build domain at the same time; mutual exclusion did not hold
    2 - could not verify: the log directory is missing, or holds no parsable run
#>
[CmdletBinding()]
param(
    [string]$LogDir,
    [datetime]$Since = [datetime]::MinValue,
    [int]$Top = 10,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

if (-not $LogDir) {
    $LogDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'temp'
}
if (-not (Test-Path -LiteralPath $LogDir -PathType Container)) {
    Write-Host "measure-build-lock-wait: log directory not found: $LogDir" -ForegroundColor Red
    exit 2
}

# The same three modes check-standard-fast.ps1 calls a LONG hold when it writes the queue reason.
# Duplicated deliberately rather than imported: that script is a 600-line gradle entry point, and
# dot-sourcing it to read one list would run its lock acquisition.
$longHoldModes = @('Unit', 'ConnectedAndroidTest', 'Assemble')
$namePattern = '^check_fast_(?<module>.+)_(?<mode>[A-Za-z]+)_(?<stamp>\d{8}_\d{6})\.log$'

$runs = [System.Collections.Generic.List[object]]::new()
$refusals = [System.Collections.Generic.List[object]]::new()
foreach ($file in Get-ChildItem -LiteralPath $LogDir -Filter 'check_fast_*.log' -File) {
    if ($file.Name -notmatch $namePattern) { continue }
    $mode = $Matches['mode']
    $module = $Matches['module']
    $requested = [datetime]::ParseExact($Matches['stamp'], 'yyyyMMdd_HHmmss', $null)
    if ($requested -lt $Since) { continue }

    $head = Get-Content -LiteralPath $file.FullName -TotalCount 12
    $dateLine = $head | Where-Object { $_.StartsWith('Date: ') } | Select-Object -First 1

    # S2612: a run refused for queue reasons writes `Queued:` where an acquired run writes `Date:`.
    # It never held the domain, so it has neither a wait nor a hold to measure - it is counted on
    # its own, as the outcome that REPLACED an unbounded wait rather than as a wait of length zero.
    # Averaging it into the wait percentiles would make the queue look calmer the more often checks
    # were turned away, which is the opposite of what the number is read for.
    if (-not $dateLine) {
        $queuedLine = $head | Where-Object { $_.StartsWith('Queued: ') } | Select-Object -First 1
        if ($queuedLine) {
            $refusals.Add([pscustomobject]@{
                Mode      = $mode
                Module    = $module
                Requested = $requested
                IsLong    = $longHoldModes -contains $mode
            })
        }
        # Anything else with no header never got past Enter-BuildLockOrExit - killed while queued.
        # There is no acquire moment to subtract, so it is not a measurable wait either way.
        continue
    }
    $acquired = [datetime]::ParseExact($dateLine.Substring(6).Trim(), 'yyyy-MM-dd HH:mm:ss', $null)
    $domainLine = $head | Where-Object { $_.StartsWith('Build Domains: ') } | Select-Object -First 1
    $domains = if ($domainLine) { $domainLine.Substring(15).Trim() } else { 'unknown' }

    $runs.Add([pscustomobject]@{
        Mode        = $mode
        Module      = $module
        Domains     = $domains
        Requested   = $requested
        Acquired    = $acquired
        Ended       = $file.LastWriteTime
        WaitSeconds = [math]::Round(($acquired - $requested).TotalSeconds)
        HoldSeconds = [math]::Round(($file.LastWriteTime - $acquired).TotalSeconds)
        IsLong      = $longHoldModes -contains $mode
        HeaderOnly  = $file.Length -lt 400
    })
}

if ($runs.Count -eq 0) {
    Write-Host "measure-build-lock-wait: no parsable check_fast_*.log in $LogDir" -ForegroundColor Red
    exit 2
}

$ordered = @($runs | Sort-Object Requested)

function Get-Percentile {
    param([double[]]$Values, [double]$Quantile)
    if ($Values.Count -eq 0) { return 0 }
    $sorted = @($Values | Sort-Object)
    $index = [math]::Min($sorted.Count - 1, [int][math]::Floor($Quantile * $sorted.Count))
    return [int]$sorted[$index]
}

function Get-ClassSummary {
    param([object[]]$Set, [string]$Label)
    $waits = @($Set | ForEach-Object { [double]$_.WaitSeconds })
    return [pscustomobject]@{
        Class      = $Label
        Runs       = $Set.Count
        WaitedAny  = @($Set | Where-Object { $_.WaitSeconds -gt 0 }).Count
        P50        = Get-Percentile -Values $waits -Quantile 0.5
        P90        = Get-Percentile -Values $waits -Quantile 0.9
        MaxWait    = if ($waits.Count) { [int]($waits | Measure-Object -Maximum).Maximum } else { 0 }
        TotalWait  = if ($waits.Count) { [int]($waits | Measure-Object -Sum).Sum } else { 0 }
    }
}

$short = @($ordered | Where-Object { -not $_.IsLong })
$long = @($ordered | Where-Object { $_.IsLong })

# Who held the domain at the moment a short check asked for it. A run is a candidate holder when
# the request falls inside its own [acquired, ended] window on the same domain; both classes are
# counted because a short check queues behind another short check just as readily.
$behindLong = 0; $behindShort = 0; $unattributed = 0
foreach ($run in $short) {
    if ($run.WaitSeconds -le 0) { continue }
    $holders = @($ordered | Where-Object {
        $_ -ne $run -and $_.Domains -eq $run.Domains -and
        $_.Acquired -le $run.Requested -and $run.Requested -le $_.Ended
    })
    if ($holders | Where-Object { $_.IsLong }) { $behindLong += $run.WaitSeconds }
    elseif ($holders.Count -gt 0) { $behindShort += $run.WaitSeconds }
    else { $unattributed += $run.WaitSeconds }
}

# Mutual exclusion. Ordered by acquire, so the scan stops at the first run starting after this one
# ended; anything before that on the same domain overlapped it. Five seconds of slack, because both
# ends are second-resolution and a handover legitimately lands inside the same second.
$byAcquire = @($ordered | Sort-Object Acquired)
$overlaps = [System.Collections.Generic.List[object]]::new()
for ($i = 0; $i -lt $byAcquire.Count; $i++) {
    $a = $byAcquire[$i]
    for ($j = $i + 1; $j -lt $byAcquire.Count; $j++) {
        $b = $byAcquire[$j]
        if ($b.Acquired -ge $a.Ended) { break }
        if ($a.Domains -ne $b.Domains -or $a.Domains -eq 'unknown') { continue }
        $seconds = [math]::Round((([datetime]::Compare($a.Ended, $b.Ended) -lt 0 ? $a.Ended : $b.Ended) - $b.Acquired).TotalSeconds)
        if ($seconds -gt 5) {
            $overlaps.Add([pscustomobject]@{ Domain = $a.Domains; First = $a; Second = $b; Seconds = $seconds })
        }
    }
}

# A short check whose queue time exceeded its own work is the shape this tool exists to size: the
# ten-second floor keeps out the runs where both numbers are rounding noise.
$waitDominated = @($short | Where-Object { $_.WaitSeconds -gt $_.HoldSeconds -and $_.WaitSeconds -gt 10 })
$dominatedWait = [int](($waitDominated | Measure-Object WaitSeconds -Sum).Sum)
$dominatedWork = [int](($waitDominated | Measure-Object HoldSeconds -Sum).Sum)

# S2612: how many short-class runs exceeded each wait threshold. This is the question the foreground
# budget is set from, and it used to be answered by re-deriving the corpus outside this tool - the
# workaround CLAUDE.md Rule 13 refuses. The budget row is marked because a bare list of thresholds
# does not say which one is operative, and reading the constant out of build-queue-refusal.ps1 is the
# step that was skipped when this was done by hand.
#
# The foreground budget is spent on wait PLUS run, not wait alone: a short target's own wall clock is
# 14-32 s (docs/BUILD_TEST_FAST_PATH.md), so the 90 s row is nearer the real survival line than the
# 120 s one, and both are printed rather than one being chosen here.
$budgetSeconds = 60
$refusalHelper = Join-Path (Split-Path -Parent $PSScriptRoot) 'builders\build-queue-refusal.ps1'
if (Test-Path -LiteralPath $refusalHelper) {
    # Read the constant rather than restating it: two copies would let the reported "budget" drift
    # from the one the refusal actually applies, and this table exists to judge that exact number.
    $budgetMatch = Select-String -LiteralPath $refusalHelper -Pattern 'BuildQueueForegroundBudgetSeconds\s*=\s*(\d+)' | Select-Object -First 1
    if ($budgetMatch) { $budgetSeconds = [int]$budgetMatch.Matches[0].Groups[1].Value }
}
$thresholds = @(15, 30, 45, 60, 90, 120, 300)
if ($thresholds -notcontains $budgetSeconds) { $thresholds = @($thresholds + $budgetSeconds | Sort-Object) }
$waitBuckets = @(foreach ($t in $thresholds) {
    $over = @($short | Where-Object { $_.WaitSeconds -gt $t })
    [pscustomobject]@{
        OverSeconds = $t
        Runs        = $over.Count
        PctOfShort  = if ($short.Count) { [math]::Round(100.0 * $over.Count / $short.Count, 1) } else { 0 }
        IsBudget    = ($t -eq $budgetSeconds)
    }
})

$abandoned = @($ordered | Where-Object { $_.HeaderOnly } | Sort-Object WaitSeconds -Descending)
$summaries = @((Get-ClassSummary -Set $short -Label 'short'), (Get-ClassSummary -Set $long -Label 'long'))
$totalShortWait = ($summaries | Where-Object Class -eq 'short').TotalWait

if ($Json) {
    [pscustomobject]@{
        logDir           = $LogDir
        runs             = $ordered.Count
        firstRequest     = $ordered[0].Requested
        lastRequest      = $ordered[-1].Requested
        classes          = $summaries
        shortWaitBehind  = [pscustomobject]@{ longHolder = $behindLong; shortHolder = $behindShort; unattributed = $unattributed }
        waitDominated    = [pscustomobject]@{ runs = $waitDominated.Count; wait = $dominatedWait; work = $dominatedWork }
        shortWaitBuckets = $waitBuckets
        foregroundBudget = $budgetSeconds
        refusals         = [pscustomobject]@{
            total = $refusals.Count
            short = @($refusals | Where-Object { -not $_.IsLong }).Count
        }
        abandonedRuns    = $abandoned.Count
        overlappingHolds = $overlaps.Count
    } | ConvertTo-Json -Depth 5
}
else {
    Write-Host ""
    Write-Host "Build-domain queue reconstructed from $($ordered.Count) runs in $LogDir" -ForegroundColor Cyan
    Write-Host "  span: $($ordered[0].Requested.ToString('yyyy-MM-dd HH:mm')) .. $($ordered[-1].Requested.ToString('yyyy-MM-dd HH:mm'))"
    Write-Host ""

    Write-Host "Per mode" -ForegroundColor Cyan
    $ordered | Group-Object Mode | Sort-Object Count -Descending | ForEach-Object {
        $waits = @($_.Group | ForEach-Object { [double]$_.WaitSeconds })
        $holds = @($_.Group | ForEach-Object { [double]$_.HoldSeconds })
        [pscustomobject]@{
            Mode      = $_.Name
            Class     = if ($longHoldModes -contains $_.Name) { 'long' } else { 'short' }
            Runs      = $_.Count
            WaitP50   = Get-Percentile -Values $waits -Quantile 0.5
            WaitP90   = Get-Percentile -Values $waits -Quantile 0.9
            WaitMax   = [int]($waits | Measure-Object -Maximum).Maximum
            HoldP50   = Get-Percentile -Values $holds -Quantile 0.5
            HoldMax   = [int]($holds | Measure-Object -Maximum).Maximum
        }
    } | Format-Table -AutoSize | Out-String | Write-Host

    Write-Host "Per hold class" -ForegroundColor Cyan
    $summaries | Format-Table -AutoSize | Out-String | Write-Host

    Write-Host "Short-class wait above each threshold (foreground budget ${budgetSeconds}s)" -ForegroundColor Cyan
    foreach ($bucket in $waitBuckets) {
        $mark = if ($bucket.IsBudget) { '<- budget' } else { '' }
        $line = "  over {0,4}s : {1,4} runs  {2,5}% of {3} short  {4}" -f `
            $bucket.OverSeconds, $bucket.Runs, $bucket.PctOfShort, $short.Count, $mark
        Write-Host $line -ForegroundColor $(if ($bucket.IsBudget) { 'Yellow' } else { 'Gray' })
    }
    Write-Host "  A short target's own wall clock is 14-32s, so the survivable wait is 88-105s - not 120s." -ForegroundColor DarkGray
    Write-Host ""

    Write-Host "Refused rather than blocked (S2612): $($refusals.Count)" -ForegroundColor Cyan
    Write-Host "  These held the domain for 0s and returned a verdict-less exit 4 in seconds, instead of" -ForegroundColor DarkGray
    Write-Host "  blocking until the caller's 120s timeout killed them with no verdict at all." -ForegroundColor DarkGray
    Write-Host ""

    Write-Host "What a short check was waiting behind (of ${totalShortWait}s total)" -ForegroundColor Cyan
    foreach ($pair in @(@('a LONG hold', $behindLong), @('another SHORT hold', $behindShort), @('unattributed - a holder writing no fast-check log, or a deeper queue', $unattributed))) {
        $share = if ($totalShortWait -gt 0) { [math]::Round(100 * $pair[1] / $totalShortWait) } else { 0 }
        Write-Host ("  {0,6}s  {1,3}%  {2}" -f $pair[1], $share, $pair[0])
    }
    Write-Host ""

    $dominatedShare = if ($short.Count) { [math]::Round(100 * $waitDominated.Count / $short.Count) } else { 0 }
    Write-Host "Short checks that queued longer than they worked: $($waitDominated.Count) of $($short.Count) ($dominatedShare%)" -ForegroundColor Cyan
    Write-Host ("  {0}s waiting against {1}s of work" -f $dominatedWait, $dominatedWork)
    Write-Host ""

    Write-Host "Acquired the domain and then wrote nothing but the header: $($abandoned.Count)" -ForegroundColor Cyan
    $abandoned | Select-Object -First $Top | ForEach-Object {
        Write-Host ("  {0}  {1,-18} waited {2}s" -f $_.Requested.ToString('yyyy-MM-dd HH:mm:ss'), $_.Mode, $_.WaitSeconds)
    }
    Write-Host ""

    if ($overlaps.Count -gt 0) {
        Write-Host "Two runs held one domain at once - mutual exclusion did not hold: $($overlaps.Count)" -ForegroundColor Red
        $overlaps | Select-Object -First $Top | ForEach-Object {
            Write-Host ("  {0}: {1} at {2} vs {3} at {4}, overlap {5}s" -f $_.Domain, $_.First.Mode,
                $_.First.Acquired.ToString('HH:mm:ss'), $_.Second.Mode, $_.Second.Acquired.ToString('HH:mm:ss'), $_.Seconds)
        }
    }
    else {
        Write-Host "Overlapping holds of one domain: none" -ForegroundColor Green
    }
    Write-Host ""
}

if ($overlaps.Count -gt 0) { exit 1 }
exit 0
