#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for the device lease sweep (S1926).

.DESCRIPTION
    The sweep is the only thing standing between a killed session and a device held for ever -
    there is no watchdog by design (S1432), so eviction happens when somebody reads next. If it
    silently stops working, the failure is invisible until a human notices a device nobody can
    take.

    Both cases are driven through the script's own verbs. Re-implementing the staleness rule here
    would make the test agree with itself rather than with the thing it guards.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every case passed.
      1 - a case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Three levels up: device-lease.tests -> devtest -> scripts -> repo root.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$leaseScript = Join-Path $repoRoot 'scripts/devtest/device-lease.ps1'
$leaseDir = Join-Path $repoRoot 'temp/DEVICE.LEASES'
New-Item -ItemType Directory -Path $leaseDir -Force | Out-Null

$staleSerial = 'test-stale-device'
$liveSerial = 'test-live-device'

function Remove-TestLease {
    param([Parameter(Mandatory)][string]$Serial)
    Remove-Item -LiteralPath (Join-Path $leaseDir "$Serial.json") -Force -ErrorAction SilentlyContinue
}

try {
    Remove-TestLease -Serial $staleSerial
    Remove-TestLease -Serial $liveSerial

    # A lease whose owner was last seen well beyond the liveness window, with no readable
    # transcript to contradict it. The ceiling is 120 minutes, so a day-old claim is stale twice
    # over - by silence and by age.
    $longAgo = [DateTimeOffset]::UtcNow.AddDays(-1).ToUnixTimeMilliseconds()
    $forged = [ordered]@{
        schema         = 1
        id             = $staleSerial
        sessionId      = '00000000-0000-4000-8000-00000000dead'
        host           = 'GONEHOST'
        pid            = 4242
        reason         = 'device-lease tests: abandoned session'
        claimedAt      = $longAgo
        lastSeenAt     = $longAgo
        transcriptPath = ''
    }
    $forged | ConvertTo-Json -Compress |
        Set-Content -LiteralPath (Join-Path $leaseDir "$staleSerial.json") -Encoding utf8NoBOM

    # This session's own lease, taken through the script so it carries a real session id.
    & pwsh -NoProfile -File $leaseScript -Verb Claim -Id $liveSerial -Reason 'device-lease tests: live' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Setup failed: could not claim $liveSerial (exit $LASTEXITCODE)." }

    # Any read sweeps first, so List is the trigger as well as the observation.
    $remaining = @(& pwsh -NoProfile -File $leaseScript -Verb List)
    if ($LASTEXITCODE -ne 0) { throw "List failed with exit $LASTEXITCODE." }

    if ($remaining -contains $staleSerial) {
        throw "Sweep kept an abandoned lease: $staleSerial is still held after a read."
    }
    if ($remaining -notcontains $liveSerial) {
        throw "Sweep evicted a live session's lease: $liveSerial vanished while its owner was active."
    }

    # A swept device must be takeable again - eviction that leaves the serial unclaimable would
    # trade a stuck lease for a stuck device.
    & pwsh -NoProfile -File $leaseScript -Verb Claim -Id $staleSerial -Reason 'device-lease tests: re-take' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "A swept device could not be claimed: exit $LASTEXITCODE." }

    Write-Output 'device-lease tests: PASS (stale lease swept, live lease kept, swept device re-claimable)'
    exit 0
}
finally {
    foreach ($serial in @($staleSerial, $liveSerial)) {
        & pwsh -NoProfile -File $leaseScript -Verb Release -Id $serial 2>&1 | Out-Null
        Remove-TestLease -Serial $serial
    }
}
