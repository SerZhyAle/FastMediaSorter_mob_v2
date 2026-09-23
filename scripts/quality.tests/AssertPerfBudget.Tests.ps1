#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-perf-budget.ps1.

.DESCRIPTION
    Every case writes a fixture budget file and, where the case needs one, a fixture measurement
    artifact, then runs the gate against them - so the three verdicts the phase asks for (within
    tolerance passes, a regression refuses, a missing measurement refuses) are demonstrated rather
    than asserted, together with the malformed-input cases that decide whether a green verdict ever
    means "did not look".

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
$gate = Join-Path $repoRoot 'scripts/quality/assert-perf-budget.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("perf-budget-fixture-{0}" -f $PID)
$budgetPath = Join-Path $fixtureRoot 'perf-budgets.json'
$measuredPath = Join-Path $fixtureRoot 'measured.json'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
}

function Set-Budget {
    param([int]$Budget = 4503, [int]$TolerancePercent = 10, [string]$MeasuredOn, [switch]$NoSource, [switch]$NoBudgets)

    if (-not $MeasuredOn) { $MeasuredOn = (Get-Date).ToString('yyyy-MM-dd') }

    if ($NoBudgets) {
        '{ "version": 1, "staleAfterDays": 90, "budgets": [] }' | Set-Content -LiteralPath $budgetPath -Encoding utf8NoBOM
        return
    }

    $row = [ordered]@{
        metric           = 'wear-cold-start'
        unit             = 'ms'
        budget           = $Budget
        tolerancePercent = $TolerancePercent
        direction        = 'lower-is-better'
        what             = 'fixture metric'
        # Deliberately not a real script path: assert-invoked-tracked.ps1 reads a path literal in a
        # .ps1 as an invocation site, and a fixture naming the real producer would make this suite
        # its declared consumer - a claim about the tree that only the fixture caused.
        producer         = 'the producer named in the real budget file'
    }
    if (-not $NoSource) {
        $row['source'] = [ordered]@{ ticket = 'S3368'; measuredOn = $MeasuredOn; value = $Budget; note = 'fixture' }
    }

    ([ordered]@{ version = 1; staleAfterDays = 90; budgets = @($row) } | ConvertTo-Json -Depth 6) |
        Set-Content -LiteralPath $budgetPath -Encoding utf8NoBOM
}

function Set-Measurement {
    param([double]$Value = 4503, [string]$Metric = 'wear-cold-start', [string]$Unit = 'ms')

    ([ordered]@{
            measuredOn   = (Get-Date).ToString('yyyy-MM-dd')
            measurements = @([ordered]@{ metric = $Metric; value = $Value; unit = $Unit; source = 'fixture' })
        } | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $measuredPath -Encoding utf8NoBOM
}

function Invoke-Budget {
    param([string]$MeasuredArgument)
    $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate', '-BudgetPath', $budgetPath)
    if ($PSBoundParameters.ContainsKey('MeasuredArgument')) { $argv += @('-Measured', $MeasuredArgument) }
    $output = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

try {
    Test-Case 'a measurement within tolerance passes' {
        Reset-Fixture
        Set-Budget
        Set-Measurement -Value 4800
        $r = Invoke-Budget -MeasuredArgument $measuredPath
        Assert-Equal 0 $r.ExitCode "within-tolerance verdict - output: $($r.Output)"
    }

    Test-Case 'a measurement exactly on the tolerance ceiling still passes' {
        Reset-Fixture
        Set-Budget
        Set-Measurement -Value 4953.3
        $r = Invoke-Budget -MeasuredArgument $measuredPath
        Assert-Equal 0 $r.ExitCode "ceiling verdict - output: $($r.Output)"
    }

    Test-Case 'a regression is refused with both numbers' {
        Reset-Fixture
        Set-Budget
        Set-Measurement -Value 5292
        $r = Invoke-Budget -MeasuredArgument $measuredPath
        Assert-Equal 1 $r.ExitCode 'regression verdict'
        if ($r.Output -notmatch '5292') { throw "the refusal did not name the measured value - output: $($r.Output)" }
        if ($r.Output -notmatch '4503') { throw "the refusal did not name the budget - output: $($r.Output)" }
        if ($r.Output -notmatch 'wear-cold-start') { throw "the refusal did not name the metric - output: $($r.Output)" }
    }

    Test-Case 'a budgeted metric missing from the artifact cannot verify' {
        Reset-Fixture
        Set-Budget
        Set-Measurement -Metric 'some-other-metric'
        $r = Invoke-Budget -MeasuredArgument $measuredPath
        Assert-Equal 2 $r.ExitCode 'missing measurement verdict'
        if ($r.Output -notmatch 'no record for this metric') { throw "the refusal did not say what was missing - output: $($r.Output)" }
    }

    Test-Case 'a measurement in the wrong unit cannot verify' {
        Reset-Fixture
        Set-Budget
        Set-Measurement -Value 4.5 -Unit 's'
        $r = Invoke-Budget -MeasuredArgument $measuredPath
        Assert-Equal 2 $r.ExitCode 'unit mismatch verdict'
        if ($r.Output -notmatch 'not comparable') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'a measurement artifact that does not exist cannot verify' {
        Reset-Fixture
        Set-Budget
        $r = Invoke-Budget -MeasuredArgument (Join-Path $fixtureRoot 'nowhere.json')
        Assert-Equal 2 $r.ExitCode 'missing artifact verdict'
        if ($r.Output -notmatch 'cannot verify') { throw "the run did not say it could not verify - output: $($r.Output)" }
    }

    Test-Case 'a budget file declaring no budget cannot verify' {
        Reset-Fixture
        Set-Budget -NoBudgets
        $r = Invoke-Budget
        Assert-Equal 2 $r.ExitCode 'empty budget file verdict'
        if ($r.Output -notmatch 'nothing is watched') { throw "the refusal did not say why an empty budget file is not a pass - output: $($r.Output)" }
    }

    Test-Case 'a budget row with no source block cannot verify' {
        Reset-Fixture
        Set-Budget -NoSource
        $r = Invoke-Budget
        Assert-Equal 2 $r.ExitCode 'sourceless budget verdict'
        if ($r.Output -notmatch "missing the 'source' field") { throw "the refusal did not name the missing field - output: $($r.Output)" }
    }

    Test-Case 'no artifact and a fresh recorded measurement passes' {
        Reset-Fixture
        Set-Budget
        $r = Invoke-Budget
        Assert-Equal 0 $r.ExitCode "fresh recorded measurement verdict - output: $($r.Output)"
    }

    Test-Case 'no artifact and a stale recorded measurement cannot verify' {
        Reset-Fixture
        Set-Budget -MeasuredOn ((Get-Date).AddDays(-400).ToString('yyyy-MM-dd'))
        $r = Invoke-Budget
        Assert-Equal 2 $r.ExitCode 'stale recorded measurement verdict'
        if ($r.Output -notmatch 'past the 90-day horizon') { throw "the refusal did not name the horizon - output: $($r.Output)" }
    }

    Test-Case 'the live budget file parses and judges itself' {
        $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate', '-Quiet')
        $output = & $pwshExe @argv 2>&1 | Out-String
        $code = [int]$LASTEXITCODE
        if ($code -ne 0) { throw "the shipped scripts/quality/perf-budgets.json did not pass its own gate - exit $code, output: $output" }
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertPerfBudget.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
