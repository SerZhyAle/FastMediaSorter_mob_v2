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
$script:cases = 0
function Assert-Case([string] $Name, [bool] $Condition, [string] $Detail = '') {
    $script:cases++
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

    $dispatched = @('Static Gates', 'Maestro E2E Tests', 'Security scan')

    $noBranch = Invoke-Verdict @((New-Run $gate 'completed' 'success')) @('-Dispatch')
    Assert-Case '-Dispatch without -Branch gives 2' ($noBranch.Code -eq 2 -and $noBranch.Text -match 'needs -Branch') "exit $($noBranch.Code): $($noBranch.Text)"

    $fresh = Invoke-Verdict @((New-Run $gate 'completed' 'success')) @('-Dispatch', '-Branch', 'DEBUG-v041')
    $freshLines = @($dispatched | Where-Object { $fresh.Text -match ('dispatch \(fixture run, nothing sent\): {0} on DEBUG-v041' -f [regex]::Escape($_)) })
    Assert-Case 'every workflow without a run is dispatched on the branch' ($freshLines.Count -eq 3) $fresh.Text
    Assert-Case 'a workflow that already has a run is not dispatched again' ($fresh.Text -match 'Android CI Quality Gate already has a run' -and $fresh.Text -notmatch 'nothing sent\): Android CI Quality Gate') $fresh.Text
    Assert-Case 'right after the dispatch the new runs are pending, not missing' ($fresh.Code -eq 3) "exit $($fresh.Code): $($fresh.Text)"

    $allRuns = @((New-Run $gate 'completed' 'success'), (New-Run 'Static Gates' 'completed' 'success' -Id 2),
        (New-Run 'Maestro E2E Tests' 'completed' 'success' -Id 3), (New-Run 'Security scan' 'completed' 'success' -Id 4))
    $already = Invoke-Verdict $allRuns @('-Dispatch', '-Branch', 'DEBUG-v041')
    Assert-Case 'a commit that has every run dispatches nothing' ($already.Text -notmatch 'nothing sent' -and ([regex]::Matches($already.Text, 'already has a run')).Count -eq 4) $already.Text
    Assert-Case 'a commit that has every run is judged as before' ($already.Code -eq 0 -and $already.Text -match 'PASS - commit aaaaaaa - 4 workflow run') "exit $($already.Code): $($already.Text)"

    $neverShows = Invoke-Verdict @((New-Run $gate 'completed' 'success')) @('-Dispatch', '-Branch', 'DEBUG-v041', '-WaitSeconds', '4', '-PollSeconds', '1', '-AppearSeconds', '1')
    Assert-Case 'a dispatched workflow that never shows a run gives 2' ($neverShows.Code -eq 2) "exit $($neverShows.Code): $($neverShows.Text)"
    Assert-Case 'the dispatched workflow without a run is named MISSING' ($neverShows.Text -match 'MISSING\s+Static Gates') $neverShows.Text

    $withoutStatic = @($allRuns | Where-Object { $_.workflowName -ne 'Static Gates' })
    $joinsRequired = Invoke-Verdict $withoutStatic @('-Dispatch', '-Branch', 'DEBUG-v041', '-Required', 'Android CI Quality Gate')
    Assert-Case 'a dispatched workflow joins an overridden -Required set, so its missing run is pending' ($joinsRequired.Code -eq 3 -and $joinsRequired.Text -match 'PENDING.*Static Gates') "exit $($joinsRequired.Code): $($joinsRequired.Text)"

    $listedDispatch = Invoke-Verdict @((New-Run $gate 'completed' 'success')) @('-Dispatch', '-Branch', 'DEBUG-v041', '-DispatchWorkflows', 'Static Gates,Security scan')
    Assert-Case 'a comma list in -DispatchWorkflows is split and limits the dispatch' ($listedDispatch.Text -match 'nothing sent\): Static Gates' -and $listedDispatch.Text -match 'nothing sent\): Security scan' -and $listedDispatch.Text -notmatch 'nothing sent\): Maestro') $listedDispatch.Text
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    Write-Host ("assert-ci-verdict tests: FAIL (run={0} skipped=0, failed={1}): {2}" -f $script:cases, $failures.Count, ($failures -join '; ')) -ForegroundColor Red
    exit 1
}
Write-Host ("assert-ci-verdict tests: PASS (run={0} skipped=0)" -f $script:cases)
exit 0
