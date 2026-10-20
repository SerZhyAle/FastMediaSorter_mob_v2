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
# Absent for E1-E20 on purpose: those cases are the proof that a tree with no queue file is untouched.
$queuePath = Join-Path $root 'PLAN\RELEASE_QUEUE.md'
$queueSeed = (@(
        '# Release Queue',
        'current-next-release: 46',
        '',
        'ticket                         changed         status',
        '46',
        'S0001_fixture-ticket           26-10-14 10:00  Draft'
    ) -join "`r`n") + "`r`n"

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
    param([ValidateSet('live', 'stale', 'working')][string] $Kind, [string] $Path = $markerPath)

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
    Set-Content -LiteralPath $Path -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
}

$wearMarkerPath = Join-Path $root 'temp\RELEASE-FREEZE-WEAR.json'
$wearEndedPath = Join-Path $root 'temp\RELEASE-FREEZE-WEAR-ENDED.json'

function Reset-WearFixture {
    Reset-Fixture
    Remove-Item -LiteralPath $wearMarkerPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $wearEndedPath -Force -ErrorAction SilentlyContinue
}

function Get-WearAnnounceLines { return @((Get-QueueText) -split "`r`n" | Where-Object { $_.StartsWith('# freeze-wear:') }) }

function Set-QueueSeed {
    param([string] $Text = $queueSeed)
    New-Item -ItemType Directory -Path (Split-Path -Parent $queuePath) -Force | Out-Null
    [System.IO.File]::WriteAllText($queuePath, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Get-QueueText { return [System.IO.File]::ReadAllText($queuePath) }

function Get-AnnounceLines { return @((Get-QueueText) -split "`r`n" | Where-Object { $_.StartsWith('# freeze:') }) }

function Get-SeedWithAnnouncement {
    $lines = [System.Collections.Generic.List[string]]::new([string[]]($queueSeed.TrimEnd("`r", "`n") -split "`r`n"))
    $lines.Insert(2, "# freeze: held by gone-session (session deadbeef) since 2026-10-14T10:00:00 until 2026-10-14T14:00:00 - 'old'")
    return ([string]::Join("`r`n", $lines.ToArray()) + "`r`n")
}

$script:executed = 0
function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:executed++
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

    # S4208: the queue header promised a `# freeze:` line that nothing wrote, so the package-45 sweep's
    # unclaimable tickets sat at the head of the package with no visible reason.
    Assert-Case 'E21 Take writes one announcement line directly under the release marker' {
        Reset-Fixture
        Set-QueueSeed
        $code = Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite announce')
        if ($code -ne 0) { return "Take expected exit 0, got $code" }
        $state = Get-FreezeJson
        $lines = (Get-QueueText) -split "`r`n"
        if (-not $lines[2].StartsWith('# freeze:')) { return "expected the announcement as line 3, found '$($lines[2])'" }
        if (@(Get-AnnounceLines).Count -ne 1) { return "expected exactly one announcement, found $(@(Get-AnnounceLines).Count)" }
        if ($lines[2] -notlike "*$($state.ownerName)*") { return "the line does not name the holder '$($state.ownerName)'" }
        # ConvertFrom-Json turns the ISO stamp into a DateTime; interpolating it would format it in the
        # host culture and never match the 's' form the announcement carries.
        $expiry = if ($state.expiresAt -is [datetime]) { $state.expiresAt.ToString('s') } else { [string]$state.expiresAt }
        if ($lines[2] -notlike "*until $expiry*") { return "the line does not carry the expiry $expiry" }
        if ($lines[2] -notlike "*'suite announce'*") { return 'the line does not carry the reason' }
        $without = ([string]::Join("`r`n", @($lines | Where-Object { -not $_.StartsWith('# freeze:') })))
        if ($without -cne $queueSeed) { return 'Take changed the queue beyond the one announcement line' }
        return $null
    }

    Assert-Case 'E22 Release removes the announcement and leaves the queue byte-identical' {
        $code = Invoke-Freeze @('-Verb', 'Release')
        if ($code -ne 0) { return "Release expected exit 0, got $code" }
        if ((Get-QueueText) -cne $queueSeed) { return 'the queue differs from its seed after Release' }
        return $null
    }

    Assert-Case 'E23 an expired freeze takes its announcement with it' {
        Reset-Fixture
        Set-QueueSeed
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        $record = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.expiresAt = (Get-Date).AddHours(-1).ToString('s')
        Set-Content -LiteralPath $markerPath -Value ($record | ConvertTo-Json -Depth 4) -Encoding UTF8
        [void](Get-FreezeJson)
        if ((Get-QueueText) -cne $queueSeed) { return 'the expired freeze left its announcement behind' }
        return $null
    }

    Assert-Case 'E24 a freeze whose owner is gone takes its announcement with it' {
        Reset-Fixture
        Set-QueueSeed -Text (Get-SeedWithAnnouncement)
        New-ForeignMarker -Kind stale
        [void](Get-FreezeJson)
        if ((Get-QueueText) -cne $queueSeed) { return 'the dead freeze left its announcement behind' }
        return $null
    }

    # A sibling's read is what re-asserts a line the catalog writer overwrote, so the foreign-live read
    # writes too - and Release -Force must clean up exactly like an ordinary Release.
    Assert-Case 'E25 a read of a live foreign freeze announces it and Release -Force removes it' {
        Reset-Fixture
        Set-QueueSeed
        New-ForeignMarker -Kind live
        $state = Get-FreezeJson
        if (-not $state.held) { return 'expected the live foreign freeze to be held' }
        $found = @(Get-AnnounceLines)
        if ($found.Count -ne 1 -or $found[0] -notlike '*foreign-otter-test*') { return 'the read did not announce the foreign holder' }
        $code = Invoke-Freeze @('-Verb', 'Release', '-Force')
        if ($code -ne 0) { return "Release -Force expected exit 0, got $code" }
        if ((Get-QueueText) -cne $queueSeed) { return 'Release -Force left the announcement behind' }
        return $null
    }

    Assert-Case 'E26 a held freeze restores an announcement the queue writer overwrote' {
        Reset-Fixture
        Set-QueueSeed
        [void](Invoke-Freeze @('-Verb', 'Take', '-Reason', 'suite'))
        Set-QueueSeed
        [void](Get-FreezeJson)
        if (@(Get-AnnounceLines).Count -ne 1) { return 'the next read did not restore the announcement' }
        [void](Invoke-Freeze @('-Verb', 'Release'))
        return $null
    }

    Assert-Case 'E27 an announcement with no freeze behind it is removed by Status' {
        Reset-Fixture
        Set-QueueSeed -Text (Get-SeedWithAnnouncement)
        $code = Invoke-Freeze @('-Verb', 'Status')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        if ((Get-QueueText) -cne $queueSeed) { return 'a stale announcement survived a read of an unfrozen tree' }
        return $null
    }

    # S4249: two scopes, two independent freezes.
    Assert-Case 'E28 two sessions take the phone and the wear freeze and both exit 0' {
        Reset-WearFixture
        New-ForeignMarker -Kind live -Path $wearMarkerPath
        $code = Invoke-Freeze @('-Verb', 'Take', '-Scope', 'phone', '-Reason', 'phone sweep')
        if ($code -ne 0) { return "phone Take beside a foreign wear freeze expected exit 0, got $code" }
        if (-not (Test-Path -LiteralPath $wearMarkerPath)) { return 'the phone Take touched the wear marker' }
        Remove-Item -LiteralPath $markerPath -Force
        Remove-Item -LiteralPath $wearMarkerPath -Force
        New-ForeignMarker -Kind live -Path $markerPath
        $code = Invoke-Freeze @('-Verb', 'Take', '-Scope', 'wear', '-Reason', 'watch sweep')
        if ($code -ne 0) { return "wear Take beside a foreign phone freeze expected exit 0, got $code" }
        $wear = Get-Content -LiteralPath $wearMarkerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($wear.scope -ne 'wear') { return "expected the wear marker to record scope=wear, got '$($wear.scope)'" }
        return $null
    }

    Assert-Case 'E29 a second session on the same scope exits 4' {
        Reset-WearFixture
        New-ForeignMarker -Kind live -Path $wearMarkerPath
        $code = Invoke-Freeze @('-Verb', 'Take', '-Scope', 'wear', '-Reason', 'mine')
        if ($code -ne 4) { return "expected exit 4, got $code" }
        return $null
    }

    Assert-Case 'E30 releasing the phone scope while the wear scope is held keeps the stop file and hands the flag over' {
        Reset-WearFixture
        $code = Invoke-Freeze @('-Verb', 'Take', '-Scope', 'phone', '-Reason', 'phone sweep')
        if ($code -ne 0) { return "Take expected exit 0, got $code" }
        if (-not (Test-Path -LiteralPath $stopPath)) { return 'the phone Take did not write the stop file' }
        New-ForeignMarker -Kind live -Path $wearMarkerPath
        $code = Invoke-Freeze @('-Verb', 'Release', '-Scope', 'phone')
        if ($code -ne 0) { return "Release expected exit 0, got $code" }
        if (-not (Test-Path -LiteralPath $stopPath)) { return 'the stop file was removed under a held wear freeze' }
        $wear = Get-Content -LiteralPath $wearMarkerPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if (-not $wear.stoppedQueue) { return 'the wear marker did not receive stoppedQueue=true' }
        Remove-Item -LiteralPath $wearMarkerPath -Force
        return $null
    }

    Assert-Case 'E31 the queue file carries both scope lines and removing one leaves the other' {
        Reset-WearFixture
        Set-QueueSeed
        [void](Invoke-Freeze @('-Verb', 'Take', '-Scope', 'phone', '-Reason', 'phone sweep'))
        New-ForeignMarker -Kind live -Path $wearMarkerPath
        [void](Invoke-Freeze @('-Verb', 'Status', '-Scope', 'wear'))
        if (@(Get-AnnounceLines).Count -ne 1) { return "expected one phone line, found $(@(Get-AnnounceLines).Count)" }
        if (@(Get-WearAnnounceLines).Count -ne 1) { return "expected one wear line, found $(@(Get-WearAnnounceLines).Count)" }
        if (@(Get-WearAnnounceLines)[0] -notlike '*wear sweep held by*') { return 'the wear line does not name its scope' }
        [void](Invoke-Freeze @('-Verb', 'Release', '-Scope', 'phone'))
        if (@(Get-AnnounceLines).Count -ne 0) { return 'the phone line survived its Release' }
        if (@(Get-WearAnnounceLines).Count -ne 1) { return 'the phone Release removed the wear line' }
        Remove-Item -LiteralPath $wearMarkerPath -Force
        [void](Invoke-Freeze @('-Verb', 'Status', '-Scope', 'wear'))
        if ((Get-QueueText) -cne $queueSeed) { return 'the queue differs from its seed after both scopes ended' }
        return $null
    }
}
finally {
    if ($null -eq $previousRoot) { Remove-Item Env:\FMS_REPO_ROOT -ErrorAction SilentlyContinue }
    else { $env:FMS_REPO_ROOT = $previousRoot }
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) {
    Write-Host "release-freeze tests: $(if ($failures -gt 0) { 'FAIL' } else { 'PASS' }) (run=$script:executed skipped=0)"
    exit 1
}
Write-Host "release-freeze tests: $(if ($failures -gt 0) { 'FAIL' } else { 'PASS' }) (run=$script:executed skipped=0)"
exit 0
