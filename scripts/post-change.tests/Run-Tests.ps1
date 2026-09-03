#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for post-change gate dispatch and recovery hints.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$facadePath = Join-Path $repoRoot 'scripts/post-change.ps1'
$hintsPath = Join-Path $repoRoot 'scripts/quality/gate-recovery-hints.psd1'
$facade = Get-Content -LiteralPath $facadePath -Raw

foreach ($removedLabel in @(
    'flavor-flag-gate',
    'public-mutable-flow-gate',
    'deprecated-pm-flags-gate'
)) {
    if ($facade -match ('Invoke-Gate "' + [regex]::Escape($removedLabel) + '"')) {
        throw "Duplicate lexical dispatch '$removedLabel' is present."
    }
}

$neuroslopRoutes = [regex]::Matches($facade, 'Invoke-Gate "neuroslop-gate"').Count
if ($neuroslopRoutes -ne 1) {
    throw "Expected one neuroslop-gate route, got $neuroslopRoutes."
}

# S1939: the document-icon and icon-inventory gates left the per-ticket facade for the
# release-scope runner - their subject is a repository-wide inventory, not the changed file.
# The assertion follows them rather than being deleted: a gate that vanishes from one runner
# and is never picked up by another is exactly the silent hole this suite exists to catch.
foreach ($movedLabel in @('doc-icons-sync-gate', 'icon-inventory-sync-gate', 'device-profile-matrix-gate')) {
    if ($facade -match ([regex]::Escape($movedLabel))) {
        throw "Gate '$movedLabel' moved to the release-scope runner but is still wired in the facade."
    }
}
$releaseRunnerPath = Join-Path $PSScriptRoot '../quality/assert-release-scope-gates.ps1'
if (-not (Test-Path $releaseRunnerPath)) {
    throw 'Release-scope gate runner is absent; the moved gates run nowhere.'
}
$releaseRunner = Get-Content -LiteralPath $releaseRunnerPath -Raw
foreach ($movedScript in @('assert-doc-icons-sync.ps1', 'assert-icon-inventory-sync.ps1', 'assert-device-profile-matrix.ps1')) {
    if ($releaseRunner -notmatch [regex]::Escape($movedScript)) {
        throw "Moved gate '$movedScript' is not listed in the release-scope runner."
    }
}

$helpOutput = & pwsh -NoProfile -File $facadePath -? 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $helpOutput -notmatch '-Deleted') {
    throw 'post-change help does not expose the declared-deletion parameter.'
}

$rejectedDeletion = & pwsh -NoProfile -File $facadePath `
    -Deleted 'scripts/post-change.ps1' `
    -Target 'post-change-tests' `
    -Description 'reject an existing declared deletion' `
    -ChangeType Script 2>&1 | Out-String
if ($LASTEXITCODE -ne 2 -or $rejectedDeletion -notmatch 'named as deleted but still on disk') {
    throw 'post-change did not reject an existing path declared as deleted.'
}

if ($facade -notmatch '\$deletedFiles = @\(' -or $facade -notmatch '\$deletedLogEntries = @\(') {
    throw 'post-change does not route an accepted declared deletion into closure bookkeeping.'
}

if ($facade -notmatch '\$catalogChangedFiles = @\(\$changedFiles\) \+ @\(\$deletedFiles\)') {
    throw 'Declared deletions are not included in catalog-sync input.'
}

# S2269: automatic release is intentionally per closure, but it must announce the transition so
# the next phase cannot mistake a green closure for continued ownership of its code domain.
if ($facade -notmatch '\$releasedDomains = @\(') {
    throw 'post-change does not compute which code domains this session actually released.'
}
if ($facade -notmatch 'Before the next repository source, resource, build, or script edit') {
    throw 'post-change does not print the code-lock reacquisition handoff.'
}

$hints = Import-PowerShellDataFile -LiteralPath $hintsPath
$labels = [regex]::Matches($facade, 'Invoke-Gate "([^"]+)"') |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique
$missingHints = @($labels | Where-Object { -not $hints.ContainsKey($_) })
if ($missingHints.Count -gt 0) {
    throw "Missing recovery hint(s): $($missingHints -join ', ')."
}

foreach ($removedLabel in @(
    'flavor-flag-gate',
    'public-mutable-flow-gate',
    'deprecated-pm-flags-gate'
)) {
    if ($hints.ContainsKey($removedLabel)) {
        throw "Removed route '$removedLabel' still has a recovery hint."
    }
}

# S1915 - the resource-link gate. Source assertions first: the gate must stay conditional, must keep
# its named skip branch, and must never reach gradlew directly (CLAUDE.md Rule 23).
if ($facade -notmatch '\$runsResourceLinkGate = \$isResourceChange') {
    throw 'Resource-link classifier is not derived from $isResourceChange.'
}
if ($facade -notmatch '(?s)if \(\$runsResourceLinkGate\) \{.*?Invoke-Gate "resource-link-gate"') {
    throw 'Resource-link gate is missing or not conditional.'
}
if ($facade -notmatch 'Skip-Step "resource-link-gate"') {
    throw 'Resource-link gate has no named skip branch.'
}
if ($facade -notmatch '(?s)Invoke-Gate "resource-link-gate".*?-Mode Resources') {
    throw 'Resource-link gate does not request the resource-processing mode.'
}
$resourceGateBlock = [regex]::Match(
    $facade,
    '(?s)Invoke-Gate "resource-link-gate" \{.*?\r?\n    \}\r?\n').Value
# Assert the extraction found something before asserting anything ABOUT it: an empty string satisfies
# every -notmatch below, so a regex that silently stopped matching would read as a clean gate.
if ([string]::IsNullOrWhiteSpace($resourceGateBlock)) {
    throw 'Could not extract the resource-link gate block - the assertions below would pass vacuously.'
}
if ($resourceGateBlock -match 'gradlew') {
    throw 'Resource-link gate invokes gradlew directly instead of the BUILD.LOCK-taking helper.'
}
if ($resourceGateBlock -notmatch 'if \(\$LASTEXITCODE -ne 0\) \{ return \}') {
    throw 'Resource-link gate does not stop at the first failing flavor, so an earlier red would read as a pass.'
}
# S2121: the module must be DERIVED from the changed paths. Reading -Module is what made a change
# entirely under watchface/ link :app_v2: resources and print PASS.
if ($resourceGateBlock -match '-Module \$Module') {
    throw 'Resource-link gate still passes the declared -Module instead of the module derived from the changed paths.'
}
if ($facade -notmatch '\$resourceLinkResolution = Resolve-GradleModulesForPaths') {
    throw 'Resource-link gate does not resolve its module set from the changed paths.'
}
if ($resourceGateBlock -notmatch 'Unresolved\.Count -gt 0') {
    throw 'Resource-link gate does not refuse a resource path belonging to no registered module.'
}
if ($resourceGateBlock -notmatch 'LinksResources') {
    throw 'Resource-link gate does not skip a module that has no resource-processing task.'
}
# S2123: the build type is per-module too. A hardcoded Debug named a task :benchmark has never had -
# that module's only build types are nonMinifiedRelease and benchmarkRelease - and the gate recorded
# the module as unlinkable rather than the task name as unbuildable.
if ($resourceGateBlock -notmatch 'Get-GradleModuleDefaultBuildType') {
    throw 'Resource-link gate does not take its build type from the registry.'
}
if (([regex]::Matches($resourceGateBlock, '-BuildType')).Count -lt 2) {
    throw 'Resource-link gate does not pass -BuildType on both the flavorless and the per-flavor branch.'
}
# The duplicated flavor table is gone - one registry, read by the builder too.
if ($facade -match 'ResourceLinkFlavors\s*=\s*@\{') {
    throw 'The per-module flavor table is declared in the facade again instead of read from the registry.'
}
if ($facade -notmatch "gradle-modules\.ps1") {
    throw 'The facade does not load the Gradle module registry.'
}

# Behaviour of the variant selector, run from the facade's own function text so a rename or a logic
# change fails here rather than silently selecting the wrong variant.
$facadeAst = [System.Management.Automation.Language.Parser]::ParseFile($facadePath, [ref]$null, [ref]$null)
$selectorAst = $facadeAst.FindAll(
    {
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'Get-ResourceLinkFlavors'
    }, $true) | Select-Object -First 1
if (-not $selectorAst) {
    throw 'Get-ResourceLinkFlavors is not defined in the facade.'
}

# S2121: the harness loads the real registry instead of stubbing a flavor list. The stub it replaced
# had already drifted - it substituted a flat array where the facade had grown a per-module hashtable,
# so the selector was being exercised in a shape the facade no longer had.
$registryPath = Join-Path $repoRoot 'scripts/utils/gradle-modules.ps1'
$selectorHarness = [scriptblock]::Create(@"
param([string[]] `$normChangedFiles, [string] `$TargetModule)
. '$registryPath'
$($selectorAst.Extent.Text)
Get-ResourceLinkFlavors -TargetModule `$TargetModule
"@)

function Assert-FlavorSelection {
    param(
        [string] $Case,
        [string[]] $ChangedSet,
        [string] $TargetModule,
        [string[]] $Expected
    )
    $actual = @(& $selectorHarness $ChangedSet $TargetModule)
    $actualSorted = ($actual | Sort-Object) -join ','
    $expectedSorted = ($Expected | Sort-Object) -join ','
    if ($actualSorted -ne $expectedSorted) {
        throw "Flavor selection [$Case]: expected '$expectedSorted', got '$actualSorted'."
    }
    if ($actual.Count -ne @($actual | Sort-Object -Unique).Count) {
        throw "Flavor selection [$Case]: returned a duplicate - '$actualSorted'."
    }
}

Assert-FlavorSelection -Case 'kotlin only' -TargetModule 'app_v2' -Expected @('Standard') `
    -ChangedSet @('app_v2/src/main/java/com/sza/fastmediasorter/ui/Foo.kt')
Assert-FlavorSelection -Case 'main layout' -TargetModule 'app_v2' -Expected @('Standard') `
    -ChangedSet @('app_v2/src/main/res/layout/activity_main.xml')
Assert-FlavorSelection -Case 'vr resource' -TargetModule 'app_v2' -Expected @('Standard', 'Vr') `
    -ChangedSet @('app_v2/src/vr/res/values/strings.xml')
Assert-FlavorSelection -Case 'two flavor source sets' -TargetModule 'app_v2' `
    -Expected @('Standard', 'Vr', 'Lite') `
    -ChangedSet @(
        'app_v2/src/vr/res/values/strings.xml',
        'app_v2/src/lite/res/values/strings.xml',
        'app_v2/src/main/res/layout/activity_main.xml')
Assert-FlavorSelection -Case 'repeated flavor source set' -TargetModule 'app_v2' `
    -Expected @('Standard', 'Vr') `
    -ChangedSet @(
        'app_v2/src/vr/res/values/strings.xml',
        'app_v2/src/vr/res/layout/vr_player.xml')
# The watch declares Standard and NoLegal only, and check-standard-fast.ps1 exits 2 on any other
# -Flavor, so a phone flavor path in the set must not leak into a wear invocation.
Assert-FlavorSelection -Case 'wear module' -TargetModule 'wear' -Expected @('Standard') `
    -ChangedSet @('wear/src/main/res/values/strings.xml', 'app_v2/src/vr/res/values/strings.xml')
Assert-FlavorSelection -Case 'wear noLegal source set' -TargetModule 'wear' -Expected @('Standard', 'NoLegal') `
    -ChangedSet @('wear/src/noLegal/res/values/strings.xml')
# S2121: a module with no flavor dimension answers with an EMPTY set, which is what tells the gate to
# invoke the builder without -Flavor and gradle to run :watchface:processDebugResources.
Assert-FlavorSelection -Case 'flavorless module' -TargetModule 'watchface' -Expected @() `
    -ChangedSet @('watchface/src/main/res/values/colors.xml')
# Only THIS module's paths may select a flavor. A set spanning two modules used to offer each of them
# the other's source sets, which is harmless only while the gate runs for one declared module.
Assert-FlavorSelection -Case 'foreign flavor path does not leak in' -TargetModule 'app_v2' `
    -Expected @('Standard') `
    -ChangedSet @('app_v2/src/main/res/values/colors.xml', 'wear/src/noLegal/res/values/strings.xml')

# S2416 - Get-RoomRowsForChangedFiles must report "no database touched" as ZERO rows. It used to
# `return ,$matched.ToArray()`, and the comma is only correct for an UNWRAPPED caller: both call
# sites wrap in @(), which turns the empty result into a one-element array whose single element is
# the empty array. So a docs-only closure scored Count 1, ran the androidTest compile gate on a set
# carrying no .kt at all, and then threw on `$_.Module` under StrictMode - a PowerShell trace instead
# of a verdict, which is exactly the "found a defect" / "did not look" confusion the facade keeps
# exit 2 for. Asserted behaviourally rather than by matching the return statement, because the
# defect is a PAIRING of the return shape with the call site: either half can be rewritten and the
# bug returns only when both line up again.
$roomRegistryPath = Join-Path $repoRoot 'scripts/quality/lib/room-databases.ps1'
function Get-FacadeFunctionText {
    param([string] $Name)
    $ast = $facadeAst.FindAll(
        {
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq $Name
        }, $true) | Select-Object -First 1
    if (-not $ast) { throw "$Name is not defined in the facade." }
    return $ast.Extent.Text
}

$roomRowHarness = [scriptblock]::Create(@"
param([string[]] `$normChangedFiles, [string[]] `$Fields)
Set-StrictMode -Version Latest
. '$roomRegistryPath'
`$roomDatabaseRows = @(Get-RoomDatabaseRegistry -RepoRoot '$repoRoot')
$(Get-FacadeFunctionText 'Test-PathUnderDir')
$(Get-FacadeFunctionText 'Get-RoomRowsForChangedFiles')
Get-RoomRowsForChangedFiles -Fields `$Fields
"@)

function Assert-RoomRows {
    param(
        [string] $Case,
        [string[]] $ChangedSet,
        [string[]] $Fields,
        [string[]] $ExpectedModules
    )
    $rows = @(& $roomRowHarness $ChangedSet $Fields)
    if ($rows.Count -ne $ExpectedModules.Count) {
        throw "Room rows [$Case]: expected $($ExpectedModules.Count) row(s), got $($rows.Count)."
    }
    # The defect surfaced as a property access, not as a wrong count: the phantom element is an
    # Object[], so reading .Module on it throws. Touch the property on every returned row.
    $actual = @($rows | ForEach-Object { $_.Module } | Sort-Object)
    $expected = @($ExpectedModules | Sort-Object)
    if (($actual -join ',') -ne ($expected -join ',')) {
        throw "Room rows [$Case]: expected module(s) '$($expected -join ',')', got '$($actual -join ',')'."
    }
}

$roomAndroidTestFields = @('AndroidTestDir', 'MigrationDir')
$roomContractFields = @('MigrationDir', 'SchemaDir', 'RegistrationFile')

# Set size is irrelevant to the match - asserted at one and at three files because the ticket that
# opened this recorded a size-dependent symptom that direct measurement refuted.
Assert-RoomRows -Case 'docs-only, one file' -Fields $roomAndroidTestFields -ExpectedModules @() `
    -ChangedSet @('PLAN/S2416_post-change-crashes-empty-room-row-set.md')
Assert-RoomRows -Case 'docs-only, three files' -Fields $roomAndroidTestFields -ExpectedModules @() `
    -ChangedSet @('PLAN/a.md', 'PLAN/b.md', 'PLAN/c.md')
Assert-RoomRows -Case 'docs-only, contract fields' -Fields $roomContractFields -ExpectedModules @() `
    -ChangedSet @('PLAN/a.md', 'PLAN/b.md', 'PLAN/c.md')
# A Kotlin change that touches no registered database is still zero rows - the gate is keyed on the
# database directories, not on the file extension.
Assert-RoomRows -Case 'kotlin outside any database' -Fields $roomContractFields -ExpectedModules @() `
    -ChangedSet @('app_v2/src/main/java/com/sza/fastmediasorter/ui/Foo.kt')
# The positive cases: the fix must not have bought a clean skip by never matching anything.
Assert-RoomRows -Case 'phone database source' -Fields $roomContractFields -ExpectedModules @('app_v2') `
    -ChangedSet @('app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt')
Assert-RoomRows -Case 'phone registration file' -Fields $roomContractFields -ExpectedModules @('app_v2') `
    -ChangedSet @('app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt')
Assert-RoomRows -Case 'wear androidTest' -Fields $roomAndroidTestFields -ExpectedModules @('wear') `
    -ChangedSet @('wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db/WearDbTest.kt')
# Both databases in one set must come back as two DISTINCT rows - the shape the caller relies on to
# name the module it is about to judge (S2355).
Assert-RoomRows -Case 'both databases' -Fields $roomContractFields -ExpectedModules @('app_v2', 'wear') `
    -ChangedSet @(
        'app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/1.json',
        'wear/src/main/java/com/sza/fastmediasorter/wear/data/db/WearVoiceNoteDatabase.kt')
# A database row must be reported ONCE even when several of its paths are in the set.
Assert-RoomRows -Case 'one database, many paths' -Fields $roomContractFields -ExpectedModules @('app_v2') `
    -ChangedSet @(
        'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt',
        'app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt',
        'app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/1.json')

# Both call sites must keep wrapping in @(): the function emits rows one at a time, so an unwrapped
# single match binds a bare row and `.Count` on it reads 1 for a hashtable-like object by accident.
foreach ($roomCallSite in @(
        '\$roomAndroidTestRows = @\(Get-RoomRowsForChangedFiles ',
        '\$runsMigrationContractGates = @\(Get-RoomRowsForChangedFiles ')) {
    if ($facade -notmatch $roomCallSite) {
        throw "A Get-RoomRowsForChangedFiles call site no longer wraps the call in @(): /$roomCallSite/."
    }
}

# S2069: an app_v2-only gate must be decided by WHERE the change is, not only by its ChangeType.
# A wear-only set used to fire the focus-highlight gate, which can read nothing but app_v2, and the
# closure was failed by another session's in-flight app_v2 edit. The trigger expressions are lifted
# out of the facade text rather than restated, so rewording one of them fails here instead of
# quietly widening the gate back to every module.
$triggerHarnessPrelude = @'
param([string[]] $Set, [bool] $IsResourceChange)
$normChangedFiles = @($Set | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
$isResourceChange = $IsResourceChange
function Test-AnyChangedFile([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $true }
    }
    return $false
}
'@

function Get-TriggerHarness {
    param([string] $VariableName)
    # The assignment may wrap onto continuation lines; take everything up to the next comment or
    # top-level assignment.
    $match = [regex]::Match(
        $facade,
        '(?m)^\$' + [regex]::Escape($VariableName) + '\s*=(?<body>(?:.*)(?:\r?\n[ \t]+.*)*)')
    if (-not $match.Success) {
        throw "Trigger '$VariableName' is not assigned at the top level of the facade."
    }
    $expression = '$' + $VariableName + ' =' + $match.Groups['body'].Value
    return [scriptblock]::Create($triggerHarnessPrelude + "`n" + $expression + "`n" + '[bool]$' + $VariableName)
}

function Assert-Trigger {
    param(
        [string] $VariableName,
        [string] $Case,
        [string[]] $ChangedSet,
        [bool] $IsResourceChange = $true,
        [bool] $Expected
    )
    $harness = Get-TriggerHarness -VariableName $VariableName
    $actual = [bool](& $harness $ChangedSet $IsResourceChange)
    if ($actual -ne $Expected) {
        throw "Trigger $VariableName [$Case]: expected $Expected, got $actual."
    }
}

# The nine-file wear set from the S2069 repro: five drawables, a colors.xml, two Kotlin files and a
# test. Not one of them is readable by an app_v2-rooted gate.
$wearOnlySet = @(
    'wear/src/main/res/drawable/ic_a.xml',
    'wear/src/main/res/drawable/ic_b.xml',
    'wear/src/main/res/values/colors.xml',
    'wear/src/main/java/com/sza/fastmediasorter/wear/ui/Screen.kt',
    'wear/src/test/java/com/sza/fastmediasorter/wear/ScreenTest.kt')

Assert-Trigger -VariableName 'runsFocusHighlightGate' -Case 'wear-only resource set' `
    -Expected $false -ChangedSet $wearOnlySet
Assert-Trigger -VariableName 'runsFocusHighlightGate' -Case 'app_v2 layout' `
    -Expected $true -ChangedSet @('app_v2/src/main/res/layout/activity_main.xml')
# The MaterialCardView half of the gate reads every XML under app_v2/src, so a non-layout app_v2
# resource must still fire it - narrowing to layout dirs alone would switch that half off.
Assert-Trigger -VariableName 'runsFocusHighlightGate' -Case 'app_v2 non-layout resource' `
    -Expected $true -ChangedSet @('app_v2/src/main/res/values/colors.xml')
Assert-Trigger -VariableName 'runsFocusHighlightGate' -Case 'mixed wear and app_v2' `
    -Expected $true -ChangedSet ($wearOnlySet + 'app_v2/src/main/res/layout/activity_main.xml')
Assert-Trigger -VariableName 'runsFocusHighlightGate' -Case 'app_v2 layout under a non-resource ChangeType' `
    -Expected $false -IsResourceChange $false -ChangedSet @('app_v2/src/main/res/layout/activity_main.xml')

# Same asymmetry, found by the S2069 sweep and reachable today: the watch module has a manifest.
Assert-Trigger -VariableName 'runsOrientationFeatureGate' -Case 'wear manifest' `
    -Expected $false -ChangedSet @('wear/src/main/AndroidManifest.xml')
Assert-Trigger -VariableName 'runsOrientationFeatureGate' -Case 'app_v2 manifest' `
    -Expected $true -ChangedSet @('app_v2/src/main/AndroidManifest.xml')
Assert-Trigger -VariableName 'runsOrientationFeatureGate' -Case 'app_v2 flavor manifest' `
    -Expected $true -ChangedSet @('app_v2/src/vr/AndroidManifest.xml')
Assert-Trigger -VariableName 'runsOrientationLayoutPairingGate' -Case 'wear manifest' `
    -Expected $false -ChangedSet @('wear/src/main/AndroidManifest.xml')
Assert-Trigger -VariableName 'runsOrientationLayoutPairingGate' -Case 'app_v2 manifest' `
    -Expected $true -ChangedSet @('app_v2/src/main/AndroidManifest.xml')
Assert-Trigger -VariableName 'runsOrientationLayoutPairingGate' -Case 'app_v2 landscape layout' `
    -Expected $true -ChangedSet @('app_v2/src/main/res/layout-land/activity_main.xml')

# A gate has to RECEIVE the changed set, not merely be reached: without -ChangedFiles it re-counts
# the whole tree and -ScopeToFile means nothing to it. S2326 moved the argument vectors out of the
# call sites and into the pool-start blocks, so the assertion follows the variable the call site
# names rather than reading the block - a form the next refactor cannot make pass vacuously.
foreach ($scopedGate in @('focus-highlight-gate', 'neuroslop-gate', 'rtl-layout-attrs-gate', 'listener-symmetry-gate')) {
    $callSitePattern = 'Invoke-Gate "{0}" \{{ Invoke-GateChild @(\w+) \}}' -f [regex]::Escape($scopedGate)
    $callSite = [regex]::Match($facade, $callSitePattern)
    if (-not $callSite.Success) {
        throw "Could not find the $scopedGate call site - the assertion below would pass vacuously."
    }
    $argvName = $callSite.Groups[1].Value
    $argvPattern = '(?s)\$' + [regex]::Escape($argvName) + ' = @\(.*?\r?\n(if \(\$ScopeToFile[^\r\n]*\r?\n)?'
    $argvBlock = [regex]::Match($facade, $argvPattern).Value
    if ($argvBlock -notmatch 'if \(\$ScopeToFile.*-ChangedFiles') {
        throw "$scopedGate does not forward the changed set under -ScopeToFile (argument vector $argvName)."
    }
}

Write-Output "post-change tests: PASS ($($labels.Count) routed labels with hints)"
