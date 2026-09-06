#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for ticket leases (S2404): the handoff identity channel.

.DESCRIPTION
    Hermetic: FMS_TICKET_LEASE_ROOT points every lease and handoff path at a throwaway root
    under temp/S2404/, and FMS_AGENT_CHAT_ROOT keeps the claim/release chat lines out of the
    real store. Identities are swapped by setting CLAUDE_CODE_SESSION_ID in this process
    before each child call, because a child pwsh inherits the environment - the
    agent-chat.tests pattern (S2372).

    Pinned (strategic section 4):
      - release without a handoff is still refused on a live foreign lease (exit 4);
      - release with a valid handoff succeeds (exit 0, viaHandoff set, lease file gone);
      - re-claim with a handoff is already-mine and advances lastSeenAt;
      - an expired handoff, one naming another ticket, and one naming the wrong session
        are each ignored, so the release refusal stands (exit 4);
      - a stale lease is still swept and re-claimable with no handoff at all (regression);
      - the claim JSON carries handoffPath and spec-preamble.ps1 surfaces the path on its
        own line and as lease_handoff in -Json.
      - spec-preamble.ps1 also ACCEPTS -Handoff, so a re-claim through the preamble under a
        new identity is already-mine instead of exit 3.

    Pinned (S2407, Clean judges liveness by the same verdict and window as Claim):
      - a lease quiet ten minutes is KEPT, which the retired two-minute window dropped;
      - a fresh chat line keeps a lease whose heartbeat expired (S2372 ADR-7: chat may only
        extend a life), while an identical lease owned by a session that never spoke goes;
      - a dropped record names the session it was taken from;
      - -QuietMinutes below SessionStaleMinutes is refused (exit 2) and drops nothing, is
        accepted beside -Force, and is accepted bare at or above that floor;
      - the claim refusal (exit 3) states that a fresh chat row means the holder is alive.

    Pinned (S2578, a host- identity is a window and cannot vouch for one ticket):
      - an abandoned host- lease releases with NO -Force, is dropped by Clean, and is swept out
        of the way of a sibling's claim - the three paths that were jammed at once;
      - its claimedAt stays recent throughout, so every drop is the fix acting and never the
        480-minute ceiling, which was the only path that still worked;
      - a host- lease quiet ten minutes is kept, and Status reports that real age instead of the
        "last seen 0 min ago" the live process used to manufacture;
      - a live pid- owner still refuses Release -Force, so S2500 is narrowed and not weakened;
      - the heartbeat refreshes only the ticket the invocation names for a host- owner, while a
        session identity still refreshes every lease it owns (S1448 regression).

    Pinned (S2608, the work signals outrank -Force):
      - Release -Force is refused while the owner HOLDS a lock whose reason names the ticket,
        with every S2500 process signal false - the shape of an ordinary interactive session;
      - it is refused while the owner merely AWAITS that lock, which is the exact instant the
        S2583 lease was taken: the owner was queued and acquired the lock only afterwards;
      - with no lock, no queue ticket and no live process, -Force still releases, so S2500's
        contract is narrowed and not revoked.
      These three run the harness file directly against a sandbox project root, because the
      forwarder pins SZA_PROJECT_ROOT to this repository and the lock store would otherwise be
      the real one every sibling session is using.

    Exit codes:
      0 - every case passed; the sandbox is deleted.
      1 - at least one case failed; the sandbox is kept and its path printed.
      2 - the sandbox could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$cli = Join-Path $repoRoot 'scripts/spec_catalog/ticket-lease.ps1'
$preamble = Join-Path $repoRoot 'scripts/spec_catalog/spec-preamble.ps1'

$script:pass = 0
$script:fail = 0
$script:skipped = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}

function Invoke-Cli([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $cli @Arguments 2>&1 | ForEach-Object { "$_" }
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Text = ($out -join "`n"); Lines = @($out) }
}

function Set-Identity([string]$Name) {
    Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue
    $env:CLAUDE_CODE_SESSION_ID = $Name
}

function Get-LeaseFile([string]$TicketId) {
    $path = Join-Path $sandbox "temp/SPEC-TICKET.LEASES/$TicketId.json"
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
}

function Write-LeaseFile([string]$TicketId, $Lease) {
    $path = Join-Path $sandbox "temp/SPEC-TICKET.LEASES/$TicketId.json"
    Set-Content -LiteralPath $path -Value ($Lease | ConvertTo-Json -Depth 4 -Compress) -Encoding utf8NoBOM
}

function Get-JsonPayload([string]$Text) {
    # The CLI prints its JSON beside chat-post status lines (and their ANSI colour), so the
    # payload is the one line that parses as JSON - the session-bootstrap Read-ChildJson rule.
    foreach ($line in ($Text -split "`r?`n")) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('{') -or $trimmed.StartsWith('[')) {
            try { return ($trimmed | ConvertFrom-Json) } catch { continue }
        }
    }
    return $null
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandbox = Join-Path $repoRoot "temp/S2404/lease-tests/$stamp"
try {
    New-Item -ItemType Directory -Path (Join-Path $sandbox 'temp') -Force | Out-Null
    $env:FMS_TICKET_LEASE_ROOT = $sandbox
    $env:FMS_AGENT_CHAT_ROOT = Join-Path $sandbox 'AGENT-CHAT'
    Remove-Item Env:FMS_AGENT_CHAT_CAP_PROGRESS -ErrorAction SilentlyContinue
    Remove-Item Env:FMS_AGENT_CHAT_CAP_FINDINGS -ErrorAction SilentlyContinue
    . (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')
    $staleMinutes = (Get-AgentLockTimings -Name SpecTicket).SessionStaleMinutes
}
catch {
    Write-Host "ticket-lease tests: sandbox could not be prepared - $_" -ForegroundColor Red
    exit 2
}

try {
    # Shared fixture: A claims S9901, the handoff path is captured from the claim JSON.
    Write-Host 'Fixture: session A claims S9901'
    Set-Identity 'session-A'
    $claimA = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9901', '-Reason', 'lease-tests', '-Json')
    $handoffA = (Get-JsonPayload $claimA.Text).handoffPath
    Assert-That 'fixture: claim exit 0' ($claimA.Exit -eq 0) $claimA.Text
    Assert-That 'case 8a: claim JSON carries handoffPath' (-not [string]::IsNullOrWhiteSpace($handoffA) -and (Test-Path -LiteralPath $handoffA)) $claimA.Text

    Write-Host 'Owner check'
    Set-Identity 'session-B'
    $relNoHandoff = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9901', '-Json')
    Assert-That 'case 1: release by B without handoff refused (exit 4)' ($relNoHandoff.Exit -eq 4) $relNoHandoff.Text
    Assert-That 'case 1: lease file still present' ($null -ne (Get-LeaseFile 'S9901')) 'lease file was removed'

    Write-Host 'Release via handoff'
    $relHandoff = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9901', '-Handoff', $handoffA, '-Json')
    $relObj = Get-JsonPayload $relHandoff.Text
    Assert-That 'case 2: release by B with valid handoff succeeds (exit 0)' ($relHandoff.Exit -eq 0) $relHandoff.Text
    Assert-That 'case 2: outcome released, viaHandoff, not forced' ($relObj.outcome -eq 'released' -and $relObj.viaHandoff -eq $true -and $relObj.forced -eq $false) $relHandoff.Text
    Assert-That 'case 2: lease file removed' ($null -eq (Get-LeaseFile 'S9901')) 'lease file still present'

    Write-Host 'Refresh via handoff'
    Set-Identity 'session-A'
    $claimA2 = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9902', '-Reason', 'lease-tests', '-Json')
    $handoffA2 = (Get-JsonPayload $claimA2.Text).handoffPath
    $before = Get-LeaseFile 'S9902'
    Start-Sleep -Seconds 2
    Set-Identity 'session-B'
    $refresh = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9902', '-Reason', 'lease-tests', '-Handoff', $handoffA2, '-Json')
    $after = Get-LeaseFile 'S9902'
    Assert-That 'case 3: re-claim by B with handoff is already-mine (exit 0)' ($refresh.Exit -eq 0 -and (Get-JsonPayload $refresh.Text).outcome -eq 'already-mine') $refresh.Text
    Assert-That 'case 3: lease owner unchanged (adoption never rewrites)' ([string]$after.sessionId -eq 'session-A') "owner=$($after.sessionId)"
    Assert-That 'case 3: lastSeenAt advanced by the refresh' ([int64]$after.lastSeenAt -gt [int64]$before.lastSeenAt) "before=$($before.lastSeenAt) after=$($after.lastSeenAt)"

    Write-Host 'Ignored handoffs'
    # Foreign ticket id: S9901's handoff (fresh, valid, on disk) proves nothing about S9902.
    $relForeignId = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9902', '-Handoff', $handoffA, '-Json')
    Assert-That 'case 5: handoff naming another ticket ignored, release refused (exit 4)' ($relForeignId.Exit -eq 4) $relForeignId.Text

    # Expired: createdAt pushed past the liveness window.
    $expired = Get-Content -LiteralPath $handoffA2 -Raw | ConvertFrom-Json
    $expired.createdAt = [DateTimeOffset]::UtcNow.AddMinutes(-1 * ($staleMinutes + 10)).ToUnixTimeMilliseconds()
    Set-Content -LiteralPath $handoffA2 -Value ($expired | ConvertTo-Json -Compress) -Encoding utf8NoBOM
    $relExpired = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9902', '-Handoff', $handoffA2, '-Json')
    Assert-That 'case 4: expired handoff ignored, release refused (exit 4)' ($relExpired.Exit -eq 4) $relExpired.Text

    # Wrong session: a fresh handoff whose sessionId is not the lease owner.
    $wrong = Get-Content -LiteralPath $handoffA2 -Raw | ConvertFrom-Json
    $wrong.createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $wrong.sessionId = 'session-C'
    Set-Content -LiteralPath $handoffA2 -Value ($wrong | ConvertTo-Json -Compress) -Encoding utf8NoBOM
    $relWrong = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9902', '-Handoff', $handoffA2, '-Json')
    Assert-That 'case 6: handoff naming a foreign session ignored, release refused (exit 4)' ($relWrong.Exit -eq 4) $relWrong.Text

    Write-Host 'Stale lease regression'
    $old = Get-LeaseFile 'S9902'
    $backdate = [DateTimeOffset]::UtcNow.AddMinutes(-1 * ($staleMinutes + 10)).ToUnixTimeMilliseconds()
    $old.lastSeenAt = $backdate
    $old.claimedAt = $backdate
    Write-LeaseFile 'S9902' $old
    $reclaim = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9902', '-Reason', 'lease-tests', '-Json')
    $reclaimed = Get-LeaseFile 'S9902'
    Assert-That 'case 7: stale lease swept, fresh claim by B succeeds (exit 0)' ($reclaim.Exit -eq 0 -and (Get-JsonPayload $reclaim.Text).outcome -eq 'claimed') $reclaim.Text
    Assert-That 'case 7: new lease owned by B' ([string]$reclaimed.sessionId -eq 'session-B') "owner=$($reclaimed.sessionId)"

    Write-Host 'Preamble surfacing'
    $preText = (& $pwshExe -NoProfile -File $preamble -Id S2404 -Reason 'lease-tests' -NoDrift 2>&1 | ForEach-Object { "$_" }) -join "`n"
    Assert-That 'case 8b: preamble text output carries a lease handoff line' ($preText -match 'lease handoff:\s*\S+') $preText
    $preJsonRaw = (& $pwshExe -NoProfile -File $preamble -Id S2404 -Reason 'lease-tests' -NoDrift -Json 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $preJsonLine = @($preJsonRaw -split "`r?`n" | Where-Object { $_.Trim().StartsWith('{') } | Select-Object -First 1)
    $preJson = $null
    if ($preJsonLine.Count -gt 0) { try { $preJson = $preJsonLine[0] | ConvertFrom-Json } catch { $preJson = $null } }
    Assert-That 'case 8b: preamble -Json parses with lease_handoff' ($null -ne $preJson -and -not [string]::IsNullOrWhiteSpace([string]$preJson.lease_handoff)) $preJsonRaw

    # Case 9: the preamble must ACCEPT the handoff it prints. Measured 2026-09-02 before this
    # case existed: two preamble runs under different identities gave exit 0 then exit 3, which
    # is strategic section 1's symptom surviving on the path /spec-dev claims through.
    Write-Host 'Preamble adoption'
    $preHandoff = [string]$preJson.lease_handoff
    Set-Identity 'session-C'
    $strangerRaw = (& $pwshExe -NoProfile -File $preamble -Id S2404 -Reason 'lease-tests' -NoDrift -Json 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $strangerJson = Get-JsonPayload $strangerRaw
    Assert-That 'case 9: preamble without a handoff is still a stranger (lease exit 3)' ($null -ne $strangerJson -and [int]$strangerJson.lease_exit -eq 3) $strangerRaw
    $adoptRaw = (& $pwshExe -NoProfile -File $preamble -Id S2404 -Reason 'lease-tests' -NoDrift -Json -Handoff $preHandoff 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $adoptJson = Get-JsonPayload $adoptRaw
    Assert-That 'case 9: preamble with the printed handoff re-claims (lease exit 0)' ($null -ne $adoptJson -and [int]$adoptJson.lease_exit -eq 0) $adoptRaw
    Assert-That 'case 9: adopted re-claim is already-mine, owner unchanged' ([string](Get-LeaseFile 'S2404').sessionId -eq 'session-B') "owner=$((Get-LeaseFile 'S2404').sessionId)"

    # S2407. Fabricated rather than claimed: a claim posts a chat line, and chat is one of the three
    # signals under test, so an owner that must look silent has to be one that never spoke.
    function New-TestLease([string]$TicketId, [string]$Owner, [int64]$Mark) {
        Write-LeaseFile $TicketId ([pscustomobject]@{
                schema         = 1
                id             = $TicketId
                sessionId      = $Owner
                host           = 'lease-tests'
                pid            = 0
                reason         = 'lease-tests'
                claimedAt      = $Mark
                lastSeenAt     = $Mark
                transcriptPath = ''
            })
    }

    Write-Host 'Clean judges by the shared window'
    $farPast = [DateTimeOffset]::UtcNow.AddMinutes(-1 * ($staleMinutes + 10)).ToUnixTimeMilliseconds()
    $tenMinutesAgo = [DateTimeOffset]::UtcNow.AddMinutes(-10).ToUnixTimeMilliseconds()
    New-TestLease 'S9903' 'session-quiet' $tenMinutesAgo
    New-TestLease 'S9904' 'session-quiet' $farPast
    # session-A claimed twice above, so its newest chat line is minutes old while its lease is not.
    New-TestLease 'S9905' 'session-A' $farPast
    Set-Identity 'session-B'
    $clean = Invoke-Cli @('-Verb', 'Clean', '-Json')
    $cleanObj = Get-JsonPayload $clean.Text
    Assert-That 'case 10: Clean -Json parses' ($null -ne $cleanObj) $clean.Text
    $keptIds = if ($null -ne $cleanObj) { @($cleanObj.kept | ForEach-Object { [string]$_.id }) } else { @() }
    $droppedIds = if ($null -ne $cleanObj) { @($cleanObj.dropped | ForEach-Object { [string]$_.id }) } else { @() }
    Assert-That 'case 10: a lease quiet 10 min is kept - the retired 2 min window dropped it' ($keptIds -contains 'S9903') $clean.Text
    Assert-That 'case 10: a lease with every signal past the window is dropped' ($droppedIds -contains 'S9904') $clean.Text
    Assert-That 'case 11: a fresh chat line keeps a lease whose heartbeat expired (S2372 ADR-7)' ($keptIds -contains 'S9905') $clean.Text
    $droppedRecord = if ($null -ne $cleanObj) { @($cleanObj.dropped | Where-Object { [string]$_.id -eq 'S9904' }) } else { @() }
    Assert-That 'case 12: the dropped record names the session it was taken from' ($droppedRecord.Count -eq 1 -and [string]$droppedRecord[0].heldBy -eq 'session-quiet') $clean.Text

    Write-Host 'The floor under -QuietMinutes'
    $lowered = Invoke-Cli @('-Verb', 'Clean', '-QuietMinutes', '1', '-Json')
    Assert-That 'case 13: a window below the shared one is refused (exit 2)' ($lowered.Exit -eq 2) $lowered.Text
    Assert-That 'case 13: the refusal names the floor' ($lowered.Text -match "$staleMinutes min") $lowered.Text
    Assert-That 'case 13: a refused run drops nothing' ($null -ne (Get-LeaseFile 'S9903')) 'S9903 went during a refused run'
    $wide = Invoke-Cli @('-Verb', 'Clean', '-QuietMinutes', "$($staleMinutes + 15)", '-Json')
    Assert-That 'case 14: a window at or above the floor needs no -Force (exit 0)' ($wide.Exit -eq 0) $wide.Text
    Assert-That 'case 14: the wider window still keeps the live lease' ($null -ne (Get-LeaseFile 'S9903')) 'S9903 went under a wider window'
    $forced = Invoke-Cli @('-Verb', 'Clean', '-QuietMinutes', '1', '-Force', '-Json')
    Assert-That 'case 15: -Force accepts the lowered window (exit 0)' ($forced.Exit -eq 0) $forced.Text
    Assert-That 'case 15: -Force drops the lot' ($null -eq (Get-LeaseFile 'S9903')) 'S9903 survived -Force'

    Write-Host 'The refusal carries the rule'
    Set-Identity 'session-A'
    $held = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9906', '-Reason', 'lease-tests')
    Assert-That 'case 16: fixture claim by A succeeds' ($held.Exit -eq 0) $held.Text
    Set-Identity 'session-B'
    $refused = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9906', '-Reason', 'lease-tests')
    Assert-That 'case 16: claim refused while a live sibling holds it (exit 3)' ($refused.Exit -eq 3) $refused.Text
    Assert-That 'case 16: the refusal states that a fresh chat row means alive' ($refused.Text -match 'do not run Clean with a lowered -QuietMinutes') $refused.Text

    Write-Host 'Release -Force process liveness check (S2500)'
    # 17a: Live owner process (our own PID) -> Release -Force MUST be refused (exit 4)
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    Write-LeaseFile 'S9907' ([pscustomobject]@{
        schema         = 1
        id             = 'S9907'
        sessionId      = 'session-live-proc'
        host           = $env:COMPUTERNAME
        pid            = $PID
        reason         = 'lease-tests'
        claimedAt      = $now
        lastSeenAt     = $now
        transcriptPath = ''
    })
    Set-Identity 'session-B'
    $relForceLive = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9907', '-Force', '-Json')
    $relForceLiveObj = Get-JsonPayload $relForceLive.Text
    Assert-That 'case 17a: Release -Force refused when owner process is still running (exit 4)' ($relForceLive.Exit -eq 4) $relForceLive.Text
    Assert-That 'case 17a: processAlive flag is true in refusal' ($null -ne $relForceLiveObj -and $relForceLiveObj.processAlive -eq $true) $relForceLive.Text
    Assert-That 'case 17a: lease file retained' ($null -ne (Get-LeaseFile 'S9907')) 'S9907 was removed despite live process'

    # 17b: Dead owner process (PID 999999) with recent timestamp -> Release without -Force refused, with -Force succeeds
    Write-LeaseFile 'S9908' ([pscustomobject]@{
        schema         = 1
        id             = 'S9908'
        sessionId      = 'session-dead-proc'
        host           = $env:COMPUTERNAME
        pid            = 999999
        reason         = 'lease-tests'
        claimedAt      = $now
        lastSeenAt     = $now
        transcriptPath = ''
    })
    $relDeadNoForce = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9908', '-Json')
    Assert-That 'case 17b: Release without -Force on dead process with fresh timestamp refused (exit 4)' ($relDeadNoForce.Exit -eq 4) $relDeadNoForce.Text
    $relDeadForce = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9908', '-Force', '-Json')
    $relDeadForceObj = Get-JsonPayload $relDeadForce.Text
    Assert-That 'case 17b: Release -Force on dead owner process succeeds (exit 0)' ($relDeadForce.Exit -eq 0) $relDeadForce.Text
    Assert-That 'case 17b: forced flag is true' ($null -ne $relDeadForceObj -and $relDeadForceObj.forced -eq $true) $relDeadForce.Text
    Assert-That 'case 17b: lease file removed' ($null -eq (Get-LeaseFile 'S9908')) 'S9908 was not removed'

    # S2578. A host- identity is a WINDOW, not a session: the walk picks that process for
    # outliving a session, so its being alive says nothing about any one ticket. The owner below
    # is built from THIS process - a genuinely live pid whose start ticks match - which is the
    # immortal owner in pure form and needs no IDE open to reproduce.
    #
    # claimedAt stays RECENT on purpose, twice over: the incident's IDE started well before the
    # lease, which is what let Test-AgentIdentityProcessAlive's -NotStartedAfter guard pass, and a
    # recent claim also keeps the lease far under TicketCeilingMinutes - so anything dropped below
    # is dropped by this fix and not by the 480-minute ceiling that was the only working path.
    #
    # S2402: the local ticket-lease.ps1 is a generated forwarder, so the SUBJECT of these cases is
    # the shipped harness, and between the canon edit and the deploy those are different files.
    # Only the owner can deploy the plugin, so a suite that went red in the meantime would hand
    # every neighbouring session a failure no session running it can fix - S2577 met this first
    # and skipped with a named reason instead. The resolved path comes from the harness's OWN
    # resolver rather than a restatement of the forwarder's search order, so this reads the very
    # file the CLI below will run.
    # Deliberately an if/else and not an early `return`: a return here would leave the trailing
    # `exit` unreached, so a run that had already FAILED an earlier case and then met an
    # undeployed harness would end in a silent exit 0 - a green verdict that observed nothing.
    $leaseSourcePath = Get-SzaHarnessScript 'locks/ticket-lease.ps1'
    if ((Get-Content -LiteralPath $leaseSourcePath -Raw) -notmatch 'Test-LeaseOwnerProcessVouches') {
        Write-Host "  SKIP S2578 host-window owner (cases 18-25) - the resolved harness predates the fix: $leaseSourcePath" -ForegroundColor DarkGray
        $script:skipped = 8
    }
    else {

    Write-Host 'Host-window owner (S2578)'
    $hostProc = Get-Process -Id $PID
    $hostIdentity = 'host-language-server-{0}-{1}' -f $PID, $hostProc.StartTime.Ticks
    $nowMark = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $pastMark = [DateTimeOffset]::UtcNow.AddMinutes(-1 * ($staleMinutes + 10)).ToUnixTimeMilliseconds()

    function New-HostLease([string]$TicketId, [int64]$LastSeen, [string]$Owner = $hostIdentity) {
        Write-LeaseFile $TicketId ([pscustomobject]@{
                schema         = 1
                id             = $TicketId
                sessionId      = $Owner
                host           = $env:COMPUTERNAME
                pid            = 999999
                reason         = 'lease-tests'
                claimedAt      = $nowMark
                lastSeenAt     = $LastSeen
                transcriptPath = ''
            })
    }

    # 18: the defect itself. Every work signal is past the window, yet before this fix the live
    # host process pinned the verdict at foreign-live and Release refused even under -Force.
    New-HostLease 'S9910' $pastMark
    Set-Identity 'session-B'
    $relHost = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9910', '-Json')
    Assert-That 'case 18: abandoned host- lease releases without -Force (exit 0)' ($relHost.Exit -eq 0) $relHost.Text
    Assert-That 'case 18: lease file removed' ($null -eq (Get-LeaseFile 'S9910')) 'S9910 survived the release'

    # 19: Clean must drop it too - before this it kept it with "its owner is live by the shared
    # window", the keep-reason that made the verb useless against exactly the case it exists for.
    New-HostLease 'S9911' $pastMark
    $cleanHost = Invoke-Cli @('-Verb', 'Clean', '-Json')
    $cleanHostObj = Get-JsonPayload $cleanHost.Text
    $cleanDropped = if ($null -ne $cleanHostObj) { @($cleanHostObj.dropped | ForEach-Object { [string]$_.id }) } else { @() }
    Assert-That 'case 19: Clean drops an abandoned host- lease' ($cleanDropped -contains 'S9911') $cleanHost.Text

    # 20: the outcome the owner asked for - the ticket returns to the picker's cycle. The sweep
    # that every Claim runs first is what clears it, so a sibling simply takes the ticket.
    New-HostLease 'S9912' $pastMark
    $claimHost = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9912', '-Reason', 'lease-tests', '-Json')
    Assert-That 'case 20: a sibling re-claims the swept host- ticket (exit 0)' ($claimHost.Exit -eq 0 -and (Get-JsonPayload $claimHost.Text).outcome -eq 'claimed') $claimHost.Text
    Assert-That 'case 20: the new lease is owned by the claimant' ([string](Get-LeaseFile 'S9912').sessionId -eq 'session-B') "owner=$((Get-LeaseFile 'S9912').sessionId)"

    # 21: the live window is not robbed. Ten minutes is inside the window, so the clock alone says
    # foreign-live and the re-judgement must not fire - it may only act where NO signal is inside.
    New-HostLease 'S9913' ([DateTimeOffset]::UtcNow.AddMinutes(-10).ToUnixTimeMilliseconds())
    $cleanLive = Invoke-Cli @('-Verb', 'Clean', '-Json')
    $cleanLiveObj = Get-JsonPayload $cleanLive.Text
    $cleanLiveKept = if ($null -ne $cleanLiveObj) { @($cleanLiveObj.kept | ForEach-Object { [string]$_.id }) } else { @() }
    Assert-That 'case 21: a host- lease quiet 10 min is kept' ($cleanLiveKept -contains 'S9913') $cleanLive.Text

    # 22: Status told the operator "last seen 0 min ago" about a lease held six hours, because the
    # live process contributed a (Get-Date) mark. The reported age must be the real one.
    $statusHost = Invoke-Cli @('-Verb', 'Status')
    $s9913Line = @($statusHost.Lines | Where-Object { $_ -match 'S9913' })
    Assert-That 'case 22: Status lists the live host- lease' ($s9913Line.Count -eq 1) $statusHost.Text
    Assert-That 'case 22: its reported age is real, not zeroed by the live process' ($s9913Line.Count -eq 1 -and $s9913Line[0] -notmatch 'last seen 0 min ago' -and $s9913Line[0] -match 'last seen (9|10|11)(\.\d+)? min ago') $statusHost.Text

    # 23: S2500 is narrowed, not weakened. A pid- identity IS the claiming process, so it is
    # bounded by the work and still vouches - Release -Force stays refused while it runs.
    Write-LeaseFile 'S9914' ([pscustomobject]@{
            schema         = 1
            id             = 'S9914'
            sessionId      = "pid-$PID"
            host           = $env:COMPUTERNAME
            pid            = 999999
            reason         = 'lease-tests'
            claimedAt      = $nowMark
            lastSeenAt     = $nowMark
            transcriptPath = ''
        })
    $relPid = Invoke-Cli @('-Verb', 'Release', '-Id', 'S9914', '-Force', '-Json')
    Assert-That 'case 23: Release -Force still refused for a live pid- owner (exit 4)' ($relPid.Exit -eq 4) $relPid.Text
    Assert-That 'case 23: processAlive is true for a pid- owner' ((Get-JsonPayload $relPid.Text).processAlive -eq $true) $relPid.Text

    # 24: the heartbeat must not spread across the window. One active chat refreshing the OTHER
    # tickets of its window is how three leases stayed immortal together, and it is also why the
    # operator's own -Verb Status could not outlast them.
    Write-Host 'Heartbeat scoped to the named ticket (S2578)'
    New-HostLease 'S9915' $pastMark
    New-HostLease 'S9916' $pastMark
    Set-Identity $hostIdentity
    $beforeNamed = (Get-LeaseFile 'S9915').lastSeenAt
    $beforeOther = (Get-LeaseFile 'S9916').lastSeenAt
    $claimNamed = Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9915', '-Reason', 'lease-tests', '-Json')
    Assert-That 'case 24: re-claiming its own lease is already-mine' ((Get-JsonPayload $claimNamed.Text).outcome -eq 'already-mine') $claimNamed.Text
    Assert-That 'case 24: the named lease is refreshed' ([int64](Get-LeaseFile 'S9915').lastSeenAt -gt [int64]$beforeNamed) "before=$beforeNamed after=$((Get-LeaseFile 'S9915').lastSeenAt)"
    Assert-That 'case 24: the window''s other lease is NOT refreshed' ([int64](Get-LeaseFile 'S9916').lastSeenAt -eq [int64]$beforeOther) "before=$beforeOther after=$((Get-LeaseFile 'S9916').lastSeenAt)"

    # 25: S1448 regression. For every identity that is genuinely one session, refreshing all of
    # its leases stays right, so the scoping above must not have leaked out of the host- case.
    New-HostLease 'S9917' $pastMark 'session-two-leases'
    New-HostLease 'S9918' $pastMark 'session-two-leases'
    Set-Identity 'session-two-leases'
    $beforeA = (Get-LeaseFile 'S9917').lastSeenAt
    $beforeB = (Get-LeaseFile 'S9918').lastSeenAt
    [void](Invoke-Cli @('-Verb', 'Claim', '-Id', 'S9917', '-Reason', 'lease-tests', '-Json'))
    Assert-That 'case 25: a session identity still refreshes the named lease' ([int64](Get-LeaseFile 'S9917').lastSeenAt -gt [int64]$beforeA) "before=$beforeA"
    Assert-That 'case 25: a session identity still refreshes its other lease' ([int64](Get-LeaseFile 'S9918').lastSeenAt -gt [int64]$beforeB) "before=$beforeB"

    }

    # S2608. The work signals, which are the ones an ordinary interactive session actually emits.
    # Every process signal S2500 added is false BY CONSTRUCTION for such a session - the sessionId
    # is a guid naming no process, the recorded pid is the pwsh that wrote the lease and exited, and
    # only a headless child registers a run ticket - so the guard passed a plainly working session
    # and -Force took its lease. Measured on S2583, 2026-09-05.
    #
    # These cases need an isolated LOCK store, not only an isolated lease store, and the forwarder
    # cannot give one: it pins SZA_PROJECT_ROOT to this repository on every call, by design, so that
    # the harness knows which project invoked it. The harness file is therefore run directly, with
    # the project root pointed at the sandbox - the lock and queue files then land beside the leases
    # under it and no sibling session's real Code.Scripts lock is read or written. The path comes
    # from the harness's own resolver for the reason case 18 already gives.
    if ((Get-Content -LiteralPath $leaseSourcePath -Raw) -notmatch 'Test-LeaseOwnerWorksTicket') {
        Write-Host "  SKIP S2608 work signals (cases 26-28) - the resolved harness predates the fix: $leaseSourcePath" -ForegroundColor DarkGray
        $script:skipped += 3
    }
    else {

    Write-Host 'Owner working the ticket (S2608)'
    $enterLock = Get-SzaHarnessScript 'locks/enter-code-lock.ps1'
    $exitLock = Get-SzaHarnessScript 'locks/exit-code-lock.ps1'
    $withdrawTicket = Get-SzaHarnessScript 'locks/withdraw-lock-ticket.ps1'
    # The domain table is read from the profile, so the sandbox needs this repository's copy or the
    # five domains collapse to whatever the harness defaults to and Code.Scripts stops existing.
    Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination (Join-Path $sandbox '.sza-profile.json') -Force

    function Invoke-InSandbox([string]$Script, [string[]]$Arguments) {
        $previousRoot = $env:SZA_PROJECT_ROOT
        $env:SZA_PROJECT_ROOT = $sandbox
        try {
            $out = & $pwshExe -NoProfile -File $Script @Arguments 2>&1 | ForEach-Object { "$_" }
            return [pscustomobject]@{ Exit = $LASTEXITCODE; Text = ($out -join "`n") }
        }
        finally { $env:SZA_PROJECT_ROOT = $previousRoot }
    }

    Set-Identity 'session-worker'
    $claimWork = Invoke-InSandbox $leaseSourcePath @('-Verb', 'Claim', '-Id', 'S9920', '-Reason', 'lease-tests', '-Json')
    Assert-That 'fixture: worker claims S9920' ($claimWork.Exit -eq 0) $claimWork.Text

    # 26: the owner HOLDS a lock whose reason names the ticket. Clean already kept such a lease and
    # Get-LeaseLiveness already promoted it back to live; Release was the one exit that never asked.
    $held = Invoke-InSandbox $enterLock @('-Files', 'scripts/worker.ps1', '-Reason', 'lease-tests S9920 step 1')
    Assert-That 'fixture: worker holds Code.Scripts naming S9920' ($held.Exit -eq 0) $held.Text
    Set-Identity 'session-raider'
    $relHeld = Invoke-InSandbox $leaseSourcePath @('-Verb', 'Release', '-Id', 'S9920', '-Force', '-Json')
    $relHeldObj = Get-JsonPayload $relHeld.Text
    Assert-That 'case 26: Release -Force refused while the owner holds a lock naming the ticket (exit 4)' ($relHeld.Exit -eq 4) $relHeld.Text
    Assert-That 'case 26: refused on the WORK signal, with every process signal false' ($null -ne $relHeldObj -and $relHeldObj.ownerWorking -eq $true -and $relHeldObj.processAlive -eq $false) $relHeld.Text
    Assert-That 'case 26: the lease survived the attempt' ($null -ne (Get-LeaseFile 'S9920')) 'lease file was removed'

    # 27: the incident's exact shape. The owner held NOTHING at the instant of the release - it was
    # queued behind another session's lock, and took the lock only afterwards. A check that reads
    # held locks alone still loses this lease, which is why the queue is a signal in its own right.
    Set-Identity 'session-worker'
    [void](Invoke-InSandbox $exitLock @())
    Set-Identity 'session-other'
    $blocking = Invoke-InSandbox $enterLock @('-Files', 'scripts/other.ps1', '-Reason', 'lease-tests unrelated')
    Assert-That 'fixture: another session holds the domain' ($blocking.Exit -eq 0) $blocking.Text
    Set-Identity 'session-worker'
    $queued = Invoke-InSandbox $enterLock @('-Files', 'scripts/worker.ps1', '-Reason', 'lease-tests S9920 step 2')
    Assert-That 'fixture: worker is queued, holding nothing (exit 4)' ($queued.Exit -eq 4) $queued.Text
    Set-Identity 'session-raider'
    $relQueued = Invoke-InSandbox $leaseSourcePath @('-Verb', 'Release', '-Id', 'S9920', '-Force', '-Json')
    $relQueuedObj = Get-JsonPayload $relQueued.Text
    Assert-That 'case 27: Release -Force refused while the owner only AWAITS the lock (exit 4)' ($relQueued.Exit -eq 4) $relQueued.Text
    Assert-That 'case 27: the queue ticket alone raises ownerWorking' ($null -ne $relQueuedObj -and $relQueuedObj.ownerWorking -eq $true) $relQueued.Text
    Assert-That 'case 27: the lease survived the attempt' ($null -ne (Get-LeaseFile 'S9920')) 'lease file was removed'

    # 28: S2500's contract is narrowed, not revoked. With no lock, no queue ticket and no live
    # process, -Force still means what its parameter doc says and the lease goes.
    Set-Identity 'session-worker'
    [void](Invoke-InSandbox $withdrawTicket @('-Name', 'Code.Scripts'))
    Set-Identity 'session-other'
    [void](Invoke-InSandbox $exitLock @())
    Set-Identity 'session-raider'
    $relFree = Invoke-InSandbox $leaseSourcePath @('-Verb', 'Release', '-Id', 'S9920', '-Force', '-Json')
    $relFreeObj = Get-JsonPayload $relFree.Text
    Assert-That 'case 28: with no work signal at all, -Force still releases (exit 0)' ($relFree.Exit -eq 0) $relFree.Text
    Assert-That 'case 28: reported as forced' ($null -ne $relFreeObj -and $relFreeObj.outcome -eq 'released' -and $relFreeObj.forced -eq $true) $relFree.Text
    Assert-That 'case 28: the lease file is gone' ($null -eq (Get-LeaseFile 'S9920')) 'lease file still present'

    }
}
finally {
    # The skip count rides in the summary line rather than only in the SKIP notice above it: the
    # gate that runs this suite reports the last line, and "57 passed" and "42 passed, 8 skipped"
    # are different answers about how much was actually observed.
    $skipNote = if ($script:skipped -gt 0) { ", $script:skipped case(s) skipped" } else { '' }
    if ($script:fail -eq 0) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "ticket-lease tests: $script:pass passed$skipNote, sandbox removed." -ForegroundColor Cyan
    } else {
        Write-Host "ticket-lease tests: $script:pass passed, $script:fail FAILED$skipNote - sandbox kept: $sandbox" -ForegroundColor Yellow
    }
}

exit ($(if ($script:fail -gt 0) { 1 } else { 0 }))
