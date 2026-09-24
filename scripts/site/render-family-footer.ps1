#requires -Version 7.0
<#
.SYNOPSIS
    Render the footer tools grid of the six product pages from scripts/site/family-footer.json.

.DESCRIPTION
    Contract SITE-FAMILY-MAP makes every product page list every sibling tool. The grid used to be
    hand-copied into six pages, and the 2026-09-22 map change reached two of them late and with two
    different Russian captions for the same tool (S3454). The pages are copied verbatim by Jekyll (no
    front matter), so the one copy lives here and is rendered into the pages at authoring time.

    The rendered block sits between `<!-- family-footer:begin .. -->` and `<!-- family-footer:end -->`
    inside `<div class="tools-grid">`. A page that has the grid but no markers yet gets them on the
    first write run. The page locale comes from the file name: `-ru.html` -> ru, `-uk.html` -> uk,
    anything else -> en. Line endings and indentation of each page are preserved.

.PARAMETER Root
    Tree to render. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER Check
    Write nothing; report every page whose block differs from the source and exit 1.

.PARAMETER Quiet
    Print only the subject and the verdict line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/site/render-family-footer.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/site/render-family-footer.ps1 -Check

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every page matches the source (after writing, or already).
      1 - -Check only: at least one page differs from the source or carries no markers.
      2 - cannot render: the root, the source or a page is missing, the source is malformed, or a
          page has neither markers nor a tools grid.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$Check,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$pages = @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html')
$beginMarker = '<!-- family-footer:begin - rendered from scripts/site/family-footer.json by scripts/site/render-family-footer.ps1; edit the source, not this block -->'
$endMarker = '<!-- family-footer:end -->'

function Stop-CannotRender([string]$reason) {
    Write-Host "render-family-footer: CANNOT RENDER - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotRender "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

$sourcePath = Join-Path $Root 'scripts/site/family-footer.json'
if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { Stop-CannotRender "source not found: $sourcePath" }
try {
    $source = Get-Content -LiteralPath $sourcePath -Raw -Encoding utf8 | ConvertFrom-Json
} catch {
    Stop-CannotRender "source is not valid JSON: $($_.Exception.Message)"
}
$tools = @($source.tools)
if ($tools.Count -eq 0) { Stop-CannotRender 'source lists no tools' }
foreach ($tool in $tools) {
    foreach ($loc in 'en', 'ru', 'uk') {
        if (-not $tool.url -or -not $tool.name -or -not $tool.caption.$loc) {
            Stop-CannotRender "source entry '$($tool.name)' lacks url, name or the $loc caption"
        }
    }
}

Write-Host "subject: root=$Root pages=$($pages.Count) tools=$($tools.Count) mode=$(if ($Check) { 'check' } else { 'write' })"

function Get-PageLocale([string]$page) {
    if ($page -like '*-ru.html') { return 'ru' }
    if ($page -like '*-uk.html') { return 'uk' }
    return 'en'
}

function Get-Block([string]$locale, [string]$indent, [string]$eol) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add($indent + $beginMarker)
    foreach ($tool in $tools) {
        $lines.Add(('{0}<a href="{1}"><b>{2}</b><span>{3}</span></a>' -f $indent, $tool.url, $tool.name, $tool.caption.$locale))
    }
    $lines.Add($indent + $endMarker)
    return ($lines -join $eol)
}

$drift = [System.Collections.Generic.List[string]]::new()
$written = 0
foreach ($page in $pages) {
    $full = Join-Path $Root $page
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { Stop-CannotRender "page not found: $page" }
    $html = [System.IO.File]::ReadAllText($full, [System.Text.UTF8Encoding]::new($false))
    $eol = if ($html.Contains("`r`n")) { "`r`n" } else { "`n" }
    $locale = Get-PageLocale $page

    $marked = [regex]::Match($html, '(?s)(?<indent>[ \t]*)<!-- family-footer:begin\b.*?' + [regex]::Escape($endMarker))
    if ($marked.Success) {
        $expected = Get-Block $locale $marked.Groups['indent'].Value $eol
        if ($marked.Value -ceq $expected) { continue }
        $updated = $html.Remove($marked.Index, $marked.Length).Insert($marked.Index, $expected)
        $reason = 'block differs from the source'
    } else {
        $grid = [regex]::Match($html, '(?s)(?<open>(?<indent>[ \t]*)<div class="tools-grid">)(?<inner>.*?)(?<close>\r?\n[ \t]*</div>)')
        if (-not $grid.Success) { Stop-CannotRender "${page}: neither family-footer markers nor a tools-grid" }
        $expected = Get-Block $locale ($grid.Groups['indent'].Value + '    ') $eol
        $replacement = $grid.Groups['open'].Value + $eol + $expected + $grid.Groups['close'].Value
        $updated = $html.Remove($grid.Index, $grid.Length).Insert($grid.Index, $replacement)
        $reason = 'no family-footer markers'
    }

    if ($Check) {
        $drift.Add("${page}: $reason")
        if (-not $Quiet) { Write-Host "  DRIFT ${page}: $reason" -ForegroundColor Red }
        continue
    }
    [System.IO.File]::WriteAllText($full, $updated, [System.Text.UTF8Encoding]::new($false))
    $written++
    if (-not $Quiet) { Write-Host "  wrote ${page} ($reason)" }
}

if ($drift.Count -gt 0) {
    Write-Host "render-family-footer: DRIFT ($($drift.Count) page(s)); run scripts/site/render-family-footer.ps1" -ForegroundColor Red
    if ($Quiet) { $drift | ForEach-Object { Write-Host "  $_" } }
    exit 1
}
Write-Host "render-family-footer: OK ($($pages.Count) pages, $written written)" -ForegroundColor Green
exit 0
