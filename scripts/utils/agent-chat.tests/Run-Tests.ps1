#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for the agent chat (S2372): identity, store, staleness, caps, CLI.

.DESCRIPTION
    Hermetic: FMS_AGENT_CHAT_ROOT points every process at a throwaway root under temp/S2372/, and
    the caps are shrunk through the test-only FMS_AGENT_CHAT_CAP_* variables so eviction is proven
    with five files instead of four hundred. Identity cases run in child pwsh processes with the
    environment edited there, because the identity of THIS process is whatever the runtime gave it.

    Pinned (strategic section 11 items 7 and 11):
      - identity is never empty: with FMS_AGENT_ID, with the session id only, with neither;
      - FMS_AGENT_ID wins over the session id;

    Pinned (S2408, the chain's third step is the ancestor host process):
      - with no identity variables set, the id is the one this tree's walk outcome calls for -
        a host- id when the walk resolved or adopted, the pid- fallback when it found no host
        (S2417: demanding a host- id outright measured the runtime, not the rule, and was red on
        every tree without a long-lived ancestor);
      - two children of one parent resolve to one id and one nickname wherever a host exists -
        the whole claim the ticket makes about a runtime that spawns a fresh shell per command;
      - FMS_AGENT_HOST_WALK=0 still reaches the pid- fallback;

    Pinned (S2417, the walk adopts the ancestor below a machine-wide process and says so):
      - the decider over synthetic name chains: resolved, adopted-below-<name>,
        no-host-below-<name>, a lost trail, an exhausted depth, and case-insensitivity;
      - hostWalk is never empty and reads 'disabled' with the walk off, 'not-reached' when an
        environment variable answered, and reaches Whoami -Json;
      - Test-AgentIdentityProcessAlive: true for the caller's own host id, false for a dead pid,
        a dead host id, a session guid, and a host id whose start ticks do not match;
      - a posted message parses, carries schema 1 and the agent object, and its file name carries
        kind and agent id;
      - schema 99 and a corrupt file are skipped by Read, not reported;
      - a finding is alive until a file in its scope is written after it, then dead;
      - a finding with TTL 0 is dead; a finding naming an unlisted device is dead;
      - a finding with neither scope nor TTL is refused (exit 1); an unknown kind is refused (exit 1);
      - the progress cap evicts the oldest and keeps the newest;
      - Get-AgentChatLastSeen returns the newest of THAT agent only;
      - Read -AgentId returns that agent's messages only; -Json parses.

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
$cli = Join-Path $repoRoot 'scripts/utils/agent-chat.ps1'
$identityPs1 = Join-Path $repoRoot 'scripts/utils/agent-identity.ps1'

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

function Invoke-Child([string]$Command) {
    $out = & $pwshExe -NoProfile -Command $Command 2>&1 | ForEach-Object { "$_" }
    return ($out -join "`n").Trim()
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$root = Join-Path $repoRoot "temp/S2372/chat-tests/$run"
try {
    New-Item -ItemType Directory -Path (Join-Path $root 'scope') -Force | Out-Null
    $env:FMS_AGENT_CHAT_ROOT = $root
    Remove-Item Env:FMS_AGENT_CHAT_CAP_PROGRESS -ErrorAction SilentlyContinue
    Remove-Item Env:FMS_AGENT_CHAT_CAP_FINDINGS -ErrorAction SilentlyContinue
    . (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')
}
catch {
    Write-Host "agent-chat tests: fixture could not be prepared - $_" -ForegroundColor Red
    exit 2
}

try {
    Write-Host 'Identity'
    $clearEnv = "Remove-Item Env:FMS_AGENT_ID -EA SilentlyContinue; Remove-Item Env:CLAUDE_CODE_SESSION_ID -EA SilentlyContinue; Remove-Item Env:FMS_AGENT_HOST_WALK -EA SilentlyContinue; "
    # S2417: these two used to demand a host- id outright, which measures the process tree the
    # suite happens to run in rather than the rule - red on every runtime that has no long-lived
    # ancestor, and a red nobody can fix is a red nobody reads. They now assert the id against the
    # walk outcome the tree actually produced, and print that outcome either way.
    $liveWalk = Invoke-Child "$clearEnv. '$identityPs1'; `$r = Get-AgentIdentityResolution; `$r.Id + '|' + `$r.HostWalk"
    $liveParts = @($liveWalk -split '\|')
    $idHost = $liveParts[0]
    $walkOutcome = if ($liveParts.Count -gt 1) { $liveParts[1] } else { '' }
    Write-Host "        live host walk on this tree: $walkOutcome -> $idHost"
    $hostExpected = ($walkOutcome -eq 'resolved' -or $walkOutcome -like 'adopted-below-*')
    $idMatchesWalk = if ($hostExpected) { $idHost -match '^host-[a-z0-9\-]+-\d+-\d+$' } else { $idHost -match '^pid-\d+$' }
    Assert-That 'no env -> the id the walk outcome calls for (S2408/S2417)' $idMatchesWalk "id=$idHost hostWalk=$walkOutcome"
    $idHostAgain = Invoke-Child "$clearEnv. '$identityPs1'; (Get-AgentIdentity).id"
    # Two children of one parent share an id only when a host was found; with none, each child is
    # its own pid and that is the correct answer, not a regression.
    $stableAcrossChildren = if ($hostExpected) { $idHostAgain -eq $idHost } else { $idHostAgain -match '^pid-\d+$' }
    Assert-That 'two children of one parent resolve to ONE id when a host exists' $stableAcrossChildren "first=$idHost second=$idHostAgain hostWalk=$walkOutcome"
    $idWalkOff = Invoke-Child "$clearEnv`$env:FMS_AGENT_HOST_WALK='0'; . '$identityPs1'; (Get-AgentIdentity).id"
    Assert-That 'FMS_AGENT_HOST_WALK=0 -> the pid- fallback is still reachable' ($idWalkOff -match '^pid-\d+$') $idWalkOff
    $walkOffOutcome = Invoke-Child "$clearEnv`$env:FMS_AGENT_HOST_WALK='0'; . '$identityPs1'; (Get-AgentIdentityResolution).HostWalk"
    Assert-That 'FMS_AGENT_HOST_WALK=0 -> hostWalk reads disabled' ($walkOffOutcome -eq 'disabled') $walkOffOutcome
    $walkNotReached = Invoke-Child "`$env:FMS_AGENT_ID='agent-x'; . '$identityPs1'; (Get-AgentIdentityResolution).HostWalk"
    Assert-That 'an id from the environment -> hostWalk reads not-reached' ($walkNotReached -eq 'not-reached') $walkNotReached
    $aliveSelf = Invoke-Child "$clearEnv. '$identityPs1'; Test-AgentIdentityProcessAlive -Id (Get-AgentIdentityId)"
    Assert-That 'the caller own host id reads as alive' ($aliveSelf -eq 'True') $aliveSelf
    $aliveDead = Invoke-Child ". '$identityPs1'; @((Test-AgentIdentityProcessAlive -Id 'pid-999999'), (Test-AgentIdentityProcessAlive -Id 'host-nothing-999999-123'), (Test-AgentIdentityProcessAlive -Id 'c0ace5a0-d617-402a-a82e-32612e7d7781')) -join ','"
    Assert-That 'a dead pid, a dead host id and a session guid all read as not alive' ($aliveDead -eq 'False,False,False') $aliveDead
    $aliveTicks = Invoke-Child "$clearEnv. '$identityPs1'; Test-AgentIdentityProcessAlive -Id ((Get-AgentIdentityId) -replace '-\d+`$', '-123')"
    Assert-That 'a host id whose start ticks do not match reads as not alive' ($aliveTicks -eq 'False') $aliveTicks
    $idSession = Invoke-Child "Remove-Item Env:FMS_AGENT_ID -EA SilentlyContinue; `$env:CLAUDE_CODE_SESSION_ID='sess-1234'; . '$identityPs1'; (Get-AgentIdentity).id"
    Assert-That 'session id only -> session id' ($idSession -eq 'sess-1234') $idSession
    $idExplicit = Invoke-Child "`$env:FMS_AGENT_ID='agent-x'; `$env:CLAUDE_CODE_SESSION_ID='sess-1234'; . '$identityPs1'; (Get-AgentIdentity).id"
    Assert-That 'FMS_AGENT_ID wins over the session id' ($idExplicit -eq 'agent-x') $idExplicit
    $fields = Invoke-Child "Remove-Item Env:FMS_AGENT_ID -EA SilentlyContinue; Remove-Item Env:CLAUDE_CODE_SESSION_ID -EA SilentlyContinue; Remove-Item Env:CLAUDECODE -EA SilentlyContinue; . '$identityPs1'; `$i = Get-AgentIdentity; @(`$i.id, `$i.runtime, `$i.entrypoint, `$i.model, `$i.instance, `$i.host) | ForEach-Object { if ([string]::IsNullOrWhiteSpace(`$_)) { 'EMPTY' } else { 'ok' } }"
    Assert-That 'no field is empty with the environment cleared' ($fields -notmatch 'EMPTY') $fields

    Write-Host 'Host walk decider over synthetic ancestor chains (S2417)'
    # Synthetic because the real tree cannot be arranged to order: the decider takes NAMES, so
    # every branch is provable in-process, and the same function the live walk uses is the one
    # under test - never a copy of the rule.
    $dResolved = Resolve-AgentHostWalk -AncestorNames @('pwsh', 'claude', 'explorer')
    Assert-That 'a non-pass-through ancestor is the host at its own index' ($dResolved.Index -eq 1 -and $dResolved.Outcome -eq 'resolved') "index=$($dResolved.Index) outcome=$($dResolved.Outcome)"
    $dAdopted = Resolve-AgentHostWalk -AncestorNames @('pwsh', 'pwsh', 'explorer')
    Assert-That 'a machine-wide ancestor adopts the one standing below it' ($dAdopted.Index -eq 1 -and $dAdopted.Outcome -eq 'adopted-below-explorer') "index=$($dAdopted.Index) outcome=$($dAdopted.Outcome)"
    $dNoHost = Resolve-AgentHostWalk -AncestorNames @('explorer')
    Assert-That 'a machine-wide DIRECT parent leaves nothing to adopt' ($dNoHost.Index -eq -1 -and $dNoHost.Outcome -eq 'no-host-below-explorer') "index=$($dNoHost.Index) outcome=$($dNoHost.Outcome)"
    $dLost = Resolve-AgentHostWalk -AncestorNames @('pwsh', 'bash')
    Assert-That 'a trail that runs out adopts nothing' ($dLost.Index -eq -1 -and $dLost.Outcome -eq 'no-host-trail-lost') "index=$($dLost.Index) outcome=$($dLost.Outcome)"
    $dEmpty = Resolve-AgentHostWalk -AncestorNames @()
    Assert-That 'no ancestor at all adopts nothing' ($dEmpty.Index -eq -1 -and $dEmpty.Outcome -eq 'no-host-trail-lost') "index=$($dEmpty.Index) outcome=$($dEmpty.Outcome)"
    $dDepth = Resolve-AgentHostWalk -AncestorNames @(1..12 | ForEach-Object { 'pwsh' })
    Assert-That 'twelve pass-through ancestors exhaust the depth bound' ($dDepth.Index -eq -1 -and $dDepth.Outcome -eq 'no-host-depth') "index=$($dDepth.Index) outcome=$($dDepth.Outcome)"
    $dCased = Resolve-AgentHostWalk -AncestorNames @('PwSh', 'Explorer')
    Assert-That 'the decider is case-insensitive on both name lists' ($dCased.Index -eq 0 -and $dCased.Outcome -eq 'adopted-below-explorer') "index=$($dCased.Index) outcome=$($dCased.Outcome)"

    Write-Host 'Nickname (owner ruling 2026-09-02)'
    $nick1 = Invoke-Child "Remove-Item Env:FMS_AGENT_NAME -EA SilentlyContinue; `$env:FMS_AGENT_ID='agent-nick'; `$env:FMS_AGENT_CHAT_ROOT='$root'; . '$identityPs1'; (Get-AgentIdentity).name"
    Assert-That 'a fresh id takes <adjective>-<animal>-<MMdd>-<HHmm>' ($nick1 -match '^[a-z]+-[a-z]+-\d{4}-\d{4}$') $nick1
    $nick2 = Invoke-Child "Remove-Item Env:FMS_AGENT_NAME -EA SilentlyContinue; `$env:FMS_AGENT_ID='agent-nick'; `$env:FMS_AGENT_CHAT_ROOT='$root'; . '$identityPs1'; (Get-AgentIdentity).name"
    Assert-That 'a second process of the same id answers with the same name' ($nick2 -eq $nick1) "first=$nick1 second=$nick2"
    $nickOther = Invoke-Child "Remove-Item Env:FMS_AGENT_NAME -EA SilentlyContinue; `$env:FMS_AGENT_ID='agent-nick-2'; `$env:FMS_AGENT_CHAT_ROOT='$root'; . '$identityPs1'; (Get-AgentIdentity).name"
    Assert-That 'a different id gets its own name file' ((Test-Path (Join-Path $root 'names/agent-nick.json')) -and (Test-Path (Join-Path $root 'names/agent-nick-2.json')) -and $nickOther -match '^[a-z]+-[a-z]+-\d{4}-\d{4}$') $nickOther
    $nickCustom = Invoke-Child "`$env:FMS_AGENT_NAME='owner-bench'; `$env:FMS_AGENT_ID='agent-nick'; `$env:FMS_AGENT_CHAT_ROOT='$root'; . '$identityPs1'; (Get-AgentIdentity).name"
    Assert-That 'FMS_AGENT_NAME overrides the registry' ($nickCustom -eq 'owner-bench') $nickCustom
    $env:FMS_AGENT_ID = 'agent-nick'
    try { $nickPost = Invoke-Cli @('-Verb', 'Post', '-Kind', 'note', '-Note', 'named post') } finally { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
    Assert-That 'a posted line is printed under the nickname, not the id' ($nickPost.Exit -eq 0 -and $nickPost.Text -match [regex]::Escape($nick1) -and $nickPost.Lines[0] -notmatch 'agent-nick ') $nickPost.Text
    $who = Invoke-Cli @('-Verb', 'Whoami', '-Json')
    Assert-That 'Whoami -Json carries name and id' ($who.Exit -eq 0 -and $who.Text -match '"name":"' -and $who.Text -match '"id":"') $who.Text
    Assert-That 'Whoami -Json carries a non-empty hostWalk (S2417)' ($who.Exit -eq 0 -and $who.Text -match '"hostWalk":\s*"[^"]+"') $who.Text

    Write-Host 'Post and Read'
    $p = Invoke-Cli @('-Verb', 'Post', '-Kind', 'phase', '-Ticket', 'S9999', '-Phase', '02', '-Note', 'suite post', '-Json')
    Assert-That 'Post exits 0' ($p.Exit -eq 0) $p.Text
    $posted = $null
    try { $posted = $p.Text | ConvertFrom-Json } catch { $posted = $null }
    Assert-That 'Post -Json parses with schema 1 and an agent object' ($null -ne $posted -and $posted.schema -eq 1 -and $null -ne $posted.agent -and $posted.agent.id) $p.Text
    $fileName = if ($posted) { Split-Path -Leaf $posted.path } else { '' }
    Assert-That 'file name carries kind and agent id' ($fileName -match '^\d{8}T\d{9}Z_phase_[A-Za-z0-9\-]+_[0-9a-f]{4}\.json$') $fileName
    $bogus = Invoke-Cli @('-Verb', 'Post', '-Kind', 'bogus', '-Note', 'x')
    Assert-That 'unknown kind exits 1' ($bogus.Exit -eq 1) $bogus.Text
    $r = Invoke-Cli @('-Verb', 'Read', '-Ticket', 'S9999', '-Json')
    $readObj = $null
    try { $readObj = @($r.Text | ConvertFrom-Json) } catch { $readObj = $null }
    Assert-That 'Read -Json parses and returns the post' ($null -ne $readObj -and $readObj.Count -eq 1 -and $readObj[0].note -eq 'suite post') $r.Text

    Write-Host 'Unknown schema and corrupt files are skipped'
    $progressDir = Join-Path $root 'progress'
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')
    Set-Content -LiteralPath (Join-Path $progressDir "${stamp}Z_note_ghost_0001.json") -Value '{"schema":99,"stream":"progress","at":"2026-01-01T00:00:00Z","kind":"note","note":"future"}' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $progressDir "${stamp}Z_note_ghost_0002.json") -Value '{not json' -Encoding utf8
    $ghost = @(Get-AgentChatMessages -AgentId ghost -Last 0)
    Assert-That 'schema 99 and corrupt files yield nothing' ($ghost.Count -eq 0) "count=$($ghost.Count)"
    $allRead = Invoke-Cli @('-Verb', 'Read', '-Last', '50')
    Assert-That 'Read exits 0 with ghosts on disk' ($allRead.Exit -eq 0 -and $allRead.Text -notmatch 'future') $allRead.Text

    Write-Host 'Finding staleness'
    $scopeFile = Join-Path $root 'scope/a.txt'
    Set-Content -LiteralPath $scopeFile -Value 'v1' -Encoding utf8
    (Get-Item -LiteralPath $scopeFile).LastWriteTimeUtc = [DateTime]::UtcNow.AddMinutes(-5)
    $f = New-AgentChatMessage -Stream finding -Kind check -Topic 'suite:scope' -Note 'scoped' -Scope @($scopeFile)
    Assert-That 'scope path stored repo-relative with forward slashes' ($f.scope[0] -match '^temp/S2372/chat-tests/' -and $f.scope[0] -notmatch '\\') ($f.scope -join ',')
    $v1 = Test-AgentChatFindingAlive -Finding $f
    Assert-That 'untouched scope -> alive' ($v1.Alive) $v1.Reason
    (Get-Item -LiteralPath $scopeFile).LastWriteTimeUtc = [DateTime]::UtcNow.AddSeconds(5)
    $v2 = Test-AgentChatFindingAlive -Finding $f
    Assert-That 'scope written after the post -> dead' (-not $v2.Alive -and $v2.Reason -like 'scope changed*') $v2.Reason
    $found = Find-AgentChatFindings -Topic 'suite:*'
    Assert-That 'Find counts the dead one and lists no alive' ($found.Alive.Count -eq 0 -and $found.Dead -eq 1) "alive=$($found.Alive.Count) dead=$($found.Dead)"
    $t0 = New-AgentChatMessage -Stream finding -Kind device -Topic 'suite:ttl' -Note 'ttl zero' -TtlMinutes 0
    $v3 = Test-AgentChatFindingAlive -Finding $t0
    Assert-That 'TTL 0 -> dead (expired)' (-not $v3.Alive -and $v3.Reason -eq 'expired') $v3.Reason
    $dev = New-AgentChatMessage -Stream finding -Kind device -Topic 'suite:device' -Note 'no such device' -TtlMinutes 60 -Device 'no-such-device-0000'
    $v4 = Test-AgentChatFindingAlive -Finding $dev
    Assert-That 'unlisted device -> dead' (-not $v4.Alive -and ($v4.Reason -like 'device not listed*' -or $v4.Reason -like 'adb not found*')) $v4.Reason
    $noScope = Invoke-Cli @('-Verb', 'Post', '-Finding', '-Kind', 'check', '-Topic', 'suite:none', '-Note', 'x')
    Assert-That 'finding without scope or TTL exits 1' ($noScope.Exit -eq 1 -and $noScope.Text -match 'Scope|TtlMinutes') $noScope.Text
    $tooWide = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt 17; $i++) { $tooWide.Add("temp/S2372/chat-tests/$run/scope/p$i") }
    $wide = Invoke-Cli (@('-Verb', 'Post', '-Finding', '-Kind', 'check', '-Topic', 'suite:wide', '-Note', 'x', '-Scope') + @($tooWide -join ','))
    Assert-That '17 scope paths exit 1' ($wide.Exit -eq 1 -and $wide.Text -match 'cap') $wide.Text

    Write-Host 'Find query and unrecognised parameter refusal (S2409)'
    [void](Invoke-Cli @('-Verb', 'Post', '-Finding', '-Kind', 'device', '-Topic', 'device:probe-query-test', '-TtlMinutes', '5', '-Note', 'q'))
    $qBare = Invoke-Cli @('-Verb', 'Find', '-Query', 'probe-query')
    Assert-That '-Query <bare word> finds finding containing that word' ($qBare.Exit -eq 0 -and $qBare.Text -match 'device:probe-query-test') $qBare.Text
    $qWild = Invoke-Cli @('-Verb', 'Find', '-Query', 'device:probe-*')
    Assert-That '-Query with explicit wildcard is not re-wrapped' ($qWild.Exit -eq 0 -and $qWild.Text -match 'device:probe-query-test') $qWild.Text
    $qConflict = Invoke-Cli @('-Verb', 'Find', '-Query', 'a', '-Topic', 'b')
    Assert-That '-Query together with -Topic exits 2' ($qConflict.Exit -eq 2 -and $qConflict.Text -match 'not both') $qConflict.Text
    $unknownParam = Invoke-Cli @('-Verb', 'Find', '-Topics', 'device:*')
    Assert-That 'unknown parameter name exits 2 and lists accepted names' ($unknownParam.Exit -eq 2 -and $unknownParam.Text -match '-Topics' -and $unknownParam.Text -match 'Query' -and $unknownParam.Text -match 'Topic') $unknownParam.Text

    Write-Host 'Dead findings reasons and coverage reader (S2409)'
    $expFinding = New-AgentChatMessage -Stream finding -Kind device -Topic 'suite:expired' -Note 'exp' -TtlMinutes -1
    $expVerdict = Test-AgentChatFindingAlive -Finding $expFinding
    Assert-That 'Test-AgentChatFindingAlive produces expired reason' (-not $expVerdict.Alive -and $expVerdict.Reason -eq 'expired') $expVerdict.Reason
    $deadRes = Find-AgentChatFindings -Topic 'suite:*'
    Assert-That 'DeadFindings member carries dead records with reasons' ($deadRes.DeadFindings.Count -gt 0 -and $deadRes.DeadFindings[0].Reason) "count=$($deadRes.DeadFindings.Count)"
    $reasonsJoined = (@($deadRes.DeadFindings | ForEach-Object { $_.Reason }) + $expVerdict.Reason) -join ','
    Assert-That 'reasons distinguish expiry from scope change' ($reasonsJoined -match 'expired' -and $reasonsJoined -match 'scope changed') $reasonsJoined

    [void](Invoke-Cli @('-Verb', 'Post', '-Finding', '-Kind', 'device', '-Topic', 'suite:coverage', '-TtlMinutes', '10', '-EvidenceCommand', 'req-cmd-A', '-Note', 'covA'))
    $covMatch = Get-AgentChatCoveringFinding -Topic 'suite:coverage' -Request 'req-cmd-A'
    Assert-That 'coverage matching finds record on equal request' ($null -ne $covMatch -and $covMatch.topic -eq 'suite:coverage') "match=$null"
    $covDiff = Get-AgentChatCoveringFinding -Topic 'suite:coverage' -Request 'req-cmd-B'
    Assert-That 'coverage matching returns null on different request' ($null -eq $covDiff) "match=$covDiff"

    $env:FMS_AGENT_ID = 'agent-author'
    try {
        [void](Invoke-Cli @('-Verb', 'Post', '-Finding', '-Kind', 'device', '-Topic', 'suite:own', '-TtlMinutes', '10', '-EvidenceCommand', 'req-own', '-Note', 'own'))
    } finally { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }

    $covOtherOwn = Get-AgentChatCoveringFinding -Topic 'suite:own' -Request 'req-own' -OwnAgentOnly
    Assert-That 'OwnAgentOnly returns null for another agent record' ($null -eq $covOtherOwn) "match=$covOtherOwn"
    $covOtherAny = Get-AgentChatCoveringFinding -Topic 'suite:own' -Request 'req-own'
    Assert-That 'without OwnAgentOnly returns another agent record' ($null -ne $covOtherAny) "match=$null"

    $env:FMS_AGENT_ID = 'agent-author'
    try {
        $covSelfOwn = Get-AgentChatCoveringFinding -Topic 'suite:own' -Request 'req-own' -OwnAgentOnly
        Assert-That 'OwnAgentOnly accepts caller own record' ($null -ne $covSelfOwn) "match=$null"
    } finally { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }

    [void](Invoke-Cli @('-Verb', 'Post', '-Finding', '-Kind', 'device', '-Topic', 'suite:nocmd', '-TtlMinutes', '10', '-Note', 'no cmd'))
    $covNoCmd = Get-AgentChatCoveringFinding -Topic 'suite:nocmd' -Request 'req-cmd-A'
    Assert-That 'record with no evidence command yields null match' ($null -eq $covNoCmd) "match=$covNoCmd"

    Write-Host 'Canonical device-ready request matching (S2409)'
    $devReadyScript = Join-Path $PSScriptRoot '../../devtest/device-ready.ps1'
    if (Test-Path -LiteralPath $devReadyScript) {
        . $devReadyScript
        $req1 = Get-CanonicalReadyRequest -DeviceId 'dev1' -Package 'pkgA' -ExpectedVersion '1.0' -CheckMcp $false
        $req2 = Get-CanonicalReadyRequest -DeviceId 'dev1' -Package 'pkgB' -ExpectedVersion '1.0' -CheckMcp $false
        Assert-That 'canonical requests differ for different packages' ($req1 -ne $req2) "req1=$req1 req2=$req2"

        [void](Invoke-Cli @('-Verb', 'Post', '-Finding', '-Kind', 'device', '-Topic', 'suite:devready1', '-TtlMinutes', '10', '-EvidenceCommand', $req1, '-Note', 'ready pkgA'))
        $match1 = Get-AgentChatCoveringFinding -Topic 'suite:devready1' -Request $req1
        Assert-That 'finding written for req1 covers req1' ($null -ne $match1) "match=$null"
        $match2 = Get-AgentChatCoveringFinding -Topic 'suite:devready1' -Request $req2
        Assert-That 'finding written for req1 does not cover req2' ($null -eq $match2) "match=$match2"
    }

    Write-Host 'Release-scope gates finding topic and evidence command parity (S2409)'
    $gatesRunner = Join-Path $PSScriptRoot '../../quality/assert-release-scope-gates.ps1'
    if (Test-Path -LiteralPath $gatesRunner) {
        $content = Get-Content -LiteralPath $gatesRunner -Raw
        Assert-That 'release-scope gates script posts gates:release-scope finding topic' ($content -match "'gates:release-scope'") 'topic missing'
        Assert-That 'release-scope gates script passes assert-release-scope-gates.ps1 evidence command' ($content -match "'assert-release-scope-gates\.ps1'") 'evidence command missing'
        Assert-That 'release-scope gates script queries gates:release-scope on reuse' ($content -match "Get-AgentChatCoveringFinding -Topic 'gates:release-scope'") 'reuse query topic missing'
    }

    Write-Host 'Cap and last-seen'
    $env:FMS_AGENT_CHAT_CAP_PROGRESS = '3'
    $noted = @()
    for ($i = 1; $i -le 5; $i++) {
        $noted += (New-AgentChatMessage -Kind note -Note "cap $i").path
        Start-Sleep -Milliseconds 15
    }
    [void](Invoke-AgentChatSweep)
    $remaining = @(Get-ChildItem -LiteralPath $progressDir -Filter '*.json' | Where-Object { $_.Name -match '_note_' -and $_.Name -notmatch '_ghost_' })
    $newestKept = Test-Path -LiteralPath $noted[-1]
    $oldestGone = -not (Test-Path -LiteralPath $noted[0])
    Assert-That 'progress cap 3 keeps three, the newest among them, the oldest gone' ($remaining.Count -le 3 -and $newestKept -and $oldestGone) "remaining=$($remaining.Count) newestKept=$newestKept oldestGone=$oldestGone"
    Remove-Item Env:FMS_AGENT_CHAT_CAP_PROGRESS -ErrorAction SilentlyContinue

    $mine = Get-AgentIdentityId
    $seenMine = Get-AgentChatLastSeen -AgentId $mine
    Start-Sleep -Milliseconds 1100
    $env:FMS_AGENT_ID = 'agent-other'
    try { $other = Invoke-Cli @('-Verb', 'Post', '-Kind', 'note', '-Note', 'other agent', '-Json') }
    finally { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
    Assert-That 'other agent posted (child with its own FMS_AGENT_ID)' ($other.Exit -eq 0 -and $other.Text -match '"id":"agent-other"') $other.Text
    $seenMineAfter = Get-AgentChatLastSeen -AgentId $mine
    $seenOther = Get-AgentChatLastSeen -AgentId 'agent-other'
    Assert-That 'last-seen of this agent unchanged by the other agent''s post' ($seenMine -eq $seenMineAfter) "before=$seenMine after=$seenMineAfter"
    Assert-That 'last-seen of the other agent is newer' ($null -ne $seenOther -and $seenOther -gt $seenMine) "other=$seenOther mine=$seenMine"
    $onlyOther = @(Get-AgentChatMessages -AgentId 'agent-other' -Last 0)
    Assert-That 'Read -AgentId returns only that agent' ($onlyOther.Count -eq 1 -and $onlyOther[0].agent.id -eq 'agent-other') "count=$($onlyOther.Count)"

    Write-Host 'Status and Sweep'
    $st = Invoke-Cli @('-Verb', 'Status')
    Assert-That 'Status lists both agents as live' ($st.Exit -eq 0 -and $st.Text -match 'agent-other' -and $st.Text -match [regex]::Escape($mine) -and $st.Text -notmatch 'SILENT') $st.Text
    $sw = Invoke-Cli @('-Verb', 'Sweep', '-Json')
    Assert-That 'Sweep exits 0 and reports a count' ($sw.Exit -eq 0 -and $sw.Text -match '"removed":\d+') $sw.Text
}
finally {
    Remove-Item Env:FMS_AGENT_CHAT_ROOT -ErrorAction SilentlyContinue
    Remove-Item Env:FMS_AGENT_CHAT_CAP_PROGRESS -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host ("agent-chat tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
