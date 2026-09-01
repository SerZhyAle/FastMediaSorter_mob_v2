#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: the always-loaded agent-memory index must stay small, and its files must not
    outlive what they describe (S1338).

.DESCRIPTION
    `MEMORY.md` is injected into EVERY turn of every session with this agent, so its size is
    billed against the whole corpus rather than against the session that needed it. It was
    manually compacted twice and both compactions were undone within a week, at a measured
    regrowth of ~1.1 KB/day. A budget that is not mechanical is not a budget.

    Only the index is judged for size. The other memory files are read on demand, so their
    bytes cost nothing per turn - deleting them saves nothing that is billed and loses the
    trap they record. That is why this gate has exactly one size rule.

    Three correctness checks ride along beside the size rule.
      - dead paths   - a memory file naming a repo path that no longer exists. ADVISORY: a path
                       in prose can be an illustrative example rather than a claim about the tree.
      - self-expiry  - a memory file whose own prose declares the condition under which it
                       should be removed ("delete this memory when ..", "this snapshot decays").
                       ADVISORY: only the reader can judge whether the stated condition is met.
      - broken links - a `[[target]]` cross-link that resolves to no other memory's frontmatter
                       `name:` value, even allowing the file-stem / kebab-stem / no-type-prefix
                       fallbacks a human author actually writes (S1345). FATAL under -Gate since
                       S2308: unlike the two above this admits no judgement call - the target
                       either exists or it does not - so its legitimate population is exactly
                       zero and a ratchet would only record debt nobody is going to pay. The
                       other two stay advisory precisely because they do produce false positives,
                       and a gate that fails on those trains the operator to bypass it.

    S2308 removed a fourth check - "references only dead tickets", every `Sxxxx` in the file
    being Archived or absent. A memory is written precisely so its lesson outlives the ticket
    that taught it, so ticket liveness measures the age of an anchor and not the decay of a
    claim. Measured 2026-09-01 over this corpus: it fired on 269 of the 342 ticket-anchored
    files and 3 of those were genuinely dead - about 1% precision - while printing 18090 B of
    the run's 21206 B output into the context of every closure that touched memory. Narrowing
    it did not rescue it (adding `type: project` plus unreachability reached 7%; a vanished
    `temp/` path reached 11%). Self-expiry replaced it because it was the one criterion that
    reached 100%, and it does so because the AUTHOR declares the expiry rather than the machine
    inferring it from an anchor.

.PARAMETER Path
    The memory index to judge. Defaults to the android-rd-specialist MEMORY.md.

.PARAMETER MaxBytes
    Hard ceiling. Above it the gate fails under -Gate. Omitted, the ceiling comes from
    `memory-budget-baseline.txt` - a ratchet, like every other count gate in this directory,
    lowered on a green run and never raised. The TARGET is 9000 B and the stretch is 6000 B;
    the ratchet exists because reaching 9000 B means dropping roughly half the pointers, and
    a pointer is what makes a memory file findable at all. Shrink it deliberately over time
    rather than paying for the whole cut in one unreviewed edit.

.PARAMETER StretchBytes
    Soft target. Between it and MaxBytes the gate warns. Default 6000.

.PARAMETER Gate
    Exit 1 when the index is above MaxBytes, or when any `[[link]]` resolves to nothing.
    Without it the run only reports.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  at or below MaxBytes with every link resolving, or a report-only run.
      1  -Gate and the index is above MaxBytes, or -Gate and a `[[link]]` is unresolvable.
      2  cannot verify - the index or the memory directory does not exist.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-memory-budget.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-memory-budget.ps1 -Path temp/probe/MEMORY.md -MaxBytes 100 -Gate
#>
[CmdletBinding()]
param(
    [string]$Path,
    [int]$MaxBytes = 0,
    [int]$StretchBytes = 6000,
    [int]$TargetBytes = 9000,
    [switch]$Gate,
    [switch]$UpdateBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Path) {
    $Path = Join-Path $repoRoot '.claude/agent-memory/android-rd-specialist/MEMORY.md'
}
elseif (-not [System.IO.Path]::IsPathRooted($Path)) {
    $Path = Join-Path $repoRoot $Path
}

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Write-Error "assert-memory-budget: memory index not found at $Path - nothing was judged." -ErrorAction Continue
    exit 2
}

$memoryDir = Split-Path -Parent $Path
$bytes = (Get-Item -LiteralPath $Path).Length

$baselineFile = Join-Path $PSScriptRoot 'memory-budget-baseline.txt'
if ($MaxBytes -le 0) {
    $MaxBytes = if (Test-Path -LiteralPath $baselineFile) {
        [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    }
    else { $TargetBytes }
}
$overshoot = $bytes - $MaxBytes

if ($UpdateBaseline) {
    if ($bytes -lt $MaxBytes) {
        Set-Content -LiteralPath $baselineFile -Value "$bytes"
        Write-Host ("memory budget ratcheted DOWN: {0} -> {1} B (target {2})" -f $MaxBytes, $bytes, $TargetBytes)
    }
    elseif ($bytes -eq $MaxBytes) { Write-Host ("memory budget unchanged ({0} B)" -f $MaxBytes) }
    else {
        Write-Error ("assert-memory-budget: refusing to RAISE the ceiling {0} -> {1} B." -f $MaxBytes, $bytes) -ErrorAction Continue
        exit 1
    }
    exit 0
}

# --- advisory 1: memory naming a repo path that no longer exists -------------------------
# Only paths that look like a repo-relative file are checked - a bare word with a slash in it
# is not a claim about the tree.
$pathRx = [regex]'(?<![\w/`])((?:app_v2|wear|scripts|docs|dev|PLAN|\.claude|\.github)/[\w./-]+\.\w{1,6})'
$deadPaths = @()
foreach ($file in Get-ChildItem -LiteralPath $memoryDir -Filter '*.md' -File) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $text) { continue }
    $missing = @()
    foreach ($m in $pathRx.Matches($text)) {
        $candidate = $m.Groups[1].Value
        # A wildcard is a pattern, not a path.
        if ($candidate -match '[*?]') { continue }
        # S1542: a URL can end in something this pattern reads as a repo path - the host
        # `test-streams.mux.dev` followed by `/x36xhzz/x36xhzz.m3u8` matched the `dev/` alternative and
        # was reported as a missing file for months. A match inside a URL token is prose, not a claim
        # about the tree.
        if ($m.Index -gt 0) {
            $sepIndex = $text.LastIndexOfAny([char[]]@(' ', "`t", "`n", "`r", '(', '[', '"', "'", '`'), $m.Index - 1)
            $token = $text.Substring($sepIndex + 1, $m.Index - $sepIndex - 1)
            if ($token -match '://') { continue }
        }
        if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $candidate))) { $missing += $candidate }
    }
    if ($missing.Count -gt 0) {
        $deadPaths += [pscustomobject]@{ File = $file.Name; Paths = @($missing | Select-Object -Unique) }
    }
}

# A memory file is allowed to WRITE ABOUT links and about expiry phrasing, and the file that
# documents those two rules is the one most likely to quote them. Quoting is what a code span is
# for, so both scans below read a copy with every fenced block and inline span blanked out - to
# spaces, not removed, so match offsets still index the original text. Without this the checks
# punish the only file that explains them, and the link check is fatal (S2308).
function Get-TextOutsideCodeSpans {
    param([string]$Text)
    $blank = { param($m) ' ' * $m.Value.Length }
    $t = [regex]::Replace($Text, '(?s)```.*?```', $blank)
    return [regex]::Replace($t, '`[^`\r\n]*`', $blank)
}

# --- advisory 2: memory that declared its own expiry condition (S2308) -------------------
# Only the author knows whether a claim is durable or a snapshot, so this reads the declaration
# instead of inferring one. The matched sentence is reported, so the reader can judge whether
# the stated condition has been met without opening the file.
$selfExpiryRx = [regex]'(?i)((?:delete|remove|drop|retire)\s+this\s+(?:memory|file|entry|note)|this\s+(?:snapshot|memory|list|note)\s+decays)'
$selfExpiring = @()
foreach ($file in Get-ChildItem -LiteralPath $memoryDir -Filter '*.md' -File) {
    if ($file.Name -eq 'MEMORY.md') { continue }
    $text = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $text) { continue }
    $m = $selfExpiryRx.Match((Get-TextOutsideCodeSpans $text))
    if (-not $m.Success) { continue }
    # Quote the surrounding sentence rather than the trigger phrase - the condition is what the
    # reader has to evaluate, and it sits next to the trigger, not inside it.
    $from = [Math]::Max(0, $m.Index - 60)
    $to = [Math]::Min($text.Length, $m.Index + $m.Length + 90)
    $quote = ($text.Substring($from, $to - $from) -replace '\s+', ' ').Trim()
    $selfExpiring += [pscustomobject]@{ File = $file.Name; Quote = $quote }
}

# --- advisory 3: [[link]] cross-references that resolve to no other memory (S1345) -------
# Canonical target is frontmatter name:, but a human author also writes the file stem, its
# kebab variant, or either with the feedback_/project_/reference_/user_ type prefix dropped -
# all four are accepted before a link counts as broken.
$nameToFile = @{}
$resolvable = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($file in Get-ChildItem -LiteralPath $memoryDir -Filter '*.md' -File) {
    if ($file.Name -eq 'MEMORY.md') { continue }
    $text = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $text) { continue }
    $nm = [regex]::Match($text, '(?m)^name:\s*(\S+)\s*$')
    if (-not $nm.Success) { continue }
    $name = $nm.Groups[1].Value.Trim()
    $nameToFile[$name] = $file.Name
    [void]$resolvable.Add($name)
    $stem = $file.BaseName
    [void]$resolvable.Add($stem)
    [void]$resolvable.Add(($stem -replace '_', '-'))
    $noPrefix = $stem -replace '^(feedback|project|reference|user)_', ''
    [void]$resolvable.Add(($noPrefix -replace '_', '-'))
    [void]$resolvable.Add($noPrefix)
}

$linkRx = [regex]'\[\[([^\]]+)\]\]'
$brokenLinks = @()
foreach ($file in Get-ChildItem -LiteralPath $memoryDir -Filter '*.md' -File) {
    if ($file.Name -eq 'MEMORY.md') { continue }
    $text = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $text) { continue }
    $missing = @()
    foreach ($m in $linkRx.Matches((Get-TextOutsideCodeSpans $text))) {
        $target = $m.Groups[1].Value -replace '\.md$', ''
        if (-not $resolvable.Contains($target)) { $missing += $m.Groups[1].Value }
    }
    if ($missing.Count -gt 0) {
        $brokenLinks += [pscustomobject]@{ File = $file.Name; Targets = @($missing | Select-Object -Unique) }
    }
}

Write-Host ("memory index: {0} B | ceiling {1} B | stretch {2} B" -f $bytes, $MaxBytes, $StretchBytes)
if ($bytes -gt $MaxBytes) {
    Write-Host ("  OVER by {0} B - trim {1} B of pointer lines." -f $overshoot, $overshoot) -ForegroundColor Red
}
elseif ($bytes -gt $StretchBytes) {
    Write-Host ("  within the ceiling, {0} B above the stretch target." -f ($bytes - $StretchBytes)) -ForegroundColor Yellow
}
else {
    Write-Host "  at or below the stretch target." -ForegroundColor Green
}

if ($deadPaths.Count -gt 0) {
    Write-Host ("  advisory: {0} memory file(s) name a path that no longer exists:" -f $deadPaths.Count) -ForegroundColor Yellow
    foreach ($d in $deadPaths) { Write-Host ("    {0} -> {1}" -f $d.File, ($d.Paths -join ', ')) -ForegroundColor Yellow }
}
if ($selfExpiring.Count -gt 0) {
    Write-Host ("  advisory: {0} memory file(s) declare their own expiry condition - check whether it is met:" -f $selfExpiring.Count) -ForegroundColor Yellow
    foreach ($e in $selfExpiring) { Write-Host ("    {0} -> ..{1}.." -f $e.File, $e.Quote) -ForegroundColor Yellow }
}
if ($brokenLinks.Count -gt 0) {
    $linkColour = if ($Gate) { 'Red' } else { 'Yellow' }
    Write-Host ("  {0}: {1} memory file(s) carry an unresolvable [[link]]:" -f $(if ($Gate) { 'FAIL' } else { 'advisory' }), $brokenLinks.Count) -ForegroundColor $linkColour
    foreach ($b in $brokenLinks) { Write-Host ("    {0} -> {1}" -f $b.File, ($b.Targets -join ', ')) -ForegroundColor $linkColour }
}

if ($Gate -and $bytes -gt $MaxBytes) {
    Write-Error ("assert-memory-budget: FAIL - {0} B exceeds the {1} B ceiling by {2} B. MEMORY.md is billed on every turn of every session; merge or drop pointer lines." -f $bytes, $MaxBytes, $overshoot) -ErrorAction Continue
    exit 1
}

if ($Gate -and $brokenLinks.Count -gt 0) {
    Write-Error ("assert-memory-budget: FAIL - {0} memory file(s) point at a target that does not exist. Retarget the link to the memory the sentence means, name the governing rule instead, or drop the pointer and keep the prose - never add a memory file to satisfy a link." -f $brokenLinks.Count) -ErrorAction Continue
    exit 1
}

Write-Host "assert-memory-budget: PASS (index within budget)." -ForegroundColor Green
exit 0
