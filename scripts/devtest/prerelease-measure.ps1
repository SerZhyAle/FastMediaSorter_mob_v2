<#
.SYNOPSIS
  S0484 pre-release sweep - per-checkpoint performance measurement.

.DESCRIPTION
  Measures one named performance checkpoint and emits a verdict record comparing the
  measured value against the configured threshold (prerelease.config.psd1 Thresholds).
  Uses adb + dumpsys + log markers only - no app code (research/01).

  Checkpoints:
    cold-start          - `am start -W` TotalTime (ms) for MainActivity after force-stop.
    list-scroll         - `dumpsys gfxinfo` janky-frame %% (skill resets + scrolls first).
    player-open         - ms to first frame; skill supplies the wall-clock via -ElapsedMs.
    network-listing     - ms to BrowseLoadingManager COMPLETE; skill supplies -ElapsedMs.

  S1502 streams checkpoints (seed the device with scripts/devtest/streams-perf-seed.ps1 first,
  otherwise the numbers describe whatever catalog the device happened to hold):
    streams-open        - ms to the streams screen, read from the ActivityTaskManager "Displayed"
                          marker in logcat; -ElapsedMs is the fallback. StreamsActivity is
                          android:exported="false", so `am start -W` is refused from the shell and
                          the caller has to tap through to the screen first. Run `adb logcat -c`
                          before that tap: this reads the most recent marker in the buffer, so an
                          earlier launch still sitting there would be reported as this run's number.
    streams-search      - `dumpsys gfxinfo` janky-frame %% across a typing burst in the search box
                          (caller resets gfxinfo, types, then calls). Strategic §11.1 asks whether a
                          keystroke holds the main thread past one frame, which is what janky frames
                          count. A wall-clock "ms to filtered list" was tried first and rejected: the
                          only way to observe the settled list from outside is a uiautomator dump,
                          whose own 1-2 s cost swamped the thing being measured.
    streams-list-scroll - `dumpsys gfxinfo` janky-frame %% while scrolling the list mode.
    streams-grid-scroll - `dumpsys gfxinfo` janky-frame %% while scrolling the grid mode.
    streams-peak-memory - peak resident set (kB) from /proc/<pid>/status VmHWM, falling back to
                          `dumpsys meminfo` total PSS where /proc is not readable. The strategic
                          criterion is "peak did not grow", which is a baseline/after comparison -
                          the configured Limit is only a coarse absolute backstop.

  S2100 Google Play threshold checkpoints (enforced from February 2027). Unlike every checkpoint
  above, these two resolve their limit at measurement time from the PlayMemory table in
  prerelease.config.psd1 rather than from a scalar Thresholds row, because Play's limit is a
  function of the device's physical RAM bucket AND the process state:
    play-anon-memory    - anonymous RSS + swap (kB) from /proc/<pid>/smaps_rollup, which is the
                          exact quantity Play names. Falls back to `dumpsys meminfo` total PSS only
                          when smaps_rollup is unreadable, and says so - PSS is a DIFFERENT quantity
                          (it counts file-backed and shared pages) and must not be read as the metric.
    play-bitmap-memory  - native heap (kB) from `dumpsys meminfo`. This is an UPPER BOUND on bitmap
                          memory, not the figure itself: from API 26 bitmap pixels are allocated in
                          the native heap alongside every other native allocation, and no
                          shell-visible source separates them. Reported so, deliberately - an
                          over-reported number presented as exact would justify a cache reduction
                          the evidence does not support (S2100 strategic ADR-2).

  Every record carries `processState`, read from /proc/<pid>/oom_score_adj. Play evaluates the
  memory metrics per process state and its limits differ between them by more than a factor of two,
  so a memory sample with no state label cannot be compared against any threshold at all.

  Exit codes:
    0  - measured and within threshold (pass), or advisory
    1  - bad arguments / config unreadable / adb missing
    11 - measured but over threshold (fail)

.PARAMETER DeviceId
  Specific adb device id.

.PARAMETER Checkpoint
  Checkpoint to measure: cold-start | list-scroll | player-open | network-listing |
  streams-open | streams-search | streams-list-scroll | streams-grid-scroll | streams-peak-memory.

.PARAMETER ElapsedMs
  Caller-supplied elapsed time (ms) for checkpoints the skill times around a UI action
  (player-open, network-listing, streams-open, streams-search).

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [Parameter(Mandatory)][ValidateSet(
        'cold-start', 'list-scroll', 'player-open', 'network-listing',
        'streams-open', 'streams-search', 'streams-list-scroll', 'streams-grid-scroll',
        'streams-peak-memory',
        'play-anon-memory', 'play-bitmap-memory')]
    [string]$Checkpoint,
    [int]$ElapsedMs = -1,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot     = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$DebugPackage = 'com.sza.fastmediasorter.debug'
$CodePackage  = 'com.sza.fastmediasorter'
$ConfigPath   = Join-Path $PSScriptRoot 'prerelease.config.psd1'

function Get-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $c = Join-Path $root 'platform-tools\adb.exe'; if (Test-Path $c) { return $c } }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $known) { return $known }
    return $null
}

# True for an Android emulator (qemu/ranchu/goldfish or an SDK image). On such targets the
# gfxinfo janky% scroll metric is structurally inflated by software/host-GPU rendering and is
# therefore reported but not release-gating (record marked advisory; the verdict aggregator skips
# advisory records). Physical devices return false and gate normally.
function Test-IsEmulator {
    param([string]$Adb, [string[]]$Target)
    $qemu  = "$(& $Adb @Target shell getprop ro.kernel.qemu 2>&1)".Trim()
    $bqemu = "$(& $Adb @Target shell getprop ro.boot.qemu 2>&1)".Trim()
    $hw    = "$(& $Adb @Target shell getprop ro.hardware 2>&1)".Trim()
    $model = "$(& $Adb @Target shell getprop ro.product.model 2>&1)".Trim()
    if ($qemu -eq '1' -or $bqemu -eq '1') { return $true }
    if ($hw -in @('ranchu', 'goldfish'))  { return $true }
    if ($model -match 'sdk|emulator|Android SDK') { return $true }
    return $false
}

# S2100: the process state Play attributes a memory sample to, read from oom_score_adj rather than
# inferred from what the caller believes it did. Boundaries are the framework's own adj constants:
# FOREGROUND_APP_ADJ 0, PERCEPTIBLE_APP_ADJ 200, CACHED_APP_MIN_ADJ 900. Returns 'unknown' when the
# process is gone or /proc is unreadable - never a guess, because a mislabelled sample is compared
# against the wrong threshold and reads as a pass or a failure that never happened.
function Get-ProcessState {
    param([string]$Adb, [string[]]$Target, [string]$Package)
    $procPid = (("$(& $Adb @Target shell pidof $Package 2>&1)".Trim()) -split '\s+')[0]
    if ($procPid -notmatch '^\d+$') { return @{ state = 'unknown'; pid = $null } }
    $adj = "$(& $Adb @Target shell "cat /proc/$procPid/oom_score_adj 2>/dev/null" 2>&1)".Trim()
    if ($adj -notmatch '^-?\d+$') { return @{ state = 'unknown'; pid = $procPid } }
    $value = [int]$adj
    $state = if ($value -le 0) { 'foreground' }
             elseif ($value -le 200) { 'perceptible' }
             elseif ($value -lt 900) { 'background' }
             else { 'cached' }
    return @{ state = $state; pid = $procPid }
}

# S2100: physical RAM rounded to the nearest bucket Play declares. Rounding rather than flooring is
# deliberate - a phone advertised as 8 GB reports slightly under it in MemTotal because the kernel
# and reserved regions are already deducted, so flooring would put every device one bucket low and
# judge it against a stricter limit than Play applies.
function Get-PlayRamBucketGb {
    param([string]$Adb, [string[]]$Target, [string[]]$Buckets)
    $meminfo = & $Adb @Target shell "cat /proc/meminfo 2>/dev/null" 2>&1
    $m = [regex]::Match("$meminfo", 'MemTotal:\s*(\d+)\s*kB')
    if (-not $m.Success) { return $null }
    $totalGb = [double]$m.Groups[1].Value / 1048576.0
    $nearest = $null; $bestDelta = [double]::MaxValue
    foreach ($b in $Buckets) {
        $delta = [Math]::Abs([double]$b - $totalGb)
        if ($delta -lt $bestDelta) { $bestDelta = $delta; $nearest = $b }
    }
    return @{ bucket = $nearest; totalGb = [Math]::Round($totalGb, 2) }
}

$adb = Get-Adb
if (-not $adb) { if ($Json) { '{"error":"adb not found"}' } else { Write-Host 'adb not found' }; exit 1 }
$adbTarget = @(); if ($DeviceId) { $adbTarget = @('-s', $DeviceId) }

# Capture the raw measured value for the requested checkpoint.
$measured     = $null
$detail       = ''
$insufficient = $false
# Set by a checkpoint that measured something other than the quantity it was asked for. Such a
# record is reported but never gated on: the number is not wrong, it is a different number.
$advisoryRequested = $false

# Minimum rendered frames before a janky-frame percentage is treated as comparable between runs.
$MinFramesForJank = 100
switch ($Checkpoint) {
    'cold-start' {
        & $adb @adbTarget shell am force-stop $DebugPackage *> $null
        $out = & $adb @adbTarget shell am start -W -n "$DebugPackage/$CodePackage.ui.main.MainActivity" 2>&1
        $m = [regex]::Match("$out", 'TotalTime:\s*(\d+)')
        if ($m.Success) { $measured = [int]$m.Groups[1].Value; $detail = "am start -W TotalTime" }
        else { $detail = "TotalTime not parsed" }
    }
    { $_ -in @('list-scroll', 'streams-list-scroll', 'streams-grid-scroll', 'streams-search') } {
        # gfxinfo reset + scroll are driven by the skill before this call; read the stats now.
        $gfx = & $adb @adbTarget shell dumpsys gfxinfo $DebugPackage 2>&1
        $m = [regex]::Match("$gfx", 'Janky frames:\s*\d+\s*\(([\d.]+)%\)')
        $framesMatch = [regex]::Match("$gfx", 'Total frames rendered:\s*(\d+)')
        $frames = if ($framesMatch.Success) { [int]$framesMatch.Groups[1].Value } else { 0 }
        if ($m.Success) { $measured = [double]$m.Groups[1].Value; $detail = "gfxinfo janky % over $frames frames" }
        else { $detail = "janky frames not parsed" }
        # A percentage over a handful of frames is arithmetic, not a measurement. Measured on an
        # emulator under host build load, a 12-swipe burst rendered 27-30 frames and three identical
        # back-to-back runs returned 46%, 56% and 60% - a spread wider than any change worth
        # detecting. Below the floor the record is marked insufficient so a caller cannot compare it
        # against another run and believe the difference means something.
        if ($frames -lt $MinFramesForJank) {
            $insufficient = $true
            $detail = "$detail (INSUFFICIENT SAMPLE: under $MinFramesForJank frames - not comparable)"
        }
    }
    'streams-open' {
        # ActivityTaskManager logs "Displayed <pkg>/<activity>: +Xms" for every activity the system
        # brings to the foreground, so the screen-open time is available without app code and without
        # `am start` - which StreamsActivity refuses anyway, being android:exported="false". The caller
        # taps through to the screen; this reads what the system already recorded. -ElapsedMs stays as
        # the fallback for a target whose logcat has already rotated past the marker.
        $log = & $adb @adbTarget logcat -d -s ActivityTaskManager:I 2>&1
        $matches_ = [regex]::Matches("$log", 'Displayed\s+\S*StreamsActivity[^+]*\+(?:(\d+)s)?(\d+)ms')
        if ($matches_.Count -gt 0) {
            $last = $matches_[$matches_.Count - 1]
            $seconds = if ($last.Groups[1].Success) { [int]$last.Groups[1].Value } else { 0 }
            $measured = ($seconds * 1000) + [int]$last.Groups[2].Value
            $detail = "ActivityTaskManager Displayed marker"
        }
        elseif ($ElapsedMs -ge 0) { $measured = $ElapsedMs; $detail = "caller-supplied elapsed ms (no Displayed marker in logcat)" }
        else { $detail = "no Displayed marker in logcat and no -ElapsedMs supplied" }
    }
    'streams-peak-memory' {
        # VmHWM is the process high-water resident set, so it answers "did the peak grow" directly.
        # dumpsys meminfo only reports the sample taken at call time, which a scroll can easily miss;
        # it is the fallback for targets where /proc/<pid>/status is not readable by the shell user.
        $procPid = (("$(& $adb @adbTarget shell pidof $DebugPackage 2>&1)".Trim()) -split '\s+')[0]
        if ($procPid -match '^\d+$') {
            $status = & $adb @adbTarget shell "cat /proc/$procPid/status 2>/dev/null" 2>&1
            $m = [regex]::Match("$status", 'VmHWM:\s*(\d+)\s*kB')
            if ($m.Success) { $measured = [int]$m.Groups[1].Value; $detail = "peak RSS kB (/proc VmHWM)" }
        }
        if ($null -eq $measured) {
            $mem = & $adb @adbTarget shell dumpsys meminfo $DebugPackage 2>&1
            $m = [regex]::Match("$mem", 'TOTAL PSS:\s*(\d+)')
            if ($m.Success) {
                $measured = [int]$m.Groups[1].Value
                $detail = "current total PSS kB (dumpsys meminfo) - /proc unreadable, this is a sample not a peak"
            }
            else { $detail = "peak memory not parsed (no running process?)" }
        }
    }
    'play-anon-memory' {
        # Anonymous + Swap from smaps_rollup is exactly what Play names: private process memory,
        # active and compressed, with code and assets excluded. Both fields are needed - a device
        # under pressure moves anonymous pages into zram, where they leave Anonymous and appear in
        # Swap, so reading only the first understates the metric precisely when it matters most.
        #
        # The file is readable only by the process's own uid, so the shell user cannot cat it
        # directly and `run-as` is the only route on an unrooted device. Two traps, both measured
        # 2026-08-27: run-as works only for a DEBUGGABLE package, and the path must name the app's
        # real pid - `/proc/self` under run-as is the cat process itself, which reports a few
        # hundred kB and looks like a plausible answer.
        $procPid = (("$(& $adb @adbTarget shell pidof $DebugPackage 2>&1)".Trim()) -split '\s+')[0]
        if ($procPid -match '^\d+$') {
            $rollup = (& $adb @adbTarget shell "run-as $DebugPackage cat /proc/$procPid/smaps_rollup 2>/dev/null" 2>&1) -join "`n"
            $anon = [regex]::Match($rollup, '(?m)^Anonymous:\s*(\d+)\s*kB')
            $swap = [regex]::Match($rollup, '(?m)^Swap:\s*(\d+)\s*kB')
            if ($anon.Success) {
                $swapKb   = if ($swap.Success) { [int]$swap.Groups[1].Value } else { 0 }
                $measured = [int]$anon.Groups[1].Value + $swapKb
                $detail   = "anonymous $($anon.Groups[1].Value) kB + swap $swapKb kB (smaps_rollup via run-as)"
            }
        }
        if ($null -eq $measured) {
            # Fallback for a non-debuggable build. Sums the App Summary Rss rows that hold anonymous
            # memory and deliberately omits Code, which is what Play means by "code and assets do
            # not count". Advisory: it is a close proxy, not the kernel's own figure.
            $mem = (& $adb @adbTarget shell dumpsys meminfo $DebugPackage 2>&1) -join "`n"
            $sum = 0; $found = $false
            foreach ($row in @('Java Heap', 'Native Heap', 'Stack', 'Graphics')) {
                $m = [regex]::Match($mem, "(?m)^\s*$row`:\s+\d+\s+(\d+)")
                if ($m.Success) { $sum += [int]$m.Groups[1].Value; $found = $true }
            }
            if ($found) {
                $measured = $sum
                $detail = "App Summary Rss sum of Java Heap + Native Heap + Stack + Graphics = $sum kB, Code excluded - PROXY for anonymous RSS, run-as unavailable (non-debuggable build?)"
                $advisoryRequested = $true
            }
            else { $detail = 'anonymous memory not parsed (no running process?)' }
        }
    }
    'play-bitmap-memory' {
        # Native heap is an UPPER BOUND, not the bitmap figure: since API 26 bitmap pixels live in
        # the native heap next to every other native allocation, and nothing visible from the shell
        # separates them. Read from the App Summary Rss column, not the detail table's first column,
        # which is Pss and understates a process sharing pages with zygote.
        # The graphics figure is reported alongside for context only - it counts GL and EGL surfaces,
        # which are not bitmaps and must not be added in.
        $mem = (& $adb @adbTarget shell dumpsys meminfo $DebugPackage 2>&1) -join "`n"
        $native = [regex]::Match($mem, '(?m)^\s*Native Heap:\s+\d+\s+(\d+)')
        $gfx    = [regex]::Match($mem, '(?m)^\s*Graphics:\s+\d+\s+(\d+)')
        if ($native.Success) {
            $measured = [int]$native.Groups[1].Value
            $gfxKb    = if ($gfx.Success) { $gfx.Groups[1].Value } else { 'n/a' }
            $detail   = "native heap Rss $measured kB, graphics Rss $gfxKb kB - UPPER BOUND on bitmap memory, not the figure itself"
        }
        else { $detail = 'native heap not parsed (no running process?)' }
    }
    default {
        # player-open / network-listing / streams-open / streams-search: the skill times the UI
        # action and passes -ElapsedMs.
        if ($ElapsedMs -ge 0) { $measured = $ElapsedMs; $detail = "caller-supplied elapsed ms" }
        else { $detail = "no -ElapsedMs supplied" }
    }
}

# ---------- threshold compare (step 03.3) ----------
if (-not (Test-Path $ConfigPath)) {
    if ($Json) { '{"error":"config not found"}' } else { Write-Host 'config not found' }; exit 1
}
$config     = Import-PowerShellDataFile $ConfigPath
$thresholds = $config.Thresholds
$keyMap = @{
    'cold-start'          = 'ColdStart'
    'list-scroll'         = 'ListScroll'
    'player-open'         = 'PlayerOpen'
    'network-listing'     = 'NetworkListing'
    'streams-open'        = 'StreamsOpen'
    'streams-search'      = 'StreamsSearch'
    'streams-list-scroll' = 'StreamsListScroll'
    'streams-grid-scroll' = 'StreamsGridScroll'
    'streams-peak-memory' = 'StreamsPeakMemory'
    'play-anon-memory'    = 'PlayAnonMemory'
    'play-bitmap-memory'  = 'PlayBitmapMemory'
}
$limit  = $thresholds[$keyMap[$Checkpoint]].Limit

# S2100: every record carries the process state, because Play judges the memory metrics per state.
$stateInfo    = Get-ProcessState -Adb $adb -Target $adbTarget -Package $DebugPackage
$processState = $stateInfo.state
$ramBucketGb  = $null

# The two Play checkpoints resolve their limit here rather than reading a scalar Thresholds row,
# which is why both of those rows hold $null. An unresolvable limit (unknown state, unreadable
# MemTotal, or a state Play declares no limit for) leaves $limit null and the record advisory:
# "not judged" is the honest verdict, and failing closed on it would report a threshold breach
# that Play itself does not define.
if ($Checkpoint -eq 'play-anon-memory') {
    $grid   = $config.PlayMemory.AnonMemoryKb
    $bucket = Get-PlayRamBucketGb -Adb $adb -Target $adbTarget -Buckets ($grid.Keys)
    if ($null -ne $bucket -and $processState -ne 'unknown') {
        $ramBucketGb = $bucket.bucket
        $limit  = $grid[$bucket.bucket][$processState]
        $detail = "$detail | state=$processState, RAM $($bucket.totalGb) GB -> Play bucket $($bucket.bucket) GB"
    }
    else {
        $advisoryRequested = $true
        $detail = "$detail | limit unresolved (state=$processState, MemTotal readable=$($null -ne $bucket)) - not judged"
    }
}
elseif ($Checkpoint -eq 'play-bitmap-memory') {
    $row = $config.PlayMemory.BitmapMemoryKb
    if ($row.ContainsKey($processState)) {
        $limit  = $row[$processState]
        $detail = "$detail | state=$processState"
    }
    else {
        # foreground included: Play declares no bitmap limit there and inventing one would gate on
        # a rule that does not exist.
        $advisoryRequested = $true
        $detail = "$detail | state=$processState, Play declares no bitmap threshold for this state - not judged"
    }
}

# Unmeasured (null) fails closed; otherwise pass when measured value is within the limit.
$pass = ($null -ne $measured) -and ($limit -ne $null) -and ([double]$measured -le [double]$limit)

# Advisory: list-scroll gfxinfo janky% is not a valid release gate on an emulator. Keep the raw
# measured/pass for transparency but flag the record so the verdict aggregator does not gate on it.
$advisory = $false
if ($Checkpoint -in @('list-scroll', 'streams-list-scroll', 'streams-grid-scroll', 'streams-search') -and
    (Test-IsEmulator -Adb $adb -Target $adbTarget)) {
    $advisory = $true
    $detail   = "$detail (advisory: emulator software render, not release-gating)"
}

# An insufficient frame sample is never allowed to gate: the number is not wrong so much as
# meaningless, and failing a release on it would be as unjustified as passing one.
if ($insufficient) { $advisory = $true }
if ($advisoryRequested) { $advisory = $true }

$record = [ordered]@{
    checkpoint   = $Checkpoint
    measured     = $measured
    limit        = $limit
    pass         = [bool]$pass
    advisory     = [bool]$advisory
    insufficient = [bool]$insufficient
    processState = $processState
    ramBucketGb  = $ramBucketGb
    detail       = $detail
}
if ($Json) { $record | ConvertTo-Json -Compress }
else { Write-Host ("{0}: measured={1} limit={2} pass={3} advisory={4} state={5} ({6})" -f $Checkpoint, $measured, $limit, $pass, $advisory, $processState, $detail) }
# An advisory record never reports failure on a standalone call; the aggregator owns gating.
exit $(if ($pass -or $advisory) { 0 } else { 11 })
