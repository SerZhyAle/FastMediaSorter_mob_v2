#requires -Version 7.0
<#
.SYNOPSIS
    S2935: fail when docs/BUILD_TEST_FAST_PATH.md claims a fast-gate count the live $gates table
    in assert-fast-gates.ps1 disagrees with.

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
    Document holding the claim (default: docs/BUILD_TEST_FAST_PATH.md). Same reason.

.EXIT CODES
    0 - the documented count matches the live table, or a finding exists and -Gate was absent.
    1 - the documented count contradicts the live table, and -Gate was passed.
    2 - could not verify: either file is absent, the runner does not parse, it holds no single
        $gates assignment or no hashtable literal under it, or the document's anchor matched a
        number of lines other than exactly one. "Did not look" must never read as green.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-gate-count-prose.ps1
    pwsh -NoProfile -File scripts/quality/assert-gate-count-prose.ps1 -Gate -Quiet
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$RunnerPath,
    [string]$DocPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = if ($RunnerPath) { $RunnerPath } else { Join-Path $PSScriptRoot 'assert-fast-gates.ps1' }
$doc = if ($DocPath) { $DocPath } else { Join-Path $repoRoot 'docs/BUILD_TEST_FAST_PATH.md' }

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

if ($claimedCount -eq $liveCount) {
    if (-not $Quiet) {
        Write-Host 'assert-gate-count-prose: PASS (the documented gate count matches the table).' -ForegroundColor Green
    }
    exit 0
}

# Single-quoted format strings: the gate names are written with backticks, and inside a
# double-quoted string PowerShell reads `f and `a as form feed and bell rather than as text.
Write-Host ''
Write-Host ('assert-gate-count-prose: the `fg` row claims {0} gates, the table holds {1}.' -f
    $claimedCount, $liveCount) -ForegroundColor Red
Write-Host ('  {0} - update the count in the `a.ps1 fg` row.' -f $doc) -ForegroundColor Red
Write-Host '  The wall clock in the same row is a separate claim: re-measure it before editing it,' -ForegroundColor Yellow
Write-Host '  and leave the dated S2451 figures elsewhere in that document alone.' -ForegroundColor Yellow

if (-not $Gate) { exit 0 }

exit 1
