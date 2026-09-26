#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the runner progress watcher: what it prints, and what it hides.

.DESCRIPTION
    The watcher exists so an unattended runner console shows progress; it is worth nothing if it
    prints a sibling instance's work as its own, and worth negative if it floods the console. Both
    are silent failures on a console nobody watches closely, so both are cases here.

    Everything runs against a SYNTHETIC progress directory under a throwaway root, pointed at by
    FMS_AGENT_CHAT_ROOT, and the watcher is always invoked with -Once so no case can hang. The live
    temp/AGENT-CHAT is never read or written.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - a case failed.
      2 - the sandbox could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$watcher = Join-Path $repoRoot 'scripts/utils/watch-agent-progress.ps1'
if (-not (Test-Path -LiteralPath $watcher)) {
    Write-Host "watch-agent-progress.tests: script under test not found - $watcher" -ForegroundColor Red
    exit 2
}

$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("fms-watch-progress-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
$progressDir = Join-Path $sandbox 'progress'
New-Item -ItemType Directory -Path $progressDir -Force | Out-Null

$failures = 0
$caseCount = 0

function New-ProgressRecord {
    param(
        [string] $Kind,
        [string] $Ticket,
        [string] $Instance,
        [string] $Note,
        [string] $Stamp
    )
    $record = [ordered]@{
        schema = 1
        stream = 'progress'
        at     = (Get-Date).ToUniversalTime().ToString('o')
        agent  = [ordered]@{ id = 'test-agent'; name = 'test-agent'; instance = $Instance }
        kind   = $Kind
        ticket = $Ticket
        note   = $Note
    }
    $path = Join-Path $progressDir ("{0}_{1}_test.json" -f $Stamp, $Kind)
    ($record | ConvertTo-Json -Compress -Depth 5) | Set-Content -LiteralPath $path -Encoding utf8NoBOM
}

function Invoke-Watcher {
    param([string[]] $ExtraArgs)
    # -Since 60 because every fixture is written seconds ago: without a window the watcher treats
    # the whole directory as backlog and prints nothing, which is its correct live behaviour.
    $args = @('-NoProfile', '-File', $watcher, '-Once', '-Since', '60') + $ExtraArgs
    $env:FMS_AGENT_CHAT_ROOT = $sandbox
    try { return (& pwsh @args 2>&1 | Out-String) }
    finally { Remove-Item Env:FMS_AGENT_CHAT_ROOT -ErrorAction SilentlyContinue }
}

function Assert-Case {
    param([string] $Name, [bool] $Condition, [string] $Detail = '')
    $script:caseCount++
    if ($Condition) {
        Write-Host ("  PASS  {0}" -f $Name) -ForegroundColor Green
    }
    else {
        $script:failures++
        Write-Host ("  FAIL  {0}" -f $Name) -ForegroundColor Red
        if ($Detail) { Write-Host ("        {0}" -f $Detail) -ForegroundColor DarkGray }
    }
}

try {
    New-ProgressRecord -Kind 'status' -Ticket 'S1111' -Instance 'a' -Note 'Tactical -> In Progress' -Stamp '20260918T000001Z'
    New-ProgressRecord -Kind 'status' -Ticket 'S2222' -Instance 'b' -Note 'Draft -> Approved' -Stamp '20260918T000002Z'
    New-ProgressRecord -Kind 'lock' -Ticket 'S1111' -Instance 'a' -Note 'Code.Phone acquired' -Stamp '20260918T000003Z'
    New-ProgressRecord -Kind 'verdict' -Ticket 'S1111' -Instance 'a' -Note 'post-change PASS' -Stamp '20260918T000004Z'

    Write-Host 'watch-agent-progress: instance scope' -ForegroundColor Cyan
    $outA = Invoke-Watcher -ExtraArgs @('-Instance', 'a')
    Assert-Case 'own instance event is printed' ($outA -match 'S1111') $outA
    Assert-Case "sibling instance's event is not" (-not ($outA -match 'S2222')) $outA

    Write-Host 'watch-agent-progress: kind filter' -ForegroundColor Cyan
    Assert-Case 'lock noise is excluded by default' (-not ($outA -match 'Code\.Phone')) $outA
    Assert-Case 'closure verdict is included' ($outA -match 'post-change PASS') $outA

    Write-Host 'watch-agent-progress: no instance given' -ForegroundColor Cyan
    $outAll = Invoke-Watcher -ExtraArgs @()
    Assert-Case 'every instance is shown when none is named' (($outAll -match 'S1111') -and ($outAll -match 'S2222')) $outAll

    Write-Host 'watch-agent-progress: flood cap' -ForegroundColor Cyan
    for ($i = 10; $i -lt 30; $i++) {
        New-ProgressRecord -Kind 'status' -Ticket ("S30{0}" -f $i) -Instance 'c' -Note 'In Progress -> Implemented' -Stamp ("20260918T0001{0}Z" -f $i)
    }
    $outC = Invoke-Watcher -ExtraArgs @('-Instance', 'c', '-MaxLinesPerPass', '3')
    $printedLines = @($outC -split "`n" | Where-Object { $_ -match '\[C\] \d{2}:\d{2}:\d{2}  (?!>>)' }).Count
    Assert-Case 'the pass is capped' ($printedLines -eq 3) ("printed {0} line(s)" -f $printedLines)
    Assert-Case 'the remainder is counted, not dropped silently' ($outC -match 'and 17 more event') $outC

    Write-Host 'watch-agent-progress: ticket title and build stages' -ForegroundColor Cyan
    $planDir = Join-Path $sandbox 'PLAN'
    New-Item -ItemType Directory -Path $planDir -Force | Out-Null
    '# Spec: S5555 - Scheduled history loads off the main thread' |
        Set-Content -LiteralPath (Join-Path $planDir 'S5555_bugfix-history-off-main.md') -Encoding utf8NoBOM
    New-ProgressRecord -Kind 'note' -Ticket 'S5555' -Instance 'e' -Note 'MONO start' -Stamp '20260918T000301Z'
    New-ProgressRecord -Kind 'lock' -Ticket '' -Instance 'e' -Stamp '20260918T000302Z' `
        -Note 'acquired Build.Phone: check-standard-fast.ps1 -Mode Unit (app_v2 StandardDebug, filtered: com.x.FooViewModelTest,*BarTest) - LONG hold'
    New-ProgressRecord -Kind 'lock' -Ticket '' -Instance 'e' -Note 'released Build.Phone' -Stamp '20260918T000303Z'
    New-ProgressRecord -Kind 'lock' -Ticket '' -Instance 'e' -Note 'acquired Code.Phone: split-detekt-baseline.ps1' -Stamp '20260918T000304Z'
    New-ProgressRecord -Kind 'verdict' -Ticket '' -Instance 'e' -Note 'post-change PASS, 35058 ms (Kotlin)' -Stamp '20260918T000305Z'
    $env:FMS_AGENT_CHAT_ROOT = $sandbox
    try { $outE = (& pwsh -NoProfile -File $watcher -Once -Since 60 -Instance e -RepoRoot $sandbox 2>&1 | Out-String) }
    finally { Remove-Item Env:FMS_AGENT_CHAT_ROOT -ErrorAction SilentlyContinue }
    Assert-Case 'the full title is announced once' (@([regex]::Matches($outE, '>> S5555  Scheduled history loads off the main thread')).Count -eq 1) $outE
    Assert-Case 'a build hold reads as its stage' ($outE -match 'unit tests: FooViewModelTest, BarTest \.\.') $outE
    Assert-Case 'its release carries the duration' ($outE -match 'unit tests: FooViewModelTest, BarTest - done, \d') $outE
    Assert-Case 'a code hold stays out' (-not ($outE -match 'detekt')) $outE
    Assert-Case 'a verdict with no ticket goes to the ticket in hand' ($outE -match 'S5555\s+verdict') $outE

    Write-Host 'watch-agent-progress: malformed record' -ForegroundColor Cyan
    'this is not json' | Set-Content -LiteralPath (Join-Path $progressDir '20260918T000200Z_status_broken.json') -Encoding utf8NoBOM
    New-ProgressRecord -Kind 'status' -Ticket 'S4444' -Instance 'd' -Note 'Approved -> Tactical' -Stamp '20260918T000201Z'
    $outD = Invoke-Watcher -ExtraArgs @('-Instance', 'd')
    Assert-Case 'a broken record does not end the pass' ($outD -match 'S4444') $outD
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
if ($failures -gt 0) {
    Write-Host ("watch-agent-progress.tests: {0} of {1} case(s) FAILED" -f $failures, $caseCount) -ForegroundColor Red
    exit 1
}
Write-Host ("watch-agent-progress.tests: {0} case(s) passed" -f $caseCount) -ForegroundColor Green
exit 0
