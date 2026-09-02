#requires -Version 7.0
<#
.SYNOPSIS
    S2197 regression test suite: verify that devtest and device utility scripts configure UTF-8 output encoding.

.DESCRIPTION
    Hermetic test: scans scripts in scripts/devtest/ and scripts/utils/ to ensure
    `[Console]::OutputEncoding` is set so output redirected from pwsh in non-interactive/subshell
    environments (such as bash tool or CI) is emitted as UTF-8 rather than OEM codepage (cp866).

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/devtest-encoding.tests/Run-Tests.ps1

.EXIT CODES
    0 - all audited devtest/utils scripts contain OutputEncoding configuration.
    1 - at least one audited script is missing OutputEncoding configuration.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)

$targetScripts = @(
    'scripts/devtest/adb.ps1',
    'scripts/devtest/device-ready.ps1',
    'scripts/devtest/device-lease.ps1',
    'scripts/devtest/find-recent-screenshots.ps1',
    'scripts/devtest/maestro-run.ps1',
    'scripts/devtest/prerelease-configure.ps1',
    'scripts/devtest/prerelease-log-audit.ps1',
    'scripts/devtest/prerelease-measure.ps1',
    'scripts/devtest/prerelease-prepare.ps1',
    'scripts/devtest/prerelease-verdict.ps1',
    'scripts/devtest/streams-perf-seed.ps1',
    'scripts/devtest/wear-prerelease-prepare.ps1',
    'scripts/devtest/wear-prerelease-walk.ps1',
    'scripts/devtest/camera-wysiwyg-selftest.ps1',
    'scripts/devtest/camera-wysiwyg-sweep.ps1',
    'scripts/devtest/lib/find-adb.ps1',
    'scripts/utils/extract-device-logs.ps1'
)

$passed = 0
$failed = 0

foreach ($relPath in $targetScripts) {
    $fullPath = Join-Path $repoRoot ($relPath -replace '/', '\')
    if (-not (Test-Path -LiteralPath $fullPath)) {
        Write-Host "FAIL | $relPath (file not found)"
        $failed++
        continue
    }
    $content = Get-Content -LiteralPath $fullPath -Raw
    if ($content -match '\[Console\]::OutputEncoding\s*=') {
        Write-Host "PASS | $relPath specifies OutputEncoding"
        $passed++
    } else {
        Write-Host "FAIL | $relPath missing [Console]::OutputEncoding assignment"
        $failed++
    }
}

Write-Host ""
Write-Host "passed: $passed | failed: $failed"

if ($failed -gt 0) {
    exit 1
}
exit 0
