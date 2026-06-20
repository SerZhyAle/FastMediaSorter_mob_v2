<#
.SYNOPSIS
    S0440 Phase 04 - assert the settings-search layout catalog is complete.

.DESCRIPTION
    Enumerates app_v2/src/main/res/layout/fragment_settings_*.xml and fails
    (exit 1) if any settings-row layout is absent from
    SettingsSearchLayoutCatalog.layoutResIds. The tab-host container
    fragment_settings_media_container is the only allowed exclusion (it has no
    rows of its own). A layout missing from the catalog would silently drop its
    settings from both the in-app search and the generated docs, so this closes
    the documented leak called out in the catalog's KDoc.

    Exit 0 with a one-line summary when complete.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$layoutDir   = Join-Path $RepoRoot 'app_v2/src/main/res/layout'
$catalogFile = Join-Path $RepoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt'
$allowedExclusions = @('fragment_settings_media_container')

if (-not (Test-Path $catalogFile)) { Write-Host "Catalog not found: $catalogFile" -ForegroundColor Red; exit 1 }

$layoutNames = @(Get-ChildItem -Path $layoutDir -Filter 'fragment_settings_*.xml' |
    ForEach-Object { $_.BaseName })

$catalogText = Get-Content $catalogFile -Raw
$catalogued = @([regex]::Matches($catalogText, 'R\.layout\.(fragment_settings_\w+)') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)

$missing = @($layoutNames | Where-Object { $_ -notin $catalogued -and $_ -notin $allowedExclusions })

if ($missing.Count) {
    Write-Host "settings catalog INCOMPLETE - $($missing.Count) layout(s) with rows not in SettingsSearchLayoutCatalog.layoutResIds:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_  (append R.layout.$_ to SettingsSearchLayoutCatalog, or add to allowed exclusions if it is a row-less host)" }
    exit 1
}

Write-Host "settings catalog: OK - $($catalogued.Count) catalogued, $($allowedExclusions.Count) host exclusion(s), 0 missing." -ForegroundColor Green
exit 0
