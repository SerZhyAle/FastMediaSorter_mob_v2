<#
.SYNOPSIS
  S2547 - reads a watch's wakefulness out of `dumpsys power`, for the pre-release walk.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes. That is the point - the classification is the
  part worth pinning, and scripts/devtest/wear-prerelease-walk.tests/Run-Tests.ps1 can only dot-source
  it if it lives outside a script that walks screens on load. Same reason lib/ui-tree.ps1 exists.

  Why the walk needs this at all, measured 2026-09-04: a run on a qualified watch returned
  `observed 0, failed 16` with a clean log audit. The watch had gone into ambient mode, the watch face
  was in front, and all sixteen "screen failures" were readings of the launcher. The walk already
  checks that the app is in front and relaunches it when it is not - but it relaunched the app UNDER a
  sleeping display, so the check passed and the readings stayed wrong. A dark display and sixteen
  broken screens are different answers and must not share an exit code.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

# The values Android's power manager reports. Only Awake can be walked: Dozing is ambient mode, where
# the watch face is in front and the app is not, and Dreaming is the screensaver.
$script:WEAR_WAKEFULNESS_USABLE = @('Awake')

function Get-WearWakefulness {
    <#
    .SYNOPSIS
      The mWakefulness value in a `dumpsys power` capture, or $null when the capture does not carry one.
    #>
    param([string]$DumpText)

    if ([string]::IsNullOrWhiteSpace($DumpText)) { return $null }
    $match = [regex]::Match($DumpText, 'mWakefulness=([A-Za-z_]+)')
    if (-not $match.Success) { return $null }
    return $match.Groups[1].Value
}

function Test-WearDisplayUsable {
    <#
    .SYNOPSIS
      True only when the value is one the walk may read screens under.

    .DESCRIPTION
      An unknown or absent value is NOT usable. A dump that never reported wakefulness is a question,
      and answering it with "probably fine" is how the 2026-09-04 run produced sixteen confident
      readings of the wrong application.
    #>
    param([string]$Wakefulness)

    if ([string]::IsNullOrWhiteSpace($Wakefulness)) { return $false }
    return $script:WEAR_WAKEFULNESS_USABLE -contains $Wakefulness
}
