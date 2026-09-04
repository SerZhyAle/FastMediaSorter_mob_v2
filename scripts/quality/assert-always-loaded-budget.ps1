#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: every text file that is injected into EVERY request must stay under a ceiling
    that only ever goes down (S2517).

.DESCRIPTION
    A file on the always-loaded page is not billed to the session that needed it. It is billed
    to every request of every session, forever, whether or not anything in it was relevant.
    S2513 measured what that costs on 2026-09-04: the fixed preamble is 75702 tokens per
    request and 37.8% of all billed `cache_read`, against 23.3% five weeks earlier - and the
    share grew from both ends, the preamble gaining 18% while the average request shrank 27%
    because the earlier accumulation tickets worked. Inside the preamble the growth is
    concentrated in one file: `CLAUDE.md` went from 28.6 KB to 67251 B in 35 days, ~770 B/day,
    which is 24.6% of the floor and 9.3% of the whole bill.

    This gate exists because the alternatives were both tried and both failed. A written rule
    saying "keep the page short" is an ungated rule, and the 2026-07-31 process audit measured
    ungated rules holding at 1-8% against ~99% for gated ones. A one-off cleanup was done -
    S1340 compressed `CLAUDE.md` on 2026-08-01 - and within a month the file was double what
    it had been before that cleanup. The same story ran twice more on `MEMORY.md`, whose two
    manual compactions were each undone within a week at ~1.1 KB/day, and there the regrowth
    stopped only when `assert-memory-budget.ps1` started judging it. Three cleanups, three
    reversals, one ratchet that held: that is the whole argument for this file.

    Judged files come from `always-loaded-budget-baseline.txt` beside this script, one per
    line, because the reasoning is about a PROPERTY - being present in every request - and not
    about a name. A second such file already exists (`MEMORY.md`, judged by its own gate for
    its own extra correctness checks), and a third will appear the moment another always-on
    surface is added.

    The refusal names the destination as well as the overshoot, and that is not politeness. A
    ceiling that says only "too big" leaves exactly one action available - deleting a rule -
    and S2517 ADR-4 forbids that: the subject of the budget is the WEIGHT OF THE RATIONALE,
    not the number of rules. A rule dropped to fit a ceiling silently returns the exact cost
    the gates exist to avoid paying.

.PARAMETER Path
    Judge one file instead of the whole baseline. Repo-relative or absolute. Used by the probe
    runs in the tests and by a caller that knows which file it just edited.

.PARAMETER BaselineFile
    Override the baseline. Defaults to `always-loaded-budget-baseline.txt` beside this script.

.PARAMETER Gate
    Exit 1 when any judged file is above its ceiling. Without it the run only reports.

.PARAMETER UpdateBaseline
    Lower every ceiling that the current tree beats, and refuse to raise one. Seeds an entry
    for a judged file the baseline does not carry yet.

.PARAMETER Quiet
    Print the failing and warning lines only. The batch runner passes this.

.PARAMETER RepoRoot
    Judge a different tree. Defaults to this repository; the contract suite points it at a
    throwaway copy so the failure paths can be exercised without breaking the live rules files.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every judged file is at or below its ceiling, or a report-only run, or a successful
         `-UpdateBaseline`.
      1  `-Gate` and at least one file is above its ceiling; or `-UpdateBaseline` asked to RAISE
         a ceiling.
      2  cannot verify - the baseline is missing or unparseable, or a file it names is gone.

    Baseline line format (`#` starts a comment):
      <repo-relative path>|<ceiling bytes>|<stretch bytes>|<where the text goes instead>

    Two more checks ride along (S2521), because the lever the refusal above points at is
    `.claude/rules/*.md` with `paths:` frontmatter - loaded only when Claude reads a matching
    file - and that lever has two silent failure shapes. A rules file WITHOUT `paths:` is
    loaded at launch like `CLAUDE.md` itself, so it must be listed in the baseline or the split
    has moved the cost instead of removing it; and a `paths` entry whose literal prefix (the
    text before the first wildcard) names nothing on disk is a rule that never loads, which no
    session would notice. Both exit 1 under -Gate and print as warnings otherwise.

    Bytes are measured on the INJECTED text: block-level HTML comments (`<!-- .. -->`) are
    stripped by Claude Code before a memory file enters context, so they are stripped here too.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-always-loaded-budget.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-always-loaded-budget.ps1 -UpdateBaseline
#>
[CmdletBinding()]
param(
    [string]$Path,
    [string]$BaselineFile,
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$Quiet,
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not $BaselineFile) { $BaselineFile = Join-Path $PSScriptRoot 'always-loaded-budget-baseline.txt' }

if (-not (Test-Path -LiteralPath $BaselineFile -PathType Leaf)) {
    Write-Error "assert-always-loaded-budget: baseline not found at $BaselineFile - nothing was judged." -ErrorAction Continue
    exit 2
}

# --- parse the baseline ------------------------------------------------------------------
$entries = [System.Collections.Generic.List[object]]::new()
$lineNo = 0
foreach ($raw in Get-Content -LiteralPath $BaselineFile) {
    $lineNo++
    $line = $raw.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith('#')) { continue }
    $parts = $line -split '\|'
    if ($parts.Count -lt 3) {
        Write-Error ("assert-always-loaded-budget: baseline line {0} is malformed - expected 'path|ceiling|stretch|hint', got '{1}'." -f $lineNo, $line) -ErrorAction Continue
        exit 2
    }
    $ceiling = 0
    $stretch = 0
    if (-not [int]::TryParse($parts[1].Trim(), [ref]$ceiling) -or -not [int]::TryParse($parts[2].Trim(), [ref]$stretch)) {
        Write-Error ("assert-always-loaded-budget: baseline line {0} carries a non-numeric ceiling or stretch." -f $lineNo) -ErrorAction Continue
        exit 2
    }
    $entries.Add([pscustomobject]@{
            RelPath = $parts[0].Trim()
            Ceiling = $ceiling
            Stretch = $stretch
            Hint    = if ($parts.Count -ge 4) { $parts[3].Trim() } else { '' }
            Line    = $lineNo
        })
}

if ($entries.Count -eq 0) {
    Write-Error "assert-always-loaded-budget: the baseline carries no entries - nothing was judged." -ErrorAction Continue
    exit 2
}

# --- narrow to one file when asked -------------------------------------------------------
if ($Path) {
    $wanted = if ([System.IO.Path]::IsPathRooted($Path)) {
        [System.IO.Path]::GetRelativePath($repoRoot, $Path) -replace '\\', '/'
    }
    else { $Path -replace '\\', '/' }
    $narrowed = @($entries | Where-Object { $_.RelPath -ieq $wanted })
    if ($narrowed.Count -eq 0) {
        Write-Error ("assert-always-loaded-budget: '{0}' is not an always-loaded file - the baseline does not list it." -f $wanted) -ErrorAction Continue
        exit 2
    }
    $entries = [System.Collections.Generic.List[object]]::new()
    foreach ($n in $narrowed) { $entries.Add($n) }
}

# --- measure -----------------------------------------------------------------------------
function Measure-InjectedBytes([string]$fullPath) {
    # Claude Code strips block-level HTML comments before a memory file enters context, so a
    # maintainer note inside one costs no tokens and must not count here either.
    $text = [System.IO.File]::ReadAllText($fullPath)
    $stripped = [regex]::Replace($text, '(?s)<!--.*?-->', '')
    return [System.Text.Encoding]::UTF8.GetByteCount($stripped)
}

$measured = [System.Collections.Generic.List[object]]::new()
foreach ($e in $entries) {
    $full = Join-Path $repoRoot $e.RelPath
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Write-Error ("assert-always-loaded-budget: '{0}' is listed in the baseline but does not exist - either restore it or drop its line." -f $e.RelPath) -ErrorAction Continue
        exit 2
    }
    $bytes = Measure-InjectedBytes $full
    $measured.Add([pscustomobject]@{
            RelPath   = $e.RelPath
            Bytes     = [int]$bytes
            Ceiling   = $e.Ceiling
            Stretch   = $e.Stretch
            Hint      = $e.Hint
            Overshoot = [int]$bytes - $e.Ceiling
        })
}

# --- path-scoped rules: the two silent failure shapes (S2521) ----------------------------
$ruleFailures = [System.Collections.Generic.List[string]]::new()
$rulesDir = Join-Path $repoRoot '.claude/rules'
if (-not $Path -and (Test-Path -LiteralPath $rulesDir -PathType Container)) {
    $listed = @($entries | ForEach-Object { $_.RelPath.ToLowerInvariant() })
    foreach ($rule in Get-ChildItem -LiteralPath $rulesDir -Recurse -File -Filter '*.md') {
        $rel = [System.IO.Path]::GetRelativePath($repoRoot, $rule.FullName) -replace '\\', '/'
        $text = [System.IO.File]::ReadAllText($rule.FullName)
        $fm = [regex]::Match($text, '(?s)\A---\r?\n(?<body>.*?)\r?\n---')
        $patterns = @()
        if ($fm.Success) {
            $inPaths = $false
            foreach ($line in ($fm.Groups['body'].Value -split '\r?\n')) {
                if ($line -match '^paths:\s*$') { $inPaths = $true; continue }
                if ($line -match '^paths:\s*\[(?<inline>.*)\]\s*$') {
                    $patterns += @($matches['inline'] -split ',' | ForEach-Object { $_.Trim().Trim('"', "'") } | Where-Object { $_ })
                    continue
                }
                if ($inPaths) {
                    if ($line -match '^\s*-\s*(?<p>.+?)\s*$') { $patterns += $matches['p'].Trim('"', "'"); continue }
                    if ($line -match '^\S') { $inPaths = $false }
                }
            }
        }
        if ($patterns.Count -eq 0) {
            if ($listed -notcontains $rel.ToLowerInvariant()) {
                $ruleFailures.Add(("{0} carries no 'paths:' frontmatter, so Claude Code loads it at launch like CLAUDE.md - either scope it with 'paths:' or list it in the baseline so its bytes are judged." -f $rel))
            }
            continue
        }
        foreach ($pat in $patterns) {
            $prefix = [regex]::Match($pat, '^[^*?\[{]*').Value
            if ($prefix.EndsWith('/')) { $prefix = $prefix.TrimEnd('/') }
            if ([string]::IsNullOrWhiteSpace($prefix)) { continue }
            if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $prefix))) {
                $ruleFailures.Add(("{0}: paths entry '{1}' starts with '{2}', which does not exist - this rule can never load; fix the glob." -f $rel, $pat, $prefix))
            }
        }
    }
}
if ($ruleFailures.Count -gt 0) {
    foreach ($f in $ruleFailures) { Write-Host ("always-loaded: rules: {0}" -f $f) -ForegroundColor $(if ($Gate -or $UpdateBaseline) { 'Red' } else { 'Yellow' }) }
    if ($Gate -or $UpdateBaseline) {
        Write-Error ("assert-always-loaded-budget: FAIL - {0} path-scoped rule problem(s) under .claude/rules/ (see lines above)." -f $ruleFailures.Count) -ErrorAction Continue
        exit 1
    }
}

# --- ratchet -----------------------------------------------------------------------------
if ($UpdateBaseline) {
    $raised = @($measured | Where-Object { $_.Bytes -gt $_.Ceiling })
    if ($raised.Count -gt 0) {
        foreach ($r in $raised) {
            Write-Host ("  {0}: {1} B is ABOVE the {2} B ceiling by {3} B." -f $r.RelPath, $r.Bytes, $r.Ceiling, $r.Overshoot) -ForegroundColor Red
        }
        Write-Error ("assert-always-loaded-budget: refusing to RAISE a ceiling for {0} file(s). A ceiling goes down on a run that passed and never up - that is the whole mechanism, and three manual cleanups were undone in the month before it existed." -f $raised.Count) -ErrorAction Continue
        exit 1
    }
    $text = Get-Content -LiteralPath $BaselineFile -Raw
    $changed = 0
    foreach ($m in $measured) {
        if ($m.Bytes -ge $m.Ceiling) { continue }
        $escaped = [regex]::Escape($m.RelPath)
        $pattern = "(?m)^(\s*$escaped\s*\|\s*)\d+(\s*\|)"
        $text = [regex]::Replace($text, $pattern, { param($mm) "$($mm.Groups[1].Value)$($m.Bytes)$($mm.Groups[2].Value)" })
        Write-Host ("always-loaded budget ratcheted DOWN: {0} {1} -> {2} B (stretch {3})" -f $m.RelPath, $m.Ceiling, $m.Bytes, $m.Stretch)
        $changed++
    }
    if ($changed -gt 0) { Set-Content -LiteralPath $BaselineFile -Value $text.TrimEnd("`r", "`n") -NoNewline:$false }
    else { Write-Host "always-loaded budget unchanged - no file is smaller than its ceiling." }
    exit 0
}

# --- report ------------------------------------------------------------------------------
# A stretch at or above the ceiling means no target has been set below it yet - S2517 section 6
# item 4 defers that number until after the first move, because before it any target is a guess.
$over = @($measured | Where-Object { $_.Overshoot -gt 0 })
$near = @($measured | Where-Object { $_.Overshoot -le 0 -and $_.Stretch -lt $_.Ceiling -and $_.Bytes -gt $_.Stretch })

foreach ($m in $measured) {
    if ($m.Overshoot -gt 0) {
        Write-Host ("always-loaded: {0} | {1} B | ceiling {2} B | OVER by {3} B" -f $m.RelPath, $m.Bytes, $m.Ceiling, $m.Overshoot) -ForegroundColor Red
        if ($m.Hint) { Write-Host ("    move {0} B of rationale to: {1}" -f $m.Overshoot, $m.Hint) -ForegroundColor Red }
    }
    elseif ($m.Stretch -ge $m.Ceiling) {
        if (-not $Quiet) {
            Write-Host ("always-loaded: {0} | {1} B | ceiling {2} B | no stretch target set below the ceiling yet" -f $m.RelPath, $m.Bytes, $m.Ceiling) -ForegroundColor Green
        }
    }
    elseif ($m.Bytes -gt $m.Stretch) {
        if (-not $Quiet) {
            Write-Host ("always-loaded: {0} | {1} B | ceiling {2} B | {3} B above the {4} B stretch target" -f $m.RelPath, $m.Bytes, $m.Ceiling, ($m.Bytes - $m.Stretch), $m.Stretch) -ForegroundColor Yellow
        }
    }
    elseif (-not $Quiet) {
        Write-Host ("always-loaded: {0} | {1} B | ceiling {2} B | at or below the stretch target" -f $m.RelPath, $m.Bytes, $m.Ceiling) -ForegroundColor Green
    }
}

if ($Gate -and $over.Count -gt 0) {
    $lines = foreach ($o in $over) {
        "    {0}: {1} B, over by {2} B -> {3}" -f $o.RelPath, $o.Bytes, $o.Overshoot, ($o.Hint ? $o.Hint : 'the mechanism doc that already describes this rule')
    }
    $why = @(
        "assert-always-loaded-budget: FAIL - {0} always-loaded file(s) are over their ceiling." -f $over.Count
        ($lines -join "`n")
        ""
        "The lever: a rule's mechanism and incident text goes to .claude/rules/<topic>.md with"
        "'paths:' frontmatter, which Claude Code loads only when it reads a matching file (S2521);"
        "the statement and its number stay on the page. Not an @import - that loads at launch."
        ""
        "Why this refuses instead of warning. An always-loaded file is billed on EVERY request of"
        "every session, not on the session that needed it. Measured 2026-09-04 (S2513): the fixed"
        "preamble is 75702 tokens per request and 37.8% of all billed cache_read, up from 23.3% five"
        "weeks earlier; CLAUDE.md alone grew 2.35x in 35 days (~770 B/day) to 24.6% of that floor and"
        "9.3% of the entire bill. The written-rule version of this was tried and holds at 1-8%; the"
        "one-off-cleanup version was tried three times - S1340 on CLAUDE.md, twice on MEMORY.md - and"
        "was undone every time, within a month and within a week respectively."
        ""
        "What to do, in order:"
        "  1. Move the RATIONALE, not the rule. Incident narrative - dates, measured values, ticket"
        "     ids - belongs in the refusal text of the gate that enforces the rule, because that text"
        "     is read exactly when the rule is broken and costs nothing on every other request."
        "  2. If the rule has no mechanical gate, its rationale IS its enforcement mechanism. Leave"
        "     it alone and find the bytes elsewhere."
        "  3. Never delete or weaken a rule to fit the ceiling (S2517 ADR-4). The subject of this"
        "     budget is the weight of the rationale, not the number of rules."
        "  4. Once the file is smaller, run this script with -UpdateBaseline so the ceiling follows"
        "     it down."
    ) -join "`n"
    Write-Error $why -ErrorAction Continue
    exit 1
}

if (-not $Quiet -or $near.Count -gt 0) {
    Write-Host ("assert-always-loaded-budget: PASS ({0} file(s) within budget, {1} above the stretch target)." -f $measured.Count, $near.Count) -ForegroundColor Green
}
exit 0
