#requires -Version 7.0
<#
.SYNOPSIS
    S4166 dynamic-audit stand: consolidation of run records into matrix cells, finding cards, redacted export and the first-pass report.

.DESCRIPTION
    Dot-sourced by scripts/devtest/dynamic-audit/da.ps1 (-Verb Report) and its contract suite; needs lib/da-common.ps1
    and lib/da-stats.ps1. No device access.

    Update-DaMatrix turns the latest valid run of each scene, variant, fault and device into measured scene cells.
    Only scene-bound cells (matrix.sceneCells) are written: a run drives atomic actions, not each sampled capability,
    so capability-bound cells keep their open status. A cell is measured only for the context the stand really used
    (Get-DaStandContext); every other locale, input and state stays open. An invalid run never produces a cell.

    Export-DaAggregate redacts before anything is written to the durable audit folder and appends one entry to the
    access and export manifest (who, what, where, when). Raw run records, heap dumps and backups stay under the
    ticket scratch root; what leaves it is a per-run aggregate of counts, verdicts and distributions.
#>

function Get-DaSecretPatterns {
    <#
    .SYNOPSIS
        Regular expressions whose matches are replaced before export: credentials, provider token shapes, mail
        addresses, URL query strings and user home paths.
    #>
    return @(
        [regex]'(?i)\b(?:pass(?:word|wd)?|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|client[_-]?secret|auth[_-]?token|credential|bearer|cookie|session[_-]?id|pin)\s*[=:]\s*\S+'
        [regex]'(?:AIza[0-9A-Za-z_\-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{12,}|xox[abprs]-[A-Za-z0-9\-]{10,}|sk-[A-Za-z0-9]{20,}|eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-\.]*)'
        [regex]'(?i)Bearer\s+[A-Za-z0-9\-\._~\+/]+=*'
        [regex]'[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}'
        [regex]'(?i)\b(?:https?|ftp|smb|sftp|webdav)://\S+'
        [regex]'(?i)[A-Z]:\\Users\\[^\\\s"]+'
        [regex]'/(?:home|Users)/[^/\s"]+'
    )
}

function Get-DaSecretKeyNames {
    <#
    .SYNOPSIS
        Property names whose values are never exported, whatever they hold.
    #>
    return [regex]'(?i)^(?:password|passwd|secret|token|apikey|api_key|accesskey|cookie|cookies|pin|auth|authorization|credential|credentials|clipboard|session)$'
}

function ConvertTo-DaRedacted {
    <#
    .SYNOPSIS
        Deep copy of a value with every secret-shaped string replaced by <redacted>; dictionaries, objects and lists are walked.
    .PARAMETER Value
        Any JSON-shaped value.
    #>
    param($Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [string]) {
        $t = $Value
        foreach ($rx in (Get-DaSecretPatterns)) { $t = $rx.Replace($t, '<redacted>') }
        return $t
    }
    if ($Value -is [System.ValueType]) { return $Value }
    $keyRx = Get-DaSecretKeyNames
    if ($Value -is [System.Collections.IDictionary]) {
        $o = [ordered]@{}
        foreach ($k in $Value.Keys) { $o[[string]$k] = if ($keyRx.IsMatch([string]$k)) { '<redacted>' } else { ConvertTo-DaRedacted -Value $Value[$k] } }
        return $o
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        $o = [ordered]@{}
        foreach ($p in $Value.PSObject.Properties) { $o[$p.Name] = if ($keyRx.IsMatch($p.Name)) { '<redacted>' } else { ConvertTo-DaRedacted -Value $p.Value } }
        return $o
    }
    if ($Value -is [System.Collections.IEnumerable]) { return @(foreach ($x in $Value) { ConvertTo-DaRedacted -Value $x }) }
    return (ConvertTo-DaRedacted -Value ([string]$Value))
}

function Get-DaStandContext {
    <#
    .SYNOPSIS
        The matrix context (locale/input/state) the stand really drives for one atomic scene; $null when it drives none.
    .DESCRIPTION
        Locale: the stand AVDs run in English and the scenes never switch locale outside the Access verb.
        Input: adb taps are touch; the held-key burst is a keyboard key event. State: the watch geometry comes
        from the AVD name; idle scenes run on the restored backup profile with no optional background feature
        switched on, which is the matrix state feature-off; every other atomic runs in its default state.
    .PARAMETER Atomic
        Atomic scene id, for example C12.held-key.
    .PARAMETER Avd
        AVD name of the run's device.
    #>
    param([Parameter(Mandatory)][string]$Atomic, [string]$Avd)
    $inp = if ($Atomic -eq 'C12.held-key') { 'keyboard' } else { 'touch' }
    $state = 'default'
    if ($Atomic -match '^C1[26]\.watch-') {
        if ($Avd -match '(?i)small') { $state = 'small-round' } elseif ($Avd -match '(?i)large') { $state = 'large-round' } else { return $null }
    }
    elseif ($Atomic -match '^C16\.idle-') { $state = 'feature-off' }
    return "en/$inp/$state"
}

function Get-DaVerdictRank {
    <#
    .SYNOPSIS
        Severity order of a verdict: fail 3, inconclusive 2, pass 1, anything else 0.
    .PARAMETER Verdict
        pass, fail or inconclusive.
    #>
    param([string]$Verdict)
    switch ($Verdict) { 'fail' { 3 } 'inconclusive' { 2 } 'pass' { 1 } default { 0 } }
}

function Get-DaRunRecords {
    <#
    .SYNOPSIS
        Scene runs under a runs root, newest first: {RunId; Dir; Scene; Analysis; Key}; Key groups scene, variant, fault and device.
    .PARAMETER RunsRoot
        The stand's runs directory.
    #>
    param([Parameter(Mandatory)][string]$RunsRoot)
    if (-not (Test-Path -LiteralPath $RunsRoot)) { return @() }
    $out = foreach ($d in (Get-ChildItem -LiteralPath $RunsRoot -Directory | Sort-Object Name -Descending)) {
        $scene = Read-DaJson -Path (Join-Path $d.FullName 'scene.json')
        if (-not $scene -or [string]$scene.kind -ne 'scene') { continue }
        $analysis = Read-DaJson -Path (Join-Path $d.FullName 'analysis.json')
        [pscustomobject]@{
            RunId = [string]$scene.runId; Dir = $d.FullName; Scene = $scene; Analysis = $analysis
            Key = "$($scene.scene)|$($scene.variant)|$($scene.fault)|$($scene.device.avd)"
        }
    }
    return @($out)
}

function Select-DaLatestRuns {
    <#
    .SYNOPSIS
        Splits run records into the latest valid run per key (Used) and every invalid run (Invalid); older valid runs are Superseded.
    .PARAMETER Records
        Get-DaRunRecords output, newest first.
    #>
    param([AllowEmptyCollection()][object[]]$Records = @())
    $used = [ordered]@{}; $invalid = [System.Collections.Generic.List[object]]::new(); $superseded = [System.Collections.Generic.List[object]]::new()
    foreach ($r in $Records) {
        if (-not $r.Scene.valid) { $invalid.Add($r); continue }
        if ($used.Contains($r.Key)) { $superseded.Add($r); continue }
        $used[$r.Key] = $r
    }
    return [pscustomobject]@{ Used = @($used.Values); Invalid = $invalid.ToArray(); Superseded = $superseded.ToArray() }
}

function Get-DaRunMeasurements {
    <#
    .SYNOPSIS
        Property measurements one valid run supports, per atomic scene: list of {atomic; property; verdict; method; metric; unit; n; budget; detail}.
    .DESCRIPTION
        E from the run's independently judged invariants of that atomic (budget B13). L from a valid cycle analysis,
        shared by every atomic the cycle drove (budget B09 for memory, B10 for objects; the worst verdict of both).
        O from key-probe reactions recorded under the atomic (budget B02): a lost probe or no reaction makes the
        verdict inconclusive because the probe reads reaction from the UI tree, p95 over 100 ms on 20 or more probes
        fails, a pass needs 1000 probes for p99. S is never measured by a scene run. Atomic ids the run lists as
        open are skipped.
    .PARAMETER Record
        One Get-DaRunRecords item.
    #>
    param([Parameter(Mandatory)]$Record)
    $scene = $Record.Scene
    $openAtomic = @{}; foreach ($o in @($scene.open)) { if ($o.atomic) { $openAtomic[[string]$o.atomic] = $true } }
    $atomics = [System.Collections.Generic.List[string]]::new()
    foreach ($x in @(@($scene.actions) + @($scene.invariants))) {
        $a = [string]$x.atomic
        if ($a -and -not $openAtomic.Contains($a) -and -not $atomics.Contains($a)) { $atomics.Add($a) }
    }
    $out = [System.Collections.Generic.List[object]]::new()
    $an = $Record.Analysis
    $lVerdict = $null
    if ($an -and $an.valid) {
        $worst = 'pass'
        foreach ($v in @($an.verdicts)) { if ((Get-DaVerdictRank $v.verdict) -gt (Get-DaVerdictRank $worst)) { $worst = [string]$v.verdict } }
        $growth = @($an.result.retainedGrowth)
        $lVerdict = [ordered]@{ verdict = $worst; n = [int]$an.measuredPoints; detail = "retained growth: $(if ($growth.Count) { $growth -join ', ' } else { 'none' }); verdicts $(@($an.verdicts).Count), inconclusive $([int]$an.result.inconclusive)" }
    }
    foreach ($a in $atomics) {
        $inv = @($scene.invariants | Where-Object { [string]$_.atomic -eq $a })
        if ($inv.Count) {
            $broken = @($inv | Where-Object { -not $_.holds })
            $out.Add([ordered]@{ atomic = $a; property = 'E'; verdict = if ($broken.Count) { 'fail' } else { 'pass' }; method = 'independent invariant check after the action'
                    metric = 'invariants-held'; unit = 'invariants'; n = $inv.Count; budget = 'B13'; detail = "$($inv.Count - $broken.Count) of $($inv.Count) held$(if ($broken.Count) { '; broken: ' + (($broken | ForEach-Object { $_.id }) -join ', ') })" })
        }
        if ($lVerdict) {
            $out.Add([ordered]@{ atomic = $a; property = 'L'; verdict = $lVerdict.verdict; method = 'resource sample at the quiescence point of each measured cycle, block-bootstrap slope'
                    metric = 'slope-upper-95'; unit = 'KiB/cycle; objects/hour'; n = $lVerdict.n; budget = 'B09'; detail = $lVerdict.detail })
        }
        $react = @($scene.actions | Where-Object { [string]$_.atomic -eq $a -and $_.result -and $_.result.PSObject.Properties['hostReactionMs'] })
        if ($react.Count) {
            $vals = [double[]]@($react | Where-Object { $_.result.outcome -eq 'reacted' -and $null -ne $_.result.hostReactionMs } | ForEach-Object { [double]$_.result.hostReactionMs })
            $lost = $react.Count - $vals.Length
            $dist = Get-DaPercentiles -Values $vals -Timeouts $lost -MinValidForP99 1000
            $verdict = 'inconclusive'
            if ($lost -eq 0 -and $vals.Length -ge 20 -and [double]$dist.p95 -gt 100) { $verdict = 'fail' }
            elseif ($lost -eq 0 -and $vals.Length -ge 1000 -and [double]$dist.p95 -le 100 -and [double]$dist.p99 -le 200) { $verdict = 'pass' }
            $out.Add([ordered]@{ atomic = $a; property = 'O'; verdict = $verdict; method = 'host key injection, reaction read from the UI tree'
                    metric = 'key-to-visible-reaction'; unit = 'ms'; n = $react.Count; budget = 'B02'; detail = "p50 $($dist.p50), p95 $($dist.p95), no reaction $lost of $($react.Count)" })
        }
    }
    return $out.ToArray()
}

function Get-DaRunSummary {
    <#
    .SYNOPSIS
        The runs.json entry of one run record.
    .PARAMETER Record
        One Get-DaRunRecords item.
    #>
    param([Parameter(Mandatory)]$Record)
    $s = $Record.Scene
    $plan = $s.cyclePlan
    $steady = if ($Record.Analysis -and $Record.Analysis.valid) {
        $w = @(foreach ($p in $Record.Analysis.segments.PSObject.Properties) { [double]$p.Value.windowMs }) | Measure-Object -Sum
        [math]::Round($w.Sum / 60000.0, 2)
    } else { $null }
    return [ordered]@{
        runId = $Record.RunId; scene = [string]$s.scene; fault = $s.fault; variant = [string]$s.variant
        build = [ordered]@{ package = $s.build.package; versionName = $s.build.versionName; versionCode = $s.build.versionCode; apkSha256 = $s.build.apkSha256 }
        device = [ordered]@{ kind = $s.device.kind; avd = $s.device.avd; api = $s.device.api }
        valid = [bool]$s.valid; invalidReason = $s.invalidReason; startedAt = $s.startedAt
        cycles = if ($plan) { $plan.Cycles } else { $null }; steadyMinutes = $steady
        calibration = if ($s.calibration) { $s.calibration.runId } else { $null }
    }
}

function New-DaRunAggregate {
    <#
    .SYNOPSIS
        Anonymised per-run aggregate written as evidence: identity, invariant outcomes, open atomics, finding cards
        and, for a cycle run, the analysis verdicts and distributions. No raw sample, message text or path leaves.
    .PARAMETER Record
        One Get-DaRunRecords item.
    .PARAMETER Measurements
        Get-DaRunMeasurements output of the same record.
    .PARAMETER Cards
        Finding cards of the run.
    #>
    param([Parameter(Mandatory)]$Record, [AllowEmptyCollection()][object[]]$Measurements = @(), [AllowEmptyCollection()][object[]]$Cards = @())
    $s = $Record.Scene
    $agg = [ordered]@{
        schemaVersion = 1; kind = 'run-aggregate'; run = Get-DaRunSummary -Record $Record
        invariants = @($s.invariants | ForEach-Object { [ordered]@{ id = $_.id; atomic = $_.atomic; holds = [bool]$_.holds } })
        open = @($s.open | ForEach-Object { [ordered]@{ atomic = $_.atomic; reason = $_.reason } })
        measurements = $Measurements; findings = $Cards
    }
    if ($Record.Analysis -and $Record.Analysis.valid) {
        $agg.analysis = [ordered]@{
            measuredPoints = $Record.Analysis.measuredPoints; warmupExcluded = $Record.Analysis.warmupExcluded; bootstrap = $Record.Analysis.bootstrap
            verdicts = $Record.Analysis.verdicts; retainedGrowth = $Record.Analysis.result.retainedGrowth; open = $Record.Analysis.open
            segments = [ordered]@{}
        }
        foreach ($p in $Record.Analysis.segments.PSObject.Properties) {
            $m = [ordered]@{}
            foreach ($q in $p.Value.metrics.PSObject.Properties) {
                $v = $q.Value
                $m[$q.Name] = [ordered]@{ unit = $v.unit; state = $v.state; n = $v.n; decision = $v.decision; p50 = $v.distribution.p50; max = $v.distribution.max
                    slopePerCycle = if ($v.slopePerCycle) { [ordered]@{ slope = $v.slopePerCycle.slope; lower = $v.slopePerCycle.lower; upper = $v.slopePerCycle.upper } } else { $null } }
            }
            $agg.analysis.segments[$p.Name] = [ordered]@{ points = $p.Value.points; windowMs = $p.Value.windowMs; metrics = $m }
        }
    }
    return ConvertTo-DaRedacted -Value $agg
}

function New-DaFindingCard {
    <#
    .SYNOPSIS
        Finding card of strategic section 5.7 from one candidate finding of a run.
    .DESCRIPTION
        Severity comes from the finding; a P0 or P1 whose rung is not T or L stays a candidate hypothesis, and a
        hypothesis-budget exceedance alone never raises a card to P0 or P1 (it is capped at P2 and marked).
        Every text field is redacted.
    .PARAMETER Finding
        A candidate finding of scene.json (title, scene, atomic, variant, fault, severityGuess, rung, detail..).
    .PARAMETER Record
        The run record the finding belongs to.
    .PARAMETER BudgetOnly
        The finding rests only on a budget that is still a hypothesis.
    .PARAMETER Decision
        Reference to an accepted decision in audit/decisions.json, when one applies.
    #>
    param([Parameter(Mandatory)]$Finding, [Parameter(Mandatory)]$Record, [switch]$BudgetOnly, [string]$Decision)
    $s = $Record.Scene
    $sev = [string]$Finding.severityGuess; if ($sev -notmatch '^P[0-3]$') { $sev = 'P2' }
    $rung = [string]$Finding.rung; if ($rung -notmatch '^[RVTL]$') { $rung = 'V' }
    $notes = [System.Collections.Generic.List[string]]::new()
    if ($BudgetOnly -and $sev -in @('P0', 'P1')) { $sev = 'P2'; $notes.Add('capped at P2: the exceeded budget is a hypothesis, not an approved threshold') }
    $standing = 'confirmed'
    if ($sev -in @('P0', 'P1') -and $rung -notin @('T', 'L')) { $standing = 'candidate hypothesis'; $notes.Add("rung $rung is below T; a $sev needs T or L to be confirmed") }
    $card = [ordered]@{
        id = "FC-$($Record.RunId)-$([string]$Finding.id)"; scene = [string]$s.scene; atomic = [string]$Finding.atomic
        cell = "$($s.scene)|$($Finding.atomic)|$($s.variant)|$(Get-DaStandContext -Atomic ([string]$Finding.atomic) -Avd ([string]$s.device.avd))"
        binary = [ordered]@{ variant = $s.variant; versionName = $s.build.versionName; apkSha256 = $s.build.apkSha256 }
        stand = [ordered]@{ device = $s.device.kind; avd = $s.device.avd; calibration = if ($s.calibration) { $s.calibration.runId } else { $null }; debugBuild = $true }
        preconditions = 'verified backup restored; synthetic fixture published by the run'
        actions = @($s.actions | Where-Object { [string]$_.atomic -eq [string]$Finding.atomic } | ForEach-Object { $_.name })
        fixture = if ($s.fixture) { $s.fixture.set } else { $null }; fault = $s.fault
        expected = $Finding.expected; actual = $Finding.detail
        frequency = $Finding.frequency; sample = $Finding.sample
        control = 'calibration and control run of the stand; see run identity'; magnitude = $Finding.magnitude
        severity = $sev; rung = $rung; standing = $standing; decision = if ($Decision) { $Decision } else { $null }
        notes = $notes.ToArray(); nextStep = 'reproduce with the named run command; fix under its own ticket'
    }
    return ConvertTo-DaRedacted -Value $card
}

function Update-DaMatrix {
    <#
    .SYNOPSIS
        Writes measured scene cells from the latest valid runs into a matrix (hashtable from matrix.json); returns
        {Matrix; Runs; Evidence; Aggregates; Cards; Applied; Unmatched; Selection}.
    .DESCRIPTION
        A cell receiving several measurements keeps the worst verdict and names the run that produced it; n is the
        sum. A measurement whose context the matrix does not carry is reported as unmatched, never forced into
        another cell. Cells not touched keep their status and reason.
    .PARAMETER Matrix
        matrix.json read as a hashtable.
    .PARAMETER Records
        Get-DaRunRecords output.
    .PARAMETER BudgetVersion
        budgets.json version.
    .PARAMETER EvidenceRel
        Repo-relative folder the per-run aggregates are written to (the evidence uri prefix).
    #>
    param([Parameter(Mandatory)]$Matrix, [AllowEmptyCollection()][object[]]$Records = @(), [Parameter(Mandatory)][string]$BudgetVersion, [Parameter(Mandatory)][string]$EvidenceRel)
    $sel = Select-DaLatestRuns -Records $Records
    $index = @{}; foreach ($c in @($Matrix['sceneCells'])) { $index[[string]$c['key']] = $c }
    $runs = [System.Collections.Generic.List[object]]::new(); $evidence = [System.Collections.Generic.List[object]]::new()
    $aggregates = [ordered]@{}; $cards = [System.Collections.Generic.List[object]]::new(); $unmatched = [System.Collections.Generic.List[string]]::new()
    $applied = @{}
    foreach ($r in $sel.Used) {
        $ms = @(Get-DaRunMeasurements -Record $r)
        $runCards = @(foreach ($f in @($r.Scene.findings)) { New-DaFindingCard -Finding $f -Record $r })
        foreach ($cd in $runCards) { $cards.Add($cd) }
        $runs.Add((Get-DaRunSummary -Record $r))
        $evId = "ev-$($r.RunId)"
        $aggregates[$evId] = [ordered]@{ uri = "$EvidenceRel/$($r.RunId).json"; body = (New-DaRunAggregate -Record $r -Measurements $ms -Cards $runCards) }
        foreach ($m in $ms) {
            $ctx = Get-DaStandContext -Atomic $m.atomic -Avd ([string]$r.Scene.device.avd)
            $key = "$($r.Scene.scene)|$($m.atomic)|$($r.Scene.variant)|$ctx|$($m.property)"
            if (-not $ctx -or -not $index.Contains($key)) { $unmatched.Add("$key (run $($r.RunId))"); continue }
            $c = $index[$key]
            if ($applied.Contains($key)) {
                $c['n'] = [int]$c['n'] + [int]$m.n
                if ((Get-DaVerdictRank $m.verdict) -le (Get-DaVerdictRank ([string]$c['verdict']))) { continue }
            }
            else { $applied[$key] = $true; $c['n'] = [int]$m.n }
            foreach ($drop in @('reason', 'owner', 'nextStep', 'source', 'condition')) { if ($c.Contains($drop)) { $c.Remove($drop) } }
            $c['status'] = 'measured'; $c['run'] = $r.RunId; $c['method'] = $m.method; $c['metric'] = $m.metric; $c['unit'] = $m.unit
            $c['budget'] = $m.budget; $c['budgetVersion'] = $BudgetVersion; $c['evidence'] = $evId; $c['verdict'] = $m.verdict; $c['detail'] = ConvertTo-DaRedacted -Value $m.detail
        }
    }
    return [pscustomobject]@{
        Matrix = $Matrix; Runs = $runs.ToArray(); Aggregates = $aggregates; Cards = $cards.ToArray()
        Applied = $applied.Count; Unmatched = $unmatched.ToArray(); Selection = $sel
    }
}

function Export-DaAggregate {
    <#
    .SYNOPSIS
        Writes the redacted matrix, runs.json, evidence.json and the per-run aggregates into the audit folder and
        appends one entry to audit/export-manifest.json; returns {Written; EvidenceCount}.
    .DESCRIPTION
        Redaction runs on every object before it is serialised. evidence.json carries the sha256 of each aggregate
        file as written, so the coverage validator can check it. The manifest entry states who exported, what
        files, where to and when; nothing is published outside the repository.
    .PARAMETER AuditRoot
        PLAN/S4166_deep-dynamic-audit/audit (or a test copy).
    .PARAMETER RepoRoot
        Repository root the evidence uris are relative to.
    .PARAMETER Update
        Update-DaMatrix output.
    .PARAMETER Who
        Exporter label for the access and export manifest.
    #>
    param([Parameter(Mandatory)][string]$AuditRoot, [Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)]$Update, [string]$Who = 'S4166 stand (agent run)')
    $written = [System.Collections.Generic.List[string]]::new()
    $evidence = [System.Collections.Generic.List[object]]::new()
    foreach ($id in $Update.Aggregates.Keys) {
        $a = $Update.Aggregates[$id]
        $full = Join-Path $RepoRoot $a.uri
        [void][System.IO.Directory]::CreateDirectory((Split-Path -Parent $full))
        Write-DaJson -Path $full -InputObject (ConvertTo-DaRedacted -Value $a.body)
        $evidence.Add([ordered]@{ id = $id; uri = $a.uri; sha256 = Get-DaFileSha256 -Path $full })
        $written.Add($a.uri)
    }
    Write-DaJson -Path (Join-Path $AuditRoot 'runs.json') -InputObject ([ordered]@{ schemaVersion = 1; runs = @(ConvertTo-DaRedacted -Value $Update.Runs) })
    Write-DaJson -Path (Join-Path $AuditRoot 'evidence.json') -InputObject ([ordered]@{ schemaVersion = 1; evidence = $evidence.ToArray() })
    Write-DaJson -Path (Join-Path $AuditRoot 'matrix.json') -InputObject (ConvertTo-DaRedacted -Value $Update.Matrix)
    foreach ($f in @('runs.json', 'evidence.json', 'matrix.json')) { $written.Add($f) }
    $manPath = Join-Path $AuditRoot 'export-manifest.json'
    $man = Read-DaJson -Path $manPath
    $entries = [System.Collections.Generic.List[object]]::new()
    if ($man -and $man.entries) { foreach ($e in @($man.entries)) { $entries.Add($e) } }
    $entries.Add([ordered]@{ who = $Who; what = $written.ToArray(); where = 'PLAN/S4166_deep-dynamic-audit/audit (repository only, no external publication)'; when = (Get-Date).ToUniversalTime().ToString('o'); redaction = 'ConvertTo-DaRedacted before serialisation' })
    Write-DaJson -Path $manPath -InputObject ([ordered]@{ schemaVersion = 1; entries = $entries.ToArray() })
    return [pscustomobject]@{ Written = $written.ToArray(); EvidenceCount = $evidence.Count }
}

function Get-DaCoverageCounts {
    <#
    .SYNOPSIS
        Cell counts by status and by property for capability cells and scene cells, plus verdict counts of measured cells.
    .PARAMETER Matrix
        matrix.json as a hashtable.
    #>
    param([Parameter(Mandatory)]$Matrix)
    $out = [ordered]@{}
    foreach ($kind in @('cells', 'sceneCells')) {
        $byStatus = [ordered]@{ measured = 0; 'n/a' = 0; open = 0 }; $byProp = [ordered]@{}; $verdicts = [ordered]@{ pass = 0; fail = 0; inconclusive = 0 }
        foreach ($c in @($Matrix[$kind])) {
            $st = [string]$c['status']; if ($byStatus.Contains($st)) { $byStatus[$st]++ }
            $p = [string]$c['property']; if (-not $byProp.Contains($p)) { $byProp[$p] = [ordered]@{ measured = 0; 'n/a' = 0; open = 0 } }; if ($byProp[$p].Contains($st)) { $byProp[$p][$st]++ }
            if ($st -eq 'measured' -and $verdicts.Contains([string]$c['verdict'])) { $verdicts[[string]$c['verdict']]++ }
        }
        $out[$kind] = [ordered]@{ total = @($Matrix[$kind]).Count; byStatus = $byStatus; byProperty = $byProp; verdicts = $verdicts }
    }
    return $out
}

function Format-DaReport {
    <#
    .SYNOPSIS
        Markdown of the first-pass report: identity, coverage, measured cells, finding cards, open and inconclusive
        cells grouped by reason, limits and the validator verdict lines. Redacted before return.
    .PARAMETER Update
        Update-DaMatrix output after export.
    .PARAMETER Counts
        Get-DaCoverageCounts output.
    .PARAMETER ValidatorLines
        Verdict lines of assert-dynamic-audit-coverage.ps1 -Validate and -SelfTest.
    .PARAMETER GeneratedAt
        Timestamp printed in the header.
    #>
    param([Parameter(Mandatory)]$Update, [Parameter(Mandatory)]$Counts, [AllowEmptyCollection()][string[]]$ValidatorLines = @(), [string]$GeneratedAt = (Get-Date).ToUniversalTime().ToString('o'))
    $L = [System.Collections.Generic.List[string]]::new()
    $L.Add('# S4166 - first-pass dynamic audit report'); $L.Add('')
    $L.Add("Generated by ``scripts/devtest/dynamic-audit/da.ps1 -Verb Report`` at $GeneratedAt. Do not edit by hand; re-run the verb.")
    $L.Add('')
    $L.Add('**No complete successful check exists.** Every applicable cell that is still `open` keeps the audit incomplete, and every `measured` cell is judged against hypothesis budgets (version `h1`) that the owner has not approved yet.')
    $L.Add(''); $L.Add('## Stand and binaries'); $L.Add('')
    $builds = $Update.Runs | Group-Object { "$($_.variant)|$($_.build.versionName)|$($_.build.apkSha256)|$($_.device.avd)" }
    foreach ($g in $builds) { $p = $g.Name.Split('|'); $L.Add("- $($p[0]) $($p[1]) on $($p[3]), APK sha256 ``$($p[2])``, $($g.Count) run(s)") }
    if (-not @($builds).Count) { $L.Add('- no valid run') }
    $L.Add(''); $L.Add('## Runs'); $L.Add('')
    foreach ($r in $Update.Runs) { $L.Add("- ``$($r.runId)`` $($r.scene)$(if ($r.fault) { " fault $($r.fault)" }) $($r.variant) on $($r.device.avd)$(if ($r.cycles) { ", $($r.cycles) cycles" })$(if ($r.steadyMinutes) { ", $($r.steadyMinutes) measured min" })") }
    if ($Update.Selection.Invalid.Count) {
        $L.Add(''); $L.Add('Invalid runs (kept, never replaced silently, produce no cell):'); $L.Add('')
        foreach ($r in $Update.Selection.Invalid) { $L.Add("- ``$($r.RunId)`` $($r.Scene.scene) $($r.Scene.variant): $(ConvertTo-DaRedacted -Value ([string]$r.Scene.invalidReason))") }
    }
    if ($Update.Selection.Superseded.Count) { $L.Add(''); $L.Add("Superseded valid runs (an older run of the same scene, variant, fault and device): $($Update.Selection.Superseded.Count).") }
    $L.Add(''); $L.Add('## Coverage'); $L.Add('')
    foreach ($kind in $Counts.Keys) {
        $c = $Counts[$kind]
        $L.Add("- ``$kind``: $($c.total) cells; measured $($c.byStatus.measured), n/a $($c.byStatus['n/a']), open $($c.byStatus.open); verdicts pass $($c.verdicts.pass), fail $($c.verdicts.fail), inconclusive $($c.verdicts.inconclusive)")
        foreach ($p in $c.byProperty.Keys) { $v = $c.byProperty[$p]; $L.Add("  - $p - measured $($v.measured), n/a $($v['n/a']), open $($v.open)") }
    }
    $L.Add(''); $L.Add('Capability-bound cells stay open in this pass: a scene run drives atomic actions, not each sampled capability on its own.')
    $L.Add(''); $L.Add('## Measured cells'); $L.Add('')
    $meas = @($Update.Matrix['sceneCells'] | Where-Object { $_['status'] -eq 'measured' } | Sort-Object { $_['key'] })
    foreach ($c in $meas) { $L.Add("- ``$($c['key'])`` - $($c['verdict']); $($c['metric']) ($($c['unit'])), n $($c['n']), budget $($c['budget']) $($c['budgetVersion']), run ``$($c['run'])``; $($c['detail'])") }
    if (-not $meas.Count) { $L.Add('- none') }
    $L.Add(''); $L.Add('## Finding cards'); $L.Add('')
    foreach ($cd in $Update.Cards) {
        $L.Add("### $($cd.id) - $($cd.severity) $($cd.rung), $($cd.standing)"); $L.Add('')
        $L.Add("- Cell: ``$($cd.cell)``"); $L.Add("- Binary: $($cd.binary.variant) $($cd.binary.versionName), stand $($cd.stand.avd)")
        $L.Add("- Actions: $(@($cd.actions) -join ', ')"); $L.Add("- Expected: $($cd.expected)"); $L.Add("- Actual: $($cd.actual)")
        foreach ($n in @($cd.notes)) { $L.Add("- Note: $n") }
        $L.Add("- Next step: $($cd.nextStep)"); $L.Add('')
    }
    if (-not $Update.Cards.Count) { $L.Add('- no candidate finding was recorded by a valid run'); $L.Add('') }
    $L.Add('## Inconclusive cells'); $L.Add('')
    $inc = @($meas | Where-Object { $_['verdict'] -eq 'inconclusive' })
    foreach ($c in $inc) { $L.Add("- ``$($c['key'])`` - $($c['detail'])") }
    if (-not $inc.Count) { $L.Add('- none') }
    $L.Add(''); $L.Add('## Open cells by reason'); $L.Add('')
    foreach ($kind in @('cells', 'sceneCells')) {
        $groups = @($Update.Matrix[$kind] | Where-Object { $_['status'] -eq 'open' } | Group-Object { "$($_['reason'])|$($_['owner'])|$($_['nextStep'])" } | Sort-Object Count -Descending)
        foreach ($g in $groups) {
            $p = $g.Name.Split('|')
            $L.Add("- ``$kind``, $($g.Count) cell(s): $($p[0]) - owner $($p[1]); next step: $($p[2])")
        }
    }
    if ($Update.Unmatched.Count) {
        $L.Add(''); $L.Add('Measurements with no matrix cell for the context the stand used (not forced into another cell):'); $L.Add('')
        foreach ($u in $Update.Unmatched) { $L.Add("- ``$u``") }
    }
    $L.Add(''); $L.Add('## Limits'); $L.Add('')
    foreach ($t in @(
            'Debug builds: the debuggable build carries overhead a release build does not, so absolute latencies are upper-biased.'
            'Emulator only (Pixel_9 API 35 and the Wear round AVDs); no physical phone, and minSdk 26 cells are open.'
            'One run per scene, variant and device; 20 cycles with 3 warm-up cycles; no soak. Thresholds are hypotheses until the owner approves a baseline.'
            'The owner is the only participant of the understanding protocol; U cells stay open until external participants run it.'
            'The vr pass needs the headset (no OpenXR FOCUSED on an emulator) and was not run; the Windows contour has no product in this repository and was not run.'
            'The stand drives English, touch and one keyboard burst; every other locale, input and state cell stays open.'
            'Neighbour impact (S) is not measured by any scene run.'
        )) { $L.Add("- $t") }
    $L.Add(''); $L.Add('## Validator'); $L.Add('')
    foreach ($v in $ValidatorLines) { $L.Add("- ``$v``") }
    if (-not $ValidatorLines.Count) { $L.Add('- not run') }
    $L.Add('')
    return [string](ConvertTo-DaRedacted -Value ($L -join "`n"))
}

function Invoke-DaReport {
    <#
    .SYNOPSIS
        Report verb: reads the runs, updates the matrix, exports the redacted aggregates, runs the coverage validator
        and writes reports/first-pass-report.md; returns {Code; Reason; Path}.
    .DESCRIPTION
        Code 2 when the audit data cannot be read; otherwise the code of the coverage validator's -Validate run
        (1 while any cell is open, which is the expected first-pass outcome), with the -SelfTest verdict printed beside it.
    .PARAMETER RepoRoot
        Repository root.
    .PARAMETER RunsRoot
        The stand's runs directory.
    .PARAMETER SkipValidator
        Do not run the coverage validator (contract suite only).
    #>
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$RunsRoot, [switch]$SkipValidator)
    $codes = Get-DaExitCode
    $auditRel = 'PLAN/S4166_deep-dynamic-audit/audit'
    $auditRoot = Join-Path $RepoRoot $auditRel
    $matrixPath = Join-Path $auditRoot 'matrix.json'
    $budgetsPath = Join-Path $auditRoot 'budgets.json'
    if (-not (Test-Path -LiteralPath $matrixPath) -or -not (Test-Path -LiteralPath $budgetsPath)) { return [pscustomobject]@{ Code = $codes.Refused; Reason = "audit data missing under $auditRel (run -Verb BuildInventory)"; Path = $null } }
    $matrix = Get-Content -LiteralPath $matrixPath -Raw | ConvertFrom-Json -AsHashtable -Depth 64
    $budgets = Get-Content -LiteralPath $budgetsPath -Raw | ConvertFrom-Json -AsHashtable
    if ([int]$matrix['schemaVersion'] -ne 1) { return [pscustomobject]@{ Code = $codes.Refused; Reason = "matrix.json schemaVersion $($matrix['schemaVersion']) is not 1"; Path = $null } }
    $records = Get-DaRunRecords -RunsRoot $RunsRoot
    $upd = Update-DaMatrix -Matrix $matrix -Records $records -BudgetVersion ([string]$budgets['version']) -EvidenceRel "$auditRel/evidence"
    $exp = Export-DaAggregate -AuditRoot $auditRoot -RepoRoot $RepoRoot -Update $upd
    $lines = [System.Collections.Generic.List[string]]::new(); $vCode = $codes.Ok
    if (-not $SkipValidator) {
        $gate = Join-Path $RepoRoot 'scripts/quality/assert-dynamic-audit-coverage.ps1'
        foreach ($mode in @('-Validate', '-SelfTest')) {
            $o = & pwsh -NoProfile -File $gate $mode 2>&1
            $c = $LASTEXITCODE
            if ($mode -eq '-Validate') { $vCode = $c }
            $last = @($o | ForEach-Object { "$_" } | Where-Object { $_ -match '^assert-dynamic-audit-coverage: ' } | Select-Object -Last 1)
            $lines.Add("$mode exit $c - $(if ($last.Count) { $last[0] } else { 'assert-dynamic-audit-coverage: no verdict line' })")
        }
    }
    $counts = Get-DaCoverageCounts -Matrix $matrix
    $reportPath = Join-Path $RepoRoot 'PLAN/S4166_deep-dynamic-audit/reports/first-pass-report.md'
    [void][System.IO.Directory]::CreateDirectory((Split-Path -Parent $reportPath))
    $md = Format-DaReport -Update $upd -Counts $counts -ValidatorLines $lines.ToArray()
    [System.IO.File]::WriteAllText($reportPath, $md + "`n", [System.Text.UTF8Encoding]::new($false))
    $code = if ($SkipValidator) { $codes.Ok } else { [int]$vCode }
    return [pscustomobject]@{ Code = $code; Reason = "$($upd.Runs.Count) run(s) used, $($upd.Applied) scene cell(s) measured, $($upd.Cards.Count) finding card(s), $($exp.EvidenceCount) evidence file(s), $($upd.Unmatched.Count) unmatched; validator: $($lines -join ' / '); report $reportPath"; Path = $reportPath }
}
