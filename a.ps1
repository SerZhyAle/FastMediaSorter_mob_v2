#!/usr/bin/env pwsh
<#
.SYNOPSIS
    FastMediaSorter project scripts launcher
.DESCRIPTION
    Quick alias launcher for common project scripts
.PARAMETER Command
    Script command to execute:
    r   - Build AAB Release
    dc  - Build Debug Clean
    d   - Build Debug
    c   - Commit & Push
    ch  - Check Typo/Lint
    s   - Setup Test Media
    bp  - Build and Push All
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
    'r'  = 'scripts\builders\build-aab-release.ps1'
    'dc' = 'scripts\builders\build-debug-clean.PS1'
    'd'  = 'scripts\builders\build-debug.PS1'
    'c'  = 'scripts\utils\commit-push.ps1'
    'ch' = 'scripts\utils\check-typo-lint.ps1'
    's'  = 'scripts\utils\setup_test_media.ps1'
    'bp' = 'scripts\builders\build-and-push-all.ps1'
}

# Validate command
if (-not $scripts.ContainsKey($Command)) {
    Write-Host "❌ Unknown command: $Command" -ForegroundColor Red
    Write-Host ""
    Write-Host "Available commands:" -ForegroundColor Yellow
    Write-Host "  r   - Build AAB Release" -ForegroundColor Cyan
    Write-Host "  dc  - Build Debug Clean" -ForegroundColor Cyan
    Write-Host "  d   - Build Debug" -ForegroundColor Cyan
    Write-Host "  c   - Commit & Push" -ForegroundColor Cyan
    Write-Host "  ch  - Check Typo/Lint" -ForegroundColor Cyan
    Write-Host "  s   - Setup Test Media" -ForegroundColor Cyan
    Write-Host "  bp  - Build and Push All" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\a.ps1 <command>" -ForegroundColor Gray
    Write-Host "Example: .\a.ps1 d" -ForegroundColor Gray
    exit 1
}

# Get script path
$scriptPath = Join-Path $ProjectRoot $scripts[$Command]

# Verify script exists
if (-not (Test-Path $scriptPath)) {
    Write-Host "❌ Script not found: $scriptPath" -ForegroundColor Red
    exit 1
}

# Execute script
Write-Host "🚀 Executing: $($scripts[$Command])" -ForegroundColor Green
Write-Host ""

& $scriptPath

# Return exit code from executed script
exit $LASTEXITCODE
