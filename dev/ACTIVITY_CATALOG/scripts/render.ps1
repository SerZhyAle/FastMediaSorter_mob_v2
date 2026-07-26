# Renders ACTIVITY_CATALOG JSONL into a human-readable Markdown file.
#
# Usage:
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module app_v2
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module wear
#
# Exit codes:
#   0 - success.

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("app_v2","wear")]
    [string]$Module,
    [string]$Root,
    [string]$InFile,
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

if (-not $InFile)  { $InFile  = Join-Path $Root "dev\ACTIVITY_CATALOG\$Module.jsonl" }
if (-not $OutFile) { $OutFile = Join-Path $Root "dev\ACTIVITY_CATALOG\$Module.md" }

if (-not (Test-Path $InFile)) { throw "JSONL not found: $InFile (run scan.ps1 first)" }

# ── Load records ──────────────────────────────────────────────────────────────

$records = @()
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if (-not $line.Trim()) { continue }
    $records += ($line | ConvertFrom-Json)
}

# Sort: launcher first, then alphabetical by class
$sorted = @(
    ($records | Where-Object { $_.launcher -eq $true } | Sort-Object class)
    ($records | Where-Object { $_.launcher -ne $true } | Sort-Object class)
)

$total    = $records.Count
$withRole = ($records | Where-Object { $_.role -and $_.role.Trim() -ne "" }).Count
$launcher = ($records | Where-Object { $_.launcher -eq $true }).Count
$now      = Get-Date -Format "yyyy-MM-dd HH:mm"

# ── Build Markdown ────────────────────────────────────────────────────────────

$sb = [System.Text.StringBuilder]::new()

[void]$sb.AppendLine("# Activity Catalog - $Module")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("*Generated: $now*")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("**$total Activities · $withRole with role · $launcher launcher**")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| Class | Launcher | Exported | Flavors | Tags | Role (EN) | Role (RU) |")
[void]$sb.AppendLine("|-------|:--------:|:--------:|---------|------|-----------|-----------|")

foreach ($r in $sorted) {
    $lnc  = if ($r.launcher) { "✓" } else { "" }
    $exp  = if ($r.exported) { "✓" } else { "" }

    $flavors = if (-not $r.noFlavors -or $r.noFlavors.Count -eq 0) {
        "all"
    } else {
        ($r.noFlavors | ForEach-Object { "–$_" }) -join " "
    }

    $tags    = if ($r.tags -and $r.tags.Count -gt 0) { $r.tags -join ", " } else { "" }
    $roleEn  = if ($r.role)   { $r.role }   else { "" }
    $roleRu  = if ($r.roleRu) { $r.roleRu } else { "" }

    [void]$sb.AppendLine("| $($r.class) | $lnc | $exp | $flavors | $tags | $roleEn | $roleRu |")
}

[void]$sb.AppendLine("")
[void]$sb.AppendLine("---")
[void]$sb.AppendLine("")
$footerLine = "*Manual fields: set via set.ps1. Source of truth: ${Module}.jsonl.*"
[void]$sb.AppendLine($footerLine)

# ── Write file ────────────────────────────────────────────────────────────────

$sb.ToString() | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "Rendered $total records -> $OutFile"

exit 0
