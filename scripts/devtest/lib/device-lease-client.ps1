[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)

function Find-SharedDeviceLease {
    $tool = 'devices/device-lease.ps1'
    if ($env:SZA_HARNESS_ROOT) {
        $candidate = Join-Path $env:SZA_HARNESS_ROOT $tool
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }

    $userDir = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
    if (-not $userDir) { return }
    $cache = Join-Path $userDir '.claude/plugins/cache/sza-unified-rules/sza'
    $versions = Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue | Sort-Object -Descending {
        $version = $null
        if ([version]::TryParse($_.Name, [ref]$version)) { $version } else { [version]'0.0' }
    }
    foreach ($entry in $versions) {
        $candidate = Join-Path $entry.FullName "tools/harness/$tool"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
}

function Get-FmsDeviceLeaseRows {
    # Monitoring must never sweep another project's leases or refresh this session's lease.
    $shared = Find-SharedDeviceLease
    if ($shared) {
        $rows = & {
            . (Join-Path (Split-Path -Parent $shared) '_device-lease.ps1')
            Get-DeviceLeases
        }
    }
    else {
        $local = Join-Path $PSScriptRoot 'device-lease-local.ps1'
        $json = & $local -Verb Status -Json -ReadOnly
        if ($LASTEXITCODE -ne 0) { throw "Local device lease read failed (exit $LASTEXITCODE)." }
        $rows = $json | ConvertFrom-Json
    }
    return @($rows | Where-Object { $_.liveness -ne 'foreign-stale' })
}

function Get-FmsAvdName {
    param([Parameter(Mandatory)][string]$Adb, [Parameter(Mandatory)][string]$Serial)
    $response = @(& $Adb -s $Serial emu avd name 2>$null)
    $firstLine = $response | Select-Object -First 1
    if ($LASTEXITCODE -eq 0 -and "$firstLine".Trim() -cmatch '^[A-Za-z0-9._-]{1,92}$') {
        return "$firstLine".Trim()
    }
}

function Get-FmsHardwareSerial {
    param([Parameter(Mandatory)][string]$Adb, [Parameter(Mandatory)][string]$Serial)
    foreach ($property in @('ro.serialno', 'ro.boot.serialno')) {
        $value = (& $Adb -s $Serial shell getprop $property 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $value -match '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$' -and $value -ne 'unknown') {
            return $value
        }
    }
}

function Get-FmsDeviceLeaseKeys {
    param(
        [Parameter(Mandatory)][string]$Adb,
        [Parameter(Mandatory)][string]$Serial,
        [string[]]$OnlineSerials = @()
    )
    if ($Serial -match '^emulator-\d+$') {
        $avd = Get-FmsAvdName -Adb $Adb -Serial $Serial
        if ($avd) { return "avd:$avd" }
        return
    }

    # Include the USB identity and current aliases so different transports contend for one device.
    $keys = @($Serial)
    $hardware = if ($Serial -notmatch '[:.]' -and $Serial -notlike 'adb-*') {
        $Serial
    } else {
        Get-FmsHardwareSerial -Adb $Adb -Serial $Serial
    }
    if ($hardware) {
        $keys += $hardware
        foreach ($alias in $OnlineSerials) {
            if ($alias -in $keys -or $alias -match '^emulator-\d+$') { continue }
            if ((Get-FmsHardwareSerial -Adb $Adb -Serial $alias) -eq $hardware) { $keys += $alias }
        }
    }
    return @($keys | Where-Object { $_ -cmatch '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$' } | Select-Object -Unique)
}

function Get-FmsUnleasedDeviceSerials {
    param(
        [Parameter(Mandatory)][string]$Adb,
        [Parameter(Mandatory)][string[]]$Serials
    )
    $foreignKeys = @(Get-FmsDeviceLeaseRows | Where-Object { -not $_.mine } | ForEach-Object { $_.id })
    if ($foreignKeys.Count -eq 0) { return $Serials }

    foreach ($serial in $Serials) {
        $keys = @(Get-FmsDeviceLeaseKeys -Adb $Adb -Serial $serial -OnlineSerials $Serials)
        if ($keys.Count -gt 0 -and @($keys | Where-Object { $_ -in $foreignKeys }).Count -eq 0) { $serial }
    }
}

function Request-FmsDeviceLease {
    param([Parameter(Mandatory)][string[]]$Keys, [Parameter(Mandatory)][string]$Reason)
    $leaseCli = Join-Path $PSScriptRoot '../device-lease.ps1'
    $alreadyOwned = @(Get-FmsDeviceLeaseRows | Where-Object { $_.mine } | ForEach-Object { $_.id })
    $json = & $leaseCli -Verb Claim -Id $Keys -Reason $Reason -Json
    $code = $LASTEXITCODE
    if ($code -eq 3) { return [pscustomobject]@{ Acquired = $false; NewKeys = @() } }
    if ($code -ne 0) { throw "Device lease claim failed (exit $code). $json" }
    $result = $json | ConvertFrom-Json
    $newKeys = if ($result.outcome -eq 'claimed') { @($Keys | Where-Object { $_ -notin $alreadyOwned }) }
    return [pscustomobject]@{ Acquired = $true; NewKeys = @($newKeys | Where-Object { $_ }) }
}

function Release-FmsDeviceLease {
    param([string[]]$Keys)
    if (-not $Keys) { return }
    $leaseCli = Join-Path $PSScriptRoot '../device-lease.ps1'
    & $leaseCli -Verb Release -Id $Keys -Json | Out-Null
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine("Device lease release failed (exit $LASTEXITCODE).")
    }
}
