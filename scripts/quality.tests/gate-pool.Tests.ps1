#requires -Version 7.0
<#
.SYNOPSIS
    S2326: tests for scripts/quality/lib/gate-pool.ps1 - the closure's read-only gate pool.

.DESCRIPTION
    The pool's whole safety argument is that a consumed entry runs the same command the call site
    would have run inline, and that a key nobody started still runs. Both are asserted here against
    a real child process, because a pool that silently skipped a gate would report a green closure
    over a check that never executed.

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
$pwsh = (Get-Process -Id $PID).Path
. (Join-Path $repoRoot 'scripts/quality/lib/gate-pool.ps1')

$echoScript = Join-Path ([System.IO.Path]::GetTempPath()) ("gate-pool-probe-{0}.ps1" -f $PID)
@'
param([string]$Tag, [int]$Code = 0)
Write-Host "probe:$Tag"
exit $Code
'@ | Set-Content -LiteralPath $echoScript -Encoding UTF8

try {
    Test-Case 'a pooled gate returns the child output and exit code' {
        $argv = @('-NoProfile', '-File', $echoScript, '-Tag', 'pooled', '-Code', '0')
        Start-PooledGate @argv
        $out = Invoke-GateChild @argv 6>&1 | Out-String
        Assert-Equal 0 $LASTEXITCODE 'exit code of a pooled pass'
        if ($out -notmatch 'probe:pooled') { throw "child output missing - got: $out" }
    }

    Test-Case 'a pooled gate reports its own elapsed time, not the wait' {
        $argv = @('-NoProfile', '-File', $echoScript, '-Tag', 'timed', '-Code', '0')
        Reset-PooledElapsedMs
        Start-PooledGate @argv
        Invoke-GateChild @argv 6>&1 | Out-Null
        $elapsed = Get-PooledElapsedMs
        if ($null -eq $elapsed) { throw 'the pooled run recorded no elapsed time' }
        if ($elapsed -le 0) { throw "elapsed must be positive - actual: $elapsed" }
    }

    Test-Case 'a non-zero child exit survives the pool' {
        $argv = @('-NoProfile', '-File', $echoScript, '-Tag', 'red', '-Code', '3')
        Start-PooledGate @argv
        Invoke-GateChild @argv 6>&1 | Out-Null
        Assert-Equal 3 $LASTEXITCODE 'exit code of a pooled failure'
    }

    Test-Case 'a key that was never pooled still runs inline' {
        $argv = @('-NoProfile', '-File', $echoScript, '-Tag', 'inline', '-Code', '4')
        Reset-PooledElapsedMs
        $out = Invoke-GateChild @argv 2>&1 | Out-String
        Assert-Equal 4 $LASTEXITCODE 'exit code of an inline run'
        if ($out -notmatch 'probe:inline') { throw "inline child output missing - got: $out" }
        if ($null -ne (Get-PooledElapsedMs)) { throw 'an inline run must not report a pooled elapsed time' }
    }

    Test-Case 'consuming an entry twice runs it again rather than returning nothing' {
        $argv = @('-NoProfile', '-File', $echoScript, '-Tag', 'twice', '-Code', '0')
        Start-PooledGate @argv
        Invoke-GateChild @argv 6>&1 | Out-Null
        $out = Invoke-GateChild @argv 2>&1 | Out-String
        Assert-Equal 0 $LASTEXITCODE 'exit code of the second consume'
        if ($out -notmatch 'probe:twice') { throw "second consume produced no output - got: $out" }
    }

    Test-Case 'Stop-GatePool leaves no job behind' {
        $argv = @('-NoProfile', '-File', $echoScript, '-Tag', 'abandoned', '-Code', '0')
        Start-PooledGate @argv
        Stop-GatePool
        Reset-PooledElapsedMs
        $out = Invoke-GateChild @argv 2>&1 | Out-String
        if ($out -notmatch 'probe:abandoned') { throw "the cleared key did not fall back to an inline run - got: $out" }
        if ($null -ne (Get-PooledElapsedMs)) { throw 'a cleared key must not resolve to a pooled result' }
    }
}
finally {
    Stop-GatePool
    Remove-Item -LiteralPath $echoScript -Force -ErrorAction SilentlyContinue
}

Write-Host ("gate-pool.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
