#requires -Version 7.0
<#
.SYNOPSIS
    Fail when a test in the shared unit-test set references a type that lives only in a
    flavor-scoped source set (S1453).

.DESCRIPTION
    app_v2/src/test is compiled for EVERY flavor. A test placed there for a class that lives in a
    flavor-scoped set does not merely fail - it breaks unit-test COMPILATION on every flavor
    mounting the disabled counterpart, so no test runs there at all. Nobody routinely runs unit
    tests on the reduced flavors, so the break surfaces only when a release gate asks for them:
    docs/RELEASE_READINESS_STANDARD.md requires PermissionRegistryManifestParityTest on lite as a
    release blocker, and while lite unit tests did not compile that blocker could not run at all.

    S1450 repaired two such clusters by hand and wrote dev/FLAVOR_DEVELOPMENT_RULES.md RULE 7 as
    the human-facing rule; S1433 added a third. CLAUDE.md's own routing audit records ungated rules
    holding at 1-8% against about 99% for gated ones, which is why the rule needed a gate.

    Compiling the unit tests of the most reduced flavor was considered and rejected: lite is the
    only flavor mounting src/cloudDisabled, so a reference to it compiles there and breaks the other
    five. Full coverage would need all six unit-test compilations - minutes under BUILD.LOCK, not a
    fast gate. This check parses one build file and two source trees instead, with no gradle daemon.

    MEASURED COST: 1943 ms inside assert-fast-gates.ps1 on 2026-08-09, reading about 2650 .kt files -
    1920 in src/main, 265 across the flavor-scoped sets, 470 in the shared unit-test set. src/main is
    the largest share and is not optional: a name declared there is visible to every flavor and has to
    be excluded from the index. Recorded so a later reader can tell growth from a regression rather
    than guess; re-measure if the figure and the batch's own column stop agreeing.

.PARAMETER Gate
    Exit non-zero on a violation. Without it the script reports and exits 0, so it can be run for
    information without failing a caller.

.PARAMETER Quiet
    Suppress the per-run progress lines; the verdict line and any violation rows still print. This
    is what assert-fast-gates.ps1 passes when it runs the gate as part of the batch.

.PARAMETER RepoRoot
    Repository root. Defaults to the checkout this script sits in; the tests point it at a fixture.

.PARAMETER Module
    Gradle module whose source sets are examined.

.PARAMETER DumpIndex
    Print the declaration index - one row per flavor-scoped name with its source sets and the
    flavors that cannot see it - and exit 0. Diagnostic only; no violation is reported.

.EXIT CODES
    0 - no violation (or violations found without -Gate).
    1 - at least one violation, and -Gate was passed.
    2 - could not verify: the build file is unreadable, or its mount map carries a line this gate
        could not attribute to a source set. "Did not look" is not "looked and found nothing".

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.ps1
    pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.ps1 -Gate -Quiet
#>
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$Module = 'app_v2',
    [switch]$DumpIndex
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/flavor-source-map.ps1')

function Write-Progressline([string]$Message) {
    if (-not $Quiet) { Write-Host $Message }
}

try {
    $map = Get-FlavorSourceMap -RepoRoot $RepoRoot -Module $Module
}
catch {
    Write-Error "assert-shared-test-flavor-scope: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if ($map.Unparsed.Count -gt 0) {
    Write-Host 'assert-shared-test-flavor-scope: the mount map is incomplete - these lines were attributed to no source set:' -ForegroundColor Red
    foreach ($line in $map.Unparsed) { Write-Host "    $($map.GradleFile):$($line.Line)  $($line.Text)" }
    $why = 'assert-shared-test-flavor-scope: a mount syntax this gate cannot read narrows the scan ' +
    'silently, so the run is reported as unverifiable rather than clean. Teach the parser the new ' +
    'form in scripts/quality/lib/flavor-source-map.ps1.'
    Write-Error $why -ErrorAction Continue
    exit 2
}

Write-Progressline ("reading $($map.FlavorNames.Count) flavor(s) from $($map.GradleFile)")

# A type declaration matched at zero indentation. Types take the identifier straight after the
# keyword; callables may carry generics and an extension receiver first, and taking the first
# identifier there would index `fun Context.foo()` as `Context` - a name almost every test mentions.
$script:TypeDeclRx = [regex]'^(?:(?:public|internal|private|protected|abstract|sealed|open|data|value|inline|expect|actual)\s+)*(?:class|interface|object|enum\s+class|annotation\s+class|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)'
$script:CallableDeclRx = [regex]'^(?:(?:public|internal|private|protected|inline|suspend|expect|actual|operator|infix|const|lateinit)\s+)*(?:fun|val|var)\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*(?:<[^>]*>)?\s*\.\s*)?([A-Za-z_][A-Za-z0-9_]*)'

function Get-TopLevelDeclarations([string]$Directory) {
    $found = @{}
    if (-not (Test-Path -LiteralPath $Directory)) { return $found }
    foreach ($file in Get-ChildItem -LiteralPath $Directory -Recurse -Filter '*.kt' -File -ErrorAction SilentlyContinue) {
        $package = $null
        foreach ($line in [System.IO.File]::ReadAllLines($file.FullName)) {
            if ($null -eq $package) {
                if ($line -match '^package\s+([\w.]+)') { $package = $Matches[1] }
                continue
            }
            if ($line.Length -eq 0 -or $line[0] -eq ' ' -or $line[0] -eq "`t") { continue }
            $match = $script:TypeDeclRx.Match($line)
            if (-not $match.Success) { $match = $script:CallableDeclRx.Match($line) }
            if ($match.Success) { $found["$package.$($match.Groups[1].Value)"] = $file.FullName }
        }
    }
    return $found
}

$mainDeclarations = Get-TopLevelDeclarations (Join-Path $RepoRoot "$Module/src/main")

# Only sets some flavor actually mounts are scanned. A set nobody mounts (src/castDisabled, kept for
# the planned foss flavor) cannot break one flavor and not another - a reference to it fails to
# compile everywhere at once, which the ordinary build reports on the spot.
$declaringSets = @{}
foreach ($set in $map.MainSetMounts.Keys) {
    foreach ($fqcn in (Get-TopLevelDeclarations (Join-Path $RepoRoot "$Module/src/$set")).Keys) {
        if (-not $declaringSets.ContainsKey($fqcn)) { $declaringSets[$fqcn] = [System.Collections.Generic.List[string]]::new() }
        $declaringSets[$fqcn].Add($set)
    }
}

# A name is flavor-scoped only when the flavors that can see it are a PROPER subset of all of them.
# One FQCN with a copy in every flavor - OfficeDocumentFamilyCatalog, the S1455 shape - compiles
# everywhere and is not this defect; it differs in behaviour, not in whether it links.
$index = @{}
foreach ($fqcn in $declaringSets.Keys) {
    if ($mainDeclarations.ContainsKey($fqcn)) { continue }
    $visible = [System.Collections.Generic.List[string]]::new()
    foreach ($set in $declaringSets[$fqcn]) {
        foreach ($flavor in $map.MainSetMounts[$set]) {
            if (-not $visible.Contains($flavor)) { $visible.Add($flavor) }
        }
    }
    if ($visible.Count -ge $map.FlavorNames.Count) { continue }
    $index[$fqcn] = [pscustomobject]@{
        Fqcn    = $fqcn
        Simple  = $fqcn.Substring($fqcn.LastIndexOf('.') + 1)
        Package = $fqcn.Substring(0, $fqcn.LastIndexOf('.'))
        Sets    = @($declaringSets[$fqcn])
        Missing = @($map.FlavorNames | Where-Object { -not $visible.Contains($_) })
    }
}

Write-Progressline ("indexed $($index.Count) flavor-scoped declaration(s) across $($map.MainSetMounts.Count) source set(s)")

if ($DumpIndex) {
    foreach ($entry in ($index.Values | Sort-Object Fqcn)) {
        Write-Host ("{0}`t{1}`t{2}" -f $entry.Fqcn, ($entry.Sets -join ','), ($entry.Missing -join ','))
    }
    exit 0
}

# Same-package lookup: Kotlin needs no import for a name declared in the file's own package, so an
# import scan alone would miss that half. Every other simple-name coincidence is ignored on purpose -
# matching bare names project-wide is what drowned the ad-hoc S1450 attempt in false positives.
$byPackage = @{}
foreach ($entry in $index.Values) {
    if (-not $byPackage.ContainsKey($entry.Package)) { $byPackage[$entry.Package] = [System.Collections.Generic.List[object]]::new() }
    $byPackage[$entry.Package].Add($entry)
}

$sharedTestRoot = Join-Path $RepoRoot "$Module/src/test"
if (-not (Test-Path -LiteralPath $sharedTestRoot)) {
    Write-Error "assert-shared-test-flavor-scope: no shared unit-test set at $sharedTestRoot - nothing was examined." -ErrorAction Continue
    exit 2
}

$violations = [System.Collections.Generic.List[object]]::new()
$scanned = 0

foreach ($file in Get-ChildItem -LiteralPath $sharedTestRoot -Recurse -Filter '*.kt' -File) {
    $scanned++
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    $relative = $file.FullName.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')

    $package = $null
    foreach ($line in $lines) {
        if ($line -match '^package\s+([\w.]+)') { $package = $Matches[1]; break }
    }
    $localNames = if ($package -and $byPackage.ContainsKey($package)) { $byPackage[$package] } else { @() }

    for ($n = 0; $n -lt $lines.Count; $n++) {
        $line = $lines[$n]
        if ($line -match '^\s*import\s+([\w.]+)') {
            $imported = $Matches[1]
            if ($index.ContainsKey($imported)) {
                $violations.Add([pscustomobject]@{ File = $relative; Line = $n + 1; Entry = $index[$imported]; Via = 'import' })
            }
            continue
        }
        foreach ($entry in $localNames) {
            if ($line -match "\b$([regex]::Escape($entry.Simple))\b") {
                $violations.Add([pscustomobject]@{ File = $relative; Line = $n + 1; Entry = $entry; Via = 'same package' })
                break
            }
        }
    }
}

Write-Progressline ("scanned $scanned shared test file(s) under $Module/src/test")

# Where the test belongs. A set only one flavor mounts sends the test to that flavor's own unit-test
# set; a set several flavors share sends it to the matching shared test set, which build.gradle.kts
# mounts into exactly the same flavors. Duplicating the file into each test<Flavor> is the wrong fix.
function Get-TargetTestSet([object]$Entry) {
    $set = $Entry.Sets[0]
    $mountedBy = @($map.MainSetMounts[$set])
    if ($mountedBy.Count -eq 1) { return Get-UnitTestSetName $mountedBy[0] }
    return 'test' + $set.Substring(0, 1).ToUpperInvariant() + $set.Substring(1)
}

# Second rule: a shared test set must be mounted into exactly the flavors that mount its main
# counterpart. Drift between the two lists puts the test back out of reach of a flavor that has the
# subject, or in reach of one that does not - which is how this defect class returns after a fix.
# Applied only where the main counterpart exists on disk: testDocumentsEnabled groups tests by a
# capability flag rather than by a shared source set, and has no src/documentsEnabled to mirror.
$mirrorIssues = [System.Collections.Generic.List[object]]::new()
foreach ($testSet in $map.TestSetMounts.Keys) {
    if ($testSet -notmatch '^test(?<suffix>[A-Z].*)$') { continue }
    $suffix = $Matches['suffix']
    $mainSet = $suffix.Substring(0, 1).ToLowerInvariant() + $suffix.Substring(1)
    if ($map.FlavorNames -contains $mainSet) { continue }
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot "$Module/src/$mainSet"))) { continue }

    $mainFlavors = @($map.MainSetMounts[$mainSet] | Sort-Object)
    $testFlavors = @($map.TestSetMounts[$testSet] |
        ForEach-Object { $_.Substring(4, 1).ToLowerInvariant() + $_.Substring(5) } |
        Sort-Object)
    if (($mainFlavors -join ',') -eq ($testFlavors -join ',')) { continue }

    $mirrorIssues.Add([pscustomobject]@{
            TestSet     = $testSet
            MainSet     = $mainSet
            MainFlavors = $mainFlavors
            TestFlavors = $testFlavors
        })
}

if ($mirrorIssues.Count -gt 0) {
    Write-Host "assert-shared-test-flavor-scope: FAIL - $($mirrorIssues.Count) test set(s) drifted from the main mount list." -ForegroundColor Red
    foreach ($issue in $mirrorIssues) {
        Write-Host ("    src/$($issue.TestSet) is mounted into: $($issue.TestFlavors -join ', ')")
        Write-Host ("        src/$($issue.MainSet) is mounted into: $($issue.MainFlavors -join ', ')")
    }
    $mirrorWhy = 'assert-shared-test-flavor-scope: mirror the two mount lists in ' +
    'app_v2/build.gradle.kts - a test set reaching a different flavor set than its subject ' +
    'reintroduces the defect this gate exists to refuse.'
    if ($Gate) {
        Write-Error $mirrorWhy -ErrorAction Continue
        exit 1
    }
    Write-Host $mirrorWhy
}

if ($violations.Count -gt 0) {
    Write-Host "assert-shared-test-flavor-scope: FAIL - $($violations.Count) shared test reference(s) to a flavor-scoped type." -ForegroundColor Red
    foreach ($violation in $violations) {
        $entry = $violation.Entry
        Write-Host ("    {0}:{1}  {2} ({3})" -f $violation.File, $violation.Line, $entry.Fqcn, $violation.Via)
        Write-Host ("        lives in $Module/src/$($entry.Sets -join ', ')/java - unit-test compilation breaks on: $($entry.Missing -join ', ')")
        Write-Host ("        move the test to $Module/src/$(Get-TargetTestSet $entry)/java")
    }
    $why = 'assert-shared-test-flavor-scope: the shared unit-test set compiles for EVERY flavor, so a ' +
    'reference to a flavor-scoped type breaks unit-test COMPILATION on the flavors listed above - no ' +
    'test runs there at all. See dev/FLAVOR_DEVELOPMENT_RULES.md RULE 7.'
    if ($Gate) {
        Write-Error $why -ErrorAction Continue
        exit 1
    }
    Write-Host $why
    exit 0
}

Write-Host 'assert-shared-test-flavor-scope: PASS - no shared test references a flavor-scoped type.' -ForegroundColor Green
exit 0
