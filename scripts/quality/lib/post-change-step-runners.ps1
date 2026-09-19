#requires -Version 7.0
# S3150: the step and gate execution surface of scripts/post-change.ps1 - run state, the protocol
# file, the captured child output, the recovery hints, the chat verdict, the step/gate/advisory/
# fixed-input wrappers and the barrier that guards the mutating steps. None of it names a single
# gate, yet every new gate used to grow the file that carried it, which is how the facade passed
# the 2000-line ceiling of CLAUDE.md Rule 2 (measured 2314 lines on 2026-09-16).
#
# Dot-sourced, never invoked as a child: the wrappers read and write the run`s $script: state and
# $LASTEXITCODE in the facade`s own scope, so a child process or a module would break the very
# behaviour this extraction must leave untouched. $root is supplied by the caller.

# S1338: every advisory gate that found something lands here, so the final
# verdict can report what it could not attribute instead of swallowing it.
$script:AdvisoryFindings = @()

# S1598: every FATAL gate failure lands here instead of ending the process on the
# spot. Fail-fast made one set of defects cost several full runs of the facade:
# 215 failed runs in the week of 2026-08-05, median 8 turns from a failed run to
# the next one, because each run could only ever name the first thing wrong.
$script:FatalFindings = @()

# S1937: steps that did not apply to this change. Collected instead of printed one per line -
# a closure skips more steps than it runs, and each printed line stays in the session context
# for every later request. The summary keeps every name, so "why did detekt not run" is still
# answerable; -ShowSkips restores the per-step reason.
$script:SkippedSteps = @()

# S3301: set by the closure ledger when this run`s changed set was already judged clean by a
# recent run of the same closure over the same bytes. Declared here rather than in the ledger
# because the wrappers below are what read it, and a consumer that never loads the ledger must
# still parse and behave exactly as before.
$script:ClosureReuseActive = $false
$script:ClosureReuseRecord = $null

# S3151: per-gate lines go to a protocol file; the console gets them only on request.
$script:ConsoleVerbose = $env:FMS_POSTCHANGE_VERBOSE -eq '1'
$script:ConsolePasses = $ShowPasses -or $script:ConsoleVerbose
$script:ConsoleSkips = $ShowSkips -or $script:ConsoleVerbose
$script:PassedCount = 0
$script:ProtocolPath = Join-Path $root ("temp/metrics/post-change-runs/{0}-{1}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $PID)

function Write-ProtocolLine([string]$Line) {
    try {
        $dir = Split-Path -Parent $script:ProtocolPath
        if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        Add-Content -LiteralPath $script:ProtocolPath -Value $Line -Encoding utf8
    }
    catch {
        # The protocol is a convenience copy; the verdict and the telemetry journal do not depend on it.
        Write-Host "  [protocol] WARN - not written: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

function Write-ProtocolPointer {
    if (Test-Path -LiteralPath $script:ProtocolPath) {
        Write-Host "protocol: $($script:ProtocolPath)" -ForegroundColor DarkGray
    }
}

# S3151: a gate child prints its own report, which used to reach the console on every clean run.
# It is buffered per step; the wrapper prints the buffer when the step fails or turns advisory,
# verbose mode streams it live, and the protocol file always receives it.
$script:CaptureLines = [System.Collections.Generic.List[string]]::new()
function Invoke-CapturedAction([scriptblock]$Action) {
    $script:CaptureLines = [System.Collections.Generic.List[string]]::new()
    try {
        & $Action *>&1 | ForEach-Object {
            $captureText = "$_"
            $script:CaptureLines.Add($captureText)
            if ($script:ConsolePasses) { Write-Host $captureText }
        }
    }
    finally {
        if ($script:CaptureLines.Count -gt 0) {
            Write-ProtocolLine (($script:CaptureLines | ForEach-Object { "    $_" }) -join [Environment]::NewLine)
        }
    }
}

function Write-CapturedOutput {
    if ($script:ConsolePasses) { return }
    foreach ($captureText in $script:CaptureLines) { Write-Host $captureText }
}

# S1598: label -> @{ Repro = '<command that runs this gate alone>'; Fix = '<what to do>' }.
# Data, not prose in the facade, so registering a new gate never edits the output
# logic (owner input). A label with no entry prints without a hint - not an error;
# assert-gate-hints-sync.ps1 is what keeps the two sets in step.
$script:GateHints = @{}
$hintFile = Join-Path $root "scripts/quality/gate-recovery-hints.psd1"
if (Test-Path $hintFile) {
    try { $script:GateHints = Import-PowerShellDataFile -LiteralPath $hintFile }
    catch { Write-Host "  [gate-hints] WARN - unreadable: $($_.Exception.Message)" -ForegroundColor Yellow }
}

function Send-PostChangeChatVerdict {
    # S2372: the closure verdict is a moment sibling sessions want to see. A child process keeps the
    # facade decoupled from the lock library; best effort keeps a chat failure from touching the verdict.
    param([Parameter(Mandatory)][string]$Verdict)
    try {
        $cli = Join-Path $root 'scripts/utils/agent-chat.ps1'
        if (-not (Test-Path -LiteralPath $cli)) { return }
        $exe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
        $note = "post-change $Verdict ($resolvedChangeType) $Target - $Description"
        & $exe -NoProfile -File $cli -Verb Post -Kind verdict -Note $note *> $null
    }
    catch { }
}

function Get-GateHint([string]$Label) {
    if ($script:GateHints.ContainsKey($Label)) { return $script:GateHints[$Label] }
    return $null
}

function Write-StepResult(
    [string]$Label,
    [ValidateSet('PASS', 'FAIL', 'SKIP')][string]$Status,
    [int]$ElapsedMs,
    [string]$Details = '',
    [int]$ExitCode = 0,
    # Advisory SKIP lines carry what the gate found, so they print even on a quiet run.
    [switch]$AlwaysShow
) {
    Write-GateTelemetryRecord -Runner 'post-change' -Gate $Label -Status $Status `
        -ExitCode $ExitCode -ElapsedMs ([Math]::Max($ElapsedMs, 0))
    if ($Status -eq 'PASS') { $script:PassedCount++ }

    $color = switch ($Status) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        default { 'DarkGray' }
    }

    $message = "  [$Label] $Status"
    if ($ElapsedMs -ge 0) {
        $message += " ($ElapsedMs ms)"
    }
    if (-not [string]::IsNullOrWhiteSpace($Details)) {
        $message += " - $Details"
    }

    Write-ProtocolLine $message
    $show = switch ($Status) {
        'FAIL' { $true }
        'PASS' { $script:ConsolePasses }
        default { $script:ConsoleSkips -or $AlwaysShow }
    }
    if ($show) { Write-Host $message -ForegroundColor $color }
}

# A pooled gate ran before its call site was reached, so the wrapper's stopwatch measured the wait.
# The child's own measurement is the one the gate-frequency report needs.
function Get-StepElapsedMs([System.Diagnostics.Stopwatch]$Stopwatch) {
    $pooled = Get-PooledElapsedMs
    if ($null -ne $pooled) { return [int]$pooled }
    return [int]$Stopwatch.Elapsed.TotalMilliseconds
}

function Invoke-Step([string]$Label, [scriptblock]$Action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Reset-PooledElapsedMs

    try {
        $global:LASTEXITCODE = 0
        Invoke-CapturedAction $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        if ($exitCode -ne 0) {
            throw "exit $exitCode"
        }

        $sw.Stop()
        Write-StepResult -Label $Label -Status PASS -ElapsedMs (Get-StepElapsedMs $sw)
    }
    catch {
        $sw.Stop()
        Write-CapturedOutput
        $exitCode = if ($LASTEXITCODE -and [int]$LASTEXITCODE -ne 0) { [int]$LASTEXITCODE } else { 1 }
        $reason = $_.Exception.Message
        if ($reason -eq "exit $exitCode") {
            $reason = "child exit code $exitCode"
        }

        Write-StepResult -Label $Label -Status FAIL -ElapsedMs (Get-StepElapsedMs $sw) `
            -Details $reason -ExitCode $exitCode
        Write-ProtocolPointer
        exit $exitCode
    }
}

# S1598: the gate wrapper. Same verdict surface as Invoke-Step, but a failure is
# recorded and the run continues, so ONE run names every gate the changed set
# breaks. Certification is unchanged: Test-FatalFindings barricades the mutating
# steps and exits 1, so a failed run still writes no changelog row and no catalog
# index. Invoke-Step stays for those mutating steps, where "cannot go on" is real.
# S2612: check-standard-fast.ps1 returns 4 for "queued, not my turn" - a short, foreground-scale
# check that found its build domain busy and refused rather than blocking past the caller's 120 s
# timeout. Nothing was inspected and nothing is wrong, which is this script's exit 2, not its exit 1:
# the contract at the top of this file is explicit that "found a defect" and "did not look" are
# different answers, and Invoke-Gate would otherwise turn the refusal into a FatalFinding and report
# a red closure over a lock somebody else legitimately held.
#
# Aborting outright is safe here because every gate runs BEFORE the first mutating step, so this
# writes no changelog row, no capability record and no catalog entry - the run is simply repeatable
# once the domain frees up.
function Stop-ClosureOnQueuedBuild([int]$ExitCode, [string]$Label) {
    if ($ExitCode -ne 4) { return }
    # Called inside a captured gate action: Write-Host would land in the step buffer and die with
    # the process, and the queue handoff the child printed must reach the caller.
    if (-not $script:ConsolePasses) { foreach ($captureText in $script:CaptureLines) { [Console]::Out.WriteLine($captureText) } }
    [Console]::Out.WriteLine('')
    [Console]::Out.WriteLine("post-change: could not verify - '$Label' was QUEUED behind another session's build, not run.")
    [Console]::Out.WriteLine("  Nothing was inspected and nothing was written. Your place in the build queue is taken.")
    [Console]::Out.WriteLine("  Wait for the turn in the background with the command the check printed above, then re-run this closure.")
    exit 2
}

function Invoke-Gate([string]$Label, [scriptblock]$Action) {
    if ($script:ClosureReuseActive) {
        Skip-Step $Label "reused: run $($script:ClosureReuseRecord.RunId) passed this gate over the same bytes $($script:ClosureReuseRecord.AgeSec)s ago"
        return
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Reset-PooledElapsedMs

    try {
        $global:LASTEXITCODE = 0
        Invoke-CapturedAction $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        if ($exitCode -ne 0) {
            throw "exit $exitCode"
        }

        $sw.Stop()
        Write-StepResult -Label $Label -Status PASS -ElapsedMs (Get-StepElapsedMs $sw)
    }
    catch {
        $sw.Stop()
        Write-CapturedOutput
        $exitCode = if ($LASTEXITCODE -and [int]$LASTEXITCODE -ne 0) { [int]$LASTEXITCODE } else { 1 }
        $reason = $_.Exception.Message
        if ($reason -eq "exit $exitCode") {
            $reason = "child exit code $exitCode"
        }

        Write-StepResult -Label $Label -Status FAIL -ElapsedMs (Get-StepElapsedMs $sw) `
            -Details $reason -ExitCode $exitCode
        $hint = Get-GateHint $Label
        if ($hint) {
            if ($hint.Repro) { Write-Host "      repro: $($hint.Repro)" -ForegroundColor Yellow }
            if ($hint.Fix) { Write-Host "      fix:   $($hint.Fix)" -ForegroundColor Yellow }
        }
        $script:FatalFindings += [pscustomobject]@{ Label = $Label; ExitCode = $exitCode }
    }
}

# S1598: the barrier. Called immediately before the first mutating step, so the
# accumulated failures end the run exactly where fail-fast used to end it - with
# nothing written. Exit 1 means "found a defect", per the exit contract above.
# S2326: the read-only gates are started together and consumed at their own call sites, so the
# printed sequence, the exit codes and the fail-fast barrier stay identical while the wall clock
# drops. The mechanism and the two rules that keep it honest live in the library, which is
# dot-sourced rather than inlined so scripts/quality.tests can execute it.
. (Join-Path $root 'scripts/quality/lib/gate-pool.ps1')

function Test-FatalFindings {
    if ($script:FatalFindings.Count -eq 0) { return }

    Write-Host ''
    Write-SkippedSummary
    Write-Host "post-change: FAIL ($($script:FatalFindings.Count) gate(s), $resolvedChangeType)" -ForegroundColor Red
    Send-PostChangeChatVerdict -Verdict "FAIL ($($script:FatalFindings.Count) gate(s))"
    foreach ($finding in $script:FatalFindings) {
        Write-Host "  failed: $($finding.Label) (exit $($finding.ExitCode))" -ForegroundColor Red
        $hint = Get-GateHint $finding.Label
        if ($hint -and $hint.Repro) { Write-Host "      repro: $($hint.Repro)" -ForegroundColor Yellow }
    }
    Write-Host "  Nothing was written: no changelog row, no catalog sync. Fix the above and re-run." -ForegroundColor Red
    Write-ProtocolPointer
    exit 1
}

function Skip-Step([string]$Label, [string]$Reason) {
    $script:SkippedSteps += [pscustomobject]@{ Label = $Label; Reason = $Reason }
    # Write-StepResult records the journal row and the protocol line, and prints only on request.
    Write-StepResult -Label $Label -Status SKIP -ElapsedMs 0 -Details $Reason
}

# S1937: one line for the whole not-applicable set, printed before the verdict on the failing
# and advisory paths so a failed closure still shows what never ran.
function Write-SkippedSummary {
    if ($script:ConsoleSkips -or $script:SkippedSteps.Count -eq 0) { return }
    $names = ($script:SkippedSteps | ForEach-Object { $_.Label }) -join ', '
    Write-Host "  skipped ($($script:SkippedSteps.Count), not applicable to this change): $names" -ForegroundColor DarkGray
    Write-Host '  reasons: re-run with -ShowSkips' -ForegroundColor DarkGray
}

# S0826: like Invoke-Step but non-fatal. A project-wide gate that cannot attribute its
# failure to THIS change (today only icon-inventory-sync; the count-ratchet gates moved to
# FATAL per-file deltas in S0848/S0850) is reported as a WARN under -ScopeToFile and the
# facade keeps going instead of aborting the close. The operator still sees it.
function Invoke-AdvisoryStep([string]$Label, [scriptblock]$Action, [string]$AdvisoryDetails) {
    if ($script:ClosureReuseActive) {
        Skip-Step $Label "reused: run $($script:ClosureReuseRecord.RunId) passed this gate over the same bytes $($script:ClosureReuseRecord.AgeSec)s ago"
        return
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $global:LASTEXITCODE = 0
        Invoke-CapturedAction $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        $sw.Stop()
        if ($exitCode -ne 0) {
            # Most advisory gates are project-wide ratchets whose finding may belong to another
            # ticket's WIP. A caller that knows better (the preflight judges YOUR files only)
            # passes its own wording, so the verdict line never misdescribes what was found.
            $details = if ($AdvisoryDetails) { $AdvisoryDetails } else { "advisory (project-wide ratchet; not attributed to your change - verify your files manually)" }
            Write-CapturedOutput
            Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details $details -AlwaysShow
            $script:AdvisoryFindings += "$Label (exit $exitCode)"
        }
        else {
            Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
        }
    }
    catch {
        $sw.Stop()
        Write-CapturedOutput
        Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details "advisory (gate error: $($_.Exception.Message))" -AlwaysShow
        $script:AdvisoryFindings += "$Label (gate error)"
    }
}

# S2824: the call site for a fixed-input gate - one that reads a short, enumerated list of source
# files and judges one rule between them. Given the changed set it answers 3 for "a divergence
# stands in the tree, but no file I read is in that set", which is an advisory here: its author is
# another session, and charging this closure for it made CLAUDE.md section 12's promise false for
# these gates exactly as S1889/S1895 did for the ticket-log gate. Codes 1 and 2 stay fatal.
#
# The child runs OUTSIDE the wrapper because the verdict shape is chosen from its exit code and
# Invoke-AdvisoryStep's catch turns any exception into a SKIP - raising one for code 1 would
# downgrade this change's own divergence instead of scoping it. The pooled duration is carried
# across by hand, because Invoke-Gate's Reset-PooledElapsedMs would otherwise leave the wrapper's
# own near-zero stopwatch in the telemetry that measure-gate-frequency.ps1 ranks gates by.
function Invoke-FixedInputGate([string]$Label, [string[]]$Argv, [string]$GateScript) {
    # S3301: short-circuited here as well as in the two wrappers below, because this one runs its
    # child BEFORE it decides which wrapper reports the verdict - reaching Invoke-Gate too late to
    # save the work.
    if ($script:ClosureReuseActive) {
        Skip-Step $Label "reused: run $($script:ClosureReuseRecord.RunId) passed this gate over the same bytes $($script:ClosureReuseRecord.AgeSec)s ago"
        return
    }
    Invoke-CapturedAction { Invoke-GateChild @Argv }
    $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    $elapsedMs = Get-PooledElapsedMs
    # Replayed inside the verdict wrapper below, which captures it again and prints it only on red.
    $childLines = @($script:CaptureLines)

    if ($exitCode -eq 3) {
        Invoke-AdvisoryStep $Label { $childLines | ForEach-Object { Write-Host $_ }; $global:LASTEXITCODE = 3 } `
            -AdvisoryDetails ("a divergence stands in the tree between files this change never opened, so this " +
                "closure is not charged for it - its author owns it. Run $GateScript with no -ChangedFiles for " +
                "the project-wide verdict.")
        return
    }

    # Restored INSIDE the action: Invoke-Gate opens with Reset-PooledElapsedMs, so a value set
    # before the call is wiped before the step result reads it.
    Invoke-Gate $Label {
        $childLines | ForEach-Object { Write-Host $_ }
        if ($null -ne $elapsedMs) { Set-PooledElapsedMs $elapsedMs }
        $global:LASTEXITCODE = $exitCode
    }
}
