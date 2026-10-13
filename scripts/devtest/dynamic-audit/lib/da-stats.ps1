#requires -Version 7.0
<#
.SYNOPSIS
    Statistics and run-validity library of the S4166 dynamic-audit stand: percentiles, block-bootstrap slope, budget verdict, validity.

.DESCRIPTION
    Dot-source after lib/da-common.ps1. Functions only; nothing is assigned at script scope.

      Get-DaDefaultThresholds  the strategic hypothesis values (S4166 section 5.3) that Calibrate copies
                               into a calibration record: scheduler lateness p99 above 10 ms, more than
                               1 percent missed probes or more than 2 percent unplanned sampler gaps make
                               a run invalid; a p99 needs at least 1000 valid probes
      Get-DaBootstrapDefaults  fixed seed, repeat count and confidence of the block bootstrap
      Get-DaPercentiles        p50, p95, p99, max, count, loss and timeout share; losses and timeouts rank
                               ABOVE every valid value, so a percentile that falls into them is censored
                               (null) instead of silently improved; flag insufficient-for-p99 below the
                               valid-probe minimum
      Get-DaSlopeCI            least-squares slope over time and over cycles with a 95 percent interval
                               from a moving-block residual bootstrap; seed, block length and repeat count
                               are returned with the result
      Get-DaBudgetVerdict      pass only when the upper bound is below the budget (inside the observed
                               window), fail when the lower bound is above it, inconclusive when the
                               interval crosses it
      Test-DaRunValidity       names every invalid-run cause against the thresholds of a calibration
                               record; returns the refused exit code when any cause is present
      Get-DaPairedSummary      pairwise differences (subject minus control) of a metric by pair index
      Get-DaGrowthDecision     plateau versus retained growth at the measured cycle points: retained only when
                               the slope interval is above zero over the whole window and its second half
      Invoke-DaRunAnalysis     the Analyze verb: validity, per-segment distributions, slopes with intervals,
                               the plateau decision and the B09/B10 budget verdicts into analysis.json; a
                               metric never observed is open, never zero

    WHY THRESHOLDS COME FROM THE CALIBRATION RECORD. The spec fixes that the invalid-run limits are
    calibrated on the base and written down BEFORE the application is checked. A call site that carried
    its own number could quietly loosen a limit after a bad run, so Test-DaRunValidity reads the record
    and refuses when it has none.

    WHY FAILURES RANK ABOVE VALID VALUES. A lost key or a timed-out start is part of the violation share
    (section 5.5). Dropping it would make the tail look better exactly when the subject is worse.

    WHY A BLOCK BOOTSTRAP. Consecutive resource samples are autocorrelated; resampling single residuals
    would understate the interval. Moving blocks of consecutive residuals keep that structure.
#>

function Get-DaDefaultThresholds {
    <#
    .SYNOPSIS
        Invalid-run thresholds as strategic hypotheses (S4166 section 5.3); Calibrate records them, call sites never repeat them.
    #>
    return [ordered]@{
        schedulerLatenessP99Ms = 10.0
        missedProbeShareMax    = 0.01
        samplerGapShareMax     = 0.02
        minValidForP99         = 1000
        source                 = 'strategic-hypothesis-5.3'
    }
}

function Get-DaBootstrapDefaults {
    <#
    .SYNOPSIS
        Fixed defaults of the block bootstrap; the block length defaults to ceil(n^(1/3)) per series.
    #>
    return [ordered]@{ seed = 4166; repeats = 1000; confidence = 0.95; blockLength = $null }
}

function Get-DaNearestRank {
    <#
    .SYNOPSIS
        Nearest-rank percentile index (0-based) in a sorted list of Total items.
    .PARAMETER Percent
        Percentile in 0..100.
    .PARAMETER Total
        List length.
    #>
    param([double]$Percent, [int]$Total)
    $k = [int][math]::Ceiling(($Percent / 100.0) * $Total)
    if ($k -lt 1) { $k = 1 }
    if ($k -gt $Total) { $k = $Total }
    return ($k - 1)
}

function Get-DaPercentiles {
    <#
    .SYNOPSIS
        Distribution summary with failures ranked into the tail; returns an ordered dictionary.
    .PARAMETER Values
        Valid measurements (one unit, e.g. milliseconds).
    .PARAMETER Lost
        Probes that were lost (never delivered).
    .PARAMETER Timeouts
        Probes that timed out or got no reaction.
    .PARAMETER MinValidForP99
        Valid-probe count below which p99 carries the insufficient-for-p99 flag.
    #>
    param(
        [double[]]$Values = @(),
        [int]$Lost = 0,
        [int]$Timeouts = 0,
        [int]$MinValidForP99 = 1000
    )
    $sorted = [double[]]@($Values | Sort-Object)
    $count = $sorted.Length
    $failures = $Lost + $Timeouts
    $total = $count + $failures
    $flags = [System.Collections.Generic.List[string]]::new()
    $censored = [System.Collections.Generic.List[string]]::new()
    $result = [ordered]@{
        count = $count; total = $total; lost = $Lost; timeouts = $Timeouts
        lossShare = $null; timeoutShare = $null; failureShare = $null
        min = $null; mean = $null; p50 = $null; p95 = $null; p99 = $null; max = $null
        censored = @(); flags = @()
    }
    if ($total -eq 0) {
        $result.flags = @('empty', 'insufficient-for-p99')
        return $result
    }
    $result.lossShare = [math]::Round($Lost / $total, 6)
    $result.timeoutShare = [math]::Round($Timeouts / $total, 6)
    $result.failureShare = [math]::Round($failures / $total, 6)
    if ($count -gt 0) {
        $result.min = $sorted[0]
        $result.max = $sorted[$count - 1]
        $result.mean = [math]::Round((($sorted | Measure-Object -Sum).Sum / $count), 3)
    }
    foreach ($p in @(@('p50', 50.0), @('p95', 95.0), @('p99', 99.0))) {
        $idx = Get-DaNearestRank -Percent $p[1] -Total $total
        if ($idx -lt $count) { $result[$p[0]] = $sorted[$idx] }
        else { $censored.Add($p[0]) }
    }
    if ($failures -gt 0) { $censored.Add('max') }
    if ($count -lt $MinValidForP99) { $flags.Add('insufficient-for-p99') }
    $result.censored = $censored.ToArray()
    $result.flags = $flags.ToArray()
    return $result
}

function Get-DaOlsFit {
    <#
    .SYNOPSIS
        Ordinary least-squares line; returns {Slope; Intercept; Residuals; Dx; Sxx} or $null when X has no spread.
    .PARAMETER X
        Abscissa.
    .PARAMETER Y
        Ordinate, same length as X.
    #>
    param([double[]]$X, [double[]]$Y)
    $n = $X.Length
    $mx = 0.0; $my = 0.0
    for ($i = 0; $i -lt $n; $i++) { $mx += $X[$i]; $my += $Y[$i] }
    $mx /= $n; $my /= $n
    $dx = [double[]]::new($n)
    $sxx = 0.0; $sxy = 0.0
    for ($i = 0; $i -lt $n; $i++) {
        $dx[$i] = $X[$i] - $mx
        $sxx += $dx[$i] * $dx[$i]
        $sxy += $dx[$i] * ($Y[$i] - $my)
    }
    if ($sxx -le 0) { return $null }
    $slope = $sxy / $sxx
    $intercept = $my - $slope * $mx
    $res = [double[]]::new($n)
    for ($i = 0; $i -lt $n; $i++) { $res[$i] = $Y[$i] - ($intercept + $slope * $X[$i]) }
    return [pscustomobject]@{ Slope = $slope; Intercept = $intercept; Residuals = $res; Dx = $dx; Sxx = $sxx }
}

function Invoke-DaBlockBootstrapSlope {
    <#
    .SYNOPSIS
        Slope with a moving-block residual bootstrap interval for one axis; returns an ordered dictionary.
    .PARAMETER X
        Abscissa (time or cycle).
    .PARAMETER Y
        Ordinate.
    .PARAMETER Seed
        System.Random seed.
    .PARAMETER BlockLength
        Block length; 0 means ceil(n^(1/3)).
    .PARAMETER Repeats
        Bootstrap repeat count.
    .PARAMETER Confidence
        Two-sided interval level.
    #>
    param([double[]]$X, [double[]]$Y, [int]$Seed, [int]$BlockLength, [int]$Repeats, [double]$Confidence)
    $n = $X.Length
    $L = if ($BlockLength -gt 0) { $BlockLength } else { [int][math]::Max(1, [math]::Ceiling([math]::Pow($n, 1.0 / 3.0))) }
    if ($L -gt $n) { $L = $n }
    $out = [ordered]@{
        n = $n; slope = $null; intercept = $null; lower = $null; upper = $null; growth = 'unknown'
        seed = $Seed; blockLength = $L; repeats = $Repeats; confidence = $Confidence
        blocks = [int][math]::Floor($n / [math]::Max(1, $L)); method = 'moving-block residual bootstrap, percentile interval'; flags = @()
    }
    if ($n -lt 3) { $out.flags = @('too-few-points'); return $out }
    $fit = Get-DaOlsFit -X $X -Y $Y
    if (-not $fit) { $out.flags = @('no-spread-in-x'); return $out }
    $rng = [System.Random]::new($Seed)
    $slopes = [double[]]::new($Repeats)
    $res = $fit.Residuals; $dx = $fit.Dx; $sxx = $fit.Sxx
    $maxStart = $n - $L + 1
    for ($r = 0; $r -lt $Repeats; $r++) {
        # With X fixed, slope* = slope + sum(dx_i * resampled_residual_i) / Sxx.
        $acc = 0.0
        $pos = 0
        while ($pos -lt $n) {
            $s = $rng.Next(0, $maxStart)
            for ($j = 0; $j -lt $L -and $pos -lt $n; $j++) {
                $acc += $dx[$pos] * $res[$s + $j]
                $pos++
            }
        }
        $slopes[$r] = $fit.Slope + $acc / $sxx
    }
    [System.Array]::Sort($slopes)
    $alpha = 1.0 - $Confidence
    $lo = Get-DaNearestRank -Percent (100.0 * $alpha / 2.0) -Total $Repeats
    $hi = Get-DaNearestRank -Percent (100.0 * (1.0 - $alpha / 2.0)) -Total $Repeats
    $out.slope = $fit.Slope
    $out.intercept = $fit.Intercept
    $out.lower = $slopes[$lo]
    $out.upper = $slopes[$hi]
    $out.growth = if ($out.lower -gt 0) { 'growing' } elseif ($out.upper -lt 0) { 'shrinking' } else { 'none-detected' }
    if ($out.blocks -lt 30) { $out.flags = @('fewer-than-30-blocks') }
    return $out
}

function Get-DaSlopeCI {
    <#
    .SYNOPSIS
        Least-squares slope over time and over cycles with a block-bootstrap interval; returns {time; cycle; warmupDropped}.
    .PARAMETER Y
        Measured series (e.g. PSS in KiB at the same cycle point).
    .PARAMETER Time
        Time of each point (e.g. minutes since the end of warm-up); omitted means no time fit.
    .PARAMETER Cycle
        Cycle index of each point; omitted means no cycle fit.
    .PARAMETER WarmupCount
        Leading points dropped as the explicit warm-up before fitting.
    .PARAMETER Seed
        Bootstrap seed; defaults to the fixed seed of Get-DaBootstrapDefaults.
    .PARAMETER BlockLength
        Block length; 0 means ceil(n^(1/3)).
    .PARAMETER Repeats
        Bootstrap repeat count.
    .PARAMETER Confidence
        Two-sided interval level.
    #>
    param(
        [Parameter(Mandatory)][double[]]$Y,
        [double[]]$Time,
        [double[]]$Cycle,
        [int]$WarmupCount = 0,
        [int]$Seed = -1,
        [int]$BlockLength = 0,
        [int]$Repeats = 0,
        [double]$Confidence = 0
    )
    $d = Get-DaBootstrapDefaults
    if ($Seed -lt 0) { $Seed = $d.seed }
    if ($Repeats -le 0) { $Repeats = $d.repeats }
    if ($Confidence -le 0) { $Confidence = $d.confidence }
    $n = $Y.Length
    $from = [math]::Min($WarmupCount, $n)
    $take = { param([double[]]$a) if ($null -eq $a -or $a.Length -eq 0) { return $null }; if ($a.Length -ne $n) { throw "series length $($a.Length) differs from Y length $n" }; return [double[]]$a[$from..($n - 1)] }
    $yy = if ($from -lt $n) { [double[]]$Y[$from..($n - 1)] } else { [double[]]@() }
    $result = [ordered]@{ time = $null; cycle = $null; warmupDropped = $from }
    if ($Time -and $Time.Length) {
        $tt = & $take $Time
        $result.time = Invoke-DaBlockBootstrapSlope -X $tt -Y $yy -Seed $Seed -BlockLength $BlockLength -Repeats $Repeats -Confidence $Confidence
    }
    if ($Cycle -and $Cycle.Length) {
        $cc = & $take $Cycle
        $result.cycle = Invoke-DaBlockBootstrapSlope -X $cc -Y $yy -Seed $Seed -BlockLength $BlockLength -Repeats $Repeats -Confidence $Confidence
    }
    return $result
}

function Get-DaBudgetVerdict {
    <#
    .SYNOPSIS
        Budget verdict from an interval: pass (upper below budget, observed window only), fail (lower above), inconclusive (crossing).
    .PARAMETER Lower
        Lower interval bound; $null makes the verdict inconclusive.
    .PARAMETER Upper
        Upper interval bound; $null makes the verdict inconclusive.
    .PARAMETER Budget
        Budget in the same unit.
    #>
    param($Lower, $Upper, [Parameter(Mandatory)][double]$Budget)
    $verdict = 'inconclusive'
    $why = 'the interval crosses the budget'
    if ($null -eq $Lower -or $null -eq $Upper) { $why = 'no interval' }
    elseif ([double]$Upper -lt $Budget) { $verdict = 'pass'; $why = 'upper bound below budget; holds only inside the observed window' }
    elseif ([double]$Lower -gt $Budget) { $verdict = 'fail'; $why = 'lower bound above budget' }
    return [ordered]@{ verdict = $verdict; lower = $Lower; upper = $Upper; budget = $Budget; scope = 'observed-window'; reason = $why }
}

function Test-DaRunValidity {
    <#
    .SYNOPSIS
        Judges a run against the thresholds of a calibration record; returns {Valid; Code; Causes; Measured; Thresholds}.
    .DESCRIPTION
        Measurements fields: schedulerLatenessMs (double[]), plannedProbes, missedProbes, samplerTicks
        (rows plus gap periods), samplerGaps (unplanned gap periods), and the change details powerChange,
        frequencyChange, foregroundChange and processRestart (each $null when nothing changed).
    .PARAMETER Measurements
        What the run observed about itself.
    .PARAMETER Calibration
        The calibration record; its thresholds are the only limits applied.
    #>
    param([Parameter(Mandatory)]$Measurements, $Calibration)
    $codes = Get-DaExitCode
    $causes = [System.Collections.Generic.List[object]]::new()
    $add = { param($id, $detail) $causes.Add([ordered]@{ id = $id; detail = $detail }) }
    $th = if ($Calibration) { $Calibration.thresholds } else { $null }
    if (-not $th -or $null -eq $th.schedulerLatenessP99Ms -or $null -eq $th.missedProbeShareMax -or $null -eq $th.samplerGapShareMax) {
        & $add 'calibration-missing' 'no calibration record with thresholds; run -Verb Calibrate before any application run'
        return [pscustomobject]@{ Valid = $false; Code = $codes.Refused; Causes = $causes.ToArray(); Measured = $null; Thresholds = $null }
    }
    $m = $Measurements
    $lat = Get-DaPercentiles -Values ([double[]]@($m.schedulerLatenessMs)) -MinValidForP99 ([int]$th.minValidForP99)
    $planned = [int]$m.plannedProbes
    $missed = [int]$m.missedProbes
    $missedShare = if ($planned -gt 0) { $missed / $planned } else { $null }
    $ticks = [int]$m.samplerTicks
    $gapShare = if ($ticks -gt 0) { [int]$m.samplerGaps / $ticks } else { $null }
    if ($planned -le 0) { & $add 'no-probes' 'the run planned no scheduled probes, so its own lateness cannot be judged' }
    if ($null -ne $lat.p99 -and [double]$lat.p99 -gt [double]$th.schedulerLatenessP99Ms) {
        & $add 'scheduler-lateness' ("scheduler lateness p99 {0} ms > {1} ms" -f $lat.p99, $th.schedulerLatenessP99Ms)
    }
    if ($null -ne $missedShare -and $missedShare -gt [double]$th.missedProbeShareMax) {
        & $add 'missed-probes' ("{0} of {1} planned probes missed ({2:P2} > {3:P2})" -f $missed, $planned, $missedShare, [double]$th.missedProbeShareMax)
    }
    if ($null -ne $gapShare -and $gapShare -gt [double]$th.samplerGapShareMax) {
        & $add 'sampler-gaps' ("{0} of {1} sampler ticks were unplanned gaps ({2:P2} > {3:P2})" -f $m.samplerGaps, $ticks, $gapShare, [double]$th.samplerGapShareMax)
    }
    foreach ($pair in @(@('powerChange', 'power-change'), @('frequencyChange', 'frequency-change'), @('foregroundChange', 'foreground-change'), @('processRestart', 'process-restarted'))) {
        $v = $m.($pair[0])
        if ($v) { & $add $pair[1] ([string]$v) }
    }
    $valid = ($causes.Count -eq 0)
    return [pscustomobject]@{
        Valid      = $valid
        Code       = if ($valid) { $codes.Ok } else { $codes.Refused }
        Causes     = $causes.ToArray()
        Measured   = [ordered]@{ schedulerLateness = $lat; missedShare = $missedShare; samplerGapShare = $gapShare; plannedProbes = $planned; missedProbes = $missed; samplerTicks = $ticks; samplerGaps = [int]$m.samplerGaps }
        Thresholds = $th
    }
}

function Get-DaPairedSummary {
    <#
    .SYNOPSIS
        Pairwise differences of one metric (subject minus control) matched by pair index; returns an ordered dictionary.
    .PARAMETER Records
        Records carrying pair, target and the metric property.
    .PARAMETER Metric
        Property name of the metric.
    .PARAMETER Subject
        Target id of the subject.
    .PARAMETER Control
        Target id of the control.
    #>
    param([object[]]$Records, [Parameter(Mandatory)][string]$Metric, [string]$Subject = 'app', [string]$Control = 'control')
    $byPair = @{}
    foreach ($r in @($Records)) {
        $v = $r.$Metric
        if ($null -eq $v) { continue }
        $k = [int]$r.pair
        if (-not $byPair.ContainsKey($k)) { $byPair[$k] = @{} }
        $byPair[$k][[string]$r.target] = [double]$v
    }
    $diffs = [System.Collections.Generic.List[double]]::new()
    foreach ($k in ($byPair.Keys | Sort-Object)) {
        $p = $byPair[$k]
        if ($p.ContainsKey($Subject) -and $p.ContainsKey($Control)) { $diffs.Add($p[$Subject] - $p[$Control]) }
    }
    return [ordered]@{ metric = $Metric; subject = $Subject; control = $Control; pairs = $diffs.Count; difference = (Get-DaPercentiles -Values $diffs.ToArray()) }
}

function Get-DaGrowthBudgets {
    <#
    .SYNOPSIS
        Growth budgets of the first pass, copied from audit/budgets.json B09 and B10 (hypothesis h1): memory 0.1 MiB per cycle and 1 MiB per hour on the upper 95 percent bound, objects 1 per hour and a plateau band of 2.
    #>
    return [ordered]@{
        memoryKiBPerCycle = 102.4
        memoryKiBPerHour  = 1024.0
        objectsPerHour    = 1.0
        plateauBand       = 2.0
        source            = 'PLAN/S4166_deep-dynamic-audit/audit/budgets.json B09, B10 (version h1, hypothesis)'
    }
}

function Get-DaGrowthDecision {
    <#
    .SYNOPSIS
        Plateau versus retained growth of one metric at the measured cycle points; returns {decision; reason; whole; secondHalf}.
    .DESCRIPTION
        retained-growth  the 95 percent slope interval over cycles lies above zero over the whole window AND over
                         its second half - the metric keeps climbing after warm-up;
        plateau          no growth detected over the whole window, or growth only in its first half (a bounded
                         cache filling up, then flat);
        insufficient     fewer than 3 points in the window.
        The decision holds only inside the observed window; it never claims absence beyond it.
    .PARAMETER Y
        Metric at the measured cycle points, warm-up already excluded.
    .PARAMETER Cycle
        Cycle index of each point.
    .PARAMETER Time
        Time of each point in hours from the first measured point.
    .PARAMETER Seed
        Bootstrap seed; the fixed default when negative.
    #>
    param([Parameter(Mandatory)][AllowEmptyCollection()][double[]]$Y, [Parameter(Mandatory)][AllowEmptyCollection()][double[]]$Cycle, [double[]]$Time, [int]$Seed = -1)
    $n = $Y.Length
    $out = [ordered]@{ decision = 'insufficient'; reason = "only $n measured points"; whole = $null; secondHalf = $null }
    if ($n -lt 3) { return $out }
    $out.whole = Get-DaSlopeCI -Y $Y -Cycle $Cycle -Time $Time -Seed $Seed
    $from = [int][math]::Floor($n / 2)
    if ($n - $from -lt 3) { $from = $n - 3 }
    $yy = [double[]]$Y[$from..($n - 1)]
    $cc = [double[]]$Cycle[$from..($n - 1)]
    $tt = if ($Time -and $Time.Length -eq $n) { [double[]]$Time[$from..($n - 1)] } else { $null }
    $out.secondHalf = Get-DaSlopeCI -Y $yy -Cycle $cc -Time $tt -Seed $Seed
    $w = $out.whole.cycle
    $h = $out.secondHalf.cycle
    $r3 = { param($x) if ($null -eq $x) { 'n/a' } else { [math]::Round([double]$x, 3) } }
    if ($null -eq $w.slope) { $out.reason = "no slope: $(@($w.flags) -join ',')"; return $out }
    if ($w.growth -ne 'growing') {
        $out.decision = 'plateau'
        $out.reason = "whole-window slope $(& $r3 $w.slope) per cycle, interval [$(& $r3 $w.lower), $(& $r3 $w.upper)] includes or lies below zero"
        return $out
    }
    if ($null -ne $h.slope -and $h.growth -ne 'growing') {
        $out.decision = 'plateau'
        $out.reason = "growth only in the first half: second-half interval [$(& $r3 $h.lower), $(& $r3 $h.upper)] includes or lies below zero"
        return $out
    }
    $out.decision = 'retained-growth'
    $out.reason = "slope interval above zero over the whole window [$(& $r3 $w.lower), $(& $r3 $w.upper)] and over its second half [$(& $r3 $h.lower), $(& $r3 $h.upper)]"
    return $out
}

function Get-DaCycleMetricDefinitions {
    <#
    .SYNOPSIS
        The resource metrics read at every cycle point: name, unit, class (memory or objects) and the property path in the sample row.
    #>
    return @(
        [ordered]@{ name = 'totalPssKb'; unit = 'KiB'; class = 'memory'; path = 'meminfo.totalPssKb' }
        [ordered]@{ name = 'javaPssKb'; unit = 'KiB'; class = 'memory'; path = 'meminfo.javaPssKb' }
        [ordered]@{ name = 'nativePssKb'; unit = 'KiB'; class = 'memory'; path = 'meminfo.nativePssKb' }
        [ordered]@{ name = 'graphicsPssKb'; unit = 'KiB'; class = 'memory'; path = 'meminfo.graphicsPssKb' }
        [ordered]@{ name = 'vmRssKb'; unit = 'KiB'; class = 'memory'; path = 'vmRssKb' }
        [ordered]@{ name = 'threads'; unit = 'threads'; class = 'objects'; path = 'threads' }
        [ordered]@{ name = 'fdCount'; unit = 'descriptors'; class = 'objects'; path = 'fdCount' }
        [ordered]@{ name = 'views'; unit = 'views'; class = 'objects'; path = 'meminfo.views' }
        [ordered]@{ name = 'activities'; unit = 'activities'; class = 'objects'; path = 'meminfo.activities' }
        [ordered]@{ name = 'appContexts'; unit = 'contexts'; class = 'objects'; path = 'meminfo.appContexts' }
        [ordered]@{ name = 'localBinders'; unit = 'binders'; class = 'objects'; path = 'meminfo.localBinders' }
        [ordered]@{ name = 'proxyBinders'; unit = 'binders'; class = 'objects'; path = 'meminfo.proxyBinders' }
    )
}

function Get-DaPathValue {
    <#
    .SYNOPSIS
        Value at a dotted property path of an object, or $null. Pure.
    .PARAMETER Object
        Source object.
    .PARAMETER Path
        Dotted path such as meminfo.totalPssKb.
    #>
    param($Object, [Parameter(Mandatory)][string]$Path)
    $v = $Object
    foreach ($part in $Path.Split('.')) {
        if ($null -eq $v) { return $null }
        $v = $v.$part
    }
    return $v
}

function Get-DaSegmentAnalysis {
    <#
    .SYNOPSIS
        Metrics and timings of one segment's measured cycle points; returns {Segment; Verdicts; Open}.
    .PARAMETER Name
        Segment name.
    .PARAMETER Points
        Measured cycle rows of the segment (warm-up excluded), in cycle order.
    .PARAMETER Budgets
        Get-DaGrowthBudgets output.
    #>
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][object[]]$Points, [Parameter(Mandatory)]$Budgets)
    $verdicts = [System.Collections.Generic.List[object]]::new()
    $open = [System.Collections.Generic.List[object]]::new()
    $t0 = [double]$Points[0].pointMonoMs
    $segOut = [ordered]@{ points = $Points.Count; cycles = @($Points | ForEach-Object { [int]$_.index }); windowMs = [double]$Points[-1].pointMonoMs - $t0; metrics = [ordered]@{}; timings = [ordered]@{} }
    foreach ($m in (Get-DaCycleMetricDefinitions)) {
        $pairs = @(foreach ($r in $Points) { $v = Get-DaPathValue -Object $r.point -Path $m.path; if ($null -ne $v) { [pscustomobject]@{ y = [double]$v; c = [double]$r.index; t = ([double]$r.pointMonoMs - $t0) / 3600000.0 } } })
        if (-not $pairs.Count) {
            $open.Add([ordered]@{ segment = $Name; metric = $m.name; reason = "not observed at any cycle point ($($m.path) empty)" })
            $segOut.metrics[$m.name] = [ordered]@{ unit = $m.unit; class = $m.class; state = 'open' }
            continue
        }
        $y = [double[]]@($pairs.y); $c = [double[]]@($pairs.c); $t = [double[]]@($pairs.t)
        $dec = Get-DaGrowthDecision -Y $y -Cycle $c -Time $t
        $mo = [ordered]@{
            unit = $m.unit; class = $m.class; state = 'measured'; n = $y.Length; distribution = Get-DaPercentiles -Values $y -MinValidForP99 1000
            decision = $dec.decision; reason = $dec.reason; slopePerCycle = if ($dec.whole) { $dec.whole.cycle } else { $null }
            slopePerHour = if ($dec.whole) { $dec.whole.time } else { $null }; secondHalf = if ($dec.secondHalf) { $dec.secondHalf.cycle } else { $null }; budget = @()
        }
        if ($dec.whole) {
            $axes = if ($m.class -eq 'memory') { @(@('per-cycle', $dec.whole.cycle, $Budgets.memoryKiBPerCycle, 'KiB/cycle'), @('per-hour', $dec.whole.time, $Budgets.memoryKiBPerHour, 'KiB/hour')) } else { @(, @('per-hour', $dec.whole.time, $Budgets.objectsPerHour, "$($m.unit)/hour")) }
            foreach ($b in $axes) {
                $ax = $b[1]
                $v = Get-DaBudgetVerdict -Lower $(if ($ax) { $ax.lower }) -Upper $(if ($ax) { $ax.upper }) -Budget ([double]$b[2])
                $v.axis = $b[0]; $v.unit = $b[3]; $v.window = "segment $Name, cycles $($c[0])..$($c[-1]), $([math]::Round($t[-1] * 60, 2)) min"
                $mo.budget += $v
                $verdicts.Add([ordered]@{ segment = $Name; metric = $m.name; axis = $b[0]; verdict = $v.verdict; upper = $v.upper; budget = $v.budget; unit = $b[3] })
            }
            if ($m.class -eq 'objects') {
                $med = [double]$mo.distribution.p50
                $dev = [double](@($y | ForEach-Object { [math]::Abs($_ - $med) }) | Measure-Object -Maximum).Maximum
                $band = if ($dev -le $Budgets.plateauBand) { 'pass' } else { 'fail' }
                $mo.budget += [ordered]@{ verdict = $band; axis = 'plateau-band'; budget = $Budgets.plateauBand; observed = $dev; unit = $m.unit; scope = 'observed-window'; reason = "largest distance from the median $med at a cycle point" }
                $verdicts.Add([ordered]@{ segment = $Name; metric = $m.name; axis = 'plateau-band'; verdict = $band; upper = $dev; budget = $Budgets.plateauBand; unit = $m.unit })
            }
        }
        $segOut.metrics[$m.name] = $mo
    }
    $names = [System.Collections.Generic.List[string]]::new()
    foreach ($r in $Points) { if ($r.result -and $r.result.timings) { foreach ($p in $r.result.timings.PSObject.Properties) { if (-not $names.Contains($p.Name)) { $names.Add($p.Name) } } } }
    foreach ($tn in $names) {
        $vals = [double[]]@($Points | ForEach-Object { if ($_.result -and $_.result.timings) { $_.result.timings.$tn } } | Where-Object { $null -ne $_ })
        $segOut.timings[$tn] = [ordered]@{ unit = 'ms'; distribution = Get-DaPercentiles -Values $vals -Timeouts ($Points.Count - $vals.Length) -MinValidForP99 1000; note = 'a cycle without a value counts as a timeout in the tail' }
    }
    return [pscustomobject]@{ Segment = $segOut; Verdicts = $verdicts.ToArray(); Open = $open.ToArray() }
}

function Invoke-DaRunAnalysis {
    <#
    .SYNOPSIS
        Analysis of one cycle run directory (scene.json and cycles.jsonl); writes analysis.json beside them and returns {Code; Reason; Path; Analysis}.
    .DESCRIPTION
        Validity first: the scene record's own verdict (calibration, sampler gaps, power and frequency changes,
        abort) and at least 3 measured cycle points per segment; an invalid run gives code 2 and no verdicts.
        Per segment and metric: the distribution at the measured cycle points (warm-up excluded), the slope over
        cycles and over time with its block-bootstrap interval (seed, block length and repeat count recorded),
        the plateau or retained-growth decision and the budget verdicts of Get-DaGrowthBudgets for the observed
        window. A metric never observed is 'open', never zero. The scene's own timings are summarised per
        segment. Code 1 when any budget verdict is fail or any decision is retained-growth, 0 otherwise.
    .PARAMETER RunDir
        Run directory.
    #>
    param([Parameter(Mandatory)][string]$RunDir)
    $codes = Get-DaExitCode
    $scene = Read-DaJson -Path (Join-Path $RunDir 'scene.json')
    $cyclesPath = Join-Path $RunDir 'cycles.jsonl'
    if (-not $scene -or -not (Test-Path -LiteralPath $cyclesPath)) { return [pscustomobject]@{ Code = $codes.Refused; Reason = "no scene.json or cycles.jsonl in $RunDir"; Path = $null; Analysis = $null } }
    $rows = @([System.IO.File]::ReadAllLines($cyclesPath, [System.Text.UTF8Encoding]::new($false)) | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json -Depth 64 })
    $cycleRows = @($rows | Where-Object { $_.type -eq 'cycle' })
    $measured = @($cycleRows | Where-Object { $_.phase -eq 'measured' -and $_.point })
    $budgets = Get-DaGrowthBudgets
    $boot = Get-DaBootstrapDefaults
    $causes = [System.Collections.Generic.List[string]]::new()
    if (-not $scene.valid) { $causes.Add("scene record invalid: $($scene.invalidReason)") }
    $segments = [ordered]@{}
    foreach ($r in $measured) { $k = [string]$r.segment; if (-not $segments.Contains($k)) { $segments[$k] = [System.Collections.Generic.List[object]]::new() }; $segments[$k].Add($r) }
    if (-not $segments.Count) { $causes.Add('no measured cycle point') }
    foreach ($k in $segments.Keys) { if ($segments[$k].Count -lt 3) { $causes.Add("segment $k has $($segments[$k].Count) measured points (minimum 3)") } }
    $valid = ($causes.Count -eq 0)
    $result = [ordered]@{
        schemaVersion = 1; kind = 'cycle-analysis'; runId = $scene.runId; scene = $scene.scene; variant = $scene.variant; build = $scene.build; device = $scene.device
        valid = $valid; invalidReasons = $causes.ToArray(); cyclePlan = $scene.cyclePlan
        warmupExcluded = @($cycleRows | Where-Object { $_.phase -eq 'warm-up' }).Count; measuredPoints = $measured.Count
        starts = @($rows | Where-Object { $_.type -eq 'start' } | ForEach-Object { [ordered]@{ phase = $_.phase; outcome = $_.start.outcome; totalTimeMs = $_.start.totalTimeMs; launchState = $_.start.launchState } })
        bootstrap = [ordered]@{ seed = $boot.seed; repeats = $boot.repeats; confidence = $boot.confidence; blockLength = 'ceil(n^(1/3)) per series, recorded per slope' }
        budgets = $budgets; forcedGc = [bool]@($cycleRows | Where-Object { $_.forcedGc }).Count; segments = [ordered]@{}; verdicts = @(); open = @()
    }
    $verdicts = [System.Collections.Generic.List[object]]::new()
    $open = [System.Collections.Generic.List[object]]::new()
    if ($valid) {
        foreach ($k in $segments.Keys) {
            $sa = Get-DaSegmentAnalysis -Name $k -Points @($segments[$k]) -Budgets $budgets
            $result.segments[$k] = $sa.Segment
            foreach ($v in $sa.Verdicts) { $verdicts.Add($v) }
            foreach ($o in $sa.Open) { $open.Add($o) }
        }
    }
    $result.verdicts = $verdicts.ToArray()
    $result.open = $open.ToArray()
    $growth = @(foreach ($k in $result.segments.Keys) { foreach ($mn in $result.segments[$k].metrics.Keys) { if ($result.segments[$k].metrics[$mn].decision -eq 'retained-growth') { "$k/$mn" } } })
    $fails = @($verdicts | Where-Object { $_.verdict -eq 'fail' } | ForEach-Object { "$($_.segment)/$($_.metric) $($_.axis)" })
    $code = if (-not $valid) { $codes.Refused } elseif ($growth.Count -or $fails.Count) { $codes.Finding } else { $codes.Ok }
    $result.result = [ordered]@{ code = $code; retainedGrowth = $growth; budgetFails = $fails; inconclusive = @($verdicts | Where-Object { $_.verdict -eq 'inconclusive' }).Count }
    $path = Join-Path $RunDir 'analysis.json'
    Write-DaJson -Path $path -InputObject $result -Durable
    $reason = if (-not $valid) { "invalid run: $($causes -join '; ')" } else {
        "$($scene.scene) $($scene.variant) run $($scene.runId): $($measured.Count) measured points in $($segments.Count) segment(s); retained growth: $(if ($growth.Count) { $growth -join ', ' } else { 'none' }); budget fails: $(if ($fails.Count) { $fails -join ', ' } else { 'none' }); $($result.result.inconclusive) inconclusive; $($open.Count) open; record $path"
    }
    return [pscustomobject]@{ Code = $code; Reason = $reason; Path = $path; Analysis = $result }
}
