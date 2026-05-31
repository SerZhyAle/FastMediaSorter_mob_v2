# Classifier.ps1 (S0313) - exports Get-LineClassification.
#
# Fills the `classification` field of a violation record:
#   new-or-touched - the matched line was added/modified in the change set, so a
#                    NEW flavor gate landed there; this is what blocks.
#   legacy         - the matched line is pre-existing debt that merely sits in a
#                    changed file (or a file scanned without diff context that
#                    the caller did not assert intent on); never blocks.
#
# Diff context comes from `git diff --unified=0` against the resolved ref: only
# the right-hand (added) line numbers of each hunk count as touched. Explicit
# -Path files carry no diff context - the caller asserted intent to scan them,
# so every match there is new-or-touched.

Set-StrictMode -Version Latest

function Get-AddedLineSet {
    # Returns a HashSet[int] of 1-based line numbers added/modified for one file,
    # parsed from `git diff -U0` hunk headers (@@ -a,b +c,d @@). The right-hand
    # '+c,d' span enumerates the touched lines on the new side of the file.
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [Parameter(Mandatory)][string] $RelPath,
        [string] $Ref
    )

    $set = [System.Collections.Generic.HashSet[int]]::new()

    if ([string]::IsNullOrWhiteSpace($Ref)) {
        # Working-tree view: combine unstaged and staged hunks so a change is
        # seen whether or not it has been `git add`-ed.
        $diff = @()
        $diff += (git -C $RepoRoot diff --unified=0 -- $RelPath) 2>$null
        $diff += (git -C $RepoRoot diff --unified=0 --cached -- $RelPath) 2>$null
    } else {
        $diff = (git -C $RepoRoot diff --unified=0 $Ref -- $RelPath) 2>$null
    }

    foreach ($line in $diff) {
        # Hunk header form: @@ -<oldStart>[,<oldLen>] +<newStart>[,<newLen>] @@
        if ($line -match '^@@ .* \+(\d+)(?:,(\d+))? @@') {
            $start = [int]$Matches[1]
            $len = if ($Matches[2]) { [int]$Matches[2] } else { 1 }
            # A hunk with +c,0 is a pure deletion: nothing added on the new side.
            for ($i = 0; $i -lt $len; $i++) {
                [void]$set.Add($start + $i)
            }
        }
    }
    return $set
}

function Get-LineClassification {
    # Classifies a single already-built violation record and returns it with the
    # `classification` field filled.
    #
    # -Violation   the record from New-FlavorViolation (file/line/...).
    # -Source      'explicit' or 'diff' (from Get-ChangedMainKotlin).
    # -AddedLines  pre-computed HashSet[int] of touched line numbers for this
    #              file in diff mode; ignored for explicit source. The driver
    #              builds it once per file via Get-AddedLineSet to avoid invoking
    #              git once per match.
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject] $Violation,
        [Parameter(Mandatory)][string] $Source,
        [System.Collections.Generic.HashSet[int]] $AddedLines
    )

    if ($Source -eq 'explicit') {
        # Caller asserted intent on this exact path: treat every match as touched.
        $Violation.classification = 'new-or-touched'
        return $Violation
    }

    # Diff source: touched only if the line was added on the new side.
    if ($null -ne $AddedLines -and $AddedLines.Contains([int]$Violation.line)) {
        $Violation.classification = 'new-or-touched'
    } else {
        $Violation.classification = 'legacy'
    }
    return $Violation
}
