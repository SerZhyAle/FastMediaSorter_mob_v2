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

  Exit codes:
    0  - measured and within threshold (pass)
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
        'streams-peak-memory')]
    [string]$Checkpoint,
    [int]$ElapsedMs = -1,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

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

$adb = Get-Adb
if (-not $adb) { if ($Json) { '{"error":"adb not found"}' } else { Write-Host 'adb not found' }; exit 1 }
$adbTarget = @(); if ($DeviceId) { $adbTarget = @('-s', $DeviceId) }

# Capture the raw measured value for the requested checkpoint.
$measured     = $null
$detail       = ''
$insufficient = $false

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
$thresholds = (Import-PowerShellDataFile $ConfigPath).Thresholds
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
}
$limit  = $thresholds[$keyMap[$Checkpoint]].Limit

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

$record = [ordered]@{
    checkpoint   = $Checkpoint
    measured     = $measured
    limit        = $limit
    pass         = [bool]$pass
    advisory     = [bool]$advisory
    insufficient = [bool]$insufficient
    detail       = $detail
}
if ($Json) { $record | ConvertTo-Json -Compress }
else { Write-Host ("{0}: measured={1} limit={2} pass={3} advisory={4} ({5})" -f $Checkpoint, $measured, $limit, $pass, $advisory, $detail) }
# An advisory record never reports failure on a standalone call; the aggregator owns gating.
exit $(if ($pass -or $advisory) { 0 } else { 11 })
