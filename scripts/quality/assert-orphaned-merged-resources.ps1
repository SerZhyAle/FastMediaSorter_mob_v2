#requires -Version 7.0
<#
.SYNOPSIS
    S1825: detect orphaned compiled resource artifacts (.flat) in merged_res.

.DESCRIPTION
    Incremental resource merging in AGP updates changed files but does not delete
    compiled .flat intermediate artifacts in build/intermediates/merged_res/ when
    a source resource file is deleted from src/*/res. This gate inspects .flat
    files in <module>/build/intermediates/merged_res/, maps each non-excluded
    artifact back to source resource directories (src/*/res or build/generated/res),
    and flags orphaned artifacts whose source files no longer exist.

    Exclusions (legitimate generated artifacts without direct source files):
      - values-*/*.arsc.flat (binary value aggregates compiled by AAPT2)
      - mipmap-anydpi*/ic_launcher*.flat (generated adaptive launcher icons)

    Exit codes (S1070):
      0 - clean (or audit mode).
      1 - substantive failure: at least one orphaned .flat artifact exists (-Gate only).
      2 - gate execution error (e.g. invalid module path).

.PARAMETER Gate
    Fail-closed: exit 1 if any orphaned .flat artifact is found.

.PARAMETER Quiet
    Suppress per-finding output; print only summary.

.PARAMETER Module
    Module to inspect ('app_v2' by default, or 'wear').

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-orphaned-merged-resources.ps1
    pwsh -NoProfile -File scripts/quality/assert-orphaned-merged-resources.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-orphaned-merged-resources.ps1 -Gate -Module wear
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [ValidateSet('app_v2', 'wear')]
    [string]$Module = 'app_v2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$moduleDir = Join-Path $repoRoot $Module

if (-not (Test-Path -LiteralPath $moduleDir)) {
    Write-Error "assert-orphaned-merged-resources: module directory not found at $moduleDir" -ErrorAction Continue
    exit 2
}

$mergedResDir = Join-Path $moduleDir 'build/intermediates/merged_res'
if (-not (Test-Path -LiteralPath $mergedResDir)) {
    if (-not $Quiet) {
        Write-Host "assert-orphaned-merged-resources: merged_res directory does not exist ($mergedResDir). Nothing to check." -ForegroundColor Gray
    }
    Write-Host "expected: 0 orphaned merged resource artifacts | actual: 0" -ForegroundColor Green
    exit 0
}

# Collect source resource directories under <module>/src/*/res and <module>/build/generated
$srcDir = Join-Path $moduleDir 'src'
$sourceResDirs = [System.Collections.Generic.List[string]]::new()

if (Test-Path -LiteralPath $srcDir) {
    foreach ($rd in Get-ChildItem -Path $srcDir -Recurse -Directory -Filter 'res' -ErrorAction SilentlyContinue) {
        $sourceResDirs.Add($rd.FullName)
    }
}

$genDir = Join-Path $moduleDir 'build/generated'
if (Test-Path -LiteralPath $genDir) {
    foreach ($rd in Get-ChildItem -Path $genDir -Recurse -Directory -Filter 'res' -ErrorAction SilentlyContinue) {
        $sourceResDirs.Add($rd.FullName)
    }
}

$flatFiles = Get-ChildItem -Path $mergedResDir -Filter '*.flat' -Recurse -ErrorAction SilentlyContinue

if ($null -eq $flatFiles -or $flatFiles.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-orphaned-merged-resources: no .flat files found in $mergedResDir." -ForegroundColor Gray
    }
    Write-Host "expected: 0 orphaned merged resource artifacts | actual: 0" -ForegroundColor Green
    exit 0
}

$orphans = [System.Collections.Generic.List[PSCustomObject]]::new()

foreach ($f in $flatFiles) {
    $filename = $f.Name
    # Exclusion 1: AAPT2 binary value aggregates (*.arsc.flat)
    if ($filename -like '*.arsc.flat') { continue }

    $nameWithoutExt = $filename.Substring(0, $filename.Length - 5) # remove .flat
    $firstIdx = $nameWithoutExt.IndexOf('_')
    if ($firstIdx -lt 0) { continue }

    $folder = $nameWithoutExt.Substring(0, $firstIdx)
    $realFile = $nameWithoutExt.Substring($firstIdx + 1)

    # Exclusion 2: Generated adaptive launcher icons (mipmap-anydpi*/ic_launcher*)
    if ($folder -match '^mipmap-anydpi' -and $realFile -match '^ic_launcher') { continue }

    # Candidate folder names:
    # 1. $folder (exact)
    # 2. $folder with trailing -v\d+ removed (e.g. layout-w600dp-v13 -> layout-w600dp)
    $candidates = [System.Collections.Generic.List[string]]::new()
    $candidates.Add($folder)
    if ($folder -match '-v\d+$') {
        $stripped = $folder -replace '-v\d+$', ''
        if ($stripped.Length -gt 0 -and -not $candidates.Contains($stripped)) {
            $candidates.Add($stripped)
        }
    }

    $found = $false
    foreach ($cand in $candidates) {
        foreach ($rd in $sourceResDirs) {
            $testPath = Join-Path $rd (Join-Path $cand $realFile)
            if (Test-Path -LiteralPath $testPath) {
                $found = $true
                break
            }
        }
        if ($found) { break }
    }

    if (-not $found) {
        # Derive variant name relative to mergedResDir
        $relPath = [System.IO.Path]::GetRelativePath($mergedResDir, $f.FullName)
        $variant = $relPath.Split([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)[0]
        $orphans.Add([PSCustomObject]@{
            FlatFile = $filename
            Variant  = $variant
            Folder   = $folder
            File     = $realFile
            FullPath = $f.FullName
        })
    }
}

if ($orphans.Count -gt 0) {
    if (-not $Quiet) {
        Write-Host "assert-orphaned-merged-resources: found $($orphans.Count) orphaned merged resource artifact(s):" -ForegroundColor Red
        foreach ($o in $orphans) {
            Write-Host "  [$($o.Variant)] $($o.FlatFile) (Folder: $($o.Folder), File: $($o.File))" -ForegroundColor Yellow
        }
    }
    Write-Host "expected: 0 orphaned merged resource artifacts | actual: $($orphans.Count)" -ForegroundColor Red
    if ($Gate) {
        exit 1
    }
    exit 0
}

if (-not $Quiet) {
    Write-Host "assert-orphaned-merged-resources: verified $($flatFiles.Count) .flat artifacts - 0 orphaned." -ForegroundColor Green
}
Write-Host "expected: 0 orphaned merged resource artifacts | actual: 0" -ForegroundColor Green
exit 0
