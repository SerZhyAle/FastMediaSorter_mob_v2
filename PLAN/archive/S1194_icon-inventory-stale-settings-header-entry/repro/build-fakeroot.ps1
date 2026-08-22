<#
.SYNOPSIS
    S1194 reproducer - render the icon-inventory gate's settings-source-fresh failure.

.DESCRIPTION
    Builds a throwaway repo root under temp/S1194/fakeroot whose copy of
    docs/icons/icon-inventory.json is missing the headerAppData entry, which is exactly
    the drift captured in the ticket. Running the gate against that root prints the
    failure line, so the remedy wording can be verified without dirtying the real tree.

    Usage:
      pwsh -NoProfile -File PLAN/S1194_icon-inventory-stale-settings-header-entry/repro/build-fakeroot.ps1
      pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -RepoRoot <printed path>

    Expected: exit 1, with the first issue line ending in
      - run: pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -RegenerateInventory

    Exit codes:
      0 - fakeroot built.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$fake = Join-Path $RepoRoot 'temp\S1194\fakeroot'

if (Test-Path $fake) { Remove-Item $fake -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $fake 'docs') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $fake 'scripts\docs') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $fake 'scripts\utils') | Out-Null

Copy-Item (Join-Path $RepoRoot 'docs\icons') (Join-Path $fake 'docs\icons') -Recurse
Copy-Item (Join-Path $RepoRoot 'docs\ICON_LEGEND*.md') (Join-Path $fake 'docs')
Copy-Item (Join-Path $RepoRoot 'scripts\docs\render-icon-legend.ps1') (Join-Path $fake 'scripts\docs')
Copy-Item (Join-Path $RepoRoot 'scripts\utils\agent-lock.ps1') (Join-Path $fake 'scripts\utils')

$srcRes = Join-Path $RepoRoot 'app_v2\src\main\res'
$dstRes = Join-Path $fake 'app_v2\src\main\res'
New-Item -ItemType Directory -Force -Path (Join-Path $dstRes 'layout') | Out-Null
Copy-Item (Join-Path $srcRes 'layout\fragment_settings_*.xml') (Join-Path $dstRes 'layout')
Get-ChildItem $srcRes -Directory |
    Where-Object { $_.Name -like 'drawable*' -or $_.Name -like 'values*' } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $dstRes $_.Name) -Recurse }

$invPath = Join-Path $fake 'docs\icons\icon-inventory.json'
$inv = @(Get-Content $invPath -Raw | ConvertFrom-Json)
$kept = @($inv | Where-Object { $_.key -ne 'headerAppData' })
($kept | ConvertTo-Json -Depth 4) | Set-Content $invPath -Encoding UTF8

Write-Host "fakeroot ready at $fake - dropped $($inv.Count - $kept.Count) inventory entry(ies)"
exit 0
