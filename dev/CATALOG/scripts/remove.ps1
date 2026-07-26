# Removes a record from the catalogue.
#
# Note: scan.ps1 automatically drops records whose source file no longer exists.
# Use this script only for cleaning up manual entries or stale records.
#
# Usage:
#   pwsh -File dev/CATALOG/scripts/remove.ps1 -Module app_v2 -Path "com/sza/.../Foo.kt"
#
# Exit codes:
#   0 - success.
#   1 - failure: a throw under $ErrorActionPreference = 'Stop' ends the process.

param(
    [Parameter(Mandatory=$true)][string]$Module,
    [Parameter(Mandatory=$true)][string]$Path,
    [switch]$NoRender
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$InFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
if (-not (Test-Path $InFile)) { throw "Catalogue not found: $InFile" }

$records = @()
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if ($line) { $records += ($line | ConvertFrom-Json) }
}

$before = $records.Count
$kept = @($records | Where-Object { $_.path -ne $Path })
$removed = $before - $kept.Count

if ($removed -eq 0) {
    throw "No record with path '$Path' in $Module"
}

$kept | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress } | Set-Content -Path $InFile -Encoding UTF8
Write-Host "Removed $removed record(s); $($kept.Count) remain." -ForegroundColor Green

if (-not $NoRender) {
    & (Join-Path $PSScriptRoot 'render.ps1') -Module $Module
}

exit 0
