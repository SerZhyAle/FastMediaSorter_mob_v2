#requires -Version 7.0
# Subject: scripts/release/assert-ci-verdict.ps1
<#
.SYNOPSIS
    Contract suite for assert-ci-verdict.ps1: green, red, pending, missing, superseded, accepted.

.DESCRIPTION
    The script reads GitHub through `gh`, so the suite feeds it a fixture of `gh run list --json`
    through -RunsJsonPath. No network and no gh run.

.NOTES
    Exit codes:
      0 - every assertion passed
      1 - an assertion failed
      2 - the script under test is missing
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot '..\assert-ci-verdict.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "assert-ci-verdict tests: script under test missing: $script" -ForegroundColor Yellow
    exit 2
}

$sandbox = Join-Path ([IO.Path]::GetTempPath()) ("assert-ci-verdict-tests-{0}" -f $PID)
New-Item -ItemType Directory -Force -Path $sandbox | Out-Null
$sha = 'a' * 40
$gate = 'Android CI Quality Gate'

$failures = New-Object System.Collections.Generic.List[string]
function Assert-Case([string] $Name, [bool] $Condition, [string] $Detail = '') {
    if ($Condition) { Write-Host "  ok   $Name" } else { Write-Host "  FAIL $Name $Detail" -ForegroundColor Red; $failures.Add($Name) }
}

function New-Run([string] $Name, [string] $Status, [string] $Conclusion, [int] $Attempt = 1, [string] $Created = '2026-10-29T20:47:22Z', [int] $Id = 1) {
    return @{
        databaseId = $Id; workflowName = $Name; status = $Status; conclusion = $Conclusion
        attempt = $Attempt; url = "https://example.invalid/run/$Id"; createdAt = $Created
    }
}

function Invoke-Verdict([object[]] $Runs, [string[]] $Extra = @(), [string] $Ref = $sha) {
    $path = Join-Path $sandbox ('runs-{0}.json' -f [guid]::NewGuid().ToString('N'))
    ConvertTo-Json -InputObject @($Runs) -Depth 6 | Set-Content -LiteralPath $path -Encoding utf8
    $text = (& pwsh -NoProfile -File $script -Sha $Ref -RunsJsonPath $path -RepoRoot $sandbox @Extra 2>&1 | Out-String)
    return @{ Code = $LASTEXITCODE; Text = $text }
}

try {
    $green = Invoke-Verdict @((New-Run $gate 'completed' 'success'), (New-Run 'Static Gates' 'completed' 'success' -Id 2))
    Assert-Case 'all green with the required run exits 0' ($green.Code -eq 0) "exit $($green.Code): $($green.Text)"
    Assert-Case 'green verdict says PASS' ($green.Text -match 'PASS - commit aaaaaaa - 2 workflow run') $green.Text

    $red = Invoke-Verdict @((New-Run $gate 'completed' 'failure' -Id 7), (New-Run 'Static Gates' 'completed' 'success' -Id 2))
    Assert-Case 'a red run exits 1' ($red.Code -eq 1) "exit $($red.Code): $($red.Text)"
    Assert-Case 'the red workflow and its run id are named' ($red.Text -match 'red: Android CI Quality Gate \(run 7\)') $red.Text
    Assert-Case 'the red names the log command' ($red.Text -match 'gh run view 7 --log-failed') $red.Text

    $cancelled = Invoke-Verdict @((New-Run $gate 'completed' 'cancelled'))
    Assert-Case 'a cancelled run is red' ($cancelled.Code -eq 1) "exit $($cancelled.Code)"

    $redJobs = New-Run $gate 'completed' 'failure' -Id 9
    $redJobs.jobs = @(@{ name = 'Verify (app_v2)'; conclusion = 'failure'; steps = @(@{ name = 'Unit tests'; conclusion = 'failure' }, @{ name = 'Checkout'; conclusion = 'success' }) })
    $detail = Invoke-Verdict @($redJobs)
    Assert-Case 'a red shows its failing job and step' ($detail.Text -match 'job:  Verify \(app_v2\)' -and $detail.Text -match 'step: Unit tests' -and $detail.Text -notmatch 'step: Checkout') $detail.Text

    $pending = Invoke-Verdict @((New-Run $gate 'in_progress' ''))
    Assert-Case 'a pending run with no wait exits 3' ($pending.Code -eq 3) "exit $($pending.Code): $($pending.Text)"
    Assert-Case 'pending says it is not a verdict' ($pending.Text -match 'PENDING - not a verdict') $pending.Text

    $redWins = Invoke-Verdict @((New-Run $gate 'in_progress' ''), (New-Run 'Static Gates' 'completed' 'failure' -Id 2))
    Assert-Case 'a red wins over a pending run' ($redWins.Code -eq 1) "exit $($redWins.Code)"

    $notYet = Invoke-Verdict @((New-Run 'Static Gates' 'completed' 'success'))
    Assert-Case 'a required workflow with no run yet is pending, not missing' ($notYet.Code -eq 3) "exit $($notYet.Code): $($notYet.Text)"

    $missing = Invoke-Verdict @((New-Run 'Static Gates' 'completed' 'success')) @('-WaitSeconds', '4', '-PollSeconds', '1', '-AppearSeconds', '1')
    Assert-Case 'a required workflow that never appears gives 2' ($missing.Code -eq 2) "exit $($missing.Code): $($missing.Text)"
    Assert-Case 'the missing workflow is named' ($missing.Text -match 'MISSING\s+Android CI Quality Gate') $missing.Text

    $emptyList = Invoke-Verdict @() @('-AppearSeconds', '0')
    Assert-Case 'no runs at all with a required workflow gives 2' ($emptyList.Code -eq 2) "exit $($emptyList.Code): $($emptyList.Text)"

    $listed = Invoke-Verdict @((New-Run $gate 'completed' 'success'), (New-Run 'Static Gates' 'completed' 'success' -Id 2)) @('-Required', "$gate,Static Gates")
    Assert-Case 'a comma list in -Required is split, as pwsh -File passes it' ($listed.Code -eq 0) "exit $($listed.Code): $($listed.Text)"

    $override = Invoke-Verdict @((New-Run 'Static Gates' 'completed' 'success')) @('-Required', 'Static Gates')
    Assert-Case '-Required replaces the default required set' ($override.Code -eq 0) "exit $($override.Code): $($override.Text)"

    $superseded = Invoke-Verdict @(
        (New-Run $gate 'completed' 'failure' -Id 10 -Created '2026-10-29T20:47:22Z'),
        (New-Run $gate 'completed' 'success' -Id 11 -Created '2026-10-29T21:34:47Z'))
    Assert-Case 'a newer green run supersedes an older red one' ($superseded.Code -eq 0) "exit $($superseded.Code): $($superseded.Text)"

    $retried = Invoke-Verdict @((New-Run $gate 'completed' 'success' -Attempt 2))
    Assert-Case 'a pass on attempt 2 is green' ($retried.Code -eq 0) "exit $($retried.Code)"
    Assert-Case 'a pass on attempt 2 carries the flaky note' ($retried.Text -match 'needed attempt 2') $retried.Text

    $skippedOptional = Invoke-Verdict @((New-Run $gate 'completed' 'success'), (New-Run 'Deploy Pages' 'completed' 'skipped' -Id 3))
    Assert-Case 'a skipped optional workflow is not red' ($skippedOptional.Code -eq 0) "exit $($skippedOptional.Code)"

    $skippedRequired = Invoke-Verdict @((New-Run $gate 'completed' 'skipped'))
    Assert-Case 'a skipped required workflow is red' ($skippedRequired.Code -eq 1) "exit $($skippedRequired.Code)"

    $accepted = Invoke-Verdict @((New-Run $gate 'completed' 'success'), (New-Run 'Maestro E2E Tests' 'completed' 'failure' -Id 4)) @('-Accept', 'Maestro E2E Tests', '-AcceptReason', 'owner: E2E known red')
    Assert-Case 'an accepted red passes' ($accepted.Code -eq 0) "exit $($accepted.Code): $($accepted.Text)"
    Assert-Case 'an accepted red is printed as ACCEPTED with the reason' ($accepted.Text -match 'ACCEPTED\s+Maestro E2E Tests' -and $accepted.Text -match 'owner: E2E known red') $accepted.Text

    $noReason = Invoke-Verdict @((New-Run $gate 'completed' 'failure')) @('-Accept', $gate)
    Assert-Case '-Accept without -AcceptReason gives 2' ($noReason.Code -eq 2) "exit $($noReason.Code): $($noReason.Text)"

    $badRef = Invoke-Verdict @((New-Run $gate 'completed' 'success')) @() 'no-such-ref'
    Assert-Case 'a ref that does not resolve gives 2' ($badRef.Code -eq 2) "exit $($badRef.Code): $($badRef.Text)"

    $noFile = (& pwsh -NoProfile -File $script -Sha $sha -RunsJsonPath (Join-Path $sandbox 'absent.json') -RepoRoot $sandbox 2>&1 | Out-String)
    Assert-Case 'a missing runs file gives 2' ($LASTEXITCODE -eq 2) "exit ${LASTEXITCODE}: $noFile"
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    Write-Host ("assert-ci-verdict tests: FAIL - {0} case(s): {1}" -f $failures.Count, ($failures -join '; ')) -ForegroundColor Red
    exit 1
}
Write-Host 'assert-ci-verdict tests: PASS'
exit 0
