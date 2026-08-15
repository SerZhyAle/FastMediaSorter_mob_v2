#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Retrieve a retained release's deobfuscation payload by version, or verify that
    one is really stored and really readable.

.DESCRIPTION
    Spec S1695 - release-deobfuscation-artifact-retention. Counterpart of
    scripts/release/retain-deobfuscation.ps1, which fills the archive this reads.

    The archive is keyed by versionCode, but the operator arriving from a crash
    report usually holds a versionName - both are accepted, as is -Latest.

    Exit codes:
      0 - request answered: payload extracted, verification passed, or -List
          printed the archive (an empty archive is an answer, not a failure).
      1 - the requested release or variant is not retained, or -Verify found the
          stored payload missing, unreadable, or not matching its recorded hash.
      2 - archive root unreachable or unreadable, or an invalid invocation.

.PARAMETER VersionCode
    Exact archive key to fetch.

.PARAMETER VersionName
    Version string as recorded in the manifests, for the common case where the
    crash report names a version rather than a code.

.PARAMETER Latest
    Fetch the highest retained versionCode.

.PARAMETER Variant
    Which published variant to fetch. Defaults to standard.

.PARAMETER Destination
    Where to extract. Defaults to temp/deobfuscation, per the repository rule that
    scratch artifacts live under temp/.

.PARAMETER List
    Print every retained release and exit. Needs no version argument.

.PARAMETER Verify
    Read the stored payload back and check it against its recorded hash. Extracts
    nothing. This is the mode the pre-release gate consumes.

.PARAMETER Json
    Emit machine-readable output for -List and -Verify.

.PARAMETER ArchiveRoot
    Root of the archive. Must match the root retain-deobfuscation.ps1 wrote to.

.EXAMPLE
    pwsh -File scripts/release/fetch-deobfuscation.ps1 -VersionCode 260812204

.EXAMPLE
    pwsh -File scripts/release/fetch-deobfuscation.ps1 -List
#>

[CmdletBinding()]
param(
    [int]    $VersionCode,
    [string] $VersionName,
    [switch] $Latest,
    [ValidateSet('standard', 'lite', 'photos', 'legacy', 'vr', 'noLegal', 'wear')]
    [string] $Variant = 'standard',
    [string] $Destination = 'temp/deobfuscation',
    [switch] $List,
    [switch] $Verify,
    [switch] $Json,
    [string] $ArchiveRoot = 'c:\GD\WORK\FastMediaSorter\deobfuscation',
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/release/fetch-deobfuscation.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'

# Rule 7: under 'Stop' a bare Write-Error throws and swallows the exit code that
# follows, so every non-zero exit routes through here with -ErrorAction Continue.
function Exit-Fetch {
    param([Parameter(Mandatory)] [string] $Message, [Parameter(Mandatory)] [int] $Code)
    Write-Error "fetch-deobfuscation: $Message" -ErrorAction Continue
    exit $Code
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path -LiteralPath $ArchiveRoot)) {
    # Not exit 1: an absent archive root says nothing about whether any given
    # release was retained, only that the archive could not be consulted.
    Exit-Fetch -Message "archive root not found: $ArchiveRoot" -Code 2
}

# ----------------------------------------------------------------------
# Read every release manifest once. The archive is a flat set of versionCode
# folders, so this is the whole index.
# ----------------------------------------------------------------------
function Get-Releases {
    $releases = @()
    foreach ($dir in (Get-ChildItem -LiteralPath $ArchiveRoot -Directory -ErrorAction SilentlyContinue)) {
        $manifestPath = Join-Path $dir.FullName 'manifest.json'
        if (-not (Test-Path -LiteralPath $manifestPath)) { continue }
        try {
            $manifest = [System.IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
        }
        catch {
            Write-Warning "fetch-deobfuscation: unreadable manifest, skipped: $manifestPath"
            continue
        }
        $releases += [pscustomobject]@{
            VersionCode = [int]$manifest.versionCode
            VersionName = $manifest.versionName
            Variants    = @($manifest.variants)
            Dir         = $dir.FullName
        }
    }
    return @($releases | Sort-Object VersionCode)
}

$releases = Get-Releases

if ($List) {
    if ($Json) {
        $releases | ForEach-Object {
            [pscustomobject]@{
                versionCode = $_.VersionCode
                versionName = $_.VersionName
                variants    = @($_.Variants | ForEach-Object { $_.variant })
                bytes       = (@($_.Variants | ForEach-Object { [long]$_.payloadBytes }) | Measure-Object -Sum).Sum
            }
        } | ConvertTo-Json -Depth 5 -AsArray
        exit 0
    }

    if ($releases.Count -eq 0) {
        Write-Host "Archive is empty: $ArchiveRoot" -ForegroundColor Yellow
        exit 0
    }

    Write-Host "Retained releases in $ArchiveRoot" -ForegroundColor Cyan
    foreach ($release in $releases) {
        $bytes = (@($release.Variants | ForEach-Object { [long]$_.payloadBytes }) | Measure-Object -Sum).Sum
        $names = (@($release.Variants | ForEach-Object { $_.variant }) -join ', ')
        Write-Host ("  {0,-11} {1,-16} {2,7:N1} MB  [{3}]" -f $release.VersionCode, $release.VersionName, ($bytes / 1MB), $names)
    }
    exit 0
}

# ----------------------------------------------------------------------
# Resolve which release was asked for. Exactly one selector, because two of them
# can disagree and guessing which one the operator meant is worse than refusing.
# ----------------------------------------------------------------------
$selectors = @()
if ($VersionCode -gt 0) { $selectors += '-VersionCode' }
if ($VersionName)       { $selectors += '-VersionName' }
if ($Latest)            { $selectors += '-Latest' }

if ($selectors.Count -eq 0) {
    Exit-Fetch -Message 'no release selected: pass -VersionCode, -VersionName or -Latest, or use -List to see what is retained.' -Code 2
}
if ($selectors.Count -gt 1) {
    Exit-Fetch -Message "pass exactly one of -VersionCode / -VersionName / -Latest (got $($selectors -join ', '))." -Code 2
}

$target = $null
if ($Latest) {
    $target = $releases | Select-Object -Last 1
    if (-not $target) {
        Exit-Fetch -Message "archive holds no retained release: $ArchiveRoot" -Code 1
    }
}
elseif ($VersionCode -gt 0) {
    $target = $releases | Where-Object { $_.VersionCode -eq $VersionCode } | Select-Object -First 1
    if (-not $target) {
        Exit-Fetch -Message "versionCode $VersionCode is not retained. Run -List to see what is." -Code 1
    }
}
else {
    $matched = @($releases | Where-Object { $_.VersionName -eq $VersionName })
    if ($matched.Count -eq 0) {
        Exit-Fetch -Message "versionName $VersionName is not retained. Run -List to see what is." -Code 1
    }
    # Version names are derived from a timestamp and are unique in practice, but a
    # duplicate must not be resolved by silently picking one.
    if ($matched.Count -gt 1) {
        Exit-Fetch -Message ("versionName $VersionName matches several retained releases " +
            "($(($matched | ForEach-Object { $_.VersionCode }) -join ', ')). Re-run with -VersionCode.") -Code 2
    }
    $target = $matched[0]
}

$record = $target.Variants | Where-Object { $_.variant -eq $Variant } | Select-Object -First 1
if (-not $record) {
    $held = (@($target.Variants | ForEach-Object { $_.variant }) -join ', ')
    Exit-Fetch -Message "release $($target.VersionCode) is retained but holds no '$Variant' payload. It holds: $held." -Code 1
}

$payloadPath = Join-Path $target.Dir "$Variant-deobfuscation.zip"
if (-not (Test-Path -LiteralPath $payloadPath)) {
    Exit-Fetch -Message "manifest records '$Variant' for $($target.VersionCode) but the payload file is missing: $payloadPath" -Code 1
}

# ----------------------------------------------------------------------
# Verification mode. Reads the stored mapping back through the archive and
# recomputes its hash, rather than trusting the file's presence or its size: a
# cloud folder mid-sync typically presents a correctly sized placeholder, which
# is exactly the "retention succeeded into the void" failure this guards.
# ----------------------------------------------------------------------
if ($Verify) {
    $failure = $null
    $actualHash = $null

    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($payloadPath)
        try {
            $entry = $archive.GetEntry('mapping.txt')
            if (-not $entry) {
                $failure = 'payload-has-no-mapping'
            }
            else {
                $stream = $entry.Open()
                try {
                    $actualHash = (Get-FileHash -InputStream $stream -Algorithm SHA256).Hash.ToLower()
                }
                finally {
                    $stream.Dispose()
                }
            }
        }
        finally {
            $archive.Dispose()
        }
    }
    catch {
        $failure = 'payload-unreadable'
    }

    if (-not $failure -and $actualHash -ne "$($record.mappingSha256)".ToLower()) {
        $failure = 'hash-mismatch'
    }

    $result = [ordered]@{
        versionCode = $target.VersionCode
        versionName = $target.VersionName
        variant     = $Variant
        payload     = $payloadPath
        expected    = $record.mappingSha256
        actual      = $actualHash
        verdict     = if ($failure) { $failure } else { 'ok' }
    }

    if ($Json) { $result | ConvertTo-Json -Depth 4 }

    if ($failure) {
        Exit-Fetch -Message "verification FAILED for $Variant at $($target.VersionCode): $failure (expected $($record.mappingSha256), got $actualHash)" -Code 1
    }

    if (-not $Json) {
        Write-Host "Verified:     $Variant at $($target.VersionCode) reads back intact ($actualHash)" -ForegroundColor Green
    }
    exit 0
}

$outDir = Join-Path (Join-Path $Destination "$($target.VersionCode)") $Variant
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

Write-Host "Release:      $($target.VersionCode) ($($target.VersionName)), variant $Variant, source $($record.source)" -ForegroundColor Cyan
Write-Host "Extracting:   $payloadPath" -ForegroundColor Cyan

try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($payloadPath, $outDir, $true)
}
catch {
    Exit-Fetch -Message "payload could not be extracted: $payloadPath ($_)" -Code 1
}

$mappingOut = Join-Path $outDir 'mapping.txt'
if (-not (Test-Path -LiteralPath $mappingOut)) {
    Exit-Fetch -Message "payload extracted but carries no mapping.txt: $payloadPath" -Code 1
}

Write-Host "Symbols:      $($record.symbolCount) file(s) under $(Join-Path $outDir 'symbols')" -ForegroundColor Green
# Last line, deliberately bare: it is fed straight to a retrace tool or to
# assert-enum-persistence-contract.ps1 -Mapping.
Write-Host (Resolve-Path -LiteralPath $mappingOut).Path
exit 0
