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
# Reads live catalog data, writes none of it (S1534). The value of cases A/B/C/E/F is that they run
# against real specs, so the suite does not fabricate a catalog - it SNAPSHOTS the two journals into
# a sandbox directory and points the CLI at the copy via $env:FMS_SPEC_CATALOG_DIR. Reads stay real,
# writes land in the copy, and the copy is deleted at the end.
#
# What that replaced, and why: probes used to be inserted into the production journal with ids from
# the real next-id.ps1. delete.ps1 is a SOFT delete, so each probe left a row in
# PLAN/spec-catalog-archive.jsonl for good, and New-CatalogId must never reissue an archived id - so
# every run burned 4 spec ids permanently (21 by 2026-08-09) and grew the archive by 4 junk rows.
# Worse, it raced: during S1490 next-id.ps1 handed S1526 to a genuine parked draft, a harness run
# took that id first, and the real insert died with "Duplicate id 'S1526'".
#
# Two rules keep it that way, and both are load-bearing:
#   * Probe ids come from the FIXED reserved block S9991-S9994, never from next-id.ps1. A fixed
#     block far above the live maximum cannot collide with an id a sibling session allocates, which
#     also keeps the probe's PLAN/S999x_*.md filename collision-free.
#   * The finally block asserts against the REAL journals after tearing the sandbox down: none of
#     the probe ids resolve, and no `preview-tests-probe` row exists. The old leak went unmeasured
#     for 13 archive records, so the suite now reports its own regression instead of waiting for an
#     audit to notice.
#
# Probe cleanup is still per record via delete.ps1, never a whole-journal restore (S1490): a probe
# that survives cleanup is named and fails the run, which is how a delete.ps1 failure stays visible.
# preview.ps1 itself is read-only; nothing here mutates a real ticket.
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
$selectPs1 = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'
$archiveJournal = Join-Path $repoRoot 'PLAN/spec-catalog-archive.jsonl'

# Sandbox: a snapshot of both journals that the CLI writes instead of production. Keyed by pid so
# two suites running side by side never share one. Torn down in finally.
$sandboxDir = Join-Path $repoRoot ('temp/scratch/spec-catalog-sandbox-{0}' -f $PID)
New-Item -ItemType Directory -Force -Path $sandboxDir | Out-Null
Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog.jsonl') (Join-Path $sandboxDir 'spec-catalog.jsonl') -Force
Copy-Item $archiveJournal (Join-Path $sandboxDir 'spec-catalog-archive.jsonl') -Force
$env:FMS_SPEC_CATALOG_DIR = $sandboxDir
# The release queue reconciles against the real PLAN/RELEASE_QUEUE.md, which the sandbox has no
# business reordering - SCHEMA.md names this the supported switch for an alternate-catalog run.
$env:FMS_SKIP_RELEASE_QUEUE = '1'

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

$script:probeIds = @()

# Reserved probe ids, mapped by slug rather than handed out by a counter: a case that skips for want
# of a live fixture must not shift the ids of the cases after it, or a failure would name a
# different probe than the one that produced it.
$script:probeIdBySlug = @{
    'preview-tests-probe-note'      = 'S9991'
    'preview-tests-probe'           = 'S9992'
    'preview-tests-probe-relations' = 'S9993'
    'preview-tests-probe-token'     = 'S9994'
}

# Insert a throwaway spec through the CLI and remember it for the finally block. Body is written
# verbatim so a case can shape the exact section-10 / statusNote combination it wants to exercise.
function New-Probe {
    param(
        [Parameter(Mandatory = $true)][string]$Slug,
        [Parameter(Mandatory = $true)][string]$Body,
        [string]$Status = 'BlockByOtherTask',
        [string]$StatusNote
    )

    $id = $script:probeIdBySlug[$Slug]
    if (-not $id) { throw "No reserved probe id for slug '$Slug' - add one to `$script:probeIdBySlug." }
    $file = "PLAN/${id}_$Slug.md"
    [System.IO.File]::WriteAllText((Join-Path $repoRoot $file), $Body.Replace('<ID>', $id))
    # Registered before insert.ps1, not after: the file on disk is already a leak the cleanup owns,
    # so a failed insert must not orphan it in PLAN/.
    $script:probeIds += [PSCustomObject]@{ id = $id; file = $file }

    & $pwshExe -NoProfile -File $insertPs1 -Id $id -Name $Slug `
        -Status $Status -Priority 1 -Tier 2 -File $file *> $null
    if ($LASTEXITCODE -ne 0) { return $null }
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
    # Per record, never the whole journal (S1490) - see the header note.
    $residue = @()
    foreach ($p in $script:probeIds) {
        & $pwshExe -NoProfile -File $deletePs1 -Id $p.id -Confirm *> $null
        Remove-Item -LiteralPath (Join-Path $repoRoot $p.file) -Force -ErrorAction SilentlyContinue
        # "Cleaned" means "no longer live", not "absent": delete.ps1 is a soft delete - it moves the
        # record to PLAN/spec-catalog-archive.jsonl with status Archived, and select.ps1 -Id reads
        # that archive too. Asserting absence here fires on every green run.
        $raw = & $pwshExe -NoProfile -File $selectPs1 -Id $p.id -Format json 2>$null
        $left = if ($raw) { @($raw | ConvertFrom-Json) } else { @() }
        if (@($left | Where-Object { $_.status -ne 'Archived' }).Count -gt 0) { $residue += $p.id }
    }
    if ($residue.Count -gt 0) {
        Write-Host "  FAIL  probe cleanup left $($residue.Count) live record(s) in the catalog" -ForegroundColor Red
        foreach ($leftId in $residue) {
            Write-Host "        pwsh -NoProfile -File scripts/spec_catalog/delete.ps1 -Id $leftId -Confirm" -ForegroundColor Red
        }
        $script:fail += $residue.Count
    }
    Write-Host "$($script:probeIds.Count) probe(s) removed per record" -ForegroundColor DarkGray

    # Sandbox down and the redirect cleared BEFORE the leak check - with the variable still set,
    # select.ps1 would answer from the copy and the check would pass no matter what leaked.
    $env:FMS_SPEC_CATALOG_DIR = $null
    $env:FMS_SKIP_RELEASE_QUEUE = $null
    Remove-Item -LiteralPath $sandboxDir -Recurse -Force -ErrorAction SilentlyContinue

    # Every reserved id is checked, not just the ones this run inserted: a case that skipped for
    # want of a fixture still must not have left a row behind from an earlier, leakier run.
    $leaked = @()
    foreach ($reservedId in ($script:probeIdBySlug.Values | Sort-Object)) {
        $realRaw = & $pwshExe -NoProfile -File $selectPs1 -Id $reservedId -Format json 2>$null
        # A miss prints the literal '[]', which is a non-empty string and so passes a truthiness
        # test - the predicate has to read the parsed count, not the presence of output.
        # @() at the point of use, not at assignment: an empty collection returned from an if-block
        # collapses to $null on the way out, and .Count on $null throws under StrictMode.
        $found = if ($realRaw) { $realRaw | ConvertFrom-Json } else { $null }
        if (@($found).Count -gt 0) { $leaked += $reservedId }
    }
    $archiveJunk = Select-String -Path $archiveJournal -Pattern 'preview-tests-probe' -ErrorAction SilentlyContinue
    if (@($leaked).Count -gt 0 -or @($archiveJunk).Count -gt 0) {
        Write-Host "  FAIL  probes leaked into the REAL catalog - the sandbox did not hold" -ForegroundColor Red
        foreach ($leakedId in $leaked) { Write-Host "        reserved id $leakedId resolves against PLAN/" -ForegroundColor Red }
        if ($archiveJunk.Count -gt 0) {
            Write-Host "        $($archiveJunk.Count) preview-tests-probe row(s) in PLAN/spec-catalog-archive.jsonl" -ForegroundColor Red
            Write-Host "        pwsh -NoProfile -File scripts/spec_catalog/purge-probe-records.ps1" -ForegroundColor Red
        }
        $script:fail++
    } else {
        Write-Host 'no probe reached the real journals' -ForegroundColor DarkGray
    }
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "preview tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "preview tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
