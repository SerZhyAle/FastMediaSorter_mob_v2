<#
.SYNOPSIS
    Domain table for the cross-agent coordination locks (S2109).

.DESCRIPTION
    Dot-source this file to get Resolve-AgentLockDomains and the domain table it reads.

    A coordination resource used to be one word - Build or Code - and both were global by
    construction: two sessions competed whenever both were building or both were editing, even
    when their file sets could not overlap. The resource is now a pair, resource type plus
    domain, and this file is the ONE place the domains are listed. Adding a module is a row
    here, not an edit in every entry point (strategic spec S2109 section 5.3).

    Concrete domains:
      Build.Phone   - the app_v2 module and its six flavors
      Build.Wear    - the wear module
      Code.Phone    - app_v2 sources, resources and module build files
      Code.Wear     - wear sources, resources and module build files
      Code.Scripts  - repository tooling, documentation and specs

    The bare names Build and Code stay valid and mean "every domain of that type". That is the
    all-encompassing domain of section 5.1 pillar C: a set that does not decompose takes the
    bare name and is serialised exactly as strongly as before the split.

    Resolution is ordered by the fixed rank declared in the table, never by the order a caller
    supplied. Two sessions taking the same two domains in opposite orders is precisely what
    turns a split into a deadlock (section 5.1 pillar D), so callers do not get to choose.

.EXAMPLE
    . "$PSScriptRoot\agent-lock-domains.ps1"
    Resolve-AgentLockDomains -Name Code      # Code.Phone, Code.Wear, Code.Scripts
    Resolve-AgentLockDomains -Name Code.Wear # Code.Wear

.NOTES
    Exit codes: 0 dot-sourced successfully; 2 invoked as a script instead of being dot-sourced
    (see the direct-invocation guard below - this file exposes no command-line interface).
#>

# Same guard as agent-lock.ps1 (S1505): a library invoked with `pwsh -File` binds nothing,
# defines nothing the caller can see and exits 0, which reads as a working call.
if ($MyInvocation.InvocationName -ne '.') {
    $domainsGuardMessage = @(
        "agent-lock-domains.ps1 is a dot-source library and has no command-line interface.",
        "Running it as a script does nothing at all - it resolves no domain.",
        "",
        "From a script, load the functions instead:",
        "    . `"`$PSScriptRoot\agent-lock-domains.ps1`"",
        "    Resolve-AgentLockDomains -Name Code"
    ) -join [Environment]::NewLine
    Write-Error $domainsGuardMessage -ErrorAction Continue
    exit 2
}

# Canonical acquisition order. Rank is the contract, not the hashtable's enumeration order -
# PowerShell hashtables are unordered, and an accidental reordering here is a deadlock.
$Script:AgentLockDomainTable = @(
    [pscustomobject]@{ Domain = 'Build.Phone'; Type = 'Build'; Rank = 1 }
    [pscustomobject]@{ Domain = 'Build.Wear'; Type = 'Build'; Rank = 2 }
    [pscustomobject]@{ Domain = 'Code.Phone'; Type = 'Code'; Rank = 3 }
    [pscustomobject]@{ Domain = 'Code.Wear'; Type = 'Code'; Rank = 4 }
    [pscustomobject]@{ Domain = 'Code.Scripts'; Type = 'Code'; Rank = 5 }
)

function Get-AgentLockDomainTable {
    <#
    .SYNOPSIS
        The domain table itself, in canonical rank order.
    #>
    return $Script:AgentLockDomainTable | Sort-Object -Property Rank
}

function Get-AgentLockDomainNames {
    <#
    .SYNOPSIS
        Every accepted resource name - the five concrete domains plus the two bare types.
    #>
    return @((Get-AgentLockDomainTable | ForEach-Object { $_.Domain }) + @('Build', 'Code'))
}

function Resolve-AgentLockDomains {
    <#
    .SYNOPSIS
        Turn a resource name into the ordered set of concrete domains it covers.
    .DESCRIPTION
        A concrete domain returns itself. A bare type returns every domain of that type. An
        unknown name throws rather than returning an empty set: strategic spec S2109 section 11
        criterion 6 requires an unknown resource name to end in an explicit error, because a
        quiet empty result is a lock nobody holds and everybody believes in.
    #>
    param([Parameter(Mandatory)][string]$Name)

    $table = Get-AgentLockDomainTable
    $matched = $table | Where-Object { $_.Domain -eq $Name }
    if ($matched) {
        return @($matched.Domain)
    }

    $byType = $table | Where-Object { $_.Type -eq $Name }
    if ($byType) {
        return @($byType | ForEach-Object { $_.Domain })
    }

    $accepted = (Get-AgentLockDomainNames) -join ', '
    throw "Unknown coordination resource name '$Name'. Accepted values: $accepted."
}

function Get-AgentLockLegacyName {
    <#
    .SYNOPSIS
        The pre-split resource name a concrete domain must still honour, or $null.
    .DESCRIPTION
        S2109. Coordination state outlives a session: at the moment this ticket lands, a sibling
        may already hold temp/BUILD.LOCK or temp/CODE.LOCK and have tickets in the matching queue,
        written before any domain existed. Those files name no domain, so the only safe reading is
        the widest one - a legacy lock holds EVERY domain of its type, and a legacy ticket is a
        ticket for the full set. Reading them any narrower would let two sessions build one tree
        while both believed they were alone (strategic section 3.2).

        Returns the bare type for a concrete domain, and $null for a bare name (which already IS
        the legacy name, so honouring it again would be circular).
    #>
    param([Parameter(Mandatory)][string]$Name)

    $entry = Get-AgentLockDomainTable | Where-Object { $_.Domain -eq $Name }
    if ($entry) { return $entry.Type }
    return $null
}

function Sort-AgentLockDomains {
    <#
    .SYNOPSIS
        Validate an explicit list of concrete domains and return it in canonical rank order.
    .DESCRIPTION
        S2109. A derived set is an arbitrary SUBSET - "phone and wear but not scripts" - which no
        single resource name can express, so callers hand the list itself. It still has to be
        ordered here rather than by the caller: canonical order is what keeps two overlapping sets
        from deadlocking, and a caller that got to choose its own order could break that without
        ever touching the lock library.
    #>
    param([Parameter(Mandatory)][string[]]$Domain)

    $unique = @($Domain | Select-Object -Unique)
    foreach ($name in $unique) { [void](Assert-AgentLockDomainName -Name $name) }
    return @(Get-AgentLockDomainTable | Where-Object { $unique -contains $_.Domain } |
        ForEach-Object { $_.Domain })
}

function Resolve-CodeDomainsForPaths {
    <#
    .SYNOPSIS
        The canonical-ordered set of code domains a changed file set belongs to.
    .DESCRIPTION
        S2109 ADR-1: the domain is DERIVED from what the call already carries - here the changed
        paths - rather than declared. A declared domain that is wrong removes protection silently
        and still looks like working coordination; a derived one is wrong only if the path set is
        wrong, and the caller already prints that set.

        ADR-2, the fail-closed half: anything that does not decompose takes the full code set.
        That covers three cases deliberately.
          - A build file, in either module or at the root, and a SHARED static-analysis config.
            Research artifact 02 measured why: the configuration phase processes every subproject,
            so a broken build file in one module fails a check requested for the other. A module's
            own build.gradle.kts is therefore NOT that module's domain.
            The per-module detekt baselines are the exception, carved out below - they are named
            for their module and read by that module's check alone, so failing closed on them
            bought no protection and cost the split on most Kotlin closures.
          - A path the table does not recognise, including a module added later. A new module is
            then over-protected rather than unprotected, which is the safe direction to be wrong.
          - An empty set, which asks about nothing and so cannot be narrowed.

        S2338 adds the one exemption: a PLAN/ path resolves to NO domain, so a PLAN-only set
        returns an empty array and its caller takes no code lock. That is not a hole in ADR-2 -
        it is ADR-2's own test applied one level down. The lock exists to serialise what nothing
        else serialises, and PLAN/ is already exclusive per ticket (ticket-lease.ps1) and per
        journal (Enter-CatalogLock). Callers must therefore handle an EMPTY result, which before
        this ticket was unreachable.

        S2342 narrows the second fail-closed case, and is likewise not a hole in ADR-2. Fail-closed
        exists for a path that MIGHT belong to a module: over-protecting an unknown one is the safe
        direction to be wrong. A store-listing file, a site page or a root licence cannot belong to
        a module in principle - none of them compiles, links or packs into an APK - so the branch
        below names those trees rather than guessing at them, and `return $full` stays untouched for
        everything genuinely unrecognised, corex/, benchmark/ and watchface/ included.
    #>
    param([string[]]$Path)

    $full = @(Get-AgentLockDomainTable | Where-Object { $_.Type -eq 'Code' } | ForEach-Object { $_.Domain })
    if (-not $Path -or @($Path).Count -eq 0) { return $full }

    # Split on commas, exactly as post-change.ps1 does with its own -Files. `pwsh -File` hands a
    # comma list to a [string[]] parameter as ONE element, so "wear/x.kt,app_v2/y.kt" would arrive
    # as a single string, match the first prefix only, and resolve to Code.Wear alone - a set
    # NARROWER than the change, which is the one direction this table must never fail in.
    $expanded = @(
        $Path |
            ForEach-Object { ([string]$_) -split ',' } |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ }
    )
    if ($expanded.Count -eq 0) { return $full }

    $matched = @{}
    # Counts paths that resolve to no domain BY DESIGN (S2338), so an all-exempt set can be told
    # apart from a set that matched nothing because it was empty.
    $exempt = 0
    foreach ($raw in $expanded) {
        if ([string]::IsNullOrWhiteSpace($raw)) { continue }
        # Normalise to repo-relative forward slashes: callers pass a mix of both separators, and a
        # backslash path silently matching nothing is how a set would widen without anyone noticing.
        # Strip a leading './' as a PREFIX, never with TrimStart: that takes a character SET, so it
        # also ate the leading dot of '.claude/..' and '.github/..'. Those then matched no branch
        # below and fell through to the fail-closed full set, which made every command, hook, skill
        # and agent-memory edit take all three code domains - the most common set in this repo.
        $normalised = (($raw -replace '\\', '/').Trim() -replace '^\./')

        # A per-module detekt baseline is that module's own file despite sitting beside the shared
        # detekt config, so it is carved out ABOVE the config/detekt branch below, which must keep
        # winning for detekt.yml and its siblings. The baselines are named for their module
        # (baseline-app_v2*, baseline-wear*) and a check for one module never reads the other's.
        # Widening here serialised every Kotlin closure that regenerated a baseline, which is most
        # of them: observed live, a one-file app_v2 edit plus its baseline took all three domains.
        if ($normalised -match '^config/detekt/baseline-app_v2') { $matched['Code.Phone'] = $true; continue }
        if ($normalised -match '^config/detekt/baseline-wear') { $matched['Code.Wear'] = $true; continue }

        # Build files first - a module's own build file belongs to the full set, not to its module,
        # so this test has to win over the module-prefix tests below.
        if ($normalised -match '^(settings\.gradle\.kts|build\.gradle\.kts|gradle\.properties|gradlew(\.bat)?)$' -or
            $normalised -match '^(gradle|config/detekt|lint-rules)/' -or
            $normalised -match '^(app_v2|wear)/build\.gradle\.kts$') {
            return $full
        }

        if ($normalised -match '^app_v2/') { $matched['Code.Phone'] = $true; continue }
        if ($normalised -match '^wear/') { $matched['Code.Wear'] = $true; continue }

        # S2338: PLAN/ contributes NO domain, and is the one exemption in this table. The test is
        # not "is it source" but "is this path already serialised by a finer mechanism", and every
        # path under PLAN/ is, twice over:
        #   - PLAN/Sxxxx_*.md and PLAN/Sxxxx_<slug>/ belong to exactly one ticket, and a ticket is
        #     held exclusively by scripts/spec_catalog/ticket-lease.ps1 (atomic claim, exit 3 to
        #     the loser), so two sessions cannot reach one spec file at all.
        #   - the journals and both release files are written only through the catalog mutators,
        #     which all hold Enter-CatalogLock (scripts/spec_catalog/_lib.ps1, S1437).
        # docs/ and dev/ are deliberately NOT exempt: they are hand-edited prose with no finer
        # mechanism over them, so a concurrent edit there is an ordinary lost update.
        # Measured 2026-09-02 over the last 397 dev-log rows: 217 (55%) touched PLAN/ and nothing
        # else, so before this branch the majority of closures took a domain that protected
        # nothing while serialising every scripts/, docs/ and .claude/ edit in the repository.
        if ($normalised -match '^PLAN/') { $exempt++; continue }

        if ($normalised -match '^(scripts|dev|docs|\.claude|\.github)/' -or
            $normalised -match '^(CLAUDE|AGENTS|README)\.md$' -or
            $normalised -match '^a\.ps1$') {
            $matched['Code.Scripts'] = $true; continue
        }

        # S2342: content with no code in it joins Code.Scripts rather than failing closed. These
        # trees and root files cannot belong to a module - nothing under them compiles, links or
        # packs into an APK - so the full set bought no protection while serialising phone and watch
        # work against a store-listing edit. Measured 2026-09-02 over the last 400 dev-log rows: the
        # full code set was taken 11 times, 9 of those sets touched content and 8 were content ONLY.
        # They still need a lock, of exactly the shape docs/ has above: hand-edited files with no
        # finer mechanism over them, so a concurrent edit is an ordinary lost update.
        # Read off a full listing of the repository root, not enumerated one directory per finding -
        # that drift is what this branch exists to avoid repeating.
        # Deliberately NOT here: corex/, benchmark/ and watchface/. The last two are real Gradle
        # modules (settings.gradle.kts) with no Build.* domain of their own, so giving them a code
        # domain is a boundary decision, not a content one, and they keep failing closed until it.
        if ($normalised -match '^(play|fastlane|store_assets|delivery|maestro)/' -or
            $normalised -match '^(index|nolegal)[^/]*\.html$' -or
            $normalised -match '^(styles\.css|sitemap\.xml|robots\.txt|_config\.yml|_typos\.toml)$' -or
            $normalised -match '^(GEMINI\.md|LICENSE|THIRD_PARTY_LICENSES\.md)$' -or
            $normalised -match '^(favicon[^/]*|icon\.png|apple-touch-icon\.png)$') {
            $matched['Code.Scripts'] = $true; continue
        }

        # Unrecognised - fail closed rather than guess which module it belongs to.
        return $full
    }

    # An all-exempt set needs no code domain, and that is NOT the same answer as the empty input
    # handled at the top, which still fails closed to the full set. Collapsing the two would send
    # every PLAN-only closure back to taking all three domains - the defect this branch removes.
    if ($matched.Count -eq 0 -and $exempt -gt 0) { return @() }
    if ($matched.Count -eq 0) { return $full }
    return @(Get-AgentLockDomainTable |
        Where-Object { $_.Type -eq 'Code' -and $matched.ContainsKey($_.Domain) } |
        ForEach-Object { $_.Domain })
}

function Test-AgentLockDomainName {
    <#
    .SYNOPSIS
        True when the name is an accepted concrete domain or bare type.
    #>
    param([Parameter(Mandatory)][string]$Name)
    return (Get-AgentLockDomainNames) -contains $Name
}

function Assert-AgentLockDomainName {
    <#
    .SYNOPSIS
        Throw unless the name is accepted. Replaces the hardcoded ValidateSet attributes that
        each entry point used to carry its own copy of.
    #>
    param([Parameter(Mandatory)][string]$Name)
    if (-not (Test-AgentLockDomainName -Name $Name)) {
        $accepted = (Get-AgentLockDomainNames) -join ', '
        throw "Unknown coordination resource name '$Name'. Accepted values: $accepted."
    }
    return $Name
}
