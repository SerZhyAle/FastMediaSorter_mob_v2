# Build NoLegal Release APK
# S0156: noLegal = standard + VR + sideload-only capabilities (single universal APK).
# Installs on any Android device (4 ABIs); OpenXR bridge compiled for arm64-v8a only.
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

Write-Host "Building NoLegal Release APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Standard runtime + sideload-only extras (NewPipe, etc.)" -ForegroundColor Yellow
Write-Host "Distribution: ADB sideload only - not for any public store." -ForegroundColor Magenta

# S1873: one formula for the whole repository, and it travels as a build property - a build
# never writes build.gradle.kts, so the working tree stays clean and nothing has to revert it.
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
$stamp = Get-BuildVersionStamp
$versionName = $stamp.VersionName
$versionCodeInt = $stamp.AppVersionCode

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-nolegal-release.ps1" -Domain Build.Phone

# CRITICAL: pin CWD to $projectRoot so Gradle resolves the correct project
# directory regardless of how the script was invoked (e.g. when a.ps1
# delegates from the dev worktree into the release worktree).
Push-Location $projectRoot
try {

# Clean stale Gradle CMake .tmp files that cause "Access is denied" lock errors.
$cxxReleaseDir = Join-Path $projectRoot "app_v2\build\intermediates\cxx\Release"
if (Test-Path $cxxReleaseDir) {
    $tmpFiles = @(Get-ChildItem -Path $cxxReleaseDir -Recurse -Filter "*.tmp" -ErrorAction SilentlyContinue)
    foreach ($f in $tmpFiles) {
        Remove-Item $f.FullName -Force -ErrorAction SilentlyContinue
        if (-not (Test-Path $f.FullName)) {
            Write-Host "  Cleaned stale lock: $($f.Name)" -ForegroundColor DarkGray
        }
    }
}

# Start the Gradle build process (Release with R8 optimizations).
# --no-configuration-cache: Chaquopy 17.x is not configuration-cache-compatible (S0175).
# -Pchaquopy.enabled=true: noLegal flavor REQUIRES the Chaquopy Python runtime.
#   Passing the flag explicitly removes the dependency on a machine-local
#   `chaquopy.enabled=true` line in `local.properties` (gitignored, may be absent).
& $gradlew :app_v2:assembleNoLegalRelease "-Pfms.versionCode=$versionCodeInt" "-Pfms.versionName=$versionName" "-Pchaquopy.enabled=true" --no-configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\noLegal\release"
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
Write-Host "Package name: com.sza.fastmediasorter" -ForegroundColor Cyan

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_nolegal_release.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | nolegal-release | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName -NoCommander

}
finally {
    Pop-Location
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
