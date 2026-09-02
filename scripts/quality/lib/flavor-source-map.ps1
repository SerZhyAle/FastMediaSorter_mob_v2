#requires -Version 7.0
<#
.SYNOPSIS
    S1453: the one reader of the flavor / source-set mount map declared in app_v2/build.gradle.kts.

.DESCRIPTION
    Which flavor mounts which source set is stated in exactly one place - the `sourceSets` block of
    the module build file. Three checks need that map: the shared-test scope gate, the mount-mirror
    rule, and the unit-suite completeness gate. A copy of the map inside any of them would be a
    second place for it to drift, which is the very failure S1450 and S1433 kept repairing by hand.

    So the map is derived on every run and never stored. The parser is deliberately strict: every
    `directories.add("src/..")` line inside the block must be attributable to a set, and any line it
    cannot attribute is returned on `Unparsed` rather than skipped. A caller that finds `Unparsed`
    non-empty must report "could not verify" - a narrowed scan that still prints PASS is worse than
    no gate at all.

    This is a library: it defines functions, throws on a map it cannot read, and never calls `exit`.
    Turning a throw into a process exit code is the caller's decision, not this file's.

    Dot-source it:  . (Join-Path $PSScriptRoot 'lib/flavor-source-map.ps1')
                    $map = Get-FlavorSourceMap -RepoRoot $repoRoot
#>

# Strip a line comment without touching a "//" inside a quoted string - source-set paths are
# quoted and the surrounding comments carry ticket ids and URLs.
function Remove-GradleLineComment([string]$Line) {
    $inString = $false
    for ($i = 0; $i -lt $Line.Length; $i++) {
        $ch = $Line[$i]
        if ($ch -eq '"' -and ($i -eq 0 -or $Line[$i - 1] -ne '\')) {
            $inString = -not $inString
            continue
        }
        if (-not $inString -and $ch -eq '/' -and $i + 1 -lt $Line.Length -and $Line[$i + 1] -eq '/') {
            return $Line.Substring(0, $i)
        }
    }
    return $Line
}

function Get-GradleBraceDelta([string]$CodeOnly) {
    $open = ([regex]::Matches($CodeOnly, '\{')).Count
    $close = ([regex]::Matches($CodeOnly, '\}')).Count
    return $open - $close
}

<#
.SYNOPSIS
    0-based line range of a named block's body, or $null when the header is not found.
#>
function Get-GradleBlockRange {
    param(
        # AllowEmptyString, because a mandatory [string[]] otherwise refuses the whole array as
        # soon as one element is empty - and a build file is mostly blank lines.
        [Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines,
        [Parameter(Mandatory)][string]$HeaderPattern,
        [int]$From = 0
    )
    for ($i = $From; $i -lt $Lines.Count; $i++) {
        $code = Remove-GradleLineComment $Lines[$i]
        if ($code -notmatch $HeaderPattern) { continue }
        if ($code -notmatch '\{') { continue }

        $depth = Get-GradleBraceDelta $code
        for ($j = $i + 1; $j -lt $Lines.Count; $j++) {
            $depth += Get-GradleBraceDelta (Remove-GradleLineComment $Lines[$j])
            if ($depth -le 0) {
                return [pscustomobject]@{ Header = $i; Start = $i + 1; End = $j }
            }
        }
        throw "flavor-source-map: unbalanced braces after line $($i + 1) while reading '$HeaderPattern'."
    }
    return $null
}

<#
.SYNOPSIS
    Flavor names declared in the productFlavors block, in declaration order.
#>
function Get-GradleFlavorNames {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines,
        [Parameter(Mandatory)][int]$Start,
        [Parameter(Mandatory)][int]$End
    )
    $names = [System.Collections.Generic.List[string]]::new()
    for ($i = $Start; $i -le $End; $i++) {
        $code = Remove-GradleLineComment $Lines[$i]
        if ($code -match '^\s*create\(\s*"(?<n>[A-Za-z0-9_]+)"\s*\)\s*\{') { $names.Add($Matches['n']) }
    }
    return $names.ToArray()
}

# Unit-test set of a flavor: AGP derives it as test<Flavor>, capitalising the first letter only.
function Get-UnitTestSetName([string]$Flavor) {
    if ([string]::IsNullOrEmpty($Flavor)) { return $Flavor }
    return 'test' + $Flavor.Substring(0, 1).ToUpperInvariant() + $Flavor.Substring(1)
}

# Source-set name out of a mount path: "src/streamingEnabled/java" -> "streamingEnabled".
function Get-MountedSetName([string]$Path) {
    if ($Path -match '^src/(?<s>[A-Za-z0-9_]+)/') { return $Matches['s'] }
    return $null
}

<#
.SYNOPSIS
    Walk the sourceSets block and attribute every mount line to the set that declares it.

.DESCRIPTION
    Two syntaxes carry mounts in this build file and both must be understood: the explicit
    `getByName("name") { .. }` block, and a `listOf(..).forEach { v -> getByName(v) { .. } }` loop
    that mounts the same set into several targets at once. A mount inside an `if` block is recorded
    as conditional - a gradle property decides it, so nothing may assume it is present.
#>
function Get-GradleMountWalk {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines,
        [Parameter(Mandatory)][int]$Start,
        [Parameter(Mandatory)][int]$End
    )

    $mounts = [System.Collections.Generic.List[object]]::new()
    $unattributed = [System.Collections.Generic.List[object]]::new()
    $stack = [System.Collections.Generic.List[object]]::new()
    $pendingLoopNames = @()
    $depth = 0

    for ($i = $Start; $i -le $End; $i++) {
        $code = Remove-GradleLineComment $Lines[$i]
        $depthBefore = $depth

        if ($code -match 'listOf\(\s*(?<items>[^)]*)\)\s*\.forEach\s*\{') {
            $loopNames = @([regex]::Matches($Matches['items'], '"([A-Za-z0-9_]+)"') | ForEach-Object { $_.Groups[1].Value })
            $stack.Add([pscustomobject]@{ Kind = 'loop'; Names = $loopNames; Depth = $depthBefore })
            $pendingLoopNames = @()
        }
        elseif ($code -match '^\s*listOf\(\s*(?<items>[^)]*)\)\s*$') {
            $pendingLoopNames = @([regex]::Matches($Matches['items'], '"([A-Za-z0-9_]+)"') | ForEach-Object { $_.Groups[1].Value })
        }
        elseif ($pendingLoopNames.Count -gt 0 -and $code -match '^\s*\.forEach\s*\{') {
            $stack.Add([pscustomobject]@{ Kind = 'loop'; Names = $pendingLoopNames; Depth = $depthBefore })
            $pendingLoopNames = @()
        }
        else {
            if ($pendingLoopNames.Count -gt 0 -and $code -match '\S') {
                $pendingLoopNames = @()
            }
            if ($code -match 'getByName\(\s*"(?<n>[A-Za-z0-9_]+)"\s*\)\s*\{') {
                $stack.Add([pscustomobject]@{ Kind = 'target'; Names = @($Matches['n']); Depth = $depthBefore })
            }
            elseif ($code -match 'getByName\(\s*(?<v>[A-Za-z_][A-Za-z0-9_]*)\s*\)\s*\{') {
                $loop = $stack | Where-Object { $_.Kind -eq 'loop' } | Select-Object -Last 1
                $names = if ($loop) { $loop.Names } else { @() }
                $stack.Add([pscustomobject]@{ Kind = 'target'; Names = $names; Depth = $depthBefore })
            }
            elseif ($code -match '^\s*if\s*\(.*\)\s*\{') {
                $stack.Add([pscustomobject]@{ Kind = 'cond'; Names = @(); Depth = $depthBefore })
            }
        }

        if ($code -match '(?<kind>kotlin|res|assets)\.directories\.add\(\s*"(?<p>[^"]+)"\s*\)') {
            $kind = $Matches['kind']
            $path = $Matches['p']
            $target = $stack | Where-Object { $_.Kind -eq 'target' } | Select-Object -Last 1
            $setName = Get-MountedSetName $path
            if ($null -eq $target -or $target.Names.Count -eq 0 -or $null -eq $setName) {
                # Only a src/ path is a source-set mount; "schemas" and friends are assets, not sets.
                if ($path -like 'src/*') {
                    $unattributed.Add([pscustomobject]@{ Line = $i + 1; Text = $code.Trim() })
                }
            }
            else {
                $conditional = [bool]($stack | Where-Object { $_.Kind -eq 'cond' -and $_.Depth -ge $target.Depth })
                foreach ($owner in $target.Names) {
                    $mounts.Add([pscustomobject]@{
                            Owner       = $owner
                            Set         = $setName
                            Kind        = $kind
                            Conditional = $conditional
                            Line        = $i + 1
                        })
                }
            }
        }

        $depth += Get-GradleBraceDelta $code
        while ($stack.Count -gt 0 -and $depth -le $stack[$stack.Count - 1].Depth) {
            $stack.RemoveAt($stack.Count - 1)
        }
    }

    return [pscustomobject]@{ Mounts = $mounts.ToArray(); Unattributed = $unattributed.ToArray() }
}

<#
.SYNOPSIS
    Repo-relative source roots one flavor's unit-test compilation actually sees.

.DESCRIPTION
    The shared set, the flavor's own test set, and every shared test set mounted into it. A root is
    returned only when its directory exists: testLegacy is mounted in the build file and has no
    directory on disk, and a caller counting files under a path that is not there would report a
    smaller suite than the one that ran.
#>
function Get-EffectiveTestSourceRoots {
    param(
        [Parameter(Mandatory)][object]$Map,
        [string]$Flavor = ''
    )

    if ([string]::IsNullOrEmpty($Flavor) -or $Map.FlavorNames.Count -eq 0) {
        $roots = [System.Collections.Generic.List[string]]::new()
        $relative = "$($Map.Module)/src/test"
        if (Test-Path -LiteralPath (Join-Path $Map.RepoRoot $relative)) {
            $roots.Add($relative)
        }
        return $roots.ToArray()
    }

    $unitTestSet = Get-UnitTestSetName $Flavor
    if (-not $Map.TestSets.Contains($unitTestSet)) {
        throw "flavor-source-map: '$Flavor' is not a declared flavor of $($Map.Module)."
    }

    $candidates = [System.Collections.Generic.List[string]]::new()
    $candidates.Add('test')
    foreach ($set in $Map.TestSets[$unitTestSet]) { $candidates.Add($set) }

    $roots = [System.Collections.Generic.List[string]]::new()
    foreach ($set in $candidates) {
        $relative = "$($Map.Module)/src/$set"
        if (-not (Test-Path -LiteralPath (Join-Path $Map.RepoRoot $relative))) { continue }
        if (-not $roots.Contains($relative)) { $roots.Add($relative) }
    }
    return $roots.ToArray()
}

<#
.SYNOPSIS
    Read the module build file and return its flavor / source-set mount map.
#>
function Get-FlavorSourceMap {
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [string]$Module = 'app_v2'
    )

    $gradleFile = Join-Path $RepoRoot "$Module/build.gradle.kts"
    if (-not (Test-Path -LiteralPath $gradleFile)) {
        throw "flavor-source-map: build file not found: $gradleFile"
    }

    $lines = @(Get-Content -LiteralPath $gradleFile)

    $flavorsBlock = Get-GradleBlockRange -Lines $lines -HeaderPattern '^\s*productFlavors\s*\{'
    $sourceSetsBlock = Get-GradleBlockRange -Lines $lines -HeaderPattern '^\s*sourceSets\s*\{'

    $flavorNames = if ($flavorsBlock) {
        Get-GradleFlavorNames -Lines $lines -Start $flavorsBlock.Start -End $flavorsBlock.End
    }
    else {
        @()
    }

    $walk = if ($sourceSetsBlock) {
        Get-GradleMountWalk -Lines $lines -Start $sourceSetsBlock.Start -End $sourceSetsBlock.End
    }
    else {
        [pscustomobject]@{ Mounts = @(); Unattributed = @() }
    }

    $flavors = [ordered]@{}
    $testSets = [ordered]@{}
    # AGP mounts src/<name>/java by convention without a line in the build file, so the map would
    # under-report every flavor's own set if it only counted what is written down.
    foreach ($flavor in $flavorNames) {
        $flavors[$flavor] = [System.Collections.Generic.List[string]]@($flavor)
        $testSets[(Get-UnitTestSetName $flavor)] = [System.Collections.Generic.List[string]]@((Get-UnitTestSetName $flavor))
    }

    foreach ($mount in $walk.Mounts) {
        if ($mount.Kind -ne 'kotlin') { continue }
        if ($flavors.Contains($mount.Owner)) {
            if (-not $flavors[$mount.Owner].Contains($mount.Set)) { $flavors[$mount.Owner].Add($mount.Set) }
        }
        elseif ($testSets.Contains($mount.Owner)) {
            if (-not $testSets[$mount.Owner].Contains($mount.Set)) { $testSets[$mount.Owner].Add($mount.Set) }
        }
    }

    $mainSetMounts = [ordered]@{}
    foreach ($flavor in $flavors.Keys) {
        foreach ($set in $flavors[$flavor]) {
            if (-not $mainSetMounts.Contains($set)) { $mainSetMounts[$set] = [System.Collections.Generic.List[string]]::new() }
            $mainSetMounts[$set].Add($flavor)
        }
    }

    $testSetMounts = [ordered]@{}
    foreach ($testSet in $testSets.Keys) {
        foreach ($set in $testSets[$testSet]) {
            if (-not $testSetMounts.Contains($set)) { $testSetMounts[$set] = [System.Collections.Generic.List[string]]::new() }
            $testSetMounts[$set].Add($testSet)
        }
    }

    return [pscustomobject]@{
        Module          = $Module
        RepoRoot        = $RepoRoot
        GradleFile      = $gradleFile
        Lines           = $lines
        FlavorNames     = $flavorNames
        Flavors         = $flavors
        TestSets        = $testSets
        MainSetMounts   = $mainSetMounts
        TestSetMounts   = $testSetMounts
        Conditional     = @($walk.Mounts | Where-Object { $_.Conditional })
        # Non-empty means the walk met a mount line it could not attribute, so the map is partial.
        # A caller must report "could not verify" rather than judge on it - a narrowed scan that
        # still prints PASS is the exact regression this library exists to prevent.
        Unparsed        = $walk.Unattributed
        FlavorsBlock    = $flavorsBlock
        SourceSetsBlock = $sourceSetsBlock
    }
}
