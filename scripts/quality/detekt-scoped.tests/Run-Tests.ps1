#requires -Version 7.0
# Run-Tests.ps1 (S1595) - contract suite for detekt-scoped.ps1.
#
# The point of this suite is the exit-2 path. A scoped runner assembles its analyser from the
# gradle dependency cache, so it WILL break on some future version bump - and the failure mode
# that matters is not "it breaks", it is "it breaks and reports clean". Everything else here is
# scaffolding around pinning that one behaviour.
#
# S2116 added the -Fix cases. That mode rewrites source, and before S2116 nothing here judged what
# it left on disk - which is how it shipped able to create findings the next step refused, over
# files a judge run had just called clean.
#
# Each case builds a throwaway repo root under temp/ - a detekt config, a baseline, a build file
# carrying the version pin, and a fixture - so no case touches the real source tree. The
# dependency cache stays real: these run the actual analyser, not a stub.
#
# Exit codes:
#   0 - every case passed.
#   1 - at least one case failed.
#   2 - the suite could not build a fixture it needs, so it never judged anything.

[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Passed = 0
$script:Failed = 0
$runner = Join-Path $RepoRoot 'scripts/quality/detekt-scoped.ps1'
$sandbox = Join-Path $RepoRoot 'temp/detekt-scoped-tests'
$fixtureRel = 'app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt'

. (Join-Path $RepoRoot 'scripts/quality/lib/detekt-classpath.ps1')

function Assert-Equal([string] $Label, $Expected, $Actual) {
    if ($Expected -eq $Actual) {
        Write-Host "  PASS  $Label" -ForegroundColor Green
        $script:Passed++
    }
    else {
        Write-Host "  FAIL  $Label (expected '$Expected', got '$Actual')" -ForegroundColor Red
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

function New-FakeRepo([string] $Name, [string] $FixtureBody) {
    $root = Join-Path $sandbox $Name
    if (Test-Path $root) { Remove-Item $root -Recurse -Force }
    $pkgDir = Join-Path $root 'app_v2/src/main/java/com/sza/fastmediasorter'
    New-Item -ItemType Directory -Path $pkgDir -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'config/detekt') -Force | Out-Null
    Copy-Item (Join-Path $RepoRoot 'config/detekt/detekt.yml') (Join-Path $root 'config/detekt/detekt.yml')
    Copy-Item (Join-Path $RepoRoot 'config/detekt/format-autocorrect.yml') (Join-Path $root 'config/detekt/format-autocorrect.yml')
    '<?xml version="1.0" ?><SmellBaseline><ManuallySuppressedIssues/><CurrentIssues/></SmellBaseline>' |
        Set-Content (Join-Path $root 'config/detekt/baseline-app_v2.xml') -Encoding UTF8
    # The runner reads the pin from here, so the fake repo must carry the same declaration shape.
    $pin = Select-String -LiteralPath (Join-Path $RepoRoot 'build.gradle.kts') `
        -Pattern 'id\("io\.gitlab\.arturbosch\.detekt"\)\s+version\s+"[^"]+"' | Select-Object -First 1
    if (-not $pin) { throw 'could not read the detekt pin from the real build.gradle.kts' }
    "plugins { $($pin.Matches[0].Value) apply false }" | Set-Content (Join-Path $root 'build.gradle.kts') -Encoding UTF8
    Set-Fixture $root $FixtureBody
    return $root
}

function Set-Fixture([string] $Root, [string] $Body) {
    $Body | Set-Content (Join-Path $Root $fixtureRel) -Encoding UTF8
}

function Get-FixtureBytes([string] $Root) {
    return [System.IO.File]::ReadAllBytes((Join-Path $Root $fixtureRel))
}

function Test-BytesEqual($A, $B) {
    if ($A.Length -ne $B.Length) { return $false }
    for ($i = 0; $i -lt $A.Length; $i++) { if ($A[$i] -ne $B[$i]) { return $false } }
    return $true
}

function Invoke-Runner([string] $Root, [string[]] $Files, [string] $CacheRoot) {
    $argv = @('-NoProfile', '-File', $runner, '-RepoRoot', $Root, '-ChangedFiles', ($Files -join ','))
    if ($CacheRoot) { $argv += @('-CacheRoot', $CacheRoot) }
    $out = & pwsh @argv 2>&1 | Out-String
    return @{ Exit = $LASTEXITCODE; Output = $out }
}

function Invoke-Fixer([string] $Root, [string[]] $Files) {
    $argv = @('-NoProfile', '-File', $runner, '-RepoRoot', $Root, '-Fix', '-ChangedFiles', ($Files -join ','))
    $out = & pwsh @argv 2>&1 | Out-String
    return @{ Exit = $LASTEXITCODE; Output = $out }
}

# Writes a real baseline over the fixture as it stands. Hand-writing one is not an option: a
# baseline id carries the declaration text detekt derives, and guessing that text wrong would make
# the case pass for the wrong reason. Exit 2 rather than a failed assertion if it cannot be built -
# a fixture that was never created has judged nothing.
function New-FixtureBaseline([string] $Root) {
    $cli = Initialize-DetektCli -RepoRoot $Root -CacheRoot (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1')
    if (-not $cli.Ok) {
        Write-Error "detekt-scoped tests: CANNOT VERIFY - $($cli.Reason)" -ErrorAction Continue
        exit 2
    }
    $baseline = Join-Path $Root 'config/detekt/baseline-app_v2.xml'
    Remove-Item -LiteralPath $baseline -Force
    Push-Location $Root
    try {
        foreach ($kotlinVersion in @($cli.KotlinCandidates)) {
            $jars = Build-DetektClasspath -CacheRoot $cli.CacheRoot -DetektVersion $cli.DetektVersion -KotlinVersion $kotlinVersion
            & java -cp ($jars -join ';') io.gitlab.arturbosch.detekt.cli.Main `
                '--input' (Join-Path $Root $fixtureRel) `
                '--config' (Join-Path $Root 'config/detekt/detekt.yml') `
                '--build-upon-default-config' `
                '--plugins' $cli.PluginJar `
                '--baseline' $baseline `
                '--create-baseline' 2>&1 | Out-Null
            if (Test-Path -LiteralPath $baseline) { return }
        }
    }
    finally { Pop-Location }
    Write-Error 'detekt-scoped tests: CANNOT VERIFY - could not create a fixture baseline.' -ErrorAction Continue
    exit 2
}

$dirty = @'
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

# ImportOrdering is correctable by the formatting ruleset, so this is the case -Fix exists for.
$misordered = @'
package com.sza.fastmediasorter

import kotlin.math.min
import kotlin.math.abs

class Fixture {
    fun describe(x: Int, cap: Int): Int = min(abs(x), cap)
}
'@

# A single-line call whose string literal alone is longer than the limit. Wrapping it changes the
# declaration text its baseline id is addressed by AND leaves a line no formatter can shorten -
# the exact shape measured on S2104 (PlayerViewModel.kt, 0 findings before -Fix, 4 after).
$longCall = @'
package com.sza.fastmediasorter

class Fixture {
    fun emit(tag: String, message: String): String = tag + message

    val note: String = emit("Fixture.emit", "a message that is deliberately far longer than the configured maximum line length so that no wrapping of the call around it can ever bring this literal under the limit")
}
'@

$longCallPlusFinding = $longCall + @'


class Second {
    fun tooManyReturns(x: Int): String {
        if (x == 3) return "three"
        if (x == 5) return "five"
        if (x == 7) return "seven"
        return "other"
    }
}
'@

Write-Host 'A: a fresh finding is reported and blocks'
$rootA = New-FakeRepo 'a-dirty' $dirty
$a = Invoke-Runner $rootA @($fixtureRel)
Assert-Equal 'A1 exit 1 on a finding' 1 $a.Exit
Assert-Match 'A2 names the rule' 'ReturnCount' $a.Output
Assert-Match 'A3 names file, line and column' 'fixture\.kt:\d+:\d+' $a.Output

Write-Host 'B: a clean file passes'
$rootB = New-FakeRepo 'b-clean' $clean
$b = Invoke-Runner $rootB @($fixtureRel)
Assert-Equal 'B1 exit 0 when clean' 0 $b.Exit
Assert-Match 'B2 says the whole rule set was applied' 'full configured rule set' $b.Output

Write-Host 'C: an unusable dependency cache cannot verify - and never reports clean'
$rootC = New-FakeRepo 'c-nocache' $dirty
$emptyCache = Join-Path $sandbox 'empty-cache'
New-Item -ItemType Directory -Path $emptyCache -Force | Out-Null
$c = Invoke-Runner $rootC @($fixtureRel) $emptyCache
Assert-Equal 'C1 exit 2, not 0' 2 $c.Exit
Assert-Match 'C2 says cannot verify' 'CANNOT VERIFY' $c.Output

Write-Host 'D: a named file that does not exist cannot verify'
$rootD = New-FakeRepo 'd-missing' $clean
$d = Invoke-Runner $rootD @('app_v2/src/main/java/com/sza/fastmediasorter/Absent.kt')
Assert-Equal 'D1 exit 2 on a missing file' 2 $d.Exit
Assert-Match 'D2 names the missing file' 'Absent\.kt' $d.Output

Write-Host 'E: nothing analysable is a pass, not a refusal'
$rootE = New-FakeRepo 'e-nonkt' $clean
$e = Invoke-Runner $rootE @('config/detekt/detekt.yml')
Assert-Equal 'E1 exit 0 when no .kt in the set' 0 $e.Exit
Assert-Match 'E2 says there was nothing to check' 'nothing to check' $e.Output

Write-Host 'F: -Fix does not touch a file the judge already called clean'
$rootF = New-FakeRepo 'f-fix-clean' $clean
$beforeF = Get-FixtureBytes $rootF
$f = Invoke-Fixer $rootF @($fixtureRel)
Assert-Equal 'F1 exit 0' 0 $f.Exit
Assert-Match 'F2 says the source was untouched' 'source untouched' $f.Output
Assert-Equal 'F3 file is byte-identical' $true (Test-BytesEqual $beforeF (Get-FixtureBytes $rootF))

Write-Host 'G: -Fix corrects a correctable finding and leaves the file clean'
$rootG = New-FakeRepo 'g-fix-correctable' $misordered
$gBefore = Invoke-Runner $rootG @($fixtureRel)
Assert-Equal 'G0 the judge fails before the correction' 1 $gBefore.Exit
$g = Invoke-Fixer $rootG @($fixtureRel)
Assert-Equal 'G1 exit 0' 0 $g.Exit
Assert-Match 'G2 says nothing was made worse' 'none made worse' $g.Output
$gJudge = Invoke-Runner $rootG @($fixtureRel)
Assert-Equal 'G3 the judge is clean afterwards' 0 $gJudge.Exit

Write-Host 'H: -Fix restores a file its own correction made worse'
$rootH = New-FakeRepo 'h-fix-regresses' $longCall
New-FixtureBaseline $rootH
Set-Fixture $rootH $longCallPlusFinding
$beforeH = Get-FixtureBytes $rootH
$hJudge = Invoke-Runner $rootH @($fixtureRel)
Assert-Equal 'H1 the judge fails first, so the corrector is offered the file' 1 $hJudge.Exit
$h = Invoke-Fixer $rootH @($fixtureRel)
Assert-Equal 'H2 exit 0' 0 $h.Exit
Assert-Match 'H3 says it restored the file' 'restored because the correction' $h.Output
Assert-Equal 'H4 file is byte-identical to the pre-correction state' $true (Test-BytesEqual $beforeH (Get-FixtureBytes $rootH))

if (Test-Path $sandbox) { Remove-Item $sandbox -Recurse -Force }
Write-Host 'sandbox removed'

Write-Host ''
if ($script:Failed -gt 0) {
    Write-Error "detekt-scoped tests: $($script:Passed) passed, $($script:Failed) FAILED" -ErrorAction Continue
    exit 1
}
Write-Host "detekt-scoped tests: $($script:Passed) passed" -ForegroundColor Green
exit 0
