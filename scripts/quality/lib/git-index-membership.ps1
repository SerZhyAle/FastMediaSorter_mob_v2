#requires -Version 7.0
<#
.SYNOPSIS
    Split a set of paths into the ones git's index knows and the ones it does not (S2616).

.DESCRIPTION
    Two gates ask this same question about two different selections. assert-suite-tracked.ps1
    (S2411) asks it about every discovered `<subject>.tests/Run-Tests.ps1`; assert-dotsource-tracked.ps1
    (S2616) asks it about every statically resolvable dot-source target. The selections differ, the
    question does not - so the question is answered once, here.

    THE DUPLICATION THIS REMOVES IS NOT LINE COUNT, IT IS THE NORMALIZATION. `git ls-files` prints
    repository-relative paths with forward slashes; a caller holds absolute paths with backslashes
    and whatever casing the file system handed it. Comparing the two is the step this repository has
    already got wrong once: the measurement that opened S2411 searched for the literal
    `Run-Tests.ps1`, Windows matched `run-tests.ps1` case-insensitively and git did not, and two
    suites that were tracked all along were reported untracked. One copy of that comparison can be
    fixed; two copies diverge (the S1621 rule).

    ONE `git ls-files` FOR THE WHOLE SELECTION, not one call per path. git prints the members it
    knows and stays silent about the rest, so the difference IS the answer, and a 65-path sweep pays
    for one process instead of 65.

    ASKS ABOUT THE INDEX, NOT ABOUT HEAD (inherited from S2411). "Present in the last commit" is
    unsatisfiable at the moment a gate runs: the file is written by the very ticket now closing and
    the owner commits later. The index is the minimal irreversible step - after `git add` the next
    commit carries the file on its own and it can no longer be lost.

    THROWS RATHER THAN EXITS. "Could not verify" and "found a defect" call for opposite reactions,
    so this helper never picks an exit code: it throws with the reason, and each caller maps that to
    its own exit 2. A helper that exited would decide the caller's contract for it.

.NOTES
    Dot-sourced helper: defines functions and exits nothing.

    Assigns nothing at top level. A dot-sourced file writes into its CALLER's scope, and S2441
    records what that costs - a `$target` here silently became post-change.ps1's own `-Target`
    parameter and every changelog row written that day recorded the wrong value.
#>

<#
.SYNOPSIS
    Verify that a directory is a git work tree, and return its top-level path with forward slashes.

.DESCRIPTION
    Separate from the query below because a caller must be able to establish "git can be asked about
    this tree" BEFORE it knows whether it has anything to ask about. Folding the check into the query
    alone leaves a hole exactly where it costs most: a selection that narrows to zero paths skips the
    query, so an unusable work tree goes undetected and the gate reports a clean tree it never
    looked at. Caught 2026-09-06 by case G of the S2616 contract suite, against the gate written in
    that same ticket.
#>
function Assert-GitWorkTree {
    param([Parameter(Mandatory)] [string] $WorkTree)

    if (-not (Test-Path -LiteralPath $WorkTree -PathType Container)) {
        throw "work tree not found: $WorkTree"
    }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw 'git is not on PATH, so the index cannot be read.'
    }

    $inside = & git -C $WorkTree rev-parse --is-inside-work-tree 2>&1
    if ($LASTEXITCODE -ne 0 -or ("$inside").Trim() -ne 'true') {
        throw "not a git work tree: $WorkTree"
    }
    $topLevel = & git -C $WorkTree rev-parse --show-toplevel 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git could not resolve the work tree root of ${WorkTree}: $((($topLevel | ForEach-Object { [string]$_ }) -join ' ').Trim())"
    }
    return ((("$topLevel").Trim()) -replace '\\', '/').TrimEnd('/')
}

<#
.SYNOPSIS
    Ask git which of the given paths are in the index.

.PARAMETER WorkTree
    The work tree whose index is queried. Exists so a contract suite can point a gate at a fixture
    repository instead of this one; a checker whose refusal path is never executed is the same
    unobserved green it was written to prevent.

.PARAMETER Paths
    Paths to judge, absolute or relative to WorkTree. Returned verbatim in the two output lists, so
    a caller keeps whatever spelling it wants to print. Duplicates collapse to their first spelling.

.OUTPUTS
    [pscustomobject] with Tracked and Untracked, each an array of the caller's own strings.
#>
function Get-GitIndexMembership {
    param(
        [Parameter(Mandatory)] [string] $WorkTree,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [string[]] $Paths
    )

    $top = Assert-GitWorkTree -WorkTree $WorkTree

    # Keyed by absolute path so the answer does not depend on which spelling the caller used, and
    # de-duplicated so one path passed twice cannot appear in both output lists.
    $originalByKey = [ordered]@{}
    $pathspec = @()
    foreach ($path in $Paths) {
        $text = [string]$path
        if (-not $text) { continue }
        $absolute = if ([System.IO.Path]::IsPathRooted($text)) { $text } else { Join-Path $WorkTree $text }
        try { $absolute = [System.IO.Path]::GetFullPath($absolute) } catch { continue }
        $key = ($absolute -replace '\\', '/').ToLowerInvariant()
        if ($originalByKey.Contains($key)) { continue }
        $originalByKey[$key] = $text
        $pathspec += $absolute
    }

    if ($pathspec.Count -eq 0) {
        return [pscustomobject]@{ Tracked = @(); Untracked = @() }
    }

    # core.quotepath=off: with it on git escapes any byte above ASCII into `"\303\251"`-style
    # octal, and the printed path would no longer equal the one on disk - a tracked file under a
    # non-ASCII directory would be reported untracked.
    $printed = & git -C $WorkTree -c core.quotepath=off ls-files -- @pathspec 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files failed: $((($printed | ForEach-Object { [string]$_ }) -join ' ').Trim())"
    }

    $trackedKeys = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($line in $printed) {
        $rel = ([string]$line).Trim()
        if (-not $rel) { continue }
        [void]$trackedKeys.Add("$top/$rel".ToLowerInvariant())
    }

    $tracked = @()
    $untracked = @()
    foreach ($key in $originalByKey.Keys) {
        if ($trackedKeys.Contains($key)) { $tracked += $originalByKey[$key] }
        else { $untracked += $originalByKey[$key] }
    }

    return [pscustomobject]@{ Tracked = @($tracked); Untracked = @($untracked) }
}
