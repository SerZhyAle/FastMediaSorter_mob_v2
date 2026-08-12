<#
.SYNOPSIS
    Regression suite for scripts/spec_catalog/plan-tick.ps1 (S1596).

.DESCRIPTION
    The failure that matters is not "the tool did nothing" - it is "the tool wrote something and
    said it had not", or "it wrote the wrong step". So the negative cases assert that the FILE is
    unchanged, never merely that the exit code is non-zero.

    Isolation: every case runs against a disposable plan folder the suite builds and destroys,
    PLAN/S9991_plan-tick-probe/. The id comes from the fixed reserved block, never from
    next-id.ps1, and no catalog row is ever inserted - the tool under test reads plan files only.

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

$failures = New-Object System.Collections.Generic.List[string]
$cases = 0

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
        '**Steps done:** 1 / 3', '',
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
    $markerLines = @([IO.File]::ReadAllLines($phaseFile) | Where-Object { $_ -match '^\*\*Status:\*\*' })
    $pristineMarkers = @($pristine -split "`n" | Where-Object { $_ -match '^\*\*Status:\*\*' })
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
} finally {
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

Write-Host "plan-tick.tests: PASS - $cases cases passed (A batch, B counters, C reversal, D unknown step, E divergence, F checkbox, G trace)." -ForegroundColor Green
exit 0
