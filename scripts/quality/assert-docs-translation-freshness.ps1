# Quality Gate: Documentation Translation Freshness & 1:1 Parity
# Part of S2973 (Documentation: Translation to Russian & Locales)
# Verifies 1-to-1 parity between English original recipes and Russian translated recipes,
# validates frontmatter structure, and ensures compiled -ru.html pages exist on disk.

[CmdletBinding()]
param(
    [string]$EnDir = "docs/content/recipes",
    [string]$RuDir = "docs/content/recipes-ru",
    [string]$DocDir = "documentation"
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'docs-translation-freshness'; files = 'docs/content/recipes/*.md,docs/content/recipes-ru/*.md,documentation/**/*.html' })

$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$enRoot = Join-Path $repoRoot $EnDir
$ruRoot = Join-Path $repoRoot $RuDir
$docRoot = Join-Path $repoRoot $DocDir

if (-not (Test-Path $enRoot)) {
    Write-Error "assert-docs-translation-freshness: English recipe directory not found at $enRoot"
    exit 1
}

if (-not (Test-Path $ruRoot)) {
    Write-Error "assert-docs-translation-freshness: Russian recipe directory not found at $ruRoot"
    exit 1
}

$enFiles = Get-ChildItem -Path $enRoot -Filter *.md | Sort-Object Name
$ruFiles = Get-ChildItem -Path $ruRoot -Filter *.md | Sort-Object Name

Write-Host "=== Documentation Translation Freshness & Parity Gate ===" -ForegroundColor Cyan
Write-Host "Found $($enFiles.Count) English recipe(s) and $($ruFiles.Count) Russian recipe(s)."

$errors = [System.Collections.Generic.List[string]]::new()

# 1. Parity Check: Every English recipe must have a Russian counterpart
$ruFileNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($rf in $ruFiles) {
    $null = $ruFileNames.Add($rf.Name)
}

foreach ($ef in $enFiles) {
    if (-not $ruFileNames.Contains($ef.Name)) {
        $errors.Add("Missing Russian translation for $($ef.Name) in $RuDir")
    }
}

# 2. Structural and HTML existence check for Russian recipes
$compiledRuCount = 0
foreach ($rf in $ruFiles) {
    $content = Get-Content $rf.FullName -Raw -Encoding utf8
    if ($content -notmatch '^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$') {
        $errors.Add("Malformed YAML frontmatter in $($rf.Name)")
        continue
    }

    $yaml = $Matches[1]
    if ($yaml -notmatch 'page_id:\s*([^\r\n]+)') {
        $errors.Add("Missing page_id in $($rf.Name)")
    }
    if ($yaml -notmatch 'canonical_url:\s*([^\r\n]+)') {
        $errors.Add("Missing canonical_url in $($rf.Name)")
    } else {
        $canonical = $Matches[1].Trim()
        $htmlPath = Join-Path $repoRoot $canonical
        if (-not (Test-Path $htmlPath)) {
            $errors.Add("Compiled HTML page missing: $canonical for $($rf.Name)")
        } else {
            $compiledRuCount++
        }
    }
}

# 3. Portal core Russian pages existence check
$corePages = @(
    "documentation/index-ru.html",
    "documentation/overview-ru.html",
    "documentation/general/glossary-ru.html",
    "documentation/subject-index-ru.html"
)

foreach ($cp in $corePages) {
    $fullCp = Join-Path $repoRoot $cp
    if (-not (Test-Path $fullCp)) {
        $errors.Add("Core Russian portal page missing: $cp")
    }
}

if ($errors.Count -gt 0) {
    Write-Host "`nassert-docs-translation-freshness: FAIL ($($errors.Count) error(s))" -ForegroundColor Red
    foreach ($err in $errors) {
        Write-Host "  [ERROR] $err" -ForegroundColor Red
    }
    exit 1
}

Write-Host "assert-docs-translation-freshness: PASS - $($enFiles.Count) recipes in 1:1 parity, $compiledRuCount HTML pages verified, core portal pages present." -ForegroundColor Green
exit 0
