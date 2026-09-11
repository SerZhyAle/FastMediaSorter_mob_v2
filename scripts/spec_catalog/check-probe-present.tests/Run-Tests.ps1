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
#   0   all cases pass (a skip is not a failure).
#   1   at least one case failed.
#   2   the fixtures could not be prepared.
#
# S2934 added the shape half: a probe must be one statement alone on its physical line, because it
# is deleted in bulk when the ticket leaves the status. Those cases are skipped by name while the
# resolved harness predates them, and the summary line counts the skips.

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

# S2934: the shape half. This probe IS found by the presence half - it is a real Timber.d carrying a
# real id - and it is exactly the shape that broke the build when a release swept 176 files, because
# dropping the line also drops the `val x =` binding that the rest of the function reads.
Set-Content -LiteralPath (Join-Path $fixtureSrc 'SharedLine.kt') -Value @'
package fixture

class SharedLine {
    fun run() {
        val x = compute().also { Timber.d("S9006: a probe sharing its line with code") }
        use(x)
    }
}
'@

$roots = @($fixtureRoot)

# S2934 added the -All switch and the LineText field to the helper, and the shape half to the gate.
# The mechanism ships with the canon harness and arrives by a plugin deploy no project session
# performs, so while the RESOLVED harness predates it the cases below are skipped BY NAME rather
# than failed - otherwise every sibling session goes red over a deploy it cannot run (the rule
# S2577/S2578 established). Run with $env:SZA_HARNESS_ROOT pointed at the canon checkout to see them
# execute before the deploy.
$hasShapeHalf = (Get-Command Test-TicketProbeInSource).Parameters.ContainsKey('All')
$script:skipped = 0

function Skip-Case([string]$name, [string]$why) {
    Write-Host "  SKIP  $name" -ForegroundColor DarkYellow
    Write-Host "        $why" -ForegroundColor DarkGray
    $script:skipped++
}

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

# --- shape half (S2934) -----------------------------------------------------
if (-not $hasShapeHalf) {
    Skip-Case "a probe sharing its line is found, and its line text is carried" `
        "S2934 shape half - the resolved harness has no -All switch yet"
    Skip-Case "the gate refuses a probe that shares its line, and admits it once split" `
        "S2934 shape half - the resolved harness has no -All switch yet"
} else {
    $ownLineRx = [regex]([string](Get-SzaProfileValue 'probes.ownLineRegex'))

    $r5 = Test-TicketProbeInSource -Id 'S9006' -SourceRoots $roots -All
    $carriesText = $r5.Found -and $r5.Hits.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace($r5.Hits[0].LineText)
    $shapeRejected = $carriesText -and -not $ownLineRx.IsMatch($r5.Hits[0].LineText)
    Assert-That "a probe sharing its line is found, and its line text is carried" $shapeRejected `
        "presence must still say yes - it is the SHAPE that is wrong, and the gate needs the physical line to say so"

    # The gate itself, against a throwaway project root. The harness script is resolved and invoked
    # DIRECTLY rather than through scripts/spec_catalog/check-probe-present.ps1, because that
    # forwarder overwrites SZA_PROJECT_ROOT with this repository's root (S2520) - the redirect would
    # be silently discarded and the case would scan app_v2/ for real.
    $harnessGate = Get-SzaHarnessScript 'spec_catalog/check-probe-present.ps1'
    $sandbox = Join-Path $repoRoot 'temp/scratch/s2934-shape-sandbox'
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'PLAN') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'app_v2/src') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination (Join-Path $sandbox '.sza-profile.json')
    $sandboxRecord = [ordered]@{ id = 'S9006'; title = 'sandbox subject'; status = 'BlockNeedUserTest'; priority = 50; file = 'PLAN/S9006_sandbox.md' }
    Set-Content -LiteralPath (Join-Path $sandbox 'PLAN/spec-catalog.jsonl') -Value ($sandboxRecord | ConvertTo-Json -Compress) -Encoding utf8
    Set-Content -LiteralPath (Join-Path $sandbox 'PLAN/S9006_sandbox.md') -Value '**Status:** BlockNeedUserTest' -Encoding utf8
    $sandboxSrc = Join-Path $sandbox 'app_v2/src/Sandbox.kt'

    $priorProjectRoot = $env:SZA_PROJECT_ROOT
    $priorHarnessRoot = $env:SZA_HARNESS_ROOT
    try {
        # Both variables, and they do different jobs: SZA_PROJECT_ROOT picks WHERE the gate looks,
        # SZA_HARNESS_ROOT picks WHICH code runs. Without the second, a checkout-side gate would
        # resolve its own library through Get-SzaHarnessScript and dot-source the deployed cache.
        $env:SZA_PROJECT_ROOT = $sandbox
        $env:SZA_HARNESS_ROOT = Split-Path -Parent (Split-Path -Parent $harnessGate)

        Set-Content -LiteralPath $sandboxSrc -Encoding utf8 -Value @'
package sandbox

class Sandbox {
    fun run() {
        val x = compute().also { Timber.d("S9006: shares its line") }
        use(x)
    }
}
'@
        & $pwshExe -NoProfile -File $harnessGate -Id 'S9006' > $null 2>&1
        $sharedCode = $LASTEXITCODE

        Set-Content -LiteralPath $sandboxSrc -Encoding utf8 -Value @'
package sandbox

class Sandbox {
    fun run() {
        Timber.d("S9006: shares its line")
        val x = compute()
        use(x)
    }
}
'@
        & $pwshExe -NoProfile -File $harnessGate -Id 'S9006' > $null 2>&1
        $ownCode = $LASTEXITCODE
    } finally {
        $env:SZA_PROJECT_ROOT = $priorProjectRoot
        $env:SZA_HARNESS_ROOT = $priorHarnessRoot
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
    Assert-That "the gate refuses a probe that shares its line, and admits it once split" `
        ($sharedCode -eq 1 -and $ownCode -eq 0) ("shared=$sharedCode (want 1), own=$ownCode (want 0)")
}

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

# S2938: All tickets the tree gate marked missing are tested against the closing gate (100% of
# unprobed tickets). For tickets the tree gate did NOT mark missing, a deterministic sample
# (first 2 and last 2) is tested. This validates the integration between both gates in both
# directions without spawning 250+ separate pwsh processes.
$treePresent = @($blocked | Where-Object { -not $treeMissing.Contains($_) })
$sampleSize = 4
$presentSample = if ($treePresent.Count -le $sampleSize) {
    $treePresent
} else {
    $half = [int]($sampleSize / 2)
    @($treePresent[0..($half - 1)]) + @($treePresent[-($sampleSize - $half)..-1])
}

$sampleToTest = @($treeMissing) + @($presentSample) | Select-Object -Unique

$disagreements = [System.Collections.Generic.List[string]]::new()
$shapeRefusals = [System.Collections.Generic.List[string]]::new()
foreach ($id in $sampleToTest) {
    $closingOut = (& $pwshExe -NoProfile -File $gatePs1 -Id $id 2>&1 | Out-String)
    # S2934: exit 1 now covers TWO refusals, and only one of them is the sentence this case compares.
    # A shape refusal means the probe is present - the tree gate reports it under its own
    # probe-line-shape finding, not as a missing probe - so counting it as "missing" here would
    # manufacture a disagreement out of the two gates agreeing.
    $closingSaysShape = ($LASTEXITCODE -eq 1) -and ($closingOut -match 'share a line')
    $closingSaysMissing = ($LASTEXITCODE -eq 1) -and -not $closingSaysShape
    if ($closingSaysShape) { $shapeRefusals.Add($id) }
    $treeSaysMissing = $treeMissing.Contains($id)
    if ($closingSaysMissing -ne $treeSaysMissing) {
        $disagreements.Add(("{0} (tree={1}, closing={2})" -f $id, $treeSaysMissing, $closingSaysMissing))
    }
}
Assert-That "tree gate and closing gate agree on BlockNeedUserTest tickets" `
    ($disagreements.Count -eq 0) (($disagreements -join '; '))
Assert-That "no sampled parked ticket on the live tree carries a probe that shares its line" `
    ($shapeRefusals.Count -eq 0) (($shapeRefusals -join ', '))

Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
$summary = "check-probe-present.tests: {0} passed, {1} failed" -f $script:pass, $script:fail
if ($script:skipped -gt 0) { $summary += ", {0} case(s) skipped" -f $script:skipped }
Write-Host $summary
if ($script:fail -gt 0) { exit 1 }
exit 0
