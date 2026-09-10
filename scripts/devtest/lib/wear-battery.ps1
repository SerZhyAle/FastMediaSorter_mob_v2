<#
.SYNOPSIS
  S2794 - reads the battery level out of `dumpsys battery`, for the pre-release walk.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes. Same pattern as lib/wear-wakefulness.ps1: the
  parsing is the part worth pinning, and the test suite can only dot-source it if it lives outside a
  script that walks screens on load.

  Why the walk needs this at all, measured 2026-09-09 on Galaxy Watch 7 (SM-L310): the watch drained
  to 16% and Samsung's low-battery panel took the display, producing fifteen false verdicts in one
  run. The walk never reported the device's charge, so a run on a dying watch looked like a defect
  report. Reading the level at start and refusing below a threshold is the same pattern S2547 used
  for a sleeping display: a precondition that exits 2 (could-not-verify) rather than describing the
  watch face - or in this case, the battery panel.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

function Get-BatteryLevel {
    <#
    .SYNOPSIS
      The `level:` field (0-100) in a `dumpsys battery` capture, or $null when the capture does not
      carry one.

    .DESCRIPTION
      `dumpsys battery` prints `  level: 42` on its own line. An absent or unparseable field returns
      $null, which the caller reads as "unknown" and skips the threshold check rather than guessing
      - the same discipline `Get-WearWakefulness` follows for a capture that never reported
      wakefulness.
    #>
    param([string]$DumpText)

    if ([string]::IsNullOrWhiteSpace($DumpText)) { return $null }
    $match = [regex]::Match($DumpText, '(?m)^\s*level:\s*(\d+)\s*$')
    if (-not $match.Success) { return $null }
    return [int]$match.Groups[1].Value
}
