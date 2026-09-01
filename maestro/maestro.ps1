#!/usr/bin/env powershell
# Maestro Test Wrapper - Handles PATH and runs tests
# Usage: .\maestro.ps1 [test_name] [-Debug]

param(
    [string]$Test = "all",
    [switch]$Debug,
    [switch]$Studio
)

. "$PSScriptRoot\..\scripts\utils\project-paths.ps1"

# Setup PATH for Node.js and npm. Both are discovered rather than assumed (S2326); a machine
# without them falls through to the maestro-cli check below, which already reports the problem.
foreach ($tool in 'Node', 'Npm') {
    $dir = try { Split-Path -Parent (Get-ToolPath -Tool $tool -Quiet) } catch { $null }
    if ($dir -and $env:PATH -notlike "*$dir*") { $env:PATH = "$dir;$env:PATH" }
}

# Verify maestro-cli is available
try {
    $version = maestro-cli --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "maestro-cli failed"
    }
}
catch {
    Write-Host "ERROR: maestro-cli not found or not working"
    Write-Host "Install with: npm install -g maestro-cli"
    exit 1
}

Write-Host ""
Write-Host "FastMediaSorter v2 - Maestro E2E Smoke Tests"
Write-Host "January 26, 2026"
Write-Host ""

# Interactive mode
if ($Studio) {
    Write-Host "Opening Maestro Studio (interactive mode)..."
    Write-Host "Open browser to: http://localhost:5590"
    maestro-cli studio
    exit 0
}

# Build debug argument
$debugArgs = @()
if ($Debug) {
    $debugArgs += "--debug"
}

# Run tests
if ($Test -eq "all") {
    Write-Host "Running ALL smoke tests (3-4 minutes)..."
    Write-Host ""
    
    maestro-cli test smoke/ @debugArgs --timeout 60000
}
else {
    Write-Host "Running test: $Test"
    Write-Host ""
    
    maestro-cli test smoke/$Test.yaml @debugArgs --timeout 60000
}

Write-Host ""
if ($LASTEXITCODE -eq 0) {
    Write-Host "SUCCESS: Tests PASSED!"
}
else {
    Write-Host "FAILED: Tests failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}
Write-Host ""
