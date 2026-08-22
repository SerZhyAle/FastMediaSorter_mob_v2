#requires -Version 7.0
<#
.SYNOPSIS
    S1847 calibration suite for the `adb.ps1 clip-check` classification.

.DESCRIPTION
    Hermetic: drives scripts/devtest/lib/ui-tree.ps1 against recorded uiautomator dumps from a real
    Galaxy Watch. No adb call, no device, no network, no writes.

    The cases that matter are the five real dumps. Every one of them scrolls, and the naive test -
    "a corner of the box is further from the centre than the radius" - fired on all five, because
    uiautomator reports bounds already clipped to the screen. None of those five was a defect. So
    the suite asserts ZERO OFF-GLASS findings across them, and goes red the moment anyone widens the
    defect class back to plain geometry.

    The two synthetic controls assert the opposite direction: OFF-GLASS must stay reachable, or the
    exit-9 branch in clip-check is dead code that will never report anything.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/adb-clip-check.tests/Run-Tests.ps1

    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
. (Join-Path $repoRoot 'scripts/devtest/lib/ui-tree.ps1')

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $Expected | actual: $Actual"
        $script:failed++
    }
}

# The watch these dumps came from: 480x480, corner radius 240 at every corner, i.e. a circle.
# clip-check reads this off the device; the suite states it, so the fixtures stay self-describing.
$watch = [ordered]@{ width = 480; height = 480; radius = 240; round = $true; source = 'fixture' }

function Get-Findings {
    param([string]$FixtureName)
    $path = Join-Path $PSScriptRoot "fixtures/$FixtureName"
    $tree = [xml](Get-Content -LiteralPath $path -Raw -Encoding UTF8)
    $out = @{ 'EDGE' = 0; 'CLIPPED' = 0; 'OFF-GLASS' = 0 }
    # Mirrors the clip-check verb's own filter, including the `labelled` half added by S1879 - a
    # suite that selects a different set than the script proves nothing about the script.
    foreach ($n in (@(Get-UiNodes $tree) | Where-Object { $_.leaf -and $_.labelled })) {
        $v = Get-ClipVerdict $n $watch
        if ($null -ne $v) { $out[$v.kind]++ }
    }
    return $out
}

# ---- the real dumps: nothing here was ever a defect ------------------------------------------
# Per-fixture expectations, not just a total: a rule that mislabels one dump while another
# compensates would still add up to zero.
$cases = @(
    @{ file = 'root_after_fix.xml';              edge = 0; clipped = 2 },
    @{ file = 'root_scrolled_after_fix.xml';     edge = 1; clipped = 0 },
    @{ file = 'settings_final_after_fix.xml';    edge = 0; clipped = 1 },
    @{ file = 'ui_resources.xml';                edge = 1; clipped = 0 },
    @{ file = 'files_grid_live.xml';             edge = 3; clipped = 0 }
)
foreach ($c in $cases) {
    $f = Get-Findings $c.file
    Assert-Equal 0           $f['OFF-GLASS'] "$($c.file): no OFF-GLASS - every alarm here is normal scrolling"
    Assert-Equal $c.edge     $f['EDGE']      "$($c.file): EDGE count"
    Assert-Equal $c.clipped  $f['CLIPPED']   "$($c.file): CLIPPED count"
}

# ---- positive controls: the defect class must stay reachable ----------------------------------
$corner = @{ label = 'corner badge'; x1 = 30; y1 = 30; x2 = 120; y2 = 70; scrollAncestor = $false; leaf = $true }
Assert-Equal 'OFF-GLASS' (Get-ClipVerdict $corner $watch).kind 'fixed chrome in a corner has no scroll to save it'

$hero = @{ label = 'hero card'; x1 = 60; y1 = 60; x2 = 420; y2 = 420; scrollAncestor = $true; leaf = $true }
Assert-Equal 'OFF-GLASS' (Get-ClipVerdict $hero $watch).kind 'a box too wide AND too tall cannot fit even at the centre'

# ---- the discriminators themselves -------------------------------------------------------------
$tail = @{ label = 'last row'; x1 = 163; y1 = 466; x2 = 250; y2 = 480; scrollAncestor = $true; leaf = $true }
Assert-Equal 'EDGE' (Get-ClipVerdict $tail $watch).kind 'a box touching the screen edge is cut by the viewport, not the layout'

$middle = @{ label = 'centred label'; x1 = 150; y1 = 220; x2 = 330; y2 = 260; scrollAncestor = $false; leaf = $true }
Assert-Equal '' "$(Get-ClipVerdict $middle $watch)" 'a box in the straight-edged middle is never a finding'

Write-Host ""
Write-Host "passed: $script:passed  failed: $script:failed"
if ($script:failed -gt 0) { exit 1 }
exit 0
