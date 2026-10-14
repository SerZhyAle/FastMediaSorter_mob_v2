#requires -Version 7.0
<#
.SYNOPSIS
    Start, input, frame and adb-transport probes of the S4166 dynamic-audit stand, plus the scheduled probe loop.

.DESCRIPTION
    Dot-source after lib/da-common.ps1, lib/da-stats.ps1 and lib/da-sampler.ps1. Functions only; nothing
    is assigned at script scope.

      Invoke-DaTickSchedule   the probe loop: ticks planned at a fixed period from the first one; each
                              executed tick records its scheduler lateness, a tick that cannot start within
                              one period of its plan is recorded as missed (never silently dropped)
      Measure-DaStart         cold (force-stop) or warm (HOME) start through am start -W -n: device
                              TotalTime and WaitTime beside the host wall time, the requested mode and the
                              LaunchState the device reports
      Invoke-DaKeyProbe       gfxinfo reset, host monotonic timestamp, one injected key (delivery confirmed
                              by the command result), then the next completed frame read from dumpsys
                              gfxinfo <pkg> framestats; a lost key and a missing reaction are separate outcomes
      Get-DaFrameStats        janky percent, p50, p90, p95 and p99 frame time and the count of frames longer
                              than 100 ms since the last reset
      Measure-DaAdbTransport  round trip of a no-op shell command, reported on its own so adb latency is never
                              mixed into application latency
      Get-DaProbeContext      foreground window and process state (pid, oom_score_adj class) in one adb call;
                              every probe record carries it
      Get-DaDeviceConditions  power source and CPU frequency policy, compared before and after a run
      Invoke-DaCalibration    the Calibrate verb: host scheduler lateness of the probe loop, adb transport
                              round trip and sampler cost on a quiet emulator, written as a calibration
                              record (with the thresholds every later run is judged by) before any app run
      Invoke-DaControlRun     the Control verb: the same controller, probes and sampler against a stock
                              target and the app, interleaved in a seeded random order, paired JSONL records
                              and a run record judged by Test-DaRunValidity

    WHY AN INVALID CONTROL RUN BLOCKS THE NEXT ONE. The spec forbids replacing an invalid run with a lucky
    repeat. state/last-control.json remembers the last verdict; after an invalid run the next Control run
    is refused until -StandFix names what was fixed in the stand, and that text lands in the new record.
    A run with a test-only injected fault is a self-test of the rule, so its expected invalidity does not
    set the block.

    WHAT THE KEY PROBE MEASURES. Three numbers, never merged: the host time of the inject command (adb
    transport plus the input tool), the host-observed reaction (inject start to the poll that first sees
    the frame; an upper bound that includes transport and the poll period) and the device frame time from
    HandleInputStart to FrameCompleted of the frame that carries an InputEventId. The device clock of the
    injection itself is not readable from the shell at better than 10 ms, so the dispatch delay between
    injection and HandleInputStart is inside the host-observed number only.
#>

function Set-DaTimerResolution {
    <#
    .SYNOPSIS
        Requests (or releases) a 1 ms Windows timer resolution for this process through winmm timeBeginPeriod/timeEndPeriod.
    .DESCRIPTION
        Measured: with the default 15.6 ms timer a coarse Start-Sleep overshot its deadline enough to put
        the probe loop's lateness p99 at 12 ms in control run 261011-215051-d113, above the 10 ms
        invalid-run threshold. Returns $false where winmm is unavailable; the caller still spins.
    .PARAMETER Enable
        Request 1 ms; without it the request is released.
    #>
    param([switch]$Enable)
    if (-not $IsWindows) { return $false }
    try {
        if (-not ('DaWinmm' -as [type])) {
            Add-Type -Namespace '' -Name 'DaWinmm' -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("winmm.dll")] public static extern uint timeBeginPeriod(uint uMilliseconds);
[System.Runtime.InteropServices.DllImport("winmm.dll")] public static extern uint timeEndPeriod(uint uMilliseconds);
'@
        }
        $rc = if ($Enable) { [DaWinmm]::timeBeginPeriod(1) } else { [DaWinmm]::timeEndPeriod(1) }
        return ($rc -eq 0)
    }
    catch { return $false }
}

function Wait-DaUntilMs {
    <#
    .SYNOPSIS
        Waits until a monotonic deadline: coarse sleep while far, then spin, because Windows sleep can overshoot by a timer period.
    .PARAMETER DeadlineMs
        Get-DaMonotonicMs deadline.
    .PARAMETER SpinMarginMs
        Distance to the deadline below which the wait spins instead of sleeping.
    #>
    param([Parameter(Mandatory)][long]$DeadlineMs, [int]$SpinMarginMs = 30)
    while ($true) {
        $left = $DeadlineMs - (Get-DaMonotonicMs)
        if ($left -le 0) { return }
        if ($left -gt $SpinMarginMs) { Start-Sleep -Milliseconds ([int]($left - $SpinMarginMs)) }
        else { [System.Threading.Thread]::SpinWait(500) }
    }
}

function Invoke-DaTickSchedule {
    <#
    .SYNOPSIS
        Runs a body on planned ticks and records lateness and missed ticks; returns {Ticks; Planned; Executed; Missed; LatenessMs}.
    .PARAMETER PeriodMs
        Planned tick period.
    .PARAMETER Count
        Planned tick count.
    .PARAMETER Body
        Scriptblock called with the tick index; its output is stored as the tick's result.
    .PARAMETER InjectDelayMs
        Test-only fault: extra stall after the deadline of selected ticks (0 = none).
    .PARAMETER InjectEvery
        Test-only fault: stall every Nth tick (0 = none).
    #>
    param(
        [Parameter(Mandatory)][int]$PeriodMs,
        [Parameter(Mandatory)][int]$Count,
        [Parameter(Mandatory)][scriptblock]$Body,
        [int]$InjectDelayMs = 0,
        [int]$InjectEvery = 0
    )
    $ticks = [System.Collections.Generic.List[object]]::new()
    $lateness = [System.Collections.Generic.List[double]]::new()
    $missed = 0
    $fineTimer = Set-DaTimerResolution -Enable
    try {
        $t0 = (Get-DaMonotonicMs) + 50
        for ($i = 0; $i -lt $Count; $i++) {
            $planned = $t0 + [long]$i * $PeriodMs
            if ((Get-DaMonotonicMs) -gt $planned + $PeriodMs) {
                $missed++
                $ticks.Add([ordered]@{ index = $i; plannedMonoMs = $planned; missed = $true; startedMonoMs = $null; latenessMs = $null; durationMs = $null; result = $null })
                continue
            }
            Wait-DaUntilMs -DeadlineMs $planned
            if ($InjectEvery -gt 0 -and $InjectDelayMs -gt 0 -and ($i % $InjectEvery) -eq ($InjectEvery - 1)) {
                Start-Sleep -Milliseconds $InjectDelayMs
            }
            $start = Get-DaMonotonicMs
            $late = [double]($start - $planned)
            $lateness.Add($late)
            $result = & $Body $i
            $ticks.Add([ordered]@{ index = $i; plannedMonoMs = $planned; missed = $false; startedMonoMs = $start; latenessMs = $late; durationMs = (Get-DaMonotonicMs) - $start; result = $result })
        }
    }
    finally { if ($fineTimer) { $null = Set-DaTimerResolution } }
    return [pscustomobject]@{ Ticks = $ticks.ToArray(); Planned = $Count; Executed = $Count - $missed; Missed = $missed; LatenessMs = $lateness.ToArray(); FineTimer = $fineTimer }
}

function ConvertFrom-DaAmStart {
    <#
    .SYNOPSIS
        Parses am start -W output: Status, LaunchState, Activity, TotalTime, WaitTime.
    .PARAMETER Lines
        Command output lines.
    #>
    param([string[]]$Lines)
    $text = $Lines -join "`n"
    $pick = { param($rx) if ($text -match $rx) { $Matches[1].Trim() } else { $null } }
    $total = & $pick '(?m)^TotalTime:\s*(\d+)'
    $wait = & $pick '(?m)^WaitTime:\s*(\d+)'
    return [ordered]@{
        status      = & $pick '(?m)^Status:\s*(\S+)'
        launchState = & $pick '(?m)^LaunchState:\s*(\S+)'
        activity    = & $pick '(?m)^Activity:\s*(\S+)'
        totalTimeMs = if ($total) { [int]$total } else { $null }
        waitTimeMs  = if ($wait) { [int]$wait } else { $null }
        error       = & $pick '(?m)^(Error:.*)$'
    }
}

function ConvertFrom-DaFramestats {
    <#
    .SYNOPSIS
        Parses every ---PROFILEDATA--- block of dumpsys gfxinfo framestats into rows keyed by column name (nanoseconds).
    .PARAMETER Lines
        Command output lines.
    #>
    param([string[]]$Lines)
    $rows = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new()
    $inBlock = $false
    $cols = $null
    foreach ($l in $Lines) {
        $t = $l.Trim()
        if ($t -eq '---PROFILEDATA---') { $inBlock = -not $inBlock; $cols = $null; continue }
        if (-not $inBlock -or -not $t) { continue }
        if (-not $cols) { $cols = @($t.TrimEnd(',') -split ','); continue }
        $vals = @($t.TrimEnd(',') -split ',')
        if ($vals.Count -lt $cols.Count) { continue }
        $row = [ordered]@{}
        for ($k = 0; $k -lt $cols.Count; $k++) {
            $n = 0L
            $row[$cols[$k]] = if ([long]::TryParse($vals[$k], [ref]$n)) { $n } else { $null }
        }
        $key = "$($row['FrameTimelineVsyncId'])|$($row['IntendedVsync'])"
        if ($seen.Add($key)) { $rows.Add([pscustomobject]$row) }
    }
    return @($rows | Sort-Object -Property IntendedVsync)
}

function ConvertFrom-DaGfxSummary {
    <#
    .SYNOPSIS
        Reads the first (process-wide) summary of dumpsys gfxinfo: frames, janky share, percentiles, frames over 100 ms.
    .PARAMETER Lines
        Command output lines.
    #>
    param([string[]]$Lines)
    $text = $Lines -join "`n"
    $pick = { param($rx) if ($text -match $rx) { $Matches[1] } else { $null } }
    $hist = $null
    $over100 = $null
    foreach ($l in $Lines) {
        if ($l -match '^\s*HISTOGRAM:\s*(.*)$') { $hist = $Matches[1]; break }
    }
    if ($null -ne $hist) {
        $over100 = 0
        foreach ($m in [regex]::Matches($hist, '(\d+)ms=(\d+)')) {
            if ([int]$m.Groups[1].Value -gt 100) { $over100 += [int]$m.Groups[2].Value }
        }
    }
    $num = { param($v) if ($null -ne $v) { [double]$v } else { $null } }
    return [ordered]@{
        totalFrames     = & $num (& $pick '(?m)^\s*Total frames rendered:\s*(\d+)')
        jankyFrames     = & $num (& $pick '(?m)^\s*Janky frames:\s*(\d+)')
        jankyPercent    = & $num (& $pick '(?m)^\s*Janky frames:\s*\d+\s*\(([\d.]+)%\)')
        p50Ms           = & $num (& $pick '(?m)^\s*50th percentile:\s*(\d+)ms')
        p90Ms           = & $num (& $pick '(?m)^\s*90th percentile:\s*(\d+)ms')
        p95Ms           = & $num (& $pick '(?m)^\s*95th percentile:\s*(\d+)ms')
        p99Ms           = & $num (& $pick '(?m)^\s*99th percentile:\s*(\d+)ms')
        framesOver100Ms = $over100
    }
}

function Get-DaProbeContext {
    <#
    .SYNOPSIS
        Foreground window and the package's process state in one adb call; returns {foreground; foregroundPackage; pid; oomScoreAdj; processState}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package whose process state is read.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    $cmd = "dumpsys window displays | grep -E 'mCurrentFocus='; p=`$(pidof -s $Package); echo pid=`$p; [ -n `"`$p`" ] && echo oom=`$(cat /proc/`$p/oom_score_adj)"
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', $cmd) -TimeoutMs 15000
    $text = $r.Lines -join "`n"
    $fg = if ($text -match 'mCurrentFocus=Window\{\S+ \S+ ([^}\s]+)\}') { $Matches[1] } elseif ($text -match 'mCurrentFocus=(\S+)') { $Matches[1] } else { $null }
    $procId = if ($text -match '(?m)^pid=(\d+)') { [int]$Matches[1] } else { $null }
    $oom = if ($text -match '(?m)^oom=(-?\d+)') { [int]$Matches[1] } else { $null }
    return [ordered]@{
        foreground        = $fg
        foregroundPackage = if ($fg -and $fg.Contains('/')) { $fg.Split('/')[0] } else { $fg }
        pid               = $procId
        oomScoreAdj       = $oom
        processState      = Get-DaOomClass -Adj $oom
    }
}

function Measure-DaStart {
    <#
    .SYNOPSIS
        One cold or warm start through am start -W -n; returns the start record.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package of the component.
    .PARAMETER Component
        package/activity to start.
    .PARAMETER Mode
        cold (force-stop first) or warm (HOME first, process kept).
    .PARAMETER SettleMs
        Pause after force-stop or HOME so the previous transition has finished.
    .PARAMETER TimeoutMs
        Limit of the am start call; on expiry the outcome is timeout.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [Parameter(Mandatory)][string]$Component,
        [Parameter(Mandatory)][ValidateSet('cold', 'warm')][string]$Mode,
        [int]$SettleMs = 700,
        [int]$TimeoutMs = 20000
    )
    if ($Mode -eq 'cold') { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $Package) -TimeoutMs 15000 }
    else { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_HOME') -TimeoutMs 15000 }
    Start-Sleep -Milliseconds $SettleMs
    $before = Get-DaProcessIdentity -Serial $Serial -Package $Package
    $t0 = Get-DaMonotonicMs
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'start', '-W', '-n', $Component) -TimeoutMs $TimeoutMs
    $wall = (Get-DaMonotonicMs) - $t0
    $am = ConvertFrom-DaAmStart -Lines $r.Lines
    $outcome = if ($r.TimedOut) { 'timeout' } elseif ($am.status -eq 'ok' -and $null -ne $am.totalTimeMs) { 'ok' } else { 'error' }
    $ctx = Get-DaProbeContext -Serial $Serial -Package $Package
    return [ordered]@{
        kind              = 'start'
        mode              = $Mode
        package           = $Package
        component         = $Component
        outcome           = $outcome
        hostStartMonoMs   = $t0
        hostWallMs        = $wall
        totalTimeMs       = $am.totalTimeMs
        waitTimeMs        = $am.waitTimeMs
        launchState       = $am.launchState
        activity          = $am.activity
        processBefore     = if ($before) { $before.pid } else { $null }
        error             = if ($outcome -ne 'ok') { (@($am.error, ($r.Stderr -replace '\s+', ' ').Trim()) | Where-Object { $_ }) -join ' ' } else { $null }
        foreground        = $ctx.foreground
        foregroundPackage = $ctx.foregroundPackage
        pid               = $ctx.pid
        processState      = $ctx.processState
        oomScoreAdj       = $ctx.oomScoreAdj
    }
}

function Invoke-DaKeyProbe {
    <#
    .SYNOPSIS
        One injected key and its first frame; returns the key record with outcome ok, lost or no-reaction.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package whose frames are read.
    .PARAMETER KeyCode
        Android key code (a focus move such as 19 or 20 is safe and visible).
    .PARAMETER ReactionTimeoutMs
        Time after the inject command within which a frame must appear.
    .PARAMETER PollMs
        Pause between framestats reads.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [Parameter(Mandatory)][int]$KeyCode,
        [int]$ReactionTimeoutMs = 500,
        [int]$PollMs = 30
    )
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $Package, 'reset') -TimeoutMs 15000
    $t0 = Get-DaMonotonicMs
    $inj = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'keyevent', [string]$KeyCode) -TimeoutMs 15000
    $t1 = Get-DaMonotonicMs
    $delivered = (-not $inj.TimedOut -and $inj.ExitCode -eq 0 -and -not (($inj.Lines -join ' ') -match 'Exception|Error') -and -not ($inj.Stderr -match 'Exception|Error'))
    $outcome = 'lost'
    $frame = $null
    $attributed = $false
    $observed = $null
    $polls = 0
    if ($delivered) {
        $outcome = 'no-reaction'
        $deadline = $t1 + $ReactionTimeoutMs
        while ($true) {
            $polls++
            $fs = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $Package, 'framestats') -TimeoutMs 15000
            $now = Get-DaMonotonicMs
            $rows = @(ConvertFrom-DaFramestats -Lines $fs.Lines | Where-Object { $_.FrameCompleted -gt 0 })
            $withInput = @($rows | Where-Object { $_.InputEventId -and $_.InputEventId -ne 0 } | Select-Object -First 1)
            if ($withInput) { $frame = $withInput[0]; $attributed = $true }
            elseif ($rows) { $frame = $rows[0] }
            if ($frame) { $observed = $now; $outcome = 'ok'; break }
            if ($now -ge $deadline) { break }
            Start-Sleep -Milliseconds $PollMs
        }
    }
    $ctx = Get-DaProbeContext -Serial $Serial -Package $Package
    $ns = 1000000.0
    return [ordered]@{
        kind                 = 'key'
        keyCode              = $KeyCode
        outcome              = $outcome
        delivered            = $delivered
        hostInjectStartMonoMs = $t0
        injectMs             = $t1 - $t0
        hostReactionMs       = if ($observed) { $observed - $t0 } else { $null }
        deviceInputToFrameMs = if ($frame -and $frame.HandleInputStart -gt 0) { [math]::Round(($frame.FrameCompleted - $frame.HandleInputStart) / $ns, 3) } else { $null }
        deviceFrameMs        = if ($frame) { [math]::Round(($frame.FrameCompleted - $frame.IntendedVsync) / $ns, 3) } else { $null }
        frameFlags           = if ($frame) { $frame.Flags } else { $null }
        attributed           = $attributed
        polls                = $polls
        foreground           = $ctx.foreground
        foregroundPackage    = $ctx.foregroundPackage
        pid                  = $ctx.pid
        processState         = $ctx.processState
        oomScoreAdj          = $ctx.oomScoreAdj
    }
}

function Get-DaFrameStats {
    <#
    .SYNOPSIS
        Frame statistics of a package since the last gfxinfo reset; returns the frame record.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package whose frames are read.
    .PARAMETER Reset
        Reset the counters after reading.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [switch]$Reset)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $Package, 'framestats') -TimeoutMs 30000
    $sum = ConvertFrom-DaGfxSummary -Lines $r.Lines
    $rows = @(ConvertFrom-DaFramestats -Lines $r.Lines | Where-Object { $_.FrameCompleted -gt 0 -and $_.Flags -eq 0 })
    $durations = [double[]]@($rows | ForEach-Object { ($_.FrameCompleted - $_.IntendedVsync) / 1000000.0 })
    if ($Reset) { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $Package, 'reset') -TimeoutMs 15000 }
    $ctx = Get-DaProbeContext -Serial $Serial -Package $Package
    return [ordered]@{
        kind               = 'frames'
        package            = $Package
        totalFrames        = $sum.totalFrames
        jankyFrames        = $sum.jankyFrames
        jankyPercent       = $sum.jankyPercent
        p50Ms              = $sum.p50Ms
        p90Ms              = $sum.p90Ms
        p95Ms              = $sum.p95Ms
        p99Ms              = $sum.p99Ms
        framesOver100Ms    = $sum.framesOver100Ms
        profileRows        = $rows.Count
        profileFrameMs     = Get-DaPercentiles -Values $durations
        foreground         = $ctx.foreground
        foregroundPackage  = $ctx.foregroundPackage
        pid                = $ctx.pid
        processState       = $ctx.processState
        oomScoreAdj        = $ctx.oomScoreAdj
    }
}

function Measure-DaAdbTransport {
    <#
    .SYNOPSIS
        Round trip of a no-op adb shell command; returns {samples; failures; stats}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Count
        Number of round trips.
    .PARAMETER IntervalMs
        Pause between round trips.
    .PARAMETER TimeoutMs
        Limit of one round trip; an expired one is a timeout in the distribution.
    #>
    param([Parameter(Mandatory)][string]$Serial, [int]$Count = 30, [int]$IntervalMs = 50, [int]$TimeoutMs = 5000)
    $samples = [System.Collections.Generic.List[double]]::new()
    $fail = 0
    for ($i = 0; $i -lt $Count; $i++) {
        $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'true') -TimeoutMs $TimeoutMs
        if ($r.TimedOut -or $r.ExitCode -ne 0) { $fail++ } else { $samples.Add([double]$r.ElapsedMs) }
        if ($IntervalMs -gt 0) { Start-Sleep -Milliseconds $IntervalMs }
    }
    return [ordered]@{ command = 'adb shell true'; samples = $samples.ToArray(); failures = $fail; stats = (Get-DaPercentiles -Values $samples.ToArray() -Timeouts $fail) }
}

function Get-DaDeviceConditions {
    <#
    .SYNOPSIS
        Power source and CPU frequency policy of cpu0 in one adb call; values the device does not expose stay $null.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $cmd = "dumpsys battery | grep -E '(AC|USB|Wireless) powered:|^  status:|^  level:'; for f in scaling_governor scaling_min_freq scaling_max_freq scaling_cur_freq; do echo `"`$f=`$(cat /sys/devices/system/cpu/cpu0/cpufreq/`$f 2>/dev/null)`"; done"
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', $cmd) -TimeoutMs 15000
    $text = $r.Lines -join "`n"
    $pick = { param($rx) if ($text -match $rx) { $Matches[1].Trim() } else { $null } }
    $nz = { param($v) if ([string]::IsNullOrWhiteSpace($v)) { $null } else { $v } }
    return [ordered]@{
        acPowered       = & $pick 'AC powered:\s*(\S+)'
        usbPowered      = & $pick 'USB powered:\s*(\S+)'
        wirelessPowered = & $pick 'Wireless powered:\s*(\S+)'
        batteryStatus   = & $pick '(?m)^\s*status:\s*(\S+)'
        batteryLevel    = & $pick '(?m)^\s*level:\s*(\S+)'
        cpuGovernor     = & $nz (& $pick '(?m)^scaling_governor=(.*)$')
        cpuMinFreq      = & $nz (& $pick '(?m)^scaling_min_freq=(.*)$')
        cpuMaxFreq      = & $nz (& $pick '(?m)^scaling_max_freq=(.*)$')
        cpuCurFreq      = & $nz (& $pick '(?m)^scaling_cur_freq=(.*)$')
        readMonoMs      = Get-DaMonotonicMs
    }
}

function Compare-DaDeviceConditions {
    <#
    .SYNOPSIS
        Names a power-source or frequency-policy change between two condition snapshots; returns {powerChange; frequencyChange}.
    .DESCRIPTION
        The current frequency is recorded but never compared: it moves with load on any device. A change of
        governor or of the minimum or maximum frequency is a policy change the run did not plan.
    .PARAMETER Before
        Snapshot taken before the run.
    .PARAMETER After
        Snapshot taken after the run.
    #>
    param([Parameter(Mandatory)]$Before, [Parameter(Mandatory)]$After)
    $diff = {
        param([string[]]$keys)
        $out = foreach ($k in $keys) { if ([string]$Before.$k -ne [string]$After.$k) { "$k '$($Before.$k)' -> '$($After.$k)'" } }
        if ($out) { return (@($out) -join '; ') }
        return $null
    }
    return [ordered]@{
        powerChange     = & $diff @('acPowered', 'usbPowered', 'wirelessPowered', 'batteryStatus')
        frequencyChange = & $diff @('cpuGovernor', 'cpuMinFreq', 'cpuMaxFreq')
    }
}

function Get-DaCalibrationPointerPath { return (Join-Path (Get-DaPaths).State 'current-calibration.json') }
function Get-DaLastControlPath { return (Join-Path (Get-DaPaths).State 'last-control.json') }

function Get-DaShuffledOrder {
    <#
    .SYNOPSIS
        Fisher-Yates shuffle of a copy of Items with the given System.Random (the seeded run order).
    .PARAMETER Random
        Seeded generator.
    .PARAMETER Items
        Items to order.
    #>
    param([Parameter(Mandatory)][System.Random]$Random, [Parameter(Mandatory)][object[]]$Items)
    $a = [object[]]$Items.Clone()
    for ($i = $a.Length - 1; $i -gt 0; $i--) {
        $j = $Random.Next(0, $i + 1)
        $tmp = $a[$i]; $a[$i] = $a[$j]; $a[$j] = $tmp
    }
    return , $a
}

function Test-DaComponentResolves {
    <#
    .SYNOPSIS
        True when package/activity resolves on the device (cmd package resolve-activity).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Component
        package/activity.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Component)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-n', $Component) -TimeoutMs 15000
    $last = @($r.Lines | Where-Object { $_.Trim() } | Select-Object -Last 1)
    return ($r.ExitCode -eq 0 -and $last -and $last[0] -match '^[\w.]+/[\w.$]+$' -and $last[0] -notmatch 'ResolverActivity')
}

function Invoke-DaCalibration {
    <#
    .SYNOPSIS
        Measures the stand on a quiet emulator and writes the calibration record; returns {Code; Reason; RecordPath}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        The application under test; it is force-stopped and must stay stopped during calibration.
    .PARAMETER ControlPackage
        Stock package used as the idle process.
    .PARAMETER ControlComponent
        Stock activity started before measuring.
    .PARAMETER ProbePeriodMs
        Period of the probe loop whose lateness is measured.
    .PARAMETER Ticks
        Ticks of the lateness measurement.
    .PARAMETER TransportCount
        adb round trips.
    .PARAMETER SamplerCostSec
        Length of each sampler-cost window.
    .PARAMETER SamplerPeriodMs
        Sampler period.
    .PARAMETER MeminfoEvery
        Every Nth sampler row adds dumpsys meminfo.
    .PARAMETER RunId
        Run id; generated when empty.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [string]$ControlPackage = 'com.android.settings',
        [string]$ControlComponent = 'com.android.settings/.Settings',
        [int]$ProbePeriodMs = 200,
        [int]$Ticks = 150,
        [int]$TransportCount = 40,
        [int]$SamplerCostSec = 20,
        [int]$SamplerPeriodMs = 1000,
        [int]$MeminfoEvery = 10,
        [string]$RunId
    )
    $codes = Get-DaExitCode
    if (-not $RunId) { $RunId = New-DaRunId }
    $paths = Get-DaPaths
    $runDir = Join-Path $paths.Runs $RunId
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    if (-not (Test-DaComponentResolves -Serial $Serial -Component $ControlComponent)) {
        return [pscustomobject]@{ Code = $codes.Refused; Reason = "control component $ControlComponent does not resolve on this image"; RecordPath = $null }
    }
    $startedAt = (Get-Date).ToUniversalTime().ToString('o')
    $startedMono = Get-DaMonotonicMs
    $avd = Get-DaAvdName -Serial $Serial
    $api = ((Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'getprop', 'ro.build.version.sdk')).Lines -join '').Trim()
    $condBefore = Get-DaDeviceConditions -Serial $Serial
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'force-stop', $Package)
    $appAtStart = Get-DaProcessIdentity -Serial $Serial -Package $Package
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'start', '-W', '-n', $ControlComponent) -TimeoutMs 20000
    Start-Sleep -Milliseconds 1500

    $transport = Measure-DaAdbTransport -Serial $Serial -Count $TransportCount
    $sched = Invoke-DaTickSchedule -PeriodMs $ProbePeriodMs -Count $Ticks -Body { param($i) $null }
    $cost = Measure-DaSamplerCost -Serial $Serial -Package $ControlPackage -DurationSec $SamplerCostSec -OutFile (Join-Path $runDir 'sampler-cost.jsonl') -PeriodMs $SamplerPeriodMs -MeminfoEvery $MeminfoEvery
    $condAfter = Get-DaDeviceConditions -Serial $Serial
    $changes = Compare-DaDeviceConditions -Before $condBefore -After $condAfter
    $appAtEnd = Get-DaProcessIdentity -Serial $Serial -Package $Package
    $thresholds = Get-DaDefaultThresholds
    $sc = $cost.sampler
    $restart = if (-not $cost.identityUnchanged) { "idle $ControlPackage restarted during the sampler-cost window" } elseif ($sc.processRestarted) { $sc.processRestarted } else { $null }
    $self = Test-DaRunValidity -Calibration ([pscustomobject]@{ thresholds = [pscustomobject]$thresholds }) -Measurements ([pscustomobject]@{
            schedulerLatenessMs = $sched.LatenessMs; plannedProbes = $sched.Planned; missedProbes = $sched.Missed
            samplerTicks = $sc.ticks; samplerGaps = $sc.gapPeriods
            powerChange = $changes.powerChange; frequencyChange = $changes.frequencyChange; foregroundChange = $null; processRestart = $restart
        })
    $appRan = if ($appAtStart -or $appAtEnd) { "the application process was present (start $([bool]$appAtStart), end $([bool]$appAtEnd))" } else { $null }
    $record = [ordered]@{
        schemaVersion         = 1
        kind                  = 'calibration'
        runId                 = $RunId
        at                    = $startedAt
        atMonoMs              = $startedMono
        finishedAt            = (Get-Date).ToUniversalTime().ToString('o')
        durationMs            = (Get-DaMonotonicMs) - $startedMono
        beforeAnyAppRun       = (-not $appRan)
        appProcess            = $appRan
        device                = [ordered]@{ kind = 'emulator'; serial = $Serial; avd = $avd; api = $api }
        host                  = [ordered]@{ psVersion = $PSVersionTable.PSVersion.ToString(); processors = [Environment]::ProcessorCount; stopwatchHz = [System.Diagnostics.Stopwatch]::Frequency }
        control               = [ordered]@{ package = $ControlPackage; component = $ControlComponent }
        conditions            = [ordered]@{ before = $condBefore; after = $condAfter; powerChange = $changes.powerChange; frequencyChange = $changes.frequencyChange }
        hostSchedulerLateness = [ordered]@{ periodMs = $ProbePeriodMs; fineTimer = $sched.FineTimer; planned = $sched.Planned; missed = $sched.Missed; stats = (Get-DaPercentiles -Values $sched.LatenessMs -MinValidForP99 $thresholds.minValidForP99) }
        adbTransport          = [ordered]@{ command = $transport.command; failures = $transport.failures; stats = $transport.stats }
        samplerCost           = $cost
        thresholds            = $thresholds
        bootstrap             = Get-DaBootstrapDefaults
        selfCheck             = [ordered]@{ valid = $self.Valid; causes = $self.Causes }
    }
    $recordPath = Join-Path $runDir 'calibration.json'
    Write-DaJson -Path $recordPath -InputObject $record -Durable
    $lat = $record.hostSchedulerLateness.stats
    $tr = $transport.stats
    $summary = "lateness p50/p99/max $($lat.p50)/$($lat.p99)/$($lat.max) ms over $($sched.Planned) ticks ($($sched.Missed) missed); adb round trip p50/p95/p99 $($tr.p50)/$($tr.p95)/$($tr.p99) ms; sampler row p50/p99 $($sc.rowCostMs.p50)/$($sc.rowCostMs.p99) ms, meminfo row p50 $($sc.meminfoRowCostMs.p50) ms, gaps $($sc.gapPeriods)/$($sc.ticks), added device CPU $($cost.addedDeviceBusyPct) pct, added host CPU $($cost.addedHostCpuMs) ms"
    if ($appRan) {
        return [pscustomobject]@{ Code = $codes.Refused; Reason = "calibration $RunId is not before an app run: $appRan; record $recordPath kept, pointer not moved"; RecordPath = $recordPath }
    }
    if (-not $self.Valid) {
        $why = ($self.Causes | ForEach-Object { "$($_.id): $($_.detail)" }) -join '; '
        return [pscustomobject]@{ Code = $codes.Refused; Reason = "calibration $RunId INVALID - the stand misses its own thresholds on a quiet emulator ($why); $summary; record $recordPath kept, pointer not moved"; RecordPath = $recordPath }
    }
    Write-DaJson -Path (Get-DaCalibrationPointerPath) -Durable -InputObject ([ordered]@{ schemaVersion = 1; runId = $RunId; path = $recordPath; at = $startedAt; avd = $avd })
    return [pscustomobject]@{ Code = $codes.Ok; Reason = "calibration $RunId written at $startedAt before any app run: $summary; record $recordPath"; RecordPath = $recordPath }
}

function Invoke-DaControlRun {
    <#
    .SYNOPSIS
        Runs the control: same probes and sampler on a stock target and the app in seeded random order; returns {Code; Reason; RunDir}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id of the subject.
    .PARAMETER AppComponent
        Subject activity; default <Package>/com.sza.fastmediasorter.ui.main.MainActivity.
    .PARAMETER ControlPackage
        Stock control package.
    .PARAMETER ControlComponent
        Stock control activity.
    .PARAMETER Seed
        Seed of the run order.
    .PARAMETER ColdStarts
        Cold starts per target.
    .PARAMETER WarmStarts
        Warm starts per target.
    .PARAMETER KeyProbes
        Key probes per target, split over KeyBlocks.
    .PARAMETER KeyBlocks
        Interleaved key blocks per target.
    .PARAMETER KeyPeriodMs
        Planned key probe period.
    .PARAMETER ReactionTimeoutMs
        Time a key has to produce a frame.
    .PARAMETER FrameKeys
        Keys injected in each frame-statistics window.
    .PARAMETER SamplerPeriodMs
        Sampler period.
    .PARAMETER MeminfoEvery
        Every Nth sampler row adds dumpsys meminfo.
    .PARAMETER InjectFault
        Test-only: none, late-tick (stalls every fifth probe tick by three times the lateness threshold) or
        sampler-gap (stalls each sampler for three periods). The run is recorded as injected.
    .PARAMETER StandFix
        What was fixed in the stand since the last invalid run; required to run again after one.
    .PARAMETER Variant
        Build variant label of the subject in the run record.
    .PARAMETER RunId
        Run id; generated when empty.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [string]$AppComponent,
        [string]$ControlPackage = 'com.android.settings',
        [string]$ControlComponent = 'com.android.settings/.Settings',
        [int]$Seed = 4166,
        [int]$ColdStarts = 10,
        [int]$WarmStarts = 10,
        [int]$KeyProbes = 30,
        [int]$KeyBlocks = 3,
        [int]$KeyPeriodMs = 1000,
        [int]$ReactionTimeoutMs = 500,
        [int]$FrameKeys = 10,
        [int]$SamplerPeriodMs = 1000,
        [int]$MeminfoEvery = 10,
        [ValidateSet('none', 'late-tick', 'sampler-gap')][string]$InjectFault = 'none',
        [string]$StandFix,
        [string]$Variant = 'standard',
        [string]$RunId
    )
    $codes = Get-DaExitCode
    $refuse = { param($why) [pscustomobject]@{ Code = $codes.Refused; Reason = $why; RunDir = $null } }
    if (-not $AppComponent) { $AppComponent = "$Package/com.sza.fastmediasorter.ui.main.MainActivity" }
    $avd = Get-DaAvdName -Serial $Serial
    $ptr = Read-DaJson -Path (Get-DaCalibrationPointerPath)
    $cal = if ($ptr) { Read-DaJson -Path ([string]$ptr.path) } else { $null }
    if (-not $cal) { return (& $refuse 'no calibration record; run -Verb Calibrate before any control or application run') }
    if ([string]$cal.device.avd -ne $avd) { return (& $refuse "calibration $($cal.runId) was taken on AVD $($cal.device.avd), target is $avd; calibrate this AVD first") }
    if (-not $cal.beforeAnyAppRun -or -not $cal.selfCheck.valid) { return (& $refuse "calibration $($cal.runId) is not usable (beforeAnyAppRun $($cal.beforeAnyAppRun), selfCheck $($cal.selfCheck.valid))") }
    $last = Read-DaJson -Path (Get-DaLastControlPath)
    if ($last -and -not $last.valid -and -not $last.injected -and [string]::IsNullOrWhiteSpace($StandFix)) {
        return (& $refuse "the last control run $($last.runId) was invalid ($($last.invalidReason)); an invalid run is not replaced by a repeat - pass -StandFix '<what was fixed in the stand>'")
    }
    foreach ($c in @($ControlComponent, $AppComponent)) {
        if (-not (Test-DaComponentResolves -Serial $Serial -Component $c)) { return (& $refuse "component $c does not resolve on this image") }
    }
    $subject = Get-DaSubject -Serial $Serial -Package $Package
    if (-not $subject.installed) { return (& $refuse "$Package is not installed") }
    if (-not $RunId) { $RunId = New-DaRunId }
    $runDir = Join-Path (Get-DaPaths).Runs $RunId
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $recordsFile = Join-Path $runDir 'control-records.jsonl'
    $writer = [System.IO.StreamWriter]::new($recordsFile, $true, [System.Text.UTF8Encoding]::new($false))
    $writer.NewLine = "`n"
    $writer.AutoFlush = $true
    $all = [System.Collections.Generic.List[object]]::new()
    $emit = {
        param($rec, [string]$phase, [int]$pair, [int]$position, $target)
        $rec['runId'] = $RunId; $rec['phase'] = $phase; $rec['pair'] = $pair; $rec['position'] = $position; $rec['target'] = $target.id
        $writer.WriteLine(($rec | ConvertTo-Json -Compress -Depth 12))
        $all.Add([pscustomobject]$rec)
    }
    $th = $cal.thresholds
    $injDelay = if ($InjectFault -eq 'late-tick') { [int][math]::Ceiling(3 * [double]$th.schedulerLatenessP99Ms) } else { 0 }
    $injEvery = if ($InjectFault -eq 'late-tick') { 5 } else { 0 }
    $gapAt = if ($InjectFault -eq 'sampler-gap') { 2 } else { 0 }
    $targets = @(
        [pscustomobject]@{ id = 'control'; package = $ControlPackage; component = $ControlComponent; runAs = $false },
        [pscustomobject]@{ id = 'app'; package = $Package; component = $AppComponent; runAs = $true }
    )
    $rng = [System.Random]::new($Seed)
    $schedule = [System.Collections.Generic.List[object]]::new()
    $startedAt = (Get-Date).ToUniversalTime().ToString('o')
    $startedMono = Get-DaMonotonicMs
    $lateness = [System.Collections.Generic.List[double]]::new()
    $planned = 0; $missed = 0; $coarseTimer = 0
    $samplers = [System.Collections.Generic.List[object]]::new()
    $fgIssues = [System.Collections.Generic.List[string]]::new()
    $condBefore = Get-DaDeviceConditions -Serial $Serial
    try {
        foreach ($phase in @(@('cold', $ColdStarts), @('warm', $WarmStarts))) {
            for ($p = 1; $p -le [int]$phase[1]; $p++) {
                $order = Get-DaShuffledOrder -Random $rng -Items $targets
                $schedule.Add([ordered]@{ phase = $phase[0]; pair = $p; order = @($order | ForEach-Object { $_.id }) })
                $pos = 0
                foreach ($t in $order) {
                    $pos++
                    $rec = Measure-DaStart -Serial $Serial -Package $t.package -Component $t.component -Mode $phase[0]
                    & $emit $rec $phase[0] $p $pos $t
                }
            }
        }
        $perBlock = [int][math]::Ceiling($KeyProbes / [double][math]::Max(1, $KeyBlocks))
        for ($b = 1; $b -le $KeyBlocks; $b++) {
            $order = Get-DaShuffledOrder -Random $rng -Items $targets
            $schedule.Add([ordered]@{ phase = 'keys'; pair = $b; order = @($order | ForEach-Object { $_.id }) })
            $pos = 0
            foreach ($t in $order) {
                $pos++
                $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'start', '-W', '-n', $t.component) -TimeoutMs 20000
                Start-Sleep -Milliseconds 800
                $sf = Join-Path $runDir ("sampler-{0}-block{1}.jsonl" -f $t.id, $b)
                $smp = Start-DaSampler -Serial $Serial -Package $t.package -OutFile $sf -PeriodMs $SamplerPeriodMs -MeminfoEvery $MeminfoEvery -RunAs:$t.runAs -InjectGapAtRow $gapAt -InjectGapPeriods 3
                $pkg = $t.package
                $sched = Invoke-DaTickSchedule -PeriodMs $KeyPeriodMs -Count $perBlock -InjectDelayMs $injDelay -InjectEvery $injEvery -Body {
                    param($i)
                    $code = if (($i % 2) -eq 0) { 20 } else { 19 }
                    Invoke-DaKeyProbe -Serial $Serial -Package $pkg -KeyCode $code -ReactionTimeoutMs $ReactionTimeoutMs
                }
                foreach ($x in $sched.LatenessMs) { $lateness.Add($x) }
                $planned += $sched.Planned; $missed += $sched.Missed
                if (-not $sched.FineTimer) { $coarseTimer++ }
                foreach ($tick in $sched.Ticks) {
                    $rec = if ($tick.missed) { [ordered]@{ kind = 'key'; outcome = 'missed' } } else { $tick.result }
                    $rec['tick'] = $tick.index; $rec['tickLatenessMs'] = $tick.latenessMs; $rec['tickPlannedMonoMs'] = $tick.plannedMonoMs
                    if (-not $tick.missed -and $rec.foregroundPackage -ne $t.package) { $fgIssues.Add("key tick $($tick.index) of $($t.id) block $b saw '$($rec.foreground)'") }
                    & $emit $rec 'keys' $b $pos $t
                }
                $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $t.package, 'reset') -TimeoutMs 15000
                for ($k = 0; $k -lt $FrameKeys; $k++) {
                    $kc = if (($k % 2) -eq 0) { '20' } else { '19' }
                    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'keyevent', $kc) -TimeoutMs 15000
                    Start-Sleep -Milliseconds 100
                }
                Start-Sleep -Milliseconds 500
                $fr = Get-DaFrameStats -Serial $Serial -Package $t.package
                if ($fr.foregroundPackage -ne $t.package) { $fgIssues.Add("frame window of $($t.id) block $b saw '$($fr.foreground)'") }
                & $emit $fr 'frames' $b $pos $t
                $sum = Stop-DaSampler -Sampler $smp
                $sum['target'] = $t.id; $sum['block'] = $b
                $samplers.Add($sum)
            }
        }
    }
    finally { $writer.Dispose() }
    $condAfter = Get-DaDeviceConditions -Serial $Serial
    $changes = Compare-DaDeviceConditions -Before $condBefore -After $condAfter
    $restarts = @($samplers | Where-Object { $_.processRestarted } | ForEach-Object { "$($_.target) block $($_.block): $($_.processRestarted)" })
    $samplerErrors = @($samplers | Where-Object { $_.error } | ForEach-Object { "$($_.target) block $($_.block): $($_.error)" })
    $measure = [pscustomobject]@{
        schedulerLatenessMs = $lateness.ToArray(); plannedProbes = $planned; missedProbes = $missed
        samplerTicks = [int](($samplers | ForEach-Object { $_.ticks } | Measure-Object -Sum).Sum)
        samplerGaps = [int](($samplers | ForEach-Object { $_.gapPeriods } | Measure-Object -Sum).Sum)
        powerChange = $changes.powerChange; frequencyChange = $changes.frequencyChange
        foregroundChange = if ($fgIssues.Count) { "$($fgIssues.Count) probe(s) outside the target: $(@($fgIssues | Select-Object -First 3) -join '; ')" } else { $null }
        processRestart = if ($restarts.Count -or $samplerErrors.Count) { (@($restarts) + @($samplerErrors)) -join '; ' } else { $null }
    }
    $validity = Test-DaRunValidity -Measurements $measure -Calibration $cal
    $minP99 = [int]$th.minValidForP99
    $summary = [ordered]@{}
    foreach ($t in $targets) {
        $mine = @($all | Where-Object { $_.target -eq $t.id })
        $starts = {
            param($mode)
            $s = @($mine | Where-Object { $_.kind -eq 'start' -and $_.mode -eq $mode })
            Get-DaPercentiles -Values ([double[]]@($s | Where-Object { $_.outcome -eq 'ok' } | ForEach-Object { $_.totalTimeMs })) -Timeouts @($s | Where-Object { $_.outcome -ne 'ok' }).Count -MinValidForP99 $minP99
        }
        $states = { param($mode) @($mine | Where-Object { $_.kind -eq 'start' -and $_.mode -eq $mode } | Group-Object launchState | ForEach-Object { "$($_.Name)=$($_.Count)" }) }
        $keys = @($mine | Where-Object { $_.kind -eq 'key' })
        $ok = @($keys | Where-Object { $_.outcome -eq 'ok' })
        $lost = @($keys | Where-Object { $_.outcome -eq 'lost' }).Count
        $noReact = @($keys | Where-Object { $_.outcome -eq 'no-reaction' }).Count
        $frames = @($mine | Where-Object { $_.kind -eq 'frames' })
        $summary[$t.id] = [ordered]@{
            coldTotalTimeMs         = & $starts 'cold'
            warmTotalTimeMs         = & $starts 'warm'
            coldLaunchStates        = & $states 'cold'
            warmLaunchStates        = & $states 'warm'
            keyDeviceInputToFrameMs = Get-DaPercentiles -Values ([double[]]@($ok | Where-Object { $null -ne $_.deviceInputToFrameMs } | ForEach-Object { $_.deviceInputToFrameMs })) -Lost $lost -Timeouts $noReact -MinValidForP99 $minP99
            keyHostReactionMs       = Get-DaPercentiles -Values ([double[]]@($ok | ForEach-Object { $_.hostReactionMs })) -Lost $lost -Timeouts $noReact -MinValidForP99 $minP99
            keyInjectMs             = Get-DaPercentiles -Values ([double[]]@($keys | Where-Object { $_.outcome -ne 'missed' } | ForEach-Object { $_.injectMs })) -MinValidForP99 $minP99
            keyLost                 = $lost
            keyNoReaction           = $noReact
            keyUnattributed         = @($ok | Where-Object { -not $_.attributed }).Count
            framesJankyPercent      = @($frames | ForEach-Object { $_.jankyPercent })
            framesOver100Ms         = [int](($frames | ForEach-Object { $_.framesOver100Ms } | Measure-Object -Sum).Sum)
        }
    }
    $blockKeys = foreach ($t in $targets) {
        foreach ($b in 1..$KeyBlocks) {
            $v = [double[]]@($all | Where-Object { $_.target -eq $t.id -and $_.kind -eq 'key' -and $_.pair -eq $b -and $null -ne $_.deviceInputToFrameMs } | ForEach-Object { $_.deviceInputToFrameMs })
            if ($v.Count) { [pscustomobject]@{ target = $t.id; pair = $b; medianInputToFrameMs = (Get-DaPercentiles -Values $v).p50 } }
        }
    }
    $paired = [ordered]@{
        coldTotalTimeMs              = Get-DaPairedSummary -Records @($all | Where-Object { $_.kind -eq 'start' -and $_.mode -eq 'cold' -and $_.outcome -eq 'ok' }) -Metric 'totalTimeMs'
        warmTotalTimeMs              = Get-DaPairedSummary -Records @($all | Where-Object { $_.kind -eq 'start' -and $_.mode -eq 'warm' -and $_.outcome -eq 'ok' }) -Metric 'totalTimeMs'
        keyBlockMedianInputToFrameMs = Get-DaPairedSummary -Records @($blockKeys) -Metric 'medianInputToFrameMs'
    }
    $invalidReason = if ($validity.Valid) { $null } else { ($validity.Causes | ForEach-Object { "$($_.id): $($_.detail)" }) -join '; ' }
    $record = [ordered]@{
        schemaVersion      = 1
        kind               = 'control'
        runId              = $RunId
        scene              = 'control'
        variant            = $Variant
        build              = [ordered]@{ package = $subject.package; versionName = $subject.versionName; versionCode = $subject.versionCode; apkSha256 = $subject.apkSha256; hostApk = $subject.hostApk }
        device             = [ordered]@{ kind = 'emulator'; serial = $Serial; avd = $avd; api = $cal.device.api }
        valid              = $validity.Valid
        invalidReason      = $invalidReason
        causes             = $validity.Causes
        startedAt          = $startedAt
        finishedAt         = (Get-Date).ToUniversalTime().ToString('o')
        durationMs         = (Get-DaMonotonicMs) - $startedMono
        cycles             = $null
        steadyMinutes      = $null
        calibration        = [ordered]@{ runId = $cal.runId; at = $cal.at; path = [string]$ptr.path }
        seed               = $Seed
        schedule           = $schedule.ToArray()
        targets            = $targets
        parameters         = [ordered]@{ coldStarts = $ColdStarts; warmStarts = $WarmStarts; keyProbes = $KeyProbes; keyBlocks = $KeyBlocks; keyPeriodMs = $KeyPeriodMs; reactionTimeoutMs = $ReactionTimeoutMs; frameKeys = $FrameKeys; samplerPeriodMs = $SamplerPeriodMs; meminfoEvery = $MeminfoEvery; coarseTimerBlocks = $coarseTimer }
        injectedFault      = $InjectFault
        standFix           = $StandFix
        debugBuildOverhead = 'StrictMode, LeakCanary, the Timber UI tree and the crash handler are active in the debug subject'
        conditions         = [ordered]@{ before = $condBefore; after = $condAfter }
        validity           = [ordered]@{ measured = $validity.Measured; thresholds = $validity.Thresholds }
        samplers           = $samplers.ToArray()
        summary            = $summary
        paired             = $paired
        files              = [ordered]@{ records = 'control-records.jsonl'; samplers = @($samplers | ForEach-Object { Split-Path -Leaf $_.outFile }) }
    }
    Write-DaJson -Path (Join-Path $runDir 'run.json') -InputObject $record -Durable
    Write-DaJson -Path (Get-DaLastControlPath) -Durable -InputObject ([ordered]@{ schemaVersion = 1; runId = $RunId; valid = $validity.Valid; invalidReason = $invalidReason; injected = ($InjectFault -ne 'none'); at = $record.finishedAt })
    $c = $summary['control']; $a = $summary['app']
    $brief = "cold p50 control/app $($c.coldTotalTimeMs.p50)/$($a.coldTotalTimeMs.p50) ms, warm p50 $($c.warmTotalTimeMs.p50)/$($a.warmTotalTimeMs.p50) ms, key input-to-frame p50 $($c.keyDeviceInputToFrameMs.p50)/$($a.keyDeviceInputToFrameMs.p50) ms, lateness p99 $($validity.Measured.schedulerLateness.p99) ms, missed $missed/$planned, sampler gaps $($measure.samplerGaps)/$($measure.samplerTicks)"
    if (-not $validity.Valid) {
        return [pscustomobject]@{ Code = $codes.Refused; Reason = "control run $RunId INVALID - $invalidReason; raw data kept in $runDir; $brief"; RunDir = $runDir }
    }
    return [pscustomobject]@{ Code = $codes.Ok; Reason = "control run $RunId valid ($($all.Count) paired records, seed $Seed): $brief; run record $(Join-Path $runDir 'run.json')"; RunDir = $runDir }
}
