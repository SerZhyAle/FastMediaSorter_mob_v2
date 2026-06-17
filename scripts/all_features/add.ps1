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
#>
param(
    [Parameter(Mandatory = $true)] [string]$Id,
    [Parameter(Mandatory = $true)] [string]$Area,
    [Parameter(Mandatory = $true)] [string]$Name,
    [Parameter(Mandatory = $true)] [string]$Description,
    [Parameter(Mandatory = $true)] [string]$Flavors,
    [Parameter(Mandatory = $false)] [string]$Spec = "",
    [Parameter(Mandatory = $false)] [ValidateSet("active", "removed")] [string]$Status = "active",
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
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
if (-not $repoRoot -or -not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) {
    $repoRoot = (Get-Location).Path
}
$fileName = if ($NoLegal) { "ALL_FEATURES_noLegal.jsonl" } else { "ALL_FEATURES.jsonl" }
$dataFile = Join-Path (Join-Path $repoRoot "docs") $fileName

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

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Read existing records (skip blank lines)
$existing = @()
if (Test-Path $dataFile) {
    $existing = @(Get-Content -LiteralPath $dataFile -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })
}

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

$text = ($out -join "`n") + "`n"
[System.IO.File]::WriteAllText($dataFile, $text, $utf8NoBom)

if (-not $Quiet) {
    $verb = if ($found) { "updated" } else { "added" }
    Write-Host "[ALL_FEATURES] $verb '$idN' -> docs/$fileName" -ForegroundColor Green
}
exit 0
