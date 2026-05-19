# Build NoLegal Debug APK
# S0156: noLegal = standard + VR + sideload-only capabilities (single universal APK).
# Installs on any Android device (4 ABIs); OpenXR bridge compiled for arm64-v8a only.
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

Write-Host "Building NoLegal Debug APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Standard runtime + sideload-only extras (NewPipe, etc.)" -ForegroundColor Yellow
Write-Host "Distribution: ADB sideload only — not for any public store." -ForegroundColor Magenta

# Generate version
$now = Get-Date
$yy  = $now.ToString("yy")
$mon = $now.ToString("MM")   # "mon" avoids PowerShell case-insensitive clash with $mm
$dd  = $now.ToString("dd")
$HH  = $now.ToString("HH")
$mm  = $now.ToString("mm")

# versionCode: YYMMDDHHm (9 digits, first digit of minutes — avoids Int32 overflow)
$versionCodeInt = [Convert]::ToInt32($now.ToString("yyMMddHH") + $mm[0])
# versionName: Y.YM.MDDH.Hmm  e.g. 2.60.4260.457
$versionName = "$($yy[0]).$($yy[1])$($mon[0]).$($mon[1])$dd$($HH[0]).$($HH[1])$mm"

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

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
#   `chaquopy.enabled=true` line in `local.properties` (which is gitignored and
#   may be commented out / absent on a fresh checkout).
& $gradlew assembleNoLegalDebug "-Pchaquopy.enabled=true" --no-configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\noLegal\debug"
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
        Write-Host "Warning: Failed to parse output-metadata.json" -ForegroundColor Yellow
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    $latestApk = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestApk) { $apkPath = $latestApk.FullName }
}

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
