#requires -Version 7.0
<#
.SYNOPSIS
    S2411: a contract suite the runner can discover must also be known to git.

.DESCRIPTION
    A suite lives at `<subject>.tests/Run-Tests.ps1` and is discovered by being placed - there is no
    registry to update (S2122). That makes placement the whole registration on the machine that
    placed it, and nothing at all anywhere else: closing a ticket stages nothing (neither
    post-change.ps1 nor close-and-log.ps1 runs `git add`), and `/git` assembles a commit by naming
    files inside groups built from the changed set, where a new untracked directory shows as the one
    folded line `?? dir/` and is lost. Once missed it stays missed - `git commit -a` stages edits to
    TRACKED files only - so the omission is irreversible by construction and accumulates. Measured
    2026-09-03: eight of 65 runners existed on the owner's machine only.

    The consequence is not a missing test but a lying one. In a fresh clone or the release worktree
    the runner discovers a SMALLER set and prints the same green verdict - a check that observed
    nothing, which is the failure class this repository keeps paying for.

    ASKS ABOUT THE INDEX, NOT ABOUT HEAD (strategic ADR-1). "Present in the last commit" is
    unsatisfiable at the moment this runs: the suite is written by the very ticket now closing, and
    the owner commits later. The index is the minimal irreversible step - after `git add` the next
    commit carries the file on its own and it can no longer be lost.

    TAKES THE LIST FROM THE RUNNER, DOES NOT WALK THE TREE (strategic ADR-3). Its own walk would
    judge a set different from the one that executes, which is the divergence S1621 forbids between
    two checks deciding the same thing. The original measurement behind this ticket made exactly
    that mistake in miniature: it searched for the literal `Run-Tests.ps1`, Windows matched
    `run-tests.ps1` case-insensitively and git did not, and two suites were reported untracked that
    were tracked all along. So the names come from discovery, never from a literal.

    "COULD NOT VERIFY" IS NOT "FOUND A DEFECT". No git, no work tree, or no list from the runner
    exits 2, not 1 - the two call for opposite reactions, and merging them is what produced the
    finding this gate exists for.

.PARAMETER ChangedFiles
    Changed-file set, forwarded to the runner so only the neighbouring suites are judged. Accepts a
    comma-separated string, because `pwsh -File` binds `-a x,y` as ONE array element. Absent means
    every discovered suite is judged.

.PARAMETER Gate
    Fail-closed: exit 1 when a discovered runner is not in the index. Without it the same condition
    is reported and the run exits 0. Exit 2 is unconditional - a gate that could not look must never
    answer green.

.PARAMETER Root
    Discovery root, forwarded to the runner (default: the repository's scripts/ directory).

.PARAMETER GitRoot
    Work tree whose index is queried (default: the repository root). Exists so the suite can point
    the gate at a fixture repository instead of this one; a checker whose refusal path is never
    executed is the same unobserved green it is here to prevent.

.PARAMETER Quiet
    Print the summary line only, not the per-path list.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-suite-tracked.ps1 -Gate
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-suite-tracked.ps1 -Gate -ChangedFiles "scripts/utils/agent-chat.tests/Run-Tests.ps1"

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every discovered runner is in the git index (or none was discovered), or -Gate was absent.
      1  at least one discovered runner is not in the index, and -Gate was passed.
      2  cannot verify - git absent, the target is not a git work tree, or the runner produced no list.
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
$runner = Join-Path $PSScriptRoot 'run-script-suites.ps1'
$workTree = if ($GitRoot) { $GitRoot } else { $repoRoot }

$script:listPath = $null

function Deny-Verify([string]$Message) {
    if ($script:listPath -and (Test-Path -LiteralPath $script:listPath)) {
        Remove-Item -LiteralPath $script:listPath -Force -ErrorAction SilentlyContinue
    }
    Write-Error "assert-suite-tracked: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    Deny-Verify "the suite runner is absent: $runner"
}
if (-not (Test-Path -LiteralPath $workTree -PathType Container)) {
    Deny-Verify "work tree not found: $workTree"
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Deny-Verify 'git is not on PATH, so the index cannot be read.'
}

$insideWorkTree = & git -C $workTree rev-parse --is-inside-work-tree 2>&1
if ($LASTEXITCODE -ne 0 -or ("$insideWorkTree").Trim() -ne 'true') {
    Deny-Verify "not a git work tree: $workTree"
}
$topLevel = & git -C $workTree rev-parse --show-toplevel 2>&1
if ($LASTEXITCODE -ne 0) {
    Deny-Verify "git could not resolve the work tree root of ${workTree}: $(($topLevel -join ' ').Trim())"
}
$topFwd = ((("$topLevel").Trim()) -replace '\\', '/').TrimEnd('/')

# --- the selection, taken from the runner ------------------------------------------------------
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$listDir = Join-Path $repoRoot 'temp/scratch'
New-Item -ItemType Directory -Force -Path $listDir | Out-Null
$script:listPath = Join-Path $listDir ("suite-tracked-list-{0}.json" -f $PID)

$argv = @('-NoProfile', '-File', $runner, '-ListOnly', '-Json', $script:listPath)
if ($Root) { $argv += @('-Root', $Root) }
$normalizedChanged = @(
    $ChangedFiles |
        ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)
if ($normalizedChanged.Count -gt 0) { $argv += @('-ChangedFiles', ($normalizedChanged -join ',')) }

$listOutput = & $pwshExe @argv 2>&1
$listCode = [int]$LASTEXITCODE
if ($listCode -ne 0 -or -not (Test-Path -LiteralPath $script:listPath)) {
    Deny-Verify "the suite runner produced no list (exit ${listCode}): $((($listOutput | ForEach-Object { [string]$_ }) -join ' ').Trim())"
}

try {
    $records = @(Get-Content -LiteralPath $script:listPath -Raw | ConvertFrom-Json)
}
catch {
    Deny-Verify "the suite list is not readable JSON: $($_.Exception.Message)"
}
finally {
    Remove-Item -LiteralPath $script:listPath -Force -ErrorAction SilentlyContinue
    $script:listPath = $null
}

# --- the index question ------------------------------------------------------------------------
# One `git ls-files` for the whole selection rather than one per path: it prints the members it
# knows and stays silent about the rest, so the difference IS the answer, and a 65-suite release
# sweep pays for one process instead of 65.
$relByKey = @{}
$pathspec = @()
foreach ($record in $records) {
    $rel = [string]$record.Suite
    if (-not $rel) { continue }
    $absolute = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $repoRoot $rel }
    $relByKey[(($absolute -replace '\\', '/').ToLowerInvariant())] = $rel
    $pathspec += $absolute
}

if ($pathspec.Count -eq 0) {
    Write-Host 'assert-suite-tracked: expected: 0 | actual: 0 untracked runner(s) of 0 discovered'
    Write-Host 'assert-suite-tracked: PASS - no contract suite in the selection.' -ForegroundColor Green
    exit 0
}

$trackedOutput = & git -C $workTree ls-files -- @pathspec 2>&1
if ($LASTEXITCODE -ne 0) {
    Deny-Verify "git ls-files failed: $((($trackedOutput | ForEach-Object { [string]$_ }) -join ' ').Trim())"
}

$trackedKeys = [System.Collections.Generic.HashSet[string]]::new()
foreach ($line in $trackedOutput) {
    $printed = ([string]$line).Trim()
    if (-not $printed) { continue }
    [void]$trackedKeys.Add("$topFwd/$printed".ToLowerInvariant())
}

$untracked = @(
    $relByKey.Keys |
        Where-Object { -not $trackedKeys.Contains($_) } |
        ForEach-Object { $relByKey[$_] } |
        Sort-Object
)

Write-Host ("assert-suite-tracked: expected: 0 | actual: {0} untracked runner(s) of {1} discovered" -f $untracked.Count, $pathspec.Count)

if ($untracked.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-suite-tracked: PASS - every discovered contract-suite runner is in the git index.' -ForegroundColor Green
    }
    exit 0
}

if (-not $Quiet) {
    foreach ($path in $untracked) {
        Write-Host ("  {0}  exists here, unknown to git - absent in a fresh clone" -f $path) -ForegroundColor Red
    }
    Write-Host ''
    Write-Host '  Fix: stage them; the next commit then carries them by itself. Nothing else stages for you.' -ForegroundColor Yellow
    Write-Host ("    git add -- {0}" -f (($untracked | ForEach-Object { "`"$_`"" }) -join ' ')) -ForegroundColor Yellow
}

if ($Gate) {
    $failMessage = "assert-suite-tracked: FAIL - $($untracked.Count) contract-suite runner(s) exist on this machine only. " +
    'A fresh clone and the release worktree discover a smaller set and print the same green verdict, ' +
    'so the suite protects nobody but its author. Stage them with the command printed above.'
    Write-Error $failMessage -ErrorAction Continue
    exit 1
}

Write-Host 'assert-suite-tracked: reported without gating - pass -Gate to make an untracked runner fatal.' -ForegroundColor Yellow
exit 0
