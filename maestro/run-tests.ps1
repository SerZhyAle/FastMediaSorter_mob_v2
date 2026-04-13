# Maestro Test Suite Runner
# FastMediaSorter v2
# Usage: .\maestro\run-tests.ps1 [smoke|critical|all]

param(
    [string]$Suite = "all",
    [switch]$DebugMode,
    [int]$MaxMinutes = 20
)

$MaestroDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $MaestroDir

Write-Host "🚀 FastMediaSorter v2 - Maestro E2E Tests" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Add custom Maestro path if exists
$customMaestroPath = "c:\GD\tc\Programm\maestro\bin"
if (Test-Path $customMaestroPath) {
    $env:PATH += ";$customMaestroPath"
}

# Ensure Java 17+ is used by maestro.bat (RedHat Java 8 may be on PATH)
$java21 = "C:\Program Files\Java\jdk-21.0.10"
if (Test-Path $java21) {
    $env:JAVA_HOME = $java21
    $env:PATH = "$java21\bin;$env:PATH"
}

# Check if Maestro is installed (try both 'maestro' and 'maestro-cli')
$maestroCmd = Get-Command maestro -ErrorAction SilentlyContinue
if (-not $maestroCmd) {
    $maestroCmd = Get-Command maestro-cli -ErrorAction SilentlyContinue
}

if (-not $maestroCmd) {
    Write-Host "❌ Maestro CLI not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Maestro Mobile from: https://maestro.mobile.dev" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Windows (PowerShell as Administrator):" -ForegroundColor Yellow
    Write-Host '  Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1' -ForegroundColor Cyan
    Write-Host '  .\install.ps1' -ForegroundColor Cyan
    Write-Host '  Remove-Item install.ps1' -ForegroundColor Cyan
    Write-Host ""
    Write-Host "macOS/Linux (Homebrew):" -ForegroundColor Yellow
    Write-Host '  brew tap mobile-dev-inc/tap' -ForegroundColor Cyan
    Write-Host '  brew install maestro' -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Note: 'npm install -g maestro-cli' installs WRONG package!" -ForegroundColor Red
    exit 1
}

$maestroCmdName = $maestroCmd.Name
Write-Host "✓ Maestro CLI found ($maestroCmdName)" -ForegroundColor Green

# Check if ADB is available
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    # Try to find ADB in common Android SDK locations
    $possiblePaths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    )
    
    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $adbCmd = Get-Command $path
            break
        }
    }
}

if (-not $adbCmd) {
    Write-Host "❌ ADB (Android Debug Bridge) not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Android SDK Platform Tools:" -ForegroundColor Yellow
    Write-Host "  1. Install Android Studio: https://developer.android.com/studio" -ForegroundColor Yellow
    Write-Host "  2. Or download SDK Platform Tools: https://developer.android.com/tools/releases/platform-tools" -ForegroundColor Yellow
    Write-Host "  3. Add platform-tools to your PATH" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Typical location: %LOCALAPPDATA%\Android\Sdk\platform-tools" -ForegroundColor Yellow
    exit 1
}

$adbPath = $adbCmd.Source
Write-Host "✓ ADB found" -ForegroundColor Green

# Check if device is connected
$devices = & $adbPath devices | Select-String -Pattern "device$"
if ($devices.Count -eq 0) {
    Write-Host "❌ No Android device/emulator found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please connect a device or start an emulator:" -ForegroundColor Yellow
    Write-Host "  - adb devices" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Android device found" -ForegroundColor Green

# Disable animations (critical for Maestro stability)
Write-Host "Disabling animations..." -ForegroundColor Yellow
& $adbPath shell settings put global window_animation_scale 0.0 2>$null
& $adbPath shell settings put global transition_animation_scale 0.0 2>$null
& $adbPath shell settings put global animator_duration_scale 0.0 2>$null
Write-Host "✓ Animations disabled" -ForegroundColor Green

# Check if app is installed
$appInstalled = & $adbPath shell pm list packages | Select-String "com.sza.fastmediasorter"
if (-not $appInstalled) {
    Write-Host "⚠ FastMediaSorter not installed on device" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Building and installing app..." -ForegroundColor Yellow
    
    # Build debug APK
    & "$ProjectRoot\gradlew.bat" assembleStandardDebug
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Build failed!" -ForegroundColor Red
        exit 1
    }
    
    # Find APK
    $apk = Get-ChildItem -Path "$ProjectRoot\app_v2\build\outputs\apk\standard\debug" -Filter "*.apk" | Select-Object -First 1
    
    if (-not $apk) {
        Write-Host "❌ APK not found!" -ForegroundColor Red
        exit 1
    }
    
    # Install APK
    & $adbPath install -r $apk.FullName
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Installation failed!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "✓ App installed" -ForegroundColor Green
}
else {
    Write-Host "✓ App already installed" -ForegroundColor Green
}

Write-Host ""
Write-Host "Running test suite: $Suite" -ForegroundColor Cyan
Write-Host ""

function Invoke-MaestroWithWatchdog {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [int]$TimeoutMinutes
    )

    $joinedArgs = ($Arguments | ForEach-Object { $_.Trim() }) -join " "
    Write-Host "Executing: $maestroCmdName $joinedArgs" -ForegroundColor DarkGray

    $process = Start-Process -FilePath $env:ComSpec `
        -ArgumentList "/c", "$maestroCmdName $joinedArgs" `
        -NoNewWindow `
        -PassThru

    $startedAt = Get-Date
    $deadline = $startedAt.AddMinutes($TimeoutMinutes)

    while (-not $process.HasExited) {
        Start-Sleep -Seconds 2
        if ((Get-Date) -gt $deadline) {
            Write-Host "❌ Timeout: Maestro exceeded $TimeoutMinutes minutes. Killing process..." -ForegroundColor Red
            try {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            } catch {
                Write-Host "⚠ Failed to stop Maestro process cleanly: $($_.Exception.Message)" -ForegroundColor Yellow
            }
            return 124
        }
    }

    return $process.ExitCode
}

# Run tests based on suite parameter
$testPath = ""
switch ($Suite.ToLower()) {
    "smoke" {
        $testPath = "$MaestroDir\smoke"
        Write-Host "Running smoke tests..." -ForegroundColor Yellow
    }
    "critical" {
        $testPath = "$MaestroDir\critical"
        Write-Host "Running critical path tests..." -ForegroundColor Yellow
    }
    "features" {
        $testPath = "$MaestroDir\features"
        Write-Host "Running all feature tests..." -ForegroundColor Yellow
    }
    "audio" {
        $testPath = "$MaestroDir\features\audio"
        Write-Host "Running audio feature tests..." -ForegroundColor Yellow
    }
    "documents" {
        $testPath = "$MaestroDir\features\documents"
        Write-Host "Running document feature tests..." -ForegroundColor Yellow
    }
    "images" {
        $testPath = "$MaestroDir\features\images"
        Write-Host "Running image feature tests..." -ForegroundColor Yellow
    }
    "video" {
        $testPath = "$MaestroDir\features\video"
        Write-Host "Running video feature tests..." -ForegroundColor Yellow
    }
    "files" {
        $testPath = "$MaestroDir\features\files"
        Write-Host "Running file operations tests..." -ForegroundColor Yellow
    }
    "favorites" {
        $testPath = "$MaestroDir\features\favorites"
        Write-Host "Running favorites tests..." -ForegroundColor Yellow
    }
    "slideshow" {
        $testPath = "$MaestroDir\features\slideshow"
        Write-Host "Running slideshow tests..." -ForegroundColor Yellow
    }
    "settings" {
        $testPath = "$MaestroDir\features\settings"
        Write-Host "Running settings tests..." -ForegroundColor Yellow
    }
    "navigation" {
        $testPath = "$MaestroDir\features\navigation"
        Write-Host "Running navigation tests..." -ForegroundColor Yellow
    }
    "translation" {
        $testPath = "$MaestroDir\features\translation"
        Write-Host "Running translation/OCR tests..." -ForegroundColor Yellow
    }
    "stress" {
        Write-Host "Redirecting to stress test runner..." -ForegroundColor Yellow
        $stressScript = Join-Path $ProjectRoot "scripts\utils\run-maestro-stress.ps1"
        if (Test-Path $stressScript) {
            & $stressScript -Suite all -Monitor -Report
            exit $LASTEXITCODE
        }
        else {
            Write-Host "❌ Stress test script not found: $stressScript" -ForegroundColor Red
            exit 1
        }
    }
    "all" {
        Write-Host "Running all tests (smoke + critical + features)..." -ForegroundColor Yellow

        $allSuites = @(
            @{ Name = "smoke"; Path = "$MaestroDir\smoke" },
            @{ Name = "critical"; Path = "$MaestroDir\critical" },
            @{ Name = "features"; Path = "$MaestroDir\features" }
        )

        $startTime = Get-Date
        $overallExit = 0

        foreach ($suiteItem in $allSuites) {
            Write-Host "" 
            Write-Host "--- Running $($suiteItem.Name) ---" -ForegroundColor Cyan

            $suiteArgs = @("test")
            if ($DebugMode) {
                $suiteArgs += "--flatten-debug-output"
            }
            $suiteArgs += $suiteItem.Path

            $suiteExit = Invoke-MaestroWithWatchdog -Arguments $suiteArgs -TimeoutMinutes $MaxMinutes
            if ($suiteExit -ne 0) {
                $overallExit = $suiteExit
                break
            }
        }

        $endTime = Get-Date
        $duration = $endTime - $startTime

        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Cyan
        Write-Host "Test Duration: $($duration.ToString('mm\:ss'))" -ForegroundColor Cyan

        if ($overallExit -eq 0) {
            Write-Host "✅ All tests passed!" -ForegroundColor Green
        }
        else {
            if ($overallExit -eq 124) {
                Write-Host "❌ Tests aborted by timeout watchdog!" -ForegroundColor Red
            }
            else {
                Write-Host "❌ Some tests failed!" -ForegroundColor Red
            }
        }

        exit $overallExit
    }
    default {
        Write-Host "❌ Unknown test suite: $Suite" -ForegroundColor Red
        Write-Host "Valid options: smoke, critical, features, audio, documents, images, video, files, favorites, slideshow, settings, navigation, translation, stress, all" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host ""

# Run Maestro tests
$startTime = Get-Date

# Construct command arguments
$cmdArgs = @("test")
if ($DebugMode) {
    # Use flatten-debug-output for terminal visibility
    $cmdArgs += "--flatten-debug-output" 
    $cmdArgs += $testPath
}
else {
    $cmdArgs += $testPath
}

$exitCode = Invoke-MaestroWithWatchdog -Arguments $cmdArgs -TimeoutMinutes $MaxMinutes
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Test Duration: $($duration.ToString('mm\:ss'))" -ForegroundColor Cyan

if ($exitCode -eq 0) {
    Write-Host "✅ All tests passed!" -ForegroundColor Green
}
else {
    Write-Host "❌ Some tests failed!" -ForegroundColor Red
}

Write-Host ""
exit $exitCode
