<#
.SYNOPSIS
    Scaffolds every missing document of the S1211 Localized Page Set for one site language.

.DESCRIPTION
    Reads the page set from scripts/docs/localized-page-set.json and the language from
    _data/languages.yml. For each English source document it creates <base>-<slug>.md beside it,
    carrying the source's layout and title, a permalink of the same shape with -<slug> before the
    extension, the page-language switcher include, and a body wrapped in a lang/dir container so a
    right-to-left language renders right-to-left under the shared theme layout. The body is the
    marker TODO(S1211-translate) until a translator replaces it.

    Nothing is overwritten: an existing target stops the run before any file is written.
    The landing page is not scaffolded here - scripts/site/generate-landing-pages.ps1 writes it
    from _data/landing/<slug>.json.

.PARAMETER Language
    The language code as _data/languages.yml declares it (de, zh-Hans, ar, ..). The slug is read
    from the same entry.

.PARAMETER Root
    Repository root. Defaults to two levels above this script.

.PARAMETER DryRun
    List the files that would be created and write nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/new-localized-page.ps1 -Language de

.OUTPUTS
    Exit codes:
      0 - every missing file created (or listed under -DryRun).
      1 - the language is not in _data/languages.yml, or is English.
      2 - a target file already exists; nothing was written.
      3 - cannot run: the page set, the language list or a source document is missing.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Language,
    [string]$Root,
    [switch]$DryRun,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit 0
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$utf8 = [System.Text.UTF8Encoding]::new($false)
$setPath = Join-Path $Root 'scripts/docs/localized-page-set.json'
$languagesPath = Join-Path $Root '_data/languages.yml'
foreach ($p in @($setPath, $languagesPath)) {
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) {
        Write-Host "new-localized-page: CANNOT RUN - not found: $p" -ForegroundColor Yellow
        exit 3
    }
}

$lang = $null
$entry = $null
foreach ($line in [System.IO.File]::ReadAllLines($languagesPath, $utf8)) {
    if ($line -match '^- code:\s*(\S+)\s*$') {
        $entry = @{ code = $Matches[1]; slug = ''; dir = 'ltr' }
        if ($entry.code -ceq $Language) { $lang = $entry }
    } elseif ($null -ne $entry -and $line -match '^\s+(slug|dir):\s*(\S+)\s*$') {
        $entry[$Matches[1]] = $Matches[2]
    }
}
if ($null -eq $lang -or $lang.code -eq 'en') {
    Write-Host "new-localized-page: '$Language' is not a non-English language of _data/languages.yml" -ForegroundColor Red
    exit 1
}

$set = [System.IO.File]::ReadAllText($setPath, $utf8) | ConvertFrom-Json
$plan = [System.Collections.Generic.List[object]]::new()
foreach ($rel in $set.documents) {
    $source = Join-Path $Root $rel
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        Write-Host "new-localized-page: CANNOT RUN - source not found: $rel" -ForegroundColor Yellow
        exit 3
    }
    $targetRel = $rel -replace '\.md$', "-$($lang.slug).md"
    $target = Join-Path $Root $targetRel
    if (Test-Path -LiteralPath $target) {
        Write-Host "new-localized-page: $targetRel exists; nothing written" -ForegroundColor Red
        exit 2
    }
    $text = [System.IO.File]::ReadAllText($source, $utf8)
    $layout = [regex]::Match($text, '(?m)^layout:\s*(.+?)\s*$').Groups[1].Value
    $title = [regex]::Match($text, '(?m)^title:\s*(.+?)\s*$').Groups[1].Value
    $permalink = [regex]::Match($text, '(?m)^permalink:\s*(\S+)\s*$').Groups[1].Value
    $switcher = [regex]::Match($text, '\{% include lang-switcher\.html doc="([^"]+)" dir="([^"]+)" current="en" %\}')
    if (-not $layout -or -not $permalink -or -not $switcher.Success) {
        Write-Host "new-localized-page: CANNOT RUN - $rel lacks layout, permalink or the switcher include" -ForegroundColor Yellow
        exit 3
    }
    $newPermalink = if ($permalink.EndsWith('/')) { "${permalink}index-$($lang.slug).html" } else { $permalink -replace '\.html$', "-$($lang.slug).html" }
    $body = @(
        '---'
        "layout: $layout"
        "title: $title"
        "permalink: $newPermalink"
        '---'
        "<div lang=`"$($lang.code)`" dir=`"$($lang.dir)`" markdown=`"1`">"
        ''
        "{% include lang-switcher.html doc=`"$($switcher.Groups[1].Value)`" dir=`"$($switcher.Groups[2].Value)`" current=`"$($lang.code)`" %}"
        ''
        'TODO(S1211-translate)'
        ''
        '</div>'
        ''
    ) -join "`n"
    $plan.Add([pscustomobject]@{ Rel = $targetRel; Path = $target; Body = $body })
}

foreach ($item in $plan) {
    if ($DryRun) { Write-Host "  would create $($item.Rel)"; continue }
    [System.IO.File]::WriteAllText($item.Path, $item.Body, $utf8)
    Write-Host "  created $($item.Rel)"
}
Write-Host "new-localized-page: OK ($($plan.Count) file(s) for $($lang.code)$(if ($DryRun) { ', dry run' }))" -ForegroundColor Green
exit 0
