<#
.SYNOPSIS
    Runs the custom lint detectors' own unit suite: :lint-rules:test.

.DESCRIPTION
    S1195: the project's five custom detectors are what `lintStandardDebug` enforces, but their
    test suite was reachable from no script, no a.ps1 target and no CI job - configured strictly,
    never run. This is the fast red/green loop that makes a detector change verifiable without
    paying the ~7 minute cost of a full lint run.

    Rule 23: acquires temp/BUILD.LOCK for the whole gradle invocation and releases it on both the
    success and the failure path.

.PARAMETER Tests
    Optional gradle --tests filter, e.g. '*PlayerRelease*'. Passed straight through.

.PARAMETER Quiet
    Suppress UP-TO-DATE / NO-SOURCE / FROM-CACHE noise lines.

.EXITCODES
    0 - the suite ran and passed.
    1 - BUILD.LOCK held by a live build, or the gradle run failed (gradle's own exit code is
        propagated verbatim, and gradle reports 1 for a test failure).
    2 - the run reported success but produced no test-result XML, so nothing was actually
        verified. Distinguished from a real failure because the fix is different: a silently
        empty suite is a harness defect, not a detector defect.
#>

param(
    [string]$Tests,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "check-lint-rules.ps1"
try {

    $projectRoot = Resolve-Path "$PSScriptRoot\..\.."
    Set-Location $projectRoot

    $gradleArgs = New-Object System.Collections.Generic.List[string]
    $null = $gradleArgs.Add(":lint-rules:test")
    # The detector suite is pure JVM - no Android variant, no Chaquopy, so the configuration
    # cache applies cleanly and keeps the loop fast enough to run after every edit.
    $null = $gradleArgs.Add("--configuration-cache")

    if ($Tests) {
        $null = $gradleArgs.Add("--tests")
        $null = $gradleArgs.Add($Tests)
    }

    Write-Host "Lint-rules detector test suite.." -ForegroundColor Cyan
    if ($Tests) {
        Write-Host "Tests filter: $Tests" -ForegroundColor Yellow
    }
    Write-Host "Command: .\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

    & "$projectRoot\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        if ($Quiet -and ($line -match " UP-TO-DATE$" -or $line -match " NO-SOURCE$" -or $line -match " FROM-CACHE$")) {
            return
        }
        Write-Host $line
    }

    $gradleExit = $LASTEXITCODE

    if ($gradleExit -ne 0) {
        Write-Host "`nLint-rules tests FAILED (gradle exit $gradleExit)." -ForegroundColor Red
        Write-Host "Report: lint-rules/build/reports/tests/test/index.html" -ForegroundColor Gray
        exit $gradleExit
    }

    # A green gradle verdict over an empty suite proves nothing - the whole reason this script
    # exists is that "the tests pass" was an unverifiable claim. Count the cases and say so.
    $resultsDir = Join-Path $projectRoot "lint-rules\build\test-results\test"
    $resultFiles = @(Get-ChildItem -LiteralPath $resultsDir -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
    if ($resultFiles.Count -eq 0) {
        Write-Error "No test-result XML under $resultsDir - the suite reported success without running." -ErrorAction Continue
        exit 2
    }

    $total = 0
    $failed = 0
    $skipped = 0
    foreach ($file in $resultFiles) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $total += [int]$xml.testsuite.tests
        $failed += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
        $skipped += [int]$xml.testsuite.skipped
    }

    Write-Host "`nLint-rules tests passed: $($total - $failed - $skipped)/$total (skipped $skipped)." -ForegroundColor Green

}
finally {
    Exit-AgentLock -Name Build
}
