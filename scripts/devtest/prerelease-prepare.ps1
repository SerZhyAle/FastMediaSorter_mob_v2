<#
.SYNOPSIS
  S0484 pre-release sweep - environment preparation stage.

.DESCRIPTION
  Brings a chosen emulator to a clean known state for the /spec-prerelease sweep:
    1. Device gate (delegates to the device-readiness pre-flight; aborts on its exit codes 1..3).
    2. Clean uninstall + install of the standard-debug build (added in step 01.2).
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

# ---------- stage 2: clean install (step 01.2) ----------
# Pin adb-driven steps to the chosen device via ANDROID_SERIAL (belt-and-braces alongside
# adb.ps1's own -DeviceId), so a stray second device cannot capture the install.
if ($DeviceId) { $env:ANDROID_SERIAL = $DeviceId }

# Uninstall the prior build if present; a missing package (adb.ps1 exit 4) is not an error.
$unArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'uninstall')
if ($DeviceId) { $unArgs += @('-DeviceId', $DeviceId) }
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
$instArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'install', '-Flavor', 'standard')
if ($DeviceId) { $instArgs += @('-DeviceId', $DeviceId) }
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
$wcXml = '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="welcome_completed" value="true" /></map>'
$wcB64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($wcXml))
$wcCmd = "run-as $DebugPackage sh -c 'mkdir -p shared_prefs; echo $wcB64 | base64 -d > shared_prefs/welcome_prefs.xml'"
$wcArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'shell', '-Cmd', $wcCmd)
if ($DeviceId) { $wcArgs += @('-DeviceId', $DeviceId) }
& pwsh @wcArgs *> $null
Add-Stage 'onboarding-bypass' 'OK' 'welcome_completed=true (skip first-run onboarding)'

# ---------- stage 3: seed media (step 01.3) ----------
# Probe for the seeded media root; only seed when absent so re-runs are idempotent.
$mediaRoot  = '/sdcard/Download/FastMediaSorter_Test'
$probeArgs  = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'shell', '-Cmd', "ls -d $mediaRoot")
if ($DeviceId) { $probeArgs += @('-DeviceId', $DeviceId) }
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

# ---------- stage 4: launch verify (step 01.4) ----------
# Launch via "adb.ps1 launch" - on debug builds it starts the explicit MainActivity and
# bypasses the LeakCanary launcher trap. Then scan the launch window for a crash/FATAL/ANR.
$launchArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'launch')
if ($DeviceId) { $launchArgs += @('-DeviceId', $DeviceId) }
& pwsh @launchArgs *> $null
$launchCode = $LASTEXITCODE
if ($launchCode -ne 0) {
    Add-Stage 'launch' 'FAIL' "adb launch exit $launchCode"
    Complete-Run 10
}

# Allow the cold start to settle before reading the launch-window log.
Start-Sleep -Seconds 3

$logArgs = @('-NoProfile', '-File', "$RepoRoot/scripts/devtest/adb.ps1", 'log', '-Tail', '400', '-Grep', 'FATAL|beginning of crash|ANR in')
if ($DeviceId) { $logArgs += @('-DeviceId', $DeviceId) }
$logOut = & pwsh @logArgs 2>$null
if ("$logOut" -match 'FATAL|beginning of crash|ANR in') {
    Add-Stage 'launch-verify' 'FAIL' 'crash/FATAL/ANR detected in launch window'
    Complete-Run 10
}
Add-Stage 'launch-verify' 'OK' "launched $DebugPackage (MainActivity), no crash in launch window"

Complete-Run 0
