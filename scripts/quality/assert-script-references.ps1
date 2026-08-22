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

.PARAMETER Gate
    Accepted for the fast-gate batch's uniform call shape; judging is already the default.

.PARAMETER Report
    List the findings and exit 0 regardless of the baseline. Use when deciding, not when gating.

.PARAMETER Memory
    Check agent-memory script paths instead of repository reference connectivity.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.NOTES
    Exit codes:
      0 - at or below the baseline, or -Report was given
      1 - above the baseline: an unreferenced script appeared, or a memory path resolves to nothing
      2 - cannot verify: a script root or the baseline file is missing
#>
[CmdletBinding()]
param(
    # Accepted for the fast-gate batch's uniform call shape. Judging against the baseline is
    # already this script's default, so the switch changes nothing - refusing it would make the
    # batch report a parameter error as a gate failure.
    [switch] $Gate,
    [switch] $Report,
    [switch] $Memory,
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

if ($Memory) {
    # ------------------------------------------------------------ memory mode
    $memoryRoot = Join-Path $RepoRoot '.claude/agent-memory'
    if (-not (Test-Path -LiteralPath $memoryRoot)) {
        Write-Host "assert-script-references: cannot verify - no agent memory at .claude/agent-memory" -ForegroundColor Yellow
        exit 2
    }
    # The leading dot matters: '.claude/hooks/x.ps1' is a real path and a token regex anchored on a
    # word character silently drops its first segment.
    $tokenPattern = [regex]'(?i)[\w.][\w.\-/\\]*\.ps1'
    $unresolved = New-Object System.Collections.Generic.List[string]
    foreach ($f in Get-ChildItem -LiteralPath $memoryRoot -Recurse -File -Filter *.md) {
        $lines = Get-Content -LiteralPath $f.FullName
        for ($i = 0; $i -lt $lines.Count; $i++) {
            foreach ($m in $tokenPattern.Matches($lines[$i])) {
                $token = $m.Value
                $leaf = Split-Path $token -Leaf
                if ($scripts.ContainsKey($leaf)) { continue }
                $context = $lines[$i]
                if ($i -gt 0) { $context += "`n" + $lines[$i - 1] }
                if ($context -match '(?i)\b(Historical|External|placeholder|example)\b') { continue }
                $unresolved.Add(("{0}: {1}" -f (Get-Relative $f.FullName), $token))
            }
        }
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
$corpusFiles = $liveFiles

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
