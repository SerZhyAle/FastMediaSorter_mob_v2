# One-shot Catalogue sync: scan + render in a single PowerShell process.
#
# Replaces the two separate invocations:
#   pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
#   pwsh -NoProfile -File dev/CATALOG/scripts/render.ps1 -Module app_v2
# with one process, eliminating PowerShell cold-start overhead twice.
#
# Usage:
#   pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
#   pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module wear

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('app_v2', 'wear')]
    [string]$Module
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$scanScript   = Join-Path $repoRoot 'dev\CATALOG\scripts\scan.ps1'
$renderScript = Join-Path $repoRoot 'dev\CATALOG\scripts\render.ps1'

Write-Host "[catalog_sync] scan  -> $Module" -ForegroundColor Cyan
& $scanScript -Module $Module
if ($LASTEXITCODE -ne 0) { throw "scan.ps1 failed with exit $LASTEXITCODE" }

Write-Host "[catalog_sync] render -> $Module" -ForegroundColor Cyan
& $renderScript -Module $Module
if ($LASTEXITCODE -ne 0) { throw "render.ps1 failed with exit $LASTEXITCODE" }

Write-Host "[catalog_sync] OK ($Module)" -ForegroundColor Green
