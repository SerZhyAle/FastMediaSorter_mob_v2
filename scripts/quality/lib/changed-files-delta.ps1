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
    its occurrences as new (fail-closed: new code must not add the anti-pattern). This preserves
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

        $workCount = [int](& $CountInText $workText)
        $headCount = [int](& $CountInText $headText)
        $delta = $workCount - $headCount
        if ($delta -lt 0) { $delta = 0 }
        $growth += $delta
        $perFile.Add([pscustomobject]@{ Path = $rel; Work = $workCount; Head = $headCount; New = $delta })
    }

    [pscustomobject]@{ Growth = $growth; PerFile = $perFile }
}
