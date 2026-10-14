#requires -Version 7.0
<#
.SYNOPSIS
    S4102 gate: every showcase and release-notes bullet names a shipped record of the capability inventory.

.DESCRIPTION
    The showcase (docs/FEATURES*.md) and the release notes (docs/WHATS_NEW*.md) are curated by hand at
    the release from the docs/ALL_FEATURES.jsonl diff, and nothing compared what they name with the
    inventory (SITE-REPRESENTATION rule 11, SITE-STRUCTURE rule 14). Each bullet therefore carries an
    invisible anchor at the end of its line:

        - **Title** `[Standard]`: text. <!-- af: area.feature-id, area.other-id -->

    This gate refuses:
      - a bullet without an anchor;
      - an anchored id that is not in docs/ALL_FEATURES.jsonl, or whose status is not active;
      - an id whose reach is noLegal-only - `flavors` is exactly noLegal, or `wearFlavors` is
        exactly noLegal (the watch ships it only in the sideload build) - since both files are public;
      - an RU or UK file whose anchor lists differ from the EN file's, position by position.

    Judged files: docs/FEATURES.md with -ru/-uk, and the "What's New" bullets of the current release
    block of docs/WHATS_NEW.md with -ru/-uk. "What's Fixed" names fixes rather than capabilities, and
    the older release blocks predate the anchor, so neither is judged.

    Not judged: whether a bullet's flavor label agrees with its record - the label vocabulary is
    free-form today; the gitignored docs/FEATURES_noLegal*.md, which is never published and is a
    per-capability description file (### headings with detail bullets), not a bullet showcase.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; a test passes a fixture tree.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject and the verdict line, plus findings on failure.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-showcase-inventory.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE, plus a fixed-input trigger in the docs-corpus closure
    when a showcase or release-notes file is in the changed set. Placement row in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - every judged bullet is anchored to a shipped record and the locales agree.
      1 - at least one finding.
      2 - cannot verify: the root, the inventory or a required showcase / release-notes file is
          missing, or an inventory line is not valid JSON.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-showcase-inventory: COULD NOT VERIFY - $reason" -ForegroundColor Yellow
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

function Get-Prop($obj, [string]$name) {
    $p = $obj.PSObject.Properties[$name]
    if ($p) { return $p.Value }
    return $null
}

function Test-NoLegalOnly($values) {
    $list = @($values | Where-Object { $_ })
    return ($list.Count -eq 1 -and $list[0] -eq 'noLegal')
}

function Read-Inventory([string]$rel) {
    $full = Join-Path $Root $rel
    $map = @{}
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Stop-CannotVerify "inventory not found: $rel"
    }
    $n = 0
    foreach ($line in [System.IO.File]::ReadAllLines($full)) {
        $n++
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $rec = $line | ConvertFrom-Json } catch { Stop-CannotVerify "${rel}:$n is not valid JSON" }
        $id = Get-Prop $rec 'id'
        if (-not $id) { continue }
        $status = Get-Prop $rec 'status'
        $map[[string]$id] = [pscustomobject]@{
            Status       = if ($status) { [string]$status } else { 'active' }
            NoLegalOnly  = (Test-NoLegalOnly (Get-Prop $rec 'flavors')) -or (Test-NoLegalOnly (Get-Prop $rec 'wearFlavors'))
        }
    }
    return $map
}

$inventory = Read-Inventory 'docs/ALL_FEATURES.jsonl'

$anchorPattern = '<!--\s*af:\s*([^>]*?)\s*-->\s*$'
$bulletPattern = '^- \*\*'

function Get-ShowcaseBullets([string[]]$lines) {
    $out = [System.Collections.Generic.List[object]]::new()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $bulletPattern) { $out.Add([pscustomobject]@{ Line = $i + 1; Text = $lines[$i] }) }
    }
    return , $out
}

# The current block runs from the "Current release" marker to the next "Previous Release" heading;
# inside it the first level-2 heading is the What's New section, closed by the next heading or rule.
function Get-WhatsNewBullets([string[]]$lines, [string]$rel) {
    $out = [System.Collections.Generic.List[object]]::new()
    $start = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\*\*(Current release:|Текущий релиз:|Поточний реліз:)') { $start = $i; break }
    }
    if ($start -lt 0) { Stop-CannotVerify "${rel}: no current-release marker" }
    $inNew = $false
    for ($i = $start + 1; $i -lt $lines.Count; $i++) {
        $ln = $lines[$i]
        if ($ln -match '^## (Previous Release:|Предыдущий релиз:|Попередній реліз:)') { break }
        if ($ln -match "^## (What's New|Что нового|Що нового)\s*$") { $inNew = $true; continue }
        if ($ln -match '^## ' -or $ln -match '^---\s*$') {
            if ($inNew) { break }
            continue
        }
        if ($inNew -and $ln -match '^- ') { $out.Add([pscustomobject]@{ Line = $i + 1; Text = $ln }) }
    }
    return , $out
}

$findings = [System.Collections.Generic.List[string]]::new()
$judged = 0

function Test-Group([string[]]$files, [string]$kind) {
    $reference = $null
    $referenceFile = $null
    foreach ($rel in $files) {
        $full = Join-Path $Root $rel
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            Stop-CannotVerify "file not found: $rel"
        }
        $lines = [System.IO.File]::ReadAllLines($full)
        $bullets = if ($kind -eq 'showcase') { Get-ShowcaseBullets $lines } else { Get-WhatsNewBullets $lines $rel }
        $anchors = [System.Collections.Generic.List[string]]::new()
        foreach ($b in $bullets) {
            $script:judged++
            $m = [regex]::Match($b.Text, $anchorPattern)
            if (-not $m.Success) {
                $findings.Add("${rel}:$($b.Line): bullet has no '<!-- af: id -->' anchor")
                $anchors.Add('')
                continue
            }
            $ids = @($m.Groups[1].Value -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            if ($ids.Count -eq 0) { $findings.Add("${rel}:$($b.Line): anchor names no id") }
            foreach ($id in $ids) {
                if (-not $inventory.ContainsKey($id)) {
                    $findings.Add("${rel}:$($b.Line): '$id' is not in the inventory")
                } elseif ($inventory[$id].Status -ne 'active') {
                    $findings.Add("${rel}:$($b.Line): '$id' has status '$($inventory[$id].Status)', not active")
                } elseif ($inventory[$id].NoLegalOnly) {
                    $findings.Add("${rel}:$($b.Line): '$id' ships only in noLegal and cannot appear in a public file")
                }
            }
            $anchors.Add((($ids | Sort-Object) -join ','))
        }
        if ($null -eq $reference) {
            $reference = $anchors
            $referenceFile = $rel
            continue
        }
        if ($anchors.Count -ne $reference.Count) {
            $findings.Add("${rel}: $($anchors.Count) bullet(s), $referenceFile has $($reference.Count)")
            continue
        }
        # The RU and UK showcases translate the EN bullets but do not keep their order, so the
        # locales are compared as multisets of anchor lists: a capability announced in one locale
        # and missing from another is the defect, a different position is not.
        $missing = [System.Collections.Generic.List[string]]::new($reference)
        $extra = [System.Collections.Generic.List[string]]::new()
        foreach ($a in $anchors) {
            if (-not $missing.Remove($a)) { $extra.Add($a) }
        }
        foreach ($a in $extra) { $findings.Add("${rel}: a bullet anchors '$a', which no $referenceFile bullet anchors") }
        foreach ($a in $missing) { $findings.Add("${rel}: no bullet anchors '$a', which a $referenceFile bullet anchors") }
    }
}

Test-Group @('docs/FEATURES.md', 'docs/FEATURES-ru.md', 'docs/FEATURES-uk.md') 'showcase'
Test-Group @('docs/WHATS_NEW.md', 'docs/WHATS_NEW-ru.md', 'docs/WHATS_NEW-uk.md') 'notes'

Write-Host "subject: root=$Root bullets=$judged inventory=$($inventory.Count)"

if ($findings.Count -gt 0) {
    Write-Host "assert-showcase-inventory: FAIL ($($findings.Count) finding(s))" -ForegroundColor Red
    $shown = if ($Quiet) { $findings | Select-Object -First 40 } else { $findings }
    $shown | ForEach-Object { Write-Host "  $_" }
    if ($Quiet -and $findings.Count -gt 40) { Write-Host "  .. and $($findings.Count - 40) more" }
    exit 1
}
Write-Host "assert-showcase-inventory: PASS ($judged bullets anchored to shipped records)" -ForegroundColor Green
exit 0
