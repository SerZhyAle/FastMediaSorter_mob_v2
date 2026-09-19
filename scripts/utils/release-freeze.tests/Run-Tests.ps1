#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/utils/release-freeze.ps1 - the release-sweep tree freeze (S3010).

.DESCRIPTION
    Every case runs the script against a throwaway fixture tree that FMS_REPO_ROOT points it at, so
    no case can write the repository's own temp/RELEASE-FREEZE.json or disturb a real sweep.

    Both directions are pinned. A freeze that never refuses is the defect it exists to close; a
    freeze that refuses its own owner, or that outlives the session which took it, is the same defect
    wearing the opposite sign - and the second one is worse, because it blocks every sibling with
    nobody left to release it.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'release-freeze.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "script not found: $script" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$failures = 0
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("release-freeze-tests-" + [guid]::NewGuid().ToString('n').Substring(0, 8))
New-Item -ItemType Directory -Path (Join-Path $root 'temp') -Force | Out-Null

$markerPath = Join-Path $root 'temp\RELEASE-FREEZE.json'
$endedPath = Join-Path $root 'temp\RELEASE-FREEZE-ENDED.json'
$stopPath = Join-Path $root 'temp\STOP-SPEC-QUEUE'

$previousRoot = $env:FMS_REPO_ROOT
$env:FMS_REPO_ROOT = $root

function Invoke-Freeze {
    param([string[]] $FreezeArgs)
    & $pwshExe -NoProfile -File $script @FreezeArgs *> $null
    return $LASTEXITCODE
}

function Get-FreezeJson {
    $out = & $pwshExe -NoProfile -File $script -Verb Status -Json 2>$null
    if (-not $out) { return $null }
    return ($out | Out-String).Trim() | ConvertFrom-Json
}

function Reset-Fixture {
    Remove-Item -LiteralPath $markerPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $endedPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stopPath -Force -ErrorAction SilentlyContinue
}

# A marker owned by somebody else. 'live' gives it a transcript written just now, which is the signal
# Get-AgentTicketLiveness reads after the heartbeat; 'stale' gives it neither a reachable transcript
# nor a recent heartbeat, so only the clock is left and the clock says it is gone. 'working' is S3320's
# case and the one the two above cannot express: an owner that has been holding the freeze for an hour -
# so its Take-time heartbeat is long past the 45-minute window - while its transcript says it is writing
# right now, which is every real sweep past its first three-quarters of an hour.
function New-ForeignMarker {
    param([ValidateSet('live', 'stale', 'working')][string] $Kind)

    $now = Get-Date
    $stampMs = { param($d) [DateTimeOffset]::new($d.ToUniversalTime(), [TimeSpan]::Zero).ToUnixTimeMilliseconds() }
    $transcript = Join-Path $root 'foreign-transcript.jsonl'

    if ($Kind -eq 'live') {
        Set-Content -LiteralPath $transcript -Value '{}' -Encoding UTF8
        $when = $now
    }
    elseif ($Kind -eq 'working') {
        Set-Content -LiteralPath $transcript -Value '{}' -Encoding UTF8
        $when = $now.AddMinutes(-60)
    }
    else {
        Remove-Item -LiteralPath $transcript -Force -ErrorAction SilentlyContinue
        $transcript = Join-Path $root 'no-such-transcript.jsonl'
        $when = $now.AddHours(-6)
    }

    $record = [ordered]@{
        schema         = 1
        sessionId      = 'ffffffff-0000-0000-0000-00000000beef'
        enqueuedAt     = (& $stampMs $when)
        lastSeenAt     = (& $stampMs $when)
        transcriptPath = $transcript
        ownerName      = 'foreign-otter-test'
        host           = 'OTHER'
        reason         = 'a sibling sweep'
        takenAt        = $when.ToString('s')
        expiresAt      = $now.AddHours(3).ToString('s')
        stoppedQueue   = $false
    }
    Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "PASS  $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

try {
    Assert-Case 'E1 Status on a tree with no marker reports nothing held' {
        Reset-Fixture
        $code = Invoke-Freeze @('-Verb', 'Status')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false' }
        return $null
    }

    Assert-Case 'E2 Take then Status reports this session as the holder' {
        Reset-Fixture
        $code = Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite')
        if ($code -ne 0) { return "Take expected exit 0, got $code" }
        $state = Get-FreezeJson
        if (-not $state.held) { return 'expected held=true' }
        if ($state.liveness -ne 'self' -and $state.liveness -ne 'undetermined') {
            return "expected liveness self/undetermined, got '$($state.liveness)'"
        }
        return $null
    }

    # The guid-owner regression: Test-AgentIdentityProcessAlive answers false for a session guid by
    # design, so a pid-shaped liveness test would report this live freeze dead within a second.
    Assert-Case 'E3 a freeze this session holds is never read as dead' {
        $state = Get-FreezeJson
        if (-not $state.held) { return 'own freeze was cleared by its own reader' }
        if (-not (Test-Path -LiteralPath $markerPath)) { return 'own marker was deleted' }
        return $null
    }

    Assert-Case 'E4 Take by the same owner refreshes instead of refusing' {
        $code = Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite again')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        $state = Get-FreezeJson
        if ($state.reason -ne 'suite again') { return "expected the reason to be refreshed, got '$($state.reason)'" }
        return $null
    }

    Assert-Case 'E5 Take is refused while a live foreign session holds the freeze' {
        Reset-Fixture
        New-ForeignMarker -Kind live
        $code = Invoke-Freeze @('-Verb', 'Take', '-Reason', 'mine')
        if ($code -ne 4) { return "expected exit 4, got $code" }
        if (-not (Test-Path -LiteralPath $markerPath)) { return "the refused Take deleted someone else's marker" }
        return $null
    }

    Assert-Case 'E6 Release is refused while a live foreign session holds the freeze' {
        $code = Invoke-Freeze @('-Verb', 'Release')
        if ($code -ne 4) { return "expected exit 4, got $code" }
        if (-not (Test-Path -LiteralPath $markerPath)) { return "the refused Release deleted someone else's marker" }
        return $null
    }

    Assert-Case 'E7 a marker whose owner is gone reads as absent and is cleared' {
        Reset-Fixture
        New-ForeignMarker -Kind stale
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false for a dead owner' }
        if ($state.endedBy -ne 'dead-owner') { return "expected endedBy=dead-owner, got '$($state.endedBy)'" }
        if (Test-Path -LiteralPath $markerPath) { return 'the stale marker was left on disk' }
        return $null
    }

    Assert-Case 'E8 an expired marker reads as absent and is cleared' {
        Reset-Fixture
        New-ForeignMarker -Kind live
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.expiresAt = (Get-Date).AddHours(-1).ToString('s')
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false for an expired freeze' }
        if ($state.endedBy -ne 'expiry') { return "expected endedBy=expiry, got '$($state.endedBy)'" }
        if (Test-Path -LiteralPath $markerPath) { return 'the expired marker was left on disk' }
        return $null
    }

    Assert-Case 'E9 Take stands the queue runner down and Release restores it' {
        Reset-Fixture
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        if (-not (Test-Path -LiteralPath $stopPath)) { return 'Take did not write the stop file' }
        [void](Invoke-Freeze @('-Verb', 'Release'))
        if (Test-Path -LiteralPath $stopPath) { return 'Release did not remove the stop file it wrote' }
        return $null
    }

    # The owner may have stopped the runner hours earlier for reasons of their own. A freeze that
    # restarts it on release would silently undo a decision it never made.
    Assert-Case 'E10 a stop the freeze did not request survives Release' {
        Reset-Fixture
        Set-Content -LiteralPath $stopPath -Value 'stopped by the owner' -Encoding UTF8
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        [void](Invoke-Freeze @('-Verb', 'Release'))
        if (-not (Test-Path -LiteralPath $stopPath)) { return "Release removed a stop file the freeze had not written" }
        return $null
    }

    Assert-Case 'E11 Release with nothing held succeeds and says so' {
        Reset-Fixture
        $code = Invoke-Freeze @('-Verb', 'Release')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        return $null
    }

    Assert-Case 'E12 -Hours beyond the SpecTicket ceiling is refused' {
        Reset-Fixture
        $code = Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite', '-Hours', '24')
        if ($code -ne 2) { return "expected exit 2, got $code" }
        if (Test-Path -LiteralPath $markerPath) { return 'a refused Take wrote a marker anyway' }
        return $null
    }

    # The three ends are meant to be interchangeable (docs/DEV_OPS.md, "Release freeze"), so the two
    # that need no co-operation must undo exactly what Release undoes. Until 2026-09-19 they undid the
    # marker alone, and the stop flag left behind stood every queue lane down for six hours.
    Assert-Case 'E13 an expired freeze lifts the stand-down it requested' {
        Reset-Fixture
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        if (-not (Test-Path -LiteralPath $stopPath)) { return 'Take did not write the stop file' }
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.expiresAt = (Get-Date).AddHours(-1).ToString('s')
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false for an expired freeze' }
        if (Test-Path -LiteralPath $stopPath) { return 'the expired freeze left its stop file behind' }
        return $null
    }

    Assert-Case 'E14 a freeze whose owner is gone lifts the stand-down it requested' {
        Reset-Fixture
        New-ForeignMarker -Kind stale
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.stoppedQueue = $true
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        Set-Content -LiteralPath $stopPath -Value 'stop requested by the freeze that died' -Encoding UTF8
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false for a dead owner' }
        if (Test-Path -LiteralPath $stopPath) { return 'the dead freeze left its stop file behind' }
        return $null
    }

    # The mirror of E10. An owner's own stop must survive every one of the three ends, not just Release.
    Assert-Case 'E15 a stop the freeze did not request survives the expiry clearing' {
        Reset-Fixture
        Set-Content -LiteralPath $stopPath -Value 'stopped by the owner' -Encoding UTF8
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.expiresAt = (Get-Date).AddHours(-1).ToString('s')
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        [void](Get-FreezeJson)
        if (-not (Test-Path -LiteralPath $stopPath)) { return 'the expiry removed a stop file the freeze had not written' }
        return $null
    }

    # S3320, the regression this pair exists for. Take stamps lastSeenAt once and nothing refreshes it,
    # and Get-AgentTicketLiveness ranks that heartbeat above the transcript - so until the marker started
    # presenting the newest of the two, EVERY freeze was evictable 45 minutes after Take, by any reader,
    # including the Status child guard-release-freeze.ps1 spawns in a sibling session. Measured
    # 2026-09-19: held=false, endedBy=dead-owner, marker deleted, with the owner's transcript written one
    # second earlier and three hours of the freeze's own expiry left.
    Assert-Case 'E16 a freeze older than the stale window survives while its owner is writing' {
        Reset-Fixture
        New-ForeignMarker -Kind working
        $state = Get-FreezeJson
        if (-not $state.held) { return "a live owner's freeze was cleared - endedBy '$($state.endedBy)'" }
        if (-not (Test-Path -LiteralPath $markerPath)) { return 'the marker was deleted under a live owner' }
        return $null
    }

    # The other direction, and the one that must not be lost while fixing the first: an owner with no
    # signal at all is still evicted, or an abandoned freeze blocks every sibling for its whole expiry.
    Assert-Case 'E17 the same age with no reachable transcript is still cleared' {
        Reset-Fixture
        New-ForeignMarker -Kind stale
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false for an owner with no signal' }
        if ($state.endedBy -ne 'dead-owner') { return "expected endedBy=dead-owner, got '$($state.endedBy)'" }
        return $null
    }

    Assert-Case 'E18 the owner reading its own freeze refreshes the heartbeat' {
        Reset-Fixture
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $backdated = [DateTimeOffset]::new((Get-Date).AddMinutes(-60).ToUniversalTime(), [TimeSpan]::Zero).ToUnixTimeMilliseconds()
        $record.lastSeenAt = $backdated
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        [void](Get-FreezeJson)
        $after = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([int64]$after.lastSeenAt -le [int64]$backdated) { return 'the holder read left the stale heartbeat on disk' }
        return $null
    }

    # "No freeze held" used to read the same whether the tree had never been frozen or the sweep's own
    # freeze had ended under it, which is why the package-39 loss was noticed only by a claim that should
    # have been refused (S3320 section 0).
    Assert-Case 'E19 a freeze that ends leaves a record the next Status names' {
        Reset-Fixture
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.expiresAt = (Get-Date).AddHours(-1).ToString('s')
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        [void](Get-FreezeJson)
        if (-not (Test-Path -LiteralPath $endedPath)) { return 'the cleared freeze left no record of its end' }
        $state = Get-FreezeJson
        if ($state.held) { return 'expected held=false after the expiry' }
        if ($state.endedBy -ne 'expiry') { return "expected the later read to still say expiry, got '$($state.endedBy)'" }
        if (-not $state.endedAt) { return 'the later read carried no endedAt' }
        return $null
    }

    Assert-Case 'E20 taking a fresh freeze clears the previous end record' {
        if (-not (Test-Path -LiteralPath $endedPath)) { return 'precondition: E19 should have left the record in place' }
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        if (Test-Path -LiteralPath $endedPath) { return 'a new freeze kept the record of the old one ending' }
        $state = Get-FreezeJson
        if (-not $state.held) { return 'expected the fresh freeze to be held' }
        return $null
    }
}
finally {
    if ($null -eq $previousRoot) { Remove-Item Env:\FMS_REPO_ROOT -ErrorAction SilentlyContinue }
    else { $env:FMS_REPO_ROOT = $previousRoot }
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) {
    Write-Host "release-freeze tests: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'release-freeze tests: all cases passed.' -ForegroundColor Green
exit 0
