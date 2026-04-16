# Run Maestro tests
# Usage:
#   .\scripts\utils\run-maestro-smoke.ps1           - Run smoke tests
#   .\scripts\utils\run-maestro-smoke.ps1 -Suite critical
#   .\scripts\utils\run-maestro-smoke.ps1 -Suite all

param(
    [ValidateSet("smoke", "critical", "all")]
    [string]$Suite = "smoke",
    [switch]$Stress
)

Write-Host "Running Maestro tests..." -ForegroundColor Cyan

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptsPath = Split-Path -Parent $scriptPath
$projectRoot = Split-Path -Parent $scriptsPath

# Stress suite was removed from default test inventory.
if ($Stress) {
    Write-Host "❌ Stress suite has been removed from active Maestro tests." -ForegroundColor Red
    Write-Host "Use -Suite smoke, -Suite critical, or -Suite all." -ForegroundColor Yellow
    exit 1
}

Push-Location $projectRoot
try {
    & .\maestro\run-tests.ps1 $Suite -DebugMode
}
finally {
    Pop-Location
}
