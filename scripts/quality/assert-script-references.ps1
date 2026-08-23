#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a repository script that nothing references is either deleted or declares itself a
    hand-run tool (S1872).

.DESCRIPTION
    Before this gate the only way to learn a script was dead was to sweep the repository by hand -
    an answer that is true once and never re-checked. Fifteen scripts were found that way on
    2026-08-21, including a twelve-script migration directory whose own README calls itself
    finished and two wrappers whose headers claim callers they do not have.

    HOW IT JUDGES. Every project-authored .ps1 under the script roots is collected, then every file
    in the reference corpus is read once and scanned for .ps1-shaped tokens. A script is
    UNREFERENCED when every file mentioning its basename is itself a file of that basename - a
    script quoting its own name in help text does not keep itself alive.

    THE CHEATSHEET IS EXCLUDED, AND THAT IS NOT A SETTING. docs/SCRIPT_CHEATSHEET.md is generated
    from every script in the repository, so a corpus containing it reports zero unreferenced
    scripts forever and the gate becomes a check that cannot fail.

    READ-ONLY ZONES ARE IN THE CORPUS. dev/archive, V1, v2_6 and spec_v2 may not be written, but
    they may be read, and a reference living only there is still a reference.

    THE ESCAPE HATCH. A script the owner runs by hand is unreferenced by definition and is the
    costliest class to delete, because the loss surfaces only when it is next needed. Such a script
    declares itself with a line in its comment-based help:

        Manual tool: <why it exists and who runs it>

    An empty reason does not count as a declaration.

    MEMORY MODE. -Memory checks the other direction: every .ps1 path token written in the agent
    memory must resolve to a real file, or carry a `Historical:` or `External:` marker on its line
    or the line above. The tokens include paths beginning with a dot - dropping that leading dot
    was the exact flaw in the manual pass this gate replaces.

    DOCS MODE. -Docs asks the memory question of the live documents: a document that names a .ps1
    file which does not exist hands the reader a command that cannot run. S1978 found one such line
    and a sweep found thirteen, in three registered documents, alive for an unknown time because
    nothing ever re-asked (S1979). The corpus is docs/, dev/ minus its archive and changelog,
    .claude/ minus agent-memory (owned by -Memory above), and the four agent-rule files at the
    root. PLAN/, V1/, v2_6/ and spec_v2/ are excluded on the same live-versus-historical cut the
    main mode uses: a spec that described a script does not run it.

    RESOLUTION IS TREE-WIDE, JUDGING IS NOT. Both reverse modes resolve a token against every .ps1
    in the repository, not against the three script roots the main mode judges - maestro/,
    .claude/hooks/ and dev/build-with-version.ps1 are real scripts, and resolving against the
    narrow set would report each of them as a phantom.

    THE DOCS BASELINE IS A LIST, NOT A COUNT. doc-script-reference-baseline.txt holds one
    `path :: token` line per known-bad reference, so a new phantom cannot hide behind a fixed one.
    A baseline line that no longer matches anything is printed as a prune hint, not a failure.

.PARAMETER Gate
    Accepted for the fast-gate batch's uniform call shape; judging is already the default.

.PARAMETER Report
    List the findings and exit 0 regardless of the baseline. Use when deciding, not when gating.

.PARAMETER Memory
    Check agent-memory script paths instead of repository reference connectivity.

.PARAMETER Docs
    Check that every .ps1 token in the live documents resolves to a real script.

.PARAMETER ChangedFiles
    -Docs only: judge findings in these files alone, so one closure is not charged for another
    session's in-flight document. A .ps1 anywhere in the set widens the judgement back to the whole
    corpus, because a renamed or deleted script breaks documents that are not in the set.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.NOTES
    Exit codes:
      0 - at or below the baseline, or -Report was given
      1 - above the baseline: an unreferenced script appeared, a memory path resolves to nothing,
          or a document names a script that does not exist and is not in the docs baseline
      2 - cannot verify: a script root, the agent memory, the document corpus or a baseline file
          is missing
#>
[CmdletBinding()]
param(
    # Accepted for the fast-gate batch's uniform call shape. Judging against the baseline is
    # already this script's default, so the switch changes nothing - refusing it would make the
    # batch report a parameter error as a gate failure.
    [switch] $Gate,
    [switch] $Report,
    [switch] $Memory,
    [switch] $Docs,
    [string[]] $ChangedFiles = @(),
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$scriptRoots = @('scripts', 'dev/CATALOG/scripts', 'dev/ACTIVITY_CATALOG/scripts')
# A mention is not a reference. A spec that once described a script, or a changelog row recording
# the day it was edited, keeps no script alive - and the repository holds over 1500 such documents,
# enough to make every dead script look wired. So the corpus has two tiers, and only the first one
# decides: LIVE is what actually calls or wires a script, HISTORICAL is what merely remembers it.
$liveRoots = @(
    'scripts', 'docs', '.claude', '.github', 'maestro', 'fastlane', 'config',
    'lint-rules', 'corex', 'delivery', 'benchmark'
)
$historicalRoots = @('PLAN', 'V1', 'v2_6', 'spec_v2')
# dev/ is split: its guides and catalogues are live wiring, its changelog and archive are a record.
$devLiveExclusions = @('dev/archive', 'dev/CHANGELOG.md')
$corpusExtensions = @('.ps1', '.psm1', '.psd1', '.md', '.json', '.jsonl', '.txt', '.yml', '.yaml', '.sh', '.bat', '.cmd')
$rootFiles = @('a.ps1', 'CLAUDE.md', 'AGENTS.md', 'GEMINI.md', 'README.md')
# Generated from every script in the repository - including it would keep every dead script alive.
$corpusExclusions = @('docs/SCRIPT_CHEATSHEET.md')
$baselinePath = Join-Path $PSScriptRoot 'script-reference-baseline.txt'
$docsBaselinePath = Join-Path $PSScriptRoot 'doc-script-reference-baseline.txt'
# The documents an agent or a developer is expected to act on. agent-memory is excluded because
# -Memory already owns it, and the cheatsheet because it is generated from the scripts themselves.
$docRoots = @('docs', 'dev', '.claude')
$docRootFiles = @('CLAUDE.md', 'AGENTS.md', 'GEMINI.md', 'README.md')
$docExclusions = @('dev/archive', 'dev/CHANGELOG.md', '.claude/agent-memory', 'docs/SCRIPT_CHEATSHEET.md')
$docExtensions = @('.md', '.json', '.jsonl')
# A token in a document or in memory resolves against every script in the tree, but never against
# temp/: that directory is scratch, so a document pointing into it points at a draft.
$knownScriptExclusions = @('temp', '.git', '.gradle', 'build', 'node_modules')
$tokenPattern = [regex]'(?i)[\w.][\w.\-/\\]*\.ps1'
# Same excuse vocabulary in both reverse modes: a line may name a script that lives outside this
# repository, or one that used to live in it, as long as it says so.
$excusePattern = '(?i)\b(Historical|External|placeholder|example)\b'

function Test-Excluded {
    param([string] $Relative)
    if ($Relative -match '(^|[\\/])node_modules([\\/]|$)') { return $true }
    foreach ($e in $corpusExclusions) {
        if ($Relative.Replace('\', '/') -ieq $e) { return $true }
    }
    return $false
}

function Get-Relative {
    param([string] $Full)
    return $Full.Substring($RepoRoot.Length).TrimStart('\', '/')
}

# ---------------------------------------------------------------- collect scripts
$scripts = @{}
foreach ($root in $scriptRoots) {
    $full = Join-Path $RepoRoot ($root -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Host "assert-script-references: cannot verify - script root missing: $root" -ForegroundColor Yellow
        exit 2
    }
    foreach ($f in Get-ChildItem -LiteralPath $full -Recurse -File -Filter *.ps1) {
        $rel = Get-Relative $f.FullName
        if (Test-Excluded $rel) { continue }
        if (-not $scripts.ContainsKey($f.Name)) { $scripts[$f.Name] = New-Object System.Collections.Generic.List[string] }
        $scripts[$f.Name].Add($rel)
    }
}
foreach ($name in @('a.ps1')) {
    $full = Join-Path $RepoRoot $name
    if (Test-Path -LiteralPath $full) {
        if (-not $scripts.ContainsKey($name)) { $scripts[$name] = New-Object System.Collections.Generic.List[string] }
        $scripts[$name].Add($name)
    }
}

# ---------------------------------------------------------------- reverse-direction helpers
# Every script in the tree, by file name. The main mode judges only $scriptRoots, but a token in a
# document naming maestro/run-tests.ps1 or .claude/hooks/observe-empty-grep.ps1 resolves perfectly
# well - judging those against the narrow set would invent phantoms (S1979).
function Get-KnownScriptNames {
    $known = @{}
    # Pruned walk rather than -Recurse: the excluded directories are the build outputs and the git
    # object store, and descending into them costs more than the rest of the repository together.
    $stack = New-Object System.Collections.Generic.Stack[string]
    $stack.Push($RepoRoot)
    while ($stack.Count -gt 0) {
        $dir = $stack.Pop()
        foreach ($sub in [IO.Directory]::EnumerateDirectories($dir)) {
            if ($knownScriptExclusions -contains (Split-Path $sub -Leaf)) { continue }
            $stack.Push($sub)
        }
        foreach ($f in [IO.Directory]::EnumerateFiles($dir, '*.ps1')) {
            $known[[IO.Path]::GetFileName($f)] = $true
        }
    }
    return $known
}

# The leading dot matters: '.claude/hooks/x.ps1' is a real path and a token regex anchored on a
# word character silently drops its first segment.
function Find-UnresolvedTokens {
    param([string[]] $Files, [hashtable] $Known)
    $out = New-Object System.Collections.Generic.List[object]
    foreach ($file in $Files) {
        $lines = @(Get-Content -LiteralPath $file)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            foreach ($m in $tokenPattern.Matches($lines[$i])) {
                $leaf = Split-Path $m.Value -Leaf
                if ($Known.ContainsKey($leaf)) { continue }
                $context = $lines[$i]
                if ($i -gt 0) { $context += "`n" + $lines[$i - 1] }
                if ($context -match $excusePattern) { continue }
                $out.Add([pscustomobject]@{
                        File  = (Get-Relative $file).Replace('\', '/')
                        Token = $leaf
                        Line  = $i + 1
                    })
            }
        }
    }
    return $out
}

if ($Memory) {
    # ------------------------------------------------------------ memory mode
    $memoryRoot = Join-Path $RepoRoot '.claude/agent-memory'
    if (-not (Test-Path -LiteralPath $memoryRoot)) {
        Write-Host "assert-script-references: cannot verify - no agent memory at .claude/agent-memory" -ForegroundColor Yellow
        exit 2
    }
    $knownScripts = Get-KnownScriptNames
    $memoryFiles = @(Get-ChildItem -LiteralPath $memoryRoot -Recurse -File -Filter *.md | ForEach-Object { $_.FullName })
    $unresolved = New-Object System.Collections.Generic.List[string]
    foreach ($finding in (Find-UnresolvedTokens -Files $memoryFiles -Known $knownScripts)) {
        $unresolved.Add(("{0}: {1}" -f $finding.File, $finding.Token))
    }
    if ($Report -or -not $Quiet) {
        foreach ($u in $unresolved) { Write-Host "  unresolved: $u" }
    }
    if ($Report) {
        Write-Host "assert-script-references (memory): $($unresolved.Count) unresolved token(s) - report mode."
        exit 0
    }
    if ($unresolved.Count -gt 0) {
        Write-Host "assert-script-references (memory): FAIL - $($unresolved.Count) memory path(s) resolve to no script." -ForegroundColor Red
        Write-Host "  Mark a deliberate one with 'Historical:' or 'External:' on its line or the line above."
        exit 1
    }
    Write-Host "assert-script-references (memory): PASS - every memory script path resolves." -ForegroundColor Green
    exit 0
}

if ($Docs) {
    # ------------------------------------------------------------ docs mode
    $knownScripts = Get-KnownScriptNames
    $docFiles = New-Object System.Collections.Generic.List[string]
    foreach ($root in $docRoots) {
        $full = Join-Path $RepoRoot ($root -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path -LiteralPath $full)) { continue }
        foreach ($f in Get-ChildItem -LiteralPath $full -Recurse -File) {
            if ($docExtensions -notcontains $f.Extension.ToLowerInvariant()) { continue }
            $rel = (Get-Relative $f.FullName).Replace('\', '/')
            if (Test-Excluded $rel) { continue }
            $skip = $false
            foreach ($x in $docExclusions) { if ($rel -like "$x/*" -or $rel -ieq $x) { $skip = $true; break } }
            if ($skip) { continue }
            $docFiles.Add($f.FullName)
        }
    }
    foreach ($name in $docRootFiles) {
        $full = Join-Path $RepoRoot $name
        if (Test-Path -LiteralPath $full) { $docFiles.Add($full) }
    }
    if ($docFiles.Count -eq 0) {
        Write-Host "assert-script-references: cannot verify - the document corpus is empty" -ForegroundColor Yellow
        exit 2
    }

    $entries = [ordered]@{}
    foreach ($finding in (Find-UnresolvedTokens -Files $docFiles -Known $knownScripts)) {
        $key = "{0} :: {1}" -f $finding.File, $finding.Token
        if (-not $entries.Contains($key)) { $entries[$key] = $finding }
    }

    # A .ps1 in the changed set widens the judgement back to the whole corpus: renaming or deleting
    # a script breaks the documents that name it, and none of them is in the changed set.
    # Callers pass the set either as an array or as one comma-joined string - post-change.ps1 hands
    # every scoped gate the joined form, which binds to a [string[]] parameter as a single element.
    $normChanged = @($ChangedFiles |
            Where-Object { $_ } |
            ForEach-Object { $_ -split ',' } |
            ForEach-Object { $_.Trim().Replace('\', '/') -replace '^\./', '' } |
            Where-Object { $_ })
    $scoped = $normChanged.Count -gt 0 -and -not ($normChanged | Where-Object { $_ -match '(?i)\.ps1$' })
    $judged = @($entries.Keys)
    if ($scoped) {
        $judged = @($entries.Keys | Where-Object { $normChanged -contains $entries[$_].File })
    }

    if (-not $Quiet -or $Report) {
        foreach ($key in $judged) { Write-Host ("  phantom: {0}  (line {1})" -f $key, $entries[$key].Line) }
    }
    if ($Report) {
        Write-Host "assert-script-references (docs): $($entries.Count) unresolved reference(s) in $($docFiles.Count) document(s) - report mode."
        exit 0
    }

    if (-not (Test-Path -LiteralPath $docsBaselinePath)) {
        Write-Host "assert-script-references: cannot verify - baseline file missing: $docsBaselinePath" -ForegroundColor Yellow
        Write-Host "  Create it from: assert-script-references.ps1 -Docs -Report"
        exit 2
    }
    $docsBaseline = @{}
    foreach ($line in (Get-Content -LiteralPath $docsBaselinePath)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $docsBaseline[$trimmed] = $true
    }

    $new = @($judged | Where-Object { -not $docsBaseline.ContainsKey($_) })
    # Only an unscoped run has seen the whole corpus, so only it may claim a baseline line is dead.
    if (-not $scoped -and -not $Quiet) {
        foreach ($line in ($docsBaseline.Keys | Sort-Object)) {
            if (-not $entries.Contains($line)) { Write-Host "  prune (no longer found): $line" -ForegroundColor DarkGray }
        }
    }

    if ($new.Count -gt 0) {
        Write-Host ("assert-script-references (docs): FAIL - {0} document reference(s) name a script that does not exist." -f $new.Count) -ForegroundColor Red
        foreach ($key in $new) { Write-Host ("  {0}  (line {1})" -f $key, $entries[$key].Line) -ForegroundColor Red }
        Write-Host "  Fix the reference, or say so on its line: a script outside this repository is marked 'External:', a retired one 'Historical:'."
        exit 1
    }

    $scopeNote = if ($scoped) { " in the changed set" } else { "" }
    Write-Host ("assert-script-references (docs): PASS - no new phantom reference{0} ({1} baselined, {2} documents)." -f `
            $scopeNote, $docsBaseline.Count, $docFiles.Count) -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- build the corpus
$mentions = @{}
foreach ($name in $scripts.Keys) { $mentions[$name] = New-Object System.Collections.Generic.List[string] }
$namePattern = [regex]'(?i)[\w.][\w.\-]*\.ps1'

function Get-CorpusFiles {
    param([string[]] $Roots, [string[]] $ExtraFiles = @(), [string[]] $Skip = @())
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($root in $Roots) {
        $full = Join-Path $RepoRoot ($root -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path -LiteralPath $full)) { continue }
        foreach ($f in Get-ChildItem -LiteralPath $full -Recurse -File) {
            if ($corpusExtensions -notcontains $f.Extension.ToLowerInvariant()) { continue }
            $rel = (Get-Relative $f.FullName).Replace('\', '/')
            if (Test-Excluded $rel) { continue }
            $skipped = $false
            foreach ($s in $Skip) { if ($rel -like "$s/*" -or $rel -ieq $s) { $skipped = $true; break } }
            if ($skipped) { continue }
            $out.Add($f.FullName)
        }
    }
    foreach ($name in $ExtraFiles) {
        $full = Join-Path $RepoRoot $name
        if (Test-Path -LiteralPath $full) { $out.Add($full) }
    }
    return $out
}

function Add-Mentions {
    param([System.Collections.Generic.List[string]] $Files, [hashtable] $Into)
    foreach ($file in $Files) {
        $text = [IO.File]::ReadAllText($file)
        if ($text -notmatch '(?i)\.ps1') { continue }
        $rel = Get-Relative $file
        $seen = @{}
        foreach ($m in $namePattern.Matches($text)) {
            $leaf = Split-Path $m.Value -Leaf
            if (-not $Into.ContainsKey($leaf)) { continue }
            if ($seen.ContainsKey($leaf)) { continue }
            $seen[$leaf] = $true
            $Into[$leaf].Add($rel)
        }
    }
}

$historical = @{}
foreach ($name in $scripts.Keys) { $historical[$name] = New-Object System.Collections.Generic.List[string] }

$liveFiles = Get-CorpusFiles -Roots ($liveRoots + @('dev')) `
    -ExtraFiles ($rootFiles + @('PLAN/RELEASE_QUEUE.md', 'PLAN/RELEASE_READY.md')) `
    -Skip $devLiveExclusions
$historicalFiles = Get-CorpusFiles -Roots $historicalRoots -ExtraFiles @('dev/CHANGELOG.md')

Add-Mentions -Files $liveFiles -Into $mentions
Add-Mentions -Files $historicalFiles -Into $historical

# ---------------------------------------------------------------- judge
$unreferenced = New-Object System.Collections.Generic.List[string]
$excused = New-Object System.Collections.Generic.List[string]

foreach ($name in ($scripts.Keys | Sort-Object)) {
    $ownPaths = $scripts[$name]
    $others = @($mentions[$name] | Where-Object { $ownPaths -notcontains $_ })
    if ($others.Count -gt 0) { continue }

    # A Pester suite is reached by discovery, not by name: its runner globs *.Tests.ps1 in its own
    # directory, so no file ever writes the suite's filename. Naming each one would be the only way
    # to satisfy a by-name check, which is a worse convention than the one already in use.
    $byConvention = $false
    foreach ($own in $ownPaths) {
        if ($own -notmatch '(?i)\.Tests\.ps1$') { continue }
        $dir = Split-Path (Join-Path $RepoRoot $own) -Parent
        if (Test-Path -LiteralPath (Join-Path $dir 'Run-Tests.ps1')) { $byConvention = $true }
    }
    if ($byConvention) { continue }

    $declared = $false
    foreach ($own in $ownPaths) {
        $text = [IO.File]::ReadAllText((Join-Path $RepoRoot $own))
        if ($text -match '(?im)^\s*#?\s*Manual tool:\s*\S+') { $declared = $true }
    }
    if ($declared) {
        $excused.Add(($ownPaths -join ', '))
        continue
    }
    $onlyHistory = @($historical[$name] | Where-Object { $ownPaths -notcontains $_ })
    $suffix = if ($onlyHistory.Count -gt 0) { "  (mentioned only historically, e.g. $($onlyHistory[0]))" } else { '' }
    $unreferenced.Add((($ownPaths -join ', ') + $suffix))
}

if (-not $Quiet) {
    foreach ($e in $excused) { Write-Host "  excused (manual tool): $e" -ForegroundColor DarkGray }
    foreach ($u in $unreferenced) { Write-Host "  unreferenced: $u" -ForegroundColor Yellow }
}

if ($Report) {
    Write-Host "assert-script-references: $($unreferenced.Count) unreferenced, $($excused.Count) excused, $($scripts.Count) scripts, $($liveFiles.Count) live corpus files, $($historicalFiles.Count) historical - report mode."
    exit 0
}

if (-not (Test-Path -LiteralPath $baselinePath)) {
    Write-Host "assert-script-references: cannot verify - baseline file missing: $baselinePath" -ForegroundColor Yellow
    Write-Host "  Create it with the current count from: assert-script-references.ps1 -Report"
    exit 2
}
$baseline = [int](Get-Content -LiteralPath $baselinePath -Raw).Trim()

if ($unreferenced.Count -gt $baseline) {
    Write-Host ("assert-script-references: FAIL - {0} unreferenced script(s), baseline {1}." -f $unreferenced.Count, $baseline) -ForegroundColor Red
    Write-Host "  Delete it, or declare it with a 'Manual tool: <reason>' line in its comment-based help."
    exit 1
}

Write-Host ("assert-script-references: PASS - {0} unreferenced (baseline {1}), {2} excused." -f `
    $unreferenced.Count, $baseline, $excused.Count) -ForegroundColor Green
exit 0
