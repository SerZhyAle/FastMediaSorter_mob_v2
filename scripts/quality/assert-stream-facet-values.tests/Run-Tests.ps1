<#
.SYNOPSIS
Contract tests for assert-stream-facet-values.ps1 (S4133).

.DESCRIPTION
Every case writes a throwaway catalog under the system temp directory and runs the gate against it with
the live facet module, so no case depends on what the real catalog carries this minute.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-stream-facet-values.ps1')).Path
$passed = 0
$failed = 0
$sandboxes = [System.Collections.Generic.List[string]]::new()

function New-Catalog {
    param([string[]]$Lines)
    $dir = Join-Path ([System.IO.Path]::GetTempPath()) ("s4133-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $sandboxes.Add($dir)
    $path = Join-Path $dir 'streams.csv'
    [System.IO.File]::WriteAllLines($path, $Lines, [System.Text.UTF8Encoding]::new($false))
    return $path
}

function Invoke-Case {
    param([string]$Name, [string]$Path, [int]$Expect)
    $null = & pwsh -NoProfile -File $Gate -CatalogPath $Path -Quiet 2>&1
    if ($LASTEXITCODE -eq $Expect) {
        $script:passed++
        Write-Host "PASS  $Name"
    } else {
        $script:failed++
        Write-Host "FAIL  $Name (expected exit $Expect, got $LASTEXITCODE)"
    }
}

try {
    $header = 'category,topic,name,url,language,country'
    Invoke-Case 'clean catalog exits 0' (New-Catalog @(
            $header, 'Radio,Jazz,A,https://a.test/1,"english,german",DE',
            'Radio,Jazz,B,https://a.test/2,,')) 0
    Invoke-Case 'a space-joined language list exits 1' (New-Catalog @(
            $header, 'Radio,Jazz,A,https://a.test/1,english hindi,DE')) 1
    Invoke-Case 'a variety label exits 1' (New-Catalog @(
            $header, 'Radio,Jazz,A,https://a.test/1,caribbean english,DE')) 1
    Invoke-Case 'a country name exits 1' (New-Catalog @(
            $header, 'Radio,Jazz,A,https://a.test/1,english,Czech Republic')) 1
    Invoke-Case 'an unassigned country code exits 1' (New-Catalog @(
            $header, 'Radio,Jazz,A,https://a.test/1,english,XX')) 1
    Invoke-Case 'a catalog without a country column exits 2' (New-Catalog @(
            'category,topic,name,url,language', 'Radio,Jazz,A,https://a.test/1,english')) 2
    Invoke-Case 'an absent catalog exits 3' (Join-Path ([System.IO.Path]::GetTempPath()) 'no-such-s4133.csv') 3
} finally {
    foreach ($d in $sandboxes) { Remove-Item -LiteralPath $d -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host ("assert-stream-facet-values.tests: {0} passed, {1} failed" -f $passed, $failed)
if ($failed -gt 0) { exit 1 }
exit 0
