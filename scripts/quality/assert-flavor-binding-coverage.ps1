#requires -Version 7.0
<#
.SYNOPSIS
    S2447: a Hilt binding that lives only in flavor-specific source sets, for a type src/main injects
    unconditionally - the shape that fails hiltJavaCompile in whichever flavor mounts neither half.

.DESCRIPTION
    `AccessibilityServiceControl` is declared in src/main and injected unconditionally by
    OperationsSettingsFragment, but both of its bindings sat in flavor-specific source sets
    (src/noLegal and src/standardScreenCapture). Five flavors mounted neither, so
    :app_v2:hiltJavaCompileLiteDebug failed with Dagger/MissingBinding - two weeks after the
    binding stopped reaching them, because no fast check compiles those five flavors at all.
    `.\a.ps1 fk` compiles standard and `fkn` compiles noLegal; both happened to carry a binding.

    Compiling the remaining five is not an option for a per-ticket gate: hiltJavaCompileStandardDebug
    alone measured 1 m 4 s and build-lite-debug 2 m 9 s, against the 32 s the whole fast batch
    costs. So this check is lexical. It reads the source-set mount map straight out of
    app_v2/build.gradle.kts, collects every non-multibound type provided in a flavor-specific source
    set, keeps the ones src/main injects, and asserts each flavor mounts a provider.

    A conditionally mounted directory counts only when EVERY branch of its `if`/`else` provides the
    type. That is not pedantry - it is the second half of the same defect: src/standardScreenCapture
    carried standard's only binding behind `if (edgeGestureOverlayStandardEnabled)`, so flipping
    fms.edgeGestureOverlay to off would have broken standard exactly as lite was broken, and a check
    that treated the mount as unconditional would have called that tree clean.

    Multibindings are deliberately out of scope: `@IntoSet` / `@IntoMap` contributions resolve to an
    empty collection when the contract declares `@Multibinds` in src/main, which fifteen of them do,
    so an absent contribution is a legal state rather than a missing binding.

.PARAMETER Gate
    Exit 1 on findings instead of only reporting them. assert-fast-gates.ps1 supplies this to every
    gate it runs, so a gate that does not declare it dies on a parameter-binding error and the batch
    records a FAIL that says nothing about the tree - measured on this gate's first batch run.

.PARAMETER Quiet
    Print only the verdict line and any findings.

.PARAMETER BuildFile
    Read the mount map from this file instead of app_v2/build.gradle.kts. The negative test - drop a
    mount and confirm the gate names the flavor and the type - would otherwise have to edit the real
    build file, which takes all three code-domain locks and cannot be repeated by anyone reviewing
    this later. Source sets are still read from app_v2/src.

Exit codes:
    0 - every unconditionally injected type has a binding in every flavor, or findings were reported
        without -Gate.
    1 - at least one flavor has no binding for such a type, and -Gate was supplied.
    2 - cannot verify: app_v2/build.gradle.kts or app_v2/src is missing, or no flavor was parsed.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$BuildFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$buildFile = if ($BuildFile) { $BuildFile } else { Join-Path $repoRoot 'app_v2\build.gradle.kts' }
$srcRoot = Join-Path $repoRoot 'app_v2\src'

if (-not (Test-Path -LiteralPath $buildFile) -or -not (Test-Path -LiteralPath $srcRoot)) {
    Write-Host "assert-flavor-binding-coverage: CANNOT VERIFY - app_v2/build.gradle.kts or app_v2/src not found." -ForegroundColor Red
    exit 2
}

# Line comments are stripped before ANY parsing below. Without this the mount regex matched a
# commented-out `kotlin.directories.add(..)` and reported the flavor as covered - measured on this
# ticket's own negative test, where dropping lite's mount left the gate green. `//` preceded by a
# colon is left alone so a URL inside a string is not mistaken for a comment.
$lines = Get-Content -LiteralPath $buildFile | ForEach-Object { $_ -replace '(?<!:)//.*$', '' }

function Get-BlockRange {
    <#
        First line index at or after $StartIndex whose brace balance returns to zero, given the block
        opens on $StartIndex. Returns the inclusive end index, or -1 when the file is unbalanced.
    #>
    param([string[]]$Text, [int]$StartIndex)

    $depth = 0
    for ($i = $StartIndex; $i -lt $Text.Count; $i++) {
        $stripped = $Text[$i] -replace '"[^"]*"', '""'
        $depth += ([regex]::Matches($stripped, '\{')).Count
        $depth -= ([regex]::Matches($stripped, '\}')).Count
        if ($i -gt $StartIndex -or $depth -eq 0) {
            if ($depth -le 0) { return $i }
        }
    }
    return -1
}

# --- 1. Flavor names, read from the productFlavors block only ------------------------------------
# `create("..")` also names signing configs and build types, so the block boundary is what makes the
# list right rather than a plausible superset.
$flavorNames = @()
$pfStart = ($lines | Select-String -Pattern '^\s*productFlavors\s*\{' | Select-Object -First 1)
if ($pfStart) {
    $pfFrom = $pfStart.LineNumber - 1
    $pfTo = Get-BlockRange -Text $lines -StartIndex $pfFrom
    if ($pfTo -gt $pfFrom) {
        for ($i = $pfFrom; $i -le $pfTo; $i++) {
            $m = [regex]::Match($lines[$i], '^\s*create\("(\w+)"\)\s*\{')
            if ($m.Success) { $flavorNames += $m.Groups[1].Value }
        }
    }
}

if ($flavorNames.Count -eq 0) {
    Write-Host "assert-flavor-binding-coverage: CANNOT VERIFY - parsed no flavors out of productFlavors." -ForegroundColor Red
    exit 2
}

# --- 2. Mount map: flavor -> unconditional dirs + conditional groups -----------------------------
$mounts = @{}
foreach ($flavor in $flavorNames) {
    $mounts[$flavor] = [pscustomobject]@{
        Unconditional = [System.Collections.Generic.List[string]]::new()
        # Each group is a list of branches; a branch is a list of dirs. A group covers a type only
        # when every branch provides it.
        Groups        = [System.Collections.Generic.List[object]]::new()
    }
    # src/main and the flavor's own source set are mounted by AGP without any declaration.
    $mounts[$flavor].Unconditional.Add('main')
    $mounts[$flavor].Unconditional.Add($flavor)
}

$ssStart = ($lines | Select-String -Pattern '^\s*sourceSets\s*\{' | Select-Object -First 1)
if (-not $ssStart) {
    Write-Host "assert-flavor-binding-coverage: CANNOT VERIFY - no sourceSets block in app_v2/build.gradle.kts." -ForegroundColor Red
    exit 2
}
$ssFrom = $ssStart.LineNumber - 1
$ssTo = Get-BlockRange -Text $lines -StartIndex $ssFrom
if ($ssTo -le $ssFrom) {
    Write-Host "assert-flavor-binding-coverage: CANNOT VERIFY - the sourceSets block does not close." -ForegroundColor Red
    exit 2
}

for ($i = $ssFrom; $i -le $ssTo; $i++) {
    $m = [regex]::Match($lines[$i], '^\s*getByName\("(\w+)"\)\s*\{')
    if (-not $m.Success) { continue }
    $flavor = $m.Groups[1].Value
    if (-not $mounts.ContainsKey($flavor)) { continue }

    $blockEnd = Get-BlockRange -Text $lines -StartIndex $i
    if ($blockEnd -le $i) { continue }

    $depth = 0
    $currentBranch = $null
    $currentGroup = $null
    for ($j = $i; $j -le $blockEnd; $j++) {
        $line = $lines[$j]
        $stripped = $line -replace '"[^"]*"', '""'
        $opens = ([regex]::Matches($stripped, '\{')).Count
        $closes = ([regex]::Matches($stripped, '\}')).Count

        # A nested block opening at depth 1 starts (or continues) a conditional group. `} else {`
        # closes and reopens on ONE line, so its depth is still 2 here - the test is what the depth
        # will be once this line's closing braces are applied, not what it is on entry.
        $depthAfterCloses = $depth - $closes
        if ($opens -gt 0 -and $depthAfterCloses -eq 1) {
            if ($stripped -match '^\s*\}\s*else\b' -and $null -ne $currentGroup) {
                $currentBranch = [System.Collections.Generic.List[string]]::new()
                $currentGroup.Add($currentBranch)
            } elseif ($stripped -match '^\s*if\s*\(') {
                $currentGroup = [System.Collections.Generic.List[object]]::new()
                $currentBranch = [System.Collections.Generic.List[string]]::new()
                $currentGroup.Add($currentBranch)
                $mounts[$flavor].Groups.Add($currentGroup)
            }
        }

        $depth += $opens - $closes

        $dirMatch = [regex]::Match($line, 'directories\.add\("src/([A-Za-z0-9_]+)/java"\)')
        if ($dirMatch.Success) {
            $dir = $dirMatch.Groups[1].Value
            if ($depth -ge 2 -and $null -ne $currentBranch) {
                $currentBranch.Add($dir)
            } elseif ($depth -eq 1) {
                $mounts[$flavor].Unconditional.Add($dir)
            }
        }

        if ($depth -le 1) { $currentBranch = $null }
    }
    $i = $blockEnd
}

# --- 3. Bindings per source set -------------------------------------------------------------------
# Key: source set name -> set of simple type names it provides through a single (non-multibound)
# @Provides / @Binds.
$providedBy = @{}
$sourceSetDirs = Get-ChildItem -LiteralPath $srcRoot -Directory -ErrorAction SilentlyContinue

foreach ($dir in $sourceSetDirs) {
    $javaRoot = Join-Path $dir.FullName 'java'
    if (-not (Test-Path -LiteralPath $javaRoot)) { continue }
    $types = [System.Collections.Generic.HashSet[string]]::new()

    # Pre-filter to the files that carry a binding annotation at all. Reading every .kt under
    # app_v2/src cost 5.6 s, a third of the whole fast batch, to look at a few hundred module files.
    $candidateFiles = @(Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter '*.kt' -File -ErrorAction SilentlyContinue |
        Select-String -Pattern '^\s*@(Provides|Binds)\b' -List | Select-Object -ExpandProperty Path)

    foreach ($filePath in $candidateFiles) {
        $text = Get-Content -LiteralPath $filePath
        for ($k = 0; $k -lt $text.Count; $k++) {
            if ($text[$k] -notmatch '^\s*@(Provides|Binds)\b') { continue }

            # Walk forward over the remaining annotations to the declaration itself, remembering
            # whether any of them makes this a multibinding contribution.
            $multibound = $false
            $declStart = -1
            for ($n = $k; $n -lt [Math]::Min($k + 8, $text.Count); $n++) {
                if ($text[$n] -match '@(IntoSet|IntoMap|ElementsIntoSet)\b') { $multibound = $true }
                if ($text[$n] -match '\bfun\s') { $declStart = $n; break }
            }
            if ($multibound -or $declStart -lt 0) { continue }

            # The return type may sit on a later line than `fun`; joining a short window keeps a
            # wrapped signature readable without pulling in the next declaration.
            $window = ($text[$declStart..([Math]::Min($declStart + 5, $text.Count - 1))] -join ' ')
            $rt = [regex]::Match($window, '\)\s*:\s*([A-Za-z_][\w.]*)')
            if (-not $rt.Success) { continue }
            $simple = ($rt.Groups[1].Value -split '\.')[-1]
            [void]$types.Add($simple)
        }
    }
    $providedBy[$dir.Name] = $types
}

# --- 4. Types src/main injects unconditionally ----------------------------------------------------
$injectedInMain = [System.Collections.Generic.HashSet[string]]::new()
$mainJava = Join-Path $srcRoot 'main\java'
$mainCandidates = @(Get-ChildItem -LiteralPath $mainJava -Recurse -Filter '*.kt' -File -ErrorAction SilentlyContinue |
    Select-String -Pattern '@Inject\b' -List | Select-Object -ExpandProperty Path)

foreach ($filePath in $mainCandidates) {
    $text = Get-Content -LiteralPath $filePath
    for ($k = 0; $k -lt $text.Count; $k++) {
        $line = $text[$k]
        if ($line -notmatch '@Inject\b') { continue }

        if ($line -match '@Inject\s+constructor\s*\(') {
            # Constructor injection: read to the closing paren of the parameter list.
            $depth = 0
            for ($n = $k; $n -lt [Math]::Min($k + 60, $text.Count); $n++) {
                $depth += ([regex]::Matches($text[$n], '\(')).Count
                $depth -= ([regex]::Matches($text[$n], '\)')).Count
                foreach ($p in [regex]::Matches($text[$n], ':\s*(?:dagger\.)?(?:Lazy|Provider)?<?\s*([A-Za-z_][\w.]*)')) {
                    [void]$injectedInMain.Add((($p.Groups[1].Value -split '\.')[-1]))
                }
                if ($n -gt $k -and $depth -le 0) { break }
            }
            continue
        }

        # Field injection: the declaration is the next line that is not another annotation.
        for ($n = $k + 1; $n -lt [Math]::Min($k + 5, $text.Count); $n++) {
            if ($text[$n] -match '^\s*@') { continue }
            $fm = [regex]::Match($text[$n], '\b(?:lateinit\s+)?var\s+\w+\s*:\s*(?:dagger\.)?(?:Lazy|Provider)?<?\s*([A-Za-z_][\w.]*)')
            if ($fm.Success) {
                [void]$injectedInMain.Add((($fm.Groups[1].Value -split '\.')[-1]))
            }
            break
        }
    }
}

# --- 5. Verdict ------------------------------------------------------------------------------------
# A type is in scope when src/main injects it and NO source set mounted by every flavor provides it -
# in practice, when its only providers are flavor-specific source sets.
$mainProvided = if ($providedBy.ContainsKey('main')) { $providedBy['main'] } else { [System.Collections.Generic.HashSet[string]]::new() }

$candidates = [System.Collections.Generic.HashSet[string]]::new()
foreach ($setName in $providedBy.Keys) {
    if ($setName -eq 'main') { continue }
    foreach ($type in $providedBy[$setName]) {
        if ($mainProvided.Contains($type)) { continue }
        if (-not $injectedInMain.Contains($type)) { continue }
        [void]$candidates.Add($type)
    }
}

$findings = @()
foreach ($type in ($candidates | Sort-Object)) {
    foreach ($flavor in ($flavorNames | Sort-Object)) {
        $map = $mounts[$flavor]

        $covered = $false
        foreach ($dir in $map.Unconditional) {
            if ($providedBy.ContainsKey($dir) -and $providedBy[$dir].Contains($type)) { $covered = $true; break }
        }
        if (-not $covered) {
            foreach ($group in $map.Groups) {
                if ($group.Count -lt 2) { continue }
                $allBranches = $true
                foreach ($branch in $group) {
                    $branchHas = $false
                    foreach ($dir in $branch) {
                        if ($providedBy.ContainsKey($dir) -and $providedBy[$dir].Contains($type)) { $branchHas = $true; break }
                    }
                    if (-not $branchHas) { $allBranches = $false; break }
                }
                if ($allBranches) { $covered = $true; break }
            }
        }

        if (-not $covered) {
            $findings += "  $flavor has no binding for '$type' - src/main injects it, and every provider sits in a source set this flavor does not mount unconditionally."
        }
    }
}

if (-not $Quiet) {
    Write-Host "assert-flavor-binding-coverage: $($flavorNames.Count) flavors, $($candidates.Count) flavor-bound type(s) injected from src/main."
}

if ($findings.Count -gt 0) {
    Write-Host "assert-flavor-binding-coverage: FAIL ($($findings.Count))" -ForegroundColor Red
    $findings | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    Write-Host "  Fix: mount a source set providing the type into that flavor (the no-op half of the seam), as src/screenCaptureDisabled does for AccessibilityServiceControl." -ForegroundColor Yellow
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host "assert-flavor-binding-coverage: PASS" -ForegroundColor Green
exit 0
