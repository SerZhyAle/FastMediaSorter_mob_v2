#requires -Version 7.0
<#
.SYNOPSIS
    S1259: android:id parity between res/layout-land and res/layout-w600dp variants.

.DESCRIPTION
    layout-w600dp beats layout-land whenever the device is wide AND landscape (phones rotated,
    tablets, car head units), so the two are same-shape siblings: a view id present in one and
    missing in the other means runtime findViewById(null) on the very devices the -land copy was
    written for. S1259 shipped exactly that: the S0774 recording-indicator include existed in
    layout-land/activity_main.xml but not in layout-w600dp/activity_main.xml, and the first
    show() in landscape NPE'd. Rule 11 covers layout <-> layout-land pairing; this gate covers
    the third sibling mechanically.

    For every *.xml filename present in BOTH res/layout-land and res/layout-w600dp (app_v2
    main source set), the sets of android:id="@+id/.." declarations must be identical.

.OUTPUTS
    Exit 0 - parity holds for every shared filename.
    Exit 1 - at least one shared file has ids on one side only (listed per file).

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
$landDir = Join-Path $repoRoot 'app_v2/src/main/res/layout-land'
$w600Dir = Join-Path $repoRoot 'app_v2/src/main/res/layout-w600dp'

if (-not (Test-Path $landDir) -or -not (Test-Path $w600Dir)) {
    Write-Host 'assert-layout-variant-id-parity: PASS (variant dirs absent)'
    exit 0
}

$idPattern = 'android:id="@\+id/(\w+)"'
$landFiles = Get-ChildItem $landDir -Filter *.xml | ForEach-Object Name
$w600Files = Get-ChildItem $w600Dir -Filter *.xml | ForEach-Object Name
$shared = @($landFiles | Where-Object { $w600Files -contains $_ })

$violations = [System.Collections.Generic.List[string]]::new()
foreach ($name in $shared) {
    $idsLand = @([regex]::Matches((Get-Content (Join-Path $landDir $name) -Raw), $idPattern) |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    $idsW600 = @([regex]::Matches((Get-Content (Join-Path $w600Dir $name) -Raw), $idPattern) |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    $onlyLand = @($idsLand | Where-Object { $idsW600 -notcontains $_ })
    $onlyW600 = @($idsW600 | Where-Object { $idsLand -notcontains $_ })
    if ($onlyLand.Count -gt 0 -or $onlyW600.Count -gt 0) {
        $parts = @()
        if ($onlyLand.Count -gt 0) { $parts += "land-only: $($onlyLand -join ', ')" }
        if ($onlyW600.Count -gt 0) { $parts += "w600dp-only: $($onlyW600 -join ', ')" }
        $violations.Add("$name - $($parts -join '; ')")
    }
}

if ($violations.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-layout-variant-id-parity: PASS ($($shared.Count) shared layout file(s) in parity)"
    }
    exit 0
}

Write-Host 'assert-layout-variant-id-parity: FAIL - id sets diverge between layout-land and layout-w600dp:'
foreach ($v in $violations) { Write-Host "  $v" }
Write-Host '  Fix: mirror the missing views (or drop the extras) so both wide-layout variants declare the same ids.'
exit 1
