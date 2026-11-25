#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/quality/assert-set-baseline-rise.ps1 (S4645).

.DESCRIPTION
    Every case runs the gate against a throwaway git repository under temp/S4645/ passed with
    -RepoRoot, never against this repository's own HEAD: a verdict that depended on what other sessions
    had edited in the real baselines would be the defect S3025 was opened for.

    The fixture holds one inventory with two judged set baselines, one set owned by an absorption gate
    (not judged here), and one count ratchet (not a set). It is committed once; each case edits the
    working tree and the fixture is reset to HEAD before the next.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$gate = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-set-baseline-rise.ps1'
if (-not (Test-Path -LiteralPath $gate)) {
    Write-Error "gate not found: $gate" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture = Join-Path $repoRoot 'temp/S4645/rise-fixture'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$script:run = 0
$script:failures = 0

function Write-Fixture([string]$Rel, [string[]]$Lines) {
    $path = Join-Path $fixture $Rel
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [System.IO.File]::WriteAllText($path, (($Lines -join "`n") + "`n"), $utf8)
}

function Invoke-Git([string[]]$GitArgs) {
    # `checkout -- .` and `clean -fd` below would hit THIS repository if the fixture were not its own
    # repository, so the toplevel is proved before every call rather than assumed after `git init`.
    $top = (& git -C $fixture rev-parse --show-toplevel 2>$null | Out-String).Trim()
    if (-not $top -or ((Resolve-Path -LiteralPath $top).Path -ne (Resolve-Path -LiteralPath $fixture).Path)) {
        throw "the fixture is not its own git repository (toplevel '$top'); refusing to run git in it"
    }
    & git -C $fixture -c user.name=fixture -c user.email=fixture@example.invalid -c commit.gpgsign=false @GitArgs *> $null
    if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') failed in the fixture ($LASTEXITCODE)" }
}

function New-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    New-Item -ItemType Directory -Path $fixture -Force | Out-Null
    & git -C $fixture init --quiet 2>&1 | Out-Null
    $rows = @(
        '{"file":"scripts/quality/a-baseline.txt","shape":"set","owner":"scripts/quality/assert-a.ps1","write":"hand-only","verdict":"safe"}',
        '{"file":"scripts/quality/b-baseline.txt","shape":"set","owner":"scripts/quality/assert-b.ps1","write":"down-only","verdict":"safe"}',
        '{"file":"config/c-baseline.ids","shape":"set","owner":"scripts/quality/assert-c-baseline-absorption.ps1","write":"raise-with-reason","verdict":"safe"}',
        '{"file":"scripts/quality/n-baseline.txt","shape":"count","owner":"scripts/quality/assert-n.ps1","write":"down-only","verdict":"safe"}'
    )
    Write-Fixture 'scripts/quality/baseline-inventory.jsonl' $rows
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta')
    Write-Fixture 'scripts/quality/b-baseline.txt' @('one')
    Write-Fixture 'config/c-baseline.ids' @('x')
    Write-Fixture 'scripts/quality/n-baseline.txt' @('3')
    Invoke-Git @('add', '-A')
    Invoke-Git @('commit', '--quiet', '-m', 'fixture')
}

function Reset-Fixture {
    Invoke-Git @('checkout', '--quiet', '--', '.')
    Invoke-Git @('clean', '--quiet', '-fd')
}

function Invoke-Gate([string[]]$GateArgs) {
    $output = & $pwshExe -NoProfile -File $gate -RepoRoot $fixture @GateArgs 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($output | Out-String) }
}

function Assert-Case {
    param([string]$Name, [scriptblock]$Body)
    $script:run++
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else { Write-Host "PASS  $Name" -ForegroundColor Green }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

function Test-Result($Result, [int]$Code, [string[]]$Contains = @(), [string[]]$Lacks = @()) {
    if ($Result.Code -ne $Code) { return "exit $($Result.Code), expected $Code. Output: $($Result.Text.Trim())" }
    foreach ($needle in $Contains) {
        if (-not $Result.Text.Contains($needle)) { return "output lacks '$needle'. Output: $($Result.Text.Trim())" }
    }
    foreach ($needle in $Lacks) {
        if ($Result.Text.Contains($needle)) { return "output holds '$needle'. Output: $($Result.Text.Trim())" }
    }
    return $null
}

New-Fixture

Assert-Case 'unchanged baselines pass, two sets compared, subject line printed' {
    Test-Result (Invoke-Gate @('-Gate')) 0 @('subject: scope=set-baselines', 'PASS', '2 set baseline(s) compared with HEAD')
}

Assert-Case 'an added identifier fails under -Gate and is named with the accept command' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta', 'gamma')
    Test-Result (Invoke-Gate @('-Gate')) 1 @('FAIL', '+ gamma', '-Accept -File scripts/quality/a-baseline.txt')
}

Assert-Case 'the same finding without -Gate is reported and exits 0' {
    Test-Result (Invoke-Gate @()) 0 @('FAIL', '+ gamma')
}

Assert-Case 'a pruned identifier passes' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha')
    Test-Result (Invoke-Gate @('-Gate')) 0 @('PASS')
}

Assert-Case 'an added identifier with a journal row passes and counts as accepted' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta', 'gamma')
    Write-Fixture 'scripts/quality/set-baseline-accepted.jsonl' @('{"file":"scripts/quality/a-baseline.txt","id":"gamma","reason":"r","ticket":"S1234","date":"2026-11-15"}')
    Test-Result (Invoke-Gate @('-Gate')) 0 @('PASS', '1 accepted rise(s)')
}

Assert-Case '-Accept writes the journal row and the next gate run passes' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta', 'gamma')
    $accept = Invoke-Gate @('-Accept', '-File', 'scripts/quality/a-baseline.txt', '-Reason', 'screen X ships without a page', '-Ticket', 'S1234')
    $problem = Test-Result $accept 0 @('+ gamma')
    if ($problem) { return $problem }
    $journal = Get-Content -LiteralPath (Join-Path $fixture 'scripts/quality/set-baseline-accepted.jsonl') -Raw
    if ($journal -notmatch '"id":"gamma"' -or $journal -notmatch '"ticket":"S1234"') { return "journal row missing: $journal" }
    Test-Result (Invoke-Gate @('-Gate')) 0 @('PASS')
}

Assert-Case '-Accept without a reason exits 2' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta', 'gamma')
    Test-Result (Invoke-Gate @('-Accept', '-File', 'scripts/quality/a-baseline.txt', '-Ticket', 'S1234')) 2 @('COULD NOT VERIFY')
}

Assert-Case '-Accept for a count ratchet or an absorption-owned set exits 2' {
    Reset-Fixture
    $count = Invoke-Gate @('-Accept', '-File', 'scripts/quality/n-baseline.txt', '-Reason', 'r', '-Ticket', 'S1234')
    $problem = Test-Result $count 2 @('COULD NOT VERIFY')
    if ($problem) { return $problem }
    Test-Result (Invoke-Gate @('-Accept', '-File', 'config/c-baseline.ids', '-Reason', 'r', '-Ticket', 'S1234')) 2 @('COULD NOT VERIFY')
}

Assert-Case 'a baseline with no copy at HEAD is seeded, never a rise' {
    Reset-Fixture
    $row = '{"file":"scripts/quality/d-baseline.txt","shape":"set","owner":"scripts/quality/assert-d.ps1","write":"hand-only","verdict":"safe"}'
    $inventory = Join-Path $fixture 'scripts/quality/baseline-inventory.jsonl'
    [System.IO.File]::AppendAllText($inventory, "$row`n", $utf8)
    Write-Fixture 'scripts/quality/d-baseline.txt' @('new-one', 'new-two')
    Test-Result (Invoke-Gate @('-Gate')) 0 @('PASS', '1 seeded')
}

Assert-Case 'an absorption-owned set and a count ratchet are not judged here' {
    Reset-Fixture
    Write-Fixture 'config/c-baseline.ids' @('x', 'y')
    Write-Fixture 'scripts/quality/n-baseline.txt' @('9')
    Test-Result (Invoke-Gate @('-Gate')) 0 @('PASS')
}

Assert-Case 'a missing inventory exits 2' {
    Reset-Fixture
    Remove-Item -LiteralPath (Join-Path $fixture 'scripts/quality/baseline-inventory.jsonl')
    Test-Result (Invoke-Gate @('-Gate')) 2 @('COULD NOT VERIFY')
}

Assert-Case 'a journal line that is not JSON exits 2' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/set-baseline-accepted.jsonl' @('not json')
    Test-Result (Invoke-Gate @('-Gate')) 2 @('COULD NOT VERIFY')
}

Assert-Case 'a changed set that holds neither the baseline nor the journal is not charged (exit 3)' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta', 'gamma')
    Test-Result (Invoke-Gate @('-Gate', '-ChangedFiles', 'app_v2/src/main/Other.kt')) 3 @('reported, not charged')
}

Assert-Case 'a changed set that names the baseline is charged (exit 1)' {
    Reset-Fixture
    Write-Fixture 'scripts/quality/a-baseline.txt' @('# header line', 'alpha', 'beta', 'gamma')
    Test-Result (Invoke-Gate @('-Gate', '-ChangedFiles', 'scripts/quality/a-baseline.txt')) 1 @('+ gamma')
}

Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue

$verdict = if ($script:failures -gt 0) { 'FAIL' } else { 'PASS' }
Write-Host ("assert-set-baseline-rise.tests: {0} (run={1} skipped=0)" -f $verdict, $script:run)
if ($script:failures -gt 0) { exit 1 }
exit 0
