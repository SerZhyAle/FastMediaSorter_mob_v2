#requires -Version 7.0
<#
.SYNOPSIS
    S4130: reads the NOT APPLICABLE verdict word off a child check's output - contract CHECK-VERDICT
    0.12, section 10 item A.

.DESCRIPTION
    A check whose work the caller deliberately switched off exits 0 and ends on
    `<subject>: NOT APPLICABLE`, optionally followed by a parenthesis. Exit 0 alone cannot tell it
    from a pass, so an aggregator reads the word off the verdict line, which rule 2.5 makes the
    last line written. The three aggregators (post-change.ps1, assert-fast-gates.ps1,
    assert-release-scope-gates.ps1) all call this one reader so the match cannot drift between them.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

function Test-NotApplicableVerdict {
    <# True when the last non-blank line is `<subject>: NOT APPLICABLE[ (..)]`. Colour escapes are
       stripped first: a child rendered through Write-Host into a captured stream can carry them. #>
    [CmdletBinding()]
    [OutputType([bool])]
    param([AllowNull()][AllowEmptyCollection()][object[]]$Lines)
    if (-not $Lines) { return $false }
    $last = $null
    foreach ($line in $Lines) {
        foreach ($part in ([string]$line -split "`r?`n")) {
            $clean = ($part -replace '\x1b\[[0-9;]*m', '').Trim()
            if ($clean) { $last = $clean }
        }
    }
    if (-not $last) { return $false }
    return $last -cmatch '^[^\s:][^:]*: NOT APPLICABLE(\s*\(.*\))?\.?$'
}
