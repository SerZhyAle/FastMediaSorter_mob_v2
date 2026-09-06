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

    The exit-4 case runs in a nested pwsh for a second reason as well: Enter-CodeLockOrExit calls
    `exit` directly, so hitting a busy domain in-process would terminate this runner instead of
    producing an observable code.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - a case failed.
      2 - could not verify: a code domain was already held by another live session, so the
          acquiring cases could not run.
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
$scriptsLock = Get-AgentLockPath -Name 'Code.Scripts'
$phoneLock = Get-AgentLockPath -Name 'Code.Phone'
$wearLock = Get-AgentLockPath -Name 'Code.Wear'

Write-Host 'code-lock-scope contract suite' -ForegroundColor Cyan

# --- Guard: never run the acquiring cases against a foreign holder -------------
$mySession = Get-AgentSessionId
foreach ($domain in @('Code.Phone', 'Code.Wear', 'Code.Scripts')) {
    $status = Get-AgentLockStatus -Name $domain
    if ($status.Exists -and -not $status.Stale -and [string]$status.SessionId -ne $mySession) {
        Write-Error "code-lock-scope.tests: cannot verify - $domain is held by another live session ($($status.SessionId))." -ErrorAction Continue
        exit 2
    }
}

# --- Cases 1-4: path normalisation, no locks taken ----------------------------
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
    ((Resolve-ForTest -Path @('C:/somewhere/else/file.md')) -eq 'Code.Phone,Code.Wear,Code.Scripts')

# The near miss that actually happened, and the reason Enter-CodeLockOrExit announces this answer
# instead of just returning it: every rule is an anchored PREFIX, so the CONTAINING directory of a
# target matches nothing and widens to every code domain. Safe, wrong, and otherwise silent - two
# docs renderers passed their $OutDir here and queued behind a sibling's Code.Phone (2026-09-06).
# Asserted on the resolver rather than on a real acquisition: acquiring all three to read one
# advisory line would take the whole repository's code domains, and exit 4 on a busy one would take
# this runner down with it.
Assert-Case 'the containing directory alone still fails closed (the near miss)' `
    ((Resolve-ForTest -Path @((Join-Path $repoRoot 'docs'))) -eq 'Code.Phone,Code.Wear,Code.Scripts')

# --- Cases 5-6: a free domain is acquired and fully released ------------------
$scope = $null
try {
    $scope = Enter-CodeLockOrExit -Path @($docTarget) -Reason 'code-lock-scope.tests case 5'
    Assert-Case 'a docs/ target acquires Code.Scripts and nothing else' `
        ((@($scope.Acquired) -join ',') -eq 'Code.Scripts' -and (Test-Path -LiteralPath $scriptsLock) -and
         -not (Test-Path -LiteralPath $phoneLock) -and -not (Test-Path -LiteralPath $wearLock)) `
        "acquired: $(@($scope.Acquired) -join ',')"
}
finally { if ($scope) { Exit-CodeLockScope -Scope $scope } }
Assert-Case 'the release removes the lock file' (-not (Test-Path -LiteralPath $scriptsLock))

# --- Cases 7-9: re-entry acquires nothing and releases nothing ----------------
$outer = $null
try {
    $outer = Enter-CodeLockOrExit -Path @($docTarget) -Reason 'code-lock-scope.tests case 7 outer'
    $inner = Enter-CodeLockOrExit -Path @($docTarget) -Reason 'code-lock-scope.tests case 7 inner'
    Assert-Case 'a re-entrant call acquires nothing' (@($inner.Acquired).Count -eq 0)
    Exit-CodeLockScope -Scope $inner
    Assert-Case 'the inner release leaves the outer lock in place' (Test-Path -LiteralPath $scriptsLock)
}
finally { if ($outer) { Exit-CodeLockScope -Scope $outer } }
Assert-Case 'the outer release then frees the domain' (-not (Test-Path -LiteralPath $scriptsLock))

# --- Case 10: an exception between enter and exit still releases --------------
$threw = $false
$scope4 = $null
try {
    $scope4 = Enter-CodeLockOrExit -Path @($docTarget) -Reason 'code-lock-scope.tests case 10'
    throw 'deliberate failure inside the locked window'
}
catch { $threw = $true }
finally { if ($scope4) { Exit-CodeLockScope -Scope $scope4 } }
Assert-Case 'a throw inside the window still releases through the caller finally' `
    ($threw -and -not (Test-Path -LiteralPath $scriptsLock))

# --- Case 11: a PLAN/ only path set takes no domain --------------------------
$planScope = $null
try {
    $planScope = Enter-CodeLockOrExit -Path @((Join-Path $repoRoot 'PLAN/S2615_bugfix-icon-inventory-regenerator-skips-code-lock.md')) `
        -Reason 'code-lock-scope.tests case 11'
    Assert-Case 'a PLAN/ only set resolves to no domain and acquires nothing' `
        ((@($planScope.Acquired).Count -eq 0) -and -not (Test-Path -LiteralPath $scriptsLock))
}
finally { if ($planScope) { Exit-CodeLockScope -Scope $planScope } }

# --- Cases 12-13: a foreign holder yields exit 4 and writes nothing -----------
$probe = Join-Path $repoRoot 'temp/S2615/probe-exit-4.ps1'
New-Item -ItemType Directory -Path (Split-Path -Parent $probe) -Force | Out-Null
@"
Set-StrictMode -Version Latest
`$ErrorActionPreference = 'Stop'
. '$helper'
`$null = Enter-CodeLockOrExit -Path @('docs/ICON_LEGEND.md') -Reason 'code-lock-scope.tests case 12 (foreign)'
exit 0
"@ | Set-Content -LiteralPath $probe -Encoding utf8

$held = $null
$probeExit = -1
try {
    $held = Enter-CodeLockOrExit -Path @($docTarget) -Reason 'code-lock-scope.tests case 12 holder'
    $before = (Get-Item -LiteralPath $scriptsLock).LastWriteTimeUtc
    # A different first link of the identity chain makes the child a stranger to this lock, without
    # forging any state: it queues and refuses exactly as a real sibling session would.
    $priorAgentId = $env:FMS_AGENT_ID
    $env:FMS_AGENT_ID = 'code-lock-scope-tests-foreign'
    try { & pwsh -NoProfile -File $probe *> (Join-Path $repoRoot 'temp/S2615/probe-exit-4.out'); $probeExit = $LASTEXITCODE }
    finally { $env:FMS_AGENT_ID = $priorAgentId }
    $after = (Get-Item -LiteralPath $scriptsLock).LastWriteTimeUtc
    Assert-Case 'a foreign holder makes the helper exit 4' ($probeExit -eq 4) "actual exit $probeExit"
    Assert-Case 'the refused run left the holder lock untouched' ($after -eq $before)
}
finally {
    if ($held) { Exit-CodeLockScope -Scope $held }
    # The refusal keeps its place in the queue by design; that place belongs to an identity nothing
    # else will ever reuse, so this suite retires it rather than leaving it on the real queue head.
    Remove-AgentSessionTickets -Name 'Code.Scripts' -SessionId 'code-lock-scope-tests-foreign' | Out-Null
}

# --- Case 14: a null scope releases nothing and raises nothing ----------------
# The shape every adopting script's `finally` sees when the run died before Enter returned. A
# Mandatory -Scope would PROMPT here, turning a cleanup path into an error that buries the real one.
$nullReleaseOk = $true
try { Exit-CodeLockScope -Scope $null } catch { $nullReleaseOk = $false }
Assert-Case 'releasing a null scope is a silent no-op' $nullReleaseOk

# --- Case 15: running the library as a script refuses -------------------------
& pwsh -NoProfile -File $helper *> (Join-Path $repoRoot 'temp/S2615/probe-direct.out')
Assert-Case 'invoking the library as a script exits 2' ($LASTEXITCODE -eq 2) "actual exit $LASTEXITCODE"

# --- Cases 16-18: Enter-CodeLockOrSkip skips instead of exiting (S2635) -------
# Same shape as cases 12-13, one assertion inverted: the busy domain must NOT end the process.
# assert-source-gates.ps1 reaches this from inside the concurrent fg battery with its verdict
# already computed, so an exit there would paint a passing gate red over a bookkeeping write.
$skipProbe = Join-Path $repoRoot 'temp/S2635/probe-skip-case.ps1'
New-Item -ItemType Directory -Path (Split-Path -Parent $skipProbe) -Force | Out-Null
@"
Set-StrictMode -Version Latest
`$ErrorActionPreference = 'Stop'
. '$helper'
`$s = Enter-CodeLockOrSkip -Path @('docs/ICON_LEGEND.md') -Reason 'code-lock-scope.tests skip (foreign)'
Write-Host "SKIPPED=`$(`$s.Skipped) ACQUIRED=`$(@(`$s.Acquired).Count)"
Exit-CodeLockScope -Scope `$s
exit 0
"@ | Set-Content -LiteralPath $skipProbe -Encoding utf8

$skipHeld = $null
$skipExit = -1
$skipOut = ''
$foreignQueued = -1
try {
    $skipHeld = Enter-CodeLockOrExit -Path @($docTarget) -Reason 'code-lock-scope.tests skip holder'
    $beforeSkip = (Get-Item -LiteralPath $scriptsLock).LastWriteTimeUtc
    $skipOutFile = Join-Path $repoRoot 'temp/S2635/probe-skip-case.out'
    $priorAgentId = $env:FMS_AGENT_ID
    $env:FMS_AGENT_ID = 'code-lock-scope-tests-skipper'
    try {
        & pwsh -NoProfile -File $skipProbe *> $skipOutFile
        $skipExit = $LASTEXITCODE
    }
    finally { $env:FMS_AGENT_ID = $priorAgentId }
    $skipOut = (Get-Content -LiteralPath $skipOutFile -Raw)
    $afterSkip = (Get-Item -LiteralPath $scriptsLock).LastWriteTimeUtc
    $foreignQueued = @(Get-AgentLockQueue -Name 'Code.Scripts' |
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
    Remove-AgentSessionTickets -Name 'Code.Scripts' -SessionId 'code-lock-scope-tests-skipper' | Out-Null
}

# --- Case 19: OrSkip on a free domain acquires and releases normally ----------
$freeSkip = $null
try {
    $freeSkip = Enter-CodeLockOrSkip -Path @($docTarget) -Reason 'code-lock-scope.tests free skip'
    Assert-Case 'OrSkip on a free domain acquires the domain and does not skip' `
        ((-not $freeSkip.Skipped) -and (@($freeSkip.Acquired).Count -eq 1) -and (Test-Path -LiteralPath $scriptsLock))
}
finally { if ($freeSkip) { Exit-CodeLockScope -Scope $freeSkip } }
Assert-Case 'the OrSkip release frees the domain' (-not (Test-Path -LiteralPath $scriptsLock))

# --- Verdict ------------------------------------------------------------------
Write-Host ''
if ($failures -gt 0) {
    Write-Host "code-lock-scope.tests: FAIL ($failures of $caseNo case(s))" -ForegroundColor Red
    exit 1
}
Write-Host "code-lock-scope.tests: PASS ($caseNo cases)" -ForegroundColor Green
exit 0
