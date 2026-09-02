# Run-Tests.ps1 (S2324) - regression suite for scripts/spec_catalog/check-probe-present.ps1 and the
# shared helper it decides with, scripts/quality/lib/blockneedusertest-probes.ps1.
#
# What broke: the debug-probe invariant is an equivalence, and only one direction was guarded. A
# probe whose ticket is not in BlockNeedUserTest fails assert-no-ticket-logs.ps1 outright; a ticket
# ENTERING BlockNeedUserTest with no probe was refused by nothing, because Assert-ClosingGates
# guarded only Implemented and Verified. Measured 2026-09-02: 20 tickets in that state, and the set
# had turned over since 2026-09-01 rather than sitting still.
#
# The cases that matter here are the ones where a naive implementation looks correct:
#   * a probe split across physical lines is a real probe - a per-line search misses it, and that
#     miss would make this gate refuse a transition assert-no-ticket-logs.ps1 is content with,
#   * a probe id inside a comment is not a probe,
#   * ticket S9998's probe must not satisfy ticket S9999,
#   * an excused ticket passes with no probe anywhere,
#   * a malformed id exits 2, so "could not look" stays distinct from "looked and refused".
#
# The last case is the important one: it asserts the S1621 property directly, by running BOTH
# implementations over the SAME live tree and demanding the same answer for every BlockNeedUserTest
# ticket. A suite that only exercised fixtures could pass while the two gates disagreed in
# production, which is the failure this helper exists to prevent.
#
# Fixtures live under temp/scratch/ - never in app_v2/ or wear/, where a fake probe would be read by
# the real gate as a live ticket tag.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/check-probe-present.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the fixtures could not be prepared.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gatePs1 = Join-Path $repoRoot 'scripts/spec_catalog/check-probe-present.ps1'
$libPs1 = Join-Path $repoRoot 'scripts/quality/lib/blockneedusertest-probes.ps1'
$treeGatePs1 = Join-Path $repoRoot 'scripts/quality/assert-no-ticket-logs.ps1'

foreach ($required in @($gatePs1, $libPs1, $treeGatePs1)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "Missing required script: $required" -ErrorAction Continue
        exit 2
    }
}

. $libPs1

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name" -ForegroundColor Red
        if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

# ---------------------------------------------------------------------------
# Fixtures: a throwaway source root that looks like a module to the helper.
# ---------------------------------------------------------------------------
$fixtureRoot = Join-Path $repoRoot 'temp/scratch/s2324-probe-fixtures'
if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
$fixtureSrc = Join-Path $fixtureRoot 'src'
New-Item -ItemType Directory -Path $fixtureSrc -Force | Out-Null

Set-Content -LiteralPath (Join-Path $fixtureSrc 'SingleLine.kt') -Value @'
package fixture

class SingleLine {
    fun run() {
        Timber.d("S9001: single line probe")
    }
}
'@

# The case a per-line grep gets wrong.
Set-Content -LiteralPath (Join-Path $fixtureSrc 'MultiLine.kt') -Value @'
package fixture

class MultiLine {
    fun run() {
        Timber.d(
            "S9002: probe whose string sits on a later physical line"
        )
    }
}
'@

Set-Content -LiteralPath (Join-Path $fixtureSrc 'CommentOnly.kt') -Value @'
package fixture

class CommentOnly {
    fun run() {
        // Timber.d("S9003: commented out, so not a probe")
        doWork()
    }
}
'@

Set-Content -LiteralPath (Join-Path $fixtureSrc 'Neighbour.kt') -Value @'
package fixture

class Neighbour {
    fun run() {
        Timber.d("S9004: a probe belonging to a different ticket")
    }
}
'@

$roots = @($fixtureRoot)

Write-Host ""
Write-Host "check-probe-present.tests"
Write-Host ""

# --- helper-level cases -----------------------------------------------------
$r1 = Test-TicketProbeInSource -Id 'S9001' -SourceRoots $roots
Assert-That "a single-line probe is found" ($r1.Found -eq $true) ("Found=" + $r1.Found)

$r2 = Test-TicketProbeInSource -Id 'S9002' -SourceRoots $roots
Assert-That "a probe split across physical lines is found" ($r2.Found -eq $true) `
    "a per-line search would miss this one, and the tree gate does not"

$r3 = Test-TicketProbeInSource -Id 'S9003' -SourceRoots $roots
Assert-That "a probe inside a comment is not a probe" ($r3.Found -eq $false) ("Found=" + $r3.Found)

$r4 = Test-TicketProbeInSource -Id 'S9005' -SourceRoots $roots
Assert-That "a neighbour's probe does not satisfy another ticket" ($r4.Found -eq $false) `
    "S9004's probe must not answer for S9005"

# --- baseline parse ---------------------------------------------------------
$baselineFixture = Join-Path $fixtureRoot 'baseline.txt'
Set-Content -LiteralPath $baselineFixture -Value @'
# comment line
S9100  a stated reason
S9101
S9102  another stated reason
'@
$excused = Get-ExcusedProbeTickets -BaselinePath $baselineFixture
Assert-That "a baseline row with a reason is read" ($excused.Contains('S9100')) "S9100 missing"
Assert-That "a baseline row with no reason does not count" (-not $excused.Contains('S9101')) `
    "an id with no reason is anonymous debt, which the allow-list exists to prevent"
Assert-That "a comment line is not an id" ($excused.Count -eq 2) ("count=" + $excused.Count)

# --- invocation contract ----------------------------------------------------
$bad = & $pwshExe -NoProfile -File $gatePs1 -Id 'nonsense' 2>&1
$badCode = $LASTEXITCODE
Assert-That "a malformed id exits 2, not 1" ($badCode -eq 2) ("exit=$badCode")

$unknown = & $pwshExe -NoProfile -File $gatePs1 -Id 'S9999' 2>&1
$unknownCode = $LASTEXITCODE
Assert-That "an id absent from the catalog exits 2" ($unknownCode -eq 2) ("exit=$unknownCode")

# --- the S1621 property: both implementations, one tree, same answer --------
# The tree gate names every BlockNeedUserTest ticket it considers unprobed. The closing gate is
# asked about each of those, plus a sample of the tickets the tree gate did NOT name. Disagreement
# in either direction is the defect this helper was extracted to prevent.
$treeOut = & $pwshExe -NoProfile -File $treeGatePs1 2>&1 | Out-String
$treeMissing = [System.Collections.Generic.List[string]]::new()
foreach ($line in ($treeOut -split "`r?`n")) {
    if ($line -match '^\s+(?<id>S\d{4})\s+- status BlockNeedUserTest but no Timber') {
        $treeMissing.Add($Matches['id'])
    }
}

$catalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'
$blocked = [System.Collections.Generic.List[string]]::new()
foreach ($line in Get-Content -LiteralPath $catalog) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $rec = $line | ConvertFrom-Json } catch { continue }
    if ($rec.status -eq 'BlockNeedUserTest') { $blocked.Add($rec.id) }
}

$disagreements = [System.Collections.Generic.List[string]]::new()
foreach ($id in $blocked) {
    & $pwshExe -NoProfile -File $gatePs1 -Id $id > $null 2>&1
    $closingSaysMissing = ($LASTEXITCODE -eq 1)
    $treeSaysMissing = $treeMissing.Contains($id)
    if ($closingSaysMissing -ne $treeSaysMissing) {
        $disagreements.Add(("{0} (tree={1}, closing={2})" -f $id, $treeSaysMissing, $closingSaysMissing))
    }
}
Assert-That "tree gate and closing gate agree on every BlockNeedUserTest ticket" `
    ($disagreements.Count -eq 0) (($disagreements -join '; '))

Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host ("check-probe-present.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
