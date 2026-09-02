#requires -Version 7.0
<#
.SYNOPSIS
    S2126: entry point for the streams.tests suite - runs the folder's Pester specs.

.DESCRIPTION
    These four files are Pester specs (`Describe` / `It`), so neither of the repository's other two
    conventions reaches them: dot-sourcing defines blocks that nothing executes, and running a file
    with `pwsh -File` fails on an unloaded `Describe`. Either way zero tests run and the folder
    reports green, which is the failure mode this ticket exists to remove.

    A MISSING FRAMEWORK IS "COULD NOT VERIFY", NOT "PASSED". Pester is a machine-level module here,
    not vendored in the repository, so a fresh checkout may not have it. Exiting 0 in that case would
    make an unobserved folder indistinguishable from a green one. Exit 2 is the answer, and
    scripts/quality/run-script-suites.ps1 already classifies it: advisory in a per-ticket closure,
    where a developer machine may lack an optional tool, and fatal before a release, where the
    environment must be complete.

    NO `Set-StrictMode` HERE, DELIBERATELY. The installed Pester is 3.4.0, which predates strict mode
    and is not written against it; the setting is inherited by the specs it runs. Measured 2026-08-27
    on this exact folder: without it 15 tests run and 15 pass, with `-Version Latest` only 10 are
    discovered and 2 of those report failures that do not exist. A runner that changes what its own
    suite observes is the S2126 defect wearing a different hat, so this file keeps the default.
    `$ErrorActionPreference = 'Stop'` was measured in isolation and is harmless - it stays.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every Pester test passed.
      1  at least one Pester test failed.
      2  could not verify - the Pester module is not installed.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

if (-not (Get-Module -ListAvailable -Name Pester)) {
    Write-Host 'streams.tests: CANNOT VERIFY - the Pester module is not installed (Install-Module Pester).' -ForegroundColor Yellow
    exit 2
}

$result = Invoke-Pester -Path $PSScriptRoot -PassThru -Quiet

# Pester 3 and 5 disagree on almost every property name, but not on these three counters.
$total = [int]$result.TotalCount
$failedCount = [int]$result.FailedCount
$passedCount = [int]$result.PassedCount

Write-Host ("streams.tests: {0} test(s) | passed: {1} | failed: {2}" -f $total, $passedCount, $failedCount) -ForegroundColor Cyan

if ($failedCount -gt 0) { exit 1 }
exit 0
