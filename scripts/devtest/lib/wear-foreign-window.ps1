<#
.SYNOPSIS
  S2794 - reads the top visible window's package out of `dumpsys window`, for the pre-release walk.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes. That is the point - the classification is the
  part worth pinning, and scripts/devtest/wear-prerelease-walk.tests/Run-Tests.ps1 can only dot-source
  it if it lives outside a script that walks screens on load. Same reason lib/wear-wakefulness.ps1
  exists.

  Why the walk needs this at all, measured 2026-09-09 on Galaxy Watch 7 (SM-L310): the watch drained
  to 16% and Samsung's low-battery panel took the display. The walk then reported apps-calculator as
  `failed` and the next fourteen screens as `unreachable` - fifteen false verdicts in one run. The
  app-in-front guard reads `dumpsys activity activities` and finds `ResumedActivity`, but a system
  window is a window, not an activity, so the app remains the resumed activity and the guard sees the
  app as "in front". The tree read returns the panel's content, not the app's, and the expected token
  is not found. Reading `mCurrentFocus` from `dumpsys window` is the check that sees the panel,
  because `mCurrentFocus` names the window actually drawn on top, resumed activity or not.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

function Get-TopWindowPackage {
    <#
    .SYNOPSIS
      The package of the mCurrentFocus window in a `dumpsys window` capture, or $null when the
      capture does not carry one.

    .DESCRIPTION
      `mCurrentFocus=Window{<hash> <package>/<activity>}` is the window Android draws on top. When a
      system window (Samsung's low-battery panel, a permission dialog) takes the display, this line
      names the system package, not the app - and that is the one signal the app-in-front guard
      cannot read, because it reads `ResumedActivity` from `dumpsys activity activities` and a system
      window does not change the resumed activity.

      An absent or unparseable line returns $null, which the caller reads as "not foreign" and
      proceeds normally. A false negative (missed foreign window) is a screen judged on its own
      merits; a false positive (app's own window read as foreign) is a screen the walk skipped for
      no reason, which is strictly worse.
    #>
    param([string]$DumpText)

    if ([string]::IsNullOrWhiteSpace($DumpText)) { return $null }
    $focus = ($DumpText -split "`r?`n" | Where-Object { $_ -match 'mCurrentFocus=' } | Select-Object -First 1)
    if (-not $focus) { return $null }
    # mCurrentFocus=Window{<hash> <package>/<activity>}  or  mCurrentFocus=Window{<hash> u0 <package>/<activity>}
    # The package is the first non-space token after the hash (and optional uid), before any '/'.
    $match = [regex]::Match($focus, 'mCurrentFocus=Window\{\w+\s+(?:u\d+\s+)?(\S+)')
    if (-not $match.Success) { return $null }
    $token = $match.Groups[1].Value
    return ($token -split '/')[0]
}

function Test-IsAppWindowPackage {
    <#
    .SYNOPSIS
      True when a window package belongs to the app, in its release or its debug install.

    .DESCRIPTION
      The walk runs against a release artifact, but a live-watch check on a debug install is the same
      walk. The debug build carries the `.debug` applicationId suffix, and comparing against the release
      id alone read the app's own window as foreign: S3181 measured three screens recorded `manual`
      with the foreign package named as `com.sza.fastmediasorter.debug` itself.
    #>
    param([string]$Package, [string]$AppPackage)

    return ($Package -eq $AppPackage -or $Package -eq "$AppPackage.debug")
}
