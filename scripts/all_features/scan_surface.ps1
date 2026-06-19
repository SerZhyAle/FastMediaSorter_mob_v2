<#
.SYNOPSIS
    S0543 - Build per-area audit worksheets pairing the ALL_FEATURES inventory with
    the actual code surface (class catalog), to drive the inventory completeness audit.

.DESCRIPTION
    READ-ONLY. Produces no source changes, no build, no device interaction.

    Joins two machine-readable sources:
      - docs/ALL_FEATURES.jsonl  (the developer capability inventory, grouped by area)
      - dev/CATALOG/<module>.jsonl (the class catalog: path/class/layer/loc/role/functions)

    Emits, under temp/s0543/:
      - inventory_by_area.json   area  -> [inventory records]
      - catalog_modules.json     module (layer/feature) -> [classes]
      - coverage_seed.txt        side-by-side: inventory areas + code modules,
                                 flags id-hygiene defects and lopsided coverage
    These are SEED worksheets - signals, not truths. The per-area agents verify
    against code before any inventory write (S0543 Phase 02).

.NOTES
    The catalog (dev/CATALOG/*.jsonl) is a gitignored local index; run
    scripts/catalog_sync.ps1 -Module app_v2 first if it is stale or missing.

.EXAMPLE
    pwsh -NoProfile -File scripts/all_features/scan_surface.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/all_features/scan_surface.ps1 -Module app_v2
#>
param(
    [ValidateSet("app_v2", "wear")] [string]$Module = "app_v2",
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

# Resolve repo root (script lives in scripts/all_features/)
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
if (-not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) { $repoRoot = (Get-Location).Path }

$invFile = Join-Path $repoRoot "docs/ALL_FEATURES.jsonl"
$catFile = Join-Path $repoRoot "dev/CATALOG/$Module.jsonl"
$outDir = Join-Path $repoRoot "temp/s0543"

if (-not (Test-Path $invFile)) { Write-Error "Inventory not found: $invFile"; exit 1 }
if (-not (Test-Path $catFile)) {
    Write-Error "Catalog not found: $catFile - run: pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module $Module"
    exit 1
}
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# --- Load inventory, group by area -------------------------------------------
$invRecords = @(Get-Content -LiteralPath $invFile -Encoding UTF8 |
    Where-Object { $_.Trim().Length -gt 0 } |
    ForEach-Object { try { $_ | ConvertFrom-Json } catch { $null } } |
    Where-Object { $_ })

$byArea = [ordered]@{}
foreach ($r in ($invRecords | Sort-Object area, id)) {
    $a = "$($r.area)"
    if (-not $byArea.Contains($a)) { $byArea[$a] = New-Object System.Collections.Generic.List[object] }
    $byArea[$a].Add([ordered]@{ id = $r.id; name = $r.name; flavors = $r.flavors; spec = $r.spec; status = $r.status })
}

# --- id hygiene: id area-prefix should relate to the area slug ----------------
function To-Slug([string]$s) {
    $x = $s.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    return $x.Trim('-')
}
$idHygiene = New-Object System.Collections.Generic.List[string]
foreach ($r in $invRecords) {
    $prefix = "$($r.id)".Split('.')[0]
    if ($prefix -match '^s\d{4}$') {
        $idHygiene.Add("$($r.id)   (area='$($r.area)', prefix is a spec id, expected area slug '$(To-Slug $r.area)')")
    }
}

# --- Load catalog, group by module (layer/feature) ---------------------------
$catRecords = @(Get-Content -LiteralPath $catFile -Encoding UTF8 |
    Where-Object { $_.Trim().Length -gt 0 } |
    ForEach-Object { try { $_ | ConvertFrom-Json } catch { $null } } |
    Where-Object { $_ })

function Module-Key([string]$path) {
    # com/sza/fastmediasorter/<a>/<b>/File.kt -> "a/b"
    $p = $path -replace '\\', '/'
    $p = $p -replace '^com/sza/fastmediasorter/', ''
    $segs = @($p -split '/')
    if ($segs.Count -le 1) { return "(root)" }
    $segs = $segs[0..($segs.Count - 2)]   # drop filename
    if ($segs.Count -ge 2) { return ($segs[0..1] -join '/') }
    return $segs[0]
}

$byModule = @{}
foreach ($c in $catRecords) {
    $k = Module-Key "$($c.path)"
    if (-not $byModule.ContainsKey($k)) { $byModule[$k] = New-Object System.Collections.Generic.List[object] }
    $byModule[$k].Add([ordered]@{ class = $c.class; loc = $c.loc; role = $c.role; path = $c.path })
}

# --- Write artifacts ----------------------------------------------------------
$invByAreaJson = ([pscustomobject]$byArea | ConvertTo-Json -Depth 6)
[System.IO.File]::WriteAllText((Join-Path $outDir "inventory_by_area.json"), $invByAreaJson, $utf8NoBom)

$modObj = [ordered]@{}
foreach ($k in ($byModule.Keys | Sort-Object)) { $modObj[$k] = $byModule[$k] }
$catModJson = ([pscustomobject]$modObj | ConvertTo-Json -Depth 6)
[System.IO.File]::WriteAllText((Join-Path $outDir "catalog_modules.json"), $catModJson, $utf8NoBom)

# coverage seed
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("S0543 coverage seed - inventory areas vs code modules ($Module)")
[void]$sb.AppendLine("inventory records: $($invRecords.Count)   areas: $($byArea.Keys.Count)   catalog classes: $($catRecords.Count)   modules: $($byModule.Keys.Count)")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("== Inventory records per area ==")
foreach ($a in ($byArea.Keys | Sort-Object { $byArea[$_].Count } -Descending)) {
    [void]$sb.AppendLine(("{0,4}  {1}" -f $byArea[$a].Count, $a))
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("== Code modules per class count (top 40; candidate feature surface) ==")
$topMods = $byModule.Keys | Sort-Object { $byModule[$_].Count } -Descending | Select-Object -First 40
foreach ($k in $topMods) {
    [void]$sb.AppendLine(("{0,4}  {1}" -f $byModule[$k].Count, $k))
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("== id-hygiene defects (spec id used as area prefix) ==")
if ($idHygiene.Count -eq 0) { [void]$sb.AppendLine("  none") }
else { foreach ($h in $idHygiene) { [void]$sb.AppendLine("  $h") } }

[System.IO.File]::WriteAllText((Join-Path $outDir "coverage_seed.txt"), $sb.ToString(), $utf8NoBom)

if (-not $Quiet) {
    Write-Host "[scan_surface] inventory=$($invRecords.Count) areas=$($byArea.Keys.Count) classes=$($catRecords.Count) modules=$($byModule.Keys.Count) idHygiene=$($idHygiene.Count)" -ForegroundColor Green
    Write-Host "[scan_surface] -> temp/s0543/{inventory_by_area.json, catalog_modules.json, coverage_seed.txt}" -ForegroundColor Green
}
exit 0
