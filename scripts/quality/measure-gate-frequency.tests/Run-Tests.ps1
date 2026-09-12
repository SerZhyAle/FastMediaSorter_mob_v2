#requires -Version 7.0
# Subject: scripts/quality/measure-gate-frequency.ps1
<#
.SYNOPSIS
    Regression suite for the placement view of measure-gate-frequency.ps1 (S2537).

.DESCRIPTION
    The placement view is the input to a decision about where a gate runs, so an error in its
    arithmetic moves the decision rather than merely misreporting it. Every case here is judged
    against a fixture journal with known counts: a gate that finds things at a high price, a gate
    that finds nothing, a gate too rarely executed to judge, a gate too cheap to be worth moving,
    and a threshold override that changes the marked set.

    The real journal is never read - a suite judging live telemetry would pass or fail by whatever
    ran on the host that hour, which is the opposite of a contract.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   the fixtures could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$subject = Join-Path $repoRoot 'scripts/quality/measure-gate-frequency.ps1'
$scratch = Join-Path $repoRoot 'temp/scratch/S2537-measure-gate-frequency'
$fixture = Join-Path $scratch 'gate-executions.jsonl'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

function New-FixtureRecord([string]$Gate, [string]$Status, [int]$ElapsedMs, [int]$Index, [string]$RunId = '') {
    $r = [ordered]@{
        timestampUtc = ([datetime]'2026-09-01T00:00:00Z').AddMinutes($Index).ToString('o')
        runner       = 'post-change'
        gate         = $Gate
        status       = $Status
        exitCode     = (($Status -eq 'PASS' -or $Status -eq 'SKIP') ? 0 : 1)
        elapsedMs    = $ElapsedMs
    }
    # S2538: omitted rather than blank when absent. The repository's own journal predates the field,
    # and a reader that cannot tell "no id" from "empty id" would flag all of that history.
    if ($RunId) { $r['runId'] = $RunId }
    return ($r | ConvertTo-Json -Compress)
}

function Invoke-Report([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $subject -Journal $fixture @Arguments 2>&1
    return [pscustomobject]@{
        Code = [int]$LASTEXITCODE
        Text = (($out | ForEach-Object { [string]$_ }) -join "`n")
    }
}

function Get-Row([object]$Report, [string]$Gate) {
    return @($Report.gates | Where-Object { $_.Gate -eq $Gate })[0]
}

try {
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
    [void](New-Item -ItemType Directory -Path $scratch -Force)

    # expensive-with-catches: 40 runs, 2 FAIL, 40 000 s total -> 20 000 s per finding.
    # zero-catch-expensive: 40 runs, no FAIL, 40 000 s total.
    # zero-catch-cheap:     40 runs, no FAIL, 40 s total - below the cost floor, so it stays put.
    # rare-expensive:       5 runs, no FAIL, 50 000 s total - below the sample floor, so unjudged.
    # cheap-with-catches:   40 runs, 20 FAIL, 400 s total - 20 s per finding, the shape to keep.
    $lines = New-Object System.Collections.Generic.List[string]
    $i = 0
    foreach ($n in 1..40) { $lines.Add((New-FixtureRecord 'expensive-with-catches' ($n -le 2 ? 'FAIL' : 'PASS') 1000000 $i)); $i++ }
    foreach ($n in 1..40) { $lines.Add((New-FixtureRecord 'zero-catch-expensive' 'PASS' 1000000 $i)); $i++ }
    foreach ($n in 1..40) { $lines.Add((New-FixtureRecord 'zero-catch-cheap' 'PASS' 1000 $i)); $i++ }
    foreach ($n in 1..5) { $lines.Add((New-FixtureRecord 'rare-expensive' 'PASS' 10000000 $i)); $i++ }
    foreach ($n in 1..40) { $lines.Add((New-FixtureRecord 'cheap-with-catches' ($n -le 20 ? 'FAIL' : 'PASS') 10000 $i)); $i++ }
    # A gate that only ever skipped: it must not count as executed, and must not be a candidate.
    foreach ($n in 1..40) { $lines.Add((New-FixtureRecord 'always-skipped' 'SKIP' 0 $i)); $i++ }
    # stalled-but-cheap reproduces the shape that opened S2537: 40 runs of 10 ms and one run of
    # 34 711 s. Its observed sum makes it the most expensive gate in the fixture; its median makes
    # it the cheapest. It must NOT be a candidate - the mean is what misdirected the first pass.
    foreach ($n in 1..40) { $lines.Add((New-FixtureRecord 'stalled-but-cheap' 'PASS' 10 $i)); $i++ }
    $lines.Add((New-FixtureRecord 'stalled-but-cheap' 'PASS' 34711000 $i)); $i++
    Set-Content -LiteralPath $fixture -Value $lines -Encoding utf8
}
catch {
    Write-Error "measure-gate-frequency.tests: CANNOT VERIFY - fixture preparation failed: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

Write-Host 'measure-gate-frequency.tests (S2537 placement view)' -ForegroundColor Cyan

$run = Invoke-Report @('-Placement', '-Json')
Assert-That 'placement view exits 0 over a fixture journal' ($run.Code -eq 0) "exit $($run.Code)"

$report = $null
try { $report = $run.Text | ConvertFrom-Json } catch { $report = $null }
Assert-That 'placement JSON parses' ($null -ne $report) 'output was not JSON'

if ($null -ne $report) {
    $expensiveCatches = Get-Row $report 'expensive-with-catches'
    Assert-That 'cost per finding is total time divided by findings' ($expensiveCatches.SecPerCatch -eq 20000) "SecPerCatch=$($expensiveCatches.SecPerCatch)"
    Assert-That 'an expensive gate with a high cost per finding is a candidate' ($expensiveCatches.Candidate -eq $true) 'not marked'

    $zeroExpensive = Get-Row $report 'zero-catch-expensive'
    Assert-That 'a gate that found nothing reports an empty cost per finding' ($null -eq $zeroExpensive.SecPerCatch) "SecPerCatch=$($zeroExpensive.SecPerCatch)"
    Assert-That 'an expensive gate that found nothing is a candidate' ($zeroExpensive.Candidate -eq $true) 'not marked'

    $zeroCheap = Get-Row $report 'zero-catch-cheap'
    Assert-That 'a cheap gate that found nothing stays put' ($zeroCheap.Candidate -eq $false) 'marked despite being below the cost floor'

    $rare = Get-Row $report 'rare-expensive'
    Assert-That 'a gate below the sample floor is not judged' ($rare.Candidate -eq $false) 'marked on 5 executions'

    $cheapCatches = Get-Row $report 'cheap-with-catches'
    Assert-That 'a gate that finds things cheaply stays put' ($cheapCatches.Candidate -eq $false) "SecPerCatch=$($cheapCatches.SecPerCatch)"

    $stalled = Get-Row $report 'stalled-but-cheap'
    Assert-That 'a stall does not move the median' ($stalled.MedianSec -lt 1) "MedianSec=$($stalled.MedianSec)"
    Assert-That 'the observed sum still reports the stall' ($stalled.TotalSec -gt 34000) "TotalSec=$($stalled.TotalSec)"
    Assert-That 'a gate that is cheap except for one stall is not a candidate' ($stalled.Candidate -eq $false) "TypicalSec=$($stalled.TypicalSec)"

    $skipped = Get-Row $report 'always-skipped'
    Assert-That 'a skip is not an execution' ($skipped.Executed -eq 0 -and $skipped.Skipped -eq 40) "Executed=$($skipped.Executed) Skipped=$($skipped.Skipped)"
    Assert-That 'a gate that only skipped is not a candidate' ($skipped.Candidate -eq $false) 'marked on skips alone'

    Assert-That 'the journal window is reported' (
        $null -ne $report.windowFrom -and $null -ne $report.windowTo
    ) 'window absent from the JSON'

    Assert-That 'the thresholds in force are reported' (
        $report.thresholds.minExecutions -eq 20 -and $report.thresholds.minTotalSeconds -eq 600 -and $report.thresholds.maxSecondsPerCatch -eq 300
    ) 'thresholds absent or wrong in the JSON'
}

# A threshold override must change the marked set - otherwise the parameters are decoration.
$raised = Invoke-Report @('-Placement', '-MinExecutions', '3', '-Json')
$raisedReport = $null
try { $raisedReport = $raised.Text | ConvertFrom-Json } catch { $raisedReport = $null }
if ($null -ne $raisedReport) {
    $rareRaised = Get-Row $raisedReport 'rare-expensive'
    Assert-That 'lowering the sample floor admits the rare gate' ($rareRaised.Candidate -eq $true) 'still unmarked at -MinExecutions 3'
}
else {
    Assert-That 'lowering the sample floor admits the rare gate' $false 'override run produced no JSON'
}

# Both thresholds must be relaxed: cheap-with-catches is under the cost floor as well, and a case
# that moved only the ceiling would pass for the wrong reason.
$ceiling = Invoke-Report @('-Placement', '-MaxSecondsPerCatch', '5', '-MinTotalSeconds', '100', '-Json')
$ceilingReport = $null
try { $ceilingReport = $ceiling.Text | ConvertFrom-Json } catch { $ceilingReport = $null }
if ($null -ne $ceilingReport) {
    $cheapRaised = Get-Row $ceilingReport 'cheap-with-catches'
    Assert-That 'lowering the cost ceiling admits a gate that finds things cheaply' ($cheapRaised.Candidate -eq $true) 'still unmarked at -MaxSecondsPerCatch 5 -MinTotalSeconds 100'
}
else {
    Assert-That 'lowering the cost ceiling admits a gate that finds things cheaply' $false 'override run produced no JSON'
}

# S2538: the duplicate-row report. The main fixture carries no runId at all - the shape of the
# repository's own history - and must stay silent, because an absent id is not a duplicate.
$quiet = Invoke-Report @('-Placement')
Assert-That 'a journal without run ids reports no duplicates' (
    $quiet.Text -notmatch 'Duplicate rows'
) 'the duplicate report fired on a journal that has no run ids to compare'

$dupFixture = Join-Path $scratch 'gate-executions-runid.jsonl'
$dupLines = New-Object System.Collections.Generic.List[string]
$k = 0
foreach ($run in 1..3) {
    foreach ($g in @('honest-gate', 'double-reporting-gate')) {
        $dupLines.Add((New-FixtureRecord $g 'PASS' 1000 $k "run$run")); $k++
    }
    # The second row for the same (runId, gate) is what a run reporting twice looks like.
    $dupLines.Add((New-FixtureRecord 'double-reporting-gate' 'FAIL' 1000 $k "run$run")); $k++
}
Set-Content -LiteralPath $dupFixture -Value $dupLines -Encoding utf8

$dupRun = & $pwshExe -NoProfile -File $subject -Journal $dupFixture -Placement 2>&1
$dupText = (($dupRun | ForEach-Object { [string]$_ }) -join "`n")
Assert-That 'a gate reported twice in one run is named' (
    $dupText -match 'Duplicate rows' -and $dupText -match 'double-reporting-gate'
) 'the duplicate report did not name the offending gate'
Assert-That 'a gate reported once per run is not named as a duplicate' (
    $dupText -notmatch '  honest-gate:'
) 'a gate with one row per run was reported as duplicated'

# The frequency view must survive the placement columns being added beside it.
$frequency = Invoke-Report @()
Assert-That 'the frequency view still exits 0' ($frequency.Code -eq 0) "exit $($frequency.Code)"

$absent = & $pwshExe -NoProfile -File $subject -Journal (Join-Path $scratch 'no-such-journal.jsonl') 2>&1
Assert-That 'an absent journal is could-not-verify, not a finding' ([int]$LASTEXITCODE -eq 2) "exit $LASTEXITCODE"

if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }

Write-Host ""
Write-Host ("measure-gate-frequency.tests: {0} passed, {1} failed." -f $script:pass, $script:fail) -ForegroundColor ($script:fail -gt 0 ? 'Red' : 'Green')
exit ($script:fail -gt 0 ? 1 : 0)
