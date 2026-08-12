<#
.SYNOPSIS
    S1568: one definition of "is this string resource name referenced", shared by the audit report,
    the removal tool and the ratchet gate.

.DESCRIPTION
    Three primitives over ONE module:

      Get-DeclaredResourceNames    - what a single strings file declares, by kind.
      Get-ReferencedResourceNames  - every resource name mentioned anywhere under <module>/src.
      Get-UnreferencedResourceNames - the difference, plus the counts a caller reports.

    Two design points are load-bearing and must not be "simplified" away.

    Module scope (strategic ADR-1). Liveness is decided inside one module and never across the
    repository. app_v2 and wear are separate resource namespaces with no dependency between them,
    each with its own generated R, so a name declared in one is unreachable from the other. 15 names
    exist in both modules, and a scan spanning both reports every one of them as alive on the
    strength of the other module's reference - which is exactly how `test_connection` was first
    counted as live while being dead in app_v2.

    Source-set scope. The walk root is <module>/src, NOT <module>/src/main. 40 of app_v2's 41
    source-set directories are flavor, feature or test sets, and 216 names of values/strings.xml are
    referenced only from one of them. A main-only scan calls all 216 unreferenced, i.e. safe to
    delete (S1571).

    All three resource kinds are handled - <string>, <plurals> and <string-array> - on both sides:
    declarations and references. Matching only <string> is how a dead <plurals> stays invisible.

    Dot-source it:  . (Join-Path $PSScriptRoot 'lib/android-string-liveness.ps1')

.NOTES
    Pure PowerShell. No gradle, no git, no network. The library never exits; a consuming script owns
    its own exit contract.
#>

Set-StrictMode -Version Latest

# 'string-array' precedes 'string' in the alternation on purpose: "string" followed by "-" is a word
# boundary, so a leading `<string\b` alternative would claim `<string-array` and mislabel its kind.
$script:AndroidResourceDeclarationPattern =
    '<(?<kind>string-array|plurals|string)\b[^>]*\bname\s*=\s*"(?<name>[^"]+)"'

# The kind list is written ONCE. Two call shapes need a reference pattern - "find every name" for the
# audit and "find this one name, with locations" for the removal gate - and if each spelled its own
# alternation they would drift, which is precisely the divergence strategic ADR-2 forbids.
# Note `array`, not `string-array`: the reference forms are R.array./@array/, while the DECLARATION
# is <string-array>. Same resource, two spellings, and conflating them makes arrays unreachable.
$script:AndroidResourceReferenceKinds = 'string|plurals|array'

# Both reference forms in one pass. The trailing guard stops a name from matching a longer name that
# merely starts with it, so `open` is not kept alive by `open_with_title`.
$script:AndroidResourceReferencePattern =
    "(?:R\.(?:$script:AndroidResourceReferenceKinds)\.(?<code>[A-Za-z0-9_]+)|@(?:$script:AndroidResourceReferenceKinds)/(?<res>[A-Za-z0-9_]+))(?![A-Za-z0-9_])"

<#
.SYNOPSIS
    The declaration pattern for ONE resource name, capturing its kind in group 'kind'.

.DESCRIPTION
    Same alternation and same ordering as the bulk declaration scan, so a caller resolving "what kind
    of element is this name" can never disagree with the caller that counted it. The removal tool
    needs exactly this: it must know whether to delete a <string>, a <plurals> or a <string-array>
    before it builds the element regex.
#>
function New-ResourceDeclarationPattern {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name)

    $esc = [regex]::Escape($Name)
    return '<(?<kind>string-array|plurals|string)\b[^>]*\bname\s*=\s*"' + $esc + '"'
}

<#
.SYNOPSIS
    The reference pattern for ONE resource name, from the same kind list the bulk scan uses.
#>
function New-ResourceReferencePattern {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name)

    $esc = [regex]::Escape($Name)
    return "(?:R\.(?:$script:AndroidResourceReferenceKinds)\.$esc|@(?:$script:AndroidResourceReferenceKinds)/$esc)(?![A-Za-z0-9_])"
}

$script:AndroidReferenceScanExtensions = @('*.kt', '*.java', '*.xml')

<#
.SYNOPSIS
    Names declared by ONE strings file, mapped to their resource kind.

.PARAMETER ResDir
    A values directory, e.g. <module>/src/main/res/values.

.PARAMETER File
    Basename of the strings file inside it. Default strings.xml.

.OUTPUTS
    [ordered] name -> kind ('string' | 'plurals' | 'string-array'), in declaration order. Empty when
    the file does not exist, so a caller can distinguish "nothing declared" from a throw.
#>
function Get-DeclaredResourceNames {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ResDir,
        [string]$File = 'strings.xml'
    )

    $declared = [ordered]@{}
    $path = Join-Path $ResDir $File
    if (-not (Test-Path -LiteralPath $path)) { return $declared }

    $content = [System.IO.File]::ReadAllText($path)
    foreach ($m in [regex]::Matches($content, $script:AndroidResourceDeclarationPattern)) {
        $declared[$m.Groups['name'].Value] = $m.Groups['kind'].Value
    }
    return $declared
}

<#
.SYNOPSIS
    Every resource name referenced anywhere under one module's source root.

.PARAMETER SrcRoot
    <module>/src - the module source root, never a single source set.

.PARAMETER ScannedFileCount
    Optional [ref] receiving how many files were read, so a caller can report the denominator
    instead of walking the tree a second time to count it.

.OUTPUTS
    [System.Collections.Generic.HashSet[string]] of names. One walk, one read per file.
#>
function Get-ReferencedResourceNames {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$SrcRoot,
        [ref]$ScannedFileCount
    )

    $referenced = [System.Collections.Generic.HashSet[string]]::new()
    if ($ScannedFileCount) { $ScannedFileCount.Value = 0 }
    # `,` on every return path: PowerShell unrolls a returned collection into the pipeline, which hands
    # the caller an Object[] instead of the HashSet. That silently turns every O(1) .Contains into an
    # O(n) scan, and an EMPTY set unrolls to $null, so `$referenced.Count` throws under StrictMode.
    if (-not (Test-Path -LiteralPath $SrcRoot)) { return , $referenced }

    $files = @(Get-ChildItem -LiteralPath $SrcRoot -Recurse -File -Include $script:AndroidReferenceScanExtensions -ErrorAction SilentlyContinue)
    if ($ScannedFileCount) { $ScannedFileCount.Value = $files.Count }

    $rx = [regex]$script:AndroidResourceReferencePattern
    foreach ($f in $files) {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        foreach ($m in $rx.Matches($text)) {
            $name = if ($m.Groups['code'].Success) { $m.Groups['code'].Value } else { $m.Groups['res'].Value }
            [void]$referenced.Add($name)
        }
    }
    return , $referenced
}

<#
.SYNOPSIS
    Where ONE resource name is referenced, as repo-relative "path:line" strings.

.DESCRIPTION
    The locating counterpart of Get-ReferencedResourceNames, for a caller that must show a human WHY
    a removal was refused rather than merely that it was. Same kind list, same trailing guard, same
    walk root - so a name this returns empty for is exactly a name the bulk scan calls unreferenced.

    Cost note: this walks the tree per call. Removing many names in one run must build the bulk set
    once with Get-ReferencedResourceNames and use this only to explain the few refusals.

.PARAMETER SrcRoot
    <module>/src - the module source root, never a single source set.

.PARAMETER Name
    The resource name to locate.

.PARAMETER RepoRoot
    Optional, only to shorten printed paths. Defaults to no trimming.
#>
function Get-ResourceReferenceLocations {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$SrcRoot,
        [Parameter(Mandatory)][string]$Name,
        [string]$RepoRoot
    )

    if (-not (Test-Path -LiteralPath $SrcRoot)) { return @() }
    $rx = [regex](New-ResourceReferencePattern -Name $Name)

    $files = @(Get-ChildItem -LiteralPath $SrcRoot -Recurse -File -Include $script:AndroidReferenceScanExtensions -ErrorAction SilentlyContinue)
    $hits = [System.Collections.Generic.List[string]]::new()
    foreach ($f in $files) {
        $lineNo = 0
        foreach ($line in [System.IO.File]::ReadAllLines($f.FullName)) {
            $lineNo++
            if ($rx.IsMatch($line)) {
                $path = $f.FullName
                if ($RepoRoot -and $path.StartsWith($RepoRoot)) {
                    $path = $path.Substring($RepoRoot.Length).TrimStart('\', '/')
                }
                $hits.Add(('{0}:{1}' -f ($path -replace '\\', '/'), $lineNo))
            }
        }
    }
    return @($hits)
}

<#
.SYNOPSIS
    Declared names that nothing in the module references.

.PARAMETER Module
    Module directory name relative to the repository root, e.g. app_v2 or wear.

.PARAMETER File
    Strings file basename inside <module>/src/main/res/values. Default strings.xml.

.PARAMETER RepoRoot
    Repository root. Defaults to the parent of scripts/.

.OUTPUTS
    A result object: .Entries (Name + Kind, sorted by Name), .DeclaredCount, .ReferencedCount,
    .ScannedFileCount, .ResDir, .SrcRoot. Throws when the module resource directory is absent -
    a caller that cannot look must not report a clean count.
#>
function Get-UnreferencedResourceNames {
    [CmdletBinding()]
    param(
        [string]$Module = 'app_v2',
        [string]$File = 'strings.xml',
        [string]$RepoRoot
    )

    if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) }

    $resDir = Join-Path $RepoRoot (Join-Path $Module 'src/main/res/values')
    $srcRoot = Join-Path $RepoRoot (Join-Path $Module 'src')
    if (-not (Test-Path -LiteralPath $resDir)) { throw "Resource dir not found for module '$Module': $resDir" }
    if (-not (Test-Path -LiteralPath (Join-Path $resDir $File))) { throw "Strings file not found: $(Join-Path $resDir $File)" }

    $declared = Get-DeclaredResourceNames -ResDir $resDir -File $File
    $scanned = 0
    $referenced = Get-ReferencedResourceNames -SrcRoot $srcRoot -ScannedFileCount ([ref]$scanned)

    $entries = @(
        $declared.Keys |
        Where-Object { -not $referenced.Contains($_) } |
        Sort-Object |
        ForEach-Object { [pscustomobject]@{ Name = $_; Kind = $declared[$_] } }
    )

    return [pscustomobject]@{
        Entries          = $entries
        DeclaredCount    = $declared.Count
        ReferencedCount  = $referenced.Count
        ScannedFileCount = $scanned
        ResDir           = $resDir
        SrcRoot          = $srcRoot
    }
}
