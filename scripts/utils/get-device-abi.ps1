#requires -Version 7.0
<#
.SYNOPSIS
    Reads the primary ABI of a connected device, so an installer can ask for the architecture in
    front of it instead of for whatever artifact happens to be first.

.DESCRIPTION
    Dot-source this file to get Get-TargetDeviceAbi. No top-level side effects, no exit, no
    preference variables assigned.

    The value comes from `ro.product.cpu.abi`, which is the ABI the device actually runs - an
    arm64-v8a phone and an x86_64 emulator differ here, and that difference is the whole point of
    the ABI splits S1972 turns on: once a debug build emits several APKs, "install the newest file"
    puts an unrunnable architecture on the device and says nothing.

    Best-effort by construction: every failure path - adb missing, no device, an offline device, an
    empty property - returns $null rather than throwing. A caller then resolves without an ABI,
    which is exactly the pre-S1972 behaviour, so a probe that cannot answer never blocks an install
    that would otherwise have worked.

.PARAMETER Adb
    Path to the adb executable. Omit it to have the function locate adb itself through
    scripts/devtest/lib/find-adb.ps1 - adb is not on PATH on this machine, so a caller that has no
    adb path of its own must not pass the bare name and hope.

.PARAMETER DeviceId
    Serial of the target device. Omit it when exactly one device is connected.

.PARAMETER Fallback
    ABI to return when the device cannot answer. Without it the function returns $null, which asks
    the resolver for "whatever this build emitted" - correct for a caller that will accept any
    artifact, wrong for one that must name a slice of a split build. A debug builder passes
    'arm64-v8a' here, because this repo routinely has a phone, a watch and an emulator online at
    once (S1986), and with more than one attached adb refuses the property read outright.

.EXAMPLE
    . "$PSScriptRoot/../utils/get-device-abi.ps1"
    $abi = Get-TargetDeviceAbi -Adb $adb -DeviceId $DeviceId
    $apk = Find-BuildArtifact -Dir $apkDir -Abi $abi

.NOTES
    Exit codes: none. This file defines a function and returns nothing; it is dot-sourced, never
    invoked as a script.
#>

function Get-TargetDeviceAbi {
    <#
    .SYNOPSIS
        Primary ABI of the connected device, or the caller's fallback when it cannot be read.
    .OUTPUTS
        String such as 'arm64-v8a' or 'x86_64'; the -Fallback value (default $null) when the device
        cannot answer. Never throws.
    #>
    [CmdletBinding()]
    param(
        [string] $Adb,
        [string] $DeviceId,
        [string] $Fallback
    )

    try {
        if (-not $Adb) {
            . "$PSScriptRoot/../devtest/lib/find-adb.ps1"
            $Adb = Find-Adb
            if (-not $Adb) { return $Fallback }
        }

        $adbArgs = @()
        if ($DeviceId) { $adbArgs += @('-s', $DeviceId) }
        $adbArgs += @('shell', 'getprop', 'ro.product.cpu.abi')

        $raw = (& $Adb @adbArgs 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $raw) { return $Fallback }

        # A property read that reached no device still prints diagnostics on stdout in some adb
        # builds, so accept only something shaped like an ABI name.
        if ($raw -notmatch '^[a-z0-9_\-]+$') { return $Fallback }
        return $raw
    }
    catch {
        return $Fallback
    }
}
