#requires -Version 7.0
<#
.SYNOPSIS
    CI triage: classify every failing test of a JUnit report against the flaky quarantine ledger.
    A quarantined failure is a named warning; a failure nobody quarantined stays a hard failure.

.DESCRIPTION
    S3371 phase 05, the CI half of the flaky policy. The other half is the refusal:
    scripts/quality/assert-no-test-retry.ps1 forbids every mechanism that re-runs a failed test
    until it passes. Forbidding retry only works if the alternative exists and is visible, and this
    script is what makes it visible - a quarantined test keeps appearing in every run's output, with
    its symptom and its ticket, until somebody fixes it. That is the whole difference from a retry:
    the signal survives.

    THE LEDGER: docs/test-flaky-quarantine.jsonl, one JSON object per line, four fields, all
    required:

      { "test": "com.sza.fastmediasorter.foo.BarTest.doesTheThing",
        "symptom": "fails about one run in six on a loaded runner, always in the same assertion",
        "ticket": "S3371",
        "entered": "2026-09-22" }

      test     fully qualified class plus method, exactly as the JUnit report spells it
               (classname + "." + name), because that is the only form the comparison can use.
      symptom  what the flake looks like, in one sentence a reader can match against a new failure.
      ticket   a live Sxxxx. A quarantine with no ticket is a deletion with extra steps.
      entered  the date the row was written, so an old quarantine is visibly old.

    An empty ledger is the correct state of a healthy repository, and it is how this file ships.

    WHAT THIS SCRIPT DOES NOT DO. It does not rescue the job. The CI verify job runs lint, the unit
    suite and the assembly in ONE Gradle invocation, so that step's exit code is the job's verdict,
    and making this script the verdict instead would also swallow a lint or assembly failure. The
    script therefore classifies and reports: every quarantined failure is printed as a GitHub
    warning that names the test, the ticket and the symptom, and every unledgered failure is printed
    as an error and fails this step. Turning a quarantined-only run green at the JOB level needs the
    verify job restructured so the Gradle step is no longer the verdict, which is a separate change
    with its own trade-offs.

.PARAMETER ResultsDir
    Directory holding the JUnit report XML, searched recursively. Defaults to the app_v2 unit-test
    results directory.

.PARAMETER Ledger
    The quarantine ledger. Defaults to docs/test-flaky-quarantine.jsonl.

.PARAMETER RepoRoot
    Root that the two defaults resolve against. Defaults to the parent of scripts/ci.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/ci/triage-junit-flaky.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/ci/triage-junit-flaky.ps1 -ResultsDir app_v2/build/test-results/testStandardDebugUnitTest

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  no failing test, or every failing test carries a quarantine row.
      1  at least one failing test has no row in the ledger, or a ledger row is malformed.
      2  cannot verify: the results directory does not exist or holds no JUnit XML, or the ledger
         cannot be read. "Found no report" must never read as "found no failure".
#>
[CmdletBinding()]
param(
    [string]$ResultsDir,
    [string]$Ledger,
    [string]$RepoRoot,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
if (-not $ResultsDir) { $ResultsDir = Join-Path $RepoRoot 'app_v2/build/test-results' }
elseif (-not [System.IO.Path]::IsPathRooted($ResultsDir)) { $ResultsDir = Join-Path $RepoRoot $ResultsDir }
if (-not $Ledger) { $Ledger = Join-Path $RepoRoot 'docs/test-flaky-quarantine.jsonl' }
elseif (-not [System.IO.Path]::IsPathRooted($Ledger)) { $Ledger = Join-Path $RepoRoot $Ledger }

$script:TicketPattern = [regex]'^S\d{4}$'

function Get-QuarantineRow {
    <#
        One record per ledger line, malformed lines included - a row that fails to parse must be
        visible, not absent. Returns an empty list for an empty ledger; the caller wraps the call
        in @( ) because an empty List[T] unrolls to $null on return and $null.Count throws under
        StrictMode.
    #>
    param([Parameter(Mandatory)][string]$Path)

    $rows = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return $rows }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed) { continue }

        $parsed = $null
        try { $parsed = $trimmed | ConvertFrom-Json }
        catch {
            $rows.Add([pscustomobject]@{ Line = $lineNumber; Test = ''; Ticket = ''; Symptom = ''; Entered = ''; Problem = "not parsable JSON - $($_.Exception.Message)" })
            continue
        }

        $names = @($parsed.PSObject.Properties.Name)
        $missing = @(@('test', 'symptom', 'ticket', 'entered') | Where-Object { $names -notcontains $_ })
        if ($missing.Count -gt 0) {
            $rows.Add([pscustomobject]@{ Line = $lineNumber; Test = ''; Ticket = ''; Symptom = ''; Entered = ''; Problem = "missing field(s): $($missing -join ', ')" })
            continue
        }

        $problem = ''
        if (-not $script:TicketPattern.IsMatch([string]$parsed.ticket)) {
            $problem = "the ticket '$($parsed.ticket)' is not an Sxxxx id - a quarantine with no ticket is a deletion with extra steps"
        }

        $rows.Add([pscustomobject]@{
                Line    = $lineNumber
                Test    = [string]$parsed.test
                Ticket  = [string]$parsed.ticket
                Symptom = [string]$parsed.symptom
                Entered = [string]$parsed.entered
                Problem = $problem
            })
    }

    return $rows
}

function Get-FailingTest {
    <#
        One record per failing or erroring <testcase> across every JUnit XML under the results
        directory. Same empty-list contract as Get-QuarantineRow.
    #>
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Files)

    $failures = [System.Collections.Generic.List[object]]::new()

    foreach ($file in $Files) {
        $document = New-Object System.Xml.XmlDocument
        try { $document.Load($file.FullName) }
        catch {
            # A truncated or unparsable report is the one case where reading on would be worse than
            # stopping: the tests it holds are simply invisible, so a clean verdict from the rest of
            # the files would be a verdict about a suite nobody read.
            $failures.Add([pscustomobject]@{ Id = ''; Report = $file.Name; Unreadable = "$($_.Exception.Message)" })
            continue
        }
        foreach ($case in $document.SelectNodes('//testcase')) {
            $failed = @($case.SelectNodes('failure')).Count -gt 0 -or @($case.SelectNodes('error')).Count -gt 0
            if (-not $failed) { continue }

            $className = $case.GetAttribute('classname')
            $name = $case.GetAttribute('name')
            $id = if ($className) { "$className.$name" } else { $name }
            $failures.Add([pscustomobject]@{ Id = $id; Report = $file.Name; Unreadable = '' })
        }
    }

    return $failures
}

if (-not (Test-Path -LiteralPath $ResultsDir)) {
    [Console]::Error.WriteLine("triage-junit-flaky: cannot verify - the results directory '$ResultsDir' does not exist, so no test report was read.")
    exit 2
}

$reportFiles = @(Get-ChildItem -LiteralPath $ResultsDir -Recurse -Filter '*.xml' -File)
if ($reportFiles.Count -eq 0) {
    [Console]::Error.WriteLine("triage-junit-flaky: cannot verify - no JUnit XML under '$ResultsDir'. A run that produced no report proves nothing about the suite.")
    exit 2
}

try { $ledgerRows = @(Get-QuarantineRow -Path $Ledger) }
catch {
    [Console]::Error.WriteLine("triage-junit-flaky: cannot verify - the quarantine ledger '$Ledger' could not be read: $($_.Exception.Message)")
    exit 2
}

$malformed = @($ledgerRows | Where-Object { $_.Problem })
$quarantined = @{}
foreach ($row in ($ledgerRows | Where-Object { -not $_.Problem })) { $quarantined[$row.Test] = $row }

$scanned = @(Get-FailingTest -Files $reportFiles)
$unreadable = @($scanned | Where-Object { $_.Unreadable })
if ($unreadable.Count -gt 0) {
    [Console]::Error.WriteLine("triage-junit-flaky: cannot verify - $($unreadable.Count) report file(s) under '$ResultsDir' could not be parsed, so the tests they hold were never read:")
    foreach ($item in $unreadable) { [Console]::Error.WriteLine("  $($item.Report): $($item.Unreadable)") }
    exit 2
}

$failures = @($scanned)
$ledgered = @($failures | Where-Object { $quarantined.ContainsKey($_.Id) })
$unledgered = @($failures | Where-Object { -not $quarantined.ContainsKey($_.Id) })

Write-Host ("triage-junit-flaky: {0} report file(s), {1} failing test(s), {2} quarantine row(s)." -f `
        $reportFiles.Count, $failures.Count, $ledgerRows.Count)

foreach ($failure in $ledgered) {
    $row = $quarantined[$failure.Id]
    Write-Host ("::warning title=Quarantined flaky test::{0} failed and is quarantined under {1} since {2}: {3}" -f `
            $failure.Id, $row.Ticket, $row.Entered, $row.Symptom)
}

# A catalog lookup is the cheapest liveness check available in CI, and it is only a warning: an id
# the catalog does not carry may be archived rather than invented, and that is a judgement call the
# person reading the run makes, not one this script may fail a build over.
$catalogPath = Join-Path $RepoRoot 'PLAN/spec-catalog.jsonl'
if (Test-Path -LiteralPath $catalogPath) {
    $catalogText = Get-Content -LiteralPath $catalogPath -Raw
    foreach ($row in ($ledgerRows | Where-Object { -not $_.Problem })) {
        if ($catalogText -notmatch ('"' + [regex]::Escape($row.Ticket) + '"')) {
            Write-Host ("::warning title=Stale quarantine row::{0} is quarantined under {1}, which the spec catalog does not carry - re-open the ticket or drop the row." -f $row.Test, $row.Ticket)
        }
    }
}

$quarantinedOnly = ($failures.Count -gt 0 -and $unledgered.Count -eq 0 -and $malformed.Count -eq 0)
if ($env:GITHUB_OUTPUT) {
    Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value ("quarantined_only={0}" -f $quarantinedOnly.ToString().ToLowerInvariant())
}

if ($malformed.Count -gt 0) {
    Write-Host ("triage-junit-flaky: FAIL - {0} malformed quarantine row(s) in {1}:" -f $malformed.Count, $Ledger) -ForegroundColor Red
    foreach ($row in $malformed) {
        Write-Host ("::error title=Malformed quarantine row::{0}:{1} {2}" -f (Split-Path -Leaf $Ledger), $row.Line, $row.Problem)
    }
}

if ($unledgered.Count -gt 0) {
    Write-Host ("triage-junit-flaky: FAIL - {0} failing test(s) are not quarantined:" -f $unledgered.Count) -ForegroundColor Red
    foreach ($failure in $unledgered) {
        Write-Host ("::error title=Test failure::{0} failed in {1} and has no row in docs/test-flaky-quarantine.jsonl" -f $failure.Id, $failure.Report)
    }
    Write-Host "  Fix the test. If it is genuinely intermittent, quarantine it with a ticket - never retry it to green (scripts/quality/assert-no-test-retry.ps1)." -ForegroundColor Red
}

if ($malformed.Count -gt 0 -or $unledgered.Count -gt 0) { exit 1 }

if ($quarantinedOnly) {
    Write-Host ("triage-junit-flaky: PASS with {0} quarantined failure(s) - every failing test is on the ledger with a ticket." -f $ledgered.Count) -ForegroundColor Yellow
    exit 0
}

Write-Host "triage-junit-flaky: PASS - no failing test in the report." -ForegroundColor Green
exit 0
