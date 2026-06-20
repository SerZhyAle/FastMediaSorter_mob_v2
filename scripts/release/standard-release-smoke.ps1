<#
.SYNOPSIS
  S0553 - standardRelease smoke: detect R8/shrink/signing/auth breakage on the
  minified release artifact (the only build that exposes R8/shrink failures).

.DESCRIPTION
  Two modes (S0553 §5.3 release-only technical regressions, §6.2 release-vs-debug drift):

   - default (device): build/locate the minified standardRelease APK, install on a
     connected device, cold-launch com.sza.fastmediasorter, capture the launch
     logcat window and scan it (via scripts/utils/search-log.ps1) for R8/shrink
     fatal markers. Missing device / keystore / APK => infrastructure abort (exit 2),
     never a false content FAIL.

   - -CheckSeams (static, no device): assert release identity seams in
     app_v2/build.gradle.kts - production OAuth/Dropbox manifest placeholders resolve
     for the release build type, and no debug applicationIdSuffix / debug app key
     leaks into release.

  Exit codes:
    0 - clean (launch had no R8 markers / all seams present)
    1 - content FAIL (R8 marker found / required seam missing)
    2 - infrastructure abort (no device / no keystore / no APK / inputs unreadable)

.PARAMETER ApkPath
  Pre-built standardRelease APK. When omitted, the APK is resolved from the AGP
  output dir, or built via assembleStandardRelease when -Build is set.

.PARAMETER Build
  Build the minified standardRelease before installing (requires release keystore).

.PARAMETER CheckSeams
  Run the static release-identity seam assertions only (no device, no build).

.PARAMETER Json
  Emit a single JSON verdict object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/release/standard-release-smoke.ps1 -CheckSeams

.EXAMPLE
  pwsh -NoProfile -File scripts/release/standard-release-smoke.ps1 -Build -Json
#>
[CmdletBinding()]
param(
    [string]$ApkPath,
    [switch]$Build,
    [switch]$CheckSeams,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
trap { Write-Host "ERROR: $_" -ForegroundColor Red; exit 2 }

$repoRoot   = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$buildGradle = Join-Path $repoRoot 'app_v2/build.gradle.kts'
$pkg = 'com.sza.fastmediasorter'

function Write-Verdict {
    param([string]$Mode, [bool]$Pass, $Detail, [int]$ExitCode)
    if ($Json) {
        ([ordered]@{ mode = $Mode; pass = $Pass; detail = $Detail } | ConvertTo-Json -Depth 6 -Compress)
    } else {
        Write-Host ("{0}: {1}" -f $Mode, $(if ($Pass) { 'PASS' } else { 'FAIL' })) -ForegroundColor $(if ($Pass) { 'Green' } else { 'Red' })
        $Detail | ForEach-Object { Write-Host "  $_" }
    }
    exit $ExitCode
}

# ============================ static seam mode ============================
if ($CheckSeams) {
    if (-not (Test-Path $buildGradle)) { Write-Verdict 'seams' $false @('build.gradle.kts not found') 2 }
    $raw = Get-Content -Raw -Path $buildGradle

    # Isolate the `release { ... }` build-type block (up to the staging build type).
    $relStart = $raw.IndexOf("`n        release {")
    $relBlock = ''
    if ($relStart -ge 0) {
        $tail = $raw.Substring($relStart)
        $stg = $tail.IndexOf('create("staging")')
        $relBlock = if ($stg -ge 0) { $tail.Substring(0, $stg) } else { $tail }
    }

    $debugAppKey = 'u43ocp6pqvwaiu1'   # debug-only Dropbox key - must NOT appear in release
    $results = @()
    $ok = $true

    # 1. Production Dropbox app key declared in defaultConfig (release inherits it).
    if ($raw -match 'manifestPlaceholders\["dropboxAppKey"\]\s*=\s*"dpy64e70kqobr6x"') {
        $results += 'OK   production dropboxAppKey present in defaultConfig'
    } else { $results += 'MISSING production dropboxAppKey in defaultConfig'; $ok = $false }

    # 2. Release build type must not leak a debug applicationIdSuffix.
    if ($relBlock -and ($relBlock -match 'applicationIdSuffix')) {
        $results += 'MISSING release leaks applicationIdSuffix (package would not match production OAuth)'; $ok = $false
    } else { $results += 'OK   release has no applicationIdSuffix (package = production)' }

    # 3. Release build type must not override the production key with the debug key.
    if ($relBlock -and ($relBlock -match [regex]::Escape($debugAppKey))) {
        $results += 'MISSING release overrides dropboxAppKey with the debug key'; $ok = $false
    } else { $results += 'OK   release does not use the debug Dropbox key' }

    # 4. MSAL (OneDrive OAuth) dependency present.
    if ($raw -match 'com\.microsoft\.identity\.client:msal') {
        $results += 'OK   MSAL (OneDrive OAuth) dependency present'
    } else { $results += 'MISSING MSAL dependency'; $ok = $false }

    # Note: release signing fingerprint vs production OAuth registration is a manual
    # operator check (recorded in the evidence pack), not statically assertable here.
    Write-Verdict 'seams' $ok $results $(if ($ok) { 0 } else { 1 })
}

# ============================ device smoke mode ============================
# Resolve the minified release APK.
$apkDir = Join-Path $repoRoot 'app_v2/build/outputs/apk/standard/release'
$resolvedApk = $null
if ($ApkPath) {
    if (-not (Test-Path $ApkPath)) { Write-Verdict 'smoke' $false @("ApkPath not found: $ApkPath") 2 }
    $resolvedApk = $ApkPath
} else {
    if ($Build) {
        $keystore = (Test-Path (Join-Path $repoRoot '.secrets/keystore.properties')) -or (Test-Path (Join-Path $repoRoot 'keystore.properties'))
        if (-not $keystore) { Write-Verdict 'smoke' $false @('release keystore absent (.secrets/keystore.properties) - cannot build standardRelease') 2 }
        Push-Location $repoRoot
        try { & (Join-Path $repoRoot 'gradlew.bat') assembleStandardRelease '-Pchaquopy.enabled=false' | Out-Null }
        finally { Pop-Location }
    }
    $meta = Join-Path $apkDir 'output-metadata.json'
    if (Test-Path $meta) {
        $m = Get-Content -Raw $meta | ConvertFrom-Json
        if ($m.elements -and $m.elements[0].outputFile) { $resolvedApk = Join-Path $apkDir $m.elements[0].outputFile }
    }
    if (-not $resolvedApk -or -not (Test-Path $resolvedApk)) {
        $latest = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latest) { $resolvedApk = $latest.FullName }
    }
}
if (-not $resolvedApk -or -not (Test-Path $resolvedApk)) {
    Write-Verdict 'smoke' $false @('no standardRelease APK found - pass -ApkPath or run with -Build (needs release keystore)') 2
}

# Device probe (reuse device-ready.ps1 when available; tolerate its absence).
$deviceReady = Join-Path $repoRoot 'scripts/devtest/device-ready.ps1'
$deviceOnline = $false
if (Test-Path $deviceReady) {
    & pwsh -NoProfile -File $deviceReady -Package $pkg -Json *> $null
    $deviceOnline = ($LASTEXITCODE -eq 0)
} else {
    $adbDevices = (& adb devices 2>$null) | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
    $deviceOnline = [bool]$adbDevices
}
if (-not $deviceOnline) { Write-Verdict 'smoke' $false @('no device online - R8 smoke requires a connected device') 2 }

# Install + cold launch + capture launch logcat window.
& adb install -r $resolvedApk *> $null
if ($LASTEXITCODE -ne 0) { Write-Verdict 'smoke' $false @("adb install failed for $resolvedApk") 2 }
& adb shell am force-stop $pkg *> $null
& adb logcat -c *> $null
& adb shell am start -n "$pkg/.ui.main.MainActivity" *> $null
Start-Sleep -Seconds 8
$logFile = Join-Path $repoRoot 'temp/standard-release-smoke.logcat'
& adb logcat -d *> $logFile

# Scan for R8/shrink fatal markers - reuse search-log.ps1 for the app-error count,
# plus a raw scan for the hard markers (regardless of log format).
$r8Markers = 'ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|VerifyError|Resources\$NotFoundException|FATAL EXCEPTION'
$hits = @(Select-String -Path $logFile -Pattern $r8Markers -ErrorAction SilentlyContinue)
$searchLog = Join-Path $repoRoot 'scripts/utils/search-log.ps1'
$appErrors = 0
if (Test-Path $searchLog) {
    $out = & pwsh -NoProfile -File $searchLog -LogFile $logFile -Count -Errors -AppOnly 2>&1
    $mm = [regex]::Match("$out", 'Match count:\s*(\d+)')
    if ($mm.Success) { $appErrors = [int]$mm.Groups[1].Value }
}

$pass = ($hits.Count -eq 0)
$detail = @("r8Markers=$($hits.Count)", "appErrors=$appErrors", "apk=$([System.IO.Path]::GetFileName($resolvedApk))")
if ($hits.Count -gt 0) { $detail += ($hits | Select-Object -First 5 | ForEach-Object { $_.Line.Trim() }) }
Write-Verdict 'smoke' $pass $detail $(if ($pass) { 0 } else { 1 })
