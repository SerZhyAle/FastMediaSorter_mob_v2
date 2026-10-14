#requires -Version 7.0
<#
.SYNOPSIS
    Test Suite: regression tests for assert-docs-portal-ui-ux.ps1.
    Part of S3533 (documentation-portal-ui-ux-testing).

.DESCRIPTION
    Tests both the live documentation tree and isolated synthetic fixtures:
    - Live tree verification (passes exit 0).
    - Synthetic valid tree (exit 0).
    - Missing viewport tag detection (exit 1).
    - Missing theme resolver or button detection (exit 1).
    - Image missing alt attribute detection (exit 1).
    - Broken local reference detection (exit 1).
    - Jekyll markdown source target resolution (exit 0).
    - CSS token validation (exit 1 when token missing).
    - PAGE-STYLE 1.0 pre-paint resolver and a missing sza-lang writer (exit 1).
    - SITE-EXPERIENCE rules 2, 3, 4, 12 and 15 on the portal layer (S4099): a copied kit colour, the
      kit linked after the layer, a style attribute or element, a missing component or a doubled
      edition badge, an empty search result without the query, a page without its skip link.

.NOTES
    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-docs-portal-ui-ux.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

Write-Host "=== assert-docs-portal-ui-ux Test Suite ===" -ForegroundColor Cyan

# -------------------------------------------------------------------------
# Test 1: Live repository run
# -------------------------------------------------------------------------
$liveOutput = & $pwshExe -NoProfile -File $gateScript 2>&1
$liveExit = $LASTEXITCODE
Assert-That "Live documentation tree passes gate" ($liveExit -eq 0) "Exit code was $liveExit, output: $($liveOutput -join '; ')"

# -------------------------------------------------------------------------
# Synthetic Fixture Tests
# -------------------------------------------------------------------------
$scratchRoot = Join-Path $repoRoot 'temp/S3533/ui-ux-tests'
if (Test-Path -LiteralPath $scratchRoot) {
    Remove-Item -LiteralPath $scratchRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $scratchRoot -Force | Out-Null

try {
    function Initialize-SyntheticDocTree([string]$baseDir) {
        $docDir = Join-Path $baseDir 'documentation'
        $assetsDir = Join-Path $docDir 'assets'
        $docsDir = Join-Path $baseDir 'docs'
        New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
        New-Item -ItemType Directory -Path $docsDir -Force | Out-Null

        # The kit tokens, carried by the product layer the portal links first
        $kit = @'
:root { --bg: #0a0f0a; --text: #f1f5ee; --acc: #3fb950; --gold: #e3b341; --border: rgba(255, 255, 255, 0.08); }
html[data-theme="light"] { --bg: #eef3ea; --acc-strong: #267a30; --bg-2: #ffffff; }
'@
        Set-Content -LiteralPath (Join-Path $baseDir 'styles.css') -Value $kit -Encoding utf8

        # Minimal valid CSS
        $css = @'
:root {
    --doc-bg: var(--bg);
    --doc-text: var(--text);
    --doc-accent: var(--acc);
    --doc-gold: var(--gold);
    --doc-border: var(--border);
}
html[data-theme="light"] {
    --doc-bg: var(--bg);
    --doc-text: var(--text);
    --doc-accent: var(--acc-strong);
    --doc-gold: var(--gold);
    --doc-border: var(--border);
}
.doc-menu-path-item { font-weight: 600; }
.doc-edition-badge { display: inline-flex; }
.doc-mobile-menu-btn { display: none; }
.doc-sidebar.active { left: 0; }
@media (max-width: 1100px) { .doc-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .doc-grid { grid-template-columns: 1fr; } }
@media (max-width: 480px) { .doc-grid { grid-template-columns: 1fr; } }
@media print { .doc-header { display: none; } body { background: #ffffff; } }
'@
        Set-Content -LiteralPath (Join-Path $assetsDir 'docs.css') -Value $css -Encoding utf8

        # Minimal valid search JS
        $js = @'
(function() {
    var messages = { en: { empty: 'No guides match "{query}".' }, ru: { empty: 'Нет руководств «{query}».' }, uk: { empty: 'Немає посібників «{query}».' } };
    function getDocRelativePrefix() { return ''; }
    function getSearchIndexPath() { return 'assets/search-index.json'; }
    function emptyResult(query) {
        return getDocRelativePrefix() + 'subject-index.html' + query;
    }
    function search(query) { emptyResult(query); }
    var modal = document.createElement('div');
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    document.addEventListener('keydown', function(e) {});
})();
'@
        Set-Content -LiteralPath (Join-Path $assetsDir 'search.js') -Value $js -Encoding utf8

        # Minimal valid search index
        $index = @'
{ "version": "1.0", "total_pages": 1, "pages": [
    { "page_id": "home", "title": "Home", "url": "documentation/index.html", "category": "General", "description": "Home", "keywords": "home" }
]}
'@
        Set-Content -LiteralPath (Join-Path $assetsDir 'search-index.json') -Value $index -Encoding utf8

        # Minimal valid HTML
        $html = @'
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Doc Portal</title>
    <script>try{var t=localStorage.getItem('sza-theme');if(t !== 'dark' && t !== 'light')t='dark';var l=localStorage.getItem('sza-lang');if(l === 'uk') l = 'ua';}catch(e){}</script>
    <link rel="stylesheet" href="../styles.css">
    <link rel="stylesheet" href="assets/docs.css">
</head>
<body>
    <a class="doc-skip-link" href="#main-content">Skip to content</a>
    <header class="doc-header"><button id="themeBtn">◐</button></header>
    <main class="doc-content" id="main-content">
        <div class="doc-meta-row"><span class="doc-badge">Section</span><span class="doc-edition-badge">All editions</span></div>
        <img src="assets/docs.css" alt="Valid asset reference">
        <a href="../docs/PRIVACY_POLICY.html">Privacy</a>
    </main>
    <footer class="doc-footer">Footer</footer>
    <script src="assets/search.js"></script>
</body>
</html>
'@
        Set-Content -LiteralPath (Join-Path $docDir 'index.html') -Value $html -Encoding utf8

        # Jekyll markdown source
        Set-Content -LiteralPath (Join-Path $docsDir 'PRIVACY_POLICY.md') -Value '# Privacy Policy' -Encoding utf8
    }

    # Test 2: Synthetic valid tree passes
    $case2 = Join-Path $scratchRoot 'case2-valid'
    Initialize-SyntheticDocTree $case2
    $out2 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case2 2>&1
    Assert-That "Synthetic valid documentation tree passes" ($LASTEXITCODE -eq 0) ($out2 -join '; ')

    # Test 3: Missing viewport fails
    $case3 = Join-Path $scratchRoot 'case3-no-viewport'
    Initialize-SyntheticDocTree $case3
    (Get-Content (Join-Path $case3 'documentation/index.html') -Raw) -replace '<meta name="viewport"[^>]+>', '' | Set-Content (Join-Path $case3 'documentation/index.html')
    $out3 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case3 2>&1
    Assert-That "Missing viewport tag triggers failure" ($LASTEXITCODE -ne 0 -and ($out3 -match 'missing responsive viewport')) ($out3 -join '; ')

    # Test 4: Image missing alt attribute fails
    $case4 = Join-Path $scratchRoot 'case4-no-alt'
    Initialize-SyntheticDocTree $case4
    (Get-Content (Join-Path $case4 'documentation/index.html') -Raw) -replace 'alt="[^"]*"', '' | Set-Content (Join-Path $case4 'documentation/index.html')
    $out4 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case4 2>&1
    Assert-That "Image missing alt attribute triggers failure" ($LASTEXITCODE -ne 0 -and ($out4 -match 'missing alt attribute')) ($out4 -join '; ')

    # Test 5: Broken reference fails
    $case5 = Join-Path $scratchRoot 'case5-broken-ref'
    Initialize-SyntheticDocTree $case5
    (Get-Content (Join-Path $case5 'documentation/index.html') -Raw) -replace 'assets/docs.css', 'assets/nonexistent.css' | Set-Content (Join-Path $case5 'documentation/index.html')
    $out5 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case5 2>&1
    Assert-That "Broken link/asset reference triggers failure" ($LASTEXITCODE -ne 0 -and ($out5 -match 'broken reference')) ($out5 -join '; ')

    # Test 6: Missing light theme tokens in CSS fails
    $case6 = Join-Path $scratchRoot 'case6-missing-css'
    Initialize-SyntheticDocTree $case6
    (Get-Content (Join-Path $case6 'documentation/assets/docs.css') -Raw) -replace 'html\[data-theme="light"\]', '/* stripped */' | Set-Content (Join-Path $case6 'documentation/assets/docs.css')
    $out6 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case6 2>&1
    Assert-That "Missing CSS light theme tokens triggers failure" ($LASTEXITCODE -ne 0 -and ($out6 -match 'missing light theme')) ($out6 -join '; ')

    # Test 7: the PAGE-STYLE 1.0 resolver, trusting any stored theme and ignoring sza-lang, fails (S4074)
    $case7 = Join-Path $scratchRoot 'case7-old-resolver'
    Initialize-SyntheticDocTree $case7
    (Get-Content (Join-Path $case7 'documentation/index.html') -Raw) -replace '<script>try\{var t=[^<]+</script>', "<script>try{var t=localStorage.getItem('sza-theme')||'dark';}catch(e){}</script>" | Set-Content (Join-Path $case7 'documentation/index.html')
    $out7 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case7 2>&1
    Assert-That "PAGE-STYLE 1.0 pre-paint resolver triggers failure" ($LASTEXITCODE -ne 0 -and ($out7 -match 'not the PAGE-STYLE 1.2 form')) ($out7 -join '; ')

    # Test 8: data-lang links without a sza-lang writer fail (S4074)
    $case8 = Join-Path $scratchRoot 'case8-no-lang-writer'
    Initialize-SyntheticDocTree $case8
    (Get-Content (Join-Path $case8 'documentation/index.html') -Raw) -replace '</header>', '<a href="?lang=ru" data-lang="ru">RU</a></header>' | Set-Content (Join-Path $case8 'documentation/index.html')
    $out8 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case8 2>&1
    Assert-That "data-lang links without a sza-lang writer trigger failure" ($LASTEXITCODE -ne 0 -and ($out8 -match 'without a sza-lang writer')) ($out8 -join '; ')

    # Both supported index schemas retain the same search and accessibility guarantees.
    $case9 = Join-Path $scratchRoot 'case9-sharded-index'
    Initialize-SyntheticDocTree $case9
    $assets9 = Join-Path $case9 'documentation/assets'
    $original9 = Get-Content (Join-Path $assets9 'search-index.json') -Raw
    Set-Content (Join-Path $assets9 'search-pages-en-01.json') $original9 -Encoding utf8
    Set-Content (Join-Path $assets9 'search-index.json') '{"version":"2.0","total_pages":1,"chunks":[{"file":"search-pages-en-01.json","lang":"en"}]}' -Encoding utf8
    $out9 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case9 2>&1
    Assert-That "Sharded search index passes" ($LASTEXITCODE -eq 0) ($out9 -join '; ')
    Set-Content (Join-Path $assets9 'search-index.json') '{"version":"2.0","total_pages":1,"chunks":[{"file":"../outside.json","lang":"en"}]}' -Encoding utf8
    $out10 = & $pwshExe -NoProfile -File $gateScript -RepoRoot $case9 2>&1
    Assert-That "Search shard traversal is refused" ($LASTEXITCODE -ne 0 -and ($out10 -match 'Invalid search shard name')) ($out10 -join '; ')

    # SITE-EXPERIENCE rules 2, 3, 4, 12 and 15 (S4099): one change per case against the valid tree.
    function Test-PortalRule([string]$name, [string]$relative, [string]$pattern, [string]$replacement, [string]$expected) {
        $caseDir = Join-Path $scratchRoot ('s4099-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
        Initialize-SyntheticDocTree $caseDir
        $target = Join-Path $caseDir $relative
        $text = Get-Content -LiteralPath $target -Raw
        $changed = $text -replace $pattern, $replacement
        if ($changed -ceq $text) { Assert-That $name $false "fixture edit '$pattern' changed nothing"; return }
        Set-Content -LiteralPath $target -Value $changed -Encoding utf8
        $out = & $pwshExe -NoProfile -File $gateScript -RepoRoot $caseDir 2>&1
        $code = $LASTEXITCODE
        if ($expected) {
            Assert-That $name ($code -ne 0 -and (($out -join "`n") -match [regex]::Escape($expected))) ($out -join '; ')
        } else {
            Assert-That $name ($code -eq 0) ($out -join '; ')
        }
    }

    Test-PortalRule 'A kit colour copied into docs.css fails (rule 2)' 'documentation/assets/docs.css' '--doc-accent: var\(--acc\);' '--doc-accent: #3FB950;' 'copies the kit value #3FB950 of --acc'
    Test-PortalRule 'A kit colour in a print rule is not judged (rule 2)' 'documentation/assets/docs.css' 'body \{ background: #ffffff; \}' 'body { background: #ffffff; color: #0a0f0a; }' ''
    Test-PortalRule 'The kit linked after docs.css fails (rule 2)' 'documentation/index.html' '(<link rel="stylesheet" href="\.\./styles\.css">)(\s*)(<link rel="stylesheet" href="assets/docs\.css">)' '$3$2$1' 'is not linked before docs.css'
    Test-PortalRule 'A missing menu-path component fails (rule 3)' 'documentation/assets/docs.css' '\.doc-menu-path-item \{ font-weight: 600; \}' '' 'does not define the .doc-menu-path-item component'
    Test-PortalRule 'A badge row with two edition badges fails (rule 3)' 'documentation/index.html' '(<span class="doc-edition-badge">All editions</span>)' '$1$1' 'exactly one edition badge'
    Test-PortalRule 'A style attribute fails (rule 4)' 'documentation/index.html' '<footer class="doc-footer">' '<footer class="doc-footer" style="margin-top: 2rem;">' '1 style attribute(s)'
    Test-PortalRule 'A style element fails (rule 4)' 'documentation/index.html' '</head>' '<style>.x { color: red; }</style></head>' 'a <style> element'
    Test-PortalRule 'A style string inside a script is not judged (rule 4)' 'documentation/index.html' '<script src="assets/search.js"></script>' '<script>var html = ''<b style="color: red">x</b>'';</script><script src="assets/search.js"></script>' ''
    Test-PortalRule 'An empty search result without the query fails (rule 12)' 'documentation/assets/search.js' '\{query\}' '' 'SITE-EXPERIENCE rule 12'
    Test-PortalRule 'A page without its skip link fails (rule 15)' 'documentation/index.html' '<a class="doc-skip-link" href="#main-content">Skip to content</a>' '' 'skip link to #main-content'
    Test-PortalRule 'A skip link after the chrome fails (rule 15)' 'documentation/index.html' '(<a class="doc-skip-link"[^>]*>[^<]*</a>)(\s*)(<header class="doc-header">.*?</header>)' '$3$2$1' 'skip link to #main-content'
} finally {
    if (Test-Path -LiteralPath $scratchRoot) {
        Remove-Item -LiteralPath $scratchRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`nSuite Result: $($script:pass) passed, $($script:fail) failed." -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
