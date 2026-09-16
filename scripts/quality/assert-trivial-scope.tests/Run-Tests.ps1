#requires -Version 7.0
<#
.SYNOPSIS
    S3151: contract suite for assert-trivial-scope.ps1 against a throwaway git fixture.

.DESCRIPTION
    The fixture carries the repository's own `trivial` profile key, so a pattern edited in
    .sza-profile.json is judged here exactly as the closure will judge it.

    Exit codes:
      0  every case passed.
      1  at least one case failed.
      2  cannot verify - git is not on PATH or the subject script is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$subject = Join-Path $repoRoot 'scripts/quality/assert-trivial-scope.ps1'
if (-not (Test-Path -LiteralPath $subject)) { Write-Host "cannot verify: $subject is missing."; exit 2 }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Write-Host 'cannot verify: git is not on PATH.'; exit 2 }
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixture = Join-Path $repoRoot "temp/scratch/trivial-scope-tests-$PID"
if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
New-Item -ItemType Directory -Force -Path $fixture | Out-Null

$trivialKey = (Get-Content -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json).trivial
@{ trivial = $trivialKey } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $fixture '.sza-profile.json') -Encoding utf8
foreach ($name in 'a', 'b', 'c', 'd') {
    Set-Content -LiteralPath (Join-Path $fixture "$name.kt") -Value "package x`n`nfun $name() = 1" -Encoding utf8
}
& git -C $fixture init -q *> $null
# `git stash create` (S3182's baseline) writes a commit, so the fixture needs its own identity.
& git -C $fixture config user.email suite@example.com *> $null
& git -C $fixture config user.name suite *> $null
& git -C $fixture add -A *> $null
& git -C $fixture -c user.email=suite@example.com -c user.name=suite -c core.autocrlf=false commit -q -m base *> $null

$failures = 0
function Reset-Fixture {
    & git -C $fixture checkout -q -- . *> $null
    & git -C $fixture clean -qfd *> $null
}
function Invoke-Case {
    param([string]$Name, [int]$Expected, [string[]]$Arguments, [string]$Needle = '')
    $out = & $pwshExe -NoProfile -File $subject @Arguments 2>&1 | Out-String
    $code = [int]$LASTEXITCODE
    if ($code -eq $Expected -and (-not $Needle -or $out -match $Needle)) {
        Write-Host "  PASS  $Name"
    }
    else {
        Write-Host "  FAIL  $Name - exit $code (expected $Expected): $($out.Trim())"
        $script:failures++
    }
    Reset-Fixture
}
function Add-Line([string]$File, [string]$Line) {
    Add-Content -LiteralPath (Join-Path $fixture $File) -Value $Line -Encoding utf8
}

Write-Host 'assert-trivial-scope.tests'

Add-Line 'a.kt' 'fun extra() = 2'
Invoke-Case 'a one-file edit with no new type passes' 0 @('-RepoRoot', $fixture, '-Id', 'S0001', '-Files', 'a.kt') 'PASS'

foreach ($name in 'a', 'b', 'c', 'd') { Add-Line "$name.kt" 'fun more() = 3' }
Invoke-Case 'four changed files escalate' 1 @('-RepoRoot', $fixture, '-Files', 'a.kt,b.kt,c.kt,d.kt') 'the Trivial limit is 3'

Add-Line 'a.kt' 'fun extra() = 2'
Set-Content -LiteralPath (Join-Path $fixture 'e.kt') -Value 'package x' -Encoding utf8
Invoke-Case 'a new file escalates' 1 @('-RepoRoot', $fixture, '-Files', 'a.kt,e.kt') 'e\.kt is a new file'

Add-Line 'a.kt' 'data class Extra(val id: Int)'
Invoke-Case 'an added class declaration escalates' 1 @('-RepoRoot', $fixture, '-Files', 'a.kt') 'adds'

Add-Line 'a.kt' '@Entity(tableName = "x")'
Invoke-Case 'an added Room entity escalates' 1 @('-RepoRoot', $fixture, '-Files', 'a.kt') 'Entity'

Add-Line 'a.kt' 'fun extra() = 2'
New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'PLAN') | Out-Null
Set-Content -LiteralPath (Join-Path $fixture 'PLAN/S0001_x.md') -Value '# spec' -Encoding utf8
Invoke-Case 'the spec under PLAN/ is not judged' 0 @('-RepoRoot', $fixture, '-Files', 'a.kt,PLAN/S0001_x.md') 'PASS'

Invoke-Case 'no -Files is a bad invocation' 2 @('-RepoRoot', $fixture) 'cannot judge'

Invoke-Case '-RecordBaseline without -Id is a bad invocation' 2 @('-RepoRoot', $fixture, '-RecordBaseline') 'needs -Id'

# S3182: a sibling ticket's uncommitted type line must not be charged to this ticket. The snapshot
# is taken after that line exists and before this ticket's own edit, exactly as the Trivial path does.
Add-Line 'a.kt' 'data class SiblingLeftover(val id: Int)'
Invoke-Case 'a sibling type line without a baseline escalates' 1 @('-RepoRoot', $fixture, '-Id', 'S0002', '-Files', 'a.kt') 'SiblingLeftover'

Add-Line 'a.kt' 'data class SiblingLeftover(val id: Int)'
& $pwshExe -NoProfile -File $subject -RepoRoot $fixture -Id 'S0002' -RecordBaseline *> $null
Add-Line 'a.kt' 'fun mine() = 4'
Invoke-Case 'a baseline taken after the sibling line passes' 0 @('-RepoRoot', $fixture, '-Id', 'S0002', '-Files', 'a.kt') 'snapshot'

Add-Line 'a.kt' 'data class SiblingLeftover(val id: Int)'
& $pwshExe -NoProfile -File $subject -RepoRoot $fixture -Id 'S0002' -RecordBaseline *> $null
Add-Line 'a.kt' 'data class MineNow(val id: Int)'
Invoke-Case 'a type added after the baseline still escalates' 1 @('-RepoRoot', $fixture, '-Id', 'S0002', '-Files', 'a.kt') 'MineNow'

Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
if ($failures -gt 0) {
    Write-Host "assert-trivial-scope.tests: FAIL ($failures case(s))."
    exit 1
}
Write-Host 'assert-trivial-scope.tests: PASS.'
exit 0
