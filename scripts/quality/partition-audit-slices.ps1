#requires -Version 7.0
<#
.SYNOPSIS
    S3556: partition the shipped Kotlin of app_v2 and wear into capped, risk-ordered audit slices.

.DESCRIPTION
    A whole-tree audit cannot be read in one session (about 520k shipped lines), so it is cut into
    slices that one ticket each can audit. This tool is the cut: deterministic, so two runs on the
    same tree print the same manifest, and mechanical, so no file is forgotten and none is counted
    twice - the self-check refuses to write otherwise.

    Enumeration: every src/<set>/java|kotlin/**/*.kt of app_v2 and wear. Test source sets
    (^(test|androidTest|benchmark), ^test[A-Z]) are excluded unless -IncludeTests; the debug set and
    any set ending in Debug are excluded unless -IncludeDebug. The release set ships and stays.

    Partition, per (module, source set): a package whose subtree fits both caps is one slice;
    otherwise its direct files are cut alphabetically into balanced -a, -b, .. parts (as few parts
    as the caps allow, with the lines spread evenly, so no part is a two-file remainder) and each
    child package recurses; small sibling results are then merged in ordinal order while the result
    still fits both caps. A source set other than main is partitioned from its own root; a set that
    is small on its own (at most a quarter of -MaxLoc) is merged whole with its small neighbours
    into one slice, never split across two, so a reviewer still reads one set's contract in one
    sitting without a two-file ticket per disabled stub.

    Risk per slice = (listener registrations + coroutine launches + shared mutable state + `!!`
    + files at or above 600 lines) per thousand lines, with the divisor floored at a quarter of
    -MaxLoc so a tiny slice cannot outrank a large one on one signal. Slices are ordered by risk
    descending; the order is baked into the index and name, so the fan-out creates queue rows in it.

    Baseline share per file: lint = entries of <module>/lint-baseline.xml whose file attribute is
    the file; detekt = entries of config/detekt/baseline-<module>.xml whose second segment is the
    file's basename. Detekt names files by basename only, so a slice is flagged detektAmbiguous when
    one of its baselined basenames occurs more than once anywhere in the module's source sets,
    excluded sets included - the count is then an upper bound, not a share. A baseline that is
    absent counts as zero and is noted; one that exists and cannot be parsed is a cannot-verify.

.PARAMETER Id
    The umbrella ticket (S####). Names the manifest's parent and the default JSON location.

.PARAMETER RepoRoot
    Repository root. Defaults to two levels above this script; the contract suite passes a fixture.

.PARAMETER MaxFiles
    Cap on files per slice (default 40).

.PARAMETER MaxLoc
    Cap on raw lines per slice (default 8000). A single file above the cap gets a slice of its own.

.PARAMETER IncludeTests
    Also partition the test source sets (class B).

.PARAMETER IncludeDebug
    Also partition the debug source sets (class B).

.PARAMETER FileList
    A file holding repo-relative .kt paths, one per line. Only those files are partitioned - the
    input for a tail slice built from the roll-up's uncovered files.

.PARAMETER OutJson
    Manifest path. Defaults to <paths.tempDir from .sza-profile.json>/<Id>/audit-slices.json.

.PARAMETER OutMarkdown
    Optional human-readable manifest (one table plus the coverage line).

.PARAMETER Quiet
    Print only the coverage line and refusals.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/partition-audit-slices.ps1 -Id S3556 -OutMarkdown PLAN/S3556_full-code-audit-pre-release/research/03__audit-slices.md

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - manifest written; every enumerated file sits in exactly one slice.
      1 - self-check failed: a file in no slice or in two; nothing written.
      2 - cannot verify: a module root, the profile or a present baseline cannot be read, -FileList
          names a missing file, or an unexpected error ended the run.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [int] $MaxFiles = 40,
    [int] $MaxLoc = 8000,
    [switch] $IncludeTests,
    [switch] $IncludeDebug,
    [string] $FileList,
    [string] $OutJson,
    [string] $OutMarkdown,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# An unexpected exception is a cannot-verify, never the self-check's exit 1: the two codes carry
# different remedies and a caller must be able to tell them apart.
trap {
    Write-Host "partition-audit-slices: unexpected error - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

$script:modules = @(
    [pscustomobject]@{ Name = 'app_v2'; Short = 'app';  BasePackage = 'com/sza/fastmediasorter' },
    [pscustomobject]@{ Name = 'wear';   Short = 'wear'; BasePackage = 'com/sza/fastmediasorter/wear' }
)
$script:ordinal = [System.StringComparer]::Ordinal

# The same four signal regexes measure-hotspots.ps1 scores with, plus the `!!` count; kept as one
# table so a new signal is one row here and nothing in the partition changes.
$script:signalRx = [ordered]@{
    listeners   = [regex]::new('set[A-Za-z]*Listener\(|addListener\(|\.observe\(|\.observeForever\(|registerReceiver\(|registerForActivityResult\(|addCallback\(|addTextChangedListener\(', 'Compiled')
    coroutines  = [regex]::new('\.launch\s*\(|\.async\s*\(|GlobalScope', 'Compiled')
    sharedState = [regex]::new('synchronized\s*\(|Mutex\(|@Volatile|Atomic(Integer|Long|Boolean|Reference)|MutableStateFlow\(|MutableSharedFlow\(|MutableLiveData\(', 'Compiled')
    nonNull     = [regex]::new('!!', 'Compiled')
}
$script:largeFileFloor = 600
$script:newlineRx = [regex]::new('\n', 'Compiled')
$script:smallSetLoc = [math]::Max(1, [int][math]::Floor($MaxLoc / 4))
$script:riskFloorLoc = $script:smallSetLoc

function Write-Refusal([string] $Text) { Write-Host "partition-audit-slices: $Text" -ForegroundColor Red }

function Get-SourceSetExcluded([string] $SetName) {
    if (-not $IncludeTests -and ($SetName -cmatch '^(test|androidTest|benchmark)' -or $SetName -cmatch '^test[A-Z]')) { return $true }
    if (-not $IncludeDebug -and ($SetName -ceq 'debug' -or $SetName -cmatch 'Debug$')) { return $true }
    return $false
}

function Get-LineCount([string] $Text) {
    if ($Text.Length -eq 0) { return 0 }
    $n = $script:newlineRx.Matches($Text).Count
    if (-not $Text.EndsWith("`n")) { $n++ }
    return $n
}

function New-FileRecord([string] $RelPath, [string] $Module, [string] $SourceSet, [string] $Package) {
    $full = Join-Path $RepoRoot ($RelPath -replace '/', [IO.Path]::DirectorySeparatorChar)
    $text = [IO.File]::ReadAllText($full)
    $loc = Get-LineCount $text
    $signals = [ordered]@{}
    $sum = 0
    foreach ($key in @($script:signalRx.Keys)) {
        $c = $script:signalRx[$key].Matches($text).Count
        $signals[$key] = $c
        $sum += $c
    }
    $large = if ($loc -ge $script:largeFileFloor) { 1 } else { 0 }
    $signals['largeFile'] = $large
    $sum += $large
    return [pscustomobject]@{
        Path      = $RelPath
        Module    = $Module
        SourceSet = $SourceSet
        Package   = $Package
        BaseName  = [IO.Path]::GetFileName($RelPath)
        Loc       = $loc
        Signals   = $signals
        SignalSum = $sum
        Lint      = 0
        Detekt    = 0
    }
}

# --- enumeration -------------------------------------------------------------------------------

$records = [System.Collections.Generic.List[object]]::new()
# Every basename in every source set of the module, excluded sets included: the detekt baseline
# names files by basename, and a release/ file that shares its name with a debug/ file must not
# inherit the other's entries unnoticed.
$baseNameCount = @{}

foreach ($module in $script:modules) {
    $srcRoot = Join-Path $RepoRoot "$($module.Name)/src"
    if (-not (Test-Path -LiteralPath $srcRoot -PathType Container)) {
        if ($FileList) { continue }
        Write-Refusal "module root '$($module.Name)/src' is missing under $RepoRoot."; exit 2
    }
    $sets = @(Get-ChildItem -LiteralPath $srcRoot -Directory | Sort-Object -Property Name -Culture '' -CaseSensitive)
    foreach ($set in $sets) {
        foreach ($langRoot in @('java', 'kotlin')) {
            $root = Join-Path $set.FullName $langRoot
            if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
            $files = @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt')
            foreach ($f in $files) {
                $bk = "$($module.Name)|$($f.Name)"
                if ($baseNameCount.ContainsKey($bk)) { $baseNameCount[$bk]++ } else { $baseNameCount[$bk] = 1 }
            }
            if ($FileList -or (Get-SourceSetExcluded $set.Name)) { continue }
            foreach ($f in $files) {
                $relToRoot = $f.FullName.Substring($root.Length).TrimStart('\', '/') -replace '\\', '/'
                $pkg = if ($relToRoot.Contains('/')) { $relToRoot.Substring(0, $relToRoot.LastIndexOf('/')) } else { '' }
                $rel = "$($module.Name)/src/$($set.Name)/$langRoot/$relToRoot"
                $records.Add((New-FileRecord $rel $module.Name $set.Name $pkg))
            }
        }
    }
}

if ($FileList) {
    if (-not (Test-Path -LiteralPath $FileList -PathType Leaf)) { Write-Refusal "-FileList '$FileList' does not exist."; exit 2 }
    $pathRx = [regex]'^(app_v2|wear)/src/([^/]+)/(?:java|kotlin)/(?:(.+)/)?[^/]+\.kt$'
    foreach ($raw in [IO.File]::ReadAllLines($FileList)) {
        $rel = $raw.Trim() -replace '\\', '/'
        if (-not $rel) { continue }
        $m = $pathRx.Match($rel)
        if (-not $m.Success) { Write-Refusal "-FileList entry '$rel' is not a module source path."; exit 2 }
        if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $rel) -PathType Leaf)) { Write-Refusal "-FileList entry '$rel' does not exist."; exit 2 }
        $records.Add((New-FileRecord $rel $m.Groups[1].Value $m.Groups[2].Value $m.Groups[3].Value))
    }
}

if ($records.Count -eq 0) { Write-Refusal 'no Kotlin file enumerated - nothing to partition.'; exit 2 }

# --- profile and baselines ---------------------------------------------------------------------

$profilePath = Join-Path $RepoRoot '.sza-profile.json'
if (-not (Test-Path -LiteralPath $profilePath -PathType Leaf)) { Write-Refusal ".sza-profile.json is missing under $RepoRoot."; exit 2 }
try { $profile = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json }
catch { Write-Refusal ".sza-profile.json cannot be parsed: $($_.Exception.Message)"; exit 2 }
$tempDir = if ($profile.paths -and $profile.paths.tempDir) { [string]$profile.paths.tempDir } else { 'temp' }
if (-not $OutJson) { $OutJson = Join-Path $RepoRoot "$tempDir/$Id/audit-slices.json" }

$notes = [System.Collections.Generic.List[string]]::new()
$lintByPath = @{}
$detektByName = @{}
foreach ($module in $script:modules) {
    $lintPath = Join-Path $RepoRoot "$($module.Name)/lint-baseline.xml"
    if (Test-Path -LiteralPath $lintPath -PathType Leaf) {
        try {
            $lintText = [IO.File]::ReadAllText($lintPath)
            foreach ($m in [regex]::Matches($lintText, 'file="([^"]+)"')) {
                $key = "$($module.Name)/" + ($m.Groups[1].Value -replace '\\', '/')
                if ($lintByPath.ContainsKey($key)) { $lintByPath[$key]++ } else { $lintByPath[$key] = 1 }
            }
        }
        catch { Write-Refusal "lint baseline '$lintPath' cannot be read: $($_.Exception.Message)"; exit 2 }
    }
    else { $notes.Add("note: $($module.Name)/lint-baseline.xml absent - lint share counted as 0") }

    $detektPath = Join-Path $RepoRoot "config/detekt/baseline-$($module.Name).xml"
    if (Test-Path -LiteralPath $detektPath -PathType Leaf) {
        try {
            $detektText = [IO.File]::ReadAllText($detektPath)
            foreach ($m in [regex]::Matches($detektText, '<ID>([^<]+)</ID>')) {
                $parts = $m.Groups[1].Value.Split(':', 3)
                if ($parts.Length -lt 2) { continue }
                $name = $parts[1]
                $dollar = $name.IndexOf('$')
                if ($dollar -ge 0) { $name = $name.Substring(0, $dollar) }
                $key = "$($module.Name)|$name"
                if ($detektByName.ContainsKey($key)) { $detektByName[$key]++ } else { $detektByName[$key] = 1 }
            }
        }
        catch { Write-Refusal "detekt baseline '$detektPath' cannot be read: $($_.Exception.Message)"; exit 2 }
    }
    else { $notes.Add("note: config/detekt/baseline-$($module.Name).xml absent - detekt share counted as 0") }
}

foreach ($r in $records) {
    if ($lintByPath.ContainsKey($r.Path)) { $r.Lint = $lintByPath[$r.Path] }
    $dk = "$($r.Module)|$($r.BaseName)"
    if ($detektByName.ContainsKey($dk)) { $r.Detekt = $detektByName[$dk] }
}

# --- partition ---------------------------------------------------------------------------------

function New-Node([string] $Package) {
    return [pscustomobject]@{
        Package  = $Package
        Files    = [System.Collections.Generic.List[object]]::new()
        Children = [System.Collections.Generic.SortedDictionary[string, object]]::new($script:ordinal)
    }
}

function Add-ToTree($Root, $Record) {
    $node = $Root
    if ($Record.Package) {
        $walk = ''
        foreach ($seg in $Record.Package.Split('/')) {
            $walk = if ($walk) { "$walk/$seg" } else { $seg }
            if (-not $node.Children.ContainsKey($seg)) { $node.Children[$seg] = New-Node $walk }
            $node = $node.Children[$seg]
        }
    }
    $node.Files.Add($Record)
}

function Get-SubtreeFiles($Node, [System.Collections.Generic.List[object]] $Into) {
    foreach ($f in $Node.Files) { $Into.Add($f) }
    foreach ($key in $Node.Children.Keys) { Get-SubtreeFiles $Node.Children[$key] $Into }
}

function New-Slice([System.Collections.Generic.List[object]] $Files, [string] $Chunk) {
    $sorted = @($Files | Sort-Object -Property Path -Culture '' -CaseSensitive)
    $packages = @($sorted | ForEach-Object { $_.Package } | Select-Object -Unique | Sort-Object -Culture '' -CaseSensitive)
    $sets = @($sorted | ForEach-Object { $_.SourceSet } | Select-Object -Unique | Sort-Object -Culture '' -CaseSensitive)
    $loc = 0; foreach ($f in $sorted) { $loc += $f.Loc }
    return [pscustomobject]@{
        Files    = [System.Collections.Generic.List[object]]::new([object[]]$sorted)
        Packages = [System.Collections.Generic.List[string]]::new([string[]]$packages)
        Sets     = [System.Collections.Generic.List[string]]::new([string[]]$sets)
        Module   = $sorted[0].Module
        Loc      = $loc
        Chunk    = $Chunk
    }
}

function Test-WithinCaps([int] $Files, [int] $Loc) { return ($Files -le $MaxFiles -and $Loc -le $MaxLoc) }

# Balanced cut of one package's direct files: the fewest parts the caps allow, lines spread evenly,
# so an alphabetical tail of two files never becomes a slice of its own. A single file above the
# line cap is its own part rather than a refusal - the audit still has to read it.
function Split-Balanced([object[]] $Sorted, [System.Collections.Generic.List[object]] $Into) {
    $total = 0; foreach ($f in $Sorted) { $total += $f.Loc }
    $kMin = [math]::Max([math]::Ceiling($Sorted.Count / $MaxFiles), [math]::Ceiling($total / $MaxLoc))
    $kMin = [int][math]::Max(1, $kMin)
    for ($k = $kMin; $k -le $Sorted.Count; $k++) {
        $chunks = [System.Collections.Generic.List[object]]::new()
        $current = [System.Collections.Generic.List[object]]::new()
        $running = 0
        foreach ($f in $Sorted) {
            $current.Add($f); $running += $f.Loc
            if ($chunks.Count -lt ($k - 1) -and $running -ge ($total * ($chunks.Count + 1) / $k)) {
                $chunks.Add($current); $current = [System.Collections.Generic.List[object]]::new()
            }
        }
        if ($current.Count -gt 0) { $chunks.Add($current) }
        $ok = $true
        foreach ($c in $chunks) {
            $cl = 0; foreach ($f in $c) { $cl += $f.Loc }
            if ($c.Count -gt $MaxFiles -or ($cl -gt $MaxLoc -and $c.Count -gt 1)) { $ok = $false; break }
        }
        if ($ok) { foreach ($c in $chunks) { $Into.Add($c) }; return }
    }
    foreach ($f in $Sorted) { $one = [System.Collections.Generic.List[object]]::new(); $one.Add($f); $Into.Add($one) }
}

function Split-Node($Node, [System.Collections.Generic.List[object]] $Into) {
    $all = [System.Collections.Generic.List[object]]::new()
    Get-SubtreeFiles $Node $all
    if ($all.Count -eq 0) { return }
    $allLoc = 0; foreach ($f in $all) { $allLoc += $f.Loc }
    if (Test-WithinCaps $all.Count $allLoc) { $Into.Add((New-Slice $all '')); return }

    $local = [System.Collections.Generic.List[object]]::new()
    $direct = @($Node.Files | Sort-Object -Property BaseName, Path -Culture '' -CaseSensitive)
    if ($direct.Count -gt 0) {
        $chunks = [System.Collections.Generic.List[object]]::new()
        Split-Balanced $direct $chunks
        if ($chunks.Count -eq 1) { $local.Add((New-Slice $chunks[0] '')) }
        else {
            $letter = 0
            foreach ($chunk in $chunks) {
                $local.Add((New-Slice $chunk ([string][char](97 + $letter))))
                $letter++
            }
        }
    }
    foreach ($key in $Node.Children.Keys) { Split-Node $Node.Children[$key] $local }

    # Merge pass: consecutive small results under this node fuse while both caps hold. Chunked
    # slices are never fused - they already carry a part letter that names one package.
    $pending = $null
    foreach ($slice in $local) {
        if ($slice.Chunk) {
            if ($pending) { $Into.Add($pending); $pending = $null }
            $Into.Add($slice); continue
        }
        if ($pending -and (Test-WithinCaps ($pending.Files.Count + $slice.Files.Count) ($pending.Loc + $slice.Loc))) {
            $merged = [System.Collections.Generic.List[object]]::new($pending.Files)
            foreach ($f in $slice.Files) { $merged.Add($f) }
            $pending = New-Slice $merged ''
        }
        else {
            if ($pending) { $Into.Add($pending) }
            $pending = $slice
        }
    }
    if ($pending) { $Into.Add($pending) }
}

$groups = @($records | Group-Object -Property Module, SourceSet | Sort-Object -Property Name -Culture '' -CaseSensitive)
$rawSlices = [System.Collections.Generic.List[object]]::new()
$smallSetSlices = [System.Collections.Generic.List[object]]::new()
foreach ($g in $groups) {
    $first = $g.Group[0]
    $root = New-Node ''
    foreach ($r in $g.Group) { Add-ToTree $root $r }
    $groupSlices = [System.Collections.Generic.List[object]]::new()
    Split-Node $root $groupSlices
    $groupLoc = 0; foreach ($r in $g.Group) { $groupLoc += $r.Loc }
    $isSmallSet = ($first.SourceSet -cne 'main') -and ($groupSlices.Count -eq 1) -and ($groupLoc -le $script:smallSetLoc)
    foreach ($s in $groupSlices) {
        if ($isSmallSet) { $smallSetSlices.Add($s) } else { $rawSlices.Add($s) }
    }
}

# Small non-main sets fuse whole, in ordinal set order per module, while both caps hold.
foreach ($module in $script:modules) {
    $pending = $null
    $own = @($smallSetSlices | Where-Object { $_.Module -ceq $module.Name } | Sort-Object -Property { $_.Sets[0] } -Culture '' -CaseSensitive)
    foreach ($slice in $own) {
        if ($pending -and (Test-WithinCaps ($pending.Files.Count + $slice.Files.Count) ($pending.Loc + $slice.Loc))) {
            $merged = [System.Collections.Generic.List[object]]::new($pending.Files)
            foreach ($f in $slice.Files) { $merged.Add($f) }
            $pending = New-Slice $merged ''
        }
        else {
            if ($pending) { $rawSlices.Add($pending) }
            $pending = $slice
        }
    }
    if ($pending) { $rawSlices.Add($pending) }
}

# --- naming, risk, order -----------------------------------------------------------------------

function Get-CommonPackage([System.Collections.Generic.List[string]] $Packages) {
    if ($Packages.Count -eq 0) { return '' }
    $segments = $Packages[0].Split('/')
    $common = $segments.Length
    foreach ($p in $Packages) {
        $other = $p.Split('/')
        $i = 0
        while ($i -lt $common -and $i -lt $other.Length -and $other[$i] -ceq $segments[$i]) { $i++ }
        $common = $i
    }
    if ($common -eq 0) { return '' }
    return ($segments[0..($common - 1)] -join '/')
}

function Get-ModuleShort([string] $Module, [string] $SourceSet) {
    $m = $script:modules | Where-Object { $_.Name -ceq $Module } | Select-Object -First 1
    if ($SourceSet -ceq 'main') { return $m.Short }
    return "$($m.Short)-$($SourceSet.ToLowerInvariant())"
}

function Get-PackageSlug([string] $Module, [string] $CommonPackage) {
    $m = $script:modules | Where-Object { $_.Name -ceq $Module } | Select-Object -First 1
    $rel = $CommonPackage
    if ($rel.StartsWith($m.BasePackage)) { $rel = $rel.Substring($m.BasePackage.Length).TrimStart('/') }
    if (-not $rel) { return 'root' }
    return ($rel.ToLowerInvariant() -replace '[^a-z0-9/]', '' -replace '/', '-')
}

function Get-Leaf([string] $Package, [string] $Common) {
    $rel = $Package
    if ($Common -and $rel.StartsWith($Common)) { $rel = $rel.Substring($Common.Length).TrimStart('/') }
    if (-not $rel) { return '' }
    $seg = $rel.Split('/')[0]
    return ($seg.ToLowerInvariant() -replace '[^a-z0-9]', '')
}

foreach ($s in $rawSlices) {
    $sum = 0; foreach ($f in $s.Files) { $sum += $f.SignalSum }
    $risk = [math]::Round(($sum / [math]::Max($s.Loc, $script:riskFloorLoc)) * 1000, 1)
    if ($s.Sets.Count -gt 1) {
        # A fused set slice is named by its first and last set; the common package means nothing there.
        $common = ''
        $preName = "$(Get-ModuleShort $s.Module 'main')-sets-$($s.Sets[0].ToLowerInvariant())-$($s.Sets[$s.Sets.Count - 1].ToLowerInvariant())"
    }
    else {
        $common = Get-CommonPackage $s.Packages
        $preName = "$(Get-ModuleShort $s.Module $s.Sets[0])-$(Get-PackageSlug $s.Module $common)"
        if ($s.Packages.Count -gt 1) {
            # A merged slice names its first and last child so the reader sees the range at a glance.
            $leaves = @($s.Packages | ForEach-Object { Get-Leaf $_ $common } | Where-Object { $_ })
            if ($leaves.Count -gt 0) {
                $preName += "-$($leaves[0])"
                if ($leaves[$leaves.Count - 1] -cne $leaves[0]) { $preName += "-$($leaves[$leaves.Count - 1])" }
            }
        }
    }
    if ($s.Chunk) { $preName += "-$($s.Chunk)" }
    $s | Add-Member -NotePropertyName Risk -NotePropertyValue $risk
    $s | Add-Member -NotePropertyName SignalSum -NotePropertyValue $sum
    $s | Add-Member -NotePropertyName CommonPackage -NotePropertyValue $common
    $s | Add-Member -NotePropertyName PreName -NotePropertyValue $preName
}

# Two slices can still share a pre-name; a part letter in path order disambiguates.
$byPreName = @($rawSlices | Group-Object -Property PreName | Where-Object { $_.Count -gt 1 })
foreach ($g in $byPreName) {
    $letter = 0
    foreach ($s in ($g.Group | Sort-Object -Property { $_.Files[0].Path } -Culture '' -CaseSensitive)) {
        $s.PreName = "$($s.PreName)-$([string][char](97 + $letter))"
        $letter++
    }
}

$ordered = @($rawSlices | Sort-Object -Property @{ Expression = 'Risk'; Descending = $true }, @{ Expression = 'PreName'; Descending = $false } -Culture '' -CaseSensitive)
$width = [math]::Max(2, ([string]$ordered.Count).Length)
$index = 0
foreach ($s in $ordered) {
    $index++
    $nn = ([string]$index).PadLeft($width, '0')
    $prefix = "audit-slice-$nn-"
    $body = $s.PreName
    while (($prefix.Length + $body.Length) -gt 60 -and $body.Contains('-')) {
        $cut = $body.LastIndexOf('-')
        $tail = $body.Substring($cut + 1)
        $body = $body.Substring(0, $cut)
        # A part letter must survive the trim, or two chunks of one package collapse into one name.
        if ($tail.Length -eq 1 -and $body.Contains('-')) { $body = ($body.Substring(0, $body.LastIndexOf('-'))) + "-$tail" }
    }
    $s | Add-Member -NotePropertyName Index -NotePropertyValue $index
    $s | Add-Member -NotePropertyName Name -NotePropertyValue ($prefix + $body)
}

$sliceByFirstPackage = @{}
foreach ($s in $ordered) {
    $fp = "$($s.Module)|$($s.Sets -join '+')|$($s.Packages[0])"
    if (-not $sliceByFirstPackage.ContainsKey($fp)) { $sliceByFirstPackage[$fp] = [System.Collections.Generic.List[string]]::new() }
    $sliceByFirstPackage[$fp].Add($s.Name)
}

# --- self-check --------------------------------------------------------------------------------

$seen = [System.Collections.Generic.Dictionary[string, int]]::new($script:ordinal)
$duplicated = [System.Collections.Generic.List[string]]::new()
foreach ($s in $ordered) {
    foreach ($f in $s.Files) {
        if ($seen.ContainsKey($f.Path)) { $duplicated.Add($f.Path); $seen[$f.Path]++ } else { $seen[$f.Path] = 1 }
    }
}
$uncovered = [System.Collections.Generic.List[string]]::new()
foreach ($r in $records) { if (-not $seen.ContainsKey($r.Path)) { $uncovered.Add($r.Path) } }
$totalLoc = 0; foreach ($r in $records) { $totalLoc += $r.Loc }
$coverageLine = "Coverage: $($records.Count) files in $($ordered.Count) slices, $($uncovered.Count) uncovered, $($duplicated.Count) duplicated"
if ($uncovered.Count -gt 0 -or $duplicated.Count -gt 0) {
    Write-Refusal "self-check failed - $coverageLine"
    foreach ($p in $uncovered) { Write-Host "  uncovered: $p" }
    foreach ($p in $duplicated) { Write-Host "  duplicated: $p" }
    exit 1
}

# --- outputs -----------------------------------------------------------------------------------

$sliceObjects = [System.Collections.Generic.List[object]]::new()
foreach ($s in $ordered) {
    $sig = [ordered]@{}
    foreach ($key in @($script:signalRx.Keys) + @('largeFile')) { $sig[$key] = 0 }
    $lint = 0; $detekt = 0; $ambiguous = $false
    $fileObjects = [System.Collections.Generic.List[object]]::new()
    foreach ($f in $s.Files) {
        foreach ($key in @($sig.Keys)) { $sig[$key] += [int]$f.Signals[$key] }
        $lint += $f.Lint; $detekt += $f.Detekt
        $bk = "$($f.Module)|$($f.BaseName)"
        if ($f.Detekt -gt 0 -and $baseNameCount.ContainsKey($bk) -and $baseNameCount[$bk] -gt 1) { $ambiguous = $true }
        $fileObjects.Add([ordered]@{ path = $f.Path; loc = $f.Loc; signals = $f.SignalSum; lint = $f.Lint; detekt = $f.Detekt })
    }
    $fp = "$($s.Module)|$($s.Sets -join '+')|$($s.Packages[0])"
    $siblings = @($sliceByFirstPackage[$fp] | Where-Object { $_ -cne $s.Name })
    $partNote = if ($s.Chunk) { " (part $($s.Chunk))" } else { '' }
    $setLabel = $s.Sets -join '+'
    $title = "$($s.Module)/$setLabel " + ($s.Packages -join ', ') + $partNote
    $sliceObjects.Add([ordered]@{
        index           = $s.Index
        name            = $s.Name
        title           = $title
        module          = $s.Module
        sourceSet       = $setLabel
        packages        = @($s.Packages)
        fileCount       = $s.Files.Count
        loc             = $s.Loc
        signals         = $sig
        risk            = $s.Risk
        lint            = $lint
        detekt          = $detekt
        detektAmbiguous = $ambiguous
        siblings        = @($siblings)
        files           = @($fileObjects)
    })
}

$manifest = [ordered]@{
    schema      = 'audit-slices/1'
    parent      = $Id
    generatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    params      = [ordered]@{ maxFiles = $MaxFiles; maxLoc = $MaxLoc; includeTests = [bool]$IncludeTests; includeDebug = [bool]$IncludeDebug; fileList = [string]$FileList }
    tree        = [ordered]@{ files = $records.Count; loc = $totalLoc }
    slices      = @($sliceObjects)
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
$jsonDir = Split-Path -Parent $OutJson
if ($jsonDir -and -not (Test-Path -LiteralPath $jsonDir)) { New-Item -ItemType Directory -Force -Path $jsonDir | Out-Null }
[IO.File]::WriteAllText($OutJson, ($manifest | ConvertTo-Json -Depth 8), $utf8)

if ($OutMarkdown) {
    $md = [System.Collections.Generic.List[string]]::new()
    $md.Add("# Audit slices - $Id")
    $md.Add('')
    $md.Add("**Generated:** $($manifest.generatedAt)")
    $md.Add("**Parameters:** maxFiles $MaxFiles, maxLoc $MaxLoc, includeTests $([bool]$IncludeTests), includeDebug $([bool]$IncludeDebug)$(if ($FileList) { ", fileList $FileList" })")
    $md.Add("**Tree:** $($records.Count) files, $totalLoc lines")
    foreach ($n in $notes) { $md.Add("**$n**") }
    $md.Add('')
    $md.Add('| # | Name | Module/set | Files | LOC | Risk | Lint | Detekt |')
    $md.Add('| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |')
    foreach ($o in $sliceObjects) {
        $amb = if ($o.detektAmbiguous) { '*' } else { '' }
        $md.Add("| $($o.index) | $($o.name) | $($o.module)/$($o.sourceSet) | $($o.fileCount) | $($o.loc) | $($o.risk) | $($o.lint) | $($o.detekt)$amb |")
    }
    $md.Add('')
    $md.Add('Detekt counts are by file basename; `*` marks a slice holding a baselined basename that occurs more than once in the module, so its count is an upper bound.')
    $md.Add('')
    $md.Add($coverageLine)
    $mdDir = Split-Path -Parent $OutMarkdown
    if ($mdDir -and -not (Test-Path -LiteralPath $mdDir)) { New-Item -ItemType Directory -Force -Path $mdDir | Out-Null }
    [IO.File]::WriteAllText($OutMarkdown, (($md -join "`n") + "`n"), $utf8)
}

if (-not $Quiet) {
    foreach ($n in $notes) { Write-Host $n }
    Write-Host ("{0,3}  {1,-58} {2,5} {3,7} {4,6} {5,5} {6,6}" -f '#', 'NAME', 'FILES', 'LOC', 'RISK', 'LINT', 'DETEKT')
    foreach ($o in $sliceObjects) {
        Write-Host ("{0,3}  {1,-58} {2,5} {3,7} {4,6} {5,5} {6,6}" -f $o.index, $o.name, $o.fileCount, $o.loc, $o.risk, $o.lint, $o.detekt)
    }
    Write-Host "manifest: $OutJson"
    if ($OutMarkdown) { Write-Host "markdown: $OutMarkdown" }
}
Write-Host $coverageLine
exit 0
