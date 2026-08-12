<#
.SYNOPSIS
    Upsert one record into the ALL_FEATURES inventory (docs/ALL_FEATURES.jsonl).

.DESCRIPTION
    Developer-facing feature inventory, EN-only. One JSON object per line.
    Replaces dev/FUNCTIONALITY.log as the source of truth about implemented
    functionality. Records are validated against docs/ALL_FEATURES.schema.json
    rules before write. Upsert by `id`: an existing record with the same id is
    replaced in place; otherwise the record is appended.

    noLegal-only capabilities route to the gitignored docs/ALL_FEATURES_noLegal.jsonl
    via -NoLegal.

.EXAMPLE
    .\scripts\all_features\add.ps1 -Id "video.session_restore" -Area "Video Player" `
        -Name "Session save and restore" -Description "Remembers exact playback coordinates" `
        -Flavors "standard,vr" -Spec S0001

.EXAMPLE
    # List the distinct areas already in use (no exploratory ConvertFrom-Json needed):
    .\scripts\all_features\add.ps1 -ListAreas
#>
[CmdletBinding(DefaultParameterSetName = 'Add')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Add')] [string]$Id,
    [Parameter(Mandatory = $true, ParameterSetName = 'Add')] [string]$Area,
    [Parameter(Mandatory = $true, ParameterSetName = 'Add')] [string]$Name,
    [Parameter(Mandatory = $true, ParameterSetName = 'Add')] [string]$Description,
    [Parameter(Mandatory = $true, ParameterSetName = 'Add')] [string]$Flavors,
    [Parameter(Mandatory = $false, ParameterSetName = 'Add')] [string]$Spec = "",
    [Parameter(Mandatory = $false, ParameterSetName = 'Add')] [ValidateSet("active", "removed")] [string]$Status = "active",
    [Parameter(Mandatory = $true, ParameterSetName = 'List')] [switch]$ListAreas,
    [switch]$NoLegal,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$validFlavors = @("standard", "lite", "photos", "legacy", "vr", "noLegal")

function Fail([string]$msg) { Write-Error $msg; exit 1 }

function Test-NonAscii([string]$s) {
    foreach ($ch in $s.ToCharArray()) { if ([int][char]$ch -gt 127) { return $true } }
    return $false
}

# Resolve repo root
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
. (Join-Path $scriptDir '_lib.ps1')
$repoRoot = Resolve-FeatureRepoRoot -ScriptDir $scriptDir
$fileName = if ($NoLegal) { "ALL_FEATURES_noLegal.jsonl" } else { "ALL_FEATURES.jsonl" }
$dataFile = Get-FeatureInventoryPath -RepoRoot $repoRoot -NoLegal:$NoLegal

# -ListAreas: print the distinct areas already in the inventory and exit. Lets a
# caller pick an existing area name without a separate exploratory ConvertFrom-Json pass.
if ($ListAreas) {
    if (Test-Path $dataFile) {
        @(Get-Content -LiteralPath $dataFile -Encoding UTF8 |
            Where-Object { $_.Trim().Length -gt 0 } |
            ForEach-Object { try { ($_ | ConvertFrom-Json).area } catch { } } |
            Where-Object { $_ } |
            Sort-Object -Unique) | ForEach-Object { Write-Output $_ }
    }
    exit 0
}

# Normalize / validate fields (mirror docs/ALL_FEATURES.schema.json)
$idN = $Id.Trim()
if ($idN -notmatch '^[a-z0-9]+(?:[-_][a-z0-9]+)*\.[a-z0-9]+(?:[-_][a-z0-9]+)*$') {
    Fail "Invalid -Id '$idN'. Expected kebab '<area>.<feature>' (lowercase)."
}
$areaN = $Area.Trim()
$nameN = $Name.Trim()
$descN = ($Description -replace '\s+', ' ').Trim()
if ([string]::IsNullOrWhiteSpace($areaN)) { Fail "-Area is empty." }
if ([string]::IsNullOrWhiteSpace($nameN)) { Fail "-Name is empty." }
if ([string]::IsNullOrWhiteSpace($descN)) { Fail "-Description is empty." }

$flavorList = @($Flavors -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { $_.Trim() })
if ($flavorList.Count -eq 0) { Fail "-Flavors is empty." }
foreach ($f in $flavorList) {
    if ($validFlavors -notcontains $f) {
        Fail "Invalid flavor '$f'. Allowed: $($validFlavors -join ', ')."
    }
}
$flavorList = @($flavorList | Select-Object -Unique)

$specVal = $null
if (-not [string]::IsNullOrWhiteSpace($Spec)) {
    $specT = $Spec.Trim()
    if ($specT -notmatch '^S\d{4}$') { Fail "Invalid -Spec '$specT'. Expected Sxxxx or empty." }
    $specVal = $specT
}

# EN-only inventory: reject non-ASCII in name/description
if ((Test-NonAscii $nameN) -or (Test-NonAscii $descN)) {
    Fail "ALL_FEATURES is EN-only: non-ASCII found in -Name/-Description."
}

# Build record with stable key order
$record = [ordered]@{
    id          = $idN
    area        = $areaN
    name        = $nameN
    description = $descN
    flavors     = $flavorList
    spec        = $specVal
    status      = $Status
}
$line = ($record | ConvertTo-Json -Compress -Depth 5)

# S1537: read -> upsert -> write is one critical section. Serializing only the write would
# still lose this record - the other writer's snapshot was taken before it existed. Every
# Fail path above runs before the lock is taken, so no early exit can leave it held.
Enter-FeatureLock -RepoRoot $repoRoot
try {
    $existing = Read-FeatureLines -Path $dataFile

    # Upsert by id
    $found = $false
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($l in $existing) {
        try { $obj = $l | ConvertFrom-Json } catch { $obj = $null }
        if ($obj -and $obj.id -eq $idN) {
            $out.Add($line); $found = $true
        } else {
            $out.Add($l)
        }
    }
    if (-not $found) { $out.Add($line) }

    Write-FeatureLines -Path $dataFile -Lines $out
}
finally { Exit-FeatureLock }

if (-not $Quiet) {
    $verb = if ($found) { "updated" } else { "added" }
    Write-Host "[ALL_FEATURES] $verb '$idN' -> docs/$fileName" -ForegroundColor Green
}
exit 0
