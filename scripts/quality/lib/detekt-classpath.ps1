# S2112: assembling the detekt CLI classpath out of the gradle dependency cache, split out of
# detekt-scoped.ps1 so a second caller cannot assemble a subtly different one.
#
# The Kotlin artifact is the fragile axis. detekt is compiled against one Kotlin version while this
# project builds with another, and BOTH sit in the shared cache - putting the wrong one first fails
# inside detekt's own rule-set wiring, not at load time, so it cannot be detected by inspecting the
# classpath. A second, independently written copy of this logic could therefore resolve a different
# Kotlin version and emit baseline IDs that do not match the ones the operational baseline holds,
# which would make every prune diff wrong in a way no unit test would catch.
#
# Dot-source it:  . (Join-Path $repoRoot 'scripts/quality/lib/detekt-classpath.ps1')

<#
.SYNOPSIS
    Read the detekt version pin out of the root build.gradle.kts.

.OUTPUTS
    Hashtable: Ok [bool], Version [string], Reason [string].
#>
function Get-DetektVersionPin {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot
    )

    $buildFile = Join-Path $RepoRoot 'build.gradle.kts'
    if (-not (Test-Path -LiteralPath $buildFile)) {
        return @{ Ok = $false; Version = ''; Reason = "build.gradle.kts not found at $buildFile." }
    }
    $pin = [regex]::Match((Get-Content -LiteralPath $buildFile -Raw),
        'id\("io\.gitlab\.arturbosch\.detekt"\)\s+version\s+"([^"]+)"')
    if (-not $pin.Success) {
        return @{ Ok = $false; Version = ''; Reason = 'could not read the detekt version pin from build.gradle.kts.' }
    }
    return @{ Ok = $true; Version = $pin.Groups[1].Value; Reason = '' }
}

<#
.SYNOPSIS
    Resolve one jar per version directory for a gradle-cache coordinate.

.OUTPUTS
    String[] of absolute jar paths; empty when the coordinate is absent.
#>
function Resolve-DetektCacheJar {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$CacheRoot,
        [Parameter(Mandatory)][string]$Coordinate,
        [string]$Version
    )

    $dir = Join-Path $CacheRoot $Coordinate
    if (-not (Test-Path -LiteralPath $dir)) { return @() }
    $versionDirs = @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue)
    if ($Version) { $versionDirs = @($versionDirs | Where-Object { $_.Name -eq $Version }) }
    $out = @()
    foreach ($vd in $versionDirs) {
        $jar = Get-ChildItem -LiteralPath $vd.FullName -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
            Select-Object -First 1
        if ($jar) { $out += $jar.FullName }
    }
    return $out
}

<#
.SYNOPSIS
    The Kotlin versions present in the cache, ascending.
#>
function Get-DetektKotlinCandidate {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$CacheRoot
    )

    $dir = Join-Path $CacheRoot 'org.jetbrains.kotlin/kotlin-compiler-embeddable'
    if (-not (Test-Path -LiteralPath $dir)) { return @() }
    return @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue |
            Sort-Object -Property Name | ForEach-Object { $_.Name })
}

<#
.SYNOPSIS
    Build the full detekt CLI classpath for one candidate Kotlin version.
#>
function Build-DetektClasspath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$CacheRoot,
        [Parameter(Mandatory)][string]$DetektVersion,
        [Parameter(Mandatory)][string]$KotlinVersion
    )

    $list = [System.Collections.Generic.List[string]]::new()
    foreach ($a in (Get-ChildItem -LiteralPath (Join-Path $CacheRoot 'io.gitlab.arturbosch.detekt') -Directory -ErrorAction SilentlyContinue)) {
        if ($a.Name -in @('detekt-formatting', 'detekt-gradle-plugin')) { continue }
        foreach ($j in (Resolve-DetektCacheJar -CacheRoot $CacheRoot -Coordinate "io.gitlab.arturbosch.detekt/$($a.Name)" -Version $DetektVersion)) { $list.Add($j) }
    }
    foreach ($a in @('kotlin-stdlib', 'kotlin-compiler-embeddable', 'kotlin-reflect', 'kotlin-script-runtime')) {
        foreach ($j in (Resolve-DetektCacheJar -CacheRoot $CacheRoot -Coordinate "org.jetbrains.kotlin/$a" -Version $KotlinVersion)) { $list.Add($j) }
    }
    foreach ($c in @('org.jcommander/jcommander', 'com.beust/jcommander',
            'org.jetbrains.intellij.deps/trove4j', 'org.jetbrains/annotations',
            'org.snakeyaml/snakeyaml-engine', 'org.yaml/snakeyaml',
            'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm',
            'io.github.davidburstrom.contester/contester-breakpoint',
            'org.ec4j.core/ec4j-core', 'io.github.microutils/kotlin-logging-jvm',
            'org.slf4j/slf4j-api', 'org.slf4j/slf4j-simple')) {
        foreach ($j in (Resolve-DetektCacheJar -CacheRoot $CacheRoot -Coordinate $c)) { $list.Add($j) }
    }
    foreach ($k in (Get-ChildItem -LiteralPath (Join-Path $CacheRoot 'com.pinterest.ktlint') -Directory -ErrorAction SilentlyContinue)) {
        foreach ($j in (Resolve-DetektCacheJar -CacheRoot $CacheRoot -Coordinate "com.pinterest.ktlint/$($k.Name)")) { $list.Add($j) }
    }
    return @($list)
}

<#
.SYNOPSIS
    Everything a caller needs to invoke the detekt CLI: version, formatting plugin jar, and the
    Kotlin candidates in the order worth trying.

.DESCRIPTION
    The memo at temp/detekt-scoped/classpath.json remembers the combination that last worked, keyed
    on the detekt pin, so a later run costs one JVM start rather than several. A pin change
    invalidates the entry by construction. Both the scoped runner and the prune tool share the memo
    on purpose - they assemble the same classpath, so a hit earned by one is valid for the other.

.OUTPUTS
    Hashtable:
      Ok               [bool]     - $false when the CLI cannot be assembled at all.
      Reason           [string]   - why Ok is $false; empty when Ok.
      DetektVersion    [string]
      PluginJar        [string]   - detekt-formatting jar, passed via --plugins.
      KotlinCandidates [string[]] - preferred first.
      CacheRoot        [string]
      MemoPath         [string]   - where Save-DetektClasspathMemo should write a winner.
#>
function Initialize-DetektCli {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [string]$CacheRoot = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1')
    )

    $fail = { param($m) @{ Ok = $false; Reason = $m; DetektVersion = ''; PluginJar = ''; KotlinCandidates = @(); CacheRoot = $CacheRoot; MemoPath = '' } }

    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { return (& $fail 'java is not on PATH.') }
    if (-not (Test-Path -LiteralPath $CacheRoot)) { return (& $fail "gradle dependency cache not found at $CacheRoot.") }

    $pin = Get-DetektVersionPin -RepoRoot $RepoRoot
    if (-not $pin.Ok) { return (& $fail $pin.Reason) }
    $detektVersion = $pin.Version

    $plugin = @(Resolve-DetektCacheJar -CacheRoot $CacheRoot -Coordinate 'io.gitlab.arturbosch.detekt/detekt-formatting' -Version $detektVersion)
    if ($plugin.Count -eq 0) {
        return (& $fail "detekt-formatting $detektVersion is not in the dependency cache - the formatting rules would be silently skipped.")
    }

    $candidates = @(Get-DetektKotlinCandidate -CacheRoot $CacheRoot)
    if ($candidates.Count -eq 0) { return (& $fail "no kotlin-compiler-embeddable in the dependency cache under $CacheRoot.") }

    $memoPath = Join-Path $RepoRoot 'temp/detekt-scoped/classpath.json'
    $preferred = $null
    if (Test-Path -LiteralPath $memoPath) {
        try {
            $cached = Get-Content -LiteralPath $memoPath -Raw | ConvertFrom-Json
            if ($cached.detekt -eq $detektVersion -and ($candidates -contains $cached.kotlin)) { $preferred = $cached.kotlin }
        }
        catch { $preferred = $null }
    }
    $ordered = @()
    if ($preferred) { $ordered += $preferred }
    $ordered += @($candidates | Where-Object { $_ -ne $preferred })

    return @{
        Ok               = $true
        Reason           = ''
        DetektVersion    = $detektVersion
        PluginJar        = $plugin[0]
        KotlinCandidates = $ordered
        CacheRoot        = $CacheRoot
        MemoPath         = $memoPath
    }
}

<#
.SYNOPSIS
    Record the Kotlin version that actually ran, so the next call starts with it.
#>
function Save-DetektClasspathMemo {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$MemoPath,
        [Parameter(Mandatory)][string]$DetektVersion,
        [Parameter(Mandatory)][string]$KotlinVersion
    )

    $dir = Split-Path -Parent $MemoPath
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [PSCustomObject]@{ detekt = $DetektVersion; kotlin = $KotlinVersion } |
        ConvertTo-Json | Set-Content -LiteralPath $MemoPath -Encoding UTF8
}
