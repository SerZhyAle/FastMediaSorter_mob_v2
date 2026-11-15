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

    S4653 narrows that decision for the two file-level roots, docs/ and dev/ (DOC-INTERNAL-QUALITY rule 2 and item V):
    there every .md file must be matched by a record glob itself, or named by a baseline row - a file
    (`dev/foo.md`), a file glob (`dev/TASK_*.md`) or its directory. Measured 2026-11-15: 32 files under
    docs/ and dev/ sat in covered directories with no record naming them, so a new file dropped into a
    covered folder passed. The other roots keep the directory rule.

    The local, gitignored registry dev/DOCUMENT_REGISTRY.jsonl is read when present (S4653): its globs name
    files like the tracked ones, and each of its globs must match a file - the canon harness reads one
    registry path only, so nothing else checks the local corpus. An absent local registry is no finding.

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
    1 - a directory or a file-level-root file is unaccounted for, a baseline reason is too thin, a
        baseline row is stale, or a local registry glob matches no file.
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
    Write-Host "assert-document-registry-coverage: COULD NOT VERIFY - no such directory: $RepoRoot" -ForegroundColor Yellow
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

# Roots judged per FILE rather than per directory (S4653, DOC-INTERNAL-QUALITY rule 2). Kept to the two
# trees whose one-off notes are few enough to excuse by name; widening it is a decision, not a free edit.
$fileLevelRoots = @('docs', 'dev')

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

function Get-RelativePath {
    param([string] $FullPath)
    return $FullPath.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
}

function Test-FileLevelRoot {
    param([string] $RelativeFile)
    foreach ($fileRoot in $fileLevelRoots) {
        if ($RelativeFile.StartsWith("$fileRoot/", [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

function Test-FileRow {
    # A row whose left side ends in a document extension names files, not a directory.
    param([string] $Row)
    return @($documentExtensions | Where-Object { $Row.EndsWith($_, [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0
}

$errors = [System.Collections.Generic.List[string]]::new()
$advisories = [System.Collections.Generic.List[string]]::new()

function Test-RenderFields {
    <#
        DOC-INTERNAL-QUALITY item W (S4653): a generated record names its generator and its drift check as
        fields, each a command whose first .ps1 token exists. `check` = `none` is accepted as an ADVISORY -
        the renders that have no drift check today are listed on every run rather than hidden as free text.
    #>
    param($Record)
    $names = @($Record.PSObject.Properties.Name)
    $isGenerated = ($names -contains 'generated') -and [bool]$Record.generated
    if (-not $isGenerated) {
        if ($names -contains 'generator') {
            $errors.Add("record '$($Record.id)': carries 'generator' but is not generated - drop the field or set generated true")
        }
        return
    }
    foreach ($field in 'generator', 'check') {
        $value = if ($names -contains $field) { "$($Record.$field)".Trim() } else { '' }
        if (-not $value) {
            $errors.Add("record '$($Record.id)': generated record has no '$field' field - name the command (DOC-INTERNAL-QUALITY item W)")
            continue
        }
        if ($field -eq 'check' -and $value -ieq 'none') {
            $advisories.Add("record '$($Record.id)': render has no drift check (check = none)")
            continue
        }
        $script = @(($value -split '\s+') | Where-Object { $_ -like '*.ps1' } | Select-Object -First 1)
        if (-not $script) {
            $errors.Add("record '$($Record.id)': '$field' names no .ps1 command: $value")
        } elseif (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $script[0]))) {
            $errors.Add("record '$($Record.id)': '$field' names a script that does not exist: $($script[0])")
        }
    }
}

# --- covered set: every directory a record glob actually reaches -------------------------------
# Directory EQUALITY, never prefix: `.claude/` had covered siblings when `.claude/rules/` was
# missed, so a parent counting for its children would have passed the very gap this gate exists for.
$registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
if (-not (Test-Path -LiteralPath $registryPath)) {
    Write-Host "assert-document-registry-coverage: COULD NOT VERIFY - registry not found: docs/DOCUMENT_REGISTRY.jsonl" -ForegroundColor Yellow
    exit 2
}

$covered = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$namedFiles = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$recordCount = 0
$localRecordCount = 0
$localRegistryPath = Join-Path $RepoRoot 'dev/DOCUMENT_REGISTRY.jsonl'
$registrySources = @(@{ Path = $registryPath; Local = $false })
if (Test-Path -LiteralPath $localRegistryPath) { $registrySources += @{ Path = $localRegistryPath; Local = $true } }
try {
    foreach ($source in $registrySources) {
        foreach ($line in Get-Content -LiteralPath $source.Path -Encoding utf8) {
            if (-not "$line".Trim()) { continue }
            $record = $null
            try { $record = "$line".Trim() | ConvertFrom-Json } catch { continue }
            if (-not ($record.PSObject.Properties.Name -contains 'paths')) { continue }
            if ($source.Local) { $localRecordCount++ } else { $recordCount++ }
            Test-RenderFields -Record $record
            foreach ($registered in @($record.paths)) {
                $pattern = Join-Path $RepoRoot ("$registered" -replace '/', [IO.Path]::DirectorySeparatorChar)
                $matchedFiles = @(Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue)
                if ($source.Local -and $matchedFiles.Count -eq 0) {
                    $errors.Add("local registry record '$($record.id)': glob '$registered' matches no file - fix or delete the glob in dev/DOCUMENT_REGISTRY.jsonl")
                }
                foreach ($file in $matchedFiles) {
                    [void]$covered.Add((Get-RelativeDirectory -FullPath $file.FullName))
                    [void]$namedFiles.Add((Get-RelativePath -FullPath $file.FullName))
                }
            }
        }
    }
} catch {
    Write-Host "assert-document-registry-coverage: COULD NOT VERIFY - $($_.Exception.Message)" -ForegroundColor Yellow
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
$fileLevelFiles = [System.Collections.Generic.List[string]]::new()
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
        $relativeFile = Get-RelativePath -FullPath $file.FullName
        if (Test-FileLevelRoot -RelativeFile $relativeFile) { $fileLevelFiles.Add($relativeFile); continue }
        [void]$documentDirectories.Add($directory)
    }
}

# --- verdict ------------------------------------------------------------------------------------
$baselineRows = @($baseline.Keys)
$directoryRows = @($baselineRows | Where-Object { -not (Test-FileRow -Row $_) })
$fileRows = @($baselineRows | Where-Object { Test-FileRow -Row $_ })
$unaccounted = @($documentDirectories | Where-Object {
    $directory = $_
    if ($covered.Contains($directory)) { return $false }
    -not @($directoryRows | Where-Object { Test-BaselineRowMatches -Row $_ -Directory $directory }).Count
} | Sort-Object)
$unaccountedFiles = @($fileLevelFiles | Where-Object {
    $relativeFile = $_
    if ($namedFiles.Contains($relativeFile)) { return $false }
    if (@($fileRows | Where-Object { $relativeFile -like $_ }).Count) { return $false }
    $directory = $relativeFile.Substring(0, [Math]::Max(0, $relativeFile.LastIndexOf('/')))
    -not @($directoryRows | Where-Object { Test-BaselineRowMatches -Row $_ -Directory $directory }).Count
} | Sort-Object)
foreach ($relativeFile in $unaccountedFiles) {
    $errors.Add("$relativeFile is a document that no registry record names - add it to a record glob in " +
        "docs/DOCUMENT_REGISTRY.jsonl (or dev/DOCUMENT_REGISTRY.jsonl when gitignored), or add a file row to " +
        "scripts/quality/document-registry-coverage-baseline.txt saying what the file is and why it is not maintained")
}
foreach ($directory in $unaccounted) {
    $errors.Add("$directory holds documents that no registry record names - add a record (or a glob to an " +
        "existing one) in docs/DOCUMENT_REGISTRY.jsonl, or add a row to " +
        "scripts/quality/document-registry-coverage-baseline.txt saying what the tree is and who it is for")
}

# A row naming a tree that no longer holds documents is a decision about nothing, and it reads as
# one until someone checks - the same reason validate.ps1 refuses a sitemap_exclude naming a missing
# file. Covered counts as alive: a tree that got registered after being excused is a fixed problem,
# not a stale row, and failing there would punish the fix.
$fileLevelDirectories = @($fileLevelFiles | ForEach-Object { $_.Substring(0, [Math]::Max(0, $_.LastIndexOf('/'))) } | Select-Object -Unique)
foreach ($row in $baselineRows) {
    if (Test-FileRow -Row $row) {
        if (@($fileLevelFiles | Where-Object { $_ -like $row }).Count -gt 0) { continue }
        $errors.Add("baseline row '$row' names no document file - delete the row")
        continue
    }
    if (@($fileLevelDirectories | Where-Object { Test-BaselineRowMatches -Row $row -Directory $_ }).Count -gt 0) { continue }
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
    Write-Host ("  records: {0} (+{1} local)   covered directories: {2}   document directories: {3}   file-level files: {4}   baseline rows: {5}" -f `
        $recordCount, $localRecordCount, $covered.Count, $documentDirectories.Count, $fileLevelFiles.Count, $baseline.Count) -ForegroundColor Gray
}
foreach ($advisory in $advisories) { Write-Host "  advisory: $advisory" -ForegroundColor Yellow }
Write-Host "assert-document-registry-coverage: PASS (every document directory is registered or accounted for)" -ForegroundColor Green
exit 0
