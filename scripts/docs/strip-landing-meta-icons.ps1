<#
.SYNOPSIS
    S0889 addendum (2026-07-03) - one-time removal of the card-icon element from the
    16 meta/nav/legal landing cards that have no genuine app-drawable match, per
    owner reversal of the earlier "nearest app drawable for all" policy for this
    subset.

.DESCRIPTION
    Removes the whole `<span class="card-icon">...</span>` line for card-icon
    occurrences 22-37 (1-indexed) / 21-36 (0-indexed) out of the original 37 in
    index.html, index-ru.html, index-uk.html. Those positions are the text-only
    cards: What's New, Quick Start Guide, FAQ, Troubleshooting, How-To & Scenarios,
    APK Editions & Mirrors, Documentation Map, Settings Reference, User Manual,
    Technical Specification, Architecture Overview, Terminology Reference,
    Development Roadmap, GitHub Repository, Privacy Policy, Terms of Service.

    Position (not title text) drives the removal because only the EN file carries
    literal English titles - card order is otherwise identical across all 3 locale
    files (existing invariant, see apply-doc-icons.ps1). Positions were resolved by
    cross-referencing docs/icons/doc-icon-map.json's (pre-trim) `landing` array,
    which listed all 37 titles in card order.

    Leaves the surrounding `<div class="card-title-row">` / `<h3>` untouched, so the
    remaining markup is a clean text-only title (`.card-title-row` is a flex row;
    a lone `<h3>` child renders with no reserved icon gap - see styles.css).

    Asserts a pre-strip count of 37 card-icon spans per file so a stale position
    assumption fails loudly instead of stripping the wrong cards. Companion step:
    docs/icons/doc-icon-map.json's `landing` array is trimmed separately by hand
    (it is itself hand-authored source data, not a generated artifact).

    Run once, before export-doc-icon-pngs.ps1 (prunes the now-orphaned drawables)
    and apply-doc-icons.ps1 (re-verifies the remaining 21 spans against the trimmed
    map). Re-running after the first successful pass is a no-op error (count check
    fails at 21, not 37) by design - this is a single migration, not a generator.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)

# 0-indexed positions among the 37 original card-icon spans (confirmed by title
# cross-reference against docs/icons/doc-icon-map.json before its S0889-addendum trim).
$dropPositions = 21..36
$expectedTotal = 37

$rxLine = [regex]'(?m)^[ \t]*<span class="card-icon">.*?</span>\r?\n'
foreach ($file in 'index.html', 'index-ru.html', 'index-uk.html') {
    $path = Join-Path $RepoRoot $file
    $text = Get-Content -LiteralPath $path -Raw
    $lineMatches = $rxLine.Matches($text)
    if ($lineMatches.Count -ne $expectedTotal) {
        throw "$file - card-icon line count $($lineMatches.Count) != expected $expectedTotal (positions stale - abort)"
    }
    $sb = [System.Text.StringBuilder]::new($text)
    for ($k = $lineMatches.Count - 1; $k -ge 0; $k--) {
        if ($dropPositions -notcontains $k) { continue }
        $m = $lineMatches[$k]
        [void]$sb.Remove($m.Index, $m.Length)
    }
    [System.IO.File]::WriteAllText($path, $sb.ToString(), $utf8)
    Write-Host ("{0}: removed {1} card-icon spans, {2} remain" -f $file, $dropPositions.Count, ($expectedTotal - $dropPositions.Count))
}

Write-Host ''
Write-Host 'Done. Next: trim docs/icons/doc-icon-map.json landing[] to match (hand-edit), then export-doc-icon-pngs.ps1 + apply-doc-icons.ps1 + assert-doc-icons-sync.ps1 -Gate.'
exit 0
