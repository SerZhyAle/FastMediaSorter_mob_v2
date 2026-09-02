<#
.SYNOPSIS
    Fail when documentation names a dependency the project retired and replaced.

.DESCRIPTION
    S1489. A library swap changes the build file in one commit and leaves every prose mention of the
    old library behind. The project migrated SFTP from SSHJ to JSch across S0207 and S0046, and the
    old name survived in nine maintained documents for months - among them the published privacy
    policy in three locales, which the app opens directly from Settings. A reader was told the app
    ships a third-party component it does not ship, and a developer reading the tech-requirements row
    was sent to the wrong library's callback API.

    Nothing caught it, and the near-miss is instructive. assert-doc-pin-drift.ps1 does watch jsch -
    but it compares the VERSION in one gated table row, and passed the whole time because that row
    was already correct. A version comparator cannot express "this name must not appear at all",
    which is why this is a separate gate rather than another row in scripts/doc-drift/pins.psd1.

    Deliberately NOT scanned:
      - dev/CHANGELOG.md   - a historical journal; a row naming the retired library is true for its
                             own date, and rewriting it would destroy the record of the migration.
      - PLAN/              - specs record what was decided when, including the rejected option.
      - temp/              - scratch and build artifacts, not maintained text.
      - build.gradle.kts   - naming the rejected alternative next to the chosen dependency IS the
                             record of the decision ("better KEX support than SSHJ"), so build files
                             are not scan roots. Do not add them.

.PARAMETER Gate
    Exit non-zero on a hit. Without it the script reports and exits 0, so it can be run for
    information without failing a caller.

.PARAMETER Quiet
    Suppress the per-run progress lines; the verdict line and any hit rows still print. This is what
    assert-fast-gates.ps1 passes when it runs the gate as part of the batch.

.EXIT CODES
    0 - no retired dependency name found (or hits found without -Gate).
    1 - at least one retired name found in maintained text, and -Gate was passed.
    2 - could not verify: no scan root existed, so nothing was examined.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-retired-dependency-names.ps1
    pwsh -NoProfile -File scripts/quality/assert-retired-dependency-names.ps1 -Gate
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

# One row per retirement. Adding the next swap is a single line, not a second script.
#   Pattern - word-bounded so a substring inside an unrelated identifier does not fire.
#   Because - printed on a hit, so the fix is obvious without opening the ticket.
$retired = @(
    [pscustomobject]@{
        Name       = 'SSHJ'
        ReplacedBy = 'JSch (com.github.mwiede:jsch)'
        Pattern    = '\bSSHJ\b'
        Because    = 'SFTP moved to JSch in S0207/S0046; SSHJ is not in any build file.'
    }
)

# Kotlin sources are scanned alongside prose: a comment naming the retired library misleads exactly
# like a document does, and costs more because it sits next to the code that proves it wrong.
$scanRoots = @('docs', 'dev', 'store_assets', '.claude', '.github', 'app_v2/src', 'wear/src')
$scanExtensions = @('.md', '.kt')
# The published landing pages are hand-written HTML at the repository root, outside every scan root
# above, and they carry the same technology bullets the README does - the most visible place a
# retired library name could sit. They are named individually rather than by adding '.html' to the
# extension list, because the only HTML that matters here lives at the root: docs/ has none.
$scanRootFiles = @(
    'README.md',
    'index.html', 'index-ru.html', 'index-uk.html',
    'nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html'
)
$excludedPaths = @(
    'dev/CHANGELOG.md',
    'scripts/doc-drift.tests'
)

function Test-Excluded {
    param([Parameter(Mandatory)][string]$RelativePath)
    $normalized = $RelativePath.Replace('\', '/')
    # Counted rather than merely skipped: the verdict line below has to be able to say the walk
    # dropped a subtree, or "0 hits" reads the same whether it looked or not.
    if (Test-InNestedWorktree -RelativePath $normalized -Prefixes $nestedWorktrees) {
        [void]$skippedWorktreeFiles.Add($normalized)
        return $true
    }
    foreach ($ex in $excludedPaths) {
        if ($normalized -eq $ex -or $normalized.StartsWith("$ex/")) { return $true }
    }
    return $false
}

$filesToScan = [System.Collections.Generic.List[object]]::new()

foreach ($root in $scanRoots) {
    $rootPath = Join-Path $repoRoot $root
    if (-not (Test-Path -LiteralPath $rootPath)) { continue }
    foreach ($file in @(Get-ChildItem -LiteralPath $rootPath -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $scanExtensions -contains $_.Extension })) {
        $filesToScan.Add($file)
    }
}

foreach ($rootFile in $scanRootFiles) {
    $path = Join-Path $repoRoot $rootFile
    if (Test-Path -LiteralPath $path) { $filesToScan.Add((Get-Item -LiteralPath $path)) }
}

if ($filesToScan.Count -eq 0) {
    Write-Error "assert-retired-dependency-names: no scan root existed - nothing was examined." -ErrorAction Continue
    exit 2
}

if (-not $Quiet) {
    Write-Host ("watching {0} retired name(s) across {1} file(s)" -f $retired.Count, $filesToScan.Count)
}

$hits = @()
$scanned = 0

foreach ($file in $filesToScan) {
    $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
    if (Test-Excluded -RelativePath $rel) { continue }
    $scanned++
    $lineNumber = 0
    foreach ($line in (Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue)) {
        $lineNumber++
        foreach ($entry in $retired) {
            if ($line -notmatch $entry.Pattern) { continue }
            $hits += [pscustomobject]@{
                File = $rel.Replace('\', '/'); Line = $lineNumber
                Name = $entry.Name; ReplacedBy = $entry.ReplacedBy
                Because = $entry.Because; Text = $line.Trim()
            }
        }
    }
}

$skipNotice = Get-NestedWorktreeSkipNotice -SkippedFileCount $skippedWorktreeFiles.Count -Prefixes $nestedWorktrees
if ($skipNotice) { Write-Host "assert-retired-dependency-names: $skipNotice" }

Write-Host ("assert-retired-dependency-names: expected: 0 | actual: {0} retired name(s) across {1} scanned file(s)" -f $hits.Count, $scanned)

if ($hits.Count -eq 0) {
    Write-Host "assert-retired-dependency-names: PASS - no maintained text names a retired dependency." -ForegroundColor Green
    exit 0
}

foreach ($h in $hits) {
    Write-Host ("  {0}:{1}  names {2}, replaced by {3}" -f $h.File, $h.Line, $h.Name, $h.ReplacedBy) -ForegroundColor Red
    Write-Host ("      {0}" -f $h.Text) -ForegroundColor DarkGray
    Write-Host ("      {0}" -f $h.Because) -ForegroundColor DarkGray
}

if (-not $Gate) {
    Write-Host "assert-retired-dependency-names: reporting only (-Gate not passed)." -ForegroundColor Yellow
    exit 0
}

Write-Error "assert-retired-dependency-names: FAIL - $($hits.Count) retired dependency name(s) in maintained text. Rename to the current library; the build files are the source." -ErrorAction Continue
exit 1
