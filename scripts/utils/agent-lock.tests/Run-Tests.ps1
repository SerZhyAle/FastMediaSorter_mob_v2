#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for agent-lock.ps1: the domain taxonomy (S2109), the stale-JAVA_HOME
    snapshot repair (S1928), the BUILD.LOCK fail-fast refusal's exit code (S2058) and the two
    liveness keep-signals of S2408 (a running owner process; a session writing only into its
    subagent subtree).

.DESCRIPTION
    The repair sits directly in front of a refusal that gates every gradle target in the repository,
    so both of its answers matter equally: repairing when the machine is fine, and staying out of
    the way when it is not. A repair that fired too eagerly would silently swap the JVM - the exact
    outcome the capture rates worse than stopping.

    The helper is exercised directly rather than through a gradle run: it reads the environment and
    two files, so driving it needs no build, and a test that started gradle would be timing the
    daemon instead of checking the branch.

    S2058 adds a second, unrelated concern to this same file because both exercise agent-lock.ps1
    directly: Enter-BuildLockOrExit's fail-fast refusal (a held BUILD.LOCK, -NoWait) must exit 1,
    never 0 - a refusal that exits 0 is indistinguishable from a successful build to any caller that
    only reads $LASTEXITCODE, which is exactly how the defect was observed (CLAUDE.md Rule 7,
    "reachable exit codes"). The refusal is driven in a NESTED pwsh process, never in-process,
    because Enter-BuildLockOrExit calls `exit` directly - dot-sourcing it into this test process
    would terminate the test runner itself instead of producing an observable exit code.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')
# S2372: the real BUILD.LOCK hold below posts to the agent chat - keep it out of the live store.
$env:FMS_AGENT_CHAT_ROOT = Join-Path $repoRoot 'temp/S2372/chat-agent-lock-tests'

$originalJavaHome = $env:JAVA_HOME
$persistedUser = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$failures = 0

# S2577 case 10's child process, written to the sandbox at run time. It lives here as a literal
# here-string rather than as a sibling file because it is not independently runnable: it only means
# anything against the harness path and throwaway project root this suite hands it.
$s2577ProbeBody = @'
#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$HarnessPath,
    [Parameter(Mandatory)][string]$Sandbox
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$env:SZA_PROJECT_ROOT = $Sandbox
$env:FMS_AGENT_CHAT_ROOT = (Join-Path $Sandbox 'chat')
# The sweeping identity must own none of the fixtures: 'self' is never evicted and an absent
# identity reads as 'undetermined', so either would pass this probe for the wrong reason.
$env:FMS_AGENT_ID = 'agent-lock-tests-sweeper'

. $HarnessPath

$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$selfStartTicks = (Get-Process -Id $PID).StartTime.Ticks

function New-TicketFixture {
    param(
        [Parameter(Mandatory)][string]$Domain,
        [Parameter(Mandatory)][int]$Seq,
        [Parameter(Mandatory)][string]$Owner,
        [hashtable]$Extra = @{}
    )
    # procStart = 1 against this process's own live pid is the recycled-pid shape: a number that is
    # demonstrably taken paired with a start time it cannot have. It is the only way to fabricate a
    # provably dead owner without killing a process.
    $body = [ordered]@{
        schema = 1; seq = $Seq; lockType = $Domain; sessionId = $Owner
        host = $env:COMPUTERNAME; pid = $PID; procStart = 1
        reason = 'S2577 fixture'; enqueuedAt = $nowMs; transcriptPath = $null
    }
    foreach ($key in $Extra.Keys) { $body[$key] = $Extra[$key] }
    $path = Join-Path (Get-AgentLockQueueDir -Name $Domain) ('{0:0000}__{1}.json' -f $Seq, $Owner)
    Set-Content -LiteralPath $path -Value ($body | ConvertTo-Json -Compress) -Encoding utf8NoBOM
    return $path
}

$fixtures = [ordered]@{
    buildDeadPid       = (New-TicketFixture -Domain 'Build.Phone'  -Seq 1 -Owner 's2577-dead')
    buildLivePid       = (New-TicketFixture -Domain 'Build.Phone'  -Seq 2 -Owner 's2577-live' -Extra @{ procStart = $selfStartTicks })
    buildDeadHeartbeat = (New-TicketFixture -Domain 'Build.Phone'  -Seq 3 -Owner 's2577-beat' -Extra @{ lastSeenAt = $nowMs })
    buildDeadGranted   = (New-TicketFixture -Domain 'Build.Phone'  -Seq 4 -Owner 's2577-turn' -Extra @{ turnGrantedAt = $nowMs })
    codeDeadPid        = (New-TicketFixture -Domain 'Code.Scripts' -Seq 1 -Owner 's2577-code')
}

[void](Remove-StaleAgentLockTickets -Name 'Build.Phone')
[void](Remove-StaleAgentLockTickets -Name 'Code.Scripts')

$survived = [ordered]@{}
foreach ($key in $fixtures.Keys) { $survived[$key] = [bool](Test-Path -LiteralPath $fixtures[$key]) }
$survived | ConvertTo-Json -Compress
exit 0
'@

# S2582 case 11's child process. A here-string for case 10's reason and one more: it fabricates a
# BUILD.PHONE.LOCK and a queue ticket, so it is only ever safe against the throwaway project root
# this suite hands it - run anywhere else it would overwrite the machine's live build lock.
$s2582ProbeBody = @'
#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$HarnessPath,
    [Parameter(Mandatory)][string]$Sandbox
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$env:SZA_PROJECT_ROOT = $Sandbox
$env:FMS_AGENT_CHAT_ROOT = (Join-Path $Sandbox 'chat')
$env:FMS_AGENT_ID = 's2582-probe'
. $HarnessPath

# The holder is a real, live, idle child process - the only way to produce "alive and burning
# nothing" without borrowing somebody else's build, which is exactly the state processAlive: True
# could not tell apart from a healthy one.
$r = [ordered]@{}
$holder = Start-Process -FilePath 'pwsh' -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 120' -PassThru -WindowStyle Hidden
try {
    Start-Sleep -Milliseconds 700
    $holderProc = Get-Process -Id $holder.Id
    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

    function Write-LockFixture([int]$AgeMinutes) {
        $body = [ordered]@{
            schema = 2; pid = $holder.Id; procStart = $holderProc.StartTime.Ticks
            acquiredAt = $nowMs - ($AgeMinutes * 60000); reason = 'S2582 fixture'
            host = $env:COMPUTERNAME; sessionId = 's2582-holder'; transcriptPath = $null
        }
        Set-Content -LiteralPath (Get-AgentLockPath -Name 'Build.Phone') -Value ($body | ConvertTo-Json -Compress) -Encoding utf8NoBOM
    }
    function Write-TicketFixture([string]$Owner) {
        $body = [ordered]@{
            schema = 1; seq = 1; lockType = 'Build.Phone'; sessionId = $Owner
            host = $env:COMPUTERNAME; pid = $PID; procStart = (Get-Process -Id $PID).StartTime.Ticks
            reason = 'S2582 waiter'; enqueuedAt = ($nowMs - 600000); transcriptPath = $null
        }
        $path = Join-Path (Get-AgentLockQueueDir -Name 'Build.Phone') '0001__waiter.json'
        Set-Content -LiteralPath $path -Value ($body | ConvertTo-Json -Compress) -Encoding utf8NoBOM
        return $path
    }

    $profilePath = Join-Path $Sandbox '.sza-profile.json'
    $profileText = Get-Content -LiteralPath $profilePath -Raw
    $profileObj = $profileText | ConvertFrom-Json
    if ($profileObj.locks -and $profileObj.locks.buildEngine) {
        $profileObj.locks.buildEngine.busyMatch = 's2582-nonexistent-idle-target'
        Set-Content -LiteralPath $profilePath -Value ($profileObj | ConvertTo-Json -Depth 12) -Encoding utf8NoBOM
        $script:SzaProfileCache = $null
    }

    # 1. Held past the threshold, a foreign waiter, an idle tree, an idle engine -> the verdict.
    Write-LockFixture -AgeMinutes 40
    $ticketPath = Write-TicketFixture -Owner 's2582-waiter'
    $stall = Get-AgentLockStall -Name 'Build.Phone' -SampleSeconds 2
    $r.idleIsStall = ($null -ne $stall -and $stall.rule -eq 'no-cpu')
    $r.verdictNamesHolder = ($null -ne $stall -and $stall.holderPid -eq $holder.Id -and $stall.sampleSeconds -eq 2)
    $r.verdictReportsLiveProcess = ($null -ne $stall -and [bool]$stall.holderProcessAlive)
    $r.thresholdIsBuildField = ($null -ne $stall -and $stall.thresholdMinutes -eq 25)

    # 2. Younger than the threshold: no verdict, and no sampling window paid for it. The elapsed
    #    time is the assertion - a pre-filter placed after the sample would still return null.
    Write-LockFixture -AgeMinutes 3
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $young = Get-AgentLockStall -Name 'Build.Phone' -SampleSeconds 2
    $sw.Stop()
    $r.youngIsNotStall = ($null -eq $young)
    $r.youngPaidNoSample = ($sw.ElapsedMilliseconds -lt 1500)
    $r.youngElapsedMs = [int]$sw.ElapsedMilliseconds

    # 3. An empty queue: a stuck holder blocking nobody is not reported.
    Write-LockFixture -AgeMinutes 40
    Remove-Item -LiteralPath $ticketPath -Force
    $r.emptyQueueIsNotStall = ($null -eq (Get-AgentLockStall -Name 'Build.Phone' -SampleSeconds 2))

    # 4. A working engine cancels the verdict. This is the half that matters most: the engine
    #    detaches, so it is never in the holder's tree, and the profile is re-pointed at a spinner
    #    this probe starts so the branch runs without borrowing a real build.
    Write-LockFixture -AgeMinutes 40
    $ticketPath = Write-TicketFixture -Owner 's2582-waiter'
    $profilePath = Join-Path $Sandbox '.sza-profile.json'
    $profileText = Get-Content -LiteralPath $profilePath -Raw
    $spinner = Start-Process -FilePath 'pwsh' -ArgumentList '-NoProfile', '-Command',
        '$m = "s2582-spinner"; $end = (Get-Date).AddSeconds(30); while ((Get-Date) -lt $end) { $null = [math]::Sqrt(2) }' -PassThru -WindowStyle Hidden
    try {
        $profileObj = $profileText | ConvertFrom-Json
        $profileObj.locks.buildEngine.processNames = @('pwsh.exe')
        $profileObj.locks.buildEngine.busyMatch = 's2582-spinner'
        $profileObj.locks.buildEngine.busyExclude = ''
        Set-Content -LiteralPath $profilePath -Value ($profileObj | ConvertTo-Json -Depth 12) -Encoding utf8NoBOM
        $script:SzaProfileCache = $null
        Start-Sleep -Milliseconds 500
        $r.busyEngineCancels = ($null -eq (Get-AgentLockStall -Name 'Build.Phone' -SampleSeconds 2))
        # Proves the cancellation came from a measured engine and not from an unrelated null.
        $activity = Measure-AgentBuildActivity -HolderPid $holder.Id -SampleSeconds 2
        $r.busyEngineWasMeasured = ($null -ne $activity -and $activity.engineCount -ge 1 -and
                                    [double]$activity.engineCpuSeconds -ge 1.0)
        $r.engineCpuSeconds = if ($activity) { [double]$activity.engineCpuSeconds } else { -1 }
    }
    finally {
        Get-Process -Id $spinner.Id -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    }

    # 5. A project that declares no build engine gets no build verdict at all.
    $profileObj = $profileText | ConvertFrom-Json
    $profileObj.locks.PSObject.Properties.Remove('buildEngine')
    Set-Content -LiteralPath $profilePath -Value ($profileObj | ConvertTo-Json -Depth 12) -Encoding utf8NoBOM
    $script:SzaProfileCache = $null
    $r.noEngineNoVerdict = ($null -eq (Get-AgentLockStall -Name 'Build.Phone' -SampleSeconds 2))
    Set-Content -LiteralPath $profilePath -Value $profileText -Encoding utf8NoBOM
    $script:SzaProfileCache = $null

    # 6. A dead holder: already stale, already reclaimable, nothing for a human to do about it.
    [void](Write-TicketFixture -Owner 's2582-waiter')
    Stop-Process -Id $holder.Id -Force
    Start-Sleep -Milliseconds 500
    $r.deadHolderIsNotStall = ($null -eq (Get-AgentLockStall -Name 'Build.Phone' -SampleSeconds 2))
}
finally {
    Get-Process -Id $holder.Id -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
}

$r | ConvertTo-Json -Compress
exit 0
'@

# S2697 case 12's child process. A profile-declared domain the harness has no hardcoded row for must
# work through every entry point, and the handoff must carry the changed set. It runs against a
# throwaway project root whose profile adds Code.Fixture, for case 10's reasons: the domain table is
# read from the profile once per process, and the entry points are real processes taking real locks.
$s2697ProbeBody = @'
#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$LocksDir,
    [Parameter(Mandatory)][string]$Sandbox
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$env:SZA_PROJECT_ROOT = $Sandbox
$env:SZA_PROFILE_PATH = (Join-Path $Sandbox '.sza-profile.json')
$env:FMS_AGENT_CHAT_ROOT = (Join-Path $Sandbox 'chat')
$env:FMS_AGENT_ID = 's2697-self'
. (Join-Path $LocksDir 'agent-lock.ps1')

$r = [ordered]@{}
$logDir = Join-Path $Sandbox 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$step = 0
function Invoke-Entry {
    param([Parameter(Mandatory)][string]$Script, [Parameter(Mandatory)][string]$AgentId, [string[]]$Arguments = @())
    $script:step++
    $prior = $env:FMS_AGENT_ID
    $env:FMS_AGENT_ID = $AgentId
    try { & pwsh -NoProfile -File (Join-Path $LocksDir $Script) @Arguments *> (Join-Path $logDir ('{0:00}-{1}.log' -f $script:step, $Script)) }
    finally { $env:FMS_AGENT_ID = $prior }
    return $LASTEXITCODE
}
$locks = Get-SzaPath 'locksDir'
$fixtureLock = Join-Path $locks 'CODE.FIXTURE.LOCK'
$scriptsLock = Join-Path $locks 'CODE.SCRIPTS.LOCK'
$scriptsQueue = Join-Path $locks 'CODE.SCRIPTS.QUEUE'
function Test-ScriptsUntouched { -not (Test-Path -LiteralPath $scriptsLock) -and -not (Test-Path -LiteralPath $scriptsQueue) }

$r.bareCode = (@(Resolve-AgentLockDomains -Name 'Code') -join ',')
$fixtureTimings = Get-AgentLockTimings -Name 'Code.Fixture'
$codeTimings = Get-AgentLockTimings -Name 'Code'
$r.inheritsCodeTimings = (($fixtureTimings | ConvertTo-Json -Compress) -eq ($codeTimings | ConvertTo-Json -Compress))
$r.existingTimingsKept = ((Get-AgentLockTimings -Name 'Code.Scripts').LockStaleMinutes -eq 10 -and
    (Get-AgentLockTimings -Name 'Build.Phone').StallMinutes -eq 25 -and (Get-AgentLockTimings -Name 'SpecTicket').TicketCeilingMinutes -eq 480)
$timingsError = ''
try { [void](Get-AgentLockTimings -Name 'Code.Nope') } catch { $timingsError = $_.Exception.Message }
$resolveError = ''
try { [void](Resolve-AgentLockDomains -Name 'Code.Nope') } catch { $resolveError = $_.Exception.Message }
$r.unknownTimingsThrowsProfileList = ($timingsError -match "Unknown coordination resource name 'Code\.Nope'" -and
    $timingsError -match 'Code\.Fixture' -and $timingsError -match 'SpecTicket' -and $timingsError -match 'Device')
$r.unknownResolveThrowsProfileList = ($resolveError -match "Unknown coordination resource name 'Code\.Nope'" -and $resolveError -match 'Code\.Fixture')
$r.unknownStatusExit = (Invoke-Entry 'lock-status.ps1' 's2697-self' @('-Name', 'Code.Nope'))
$r.unknownExitCodeLockExit = (Invoke-Entry 'exit-code-lock.ps1' 's2697-self' @('-Name', 'Code.Nope'))

$r.enterExit = (Invoke-Entry 'enter-code-lock.ps1' 's2697-self' @('-Reason', 'S2697 probe', '-Files', 'temp/lock-fixture/probe.txt'))
$r.enterTookFixtureOnly = ((Test-Path -LiteralPath $fixtureLock) -and (Test-ScriptsUntouched))
$r.statusExit = (Invoke-Entry 'lock-status.ps1' 's2697-self' @('-Name', 'Code.Fixture', '-Queue', '-Json'))
$statusText = Get-Content -LiteralPath (Join-Path $logDir ('{0:00}-lock-status.ps1.log' -f $step)) -Raw
$r.statusSaysHeld = ($statusText -match '"status"\s*:\s*"held"')

# Contention: a stranger asks for the same domain with a messy form of the same set.
$handoffDir = Get-SzaPath 'lockHandoffDir'
$r.contendedExit = (Invoke-Entry 'enter-code-lock.ps1' 's2697-foreign' @('-Reason', 'S2697 contender',
    '-Files', 'temp\lock-fixture\probe.txt,./temp/lock-fixture/b.txt,temp/lock-fixture/probe.txt'))
$handoff = @(Get-ChildItem -LiteralPath $handoffDir -Filter 'HANDOFF-CODE.FIXTURE-*.json' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)[0]
$h = if ($handoff) { Get-Content -LiteralPath $handoff.FullName -Raw | ConvertFrom-Json } else { $null }
$r.handoffSchema = if ($h) { $h.schema } else { $null }
$r.handoffPaths = if ($h -and $h.PSObject.Properties['paths']) { (@($h.paths) -join '|') } else { '<absent>' }
$queued = @(Get-AgentLockQueue -Name 'Code.Fixture' | Where-Object { [string]$_.sessionId -eq 's2697-foreign' })
$foreignSeq = if ($queued.Count -gt 0) { [int]$queued[0].seq } else { -1 }
$r.contendedScriptsUntouched = (Test-ScriptsUntouched)

# A declared domain with no paths writes an empty array, not an absent field.
[string]$emptyPath = Save-AgentLockTicketHandoff -Tickets @{ 'Code.Fixture' = $queued[0] } -Reason 'S2697 empty'
$r.emptyPathsIsArray = ((Get-Content -LiteralPath $emptyPath -Raw) -match '"paths":\[\]')

$r.releaseExit = (Invoke-Entry 'exit-code-lock.ps1' 's2697-self' @('-Name', 'Code.Fixture'))
$r.released = (-not (Test-Path -LiteralPath $fixtureLock))

# The waiter adopts both shapes: the schema-2 file just written, and a schema-1 file with no paths.
$waitArgs = @('-Name', 'Code.Fixture', '-Reason', 'S2697 waiter', '-WaitTimeoutSeconds', '30', '-PollSeconds', '1', '-Handoff')
$marker = Join-Path $locks 'CODE.FIXTURE.TURN-s2697-waiter.json'
$r.waitSchema2Exit = if ($handoff) { Invoke-Entry 'wait-for-lock-turn.ps1' 's2697-waiter' ($waitArgs + $handoff.FullName) } else { -1 }
$r.waitSchema2Adopted = ((Test-Path -LiteralPath $marker) -and [int](Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).seq -eq $foreignSeq)
Remove-Item -LiteralPath $marker -Force -ErrorAction SilentlyContinue
$legacy = Join-Path $handoffDir 'HANDOFF-CODE.FIXTURE-legacy-schema1.json'
([ordered]@{ schema = 1; reason = 'S2697 legacy'; sessionId = 's2697-foreign'
    createdAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); tickets = [ordered]@{ 'Code.Fixture' = $foreignSeq } } |
    ConvertTo-Json -Compress) | Set-Content -LiteralPath $legacy -Encoding utf8NoBOM
$r.waitSchema1Exit = (Invoke-Entry 'wait-for-lock-turn.ps1' 's2697-waiter' ($waitArgs + $legacy))
$r.waitSchema1Adopted = ((Test-Path -LiteralPath $marker) -and [int](Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).seq -eq $foreignSeq)
$r.noSecondTicket = (@(Get-AgentLockQueue -Name 'Code.Fixture').Count -eq 1)

$r.withdrawExit = (Invoke-Entry 'withdraw-lock-ticket.ps1' 's2697-foreign' @('-Name', 'Code.Fixture'))
$r.withdrawn = (@(Get-AgentLockQueue -Name 'Code.Fixture').Count -eq 0)
$r.scriptsNeverTouched = (Test-ScriptsUntouched)
$r | ConvertTo-Json -Compress
exit 0
'@

function Assert-Case {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Ok, [string]$Detail)
    if ($Ok) { Write-Output "  PASS $Name" }
    else { Write-Output "  FAIL $Name - $Detail"; $script:failures++ }
}

try {
    # 0. S2109 domain taxonomy. These cases touch no lock file at all - they read the table and
    #    the two path builders - so they run first and unconditionally, before any case that has
    #    to skip itself around a live BUILD.LOCK.
    $codeDomains = @(Resolve-AgentLockDomains -Name 'Code')
    Assert-Case -Name 'a bare Code resolves to the three code domains in canonical order' `
        -Ok (($codeDomains -join ',') -eq 'Code.Phone,Code.Wear,Code.Scripts') `
        -Detail "got '$($codeDomains -join ',')'"

    $buildDomains = @(Resolve-AgentLockDomains -Name 'Build')
    Assert-Case -Name 'a bare Build resolves to both build domains in canonical order' `
        -Ok (($buildDomains -join ',') -eq 'Build.Phone,Build.Wear') `
        -Detail "got '$($buildDomains -join ',')'"

    Assert-Case -Name 'a concrete domain resolves to itself' `
        -Ok ((@(Resolve-AgentLockDomains -Name 'Code.Wear') -join ',') -eq 'Code.Wear') `
        -Detail 'a concrete domain did not resolve to a single-element set'

    # An unknown name must throw rather than return empty (strategic S2109 section 11 criterion 6):
    # an empty set is a lock nobody holds and every caller believes in.
    $unknownThrew = $false
    try { Resolve-AgentLockDomains -Name 'Code.Tablet' | Out-Null }
    catch { $unknownThrew = $true }
    Assert-Case -Name 'an unknown resource name throws instead of resolving to nothing' `
        -Ok $unknownThrew -Detail 'Resolve-AgentLockDomains returned quietly for an unknown name'

    Assert-Case -Name 'two domains produce two different lock paths' `
        -Ok ((Get-AgentLockPath -Name 'Code.Wear') -ne (Get-AgentLockPath -Name 'Code.Phone')) `
        -Detail 'two distinct domains share one lock file'

    Assert-Case -Name 'a bare name still resolves to the pre-split path' `
        -Ok ((Get-AgentLockPath -Name 'Code').EndsWith('CODE.LOCK') -and
             (Get-AgentLockPath -Name 'Build').EndsWith('BUILD.LOCK')) `
        -Detail 'a bare name no longer points at the path every existing caller uses'

    Assert-Case -Name 'every concrete domain has its own timings record' `
        -Ok (@('Build.Phone', 'Build.Wear', 'Code.Phone', 'Code.Wear', 'Code.Scripts' |
            Where-Object { $null -eq (Get-AgentLockTimings -Name $_) }).Count -eq 0) `
        -Detail 'a concrete domain resolved to no timings record'

    # S2109 derivation table. These are strategic criteria 1 and 2 stated as arithmetic on the
    # mapping: the whole benefit of the split is that these sets come out DISJOINT, and a table
    # without a test is one rename away from quietly mapping everything to the full set again -
    # which would restore the old serialisation while every banner still printed a domain name.
    $wearSet = @(Resolve-CodeDomainsForPaths -Path @('wear/src/main/java/A.kt'))
    $phoneSet = @(Resolve-CodeDomainsForPaths -Path @('app_v2/src/main/java/B.kt'))
    $scriptSet = @(Resolve-CodeDomainsForPaths -Path @('scripts/post-change.ps1'))
    Assert-Case -Name 'disjoint code domains: a wear set and a phone set share none' `
        -Ok (@($wearSet | Where-Object { $phoneSet -contains $_ }).Count -eq 0 -and
             $wearSet.Count -eq 1 -and $phoneSet.Count -eq 1) `
        -Detail "wear=$($wearSet -join ','), phone=$($phoneSet -join ',')"
    Assert-Case -Name 'disjoint code domains: a scripts set shares none with either module' `
        -Ok (@($scriptSet | Where-Object { $wearSet -contains $_ -or $phoneSet -contains $_ }).Count -eq 0) `
        -Detail "scripts=$($scriptSet -join ',')"
    Assert-Case -Name 'a set spanning both modules resolves to both module domains' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt', 'app_v2/src/B.kt')) -join ',') -eq 'Code.Phone,Code.Wear') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt','app_v2/src/B.kt')) -join ',')'"
    # A module's OWN build file is deliberately NOT that module's domain - the configuration phase
    # processes every subproject, so a broken one fails a check requested for the other module.
    Assert-Case -Name 'a build file resolves to the full code set, not to its own module' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('wear/build.gradle.kts')).Count -eq 3) -and
             (@(Resolve-CodeDomainsForPaths -Path @('settings.gradle.kts')).Count -eq 3)) `
        -Detail "wear build file=$(@(Resolve-CodeDomainsForPaths -Path @('wear/build.gradle.kts')) -join ',')"
    Assert-Case -Name 'an unrecognised path fails closed to the full code set' `
        -Ok (@(Resolve-CodeDomainsForPaths -Path @('brand_new_module/src/A.kt')).Count -eq 3) `
        -Detail 'an unknown path narrowed the set instead of widening it'

    # A leading './' has to be stripped as a PREFIX. TrimStart('./') takes a character SET, so it
    # also ate the dot of a dotfile path: '.claude/..' became 'claude/..', matched no branch, and
    # fell through to the fail-closed full set. Every command, hook, skill and agent-memory edit
    # therefore took all three code domains - the most common changed set in this repository.
    $dotSet = @(Resolve-CodeDomainsForPaths -Path @('.claude/commands/spec-all.md'))
    $ghSet = @(Resolve-CodeDomainsForPaths -Path @('.github/workflows/ci.yml'))
    Assert-Case -Name 'a dotfile path keeps its leading dot and resolves to Code.Scripts' `
        -Ok (($dotSet -join ',') -eq 'Code.Scripts' -and ($ghSet -join ',') -eq 'Code.Scripts') `
        -Detail ".claude=$($dotSet -join ','), .github=$($ghSet -join ',')"
    Assert-Case -Name "a leading './' is still stripped from a module path" `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('./app_v2/src/B.kt')) -join ',') -eq 'Code.Phone') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('./app_v2/src/B.kt')) -join ',')'"

    # A per-module detekt baseline is that module's file, not a shared config: it is named for its
    # module and no other module's check reads it. Widening on it serialised every Kotlin closure
    # that regenerated one. The shared config beside it must still fail closed.
    $baselineSet = @(Resolve-CodeDomainsForPaths -Path @('app_v2/src/B.kt,config/detekt/baseline-app_v2.xml'))
    Assert-Case -Name "a module's detekt baseline stays in that module's domain" `
        -Ok (($baselineSet -join ',') -eq 'Code.Phone' -and
             (@(Resolve-CodeDomainsForPaths -Path @('config/detekt/baseline-wear.ids')) -join ',') -eq 'Code.Wear') `
        -Detail "phone edit + its baseline=$($baselineSet -join ',')"
    Assert-Case -Name 'the shared detekt config still resolves to the full code set' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('config/detekt/detekt.yml')).Count -eq 3) -and
             (@(Resolve-CodeDomainsForPaths -Path @('config/detekt/rule-categories.txt')).Count -eq 3)) `
        -Detail "detekt.yml=$(@(Resolve-CodeDomainsForPaths -Path @('config/detekt/detekt.yml')) -join ',')"

    # `pwsh -File` collapses a comma list into ONE string element, so an unsplit list matched only
    # its first prefix and resolved NARROWER than the change - the one direction this must never
    # fail in. Observed live: -Files with three script paths reported "1 changed path".
    Assert-Case -Name 'a comma-joined file list is split, not matched as one path' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt,app_v2/src/B.kt')) -join ',') -eq 'Code.Phone,Code.Wear') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('wear/src/A.kt,app_v2/src/B.kt')) -join ',')' - a collapsed list must not narrow the set"

    # S2338: PLAN/ is the table's one exemption - it resolves to NO domain, because every path
    # under it is already exclusive by ticket lease (a spec file belongs to one ticket) or by the
    # catalog mutex (the journals and both release files). Measured 2026-09-02: 55% of recent
    # closures touched PLAN/ and nothing else, so before the exemption the majority of them took a
    # domain that protected nothing while serialising every other scripts/docs edit in the repo.
    $planSet = @(Resolve-CodeDomainsForPaths -Path @('PLAN/S2338_lock-domain-claims-specs-and-docs.md'))
    $planQueueSet = @(Resolve-CodeDomainsForPaths -Path @('PLAN/RELEASE_QUEUE.md'))
    Assert-Case -Name 'a PLAN-only set resolves to no code domain at all' `
        -Ok ($planSet.Count -eq 0 -and $planQueueSet.Count -eq 0) `
        -Detail "spec file=$($planSet.Count) domain(s), RELEASE_QUEUE=$($planQueueSet.Count) domain(s) - both must be 0"
    # The exemption must not leak into a mixed set: a change that also touches a module still takes
    # that module's domain, and the PLAN path simply contributes nothing to it.
    Assert-Case -Name 'a PLAN path mixed with a module path resolves to the module domain only' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('PLAN/S2338_x.md', 'app_v2/src/B.kt')) -join ',') -eq 'Code.Phone') `
        -Detail "got '$(@(Resolve-CodeDomainsForPaths -Path @('PLAN/S2338_x.md','app_v2/src/B.kt')) -join ',')'"
    # docs/ and dev/ are deliberately NOT exempt. They are hand-edited prose with no finer
    # mechanism over them, so a concurrent edit there is an ordinary lost update. Narrowing them
    # out along with PLAN/ is the tempting mistake this case exists to catch.
    Assert-Case -Name 'docs/ and dev/ still resolve to Code.Scripts' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @('docs/ARCHITECTURE.md')) -join ',') -eq 'Code.Scripts' -and
             (@(Resolve-CodeDomainsForPaths -Path @('dev/CHANGELOG.md')) -join ',') -eq 'Code.Scripts') `
        -Detail "docs=$(@(Resolve-CodeDomainsForPaths -Path @('docs/ARCHITECTURE.md')) -join ','), dev=$(@(Resolve-CodeDomainsForPaths -Path @('dev/CHANGELOG.md')) -join ',')"
    # S2342: content with no code in it resolves to Code.Scripts instead of failing closed. None of
    # these paths compiles, links or packs into an APK, so the full set protected nothing and
    # serialised phone and watch work against a store-listing edit. Measured 2026-09-02: of the 11
    # recent changed sets that took the full code set, 8 were content only.
    $contentTrees = @(
        'play/listing/README.md', 'fastlane/metadata/android/en-US/title.txt',
        'store_assets/design_brief.md', 'delivery/INVENTORY.md', 'maestro/README.md'
    )
    $widerTree = @($contentTrees | Where-Object {
        (@(Resolve-CodeDomainsForPaths -Path @($_)) -join ',') -ne 'Code.Scripts' })
    Assert-Case -Name 'a store/content tree resolves to Code.Scripts, not the full set' `
        -Ok ($widerTree.Count -eq 0) -Detail "still wider than Code.Scripts: $($widerTree -join ', ')"

    $rootContent = @(
        'index.html', 'index-ru.html', 'nolegal-uk.html', 'styles.css', 'sitemap.xml', 'robots.txt',
        '_config.yml', '_typos.toml', 'GEMINI.md', 'LICENSE', 'THIRD_PARTY_LICENSES.md',
        'favicon.ico', 'favicon-32x32.png', 'icon.png', 'apple-touch-icon.png'
    )
    $widerRoot = @($rootContent | Where-Object {
        (@(Resolve-CodeDomainsForPaths -Path @($_)) -join ',') -ne 'Code.Scripts' })
    Assert-Case -Name 'a root site page, document or icon resolves to Code.Scripts' `
        -Ok ($widerRoot.Count -eq 0) -Detail "still wider than Code.Scripts: $($widerRoot -join ', ')"

    # The motivating call (S2340 phase 03): one repository script plus one listing file used to take
    # all three domains and queue behind a wear session it could not possibly conflict with.
    $motivating = @(Resolve-CodeDomainsForPaths -Path @('scripts/release/publish-play-listing.py,play/listing/README.md'))
    Assert-Case -Name 'a script plus a listing file resolves to Code.Scripts alone' `
        -Ok (($motivating -join ',') -eq 'Code.Scripts') -Detail "got '$($motivating -join ',')'"

    # The fail-closed remainder is the point of the branch, so it is asserted rather than assumed:
    # naming content trees must not become a habit of naming any directory that shows up. corex/ is
    # unrecognised source, and benchmark/ and watchface/ are real Gradle modules with no Build.*
    # domain of their own - all three must keep taking every code domain.
    $stillClosed = @(
        'corex/androidx/core/content/ContextCompat.java', 'benchmark/src/A.kt', 'watchface/src/A.kt'
    )
    $narrowed = @($stillClosed | Where-Object { @(Resolve-CodeDomainsForPaths -Path @($_)).Count -ne 3 })
    Assert-Case -Name 'unrecognised source and the domain-less modules still fail closed' `
        -Ok ($narrowed.Count -eq 0) -Detail "narrowed instead of failing closed: $($narrowed -join ', ')"

    # "Every path was exempt" and "there were no paths" are different questions with opposite safe
    # answers. Collapsing them would send every PLAN-only closure back to the full set, silently
    # undoing the exemption while every test above still passed.
    Assert-Case -Name 'an empty input still fails closed to the full code set' `
        -Ok ((@(Resolve-CodeDomainsForPaths -Path @()).Count -eq 3) -and
             (@(Resolve-CodeDomainsForPaths -Path @('')).Count -eq 3)) `
        -Detail "empty array=$(@(Resolve-CodeDomainsForPaths -Path @()).Count), empty string=$(@(Resolve-CodeDomainsForPaths -Path @('')).Count) - both must be 3"

    # Deliberately table-only. Proving disjointness by actually TAKING the two sets belongs in
    # test-agent-lock-queue.ps1 (case 11), which runs in a throwaway sandbox: this file resolves
    # against the real repository root, so acquiring here would contend with whatever sibling
    # session is editing right now - and releasing afterwards would drop a lock this process never
    # owned. A test that can hand away a working session's lock is worse than the gap it closes.

    $missingDir = Join-Path $repoRoot 'temp/S1928-no-such-jdk'

    # 1. Stale snapshot, persisted value usable and different -> repaired, and the scope is named.
    if ([string]::IsNullOrWhiteSpace($persistedUser) -or
        (Test-JvmHomeMissingParts -JvmHome $persistedUser).Count -gt 0) {
        # Nothing to assert against on a machine with no usable persisted value. Say so rather
        # than passing: a case that silently did not run reads exactly like one that succeeded.
        Write-Output '  SKIP stale snapshot repaired - this machine has no usable persisted JAVA_HOME'
    }
    else {
        $repair = Resolve-PersistedJavaHomeRepair -CurrentValue $missingDir
        Assert-Case -Name 'stale snapshot is repaired from the persisted value' `
            -Ok ($null -ne $repair -and $repair.Path -eq $persistedUser) `
            -Detail "got '$($repair.Path)', expected '$persistedUser'"
        Assert-Case -Name 'the repair names the scope it came from' `
            -Ok ($null -ne $repair -and $repair.Scope -in @('User', 'Machine')) `
            -Detail "scope was '$($repair.Scope)'"
    }

    # 2. Persisted value equal to the snapshot -> nothing to refresh, the JDK really is gone.
    $sameValue = Resolve-PersistedJavaHomeRepair -CurrentValue $persistedUser
    Assert-Case -Name 'a persisted value equal to the snapshot is not a repair' `
        -Ok ($null -eq $sameValue -or $sameValue.Path -ne $persistedUser) `
        -Detail 'the helper offered the value the caller already had'

    # 3. Persisted value itself unusable -> no repair. Simulated by asking the usability probe
    #    directly, since the persisted variable cannot be rewritten from a test.
    Assert-Case -Name 'an unusable path is rejected by the usability probe' `
        -Ok ((Test-JvmHomeMissingParts -JvmHome $missingDir).Count -gt 0) `
        -Detail 'a non-existent JDK directory was judged usable'

    # 4. The healthy path pays nothing: a usable snapshot never reaches the helper at all, which is
    #    a property of the caller's guard rather than of the helper.
    # S2402: the local path is a generated forwarder to the canon-shipped harness, so a check that reads
# the SUBJECT'S TEXT has to read the shipped file - the forwarder's body is not the mechanism. The
# resolution is the forwarder's own, taken from it rather than restated: SZA_HARNESS_ROOT, then the
# plugin cache's newest version, then the canon checkout.
function Get-AgentLockSourcePath {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $local = Join-Path $RepoRoot 'scripts/utils/agent-lock.ps1'
    $body = Get-Content -LiteralPath $local -Raw
    if ($body -notmatch 'Forwarder to the canon-shipped harness') { return $local }
    $candidates = @()
    if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
    $cache = Join-Path $env:USERPROFILE '.claude\plugins\cache\sza-unified-rules\sza'
    if (Test-Path -LiteralPath $cache) {
        $candidates += @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'tools\harness' })
    }
    $checkout = if ($env:SZA_CANON_ROOT) { $env:SZA_CANON_ROOT } else { 'P:\WEB\sza-unified-rules' }
    $candidates += (Join-Path $checkout 'tools\harness')
    foreach ($c in $candidates) {
        $p = Join-Path $c 'locks\agent-lock.ps1'
        if (Test-Path -LiteralPath $p) { return $p }
    }
    throw "agent-lock.tests: the local agent-lock.ps1 is a forwarder and the shipped harness was not found in: $($candidates -join '; ')"
}

$agentLockSourcePath = Get-AgentLockSourcePath -RepoRoot $repoRoot
$guardSource = Get-Content -LiteralPath $agentLockSourcePath -Raw
    Assert-Case -Name 'the helper is only consulted after the snapshot is judged unusable' `
        -Ok ($guardSource -match '(?s)if \(\$launcherMissing\.Count -gt 0\) \{\s*\r?\n\s*Resolve-PersistedJavaHomeRepair') `
        -Detail 'the repair is not guarded by the unusable-snapshot condition'

    # 5. The refusal must survive intact for the case with nothing to repair.
    Assert-Case -Name 'the original refusal and its exit code are unchanged' `
        -Ok ($guardSource -match 'Launcher JVM unusable - refusing to start gradle\. Nothing was built\.') `
        -Detail 'the refusal text was altered or removed'

    # Both S2058 cases below fabricate or hold a real temp/BUILD.LOCK, so both are skipped rather
    # than forced when a real lock is already live: stealing it here would corrupt whatever build
    # or gate holds it (CLAUDE.md Rule 23 - never contend for BUILD.LOCK outside its own protocol).
    $preExisting = Get-AgentLockStatus -Name Build
    if ($preExisting.Exists -and -not $preExisting.Stale) {
        Write-Output "  SKIP fail-fast refusal exits 1 - BUILD.LOCK is already live (pid $($preExisting.Pid))"
        Write-Output "  SKIP genuine nested reuse still succeeds - BUILD.LOCK is already live (pid $($preExisting.Pid))"
    }
    else {
        # 6. S2058 regression: the re-entrancy guard used to match on a bare inherited PID. A
        #    process that inherits FMS_BUILD_LOCK_HELD_BY from an ancestor which is NOT the
        #    current lock's actual holder - simulating Windows reusing that PID for an unrelated,
        #    later holder - must still refuse/queue, never silently `return` as if self-held. Real
        #    PID reuse cannot be forced from a test, so this fabricates the mismatch directly: a
        #    lock file whose recorded pid is this test process's own (real, alive) PID, paired
        #    with an inherited env value that names the SAME pid but the WRONG start ticks - the
        #    one piece of information a reused PID cannot carry forward.
        $selfProc = Get-Process -Id $PID
        $fakeLockPath = Get-AgentLockPath -Name Build
        $fakeLockBody = [ordered]@{
            schema = 2; lockType = 'Build'; pid = $PID; procStart = $selfProc.StartTime.Ticks
            acquiredAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            reason = 'S2058-simulated-unrelated-holder'; host = $env:COMPUTERNAME
            sessionId = $null; transcriptPath = $null
        } | ConvertTo-Json -Compress
        Set-Content -LiteralPath $fakeLockPath -Value $fakeLockBody -Encoding utf8NoBOM
        $originalHeldBy = $env:FMS_BUILD_LOCK_HELD_BY
        try {
            $env:FMS_BUILD_LOCK_HELD_BY = "$PID`:1"
            $childCommand = ". `"$repoRoot\scripts\utils\agent-lock.ps1`"; " +
                "Enter-BuildLockOrExit -Reason 'S2058-regression-mismatched-ticks' -NoWait"
            $proc = Start-Process -FilePath 'pwsh' `
                -ArgumentList @('-NoProfile', '-Command', $childCommand) `
                -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $repoRoot 'temp/S2058-refusal-stdout.log') `
                -RedirectStandardError (Join-Path $repoRoot 'temp/S2058-refusal-stderr.log')
            Assert-Case -Name 'a PID-reused inherited holder is refused, not treated as self-held' `
                -Ok ($proc.ExitCode -eq 1) `
                -Detail "child process exited $($proc.ExitCode), expected 1 (fail-fast refusal)"
        }
        finally {
            $env:FMS_BUILD_LOCK_HELD_BY = $originalHeldBy
            Remove-Item -LiteralPath $fakeLockPath -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-refusal-stdout.log') -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-refusal-stderr.log') -Force -ErrorAction SilentlyContinue
        }

        # 7. Positive control for case 6: a genuinely nested subprocess of a run that really does
        #    hold BUILD.LOCK (matching pid AND start ticks) must still take the fast reuse path -
        #    the fix must not turn every nested invocation into an unwanted queue/refuse.
        $acquired = Enter-AgentLock -Name Build -Reason 'S2058-regression-test-hold'
        if (-not $acquired.Acquired) {
            Write-Output '  SKIP genuine nested reuse still succeeds - could not acquire BUILD.LOCK for the probe'
        }
        else {
            try {
                $childCommand = ". `"$repoRoot\scripts\utils\agent-lock.ps1`"; " +
                    "Enter-BuildLockOrExit -Reason 'S2058-regression-genuine-reuse' -NoWait"
                $proc = Start-Process -FilePath 'pwsh' `
                    -ArgumentList @('-NoProfile', '-Command', $childCommand) `
                    -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $repoRoot 'temp/S2058-reuse-stdout.log') `
                    -RedirectStandardError (Join-Path $repoRoot 'temp/S2058-reuse-stderr.log')
                Assert-Case -Name 'genuine nested reuse (matching pid and start ticks) still succeeds' `
                    -Ok ($proc.ExitCode -eq 0) `
                    -Detail "child process exited $($proc.ExitCode), expected 0 (fast reuse path)"
            }
            finally {
                Exit-AgentLock -Name Build
                Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-reuse-stdout.log') -Force -ErrorAction SilentlyContinue
                Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2058-reuse-stderr.log') -Force -ErrorAction SilentlyContinue
            }
        }
    }

    # 8. S2405 turn marker cleanup test: Remove-StaleTurnMarkers deletes markers older than cutoff
    #    while preserving markers younger than cutoff.
    $tempDir = Join-Path $repoRoot 'temp'
    $oldMarker = Join-Path $tempDir "CODE.PHONE.TURN-test-old-s2405.json"
    $freshMarker = Join-Path $tempDir "CODE.PHONE.TURN-test-fresh-s2405.json"
    try {
        Set-Content -LiteralPath $oldMarker -Value '{"outcome":"granted"}' -Encoding utf8NoBOM
        Set-Content -LiteralPath $freshMarker -Value '{"outcome":"granted"}' -Encoding utf8NoBOM
        (Get-Item -LiteralPath $oldMarker).LastWriteTime = (Get-Date).AddMinutes(-30)

        [void](Remove-StaleTurnMarkers -Name 'Code.Phone')

        $oldDeleted = -not (Test-Path -LiteralPath $oldMarker)
        $freshKept = Test-Path -LiteralPath $freshMarker

        Assert-Case -Name 'S2405: Remove-StaleTurnMarkers deletes turn marker older than cutoff' `
            -Ok $oldDeleted -Detail 'old turn marker was not deleted'
        Assert-Case -Name 'S2405: Remove-StaleTurnMarkers preserves fresh turn marker' `
            -Ok $freshKept -Detail 'fresh turn marker was deleted'
    }
    finally {
        Remove-Item -LiteralPath $oldMarker -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $freshMarker -Force -ErrorAction SilentlyContinue
    }

    # 9. S2408: the two keep-signals. Both are one-directional - they may only answer live - so
    #    each case here proves that a working owner is NOT evicted, which is the failure that
    #    opened the ticket (a Code.Scripts lock taken from a session mid-edit on 2026-09-03).
    $selfStart = (Get-Process -Id $PID).StartTime
    # Written by this very process, so the record cannot predate the process - the shape every
    # real record has. StaleMinutes 0 puts it outside the clock-based window, so only the process
    # check can answer live.
    $liveTicket = [pscustomobject]@{
        sessionId      = "pid-$PID"
        transcriptPath = $null
        enqueuedAt     = [DateTimeOffset]::new($selfStart).ToUnixTimeMilliseconds()
    }
    # A record from BEFORE this process existed cannot have been written by it: this is the
    # recycled-pid case, and the keep-signal must refuse to resurrect its owner.
    $recycledTicket = [pscustomobject]@{
        sessionId      = "pid-$PID"
        transcriptPath = $null
        enqueuedAt     = [DateTimeOffset]::UtcNow.AddHours(-9).ToUnixTimeMilliseconds()
    }
    $liveVerdict = 'unset'
    $recycledVerdict = 'unset'
    # The owner must not be US, or the verdict short-circuits to 'self' and proves nothing.
    $previousAgentId = $env:FMS_AGENT_ID
    try {
        $env:FMS_AGENT_ID = 'agent-lock-tests-observer'
        $liveVerdict = Get-AgentTicketLiveness -Ticket $liveTicket -StaleMinutes 0
        $recycledVerdict = Get-AgentTicketLiveness -Ticket $recycledTicket -StaleMinutes 15
    }
    finally {
        if ($null -eq $previousAgentId) { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
        else { $env:FMS_AGENT_ID = $previousAgentId }
    }
    Assert-Case -Name 'S2408: an owner whose process is running is live past the clock window' `
        -Ok ($liveVerdict -eq 'foreign-live') -Detail "verdict was '$liveVerdict', expected foreign-live"
    Assert-Case -Name 'S2408: a pid whose process is younger than the record does not revive it' `
        -Ok ($recycledVerdict -eq 'foreign-stale') -Detail "verdict was '$recycledVerdict', expected foreign-stale"

    $transcriptRoot = Join-Path $repoRoot 'temp/S2408/lock-tests'
    $fakeSession = 'sess-s2408-fixture'
    $mainTranscript = Join-Path $transcriptRoot "$fakeSession.jsonl"
    $subagentDir = Join-Path (Join-Path $transcriptRoot $fakeSession) 'subagents'
    try {
        New-Item -ItemType Directory -Path $subagentDir -Force | Out-Null
        Set-Content -LiteralPath $mainTranscript -Value '{}' -Encoding utf8NoBOM
        $subagentFile = Join-Path $subagentDir 'agent-fixture.jsonl'
        Set-Content -LiteralPath $subagentFile -Value '{}' -Encoding utf8NoBOM
        # The shape of the incident: the session's own file went quiet an hour ago while its
        # subagent kept writing.
        (Get-Item -LiteralPath $mainTranscript).LastWriteTime = (Get-Date).AddHours(-1)
        $subagentWrite = (Get-Item -LiteralPath $subagentFile).LastWriteTime

        $newest = Get-AgentSessionTranscriptLastWrite -TranscriptPath $mainTranscript
        Assert-Case -Name 'S2408: transcript freshness counts the subagent subtree' `
            -Ok ($null -ne $newest -and $newest -eq $subagentWrite) `
            -Detail "helper returned '$newest', expected the subagent file's $subagentWrite"

        $missing = Get-AgentSessionTranscriptLastWrite -TranscriptPath (Join-Path $transcriptRoot 'absent.jsonl')
        Assert-Case -Name 'S2408: a transcript with neither file nor subtree returns null' `
            -Ok ($null -eq $missing) -Detail "helper returned '$missing', expected null"
    }
    finally {
        Remove-Item -LiteralPath $transcriptRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    # The waiter's self-acquire eligibility. Driven through the helper rather than through
    # wait-for-lock-turn.ps1 itself: the script enqueues in a LIVE queue, and a test that took a
    # place in Code.Scripts would compete with whatever session is actually working.
    $eligible = Test-WaiterAcquireEligible -Domains @('Code.Scripts') -SessionId 'host-code-1234-5678'
    Assert-Case -Name 'waiter self-acquire: a code domain under a stable identity is eligible' `
        -Ok $eligible.Eligible -Detail $eligible.Reason

    $buildVerdict = Test-WaiterAcquireEligible -Domains @('Code.Phone', 'Build.Phone') -SessionId 'host-code-1234-5678'
    Assert-Case -Name 'waiter self-acquire: a build domain anywhere in the set is refused' `
        -Ok (-not $buildVerdict.Eligible) -Detail 'a PID-judged lock would read as dead the moment the waiter exits'

    $pidVerdict = Test-WaiterAcquireEligible -Domains @('Code.Scripts') -SessionId 'pid-4242'
    Assert-Case -Name 'waiter self-acquire: a pid- identity is refused' `
        -Ok (-not $pidVerdict.Eligible) -Detail 'the lock would be foreign to the session that asked for the wait'

    # S2413 - the stalled-holder predicate. Every case supplies the holder and the queue rather
    # than writing a lock file: the live domains belong to whichever sessions are actually working,
    # and a test that took one would block them to prove a read-only signal. Three of the four are
    # negative on purpose - a signal that lights on a healthy tree is switched off within a day,
    # and then the incident it exists for passes unremarked.
    $stallRoot = Join-Path $repoRoot 'temp/S2413/stall-predicate'
    New-Item -ItemType Directory -Path $stallRoot -Force | Out-Null
    try {
        $quietOwner = 'stall-quiet-owner-session'
        $quietTranscript = Join-Path $stallRoot 'quiet.jsonl'
        Set-Content -LiteralPath $quietTranscript -Value '{}' -Encoding utf8
        # Well past Code.Scripts' LockStaleMinutes of 10, and past its SessionStaleMinutes of 15 too,
        # so the case cannot pass by accident on a machine whose clock granularity differs.
        (Get-Item -LiteralPath $quietTranscript).LastWriteTime = (Get-Date).AddMinutes(-40)

        $freshOwner = 'stall-fresh-owner-session'
        $freshTranscript = Join-Path $stallRoot 'fresh.jsonl'
        Set-Content -LiteralPath $freshTranscript -Value '{}' -Encoding utf8

        $waiter = @([pscustomobject]@{ seq = 7; sessionId = 'stall-waiter-session'; waitedMinutes = 6 })

        $stalled = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 -Queue $waiter
        Assert-Case -Name 'S2413: held, a queue behind it and a quiet owner is a stall' `
            -Ok ($null -ne $stalled -and $stalled.domain -eq 'Code.Scripts' -and
                 $stalled.queueDepth -eq 1 -and $stalled.quietMinutes -gt $stalled.thresholdMinutes) `
            -Detail "predicate returned '$stalled'"

        Assert-Case -Name 'S2413: the threshold is the domain LockStaleMinutes, not a literal' `
            -Ok ($null -ne $stalled -and $stalled.thresholdMinutes -eq (Get-AgentLockTimings -Name 'Code.Scripts').LockStaleMinutes) `
            -Detail "threshold was '$($stalled.thresholdMinutes)'"

        $fresh = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $freshOwner `
            -HolderTranscriptPath $freshTranscript -HeldMinutes 12 -Queue $waiter
        Assert-Case -Name 'S2413: a fresh owner holding with a queue is not a stall' `
            -Ok ($null -eq $fresh) -Detail "predicate returned '$fresh' for an owner seen seconds ago"

        $noQueue = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 -Queue @()
        Assert-Case -Name 'S2413: a quiet holder with an empty queue blocks nobody and is not a stall' `
            -Ok ($null -eq $noQueue) -Detail "predicate returned '$noQueue' with nobody waiting"

        # S2413's build case - "a build domain is never a stall" - is deliberately absent here: it
        # asserted the exclusion S2582 removed, so keeping it would pin the defect. Its replacement
        # is case 11 below, which needs the fix present and is skipped without it. It cannot simply
        # be re-aimed in place either: under the new predicate a build call with no -HolderPid falls
        # back to reading whichever live Build.Phone lock a sibling session happens to hold, which
        # would make this suite's verdict depend on another session's build.
        $selfQueued = Get-AgentLockStall -Name 'Code.Scripts' -HolderSessionId $quietOwner `
            -HolderTranscriptPath $quietTranscript -HeldMinutes 12 `
            -Queue @([pscustomobject]@{ seq = 3; sessionId = $quietOwner; waitedMinutes = 9 })
        Assert-Case -Name 'S2413: the holder own leftover ticket is not a second session waiting' `
            -Ok ($null -eq $selfQueued) -Detail "predicate counted the holder as its own waiter"

        Assert-Case -Name 'S2413: quiet time for a session with no readable mark is null, not zero' `
            -Ok ($null -eq (Get-AgentOwnerQuietMinutes -SessionId 'stall-no-marks-anywhere-session')) `
            -Detail 'an unmeasurable owner would otherwise read as seen just now'
    }
    finally {
        Remove-Item -LiteralPath $stallRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    # 10. S2577 - a BUILD ticket whose own process is gone while its session keeps writing. Four of
    #     the six cases are protective: this sweep has already evicted a live waiter once (S2421,
    #     292 s of correct polling lost), so each condition that narrows the new eviction gets a
    #     case proving it still keeps somebody's place.
    $s2577Skipped = 0
    if (-not (Get-Command Test-AgentTicketProcessAlive -ErrorAction SilentlyContinue)) {
        # The resolved harness is the deployed plugin cache and only the owner can deploy it, so a
        # session running this suite between the canon edit and the deploy must not go red over
        # work no session running it can perform.
        Write-Output "  SKIP S2577 dead-pid build ticket (6 cases) - the resolved harness predates the fix: $agentLockSourcePath"
        $s2577Skipped = 6
    }
    else {
        $selfStart = (Get-Process -Id $PID).StartTime
        $selfStartTicks = $selfStart.Ticks
        $selfEnqueuedAt = [DateTimeOffset]::new($selfStart).ToUnixTimeMilliseconds()

        Assert-Case -Name 'S2577: a running process with matching start ticks reads alive' `
            -Ok (Test-AgentTicketProcessAlive -Ticket ([pscustomobject]@{
                    pid = $PID; procStart = $selfStartTicks; enqueuedAt = $selfEnqueuedAt })) `
            -Detail 'a live owner judged gone would be evicted in the middle of its own wait'

        # Real pid reuse cannot be forced from a test, so it is fabricated the way case 6 does it:
        # this process's own live pid paired with start ticks it cannot have.
        Assert-Case -Name 'S2577: a live pid with different start ticks is a recycled pid, not a survivor' `
            -Ok (-not (Test-AgentTicketProcessAlive -Ticket ([pscustomobject]@{
                    pid = $PID; procStart = 1; enqueuedAt = $selfEnqueuedAt }))) `
            -Detail 'pid reuse would keep a dead owner alive for as long as the number stays taken'

        Assert-Case -Name 'S2577: a ticket carrying no pid reads alive - every doubt keeps the place' `
            -Ok (Test-AgentTicketProcessAlive -Ticket ([pscustomobject]@{
                    sessionId = 's2577-no-pid'; enqueuedAt = $selfEnqueuedAt })) `
            -Detail 'an unanswerable ticket was deleted instead of kept'

        # A pre-S2577 ticket carries no procStart, so its enqueue time is the only pid-reuse guard
        # left: a process that started nine hours after the ticket was written did not write it.
        Assert-Case -Name 'S2577: a legacy ticket with no procStart is judged by its enqueue time' `
            -Ok (-not (Test-AgentTicketProcessAlive -Ticket ([pscustomobject]@{
                    pid = $PID; enqueuedAt = ([DateTimeOffset]::UtcNow.AddHours(-9).ToUnixTimeMilliseconds()) }))) `
            -Detail 'a legacy ticket older than the process it names was judged alive'

        # The sweep itself runs in a child process against a THROWAWAY project root, never in this
        # one: SZA_PROJECT_ROOT is cached per process, and the forwarder overwrites it with the
        # repository root, so the only working sandbox is the harness file dot-sourced directly
        # (S2426, S2520 - getting this wrong swept the live queue and stranded nine tickets in the
        # real temp/CODE.SCRIPTS.QUEUE).
        $s2577Sandbox = Join-Path $repoRoot 'temp/S2577/agent-lock-tests-sandbox'
        $s2577Probe = Join-Path $s2577Sandbox 'probe.ps1'
        $s2577Out = Join-Path $repoRoot 'temp/S2577-sweep-stdout.log'
        $s2577Err = Join-Path $repoRoot 'temp/S2577-sweep-stderr.log'
        try {
            Remove-Item -LiteralPath $s2577Sandbox -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path $s2577Sandbox -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination $s2577Sandbox -Force
            Set-Content -LiteralPath $s2577Probe -Value $s2577ProbeBody -Encoding utf8NoBOM

            $proc = Start-Process -FilePath 'pwsh' `
                -ArgumentList @('-NoProfile', '-File', $s2577Probe, '-HarnessPath', $agentLockSourcePath, '-Sandbox', $s2577Sandbox) `
                -NoNewWindow -Wait -PassThru -RedirectStandardOutput $s2577Out -RedirectStandardError $s2577Err
            $verdict = $null
            if ($proc.ExitCode -eq 0) {
                try { $verdict = (Get-Content -LiteralPath $s2577Out -Raw).Trim() | ConvertFrom-Json }
                catch { $verdict = $null }
            }

            Assert-Case -Name 'S2577: a build ticket whose process is gone is swept' `
                -Ok ($null -ne $verdict -and -not $verdict.buildDeadPid) `
                -Detail "probe exited $($proc.ExitCode), verdict '$($verdict | ConvertTo-Json -Compress)' - the ticket that stalls the queue survived"

            Assert-Case -Name 'S2577: a live pid, a fresh heartbeat, a granted turn and any code ticket all survive' `
                -Ok ($null -ne $verdict -and $verdict.buildLivePid -and $verdict.buildDeadHeartbeat -and
                     $verdict.buildDeadGranted -and $verdict.codeDeadPid) `
                -Detail "verdict '$($verdict | ConvertTo-Json -Compress)' - the new reason reached a ticket it must never touch"
        }
        finally {
            Remove-Item -LiteralPath $s2577Sandbox -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $s2577Out -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $s2577Err -Force -ErrorAction SilentlyContinue
        }
    }

    # 11. S2582 - the build half of the stalled-holder signal. Eight cases and five of them
    #     negative, because the naive form of this signal has a measured price: a reaper judging the
    #     holder's tree alone killed 24 live builds in 105 minutes, always exactly at its threshold,
    #     while one build in that window reached success. Every condition that narrows the verdict
    #     therefore gets a case proving it still stays quiet.
    $s2582Skipped = 0
    if (-not (Get-Command Measure-AgentBuildActivity -ErrorAction SilentlyContinue)) {
        # Same reason as case 10: only the owner can deploy the plugin cache, so a session running
        # this suite between the canon edit and the deploy must not go red over work it cannot do.
        Write-Output "  SKIP S2582 build stall rule (8 cases) - the resolved harness predates the fix: $agentLockSourcePath"
        $s2582Skipped = 8
    }
    else {
        $codeTimings = Get-AgentLockTimings -Name 'Code.Scripts'
        Assert-Case -Name 'S2582: the code threshold is StallMinutes and still equals LockStaleMinutes' `
            -Ok ($codeTimings.StallMinutes -eq 10 -and $codeTimings.StallMinutes -eq $codeTimings.LockStaleMinutes) `
            -Detail "StallMinutes $($codeTimings.StallMinutes) vs LockStaleMinutes $($codeTimings.LockStaleMinutes) - the code rule changed verdict"

        Assert-Case -Name 'S2582: the build threshold is its own field, not the hour-long stale limit' `
            -Ok ((Get-AgentLockTimings -Name 'Build.Phone').StallMinutes -eq 25) `
            -Detail "got $((Get-AgentLockTimings -Name 'Build.Phone').StallMinutes), expected the reaper's 25"

        # The six build cases run in a child process against a THROWAWAY project root, for case 10's
        # two reasons plus one of their own: the engine vocabulary is read from the profile and
        # cached per process, and re-pointing it at a spinner - the only way to exercise the busy
        # half without borrowing somebody's real build - cannot be undone inside this process.
        $s2582Sandbox = Join-Path $repoRoot 'temp/S2582/agent-lock-tests-sandbox'
        $s2582Probe = Join-Path $s2582Sandbox 'probe.ps1'
        $s2582Out = Join-Path $repoRoot 'temp/S2582-stall-stdout.log'
        $s2582Err = Join-Path $repoRoot 'temp/S2582-stall-stderr.log'
        try {
            Remove-Item -LiteralPath $s2582Sandbox -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path $s2582Sandbox -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination $s2582Sandbox -Force
            Set-Content -LiteralPath $s2582Probe -Value $s2582ProbeBody -Encoding utf8NoBOM

            $proc = Start-Process -FilePath 'pwsh' `
                -ArgumentList @('-NoProfile', '-File', $s2582Probe, '-HarnessPath', $agentLockSourcePath, '-Sandbox', $s2582Sandbox) `
                -NoNewWindow -Wait -PassThru -RedirectStandardOutput $s2582Out -RedirectStandardError $s2582Err
            $v = $null
            if ($proc.ExitCode -eq 0) {
                try { $v = (Get-Content -LiteralPath $s2582Out -Raw).Trim() | ConvertFrom-Json }
                catch { $v = $null }
            }
            $probeNote = "probe exited $($proc.ExitCode), verdict '$($v | ConvertTo-Json -Compress)'"

            Assert-Case -Name 'S2582: a build holder past its threshold with an idle tree and an idle engine is a stall' `
                -Ok ($null -ne $v -and $v.idleIsStall -and $v.verdictNamesHolder -and
                     $v.verdictReportsLiveProcess -and $v.thresholdIsBuildField) `
                -Detail "$probeNote - the 51-minute hang this ticket exists for would still print nothing"

            Assert-Case -Name 'S2582: a working build engine cancels the verdict' `
                -Ok ($null -ne $v -and $v.busyEngineCancels -and $v.busyEngineWasMeasured) `
                -Detail "$probeNote - the engine detaches, so without this half the signal fires on every healthy build"

            Assert-Case -Name 'S2582: a holder younger than the threshold is not a stall, and pays for no sample' `
                -Ok ($null -ne $v -and $v.youngIsNotStall -and $v.youngPaidNoSample) `
                -Detail "$probeNote - the free pre-filter must run before the Start-Sleep, not after it"

            Assert-Case -Name 'S2582: a build holder with an empty queue blocks nobody and is not a stall' `
                -Ok ($null -ne $v -and $v.emptyQueueIsNotStall) -Detail $probeNote

            Assert-Case -Name 'S2582: a project declaring no build engine gets no build verdict at all' `
                -Ok ($null -ne $v -and $v.noEngineNoVerdict) `
                -Detail "$probeNote - fail-closed: half the predicate is unmeasurable without the vocabulary"

            Assert-Case -Name 'S2582: a dead build holder is not a stall - it is already stale and reclaimable' `
                -Ok ($null -ne $v -and $v.deadHolderIsNotStall) -Detail $probeNote
        }
        finally {
            Remove-Item -LiteralPath $s2582Sandbox -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $s2582Out -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $s2582Err -Force -ErrorAction SilentlyContinue
        }
    }

    # 12. S2697 - a profile-declared domain works end to end, and the handoff carries the changed set.
    #     Every entry point is driven as a real process in a throwaway project root whose profile
    #     adds Code.Fixture (type Code, rank 6) with the rule ^temp/lock-fixture/ ahead of the rest.
    $s2697Skipped = 0
    if (-not (Get-Command ConvertTo-AgentLockHandoffPaths -ErrorAction SilentlyContinue)) {
        Write-Output "  SKIP S2697 profile-declared domain (9 cases) - the resolved harness predates the fix: $agentLockSourcePath"
        $s2697Skipped = 9
    }
    else {
        $s2697Sandbox = Join-Path $repoRoot 'temp/S2697/agent-lock-tests-sandbox'
        $s2697Probe = Join-Path $s2697Sandbox 'probe.ps1'
        $s2697Out = Join-Path $repoRoot 'temp/S2697-domain-stdout.log'
        $s2697Err = Join-Path $repoRoot 'temp/S2697-domain-stderr.log'
        $s2697Passed = $false
        try {
            Remove-Item -LiteralPath $s2697Sandbox -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path $s2697Sandbox -Force | Out-Null
            $fixtureProfile = Get-Content -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Raw | ConvertFrom-Json
            $fixtureProfile.locks.domains = @($fixtureProfile.locks.domains) + [pscustomobject]@{ name = 'Code.Fixture'; type = 'Code'; rank = 6 }
            $fixtureProfile.locks.pathRules = @([pscustomobject]@{ pattern = '^temp/lock-fixture/'; domain = 'Code.Fixture' }) + @($fixtureProfile.locks.pathRules)
            $fixtureProfile | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $s2697Sandbox '.sza-profile.json') -Encoding utf8NoBOM
            Set-Content -LiteralPath $s2697Probe -Value $s2697ProbeBody -Encoding utf8NoBOM

            $proc = Start-Process -FilePath 'pwsh' `
                -ArgumentList @('-NoProfile', '-File', $s2697Probe, '-LocksDir', (Split-Path -Parent $agentLockSourcePath), '-Sandbox', $s2697Sandbox) `
                -NoNewWindow -Wait -PassThru -RedirectStandardOutput $s2697Out -RedirectStandardError $s2697Err
            $v = $null
            if ($proc.ExitCode -eq 0) {
                try { $v = (Get-Content -LiteralPath $s2697Out -Raw).Trim() | ConvertFrom-Json }
                catch { $v = $null }
            }
            $probeNote = "probe exited $($proc.ExitCode), verdict '$($v | ConvertTo-Json -Compress)'"

            Assert-Case -Name 'S2697: a bare Code expands to include Code.Fixture in rank order' `
                -Ok ($null -ne $v -and $v.bareCode -eq 'Code.Phone,Code.Wear,Code.Scripts,Code.Fixture') -Detail $probeNote

            Assert-Case -Name 'S2697: an undeclared-in-the-map domain inherits its type timings; existing ones are unchanged' `
                -Ok ($null -ne $v -and $v.inheritsCodeTimings -and $v.existingTimingsKept) -Detail $probeNote

            Assert-Case -Name 'S2697: an unknown name still throws, quoting the profile table (and the lease names for timings)' `
                -Ok ($null -ne $v -and $v.unknownTimingsThrowsProfileList -and $v.unknownResolveThrowsProfileList -and
                     $v.unknownStatusExit -eq 2 -and $v.unknownExitCodeLockExit -eq 2) -Detail $probeNote

            Assert-Case -Name 'S2697: enter-code-lock on a fixture path acquires Code.Fixture only' `
                -Ok ($null -ne $v -and $v.enterExit -eq 0 -and $v.enterTookFixtureOnly) -Detail $probeNote

            Assert-Case -Name 'S2697: lock-status -Name Code.Fixture -Queue recognises the held domain' `
                -Ok ($null -ne $v -and $v.statusExit -eq 0 -and $v.statusSaysHeld) -Detail $probeNote

            Assert-Case -Name 'S2697: an exit-4 handoff is schema 2 and carries the normalised, deduplicated, sorted paths' `
                -Ok ($null -ne $v -and $v.contendedExit -eq 4 -and $v.handoffSchema -eq 2 -and
                     $v.handoffPaths -eq 'temp/lock-fixture/b.txt|temp/lock-fixture/probe.txt' -and $v.emptyPathsIsArray) -Detail $probeNote

            Assert-Case -Name 'S2697: exit-code-lock -Name Code.Fixture releases it' `
                -Ok ($null -ne $v -and $v.releaseExit -eq 0 -and $v.released) -Detail $probeNote

            Assert-Case -Name 'S2697: wait-for-lock-turn adopts a schema-2 handoff and a pre-existing schema-1 file without paths' `
                -Ok ($null -ne $v -and $v.waitSchema2Exit -eq 0 -and $v.waitSchema2Adopted -and
                     $v.waitSchema1Exit -eq 0 -and $v.waitSchema1Adopted -and $v.noSecondTicket) -Detail $probeNote

            Assert-Case -Name 'S2697: withdraw accepts the domain, and Code.Scripts is never touched' `
                -Ok ($null -ne $v -and $v.withdrawExit -eq 0 -and $v.withdrawn -and $v.contendedScriptsUntouched -and $v.scriptsNeverTouched) -Detail $probeNote
            $s2697Passed = ($null -ne $v)
        }
        finally {
            # Kept on failure: the per-step logs under logs/ are the only record of which entry point refused.
            if ($s2697Passed -and $failures -eq 0) {
                Remove-Item -LiteralPath $s2697Sandbox -Recurse -Force -ErrorAction SilentlyContinue
                Remove-Item -LiteralPath $s2697Out -Force -ErrorAction SilentlyContinue
                Remove-Item -LiteralPath $s2697Err -Force -ErrorAction SilentlyContinue
            }
        }
    }

    if ($failures -gt 0) {
        Write-Output "agent-lock tests: FAIL ($failures case(s))"
        exit 1
    }
    $totalSkipped = $s2577Skipped + $s2582Skipped + $s2697Skipped
    $skipNote = if ($totalSkipped -gt 0) { ", $totalSkipped skipped" } else { '' }
    Write-Output "agent-lock tests: PASS (JAVA_HOME snapshot repair, both directions$skipNote)"
    exit 0
}
finally {
    $env:JAVA_HOME = $originalJavaHome
}
