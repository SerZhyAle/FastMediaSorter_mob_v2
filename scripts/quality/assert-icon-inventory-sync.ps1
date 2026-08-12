<#
.SYNOPSIS
    S0815 Phase 04 - icon inventory / asset / legend drift gate.

.DESCRIPTION
    One gate that proves the generated icon documentation tree is internally
    consistent and up to date with docs/icons/icon-inventory.json. Checks:

      1. Settings-source freshness (pure PS) - scan source
         app_v2/src/main/res/layout/fragment_settings_*.xml for the same
         settings icon/title pairs that IconInventoryExportTest exports
         (csh_icon/csh_title, str_icon/str_title, ssr_icon/ssr_title), then
         compare that source-derived subset against the committed
         settings-header/settings-row subset in docs/icons/icon-inventory.json.
         This catches silent settings drift without the JVM export test.
      2. Asset coverage (pure PS) - for every inventory entry:
           assetFormat=vector && public   -> docs/icons/svg/<drawable>.svg MUST exist
           assetFormat=raster && public   -> docs/icons/svg/<drawable>.png MUST exist
           assetFormat=framework          -> NO svg may exist (android.R.drawable.*
                                             has no local asset)
      3. Orphan assets (pure PS) - every docs/icons/svg/*.svg MUST map back to a
         vector && public inventory drawable, so a stale svg left after an icon was
         removed or made private is caught.
      4. Legend freshness (pure PS) - re-render the trilingual legend to a temp dir
         via render-icon-legend.ps1 -OutDir <temp> and byte-diff each committed
         docs/ICON_LEGEND*.md. Any drift fails with a "re-run the renderer" hint.
      5. Cross-locale parity (pure PS) - the three legend files must carry the same
         number of table data rows and the same ordered sequence of icon cells
         (svg src basename, or a framework "system icon" marker). Modelled on the
         positional-signature compare in assert-howto-settings-paths.ps1, so a row
         added / removed / reordered / repointed in one language only is caught.
      6. Inventory freshness (opt-in, heavy) - only with -IncludeExportTest: run the
         Robolectric IconInventoryExportTest in assert mode to prove the committed
         inventory still matches the live app registries. Without the flag it prints
         a one-line advisory and does NOT fail - the unit-test source set may not
         compile on a dirty dev tree (unrelated WIP), so the JVM check is CI/opt-in.

    Checks 1-5 are pure text/file analysis (no JVM/gradle) so a doc edit stays
    fast. Run with -Gate from post-change.ps1; the switch is cosmetic so the call
    site reads intentionally.

    -RegenerateInventory (S1402) runs the same export test in generate mode under
    BUILD.LOCK and rewrites docs/icons/icon-inventory.json, then exits without
    running checks 1-5: it is the fix the freshness failure names, and before it
    existed the only route was a bare gradlew call, which takes no lock (Rule 23).

    Exit codes:
      0 - every enforced check passed, or the inventory was regenerated.
      1 - a check failed.
      2 - regeneration could not run (gradle returned non-zero).
#>
param(
    [switch] $Gate,
    [switch] $IncludeExportTest,
    [switch] $RegenerateInventory,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $RepoRoot "scripts/utils/agent-lock.ps1")

$invPath = Join-Path $RepoRoot 'docs/icons/icon-inventory.json'
$svgDir  = Join-Path $RepoRoot 'docs/icons/svg'
$renderer = Join-Path $RepoRoot 'scripts/docs/render-icon-legend.ps1'
$legendFiles = [ordered]@{ en = 'ICON_LEGEND.md'; ru = 'ICON_LEGEND_RU.md'; uk = 'ICON_LEGEND_UK.md' }
$resBase = Join-Path $RepoRoot 'app_v2/src/main/res'
$layoutDir = Join-Path $resBase 'layout'
$drawableDirs = @(Get-ChildItem -Path $resBase -Directory | Where-Object { $_.Name -like 'drawable*' })
$androidNs = 'http://schemas.android.com/apk/res/android'
$appNs = 'http://schemas.android.com/apk/res-auto'
$settingsSurfaces = @('settings-header', 'settings-row')
$vrDrawable = 'ic_vr_headset'

if (-not (Test-Path $invPath))  { Write-Host "icon-inventory-sync: inventory not found: $invPath" -ForegroundColor Red; exit 1 }
if (-not (Test-Path $renderer)) { Write-Host "icon-inventory-sync: renderer not found: $renderer" -ForegroundColor Red; exit 1 }

$failures = New-Object System.Collections.Generic.List[string]
function Add-Fail([string] $stage, [string] $msg) { [void]$failures.Add("[$stage] $msg") }

$inventory = Get-Content $invPath -Raw | ConvertFrom-Json

function Get-RefName([string] $rawValue) {
    if ([string]::IsNullOrWhiteSpace($rawValue)) { return $null }
    if ($rawValue -match '^@\+?id/(?<name>[^/]+)$') { return $matches['name'] }
    if ($rawValue -match '^@id/(?<name>[^/]+)$') { return $matches['name'] }
    if ($rawValue -match '^@string/(?<name>[^/]+)$') { return $matches['name'] }
    if ($rawValue -match '^@drawable/(?<name>[^/]+)$') { return $matches['name'] }
    if ($rawValue -match '^@android:drawable/(?<name>[^/]+)$') { return $matches['name'] }
    return $null
}

function Get-AssetFormat([string] $rawIconRef, [string] $drawableName) {
    if ($rawIconRef -like '@android:drawable/*') { return 'framework' }
    foreach ($dir in $drawableDirs) {
        if (Test-Path (Join-Path $dir.FullName "$drawableName.xml")) { return 'vector' }
    }
    foreach ($ext in @('png', 'webp', 'jpg', 'jpeg')) {
        foreach ($dir in $drawableDirs) {
            if (Test-Path (Join-Path $dir.FullName "$drawableName.$ext")) { return 'raster' }
        }
    }
    return 'vector'
}

function New-SettingsInventoryEntry(
    [System.Xml.XmlNode] $Node,
    [string] $IconAttr,
    [string] $TitleAttr,
    [string] $Surface
) {
    $rawIcon = $Node.GetAttribute($IconAttr, $appNs)
    $drawable = Get-RefName $rawIcon
    if ([string]::IsNullOrWhiteSpace($drawable)) { return $null }

    $viewId = Get-RefName ($Node.GetAttribute('id', $androidNs))
    $titleName = Get-RefName ($Node.GetAttribute($TitleAttr, $appNs))
    $feature = if ($titleName) { $titleName } elseif ($viewId) { $viewId } else { $IconAttr }
    $key = if ($viewId) { $viewId } else { $feature }

    [pscustomobject][ordered]@{
        surface = $Surface
        key = $key
        feature = $feature
        drawable = $drawable
        assetFormat = Get-AssetFormat -rawIconRef $rawIcon -drawableName $drawable
        public = ($drawable -ne $vrDrawable)
    }
}

function Get-SettingsSourceEntries() {
    $entries = New-Object System.Collections.Generic.List[object]
    if (-not (Test-Path $layoutDir)) { return @() }

    $layoutFiles = @(Get-ChildItem -Path $layoutDir -Filter 'fragment_settings_*.xml' -File | Sort-Object Name)
    foreach ($layoutFile in $layoutFiles) {
        $doc = New-Object System.Xml.XmlDocument
        $doc.Load($layoutFile.FullName)
        foreach ($node in $doc.SelectNodes('descendant-or-self::*')) {
            if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
            foreach ($spec in @(
                    @{ icon = 'csh_icon'; title = 'csh_title'; surface = 'settings-header' }
                    @{ icon = 'str_icon'; title = 'str_title'; surface = 'settings-row' }
                    @{ icon = 'ssr_icon'; title = 'ssr_title'; surface = 'settings-row' }
                )) {
                $entry = New-SettingsInventoryEntry -Node $node -IconAttr $spec.icon -TitleAttr $spec.title -Surface $spec.surface
                if ($null -ne $entry) {
                    [void]$entries.Add($entry)
                }
            }
        }
    }

    return @($entries.ToArray())
}

function Get-EntrySignature($entry) {
    $isPublic = if ($entry.public) { 'true' } else { 'false' }
    return "$($entry.surface)|$($entry.key)|$($entry.feature)|$($entry.drawable)|$($entry.assetFormat)|$isPublic"
}

$inventorySettings = @(
    $inventory |
        Where-Object { $_.surface -in $settingsSurfaces } |
        ForEach-Object { Get-EntrySignature $_ } |
        Sort-Object -Unique
)
$sourceSettings = @(
    Get-SettingsSourceEntries |
        ForEach-Object { Get-EntrySignature $_ } |
        Sort-Object -Unique
)

$missingSettings = @($sourceSettings | Where-Object { $_ -notin $inventorySettings })
$staleSettings = @($inventorySettings | Where-Object { $_ -notin $sourceSettings })
if ($missingSettings.Count -gt 0 -or $staleSettings.Count -gt 0) {
    if ($missingSettings.Count -gt 0) {
        Add-Fail 'settings-source-fresh' "inventory missing source entry '$($missingSettings[0])' - regenerate docs/icons/icon-inventory.json via IconInventoryExportTest generate mode"
    }
    if ($staleSettings.Count -gt 0) {
        Add-Fail 'settings-source-fresh' "inventory still carries stale settings entry '$($staleSettings[0])' - regenerate docs/icons/icon-inventory.json via IconInventoryExportTest generate mode"
    }
}

# Distinct drawable buckets. A drawable is reused across many entries (e.g. one
# feature per surface), so dedup to distinct names before comparing against assets.
$expectedSvg = @(@($inventory | Where-Object { $_.assetFormat -eq 'vector' -and $_.public }) | ForEach-Object { $_.drawable } | Sort-Object -Unique)
$expectedPng = @(@($inventory | Where-Object { $_.assetFormat -eq 'raster' -and $_.public }) | ForEach-Object { $_.drawable } | Sort-Object -Unique)
$frameworkSet = @(@($inventory | Where-Object { $_.assetFormat -eq 'framework' }) | ForEach-Object { $_.drawable } | Sort-Object -Unique)

# --- Check 2: asset coverage --------------------------------------------------
foreach ($d in $expectedSvg) {
    if (-not (Test-Path (Join-Path $svgDir "$d.svg"))) {
        Add-Fail 'asset-coverage' "vector&&public icon '$d' has no docs/icons/svg/$d.svg - run: pwsh -NoProfile -File scripts/docs/export-icon-svgs.ps1"
    }
}
foreach ($d in $expectedPng) {
    if (-not (Test-Path (Join-Path $svgDir "$d.png"))) {
        Add-Fail 'asset-coverage' "raster&&public icon '$d' has no docs/icons/svg/$d.png - run: pwsh -NoProfile -File scripts/docs/export-icon-svgs.ps1"
    }
}
foreach ($d in $frameworkSet) {
    if (Test-Path (Join-Path $svgDir "$d.svg")) {
        Add-Fail 'asset-coverage' "framework icon '$d' must NOT have an svg but docs/icons/svg/$d.svg exists (framework icons are android.R.drawable.*, no local asset)"
    }
}

# --- Check 3: orphan svg assets ----------------------------------------------
$onDiskSvg = @(Get-ChildItem -LiteralPath $svgDir -Filter '*.svg' -File -ErrorAction SilentlyContinue | ForEach-Object { $_.BaseName })
foreach ($s in $onDiskSvg) {
    if ($s -notin $expectedSvg) {
        Add-Fail 'orphan-asset' "docs/icons/svg/$s.svg has no vector&&public inventory drawable (stale) - re-run: pwsh -NoProfile -File scripts/docs/export-icon-svgs.ps1"
    }
}

# --- Check 4: legend freshness (re-render to temp + byte-diff) -----------------
$tmp = Join-Path $RepoRoot 'temp/_icon_legend_gate'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
    $renderExit = 1
    try {
        # Suppress the renderer's own Write-Host/Warning chatter (streams 3-6); keep
        # errors (2) visible. A terminating render error propagates to the catch.
        & $renderer -RepoRoot $RepoRoot -OutDir $tmp 3>$null 4>$null 5>$null 6>$null | Out-Null
        $renderExit = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    }
    catch {
        Add-Fail 'legend-fresh' "renderer threw: $($_.Exception.Message)"
    }
    if ($renderExit -ne 0) {
        Add-Fail 'legend-fresh' 'render-icon-legend.ps1 returned non-zero'
    }
    else {
        foreach ($f in $legendFiles.Values) {
            $committed = Join-Path $RepoRoot "docs/$f"
            $fresh     = Join-Path $tmp $f
            if (-not (Test-Path $committed)) {
                Add-Fail 'legend-fresh' "committed docs/$f is missing - run: pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1"
            }
            elseif (-not (Test-Path $fresh)) {
                Add-Fail 'legend-fresh' "renderer did not produce $f"
            }
            elseif ([System.IO.File]::ReadAllText($committed) -ne [System.IO.File]::ReadAllText($fresh)) {
                Add-Fail 'legend-fresh' "docs/$f is stale - run: pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1"
            }
        }
    }
}
finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}

# --- Check 5: cross-locale parity (positional signature compare) --------------
# Row signature is locale-independent: 'svg:<drawable>' for an icon row, 'sys' for
# a framework/system-icon row. Header + separator table lines are excluded so only
# the cell TEXT differs by locale, never the structure.
function Get-LegendRowSignatures([string] $path) {
    $lines  = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
    $sepIdx = New-Object 'System.Collections.Generic.HashSet[int]'
    for ($i = 0; $i -lt $lines.Count; $i++) {
        # A separator is a pipe row made only of dashes/colons/spaces (e.g. |---|---|).
        if ($lines[$i] -match '^\|[\s:\-\|]+$' -and $lines[$i].Contains('-')) { [void]$sepIdx.Add($i) }
    }
    $headerIdx = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($s in $sepIdx) { if ($s -ge 1) { [void]$headerIdx.Add($s - 1) } }  # header is the row above its separator

    $sigs = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch '^\|') { continue }
        if ($sepIdx.Contains($i) -or $headerIdx.Contains($i)) { continue }
        $m = [regex]::Match($lines[$i], 'src="icons/svg/([^"]+)\.svg"')
        if ($m.Success) { $sigs.Add('svg:' + $m.Groups[1].Value) } else { $sigs.Add('sys') }
    }
    return , $sigs
}

$sigs = @{}
$parityReadable = $true
foreach ($loc in $legendFiles.Keys) {
    $p = Join-Path $RepoRoot "docs/$($legendFiles[$loc])"
    if (-not (Test-Path $p)) { Add-Fail 'locale-parity' "missing legend file: docs/$($legendFiles[$loc])"; $parityReadable = $false; continue }
    $sigs[$loc] = Get-LegendRowSignatures $p
}
if ($parityReadable) {
    $cEn = $sigs['en'].Count; $cRu = $sigs['ru'].Count; $cUk = $sigs['uk'].Count
    if (-not ($cEn -eq $cRu -and $cEn -eq $cUk)) {
        Add-Fail 'locale-parity' "data-row counts differ - en=$cEn ru=$cRu uk=$cUk (re-run render-icon-legend.ps1)"
    }
    else {
        for ($i = 0; $i -lt $cEn; $i++) {
            if (-not ($sigs['en'][$i] -ceq $sigs['ru'][$i] -and $sigs['en'][$i] -ceq $sigs['uk'][$i])) {
                # First mismatch is enough: a reorder shifts every following row, so
                # listing them all would be noise. Point at the diverging row only.
                Add-Fail 'locale-parity' "row #$($i + 1) icon mismatch - en='$($sigs['en'][$i])' ru='$($sigs['ru'][$i])' uk='$($sigs['uk'][$i])' (re-run render-icon-legend.ps1)"
                break
            }
        }
    }
}

# --- Check 6: inventory-vs-source freshness (opt-in, JVM) ----------------------
if ($RegenerateInventory) {
    # S1402: the freshness check names the fix (-Dicon.inventory.generate=true) but nothing here could
    # run it, so the only route was a bare gradlew call - which Rule 23 forbids because it takes no
    # BUILD.LOCK. The regeneration belongs to the gate that detects the staleness.
    Enter-BuildLockOrExit -Reason "assert-icon-inventory-sync.ps1 (IconInventoryExportTest, generate mode)"
    Push-Location $RepoRoot
    try {
        & '.\gradlew.bat' ':app_v2:testStandardDebugUnitTest' '--tests' '*IconInventoryExportTest' `
            '-Dicon.inventory.generate=true' | Out-Null
        $genExit = $LASTEXITCODE
    }
    finally { Pop-Location; Exit-AgentLock -Name Build }
    if ($genExit -ne 0) {
        Write-Error "assert-icon-inventory-sync: regeneration run failed (gradle exit $genExit)" -ErrorAction Continue
        exit 2
    }
    Write-Host 'icon-inventory-sync: docs/icons/icon-inventory.json regenerated - review and commit it.' -ForegroundColor Green
    exit 0
}

if ($IncludeExportTest) {
    Enter-BuildLockOrExit -Reason "assert-icon-inventory-sync.ps1 (IconInventoryExportTest)"
    Push-Location $RepoRoot
    try {
        & '.\gradlew.bat' ':app_v2:testStandardDebugUnitTest' '--tests' '*IconInventoryExportTest' | Out-Null
        $testExit = $LASTEXITCODE
    }
    finally { Pop-Location; Exit-AgentLock -Name Build }
    if ($testExit -ne 0) {
        Add-Fail 'inventory-fresh' 'committed docs/icons/icon-inventory.json differs from the live app registries - regenerate with the export test in generate mode (-Dicon.inventory.generate=true)'
    }
}
else {
    Write-Host 'icon-inventory-sync: inventory-vs-source freshness skipped (pass -IncludeExportTest / enforced in CI)' -ForegroundColor DarkGray
}

# --- Verdict ------------------------------------------------------------------
if ($failures.Count -gt 0) {
    Write-Host "assert-icon-inventory-sync: FAIL ($($failures.Count) issue(s))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "assert-icon-inventory-sync: PASS - $($expectedSvg.Count) vector svg(s) present, no orphans, legend fresh, locales in parity." -ForegroundColor Green
exit 0
