<#
.SYNOPSIS
  Data contract and pure parser for the build-failure digest (S0312).

.DESCRIPTION
  Dot-source-only helper. It defines the machine-readable digest shape and a pure
  parser that turns raw build/lint log lines into that shape. It NEVER reads a file,
  spawns a process, or writes to the console on load - the consuming command
  (scripts/builders/build-failure-digest.ps1, Phase 02) owns all I/O.

  Digest object (ordered, contract tokens are asserted verbatim by the spec):
    command                - string. The build/lint command whose log was digested
                             (e.g. assembleStandardDebug), or 'unknown' when not recoverable.
    exitCode               - integer. The digest tool's own exit code (mirrors the verdict).
    firstActionableFailure - object or $null. The first actionable failure, with:
        module   - string or $null (e.g. app_v2, wear).
        flavor   - string or $null (e.g. standardDebug, noLegalDebug).
        file     - string or $null (path of the offending source).
        line     - integer or $null.
        message  - string or $null (the compiler/lint message text).
    rawLogPath             - string or $null. Absolute path to the full raw log.
    verdict                - string enum: failure | success | blocked.

  verdict values:
    failure - a FAILURE: block or at least one actionable failure was found.
    success - the log contains BUILD SUCCESSFUL and no failure block.
    blocked - the log could not be resolved or read (no log, empty log), so neither
              success nor failure can be asserted. Anti-stale-success guard
              (strategic S0312 §2.4 and §11.3).

  This file is a DOT-SOURCE-ONLY helper. Do not invoke it as a script entry point;
  it produces no output and performs no work on its own. Dot-source it
  (. ./scripts/builders/build-failure-digest.contract.ps1) to obtain the two
  functions below.

  ADR-1 (strategic §9): failure-block extraction is delegated to the existing
  scripts/builders/get-last-build-failure.ps1 (a.ps1 bf). This parser reuses the
  same 'e: file:///' and '> Task ... FAILED' markers that script already recognizes;
  it does not invent new failure markers.
#>

# GUARD: this is a dot-source-only helper. Never run it as a script entry point - it
# performs no work and emits nothing on load. Dot-source it to obtain the two functions.

# Blocked is the default verdict and its corresponding exit code; both live here so
# the contract builder and the Phase 02 command agree on a single source of truth.
$script:BuildFailureDigestBlockedExitCode = 20

function New-BuildFailureDigest {
    <#
    .SYNOPSIS
      Construct an empty build-failure digest with the contract field order.
    .DESCRIPTION
      Returns an ordered hashtable with keys (in order):
        command, exitCode, firstActionableFailure, rawLogPath, verdict
      firstActionableFailure is itself an ordered hashtable with keys:
        module, flavor, file, line, message
      Every field defaults to $null except verdict (defaults to 'blocked') and
      exitCode (defaults to the blocked exit code). Constructs shape only - reads no
      file and runs no process.
    #>
    [CmdletBinding()]
    param()

    return [ordered]@{
        command                = $null
        exitCode               = $script:BuildFailureDigestBlockedExitCode
        firstActionableFailure = [ordered]@{
            module  = $null
            flavor  = $null
            file    = $null
            line    = $null
            message = $null
        }
        rawLogPath             = $null
        verdict                = 'blocked'
    }
}

function ConvertFrom-BuildFailureLog {
    <#
    .SYNOPSIS
      Pure parser: raw build/lint log lines -> build-failure digest.
    .DESCRIPTION
      Transforms the supplied log lines into a digest built via New-BuildFailureDigest.
      Reads no file and spawns no process; it only inspects the lines passed in. Never
      throws on malformed input - unrecognized lines simply leave fields $null.

      Precedence for firstActionableFailure:
        1. First compiler error line ('e: file:///PATH:LINE:COL: MESSAGE') -> file/line/message.
        2. Else first failed-task line ('> Task :module:taskName FAILED') -> message = task token.
      module/flavor are always derived from the first failed-task line when one exists.

      verdict:
        failure - any FAILURE: line OR any actionable failure was found.
        success - a BUILD SUCCESSFUL line exists and no failure was found.
        blocked - otherwise (left as the New-BuildFailureDigest default).
    .PARAMETER LogLines
      The raw log lines to digest (as produced by get-last-build-failure.ps1 / a.ps1 bf).
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $false)]
        [string[]]$LogLines
    )

    $digest = New-BuildFailureDigest

    if ($null -eq $LogLines -or $LogLines.Count -eq 0) {
        return $digest
    }

    $foundActionable = $false
    $foundFailureMarker = $false
    $foundSuccess = $false

    foreach ($line in $LogLines) {
        if ($null -eq $line) { continue }

        # Compiler error anchor. Same 'e: file:///' marker get-last-build-failure.ps1 uses.
        # Groups: path (lazy up to the line:col), line (digits), msg (remainder).
        if (-not $foundActionable -and
            $line -match '^\s*e: file:///(?<path>.+?):(?<line>\d+):\d+:?\s*(?<msg>.*)$') {
            $digest.firstActionableFailure.file    = $Matches['path']
            $digest.firstActionableFailure.line    = [int]$Matches['line']
            $digest.firstActionableFailure.message = $Matches['msg']
            $foundActionable = $true
        }

        # Failed-task anchor. Same '> Task ... FAILED' marker get-last-build-failure.ps1 uses.
        # task token is ':module:taskName'; capture it to derive module/flavor and, as a
        # fallback, the actionable message when no compiler error line was present.
        if ($line -match '^\s*> Task (?<task>:[^\s]+) FAILED$') {
            $taskToken = $Matches['task']

            # Module is the segment between the first two colons of ':module:taskName'.
            $taskParts = $taskToken.TrimStart(':').Split(':')
            if ($taskParts.Count -ge 1 -and $taskParts[0]) {
                if ($null -eq $digest.firstActionableFailure.module) {
                    $digest.firstActionableFailure.module = $taskParts[0]
                }

                # Flavor: the lowercased leading variant token inside the task name
                # (assemble|compile|lint|test)<Variant>(Debug|Release|...). Null when the
                # task name carries no recognizable build-type suffix.
                if ($null -eq $digest.firstActionableFailure.flavor -and $taskParts.Count -ge 2) {
                    $taskName = $taskParts[1]
                    if ($taskName -match '^(?:assemble|compile|lint|test)(?<variant>[A-Z][A-Za-z]*?)(?:Debug|Release|UnitTest|AndroidTest)') {
                        $variant = $Matches['variant']
                        if ($variant) {
                            $digest.firstActionableFailure.flavor =
                                $variant.Substring(0, 1).ToLowerInvariant() + $variant.Substring(1)
                        }
                    }
                }
            }

            if (-not $foundActionable) {
                $digest.firstActionableFailure.message = $taskToken
                $foundActionable = $true
            }
        }

        if ($line -match '^\s*FAILURE:') {
            $foundFailureMarker = $true
        }

        if ($line -match 'BUILD SUCCESSFUL') {
            $foundSuccess = $true
        }
    }

    if ($foundFailureMarker -or $foundActionable) {
        $digest.verdict = 'failure'
    }
    elseif ($foundSuccess) {
        $digest.verdict = 'success'
    }
    # else: leave the New-BuildFailureDigest default of 'blocked'.

    return $digest
}
