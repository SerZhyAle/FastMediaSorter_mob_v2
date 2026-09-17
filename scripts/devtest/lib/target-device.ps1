<#
.SYNOPSIS
  Shared target-device resolver for the builders that install what they just built (S3169).

.DESCRIPTION
  Dot-source this file to get two functions:
    . "$PSScriptRoot/../devtest/lib/target-device.ps1"
    $devs   = Get-OnlineAdbDevices -Adb $adb                          # @('RFCR110NBQJ', '192.168.1.166:46551')
    $serial = Resolve-TargetDevice -Adb $adb -DeviceId $DeviceId      # one id, or it throws

  WHY IT EXISTS. Six builders under scripts/builders/ called `adb` with no `-s` and read no exit
  code. With a phone and a watch both online - the workstation's normal state, since the watch stays
  paired over Wi-Fi debugging - every step printed `more than one device/emulator` and the script
  still ended with "launched successfully!" and exit 0. The next step of a scenario then exercised
  the OLD APK believing it was the new one, which is worse than a plain failure.

  WHY A LIBRARY AND NOT A COPY. scripts/builders/install-standard-debug-to-device.ps1 and its
  noLegal sibling already carried this selection inline, so the tree held two copies before this
  file and would have held eight after. The classification half is not re-derived either: it calls
  Get-DeviceFormFactor from lib/device-form-factor.ps1, the signal adb.ps1 itself resolves a paired
  Galaxy Watch by.

  IT THROWS RATHER THAN RETURNING $null. Every caller installs an APK immediately afterwards, and a
  silent null is exactly the shape of failure this ticket removes - the caller would carry on with
  an empty `-s` argument and land back on adb's own device guess.
#>

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. "$PSScriptRoot\device-form-factor.ps1"

function Get-OnlineAdbDevices {
    param(
        [Parameter(Mandatory)][string]$Adb
    )
    $raw = & $Adb devices 2>$null
    $lines = ($raw -join "`n") -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^\s*List of devices' }
    # Split on the TAB adb prints between id and state, never on whitespace: an mDNS service name
    # can contain a space, and splitting on it hid a wirelessly paired watch entirely (adb.ps1
    # Get-OnlineDevices carries the same note and the same measurement).
    $devices = foreach ($line in $lines) {
        $parts = ($line -split "`t", 2) | Where-Object { $_ }
        if ($parts.Count -ge 2 -and $parts[1].Trim() -eq 'device') { $parts[0].Trim() }
    }
    return @($devices)
}

function Resolve-TargetDevice {
    param(
        [Parameter(Mandatory)][string]$Adb,
        [string]$DeviceId,
        [ValidateSet('app_v2', 'wear')][string]$Module = 'app_v2'
    )
    # @() guards the single-device case: a one-element array returned from a function unwraps to a
    # scalar string on assignment, and indexing it would then walk characters.
    $devices = @(Get-OnlineAdbDevices -Adb $Adb)
    if ($devices.Count -eq 0) {
        throw "No online device. Connect a phone or boot an emulator, then re-run."
    }
    if ($DeviceId) {
        if ($devices -notcontains $DeviceId) {
            throw "Device '$DeviceId' is not online (online: $($devices -join ', '))."
        }
        return $DeviceId
    }
    if ($devices.Count -eq 1) { return $devices[0] }

    # Several devices and none named: the watch is distinguishable without asking, so a phone build
    # with one phone and one watch online resolves instead of refusing. Anything less clear-cut
    # refuses and names every id, because a guess here installs to the wrong device.
    $matched = @($devices | Where-Object {
            Test-FormFactorMatch -FormFactor (Get-DeviceFormFactor -Adb $Adb -Serial $_) -Module $Module
        })
    if ($matched.Count -eq 1) { return $matched[0] }
    throw "$($devices.Count) devices online ($($devices -join ', ')) and no -DeviceId given; $($matched.Count) match -Module $Module. Pass -DeviceId <serial> or set ANDROID_SERIAL."
}
