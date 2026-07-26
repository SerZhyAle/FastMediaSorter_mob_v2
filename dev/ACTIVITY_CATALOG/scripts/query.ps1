# Filters ACTIVITY_CATALOG JSONL records by keyword, tag, module, or flags.
#
# Examples:
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "player"
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module all -Launcher
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "плеер"
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -MissingRole
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "portrait" -Json
#
# Exit codes:
#   0 - success.

param(
    [Parameter(Mandatory=$true)]
    [string]$Module,         # app_v2 | wear | all
    [string]$Root,
    [string]$Search,         # substring match across class, role, roleRu, tags, package
    [string]$Tag,            # exact tag element match (case-insensitive)
    [switch]$Launcher,       # filter to launcher Activities only
    [switch]$Exported,       # filter to exported Activities only
    [switch]$HasRole,        # records where role is non-empty
    [switch]$MissingRole,    # records where role is empty
    [switch]$Json            # output as JSON array instead of table
)

$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

# ── Load records ──────────────────────────────────────────────────────────────

function Load-Module([string]$mod) {
    $path = Join-Path $Root "dev\ACTIVITY_CATALOG\$mod.jsonl"
    if (-not (Test-Path $path)) { throw "JSONL not found: $path (run scan.ps1 first)" }
    $records = @()
    foreach ($line in (Get-Content -Path $path -Encoding UTF8)) {
        if (-not $line.Trim()) { continue }
        $records += ($line | ConvertFrom-Json)
    }
    return $records
}

$records = @()
if ($Module -eq "all") {
    $records += Load-Module "app_v2"
    $records += Load-Module "wear"
} else {
    $records = Load-Module $Module
}

# ── Apply filters (AND logic) ─────────────────────────────────────────────────

if ($Search) {
    $term = $Search.ToLower()
    $records = $records | Where-Object {
        $tagStr = if ($_.tags) { ($_.tags -join " ").ToLower() } else { "" }
        ($_.class.ToLower()   -like "*$term*") -or
        ($_.role.ToLower()    -like "*$term*") -or
        ($_.roleRu.ToLower()  -like "*$term*") -or
        ($_.package.ToLower() -like "*$term*") -or
        ($tagStr              -like "*$term*")
    }
}

if ($Tag) {
    $tagLower = $Tag.ToLower()
    $records = $records | Where-Object {
        $_.tags -and ($_.tags | ForEach-Object { $_.ToLower() }) -contains $tagLower
    }
}

if ($Launcher) {
    $records = $records | Where-Object { $_.launcher -eq $true }
}

if ($Exported) {
    $records = $records | Where-Object { $_.exported -eq $true }
}

if ($HasRole) {
    $records = $records | Where-Object { $_.role -and $_.role.Trim() -ne "" }
}

if ($MissingRole) {
    $records = $records | Where-Object { -not $_.role -or $_.role.Trim() -eq "" }
}

# ── Output ────────────────────────────────────────────────────────────────────

if ($Json) {
    $records | ConvertTo-Json -Depth 5
    exit 0
}

if ($records.Count -eq 0) {
    Write-Host "(no matches)"
    exit 0
}

# Table: Module | Class | Launcher | Exported | Tags | Role
$colWidths = @{ Module=6; Class=40; L=3; X=3; Tags=30; Role=50 }

$header = "{0,-$($colWidths.Module)}  {1,-$($colWidths.Class)}  {2,-$($colWidths.L)}  {3,-$($colWidths.X)}  {4,-$($colWidths.Tags)}  {5}" `
    -f "Module","Class","Lnc","Exp","Tags","Role"
$sep = ("-" * $colWidths.Module) + "  " + ("-" * $colWidths.Class) + "  " + "---  ---  " + ("-" * $colWidths.Tags) + "  " + ("-" * 40)

Write-Host $header
Write-Host $sep

foreach ($r in $records) {
    $tags  = if ($r.tags -and $r.tags.Count -gt 0) { $r.tags -join "," } else { "" }
    $role  = if ($r.role) { $r.role } else { "" }
    $lnc   = if ($r.launcher) { "Y" } else { "-" }
    $exp   = if ($r.exported) { "Y" } else { "-" }

    if ($tags.Length -gt $colWidths.Tags)  { $tags = $tags.Substring(0, $colWidths.Tags - 2) + ".." }
    if ($role.Length -gt $colWidths.Role)  { $role = $role.Substring(0, $colWidths.Role - 2) + ".." }

    Write-Host ("{0,-$($colWidths.Module)}  {1,-$($colWidths.Class)}  {2,-$($colWidths.L)}  {3,-$($colWidths.X)}  {4,-$($colWidths.Tags)}  {5}" `
        -f $r.module, $r.class, $lnc, $exp, $tags, $role)
}

Write-Host ""
Write-Host "$($records.Count) result(s)."

exit 0
