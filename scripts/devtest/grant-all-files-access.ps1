<#
.SYNOPSIS
  S2713 - grant MANAGE_EXTERNAL_STORAGE to a debug build and PROVE the grant took.

.DESCRIPTION
  `appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` succeeds silently against a package that does
  not declare the permission, so the caller cannot tell "granted" from "did nothing". S2012 dropped
  the declaration from every store flavor and left it only in `app_v2/src/noLegal/AndroidManifest.xml`,
  which is exactly the build the pre-release sweep installs (`standard` debug) - so on that build the
  command has never done anything, on any device, and the sweep read the silence as success.

  This script separates the three outcomes the caller must react to differently:
    - the package declares the permission and the appop reads back `allow`   -> granted
    - the package does not declare it (normal for a store flavor after S2012) -> not-declared
    - the package declares it and the appop reads back something else         -> refused (device policy)

  The read-back is the whole point: only it distinguishes the last case from the first.

  Exit codes:
    0 - granted; `appops get` reads back `allow`
    1 - refused; the permission is declared but the appop did not take (value quoted in the output)
    2 - cannot verify: adb missing, no online device, several devices and no -DeviceId, or the
        package is not installed
    3 - not-declared; this build does not request MANAGE_EXTERNAL_STORAGE, so there is nothing to
        grant through any channel

.PARAMETER DeviceId
  Specific adb device id. Required when several devices are online.

.PARAMETER Package
  Package to grant to. Defaults to the standard debug application id.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/grant-all-files-access.ps1 -DeviceId RFCR110NBQJ -Json
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string]$Package = 'com.sza.fastmediasorter.debug',
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$result = [ordered]@{
    outcome  = 'unknown'
    package  = $Package
    device   = $null
    appop    = $null
    detail   = $null
    exitCode = 2
}

function Complete-Run {
    param([int]$Code, [string]$Outcome, [string]$Detail)
    $script:result.outcome  = $Outcome
    $script:result.detail   = $Detail
    $script:result.exitCode = $Code
    if ($Json) {
        $script:result | ConvertTo-Json -Depth 4 -Compress
    } else {
        Write-Host ("ALL-FILES-ACCESS {0} - package={1} device={2} exit={3}" -f `
            $Outcome.ToUpperInvariant(), $script:result.package, $script:result.device, $Code)
        Write-Host ("  {0}" -f $Detail)
    }
    exit $Code
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repoRoot 'scripts/devtest/lib/find-adb.ps1')
$adb = Find-Adb
if (-not $adb) { Complete-Run 2 'no-adb' 'adb not found; install platform-tools or set ANDROID_HOME' }

if ($DeviceId) {
    $target = $DeviceId
} else {
    $online = @(& $adb devices 2>$null | Select-Object -Skip 1 |
        Where-Object { $_ -match '^\S+\s+device\s*$' } |
        ForEach-Object { ($_ -split '\s+')[0] })
    if ($online.Count -eq 0) { Complete-Run 2 'no-device' 'no online device' }
    if ($online.Count -gt 1) {
        Complete-Run 2 'many-devices' ("{0} devices online; pass -DeviceId" -f $online.Count)
    }
    $target = $online[0]
}
$result.device = $target

# Presence is asked of `pm list packages`, not of the dump: `dumpsys package <unknown>` answers
# "Unable to find package: <name>", which contains the name and so passes any substring test on the
# dump - an absent package would have been reported as declaring nothing rather than as absent.
# Kept as an array: `pm list packages <prefix>` also returns longer siblings, and interpolating the
# whole result into one string would join those lines with a space and defeat an exact-line test.
$listed = @(& $adb -s $target shell "pm list packages $Package" 2>$null)
if (-not ($listed | Where-Object { $_.Trim() -eq "package:$Package" })) {
    Complete-Run 2 'not-installed' "package $Package is not installed on $target"
}
$dump = "$(& $adb -s $target shell "dumpsys package $Package" 2>$null)"

# The declaration is read off the INSTALLED package rather than off the manifest sources: the sweep
# can be pointed at a build it did not just assemble, and only the device knows what is on it.
if ($dump -notmatch 'permission\.MANAGE_EXTERNAL_STORAGE') {
    Complete-Run 3 'not-declared' ("$Package does not request MANAGE_EXTERNAL_STORAGE; S2012 left the " +
        'declaration in the noLegal flavor only, so no appops channel can grant it on this build')
}

& $adb -s $target shell appops set $Package MANAGE_EXTERNAL_STORAGE allow *> $null
$readBack = "$(& $adb -s $target shell "appops get $Package MANAGE_EXTERNAL_STORAGE" 2>$null)".Trim()
$result.appop = $readBack

if ($readBack -match 'MANAGE_EXTERNAL_STORAGE:\s*allow') {
    Complete-Run 0 'granted' "appops reads back: $readBack"
}

Complete-Run 1 'refused' ("the permission is declared but the appop did not take; appops reads back: " +
    "'$readBack'. This is a device or vendor policy refusal - investigate before trusting any " +
    'scenario that needs all-files access')
