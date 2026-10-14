#requires -Version 7.0
<#
.SYNOPSIS
Contract tests for assert-dynamic-audit-coverage.ps1 (S4166).

.DESCRIPTION
Case 1 runs the validator's -SelfTest, which builds every synthetic fixture itself and expects 0. Case 2
runs one live -Validate against this ticket's audit data and expects 1: the first-pass matrix is open by
construction, so a 0 would be a false "all covered" and a 2 means the data or the chain is unreadable.
PLAN/ is gitignored, so on a checkout without the audit data case 2 is reported as skipped, not failed.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed (a skipped live case does not fail the suite)
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-dynamic-audit-coverage.ps1')).Path
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $Gate))
$auditManifest = Join-Path $repoRoot 'PLAN/S4166_deep-dynamic-audit/audit/manifest.json'
$passed = 0
$failed = 0

function Invoke-Case {
    param([string]$Name, [string[]]$Arguments, [int]$Expect)
    $output = & pwsh -NoProfile -File $Gate @Arguments 2>&1 | Out-String
    $actual = $LASTEXITCODE
    if ($actual -eq $Expect) {
        $script:passed++
        Write-Host "PASS  $Name - expected: $Expect | actual: $actual"
    } else {
        $script:failed++
        Write-Host "FAIL  $Name - expected: $Expect | actual: $actual"
        Write-Host (($output -split "`n" | Select-Object -Last 40 | ForEach-Object { "      | $_" }) -join "`n")
    }
}

Invoke-Case 'self-test separates 0, 1 and 2 on synthetic fixtures' @('-SelfTest') 0

if (Test-Path -LiteralPath $auditManifest -PathType Leaf) {
    Invoke-Case 'live validate of the first-pass audit data reports open coverage' @('-Validate', '-Quiet') 1
} else {
    Write-Host 'SKIP  live validate - this checkout holds no S4166 audit data (PLAN/ is gitignored)'
}

Write-Host ("assert-dynamic-audit-coverage.tests: {0} passed, {1} failed" -f $passed, $failed)
if ($failed -gt 0) { exit 1 }
exit 0
