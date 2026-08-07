# Run-Tests.ps1 (S1463) - regression suite for scripts/builders/gradle-run-verdict.ps1.
#
# The defect being guarded: a dead Gradle test worker used to be reported with exit 1, the same code a
# genuine red test returns, so "nothing was proven" and "a test failed" were indistinguishable in the
# verdict. Two things get asserted, because a helper that only ever says yes proves nothing:
#   * it RECOGNISES the worker-death signature and repeats the run once,
#   * it SPARES an ordinary red test - no repeat, verdict passed straight through.
#
# Hermetic: no Gradle is invoked. Invoke-GradleRunWithRetry takes its runner as a script block exactly
# so the retry policy can be driven from crafted output here.
#
# Usage:  pwsh -NoProfile -File scripts/builders/gradle-run-verdict.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/gradle-run-verdict.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Verbatim from daemon-30316.out.log, 2026-08-07 - the shape this suite exists for.
$deathOutput = @(
    'Unexpected exception thrown.',
    "org.gradle.internal.remote.internal.MessageIOException: Could not write '/127.0.0.1:51595'.",
    'Caused by: java.io.IOException: Connection reset by peer',
    "> Task :app_v2:testStandardDebugUnitTest FAILED",
    "Process 'Gradle Test Executor 33' finished with non-zero exit value 10",
    'BUILD FAILED in 9s'
)

$genericDeathOutput = @(
    'Execution failed for task ":app_v2:testStandardDebugUnitTest".',
    '> Test process encountered an unexpected problem.'
)

$redTestOutput = @(
    'PermissionRegistryManifestParityTest > every declared permission is a registry row FAILED',
    '    java.lang.AssertionError: Declared with no registry row and no exemption:',
    '12 tests completed, 1 failed',
    'BUILD FAILED in 41s'
)

$greenOutput = @('BUILD SUCCESSFUL in 14s', '42 actionable tasks: 1 executed, 41 up-to-date')

Write-Host "gradle-run-verdict regression suite" -ForegroundColor Cyan

# --- detection -------------------------------------------------------------------------------
Assert-That 'detects the executor-exit-value death' `
(Test-GradleWorkerDeath -Lines $deathOutput) 'signature not recognised'

Assert-That 'detects the generic worker-problem death' `
(Test-GradleWorkerDeath -Lines $genericDeathOutput) 'signature not recognised'

Assert-That 'spares a red test' `
(-not (Test-GradleWorkerDeath -Lines $redTestOutput)) 'a failing test was read as a dead worker'

Assert-That 'spares a green run' `
(-not (Test-GradleWorkerDeath -Lines $greenOutput)) 'a passing run was read as a dead worker'

Assert-That 'spares empty output' `
(-not (Test-GradleWorkerDeath -Lines @())) 'empty output was read as a dead worker'

# Regression, observed on this helper's own first real run: PowerShell unrolls a single-element array
# when binding, so one blank output line arrives as the scalar '' and a Mandatory [string[]] threw
# mid-run - turning a working check into a crash. Each shape below is a real capture, not a curiosity.
foreach ($shape in @(
        @{ Name = 'single blank line'; Value = @('') },
        @{ Name = 'single real line'; Value = @('BUILD SUCCESSFUL in 3s') },
        @{ Name = 'null output'; Value = $null }
    )) {
    $threw = $false
    try { $null = Test-GradleWorkerDeath -Lines $shape.Value } catch { $threw = $true }
    Assert-That "binds $($shape.Name) without throwing" (-not $threw) 'parameter binding threw'
}

# The same shapes must survive the retry wrapper, which is where the crash actually surfaced.
$result = Invoke-GradleRunWithRetry -RunOnce {
    param([int]$Attempt)
    return [pscustomobject]@{ ExitCode = 1; Lines = @('') }
} -MaxAttempts 2

Assert-That 'retry wrapper survives single-blank-line output' `
($result.Attempts -eq 1 -and $result.ExitCode -eq 1 -and -not $result.WorkerDeath) `
"attempts=$($result.Attempts) exit=$($result.ExitCode)"

# --- retry policy ----------------------------------------------------------------------------
# A death that clears on the repeat is the common case: the run must end green, having run twice.
$attempts = 0
$result = Invoke-GradleRunWithRetry -RunOnce {
    param([int]$Attempt)
    $script:attempts = $Attempt
    if ($Attempt -eq 1) { return [pscustomobject]@{ ExitCode = 1; Lines = $deathOutput } }
    return [pscustomobject]@{ ExitCode = 0; Lines = $greenOutput }
} -MaxAttempts 2

Assert-That 'transient death retries and succeeds' `
($result.ExitCode -eq 0 -and $result.Attempts -eq 2 -and -not $result.WorkerDeath) `
"exit=$($result.ExitCode) attempts=$($result.Attempts) death=$($result.WorkerDeath)"

# A death on both attempts must stop at two - a third would only spend more time saying the same.
$result = Invoke-GradleRunWithRetry -RunOnce {
    param([int]$Attempt)
    return [pscustomobject]@{ ExitCode = 1; Lines = $deathOutput }
} -MaxAttempts 2

Assert-That 'persistent death stops at two attempts and reports no verdict' `
($result.Attempts -eq 2 -and $result.WorkerDeath) `
"attempts=$($result.Attempts) death=$($result.WorkerDeath)"

# A red test must NOT be retried - repeating it only doubles the wait for the same answer.
$result = Invoke-GradleRunWithRetry -RunOnce {
    param([int]$Attempt)
    return [pscustomobject]@{ ExitCode = 1; Lines = $redTestOutput }
} -MaxAttempts 2

Assert-That 'red test runs once and keeps its own verdict' `
($result.Attempts -eq 1 -and $result.ExitCode -eq 1 -and -not $result.WorkerDeath) `
"attempts=$($result.Attempts) exit=$($result.ExitCode) death=$($result.WorkerDeath)"

# A green run must not be retried either.
$result = Invoke-GradleRunWithRetry -RunOnce {
    param([int]$Attempt)
    return [pscustomobject]@{ ExitCode = 0; Lines = $greenOutput }
} -MaxAttempts 2

Assert-That 'green run runs once' `
($result.Attempts -eq 1 -and $result.ExitCode -eq 0) `
"attempts=$($result.Attempts) exit=$($result.ExitCode)"

# --- JUnit report outcome (S1464) --------------------------------------------------------------
# Existence is not execution. Each fixture below is a report a gate might be handed; only one of them
# licenses the claim "the thing under test is wrong".
$sandbox = Join-Path $env:TEMP "gradle-run-verdict.tests.$PID"
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null

function New-Report([string]$name, [string]$body) {
    $p = Join-Path $sandbox $name
    [System.IO.File]::WriteAllText($p, $body)
    return $p
}

try {
    # Verbatim shape from S1464 section 1: a dead worker leaves a fresh, well-formed, skipped report.
    $skippedReport = New-Report 'skipped.xml' @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="SettingsManifestExportTest" tests="1" skipped="1" failures="0" errors="0">
  <testcase name="committed manifest is fresh"><skipped/></testcase>
</testsuite>
'@
    $emptyReport = New-Report 'empty.xml' @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="SettingsManifestExportTest" tests="0" skipped="0" failures="0" errors="0"/>
'@
    $failedReport = New-Report 'failed.xml' @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="SettingsManifestExportTest" tests="1" skipped="0" failures="1" errors="0">
  <testcase name="committed manifest is fresh"><failure>manifest differs</failure></testcase>
</testsuite>
'@
    $greenReport = New-Report 'green.xml' @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="SettingsManifestExportTest" tests="1" skipped="0" failures="0" errors="0">
  <testcase name="committed manifest is fresh"/>
</testsuite>
'@

    $o = Get-JUnitSuiteOutcome -ReportPath $skippedReport
    Assert-That 'skipped report does not count as executed' `
    (-not $o.Executed -and $o.Reason -match 'skipped') "executed=$($o.Executed) reason=$($o.Reason)"

    $o = Get-JUnitSuiteOutcome -ReportPath $emptyReport
    Assert-That 'empty report does not count as executed' `
    (-not $o.Executed -and $o.Reason -match 'no tests') "executed=$($o.Executed) reason=$($o.Reason)"

    $o = Get-JUnitSuiteOutcome -ReportPath $failedReport
    Assert-That 'genuine failure counts as executed and failed' `
    ($o.Executed -and $o.Failed) "executed=$($o.Executed) failed=$($o.Failed)"

    $o = Get-JUnitSuiteOutcome -ReportPath $greenReport
    Assert-That 'passing report counts as executed, not failed' `
    ($o.Executed -and -not $o.Failed) "executed=$($o.Executed) failed=$($o.Failed)"

    $o = Get-JUnitSuiteOutcome -ReportPath (Join-Path $sandbox 'does-not-exist.xml')
    Assert-That 'missing report does not count as executed' `
    (-not $o.Executed -and $o.Reason -eq 'report missing') "executed=$($o.Executed) reason=$($o.Reason)"
} finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "passed=$script:pass failed=$script:fail" -ForegroundColor $(if ($script:fail) { 'Red' } else { 'Green' })
if ($script:fail -gt 0) { exit 1 }
exit 0
