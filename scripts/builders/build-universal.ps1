# Universal Build Script for FastMediaSorter Flavors
# Usage: .\build-universal.ps1 [standard|lite] [debug|release] [device]

param(
    [Parameter(Mandatory = $true, HelpMessage = "Flavor: standard, lite, photos, or legacy")]
    [ValidateSet("standard", "lite", "photos", "legacy")]
    [string]$Flavor,

    [Parameter(Mandatory = $true, HelpMessage = "Build type: debug or release")]
    [ValidateSet("debug", "release")]
    [string]$BuildType,

    [Parameter(Mandatory = $false, HelpMessage = "Install on device after build")]
    [switch]$Device
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-universal.ps1" -Domain Build.Phone
try {

Write-Host "=== FastMediaSorter Universal Build Script ===" -ForegroundColor Cyan
Write-Host "Flavor: $Flavor" -ForegroundColor Yellow
Write-Host "Build Type: $BuildType" -ForegroundColor Yellow

# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"

if ($Flavor -eq "standard") {
    $features = "Full (cloud, EPUB, translation, OCR)"
    $apkPathRelative = "app_v2\build\outputs\apk\standard\$BuildType\FastMediaSorter"
    if ($BuildType -eq "debug") { $apkPathRelative += "_debug.apk"; $packageName = "com.sza.fastmediasorter.debug" }
    else { $apkPathRelative += ".apk"; $packageName = "com.sza.fastmediasorter" }
}
elseif ($Flavor -eq "lite") {
    $features = "Local files only (no cloud, no EPUB)"
    $apkPathRelative = "app_v2\build\outputs\apk\lite\$BuildType\FastMediaSorter"
    if ($BuildType -eq "debug") { $apkPathRelative += "_debug.apk"; $packageName = "com.sza.fastmediasorter.lite.debug" }
    else { $apkPathRelative += ".apk"; $packageName = "com.sza.fastmediasorter.lite" }
}
elseif ($Flavor -eq "photos") {
    $features = "Images only (with cloud support)"
    $apkPathRelative = "app_v2\build\outputs\apk\photos\$BuildType\FastMediaSorter"
    if ($BuildType -eq "debug") { $apkPathRelative += "_debug.apk"; $packageName = "com.sza.fastmediasorter.photos.debug" }
    else { $apkPathRelative += ".apk"; $packageName = "com.sza.fastmediasorter.photos" }
}
elseif ($Flavor -eq "legacy") {
    $features = "Full (Android 6.0+ compatibility)"
    $apkPathRelative = "app_v2\build\outputs\apk\legacy\$BuildType\FastMediaSorter"
    if ($BuildType -eq "debug") { $apkPathRelative += "_debug.apk"; $packageName = "com.sza.fastmediasorter.legacy.debug" }
    else { $apkPathRelative += ".apk"; $packageName = "com.sza.fastmediasorter.legacy" }
}

$apkDir = Split-Path -Parent "$projectRoot\$apkPathRelative"

Write-Host "Features: $features" -ForegroundColor Yellow

# Build the APK
$taskName = ":app_v2:assemble" + $Flavor.Substring(0, 1).ToUpper() + $Flavor.Substring(1) + $BuildType.Substring(0, 1).ToUpper() + $BuildType.Substring(1)
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
$stamp = Get-BuildVersionStamp
Write-Host "`nBuilding $Flavor $BuildType APK..." -ForegroundColor Cyan
Write-Host "Version override: $($stamp.VersionName) (code: $($stamp.AppVersionCode))" -ForegroundColor Green
& $gradlew $taskName "-Pchaquopy.enabled=false" "-Pfms.versionCode=$($stamp.AppVersionCode)" "-Pfms.versionName=$($stamp.VersionName)" --configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Ask the build what it produced instead of assembling a filename from a naming convention: the
# convention names a file that stops existing the moment a variant emits more than one output, and
# this script would then print a path to nothing and copy nothing (S1972).
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$built = Find-BuildArtifact -Dir $apkDir
if (-not $built) {
    Write-Host "`nBuild reported success but no APK is present in $apkDir." -ForegroundColor Red
    exit 1
}
$apkPath = $built.FullName

Write-Host "APK location: $apkPath" -ForegroundColor Cyan
Write-Host "Package name: $packageName" -ForegroundColor Cyan

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_${Flavor}_${BuildType}.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | $Flavor-$BuildType | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

# Install on device if requested
if ($Device) {
    $adb = Get-ToolPath -Tool Adb

    Write-Host "`nInstalling on device..." -ForegroundColor Yellow
    & $adb wait-for-device
    & $adb install -r -d $apkPath

    if ($BuildType -eq "debug") {
        Write-Host "Launching app..." -ForegroundColor Cyan
        & $adb shell am start -n "$packageName/com.sza.fastmediasorter.ui.main.MainActivity"
    }

    Write-Host "`nApp installed and launched!" -ForegroundColor Green
}

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
