#requires -Version 7.0
<#
.SYNOPSIS
    Copy the FastMediaSorter family master folder from the contracts catalog into assets/identity/family/ (S4784).

.DESCRIPTION
    PRODUCT-IDENTITY 0.2 section 3.3 rule 17: the product's master is generated from the bytes of the family master
    folder, so the repository keeps a byte-for-byte copy and the build never needs the catalog. This script is the
    only writer of assets/identity/family/. It reads family-mark.json in the catalog, checks every file it lists
    against the SHA-256 recorded there, and only then copies the manifest and the files, keeping relative paths.
    A catalog file that disagrees with its own recorded hash writes nothing.

    -Check compares the local copy with the catalog instead of writing.

.PARAMETER CatalogRoot
    Root of the shared contracts catalog. Falls back to FMS_CONTRACTS_ROOT in the process, then at user scope.

.PARAMETER Check
    Compare only: report every file that is missing or differs locally, write nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/identity/sync-family-mark.ps1
    pwsh -NoProfile -File scripts/identity/sync-family-mark.ps1 -Check

.NOTES
    Exit codes:
      0  copied, or with -Check the local copy equals the catalog.
      1  -Check found a local file missing or different, or a catalog file disagrees with its recorded hash.
      2  could not verify: no catalog root, or the family folder or its family-mark.json is missing.
    The last stdout line is the verdict: `sync-family-mark: PASS|FAIL|COULD NOT VERIFY (..)`.
#>
[CmdletBinding()]
param(
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT,
    [switch] $Check
)
$ErrorActionPreference = 'Stop'

function Stop-Verdict([int] $Code, [string] $Word, [string] $Detail) {
    Write-Output "sync-family-mark: $Word ($Detail)"
    exit $Code
}

function Get-Sha256([byte[]] $Bytes) {
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

# A session started before the user variable was set still finds it, as export-icon-contract.ps1 does.
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) {
    Stop-Verdict 2 'COULD NOT VERIFY' 'no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT'
}
$source = Join-Path $CatalogRoot 'product-identity/family/fastmediasorter'
$manifestPath = Join-Path $source 'family-mark.json'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    Stop-Verdict 2 'COULD NOT VERIFY' "family-mark.json not found under $source"
}
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$target = Join-Path $repoRoot 'assets/identity/family'

$manifestBytes = [IO.File]::ReadAllBytes($manifestPath)
$listed = (ConvertFrom-Json ([Text.Encoding]::UTF8.GetString($manifestBytes)) -AsHashtable).files
$files = [ordered]@{ 'family-mark.json' = $manifestBytes }
$bad = @()
foreach ($rel in ($listed.Keys | Sort-Object)) {
    $path = Join-Path $source $rel
    if (-not (Test-Path -LiteralPath $path)) {
        Stop-Verdict 2 'COULD NOT VERIFY' "listed file missing in the catalog: $rel"
    }
    $bytes = [IO.File]::ReadAllBytes($path)
    if ((Get-Sha256 $bytes) -ne $listed[$rel]) { $bad += $rel }
    $files[$rel] = $bytes
}
if ($bad.Count -gt 0) {
    $bad | ForEach-Object { Write-Output "  FAIL catalog file disagrees with family-mark.json: $_" }
    Stop-Verdict 1 'FAIL' "$($bad.Count) catalog file(s) disagree with their recorded hash; nothing written"
}

$drift = @()
$written = 0
foreach ($rel in $files.Keys) {
    $dest = Join-Path $target $rel
    $same = (Test-Path -LiteralPath $dest) -and ((Get-Sha256 ([IO.File]::ReadAllBytes($dest))) -eq (Get-Sha256 $files[$rel]))
    if ($same) { continue }
    if ($Check) { $drift += $rel; continue }
    New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
    [IO.File]::WriteAllBytes($dest, $files[$rel])
    $written++
}
if ($Check) {
    if ($drift.Count -gt 0) {
        $drift | ForEach-Object { Write-Output "  FAIL missing or different locally: $_" }
        Stop-Verdict 1 'FAIL' "$($drift.Count) of $($files.Count) file(s) drifted; rerun without -Check"
    }
    Stop-Verdict 0 'PASS' "$($files.Count) file(s) equal the catalog"
}
Stop-Verdict 0 'PASS' "$written of $($files.Count) file(s) written"
