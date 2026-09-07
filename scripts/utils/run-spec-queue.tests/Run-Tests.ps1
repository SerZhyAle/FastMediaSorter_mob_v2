#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the queue runner's idle-run series (S2695) - the count of consecutive
    runs that handed a ticket back without moving its status, derived from the run journals.

.DESCRIPTION
    The series decides two visible things: whether automatic ranking passes a ticket over, and
    whether `[idle N, <outcome>]` appears on its row in the release queue. Both are wrong in the
    expensive direction if the walk is wrong - a series that never ends holds a healthy ticket out
    of the queue, and one that ends too early re-runs a ticket that has nothing left to do, which
    is the 18% of runner time this ticket exists to remove.

    Everything runs against SYNTHETIC journals under a throwaway project root. The library is
    invoked at its HARNESS path, never through scripts/utils/run-spec-queue.ps1: that forwarder
    overwrites SZA_PROJECT_ROOT with the repository root, so a sandbox set up around it would be
    silently discarded and the suite would read - and could write - the live temp/spec-queue
    (S2520, S2426).

    Each sandbox is exercised in its own nested pwsh process. Get-SzaProfile and the idle map are
    both cached per process, so a case that changes the threshold cannot share a process with one
    that does not.

.NOTES
    Exit codes:
      0 - every case passed, or the resolved harness predates S2695 and the cases were skipped.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$failures = 0
$skipped = 0
$caseCount = 0

function Assert-Case {
    param([string] $Name, [bool] $Ok, [string] $Detail = '')
    $script:caseCount++
    if ($Ok) {
        Write-Host ("  PASS  {0}" -f $Name) -ForegroundColor DarkGray
    } else {
        Write-Host ("  FAIL  {0}" -f $Name) -ForegroundColor Red
        if ($Detail) { Write-Host ("        {0}" -f $Detail) -ForegroundColor DarkYellow }
        $script:failures++
    }
}

# The probe body. It lives here rather than as a sibling script because it is not independently
# runnable - it means nothing without the sandbox and harness path handed to it.
$probeBody = @'
param([Parameter(Mandatory)][string]$HarnessLib, [Parameter(Mandatory)][string]$Sandbox)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Set BEFORE the dot-source: Get-SzaProjectRoot caches the value for the life of the process.
$env:SZA_PROJECT_ROOT = $Sandbox
. $HarnessLib
$out = [ordered]@{ threshold = (Get-IdleRunPolicy).Threshold }
foreach ($id in @('S9001', 'S9002', 'S9003', 'S9004', 'S9005')) {
    $s = Get-IdleRunSeries -Id $id
    $out[$id] = [ordered]@{ count = $s.Count; last = [string]$s.LastOutcome; held = [bool](Test-IdleRunHeld -Id $id) }
}
$out | ConvertTo-Json -Depth 5 -Compress
'@

function New-Sandbox {
    param([int] $Threshold)
    $sandbox = Join-Path $repoRoot ("temp/S2695/sandbox-{0}" -f $Threshold)
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'temp/spec-queue') -Force | Out-Null

    $profile = Get-Content -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json
    $profile.runner.idleRunThreshold = $Threshold
    $profile | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $sandbox '.sza-profile.json') -Encoding UTF8

    # runs-a: S9001 spans instances with runs-b; S9002's series is broken by a moving run;
    # S9003 ends on an outcome the profile does not call idle; S9004 has one idle run only.
    $runsA = @(
        '{"id":"S9001","moved":false,"outcome":"timeout","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9002","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9002","moved":true,"outcome":"ok","finishedAt":"2026-09-01T12:00:00"}'
        '{"id":"S9003","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9003","moved":false,"outcome":"blocked-by-owner","finishedAt":"2026-09-01T12:00:00"}'
        '{"id":"S9004","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        'this line is not json at all'
    )
    $runsB = @(
        '{"id":"S9001","moved":false,"outcome":"no-progress-or-claim-lost","finishedAt":"2026-09-01T11:00:00"}'
        '{"id":"S9002","moved":false,"outcome":"ok","finishedAt":"2026-09-01T13:00:00"}'
        '{"id":"S9002","moved":false,"outcome":"timeout","finishedAt":"2026-09-01T14:00:00"}'
        '{"id":"S9003","moved":false,"outcome":"ok","finishedAt":"2026-09-01T13:00:00"}'
    )
    Set-Content -LiteralPath (Join-Path $sandbox 'temp/spec-queue/runs-a.jsonl') -Value $runsA -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $sandbox 'temp/spec-queue/runs-b.jsonl') -Value $runsB -Encoding UTF8
    return $sandbox
}

function Invoke-Probe {
    param([string] $HarnessLib, [string] $Sandbox)
    $probePath = Join-Path $Sandbox 'probe.ps1'
    Set-Content -LiteralPath $probePath -Value $probeBody -Encoding UTF8
    $raw = & pwsh -NoProfile -File $probePath -HarnessLib $HarnessLib -Sandbox $Sandbox 2>&1 | Out-String
    try { return ($raw | ConvertFrom-Json) } catch { throw "probe returned no JSON:`n$raw" }
}

Write-Host ''
Write-Host 'run-spec-queue tests - idle-run series (S2695)' -ForegroundColor Cyan

# Resolve the library the way every caller does, and answer honestly when the deployed harness
# predates this ticket. Gated with if/else and never an early return: a return inside this file
# would leave the trailing exit unreached, turning an already-failed run into a silent exit 0.
$harnessLib = $null
try {
    . (Join-Path $repoRoot 'scripts/utils/agent-lock-domains.ps1')
    $harnessLib = Get-SzaHarnessScript 'batch/_idle-runs.ps1'
} catch {
    $harnessLib = $null
}
if (-not $harnessLib -or -not (Test-Path -LiteralPath $harnessLib)) {
    Write-Host '  SKIP S2695 idle-run series (7 cases) - the resolved harness predates the fix.' -ForegroundColor DarkGray
    Write-Host "        Run with SZA_HARNESS_ROOT pointed at the canon checkout to execute them." -ForegroundColor DarkGray
    $skipped = 7
} else {
    $sandboxes = @()
    try {
        $sandbox2 = New-Sandbox -Threshold 2
        $sandboxes += $sandbox2
        $r = Invoke-Probe -HarnessLib $harnessLib -Sandbox $sandbox2

        Assert-Case -Name 'the threshold comes from the profile, not from the script' `
            -Ok ($r.threshold -eq 2) -Detail "expected: 2 | actual: $($r.threshold)"

        Assert-Case -Name 'a series spans instances - two journals, one count' `
            -Ok ($r.S9001.count -eq 2 -and $r.S9001.last -eq 'no-progress-or-claim-lost') `
            -Detail "expected: 2 / no-progress-or-claim-lost | actual: $($r.S9001.count) / $($r.S9001.last)"

        Assert-Case -Name 'a moving run breaks the series - only the runs after it count' `
            -Ok ($r.S9002.count -eq 2 -and $r.S9002.last -eq 'timeout') `
            -Detail "expected: 2 / timeout (the two rows after the 12:00 move) | actual: $($r.S9002.count) / $($r.S9002.last)"

        Assert-Case -Name 'an outcome outside idleOutcomes ends the series rather than being stepped over' `
            -Ok ($r.S9003.count -eq 1 -and $r.S9003.last -eq 'ok') `
            -Detail "expected: 1 / ok (the 13:00 row; the 12:00 blocked-by-owner row stops the walk) | actual: $($r.S9003.count) / $($r.S9003.last)"

        Assert-Case -Name 'a ticket the journals never mention reports zero, not an error' `
            -Ok ($r.S9005.count -eq 0 -and -not $r.S9005.held) `
            -Detail "expected: 0 / not held | actual: $($r.S9005.count) / held=$($r.S9005.held)"

        Assert-Case -Name 'an unparsable journal line is skipped without losing the file' `
            -Ok ($r.S9004.count -eq 1) `
            -Detail "expected: 1 - S9004 sits after the malformed line in runs-a | actual: $($r.S9004.count)"

        # A second process, because the profile and the map are both cached per process.
        $sandbox3 = New-Sandbox -Threshold 3
        $sandboxes += $sandbox3
        $r3 = Invoke-Probe -HarnessLib $harnessLib -Sandbox $sandbox3

        Assert-Case -Name 'raising the threshold in the profile releases a ticket the lower one held' `
            -Ok ($r3.threshold -eq 3 -and $r3.S9001.count -eq 2 -and -not $r3.S9001.held) `
            -Detail "expected: threshold 3, S9001 count 2, not held | actual: $($r3.threshold) / $($r3.S9001.count) / held=$($r3.S9001.held)"
    } finally {
        foreach ($s in $sandboxes) { Remove-Item -LiteralPath $s -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

Write-Host ''
if ($failures -gt 0) {
    Write-Host ("run-spec-queue tests: FAIL ({0} of {1} case(s))" -f $failures, $caseCount) -ForegroundColor Red
    exit 1
}
$skipNote = if ($skipped -gt 0) { ", $skipped skipped" } else { '' }
Write-Host ("run-spec-queue tests: PASS ({0} case(s){1})" -f $caseCount, $skipNote) -ForegroundColor Green
exit 0
