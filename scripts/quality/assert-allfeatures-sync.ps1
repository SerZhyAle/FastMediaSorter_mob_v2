#requires -Version 7.0
<#
.SYNOPSIS
    Keep docs/ALL_FEATURES.jsonl well-formed and from silently shrinking (S0489).

.DESCRIPTION
    Two checks on the developer feature inventory:
      1. Schema validity - delegates to scripts/all_features/validate.ps1 (-Gate):
         JSON well-formed, required fields, flavor enum, id uniqueness, EN-only.
      2. Ratchet - the public inventory record count must not drop below the
         committed baseline (scripts/quality/allfeatures-sync-baseline.txt). A drop
         means records were lost; it fails the gate unless -UpdateBaseline is used.

    Default mode reports and exits 0 (audit). With -Gate it fails closed (exit 1)
    on either a validation error or an unexplained record-count drop.

    Exit codes (S1070):
      0 - clean (or audit mode).
      1 - substantive failure: validation error or record-count regression.
      2 - the gate itself cannot run (inventory or validate.ps1 missing). Distinct
          from 1 on purpose: "the gate is broken" is not "the code is bad".

.PARAMETER Gate
    Fail-closed: exit 1 on validation failure or record-count regression.

.PARAMETER Quiet
    Print only the expected/actual summary line.

.PARAMETER UpdateBaseline
    Rewrite the baseline file with the current record count, then exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-allfeatures-sync.ps1
    pwsh -NoProfile -File scripts/quality/assert-allfeatures-sync.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-allfeatures-sync.ps1 -UpdateBaseline
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$UpdateBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dataFile = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'
$validate = Join-Path $repoRoot 'scripts/all_features/validate.ps1'
$baselineFile = Join-Path $PSScriptRoot 'allfeatures-sync-baseline.txt'

if (-not (Test-Path $dataFile)) { Write-Error "ALL_FEATURES.jsonl not found at $dataFile" -ErrorAction Continue; exit 2 }
if (-not (Test-Path $validate)) { Write-Error "validate.ps1 not found at $validate" -ErrorAction Continue; exit 2 }

# Current public record count (non-blank lines)
$count = @(Get-Content -LiteralPath $dataFile | Where-Object { $_.Trim().Length -gt 0 }).Count

if ($UpdateBaseline) {
    Set-Content -LiteralPath $baselineFile -Value "$count" -Encoding utf8 -NoNewline
    Write-Host "assert-allfeatures-sync: baseline updated -> $count"
    exit 0
}

# 1. Schema validity
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
& $pwshExe -NoProfile -File $validate -Gate
$schemaOk = ($LASTEXITCODE -eq 0)

# 2. Ratchet
$baseline = 0
if (Test-Path $baselineFile) {
    $raw = (Get-Content -LiteralPath $baselineFile -Raw).Trim()
    if ($raw -match '^\d+$') { $baseline = [int]$raw }
}
$regressed = ($count -lt $baseline)

if (-not $Quiet) {
    if (-not $schemaOk) { Write-Host "  ALL_FEATURES schema validation FAILED (see validate.ps1 output above)" -ForegroundColor Red }
    if ($regressed) { Write-Host "  ALL_FEATURES record count dropped: baseline $baseline -> current $count (run -UpdateBaseline if intentional)" -ForegroundColor Red }
}

Write-Host ("assert-allfeatures-sync: expected: schema ok and count >= {0} | actual: schema {1}, count {2}" -f $baseline, $(if ($schemaOk) { 'ok' } else { 'FAIL' }), $count)

if ($Gate -and (-not $schemaOk -or $regressed)) { exit 1 }
exit 0
