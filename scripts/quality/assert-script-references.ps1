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

    HOW IT JUDGES. Every project-authored .ps1 under the script roots is collected BY PATH, then
    every file in the reference corpus is read once and scanned for .ps1-shaped tokens. Each token
    is resolved into the file it names by the ladder in lib/script-reference-resolution.ps1, and a
    script is UNREFERENCED when no corpus file other than itself resolves to it. A script quoting
    its own name in help text does not keep itself alive.

    THE KEY IS A PATH, NOT A FILE NAME (S2124). It was a name until 2026-08-27, and the tree holds
    37 files called Run-Tests.ps1: they shared one entry, so three comments naming that bare word
    marked all 37 referenced, none of which is called from anywhere. The blindness was structural -
    any group of files sharing a name went unjudged the moment one of them was mentioned. Under the
    path key the verdict rose from 30 to 58, and the 28 added files are one homogeneous class.

    AN AMBIGUOUS BARE NAME IS NOT EVIDENCE. A token that is a bare file name carried by several
    scripts names none of them. -Report says how many scripts are held up by nothing better, which
    is the number that was silently zero before S2124.

    FILES THAT LIST SCRIPTS ARE EXCLUDED, AND THAT IS NOT A SETTING. docs/SCRIPT_CHEATSHEET.md is
    generated from every script in the repository, and the two baselines beside this script are
    lists of script paths it writes itself. A corpus containing any of them reports zero
    unreferenced scripts forever and the gate becomes a check that cannot fail - which is exactly
    what happened the moment the main baseline stopped being a count.

    THE BASELINE IS A LIST, NOT A COUNT (S2124). script-reference-baseline.txt holds one script
    path per known orphan, so repairing one cannot free a slot the next one occupies silently. This
    is only possible under the path key: with a name key the 37 Run-Tests.ps1 could not be told
    apart on a line. A baseline line matching nothing is printed as a prune hint, not a failure -
    the same contract the docs baseline has had since S1979.

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
      0 - every finding is in the baseline, or -Report was given
      1 - an unreferenced script that is not in the baseline appeared, a memory path resolves to
          nothing, or a document names a script that does not exist and is not in the docs baseline
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

. (Join-Path $PSScriptRoot 'lib/script-reference-resolution.ps1')

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
# Files that list scripts rather than call them. Including one keeps every script it names alive,
# which turns this gate into a check that cannot fail. The cheatsheet is generated from every
# script in the repository; the two baselines are lists of script paths this gate itself writes -
# once the main baseline became a list of paths (S2124) it vouched for all 58 of its own entries
# and the verdict went to zero.
$corpusExclusions = @(
    'docs/SCRIPT_CHEATSHEET.md',
    'scripts/quality/script-reference-baseline.txt',
    'scripts/quality/doc-script-reference-baseline.txt'
)
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
    # A git worktree under .claude/worktrees is another agent's isolated checkout of this same
    # repository, not this tree's content. Every document in it is a copy, so scanning it doubles
    # every finding and judges files the working tree is not responsible for. Observed 2026-08-27
    # while closing S2194: a sibling agent's worktree appeared mid-ticket and added 505 phantom
    # document references, failing the closure of a change that had touched none of them.
    if ($Relative.Replace('\', '/') -like '.claude/worktrees/*') { return $true }
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
# Keyed by path, not by file name (S2124). The tree carries 37 files called Run-Tests.ps1; under a
# name key they shared one entry, and one comment naming that word marked all 37 referenced.
$scriptPaths = New-Object System.Collections.Generic.List[string]
foreach ($root in $scriptRoots) {
    $full = Join-Path $RepoRoot ($root -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Host "assert-script-references: cannot verify - script root missing: $root" -ForegroundColor Yellow
        exit 2
    }
    foreach ($f in Get-ChildItem -LiteralPath $full -Recurse -File -Filter *.ps1) {
        $rel = Get-Relative $f.FullName
        if (Test-Excluded $rel) { continue }
        $scriptPaths.Add($rel.Replace('\', '/'))
    }
}
foreach ($name in @('a.ps1')) {
    if (Test-Path -LiteralPath (Join-Path $RepoRoot $name)) { $scriptPaths.Add($name) }
}
$scriptIndex = New-ScriptPathIndex -RelativePaths $scriptPaths.ToArray()

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
# Two ledgers per script. PRECISE holds mentions that name this exact file; AMBIGUOUS holds bare
# names several scripts carry, which are evidence about none of them (S2124) but are still counted
# so -Report can say how many files are held up by nothing better.
$mentions = @{}
$ambiguous = @{}
foreach ($p in $scriptIndex.ByPath.Keys) {
    $mentions[$p] = New-Object System.Collections.Generic.List[string]
    $ambiguous[$p] = New-Object System.Collections.Generic.List[string]
}
# Path-shaped, unlike the reverse modes' token pattern only in that both separators are kept: the
# resolver needs the directory segments the old name-only pattern threw away.
$namePattern = [regex]'(?i)[\w.][\w.\-/\\]*\.ps1'

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
    param(
        [System.Collections.Generic.List[string]] $Files,
        [hashtable] $Into,
        [hashtable] $IntoAmbiguous
    )
    foreach ($file in $Files) {
        $text = [IO.File]::ReadAllText($file)
        if ($text -notmatch '(?i)\.ps1') { continue }
        $rel = (Get-Relative $file).Replace('\', '/')
        $slash = $rel.LastIndexOf('/')
        $dir = if ($slash -lt 0) { '' } else { $rel.Substring(0, $slash) }
        $seen = @{}
        foreach ($m in $namePattern.Matches($text)) {
            if ($seen.ContainsKey($m.Value)) { continue }
            $seen[$m.Value] = $true
            $resolved = Resolve-ScriptTokenPaths -Token $m.Value -MentionDirectory $dir -Index $scriptIndex
            foreach ($p in $resolved.Paths) {
                # A script quoting its own name in help text does not keep itself alive.
                if ($p -ieq $rel) { continue }
                if ($resolved.Ambiguous) { $IntoAmbiguous[$p].Add($rel) } else { $Into[$p].Add($rel) }
            }
        }
    }
}

$historical = @{}
$historicalAmbiguous = @{}
foreach ($p in $scriptIndex.ByPath.Keys) {
    $historical[$p] = New-Object System.Collections.Generic.List[string]
    $historicalAmbiguous[$p] = New-Object System.Collections.Generic.List[string]
}

$liveFiles = Get-CorpusFiles -Roots ($liveRoots + @('dev')) `
    -ExtraFiles ($rootFiles + @('PLAN/RELEASE_QUEUE.md', 'PLAN/RELEASE_READY.md')) `
    -Skip $devLiveExclusions
$historicalFiles = Get-CorpusFiles -Roots $historicalRoots -ExtraFiles @('dev/CHANGELOG.md')

Add-Mentions -Files $liveFiles -Into $mentions -IntoAmbiguous $ambiguous
Add-Mentions -Files $historicalFiles -Into $historical -IntoAmbiguous $historicalAmbiguous

# ---------------------------------------------------------------- judge
$unreferenced = New-Object System.Collections.Generic.List[string]
$unreferencedPaths = New-Object System.Collections.Generic.List[string]
$excused = New-Object System.Collections.Generic.List[string]

$heldByAmbiguityOnly = 0

foreach ($own in ($scriptIndex.ByPath.Keys | Sort-Object)) {
    if ($mentions[$own].Count -gt 0) { continue }

    # A Pester suite is reached by discovery, not by name: its runner globs *.Tests.ps1 in its own
    # directory, so no file ever writes the suite's filename. Naming each one would be the only way
    # to satisfy a by-name check, which is a worse convention than the one already in use.
    if ($own -match '(?i)\.Tests\.ps1$') {
        $dir = Split-Path (Join-Path $RepoRoot $own) -Parent
        if (Test-Path -LiteralPath (Join-Path $dir 'Run-Tests.ps1')) { continue }
    }

    # S2122: a suite entry point is reached the same way, one level up. Since the run site exists,
    # scripts/quality/run-script-suites.ps1 discovers every Run-Tests.ps1 under a <name>.tests/ or a
    # <name>/tests/ directory by walking the tree, so nothing writes these filenames either. Before
    # the run site they were genuinely unreferenced and 27 of them sit in the baseline saying so;
    # that is now the wrong answer, and excusing them here is what makes "wired" and "baselined as
    # dead weight" different states again. Only the entry point qualifies - a helper dropped beside
    # it still has to be reached by name from the suite.
    if ($own -match '(?i)(^|/)[^/]+(\.tests|/tests)/[Rr]un-[Tt]ests\.ps1$') { continue }

    $text = [IO.File]::ReadAllText((Join-Path $RepoRoot $own))
    if ($text -match '(?im)^\s*#?\s*Manual tool:\s*\S+') {
        $excused.Add($own)
        continue
    }
    if ($ambiguous[$own].Count -gt 0) { $heldByAmbiguityOnly++ }
    $onlyHistory = @($historical[$own])
    $suffix = if ($onlyHistory.Count -gt 0) { "  (mentioned only historically, e.g. $($onlyHistory[0]))" } else { '' }
    $unreferencedPaths.Add($own)
    $unreferenced.Add($own + $suffix)
}

if (-not $Quiet) {
    foreach ($e in $excused) { Write-Host "  excused (manual tool): $e" -ForegroundColor DarkGray }
    foreach ($u in $unreferenced) { Write-Host "  unreferenced: $u" -ForegroundColor Yellow }
}

if ($Report) {
    Write-Host ("assert-script-references: {0} unreferenced ({1} held up today by nothing but an ambiguous bare name), {2} excused, {3} scripts, {4} live corpus files, {5} historical - report mode." -f `
            $unreferenced.Count, $heldByAmbiguityOnly, $excused.Count, $scriptIndex.ByPath.Count, $liveFiles.Count, $historicalFiles.Count)
    exit 0
}

if (-not (Test-Path -LiteralPath $baselinePath)) {
    Write-Host "assert-script-references: cannot verify - baseline file missing: $baselinePath" -ForegroundColor Yellow
    Write-Host "  Create it with the current paths from: assert-script-references.ps1 -Report"
    exit 2
}
# A list, not a count (S2124). Under a count, repairing one orphan frees a slot the next one
# occupies silently - the exact invisibility this gate was built against, and the reason the docs
# baseline beside it has been a list since S1979. A path key is what makes the list possible: with
# a name key the 37 files called Run-Tests.ps1 could not be told apart on a line.
$baselinePaths = @{}
foreach ($line in (Get-Content -LiteralPath $baselinePath)) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $baselinePaths[$trimmed.Replace('\', '/')] = $true
}

$new = @($unreferencedPaths | Where-Object { -not $baselinePaths.ContainsKey($_) })
$stale = @($baselinePaths.Keys | Where-Object { $unreferencedPaths -notcontains $_ } | Sort-Object)

if (-not $Quiet) {
    foreach ($s in $stale) { Write-Host "  baseline line no longer matches anything - prune it: $s" -ForegroundColor DarkGray }
}

if ($new.Count -gt 0) {
    Write-Host ("assert-script-references: FAIL - {0} unreferenced script(s) not in the baseline." -f $new.Count) -ForegroundColor Red
    foreach ($n in $new) { Write-Host "  new: $n" -ForegroundColor Red }
    Write-Host "  Delete it, wire it, or declare it with a 'Manual tool: <reason>' line in its comment-based help."
    Write-Host "  Baselining it instead is a decision to keep dead weight: $baselinePath"
    exit 1
}

Write-Host ("assert-script-references: PASS - {0} unreferenced, all baselined ({1} stale line(s)), {2} excused." -f `
        $unreferenced.Count, $stale.Count, $excused.Count) -ForegroundColor Green
exit 0
