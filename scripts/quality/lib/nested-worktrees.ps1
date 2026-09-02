#requires -Version 7.0
<#
.SYNOPSIS
    One definition of "this path belongs to another agent's checkout, not to this tree",
    shared by every repository-wide file walk.

.DESCRIPTION
    A git worktree created inside the repository - the shape the agent harness produces for
    `isolation: worktree`, under `.claude/worktrees/` - is a full second copy of this same
    repository. It is invisible to git status (`.gitignore` carries `.claude/`) but perfectly
    visible to a gate that walks the filesystem instead of the index, so every document and
    every source file in it is judged a second time, at whatever revision the copy was left at.

    Observed 2026-08-27 while closing S2194: a sibling agent's worktree appeared mid-ticket and
    added 505 phantom document references to assert-script-references, failing the closure of a
    change that had touched none of them. The fix went into that one gate as a private line.

    Observed again 2026-09-02 (S2333), from a worktree left behind six days earlier: all 30
    findings of assert-sdk-pin-claims and all 74 of assert-retired-dependency-names came from
    the copy, none from the working tree, and the two gates spent 69 s between them walking
    19705 extra files. check-typo-lint was silently double-counting every .kt in the tree.

    That is four scripts needing one answer, which is the S1621 rule: a sentence decided in
    more than one place has already diverged. It is decided here and nowhere else.

    WHY THE LIST COMES FROM GIT AND NOT FROM A HARDCODED '.claude/worktrees':
    the harness picks that directory, not this repository. A future version that places a
    worktree elsewhere would slip past a hardcoded path silently, and silence is the failure
    mode this library exists to remove. Git already knows every worktree it created, so it is
    asked. The hardcoded path survives only as the fallback for when git cannot be reached.

    WHY '.claude/' ITSELF IS NOT EXCLUDED: without its worktrees it is 572 markdown and Kotlin
    files of rules, commands, hooks, skills and agent memory, and they are judged on purpose -
    an agent-memory file whose compileSdk claim has drifted outlives the session that wrote it
    and repeats in every later one. Measured 2026-09-02: five such claims live there. Excluding
    the directory would blind these gates to the one corpus only they inspect.

    Dot-source it:  . (Join-Path $PSScriptRoot 'lib/nested-worktrees.ps1')

.NOTES
    Pure PowerShell plus one read-only `git worktree list`. No gradle, no network, no writes.
    Never throws on a missing or failing git: a walk that cannot ask still gets the fallback.
#>

Set-StrictMode -Version Latest

# Used when git cannot be consulted. Deliberately the observed harness location and nothing
# more: a guess wider than the evidence would start excluding real working-tree content.
$script:NestedWorktreeFallback = @('.claude/worktrees')

# Resolved lists keyed by normalized repository root, so a gate that asks per file pays for
# one `git worktree list` per process instead of one per file.
$script:NestedWorktreeCache = @{}

function ConvertTo-NestedWorktreeSlashPath {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Path)
    return $Path.Replace('\', '/').TrimEnd('/')
}

<#
.SYNOPSIS
    Repo-relative, forward-slashed prefixes of every git worktree nested inside the repository.

.DESCRIPTION
    Returns an empty array when the repository holds no nested worktree, which is the normal
    case - callers must treat "no prefixes" as "exclude nothing", never as an error.

    A worktree that is a sibling of the repository rather than a child of it is NOT returned:
    it is outside every scan root already, and naming it here would invite a caller to compare
    against a path it can never produce.

.PARAMETER RepoRoot
    Absolute path to the repository root.

.PARAMETER Refresh
    Bypass the per-process cache. Only tests need this.
#>
function Get-NestedWorktreeRelativePath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [switch]$Refresh
    )

    $rootKey = (ConvertTo-NestedWorktreeSlashPath $RepoRoot)
    if (-not $Refresh -and $script:NestedWorktreeCache.ContainsKey($rootKey)) {
        return $script:NestedWorktreeCache[$rootKey]
    }

    $prefixes = [System.Collections.Generic.List[string]]::new()
    $lines = @()
    try {
        # -C keeps the call independent of the caller's current directory: a gate may have
        # pushed into a scan root before asking.
        $lines = @(& git -C $RepoRoot worktree list --porcelain 2>$null)
        if ($LASTEXITCODE -ne 0) { $lines = @() }
    } catch {
        # A missing git, a corrupt .git, or a repository that is not one at all. None of these
        # are this library's problem to report - the walk still has to happen.
        $lines = @()
    }

    if ($lines.Count -gt 0) {
        # Compared with a trailing slash so a NEIGHBOUR whose name merely starts with the root's
        # name - FastMediaSorter_mob_v2_backup beside FastMediaSorter_mob_v2 - is not mistaken
        # for a child. The main worktree equals the root exactly and is skipped by the same test.
        $rootWithSlash = "$rootKey/"
        foreach ($line in $lines) {
            if ($line -notmatch '^worktree\s+(.+)$') { continue }
            $wt = ConvertTo-NestedWorktreeSlashPath $Matches[1]
            if (-not $wt.StartsWith($rootWithSlash, [StringComparison]::OrdinalIgnoreCase)) { continue }
            $prefixes.Add($wt.Substring($rootWithSlash.Length))
        }
    } else {
        foreach ($f in $script:NestedWorktreeFallback) { $prefixes.Add($f) }
    }

    $result = @($prefixes)
    $script:NestedWorktreeCache[$rootKey] = $result
    return $result
}

<#
.SYNOPSIS
    True when a repo-relative path sits inside one of the given nested-worktree prefixes.

.PARAMETER RelativePath
    Repo-relative path, either slash style.

.PARAMETER Prefixes
    Output of Get-NestedWorktreeRelativePath. Empty means nothing is excluded.
#>
function Test-InNestedWorktree {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$RelativePath,
        [AllowEmptyCollection()][AllowNull()][string[]]$Prefixes
    )

    if (-not $Prefixes -or $Prefixes.Count -eq 0) { return $false }
    $normalized = ConvertTo-NestedWorktreeSlashPath $RelativePath
    foreach ($prefix in $Prefixes) {
        if ([string]::IsNullOrWhiteSpace($prefix)) { continue }
        # The directory itself and everything under it. Prefix + '/' rather than a bare
        # StartsWith for the same reason as above: '.claude/worktrees-old' is not inside
        # '.claude/worktrees'.
        if ($normalized.Equals($prefix, [StringComparison]::OrdinalIgnoreCase)) { return $true }
        if ($normalized.StartsWith("$prefix/", [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

<#
.SYNOPSIS
    One-line report of what a walk dropped, or empty string when it dropped nothing.

.DESCRIPTION
    A walk that silently discards a subtree reports "0 findings" identically whether it looked
    and found nothing or never looked at all. Callers print this whenever the count is non-zero
    so the two stay distinguishable.
#>
function Get-NestedWorktreeSkipNotice {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$SkippedFileCount,
        [AllowEmptyCollection()][AllowNull()][string[]]$Prefixes
    )

    if ($SkippedFileCount -le 0) { return '' }
    $names = @($Prefixes) -join ', '
    return ("ignored {0} file(s) inside {1} nested git worktree(s): {2}" -f `
        $SkippedFileCount, @($Prefixes).Count, $names)
}
