<#
.SYNOPSIS
  S4485 - which installed FastMediaSorter package the watch walk drives, and whether the app is in front.

.DESCRIPTION
  Pure functions: no adb call, no device, no writes. The walk captures `pm list packages` and
  `dumpsys activity` output and hands the text in, which is what lets a suite pin the decision
  without a watch.

  Why the choice is made here and never defaulted. The release and the debug build publish under
  two application ids (`com.sza.fastmediasorter`, `com.sza.fastmediasorter.debug`), and a real watch
  can carry both. The walk used to hard-code the release id, so a run started right after installing
  a debug build drove the store build and its verdict described the wrong artifact (measured
  2026-10-28, Galaxy Watch 7: topResumedActivity was the store build while the debug build was the
  one just installed). Two installed builds with no named choice is therefore a refusal, the same
  could-not-verify answer a sleeping display gets, not a guess.

  `pm list packages <id>` filters by SUBSTRING, so a query for the release id also lists the debug
  id. The parse here compares whole lines, and the in-front test requires the id to end at the
  component separator, for the same reason: a plain substring match read the debug build in front
  as the release build.

  Sourced, never executed directly, so it declares no exit codes of its own. It sets no StrictMode:
  it is dot-sourced into a caller's scope, where changing strictness would alter code this file
  never sees.
#>

. (Join-Path $PSScriptRoot 'prerelease-package-guard.ps1')

function Select-WearWalkPackage {
    <#
    .SYNOPSIS
      The package the walk drives, how it was chosen, or the reason it cannot be chosen.

    .DESCRIPTION
      Returns an object with `Package` (the id, $null on refusal), `ChosenBy` (`package`,
      `debugBuild`, `only-installed`, or $null on refusal), `Installed` (the FastMediaSorter ids the
      capture reported, release first) and `Error` ($null when a package was chosen).

    .PARAMETER PmListOutput
      Raw output of `adb shell pm list packages <base id>`. $null and empty mean nothing installed.

    .PARAMETER Requested
      The id named with -Package. Only the release or the debug id of this application is accepted.

    .PARAMETER DebugBuild
      The -DebugBuild switch: name the debug id without spelling it. Combined with -Package it is
      a refusal, because two statements of the choice can disagree.

    .PARAMETER BasePackage
      The release application id; the debug id is this plus `.debug`.
    #>
    [CmdletBinding()]
    param(
        [AllowNull()]
        [AllowEmptyCollection()]
        [string[]]$PmListOutput,

        [string]$Requested,

        [switch]$DebugBuild,

        [string]$BasePackage = 'com.sza.fastmediasorter'
    )

    $debugPackage = "$BasePackage.debug"
    $installed = @(@($BasePackage, $debugPackage) |
            Where-Object { Test-PmPackagePresent -PmListOutput $PmListOutput -Package $_ })
    $listed = if ($installed.Count -gt 0) { $installed -join ', ' } else { 'none' }

    $refuse = {
        param([string]$Reason)
        [pscustomobject]@{ Package = $null; ChosenBy = $null; Installed = $installed; Error = $Reason }
    }

    if ($Requested -and $DebugBuild) {
        return (& $refuse '-Package and -DebugBuild both name the package to walk. Pass one of them.')
    }

    $wanted = $null
    $by = $null
    if ($Requested) {
        if ($Requested -ne $BasePackage -and $Requested -ne $debugPackage) {
            return (& $refuse "-Package '$Requested' is not an application id of this app. Use $BasePackage or $debugPackage.")
        }
        $wanted = $Requested
        $by = 'package'
    }
    elseif ($DebugBuild) {
        $wanted = $debugPackage
        $by = 'debugBuild'
    }

    if ($wanted) {
        if ($installed -notcontains $wanted) {
            return (& $refuse "the package to walk, $wanted, is not installed on the watch (installed: $listed).")
        }
        return [pscustomobject]@{ Package = $wanted; ChosenBy = $by; Installed = $installed; Error = $null }
    }

    if ($installed.Count -eq 0) {
        return (& $refuse "no FastMediaSorter package is installed on the watch ($BasePackage or $debugPackage).")
    }
    if ($installed.Count -gt 1) {
        return (& $refuse "both $BasePackage and $debugPackage are installed on the watch, and the walk will not guess which artifact to judge. Pass -Package <id> or -DebugBuild.")
    }
    return [pscustomobject]@{ Package = $installed[0]; ChosenBy = 'only-installed'; Installed = $installed; Error = $null }
}

function Test-WearWalkAppInFront {
    <#
    .SYNOPSIS
      True when the resumed-activity line names a component of exactly $Package.

    .DESCRIPTION
      The line reads `... u0 <package>/<activity> ...`. The id must start at a token boundary and end
      at the `/`, so the release id does not match the debug build's `<release id>.debug/` component.
      $null and empty are not in front, which sends the caller to the relaunch.

    .PARAMETER CurrentOutput
      Output of `adb.ps1 current`.

    .PARAMETER Package
      The id the walk drives.
    #>
    param(
        [AllowNull()]
        [AllowEmptyCollection()]
        [string[]]$CurrentOutput,

        [Parameter(Mandatory)]
        [string]$Package
    )

    if ($null -eq $CurrentOutput) { return $false }
    $text = @($CurrentOutput | Where-Object { $null -ne $_ }) -join "`n"
    return [bool]($text -match ('(?<![\w.])' + [regex]::Escape($Package) + '/'))
}
