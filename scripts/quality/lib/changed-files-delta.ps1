#requires -Version 7.0
<#
.SYNOPSIS
    S0848 Phase 04: shared changed-files delta for count-vs-baseline ratchet gates.

.DESCRIPTION
    A count-ratchet gate normally scans all of src/main and compares the total against a
    committed integer baseline. Under post-change -ScopeToFile that full scan is wasteful
    (it re-counts the whole tree just to soften the verdict to advisory). This helper lets a
    gate judge only the CHANGED files with a real delta instead:

      new occurrences = max(0, count(working copy) - count(HEAD copy))   summed over changed files

    A "new occurrence" is one the uncommitted change INTRODUCED (present in the working file,
    absent from its committed HEAD version). Pre-existing (baselined) occurrences in the same
    file do not fail - they are already in HEAD. A brand-new file (absent from HEAD) counts all
    its occurrences as new (fail-closed: new code must not add the anti-pattern); a RENAMED file is
    compared against its pre-rename HEAD copy, so moving a file does not re-charge its existing
    occurrences to the change that moved it (S3244). This preserves
    the "count must not grow" guarantee for the changed file without a full-project scan, and
    without needing a per-file baseline (the integer baseline is untouched).

    Dot-source this file and call Measure-ChangedFileGrowth with the gate's OWN counting logic
    as the -CountInText callback, so the per-file count is byte-for-byte what the full scan uses.
    The full-scan path (no -ChangedFiles) is unaffected by this helper.
#>

# S1184: shared -ChangedFiles CSV-splitting normalizer (Expand-ChangedFiles).
. (Join-Path $PSScriptRoot 'changed-files.ps1')

# Returns the HEAD version text of a repo-relative path, or '' if untracked / git unavailable.
function Get-GitHeadText {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$RelPath)
    try {
        $text = & git -C $RepoRoot show "HEAD:$RelPath" 2>$null | Out-String
        if ($LASTEXITCODE -ne 0) { return '' }
        return $text
    }
    catch { return '' }
}

# S3244: the path a renamed file had in HEAD, or '' when it is not a rename. Without this, a rename
# reads as a brand-new file and every occurrence the file already carried counts as introduced today -
# renaming dialog_stream_offload_offer.xml to sheet_stream_offload_offer.xml scored 18 new hardcoded
# dimens that no edit added. Rename detection is git's (-M), so a rewritten file is still judged new.
function Get-GitRenameSource {
    param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$RelPath)
    try {
        # No pathspec: git only reports a rename when BOTH halves are in the diff, so narrowing to the
        # new path returns a bare 'A' and the rename is invisible.
        $records = & git -C $RepoRoot diff -M --name-status --diff-filter=R HEAD 2>$null
        if ($LASTEXITCODE -ne 0) { return '' }
        foreach ($record in $records) {
            $fields = $record -split "`t"
            if ($fields.Count -ge 3 -and $fields[0] -match '^R' -and $fields[2] -eq $RelPath) { return $fields[1] }
        }
        return ''
    }
    catch { return '' }
}

function Measure-ChangedFileGrowth {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$ChangedFiles,
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][string[]]$Extensions,      # e.g. @('.kt') or @('.xml')
        [Parameter(Mandatory)][scriptblock]$CountInText,  # param([string]$text) -> [int]
        [string[]]$ExcludeNames = @()
    )

    $rootNorm = ($RepoRoot -replace '\\', '/').TrimEnd('/')
    $growth = 0
    $perFile = [System.Collections.Generic.List[object]]::new()

    # S1184: split a comma-joined -ChangedFiles into individual paths - pwsh -File binds a CSV as one
    # array element, which would otherwise be treated as a single bogus path that Test-Path misses.
    foreach ($cf in (Expand-ChangedFiles -ChangedFiles $ChangedFiles)) {
        if ([string]::IsNullOrWhiteSpace($cf)) { continue }
        $ext = [System.IO.Path]::GetExtension($cf)
        if ($Extensions -notcontains $ext) { continue }
        $name = [System.IO.Path]::GetFileName($cf)
        if ($ExcludeNames -contains $name) { continue }

        $abs = if ([System.IO.Path]::IsPathRooted($cf)) { $cf } else { Join-Path $RepoRoot $cf }
        $rel = ($abs -replace '\\', '/')
        if ($rel.ToLower().StartsWith($rootNorm.ToLower() + '/')) {
            $rel = $rel.Substring($rootNorm.Length + 1)
        }

        $workText = ''
        if (Test-Path -LiteralPath $abs) {
            $workText = Get-Content -LiteralPath $abs -Raw
            if ($null -eq $workText) { $workText = '' }
        }
        $headText = Get-GitHeadText -RepoRoot $RepoRoot -RelPath $rel
        if ([string]::IsNullOrEmpty($headText)) {
            $renamedFrom = Get-GitRenameSource -RepoRoot $RepoRoot -RelPath $rel
            if ($renamedFrom) { $headText = Get-GitHeadText -RepoRoot $RepoRoot -RelPath $renamedFrom }
        }

        # S3255: the path rides along as a second positional argument so a predicate whose count is
        # a property of a file pair (landscape parity) sees it; existing param($text) predicates
        # are unaffected.
        $workCount = [int](& $CountInText $workText $rel)
        $headCount = [int](& $CountInText $headText $rel)
        $delta = $workCount - $headCount
        if ($delta -lt 0) { $delta = 0 }
        $growth += $delta
        $perFile.Add([pscustomobject]@{ Path = $rel; Work = $workCount; Head = $headCount; New = $delta })
    }

    [pscustomobject]@{ Growth = $growth; PerFile = $perFile }
}
