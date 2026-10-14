<#
Run-Tests.ps1 - contract tests for assert-positioning-consistency.ps1 (S4102).
# Subject: docs/POSITIONING.md, _data/landing/en.json

Every case builds a throwaway tree under the system temp directory with its own positioning source,
every per-locale surface and a landing data set, so no case depends on the live site. The decisive
cases are the S4102 refusals: a portal page that misses a pillar, a landing data file that leaves a
pillar segment untranslated or copies the English text, and a data file whose generated page is
missing.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$GatePath = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-positioning-consistency.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$names = @('Alpha', 'Beta', 'Gamma', 'Delta', 'Epsilon', 'Zeta', 'Eta', 'Theta')
$canonText = "# Positioning`n`n## 2. The eight pillars`n`n" +
    ((0..7 | ForEach-Object { "$($_ + 1). **$($names[$_]).** Text." }) -join "`n") + "`n`n## 3. Next`n"
$surfaceText = '<p>We are ' + ($names -join ', ') + '.</p>'

$surfaces = @(
    'index.html', 'nolegal.html', 'README.md', 'docs/README.md', 'docs/FEATURES.md', 'docs/REPLACES.md',
    'documentation/index.html', 'documentation/overview.html',
    'play/listing/en-US/full_description.txt', 'fastlane/metadata/android/en-US/full_description.txt',
    'index-ru.html', 'nolegal-ru.html', 'docs/README-ru.md', 'docs/FEATURES-ru.md', 'docs/REPLACES-ru.md',
    'documentation/index-ru.html', 'documentation/overview-ru.html',
    'play/listing/ru-RU/full_description.txt', 'fastlane/metadata/android/ru-RU/full_description.txt',
    'index-uk.html', 'nolegal-uk.html', 'docs/README-uk.md', 'docs/FEATURES-uk.md', 'docs/REPLACES-uk.md',
    'documentation/index-uk.html', 'documentation/overview-uk.html',
    'play/listing/uk-UA/full_description.txt', 'fastlane/metadata/android/uk-UA/full_description.txt'
)

$segment = 'We are alpha, beta and the rest.'
$catalogJson = '{"_comment":"source","text":["' + $segment + '","Unrelated words."],"js":[]}'
$deJson = '{"text":{"' + $segment + '":"Wir sind Alpha, Beta und der Rest."},"js":{}}'

function New-Tree([hashtable]$override) {
    $dir = Join-Path ([System.IO.Path]::GetTempPath()) ("s4102-positioning-" + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($dir)
    $files = [ordered]@{
        'docs/POSITIONING.md'    = $canonText
        'docs/POSITIONING-ru.md' = $canonText
        'docs/POSITIONING-uk.md' = $canonText
        '_data/landing/en.json'  = $catalogJson
        '_data/landing/de.json'  = $deJson
        'index-de.html'          = '<p>de</p>'
    }
    foreach ($s in $surfaces) { $files[$s] = $surfaceText }
    foreach ($k in $override.Keys) { $files[$k] = $override[$k] }
    foreach ($k in $files.Keys) {
        if ($null -eq $files[$k]) { continue }
        $full = Join-Path $dir $k
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $full) -Force
        [System.IO.File]::WriteAllText($full, $files[$k], [System.Text.UTF8Encoding]::new($false))
    }
    return $dir
}

function Invoke-Case([string]$name, [hashtable]$override, [int]$expectedExit, [string]$expectedText) {
    $dir = New-Tree $override
    $out = & pwsh -NoProfile -File $GatePath -Root $dir 2>&1 | Out-String
    $code = $LASTEXITCODE
    $textOk = (-not $expectedText) -or $out.Contains($expectedText)
    if ($code -eq $expectedExit -and $textOk) {
        $script:passed++
        Write-Host "  PASS  $name"
    } else {
        $script:failed++
        Write-Host "  FAIL  $name (expected exit $expectedExit, got $code; text '$expectedText' found: $textOk)" -ForegroundColor Red
        Write-Host ($out.Trim() -replace '(?m)^', '        ')
    }
}

try {
    Invoke-Case 'clean tree passes' @{} 0 '28 surfaces, 8 pillars each; 1 landing languages'
    Invoke-Case 'portal overview misses a pillar' @{
        'documentation/overview-uk.html' = '<p>We are ' + (($names | Where-Object { $_ -ne 'Delta' }) -join ', ') + '.</p>'
    } 1 "documentation/overview-uk.html: pillar 4 'delta' missing"
    Invoke-Case 'landing data leaves a pillar segment out' @{
        '_data/landing/de.json' = '{"text":{},"js":{}}'
    } 1 '_data/landing/de.json: pillar segment untranslated'
    Invoke-Case 'landing data copies the English text' @{
        '_data/landing/de.json' = '{"text":{"' + $segment + '":"' + $segment + '"},"js":{}}'
    } 1 'pillar segment untranslated'
    Invoke-Case 'generated page missing' @{ 'index-de.html' = $null } 1 'index-de.html is missing'
    Invoke-Case 'missing source catalog' @{ '_data/landing/en.json' = $null } 2 'landing source catalog not found'
    Invoke-Case 'missing portal page' @{ 'documentation/index.html' = $null } 2 'file not found: documentation/index.html'
} finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-positioning-consistency tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
