# Quick debug build script with auto-versioning
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)

Write-Host "Building debug APK (auto-versioned)..." -ForegroundColor Cyan

# Generate version code and name
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

# Update build.gradle.kts
$buildGradlePath = "$PSScriptRoot\..\..\\app_v2\build.gradle.kts"
$content = Get-Content $buildGradlePath -Raw
$content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$versionCodeInt"
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""
Set-Content $buildGradlePath $content -NoNewline

# Start the Gradle build process (Debug only for speed)
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"

function Invoke-DebugBuild {
    param(
        [switch]$DisableBuildCache
    )

    $gradleArgs = @("assembleStandardDebug")
    if ($DisableBuildCache) {
        $gradleArgs += "--no-build-cache"
        $gradleArgs += "--rerun-tasks"
    }

    $output = & "$PSScriptRoot\..\..\\gradlew.bat" @gradleArgs 2>&1
    $output | ForEach-Object { Write-Host $_ }

    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output | Out-String)
    }
}

$buildResult = Invoke-DebugBuild

if ($buildResult.ExitCode -ne 0) {
    $isKotlinCachePackError =
        ($buildResult.Output -match "Failed to store cache entry") -and
        ($buildResult.Output -match "Could not pack tree") -and
        ($buildResult.Output -match "lookups\.tab\.values")

    if ($isKotlinCachePackError) {
        Write-Host "`nDetected Kotlin/Gradle cache packing issue. Cleaning local caches and retrying once..." -ForegroundColor Yellow

        $localGradleCachePath = Join-Path $projectRoot ".gradle"
        $moduleKotlinCachePath = Join-Path $projectRoot "app_v2\build\kotlin"

        if (Test-Path -Path $localGradleCachePath) {
            Remove-Item -Path $localGradleCachePath -Recurse -Force -ErrorAction SilentlyContinue
        }
        if (Test-Path -Path $moduleKotlinCachePath) {
            Remove-Item -Path $moduleKotlinCachePath -Recurse -Force -ErrorAction SilentlyContinue
        }

        $buildResult = Invoke-DebugBuild -DisableBuildCache
    }
}

if ($buildResult.ExitCode -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $buildResult.ExitCode
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"

# Resolve actual APK path from AGP output metadata (fallback to newest .apk)
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\debug"
$metadataPath = Join-Path $apkDir "output-metadata.json"
$apkPath = $null

if (Test-Path -Path $metadataPath) {
    try {
        $meta = Get-Content -Path $metadataPath -Raw | ConvertFrom-Json
        if ($meta.elements -and $meta.elements.Count -gt 0 -and $meta.elements[0].outputFile) {
            $apkPath = Join-Path $apkDir $meta.elements[0].outputFile
        }
    }
    catch {
        Write-Host "Warning: Failed to parse output-metadata.json, falling back to latest APK." -ForegroundColor Yellow
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    $latestApk = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestApk) {
        $apkPath = $latestApk.FullName
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan

if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_standard_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | standard-debug | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Zip with password and copy to Google Drive
$gdDir = "c:\GD\WORK\FastMediaSorter"
if (!(Test-Path -Path $gdDir)) {
    New-Item -ItemType Directory -Path $gdDir | Out-Null
}

$zipName = [System.IO.Path]::ChangeExtension($destName, ".zip")
$zipPath = "$gdDir\$zipName"

# Use 7-Zip to create password-protected archive
$7zipPath = "C:\Program Files\7-Zip\7z.exe"
if (Test-Path -Path $7zipPath) {
    & $7zipPath a -tzip -p1 "$zipPath" "$downloadsDir\$destName" | Out-Null
    Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
    # Write-Host "Password: 1" -ForegroundColor Yellow
}
else {
    Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
    Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
}