<#
.SYNOPSIS
    Registry of the Gradle modules declared in settings.gradle.kts (S2121).

.DESCRIPTION
    Dot-source this file to get the module table and the resolvers that read it.

    One table, read by everything that has to answer "which module is this change in, and what are
    its variants". Before S2121 the same knowledge sat in three places - a hashtable in
    post-change.ps1, a second copy in check-standard-fast.ps1, and a ValidateSet on the latter's
    -Module parameter - and the resource-link gate answered the question from a DECLARED -Module
    that defaulted to app_v2. A ten-file change entirely under watchface/ therefore linked
    :app_v2:processStandardDebugResources and printed PASS: a verdict about a module the change
    never touched. S2109 ADR-1 states the shape this fixes - a domain is derived from what the call
    already carries, because a declared one that is wrong removes protection silently while still
    looking like a working check.

    Adding a module to settings.gradle.kts means adding a row here. Nothing guesses a task name for
    a module the table does not know: Resolve-GradleModulesForPaths reports the path as unresolved
    and the caller refuses, because the alternative is a green verdict about something nobody chose.

    Row fields:
      Module         - the Gradle project name, exactly as settings.gradle.kts includes it.
      PathPrefix     - repo-relative directory the module's files live under, trailing slash.
      Flavors        - declared product flavors, capitalised as a Gradle task-name segment. EMPTY
                       for a module with no flavor dimension, whose task names carry no variant
                       segment at all (:watchface:processDebugResources).
      BuildTypes     - declared build types, capitalised the same way. The FIRST entry is the
                       module's default: it is what a call that binds no -BuildType resolves to.
                       Debug, Release for the three ordinary Android modules; a module whose build
                       types come from a plugin declares those instead (S2123 - the baseline-profile
                       plugin gives :benchmark nonMinifiedRelease and benchmarkRelease and no debug
                       at all). EMPTY for a module with no Android plugin, which declares none.
      BuildDomain    - the S2109 build-lock domain, or $null for a module that has none. $null means
                       "take every build domain", which is ADR-2's safe direction: over-protected
                       rather than silently unprotected.
      LinksResources - whether the module has Android resources a link check can process. False for
                       a module with no Android plugin at all.

.EXAMPLE
    . "$PSScriptRoot\gradle-modules.ps1"
    Get-GradleModuleFlavors -Name wear                                  # Standard, NoLegal
    (Resolve-GradleModulesForPaths -Path 'watchface/src/main/res/values/colors.xml').Modules

.NOTES
    Exit codes: 0 dot-sourced successfully; 2 invoked as a script instead of being dot-sourced
    (see the direct-invocation guard below - this file exposes no command-line interface).
#>

# Same guard as agent-lock-domains.ps1 (S1505): a library invoked with `pwsh -File` binds nothing,
# defines nothing the caller can see and exits 0, which reads as a working call.
if ($MyInvocation.InvocationName -ne '.') {
    $modulesGuardMessage = @(
        "gradle-modules.ps1 is a dot-source library and has no command-line interface.",
        "Running it as a script does nothing at all - it resolves no module.",
        "",
        "From a script, load the functions instead:",
        "    . `"`$PSScriptRoot\gradle-modules.ps1`"",
        "    Get-GradleModuleFlavors -Name wear"
    ) -join [Environment]::NewLine
    Write-Error $modulesGuardMessage -ErrorAction Continue
    exit 2
}

# Declaration order is the order every consumer reports and acquires in, so a set spanning two
# modules is always linked in the same sequence regardless of how the caller listed its paths.
$Script:GradleModuleTable = @(
    [pscustomobject]@{
        Module         = 'app_v2'
        PathPrefix     = 'app_v2/'
        # S0403 added Foss (F-Droid). It is last because the first entry is the module default, and
        # a build that names no flavor still means Standard.
        Flavors        = @('Standard', 'NoLegal', 'Lite', 'Photos', 'Legacy', 'Vr', 'Foss')
        BuildTypes     = @('Debug', 'Release')
        BuildDomain    = 'Build.Phone'
        LinksResources = $true
    }
    [pscustomobject]@{
        Module         = 'wear'
        PathPrefix     = 'wear/'
        # S2090: the watch grew its own two-flavor `version` dimension. It is a strict subset of the
        # phone's, so the plausible mistake is asking the watch for a phone-only flavor.
        Flavors        = @('Standard', 'NoLegal')
        BuildTypes     = @('Debug', 'Release')
        BuildDomain    = 'Build.Wear'
        LinksResources = $true
    }
    [pscustomobject]@{
        Module         = 'watchface'
        PathPrefix     = 'watchface/'
        # S1677: Watch Face Format package - com.android.application with enableKotlin = false and no
        # flavor dimension. Its resource task is :watchface:processDebugResources, with no variant
        # segment; a builder that always interpolates one cannot name it.
        Flavors        = @()
        BuildTypes     = @('Debug', 'Release')
        # No lock domain of its own. $null resolves to the full build set (ADR-2).
        BuildDomain    = $null
        LinksResources = $true
    }
    [pscustomobject]@{
        Module         = 'benchmark'
        PathPrefix     = 'benchmark/'
        # com.android.test, self-instrumenting against :app_v2. No flavor dimension of its own.
        Flavors        = @()
        # S2123: the module has NO debug build type at all. androidx.baselineprofile declares it
        # exactly two variants, and since it carries no flavor dimension the whole task-name
        # segment is the build type: :benchmark:processNonMinifiedReleaseResources. This is why
        # S2121 measured ":benchmark:processDebugResources does not exist" and drew the wrong
        # conclusion from it - the task name was unbuildable, not the module unlinkable.
        BuildTypes     = @('NonMinifiedRelease', 'BenchmarkRelease')
        BuildDomain    = $null
        # Measured 2026-08-27: :benchmark:processNonMinifiedReleaseResources exits 0 in 2.4 s
        # (10 actionable tasks) and :benchmark:processNonMinifiedReleaseManifest in 1.4 s, neither
        # needing :app_v2 to build. The module has no res/ directory, but the manifest AGP merges
        # into the test APK is processed by a real task, so there IS a link step to run.
        LinksResources = $true
    }
    [pscustomobject]@{
        Module         = 'lint-rules'
        PathPrefix     = 'lint-rules/'
        # Pure kotlin("jvm"). No Android plugin, so no resource processing exists to run - a link
        # check here would have no task to call, which is a different answer from "not checked yet".
        Flavors        = @()
        # No Android plugin means no build type dimension either, so no task name can be built for
        # this module at all. Empty is the honest answer, and the resolver below refuses rather
        # than inventing a default (S2123).
        BuildTypes     = @()
        BuildDomain    = $null
        LinksResources = $false
    }
)

function Get-GradleModuleTable {
    <#
    .SYNOPSIS
        The module table itself, in declaration order.
    #>
    return $Script:GradleModuleTable
}

function Get-GradleModuleNames {
    <#
    .SYNOPSIS
        Every module name the table knows.
    #>
    return @(Get-GradleModuleTable | ForEach-Object { $_.Module })
}

function Get-GradleModule {
    <#
    .SYNOPSIS
        One module row by name, or $null when the table does not know it.
    .DESCRIPTION
        Returns $null rather than throwing so a caller can phrase its own refusal - the message a
        build helper owes its reader is not the message a closure facade owes its reader.
    #>
    param([Parameter(Mandatory)][string]$Name)
    return (Get-GradleModuleTable | Where-Object { $_.Module -eq $Name } | Select-Object -First 1)
}

function Test-GradleModuleName {
    <#
    .SYNOPSIS
        True when the table knows this module name.
    #>
    param([Parameter(Mandatory)][string]$Name)
    return $null -ne (Get-GradleModule -Name $Name)
}

function Get-GradleModuleFlavors {
    <#
    .SYNOPSIS
        The module's declared product flavors - an EMPTY array for a module with no flavor dimension.
    .DESCRIPTION
        Empty is a real answer, not a missing one: it is what tells a task-name builder to omit the
        variant segment entirely. An unknown module throws, because silently answering "no flavors"
        would produce a task name for a project that does not exist and blame gradle for it.
    #>
    param([Parameter(Mandatory)][string]$Name)
    $row = Get-GradleModule -Name $Name
    if (-not $row) {
        throw "Unknown Gradle module '$Name'. Known modules: $((Get-GradleModuleNames) -join ', ')."
    }
    return @($row.Flavors)
}

function Get-GradleModuleBuildTypes {
    <#
    .SYNOPSIS
        The module's declared build types - an EMPTY array for a module with no Android plugin.
    .DESCRIPTION
        The mirror of Get-GradleModuleFlavors, and it exists for the same reason (S2123): the build
        type was the half of the variant name that stayed hardcoded when S2121 moved the flavor half
        into this table. A ValidateSet of Debug and Release is a claim about every module, and it is
        false for :benchmark, which declares neither.

        An unknown module throws rather than answering an empty list, because "this module declares
        no build types" and "this table has never heard of this module" lead to different fixes.
    #>
    param([Parameter(Mandatory)][string]$Name)
    $row = Get-GradleModule -Name $Name
    if (-not $row) {
        throw "Unknown Gradle module '$Name'. Known modules: $((Get-GradleModuleNames) -join ', ')."
    }
    return @($row.BuildTypes)
}

function Get-GradleModuleDefaultBuildType {
    <#
    .SYNOPSIS
        The build type a call that binds no -BuildType resolves to - the row's FIRST declared one.
    .DESCRIPTION
        Declaration order carries the meaning, so a caller never has to name Debug to get the
        ordinary case. A module that declares no build type gets a refusal naming that fact: there
        is no task name to build for it, and returning Debug would name a task gradle has never had.
    #>
    param([Parameter(Mandatory)][string]$Name)
    $types = @(Get-GradleModuleBuildTypes -Name $Name)
    if ($types.Count -eq 0) {
        throw "Gradle module '$Name' declares no build types - it has no Android plugin, so no variant task name exists for it."
    }
    return $types[0]
}

function Get-GradleModuleBuildDomains {
    <#
    .SYNOPSIS
        The build-lock domains a check of this module must hold, in the domain table's own order.
    .DESCRIPTION
        A module with no domain of its own returns EVERY build domain. S2109 ADR-2: a set that does
        not decompose takes the full set, because being over-protected is the safe direction to be
        wrong - the alternative is a watchface build serialising against nothing while a sibling
        builds the same tree.
    #>
    param([Parameter(Mandatory)][string]$Name)
    $row = Get-GradleModule -Name $Name
    if (-not $row) {
        throw "Unknown Gradle module '$Name'. Known modules: $((Get-GradleModuleNames) -join ', ')."
    }
    . "$PSScriptRoot\agent-lock-domains.ps1"
    if (-not $row.BuildDomain) { return @(Resolve-AgentLockDomains -Name 'Build') }
    return @(Resolve-AgentLockDomains -Name $row.BuildDomain)
}

function Resolve-GradleModulesForPaths {
    <#
    .SYNOPSIS
        Which modules a set of repo-relative paths belongs to, plus the paths that belong to none.
    .DESCRIPTION
        S2109 ADR-1 applied to the module: derived from the paths the call already carries rather
        than declared alongside them. Returns both halves on purpose - a caller that only read
        .Modules would treat an unrecognised path as "no module here" and carry on, which is the
        exact shape of the failure this ticket exists to remove.

        Callers pre-filter to the paths their question is about. A root build file or a script
        belongs to no module and is reported as unresolved; whether that matters is the caller's
        judgement, not this table's, because a resource-link check has no business refusing a
        closure over settings.gradle.kts.
    .OUTPUTS
        [pscustomobject] with .Modules (names, table order) and .Unresolved (paths, input order).
    #>
    param([string[]]$Path)

    # `pwsh -File` binds a comma list to a [string[]] parameter as ONE element, so the set is
    # re-split here. Without it "a/x.xml,b/y.xml" matches one prefix at most and the answer is
    # NARROWER than the change - the one direction this resolver must never be wrong in.
    $expanded = @(
        $Path |
            ForEach-Object { ([string]$_) -split ',' } |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ }
    )

    $matched = @{}
    $unresolved = [System.Collections.Generic.List[string]]::new()
    foreach ($raw in $expanded) {
        $normalised = ($raw -replace '\\', '/').Trim() -replace '^\./', ''
        $row = Get-GradleModuleTable |
            Where-Object { $normalised -like "$($_.PathPrefix)*" } |
            Select-Object -First 1
        if ($row) { $matched[$row.Module] = $true } else { $unresolved.Add($raw) }
    }

    return [pscustomobject]@{
        Modules    = @(Get-GradleModuleTable |
                Where-Object { $matched.ContainsKey($_.Module) } |
                ForEach-Object { $_.Module })
        Unresolved = $unresolved.ToArray()
    }
}
