#requires -Version 7.0
<#
.SYNOPSIS
    Out-of-process 1 Hz resource sampler of the S4166 dynamic-audit stand, bound to a process id and its start identity.

.DESCRIPTION
    Dot-source after lib/da-common.ps1 and lib/da-stats.ps1. Functions only; nothing is assigned at script scope.

      Get-DaProcessIdentity  pid of a package and field 22 of /proc/<pid>/stat (start time in clock ticks)
      ConvertFrom-DaProcStat parses one /proc/<pid>/stat line (the command name may hold spaces and parens)
      ConvertFrom-DaMeminfo  Java, native, graphics and total PSS plus the Views, Activities, AppContexts and
                             Binder counters of a dumpsys meminfo dump
      Get-DaSampleRow        one row in ONE adb call: host monotonic timestamp, VmRSS, VmHWM, threads, open
                             file descriptors (through run-as, debuggable packages only), CPU ticks and
                             oom_score_adj; with -WithMeminfo the dumpsys meminfo block is added
      Start-DaSampler        starts the sampler loop in its own runspace and returns a handle
      Stop-DaSampler         stops it and returns the summary (rows, unplanned gap periods, gap share,
                             process-restarted detail, row cost and lateness distributions)
      Measure-DaSamplerCost  runs a quiet window and a sampled window against an idle stock process and
                             reports the sampler's own latency and the device and host load it adds

    IDENTITY. A sampler never follows a new process silently. When the pid vanishes or field 22 changes,
    the loop writes a process-restarted row and stops; the summary carries the detail so the run record
    can name it.

    GAPS. Ticks are planned at a fixed period from the first tick (no drift). A tick that cannot start
    within one period of its plan is skipped and written as a gap row with its length; a late but
    executed row records its lateness instead.

    CLOCK. Every timestamp is Get-DaMonotonicMs (Stopwatch); the wall clock never times anything.
#>

function ConvertFrom-DaProcStat {
    <#
    .SYNOPSIS
        Parses a /proc/<pid>/stat line; returns {pid; comm; state; utime; stime; startTicks} or $null.
    .PARAMETER Line
        The raw stat line.
    #>
    param([string]$Line)
    if ([string]::IsNullOrWhiteSpace($Line)) { return $null }
    $open = $Line.IndexOf('(')
    $close = $Line.LastIndexOf(')')
    if ($open -lt 1 -or $close -lt $open) { return $null }
    $rest = $Line.Substring($close + 1).Trim() -split '\s+'
    # $rest[0] is field 3 (state); field N sits at index N-3.
    if ($rest.Length -lt 20) { return $null }
    return [pscustomobject]@{
        pid        = [int]$Line.Substring(0, $open).Trim()
        comm       = $Line.Substring($open + 1, $close - $open - 1)
        state      = $rest[0]
        utime      = [long]$rest[11]
        stime      = [long]$rest[12]
        startTicks = [long]$rest[19]
    }
}

function ConvertFrom-DaMeminfo {
    <#
    .SYNOPSIS
        Extracts PSS classes and object counters from dumpsys meminfo lines; missing values stay $null.
    .PARAMETER Lines
        dumpsys meminfo <pid> output.
    #>
    param([string[]]$Lines)
    $text = ($Lines -join "`n")
    $pick = { param($rx) if ($text -match $rx) { [long]$Matches[1] } else { $null } }
    return [ordered]@{
        javaPssKb     = & $pick '(?m)^\s*Java Heap:\s+(\d+)'
        nativePssKb   = & $pick '(?m)^\s*Native Heap:\s+(\d+)'
        graphicsPssKb = & $pick '(?m)^\s*Graphics:\s+(\d+)'
        totalPssKb    = & $pick 'TOTAL PSS:\s+(\d+)'
        views         = & $pick '(?m)^\s*Views:\s+(\d+)'
        activities    = & $pick 'Activities:\s+(\d+)'
        appContexts   = & $pick 'AppContexts:\s+(\d+)'
        localBinders  = & $pick 'Local Binders:\s+(\d+)'
        proxyBinders  = & $pick 'Proxy Binders:\s+(\d+)'
    }
}

function Get-DaOomClass {
    <#
    .SYNOPSIS
        Coarse process state named from oom_score_adj (foreground, visible, perceptible, service, previous, cached).
    .PARAMETER Adj
        oom_score_adj value; $null gives 'gone'.
    #>
    param($Adj)
    if ($null -eq $Adj) { return 'gone' }
    $a = [int]$Adj
    if ($a -lt 0) { return 'system' }
    if ($a -lt 100) { return 'foreground' }
    if ($a -lt 200) { return 'visible' }
    if ($a -lt 300) { return 'perceptible' }
    if ($a -lt 700) { return 'service' }
    if ($a -lt 900) { return 'previous' }
    return 'cached'
}

function Get-DaProcessIdentity {
    <#
    .SYNOPSIS
        pid and start identity (field 22 of /proc/<pid>/stat) of a package's main process; $null when it is not running.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Process name (the package).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "p=`$(pidof -s $Package); [ -n `"`$p`" ] && cat /proc/`$p/stat") -TimeoutMs 15000
    $line = @($r.Lines | Where-Object { $_ -match '^\d+ \(' } | Select-Object -First 1)
    if (-not $line) { return $null }
    $st = ConvertFrom-DaProcStat -Line $line[0]
    if (-not $st) { return $null }
    return [pscustomobject]@{ pid = $st.pid; startTicks = $st.startTicks; package = $Package }
}

function Get-DaSampleRow {
    <#
    .SYNOPSIS
        One sampler row from a single adb call; returns an ordered dictionary.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER ProcessId
        Bound process id.
    .PARAMETER StartTicks
        Bound start identity (field 22).
    .PARAMETER Package
        Package name, used for run-as.
    .PARAMETER RunAs
        Count open descriptors through run-as (debuggable packages only); otherwise fdCount stays $null.
    .PARAMETER WithMeminfo
        Add the dumpsys meminfo block.
    .PARAMETER Index
        Tick index.
    .PARAMETER PlannedMs
        Planned monotonic time of the tick.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][long]$StartTicks,
        [Parameter(Mandatory)][string]$Package,
        [switch]$RunAs,
        [switch]$WithMeminfo,
        [int]$Index = 0,
        [long]$PlannedMs = 0
    )
    $p = $ProcessId
    $cmd = "echo '##stat'; cat /proc/$p/stat 2>/dev/null; echo '##status'; grep -E '^(VmRSS|VmHWM|Threads):' /proc/$p/status 2>/dev/null; echo '##oom'; cat /proc/$p/oom_score_adj 2>/dev/null"
    if ($RunAs) { $cmd += "; echo '##fd'; run-as $Package ls /proc/$p/fd 2>/dev/null | wc -l" }
    if ($WithMeminfo) { $cmd += "; echo '##mem'; dumpsys meminfo $p" }
    $t0 = Get-DaMonotonicMs
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', $cmd) -TimeoutMs 30000
    $cost = (Get-DaMonotonicMs) - $t0
    $sections = @{}
    $cur = ''
    foreach ($l in $r.Lines) {
        if ($l -match '^##(\w+)$') { $cur = $Matches[1]; $sections[$cur] = [System.Collections.Generic.List[string]]::new(); continue }
        if ($cur) { $sections[$cur].Add($l) }
    }
    $get = { param($k) if ($sections.ContainsKey($k)) { @($sections[$k]) } else { @() } }
    $st = ConvertFrom-DaProcStat -Line ((& $get 'stat') | Select-Object -First 1)
    $statusText = (& $get 'status') -join "`n"
    $num = { param($rx) if ($statusText -match $rx) { [long]$Matches[1] } else { $null } }
    $oomText = ((& $get 'oom') -join '').Trim()
    $oom = if ($oomText -match '^-?\d+$') { [int]$oomText } else { $null }
    $alive = [bool]$st
    $row = [ordered]@{
        type          = 'sample'
        index         = $Index
        plannedMonoMs = $PlannedMs
        hostMonoMs    = $t0
        latenessMs    = if ($PlannedMs -gt 0) { $t0 - $PlannedMs } else { $null }
        costMs        = $cost
        adbExit       = $r.ExitCode
        pid           = $p
        alive         = $alive
        identityMatch = ($alive -and $st.startTicks -eq $StartTicks)
        startTicks    = if ($st) { $st.startTicks } else { $null }
        vmRssKb       = & $num 'VmRSS:\s+(\d+)'
        vmHwmKb       = & $num 'VmHWM:\s+(\d+)'
        threads       = & $num 'Threads:\s+(\d+)'
        fdCount       = $null
        utimeTicks    = if ($st) { $st.utime } else { $null }
        stimeTicks    = if ($st) { $st.stime } else { $null }
        cpuTicks      = if ($st) { $st.utime + $st.stime } else { $null }
        oomScoreAdj   = $oom
        processState  = Get-DaOomClass -Adj $oom
    }
    if ($RunAs) {
        $fdText = ((& $get 'fd') -join '').Trim()
        # wc counts 0 when run-as failed; a live process always has descriptors, so 0 means unreadable.
        if ($fdText -match '^\d+$' -and [int]$fdText -gt 0) { $row.fdCount = [int]$fdText }
    }
    if ($WithMeminfo) { $row.meminfo = ConvertFrom-DaMeminfo -Lines (& $get 'mem') }
    return $row
}

function Invoke-DaSamplerLoop {
    <#
    .SYNOPSIS
        The sampler loop; runs inside the runspace Start-DaSampler creates and reports through a synchronized hashtable.
    .PARAMETER Sync
        Synchronized hashtable shared with the caller (Stop flag in, counters out).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package name.
    .PARAMETER ProcessId
        Bound process id.
    .PARAMETER StartTicks
        Bound start identity.
    .PARAMETER OutFile
        JSONL file receiving the rows.
    .PARAMETER PeriodMs
        Tick period.
    .PARAMETER MeminfoEvery
        Every Nth row adds dumpsys meminfo (0 = never).
    .PARAMETER RunAs
        Count descriptors through run-as.
    .PARAMETER InjectGapAtRow
        Test-only: before this row index the loop stalls (0 = never).
    .PARAMETER InjectGapPeriods
        Test-only: stall length in periods.
    #>
    param(
        [Parameter(Mandatory)][hashtable]$Sync,
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][long]$StartTicks,
        [Parameter(Mandatory)][string]$OutFile,
        [int]$PeriodMs = 1000,
        [int]$MeminfoEvery = 10,
        [switch]$RunAs,
        [int]$InjectGapAtRow = 0,
        [int]$InjectGapPeriods = 0
    )
    $writer = [System.IO.StreamWriter]::new($OutFile, $true, [System.Text.UTF8Encoding]::new($false))
    $writer.NewLine = "`n"
    $writer.AutoFlush = $true
    try {
        $t0 = Get-DaMonotonicMs
        $i = 0
        $rows = 0
        while (-not $Sync.Stop) {
            $planned = $t0 + [long]$i * $PeriodMs
            if ($InjectGapAtRow -gt 0 -and $rows -eq $InjectGapAtRow -and -not $Sync.Injected) {
                $Sync.Injected = $true
                Start-Sleep -Milliseconds ([int]($InjectGapPeriods * $PeriodMs + $PeriodMs / 2))
            }
            $now = Get-DaMonotonicMs
            if ($now -gt $planned + $PeriodMs) {
                $skip = [int][math]::Floor(($now - $planned) / $PeriodMs)
                $gap = [ordered]@{ type = 'gap'; fromIndex = $i; periods = $skip; lengthMs = [long]$skip * $PeriodMs; planned = $false; hostMonoMs = $now }
                $writer.WriteLine(($gap | ConvertTo-Json -Compress -Depth 8))
                $Sync.GapPeriods = [int]$Sync.GapPeriods + $skip
                $i += $skip
                continue
            }
            while (-not $Sync.Stop -and (Get-DaMonotonicMs) -lt $planned) {
                $left = $planned - (Get-DaMonotonicMs)
                Start-Sleep -Milliseconds ([int][math]::Max(1, [math]::Min($left, 100)))
            }
            if ($Sync.Stop) { break }
            $withMem = ($MeminfoEvery -gt 0 -and ($rows % $MeminfoEvery) -eq ($MeminfoEvery - 1))
            $row = Get-DaSampleRow -Serial $Serial -ProcessId $ProcessId -StartTicks $StartTicks -Package $Package -RunAs:$RunAs -WithMeminfo:$withMem -Index $i -PlannedMs $planned
            $writer.WriteLine(($row | ConvertTo-Json -Compress -Depth 8))
            $rows++
            $Sync.Rows = $rows
            $Sync.Costs.Add([double]$row.costMs)
            if ($withMem) { $Sync.MemCosts.Add([double]$row.costMs) }
            $Sync.Lateness.Add([double]$row.latenessMs)
            if (-not $row.identityMatch) {
                $detail = if (-not $row.alive) { "pid $ProcessId of $Package is gone" } else { "pid $ProcessId start identity changed from $StartTicks to $($row.startTicks)" }
                $writer.WriteLine(([ordered]@{ type = 'process-restarted'; index = $i; detail = $detail; hostMonoMs = Get-DaMonotonicMs } | ConvertTo-Json -Compress))
                $Sync.ProcessRestarted = $detail
                break
            }
            $i++
        }
        $Sync.PlannedTicks = $i
    }
    catch { $Sync.Error = $_.Exception.Message }
    finally { $writer.Dispose(); $Sync.Done = $true }
}

function Start-DaSampler {
    <#
    .SYNOPSIS
        Starts the 1 Hz sampler for a package's running process in its own runspace; returns the handle Stop-DaSampler takes.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package whose main process is sampled; it must be running.
    .PARAMETER OutFile
        JSONL output file (appended).
    .PARAMETER PeriodMs
        Tick period.
    .PARAMETER MeminfoEvery
        Every Nth row adds dumpsys meminfo (0 = never).
    .PARAMETER RunAs
        Count descriptors through run-as (debuggable packages only).
    .PARAMETER InjectGapAtRow
        Test-only fault: stall before this row index (0 = never).
    .PARAMETER InjectGapPeriods
        Test-only fault: stall length in periods.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [Parameter(Mandatory)][string]$OutFile,
        [int]$PeriodMs = 1000,
        [int]$MeminfoEvery = 10,
        [switch]$RunAs,
        [int]$InjectGapAtRow = 0,
        [int]$InjectGapPeriods = 0
    )
    $id = Get-DaProcessIdentity -Serial $Serial -Package $Package
    if (-not $id) { throw "sampler: $Package is not running on $Serial" }
    $dir = Split-Path -Parent $OutFile
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $sync = [hashtable]::Synchronized(@{
            Stop = $false; Done = $false; Rows = 0; GapPeriods = 0; PlannedTicks = 0; Injected = $false
            ProcessRestarted = $null; Error = $null
            Costs = [System.Collections.Generic.List[double]]::new()
            MemCosts = [System.Collections.Generic.List[double]]::new()
            Lateness = [System.Collections.Generic.List[double]]::new()
        })
    $libDir = $PSScriptRoot
    $ps = [powershell]::Create()
    $null = $ps.AddScript({
            param($LibDir, $Sync, $Serial, $Package, $ProcessId, $StartTicks, $OutFile, $PeriodMs, $MeminfoEvery, $RunAs, $InjectGapAtRow, $InjectGapPeriods)
            $ErrorActionPreference = 'Stop'
            try {
                . (Join-Path $LibDir 'da-common.ps1')
                . (Join-Path $LibDir 'da-sampler.ps1')
                Invoke-DaSamplerLoop -Sync $Sync -Serial $Serial -Package $Package -ProcessId $ProcessId -StartTicks $StartTicks -OutFile $OutFile `
                    -PeriodMs $PeriodMs -MeminfoEvery $MeminfoEvery -RunAs:$RunAs -InjectGapAtRow $InjectGapAtRow -InjectGapPeriods $InjectGapPeriods
            }
            catch { $Sync.Error = $_.Exception.Message; $Sync.Done = $true }
        }).AddArgument($libDir).AddArgument($sync).AddArgument($Serial).AddArgument($Package).AddArgument($id.pid).AddArgument($id.startTicks).AddArgument($OutFile).AddArgument($PeriodMs).AddArgument($MeminfoEvery).AddArgument([bool]$RunAs).AddArgument($InjectGapAtRow).AddArgument($InjectGapPeriods)
    $async = $ps.BeginInvoke()
    return [pscustomobject]@{
        PowerShell = $ps; Async = $async; Sync = $sync; OutFile = $OutFile; Package = $Package
        ProcessId = $id.pid; StartTicks = $id.startTicks; PeriodMs = $PeriodMs; StartedMonoMs = Get-DaMonotonicMs
    }
}

function Stop-DaSampler {
    <#
    .SYNOPSIS
        Stops a sampler and returns its summary: rows, unplanned gap periods, gap share, process-restarted detail, cost and lateness.
    .PARAMETER Sampler
        Handle from Start-DaSampler.
    .PARAMETER TimeoutMs
        How long to wait for the loop to finish its current row.
    #>
    param([Parameter(Mandatory)]$Sampler, [int]$TimeoutMs = 60000)
    $s = $Sampler.Sync
    $s.Stop = $true
    $finished = $Sampler.Async.AsyncWaitHandle.WaitOne($TimeoutMs)
    if ($finished) { $null = $Sampler.PowerShell.EndInvoke($Sampler.Async) } else { $null = $Sampler.PowerShell.Stop() }
    $Sampler.PowerShell.Dispose()
    $rows = [int]$s.Rows
    $gaps = [int]$s.GapPeriods
    $ticks = $rows + $gaps
    return [ordered]@{
        outFile          = $Sampler.OutFile
        package          = $Sampler.Package
        pid              = $Sampler.ProcessId
        startTicks       = $Sampler.StartTicks
        periodMs         = $Sampler.PeriodMs
        durationMs       = (Get-DaMonotonicMs) - $Sampler.StartedMonoMs
        rows             = $rows
        gapPeriods       = $gaps
        ticks            = $ticks
        gapShare         = if ($ticks -gt 0) { [math]::Round($gaps / $ticks, 6) } else { $null }
        processRestarted = $s.ProcessRestarted
        injected         = [bool]$s.Injected
        error            = if ($finished) { $s.Error } else { "sampler did not stop within $TimeoutMs ms" }
        rowCostMs        = Get-DaPercentiles -Values $s.Costs.ToArray()
        meminfoRowCostMs = Get-DaPercentiles -Values $s.MemCosts.ToArray()
        rowLatenessMs    = Get-DaPercentiles -Values $s.Lateness.ToArray()
    }
}

function Get-DaDeviceCpuSnapshot {
    <#
    .SYNOPSIS
        Aggregate device CPU jiffies (busy and total) and a process's CPU ticks in one adb call.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER ProcessId
        Process whose utime plus stime is read.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][int]$ProcessId)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "head -1 /proc/stat; cat /proc/$ProcessId/stat 2>/dev/null") -TimeoutMs 15000
    $cpu = @($r.Lines | Where-Object { $_ -match '^cpu\s' } | Select-Object -First 1)
    $busy = $null; $total = $null
    if ($cpu) {
        $f = @(($cpu[0] -split '\s+') | Select-Object -Skip 1 | ForEach-Object { [long]$_ })
        $total = ($f | Measure-Object -Sum).Sum
        $idle = $f[3] + $(if ($f.Count -gt 4) { $f[4] } else { 0 })
        $busy = $total - $idle
    }
    $st = ConvertFrom-DaProcStat -Line (@($r.Lines | Where-Object { $_ -match '^\d+ \(' } | Select-Object -First 1) | Select-Object -First 1)
    return [pscustomobject]@{ busy = $busy; total = $total; procTicks = if ($st) { $st.utime + $st.stime } else { $null }; hostCpuMs = [System.Diagnostics.Process]::GetCurrentProcess().TotalProcessorTime.TotalMilliseconds }
}

function Measure-DaSamplerCost {
    <#
    .SYNOPSIS
        Measures the sampler against an idle stock process: its own row latency and the device and host load it adds.
    .DESCRIPTION
        Runs a quiet window of DurationSec without the sampler, then the same window with it, and
        compares device busy share, the target's CPU ticks and this host process's CPU time. The
        sampler is measured first because it can cause the leak or the delay it reports (section 5.2).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Idle stock package; it must be running.
    .PARAMETER DurationSec
        Length of each window.
    .PARAMETER OutFile
        JSONL file for the sampled window's rows.
    .PARAMETER PeriodMs
        Sampler period.
    .PARAMETER MeminfoEvery
        Every Nth row adds dumpsys meminfo.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [string]$Package = 'com.android.settings',
        [int]$DurationSec = 20,
        [Parameter(Mandatory)][string]$OutFile,
        [int]$PeriodMs = 1000,
        [int]$MeminfoEvery = 10
    )
    $id = Get-DaProcessIdentity -Serial $Serial -Package $Package
    if (-not $id) { throw "sampler cost: $Package is not running" }
    $window = {
        param([bool]$withSampler)
        $a = Get-DaDeviceCpuSnapshot -Serial $Serial -ProcessId $id.pid
        $handle = $null
        $summary = $null
        if ($withSampler) { $handle = Start-DaSampler -Serial $Serial -Package $Package -OutFile $OutFile -PeriodMs $PeriodMs -MeminfoEvery $MeminfoEvery }
        $end = (Get-DaMonotonicMs) + $DurationSec * 1000L
        while ((Get-DaMonotonicMs) -lt $end) { Start-Sleep -Milliseconds 200 }
        if ($handle) { $summary = Stop-DaSampler -Sampler $handle }
        $b = Get-DaDeviceCpuSnapshot -Serial $Serial -ProcessId $id.pid
        $dt = $b.total - $a.total
        return [pscustomobject]@{
            deviceBusyPct = if ($dt -gt 0) { [math]::Round(100.0 * ($b.busy - $a.busy) / $dt, 3) } else { $null }
            targetCpuTicks = if ($null -ne $a.procTicks -and $null -ne $b.procTicks) { $b.procTicks - $a.procTicks } else { $null }
            hostCpuMs = [math]::Round($b.hostCpuMs - $a.hostCpuMs, 1)
            sampler = $summary
        }
    }
    $quiet = & $window $false
    $sampled = & $window $true
    $after = Get-DaProcessIdentity -Serial $Serial -Package $Package
    return [ordered]@{
        package               = $Package
        durationSec           = $DurationSec
        periodMs              = $PeriodMs
        meminfoEvery          = $MeminfoEvery
        quiet                 = [ordered]@{ deviceBusyPct = $quiet.deviceBusyPct; targetCpuTicks = $quiet.targetCpuTicks; hostCpuMs = $quiet.hostCpuMs }
        sampled               = [ordered]@{ deviceBusyPct = $sampled.deviceBusyPct; targetCpuTicks = $sampled.targetCpuTicks; hostCpuMs = $sampled.hostCpuMs }
        addedDeviceBusyPct    = if ($null -ne $quiet.deviceBusyPct -and $null -ne $sampled.deviceBusyPct) { [math]::Round($sampled.deviceBusyPct - $quiet.deviceBusyPct, 3) } else { $null }
        addedTargetCpuTicks   = if ($null -ne $quiet.targetCpuTicks -and $null -ne $sampled.targetCpuTicks) { $sampled.targetCpuTicks - $quiet.targetCpuTicks } else { $null }
        addedHostCpuMs        = [math]::Round($sampled.hostCpuMs - $quiet.hostCpuMs, 1)
        identityUnchanged     = ($after -and $after.pid -eq $id.pid -and $after.startTicks -eq $id.startTicks)
        sampler               = $sampled.sampler
    }
}
