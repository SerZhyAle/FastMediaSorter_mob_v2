#requires -Version 7.0
<#
.SYNOPSIS
    Read GitHub Actions' verdict for the commit a release pushed, and refuse the release on a red or a missing one.

.DESCRIPTION
    Step 12 of /skill-release, after the artifact build and before the first store publication.
    Step 10 pushes `main` and the tag, and since S4488 that push is what starts the full CI: the
    Android build with lint and the unit suite, the emulator E2E, the static gates. run-ci-parity.ps1
    (Step 6b) narrows what CI can find but, by its own header, cannot see Linux / JDK 21 behaviour,
    so CI stays the verdict on the pushed commit. Until this script nothing in the release read that
    verdict, and a red CI was found afterwards by the owner, release after release.

    Every workflow run that exists for the commit is judged. The newest run of each workflow stands,
    so a re-run that went green supersedes the red it re-ran; a run that needed more than one
    attempt is reported all the same, because a pass that took a retry is a flaky test.

    -Required names workflows that MUST have a run. A push that started none would otherwise read
    as "nothing red". A required workflow whose run does not exist yet counts as pending until
    -AppearSeconds of this call have passed, then as a missing verdict.

    A red is final the moment it is seen: the script does not wait for the other runs to finish
    before reporting it. A red that the owner has accepted for this release is passed with -Accept
    plus -AcceptReason quoting his word; it is printed as ACCEPTED, never as PASS.

    One call blocks for at most -WaitSeconds. CI takes longer than a tool call may, so a release
    calls this with -WaitSeconds below the tool timeout and calls it again on exit 3.

.PARAMETER Sha
    The commit CI judged: a full SHA, or any ref the repository resolves (a release passes the tag,
    `release/v<version>`). Defaults to `origin/main`.

.PARAMETER Required
    Workflow names that must have a run for the commit. Defaults to `Android CI Quality Gate`.

.PARAMETER Accept
    Workflow names whose red is accepted for this release. Needs -AcceptReason.

.PARAMETER AcceptReason
    The owner's word for accepting the red, quoted. Printed in the verdict.

.PARAMETER WaitSeconds
    How long this call may wait for pending runs. 0 takes one snapshot.

.PARAMETER PollSeconds
    Pause between snapshots while waiting.

.PARAMETER AppearSeconds
    How long a required workflow may stay without a run before the verdict is "missing".

.PARAMETER RunsJsonPath
    JSON file in the shape of `gh run list --json` replacing the GitHub call. For the contract suite.

.PARAMETER RepoRoot
    Repository root, used to resolve -Sha and as the working directory of `gh`.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-ci-verdict.ps1 -Sha release/v2.61.0291.626 -WaitSeconds 540

.NOTES
    Exit codes:
      0 - every run for the commit is green and every required workflow has a run
      1 - at least one run is red (failure, cancelled, timed out, ..) and not accepted
      2 - could not verify: gh missing or unauthenticated, the commit does not resolve, an unreadable
          run list, -Accept without -AcceptReason, or a required workflow that never got a run
      3 - not a verdict yet: runs are still pending when the wait ended. Call again.
#>
[CmdletBinding()]
param(
    [string] $Sha = 'origin/main',
    [string[]] $Required = @('Android CI Quality Gate'),
    [string[]] $Accept = @(),
    [string] $AcceptReason = '',
    [ValidateRange(0, 3600)] [int] $WaitSeconds = 0,
    [ValidateRange(1, 600)] [int] $PollSeconds = 30,
    [ValidateRange(0, 3600)] [int] $AppearSeconds = 120,
    [string] $RunsJsonPath,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Stop-Unverified([string] $Message) {
    Write-Host "assert-ci-verdict: COULD NOT VERIFY - $Message"
    exit 2
}

if ($Accept.Count -gt 0 -and [string]::IsNullOrWhiteSpace($AcceptReason)) {
    Stop-Unverified '-Accept needs -AcceptReason, the owner''s word for accepting this red'
}

$fullSha = $null
if ($Sha -match '^[0-9a-fA-F]{40}$') {
    $fullSha = $Sha.ToLowerInvariant()
}
else {
    $resolved = & git -C $RepoRoot rev-parse --verify --quiet "$Sha^{commit}" 2>$null
    if ($LASTEXITCODE -eq 0 -and $resolved) { $fullSha = ([string] @($resolved)[0]).Trim() }
}
if (-not $fullSha) { Stop-Unverified "cannot resolve '$Sha' to a commit in $RepoRoot" }

$gh = $null
if (-not $RunsJsonPath) {
    $found = Get-Command gh -ErrorAction SilentlyContinue
    if ($found) { $gh = $found.Source }
    elseif (Test-Path -LiteralPath 'C:\Program Files\GitHub CLI\gh.exe') { $gh = 'C:\Program Files\GitHub CLI\gh.exe' }
    else { Stop-Unverified 'gh (GitHub CLI) is not on PATH and not in its default install folder' }
}

function Get-RunSnapshot {
    if ($RunsJsonPath) {
        if (-not (Test-Path -LiteralPath $RunsJsonPath)) { Stop-Unverified "runs file missing: $RunsJsonPath" }
        $json = Get-Content -LiteralPath $RunsJsonPath -Raw
    }
    else {
        Push-Location $RepoRoot
        try {
            $json = & $gh run list --commit $fullSha --limit 100 --json 'databaseId,workflowName,status,conclusion,attempt,url,createdAt' 2>$null | Out-String
            $code = $LASTEXITCODE
        }
        finally { Pop-Location }
        if ($code -ne 0) { Stop-Unverified "gh run list exited $code - run 'gh auth status' and check network" }
    }
    try { return , @($json | ConvertFrom-Json) }
    catch { Stop-Unverified "unreadable run list: $($_.Exception.Message)" }
}

function Get-Stamp($Run) {
    if ($Run.PSObject.Properties['createdAt'] -and $Run.createdAt) { return [datetime] $Run.createdAt }
    return [datetime]::MinValue
}

function Get-Verdict([object[]] $Runs) {
    $latest = @{}
    foreach ($run in $Runs) {
        $key = [string] $run.workflowName
        if (-not $latest.ContainsKey($key) -or (Get-Stamp $run) -gt (Get-Stamp $latest[$key])) { $latest[$key] = $run }
    }
    $rows = [System.Collections.Generic.List[object]]::new()
    foreach ($name in ($latest.Keys | Sort-Object)) {
        $run = $latest[$name]
        $conclusion = [string] $run.conclusion
        $state = 'FAIL'
        if ([string] $run.status -ne 'completed') { $state = 'WAIT' }
        elseif ($conclusion -eq 'success') { $state = 'PASS' }
        elseif (($Required -notcontains $name) -and (@('neutral', 'skipped') -contains $conclusion)) { $state = 'PASS' }
        if ($state -eq 'FAIL' -and $Accept -contains $name) { $state = 'ACCEPTED' }
        $attempt = 1
        if ($run.PSObject.Properties['attempt'] -and $run.attempt) { $attempt = [int] $run.attempt }
        $rows.Add([pscustomobject] @{ Name = $name; Run = $run; State = $state; Attempt = $attempt })
    }
    $missing = @($Required | Where-Object { -not $latest.ContainsKey($_) })
    return [pscustomobject] @{ Rows = $rows; Missing = $missing }
}

function Write-FailureDetail($Run) {
    $jobs = $null
    if ($Run.PSObject.Properties['jobs']) { $jobs = @($Run.jobs) }
    elseif ($gh) {
        Push-Location $RepoRoot
        try {
            $raw = & $gh run view $Run.databaseId --json jobs 2>$null | Out-String
            if ($LASTEXITCODE -eq 0) { $jobs = @(($raw | ConvertFrom-Json).jobs) }
        }
        finally { Pop-Location }
    }
    if ($null -eq $jobs) {
        Write-Host '      (job details unavailable)'
        return
    }
    foreach ($job in $jobs | Where-Object { @('failure', 'cancelled', 'timed_out') -contains [string] $_.conclusion }) {
        Write-Host "      job:  $($job.name)"
        foreach ($step in @($job.steps) | Where-Object { @('failure', 'cancelled', 'timed_out') -contains [string] $_.conclusion }) {
            Write-Host "      step: $($step.name)"
        }
    }
}

$started = [datetime]::UtcNow
$deadline = $started.AddSeconds($WaitSeconds)
while ($true) {
    $verdict = Get-Verdict (Get-RunSnapshot)
    $elapsed = ([datetime]::UtcNow - $started).TotalSeconds
    $failed = @($verdict.Rows | Where-Object State -eq 'FAIL')
    $waiting = @($verdict.Rows | Where-Object State -eq 'WAIT')
    $notYetVisible = ($verdict.Missing.Count -gt 0) -and ($elapsed -lt $AppearSeconds)
    if ($failed.Count -gt 0) { break }
    if ($waiting.Count -eq 0 -and -not $notYetVisible) { break }
    if ([datetime]::UtcNow -ge $deadline) { break }
    $pendingNames = @($waiting | ForEach-Object { $_.Name }) + @($verdict.Missing)
    Write-Host ("assert-ci-verdict: waiting for {0}" -f ($pendingNames -join ', '))
    Start-Sleep -Seconds $PollSeconds
}

$short = $fullSha.Substring(0, 7)
Write-Host "assert-ci-verdict: commit $short ($Sha)"
foreach ($row in $verdict.Rows) {
    $outcome = if ([string] $row.Run.status -eq 'completed') { [string] $row.Run.conclusion } else { [string] $row.Run.status }
    Write-Host ("  {0,-8} {1}  run {2}  attempt {3}  {4}  {5}" -f $row.State, $row.Name, $row.Run.databaseId, $row.Attempt, $outcome, $row.Run.url)
}
foreach ($name in $verdict.Missing) { Write-Host ("  {0,-8} {1}  no run for this commit" -f 'MISSING', $name) }

foreach ($row in @($verdict.Rows | Where-Object { $_.State -in 'PASS', 'ACCEPTED' -and $_.Attempt -gt 1 })) {
    Write-Host "  note: $($row.Name) needed attempt $($row.Attempt) - a retry hid a red; report the flaky test"
}
foreach ($row in @($verdict.Rows | Where-Object State -eq 'ACCEPTED')) {
    Write-Host "  accepted: $($row.Name) - $AcceptReason"
}

if ($failed.Count -gt 0) {
    foreach ($row in $failed) {
        Write-Host "  red: $($row.Name) (run $($row.Run.databaseId))"
        Write-FailureDetail $row.Run
        Write-Host "      read it: gh run view $($row.Run.databaseId) --log-failed"
    }
    Write-Host ("assert-ci-verdict: FAIL - {0} red run(s): {1}" -f $failed.Count, (($failed | ForEach-Object { $_.Name }) -join ', '))
    exit 1
}
if ($waiting.Count -gt 0 -or $notYetVisible) {
    $pendingNames = @($waiting | ForEach-Object { $_.Name }) + @($verdict.Missing)
    Write-Host ("assert-ci-verdict: PENDING - not a verdict, call again: {0}" -f ($pendingNames -join ', '))
    exit 3
}
if ($verdict.Missing.Count -gt 0) {
    Stop-Unverified ("no run for the required workflow(s) after {0}s: {1} - the push may not have started CI" -f $AppearSeconds, ($verdict.Missing -join ', '))
}
Write-Host ("assert-ci-verdict: PASS - commit {0} - {1} workflow run(s) green" -f $short, $verdict.Rows.Count)
exit 0
