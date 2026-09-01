# Build NoLegal Debug APK and Install on Device (phone or Quest via ADB)
# S0156: noLegal = standard + VR + sideload-only capabilities (single universal APK).
# On phones: launches MainActivity (standard + noLegal mode, VR inactive).
# On Quest:  launches MainActivity as 2D panel (full VR bridge available).
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-nolegal-device.ps1" -Domain Build.Phone
try {

# ADB path
$adb = Get-ToolPath -Tool Adb

Write-Host "Building NoLegal Debug APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Full standard + OpenXR VR + sideload-only (NewPipe, etc.)" -ForegroundColor Yellow
Write-Host "Target: any ADB-connected device (phone or Quest)" -ForegroundColor Magenta

# Generate version
$now = Get-Date
$yy  = $now.ToString("yy")
$mon = $now.ToString("MM")
$dd  = $now.ToString("dd")
$HH  = $now.ToString("HH")
$mm  = $now.ToString("mm")

# versionCode: YYMMDDHHm (9 digits, first digit of minutes - avoids Int32 overflow)
$versionCodeInt = [Convert]::ToInt32($now.ToString("yyMMddHH") + $mm[0])
# versionName: Y.YM.MDDH.Hmm  e.g. 2.60.4260.457
$versionName = "$($yy[0]).$($yy[1])$($mon[0]).$($mon[1])$dd$($HH[0]).$($HH[1])$mm"

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"
$logDir = "$projectRoot\temp"

# Clean stale Gradle CMake .tmp files that cause "Access is denied" lock errors.
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

# Update build.gradle.kts
$buildGradlePath = "$projectRoot\app_v2\build.gradle.kts"
$content = Get-Content $buildGradlePath -Raw
$content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$versionCodeInt"
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""
Set-Content $buildGradlePath $content -NoNewline

# Start the Gradle build process.
# --no-configuration-cache: Chaquopy 17.x is not configuration-cache-compatible (S0175).
# -Pchaquopy.enabled=true: noLegal flavor REQUIRES the Chaquopy Python runtime.
#   Passing the flag explicitly removes the dependency on a machine-local
#   `chaquopy.enabled=true` line in `local.properties` (gitignored, may be absent).
& $gradlew :app_v2:assembleNoLegalDebug "-Pchaquopy.enabled=true" --no-configuration-cache

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
$resolvedArtifact = Find-BuildArtifact -Dir $apkDir
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

# Wait for device
Write-Host "`nWaiting for device..." -ForegroundColor Yellow
& $adb wait-for-device

# Clear logcat before launching
Write-Host "Clearing logcat..." -ForegroundColor Cyan
& $adb logcat -c

# Install and launch
Write-Host "Installing and launching NoLegal debug build..." -ForegroundColor Cyan
& $adb install -r -d $apkPath
& $adb shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity

Write-Host "`nNoLegal debug build launched successfully!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_nolegal_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | nolegal-debug-device | $destName"
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
$7zipPath = Get-ToolPath -Tool SevenZip
if (Test-Path -Path $7zipPath) {
    if (Test-Path -Path $zipPath) {
        Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
    }
    & $7zipPath a -y -tzip -p1 "$zipPath" "$downloadsDir\$destName" | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
    }
    else {
        Write-Host "Warning: 7-Zip archive creation failed with exit code $LASTEXITCODE" -ForegroundColor Yellow
    }
}
else {
    Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
    Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
}

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
