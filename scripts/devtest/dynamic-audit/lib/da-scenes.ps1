#requires -Version 7.0
<#
.SYNOPSIS
    Scene registry, mutation envelope, scene runs and heavy-run sequence of the S4166 dynamic-audit stand
    (lifecycle scenes C01, C02, C03, file-operation scenes C07, C15, load and growth cycle scenes C05, C06,
    C12, C14, C16).

.DESCRIPTION
    Dot-source after lib/da-common.ps1, da-emulator.ps1, da-fixture.ps1, da-state.ps1, da-stats.ps1,
    da-sampler.ps1, da-probe.ps1, da-fault.ps1 and da-ui.ps1. Functions only; nothing is assigned at
    script scope. The UI driver (adb.ps1 verbs, the accessibility-event capture of toasts, navigation and
    resource registration) lives in lib/da-ui.ps1.

    ENVELOPE. Invoke-DaEnvelope is the reusable mutation envelope: durable restore checkpoint first, the
    body second, Restore-DaBackup in finally whatever happened. A killed run leaves the checkpoint, and
    -Verb Recover restores it at the next start.

    SCENES. Get-DaSceneRegistry lists every scene with its named actions; each action is run through
    Invoke-DaSceneAction, which stamps the correlation id, the monotonic start and duration, the probe
    context (foreground window, pid, process state) and keeps a 1 Hz sampler attached to the app process
    (restarted when the process changes). Each scene writes runs/<runId>/scene.json with its invariants,
    the raw user-visible message texts and where the user learned the outcome, candidate findings and the
    atomic scenes left open with the concrete reason.

    INVARIANTS ARE INDEPENDENT. After every file operation the run's src and dst trees are re-hashed on the
    device (Get-DaTreeHash) and compared with Compare-DaTreeHash and Test-DaTreeExpectation; an application
    log line never decides an invariant.

    HEAVY RUN. Invoke-DaHeavyRun: emulator guard (done by the facade), preflight, verified backup, restore
    checkpoint, fixture publish, calibration check, one scene with at most one fault, the outside-the-run
    invariant, Restore-DaBackup in finally, heavy.json. Every step is a hook so the sequence and the
    restore-on-failure path are tested without a device.

    CYCLE SCENES (lib/da-cycle.ps1). C05, C06, C12, C14 and C16 run through Invoke-DaCycleScene (the Cycle verb runs it in the
    heavy-run sequence): a measured cold start, the scene's setup, N cycles each closed at the scene's
    quiescence point (the main screen with no dialog and 3 s without input, the open list for C16, the
    launcher for its background half, the watch app on a watch) with one meminfo sample, then a warm start.
    The first cycles are the explicit warm-up part. cycles.jsonl carries every cycle's index, phase, segment,
    monotonic interval and sample; no garbage collection is forced, and a heap snapshot is diagnostic and
    taken only after the window. C12 and C16 also run on the watch build.
#>

# ----------------------------------------------------------------------------------------- tree invariants

function Get-DaRunTree {
    <#
    .SYNOPSIS
        Hashes the run's src and dst directories into one tree whose paths start with src/ or dst/.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER RunRemoteDir
        The run's fixture directory.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$RunRemoteDir)
    $entries = foreach ($side in 'src', 'dst') {
        $t = Get-DaTreeHash -Serial $Serial -RemoteDir "$RunRemoteDir/$side"
        foreach ($e in $t.Entries) { [pscustomobject]@{ path = "$side/$($e.path)"; size = $e.size; sha256 = $e.sha256 } }
    }
    return New-DaTreeRecord -Entries @($entries)
}

function Test-DaTreeExpectation {
    <#
    .SYNOPSIS
        Judges a before/after tree pair against the declared change of one operation. Pure.
    .DESCRIPTION
        Every difference must be declared: added paths (optionally byte-equal to a named source), removed
        paths, changed paths, or optional paths whose presence either way is accepted. Returns
        {holds; diff; problems}.
    .PARAMETER Before
        Tree record or entries before.
    .PARAMETER After
        Tree record or entries after.
    .PARAMETER Added
        Paths that must appear.
    .PARAMETER Removed
        Paths that must disappear.
    .PARAMETER Changed
        Paths whose content may change.
    .PARAMETER SameAs
        Hashtable path -> source path: the added path must equal the source in the BEFORE tree.
    .PARAMETER Optional
        Paths that may appear, disappear or change without breaking the invariant.
    .PARAMETER OptionalPattern
        Regex of paths treated as optional.
    #>
    param(
        [AllowEmptyCollection()][object[]]$Before,
        [AllowEmptyCollection()][object[]]$After,
        [string[]]$Added = @(),
        [string[]]$Removed = @(),
        [string[]]$Changed = @(),
        [hashtable]$SameAs = @{},
        [string[]]$Optional = @(),
        [string]$OptionalPattern
    )
    $unwrap = { param($x) if ($x.Count -eq 1 -and $null -ne $x[0] -and $x[0].PSObject.Properties['Entries']) { @($x[0].Entries) } else { @($x | Where-Object { $_ }) } }
    $b = @{}; foreach ($e in (& $unwrap @($Before))) { $b[[string]$e.path] = $e }
    $a = @{}; foreach ($e in (& $unwrap @($After))) { $a[[string]$e.path] = $e }
    $diff = Compare-DaTreeHash -Reference @($b.Values) -Difference @($a.Values)
    $problems = [System.Collections.Generic.List[string]]::new()
    $isOpt = { param($p) ($Optional -contains $p) -or ($OptionalPattern -and $p -match $OptionalPattern) }
    foreach ($p in $diff.Added) { if ($Added -notcontains $p -and -not (& $isOpt $p)) { $problems.Add("unexpected new file $p") } }
    foreach ($p in $diff.Removed) { if ($Removed -notcontains $p -and -not (& $isOpt $p)) { $problems.Add("unexpected loss of $p") } }
    foreach ($p in $diff.Changed) { if ($Changed -notcontains $p -and -not (& $isOpt $p)) { $problems.Add("unexpected change of $p") } }
    foreach ($p in $Added) { if (-not $a.ContainsKey($p)) { $problems.Add("expected new file $p is absent") } }
    foreach ($p in $Removed) { if ($a.ContainsKey($p)) { $problems.Add("expected removal of $p did not happen") } }
    foreach ($k in $SameAs.Keys) {
        $src = [string]$SameAs[$k]
        if (-not $a.ContainsKey($k) -or -not $b.ContainsKey($src)) { continue }
        if ([string]$a[$k].sha256 -ne [string]$b[$src].sha256 -or [long]$a[$k].size -ne [long]$b[$src].size) {
            $problems.Add("$k differs from its source $src (size $($a[$k].size) vs $($b[$src].size))")
        }
    }
    return [pscustomobject]@{ holds = ($problems.Count -eq 0); diff = $diff.Summary; problems = $problems.ToArray() }
}

# --------------------------------------------------------------------------------------------- logs, crashes

function Find-DaCrashSignals {
    <#
    .SYNOPSIS
        Crash, ANR and crash-handler exit lines of a package in logcat text. Pure.
    .PARAMETER Lines
        logcat -v threadtime lines.
    .PARAMETER Package
        Application id.
    #>
    param([string[]]$Lines, [Parameter(Mandatory)][string]$Package)
    $pk = [regex]::Escape($Package)
    $out = [System.Collections.Generic.List[object]]::new()
    for ($i = 0; $i -lt @($Lines).Count; $i++) {
        $l = $Lines[$i]
        if ($l -match 'FATAL EXCEPTION') {
            $ctx = (@($Lines[$i..([math]::Min($i + 3, $Lines.Count - 1))]) -join ' ')
            if ($ctx -match "Process: $pk\b") { $out.Add([pscustomobject]@{ kind = 'crash'; line = $l.Trim(); context = $ctx.Substring(0, [math]::Min(400, $ctx.Length)) }) }
        }
        elseif ($l -match "ANR in $pk\b") { $out.Add([pscustomobject]@{ kind = 'anr'; line = $l.Trim(); context = $l.Trim() }) }
        elseif ($l -match "Process $pk \(pid \d+\) has died") { $out.Add([pscustomobject]@{ kind = 'process-died'; line = $l.Trim(); context = $l.Trim() }) }
    }
    return $out.ToArray()
}

function Save-DaSceneLogcat {
    <#
    .SYNOPSIS
        Saves the device log (main, system, crash) into the run directory; returns {File; Signals}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER OutFile
        Host file.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$OutFile)
    $null = Invoke-DaAdbToFile -Serial $Serial -Arguments @('exec-out', 'logcat', '-d', '-v', 'threadtime', '-b', 'main,system,crash') -OutFile $OutFile -TimeoutMs 120000
    $lines = if (Test-Path -LiteralPath $OutFile) { [System.IO.File]::ReadAllLines($OutFile, [System.Text.UTF8Encoding]::new($false)) } else { @() }
    return [pscustomobject]@{ File = (Split-Path -Leaf $OutFile); Signals = @(Find-DaCrashSignals -Lines $lines -Package $Package) }
}

# ----------------------------------------------------------------------------------------------- envelope

function Invoke-DaEnvelope {
    <#
    .SYNOPSIS
        The mutation envelope: restore checkpoint, body, Restore-DaBackup in finally. Returns {Code; Reason; Result; Restore; Restored}.
    .DESCRIPTION
        The checkpoint is durable before the body starts, so a killed run is recovered by -Verb Recover.
        A body failure becomes code 2 with its message; a failed restore makes the code 2 whatever the
        body returned. -Checkpoint and -Restore are hooks for the device-free suite.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id the body mutates.
    .PARAMETER RunId
        Run id.
    .PARAMETER Reason
        What mutates the device.
    .PARAMETER Body
        Scriptblock; its output object should carry Code and Reason.
    .PARAMETER Checkpoint
        Hook replacing New-DaRestoreCheckpoint (tests).
    .PARAMETER Restore
        Hook replacing Restore-DaBackup (tests).
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [Parameter(Mandatory)][string]$RunId,
        [Parameter(Mandatory)][string]$Reason,
        [Parameter(Mandatory)][scriptblock]$Body,
        [scriptblock]$Checkpoint,
        [scriptblock]$Restore
    )
    $codes = Get-DaExitCode
    if (-not $Checkpoint) { $Checkpoint = { New-DaRestoreCheckpoint -Serial $Serial -Package $Package -Reason $Reason -RunId $RunId } }
    if (-not $Restore) { $Restore = { Restore-DaBackup -Serial $Serial } }
    $ck = & $Checkpoint
    if ([int]$ck.Code -ne 0) { return [pscustomobject]@{ Code = [int]$ck.Code; Reason = "refused before any mutation: $($ck.Reason)"; Result = $null; Restore = $null; Restored = $false } }
    $envResult = $null
    $envRestore = $null
    try {
        $envResult = & $Body
    }
    catch {
        $envResult = [pscustomobject]@{ Code = $codes.Refused; Reason = "run $RunId failed: $($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)" }
    }
    finally {
        try { $envRestore = & $Restore }
        catch { $envRestore = [pscustomobject]@{ Code = $codes.Refused; Reason = "restore threw: $($_.Exception.Message); checkpoint kept, run -Verb Recover" } }
    }
    if (-not $envResult -or $null -eq $envResult.PSObject.Properties['Code']) { $envResult = [pscustomobject]@{ Code = $codes.Refused; Reason = 'the run returned no verdict' } }
    $code = [math]::Max([int]$envResult.Code, [int]$envRestore.Code)
    return [pscustomobject]@{ Code = $code; Reason = "$($envResult.Reason); restore: $($envRestore.Reason)"; Result = $envResult; Restore = $envRestore; Restored = ([int]$envRestore.Code -eq 0) }
}

# ------------------------------------------------------------------------------------------ scene context

function New-DaSceneContext {
    <#
    .SYNOPSIS
        A scene run's mutable context (hashtable): ids, run directory, action and invariant lists.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Scene
        Scene id.
    .PARAMETER RunId
        Run id.
    .PARAMETER Fault
        Fault id or empty.
    .PARAMETER Variant
        Build variant label.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Scene,
        [Parameter(Mandatory)][string]$RunId, [string]$Fault, [string]$Variant = 'standard')
    $runDir = Join-Path (Get-DaPaths).Runs $RunId
    [void][System.IO.Directory]::CreateDirectory($runDir)
    return @{
        Serial = $Serial; Package = $Package; Scene = $Scene; RunId = $RunId; Fault = $Fault; Variant = $Variant
        RunDir = $runDir; CorrelationId = New-DaCorrelationId
        StartedAt = (Get-Date).ToUniversalTime().ToString('o'); StartedMonoMs = Get-DaMonotonicMs
        Actions = [System.Collections.Generic.List[object]]::new()
        Invariants = [System.Collections.Generic.List[object]]::new()
        Messages = [System.Collections.Generic.List[object]]::new()
        Findings = [System.Collections.Generic.List[object]]::new()
        Open = [System.Collections.Generic.List[object]]::new()
        NotApplicable = [System.Collections.Generic.List[object]]::new()
        Samplers = [System.Collections.Generic.List[object]]::new()
        Notes = [System.Collections.Generic.List[string]]::new()
        Sampler = $null; SamplerIndex = 0; Fixture = $null; FaultRecord = $null; Cleanups = [System.Collections.Generic.List[scriptblock]]::new()
        Resources = @{}
    }
}

function Update-DaSceneSampler {
    <#
    .SYNOPSIS
        Keeps one sampler bound to the app's current process: collects a finished one, starts a new one when the app runs.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Stop
        Stop the running sampler and start none.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [switch]$Stop)
    if ($Ctx.Sampler -and ($Stop -or $Ctx.Sampler.Sync.Done)) {
        $sum = Stop-DaSampler -Sampler $Ctx.Sampler
        $Ctx.Samplers.Add($sum)
        $Ctx.Sampler = $null
    }
    if ($Stop -or $Ctx.Sampler) { return }
    if (Get-DaProcessIdentity -Serial $Ctx.Serial -Package $Ctx.Package) {
        $Ctx.SamplerIndex++
        try {
            $Ctx.Sampler = Start-DaSampler -Serial $Ctx.Serial -Package $Ctx.Package -OutFile (Join-Path $Ctx.RunDir ("sampler-{0:D2}.jsonl" -f $Ctx.SamplerIndex)) -PeriodMs 1000 -MeminfoEvery 10 -RunAs
        }
        catch { $Ctx.Notes.Add("sampler not started: $($_.Exception.Message)") }
    }
}

function Invoke-DaSceneAction {
    <#
    .SYNOPSIS
        Runs one named scene action with the probe and sampler attached; returns the body's output.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Name
        Action name.
    .PARAMETER Atomic
        Atomic scene id the action belongs to.
    .PARAMETER Body
        Scriptblock of the action. It runs in a child scope of this function, so a variable of the calling
        scene named like a parameter here ($Name, $Atomic, $Body, $Ctx - names are case-insensitive) is
        shadowed inside the body; scenes use other names.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Name, [string]$Atomic, [Parameter(Mandatory)][scriptblock]$Body)
    $saStart = Get-DaMonotonicMs
    $saOut = $null; $saErr = $null
    try { $saOut = & $Body } catch { $saErr = "$($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)" }
    $saEnd = Get-DaMonotonicMs
    $saProbe = $null
    try { $saProbe = Get-DaProbeContext -Serial $Ctx.Serial -Package $Ctx.Package } catch { $saProbe = $null }
    Update-DaSceneSampler -Ctx $Ctx
    $Ctx.Actions.Add([ordered]@{
            name = $Name; atomic = $Atomic; correlationId = $Ctx.CorrelationId; startMonoMs = $saStart; durationMs = $saEnd - $saStart
            ok = (-not $saErr); error = $saErr; result = $saOut; probe = $saProbe
        })
    if ($saErr) { $Ctx.Notes.Add("action $Name failed: $saErr") }
    return $saOut
}

function Add-DaInvariant {
    <#
    .SYNOPSIS
        Records one checked invariant.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Id
        Invariant id.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER Holds
        Whether it holds.
    .PARAMETER Detail
        What was compared.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Id, [string]$Atomic, [Parameter(Mandatory)][bool]$Holds, [string]$Detail)
    $Ctx.Invariants.Add([ordered]@{ id = $Id; atomic = $Atomic; holds = $Holds; detail = $Detail; correlationId = $Ctx.CorrelationId })
}

function Add-DaFinding {
    <#
    .SYNOPSIS
        Records a candidate finding with steps, evidence, severity guess and rung.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Title
        One-line title.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER Severity
        P0..P3 guess.
    .PARAMETER Rung
        V or T.
    .PARAMETER Steps
        Exact steps.
    .PARAMETER Expected
        Expected outcome.
    .PARAMETER Actual
        Observed outcome.
    .PARAMETER Evidence
        Evidence file names in the run directory.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Title, [string]$Atomic, [ValidateSet('P0', 'P1', 'P2', 'P3')][string]$Severity = 'P2',
        [ValidateSet('V', 'T')][string]$Rung = 'V', [string[]]$Steps = @(), [string]$Expected, [string]$Actual, [string[]]$Evidence = @())
    $Ctx.Findings.Add([ordered]@{
            title = $Title; scene = $Ctx.Scene; atomic = $Atomic; variant = $Ctx.Variant; fault = $Ctx.Fault; severityGuess = $Severity; rung = $Rung
            candidate = $true; steps = $Steps; expected = $Expected; actual = $Actual; evidence = $Evidence; correlationId = $Ctx.CorrelationId
        })
}

function Add-DaMessage {
    <#
    .SYNOPSIS
        Records the raw user-visible outcome texts of an action and where the user learns the outcome.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Action
        Action name.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER Trigger
        Invoke-DaUiTriggerTap output (toasts and dialogs).
    .PARAMETER Expected
        Whether the operation was expected to fail (an error message is then required).
    .PARAMETER OutcomeDialog
        Text of a dialog left on screen after the operation (an error or conflict dialog); it counts as the outcome channel.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Action, [string]$Atomic, $Trigger, [switch]$Expected, [string]$OutcomeDialog)
    $raw = if ($Trigger) { @($Trigger.Toasts) } else { @() }
    $toasts = @($raw | Where-Object { $_ -and $_ -notmatch '^Please wait' })
    $dialogs = @(if ($Trigger) { $Trigger.Dialogs })
    if ($OutcomeDialog) { $dialogs += $OutcomeDialog }
    $channel = if ($toasts.Count) { 'toast' } elseif ($OutcomeDialog) { 'outcome-dialog' } elseif ($dialogs.Count) { 'progress-only' } else { 'nothing' }
    $issues = @(foreach ($t in @($toasts) + @($dialogs)) { foreach ($i in (Test-DaMessageText -Text $t)) { "${i}: $t" } })
    $Ctx.Messages.Add([ordered]@{ action = $Action; atomic = $Atomic; channel = $channel; toasts = $toasts; dialogs = $dialogs; issues = $issues; correlationId = $Ctx.CorrelationId })
    return [pscustomobject]@{ Channel = $channel; Toasts = $toasts; Dialogs = $dialogs; Issues = $issues }
}

function Add-DaOpen {
    <#
    .SYNOPSIS
        Records an atomic scene that could not be automated reliably, with the concrete reason and the observed UI facts.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER Reason
        Why it is open.
    .PARAMETER Observed
        What the UI showed.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Atomic, [Parameter(Mandatory)][string]$Reason, [string]$Observed)
    $Ctx.Open.Add([ordered]@{ atomic = $Atomic; reason = $Reason; observed = $Observed; owner = 'S4166 phase 05/07 author'; nextStep = 'automate the named step or keep it for the owner pass' })
}

function Get-DaSceneVerdict {
    <#
    .SYNOPSIS
        Code of a finished scene: 2 invalid or nothing judged (no invariant, no finding), 1 finding or broken invariant, 0 clean. Pure.
    .PARAMETER Valid
        Run validity.
    .PARAMETER Invariants
        Invariant records.
    .PARAMETER Findings
        Finding records.
    #>
    param([bool]$Valid, [object[]]$Invariants = @(), [object[]]$Findings = @())
    if (-not $Valid) { return 2 }
    if (@($Invariants | Where-Object { -not $_.holds }).Count -or @($Findings).Count) { return 1 }
    if (-not @($Invariants).Count) { return 2 }
    return 0
}

function Complete-DaSceneRecord {
    <#
    .SYNOPSIS
        Closes a scene run: sampler stopped, log saved, validity judged, scene.json written. Returns {Code; Reason; RecordPath}.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER ConditionsBefore
        Get-DaDeviceConditions snapshot from the scene start.
    .PARAMETER AbortReason
        Why the scene body stopped early (an exception); makes the run invalid.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, $ConditionsBefore, [string]$AbortReason)
    Update-DaSceneSampler -Ctx $Ctx -Stop
    $kinds = [ordered]@{}
    foreach ($m in $Ctx.Messages) { foreach ($i in @($m.issues)) { $k = ($i -split ':', 2)[0]; if (-not $kinds.Contains($k)) { $kinds[$k] = [System.Collections.Generic.List[string]]::new() }; $kinds[$k].Add("$($m.action): $((($i -split ':', 2)[1]).Trim())") } }
    foreach ($k in $kinds.Keys) {
        $texts = @($kinds[$k] | Select-Object -Unique)
        Add-DaFinding -Ctx $Ctx -Title "outcome texts with $k ($($texts.Count) occurrence(s))" -Atomic $null -Severity 'P3' -Rung 'V' -Steps @('see messages[] of this record; each text is raw from the accessibility event stream') `
            -Expected 'plain-language texts naming the file, with correct number agreement' -Actual ($texts -join ' || ') -Evidence @('scene.json')
    }
    $log = Save-DaSceneLogcat -Serial $Ctx.Serial -Package $Ctx.Package -OutFile (Join-Path $Ctx.RunDir 'logcat.txt')
    foreach ($s in $log.Signals) {
        if ($s.kind -in @('crash', 'anr')) {
            Add-DaFinding -Ctx $Ctx -Title "$($s.kind) of $($Ctx.Package) during scene $($Ctx.Scene)" -Severity 'P0' -Rung 'V' -Steps @("scene $($Ctx.Scene) actions in scene.json") `
                -Expected 'no crash or ANR' -Actual $s.context -Evidence @($log.File)
        }
    }
    $condAfter = Get-DaDeviceConditions -Serial $Ctx.Serial
    $changes = if ($ConditionsBefore) { Compare-DaDeviceConditions -Before $ConditionsBefore -After $condAfter } else { [ordered]@{ powerChange = $null; frequencyChange = $null } }
    $calPtr = Read-DaJson -Path (Get-DaCalibrationPointerPath)
    $cal = if ($calPtr) { Read-DaJson -Path ([string]$calPtr.path) } else { $null }
    $gapMax = if ($cal) { [double]$cal.thresholds.samplerGapShareMax } else { [double](Get-DaDefaultThresholds).samplerGapShareMax }
    $ticks = [int](($Ctx.Samplers | ForEach-Object { $_.ticks } | Measure-Object -Sum).Sum)
    $gaps = [int](($Ctx.Samplers | ForEach-Object { $_.gapPeriods } | Measure-Object -Sum).Sum)
    $causes = [System.Collections.Generic.List[string]]::new()
    if (-not $cal) { $causes.Add('no calibration record') }
    if ($ticks -gt 0 -and ($gaps / $ticks) -gt $gapMax) { $causes.Add("sampler gaps $gaps of $ticks ticks above $gapMax") }
    if ($changes.powerChange) { $causes.Add("power change: $($changes.powerChange)") }
    if ($changes.frequencyChange) { $causes.Add("frequency change: $($changes.frequencyChange)") }
    if ($AbortReason) { $causes.Add("scene aborted: $AbortReason") }
    $valid = ($causes.Count -eq 0)
    $subject = Get-DaSubject -Serial $Ctx.Serial -Package $Ctx.Package
    $code = Get-DaSceneVerdict -Valid $valid -Invariants $Ctx.Invariants.ToArray() -Findings $Ctx.Findings.ToArray()
    $record = [ordered]@{
        schemaVersion      = 1
        kind               = 'scene'
        runId              = $Ctx.RunId
        scene              = $Ctx.Scene
        fault              = if ($Ctx.Fault) { $Ctx.Fault } else { $null }
        cyclePlan          = if ($Ctx.CyclePlan) { $Ctx.CyclePlan } else { $null }
        cycleFile          = if ($Ctx.CyclePlan) { 'cycles.jsonl' } else { $null }
        variant            = $Ctx.Variant
        correlationId      = $Ctx.CorrelationId
        build              = [ordered]@{ package = $subject.package; versionName = $subject.versionName; versionCode = $subject.versionCode; apkSha256 = $subject.apkSha256; hostApk = $subject.hostApk }
        device             = [ordered]@{ kind = 'emulator'; serial = $Ctx.Serial; avd = (Get-DaAvdName -Serial $Ctx.Serial) }
        valid              = $valid
        invalidReason      = if ($valid) { $null } else { $causes -join '; ' }
        startedAt          = $Ctx.StartedAt
        finishedAt         = (Get-Date).ToUniversalTime().ToString('o')
        durationMs         = (Get-DaMonotonicMs) - $Ctx.StartedMonoMs
        calibration        = if ($cal) { [ordered]@{ runId = $cal.runId; at = $cal.at } } else { $null }
        debugBuildOverhead = 'StrictMode, LeakCanary, the Timber UI tree and the crash handler are active in the debug subject'
        conditions         = [ordered]@{ before = $ConditionsBefore; after = $condAfter }
        fixture            = $Ctx.Fixture
        faultRecord        = $Ctx.FaultRecord
        actions            = $Ctx.Actions.ToArray()
        invariants         = $Ctx.Invariants.ToArray()
        messages           = $Ctx.Messages.ToArray()
        findings           = $Ctx.Findings.ToArray()
        open               = $Ctx.Open.ToArray()
        notApplicable      = $Ctx.NotApplicable.ToArray()
        samplers           = $Ctx.Samplers.ToArray()
        logcat             = [ordered]@{ file = $log.File; signals = $log.Signals }
        notes              = $Ctx.Notes.ToArray()
        result             = [ordered]@{ code = $code; word = @('ok', 'finding', 'invalid or nothing judged')[$code] }
    }
    $path = Join-Path $Ctx.RunDir 'scene.json'
    Write-DaJson -Path $path -InputObject $record -Durable
    $broken = @($Ctx.Invariants | Where-Object { -not $_.holds }).Count
    $summary = "scene $($Ctx.Scene)$(if ($Ctx.Fault) { "/$($Ctx.Fault)" }) $($Ctx.Variant) run $($Ctx.RunId): $(if ($valid) { 'valid' } else { "INVALID ($($causes -join '; '))" }), $($Ctx.Invariants.Count) invariants ($broken broken), $($Ctx.Findings.Count) candidate findings, $($Ctx.Open.Count) open; record $path"
    return [pscustomobject]@{ Code = $code; Reason = $summary; RecordPath = $path }
}

function Invoke-DaTreeCheck {
    <#
    .SYNOPSIS
        Re-hashes the run trees, judges the declared change against the previous tree and records the invariant; returns the new tree.
    .PARAMETER Ctx
        Scene context (Fixture and LastTree used).
    .PARAMETER Id
        Invariant id.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER Expect
        Hashtable of Test-DaTreeExpectation parameters (Added, Removed, Changed, SameAs, Optional, OptionalPattern).
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Id, [string]$Atomic, [hashtable]$Expect = @{})
    $after = Get-DaRunTree -Serial $Ctx.Serial -RunRemoteDir $Ctx.Fixture.runRemoteDir
    $j = Test-DaTreeExpectation -Before $Ctx.LastTree -After $after @Expect
    Add-DaInvariant -Ctx $Ctx -Id $Id -Atomic $Atomic -Holds $j.holds -Detail "Compare-DaTreeHash: $($j.diff)$(if ($j.problems.Count) { '; ' + ($j.problems -join '; ') })"
    $Ctx.LastTree = $after
    return $j
}

function Initialize-DaOpsScene {
    <#
    .SYNOPSIS
        Fixture publish, resource registration and the first tree for scenes that operate on files; returns $true when ready.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    if (-not $Ctx.Fixture) {
        $Ctx.Fixture = Invoke-DaSceneAction -Ctx $Ctx -Name 'publish-fixture' -Atomic $null -Body { Initialize-DaOpsFixture -Serial $Ctx.Serial -RunId $Ctx.RunId }
    }
    if (-not $Ctx.Fixture) { return $false }
    $reg = Invoke-DaSceneAction -Ctx $Ctx -Name 'register-resources' -Atomic $null -Body { Register-DaSafResources -Serial $Ctx.Serial -Package $Ctx.Package -RunId $Ctx.RunId }
    if (-not $reg -or -not $reg.Ok) {
        $Ctx.Notes.Add("resource registration failed: $(if ($reg) { $reg.Reason } else { 'error' })")
        return $false
    }
    $Ctx.Resources = @{ Src = $reg.Src; Dst = $reg.Dst }
    $Ctx.LastTree = Get-DaRunTree -Serial $Ctx.Serial -RunRemoteDir $Ctx.Fixture.runRemoteDir
    return $true
}

# ------------------------------------------------------------------------------------------------- C01

function Invoke-DaOnboardingWalk {
    <#
    .SYNOPSIS
        Walks the first-run wizard to the main screen: Next/Finish, Grant All, every system permission prompt allowed, every app dialog declined, the home-role request declined, foreign screens left with Back. Returns the recorded path.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER MaxSteps
        Upper bound of loop iterations.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [int]$MaxSteps = 45)
    $steps = [System.Collections.Generic.List[string]]::new()
    $prompts = [System.Collections.Generic.List[string]]::new()
    $alerts = [System.Collections.Generic.List[string]]::new()
    $foreign = [System.Collections.Generic.List[string]]::new()
    $entries = @()
    $grantAll = $false; $roleAsked = $false; $reached = $false; $unknown = 0
    for ($i = 0; $i -lt $MaxSteps; $i++) {
        $fg = Get-DaForeground -Serial $Serial -Package $Package
        if ($fg -match 'permissioncontroller') {
            $t = Get-DaUiTree -Serial $Serial
            if ($fg -match 'RequestRoleActivity') {
                $title = (@(Find-DaUiNode -Tree $t -Id 'title') | Select-Object -First 1).label
                $null = Invoke-DaUiTap -Serial $Serial -Id 'button2'
                $roleAsked = $true; $steps.Add("role request '$title' declined")
            }
            else {
                $msg = (@(Find-DaUiNode -Tree $t -Id 'permission_message') | Select-Object -First 1).label
                $answer = @('permission_allow_foreground_only_button', 'permission_allow_button', 'permission_allow_all_button') | Where-Object { @(Find-DaUiNode -Tree $t -Id $_).Count } | Select-Object -First 1
                if ($answer) { $null = Invoke-DaUiTap -Serial $Serial -Id $answer; $prompts.Add("$msg -> $answer") } else { $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK'; $prompts.Add("$msg -> back") }
            }
        }
        elseif ($fg -and $fg.StartsWith("$Package/")) {
            if ($fg -match 'ui\.main\.MainActivity') { $reached = $true; $steps.Add('main screen reached'); break }
            $t = Get-DaUiTree -Serial $Serial
            if (-not $t.Ok) { Start-Sleep -Milliseconds 1000; continue }
            $alert = @(Find-DaUiNode -Tree $t -Id 'alertTitle') | Select-Object -First 1
            if ($alert) {
                $m = (@(Find-DaUiNode -Tree $t -Id 'message') | Select-Object -First 1).label
                $alerts.Add("$($alert.label): $m")
                $null = Invoke-DaUiTap -Serial $Serial -Id 'button2'
                $steps.Add("dialog '$($alert.label)' declined")
            }
            elseif ($fg -match 'SettingsActivity') { $steps.Add("settings screen $fg opened by the wizard; back"); $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK' }
            elseif (-not $grantAll -and @(Find-DaUiNode -Tree $t -Id 'btnGrantAll').Count) {
                $titles = @(Get-DaUiLabels -Tree $t -Id 'tv_perm_entry_title')
                $states = @(Get-DaUiLabels -Tree $t -Id 'iv_perm_state')
                $entries = @(for ($k = 0; $k -lt $titles.Count; $k++) { "$($titles[$k]): $(if ($k -lt $states.Count) { $states[$k] })" })
                $null = Invoke-DaUiTap -Serial $Serial -Id 'btnGrantAll'
                $grantAll = $true; $steps.Add('permissions page: Grant All')
            }
            elseif (@(Find-DaUiNode -Tree $t -Id 'btnFinish').Count) { $null = Invoke-DaUiTap -Serial $Serial -Id 'btnFinish'; $steps.Add('Finish') }
            elseif (@(Find-DaUiNode -Tree $t -Id 'btnNext').Count) {
                $page = (@(Find-DaUiNode -Tree $t -Id 'tvTitle') + @(Find-DaUiNode -Tree $t -Id 'tvPermissionsTitle') | Select-Object -First 1).label
                $null = Invoke-DaUiTap -Serial $Serial -Id 'btnNext'; $steps.Add("Next on '$page'")
            }
            else { $unknown++; $steps.Add("unknown app screen $fg"); if ($unknown -gt 2) { $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK' } }
        }
        elseif ($fg) { $foreign.Add($fg); $steps.Add("left to $fg; back"); $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK' }
        Start-Sleep -Milliseconds 1300
    }
    return [ordered]@{ reachedMain = $reached; steps = $steps.ToArray(); permissionPrompts = $prompts.ToArray(); appDialogs = $alerts.ToArray()
        foreignScreens = $foreign.ToArray(); permissionEntriesBefore = $entries; grantAllUsed = $grantAll; homeRoleRequested = $roleAsked }
}

function Invoke-DaSceneC01 {
    <#
    .SYNOPSIS
        C01: first launch of an empty profile (app data cleared inside the envelope), onboarding and permission path, warm start, second cold start.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $s = $Ctx.Serial; $p = $Ctx.Package; $comp = Get-DaMainComponent -Package $p
    $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'clear-app-data' -Atomic 'C01.cold-start' -Body {
        $r = Invoke-DaAdb -Serial $s -Arguments @('shell', 'pm', 'clear', $p)
        $prefs = Invoke-DaAdb -Serial $s -Arguments @('shell', "run-as $p ls shared_prefs 2>/dev/null | wc -l")
        [ordered]@{ pmClear = (@($r.Lines) -join ' ').Trim(); sharedPrefsFiles = (@($prefs.Lines) -join '').Trim() }
    }
    $cold = Invoke-DaSceneAction -Ctx $Ctx -Name 'cold-start-empty-profile' -Atomic 'C01.cold-start' -Body { Measure-DaStart -Serial $s -Package $p -Component $comp -Mode cold -TimeoutMs 30000 }
    Add-DaInvariant -Ctx $Ctx -Id 'first-launch-shows-onboarding' -Atomic 'C01.onboarding-flow' -Holds ([string]$cold.activity -match 'welcome') -Detail "am start -W landed on $($cold.activity) ($($cold.launchState), TotalTime $($cold.totalTimeMs) ms; budget hypothesis 3000 ms, n=1)"
    Start-Sleep -Milliseconds 2500
    $null = Save-DaUiShot -Serial $s -OutDir $Ctx.RunDir
    $walk = Invoke-DaSceneAction -Ctx $Ctx -Name 'onboarding-walk' -Atomic 'C01.onboarding-flow' -Body { Invoke-DaOnboardingWalk -Serial $s -Package $p }
    Add-DaInvariant -Ctx $Ctx -Id 'onboarding-reaches-main' -Atomic 'C01.onboarding-flow' -Holds ([bool]$walk.reachedMain) -Detail "$(@($walk.steps).Count) steps: $(@($walk.steps) -join ' | ')"
    Add-DaInvariant -Ctx $Ctx -Id 'permission-prompts-answerable' -Atomic 'C01.permission-flow' -Holds (@($walk.permissionPrompts).Count -gt 0) -Detail "$(@($walk.permissionPrompts).Count) system prompts: $(@($walk.permissionPrompts) -join ' | ')"
    $Ctx.Messages.Add([ordered]@{ action = 'onboarding-walk'; atomic = 'C01.onboarding-flow'; channel = 'dialog'; toasts = @(); dialogs = @($walk.appDialogs); issues = @(foreach ($d in @($walk.appDialogs)) { foreach ($i in (Test-DaMessageText -Text $d)) { "${i}: $d" } }); correlationId = $Ctx.CorrelationId })
    Add-DaOpen -Ctx $Ctx -Atomic 'C01.permission-flow' -Reason 'only the granted path was driven (Grant All, every prompt allowed); the denied path needs its own run' -Observed "prompts: $(@($walk.permissionPrompts).Count); foreign screens opened: $(@($walk.foreignScreens) -join ', ')"
    if ($walk.homeRoleRequested) { $Ctx.Notes.Add('the wizard raised the system default-home request at Finish although the walk never turned on the home-screen option; declined') }
    $warm = Invoke-DaSceneAction -Ctx $Ctx -Name 'warm-start' -Atomic 'C01.warm-start' -Body { Measure-DaStart -Serial $s -Package $p -Component $comp -Mode warm }
    Add-DaInvariant -Ctx $Ctx -Id 'warm-start-lands-on-main' -Atomic 'C01.warm-start' -Holds ($warm.outcome -eq 'ok' -and [string]$warm.foreground -match 'MainActivity') -Detail "foreground $($warm.foreground), $($warm.launchState), TotalTime $($warm.totalTimeMs) ms (budget hypothesis 1000 ms, n=1)"
    $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'frames-after-first-launch' -Atomic 'C01.warm-start' -Body { Get-DaFrameStats -Serial $s -Package $p -Reset }
    $second = Invoke-DaSceneAction -Ctx $Ctx -Name 'second-cold-start' -Atomic 'C01.cold-start' -Body { Measure-DaStart -Serial $s -Package $p -Component $comp -Mode cold -TimeoutMs 30000 }
    Add-DaInvariant -Ctx $Ctx -Id 'onboarding-not-repeated' -Atomic 'C01.onboarding-flow' -Holds ($second.outcome -eq 'ok' -and [string]$second.activity -notmatch 'welcome') -Detail "second cold start landed on $($second.activity), TotalTime $($second.totalTimeMs) ms"
    $flag = Invoke-DaSceneAction -Ctx $Ctx -Name 'first-run-flag-persisted' -Atomic 'C01.onboarding-flow' -Body { (@((Invoke-DaAdb -Serial $s -Arguments @('shell', "run-as $p cat shared_prefs/welcome_prefs.xml")).Lines) -join ' ') }
    Add-DaInvariant -Ctx $Ctx -Id 'first-run-flag-persisted' -Atomic 'C01.onboarding-flow' -Holds ([string]$flag -match 'welcome_completed" value="true') -Detail 'shared_prefs/welcome_prefs.xml read through run-as'
}

# ------------------------------------------------------------------------------------------------- C02

function Wait-DaRemoteSize {
    <#
    .SYNOPSIS
        Waits until a device file reaches a size; returns the last size seen (-1 when absent).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Path
        Device file.
    .PARAMETER Size
        Target size in bytes.
    .PARAMETER TimeoutMs
        Limit.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Path, [long]$Size, [int]$TimeoutMs = 90000)
    $deadline = (Get-DaMonotonicMs) + $TimeoutMs
    $last = -1L
    do {
        $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "stat -c %s '$Path' 2>/dev/null || echo -1")
        $v = (@($r.Lines) -join '').Trim()
        if ($v -match '^-?\d+$') { $last = [long]$v }
        if ($last -eq $Size) { return $last }
        Start-Sleep -Milliseconds 1000
    } while ((Get-DaMonotonicMs) -lt $deadline)
    return $last
}

function Invoke-DaInterruptedCopy {
    <#
    .SYNOPSIS
        Copies one large source to dst and runs an interruption on the host while the copy runs; returns {Trigger; Message; SrcSize; DstSize}.
    .PARAMETER Ctx
        Scene context with fixture and resources.
    .PARAMETER Name
        File name in src (created when absent).
    .PARAMETER Interrupt
        Scriptblock run about one second after the destination tap; receives the tap time.
    .PARAMETER Action
        Action name for the message record.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER WatchSec
        Length of the outcome stream.
    .PARAMETER SizeMb
        Size of the source in MiB (a copy runs at roughly 100 MiB/s or more on the AVD).
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Name, [scriptblock]$Interrupt, [string]$Action, [string]$Atomic, [int]$WatchSec = 4, [int]$SizeMb = 1024)
    $null = Add-DaBigSource -Serial $Ctx.Serial -RunRemoteDir $Ctx.Fixture.runRemoteDir -Name $Name -SizeMb $SizeMb
    $Ctx.LastTree = Get-DaRunTree -Serial $Ctx.Serial -RunRemoteDir $Ctx.Fixture.runRemoteDir
    if (-not (Open-DaResource -Serial $Ctx.Serial -Package $Ctx.Package -Name $Ctx.Resources.Src)) { throw "resource $($Ctx.Resources.Src) did not open" }
    $sel = Select-DaBrowseFiles -Serial $Ctx.Serial -Names @($Name)
    if (-not $sel.Ok) { throw $sel.Reason }
    $tr = Invoke-DaBrowseTransfer -Serial $Ctx.Serial -Package $Ctx.Package -Kind Copy -Destination $Ctx.Resources.Dst -WatchSec $WatchSec -During $Interrupt
    $msg = Add-DaMessage -Ctx $Ctx -Action $Action -Atomic $Atomic -Trigger $tr.Trigger
    $srcSize = [long]$SizeMb * 1048576
    return [pscustomobject]@{ Transfer = $tr; Message = $msg; SrcSize = $srcSize; DstPath = "$($Ctx.Fixture.dst)/$Name" }
}

function Invoke-DaSceneC02 {
    <#
    .SYNOPSIS
        C02: orderly exit by Back and by the Exit button, exit cancellation, closing a screen, leaving during a copy, and force-stop during a copy as the abnormal case.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $ready = Initialize-DaOpsScene -Ctx $Ctx
    $back = Invoke-DaSceneAction -Ctx $Ctx -Name 'orderly-exit-back' -Atomic 'C02.orderly-exit' -Body {
        if (-not (Open-DaAppMain -Serial $s -Package $p)) { throw 'main screen not reached' }
        $before = Get-DaProcessIdentity -Serial $s -Package $p
        $t0 = Get-DaMonotonicMs
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        $leftMs = $null
        while ((Get-DaMonotonicMs) - $t0 -lt 5000) { if (-not ((Get-DaForeground -Serial $s -Package $p) -like "$p/*")) { $leftMs = (Get-DaMonotonicMs) - $t0; break }; Start-Sleep -Milliseconds 100 }
        Start-Sleep -Seconds 4
        $after = Get-DaProbeContext -Serial $s -Package $p
        [ordered]@{ leftForegroundMs = $leftMs; pidBefore = if ($before) { $before.pid } else { $null }; pidAfter4s = $after.pid; processStateAfter4s = $after.processState; foregroundAfter = $after.foreground }
    }
    Add-DaInvariant -Ctx $Ctx -Id 'back-leaves-main' -Atomic 'C02.orderly-exit' -Holds ($null -ne $back.leftForegroundMs) -Detail "left the foreground in $($back.leftForegroundMs) ms (budget hypothesis 2000 ms); 4 s later pid $($back.pidAfter4s) state $($back.processStateAfter4s)"
    $exit = Invoke-DaSceneAction -Ctx $Ctx -Name 'exit-button' -Atomic 'C02.orderly-exit' -Body {
        if (-not (Open-DaAppMain -Serial $s -Package $p)) { throw 'main screen not reached' }
        $script:daExitGone = $null
        $trig = Invoke-DaUiTriggerTap -Serial $s -Package $p -Id 'btnExit' -WatchSec 4 -During { param($at) $script:daExitGone = Wait-DaProcessGone -Serial $s -Package $p -TimeoutMs 15000 -FromMonoMs $at; "process gone check from the tap" }
        if (-not $trig.Ok) { throw "btnExit not tappable: $($trig.Reason)" }
        $gone = $script:daExitGone
        [ordered]@{ tap = 'btnExit node from a fresh tree, input tap'; processGoneMs = $gone; toasts = $trig.Toasts; note = 'measured from the input tap; includes the adb poll period of 200 ms' }
    }
    Add-DaInvariant -Ctx $Ctx -Id 'exit-button-ends-process' -Atomic 'C02.orderly-exit' -Holds ($null -ne $exit.processGoneMs) -Detail "process gone $($exit.processGoneMs) ms after the input tap (budget hypothesis 2000 ms)"
    $cancel = Invoke-DaSceneAction -Ctx $Ctx -Name 'exit-cancelled' -Atomic 'C02.exit-cancelled' -Body {
        if (-not (Open-DaAppMain -Serial $s -Package $p)) { throw 'main screen not reached' }
        $null = Invoke-DaUiTap -Serial $s -Id 'btnAddResource'
        Start-Sleep -Milliseconds 1500
        $sub = Get-DaForeground -Serial $s -Package $p
        $null = Invoke-DaUiKey -Serial $s -Key 'BACK'
        Start-Sleep -Milliseconds 1500
        [ordered]@{ subScreen = $sub; afterBack = Get-DaForeground -Serial $s -Package $p }
    }
    Add-DaInvariant -Ctx $Ctx -Id 'back-from-sub-screen-returns-to-main' -Atomic 'C02.exit-cancelled' -Holds ([string]$cancel.afterBack -match 'MainActivity') -Detail "from $($cancel.subScreen) Back led to $($cancel.afterBack)"
    Add-DaOpen -Ctx $Ctx -Atomic 'C02.exit-cancelled' -Reason 'the main screen asks for no confirmation: Back and Exit leave at once (orderly-exit-back, exit-button), so no cancel step exists to measure; whether one should exist is a U judgement' -Observed "Back left the foreground in $($back.leftForegroundMs) ms; Exit ended the process in $($exit.processGoneMs) ms"
    if (-not $ready) {
        foreach ($a in 'C02.close-screen', 'C02.force-stop') { Add-DaOpen -Ctx $Ctx -Atomic $a -Reason 'fixture resources could not be registered' -Observed (@($Ctx.Notes) -join '; ') }
        return
    }
    $close = Invoke-DaSceneAction -Ctx $Ctx -Name 'close-screen' -Atomic 'C02.close-screen' -Body {
        if (-not (Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src)) { throw 'src resource did not open' }
        $null = Invoke-DaUiKey -Serial $s -Key 'BACK'
        Start-Sleep -Milliseconds 1500
        Get-DaForeground -Serial $s -Package $p
    }
    Add-DaInvariant -Ctx $Ctx -Id 'closing-browse-returns-to-main' -Atomic 'C02.close-screen' -Holds ([string]$close -match 'MainActivity') -Detail "after Back from browse: $close"
    $leave = Invoke-DaSceneAction -Ctx $Ctx -Name 'leave-during-copy' -Atomic 'C02.orderly-exit' -Body {
        $r = Invoke-DaInterruptedCopy -Ctx $Ctx -Name 'big_leave.bin' -SizeMb 512 -Action 'leave-during-copy' -Atomic 'C02.orderly-exit' -Interrupt { param($at) Start-Sleep -Milliseconds 800; $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_HOME'); "HOME sent $((Get-DaMonotonicMs) - $at) ms after the tap" }
        $size = Wait-DaRemoteSize -Serial $s -Path $r.DstPath -Size $r.SrcSize -TimeoutMs 120000
        [ordered]@{ during = $r.Transfer.Trigger.DuringResult; dstSize = $size; srcSize = $r.SrcSize; channel = $r.Message.Channel; toasts = $r.Message.Toasts }
    }
    if ($leave) {
        $j = Invoke-DaTreeCheck -Ctx $Ctx -Id 'copy-continues-after-leaving' -Atomic 'C02.orderly-exit' -Expect @{ Added = @('dst/big_leave.bin'); SameAs = @{ 'dst/big_leave.bin' = 'src/big_leave.bin' } }
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', "rm -f '$($Ctx.Fixture.src)/big_leave.bin' '$($Ctx.Fixture.dst)/big_leave.bin'")
        $Ctx.LastTree = Get-DaRunTree -Serial $s -RunRemoteDir $Ctx.Fixture.runRemoteDir
        if (-not $j.holds) { Add-DaFinding -Ctx $Ctx -Title 'a copy started before HOME did not complete in the background' -Atomic 'C02.orderly-exit' -Severity 'P2' -Rung 'T' -Steps @('open src', 'select big_leave.bin (512 MiB)', 'Copy to dst', 'press HOME 0.8 s later', 'wait 120 s') -Expected 'dst/big_leave.bin byte-equal to the source' -Actual ($j.problems -join '; ') -Evidence @('scene.json', 'logcat.txt') }
    }
    $kill = Invoke-DaSceneAction -Ctx $Ctx -Name 'force-stop-during-copy' -Atomic 'C02.force-stop' -Body {
        $r = Invoke-DaInterruptedCopy -Ctx $Ctx -Name 'big_kill.bin' -Action 'force-stop-during-copy' -Atomic 'C02.force-stop' -Interrupt { param($at) Start-Sleep -Milliseconds 1000; $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'am', 'force-stop', $p); "force-stop $((Get-DaMonotonicMs) - $at) ms after the tap" }
        Start-Sleep -Seconds 3
        $sz = (@((Invoke-DaAdb -Serial $s -Arguments @('shell', "stat -c %s '$($r.DstPath)' 2>/dev/null || echo -1")).Lines) -join '').Trim()
        $relaunch = Measure-DaStart -Serial $s -Package $p -Component (Get-DaMainComponent -Package $p) -Mode cold -TimeoutMs 30000
        Start-Sleep -Seconds 2
        $t = Get-DaUiTree -Serial $s
        $dialog = (@(Find-DaUiNode -Tree $t -Id 'alertTitle') | Select-Object -First 1).label
        [ordered]@{ during = $r.Transfer.Trigger.DuringResult; dstSizeAfterKill = $sz; srcSize = $r.SrcSize; relaunchActivity = $relaunch.activity; relaunchTotalMs = $relaunch.totalTimeMs; dialogOnRelaunch = $dialog }
    }
    if ($kill) {
        $partial = ([long]$kill.dstSizeAfterKill -ge 0 -and [long]$kill.dstSizeAfterKill -ne [long]$kill.srcSize)
        $null = Invoke-DaTreeCheck -Ctx $Ctx -Id 'force-stop-touches-only-the-copied-name' -Atomic 'C02.force-stop' -Expect @{ Optional = @('dst/big_kill.bin') }
        Add-DaInvariant -Ctx $Ctx -Id 'abnormal-stop-leaves-no-partial-file' -Atomic 'C02.force-stop' -Holds (-not $partial) -Detail "dst/big_kill.bin size after the kill: $($kill.dstSizeAfterKill) of $($kill.srcSize); relaunch dialog: '$($kill.dialogOnRelaunch)'"
        if ($partial) {
            Add-DaFinding -Ctx $Ctx -Title 'force-stop during a copy leaves a truncated file under the final name in the destination' -Atomic 'C02.force-stop' -Severity 'P1' -Rung 'T' `
                -Steps @('open src (SAF local folder)', 'select big_kill.bin (1 GiB)', 'Copy to dst', 'am force-stop 1 s after the destination tap', 'stat dst/big_kill.bin') `
                -Expected 'no file, or a temporary name the user can tell apart' -Actual "dst/big_kill.bin is $($kill.dstSizeAfterKill) of $($kill.srcSize) bytes; relaunch showed '$($kill.dialogOnRelaunch)'" -Evidence @('scene.json', 'logcat.txt')
        }
    }
}

# ------------------------------------------------------------------------------------------------- C03

function Invoke-DaSleepWakeCycle {
    <#
    .SYNOPSIS
        One sleep and wake cycle with keyguard dismissal; returns timings, process identity and keyguard state.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER SleepKey
        Key that turns the screen off.
    .PARAMETER WakeKey
        Key that turns it on.
    .PARAMETER HoldSec
        Seconds asleep.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [string]$SleepKey = 'KEYCODE_SLEEP', [string]$WakeKey = 'KEYCODE_WAKEUP', [int]$HoldSec = 5)
    $before = Get-DaProcessIdentity -Serial $Serial -Package $Package
    $cpuA = if ($before) { (Get-DaDeviceCpuSnapshot -Serial $Serial -ProcessId $before.pid).procTicks } else { $null }
    $t0 = Get-DaMonotonicMs
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'keyevent', $SleepKey)
    $asleepMs = Wait-DaPowerState -Serial $Serial -Wakefulness 'Asleep' -FromMonoMs $t0 -TimeoutMs 10000
    Start-Sleep -Seconds $HoldSec
    $whileAsleep = Get-DaProbeContext -Serial $Serial -Package $Package
    $cpuB = if ($before) { (Get-DaDeviceCpuSnapshot -Serial $Serial -ProcessId $before.pid).procTicks } else { $null }
    $t1 = Get-DaMonotonicMs
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'keyevent', $WakeKey)
    $awakeMs = Wait-DaPowerState -Serial $Serial -Wakefulness 'Awake' -FromMonoMs $t1 -TimeoutMs 10000
    Start-Sleep -Milliseconds 800
    $kg = (Get-DaPowerState -Serial $Serial).keyguard
    if ($kg) { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'wm', 'dismiss-keyguard') }
    $focusMs = Wait-DaAppFocus -Serial $Serial -Package $Package -FromMonoMs $t1 -TimeoutMs 10000
    $after = Get-DaProcessIdentity -Serial $Serial -Package $Package
    $key = if ($focusMs) { Invoke-DaKeyProbe -Serial $Serial -Package $Package -KeyCode 20 -ReactionTimeoutMs 1000 } else { $null }
    return [ordered]@{
        sleepKey = $SleepKey; wakeKey = $WakeKey; asleepMs = $asleepMs; holdSec = $HoldSec; processStateAsleep = $whileAsleep.processState
        cpuTicksWhileAsleep = if ($null -ne $cpuA -and $null -ne $cpuB) { $cpuB - $cpuA } else { $null }
        awakeMs = $awakeMs; keyguardShown = $kg; appFocusMs = $focusMs
        samePid = ($before -and $after -and $before.pid -eq $after.pid -and $before.startTicks -eq $after.startTicks)
        pidBefore = if ($before) { $before.pid } else { $null }; pidAfter = if ($after) { $after.pid } else { $null }
        keyProbe = if ($key) { [ordered]@{ outcome = $key.outcome; hostReactionMs = $key.hostReactionMs; deviceInputToFrameMs = $key.deviceInputToFrameMs } } else { $null }
    }
}

function Invoke-DaSceneC03 {
    <#
    .SYNOPSIS
        C03: sleep and wake, power-key lock and unlock with the keyguard on, idle and with a copy running.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $ready = Initialize-DaOpsScene -Ctx $Ctx
    $kgDisabled = (@((Invoke-DaAdb -Serial $s -Arguments @('shell', 'locksettings', 'get-disabled')).Lines) -join '').Trim()
    if ($kgDisabled -eq 'true') {
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'locksettings', 'set-disabled', 'false')
        $Ctx.Cleanups.Add({ param($sceneCtx) $null = Invoke-DaAdb -Serial ([string]$sceneCtx.Serial) -Arguments @('shell', 'locksettings', 'set-disabled', 'true'); 'keyguard disabled again' })
    }
    $Ctx.Notes.Add("keyguard disabled at start: $kgDisabled (swipe keyguard enabled for the scene, restored in finally)")
    $onBrowse = if ($ready) { Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src } else { Open-DaAppMain -Serial $s -Package $p }
    if (-not $onBrowse) { $Ctx.Notes.Add('the idle screen could not be reached') }
    $idle = Invoke-DaSceneAction -Ctx $Ctx -Name 'idle-sleep-wake' -Atomic 'C03.sleep-wake' -Body { Invoke-DaSleepWakeCycle -Serial $s -Package $p }
    Add-DaInvariant -Ctx $Ctx -Id 'process-survives-idle-sleep' -Atomic 'C03.sleep-wake' -Holds ([bool]$idle.samePid) -Detail "pid $($idle.pidBefore) -> $($idle.pidAfter); asleep after $($idle.asleepMs) ms, awake after $($idle.awakeMs) ms"
    Add-DaInvariant -Ctx $Ctx -Id 'app-regains-focus-after-wake' -Atomic 'C03.sleep-wake' -Holds ($null -ne $idle.appFocusMs) -Detail "focus $($idle.appFocusMs) ms after the wake key; keyguard shown $($idle.keyguardShown); key probe $($idle.keyProbe.outcome)"
    Add-DaInvariant -Ctx $Ctx -Id 'idle-cpu-while-asleep-recorded' -Atomic 'C03.activity-while-idle' -Holds ($null -ne $idle.cpuTicksWhileAsleep) -Detail "app CPU ticks during $($idle.holdSec) s asleep: $($idle.cpuTicksWhileAsleep) (no budget; recorded for the growth phase)"
    $pwr = Invoke-DaSceneAction -Ctx $Ctx -Name 'power-key-lock-unlock' -Atomic 'C03.lock-unlock' -Body { Invoke-DaSleepWakeCycle -Serial $s -Package $p -SleepKey 'KEYCODE_POWER' -WakeKey 'KEYCODE_POWER' }
    Add-DaInvariant -Ctx $Ctx -Id 'process-survives-lock-unlock' -Atomic 'C03.lock-unlock' -Holds ([bool]$pwr.samePid) -Detail "pid $($pwr.pidBefore) -> $($pwr.pidAfter); keyguard shown $($pwr.keyguardShown)"
    Add-DaInvariant -Ctx $Ctx -Id 'app-in-front-after-unlock' -Atomic 'C03.lock-unlock' -Holds ($null -ne $pwr.appFocusMs) -Detail "focus $($pwr.appFocusMs) ms after the power key; key probe $($pwr.keyProbe.outcome)"
    $Ctx.NotApplicable.Add([ordered]@{ atomic = 'C03.ambient-wake-watch'; reason = 'watch ambient mode; the subject is the phone build on a phone AVD'; source = 'audit/scenes.json variants'; condition = 'device characteristics are not watch' })
    if (-not $ready) { Add-DaOpen -Ctx $Ctx -Atomic 'C03.sleep-wake' -Reason 'the active-operation variant needs the fixture resources, which could not be registered' -Observed (@($Ctx.Notes) -join '; '); return }
    $active = Invoke-DaSceneAction -Ctx $Ctx -Name 'sleep-during-copy' -Atomic 'C03.sleep-wake' -Body {
        $r = Invoke-DaInterruptedCopy -Ctx $Ctx -Name 'big_sleep.bin' -SizeMb 512 -Action 'sleep-during-copy' -Atomic 'C03.sleep-wake' -WatchSec 3 -Interrupt { param($at) Start-Sleep -Milliseconds 600; $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_SLEEP'); "SLEEP sent $((Get-DaMonotonicMs) - $at) ms after the tap" }
        Start-Sleep -Seconds 8
        $t1 = Get-DaMonotonicMs
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        $awake = Wait-DaPowerState -Serial $s -Wakefulness 'Awake' -FromMonoMs $t1
        Start-Sleep -Milliseconds 800
        if ((Get-DaPowerState -Serial $s).keyguard) { $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'wm', 'dismiss-keyguard') }
        $focus = Wait-DaAppFocus -Serial $s -Package $p -FromMonoMs $t1
        $size = Wait-DaRemoteSize -Serial $s -Path $r.DstPath -Size $r.SrcSize -TimeoutMs 120000
        [ordered]@{ during = $r.Transfer.Trigger.DuringResult; awakeMs = $awake; appFocusMs = $focus; dstSize = $size; srcSize = $r.SrcSize; channelBeforeSleep = $r.Message.Channel }
    }
    if ($active) {
        $j = Invoke-DaTreeCheck -Ctx $Ctx -Id 'copy-completes-across-sleep' -Atomic 'C03.sleep-wake' -Expect @{ Added = @('dst/big_sleep.bin'); SameAs = @{ 'dst/big_sleep.bin' = 'src/big_sleep.bin' } }
        if (-not $j.holds) { Add-DaFinding -Ctx $Ctx -Title 'a copy running when the screen went to sleep did not complete' -Atomic 'C03.sleep-wake' -Severity 'P1' -Rung 'T' -Steps @('open src', 'Copy big_sleep.bin (512 MiB) to dst', 'KEYCODE_SLEEP 0.6 s later', 'wake after 8 s', 'wait 120 s') -Expected 'dst/big_sleep.bin byte-equal to the source' -Actual ($j.problems -join '; ') -Evidence @('scene.json') }
        Add-DaInvariant -Ctx $Ctx -Id 'app-in-front-after-wake-with-copy' -Atomic 'C03.sleep-wake' -Holds ($null -ne $active.appFocusMs) -Detail "focus $($active.appFocusMs) ms after wake"
    }
}

# ------------------------------------------------------------------------------------------- C07 and C15

function Invoke-DaFileOp {
    <#
    .SYNOPSIS
        One file operation of the open browse list: select, act, capture the outcome, judge the trees. Returns {Ok; Reason; Message; Tree; Extra}.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Action
        Action name.
    .PARAMETER Atomic
        Atomic scene id.
    .PARAMETER Files
        File names to select.
    .PARAMETER Op
        Copy, Move, Rename, RenameKeep, RenameCancel, DeleteCancel or Delete.
    .PARAMETER NewName
        New name for Rename.
    .PARAMETER Expect
        Test-DaTreeExpectation parameters.
    .PARAMETER During
        Scriptblock run while the outcome stream is open.
    .PARAMETER BeforeConfirm
        Scriptblock run before the confirming tap.
    .PARAMETER ExpectFailure
        The operation is expected to fail visibly; a missing message becomes a candidate finding.
    .PARAMETER WatchSec
        Length of the outcome stream.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Action, [string]$Atomic, [Parameter(Mandatory)][string[]]$Files,
        [Parameter(Mandatory)][ValidateSet('Copy', 'Move', 'Rename', 'RenameKeep', 'RenameCancel', 'DeleteCancel', 'Delete')][string]$Op,
        [string]$NewName, [hashtable]$Expect = @{}, [scriptblock]$During, [scriptblock]$BeforeConfirm, [switch]$ExpectFailure, [int]$WatchSec = 8)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $extra = [ordered]@{}
    $sel = Select-DaBrowseFiles -Serial $s -Names $Files
    if (-not $sel.Ok) { Add-DaOpen -Ctx $Ctx -Atomic $Atomic -Reason "selection for $Action failed: $($sel.Reason)" -Observed ''; return [pscustomobject]@{ Ok = $false; Reason = $sel.Reason } }
    $trig = $null
    switch ($Op) {
        { $_ -in 'Copy', 'Move' } {
            $tr = Invoke-DaBrowseTransfer -Serial $s -Package $p -Kind $Op -Destination $Ctx.Resources.Dst -WatchSec $WatchSec -During $During -BeforeConfirm $BeforeConfirm
            if (-not $tr.Ok) { Add-DaOpen -Ctx $Ctx -Atomic $Atomic -Reason "$Action could not be driven: $($tr.Reason)" -Observed ''; return [pscustomobject]@{ Ok = $false; Reason = $tr.Reason } }
            $extra.dialog = $tr.Dialog; $extra.beforeConfirm = $tr.BeforeConfirm; $extra.during = $tr.Trigger.DuringResult
            $trig = $tr.Trigger
        }
        { $_ -like 'Rename*' } {
            $null = Invoke-DaUiTap -Serial $s -Id 'actionBrowseRename'
            $t = Wait-DaUiNode -Serial $s -Id 'etFileName' -TimeoutMs 8000
            if (-not $t) { Add-DaOpen -Ctx $Ctx -Atomic $Atomic -Reason 'rename dialog did not open' -Observed ''; return [pscustomobject]@{ Ok = $false; Reason = 'no rename dialog' } }
            $extra.prefill = (@(Find-DaUiNode -Tree $t -Id 'etFileName') | Select-Object -First 1).label
            $extra.dialogText = (@(Find-DaUiNode -Tree $t -Id 'tvFileCount') | Select-Object -First 1).label
            if ($Op -eq 'RenameCancel') { $null = Invoke-DaUiTap -Serial $s -Id 'btnCancel'; Start-Sleep -Milliseconds 1200 }
            else {
                if ($Op -eq 'Rename') {
                    $st = Set-DaUiText -Serial $s -Id 'etFileName' -Text $NewName
                    $extra.setText = if ($st.Ok) { 'read back' } else { $st.Reason }
                }
                $trig = Invoke-DaUiTriggerTap -Serial $s -Package $p -Id 'btnApply' -WatchSec $WatchSec
            }
        }
        { $_ -like 'Delete*' } {
            $null = Invoke-DaUiTap -Serial $s -Id 'actionBrowseDelete'
            $t = Wait-DaUiNode -Serial $s -Id 'button1' -TimeoutMs 8000
            if (-not $t) { Add-DaOpen -Ctx $Ctx -Atomic $Atomic -Reason 'delete confirmation did not open' -Observed ''; return [pscustomobject]@{ Ok = $false; Reason = 'no delete dialog' } }
            $extra.confirmTitle = (@(Find-DaUiNode -Tree $t -Id 'alertTitle') | Select-Object -First 1).label
            $extra.confirmMessage = (@(Find-DaUiNode -Tree $t -Id 'message') | Select-Object -First 1).label
            if ($Op -eq 'DeleteCancel') { $null = Invoke-DaUiTap -Serial $s -Id 'button2'; Start-Sleep -Milliseconds 1200 }
            else { $trig = Invoke-DaUiTriggerTap -Serial $s -Package $p -Id 'button1' -WatchSec $WatchSec }
        }
    }
    Start-Sleep -Milliseconds 1200
    $after = Get-DaUiTree -Serial $s
    if ($after.Ok) {
        $extra.emptyState = (@(Find-DaUiNode -Tree $after -Id 'tvEmptyStateMessage') | Select-Object -First 1).label
        $extra.resourceInfo = (@(Find-DaUiNode -Tree $after -Id 'tvResourceInfo') | Select-Object -First 1).label
    }
    $pending = if ($after.Ok) { Resolve-DaPendingDialog -Serial $s -Prefer 'Skip|Cancel' -Tree $after } else { Resolve-DaPendingDialog -Serial $s -Prefer 'Skip|Cancel' }
    if ($pending) { $extra.pendingDialog = $pending }
    $outcomeText = if ($pending) { (@($pending.Title) + @($pending.Texts | Where-Object { $_ -ne $pending.Title })) -join ' / ' } else { $null }
    $msg = Add-DaMessage -Ctx $Ctx -Action $Action -Atomic $Atomic -Trigger $trig -Expected:$ExpectFailure -OutcomeDialog $outcomeText
    $j = Invoke-DaTreeCheck -Ctx $Ctx -Id $Action -Atomic $Atomic -Expect $Expect
    if ($ExpectFailure -and -not @($msg.Toasts).Count -and -not $pending) {
        Add-DaFinding -Ctx $Ctx -Title "$Action failed or was refused without any message" -Atomic $Atomic -Severity 'P2' -Rung 'V' -Steps @("select $($Files -join ', ')", "$Op", 'watch accessibility events for 8 s') -Expected 'a readable message' -Actual "no toast or outcome dialog; only the progress windows: $(@($msg.Dialogs) -join ' || ')" -Evidence @('scene.json')
    }

    return [pscustomobject]@{ Ok = $true; Reason = $null; Message = $msg; Tree = $j; Extra = $extra }
}

function Invoke-DaSceneC07 {
    <#
    .SYNOPSIS
        C07: copy, repeated copy, move, rename (edited, unedited, cancelled), delete (cancelled, confirmed), undo, trash and restore, and a cancelled copy, on the published small set.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $s = $Ctx.Serial; $p = $Ctx.Package
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) {
        foreach ($a in 'C07.copy', 'C07.move', 'C07.rename', 'C07.delete', 'C07.cancel-operation', 'C07.undo', 'C07.trash-restore') { Add-DaOpen -Ctx $Ctx -Atomic $a -Reason 'fixture resources could not be registered' -Observed (@($Ctx.Notes) -join '; ') }
        return
    }
    $open = { if (-not (Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src)) { throw "resource $($Ctx.Resources.Src) did not open" } }
    & $open
    $r = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-one' -Atomic 'C07.copy' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'copy-one' -Atomic 'C07.copy' -Files @('IMG_0001.jpg') -Op Copy -Expect @{ Added = @('dst/IMG_0001.jpg'); SameAs = @{ 'dst/IMG_0001.jpg' = 'src/IMG_0001.jpg' } } }
    $r = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-repeat' -Atomic 'C07.copy' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'copy-repeat' -Atomic 'C07.copy' -Files @('IMG_0001.jpg') -Op Copy -Expect @{ OptionalPattern = '^dst/IMG_0001' } }
    if ($r -and $r.Ok) {
        $dups = @($Ctx.LastTree.Entries | Where-Object { $_.path -like 'dst/IMG_0001*' -and $_.path -ne 'dst/IMG_0001.jpg' })
        $asked = [bool]$r.Extra.pendingDialog
        Add-DaInvariant -Ctx $Ctx -Id 'repeat-copy-keeps-one-copy-or-asks' -Atomic 'C07.copy' -Holds ($dups.Count -eq 0 -or $asked) -Detail "second names in dst: $(@($dups.path) -join ', '); conflict dialog: $(if ($asked) { $r.Extra.pendingDialog.Title } else { 'none' }); message: $(@($r.Message.Toasts) -join ' | ')"
        if (-not $dups.Count -and -not $asked -and $r.Message.Channel -ne 'toast') { Add-DaFinding -Ctx $Ctx -Title 'copying a file onto an existing name in the destination ends silently: nothing is copied and no message says so' -Atomic 'C07.copy' -Severity 'P3' -Rung 'T' -Steps @('copy IMG_0001.jpg to dst', 'copy IMG_0001.jpg to dst again', 'watch accessibility events for 8 s') -Expected 'a conflict question or a message' -Actual 'dst unchanged, no toast, no dialog after the progress window' -Evidence @('scene.json') }
        if ($dups.Count -and -not $asked) { Add-DaFinding -Ctx $Ctx -Title 'copying a file that already exists in the destination silently creates a second copy' -Atomic 'C07.copy' -Severity 'P3' -Rung 'T' -Steps @('copy IMG_0001.jpg to dst', 'copy IMG_0001.jpg to dst again') -Expected 'a conflict question, or one copy' -Actual "new: $(@($dups.path) -join ', ')" -Evidence @('scene.json') }
    }
    $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'move-one' -Atomic 'C07.move' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'move-one' -Atomic 'C07.move' -Files @('IMG_0002.jpg') -Op Move -Expect @{ Added = @('dst/IMG_0002.jpg'); Removed = @('src/IMG_0002.jpg'); SameAs = @{ 'dst/IMG_0002.jpg' = 'src/IMG_0002.jpg' } } }
    $ren = Invoke-DaSceneAction -Ctx $Ctx -Name 'rename-one' -Atomic 'C07.rename' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'rename-one' -Atomic 'C07.rename' -Files @('IMG_0003.jpg') -Op Rename -NewName 'IMG_0003_renamed.jpg' -Expect @{ Added = @('src/IMG_0003_renamed.jpg'); Removed = @('src/IMG_0003.jpg'); SameAs = @{ 'src/IMG_0003_renamed.jpg' = 'src/IMG_0003.jpg' } } }
    if ($ren -and $ren.Ok -and [string]$ren.Extra.prefill -ne 'IMG_0003.jpg') {
        $shot = Save-DaUiShot -Serial $s -OutDir $Ctx.RunDir
        Add-DaFinding -Ctx $Ctx -Title 'the rename dialog of a folder added through the system picker prefills the encoded storage document id instead of the file name' -Atomic 'C07.rename' -Severity 'P2' -Rung 'T' `
            -Steps @('add a local folder through Add > Local Folder > Add Manually (system picker)', 'open it, select IMG_0003.jpg', 'tap Rename') -Expected "the name field holds 'IMG_0003.jpg'" -Actual "the name field holds '$($ren.Extra.prefill)'" -Evidence @('scene.json', [string]$shot)
    }
    $keep = Invoke-DaSceneAction -Ctx $Ctx -Name 'rename-unedited-apply' -Atomic 'C07.rename' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'rename-unedited-apply' -Atomic 'C07.rename' -Files @('IMG_0004.jpg') -Op RenameKeep -Expect @{ OptionalPattern = '^src/(IMG_0004|primary)' } }
    if ($keep -and $keep.Ok) {
        $odd = @($Ctx.LastTree.Entries | Where-Object { $_.path -match '%|primary' })
        if ($odd.Count) { Add-DaFinding -Ctx $Ctx -Title 'applying the prefilled rename value writes the encoded document id as the file name' -Atomic 'C07.rename' -Severity 'P2' -Rung 'T' -Steps @('select IMG_0004.jpg', 'Rename', 'Apply without editing') -Expected 'no change' -Actual "new name(s): $(@($odd.path) -join ', ')" -Evidence @('scene.json') }
        elseif ($keep.Message.Channel -eq 'nothing') { $Ctx.Notes.Add("rename-unedited-apply: the dialog closed with no message and no change (prefill '$($keep.Extra.prefill)')") }
    }
    $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'rename-cancel' -Atomic 'C07.cancel-operation' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'rename-cancel' -Atomic 'C07.cancel-operation' -Files @('IMG_0005.jpg') -Op RenameCancel }
    $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'delete-cancel' -Atomic 'C07.cancel-operation' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'delete-cancel' -Atomic 'C07.cancel-operation' -Files @('IMG_0006.jpg') -Op DeleteCancel }
    $del = Invoke-DaSceneAction -Ctx $Ctx -Name 'delete-confirm' -Atomic 'C07.delete' -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'delete-confirm' -Atomic 'C07.delete' -Files @('IMG_0006.jpg') -Op Delete -Expect @{ Optional = @('src/IMG_0006.jpg'); OptionalPattern = '^src/\.trash/' } }
    if ($del -and $del.Ok) {
        $still = @($Ctx.LastTree.Entries | Where-Object { $_.path -eq 'src/IMG_0006.jpg' }).Count -gt 0
        $text = @($del.Message.Toasts) -join ' | '
        $saysFail = $text -match 'Could not|failed|error'
        Add-DaInvariant -Ctx $Ctx -Id 'delete-outcome-matches-message' -Atomic 'C07.delete' -Holds (($still -and $saysFail) -or (-not $still -and -not $saysFail)) -Detail "file still present: $still; message: '$text'; confirm text: '$($del.Extra.confirmMessage)'"
        if ($still) {
            Add-DaFinding -Ctx $Ctx -Title 'delete of a file in a folder added through the system picker fails' -Atomic 'C07.delete' -Severity 'P2' -Rung 'T' `
                -Steps @('add a local folder through the system picker', 'open it, select IMG_0006.jpg', 'Delete', 'confirm Delete') -Expected 'the file is deleted or moved to trash' -Actual "file unchanged; message '$text'" -Evidence @('scene.json', 'logcat.txt')
        }
        if ($del.Extra.emptyState) {
            $shot = Save-DaUiShot -Serial $s -OutDir $Ctx.RunDir
            Add-DaFinding -Ctx $Ctx -Title 'after the delete the browse screen shows an empty-folder state for a folder that still holds files' -Atomic 'C07.delete' -Severity 'P2' -Rung 'V' `
                -Steps @('select IMG_0006.jpg in the picker-added folder', 'Delete, confirm', 'read the screen 1.2 s later') -Expected 'the file list' -Actual "'$($del.Extra.emptyState)' with header '$($del.Extra.resourceInfo)'" -Evidence @('scene.json', [string]$shot)
        }
    }
    $undo = Invoke-DaSceneAction -Ctx $Ctx -Name 'undo-offered' -Atomic 'C07.undo' -Body {
        & $open
        $sel = Select-DaBrowseFiles -Serial $s -Names @('data.csv')
        $t = Get-DaUiTree -Serial $s
        $ops = @(@($t.Nodes) | Where-Object { [string]$_.resIdShort -like 'actionBrowse*' } | ForEach-Object { [string]$_.resIdShort })
        $null = Invoke-DaUiTap -Serial $s -Id 'btnDeselectAll'
        [ordered]@{ selected = $sel.Ok; operationBar = $ops; undoVisible = ($ops -contains 'actionBrowseUndo') }
    }
    if ($undo -and $undo.undoVisible) { $Ctx.Notes.Add('an Undo action is offered; driving it is left to the next phase') ; Add-DaOpen -Ctx $Ctx -Atomic 'C07.undo' -Reason 'Undo is offered but its target operation is not identified by the stand yet' -Observed (@($undo.operationBar) -join ', ') }
    else { Add-DaOpen -Ctx $Ctx -Atomic 'C07.undo' -Reason 'no Undo action is offered after the copy, move, rename and delete above on a picker-added local folder, although trash is on' -Observed "operation bar: $(@($undo.operationBar) -join ', ')" }
    $trash = (@((Invoke-DaAdb -Serial $s -Arguments @('shell', "ls -a '$($Ctx.Fixture.src)'")).Lines) -join ' ')
    if ($trash -match '\.trash') { Add-DaOpen -Ctx $Ctx -Atomic 'C07.trash-restore' -Reason 'a .trash folder exists; restoring from it through the UI is not automated yet' -Observed $trash }
    else { Add-DaOpen -Ctx $Ctx -Atomic 'C07.trash-restore' -Reason 'no .trash folder was created by the delete, so there is nothing to restore' -Observed "src listing has no .trash; delete message: $(@($del.Message.Toasts) -join ' | ')" }
    # The progress dialog cannot be read while the copy runs (a 1.5 GiB copy finished before a tree read
    # reached its Cancel, runs 261011-231600-c5b2 and 261012-001832-fcbc), so the cancel is the Back key
    # sent while the event stream records what follows.
    $cc = Invoke-DaSceneAction -Ctx $Ctx -Name 'cancel-copy-back-key' -Atomic 'C07.cancel-operation' -Body {
        $null = Add-DaBigSource -Serial $s -RunRemoteDir $Ctx.Fixture.runRemoteDir -Name 'big_cancel.bin' -SizeMb 1536
        $Ctx.LastTree = Get-DaRunTree -Serial $s -RunRemoteDir $Ctx.Fixture.runRemoteDir
        & $open
        $sel = Select-DaBrowseFiles -Serial $s -Names @('big_cancel.bin')
        if (-not $sel.Ok) { throw $sel.Reason }
        $tr = Invoke-DaBrowseTransfer -Serial $s -Package $p -Kind Copy -Destination $Ctx.Resources.Dst -WatchSec 7 -During { param($at) Start-Sleep -Milliseconds 1500; $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_BACK'); "Back sent $((Get-DaMonotonicMs) - $at) ms after the tap" }
        $dst = "$($Ctx.Fixture.dst)/big_cancel.bin"
        $read = { (@((Invoke-DaAdb -Serial $s -Arguments @('shell', "stat -c %s '$dst' 2>/dev/null || echo -1")).Lines) -join '').Trim() }
        Start-Sleep -Seconds 2
        $size1 = & $read
        Start-Sleep -Seconds 20
        $size2 = & $read
        [ordered]@{ back = $tr.Trigger.DuringResult; toasts = $tr.Trigger.Toasts; dialogs = $tr.Trigger.Dialogs; dstSize2s = $size1; dstSize22s = $size2; srcSize = 1536L * 1048576; foreground = Get-DaForeground -Serial $s -Package $p }
    }
    if ($cc) {
        $Ctx.Messages.Add([ordered]@{ action = 'cancel-copy-back-key'; atomic = 'C07.cancel-operation'; channel = if (@($cc.toasts).Count) { 'toast' } else { 'nothing' }; toasts = @($cc.toasts); dialogs = @($cc.dialogs); issues = @(foreach ($d in @($cc.dialogs) + @($cc.toasts)) { foreach ($i in (Test-DaMessageText -Text $d)) { "${i}: $d" } }); correlationId = $Ctx.CorrelationId })
        $null = Invoke-DaTreeCheck -Ctx $Ctx -Id 'cancel-copy-touches-only-the-copied-name' -Atomic 'C07.cancel-operation' -Expect @{ Optional = @('dst/big_cancel.bin') }
        $completed = ([long]$cc.dstSize22s -eq [long]$cc.srcSize)
        if ($completed) {
            Add-DaOpen -Ctx $Ctx -Atomic 'C07.cancel-operation' -Reason 'Back on the copy progress dialog did not cancel the copy (it completed); the dialog Cancel button cannot be read while the copy runs' -Observed "$($cc.back); dst 2 s later $($cc.dstSize2s), 22 s later $($cc.dstSize22s) of $($cc.srcSize); toasts $(@($cc.toasts) -join ' | '); foreground $($cc.foreground)"
        }
        else {
            $partial = ([long]$cc.dstSize22s -ge 0)
            Add-DaInvariant -Ctx $Ctx -Id 'cancelled-copy-leaves-no-partial-file' -Atomic 'C07.cancel-operation' -Holds (-not $partial) -Detail "$($cc.back); dst/big_cancel.bin 2 s later $($cc.dstSize2s), 22 s later $($cc.dstSize22s) of $($cc.srcSize); toasts $(@($cc.toasts) -join ' | ')"
            if ($partial) { Add-DaFinding -Ctx $Ctx -Title 'a copy cancelled with Back leaves a truncated file under the final name in the destination' -Atomic 'C07.cancel-operation' -Severity 'P1' -Rung 'T' -Steps @('select big_cancel.bin (1.5 GiB)', 'Copy to dst', 'press Back 1.5 s after the destination tap') -Expected 'no partial file, and a cancellation message' -Actual "dst/big_cancel.bin is $($cc.dstSize22s) bytes 22 s later; toasts '$(@($cc.toasts) -join ' | ')'" -Evidence @('scene.json') }
        }
    }
}

function Invoke-DaSceneC15 {
    <#
    .SYNOPSIS
        C15: the C07 operations with exactly one injected fault from audit/faults.json.
    .PARAMETER Ctx
        Scene context; Ctx.Fault names the fault.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $s = $Ctx.Serial; $p = $Ctx.Package; $f = $Ctx.Fault
    if (-not $f) { throw 'scene C15 needs -Fault <id> from audit/faults.json' }
    $cat = Get-DaFaultCatalog
    $entry = @($cat.faults | Where-Object { $_.id -eq $f }) | Select-Object -First 1
    if (-not $entry) { throw "fault $f is not in the catalogue" }
    if ($entry.status -ne 'injectable') { Add-DaOpen -Ctx $Ctx -Atomic $entry.atomic -Reason "fault $f is open: $($entry.reason)" -Observed ''; return }
    $atomic = [string]$entry.atomic
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) { Add-DaOpen -Ctx $Ctx -Atomic $atomic -Reason 'fixture resources could not be registered' -Observed (@($Ctx.Notes) -join '; '); return }
    $run = $Ctx.Fixture.runRemoteDir
    $adb = { param($a) Invoke-DaAdb -Serial $s -Arguments $a }.GetNewClosure()
    $inject = { param([hashtable]$extra) $rec = Invoke-DaFault -Id $f -Catalog $cat -Package $p -RunRemoteDir $run -CorrelationId $Ctx.CorrelationId -Adb $adb @extra; $Ctx.FaultRecord = $rec; $rec }
    $Ctx.Cleanups.Add({ param($sceneCtx) if ($sceneCtx.FaultRecord) { $clearSerial = [string]$sceneCtx.Serial; Clear-DaFault -Record $sceneCtx.FaultRecord -Adb ({ param($a) Invoke-DaAdb -Serial $clearSerial -Arguments $a }.GetNewClosure()) } else { 'no fault record' } })
    $open = { if (-not (Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src)) { throw "resource $($Ctx.Resources.Src) did not open" } }
    switch ($f) {
        'F01' {
            & $open
            $srcBefore = @($Ctx.LastTree.Entries | Where-Object { $_.path -eq 'src/IMG_0007.jpg' }) | Select-Object -First 1
            $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-source-removed-before-confirm' -Atomic $atomic -Body {
                Invoke-DaFileOp -Ctx $Ctx -Action 'copy-source-removed-before-confirm' -Atomic $atomic -Files @('IMG_0007.jpg') -Op Copy -ExpectFailure -Expect @{ Removed = @('src/IMG_0007.jpg') } `
                    -BeforeConfirm { & $inject @{ Target = 'src/IMG_0007.jpg'; OperationStartMonoMs = (Get-DaMonotonicMs) } }
            }
            $ghost = @($Ctx.LastTree.Entries | Where-Object { $_.path -eq 'dst/IMG_0007.jpg' }) | Select-Object -First 1
            if ($ghost) {
                $same = ($srcBefore -and $ghost.sha256 -eq $srcBefore.sha256)
                $toastText = (@($Ctx.Messages[-1].toasts) + @($Ctx.Messages[-1].dialogs | Where-Object { $_ -match 'Could not|Error' })) -join ' | '
                Add-DaFinding -Ctx $Ctx -Title "a file deleted after selection still leaves a file in the destination$(if ($same) { ' with its old content' } elseif ([long]$ghost.size -eq 0) { ' (zero bytes)' } else { ' with different content' })$(if (-not $toastText) { ', and no message says anything' } else { ' while the message reports a failure' })" -Atomic $atomic -Severity $(if ($same) { 'P2' } else { 'P1' }) -Rung 'T' `
                    -Steps @('open src, select IMG_0007.jpg', 'tap Copy; the destination dialog opens', 'rm src/IMG_0007.jpg through adb', 'tap the dst destination') -Expected 'the copy reports the missing source; dst unchanged' `
                    -Actual "dst/IMG_0007.jpg exists, $($ghost.size) bytes, $(if ($same) { 'byte-equal to the deleted source' } else { "sha256 $($ghost.sha256) differs from the source" }); message: '$($toastText.Substring(0, [math]::Min(400, $toastText.Length)))'" -Evidence @('scene.json', 'logcat.txt')
            }
        }
        'F02' {
            $null = Add-DaBigSource -Serial $s -RunRemoteDir $run -Name 'big_f02.bin' -SizeMb 1024
            $Ctx.LastTree = Get-DaRunTree -Serial $s -RunRemoteDir $run
            & $open
            $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-source-removed-during-copy' -Atomic $atomic -Body {
                Invoke-DaFileOp -Ctx $Ctx -Action 'copy-source-removed-during-copy' -Atomic $atomic -Files @('big_f02.bin') -Op Copy -ExpectFailure -WatchSec 10 -Expect @{ Removed = @('src/big_f02.bin'); Optional = @('dst/big_f02.bin') } `
                    -During { param($at) Start-Sleep -Milliseconds 1000; & $inject @{ Target = 'src/big_f02.bin'; OperationStartMonoMs = $at } | Out-Null; 'source removed' }
            }
            $sz = (@((Invoke-DaAdb -Serial $s -Arguments @('shell', "stat -c %s '$($Ctx.Fixture.dst)/big_f02.bin' 2>/dev/null || echo -1")).Lines) -join '').Trim()
            Add-DaInvariant -Ctx $Ctx -Id 'no-truncated-copy-after-source-loss' -Atomic $atomic -Holds ([long]$sz -lt 0 -or [long]$sz -eq 1024L * 1048576) -Detail "dst/big_f02.bin size $sz"
            if ([long]$sz -ge 0 -and [long]$sz -ne 1024L * 1048576) { Add-DaFinding -Ctx $Ctx -Title 'source removed during a copy leaves a truncated destination file under the final name' -Atomic $atomic -Severity 'P1' -Rung 'T' -Steps @('Copy big_f02.bin (1 GiB) to dst', 'rm the source 1 s after the destination tap') -Expected 'no partial file' -Actual "dst/big_f02.bin is $sz bytes" -Evidence @('scene.json') }
        }
        { $_ -in 'F03', 'F04' } {
            $damagedName = if ($f -eq 'F03') { 'corrupt_injected.jpg' } else { 'zero_bytes.jpg' }
            $extra = if ($f -eq 'F03') { @{ Target = "src/$damagedName"; Source = 'src/IMG_0008.jpg' } } else { @{ Target = "src/$damagedName" } }
            $null = & $inject $extra
            $Ctx.LastTree = Get-DaRunTree -Serial $s -RunRemoteDir $run
            & $open
            $view = Invoke-DaSceneAction -Ctx $Ctx -Name 'open-damaged-media' -Atomic $atomic -Body {
                if (-not (Find-DaUiNodeScrolling -Serial $s -Id 'tvFileName' -Label $damagedName -Exact -ContainerId 'rvMediaFiles')) { throw "$damagedName not listed" }
                $trig = Invoke-DaUiTriggerTap -Serial $s -Package $p -Id 'tvFileName' -Label $damagedName -Exact -WatchSec 6
                $fg = Get-DaForeground -Serial $s -Package $p
                $shot = Save-DaUiShot -Serial $s -OutDir $Ctx.RunDir
                $t = Get-DaUiTree -Serial $s
                $texts = if ($t.Ok) { @($t.Nodes | Where-Object { $_.labelled -and $_.class -match 'TextView' } | ForEach-Object { [string]$_.label } | Select-Object -First 10) } else { @("tree refused: $($t.Reason)") }
                $alive = [bool](Get-DaProcessIdentity -Serial $s -Package $p)
                $null = Invoke-DaUiKey -Serial $s -Key 'BACK'
                Start-Sleep -Milliseconds 1200
                [ordered]@{ foreground = $fg; screenTexts = $texts; toasts = $trig.Toasts; shot = $shot; processAlive = $alive }
            }
            if ($view) {
                Add-DaInvariant -Ctx $Ctx -Id 'damaged-media-open-survives' -Atomic $atomic -Holds ([bool]$view.processAlive) -Detail "viewer $($view.foreground); toasts $(@($view.toasts) -join ' | '); texts $(@($view.screenTexts) -join ' | ')"
                $Ctx.Messages.Add([ordered]@{ action = 'open-damaged-media'; atomic = $atomic; channel = if (@($view.toasts).Count) { 'toast' } else { 'screen' }; toasts = @($view.toasts); dialogs = @($view.screenTexts); issues = @(); correlationId = $Ctx.CorrelationId })
            }
            & $open
            $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-damaged-media' -Atomic $atomic -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'copy-damaged-media' -Atomic $atomic -Files @($damagedName) -Op Copy -Expect @{ Optional = @("dst/$damagedName"); SameAs = @{ "dst/$damagedName" = "src/$damagedName" } } }
        }
        'F05' {
            & $open
            $copy = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-all-into-full-storage' -Atomic $atomic -Body {
                $null = & $inject @{ MarginBytes = 16384; OperationStartMonoMs = (Get-DaMonotonicMs) }
                $null = Invoke-DaUiTap -Serial $s -Id 'btnSelectAll'
                Start-Sleep -Milliseconds 800
                $tr = Invoke-DaBrowseTransfer -Serial $s -Package $p -Kind Copy -Destination $Ctx.Resources.Dst -WatchSec 15
                Start-Sleep -Seconds 3
                $pending = Resolve-DaPendingDialog -Serial $s
                $msg = Add-DaMessage -Ctx $Ctx -Action 'copy-all-into-full-storage' -Atomic $atomic -Trigger $tr.Trigger -Expected
                [ordered]@{ dialog = $tr.Dialog; channel = $msg.Channel; toasts = $msg.Toasts; pending = $pending; fault = $Ctx.FaultRecord.detail }
            }
            if (-not $copy) { Add-DaOpen -Ctx $Ctx -Atomic $atomic -Reason 'the full-storage fault or the copy could not be driven' -Observed (@($Ctx.Notes) -join '; '); return }
            $after = Get-DaRunTree -Serial $s -RunRemoteDir $run
            # The browse list flattens subfolders (src/nested/deeper/IMG_nested.jpg is copied as dst/IMG_nested.jpg),
            # so a destination file is matched to any source file with the same leaf name.
            $srcByLeaf = @{}
            foreach ($e in $Ctx.LastTree.Entries) { if ($e.path -like 'src/*') { $leaf = ($e.path -split '/')[-1]; if (-not $srcByLeaf.ContainsKey($leaf)) { $srcByLeaf[$leaf] = [System.Collections.Generic.List[string]]::new() }; $srcByLeaf[$leaf].Add([string]$e.sha256) } }
            $bad = @(foreach ($e in $after.Entries) { if ($e.path -like 'dst/*') { $leaf = ($e.path -split '/')[-1]; if (-not $srcByLeaf.ContainsKey($leaf) -or -not $srcByLeaf[$leaf].Contains([string]$e.sha256)) { "$($e.path) ($($e.size) bytes)" } } })
            $copied = @($after.Entries | Where-Object { $_.path -like 'dst/*' }).Count
            $dstLeaves = @($after.Entries | Where-Object { $_.path -like 'dst/*' } | ForEach-Object { ($_.path -split '/')[-1] })
            $notCopied = @($srcByLeaf.Keys | Where-Object { $dstLeaves -notcontains $_ } | Sort-Object)
            $Ctx.Notes.Add("source files not in dst after Select All + Copy: $($notCopied -join ', ')")
            $null = Invoke-DaTreeCheck -Ctx $Ctx -Id 'full-storage-src-unchanged' -Atomic $atomic -Expect @{ OptionalPattern = '^dst/' }
            Add-DaInvariant -Ctx $Ctx -Id 'full-storage-no-truncated-file' -Atomic $atomic -Holds ($bad.Count -eq 0) -Detail "$copied files in dst; not byte-equal to their source: $($bad -join ', ')"
            if ($bad.Count) { Add-DaFinding -Ctx $Ctx -Title 'copying into nearly full storage leaves truncated files under their final names' -Atomic $atomic -Severity 'P1' -Rung 'T' -Steps @('fill storage to a 16 KiB margin', 'Select All in src', 'Copy to dst') -Expected 'only complete files, and a message' -Actual ($bad -join ', ') -Evidence @('scene.json') }
            $selected = if ($copy -and [string]$copy.dialog -match 'Copying (\d+) file') { [int]$Matches[1] } else { 0 }
            $toastText = if ($copy) { @($copy.toasts) -join ' | ' } else { '' }
            if ($selected -gt $copied -and $toastText -notmatch 'space|full|fail|error|could not|not enough') {
                Add-DaFinding -Ctx $Ctx -Title "a copy into nearly full storage reports only the files it copied ($copied of $selected) and says nothing about the rest" -Atomic $atomic -Severity 'P2' -Rung 'T' `
                    -Steps @('fill storage to a 16 KiB margin', 'Select All in src', 'Copy to dst') -Expected 'a message that the copy stopped for lack of space, with what was not copied' -Actual "toast '$toastText'; $copied of $selected files in dst" -Evidence @('scene.json')
            }
            if ($copy -and $copy.channel -eq 'nothing' -and -not $copy.pending) { Add-DaFinding -Ctx $Ctx -Title 'a copy into full storage ends without any message' -Atomic $atomic -Severity 'P2' -Rung 'V' -Steps @('fill storage', 'copy all') -Expected 'a free-space message' -Actual 'no toast or dialog' -Evidence @('scene.json') }
        }
        'F06' {
            & $open
            $rv = Invoke-DaSceneAction -Ctx $Ctx -Name 'revoke-media-permission' -Atomic $atomic -Body {
                $before = Get-DaProcessIdentity -Serial $s -Package $p
                $rec = & $inject @{ Permission = 'android.permission.READ_MEDIA_IMAGES'; OperationStartMonoMs = (Get-DaMonotonicMs) }
                Start-Sleep -Seconds 3
                $after = Get-DaProcessIdentity -Serial $s -Package $p
                [ordered]@{ pidBefore = if ($before) { $before.pid } else { $null }; pidAfter = if ($after) { $after.pid } else { $null }; fault = $rec.detail }
            }
            & $open
            $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-after-revocation' -Atomic $atomic -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'copy-after-revocation' -Atomic $atomic -Files @('IMG_0009.jpg') -Op Copy -Expect @{ Optional = @('dst/IMG_0009.jpg'); SameAs = @{ 'dst/IMG_0009.jpg' = 'src/IMG_0009.jpg' } } }
            if ($rv) { $Ctx.Notes.Add("process across the revocation: $($rv.pidBefore) -> $($rv.pidAfter)") }
        }
        'F07' {
            $k = Invoke-DaSceneAction -Ctx $Ctx -Name 'kill-during-copy' -Atomic $atomic -Body {
                $r = Invoke-DaInterruptedCopy -Ctx $Ctx -Name 'big_f07.bin' -Action 'kill-during-copy' -Atomic $atomic -Interrupt { param($at) Start-Sleep -Milliseconds 1000; & $inject @{ OperationStartMonoMs = $at } | Out-Null; 'process force-stopped' }
                Start-Sleep -Seconds 3
                [ordered]@{ dstSizeAfterKill = (@((Invoke-DaAdb -Serial $s -Arguments @('shell', "stat -c %s '$($r.DstPath)' 2>/dev/null || echo -1")).Lines) -join '').Trim(); srcSize = $r.SrcSize }
            }
            if (-not $k) { Add-DaOpen -Ctx $Ctx -Atomic $atomic -Reason 'the interrupted copy could not be driven' -Observed (@($Ctx.Notes) -join '; '); return }
            $null = Invoke-DaTreeCheck -Ctx $Ctx -Id 'kill-touches-only-the-copied-name' -Atomic $atomic -Expect @{ Optional = @('dst/big_f07.bin') }
            $retry = Invoke-DaSceneAction -Ctx $Ctx -Name 'retry-after-kill' -Atomic $atomic -Body {
                $relaunch = Measure-DaStart -Serial $s -Package $p -Component (Get-DaMainComponent -Package $p) -Mode cold -TimeoutMs 30000
                & $open
                $op = Invoke-DaFileOp -Ctx $Ctx -Action 'retry-after-kill' -Atomic $atomic -Files @('big_f07.bin') -Op Copy -WatchSec 12 -Expect @{ OptionalPattern = '^dst/big_f07' }
                $size = Wait-DaRemoteSize -Serial $s -Path "$($Ctx.Fixture.dst)/big_f07.bin" -Size (1024L * 1048576) -TimeoutMs 120000
                [ordered]@{ relaunch = $relaunch.activity; pending = $op.Extra.pendingDialog; toasts = $op.Message.Toasts; finalSize = $size }
            }
            if (-not $retry) { Add-DaOpen -Ctx $Ctx -Atomic $atomic -Reason 'the retry after the kill could not be driven' -Observed "after kill: $($k.dstSizeAfterKill) of $($k.srcSize) bytes; $(@($Ctx.Notes) -join '; ')"; return }
            $final = Get-DaRunTree -Serial $s -RunRemoteDir $run
            $copies = @($final.Entries | Where-Object { $_.path -like 'dst/big_f07*' })
            $srcE = @($final.Entries | Where-Object { $_.path -eq 'src/big_f07.bin' }) | Select-Object -First 1
            $exact = ($copies.Count -eq 1 -and $copies[0].path -eq 'dst/big_f07.bin' -and $srcE -and $copies[0].sha256 -eq $srcE.sha256)
            Add-DaInvariant -Ctx $Ctx -Id 'retry-after-kill-yields-one-complete-copy' -Atomic $atomic -Holds $exact -Detail "after kill: $($k.dstSizeAfterKill) bytes; after retry: $(@($copies | ForEach-Object { "$($_.path)=$($_.size)" }) -join ', '); conflict dialog: $(if ($retry.pending) { $retry.pending.Title } else { 'none' })"
            if (-not $exact) { Add-DaFinding -Ctx $Ctx -Title 'retrying a copy interrupted by a process kill does not yield exactly one complete copy' -Atomic $atomic -Severity 'P1' -Rung 'T' -Steps @('Copy big_f07.bin (1 GiB) to dst', 'am force-stop 1 s after the destination tap', 'relaunch, open src', 'Copy big_f07.bin to dst again') -Expected 'one dst/big_f07.bin byte-equal to the source' -Actual "dst: $(@($copies | ForEach-Object { "$($_.path)=$($_.size)" }) -join ', ')" -Evidence @('scene.json') }
            if ([long]$k.dstSizeAfterKill -ge 0 -and [long]$k.dstSizeAfterKill -ne [long]$k.srcSize) { $Ctx.Notes.Add("a truncated dst/big_f07.bin of $($k.dstSizeAfterKill) bytes stayed under the final name after the kill") }
            $Ctx.LastTree = $final
        }
        'F08' {
            $null = & $inject @{}
            & $open
            $uni = @($Ctx.LastTree.Entries | Where-Object { $_.path -like 'src/*' -and $_.path -notlike 'src/nested/*' -and ($_.path -match '[^\x00-\x7F]' -or $_.path.Length -gt 60) } | ForEach-Object { $_.path.Substring(4) })
            $added = @($uni | ForEach-Object { "dst/$_" }); $same = @{}; foreach ($u in $uni) { $same["dst/$u"] = "src/$u" }
            $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'copy-unicode-and-long-names' -Atomic $atomic -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'copy-unicode-and-long-names' -Atomic $atomic -Files $uni -Op Copy -WatchSec 10 -Expect @{ Added = $added; SameAs = $same } }
            $long = ('a' * 300) + '.jpg'
            $null = Invoke-DaSceneAction -Ctx $Ctx -Name 'rename-over-long-name' -Atomic $atomic -Body { Invoke-DaFileOp -Ctx $Ctx -Action 'rename-over-long-name' -Atomic $atomic -Files @('IMG_0010.jpg') -Op Rename -NewName $long -ExpectFailure -Expect @{} }
        }
        default { throw "fault $f has no scene step" }
    }
}

# --------------------------------------------------------------------------------------- registry and runs

function Get-DaSceneRegistry {
    <#
    .SYNOPSIS
        The scene registry: id -> title, runner, whether it publishes a fixture, whether it takes a fault, and its named actions.
    #>
    return [ordered]@{
        C01 = [ordered]@{ title = 'First launch of an empty profile; onboarding and permissions; cold and warm start'; runner = 'Invoke-DaSceneC01'; fixture = $false; fault = $false
            actions = @('clear-app-data', 'cold-start-empty-profile', 'onboarding-walk', 'warm-start', 'frames-after-first-launch', 'second-cold-start', 'first-run-flag-persisted') }
        C02 = [ordered]@{ title = 'Orderly exit, cancelled exit, closing a screen, leaving during a copy, force-stop as the abnormal case'; runner = 'Invoke-DaSceneC02'; fixture = $true; fault = $false
            actions = @('publish-fixture', 'register-resources', 'orderly-exit-back', 'exit-button', 'exit-cancelled', 'close-screen', 'leave-during-copy', 'force-stop-during-copy') }
        C03 = [ordered]@{ title = 'Sleep and wake, lock and unlock, idle and with a copy running'; runner = 'Invoke-DaSceneC03'; fixture = $true; fault = $false
            actions = @('publish-fixture', 'register-resources', 'idle-sleep-wake', 'power-key-lock-unlock', 'sleep-during-copy') }
        C07 = [ordered]@{ title = 'Copy, move, rename, delete; cancel, undo, trash and restore'; runner = 'Invoke-DaSceneC07'; fixture = $true; fault = $false
            actions = @('publish-fixture', 'register-resources', 'copy-one', 'copy-repeat', 'move-one', 'rename-one', 'rename-unedited-apply', 'rename-cancel', 'delete-cancel', 'delete-confirm', 'undo-offered', 'cancel-copy-back-key') }
        C15 = [ordered]@{ title = 'The C07 operations with one injected fault from audit/faults.json'; runner = 'Invoke-DaSceneC15'; fixture = $true; fault = $true
            actions = @('publish-fixture', 'register-resources', 'one fault-specific operation sequence per catalogue entry') }
        C05 = [ordered]@{ title = 'Open a 1000-item folder, sort, filter, search, change the source'; runner = 'Invoke-DaCycleScene'; fixture = $true; fault = $false; cycle = 'Invoke-DaSceneCycleC05'; setup = 'Initialize-DaSceneC05'
            quiescence = 'main'; watch = $false; atomic = @('C05.open-large-folder', 'C05.sort', 'C05.filter', 'C05.search', 'C05.change-source')
            actions = @('cold-start', 'setup', 'cycle-N: open-large-folder, sort, filter, search, change-source', 'quiescence: main screen, no input 3 s, one meminfo sample', 'warm-start') }
        C06 = [ordered]@{ title = 'View and page images; play a clip with pause, play, seek and stop'; runner = 'Invoke-DaCycleScene'; fixture = $true; fault = $false; cycle = 'Invoke-DaSceneCycleC06'; setup = 'Initialize-DaSceneC06'
            quiescence = 'main'; watch = $false; atomic = @('C06.view-image', 'C06.page-swipe', 'C06.play-video', 'C06.transport-controls')
            actions = @('cold-start', 'setup', 'cycle-N: view-image, page-swipe, play-video with media keys', 'quiescence: main screen, no input 3 s, one meminfo sample', 'warm-start') }
        C12 = [ordered]@{ title = 'Input storm: held key, repeated command, rapid switching, rapid open and close; watch key storm'; runner = 'Invoke-DaCycleScene'; fixture = $true; fault = $false; cycle = 'Invoke-DaSceneCycleC12'; setup = 'Initialize-DaSceneC12'
            quiescence = 'main'; watch = $true; atomic = @('C12.held-key', 'C12.repeated-command', 'C12.rapid-switching', 'C12.rapid-open-close', 'C12.watch-key-storm')
            actions = @('cold-start', 'setup', 'cycle-N: storm, key probe', 'quiescence: main screen (watch: app in front), no input 3 s, one meminfo sample', 'warm-start') }
        C14 = [ordered]@{ title = 'Large inputs: 1k then 10k items, long, Unicode and RTL names, corrupt media'; runner = 'Invoke-DaCycleScene'; fixture = $true; fault = $false; cycle = 'Invoke-DaSceneCycleC14'; setup = 'Initialize-DaSceneC14'
            quiescence = 'main'; watch = $false; atomic = @('C14.items-1k', 'C14.items-10k', 'C14.long-names', 'C14.unicode-names', 'C14.rtl-names', 'C14.corrupt-media', 'C14.large-lists')
            actions = @('cold-start', 'setup', 'cycle-1: special names and corrupt media', 'cycle-N: open the large folder, scroll to its end', 'quiescence: main screen, no input 3 s, one meminfo sample', 'warm-start') }
        C16 = [ordered]@{ title = 'Long idle with the list open, then in the background; watch idle'; runner = 'Invoke-DaCycleScene'; fixture = $true; fault = $false; cycle = 'Invoke-DaSceneCycleC16'; setup = 'Initialize-DaSceneC16'
            quiescence = 'browse'; watch = $true; atomic = @('C16.idle-foreground', 'C16.idle-background', 'C16.watch-idle')
            actions = @('cold-start', 'setup', 'cycle-N: IdleSec without input (first half in front, second half behind the launcher)', 'quiescence: the end of the idle window itself, one meminfo sample', 'warm-start') }
    }
}

function Initialize-DaSceneC12 {
    <#
    .SYNOPSIS
        C12 setup: on the phone the small set in src, registered and opened; on the watch the app is brought to the front.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    if ($Ctx.Watch) { return [bool](Wait-DaQuiescence -Ctx $Ctx -Point watch -QuietMs 0).reached }
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) { return $false }
    return (Open-DaResource -Serial $Ctx.Serial -Package $Ctx.Package -Name $Ctx.Resources.Src)
}

function Get-DaVariantName {
    <#
    .SYNOPSIS
        Variant label of the installed subject from its matched host APK path (noLegal or standard).
    .PARAMETER Subject
        Get-DaSubject output.
    #>
    param([Parameter(Mandatory)]$Subject)
    $noLegal = ([string]$Subject.hostApk -match '/noLegal/' -or [string]$Subject.versionName -match 'NoLegal')
    if ([string]$Subject.formFactor -eq 'watch') { if ($noLegal) { return 'wear-noLegal' } else { return 'wear-standard' } }
    if ($noLegal) { return 'noLegal' }
    return 'standard'
}

function Invoke-DaScene {
    <#
    .SYNOPSIS
        Runs one registered scene on a context and closes its record; returns {Code; Reason; RecordPath}. Never mutates without the caller's envelope.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Scene
        Registered scene id.
    .PARAMETER Fault
        Fault id for C15.
    .PARAMETER RunId
        Run id.
    .PARAMETER Fixture
        Fixture record already published by the caller (heavy run).
    .PARAMETER CyclePlan
        For a cycle scene: Cycles, WarmupCycles, IdleSec, HeapSnapshot; omitted means one cycle and no warm-up.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Scene, [string]$Fault, [Parameter(Mandatory)][string]$RunId, $Fixture, [hashtable]$CyclePlan)
    $reg = Get-DaSceneRegistry
    if (-not $reg.Contains($Scene)) { throw "scene $Scene is not registered" }
    $entry = $reg[$Scene]
    if ($Fault -and -not $entry.fault) { throw "scene $Scene takes no fault" }
    $subject = Get-DaSubject -Serial $Serial -Package $Package
    if (-not $subject.installed) { throw "$Package is not installed" }
    $watch = ([string]$subject.formFactor -eq 'watch')
    if ($watch -and -not $entry.watch) { throw "scene $Scene has no watch variant" }
    $ctx = New-DaSceneContext -Serial $Serial -Package $Package -Scene $Scene -RunId $RunId -Fault $Fault -Variant (Get-DaVariantName -Subject $subject)
    $ctx.Watch = $watch
    if ($entry.cycle) {
        $plan = @{ Cycles = 1; WarmupCycles = 0; IdleSec = 60; HeapSnapshot = $false }
        if ($CyclePlan) { foreach ($k in @($CyclePlan.Keys)) { $plan[$k] = $CyclePlan[$k] } }
        $ctx.CyclePlan = $plan
    }
    if ($Fixture) { $ctx.Fixture = $Fixture }
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('logcat', '-c')
    $cond = Get-DaDeviceConditions -Serial $Serial
    $abort = $null
    try { & $entry.runner -Ctx $ctx }
    catch { $abort = "$($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)" }
    finally {
        foreach ($cleanStep in $ctx.Cleanups) { try { $ctx.Notes.Add("cleanup: $(& $cleanStep $ctx)") } catch { $ctx.Notes.Add("cleanup failed: $($_.Exception.Message)") } }
        if ($ctx.FaultRecord) { $ctx.FaultRecord.cleared = ($ctx.Notes | Where-Object { $_ -like 'cleanup:*' }) -join '; ' }
    }
    return Complete-DaSceneRecord -Ctx $ctx -ConditionsBefore $cond -AbortReason $abort
}

function Get-DaOutsideSnapshot {
    <#
    .SYNOPSIS
        Listing of the shared-storage folders a scene could write to by mistake, without the run's own directory; one line per entry.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER RunId
        Run id whose directory is excluded.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$RunId)
    $root = Get-DaFixtureRemoteRoot
    $cmd = "for d in /storage/emulated/0/Download /storage/emulated/0/DCIM /storage/emulated/0/Pictures /storage/emulated/0/Movies /storage/emulated/0/Music /storage/emulated/0/Documents; do [ -d `$d ] && find `$d -maxdepth 2 -not -path '$root/$RunId*' -exec stat -c '%s|%n' {} + 2>/dev/null; done"
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', $cmd) -TimeoutMs 120000
    return @(@($r.Lines) | Where-Object { $_ -match '^\d+\|' -and $_ -notmatch '/FastMediaSorter_DynAudit$' } | Sort-Object)
}

function Invoke-DaHeavyRun {
    <#
    .SYNOPSIS
        Heavy-run sequence: preflight, verified backup, checkpoint, fixture publish, calibration check, one scene (+ one fault), outside invariant, restore in finally, heavy.json. Returns {Code; Reason; RecordPath; Steps}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Scene
        Registered scene id.
    .PARAMETER Fault
        Fault id (C15 only).
    .PARAMETER RunId
        Run id; generated when empty.
    .PARAMETER FreshBackup
        Take and verify a new backup instead of reusing the current verified one.
    .PARAMETER Hooks
        Hashtable of scriptblocks replacing steps (Preflight, Backup, Checkpoint, Publish, Calibration, Outside, Scene, Restore) - the device-free suite.
    .PARAMETER RunsRoot
        Directory receiving runs/<runId>/heavy.json; defaults to the scratch runs directory.
    .PARAMETER CyclePlan
        Passed to Invoke-DaScene for a cycle scene (the Cycle verb).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Scene, [string]$Fault, [string]$RunId,
        [switch]$FreshBackup, [hashtable]$Hooks = @{}, [string]$RunsRoot, [hashtable]$CyclePlan)
    $codes = Get-DaExitCode
    if (-not $RunId) { $RunId = New-DaRunId }
    if (-not $RunsRoot) { $RunsRoot = (Get-DaPaths).Runs }
    $runDir = Join-Path $RunsRoot $RunId
    [void][System.IO.Directory]::CreateDirectory($runDir)
    $steps = [System.Collections.Generic.List[object]]::new()
    $step = { param($name, $code, $detail) $steps.Add([ordered]@{ step = $name; code = [int]$code; detail = [string]$detail; atMonoMs = Get-DaMonotonicMs }) }
    $h = @{
        Preflight   = { Assert-DaBackupVerified -Package $Package -Avd (Get-DaAvdName -Serial $Serial) }
        Backup      = {
            if (-not $FreshBackup) { return [pscustomobject]@{ Code = 0; Reason = 'reusing the current verified backup' } }
            $b = New-DaBackup -Serial $Serial -Package $Package
            if ($b.Code -ne 0) { return $b }
            Test-DaBackup
        }
        Checkpoint  = { New-DaRestoreCheckpoint -Serial $Serial -Package $Package -Reason "Heavy $Scene $Fault run $RunId" -RunId $RunId }
        Publish     = { Initialize-DaOpsFixture -Serial $Serial -RunId $RunId }
        Calibration = {
            $ptr = Read-DaJson -Path (Get-DaCalibrationPointerPath)
            $cal = if ($ptr) { Read-DaJson -Path ([string]$ptr.path) } else { $null }
            if (-not $cal) { return [pscustomobject]@{ Code = 2; Reason = 'no calibration record' } }
            if ([string]$cal.device.avd -ne (Get-DaAvdName -Serial $Serial) -or -not $cal.selfCheck.valid) { return [pscustomobject]@{ Code = 2; Reason = "calibration $($cal.runId) is not usable for this AVD" } }
            [pscustomobject]@{ Code = 0; Reason = "calibration $($cal.runId) at $($cal.at)" }
        }
        Outside     = { Get-DaOutsideSnapshot -Serial $Serial -RunId $RunId }
        Scene       = { param($fixture) Invoke-DaScene -Serial $Serial -Package $Package -Scene $Scene -Fault $Fault -RunId $RunId -Fixture $fixture -CyclePlan $CyclePlan }
    }
    foreach ($k in @($Hooks.Keys)) { $h[$k] = $Hooks[$k] }
    $finish = {
        param($code, $reason, $restored)
        $rec = [ordered]@{ schemaVersion = 1; kind = 'heavy'; runId = $RunId; scene = $Scene; fault = if ($Fault) { $Fault } else { $null }; freshBackup = [bool]$FreshBackup; cyclePlan = $CyclePlan
            code = [int]$code; reason = $reason; restored = $restored; steps = $steps.ToArray(); finishedAt = (Get-Date).ToUniversalTime().ToString('o') }
        Write-DaJson -Path (Join-Path $runDir 'heavy.json') -InputObject $rec -Durable
        [pscustomobject]@{ Code = [int]$code; Reason = $reason; RecordPath = (Join-Path $runDir 'heavy.json'); Steps = $steps.ToArray() }
    }
    $pre = & $h.Preflight
    & $step 'preflight' $pre.Code $pre.Reason
    if ([int]$pre.Code -ne 0) { return (& $finish $codes.Refused "preflight refused: $($pre.Reason)" $null) }
    $bk = & $h.Backup
    & $step 'verified-backup' $bk.Code $bk.Reason
    if ([int]$bk.Code -ne 0) { return (& $finish $codes.Refused "backup step failed: $($bk.Reason)" $null) }
    $ck = & $h.Checkpoint
    & $step 'checkpoint' $ck.Code $ck.Reason
    if ([int]$ck.Code -ne 0) { return (& $finish $codes.Refused "checkpoint refused: $($ck.Reason)" $null) }
    $sceneResult = $null
    $restore = $null
    $failure = $null
    try {
        $fixture = & $h.Publish
        & $step 'fixture-publish' 0 "$($fixture.count) files at $($fixture.runRemoteDir)"
        $cal = & $h.Calibration
        & $step 'calibration-check' $cal.Code $cal.Reason
        if ([int]$cal.Code -ne 0) { throw "calibration check failed: $($cal.Reason)" }
        $outBefore = @(& $h.Outside)
        & $step 'outside-before' 0 "$($outBefore.Count) entries outside the run directory"
        $sceneResult = & $h.Scene $fixture
        & $step 'scene' $sceneResult.Code $sceneResult.Reason
        $outAfter = @(& $h.Outside)
        $outDiff = @(Compare-Object -ReferenceObject $outBefore -DifferenceObject $outAfter | ForEach-Object { "$($_.SideIndicator) $($_.InputObject)" })
        & $step 'outside-invariant' $(if ($outDiff.Count) { 1 } else { 0 }) $(if ($outDiff.Count) { "changed outside the run directory: $(@($outDiff | Select-Object -First 8) -join '; ')" } else { 'nothing outside the run directory changed' })
    }
    catch { $failure = "$($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)"; & $step 'failure' 2 $failure }
    finally {
        try { $restore = if ($Hooks.ContainsKey('Restore')) { & $Hooks.Restore } else { Restore-DaBackup -Serial $Serial } }
        catch { $restore = [pscustomobject]@{ Code = 2; Reason = "restore threw: $($_.Exception.Message)" } }
        & $step 'restore' $restore.Code $restore.Reason
    }
    $codesSeen = @($steps | ForEach-Object { $_.code })
    $code = ($codesSeen | Measure-Object -Maximum).Maximum
    $reason = if ($failure) { "heavy run $RunId failed: $failure" } elseif ($sceneResult) { $sceneResult.Reason } else { 'no scene result' }
    return (& $finish $code "$reason; restore: $($restore.Reason)" ([int]$restore.Code -eq 0))
}

function Invoke-DaSceneRun {
    <#
    .SYNOPSIS
        The Scene verb: one registered scene inside Invoke-DaEnvelope (checkpoint, scene, Restore-DaBackup in finally). Returns {Code; Reason}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Scene
        Registered scene id.
    .PARAMETER Fault
        Fault id (C15 only).
    .PARAMETER RunId
        Run id; generated when empty.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Scene, [string]$Fault, [string]$RunId)
    if (-not $RunId) { $RunId = New-DaRunId }
    $envOut = Invoke-DaEnvelope -Serial $Serial -Package $Package -RunId $RunId -Reason "Scene $Scene $Fault run $RunId" -Body { Invoke-DaScene -Serial $Serial -Package $Package -Scene $Scene -Fault $Fault -RunId $RunId }
    return [pscustomobject]@{ Code = $envOut.Code; Reason = $envOut.Reason }
}
