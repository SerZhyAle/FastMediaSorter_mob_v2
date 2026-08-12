#requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
. (Join-Path $repoRoot 'scripts/quality/lib/ticket-acceptance-probes.ps1')
$fixtureRoot = Join-Path $repoRoot 'temp/S1582/acceptance-probes-test'
$sourceRoot = Join-Path $fixtureRoot 'src/main/java'
$catalogPath = Join-Path $fixtureRoot 'spec-catalog.jsonl'

function Assert-Test {
    param([Parameter(Mandatory)][bool] $Condition, [Parameter(Mandatory)][string] $Message)
    if (-not $Condition) { throw "assert-ticket-acceptance-probes test failed: $Message" }
}

try {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $sourceRoot -Force | Out-Null
    @'
package fixture

fun probes(value: Int, user: String) {
    Timber.d(
        "S9000: multiline value=%d",
        value
    )
    Timber.d("S9001: interpolation user=$user")
    // Timber.d("S9006: commented probe")
}
'@ | Set-Content -LiteralPath (Join-Path $sourceRoot 'ProbeFixture.kt') -NoNewline
    @'
{"id":"S9000","status":"BlockNeedUserTest","statusNote":"Probe literal: S9000: multiline value=%d"}
{"id":"S9001","status":"BlockNeedUserTest","statusNote":"Probe template: S9001: interpolation user=<name>"}
{"id":"S9002","status":"BlockNeedUserTest","statusNote":"Probe literal: S9002: absent literal"}
{"id":"S9003","status":"BlockNeedUserTest","statusNote":"Probe none: visual proof in system UI"}
{"id":"S9004","status":"BlockNeedUserTest","statusNote":"Probe none: no"}
{"id":"S9005","status":"BlockNeedUserTest","statusNote":"Probe: S9005: unmarked prose"}
'@ | Set-Content -LiteralPath $catalogPath -NoNewline

    $templates = @(Get-TimberProbeTemplates -SourceRoots @($fixtureRoot))
    Assert-Test ($templates.Count -eq 2) 'multiline/interpolation parser must ignore comments'
    Assert-Test (@($templates | Where-Object Ticket -eq 'S9000').Count -eq 1) 'multiline probe missing'
    Assert-Test (@($templates | Where-Object Ticket -eq 'S9001').Count -eq 1) 'interpolation probe missing'

    $results = @(Test-TicketAcceptanceProbeContracts -CatalogPath $catalogPath -SourceRoots @($fixtureRoot))
    $byTicket = @{}
    foreach ($result in $results) { $byTicket[$result.Ticket] = $result.Outcome }
    Assert-Test ($results.Count -eq 5) 'unmarked prose must not become a contract'
    Assert-Test ($byTicket['S9000'] -eq 'pass') 'multiline literal did not match'
    Assert-Test ($byTicket['S9001'] -eq 'pass') 'interpolation template did not match'
    Assert-Test ($byTicket['S9002'] -eq 'missing-source-template') 'absent literal was not reported'
    Assert-Test ($byTicket['S9003'] -eq 'pass') 'valid Probe none was rejected'
    Assert-Test ($byTicket['S9004'] -eq 'invalid-alternative-evidence') 'invalid Probe none was accepted'
    Write-Host 'assert-ticket-acceptance-probes tests: expected: 8 | actual: 8'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}
