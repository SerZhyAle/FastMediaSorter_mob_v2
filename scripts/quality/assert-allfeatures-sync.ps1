#requires -Version 7.0
<#
.SYNOPSIS
    Keep docs/ALL_FEATURES.jsonl well-formed and from silently shrinking (S0489).

.DESCRIPTION
    Four checks on the developer feature inventory:
      1. Schema validity - delegates to scripts/all_features/validate.ps1 (-Gate):
         JSON well-formed, required fields, flavor enum, id uniqueness, EN-only.
      2. Ratchet - the public inventory record count must not drop below the
         committed baseline (scripts/quality/allfeatures-sync-baseline.txt). A drop
         means records were lost; it fails the gate unless -UpdateBaseline is used.
      3. Area vocabulary (S2842, S3170) - every record's 'area' must appear in the closed
         enum declared by docs/ALL_FEATURES.schema.json. The field was a free string
         until S2842 and had grown 91 spellings over 1059 records, near-duplicates
         included, so counting capabilities per area meant merging twin groups by
         hand first. S3170 added early schema-based area validation into add.ps1,
         so invalid area names are rejected before writing to ALL_FEATURES.jsonl,
         and this gate serves as the final repository-level verification.
      4. Subject reach (S2933) - a record whose subject is named in
         scripts/quality/allfeatures-subject-reach.json must declare either that
         subject's flag row in docs/FLAVOR_MATRIX.md or the full flavor dimension.
         The S1934 ratchet inside validate.ps1 accepts an ungated set that equals
         the row of ANY flag, so a set that is right only by coincidence is
         indistinguishable from one that is right on purpose - 491 of 1085 records
         sat in that position when this was measured on 2026-09-11, two of them
         claiming the SUPPORT_CAST row while describing phone-to-watch settings
         sync. This check lives here for the same reason check 3 does: validate.ps1
         is a canon forwarder whose body this repository does not own (S2402), and
         the subject map is the project's.

      5. Gate retention (S3209) - a record that carried a 'gate' in
         scripts/quality/allfeatures-gate-baseline.txt must still carry that same
         flag. The sanctioned closure path destroys the field: all_features/add.ps1
         upserts the WHOLE record rather than merging fields, and close-and-log.ps1
         never passes Gate at all (S2937), so any -FuncOp CHANGE/FIX landing on an
         existing gated id silently strips its flag. Measured 2026-09-17 on the
         S3208 closure: launcher.start-panel-rows lost "gate":"SUPPORT_LAUNCHER"
         and validate.ps1 returned PASS both before and after - the schema does not
         require the field, and the flavors set was untouched, so check 4 saw a
         correct reach too. 141 of 1177 records carry a gate, which is the size of
         the exposed set. This check lives here for the same reason checks 3 and 4
         do: validate.ps1 is a canon forwarder whose body this repository does not
         own (S2402), and the baseline is the project's. Restoring a stripped flag
         is scripts/all_features/patch.ps1 -Id <id> -Gate <flag>; an intentional
         re-gate or removal is -UpdateBaseline.

    Default mode reports and exits 0 (audit). With -Gate it fails closed (exit 1)
    on a validation error, an unexplained record-count drop, an unknown area, a
    subject record whose reach is neither its flag's row nor the full dimension, or
    a baseline record that lost its gate.

    Exit codes (S1070):
      0 - clean (or audit mode).
      1 - substantive failure: validation error, record-count regression, a record
          carrying an area outside the schema's enum, a subject record declaring
          a reach the build system does not produce, or a record that lost the gate
          the baseline recorded for it.
      2 - the gate itself cannot run (inventory, validate.ps1, the schema's area
          enum, the subject map or the flavor matrix missing or malformed).
          Distinct from 1 on purpose: "the gate is broken" is not "the code is
          bad" - and an unreadable input must not read as a pass, which is the one
          way this check could silently switch itself off.
      4 - Code.Scripts is held by another session, so no baseline was written. The queue
          place is held - wait for the turn in the background and rerun (S2635).

.PARAMETER Gate
    Fail-closed: exit 1 on validation failure, record-count regression, an unknown
    area, a bad subject reach, or a lost record gate.

.PARAMETER Quiet
    Print only the expected/actual summary line.

.PARAMETER UpdateBaseline
    Rewrite both baselines - the record count and the id/gate pairs - then exit 0.

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
. (Join-Path $PSScriptRoot 'lib/flavor-matrix.ps1')

$dataFile = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'
$validate = Join-Path $repoRoot 'scripts/all_features/validate.ps1'
$schemaFile = Join-Path $repoRoot 'docs/ALL_FEATURES.schema.json'
$baselineFile = Join-Path $PSScriptRoot 'allfeatures-sync-baseline.txt'
$gateBaselineFile = Join-Path $PSScriptRoot 'allfeatures-gate-baseline.txt'
$subjectMapFile = Join-Path $PSScriptRoot 'allfeatures-subject-reach.json'

# S3209. One reader for both callers - -UpdateBaseline writes from it and check 5 judges against
# it - so the baseline can never be written by a rule the check does not apply. A malformed line is
# check 1's finding and is skipped here rather than reported twice.
function Get-RecordGateMap {
    param([Parameter(Mandatory)][string]$Path)
    $map = [ordered]@{}
    foreach ($l in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        if ($l.Trim().Length -eq 0) { continue }
        $o = $null
        try { $o = $l | ConvertFrom-Json -ErrorAction Stop } catch { continue }
        $id = "$($o.id)"
        if (-not $id) { continue }
        $g = ''
        if ($o.PSObject.Properties.Name -contains 'gate') { $g = "$($o.gate)".Trim() }
        $map[$id] = $g
    }
    return $map
}

if (-not (Test-Path $dataFile)) { Write-Error "ALL_FEATURES.jsonl not found at $dataFile" -ErrorAction Continue; exit 2 }
if (-not (Test-Path $validate)) { Write-Error "validate.ps1 not found at $validate" -ErrorAction Continue; exit 2 }
if (-not (Test-Path $schemaFile)) { Write-Error "ALL_FEATURES.schema.json not found at $schemaFile" -ErrorAction Continue; exit 2 }
if (-not (Test-Path $subjectMapFile)) { Write-Error "allfeatures-subject-reach.json not found at $subjectMapFile" -ErrorAction Continue; exit 2 }

# Current public record count (non-blank lines)
$count = @(Get-Content -LiteralPath $dataFile | Where-Object { $_.Trim().Length -gt 0 }).Count

if ($UpdateBaseline) {
    # S3209: both baselines move under ONE lock acquisition. Written separately they could disagree -
    # a count taken from one read of the ledger and a gate map from another - and the gate ratchet
    # would then be armed against a ledger state that never existed.
    $gateRows = @()
    foreach ($kv in (Get-RecordGateMap -Path $dataFile).GetEnumerator()) {
        if ($kv.Value) { $gateRows += "$($kv.Key) $($kv.Value)" }
    }
    $gateRows = @($gateRows | Sort-Object)
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-allfeatures-sync.ps1 -UpdateBaseline'
        Set-Content -LiteralPath $baselineFile -Value "$count" -Encoding utf8 -NoNewline
        Set-Content -LiteralPath $gateBaselineFile -Value $gateRows -Encoding utf8
    }
    finally { Exit-CodeLockScope -Scope $scope }
    Write-Host "assert-allfeatures-sync: baselines updated -> count $count, gated records $($gateRows.Count)"
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

# 4. Subject reach (S2933). The map declares which flag dictates a subject's reach; the matrix
# supplies that flag's row and the full dimension. Both are read once, before the record walk.
$subjects = @()
try { $subjectMap = Get-Content -LiteralPath $subjectMapFile -Raw -Encoding UTF8 | ConvertFrom-Json }
catch { Write-Error "allfeatures-subject-reach.json is not valid JSON: $($_.Exception.Message)" -ErrorAction Continue; exit 2 }
if ($subjectMap.PSObject.Properties.Name -contains 'subjects') { $subjects = @($subjectMap.subjects) }

$matrix = Get-FlavorMatrixTable
if ($subjects.Count -gt 0 -and $matrix.Flags.Count -eq 0) {
    # Only when a subject is actually declared: a repository with an empty map has no reach rule to
    # run and must not be failed for a grid it never consults.
    Write-Error "docs/FLAVOR_MATRIX.md could not be read ($($matrix.Path)) - the subject-reach check cannot run. Regenerate it with scripts/docs/generate-flavor-matrix.ps1." -ErrorAction Continue
    exit 2
}
$fullDimensionSig = Get-FlavorSetSignature -Flavors $matrix.Flavors
foreach ($s in $subjects) {
    $flag = [string]$s.gate
    if (-not $matrix.Flags.ContainsKey($flag)) {
        # A subject pointing at a flag the matrix does not carry checks nothing while looking like
        # it does - the same silent switch-off an unknown `gate` value is rejected for in validate.ps1.
        Write-Error "allfeatures-subject-reach.json: subject '$($s.name)' names gate '$flag', which is not a flag in docs/FLAVOR_MATRIX.md." -ErrorAction Continue
        exit 2
    }
}

$badAreas = [System.Collections.Generic.List[string]]::new()
$badReach = [System.Collections.Generic.List[string]]::new()
$lineNo = 0
foreach ($line in (Get-Content -LiteralPath $dataFile -Encoding UTF8)) {
    $lineNo++
    if ($line.Trim().Length -eq 0) { continue }
    # A malformed line is check 1's finding, not this one's - reporting it twice would
    # make one defect look like two.
    $obj = $null
    try { $obj = $line | ConvertFrom-Json -ErrorAction Stop } catch { continue }
    $area = [string]$obj.area
    if (-not ($allowedAreas -ccontains $area)) {
        $badAreas.Add("L${lineNo}: area '$area' (id '$($obj.id)')")
    }

    # A record naming its own gate is validate.ps1's to judge: that script compares the set against
    # the named row, which is a stronger claim than this check makes. It is also the in-band exit for
    # a subject record that genuinely lives behind some other flag.
    if ($obj.PSObject.Properties.Name -contains 'gate' -and -not [string]::IsNullOrWhiteSpace("$($obj.gate)")) { continue }
    if (-not ($obj.PSObject.Properties.Name -contains 'flavors')) { continue }

    $recordSig = Get-FlavorSetSignature -Flavors @($obj.flavors)
    foreach ($s in $subjects) {
        $matched = (@($s.areas) -ccontains $area) -or ("$($obj.id)" -match [string]$s.idPattern)
        if (-not $matched) { continue }
        $rowSig = Get-FlavorSetSignature -Flavors $matrix.Flags[[string]$s.gate]
        if ($recordSig -eq $rowSig -or $recordSig -eq $fullDimensionSig) { break }
        $badReach.Add("L${lineNo}: $($obj.id) declares [$recordSig] - $($s.name) reaches [$rowSig] ($($s.gate)), or [$fullDimensionSig] when nothing narrows it")
        break
    }
}
$areasOk = ($badAreas.Count -eq 0)
$reachOk = ($badReach.Count -eq 0)

# 5. Gate retention (S3209). The ledger's own writer cannot tell "say nothing about the gate" from
# "remove the gate" - add.ps1 upserts the whole record - so the only place the distinction survives
# is a baseline written when the field was still there.
$lostGates = [System.Collections.Generic.List[string]]::new()
$gateBaselineRows = 0
if (Test-Path -LiteralPath $gateBaselineFile) {
    $currentGates = Get-RecordGateMap -Path $dataFile
    foreach ($row in (Get-Content -LiteralPath $gateBaselineFile -Encoding UTF8)) {
        $trimmed = $row.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $parts = @($trimmed -split '\s+', 2)
        if ($parts.Count -ne 2) {
            Write-Error "allfeatures-gate-baseline.txt: '$trimmed' is not a '<id> <gate>' pair." -ErrorAction Continue
            exit 2
        }
        $gateBaselineRows++
        $bId = $parts[0]; $bGate = $parts[1].Trim()
        if (-not $currentGates.Contains($bId)) {
            $lostGates.Add("$bId - the record is gone from the ledger (it carried $bGate)")
            continue
        }
        $now = [string]$currentGates[$bId]
        if ($now -cne $bGate) {
            $nowText = if ($now) { "carries $now" } else { 'carries no gate' }
            $lostGates.Add("$bId - baseline $bGate, ledger $nowText")
        }
    }
}
$gatesOk = ($lostGates.Count -eq 0)

if (-not $Quiet) {
    if (-not $schemaOk) { Write-Host "  ALL_FEATURES schema validation FAILED (see validate.ps1 output above)" -ForegroundColor Red }
    if ($regressed) { Write-Host "  ALL_FEATURES record count dropped: baseline $baseline -> current $count (run -UpdateBaseline if intentional)" -ForegroundColor Red }
}
if (-not $areasOk) {
    Write-Host "  ALL_FEATURES area outside the closed vocabulary ($($badAreas.Count) record(s)):" -ForegroundColor Red
    foreach ($b in $badAreas) { Write-Host "    $b" -ForegroundColor Red }
    Write-Host "  Use an existing area (scripts\all_features\add.ps1 -ListAreas), or add the new one to 'area.enum' in docs/ALL_FEATURES.schema.json." -ForegroundColor Yellow
}
if (-not $reachOk) {
    Write-Host "  ALL_FEATURES record claims a reach its subject does not produce ($($badReach.Count) record(s)):" -ForegroundColor Red
    foreach ($b in $badReach) { Write-Host "    $b" -ForegroundColor Red }
    Write-Host "  Correct the set with scripts\all_features\patch.ps1 -SetFlavors, or name the flag the capability really lives behind with -Gate (scripts\all_features\README.md, 'The three legal shapes')." -ForegroundColor Yellow
}

if (-not $gatesOk) {
    Write-Host "  ALL_FEATURES record lost the gate the baseline recorded for it ($($lostGates.Count) record(s)):" -ForegroundColor Red
    foreach ($b in $lostGates) { Write-Host "    $b" -ForegroundColor Red }
    Write-Host "  The closure path strips it (S3209): restore with scripts\all_features\patch.ps1 -Id <id> -Gate <flag>." -ForegroundColor Yellow
    Write-Host "  Re-gated or removed the record on purpose? Re-run this script with -UpdateBaseline." -ForegroundColor Yellow
}

Write-Host ("assert-allfeatures-sync: expected: schema ok, count >= {0}, every area in the {1}-value vocabulary, every subject record at its flag's reach, all {2} baseline gates retained | actual: schema {3}, count {4}, areas {5}, reach {6}, gates {7}" -f `
    $baseline, $allowedAreas.Count, $gateBaselineRows, $(if ($schemaOk) { 'ok' } else { 'FAIL' }), $count, `
    $(if ($areasOk) { 'ok' } else { "FAIL ($($badAreas.Count))" }), `
    $(if ($reachOk) { 'ok' } else { "FAIL ($($badReach.Count))" }), `
    $(if ($gatesOk) { 'ok' } else { "FAIL ($($lostGates.Count))" }))

if ($Gate -and (-not $schemaOk -or $regressed -or -not $areasOk -or -not $reachOk -or -not $gatesOk)) {
    # Named rather than a bare `exit 1`: this script runs as a hooks.postClose step, where the
    # caller reports the step as FAILED and the detail above may be many lines up the transcript.
    $failed = @()
    if (-not $schemaOk) { $failed += 'schema validation' }
    if ($regressed) { $failed += "record count ($count below baseline $baseline)" }
    if (-not $areasOk) { $failed += "area vocabulary ($($badAreas.Count) record(s))" }
    if (-not $reachOk) { $failed += "subject reach ($($badReach.Count) record(s))" }
    if (-not $gatesOk) { $failed += "gate retention ($($lostGates.Count) record(s))" }
    Write-Error "assert-allfeatures-sync: FAIL - $($failed -join '; '). The per-check detail is printed above." -ErrorAction Continue
    exit 1
}
exit 0
