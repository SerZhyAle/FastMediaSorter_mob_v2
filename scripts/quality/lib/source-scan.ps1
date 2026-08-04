#requires -Version 7.0
<#
.SYNOPSIS
    S1338: one walk of the source tree, many matchers.

.DESCRIPTION
    Twelve lexical gates each walked `app_v2/src` independently and each read every file
    again from disk. Measured: `a.ps1 fg` took 26.5 s, of which roughly 28.8 s of corpus
    time was duplicated walking plus fourteen pwsh cold starts. The tree does not change
    between two gates in the same second, so the walk is the waste, not the matching.

    This library walks each root ONCE, reads each file ONCE, and hands the text to every
    registered matcher whose extension filter accepts it. Adding a thirteenth matcher then
    costs one regex pass over already-loaded text instead of a whole new traversal.

    A matcher is deliberately just a name, an extension filter and a counting scriptblock -
    the same `param($text) -> [int]` shape the count-ratchet gates already pass to
    Measure-ChangedFileGrowth in lib/changed-files-delta.ps1. That keeps one definition of
    "what counts as a violation" per rule, shared between the full scan and the delta path,
    so the two can never drift apart.

    Dot-source it:  . (Join-Path $PSScriptRoot 'lib/source-scan.ps1')

.NOTES
    Pure PowerShell. No gradle, no git, no network. Safe to call from any gate.
#>

<#
.SYNOPSIS
    Describe one lexical rule for Invoke-SourceScan.

.PARAMETER Name
    Gate name as it appears in output, e.g. 'em-dash'.

.PARAMETER Extensions
    File extensions this rule applies to, lowercase and dotted, e.g. @('.kt').

.PARAMETER CountInText
    param([string]$text) -> [int]. Counts violations in one file's full text.

.PARAMETER LocateInText
    Optional param([string]$text) -> array of 1-based line numbers, used only when the
    caller asks for hit locations. Omitted means "count only", which is all a ratchet needs.

.PARAMETER PathFilter
    Optional regex a file's forward-slashed repo-relative path must match. Use it for a rule
    scoped tighter than its extension - layout colours live only under res/layout*.
#>
function New-SourceMatcher {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string[]]$Extensions,
        [Parameter(Mandatory)][scriptblock]$CountInText,
        [scriptblock]$LocateInText,
        [string]$PathFilter,
        [AllowEmptyCollection()][AllowNull()][string[]]$ExcludeNames = @()
    )
    [pscustomobject]@{
        Name         = $Name
        Extensions   = @($Extensions | ForEach-Object { $_.ToLower() })
        CountInText  = $CountInText
        LocateInText = $LocateInText
        PathFilter   = $PathFilter
        ExcludeNames = @($ExcludeNames)
    }
}

<#
.SYNOPSIS
    Walk the given roots once and apply every matcher to every file it accepts.

.OUTPUTS
    Hashtable keyed by matcher name:
      Count    [int]      - total violations across the walk.
      PerFile  [object[]] - { Path; Count; Lines } for each file with a non-zero count.
    Plus the reserved key '_scan' carrying { FilesWalked; FilesRead; ElapsedMs }, so a caller
    can prove the walk happened once instead of once per matcher.
#>
function Invoke-SourceScan {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][object[]]$Matchers,
        [Parameter(Mandatory)][string[]]$Roots,
        # Restrict the walk to these repo-relative paths (comma-joined or array, both accepted).
        # An empty set means walk everything - the full-project judgement.
        [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
        [switch]$Locate
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $rootNorm = ($RepoRoot -replace '\\', '/').TrimEnd('/')

    $results = @{}
    foreach ($m in $Matchers) {
        $results[$m.Name] = [pscustomobject]@{
            Count   = 0
            PerFile = [System.Collections.Generic.List[object]]::new()
        }
    }

    $wantedExtensions = @($Matchers | ForEach-Object { $_.Extensions } | Select-Object -Unique)

    # Build the candidate file list. ONE Get-ChildItem -Recurse per root - this single call
    # site is the whole point of the library, so keep it single.
    # A HashSet, not a List: `app_v2/src/main` and `app_v2/src/main/res/layout` are both
    # legitimate roots for different rules, and the second is inside the first - so a layout
    # file arrives twice and every count over it doubles. Caught by layout-hardcoded-colors
    # reporting exactly 2x its baseline on the first combined run.
    $candidates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    if ($ChangedFiles -and @($ChangedFiles).Count -gt 0) {
        . (Join-Path $PSScriptRoot 'changed-files.ps1')
        foreach ($cf in (Expand-ChangedFiles -ChangedFiles $ChangedFiles)) {
            $abs = if ([System.IO.Path]::IsPathRooted($cf)) { $cf } else { Join-Path $RepoRoot $cf }
            if (Test-Path -LiteralPath $abs -PathType Leaf) { [void]$candidates.Add($abs) }
        }
    }
    else {
        foreach ($root in $Roots) {
            $absRoot = if ([System.IO.Path]::IsPathRooted($root)) { $root } else { Join-Path $RepoRoot $root }
            if (-not (Test-Path -LiteralPath $absRoot)) { continue }
            Get-ChildItem -LiteralPath $absRoot -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object {
                    $wantedExtensions -contains $_.Extension.ToLower() -and
                    $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin|\.idea)[\\/]'
                } |
                ForEach-Object { [void]$candidates.Add($_.FullName) }
        }
    }

    $filesWalked = $candidates.Count
    $filesRead = 0

    foreach ($abs in $candidates) {
        $ext = [System.IO.Path]::GetExtension($abs).ToLower()
        $rel = ($abs -replace '\\', '/')
        if ($rel.ToLower().StartsWith($rootNorm.ToLower() + '/')) {
            $rel = $rel.Substring($rootNorm.Length + 1)
        }

        $leaf = [System.IO.Path]::GetFileName($abs)
        $applicable = @($Matchers | Where-Object {
                $_.Extensions -contains $ext -and
                ([string]::IsNullOrEmpty($_.PathFilter) -or $rel -match $_.PathFilter) -and
                ($_.ExcludeNames.Count -eq 0 -or $_.ExcludeNames -notcontains $leaf)
            })
        if ($applicable.Count -eq 0) { continue }

        # Read once, match many. This is the saving.
        $text = ''
        try { $text = Get-Content -LiteralPath $abs -Raw -ErrorAction Stop }
        catch { continue }
        $filesRead++
        if ([string]::IsNullOrEmpty($text)) { continue }

        foreach ($m in $applicable) {
            $count = [int](& $m.CountInText $text)
            if ($count -le 0) { continue }
            $entry = $results[$m.Name]
            $entry.Count += $count
            $lines = @()
            if ($Locate -and $m.LocateInText) { $lines = @(& $m.LocateInText $text) }
            $entry.PerFile.Add([pscustomobject]@{ Path = $rel; Count = $count; Lines = $lines })
        }
    }

    $sw.Stop()
    $results['_scan'] = [pscustomobject]@{
        FilesWalked = $filesWalked
        FilesRead   = $filesRead
        ElapsedMs   = [int]$sw.Elapsed.TotalMilliseconds
    }
    return $results
}
