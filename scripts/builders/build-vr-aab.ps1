# Build VR Release AAB (for Google Play / Android XR)
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

Write-Host "Building VR Release AAB + APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Full standard + OpenXR VR rendering" -ForegroundColor Yellow
Write-Host "Target: Google Play (Android XR devices)" -ForegroundColor Magenta

# Generate version
$now = Get-Date
$year = $now.Year
$month = $now.Month
$day = $now.Day
$hour = $now.Hour
$minute = $now.Minute

$firstYearDigit = [int]($year.ToString()[0].ToString())
$lastYearDigit = [int]($year.ToString()[-1].ToString())
$firstMonthDigit = [int]($month.ToString("00")[0].ToString())
$secondMonthDigit = [int]($month.ToString("00")[1].ToString())
$dayStr = $day.ToString("00")
$firstHourDigit = [int]($hour.ToString("00")[0].ToString())
$secondHourDigit = [int]($hour.ToString("00")[1].ToString())
$minuteStr = $minute.ToString("00")
$firstMinuteDigit = [int]($minuteStr[0].ToString())

# YYMMDDHHm format (9 digits): year(2) + month(2) + day(2) + hour(2) + minute_first_digit(1)
$versionCodeStr = $now.ToString("yyMMddHH") + $firstMinuteDigit.ToString()
$versionCodeInt = [Convert]::ToInt32($versionCodeStr)
$versionName = "$firstYearDigit.$lastYearDigit$firstMonthDigit.$secondMonthDigit$dayStr$firstHourDigit.$secondHourDigit$minuteStr"

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"

# Update build.gradle.kts
$buildGradlePath = "$projectRoot\app_v2\build.gradle.kts"
$content = Get-Content $buildGradlePath -Raw
$content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$versionCodeInt"
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""
Set-Content $buildGradlePath $content -NoNewline

# Start the Gradle build process for AAB (Release with R8 optimizations)
Write-Host "Running: gradlew bundleVrRelease" -ForegroundColor Yellow
& $gradlew bundleVrRelease

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nAAB Build Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nAAB Build Successful!" -ForegroundColor Green

# Build APK as well
Write-Host "Running: gradlew assembleVrRelease" -ForegroundColor Yellow
& $gradlew assembleVrRelease

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nAPK Build Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nAPK Build Successful!" -ForegroundColor Green

# Find the AAB file
$aabDir = Join-Path $projectRoot "app_v2\build\outputs\bundle\vrRelease"
$aabPath = Get-ChildItem -Path $aabDir -Filter *.aab -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $aabPath) {
    Write-Host "Error: AAB not found in $aabDir" -ForegroundColor Red
    exit 1
}

Write-Host "AAB location: $($aabPath.FullName)" -ForegroundColor Green

# Copy AAB to DOWNLOADS folder
$downloadsDir = Join-Path $projectRoot "DOWNLOADS"
if (-not (Test-Path -Path $downloadsDir)) {
    New-Item -Path $downloadsDir -ItemType Directory | Out-Null
}

$destAabPath = Join-Path $downloadsDir "FastMediaSorter_vr_release.aab"
Copy-Item -Path $aabPath.FullName -Destination $destAabPath -Force
Write-Host "AAB copied to $destAabPath" -ForegroundColor Green

# Get AAB file size
$aabSize = [math]::Round($aabPath.Length / 1MB, 2)
Write-Host "AAB size: $aabSize MB" -ForegroundColor Cyan

# Find the APK file
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\vr\release"
$apkPath = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $apkPath) {
    Write-Host "Warning: APK not found in $apkDir" -ForegroundColor Yellow
} else {
    Write-Host "APK location: $($apkPath.FullName)" -ForegroundColor Green
    $destApkPath = Join-Path $downloadsDir "FastMediaSorter_vr_release.apk"
    Copy-Item -Path $apkPath.FullName -Destination $destApkPath -Force
    Write-Host "APK copied to $destApkPath" -ForegroundColor Green
    $apkSize = [math]::Round($apkPath.Length / 1MB, 2)
    Write-Host "APK size: $apkSize MB" -ForegroundColor Cyan
}

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | vr-release-aab | FastMediaSorter_vr_release.aab ($aabSize MB)"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Try to copy to Google Drive if path exists
$gdPath = "c:\GD\WORK\FastMediaSorter"
if (Test-Path -Path $gdPath) {
    try {
        # Create ZIP with password protection if 7z is available
        $sevenZipPath = "C:\Program Files\7-Zip\7z.exe"
        if (Test-Path -Path $sevenZipPath) {
            $zipPath = Join-Path $gdPath "FastMediaSorter_vr_release.zip"
            # Remove old ZIP first to guarantee fresh archive
            if (Test-Path -Path $zipPath) {
                Remove-Item -Path $zipPath -Force
                Write-Host "Removed old ZIP (will recreate fresh)" -ForegroundColor Gray
            }
            Write-Host "Creating password-protected ZIP..." -ForegroundColor Yellow
            Push-Location $downloadsDir
            & $sevenZipPath a -tzip -p1 "$zipPath" "FastMediaSorter_vr_release.aab" "FastMediaSorter_vr_release.apk" | Out-Null
            Pop-Location
            Write-Host "Archived to Google Drive: $zipPath" -ForegroundColor Cyan
        }
    }
    catch {
        Write-Host "Warning: Failed to copy to Google Drive - $($_.Exception.Message)" -ForegroundColor Yellow
    }
}
