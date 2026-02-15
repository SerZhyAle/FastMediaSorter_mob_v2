# K.5 Emulator Test Automation Script
# 
# This script helps test FastMediaSorter on multiple Android API levels
# Usage: .\scripts\test-compatibility.ps1

param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("28", "29", "30", "33", "35", "all")]
    [string]$ApiLevel = "all"
)

$ErrorActionPreference = "Stop"

# Color output functions
function Write-Success { Write-Host $args[0] -ForegroundColor Green }
function Write-Info { Write-Host $args[0] -ForegroundColor Cyan }
function Write-Warn { Write-Host $args[0] -ForegroundColor Yellow }
function Write-Fail { Write-Host $args[0] -ForegroundColor Red }

# Configuration
$ProjectRoot = "C:\GIT\FastMediaSorter_mob_v2"
$ApkPath = "$ProjectRoot\DOWNLOADS\FastMediaSorter_standard_debug.apk"
$LogOutputDir = "$ProjectRoot\temp\emulator_logs"

# Emulator configurations
$Emulators = @{
    "28" = "Pixel_4_API_28"
    "29" = "Pixel_4_API_29"
    "30" = "Pixel_4_API_30"
    "33" = "Pixel_4_API_33"
    "35" = "Pixel_4_API_35"
}

# Ensure log directory exists
New-Item -ItemType Directory -Path $LogOutputDir -Force | Out-Null

function Test-AdbAvailable {
    try {
        $null = adb version
        return $true
    }
    catch {
        Write-Fail "ADB not found in PATH. Add Android SDK platform-tools to your PATH."
        return $false
    }
}

function Test-ApkExists {
    if (-not (Test-Path $ApkPath)) {
        Write-Fail "APK not found at: $ApkPath"
        Write-Info "Build the app first: .\scripts\builders\build-debug.PS1"
        return $false
    }
    return $true
}

function Get-ConnectedDevices {
    $devices = adb devices | Select-String -Pattern "device$" | ForEach-Object { ($_ -split "\s+")[0] }
    return $devices
}

function Start-EmulatorIfNeeded {
    param([string]$EmulatorName)
    
    $runningEmulators = Get-ConnectedDevices
    
    if ($runningEmulators -notcontains "emulator-*") {
        Write-Info "Starting emulator: $EmulatorName"
        Start-Process -FilePath "emulator" -ArgumentList "@$EmulatorName" -NoNewWindow
        
        Write-Info "Waiting for emulator to boot..."
        $timeout = 120
        $elapsed = 0
        
        while ($elapsed -lt $timeout) {
            Start-Sleep -Seconds 5
            $elapsed += 5
            
            $bootCompleted = adb shell getprop sys.boot_completed 2>$null
            if ($bootCompleted -match "1") {
                Write-Success "Emulator booted successfully!"
                Start-Sleep -Seconds 5  # Additional wait for stability
                return $true
            }
            
            Write-Info "Booting... ($elapsed/$timeout seconds)"
        }
        
        Write-Fail "Emulator boot timeout!"
        return $false
    }
    
    return $true
}

function Install-Apk {
    param([string]$DeviceSerial)
    
    Write-Info "Installing APK..."
    
    if ($DeviceSerial) {
        $output = adb -s $DeviceSerial install -r $ApkPath 2>&1
    }
    else {
        $output = adb install -r $ApkPath 2>&1
    }
    
    if ($LASTEXITCODE -eq 0 -or $output -match "Success") {
        Write-Success "APK installed successfully!"
        return $true
    }
    else {
        Write-Fail "APK installation failed: $output"
        return $false
    }
}

function Start-AppAndCaptureLogs {
    param(
        [string]$ApiLevel,
        [string]$DeviceSerial,
        [int]$Duration = 30
    )
    
    Write-Info "Launching app..."
    
    # Clear logcat buffer
    if ($DeviceSerial) {
        adb -s $DeviceSerial logcat -c
        adb -s $DeviceSerial shell am start -n com.sza.fastmediasorter/.ui.splash.SplashActivity
    }
    else {
        adb logcat -c
        adb shell am start -n com.sza.fastmediasorter/.ui.splash.SplashActivity
    }
    
    Start-Sleep -Seconds 2
    
    Write-Info "Capturing logs for $Duration seconds..."
    Write-Warn "MANUAL TESTING REQUIRED:"
    Write-Warn "  - Navigate through Browse/Player activities"
    Write-Warn "  - Test permission prompts"
    Write-Warn "  - Verify memory tier detection in logs"
    Write-Warn "  - Check UI layout (grid columns, scrolling)"
    
    $logFile = "$LogOutputDir\api${ApiLevel}_test_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
    
    # Capture logs with filtering
    $filters = "MemoryTier|PermissionHelper|BrowseRecyclerView|AddResourceActivity|ImageLoadingManager"
    
    if ($DeviceSerial) {
        adb -s $DeviceSerial logcat -d | Select-String -Pattern $filters | Out-File -FilePath $logFile -Encoding UTF8
    }
    else {
        adb logcat -d | Select-String -Pattern $filters | Out-File -FilePath $logFile -Encoding UTF8
    }
    
    Write-Success "Logs saved to: $logFile"
}

function Get-DeviceInfo {
    param([string]$DeviceSerial)
    
    Write-Info "`nDevice Information:"
    
    if ($DeviceSerial) {
        $apiLevel = adb -s $DeviceSerial shell getprop ro.build.version.sdk
        $ram = adb -s $DeviceSerial shell cat /proc/meminfo | Select-String -Pattern "MemTotal"
        $density = adb -s $DeviceSerial shell getprop ro.sf.lcd_density
    }
    else {
        $apiLevel = adb shell getprop ro.build.version.sdk
        $ram = adb shell cat /proc/meminfo | Select-String -Pattern "MemTotal"
        $density = adb shell getprop ro.sf.lcd_density
    }
    
    Write-Info "  API Level: $apiLevel"
    Write-Info "  RAM: $ram"
    Write-Info "  Density: $density DPI"
}

function Test-SingleApi {
    param([string]$Api)
    
    Write-Info "`n=========================================="
    Write-Info "Testing API Level $Api"
    Write-Info "==========================================`n"
    
    $emulatorName = $Emulators[$Api]
    
    if (-not $emulatorName) {
        Write-Fail "No emulator configured for API $Api"
        return $false
    }
    
    # Start emulator if needed
    if (-not (Start-EmulatorIfNeeded -EmulatorName $emulatorName)) {
        return $false
    }
    
    # Get device serial
    $devices = Get-ConnectedDevices
    $deviceSerial = $devices[0]
    
    # Show device info
    Get-DeviceInfo -DeviceSerial $deviceSerial
    
    # Install APK
    if (-not (Install-Apk -DeviceSerial $deviceSerial)) {
        return $false
    }
    
    # Start app and capture logs
    Start-AppAndCaptureLogs -ApiLevel $Api -DeviceSerial $deviceSerial -Duration 30
    
    Write-Success "`nAPI $Api test setup complete!"
    Write-Info "Review checklist: temp\K5_COMPATIBILITY_TEST_CHECKLIST.md"
    
    return $true
}

# Main execution
Write-Info "FastMediaSorter K.5 Compatibility Testing"
Write-Info "=========================================`n"

# Prechecks
if (-not (Test-AdbAvailable)) { exit 1 }
if (-not (Test-ApkExists)) { exit 1 }

# Run tests
if ($ApiLevel -eq "all") {
    Write-Info "Testing all API levels: 28, 29, 30, 33, 35`n"
    Write-Warn "This will launch emulators sequentially."
    Write-Warn "Press Ctrl+C to cancel.`n"
    Start-Sleep -Seconds 3
    
    foreach ($api in @("28", "29", "30", "33", "35")) {
        Test-SingleApi -Api $api
        
        Write-Info "`nPress ENTER to continue to next API level (or Ctrl+C to stop)..."
        Read-Host
    }
}
else {
    Test-SingleApi -Api $ApiLevel
}

Write-Success "`n=========================================="
Write-Success "All tests complete!"
Write-Success "==========================================`n"
Write-Info "Check logs in: $LogOutputDir"
Write-Info "Review checklist: temp\K5_COMPATIBILITY_TEST_CHECKLIST.md"
