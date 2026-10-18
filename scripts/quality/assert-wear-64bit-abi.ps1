#requires -Version 7.0
<#
.SYNOPSIS
  Asserts that a built wear APK carries the arm64-v8a ABI required by Google Play (S3364).

.DESCRIPTION
  Google Play makes 64-bit support mandatory for Wear OS apps from the date the
  threshold catalog records as Wear64BitDeadline (2026-09-15, verified 2026-09-21).
  This check asserts a built wear APK carries the arm64-v8a ABI by listing its
  lib/ entries straight from the zip central directory - no extraction to disk.

  The threshold date is read from store-prepublish-thresholds.psd1, never hardcoded
  here, so a store policy change is a data edit in the catalog.

  Exit codes:
    0 - arm64-v8a present in the checked APK.
    1 - the artifact exists but carries no arm64-v8a; output names the ABI set.
    2 - could not verify (CHECK-VERDICT 0.12 item A.2): no built wear release artifact exists
        on the tree (unless -AllowMissingArtifact, below; the date the mandate becomes binding
        is stated in the output), Wear64BitDeadline is missing from the catalog, -ApkPath was
        passed but not found, or the file is not a readable zip.

  The last stdout line is the verdict: `assert-wear-64bit-abi: PASS (..)`, `: FAIL (..)`,
  `: COULD NOT VERIFY (..)` or `: NOT APPLICABLE (..)`.

  -Gate is accepted for uniformity with the other scripts/quality/assert-*.ps1 gates
  and is a no-op here: batch runners invoke every gate with -Gate, and a
  parameter-binding error must not read as a FAIL of the thing being audited.

  -AllowMissingArtifact is the caller declaring the wear APK optional for this run - the
  release-scope runner passes it, because that APK exists only when the watch was built. With
  it, an absent APK ends `NOT APPLICABLE (..)` with exit 0, which every aggregator counts as
  skipped, never as passed (CHECK-VERDICT 0.12 item A.1). Without it an absent APK is exit 2.
#>
[CmdletBinding()]
param(
    [string] $ApkPath,
    [switch] $Gate,
    [switch] $AllowMissingArtifact
)

$ErrorActionPreference = 'Stop'

function Write-FailLine([string] $m) { Write-Error $m -ErrorAction Continue }

# Exit 2 with the verdict line last on stdout (CHECK-VERDICT rules 2.5 and 2.11).
function Stop-CouldNotVerify([string] $Why) {
    Write-FailLine "[wearabi] $Why"
    Write-Host "assert-wear-64bit-abi: COULD NOT VERIFY ($Why)"
    exit 2
}

# scripts/quality -> scripts -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Add-Type -AssemblyName System.IO.Compression.FileSystem

# --- Resolve the threshold date from the shared catalog ----------------------
$thresholds = Import-PowerShellDataFile (Join-Path $repoRoot 'scripts/quality/store-prepublish-thresholds.psd1')
$deadline = $thresholds['Wear64BitDeadline']
$deadlineNote = $thresholds['SourceNotes']['Wear64BitDeadline']
if (-not $deadline) {
    Stop-CouldNotVerify 'Usage error: Wear64BitDeadline missing from store-prepublish-thresholds.psd1.'
}

# --- Resolve the artifact ----------------------------------------------------
if ($PSBoundParameters.ContainsKey('ApkPath')) {
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        Stop-CouldNotVerify 'Usage error: -ApkPath was passed empty.'
    }
    if (-not (Test-Path $ApkPath)) {
        Stop-CouldNotVerify "Usage error: -ApkPath not found: $ApkPath"
    }
    $apk = (Resolve-Path $ApkPath).Path
}
else {
    # House convention: wear/build/outputs/apk/<flavor>/<buildType>/wear-<flavor>-<buildType>.apk
    $apkRoot = Join-Path $repoRoot 'wear/build/outputs/apk'
    $apk = Get-ChildItem -Path $apkRoot -Recurse -Filter *.apk -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]release[\\/]' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $apk) {
        Write-Host "[wearabi] Source: $deadlineNote"
        if ($AllowMissingArtifact) {
            Write-Host "assert-wear-64bit-abi: NOT APPLICABLE (no built wear release APK under $apkRoot; -AllowMissingArtifact; 64-bit support becomes mandatory from $deadline)"
            exit 0
        }
        Stop-CouldNotVerify "no built wear release APK under $apkRoot; 64-bit support becomes mandatory from $deadline, build the wear release first"
    }
}

# --- List lib/ ABIs from the zip central directory ---------------------------
try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
}
catch {
    Stop-CouldNotVerify "Usage error: cannot open $apk as a zip archive: $($_.Exception.Message)"
}
try {
    $abis = $zip.Entries.FullName |
        Where-Object { $_ -match '^lib/([^/]+)/' } |
        ForEach-Object { $Matches[1] } |
        Sort-Object -Unique
}
finally {
    $zip.Dispose()
}

if ($abis.Count -eq 0) {
    Write-FailLine "[wearabi] FAIL $apk carries no lib/<abi> entries at all - cannot prove 64-bit support."
    Write-Host 'assert-wear-64bit-abi: FAIL (the APK carries no lib/<abi> entries; 64-bit support cannot be proven)'
    exit 1
}

Write-Host "[wearabi] $apk ABI set: $($abis -join ', ')"
Write-Host "[wearabi] Mandatory-from date (Wear64BitDeadline): $deadline"

if ($abis -notcontains 'arm64-v8a') {
    Write-FailLine "[wearabi] FAIL arm64-v8a missing from: $($abis -join ', ') - Play requires 64-bit support from $deadline."
    Write-Host "assert-wear-64bit-abi: FAIL (arm64-v8a missing from: $($abis -join ', '))"
    exit 1
}

Write-Host "[wearabi] arm64-v8a present; the wear artifact satisfies the 64-bit mandate ($deadline)."
Write-Host "assert-wear-64bit-abi: PASS (arm64-v8a present in $($abis -join ', '))"
exit 0
