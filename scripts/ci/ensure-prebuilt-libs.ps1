#requires -Version 7.0
<#
.SYNOPSIS
    Assert the prebuilt native AARs a release build needs are present, and fetch what is missing.

.DESCRIPTION
    app_v2/libs/ is gitignored, so a fresh checkout or a release worktree carries none of the native
    AARs app_v2/build.gradle.kts declares. Gradle's own verifyPrebuiltNativeAars task does catch it,
    but only after the build has configured - the message arrives as a failed task deep inside a
    build and reads as a broken build rather than as a missing download. This runs before gradle
    starts, where the refusal can still name the cause and the command that fixes it.

    WHICH AARs is not this script's list: scripts/ci/prebuilt-native-aars.txt is, and the publisher,
    the CI fetch script and the build's own verify task all read the same file. This is the fourth
    reader that file's header describes, not a second list.

    The GitHub CLI is resolved rather than assumed: it is installed on this machine but on no PATH
    the repository's shells see, which is how the v2.60.9121.346 release met "gh: command not found"
    with the binary sitting in Program Files (S3029).

.PARAMETER RepoRoot
    Repository root the manifest's relative paths resolve against. Defaults to this script's own
    repository, so a release worktree gets its own copy rather than the development checkout's.

.PARAMETER ManifestPath
    The AAR list. Defaults to scripts/ci/prebuilt-native-aars.txt under -RepoRoot.

.PARAMETER NoFetch
    Check only - never download. The refusal still names the fetch command.

.NOTES
    Exit codes:
      0 - every AAR in the manifest is present and non-empty (fetched now, or already there).
      1 - an AAR is missing and could not be fetched (no CLI, or the download failed or was empty).
      2 - the manifest is absent, empty, or could not be read.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot,
    [string] $ManifestPath,
    [switch] $NoFetch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$DeliveryTag = 'delivery-so-v1'
$Repo = if ($env:GITHUB_REPOSITORY) { $env:GITHUB_REPOSITORY } else { 'SerZhyAle/FastMediaSorter_mob_v2' }

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $RepoRoot 'scripts/ci/prebuilt-native-aars.txt'
}

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    Write-Host "ensure-prebuilt-libs: manifest not found: $ManifestPath" -ForegroundColor Red
    exit 2
}

$entries = @(Get-Content -LiteralPath $ManifestPath |
    ForEach-Object { ($_ -split '#', 2)[0].Trim() } |
    Where-Object { $_ })

if ($entries.Count -eq 0) {
    Write-Host "ensure-prebuilt-libs: manifest lists no AAR: $ManifestPath" -ForegroundColor Red
    exit 2
}

# The CLI is resolved once and only when something is actually missing, so a prepared worktree never
# pays for a lookup and never fails on an absent CLI it did not need.
function Resolve-GitHubCli {
    $onPath = Get-Command gh -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if (-not $base) { continue }
        $candidate = Join-Path $base 'GitHub CLI\gh.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    return $null
}

function Test-AarPresent {
    param([Parameter(Mandatory)][string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    return ((Get-Item -LiteralPath $Path).Length -gt 0)
}

$missing = @()
foreach ($entry in $entries) {
    $full = Join-Path $RepoRoot $entry
    if (Test-AarPresent -Path $full) { continue }
    $missing += [pscustomobject]@{ Entry = $entry; Full = $full }
}

if ($missing.Count -eq 0) {
    Write-Host "ensure-prebuilt-libs: $($entries.Count) prebuilt AAR(s) present." -ForegroundColor Green
    exit 0
}

Write-Host "ensure-prebuilt-libs: $($missing.Count) of $($entries.Count) prebuilt AAR(s) missing:" -ForegroundColor Yellow
foreach ($m in $missing) { Write-Host "  $($m.Entry)" -ForegroundColor Yellow }

$cli = Resolve-GitHubCli
$fetchHint = if ($cli) { "& '$cli' release download $DeliveryTag --repo $Repo --pattern <asset> --dir <dir> --clobber" }
             else { "install the GitHub CLI, or set PATH to the directory holding gh.exe, then re-run this script" }

if ($NoFetch) {
    Write-Host "ensure-prebuilt-libs: -NoFetch set, nothing downloaded. Fetch with:" -ForegroundColor Red
    Write-Host "  $fetchHint" -ForegroundColor Gray
    exit 1
}

if (-not $cli) {
    Write-Host "ensure-prebuilt-libs: the GitHub CLI could not be resolved - not on PATH and not at the known install paths." -ForegroundColor Red
    Write-Host "  $fetchHint" -ForegroundColor Gray
    exit 1
}

foreach ($m in $missing) {
    $asset = Split-Path -Leaf $m.Entry
    $dir = Split-Path -Parent $m.Full
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    Write-Host "ensure-prebuilt-libs: fetching $asset from $DeliveryTag .." -ForegroundColor Cyan
    & $cli release download $DeliveryTag --repo $Repo --pattern $asset --dir $dir --clobber
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ensure-prebuilt-libs: download of $asset failed (gh exit $LASTEXITCODE)." -ForegroundColor Red
        Write-Host "  $fetchHint" -ForegroundColor Gray
        exit 1
    }
    # A truncated or zero-byte download fails much later inside an opaque Gradle transform, so the
    # shape is asserted here, where the error still names the asset.
    if (-not (Test-AarPresent -Path $m.Full)) {
        Write-Host "ensure-prebuilt-libs: $($m.Entry) is missing or empty after download." -ForegroundColor Red
        exit 1
    }
}

Write-Host "ensure-prebuilt-libs: $($missing.Count) AAR(s) fetched, $($entries.Count) present." -ForegroundColor Green
exit 0
