# Queries the catalogue by filters. Combine filters freely (all are AND'd).
#
# Examples:
#   # All data-layer classes that touch disk
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -SideEffect disk
#
#   # Big files that may need decomposition
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -MinLoc 800
#
#   # Records missing role description
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Missing role
#
#   # All classes that inject a specific type
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Injected ResourceDao
#
#   # Classes changed since a date
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -TouchedSince 2026-04-01
#
#   # Machine-readable output for piping
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer ui -Json

param(
    [Parameter(Mandatory=$true)][string]$Module,
    [string]$Layer,
    [ValidateSet('new','tested','legacy','todo','unknown')][string]$Status,
    [ValidateSet('db','network','disk','prefs')][string]$SideEffect,
    [int]$MinLoc,
    [int]$MaxLoc,
    [string]$ClassMatches,
    [string]$PathMatches,
    [string]$Injected,
    [ValidateSet('role','description')][string]$Missing,
    [switch]$Coroutines,
    [switch]$UserFeedback,
    [switch]$Tests,
    [switch]$NoTests,
    [string]$TouchedSince,
    [string]$TouchedBefore,
    [switch]$Json
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$InFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
if (-not (Test-Path $InFile)) { throw "Catalogue not found: $InFile" }

$result = @()
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if ($line) { $result += ($line | ConvertFrom-Json) }
}

if ($Layer)         { $result = @($result | Where-Object { $_.layer -eq $Layer }) }
if ($Status)        { $result = @($result | Where-Object { $_.status -eq $Status }) }
if ($SideEffect)    { $result = @($result | Where-Object { $_.sideEffects -contains $SideEffect }) }
if ($MinLoc)        { $result = @($result | Where-Object { $_.loc -ge $MinLoc }) }
if ($MaxLoc)        { $result = @($result | Where-Object { $_.loc -le $MaxLoc }) }
if ($ClassMatches)  { $result = @($result | Where-Object { $_.class -like $ClassMatches }) }
if ($PathMatches)   { $result = @($result | Where-Object { $_.path -like $PathMatches }) }
if ($Injected)      { $result = @($result | Where-Object { $_.injected -contains $Injected }) }
if ($Coroutines)    { $result = @($result | Where-Object { $_.coroutines }) }
if ($UserFeedback)  { $result = @($result | Where-Object { $_.userFeedback }) }
if ($Tests)         { $result = @($result | Where-Object { $_.hasTests }) }
if ($NoTests)       { $result = @($result | Where-Object { -not $_.hasTests }) }
if ($TouchedSince)  { $result = @($result | Where-Object { $_.lastTouched -ge $TouchedSince }) }
if ($TouchedBefore) { $result = @($result | Where-Object { $_.lastTouched -and $_.lastTouched -lt $TouchedBefore }) }
if ($Missing -eq 'role') {
    $result = @($result | Where-Object { -not $_.role })
}
if ($Missing -eq 'description') {
    $result = @($result | Where-Object {
        @($_.functions | Where-Object { -not $_.description }).Count -gt 0
    })
}

if ($Json) {
    $result | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress }
    return
}

if (-not $result -or $result.Count -eq 0) {
    Write-Host "No records matched." -ForegroundColor Yellow
    return
}

$result |
    Sort-Object -Property @{Expression={$_.layer}}, @{Expression={$_.path}} |
    Select-Object `
        @{N='path';E={$_.path}}, `
        @{N='class';E={$_.class}}, `
        @{N='layer';E={$_.layer}}, `
        @{N='loc';E={$_.loc}}, `
        @{N='last';E={$_.lastTouched}}, `
        @{N='status';E={$_.status}}, `
        @{N='role';E={if ($_.role) { $_.role } else { '—' }}} |
    Format-Table -AutoSize -Wrap

Write-Host "`n$($result.Count) records matched" -ForegroundColor Cyan
