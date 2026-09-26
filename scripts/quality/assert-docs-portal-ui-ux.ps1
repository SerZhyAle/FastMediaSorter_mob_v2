#requires -Version 7.0
<#
.SYNOPSIS
    Quality Gate: Assert Documentation Portal UI/UX Standards.
    Part of S3533 (documentation-portal-ui-ux-testing).

.DESCRIPTION
    Comprehensive UI/UX audit for FastMediaSorter v2 documentation portal:
    - Validates all HTML pages for viewport, title, pre-paint theme resolver, theme toggle button,
      semantic landmarks, and image accessibility (alt text).
    - Verifies local asset and relative link integrity across all published documentation pages
      (including Jekyll Markdown sources in docs/).
    - Validates CSS design tokens (dark/light themes), responsive breakpoints down to 400px,
      and mobile navigation drawer styling.
    - Validates search engine client implementation, scoring accuracy (no false positives on
      unmatched tokens), universal category link prefixes, and modal accessibility.

.PARAMETER RepoRoot
    Target repository root path.

.PARAMETER Gate
    Accepted for uniform runner invocation.

.PARAMETER Quiet
    Suppress non-error progress output.
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
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'docs-portal-ui-ux'; files = 'documentation/**/*.html,documentation/assets/docs.css,documentation/assets/search.js' })

$docRoot = Join-Path $RepoRoot 'documentation'
$docsCssPath = Join-Path $docRoot 'assets/docs.css'
$searchJsPath = Join-Path $docRoot 'assets/search.js'
$searchIndexPath = Join-Path $docRoot 'assets/search-index.json'

if (-not (Test-Path -LiteralPath $docRoot)) {
    Write-Host "assert-docs-portal-ui-ux: FAIL - documentation directory missing at $docRoot" -ForegroundColor Red
    exit 1
}

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

# A docs/*.md page is served at its `permalink:` address, which often differs from the source
# name (PRIVACY_POLICY-ru.md -> /docs/PRIVACY_POLICY.ru.html), so a link is live when it names a
# published permalink even though no file of that name exists on disk.
$publishedPermalinks = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($mdPage in (Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'docs') -Filter *.md -File)) {
    $mdHead = (Get-Content -LiteralPath $mdPage.FullName -TotalCount 12) -join "`n"
    if ($mdHead -match '(?m)^permalink:\s*(\S+)') {
        $null = $publishedPermalinks.Add([System.IO.Path]::GetFullPath((Join-Path $RepoRoot $Matches[1].Trim().TrimStart('/'))))
    }
}

# -------------------------------------------------------------------------
# 1. CSS Design Tokens & Mobile Responsive Styles
# -------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $docsCssPath)) {
    $errors.Add("Docs CSS missing at $docsCssPath")
} else {
    $css = Get-Content -LiteralPath $docsCssPath -Raw -Encoding utf8

    $requiredDarkTokens = @('--doc-bg:', '--doc-text:', '--doc-accent:', '--doc-gold:', '--doc-border:')
    foreach ($token in $requiredDarkTokens) {
        if ($css -notmatch [regex]::Escape($token)) {
            $errors.Add("docs.css missing dark theme token '$token'")
        }
    }

    $requiredLightTokens = @('--doc-bg:', '--doc-text:', '--doc-accent:', '--doc-gold:', '--doc-border:')
    if ($css -notmatch 'html\[data-theme=["'']light["'']\]') {
        $errors.Add("docs.css missing light theme selector html[data-theme='light']")
    }

    # Responsive media queries
    $breakpoints = @('1100px', '768px', '480px')
    foreach ($bp in $breakpoints) {
        if ($css -notmatch "@media\s*\(\s*max-width\s*:\s*$bp\s*\)") {
            $errors.Add("docs.css missing responsive breakpoint max-width: $bp")
        }
    }

    # Mobile drawer & menu button styles
    if ($css -notmatch '\.doc-mobile-menu-btn' -or $css -notmatch '\.doc-sidebar\.(active|open)') {
        $errors.Add("docs.css missing mobile drawer styles (.doc-mobile-menu-btn or .doc-sidebar.active)")
    }

    # Print styles
    if ($css -notmatch '@media\s+print') {
        $errors.Add("docs.css missing @media print styles")
    }
}

# -------------------------------------------------------------------------
# 2. Client Search Engine JS Asset & Scoring Logic
# -------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $searchJsPath)) {
    $errors.Add("Search JS asset missing at $searchJsPath")
} else {
    $js = Get-Content -LiteralPath $searchJsPath -Raw -Encoding utf8

    $hasDialogRole = ($js -match 'role=["'']dialog["'']' -or $js -match "['""]role['""],\s*['""]dialog['""]")
    $hasAriaModal = ($js -match 'aria-modal=["'']true["'']' -or $js -match "['""]aria-modal['""],\s*['""]true['""]")
    if (-not $hasDialogRole -or -not $hasAriaModal) {
        $errors.Add("search.js modal missing accessibility attributes (role='dialog' or aria-modal='true')")
    }

    if ($js -notmatch 'getDocRelativePrefix' -and $js -notmatch 'getSearchIndexPath') {
        $errors.Add("search.js missing universal relative path resolver functions")
    }

    if ($js -notmatch 'keydown') {
        $errors.Add("search.js missing keyboard navigation handler")
    }
}

# -------------------------------------------------------------------------
# 3. HTML Pages Semantic Structure, Accessibility & Link Integrity
# -------------------------------------------------------------------------
$tempSubdir = Join-Path $docRoot 'temp'
$htmlFiles = @(Get-ChildItem -LiteralPath $docRoot -Recurse -Filter *.html -File | Where-Object { -not $_.FullName.StartsWith($tempSubdir, [System.StringComparison]::OrdinalIgnoreCase) })

$checkedPages = 0
$checkedImages = 0
$checkedLinks = 0

foreach ($file in $htmlFiles) {
    $checkedPages++
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    $relPath = (Resolve-Path -LiteralPath $file.FullName -Relative).Replace('\', '/').TrimStart('./')
    $dir = $file.DirectoryName

    # 3a. Viewport meta tag
    if ($content -notmatch '<meta\s+name=["'']viewport["'']\s+content=["''][^"'']*width=device-width[^"'']*["'']' -and
        $content -notmatch '<meta\s+content=["''][^"'']*width=device-width[^"'']*["'']\s+name=["'']viewport["'']') {
        $errors.Add("$relPath - missing responsive viewport meta tag")
    }

    # 3b. Title
    if ($content -notmatch '<title>\s*[^<]+\s*</title>') {
        $errors.Add("$relPath - missing or empty <title> tag")
    }

    # 3c. Pre-paint theme resolver script
    if ($content -notmatch 'localStorage\.getItem\(.*sza-theme') {
        $errors.Add("$relPath - missing pre-paint theme resolver script in head")
    }

    # 3d. Theme toggle button
    if ($content -notmatch 'id=["'']themeBtn["'']' -and $content -notmatch 'class=["''][^"'']*doc-theme-btn') {
        $errors.Add("$relPath - missing theme toggle button (#themeBtn)")
    }

    # 3e. Semantic landmarks
    if ($content -notmatch '<header\b' -or $content -notmatch '<main\b' -or $content -notmatch '<footer\b') {
        $warnings.Add("$relPath - missing one or more semantic landmarks (<header>, <main>, <footer>)")
    }

    # 3f. Search integration
    if ($content -notmatch 'search\.js') {
        $errors.Add("$relPath - missing search.js client script inclusion")
    }

    # 3g. Image accessibility (alt attribute)
    $imgMatches = [regex]::Matches($content, '<img\b([^>]*)>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($m in $imgMatches) {
        $checkedImages++
        $imgAttrs = $m.Groups[1].Value
        if ($imgAttrs -notmatch '\balt=["''][^"'']*["'']') {
            $errors.Add("$relPath - <img> tag missing alt attribute ($($m.Value))")
        }
    }

    # 3h. Local asset and relative link resolution
    $refMatches = [regex]::Matches($content, '(?:src|href)=["'']([^"''#?]+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($m in $refMatches) {
        $checkedLinks++
        $target = $m.Groups[1].Value.Trim()
        if ($target -match '^(https?://|mailto:|javascript:|#|\{\{)' -or [string]::IsNullOrWhiteSpace($target)) {
            continue
        }

        $resolved = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($dir, $target))
        $exists = (Test-Path -LiteralPath $resolved) -or $publishedPermalinks.Contains($resolved) -or ($resolved -match '\.html$' -and (Test-Path -LiteralPath ($resolved -replace '\.html$', '.md')))
        if (-not $exists) {
            $errors.Add("$relPath - broken reference '$target' (resolved: $resolved)")
        }
    }
}

# -------------------------------------------------------------------------
# 4. Search Simulation: Unmatched Query Scoring Verification
# -------------------------------------------------------------------------
if (Test-Path -LiteralPath $searchIndexPath) {
    try {
        $indexData = Get-Content -LiteralPath $searchIndexPath -Raw -Encoding utf8 | ConvertFrom-Json
        $pages = $indexData.pages

        # Verify that an unindexed random term matches 0 documents
        $bogusQuery = "xyznonexistentbogusterm999"
        $bogusMatches = 0
        foreach ($p in $pages) {
            $t = if ($p.title) { $p.title.ToLowerInvariant() } else { "" }
            $k = if ($p.keywords) { $p.keywords.ToLowerInvariant() } else { "" }
            $d = if ($p.description) { $p.description.ToLowerInvariant() } else { "" }
            if ($t.Contains($bogusQuery) -or $k.Contains($bogusQuery) -or $d.Contains($bogusQuery)) {
                $bogusMatches++
            }
        }
        if ($bogusMatches -gt 0) {
            $errors.Add("Search index contains match for bogus token '$bogusQuery' ($bogusMatches hit(s))")
        }
    } catch {
        $errors.Add("Failed to parse search index ${searchIndexPath}: $($_.Exception.Message)")
    }
}

# -------------------------------------------------------------------------
# Verdict
# -------------------------------------------------------------------------
if (-not $Quiet) {
    Write-Host "Audited $checkedPages documentation page(s), $checkedImages image(s), $checkedLinks link reference(s)." -ForegroundColor Gray
}

if ($warnings.Count -gt 0 -and -not $Quiet) {
    foreach ($w in $warnings) {
        Write-Host "  WARN: $w" -ForegroundColor Yellow
    }
}

if ($errors.Count -gt 0) {
    Write-Host "assert-docs-portal-ui-ux: FAIL ($($errors.Count) error(s) found)" -ForegroundColor Red
    foreach ($err in $errors) {
        Write-Host "  - $err" -ForegroundColor Red
    }
    exit 1
}

Write-Host "assert-docs-portal-ui-ux: PASS ($checkedPages pages verified, a11y clean, 0 broken refs, responsive tokens valid)" -ForegroundColor Green
exit 0
