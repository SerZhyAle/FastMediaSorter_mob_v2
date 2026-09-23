#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-no-test-retry.ps1.

.DESCRIPTION
    The gate refuses a mechanism somebody has already decided to add, so its refusals are
    demonstrated rather than asserted. Every case builds a fixture tree with the real test source
    layout and runs the gate against it with -RepoRoot, so the whole verdict path executes,
    including the refusal text and the cure line a developer reads at 2 a.m.

    The last two cases are the ones that keep the gate honest: a test whose NAME carries Retry
    because it tests the transport's own retry policy must pass, and a missing tree must say it
    could not verify rather than report a clean tree.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gate = Join-Path $repoRoot 'scripts/quality/assert-no-test-retry.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("no-test-retry-fixture-{0}" -f $PID)
$testRelative = 'app_v2/src/test/java/com/sza/fastmediasorter/sample'
$testDir = Join-Path $fixtureRoot $testRelative

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $testDir -Force | Out-Null
}

function Set-TestSource([string]$FileName, [string[]]$Body) {
    Set-Content -LiteralPath (Join-Path $testDir $FileName) -Value $Body -Encoding UTF8
}

function Set-BuildScript([string[]]$Body) {
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'app_v2/build.gradle.kts') -Value $Body -Encoding UTF8
}

function Invoke-NoRetry {
    param([switch]$NoRoot)
    $root = if ($NoRoot) { Join-Path $fixtureRoot 'nowhere' } else { $fixtureRoot }
    $output = & $pwshExe -NoProfile -NonInteractive -File $gate -Gate -RepoRoot $root 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

try {
    Test-Case 'a retry annotation on a test is refused, naming the file and the detector' {
        Reset-Fixture
        Set-TestSource 'UploadQueueTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            'class UploadQueueTest {',
            '    @Retry(3)',
            '    @Test',
            '    fun drainsUnderLoad() { }',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 1 $r.ExitCode 'retry annotation verdict'
        if ($r.Output -notmatch 'UploadQueueTest') { throw "the refusal did not name the file - output: $($r.Output)" }
        if ($r.Output -notmatch 'retry-annotation') { throw "the refusal did not name the detector - output: $($r.Output)" }
        if ($r.Output -notmatch 'test-flaky-quarantine\.jsonl') { throw "the refusal did not name the ledger to use instead - output: $($r.Output)" }
    }

    Test-Case 'the same test passes once the annotation is gone' {
        Reset-Fixture
        Set-TestSource 'UploadQueueTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            'class UploadQueueTest {',
            '    @Test',
            '    fun drainsUnderLoad() { }',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 0 $r.ExitCode "ordinary test verdict - output: $($r.Output)"
    }

    Test-Case 'an ordinary flaky-looking test without a retry mechanism passes' {
        Reset-Fixture
        Set-TestSource 'ScanTimingTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            'class ScanTimingTest {',
            '    @Test',
            '    fun finishesWithinTheWindow() {',
            '        Thread.sleep(50)',
            '        assertTrue(elapsedMs() < 500)',
            '    }',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 0 $r.ExitCode "flaky-looking test verdict - output: $($r.Output)"
    }

    Test-Case 'a retry TestRule declaration is refused' {
        Reset-Fixture
        Set-TestSource 'FlakyScreenTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            'class FlakyScreenTest {',
            '    @get:Rule',
            '    val retry = RetryRule(3)',
            '',
            '    @Test',
            '    fun opens() { }',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 1 $r.ExitCode 'retry rule verdict'
        if ($r.Output -notmatch 'retry-rule') { throw "the refusal did not name the detector - output: $($r.Output)" }
    }

    Test-Case 'a retry runner is refused' {
        Reset-Fixture
        Set-TestSource 'RunnerTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            '@RunWith(RetryingAndroidJUnit4Runner::class)',
            'class RunnerTest {',
            '    @Test',
            '    fun opens() { }',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 1 $r.ExitCode 'retry runner verdict'
        if ($r.Output -notmatch 'retry-runner') { throw "the refusal did not name the detector - output: $($r.Output)" }
    }

    Test-Case 'the Gradle test-retry plugin in a build script is refused' {
        Reset-Fixture
        Set-TestSource 'UploadQueueTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            'class UploadQueueTest {',
            '    @Test',
            '    fun drainsUnderLoad() { }',
            '}'
        )
        Set-BuildScript @(
            'plugins {',
            '    id("org.gradle.test-retry") version "1.5.9"',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 1 $r.ExitCode 'gradle retry plugin verdict'
        if ($r.Output -notmatch 'gradle-test-retry') { throw "the refusal did not name the detector - output: $($r.Output)" }
        if ($r.Output -notmatch 'build\.gradle\.kts') { throw "the refusal did not name the build script - output: $($r.Output)" }
    }

    Test-Case 'a test of the product own retry policy is not a subject' {
        Reset-Fixture
        Set-TestSource 'IoContractErrorRetryTest.kt' @(
            'package com.sza.fastmediasorter.sample',
            '',
            'class IoContractErrorRetryTest {',
            '    @Test',
            '    fun retriesTheTransferOnATransientError() {',
            '        val policy = RetryPolicy(maxAttempts = 3)',
            '        assertEquals(3, policy.maxAttempts)',
            '    }',
            '}'
        )
        $r = Invoke-NoRetry
        Assert-Equal 0 $r.ExitCode "product retry-policy test verdict - output: $($r.Output)"
    }

    Test-Case 'a tree that does not exist cannot verify rather than passing' {
        Reset-Fixture
        $r = Invoke-NoRetry -NoRoot
        Assert-Equal 2 $r.ExitCode 'missing tree verdict'
        if ($r.Output -notmatch 'cannot verify') { throw "the run did not say it could not verify - output: $($r.Output)" }
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertNoTestRetry.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
