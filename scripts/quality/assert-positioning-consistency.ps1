#requires -Version 7.0
<#
.SYNOPSIS
    S2271 gate: every positioning surface names the eight pillars of docs/POSITIONING*.md in order.

.DESCRIPTION
    The product was defined six different ways across the site, the READMEs, the showcase and the
    store listings, because nothing read two surfaces and compared them (S2271). The canonical source
    is docs/POSITIONING.md with its RU and UK translations; its "## 2" section lists the pillars as
    numbered bold names. This gate reads those names per locale and asserts that each surface below
    contains all of them as an ordered subsequence - pillar N found somewhere after pillar N-1 -
    after collapsing whitespace, stripping HTML tags and ignoring case. A leading English article
    ("A full file manager") is dropped so a list item and a heading both match.

    S4102 added the portal home and overview (documentation/index*.html, overview*.html), which
    SITE-REPRESENTATION rule 1 names as surfaces, and the generated landing languages. Those pages
    (index-<slug>.html) are the English landing with each segment swapped for its translation in
    _data/landing/<slug>.json, so a segment left untranslated publishes the English pillar wording.
    For every data file other than en.json the gate requires each segment of the source catalog
    (_data/landing/en.json) that names an English pillar to carry a non-empty translation that is
    not the English text, and the generated page to exist. Whether the page is current with its
    data is scripts/site/generate-landing-pages.ps1 -Check, not this gate.

    Not judged: the Play and fastlane short descriptions, where the 80-character limit makes the
    full list impossible; the ten further Play listings, which have no per-language pillar source;
    the wording around the names, which is each surface's own genre.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; a test passes a fixture tree.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject and the verdict line, plus findings on failure.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-positioning-consistency.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. Its subject is the agreement between surfaces that
    belong to different tickets; a finding reaches a reader only when the site or a listing is
    published. Runs from assert-release-scope-gates.ps1; placement row in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - every surface names every pillar in canonical order.
      1 - at least one surface misses a pillar or names it out of order.
      2 - cannot verify: the root, a canonical file, a surface or the landing source catalog is
          missing, the canonical file does not list eight pillars, or a landing data file is not
          valid JSON.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-positioning-consistency: COULD NOT VERIFY - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

$canon = [ordered]@{ en = 'docs/POSITIONING.md'; ru = 'docs/POSITIONING-ru.md'; uk = 'docs/POSITIONING-uk.md' }
$surfaces = [ordered]@{
    en = @('index.html', 'nolegal.html', 'README.md', 'docs/README.md', 'docs/FEATURES.md', 'docs/REPLACES.md',
        'documentation/index.html', 'documentation/overview.html',
        'play/listing/en-US/full_description.txt', 'fastlane/metadata/android/en-US/full_description.txt')
    ru = @('index-ru.html', 'nolegal-ru.html', 'docs/README-ru.md', 'docs/FEATURES-ru.md', 'docs/REPLACES-ru.md',
        'documentation/index-ru.html', 'documentation/overview-ru.html',
        'play/listing/ru-RU/full_description.txt', 'fastlane/metadata/android/ru-RU/full_description.txt')
    uk = @('index-uk.html', 'nolegal-uk.html', 'docs/README-uk.md', 'docs/FEATURES-uk.md', 'docs/REPLACES-uk.md',
        'documentation/index-uk.html', 'documentation/overview-uk.html',
        'play/listing/uk-UA/full_description.txt', 'fastlane/metadata/android/uk-UA/full_description.txt')
}

function Read-Surface([string]$rel) {
    $full = Join-Path $Root $rel
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { Stop-CannotVerify "file not found: $rel" }
    return Get-Content -LiteralPath $full -Raw -Encoding utf8
}

function ConvertTo-Plain([string]$text) {
    $t = [regex]::Replace($text, '<[^>]+>', ' ')
    $t = $t.Replace('&amp;', '&').Replace('&nbsp;', ' ')
    return ([regex]::Replace($t, '\s+', ' ')).ToLowerInvariant()
}

$pillars = @{}
foreach ($loc in $canon.Keys) {
    $text = Read-Surface $canon[$loc]
    $section = [regex]::Match($text, '(?ms)^## 2\..*?(?=^## |\z)')
    if (-not $section.Success) { Stop-CannotVerify "$($canon[$loc]): no '## 2.' pillar section" }
    $names = @([regex]::Matches($section.Value, '(?m)^\d+\. \*\*([^*]+?)\.?\*\*') | ForEach-Object {
            ($_.Groups[1].Value.Trim() -replace '^(?i:a|an|the) ', '').ToLowerInvariant()
        })
    if ($names.Count -ne 8) { Stop-CannotVerify "$($canon[$loc]): expected 8 pillars, found $($names.Count)" }
    $pillars[$loc] = $names
}

$count = @($surfaces.Values | ForEach-Object { $_ }).Count
$failures = [System.Collections.Generic.List[string]]::new()

$landingDir = Join-Path $Root '_data/landing'
$catalogPath = Join-Path $landingDir 'en.json'
$landingFiles = @()
if (Test-Path -LiteralPath $landingDir -PathType Container) {
    $landingFiles = @(Get-ChildItem -LiteralPath $landingDir -Filter '*.json' -File | Where-Object Name -ne 'en.json' | Sort-Object Name)
}
if ($landingFiles.Count -gt 0) {
    if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) { Stop-CannotVerify 'landing source catalog not found: _data/landing/en.json' }
    try { $catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding utf8 | ConvertFrom-Json } catch { Stop-CannotVerify '_data/landing/en.json is not valid JSON' }
    $pillarSegments = @($catalog.text | Where-Object {
            $key = ([regex]::Replace([string]$_, '\s+', ' ')).ToLowerInvariant()
            @($pillars['en'] | Where-Object { $key.Contains($_) }).Count -gt 0
        })
    foreach ($file in $landingFiles) {
        $slug = $file.BaseName
        $rel = "_data/landing/$($file.Name)"
        try { $data = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable } catch { Stop-CannotVerify "$rel is not valid JSON" }
        if (-not (Test-Path -LiteralPath (Join-Path $Root "index-$slug.html") -PathType Leaf)) {
            $failures.Add("${rel}: generated page index-$slug.html is missing")
        }
        $text = if ($data.ContainsKey('text') -and $data['text'] -is [System.Collections.IDictionary]) { $data['text'] } else { @{} }
        foreach ($segment in $pillarSegments) {
            $value = if ($text.Contains($segment)) { [string]$text[$segment] } else { '' }
            if ([string]::IsNullOrWhiteSpace($value) -or $value -ceq $segment) {
                $shown = if ($segment.Length -gt 70) { $segment.Substring(0, 70) + '..' } else { $segment }
                $failures.Add("${rel}: pillar segment untranslated - '$shown'")
            }
        }
    }
}

Write-Host "subject: root=$Root surfaces=$count locales=$($canon.Count) landing-languages=$($landingFiles.Count)"
foreach ($loc in $surfaces.Keys) {
    foreach ($rel in $surfaces[$loc]) {
        $plain = ConvertTo-Plain (Read-Surface $rel)
        $names = $pillars[$loc]
        $at = -1
        for ($i = 0; $i -lt $names.Count; $i++) {
            $next = $plain.IndexOf($names[$i], $at + 1, [StringComparison]::Ordinal)
            if ($next -lt 0) {
                $why = if ($plain.Contains($names[$i])) { 'out of order' } else { 'missing' }
                $failures.Add("${rel}: pillar $($i + 1) '$($names[$i])' $why")
                break
            }
            $at = $next
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "assert-positioning-consistency: FAIL ($($failures.Count) surface(s))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host "assert-positioning-consistency: PASS ($count surfaces, 8 pillars each; $($landingFiles.Count) landing languages translate every pillar segment)" -ForegroundColor Green
exit 0
