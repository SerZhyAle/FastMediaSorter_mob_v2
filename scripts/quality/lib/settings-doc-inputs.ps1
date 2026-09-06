#requires -Version 7.0
<#
.SYNOPSIS
    S2604 - which changed paths feed the settings-doc composite gate, and which of its stages.

.DESCRIPTION
    Dot-sourced library. Declares no exit code and mutates nothing; the caller decides
    what to do with each verdict.

    scripts/quality/assert-settings-doc-sync.ps1 runs five stages, and each reads a
    different input. The closure facade's trigger and the gate's own per-stage delta
    predicates therefore answer the same question - "does this changed set feed this
    check" - and used to answer it from two independent regexes in two files. That is
    the S1621 defect class: a refusal at one site and a skip at the other, disagreeing
    with nobody to notice. Both now read this file.

    What each file in docs/settings/ actually feeds, measured 2026-09-05:
      settings-manifest.json          stages 3 (annotations), 4 (reference), 5 (HOW_TO)
      settings-annotations.json       stages 3, 4
      settings-scope-exclusions.json  stage 1 (assert-settings-catalog-complete.ps1)
      howto-path-vocab.json           stage 5 (assert-howto-settings-paths.ps1)
      device-profile-nonpresettable.json  NONE - it is read by
                                      scripts/check_device_profile_presets.ps1 and
                                      scripts/quality/assert-device-profile-matrix.ps1,
                                      each of which has its own gate.
    The last row is why a path prefix of 'docs/settings/' is the wrong trigger: it
    charged a device-profile ticket for four stages that cannot see its file, and the
    project-wide reference stage then failed it on a sibling's unrendered row.

    An empty or absent changed set answers $true everywhere. That is what keeps an
    unscoped run - release, CI, a bare `.\a.ps1 fg` - judging the whole project, and it
    must stay that way: the delta paths exist only to stop a SCOPED closure from being
    charged for drift it could not have produced.
#>

Set-StrictMode -Version Latest

# Comma-joined entries reach these predicates from post-change.ps1's -ChangedFiles, and
# separators arrive both ways on Windows. Normalise once so every predicate below matches
# against the same spelling.
function Expand-SettingsDocPaths {
    param([string[]] $ChangedFiles)

    $out = @()
    foreach ($entry in ($ChangedFiles | Where-Object { $_ })) {
        $out += ($entry -split ',') |
            ForEach-Object { $_.Trim().Replace('\', '/') -replace '^\./', '' } |
            Where-Object { $_ }
    }
    return $out
}

# The manifest is produced by scanning the settings LAYOUTS through
# LayoutSettingsSearchSource + SettingsSearchTabMapping and resolving titles from
# values*/strings*.xml, so only those inputs can move it (S1338 step 04.7).
$script:SettingsRowTagPattern = '<[\w.]*\b(?:SettingsToggleRow|SettingsDropdownRow|SettingsInputRow|SettingsSelectionRow)\b'
$script:SettingsLayoutPathPattern = '(^|/)app_v2/src/[^/]+/res/layout[^/]*/'
$script:SettingsManifestInputPatterns = @(
    '(^|/)app_v2/src/[^/]+/res/values[^/]*/strings',
    '(^|/)app_v2/src/[^/]+/java/com/sza/fastmediasorter/ui/settings/',
    '(^|/)app_v2/src/[^/]+/java/com/sza/fastmediasorter/di/[^/]*SettingsSearch',
    '(^|/)wear/src/[^/]+/res/values[^/]*/strings',
    '(^|/)wear/src/[^/]+/java/com/sza/fastmediasorter/(wear/)?ui/settings/'
)

# scripts/docs/render-settings-reference.ps1 merges the manifest with the annotations and
# the per-flavor availability modules, decorating from the doc-icon map. Every one of those
# is an input, and so is the renderer itself and the published files it overwrites.
$script:SettingsReferenceInputPatterns = @(
    '(^|/)docs/settings/settings-manifest\.json$',
    '(^|/)docs/settings/settings-annotations\.json$',
    '(^|/)app_v2/src/[^/]+/java/com/sza/fastmediasorter/di/[^/]*SettingsSearchAvailabilityModule\.kt$',
    '(^|/)docs/icons/doc-icon-map\.json$',
    '(^|/)scripts/docs/render-settings-reference\.ps1$',
    '(^|/)docs/SETTINGS_REFERENCE[A-Za-z_]*\.md$'
)

# The docs/ artifacts that feed at least one stage. This is the closure facade's trigger and
# nothing else: it replaces a bare 'docs/settings/' path prefix plus a 'docs/SETTINGS_REFERENCE'
# one, and the only path it drops is device-profile-nonpresettable.json. Source-tree triggers
# (settings layouts, ui/settings, the availability modules, wear strings) stay spelled out in
# post-change.ps1 - folding the manifest-scan patterns in here would fire this gate on every
# app_v2 strings.xml edit in the repository, which no ticket asked for.
$script:SettingsDocArtifactPatterns = @(
    '(^|/)docs/settings/settings-manifest\.json$',
    '(^|/)docs/settings/settings-annotations\.json$',
    '(^|/)docs/settings/settings-scope-exclusions\.json$',
    '(^|/)docs/settings/howto-path-vocab\.json$',
    '(^|/)docs/icons/doc-icon-map\.json$',
    '(^|/)docs/SETTINGS_REFERENCE[A-Za-z_]*\.md$'
)

function Test-SettingsPathAgainst {
    param([string] $Path, [string[]] $Patterns)

    foreach ($rx in $Patterns) {
        if ($Path -match $rx) { return $true }
    }
    return $false
}

<#
.SYNOPSIS
    Does the changed set feed the manifest scan (stage 2, the gate's only gradle stage)?
#>
function Test-SettingsManifestInput {
    param(
        [string[]] $ChangedFiles,
        [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
    )

    $scoped = @(Expand-SettingsDocPaths -ChangedFiles $ChangedFiles)
    if ($scoped.Count -eq 0) { return $true }

    foreach ($f in $scoped) {
        if ($f -match $script:SettingsLayoutPathPattern) {
            # A layout that no longer exists was deleted from the set the scan reads, which moves
            # the manifest - so an unreadable path widens back rather than being read as clean.
            $layoutFull = Join-Path $RepoRoot $f
            if (-not (Test-Path -LiteralPath $layoutFull)) { return $true }
            if ([System.IO.File]::ReadAllText($layoutFull) -match $script:SettingsRowTagPattern) { return $true }
            continue
        }
        if (Test-SettingsPathAgainst -Path $f -Patterns $script:SettingsManifestInputPatterns) { return $true }
    }
    return $false
}

<#
.SYNOPSIS
    Does the changed set feed the SETTINGS_REFERENCE render (stage 4)?

.DESCRIPTION
    Strictly wider than Test-SettingsManifestInput, and the difference is the whole point:
    a set holding only docs/settings/settings-annotations.json matches no manifest input,
    so stage 2 is skipped - yet the reference IS rendered from the annotations and that
    ticket owns the render. Treating "stage 2 was skipped" as the advisory condition would
    let exactly that change ship a stale reference.
#>
function Test-SettingsReferenceInput {
    param(
        [string[]] $ChangedFiles,
        [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
    )

    $scoped = @(Expand-SettingsDocPaths -ChangedFiles $ChangedFiles)
    if ($scoped.Count -eq 0) { return $true }

    foreach ($f in $scoped) {
        if (Test-SettingsPathAgainst -Path $f -Patterns $script:SettingsReferenceInputPatterns) { return $true }
    }
    # The manifest is itself an input to the render, so whatever moves the manifest moves
    # the reference with it.
    return (Test-SettingsManifestInput -ChangedFiles $ChangedFiles -RepoRoot $RepoRoot)
}

<#
.SYNOPSIS
    Does the changed set hold a docs/ artifact that feeds a stage of the composite gate?

.DESCRIPTION
    The closure facade's doc-artifact trigger. Unlike the two predicates above, an empty set
    answers $false: this decides whether to RUN the gate, and running it over a set nobody
    named is not the unscoped judgement - post-change.ps1 always knows its changed set, and
    the source-tree half of the trigger sits beside this call.
#>
function Test-SettingsDocArtifactInput {
    param([string[]] $ChangedFiles)

    foreach ($f in (Expand-SettingsDocPaths -ChangedFiles $ChangedFiles)) {
        if (Test-SettingsPathAgainst -Path $f -Patterns $script:SettingsDocArtifactPatterns) { return $true }
    }
    return $false
}
