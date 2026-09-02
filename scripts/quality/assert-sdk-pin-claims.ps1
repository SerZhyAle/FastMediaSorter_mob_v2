<#
.SYNOPSIS
    Fail when a document or agent definition claims an SDK pin value the build files disagree with.

.DESCRIPTION
    S1438. The repository already pins toolchain values into a marker-delimited managed block
    (scripts/quality/generate-toolchain-pins.ps1, guarded by assert-doc-pin-drift.ps1), but that
    block is the only thing it guards. Pins are also stated in ordinary prose - "compileSdk 36,
    minSdk 26 (Android 8+), Java 17" in an index, "`compileSdk 36`" in an architecture bullet, the
    same line in an agent definition and in agent memory. Nothing watched those, and eight copies
    drifted to 35 while the build compiled against 36, silently: an agent reading one concludes an
    API is unavailable and can file an SDK-uplift ticket for an uplift that already happened.

    This check reads the value from the build file every run and never stores a number of its own,
    so bumping compileSdk in Gradle moves the expectation with it.

    Deliberately NOT scanned, because their old values are correct and rewriting them would destroy
    the file's purpose:
      - dev/CHANGELOG.md         - a historical journal; each row is true for its own date.
      - scripts/doc-drift.tests/ - fixtures; a stale pin is the test's input.
      - CLAUDE.md, docs/TECH_STACK.md - already covered by the managed-block gate; leaving them here
        too would report one drift twice and split ownership of the fix.

.PARAMETER Gate
    Exit non-zero on a mismatch. Without it the script reports and exits 0, so it can be run for
    information without failing a caller.

.PARAMETER Quiet
    Suppress the per-run progress lines; the verdict line and any mismatch rows still print. This
    is what assert-fast-gates.ps1 passes when it runs the gate as part of the batch.

.EXIT CODES
    0 - every scanned claim matches the build files (or mismatches found without -Gate).
    1 - at least one claim contradicts the build files, and -Gate was passed.
    2 - could not verify: the build file or its pin could not be read, so nothing was compared.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-sdk-pin-claims.ps1
    pwsh -NoProfile -File scripts/quality/assert-sdk-pin-claims.ps1 -Gate
#>
param([switch]$Gate, [switch]$Quiet)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# '.claude' is a scan root below, and a nested agent worktree lives under it carrying a full copy
# of this repository. Which paths that covers is decided in one place for all four repo-wide walks.
. (Join-Path $PSScriptRoot 'lib/nested-worktrees.ps1')
$nestedWorktrees = Get-NestedWorktreeRelativePath -RepoRoot $repoRoot
$skippedWorktreeFiles = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)

function Get-BuildPin {
    <#
        Reads one numeric pin out of a Gradle build file. Returns $null when absent, which the
        caller turns into exit 2 - "could not verify" is a different answer from "matches".
    #>
    param([Parameter(Mandatory)][string]$BuildFile, [Parameter(Mandatory)][string]$Pattern)
    $path = Join-Path $repoRoot $BuildFile
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $m = [regex]::Match((Get-Content -LiteralPath $path -Raw), $Pattern)
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value
}

# One row per pin. Adding targetSdk or minSdk here is a single line, not a second script.
#   Claim  - matches how the pin is written in prose, capturing the claimed number.
$pins = @(
    [pscustomobject]@{
        Name      = 'compileSdk'
        BuildFile = 'app_v2/build.gradle.kts'
        Source    = 'compileSdk\s*=\s*(\d+)'
        Claim     = 'compileSdk`?\s*=?\s*(\d{2})'
    }
)

# Kotlin sources are scanned too, not only prose: the drift that started S1438 included a comment
# in PermissionHelper explaining an API workaround by a compileSdk value that had already moved.
# A gate that missed its own originating case would be the wrong gate.
$scanRoots = @('docs', 'dev', '.claude', '.github', 'app_v2/src', 'wear/src')
$scanExtensions = @('.md', '.kt')
$excludedPaths = @(
    'dev/CHANGELOG.md',
    'docs/TECH_STACK.md',
    'scripts/doc-drift.tests'
)
$excludedRootFiles = @('CLAUDE.md')

function Test-Excluded {
    param([Parameter(Mandatory)][string]$RelativePath)
    $normalized = $RelativePath.Replace('\', '/')
    # Counted rather than merely skipped: the verdict line below has to be able to say the walk
    # dropped a subtree, or "0 mismatches" reads the same whether it looked or not.
    if (Test-InNestedWorktree -RelativePath $normalized -Prefixes $nestedWorktrees) {
        [void]$skippedWorktreeFiles.Add($normalized)
        return $true
    }
    foreach ($ex in $excludedPaths) {
        if ($normalized -eq $ex -or $normalized.StartsWith("$ex/")) { return $true }
    }
    if ($excludedRootFiles -contains $normalized) { return $true }
    return $false
}

$mismatches = @()
$scanned = 0

foreach ($pin in $pins) {
    $actual = Get-BuildPin -BuildFile $pin.BuildFile -Pattern $pin.Source
    if ([string]::IsNullOrWhiteSpace($actual)) {
        Write-Error "assert-sdk-pin-claims: cannot read $($pin.Name) from $($pin.BuildFile) - nothing was compared." -ErrorAction Continue
        exit 2
    }
    if (-not $Quiet) { Write-Host "$($pin.Name): build files say $actual (from $($pin.BuildFile))" }

    foreach ($root in $scanRoots) {
        $rootPath = Join-Path $repoRoot $root
        if (-not (Test-Path -LiteralPath $rootPath)) { continue }
        foreach ($file in @(Get-ChildItem -LiteralPath $rootPath -Recurse -File -ErrorAction SilentlyContinue |
                    Where-Object { $scanExtensions -contains $_.Extension })) {
            $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
            if (Test-Excluded -RelativePath $rel) { continue }
            $scanned++
            $lineNumber = 0
            foreach ($line in (Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue)) {
                $lineNumber++
                foreach ($m in [regex]::Matches($line, $pin.Claim)) {
                    $claimed = $m.Groups[1].Value
                    if ($claimed -eq $actual) { continue }
                    $mismatches += [pscustomobject]@{
                        File = $rel.Replace('\', '/'); Line = $lineNumber
                        Pin = $pin.Name; Claimed = $claimed; Actual = $actual; Text = $line.Trim()
                    }
                }
            }
        }
    }
}

$skipNotice = Get-NestedWorktreeSkipNotice -SkippedFileCount $skippedWorktreeFiles.Count -Prefixes $nestedWorktrees
if ($skipNotice) { Write-Host "assert-sdk-pin-claims: $skipNotice" }

Write-Host ("assert-sdk-pin-claims: expected: 0 | actual: {0} stale claim(s) across {1} scanned file(s)" -f $mismatches.Count, $scanned)

if ($mismatches.Count -eq 0) {
    Write-Host "assert-sdk-pin-claims: PASS - every prose SDK-pin claim matches the build files." -ForegroundColor Green
    exit 0
}

foreach ($m in $mismatches) {
    Write-Host ("  {0}:{1}  claims {2} {3}, build files say {4}" -f $m.File, $m.Line, $m.Pin, $m.Claimed, $m.Actual) -ForegroundColor Red
    Write-Host ("      {0}" -f $m.Text) -ForegroundColor DarkGray
}

if (-not $Gate) {
    Write-Host "assert-sdk-pin-claims: reporting only (-Gate not passed)." -ForegroundColor Yellow
    exit 0
}

Write-Error "assert-sdk-pin-claims: FAIL - $($mismatches.Count) stale SDK-pin claim(s). Update the text; the build files are the source." -ErrorAction Continue
exit 1
