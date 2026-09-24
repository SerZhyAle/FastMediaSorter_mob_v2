#requires -Version 7.0
<#
.SYNOPSIS
    S0889 drift gate: the docs/site icon system (map, assets, embeds) stays consistent.

.DESCRIPTION
    Locks the emoji->app-icon work against drift:
      1. Every drawable named in docs/icons/doc-icon-map.json has a generated
         docs/icons/doc/<drawable>.svg + .png (run export-doc-icon-pngs.ps1).
      2. No orphan assets in docs/icons/doc/ beyond the map.
      3. Each landing page (index{,-ru,-uk}.html) has exactly map.landing many
         `card-icon` spans, and NONE still contains an emoji (each holds an <svg>).
      4. Every icons/doc/<x>.png referenced on any docs page exists and <x> is declared by the map;
         each `pageIcons` entry names a page that shows its declared icon (S3442).
      6. No docs page puts an emoji in parentheses where a control's glyph belongs (S3442).

    Scope note: sections 1-4 cover the S0889 icon SLOTS (landing cards, howto, DOCS_MAP,
    SETTINGS_REFERENCE). Section 5 (S0907) additionally asserts the landing pages carry no
    non-whitelist emoji anywhere - the JS scenario-filter labels, variant/tab buttons and
    decorative headings are text-only, keeping only the functional controls.

    -Gate exits 1 on any drift; default prints a report and exits 0.
#>
[CmdletBinding()]
param([switch]$Gate)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mapPath  = Join-Path $repoRoot 'docs/icons/doc-icon-map.json'
$docDir   = Join-Path $repoRoot 'docs/icons/doc'
$fail = 0
function Bad([string]$m) { Write-Host "  FAIL: $m" -ForegroundColor Red; $script:fail++ }

if (-not (Test-Path $mapPath)) { Write-Host "doc-icons-sync: map missing"; exit 2 }
$map = Get-Content -LiteralPath $mapPath -Raw | ConvertFrom-Json

# 1 + 2: map <-> asset coverage.
$names = [System.Collections.Generic.HashSet[string]]::new()
# Every section, as export-doc-icon-pngs.ps1 reads it (S3442).
foreach ($sec in $map.PSObject.Properties) {
    if ($sec.Name -like '_*') { continue }
    if ($sec.Value -is [System.Array]) { foreach ($c in $sec.Value) { [void]$names.Add($c.drawable) } }
    else { foreach ($p in $sec.Value.PSObject.Properties) { [void]$names.Add($p.Value) } }
}
if ($map.PSObject.Properties['pageIcons']) {
    foreach ($c in $map.pageIcons) {
        $pagePath = Join-Path $repoRoot $c.page
        if (-not (Test-Path -LiteralPath $pagePath)) { Bad "pageIcons names a missing page $($c.page)" }
        elseif ((Get-Content -LiteralPath $pagePath -Raw) -notmatch ('icons/doc/' + [regex]::Escape($c.drawable) + '\.png')) {
            Bad "$($c.page) does not show its declared page icon $($c.drawable)"
        }
    }
}
foreach ($n in $names) {
    foreach ($ext in '.svg', '.png') {
        if (-not (Test-Path (Join-Path $docDir ($n + $ext)))) { Bad "missing asset $n$ext (run export-doc-icon-pngs.ps1)" }
    }
}
$expected = [System.Collections.Generic.HashSet[string]]::new()
foreach ($n in $names) { [void]$expected.Add($n + '.svg'); [void]$expected.Add($n + '.png') }
Get-ChildItem -LiteralPath $docDir -File | Where-Object { -not $expected.Contains($_.Name) } | ForEach-Object {
    Bad "orphan asset $($_.Name) (not in doc-icon-map)"
}

# 3: landing card spans - right count, emoji-free.
# Astral-plane emoji (high surrogates) + common BMP symbol/dingbat ranges + VS16.
$emojiRx = [regex]'[\uD800-\uDBFF]|[←-⇿⌀-➿⬀-⯿️]'
$landingCount = @($map.landing).Count
foreach ($f in 'index.html', 'index-ru.html', 'index-uk.html') {
    $p = Join-Path $repoRoot $f
    if (-not (Test-Path $p)) { Bad "landing page missing: $f"; continue }
    $t = Get-Content -LiteralPath $p -Raw
    $spans = [regex]::Matches($t, '<span class="card-icon">(.*?)</span>')
    if ($spans.Count -ne $landingCount) { Bad "$f card-icon spans $($spans.Count) != map $landingCount" }
    foreach ($s in $spans) {
        $inner = $s.Groups[1].Value
        if ($inner -notmatch '<svg') { Bad "$f card-icon without <svg>: '$inner'" }
        elseif ($emojiRx.IsMatch(($inner -replace '<svg.*?</svg>', ''))) { Bad "$f card-icon still has emoji outside svg" }
    }
}

# 4: every docs page, not a named list (S3442): a referenced PNG exists and its drawable is declared by the
# map, so an icon hand-embedded on a page nobody listed cannot pick a picture outside ICON-SET rule 8.
$pageFiles = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'docs') -Recurse -File -Include '*.md', '*.html' |
    Where-Object { $_.FullName -notmatch '[\\/]docs[\\/](archive|icons)[\\/]' }
foreach ($pf in $pageFiles) {
    $f = [IO.Path]::GetRelativePath($repoRoot, $pf.FullName) -replace '\\', '/'
    foreach ($m in [regex]::Matches((Get-Content -LiteralPath $pf.FullName -Raw), 'icons/doc/([a-z0-9_]+)\.png')) {
        $d = $m.Groups[1].Value
        if (-not (Test-Path (Join-Path $docDir ($d + '.png')))) { Bad "$f references missing PNG $d.png" }
        elseif (-not $names.Contains($d)) { Bad "$f shows $d, which doc-icon-map.json does not declare" }
    }
}

# 6 (S3442): an emoji in parentheses beside a control's name stands in for its glyph - ICON-SET rule 8
# asks for the glyph itself (declare it in controlGlyphs and embed it). U+2261 is exempt: it is the
# printed marking of a VR controller's hardware button, not an app glyph.
$emojiParenRx = [regex]'\((?:[\uD800-\uDBFF][\uDC00-\uDFFF]|[←-≠≢-➿⬀-⯿])️?\)'
foreach ($pf in $pageFiles) {
    if ($pf.Extension -ne '.md') { continue }
    $f = [IO.Path]::GetRelativePath($repoRoot, $pf.FullName) -replace '\\', '/'
    $n = 0
    foreach ($line in Get-Content -LiteralPath $pf.FullName -Encoding utf8) {
        $n++
        if ($line.Contains('![')) { continue }
        $m = $emojiParenRx.Match($line)
        if ($m.Success) { Bad "$f`:$n shows emoji $($m.Value) in place of a control glyph (ICON-SET rule 8)" }
    }
}

# 5 (S0907): landing pages carry no emoji except the functional controls (text-only filter).
# Whitelist: bullet U+2022, link arrow U+2192, dropdown U+25BC, theme toggle U+25D0,
# clear-filter U+2716, search U+1F50D. Built from hex code points so this stays ASCII-safe.
$keepRx = [regex]('[' + [char]0x2022 + [char]0x2192 + [char]0x25BC + [char]0x25D0 + [char]0x2716 + ']|' + [char]::ConvertFromUtf32(0x1F50D))
foreach ($f in 'index.html', 'index-ru.html', 'index-uk.html') {
    $p = Join-Path $repoRoot $f
    if (-not (Test-Path $p)) { continue }   # section 3 already reports a missing landing page
    $stripped = $keepRx.Replace((Get-Content -LiteralPath $p -Raw), '')
    $hits = $emojiRx.Matches($stripped)
    if ($hits.Count -gt 0) {
        $cps = ($hits | Select-Object -First 6 | ForEach-Object { 'U+{0:X4}' -f [int][char]$_.Value } | Select-Object -Unique) -join ' '
        Bad "$f has $($hits.Count) non-whitelist emoji char(s) [$cps] - run scripts/docs/strip-landing-filter-emoji.ps1"
    }
}

if ($fail -gt 0) {
    Write-Host "assert-doc-icons-sync: FAIL ($fail issue(s))." -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}
Write-Host "assert-doc-icons-sync: PASS - map/assets/landing/markdown in sync ($($names.Count) drawables)." -ForegroundColor Green
exit 0
