# Stub `adb` for scripts/devtest/device-ready.tests/Run-Tests.ps1 (S2600).
#
# Reached only through the sibling adb.cmd, which `Find-Adb` picks off PATH once the suite has
# blanked ANDROID_HOME and ANDROID_SDK_ROOT. Answers the handful of calls device-ready.ps1 makes,
# so the whole probe runs to its -Json branch with no device attached.
#
# DELIBERATELY NO param() BLOCK, for the reason recorded in adb.tests/stub/adb-stub.ps1: with one,
# PowerShell binds adb's own `-s <id>` as a parameter name and the call dies before reaching the
# table below.
#
# Per-case input comes from the environment, set by the suite before each child process:
#   FMS_STUB_DEVICES   comma-separated online device ids (default emulator-5554; empty = none)
#   FMS_STUB_WATCHES   comma-separated subset of those that report watch characteristics
#   FMS_STUB_PACKAGES  comma-separated installed package ids (default the debug id)
#   FMS_STUB_VERSION   versionName `dumpsys package` reports (default 9.9.9)
#
# Exit codes:
#   0  - the call matched the table
#   99 - the call matched nothing. Silence would turn any change in the probe's adb usage into a
#        silently green suite, which is the defect class this suite exists for (adb.tests ADR-3).

$ErrorActionPreference = 'Stop'

$call = @($args)

# The serial has to be read BEFORE the selector is dropped: unlike adb.ps1's stub, this one answers
# a per-device question (characteristics), so "which device" is part of the answer, not noise.
$serial = ''
if ($call.Count -ge 2 -and $call[0] -eq '-s') {
    $serial = $call[1]
    $call = if ($call.Count -gt 2) { $call[2..($call.Count - 1)] } else { @() }
}
$sig = ($call -join ' ')

$devices = if ($null -ne $env:FMS_STUB_DEVICES) { @($env:FMS_STUB_DEVICES -split ',' | Where-Object { $_ }) }
           else { @('emulator-5554') }
$watches = @($env:FMS_STUB_WATCHES -split ',' | Where-Object { $_ })
$packages = if ($null -ne $env:FMS_STUB_PACKAGES) { @($env:FMS_STUB_PACKAGES -split ',' | Where-Object { $_ }) }
            else { @('com.sza.fastmediasorter.debug') }
$version = if ($env:FMS_STUB_VERSION) { $env:FMS_STUB_VERSION } else { '9.9.9' }

switch -Regex ($sig) {

    '^start-server$' { exit 0 }

    '^devices$' {
        Write-Output 'List of devices attached'
        foreach ($d in $devices) { Write-Output "$d`tdevice" }
        exit 0
    }

    '^shell getprop ro\.build\.characteristics$' {
        # The real shapes, copied from the 2026-09-05 measurement recorded in S2600 section 6.1.
        Write-Output $(if ($watches -contains $serial) { 'emulator,nosdcard,watch' } else { 'emulator' })
        exit 0
    }

    '^shell pm list packages (?<pkg>\S+)$' {
        $wanted = $Matches['pkg']
        foreach ($p in $packages) {
            if ($p -eq $wanted) { Write-Output "package:$p" }
        }
        exit 0
    }

    '^shell dumpsys package \S+$' {
        Write-Output "    versionName=$version"
        exit 0
    }
}

Add-Content -LiteralPath (Join-Path $env:FMS_STUB_HOME 'stub-misses.txt') -Value "-s '$serial' $sig"
exit 99
