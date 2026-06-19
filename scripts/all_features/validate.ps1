<#
.SYNOPSIS
    Validate the ALL_FEATURES inventory (docs/ALL_FEATURES.jsonl) against the schema rules.

.DESCRIPTION
    Parses each JSONL line, checks required fields, flavor enum membership,
    id pattern + uniqueness, spec pattern, status enum, and EN-only ASCII for
    name/description. Prints a per-error report and exits non-zero on any
    violation; exits 0 when clean. An empty file is valid.

    -NoLegal validates docs/ALL_FEATURES_noLegal.jsonl instead.
    -Gate prints nothing on success (exit-code-only) for post-change wiring.

.EXAMPLE
    .\scripts\all_features\validate.ps1
.EXAMPLE
    .\scripts\all_features\validate.ps1 -Gate
#>
param(
    [switch]$NoLegal,
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$validFlavors = @("standard", "lite", "photos", "legacy", "vr", "noLegal")
$validStatus = @("active", "removed")
$required = @("id", "area", "name", "description", "flavors")

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

$errors = New-Object System.Collections.Generic.List[string]
$seenIds = @{}
$count = 0

if (Test-Path $dataFile) {
    $lineNo = 0
    foreach ($raw in (Get-Content -LiteralPath $dataFile -Encoding UTF8)) {
        $lineNo++
        if ($raw.Trim().Length -eq 0) { continue }
        $count++
        $obj = $null
        try { $obj = $raw | ConvertFrom-Json } catch {
            $errors.Add("L${lineNo}: not valid JSON"); continue
        }
        # Required fields
        foreach ($k in $required) {
            if (-not ($obj.PSObject.Properties.Name -contains $k)) {
                $errors.Add("L${lineNo}: missing required field '$k'")
            }
        }
        # id pattern + uniqueness
        if ($obj.id) {
            if ($obj.id -notmatch '^[a-z0-9]+(?:[-_][a-z0-9]+)*\.[a-z0-9]+(?:[-_][a-z0-9]+)*$') {
                $errors.Add("L${lineNo}: id '$($obj.id)' not kebab '<area>.<feature>'")
            }
            # S0543: the area-prefix must be an area slug, not a spec id (s####). Active records only;
            # 'removed' tombstones keep their frozen historical id.
            $idStatus = if ($obj.PSObject.Properties.Name -contains 'status' -and $obj.status) { "$($obj.status)" } else { "active" }
            if ($idStatus -ne 'removed' -and ($obj.id.Split('.')[0] -match '^s\d{4}$')) {
                $errors.Add("L${lineNo}: id '$($obj.id)' uses a spec id as area prefix; use the area slug")
            }
            if ($seenIds.ContainsKey($obj.id)) {
                $errors.Add("L${lineNo}: duplicate id '$($obj.id)' (first at L$($seenIds[$obj.id]))")
            } else {
                $seenIds[$obj.id] = $lineNo
            }
        }
        # flavors
        if ($obj.flavors) {
            if ($obj.flavors -isnot [array] -or $obj.flavors.Count -eq 0) {
                $errors.Add("L${lineNo}: flavors must be a non-empty array")
            } else {
                foreach ($f in $obj.flavors) {
                    if ($validFlavors -notcontains $f) {
                        $errors.Add("L${lineNo}: invalid flavor '$f'")
                    }
                }
            }
        }
        # spec
        if ($obj.PSObject.Properties.Name -contains 'spec' -and $null -ne $obj.spec) {
            if ("$($obj.spec)" -notmatch '^S\d{4}$') {
                $errors.Add("L${lineNo}: spec '$($obj.spec)' not Sxxxx or null")
            }
        }
        # status
        if ($obj.PSObject.Properties.Name -contains 'status' -and $obj.status) {
            if ($validStatus -notcontains $obj.status) {
                $errors.Add("L${lineNo}: invalid status '$($obj.status)'")
            }
        }
        # EN-only
        if ($obj.name -and (Test-NonAscii "$($obj.name)")) {
            $errors.Add("L${lineNo}: non-ASCII in name (EN-only)")
        }
        if ($obj.description -and (Test-NonAscii "$($obj.description)")) {
            $errors.Add("L${lineNo}: non-ASCII in description (EN-only)")
        }
    }
}

if ($errors.Count -gt 0) {
    if (-not $Gate) {
        Write-Host "ALL_FEATURES validation FAILED ($($errors.Count) error(s)) in docs/$fileName" -ForegroundColor Red
        foreach ($e in $errors) { Write-Host "  $e" -ForegroundColor Red }
    }
    exit 1
}

if (-not $Gate -and -not $Quiet) {
    Write-Host "ALL_FEATURES validation PASS: $count record(s) in docs/$fileName" -ForegroundColor Green
}
exit 0
