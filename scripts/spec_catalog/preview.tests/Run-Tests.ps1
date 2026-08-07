# Run-Tests.ps1 (S1073, extended S1482) - regression suite for scripts/spec_catalog/preview.ps1's
# depends_on resolution and auto-skip verdict.
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
# S1482 added the other half. The section-10 read used to scrape every id out of the section body,
# but that section is "Связи с другими спеками" - it lists consumers, successors and neighbours next
# to blockers, and its prose as often DENIES the dependency it mentions. A producer therefore looked
# blocked by its own consumers, and the blocker auto-skip could remove a ready ticket from selection.
# Cases E and F pin the replacement: prose relations yield nothing, an explicit `Блокер:`/`Blocker:`
# token yields exactly the ticket it names.
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
$updatePs1 = Join-Path $repoRoot 'scripts/spec_catalog/update.ps1'
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
$script:probeIds = @()

# Insert a throwaway spec through the CLI and remember it for the finally block. Body is written
# verbatim so a case can shape the exact section-10 / statusNote combination it wants to exercise.
function New-Probe {
    param(
        [Parameter(Mandatory = $true)][string]$Slug,
        [Parameter(Mandatory = $true)][string]$Body,
        [string]$Status = 'BlockByOtherTask',
        [string]$StatusNote
    )

    $id = (& $pwshExe -NoProfile -File $nextIdPs1).Trim()
    $file = "PLAN/${id}_$Slug.md"
    [System.IO.File]::WriteAllText((Join-Path $repoRoot $file), $Body.Replace('<ID>', $id))

    & $pwshExe -NoProfile -File $insertPs1 -Id $id -Name $Slug `
        -Status $Status -Priority 1 -Tier 2 -File $file *> $null
    if ($LASTEXITCODE -ne 0) { return $null }

    $script:probeIds += [PSCustomObject]@{ id = $id; file = $file }
    # insert.ps1 takes no -StatusNote; the note is a separate update, same as in real ticket flow.
    if ($StatusNote) {
        & $pwshExe -NoProfile -File $updatePs1 -Id $id -Status $Status -StatusNote $StatusNote *> $null
    }
    return $id
}

try {
    # --- A: no live BlockByOtherTask spec is offered while its blocker is still open, whatever names
    # that blocker. A spec whose every named blocker has reached Verified/Archived IS offered on
    # purpose - that is the documented conditional eligibility (.claude/reference/spec-next.md), and it
    # is how a ticket returns to the queue once the thing it waited for lands. Asserting the blanket
    # "never offered" made the suite red exactly when the mechanism worked (S1433 / S1463). ---
    Write-Host 'A: a BlockByOtherTask spec is offered only once its blockers are closed' -ForegroundColor Yellow
    $blocked = @(& $pwshExe -NoProfile -File $searchPs1 -Status BlockByOtherTask -Format json | ConvertFrom-Json)
    Assert-That 'A0 the catalog still has BlockByOtherTask specs to check' ($blocked.Count -gt 0) 'none found'
    foreach ($b in $blocked) {
        $pv = Get-Preview $b.id
        $skip = if ($pv -and $pv.auto_skip) { $pv.auto_skip } else { 'null' }
        $deps = @($pv.depends_on)
        $openDeps = @($deps | Where-Object { $_.status -ne 'Verified' -and $_.status -ne 'Archived' })
        if ($deps.Count -gt 0 -and $openDeps.Count -eq 0) {
            $named = ($deps | ForEach-Object { "$($_.id)($($_.status))" }) -join ','
            Assert-That "A1 $($b.id) is released because its blockers closed [$named]" ($skip -eq 'null') "still skipped as '$skip'"
        } else {
            Assert-That "A1 $($b.id) auto_skip is set (got '$skip')" ($skip -ne 'null') 'offered while a blocker is open'
        }
    }

    # --- B: the statusNote source names the blocker precisely - the explicit token only, never every
    # Sxxxx around it. The original fixture was S0426, whose note read "Blocker: S0404" plus a passing
    # "for S0429, external OAuth/CASA cost"; scraping both gave the right verdict for the wrong reason.
    # S0426 has since been Archived and its file moved out of PLAN/, so the case silently degraded to a
    # FAIL on an absent preview. Same shape, hermetic fixture. ---
    Write-Host 'B: statusNote blocker is read from the explicit token, not scraped' -ForegroundColor Yellow
    $liveB = @(& $pwshExe -NoProfile -File $searchPs1 -Status Approved -Format json | ConvertFrom-Json) |
        Select-Object -First 2
    if (@($liveB).Count -ge 2) {
        $tokenId = $liveB[0].id
        $passingId = $liveB[1].id
        $probeB = New-Probe -Slug 'preview-tests-probe-note' -StatusNote "Blocker: $tokenId (waiting on it). Cost for $passingId is external and unrelated." -Body @"
# <ID> - preview.tests probe (statusNote token)

**Status:** BlockByOtherTask

Temporary fixture written by scripts/spec_catalog/preview.tests/Run-Tests.ps1. Deleted by the same run.
The blocker is recorded only in the catalog statusNote, which also mentions a second, passing id.
"@
        $pvB = if ($probeB) { Get-Preview $probeB } else { $null }
        if ($pvB) {
            $ids = @($pvB.depends_on | ForEach-Object { $_.id })
            Assert-That 'B1 depends_on names exactly one blocker' ($ids.Count -eq 1) "got [$($ids -join ',')]"
            Assert-That "B2 that blocker is $tokenId" ($ids -contains $tokenId) "got [$($ids -join ',')]"
            Assert-That "B3 the passing sibling $passingId is not treated as a blocker" (-not ($ids -contains $passingId)) "got [$($ids -join ',')]"
            Assert-That 'B4 reason is blocker-not-verified' ($pvB.auto_skip -eq 'blocker-not-verified') "got '$($pvB.auto_skip)'"
        } else {
            Assert-That 'B0 statusNote probe previewable' $false 'preview returned nothing'
        }
    } else {
        Write-Host '  SKIP  B - fewer than two Approved specs to use as note fixtures' -ForegroundColor DarkGray
    }

    # --- C: no over-skipping - a spec that is not BlockByOtherTask keeps auto_skip null. ---
    Write-Host 'C: a non-blocked spec is not skipped' -ForegroundColor Yellow
    $drafts = @(@(& $pwshExe -NoProfile -File $searchPs1 -Status Draft -Format json | ConvertFrom-Json) |
        Where-Object { -not $_.PSObject.Properties['tier'] -or $_.tier -ne 5 })
    $draft = $drafts | Select-Object -First 1
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
    # A spec body with no **Depends on:** line, no section 10, and a statusNote with no Blocker: token.
    $probeD = New-Probe -Slug 'preview-tests-probe' -Body @"
# <ID> - preview.tests probe

**Status:** BlockByOtherTask

Temporary fixture written by scripts/spec_catalog/preview.tests/Run-Tests.ps1. Deleted by the same run.
It deliberately records no blocker anywhere, to exercise the fail-closed branch.
"@
    Assert-That 'D0 probe spec inserted' ([bool]$probeD) "insert.ps1 exit $LASTEXITCODE"
    if ($probeD) {
        $pvP = Get-Preview $probeD
        $skipP = if ($pvP -and $pvP.auto_skip) { $pvP.auto_skip } else { 'null' }
        Assert-That "D1 unparseable blocker is skipped (got '$skipP')" ($skipP -ne 'null') 'fail-open - offered as a candidate'
        Assert-That "D2 reason is blocker-unresolvable, not blocker-not-verified" ($skipP -eq 'blocker-unresolvable') "got '$skipP'"
    }

    # --- E (S1482): section 10 is a RELATIONS section. Ids named there in prose - consumers,
    # neighbours, successors - are not dependencies, and the prose routinely denies the dependency it
    # mentions ("не блокирует"). Reading them made a producer look blocked by its own consumers and
    # auto-skipped a ready ticket. Nothing directional here, so the verdict must be the fail-closed
    # 'blocker-unresolvable' - "fix the spec" - not a blocker chain invented from neighbours. ---
    Write-Host 'E: section-10 prose relations are not dependencies' -ForegroundColor Yellow
    if ($drafts.Count -ge 2) {
        $relA = $drafts[0].id
        $relB = $drafts[1].id
        $probeE = New-Probe -Slug 'preview-tests-probe-relations' -Body @"
# <ID> - preview.tests probe (section-10 relations)

**Status:** BlockByOtherTask

Temporary fixture written by scripts/spec_catalog/preview.tests/Run-Tests.ps1. Deleted by the same run.

## 10. Связи с другими спеками

- $relA - потребитель того же ярлыка: зависит от этого тикета, а не наоборот.
- $relB - соседняя поверхность, блокирующей зависимости нет.
"@
        Assert-That 'E0 relations probe inserted' ([bool]$probeE) "insert.ps1 exit $LASTEXITCODE"
        $pvE = if ($probeE) { Get-Preview $probeE } else { $null }
        if ($pvE) {
            $idsE = @($pvE.depends_on | ForEach-Object { $_.id })
            Assert-That 'E1 section-10 prose yields no dependencies' ($idsE.Count -eq 0) "got [$($idsE -join ',')]"
            Assert-That 'E2 verdict is blocker-unresolvable' ($pvE.auto_skip -eq 'blocker-unresolvable') "got '$($pvE.auto_skip)'"
        }
    } else {
        Write-Host '  SKIP  E - fewer than two Draft specs to use as relation fixtures' -ForegroundColor DarkGray
    }

    # --- F (S1482): the directional token still works, and only it. A `Блокер: Sxxxx` token in
    # section 10 names the blocker; a second id mentioned in the same section as prose does not. ---
    Write-Host 'F: a Блокер:/Blocker: token in section 10 is read, its prose neighbour is not' -ForegroundColor Yellow
    if ($drafts.Count -ge 2) {
        $blockerId = $drafts[0].id
        $neighbourId = $drafts[1].id
        $probeF = New-Probe -Slug 'preview-tests-probe-token' -Body @"
# <ID> - preview.tests probe (section-10 token)

**Status:** BlockByOtherTask

Temporary fixture written by scripts/spec_catalog/preview.tests/Run-Tests.ps1. Deleted by the same run.

## 10. Связи с другими спеками

- **Блокер: $blockerId** - без него этот тикет не начинается.
- $neighbourId - смежная область, не блокирует.
"@
        Assert-That 'F0 token probe inserted' ([bool]$probeF) "insert.ps1 exit $LASTEXITCODE"
        $pvF = if ($probeF) { Get-Preview $probeF } else { $null }
        if ($pvF) {
            $idsF = @($pvF.depends_on | ForEach-Object { $_.id })
            Assert-That 'F1 the token names exactly one blocker' ($idsF.Count -eq 1) "got [$($idsF -join ',')]"
            Assert-That "F2 that blocker is $blockerId" ($idsF -contains $blockerId) "got [$($idsF -join ',')]"
            Assert-That 'F3 the prose neighbour is not a blocker' (-not ($idsF -contains $neighbourId)) "got [$($idsF -join ',')]"
            Assert-That 'F4 verdict is blocker-not-verified' ($pvF.auto_skip -eq 'blocker-not-verified') "got '$($pvF.auto_skip)'"
        }
    } else {
        Write-Host '  SKIP  F - fewer than two Draft specs to use as blocker fixtures' -ForegroundColor DarkGray
    }
}
finally {
    foreach ($p in $script:probeIds) {
        & $pwshExe -NoProfile -File $deletePs1 -Id $p.id -Confirm *> $null
        Remove-Item -LiteralPath (Join-Path $repoRoot $p.file) -Force -ErrorAction SilentlyContinue
    }
    Copy-Item $bkCatalog $catalog -Force
    Remove-Item $bkCatalog -Force -ErrorAction SilentlyContinue
    Write-Host "catalog restored, $($script:probeIds.Count) probe(s) removed" -ForegroundColor DarkGray
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "preview tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "preview tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
