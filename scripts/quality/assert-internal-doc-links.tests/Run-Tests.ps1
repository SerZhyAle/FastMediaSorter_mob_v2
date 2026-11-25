#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for assert-internal-doc-links.ps1 (S4653) on synthetic trees.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
$ErrorActionPreference = 'Stop'
$gate = Join-Path $PSScriptRoot '..\assert-internal-doc-links.ps1'
$sandbox = Join-Path ([IO.Path]::GetTempPath()) ("internal-doc-links-" + [guid]::NewGuid().ToString('N'))
$script:pass = 0
$script:fail = 0
$script:case = 0

function Assert-That([string] $Name, [bool] $Ok, [string] $Detail) {
    if ($Ok) { $script:pass++; Write-Host "  PASS $Name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL $Name - $Detail" -ForegroundColor Red }
}

function New-Tree([hashtable] $Documents, [string[]] $Baseline = @()) {
    $script:case++
    $root = Join-Path $sandbox "case$($script:case)"
    foreach ($name in $Documents.Keys) {
        $full = Join-Path $root $name
        [void](New-Item -ItemType Directory -Path (Split-Path $full -Parent) -Force)
        Set-Content -LiteralPath $full -Value $Documents[$name] -Encoding utf8
    }
    $baselineFile = Join-Path $root 'baseline.txt'
    [void](New-Item -ItemType Directory -Path $root -Force)
    Set-Content -LiteralPath $baselineFile -Value (@('# fixture') + $Baseline) -Encoding utf8
    return $root
}

function Invoke-Gate([string] $Root) {
    $text = & pwsh -NoProfile -File $gate -RepoRoot $Root -BaselinePath (Join-Path $Root 'baseline.txt') 2>&1 | Out-String
    return @{ Code = $LASTEXITCODE; Text = $text }
}

Write-Host 'assert-internal-doc-links.tests' -ForegroundColor Cyan

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "# A`n[b](B.md#second-part)"; 'docs/B.md' = "# B`n## Second part" })
Assert-That 'S4653 a resolving link and anchor pass' ($r.Code -eq 0) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "[gone](GONE.md)" })
Assert-That 'S4653 a missing target fails and is named' (($r.Code -eq 1) -and ($r.Text -match 'GONE\.md')) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "[b](B.md#nowhere)"; 'docs/B.md' = "# B" })
Assert-That 'S4653 a missing anchor fails' (($r.Code -eq 1) -and ($r.Text -match 'nowhere')) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "``[x](GONE.md)```n``````n[y](ALSO_GONE.md)`n``````" })
Assert-That 'S4653 links inside code are not links' ($r.Code -eq 0) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "[gone](GONE.md)" } @('docs/A.md -> GONE.md'))
Assert-That 'S4653 a baselined broken link passes' ($r.Code -eq 0) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "# A" } @('docs/A.md -> GONE.md'))
Assert-That 'S4653 a stale baseline row fails' (($r.Code -eq 1) -and ($r.Text -match 'stale')) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "[b](B.html)"; 'docs/B.md' = "# B" })
Assert-That 'S4653 a .html link to a sibling .md page resolves' ($r.Code -eq 0) $r.Text

$r = Invoke-Gate (New-Tree @{ 'docs/A.md' = "[w](https://example.org/x) [s](/docs/site.html)" })
Assert-That 'S4653 scheme and site-absolute links are not judged' ($r.Code -eq 0) $r.Text

Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
Write-Host ''
Write-Host "assert-internal-doc-links.tests: $(if ($script:fail -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:pass + $script:fail) skipped=0)"
exit $(if ($script:fail -eq 0) { 0 } else { 1 })
