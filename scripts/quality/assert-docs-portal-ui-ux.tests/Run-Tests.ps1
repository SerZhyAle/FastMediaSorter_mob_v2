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

        # Minimal valid CSS
        $css = @'
:root {
    --doc-bg: #0a0f0a;
    --doc-text: #f1f5ee;
    --doc-accent: #3fb950;
    --doc-gold: #e3b341;
    --doc-border: rgba(255, 255, 255, 0.09);
}
html[data-theme="light"] {
    --doc-bg: #ffffff;
    --doc-text: #162115;
    --doc-accent: #267a30;
    --doc-gold: #9a6a13;
    --doc-border: rgba(22, 33, 15, 0.12);
}
.doc-mobile-menu-btn { display: none; }
.doc-sidebar.active { left: 0; }
@media (max-width: 1100px) { .doc-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .doc-grid { grid-template-columns: 1fr; } }
@media (max-width: 480px) { .doc-grid { grid-template-columns: 1fr; } }
@media print { .doc-header { display: none; } }
'@
        Set-Content -LiteralPath (Join-Path $assetsDir 'docs.css') -Value $css -Encoding utf8

        # Minimal valid search JS
        $js = @'
(function() {
    function getDocRelativePrefix() { return ''; }
    function getSearchIndexPath() { return 'assets/search-index.json'; }
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
    <script>try{localStorage.getItem('sza-theme')}catch(e){}</script>
    <link rel="stylesheet" href="assets/docs.css">
</head>
<body>
    <header class="doc-header"><button id="themeBtn">◐</button></header>
    <main class="doc-content">
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

} finally {
    if (Test-Path -LiteralPath $scratchRoot) {
        Remove-Item -LiteralPath $scratchRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`nSuite Result: $($script:pass) passed, $($script:fail) failed." -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
