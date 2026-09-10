<#
.SYNOPSIS
  S2782 - classifies one `adb.ps1 clip-check` exit code for the watch pre-release sweep.

.DESCRIPTION
  Pure function: no adb call, no device, no writes. It lives here rather than in either caller
  because BOTH the walk and the verdict must answer "was the shape wrong, or did we fail to look"
  the same way - the walk turns the answer into its exit code, the verdict turns it into the PASS -
  and two copies of the 9/10 literal is exactly the disagreement the S1621 rule exists to prevent.

  Why the distinction is load-bearing (S2782): `wear-prerelease-walk.ps1` used to count EVERY
  non-zero clip-check exit as a shape failure, so a dropped adb connection was reported as a WO-V16
  violation and sent the operator down `/spec-prerelease-wear` step 2's "fix and re-run from step 1"
  branch after a defect that did not exist. That is the same substitution S2767 removed one line
  above, where a screen the walk never opened was printed as a screen the app had broken.

  Sourced, never executed directly, so it declares no exit codes of its own.
#>

function Get-ClipShapeClass {
    <#
    .SYNOPSIS
      'clean', 'finding' or 'unchecked' for one clip-check exit code.

    .DESCRIPTION
      `clip-check` spends exactly two codes on the shape itself (`scripts/devtest/adb.ps1`, Exit
      codes block): 9 is OFF-GLASS, "no scroll position saves it", and 10 is `-Strict` CLIPPED, "a
      reviewer photographing this frame sees a cut edge" - the criterion Play actually applied. EDGE
      and CLIPPED reach no code at all, so they are not what needs telling apart here.

      Every other non-zero code belongs to the wrapper, not to the screen: adb not found (1), no
      online device (2), an ambiguous device choice (3), the package not installed (4), adb itself
      returning non-zero (7). None of them says anything about the glass, and reporting one as a
      finding claims a measurement that never happened.

      A row with no exit code at all is 'clean': the shape check was skipped for the whole run, which
      the caller records once in `skipShapeCheck` rather than per screen.
    #>
    param($ExitCode)

    if ($null -eq $ExitCode -or "$ExitCode" -eq '') { return 'clean' }

    $code = 0
    # An exit code that will not parse is not a passing one. It means the caller recorded something
    # other than a number, which is a state nothing here can judge the glass from.
    if (-not [int]::TryParse("$ExitCode", [ref]$code)) { return 'unchecked' }

    if ($code -eq 0) { return 'clean' }
    if ($code -eq 9 -or $code -eq 10) { return 'finding' }
    return 'unchecked'
}
