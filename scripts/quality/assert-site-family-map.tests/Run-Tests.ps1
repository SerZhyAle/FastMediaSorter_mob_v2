<#
Run-Tests.ps1 - contract tests for assert-site-family-map.ps1 and render-family-footer.ps1 (S3454).

Every case builds a throwaway site tree and a throwaway catalog under the system temp directory, so no
case depends on what the live pages or the live catalog carry this minute.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-site-family-map.ps1')).Path
$Renderer = (Resolve-Path (Join-Path $PSScriptRoot '..\..\site\render-family-footer.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()
$pageNames = @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html')

$page = @'
<!DOCTYPE html>
<html><body>
    <footer class="site-footer">
        <div class="container">
            <div class="tools-grid">
                <a href="https://old.example/"><b>Old</b><span>stale</span></a>
            </div>
            <a href="https://github.com/SerZhyAle">GitHub</a> <a href="mailto:sza@ukr.net">sza@ukr.net</a>
        </div>
    </footer>
</body></html>
'@

$map = @'
# Site family map

## 2. The map

| Tool | Type | URL |
| --- | --- | --- |
| This product | Android | https://example.github.io/this/ |
| Tool A | Windows | https://example.github.io/a/ |
| SZA (hub) | Portfolio | https://sza.example |

## 3. The rules
'@

$sourceJson = @'
{
  "self": "https://example.github.io/this/",
  "tools": [
    { "url": "https://example.github.io/a/", "name": "Tool A", "caption": { "en": "A tool", "ru": "Инструмент", "uk": "Інструмент" } },
    { "url": "https://sza.example", "name": "SZA", "caption": { "en": "Hub", "ru": "Хаб", "uk": "Хаб" } }
  ]
}
'@

function New-Fixture {
    $base = Join-Path ([System.IO.Path]::GetTempPath()) ('s3454-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($base)
    $root = Join-Path $base 'site'
    $catalog = Join-Path $base 'catalog'
    New-Item -ItemType Directory -Path (Join-Path $root 'scripts/site'), (Join-Path $catalog 'product-web-pages') -Force | Out-Null
    foreach ($name in $pageNames) { Set-Content -LiteralPath (Join-Path $root $name) -Value $page -Encoding utf8 }
    Set-Content -LiteralPath (Join-Path $root 'README.md') -Value "Contact sza@ukr.net, https://github.com/SerZhyAle" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root '_config.yml') -Value "url: `"https://example.github.io`"`nbaseurl: `"/this`"" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'scripts/site/family-footer.json') -Value $sourceJson -Encoding utf8
    Set-Content -LiteralPath (Join-Path $catalog 'product-web-pages/SITE-FAMILY-MAP.md') -Value $map -Encoding utf8
    & pwsh -NoProfile -File $Renderer -Root $root -Quiet | Out-Null
    return [pscustomobject]@{ Root = $root; Catalog = $catalog }
}

function Edit-File([string]$path, [string]$from, [string]$to) {
    $text = Get-Content -LiteralPath $path -Raw -Encoding utf8
    Set-Content -LiteralPath $path -Value $text.Replace($from, $to) -Encoding utf8 -NoNewline
}

function Invoke-Case([string]$Name, $Fixture, [int]$ExpectedExit, [string]$ExpectedText, [string]$Catalog) {
    $cat = if ($PSBoundParameters.ContainsKey('Catalog')) { $Catalog } else { $Fixture.Catalog }
    $out = & pwsh -NoProfile -File $Gate -Root $Fixture.Root -CatalogRoot $cat 2>&1 | Out-String
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
    $ru = Get-Content -LiteralPath (Join-Path $f.Root 'index-ru.html') -Raw -Encoding utf8
    if ($ru.Contains('<span>Инструмент</span>') -and -not $ru.Contains('old.example')) { $passed++; Write-Host '  PASS renderer writes the page locale' }
    else { $failed++; Write-Host '  FAIL renderer writes the page locale' -ForegroundColor Red }
    Invoke-Case 'clean tree passes' $f 0 'PASS (6 pages, 3 map rows)'

    $f = New-Fixture
    Edit-File (Join-Path $f.Root 'nolegal.html') '<span>A tool</span>' '<span>A hand edit</span>'
    Invoke-Case 'a hand-edited page block fails' $f 1 'FAIL [RENDER] nolegal.html: block differs from the source'

    $f = New-Fixture
    Edit-File (Join-Path $f.Catalog 'product-web-pages/SITE-FAMILY-MAP.md') '| SZA (hub)' "| Tool B | Windows | https://example.github.io/b/ |`n| SZA (hub)"
    Invoke-Case 'a map row missing from the source fails' $f 1 "map row 'Tool B' (https://example.github.io/b) is missing"

    $f = New-Fixture
    Edit-File (Join-Path $f.Root 'scripts/site/family-footer.json') 'https://example.github.io/a/' 'https://example.github.io/z/'
    & pwsh -NoProfile -File $Renderer -Root $f.Root -Quiet | Out-Null
    Invoke-Case 'a source URL the map lacks fails' $f 1 'links https://example.github.io/z, which the map does not list'

    $f = New-Fixture
    Edit-File (Join-Path $f.Root '_config.yml') '/this' '/renamed'
    Invoke-Case 'a canonical address differing from the map fails' $f 1 'FAIL [SELF]'

    $f = New-Fixture
    Edit-File (Join-Path $f.Root 'README.md') 'sza@ukr.net' 'sza@ukr.net, other@example.com'
    Invoke-Case 'a second email address fails' $f 1 'README.md: states other@example.com'

    $f = New-Fixture
    Edit-File (Join-Path $f.Root 'index-uk.html') 'mailto:sza@ukr.net">sza@ukr.net' 'mailto:x">x'
    Invoke-Case 'a page without the contact fails' $f 1 'index-uk.html: does not state sza@ukr.net'

    $f = New-Fixture
    Invoke-Case 'an absent catalog cannot verify' $f 2 'CANNOT VERIFY' (Join-Path $f.Root 'no-catalog')

    $f = New-Fixture
    Remove-Item -LiteralPath (Join-Path $f.Root 'nolegal-ru.html')
    Invoke-Case 'a missing page cannot verify' $f 2 'CANNOT VERIFY'
}
finally {
    foreach ($dir in $fixtures) { Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-site-family-map suite: $passed passed, $failed failed"
exit ([int]($failed -gt 0))
