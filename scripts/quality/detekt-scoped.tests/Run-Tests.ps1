#requires -Version 7.0
# Run-Tests.ps1 (S1595) - contract suite for detekt-scoped.ps1.
#
# The point of this suite is the exit-2 path. A scoped runner assembles its analyser from the
# gradle dependency cache, so it WILL break on some future version bump - and the failure mode
# that matters is not "it breaks", it is "it breaks and reports clean". Everything else here is
# scaffolding around pinning that one behaviour.
#
# Each case builds a throwaway repo root under temp/ - a detekt config, a baseline, a build file
# carrying the version pin, and a fixture - so no case touches the real source tree. The
# dependency cache stays real: these run the actual analyser, not a stub.
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
$runner = Join-Path $RepoRoot 'scripts/quality/detekt-scoped.ps1'
$sandbox = Join-Path $RepoRoot 'temp/detekt-scoped-tests'

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
    '<?xml version="1.0" ?><SmellBaseline><ManuallySuppressedIssues/><CurrentIssues/></SmellBaseline>' |
        Set-Content (Join-Path $root 'config/detekt/baseline-app_v2.xml') -Encoding UTF8
    # The runner reads the pin from here, so the fake repo must carry the same declaration shape.
    $pin = Select-String -LiteralPath (Join-Path $RepoRoot 'build.gradle.kts') `
        -Pattern 'id\("io\.gitlab\.arturbosch\.detekt"\)\s+version\s+"[^"]+"' | Select-Object -First 1
    if (-not $pin) { throw 'could not read the detekt pin from the real build.gradle.kts' }
    "plugins { $($pin.Matches[0].Value) apply false }" | Set-Content (Join-Path $root 'build.gradle.kts') -Encoding UTF8
    $FixtureBody | Set-Content (Join-Path $pkgDir 'Fixture.kt') -Encoding UTF8
    return $root
}

function Invoke-Runner([string] $Root, [string[]] $Files, [string] $CacheRoot) {
    $argv = @('-NoProfile', '-File', $runner, '-RepoRoot', $Root, '-ChangedFiles', ($Files -join ','))
    if ($CacheRoot) { $argv += @('-CacheRoot', $CacheRoot) }
    $out = & pwsh @argv 2>&1 | Out-String
    return @{ Exit = $LASTEXITCODE; Output = $out }
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

Write-Host 'A: a fresh finding is reported and blocks'
$rootA = New-FakeRepo 'a-dirty' $dirty
$a = Invoke-Runner $rootA @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt')
Assert-Equal 'A1 exit 1 on a finding' 1 $a.Exit
Assert-Match 'A2 names the rule' 'ReturnCount' $a.Output
Assert-Match 'A3 names file, line and column' 'fixture\.kt:\d+:\d+' $a.Output

Write-Host 'B: a clean file passes'
$rootB = New-FakeRepo 'b-clean' $clean
$b = Invoke-Runner $rootB @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt')
Assert-Equal 'B1 exit 0 when clean' 0 $b.Exit
Assert-Match 'B2 says the whole rule set was applied' 'full configured rule set' $b.Output

Write-Host 'C: an unusable dependency cache cannot verify - and never reports clean'
$rootC = New-FakeRepo 'c-nocache' $dirty
$emptyCache = Join-Path $sandbox 'empty-cache'
New-Item -ItemType Directory -Path $emptyCache -Force | Out-Null
$c = Invoke-Runner $rootC @('app_v2/src/main/java/com/sza/fastmediasorter/Fixture.kt') $emptyCache
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

if (Test-Path $sandbox) { Remove-Item $sandbox -Recurse -Force }
Write-Host 'sandbox removed'

Write-Host ''
if ($script:Failed -gt 0) {
    Write-Error "detekt-scoped tests: $($script:Passed) passed, $($script:Failed) FAILED" -ErrorAction Continue
    exit 1
}
Write-Host "detekt-scoped tests: $($script:Passed) passed" -ForegroundColor Green
exit 0
