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

$selectorHarness = [scriptblock]::Create(@"
param([string[]] `$normChangedFiles, [string] `$TargetModule)
`$script:ResourceLinkFlavors = @('Standard', 'NoLegal', 'Lite', 'Photos', 'Legacy', 'Vr')
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
# The watch module declares no product flavors and check-standard-fast.ps1 exits 2 on any other
# -Flavor, so a flavor-shaped path in the set must not leak into a wear invocation.
Assert-FlavorSelection -Case 'wear module' -TargetModule 'wear' -Expected @('Standard') `
    -ChangedSet @('wear/src/main/res/values/strings.xml', 'app_v2/src/vr/res/values/strings.xml')

Write-Output "post-change tests: PASS ($($labels.Count) routed labels with hints)"
