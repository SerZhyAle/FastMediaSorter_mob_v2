<#
Run-Tests.ps1 - contract tests for assert-page-content.ps1 (S3452).

Every case builds a throwaway site tree under the system temp directory, so no case depends on what
the live pages carry this minute.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-page-content.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()
$pageNames = @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html')

$cleanPage = @'
<!DOCTYPE html>
<html><head><link rel="stylesheet" href="styles.css"></head><body>
    <header class="site-header">
        <a class="brand" href="index.html">Brand</a>
        <button class="theme-btn">&#9680;</button>
        <a class="btn btn-primary btn-sm" href="#get">Install</a>
    </header>
    <header class="hero" id="top">
        <h1>Sort files on your phone.</h1>
        <p class="subtitle">For photos and documents.</p>
        <p class="whatfor">An Android app for people who sort folders.</p>
    </header>
    <section class="get-panel" id="get"><h2>Open your first folder</h2></section>
    <script>var label = 'Open';</script>
    <footer class="site-footer"><h2>More tools</h2></footer>
</body></html>
'@

function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('s3452-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    foreach ($name in $pageNames) { Set-Content -LiteralPath (Join-Path $root $name) -Value $cleanPage -Encoding utf8 }
    return $root
}

function Set-Page([string]$root, [string]$name, [string]$text) {
    Set-Content -LiteralPath (Join-Path $root $name) -Value $text -Encoding utf8
}

function Invoke-Case([string]$Name, [string]$Root, [int]$ExpectedExit, [string]$ExpectedText) {
    $out = & pwsh -NoProfile -File $Gate -Root $Root 2>&1 | Out-String
    $code = $LASTEXITCODE
    $ok = ($code -eq $ExpectedExit) -and (-not $ExpectedText -or $out -match [regex]::Escape($ExpectedText))
    if ($ok) {
        $script:passed++
        Write-Host "  PASS $Name"
    }
    else {
        $script:failed++
        Write-Host "  FAIL $Name - expected exit $ExpectedExit with '$ExpectedText', got exit $code" -ForegroundColor Red
        Write-Host $out
    }
}

$magnifier = [char]::ConvertFromUtf32(0x1F50D)
$cross = [char]::ConvertFromUtf32(0x274C)
# Built from its parts so no editor or tool decodes the escape into the glyph it stands for.
$escapedMagnifier = ([char]92) + 'uD83D' + ([char]92) + 'uDD0D'

try {
    $r = New-Fixture
    Invoke-Case 'clean tree passes' $r 0 'PASS (6 pages)'

    $r = New-Fixture
    Set-Page $r 'index.html' ($cleanPage -replace '<h2>Open your first folder</h2>', "<h2>$magnifier Open</h2>")
    Invoke-Case 'a literal emoji in markup fails' $r 1 'FAIL [EMOJI] index.html:13: U+1F50D'

    $r = New-Fixture
    Set-Page $r 'index-ru.html' ($cleanPage.Replace("var label = 'Open';", "var label = '$cross';"))
    Invoke-Case 'a literal emoji inside a script fails' $r 1 'FAIL [EMOJI] index-ru.html:14: U+274C'

    $r = New-Fixture
    Set-Page $r 'index-uk.html' ($cleanPage.Replace("var label = 'Open';", "var label = '$escapedMagnifier';"))
    Invoke-Case 'a surrogate-escaped emoji fails' $r 1 'FAIL [EMOJI] index-uk.html:14: escaped emoji \uD83D'

    $r = New-Fixture
    Set-Page $r 'nolegal.html' ($cleanPage -replace '<h2>Open your first folder</h2>', '<h2>&#x1F50D; Open</h2>')
    Invoke-Case 'an entity emoji fails' $r 1 'FAIL [EMOJI] nolegal.html:13: escaped emoji &#x1F50D;'

    $r = New-Fixture
    Set-Page $r 'nolegal-ru.html' ($cleanPage -replace 'href="#get"', 'href="#download"')
    Invoke-Case 'a header link to #download fails' $r 1 'FAIL [ORDER] nolegal-ru.html: the site-header has no link to #get'

    $r = New-Fixture
    Set-Page $r 'nolegal-uk.html' ($cleanPage -replace '<h2>Open your first folder</h2>', '<h1>Second</h1>')
    Invoke-Case 'a second H1 fails' $r 1 'FAIL [ORDER] nolegal-uk.html: 2 H1 elements'

    $r = New-Fixture
    Set-Page $r 'index.html' ($cleanPage -replace '<p class="subtitle">[^<]*</p>', '')
    Invoke-Case 'a missing tagline fails' $r 1 'FAIL [ORDER] index.html: no tagline'

    $r = New-Fixture
    Set-Page $r 'index-ru.html' ($cleanPage -replace '<p class="whatfor">[^<]*</p>', '')
    Invoke-Case 'a missing lead fails' $r 1 'FAIL [ORDER] index-ru.html: no what-it-is lead'

    $r = New-Fixture
    $early = $cleanPage -replace '(?s)(<header class="hero".*?</header>)\s*(<section class="get-panel".*?</section>)', '$2$1'
    Set-Page $r 'index-uk.html' $early
    Invoke-Case '#get before the H1 fails' $r 1 'FAIL [ORDER] index-uk.html: the sequence site-header, H1, #get, site-footer is broken'

    $r = New-Fixture
    $late = $cleanPage -replace '(?s)(<section class="get-panel".*?</section>)(.*?)(<footer class="site-footer">.*?</footer>)', '$3$2$1'
    Set-Page $r 'nolegal.html' $late
    Invoke-Case 'a footer before #get fails' $r 1 'FAIL [ORDER] nolegal.html: the sequence site-header, H1, #get, site-footer is broken'

    $r = New-Fixture
    Remove-Item -LiteralPath (Join-Path $r 'nolegal-uk.html')
    Invoke-Case 'a missing page cannot verify' $r 2 'page not found: nolegal-uk.html'
}
finally {
    foreach ($dir in $fixtures) { Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-page-content tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
