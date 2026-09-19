#requires -Version 7.0
<#
.SYNOPSIS
    S2615 - contract tests for scripts/utils/code-lock-scope.ps1.

.DESCRIPTION
    Every case here guards a property whose failure is SILENT. A leaked domain looks exactly like a
    working lock until a sibling queues behind it forever; a nested release that hands away its
    caller's lock leaves the caller writing unprotected while believing it is protected; and a path
    form that resolves too WIDE serialises two modules behind a documentation render while every
    message still says "acquired". None of the three shows up in the output of the generator being
    fixed, so only a test can see them.

    The suite runs against the REAL lock store, deliberately. A sandbox was tried first and cannot
    work: Get-SzaProjectRoot caches the root once per process, and the forwarder this helper
    dot-sources assigns SZA_PROJECT_ROOT to the repository root BEFORE the profile that reads it is
    loaded - so a pre-set sandbox root is overwritten before it is ever consulted. Every acquiring
    case therefore releases in a `finally`, and the two cases below that must observe a FOREIGN
    holder do it by giving a child process a different FMS_AGENT_ID - the first link of the identity
    chain - rather than by forging a lock file, so nothing here writes state a real session could
    mistake for its own.

    S2697: every acquiring case targets temp/lock-fixture/, which the profile maps to the dedicated
    Code.Fixture domain. Until then the suite held the production Code.Scripts - measured 14 of 63
    Code.Scripts queue handoffs in three days were this suite, queuing real script work behind a test.
    Code.Scripts stays covered by the read-only resolver cases and by one non-interference assertion.

    The exit-4 case runs in a nested pwsh for a second reason as well: Enter-CodeLockOrExit calls
    `exit` directly, so hitting a busy domain in-process would terminate this runner instead of
    producing an observable code.

.NOTES
    Exit codes:
      0 - every runnable case passed. When Code.Fixture is held (by this session or a sibling), the
          acquiring cases are skipped and the resolution and non-acquiring cases are verified; the
          suite exits 0 with a note about the skipped cases.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$helper = Join-Path $repoRoot 'scripts/utils/code-lock-scope.ps1'
$failures = 0
$caseNo = 0

function Assert-Case {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Condition, [string]$Detail = '')
    $script:caseNo++
    if ($Condition) { Write-Host ("  PASS  {0}. {1}" -f $script:caseNo, $Name) -ForegroundColor Green }
    else {
        Write-Host ("  FAIL  {0}. {1}" -f $script:caseNo, $Name) -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor Red }
        $script:failures++
    }
}

. $helper

$docTarget = Join-Path $repoRoot 'docs/ICON_LEGEND.md'
$fixtureRelative = 'temp/lock-fixture/code-lock-scope.txt'
$fixtureTarget = Join-Path $repoRoot $fixtureRelative
$fixtureLock = Get-AgentLockPath -Name 'Code.Fixture'
$allCodeDomains = 'Code.Phone,Code.Wear,Code.Scripts,Code.Fixture'
$testIdentities = @('code-lock-scope-tests-foreign', 'code-lock-scope-tests-skipper')

# Every OTHER code domain this session holds after an acquiring case would be a leak the fixture
# retarget exists to prevent; a sibling's lock on them is not ours and is ignored.
#
# S3317: "held by this identity" is NOT that leak on its own. The suite runs as a child process of
# whatever invoked it and Get-AgentIdentityResolution answers from the inherited
# CLAUDE_CODE_SESSION_ID, so a domain the CALLER took for its own edits before the suite started is
# stamped with the same id as one the suite took - and a release-scope batch is exactly the context
# where the caller holds Code.Scripts. Measured 2026-09-19: four cases red inside
# run-script-suites -Gate while the subject was provably intact, diagnosed at the price of a
# 1172 s suite run plus an isolated re-run. The baseline below is the difference: a hold that
# predates the run and never moved is the caller's, a hold that appeared - or was released and
# retaken - inside the run is the leak, and the acquisition stamp separates them.
$productionCodeDomains = @('Code.Phone', 'Code.Wear', 'Code.Scripts')

function Get-OwnProductionCodeHolds {
    $self = Get-AgentSessionId
    $holds = @{}
    foreach ($domain in $script:productionCodeDomains) {
        $status = Get-AgentLockStatus -Name $domain
        if ($status.Exists -and [string]$status.SessionId -eq $self) {
            $holds[$domain] = '{0}|{1}' -f $status.AcquiredAtIso, $status.Reason
        }
    }
    return $holds
}

$preHeldOwnCodeLocks = Get-OwnProductionCodeHolds

function Get-OwnProductionCodeLeaks {
    $now = Get-OwnProductionCodeHolds
    $leaks = [System.Collections.Generic.List[string]]::new()
    foreach ($domain in $script:productionCodeDomains) {
        if (-not $now.ContainsKey($domain)) { continue }
        if ((-not $script:preHeldOwnCodeLocks.ContainsKey($domain)) -or
            ($script:preHeldOwnCodeLocks[$domain] -ne $now[$domain])) { $leaks.Add($domain) }
    }
    return $leaks.ToArray()
}

function Test-NoOtherOwnCodeLock {
    return ((@(Get-OwnProductionCodeLeaks)).Count -eq 0)
}

Write-Host 'code-lock-scope contract suite' -ForegroundColor Cyan

# --- Guard: skip acquiring cases when the fixture domain is held --------------
# The acquiring cases touch Code.Fixture only, so a sibling working in scripts/ no longer turns them
# off. When the fixture domain is held - by this session or a parallel run of this suite - run only
# the resolution and non-acquiring cases and exit 0, so the release-scope gate gets a trustworthy
# verdict instead of a false red or a cannot-verify.
$skipAcquiring = $false
$fixtureStatus = Get-AgentLockStatus -Name 'Code.Fixture'
if ($fixtureStatus.Exists -and -not $fixtureStatus.Stale) {
    $skipAcquiring = $true
    Write-Host "  SKIP  acquiring cases - Code.Fixture held: $($fixtureStatus.SessionId)" -ForegroundColor Yellow
}

# --- Cases 1-5: path normalisation, no locks taken ----------------------------
# The whole adoption rests on this: every caller in the family builds its targets with Join-Path
# against a repo root, and an absolute path reaches the resolver as "unrecognised" -> every domain.
function Resolve-ForTest {
    param([string[]]$Path)
    return (@(Resolve-CodeDomainsForPaths -Path (ConvertTo-CodeLockRelativePath -Path $Path)) -join ',')
}

Assert-Case 'an absolute docs/ path narrows to Code.Scripts alone' `
    ((Resolve-ForTest -Path @($docTarget)) -eq 'Code.Scripts') `
    "resolved: $(Resolve-ForTest -Path @($docTarget))"

Assert-Case 'a repo-relative docs/ path gives the same answer' `
    ((Resolve-ForTest -Path @('docs/ICON_LEGEND.md')) -eq 'Code.Scripts')

Assert-Case 'an absolute app_v2 + docs pair gives exactly those two domains' `
    ((Resolve-ForTest -Path @(
        (Join-Path $repoRoot 'app_v2/src/main/res/raw/oss_notices_standard.json'),
        (Join-Path $repoRoot 'docs/OPEN_SOURCE.md'))) -eq 'Code.Phone,Code.Scripts')

Assert-Case 'a rooted path outside the project still fails closed to every code domain' `
    ((Resolve-ForTest -Path @('C:/somewhere/else/file.md')) -eq $allCodeDomains) `
    "resolved: $(Resolve-ForTest -Path @('C:/somewhere/else/file.md'))"

# The near miss that actually happened, and the reason Enter-CodeLockOrExit announces this answer
# instead of just returning it: every rule is an anchored PREFIX, so the CONTAINING directory of a
# target matches nothing and widens to every code domain. Safe, wrong, and otherwise silent - two
# docs renderers passed their $OutDir here and queued behind a sibling's Code.Phone (2026-09-06).
# Asserted on the resolver rather than on a real acquisition: acquiring all of them to read one
# advisory line would take the whole repository's code domains, and exit 4 on a busy one would take
# this runner down with it.
Assert-Case 'the containing directory alone still fails closed (the near miss)' `
    ((Resolve-ForTest -Path @((Join-Path $repoRoot 'docs'))) -eq $allCodeDomains)

# S2697: the fixture rule must win over the temp/ exemption below it, or the acquiring cases would
# silently test nothing.
Assert-Case 'an absolute temp/lock-fixture/ path narrows to Code.Fixture alone' `
    ((Resolve-ForTest -Path @($fixtureTarget)) -eq 'Code.Fixture') `
    "resolved: $(Resolve-ForTest -Path @($fixtureTarget))"

# --- Cases 6-7: a free domain is acquired and fully released ------------------
if (-not $skipAcquiring) {
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path @($fixtureTarget) -Reason 'code-lock-scope.tests case 6'
        Assert-Case 'a fixture target acquires Code.Fixture and nothing else' `
            ((@($scope.Acquired) -join ',') -eq 'Code.Fixture' -and (Test-Path -LiteralPath $fixtureLock) -and
             (Test-NoOtherOwnCodeLock)) `
            "acquired: $(@($scope.Acquired) -join ',')"
    }
    finally { if ($scope) { Exit-CodeLockScope -Scope $scope } }
    Assert-Case 'the release removes the lock file' (-not (Test-Path -LiteralPath $fixtureLock))
}

# --- Cases 8-10: re-entry acquires nothing and releases nothing ---------------
if (-not $skipAcquiring) {
    $outer = $null
    try {
        $outer = Enter-CodeLockOrExit -Path @($fixtureTarget) -Reason 'code-lock-scope.tests case 8 outer'
        $inner = Enter-CodeLockOrExit -Path @($fixtureTarget) -Reason 'code-lock-scope.tests case 8 inner'
        Assert-Case 'a re-entrant call acquires nothing' (@($inner.Acquired).Count -eq 0)
        Exit-CodeLockScope -Scope $inner
        Assert-Case 'the inner release leaves the outer lock in place' (Test-Path -LiteralPath $fixtureLock)
    }
    finally { if ($outer) { Exit-CodeLockScope -Scope $outer } }
    Assert-Case 'the outer release then frees the domain' (-not (Test-Path -LiteralPath $fixtureLock))
}

# --- Case 11: an exception between enter and exit still releases --------------
if (-not $skipAcquiring) {
    $threw = $false
    $scope4 = $null
    try {
        $scope4 = Enter-CodeLockOrExit -Path @($fixtureTarget) -Reason 'code-lock-scope.tests case 11'
        throw 'deliberate failure inside the locked window'
    }
    catch { $threw = $true }
    finally { if ($scope4) { Exit-CodeLockScope -Scope $scope4 } }
    Assert-Case 'a throw inside the window still releases through the caller finally' `
        ($threw -and -not (Test-Path -LiteralPath $fixtureLock))
}

# --- Case 12: a PLAN/ only path set takes no domain --------------------------
$planScope = $null
try {
    $planScope = Enter-CodeLockOrExit -Path @((Join-Path $repoRoot 'PLAN/S2615_bugfix-icon-inventory-regenerator-skips-code-lock.md')) `
        -Reason 'code-lock-scope.tests case 12'
    Assert-Case 'a PLAN/ only set resolves to no domain and acquires nothing' `
        ((@($planScope.Acquired).Count -eq 0) -and (Test-NoOtherOwnCodeLock) -and
         ($skipAcquiring -or -not (Test-Path -LiteralPath $fixtureLock)))
}
finally { if ($planScope) { Exit-CodeLockScope -Scope $planScope } }

# --- Case 13-14: a temp/ path takes no domain either (S2710) ------------------
# The second exemption, and it arrived as a red contract suite rather than as a design: every path
# rule is an anchored prefix and none of them named temp/, so a throwaway file under temp/scratch
# hit the fail-closed branch and acquired ALL THREE code domains to write a file the run deletes.
# Measured 2026-09-07 - assert-always-loaded-budget's own suite failed C8 with exit 4 against a
# free Code.Scripts whose QUEUE a foreign session held. PLAN/ is exempt because the lease and the
# catalog mutex already serialise it; temp/ is exempt because there is nothing there to serialise.
Assert-Case 'a temp/ path resolves to no domain at all' `
    ((Resolve-ForTest -Path @((Join-Path $repoRoot 'temp/scratch/s2710-sandbox/baseline.txt'))) -eq '') `
    "resolved: $(Resolve-ForTest -Path @((Join-Path $repoRoot 'temp/scratch/s2710-sandbox/baseline.txt')))"

$tempScope = $null
try {
    $tempScope = Enter-CodeLockOrExit -Path @((Join-Path $repoRoot 'temp/scratch/s2710-sandbox/baseline.txt')) `
        -Reason 'code-lock-scope.tests case 14'
    Assert-Case 'a temp/ only set acquires nothing and creates no lock file' `
        ((@($tempScope.Acquired).Count -eq 0) -and (Test-NoOtherOwnCodeLock) -and
         ($skipAcquiring -or -not (Test-Path -LiteralPath $fixtureLock)))
}
finally { if ($tempScope) { Exit-CodeLockScope -Scope $tempScope } }

# --- Cases 15-16: a foreign holder yields exit 4 and writes nothing -----------
if (-not $skipAcquiring) {
    $probe = Join-Path $repoRoot 'temp/S2615/probe-exit-4.ps1'
    New-Item -ItemType Directory -Path (Split-Path -Parent $probe) -Force | Out-Null
    @"
Set-StrictMode -Version Latest
`$ErrorActionPreference = 'Stop'
. '$helper'
`$null = Enter-CodeLockOrExit -Path @('$fixtureRelative') -Reason 'code-lock-scope.tests case 15 (foreign)'
exit 0
"@ | Set-Content -LiteralPath $probe -Encoding utf8

    $held = $null
    $probeExit = -1
    try {
        $held = Enter-CodeLockOrExit -Path @($fixtureTarget) -Reason 'code-lock-scope.tests case 15 holder'
        $before = (Get-Item -LiteralPath $fixtureLock).LastWriteTimeUtc
        # A different first link of the identity chain makes the child a stranger to this lock, without
        # forging any state: it queues and refuses exactly as a real sibling session would.
        $priorAgentId = $env:FMS_AGENT_ID
        $env:FMS_AGENT_ID = 'code-lock-scope-tests-foreign'
        try { & pwsh -NoProfile -File $probe *> (Join-Path $repoRoot 'temp/S2615/probe-exit-4.out'); $probeExit = $LASTEXITCODE }
        finally { $env:FMS_AGENT_ID = $priorAgentId }
        $after = (Get-Item -LiteralPath $fixtureLock).LastWriteTimeUtc
        Assert-Case 'a foreign holder makes the helper exit 4' ($probeExit -eq 4) "actual exit $probeExit"
        Assert-Case 'the refused run left the holder lock untouched' ($after -eq $before)
    }
    finally {
        if ($held) { Exit-CodeLockScope -Scope $held }
        # The refusal keeps its place in the queue by design; that place belongs to an identity nothing
        # else will ever reuse, so this suite retires it rather than leaving it on the real queue head.
        Remove-AgentSessionTickets -Name 'Code.Fixture' -SessionId 'code-lock-scope-tests-foreign' | Out-Null
    }
}

# --- Case 17: a null scope releases nothing and raises nothing ----------------
# The shape every adopting script's `finally` sees when the run died before Enter returned. A
# Mandatory -Scope would PROMPT here, turning a cleanup path into an error that buries the real one.
$nullReleaseOk = $true
try { Exit-CodeLockScope -Scope $null } catch { $nullReleaseOk = $false }
Assert-Case 'releasing a null scope is a silent no-op' $nullReleaseOk

# --- Case 18: running the library as a script refuses -------------------------
& pwsh -NoProfile -File $helper *> (Join-Path $repoRoot 'temp/S2615/probe-direct.out')
Assert-Case 'invoking the library as a script exits 2' ($LASTEXITCODE -eq 2) "actual exit $LASTEXITCODE"

# --- Cases 19-21: Enter-CodeLockOrSkip skips instead of exiting (S2635) -------
# Same shape as cases 15-16, one assertion inverted: the busy domain must NOT end the process.
# assert-source-gates.ps1 reaches this from inside the concurrent fg battery with its verdict
# already computed, so an exit there would paint a passing gate red over a bookkeeping write.
if (-not $skipAcquiring) {
    $skipProbe = Join-Path $repoRoot 'temp/S2635/probe-skip-case.ps1'
    New-Item -ItemType Directory -Path (Split-Path -Parent $skipProbe) -Force | Out-Null
    @"
Set-StrictMode -Version Latest
`$ErrorActionPreference = 'Stop'
. '$helper'
`$s = Enter-CodeLockOrSkip -Path @('$fixtureRelative') -Reason 'code-lock-scope.tests skip (foreign)'
Write-Host "SKIPPED=`$(`$s.Skipped) ACQUIRED=`$(@(`$s.Acquired).Count)"
Exit-CodeLockScope -Scope `$s
exit 0
"@ | Set-Content -LiteralPath $skipProbe -Encoding utf8

    $skipHeld = $null
    $skipExit = -1
    $skipOut = ''
    $foreignQueued = -1
    try {
        $skipHeld = Enter-CodeLockOrExit -Path @($fixtureTarget) -Reason 'code-lock-scope.tests skip holder'
        $beforeSkip = (Get-Item -LiteralPath $fixtureLock).LastWriteTimeUtc
        $skipOutFile = Join-Path $repoRoot 'temp/S2635/probe-skip-case.out'
        $priorAgentId = $env:FMS_AGENT_ID
        $env:FMS_AGENT_ID = 'code-lock-scope-tests-skipper'
        try {
            & pwsh -NoProfile -File $skipProbe *> $skipOutFile
            $skipExit = $LASTEXITCODE
        }
        finally { $env:FMS_AGENT_ID = $priorAgentId }
        $skipOut = (Get-Content -LiteralPath $skipOutFile -Raw)
        $afterSkip = (Get-Item -LiteralPath $fixtureLock).LastWriteTimeUtc
        $foreignQueued = @(Get-AgentLockQueue -Name 'Code.Fixture' |
            Where-Object { [string]$_.sessionId -eq 'code-lock-scope-tests-skipper' }).Count

        Assert-Case 'a foreign holder makes OrSkip return instead of exiting' `
            (($skipExit -eq 0) -and ($skipOut -match 'SKIPPED=True')) "actual exit $skipExit, output: $($skipOut.Trim())"
        Assert-Case 'the skipped run left the holder lock untouched' ($afterSkip -eq $beforeSkip)
        # The whole reason OrSkip is ticketless: a caller that never returns for the write must not
        # leave a place nobody retires, which through the head-of-queue reservation would hold the
        # domain against a session that really is waiting.
        Assert-Case 'the skipped run took no place in the queue' ($foreignQueued -eq 0) `
            "actual $foreignQueued ticket(s)"
    }
    finally {
        if ($skipHeld) { Exit-CodeLockScope -Scope $skipHeld }
        Remove-AgentSessionTickets -Name 'Code.Fixture' -SessionId 'code-lock-scope-tests-skipper' | Out-Null
    }
}

# --- Cases 22-23: OrSkip on a free domain acquires and releases normally ------
if (-not $skipAcquiring) {
    $freeSkip = $null
    try {
        $freeSkip = Enter-CodeLockOrSkip -Path @($fixtureTarget) -Reason 'code-lock-scope.tests free skip'
        Assert-Case 'OrSkip on a free domain acquires the domain and does not skip' `
            ((-not $freeSkip.Skipped) -and (@($freeSkip.Acquired) -join ',') -eq 'Code.Fixture' -and
             (Test-Path -LiteralPath $fixtureLock))
    }
    finally { if ($freeSkip) { Exit-CodeLockScope -Scope $freeSkip } }
    Assert-Case 'the OrSkip release frees the domain' (-not (Test-Path -LiteralPath $fixtureLock))
}

# --- Case 24: the suite never touched Code.Scripts (S2697) --------------------
# The non-interference claim the fixture domain exists for: this RUN took no production code lock,
# and neither test identity left a ticket on the Code.Scripts queue. The detail names both halves
# separately, so a future red case says which one it is instead of leaving the caller to re-run the
# suite alone to find out (S3317).
$scriptsLeftovers = @(Get-AgentLockQueue -Name 'Code.Scripts' |
    Where-Object { $testIdentities -contains [string]$_.sessionId }).Count
$ownLeaks = @(Get-OwnProductionCodeLeaks)
Assert-Case 'acquiring cases use Code.Fixture and leave Code.Scripts free of this suite' `
    (($ownLeaks.Count -eq 0) -and ($scriptsLeftovers -eq 0)) `
    ("production domains taken by this run: $(if ($ownLeaks.Count) { $ownLeaks -join ',' } else { 'none' }); " +
     "pre-held by the caller and ignored: $(if ($preHeldOwnCodeLocks.Count) { ($preHeldOwnCodeLocks.Keys | Sort-Object) -join ',' } else { 'none' }); " +
     "test tickets on Code.Scripts: $scriptsLeftovers")

# --- Verdict ------------------------------------------------------------------
Write-Host ''
if ($failures -gt 0) {
    Write-Host "code-lock-scope.tests: FAIL ($failures of $caseNo case(s))" -ForegroundColor Red
    exit 1
}
if ($skipAcquiring) {
    Write-Host "code-lock-scope.tests: PASS ($caseNo cases, acquiring cases skipped - Code.Fixture held)" -ForegroundColor Green
    exit 0
}
Write-Host "code-lock-scope.tests: PASS ($caseNo cases)" -ForegroundColor Green
exit 0
