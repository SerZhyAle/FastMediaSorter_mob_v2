#requires -Version 7.0
<#
.SYNOPSIS
    S2935: fail when a document states a gate population the live tree disagrees with - the
    fast-gate count in docs/BUILD_TEST_FAST_PATH.md, and the assert-*.ps1 inventory in section 9
    of docs/RULES_DIGEST.md.

.DESCRIPTION
    The `a.ps1 fg` row of that document carries two numbers. S2453 already watches the wall clock
    against the telemetry journal; the gate COUNT beside it was watched by nothing, and it moves
    whenever any ticket adds a gate - that is, in a ticket whose subject is something else
    entirely, written by someone with no reason to open this document. Measured 2026-09-11 the row
    still said 45 against a live 61: fifteen revisions of silent drift, accumulated one ticket at a
    time over five weeks.

    The count is not the load-bearing half of that row - CLAUDE.md Rule 6 decides foreground or
    background on the TIME - but it is the half that explains the time, and a reader who trusts it
    reasons about the wrong batch.

    THE NUMBER STAYS IN THE PROSE. This script holds an anchor, never a copy of the count, which
    is S2453's ruling for the sibling figure in the same row: a copy here would be a third source
    of truth and the same divergence one level down. Generating the row instead was rejected -
    its other cell is a hand-measured wall clock no generator produces, and a table row half
    generated and half authored is worse than either whole.

    AST, NOT A REGULAR EXPRESSION, for the live side. $gates is a PowerShell [ordered]@{} literal,
    so its key count is a parse away and needs no pattern that a reformat could defeat. The
    neighbouring Get-GateReferenceNames in lib/gate-placement-registry.ps1 deliberately answers a
    different question and is NOT reused: it collects every assert-*.ps1 string literal in the
    file, which on 2026-09-11 returned 63 - the 61 table entries plus assert-detekt.ps1 behind
    -IncludeDetekt and assert-release-scope-gates.ps1 out of a console message.

    SECOND CLAIM, SAME FAILURE MODE. Section 9 of docs/RULES_DIGEST.md states a count AND spells
    out every assert-*.ps1 basename. Both move in whichever ticket adds a gate, and that ticket has
    no reason to open a digest written for automation research: measured 2026-09-22 the section
    said 124 against a live 132, and the name block was short by the same eight - the S3371
    security and perf gates, added by the very ticket that created the section. The inventory is
    the load-bearing half here; a reader mining the digest for gate names gets a list that silently
    omits whichever gates are newest, which is the subset a researcher most wants.

    SET COMPARISON, NOT TEXT COMPARISON, for that half. The block is two hand-aligned columns, and
    a gate that judged its layout would refuse a re-wrap that changed nothing. What is checked is
    the set of names and the number beside it; how they are arranged on the page stays the author's.

.PARAMETER Gate
    Exit non-zero on a finding. Without it the script reports both numbers and exits 0, which is
    the read-only mode used while correcting the document.

.PARAMETER Quiet
    Suppress the progress line; the verdict and any finding still print. This is what
    assert-fast-gates.ps1 passes when it runs the gate as part of the batch.

.PARAMETER RunnerPath
    Script holding the $gates table (default: assert-fast-gates.ps1 beside this one). Exists so a
    contract test can point the gate at a fixture instead of this tree.

.PARAMETER DocPath
    Document holding the fast-gate count claim (default: docs/BUILD_TEST_FAST_PATH.md). Same reason.

.PARAMETER DigestPath
    Document holding the inventory claim (default: docs/RULES_DIGEST.md). Same reason.

.PARAMETER GateDirectory
    Directory the inventory claims to enumerate (default: scripts/quality, this script's own).

.EXIT CODES
    0 - both documented populations match the live tree, or a finding exists and -Gate was absent.
    1 - a documented population contradicts the live tree, and -Gate was passed.
    2 - could not verify: a file or the gate directory is absent, the runner does not parse, it
        holds no single $gates assignment or no hashtable literal under it, the gate directory
        holds no entry point, or an anchor matched a number of spans other than exactly one.
        "Did not look" must never read as green.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-gate-count-prose.ps1
    pwsh -NoProfile -File scripts/quality/assert-gate-count-prose.ps1 -Gate -Quiet
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$RunnerPath,
    [string]$DocPath,
    [string]$DigestPath,
    [string]$GateDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = if ($RunnerPath) { $RunnerPath } else { Join-Path $PSScriptRoot 'assert-fast-gates.ps1' }
$doc = if ($DocPath) { $DocPath } else { Join-Path $repoRoot 'docs/BUILD_TEST_FAST_PATH.md' }
$digest = if ($DigestPath) { $DigestPath } else { Join-Path $repoRoot 'docs/RULES_DIGEST.md' }
$gateDir = if ($GateDirectory) { $GateDirectory } else { $PSScriptRoot }

# Group 1 is the count. The anchor stops before the wall-clock cell on purpose: that cell belongs
# to assert-gate-timing-claims.ps1, and two gates reading one span would disagree the first time
# either pattern was loosened.
$anchor = '(?m)^\|\s*`a\.ps1 fg`\s*\(fast static gates,\s*([0-9]+)\s+gates\b'

function Deny-Verify([string]$Message) {
    Write-Error "assert-gate-count-prose: CANNOT VERIFY - $Message" -ErrorAction Continue
    exit 2
}

if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    Deny-Verify "the runner is absent: $runner"
}
if (-not (Test-Path -LiteralPath $doc -PathType Leaf)) {
    Deny-Verify "the document is absent: $doc"
}

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path -LiteralPath $runner).Path, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) {
    Deny-Verify "the runner does not parse ($($parseErrors.Count) error(s)): $($parseErrors[0].Message)"
}

$assignments = @($ast.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
            $node.Left.Extent.Text -eq '$gates'
        }, $true))
if ($assignments.Count -ne 1) {
    Deny-Verify ("the runner holds $($assignments.Count) assignment(s) to `$gates, expected exactly 1. " +
        'The table was split or renamed: re-point this gate, or the count it guards is unguarded from now on.')
}

$tables = @($assignments[0].Right.FindAll({
            param($node) $node -is [System.Management.Automation.Language.HashtableAst]
        }, $true))
if ($tables.Count -lt 1) {
    Deny-Verify 'the $gates assignment carries no hashtable literal - the table is now built rather than written.'
}
$liveCount = $tables[0].KeyValuePairs.Count

$docText = Get-Content -LiteralPath $doc -Raw
# Not $matches: that is an automatic variable the -match operator overwrites, and a gate whose
# evidence can be clobbered by an unrelated comparison later in the file is a gate that lies.
$anchorMatches = [regex]::Matches($docText, $anchor)
if ($anchorMatches.Count -ne 1) {
    Deny-Verify ("the anchor matched $($anchorMatches.Count) line(s) in $(Split-Path -Leaf $doc), expected exactly 1. " +
        'The row was reworded or duplicated: re-anchor this gate.')
}
$claimedCount = [int]$anchorMatches[0].Groups[1].Value

if (-not $Quiet) {
    Write-Host ("assert-gate-count-prose: documented {0}, live {1} ({2})." -f
        $claimedCount, $liveCount, (Split-Path -Leaf $runner))
}

$failed = $false

if ($claimedCount -eq $liveCount) {
    if (-not $Quiet) {
        Write-Host 'assert-gate-count-prose: PASS (the documented gate count matches the table).' -ForegroundColor Green
    }
}
else {
    $failed = $true
    # Single-quoted format strings: the gate names are written with backticks, and inside a
    # double-quoted string PowerShell reads `f and `a as form feed and bell rather than as text.
    Write-Host ''
    Write-Host ('assert-gate-count-prose: the `fg` row claims {0} gates, the table holds {1}.' -f
        $claimedCount, $liveCount) -ForegroundColor Red
    Write-Host ('  {0} - update the count in the `a.ps1 fg` row.' -f $doc) -ForegroundColor Red
    Write-Host '  The wall clock in the same row is a separate claim: re-measure it before editing it,' -ForegroundColor Yellow
    Write-Host '  and leave the dated S2451 figures elsewhere in that document alone.' -ForegroundColor Yellow
}

# --- Second claim: the section 9 inventory of docs/RULES_DIGEST.md ----------------------------
# Absence is a finding of the "cannot verify" class, not lib/absent-input.ps1's exit 3: that code
# is reserved for a gitignored root, a PUBLISHED property of the checkout. A digest that is simply
# missing is something broken, and calling it a skip would hide exactly the drift this half exists
# to catch.
if (-not (Test-Path -LiteralPath $digest -PathType Leaf)) {
    Deny-Verify "the digest is absent: $digest"
}
if (-not (Test-Path -LiteralPath $gateDir -PathType Container)) {
    Deny-Verify "the gate directory is absent: $gateDir"
}

$liveNames = @(Get-ChildItem -LiteralPath $gateDir -Filter 'assert-*.ps1' -File |
        ForEach-Object { $_.BaseName } | Sort-Object)
if ($liveNames.Count -lt 1) {
    Deny-Verify ("no assert-*.ps1 entry point under $gateDir. " +
        'The gates moved: re-point this half, or the inventory it guards is unguarded from now on.')
}

# Newline-normalised so one pattern reads a CRLF and an LF checkout alike.
$digestText = (Get-Content -LiteralPath $digest -Raw) -replace "`r`n", "`n"
$digestLeaf = Split-Path -Leaf $digest

# Anchored on the sentence, not on the section number: renumbering the digest must not silently
# unhook the gate. Group 1 is the claimed population.
$inventoryCountAnchor = '(?m)^There are ([0-9]+) `scripts/quality/assert-\*\.ps1` entry points\b'
$inventoryFenceAnchor = '(?s)^#+ [^\n]*quality-gate inventory[^\n]*\n.*?\n```text\n(.*?)\n```\n'

$countMatches = [regex]::Matches($digestText, $inventoryCountAnchor)
if ($countMatches.Count -ne 1) {
    Deny-Verify ("the inventory count anchor matched $($countMatches.Count) line(s) in $digestLeaf, expected exactly 1. " +
        'The sentence was reworded or duplicated: re-anchor this gate.')
}
$fenceMatches = [regex]::Matches($digestText, $inventoryFenceAnchor, [System.Text.RegularExpressions.RegexOptions]::Multiline)
if ($fenceMatches.Count -ne 1) {
    Deny-Verify ("the inventory name block matched $($fenceMatches.Count) fence(s) in $digestLeaf, expected exactly 1. " +
        'The heading or the fence changed: re-anchor this gate.')
}

$claimedInventory = [int]$countMatches[0].Groups[1].Value
$documentedNames = @([regex]::Matches($fenceMatches[0].Groups[1].Value, 'assert-[a-z0-9-]+') |
        ForEach-Object { $_.Value } | Sort-Object -Unique)

$undocumented = @($liveNames | Where-Object { $documentedNames -notcontains $_ })
$phantom = @($documentedNames | Where-Object { $liveNames -notcontains $_ })

if (-not $Quiet) {
    Write-Host ("assert-gate-count-prose: {0} lists {1} name(s) and claims {2}, live {3}." -f
        $digestLeaf, $documentedNames.Count, $claimedInventory, $liveNames.Count)
}

if ($claimedInventory -eq $liveNames.Count -and $undocumented.Count -eq 0 -and $phantom.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-gate-count-prose: PASS (the documented inventory matches the gate directory).' -ForegroundColor Green
    }
}
else {
    $failed = $true
    Write-Host ''
    Write-Host ('assert-gate-count-prose: the {0} inventory contradicts {1}.' -f $digestLeaf, $gateDir) -ForegroundColor Red
    if ($claimedInventory -ne $liveNames.Count) {
        Write-Host ('  the section claims {0} entry points, the directory holds {1}.' -f
            $claimedInventory, $liveNames.Count) -ForegroundColor Red
    }
    foreach ($name in $undocumented) {
        Write-Host "  on disk, absent from the name block: $name" -ForegroundColor Red
    }
    foreach ($name in $phantom) {
        Write-Host "  in the name block, absent from disk: $name" -ForegroundColor Red
    }
    Write-Host '  Add or drop the names and correct the count in the same edit - the block is the' -ForegroundColor Yellow
    Write-Host '  list a researcher mines for gate names, so a short one reads as a complete one.' -ForegroundColor Yellow
}

if (-not $failed) { exit 0 }
if (-not $Gate) { exit 0 }

Write-Error ('assert-gate-count-prose: FAIL - a documented gate population contradicts the live tree; ' +
    'the finding above names the document and the exact correction.') -ErrorAction Continue
exit 1
