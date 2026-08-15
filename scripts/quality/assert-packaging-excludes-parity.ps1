#requires -Version 7.0
<#
.SYNOPSIS
    S1679: a packaging exclusion written for a shared transitive library must exist in every module
    that carries that library, not only in the module where someone noticed the waste.

.DESCRIPTION
    `app_v2` and `wear` both depend on `com.hierynomus:smbj`, which transitively brings
    `org.bouncycastle:bcprov-jdk18on`. BouncyCastle ships post-quantum PICNIC data tables as java
    resources inside its jar. R8 shrinks code but never java resources inside a jar, so those tables
    survive minification in full and are stored uncompressed in the APK.

    S0385 excluded them - in `app_v2` only. The `wear` module kept shipping them for months:
    1,214,815 bytes, 10.1 % of its 12,031,273-byte release APK, on a watch. Nothing tied the two
    build files together, so the divergence was invisible until someone unzipped a watch APK by hand.

    The gate compares the two `packaging { resources { excludes } }` sets and fails when:
      - a payload exclusion present in `app_v2` is absent from a module that declares the coordinate
        which brings that payload;
      - `app_v2` declares a payload exclusion whose prefix this script does not know, which means the
        next shared library arrived and nobody taught the gate about it.

    Scope is deliberately narrow. `/META-INF/**` exclusions are ignored: they resolve packaging
    conflicts between whatever set of jars a single module happens to merge, so they are legitimately
    per-module and comparing them would produce noise, not findings. Only library payload paths -
    the ones that cost real bytes in a shipped package - are compared.

    Direction is subset, not equality: a module may exclude MORE than `app_v2`. It may never exclude
    less while carrying the same library, because that is the direction that ships dead weight.

.NOTES
    Run from anywhere; paths are resolved relative to the repo root.

    Extending it: add a row to $PayloadRules. Each row names a path prefix, the declared Gradle
    coordinate that pulls it in transitively, and why it is dead weight. A payload exclusion in
    `app_v2` with no matching row is a failure by design - an unknown prefix is exactly the case
    this gate exists to catch, so it must not pass silently.

    Exit codes:
      0 - clean, every module carrying a shared library repeats its payload exclusions
      1 - a module is missing an exclusion, or an unknown payload prefix appeared
      2 - cannot verify: a build file is missing, or has no packaging/resources block to read
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/../..").Path

# The reference module: the one whose exclusion set is treated as the project's decision.
$referenceModule = 'app_v2'
$comparedModules = @('wear')

# Known shared payloads. Prefix -> the declared coordinate that brings it, plus the reason.
$PayloadRules = @(
    @{
        Prefix     = 'org/bouncycastle/'
        Coordinate = 'com.hierynomus:smbj'
        Reason     = 'BouncyCastle arrives transitively through SMBJ; its post-quantum and localized-message resources are never loaded by SMB/FTP/SFTP (S0385, S1679).'
    }
)

function Get-BuildFilePath {
    param([string]$Module)
    return (Join-Path $root "$Module/build.gradle.kts")
}

# Walks a chain of named Kotlin DSL blocks by brace balance and returns the innermost body, or $null
# if any link is absent. Brace counting rather than a regex, because the nesting depth is what
# distinguishes packaging.resources.excludes from packaging.jniLibs.excludes and regex cannot count.
function Get-NestedBlockText {
    param([string]$Text, [string[]]$Path)

    $current = $Text
    foreach ($name in $Path) {
        $header = [regex]::Match($current, "(?m)^\s*$([regex]::Escape($name))\s*\{")
        if (-not $header.Success) { return $null }

        $depth = 0
        $bodyStart = -1
        for ($i = $header.Index; $i -lt $current.Length; $i++) {
            $ch = $current[$i]
            if ($ch -eq '{') {
                $depth++
                if ($depth -eq 1) { $bodyStart = $i + 1 }
            } elseif ($ch -eq '}') {
                $depth--
                if ($depth -eq 0) {
                    $current = $current.Substring($bodyStart, $i - $bodyStart)
                    break
                }
            }
            if ($i -eq $current.Length - 1) { return $null }
        }
        if ($depth -ne 0) { return $null }
    }
    return $current
}

function Get-PackagingExcludes {
    param([string]$Module)

    $path = Get-BuildFilePath -Module $Module
    if (-not (Test-Path $path)) {
        Write-Error "assert-packaging-excludes-parity: build file not found - '$path'." -ErrorAction Continue
        exit 2
    }

    $text = Get-Content -LiteralPath $path -Raw

    # Scope matters, and a bare literal match is not enough: `jniLibs` declares `excludes` with the
    # same syntax one block below, and app_v2's `**/*.tmp` there is a Windows NDK workaround with no
    # bearing on library payload. Reading it as a payload exclusion made the first version of this
    # gate fail on a clean tree. So descend into packaging { resources { } } by brace balance first.
    $resourcesBlock = Get-NestedBlockText -Text $text -Path @('packaging', 'resources')
    if ($null -eq $resourcesBlock) {
        Write-Error "assert-packaging-excludes-parity: '$path' has no packaging { resources { } } block - nothing to compare." -ErrorAction Continue
        exit 2
    }

    # Not $matches: that is an automatic variable populated by -match, and shadowing it here would
    # make every later regex in this scope read as if it had already run.
    $found = [regex]::Matches($resourcesBlock, 'excludes\s*\+=\s*"(?<value>[^"]+)"')
    if ($found.Count -eq 0) {
        Write-Error "assert-packaging-excludes-parity: '$path' declares no packaging exclusions at all - nothing to compare." -ErrorAction Continue
        exit 2
    }

    $values = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($m in $found) {
        [void]$values.Add($m.Groups['value'].Value)
    }

    # The comma keeps PowerShell from unrolling the set into a plain array on return, which would
    # silently swap HashSet.Contains for Array.Contains and change the comparison semantics.
    return ,$values
}

function Test-DeclaresCoordinate {
    param([string]$Module, [string]$Coordinate)

    $path = Get-BuildFilePath -Module $Module
    $text = Get-Content -LiteralPath $path -Raw
    return $text.Contains($Coordinate)
}

# A payload exclusion is one that removes library content. META-INF housekeeping is per-module.
function Test-IsPayloadExclusion {
    param([string]$Value)
    return -not $Value.TrimStart('/').StartsWith('META-INF/')
}

$referenceExcludes = Get-PackagingExcludes -Module $referenceModule
$violations = [System.Collections.Generic.List[string]]::new()

foreach ($value in $referenceExcludes) {
    if (-not (Test-IsPayloadExclusion -Value $value)) { continue }

    $rule = $PayloadRules | Where-Object { $value.TrimStart('/').StartsWith($_.Prefix) } | Select-Object -First 1
    if ($null -eq $rule) {
        $violations.Add("$referenceModule declares payload exclusion '$value', but this gate knows no rule for it. Add a row to `$PayloadRules naming the coordinate that brings it, so the other modules get checked too.")
        continue
    }

    foreach ($module in $comparedModules) {
        if (-not (Test-DeclaresCoordinate -Module $module -Coordinate $rule.Coordinate)) { continue }

        $moduleExcludes = Get-PackagingExcludes -Module $module
        if (-not $moduleExcludes.Contains($value)) {
            $violations.Add("$module carries $($rule.Coordinate) but does not exclude '$value', which $referenceModule excludes. $($rule.Reason)")
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error "assert-packaging-excludes-parity: $($violations.Count) violation(s) - modules sharing a library disagree on what it may ship." -ErrorAction Continue
    foreach ($violation in $violations) {
        Write-Host "  $violation"
    }
    Write-Host '  Fix: copy the missing excludes line into the module packaging { resources { } } block.'
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-packaging-excludes-parity: OK - every module sharing a library repeats its payload exclusions."
}
exit 0
