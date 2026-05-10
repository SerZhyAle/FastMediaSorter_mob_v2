<#
.SYNOPSIS
    Checks that string keys matching a prefix exist in all locale files (EN/RU/UK).

.PARAMETER KeyPrefix
    Prefix to filter string keys (e.g. "local_network_permission").
    Supports wildcards: "*network*"

.PARAMETER Module
    Android module to search (default: app_v2).

.EXAMPLE
    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "local_network_permission"
    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "*permission*"
#>
param(
    [Parameter(Mandatory)]
    [string]$KeyPrefix,
    [string]$Module = "app_v2"
)

$resDir = "$PSScriptRoot\..\$Module\src\main\res"
$locales = @(
    @{ Tag = "EN"; Dir = "values" },
    @{ Tag = "RU"; Dir = "values-ru" },
    @{ Tag = "UK"; Dir = "values-uk" }
)

# Normalize prefix to wildcard pattern
$pattern = if ($KeyPrefix -match '\*') { $KeyPrefix } else { "$KeyPrefix*" }

# Collect keys per locale
$byLocale = @{}
foreach ($loc in $locales) {
    $dir = Join-Path $resDir $loc.Dir
    if (-not (Test-Path $dir)) {
        $byLocale[$loc.Tag] = @()
        Write-Warning "  [$($loc.Tag)] locale dir not found: $dir"
        continue
    }

    $files = Get-ChildItem -Path $dir -Filter "strings*.xml" -File | Sort-Object Name
    if ($files.Count -eq 0) {
        $byLocale[$loc.Tag] = @()
        Write-Warning "  [$($loc.Tag)] no strings*.xml files found in: $dir"
        continue
    }

    $keys = foreach ($file in $files) {
        [xml]$xml = Get-Content $file.FullName -Encoding UTF8
        $xml.resources.string |
        Where-Object { $_.name -like $pattern } |
        ForEach-Object { $_.name }
    }
    $byLocale[$loc.Tag] = @($keys)
}

# Union of all keys
$allKeys = ($byLocale.Values | ForEach-Object { $_ }) | Sort-Object -Unique

if ($allKeys.Count -eq 0) {
    Write-Host "No keys matching '$pattern' found in any locale." -ForegroundColor Yellow
    exit 0
}

# Report
$colW = 48
$header = "{0,-$colW}  {1,-4}  {2,-4}  {3,-4}" -f "Key", "EN", "RU", "UK"
Write-Host ""
Write-Host $header -ForegroundColor Cyan
Write-Host ("-" * ($colW + 20))

$missing = 0
foreach ($key in $allKeys) {
    $cells = foreach ($loc in $locales) {
        if ($byLocale[$loc.Tag] -contains $key) { "OK  " } else { "MISS" }
    }
    $hasMiss = $cells -contains "MISS"
    if ($hasMiss) { $missing++ }
    $line = "{0,-$colW}  {1,-4}  {2,-4}  {3,-4}" -f $key, $cells[0], $cells[1], $cells[2]
    if ($hasMiss) {
        Write-Host $line -ForegroundColor Red
    }
    else {
        Write-Host $line -ForegroundColor Green
    }
}

Write-Host ""
if ($missing -gt 0) {
    Write-Host "FAIL: $missing key(s) missing in at least one locale." -ForegroundColor Red
    exit 1
}
else {
    Write-Host "OK: all $($allKeys.Count) key(s) present in EN/RU/UK." -ForegroundColor Green
    exit 0
}
