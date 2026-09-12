<#
.SYNOPSIS
  S2709 - decide whether `pm list packages` reported an exact package as installed.

.DESCRIPTION
  Dot-sourced by scripts/devtest/prerelease-configure.ps1. Takes output already captured from
  `adb shell pm list packages <id>` and answers one question, so the parse is testable with no
  device attached and no adb on the machine.

  `pm list packages <id>` filters by SUBSTRING, not by equality: a query for
  com.sza.fastmediasorter.debug also returns com.sza.fastmediasorter.debug.test. The connected
  androidTest task installs both APKs and removes both when it finishes, and a partial removal
  leaves exactly the neighbour behind - so a non-empty check would report the app installed on a
  device carrying only the leftover test package.

  This file performs no I/O and invokes no external process. It deliberately sets no StrictMode:
  it is dot-sourced into a caller's scope, where changing strictness would alter the semantics of
  code this file never sees.
#>

function Test-PmPackagePresent {
    <#
    .SYNOPSIS
      True when the captured output carries a line naming exactly $Package.
    .PARAMETER PmListOutput
      Raw output of `adb shell pm list packages <id>`. $null, empty and whitespace mean absent.
    .PARAMETER Package
      The application id to match. Compared for equality, never as a prefix.
    #>
    [CmdletBinding()]
    [OutputType([bool])]
    param(
        [AllowNull()]
        [AllowEmptyCollection()]
        [string[]]$PmListOutput,

        [Parameter(Mandatory)]
        [string]$Package
    )

    if ($null -eq $PmListOutput) { return $false }

    $wanted = "package:$Package"
    foreach ($line in $PmListOutput) {
        if ($null -eq $line) { continue }
        # Split rather than trim only: a single captured string may carry several CRLF-joined lines.
        foreach ($piece in ($line -split "`r?`n")) {
            if ($piece.Trim() -eq $wanted) { return $true }
        }
    }

    return $false
}
