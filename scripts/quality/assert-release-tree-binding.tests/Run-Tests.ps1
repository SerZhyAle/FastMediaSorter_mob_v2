<#
Run-Tests.ps1 - contract tests for assert-release-tree-binding.ps1 (S4057).

Every case builds a throwaway git repository under the system temp directory, so no case reads the
live release worktree or its tags.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-release-tree-binding.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

function Invoke-Git([string]$Root, [string[]]$GitArgs) {
    & git -C $Root -c user.email=fixture@example.invalid -c user.name=fixture -c commit.gpgsign=false -c tag.gpgsign=false @GitArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') failed in $Root" }
}

# A tested branch, a tag on the same tree, and HEAD at the tag - the bound case.
function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("s4057-" + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    Invoke-Git $root @('init', '-q', '-b', 'main')
    Set-Content -LiteralPath (Join-Path $root 'a.txt') -Value 'one' -Encoding utf8
    Invoke-Git $root @('add', 'a.txt')
    Invoke-Git $root @('commit', '-q', '-m', 'one')
    Invoke-Git $root @('branch', 'tested')
    Invoke-Git $root @('tag', 'release/v1')
    return $root
}

function Invoke-Gate([string]$Root, [string[]]$Extra = @()) {
    $out = & pwsh -NoProfile -File $Gate -Worktree $Root @Extra 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
}

function Assert-Case([string]$Name, [int]$Expected, [pscustomobject]$Result, [string]$MustContain) {
    $ok = $Result.ExitCode -eq $Expected
    if ($ok -and $MustContain) { $ok = $Result.Output -match [regex]::Escape($MustContain) }
    if ($ok) { $script:passed++; Write-Host ("  PASS  {0}" -f $Name) -ForegroundColor Green; return }
    $script:failed++
    Write-Host ("  FAIL  {0} - expected exit {1}, got {2}" -f $Name, $Expected, $Result.ExitCode) -ForegroundColor Red
    Write-Host ($Result.Output -split "`n" | Select-Object -First 8 | ForEach-Object { "        $_" })
}

Write-Host 'assert-release-tree-binding contract tests (S4057)'

$root = New-Fixture
Assert-Case 'tested, tagged and built trees equal is bound' 0 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested')) 'BOUND'

$root = New-Fixture
Invoke-Git $root @('commit', '-q', '--allow-empty', '-m', 'merge-like commit, same tree')
Invoke-Git $root @('tag', '-f', 'release/v1')
Assert-Case 'a different commit with the same tree is bound' 0 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested'))

$root = New-Fixture
Set-Content -LiteralPath (Join-Path $root 'a.txt') -Value 'two' -Encoding utf8
Invoke-Git $root @('commit', '-q', '-am', 'untested change')
Invoke-Git $root @('tag', '-f', 'release/v1')
Assert-Case 'a tag carrying an untested change is refused' 1 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested')) 'tested:'

$root = New-Fixture
Set-Content -LiteralPath (Join-Path $root 'a.txt') -Value 'two' -Encoding utf8
Invoke-Git $root @('commit', '-q', '-am', 'main moved after the tag')
Assert-Case 'a worktree HEAD past the tag is refused' 1 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested')) 'built:'

$root = New-Fixture
Set-Content -LiteralPath (Join-Path $root 'a.txt') -Value 'edited' -Encoding utf8
Assert-Case 'a tracked modification in the worktree is refused' 1 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested')) 'dirty:'

$root = New-Fixture
Assert-Case 'an unknown ref cannot be verified' 2 (Invoke-Gate $root @('-Tag', 'release/v9', '-TestedRef', 'tested'))

$root = New-Fixture
$bundle = Join-Path $root 'out/bundle/standardRelease'
New-Item -ItemType Directory -Path $bundle -Force | Out-Null
Set-Content -LiteralPath (Join-Path $bundle 'output-metadata.json') -Encoding utf8 -Value '{"elements":[{"versionCode":1234,"versionName":"2.0"}]}'
Assert-Case 'the read-back versionCode matching is bound' 0 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested', '-ExpectedVersionCode', '1234', '-BundleDir', (Join-Path $root 'out/bundle')))
Assert-Case 'the read-back versionCode differing is refused' 1 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested', '-ExpectedVersionCode', '1235', '-BundleDir', (Join-Path $root 'out/bundle'))) 'version:'
Assert-Case 'no bundle metadata cannot be verified' 2 (Invoke-Gate $root @('-Tag', 'release/v1', '-TestedRef', 'tested', '-ExpectedVersionCode', '1234', '-BundleDir', (Join-Path $root 'nothing')))

foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }

Write-Host ''
Write-Host ("assert-release-tree-binding tests: {0} passed, {1} failed" -f $passed, $failed)
if ($failed -gt 0) { exit 1 }
exit 0
