<#
.SYNOPSIS
    S0440 Phase 04 (widened by S1313) - assert every settings-row layout is classified.

.DESCRIPTION
    Recurses every `res/layout*` directory under `app_v2/src` (all flavors, portrait and
    landscape) and selects each layout whose text contains a real settings-row widget TAG -
    `<...SettingsToggleRow`, `<...SettingsDropdownRow`, `<...SettingsInputRow` or
    `<...SettingsSelectionRow` - matched on the opening `<` so a layout that merely MENTIONS
    one of these class names in a KDoc/XML comment (e.g. the widget's own internal
    `view_settings_toggle_row.xml`) does not false-positive. Portrait/landscape pairs dedupe by
    layout base name.

    Every discovered layout MUST be classified in exactly one place:
      - `SettingsSearchLayoutCatalog.layoutResIds` - navigable in-app search index.
      - `SettingsDocScopeCatalog.surfaces` - documentation-only (S1313); never added to the
        search index (S1035 SS6.6 already ruled dialog-hosted rows out of live search).
      - `docs/settings/settings-scope-exclusions.json` - explicitly excluded, with a reason and
        a category (entity-editor / onboarding / flavor-scoped / row-less-host / session-scoped /
        unsupported-widget).
    A layout in none of the three fails the gate (exit 1), naming the layout and all three ways
    to resolve it. A layout that no longer exists on disk but still has an exclusion entry also
    fails, so the exclusion list cannot rot into referencing dead layouts.

    This closes the S1313 blind spot mechanically: any FUTURE dialog/page that grows a settings
    row now forces a classification decision at gate time, instead of silently landing in no
    catalog at all.

.OUTPUTS
    Exit codes:
      0 - every discovered layout is classified; every exclusion entry matches a real layout.
      1 - at least one discovered layout is unclassified, or an exclusion entry is stale.
      2 - cannot verify: a required file (either catalog `.kt`, or the exclusions `.json`) is
          missing or the exclusions file does not parse as JSON.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$searchDir = Join-Path $RepoRoot 'app_v2/src'
$searchCatalogFile = Join-Path $RepoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt'
$docScopeCatalogFile = Join-Path $RepoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt'
$exclusionsFile = Join-Path $RepoRoot 'docs/settings/settings-scope-exclusions.json'

foreach ($f in @($searchCatalogFile, $docScopeCatalogFile, $exclusionsFile)) {
    if (-not (Test-Path $f)) {
        Write-Error "settings catalog: required file not found: $f" -ErrorAction Continue
        exit 2
    }
}

$exclusions = $null
try {
    $exclusions = Get-Content $exclusionsFile -Raw | ConvertFrom-Json
} catch {
    Write-Error "settings catalog: $exclusionsFile does not parse as JSON - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}
$excludedNames = @($exclusions.PSObject.Properties.Name)

# --- Discover every layout with a real settings-row widget TAG (not a comment mention) --------
$widgetTagPattern = '<[\w.]*\b(?:SettingsToggleRow|SettingsDropdownRow|SettingsInputRow|SettingsSelectionRow)\b'
$layoutFiles = @(Get-ChildItem -Path $searchDir -Recurse -Filter '*.xml' -File |
    Where-Object { $_.DirectoryName -match '[\\/]res[\\/]layout' })
$discovered = @($layoutFiles |
    Where-Object { (Get-Content $_.FullName -Raw) -match $widgetTagPattern } |
    ForEach-Object { $_.BaseName } |
    Sort-Object -Unique)

# --- Classified sets from the two Kotlin catalogs (regex-over-source, no gradle needed) -------
$searchCatalogText = Get-Content $searchCatalogFile -Raw
$searchScoped = @([regex]::Matches($searchCatalogText, 'R\.layout\.(\w+)') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)

$docScopeCatalogText = Get-Content $docScopeCatalogFile -Raw
$docScoped = @([regex]::Matches($docScopeCatalogText, 'R\.layout\.(\w+)') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)

# --- Every discovered layout must land in exactly one bucket ----------------------------------
$unclassified = @($discovered | Where-Object {
    $_ -notin $searchScoped -and $_ -notin $docScoped -and $_ -notin $excludedNames
})

# --- Exclusion entries must still name a real layout (no rot) ---------------------------------
$staleExclusions = @($excludedNames | Where-Object { $_ -notin $discovered })

if ($unclassified.Count -or $staleExclusions.Count) {
    if ($unclassified.Count) {
        Write-Host "settings catalog INCOMPLETE - $($unclassified.Count) layout(s) with settings rows are unclassified:" -ForegroundColor Red
        $unclassified | ForEach-Object {
            Write-Host "  $_  - resolve by ONE of: (1) append R.layout.$_ to SettingsSearchLayoutCatalog.layoutResIds if it must be live-searchable, (2) add a DocScopeSurface to SettingsDocScopeCatalog.surfaces if it is documentation-only, (3) add an entry to docs/settings/settings-scope-exclusions.json with a category and reason if it is deliberately out of scope."
        }
    }
    if ($staleExclusions.Count) {
        Write-Host "settings-scope-exclusions.json has $($staleExclusions.Count) stale entr(y/ies) naming a layout no longer found with a settings-row widget:" -ForegroundColor Red
        $staleExclusions | ForEach-Object { Write-Host "  $_  - remove the entry, or the layout was renamed/deleted and the exclusion should follow." }
    }
    Write-Error "settings catalog: FAIL" -ErrorAction Continue
    exit 1
}

Write-Host "settings catalog: OK - $($discovered.Count) layout(s) with settings rows, all classified ($($searchScoped.Count) search-scope, $($docScoped.Count) doc-scope, $($excludedNames.Count) excluded)." -ForegroundColor Green
exit 0
