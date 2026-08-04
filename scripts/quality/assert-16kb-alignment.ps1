#requires -Version 7.0
<#
  assert-16kb-alignment.ps1 (S1149)

  Assert every shipped 64-bit native .so is 16 KB page-size aligned, as required by
  Google Play for apps targeting Android 15+ (API >= 35). Generalizes the one-off
  readelf check in scripts/builders/build-ffmpeg-dts.sh so future native-dependency
  bumps (Tesseract / MLKit / CameraX / OpenXR / Chaquopy / first-party AARs) are
  audited automatically instead of by manual spikes.

  Scope: 64-bit ABIs only (arm64-v8a, x86_64). Google Play's 16 KB requirement does
  not cover 32-bit (armeabi-v7a, x86), which stay 4 KB aligned by design.

  Needs a prior build: scans the AGP native-lib output (merged_native_libs) plus
  committed jniLibs. If no built .so exist yet, it is an advisory skip (exit 0).

  Exit codes:
    0 - all checked 64-bit .so have every LOAD segment aligned >= 16 KB, OR nothing
        to verify (no .so found) / readelf toolchain unavailable (advisory skip).
    1 - at least one 64-bit .so has a LOAD segment below 16 KB (Play would reject).
    2 - usage error (invalid -ScanRoot or -Readelf path).

  -Gate is accepted for uniformity with the other scripts/quality/assert-*.ps1 gates and
  is a no-op here: this gate already exits 1 on a finding with no advisory mode. Batch
  runners invoke every gate with -Gate, and a parameter-binding error reads as a FAIL of
  the thing being audited rather than of the invocation (S1338 step 04.5).
#>
[CmdletBinding()]
param(
    [string[]] $ScanRoot,
    [string]   $Readelf,
    [int]      $MinAlign = 16384,
    [switch]   $Gate
)

$ErrorActionPreference = 'Stop'

function Write-FailLine([string] $m) { Write-Error $m -ErrorAction Continue }

# scripts/quality -> scripts -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# --- Resolve scan roots -----------------------------------------------------
if (-not $ScanRoot -or $ScanRoot.Count -eq 0) {
    $ScanRoot = @(
        (Join-Path $repoRoot 'app_v2/build/intermediates/merged_native_libs'),
        (Join-Path $repoRoot 'app_v2/src')
    )
}
$roots = @()
foreach ($r in $ScanRoot) {
    if (Test-Path $r) { $roots += (Resolve-Path $r).Path }
}
if ($roots.Count -eq 0) {
    Write-Host '[16k] Nothing to verify: no scan root exists yet (build native libs first). Advisory skip.'
    exit 0
}

# --- Resolve readelf --------------------------------------------------------
function Resolve-Readelf {
    param([string] $Explicit)
    if ($Explicit) {
        if (Test-Path $Explicit) { return (Resolve-Path $Explicit).Path }
        Write-FailLine "[16k] -Readelf path not found: $Explicit"
        exit 2
    }
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
    if ($sdk -and (Test-Path (Join-Path $sdk 'ndk'))) {
        $hit = Get-ChildItem (Join-Path $sdk 'ndk') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($hit) { return $hit }
    }
    foreach ($name in @('llvm-readelf', 'readelf')) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    return $null
}

$readelfPath = Resolve-Readelf -Explicit $Readelf
if (-not $readelfPath) {
    Write-Host '[16k] readelf/llvm-readelf not found (NDK or PATH). Cannot verify - advisory skip (exit 0).'
    exit 0
}

# --- Collect unique 64-bit .so (dedup identical copies across variants) ------
$abiRegex = '[\\/](arm64-v8a|x86_64)[\\/]'
$seen = @{}
$unique = @()
foreach ($root in $roots) {
    $found = Get-ChildItem -Path $root -Recurse -Filter *.so -File -ErrorAction SilentlyContinue
    foreach ($f in $found) {
        if ($f.FullName -notmatch $abiRegex) { continue }
        $abi = $Matches[1]
        $key = "$abi/$($f.Name)"
        if (-not $seen.ContainsKey($key)) {
            $seen[$key] = $true
            $unique += [pscustomobject]@{ Abi = $abi; Name = $f.Name; Path = $f.FullName }
        }
    }
}
if ($unique.Count -eq 0) {
    Write-Host '[16k] No 64-bit .so found under scan roots. Nothing to verify - advisory skip (exit 0).'
    exit 0
}

# --- Read LOAD-segment alignments via readelf -------------------------------
function Get-LoadAligns {
    param([string] $So)
    $raw = & $readelfPath -lW $So 2>&1
    $aligns = @()
    foreach ($line in $raw) {
        if ($line -match '^\s*LOAD\b') {
            $tok = ($line -split '\s+') | Where-Object { $_ -ne '' }
            $last = $tok[-1]
            if ($last -match '^0x[0-9a-fA-F]+$') {
                $aligns += [Convert]::ToInt64(($last -replace '^0x', ''), 16)
            }
        }
    }
    return , $aligns
}

$failures = @()
$passed = 0
foreach ($lib in $unique) {
    $aligns = Get-LoadAligns -So $lib.Path
    if ($aligns.Count -eq 0) {
        Write-Host ('[16k] WARN {0}/{1}: no LOAD alignment parsed - skipping' -f $lib.Abi, $lib.Name)
        continue
    }
    $min = ($aligns | Measure-Object -Minimum).Minimum
    if ($min -lt $MinAlign) {
        $failures += [pscustomobject]@{ Abi = $lib.Abi; Name = $lib.Name; Align = $min }
        Write-Host ('[16k] FAIL {0}/{1}: min LOAD align 0x{2:x} (< 0x{3:x})' -f $lib.Abi, $lib.Name, $min, $MinAlign)
    }
    else {
        $passed++
    }
}

$tool = Split-Path $readelfPath -Leaf
Write-Host ('[16k] Checked {0} unique 64-bit .so via {1}: {2} passed, {3} failed.' -f $unique.Count, $tool, $passed, $failures.Count)

if ($failures.Count -gt 0) {
    Write-FailLine "[16k] $($failures.Count) native lib(s) below 16 KB alignment - Google Play will reject this artifact."
    Write-FailLine '[16k] Fix: rebuild/repackage with -Wl,-z,max-page-size=16384 (NDK r27c+) or bump the dependency to a 16 KB-aligned release.'
    exit 1
}

Write-Host '[16k] PASS - all shipped 64-bit native libs are 16 KB page-size aligned.'
exit 0
