# Applies owner-reviewed role drafts (from generate-role-drafts.ps1) back into the catalogue.
# Writes `role` for each TSV row with a non-empty draftRole, ONLY where the record's role is
# currently empty (never overwrites). Fills EVERY record sharing a path::class (a file can hold
# several records - nested objects, companion, interface+impl). Single-pass rewrite in the native
# JSONL format, then re-renders the Markdown view.
#
# Review workflow: open the TSV, blank the draftRole cell for any row you reject, fix the rest, save.
#
# Usage:
#   pwsh -NoProfile -File dev/CATALOG/scripts/apply-role-drafts.ps1 -Module app_v2
#   pwsh -NoProfile -File dev/CATALOG/scripts/apply-role-drafts.ps1 -Module app_v2 -DryRun

param(
    [string]$Module = 'app_v2',
    [string]$File = 'temp/scratch/role-drafts.tsv',
    [switch]$DryRun,
    [switch]$NoRender
)

$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$InFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
$tsvPath = Join-Path $Root $File
if (-not (Test-Path $InFile)) { throw "Catalogue not found: $InFile" }
if (-not (Test-Path $tsvPath)) { throw "Drafts TSV not found: $tsvPath" }

$records = @()
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if ($line) { $records += ($line | ConvertFrom-Json) }
}
# Group by path::class - one .kt file can yield several records; role is class-scoped, apply to all.
$index = @{}
foreach ($rec in $records) {
    $k = "$($rec.path)::$($rec.class)"
    if (-not $index.ContainsKey($k)) { $index[$k] = New-Object System.Collections.ArrayList }
    [void]$index[$k].Add($rec)
}

$tsvLines = Get-Content -Path $tsvPath -Encoding UTF8
$applied = 0; $skippedFilled = 0; $skippedBlank = 0; $notFound = 0
for ($i = 1; $i -lt $tsvLines.Count; $i++) {   # skip header
    $line = $tsvLines[$i]
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $cols = $line -split "`t", 5
    if ($cols.Count -lt 5) { continue }
    $path = $cols[0]; $class = $cols[1]; $draft = $cols[4].Trim()
    if (-not $draft) { $skippedBlank++; continue }
    $group = $index["$path::$class"]
    if (-not $group) { $notFound++; Write-Host "  not found: $path :: $class" -ForegroundColor DarkGray; continue }
    $touched = $false
    foreach ($rec in $group) {
        if (-not [string]::IsNullOrWhiteSpace($rec.role)) { continue }
        if ($DryRun) { Write-Host "  WOULD set [$class] = $draft" -ForegroundColor DarkCyan }
        else { $rec.role = $draft }
        $touched = $true
    }
    if ($touched) { $applied++ } else { $skippedFilled++ }
}

if ($DryRun) {
    Write-Host "DRY RUN - would apply $applied role(s); blank $skippedBlank; already filled $skippedFilled; not found $notFound" -ForegroundColor Cyan
    return
}

$records | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress } | Set-Content -Path $InFile -Encoding UTF8
Write-Host "Applied $applied role(s); blank $skippedBlank; already filled $skippedFilled; not found $notFound" -ForegroundColor Green

if (-not $NoRender) {
    & (Join-Path $PSScriptRoot 'render.ps1') -Module $Module
    Write-Host "Re-rendered $Module.md" -ForegroundColor Green
}
