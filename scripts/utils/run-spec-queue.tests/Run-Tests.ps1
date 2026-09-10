#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the queue runner: the idle-run series (S2695) and the per-instance child
    invocation - command, argument template and their fallbacks (S2698).

.DESCRIPTION
    The idle-run series decides two visible things: whether automatic ranking passes a ticket over,
    and whether `[idle N, <outcome>]` appears on its row in the release queue. Both are wrong in the
    expensive direction if the walk is wrong - a series that never ends holds a healthy ticket out
    of the queue, and one that ends too early re-runs a ticket that has nothing left to do, which
    is the 18% of runner time this ticket exists to remove.

    The per-instance invocation decides what each instance LAUNCHES. Its cases are guarded against
    one failure above all others: a default that drifts from the call the runner made before the
    template existed changes every queue run at once, on every instance, silently. So the expected
    vectors here are written out element by element rather than derived from the profile.

    Everything runs against SYNTHETIC journals and a SANDBOX profile under a throwaway project root.
    The code under test is invoked at its HARNESS path, never through
    scripts/utils/run-spec-queue.ps1: that forwarder overwrites SZA_PROJECT_ROOT with the repository
    root, so a sandbox set up around it would be silently discarded and the suite would read - and
    could write - the live temp/spec-queue (S2520, S2426).

    Each sandbox is exercised in its own nested pwsh process. Get-SzaProfile and the idle map are
    both cached per process, so a case that changes the threshold cannot share a process with one
    that does not. The S2698 functions are lifted out of the runner by parsing it and evaluating the
    two function definitions: the runner is a top-to-bottom script that ranks and launches tickets,
    so dot-sourcing it to reach a function would run the queue.

.NOTES
    Exit codes:
      0 - every case passed, or the resolved harness predates a fix and its cases were skipped.
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
foreach ($id in @('S9001', 'S9002', 'S9003', 'S9004', 'S9005', 'S9006')) {
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
    # S9003 ends on an outcome the profile does not call idle; S9004 has one idle run only;
    # S9006 is a genuine idle run followed by a runner-side failure (S2871).
    $runsA = @(
        '{"id":"S9001","moved":false,"outcome":"timeout","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9002","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9002","moved":true,"outcome":"ok","finishedAt":"2026-09-01T12:00:00"}'
        '{"id":"S9003","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9003","moved":false,"outcome":"blocked-by-owner","finishedAt":"2026-09-01T12:00:00"}'
        '{"id":"S9004","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        '{"id":"S9006","moved":false,"outcome":"ok","finishedAt":"2026-09-01T10:00:00"}'
        'this line is not json at all'
    )
    $runsB = @(
        '{"id":"S9001","moved":false,"outcome":"ok","finishedAt":"2026-09-01T11:00:00"}'
        '{"id":"S9002","moved":false,"outcome":"ok","finishedAt":"2026-09-01T13:00:00"}'
        '{"id":"S9002","moved":false,"outcome":"timeout","finishedAt":"2026-09-01T14:00:00"}'
        '{"id":"S9003","moved":false,"outcome":"ok","finishedAt":"2026-09-01T13:00:00"}'
        '{"id":"S9006","moved":false,"outcome":"no-progress-or-claim-lost","finishedAt":"2026-09-01T11:00:00"}'
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
    Write-Host '  SKIP S2695 idle-run series (8 cases) - the resolved harness predates the fix.' -ForegroundColor DarkGray
    Write-Host "        Run with SZA_HARNESS_ROOT pointed at the canon checkout to execute them." -ForegroundColor DarkGray
    $skipped = 8
} else {
    $sandboxes = @()
    try {
        $sandbox2 = New-Sandbox -Threshold 2
        $sandboxes += $sandbox2
        $r = Invoke-Probe -HarnessLib $harnessLib -Sandbox $sandbox2

        Assert-Case -Name 'the threshold comes from the profile, not from the script' `
            -Ok ($r.threshold -eq 2) -Detail "expected: 2 | actual: $($r.threshold)"

        Assert-Case -Name 'a series spans instances - two journals, one count' `
            -Ok ($r.S9001.count -eq 2 -and $r.S9001.last -eq 'ok') `
            -Detail "expected: 2 / ok | actual: $($r.S9001.count) / $($r.S9001.last)"

        # S2871: the outcomes naming a runner-side failure were dropped from runner.idleOutcomes,
        # so such a run now ENDS the series rather than extending it - the ticket returns to
        # automatic ranking instead of being held on evidence about the runner, not about itself.
        # Measured 2026-09-08: nine tickets were held by one six-minute window of children that
        # exited 1 in zero minutes.
        Assert-Case -Name 'a runner-side failure releases the ticket rather than holding it' `
            -Ok ($r.S9006.count -eq 0 -and -not $r.S9006.held) `
            -Detail "expected: 0 / not held - the 11:00 no-progress-or-claim-lost row ends the walk | actual: $($r.S9006.count) / held=$($r.S9006.held)"

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
Write-Host 'run-spec-queue tests - per-instance child invocation (S2698)' -ForegroundColor Cyan

# The two functions are lifted out of the runner rather than reached by dot-sourcing it: the runner
# ranks and launches tickets from its top level, so dot-sourcing would run the live queue.
$instanceProbeBody = @'
param(
    [Parameter(Mandatory)][string]$Runner,
    [Parameter(Mandatory)][string]$Sandbox,
    [Parameter(Mandatory)][string]$ProfileLib
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Set BEFORE the dot-source: Get-SzaProjectRoot caches the value for the life of the process.
$env:SZA_PROJECT_ROOT = $Sandbox
. $ProfileLib
$ast = [System.Management.Automation.Language.Parser]::ParseFile($Runner, [ref]$null, [ref]$null)
foreach ($name in @('Get-InstanceSetting', 'Expand-ChildArgs')) {
    $fn = $ast.FindAll({
            param($n)
            $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name
        }, $true)
    if ($fn.Count -eq 0) { throw "function $name not found in $Runner" }
    . ([scriptblock]::Create($fn[0].Extent.Text))
}
$out = [ordered]@{}
foreach ($i in @('a', 'b', 'zz')) {
    $Instance = $i
    $template = @(Get-InstanceSetting -Field 'argsTemplate')
    $out[$i] = [ordered]@{
        command       = [string](Get-InstanceSetting -Field 'command')
        headlessMatch = [string](Get-InstanceSetting -Field 'headlessMatch')
        withModel     = @(Expand-ChildArgs -Template $template -Prompt '/spec-all S9001' -Mode 'bypassPermissions' -Model 'opus')
        noModel       = @(Expand-ChildArgs -Template $template -Prompt '/spec-all S9001' -Mode 'bypassPermissions' -Model '')
    }
}
$out | ConvertTo-Json -Depth 6 -Compress
'@

function New-InstanceSandbox {
    $sandbox = Join-Path $repoRoot 'temp/S2698/sandbox'
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'temp') -Force | Out-Null

    $profile = Get-Content -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json
    # Instance a overrides all three fields, b overrides nothing, and zz is absent from the map -
    # the three answers the resolution chain has to give.
    $profile.runner.instances = [ordered]@{
        a = [ordered]@{
            command       = 'pwsh'
            argsTemplate  = @('run', '--task', '{prompt}', '--perm', '{permissionMode}', '--agent', '{model}')
            headlessMatch = '\s--task\s'
        }
        b = [ordered]@{}
    }
    $profile | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $sandbox '.sza-profile.json') -Encoding UTF8
    return $sandbox
}

$runnerScript = $null
try {
    $runnerScript = Get-SzaHarnessScript 'batch/run-spec-queue.ps1'
} catch {
    $runnerScript = $null
}
$hasInstanceMechanism = $runnerScript -and (Test-Path -LiteralPath $runnerScript) -and
    (Select-String -LiteralPath $runnerScript -Pattern 'function Get-InstanceSetting' -Quiet)
if (-not $hasInstanceMechanism) {
    Write-Host '  SKIP S2698 per-instance invocation (7 cases) - the resolved harness predates the fix.' -ForegroundColor DarkGray
    Write-Host '        Run with SZA_HARNESS_ROOT pointed at the canon checkout to execute them.' -ForegroundColor DarkGray
    $skipped += 7
} else {
    $instanceSandbox = $null
    try {
        $instanceSandbox = New-InstanceSandbox
        $probePath = Join-Path $instanceSandbox 'instance-probe.ps1'
        Set-Content -LiteralPath $probePath -Value $instanceProbeBody -Encoding UTF8
        $profileLib = Get-SzaHarnessScript '_profile.ps1'
        $rawI = & pwsh -NoProfile -File $probePath -Runner $runnerScript -Sandbox $instanceSandbox -ProfileLib $profileLib 2>&1 | Out-String
        try { $ri = $rawI | ConvertFrom-Json } catch { throw "instance probe returned no JSON:`n$rawI" }

        # Written out rather than derived: a default that drifts from this vector changes every queue
        # run on every instance at once, which is the failure the whole template exists not to cause.
        $today = @('-p', '/spec-all S9001', '--permission-mode', 'bypassPermissions', '--model', 'opus')
        $todayNoModel = @('-p', '/spec-all S9001', '--permission-mode', 'bypassPermissions')

        Assert-Case -Name 'an instance with an empty record builds today vector, model chosen' `
            -Ok ((@($ri.b.withModel) -join '|') -eq ($today -join '|')) `
            -Detail "expected: $($today -join ' ') | actual: $(@($ri.b.withModel) -join ' ')"

        Assert-Case -Name 'an empty model drops its flag rather than passing an empty argument' `
            -Ok ((@($ri.b.noModel) -join '|') -eq ($todayNoModel -join '|')) `
            -Detail "expected: $($todayNoModel -join ' ') | actual: $(@($ri.b.noModel) -join ' ')"

        Assert-Case -Name 'an instance absent from the map takes the shared values whole' `
            -Ok ($ri.zz.command -eq 'claude' -and (@($ri.zz.withModel) -join '|') -eq ($today -join '|')) `
            -Detail "expected: claude / $($today -join ' ') | actual: $($ri.zz.command) / $(@($ri.zz.withModel) -join ' ')"

        Assert-Case -Name 'a record command changes that instance only' `
            -Ok ($ri.a.command -eq 'pwsh' -and $ri.b.command -eq 'claude') `
            -Detail "expected: a=pwsh, b=claude | actual: a=$($ri.a.command), b=$($ri.b.command)"

        $custom = @('run', '--task', '/spec-all S9001', '--perm', 'bypassPermissions', '--agent', 'opus')
        Assert-Case -Name 'a record argsTemplate fills all three substitutions' `
            -Ok ((@($ri.a.withModel) -join '|') -eq ($custom -join '|')) `
            -Detail "expected: $($custom -join ' ') | actual: $(@($ri.a.withModel) -join ' ')"

        Assert-Case -Name 'a record headlessMatch changes that instance only' `
            -Ok ($ri.a.headlessMatch -eq '\s--task\s' -and $ri.b.headlessMatch -eq '\s-p\s') `
            -Detail "expected: a=\s--task\s, b=\s-p\s | actual: a=$($ri.a.headlessMatch), b=$($ri.b.headlessMatch)"

        # A command this machine does not have. The watchdog restarts a fallen instance on a timer,
        # so a silent failure here costs that instance's whole share of the queue's throughput with
        # nothing on screen naming which of the three stopped working.
        $missingCommand = 's2698-no-such-command'
        $badProfilePath = Join-Path $instanceSandbox '.sza-profile.json'
        $badProfile = Get-Content -LiteralPath $badProfilePath -Raw | ConvertFrom-Json
        $badProfile.runner.instances | Add-Member -NotePropertyName 'x' `
            -NotePropertyValue ([pscustomobject]@{ command = $missingCommand }) -Force
        $badProfile | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $badProfilePath -Encoding UTF8

        $refusal = & pwsh -NoProfile -Command `
            "`$env:SZA_PROJECT_ROOT = '$instanceSandbox'; & '$runnerScript' -RepoRoot '$instanceSandbox' -Instance x -DryRun 2>&1 | Out-String; exit `$LASTEXITCODE"
        $refusalCode = $LASTEXITCODE
        $refusalText = ($refusal | Out-String)

        Assert-Case -Name 'an instance whose command is missing refuses with exit 2, naming both' `
            -Ok ($refusalCode -eq 2 -and $refusalText -match "instance 'x'" -and $refusalText -match [regex]::Escape($missingCommand)) `
            -Detail "expected: exit 2, text naming instance x and $missingCommand | actual: exit $refusalCode | $($refusalText.Trim())"
    } finally {
        if ($instanceSandbox) { Remove-Item -LiteralPath $instanceSandbox -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

Write-Host ''
Write-Host 'run-spec-queue tests - what a finished child was (S2873)' -ForegroundColor Cyan

# Resolve-RunOutcome is pure - it reads no profile, no journal and no catalog - so unlike the two
# blocks above it needs neither a sandbox nor a nested process, and the function is lifted straight
# into this one. The runner itself still must not be dot-sourced: it ranks and launches tickets from
# its top level.
$outcomeRunner = $null
try {
    . (Join-Path $repoRoot 'scripts/utils/agent-lock-domains.ps1')
    $outcomeRunner = Get-SzaHarnessScript 'batch/run-spec-queue.ps1'
} catch {
    $outcomeRunner = $null
}
$hasOutcomeResolver = $outcomeRunner -and (Test-Path -LiteralPath $outcomeRunner) -and
    (Select-String -LiteralPath $outcomeRunner -Pattern 'function Resolve-RunOutcome' -Quiet)
if (-not $hasOutcomeResolver) {
    Write-Host '  SKIP S2873 outcome classification (8 cases) - the resolved harness predates the fix.' -ForegroundColor DarkGray
    Write-Host '        Run with SZA_HARNESS_ROOT pointed at the canon checkout to execute them.' -ForegroundColor DarkGray
    $skipped += 8
} else {
    $outcomeAst = [System.Management.Automation.Language.Parser]::ParseFile($outcomeRunner, [ref]$null, [ref]$null)
    $outcomeFn = $outcomeAst.FindAll({
            param($n)
            $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Resolve-RunOutcome'
        }, $true)
    if ($outcomeFn.Count -eq 0) { throw "function Resolve-RunOutcome not found in $outcomeRunner" }
    . ([scriptblock]::Create($outcomeFn[0].Extent.Text))

    # The row this ticket was written for: a child that started and exited 1 in zero minutes. Before
    # S2873 it was journalled as an idle ticket, which is a claim about the TICKET made from evidence
    # about the RUNNER.
    $failedFast = Resolve-RunOutcome -Outcome 'ok' -ExitCode 1 -StatusBefore 'Partial' -StatusAfter 'Partial' -ElapsedSeconds 3
    Assert-Case -Name 'a child that exited non-zero is named a failure, not an idle run' `
        -Ok ($failedFast.Outcome -eq 'child-failed' -and -not $failedFast.Moved) `
        -Detail "expected: child-failed / moved false | actual: $($failedFast.Outcome) / moved=$($failedFast.Moved)"

    # The same shape with exit 0 keeps the pre-existing guess. Its wording is deliberate - the two
    # lease checks in the runner cover what they can observe, and this covers what is left.
    $quietOk = Resolve-RunOutcome -Outcome 'ok' -ExitCode 0 -StatusBefore 'Partial' -StatusAfter 'Partial' -ElapsedSeconds 3
    Assert-Case -Name 'a clean quick exit that changed nothing keeps the elapsed-time guess' `
        -Ok ($quietOk.Outcome -eq 'no-progress-or-claim-lost') `
        -Detail "expected: no-progress-or-claim-lost | actual: $($quietOk.Outcome)"

    # 'ok' is an idle outcome in this repository's profile, so a long failing run used to extend the
    # series exactly as a successful one did - which is how S1565 came to be held as [idle 2, ok] on
    # 2026-09-10 with its last run having exited 1.
    $failedSlow = Resolve-RunOutcome -Outcome 'ok' -ExitCode 1 -StatusBefore 'In Progress' -StatusAfter 'In Progress' -ElapsedSeconds 240
    Assert-Case -Name 'a long run that failed is a failure too, not a run that reached the ticket' `
        -Ok ($failedSlow.Outcome -eq 'child-failed') `
        -Detail "expected: child-failed | actual: $($failedSlow.Outcome)"

    # Work done before the failure is still work done: the status moved, so the series must reset.
    $failedAfterMove = Resolve-RunOutcome -Outcome 'ok' -ExitCode 1 -StatusBefore 'Draft' -StatusAfter 'Approved' -ElapsedSeconds 700
    Assert-Case -Name 'a failure after a real status move still reports the move' `
        -Ok ($failedAfterMove.Outcome -eq 'child-failed' -and $failedAfterMove.Moved) `
        -Detail "expected: child-failed / moved true | actual: $($failedAfterMove.Outcome) / moved=$($failedAfterMove.Moved)"

    # 1073807364 is 0x40010004 - the process was killed. Two such rows in the live journal recorded
    # 'Draft -> "", moved: true': Get-TicketStatus could not read the catalog under a dying child,
    # and '' differs from every real status. moved outranks the outcome in every consumer, so this
    # case is what stops the rename being cosmetic on exactly those rows.
    $killed = Resolve-RunOutcome -Outcome 'ok' -ExitCode 1073807364 -StatusBefore 'Draft' -StatusAfter '' -ElapsedSeconds 1200
    Assert-Case -Name 'an unreadable status after the run is a missing reading, never a move' `
        -Ok ($killed.Outcome -eq 'child-failed' -and -not $killed.Moved) `
        -Detail "expected: child-failed / moved false | actual: $($killed.Outcome) / moved=$($killed.Moved)"

    # The three verdicts that observed the child itself outrank the exit code. A killed process's
    # exit code is a consequence of the kill, so reading it as a diagnosis would relabel every
    # timeout and every lost claim as a failed child.
    $timedOut = Resolve-RunOutcome -Outcome 'timeout' -ExitCode $null -StatusBefore 'Tactical' -StatusAfter 'Tactical' -ElapsedSeconds 3600
    Assert-Case -Name 'a timeout keeps its name' `
        -Ok ($timedOut.Outcome -eq 'timeout') -Detail "expected: timeout | actual: $($timedOut.Outcome)"

    $claimLost = Resolve-RunOutcome -Outcome 'claim-lost' -ExitCode $null -StatusBefore 'Draft' -StatusAfter 'Approved' -ElapsedSeconds 40
    Assert-Case -Name 'a lost claim keeps its name and never reports the sibling move as its own' `
        -Ok ($claimLost.Outcome -eq 'claim-lost' -and -not $claimLost.Moved) `
        -Detail "expected: claim-lost / moved false | actual: $($claimLost.Outcome) / moved=$($claimLost.Moved)"

    $launchFailed = Resolve-RunOutcome -Outcome 'launch-failed' -ExitCode $null -StatusBefore 'Draft' -StatusAfter 'Draft' -ElapsedSeconds 1
    Assert-Case -Name 'a child that never launched keeps its name' `
        -Ok ($launchFailed.Outcome -eq 'launch-failed') `
        -Detail "expected: launch-failed | actual: $($launchFailed.Outcome)"
}

Write-Host ''
if ($failures -gt 0) {
    Write-Host ("run-spec-queue tests: FAIL ({0} of {1} case(s))" -f $failures, $caseCount) -ForegroundColor Red
    exit 1
}
$skipNote = if ($skipped -gt 0) { ", $skipped skipped" } else { '' }
Write-Host ("run-spec-queue tests: PASS ({0} case(s){1})" -f $caseCount, $skipNote) -ForegroundColor Green
exit 0
