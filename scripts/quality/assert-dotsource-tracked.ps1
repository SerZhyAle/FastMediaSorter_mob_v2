#requires -Version 7.0
<#
.SYNOPSIS
    S2616: a script reached by a dot-source must also be known to git.

.DESCRIPTION
    `. "$PSScriptRoot\gradle-worker-reaper.ps1"` is resolved when the CONSUMER is parsed, not when a
    function in it is first called. So a target that exists on the author's disk but never reached the index does
    not degrade anything in a fresh clone - it aborts the consumer before its first line runs.

    Measured 2026-09-05 (S2588, while staging its own files): scripts/builders/gradle-worker-reaper.ps1
    was dot-sourced by check-standard-fast.ps1 and by its own contract suite, was not matched by any
    .gitignore rule, and was simply never `git add`ed. Its ticket S2585 had closed Verified the day
    before, because every check it ran read the WORKING TREE, which holds the file either way. In a
    fresh clone that one missing file takes down fk, fkn, fc, fr, fu and every a.ps1 target above them.

    WHY THIS IS NOT assert-suite-tracked.ps1. That gate (S2411) asks the identical question - is this
    path in the index - about a selection that comes from the suite runner, so it holds
    `<subject>.tests/Run-Tests.ps1` and nothing else. An ordinary helper is outside it by
    construction. The consequences are inverted, too: a missing suite runner shrinks a green report,
    a missing dot-source target refuses to parse. The two gates share the index question itself
    through lib/git-index-membership.ps1 and differ only in what they select.

    THE SELECTION COMES FROM THE PARSER, NOT FROM A REGULAR EXPRESSION. `.` is an operator that a
    regex cannot tell from a decimal point, from a sentence in a comment, or from a member access.
    The AST answers exactly one question - is this node a CommandAst whose InvocationOperator is Dot
    - and a comment mentioning a script produces no node at all. This is deliberately NOT
    lib/script-reference-resolution.ps1: that resolver is permissive on purpose, because for orphan
    detection (S2124) a script named in a comment IS referenced. Here the same permissiveness would
    invent findings.

    ONLY WHAT RESOLVES STATICALLY IS JUDGED, AND THE REST IS COUNTED OUT LOUD. Measured 2026-09-06 on
    this tree: 509 dot-source sites in 595 scripts, of which 229 resolve onto 52 distinct targets.
    The other 280 address `$szaFwdTarget` - the
    canon forwarders (S2402), whose target is a plugin-cache path outside the repository and is not
    this repository's to track. A gate cannot evaluate those without running them, so it does not
    guess: it prints how many it could not resolve, and a reader can see the coverage rather than
    infer it from a green line.

    A TARGET ABSENT FROM DISK IS REPORTED, NOT REFUSED. It means either a broken dot-source or a
    resolution this gate got wrong, and calling it "untracked" would be inventing a finding out of
    its own uncertainty. It is printed on its own line and changes no exit code.

    ASKS ABOUT THE INDEX, NOT ABOUT HEAD (inherited from S2411). The file is written by the very
    ticket now closing and the owner commits later, so "present in the last commit" is unsatisfiable
    at gate time. `git add` is the minimal irreversible step: after it the next commit carries the
    file by itself.

    "COULD NOT VERIFY" IS NOT "FOUND A DEFECT" (inherited from S2411). No git, no work tree, or no
    readable discovery root exits 2, never 1 - the two call for opposite reactions.

.PARAMETER ChangedFiles
    Changed-file set. Narrows the CONSUMERS that are parsed, not the targets that are judged: a
    helper nobody dot-sources yet has no edge and can break nothing, and the ticket that does wire it
    up necessarily edits a consumer, which is the closure that then refuses. Accepts a
    comma-separated string, because `pwsh -File` binds `-a x,y` as ONE array element. Absent means
    every discovered consumer is parsed.

.PARAMETER Gate
    Fail-closed: exit 1 when a judged target is not in the index. Without it the same condition is
    reported and the run exits 0. Exit 2 is unconditional - a gate that could not look must never
    answer green.

.PARAMETER Root
    Discovery root (default: the repository root). Exclusions are computed relative to THIS root, not
    to the repository, so pointing the gate at a fixture under temp/ still discovers the fixture.

.PARAMETER GitRoot
    Work tree whose index is queried (default: the repository root). Exists so the contract suite can
    point the gate at a fixture repository instead of this one; a gate whose refusal path is never
    executed is the same unobserved green it was written to prevent.

.PARAMETER Quiet
    Print the summary lines only, not the per-path list.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-dotsource-tracked.ps1 -Gate
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-dotsource-tracked.ps1 -Gate -ChangedFiles "scripts/builders/check-standard-fast.ps1"

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every judged dot-source target is in the git index (or none was judged), or -Gate was absent.
      1  at least one judged target is not in the index, and -Gate was passed.
      2  cannot verify - git absent, the target is not a git work tree, or the discovery root is unreadable.
#>
[CmdletBinding()]
param(
    [string[]]$ChangedFiles,
    [switch]$Gate,
    [string]$Root,
    [string]$GitRoot,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
. "$PSScriptRoot\lib\git-index-membership.ps1"

function Deny-Verify([string]$Message) {
    Write-Error "assert-dotsource-tracked: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

$discoveryRoot = if ($Root) { $Root } else { $repoRoot }
$workTree = if ($GitRoot) { $GitRoot } else { $repoRoot }

if (-not (Test-Path -LiteralPath $discoveryRoot -PathType Container)) {
    Deny-Verify "discovery root not found: $discoveryRoot"
}
$discoveryRoot = (Resolve-Path -LiteralPath $discoveryRoot).Path

# Frozen or foreign trees. PLAN/archive/ and dev/archive/ are read-only evidence (Rule 4) that
# describes the tree as it was - the 2026-09-06 measurement found two dot-sources to paths that no
# longer exist in there, and neither is fixable or should be. Anything under a build/ segment is
# generated output, and V1/v2_6/spec_v2 are the read-only legacy checkouts.
$script:ExcludedFirstSegments = @('temp', 'V1', 'v2_6', 'spec_v2', '.git', '.gradle', 'node_modules')
$script:ExcludedPrefixes = @('dev/archive/', 'PLAN/archive/')

function Test-PathExcluded([string]$RelativeForward) {
    $first = ($RelativeForward -split '/')[0]
    if ($script:ExcludedFirstSegments -contains $first) { return $true }
    foreach ($prefix in $script:ExcludedPrefixes) {
        if ($RelativeForward.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    # A build/ segment at any depth: app_v2/build/, wear/build/, corex/*/build/ are all generated.
    if ($RelativeForward -match '(^|/)build/') { return $true }
    return $false
}

<#
.SYNOPSIS
    Turn the path expression of a dot-source into a concrete path, or $null when it cannot be known
    without running the script.
#>
function Resolve-DotSourceTarget {
    param(
        [Parameter(Mandatory)] [System.Management.Automation.Language.Ast] $Expression,
        [Parameter(Mandatory)] [string] $ScriptDirectory
    )

    # `. 'literal.ps1'` and `. "$PSScriptRoot\x.ps1"`. Any variable still standing AFTER $PSScriptRoot
    # is substituted makes the value unknowable here, so the site is counted as unresolved rather
    # than guessed at. Testing for a leftover `$` before the substitution instead would accept
    # "$PSScriptRoot\$name.ps1" and resolve it to a path containing a literal dollar sign - a
    # nonexistent file this gate would then report as a finding of its own making.
    if ($Expression -is [System.Management.Automation.Language.StringConstantExpressionAst]) {
        return $Expression.Value
    }
    if ($Expression -is [System.Management.Automation.Language.ExpandableStringExpressionAst]) {
        $text = ($Expression.Value -replace '(?i)\$PSScriptRoot', $ScriptDirectory)
        if ($text -match '\$') { return $null }
        return $text
    }

    # `. (Join-Path $PSScriptRoot 'x.ps1')`
    if ($Expression -is [System.Management.Automation.Language.ParenExpressionAst]) {
        $pipeline = $Expression.Pipeline
        if ($pipeline -isnot [System.Management.Automation.Language.PipelineAst]) { return $null }
        if ($pipeline.PipelineElements.Count -ne 1) { return $null }
        $command = $pipeline.PipelineElements[0]
        if ($command -isnot [System.Management.Automation.Language.CommandAst]) { return $null }
        $name = $command.GetCommandName()
        if (-not $name -or $name -notmatch '(?i)^join-path$') { return $null }

        $parts = @()
        foreach ($element in @($command.CommandElements | Select-Object -Skip 1)) {
            if ($element -is [System.Management.Automation.Language.StringConstantExpressionAst]) {
                $parts += $element.Value
                continue
            }
            if ($element -is [System.Management.Automation.Language.VariableExpressionAst] -and
                $element.VariablePath.UserPath -match '(?i)^PSScriptRoot$') {
                $parts += $ScriptDirectory
                continue
            }
            if ($element -is [System.Management.Automation.Language.ExpandableStringExpressionAst]) {
                $text = ($element.Value -replace '(?i)\$PSScriptRoot', $ScriptDirectory)
                if ($text -match '\$') { return $null }
                $parts += $text
                continue
            }
            # A named parameter (-Path) or any other expression: not worth a second guess.
            return $null
        }
        if ($parts.Count -eq 0) { return $null }
        return ($parts -join [System.IO.Path]::DirectorySeparatorChar)
    }

    return $null
}

# --- discovery ---------------------------------------------------------------------------------
# Excluded directories are pruned AT DESCENT, not filtered after the fact: `-Recurse` walks into
# app_v2/build and wear/build first and hands back the whole generated tree for a Where-Object to
# throw away. Measured 2026-09-06 over the same 595 scripts: 10.4 s filtered, 8.1 s pruned. The
# remainder is the parse itself and not the walk, which is why a named changed set below addresses
# its files directly rather than walking and narrowing - that path measures 639 ms.
function Get-CandidateScript {
    param([Parameter(Mandatory)] [string] $RootPath)

    $found = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    $pending = [System.Collections.Generic.Stack[string]]::new()
    $pending.Push($RootPath)

    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($entry in @(Get-ChildItem -LiteralPath $directory -Force -ErrorAction SilentlyContinue)) {
            $relative = $entry.FullName.Substring($RootPath.Length).TrimStart('\', '/') -replace '\\', '/'
            if ($entry.PSIsContainer) {
                # The trailing slash is what lets one predicate judge a directory and a file alike:
                # the build/ rule is written as a path segment, not as a name.
                if (-not (Test-PathExcluded "$relative/")) { $pending.Push($entry.FullName) }
                continue
            }
            if ($entry.Extension -ine '.ps1') { continue }
            if (-not (Test-PathExcluded $relative)) { $found.Add($entry) }
        }
    }
    return $found
}

# -ChangedFiles narrows the CONSUMERS. Matched on the repository-relative tail so a caller may pass
# either spelling; post-change.ps1 passes repository-relative paths.
# De-duplicated by a lower-cased key, but the VALUE keeps the caller's own spelling, and the walk
# below uses the value. Get-Item echoes back whatever casing it was handed rather than the casing on
# disk, so a lower-cased entry travels all the way into the `git ls-files` pathspec - and git matches
# a pathspec case-SENSITIVELY. A lower-cased `dev/catalog/..` then selects nothing from an index that
# holds `dev/CATALOG/..`, git stays silent, and silence is this gate's evidence of absence: a file
# staged seconds earlier was reported as existing on this machine only, with the printed `git add`
# fix being the command that had just been run (S2837, 2026-09-10). It is the same normalization
# mistake the shared lib beside this gate was extracted to hold once - the lib compares
# case-insensitively on both sides and is correct; the case was already destroyed before it was called.
$changedPaths = [ordered]@{}
foreach ($entry in @($ChangedFiles | ForEach-Object { ([string]$_) -split ',' })) {
    $trimmed = $entry.Trim()
    if (-not $trimmed) { continue }
    $key = ($trimmed -replace '\\', '/').ToLowerInvariant()
    if (-not $changedPaths.Contains($key)) { $changedPaths[$key] = $trimmed }
}

try {
    if ($changedPaths.Count -gt 0) {
        # A named changed set addresses its files directly - walking the tree to then discard all but
        # a handful is the per-ticket half paying the release half's price on every closure.
        $candidates = @(
            $changedPaths.Values |
                ForEach-Object {
                    $rooted = if ([System.IO.Path]::IsPathRooted($_)) { $_ } else { Join-Path $discoveryRoot $_ }
                    if (Test-Path -LiteralPath $rooted -PathType Leaf) { Get-Item -LiteralPath $rooted }
                } |
                Where-Object { $_ -and $_.Extension -ieq '.ps1' }
        )
    }
    else {
        $candidates = @(Get-CandidateScript -RootPath $discoveryRoot)
    }
}
catch {
    Deny-Verify "the discovery root could not be walked: $($_.Exception.Message)"
}

# --- parse -------------------------------------------------------------------------------------
# Asked BEFORE the parse, and unconditionally. A selection that narrows to zero judged targets never
# reaches the index query, so leaving this to it made an unusable work tree report a clean tree it
# had not looked at - case G of this gate's own contract suite, on the day it was written.
try {
    [void](Assert-GitWorkTree -WorkTree $workTree)
}
catch {
    Deny-Verify $_.Exception.Message
}

$workTreeFull = (Resolve-Path -LiteralPath $workTree).Path
$workTreePrefix = ($workTreeFull -replace '\\', '/').TrimEnd('/').ToLowerInvariant() + '/'

$sitesSeen = 0
$unresolved = 0
$outsideWorkTree = 0
$consumersByTarget = [ordered]@{}
$absentTargets = [System.Collections.Generic.List[string]]::new()

foreach ($file in $candidates) {
    $tokens = $null
    $errors = $null
    $ast = $null
    try {
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$errors)
    }
    catch {
        $ast = $null
    }
    # A file this parser cannot read is counted as unresolved, never as clean: the whole point of
    # the gate is that unread things are the ones that break somebody else's clone.
    if ($null -eq $ast) { $unresolved++; continue }

    $dotSources = @($ast.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.InvocationOperator -eq [System.Management.Automation.Language.TokenKind]::Dot
            }, $true))

    foreach ($dotSource in $dotSources) {
        if ($dotSource.CommandElements.Count -lt 1) { continue }
        $sitesSeen++

        $resolved = Resolve-DotSourceTarget -Expression $dotSource.CommandElements[0] -ScriptDirectory $file.DirectoryName
        if (-not $resolved) { $unresolved++; continue }

        # `. Some-Function` dot-sources a function into the current scope and names no file at all.
        # Treating it as a path would resolve it against the script's directory and then report the
        # nonexistent result, so it is counted honestly as something this gate did not judge.
        if ($resolved -notmatch '(?i)\.psm?1$') { $unresolved++; continue }

        $full = $null
        try {
            $rooted = if ([System.IO.Path]::IsPathRooted($resolved)) { $resolved } else { Join-Path $file.DirectoryName $resolved }
            $full = [System.IO.Path]::GetFullPath($rooted)
        }
        catch {
            $unresolved++
            continue
        }

        # Outside the judged work tree - the canon forwarders' plugin-cache path is the live case,
        # and it is genuinely not this repository's file to track.
        if (-not (($full -replace '\\', '/').ToLowerInvariant().StartsWith($workTreePrefix))) {
            $outsideWorkTree++
            continue
        }

        # A -ChangedFiles entry may be an absolute path from outside the discovery root, so the tail
        # is taken only when it really is a tail; otherwise the full path is the honest label.
        $consumerPath = ($file.FullName -replace '\\', '/')
        if ($consumerPath.ToLowerInvariant().StartsWith(($discoveryRoot -replace '\\', '/').TrimEnd('/').ToLowerInvariant() + '/')) {
            $consumerPath = $file.FullName.Substring($discoveryRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        }
        $consumer = '{0}:{1}' -f $consumerPath, $dotSource.Extent.StartLineNumber

        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            $absentTargets.Add(('{0} -> {1}' -f $consumer, $full))
            continue
        }

        if (-not $consumersByTarget.Contains($full)) {
            $consumersByTarget[$full] = [System.Collections.Generic.List[string]]::new()
        }
        $consumersByTarget[$full].Add($consumer)
    }
}

# --- the index question --------------------------------------------------------------------------
$judged = @($consumersByTarget.Keys)
$untracked = @()
if ($judged.Count -gt 0) {
    try {
        $membership = Get-GitIndexMembership -WorkTree $workTreeFull -Paths $judged
    }
    catch {
        Deny-Verify $_.Exception.Message
    }
    $untracked = @($membership.Untracked | Sort-Object)
}

# --- report ---------------------------------------------------------------------------------------
Write-Host ("assert-dotsource-tracked: expected: 0 | actual: {0} untracked target(s) of {1} judged" -f $untracked.Count, $judged.Count)
Write-Host ("assert-dotsource-tracked: coverage - {0} dot-source site(s) in {1} script(s); {2} unresolved (runtime variable), {3} outside the work tree, {4} absent from disk" -f `
        $sitesSeen, $candidates.Count, $unresolved, $outsideWorkTree, $absentTargets.Count)

if (-not $Quiet -and $absentTargets.Count -gt 0) {
    Write-Host '  dot-source targets that do not exist on disk (reported, not refused):' -ForegroundColor DarkYellow
    foreach ($absent in $absentTargets) { Write-Host "    $absent" -ForegroundColor DarkYellow }
}

if ($untracked.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-dotsource-tracked: PASS - every judged dot-source target is in the git index.' -ForegroundColor Green
    }
    exit 0
}

if (-not $Quiet) {
    foreach ($target in $untracked) {
        $relative = if (($target -replace '\\', '/').ToLowerInvariant().StartsWith($workTreePrefix)) {
            $target.Substring($workTreeFull.Length).TrimStart('\', '/') -replace '\\', '/'
        }
        else { $target }
        Write-Host ("  {0}" -f $relative) -ForegroundColor Red
        Write-Host ("      dot-sourced by {0}" -f ($consumersByTarget[$target] -join ', ')) -ForegroundColor DarkGray
        Write-Host '      exists here, unknown to git - a fresh clone fails to PARSE the consumer' -ForegroundColor DarkGray
    }
    Write-Host ''
    Write-Host '  Fix: stage them; the next commit then carries them by itself. Nothing else stages for you.' -ForegroundColor Yellow
    Write-Host ("    git add -- {0}" -f (($untracked | ForEach-Object { "`"$_`"" }) -join ' ')) -ForegroundColor Yellow
}

if ($Gate) {
    $failMessage = "assert-dotsource-tracked: FAIL - $($untracked.Count) dot-sourced script(s) exist on this machine only. " +
    'A dot-source is resolved when the consumer is PARSED, so in a fresh clone or the release worktree ' +
    'the consumer does not start at all. Stage them with the command printed above.'
    Write-Error $failMessage -ErrorAction Continue
    exit 1
}

Write-Host 'assert-dotsource-tracked: reported without gating - pass -Gate to make an untracked target fatal.' -ForegroundColor Yellow
exit 0
