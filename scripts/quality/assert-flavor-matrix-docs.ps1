<#
.SYNOPSIS
    S1392 - documentation-vs-gradle flavor capability matrix gate.

.DESCRIPTION
    Compares every glyph table declared in scripts/quality/flavor-matrix-docs.psd1 against the
    generated snapshot docs/flavors/flavor-matrix.json, cell by cell, comparing the resolved
    VALUE of a cell rather than its literal text. A qualifier next to a marker ("(progressive
    only)") does not hide a wrong marker, and a footnote next to a correct marker does not trip
    the gate.

    Before S1392 nothing compared a documentation table to the build file: the version-pin
    checker (scripts/check-doc-vs-gradle.ps1) covers pins only, and the release snapshot
    (scripts/release/standard-surface-snapshot.ps1) covers the standard flavor only, against
    docs/ALL_FEATURES.jsonl rather than markdown. Drift was therefore silent, which is how
    docs/HOW_TO.md came to state the opposite of the lite gates on two rows.

    Failure classes, all fatal under -Gate:
      value      a mapped cell disagrees with the snapshot
      missing    a mapped row or column is absent from the table
      anchor     the declared heading, or a table after it, is absent
      glyph      a cell carries a marker outside the document's declared vocabulary
      ambiguous  a column maps to several flavors that disagree in the snapshot - split it
      flag       the manifest maps to a flag the snapshot does not declare

    A structural failure is never downgraded to a skip: a table that moved is exactly the case
    where a silent pass would restore the pre-S1392 condition.

.PARAMETER Gate
    Exit non-zero on any finding. Without it the script reports and exits 0, which is the
    read-only mode used while correcting documents.

.PARAMETER Table
    Restrict to one manifest table Id (diagnostic aid while editing a single document).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1
    pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1 -Gate -Quiet

.NOTES
    Exit codes:
      0  no findings (or findings reported without -Gate)
      1  -Gate and at least one finding
      2  could not verify: snapshot or manifest missing / unreadable, or the snapshot is stale
         relative to app_v2/build.gradle.kts
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Quiet,
    [string] $Table,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$manifestPath = Join-Path $PSScriptRoot 'flavor-matrix-docs.psd1'
$snapshotPath = Join-Path $RepoRoot 'docs/flavors/flavor-matrix.json'

function Write-Info([string] $Message) {
    if (-not $Quiet) { Write-Host $Message }
}

function Fail-Unverifiable([string] $Message) {
    Write-Error "assert-flavor-matrix-docs: $Message" -ErrorAction Continue
    exit 2
}

function Normalize-Label([string] $Raw) {
    $t = $Raw
    $t = $t -replace '\*\*', ''
    $t = $t -replace '`', ''
    $t = $t -replace '\s+', ' '
    return $t.Trim().ToLowerInvariant()
}

# Resolve a cell's text to $true / $false / $null (absent), or 'glyph' when the text carries no
# marker from this document's vocabulary. Longest marker first so '[+]*' wins over '[+]'.
function Resolve-Cell([string] $Text, [hashtable] $Glyphs) {
    $t = ($Text -replace '\s+', ' ').Trim()
    if ([string]::IsNullOrWhiteSpace($t)) { return 'glyph' }

    $candidates = [System.Collections.Generic.List[object]]::new()
    foreach ($state in @('True', 'False', 'Absent')) {
        foreach ($marker in @($Glyphs[$state])) {
            if ([string]::IsNullOrEmpty($marker)) { continue }
            $candidates.Add([pscustomobject]@{ State = $state; Marker = [string]$marker })
        }
    }
    $ordered = $candidates | Sort-Object { $_.Marker.Length } -Descending
    foreach ($c in $ordered) {
        if ($t.Contains($c.Marker)) {
            switch ($c.State) {
                'True'   { return $true }
                'False'  { return $false }
                'Absent' { return $null }
            }
        }
    }
    return 'glyph'
}

# Parse the first markdown table that follows $AnchorIndex. Returns header cells, and body rows
# as objects carrying the 1-based file line number.
function Read-MarkdownTable([string[]] $Lines, [int] $AnchorIndex) {
    $i = $AnchorIndex + 1
    while ($i -lt $Lines.Count -and $Lines[$i] -notmatch '^\s*\|') {
        # A second heading before any table means the anchor's table is gone.
        if ($Lines[$i] -match '^\s*#{2,}\s') { return $null }
        $i++
    }
    if ($i -ge $Lines.Count) { return $null }

    $split = {
        param([string] $Row)
        $inner = $Row.Trim()
        $inner = $inner -replace '^\|', ''
        $inner = $inner -replace '\|\s*$', ''
        return @($inner -split '\|')
    }

    $header = & $split $Lines[$i]
    $bodyStart = $i + 1
    if ($bodyStart -lt $Lines.Count -and $Lines[$bodyStart] -match '^\s*\|[\s:\-|]+\|?\s*$') { $bodyStart++ }

    $rows = [System.Collections.Generic.List[object]]::new()
    for ($j = $bodyStart; $j -lt $Lines.Count; $j++) {
        if ($Lines[$j] -notmatch '^\s*\|') { break }
        $rows.Add([pscustomobject]@{ Line = $j + 1; Cells = (& $split $Lines[$j]) })
    }
    return [pscustomobject]@{ Header = $header; Rows = $rows; HeaderLine = $i + 1 }
}

# ---------------------------------------------------------------- load inputs

if (-not (Test-Path -LiteralPath $manifestPath)) { Fail-Unverifiable "manifest not found: $manifestPath" }
if (-not (Test-Path -LiteralPath $snapshotPath)) {
    Fail-Unverifiable "snapshot not found: $snapshotPath - run scripts/docs/generate-flavor-matrix.ps1 first."
}

try { $manifest = Import-PowerShellDataFile -LiteralPath $manifestPath }
catch { Fail-Unverifiable "manifest unreadable: $($_.Exception.Message)" }

try { $snapshot = Get-Content -LiteralPath $snapshotPath -Raw | ConvertFrom-Json }
catch { Fail-Unverifiable "snapshot unreadable: $($_.Exception.Message)" }

# A stale snapshot would let the gate certify documents against yesterday's build file.
$generator = Join-Path $RepoRoot 'scripts/docs/generate-flavor-matrix.ps1'
if (Test-Path -LiteralPath $generator) {
    & (Get-Process -Id $PID).Path -NoProfile -File $generator -Check -Quiet 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Fail-Unverifiable 'snapshot is stale relative to app_v2/build.gradle.kts - regenerate it before gating documents.'
    }
}

$snapFlavors = @($snapshot.flavors)
$findings = [System.Collections.Generic.List[object]]::new()

function Add-Finding([string] $Class, [string] $TableId, [string] $Path, [int] $Line, [string] $Message) {
    $findings.Add([pscustomobject]@{
        Class = $Class; TableId = $TableId; Path = $Path; Line = $Line; Message = $Message
    })
}

function Get-SnapshotValue([string] $Flavor, [string] $Flag) {
    $row = $snapshot.matrix.$Flavor
    if ($null -eq $row) { return 'noflavor' }
    if (-not ($row.PSObject.Properties.Name -contains $Flag)) { return 'noflag' }
    return $row.$Flag
}

# ---------------------------------------------------------------- walk tables

$tables = @($manifest.Tables)
if ($Table) { $tables = @($tables | Where-Object { $_.Id -eq $Table }) }
if ($tables.Count -eq 0) { Fail-Unverifiable "no table matched -Table '$Table'." }

$cellsChecked = 0

foreach ($spec in $tables) {
    $path = Join-Path $RepoRoot $spec.Path
    if (-not (Test-Path -LiteralPath $path)) {
        Add-Finding 'anchor' $spec.Id $spec.Path 0 "document not found"
        continue
    }
    $lines = Get-Content -LiteralPath $path

    $anchorIdx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ((Normalize-Label $lines[$i]) -eq (Normalize-Label $spec.Anchor)) { $anchorIdx = $i; break }
    }
    if ($anchorIdx -lt 0) {
        Add-Finding 'anchor' $spec.Id $spec.Path 0 "anchor heading not found: '$($spec.Anchor)'"
        continue
    }

    $parsed = Read-MarkdownTable -Lines $lines -AnchorIndex $anchorIdx
    if ($null -eq $parsed) {
        Add-Finding 'anchor' $spec.Id $spec.Path ($anchorIdx + 1) "no markdown table follows the anchor"
        continue
    }

    $glyphs   = $spec.Glyphs
    $skipRows = @()
    if ($spec.ContainsKey('SkipRows')) { $skipRows = @($spec.SkipRows) }

    # Column labels: index 0 is the row-label column.
    $colLabels = @{}
    for ($c = 1; $c -lt $parsed.Header.Count; $c++) {
        $colLabels[(Normalize-Label $parsed.Header[$c])] = $c
    }
    foreach ($declared in $spec.ColMap.Keys) {
        if (-not $colLabels.ContainsKey($declared)) {
            Add-Finding 'missing' $spec.Id $spec.Path $parsed.HeaderLine "declared column '$declared' absent from the header row"
        }
    }

    $seenRows = @{}
    foreach ($row in $parsed.Rows) {
        if ($row.Cells.Count -lt 1) { continue }
        $label = Normalize-Label $row.Cells[0]
        if ($skipRows -contains $label) { continue }
        if (-not $spec.RowMap.ContainsKey($label)) { continue }
        $seenRows[$label] = $true
        $rowTarget = $spec.RowMap[$label]

        foreach ($colLabel in $spec.ColMap.Keys) {
            if (-not $colLabels.ContainsKey($colLabel)) { continue }
            $colIdx = $colLabels[$colLabel]
            if ($colIdx -ge $row.Cells.Count) {
                Add-Finding 'missing' $spec.Id $spec.Path $row.Line "row '$label' has no cell under column '$colLabel'"
                continue
            }

            if ($spec.Axis -eq 'FlagRows') {
                $flags   = @($rowTarget)
                $flavors = @($spec.ColMap[$colLabel])
            }
            else {
                $flavors = @($rowTarget)
                $flags   = @($spec.ColMap[$colLabel])
            }

            $expectedSet = [System.Collections.Generic.List[object]]::new()
            $bad = $false
            foreach ($flavor in $flavors) {
                if ($snapFlavors -notcontains $flavor) {
                    Add-Finding 'flag' $spec.Id $spec.Path $row.Line "manifest names flavor '$flavor', absent from the snapshot"
                    $bad = $true; continue
                }
                foreach ($flag in $flags) {
                    $v = Get-SnapshotValue $flavor $flag
                    if ($v -is [string] -and $v -eq 'noflag') {
                        Add-Finding 'flag' $spec.Id $spec.Path $row.Line "manifest names flag '$flag', absent from the snapshot"
                        $bad = $true; continue
                    }
                    if (-not $expectedSet.Contains($v)) { $expectedSet.Add($v) }
                }
            }
            if ($bad) { continue }
            if ($expectedSet.Count -gt 1) {
                Add-Finding 'ambiguous' $spec.Id $spec.Path $row.Line ("column '$colLabel' merges flavors that disagree on '$($flags -join ", ")' - split the column")
                continue
            }

            $expected = $expectedSet[0]
            $actual = Resolve-Cell $row.Cells[$colIdx] $glyphs
            $cellsChecked++

            if ($actual -is [string] -and $actual -eq 'glyph') {
                Add-Finding 'glyph' $spec.Id $spec.Path $row.Line ("cell [$label / $colLabel] carries no known marker: '$($row.Cells[$colIdx].Trim())'")
                continue
            }
            if ($expected -ne $actual) {
                $exp = if ($null -eq $expected) { 'not declared' } else { "$expected" }
                $act = if ($null -eq $actual) { 'not declared' } else { "$actual" }
                Add-Finding 'value' $spec.Id $spec.Path $row.Line ("cell [$label / $colLabel] expected: $exp | actual: $act")
            }
        }
    }

    foreach ($declared in $spec.RowMap.Keys) {
        if (-not $seenRows.ContainsKey($declared)) {
            Add-Finding 'missing' $spec.Id $spec.Path $parsed.HeaderLine "declared row '$declared' absent from the table"
        }
    }
}

# ---------------------------------------------------------------- report

if ($findings.Count -gt 0) {
    Write-Host "assert-flavor-matrix-docs: $($findings.Count) finding(s) across $($tables.Count) table(s)." -ForegroundColor Yellow
    foreach ($f in ($findings | Sort-Object Path, Line)) {
        Write-Host ("  {0}:{1} [{2}/{3}] {4}" -f $f.Path, $f.Line, $f.TableId, $f.Class, $f.Message)
    }
    Write-Host "expected: 0 finding(s) | actual: $($findings.Count)"
    if ($Gate) {
        Write-Error "assert-flavor-matrix-docs: FAIL - documentation disagrees with docs/flavors/flavor-matrix.json." -ErrorAction Continue
        exit 1
    }
    exit 0
}

Write-Info "assert-flavor-matrix-docs: PASS - $cellsChecked cell(s) in $($tables.Count) table(s) agree with the snapshot."
exit 0
