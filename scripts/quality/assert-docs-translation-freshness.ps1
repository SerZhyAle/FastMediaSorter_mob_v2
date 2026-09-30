# Quality Gate: Documentation Translation Freshness & 1:1 Parity
# Part of S2973 (Documentation: Translation to Russian & Locales); language measure by S3540.
#
# Structure: every English recipe has a Russian counterpart with valid frontmatter and a compiled
# page, and the core Russian portal pages exist.
# Language: every *-ru.html under -DocDir must be Russian text. The measure is the share of
# Cyrillic letters among all letters of the visible text (head, script and style removed).
# S3540 measured the corpus: the one translated page scored 0.92 and the untranslated ones 0.00-0.27,
# so -MinCyrillicShare 0.5 separates them with room for code names and product names.
#
# Known untranslated pages live in -BaselinePath, one repository-relative path per line. The
# baseline only shrinks: an untranslated page missing from it fails the gate, and a row whose page
# now passes (or no longer exists) fails the gate until the row is deleted.
#
# Exit codes:
#   0 - parity, structure and language all hold, untranslated pages are baselined.
#   1 - a parity or structure finding, a new untranslated page, a stale baseline row, or a
#       source directory is missing.

[CmdletBinding()]
param(
    [string]$EnDir = "docs/content/recipes",
    [string]$RuDir = "docs/content/recipes-ru",
    [string]$UkDir = "docs/content/recipes-uk",
    [string]$DocDir = "documentation",
    [double]$MinCyrillicShare = 0.5,
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$BaselinePath = (Join-Path $PSScriptRoot 'docs-translation-baseline.txt')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'docs-translation-freshness'; files = 'docs/content/recipes/*.md,docs/content/recipes-ru/*.md,docs/content/recipes-uk/*.md,documentation/**/*-ru.html,documentation/**/*-uk.html' })

$repoRoot = (Resolve-Path $RepoRoot).Path.TrimEnd('\', '/')
$enRoot = Join-Path $repoRoot $EnDir
$ruRoot = Join-Path $repoRoot $RuDir
$ukRoot = Join-Path $repoRoot $UkDir
$docRoot = Join-Path $repoRoot $DocDir

foreach ($dir in @($enRoot, $ruRoot, $ukRoot, $docRoot)) {
    if (-not (Test-Path $dir)) {
        Write-Host "assert-docs-translation-freshness: FAIL - directory not found: $dir" -ForegroundColor Red
        exit 1
    }
}

$enFiles = Get-ChildItem -Path $enRoot -Filter *.md | Sort-Object Name
$ruFiles = Get-ChildItem -Path $ruRoot -Filter *.md | Sort-Object Name
$ukFiles = Get-ChildItem -Path $ukRoot -Filter *.md | Sort-Object Name

Write-Host "=== Documentation Translation Freshness & Parity Gate ===" -ForegroundColor Cyan
Write-Host "Found $($enFiles.Count) English, $($ruFiles.Count) Russian, and $($ukFiles.Count) Ukrainian recipe(s)."

$errors = [System.Collections.Generic.List[string]]::new()

# 1. Parity: every English recipe has a Russian and Ukrainian counterpart.
$ruFileNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($rf in $ruFiles) { $null = $ruFileNames.Add($rf.Name) }
foreach ($ef in $enFiles) {
    if (-not $ruFileNames.Contains($ef.Name)) { $errors.Add("Missing Russian translation for $($ef.Name) in $RuDir") }
}

$ukFileNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($uf in $ukFiles) { $null = $ukFileNames.Add($uf.Name) }
foreach ($ef in $enFiles) {
    if (-not $ukFileNames.Contains($ef.Name)) { $errors.Add("Missing Ukrainian translation for $($ef.Name) in $UkDir") }
}

# 1a. Structural key-sequence parity: the YAML key skeleton of each translated
# recipe must match the English source's skeleton.  Catches misplaced blocks such
# as an image_bookmark or callout sitting under the wrong step (S3559).
function Get-KeySkeleton([string]$filePath) {
    $lines = Get-Content $filePath -Encoding utf8
    $inFront = $false
    $skeleton = [System.Collections.Generic.List[string]]::new()
    $blockIndent = -1
    foreach ($line in $lines) {
        if ($line -match '^---\s*$') {
            if ($inFront) { break }
            $inFront = $true
            continue
        }
        if (-not $inFront) { continue }
        if ($line -match '^\s*$') { continue }
        $indent = if ($line -match '^(\s+)') { $Matches[1].Length } else { 0 }
        if ($blockIndent -ge 0) {
            if ($indent -gt $blockIndent) { continue }
            $blockIndent = -1
        }
        if ($line -match '^(\s*(?:- )?[a-z_]+):') {
            $skeleton.Add($Matches[1] + ':')
            if ($line -match ':\s*[|>]\s*$') { $blockIndent = $indent }
        }
    }
    return ($skeleton -join "`n")
}

foreach ($ef in $enFiles) {
    $enSkel = Get-KeySkeleton $ef.FullName
    $ruPath = Join-Path $ruRoot $ef.Name
    if (Test-Path $ruPath) {
        $ruSkel = Get-KeySkeleton $ruPath
        if ($enSkel -ne $ruSkel) {
            $errors.Add("YAML key-sequence drift in RU: $($ef.Name) - translated recipe structure differs from EN source (S3559)")
        }
    }
    $ukPath = Join-Path $ukRoot $ef.Name
    if (Test-Path $ukPath) {
        $ukSkel = Get-KeySkeleton $ukPath
        if ($enSkel -ne $ukSkel) {
            $errors.Add("YAML key-sequence drift in UK: $($ef.Name) - translated recipe structure differs from EN source (S3559)")
        }
    }
}

# 2. Structure and compiled page of each Russian recipe.
$compiledRuCount = 0
foreach ($rf in $ruFiles) {
    $content = Get-Content $rf.FullName -Raw -Encoding utf8
    if ($content -notmatch '^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$') {
        $errors.Add("Malformed YAML frontmatter in $($rf.Name)")
        continue
    }
    $yaml = $Matches[1]
    if ($yaml -notmatch 'page_id:\s*([^\r\n]+)') { $errors.Add("Missing page_id in $($rf.Name)") }
    if ($yaml -notmatch 'canonical_url:\s*([^\r\n]+)') {
        $errors.Add("Missing canonical_url in $($rf.Name)")
    }
    elseif (-not (Test-Path (Join-Path $repoRoot $Matches[1].Trim()))) {
        $errors.Add("Compiled HTML page missing: $($Matches[1].Trim()) for $($rf.Name)")
    }
    else { $compiledRuCount++ }
}

# 3. Structure and compiled page of each Ukrainian recipe.
$compiledUkCount = 0
foreach ($uf in $ukFiles) {
    $content = Get-Content $uf.FullName -Raw -Encoding utf8
    if ($content -notmatch '^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$') {
        $errors.Add("Malformed YAML frontmatter in $($uf.Name) ($UkDir)")
        continue
    }
    $yaml = $Matches[1]
    if ($yaml -notmatch 'page_id:\s*([^\r\n]+)') { $errors.Add("Missing page_id in $($uf.Name) ($UkDir)") }
    if ($yaml -notmatch 'canonical_url:\s*([^\r\n]+)') {
        $errors.Add("Missing canonical_url in $($uf.Name) ($UkDir)")
    }
    elseif (-not (Test-Path (Join-Path $repoRoot $Matches[1].Trim()))) {
        $errors.Add("Compiled HTML page missing: $($Matches[1].Trim()) for $($uf.Name) ($UkDir)")
    }
    else { $compiledUkCount++ }
}

# 4. Core Russian and Ukrainian portal pages.
foreach ($cp in @('index-ru.html', 'overview-ru.html', 'general/glossary-ru.html', 'subject-index-ru.html')) {
    if (-not (Test-Path (Join-Path $docRoot $cp))) { $errors.Add("Core Russian portal page missing: $DocDir/$cp") }
}
foreach ($cp in @('index-uk.html', 'overview-uk.html', 'general/glossary-uk.html', 'subject-index-uk.html')) {
    if (-not (Test-Path (Join-Path $docRoot $cp))) { $errors.Add("Core Ukrainian portal page missing: $DocDir/$cp") }
}

# 5. Language check with baseline.
function Get-CyrillicShare([string]$html) {
    $text = [regex]::Replace($html, '(?is)<(head|script|style)\b.*?</\1\s*>', ' ')
    $text = [regex]::Replace($text, '(?s)<!--.*?-->', ' ')
    $text = [regex]::Replace($text, '(?s)<[^>]+>', ' ')
    $text = [System.Net.WebUtility]::HtmlDecode($text)
    $cyrillic = [regex]::Matches($text, '[\p{IsCyrillic}\u0400-\u04FF]').Count
    $latin = [regex]::Matches($text, '[A-Za-z]').Count
    if ($cyrillic + $latin -eq 0) { return 0.0 }
    return $cyrillic / ($cyrillic + $latin)
}

$baseline = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
if (Test-Path $BaselinePath) {
    foreach ($line in Get-Content $BaselinePath) {
        $entry = ($line -replace '#.*$', '').Trim()
        if ($entry) { [void]$baseline.Add($entry.Replace('\', '/')) }
    }
}

$locPages = Get-ChildItem -Path $docRoot -Recurse -File | Where-Object { $_.Name -like '*-ru.html' -or $_.Name -like '*-uk.html' } | Sort-Object FullName
$untranslated = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$newUntranslated = [System.Collections.Generic.List[string]]::new()
foreach ($page in $locPages) {
    $rel = $page.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
    $share = Get-CyrillicShare ([System.IO.File]::ReadAllText($page.FullName))
    if ($share -ge $MinCyrillicShare) { continue }
    [void]$untranslated.Add($rel)
    if (-not $baseline.Contains($rel)) { $newUntranslated.Add(('{0} (Cyrillic share {1:0.00})' -f $rel, $share)) }
}
foreach ($p in $newUntranslated) { $errors.Add("Localized page is below Cyrillic threshold: $p - translate it; threshold is $MinCyrillicShare") }
foreach ($b in $baseline) {
    if (-not $untranslated.Contains($b)) { $errors.Add("Stale baseline row: $b now passes or no longer exists - delete it from $BaselinePath") }
}
$translatedCount = $locPages.Count - $untranslated.Count

if ($errors.Count -gt 0) {
    Write-Host "`nassert-docs-translation-freshness: FAIL ($($errors.Count) error(s))" -ForegroundColor Red
    foreach ($err in $errors) { Write-Host "  [ERROR] $err" -ForegroundColor Red }
    exit 1
}

Write-Host ("assert-docs-translation-freshness: PASS - {0} recipes in 1:1 parity (RU: {1}, UK: {2}), core portal pages present; localized pages: {3} of {4} translated, {5} untranslated and baselined." -f $enFiles.Count, $compiledRuCount, $compiledUkCount, $translatedCount, $locPages.Count, $untranslated.Count) -ForegroundColor Green
exit 0
