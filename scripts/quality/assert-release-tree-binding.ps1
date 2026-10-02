#requires -Version 7.0
<#
.SYNOPSIS
    Gate: the release artifact is bound to the tested build - tested tree equals tagged tree equals
    built tree, plus the versionCode read back from the built bundle (BUILD-EVIDENCE 0.10 rule 8).

.DESCRIPTION
    The release AAB is not the build CI tested. /skill-release pushes the DEBUG branch (step 7),
    android-ci.yml tests that head, the branch is merged into main with --no-ff and tagged in the
    FastMediaSorter_release worktree (steps 8-9), and `a.ps1 r` rebuilds there (step 12). A merge
    commit whose main side carried a commit the DEBUG branch lacked, a worktree left with a tracked
    edit, or a `git pull` inside `a.ps1 r` that moved main after the tag all produce an artifact
    from a tree nobody tested, and nothing before this gate compared them.

    Binding chosen by the owner (S4057, 2026-10-02): the tested tree equals the tagged tree, plus a
    version read back from the built artifact. Checks, each fatal:

      tested   the tree of -TestedRef differs from the tree of -Tag
      built    the worktree HEAD tree differs from the tree of -Tag
      dirty    the worktree carries a tracked modification (staged or not)
      version  with -ExpectedVersionCode: the newest output-metadata.json under -BundleDir carries
               another versionCode

    Trees are compared, not commits: the --no-ff merge commit is a different commit with the same
    tree when main had nothing the DEBUG branch lacked, which is the bound case. Gitignored inputs
    copied in by scripts/release-worktree-sync.txt (signing, local.properties, prebuilt AARs) are
    outside the tracked tree and outside this binding.

.PARAMETER Worktree
    The release worktree the artifact was built in. Default: the sibling FastMediaSorter_release.

.PARAMETER Tag
    The release tag, e.g. release/v2.62.1234.567.

.PARAMETER TestedRef
    The ref CI tested - the pushed DEBUG head, e.g. origin/DEBUG-v041.

.PARAMETER ExpectedVersionCode
    The versionCode the release was pinned to. Omit to skip the read-back.

.PARAMETER BundleDir
    Where AGP wrote the bundle and its output-metadata.json. Default:
    <Worktree>/app_v2/build/outputs/bundle.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-release-tree-binding.ps1 -Tag release/v$NEW_VERSION -TestedRef origin/$CURRENT_DEBUG -ExpectedVersionCode $NEW_VERSION_CODE

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  bound - every check passed.
      1  refused - at least one check failed; the artifact is not bound to the tested build.
      2  cannot verify - the worktree, a ref or the bundle metadata could not be read.
#>
[CmdletBinding()]
param(
    [string]$Worktree,
    [Parameter(Mandatory)][string]$Tag,
    [Parameter(Mandatory)][string]$TestedRef,
    [string]$ExpectedVersionCode,
    [string]$BundleDir
)

$ErrorActionPreference = 'Stop'

function Exit-CannotVerify([string]$Why) {
    Write-Host "assert-release-tree-binding: COULD NOT VERIFY - $Why" -ForegroundColor Yellow
    exit 2
}

if (-not $Worktree) {
    $Worktree = Join-Path (Split-Path -Parent (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path) 'FastMediaSorter_release'
}
if (-not (Test-Path -LiteralPath $Worktree)) { Exit-CannotVerify "worktree not found: $Worktree" }

function Get-TreeId([string]$Ref) {
    $out = & git -C $Worktree rev-parse --verify --quiet "$Ref^{tree}" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $out) { return $null }
    return "$out".Trim()
}

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{
        module   = 'app_v2'
        worktree = $Worktree
        tag      = $Tag
        tested   = $TestedRef
    })

$tagTree = Get-TreeId $Tag
if (-not $tagTree) { Exit-CannotVerify "ref '$Tag' does not resolve in $Worktree" }
$testedTree = Get-TreeId $TestedRef
if (-not $testedTree) { Exit-CannotVerify "ref '$TestedRef' does not resolve in $Worktree" }
$headTree = Get-TreeId 'HEAD'
if (-not $headTree) { Exit-CannotVerify "HEAD does not resolve in $Worktree" }

$findings = [System.Collections.Generic.List[string]]::new()

if ($testedTree -ne $tagTree) {
    $findings.Add("tested: $TestedRef tree $testedTree differs from $Tag tree $tagTree - the tag carries changes CI never tested.")
}
if ($headTree -ne $tagTree) {
    $findings.Add("built: worktree HEAD tree $headTree differs from $Tag tree $tagTree - the artifact was built from another tree than the tag.")
}

$dirty = & git -C $Worktree status --porcelain --untracked-files=no 2>$null
if ($LASTEXITCODE -ne 0) { Exit-CannotVerify "git status failed in $Worktree" }
if ($dirty) {
    $findings.Add("dirty: the worktree carries tracked modifications - $((@($dirty) | Select-Object -First 5) -join '; ').")
}

if ($ExpectedVersionCode) {
    if (-not $BundleDir) { $BundleDir = Join-Path $Worktree 'app_v2/build/outputs/bundle' }
    $meta = Get-ChildItem -LiteralPath $BundleDir -Filter 'output-metadata.json' -File -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $meta) { Exit-CannotVerify "no output-metadata.json under $BundleDir" }
    try { $element = @((Get-Content -LiteralPath $meta.FullName -Raw | ConvertFrom-Json).elements)[0] }
    catch { Exit-CannotVerify "unreadable $($meta.FullName): $($_.Exception.Message)" }
    if (-not $element) { Exit-CannotVerify "$($meta.FullName) declares no elements" }
    if ("$($element.versionCode)" -ne "$ExpectedVersionCode") {
        $findings.Add("version: $($meta.FullName) carries versionCode $($element.versionCode), the release is pinned to $ExpectedVersionCode.")
    }
}

if ($findings.Count -eq 0) {
    Write-Host "assert-release-tree-binding: BOUND ($Tag = $TestedRef = worktree HEAD, tree $tagTree)." -ForegroundColor Green
    exit 0
}

Write-Host ("assert-release-tree-binding: REFUSED - {0} finding(s)" -f $findings.Count) -ForegroundColor Red
foreach ($f in $findings) { Write-Host "  - $f" -ForegroundColor Red }
exit 1
