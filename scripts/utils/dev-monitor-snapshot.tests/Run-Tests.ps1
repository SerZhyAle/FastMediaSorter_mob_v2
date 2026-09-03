#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/utils/dev-monitor-snapshot.ps1 and the terminal render that consumes
    it, scripts/utils/monitor-spec-queue.ps1 (S2406).

.DESCRIPTION
    Hermetic: a throwaway repo root under temp/S2406/snapshot-tests/<run>/ carries one lease, one
    held lock with one queued ticket, one run journal, one stop flag and a release queue; the chat
    lives under the same root through FMS_AGENT_CHAT_ROOT and is written as agent `agent-fx`.

    Pinned (strategic section 11 items 3, 6, 7 and 10):
      - schema 1, durationMs present and under the 1000 ms budget;
      - leases: id, holder nickname, liveness of a fresh heartbeat is not stale;
      - locks: every domain of the table, the held one with its queue, no legacy rows without files;
      - agents: one row per agent, the newest message is its kind, the newest `phase` message is its
        phase, the lease is joined by session id;
      - chat tail newest first, alive findings judged by scope, run journal counts and tail,
        stop flag, next-up rows of the current package only with taken / blocked marks;
      - one call writes nothing: the fixture listing is identical before and after;
      - an empty root returns every array, empty, without throwing;
      - monitor-spec-queue.ps1 -Json parses to the same object; the text render prints every section.

    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - the fixture root could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$library = Join-Path $repoRoot 'scripts/utils/dev-monitor-snapshot.ps1'
$monitor = Join-Path $repoRoot 'scripts/utils/monitor-spec-queue.ps1'
$chatCli = Join-Path $repoRoot 'scripts/utils/agent-chat.ps1'

$script:pass = 0
$script:fail = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}

function Get-Listing([string]$root) {
    return (@(Get-ChildItem -LiteralPath $root -Recurse -File -Force | Sort-Object FullName |
            ForEach-Object { '{0}|{1}|{2}' -f $_.FullName, $_.Length, $_.LastWriteTimeUtc.Ticks }) -join "`n")
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$fixture = Join-Path $repoRoot "temp/S2406/snapshot-tests/$run"
$fixtureRel = "temp/S2406/snapshot-tests/$run"
$savedChatRoot = $env:FMS_AGENT_CHAT_ROOT
$savedAgentId = $env:FMS_AGENT_ID
$savedAgentName = $env:FMS_AGENT_NAME
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
try {
    foreach ($d in @('temp/SPEC-TICKET.LEASES', 'temp/CODE.PHONE.QUEUE', 'temp/spec-queue', 'PLAN', 'scope', 'chat')) {
        New-Item -ItemType Directory -Path (Join-Path $fixture $d) -Force | Out-Null
    }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText((Join-Path $fixture 'temp/SPEC-TICKET.LEASES/S0001.json'),
        ('{{"schema":1,"id":"S0001","sessionId":"agent-fx","host":"FX","pid":1,"reason":"//spec-all","claimedAt":{0},"transcriptPath":"","lastSeenAt":{0}}}' -f $nowMs), $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'temp/CODE.PHONE.LOCK'),
        ('{{"schema":2,"lockType":"Code.Phone","pid":2,"acquiredAt":{0},"reason":"/spec-dev S0001 step 1","host":"FX","sessionId":"agent-fx","transcriptPath":""}}' -f ($nowMs - 120000)), $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'temp/CODE.PHONE.QUEUE/0001-agent-q.json'),
        ('{{"seq":1,"sessionId":"agent-q","reason":"/spec-dev S0009 edit","enqueuedAt":{0},"lastSeenAt":{1}}}' -f ($nowMs - 300000), ($nowMs - 10000)), $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'temp/spec-queue/runs-a.jsonl'), (@(
        '{"id":"S0101","model":"opus","statusBefore":"Tactical","statusAfter":"Tactical","moved":false,"outcome":"ok","exitCode":0,"minutes":7,"finishedAt":"2026-09-02T10:03:42"}',
        '{"id":"S0102","model":"opus","statusBefore":"Tactical","statusAfter":"BlockNeedUserTest","moved":true,"outcome":"ok","exitCode":0,"minutes":62,"finishedAt":"2026-09-02T11:05:31"}',
        '{"id":"S0103","model":"sonnet","statusBefore":"Approved","statusAfter":"Approved","moved":false,"outcome":"timeout","exitCode":1,"minutes":90,"finishedAt":"2026-09-02T12:40:00"}'
    ) -join "`n") + "`n", $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'temp/STOP-SPEC-QUEUE'), 'stop', $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'PLAN/RELEASE_QUEUE.md'), (@(
        '# Release Queue',
        'rel  ticket                                   changed     status',
        '35   S0035_old-thing                          2026-09-01  Approved',
        '# release 36 - fixture',
        '# 36.0 Organizational work',
        '36   S0001_first-thing                        2026-09-02  Approved          [taken 23:01, //spec-all, agent-fx]',
        '36   S0002_blocked-thing                      2026-09-02  BlockExternal',
        '36   S0003_plain-thing                        2026-09-02  Draft',
        '',
        'current-next-release: 36'
    ) -join "`n") + "`n", $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'scope/marker.txt'), 'scope', $utf8)

    $env:FMS_AGENT_CHAT_ROOT = (Join-Path $fixture 'chat')
    $env:FMS_AGENT_ID = 'agent-fx'
    $env:FMS_AGENT_NAME = 'fixture-fox-0902-0000'
    Remove-Item Env:FMS_AGENT_CHAT_CAP_PROGRESS -ErrorAction SilentlyContinue
    Remove-Item Env:FMS_AGENT_CHAT_CAP_FINDINGS -ErrorAction SilentlyContinue
    & $pwshExe -NoProfile -File $chatCli -Verb Post -Kind phase -Ticket S0001 -Phase 02 -Note 'writers' *> $null
    if ($LASTEXITCODE -ne 0) { throw "phase post failed ($LASTEXITCODE)" }
    Start-Sleep -Milliseconds 30
    & $pwshExe -NoProfile -File $chatCli -Verb Post -Kind lock -Ticket S0001 -Note 'Code.Phone acquired' *> $null
    if ($LASTEXITCODE -ne 0) { throw "lock post failed ($LASTEXITCODE)" }
    & $pwshExe -NoProfile -File $chatCli -Verb Post -Finding -Kind check -Topic 'check:fixture' -Scope "$fixtureRel/scope" -Note 'fixture check' -EvidenceCommand 'none' -EvidenceExit 0 *> $null
    if ($LASTEXITCODE -ne 0) { throw "finding post failed ($LASTEXITCODE)" }
    . $library
}
catch {
    Write-Host "dev-monitor-snapshot tests: fixture could not be prepared - $_" -ForegroundColor Red
    if ($null -ne $savedChatRoot) { $env:FMS_AGENT_CHAT_ROOT = $savedChatRoot } else { Remove-Item Env:FMS_AGENT_CHAT_ROOT -ErrorAction SilentlyContinue }
    if ($null -ne $savedAgentId) { $env:FMS_AGENT_ID = $savedAgentId } else { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
    if ($null -ne $savedAgentName) { $env:FMS_AGENT_NAME = $savedAgentName } else { Remove-Item Env:FMS_AGENT_NAME -ErrorAction SilentlyContinue }
    exit 2
}

try {
    Write-Host 'Snapshot'
    $before = Get-Listing $fixture
    $s = Get-DevMonitorSnapshot -RepoRoot $fixture -Tail 2 -NextUp 25 -ChatTail 10
    $after = Get-Listing $fixture
    Assert-That 'schema is 1' ($s.schema -eq 1) "$($s.schema)"
    Assert-That 'durationMs present and under the 1000 ms budget' ($s.durationMs -ge 0 -and $s.durationMs -lt 1000) "$($s.durationMs) ms"
    Assert-That 'chat read without error' ([string]::IsNullOrEmpty($s.chatError)) "$($s.chatError)"
    Assert-That 'one call writes nothing in the fixture' ($before -eq $after) 'listing changed'

    Write-Host 'Leases'
    Assert-That 'one lease, id S0001, holder agent-fx' (@($s.leases).Count -eq 1 -and $s.leases[0].id -eq 'S0001' -and $s.leases[0].sessionId -eq 'agent-fx') (($s.leases | ConvertTo-Json -Compress))
    Assert-That 'a fresh heartbeat is not judged stale' ($s.leases[0].liveness -ne 'foreign-stale' -and $s.leases[0].liveness -ne 'unknown') "$($s.leases[0].liveness)"
    Assert-That 'lease carries the holder nickname and a last-seen age' ($s.leases[0].name -eq 'fixture-fox-0902-0000' -and $null -ne $s.leases[0].lastSeenMinutes) "name=$($s.leases[0].name) seen=$($s.leases[0].lastSeenMinutes)"
    Assert-That 'transport fields are not exported' (-not ($s.leases[0].PSObject.Properties.Name -contains 'transcriptPath')) ''

    Write-Host 'Locks'
    $domainCount = @(Get-AgentLockDomainTable).Count
    Assert-That 'one row per domain, no legacy rows without files' (@($s.locks).Count -eq $domainCount -and @($s.locks | Where-Object legacy).Count -eq 0) "$(@($s.locks).Count) rows"
    $phone = $s.locks | Where-Object domain -eq 'Code.Phone'
    Assert-That 'Code.Phone is held with reason and holder name' ($phone.held -and $phone.reason -like '*S0001*' -and $phone.name -eq 'fixture-fox-0902-0000') (($phone | ConvertTo-Json -Compress -Depth 3))
    Assert-That 'Code.Phone has one queued ticket with both ages' (@($phone.queue).Count -eq 1 -and $phone.queue[0].sessionId -eq 'agent-q' -and $null -ne $phone.queue[0].waitedMinutes -and $null -ne $phone.queue[0].lastSeenMinutes) (($phone.queue | ConvertTo-Json -Compress))
    Assert-That 'every other domain is free' (@($s.locks | Where-Object { $_.domain -ne 'Code.Phone' -and $_.held }).Count -eq 0) ''
    Assert-That 'lock transport fields are not exported' (-not ($phone.PSObject.Properties.Name -contains 'transcriptPath')) ''

    Write-Host 'Stalled holders (S2413)'
    # The main fixture is the healthy shape: its holder posted to the chat moments ago, so the
    # signal must stay silent on it. A signal that lights on a healthy tree is one the operator
    # switches off, and then the incident it exists for passes unremarked.
    Assert-That 'stalls is present and empty while the holder is fresh' (($s.PSObject.Properties.Name -contains 'stalls') -and @($s.stalls).Count -eq 0) (($s.stalls | ConvertTo-Json -Compress))
    Assert-That 'the field is additive - the schema is still 1' ($s.schema -eq 1) "$($s.schema)"

    # A second root rather than an aged main fixture: the holder must have no chat line at all, and
    # every message in the main one belongs to agent-fx.
    $stalledRoot = Join-Path $fixture 'stalled'
    New-Item -ItemType Directory -Path (Join-Path $stalledRoot 'temp/CODE.SCRIPTS.QUEUE') -Force | Out-Null
    $agedTranscript = Join-Path $stalledRoot 'aged.jsonl'
    [IO.File]::WriteAllText($agedTranscript, '{}', $utf8)
    (Get-Item -LiteralPath $agedTranscript).LastWriteTime = (Get-Date).AddMinutes(-40)
    [IO.File]::WriteAllText((Join-Path $stalledRoot 'temp/CODE.SCRIPTS.LOCK'),
        ('{{"schema":2,"lockType":"Code.Scripts","pid":3,"acquiredAt":{0},"reason":"/spec-all S0007 edit","host":"FX","sessionId":"agent-stalled","transcriptPath":"{1}"}}' -f
            ($nowMs - 2400000), ($agedTranscript -replace '\\', '\\')), $utf8)
    [IO.File]::WriteAllText((Join-Path $stalledRoot 'temp/CODE.SCRIPTS.QUEUE/0001-agent-w.json'),
        ('{{"seq":1,"sessionId":"agent-w","reason":"/spec-all S0008 edit","enqueuedAt":{0},"lastSeenAt":{1}}}' -f ($nowMs - 480000), ($nowMs - 5000)), $utf8)
    $stalledSnapshot = Get-DevMonitorSnapshot -RepoRoot $stalledRoot
    $stall = @($stalledSnapshot.stalls)[0]
    Assert-That 'a quiet holder with somebody waiting is reported once' (@($stalledSnapshot.stalls).Count -eq 1 -and $stall.domain -eq 'Code.Scripts' -and $stall.queueDepth -eq 1) (($stalledSnapshot.stalls | ConvertTo-Json -Compress -Depth 3))
    Assert-That 'the stall carries the quiet time, the threshold and who waits longest' ($stall.quietMinutes -gt $stall.thresholdMinutes -and $stall.longestWaitMinutes -ge 7 -and $null -ne $stall.holderProcessAlive) (($stall | ConvertTo-Json -Compress))

    Write-Host 'Agents and chat'
    Assert-That 'one agent row' (@($s.agents).Count -eq 1) "$(@($s.agents).Count)"
    $a = $s.agents[0]
    Assert-That 'newest message gives lastKind, newest phase message gives the phase' ($a.lastKind -eq 'lock' -and $a.phase -eq '02' -and $a.phaseTicket -eq 'S0001') (($a | ConvertTo-Json -Compress))
    Assert-That 'agent joined to its lease by session id' ($a.lease -eq 'S0001' -and $a.name -eq 'fixture-fox-0902-0000' -and -not $a.silent) (($a | ConvertTo-Json -Compress))
    Assert-That 'chat tail newest first' (@($s.chat).Count -eq 2 -and $s.chat[0].kind -eq 'lock' -and $s.chat[1].kind -eq 'phase') (($s.chat | ConvertTo-Json -Compress))
    Assert-That 'the scoped finding is alive' (@($s.findings).Count -eq 1 -and $s.findings[0].topic -eq 'check:fixture' -and $s.findingsDead -eq 0) "alive=$(@($s.findings).Count) dead=$($s.findingsDead)"
    Assert-That 'windows come from the lock timings' ($s.windows.silentMinutes -gt 0 -and $s.windows.retentionMinutes -gt 0) (($s.windows | ConvertTo-Json -Compress))

    Write-Host 'Journals, stop, children'
    Assert-That 'one instance, counts by regex, tail by parse' (@($s.instances).Count -eq 1 -and $s.instances[0].instance -eq 'a' -and $s.instances[0].recorded -eq 3 -and $s.instances[0].moved -eq 1 -and $s.instances[0].stayed -eq 2 -and @($s.instances[0].rows).Count -eq 2) (($s.instances | ConvertTo-Json -Compress -Depth 3))
    Assert-That 'tail rows are the last ones' ($s.instances[0].rows[0].id -eq 'S0102' -and $s.instances[0].rows[1].id -eq 'S0103' -and $s.instances[0].rows[1].outcome -eq 'timeout') ''
    Assert-That 'stop flag with its age' (@($s.stop).Count -eq 1 -and $s.stop[0].name -eq 'STOP-SPEC-QUEUE' -and $null -ne $s.stop[0].requestedMinutes) ''
    Assert-That 'children is an array' ($null -ne $s.children -and $s.children -is [array]) ''

    Write-Host 'Next up'
    $n = $s.nextUp
    Assert-That 'package read from current-next-release' ($n.package -eq '36' -and $n.totalInPackage -eq 3) (($n | ConvertTo-Json -Compress -Depth 3))
    Assert-That 'first element is the group heading' (@($n.rows).Count -eq 4 -and $n.rows[0].kind -eq 'group' -and $n.rows[0].text -like '36.0*') (($n.rows | ConvertTo-Json -Compress))
    Assert-That 'package-35 row excluded, order kept' ($n.rows[1].id -eq 'S0001' -and $n.rows[2].id -eq 'S0002' -and $n.rows[3].id -eq 'S0003' -and @($n.rows | Where-Object { $_.kind -eq 'row' -and $_.id -eq 'S0035' }).Count -eq 0) ''
    Assert-That 'taken row is leased, Block row is blocked' ($n.rows[1].leased -and $n.rows[1].taken -like '*agent-fx*' -and -not $n.rows[1].blocked -and $n.rows[2].blocked -and -not $n.rows[2].leased -and -not $n.rows[3].leased -and -not $n.rows[3].blocked) ''
    $limited = (Get-DevMonitorSnapshot -RepoRoot $fixture -NextUp 2).nextUp
    Assert-That '-NextUp caps the rows, totalInPackage still counts all' (@($limited.rows).Count -eq 2 -and $limited.totalInPackage -eq 3) "$(@($limited.rows).Count)"

    Write-Host 'Empty root'
    $empty = Join-Path $fixture 'empty'
    New-Item -ItemType Directory -Path (Join-Path $empty 'temp') -Force | Out-Null
    $e = Get-DevMonitorSnapshot -RepoRoot $empty
    Assert-That 'an empty root returns every array, empty' ($e.schema -eq 1 -and @($e.leases).Count -eq 0 -and @($e.locks).Count -eq $domainCount -and @($e.instances).Count -eq 0 -and @($e.stop).Count -eq 0 -and $null -eq $e.nextUp.package -and @($e.nextUp.rows).Count -eq 0) ''

    Write-Host 'Terminal render'
    $jsonOut = & $pwshExe -NoProfile -File $monitor -RepoRoot $fixture -Json 2>&1 | Out-String
    $jsonCode = $LASTEXITCODE
    $parsed = $null
    try { $parsed = $jsonOut | ConvertFrom-Json } catch { $parsed = $null }
    Assert-That 'monitor -Json parses' ($jsonCode -eq 0 -and $null -ne $parsed -and $parsed.schema -eq 1 -and $parsed.leases[0].id -eq 'S0001') "exit=$jsonCode $($jsonOut.Substring(0, [math]::Min(200, $jsonOut.Length)))"
    $text = & $pwshExe -NoProfile -File $monitor -RepoRoot $fixture -Tail 2 2>&1 | Out-String
    $textCode = $LASTEXITCODE
    $sections = @('ticket leases', 'locks', 'agent chat', 'finished tickets', 'stop requested', 'running')
    $missing = @($sections | Where-Object { $text -notmatch [regex]::Escape($_) })
    Assert-That 'monitor prints every section' ($textCode -eq 0 -and $missing.Count -eq 0) "exit=$textCode missing=$($missing -join ',')"
    Assert-That 'monitor names the fixture lease and the held domain' ($text -match 'S0001' -and $text -match 'Code\.Phone\s+HELD') ''
    # S2413: the section is the deliverable, not the array - both directions, because a section
    # printed on every healthy run is one the operator learns to skip.
    Assert-That 'the healthy render prints no stalled-holders section' ($text -notmatch 'stalled holders') ''
    $stalledText = & $pwshExe -NoProfile -File $monitor -RepoRoot $stalledRoot 2>&1 | Out-String
    Assert-That 'the stalled render prints the section above the locks' (
        $stalledText -match 'stalled holders' -and $stalledText -match 'Code\.Scripts' -and
        $stalledText.IndexOf('stalled holders') -lt $stalledText.IndexOf('locks (who is building')) `
        ($stalledText.Substring(0, [math]::Min(400, $stalledText.Length)))
}
finally {
    if ($null -ne $savedChatRoot) { $env:FMS_AGENT_CHAT_ROOT = $savedChatRoot } else { Remove-Item Env:FMS_AGENT_CHAT_ROOT -ErrorAction SilentlyContinue }
    if ($null -ne $savedAgentId) { $env:FMS_AGENT_ID = $savedAgentId } else { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
    if ($null -ne $savedAgentName) { $env:FMS_AGENT_NAME = $savedAgentName } else { Remove-Item Env:FMS_AGENT_NAME -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host ("dev-monitor-snapshot tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
