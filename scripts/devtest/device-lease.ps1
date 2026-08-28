<#
.SYNOPSIS
    Device leases for parallel agent sessions (S1926).

.DESCRIPTION
    A lease marks one attached device as being driven by one agent session, so a sibling session
    picking a device is offered a different one instead of installing an APK or switching HOME
    out from under a running scenario.

    Ticket leases, BUILD.LOCK and CODE.LOCK already arbitrate between concurrent sessions; devices
    did not. `adb devices` reports an emulator as online whether or not somebody is mid-run on it,
    so the conflict was discovered by breaking something - observed 2026-08-21 during S1895, where
    emulator-5556 was online, had the debug package installed, and was showing a sibling session's
    PlayerActivity.

    This is deliberately the ticket lease's shape, not the build lock's (S1926 ADR-1): one file per
    lease under temp/DEVICE.LEASES/<serial>.json, the claim is an atomic file creation rather than
    a check followed by a write, and losing a claim is a normal outcome (exit 3) meaning "take a
    different device". There is NO queue: a device is held for as long as somebody's scenario runs,
    which is unbounded, so waiting is worse than deferring the device stage.

    Ownership is a session, not a process, so liveness is the write time of that session's
    transcript. That rule is NOT restated here - Get-AgentTicketLiveness in
    scripts/utils/agent-lock.ps1 owns it, and a third copy would drift from the two that exist.
    Timings come from $Script:AgentLockTimings.Device.

    A stale lease is swept by whoever reads next - List, Status and Claim all sweep first. There is
    no watchdog process, matching the queue design in S1432.

    ADVISORY, not enforcing: this coordinates consenting callers. It does not and cannot stop a raw
    `adb` command, exactly as BUILD.LOCK does not stop a raw `gradlew`.

.PARAMETER Verb
    Claim   - take the device. Atomic; idempotent for a lease this session already owns.
    Release - give it back. Owner-checked: a live foreign lease is refused.
    List    - serials only, one per line (or a JSON array under -Json). Feeds an exclusion list.
    Status  - human-readable holder map: device, session, host, last seen, reason.
    Sweep   - drop stale leases and report how many went.

.PARAMETER Id
    Device serial, as printed by `adb devices` (emulator-5554, RFCR110NBQJ, 192.168.1.5:5555).
    Required for Claim and Release.

.PARAMETER Reason
    Free text recorded in the lease, shown by Status. Defaults to the calling skill's name.

.PARAMETER Json
    Emit machine-readable output instead of console text.

.PARAMETER StaleMinutes
    Liveness window. Defaults to $Script:AgentLockTimings.Device.SessionStaleMinutes.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Claim -Id emulator-5554 -Reason "/spec-test-device S1234"
    Takes emulator-5554. Exit 0 on success, exit 3 when a live sibling got there first.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Status
    Prints which session holds which device and how long ago each was last seen.

.EXIT CODES
    0 - done: claimed, released, or reported.
    1 - error: unreadable store, bad argument shape, write failure.
    3 - claim lost: a live foreign session already holds this device.
    4 - release refused: a live foreign session owns this lease.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Claim', 'Release', 'List', 'Status', 'Sweep')]
    [string]$Verb,

    [string]$Id,

    [string]$Reason = 'device-lease',

    [switch]$Json,

    [int]$StaleMinutes = 0
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. (Join-Path $PSScriptRoot '..\utils\agent-lock.ps1')

$timings = Get-AgentLockTimings -Name Device
if ($StaleMinutes -le 0) { $StaleMinutes = $timings.SessionStaleMinutes }

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$leaseDir = Join-Path $root 'temp\DEVICE.LEASES'
if (-not (Test-Path -LiteralPath $leaseDir)) {
    New-Item -ItemType Directory -Path $leaseDir -Force | Out-Null
}

# An adb serial becomes a file name, so anything that could climb out of the lease directory is
# refused before it is used as a path. A tcpip serial carries a colon, which is legal in the set
# below but illegal in a Windows file name, so it is encoded rather than rejected.
$SerialPattern = '^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$'

function Test-SerialShape {
    param([Parameter(Mandatory)][string]$Serial)
    return ($Serial -match $SerialPattern)
}

function ConvertTo-LeaseFileName {
    # ':' is legal in an adb serial (192.168.1.5:5555) and illegal in a Windows path segment.
    param([Parameter(Mandatory)][string]$Serial)
    return ($Serial -replace ':', '_')
}

function Get-LeasePath {
    param([Parameter(Mandatory)][string]$Serial)
    return (Join-Path $leaseDir ((ConvertTo-LeaseFileName -Serial $Serial) + '.json'))
}

function Read-Lease {
    param([Parameter(Mandatory)][string]$Path)
    try { return (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json) }
    catch { return $null }
}

function Get-LeaseAgeMinutes {
    param([Parameter(Mandatory)]$Lease)
    if (-not $Lease.claimedAt) { return [double]::MaxValue }
    $claimed = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Lease.claimedAt).LocalDateTime
    return ((Get-Date) - $claimed).TotalMinutes
}

function Get-LeaseLiveness {
    # Get-AgentTicketLiveness falls back to $Ticket.enqueuedAt when the transcript is unreachable.
    # The lease's own field is claimedAt, so shim it across rather than storing the value twice.
    #
    # The ticket lease additionally treats "owner holds CODE.LOCK naming this ticket" as proof of
    # life (S1448). There is no device equivalent: a lock reason names a ticket, never a serial, so
    # that signal is absent here rather than approximated.
    param([Parameter(Mandatory)]$Lease)
    $shim = [pscustomobject]@{
        sessionId      = $Lease.sessionId
        transcriptPath = $Lease.transcriptPath
        lastSeenAt     = $Lease.lastSeenAt
        enqueuedAt     = $Lease.claimedAt
    }
    return (Get-AgentTicketLiveness -Ticket $shim -StaleMinutes $StaleMinutes)
}

function Invoke-LeaseSweep {
    <#
        Drops a lease whose owning session has gone quiet, or which passed the absolute ceiling
        regardless of liveness - the case where a transcript keeps being written but the device was
        abandoned. Never drops on 'undetermined': that means WE have no session id, so "mine" and
        "theirs" are indistinguishable and eviction would be a guess.
    #>
    $removed = @()
    foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $lease = Read-Lease -Path $file.FullName
        if ($null -eq $lease) {
            # Grace window against catching a file mid-write: a reader arriving between create and
            # write would otherwise delete a valid lease.
            $ageSeconds = ((Get-Date) - $file.LastWriteTime).TotalSeconds
            if ($ageSeconds -gt 60) {
                Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
                $removed += $file.BaseName
            }
            continue
        }
        $liveness = Get-LeaseLiveness -Lease $lease
        if ($liveness -eq 'undetermined') { continue }
        $ageMinutes = Get-LeaseAgeMinutes -Lease $lease
        if ($liveness -eq 'foreign-stale' -or $ageMinutes -gt $timings.TicketCeilingMinutes) {
            Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
            $removed += [string]$lease.id
        }
    }
    return $removed
}

function Get-LiveLeases {
    $out = @()
    foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $lease = Read-Lease -Path $file.FullName
        if ($null -eq $lease) { continue }
        $liveness = Get-LeaseLiveness -Lease $lease
        $lastSeen = $null
        if (-not [string]::IsNullOrWhiteSpace([string]$lease.transcriptPath) -and
            (Test-Path -LiteralPath ([string]$lease.transcriptPath))) {
            try { $lastSeen = (Get-Item -LiteralPath ([string]$lease.transcriptPath)).LastWriteTime } catch { $lastSeen = $null }
        }
        $lastSeenMinutes = if ($lastSeen) { [math]::Round(((Get-Date) - $lastSeen).TotalMinutes, 1) } else { $null }
        $out += [pscustomobject]@{
            id              = [string]$lease.id
            sessionId       = [string]$lease.sessionId
            host            = [string]$lease.host
            pid             = $lease.pid
            reason          = [string]$lease.reason
            claimedAt       = $lease.claimedAt
            ageMinutes      = [math]::Round((Get-LeaseAgeMinutes -Lease $lease), 1)
            lastSeenMinutes = $lastSeenMinutes
            liveness        = $liveness
            mine            = ($liveness -eq 'self')
        }
    }
    return ($out | Sort-Object id)
}

function Write-LeaseFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$SessionId
    )
    $payload = [ordered]@{
        schema         = 1
        id             = $Serial
        sessionId      = $SessionId
        host           = $env:COMPUTERNAME
        pid            = $PID
        reason         = $Reason
        claimedAt      = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        # Refreshed by Update-LeaseHeartbeat on every verb this session runs against its own lease.
        # claimedAt stays frozen so the ceiling cannot be extended by activity.
        lastSeenAt     = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        transcriptPath = (Get-AgentSessionTranscriptPath -SessionId $SessionId)
    }
    $text = ($payload | ConvertTo-Json -Depth 4 -Compress)
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    # CreateNew makes "does it exist" and "create it" one filesystem call, so two sessions racing
    # for one device cannot both win. The IOException below is the loser, not a fault.
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        $bytes = $utf8NoBom.GetBytes($text)
        $stream.Write($bytes, 0, $bytes.Length)
    }
    finally { $stream.Dispose() }
}

function Update-LeaseHeartbeat {
    <#
        Refresh lastSeenAt on every lease this session owns, so a session that is plainly active -
        it just ran a lease verb - is never judged gone. Best-effort and write-then-rename: a reader
        that caught a half-written lease would treat it as unreadable.
    #>
    param([Parameter(Mandatory)][string]$SessionId)

    foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        $lease = Read-Lease -Path $file.FullName
        if ($null -eq $lease -or [string]$lease.sessionId -ne $SessionId) { continue }
        try {
            $lease | Add-Member -NotePropertyName 'lastSeenAt' -NotePropertyValue ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) -Force
            $staging = "$($file.FullName).tmp-$PID"
            Set-Content -LiteralPath $staging -Value ($lease | ConvertTo-Json -Depth 4 -Compress) -Encoding utf8NoBOM -ErrorAction Stop
            Move-Item -LiteralPath $staging -Destination $file.FullName -Force -ErrorAction Stop
        }
        catch {
            # A missed refresh costs a delay, never a stuck device - the next verb retries.
        }
    }
}

$sessionId = Get-AgentSessionId
Update-LeaseHeartbeat -SessionId $sessionId

switch ($Verb) {

    'Claim' {
        if ([string]::IsNullOrWhiteSpace($Id)) {
            Write-Error 'device-lease: -Id is required for Claim.' -ErrorAction Continue
            exit 1
        }
        if (-not (Test-SerialShape -Serial $Id)) {
            Write-Error "device-lease: '$Id' is not a plausible adb serial." -ErrorAction Continue
            exit 1
        }
        [void](Invoke-LeaseSweep)
        $path = Get-LeasePath -Serial $Id

        if (Test-Path -LiteralPath $path) {
            $existing = Read-Lease -Path $path
            if ($null -ne $existing -and [string]$existing.sessionId -eq $sessionId) {
                # Re-claiming our own lease is a no-op, so a resumed run does not fight itself.
                if ($Json) { [pscustomobject]@{ outcome = 'already-mine'; id = $Id; sessionId = $sessionId } | ConvertTo-Json -Compress }
                else { Write-Host "device-lease: $Id already held by this session." -ForegroundColor DarkGray }
                exit 0
            }
        }

        try {
            Write-LeaseFile -Path $path -Serial $Id -SessionId $sessionId
        }
        catch [System.IO.IOException] {
            $holder = Read-Lease -Path $path
            $holderId = if ($holder) { [string]$holder.sessionId } else { 'unknown' }
            $holderHost = if ($holder) { [string]$holder.host } else { 'unknown' }
            if ($Json) {
                [pscustomobject]@{ outcome = 'claim-lost'; id = $Id; heldBy = $holderId; host = $holderHost } | ConvertTo-Json -Compress
            }
            else {
                Write-Host "device-lease: $Id already claimed by session $holderId on $holderHost." -ForegroundColor Yellow
            }
            exit 3
        }

        if ($Json) { [pscustomobject]@{ outcome = 'claimed'; id = $Id; sessionId = $sessionId } | ConvertTo-Json -Compress }
        else { Write-Host "device-lease: claimed $Id (session $sessionId)." -ForegroundColor Green }
        exit 0
    }

    'Release' {
        if ([string]::IsNullOrWhiteSpace($Id)) {
            Write-Error 'device-lease: -Id is required for Release.' -ErrorAction Continue
            exit 1
        }
        if (-not (Test-SerialShape -Serial $Id)) {
            Write-Error "device-lease: '$Id' is not a plausible adb serial." -ErrorAction Continue
            exit 1
        }
        $path = Get-LeasePath -Serial $Id
        if (-not (Test-Path -LiteralPath $path)) {
            if ($Json) { [pscustomobject]@{ outcome = 'absent'; id = $Id } | ConvertTo-Json -Compress }
            else { Write-Host "device-lease: $Id holds no lease." -ForegroundColor DarkGray }
            exit 0
        }

        $lease = Read-Lease -Path $path
        $liveness = if ($null -ne $lease) { Get-LeaseLiveness -Lease $lease } else { 'foreign-stale' }
        if ($liveness -eq 'foreign-live') {
            $holderId = [string]$lease.sessionId
            if ($Json) { [pscustomobject]@{ outcome = 'release-refused'; id = $Id; heldBy = $holderId } | ConvertTo-Json -Compress }
            else { Write-Host "device-lease: refusing to release $Id - live session $holderId owns it." -ForegroundColor Yellow }
            exit 4
        }

        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        if ($Json) { [pscustomobject]@{ outcome = 'released'; id = $Id } | ConvertTo-Json -Compress }
        else { Write-Host "device-lease: released $Id." -ForegroundColor Green }
        exit 0
    }

    'List' {
        [void](Invoke-LeaseSweep)
        $leases = @(Get-LiveLeases)
        if ($Json) { ($leases | ForEach-Object { $_.id }) | ConvertTo-Json -Compress -AsArray }
        else { $leases | ForEach-Object { Write-Output $_.id } }
        exit 0
    }

    'Status' {
        [void](Invoke-LeaseSweep)
        $leases = @(Get-LiveLeases)
        if ($Json) { $leases | ConvertTo-Json -Depth 4 -Compress -AsArray; exit 0 }
        if ($leases.Count -eq 0) {
            Write-Host 'device-lease: no device is leased.' -ForegroundColor DarkGray
            exit 0
        }
        foreach ($lease in $leases) {
            $who = if ($lease.mine) { 'this session' } else { $lease.sessionId }
            $seen = if ($null -ne $lease.lastSeenMinutes) { "{0} min ago" -f $lease.lastSeenMinutes } else { 'unknown' }
            Write-Host ("  {0,-20} {1,-14} host {2,-10} seen {3,-14} {4}" -f $lease.id, $who, $lease.host, $seen, $lease.reason)
        }
        exit 0
    }

    'Sweep' {
        $removed = @(Invoke-LeaseSweep)
        if ($Json) { [pscustomobject]@{ outcome = 'swept'; removed = $removed } | ConvertTo-Json -Depth 3 -Compress }
        elseif ($removed.Count -eq 0) { Write-Host 'device-lease: nothing to sweep.' -ForegroundColor DarkGray }
        else { Write-Host ("device-lease: swept {0} stale lease(s): {1}" -f $removed.Count, ($removed -join ', ')) -ForegroundColor Green }
        exit 0
    }
}
