#requires -Version 7.0
<#
  assert-meta-packaging-limits.ps1 (S3364)

  Measures a built VR-flavor APK against the Meta VRC packaging limits recorded in
  store-prepublish-thresholds.psd1 (verified 2026-09-21): APK size vs MetaMaxApkBytes,
  expansion file size vs MetaMaxObbBytes, minimum signature scheme MetaMinSignatureScheme
  via apksigner, and presence of MetaRequiredAbi among the lib/ entries. Every executed
  measurement is printed beside its limit; limits are read from the catalog, never
  hardcoded here.

  Exit codes:
    0 - every executed measurement passes, OR there is nothing to verify (no built
        VR-flavor release APK on the tree - advisory skip, stated in output).
    1 - at least one measurement exceeds or misses its limit; every failing
        measurement is named as measured value vs limit.
    2 - usage error (-ApkPath/-ObbPath passed but not found, -ApkPath is not a
        readable zip while every measurement else passed).

  -Gate is accepted for uniformity with the other scripts/quality/assert-*.ps1 gates
  and is a no-op here: batch runners invoke every gate with -Gate, and a
  parameter-binding error must not read as a FAIL of the thing being audited.
#>
[CmdletBinding()]
param(
    [string] $ApkPath,
    [string] $ObbPath,
    [switch] $Gate
)

$ErrorActionPreference = 'Stop'

function Write-FailLine([string] $m) { Write-Error $m -ErrorAction Continue }

# scripts/quality -> scripts -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Add-Type -AssemblyName System.IO.Compression.FileSystem

# --- Resolve thresholds from the shared catalog ------------------------------
$thresholds = Import-PowerShellDataFile (Join-Path $repoRoot 'scripts/quality/store-prepublish-thresholds.psd1')
$maxApk = $thresholds['MetaMaxApkBytes']
$maxObb = $thresholds['MetaMaxObbBytes']
$requiredAbi = $thresholds['MetaRequiredAbi']
$minScheme = $thresholds['MetaMinSignatureScheme']
foreach ($pair in @(@('MetaMaxApkBytes', $maxApk), @('MetaMaxObbBytes', $maxObb), @('MetaRequiredAbi', $requiredAbi), @('MetaMinSignatureScheme', $minScheme))) {
    if ($null -eq $pair[1]) {
        Write-FailLine "[meta] Usage error: $($pair[0]) missing from store-prepublish-thresholds.psd1."
        exit 2
    }
}

# --- Resolve the artifact ----------------------------------------------------
if ($PSBoundParameters.ContainsKey('ApkPath')) {
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        Write-FailLine '[meta] Usage error: -ApkPath was passed empty.'
        exit 2
    }
    if (-not (Test-Path $ApkPath)) {
        Write-FailLine "[meta] Usage error: -ApkPath not found: $ApkPath"
        exit 2
    }
    if ($PSBoundParameters.ContainsKey('ObbPath')) {
        if ([string]::IsNullOrWhiteSpace($ObbPath)) {
            Write-FailLine '[meta] Usage error: -ObbPath was passed empty.'
            exit 2
        }
        if (-not (Test-Path $ObbPath)) {
            Write-FailLine "[meta] Usage error: -ObbPath not found: $ObbPath"
            exit 2
        }
    }
    $apk = (Resolve-Path $ApkPath).Path
}
else {
    # House convention: app_v2/build/outputs/apk/<flavor>/<buildType>/<module>-<flavor>-<buildType>.apk
    $apkRoot = Join-Path $repoRoot 'app_v2/build/outputs/apk'
    $apk = Get-ChildItem -Path $apkRoot -Recurse -Filter *.apk -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]vr[\\/]' -and $_.FullName -match '[\\/]release[\\/]' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $apk) {
        Write-Host "[meta] No built VR-flavor release APK under $apkRoot. Nothing to verify - advisory skip (exit 0)."
        exit 0
    }
}

$failures = 0

# --- Measurement 1: APK size vs MetaMaxApkBytes ------------------------------
$apkBytes = (Get-Item -Path $apk).Length
Write-Host ("[meta] APK size: {0:N0} bytes vs MetaMaxApkBytes={1:N0} - {2}" -f $apkBytes, $maxApk, $(if ($apkBytes -lt $maxApk) { 'PASS' } else { 'FAIL' }))
if ($apkBytes -ge $maxApk) {
    $failures++
    Write-FailLine ("[meta] FAIL APK size {0:N0} exceeds MetaMaxApkBytes={1:N0} (1 GB)." -f $apkBytes, $maxApk)
}

# --- Measurement 2: OBB size vs MetaMaxObbBytes ------------------------------
if ($PSBoundParameters.ContainsKey('ObbPath')) {
    $obbBytes = (Get-Item -Path $ObbPath).Length
    Write-Host ("[meta] OBB size: {0:N0} bytes vs MetaMaxObbBytes={1:N0} - {2}" -f $obbBytes, $maxObb, $(if ($obbBytes -lt $maxObb) { 'PASS' } else { 'FAIL' }))
    if ($obbBytes -ge $maxObb) {
        $failures++
        Write-FailLine ("[meta] FAIL OBB size {0:N0} exceeds MetaMaxObbBytes={1:N0} (4 GB)." -f $obbBytes, $maxObb)
    }
}
else {
    Write-Host '[meta] OBB size: no -ObbPath given - measurement skipped (stated, not counted).'
}

# --- Measurement 3: signature scheme vs MetaMinSignatureScheme ----------------
function Resolve-Apksigner {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) {
        $lp = Join-Path $repoRoot 'local.properties'
        if (Test-Path $lp) {
            $line = Select-String -Path $lp -Pattern '^\s*sdk\.dir\s*=\s*(.+)$' | Select-Object -First 1
            if ($line) {
                # Java .properties escaping: '\\' -> '\', '\:' -> ':'
                $sdk = $line.Matches[0].Groups[1].Value.Trim() -replace '\\\\', '\' -replace '\\:', ':'
            }
        }
    }
    if ($sdk) {
        $hit = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($hit) { return $hit }
    }
    $cmd = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$apksigner = Resolve-Apksigner
if (-not $apksigner) {
    Write-Host '[meta] Signature scheme: apksigner not found (SDK build-tools or PATH) - measurement skipped. Advisory skip of this measurement only.'
}
else {
    $verifyOutput = & $apksigner verify --verbose $apk 2>&1
    $schemePattern = "$minScheme scheme\s*:\s*true"
    $schemeOk = @($verifyOutput) | Where-Object { $_ -match $schemePattern }
    if ($schemeOk) {
        Write-Host "[meta] Signature scheme: $minScheme verified by apksigner vs MetaMinSignatureScheme=$minScheme - PASS"
    }
    else {
        $failures++
        $excerpt = (@($verifyOutput) | Select-Object -First 3) -join ' | '
        Write-FailLine "[meta] FAIL signature scheme $minScheme not verified (MetaMinSignatureScheme=$minScheme). apksigner said: $excerpt"
    }
}

# --- Measurement 4: MetaRequiredAbi present under lib/ ------------------------
$abis = $null
try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $abis = $zip.Entries.FullName |
            Where-Object { $_ -match '^lib/([^/]+)/' } |
            ForEach-Object { $Matches[1] } |
            Sort-Object -Unique
    }
    finally {
        $zip.Dispose()
    }
}
catch {
    if ($failures -gt 0) {
        Write-FailLine "[meta] FAIL lib/<abi> listing unreadable in $apk - $($_.Exception.Message)"
        $failures++
    }
    else {
        Write-FailLine "[meta] Usage error: cannot open $apk as a zip archive: $($_.Exception.Message)"
        exit 2
    }
}

if ($null -ne $abis) {
    if ($abis.Count -eq 0) {
        $failures++
        Write-FailLine "[meta] FAIL $apk carries no lib/<abi> entries at all - MetaRequiredAbi=$requiredAbi cannot be present."
    }
    else {
        Write-Host "[meta] ABI set: $($abis -join ', ')"
        if ($abis -notcontains $requiredAbi) {
            $failures++
            Write-FailLine "[meta] FAIL $requiredAbi missing from ABI set: $($abis -join ', ') (MetaRequiredAbi=$requiredAbi)."
        }
        else {
            Write-Host "[meta] ABI: $requiredAbi present vs MetaRequiredAbi=$requiredAbi - PASS"
        }
    }
}

if ($failures -gt 0) {
    Write-FailLine "[meta] $failures Meta packaging violation(s) in $apk."
    exit 1
}

Write-Host "[meta] PASS - every executed measurement is within the Meta VRC packaging limits."
exit 0
