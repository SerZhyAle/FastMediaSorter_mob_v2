#requires -Version 7.0
<#
.SYNOPSIS
    S4249 contract suite for the Maestro runner's pure half: header markers, run order, shard
    partition, attempt records, the S4413 CLI start timings and the shard-result merge.

# Subject: maestro/run-tests.ps1, maestro/lib/flow-selection.ps1, maestro/merge-shard-results.ps1

.DESCRIPTION
    Hermetic: dot-sources maestro/lib/flow-selection.ps1 and runs maestro/merge-shard-results.ps1
    on fixture JSON written to a scratch folder under the system temp directory. No adb call, no
    Maestro binary, no device. The suite lives under scripts/ because scripts/quality/run-script-suites.ps1
    discovers suites there; the Subject line ties it to the maestro/ files it pins.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "maestro/lib/flow-selection.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$lib = Join-Path $repoRoot 'maestro/lib/flow-selection.ps1'
$mergeCli = Join-Path $repoRoot 'maestro/merge-shard-results.ps1'
$runner = Join-Path $repoRoot 'maestro/run-tests.ps1'

foreach ($required in @($lib, $mergeCli, $runner)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "FAIL | not found: $required" -ForegroundColor Red
        exit 1
    }
}
. $lib

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ('s4249-runner-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null

function New-FlowFixture {
    param([string]$Name, [string[]]$HeaderLines = @(), [int]$PadBefore = 0)
    $lines = @('appId: com.sza.fastmediasorter.debug', '---')
    for ($i = 0; $i -lt $PadBefore; $i++) { $lines += "# filler line $i" }
    $lines += $HeaderLines
    $lines += '- launchApp'
    $path = Join-Path $scratch $Name
    Set-Content -LiteralPath $path -Value $lines -Encoding utf8NoBOM
    return Get-Item -LiteralPath $path
}

function Invoke-Merge {
    param([object[]]$Shards, [int]$ExpectedCount, [string]$Tag)
    $inputs = @()
    $i = 0
    foreach ($shard in $Shards) {
        $i++
        $p = Join-Path $scratch ("{0}_in{1}.json" -f $Tag, $i)
        if ($shard -is [string]) { Set-Content -LiteralPath $p -Value $shard -Encoding utf8NoBOM }
        else { Set-Content -LiteralPath $p -Value ($shard | ConvertTo-Json -Depth 6) -Encoding utf8NoBOM }
        $inputs += $p
    }
    $out = Join-Path $scratch "$Tag`_merged.json"
    $null = & pwsh -NoProfile -File $mergeCli -Inputs ($inputs -join ',') -Output $out -ExpectedCount $ExpectedCount -LastFailedDir $scratch 2>&1
    $code = $LASTEXITCODE
    $merged = if (Test-Path -LiteralPath $out) { Get-Content -LiteralPath $out -Raw | ConvertFrom-Json } else { $null }
    return [pscustomobject]@{ Code = $code; Merged = $merged }
}

function New-ShardResult {
    param([int]$Index, [int]$Count, [object[]]$Flows, [string]$Device = 'emulator-5554')
    $failed = @($Flows | Where-Object { -not $_.pass -and $_.status -ne 'skip' }).Count
    return [ordered]@{ pass = ($failed -eq 0); total = $Flows.Count; failed = $failed; skipped = 0; reason = $null
                       launcherMode = 'off'; shard = [ordered]@{ index = $Index; count = $Count }; device = $Device; flows = $Flows }
}

function New-FlowEntry {
    param([string]$Name, [string]$Status = 'pass', [bool]$Flaky = $false, [string]$Advisory)
    return [ordered]@{ flow = $Name; pass = ($Status -eq 'pass'); status = $Status; skipReason = $null; log = "x/$Name.log"
                       attempts = $(if ($Flaky) { 2 } else { 1 }); firstAttemptStatus = $(if ($Flaky) { 'fail' } else { $Status })
                       firstAttemptLog = "x/$Name.log"; flaky = $Flaky; advisory = $(if ($Advisory) { $Advisory } else { $null }) }
}

try {
    # --- marker parsing -----------------------------------------------------------------------
    $marked = New-FlowFixture -Name 'stream.yaml' -HeaderLines @('# maestro-advisory: network')
    $plain = New-FlowFixture -Name 'plain.yaml'
    $late = New-FlowFixture -Name 'late.yaml' -HeaderLines @('# maestro-advisory: network') -PadBefore 25

    Assert-Equal -Label 'marker present: the advisory token is read' -Expected 'network' -Actual (Get-FlowAdvisoryKind -FlowFile $marked)
    Assert-Equal -Label 'marker absent: no token' -Expected '' -Actual (Get-FlowAdvisoryKind -FlowFile $plain)
    Assert-Equal -Label 'marker beyond line 20: not read, same window as maestro-requires' -Expected '' -Actual (Get-FlowAdvisoryKind -FlowFile $late)

    # --- ordering -----------------------------------------------------------------------------
    $a = New-FlowFixture -Name 'a.yaml'
    $b = New-FlowFixture -Name 'b.yaml'
    $c = New-FlowFixture -Name 'c.yaml'
    $ordered = Sort-FlowsForRun -Flows @($a, $marked, $b, $c, $plain) -PreviouslyFailed @('c.yaml')
    Assert-Equal -Label 'ordering: previously failed first, advisory next, the rest keep their order' `
        -Expected 'c.yaml,stream.yaml,a.yaml,b.yaml,plain.yaml' -Actual (($ordered | ForEach-Object { $_.Name }) -join ',')
    $orderedNoHistory = Sort-FlowsForRun -Flows @($a, $b, $c) -PreviouslyFailed @()
    Assert-Equal -Label 'ordering: no history and no marker keeps the incoming order' `
        -Expected 'a.yaml,b.yaml,c.yaml' -Actual (($orderedNoHistory | ForEach-Object { $_.Name }) -join ',')

    # --- attempt records ----------------------------------------------------------------------
    $first = [ordered]@{ flow = 'f.yaml'; status = 'fail'; pass = $false; log = 'l1' }
    $retryPass = [ordered]@{ flow = 'f.yaml'; status = 'pass'; pass = $true; log = 'l2' }
    $flakyEntry = Resolve-FlowAttempts -First $first -Retry $retryPass -Advisory $null
    Assert-Equal -Label 'flaky entry: final pass, two attempts, first attempt kept, flaky true' `
        -Expected 'pass/True/2/fail/l1/l2/True' `
        -Actual ("{0}/{1}/{2}/{3}/{4}/{5}/{6}" -f $flakyEntry.status, $flakyEntry.pass, $flakyEntry.attempts, $flakyEntry.firstAttemptStatus, $flakyEntry.firstAttemptLog, $flakyEntry.log, $flakyEntry.flaky)
    $cleanEntry = Resolve-FlowAttempts -First $retryPass -Retry $null -Advisory 'network'
    Assert-Equal -Label 'clean pass: one attempt, not flaky, advisory carried' `
        -Expected '1/False/network' -Actual ("{0}/{1}/{2}" -f $cleanEntry.attempts, $cleanEntry.flaky, $cleanEntry.advisory)
    $retryFail = [ordered]@{ flow = 'f.yaml'; status = 'execError'; pass = $false; log = 'l3' }
    $hardEntry = Resolve-FlowAttempts -First $first -Retry $retryFail -Advisory $null
    Assert-Equal -Label 'failed retry: final status is the retry, not flaky' `
        -Expected 'execError/False/False' -Actual ("{0}/{1}/{2}" -f $hardEntry.status, $hardEntry.pass, $hardEntry.flaky)

    $timedFirst = [ordered]@{ flow = 't.yaml'; status = 'execError'; pass = $false; log = 'l1'; preDeviceSeconds = 62.0; startSeconds = 95.4; transportWaitSeconds = 0.3 }
    $timedRetry = [ordered]@{ flow = 't.yaml'; status = 'pass'; pass = $true; log = 'l2'; preDeviceSeconds = 1.0; startSeconds = 7.5; transportWaitSeconds = 4.2 }
    $timedEntry = Resolve-FlowAttempts -First $timedFirst -Retry $timedRetry -Advisory $null
    Assert-Equal -Label 'S4413 timings: flat fields are the final attempt, attemptTimings keeps both' `
        -Expected '1/7.5/4.2/2/95.4' `
        -Actual ("{0}/{1}/{2}/{3}/{4}" -f $timedEntry.preDeviceSeconds, $timedEntry.startSeconds, $timedEntry.transportWaitSeconds, @($timedEntry.attemptTimings).Count, @($timedEntry.attemptTimings)[0].startSeconds)
    Assert-Equal -Label 'S4413 timings: an attempt record without timing fields yields nulls' `
        -Expected 'True/True' -Actual ("{0}/{1}" -f ($null -eq $cleanEntry.startSeconds), ($null -eq $cleanEntry.preDeviceSeconds))

    # --- CLI start timings (S4413) --------------------------------------------------------------
    $logLines = @(
        '14:12:02.100 [ INFO] maestro.cli.report.TestDebugReporter.logSystemInfo: Debug output path: x',
        '14:12:02.187 [ INFO] MAESTRO.logSystemInfo: ---------------------',
        '14:12:03.000 [DEBUG] io.micrometer.common.util.internal.logging.InternalLoggerFactory.newDefaultFactory: x',
        '14:13:04.187 [ INFO] maestro.cli.command.TestCommand.runShardSuite: [shard 1] Selected device emulator-5556 using port 1',
        '	at some.stack.Frame(Running flow mention inside a trace without a timestamp)',
        '14:13:37.687 [ INFO] maestro.cli.runner.TestRunner.runSingle$lambda$0: Running flow app_launch.yaml...',
        '14:14:00.000 [ INFO] maestro.cli.runner.TestRunner.runSingle$lambda$0: Running flow other.yaml...')
    $t = Get-MaestroStartTimings -Lines $logLines
    Assert-Equal -Label 'start timings: anchored on the last logSystemInfo line, first Selected device and Running flow' `
        -Expected '62/95.5' -Actual ("{0}/{1}" -f $t.preDeviceSeconds, $t.startSeconds)
    $noFlow = Get-MaestroStartTimings -Lines @($logLines[0..3])
    Assert-Equal -Label 'start timings: a run that never reached a flow reports a null start, not zero' `
        -Expected '62/True' -Actual ("{0}/{1}" -f $noFlow.preDeviceSeconds, ($null -eq $noFlow.startSeconds))
    $noAnchor = Get-MaestroStartTimings -Lines @('garbage', 'Running flow x')
    Assert-Equal -Label 'start timings: no system-info line yields both null' `
        -Expected 'True/True' -Actual ("{0}/{1}" -f ($null -eq $noAnchor.preDeviceSeconds), ($null -eq $noAnchor.startSeconds))
    $midnight = Get-MaestroStartTimings -Lines @('23:59:50.000 [ INFO] MAESTRO.logSystemInfo: ---', '00:00:01.000 [ INFO] x: Selected device e', '00:00:05.500 [ INFO] x: Running flow f')
    Assert-Equal -Label 'start timings: an interval across midnight stays positive' `
        -Expected '11/15.5' -Actual ("{0}/{1}" -f $midnight.preDeviceSeconds, $midnight.startSeconds)
    Assert-Equal -Label 'median: odd count, nulls ignored' -Expected '7.5' -Actual (Get-MedianValue -Values @(95.4, $null, 7.5, 6.0))
    Assert-Equal -Label 'median: even count averages the middle pair' -Expected '8' -Actual (Get-MedianValue -Values @(6, 7, 9, 100))
    Assert-Equal -Label 'median: no values yields null' -Expected 'True' -Actual ($null -eq (Get-MedianValue -Values @($null)))

    # --- shard partition ----------------------------------------------------------------------
    $list = @(1..10 | ForEach-Object { "flow$_" })
    $union = @()
    foreach ($i in 1..3) { $shardPart = Get-ShardSubset -Flows $list -Index $i -Count 3; $union += $shardPart }
    Assert-Equal -Label 'partition: the union of all shards equals the input with no duplicates' `
        -Expected (($list | Sort-Object) -join ',') -Actual (($union | Sort-Object) -join ',')
    Assert-Equal -Label 'partition: union size equals input size' -Expected 10 -Actual @($union | Select-Object -Unique).Count
    Assert-Equal -Label 'partition: round-robin keeps order inside a shard' `
        -Expected 'flow2,flow5,flow8' -Actual ((Get-ShardSubset -Flows $list -Index 2 -Count 3) -join ',')
    Assert-Equal -Label 'partition: count 1 returns the list unchanged' `
        -Expected ($list -join ',') -Actual ((Get-ShardSubset -Flows $list -Index 1 -Count 1) -join ',')
    $threw = $false
    try { $null = Get-ShardSubset -Flows $list -Index 4 -Count 3 } catch { $threw = $true }
    Assert-Equal -Label 'partition: an index outside the range throws' -Expected $true -Actual $threw
    Assert-Equal -Label 'last-failed file: one shard keeps the sequential name' -Expected 'last-failed.json' -Actual (Get-LastFailedFileName)
    Assert-Equal -Label 'last-failed file: a shard of three writes its own name' -Expected 'last-failed.shard2of3.json' -Actual (Get-LastFailedFileName -ShardIndex 2 -ShardCount 3)

    # --- merge tool ---------------------------------------------------------------------------
    $s1 = New-ShardResult -Index 1 -Count 2 -Flows @((New-FlowEntry -Name 'a.yaml'), (New-FlowEntry -Name 'stream.yaml' -Status 'fail' -Advisory 'network'))
    $s2 = New-ShardResult -Index 2 -Count 2 -Flows @((New-FlowEntry -Name 'b.yaml' -Flaky $true)) -Device 'emulator-5556'

    $missing = Invoke-Merge -Shards @($s1) -ExpectedCount 2 -Tag 'missing'
    Assert-Equal -Label 'merge: a missing shard exits 2' -Expected 2 -Actual $missing.Code

    $dupFlow = New-ShardResult -Index 2 -Count 2 -Flows @((New-FlowEntry -Name 'a.yaml'))
    $duplicated = Invoke-Merge -Shards @($s1, $dupFlow) -ExpectedCount 2 -Tag 'dupflow'
    Assert-Equal -Label 'merge: a duplicated flow exits 2' -Expected 2 -Actual $duplicated.Code

    $wrongIndex = New-ShardResult -Index 1 -Count 2 -Flows @((New-FlowEntry -Name 'b.yaml'))
    $badSet = Invoke-Merge -Shards @($s1, $wrongIndex) -ExpectedCount 2 -Tag 'badset'
    Assert-Equal -Label 'merge: a wrong index set exits 2' -Expected 2 -Actual $badSet.Code

    $unreadable = Invoke-Merge -Shards @($s1, '{ not json') -ExpectedCount 2 -Tag 'unreadable'
    Assert-Equal -Label 'merge: an unreadable shard exits 2' -Expected 2 -Actual $unreadable.Code

    $good = Invoke-Merge -Shards @($s1, $s2) -ExpectedCount 2 -Tag 'good'
    Assert-Equal -Label 'merge: one failed flow exits 3' -Expected 3 -Actual $good.Code
    $mergedFlaky = @($good.Merged.flows | Where-Object { $_.flow -eq 'b.yaml' })[0]
    $mergedAdvisory = @($good.Merged.flows | Where-Object { $_.flow -eq 'stream.yaml' })[0]
    Assert-Equal -Label 'merge: keeps the flaky and advisory fields of each flow' `
        -Expected 'True/network' -Actual ("{0}/{1}" -f $mergedFlaky.flaky, $mergedAdvisory.advisory)
    Assert-Equal -Label 'merge: runner shape with shards array' `
        -Expected '3/1/2/emulator-5556' -Actual ("{0}/{1}/{2}/{3}" -f $good.Merged.total, $good.Merged.failed, @($good.Merged.shards).Count, @($good.Merged.shards)[1].device)
    $lastFailed = Get-Content -LiteralPath (Join-Path $scratch 'last-failed.json') -Raw | ConvertFrom-Json
    Assert-Equal -Label 'merge: last-failed.json lists the failed flow' -Expected 'stream.yaml' -Actual (@($lastFailed) -join ',')

    $s1Exec = New-ShardResult -Index 1 -Count 2 -Flows @((New-FlowEntry -Name 'a.yaml' -Status 'execError'))
    $exec = Invoke-Merge -Shards @($s1Exec, $s2) -ExpectedCount 2 -Tag 'exec'
    Assert-Equal -Label 'merge: an execution error exits 4' -Expected 4 -Actual $exec.Code

    $s1Clean = New-ShardResult -Index 1 -Count 1 -Flows @((New-FlowEntry -Name 'a.yaml'))
    $clean = Invoke-Merge -Shards @($s1Clean) -ExpectedCount 1 -Tag 'clean'
    Assert-Equal -Label 'merge: a single clean shard exits 0' -Expected 0 -Actual $clean.Code

    # --- the runner's selection with no device --------------------------------------------------
    $listOut = & pwsh -NoProfile -File $runner -Suite all -ListFlows -ShardIndex 4 -ShardCount 3 2>&1
    Assert-Equal -Label 'runner: a shard index above the count exits 1' -Expected 1 -Actual $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "maestro-run-tests.tests: $(if ($script:failed -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:passed + $script:failed) skipped=0)"
if ($script:failed -gt 0) { exit 1 }
exit 0
