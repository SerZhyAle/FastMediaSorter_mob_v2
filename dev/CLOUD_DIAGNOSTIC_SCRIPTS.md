# Диагностические скрипты для облаков

## Скрипт 1: Полная диагностика

**Файл**: `scripts/cloud-diagnostic.ps1`

```powershell
param([string]$flavor = "standard")

Write-Host "Cloud Integration Diagnostic Tool" -ForegroundColor Cyan
Write-Host "Flavor: $flavor`n" -ForegroundColor Cyan

$errorCount = 0
$warningCount = 0

# 1. CHECK FLAVOR SUPPORT
Write-Host "[1] Checking Flavor Support..." -ForegroundColor Yellow
$buildGradle = Get-Content "app_v2\build.gradle.kts" -Raw
$cloudSupport = $buildGradle -match "create\(`"$flavor`"\).*?buildConfigField\(`"boolean`", `"SUPPORT_CLOUD`", `"true`""

if ($cloudSupport) {
    Write-Host "  ✓ Cloud support: ENABLED" -ForegroundColor Green
} else {
    Write-Host "  ✗ Cloud support: DISABLED" -ForegroundColor Red
    $errorCount++
}

# 2. CHECK FILES
Write-Host "`n[2] Checking Required Files..." -ForegroundColor Yellow
$files = @{
    "google-services.json" = "app_v2\google-services.json"
    "msal_config.json" = "app_v2\src\main\res\raw\msal_config.json"
}

foreach ($fileDesc in $files.GetEnumerator()) {
    if (Test-Path $fileDesc.Value) {
        Write-Host "  ✓ $($fileDesc.Key) found" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $($fileDesc.Key) NOT FOUND" -ForegroundColor Red
        $errorCount++
    }
}

# 3. CHECK DROPBOX APP KEY
Write-Host "`n[3] Checking Dropbox App Key..." -ForegroundColor Yellow
$dropboxMatch = $buildGradle | Select-String 'manifestPlaceholders\["dropboxAppKey"\]\s*=\s*"([^"]+)"'

if ($dropboxMatch) {
    $appKey = $dropboxMatch.Matches[0].Groups[1].Value
    if ($appKey -and $appKey -ne "YOUR_APP_KEY_HERE") {
        Write-Host "  ✓ Dropbox App Key configured: $appKey" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Dropbox App Key is placeholder" -ForegroundColor Red
        $errorCount++
    }
} else {
    Write-Host "  ✗ Dropbox App Key NOT FOUND" -ForegroundColor Red
    $errorCount++
}

# 4. CHECK DEPENDENCIES
Write-Host "`n[4] Checking Cloud Dependencies..." -ForegroundColor Yellow
$gradleOutput = & .\gradlew.bat :app_v2:dependencies --console=plain 2>&1 | Out-String

$dependencies = @{
    "play-services-auth" = "Google Sign-In"
    "msal" = "OneDrive (MSAL)"
    "dropbox-core-sdk" = "Dropbox"
}

foreach ($dep in $dependencies.GetEnumerator()) {
    if ($gradleOutput -like "*$($dep.Key)*") {
        Write-Host "  ✓ $($dep.Value) found" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ $($dep.Value) NOT found" -ForegroundColor Yellow
        $warningCount++
    }
}

# 5. CHECK PERMISSIONS
Write-Host "`n[5] Checking Permissions..." -ForegroundColor Yellow
$manifest = Get-Content "app_v2\src\main\AndroidManifest.xml" -Raw

if ($manifest -like "*android.permission.INTERNET*") {
    Write-Host "  ✓ INTERNET permission found" -ForegroundColor Green
} else {
    Write-Host "  ✗ INTERNET permission NOT FOUND" -ForegroundColor Red
    $errorCount++
}

# 6. CHECK CLOUD CLASSES
Write-Host "`n[6] Checking Cloud Classes..." -ForegroundColor Yellow
$cloudClasses = @{
    "GoogleDriveRestClient" = "app_v2\src\main\java\com\sza\fastmediasorter\data\cloud\GoogleDriveRestClient.kt"
    "OneDriveRestClient" = "app_v2\src\main\java\com\sza\fastmediasorter\data\cloud\OneDriveRestClient.kt"
    "DropboxClient" = "app_v2\src\main\java\com\sza\fastmediasorter\data\cloud\DropboxClient.kt"
}

foreach ($class in $cloudClasses.GetEnumerator()) {
    if (Test-Path $class.Value) {
        Write-Host "  ✓ $($class.Key) exists" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $($class.Key) NOT FOUND" -ForegroundColor Red
        $errorCount++
    }
}

# SUMMARY
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Errors: $errorCount" -ForegroundColor $(if ($errorCount -gt 0) { "Red" } else { "Green" })
Write-Host "Warnings: $warningCount" -ForegroundColor $(if ($warningCount -gt 0) { "Yellow" } else { "Green" })

if ($errorCount -eq 0 -and $warningCount -eq 0) {
    Write-Host "`n✓ All checks passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n✗ Issues found" -ForegroundColor Red
    exit 1
}
```

---

## Скрипт 2: Проверка SHA-1

**Файл**: `scripts/check-sha1.ps1`

```powershell
Write-Host "SHA-1 Fingerprint Extractor`n" -ForegroundColor Cyan

$signingReport = & .\gradlew.bat signingReport --console=plain 2>&1 | Out-String
$sha1Matches = [regex]::Matches($signingReport, "SHA1:\s+([A-F0-9:]+)")

if ($sha1Matches.Count -eq 0) {
    Write-Host "✗ Could not extract SHA-1" -ForegroundColor Red
    exit 1
}

Write-Host "Found $($sha1Matches.Count) fingerprints:`n" -ForegroundColor Green

foreach ($match in $sha1Matches) {
    $sha1 = $match.Groups[1].Value
    $details = $signingReport.Substring([Math]::Max(0, $match.Index - 200), 200)
    
    if ($details -like "*Debug*") {
        Write-Host "DEBUG SHA-1:" -ForegroundColor Cyan
        Write-Host "  $sha1" -ForegroundColor Green
        Write-Host "  → Firebase Console → Settings → SHA-1 Certificates`n" -ForegroundColor Gray
    } elseif ($details -like "*Release*") {
        Write-Host "RELEASE SHA-1:" -ForegroundColor Cyan
        Write-Host "  $sha1" -ForegroundColor Green
        Write-Host "  → Google Play Console → Settings → App Signing`n" -ForegroundColor Gray
    }
}
```

---

## Скрипт 3: Тестирование интеграции

**Файл**: `scripts/test-cloud-integration.ps1`

```powershell
param(
    [string]$flavor = "standard",
    [switch]$install = $true,
    [switch]$run = $true
)

$ErrorActionPreference = "Stop"

Write-Host "Cloud Integration Test - Flavor: $flavor`n" -ForegroundColor Cyan

# BUILD
Write-Host "[1] Building..." -ForegroundColor Yellow
$buildTask = "assemble$(([char]($flavor[0]).ToString().ToUpper() + $flavor.Substring(1)))Debug"

& .\gradlew.bat $buildTask -Dorg.gradle.parallel=true
Write-Host "✓ Build successful`n" -ForegroundColor Green

# INSTALL
if ($install) {
    Write-Host "[2] Installing APK..." -ForegroundColor Yellow
    $apkPath = Get-ChildItem "app_v2\build\outputs\apk\$flavor\debug\*.apk" -Recurse | Select-Object -First 1
    
    if (-not $apkPath) {
        Write-Host "✗ APK not found" -ForegroundColor Red
        exit 1
    }
    
    & adb install -r "$($apkPath.FullName)"
    Write-Host "✓ Installation successful`n" -ForegroundColor Green
}

# RUN AND CHECK LOGS
if ($run) {
    Write-Host "[3] Running app and checking logs..." -ForegroundColor Yellow
    Write-Host "    Press Ctrl+C to stop`n" -ForegroundColor Gray
    
    & adb shell am start -n "com.sza.fastmediasorter/.ui.main.MainActivity"
    Start-Sleep -Seconds 2
    
    Write-Host "Cloud Initialization Logs:" -ForegroundColor Cyan
    Write-Host "------------------------------------" -ForegroundColor Cyan
    
    & adb logcat -s "CloudAuthenticationHelper:D" -s "GoogleDriveRestClient:D" `
                 -s "OneDriveRestClient:D" -s "DropboxClient:D" | Select-Object -First 50
}
```

---

## Скрипт 4: Очистка состояния

**Файл**: `scripts/clean-cloud-state.ps1`

```powershell
param([switch]$confirm = $false)

Write-Host "Cloud State Cleaner`n" -ForegroundColor Cyan

if (-not $confirm) {
    $response = Read-Host "Clear all cloud data? (yes/no)"
    if ($response -ne "yes") {
        Write-Host "Cancelled" -ForegroundColor Yellow
        exit 0
    }
}

# Clear app data
Write-Host "[1] Clearing app data..." -ForegroundColor Yellow
& adb shell pm clear com.sza.fastmediasorter
Write-Host "✓ App data cleared`n" -ForegroundColor Green

# Uninstall
Write-Host "[2] Uninstalling app..." -ForegroundColor Yellow
& adb uninstall com.sza.fastmediasorter
Write-Host "✓ App uninstalled`n" -ForegroundColor Green

# Clean Gradle
Write-Host "[3] Cleaning Gradle..." -ForegroundColor Yellow
& .\gradlew.bat clean
Write-Host "✓ Gradle cleaned`n" -ForegroundColor Green

Write-Host "✓ Complete! Now rebuild and reinstall." -ForegroundColor Green
```

---

## Bash версии (Linux/Mac)

### Диагностика

**Файл**: `scripts/cloud-diagnostic.sh`

```bash
#!/bin/bash
FLAVOR=${1:-standard}
ERROR_COUNT=0

echo "Cloud Integration Diagnostic - Flavor: $FLAVOR"

# Check flavor
if grep -q "create(\"$FLAVOR\").*buildConfigField.*SUPPORT_CLOUD.*true" app_v2/build.gradle.kts; then
    echo "  ✓ Cloud support: ENABLED"
else
    echo "  ✗ Cloud support: DISABLED"
    ((ERROR_COUNT++))
fi

# Check files
if [[ -f "app_v2/google-services.json" ]]; then
    echo "  ✓ google-services.json found"
else
    echo "  ✗ google-services.json NOT FOUND"
    ((ERROR_COUNT++))
fi

if [[ -f "app_v2/src/main/res/raw/msal_config.json" ]]; then
    echo "  ✓ msal_config.json found"
else
    echo "  ✗ msal_config.json NOT FOUND"
    ((ERROR_COUNT++))
fi

# Summary
echo ""
echo "Errors: $ERROR_COUNT"

if [[ $ERROR_COUNT -eq 0 ]]; then
    echo "✓ All checks passed!"
    exit 0
else
    echo "✗ Errors found"
    exit 1
fi
```

### SHA-1

**Файл**: `scripts/check-sha1.sh`

```bash
#!/bin/bash
echo "SHA-1 Fingerprint Extractor"
./gradlew signingReport 2>/dev/null | grep -A 1 "SHA1:"
```

---

## CI/CD интеграция

```yaml
name: Cloud Integration Check

on: [push, pull_request]

jobs:
  cloud-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: 17
      
      - name: Run diagnostic
        run: bash scripts/cloud-diagnostic.sh standard
      
      - name: Build
        run: ./gradlew assembleStandardDebug
```

---

## Использование

```powershell
# Полная диагностика
.\scripts\cloud-diagnostic.ps1 -flavor standard

# SHA-1
.\scripts\check-sha1.ps1

# Тестирование
.\scripts\test-cloud-integration.ps1 -flavor standard -install -run

# Очистка
.\scripts\clean-cloud-state.ps1 -confirm
```
