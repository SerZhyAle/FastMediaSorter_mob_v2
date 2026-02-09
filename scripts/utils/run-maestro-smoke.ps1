# Run Maestro tests
# Usage:
#   .\scripts\utils\run-maestro-smoke.ps1           - Run smoke tests
#   .\scripts\utils\run-maestro-smoke.ps1 -Stress   - Run stress tests with monitoring
#   .\scripts\utils\run-maestro-smoke.ps1 -Suite critical

param(
    [ValidateSet("smoke", "critical", "stress", "all")]
    [string]$Suite = "smoke",
    [switch]$Stress
)

Write-Host "Running Maestro tests..." -ForegroundColor Cyan

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptsPath = Split-Path -Parent $scriptPath
$projectRoot = Split-Path -Parent $scriptsPath

# If -Stress switch, redirect to stress runner
if ($Stress) {
    $stressScript = Join-Path $scriptPath "run-maestro-stress.ps1"
    & $stressScript -Suite all -Monitor -Report
    exit $LASTEXITCODE
}

Push-Location $projectRoot
try {
    & .\maestro\run-tests.ps1 $Suite -DebugMode
}
finally {
    Pop-Location
}
