#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/quality/assert-watchface-listing-live.ps1 (S4009).

.DESCRIPTION
    The two cases that decide the verdict without Google Play run offline: a package id no surface
    carries passes without a request, and an unresolvable host is "cannot verify", never "live".
    The live-page verdict is the gate's own job at release scope; the one case that reaches Play
    requests a package that can never exist and is skipped, not failed, when the machine is offline.

.EXIT CODES
    0 - every case passed or was skipped for want of network.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$gate = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-watchface-listing-live.ps1'
if (-not (Test-Path -LiteralPath $gate)) {
    Write-Error "gate not found: $gate" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$failures = 0

function Invoke-Gate {
    param([string[]]$GateArgs)
    $output = & $pwshExe -NoProfile -File $gate @GateArgs 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($output | Out-String) }
}

function Assert-Case {
    param([string]$Name, [scriptblock]$Body)
    $message = & $Body
    if ($message -eq 'SKIP') {
        Write-Host "SKIP  $Name - no network"
    } elseif ($message) {
        Write-Host "FAIL  $Name - $message" -ForegroundColor Red
        $script:failures++
    } else {
        Write-Host "PASS  $Name"
    }
}

Assert-Case 'no surface links to the package - passes without a request' {
    $r = Invoke-Gate @('-PackageId', 'com.example.s4009.never.linked', '-Url', 'https://s4009-never-requested.invalid/')
    if ($r.Code -ne 0) { "expected exit 0, got $($r.Code): $($r.Text)" }
    elseif ($r.Text -notmatch 'no tracked user-visible surface links') { "verdict does not say why: $($r.Text)" }
}

Assert-Case 'an unresolvable host is cannot-verify, not live' {
    $r = Invoke-Gate @('-Url', 'https://s4009-watchface.invalid/', '-TimeoutSec', '10')
    if ($r.Code -ne 2) { "expected exit 2, got $($r.Code): $($r.Text)" }
    elseif ($r.Text -notmatch 'cannot verify') { "verdict does not say cannot verify: $($r.Text)" }
}

Assert-Case 'a Play page that does not exist fails and states why' {
    $r = Invoke-Gate @('-Url', 'https://play.google.com/store/apps/details?id=com.sza.s4009.never.published', '-TimeoutSec', '20')
    if ($r.Code -eq 2) { 'SKIP' }
    elseif ($r.Code -ne 1) { "expected exit 1, got $($r.Code): $($r.Text)" }
    elseif ($r.Text -notmatch '404') { "refusal does not state the 404 reason: $($r.Text)" }
}

if ($failures -gt 0) {
    Write-Host "assert-watchface-listing-live.tests: FAIL ($failures case(s))" -ForegroundColor Red
    exit 1
}
Write-Host 'assert-watchface-listing-live.tests: PASS' -ForegroundColor Green
exit 0
