<#
.SYNOPSIS
    S2129 - mirrors the phone's resource-icon set into the wear module (single source of truth renderer).

.DESCRIPTION
    A resource created on the phone carries its own icon, chosen from a fixed set of
    `ico-XX-NNN` vector drawables. S2129 ships that identity to the watch by REFERENCE:
    the sync payload carries the short id string, and the watch resolves it locally
    (S2129 ADR-1). Shipping rendered pixels instead was rejected - the sync channel has a
    hard size ceiling that thumbnails already compete for, and a raster at watch icon size
    is visibly worse than a vector.

    That decision only works if the watch actually holds the same vectors. This script is
    what puts them there, and it treats app_v2 as the ONLY source of truth:

      app_v2/src/main/res/drawable/ico_*.xml          (source, hand-authored)
        -> wear/src/main/res/drawable/ico_*.xml        (generated copy)
        -> wear/.../wear/ui/icon/WearResourceIconRegistry.kt  (generated map)

    Both outputs are render targets. Hand-editing either one is what makes the two copies
    drift, and drift here fails SILENTLY: a resource arrives carrying an id the watch does
    not know, `resolveDrawable` answers null, and the watch quietly falls back to the
    type-derived glyph - which is exactly the defect S2129 exists to remove, reappearing
    with no symptom. scripts/quality/assert-resource-icon-parity.ps1 runs -Check to catch it.

    The generated registry is deliberately smaller than the phone's ResourceIconRegistry:
    the watch resolves an id to a drawable and nothing else. The phone's set/ordinal/random
    helpers exist for its icon picker, which the watch does not have.

.PARAMETER Check
    Render into memory and compare against what is on disk. Exit 1 on any drift - a missing
    copy, a stale copy, an extra copy the source no longer has, or a registry that does not
    match the source set. Writes nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/wear/generate-wear-resource-icons.ps1
    pwsh -NoProfile -File scripts/wear/generate-wear-resource-icons.ps1 -Check

.NOTES
    Exit codes:
      0  outputs written, or -Check found them current
      1  -Check found drift (regenerate without -Check)
      2  cannot verify - source directory missing, or it holds no ico_*.xml at all
#>

[CmdletBinding()]
param(
    [switch]$Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sourceDir = Join-Path $repoRoot 'app_v2/src/main/res/drawable'
$targetDrawableDir = Join-Path $repoRoot 'wear/src/main/res/drawable'
$targetRegistry = Join-Path $repoRoot 'wear/src/main/java/com/sza/fastmediasorter/wear/ui/icon/WearResourceIconRegistry.kt'

if (-not (Test-Path $sourceDir)) {
    Write-Host "generate-wear-resource-icons: source directory not found: $sourceDir"
    exit 2
}

$sourceIcons = @(Get-ChildItem -Path $sourceDir -Filter 'ico_*.xml' -File | Sort-Object Name)
if ($sourceIcons.Count -eq 0) {
    Write-Host "generate-wear-resource-icons: no ico_*.xml under $sourceDir - refusing to render an empty set."
    exit 2
}

# `ico_01_001.xml` -> the public id `ico-01-001`. The file name is the id with underscores,
# which is why the map can be derived rather than maintained.
$idPattern = '^ico_(\d{2})_(\d{3})$'
$entries = [System.Collections.Generic.List[object]]::new()
foreach ($icon in $sourceIcons) {
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($icon.Name)
    if ($stem -notmatch $idPattern) {
        Write-Host "generate-wear-resource-icons: '$($icon.Name)' does not match ico_NN_NNN - refusing a partial set."
        exit 2
    }
    $setId = $Matches[1]
    $ordinal = $Matches[2]
    $entries.Add([pscustomobject]@{
        PublicId = "ico-$setId-$ordinal"
        Resource = $stem
        Path     = $icon.FullName
    })
}

$builder = [System.Text.StringBuilder]::new()
[void]$builder.AppendLine('package com.sza.fastmediasorter.wear.ui.icon')
[void]$builder.AppendLine()
[void]$builder.AppendLine('import androidx.annotation.DrawableRes')
[void]$builder.AppendLine('import com.sza.fastmediasorter.wear.R')
[void]$builder.AppendLine()
[void]$builder.AppendLine('/**')
[void]$builder.AppendLine(' * GENERATED FILE - do not edit by hand.')
[void]$builder.AppendLine(' *')
[void]$builder.AppendLine(' * Producer: scripts/wear/generate-wear-resource-icons.ps1')
[void]$builder.AppendLine(' * Source of truth: app_v2/src/main/res/drawable/ico_*.xml')
[void]$builder.AppendLine(' *')
[void]$builder.AppendLine(' * S2129: a resource synced from the phone carries its own icon id, and the watch resolves')
[void]$builder.AppendLine(' * it here. An unknown id answers null so the caller falls back to the type-derived glyph -')
[void]$builder.AppendLine(' * a resource synced before the id existed must still draw, not disappear.')
[void]$builder.AppendLine(' *')
[void]$builder.AppendLine(' * Narrower than the phone''s ResourceIconRegistry on purpose: the watch resolves an id and')
[void]$builder.AppendLine(' * nothing more. The set, ordinal and random helpers there serve the phone''s icon picker.')
[void]$builder.AppendLine(' */')
[void]$builder.AppendLine('object WearResourceIconRegistry {')
[void]$builder.AppendLine()
[void]$builder.AppendLine('    private val registry: Map<String, Int> = mapOf(')
for ($i = 0; $i -lt $entries.Count; $i++) {
    $comma = if ($i -eq $entries.Count - 1) { '' } else { ',' }
    [void]$builder.AppendLine(('        "{0}" to R.drawable.{1}{2}' -f $entries[$i].PublicId, $entries[$i].Resource, $comma))
}
[void]$builder.AppendLine('    )')
[void]$builder.AppendLine()
[void]$builder.AppendLine('    /** The drawable for [iconId], or null when this build does not carry that icon. */')
[void]$builder.AppendLine('    @DrawableRes')
[void]$builder.AppendLine('    fun resolveDrawable(iconId: String?): Int? = iconId?.let { registry[it] }')
[void]$builder.AppendLine('}')
$expectedRegistry = $builder.ToString()

$drift = [System.Collections.Generic.List[string]]::new()

$existingCopies = if (Test-Path $targetDrawableDir) {
    @(Get-ChildItem -Path $targetDrawableDir -Filter 'ico_*.xml' -File)
} else {
    @()
}
$expectedNames = $entries | ForEach-Object { "$($_.Resource).xml" }
foreach ($stale in $existingCopies | Where-Object { $expectedNames -notcontains $_.Name }) {
    $drift.Add("extra copy no longer in the source set: $($stale.Name)")
}
foreach ($entry in $entries) {
    $copyPath = Join-Path $targetDrawableDir "$($entry.Resource).xml"
    if (-not (Test-Path $copyPath)) {
        $drift.Add("missing copy: $($entry.Resource).xml")
    } elseif ((Get-Content -LiteralPath $copyPath -Raw) -ne (Get-Content -LiteralPath $entry.Path -Raw)) {
        $drift.Add("stale copy: $($entry.Resource).xml")
    }
}
if (-not (Test-Path $targetRegistry)) {
    $drift.Add('missing generated registry: WearResourceIconRegistry.kt')
} elseif ((Get-Content -LiteralPath $targetRegistry -Raw) -ne $expectedRegistry) {
    $drift.Add('stale generated registry: WearResourceIconRegistry.kt')
}

if ($Check) {
    if ($drift.Count -eq 0) {
        Write-Host "generate-wear-resource-icons -Check: PASS ($($entries.Count) icons mirrored, registry current)."
        exit 0
    }
    Write-Host "generate-wear-resource-icons -Check: DRIFT ($($drift.Count) finding(s)):"
    foreach ($item in $drift) { Write-Host "  - $item" }
    Write-Host '  Fix: pwsh -NoProfile -File scripts/wear/generate-wear-resource-icons.ps1'
    exit 1
}

if (-not (Test-Path $targetDrawableDir)) {
    New-Item -ItemType Directory -Path $targetDrawableDir -Force | Out-Null
}
$registryDir = Split-Path -Parent $targetRegistry
if (-not (Test-Path $registryDir)) {
    New-Item -ItemType Directory -Path $registryDir -Force | Out-Null
}

foreach ($stale in $existingCopies | Where-Object { $expectedNames -notcontains $_.Name }) {
    Remove-Item -LiteralPath $stale.FullName -Force
}
foreach ($entry in $entries) {
    Copy-Item -LiteralPath $entry.Path -Destination (Join-Path $targetDrawableDir "$($entry.Resource).xml") -Force
}
# -NoNewline: the builder already ends every line, so Set-Content would add a trailing blank
# line that -Check would then read back as permanent drift.
Set-Content -LiteralPath $targetRegistry -Value $expectedRegistry -Encoding utf8 -NoNewline

Write-Host "generate-wear-resource-icons: wrote $($entries.Count) icon(s) and WearResourceIconRegistry.kt."
exit 0
