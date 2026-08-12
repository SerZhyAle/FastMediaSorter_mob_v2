<#
.SYNOPSIS
  Generic ad-hoc adb swiss-army for fast manual work with connected emulators / devices.

.DESCRIPTION
  One entry point for the quick interactive device chores that are NOT covered by the
  purpose-bound scripts (device-ready.ps1 = pre-flight, extract-device-logs.ps1 = harvest,
  build-<flavor>-device.ps1 = build+install, maestro-run.ps1 = repeatable flows).

  Mirrors device-ready.ps1 conventions: same adb auto-discovery (adb is not on PATH on the
  dev machine), same -Json contract, same stable exit-code table. Runs natively - costs ~0
  LLM tokens; the agent reads only the compact verdict (or -Json object).

  Verbs (first positional argument):
    help                 list verbs (default when no verb given)
    devices              list online devices with model + Android version (no selection needed)
    props                selected device: model, Android release, SDK, density, wm size
    current              focused activity / package on the selected device
    launch               start the app (debug build: explicit MainActivity, bypasses the
                         LeakCanary launcher trap)
    stop                 force-stop the app
    logcat-clear         empty the device logcat buffer (alias: log-clear). Touches no app state
    wipe-data            DESTRUCTIVE: pm clear (app data, runtime grants and onboarding gone).
                         Requires -Yes
    clear                REMOVED - refuses and names its two replacements. It used to mean wipe-data
                         and was twice mistaken for "clear the log" (S1167, S1572)
    install              install -r -d an APK (-Apk <path>, or newest debug APK for -Flavor)
    uninstall            DESTRUCTIVE: uninstall the resolved package. Requires -Yes
    shot                 screenshot to temp/scratch/<device>_<TS>.png (screencap on device, then
                         pull). Warns when the focused window carries FLAG_SECURE, because such a
                         capture is black by design and reads as a rendering bug (-Json:
                         secureWindow)
    log                  the app's own log lines: every line whose pid belongs to an app
                         process, plus every line whose text names the package. -Tail N
                         (default 200), -Grep <regex>; full capture also to temp/scratch/
    tap                  input tap -X <x> -Y <y>
    text                 input text -Text "<string>" (spaces handled)
    key                  input keyevent -Key <name-or-code> (e.g. BACK, 4, KEYCODE_HOME)
    prefs                pull app_settings.xml via run-as to temp/scratch/ (debuggable build only)
    pull                 fetch a file off the device: -Remote <path> [-Local <path>] [-Latest].
                         Without -Local the file lands in temp/scratch/ under its own name.
                         -Latest treats -Remote as a directory or glob and takes the newest match
    push                 send a local file to the device: -Local <path> -Remote <path>
    shell                arbitrary passthrough: -Cmd "<adb shell command>"

  Why pull/push live here rather than in a bare `adb` call (S1578): the wrapper keeps the adb
  discovery, the device selection and the exit contract, and - the reason it was worth a ticket -
  the remote path never passes through bash, where MSYS silently rewrites `/sdcard/x` into a path
  inside the Git installation and adb then reports a missing remote object.

  Package resolution (verbs that act on the app): default debug id
  com.sza.fastmediasorter.debug; -Release switches to com.sza.fastmediasorter; -Package
  overrides explicitly. If the chosen id is not installed, the other variant is tried before
  giving up.

  How `log` decides a line is the app's (S1332): the pid set comes from `pidof <pkg>`, from
  `ps -A -o PID,NAME` (which also catches any :sub process), and from the `Start proc` lines
  in the capture itself (which recovers a process that already exited inside the window).
  A line is kept when its threadtime pid column is in that set, OR when its text names the
  package - the second arm is what keeps system-side lines such as `ANR in <pkg>` and
  `AndroidRuntime: Process: <pkg>`, which run under system_server's pid. An empty pid set is
  a legal state: the filter degrades to text-only matching and the verdict becomes WARN.
  When -Grep is given and the filter dropped lines the pattern matched, the verdict is WARN
  with a count. That is deliberately NOT an error and has no exit code of its own - a silent
  `OK 0 line(s)` was the whole defect, an error would break every caller. Do not add one.

  Exit codes (stable; mirror device-ready.ps1 where they overlap):
    0 - OK
    1 - adb not found, or bad arguments
    2 - no online device
    3 - multiple online devices and -DeviceId not supplied (for verbs needing a device)
    4 - target package not installed (for app verbs)
    5 - a destructive verb was refused: `clear` (removed), or `wipe-data`/`uninstall` without -Yes.
        Nothing was executed on the device
    6 - `pull`: the remote path does not exist on the device. Distinct from 7 because "the file was
        never written" and "the transfer failed" call for different next moves
    7 - the underlying adb command returned non-zero

  Human output: one verdict line per verb (plus the data the verb produces).
  Machine output (with -Json): a single JSON object on stdout, all human noise suppressed.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 devices
  List online devices with model + Android version.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 shot -DeviceId emulator-5554
  Grab a screenshot from a specific device into temp/scratch/.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 log -Tail 400 -Grep "S0035|Network"
  Tail the last 400 app log lines, keep only those matching the regex.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 launch -Release
  Launch the release build (com.sza.fastmediasorter) unambiguously.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 logcat-clear
  Empty the logcat buffer. This is what "clear the log" means - it never touches app data.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 wipe-data -Yes
  Reset the app to a first-run state. One-way: settings, runtime grants and onboarding are gone.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 pull -Remote "/sdcard/DCIM/Camera/*.jpg" -Latest
  Grab the newest camera frame into temp/scratch/ - the usual "shoot, then look at the file" step.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 push -Local temp/scratch/fixture.mp4 -Remote /sdcard/Movies/
  Place a fixture on the device before a scenario runs.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Verb = 'help',
    [string]$DeviceId,
    [string]$Package,
    [switch]$Release,
    [int]$Tail = 200,
    [string]$Grep,
    [string]$Apk,
    [ValidateSet('standard', 'lite', 'photos', 'legacy', 'noLegal')]
    [string]$Flavor = 'standard',
    [int]$X,
    [int]$Y,
    [string]$Text,
    [string]$Key,
    [string]$Cmd,
    [string]$Remote,
    [string]$Local,
    # pull: read -Remote as a directory or glob and take its newest entry.
    [switch]$Latest,
    [switch]$Json,
    # Confirmation for the one-way verbs (wipe-data, uninstall). This script is called by agents and by
    # other scripts, so an interactive prompt is not available - a required flag is the only gate that can
    # actually fire. It waives the confirmation only: device selection and package resolution still run.
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'

# Canonical app coordinates (see install/builder scripts and /spec-test-device).
$BASE_PACKAGE   = 'com.sza.fastmediasorter'
$DEBUG_PACKAGE  = "$BASE_PACKAGE.debug"
$MAIN_ACTIVITY  = 'com.sza.fastmediasorter.ui.main.MainActivity'

. (Join-Path $PSScriptRoot 'lib/adb-log-filter.ps1')

# ---------- result shape ----------

$script:result = [ordered]@{
    ok       = $false
    exitCode = 0
    verb     = $Verb
    device   = $DeviceId
    package  = $null
    data     = $null
    reason   = $null
}

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Emit-Ok {
    param($Data)
    $script:result.ok       = $true
    $script:result.exitCode = 0
    $script:result.data     = $Data
    if ($Json) { $script:result | ConvertTo-Json -Compress -Depth 6 }
    exit 0
}

function Fail {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.reason   = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress -Depth 6
    } else {
        Write-Host "FAIL ($Code) - $Reason" -ForegroundColor Red
    }
    exit $Code
}

# ---------- adb discovery (parity with device-ready.ps1) ----------
# S1341: Find-Adb lives in lib/find-adb.ps1 so spec-prerelease.md and other callers
# share one discovery order instead of hand-rolling their own hardcoded fallback.

. "$PSScriptRoot/lib/find-adb.ps1"

$adb = Find-Adb
if (-not $adb) {
    Fail 1 "adb.exe not found (checked ANDROID_HOME, ANDROID_SDK_ROOT, PATH, %LOCALAPPDATA%\Android\Sdk\platform-tools)"
}

# ---------- device enumeration / selection ----------

function Get-OnlineDevices {
    $raw = & $adb devices 2>$null
    if ($LASTEXITCODE -ne 0) { Fail 1 "adb devices returned exit $LASTEXITCODE" }
    $lines = $raw -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^\s*List of devices' }
    $devs = foreach ($line in $lines) {
        $parts = ($line -split "\s+", 2) | Where-Object { $_ }
        if ($parts.Count -ge 2 -and $parts[1] -eq 'device') { $parts[0] }
    }
    return @($devs)
}

function Select-Device {
    # @() guards the single-device case: a one-element array returned from a function
    # unwraps to a scalar string on assignment, and $devs[0] would then index a char.
    $devs = @(Get-OnlineDevices)
    if ($devs.Count -eq 0) { Fail 2 "no online device (boot an emulator or connect a phone, then re-run)" }
    if ($DeviceId) {
        if ($devs -notcontains $DeviceId) { Fail 2 "device '$DeviceId' is not online (online: $($devs -join ', '))" }
        return $DeviceId
    }
    if ($devs.Count -gt 1) { Fail 3 "multiple online devices ($($devs -join ', ')); pass -DeviceId" }
    return $devs[0]
}

# Run an `adb -s <id> ...` command, returning its raw stdout. Fails (7) on non-zero exit.
function Invoke-Adb {
    param([string]$Id, [string[]]$AdbArgs, [switch]$AllowFail)
    $out = & $adb -s $Id @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0 -and -not $AllowFail) {
        Fail 7 "adb $($AdbArgs -join ' ') failed (exit $LASTEXITCODE): $($out -join ' ')"
    }
    return $out
}

# S1506: a capture taken from a FLAG_SECURE window comes back black (or zero-byte) by design.
# That artifact was twice read as a rendering failure and cost a P90 ticket, so `shot` reports the
# flag next to the file instead of leaving the reader to guess. Every probe is best-effort: a
# screenshot must never fail on account of its own diagnostics.
function Test-SecureFocusedWindow {
    param([string]$Id)
    # `dumpsys window`, not `dumpsys window windows`: on Android 15 only the former prints the
    # mCurrentFocus line, and it carries the per-window "Window #N .." blocks as well, so one call
    # answers both halves.
    $raw = (Invoke-Adb $Id @('shell', 'dumpsys', 'window') -AllowFail) -join "`n"
    $focus = [regex]::Match($raw, 'mCurrentFocus=Window\{(\w+)')
    if (-not $focus.Success) { return $false }
    $focusHash = $focus.Groups[1].Value
    # mCurrentFocus only names the window; its flags live in that window's own block.
    foreach ($block in ($raw -split '(?m)^\s*Window #\d+ ')) {
        if ($block -notlike "Window{$focusHash*") { continue }
        # S1580: One UI prints the flags as a raw hex mask on its own line (`fl=81812180`) while AOSP
        # prints flag names, so matching the word SECURE answered "not protected" for every window on
        # a Samsung device - the exact false negative this probe exists to prevent. Read the number
        # when there is one (FLAG_SECURE is 0x2000) and keep the name match for the name-printing builds.
        $flags = [regex]::Match($block, '(?m)^\s*fl=#?([0-9a-fA-F]{1,16})\s*$')
        if ($flags.Success) {
            return [bool]([Convert]::ToUInt64($flags.Groups[1].Value, 16) -band 0x2000)
        }
        return [bool]($block -match '(?m)^\s*fl=[^\r\n]*\bSECURE\b')
    }
    return $false
}

# Resolve which app package id to act on: explicit -Package wins; else default debug
# (or release with -Release); fall back to the other variant if the chosen one is absent.
function Resolve-Package {
    param([string]$Id)
    if ($Package) { return $Package }
    $primary  = if ($Release) { $BASE_PACKAGE } else { $DEBUG_PACKAGE }
    $fallback = if ($Release) { $DEBUG_PACKAGE } else { $BASE_PACKAGE }
    foreach ($pkg in @($primary, $fallback)) {
        $pmRaw = & $adb -s $Id shell pm list packages $pkg 2>$null
        foreach ($pmLine in ($pmRaw -split "`r?`n")) {
            if ($pmLine.Trim() -eq "package:$pkg") { return $pkg }
        }
    }
    Fail 4 "neither '$primary' nor '$fallback' is installed on $Id (build/install first)"
}

function Get-TempDir {
    # Ad-hoc CLI outputs are no-ticket scratch by nature (CLAUDE.md Rule 10.1) -> temp/scratch/.
    $repoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path
    $tempDir  = Join-Path (Join-Path $repoRoot 'temp') 'scratch'
    if (-not (Test-Path -Path $tempDir -PathType Container)) {
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    }
    return $tempDir
}

# Per-call timestamp. Plain Get-Date is fine here (this is an interactive CLI, not a
# replayable workflow), unlike the workflow runtime which forbids it.
function Get-Stamp { (Get-Date).ToString('yyyyMMdd_HHmmss') }

# ---------- verbs ----------

switch ($Verb.ToLowerInvariant()) {

    'help' {
        if ($Json) { Emit-Ok @{ verbs = 'help,devices,props,current,launch,stop,logcat-clear,wipe-data,install,uninstall,shot,log,tap,text,key,prefs,pull,push,shell' } }
        Write-Host "adb.ps1 - ad-hoc device swiss-army" -ForegroundColor Cyan
        Write-Host "Usage: pwsh -NoProfile -File scripts/devtest/adb.ps1 <verb> [options]" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  devices    list online devices (model + Android version)" -ForegroundColor White
        Write-Host "  props      selected device props (model, release, sdk, density, size)" -ForegroundColor White
        Write-Host "  current    focused activity / package" -ForegroundColor White
        Write-Host "  launch     start app (debug: explicit MainActivity)" -ForegroundColor White
        Write-Host "  stop       force-stop app" -ForegroundColor White
        Write-Host "  logcat-clear  empty the logcat buffer (alias log-clear) - no app state touched" -ForegroundColor White
        Write-Host "  wipe-data  DESTRUCTIVE pm clear - needs -Yes (data, grants, onboarding gone)" -ForegroundColor Yellow
        Write-Host "  install    install -r -d (-Apk <path> | -Flavor <std|lite|photos|legacy|noLegal>)" -ForegroundColor White
        Write-Host "  uninstall  DESTRUCTIVE uninstall resolved package - needs -Yes" -ForegroundColor Yellow
        Write-Host "  shot       screenshot to temp/scratch/" -ForegroundColor White
        Write-Host "  log        logcat -d app tail (-Tail N, -Grep regex)" -ForegroundColor White
        Write-Host "  tap        input tap -X <x> -Y <y>" -ForegroundColor White
        Write-Host "  text       input text -Text <string>" -ForegroundColor White
        Write-Host "  key        input keyevent -Key <name-or-code>" -ForegroundColor White
        Write-Host "  prefs      pull app_settings.xml to temp/scratch/ (run-as)" -ForegroundColor White
        Write-Host "  pull       fetch a file: -Remote <path> [-Local <path>] [-Latest] -> temp/scratch/" -ForegroundColor White
        Write-Host "  push       send a file: -Local <path> -Remote <path>" -ForegroundColor White
        Write-Host "  shell      passthrough -Cmd <adb shell command>" -ForegroundColor White
        Write-Host ""
        Write-Host "Common options: -DeviceId <id> -Release -Package <id> -Json" -ForegroundColor Gray
        exit 0
    }

    'devices' {
        $devs = @(Get-OnlineDevices)
        if ($devs.Count -eq 0) { Fail 2 "no online device" }
        $rows = foreach ($id in $devs) {
            # NB: avoid a local named $release - it case-collides with the [switch]$Release param.
            $model   = (& $adb -s $id shell getprop ro.product.model 2>$null | Out-String).Trim()
            $rel     = (& $adb -s $id shell getprop ro.build.version.release 2>$null | Out-String).Trim()
            $sdk     = (& $adb -s $id shell getprop ro.build.version.sdk 2>$null | Out-String).Trim()
            [pscustomobject]@{ id = $id; model = $model; android = $rel; sdk = $sdk }
        }
        if ($Json) { Emit-Ok @($rows) }
        foreach ($r in $rows) {
            Write-Host ("  {0,-20} {1}  Android {2} (SDK {3})" -f $r.id, $r.model, $r.android, $r.sdk) -ForegroundColor Green
        }
        Write-Host "OK $($rows.Count) device(s) online" -ForegroundColor Cyan
        exit 0
    }

    'props' {
        $id = Select-Device
        $script:result.device = $id
        # NB: avoid a local named $release - it case-collides with the [switch]$Release param.
        $model   = (Invoke-Adb $id @('shell', 'getprop', 'ro.product.model') | Out-String).Trim()
        $rel     = (Invoke-Adb $id @('shell', 'getprop', 'ro.build.version.release') | Out-String).Trim()
        $sdk     = (Invoke-Adb $id @('shell', 'getprop', 'ro.build.version.sdk') | Out-String).Trim()
        $density = (Invoke-Adb $id @('shell', 'wm', 'density') | Out-String).Trim()
        $size    = (Invoke-Adb $id @('shell', 'wm', 'size') | Out-String).Trim()
        $data = [ordered]@{ id = $id; model = $model; android = $rel; sdk = $sdk; density = $density; size = $size }
        if ($Json) { Emit-Ok $data }
        Write-Host "device : $id" -ForegroundColor Green
        Write-Host "model  : $model" -ForegroundColor White
        Write-Host "android: $rel (SDK $sdk)" -ForegroundColor White
        Write-Host "$density" -ForegroundColor White
        Write-Host "$size" -ForegroundColor White
        exit 0
    }

    'current' {
        $id = Select-Device
        $script:result.device = $id
        # The resumed-activity field is named differently across Android versions
        # (mResumedActivity / ResumedActivity: / topResumedActivity=); match the common stem.
        $dump = Invoke-Adb $id @('shell', 'dumpsys', 'activity', 'activities') -AllowFail
        $line = ($dump -split "`r?`n" | Where-Object { $_ -match 'ResumedActivity' } | Select-Object -First 1)
        if (-not $line) {
            $win  = Invoke-Adb $id @('shell', 'dumpsys', 'window', 'windows') -AllowFail
            $line = ($win -split "`r?`n" | Where-Object { $_ -match 'mCurrentFocus' } | Select-Object -First 1)
        }
        $line = if ($line) { $line.Trim() } else { '(unknown)' }
        if ($Json) { Emit-Ok @{ id = $id; current = $line } }
        Write-Host $line -ForegroundColor Green
        exit 0
    }

    'launch' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        # Explicit component avoids the debug LeakCanary launcher pre-empting the app launcher.
        Invoke-Adb $id @('shell', 'am', 'start', '-n', "$pkg/$MAIN_ACTIVITY") | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg; component = "$pkg/$MAIN_ACTIVITY" } }
        Write-Host "LAUNCHED $pkg/$MAIN_ACTIVITY on $id" -ForegroundColor Green
        exit 0
    }

    'stop' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        Invoke-Adb $id @('shell', 'am', 'force-stop', $pkg) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "STOPPED $pkg on $id" -ForegroundColor Green
        exit 0
    }

    { $_ -in 'logcat-clear', 'log-clear' } {
        $id = Select-Device
        $script:result.device = $id
        Invoke-Adb $id @('logcat', '-c') | Out-Null
        if ($Json) { Emit-Ok @{ id = $id } }
        Write-Host "LOGCAT BUFFER CLEARED on $id" -ForegroundColor Green
        exit 0
    }

    # Was named 'clear'. Renamed because the old name sat two lines from 'log' in the verb list and was
    # twice read as "clear the log": S1167 (2026-07-26, a granted accessibility service revoked) and S1569
    # (2026-08-11, the owner's working phone wiped). See S1572.
    'wipe-data' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        if (-not $Yes) {
            Fail 5 "wipe-data refused: this erases $pkg data, runtime grants and onboarding on $id, and cannot be undone. Re-run with -Yes if that is what you want, or use logcat-clear to empty the log buffer."
        }
        $out = Invoke-Adb $id @('shell', 'pm', 'clear', $pkg) -AllowFail
        if (($out -join '') -notmatch 'Success') { Fail 7 "pm clear did not report Success: $($out -join ' ')" }
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "WIPED data for $pkg on $id" -ForegroundColor Green
        exit 0
    }

    # Refuses rather than forwards: forwarding to wipe-data would keep the misreading alive, and naming
    # both replacements is what turns the mistake into a corrected instruction.
    'clear' {
        Fail 5 "the 'clear' verb was removed because it was ambiguous. Did you mean 'logcat-clear' (empty the log buffer, touches nothing) or 'wipe-data -Yes' (erase app data, one-way)? Nothing was executed."
    }

    'install' {
        $id = Select-Device
        $script:result.device = $id
        $apkPath = $Apk
        if (-not $apkPath) {
            $repoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path
            $apkDir = Join-Path $repoRoot "app_v2\build\outputs\apk\$Flavor\debug"
            $metaPath = Join-Path $apkDir 'output-metadata.json'
            if (Test-Path -Path $metaPath -PathType Leaf) {
                try {
                    $meta = Get-Content -Path $metaPath -Raw | ConvertFrom-Json
                    if ($meta.elements -and $meta.elements.Count -gt 0 -and $meta.elements[0].outputFile) {
                        $apkPath = Join-Path $apkDir $meta.elements[0].outputFile
                    }
                } catch { }
            }
            if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
                $latest = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
                if ($latest) { $apkPath = $latest.FullName }
            }
        }
        if (-not $apkPath -or -not (Test-Path -Path $apkPath -PathType Leaf)) {
            Fail 1 "APK not found (pass -Apk <path>, or build the $Flavor debug variant first)"
        }
        Invoke-Adb $id @('install', '-r', '-d', $apkPath) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; apk = $apkPath } }
        Write-Host "INSTALLED $apkPath on $id" -ForegroundColor Green
        exit 0
    }

    'uninstall' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        if (-not $Yes) {
            Fail 5 "uninstall refused: this removes $pkg and everything it stored on $id, and cannot be undone. Re-run with -Yes if that is what you want."
        }
        Invoke-Adb $id @('uninstall', $pkg) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "UNINSTALLED $pkg from $id" -ForegroundColor Green
        exit 0
    }

    'shot' {
        $id = Select-Device
        $script:result.device = $id
        $stamp = Get-Stamp
        $name  = "$($id -replace '[^A-Za-z0-9_.-]', '_')_$stamp.png"
        $local = Join-Path (Get-TempDir) $name
        $remote = '/sdcard/_fms_shot.png'
        # screencap-then-pull keeps the PNG bytes intact (a `>` redirect of exec-out
        # corrupts binary on Windows pipelines).
        # Some emulator images cannot resolve their own default display and answer a bare
        # `screencap -p` with the usage text plus a zero-byte file; naming the display id
        # explicitly succeeds there, so fall back to it instead of reporting a dead capture.
        Invoke-Adb $id @('shell', 'screencap', '-p', $remote) -AllowFail | Out-Null
        $remoteSize = (Invoke-Adb $id @('shell', 'stat', '-c', '%s', $remote) -AllowFail) -join ''
        if ($remoteSize.Trim() -notmatch '^[1-9][0-9]*$') {
            $displayRaw = (Invoke-Adb $id @('shell', 'dumpsys', 'SurfaceFlinger', '--display-id') -AllowFail) -join "`n"
            if ($displayRaw -match 'Display\s+(\d+)') {
                Invoke-Adb $id @('shell', 'screencap', '-p', '-d', $Matches[1], $remote) | Out-Null
            }
        }
        Invoke-Adb $id @('pull', $remote, $local) | Out-Null
        Invoke-Adb $id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
        if (-not (Test-Path -Path $local -PathType Leaf)) { Fail 7 "screenshot pull produced no file" }
        $secureWindow = Test-SecureFocusedWindow $id
        if ($Json) { Emit-Ok @{ id = $id; file = $local; secureWindow = $secureWindow } }
        Write-Host "SHOT $local" -ForegroundColor Green
        if ($secureWindow) {
            Write-Host "NOTE the focused window carries FLAG_SECURE - this capture is expected to be" -ForegroundColor Yellow
            Write-Host "     black (or zero-byte). That is the flag working, not a rendering failure." -ForegroundColor Yellow
            Write-Host "     BaseActivity.applySecureFlagIfEnabled sets it on sensitive screens (Settings," -ForegroundColor Yellow
            Write-Host "     Add Resource, Resource Editor) while the 'secureSensitiveScreens' setting is on." -ForegroundColor Yellow
            Write-Host "     Read the layout from 'uiautomator dump' instead - a healthy node tree next to a" -ForegroundColor Yellow
            Write-Host "     black frame confirms this, it does not contradict it. To capture the screen for" -ForegroundColor Yellow
            Write-Host "     real, turn that setting off first. See docs/TEST_SCENARIOS.md." -ForegroundColor Yellow
        }
        exit 0
    }

    'log' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        # -v threadtime is already the device default, but pinning it makes the pid-column
        # parse deterministic instead of dependent on the device's own default.
        $raw = Invoke-Adb $id @('logcat', '-d', '-v', 'threadtime', '-t', "$Tail") -AllowFail
        $rawLines = $raw -split "`r?`n"

        # S1332: pid is the ownership signal - the app's own Timber lines carry a bare class
        # tag and name neither the package nor the project, so text alone never matched them.
        # The text arm survives as the second arm, for system-side lines about the app.
        $appPids = @()
        $pidofOut = (Invoke-Adb $id @('shell', 'pidof', $pkg) -AllowFail) -join ' '
        $appPids += @($pidofOut -split '\s+' | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
        $psOut = (Invoke-Adb $id @('shell', 'ps', '-A', '-o', 'PID,NAME') -AllowFail) -join "`n"
        foreach ($row in ($psOut -split "`r?`n")) {
            if ($row -match '^\s*(?<p>\d+)\s+(?<n>\S+)\s*$') {
                $name = $Matches['n']
                if ($name -eq $pkg -or $name.StartsWith("${pkg}:")) { $appPids += [int]$Matches['p'] }
            }
        }
        $appPids += Get-AppPidsFromLog -Lines $rawLines -BasePackage $BASE_PACKAGE
        $appPids = @($appPids | Sort-Object -Unique)

        $patterns = @([regex]::Escape($pkg), [regex]::Escape($BASE_PACKAGE), 'FastMediaSorter')
        $lines = Select-AppLogLines -Lines $rawLines -AppPids $appPids -TextPatterns $patterns
        $coverage = if ($Grep) { Measure-FilterCoverage -RawLines $rawLines -KeptLines $lines -Pattern $Grep } else { $null }
        if ($Grep) { $lines = $lines | Where-Object { $_ -match $Grep } }
        $logFile = Join-Path (Get-TempDir) "adb_log_$(Get-Stamp).log"
        ($raw -join "`n") | Out-File -FilePath $logFile -Encoding UTF8
        if ($Json) {
            Emit-Ok @{
                id = $id; package = $pkg; matched = @($lines).Count; file = $logFile
                rawMatched = if ($coverage) { $coverage.rawMatched } else { $null }
                suppressed = if ($coverage) { $coverage.suppressed } else { 0 }
                appPids    = $appPids
            }
        }
        foreach ($l in $lines) { Write-Host $l }
        # A filter that swallows matching lines must say so: the old silent OK 0 was
        # indistinguishable from an honest no-match and produced wrong Broken verdicts.
        # Never echo the caller's pattern - smoke.ps1 and prerelease-prepare.ps1 match their
        # own crash regex against this script's whole stdout.
        if ($coverage -and $coverage.suppressed -gt 0) {
            Write-Host "WARN $((@($lines)).Count) line(s) (window $Tail); full capture: $logFile" -ForegroundColor Yellow
            Write-Host "     the filter dropped $($coverage.suppressed) line(s) matching the pattern - read the capture file" -ForegroundColor Yellow
            exit 0
        }
        Write-Host "OK $((@($lines)).Count) line(s) (window $Tail); full capture: $logFile" -ForegroundColor Cyan
        exit 0
    }

    'tap' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $PSBoundParameters.ContainsKey('X') -or -not $PSBoundParameters.ContainsKey('Y')) {
            Fail 1 "tap needs -X <x> -Y <y>"
        }
        Invoke-Adb $id @('shell', 'input', 'tap', "$X", "$Y") | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; x = $X; y = $Y } }
        Write-Host "TAP ($X,$Y) on $id" -ForegroundColor Green
        exit 0
    }

    'text' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Text) { Fail 1 "text needs -Text <string>" }
        # adb input text: spaces must be %s; escape shell-special chars.
        $encoded = $Text -replace ' ', '%s'
        Invoke-Adb $id @('shell', 'input', 'text', $encoded) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; text = $Text } }
        Write-Host "TEXT typed on $id" -ForegroundColor Green
        exit 0
    }

    'key' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Key) { Fail 1 "key needs -Key <name-or-code> (e.g. BACK, 4, KEYCODE_HOME)" }
        Invoke-Adb $id @('shell', 'input', 'keyevent', $Key) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; key = $Key } }
        Write-Host "KEY $Key on $id" -ForegroundColor Green
        exit 0
    }

    'prefs' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        $local = Join-Path (Get-TempDir) "app_settings_$(Get-Stamp).xml"
        $out = & $adb -s $id shell "run-as $pkg cat /data/data/$pkg/shared_prefs/app_settings.xml" 2>$null
        if (-not $out) { Fail 7 "could not read app_settings.xml (non-debuggable build or file absent)" }
        ($out -join "`n") | Out-File -FilePath $local -Encoding UTF8
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg; file = $local } }
        Write-Host "PREFS $local" -ForegroundColor Green
        exit 0
    }

    'pull' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Remote) { Fail 1 "pull needs -Remote <device path> (add -Latest to take the newest match of a directory or glob)" }
        $remotePath = $Remote
        if ($Latest) {
            # -1t sorts newest first and the device shell expands the glob, so the mask never
            # reaches PowerShell, which would try to resolve it against the local filesystem.
            # -p marks directories with a trailing slash, which is how they are dropped here: "the
            # newest thing in this folder" means the newest file, never a subfolder.
            # @() guards the single-match case: one string indexed with [0] yields a char, not a line.
            $listing = @((Invoke-Adb $id @('shell', "ls -1pt $Remote") -AllowFail) -split "`r?`n" |
                Where-Object { $_ -and $_ -notmatch 'No such file|Permission denied' -and $_ -notmatch '/\s*$' })
            if ($listing.Count -eq 0) { Fail 6 "no file on $id matches '$Remote'" }
            $remotePath = $listing[0].Trim()
            # A listing prints bare names, not paths. The directory they belong to is -Remote itself when
            # it names a directory, and -Remote's parent when it carries a glob - getting this backwards
            # rebuilds a sibling of the real file and reports it as missing.
            if ($remotePath -notmatch '^/') {
                $parent = if ($Remote -match '[*?\[]') { $Remote -replace '/[^/]*$', '' } else { $Remote.TrimEnd('/') }
                $remotePath = "$parent/$remotePath"
            }
        }
        # %F alongside %s: a directory has a size too, so size alone cannot say what was pulled, and
        # `adb pull` of a directory lands a directory - which a file-only existence check reads as failure.
        # adb joins the shell arguments back into one command line for the device shell, so a path with
        # spaces or parentheses - `Download/App (1).apk`, exactly what -Latest tends to find - has to be
        # quoted here or the device shell splits it and the file reads as missing.
        $quotedRemote = "'" + ($remotePath -replace "'", "'\''") + "'"
        $statRaw = ((Invoke-Adb $id @('shell', "stat -c '%F|%s' $quotedRemote") -AllowFail) -join '').Trim()
        if ($statRaw -notmatch '^(?<kind>[^|]+)\|(?<size>\d+)$') { Fail 6 "'$remotePath' does not exist on $id" }
        $isDirectory = $Matches['kind'] -like '*directory*'
        $localPath = if ($Local) { $Local } else { Join-Path (Get-TempDir) (Split-Path -Path $remotePath -Leaf) }
        Invoke-Adb $id @('pull', $remotePath, $localPath) | Out-Null
        if (-not (Test-Path -Path $localPath)) { Fail 7 "pull of '$remotePath' produced no local file" }
        $bytes = if ($isDirectory) { 0 } else { (Get-Item -Path $localPath).Length }
        if ($Json) { Emit-Ok @{ id = $id; remote = $remotePath; file = $localPath; size = $bytes } }
        Write-Host "PULLED $localPath ($bytes bytes) from $remotePath" -ForegroundColor Green
        exit 0
    }

    'push' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Local -or -not $Remote) { Fail 1 "push needs -Local <local path> -Remote <device path>" }
        # Refused here rather than on the device: adb's own error for a missing source reads like a
        # device-side problem and sends the reader looking in the wrong place.
        if (-not (Test-Path -Path $Local -PathType Leaf)) { Fail 1 "local file '$Local' does not exist" }
        Invoke-Adb $id @('push', $Local, $Remote) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; local = $Local; remote = $Remote } }
        Write-Host "PUSHED $Local -> $Remote on $id" -ForegroundColor Green
        exit 0
    }

    'shell' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Cmd) { Fail 1 "shell needs -Cmd `"<adb shell command>`"" }
        $out = & $adb -s $id shell $Cmd 2>&1
        $code = $LASTEXITCODE
        if ($Json) { Emit-Ok @{ id = $id; cmd = $Cmd; exit = $code; out = ($out -join "`n") } }
        foreach ($l in $out) { Write-Host $l }
        if ($code -ne 0) { Fail 7 "shell command exit $code" }
        exit 0
    }

    default {
        Fail 1 "unknown verb '$Verb' (run with no verb, or 'help', for the list)"
    }
}
