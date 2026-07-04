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
    clear                pm clear (reset app data)
    install              install -r -d an APK (-Apk <path>, or newest debug APK for -Flavor)
    uninstall            uninstall the resolved package
    shot                 screenshot to temp/scratch/<device>_<TS>.png (screencap on device, then pull)
    log                  logcat -d tail for the app: -Tail N (default 200), -Grep <regex>;
                         full capture also written to temp/scratch/
    tap                  input tap -X <x> -Y <y>
    text                 input text -Text "<string>" (spaces handled)
    key                  input keyevent -Key <name-or-code> (e.g. BACK, 4, KEYCODE_HOME)
    prefs                pull app_settings.xml via run-as to temp/scratch/ (debuggable build only)
    shell                arbitrary passthrough: -Cmd "<adb shell command>"

  Package resolution (verbs that act on the app): default debug id
  com.sza.fastmediasorter.debug; -Release switches to com.sza.fastmediasorter; -Package
  overrides explicitly. If the chosen id is not installed, the other variant is tried before
  giving up.

  Exit codes (stable; mirror device-ready.ps1 where they overlap):
    0 - OK
    1 - adb not found, or bad arguments
    2 - no online device
    3 - multiple online devices and -DeviceId not supplied (for verbs needing a device)
    4 - target package not installed (for app verbs)
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
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

# Canonical app coordinates (see install/builder scripts and /spec-test-device).
$BASE_PACKAGE   = 'com.sza.fastmediasorter'
$DEBUG_PACKAGE  = "$BASE_PACKAGE.debug"
$MAIN_ACTIVITY  = 'com.sza.fastmediasorter.ui.main.MainActivity'

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

function Find-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) {
            $candidate = Join-Path $root 'platform-tools\adb.exe'
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -Path $known -PathType Leaf) { return $known }

    return $null
}

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
        if ($Json) { Emit-Ok @{ verbs = 'help,devices,props,current,launch,stop,clear,install,uninstall,shot,log,tap,text,key,prefs,shell' } }
        Write-Host "adb.ps1 - ad-hoc device swiss-army" -ForegroundColor Cyan
        Write-Host "Usage: pwsh -NoProfile -File scripts/devtest/adb.ps1 <verb> [options]" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  devices    list online devices (model + Android version)" -ForegroundColor White
        Write-Host "  props      selected device props (model, release, sdk, density, size)" -ForegroundColor White
        Write-Host "  current    focused activity / package" -ForegroundColor White
        Write-Host "  launch     start app (debug: explicit MainActivity)" -ForegroundColor White
        Write-Host "  stop       force-stop app" -ForegroundColor White
        Write-Host "  clear      pm clear (reset app data)" -ForegroundColor White
        Write-Host "  install    install -r -d (-Apk <path> | -Flavor <std|lite|photos|legacy|noLegal>)" -ForegroundColor White
        Write-Host "  uninstall  uninstall resolved package" -ForegroundColor White
        Write-Host "  shot       screenshot to temp/scratch/" -ForegroundColor White
        Write-Host "  log        logcat -d app tail (-Tail N, -Grep regex)" -ForegroundColor White
        Write-Host "  tap        input tap -X <x> -Y <y>" -ForegroundColor White
        Write-Host "  text       input text -Text <string>" -ForegroundColor White
        Write-Host "  key        input keyevent -Key <name-or-code>" -ForegroundColor White
        Write-Host "  prefs      pull app_settings.xml to temp/scratch/ (run-as)" -ForegroundColor White
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

    'clear' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        $out = Invoke-Adb $id @('shell', 'pm', 'clear', $pkg) -AllowFail
        if (($out -join '') -notmatch 'Success') { Fail 7 "pm clear did not report Success: $($out -join ' ')" }
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "CLEARED data for $pkg on $id" -ForegroundColor Green
        exit 0
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
        Invoke-Adb $id @('shell', 'screencap', '-p', $remote) | Out-Null
        Invoke-Adb $id @('pull', $remote, $local) | Out-Null
        Invoke-Adb $id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
        if (-not (Test-Path -Path $local -PathType Leaf)) { Fail 7 "screenshot pull produced no file" }
        if ($Json) { Emit-Ok @{ id = $id; file = $local } }
        Write-Host "SHOT $local" -ForegroundColor Green
        exit 0
    }

    'log' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        $raw = Invoke-Adb $id @('logcat', '-d', '-t', "$Tail") -AllowFail
        # Keep app lines + the project's named tags; logcat does not stamp every line with the pid.
        $patterns = @([regex]::Escape($pkg), [regex]::Escape($BASE_PACKAGE), 'FastMediaSorter')
        $lines = $raw -split "`r?`n" | Where-Object {
            $line = $_
            ($patterns | Where-Object { $line -match $_ }).Count -gt 0
        }
        if ($Grep) { $lines = $lines | Where-Object { $_ -match $Grep } }
        $logFile = Join-Path (Get-TempDir) "adb_log_$(Get-Stamp).log"
        ($raw -join "`n") | Out-File -FilePath $logFile -Encoding UTF8
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg; matched = @($lines).Count; file = $logFile } }
        foreach ($l in $lines) { Write-Host $l }
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
