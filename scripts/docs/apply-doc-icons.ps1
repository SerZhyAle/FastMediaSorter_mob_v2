<#
.SYNOPSIS
    S0889 - replace decorative emoji in the docs/site with the app's own icons.

.DESCRIPTION
    Consumes docs/icons/doc-icon-map.json + the generated assets in docs/icons/doc/
    (run export-doc-icon-pngs.ps1 first) and rewrites, per owner decision 2026-07-03:
      - index.html / index-ru.html / index-uk.html : each of the 37 `card-icon` spans
        (identical order across locales) gets its emoji replaced by an INLINE
        <svg ...currentColor...> so it themes with the landing's light/dark mode.
      - docs/howto/index{,-ru,-uk}.md and docs/DOCS_MAP.md : each mapped emoji is
        replaced by an <img> pointing at the baked-colour PNG (owner: "png to show").
    Idempotent: the landing rewrite replaces whatever sits inside a card-icon span (emoji
    OR a prior inline svg); the markdown rewrite is a no-op once the emoji are gone.

    This is a generator - the docs/site emoji->icon mapping lives in doc-icon-map.json,
    never hand-embedded. Re-run after changing the map or the assets.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)

$map    = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs/icons/doc-icon-map.json') -Raw | ConvertFrom-Json
$docDir = Join-Path $RepoRoot 'docs/icons/doc'

# Build the inline-SVG form of a generated asset: strip the fixed width/height, size to 1em
# so it matches the emoji it replaces, inherit text colour, mark decorative.
function Get-InlineSvg([string] $drawable) {
    $svgPath = Join-Path $docDir ($drawable + '.svg')
    if (-not (Test-Path -LiteralPath $svgPath)) { throw "missing generated svg: $svgPath (run export-doc-icon-pngs.ps1)" }
    $svg = (Get-Content -LiteralPath $svgPath -Raw).TrimEnd("`r", "`n")
    $svg = $svg -replace 'width="[^"]*"\s+height="[^"]*"', 'width="1em" height="1em" aria-hidden="true" focusable="false" style="vertical-align:-0.125em"'
    # collapse to one line so it sits cleanly inside the span
    return ($svg -replace "`r?`n\s*", '')
}

# --- Landing pages: inline SVG per card, by order ---
$landingIcons = @($map.landing | ForEach-Object { Get-InlineSvg $_.drawable })
$rxCard = [regex]'(<span class="card-icon">)(.*?)(</span>)'
foreach ($file in 'index.html', 'index-ru.html', 'index-uk.html') {
    $path = Join-Path $RepoRoot $file
    $text = Get-Content -LiteralPath $path -Raw
    $matches = $rxCard.Matches($text)
    if ($matches.Count -ne $landingIcons.Count) {
        throw "$file - card count $($matches.Count) != map landing count $($landingIcons.Count)"
    }
    # Rebuild by walking matches in reverse so earlier indices stay valid (writes into the
    # regex MatchEvaluator scope do not propagate in PowerShell, so splice by hand).
    $sb = [System.Text.StringBuilder]::new($text)
    for ($k = $matches.Count - 1; $k -ge 0; $k--) {
        $m = $matches[$k]
        $repl = $m.Groups[1].Value + $landingIcons[$k] + $m.Groups[3].Value
        [void]$sb.Remove($m.Index, $m.Length)
        [void]$sb.Insert($m.Index, $repl)
    }
    [System.IO.File]::WriteAllText($path, $sb.ToString(), $utf8)
    Write-Host ("landing: {0} - {1} cards inlined" -f $file, $matches.Count)
}

# --- Markdown surfaces: emoji -> <img> PNG ---
function Convert-MarkdownEmoji([string] $relFile, [object[]] $pairs, [string] $pngRel) {
    $path = Join-Path $RepoRoot $relFile
    if (-not (Test-Path -LiteralPath $path)) { Write-Host "skip (absent): $relFile"; return }
    $text = Get-Content -LiteralPath $path -Raw
    $replaced = 0
    foreach ($p in $pairs) {
        $img = '<img src="' + $pngRel + $p.drawable + '.png" alt="" width="20" height="20" style="vertical-align:text-bottom">'
        $before = $text
        $text = $text.Replace($p.emoji, $img)
        if ($text -ne $before) { $replaced++ }
    }
    [System.IO.File]::WriteAllText($path, $text, $utf8)
    Write-Host ("markdown: {0} - {1}/{2} emoji kinds replaced" -f $relFile, $replaced, $pairs.Count)
}

foreach ($f in 'docs/howto/index.md', 'docs/howto/index-ru.md', 'docs/howto/index-uk.md') {
    Convert-MarkdownEmoji $f $map.howto '../icons/doc/'
}
Convert-MarkdownEmoji 'docs/DOCS_MAP.md' $map.docsMap 'icons/doc/'

Write-Host ''
Write-Host 'Doc icon application complete.'
exit 0
