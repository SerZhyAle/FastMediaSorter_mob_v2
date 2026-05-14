# Updates manual fields on a catalogue record (role, status, noFlavors, function description).
# Auto-fields (loc, injected, flags, etc.) are NOT editable here — they come from scan.ps1.
#
# Usage:
#   pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "com/sza/.../Foo.kt" -Role "Loads cached SMB resources on start"
#   pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "...Foo.kt" -Status legacy
#   pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "...Foo.kt" -NoFlavors "photos,lite"
#   pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "...Foo.kt" -Function "refresh" -Description "Invalidates and rebuilds the file list"
#
# -Path supports fuzzy match: if a substring matches exactly one record, that record is used.
# By default re-renders Markdown view; pass -NoRender to skip.

param(
    [Parameter(Mandatory=$true)][string]$Module,
    [Parameter(Mandatory=$true)][string]$Path,
    [string]$Role,
    [ValidateSet('new','tested','legacy','todo','unknown')][string]$Status,
    [string]$NoFlavors,
    [string]$Function,
    [string]$Description,
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

$target = $records | Where-Object { $_.path -eq $Path }
if (-not $target) {
    $fuzzy = @($records | Where-Object { $_.path -like "*$Path*" })
    if ($fuzzy.Count -eq 1) {
        $target = $fuzzy[0]
        Write-Host "Matched: $($target.path)" -ForegroundColor DarkGray
    } elseif ($fuzzy.Count -gt 1) {
        $paths = ($fuzzy | ForEach-Object { "  $($_.path)" }) -join "`n"
        throw "Ambiguous path '$Path', matches $($fuzzy.Count) records:`n$paths"
    } else {
        throw "No record found for path '$Path' in $Module"
    }
}

$changed = @()

if ($PSBoundParameters.ContainsKey('Role')) {
    $target.role = $Role
    $changed += "role"
}
if ($PSBoundParameters.ContainsKey('Status')) {
    $target.status = $Status
    $changed += "status=$Status"
}
if ($PSBoundParameters.ContainsKey('NoFlavors')) {
    $flavors = if ($NoFlavors) { @($NoFlavors -split '\s*,\s*' | Where-Object { $_ }) } else { @() }
    $valid = @('standard','lite','photos','legacy','vr','vrUnlicensed','noLegal')
    $bad = $flavors | Where-Object { $_ -notin $valid }
    if ($bad) { throw "Invalid flavors: $($bad -join ', '). Must be subset of: $($valid -join ', ')" }
    $target.noFlavors = $flavors
    $changed += "noFlavors=[$($flavors -join ',')]"
}
if ($Function) {
    $f = $target.functions | Where-Object { $_.name -eq $Function }
    if (-not $f) {
        $available = ($target.functions | ForEach-Object { $_.name }) -join ', '
        throw "Function '$Function' not found in $($target.path). Available: $available"
    }
    if ($PSBoundParameters.ContainsKey('Description')) {
        $f.description = $Description
        $changed += "function[$Function].description"
    } else {
        throw "-Function requires -Description"
    }
} elseif ($PSBoundParameters.ContainsKey('Description')) {
    throw "-Description requires -Function (for class-level description use -Role)"
}

if (-not $changed) {
    Write-Host "No fields specified. Use -Role, -Status, -NoFlavors, or -Function + -Description." -ForegroundColor Yellow
    return
}

$records | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress } | Set-Content -Path $InFile -Encoding UTF8
Write-Host "Updated [$($target.path)]: $($changed -join ', ')" -ForegroundColor Green

if (-not $NoRender) {
    & (Join-Path $PSScriptRoot 'render.ps1') -Module $Module
}
