# Quick debug build script with auto-versioning
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)

param(
    [switch]$SkipZip,
    [string]$Task = "assembleStandardDebug",
    # S0190 Phase 01 tooling: explicit opt-in for Chaquopy on tasks that don't auto-detect.
    # Auto-enabled when -Task contains "noLegal" (case-insensitive), since the noLegal source
    # set imports com.chaquo.python.* and won't compile without the plugin.
    [switch]$Chaquopy,
    # Quiet mode: suppress UP-TO-DATE / NO-SOURCE / SKIPPED task lines, deprecated-DSL spam,
    # and known-acceptable warnings. Errors, FAIL/SUCCESS verdict, and unknown warnings are
    # always preserved. The full unfiltered output is still captured for retry-detection.
    [switch]$Quiet
)

# Patterns that are suppressed in -Quiet mode. Order: most-frequent first.
# Update this list when adding intentional warnings; do NOT use it to hide real failures.
$script:QuietSuppressPatterns = @(
    '^> Task :app_v2:[A-Za-z0-9_]+ (UP-TO-DATE|NO-SOURCE|SKIPPED|FROM-CACHE)$',
    '^WARNING: The option setting ''android\.(builtInKotlin|newDsl)=false'' is deprecated\.',
    '^WARNING: API ''(applicationVariants|testVariants|unitTestVariants)'' is obsolete',
    '^The current default is ''true''\.$',
    '^It will be removed in version 10\.0 of the Android Gradle plugin\.$',
    '^The legacy variant API is disabled by default in AGP 9\.0',
    '^\s*android\.newDsl=false$',
    '^to this project''s gradle\.properties file\.$',
    '^For more information, see http://developer\.android\.com/build/r/new-dsl\.$',
    '^To determine what is calling (applicationVariants|testVariants|unitTestVariants), use -Pandroid\.debug\.obsoleteApi=true',
    '^Using the build cache is enabled, but no build caches are configured or enabled\.$',
    "^w: file:///.+/app_v2/build\.gradle\.kts:\d+:\d+: 'fun Project\.android",
    "^This class is not used for the public extensions in AGP",
    "^w: file:///.+/PlayerActivity\.kt:(678|686|689|1122):14 'open' has no effect on a final class\.$",
    '^Note: \[\d+\] Wrote GeneratedAppGlideModule',
    '^warning: The following options were not recognized by any processor:',
    '^$'  # blank lines between suppressed blocks
)

function Test-QuietSuppress([string]$line) {
    foreach ($p in $script:QuietSuppressPatterns) {
        if ($line -match $p) { return $true }
    }
    return $false
}

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
Set-Location $projectRoot


function Stop-GradleDaemons {
    Write-Host "Stopping Gradle daemons before cleanup.." -ForegroundColor DarkGray
    & "$PSScriptRoot\..\..\\gradlew.bat" --stop | Out-Null
}


function Remove-BuildPathIfExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $targetPath = Join-Path $projectRoot $RelativePath
    if (Test-Path -Path $targetPath) {
        Write-Host "Removing locked build path: $targetPath" -ForegroundColor DarkGray
        Remove-Item -Path $targetPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}


function Get-GradleExecutionHistoryRelativePath {
    $gradleDir = Join-Path $projectRoot ".gradle"
    if (-not (Test-Path -Path $gradleDir)) {
        return $null
    }

    $versionDir = Get-ChildItem -Path $gradleDir -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d+\.\d+(\.\d+)?$' } |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($null -eq $versionDir) {
        return $null
    }

    return ".gradle\$($versionDir.Name)\executionHistory"
}


function Invoke-DebugBuild {
    param(
        [switch]$DisableBuildCache,
        [switch]$NoDaemon
    )

    # S0190 Phase 01: enable Chaquopy when -Chaquopy switch is set OR when the task name
    # contains "noLegal" (the only flavor that bundles the Python runtime). Standard / lite /
    # photos / legacy builds keep Chaquopy off for speed.
    $enableChaquopy = $script:Chaquopy.IsPresent -or ($script:Task -match '(?i)noLegal')
    $chaquopyFlag = if ($enableChaquopy) { "-Pchaquopy.enabled=true" } else { "-Pchaquopy.enabled=false" }
    $gradleArgs = @($script:Task, $chaquopyFlag)
    if ($DisableBuildCache) {
        $gradleArgs += "--no-build-cache"
        $gradleArgs += "--rerun-tasks"
    }
    if ($NoDaemon) {
        $gradleArgs += "--no-daemon"
    }

    Write-Host "Starting Gradle: .\\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

    $outputLines = New-Object System.Collections.Generic.List[string]
    $suppressed = 0
    & "$PSScriptRoot\..\..\\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        $outputLines.Add($line)
        if ($script:Quiet -and (Test-QuietSuppress $line)) {
            $suppressed++
            return
        }
        Write-Host $line
    }
    if ($script:Quiet -and $suppressed -gt 0) {
        Write-Host "[quiet] suppressed $suppressed lines of known noise" -ForegroundColor DarkGray
    }

    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output   = ($outputLines -join [Environment]::NewLine)
    }
}

$buildResult = Invoke-DebugBuild
$gradleExecutionHistoryPath = Get-GradleExecutionHistoryRelativePath

if ($buildResult.ExitCode -ne 0) {
    $isGradleCachePackError =
    ($buildResult.Output -match "Failed to store cache entry") -and
    ($buildResult.Output -match "Could not pack tree")

    $isKaptIncrementalDataLockError =
    ($buildResult.Output -match "Unable to delete directory") -and
    ($buildResult.Output -match "kapt3[\\/]incrementalData[\\/]standardDebug")

    if ($isGradleCachePackError) {
        Write-Host "`nDetected Gradle cache packing issue. Stopping daemons, cleaning volatile cache state, and retrying once..." -ForegroundColor Yellow

        Stop-GradleDaemons
        if ($null -ne $gradleExecutionHistoryPath) {
            Remove-BuildPathIfExists -RelativePath $gradleExecutionHistoryPath
        }
        Remove-BuildPathIfExists -RelativePath "app_v2\build\kotlin"

        $buildResult = Invoke-DebugBuild -DisableBuildCache -NoDaemon
    }

    if (($buildResult.ExitCode -ne 0) -and $isKaptIncrementalDataLockError) {
        Write-Host "`nDetected Windows file lock in kapt incremental data. Stopping daemons, cleaning kapt temp folders, and retrying once..." -ForegroundColor Yellow

        Stop-GradleDaemons
        # Clear only the volatile kapt/kotlin outputs because stale locked stubs block the next incremental run.
        if ($null -ne $gradleExecutionHistoryPath) {
            Remove-BuildPathIfExists -RelativePath $gradleExecutionHistoryPath
        }
        Remove-BuildPathIfExists -RelativePath "app_v2\build\tmp\kapt3"
        Remove-BuildPathIfExists -RelativePath "app_v2\build\kotlin"

        $buildResult = Invoke-DebugBuild -DisableBuildCache -NoDaemon
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

# Copy raw APK to Google Drive (in addition to password-protected ZIP below).
# Recipients with security policies that block APK downloads use the .zip copy;
# the raw .apk lets fast paths skip the unzip step.
Copy-Item -Path "$downloadsDir\$destName" -Destination "$gdDir\$destName" -Force
Write-Host "APK copied to $gdDir\$destName" -ForegroundColor Green

$zipName = [System.IO.Path]::ChangeExtension($destName, ".zip")
$zipPath = "$gdDir\$zipName"

# Use 7-Zip to create password-protected archive
if ($SkipZip) {
    Write-Host "SkipZip enabled: ZIP creation and Google Drive archive copy skipped." -ForegroundColor Yellow
}
else {
    $7zipPath = "C:\Program Files\7-Zip\7z.exe"
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
        # Write-Host "Password: 1" -ForegroundColor Yellow
    }
    else {
        Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
        Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
    }
}