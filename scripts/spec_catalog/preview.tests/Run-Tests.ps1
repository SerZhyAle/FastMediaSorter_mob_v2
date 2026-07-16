# Run-Tests.ps1 (S1073) - regression suite for scripts/spec_catalog/preview.ps1's auto-skip verdict.
#
# What broke: the BlockByOtherTask auto-skip was conditional on the blocker having PARSED
# (`-and $dependsOn.Count -gt 0`). A spec whose blocker could not be parsed fell through with
# auto_skip = null, so /spec-next offered it as a live candidate although its own status asserts it is
# blocked. 4 of the 7 BlockByOtherTask specs record the blocker only in the catalog statusNote, and
# preview read only a `**Depends on:**` line or a `## 10.` section - so all four were fail-open.
#
# Two things get asserted, because a check that only ever goes green proves nothing:
#   * every live BlockByOtherTask spec is skipped, whichever of the three sources names its blocker,
#   * the fail-closed net fires for a spec with NO blocker source at all (probe spec, below),
#   * and a non-blocked spec is still NOT skipped (no over-skipping).
#
# Not hermetic, and deliberately so - the value is in the live catalog:
#   * The fail-closed case has no live fixture (after the statusNote source landed, all 7 specs
#     resolve), so the suite inserts ONE probe spec via insert.ps1 and removes it via delete.ps1 -
#     through the CLI, never by editing the journal. PLAN/spec-catalog.jsonl is additionally backed
#     up and restored in a finally block as a crash net.
#   * preview.ps1 itself is read-only; nothing here mutates a real ticket.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/preview.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$previewPs1 = Join-Path $repoRoot 'scripts/spec_catalog/preview.ps1'
$searchPs1 = Join-Path $repoRoot 'scripts/spec_catalog/search.ps1'
$insertPs1 = Join-Path $repoRoot 'scripts/spec_catalog/insert.ps1'
$deletePs1 = Join-Path $repoRoot 'scripts/spec_catalog/delete.ps1'
$nextIdPs1 = Join-Path $repoRoot 'scripts/spec_catalog/next-id.ps1'
$catalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Get-Preview([string]$id) {
    $raw = & $pwshExe -NoProfile -File $previewPs1 -Id $id -Format json 2>$null
    if (-not $raw) { return $null }
    return $raw | ConvertFrom-Json
}

$bkCatalog = Join-Path $env:TEMP "preview.tests.catalog.$PID.bak"
Copy-Item $catalog $bkCatalog -Force
$probeId = $null

try {
    # --- A: every live BlockByOtherTask spec must be skipped, whatever names its blocker. ---
    Write-Host 'A: no BlockByOtherTask spec is offered as a live candidate' -ForegroundColor Yellow
    $blocked = @(& $pwshExe -NoProfile -File $searchPs1 -Status BlockByOtherTask -Format json | ConvertFrom-Json)
    Assert-That 'A0 the catalog still has BlockByOtherTask specs to check' ($blocked.Count -gt 0) 'none found'
    foreach ($b in $blocked) {
        $pv = Get-Preview $b.id
        $skip = if ($pv -and $pv.auto_skip) { $pv.auto_skip } else { 'null' }
        Assert-That "A1 $($b.id) auto_skip is set (got '$skip')" ($skip -ne 'null') 'offered as a candidate'
    }

    # --- B: the statusNote source names the blocker precisely. S0426's note mentions two ids -
    # "Blocker: S0404" and a passing "for S0429, ..." - and only the first is the blocker. Scraping
    # every Sxxxx would give the right verdict for the wrong reason. ---
    Write-Host 'B: statusNote blocker is read from the explicit token, not scraped' -ForegroundColor Yellow
    $pv426 = Get-Preview 'S0426'
    if ($pv426) {
        $ids = @($pv426.depends_on | ForEach-Object { $_.id })
        Assert-That 'B1 S0426 depends_on names exactly one blocker' ($ids.Count -eq 1) "got [$($ids -join ',')]"
        Assert-That 'B2 that blocker is S0404' ($ids -contains 'S0404') "got [$($ids -join ',')]"
        Assert-That 'B3 the passing sibling S0429 is not treated as a blocker' (-not ($ids -contains 'S0429')) "got [$($ids -join ',')]"
        Assert-That 'B4 reason is blocker-not-verified' ($pv426.auto_skip -eq 'blocker-not-verified') "got '$($pv426.auto_skip)'"
    } else {
        Assert-That 'B0 S0426 previewable' $false 'preview returned nothing'
    }

    # --- C: no over-skipping - a spec that is not BlockByOtherTask keeps auto_skip null. ---
    Write-Host 'C: a non-blocked spec is not skipped' -ForegroundColor Yellow
    $draft = @(& $pwshExe -NoProfile -File $searchPs1 -Status Draft -Format json | ConvertFrom-Json) |
    Where-Object { $_.tier -ne 5 } | Select-Object -First 1
    if ($draft) {
        $pvD = Get-Preview $draft.id
        $skipD = if ($pvD -and $pvD.auto_skip) { $pvD.auto_skip } else { 'null' }
        # tier-5 / owner-gate are legitimate skips for other reasons; only the blocker ones are wrong here.
        $isBlockerSkip = $skipD -in @('blocker-not-verified', 'blocker-unresolvable')
        Assert-That "C1 $($draft.id) (Draft) gets no blocker skip (got '$skipD')" (-not $isBlockerSkip) 'blocker skip on a non-blocked spec'
    } else {
        Write-Host '  SKIP  C1 - no Draft spec in the catalog' -ForegroundColor DarkGray
    }

    # --- D: the fail-closed net. A BlockByOtherTask spec with NO blocker source anywhere must be
    # skipped as 'blocker-unresolvable' - distinct from 'blocker-not-verified' so the operator can
    # tell "wait for the blocker" from "fix the spec". No live fixture exists (all 7 resolve), hence
    # the probe. ---
    Write-Host 'D: fail-closed - BlockByOtherTask with no recorded blocker' -ForegroundColor Yellow
    $probeId = (& $pwshExe -NoProfile -File $nextIdPs1).Trim()
    $probeFile = "PLAN/${probeId}_preview-tests-probe.md"
    $probePath = Join-Path $repoRoot $probeFile
    # A spec body with no **Depends on:** line, no section 10, and a statusNote with no Blocker: token.
    [System.IO.File]::WriteAllText($probePath, @"
# $probeId - preview.tests probe

**Status:** BlockByOtherTask

Temporary fixture written by scripts/spec_catalog/preview.tests/Run-Tests.ps1. Deleted by the same run.
It deliberately records no blocker anywhere, to exercise the fail-closed branch.
"@)
    & $pwshExe -NoProfile -File $insertPs1 -Id $probeId -Name 'preview-tests-probe' `
        -Status BlockByOtherTask -Priority 1 -Tier 2 -File $probeFile *> $null
    $insertOk = ($LASTEXITCODE -eq 0)
    Assert-That 'D0 probe spec inserted' $insertOk "insert.ps1 exit $LASTEXITCODE"
    if ($insertOk) {
        $pvP = Get-Preview $probeId
        $skipP = if ($pvP -and $pvP.auto_skip) { $pvP.auto_skip } else { 'null' }
        Assert-That "D1 unparseable blocker is skipped (got '$skipP')" ($skipP -ne 'null') 'fail-open - offered as a candidate'
        Assert-That "D2 reason is blocker-unresolvable, not blocker-not-verified" ($skipP -eq 'blocker-unresolvable') "got '$skipP'"
    }
}
finally {
    if ($probeId) {
        & $pwshExe -NoProfile -File $deletePs1 -Id $probeId -Confirm *> $null
        Remove-Item -LiteralPath (Join-Path $repoRoot "PLAN/${probeId}_preview-tests-probe.md") -Force -ErrorAction SilentlyContinue
    }
    Copy-Item $bkCatalog $catalog -Force
    Remove-Item $bkCatalog -Force -ErrorAction SilentlyContinue
    Write-Host 'catalog restored, probe removed' -ForegroundColor DarkGray
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "preview tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "preview tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
