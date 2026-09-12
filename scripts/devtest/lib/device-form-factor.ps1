<#
.SYNOPSIS
  Shared phone-vs-watch signal for the device scripts (S2600), extracted the way
  lib/find-adb.ps1 was (S1341) so a caller reads the form factor instead of guessing it.

.DESCRIPTION
  Dot-source this file to get two functions:
    . "$PSScriptRoot/lib/device-form-factor.ps1"
    $ff = Get-DeviceFormFactor -Adb $adb -Serial 'emulator-5554'   # 'watch' | 'phone'
    Test-FormFactorMatch -FormFactor $ff -Module 'wear'            # $true | $false

  The signal is `ro.build.characteristics`, which carries a `watch` element on a Wear OS
  build. Two rejected alternatives, both measured 2026-09-05 on the pair of emulators that
  produced the S2600 incident (watch: 'emulator,nosdcard,watch' / sdk_gwear_x86_64;
  phone: 'emulator' / sdk_gphone64_x86_64):
    - the AVD name (`sdk_gwear_*`) exists only on an emulator, so it breaks on the real
      Galaxy Watch 7 - precisely where the answer matters;
    - `pm list features android.hardware.type.watch` is correct but ships the device's whole
      feature list back for one line, and no caller in this repo uses it.
  `ro.build.characteristics` is the signal already proven on real hardware: adb.ps1's
  install-time module guard (S2043) resolves a phone and a paired Galaxy Watch 7 by it,
  wireless-adb duplicates included.

  Why 'phone' and not an error when the device cannot be reached: this is a CLASSIFIER, not a
  liveness probe. Its callers already fail on an offline device with their own, better message,
  and a classifier that throws would convert "the device dropped" into "the form factor is
  unknowable" one frame before the real check reports it properly.
#>

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# Anchored on the element boundary, not a bare substring: `-match 'watch'` would also accept a
# hypothetical 'watchdog' characteristic. This is the stricter of the two shapes already in the
# tree (wear-prerelease-prepare.ps1 line 133); adb.ps1 line 472 carries the loose one.
$script:FmsWatchCharacteristicPattern = '(^|,)\s*watch\s*($|,)'

function Get-DeviceFormFactor {
    param(
        [Parameter(Mandatory)][string]$Adb,
        [Parameter(Mandatory)][string]$Serial
    )
    $chars = ''
    try {
        $chars = (& $Adb -s $Serial shell getprop ro.build.characteristics 2>$null) -join ''
    }
    catch {
        # Unreachable device - see the .DESCRIPTION note on why this classifies rather than throws.
        $chars = ''
    }
    if ($chars -match $script:FmsWatchCharacteristicPattern) { return 'watch' }
    return 'phone'
}

function Test-FormFactorMatch {
    param(
        [Parameter(Mandatory)][string]$FormFactor,
        [Parameter(Mandatory)][string]$Module
    )
    $wantWatch = ($Module -eq 'wear')
    return (($FormFactor -eq 'watch') -eq $wantWatch)
}
