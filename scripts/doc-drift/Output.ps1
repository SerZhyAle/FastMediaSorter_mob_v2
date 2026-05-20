# Output.ps1 - formats Comparator records per DECISIONS.md D-5 output grammar.
#
# Public entry: Format-DriftReport -Records <array> [-VerboseOutput] [-Color]
# Returns: array of strings (one record per line, plus final SUMMARY line).
#
# Contract: PLAN/S0271_truth_drift_detection/DECISIONS.md (D-5 grammar lock)
# Format rules:
#   - Plain ASCII; no emoji.
#   - ANSI color escapes only when -Color is set:
#       91 red    -> FAIL, INCONSISTENT, MISSING
#       93 yellow -> WARN
#       92 green  -> PASS
#       0  reset  -> applied after each colored line
#   - PASS lines emitted only when -VerboseOutput is set.
#   - Counters in SUMMARY computed from records array, never from print loop.
#   - Stable order: manifest order by pin (preserved from comparator), then alphabetic
#     by DocPath within a pin (already sorted upstream).

function script:Wrap-Color {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string] $Text,
        [Parameter(Mandatory)][string] $ColorCode,
        [bool] $Enable
    )
    if (-not $Enable) { return $Text }
    return ([char]0x1B) + "[${ColorCode}m" + $Text + ([char]0x1B) + "[0m"
}

function script:Format-Record {
    param(
        [Parameter(Mandatory)][object] $Record,
        [bool] $UseColor
    )
    switch ($Record.Status) {
        'FAIL' {
            $line = "FAIL | $($Record.Pin) | gradle: $($Record.Gradle) | $($Record.DocPath): $($Record.DocValue)"
            return Wrap-Color -Text $line -ColorCode '91' -Enable $UseColor
        }
        'WARN' {
            $line = "WARN | $($Record.Pin) | gradle: $($Record.Gradle) | $($Record.DocPath): $($Record.DocValue) (range)"
            return Wrap-Color -Text $line -ColorCode '93' -Enable $UseColor
        }
        'INCONSISTENT' {
            $line = "INCONSISTENT | $($Record.Pin) | $($Record.DocPath): $($Record.DocValue)"
            return Wrap-Color -Text $line -ColorCode '91' -Enable $UseColor
        }
        'MISSING' {
            $line = "MISSING | $($Record.Pin) | $($Record.DocPath): required mention not found"
            return Wrap-Color -Text $line -ColorCode '91' -Enable $UseColor
        }
        'SKIP' {
            return "SKIP | $($Record.Pin) | reason: $($Record.Reason)"
        }
        'PASS' {
            $line = "PASS | $($Record.Pin) | $($Record.Gradle)"
            return Wrap-Color -Text $line -ColorCode '92' -Enable $UseColor
        }
        default {
            return "UNKNOWN | $($Record.Pin) | status=$($Record.Status)"
        }
    }
}

function Format-DriftReport {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][object[]] $Records,
        [switch] $VerboseOutput,
        [switch] $Color
    )

    $useColor = [bool]$Color
    $emitVerbose = [bool]$VerboseOutput
    $lines = @()
    $counts = @{
        pass = 0; fail = 0; warn = 0; skip = 0; inconsistent = 0; missing = 0
    }

    foreach ($r in $Records) {
        switch ($r.Status) {
            'PASS'         { $counts.pass++ }
            'FAIL'         { $counts.fail++ }
            'WARN'         { $counts.warn++ }
            'SKIP'         { $counts.skip++ }
            'INCONSISTENT' { $counts.inconsistent++ }
            'MISSING'      { $counts.missing++ }
        }
        if ($r.Status -eq 'PASS' -and -not $emitVerbose) { continue }
        $lines += Format-Record -Record $r -UseColor $useColor
    }

    $total = $counts.pass + $counts.fail + $counts.warn + $counts.skip + $counts.inconsistent + $counts.missing
    $lines += "SUMMARY | total: $total | pass: $($counts.pass) | fail: $($counts.fail) | warn: $($counts.warn) | skip: $($counts.skip) | inconsistent: $($counts.inconsistent) | missing: $($counts.missing)"
    return ,$lines
}
