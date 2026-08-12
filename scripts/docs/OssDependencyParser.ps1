<#
.SYNOPSIS
    S1495 - dependency coordinate parser for the OSS notice generator.

.DESCRIPTION
    Reads a module's build.gradle.kts as text and returns one object per Maven
    coordinate declared inside its dependencies { } block:

      Group Artifact Version Configuration Module Shipping

    Shipping answers the only question the published notice page cares about:
    does this artifact end up inside something a user receives.

      shipping      implementation, api, coreLibraryDesugaring,
                    releaseImplementation, <flavor>Implementation
      not shipping  test*, androidTest*, debug*, benchmark*, compileOnly,
                    ksp, kapt, annotationProcessor, lintChecks

    The desugared library and the release-only no-op are both packaged into a
    distributed artifact, so neither may be filtered out. Debug and benchmark
    builds are never distributed.

    Parsing is pure text with brace-depth tracking - no gradle invocation, so
    the caller stays outside BUILD.LOCK (S1495 ADR-4).

    Three declaration shapes carry no Maven coordinate and are skipped: a
    variable (implementation(composeBom)), a project or file dependency
    (lintChecks(project(":lint-rules")), "vrImplementation"(files("libs/x.aar"))),
    and a platform() BOM, which contributes version constraints rather than code.
    The bundled AAR is a shipped artifact and is carried by an explicit entry in
    scripts/docs/oss-licenses.psd1 instead.

    A BOM-managed coordinate is declared without a version
    (implementation("androidx.compose.ui:ui")); Version is empty for those.

.PARAMETER Census
    Print every distinct shipping group:artifact across both modules, sorted,
    with the configurations that declare it. Dot-source the script instead to
    call Get-OssDependencies directly.

.PARAMETER CensusOut
    Write the census to this path in addition to stdout.

.EXAMPLE
    . ./scripts/docs/OssDependencyParser.ps1
    Get-OssDependencies -GradleFile app_v2/build.gradle.kts -Module app_v2

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/OssDependencyParser.ps1 -Census

.NOTES
    Exit codes:
      0  parsed
      2  could not verify: build file missing, dependencies block not found,
         flavor list unavailable, or a string literal that cannot be decomposed
         into at least group:artifact
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Census,
    [string] $CensusOut
)

$ErrorActionPreference = 'Stop'

# Configurations whose artifacts reach a distributed build. releaseImplementation and
# coreLibraryDesugaring are here deliberately: the release-only no-op and the desugared
# library are both packaged into what users receive.
$script:ShippingExact = @(
    'implementation', 'api', 'coreLibraryDesugaring', 'releaseImplementation'
)

$script:NonShippingPrefixes = @(
    'test', 'androidTest', 'debug', 'benchmark', 'compileOnly', 'ksp', 'kapt',
    'annotationProcessor', 'lintChecks'
)

function Get-OssFlavorNames {
    <#
        .SYNOPSIS
            Flavor names, read from the flavor-matrix snapshot rather than re-parsed.
    #>
    [CmdletBinding()]
    param([string] $Root = $RepoRoot)

    $snapshot = Join-Path $Root 'docs/flavors/flavor-matrix.json'
    if (-not (Test-Path -LiteralPath $snapshot)) {
        Write-Error "flavor snapshot not found: $snapshot - run scripts/docs/generate-flavor-matrix.ps1" -ErrorAction Continue
        exit 2
    }
    $flavors = (Get-Content -LiteralPath $snapshot -Raw | ConvertFrom-Json).flavors
    if (-not $flavors -or $flavors.Count -lt 1) {
        Write-Error "flavor snapshot carries no flavors: $snapshot" -ErrorAction Continue
        exit 2
    }
    return @($flavors)
}

function Test-OssShippingConfiguration {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]   $Configuration,
        [Parameter(Mandatory)] [string[]] $Flavors
    )

    if ($script:ShippingExact -contains $Configuration) { return $true }
    foreach ($flavor in $Flavors) {
        if ($Configuration -eq "${flavor}Implementation") { return $true }
        if ($Configuration -eq "${flavor}Api") { return $true }
    }
    foreach ($prefix in $script:NonShippingPrefixes) {
        if ($Configuration -eq $prefix -or $Configuration.StartsWith($prefix)) { return $false }
    }
    return $false
}

function Get-OssDependencies {
    <#
        .SYNOPSIS
            Every Maven coordinate declared in a module's dependencies block.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]   $GradleFile,
        [Parameter(Mandatory)] [string]   $Module,
        [string[]] $Flavors
    )

    if (-not (Test-Path -LiteralPath $GradleFile)) {
        Write-Error "build file not found: $GradleFile" -ErrorAction Continue
        exit 2
    }
    if (-not $Flavors) { $Flavors = Get-OssFlavorNames }

    $lines = Get-Content -LiteralPath $GradleFile
    $inBlock = $false
    $depth = 0
    $found = $false
    $results = [System.Collections.Generic.List[object]]::new()

    # A declaration is a configuration call on its own line; the argument must be a direct
    # string literal, which is exactly what excludes project(), files() and a bare variable.
    $rxDeclaration = '^\s*(?:"(?<q>[A-Za-z][A-Za-z0-9_]*)"|(?<b>[A-Za-z][A-Za-z0-9_]*))\s*\(\s*"(?<coord>[^"]+)"'

    foreach ($raw in $lines) {
        $line = $raw -replace '//.*$', ''

        if (-not $inBlock) {
            if ($line -match '^\s*dependencies\s*\{') {
                $inBlock = $true
                $found = $true
                $depth = 1
            }
            continue
        }

        $opens = ([regex]::Matches($line, '\{')).Count
        $closes = ([regex]::Matches($line, '\}')).Count

        if ($line -match $rxDeclaration) {
            $configuration = if ($Matches['q']) { $Matches['q'] } else { $Matches['b'] }
            $coordinate = $Matches['coord']

            # platform() and project() never reach here - their argument is not the first
            # literal after the configuration's own paren - but a BOM assigned inline would.
            if ($configuration -ne 'platform') {
                $parts = $coordinate.Split(':')
                if ($parts.Count -lt 2 -or [string]::IsNullOrWhiteSpace($parts[0]) -or [string]::IsNullOrWhiteSpace($parts[1])) {
                    Write-Error "unparsable coordinate in ${GradleFile}: $coordinate" -ErrorAction Continue
                    exit 2
                }
                $results.Add([pscustomobject]@{
                    Group         = $parts[0]
                    Artifact      = $parts[1]
                    Version       = if ($parts.Count -ge 3) { $parts[2] } else { '' }
                    Configuration = $configuration
                    Module        = $Module
                    Shipping      = (Test-OssShippingConfiguration -Configuration $configuration -Flavors $Flavors)
                })
            }
        }

        $depth += $opens - $closes
        if ($depth -le 0) { $inBlock = $false }
    }

    if (-not $found) {
        Write-Error "dependencies block not found in $GradleFile" -ErrorAction Continue
        exit 2
    }

    return $results
}

function Get-OssCoordinateCensus {
    <#
        .SYNOPSIS
            Distinct shipping coordinates across both modules, with their configurations.
    #>
    [CmdletBinding()]
    param([string] $Root = $RepoRoot)

    $flavors = Get-OssFlavorNames -Root $Root
    $all = @()
    $all += Get-OssDependencies -GradleFile (Join-Path $Root 'app_v2/build.gradle.kts') -Module 'app_v2' -Flavors $flavors
    $all += Get-OssDependencies -GradleFile (Join-Path $Root 'wear/build.gradle.kts') -Module 'wear' -Flavors $flavors

    return $all |
        Where-Object { $_.Shipping } |
        Group-Object { "$($_.Group):$($_.Artifact)" } |
        Sort-Object Name |
        ForEach-Object {
            [pscustomobject]@{
                Coordinate     = $_.Name
                Configurations = (($_.Group | ForEach-Object { "$($_.Module)/$($_.Configuration)" } | Sort-Object -Unique) -join ', ')
            }
        }
}

if ($Census) {
    # Never name this $census - PowerShell variable names are case-insensitive, so it would
    # be the $Census switch itself and the assignment fails as a type conversion.
    $rows = Get-OssCoordinateCensus -Root $RepoRoot
    $text = $rows | ForEach-Object { "{0}`t{1}" -f $_.Coordinate, $_.Configurations }
    $text | Write-Output
    if ($CensusOut) {
        $dir = Split-Path -Parent $CensusOut
        if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        Set-Content -LiteralPath $CensusOut -Value $text -Encoding utf8
    }
    exit 0
}
