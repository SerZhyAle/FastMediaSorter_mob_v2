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
    2 - required catalog, helper, or source roots cannot be read (PLAN/spec-catalog.jsonl absent from this
        checkout counts, unless -AllowAbsentInput).

.PARAMETER AllowAbsentInput
    The caller declaring a checkout without the gitignored root acceptable for this run.
    assert-fast-gates.ps1 passes it, because the GitHub Static Gates job runs on a clone that does
    not carry PLAN/ or .claude/. With it the absence ends `<gate>: NOT APPLICABLE (..)` with exit 0,
    which every aggregator counts as skipped, never as passed (CHECK-VERDICT 0.12 item A.1); without
    it the same absence is exit 2 (S4380, lib/absent-input.ps1).
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Quiet,
    [switch]$AllowAbsentInput
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

. (Join-Path $PSScriptRoot 'lib/absent-input.ps1')

# S3075: the journal is the one input whose absence is published - PLAN/ is gitignored, so a fresh
# clone, a release worktree and a CI runner all lack it legitimately. The helper and the two source
# roots are tracked, so their absence stays exit 2: that is a broken checkout, not a runner.
if (-not (Test-Path -LiteralPath $catalogPath)) {
    Exit-InputAbsent -Gate 'assert-ticket-acceptance-probes' -Path 'PLAN/spec-catalog.jsonl' `
        -Reason 'PLAN/ is gitignored - present only on a workstation checkout' -Allow:$AllowAbsentInput
}

try {
    foreach ($path in @($catalogPath, $helperPath) + $sourceRoots) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Required acceptance-probe input is missing: $path" }
    }
    . $helperPath
    $results = @(Test-TicketAcceptanceProbeContracts -CatalogPath $catalogPath -SourceRoots $sourceRoots)
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    Write-Host 'assert-ticket-acceptance-probes: COULD NOT VERIFY (the contract scan raised an error)'
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

if ($Gate -and $failures.Count -gt 0) { Write-Host "assert-ticket-acceptance-probes: FAIL ($($failures.Count) contract(s) failed)"; exit 1 }
Write-Host ("assert-ticket-acceptance-probes: PASS ({0} contract(s){1})" -f $results.Count, $(if ($failures.Count -gt 0) { '; report mode, -Gate not set' } else { '' }))
exit 0
