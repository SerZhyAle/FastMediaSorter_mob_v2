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

# S2127: K2's MISSING_DEPENDENCY_CLASS. The wording is the stable part of the diagnostic - the class
# name in front of it varies, and so does whether the second line says the class is a supertype.
# S2219: KSP worker ClassCastException (KspAAWorkerAction) under incremental state.
$script:KotlinStaleIncrementalPatterns = @(
    'Check your module classpath for missing or conflicting dependencies',
    'com\.google\.devtools\.ksp\.gradle\.KspAAWorkerAction',
    '\[ksp\] java\.lang\.ClassCastException'
)

function Test-KotlinStaleIncrementalState {
    <#
    .SYNOPSIS
        True when a Kotlin compile failed on a class the incremental state lost rather than a real one.

    .DESCRIPTION
        S2127: a class whose source file moves between source sets keeps its FQCN and changes its
        source root. The incremental output then holds no .class for it while the already-compiled
        binaries of its consumers keep naming it in their signatures, so the next partial recompile
        reads a consumer from a stale binary, fails to find the class, and reports
        MISSING_DEPENDENCY_CLASS against the CONSUMER's file - a file in src/main that nobody edited,
        naming a classpath that is in fact correct. Measured 2026-08-27: the identical task, flavor
        and configuration passed under -Pkotlin.incremental=false and failed under incremental.

        The signature is deliberately not narrowed to a class name or a file. Any relocation produces
        it, and this repo relocates classes into paired source sets as a routine seam technique
        (S0403 did it for cast, wear and playServices in one ticket).
    #>
    # Same emptiness attributes as Test-GradleWorkerDeath, and for the same binding reason.
    param(
        [AllowNull()][AllowEmptyString()][AllowEmptyCollection()][string[]]$Lines
    )

    foreach ($line in $Lines) {
        foreach ($pattern in $script:KotlinStaleIncrementalPatterns) {
            if ($line -match $pattern) { return $true }
        }
    }
    return $false
}

function Get-KotlinStaleIncrementalRepairArgs {
    <#
    .SYNOPSIS
        The Gradle arguments that repair stale Kotlin incremental state on a retry.

    .DESCRIPTION
        The retry repeats the SAME task with incremental compilation off. That rebuilds the class
        output the stale state lost, which heals the state for every later incremental run too -
        measured 2026-08-27, `fkn` and `dq` both went green incrementally straight after one such run.

        Deleting app_v2/build/kotlin would also work and is what recover-kapt-stall.ps1 does, but it
        loses a race on Windows whenever a Kotlin or Gradle daemon still holds a handle into that
        directory: the removal is skipped, the retry fails identically, and the verdict blames the
        source. Changing one flag cannot be blocked by a file lock.
    #>
    return @('-Pkotlin.incremental=false')
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

    .PARAMETER RepairStaleIncrementalState
        S2127: optional script block run between attempts when, and only when, the failure carries the
        MISSING_DEPENDENCY_CLASS signature. Supplying it opts a caller into the repair; omitting it
        leaves the old behaviour exactly as it was. Bound to that one signature on purpose - an
        ordinary compile error must still cost one attempt, because repeating it would double the wait
        for an answer that is not going to change.

        Price of a false positive: a genuinely missing dependency pays one extra compile before it is
        reported, because the repaired retry fails the same way and its verdict is the one returned.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$RunOnce,
        [int]$MaxAttempts = 2,
        [scriptblock]$RepairStaleIncrementalState
    )

    $attempt = 0
    $result = $null
    $lines = @()
    $repaired = $false

    while ($attempt -lt $MaxAttempts) {
        $attempt++
        $result = & $RunOnce $attempt
        # @() before every use: the runner's Lines may be empty, one element, or a few thousand, and
        # only the wrapped form behaves the same in all three.
        $lines = @($result.Lines)

        if ($result.ExitCode -eq 0) { break }
        if ($attempt -ge $MaxAttempts) { break }

        if ($RepairStaleIncrementalState -and -not $repaired -and (Test-KotlinStaleIncrementalState -Lines $lines)) {
            Write-Host ("Kotlin reported a class its incremental state lost, not a source defect " +
                "(S2127). Retrying once without incremental compilation.") -ForegroundColor Yellow
            & $RepairStaleIncrementalState
            $repaired = $true
            continue
        }

        if (-not (Test-GradleWorkerDeath -Lines $lines)) { break }

        Write-Host ("Gradle test worker died - this run produced no verdict. " +
            "Retrying once (attempt $($attempt + 1) of $MaxAttempts).") -ForegroundColor Yellow
    }

    $died = ($result.ExitCode -ne 0) -and (Test-GradleWorkerDeath -Lines $lines)

    return [pscustomobject]@{
        ExitCode              = $result.ExitCode
        Lines                 = $lines
        Attempts              = $attempt
        WorkerDeath           = $died
        StaleIncrementalState = $repaired
    }
}
