<#
.SYNOPSIS
    Contract tests for assert-showcase-inventory.ps1.
.DESCRIPTION
Run-Tests.ps1 - contract tests for assert-showcase-inventory.ps1 (S4102).
# Subject: docs/FEATURES.md, docs/WHATS_NEW.md, docs/ALL_FEATURES.jsonl

Every case builds a throwaway tree under the system temp directory with its own inventory, showcase
and release notes, so no case depends on what the live showcase says this minute. The decisive cases
are the refusals: an unanchored bullet, an unknown id, a removed id, a noLegal-only id in a public
file, an RU anchor that differs from EN, and a missing file.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$GatePath = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-showcase-inventory.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$inventory = @(
    '{"id":"a.one","flavors":["standard","noLegal"],"status":"active"}'
    '{"id":"a.two","flavors":["standard"],"status":"active"}'
    '{"id":"a.gone","flavors":["standard"],"status":"removed"}'
    '{"id":"a.side","flavors":["noLegal"],"status":"active"}'
    '{"id":"w.side","flavors":["standard","noLegal"],"wearFlavors":["noLegal"],"status":"active"}'
) -join "`n"

function New-Showcase([string[]]$bullets) {
    return "# Showcase`n`n## 1. Section`n" + (($bullets | ForEach-Object { $_ }) -join "`n") + "`n"
}

function New-Notes([string]$marker, [string]$heading, [string[]]$bullets) {
    return "# Notes`n`n**$marker 1.0** (x)`n`n---`n`n## $heading`n`n" + ($bullets -join "`n") +
        "`n`n## Fixed`n`n- something fixed`n`n---`n`n## Previous Release: 0.9`n`n- **Old** - no anchor`n"
}

$good = @('- **One** `[Standard]`: text. <!-- af: a.one -->', '- **Two** `[Standard]`: text. <!-- af: a.two, a.one -->')
$notes = @('- **One** - text. <!-- af: a.one -->')

function New-Tree([hashtable]$override) {
    $dir = Join-Path ([System.IO.Path]::GetTempPath()) ("s4102-showcase-" + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($dir)
    $null = New-Item -ItemType Directory -Path (Join-Path $dir 'docs') -Force
    $files = [ordered]@{
        'docs/ALL_FEATURES.jsonl' = $inventory
        'docs/FEATURES.md'        = New-Showcase $good
        'docs/FEATURES-ru.md'     = New-Showcase $good
        'docs/FEATURES-uk.md'     = New-Showcase $good
        'docs/WHATS_NEW.md'       = New-Notes 'Current release:' "What's New" $notes
        'docs/WHATS_NEW-ru.md'    = New-Notes 'Текущий релиз:' 'Что нового' $notes
        'docs/WHATS_NEW-uk.md'    = New-Notes 'Поточний реліз:' 'Що нового' $notes
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
    Invoke-Case 'clean tree passes' @{} 0 'PASS (9 bullets'
    Invoke-Case 'unanchored showcase bullet' @{
        'docs/FEATURES.md' = New-Showcase @('- **One** `[Standard]`: text.', $good[1])
    } 1 'has no'
    Invoke-Case 'unknown id' @{
        'docs/FEATURES.md' = New-Showcase @('- **One**: text. <!-- af: a.nope -->', $good[1])
    } 1 "'a.nope' is not in the inventory"
    Invoke-Case 'removed id' @{
        'docs/WHATS_NEW.md' = New-Notes 'Current release:' "What's New" @('- **Gone** - text. <!-- af: a.gone -->')
    } 1 "has status 'removed'"
    Invoke-Case 'noLegal-only id in a public file' @{
        'docs/FEATURES.md' = New-Showcase @('- **Side**: text. <!-- af: a.side -->', $good[1])
    } 1 'ships only in noLegal'
    Invoke-Case 'watch noLegal-only id in a public file' @{
        'docs/FEATURES.md' = New-Showcase @($good[0], '- **Watch**: text. <!-- af: w.side -->')
    } 1 "'w.side' ships only in noLegal"
    Invoke-Case 'the unpublished noLegal file is not judged' @{
        'docs/FEATURES_noLegal.md' = New-Showcase @('- **Why not in market builds:** no anchor here.')
    } 0 'PASS'
    Invoke-Case 'RU anchor differs from EN' @{
        'docs/FEATURES-ru.md' = New-Showcase @($good[0], '- **Two**: text. <!-- af: a.two -->')
    } 1 "a bullet anchors 'a.two', which no"
    Invoke-Case 'RU in another order with the same anchors passes' @{
        'docs/FEATURES-ru.md' = New-Showcase @('- **Two**: text. <!-- af: a.one, a.two -->', $good[0])
    } 0 'PASS'
    Invoke-Case 'UK bullet count differs from EN' @{
        'docs/FEATURES-uk.md' = New-Showcase @($good[0])
    } 1 '1 bullet(s)'
    Invoke-Case 'What is Fixed and older blocks are not judged' @{} 0 'PASS'
    Invoke-Case 'missing showcase file' @{ 'docs/FEATURES-uk.md' = $null } 2 'file not found: docs/FEATURES-uk.md'
    Invoke-Case 'missing inventory' @{ 'docs/ALL_FEATURES.jsonl' = $null } 2 'inventory not found'
} finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-showcase-inventory tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
