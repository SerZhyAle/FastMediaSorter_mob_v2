<#
.SYNOPSIS
    S1716: the one command - run the overlay accuracy corpus and print the dated report it wrote.

.DESCRIPTION
    Strategic criterion 1 asks for a single command that assembles the scenes, runs them and prints a
    report. This is that command. It does almost nothing itself: the corpus, the geometry run and the
    report writer all live in the test source set, because that is what keeps the bench out of every
    shipped APK, and `CorpusReportTest` is their entry point.

    The run goes through scripts/builders/check-standard-fast.ps1 rather than calling gradlew, so it
    takes temp/BUILD.LOCK like every other gradle-backed job on this machine (CLAUDE.md Rule 23).

    The report lands in temp/ocrbench/<YYYY-MM-DD>/overlay-rectangle-report.md and the test records
    that path in temp/ocrbench/last-report.txt, which is what this script reads back. Both sides
    derive the date, so neither has to be told it.

    A red run is a failed measurement, not a finding: this script exits non-zero and prints the
    runner's own log path so the failure is read rather than guessed at. That is the opposite of the
    ticket's own subject - a metric that could not be computed must never arrive as a number.

.PARAMETER KeepGoing
    Print the previous report's path even when this run failed, instead of exiting on the failure
    alone. Use it to compare a red run against the last green one; it never turns exit 1 into exit 0.

.NOTES
    Exit codes:
      0 - the corpus ran and a report exists at the printed path.
      1 - the run failed, or it completed without leaving a report where one was expected.
      2 - the runner or the report pointer is missing, so nothing was measured and nothing can be
          said about why.
#>
param(
    [switch]$KeepGoing
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $projectRoot "scripts\builders\check-standard-fast.ps1"
$benchRoot = Join-Path $projectRoot "temp\ocrbench"
$pointer = Join-Path $benchRoot "last-report.txt"

if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    Write-Error "run-corpus: no fast-check runner at $runner - cannot run the corpus." -ErrorAction Continue
    exit 2
}

$previous = if (Test-Path -LiteralPath $pointer) { (Get-Content -LiteralPath $pointer -Raw).Trim() } else { $null }

Write-Host "run-corpus: running CorpusReportTest through check-standard-fast.ps1 .." -ForegroundColor Cyan
& $runner -Mode Unit -Tests "*CorpusReportTest*"
$runExit = $LASTEXITCODE

if ($runExit -ne 0) {
    Write-Error "run-corpus: the corpus run failed (runner exit $runExit). Nothing was measured." -ErrorAction Continue
    if ($KeepGoing -and $previous) {
        Write-Host "  previous report (NOT from this run): $previous" -ForegroundColor Yellow
    }
    exit 1
}

if (-not (Test-Path -LiteralPath $pointer -PathType Leaf)) {
    $msg = "run-corpus: the run passed but left no $pointer - the report writer did not run."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$reportPath = (Get-Content -LiteralPath $pointer -Raw).Trim()
if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    Write-Error "run-corpus: $pointer points at $reportPath, which does not exist." -ErrorAction Continue
    exit 1
}

Write-Host ""
Write-Host "run-corpus: report written to" -ForegroundColor Green
Write-Host "  $reportPath"

# Read the summary table by its position, not by a list of axis names: a hardcoded list silently stops
# printing an axis the moment one is added, and the run still exits 0 - so the omission reads as
# "that axis was not measured" (S2036 added two and this echo showed neither).
$reportLines = Get-Content -LiteralPath $reportPath
$summaryStart = ($reportLines | Select-String -SimpleMatch '## Per axis, over measured scenes only' |
    Select-Object -First 1).LineNumber
$axisLines = @()
if ($summaryStart) {
    for ($i = $summaryStart; $i -lt $reportLines.Count; $i++) {
        $line = $reportLines[$i]
        if ($line -match '^\s*$' -and $axisLines.Count -gt 0) { break }
        if ($line -match '^\|' -and $line -notmatch '^\|\s*axis\s*\|' -and $line -notmatch '^\|[-: |]+\|$') {
            $axisLines += $line
        }
    }
}
if ($axisLines) {
    Write-Host ""
    Write-Host "Per-axis summary (worst | median | measured | unmeasured):"
    foreach ($line in $axisLines) { Write-Host "  $line" }
}

# The one number ADR-3 of docs/OCR_OVERLAY_ACCURACY.md 12.1 is read from (S2036).
$spread = $reportLines | Where-Object { $_ -match '^\*\*Height-fraction spread' } | Select-Object -First 1
if ($spread) {
    Write-Host ""
    Write-Host ($spread -replace '\*\*', '')
}

exit 0
