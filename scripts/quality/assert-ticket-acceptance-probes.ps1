#requires -Version 7.0
<#
.SYNOPSIS
    Audit explicit BlockNeedUserTest acceptance-probe contracts.

.DESCRIPTION
    Reads only `Probe literal:`, `Probe template:` and `Probe none:` markers
    from catalog status notes. Ordinary prose and historical audit sections are
    intentionally outside this gate's scope.

.EXIT CODES
    0 - clean, or mismatches reported in audit mode.
    1 - `-Gate` found a missing source template or invalid alternative evidence.
    2 - required catalog, helper, or source roots cannot be read.
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$catalogPath = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'
$sourceRoots = @(
    (Join-Path $repoRoot 'app_v2/src'),
    (Join-Path $repoRoot 'wear/src')
)
$helperPath = Join-Path $PSScriptRoot 'lib/ticket-acceptance-probes.ps1'

try {
    foreach ($path in @($catalogPath, $helperPath) + $sourceRoots) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Required acceptance-probe input is missing: $path" }
    }
    . $helperPath
    $results = @(Test-TicketAcceptanceProbeContracts -CatalogPath $catalogPath -SourceRoots $sourceRoots)
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 2
}

$failures = @($results | Where-Object { $_.Outcome -ne 'pass' })
if (-not $Quiet -and $failures.Count -gt 0) {
    Write-Host 'Acceptance-probe contract findings:'
    foreach ($finding in $failures | Sort-Object Ticket, Outcome) {
        Write-Host ("  {0}  {1}  expected: {2}" -f $finding.Ticket, $finding.Outcome, $finding.Expected)
    }
}
Write-Host ("assert-ticket-acceptance-probes: expected: 0 | actual: {0} (contracts: {1})" -f $failures.Count, $results.Count)

if ($Gate -and $failures.Count -gt 0) { exit 1 }
exit 0
