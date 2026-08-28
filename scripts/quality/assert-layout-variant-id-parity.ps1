#requires -Version 7.0
<#
.SYNOPSIS
    android:id parity between sibling copies of one layout - across config variants (S1259)
    and across flavor source sets (S2198).

.DESCRIPTION
    Two axes, one subject: a layout that exists more than once must not lose a view id in one
    of its copies, because the id set is what ViewBinding turns into fields.

    Axis 1 - config variants, symmetric. layout-w600dp beats layout-land whenever the device is
    wide AND landscape (phones rotated, tablets, car head units), so the two are same-shape
    siblings: a view id present in one and missing in the other means runtime findViewById(null)
    on the very devices the -land copy was written for. S1259 shipped exactly that: the S0774
    recording-indicator include existed in layout-land/activity_main.xml but not in
    layout-w600dp/activity_main.xml, and the first show() in landscape NPE'd. Rule 11 covers
    layout <-> layout-land pairing; this axis covers the third sibling mechanically.

    Axis 2 - flavor overrides, one-directional. A flavor source set that ships its own copy of a
    layout wins for that variant, and shared src/main Kotlin still compiles against the binding
    class generated from the winner. So the override's id set must be a SUPERSET of the src/main
    one: an id only the override declares is fine (flavor-only code binds it), an id the override
    drops is an unresolved reference in code the flavor does not own. S2198 shipped exactly that:
    S2177 added tvNameOverlay to src/main/res/layout/item_media_file_grid.xml and bound it in
    MediaFileAdapter, the noLegal override of the same file was not updated, and
    :app_v2:compileNoLegalDebugKotlin failed while the standard variant compiled clean.
    Symmetric comparison is deliberately NOT used here: every noLegal override adds
    browseApkVrBadgeContainer on purpose, and judging that as a violation would make the gate
    unusable.

    Axis 2 reads @+id on the src/main side and both @+id and @id on the override side, because an
    override that reuses a pre-declared id (res/values/ids.xml) still gets a ViewBinding field.

.OUTPUTS
    Exit 0 - parity holds on both axes.
    Exit 1 - at least one sibling pair diverges (listed per file, per axis).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-layout-variant-id-parity.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$declaredIdPattern = 'android:id="@\+id/(\w+)"'
$anyIdPattern = 'android:id="@\+?id/(\w+)"'

function Get-LayoutIds {
    param([string]$Path, [string]$Pattern)
    return @([regex]::Matches((Get-Content $Path -Raw), $Pattern) |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
}

$violations = [System.Collections.Generic.List[string]]::new()

# --- Axis 1: layout-land <-> layout-w600dp (S1259) ---
$landDir = Join-Path $repoRoot 'app_v2/src/main/res/layout-land'
$w600Dir = Join-Path $repoRoot 'app_v2/src/main/res/layout-w600dp'
$sharedVariantCount = 0

if ((Test-Path $landDir) -and (Test-Path $w600Dir)) {
    $landFiles = Get-ChildItem $landDir -Filter *.xml | ForEach-Object Name
    $w600Files = Get-ChildItem $w600Dir -Filter *.xml | ForEach-Object Name
    $shared = @($landFiles | Where-Object { $w600Files -contains $_ })
    $sharedVariantCount = $shared.Count

    foreach ($name in $shared) {
        $idsLand = Get-LayoutIds (Join-Path $landDir $name) $declaredIdPattern
        $idsW600 = Get-LayoutIds (Join-Path $w600Dir $name) $declaredIdPattern
        $onlyLand = @($idsLand | Where-Object { $idsW600 -notcontains $_ })
        $onlyW600 = @($idsW600 | Where-Object { $idsLand -notcontains $_ })
        if ($onlyLand.Count -gt 0 -or $onlyW600.Count -gt 0) {
            $parts = @()
            if ($onlyLand.Count -gt 0) { $parts += "land-only: $($onlyLand -join ', ')" }
            if ($onlyW600.Count -gt 0) { $parts += "w600dp-only: $($onlyW600 -join ', ')" }
            $violations.Add("[config-variant] $name - $($parts -join '; ')")
        }
    }
}

# --- Axis 2: flavor override must be a superset of src/main (S2198) ---
$srcRoot = Join-Path $repoRoot 'app_v2/src'
$mainResRoot = Join-Path $srcRoot 'main/res'
$sharedFlavorCount = 0

if ((Test-Path $srcRoot) -and (Test-Path $mainResRoot)) {
    $flavorDirs = Get-ChildItem $srcRoot -Directory | Where-Object { $_.Name -ne 'main' }
    foreach ($flavorDir in $flavorDirs) {
        $flavorRes = Join-Path $flavorDir.FullName 'res'
        if (-not (Test-Path $flavorRes)) { continue }
        $layoutDirs = Get-ChildItem $flavorRes -Directory -Filter 'layout*' -ErrorAction SilentlyContinue
        foreach ($layoutDir in $layoutDirs) {
            $mainLayoutDir = Join-Path $mainResRoot $layoutDir.Name
            if (-not (Test-Path $mainLayoutDir)) { continue }
            foreach ($file in (Get-ChildItem $layoutDir.FullName -Filter *.xml)) {
                $mainFile = Join-Path $mainLayoutDir $file.Name
                if (-not (Test-Path $mainFile)) { continue }
                $sharedFlavorCount++
                $idsMain = Get-LayoutIds $mainFile $declaredIdPattern
                $idsOverride = Get-LayoutIds $file.FullName $anyIdPattern
                $missing = @($idsMain | Where-Object { $idsOverride -notcontains $_ })
                if ($missing.Count -gt 0) {
                    $rel = "$($flavorDir.Name)/res/$($layoutDir.Name)/$($file.Name)"
                    $violations.Add("[flavor-override] $rel - dropped from src/main: $($missing -join ', ')")
                }
            }
        }
    }
}

if ($violations.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host ("assert-layout-variant-id-parity: PASS " +
            "($sharedVariantCount config-variant pair(s), $sharedFlavorCount flavor override(s) in parity)")
    }
    exit 0
}

Write-Host 'assert-layout-variant-id-parity: FAIL - layout id sets diverge:'
foreach ($v in $violations) { Write-Host "  $v" }
Write-Host '  Fix [config-variant]: mirror the missing views so both wide-layout variants declare the same ids.'
Write-Host '  Fix [flavor-override]: re-declare the dropped views in the override; shared src/main code binds them.'
exit 1
