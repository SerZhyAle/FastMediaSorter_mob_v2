# Run-Tests.ps1 (S1453) - regression suite for the flavor/source-set mount map and the gate on it.
#
# A gate that has only ever been seen green proves nothing (the S1244/S1070/S1347 lesson). Three of
# this map's behaviours cannot be demonstrated against the real repository at all: an unattributed
# mount line, a mounted test set whose directory is absent, and a shared test that references a
# flavor-scoped type - the last one because the tree is currently clean and must stay that way.
#
# So every case runs against a synthetic repository built under temp/scratch and removed in a
# finally block. Nothing here writes into app_v2/src.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/flavor-source-map.ps1')

$gateScript = Join-Path $repoRoot 'scripts/quality/assert-shared-test-flavor-scope.ps1'
$completenessScript = Join-Path $repoRoot 'scripts/quality/assert-test-suite-complete.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# A build file with both mount syntaxes: an explicit getByName block and a forEach loop over a
# list of unit-test sets. testMissing is mounted deliberately and never created on disk.
$cleanGradle = @'
android {
    productFlavors {
        create("alpha") {
            dimension = "version"
        }
        create("beta") {
            dimension = "version"
        }
    }

    sourceSets {
        getByName("alpha") {
            kotlin.directories.add("src/sharedOne/java")
        }
        listOf("testAlpha", "testBeta").forEach { unitTestSet ->
            getByName(unitTestSet) {
                kotlin.directories.add("src/testShared/java")
                kotlin.directories.add("src/testMissing/java")
            }
        }
    }
}
'@

# Real flavor names here, because the gate's message names the flavors that break and the case is
# only convincing if the reader recognises them.
$gateGradle = @'
android {
    productFlavors {
        create("standard") {
            dimension = "version"
        }
        create("lite") {
            dimension = "version"
        }
    }

    sourceSets {
        getByName("standard") {
            kotlin.directories.add("src/sharedOne/java")
        }
    }
}
'@

# testSharedOne mirrors src/sharedOne and reaches one flavor short of it - the drift the mirror rule
# refuses. testCapability has no src/capability to mirror, which is the exempt shape it must ignore.
$mirrorDriftGradle = @'
android {
    productFlavors {
        create("standard") {
            dimension = "version"
        }
        create("lite") {
            dimension = "version"
        }
    }

    sourceSets {
        getByName("standard") {
            kotlin.directories.add("src/sharedOne/java")
        }
        getByName("lite") {
            kotlin.directories.add("src/sharedOne/java")
        }
        getByName("testStandard") {
            kotlin.directories.add("src/testSharedOne/java")
            kotlin.directories.add("src/testCapability/java")
        }
        getByName("testLite") {
            kotlin.directories.add("src/testCapability/java")
        }
    }
}
'@

$mirrorOkGradle = $mirrorDriftGradle -replace '(?m)^        getByName\("testLite"\) \{$', @'
        getByName("testLite") {
            kotlin.directories.add("src/testSharedOne/java")
'@

# testShared exists on disk and carries a test class; testMissing is mounted and never created.
$denominatorGradle = @'
android {
    productFlavors {
        create("standard") {
            dimension = "version"
        }
        create("lite") {
            dimension = "version"
        }
    }

    sourceSets {
        getByName("testStandard") {
            kotlin.directories.add("src/testShared/java")
            kotlin.directories.add("src/testMissing/java")
        }
    }
}
'@

# Same file plus one mount that sits directly in sourceSets, attributable to no set.
$orphanGradle = $cleanGradle -replace '(?m)^    sourceSets \{$', @'
    sourceSets {
        kotlin.directories.add("src/orphan/java")
'@

function New-FixtureRepo {
    param(
        [Parameter(Mandatory)][string]$Gradle,
        [Parameter(Mandatory)][string[]]$SourceDirs,
        [Parameter(Mandatory)][string]$Suffix
    )
    $root = Join-Path $repoRoot "temp/scratch/s1453-fixture-$PID-$Suffix"
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $root 'app_v2') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $root 'app_v2/build.gradle.kts') -Value $Gradle -Encoding UTF8
    foreach ($dir in $SourceDirs) {
        New-Item -ItemType Directory -Path (Join-Path $root "app_v2/src/$dir") -Force | Out-Null
    }
    return $root
}

$fixtures = @()
try {
    Write-Host 'Parser: mount map derived from a synthetic build file' -ForegroundColor Yellow

    $clean = New-FixtureRepo -Gradle $cleanGradle -Suffix 'clean' `
        -SourceDirs @('main', 'test', 'testAlpha', 'testShared', 'sharedOne', 'alpha', 'beta')
    $fixtures += $clean
    $map = Get-FlavorSourceMap -RepoRoot $clean

    Assert-That 'P1 explicit getByName mount is attributed' `
    (($map.MainSetMounts['sharedOne'] -join ',') -eq 'alpha') `
        "got '$($map.MainSetMounts['sharedOne'] -join ',')'"

    Assert-That 'P1 forEach loop mounts into every listed target' `
    ((($map.TestSetMounts['testShared'] | Sort-Object) -join ',') -eq 'testAlpha,testBeta') `
        "got '$($map.TestSetMounts['testShared'] -join ',')'"

    Assert-That 'P2 conventional same-name set is included without a build-file line' `
    ($map.Flavors['alpha'].Contains('alpha') -and $map.Flavors['beta'].Contains('beta')) `
        "alpha -> '$($map.Flavors['alpha'] -join ',')'"

    Assert-That 'P4 mounted test set with no directory is skipped' `
    (((Get-EffectiveTestSourceRoots -Map $map -Flavor 'alpha' | Sort-Object) -join ',') -eq 'app_v2/src/test,app_v2/src/testAlpha,app_v2/src/testShared') `
        "got '$((Get-EffectiveTestSourceRoots -Map $map -Flavor 'alpha') -join ',')'"

    $orphan = New-FixtureRepo -Gradle $orphanGradle -Suffix 'orphan' `
        -SourceDirs @('main', 'test', 'testAlpha', 'testShared', 'sharedOne', 'alpha', 'beta')
    $fixtures += $orphan
    $orphanMap = Get-FlavorSourceMap -RepoRoot $orphan

    Assert-That 'P3 mount attributable to no set is reported as unparsed' `
    ($orphanMap.Unparsed.Count -eq 1 -and $orphanMap.Unparsed[0].Text -like '*src/orphan/java*') `
        "got $($orphanMap.Unparsed.Count) unparsed line(s)"

    Write-Host ''
    Write-Host 'Gate: violation and non-violation shapes over a synthetic repository' -ForegroundColor Yellow

    $gateRoot = New-FixtureRepo -Gradle $gateGradle -Suffix 'gate' `
        -SourceDirs @('main/java/com/x', 'test/java/com/x', 'test/java/com/y', 'testStandard/java/com/x', 'testStandard/java/com/y', 'sharedOne/java/com/x', 'standard/java/com/x', 'lite/java/com/x')
    $fixtures += $gateRoot

    Set-Content -LiteralPath (Join-Path $gateRoot 'app_v2/src/sharedOne/java/com/x/StreamProbe.kt') `
        -Value "package com.x`n`nclass StreamProbe" -Encoding UTF8
    # A flavor-scoped name deliberately colliding with a platform type's simple name.
    Set-Content -LiteralPath (Join-Path $gateRoot 'app_v2/src/sharedOne/java/com/x/KeyEvent.kt') `
        -Value "package com.x`n`nclass KeyEvent" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $gateRoot 'app_v2/src/main/java/com/x/MainProbe.kt') `
        -Value "package com.x`n`nclass MainProbe" -Encoding UTF8
    # The S1455 shape: one FQCN with a copy in every flavor, so every flavor can see it.
    foreach ($flavor in @('standard', 'lite')) {
        Set-Content -LiteralPath (Join-Path $gateRoot "app_v2/src/$flavor/java/com/x/EveryFlavorCatalog.kt") `
            -Value "package com.x`n`nobject EveryFlavorCatalog" -Encoding UTF8
    }

    $gateCases = @(
        @{ Name = 'G1 shared test importing a flavor-scoped name fails'; Path = 'test/java/com/y/ImportTest.kt'; Body = "package com.y`n`nimport com.x.StreamProbe`n`nclass ImportTest"; Expected = 1 }
        @{ Name = 'G2 shared test naming a same-package flavor-scoped type fails'; Path = 'test/java/com/x/SamePackageTest.kt'; Body = "package com.x`n`nclass SamePackageTest {`n    val probe = StreamProbe()`n}"; Expected = 1 }
        @{ Name = 'G3 type with a copy in every flavor passes'; Path = 'test/java/com/y/EveryFlavorTest.kt'; Body = "package com.y`n`nimport com.x.EveryFlavorCatalog`n`nclass EveryFlavorTest"; Expected = 0 }
        @{ Name = 'G4 platform type colliding on simple name passes'; Path = 'test/java/com/y/PlatformTest.kt'; Body = "package com.y`n`nimport android.view.KeyEvent`n`nclass PlatformTest"; Expected = 0 }
        @{ Name = 'G5 narrow test home for a main subject fails'; Path = 'testStandard/java/com/y/MainProbeTest.kt'; Body = "package com.y`n`nimport com.x.MainProbe`n`nclass MainProbeTest"; Expected = 1 }
        @{ Name = 'G6 narrow test home matching its single-flavor subject passes'; Path = 'testStandard/java/com/y/StreamProbeTest.kt'; Body = "package com.y`n`nimport com.x.StreamProbe`n`nclass StreamProbeTest"; Expected = 0 }
        @{ Name = 'G7 same-package scoped subject overrides a misleading test file name'; Path = 'testStandard/java/com/x/MainProbeTest.kt'; Body = "package com.x`n`nclass MainProbeTest { val probe = StreamProbe() }"; Expected = 0 }
    )

    foreach ($case in $gateCases) {
        $testFile = Join-Path $gateRoot "app_v2/src/$($case.Path)"
        Set-Content -LiteralPath $testFile -Value $case.Body -Encoding UTF8
        & $pwshExe -NoProfile -File $gateScript -Gate -Quiet -RepoRoot $gateRoot | Out-Null
        $actual = $LASTEXITCODE
        Remove-Item -LiteralPath $testFile -Force
        Assert-That $case.Name ($actual -eq $case.Expected) "expected exit $($case.Expected), got $actual"
    }

    # P3 proves the parser flags an unattributable mount; this drives the gate itself over that
    # fixture, because the failure this ticket exists to prevent is a narrowed scan reported as
    # clean. The output check separates the unparsed-mount exit 2 from the missing-test-set one.
    $orphanOutput = & $pwshExe -NoProfile -File $gateScript -Gate -Quiet -RepoRoot $orphan 2>&1
    $orphanExit = $LASTEXITCODE
    Assert-That 'G5 unparsed mount line exits could-not-verify, not clean' `
    ($orphanExit -eq 2 -and (($orphanOutput -join "`n") -like '*src/orphan/java*')) `
        "expected exit 2 naming the unparsed line, got exit $orphanExit"

    Write-Host ''
    Write-Host 'Mirror: test-set mount list against its main counterpart' -ForegroundColor Yellow

    $mirrorDirs = @('main/java', 'test/java', 'sharedOne/java', 'testSharedOne/java', 'testCapability/java', 'standard/java', 'lite/java')

    $driftRoot = New-FixtureRepo -Gradle $mirrorDriftGradle -Suffix 'mirror-drift' -SourceDirs $mirrorDirs
    $fixtures += $driftRoot
    $driftOutput = & $pwshExe -NoProfile -File $gateScript -Gate -Quiet -RepoRoot $driftRoot 2>&1
    $driftExit = $LASTEXITCODE
    Assert-That 'M1 test set reaching one flavor short of its main set fails' `
    ($driftExit -eq 1 -and (($driftOutput -join "`n") -like '*testSharedOne*')) `
        "exit $driftExit, output did not name testSharedOne"

    $okRoot = New-FixtureRepo -Gradle $mirrorOkGradle -Suffix 'mirror-ok' -SourceDirs $mirrorDirs
    $fixtures += $okRoot
    & $pwshExe -NoProfile -File $gateScript -Gate -Quiet -RepoRoot $okRoot | Out-Null
    $okExit = $LASTEXITCODE
    Assert-That 'M2 test set with no main counterpart directory is exempt' `
    ($okExit -eq 0) `
        "expected exit 0 with testCapability present and unmirrored, got $okExit"

    Write-Host ''
    Write-Host 'Completeness: denominator over the effective source roots' -ForegroundColor Yellow

    $denominatorRoot = New-FixtureRepo -Gradle $denominatorGradle -Suffix 'denominator' `
        -SourceDirs @('main/java', 'test/java/com/y', 'testStandard/java', 'testShared/java/com/z', 'standard/java', 'lite/java')
    $fixtures += $denominatorRoot

    Set-Content -LiteralPath (Join-Path $denominatorRoot 'app_v2/src/test/java/com/y/OneTest.kt') `
        -Value "package com.y`n`nclass OneTest" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $denominatorRoot 'app_v2/src/testShared/java/com/z/TwoTest.kt') `
        -Value "package com.z`n`nclass TwoTest" -Encoding UTF8

    $reportDir = Join-Path $denominatorRoot 'app_v2/build/test-results/testStandardDebugUnitTest'
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $reportDir 'TEST-com.y.OneTest.xml') `
        -Value '<testsuite name="com.y.OneTest" tests="1" failures="0"/>' -Encoding UTF8

    # With the old single-root denominator this reads 1 report for 1 class and passes. It can only
    # fail if the mounted testShared set entered the count, which is exactly what is being proved.
    & $pwshExe -NoProfile -File $completenessScript -TaskDir 'testStandardDebugUnitTest' -RepoRoot $denominatorRoot | Out-Null
    $missingReportExit = $LASTEXITCODE
    Assert-That 'D1 class from a mounted test set counts toward the denominator' `
    ($missingReportExit -eq 1) `
        "expected exit 1 with com.z unreported, got $missingReportExit"

    Set-Content -LiteralPath (Join-Path $reportDir 'TEST-com.z.TwoTest.xml') `
        -Value '<testsuite name="com.z.TwoTest" tests="1" failures="0"/>' -Encoding UTF8
    & $pwshExe -NoProfile -File $completenessScript -TaskDir 'testStandardDebugUnitTest' -RepoRoot $denominatorRoot | Out-Null
    $absentRootExit = $LASTEXITCODE
    Assert-That 'D2 mounted test set with no directory contributes nothing' `
    ($absentRootExit -eq 0) `
        "expected exit 0 with testMissing mounted but absent, got $absentRootExit"

    # S1732: flavorless module (like wear) with no productFlavors block
    $flavorlessGradle = @'
android {
    defaultConfig {
        applicationId = "com.sza.wear"
    }
}
'@
    $flavorlessRoot = Join-Path $repoRoot "temp/scratch/s1732-fixture-$PID-flavorless"
    if (Test-Path -LiteralPath $flavorlessRoot) { Remove-Item -LiteralPath $flavorlessRoot -Recurse -Force }
    $fixtures += $flavorlessRoot
    New-Item -ItemType Directory -Path (Join-Path $flavorlessRoot 'wear/src/test/java/com/w') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $flavorlessRoot 'wear/build.gradle.kts') -Value $flavorlessGradle -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $flavorlessRoot 'wear/src/test/java/com/w/WearTest.kt') `
        -Value "package com.w`n`nclass WearTest" -Encoding UTF8

    $flavorlessReportDir = Join-Path $flavorlessRoot 'wear/build/test-results/testDebugUnitTest'
    New-Item -ItemType Directory -Path $flavorlessReportDir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $flavorlessReportDir 'TEST-com.w.WearTest.xml') `
        -Value '<testsuite name="com.w.WearTest" tests="1" failures="0"/>' -Encoding UTF8

    & $pwshExe -NoProfile -File $completenessScript -Module 'wear' -TaskDir 'testDebugUnitTest' -RepoRoot $flavorlessRoot | Out-Null
    $flavorlessPassExit = $LASTEXITCODE
    Assert-That 'D3 flavorless module with complete reports passes' `
    ($flavorlessPassExit -eq 0) `
        "expected exit 0 for flavorless module, got $flavorlessPassExit"

    Remove-Item -LiteralPath (Join-Path $flavorlessReportDir 'TEST-com.w.WearTest.xml') -Force
    & $pwshExe -NoProfile -File $completenessScript -Module 'wear' -TaskDir 'testDebugUnitTest' -RepoRoot $flavorlessRoot 2>&1 | Out-Null
    $flavorlessMissingExit = $LASTEXITCODE
    Assert-That 'D4 flavorless module with missing reports fails' `
    ($flavorlessMissingExit -eq 1) `
        "expected exit 1 for flavorless module missing report, got $flavorlessMissingExit"
}
finally {
    foreach ($fixture in $fixtures) {
        if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    }
}

Write-Host ''
Write-Host "assert-shared-test-flavor-scope.tests: $($script:pass) passed, $($script:fail) failed."
if ($script:fail -gt 0) { exit 1 }
exit 0
