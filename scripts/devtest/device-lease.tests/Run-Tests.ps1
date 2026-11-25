#requires -Version 7.0
<#
.SYNOPSIS
    Exercise the public device lease CLI in an isolated portfolio.
.NOTES
    Exit codes: 0 passed, 1 failed, 2 shared tool unavailable.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$leaseScript = Join-Path $repoRoot 'scripts/devtest/device-lease.ps1'
. (Join-Path $repoRoot 'scripts/devtest/lib/device-lease-client.ps1')
if (-not (Find-SharedDeviceLease)) {
    Write-Error 'The shared SZA device lease tool is unavailable.' -ErrorAction Continue
    exit 2
}

$fixture = Join-Path ([IO.Path]::GetTempPath()) "fms-device-lease-$([guid]::NewGuid().ToString('N'))"
$env:SZA_PORTFOLIO_ROOT = $fixture
$first = 'lease-test-' + [guid]::NewGuid().ToString('N')
$second = 'lease-test-' + [guid]::NewGuid().ToString('N')
$avd = 'avd:Fixture_Pixel'
$physical = 'fixture-device:5555'
$other = 'avd:Fixture_Other'

function Invoke-CheckedLease {
    param([hashtable]$Parameters, [int]$Expected = 0)
    $json = & $leaseScript -Json @Parameters
    if ($LASTEXITCODE -ne $Expected) {
        throw "expected: $($Parameters.Verb) exit $Expected | actual: exit $LASTEXITCODE ($json)"
    }
    return ($json | ConvertFrom-Json)
}

try {
    $env:FMS_AGENT_ID = $first
    $claim = Invoke-CheckedLease @{ Verb = 'Claim'; Id = @($avd, $physical); Reason = 'fixture' }
    if ($claim.outcome -ne 'claimed') { throw "Initial claim: $($claim.outcome)" }
    if ((Invoke-CheckedLease @{ Verb = 'Claim'; Id = $avd }).outcome -ne 'already-mine') {
        throw 'The same session could not reuse its lease.'
    }

    $env:FMS_AGENT_ID = $second
    $null = Invoke-CheckedLease @{ Verb = 'Claim'; Id = @($other, $avd) } -Expected 3
    $null = Invoke-CheckedLease @{ Verb = 'Release'; Id = $avd } -Expected 4
    $status = @(Invoke-CheckedLease @{ Verb = 'Status' })
    if ($status.Count -ne 2 -or $status.id -contains $other -or
        @($status | Where-Object { $_.sessionId -ne $first }).Count -ne 0) {
        throw 'Status does not show both keys of the first holder or a failed claim left a partial lease.'
    }
    $any = Invoke-CheckedLease @{ Verb = 'Claim'; Id = @($avd, $other); Any = $true }
    if ($any.id -ne $other) { throw '-Any did not select the first free key.' }
    $null = Invoke-CheckedLease @{ Verb = 'Release'; All = $true }

    $env:FMS_AGENT_ID = $first
    $null = Invoke-CheckedLease @{ Verb = 'Release'; Id = @($physical, $avd) }
    $env:FMS_AGENT_ID = $second
    $null = Invoke-CheckedLease @{ Verb = 'Claim'; Id = $avd }
    $null = Invoke-CheckedLease @{ Verb = 'Release'; Id = $avd }
    if (@(Invoke-CheckedLease @{ Verb = 'List' }).Count -ne 0) { throw 'List still contains a released lease.' }
    $null = Invoke-CheckedLease @{ Verb = 'Sweep' }
    'device-lease tests: PASS (shared claims, contention, rollback, owner-only release and re-claim)'
    exit 0
}
finally {
    if (Test-Path -LiteralPath $fixture -PathType Container) {
        $resolved = (Resolve-Path -LiteralPath $fixture).Path
        $expectedParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
        if ((Split-Path -Parent $resolved) -eq $expectedParent -and (Split-Path -Leaf $resolved) -like 'fms-device-lease-*') {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}
