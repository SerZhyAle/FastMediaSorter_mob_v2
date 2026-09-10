#requires -Version 7.0
<#
.SYNOPSIS
    Keep docs/ALL_FEATURES.jsonl well-formed and from silently shrinking (S0489).

.DESCRIPTION
    Three checks on the developer feature inventory:
      1. Schema validity - delegates to scripts/all_features/validate.ps1 (-Gate):
         JSON well-formed, required fields, flavor enum, id uniqueness, EN-only.
      2. Ratchet - the public inventory record count must not drop below the
         committed baseline (scripts/quality/allfeatures-sync-baseline.txt). A drop
         means records were lost; it fails the gate unless -UpdateBaseline is used.
      3. Area vocabulary (S2842) - every record's 'area' must appear in the closed
         enum declared by docs/ALL_FEATURES.schema.json. The field was a free string
         until S2842 and had grown 91 spellings over 1059 records, near-duplicates
         included, so counting capabilities per area meant merging twin groups by
         hand first. This check lives here rather than in validate.ps1 because that
         script is a canon forwarder whose body this repository does not own (S2402),
         and the vocabulary is the project's - another product has other areas.
         The consequence is stated rather than hidden: add.ps1 accepts an unknown
         area and exits 0, and the refusal arrives at ticket closure instead.

    Default mode reports and exits 0 (audit). With -Gate it fails closed (exit 1)
    on a validation error, an unexplained record-count drop, or an unknown area.

    Exit codes (S1070):
      0 - clean (or audit mode).
      1 - substantive failure: validation error, record-count regression, or a
          record carrying an area outside the schema's enum.
      2 - the gate itself cannot run (inventory, validate.ps1, or the schema's area
          enum missing). Distinct from 1 on purpose: "the gate is broken" is not
          "the code is bad" - and an unreadable schema must not read as a pass,
          which is the one way this check could silently switch itself off.
      4 - Code.Scripts is held by another session, so no baseline was written. The queue
          place is held - wait for the turn in the background and rerun (S2635).

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
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$dataFile = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'
$validate = Join-Path $repoRoot 'scripts/all_features/validate.ps1'
$schemaFile = Join-Path $repoRoot 'docs/ALL_FEATURES.schema.json'
$baselineFile = Join-Path $PSScriptRoot 'allfeatures-sync-baseline.txt'

if (-not (Test-Path $dataFile)) { Write-Error "ALL_FEATURES.jsonl not found at $dataFile" -ErrorAction Continue; exit 2 }
if (-not (Test-Path $validate)) { Write-Error "validate.ps1 not found at $validate" -ErrorAction Continue; exit 2 }
if (-not (Test-Path $schemaFile)) { Write-Error "ALL_FEATURES.schema.json not found at $schemaFile" -ErrorAction Continue; exit 2 }

# Current public record count (non-blank lines)
$count = @(Get-Content -LiteralPath $dataFile | Where-Object { $_.Trim().Length -gt 0 }).Count

if ($UpdateBaseline) {
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-allfeatures-sync.ps1 -UpdateBaseline'
        Set-Content -LiteralPath $baselineFile -Value "$count" -Encoding utf8 -NoNewline
    }
    finally { Exit-CodeLockScope -Scope $scope }
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

# 3. Area vocabulary (S2842). The schema's enum is the only dictionary; nothing here
# restates it, so widening the list stays a one-line edit in one file (S1392).
$schema = $null
try { $schema = Get-Content -LiteralPath $schemaFile -Raw -Encoding UTF8 | ConvertFrom-Json }
catch { Write-Error "ALL_FEATURES.schema.json is not valid JSON: $($_.Exception.Message)" -ErrorAction Continue; exit 2 }
$allowedAreas = @()
if ($schema.PSObject.Properties.Name -contains 'properties' -and
    $schema.properties.PSObject.Properties.Name -contains 'area' -and
    $schema.properties.area.PSObject.Properties.Name -contains 'enum') {
    $allowedAreas = @($schema.properties.area.enum | ForEach-Object { [string]$_ })
}
if ($allowedAreas.Count -eq 0) {
    Write-Error "docs/ALL_FEATURES.schema.json declares no 'area' enum - the vocabulary check cannot run." -ErrorAction Continue
    exit 2
}

$badAreas = [System.Collections.Generic.List[string]]::new()
$lineNo = 0
foreach ($line in (Get-Content -LiteralPath $dataFile -Encoding UTF8)) {
    $lineNo++
    if ($line.Trim().Length -eq 0) { continue }
    # A malformed line is check 1's finding, not this one's - reporting it twice would
    # make one defect look like two.
    $obj = $null
    try { $obj = $line | ConvertFrom-Json -ErrorAction Stop } catch { continue }
    $area = [string]$obj.area
    if ($allowedAreas -ccontains $area) { continue }
    $badAreas.Add("L${lineNo}: area '$area' (id '$($obj.id)')")
}
$areasOk = ($badAreas.Count -eq 0)

if (-not $Quiet) {
    if (-not $schemaOk) { Write-Host "  ALL_FEATURES schema validation FAILED (see validate.ps1 output above)" -ForegroundColor Red }
    if ($regressed) { Write-Host "  ALL_FEATURES record count dropped: baseline $baseline -> current $count (run -UpdateBaseline if intentional)" -ForegroundColor Red }
}
if (-not $areasOk) {
    Write-Host "  ALL_FEATURES area outside the closed vocabulary ($($badAreas.Count) record(s)):" -ForegroundColor Red
    foreach ($b in $badAreas) { Write-Host "    $b" -ForegroundColor Red }
    Write-Host "  Use an existing area (scripts\all_features\add.ps1 -ListAreas), or add the new one to 'area.enum' in docs/ALL_FEATURES.schema.json." -ForegroundColor Yellow
}

Write-Host ("assert-allfeatures-sync: expected: schema ok, count >= {0}, every area in the {1}-value vocabulary | actual: schema {2}, count {3}, areas {4}" -f `
    $baseline, $allowedAreas.Count, $(if ($schemaOk) { 'ok' } else { 'FAIL' }), $count, $(if ($areasOk) { 'ok' } else { "FAIL ($($badAreas.Count))" }))

if ($Gate -and (-not $schemaOk -or $regressed -or -not $areasOk)) { exit 1 }
exit 0
