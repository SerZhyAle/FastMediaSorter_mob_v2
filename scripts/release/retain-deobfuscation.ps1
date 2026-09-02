#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Retain one release variant's deobfuscation payload - the R8 mapping and the
    native debug symbols - in the out-of-repo archive, keyed by versionCode.

.DESCRIPTION
    Spec S1695 - release-deobfuscation-artifact-retention.

    Gradle overwrites app_v2/build/outputs/mapping/<variant>/mapping.txt on every
    release build, so exactly one mapping survives locally: the newest one. Once a
    release has shipped and another build has run over it, nothing local can decode
    a stack trace from it any more. This script stores the payload under a
    versionCode key so every shipped release stays decodable.

    Archive layout:
        <ArchiveRoot>\<versionCode>\<variant>-deobfuscation.zip
        <ArchiveRoot>\<versionCode>\manifest.json

    The payload zip holds mapping.txt at its root and symbols/<abi>/<lib>.so.dbg
    beneath it, whichever source produced them.

    Exit codes:
      0 - payload stored, or already present with an identical mapping.
      1 - retention failed: the source could not be read, the bundle carries no
          mapping, or a differing payload is already stored and -Force was absent.
      2 - cannot verify: the archive root is unreachable, or no source was given.

.PARAMETER VersionCode
    The release's versionCode. This is the archive key, chosen because it is also
    how the store identifies the release.

.PARAMETER VersionName
    The release's versionName, recorded in the manifest so an operator holding a
    version string rather than a code can still find the release.

.PARAMETER Variant
    Which published variant this payload belongs to.

.PARAMETER Bundle
    Path to the released .aab. Preferred source: the bundle is exactly what
    reached the user, so the payload's correspondence with the release needs no
    discipline to hold.

.PARAMETER Mapping
    Path to a loose mapping.txt. Used for variants that ship as an APK and
    therefore have no bundle to extract from.

.PARAMETER NativeSymbols
    Optional path to native debug symbols accompanying -Mapping: either a
    directory of <abi>/<lib>.so.dbg files, or an AGP native-debug-symbols.zip.

.PARAMETER ArchiveRoot
    Root of the archive. Defaults to the resolved Deobfuscation artifact sink - the cloud
    folder the build scripts already publish APKs into, so it syncs without human action
    and survives a machine reinstall.

.PARAMETER Force
    Overwrite an already-stored payload whose mapping differs from the incoming
    one. Without it, that case is an error rather than a silent overwrite.

.PARAMETER DryRun
    Report what would be stored and where, then exit without writing.

.EXAMPLE
    pwsh -File scripts/release/retain-deobfuscation.ps1 -Variant standard `
        -VersionCode 260812204 -VersionName 2.60.8122.040 `
        -Bundle app_v2/build/outputs/bundle/standardRelease/app_v2-standard-release.aab
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [int]    $VersionCode,
    [string] $VersionName,
    [Parameter(Mandatory)]
    [ValidateSet('standard', 'lite', 'photos', 'legacy', 'vr', 'noLegal', 'wear')]
    [string] $Variant,
    [string] $Bundle,
    [string] $Mapping,
    [string] $NativeSymbols,
    [string] $ArchiveRoot,
    [switch] $Force,
    [switch] $DryRun,
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/release/retain-deobfuscation.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'

# Every non-zero exit routes through here. Rule 7: under $ErrorActionPreference =
# 'Stop' a bare Write-Error throws, which would swallow the exit code that follows
# and report 1 for everything - so the message is emitted with -ErrorAction Continue
# and the intended code is returned explicitly.
function Exit-Retain {
    param([Parameter(Mandatory)] [string] $Message, [Parameter(Mandatory)] [int] $Code)
    Write-Error "retain-deobfuscation: $Message" -ErrorAction Continue
    exit $Code
}

if ($VersionCode -le 0) {
    Exit-Retain -Message "-VersionCode must be a positive integer (got $VersionCode)." -Code 2
}

. "$PSScriptRoot\..\utils\project-paths.ps1"

if (-not $ArchiveRoot) {
    $ArchiveRoot = Get-ArtifactSink -Kind Deobfuscation -Quiet
    if (-not $ArchiveRoot) {
        Exit-Retain -Message ("deobfuscation archive not reachable on this machine - " +
            "set FMS_SINK_DEOBFUSCATION or pass -ArchiveRoot.") -Code 2
    }
}

# ----------------------------------------------------------------------
# Resolve the archive location. An archive root that cannot be created or
# written is exit 2, never exit 1: nothing was inspected, so nothing can be
# asserted about whether this release is retained. The distinction matters
# because "the cloud folder is not mounted" and "the artifact is missing" call
# for different operator actions.
# ----------------------------------------------------------------------
$releaseDir = Join-Path $ArchiveRoot "$VersionCode"
$payloadPath = Join-Path $releaseDir "$Variant-deobfuscation.zip"
$manifestPath = Join-Path $releaseDir 'manifest.json'

# Called only once a source has been validated and a write is actually about to
# happen. Preparing the directory earlier would leave an empty release folder
# behind on every failed invocation, and the retention gate reads the archive by
# walking those folders - an empty one is indistinguishable from a release whose
# payload went missing.
function Initialize-ArchiveDir {
    try {
        if (-not (Test-Path -LiteralPath $releaseDir)) {
            New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
        }
        # Creating the directory can succeed on a stale mount that then refuses
        # writes, so prove writability rather than inferring it from the mkdir.
        $probe = Join-Path $releaseDir '.write-probe'
        Set-Content -LiteralPath $probe -Value 'probe' -NoNewline
        Remove-Item -LiteralPath $probe -Force
    }
    catch {
        Exit-Retain -Message "archive root is not writable: $ArchiveRoot ($_)" -Code 2
    }
}

Write-Host "Variant:      $Variant" -ForegroundColor Cyan
Write-Host "Version:      $VersionName (code $VersionCode)" -ForegroundColor Cyan
Write-Host "Archive:      $payloadPath" -ForegroundColor Cyan

Add-Type -AssemblyName System.IO.Compression.FileSystem

# AGP seals both payloads into the bundle under fixed metadata paths.
$MappingEntryName = 'BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map'
$SymbolsPrefix    = 'BUNDLE-METADATA/com.android.tools.build.debugsymbols/'

# Copy a stream into a zip entry and return the SHA-256 of what went through, as
# a side effect of the same pass. A second pass over the mapping to hash it would
# re-read 179 MB for a number the copy already had in hand.
function Copy-StreamToEntryWithHash {
    param(
        [Parameter(Mandatory)] $Source,
        [Parameter(Mandatory)] $Entry
    )

    $outStream = $Entry.Open()
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $crypto = New-Object System.Security.Cryptography.CryptoStream(
            $outStream, $sha, [System.Security.Cryptography.CryptoStreamMode]::Write)
        try {
            $Source.CopyTo($crypto)
            $crypto.FlushFinalBlock()
        }
        finally {
            $crypto.Dispose()
        }
        return (($sha.Hash | ForEach-Object { $_.ToString('x2') }) -join '')
    }
    finally {
        $sha.Dispose()
        $outStream.Dispose()
    }
}

function Get-StoredRecord {
    if (-not (Test-Path -LiteralPath $manifestPath)) { return $null }
    try {
        $manifest = [System.IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
    }
    catch {
        Exit-Retain -Message "manifest is unreadable, refusing to overwrite it: $manifestPath ($_)" -Code 1
    }
    return @($manifest.variants | Where-Object { $_.variant -eq $Variant }) | Select-Object -First 1
}

# Commits the staged payload, or refuses to. A stored payload is never silently
# replaced: overwriting it would reintroduce exactly the loss this ticket exists
# to end, only inside the archive that was supposed to prevent it.
function Complete-Retention {
    param(
        [Parameter(Mandatory)] [string] $StagingPath,
        [Parameter(Mandatory)] [string] $MappingHash,
        [Parameter(Mandatory)] [long]   $MappingBytes,
        [Parameter(Mandatory)] [int]    $SymbolCount,
        [Parameter(Mandatory)] [string] $SourceKind
    )

    $stored = Get-StoredRecord
    $payloadPresent = Test-Path -LiteralPath $payloadPath

    if ($stored -and $payloadPresent -and $stored.mappingSha256 -eq $MappingHash) {
        Remove-Item -LiteralPath $StagingPath -Force
        Write-Host "Already retained: $Variant at versionCode $VersionCode is identical, nothing rewritten." -ForegroundColor Green
        exit 0
    }

    if ($stored -and $payloadPresent -and -not $Force) {
        Remove-Item -LiteralPath $StagingPath -Force
        Exit-Retain -Message ("$Variant at versionCode $VersionCode is already retained with a different mapping. " +
            "stored $($stored.mappingSha256), incoming $MappingHash. " +
            'Pass -Force only if the stored payload is known to be wrong.') -Code 1
    }

    Move-Item -LiteralPath $StagingPath -Destination $payloadPath -Force

    $record = [ordered]@{
        variant       = $Variant
        versionName   = $VersionName
        versionCode   = $VersionCode
        source        = $SourceKind
        mappingSha256 = $MappingHash
        mappingBytes  = $MappingBytes
        symbolCount   = $SymbolCount
        payloadBytes  = (Get-Item -LiteralPath $payloadPath).Length
        storedUtc     = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    }

    # Merge rather than replace: the variants of one release are stored by
    # separate invocations, so a wholesale rewrite would drop the ones already there.
    $variants = @()
    if (Test-Path -LiteralPath $manifestPath) {
        $existing = [System.IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
        $variants = @($existing.variants | Where-Object { $_.variant -ne $Variant })
    }
    $variants += [pscustomobject]$record

    $manifest = [ordered]@{
        versionCode = $VersionCode
        versionName = $VersionName
        variants    = @($variants | Sort-Object variant)
    }
    [System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 5))

    Write-Host "Stored:       mapping.txt + $SymbolCount symbol file(s) from $SourceKind" -ForegroundColor Green
    Write-Host "Payload:      $([math]::Round($record.payloadBytes / 1MB, 2)) MB" -ForegroundColor Green
    Write-Host "Manifest:     $manifestPath" -ForegroundColor Green
}

# ----------------------------------------------------------------------
# Bundle source. Preferred wherever a bundle exists, because the .aab is
# byte-for-byte what shipped, so the payload cannot drift from the release it
# claims to describe. Measured on the release worktree 2026-08-15: the loose
# artifacts under build/outputs do NOT agree with each other - the native
# metadata was three weeks older than the mapping beside it.
# ----------------------------------------------------------------------
function Copy-BundlePayload {
    param(
        [Parameter(Mandatory)] [string] $BundlePath,
        [string] $DestinationZip,
        [switch] $PlanOnly
    )

    $source = [System.IO.Compression.ZipFile]::OpenRead($BundlePath)
    try {
        $mappingEntry = $source.Entries | Where-Object { $_.FullName -eq $MappingEntryName } | Select-Object -First 1
        if (-not $mappingEntry) {
            # An unminified bundle must never be recorded as retained: the archive
            # would claim a decodable release that cannot be decoded.
            return $null
        }

        $symbolEntries = @(
            $source.Entries |
                Where-Object { $_.FullName.StartsWith($SymbolsPrefix) -and $_.FullName.EndsWith('.so.dbg') } |
                Sort-Object FullName
        )

        if ($PlanOnly) {
            return [pscustomobject]@{
                MappingBytes = $mappingEntry.Length
                SymbolCount  = $symbolEntries.Count
                SymbolNames  = @($symbolEntries | ForEach-Object { $_.FullName.Substring($SymbolsPrefix.Length) })
            }
        }

        $dest = [System.IO.Compression.ZipFile]::Open($DestinationZip, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            $mappingOut = $dest.CreateEntry('mapping.txt', [System.IO.Compression.CompressionLevel]::Optimal)
            $inStream = $mappingEntry.Open()
            try {
                # CopyTo streams the entry through a fixed buffer. The mapping is
                # ~179 MB of text, so materialising it would cost the whole file in
                # memory for no gain.
                $mappingHash = Copy-StreamToEntryWithHash -Source $inStream -Entry $mappingOut
            }
            finally {
                $inStream.Dispose()
            }

            foreach ($symbol in $symbolEntries) {
                $relative = $symbol.FullName.Substring($SymbolsPrefix.Length)
                $symbolOut = $dest.CreateEntry("symbols/$relative", [System.IO.Compression.CompressionLevel]::Optimal)
                $symIn = $symbol.Open()
                $symOut = $symbolOut.Open()
                try {
                    $symIn.CopyTo($symOut)
                }
                finally {
                    $symOut.Dispose()
                    $symIn.Dispose()
                }
            }
        }
        finally {
            $dest.Dispose()
        }

        return [pscustomobject]@{
            MappingBytes = $mappingEntry.Length
            MappingHash  = $mappingHash
            SymbolCount  = $symbolEntries.Count
            SymbolNames  = @($symbolEntries | ForEach-Object { $_.FullName.Substring($SymbolsPrefix.Length) })
        }
    }
    finally {
        $source.Dispose()
    }
}

# A stored payload whose mapping is a different SIZE cannot be the same mapping,
# so the conflict is decidable before extracting 179 MB to learn the hash.
function Assert-NoSizeConflict {
    param([Parameter(Mandatory)] [long] $IncomingBytes)

    if ($Force) { return }
    $stored = Get-StoredRecord
    if (-not $stored) { return }
    if (-not (Test-Path -LiteralPath $payloadPath)) { return }
    if ([long]$stored.mappingBytes -eq $IncomingBytes) { return }

    Exit-Retain -Message ("$Variant at versionCode $VersionCode is already retained with a different mapping " +
        "($($stored.mappingBytes) bytes stored, $IncomingBytes incoming). " +
        'Pass -Force only if the stored payload is known to be wrong.') -Code 1
}

$sourceKind = $null
if ($Bundle) {
    if (-not (Test-Path -LiteralPath $Bundle)) {
        Exit-Retain -Message "bundle not found: $Bundle" -Code 1
    }
    $sourceKind = 'bundle'

    if ($DryRun) {
        $plan = Copy-BundlePayload -BundlePath $Bundle -PlanOnly
        if (-not $plan) {
            Exit-Retain -Message "bundle carries no $MappingEntryName - it is not a minified release build: $Bundle" -Code 1
        }
        Write-Host "Source:       bundle $Bundle" -ForegroundColor Green
        Write-Host "Would store:  mapping.txt ($($plan.MappingBytes) bytes), $($plan.SymbolCount) .so.dbg entries" -ForegroundColor Green
        foreach ($name in $plan.SymbolNames) { Write-Host "              symbols/$name" -ForegroundColor DarkGray }
        exit 0
    }

    Initialize-ArchiveDir

    $preflight = Copy-BundlePayload -BundlePath $Bundle -PlanOnly
    if (-not $preflight) {
        Exit-Retain -Message "bundle carries no $MappingEntryName - it is not a minified release build: $Bundle" -Code 1
    }
    Assert-NoSizeConflict -IncomingBytes $preflight.MappingBytes

    # Build beside the target and move into place, so an interrupted run cannot
    # leave a truncated payload that later reads as a retained release.
    $stagingPath = "$payloadPath.tmp"
    if (Test-Path -LiteralPath $stagingPath) { Remove-Item -LiteralPath $stagingPath -Force }

    $result = Copy-BundlePayload -BundlePath $Bundle -DestinationZip $stagingPath

    Complete-Retention -StagingPath $stagingPath -MappingHash $result.MappingHash `
        -MappingBytes $result.MappingBytes -SymbolCount $result.SymbolCount -SourceKind 'bundle'
    exit 0
}

# ----------------------------------------------------------------------
# Loose-file source. Only 'standard' is built with bundleStandardRelease; every
# other flavor and wear ships as an APK from assemble*Release, so no bundle
# exists to extract from and the mapping has to come from build/outputs. The
# correspondence guarantee is weaker here - it rests on the file having been
# written by the same build run - which is why the manifest records the source
# kind rather than letting this pass as bundle-grade evidence.
# ----------------------------------------------------------------------
function Get-LooseSymbolSources {
    param([string] $Path)

    if (-not $Path) { return @() }
    if (-not (Test-Path -LiteralPath $Path)) {
        # Asked for symbols and silently storing none would produce a payload that
        # looks retained and cannot decode a native frame. Say so; the manifest's
        # symbolCount of 0 is then a corroborated fact rather than an accident.
        Write-Warning "retain-deobfuscation: -NativeSymbols path not found, storing mapping only: $Path"
        return @()
    }

    if ((Test-Path -LiteralPath $Path -PathType Leaf) -and $Path.EndsWith('.zip')) {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
        try {
            return @(
                $zip.Entries |
                    Where-Object { $_.FullName.EndsWith('.so.dbg') -or $_.FullName.EndsWith('.so') } |
                    ForEach-Object { [pscustomobject]@{ Relative = $_.FullName; FromZip = $Path } } |
                    Sort-Object Relative
            )
        }
        finally {
            $zip.Dispose()
        }
    }

    $root = (Resolve-Path -LiteralPath $Path).Path
    return @(
        Get-ChildItem -LiteralPath $root -Recurse -File |
            Where-Object { $_.Name.EndsWith('.so.dbg') -or $_.Name.EndsWith('.so') } |
            ForEach-Object {
                [pscustomobject]@{
                    Relative = $_.FullName.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
                    FromZip  = $null
                    FullName = $_.FullName
                }
            } | Sort-Object Relative
    )
}

if (-not $Mapping) {
    Exit-Retain -Message "no source given: pass -Bundle <aab> for a bundled variant, or -Mapping <mapping.txt> for an APK-only one." -Code 2
}
if (-not (Test-Path -LiteralPath $Mapping)) {
    Exit-Retain -Message "mapping not found: $Mapping" -Code 1
}

$sourceKind = 'outputs'
$mappingInfo = Get-Item -LiteralPath $Mapping
$symbolSources = Get-LooseSymbolSources -Path $NativeSymbols

if ($DryRun) {
    Write-Host "Source:       outputs $Mapping" -ForegroundColor Green
    Write-Host "Would store:  mapping.txt ($($mappingInfo.Length) bytes), $($symbolSources.Count) symbol file(s)" -ForegroundColor Green
    foreach ($symbol in $symbolSources) { Write-Host "              symbols/$($symbol.Relative)" -ForegroundColor DarkGray }
    exit 0
}

Initialize-ArchiveDir
Assert-NoSizeConflict -IncomingBytes $mappingInfo.Length

$stagingPath = "$payloadPath.tmp"
if (Test-Path -LiteralPath $stagingPath) { Remove-Item -LiteralPath $stagingPath -Force }

$dest = [System.IO.Compression.ZipFile]::Open($stagingPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $mappingOut = $dest.CreateEntry('mapping.txt', [System.IO.Compression.CompressionLevel]::Optimal)
    $inStream = [System.IO.File]::OpenRead($Mapping)
    try {
        $mappingHash = Copy-StreamToEntryWithHash -Source $inStream -Entry $mappingOut
    }
    finally {
        $inStream.Dispose()
    }

    foreach ($symbol in $symbolSources) {
        $symbolOut = $dest.CreateEntry("symbols/$($symbol.Relative)", [System.IO.Compression.CompressionLevel]::Optimal)
        $symOut = $symbolOut.Open()
        try {
            if ($symbol.FromZip) {
                $srcZip = [System.IO.Compression.ZipFile]::OpenRead($symbol.FromZip)
                try {
                    $entry = $srcZip.GetEntry($symbol.Relative)
                    $symIn = $entry.Open()
                    try { $symIn.CopyTo($symOut) } finally { $symIn.Dispose() }
                }
                finally {
                    $srcZip.Dispose()
                }
            }
            else {
                $symIn = [System.IO.File]::OpenRead($symbol.FullName)
                try { $symIn.CopyTo($symOut) } finally { $symIn.Dispose() }
            }
        }
        finally {
            $symOut.Dispose()
        }
    }
}
finally {
    $dest.Dispose()
}

Complete-Retention -StagingPath $stagingPath -MappingHash $mappingHash `
    -MappingBytes $mappingInfo.Length -SymbolCount $symbolSources.Count -SourceKind 'outputs'
exit 0
