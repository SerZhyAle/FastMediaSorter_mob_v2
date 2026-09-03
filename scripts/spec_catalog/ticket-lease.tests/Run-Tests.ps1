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
}
finally {
    if ($script:fail -eq 0) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "ticket-lease tests: $script:pass passed, sandbox removed." -ForegroundColor Cyan
    } else {
        Write-Host "ticket-lease tests: $script:pass passed, $script:fail FAILED - sandbox kept: $sandbox" -ForegroundColor Yellow
    }
}

exit ($(if ($script:fail -gt 0) { 1 } else { 0 }))
