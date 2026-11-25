#requires -Version 7.0
<#
.SYNOPSIS
    Generate the machine-readable export of the rows of DEVICE-EXCHANGE item CZ (item FE).
    Manual tool: run by the contract owner when the item CZ rows change in the catalog; no gate or build invokes it.

.DESCRIPTION
    Item FE of the catalog's device-exchange/RECOMMENDATIONS-2026-11-12.md names eleven places that hold the
    rows of item CZ and makes this product, the contract's owner, generate one JSON file of them. This script
    reads each place from the catalog text: a table is cut by its
    `Code | Kind | Step | English | Русский | Українська` header after the place's anchor line, and the two
    rows worded in prose (format-unsupported, the generic refusal) are taken from their sentences.

    The file holds `contract`, `version` (the README contract block), `generator` and `rows`, in the order of
    item FE's table; each row has `code`, `qualifier`, `kind`, `step`, `en`, `ru`, `uk` and `source`. Places
    added after 1.12 (NOTES-SYNC item FL, the remote schedule) are not in the set item FE defines.

    A row is refused when its kind is not one of the five of item CZ, its step is neither `-` nor an id of the
    item CY ladder, or one of its three texts is empty. Output is deterministic: UTF-8 without BOM, LF.

    Exit codes:
      0 - the file was written.
      1 - a place was not found, yielded no row, or a row was refused (each reason is printed).
      2 - could not verify: no catalog root or no device-exchange/README.md inside it.

.PARAMETER OutFile
    Path of the JSON file to write.

.PARAMETER CatalogRoot
    Root of the shared contracts catalog. Falls back to FMS_CONTRACTS_ROOT in the process, then at user scope.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/export-item-cz-rows.ps1 -OutFile temp/scratch/item-cz-rows.json
#>
param(
    [Parameter(Mandatory)] [string] $OutFile,
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$header = '| Code | Kind | Step | English | Русский | Українська |'
$kinds = @('wire', 'cast', 'local', 'state', 'result')

if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) {
    Write-Host 'export-item-cz-rows: COULD NOT VERIFY - no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT'
    exit 2
}
$domain = Join-Path $CatalogRoot 'device-exchange'
$readmePath = Join-Path $domain 'README.md'
if (-not (Test-Path -LiteralPath $readmePath)) {
    Write-Host "export-item-cz-rows: COULD NOT VERIFY - not found: $readmePath"
    exit 2
}

$errors = [System.Collections.Generic.List[string]]::new()
$cache = @{}
function Get-Lines([string] $file) {
    if (-not $cache.ContainsKey($file)) {
        $cache[$file] = [System.IO.File]::ReadAllText((Join-Path $domain $file), $utf8) -split "`r?`n"
    }
    return $cache[$file]
}

function Get-Cells([string] $line) {
    $inner = $line.Trim()
    $inner = $inner.Substring(1, $inner.Length - 2)
    return @($inner -split '\|' | ForEach-Object { $_.Trim() })
}

# Returns a list of string arrays; the unary comma keeps a one-row table from being unrolled into its cells.
function Get-TableAfter([string] $file, [string] $anchor, [string] $tableHeader = $header) {
    $rows = [System.Collections.Generic.List[object]]::new()
    $lines = Get-Lines $file
    $start = 0
    if ($anchor) {
        $start = -1
        for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i].Contains($anchor)) { $start = $i; break } }
        if ($start -lt 0) { $errors.Add("$file - anchor not found: $anchor"); return , $rows }
    }
    $h = -1
    for ($i = $start; $i -lt $lines.Count; $i++) { if ($lines[$i].Trim().StartsWith($tableHeader)) { $h = $i; break } }
    if ($h -lt 0) { $errors.Add("$file - no table '$tableHeader' after: $anchor"); return , $rows }
    for ($i = $h + 2; $i -lt $lines.Count -and $lines[$i].Trim().StartsWith('|'); $i++) {
        $rows.Add([string[]](Get-Cells $lines[$i]))
    }
    return , $rows
}

function Get-StepIds {
    $rows = Get-TableAfter 'README.md' '**CY [NEW]' '| # | Id | Applies |'
    return @($rows | ForEach-Object { $_[1].Trim('`') })
}

function New-Row([string] $code, $qualifier, [string] $kind, [string] $step, [string] $en, [string] $ru, [string] $uk, [string] $source) {
    return [ordered]@{
        code = $code; qualifier = $qualifier; kind = $kind; step = $step
        en = $en; ru = $ru; uk = $uk; source = $source
    }
}

function ConvertFrom-TableRow($cells, [string] $source) {
    $m = [regex]::Match($cells[0], '^`([^`]+)`\s*(.*)$')
    if (-not $m.Success) { $errors.Add("$source - not a code cell: $($cells[0])"); return $null }
    $qualifier = $m.Groups[2].Value.Trim()
    if ($qualifier -eq '') { $qualifier = $null }
    return New-Row $m.Groups[1].Value $qualifier $cells[1] $cells[2].Trim('`') $cells[3] $cells[4] $cells[5] $source
}

$places = @(
    @{ source = 'README.md, item CZ'; file = 'README.md'; anchor = '**CZ [NEW]' }
    @{ source = 'README.md, item DD'; file = 'README.md'; anchor = '**DD [NEW]' }
    @{ source = 'README.md, item DR'; file = 'README.md'; anchor = '**DR [NEW]' }
    @{ source = 'BROADCAST-END-TUNNEL-2026-10-26.md, item DY'; file = 'BROADCAST-END-TUNNEL-2026-10-26.md'; anchor = '' }
    @{ source = 'SETTINGS-STANDARD-2026-11-12.md, item EP'; file = 'SETTINGS-STANDARD-2026-11-12.md'; anchor = '' }
    @{ source = 'RECOMMENDATIONS-2026-11-12.md, item EU'; file = 'RECOMMENDATIONS-2026-11-12.md'; anchor = '## EU [NEW]' }
    @{ source = 'RECOMMENDATIONS-2026-11-12.md, item EV'; file = 'RECOMMENDATIONS-2026-11-12.md'; anchor = '## EV [NEW]' }
    @{ source = 'RECOMMENDATIONS-2026-11-12.md, item FB'; file = 'RECOMMENDATIONS-2026-11-12.md'; anchor = '## FB [NEW]' }
    @{ source = 'WEB-BINDING.md, clipboard'; file = 'WEB-BINDING.md'; anchor = 'Item CZ gains two `local` rows' }
)

$rows = [System.Collections.Generic.List[object]]::new()
foreach ($place in $places) {
    $table = Get-TableAfter $place.file $place.anchor
    if ($table.Count -eq 0) { $errors.Add("$($place.source) - no rows"); continue }
    foreach ($cells in $table) {
        if ($cells.Count -ne 6) { $errors.Add("$($place.source) - row of $($cells.Count) cells"); continue }
        $row = ConvertFrom-TableRow $cells $place.source
        if ($row) { $rows.Add($row) }
    }
}

# The amendment of 2026-10-17 words format-unsupported as three quoted sentences and one code line.
$readme = (Get-Lines 'README.md') -join "`n"
$fu = [regex]::Match($readme, '(?s)canonical local text:\s*\nEN "([^"]+)"\s*\nRU "([^"]+)"\s*\nUK "([^"]+)"\s*\nCode `format-unsupported`, kind (\w+), step `([^`]+)`')
if ($fu.Success) {
    $rows.Add((New-Row 'format-unsupported' $null $fu.Groups[4].Value $fu.Groups[5].Value $fu.Groups[1].Value $fu.Groups[2].Value $fu.Groups[3].Value 'README.md, amendment of 2026-10-17'))
} else {
    $errors.Add('README.md, amendment of 2026-10-17 - the format-unsupported sentences were not found')
}

# Item CZ words the generic refusal in one bullet; it has no code of its own, so the row carries `refused`.
$gr = [regex]::Match($readme, '(?s)\*\*An unknown `refused\.reason`\*\* is shown as a generic refusal \(7\.10\): the English "([^"]+)"\s+with step `([^`]+)`, in Russian «([^»]+)» and in Ukrainian «([^»]+)»')
if ($gr.Success) {
    $rows.Add((New-Row 'refused' 'an unknown refused.reason' 'wire' $gr.Groups[2].Value $gr.Groups[1].Value $gr.Groups[3].Value $gr.Groups[4].Value 'README.md, item CZ, unknown refused.reason'))
} else {
    $errors.Add('README.md, item CZ - the generic refusal bullet was not found')
}

$steps = Get-StepIds
if ($steps.Count -eq 0) { $errors.Add('README.md, item CY - no step ids') }
foreach ($row in $rows) {
    $label = "$($row.source) - $($row.code) $($row.qualifier)".TrimEnd()
    # Item CZ writes one row for a code that is both a wire and a cast reason as `wire, cast`.
    if (@($row.kind -split ',\s*' | Where-Object { $_ -notin $kinds }).Count -gt 0) { $errors.Add("$label - kind '$($row.kind)' is not one of item CZ's five") }
    if ($row.step -ne '-' -and $row.step -notin $steps) { $errors.Add("$label - step '$($row.step)' is not in item CY") }
    foreach ($lang in 'en', 'ru', 'uk') { if ([string]::IsNullOrWhiteSpace($row[$lang])) { $errors.Add("$label - no $lang text") } }
}

$version = [regex]::Match($readme, '(?m)^version:\s*(\S+)').Groups[1].Value
if (-not $version) { $errors.Add('README.md - no version in the contract block') }

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host "  - $_" }
    Write-Host "export-item-cz-rows: FAIL ($($errors.Count))"
    exit 1
}

$doc = [ordered]@{
    contract  = 'DEVICE-EXCHANGE'
    version   = $version
    item      = 'FE'
    generator = 'FastMediaSorter Android, scripts/docs/export-item-cz-rows.ps1'
    rows      = $rows
}
$json = ($doc | ConvertTo-Json -Depth 5) -replace "`r`n", "`n"
$target = [System.IO.Path]::GetFullPath($OutFile, (Get-Location).Path)
$dir = Split-Path -Parent $target
if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
[System.IO.File]::WriteAllText($target, "$json`n", $utf8)
Write-Host "export-item-cz-rows: wrote $($rows.Count) rows against $version to $OutFile"
exit 0
