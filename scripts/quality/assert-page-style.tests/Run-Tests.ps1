<#
Run-Tests.ps1 - contract tests for assert-page-style.ps1 (S3453).

Every case builds a throwaway site tree and a throwaway catalog under the system temp directory,
so no case depends on what the live pages or the live registry carry this minute.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-page-style.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()
$pageNames = @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html')

$cleanPage = @'
<!DOCTYPE html>
<html><head>
    <script>
        (function () { var t = localStorage.getItem('sza-theme') || 'dark'; document.documentElement.dataset.theme = t; })();
    </script>
    <link rel="stylesheet" href="styles.css">
</head><body>
    <nav class="seg">
        <a href="index-ru.html" data-lang="ru">RU</a>
        <a href="index.html" class="on" data-lang="en">EN</a>
        <a href="index-uk.html" data-lang="ua">UA</a>
    </nav>
</body></html>
'@

$exceptionRow = '| `PAGE-STYLE` | FastMediaSorter website | section 0 step 1: `reference/sza-kit.css` is not vendored | open | {0} |'

function New-Fixture([string]$until = '2026-12-31') {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('s3453-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    $site = Join-Path $root 'site'
    $catalog = Join-Path $root 'catalog'
    foreach ($dir in $site, (Join-Path $catalog '_meta'), (Join-Path $catalog 'product-web-pages/reference')) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    foreach ($name in $pageNames) { Set-Content -LiteralPath (Join-Path $site $name) -Value $cleanPage -Encoding utf8 }
    Set-Content -LiteralPath (Join-Path $catalog 'product-web-pages/reference/sza-kit.css') -Value ":root { --kit: 1; }`n" -Encoding utf8 -NoNewline
    $row = if ($until) { $exceptionRow -f $until } else { '' }
    $registry = "# Registry`n`n## 2. Adoption`n`n$($exceptionRow -f '2099-01-01')`n`n## 3. Exceptions`n`n| Contract | Product | Deviation | Status | Until |`n| --- | --- | --- | --- | --- |`n$row`n`n## 4. Planned`n"
    Set-Content -LiteralPath (Join-Path $catalog '_meta/REGISTRY.md') -Value $registry -Encoding utf8
    return [pscustomobject]@{ Site = $site; Catalog = $catalog }
}

function Set-Page($fixture, [string]$name, [string]$text) {
    Set-Content -LiteralPath (Join-Path $fixture.Site $name) -Value $text -Encoding utf8
}

function Invoke-Case {
    param(
        [string]$Name,
        [object]$Fixture,
        [int]$ExpectedExit,
        [string]$ExpectedText,
        [string]$CatalogOverride
    )
    $catalog = if ($PSBoundParameters.ContainsKey('CatalogOverride')) { $CatalogOverride } else { $Fixture.Catalog }
    $out = & pwsh -NoProfile -File $Gate -Root $Fixture.Site -CatalogRoot $catalog -Today '2026-09-24' 2>&1 | Out-String
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

try {
    $f = New-Fixture
    Invoke-Case 'clean tree with an open exception passes' $f 0 'registry exception open until 2026-12-31'

    $f = New-Fixture
    Set-Page $f 'nolegal-uk.html' ($cleanPage -replace 'data-lang="ua">UA<', 'data-lang="ua">UK<')
    Invoke-Case 'a UK label fails' $f 1 'FAIL [LANG] nolegal-uk.html: a language link is labelled UK'

    $f = New-Fixture
    Set-Page $f 'index.html' ($cleanPage -replace '<nav class="seg">', '<div class="lang-switcher">')
    Invoke-Case 'leftover lang-switcher fails' $f 1 'FAIL [LANG] index.html: pre-kit lang-switcher'

    $f = New-Fixture
    Set-Page $f 'index-ru.html' ($cleanPage -replace 'sza-theme', 'theme')
    Invoke-Case 'a missing pre-paint fails' $f 1 'FAIL [THEME] index-ru.html: no <script> reads sza-theme'

    $f = New-Fixture
    $late = $cleanPage -replace '(?s)(<script>.*?</script>)\s*(<link[^>]*>)', '$2$1'
    Set-Page $f 'nolegal.html' $late
    Invoke-Case 'a pre-paint after the stylesheet fails' $f 1 'FAIL [THEME] nolegal.html: the sza-theme pre-paint script stands after'

    $f = New-Fixture ''
    Invoke-Case 'no vendored kit and no exception fails' $f 1 'no open PAGE-STYLE exception'

    $f = New-Fixture '2026-09-01'
    Invoke-Case 'an expired exception fails' $f 1 'expired on 2026-09-01'

    $f = New-Fixture ''
    Copy-Item -LiteralPath (Join-Path $f.Catalog 'product-web-pages/reference/sza-kit.css') -Destination (Join-Path $f.Site 'sza-kit.css')
    foreach ($name in $pageNames) {
        Set-Page $f $name ($cleanPage -replace '<link rel="stylesheet" href="styles.css">', "<link rel=`"stylesheet`" href=`"sza-kit.css`">`n    <link rel=`"stylesheet`" href=`"styles.css`">")
    }
    Invoke-Case 'a byte-identical vendored kit passes with no exception' $f 0 'PASS (6 pages)'

    Set-Content -LiteralPath (Join-Path $f.Site 'sza-kit.css') -Value ":root { --kit: 2; }`n" -Encoding utf8 -NoNewline
    Invoke-Case 'a vendored kit that differs fails' $f 1 'differs from the catalog reference kit'

    $f = New-Fixture
    Invoke-Case 'no catalog cannot verify' $f 2 'CANNOT VERIFY' -CatalogOverride (Join-Path $f.Site 'no-such-catalog')

    $f = New-Fixture
    Remove-Item -LiteralPath (Join-Path $f.Site 'index-uk.html')
    Invoke-Case 'a missing page cannot verify' $f 2 'page not found: index-uk.html'
}
finally {
    foreach ($dir in $fixtures) { Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-page-style tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
