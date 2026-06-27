<#
.SYNOPSIS
  S0484 pre-release sweep - environment preparation stage.

.DESCRIPTION
  Brings a chosen emulator to a clean known state for the /spec-prerelease sweep:
    1. Device gate (delegates to the device-readiness pre-flight; aborts on its exit codes 1..3).
    2. Clean uninstall + install of the standard-debug build (added in step 01.2).
    2.6 Grant ACCESS_LOCAL_NETWORK on API >= 37 so the onboarding bypass does not break
        SMB/SFTP/FTP coverage (S0614).
    3. Seed test media when absent (added in step 01.3).
    4. Verify first launch from logs (added in step 01.4).

  Emits a structured result (per-stage status) and a stable exit code so the
  orchestrating skill can branch. Composes existing scripts only - no app code.

  Exit codes:
    0 - ready (all stages passed)
    1 - adb not found / bad arguments (from the readiness pre-flight)
    2 - no online device
    3 - multiple online devices and -DeviceId not supplied
   10 - a preparation stage failed (install / seed / launch)

.PARAMETER DeviceId
  Specific adb device id. Required when multiple devices are online.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-prepare.ps1 -Json
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$RepoRoot     = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$DebugPackage = 'com.sza.fastmediasorter.debug'

$result = [ordered]@{
    ready          = $false
    exitCode       = 0
    selectedDevice = $null
    stages         = @()
}

function Add-Stage {
    param([string]$Name, [string]$Status, [string]$Detail)
    $script:result.stages += [ordered]@{ name = $Name; status = $Status; detail = $Detail }
    if (-not $Json) { Write-Host ("[{0}] {1} - {2}" -f $Status, $Name, $Detail) }
}

function Complete-Run {
    param([int]$Code)
    $script:result.exitCode = $Code
    $script:result.ready    = ($Code -eq 0)
    if ($Json) {
        $script:result | ConvertTo-Json -Depth 6 -Compress
    } else {
        Write-Host ("PREPARE {0} - device={1} exit={2}" -f `
            $(if ($Code -eq 0) { 'READY' } else { 'ABORT' }), $script:result.selectedDevice, $Code)
    }
    exit $Code
}

# Resolve adb (not on PATH on the dev machine), mirroring device-ready.ps1 / prerelease-configure.ps1.
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

# ---------- stage 1: device gate ----------
# The readiness pre-flight without -Package only checks adb + an online device (exit 0/1/2/3);
# the package is not installed yet at this point, so -Package is deliberately omitted.
$drArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/device-ready.ps1", '-Json')
if ($DeviceId) { $drArgs += @('-DeviceId', $DeviceId) }

$drOut  = & pwsh @drArgs
$drCode = $LASTEXITCODE

$dr = $null
if ($drOut) { try { $dr = $drOut | ConvertFrom-Json } catch { $dr = $null } }

if ($drCode -ne 0) {
    $reason = if ($dr -and $dr.reason) { $dr.reason } else { "device-ready exit $drCode" }
    Add-Stage 'device-gate' 'FAIL' $reason
    Complete-Run $drCode
}

$script:result.selectedDevice = if ($dr) { $dr.selectedDevice } else { $DeviceId }
Add-Stage 'device-gate' 'OK' "device=$($script:result.selectedDevice)"

# Resolve a concrete device id for every downstream adb call. device-ready.ps1 already picked the
# single ONLINE device even when -DeviceId was omitted (it ignores offline siblings); depending on
# the raw -DeviceId here - as the original code did - left bare `adb shell` ambiguous whenever
# phantom offline emulator-55xx entries were attached, silently zeroing the API probe and skipping
# the required ACCESS_LOCAL_NETWORK grant (S0625). Pin everything to the resolved id instead.
$TargetDevice = $script:result.selectedDevice
if ([string]::IsNullOrWhiteSpace($TargetDevice)) {
    Add-Stage 'device-resolve' 'FAIL' 'device-ready did not report a concrete device id; refusing to run unscoped adb'
    Complete-Run 10
}

# ---------- stage 2: clean install (step 01.2) ----------
# Pin adb-driven steps to the chosen device via ANDROID_SERIAL (belt-and-braces alongside
# adb.ps1's own -DeviceId), so a stray second device cannot capture the install.
$env:ANDROID_SERIAL = $TargetDevice

# Uninstall the prior build if present; a missing package (adb.ps1 exit 4) is not an error.
$unArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'uninstall', '-DeviceId', $TargetDevice)
& pwsh @unArgs *> $null
Add-Stage 'uninstall' 'OK' "removed $DebugPackage if present"

# Build the standard-debug APK and install it. The interactive dev builder
# (build-standard-device.ps1) is unfit for an unattended sweep: it stamps build.gradle.kts,
# uploads to Google Drive, 7-zips, and - fatally - spawns a never-terminating background
# `adb logcat` whose inherited output handle deadlocks this parent process under `*> $null`
# redirection. Build leanly via gradle, then install through adb.ps1 (no side effects).
$gradlew = Join-Path $RepoRoot 'gradlew.bat'
& $gradlew assembleStandardDebug '-Pchaquopy.enabled=false' --console=plain *> $null
if ($LASTEXITCODE -ne 0) {
    Add-Stage 'install' 'FAIL' "assembleStandardDebug exit $LASTEXITCODE"
    Complete-Run 10
}
$instArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'install', '-Flavor', 'standard', '-DeviceId', $TargetDevice)
& pwsh @instArgs *> $null
$installCode = $LASTEXITCODE
if ($installCode -ne 0) {
    Add-Stage 'install' 'FAIL' "adb.ps1 install exit $installCode"
    Complete-Run 10
}
Add-Stage 'install' 'OK' "standard-debug built + installed ($DebugPackage)"

# ---------- stage 2.5: onboarding bypass ----------
# Skip the first-run WelcomeActivity deterministically so the sweep lands on MainActivity.
# UI taps on the onboarding buttons proved flaky; writing the completion pref is reliable.
# base64 transport avoids the run-as quoting trap for the XML's embedded double-quotes.
#
# Write the COMPLETE welcome_prefs map (all four keys WelcomeViewModel owns), not just
# welcome_completed. A single injected key is fragile: the running app later commits this
# SharedPreferences file from its own in-memory map (e.g. setFirstRunCompleted() flips
# first_run_after_welcome, or maybeSeedDefaultGestureBindings() writes gesture_defaults_seeded),
# and that commit rewrites the file WITHOUT the externally-injected welcome_completed - the app
# then reverts to WelcomeActivity mid-suite and every later flow cascade-fails on go_home. Pre-
# seeding every flag the app would otherwise write leaves it no reason to commit welcome_prefs,
# so the injected bypass survives the whole run (matches the 4-key map a genuine Finish produces).
$wcXml = '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="welcome_completed" value="true" /><boolean name="first_run_after_welcome" value="false" /><boolean name="onboarding_default_player_shown" value="true" /><boolean name="gesture_defaults_seeded" value="true" /></map>'
$wcB64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($wcXml))
$wcCmd = "run-as $DebugPackage sh -c 'mkdir -p shared_prefs; echo $wcB64 | base64 -d > shared_prefs/welcome_prefs.xml'"
$wcArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'shell', '-Cmd', $wcCmd, '-DeviceId', $TargetDevice)
& pwsh @wcArgs *> $null
Add-Stage 'onboarding-bypass' 'OK' 'welcome_prefs seeded with full 4-key map (durable bypass; app never rewrites it)'

# ---------- stage 2.6: local-network permission grant (S0614) ----------
# On API >= 37 ACCESS_LOCAL_NETWORK is a runtime permission that gates every network scanner
# and network Glide loader. The onboarding bypass above skips the in-app request flow, so without
# an explicit grant any SMB/SFTP/FTP listing throws LocalNetworkPermissionDeniedException and the
# network-coverage step fails spuriously on a clean install. Mirror the implicit storage grant:
# detect the device API and grant only when the permission actually exists (declared minSdk 37);
# below that hasLocalNetworkPermission() returns true unconditionally, so nothing to grant.
$adb = Get-Adb
if (-not $adb) {
    Add-Stage 'local-network-grant' 'FAIL' 'adb not found'
    Complete-Run 10
}
# Always scope the API probe to the resolved device. An unscoped `adb shell` is ambiguous when more
# than one entry (incl. offline emulator-55xx) is attached and returns empty, which previously zeroed
# $deviceSdk and dropped into the SKIP branch - leaving a real API 37 device without the grant (S0625).
$adbTarget = @('-s', $TargetDevice)
$sdkRaw = "$(& $adb @adbTarget shell getprop ro.build.version.sdk 2>$null)".Trim()
if ($sdkRaw -notmatch '^\d+$') {
    # Fail loud rather than SKIP: a misdetected API must never silently skip a required runtime grant.
    Add-Stage 'local-network-grant' 'FAIL' "could not read device API on $TargetDevice (getprop returned '$sdkRaw'); refusing to skip the ACCESS_LOCAL_NETWORK grant"
    Complete-Run 10
}
$deviceSdk = [int]$sdkRaw

if ($deviceSdk -ge 37) {
    & $adb @adbTarget shell pm grant $DebugPackage android.permission.ACCESS_LOCAL_NETWORK *> $null
    $grantCode = $LASTEXITCODE
    if ($grantCode -ne 0) {
        Add-Stage 'local-network-grant' 'FAIL' "pm grant ACCESS_LOCAL_NETWORK exit $grantCode (device API $deviceSdk)"
        Complete-Run 10
    }
    Add-Stage 'local-network-grant' 'OK' "granted ACCESS_LOCAL_NETWORK (device API $deviceSdk)"
} else {
    Add-Stage 'local-network-grant' 'SKIP' "runtime permission needs API 37+ (device API $deviceSdk); auto-granted below that"
}

# ---------- stage 3: seed media (step 01.3) ----------
# Probe for the seeded media root; only seed when absent so re-runs are idempotent.
$mediaRoot  = '/sdcard/Download/FastMediaSorter_Test'
$probeArgs  = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'shell', '-Cmd', "ls -d $mediaRoot", '-DeviceId', $TargetDevice)
$probeOut = & pwsh @probeArgs 2>$null
$mediaPresent = ($LASTEXITCODE -eq 0 -and "$probeOut" -match 'FastMediaSorter_Test')

if ($mediaPresent) {
    Add-Stage 'seed-media' 'SKIP' "present - $mediaRoot exists"
} else {
    & pwsh -NoProfile -File "$RepoRoot/scripts/utils/setup_test_media.ps1" *> $null
    $seedCode = $LASTEXITCODE
    if ($seedCode -ne 0) {
        Add-Stage 'seed-media' 'FAIL' "setup test media exit $seedCode"
        Complete-Run 10
    }
    Add-Stage 'seed-media' 'OK' "seeded $mediaRoot"
}

# ---------- stage 3b: ensure MediaStore index (S0747) ----------
# Files seeded on disk are invisible to MediaStore queries until indexed. The seed-media stage
# above SKIPs re-seeding (and its scan) whenever $mediaRoot already exists, so a present-but-
# unindexed tree - left by a prior run whose scan never completed - keeps the canonical fixture
# unqueryable. Every Maestro image flow that looks it up by name then fails spuriously, turning an
# otherwise clean app run RED. Force a scan + verify on BOTH the present and freshly-seeded paths.
function Test-MediaIndexed {
    # Match in PowerShell rather than via a `content query --where` clause: the embedded quoting of
    # a where-expression survives the PowerShell -> adb -> device-shell hops unreliably.
    $q = & $adb @adbTarget shell content query --uri content://media/external/images/media --projection _display_name 2>$null
    return ("$q" -match 'photo_001\.jpg')
}
function Invoke-MediaScan {
    # Re-scan every seeded file (mirrors setup_test_media.ps1 step 11).
    $seededFiles = & $adb @adbTarget shell "find $mediaRoot -type f" 2>$null | Where-Object { $_ -match '^/' }
    foreach ($f in $seededFiles) {
        $p = "$f".Trim()
        if ($p) { & $adb @adbTarget shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$p'" *> $null }
    }
}
function Wait-MediaIndexed {
    # Poll after a scan instead of a single check: a broadcast-driven scan is asynchronous, so an
    # immediate query can read stale - the very race that let Maestro outrun indexing. Polling first
    # avoids a spurious heavy full re-seed when MediaStore is merely slow.
    param([int]$Attempts = 4, [int]$DelaySeconds = 2)
    for ($i = 0; $i -lt $Attempts; $i++) {
        if (Test-MediaIndexed) { return $true }
        Start-Sleep -Seconds $DelaySeconds
    }
    return (Test-MediaIndexed)
}

if (-not (Test-MediaIndexed)) { Invoke-MediaScan }

if (Wait-MediaIndexed) {
    Add-Stage 'media-index' 'OK' 'MediaStore indexed (photo_001.jpg queryable)'
} else {
    # Last resort: full re-seed (wipe + re-push + scan), then re-verify with the same poll.
    & pwsh -NoProfile -File "$RepoRoot/scripts/utils/setup_test_media.ps1" *> $null
    if (Wait-MediaIndexed) {
        Add-Stage 'media-index' 'OK' 're-seeded + MediaStore indexed (photo_001.jpg queryable)'
    } else {
        Add-Stage 'media-index' 'FAIL' 'photo_001.jpg not queryable in MediaStore after scan + full re-seed'
        Complete-Run 10
    }
}

# ---------- stage 4: launch verify (step 01.4) ----------
# Launch via "adb.ps1 launch" - on debug builds it starts the explicit MainActivity and
# bypasses the LeakCanary launcher trap. Then scan the launch window for a crash/FATAL/ANR.
$launchArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'launch', '-DeviceId', $TargetDevice)
& pwsh @launchArgs *> $null
$launchCode = $LASTEXITCODE
if ($launchCode -ne 0) {
    Add-Stage 'launch' 'FAIL' "adb launch exit $launchCode"
    Complete-Run 10
}

# Allow the cold start to settle before reading the launch-window log.
Start-Sleep -Seconds 3

$logArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'log', '-Tail', '400', '-Grep', 'FATAL|beginning of crash|ANR in', '-DeviceId', $TargetDevice)
$logOut = & pwsh @logArgs 2>$null
if ("$logOut" -match 'FATAL|beginning of crash|ANR in') {
    Add-Stage 'launch-verify' 'FAIL' 'crash/FATAL/ANR detected in launch window'
    Complete-Run 10
}
Add-Stage 'launch-verify' 'OK' "launched $DebugPackage (MainActivity), no crash in launch window"

Complete-Run 0
