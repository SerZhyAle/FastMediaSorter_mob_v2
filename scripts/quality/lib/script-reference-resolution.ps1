#requires -Version 7.0
<#
.SYNOPSIS
    Resolve a .ps1 token found in a file into the script path(s) it actually names (S2124).

.DESCRIPTION
    assert-script-references.ps1 once keyed its script table by bare file name, so every file
    sharing a name shared one entry: the tree holds 37 files called Run-Tests.ps1, and a single
    comment naming that word marked all 37 referenced. The key is a path now, which turns the
    question "is this name mentioned" into "which file does this token mean" - and that question
    has to be answered the way the repository actually writes a call.

    THE LADDER. Rules are tried in order and the first that resolves wins:

      1. SELF-DIRECTORY ANCHOR. `. "$PSScriptRoot\..\utils\get-device-abi.ps1"` reaches the token
         regex as `PSScriptRoot/../utils/get-device-abi.ps1`, because the regex cannot match `$`.
         Such a token is resolved against the mentioning file's own directory, expanding `..`.
      2. SIBLING. `& (Join-Path $PSScriptRoot 'validate.ps1')` reaches the regex as the bare word
         `validate.ps1` - the directory lives in a variable that is a separate argument. A bare
         token naming a script that sits in the mentioning file's own directory is that script.
      3. LONGEST RESOLVING SUFFIX. `scripts/guard.tests/Run-Tests.ps1` names exactly one file; a
         two-segment tail is enough when a full path is not written out. A candidate suffix
         containing `..` is skipped, because it cannot be compared to a repository-relative path.
      4. UNIQUE BARE NAME. A bare token whose name only one script in the tree carries is that
         script. This is the case that made the old by-name key work at all.
      5. AMBIGUOUS BARE NAME IS NOT EVIDENCE. A bare token carried by several scripts, with no
         sibling to disambiguate it, names none of them. This rule is the point of S2124: without
         it one comment closes an arbitrarily large group of files from the gate forever.

    WHY RULES 1-3 EXIST. They are not refinements, they are the price of the path key. Measured
    2026-08-27 on the live tree: without them the switch invented three orphans that are called
    every day - scripts/utils/get-device-abi.ps1, scripts/spec_catalog/validate.ps1 and
    scripts/all_features/_lib.ps1. With the full ladder and rule 5 disabled, the verdict reproduces
    the pre-S2124 baseline exactly, which is how the ladder was shown to invent nothing.

    AMBIGUITY IS REPORTED, NOT DISCARDED. A caller gets Ambiguous = $true with the candidate list,
    so it can say how many scripts are held up by nothing but an ambiguous mention instead of
    silently dropping the fact.

.NOTES
    Dot-sourced helper: defines functions and exits nothing.

    This header deliberately does not list its callers by path. A path written in a comment is
    indistinguishable from a call to the gate that reads this tree, so such a line vouches for a
    file without wiring it - which is the defect S2124 exists to remove, not one to reintroduce
    here. The gate and its test suite both dot-source this file; that wiring is the record.
#>

Set-StrictMode -Version Latest

# The token regex eats the leading '$', so a variable naming the script's own directory arrives as
# a plain first segment. These are the spellings this repository uses.
$script:SelfDirectorySegment = '(?i)^(PSScriptRoot|PSCommandPath|scriptRoot|scriptDir|thisDir|here)$'

<#
.SYNOPSIS
    Build the path/leaf index the resolver reads.
#>
function New-ScriptPathIndex {
    param([Parameter(Mandatory)] [AllowEmptyCollection()] [string[]] $RelativePaths)

    $byPath = @{}
    $byLeaf = @{}
    foreach ($p in $RelativePaths) {
        $norm = $p.Replace('\', '/')
        $byPath[$norm] = $true
        $leaf = $norm.Substring($norm.LastIndexOf('/') + 1)
        if (-not $byLeaf.ContainsKey($leaf)) {
            $byLeaf[$leaf] = New-Object System.Collections.Generic.List[string]
        }
        $byLeaf[$leaf].Add($norm)
    }
    return @{ ByPath = $byPath; ByLeaf = $byLeaf }
}

function Get-TokenSegments {
    param([string] $Token)
    return @($Token.Replace('\', '/') -split '/' | Where-Object { $_ -ne '' -and $_ -ne '.' })
}

# Walk the mentioning file's directory according to the token's middle segments. Returns $null when
# the walk climbs above the repository root, which means the token names nothing judgeable here.
function Resolve-AnchoredCandidate {
    param([string[]] $Segments, [string] $MentionDirectory)

    $current = $MentionDirectory
    for ($i = 1; $i -lt $Segments.Count - 1; $i++) {
        if ($Segments[$i] -eq '..') {
            if ([string]::IsNullOrEmpty($current)) { return $null }
            $slash = $current.LastIndexOf('/')
            $current = if ($slash -lt 0) { '' } else { $current.Substring(0, $slash) }
            continue
        }
        $current = if ([string]::IsNullOrEmpty($current)) { $Segments[$i] } else { "$current/$($Segments[$i])" }
    }
    $leaf = $Segments[$Segments.Count - 1]
    return $(if ([string]::IsNullOrEmpty($current)) { $leaf } else { "$current/$leaf" })
}

<#
.SYNOPSIS
    Resolve one .ps1 token into the script paths it names.
#>
function Resolve-ScriptTokenPaths {
    param(
        [Parameter(Mandatory)] [string] $Token,
        [AllowEmptyString()] [string] $MentionDirectory = '',
        [Parameter(Mandatory)] [hashtable] $Index
    )

    $empty = [pscustomobject]@{ Paths = @(); Ambiguous = $false }
    # @() is load-bearing under Set-StrictMode: a one-segment token returns a bare string, which
    # has no .Count, and the resolver would fail on exactly the bare names rule 5 exists to judge.
    $segments = @(Get-TokenSegments -Token $Token)
    if ($segments.Count -eq 0) { return $empty }

    $byPath = $Index.ByPath
    $byLeaf = $Index.ByLeaf
    $dir = $MentionDirectory.Replace('\', '/').Trim('/')
    $leaf = $segments[$segments.Count - 1]

    # 1. self-directory anchor. A miss falls through rather than answering "nothing": the token may
    #    still name a script by a suffix the anchor walk could not reach.
    if ($segments.Count -gt 1 -and $segments[0] -match $script:SelfDirectorySegment) {
        $candidate = Resolve-AnchoredCandidate -Segments $segments -MentionDirectory $dir
        if ($null -ne $candidate -and $byPath.ContainsKey($candidate)) {
            return [pscustomobject]@{ Paths = @($candidate); Ambiguous = $false }
        }
    }

    if (-not $byLeaf.ContainsKey($leaf)) { return $empty }

    # 2. sibling
    if ($segments.Count -eq 1) {
        $sibling = if ($dir -eq '') { $leaf } else { "$dir/$leaf" }
        if ($byPath.ContainsKey($sibling)) {
            return [pscustomobject]@{ Paths = @($sibling); Ambiguous = $false }
        }
    }

    # 3-4. longest resolving suffix, ending at the bare leaf
    $carriers = $byLeaf[$leaf]
    for ($start = 0; $start -lt $segments.Count; $start++) {
        $suffix = ($segments[$start..($segments.Count - 1)]) -join '/'
        if ($suffix -match '(^|/)\.\.(/|$)') { continue }
        $hits = @($carriers | Where-Object {
                $_ -ieq $suffix -or $_.EndsWith('/' + $suffix, [StringComparison]::OrdinalIgnoreCase)
            })
        if ($hits.Count -eq 0) { continue }
        # 5. an ambiguous bare name is not evidence; a longer suffix that still matches several
        #    files is equally ambiguous and is reported the same way.
        return [pscustomobject]@{ Paths = $hits; Ambiguous = ($hits.Count -gt 1) }
    }
    return $empty
}
