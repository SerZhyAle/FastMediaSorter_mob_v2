#requires -Version 7.0
<#
.SYNOPSIS
    Assert every directory holding .md documents is named by a document-registry record (S2618).

.DESCRIPTION
    The reverse direction of the document registry's own check. The harness validator
    (document_registry/validate.ps1) enforces registry -> disk: every record glob must find a live
    file. Nothing enforced disk -> registry, so a directory full of documents that no record names
    failed no gate and survived indefinitely - which is how the missing `.claude/rules/*.md` glob
    reached S2607 while dev/RULE_AND_SKILL_AUTHORING.md called that record exhaustive.

    This gate judges DIRECTORIES, not files (strategic ADR-2). Measured 2026-09-06: 8708 of 9151
    files under the document roots are named by no record, and a per-file rule would demand a
    baseline row for every one-off design note in dev/ - a refusal carrying forty justifications on
    its first run gets switched off rather than read. The failure this gate exists for is
    directory-shaped: no glob reached `.claude/rules/` at all.

    The roots are DECLARED here, never derived from git (strategic ADR-1). Measured 2026-09-06, the
    git index answers "is this a maintained document" wrongly in both directions on this tree:
    `.claude/` is gitignored in full (.gitignore:71) yet carries the largest registered surface in
    the repository (the `repository-rules` record names six globs inside it), while
    scripts/mcp/docs-search-mcp/node_modules/ is tracked with 3390 files of which none is a document.

.PARAMETER RepoRoot
    Repository root to judge. Defaults to this script's grandparent. The contract suite passes a
    temporary tree here, which is the only way to prove the rule rather than the current tree.

.PARAMETER Quiet
    Print the verdict line only. Used by assert-release-scope-gates.ps1.

.PARAMETER Gate
    Accepted and ignored. assert-release-scope-gates.ps1 passes -Gate to every gate it runs, and a
    [CmdletBinding()] script that does not declare it dies on a binding error before its first line -
    exit 1, with "A parameter cannot be found" where a coverage verdict should be. That reads as a
    real finding in the runner's summary, so the gate would have been permanently and mutely red.
    There is no advisory mode to select here: this gate is fatal either way, which is why the switch
    changes nothing. Regression case: "invoked the way the release-scope runner invokes it".

Exit codes:
    0 - every directory holding documents is covered by a record or accounted for in the baseline.
    1 - a directory is unaccounted for, a baseline reason is too thin, or a baseline row is stale.
    2 - cannot verify: the registry is missing or unreadable, or RepoRoot does not exist.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = '',
    [switch] $Quiet,
    [switch] $Gate
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
if (-not (Test-Path -LiteralPath $RepoRoot -PathType Container)) {
    Write-Host "assert-document-registry-coverage: cannot verify - no such directory: $RepoRoot" -ForegroundColor Yellow
    exit 2
}
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

# A document is a .md file. Widening this set is a strategic extension point (section 5.3), not a
# free change: every added extension drags its own tree of data files into the baseline.
$documentExtensions = @('.md')

# Where a maintained document can live. This list is the answer to "what counts as a document" and
# is meant to be edited when a new documentation tree appears - see ADR-1 for why it cannot be
# derived. The repository root is walked non-recursively (CLAUDE.md, AGENTS.md, README.md live there).
$documentRoots = @(
    'docs', 'dev', '.claude', '.github', '.agents',
    'maestro', 'store_assets', 'play', 'delivery', 'scripts'
)

# Trees that are never documents, skipped during the walk rather than excused row by row. A baseline
# row is a DECISION about a repository tree; these are categories, and listing 3390 vendored files
# as decisions would bury the two rows that carry real judgement.
$excludedSegments = @(
    'node_modules',   # vendored third-party dependency trees - upstream's documents, not ours
    '.git',           # git's own object store
    'build',          # gradle output, regenerated on every build
    '__pycache__'     # python bytecode cache
)
$excludedPrefixes = @(
    'PLAN/',          # spec journal and ticket files - their own lifecycle and gates, not documents
    'temp/',          # scratch and artifacts, CLAUDE.md Rule 1
    'V1/',            # read-only zone, CLAUDE.md Rule 4
    'v2_6/',          # read-only zone, CLAUDE.md Rule 4
    'spec_v2/',       # read-only zone, CLAUDE.md Rule 4
    'dev/archive/'    # read-only zone, CLAUDE.md Rule 4
)

function Test-ExcludedPath {
    <#
        A directory is excluded when any of its segments names a category above, or when its path
        starts with an excluded prefix. Segment matching also covers the test trees: a directory
        named `tests` or ending in `.tests` holds fixtures, and a fixture that happens to be called
        CLAUDE.md is not a document anyone maintains.
    #>
    param([string] $RelativeDirectory)

    foreach ($prefix in $excludedPrefixes) {
        if ($RelativeDirectory.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { return $true }
        if ("$RelativeDirectory/" -eq $prefix) { return $true }
    }
    foreach ($segment in ($RelativeDirectory -split '/')) {
        if (-not $segment) { continue }
        if ($excludedSegments -contains $segment.ToLowerInvariant()) { return $true }
        if ($segment -eq 'tests' -or $segment.EndsWith('.tests', [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

function Test-BaselineRowMatches {
    <#
        A baseline row names one directory, or a subtree when it ends in `/**`. The subtree form
        exists because the excused trees here are per-agent: a new subagent creates a new memory
        directory, and a per-directory-only baseline would fail the gate on a tree the repository
        already decided about, teaching the next reader to add rows rather than to think.
    #>
    param([string] $Row, [string] $Directory)

    if ($Row.EndsWith('/**')) {
        $prefix = $Row.Substring(0, $Row.Length - 3)
        return ($Directory -ieq $prefix) -or $Directory.StartsWith("$prefix/", [StringComparison]::OrdinalIgnoreCase)
    }
    return ($Directory -ieq $Row)
}

function Get-RelativeDirectory {
    <#
        The directory a file sits in, repository-relative with forward slashes. A file at the
        repository root reports '.', which is a directory the baseline and the covered set can both
        name - '' would silently prefix-match everything.
    #>
    param([string] $FullPath)

    $relative = $FullPath.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
    $lastSlash = $relative.LastIndexOf('/')
    if ($lastSlash -lt 0) { return '.' }
    return $relative.Substring(0, $lastSlash)
}

$errors = [System.Collections.Generic.List[string]]::new()

# --- covered set: every directory a record glob actually reaches -------------------------------
# Directory EQUALITY, never prefix: `.claude/` had covered siblings when `.claude/rules/` was
# missed, so a parent counting for its children would have passed the very gap this gate exists for.
$registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
if (-not (Test-Path -LiteralPath $registryPath)) {
    Write-Host "assert-document-registry-coverage: cannot verify - registry not found: docs/DOCUMENT_REGISTRY.jsonl" -ForegroundColor Yellow
    exit 2
}

$covered = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$recordCount = 0
try {
    foreach ($line in Get-Content -LiteralPath $registryPath -Encoding utf8) {
        if (-not "$line".Trim()) { continue }
        $record = $null
        try { $record = "$line".Trim() | ConvertFrom-Json } catch { continue }
        if (-not ($record.PSObject.Properties.Name -contains 'paths')) { continue }
        $recordCount++
        foreach ($registered in @($record.paths)) {
            $pattern = Join-Path $RepoRoot ("$registered" -replace '/', [IO.Path]::DirectorySeparatorChar)
            foreach ($file in @(Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue)) {
                [void]$covered.Add((Get-RelativeDirectory -FullPath $file.FullName))
            }
        }
    }
} catch {
    Write-Host "assert-document-registry-coverage: cannot verify - $($_.Exception.Message)" -ForegroundColor Yellow
    exit 2
}

# --- baseline: the trees deliberately left out, each with a reason ------------------------------
$baselinePath = Join-Path $RepoRoot 'scripts/quality/document-registry-coverage-baseline.txt'
$baseline = [ordered]@{}
if (Test-Path -LiteralPath $baselinePath) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $baselinePath -Encoding utf8) {
        $lineNumber++
        $trimmed = "$line".Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $parts = $trimmed -split '\|', 2
        if ($parts.Count -lt 2) {
            $errors.Add("baseline L${lineNumber}: expected '<directory> | <reason>', got: $trimmed")
            continue
        }
        $directory = $parts[0].Trim().Replace('\', '/').TrimEnd('/')
        $reason = $parts[1].Trim()
        # The four-word floor is the one validate.ps1 already applies to sitemap_exclude reasons
        # (S1803). One standard for "say why" beats two, and "internal" is not a reason either here.
        $words = @(($reason -split '\s+') | Where-Object { $_ })
        if ($words.Count -lt 4) {
            $errors.Add("baseline L${lineNumber}: reason too thin for '$directory' - say what the tree is and who it is for")
        }
        if ($baseline.Contains($directory)) {
            $errors.Add("baseline L${lineNumber}: duplicate row for '$directory'")
        } else {
            $baseline[$directory] = $reason
        }
    }
}

# --- walk: every directory that holds a document ------------------------------------------------
$documentDirectories = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($file in @(Get-ChildItem -LiteralPath $RepoRoot -File -ErrorAction SilentlyContinue)) {
    if ($documentExtensions -contains $file.Extension.ToLowerInvariant()) { [void]$documentDirectories.Add('.') }
}
foreach ($root in $documentRoots) {
    $rootFull = Join-Path $RepoRoot $root
    if (-not (Test-Path -LiteralPath $rootFull -PathType Container)) { continue }
    foreach ($file in @(Get-ChildItem -LiteralPath $rootFull -Recurse -File -ErrorAction SilentlyContinue)) {
        if ($documentExtensions -notcontains $file.Extension.ToLowerInvariant()) { continue }
        $directory = Get-RelativeDirectory -FullPath $file.FullName
        if (Test-ExcludedPath -RelativeDirectory $directory) { continue }
        [void]$documentDirectories.Add($directory)
    }
}

# --- verdict ------------------------------------------------------------------------------------
$baselineRows = @($baseline.Keys)
$unaccounted = @($documentDirectories | Where-Object {
    $directory = $_
    if ($covered.Contains($directory)) { return $false }
    -not @($baselineRows | Where-Object { Test-BaselineRowMatches -Row $_ -Directory $directory }).Count
} | Sort-Object)
foreach ($directory in $unaccounted) {
    $errors.Add("$directory holds documents that no registry record names - add a record (or a glob to an " +
        "existing one) in docs/DOCUMENT_REGISTRY.jsonl, or add a row to " +
        "scripts/quality/document-registry-coverage-baseline.txt saying what the tree is and who it is for")
}

# A row naming a tree that no longer holds documents is a decision about nothing, and it reads as
# one until someone checks - the same reason validate.ps1 refuses a sitemap_exclude naming a missing
# file. Covered counts as alive: a tree that got registered after being excused is a fixed problem,
# not a stale row, and failing there would punish the fix.
foreach ($row in $baselineRows) {
    $matched = @($documentDirectories | Where-Object { Test-BaselineRowMatches -Row $row -Directory $_ })
    if ($matched.Count -gt 0) { continue }
    if (@($covered | Where-Object { Test-BaselineRowMatches -Row $row -Directory $_ }).Count -gt 0) { continue }
    $errors.Add("baseline row '$row' names no directory that holds documents - delete the row")
}

if ($errors.Count -gt 0) {
    Write-Host "assert-document-registry-coverage: FAIL ($($errors.Count) finding(s))" -ForegroundColor Red
    $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

if (-not $Quiet) {
    Write-Host ("  records: {0}   covered directories: {1}   document directories: {2}   baseline rows: {3}" -f `
        $recordCount, $covered.Count, $documentDirectories.Count, $baseline.Count) -ForegroundColor Gray
}
Write-Host "assert-document-registry-coverage: PASS (every document directory is registered or accounted for)" -ForegroundColor Green
exit 0
