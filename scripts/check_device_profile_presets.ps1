<#
.SYNOPSIS
    Consistency guard for the device-profile preset matrix CSV (S0327).

.DESCRIPTION
    Verifies that the owner-authored preset matrix
    `app_v2/src/main/assets/device_profile_presets.csv` stays in sync with the code:

      * every AppSettings field has a matching CSV row (so a new setting is not silently
        forgotten from the matrix);
      * every DeviceProfileType has a matching CSV column (so a new profile gets a preset slot);
      * no CSV row / column references an unknown field / profile.

    Default mode reports mismatches and exits 1 (suitable for a pre-commit / CI gate).
    With -AddMissing it appends the missing field rows and profile columns to the CSV (empty cells =
    "no override"), so the file always covers the current code. Existing values are preserved.

.PARAMETER AddMissing
    Append missing AppSettings field rows and DeviceProfileType columns to the CSV (empty cells),
    then exit 0. Does not modify existing cells, row order, or column order.

.EXAMPLE
    pwsh -NoProfile -File scripts/check_device_profile_presets.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -AddMissing
#>
param(
    [switch]$AddMissing
)

$ErrorActionPreference = 'Stop'
trap { Write-Error $_; exit 1 }

$repo = Split-Path $PSScriptRoot -Parent
$appSettingsPath = Join-Path $repo 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt'
$deviceProfilePath = Join-Path $repo 'app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt'
$csvPath = Join-Path $repo 'app_v2/src/main/assets/device_profile_presets.csv'

foreach ($p in @($appSettingsPath, $deviceProfilePath, $csvPath)) {
    if (-not (Test-Path $p)) { Write-Error "Not found: $p"; exit 1 }
}

# --- AppSettings field names (data class `val name: Type`) ---
$appFields = Select-String -Path $appSettingsPath -Pattern '^\s*val\s+(\w+)\s*:' |
    ForEach-Object { $_.Matches[0].Groups[1].Value }

# --- DeviceProfileType enum values ---
$dpText = Get-Content $deviceProfilePath -Raw
$enumMatch = [regex]::Match($dpText, 'enum\s+class\s+DeviceProfileType\s*\{([^}]*)\}')
if (-not $enumMatch.Success) { Write-Error 'Could not locate enum class DeviceProfileType'; exit 1 }
$enumValues = ($enumMatch.Groups[1].Value -split ',') |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[A-Z0-9_]+$' }

# --- CSV (Import-Csv handles quoted fields) ---
$csvRows = @(Import-Csv -Path $csvPath)
$csvColumns = @($csvRows[0].psobject.Properties.Name)
if ($csvColumns[0] -ne 'option') { Write-Error "First CSV column must be 'option', found '$($csvColumns[0])'"; exit 1 }
$csvProfileColumns = $csvColumns | Where-Object { $_ -ne 'option' }
$csvFields = @($csvRows | ForEach-Object { $_.option })

# Profile column-key <-> enum value (case-insensitive; CSV uses lower_snake, 'Other' = OTHER).
function ColToEnum([string]$col) { return $col.ToUpperInvariant() }
function EnumToCol([string]$e)   { return $e.ToLowerInvariant() }

$csvProfileEnums = $csvProfileColumns | ForEach-Object { ColToEnum $_ }

$missingRows    = $appFields    | Where-Object { $_ -notin $csvFields }
$staleRows      = $csvFields    | Where-Object { $_ -notin $appFields }
$missingColumns = $enumValues   | Where-Object { $_ -notin $csvProfileEnums }
$unknownColumns = $csvProfileColumns | Where-Object { (ColToEnum $_) -notin $enumValues }

Write-Output "Device profile preset matrix check"
Write-Output "  AppSettings fields : $($appFields.Count)"
Write-Output "  CSV rows           : $($csvFields.Count)"
Write-Output "  DeviceProfileTypes : $($enumValues.Count)"
Write-Output "  CSV profile columns: $($csvProfileColumns.Count)"

function Report($label, $items) {
    if ($items.Count -gt 0) {
        Write-Output "  ${label} ($($items.Count)): $($items -join ', ')"
    }
}
Report 'AppSettings fields MISSING from CSV rows' $missingRows
Report 'CSV rows with NO matching AppSettings field' $staleRows
Report 'DeviceProfileTypes MISSING a CSV column' $missingColumns
Report 'CSV columns with NO matching DeviceProfileType' $unknownColumns

$hasMissing = ($missingRows.Count + $missingColumns.Count) -gt 0
$hasStale   = ($staleRows.Count + $unknownColumns.Count) -gt 0

if (-not $AddMissing) {
    if ($hasMissing -or $hasStale) {
        Write-Output ''
        Write-Output 'INCONSISTENT. Run with -AddMissing to append the missing rows/columns (empty),'
        Write-Output 'or remove stale rows/columns by hand. Stale entries are reported, not auto-removed.'
        exit 1
    }
    Write-Output 'OK: CSV matrix covers every AppSettings field and DeviceProfileType.'
    exit 0
}

# --- -AddMissing: append missing rows/columns (empty), preserve existing data ---
if (-not $hasMissing) {
    Write-Output 'Nothing to add: CSV already covers every field and profile.'
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

$rebuilt | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Output ''
Write-Output "Added $($missingRows.Count) row(s) and $($missingColumns.Count) column(s) to $csvPath (empty cells)."
Write-Output 'Review and fill the new cells, then rebuild.'
exit 0
