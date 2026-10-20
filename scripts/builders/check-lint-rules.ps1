<#
.SYNOPSIS
    Runs the custom lint detectors' own unit suite: :lint-rules:test.

.DESCRIPTION
    S1195: the project's custom detectors are what `lintStandardDebug` enforces, but their
    test suite was reachable from no script, no a.ps1 target and no CI job - configured strictly,
    never run. This verifies detector changes independently of a full application lint run.

    Rule 23: acquires the build lock for the whole gradle invocation and releases it on both the
    success and the failure path.

.PARAMETER Tests
    Optional gradle --tests filter, e.g. '*PlayerRelease*'. Passed straight through.

.PARAMETER Quiet
    Suppress UP-TO-DATE / NO-SOURCE / FROM-CACHE noise lines.

.EXITCODES
    0 - the suite ran and passed.
    1 - Gradle reported a build/test failure, or a structured report records failures/errors.
    2 - missing/unreadable reports, zero executed tests, skipped declared tests, a worker death,
        or an unexpected Gradle exit code prevented verification.
    4 - queued on the build lock; nothing inspected.
#>

param(
    [string]$Tests,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$verdict = [pscustomobject]@{ Code = 2; Word = 'COULD NOT VERIFY'; Run = 0; Skipped = 0 }
trap {
    Write-Host "  Lint-rules harness error: $($_.Exception.Message)"
    if ($verdict.Code -ne 1) { $verdict.Code = 2; $verdict.Word = 'COULD NOT VERIFY' }
    Write-Host "check-lint-rules: $($verdict.Word) (run=$($verdict.Run) skipped=$($verdict.Skipped))"
    exit $verdict.Code
}

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\quality\lib\check-subject.ps1"
. "$PSScriptRoot\lib\lint-rules-test-outcome.ps1"
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
    Write-CheckSubject -Axes ([ordered]@{ module = "lint-rules"; mode = "Unit"; tests = $Tests })
    if ($Tests) {
        Write-Host "Tests filter: $Tests" -ForegroundColor Yellow
    }
    Write-Host "Command: .\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

    $startedUtc = [datetime]::UtcNow
    $gradleOutput = [System.Collections.Generic.List[string]]::new()
    & "$projectRoot\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        $gradleOutput.Add($line)
        if ($Quiet -and ($line -match " UP-TO-DATE$" -or $line -match " NO-SOURCE$" -or $line -match " FROM-CACHE$")) {
            return
        }
        Write-Host $line
    }

    $gradleExit = $LASTEXITCODE

    $resultsDir = Join-Path $projectRoot "lint-rules\build\test-results\test"
    $resultFiles = @(Get-ChildItem -LiteralPath $resultsDir -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
    $verdict = Get-LintRulesSuiteVerdict -ReportPaths @($resultFiles | ForEach-Object FullName) `
        -GradleExitCode $gradleExit -GradleOutput @($gradleOutput) -StartedUtc $startedUtc
    foreach ($reason in $verdict.Reasons) { Write-Host "  $reason" }
    if ($verdict.Code -ne 0) { Write-Host 'Report: lint-rules/build/reports/tests/test/index.html' }
}
catch {
    Write-Host "  Lint-rules verification could not complete: $($_.Exception.Message)"
    if ($verdict.Code -ne 1) { $verdict.Code = 2; $verdict.Word = 'COULD NOT VERIFY' }
}
finally {
    try { Exit-AgentLock -Name Build }
    catch {
        Write-Host "  Build lock release failed: $($_.Exception.Message)"
        if ($verdict.Code -ne 1) { $verdict.Code = 2; $verdict.Word = 'COULD NOT VERIFY' }
    }
}
Write-Host "check-lint-rules: $($verdict.Word) (run=$($verdict.Run) skipped=$($verdict.Skipped))"
exit $verdict.Code
