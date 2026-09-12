#requires -Version 7.0
<#
.SYNOPSIS
    S2654: a script invoked by another script must also be known to git.

.DESCRIPTION
    `assert-fast-gates.ps1` and `assert-release-scope-gates.ps1` invoke gate scripts by name using
    `pwsh -File` or `& $item.Path`. A gate script that exists on the author's disk but is not in
    the git index is missing in fresh clones and release worktrees, leaving battery invocations red or
    bypassed.

    This gate scans non-comment tokens in consumer .ps1 scripts, resolves .ps1 references via
    `lib/script-reference-resolution.ps1` (`Resolve-ScriptTokenPaths`), and queries `Get-GitIndexMembership`.

.PARAMETER ChangedFiles
    Changed-file set. Narrows the CONSUMERS that are parsed. Accepts comma-separated paths or string array.

.PARAMETER Gate
    Fail-closed: exit 1 when a judged target is not in the index. Without it the same condition is
    reported and the run exits 0. Exit 2 is unconditional if verification is impossible.

.PARAMETER Root
    Discovery root (default: repository root).

.PARAMETER GitRoot
    Work tree whose index is queried (default: repository root).

.PARAMETER Quiet
    Print summary lines only, not per-path details.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-invoked-tracked.ps1 -Gate

.NOTES
    Exit codes:
      0  every judged target is in the git index (or none judged), or -Gate was absent.
      1  at least one judged target is not in the index, and -Gate was passed.
      2  cannot verify - git absent, not a work tree, or discovery root unreadable.
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
. "$PSScriptRoot\lib\script-reference-resolution.ps1"

function Deny-Verify([string]$Message) {
    $escaped = $Message.Replace('{', '{{').Replace('}', '}}')
    Write-Error "assert-invoked-tracked: CANNOT VERIFY - $escaped" -ErrorAction Continue
    exit 2
}

$discoveryRoot = if ($Root) { $Root } else { $repoRoot }
$workTree = if ($GitRoot) { $GitRoot } else { $repoRoot }

if (-not (Test-Path -LiteralPath $discoveryRoot -PathType Container)) {
    Deny-Verify "discovery root not found: $discoveryRoot"
}
$discoveryRoot = (Resolve-Path -LiteralPath $discoveryRoot).Path

try {
    [void](Assert-GitWorkTree -WorkTree $workTree)
}
catch {
    Deny-Verify $_.Exception.Message
}

$workTreeFull = (Resolve-Path -LiteralPath $workTree).Path
$workTreePrefix = ($workTreeFull -replace '\\', '/').TrimEnd('/').ToLowerInvariant() + '/'

$script:ExcludedFirstSegments = @('temp', 'V1', 'v2_6', 'spec_v2', '.git', '.gradle', 'node_modules', '.claude')
$script:ExcludedPrefixes = @('dev/archive/', 'PLAN/')

function Test-PathExcluded([string]$RelativeForward) {
    $first = ($RelativeForward -split '/')[0]
    if ($script:ExcludedFirstSegments -contains $first) { return $true }
    foreach ($prefix in $script:ExcludedPrefixes) {
        if ($RelativeForward.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    if ($RelativeForward -match '(^|/)build/') { return $true }
    return $false
}

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
                if (-not (Test-PathExcluded "$relative/")) { $pending.Push($entry.FullName) }
                continue
            }
            if ($entry.Extension -ine '.ps1') { continue }
            if (-not (Test-PathExcluded $relative)) { $found.Add($entry) }
        }
    }
    return $found
}

$changedKeys = [System.Collections.Generic.HashSet[string]]::new()
foreach ($entry in @($ChangedFiles | ForEach-Object { ([string]$_) -split ',' })) {
    $trimmed = $entry.Trim()
    if ($trimmed) { [void]$changedKeys.Add(($trimmed -replace '\\', '/').ToLowerInvariant()) }
}

try {
    if ($changedKeys.Count -gt 0) {
        $candidates = @(
            $changedKeys |
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

$treeCandidates = @(Get-CandidateScript -RootPath $workTreeFull)
$treeRelativePaths = @($treeCandidates | ForEach-Object { $_.FullName.Substring($workTreeFull.Length).TrimStart('\', '/') -replace '\\', '/' })
$candidateRelativePaths = @($candidates | ForEach-Object { $_.FullName.Substring($discoveryRoot.Length).TrimStart('\', '/') -replace '\\', '/' })
$scriptIndex = New-ScriptPathIndex -RelativePaths $candidateRelativePaths -TreePaths $treeRelativePaths

$sitesSeen = 0
$unresolved = 0
$ambiguous = 0
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
    if ($null -eq $ast) { $unresolved++; continue }

    $consumerRelPath = ($file.FullName -replace '\\', '/')
    if ($consumerRelPath.ToLowerInvariant().StartsWith($workTreePrefix)) {
        $consumerRelPath = $file.FullName.Substring($workTreeFull.Length).TrimStart('\', '/') -replace '\\', '/'
    }
    $slash = $consumerRelPath.LastIndexOf('/')
    $consumerRelDir = if ($slash -ge 0) { $consumerRelPath.Substring(0, $slash) } else { '' }

    foreach ($t in $tokens) {
        if ($t.Kind -eq [System.Management.Automation.Language.TokenKind]::Comment) { continue }
        $text = if ($t.PSObject.Properties["Value"]) { [string]$t.Value } else { [string]$t.Text }
        if (-not $text -or $text -notmatch '(?i)\.ps1') { continue }

        foreach ($m in [regex]::Matches($text, '(?i)[\w.][\w.\-/\\]*\.ps1')) {
            $rawToken = $m.Value
            $sitesSeen++

            $res = Resolve-ScriptTokenPaths -Token $rawToken -MentionDirectory $consumerRelDir -Index $scriptIndex
            if ($res.Ambiguous) {
                $ambiguous++
                continue
            }
            if ($res.Paths.Count -eq 0) {
                $unresolved++
                continue
            }

            foreach ($targetRelPath in $res.Paths) {
                if ($targetRelPath -ieq $consumerRelPath) { continue }

                $fullTarget = [System.IO.Path]::GetFullPath((Join-Path $workTreeFull ($targetRelPath -replace '/', '\')))
                $targetLower = ($fullTarget -replace '\\', '/').ToLowerInvariant()

                if (-not $targetLower.StartsWith($workTreePrefix)) {
                    $outsideWorkTree++
                    continue
                }

                $consumerLabel = '{0}:{1}' -f $consumerRelPath, $t.Extent.StartLineNumber

                if (-not (Test-Path -LiteralPath $fullTarget -PathType Leaf)) {
                    $absentTargets.Add('{0} -> {1}' -f $consumerLabel, $fullTarget)
                    continue
                }

                if (-not $consumersByTarget.Contains($fullTarget)) {
                    $consumersByTarget[$fullTarget] = [System.Collections.Generic.List[string]]::new()
                }
                $consumersByTarget[$fullTarget].Add($consumerLabel)
            }
        }
    }
}

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

Write-Host ("assert-invoked-tracked: expected: 0 | actual: {0} untracked target(s) of {1} judged" -f $untracked.Count, $judged.Count)
Write-Host ("assert-invoked-tracked: coverage - {0} site(s) seen in {1} script(s); {2} unresolved (runtime variable), {3} ambiguous, {4} outside the work tree, {5} absent from disk" -f `
        $sitesSeen, $candidates.Count, $unresolved, $ambiguous, $outsideWorkTree, $absentTargets.Count)

if (-not $Quiet -and $absentTargets.Count -gt 0) {
    Write-Host '  invoked script targets that do not exist on disk (reported, not refused):' -ForegroundColor DarkYellow
    foreach ($absent in $absentTargets) { Write-Host "    $absent" -ForegroundColor DarkYellow }
}

if ($untracked.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-invoked-tracked: PASS - every judged invoked script target is in the git index.' -ForegroundColor Green
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
        Write-Host ("      invoked by {0}" -f ($consumersByTarget[$target] -join ', ')) -ForegroundColor DarkGray
        Write-Host '      exists here, unknown to git - a fresh clone fails to run the invoked gate' -ForegroundColor DarkGray
    }
    Write-Host ''
    Write-Host '  Fix: stage them; the next commit then carries them by itself. Nothing else stages for you.' -ForegroundColor Yellow
    Write-Host ("    git add -- {0}" -f (($untracked | ForEach-Object { "`"$_`"" }) -join ' ')) -ForegroundColor Yellow
}

if ($Gate) {
    $failMessage = "assert-invoked-tracked: FAIL - $($untracked.Count) invoked script(s) exist on this machine only. " +
    'In a fresh clone or the release worktree the consumer fails when invoking them. Stage them with the command printed above.'
    Write-Error $failMessage -ErrorAction Continue
    exit 1
}

Write-Host 'assert-invoked-tracked: reported without gating - pass -Gate to make an untracked target fatal.' -ForegroundColor Yellow
exit 0
