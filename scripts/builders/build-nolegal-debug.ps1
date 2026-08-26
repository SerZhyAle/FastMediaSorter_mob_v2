# Build NoLegal Debug APK
# S0156: noLegal = standard + VR + sideload-only capabilities (single universal APK).
# Installs on any Android device (4 ABIs); OpenXR bridge compiled for arm64-v8a only.
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

param(
    # noLegal debug is a sideload DISTRIBUTION artifact (installed via `ivn`), so it must
    # carry the real build timestamp by default. Frozen versions belong to compile-only fast
    # checks (fk/fc/fr/fu) that produce no APK. Pass -AutoVersion:$false to opt into the
    # frozen default when config-cache reuse matters more than a fresh version.
    [switch]$AutoVersion = $true,

    # S1972: which slice of the split debug build to report and copy. Empty means "ask the connected
    # device", which is the right answer whenever exactly one is attached; name it explicitly when
    # several are, or when building for a device that is not plugged in.
    [string]$Abi = ''
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-nolegal-debug.ps1"
try {

Write-Host "Building NoLegal Debug APK.." -ForegroundColor Cyan
Write-Host "Features: Standard runtime + sideload-only extras (NewPipe, etc.)" -ForegroundColor Yellow
Write-Host "Distribution: ADB sideload only - not for any public store." -ForegroundColor Magenta
if ($AutoVersion) {
    Write-Host "Mode: auto-versioned sideload artifact build" -ForegroundColor Yellow
}
else {
    Write-Host "Mode: frozen-version sideload build (-AutoVersion:`$false, config-cache reuse)" -ForegroundColor Yellow
}

# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"

# Clean stale Gradle CMake .tmp files that cause "Access is denied" lock errors.
# These are left behind when a previous build is aborted; new builds cannot hash them.
$cxxDebugDir = Join-Path $projectRoot "app_v2\build\intermediates\cxx\Debug"
if (Test-Path $cxxDebugDir) {
    $tmpFiles = @(Get-ChildItem -Path $cxxDebugDir -Recurse -Filter "*.tmp" -ErrorAction SilentlyContinue)
    foreach ($f in $tmpFiles) {
        Remove-Item $f.FullName -Force -ErrorAction SilentlyContinue
        if (-not (Test-Path $f.FullName)) {
            Write-Host "  Cleaned stale lock: $($f.Name)" -ForegroundColor DarkGray
        }
    }
}

# Start the Gradle build process.
# --no-configuration-cache: Chaquopy 17.x is not configuration-cache-compatible (S0175).
# -Pchaquopy.enabled=true: noLegal flavor REQUIRES the Chaquopy Python runtime.
#   Passing the flag explicitly removes the dependency on a machine-local
#   `chaquopy.enabled=true` line in `local.properties` (which is gitignored and
#   may be commented out / absent on a fresh checkout).
# S1972: no -Pfms.abiSplits here, and it cannot be added. The two mechanisms are mutually exclusive
# for this flavor: AGP refuses ndk.abiFilters alongside splits.abi, while Chaquopy refuses their
# ABSENCE - "Variant 'noLegalDebug': Chaquopy requires ndk.abiFilters", measured 2026-08-26. So
# noLegal keeps its single arm64-v8a APK, which is what the owner ruled for on 2026-08-23 anyway;
# the emulator variant this ticket hoped to hand back is not expressible while Chaquopy is in the
# build. The other flavors, which build with -Pchaquopy.enabled=false, are split normally.
$gradleArgs = @(
    "assembleNoLegalDebug",
    "-Pchaquopy.enabled=true",
    "--no-configuration-cache"
)
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
if ($AutoVersion) {
    $stamp = Get-BuildVersionStamp
    $versionCodeInt = $stamp.AppVersionCode
    $versionName = $stamp.VersionName
    Write-Host "Version override: $versionName (code: $versionCodeInt)" -ForegroundColor Green
    $gradleArgs += @(
        "-Pfms.versionCode=$versionCodeInt",
        "-Pfms.versionName=$versionName"
    )
}
& $gradlew @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\noLegal\debug"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
. "$PSScriptRoot\..\utils\get-device-abi.ps1"
# arm64-v8a is the fallback, not a guess: adb refuses the property read whenever more than one
# device is online, which is this repo's normal state (phone + watch + emulator, S1986), and the
# phone is the thing a debug APK is built for. Pass -Abi x86_64 to take the emulator slice.
$wantedAbi = if ($Abi) { $Abi } else { Get-TargetDeviceAbi -Fallback 'arm64-v8a' }
try {
    $resolvedArtifact = Find-BuildArtifact -Dir $apkDir -Abi $wantedAbi
}
catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan
Write-Host "Package name: com.sza.fastmediasorter.debug" -ForegroundColor Cyan

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_nolegal_debug.apk"
# Judge what was produced, not what was intended. A packaging path that lost its version
# property still writes a fresh file - only the artifact's own metadata shows that the
# version inside it belongs to an older build (S1873). Checked before the copy, because the
# copy is what puts it in DOWNLOADS and on Google Drive.
if ($AutoVersion) {
    & (Join-Path $PSScriptRoot "..\quality\assert-artifact-version-fresh.ps1") -Path $apkPath
    if ($LASTEXITCODE -eq 1) {
        Write-Host "Refusing to distribute an artifact whose version is not its own." -ForegroundColor Red
        exit 1
    }
}

Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | nolegal-debug | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Zip with password and copy to Google Drive
$gdDir = "c:\GD\WORK\FastMediaSorter"
if (!(Test-Path -Path $gdDir)) {
    New-Item -ItemType Directory -Path $gdDir | Out-Null
}

# Copy raw APK to Google Drive (in addition to password-protected ZIP below).
# Recipients with security policies that block APK downloads use the .zip copy;
# the raw .apk lets fast paths skip the unzip step.
Copy-Item -Path "$downloadsDir\$destName" -Destination "$gdDir\$destName" -Force
Write-Host "APK copied to $gdDir\$destName" -ForegroundColor Green

$zipName = [System.IO.Path]::ChangeExtension($destName, ".zip")
$zipPath = "$gdDir\$zipName"

# Use 7-Zip to create password-protected archive
$7zipPath = "C:\Program Files\7-Zip\7z.exe"
if (Test-Path -Path $7zipPath) {
    & $7zipPath a -tzip -p1 "$zipPath" "$downloadsDir\$destName" | Out-Null
    Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
}
else {
    Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
    Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
}

# Copy APK to tc folder
$tcDir = "c:\GD\tc\SZA\_APP"
if (!(Test-Path -Path $tcDir)) {
    New-Item -ItemType Directory -Path $tcDir | Out-Null
}
Copy-Item -Path "$downloadsDir\$destName" -Destination "$tcDir\$destName" -Force
Write-Host "APK copied to $tcDir\$destName" -ForegroundColor Green

}
finally {
    Exit-AgentLock -Name Build
}
