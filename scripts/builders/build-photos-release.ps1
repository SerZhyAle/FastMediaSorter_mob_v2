# Build Photos Release APK
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-photos-release.ps1" -Domain Build.Phone
try {

Write-Host "Building Photos Release APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Images only (with cloud support)" -ForegroundColor Yellow

# S1873: one formula for the whole repository, and it travels as a build property - a build
# never writes build.gradle.kts, so the working tree stays clean and nothing has to revert it.
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
$stamp = Get-BuildVersionStamp
$versionName = $stamp.VersionName
$versionCodeInt = $stamp.AppVersionCode

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

# Start the Gradle build process (Release with R8 optimizations)
# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"

# Start the Gradle build process (Release with R8 optimizations)
& $gradlew :app_v2:assemblePhotosRelease "-Pfms.versionCode=$versionCodeInt" "-Pfms.versionName=$versionName" "-Pchaquopy.enabled=false" --configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\photos\release"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$resolvedArtifact = Find-BuildArtifact -Dir $apkDir
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan
Write-Host "Package name: com.sza.fastmediasorter.photos" -ForegroundColor Cyan

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_photos_release.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | photos-release | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName
}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
