#!/usr/bin/env pwsh
<#
.SYNOPSIS
    S2380 - stage a device for the automated UI sweep: synthetic media in its own root, MediaStore
    told about it, the media-read permissions granted before the app is first launched, and the
    first-run wizard cleared out of the walk's way.

.DESCRIPTION
    Runs once per sweep, before the first matrix combination. The corpus does not depend on
    orientation, theme or language, so it is never rebuilt between combinations.

    Nothing is generated here. scripts/release/store_shot_media.py already writes a reproducible
    corpus - seeded RNG, four media kinds - and a second generator would add a second set of
    criteria to the same result. This script is the wrapper around it, on the model of
    scripts/release/seed-store-shot-media.ps1.

    The device root is a THIRD one, distinct from both existing consumers of the same pattern:

      /sdcard/Download/FastMediaSorter_Store  - the store listing capture (seed-store-shot-media.ps1)
      /sdcard/Download/FastMediaSorter_Test   - the manual pre-release corpus (setup_test_media.ps1),
                                                which is real personal material (S1991)
      /sdcard/Download/FastMediaSorter_UiSweep - this one

    Three roots means a sweep can never quietly photograph the personal corpus, and clearing one
    consumer's staging does not disturb another's.

    Permissions are granted BEFORE first launch, following scripts/utils/setup-avd-for-tests.ps1: an
    ungranted read permission returns zero rows with no error, so the walker would photograph empty
    lists and the review would report the emptiness as a defect. The three granted names are the ones
    app_v2/src/main/AndroidManifest.xml declares. MANAGE_EXTERNAL_STORAGE is deliberately NOT used:
    it is declared only in the noLegal manifest, and the sweep runs on standard.

    Every device call goes through scripts/devtest/adb.ps1 rather than a raw adb, because a raw adb
    invoked from a POSIX-style shell rewrites /sdcard paths into Windows ones and fails with remote
    secure_mkdirs() (S2602).

.PARAMETER DeviceId
    Target device serial. Omit when exactly one device is attached.

.PARAMETER Root
    Device directory to write. Defaults to /sdcard/Download/FastMediaSorter_UiSweep.

.PARAMETER Package
    Package to grant. Defaults to the standard debug id the sweep installs.

.PARAMETER SkipGenerate
    Push the corpus already under temp/S2380/media/ instead of regenerating it.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/ui-sweep-seed.ps1 -DeviceId emulator-5554

.NOTES
    Exit codes:
      0 - corpus generated (unless skipped), pushed, handed to MediaStore, the three media-read
          permissions granted, and the first-run wizard marked complete.
      1 - a generation, push, scan, grant or wizard step failed: the seed did not happen.
      2 - could not verify: generator missing, no python, an empty local corpus, no device, the
          package not installed, or a device below API 33.
      3 - no usable ffmpeg. Distinct from 1 on purpose: a missing TOOL is fixed by installing it or
          setting FMS_FFMPEG, and reporting it as a failed seed sends the reader looking at the
          device instead. The sweep must learn this before the matrix starts, not in the middle.
#>
param(
    [string]$DeviceId,
    [string]$Root = '/sdcard/Download/FastMediaSorter_UiSweep',
    [string]$Package = 'com.sza.fastmediasorter.debug',
    [switch]$SkipGenerate
)

$repoRoot  = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adbScript = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$generator = Join-Path $repoRoot 'scripts/release/store_shot_media.py'
$localRoot = Join-Path $repoRoot 'temp/S2380/media'

# The three names app_v2/src/main/AndroidManifest.xml declares. Runtime media permissions were
# introduced in API 33; below that `pm grant` refuses a name the platform does not know, which is why
# the device's API level is checked before any grant is attempted.
$MEDIA_PERMISSIONS = @(
    'android.permission.READ_MEDIA_IMAGES',
    'android.permission.READ_MEDIA_VIDEO',
    'android.permission.READ_MEDIA_AUDIO'
)
$MIN_MEDIA_PERMISSION_SDK = 33

$devArgs = @(); if ($DeviceId) { $devArgs = @('-DeviceId', $DeviceId) }

function Invoke-Device {
    param([string[]]$AdbArgs)
    $out = & pwsh -NoProfile -File $adbScript @devArgs @AdbArgs 2>&1
    $script:deviceExit = $LASTEXITCODE
    return ($out | ForEach-Object { $_.ToString() })
}

function Invoke-DeviceShell {
    param([string]$Command)
    return (Invoke-Device @('shell', '-Cmd', $Command))
}

if (-not (Test-Path $adbScript)) {
    Write-Host "SEED FAIL: device wrapper not found at $adbScript"
    exit 2
}
if (-not (Test-Path $generator)) {
    Write-Host "SEED FAIL: generator not found at $generator"
    exit 2
}

$python = Join-Path $repoRoot '.venv/Scripts/python.exe'
if (-not (Test-Path $python)) { $python = 'python' }

# --- [1/7] generate ------------------------------------------------------------------------------

if (-not $SkipGenerate) {
    Write-Host "[1/7] Generating the corpus.."
    & $python $generator --clean --out $localRoot
    $generatorExit = $LASTEXITCODE
    if ($generatorExit -eq 2) {
        Write-Host "SEED FAIL: no usable ffmpeg - the generator cannot write the video and audio kinds."
        Write-Host "  Install ffmpeg or point FMS_FFMPEG at a binary, then re-run before the matrix starts."
        exit 3
    }
    if ($generatorExit -ne 0) {
        Write-Host "SEED FAIL: generator exited $generatorExit"
        exit 1
    }
} else {
    Write-Host "[1/7] Skipping generation (-SkipGenerate)."
}

$folders = @('Photos', 'Videos', 'Music', 'Books')
$localFiles = @(Get-ChildItem -Path $localRoot -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -notmatch '[\\/]\.work[\\/]' })
if ($localFiles.Count -eq 0) {
    Write-Host "SEED FAIL: no files under $localRoot - nothing to push."
    exit 2
}

# --- [2/7] check the bench can carry the grants --------------------------------------------------

Write-Host "[2/7] Checking the device.."
$sdkRaw = (Invoke-DeviceShell 'getprop ro.build.version.sdk') -join ' '
if ($script:deviceExit -ne 0) {
    Write-Host "SEED FAIL: could not reach a device (adb.ps1 exit $($script:deviceExit)): $sdkRaw"
    exit 2
}
if ($sdkRaw -notmatch '(\d+)') {
    Write-Host "SEED FAIL: device did not report an API level ($sdkRaw)"
    exit 2
}
$deviceSdk = [int]$Matches[1]
if ($deviceSdk -lt $MIN_MEDIA_PERMISSION_SDK) {
    Write-Host "SEED FAIL: device is API $deviceSdk - the READ_MEDIA_* permissions do not exist below API $MIN_MEDIA_PERMISSION_SDK."
    Write-Host "  Boot one of the benches declared in scripts/devtest/ui-sweep-profiles.json instead."
    exit 2
}

$packageList = (Invoke-DeviceShell "pm list packages $Package") -join "`n"
if ($packageList -notmatch [regex]::Escape("package:$Package")) {
    Write-Host "SEED FAIL: $Package is not installed - install the standard debug build before seeding."
    exit 2
}
Write-Host "  API $deviceSdk, $Package installed"

# --- [3/7] clear the root ------------------------------------------------------------------------

Write-Host "[3/7] Clearing the device root.."
$clearOut = Invoke-DeviceShell "rm -rf '$Root' && mkdir -p '$Root'"
if ($script:deviceExit -ne 0) {
    Write-Host "SEED FAIL: could not prepare $Root (adb.ps1 exit $($script:deviceExit)): $clearOut"
    exit 1
}

# --- [4/7] push ----------------------------------------------------------------------------------

Write-Host "[4/7] Pushing $($localFiles.Count) file(s).."
foreach ($folder in $folders) {
    $source = Join-Path $localRoot $folder
    if (-not (Test-Path $source)) { continue }
    $out = Invoke-Device @('push', '-Local', $source, '-Remote', $Root)
    if ($script:deviceExit -ne 0) {
        Write-Host ($out -join "`n")
        Write-Host "SEED FAIL: pushing $folder exited $($script:deviceExit)"
        exit 1
    }
    Write-Host "  pushed $folder"
}

# --- [5/7] hand the files to MediaStore ----------------------------------------------------------

# One broadcast per file rather than a volume rescan: the corpus is small, and the per-file form is
# the one this repository has already proven across its AVD fleet.
Write-Host "[5/7] Handing the files to MediaStore.."
foreach ($file in $localFiles) {
    $relative = $file.FullName.Substring($localRoot.Length).Replace('\', '/').TrimStart('/')
    $remote = "$Root/$relative"
    $scanOut = Invoke-DeviceShell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$remote'"
    if ($script:deviceExit -ne 0) {
        Write-Host ($scanOut -join "`n")
        Write-Host "SEED FAIL: MediaStore scan of $remote exited $($script:deviceExit)"
        exit 1
    }
}

# --- [6/7] grant, before the app is first launched ------------------------------------------------

Write-Host "[6/7] Granting the media-read permissions.."
foreach ($permission in $MEDIA_PERMISSIONS) {
    $grantOut = Invoke-DeviceShell "pm grant $Package $permission"
    if ($script:deviceExit -ne 0) {
        Write-Host ($grantOut -join "`n")
        Write-Host "SEED FAIL: granting $permission exited $($script:deviceExit) - the walker would photograph empty lists."
        exit 1
    }
    Write-Host "  granted $permission"
}

# --- [7/7] clear the first-run wizard out of the walk's way ---------------------------------------

# A fresh install opens WelcomeActivity, and the walk's every combination begins by asserting the
# start screen - so on a clean emulator the sweep refused the whole matrix before its first frame,
# with "the app never reached its start screen" (measured emulator-5554, 2026-09-20). The wizard is
# not a screen this sweep photographs: it is a one-time gate in front of every screen that is.
#
# Written as the preference rather than driven as a wizard on purpose. The wizard is a pager of
# unknown length whose pages raise system permission dialogs, so a tap chain through it would be the
# one part of the sweep that navigates blind - exactly what the declared catalog exists to avoid.
# The app must NOT be running for this write: SharedPreferences flushes its in-memory copy on exit
# and would overwrite the file underneath us.
Write-Host "[7/7] Marking the first-run wizard complete.."
$null = Invoke-DeviceShell "am force-stop $Package"
$welcomeXml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="welcome_completed" value="true" />
    <boolean name="first_run_after_welcome" value="false" />
</map>
"@
# One line, because `run-as ... sh -c` is the only shape that redirects INSIDE the app sandbox; a
# redirect written outside it lands in the shell user's filesystem and the app never sees it.
$encodedXml = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($welcomeXml))
$writeOut = Invoke-DeviceShell "run-as $Package sh -c 'mkdir -p shared_prefs && echo $encodedXml | base64 -d > shared_prefs/welcome_prefs.xml'"
if ($script:deviceExit -ne 0) {
    Write-Host ($writeOut -join "`n")
    Write-Host "SEED FAIL: could not write welcome_prefs.xml - is $Package a debuggable build? Without it the walk refuses every combination at the start screen."
    exit 1
}
$readBack = Invoke-DeviceShell "run-as $Package cat shared_prefs/welcome_prefs.xml"
if ($script:deviceExit -ne 0 -or ($readBack -join '') -notmatch 'welcome_completed') {
    Write-Host ($readBack -join "`n")
    Write-Host "SEED FAIL: welcome_prefs.xml did not read back with welcome_completed - the wizard would still open."
    exit 1
}
Write-Host "  welcome_completed=true"

Write-Host "SEED OK: $($localFiles.Count) file(s) under $Root, $($MEDIA_PERMISSIONS.Count) permission(s) granted to $Package, first-run wizard cleared"
exit 0
