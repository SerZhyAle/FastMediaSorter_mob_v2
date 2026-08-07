<#
.SYNOPSIS
    Tells a Gradle run that produced NO verdict apart from one that produced a red verdict.

.DESCRIPTION
    S1463: when the Gradle test worker JVM dies, the task fails with exit 1 - the same code a genuine
    test failure returns. The reader of that verdict goes looking for a red test that does not exist.
    Three separate scripts have now shipped this substitution (S1462 in the lock queue, S1464 in the
    settings gate, this one in the fast check), so the distinction is worth a named helper rather than
    an inline regex.

    A worker death is host-load dependent and clears on its own, which is why the retry lives here
    too: one repeat costs seconds on a warm daemon and converts most non-verdicts into real results.
    Retrying a run that failed for any OTHER reason would only double the wait for a red test, so the
    retry is bound to the death signature and to nothing else.

    Dot-source this file; it defines functions and never exits.
#>

# Deliberately no Set-StrictMode here: this file is dot-sourced, so it would impose strict mode on
# every consumer's whole scope - a side effect none of them asked for.

# Gradle prints one of these whenever the worker process itself failed rather than a test inside it.
# 'unexpected problem' is the generic wrapper; the executor line names the process and its exit code.
$script:GradleWorkerDeathPatterns = @(
    "Process 'Gradle Test Executor \d+' finished with non-zero exit value \d+",
    'Test process encountered an unexpected problem'
)

function Test-GradleWorkerDeath {
    <#
    .SYNOPSIS
        True when the captured Gradle output shows the worker process dying.
    #>
    # Every emptiness attribute is deliberate. PowerShell unrolls a single-element array during
    # parameter binding, so captured output of one blank line arrives as the scalar '' and a plain
    # Mandatory [string[]] rejects it - which is how this helper threw on its own first real run.
    param(
        [AllowNull()][AllowEmptyString()][AllowEmptyCollection()][string[]]$Lines
    )

    foreach ($line in $Lines) {
        foreach ($pattern in $script:GradleWorkerDeathPatterns) {
            if ($line -match $pattern) { return $true }
        }
    }
    return $false
}

function Get-JUnitSuiteOutcome {
    <#
    .SYNOPSIS
        Says whether a JUnit XML report proves its assertions actually ran, and whether they failed.

    .DESCRIPTION
        S1464: a gate used to accept "the report file exists and is fresh" as proof that a test ran,
        then blamed the thing under test when the task went red. A worker that dies after the test
        body writes exactly such a report - fresh, well-formed, and carrying skipped="1". Existence is
        not execution, so the two questions are answered separately here: did it run, and did it fail.

    .OUTPUTS
        Executed - at least one test in the suite actually ran (tests minus skipped >= 1).
        Failed   - at least one failure or error was recorded.
        Reason   - why Executed is false, for a message that names what was observed.
    #>
    param(
        [Parameter(Mandatory)][string]$ReportPath
    )

    $outcome = [pscustomobject]@{
        Executed = $false
        Failed   = $false
        Tests    = 0
        Skipped  = 0
        Failures = 0
        Errors   = 0
        Reason   = 'report missing'
    }

    if (-not (Test-Path $ReportPath)) { return $outcome }

    try {
        $xml = [xml](Get-Content -LiteralPath $ReportPath -Raw)
    } catch {
        $outcome.Reason = 'report unreadable'
        return $outcome
    }

    $suite = $xml.SelectSingleNode('//testsuite')
    if (-not $suite) {
        $outcome.Reason = 'report has no testsuite element'
        return $outcome
    }

    # GetAttribute returns '' for an absent attribute; the typed accessor would throw under StrictMode.
    $toInt = { param($name) $raw = $suite.GetAttribute($name); if ($raw) { [int]$raw } else { 0 } }
    $outcome.Tests = & $toInt 'tests'
    $outcome.Skipped = & $toInt 'skipped'
    $outcome.Failures = & $toInt 'failures'
    $outcome.Errors = & $toInt 'errors'

    if ($outcome.Tests -lt 1) {
        $outcome.Reason = 'report records no tests'
        return $outcome
    }
    if (($outcome.Tests - $outcome.Skipped) -lt 1) {
        $outcome.Reason = "every test in the report is marked skipped ($($outcome.Skipped) of $($outcome.Tests))"
        return $outcome
    }

    $outcome.Executed = $true
    $outcome.Failed = ($outcome.Failures + $outcome.Errors) -ge 1
    $outcome.Reason = 'executed'
    return $outcome
}

function Invoke-GradleRunWithRetry {
    <#
    .SYNOPSIS
        Runs a Gradle invocation, repeating it only when the worker died rather than a test failing.

    .PARAMETER RunOnce
        Script block taking the attempt number and returning an object with ExitCode and Lines. Kept
        as a parameter so the regression suite can drive the retry policy without invoking Gradle.

    .PARAMETER MaxAttempts
        Total attempts including the first. 2 by default - a second death is evidence of something
        other than transient host load, and a third attempt would only spend more time saying so.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$RunOnce,
        [int]$MaxAttempts = 2
    )

    $attempt = 0
    $result = $null
    $lines = @()

    while ($attempt -lt $MaxAttempts) {
        $attempt++
        $result = & $RunOnce $attempt
        # @() before every use: the runner's Lines may be empty, one element, or a few thousand, and
        # only the wrapped form behaves the same in all three.
        $lines = @($result.Lines)

        if ($result.ExitCode -eq 0) { break }
        if (-not (Test-GradleWorkerDeath -Lines $lines)) { break }
        if ($attempt -ge $MaxAttempts) { break }

        Write-Host ("Gradle test worker died - this run produced no verdict. " +
            "Retrying once (attempt $($attempt + 1) of $MaxAttempts).") -ForegroundColor Yellow
    }

    $died = ($result.ExitCode -ne 0) -and (Test-GradleWorkerDeath -Lines $lines)

    return [pscustomobject]@{
        ExitCode    = $result.ExitCode
        Lines       = $lines
        Attempts    = $attempt
        WorkerDeath = $died
    }
}
