#requires -Version 7.0
<#
.SYNOPSIS
    Durable per-device registry for test devices - the last install mark and its history (S2855).

.DESCRIPTION
    The device lease (S1926) answers "who is driving this device right now" and is swept away when
    that session goes quiet. This registry answers the question no layer could answer before -
    "what was last installed on this device, when, and by whom" - and outlives every lease on
    purpose. The two stores share the key (the adb serial) and nothing else: releasing or sweeping
    a lease never touches a mark, and a mark never creates or extends a lease.

    The sanctioned install entry points (the `adb.ps1 install` verb and the noLegal debug
    installer) record the mark themselves, stamped with a `recordedBy` value naming the caller.
    Raw `adb install` stays unrecorded - the advisory principle covers accounting exactly as it
    covers locking (S1926 ADR-1) - and `-Verb Refresh` is how out-of-band installs are learned
    from a live device. A record is an advisory fact, never a permission:
    `docs/DEVICE_FLEET.md` stays the only authority for what a device permits.

    Store: one file per device under `temp/DEVICE.REGISTRY/<serial>.json` - the lease store's
    discipline (validated serial, ':' encoded for tcpip names, UTF-8 no-BOM JSON, write-then-rename
    updates) with the opposite lifecycle: nothing sweeps it. Read-only verbs never create the
    store directory; only Record, Refresh and Forget's target write touch it. This matters because
    the monitor page writer reads both stores and must not leave artifacts behind (S2406).

    Identity chain: session id from the shared lock identity, nickname via the canon identity
    resolver - never empty for a Record, so the park listing can name who did it.

.PARAMETER Verb
    Record  - upsert the install mark for -Id. Pushes the previous mark into the capped history.
    Get     - one device's record, or an absent answer. Read-only.
    List    - serials carrying a record or a live lease, merged by serial. Read-only.
    Status  - the park listing: serial, model/role, lease holder, last mark. Read-only.
    Refresh - read the installed version back from a live device, rewrite the mark.
    Forget  - remove the record. Refuses without -Yes.

.PARAMETER Id
    Device serial, as printed by `adb devices` (emulator-5554, RFCR110NBQJ, 192.168.1.5:5555).
    Required by every verb except List and Status.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/device-registry.ps1 -Verb Status
    What is on the park: role, who is driving it, what was installed last.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/device-registry.ps1 -Verb Record -Id emulator-5554 `
        -Package com.sza.fastmediasorter.debug -Module app_v2 -Flavor standard -BuildType debug `
        -VersionName 2.60.9100.101 -VersionCode 26100 -Artifact app-standard-debug.apk `
        -Ticket S2855 -RecordedBy 'adb.ps1 install'
    The shape the install hooks call.

.EXIT CODES
    0 - done: recorded, reported, refreshed, or nothing to report.
    1 - error: bad argument shape, unreadable store, adb missing for Refresh, Forget without -Yes.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Record', 'Get', 'List', 'Status', 'Refresh', 'Forget')]
    [string]$Verb,

    [string]$Id,

    [string]$Package,
    [string]$Module,
    [string]$Flavor,
    [string]$BuildType,
    [string]$VersionName,
    [string]$VersionCode,
    [string]$Artifact,

    # The ticket that drove the install, when the caller knows one; empty is honest.
    [string]$Ticket = '',

    # Which sanctioned entry point wrote this mark ('adb.ps1 install', the noLegal installer,
    # 'refresh'). Record stamps its default so a hand record is never mistaken for a hook's.
    [string]$RecordedBy = 'device-registry Record',

    # Optional identity text. The roster (`docs/DEVICE_FLEET.md`) is prose, so there is no
    # machine source for a role - the caller that knows one passes it, the monitor shows what is
    # there rather than inventing it.
    [string]$Model = '',
    [string]$Role = '',

    [switch]$Yes,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. (Join-Path $PSScriptRoot '..\utils\agent-lock.ps1')
. (Join-Path $PSScriptRoot '..\utils\agent-identity.ps1')
. (Join-Path $PSScriptRoot 'lib\find-adb.ps1')

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$registryDir = Join-Path $root 'temp\DEVICE.REGISTRY'
$leaseDir = Join-Path $root 'temp\DEVICE.LEASES'

# Same serial grammar and file-name encoding as the lease store (S1926): one key, one shape, so a
# serial found in either store is spelled identically in both.
$SerialPattern = '^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$'
$HistoryLimit = 5

$DebugPackage = 'com.sza.fastmediasorter.debug'
$BasePackage = 'com.sza.fastmediasorter'

function Test-SerialShape {
    param([Parameter(Mandatory)][string]$Serial)
    return ($Serial -match $SerialPattern)
}

function ConvertTo-RecordFileName {
    # ':' is legal in an adb serial (192.168.1.5:5555) and illegal in a Windows path segment.
    param([Parameter(Mandatory)][string]$Serial)
    return ($Serial -replace ':', '_')
}

function Get-RecordPath {
    param([Parameter(Mandatory)][string]$Serial)
    return (Join-Path $registryDir ((ConvertTo-RecordFileName -Serial $Serial) + '.json'))
}

function Read-Record {
    param([Parameter(Mandatory)][string]$Path)
    try { return (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json) }
    catch { return $null }
}

function Read-LeaseQuiet {
    # The registry only LOOKS at the lease store, read-only, and a missing or unreadable lease
    # costs its row's lease column - never the verb.
    param([Parameter(Mandatory)][string]$Path)
    try { return (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json) }
    catch { return $null }
}

function Get-LeaseLivenessQuiet {
    # Get-AgentTicketLiveness owns the liveness rule; the shim maps the lease's field names onto
    # it exactly as device-lease.ps1 does - a third liveness implementation is the drift S1621
    # banned.
    param([Parameter(Mandatory)]$Lease)
    $shim = [pscustomobject]@{
        sessionId      = $Lease.sessionId
        transcriptPath = $Lease.transcriptPath
        lastSeenAt     = $Lease.lastSeenAt
        enqueuedAt     = $Lease.claimedAt
    }
    try { return (Get-AgentTicketLiveness -Ticket $shim) } catch { return 'undetermined' }
}

function Write-RecordFile {
    # Write-then-rename: a reader that catches a half-written record treats it as unreadable, not
    # as garbage to sweep - the registry has no sweep and must never need one.
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Record
    )
    $text = ($Record | ConvertTo-Json -Depth 6 -Compress)
    $staging = "$Path.tmp-$PID"
    Set-Content -LiteralPath $staging -Value $text -Encoding utf8NoBOM -ErrorAction Stop
    Move-Item -LiteralPath $staging -Destination $Path -Force -ErrorAction Stop
}

function New-MarkObject {
    param([Parameter(Mandatory)][string]$SessionId, [string]$Nickname)
    return [ordered]@{
        package     = $Package
        module      = $Module
        flavor      = $Flavor
        buildType   = $BuildType
        versionName = $VersionName
        versionCode = $VersionCode
        artifact    = $Artifact
        installedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        bySession   = $SessionId
        byNickname  = $Nickname
        byTicket    = $Ticket
        recordedBy  = $RecordedBy
    }
}

function Invoke-ParkRows {
    # The merge the park listing and the monitor both want: one row per serial seen in either
    # store, lease column judged by the shared liveness rule. Read-only over both directories -
    # neither may be created here.
    $rows = [ordered]@{}
    if (Test-Path -LiteralPath $registryDir) {
        foreach ($file in (Get-ChildItem -LiteralPath $registryDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
            $record = Read-Record -Path $file.FullName
            if ($null -eq $record) { continue }
            $mark = $record.lastInstall
            $rows[[string]$record.id] = [pscustomobject]@{
                id        = [string]$record.id
                model     = [string]$record.model
                role      = [string]$record.role
                mark      = $mark
                updatedAt = $record.updatedAt
                lease     = $null
            }
        }
    }
    if (Test-Path -LiteralPath $leaseDir) {
        foreach ($file in (Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue)) {
            $lease = Read-LeaseQuiet -Path $file.FullName
            if ($null -eq $lease) { continue }
            $liveness = Get-LeaseLivenessQuiet -Lease $lease
            if ($liveness -eq 'foreign-stale' -or $liveness -eq 'undetermined') { continue }
            $ageMinutes = $null
            if ($lease.claimedAt) {
                $claimed = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$lease.claimedAt).LocalDateTime
                $ageMinutes = [math]::Round(((Get-Date) - $claimed).TotalMinutes, 1)
            }
            $sid = [string]$lease.id
            if (-not $rows.Contains($sid)) {
                $rows[$sid] = [pscustomobject]@{
                    id = $sid; model = ''; role = ''; mark = $null; updatedAt = $null; lease = $null
                }
            }
            $rows[$sid].lease = [pscustomobject]@{
                sessionId  = [string]$lease.sessionId
                host       = [string]$lease.host
                reason     = [string]$lease.reason
                claimedAt  = $lease.claimedAt
                ageMinutes = $ageMinutes
                liveness   = $liveness
                mine       = ($liveness -eq 'self')
            }
        }
    }
    return @($rows.Values | Sort-Object id)
}

function Format-MarkShort {
    param($Mark)
    if ($null -eq $Mark) { return 'no record' }
    $when = 'unknown'
    if ($Mark.installedAt) {
        $installed = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$Mark.installedAt).LocalDateTime
        $when = '{0:yyyy-MM-dd HH:mm}' -f $installed
    }
    $who = if ($Mark.byNickname) { $Mark.byNickname } elseif ($Mark.bySession) { [string]$Mark.bySession } else { 'unknown' }
    $variant = @(($Mark.flavor, $Mark.buildType) | Where-Object { $_ }) -join '/'
    return ('{0} {1} [{2}] by {3}, {4}' -f $Mark.package, $Mark.versionName, $variant, $who, $when)
}

function Assert-IdProvided {
    if ([string]::IsNullOrWhiteSpace($Id)) {
        Write-Error "device-registry: -Id is required for $Verb." -ErrorAction Continue
        exit 1
    }
    if (-not (Test-SerialShape -Serial $Id)) {
        Write-Error "device-registry: '$Id' is not a plausible adb serial." -ErrorAction Continue
        exit 1
    }
}

function Get-ThisIdentity {
    $sessionId = Get-AgentSessionId
    $nickname = $null
    try { $nickname = Get-AgentNickname -Id $sessionId -NoCreate } catch { $nickname = $null }
    return @{ SessionId = [string]$sessionId; Nickname = $(if ($nickname) { [string]$nickname } else { '' }) }
}

switch ($Verb) {

    'Record' {
        Assert-IdProvided
        if ([string]::IsNullOrWhiteSpace($Package)) {
            Write-Error 'device-registry: -Package is required for Record.' -ErrorAction Continue
            exit 1
        }
        if (-not (Test-Path -LiteralPath $registryDir)) {
            New-Item -ItemType Directory -Path $registryDir -Force | Out-Null
        }
        $identity = Get-ThisIdentity
        $path = Get-RecordPath -Serial $Id
        $record = Read-Record -Path $path

        $mark = New-MarkObject -SessionId $identity.SessionId -Nickname $identity.Nickname
        if ($null -ne $record) {
            if ([string]::IsNullOrWhiteSpace($Model) -and $record.model) { $Model = [string]$record.model }
            if ([string]::IsNullOrWhiteSpace($Role) -and $record.role) { $Role = [string]$record.role }
        }
        $history = @()
        if ($null -ne $record -and $null -ne $record.lastInstall) {
            $history = @($record.history)
            $history = @($record.lastInstall) + $history
            if ($history.Count -gt $HistoryLimit) { $history = @($history | Select-Object -First $HistoryLimit) }
        }
        elseif ($null -ne $record -and $null -ne $record.history) {
            $history = @($record.history)
        }

        $payload = [ordered]@{
            schema      = 1
            id          = $Id
            model       = $Model
            role        = $Role
            lastInstall = $mark
            history     = $history
            updatedAt   = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        Write-RecordFile -Path $path -Record ([pscustomobject]$payload)

        if ($Json) { [pscustomobject]@{ outcome = 'recorded'; id = $Id; package = $Package; versionName = $VersionName } | ConvertTo-Json -Compress }
        else { Write-Host "device-registry: recorded $Package $VersionName on $Id." -ForegroundColor Green }
        exit 0
    }

    'Get' {
        Assert-IdProvided
        $path = Get-RecordPath -Serial $Id
        if (-not (Test-Path -LiteralPath $path)) {
            if ($Json) { [pscustomobject]@{ outcome = 'absent'; id = $Id } | ConvertTo-Json -Compress }
            else { Write-Host "device-registry: $Id carries no record." -ForegroundColor DarkGray }
            exit 0
        }
        $record = Read-Record -Path $path
        if ($null -eq $record) {
            Write-Error "device-registry: the record for $Id exists but is unreadable." -ErrorAction Continue
            exit 1
        }
        if ($Json) { $record | ConvertTo-Json -Depth 6 -Compress; exit 0 }
        Write-Host ("  {0,-20} {1}" -f $Id, (Format-MarkShort -Mark $record.lastInstall))
        exit 0
    }

    'List' {
        $rows = @(Invoke-ParkRows)
        if ($Json) { $rows | ConvertTo-Json -Depth 6 -Compress -AsArray; exit 0 }
        $rows | ForEach-Object { Write-Output $_.id }
        exit 0
    }

    'Status' {
        $rows = @(Invoke-ParkRows)
        if ($Json) { $rows | ConvertTo-Json -Depth 6 -Compress -AsArray; exit 0 }
        if ($rows.Count -eq 0) {
            Write-Host 'device-registry: no record and no live lease - the park is unknown.' -ForegroundColor DarkGray
            exit 0
        }
        foreach ($row in $rows) {
            $leaseCell = 'free'
            if ($null -ne $row.lease) {
                $who = if ($row.lease.mine) { 'this session' } else { [string]$row.lease.sessionId }
                $age = if ($null -ne $row.lease.ageMinutes) { '{0} min' -f $row.lease.ageMinutes } else { 'unknown age' }
                $leaseCell = 'busy: {0} ({1}, {2})' -f $who, $row.lease.liveness, $age
            }
            $identityCell = @(($row.model, $row.role) | Where-Object { $_ }) -join ' / '
            Write-Host ("  {0,-20} {1,-24} {2,-46} {3}" -f $row.id, $identityCell, $leaseCell, (Format-MarkShort -Mark $row.mark))
        }
        exit 0
    }

    'Refresh' {
        Assert-IdProvided
        $adb = Find-Adb
        if (-not $adb) {
            Write-Error 'device-registry: adb not found (ANDROID_HOME, SDK path, PATH) - cannot Refresh.' -ErrorAction Continue
            exit 1
        }
        $found = $null
        foreach ($pkg in @($DebugPackage, $BasePackage)) {
            $raw = & $adb -s $Id shell dumpsys package $pkg 2>$null
            $text = ($raw -join "`n")
            if ($text -match 'versionName=([^\s]+)') {
                $version = $Matches[1]
                $code = $null
                if ($text -match 'versionCode=(\d+)') { $code = $Matches[1] }
                $found = [pscustomobject]@{ package = $pkg; versionName = $version; versionCode = $code; buildType = $(if ($pkg -eq $DebugPackage) { 'debug' } else { 'release' }) }
                break
            }
        }
        if ($null -eq $found) {
            Write-Error "device-registry: neither '$DebugPackage' nor '$BasePackage' answers on $Id - nothing to refresh." -ErrorAction Continue
            exit 1
        }
        $deviceModel = ''
        try {
            $deviceModel = ((& $adb -s $Id shell getprop ro.product.model 2>$null) -join '').Trim()
        }
        catch { $deviceModel = '' }

        $Package = [string]$found.package
        $VersionName = [string]$found.versionName
        $VersionCode = [string]$found.versionCode
        $BuildType = [string]$found.buildType
        if ([string]::IsNullOrWhiteSpace($Module)) { $Module = '' }
        if ([string]::IsNullOrWhiteSpace($Flavor)) { $Flavor = '' }
        if ([string]::IsNullOrWhiteSpace($Artifact)) { $Artifact = '' }
        $RecordedBy = 'refresh'
        if (-not [string]::IsNullOrWhiteSpace($deviceModel)) { $Model = $deviceModel }

        if (-not (Test-Path -LiteralPath $registryDir)) {
            New-Item -ItemType Directory -Path $registryDir -Force | Out-Null
        }
        $identity = Get-ThisIdentity
        $path = Get-RecordPath -Serial $Id
        $record = Read-Record -Path $path
        $mark = New-MarkObject -SessionId $identity.SessionId -Nickname $identity.Nickname
        $history = @()
        if ($null -ne $record -and $null -ne $record.lastInstall) {
            $history = @($record.history)
            $history = @($record.lastInstall) + $history
            if ($history.Count -gt $HistoryLimit) { $history = @($history | Select-Object -First $HistoryLimit) }
        }
        elseif ($null -ne $record -and $null -ne $record.history) {
            $history = @($record.history)
        }
        $payload = [ordered]@{
            schema      = 1
            id          = $Id
            model       = $Model
            role        = $Role
            lastInstall = $mark
            history     = $history
            updatedAt   = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        Write-RecordFile -Path $path -Record ([pscustomobject]$payload)

        if ($Json) { [pscustomobject]@{ outcome = 'refreshed'; id = $Id; package = $Package; versionName = $VersionName } | ConvertTo-Json -Compress }
        else { Write-Host "device-registry: refreshed $Package $VersionName from $Id." -ForegroundColor Green }
        exit 0
    }

    'Forget' {
        Assert-IdProvided
        if (-not $Yes) {
            Write-Error 'device-registry: Forget refuses without -Yes - it erases the install history for the device.' -ErrorAction Continue
            exit 1
        }
        $path = Get-RecordPath -Serial $Id
        if (-not (Test-Path -LiteralPath $path)) {
            if ($Json) { [pscustomobject]@{ outcome = 'absent'; id = $Id } | ConvertTo-Json -Compress }
            else { Write-Host "device-registry: $Id carries no record." -ForegroundColor DarkGray }
            exit 0
        }
        Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        if ($Json) { [pscustomobject]@{ outcome = 'forgotten'; id = $Id } | ConvertTo-Json -Compress }
        else { Write-Host "device-registry: forgot $Id." -ForegroundColor Green }
        exit 0
    }
}
