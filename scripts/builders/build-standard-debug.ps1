param(
    # A packaged debug APK is a distribution artifact - it is copied to DOWNLOADS and installed
    # by hand - so it carries its own build timestamp by default (S1873). Frozen versions belong
    # to the compile-only fast checks (fk/fc/fr/fw) that produce no APK. Pass -AutoVersion:$false
    # to opt back in when configuration-cache reuse matters more than a truthful version.
    [switch]$AutoVersion = $true,

    # S1972: which slice of the split debug build to report and copy. Empty means "ask the connected
    # device", which is the right answer whenever exactly one is attached; name it explicitly when
    # several are, or when building for a device that is not plugged in.
    [string]$Abi = ''
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-standard-debug.ps1" -Domain Build.Phone
try {

Write-Host "Building Standard Debug APK.." -ForegroundColor Cyan
Write-Host "Features: Full (cloud, EPUB, translation, OCR)" -ForegroundColor Yellow
if ($AutoVersion) {
    Write-Host "Mode: auto-versioned artifact build" -ForegroundColor Yellow
}
else {
    Write-Host "Mode: fast reusable debug build (stable Gradle version fields)" -ForegroundColor Yellow
}

# Start the Gradle build process
# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"

# Start the Gradle build process. -Pfms.abiSplits=true is a debug-only flag: it slices the output
# per ABI so the phone and the emulator each get their own APK. No release builder passes it, so a
# release still emits one all-architecture APK per flavor (S1972).
$gradleArgs = @(
    ":app_v2:assembleStandardDebug",
    "-Pchaquopy.enabled=false",
    "-Pfms.abiSplits=true",
    "--configuration-cache"
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
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\debug"
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
$destName = "FastMediaSorter_standard_debug.apk"
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
$logEntry = "$timestamp | standard-debug | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
