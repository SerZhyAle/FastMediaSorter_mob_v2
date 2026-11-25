#requires -Version 7.0
<#
.SYNOPSIS
    Record a successful build in the release ledger (RELEASE-LEDGER 0.1). Never fails the caller.

.DESCRIPTION
    S4679. The last step of every builder under scripts/builders: this wrapper calls the canon
    writer `releases/release-ledger build` and reports, never fails, on any refusal - a ledger
    write must never fail a build (canon RELEASE_AND_DISTRIBUTION section 10). The writer is
    resolved exactly like every harness forwarder resolves its target: $env:SZA_HARNESS_ROOT,
    the plugin cache's newest version that ships the script, then the canon checkout.

    When -Version is empty the wrapper reads the checked-in stable sentinel
    (`defaultAppVersionName` in app_v2/build.gradle.kts) - that is the versionName a
    -Pfms.stableVersion=true device build actually packaged (S3513).

    Exit codes: 0 always - a ledger write never fails the build; every failure is reported.
#>
param(
    [string]$Package,
    [string]$Kind,
    [string]$Version,
    [string]$Flavor,
    [string]$Artifact
)

$ErrorActionPreference = 'Continue'

function Stop-RlLog([string]$Message) {
    Write-Warning "release ledger: $Message The build stands; the ledger row is not written."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Package)) { Stop-RlLog '-Package is empty.' }
if (@('release', 'test', 'debug') -cnotcontains $Kind) { Stop-RlLog "-Kind '$Kind' is not one of release, test, debug." }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# The writer's own _profile.ps1 resolves the project from this variable, the same way the
# harness forwarders set it before invoking (S2402).
$env:SZA_PROJECT_ROOT = $repoRoot

$candidates = @()
if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
$rlHome = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { $null }
$rlCache = if ($rlHome) { Join-Path $rlHome '.claude/plugins/cache/sza-unified-rules/sza' } else { $null }
if ($rlCache -and (Test-Path -LiteralPath $rlCache -PathType Container)) {
    # Ordered as VERSIONS, not as strings - a string sort puts 2026.1001.1 below 2026.903.1
    # (the same ordering bug the forwarders fixed in S3075's predecessor).
    $rlVersions = @(Get-ChildItem -LiteralPath $rlCache -Directory -ErrorAction SilentlyContinue |
        ForEach-Object {
            $rlParsed = $null
            [void][version]::TryParse($_.Name, [ref]$rlParsed)
            [pscustomobject]@{ Path = $_.FullName; Version = $rlParsed }
        } | Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                        @{ Expression = { $_.Version }; Descending = $true },
                        @{ Expression = { $_.Path }; Descending = $true })
    $candidates += @($rlVersions | ForEach-Object { Join-Path $_.Path 'tools/harness' })
}
$rlCheckout = $env:SZA_CANON_ROOT
if (-not $rlCheckout) {
    try { . (Join-Path $PSScriptRoot 'project-paths.ps1'); $rlCheckout = Get-CanonRoot } catch { $rlCheckout = $null }
}
if ($rlCheckout) { $candidates += (Join-Path $rlCheckout 'tools/harness') }

$rlWriter = $null
foreach ($rlDir in $candidates) {
    $rlProbe = Join-Path $rlDir 'releases/release-ledger.ps1'
    if (Test-Path -LiteralPath $rlProbe -PathType Leaf) { $rlWriter = $rlProbe; break }
}
if (-not $rlWriter) { Stop-RlLog 'the writer releases/release-ledger.ps1 was not found (SZA_HARNESS_ROOT, plugin cache, canon checkout).' }

if ([string]::IsNullOrWhiteSpace($Version)) {
    # Stable device/debug builds package the checked-in sentinel, not a clock stamp (S3513).
    $gradleFile = Join-Path $repoRoot 'app_v2/build.gradle.kts'
    $rlSentinel = Select-String -LiteralPath $gradleFile -Pattern 'defaultAppVersionName\s*=\s*"([^"]+)"' |
        Select-Object -First 1
    if ($rlSentinel) {
        $Version = $rlSentinel.Matches[0].Groups[1].Value
        Write-Host "release ledger: no -Version passed; using the checked-in stable sentinel $Version" -ForegroundColor DarkGray
    } else {
        Stop-RlLog '-Version is empty and the stable sentinel was not found in app_v2/build.gradle.kts.'
    }
}

# Hashtable splat, not an array: array splatting passes its elements positionally, so the
# '-Package' string would look for a positional slot and the writer would refuse the invocation.
$rlSplat = @{ Verb = 'build'; Package = $Package; Kind = $Kind; Version = $Version }
if (-not [string]::IsNullOrWhiteSpace($Flavor)) { $rlSplat.Flavor = $Flavor }
if (-not [string]::IsNullOrWhiteSpace($Artifact)) { $rlSplat.Artifact = $Artifact }

try {
    & $rlWriter @rlSplat 2>&1 | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "release ledger: the writer exited $LASTEXITCODE. The build stands; the ledger row is not written."
    }
} catch {
    Write-Warning "release ledger: the write failed ($($_.Exception.Message)). The build stands; the ledger row is not written."
}
exit 0
