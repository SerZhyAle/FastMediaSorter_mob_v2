#requires -Version 7.0
<#
  assert-wear-64bit-abi.ps1 (S3364)

  Google Play makes 64-bit support mandatory for Wear OS apps from the date the
  threshold catalog records as Wear64BitDeadline (2026-09-15, verified 2026-09-21).
  This check asserts a built wear APK carries the arm64-v8a ABI by listing its
  lib/ entries straight from the zip central directory - no extraction to disk.

  The threshold date is read from store-prepublish-thresholds.psd1, never hardcoded
  here, so a store policy change is a data edit in the catalog.

  Exit codes:
    0 - arm64-v8a present in the checked APK, OR no built wear release artifact
        exists on the tree (advisory skip, stated in output together with the date
        the mandate becomes binding).
    1 - the artifact exists but carries no arm64-v8a; output names the ABI set.
    2 - usage error (-ApkPath passed but not found, or the file is not a readable
        zip).

  -Gate is accepted for uniformity with the other scripts/quality/assert-*.ps1 gates
  and is a no-op here: batch runners invoke every gate with -Gate, and a
  parameter-binding error must not read as a FAIL of the thing being audited.
#>
[CmdletBinding()]
param(
    [string] $ApkPath,
    [switch] $Gate
)

$ErrorActionPreference = 'Stop'

function Write-FailLine([string] $m) { Write-Error $m -ErrorAction Continue }

# scripts/quality -> scripts -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Add-Type -AssemblyName System.IO.Compression.FileSystem

# --- Resolve the threshold date from the shared catalog ----------------------
$thresholds = Import-PowerShellDataFile (Join-Path $repoRoot 'scripts/quality/store-prepublish-thresholds.psd1')
$deadline = $thresholds['Wear64BitDeadline']
$deadlineNote = $thresholds['SourceNotes']['Wear64BitDeadline']
if (-not $deadline) {
    Write-FailLine '[wearabi] Usage error: Wear64BitDeadline missing from store-prepublish-thresholds.psd1.'
    exit 2
}

# --- Resolve the artifact ----------------------------------------------------
if ($PSBoundParameters.ContainsKey('ApkPath')) {
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        Write-FailLine '[wearabi] Usage error: -ApkPath was passed empty.'
        exit 2
    }
    if (-not (Test-Path $ApkPath)) {
        Write-FailLine "[wearabi] Usage error: -ApkPath not found: $ApkPath"
        exit 2
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
        Write-Host "[wearabi] No built wear release APK under $apkRoot. Nothing to verify - advisory skip (exit 0). 64-bit support becomes mandatory from $deadline."
        Write-Host "[wearabi] Source: $deadlineNote"
        exit 0
    }
}

# --- List lib/ ABIs from the zip central directory ---------------------------
try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
}
catch {
    Write-FailLine "[wearabi] Usage error: cannot open $apk as a zip archive: $($_.Exception.Message)"
    exit 2
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
    exit 1
}

Write-Host "[wearabi] $apk ABI set: $($abis -join ', ')"
Write-Host "[wearabi] Mandatory-from date (Wear64BitDeadline): $deadline"

if ($abis -notcontains 'arm64-v8a') {
    Write-FailLine "[wearabi] FAIL arm64-v8a missing from: $($abis -join ', ') - Play requires 64-bit support from $deadline."
    exit 1
}

Write-Host "[wearabi] PASS - arm64-v8a present; the wear artifact satisfies the 64-bit mandate ($deadline)."
exit 0
