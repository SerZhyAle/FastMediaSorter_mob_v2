#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the Gradle module registry (S2121).

.DESCRIPTION
    The coverage test reads settings.gradle.kts rather than a list written here. A table that has
    drifted from the build's own module set reproduces the original defect exactly - a resource
    link check rendering a verdict about a module the change never touched - so the drift has to
    fail here, not at the next closure of a module nobody added a row for.

.OUTPUTS
    Exit 0 - every assertion held.
    Exit 1 - an assertion failed; the message names which.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/utils/gradle-modules.ps1')

# 1. Coverage: every module the build declares has a row, and every row names a real directory.
$settings = Get-Content -LiteralPath (Join-Path $repoRoot 'settings.gradle.kts') -Raw
$declared = @([regex]::Matches($settings, 'include\("\:([^"]+)"\)') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
if ($declared.Count -eq 0) {
    throw 'Parsed no include(..) from settings.gradle.kts - the coverage assertion would pass vacuously.'
}
$known = @(Get-GradleModuleNames)
$missing = @($declared | Where-Object { $_ -notin $known })
if ($missing.Count -gt 0) {
    throw "settings.gradle.kts declares module(s) with no registry row: $($missing -join ', ')."
}
$extra = @($known | Where-Object { $_ -notin $declared })
if ($extra.Count -gt 0) {
    throw "Registry names module(s) the build does not declare: $($extra -join ', ')."
}
foreach ($row in Get-GradleModuleTable) {
    if ($row.PathPrefix -notmatch '/$') {
        throw "Module '$($row.Module)' has a path prefix without a trailing slash: '$($row.PathPrefix)'."
    }
    if (-not (Test-Path (Join-Path $repoRoot $row.PathPrefix))) {
        throw "Module '$($row.Module)' names a directory that does not exist: '$($row.PathPrefix)'."
    }
}

# 2. Flavors. Empty is a real answer - it is what makes a task name drop its variant segment.
function Assert-Flavors {
    param([string]$Name, [string[]]$Expected)
    $actual = @(Get-GradleModuleFlavors -Name $Name)
    if (($actual -join ',') -ne ($Expected -join ',')) {
        throw "Flavors [$Name]: expected '$($Expected -join ',')', got '$($actual -join ',')'."
    }
}
Assert-Flavors -Name 'app_v2' -Expected @('Standard', 'NoLegal', 'Lite', 'Photos', 'Legacy', 'Vr', 'Foss')
Assert-Flavors -Name 'wear' -Expected @('Standard', 'NoLegal')
Assert-Flavors -Name 'watchface' -Expected @()
Assert-Flavors -Name 'benchmark' -Expected @()

# 2b. Build types (S2123) - the other half of the variant name, and the half that stayed hardcoded
# when S2121 moved flavors here. ORDER IS LOAD-BEARING: the first entry is what a call binding no
# -BuildType resolves to, so a reorder silently changes which variant every default check runs.
function Assert-BuildTypes {
    param([string]$Name, [string[]]$Expected)
    $actual = @(Get-GradleModuleBuildTypes -Name $Name)
    if (($actual -join ',') -ne ($Expected -join ',')) {
        throw "BuildTypes [$Name]: expected '$($Expected -join ',')', got '$($actual -join ',')'."
    }
}
Assert-BuildTypes -Name 'app_v2' -Expected @('Debug', 'Release')
Assert-BuildTypes -Name 'wear' -Expected @('Debug', 'Release')
Assert-BuildTypes -Name 'watchface' -Expected @('Debug', 'Release')
# No debug variant exists here at all - androidx.baselineprofile declares these two and nothing else.
Assert-BuildTypes -Name 'benchmark' -Expected @('NonMinifiedRelease', 'BenchmarkRelease')
Assert-BuildTypes -Name 'lint-rules' -Expected @()

foreach ($pair in @(
        @{ Module = 'app_v2'; Default = 'Debug' },
        @{ Module = 'wear'; Default = 'Debug' },
        @{ Module = 'watchface'; Default = 'Debug' },
        @{ Module = 'benchmark'; Default = 'NonMinifiedRelease' })) {
    $actualDefault = Get-GradleModuleDefaultBuildType -Name $pair.Module
    if ($actualDefault -ne $pair.Default) {
        throw "Default build type [$($pair.Module)]: expected '$($pair.Default)', got '$actualDefault'."
    }
}

$unknownBuildTypesThrew = $false
try { $null = Get-GradleModuleBuildTypes -Name 'nosuch' } catch { $unknownBuildTypesThrew = $true }
if (-not $unknownBuildTypesThrew) {
    throw 'Get-GradleModuleBuildTypes answered for an unknown module instead of throwing.'
}

# A module with no build types must refuse a default rather than invent Debug - naming a task gradle
# has never had is the failure this whole field exists to remove.
$emptyDefaultThrew = $false
try { $null = Get-GradleModuleDefaultBuildType -Name 'lint-rules' } catch { $emptyDefaultThrew = $true }
if (-not $emptyDefaultThrew) {
    throw 'Get-GradleModuleDefaultBuildType invented a default for a module that declares no build types.'
}

# LinksResources is measured, not assumed. Only lint-rules has no resource-processing task at all -
# it carries no Android plugin. benchmark was recorded false here until S2123 measured the module
# instead of one guessed task name: :benchmark:processNonMinifiedReleaseResources exits 0 in 2.4 s,
# and processDebugResources was absent because the module has no debug build type, not because it
# has nothing to link.
foreach ($pair in @(
        @{ Module = 'app_v2'; Links = $true },
        @{ Module = 'wear'; Links = $true },
        @{ Module = 'watchface'; Links = $true },
        @{ Module = 'benchmark'; Links = $true },
        @{ Module = 'lint-rules'; Links = $false })) {
    $row = Get-GradleModule -Name $pair.Module
    if ($row.LinksResources -ne $pair.Links) {
        throw "LinksResources [$($pair.Module)]: expected $($pair.Links), got $($row.LinksResources)."
    }
}

$unknownFlavorsThrew = $false
try { $null = Get-GradleModuleFlavors -Name 'nosuch' } catch { $unknownFlavorsThrew = $true }
if (-not $unknownFlavorsThrew) {
    throw 'Get-GradleModuleFlavors answered for an unknown module instead of throwing.'
}
if (Test-GradleModuleName -Name 'nosuch') {
    throw 'Test-GradleModuleName accepted a module the table does not carry.'
}

# 3. Build domains. A module with no domain of its own must widen to the full set (S2109 ADR-2),
# never fall back to the phone's - falling back is how a watchface build would serialise against
# nothing while a sibling builds the same tree.
function Assert-Domains {
    param([string]$Name, [string[]]$Expected)
    $actual = @(Get-GradleModuleBuildDomains -Name $Name)
    if (($actual -join ',') -ne ($Expected -join ',')) {
        throw "Build domains [$Name]: expected '$($Expected -join ',')', got '$($actual -join ',')'."
    }
}
Assert-Domains -Name 'app_v2' -Expected @('Build.Phone')
Assert-Domains -Name 'wear' -Expected @('Build.Wear')
Assert-Domains -Name 'watchface' -Expected @('Build.Phone', 'Build.Wear')
Assert-Domains -Name 'lint-rules' -Expected @('Build.Phone', 'Build.Wear')

# 4. Path resolution.
function Assert-Resolution {
    param([string]$Case, [string[]]$Path, [string[]]$Modules, [string[]]$Unresolved)
    $actual = Resolve-GradleModulesForPaths -Path $Path
    if ((@($actual.Modules) -join ',') -ne ($Modules -join ',')) {
        throw "Resolution [$Case]: expected modules '$($Modules -join ',')', got '$(@($actual.Modules) -join ',')'."
    }
    if ((@($actual.Unresolved) -join ',') -ne ($Unresolved -join ',')) {
        throw "Resolution [$Case]: expected unresolved '$($Unresolved -join ',')', got '$(@($actual.Unresolved) -join ',')'."
    }
}
Assert-Resolution -Case 'watchface resource' -Modules @('watchface') -Unresolved @() `
    -Path @('watchface/src/main/res/values/colors.xml')
# The set that produced the original false green: nine watchface files plus a root build file.
Assert-Resolution -Case 'S1677 shape' -Modules @('watchface') -Unresolved @('settings.gradle.kts') `
    -Path @('watchface/src/main/res/raw/watchface.xml', 'settings.gradle.kts')
# Table order, not input order - two sessions must link a spanning set in one fixed sequence.
Assert-Resolution -Case 'two modules, reversed input' -Modules @('app_v2', 'wear') -Unresolved @() `
    -Path @('wear/src/main/res/values/strings.xml', 'app_v2/src/main/res/layout/activity_main.xml')
Assert-Resolution -Case 'backslashes and dot prefix' -Modules @('app_v2') -Unresolved @() `
    -Path @('.\app_v2\src\main\res\values\colors.xml')
# A module directory the table does not know must surface, not be swallowed into a default.
Assert-Resolution -Case 'unknown module directory' -Modules @() -Unresolved @('newmod/src/main/res/values/x.xml') `
    -Path @('newmod/src/main/res/values/x.xml')
# `pwsh -File` hands a comma list over as one string; a resolver that does not re-split answers
# with a set narrower than the change.
Assert-Resolution -Case 'comma-joined single argument' -Modules @('app_v2', 'watchface') -Unresolved @() `
    -Path @('app_v2/src/main/res/values/colors.xml,watchface/src/main/res/values/colors.xml')

# 5. The direct-invocation guard: a library run as a script must refuse, not exit 0 defining nothing.
$guardOutput = & pwsh -NoProfile -File (Join-Path $repoRoot 'scripts/utils/gradle-modules.ps1') 2>&1 | Out-String
if ($LASTEXITCODE -ne 2 -or $guardOutput -notmatch 'dot-source library') {
    throw "Direct invocation did not refuse with exit 2 (got $LASTEXITCODE)."
}

Write-Output "gradle-modules tests: PASS ($($known.Count) modules, $($declared.Count) declared)"
exit 0
