<#
.SYNOPSIS
    Regression suite for scripts/spec_catalog/plan-tick.ps1 (S1596).

.DESCRIPTION
    The failure that matters is not "the tool did nothing" - it is "the tool wrote something and
    said it had not", or "it wrote the wrong step". So the negative cases assert that the FILE is
    unchanged, never merely that the exit code is non-zero.

    Isolation: every case runs against a disposable plan folder the suite builds and destroys,
    PLAN/S9991_plan-tick-probe/, and a disposable compact file PLAN/S9992_plan-tick-compact-probe.md
    for the embedded-phase cases (S2666). Both ids come from the fixed reserved block, never from
    next-id.ps1, and no catalog row is ever inserted - the tool under test reads plan files only.

    The compact cases skip by name while the resolved harness predates the canon edit, because only
    the owner can deploy the plugin and a red suite would block every session that cannot fix it.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
    2 - the harness itself could not run.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$tick = Join-Path $root 'scripts\spec_catalog\plan-tick.ps1'
if (-not (Test-Path -LiteralPath $tick)) {
    Write-Error "plan-tick.tests: script under test not found at $tick" -ErrorAction Continue
    exit 2
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}

$fixture = Join-Path $root 'PLAN\S9991_plan-tick-probe'
$phaseFile = Join-Path $fixture 'PHASE_01__probe.md'
$indexFile = Join-Path $fixture 'INDEX.md'

# S2666: the compact layout keeps its phases inside the strategic file, so its fixture is one file
# and no folder at all - a compact ticket has no INDEX.md by construction. Two phases, because the
# failure this shape invites is a tick in one phase rewriting the other one's counter and header.
$compactFile = Join-Path $root 'PLAN\S9992_plan-tick-compact-probe.md'

$failures = New-Object System.Collections.Generic.List[string]
$cases = 0
$skipped = 0

function Assert-That {
    param(
        [Parameter(Mandatory)][string]$Case,
        [Parameter(Mandatory)][string]$What,
        [Parameter(Mandatory)][bool]$Condition,
        [string]$Detail = ''
    )
    if ($Condition) {
        Write-Host "  ok   $Case - $What" -ForegroundColor DarkGray
    } else {
        $script:failures.Add("$Case - $What$(if ($Detail) { " ($Detail)" })")
        Write-Host "  FAIL $Case - $What $Detail" -ForegroundColor Red
    }
}

function Reset-Fixture {
    param([string]$IndexSteps = '1/3')
    if ([IO.Directory]::Exists($fixture)) { [IO.Directory]::Delete($fixture, $true) }
    [IO.Directory]::CreateDirectory($fixture) | Out-Null
    $phaseBody = @(
        '# Phase 01 - Probe', '',
        # S1723: the fixture carries the same header a real phase file does. Without these three lines the
        # header rule had nothing to act on and its assertions passed vacuously - the shape of test that
        # reports success while checking nothing.
        '**Status:** ⬜ Not started',
        '**Steps done:** 1 / 3',
        '**Started:** -',
        '**Completed:** -', '',
        '## Prerequisites', '',
        '- [ ] Working tree is clean', '- [ ] Research read', '',
        '## Steps', '',
        '### Step 01.1 - a', '', '**Status:** `[x]` done', '', '---', '',
        '### Step 01.2 - b', '', '**Status:** `[ ]` not done', '', '---', '',
        '### Step 01.3 - c', '', '**Status:** `[ ]` not done', '', '---', '',
        '## Phase Done Criteria', '',
        '- [ ] Working tree still clean', ''
    ) -join "`n"
    [IO.File]::WriteAllText($phaseFile, $phaseBody)
    $indexBody = @(
        '# Tactical Plan: S9991 - probe', '',
        '**Phases:** 0 / 2 done',
        '**Last updated:** 2020-01-01', '',
        '| # | Phase | Depends on | Status | Steps | File |',
        '|---|-------|-----------|--------|------:|------|',
        "| 01 | probe | - | 🚧 In Progress | $IndexSteps | [PHASE_01__probe.md](PHASE_01__probe.md) |",
        '| 02 | other | 01 | ⬜ Not started | 0/2 | [PHASE_02__other.md](PHASE_02__other.md) |', ''
    ) -join "`n"
    [IO.File]::WriteAllText($indexFile, $indexBody)
}

function Reset-CompactFixture {
    $body = @(
        '# Strategic spec: S9992 - compact probe', '',
        '**Ticket:** S9992',
        # A strategic header carrying its own **Status:** line, because the phase-header rule must
        # not reach outside the phase block and rewrite the ticket's lifecycle status.
        '**Status:** In Progress', '',
        '## 1. Problem', '',
        'The tool used to resolve a tactical folder only.', '',
        '---', '',
        '# Phase 01 - First', '',
        '**Status:** ⬜ Not started',
        '**Steps done:** 0 / 2',
        '**Started:** -',
        '**Completed:** -', '',
        '## Steps', '',
        '### Step 01.1 - a', '', '**Status:** `[ ]` not done', '', '---', '',
        '### Step 01.2 - b', '', '**Status:** `[ ]` not done', '', '---', '',
        '## Phase Done Criteria', '',
        '- [ ] Compact gate ticked', '',
        '---', '',
        '# Phase 02 - Second', '',
        '**Status:** ⬜ Not started',
        '**Steps done:** 0 / 1',
        '**Started:** -',
        '**Completed:** -', '',
        '## Steps', '',
        '### Step 02.1 - c', '', '**Status:** `[ ]` not done', ''
    ) -join "`n"
    [IO.File]::WriteAllText($compactFile, $body, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-CompactPhaseBlock {
    param([Parameter(Mandatory)][string]$Heading)
    $text = [IO.File]::ReadAllText($compactFile)
    $start = $text.IndexOf($Heading)
    if ($start -lt 0) { return '' }
    $next = $text.IndexOf("`n# ", $start + $Heading.Length)
    if ($next -lt 0) { return $text.Substring($start) }
    return $text.Substring($start, $next - $start)
}

function Invoke-Tick {
    param([string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $captured = & $pwshExe -NoProfile -File $tick @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return [PSCustomObject]@{ exitCode = $code; text = ($captured | Out-String).Trim() }
}

# S2666: the local plan-tick.ps1 is a generated forwarder, so the SUBJECT of the compact cases is
# the shipped harness, and between the canon edit and the owner's deploy those are different files.
# A suite that went red in the meantime would hand every neighbouring session a failure no session
# running it can fix, so the compact cases skip by name instead (S2577, S2578, S2581 met this first).
# The candidate order mirrors the FORWARDER's, not Get-SzaHarnessScript's answer alone: that helper
# returns the plugin-cache path whether or not the file is there, while a forwarder whose cache
# probe misses falls through to the canon checkout - a suite that skips work it could have done is
# the same defect as one that passes without looking.
. (Join-Path $root 'scripts/spec_catalog/_status-sets.ps1')
$tickSourcePath = ''
$tickCandidates = @()
if ($env:SZA_HARNESS_ROOT) { $tickCandidates += (Join-Path $env:SZA_HARNESS_ROOT 'spec_catalog\plan-tick.ps1') }
try { $tickCandidates += (Get-SzaHarnessScript 'spec_catalog/plan-tick.ps1') } catch { }
try {
    . (Join-Path $root 'scripts/utils/project-paths.ps1')
    $canonRoot = Get-CanonRoot
    if ($canonRoot) { $tickCandidates += (Join-Path $canonRoot 'tools\harness\spec_catalog\plan-tick.ps1') }
} catch { }
foreach ($candidatePath in $tickCandidates) {
    if ($candidatePath -and (Test-Path -LiteralPath $candidatePath)) { $tickSourcePath = $candidatePath; break }
}
# A CLI's functions never enter the caller's scope, so Get-Command would answer "absent" whatever is
# deployed. Read the very file the CLI below will run instead.
$compactCapable = $tickSourcePath -and
    (([IO.File]::ReadAllText($tickSourcePath)).IndexOf('Resolve-PhaseSurface', [StringComparison]::Ordinal) -ge 0)

try {
    # ---- case A: one call rewrites exactly the listed markers -----------------
    Write-Host 'case A - a batch rewrites exactly the listed steps' -ForegroundColor Cyan
    Reset-Fixture
    $before = [IO.File]::ReadAllText($phaseFile)
    $a = Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2,3', '-State', 'Done')
    $body = [IO.File]::ReadAllLines($phaseFile)
    $doneCount = @($body | Where-Object { $_ -match '^\*\*Status:\*\*\s*`\[x\]`' }).Count
    Assert-That -Case 'A' -What 'exit 0' -Condition ($a.exitCode -eq 0) -Detail $a.text
    Assert-That -Case 'A' -What 'three markers done' -Condition ($doneCount -eq 3) -Detail "$doneCount"
    Assert-That -Case 'A' -What 'file actually changed' -Condition (([IO.File]::ReadAllText($phaseFile)) -ne $before)
    $cases++

    # ---- case B: index and phase header recomputed ----------------------------
    Write-Host 'case B - both counters follow the phase file' -ForegroundColor Cyan
    $indexText = [IO.File]::ReadAllText($indexFile)
    Assert-That -Case 'B' -What 'steps cell recomputed' -Condition ($indexText -match '\|\s*3/3\s*\|')
    Assert-That -Case 'B' -What 'row flipped to done' -Condition ($indexText -match '✅ Done')
    Assert-That -Case 'B' -What 'phases header recomputed' -Condition ($indexText -match '\*\*Phases:\*\* 1 / 2 done')
    Assert-That -Case 'B' -What 'last updated is not the stale date' -Condition ($indexText -notmatch '2020-01-01')
    Assert-That -Case 'B' -What 'phase header recomputed' -Condition (([IO.File]::ReadAllText($phaseFile)) -match '\*\*Steps done:\*\* 3 / 3')
    $cases++

    # ---- case C: reversal is byte-exact --------------------------------------
    Write-Host 'case C - a wrong batch is undone the way it was made' -ForegroundColor Cyan
    Reset-Fixture
    $pristine = [IO.File]::ReadAllText($phaseFile)
    Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2,3', '-State', 'Done') | Out-Null
    Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2,3', '-State', 'NotDone') | Out-Null
    $restored = [IO.File]::ReadAllText($phaseFile)
    # The Step Log the Done pass appended is a deliberate survivor: the record of what happened
    # is not erased by changing the state back. Compare the markers, not the whole file.
    # S1723: step markers only, not the phase header. The header is recomputed from the markers rather than
    # restored, and that is deliberate - this fixture starts with "Not started" beside "1 / 3", and a
    # round trip through the tool leaves it saying "In Progress", which is the truth. Comparing the header
    # here would assert that the tool preserves a lie it was written to remove.
    $markerLines = @([IO.File]::ReadAllLines($phaseFile) | Where-Object { $_ -match '^\*\*Status:\*\*\s*`' })
    $pristineMarkers = @($pristine -split "`n" | Where-Object { $_ -match '^\*\*Status:\*\*\s*`' })
    Assert-That -Case 'C' -What 'markers restored exactly' -Condition (($markerLines -join '|') -eq ($pristineMarkers -join '|')) -Detail ($markerLines -join ' / ')
    Assert-That -Case 'C' -What 'step log survives the reversal' -Condition ($restored -match '\*\*Step Log:\*\*')
    $cases++

    # ---- case D: unknown step writes nothing ---------------------------------
    Write-Host 'case D - an unknown step number writes nothing' -ForegroundColor Cyan
    Reset-Fixture
    $before = [IO.File]::ReadAllText($phaseFile)
    $d = Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2,9', '-State', 'Done')
    Assert-That -Case 'D' -What 'non-zero exit' -Condition ($d.exitCode -ne 0) -Detail "exit=$($d.exitCode)"
    Assert-That -Case 'D' -What 'file byte-identical' -Condition (([IO.File]::ReadAllText($phaseFile)) -eq $before)
    $cases++

    # ---- case E: pre-existing divergence is reported, not overwritten --------
    Write-Host 'case E - a divergence is reported, not silently fixed' -ForegroundColor Cyan
    Reset-Fixture -IndexSteps '2/3'
    $beforePhase = [IO.File]::ReadAllText($phaseFile)
    $beforeIndex = [IO.File]::ReadAllText($indexFile)
    $e = Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2', '-State', 'Done')
    Assert-That -Case 'E' -What 'exit 3' -Condition ($e.exitCode -eq 3) -Detail "exit=$($e.exitCode)"
    Assert-That -Case 'E' -What 'names both counts' -Condition ($e.text -match '2/3' -and $e.text -match '1/3') -Detail $e.text
    Assert-That -Case 'E' -What 'phase file untouched' -Condition (([IO.File]::ReadAllText($phaseFile)) -eq $beforePhase)
    Assert-That -Case 'E' -What 'index untouched' -Condition (([IO.File]::ReadAllText($indexFile)) -eq $beforeIndex)

    $reconciled = Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2', '-State', 'Done', '-Reconcile')
    $reconciledIndex = [IO.File]::ReadAllText($indexFile)
    $reconciledPhase = [IO.File]::ReadAllText($phaseFile)
    Assert-That -Case 'E' -What 'explicit reconcile succeeds' -Condition ($reconciled.exitCode -eq 0) -Detail $reconciled.text
    Assert-That -Case 'E' -What 'reconcile writes the true counters' -Condition (
        $reconciledIndex -match '\|\s*2/3\s*\|' -and $reconciledPhase -match '\*\*Steps done:\*\* 2 / 3'
    )
    $cases++

    # ---- case F: an ambiguous checkbox fragment refuses ----------------------
    Write-Host 'case F - an ambiguous checkbox fragment refuses and names candidates' -ForegroundColor Cyan
    Reset-Fixture
    $before = [IO.File]::ReadAllText($phaseFile)
    $f = Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Checkbox', 'Working tree', '-State', 'Done')
    Assert-That -Case 'F' -What 'non-zero exit' -Condition ($f.exitCode -ne 0) -Detail "exit=$($f.exitCode)"
    Assert-That -Case 'F' -What 'names more than one candidate line' -Condition ($f.text -match 'matched 2') -Detail $f.text
    Assert-That -Case 'F' -What 'file byte-identical' -Condition (([IO.File]::ReadAllText($phaseFile)) -eq $before)
    $unique = Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Checkbox', 'Research read', '-State', 'Done')
    $boxes = @([IO.File]::ReadAllLines($phaseFile) | Where-Object { $_ -match '^\s*-\s*\[x\]' })
    Assert-That -Case 'F' -What 'a unique fragment flips exactly one box' -Condition ($unique.exitCode -eq 0 -and $boxes.Count -eq 1) -Detail "$($boxes.Count) ticked"
    $cases++

    # ---- case G: one Step Log line per step moved to Done --------------------
    Write-Host 'case G - the durable trace is one line per step' -ForegroundColor Cyan
    Reset-Fixture
    Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '2,3', '-State', 'Done') | Out-Null
    $logLines = @([IO.File]::ReadAllLines($phaseFile) | Where-Object { $_ -match 'state set to done for S9991 step' })
    Assert-That -Case 'G' -What 'exactly two log lines' -Condition ($logLines.Count -eq 2) -Detail "$($logLines.Count)"
    Assert-That -Case 'G' -What 'each names its own step' -Condition (($logLines -join ' ') -match 'step 01\.2' -and ($logLines -join ' ') -match 'step 01\.3') -Detail ($logLines -join ' | ')
    $cases++

    # ---- case H: the phase file's own header follows its steps (S1723) -------
    Write-Host 'case H - the phase header stops lying about a finished phase' -ForegroundColor Cyan
    Reset-Fixture
    Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '1,2,3', '-State', 'Done') | Out-Null
    $headerDone = [IO.File]::ReadAllText($phaseFile)
    Assert-That -Case 'H' -What 'header says done' -Condition ($headerDone -match '\*\*Status:\*\* ✅ Done')
    Assert-That -Case 'H' -What 'completed date filled' -Condition ($headerDone -notmatch '\*\*Completed:\*\* -')
    Assert-That -Case 'H' -What 'started date filled' -Condition ($headerDone -notmatch '\*\*Started:\*\* -')
    Assert-That -Case 'H' -What 'step markers untouched by the header rule' -Condition ((@([IO.File]::ReadAllLines($phaseFile) | Where-Object { $_ -match '^\*\*Status:\*\* `\[x\]` done' })).Count -eq 3)

    Invoke-Tick -Arguments @('-Id', 'S9991', '-Phase', '01', '-Steps', '3', '-State', 'NotDone') | Out-Null
    $headerReopened = [IO.File]::ReadAllText($phaseFile)
    Assert-That -Case 'H' -What 'reopening drops the header back' -Condition ($headerReopened -match '\*\*Status:\*\* 🚧 In Progress')
    Assert-That -Case 'H' -What 'completion date withdrawn with it' -Condition ($headerReopened -match '\*\*Completed:\*\* -')
    $cases++

    # ---- compact layout (S2666) ----------------------------------------------
    if (-not $compactCapable) {
        Write-Host "  SKIP S2666 compact shape (4 cases) - the resolved harness has no compact phase resolution" -ForegroundColor DarkGray
        $skipped = 4
    } else {
        # ---- case I: a phase embedded in the strategic file ticks ------------
        Write-Host 'case I - a compact phase ticks like a phase file' -ForegroundColor Cyan
        Reset-CompactFixture
        $phase02Before = Get-CompactPhaseBlock -Heading '# Phase 02 - Second'
        $caseI = Invoke-Tick -Arguments @('-Id', 'S9992', '-Phase', '01', '-Steps', '1', '-State', 'Done')
        $phase01 = Get-CompactPhaseBlock -Heading '# Phase 01 - First'
        Assert-That -Case 'I' -What 'exit 0' -Condition ($caseI.exitCode -eq 0) -Detail $caseI.text
        Assert-That -Case 'I' -What 'marker flipped' -Condition ($phase01 -match '(?s)Step 01\.1.*?\*\*Status:\*\* `\[x\]` done')
        Assert-That -Case 'I' -What 'counter recomputed' -Condition ($phase01 -match '\*\*Steps done:\*\* 1 / 2') -Detail $phase01
        Assert-That -Case 'I' -What 'phase header follows its steps' -Condition ($phase01 -match '\*\*Status:\*\* 🚧 In Progress')
        Assert-That -Case 'I' -What 'started date filled' -Condition ($phase01 -notmatch '\*\*Started:\*\* -')
        Assert-That -Case 'I' -What 'step log appended' -Condition ($phase01 -match 'state set to done for S9992 step 01\.1')
        $cases++

        # ---- case J: the sibling phase is not touched -------------------------
        Write-Host 'case J - a tick in one phase leaves the other byte-identical' -ForegroundColor Cyan
        Assert-That -Case 'J' -What 'phase 02 unchanged' -Condition ((Get-CompactPhaseBlock -Heading '# Phase 02 - Second') -eq $phase02Before)
        Assert-That -Case 'J' -What 'the ticket status line is not a phase header' -Condition (([IO.File]::ReadAllText($compactFile)) -match '\*\*Status:\*\* In Progress')
        $j = Invoke-Tick -Arguments @('-Id', 'S9992', '-Phase', '02', '-Steps', '1', '-State', 'Done')
        $phase01After = Get-CompactPhaseBlock -Heading '# Phase 01 - First'
        $phase02After = Get-CompactPhaseBlock -Heading '# Phase 02 - Second'
        Assert-That -Case 'J' -What 'second phase ticks too' -Condition ($j.exitCode -eq 0 -and $phase02After -match '\*\*Steps done:\*\* 1 / 1') -Detail $j.text
        Assert-That -Case 'J' -What 'second phase reads done' -Condition ($phase02After -match '\*\*Status:\*\* ✅ Done')
        Assert-That -Case 'J' -What 'first phase counter survives' -Condition ($phase01After -match '\*\*Steps done:\*\* 1 / 2') -Detail $phase01After
        $cases++

        # ---- case K: -Checkbox reaches the compact phase's own gates ----------
        Write-Host 'case K - a compact phase gate is reachable by fragment' -ForegroundColor Cyan
        Reset-CompactFixture
        $k = Invoke-Tick -Arguments @('-Id', 'S9992', '-Phase', '01', '-Checkbox', 'Compact gate', '-State', 'Done')
        $ticked = @([IO.File]::ReadAllLines($compactFile) | Where-Object { $_ -match '^\s*-\s*\[x\]' })
        Assert-That -Case 'K' -What 'exit 0' -Condition ($k.exitCode -eq 0) -Detail $k.text
        Assert-That -Case 'K' -What 'exactly one bullet ticked' -Condition ($ticked.Count -eq 1) -Detail "$($ticked.Count)"
        $cases++

        # ---- case L: -Target Index says the layout has no index ---------------
        Write-Host 'case L - the index form refuses by name, not by matching nothing' -ForegroundColor Cyan
        $before = [IO.File]::ReadAllText($compactFile)
        $l = Invoke-Tick -Arguments @('-Id', 'S9992', '-Checkbox', 'Compact gate', '-Target', 'Index', '-State', 'Done')
        Assert-That -Case 'L' -What 'exit 2' -Condition ($l.exitCode -eq 2) -Detail "exit=$($l.exitCode)"
        Assert-That -Case 'L' -What 'names INDEX.md and the phase form' -Condition ($l.text -match 'INDEX\.md' -and $l.text -match '-Target Phase') -Detail $l.text
        Assert-That -Case 'L' -What 'file byte-identical' -Condition (([IO.File]::ReadAllText($compactFile)) -eq $before)
        $cases++
    }
} finally {
    if ([IO.File]::Exists($compactFile)) { [IO.File]::Delete($compactFile) }
    if ([IO.Directory]::Exists($fixture)) { [IO.Directory]::Delete($fixture, $true) }
    if ([IO.Directory]::Exists($fixture)) {
        $failures.Add('teardown - the probe plan folder survived deletion')
    }
}

Write-Host ''
if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Host "FAIL $failure" -ForegroundColor Red }
    Write-Error "plan-tick.tests: $($failures.Count) assertion(s) failed across $cases case(s)." -ErrorAction Continue
    exit 1
}

$skipNote = if ($skipped -gt 0) { ", $skipped case(s) skipped" } else { '' }
Write-Host "plan-tick.tests: PASS - $cases cases passed$skipNote (A batch, B counters, C reversal, D unknown step, E divergence, F checkbox, G trace, H phase header, I compact tick, J compact isolation, K compact checkbox, L compact index refusal)." -ForegroundColor Green
exit 0
