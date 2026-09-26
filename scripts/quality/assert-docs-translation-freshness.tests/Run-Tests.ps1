# Run-Tests.ps1 (S3540) - regression suite for assert-docs-translation-freshness.ps1.
#
# The live tree shows the gate one state only, so every case builds a synthetic corpus under
# temp/scratch (one English, one Russian and one Ukrainian recipe, the compiled pages, the core Russian and Ukrainian portal
# pages), runs the gate against it with -RepoRoot and -BaselinePath, and removes the tree in a
# finally block.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-docs-translation-freshness.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-docs-translation-freshness.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Write-TreeFile([string]$root, [string]$rel, [string]$text) {
    $full = Join-Path $root $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $full) | Out-Null
    [System.IO.File]::WriteAllText($full, $text, [System.Text.UTF8Encoding]::new($false))
}

$english = '<html><head><title>Заголовок</title></head><body><h1>Sorting files</h1><p>Open the folder and pick a sort order.</p><script>var ru = "Русский";</script></body></html>'
$russian = '<html><head><title>Sorting</title></head><body><h1>Сортировка файлов</h1><p>Откройте папку Fast Media Sorter и выберите порядок сортировки.</p><style>.x{color:red}</style></body></html>'

$ukRecipe = "---`npage_id: browsing.sorting`ncanonical_url: documentation/browsing/sorting-uk.html`n---`nСортування`n"

function New-Tree([string]$recipePage, [string[]]$baseline, [string]$ukRecipeText = $ukRecipe) {
    $root = Join-Path $repoRoot ("temp/scratch/translation-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    Write-TreeFile $root 'docs/content/recipes/sorting.md' "---`npage_id: browsing.sorting`ncanonical_url: documentation/browsing/sorting.html`n---`nSorting`n"
    Write-TreeFile $root 'docs/content/recipes-ru/sorting.md' "---`npage_id: browsing.sorting`ncanonical_url: documentation/browsing/sorting-ru.html`n---`nСортировка`n"
    Write-TreeFile $root 'docs/content/recipes-uk/sorting.md' $ukRecipeText
    Write-TreeFile $root 'documentation/browsing/sorting.html' $english
    Write-TreeFile $root 'documentation/browsing/sorting-ru.html' $recipePage
    Write-TreeFile $root 'documentation/browsing/sorting-uk.html' $russian
    foreach ($core in 'index', 'overview', 'general/glossary', 'subject-index') {
        Write-TreeFile $root "documentation/$core-ru.html" $russian
        Write-TreeFile $root "documentation/$core-uk.html" $russian
    }
    Write-TreeFile $root 'baseline.txt' ("# test baseline`n" + ($baseline -join "`n"))
    return $root
}

function Test-Case([string]$name, [string]$recipePage, [string[]]$baseline, [int]$expectedExit, [string]$expectedText, [string]$ukRecipeText = $ukRecipe) {
    $root = New-Tree $recipePage $baseline $ukRecipeText
    try {
        $out = & $pwshExe -NoProfile -File $gateScript -RepoRoot $root -BaselinePath (Join-Path $root 'baseline.txt') 2>&1 | Out-String
        $code = $LASTEXITCODE
        $ok = $code -eq $expectedExit -and (-not $expectedText -or $out.Contains($expectedText))
        Assert-That $name $ok "expected exit $expectedExit '$expectedText', got exit ${code}:`n$out"
    }
    finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "=== assert-docs-translation-freshness regression suite ===" -ForegroundColor Cyan

$page = 'documentation/browsing/sorting-ru.html'
Test-Case 'a translated page passes' $russian @() 0 '10 of 10 translated'
Test-Case 'an English page under a -ru name fails' $english @() 1 "below Cyrillic threshold: $page"
Test-Case 'Cyrillic in head and script does not count' $english @() 1 'Cyrillic share 0.00'
Test-Case 'a baselined untranslated page passes' $english @($page) 0 '1 untranslated and baselined'
Test-Case 'a stale baseline row fails' $russian @($page) 1 "Stale baseline row: $page"
Test-Case 'a baseline row for a missing page fails' $russian @('documentation/gone-ru.html') 1 'Stale baseline row: documentation/gone-ru.html'
$driftedUk = "---`ncanonical_url: documentation/browsing/sorting-uk.html`npage_id: browsing.sorting`n---`nСортування`n"
Test-Case 'a reordered key skeleton in a translation fails' $russian @() 1 'YAML key-sequence drift in UK: sorting.md' $driftedUk

Write-Host ""
Write-Host "Summary: $script:pass passed, $script:fail failed" -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
