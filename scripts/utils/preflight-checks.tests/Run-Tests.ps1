#requires -Version 7.0
<#
.SYNOPSIS
    S2872 - contract tests for scripts/utils/preflight-checks.ps1.

.DESCRIPTION
    Every property here fails SILENTLY. A check that never fires is indistinguishable from a
    session that was clean, so a preflight quietly reporting PASS on a leaked lock is worse than
    no preflight - it certifies the leak. A check that always fires is the same defect from the
    other side: the reader learns to skip the verdict, and the one run that mattered goes unread.
    Neither shows up in the script's own output, so only a suite can see them.

    Identity is the axis every case turns on, because "mine" is what the script judges. A case
    needing a DIFFERENT identity runs in a child pwsh with its own FMS_AGENT_ID - the first link
    of the chain - rather than by forging a lock, queue or lease file, so nothing here writes
    state a real session could mistake for its own. The one thing a case does write is an agent
    chat line for its throwaway identity, through the supported writer: the chat is descriptive
    by contract (CLAUDE.md Rule 34 - it decides nothing), so a line there breaks no invariant
    and the store's own retention sweeps it.

    The parent session's real state is used deliberately in case 5: it holds whatever it holds,
    and a foreign identity must not be blamed for it.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - a case failed.
      2 - could not verify: the script under test is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$target = Join-Path $repoRoot 'scripts/utils/preflight-checks.ps1'
$chat = Join-Path $repoRoot 'scripts/utils/agent-chat.ps1'
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

if (-not (Test-Path -LiteralPath $target)) {
    Write-Error "preflight-checks.tests: cannot verify - $target is missing." -ErrorAction Continue
    exit 2
}

Write-Host 'preflight-checks contract suite' -ForegroundColor Cyan

# Runs the script under a chosen identity and returns both halves of the answer. -Json is used
# throughout: the text report is for a human and its wording is free to change, while the JSON
# shape is what a caller could script against.
function Invoke-Preflight {
    # The identity comes from FMS_AGENT_ID in this process's environment, which the child
    # inherits - there is no parameter, because that is exactly the chain under test.
    $childArgs = @('-NoProfile', '-File', $target, '-Json')
    $out = pwsh @childArgs 2>$null
    return [pscustomobject]@{ Out = ($out -join "`n"); Code = $LASTEXITCODE }
}

$throwaway = "preflight-test-$PID-$([System.Diagnostics.Stopwatch]::GetTimestamp())"

# --- 1. a missing FMS_AGENT_ID is a failure, and it names itself ---------------
$prior = $env:FMS_AGENT_ID
try {
    $env:FMS_AGENT_ID = ''
    $r = Invoke-Preflight
    $o = $null
    try { $o = $r.Out | ConvertFrom-Json } catch { }
    $idCheck = if ($o) { @($o.checks | Where-Object { $_.name -eq 'agent identity' }) } else { @() }
    Assert-Case 'a missing FMS_AGENT_ID fails the identity check' `
        ($idCheck.Count -eq 1 -and $idCheck[0].result -eq 'FAIL') "checks: $($r.Out)"
    Assert-Case 'a missing FMS_AGENT_ID makes the run exit 1' ($r.Code -eq 1) "exit was $($r.Code)"
    Assert-Case 'the identity failure carries a remedy' `
        ($idCheck.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace([string]$idCheck[0].remedy))
}
finally { $env:FMS_AGENT_ID = $prior }

# --- 2. the JSON shape is stable ----------------------------------------------
$env:FMS_AGENT_ID = $throwaway
try {
    $r = Invoke-Preflight
    $o = $null
    try { $o = $r.Out | ConvertFrom-Json } catch { }
    Assert-Case '-Json output parses' ($null -ne $o) "raw: $($r.Out)"
    Assert-Case '-Json carries one entry per check, each with a result' `
        ($null -ne $o -and @($o.checks).Count -ge 4 -and -not (@($o.checks) | Where-Object { [string]::IsNullOrWhiteSpace([string]$_.result) })) `
        "checks: $(if ($o) { @($o.checks).Count } else { 'none' })"
    Assert-Case '-Json reports the verdict and the failure count together' `
        ($null -ne $o -and $o.PSObject.Properties['verdict'] -and $o.PSObject.Properties['failed'])

    # --- 3. an identity with no session line is told so ------------------------
    $sessionCheck = if ($o) { @($o.checks | Where-Object { $_.name -eq 'session start line' }) } else { @() }
    Assert-Case 'an identity that posted no session line fails that check' `
        ($sessionCheck.Count -eq 1 -and $sessionCheck[0].result -eq 'FAIL')

    # --- 4. this parent session's real lock is not blamed on a stranger ---------
    $lockCheck = if ($o) { @($o.checks | Where-Object { $_.name -eq 'held locks' }) } else { @() }
    Assert-Case "a foreign session's held lock does not fail this caller" `
        ($lockCheck.Count -eq 1 -and $lockCheck[0].result -eq 'PASS') "actual: $(if ($lockCheck.Count) { $lockCheck[0].actual } else { 'no check' })"

    $leaseCheck = if ($o) { @($o.checks | Where-Object { $_.name -eq 'ticket leases' }) } else { @() }
    Assert-Case "a foreign session's ticket lease does not fail this caller" `
        ($leaseCheck.Count -eq 1 -and $leaseCheck[0].result -eq 'PASS') "actual: $(if ($leaseCheck.Count) { $leaseCheck[0].actual } else { 'no check' })"

    # --- 5. a clean identity that did post its session line exits 0 -------------
    & pwsh -NoProfile -File $chat -Verb Post -Kind session -Note 'session started (preflight contract suite)' *> $null
    $r2 = Invoke-Preflight
    $o2 = $null
    try { $o2 = $r2.Out | ConvertFrom-Json } catch { }
    Assert-Case 'a clean identity with a posted session line exits 0' ($r2.Code -eq 0) "exit was $($r2.Code); out: $($r2.Out)"
    Assert-Case 'a clean run reports verdict PASS and zero failures' `
        ($null -ne $o2 -and $o2.verdict -eq 'PASS' -and [int]$o2.failed -eq 0)

    # --- 6. no check is structurally always-on ----------------------------------
    # The advisory dropped during S2872 fired on 38 standing dirty .kt, which is this tree's
    # normal state. Case 5 proving a clean exit 0 is what keeps that class of check out.
    Assert-Case 'a clean run emits no advisory either' `
        ($null -ne $o2 -and [int]$o2.advisories -eq 0) "advisories: $(if ($o2) { $o2.advisories } else { 'unknown' })"
}
finally {
    & pwsh -NoProfile -File $chat -Verb Post -Kind session -Note 'session ended (preflight contract suite finished)' *> $null
    $env:FMS_AGENT_ID = $prior
}

Write-Host ''
if ($failures -gt 0) {
    Write-Error ("preflight-checks.tests: FAIL - {0} of {1} case(s) failed." -f $failures, $caseNo) -ErrorAction Continue
    exit 1
}
Write-Host ("preflight-checks.tests: PASS ({0} case(s))" -f $caseNo) -ForegroundColor Green
exit 0
