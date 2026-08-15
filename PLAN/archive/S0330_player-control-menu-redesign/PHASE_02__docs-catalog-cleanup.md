# Phase 02 - Docs Catalog Cleanup

**Strategic spec:** [`../S0330_player-control-menu-redesign.md`](../S0330_player-control-menu-redesign.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01
**Blocks:** final verification
**Steps done:** 2 / 2
**Started:** 2026-06-02
**Completed:** 2026-06-02

---

## Objective

Close the implementation with catalog sync, validation evidence and lifecycle status updates.

---

## Prerequisites

- [x] Phase 01 is Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | generated |
| `dev/CATALOG/app_v2.md` | Modified | generated |
| `dev/CHANGELOG.md` | Modified | existing |
| `PLAN/S0330_player-control-menu-redesign.md` | Modified | existing |
| `PLAN/S0330_player-control-menu-redesign/INDEX.md` | Modified | existing |
| `PLAN/S0330_player-control-menu-redesign/PHASE_01__navigation-redesign.md` | Modified | existing |
| `PLAN/S0330_player-control-menu-redesign/PHASE_02__docs-catalog-cleanup.md` | Modified | existing |

---

## Steps

### Step 02.1 - Run catalog and structural checks

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 01

**Prompt for developer:**

> Run catalog sync for `app_v2`, string parity check for playback control keys if strings changed, and catalog validation. Record the commands and exit codes.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - catalog_sync app_v2 OK (1583 records); validate.ps1 8 OK / 0 WARN / 0 FAIL.

---

### Step 02.2 - Mark implementation awaiting device verification

**Files:** `PLAN/S0330_player-control-menu-redesign.md`, `PLAN/S0330_player-control-menu-redesign/INDEX.md`, `PLAN/S0330_player-control-menu-redesign/PHASE_02__docs-catalog-cleanup.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mark tactical files complete, add the implemented date to the strategic spec, insert one temporary `Timber.d("S0330: <entry-point description>")` probe at the dialog setup entry point, and move the journal status to `BlockNeedUserTest` because D-pad/portrait/landscape verification needs a device.

**Verification:**

- `rg -n "Status: BlockNeedUserTest|Implemented date" PLAN/S0330_player-control-menu-redesign.md` returns hits.
- `rg -n "Timber\\.d\\(\"S0330:" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` returns exactly one hit.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0330 -Format json` reports `BlockNeedUserTest`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Strategic spec marked Implemented + BlockNeedUserTest; one `Timber.d("S0330: ...")` probe inserted at `setupSectionNavigation`; journal moved to BlockNeedUserTest.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has entries for modified files.
- [x] Strategic spec is `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final phase. Run `/spec-test-device S0330` or manually verify portrait, landscape and D-pad focus, then `/spec-check S0330`.

---

## Rollback Plan

Revert S0330 implementation edits and move the spec back to Tactical if device testing fails.
