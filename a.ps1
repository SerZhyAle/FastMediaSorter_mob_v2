#!/usr/bin/env pwsh
<#
.SYNOPSIS
    FastMediaSorter project scripts launcher
.DESCRIPTION
    Quick alias launcher for common project scripts
.PARAMETER Command
    Script command to execute:
    r    - Build AAB Release
    vr   - Build VR Release APK
    vrd  - Build VR Debug APK
    ivr  - Install VR Release APK on device (no launch — use Quest Library)
    ivrd - Install VR Debug APK on device (no launch — use Quest Library)
    dc   - Build Debug Clean
    d    - Build Debug
    db   - Build Debug (without zip)
    cd   - Clean + Debug + Zip
    cdb  - Clean + Debug (without zip)
    cls  - Clean Gradle caches
    c    - Commit & Push
    ch   - Check Typo/Lint
    s    - Setup Test Media
    bp   - Build and Push All
    ss   - Show unresolved specs (alias: sca-specs)
.EXAMPLE
    .\a.ps1 d
    .\a d
#>

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Command
)

$ErrorActionPreference = "Stop"

# Get project root directory
$ProjectRoot = $PSScriptRoot

# Script mapping
$scripts = @{
    'r'    = @{ Path = 'scripts\builders\build-aab-release.ps1'; Args = @() }
    'vr'   = @{ Path = 'scripts\builders\build-vr-release.ps1'; Args = @() }
    'vrd'  = @{ Path = 'scripts\builders\build-vr-debug.ps1'; Args = @() }
    'ivr'  = @{ Path = 'scripts\builders\install-vr-release-to-device.ps1'; Args = @() }
    'ivrd' = @{ Path = 'scripts\builders\install-vr-debug-to-device.ps1'; Args = @() }
    'dc'  = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @() }
    'd'   = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @() }
    'db'  = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @('-SkipZip') }
    'cd'  = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @() }
    'cdb' = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @('-SkipZip') }
    'cls' = @{ Path = 'scripts\builders\clean-gradle-caches.ps1'; Args = @() }
    'c'   = @{ Path = 'scripts\utils\commit-push.ps1'; Args = @() }
    'ch'  = @{ Path = 'scripts\utils\check-typo-lint.ps1'; Args = @() }
    's'   = @{ Path = 'scripts\utils\setup_test_media.ps1'; Args = @() }
    'b'         = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @() }
    'bp'        = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @() }
    'ss'        = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @() }
    'sca-specs' = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @() }
}

# Validate command
if (-not $scripts.ContainsKey($Command)) {
    Write-Host "❌ Unknown command: $Command" -ForegroundColor Red
    Write-Host ""
    Write-Host "Available commands:" -ForegroundColor Yellow
    Write-Host "  r    - Build AAB Release" -ForegroundColor Cyan
    Write-Host "  vr   - Build VR Release APK" -ForegroundColor Cyan
    Write-Host "  vrd  - Build VR Debug APK" -ForegroundColor Cyan
    Write-Host "  ivr  - Install VR Release APK on device (NO launch — use Quest Library)" -ForegroundColor Cyan
    Write-Host "  ivrd - Install VR Debug APK on device (NO launch — use Quest Library)" -ForegroundColor Cyan
    Write-Host "  dc   - Build Debug Clean" -ForegroundColor Cyan
    Write-Host "  d    - Build Debug" -ForegroundColor Cyan
    Write-Host "  db   - Build Debug without zip" -ForegroundColor Cyan
    Write-Host "  cd   - Clean + Debug + zip" -ForegroundColor Cyan
    Write-Host "  cdb  - Clean + Debug without zip" -ForegroundColor Cyan
    Write-Host "  cls  - Clean Gradle caches" -ForegroundColor Cyan
    Write-Host "  c    - Commit & Push" -ForegroundColor Cyan
    Write-Host "  ch   - Check Typo/Lint" -ForegroundColor Cyan
    Write-Host "  s    - Setup Test Media" -ForegroundColor Cyan
    Write-Host "  b    - Build and Push All (same as bp)" -ForegroundColor Cyan
    Write-Host "  bp   - Build and Push All" -ForegroundColor Cyan
    Write-Host "  ss   - Show unresolved specs (alias: sca-specs)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\a.ps1 <command>" -ForegroundColor Gray
    Write-Host "Example: .\a.ps1 d" -ForegroundColor Gray
    exit 1
}

# Get script path
$scriptEntry = $scripts[$Command]
$scriptPath = Join-Path $ProjectRoot $scriptEntry.Path
$scriptArgs = $scriptEntry.Args

# Verify script exists
if (-not (Test-Path $scriptPath)) {
    Write-Host "❌ Script not found: $scriptPath" -ForegroundColor Red
    exit 1
}

# Execute script
Write-Host "🚀 Executing: $($scriptEntry.Path) $($scriptArgs -join ' ')" -ForegroundColor Green
Write-Host ""

& $scriptPath @scriptArgs

# Return exit code from executed script
exit $LASTEXITCODE
