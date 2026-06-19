<#
.SYNOPSIS
    Patch selected fields of one existing ALL_FEATURES record in place, by id.

.DESCRIPTION
    Upsert-by-id (add.ps1) requires every field, which is clumsy when an audit only
    needs to adjust `flavors` or flip `status`. This patches a single record,
    preserving all other fields and the canonical key order. Errors (exit 2) if the
    id is not found - it never creates a new record.

    Flavor edit modes (pick one):
      -AddFlavors "vr,photos"     union into the current flavors
      -RemoveFlavors "lite"       subtract from the current flavors
      -SetFlavors "standard,vr"   replace the whole flavors list
    Other optional field patches: -Status, -Name, -Description, -Area, -Spec.

    -NoLegal targets docs/ALL_FEATURES_noLegal.jsonl.

.EXAMPLE
    .\scripts\all_features\patch.ps1 -Id audio-player.background-audio-service -AddFlavors vr
.EXAMPLE
    .\scripts\all_features\patch.ps1 -Id audio-player.pulsing-rings-backdrop -Status removed
#>
param(
    [Parameter(Mandatory = $true)] [string]$Id,
    [string]$NewId = "",
    [string]$AddFlavors = "",
    [string]$RemoveFlavors = "",
    [string]$SetFlavors = "",
    [ValidateSet("active", "removed")] [string]$Status = "",
    [string]$Name = "",
    [string]$Description = "",
    [string]$Area = "",
    [string]$Spec = "",
    [switch]$NoLegal,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$validFlavors = @("standard", "lite", "photos", "legacy", "vr", "noLegal")
function Fail([string]$m) { Write-Error $m; exit 1 }
function Test-NonAscii([string]$s) { foreach ($c in $s.ToCharArray()) { if ([int][char]$c -gt 127) { return $true } } return $false }
function Split-Csv([string]$s) { return @($s -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { $_.Trim() }) }

# repo root
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
if (-not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) { $repoRoot = (Get-Location).Path }
$fileName = if ($NoLegal) { "ALL_FEATURES_noLegal.jsonl" } else { "ALL_FEATURES.jsonl" }
$dataFile = Join-Path (Join-Path $repoRoot "docs") $fileName
if (-not (Test-Path $dataFile)) { Fail "Not found: $dataFile" }

$idN = $Id.Trim()
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$lines = @(Get-Content -LiteralPath $dataFile -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 })

$found = $false
$out = New-Object System.Collections.Generic.List[string]
foreach ($l in $lines) {
    $o = $null
    try { $o = $l | ConvertFrom-Json } catch { $o = $null }
    if (-not $o -or $o.id -ne $idN) { $out.Add($l); continue }
    $found = $true

    # current values
    $area = if ($Area) { $Area.Trim() } else { "$($o.area)" }
    $name = if ($Name) { $Name.Trim() } else { "$($o.name)" }
    $desc = if ($Description) { ($Description -replace '\s+', ' ').Trim() } else { "$($o.description)" }
    # NB: local must not be named $spec - it would alias the [string]-typed $Spec param (case-insensitive) and coerce $null to "".
    $specVal = if ($PSBoundParameters.ContainsKey('Spec')) {
        if ($Spec) { $Spec.Trim() } else { $null }
    } elseif ([string]::IsNullOrEmpty([string]$o.spec)) { $null } else { "$($o.spec)" }
    $status = if ($Status) { $Status } elseif ($o.PSObject.Properties.Name -contains 'status' -and $o.status) { "$($o.status)" } else { "active" }
    $flavors = @($o.flavors)

    if ($SetFlavors) {
        $flavors = Split-Csv $SetFlavors
    } else {
        if ($AddFlavors) { $flavors = @($flavors + (Split-Csv $AddFlavors)) }
        if ($RemoveFlavors) { $rm = Split-Csv $RemoveFlavors; $flavors = @($flavors | Where-Object { $rm -notcontains $_ }) }
    }
    $flavors = @($flavors | Select-Object -Unique)

    # validate
    if ($flavors.Count -eq 0) { Fail "Record '$idN' would have empty flavors." }
    foreach ($f in $flavors) { if ($validFlavors -notcontains $f) { Fail "Invalid flavor '$f'." } }
    if ((Test-NonAscii $name) -or (Test-NonAscii $desc)) { Fail "EN-only: non-ASCII in name/description." }
    if ($specVal -and "$specVal" -notmatch '^S\d{4}$') { Fail "Invalid spec '$specVal'." }

    $writeId = if ($NewId) { $NewId.Trim() } else { $idN }
    if ($writeId -notmatch '^[a-z0-9]+(?:[-_][a-z0-9]+)*\.[a-z0-9]+(?:[-_][a-z0-9]+)*$') { Fail "Invalid new id '$writeId'." }

    $rec = [ordered]@{ id = $writeId; area = $area; name = $name; description = $desc; flavors = $flavors; spec = $specVal; status = $status }
    $out.Add(($rec | ConvertTo-Json -Compress -Depth 5))
}

if (-not $found) { Write-Error "Id '$idN' not found in docs/$fileName"; exit 2 }

[System.IO.File]::WriteAllText($dataFile, (($out -join "`n") + "`n"), $utf8NoBom)
if (-not $Quiet) { Write-Host "[patch] $idN -> flavors=[$($flavors -join ',')] status=$status (docs/$fileName)" -ForegroundColor Green }
exit 0
