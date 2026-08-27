#requires -Version 7.0
# Run-Tests.ps1 (S2112) - contract suite for prune-detekt-baseline.ps1.
#
# The point of this suite is the refusal. A tool that edits a detekt baseline is one bug away from
# reproducing the 2026-08-02 absorption incident, so what must be pinned is not "it prunes" but
# "it will not write when the named files carry a finding the baseline does not already hold", and
# "it will not delete a same-named file's entry from another source set".
#
# Each case builds a throwaway repo root under temp/ - a detekt config, a baseline, a build file
# carrying the version pin, and fixtures - so no case touches the real source tree or the real
# baseline. The dependency cache stays real: these run the actual analyser, not a stub.
#
# Exit codes:
#   0 - every case passed.
#   1 - at least one case failed.

[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Passed = 0
$script:Failed = 0
$runner = Join-Path $RepoRoot 'scripts/quality/prune-detekt-baseline.ps1'
$sandbox = Join-Path $RepoRoot 'temp/prune-detekt-baseline-tests'

function Assert-Equal([string] $Label, $Expected, $Actual, [string] $Context) {
    if ($Expected -eq $Actual) {
        Write-Host "  PASS  $Label" -ForegroundColor Green
        $script:Passed++
    }
    else {
        Write-Host "  FAIL  $Label (expected '$Expected', got '$Actual')" -ForegroundColor Red
        if ($Context) { Write-Host ($Context -replace '(?m)^', '        ') -ForegroundColor DarkGray }
        $script:Failed++
    }
}

function Assert-Match([string] $Label, [string] $Pattern, [string] $Text) {
    if ($Text -match $Pattern) {
        Write-Host "  PASS  $Label" -ForegroundColor Green
        $script:Passed++
    }
    else {
        Write-Host "  FAIL  $Label (no match for '$Pattern')" -ForegroundColor Red
        $script:Failed++
    }
}

function Assert-NoMatch([string] $Label, [string] $Pattern, [string] $Text) {
    if ($Text -notmatch $Pattern) {
        Write-Host "  PASS  $Label" -ForegroundColor Green
        $script:Passed++
    }
    else {
        Write-Host "  FAIL  $Label (unexpected match for '$Pattern')" -ForegroundColor Red
        $script:Failed++
    }
}

# $Sources: relative path under app_v2/src -> file body.
# $BaselineIds: raw <ID> payloads to seed the operational baseline with.
function New-FakeRepo([string] $Name, [hashtable] $Sources, [string[]] $BaselineIds) {
    $root = Join-Path $sandbox $Name
    if (Test-Path $root) { Remove-Item $root -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $root 'config/detekt') -Force | Out-Null
    Copy-Item (Join-Path $RepoRoot 'config/detekt/detekt.yml') (Join-Path $root 'config/detekt/detekt.yml')

    foreach ($rel in $Sources.Keys) {
        $full = Join-Path $root "app_v2/src/$rel"
        New-Item -ItemType Directory -Path (Split-Path -Parent $full) -Force | Out-Null
        Set-Content -LiteralPath $full -Value $Sources[$rel] -Encoding UTF8
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('<?xml version="1.0" ?>')
    $lines.Add('<SmellBaseline>')
    $lines.Add('  <ManuallySuppressedIssues/>')
    $lines.Add('  <CurrentIssues>')
    foreach ($id in $BaselineIds) { $lines.Add("    <ID>$id</ID>") }
    $lines.Add('  </CurrentIssues>')
    $lines.Add('</SmellBaseline>')
    [System.IO.File]::WriteAllText((Join-Path $root 'config/detekt/baseline-app_v2.xml'),
        (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

    $pin = Select-String -LiteralPath (Join-Path $RepoRoot 'build.gradle.kts') `
        -Pattern 'id\("io\.gitlab\.arturbosch\.detekt"\)\s+version\s+"[^"]+"' | Select-Object -First 1
    if (-not $pin) { throw 'could not read the detekt pin from the real build.gradle.kts' }
    "plugins { $($pin.Matches[0].Value) apply false }" | Set-Content (Join-Path $root 'build.gradle.kts') -Encoding UTF8
    return $root
}

function Invoke-Runner([string] $Root, [string[]] $Files, [switch] $Apply, [string] $Reason, [string] $CacheRoot) {
    $argv = @('-NoProfile', '-File', $runner, '-RepoRoot', $Root, '-Module', 'app_v2', '-Files', ($Files -join ','))
    if ($Apply) { $argv += '-Apply' }
    if ($Reason) { $argv += @('-Reason', $Reason) }
    if ($CacheRoot) { $argv += @('-CacheRoot', $CacheRoot) }
    $out = & pwsh @argv 2>&1 | Out-String
    return @{ Exit = $LASTEXITCODE; Output = $out }
}

# Comma-wrapped: `return @(..)` unrolls a one-element array back to a bare string, and every caller
# here reads .Count off the result.
function Get-BaselineIds([string] $Root) {
    $raw = Get-Content -LiteralPath (Join-Path $Root 'config/detekt/baseline-app_v2.xml') -Raw
    return , @([regex]::Matches($raw, '<ID>(.*?)</ID>') | ForEach-Object { $_.Groups[1].Value })
}

# A body with three returns - detekt's ReturnCount fires on it under this repo's config.
$threeReturns = @'
package com.sza.fastmediasorter

class Fixture {
    fun tooManyReturns(x: Int): String {
        if (x == 3) return "three"
        if (x == 5) return "five"
        if (x == 7) return "seven"
        return "other"
    }
}
'@

$clean = @'
package com.sza.fastmediasorter

class Fixture {
    fun describe(x: Int): String = x.toString()
}
'@

$deadId = 'ReturnCount:Fixture.kt$Fixture$fun aFunctionThatNoLongerExists(): String'

Write-Host 'A: an entry whose finding is gone is pruned'
$rootA = New-FakeRepo 'a-dead' @{ 'main/java/com/sza/fastmediasorter/Fixture.kt' = $clean } @($deadId)
$a = Invoke-Runner $rootA @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt') -Apply -Reason 'suite A'
Assert-Equal 'A1 exit 0' 0 $a.Exit $a.Output
Assert-Match 'A2 says it pruned' 'PRUNED' $a.Output
Assert-Equal 'A3 the dead entry is gone' 0 (Get-BaselineIds $rootA).Count

Write-Host 'B: a finding the baseline does not hold is a refusal, and nothing is written'
$rootB = New-FakeRepo 'b-new' @{ 'main/java/com/sza/fastmediasorter/Fixture.kt' = $threeReturns } @($deadId)
$before = Get-Content -LiteralPath (Join-Path $rootB 'config/detekt/baseline-app_v2.xml') -Raw
$b = Invoke-Runner $rootB @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt') -Apply -Reason 'suite B'
$after = Get-Content -LiteralPath (Join-Path $rootB 'config/detekt/baseline-app_v2.xml') -Raw
Assert-Equal 'B1 exit 1 on a new finding' 1 $b.Exit
Assert-Match 'B2 names the new finding' 'NEW ReturnCount' $b.Output
Assert-Equal 'B3 baseline byte-identical' $before $after
Assert-NoMatch 'B4 did not claim to prune' 'PRUNED' $b.Output

Write-Host 'C: the dead entry of a same-named file in another source set is not deleted'
# Two Fixture.kt, one per source set. The named file is the main one and it is clean; the vr one
# still carries a live ReturnCount. Both entries key on the bare name "Fixture.kt", so a prune that
# analysed only the named file would classify the vr entry as dead.
$rootC = New-FakeRepo 'c-ambiguous' @{
    'main/java/com/sza/fastmediasorter/Fixture.kt' = $clean
    'vr/java/com/sza/fastmediasorter/Fixture.kt'   = $threeReturns
} @($deadId)
$c = Invoke-Runner $rootC @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt') -Apply -Reason 'suite C'
Assert-Match 'C1 analysed both same-named files' '2 file\(s\) analysed' $c.Output
Assert-Equal 'C2 exit 1 - the vr copy carries an unbaselined finding' 1 $c.Exit
Assert-Equal 'C3 nothing deleted' 1 (Get-BaselineIds $rootC).Count

Write-Host 'D: -Apply without -Reason cannot verify'
$rootD = New-FakeRepo 'd-noreason' @{ 'main/java/com/sza/fastmediasorter/Fixture.kt' = $clean } @($deadId)
$d = Invoke-Runner $rootD @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt') -Apply
Assert-Equal 'D1 exit 2' 2 $d.Exit
Assert-Match 'D2 says cannot verify' 'CANNOT VERIFY' $d.Output
Assert-Equal 'D3 nothing deleted' 1 (Get-BaselineIds $rootD).Count

Write-Host 'E: a report run writes nothing'
$rootE = New-FakeRepo 'e-dryrun' @{ 'main/java/com/sza/fastmediasorter/Fixture.kt' = $clean } @($deadId)
$e = Invoke-Runner $rootE @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt')
Assert-Equal 'E1 exit 0' 0 $e.Exit
Assert-Match 'E2 says what it would remove' 'would be removed' $e.Output
Assert-Equal 'E3 baseline untouched' 1 (Get-BaselineIds $rootE).Count

Write-Host 'F: an unusable dependency cache cannot verify - and never reports a clean prune'
$rootF = New-FakeRepo 'f-nocache' @{ 'main/java/com/sza/fastmediasorter/Fixture.kt' = $clean } @($deadId)
$emptyCache = Join-Path $sandbox 'empty-cache'
New-Item -ItemType Directory -Path $emptyCache -Force | Out-Null
$f = Invoke-Runner $rootF @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt') -Apply -Reason 'suite F' -CacheRoot $emptyCache
Assert-Equal 'F1 exit 2, not 0' 2 $f.Exit
Assert-Match 'F2 says cannot verify' 'CANNOT VERIFY' $f.Output
Assert-Equal 'F3 nothing deleted' 1 (Get-BaselineIds $rootF).Count

Write-Host 'G: a named file outside the module cannot verify'
$rootG = New-FakeRepo 'g-outside' @{ 'main/java/com/sza/fastmediasorter/Fixture.kt' = $clean } @($deadId)
$g = Invoke-Runner $rootG @('config/detekt/detekt.yml')
Assert-Equal 'G1 exit 2' 2 $g.Exit
Assert-Match 'G2 says cannot verify' 'CANNOT VERIFY' $g.Output

if (Test-Path $sandbox) { Remove-Item $sandbox -Recurse -Force }
Write-Host 'sandbox removed'

Write-Host ''
if ($script:Failed -gt 0) {
    Write-Error "prune-detekt-baseline tests: $($script:Passed) passed, $($script:Failed) FAILED" -ErrorAction Continue
    exit 1
}
Write-Host "prune-detekt-baseline tests: $($script:Passed) passed" -ForegroundColor Green
exit 0
