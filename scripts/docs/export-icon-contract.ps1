#requires -Version 7.0
<#
.SYNOPSIS
    Regenerate the conformance artifacts of the ICON-SET contract from its vocabulary.

.DESCRIPTION
    The shared contracts catalog keeps the portfolio icon vocabulary in
    iconography/vocabulary.jsonl - one meaning per line, hand-amended like any contract text.
    This product is the reference implementation, so the glyph of every meaning is exported from
    the drawable the record names in `ref.android`, or drawn from the record's own `glyph.svg`
    where no product has the glyph yet. The script writes, inside iconography/:

      glyphs/<id>.svg            one glyph per meaning (and <id>--<state>.svg, <id>--level-N.svg,
                                 <id>--<variant>.svg for the extra forms a record declares);
                                 a raster reference (a bundled third-party mark) is copied as .png
      looks/<id>.decorated.svg   the decorated look of every record that lists it: the glyph on a
                                 plate of its hue (ICON-RENDER 0.10 section 10, items B and E)
      palette.json               every hue key with its day and night tone, plate, on-plate colour
                                 and contrast ratios, read from this product's colour resources
      CATALOG.md                 the vocabulary as a reading page, grouped, with every glyph and its
                                 looks, closed by the style report: every exported glyph measured
                                 against section 10 item A, departures named with the reason this
                                 product declares in scripts/quality/icon-style-exceptions.txt
      gallery.html               every glyph at three sizes on the light, dark and six accent
                                 themes of this product, with right-to-left mirroring and the three
                                 looks shown

    Every artifact is generated - never hand-edit one; amend vocabulary.jsonl and rerun.
    Output is deterministic (LF, UTF-8 without BOM, fixed order) so a rerun is byte-identical.

    Glyphs take `currentColor` for every paint, so each implementation colours them from its own
    theme role; records whose colour role is `brand` or `fixed` keep their literal colours.

    The catalog is found through -CatalogRoot, falling back to $env:FMS_CONTRACTS_ROOT. A clone
    without the catalog is the ordinary case, so that is "could not verify", not a failure.

    Exit codes:
      0 - every artifact was written and the vocabulary is consistent.
      1 - the vocabulary is inconsistent (duplicate id, dangling reference, unknown enum value) or
          a drawable it names is missing or cannot be converted; nothing is pruned.
      2 - could not verify: no catalog root, no iconography/vocabulary.jsonl inside it, or no repo
          venv to measure the style report with (scripts/docs/lib/requirements.txt).
#>
param(
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Stop-Verdict([int] $code, [string] $word, [string[]] $reasons) {
    foreach ($r in $reasons) { Write-Host ('  - ' + $r) }
    Write-Host ('export-icon-contract: ' + $word)
    exit $code
}

if ([string]::IsNullOrWhiteSpace($CatalogRoot)) {
    Stop-Verdict 2 'COULD NOT VERIFY' @('no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT (CLAUDE.md names the location)')
}
$domain = Join-Path $CatalogRoot 'iconography'
$vocabPath = Join-Path $domain 'vocabulary.jsonl'
if (-not (Test-Path -LiteralPath $vocabPath)) {
    Stop-Verdict 2 'COULD NOT VERIFY' @("vocabulary not found: $vocabPath")
}

# ------------------------------------------------------------------ colour resources and VectorDrawable -> SVG
. (Join-Path $PSScriptRoot 'lib/icon-contract-svg.ps1')
. (Join-Path $PSScriptRoot 'lib/icon-style-rules.ps1')
. (Join-Path $PSScriptRoot 'lib/icon-contract-looks.ps1')
Initialize-IconColourMaps $RepoRoot
if (-not (Get-StylePython $RepoRoot)) {
    Stop-Verdict 2 'COULD NOT VERIFY' @('no repo venv (.venv): the style report measures glyphs with scripts/docs/lib/measure_glyph_style.py - provision it from scripts/docs/lib/requirements.txt')
}

# ------------------------------------------------------------------ vocabulary
$groupsOrder = @('navigation', 'media', 'view', 'action', 'tool', 'content', 'source', 'status', 'app', 'system',
    'feature', 'weather', 'device', 'camera', 'brand')
$statusSet = @('active', 'proposed', 'deprecated')
$rtlSet = @('mirror', 'fixed')
$colourSet = @('content', 'accent', 'category', 'state', 'brand', 'fixed')

$records = New-Object System.Collections.Generic.List[object]
$problems = New-Object System.Collections.Generic.List[string]
$lineNo = 0
foreach ($line in [System.IO.File]::ReadAllLines($vocabPath, $utf8)) {
    $lineNo++
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $records.Add(($line | ConvertFrom-Json -AsHashtable)) }
    catch { $problems.Add("vocabulary.jsonl:$lineNo is not valid JSON") }
}
$ids = @{}
foreach ($r in $records) {
    $id = $r['id']
    if ($ids.ContainsKey($id)) { $problems.Add("duplicate id $id") } else { $ids[$id] = $r }
    if ($id -notmatch '^[a-z]+\.[a-z0-9-]+$') { $problems.Add("$id - id is not group.kebab-name") }
    elseif ($id.Split('.')[0] -ne $r['group'] -and -not ($r['group'] -eq 'navigation' -and $id -like 'nav.*')) {
        $problems.Add("$id - id prefix does not match group '$($r['group'])'")
    }
    if ($groupsOrder -notcontains $r['group']) { $problems.Add("$id - unknown group '$($r['group'])'") }
    if ($statusSet -notcontains $r['status']) { $problems.Add("$id - unknown status '$($r['status'])'") }
    if ($rtlSet -notcontains $r['rtl']) { $problems.Add("$id - unknown rtl '$($r['rtl'])'") }
    if ($colourSet -notcontains $r['colour']) { $problems.Add("$id - unknown colour role '$($r['colour'])'") }
    foreach ($l in 'en', 'ru', 'uk') { if (-not $r['name'] -or -not $r['name'][$l]) { $problems.Add("$id - missing name.$l") } }
    $hasRef = $r['ref'] -and $r['ref']['android']
    $hasSvg = $r['glyph'] -and $r['glyph']['svg']
    if (-not $hasRef -and -not $hasSvg) { $problems.Add("$id - neither ref.android nor glyph.svg") }
    if ($hasSvg -and -not $r['glyph']['source']) { $problems.Add("$id - glyph.svg without glyph.source") }
    foreach ($lk in @($r['looks'])) { if ($lk -and @('colour', 'decorated') -notcontains $lk) { $problems.Add("$id - unknown look '$lk'") } }
    if ($r['looks'] -and -not $r['hue'] -and @('content', 'accent') -notcontains $r['colour']) {
        $problems.Add("$id - lists looks but has no hue, and its colour role '$($r['colour'])' does not default to accent")
    }
    if ($r['looks'] -and @('brand', 'fixed') -contains $r['colour']) { $problems.Add("$id - a $($r['colour']) record keeps its own colours and takes no look") }
}
foreach ($r in $records) {
    foreach ($k in 'distinct', 'sharedWith') {
        foreach ($d in @($r[$k])) { if ($d -and -not $ids.ContainsKey($d)) { $problems.Add("$($r['id']) - $k names unknown id $d") } }
    }
    if ($r['sharedWith'] -and -not $r['sharedWhy']) {
        # the reason is written once, on either side of the pair
        $other = @($r['sharedWith'] | ForEach-Object { $ids[$_] } | Where-Object { $_ -and $_['sharedWhy'] })
        if (-not $other.Count) { $problems.Add("$($r['id']) - sharedWith without sharedWhy on either side") }
    }
}
if ($problems.Count) { Stop-Verdict 1 'FAIL' $problems }

# ------------------------------------------------------------------ glyph jobs
$glyphDir = Join-Path $domain 'glyphs'
$files = [ordered]@{}      # file name -> content (string or byte[])
$glyphOf = @{}             # id -> main glyph file name
$extraOf = @{}             # id -> list of @(label, file)
$notesOf = @{}

function Add-Glyph([string] $id, [string] $suffix, [string] $ref, [string] $label, $rec) {
    $literal = @('brand', 'fixed') -contains $rec['colour']
    $src = Resolve-Drawable $ref
    if (-not $src) { $problems.Add("$id - drawable '$ref' not found"); return }
    $base = $id + $suffix
    if ($src.EndsWith('.xml')) {
        try { $res = Convert-Drawable $src $literal }
        catch { $problems.Add("$id - $ref.xml: $($_.Exception.Message)"); return }
        $name = $base + '.svg'
        $files[$name] = $res.Svg
        if ($res.Notes.Count) { $notesOf[$id] = @($notesOf[$id]) + $res.Notes | Where-Object { $_ } | Sort-Object -Unique }
    }
    else {
        $name = $base + [IO.Path]::GetExtension($src)
        $files[$name] = [System.IO.File]::ReadAllBytes($src)
    }
    if ($suffix -eq '') { $glyphOf[$id] = $name }
    else {
        if (-not $extraOf.ContainsKey($id)) { $extraOf[$id] = New-Object System.Collections.Generic.List[object] }
        $extraOf[$id].Add(@($label, $name))
    }
}

foreach ($r in $records) {
    $id = $r['id']
    if ($r['ref'] -and $r['ref']['android']) { Add-Glyph $id '' $r['ref']['android'] 'main' $r }
    else {
        $name = $id + '.svg'
        $files[$name] = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' + "`n  " +
            $r['glyph']['svg'] + "`n</svg>`n"
        $glyphOf[$id] = $name
    }
    if ($r['states']) { foreach ($k in ($r['states'].Keys | Sort-Object)) { Add-Glyph $id "--$k" $r['states'][$k] $k $r } }
    if ($r['variants']) { foreach ($k in ($r['variants'].Keys | Sort-Object)) { Add-Glyph $id "--$k" $r['variants'][$k] $k $r } }
    if ($r['levels']) { $n = 0; foreach ($lv in $r['levels']) { Add-Glyph $id "--level-$n" $lv "level $n" $r; $n++ } }
}
if ($problems.Count) { Stop-Verdict 1 'FAIL' $problems }

# One shape, one meaning: two meanings whose main glyphs are the same drawing must name each other in
# sharedWith, or the vocabulary itself breaks the rule it states.
$byShape = @{}
foreach ($id in ($glyphOf.Keys | Sort-Object)) {
    $c = $files[$glyphOf[$id]]
    $key = if ($c -is [byte[]]) { [Convert]::ToBase64String([System.Security.Cryptography.SHA256]::HashData($c)) }
           else { ($c -replace '\s+', ' ').Trim() }
    if (-not $byShape.ContainsKey($key)) { $byShape[$key] = New-Object System.Collections.Generic.List[string] }
    $byShape[$key].Add($id)
}
foreach ($grp in $byShape.Values | Where-Object { $_.Count -gt 1 }) {
    foreach ($a in $grp) {
        foreach ($b in $grp) {
            if ($a -ge $b) { continue }
            $declared = (@($ids[$a]['sharedWith']) -contains $b) -or (@($ids[$b]['sharedWith']) -contains $a)
            if (-not $declared) { $problems.Add("$a and $b draw the same glyph and neither declares sharedWith") }
        }
    }
}
if ($problems.Count) { Stop-Verdict 1 'FAIL' $problems }

# ------------------------------------------------------------------ themes of this product, for the gallery
function Get-ThemeRoles([string] $themesFile, [string] $styleName, [hashtable] $map) {
    $roles = @{}
    if (-not (Test-Path -LiteralPath $themesFile)) { return $roles }
    $x = New-Object System.Xml.XmlDocument
    $x.LoadXml([System.IO.File]::ReadAllText($themesFile).TrimStart([char]0xFEFF))
    $style = $x.DocumentElement.SelectNodes('style') | Where-Object { $_.GetAttribute('name') -eq $styleName } | Select-Object -First 1
    if (-not $style) { return $roles }
    foreach ($it in @($style.SelectNodes('item'))) {
        $k = $it.GetAttribute('name') -replace '^android:', ''
        $v = Resolve-Colour $it.InnerText.Trim() $map
        if ($v) { $roles[$k] = (Split-Argb $v)[0] }
    }
    return $roles
}
$themesMain = Join-Path $RepoRoot 'app_v2/src/main/res/values/themes.xml'
$themesNight = Join-Path $RepoRoot 'app_v2/src/main/res/values-night/themes.xml'
$themeRows = New-Object System.Collections.Generic.List[object]
function Add-Theme([string] $key, [string] $label, [hashtable] $roles, [hashtable] $fallback, [hashtable] $cmap) {
    $bg = $roles['colorSurface']; if (-not $bg) { $bg = $roles['colorBackground'] }; if (-not $bg) { $bg = $fallback.bg }
    $fg = $roles['colorOnSurface']; if (-not $fg) { $fg = $fallback.fg }
    $ac = $roles['colorPrimary']; if (-not $ac) { $ac = $fallback.accent }
    $cat = [ordered]@{}
    foreach ($c in 'music', 'video', 'image', 'docs', 'other') {
        $v = Resolve-Colour ("@color/color_media_$c") $cmap
        $cat[$c] = if ($v) { (Split-Argb $v)[0] } else { $fg }
    }
    $themeRows.Add([pscustomobject]@{ key = $key; label = $label; bg = $bg; fg = $fg; accent = $ac; category = $cat })
}
$lightBase = Get-ThemeRoles $themesMain 'Theme.FastMediaSorter.App' $colourMap
$darkBase = Get-ThemeRoles $themesNight 'Theme.FastMediaSorter.App' $nightColours
Add-Theme 'light' 'Light' $lightBase @{ bg = '#FFFFFF'; fg = '#1C1B1F'; accent = '#6750A4' } $colourMap
Add-Theme 'dark' 'Dark' $darkBase @{ bg = '#1C1B1F'; fg = '#E6E1E5'; accent = '#D0BCFF' } $nightColours
foreach ($o in @(
        @('dark-green', 'Dark green', 'ThemeOverlay.FastMediaSorter.DarkGreen', $true),
        @('dark-blue', 'Dark blue', 'ThemeOverlay.FastMediaSorter.DarkBlue', $true),
        @('dark-red', 'Dark red', 'ThemeOverlay.FastMediaSorter.DarkRed', $true),
        @('light-green', 'Light green', 'ThemeOverlay.FastMediaSorter.LightGreen', $false),
        @('light-blue', 'Light blue', 'ThemeOverlay.FastMediaSorter.LightBlue', $false),
        @('light-red', 'Light red', 'ThemeOverlay.FastMediaSorter.LightRed', $false))) {
    $map = if ($o[3]) { $nightColours } else { $colourMap }
    $base = if ($o[3]) { $themeRows[1] } else { $themeRows[0] }
    Add-Theme $o[0] $o[1] (Get-ThemeRoles $themesMain $o[2] $map) @{ bg = $base.bg; fg = $base.fg; accent = $base.accent } $map
}

# ------------------------------------------------------------------ palette and looks (section 10, items B, D, E, F)
$palette = Get-IconPalette $colourMap $nightColours $themeRows[0].accent $themeRows[1].accent $themeRows[0].bg $themeRows[1].bg
$hueOf = @{}
foreach ($r in $records) {
    if (-not $r['looks']) { continue }
    $hue = if ($r['hue']) { $r['hue'] } else { 'accent' }
    if (-not $palette.Contains($hue)) { $problems.Add("$($r['id']) - hue '$hue' is not in the palette (" + ($palette.Keys -join ', ') + ')'); continue }
    $hueOf[$r['id']] = $hue
}
if ($problems.Count) { Stop-Verdict 1 'FAIL' $problems }
$looksOf = @{}
foreach ($id in ($hueOf.Keys | Sort-Object)) {
    if (@($ids[$id]['looks']) -notcontains 'decorated') { continue }
    $g = $files[$glyphOf[$id]]
    if ($g -is [byte[]]) { continue }
    $p = $palette[$hueOf[$id]]
    $name = $id + '.decorated.svg'
    $files['::looks/' + $name] = New-DecoratedLookSvg $g $p.plate $p.onPlate
    $looksOf[$id] = $name
}
$files['::palette.json'] = ([ordered]@{
        contract = 'ICON-RENDER 0.10, section 10 item D'
        generated = 'scripts/docs/export-icon-contract.ps1 in FastMediaSorter Android, from its colour resources'
        onPlateRule = 'white when white reaches 3:1 against the plate, otherwise #1F1F1F'
        hues = $palette
    } | ConvertTo-Json -Depth 5) + "`n"

# ------------------------------------------------------------------ style report (section 10, item A)
$exceptionsPath = Join-Path $RepoRoot 'scripts/quality/icon-style-exceptions.txt'
$styleExceptions = Read-StyleExceptions $exceptionsPath
$styleJobs = New-Object System.Collections.Generic.List[object]
$structureOf = @{}
foreach ($r in $records) {
    if (-not ($r['ref'] -and $r['ref']['android'])) { continue }
    $src = Resolve-Drawable $r['ref']['android']
    if (-not $src -or -not $src.EndsWith('.xml')) { continue }
    $st = Get-DrawableStructure $src
    if (-not $st) { continue }
    $structureOf[$r['id']] = $st
    $styleJobs.Add(@{ name = $r['id']; svg = $files[$glyphOf[$r['id']]]; viewportW = $st.ViewportW; viewportH = $st.ViewportH })
}
try { $metricOf = Invoke-GlyphMeasure $RepoRoot $styleJobs }
catch { Stop-Verdict 2 'COULD NOT VERIFY' @("style report: $($_.Exception.Message)") }
$styleRows = New-Object System.Collections.Generic.List[object]
foreach ($id in ($structureOf.Keys | Sort-Object)) {
    $drawable = [IO.Path]::GetFileNameWithoutExtension((Resolve-Drawable $ids[$id]['ref']['android']))
    foreach ($v in (Test-GlyphStyle $structureOf[$id] $metricOf[$id])) {
        $why = $null
        if ($styleExceptions.ContainsKey($drawable)) {
            $e = $styleExceptions[$drawable]
            $why = if ($e.ContainsKey($v.Rule)) { $e[$v.Rule] } elseif ($e.ContainsKey('*')) { $e['*'] } else { $null }
        }
        $styleRows.Add([pscustomobject]@{ Id = $id; Drawable = $drawable; Rule = $v.Rule; Value = $v.Value; Why = $why })
    }
}

# ------------------------------------------------------------------ CATALOG.md
function Esc([string] $s) { if ($null -eq $s) { return '' }; return ($s -replace '\|', '\|') }
$md = New-Object System.Collections.Generic.List[string]
$md.Add('# ICON-SET catalog')
$md.Add('')
$md.Add('Generated from `vocabulary.jsonl` by `scripts/docs/export-icon-contract.ps1` in FastMediaSorter Android, the')
$md.Add('reference implementation. Never hand-edit this page: amend the vocabulary and regenerate. The rules that bind')
$md.Add('every product are in `README.md`; `gallery.html` shows every glyph on each theme and at each size.')
$md.Add('')
$counts = $records | Group-Object { $_['status'] } | Sort-Object Name | ForEach-Object { "$($_.Count) $($_.Name)" }
$md.Add('Meanings: ' + $records.Count + ' (' + ($counts -join ', ') + ').')
$md.Add('')
foreach ($g in $groupsOrder) {
    $rows = @($records | Where-Object { $_['group'] -eq $g } | Sort-Object { $_['id'] })
    if (-not $rows.Count) { continue }
    $md.Add('## ' + $g)
    $md.Add('')
    $md.Add('| Glyph | Id | EN / RU / UK | Means | Not to be used for | Shape | Mirrors in RTL | Colour | Looks | Reference |')
    $md.Add('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
    foreach ($r in $rows) {
        $id = $r['id']
        $img = '<img src="glyphs/' + $glyphOf[$id] + '" width="24" height="24" alt="' + $r['name']['en'] + '">'
        if ($extraOf.ContainsKey($id)) {
            foreach ($x in $extraOf[$id]) { $img += ' <img src="glyphs/' + $x[1] + '" width="24" height="24" alt="' + $x[0] + '" title="' + $x[0] + '">' }
        }
        $names = (Esc $r['name']['en']) + ' / ' + (Esc $r['name']['ru']) + ' / ' + (Esc $r['name']['uk'])
        $status = if ($r['status'] -ne 'active') { ' **[' + $r['status'].ToUpper() + ']**' } else { '' }
        $means = (Esc $r['means'])
        if ($r['sharedWith']) { $means += ' Shares its shape with ' + (($r['sharedWith'] | ForEach-Object { '`' + $_ + '`' }) -join ', ') + '.' }
        if ($r['sharedWhy']) { $means += ' ' + (Esc $r['sharedWhy']) }
        if ($r['note']) { $means += ' ' + (Esc $r['note']) }
        $distinct = (@($r['distinct']) | Where-Object { $_ } | ForEach-Object { '`' + $_ + '`' }) -join ', '
        $ref = if ($r['ref'] -and $r['ref']['android']) { '`' + $r['ref']['android'] + '`' } else { $r['glyph']['source'] }
        if ($r['ref'] -and $r['ref']['wear'] -and $r['ref']['wear'] -ne $r['ref']['android']) { $ref += ', watch `' + $r['ref']['wear'] + '`' }
        $looks = if ($hueOf.ContainsKey($id)) { ((@('mono') + @($r['looks'])) -join ', ') + ' (`' + $hueOf[$id] + '`)' } else { 'mono' }
        if ($looksOf.ContainsKey($id)) { $looks += ' <img src="looks/' + $looksOf[$id] + '" width="24" height="24" alt="decorated">' }
        $md.Add("| $img | ``$id``$status | $names | $means | $distinct | $(Esc $r['shape']) | $($r['rtl']) | $($r['colour']) | $looks | $ref |")
    }
    $md.Add('')
}
$md.Add('## Style report')
$md.Add('')
$md.Add('Every exported glyph measured against `ICON-RENDER` section 10 item A: grid, one paint, stroke width 2, one unit')
$md.Add('of margin, the box or mass centre within one unit, line weight at least 1.3. A row with a reason is a departure the')
$md.Add('reference product declares in its exceptions list (`scripts/quality/icon-style-exceptions.txt`); a row marked')
$md.Add('**open** is a defect.')
$md.Add('')
if (-not $styleRows.Count) { $md.Add('No departures.') }
else {
    $openCount = @($styleRows | Where-Object { -not $_.Why }).Count
    $md.Add("Departures: $($styleRows.Count) ($openCount open).")
    $md.Add('')
    $md.Add('| Id | Drawable | Rule | Measured | Declared reason |')
    $md.Add('| --- | --- | --- | --- | --- |')
    foreach ($row in $styleRows) {
        $why = if ($row.Why) { Esc $row.Why } else { '**open**' }
        $md.Add("| ``$($row.Id)`` | ``$($row.Drawable)`` | $($row.Rule) | $(Esc $row.Value) | $why |")
    }
}
$md.Add('')
$files['::CATALOG.md'] = ($md -join "`n") + "`n"

# ------------------------------------------------------------------ gallery.html
function Html([string] $s) { return [System.Net.WebUtility]::HtmlEncode($s) }
function Inline-Svg([string] $fileName, [int] $size) {
    $c = $files[$fileName]
    if ($c -is [byte[]]) {
        return '<img src="glyphs/' + $fileName + '" width="' + $size + '" height="' + $size + '" alt="">'
    }
    $inner = ($c -replace '(?s)^<svg[^>]*>', '' -replace '(?s)</svg>\s*$', '').Trim()
    $vb = [regex]::Match($c, 'viewBox="([^"]+)"').Groups[1].Value
    return '<svg width="' + $size + '" height="' + $size + '" viewBox="' + $vb + '" aria-hidden="true">' + ($inner -replace '\s*\n\s*', '') + '</svg>'
}
$h = New-Object System.Collections.Generic.List[string]
$h.Add('<!DOCTYPE html>')
$h.Add('<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">')
$h.Add('<title>ICON-SET gallery</title>')
$h.Add('<style>')
$h.Add(':root{--bg:#FFFFFF;--fg:#1C1B1F;--ac:#6750A4;--muted:#5f6368;--line:#dadce0}')
$h.Add('body{margin:0;padding:16px;font:14px/1.4 system-ui,Segoe UI,Roboto,sans-serif;background:#f6f7f9;color:#1c1b1f}')
$h.Add('h1{font-size:20px;margin:0 0 4px}h2{font-size:16px;margin:24px 0 8px}p{margin:4px 0 12px;color:#5f6368;max-width:960px}')
$h.Add('.bar{position:sticky;top:0;background:#f6f7f9;padding:8px 0;display:flex;flex-wrap:wrap;gap:6px;z-index:1}')
$h.Add('.bar button{border:1px solid #dadce0;background:#fff;border-radius:16px;padding:4px 12px;cursor:pointer;font:inherit}')
$h.Add('.bar button[aria-pressed=true]{background:#1c1b1f;color:#fff}')
$h.Add('.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(168px,1fr));gap:8px}')
$h.Add('.card{background:var(--bg);color:var(--fg);border:1px solid var(--line);border-radius:10px;padding:10px;display:flex;flex-direction:column;gap:6px}')
$h.Add('.sizes{display:flex;align-items:flex-end;gap:12px;min-height:48px}.sizes svg,.sizes img{display:block}')
$h.Add('.id{font:12px ui-monospace,Consolas,monospace;word-break:break-all}.nm{font-size:12px;opacity:.85}')
$h.Add('.tag{font-size:11px;border-radius:8px;padding:0 6px;border:1px solid currentColor;opacity:.7;align-self:flex-start}')
$h.Add('.extra{display:flex;gap:8px;flex-wrap:wrap;font-size:11px;align-items:center;opacity:.9}')
$h.Add('.rtl svg{transform:scaleX(-1)}.accent{color:var(--ac)}')
$h.Add('.looks{display:flex;gap:10px;align-items:center;font-size:11px;flex-wrap:wrap}.looks span{display:flex;flex-direction:column;align-items:center;gap:2px}.looks svg{display:block}')
$h.Add('</style></head><body>')
$h.Add('<h1>ICON-SET gallery</h1>')
$h.Add('<p>Generated by <code>scripts/docs/export-icon-contract.ps1</code> from <code>vocabulary.jsonl</code>. Each card shows the glyph at 16, 24 and 48 px in the chosen theme of FastMediaSorter Android; a card marked RTL also shows the mirrored form a right-to-left language gets. A card with looks shows the three looks of one glyph side by side - mono, colour in its hue, decorated on its plate (<code>README.md</code> section 10). Theme colours and hues are read from the product''s own resources at generation time.</p>')
$h.Add('<div class="bar" role="toolbar" aria-label="Theme">')
foreach ($t in $themeRows) {
    $h.Add('<button type="button" data-dark="' + $(if ($t.key -like 'dark*') { '1' } else { '0' }) + '" data-bg="' + $t.bg + '" data-fg="' + $t.fg + '" data-ac="' + $t.accent + '" data-cat="' +
        (($t.category.GetEnumerator() | ForEach-Object { $_.Key + ':' + $_.Value }) -join ',') + '" aria-pressed="' +
        $(if ($t.key -eq 'light') { 'true' } else { 'false' }) + '">' + (Html $t.label) + '</button>')
}
$h.Add('</div>')
foreach ($g in $groupsOrder) {
    $rows = @($records | Where-Object { $_['group'] -eq $g } | Sort-Object { $_['id'] })
    if (-not $rows.Count) { continue }
    $h.Add('<h2>' + (Html $g) + '</h2><div class="grid">')
    foreach ($r in $rows) {
        $id = $r['id']
        $cls = switch ($r['colour']) { 'accent' { ' accent' } default { '' } }
        $catAttr = if ($r['colour'] -eq 'category') { ' data-category="' + $(switch -Wildcard ($id) { '*audio' { 'music' } '*video' { 'video' } '*image' { 'image' } '*document' { 'docs' } default { 'other' } }) + '"' } else { '' }
        $h.Add('<div class="card"' + $catAttr + '>')
        $h.Add('<div class="sizes' + $cls + '">' + (Inline-Svg $glyphOf[$id] 16) + (Inline-Svg $glyphOf[$id] 24) + (Inline-Svg $glyphOf[$id] 48) + '</div>')
        if ($r['rtl'] -eq 'mirror') { $h.Add('<div class="extra rtl">RTL ' + (Inline-Svg $glyphOf[$id] 24) + '</div>') }
        if ($hueOf.ContainsKey($id)) {
            $lk = '<div class="looks"><span>' + (Inline-Svg $glyphOf[$id] 24) + 'mono</span>'
            if (@($r['looks']) -contains 'colour') { $lk += '<span class="lk-colour" data-hue="' + $hueOf[$id] + '">' + (Inline-Svg $glyphOf[$id] 24) + 'colour</span>' }
            if ($looksOf.ContainsKey($id)) {
                $d = $files['::looks/' + $looksOf[$id]]
                $lk += '<span>' + (($d -replace '(?s)^<svg[^>]*>', '<svg width="40" height="40" viewBox="0 0 40 40" aria-hidden="true">') -replace '\s*\n\s*', '') + 'decorated</span>'
            }
            $h.Add($lk + '<span class="nm">' + (Html $hueOf[$id]) + '</span></div>')
        }
        if ($extraOf.ContainsKey($id)) {
            $h.Add('<div class="extra' + $cls + '">' + (($extraOf[$id] | ForEach-Object { (Html $_[0]) + ' ' + (Inline-Svg $_[1] 24) }) -join ' ') + '</div>')
        }
        $h.Add('<div class="id">' + (Html $id) + '</div>')
        $h.Add('<div class="nm">' + (Html ($r['name']['en'] + ' / ' + $r['name']['ru'] + ' / ' + $r['name']['uk'])) + '</div>')
        if ($r['status'] -ne 'active') { $h.Add('<span class="tag">' + (Html $r['status']) + '</span>') }
        $h.Add('</div>')
    }
    $h.Add('</div>')
}
$h.Add('<script>')
$h.Add('var PAL={' + (($palette.Keys | ForEach-Object { '"' + $_ + '":["' + $palette[$_].day + '","' + $palette[$_].night + '"]' }) -join ',') + '};')
$h.Add('(function(){var bs=document.querySelectorAll(".bar button");function apply(b){var s=document.documentElement.style;s.setProperty("--bg",b.dataset.bg);s.setProperty("--fg",b.dataset.fg);s.setProperty("--ac",b.dataset.ac);var cat={};b.dataset.cat.split(",").forEach(function(p){var kv=p.split(":");cat[kv[0]]=kv[1];});document.querySelectorAll(".card[data-category]").forEach(function(c){c.querySelector(".sizes").style.color=cat[c.dataset.category];});var dark=b.dataset.dark==="1";document.querySelectorAll(".lk-colour").forEach(function(e){var p=PAL[e.dataset.hue];if(p){e.style.color=dark?p[1]:p[0];}});bs.forEach(function(x){x.setAttribute("aria-pressed",x===b?"true":"false");});}bs.forEach(function(b){b.addEventListener("click",function(){apply(b);});});apply(bs[0]);})();')
$h.Add('</script></body></html>')
$files['::gallery.html'] = ($h -join "`n") + "`n"

# ------------------------------------------------------------------ write, then prune what the vocabulary no longer names
$null = New-Item -ItemType Directory -Force -Path $glyphDir
$looksDir = Join-Path $domain 'looks'
$null = New-Item -ItemType Directory -Force -Path $looksDir
$written = 0
foreach ($k in $files.Keys) {
    $target = if ($k.StartsWith('::')) { Join-Path $domain $k.Substring(2) } else { Join-Path $glyphDir $k }
    $v = $files[$k]
    if ($v -is [byte[]]) { [System.IO.File]::WriteAllBytes($target, $v) } else { [System.IO.File]::WriteAllText($target, $v, $utf8) }
    $written++
}
$pruned = 0
foreach ($f in Get-ChildItem -LiteralPath $glyphDir -File) {
    if (-not $files.Contains($f.Name)) { Remove-Item -LiteralPath $f.FullName -Force; $pruned++ }
}
foreach ($f in Get-ChildItem -LiteralPath $looksDir -File) {
    if (-not $files.Contains('::looks/' + $f.Name)) { Remove-Item -LiteralPath $f.FullName -Force; $pruned++ }
}
$flattened = @($notesOf.Keys | Sort-Object)
if ($flattened.Count) { Write-Host ('  note: gradient flattened to one colour in ' + ($flattened -join ', ')) }
$glyphCount = @($files.Keys | Where-Object { -not $_.StartsWith('::') }).Count
$openStyle = @($styleRows | Where-Object { -not $_.Why }).Count
Write-Host ("  meanings: $($records.Count); glyph files: $glyphCount; decorated looks: $($looksOf.Count); hues: $($palette.Count); pruned: $pruned; themes: $($themeRows.Count)")
Write-Host ("  style report: $($styleRows.Count) departure(s), $openStyle open")
Write-Host 'export-icon-contract: PASS'
exit 0
