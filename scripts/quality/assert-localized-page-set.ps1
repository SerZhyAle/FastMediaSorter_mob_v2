#requires -Version 7.0
<#
.SYNOPSIS
    S1211 completeness gate: every site language carries every page of the Localized Page Set.

.DESCRIPTION
    Reads the languages from _data/languages.yml and the page set from
    scripts/docs/localized-page-set.json. For every non-English language it requires:
      - index-<slug>.html at the repository root (the landing page);
      - <base>-<slug>.md beside each English source document of the set;
      - no TODO(S1211-translate) scaffold marker left in any of those documents.
    A missing localized page is the first form thirteen diverging versions take, and it is silent:
    the switcher skips a language whose file does not exist, so nothing on the site looks broken.

.PARAMETER RepoRoot
    Repository root. Defaults to two levels above this script.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the verdict line and the findings.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-localized-page-set.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. Its subject is the whole site's language composition,
    which a ticket adding an app locale changes without naming a single page file.

    Exit codes:
      0  every language carries every page, none left as a scaffold
      1  a localized page is missing or still carries the scaffold marker (each one is named)
      2  cannot verify: the page set, the language list or an English source is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Gate,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'localized-page-set'; files = '_data/languages.yml,scripts/docs/localized-page-set.json' })

$setPath = Join-Path $RepoRoot 'scripts/docs/localized-page-set.json'
$languagesPath = Join-Path $RepoRoot '_data/languages.yml'
foreach ($p in @($setPath, $languagesPath)) {
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) {
        Write-Host "assert-localized-page-set: CANNOT VERIFY - not found: $p" -ForegroundColor Red
        exit 2
    }
}

$set = Get-Content -Raw -Encoding utf8 -LiteralPath $setPath | ConvertFrom-Json
$slugs = [System.Collections.Generic.List[string]]::new()
$currentCode = $null
foreach ($line in [System.IO.File]::ReadAllLines($languagesPath)) {
    if ($line -match '^- code:\s*(\S+)') { $currentCode = $Matches[1] }
    elseif ($line -match '^\s+slug:\s*(\S+)' -and $currentCode -and $currentCode -ne 'en') { $slugs.Add($Matches[1]) }
}
if ($slugs.Count -eq 0) {
    Write-Host 'assert-localized-page-set: CANNOT VERIFY - _data/languages.yml declares no non-English language' -ForegroundColor Red
    exit 2
}

foreach ($doc in $set.documents) {
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $doc) -PathType Leaf)) {
        Write-Host "assert-localized-page-set: CANNOT VERIFY - English source missing: $doc" -ForegroundColor Red
        exit 2
    }
}

$findings = [System.Collections.Generic.List[string]]::new()
$checked = 0
foreach ($slug in $slugs) {
    $landing = ($set.landing -replace '\.html$', '') + "-$slug.html"
    $checked++
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $landing) -PathType Leaf)) {
        $findings.Add("missing  $landing")
    }
    foreach ($doc in $set.documents) {
        $localized = ($doc -replace '\.md$', '') + "-$slug.md"
        $checked++
        $full = Join-Path $RepoRoot $localized
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            $findings.Add("missing  $localized")
        } elseif ([System.IO.File]::ReadAllText($full).Contains('TODO(S1211-translate)')) {
            $findings.Add("scaffold $localized")
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Host "assert-localized-page-set: FAIL ($($findings.Count) of $checked pages missing or untranslated)" -ForegroundColor Red
    $findings | ForEach-Object { Write-Host "  $_" }
    Write-Host '  fix: scripts/utils/new-localized-page.ps1 -Language <code>, translate, then scripts/site/generate-landing-pages.ps1'
    exit 1
}

Write-Host "assert-localized-page-set: PASS ($($slugs.Count) languages, $checked pages)" -ForegroundColor Green
exit 0
