#requires -Version 7.0
<#
.SYNOPSIS
    Coverage gate for the device-profile preset matrix (matrix from S0327, gate rules from S1216).

.DESCRIPTION
    Cross-checks four sources so a setting cannot silently drift out of the preset matrix:

      * `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` - the fields.
      * `app_v2/src/main/assets/device_profile_presets.csv` - the owner-authored matrix.
      * `docs/settings/device-profile-nonpresettable.json` - fields a profile may never set.
      * `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` -
        the branches that decide whether a matrix cell can take effect at all.

    Rules:

      1. Every AppSettings field has either a CSV row or a registry entry.
      2. A registered non-presettable field carries no value in any profile column - the registry
         promises the value can never take effect, so authoring one is a silent data loss.
      3. Every registry entry names a field that still exists on AppSettings.
      4. Every CSV row names a field that still exists on AppSettings.
      5. Every CSV row has either an applier branch or a registry entry.
      6. A CSV row carrying a value has an applier branch - otherwise owner-authored data is
         dropped at apply time. This is an error regardless of the registry.
      7. Every DeviceProfileType has a CSV column, and no CSV column names an unknown profile.
      8. Every non-empty cell of a constrained field is inside the range its Settings screen
         accepts; the `Other` column is entirely empty.

    Rules 5 and 6 are the reason the registry exists: without it the checker cannot tell a
    forgotten setting from one that is deliberately never presettable.

.PARAMETER AddMissing
    Append missing AppSettings field rows and DeviceProfileType columns to the CSV (empty cells),
    then exit 0. Does not modify existing cells, row order, or column order. Registered
    non-presettable fields are not appended.

.PARAMETER Gate
    Same run, gate framing: exit 1 on any violation. Matches the sibling gates in scripts/quality/
    so assert-fast-gates.ps1 can call this script the same way.

.PARAMETER Quiet
    Suppress the informational counters. Violations are always printed.

.NOTES
    Exit codes (CLAUDE.md Rule 7 / S1070 contract):
      0  OK - every rule above holds, or -AddMissing completed.
      1  INCONSISTENT - at least one rule is violated, or a required input is missing/unparseable.

.EXAMPLE
    pwsh -NoProfile -File scripts/check_device_profile_presets.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -Gate -Quiet
.EXAMPLE
    pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -AddMissing
#>
param(
    [switch]$AddMissing,
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
trap { Write-Error $_ -ErrorAction Continue; exit 1 }

$repo = Split-Path $PSScriptRoot -Parent
$appSettingsPath = Join-Path $repo 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt'
$deviceProfilePath = Join-Path $repo 'app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt'
$applierPath = Join-Path $repo 'app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt'
$csvPath = Join-Path $repo 'app_v2/src/main/assets/device_profile_presets.csv'
$registryPath = Join-Path $repo 'docs/settings/device-profile-nonpresettable.json'

foreach ($p in @($appSettingsPath, $deviceProfilePath, $applierPath, $csvPath, $registryPath)) {
    if (-not (Test-Path $p)) {
        Write-Error "Not found: $p" -ErrorAction Continue
        exit 1
    }
}

function Write-Info([string]$Text) { if (-not $Quiet) { Write-Output $Text } }

# --- AppSettings field names (data class `val name: Type`) ---
$appFields = Select-String -Path $appSettingsPath -Pattern '^\s*val\s+(\w+)\s*:' |
    ForEach-Object { $_.Matches[0].Groups[1].Value }

# --- DeviceProfileType enum values ---
$dpText = Get-Content $deviceProfilePath -Raw
$enumMatch = [regex]::Match($dpText, 'enum\s+class\s+DeviceProfileType\s*\{([^}]*)\}')
if (-not $enumMatch.Success) {
    Write-Error 'Could not locate enum class DeviceProfileType' -ErrorAction Continue
    exit 1
}
$enumValues = ($enumMatch.Groups[1].Value -split ',') |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[A-Z0-9_]+$' }

# --- Non-presettable registry ---
$registry = Get-Content $registryPath -Raw | ConvertFrom-Json
if (-not $registry.fields) {
    Write-Error "Registry has no 'fields' array: $registryPath" -ErrorAction Continue
    exit 1
}
$registryFields = @($registry.fields | ForEach-Object { $_.field })
$registryNoReason = @($registry.fields | Where-Object { -not $_.reason } | ForEach-Object { $_.field })

# --- Applier branch labels (`"fieldName" ->` at line start) ---
$applierBranches = @(
    Select-String -Path $applierPath -Pattern '^\s*"(\w+)"\s*->' |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
)

# --- CSV (Import-Csv handles quoted fields) ---
$csvRows = @(Import-Csv -Path $csvPath)
$csvColumns = @($csvRows[0].psobject.Properties.Name)
if ($csvColumns[0] -ne 'option') {
    Write-Error "First CSV column must be 'option', found '$($csvColumns[0])'" -ErrorAction Continue
    exit 1
}
$csvProfileColumns = $csvColumns | Where-Object { $_ -ne 'option' }
$csvFields = @($csvRows | ForEach-Object { $_.option })

# Profile column-key <-> enum value (case-insensitive; CSV uses lower_snake, 'Other' = OTHER).
function ColToEnum([string]$col) { return $col.ToUpperInvariant() }
function EnumToCol([string]$e)   { return $e.ToLowerInvariant() }

$csvProfileEnums = $csvProfileColumns | ForEach-Object { ColToEnum $_ }

# Non-empty cells per row, as "column=value" - the shape every value report uses.
function Get-FilledCells($row) {
    $out = @()
    foreach ($c in $csvProfileColumns) {
        $v = $row.$c
        if (-not [string]::IsNullOrWhiteSpace($v)) { $out += "$c=$v" }
    }
    return $out
}

# --- Value sanity: what each Settings screen actually accepts -------------------------------
# Data-driven on purpose: a newly constrained field is one line here, not a new code path.
$allowedValues = @{
    'linkDownloadMaxResolution' = @('480p', '720p', '1080p', 'best')
    'textReaderTheme'           = @('SYSTEM', 'LIGHT', 'DARK', 'SEPIA')
    'pdfColorMode'              = @('NORMAL', 'NIGHT', 'SEPIA')
    'colorTheme'                = @('AUTO', 'LIGHT', 'DARK', 'BLACK')
}
# Fields constrained by a rule rather than an enumeration. A value off the slider step is stored
# but never displayed, so the screen silently falls back - the defect this rule exists to catch.
$valueRules = @{
    'defaultIconSize' = @{
        Description = 'must satisfy 32 + 8*N (icon-size slider step)'
        Test        = { param($v) ($v -match '^\d+$') -and ([int]$v -ge 32) -and ((([int]$v) - 32) % 8 -eq 0) }
    }
}

$valueViolations = @()
$otherColumnViolations = @()
foreach ($row in $csvRows) {
    $field = $row.option
    foreach ($c in $csvProfileColumns) {
        $v = $row.$c
        if ([string]::IsNullOrWhiteSpace($v)) { continue }
        if ($c -eq 'Other') { $otherColumnViolations += "$field=$v"; continue }
        if ($allowedValues.ContainsKey($field) -and $v -notin $allowedValues[$field]) {
            $valueViolations += "$field/${c}='$v' (allowed: $($allowedValues[$field] -join '|'))"
        }
        if ($valueRules.ContainsKey($field) -and -not (& $valueRules[$field].Test $v)) {
            $valueViolations += "$field/${c}='$v' ($($valueRules[$field].Description))"
        }
    }
}

# --- Coverage rules -------------------------------------------------------------------------
$missingRows = $appFields | Where-Object { $_ -notin $csvFields -and $_ -notin $registryFields }
$staleRows = $csvFields | Where-Object { $_ -notin $appFields }
$missingColumns = $enumValues | Where-Object { $_ -notin $csvProfileEnums }
$unknownColumns = $csvProfileColumns | Where-Object { (ColToEnum $_) -notin $enumValues }
$staleRegistry = $registryFields | Where-Object { $_ -notin $appFields }

# A registered field that nevertheless carries a value: the registry says it can never apply.
$registeredWithValue = @()
foreach ($row in $csvRows) {
    if ($row.option -notin $registryFields) { continue }
    $filled = Get-FilledCells $row
    if ($filled.Count -gt 0) { $registeredWithValue += "$($row.option) [$($filled -join ', ')]" }
}

# Rows the applier would drop on the floor. Split by whether a value is actually at stake.
$rowsWithoutBranch = @()
$valuedRowsWithoutBranch = @()
foreach ($row in $csvRows) {
    $field = $row.option
    if ($field -in $applierBranches) { continue }
    $filled = Get-FilledCells $row
    if ($filled.Count -gt 0) {
        $valuedRowsWithoutBranch += "$field [$($filled -join ', ')]"
    }
    elseif ($field -notin $registryFields) {
        $rowsWithoutBranch += $field
    }
}

Write-Info 'Device profile preset matrix check'
Write-Info "  AppSettings fields    : $($appFields.Count)"
Write-Info "  CSV rows              : $($csvFields.Count)"
Write-Info "  DeviceProfileTypes    : $($enumValues.Count)"
Write-Info "  CSV profile columns   : $($csvProfileColumns.Count)"
Write-Info "  Non-presettable fields: $($registryFields.Count)"
Write-Info "  Applier branches      : $($applierBranches.Count)"

function Report($label, $items) {
    if ($items.Count -gt 0) {
        Write-Output "  ${label} ($($items.Count)): $($items -join ', ')"
    }
}
Report 'AppSettings fields MISSING from CSV rows' $missingRows
Report 'CSV rows with NO matching AppSettings field' $staleRows
Report 'DeviceProfileTypes MISSING a CSV column' $missingColumns
Report 'CSV columns with NO matching DeviceProfileType' $unknownColumns
Report 'registered non-presettable fields NAMING an unknown AppSettings field' $staleRegistry
Report 'registered non-presettable fields WITHOUT a reason' $registryNoReason
Report 'registered non-presettable fields CARRYING a value' $registeredWithValue
Report 'CSV rows with no applier branch and no registry entry' $rowsWithoutBranch
Report 'CSV rows CARRYING A VALUE with no applier branch' $valuedRowsWithoutBranch
Report 'cells outside the range their Settings screen accepts' $valueViolations
Report "cells in the 'Other' column, which must stay empty" $otherColumnViolations

$hasMissing = ($missingRows.Count + $missingColumns.Count) -gt 0
$hasStale = ($staleRows.Count + $unknownColumns.Count + $staleRegistry.Count) -gt 0
$hasRegistryConflict = ($registryNoReason.Count + $registeredWithValue.Count) -gt 0
$hasApplierGap = ($rowsWithoutBranch.Count + $valuedRowsWithoutBranch.Count) -gt 0
$hasBadValue = ($valueViolations.Count + $otherColumnViolations.Count) -gt 0

if (-not $AddMissing) {
    if ($hasMissing -or $hasStale -or $hasRegistryConflict -or $hasApplierGap -or $hasBadValue) {
        if ($Gate) {
            Write-Output 'check-device-profile-presets: FAIL - see the violations above.'
        }
        else {
            Write-Output ''
            Write-Output 'INCONSISTENT. Every AppSettings field needs either a CSV row or an entry in'
            Write-Output 'docs/settings/device-profile-nonpresettable.json, and every valued row needs an'
            Write-Output 'applier branch. Run with -AddMissing to append missing rows/columns (empty);'
            Write-Output 'stale rows/columns are reported, not auto-removed.'
        }
        exit 1
    }
    if ($Gate) {
        Write-Output 'check-device-profile-presets: PASS (matrix, registry and applier agree).'
    }
    else {
        Write-Info 'OK: matrix, registry and applier agree on every AppSettings field and profile.'
    }
    exit 0
}

# --- -AddMissing: append missing rows/columns (empty), preserve existing data ---
if (-not $hasMissing) {
    Write-Output 'Nothing to add: CSV already covers every presettable field and profile.'
    exit 0
}

$newProfileColumns = $missingColumns | ForEach-Object { EnumToCol $_ }
$allColumns = @('option') + $csvProfileColumns + $newProfileColumns

$rebuilt = foreach ($row in $csvRows) {
    $ordered = [ordered]@{}
    foreach ($c in $allColumns) {
        $ordered[$c] = if ($row.psobject.Properties.Name -contains $c) { $row.$c } else { '' }
    }
    [pscustomobject]$ordered
}
foreach ($field in $missingRows) {
    $ordered = [ordered]@{}
    foreach ($c in $allColumns) { $ordered[$c] = if ($c -eq 'option') { $field } else { '' } }
    $rebuilt += [pscustomobject]$ordered
}

# -UseQuotes Always + utf8NoBOM reproduce the owner-authored format exactly. PowerShell 7 quotes
# only where needed by default, which would silently re-write all 189 existing rows and bury the
# appended ones in a whole-file diff. Strategic 3.2 freezes the matrix format.
$rebuilt | Export-Csv -Path $csvPath -NoTypeInformation -Encoding utf8NoBOM -UseQuotes Always
Write-Output ''
Write-Output "Added $($missingRows.Count) row(s) and $($missingColumns.Count) column(s) to $csvPath (empty cells)."
Write-Output 'Review and fill the new cells, then rebuild.'
exit 0
