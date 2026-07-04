<#
.SYNOPSIS
    S0907 - strip decorative emoji from the landing feature-explorer (JS scenario-filter
    labels, variant/tab/toggle buttons, and JS/markup section titles), text-only per owner
    decision 2026-07-04.

.DESCRIPTION
    Global pass over index{,-ru,-uk}.html: remove every non-whitelist emoji grapheme + an
    optional following space, anywhere in the page. This covers the JS label slots
    (label: '<emoji> X' -> 'X'), the variant/tab/toggle buttons, and decorative emoji outside
    those slots (e.g. <summary> headings and JS innerText titles).

    Functional control glyphs are preserved (whitelist): dropdown U+25BC, theme toggle
    U+25D0, clear-filter U+2716, search U+1F50D, link arrow U+2192, bullet U+2022. The emoji
    class below matches any astral emoji EXCEPT U+1F50D (negative lookahead) plus the two BMP
    emoji U+2699 / U+2728, so the whitelist is safe by construction - a page-wide strip cannot
    touch the kept glyphs (none are astral / U+2699 / U+2728), which is why the global pass is
    safe rather than needing per-slot anchoring.

    Sibling of apply-doc-icons.ps1 (S0889) but the inverse intent: that script swaps emoji for
    inline SVG in the static card slots; this one drops emoji outright from the surface the
    owner reversed to text-only. Idempotent: a second run is a no-op. Writes UTF-8 without BOM
    (mirrors apply-doc-icons.ps1). The pattern's specific glyphs are built from hex code points
    at runtime ([char]/ConvertFromUtf32) so this source stays pure ASCII - no literal astral
    chars / invisible VS16 to corrupt on re-save (byte-trap avoidance).
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)

# Build the strip pattern with ASCII-only source. Surrogate RANGES stay as \u escapes (lone
# surrogates, safe to type); the specific kept/stripped glyphs come from hex code points.
$search = [char]::ConvertFromUtf32(0x1F50D)          # U+1F50D search magnifier - KEEP
$bmp    = [char]0x2699, [char]0x2728 -join ''        # U+2699 gear, U+2728 sparkles - STRIP
$vs16   = [char]0xFE0F                               # U+FE0F variation selector-16 (optional)

# Any astral pair except the search glyph, OR a BMP strip-emoji, optional VS16, optional space.
$emoji   = '(?:(?!' + $search + ')[\uD800-\uDBFF][\uDC00-\uDFFF]|[' + $bmp + '])' + $vs16 + '?'
$rxEmoji = [regex]($emoji + ' ?')

foreach ($file in 'index.html', 'index-ru.html', 'index-uk.html') {
    $path = Join-Path $RepoRoot $file
    $text = [System.IO.File]::ReadAllText($path)
    $before = $text

    $n = $rxEmoji.Matches($text).Count
    $text = $rxEmoji.Replace($text, '')

    if ($text -ne $before) {
        [System.IO.File]::WriteAllText($path, $text, $utf8)
    }
    Write-Host ("strip: {0} - {1} emoji removed" -f $file, $n)
}

Write-Host ''
Write-Host 'Landing filter emoji strip complete.'
exit 0
