#requires -Version 7.0
<#
.SYNOPSIS
    Release-scope gate: a budgeted performance metric must have a fresh measurement, and that
    measurement must stay inside the budget plus its stated tolerance.

.DESCRIPTION
    S3371 phase 05. Performance was an adjective in this repository - "faster", "snappier",
    "noticeably better" - and an adjective cannot regress, so nothing ever refused a change that
    gave back what an earlier ticket won. This gate turns one number at a time into a refusal.

    THE FIRST BUDGET IS THE ONE THAT WAS JUST WON. S3368 took the Wear cold start from 5292 ms to
    4503 ms, and the budget is 4503 rather than 5292 deliberately: a budget set at the number the
    work started from passes on the very regression it exists to catch. The tolerance carries the
    measurement noise instead, which is what a tolerance is for.

    TWO FILES, ONE COMPARISON.

      scripts/quality/perf-budgets.json - the budget. Per metric: the unit, the budget value, the
      tolerance as a percentage, the direction, the producer that measures it, and the SOURCE of
      the number (which ticket measured it, on what date, and what it measured against). The source
      block is what makes a budget re-judgeable in a year instead of a constant nobody dares touch.

      -Measured <file> - a measurement artifact produced by that producer, in the shape documented
      under -Measured below. The release flow's measurement step names the file it passes.

    WHY A RELEASE-SCOPE GATE AND NOT A CLOSURE ONE (Rule 33). The subject is a measured artifact,
    not a changed file: a cold start regresses because of a new dependency, an added initializer,
    a Hilt entry point, or something nobody in this repository wrote at all, so no changed set can
    be blamed for it and no author could have been asked. The measurement also needs a device and
    minutes, which the per-ticket closure must not spend. ADR-4 of the strategic spec keeps the
    whole measurement contour out of PR for the same reason.

    "NO FRESH MEASUREMENT" IS NOT A PASS. Run with no -Measured, the gate judges the recorded
    source measurement against staleAfterDays and answers exit 2 - could not verify - once it is
    older than that horizon. A release that ships without re-measuring a budgeted metric has not
    proved the budget holds; it has only failed to look, and the two must not print the same word.

.PARAMETER Gate
    Exit non-zero on any finding. Without it the script reports and exits 0, which is the read-only
    mode used while correcting a budget.

.PARAMETER Measured
    Path to a measurement artifact. Shape:

      { "measuredOn": "2026-09-22",
        "measurements": [
          { "metric": "wear-cold-start", "value": 4503, "unit": "ms",
            "source": "scripts/utils/wear-cold-start-measure.ps1 -Runs 5" } ] }

    Every budgeted metric must appear. A metric the artifact does not carry is "could not verify"
    (exit 2), never a pass - an artifact that silently omits the slow metric is exactly how a
    budget stops meaning anything.

.PARAMETER BudgetPath
    Read this budget file instead of the one beside this script. For the contract suite.

.PARAMETER Quiet
    Suppress the per-metric progress lines. The verdict and every finding still print.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-perf-budget.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-perf-budget.ps1 -Gate -Measured temp/S3368/wear-cold-start.json

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every budgeted metric is inside its budget plus tolerance, or the recorded measurement of
         every budget is still inside the freshness horizon (or reporting only).
      1  under -Gate: a supplied measurement is past its budget plus tolerance - a regression.
      2  cannot verify: the budget file is missing, unparsable, empty or malformed; a named
         measurement artifact is missing or unparsable; a budgeted metric is absent from the
         artifact or carries a different unit; or no -Measured was supplied and a budget's recorded
         measurement is older than staleAfterDays.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string]$Measured,
    [string]$BudgetPath,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $BudgetPath) { $BudgetPath = Join-Path $PSScriptRoot 'perf-budgets.json' }

function Write-Info([string]$Message) {
    if (-not $Quiet) { Write-Host $Message }
}

function Test-HasProperty {
    param([Parameter(Mandatory)][AllowNull()]$Object, [Parameter(Mandatory)][string]$Name)
    if ($null -eq $Object) { return $false }
    return @($Object.PSObject.Properties.Name) -contains $Name
}

function Exit-CannotVerify([string]$Reason) {
    [Console]::Error.WriteLine("assert-perf-budget: cannot verify - $Reason")
    exit 2
}

function Read-JsonFile {
    <#
        Parse one JSON file, or end the run with the "cannot verify" contract. Never returns on a
        parse failure, so every caller can treat the result as a value.
    #>
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$What)

    if (-not (Test-Path -LiteralPath $Path)) { Exit-CannotVerify "$What not found at '$Path'." }
    try { return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json) }
    catch { Exit-CannotVerify "$What at '$Path' is not parsable JSON - $($_.Exception.Message)" }
}

$budgetDoc = Read-JsonFile -Path $BudgetPath -What 'the budget file'
if (-not (Test-HasProperty -Object $budgetDoc -Name 'budgets')) {
    Exit-CannotVerify "the budget file at '$BudgetPath' carries no 'budgets' array."
}

$budgets = @($budgetDoc.budgets)
if ($budgets.Count -eq 0) {
    Exit-CannotVerify "the budget file at '$BudgetPath' declares no budget, so a green verdict would mean 'nothing is watched'."
}

$staleAfterDays = 90
if (Test-HasProperty -Object $budgetDoc -Name 'staleAfterDays') { $staleAfterDays = [int]$budgetDoc.staleAfterDays }

# A malformed budget row is refused rather than skipped: a budget nobody can read is a budget
# nobody is held to, and skipping it silently is the failure mode this whole gate exists against.
foreach ($budget in $budgets) {
    foreach ($field in @('metric', 'unit', 'budget', 'tolerancePercent', 'source')) {
        if (-not (Test-HasProperty -Object $budget -Name $field)) {
            Exit-CannotVerify "a budget row in '$BudgetPath' is missing the '$field' field."
        }
    }
    foreach ($field in @('ticket', 'measuredOn', 'value')) {
        if (-not (Test-HasProperty -Object $budget.source -Name $field)) {
            Exit-CannotVerify "the '$($budget.metric)' budget in '$BudgetPath' has a source block with no '$field' - a budget whose origin cannot be re-judged is a constant, not a budget."
        }
    }
}

$regressions = [System.Collections.Generic.List[string]]::new()
$unverifiable = [System.Collections.Generic.List[string]]::new()

if ($Measured) {
    $measuredDoc = Read-JsonFile -Path $Measured -What 'the measurement artifact'
    if (-not (Test-HasProperty -Object $measuredDoc -Name 'measurements')) {
        Exit-CannotVerify "the measurement artifact at '$Measured' carries no 'measurements' array."
    }
    $records = @($measuredDoc.measurements)

    foreach ($budget in $budgets) {
        $record = @($records | Where-Object { (Test-HasProperty -Object $_ -Name 'metric') -and $_.metric -eq $budget.metric }) | Select-Object -First 1
        if ($null -eq $record) {
            $unverifiable.Add("$($budget.metric) | the measurement artifact '$Measured' carries no record for this metric - run $($budget.producer) and add it.")
            continue
        }
        if (-not (Test-HasProperty -Object $record -Name 'value')) {
            $unverifiable.Add("$($budget.metric) | the measurement record carries no 'value'.")
            continue
        }
        if ((Test-HasProperty -Object $record -Name 'unit') -and $record.unit -ne $budget.unit) {
            $unverifiable.Add("$($budget.metric) | measured in '$($record.unit)' against a budget in '$($budget.unit)' - the two numbers are not comparable.")
            continue
        }

        $value = [double]$record.value
        $ceiling = [double]$budget.budget * (1.0 + ([double]$budget.tolerancePercent / 100.0))
        if ($value -gt $ceiling) {
            $regressions.Add(("{0} | measured {1} {2} against a budget of {3} {2} plus {4}% tolerance (ceiling {5} {2}) - over by {6} {2}." -f `
                        $budget.metric, $value, $budget.unit, $budget.budget, $budget.tolerancePercent,
                    [math]::Round($ceiling, 1), [math]::Round($value - $ceiling, 1)))
        }
        else {
            Write-Info ("  {0}: {1} {2} within the {3} {2} budget plus {4}% (ceiling {5} {2})." -f `
                    $budget.metric, $value, $budget.unit, $budget.budget, $budget.tolerancePercent, [math]::Round($ceiling, 1))
        }
    }
}
else {
    $today = [datetime]::UtcNow.Date
    foreach ($budget in $budgets) {
        $measuredOn = [datetime]::MinValue
        if (-not [datetime]::TryParse([string]$budget.source.measuredOn, [ref]$measuredOn)) {
            $unverifiable.Add("$($budget.metric) | the source block carries an unparsable measuredOn '$($budget.source.measuredOn)'.")
            continue
        }
        $ageDays = [int]($today - $measuredOn.Date).TotalDays
        if ($ageDays -gt $staleAfterDays) {
            $unverifiable.Add(("{0} | no measurement was supplied and the recorded one is {1} days old, past the {2}-day horizon - run {3} and pass the artifact with -Measured." -f `
                        $budget.metric, $ageDays, $staleAfterDays, $budget.producer))
        }
        else {
            Write-Info ("  {0}: no artifact supplied; the recorded {1} {2} measurement of {3} ({4} ticket) is {5} day(s) old, inside the {6}-day horizon." -f `
                    $budget.metric, $budget.source.value, $budget.unit, $budget.source.measuredOn, $budget.source.ticket, $ageDays, $staleAfterDays)
        }
    }
}

if ($regressions.Count -gt 0) {
    Write-Host ("assert-perf-budget: FAIL - {0} budgeted metric(s) regressed past their tolerance:" -f $regressions.Count) -ForegroundColor Red
    foreach ($item in $regressions) { Write-Host ("  {0}" -f $item) -ForegroundColor Red }
    Write-Host "  Either find what gave the number back, or raise the budget in scripts/quality/perf-budgets.json with a new source block naming the ticket and the date that accepted it." -ForegroundColor Red
    if ($Gate) { exit 1 }
    Write-Host 'assert-perf-budget: reporting only - rerun with -Gate to refuse.' -ForegroundColor Yellow
    exit 0
}

if ($unverifiable.Count -gt 0) {
    Write-Host ("assert-perf-budget: CANNOT VERIFY - {0} budgeted metric(s) have no usable measurement:" -f $unverifiable.Count) -ForegroundColor Yellow
    foreach ($item in $unverifiable) { Write-Host ("  {0}" -f $item) -ForegroundColor Yellow }
    Write-Host "  A budget nobody measured proves nothing, so this is not a pass." -ForegroundColor Yellow
    if ($Gate) { exit 2 }
    exit 0
}

Write-Host ("assert-perf-budget: PASS - {0} budgeted metric(s) inside budget." -f $budgets.Count) -ForegroundColor Green
exit 0
