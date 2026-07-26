#requires -Version 7.0
<#
.SYNOPSIS
    S1184: shared -ChangedFiles normalization for diff-scoped quality gates.

.DESCRIPTION
    A scoped gate re-judges a project-wide failure against only the changed files. It receives
    them as [string[]]$ChangedFiles. The trap: `pwsh -File .. -ChangedFiles 'a,b'` binds the CSV
    as ONE array element ('a,b'), never splitting on the comma - so a gate that iterates the raw
    argument matches the whole 'a,b' as a single bogus path. In assert-detekt that yielded a
    silent "none among changed files" false PASS; in the count-ratchet gates it made Test-Path
    miss and report zero growth. Splitting on commas makes both call forms - a real PS array
    @('a','b') and a CSV 'a,b' - collapse to the same list, so the multi-file verdict equals the
    union of the single-file verdicts.
#>

function Expand-ChangedFiles {
    <# Flatten a -ChangedFiles argument into individual, comma-split, whitespace-trimmed paths.
       A single path with no comma returns itself, so existing array callers are unaffected. #>
    [CmdletBinding()]
    param([AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles)
    if (-not $ChangedFiles) { return @() }
    $ChangedFiles |
        ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
}

function Select-ChangedFileFindings {
    <# Return the subset of $FindingFiles (already lowercased, forward-slash paths from the detekt
       Checkstyle report) that match any of $ChangedFiles. A finding matches when its path contains
       a changed file's normalized path as a substring. CSV and array -ChangedFiles are identical. #>
    [CmdletBinding()]
    param(
        [AllowEmptyCollection()][AllowNull()][string[]]$FindingFiles,
        [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles
    )
    $norm = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles |
            ForEach-Object { ($_ -replace '\\', '/').Trim('.', '/').ToLower() } |
            Where-Object { $_ })
    $FindingFiles | Where-Object {
        $ff = $_
        $matched = $false
        foreach ($cf in $norm) { if ($ff -like "*$cf*") { $matched = $true; break } }
        $matched
    }
}
