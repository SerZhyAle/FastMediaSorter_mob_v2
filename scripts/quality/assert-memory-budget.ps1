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

    Three advisory checks ride along, all correctness rather than cost, and all advisory on
    purpose: a hard failure on any would train the operator to bypass the gate.
      - dead paths   - a memory file naming a repo path that no longer exists.
      - expired      - a memory file whose every `Sxxxx` reference is Archived or absent from
                       the catalog. 52% of memory bytes are anchored to tickets that are gone.
      - broken links - a `[[target]]` cross-link that resolves to no other memory's frontmatter
                       `name:` value, even allowing the file-stem / kebab-stem / no-type-prefix
                       fallbacks a human author actually writes (S1345).

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
    Exit 1 when the index is above MaxBytes. Without it the run only reports.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  at or below MaxBytes, or a report-only run.
      1  -Gate and the index is above MaxBytes.
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
        if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $candidate))) { $missing += $candidate }
    }
    if ($missing.Count -gt 0) {
        $deadPaths += [pscustomobject]@{ File = $file.Name; Paths = @($missing | Select-Object -Unique) }
    }
}

# --- advisory 2: memory anchored only to tickets that are gone ---------------------------
$liveIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$catalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'
if (Test-Path -LiteralPath $catalog) {
    foreach ($line in Get-Content -LiteralPath $catalog -Encoding UTF8) {
        $t = "$line".Trim()
        if (-not $t) { continue }
        try { $rec = $t | ConvertFrom-Json } catch { continue }
        if ($rec.status -ne 'Archived') { [void]$liveIds.Add([string]$rec.id) }
    }
}

$ticketRx = [regex]'\bS\d{4}\b'
$expired = @()
foreach ($file in Get-ChildItem -LiteralPath $memoryDir -Filter '*.md' -File) {
    if ($file.Name -eq 'MEMORY.md') { continue }
    $text = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $text) { continue }
    $ids = @($ticketRx.Matches($text) | ForEach-Object { $_.Value } | Select-Object -Unique)
    # No ticket id means the memory is not anchored to one - it cannot expire with a ticket.
    if ($ids.Count -eq 0) { continue }
    $aliveIds = @($ids | Where-Object { $liveIds.Contains($_) })
    if ($aliveIds.Count -eq 0) {
        $expired += [pscustomobject]@{ File = $file.Name; Ids = $ids }
    }
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
    foreach ($m in $linkRx.Matches($text)) {
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
if ($expired.Count -gt 0) {
    Write-Host ("  advisory: {0} memory file(s) reference only dead tickets:" -f $expired.Count) -ForegroundColor Yellow
    foreach ($e in $expired) { Write-Host ("    {0} -> {1}" -f $e.File, ($e.Ids -join ', ')) -ForegroundColor Yellow }
}
if ($brokenLinks.Count -gt 0) {
    Write-Host ("  advisory: {0} memory file(s) carry an unresolvable [[link]]:" -f $brokenLinks.Count) -ForegroundColor Yellow
    foreach ($b in $brokenLinks) { Write-Host ("    {0} -> {1}" -f $b.File, ($b.Targets -join ', ')) -ForegroundColor Yellow }
}

if ($Gate -and $bytes -gt $MaxBytes) {
    Write-Error ("assert-memory-budget: FAIL - {0} B exceeds the {1} B ceiling by {2} B. MEMORY.md is billed on every turn of every session; merge or drop pointer lines." -f $bytes, $MaxBytes, $overshoot) -ErrorAction Continue
    exit 1
}

Write-Host "assert-memory-budget: PASS (index within budget)." -ForegroundColor Green
exit 0
