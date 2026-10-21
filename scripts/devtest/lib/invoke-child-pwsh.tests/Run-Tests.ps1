#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/devtest/lib/invoke-child-pwsh.ps1 (S4413).

.DESCRIPTION
    C1-C3 pin exit-code propagation: 0, 3, and a negative loader-style code (0xC0000142), plus the
    refused start that never runs a child at all and must still come back negative, because
    ui-sweep-device-io.ps1 counts negative exits as dead spawns.
    C4 pins that stdout and stderr both land in Output, in the order the child wrote them.
    C5 pins that arguments with spaces, double quotes and single quotes arrive intact.
    C6 pins that a child writing more than a pipe buffer on both streams does not deadlock.

    Fixture scripts are written under temp/S4413/invoke-child-fixture (CLAUDE.md Rule 10) and
    removed by the suite.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/devtest/lib/invoke-child-pwsh.ps1"

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed, or the library is missing.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$lib = Join-Path $PSScriptRoot '..\invoke-child-pwsh.ps1'
if (-not (Test-Path -LiteralPath $lib)) {
    Write-Error "library not found: $lib" -ErrorAction Continue
    exit 1
}
. $lib

# Four levels up: invoke-child-pwsh.tests -> lib -> devtest -> scripts -> repo root.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$fixture = Join-Path $repoRoot 'temp/S4413/invoke-child-fixture'
$failures = 0
$script:caseCount = 0

function New-Fixture {
    param([string]$Name, [string]$Body)
    $path = Join-Path $fixture $Name
    Set-Content -LiteralPath $path -Value $Body -Encoding UTF8
    return $path
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:caseCount++
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "PASS  $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

try {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    New-Item -ItemType Directory -Path $fixture -Force | Out-Null

    Assert-Case 'C1 exit 0 and exit 3 propagate' {
        $zero = Invoke-ChildPwsh -Script (New-Fixture 'exit0.ps1' "'ok'; exit 0")
        if ($zero.Exit -ne 0) { return "expected: 0 | actual: $($zero.Exit)" }
        if ($zero.Output -ne 'ok') { return "expected output: ok | actual: $($zero.Output)" }
        $three = Invoke-ChildPwsh -Script (New-Fixture 'exit3.ps1' 'exit 3')
        if ($three.Exit -ne 3) { return "expected: 3 | actual: $($three.Exit)" }
        return $null
    }

    Assert-Case 'C2 a negative loader-style exit stays negative' {
        $neg = Invoke-ChildPwsh -Script (New-Fixture 'exitneg.ps1' 'exit -1073741502')
        if ($neg.Exit -ne -1073741502) { return "expected: -1073741502 | actual: $($neg.Exit)" }
        return $null
    }

    Assert-Case 'C3 a start the OS refuses returns a negative exit with the reason' {
        $missing = Join-Path $fixture 'no-such-pwsh.exe'
        $call = Invoke-ChildPwsh -Script (Join-Path $fixture 'exit0.ps1') -Executable $missing
        if ($call.Exit -ge 0) { return "expected: negative | actual: $($call.Exit)" }
        if ($call.Output -notmatch 'did not start') { return "expected reason in output | actual: $($call.Output)" }
        return $null
    }

    Assert-Case 'C4 stdout and stderr both land in Output, in arrival order' {
        $body = @'
[Console]::Out.WriteLine('out-1'); [Console]::Out.Flush()
Start-Sleep -Milliseconds 300
[Console]::Error.WriteLine('err-1'); [Console]::Error.Flush()
Start-Sleep -Milliseconds 300
[Console]::Out.WriteLine('out-2'); [Console]::Out.Flush()
exit 0
'@
        $call = Invoke-ChildPwsh -Script (New-Fixture 'streams.ps1' $body)
        $expected = "out-1`nerr-1`nout-2"
        if ($call.Output -ne $expected) { return "expected: $($expected -replace "`n", '|') | actual: $($call.Output -replace "`n", '|')" }
        return $null
    }

    Assert-Case 'C5 arguments with spaces and quotes arrive intact' {
        $body = 'foreach ($a in $args) { [Console]::Out.WriteLine("<$a>") }'
        $argv = @('plain', 'two words', 'say "hi"', "it's", '', '-Cmd', 'settings get global x')
        $call = Invoke-ChildPwsh -Script (New-Fixture 'echo.ps1' $body) -Arguments $argv
        $expected = ($argv | ForEach-Object { "<$_>" }) -join "`n"
        if ($call.Output -ne $expected) { return "expected: $($expected -replace "`n", '|') | actual: $($call.Output -replace "`n", '|')" }
        return $null
    }

    Assert-Case 'C6 large output on both streams does not deadlock' {
        $body = @'
$chunk = 'x' * 200
for ($i = 0; $i -lt 2000; $i++) { [Console]::Out.WriteLine($chunk); [Console]::Error.WriteLine($chunk) }
exit 0
'@
        $call = Invoke-ChildPwsh -Script (New-Fixture 'flood.ps1' $body)
        if ($call.Exit -ne 0) { return "expected: 0 | actual: $($call.Exit)" }
        $count = @($call.Output -split "`n").Count
        if ($count -ne 4000) { return "expected: 4000 lines | actual: $count" }
        return $null
    }
}
finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}

if ($failures -gt 0) {
    Write-Host "invoke-child-pwsh.tests: FAIL (run=$script:caseCount skipped=0)" -ForegroundColor Red
    exit 1
}
Write-Host "invoke-child-pwsh.tests: PASS (run=$script:caseCount skipped=0)" -ForegroundColor Green
exit 0
