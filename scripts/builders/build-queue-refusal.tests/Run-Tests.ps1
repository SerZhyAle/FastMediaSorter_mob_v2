# Run-Tests.ps1 (S2612) - contract suite for scripts/builders/build-queue-refusal.ps1.
#
# The defect being guarded: a short, foreground-scale fast check used to BLOCK inside
# Enter-BuildLockOrExit on a busy Build.* domain until the Bash tool killed it at 120 s, and a killed
# foreground check reports no verdict at all. Measured 2026-09-06: 66 of 972 short runs (6.8%) waited
# past 120 s in a three-day window.
#
# Both directions are asserted, because a helper that only ever says "refuse" is as wrong as the
# block it replaced:
#   * it REFUSES a short run on a genuinely busy domain,
#   * it SPARES a long run, a free domain, a stale holder, and an explicit block-through.
#
# Hermetic: no lock, no queue, no second process, no gradle. Every lookup in the helper is an
# injectable script block exactly so the decision can be driven from crafted state here.
#
# Usage:  pwsh -NoProfile -File scripts/builders/build-queue-refusal.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/build-queue-refusal.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# ---- fixtures -------------------------------------------------------------------------------

function New-Status([bool]$exists, [bool]$stale, [int]$lockPid = 4242, [string]$reason = 'check-standard-fast.ps1 -Mode Unit (app_v2 standardDebug, whole suite) - LONG hold (background-scale)') {
    return [pscustomobject]@{
        Exists = $exists; Stale = $stale; Pid = $lockPid; AgeSeconds = 91
        Reason = $reason; Host = 'WS'; SessionId = 'sibling-session'; Name = 'build.phone'
    }
}

$noQueue   = { param($d) @() }
$freeLock  = { param($d) New-Status $false $false }
$heldLock  = { param($d) New-Status $true  $false }
$staleLock = { param($d) New-Status $true  $true }
$envOff    = { param($s) $null }
$envOn     = { param($s) if ($s -eq 'LOCK_BLOCK') { '1' } else { $null } }

# SelfPid and InheritedHolder are pinned rather than defaulted: the fixture holder pid must never
# collide with the pid this suite happens to run under, or the re-entrancy exemption would fire and
# the occupancy cases would pass for the wrong reason.
function Get-State([scriptblock]$status, [scriptblock]$queue, [string[]]$domains = @('build.phone'),
                   [int]$selfPid = 1, [string]$inherited = '') {
    return @(Get-BuildDomainState -Domains $domains -StatusProvider $status -QueueProvider $queue `
        -SessionId 'me' -SelfPid $selfPid -InheritedHolder $inherited)
}

# ---- hold class -----------------------------------------------------------------------------

Write-Host "`nHold class" -ForegroundColor Cyan

foreach ($m in @('Code', 'Resources', 'CodeAndResources', 'AndroidTest')) {
    Assert-That "mode $m is short class" ((Get-BuildHoldClass -Mode $m) -eq 'short') "got $(Get-BuildHoldClass -Mode $m)"
}
foreach ($m in @('Unit', 'ConnectedAndroidTest', 'Assemble')) {
    Assert-That "mode $m is long class" ((Get-BuildHoldClass -Mode $m) -eq 'long') "got $(Get-BuildHoldClass -Mode $m)"
}

# S2580 owns this wording - it is what a queued sibling reads in lock-status.ps1 -Queue. A silent
# change here would make a 14 s compile and a 40 min suite indistinguishable in the queue again.
Assert-That "short label keeps the S2580 wording" `
    ((Get-BuildHoldClassLabel -Mode 'Code') -eq 'short hold (foreground-scale)') `
    (Get-BuildHoldClassLabel -Mode 'Code')
Assert-That "long label keeps the S2580 wording" `
    ((Get-BuildHoldClassLabel -Mode 'Unit') -eq 'LONG hold (background-scale)') `
    (Get-BuildHoldClassLabel -Mode 'Unit')

# ---- occupancy ------------------------------------------------------------------------------

Write-Host "`nDomain occupancy" -ForegroundColor Cyan

Assert-That "a live holder is busy" `
    ((Get-State $heldLock $noQueue)[0].Busy) "expected Busy"
Assert-That "a free lock is not busy" `
    (-not (Get-State $freeLock $noQueue)[0].Busy) "expected not Busy"

# A stale lock is a dead process's leftovers. Judging it busy would refuse a run that could have
# started at once, which is the only way this feature can make the tree slower rather than faster.
Assert-That "a stale holder is not busy" `
    (-not (Get-State $staleLock $noQueue)[0].Busy) "expected not Busy"

$foreignHead = { param($d) @([pscustomobject]@{ sessionId = 'sibling-session'; seq = 7 }) }
$ownHead     = { param($d) @([pscustomobject]@{ sessionId = 'me'; seq = 7 }) }

# S1448: a free lock whose queue head belongs to someone else is promised, not available.
Assert-That "free lock with a foreign queue head is busy" `
    ((Get-State $freeLock $foreignHead)[0].Busy) "expected Busy"
Assert-That "free lock with our own queue head is not busy" `
    (-not (Get-State $freeLock $ownHead)[0].Busy) "expected not Busy"
Assert-That "queue-head block is labelled as such" `
    ((Get-State $freeLock $foreignHead)[0].BlockedBy -eq 'queue-head') `
    (Get-State $freeLock $foreignHead)[0].BlockedBy

# Re-entrancy. Enter-BuildLockOrExit reuses a domain the same run already holds instead of queueing
# behind itself, but that guard is INSIDE the function and this check runs before it. post-change.ps1
# launches check-standard-fast.ps1 as a child process in two places, and FMS_BUILD_LOCK_HELD_BY is
# inherited so a descendant can recognise a lock its ancestor owns. Refusing there would break a
# working closure with exit 4 over a lock we hold.
Assert-That "a domain held by this very process is not busy" `
    (-not (Get-State $heldLock $noQueue -selfPid 4242)[0].Busy) "expected not Busy"
Assert-That "a domain held by an ancestor is not busy" `
    (-not (Get-State $heldLock $noQueue -inherited '4242:637')[0].Busy) "expected not Busy"
Assert-That "self-held is flagged" `
    ((Get-State $heldLock $noQueue -selfPid 4242)[0].SelfHeld) "expected SelfHeld"

# Whoever is queued for a domain we already hold is queued BEHIND us, so reading the head would
# report our own successor as the thing blocking us.
Assert-That "a self-held domain ignores a foreign queue head" `
    (-not (Get-State $heldLock $foreignHead -selfPid 4242)[0].Busy) "expected not Busy"

# ---- the decision ---------------------------------------------------------------------------

Write-Host "`nRefusal decision" -ForegroundColor Cyan

$busyState = Get-State $heldLock $noQueue
$freeState = Get-State $freeLock $noQueue

Assert-That "short class + busy domain refuses" `
    ((Test-BuildQueueRefusal -HoldClass 'short' -DomainState $busyState -EnvProvider $envOff).ShouldRefuse) `
    "expected ShouldRefuse"

Assert-That "short class + free domain does not refuse" `
    (-not (Test-BuildQueueRefusal -HoldClass 'short' -DomainState $freeState -EnvProvider $envOff).ShouldRefuse) `
    "expected no refusal"

# A long-class run is backgrounded by Rule 6, so it has no 120 s ceiling to lose the verdict to.
Assert-That "long class + busy domain does not refuse" `
    (-not (Test-BuildQueueRefusal -HoldClass 'long' -DomainState $busyState -EnvProvider $envOff).ShouldRefuse) `
    "expected no refusal"

Assert-That "-BlockThrough suppresses the refusal" `
    (-not (Test-BuildQueueRefusal -HoldClass 'short' -DomainState $busyState -BlockThrough -EnvProvider $envOff).ShouldRefuse) `
    "expected no refusal"

Assert-That "FMS_LOCK_BLOCK=1 suppresses the refusal" `
    (-not (Test-BuildQueueRefusal -HoldClass 'short' -DomainState $busyState -EnvProvider $envOn).ShouldRefuse) `
    "expected no refusal"

Assert-That "an empty domain set never refuses" `
    (-not (Test-BuildQueueRefusal -HoldClass 'short' -DomainState @() -EnvProvider $envOff).ShouldRefuse) `
    "expected no refusal"

# S2109: naming the first REQUESTED domain instead of the first BUSY one made a Build.Wear refusal
# read as a phone-build refusal twice on the two modules' fast checks.
$mixed = @(
    [pscustomobject]@{ Domain = 'build.phone'; Busy = $false; BlockedBy = 'none' },
    [pscustomobject]@{ Domain = 'build.wear';  Busy = $true;  BlockedBy = 'holder'; HolderPid = 9; HolderAge = 5; HolderReason = 'r'; HeadSession = '' }
)
$mixedVerdict = Test-BuildQueueRefusal -HoldClass 'short' -DomainState $mixed -EnvProvider $envOff
Assert-That "multi-domain refusal names the domain that actually blocked" `
    ($mixedVerdict.BlockingDomain -eq 'build.wear') $mixedVerdict.BlockingDomain

# ---- the report -----------------------------------------------------------------------------

Write-Host "`nRefusal report" -ForegroundColor Cyan

$report = Format-BuildQueueRefusalReport -BlockingDomain 'build.phone' -BlockingState $busyState[0] `
    -Reason 'check-standard-fast.ps1 -Mode Code' -HandoffPath 'temp/LEASE-HANDOFF/x.json'
$text = $report -join "`n"

Assert-That "report names the blocking domain" ($text -match 'BUILD\.PHONE') $text
Assert-That "report names the holder pid" ($text -match '4242') $text
Assert-That "report carries a runnable wait command" ($text -match 'wait-for-lock-turn\.ps1 -Name build\.phone') $text
Assert-That "report passes the handoff path through" ($text -match '-Handoff "temp/LEASE-HANDOFF/x\.json"') $text
Assert-That "report states the place survives" ($text -match 'survives this exit') $text
Assert-That "report names the block-through escape" ($text -match 'FMS_LOCK_BLOCK=1') $text
Assert-That "report names the budget" ($text -match "budget $(Get-BuildQueueBudgetSeconds)s") $text

# -Acquire cannot be offered for a build domain: the lock's staleness is judged by the acquiring PID
# and the waiter exits at once, so the lock would read as dead on arrival.
Assert-That "report does not offer -Acquire for a build domain" (-not ($text -match '-Acquire')) $text

# S3300: the -BlockThrough clause is the caller's to claim. Printed unconditionally it advertised a
# flag belonging to check-standard-fast.ps1 under every facade that spawns it and forwards its
# stdout, and following it cost a run (post-change.ps1, 2026-09-18, exit 2 'unrecognized argument').
Assert-That "an unnamed entry point gets no -BlockThrough clause" `
    (-not ($text -match '-BlockThrough')) $text

$namedReport = (Format-BuildQueueRefusalReport -BlockingDomain 'build.phone' -BlockingState $busyState[0] `
    -Reason 'r' -BlockThroughScript 'scripts/builders/check-standard-fast.ps1') -join "`n"
Assert-That "a named entry point gets the -BlockThrough clause beside its own name" `
    ($namedReport -match '-BlockThrough when you invoke scripts/builders/check-standard-fast\.ps1') $namedReport
Assert-That "the named form still carries the env-var escape" `
    ($namedReport -match 'FMS_LOCK_BLOCK=1') $namedReport

# The clause says a forwarding facade does not accept the flag. That sentence is a claim about
# post-change.ps1, so it is pinned here: the day the facade declares the parameter this fails and the
# wording gets revisited, instead of going stale silently the way the original hint did.
$facadeAst = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $repoRoot 'scripts/post-change.ps1'), [ref]$null, [ref]$null)
$facadeParams = @($facadeAst.ParamBlock.Parameters | ForEach-Object { $_.Name.VariablePath.UserPath })
Assert-That "post-change.ps1 still does not declare -BlockThrough" `
    (-not ($facadeParams -contains 'BlockThrough')) ($facadeParams -join ', ')

$queueHeadReport = (Format-BuildQueueRefusalReport -BlockingDomain 'build.phone' `
    -BlockingState (Get-State $freeLock $foreignHead)[0] -Reason 'r') -join "`n"
Assert-That "queue-head refusal names the session that holds the turn" `
    ($queueHeadReport -match 'sibling-session') $queueHeadReport

# ---- verdict --------------------------------------------------------------------------------

Write-Host ""
Write-Host "build-queue-refusal: $script:pass passed, $script:fail failed" -ForegroundColor $(if ($script:fail) { 'Red' } else { 'Green' })
if ($script:fail -gt 0) { exit 1 }
exit 0
