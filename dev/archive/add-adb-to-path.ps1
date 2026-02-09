# Add Android SDK Platform Tools to PATH
# Usage: .\scripts\add-adb-to-path.ps1

$androidSdkPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools"

if (-not (Test-Path $androidSdkPath)) {
    # Try alternative location
    $androidSdkPath = "$env:ANDROID_HOME\platform-tools"
    if (-not (Test-Path $androidSdkPath)) {
        Write-Host "❌ Android SDK platform-tools not found!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Expected location: $env:LOCALAPPDATA\Android\Sdk\platform-tools" -ForegroundColor Yellow
        Write-Host "Install Android SDK or set ANDROID_HOME environment variable" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "Found Android SDK: $androidSdkPath" -ForegroundColor Green

# Check if already in PATH
if ($env:PATH -like "*$androidSdkPath*") {
    Write-Host "✓ ADB already in PATH for this session" -ForegroundColor Green
}
else {
    Write-Host "Adding to PATH for this session..." -ForegroundColor Yellow
    $env:PATH += ";$androidSdkPath"
    Write-Host "✓ ADB added to PATH" -ForegroundColor Green
}

Write-Host ""
Write-Host "Testing adb command..." -ForegroundColor Cyan
try {
    $adbVersion = adb version 2>&1 | Select-Object -First 1
    Write-Host "✓ $adbVersion" -ForegroundColor Green
}
catch {
    Write-Host "❌ adb command still not working" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ADB is now available in this terminal!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Commands you can now use:" -ForegroundColor Yellow
Write-Host "  adb devices" -ForegroundColor Gray
Write-Host "  adb shell" -ForegroundColor Gray
Write-Host "  adb install app.apk" -ForegroundColor Gray
Write-Host ""
Write-Host "⚠ This is temporary for current session only" -ForegroundColor Yellow
Write-Host ""
Write-Host "To make PERMANENT:" -ForegroundColor Cyan
Write-Host "  1. Windows Search → 'Environment Variables'" -ForegroundColor Gray
Write-Host "  2. System Properties → Environment Variables" -ForegroundColor Gray
Write-Host "  3. User Variables → PATH → Edit → New" -ForegroundColor Gray
Write-Host "  4. Add: $androidSdkPath" -ForegroundColor White
Write-Host "  5. OK → Restart terminal" -ForegroundColor Gray
Write-Host ""
