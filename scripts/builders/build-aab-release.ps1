# Release AAB + APK build script
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

Write-Host "Building release AAB + APK (auto-versioned)..." -ForegroundColor Cyan

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
Write-Host "Running: gradlew bundleStandardRelease" -ForegroundColor Yellow
& $gradlew bundleStandardRelease "-Pchaquopy.enabled=false"

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nAAB Build Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nAAB Build Successful!" -ForegroundColor Green

# Build APK as well
Write-Host "Running: gradlew assembleStandardRelease" -ForegroundColor Yellow
& $gradlew assembleStandardRelease "-Pchaquopy.enabled=false"

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nAPK Build Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nAPK Build Successful!" -ForegroundColor Green

# Find the AAB file
$aabDir = Join-Path $projectRoot "app_v2\build\outputs\bundle\standardRelease"
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

$destAabPath = Join-Path $downloadsDir "FastMediaSorter_standard_release.aab"
Copy-Item -Path $aabPath.FullName -Destination $destAabPath -Force
Write-Host "AAB copied to $destAabPath" -ForegroundColor Green

# Get AAB file size
$aabSize = [math]::Round($aabPath.Length / 1MB, 2)
Write-Host "AAB size: $aabSize MB" -ForegroundColor Cyan

# Find the APK file
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\release"
$apkPath = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $apkPath) {
    Write-Host "Warning: APK not found in $apkDir" -ForegroundColor Yellow
} else {
    Write-Host "APK location: $($apkPath.FullName)" -ForegroundColor Green
    $destApkPath = Join-Path $downloadsDir "FastMediaSorter_standard_release.apk"
    Copy-Item -Path $apkPath.FullName -Destination $destApkPath -Force
    Write-Host "APK copied to $destApkPath" -ForegroundColor Green
    $apkSize = [math]::Round($apkPath.Length / 1MB, 2)
    Write-Host "APK size: $apkSize MB" -ForegroundColor Cyan
}

# Try to copy to Google Drive if path exists
$gdPath = "c:\GD\WORK\FastMediaSorter"
if (Test-Path -Path $gdPath) {
    try {
        # Create ZIP with password protection if 7z is available
        $sevenZipPath = "C:\Program Files\7-Zip\7z.exe"
        if (Test-Path -Path $sevenZipPath) {
            $zipPath = Join-Path $gdPath "FastMediaSorter_standard_release.zip"
            # Remove old ZIP first to guarantee fresh archive (7z 'a' updates in-place
            # and may keep the old .aab entry if paths differ between runs)
            if (Test-Path -Path $zipPath) {
                Remove-Item -Path $zipPath -Force
                Write-Host "Removed old ZIP (will recreate fresh)" -ForegroundColor Gray
            }
            Write-Host "Creating password-protected ZIP..." -ForegroundColor Yellow
            # Push-Location into downloads dir so 7z receives relative filenames
            # and stores them without full paths inside the archive
            Push-Location -Path $downloadsDir
            $filesToZip = @("FastMediaSorter_standard_release.aab")
            if (Test-Path "FastMediaSorter_standard_release.apk") {
                $filesToZip += "FastMediaSorter_standard_release.apk"
            }
            & $sevenZipPath a -tzip -p1 -mem=AES256 $zipPath @filesToZip | Out-Null
            Pop-Location
            if ($LASTEXITCODE -eq 0) {
                Write-Host "AAB+APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Green
                # Write-Host "Password: 1" -ForegroundColor Yellow
            }
            else {
                Write-Host "Warning: Failed to create password-protected ZIP" -ForegroundColor Yellow
                Copy-Item -Path $destAabPath -Destination (Join-Path $gdPath "FastMediaSorter_standard_release.aab") -Force
                if (Test-Path -Path $destApkPath) {
                    Copy-Item -Path $destApkPath -Destination (Join-Path $gdPath "FastMediaSorter_standard_release.apk") -Force
                }
                Write-Host "AAB+APK copied to Google Drive (unprotected): $gdPath" -ForegroundColor Green
            }
        }
        else {
            Copy-Item -Path $destAabPath -Destination (Join-Path $gdPath "FastMediaSorter_standard_release.aab") -Force
            if (Test-Path -Path $destApkPath) {
                Copy-Item -Path $destApkPath -Destination (Join-Path $gdPath "FastMediaSorter_standard_release.apk") -Force
            }
            Write-Host "AAB+APK copied to Google Drive: $gdPath" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "Warning: Failed to copy to Google Drive: $_" -ForegroundColor Yellow
    }
}

# Log to builds journal
$journalPath = Join-Path $downloadsDir "builds_versions.lst"
$apkSizeStr = if ($apkPath) { ", APK: $apkSize MB" } else { "" }
$buildInfo = "AAB+APK Release - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') - AAB: $aabSize MB$apkSizeStr - Version: $versionName"
Add-Content -Path $journalPath -Value $buildInfo
Write-Host "Build logged to journal" -ForegroundColor Cyan

# Copy APK to tc folder
$tcDir = "c:\GD\tc\SZA\_APP"
if (!(Test-Path -Path $tcDir)) {
    New-Item -ItemType Directory -Path $tcDir | Out-Null
}
if (Test-Path -Path $destApkPath) {
    Copy-Item -Path $destApkPath -Destination "$tcDir\FastMediaSorter_standard_release.apk" -Force
    Write-Host "APK copied to $tcDir\FastMediaSorter_standard_release.apk" -ForegroundColor Green
}

Write-Host "`nAAB + APK build complete!" -ForegroundColor Green
Write-Host "Ready for upload to Google Play Console" -ForegroundColor Cyan
