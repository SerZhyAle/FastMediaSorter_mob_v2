<#
.SYNOPSIS
  Pure flow-selection functions of the Maestro runner (S4249): header markers, run order, shard
  partition, attempt records and the shard-result merge.

.DESCRIPTION
  Dot-sourced by maestro/run-tests.ps1 and maestro/merge-shard-results.ps1, and by the hermetic
  contract suite scripts/devtest/maestro-run-tests.tests/Run-Tests.ps1. Nothing here calls adb,
  Maestro or the network, and nothing writes a file: every decision the runner takes about WHICH
  flows run, in WHAT order, on WHICH shard and how a retried or advisory flow is recorded lives here,
  so it can be pinned without a device.

  Exit codes: none - a library defines functions and returns to its caller.
#>

# ---------- header markers ----------
# A flow declares a requirement in its own header, `# maestro-requires: home-role`, because the
# alternative is a list of file names inside the runner that drifts from the flows it names (S2720).
function Get-FlowRequirements {
    param([System.IO.FileInfo]$FlowFile)
    $head = Get-Content -Path $FlowFile.FullName -TotalCount 20 -ErrorAction SilentlyContinue
    $tokens = @()
    foreach ($line in @($head)) {
        $m = [regex]::Match($line, '^\s*#\s*maestro-requires:\s*([a-z0-9-]+)\s*$')
        if ($m.Success) { $tokens += $m.Groups[1].Value }
    }
    return , $tokens
}

# `# maestro-advisory: network` marks a flow whose failure without an application crash does not
# block a release (owner decision 2026-10-14, S4249 ADR-2). Same 20-line window as the requirement
# reader, so a marker the runner honours is never one the reader of the other marker misses.
function Get-FlowAdvisoryKind {
    param([System.IO.FileInfo]$FlowFile)
    $head = Get-Content -Path $FlowFile.FullName -TotalCount 20 -ErrorAction SilentlyContinue
    foreach ($line in @($head)) {
        $m = [regex]::Match($line, '^\s*#\s*maestro-advisory:\s*([a-z0-9-]+)\s*$')
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

# ---------- run order ----------
# Previously failed flows first, advisory flows next, the rest last; each group keeps its incoming
# order. In release 45 the one failing flow sat last in the list and surfaced at minute 81 (S4249 §4).
function Sort-FlowsForRun {
    param([object[]]$Flows, [string[]]$PreviouslyFailed = @())
    $failedSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($name in @($PreviouslyFailed)) { if ($name) { [void]$failedSet.Add([string]$name) } }
    $first = [System.Collections.Generic.List[object]]::new()
    $advisory = [System.Collections.Generic.List[object]]::new()
    $rest = [System.Collections.Generic.List[object]]::new()
    foreach ($flow in @($Flows)) {
        if ($null -eq $flow) { continue }
        if ($failedSet.Contains($flow.Name)) { $first.Add($flow) }
        elseif (Get-FlowAdvisoryKind -FlowFile $flow) { $advisory.Add($flow) }
        else { $rest.Add($flow) }
    }
    return , @(@($first) + @($advisory) + @($rest))
}

# ---------- shard partition ----------
# Round-robin over the ORDERED list, so the early-run flows (previously failed, advisory) spread
# across shards instead of all landing on shard 1.
function Get-ShardSubset {
    param([object[]]$Flows, [int]$Index, [int]$Count)
    if ($Count -lt 1) { throw "shard count must be at least 1, got $Count" }
    if ($Index -lt 1 -or $Index -gt $Count) { throw "shard index $Index is outside 1..$Count" }
    $subset = [System.Collections.Generic.List[object]]::new()
    $all = @($Flows)
    for ($i = 0; $i -lt $all.Count; $i++) {
        if ((($i % $Count) + 1) -eq $Index) { $subset.Add($all[$i]) }
    }
    return , @($subset)
}

# ---------- attempt record ----------
# Folds one or two attempts into the flow entry. status/pass/log mean the FINAL attempt; the first
# attempt stays visible so a flow that passed only on its retry is reported as flaky instead of as a
# clean pass - three launcher flows did exactly that every sweep of release 45 (S4249 §4, S4250).
function Resolve-FlowAttempts {
    param($First, $Retry, [string]$Advisory)
    $final = if ($null -ne $Retry) { $Retry } else { $First }
    $attempts = if ($null -ne $Retry) { 2 } else { 1 }
    $flaky = ($null -ne $Retry) -and ($First.status -in @('fail', 'execError')) -and ($Retry.status -eq 'pass')
    return [ordered]@{
        flow               = $final.flow
        status             = $final.status
        pass               = ($final.status -eq 'pass')
        log                = $final.log
        attempts           = $attempts
        firstAttemptStatus = $First.status
        firstAttemptLog    = $First.log
        flaky              = [bool]$flaky
        advisory           = $(if ($Advisory) { $Advisory } else { $null })
    }
}

# Names whose final status is a judged or transport failure - the next run's "previously failed".
function Get-FailedFlowNames {
    param([object[]]$Flows)
    return , @(@($Flows) | Where-Object { $_ -and ($_.status -in @('fail', 'execError')) } | ForEach-Object { [string]$_.flow })
}

function Get-LastFailedFileName {
    param([int]$ShardIndex = 1, [int]$ShardCount = 1)
    if ($ShardCount -le 1) { return 'last-failed.json' }
    return ('last-failed.shard{0}of{1}.json' -f $ShardIndex, $ShardCount)
}

# ---------- shard merge ----------
# Joins N shard results into one object of the runner's own shape. Every structural defect throws:
# a shard missing, an index outside 1..N or seen twice, or a flow name present in two shards. A
# parallel branch silently dropping out of the verdict is the risk this refusal exists for (S4249 §7).
function Merge-ShardResults {
    param([object[]]$Results, [int]$ExpectedCount)
    $all = @($Results | Where-Object { $null -ne $_ })
    if ($ExpectedCount -lt 1) { throw "expected shard count must be at least 1, got $ExpectedCount" }
    if ($all.Count -ne $ExpectedCount) { throw "expected $ExpectedCount shard result(s), got $($all.Count)" }

    $seenIndex = @{}
    $seenFlow = @{}
    $flows = [System.Collections.Generic.List[object]]::new()
    $shards = [System.Collections.Generic.List[object]]::new()
    $launcherModes = @()
    foreach ($r in ($all | Sort-Object { [int]$_.shard.index })) {
        if ($null -eq $r.shard) { throw 'a shard result carries no shard field' }
        $idx = [int]$r.shard.index
        if ([int]$r.shard.count -ne $ExpectedCount) { throw "shard $idx declares count $($r.shard.count), expected $ExpectedCount" }
        if ($idx -lt 1 -or $idx -gt $ExpectedCount) { throw "shard index $idx is outside 1..$ExpectedCount" }
        if ($seenIndex.ContainsKey($idx)) { throw "shard index $idx appears twice" }
        $seenIndex[$idx] = $true
        foreach ($f in @($r.flows)) {
            $name = [string]$f.flow
            if ($seenFlow.ContainsKey($name)) { throw "flow '$name' appears in shard $($seenFlow[$name]) and shard $idx" }
            $seenFlow[$name] = $idx
            $flows.Add($f)
        }
        $launcherModes += [string]$r.launcherMode
        $shards.Add([ordered]@{ index = $idx; device = $r.device; total = [int]$r.total; failed = [int]$r.failed; skipped = [int]$r.skipped })
    }

    $skipped = @($flows | Where-Object { $_.status -eq 'skip' }).Count
    $failed = @($flows | Where-Object { -not $_.pass -and $_.status -ne 'skip' }).Count
    $anyExec = @($flows | Where-Object { $_.status -eq 'execError' }).Count -gt 0
    $distinctModes = @($launcherModes | Select-Object -Unique)
    $launcherMode = if ($distinctModes.Count -eq 1) { $distinctModes[0] } else { 'mixed' }
    $reason = if ($anyExec) { 'execution error (no device / runtime) in one or more flows' } else { $null }
    return [ordered]@{
        pass         = ($failed -eq 0)
        total        = $flows.Count
        failed       = $failed
        skipped      = $skipped
        reason       = $reason
        launcherMode = $launcherMode
        flows        = @($flows)
        shards       = @($shards)
    }
}

# Same precedence as the runner: execution error (4) outranks a failure (3), both outrank pass.
function Get-MergedExitCode {
    param($Merged)
    if (@($Merged.flows | Where-Object { $_.status -eq 'execError' }).Count -gt 0) { return 4 }
    if (@($Merged.flows | Where-Object { $_.status -eq 'fail' }).Count -gt 0) { return 3 }
    return 0
}
