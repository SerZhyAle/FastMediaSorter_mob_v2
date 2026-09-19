#requires -Version 7.0
# S3150: the changed-set half of scripts/post-change.ps1 - the normalized path set, the predicates
# every applicability test is keyed on, the Room database rows the set touches, and the flavors a
# resource change has to link. Extracted for the reason its sibling library was (CLAUDE.md Rule 2);
# it reads the changed set and the shared registries, never a gate.
#
# Dot-sourced into the facade`s scope: $changedFiles and $root come from the caller, and the
# registries below stay dot-sourced here so their consumers keep resolving in one scope.

# S1372: every path-keyed gate used to test the FIRST path the caller named, so a
# multi-file close silently skipped any gate whose trigger file was not first, while still printing
# a clean PASS. Two confirmed occurrences: S1363 (script-cheatsheet-sync skipped over a new .ps1)
# and S1370 (all-features skipped over docs/ALL_FEATURES.jsonl, in the same run where
# document-registry DID see it). Applicability is a property of the changed SET, not of one member.
$normChangedFiles = @($changedFiles | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
function Test-AnyChangedFile([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $true }
    }
    return $false
}
function Get-FirstChangedFileMatch([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $candidate }
    }
    return $null
}

# S1915: the flavors each module declares, spelled exactly as check-standard-fast.ps1 accepts them.
# S2090: wear grew its own two-flavor dimension, so the candidate list is per module.
# S2121: the list itself moved to scripts/utils/gradle-modules.ps1 - it was declared here AND in
# check-standard-fast.ps1, and the copies had already started to drift.
. (Join-Path $root 'scripts/utils/gradle-modules.ps1')

# S2355: every Room database in the repository, one row each. The migration predicates below are
# built from this rather than from literal path fragments, so a third database is a row in
# scripts/quality/lib/room-databases.ps1 and not another edit here.
. (Join-Path $root 'scripts/quality/lib/room-databases.ps1')
$roomDatabaseRows = @(Get-RoomDatabaseRegistry -RepoRoot $root)

# S2604: the settings-doc gate's five stages each read a different input, and the trigger below
# used to test a bare 'docs/settings/' prefix - so device-profile-nonpresettable.json, which feeds
# no stage of this gate at all, ran four checks that cannot see it and then failed the close on the
# project-wide reference stage. The per-stage input map lives beside the gate so the trigger here
# and the gate's own delta predicates cannot drift apart (S1621).
. (Join-Path $root 'scripts/quality/lib/settings-doc-inputs.ps1')

function Test-PathUnderDir([string]$Candidate, [string]$Directory) {
    if ([string]::IsNullOrWhiteSpace($Directory)) { return $false }
    $prefix = ($Directory -replace '\\', '/').TrimEnd('/')
    return $Candidate -eq $prefix -or $Candidate.StartsWith("$prefix/")
}

function Get-RoomRowsForChangedFiles([string[]]$Fields) {
    # Which registered databases the changed set actually touches. Returns rows, not a boolean, so
    # the caller can name the module it is about to judge - the whole point of S2355 is that a
    # verdict identifies the database it read.
    $matched = [System.Collections.Generic.List[object]]::new()
    foreach ($row in $roomDatabaseRows) {
        foreach ($candidate in $normChangedFiles) {
            $hit = $false
            foreach ($field in $Fields) {
                $value = $row.RelativePaths.$field
                if ($field -eq 'RegistrationFile') {
                    if ($candidate -eq ($value -replace '\\', '/')) { $hit = $true }
                }
                elseif (Test-PathUnderDir $candidate $value) { $hit = $true }
                if ($hit) { break }
            }
            if ($hit) { $matched.Add($row); break }
        }
    }
    # Emit the rows one at a time and let every CALL SITE wrap the call in @(). Returning the
    # collection instead is what broke this on its first real run: `return ,$matched.ToArray()`
    # survives an unwrapped `.Count`, but `@(..)` around it yields a one-element array whose single
    # element is the EMPTY array - so a changed set matching no database reported Count 1, the
    # androidTest gate ran anyway, and `$_.Module` failed on an Object[]. Emitting keeps both forms
    # honest: no match is 0 rows, one match is 1 row.
    foreach ($row in $matched) { $row }
}

function Get-ResourceLinkFlavors([string]$TargetModule) {
    # Which variants have to link before the changed set counts as proven. A resource under
    # src/<flavor>/res is compiled into that flavor alone, so linking it as `standard` renders a
    # verdict about a variant that never sees the file - the same false green S1807 found when a
    # phone target was quoted as proof under a wear change.
    #
    # S2090: wear used to be answered here with a flat @('Standard'), before the source sets were read
    # at all, because it had one variant and the builder refused any -Flavor. Both premises are gone,
    # and leaving the shortcut would close a wear/src/noLegal/res change green without ever linking it -
    # exactly the false green this gate exists to prevent (S1881).
    $candidates = @(Get-GradleModuleFlavors -Name $TargetModule)
    # S2121: an empty answer is a real one - watchface declares no flavor dimension, and its task name
    # carries no variant segment at all. The caller invokes the builder without -Flavor for it.
    if ($candidates.Count -eq 0) { return @() }

    # S2121: only THIS module's paths may select a flavor. Before, every path in the changed set was
    # scanned for every module, so a set spanning two modules offered each of them the other's source
    # sets - harmless while the gate only ever ran for one declared module, wrong the moment it runs
    # for the set it derived.
    $modulePrefix = (Get-GradleModule -Name $TargetModule).PathPrefix

    # src/main, src/debug and every other non-flavor source set ship inside the default variant,
    # so it is always linked; the loop only ever ADDS flavors on top of it.
    $selected = [System.Collections.Generic.List[string]]::new()
    $selected.Add($candidates[0])

    foreach ($candidate in $normChangedFiles) {
        if ($candidate -notlike "$modulePrefix*") { continue }
        if ($candidate -match '(^|/)src/([^/]+)/') {
            $sourceSet = $Matches[2]
            foreach ($flavor in $candidates) {
                # -eq on strings is case-insensitive here, which is what lets `vr` select `Vr`.
                if ($sourceSet -eq $flavor -and -not $selected.Contains($flavor)) { $selected.Add($flavor) }
            }
        }
    }
    return $selected.ToArray()
}

$docIconRoutingFile = Join-Path $root 'scripts/quality/lib/doc-icon-gate-routing.ps1'
. $docIconRoutingFile
