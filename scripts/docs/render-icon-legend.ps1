<#
.SYNOPSIS
    S0815 Phase 03 - render the trilingual icon-legend pages.

.DESCRIPTION
    Merges docs/icons/icon-inventory.json (the public icon set) with the app's
    OWN trilingual string table and the hand-authored sidecar
    docs/icons/icon-annotations.json (surface headings + resource-type / override
    labels), then emits one Markdown legend per locale:
      docs/ICON_LEGEND.md (en) / _RU.md (ru) / _UK.md (uk)

    Meaning resolution per locale L (first hit wins):
      overrides[key][L] -> resourceTypes[feature][L] (feature is an enum name)
      -> strings[L][feature] -> strings[en][feature] (last resort) -> humanized
      drawable + WARNING. This keeps meanings drift-free (ADR-1): they ARE the
      icon's live on-screen label, sourced from the app, not a second copy.

    String source: the FULL merged R.string namespace, i.e. EVERY values*/*.xml
    (strings.xml + strings_*.xml), not strings.xml alone. Android compiles all of
    them into one R.string table, and the inventory 'feature' field is an
    R.string entry name - so ~35 rows whose feature lives in a split file
    (strings_settings/reader/ocr/... .xml) only resolve when the whole table is
    read. Parsing strings.xml alone would leave 23 features on the humanized
    fallback; reading the merged table resolves all 117 rows.

    Output is deterministic (fixed section order, key-sorted rows via ordinal
    compare, LF newlines, single trailing newline, UTF-8 no BOM) so the Phase 04
    gate can re-render and byte-diff to detect drift. Generated - never hand-edited.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $OutDir
)

$ErrorActionPreference = 'Stop'
if (-not $OutDir) { $OutDir = Join-Path $RepoRoot 'docs' }

$invPath = Join-Path $RepoRoot 'docs/icons/icon-inventory.json'
$annPath = Join-Path $RepoRoot 'docs/icons/icon-annotations.json'
$svgDir  = Join-Path $RepoRoot 'docs/icons/svg'
$resBase = Join-Path $RepoRoot 'app_v2/src/main/res'

$inventory = Get-Content $invPath -Raw | ConvertFrom-Json
$ann       = Get-Content $annPath -Raw | ConvertFrom-Json

# --- Merged string table (all values*/*.xml -> one key->value map) --------------
$stringRx = [regex]::new('<string\s[^>]*?name="([^"]+)"[^>]*>(.*?)</string>', 'Singleline')

function ConvertFrom-AndroidString([string] $raw) {
    if ($null -eq $raw) { return '' }
    $s = $raw
    $s = $s -replace "\\'", "'"        # \' -> '
    $s = $s -replace '\\"', '"'        # \" -> "
    $s = $s -replace '&lt;', '<'
    $s = $s -replace '&gt;', '>'
    $s = $s -replace '&#160;', ' '
    $s = $s -replace ([char]0x00A0), ' '  # literal NBSP
    $s = $s -replace '&amp;', '&'         # last, so &amp;lt; stays &lt;
    return $s.Trim()
}

function Read-StringTable([string] $valuesDir) {
    $map = @{}
    if (-not (Test-Path $valuesDir)) { return $map }
    Get-ChildItem -Path $valuesDir -Filter '*.xml' | Sort-Object Name | ForEach-Object {
        $txt = [System.IO.File]::ReadAllText($_.FullName)
        # Drop XML comments so a commented-out <string> cannot pollute the table.
        $txt = [regex]::Replace($txt, '<!--.*?-->', '', 'Singleline')
        foreach ($m in $stringRx.Matches($txt)) {
            $k = $m.Groups[1].Value
            # aapt forbids duplicate names in one config, so first-wins is a no-op
            # for values but keeps the map deterministic regardless of file order.
            if (-not $map.ContainsKey($k)) { $map[$k] = ConvertFrom-AndroidString $m.Groups[2].Value }
        }
    }
    return $map
}

$maps = @{
    en = Read-StringTable (Join-Path $resBase 'values')
    ru = Read-StringTable (Join-Path $resBase 'values-ru')
    uk = Read-StringTable (Join-Path $resBase 'values-uk')
}

# --- Locale-independent constants ----------------------------------------------
$locales      = @('en', 'ru', 'uk')
$sectionOrder = @('program-nav', 'settings-header', 'settings-row', 'player-command', 'send-to')
$resTypeNames = @($ann.resourceTypes.PSObject.Properties.Name)
$overrideKeys = @($ann.overrides.PSObject.Properties.Name)

$pageTitle = @{ en = 'Icon legend';  ru = 'Легенда значков';  uk = 'Легенда значків' }
$colIcon   = @{ en = 'Icon';         ru = 'Значок';           uk = 'Значок' }
$colMean   = @{ en = 'Meaning';      ru = 'Значение';         uk = 'Значення' }
$sysIcon   = @{ en = '(system icon)'; ru = '(системный значок)'; uk = '(системний значок)' }
$intro = @{
    en = 'These are the real interface icons from FastMediaSorter, each shown next to the exact label it displays on screen.'
    ru = 'Это настоящие значки интерфейса FastMediaSorter, и рядом с каждым - точная подпись, которую он показывает на экране.'
    uk = 'Це справжні значки інтерфейсу FastMediaSorter, і поряд із кожним - точний підпис, який він показує на екрані.'
}

function Get-HumanizedDrawable([string] $drawable) {
    $s = ($drawable -replace '^ic_', '') -replace '_', ' '
    $words = $s -split ' ' | ForEach-Object {
        if ($_.Length -eq 0) { '' } else { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }
    }
    return ($words -join ' ')
}

# --- Resolve every public entry across all locales once (single warning pass) ---
$publicEntries = @($inventory | Where-Object { $_.public -eq $true })
$resolved = foreach ($e in $publicEntries) {
    $row = [ordered]@{
        key = $e.key; surface = $e.surface; feature = $e.feature
        drawable = $e.drawable; assetFormat = $e.assetFormat
    }
    foreach ($L in $locales) {
        $text = $null; $fb = ''
        if ($overrideKeys -contains $e.key -and
            ($ann.overrides.($e.key).PSObject.Properties.Name -contains $L) -and
            $ann.overrides.($e.key).$L) {
            $text = $ann.overrides.($e.key).$L
        }
        elseif ($resTypeNames -contains $e.feature) {
            $text = $ann.resourceTypes.($e.feature).$L
        }
        elseif ($maps[$L].ContainsKey($e.feature)) {
            $text = $maps[$L][$e.feature]
        }
        elseif ($maps['en'].ContainsKey($e.feature)) {
            $text = $maps['en'][$e.feature]; $fb = 'en'
        }
        else {
            $text = Get-HumanizedDrawable $e.drawable; $fb = 'humanized'
        }
        $row["m_$L"]  = $text
        $row["fb_$L"] = $fb
    }
    [pscustomobject]$row
}
$resolved = @($resolved)

# --- Render one locale ----------------------------------------------------------
function Format-Cell([string] $s) { if ($null -eq $s) { return '' } ($s -replace '\|', '\|') }

function Sort-ByKeyOrdinal($rows) {
    $list = [System.Collections.Generic.List[object]]::new()
    foreach ($r in $rows) { $list.Add($r) }
    $list.Sort([System.Comparison[object]] { param($a, $b) [string]::CompareOrdinal([string]$a.key, [string]$b.key) })
    return $list
}

function Render-Legend([string] $locale, [string] $permalinkName) {
    $sb = [System.Text.StringBuilder]::new()
    # Jekyll front matter must be the very first bytes so GitHub Pages renders HTML.
    [void]$sb.Append("---`n")
    [void]$sb.Append("layout: default`n")
    [void]$sb.Append("title: `"$($pageTitle[$locale])`"`n")
    [void]$sb.Append("permalink: /docs/$permalinkName.html`n")
    [void]$sb.Append("---`n")
    [void]$sb.Append("<!-- GENERATED by scripts/docs/render-icon-legend.ps1 from docs/icons/icon-inventory.json + icon-annotations.json + app strings. Do not edit by hand. -->`n")
    [void]$sb.Append("`n# $($pageTitle[$locale])`n")
    [void]$sb.Append("`n$($intro[$locale])`n")

    foreach ($surface in $sectionOrder) {
        $rows = @($resolved | Where-Object { $_.surface -eq $surface })
        if (-not $rows.Count) { continue }
        $sorted = Sort-ByKeyOrdinal $rows
        [void]$sb.Append("`n## $($ann.surfaces.$surface.$locale)`n")
        [void]$sb.Append("`n| $($colIcon[$locale]) | $($colMean[$locale]) |`n")
        [void]$sb.Append("|---|---|`n")
        foreach ($r in $sorted) {
            if ($r.assetFormat -eq 'framework') {
                $iconCell = $sysIcon[$locale]
            } else {
                $iconCell = '<img src="icons/svg/' + $r.drawable + '.svg" alt="' + $r.drawable + '" width="24" height="24">'
            }
            [void]$sb.Append("| $iconCell | $(Format-Cell $r."m_$locale") |`n")
        }
    }

    $out = $sb.ToString()
    # Exactly one trailing LF for a stable byte-diff.
    return ($out.TrimEnd("`r", "`n") + "`n")
}

# --- Write files ----------------------------------------------------------------
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$targets = @(
    @{ locale = 'en'; permalink = 'ICON_LEGEND';    file = 'ICON_LEGEND.md' }
    @{ locale = 'ru'; permalink = 'ICON_LEGEND_RU'; file = 'ICON_LEGEND_RU.md' }
    @{ locale = 'uk'; permalink = 'ICON_LEGEND_UK'; file = 'ICON_LEGEND_UK.md' }
)
foreach ($t in $targets) {
    $content = Render-Legend $t.locale $t.permalink
    $path = Join-Path $OutDir $t.file
    [System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
    $rowCount = ($content -split "`n" | Where-Object { $_ -match '^\| ' -and $_ -notmatch '^\| ---' -and $_ -notmatch "^\| $([regex]::Escape($colIcon[$t.locale])) " }).Count
    Write-Host "rendered -> $path  ($rowCount data rows)"
}

# --- Verification summary (advisory; the Phase 04 gate is the enforcement layer) -
Write-Host ''
Write-Host 'Section row counts (public entries):'
foreach ($surface in $sectionOrder) {
    $c = @($resolved | Where-Object { $_.surface -eq $surface }).Count
    Write-Host ("  {0,-16} {1}" -f $surface, $c)
}
Write-Host ("  {0,-16} {1}" -f 'TOTAL', $resolved.Count)

$vectorRows    = @($resolved | Where-Object { $_.assetFormat -ne 'framework' })
$frameworkRows = @($resolved | Where-Object { $_.assetFormat -eq 'framework' })
Write-Host ''
Write-Host "Vector rows: $($vectorRows.Count)   Framework rows: $($frameworkRows.Count)"

# Broken-image check: every vector drawable must have an exported SVG.
$missingSvg = @()
foreach ($r in $vectorRows) {
    if (-not (Test-Path (Join-Path $svgDir "$($r.drawable).svg"))) { $missingSvg += $r.drawable }
}
$missingSvg = @($missingSvg | Sort-Object -Unique)
if ($missingSvg.Count) {
    Write-Host "WARNING: $($missingSvg.Count) vector drawable(s) have no SVG:"
    $missingSvg | ForEach-Object { Write-Host "  - $_" }
} else {
    Write-Host "SVG link check: OK (all $($vectorRows.Count) vector rows resolve to an existing docs/icons/svg/*.svg)"
}

# Humanized-fallback coverage gaps (candidates for annotations.overrides).
$humanized = @($resolved | Where-Object { $_.fb_en -eq 'humanized' -or $_.fb_ru -eq 'humanized' -or $_.fb_uk -eq 'humanized' })
$enLastResort = @($resolved | Where-Object { $_.fb_ru -eq 'en' -or $_.fb_uk -eq 'en' })
Write-Host ''
if ($humanized.Count) {
    Write-Host "WARNING: $($humanized.Count) entr(y/ies) fell through to the humanized-drawable fallback (add to overrides):"
    foreach ($h in ($humanized | Sort-Object { $_.key })) {
        Write-Host ("  - key='{0}' surface='{1}' feature='{2}' drawable='{3}'" -f $h.key, $h.surface, $h.feature, $h.drawable)
    }
} else {
    Write-Host 'Humanized-fallback warnings: none (every feature resolved to a real app string or sidecar label).'
}
if ($enLastResort.Count) {
    Write-Host "Note: $($enLastResort.Count) entr(y/ies) missing a RU/UK string, fell back to EN:"
    foreach ($h in ($enLastResort | Sort-Object { $_.key })) {
        Write-Host ("  - key='{0}' feature='{1}'" -f $h.key, $h.feature)
    }
}

exit 0
