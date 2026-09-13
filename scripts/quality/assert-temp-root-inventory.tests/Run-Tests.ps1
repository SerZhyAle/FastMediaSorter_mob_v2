#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/quality/assert-temp-root-inventory.ps1 (S3030).

.DESCRIPTION
    Every case runs the gate against a FIXTURE root passed with -Root, never against the
    repository's own temp/. That is not tidiness: S3025 was opened because a suite asserted over the
    whole of temp/ root and failed on a file a concurrent Maestro run had written, which held a
    release. A suite whose verdict depends on what other sessions are doing is the defect, so this
    one judges a directory it created and removes.

    The direction that matters most is the false PASS on a ticket-shaped FILE. temp/S3030/ is legal
    scratch and temp/S3030_notes.txt never was, and the retired flat scheme left exactly the second
    shape behind - S2574-gate-before.txt and its four neighbours. A classification loose enough to
    admit them would pass over the entries S3030 exists to remove, so E4 pins the distinction.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$gate = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-temp-root-inventory.ps1'
if (-not (Test-Path -LiteralPath $gate)) {
    Write-Error "gate not found: $gate" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# The fixture lives under the ticket's own scratch directory (CLAUDE.md Rule 10), not at temp/ root
# and not in the system temp - the gate resolves the inventory from the repository, so the fixture
# only needs to supply entries to judge.
$fixture = Join-Path $repoRoot 'temp/S3030/fixture-root'
$failures = 0

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    New-Item -ItemType Directory -Path $fixture -Force | Out-Null
}

function Invoke-Gate {
    $output = & $pwshExe -NoProfile -File $gate -Root $fixture 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($output | Out-String) }
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
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
    Assert-Case 'E1 an empty root passes' {
        Reset-Fixture
        $r = Invoke-Gate
        if ($r.Code -ne 0) { return "expected exit 0, got $($r.Code): $($r.Text)" }
        return $null
    }

    Assert-Case 'E2 a declared fixed name passes' {
        Reset-Fixture
        New-Item -ItemType Directory -Path (Join-Path $fixture 'AGENT-CHAT') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $fixture 'DEVICE.REGISTRY') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $fixture 'RELEASE-FREEZE-FINGERPRINTS.json') -Value '{}' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'STOP-AGENT-WATCHDOG') -Value '' -Encoding UTF8
        $r = Invoke-Gate
        if ($r.Code -ne 0) { return "expected exit 0, got $($r.Code): $($r.Text)" }
        return $null
    }

    Assert-Case 'E3 a retained pattern passes' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $fixture 'check_fast_app_v2_Code_20260912_120000.log') -Value 'x' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'build_debug_20260912_120000.log') -Value 'x' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'fastmediasorter_20260912.log') -Value 'x' -Encoding UTF8
        $r = Invoke-Gate
        if ($r.Code -ne 0) { return "expected exit 0, got $($r.Code): $($r.Text)" }
        return $null
    }

    Assert-Case 'E4 a ticket DIRECTORY passes and a ticket-shaped FILE fails' {
        Reset-Fixture
        New-Item -ItemType Directory -Path (Join-Path $fixture 'S9999') -Force | Out-Null
        $r = Invoke-Gate
        if ($r.Code -ne 0) { return "a ticket directory should pass: expected exit 0, got $($r.Code): $($r.Text)" }

        Set-Content -LiteralPath (Join-Path $fixture 'S9999_leftover.txt') -Value 'x' -Encoding UTF8
        $r = Invoke-Gate
        if ($r.Code -ne 1) { return "a ticket-shaped file should fail: expected exit 1, got $($r.Code): $($r.Text)" }
        if ($r.Text -notmatch 'S9999_leftover\.txt') { return 'the refusal does not name the offending file' }
        if ($r.Text -notmatch 'only a DIRECTORY is') { return 'the refusal does not say why a ticket-shaped file is not scratch' }
        return $null
    }

    Assert-Case 'E5 an undeclared loose file fails and is named' {
        Reset-Fixture
        Set-Content -LiteralPath (Join-Path $fixture 'not-declared-anywhere.out') -Value 'x' -Encoding UTF8
        $r = Invoke-Gate
        if ($r.Code -ne 1) { return "expected exit 1, got $($r.Code): $($r.Text)" }
        if ($r.Text -notmatch 'not-declared-anywhere\.out') { return 'the refusal does not name the offending file' }
        return $null
    }

    Assert-Case 'E6 an undeclared directory fails and is named' {
        Reset-Fixture
        New-Item -ItemType Directory -Path (Join-Path $fixture 'not-declared-dir') -Force | Out-Null
        $r = Invoke-Gate
        if ($r.Code -ne 1) { return "expected exit 1, got $($r.Code): $($r.Text)" }
        if ($r.Text -notmatch 'not-declared-dir') { return 'the refusal does not name the offending directory' }
        return $null
    }

    Assert-Case 'E7 a missing root cannot verify' {
        if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
        $r = Invoke-Gate
        if ($r.Code -ne 2) { return "expected exit 2, got $($r.Code): $($r.Text)" }
        return $null
    }
}
finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($failures -gt 0) {
    Write-Error "assert-temp-root-inventory.tests: expected: 0 | actual: $failures failing case(s)." -ErrorAction Continue
    exit 1
}
Write-Host 'assert-temp-root-inventory.tests: PASS - 7 case(s).' -ForegroundColor Green
exit 0
