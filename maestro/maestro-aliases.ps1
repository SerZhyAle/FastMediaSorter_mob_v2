#!/usr/bin/env powershell
# Maestro Quick Setup Aliases
# Run this to add convenient aliases for Maestro commands

param(
    [switch]$Help,
    [switch]$Install
)

function Show-Help {
    Write-Host @"
Maestro Quick Setup Aliases

This script adds convenient PowerShell aliases for Maestro commands.

USAGE:
  .\maestro-aliases.ps1 -Install     # Add aliases permanently
  .\maestro-aliases.ps1              # Show available aliases
  .\maestro-aliases.ps1 -Help        # Show this help

ALIASES ADDED:
  maestro           maestro command wrapper
  maestro-tests     Run all smoke tests
  maestro-single    Run single test (maestro-single app_launch)
  maestro-debug     Run with debug output (maestro-debug app_launch)
  maestro-studio    Open interactive Maestro Studio
  maestro-devices   Show connected devices
  maestro-build     Build and install app
  adb               Android Debug Bridge wrapper

EXAMPLES:
  maestro-tests              # Run all 6 smoke tests
  maestro-single app_launch  # Run specific test
  maestro-debug local_browse # Run with debug output
  maestro-studio             # Open Maestro Studio (web UI)
  maestro-devices            # List Android devices
  maestro-build              # Build app
  adb devices                # Show devices (requires Android SDK)

"@
}

if ($Help) {
    Show-Help
    exit 0
}

Write-Host "🔧 Setting up Maestro aliases..."

# Get Maestro path
$maestroCmd = Get-Command maestro -ErrorAction SilentlyContinue
if (-not $maestroCmd) {
    Write-Host "❌ Maestro not found in PATH"
    Write-Host "Install with: npm install -g maestro-cli"
    exit 1
}

# Get ADB path
$adbPath = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $adbPath)) {
    Write-Host "⚠️  ADB not found at: $adbPath"
    Write-Host "This is optional but recommended for full functionality"
}

# Profile path
$profilePath = $PROFILE
$profileDir = Split-Path -Parent $profilePath

if (-not (Test-Path $profileDir)) {
    New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
}

# Create aliases content
$aliasesContent = @"
# Maestro Aliases - Added $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')

# Maestro CLI wrapper
function maestro { & maestro.cmd @args }

# Run all smoke tests
function maestro-tests { 
    Push-Location "$PWD\maestro"
    maestro test smoke/
    Pop-Location
}

# Run single smoke test
function maestro-single { 
    param([string]`$Test = "app_launch")
    Push-Location "$PWD\maestro"
    maestro test smoke/`$Test.yaml
    Pop-Location
}

# Run with debug output
function maestro-debug { 
    param([string]`$Test = "app_launch")
    Push-Location "$PWD\maestro"
    maestro test --debug smoke/`$Test.yaml
    Pop-Location
}

# Open Maestro Studio (interactive)
function maestro-studio { 
    maestro studio
}

# List Android devices
function maestro-devices { 
    & "$adbPath" devices
}

# Build and install app
function maestro-build { 
    .\dev\build-with-version.ps1 -Flavor standard
}

# ADB wrapper
function adb { 
    & "$adbPath" @args
}

# Export functions
Export-ModuleMember -Function maestro, maestro-tests, maestro-single, maestro-debug, maestro-studio, maestro-devices, maestro-build, adb

"@

$adbLine = "    & `"$adbPath`" @args`n"

# Check if profile exists and contains Maestro aliases
if (Test-Path $profilePath) {
    $content = Get-Content $profilePath -Raw
    if ($content -like "*maestro-tests*") {
        Write-Host "⚠️  Aliases already in profile"
        exit 0
    }
}

if ($Install) {
    Write-Host "📝 Adding aliases to PowerShell profile..."
    Write-Host "   Profile: $profilePath"
    
    Add-Content -Path $profilePath -Value "`n`n# --- Maestro Aliases ---`n$aliasesContent" -Force
    
    Write-Host "✅ Aliases installed!"
    Write-Host "`nReload profile with:"
    Write-Host "  . `$PROFILE"
    Write-Host "`nOr close and reopen PowerShell"
}
else {
    Write-Host @"
✨ Available Maestro Aliases:

  maestro-tests        │ Run all smoke tests
  maestro-single       │ Run single test (maestro-single app_launch)
  maestro-debug        │ Run with debug output (maestro-debug local_browse)
  maestro-studio       │ Open interactive Maestro Studio
  maestro-devices      │ List connected Android devices
  maestro-build        │ Build and install app
  adb                  │ Android Debug Bridge wrapper

To install these aliases permanently, run:
  .\maestro-aliases.ps1 -Install

Then reload your profile:
  . `$PROFILE

For help:
  .\maestro-aliases.ps1 -Help

"@
}
