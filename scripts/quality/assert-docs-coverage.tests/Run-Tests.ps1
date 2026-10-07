<#
Run-Tests.ps1 - contract tests for assert-docs-coverage.ps1 (S4102).
# Subject: docs/coverage-manifest.jsonl, docs/docs-pages-manifest.jsonl

Every case builds a throwaway tree under the system temp directory with its own inventory, coverage
manifest, page manifest and page files, so no case depends on the live corpus. The decisive cases are
the S4102 refusals: a page file missing in English or in one of the core-three siblings, a retired page
still mapped, a removed feature mapped as live or not mapped at all, and a coverage row whose feature
is in no inventory.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$GatePath = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-docs-coverage.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$inventory = @(
    '{"id":"a.one","status":"active"}'
    '{"id":"a.gone","status":"removed"}'
) -join "`n"
$coverage = @(
    '{"feature_id":"a.one","area":"A","status":"active","assigned_ticket":"S1","page_id":"p.one","is_excluded":false,"exclusion_reason":null}'
    '{"feature_id":"a.gone","area":"A","status":"removed","assigned_ticket":"S1","page_id":"p.one","is_excluded":true,"exclusion_reason":"removed"}'
) -join "`n"
$pagesManifest = '{"page_id":"p.one","canonical_path":"documentation/a/one.html","is_published":true}'

function New-Tree([hashtable]$override) {
    $dir = Join-Path ([System.IO.Path]::GetTempPath()) ("s4102-coverage-" + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($dir)
    $null = New-Item -ItemType Directory -Path (Join-Path $dir 'docs') -Force
    $null = New-Item -ItemType Directory -Path (Join-Path $dir 'documentation/a') -Force
    $files = [ordered]@{
        'docs/ALL_FEATURES.jsonl'        = $inventory
        'docs/coverage-manifest.jsonl'   = $coverage
        'docs/docs-pages-manifest.jsonl' = $pagesManifest
        'documentation/a/one.html'       = '<p>one</p>'
        'documentation/a/one-ru.html'    = '<p>one</p>'
        'documentation/a/one-uk.html'    = '<p>one</p>'
    }
    foreach ($k in $override.Keys) { $files[$k] = $override[$k] }
    foreach ($k in $files.Keys) {
        if ($null -eq $files[$k]) { continue }
        [System.IO.File]::WriteAllText((Join-Path $dir $k), $files[$k], [System.Text.UTF8Encoding]::new($false))
    }
    return $dir
}

function Invoke-Case([string]$name, [hashtable]$override, [int]$expectedExit, [string]$expectedText) {
    $dir = New-Tree $override
    $out = & pwsh -NoProfile -File $GatePath -Root $dir -SummaryOnly 2>&1 | Out-String
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
    Invoke-Case 'clean tree passes' @{} 0 'PASS'
    Invoke-Case 'missing English page file' @{ 'documentation/a/one.html' = $null } 1 'file missing: documentation/a/one.html'
    Invoke-Case 'missing RU sibling' @{ 'documentation/a/one-ru.html' = $null } 1 'file missing: documentation/a/one-ru.html'
    Invoke-Case 'retired page still mapped' @{
        'docs/docs-pages-manifest.jsonl' = '{"page_id":"p.one","canonical_path":"documentation/a/one.html","is_published":false}'
    } 1 'is retired'
    Invoke-Case 'removed feature mapped as live' @{
        'docs/coverage-manifest.jsonl' = ($coverage -replace '"status":"removed","assigned_ticket":"S1","page_id":"p.one","is_excluded":true', '"status":"active","assigned_ticket":"S1","page_id":"p.one","is_excluded":false')
    } 1 "'a.gone' is removed"
    Invoke-Case 'removed feature with no coverage row' @{
        'docs/coverage-manifest.jsonl' = ($coverage -split "`n")[0]
    } 1 "Removed feature 'a.gone' has no coverage row"
    Invoke-Case 'orphan coverage row' @{
        'docs/coverage-manifest.jsonl' = $coverage + "`n" + '{"feature_id":"a.ghost","area":"A","status":"active","page_id":"p.one","is_excluded":false}'
    } 1 "'a.ghost' names a feature in neither inventory"
    Invoke-Case 'excluded row needs no page file' @{
        'docs/coverage-manifest.jsonl' = ($coverage -replace '"page_id":"p.one","is_excluded":false,"exclusion_reason":null', '"page_id":null,"is_excluded":true,"exclusion_reason":"gap with a ticket"')
        'documentation/a/one.html'     = $null
    } 0 'PASS'
} finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-docs-coverage tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
