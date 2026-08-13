# Phase 04 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0559_split-screencapture-menu-standard.md`](../S0559_split-screencapture-menu-standard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new and relocated types, tag the flavor-only impl, and record the delivered capability in the developer feature inventory. No `docs/FEATURES*.md` edits (strategic §8: release-time only).

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done and `assembleStandardDebug` is green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Appended | - |

> The stale `OverlayHostService` comment (strategic §6.5) was already corrected during the Phase 01 move - no separate source edit here.

---

## Steps

### Step 04.1 - Regenerate catalog and tag the flavor-only launcher impl

**Files:** `dev/CATALOG/app_v2.jsonl` (+ rendered `app_v2.md`)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and render after the Phase 01 moves and the Phase 02 new types. Then set role/flavor metadata for the new types: `MenuScreenshotLauncher` (interface, all flavors), `MenuScreenshotLauncherModule`, `MenuScreenshotLauncherImpl`, `ScreenCaptureLauncherModule`. The impl + its binding module live in the screenCapture source set (standard + noLegal only), so mark them with `set.ps1 -NoFlavors "lite,photos,legacy,vr"`. Fill `role`/`status` for any entry the scan left blank.

**Verification:**

- `Grep` - `MenuScreenshotLauncher` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `MenuScreenshotLauncherImpl` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `OverlayHostService` entry path now under `src/noLegal/` (not `src/screenCapture/`) in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 04.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Record the standard-flavor capability in the developer inventory: `pwsh -NoProfile -File scripts/all_features/add.ps1` with area "Screen Capture", a concise EN name/description ("Take a screenshot from the app's Operations settings with a system capture-consent prompt; saved to the configured destination"), flavors `standard,noLegal`, and `-Spec S0559`. Do NOT edit `docs/FEATURES*.md` (release-time, `/skill-release` only).

**Verification:**

- `Grep` - an `S0559` record present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and reflects the source-set moves + new types.
- [ ] `docs/ALL_FEATURES.jsonl` has the S0559 record; `validate.ps1` exits 0.
- [ ] Dev log entry added for the catalog + inventory change.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. On-device acceptance (strategic §11.1-4) is verified by `/spec-dev` (debug tags + `BlockNeedUserTest`) then `/spec-check`.

---

## Rollback Plan

Catalog is a regenerated local index - rerun `catalog_sync.ps1` on the reverted tree. Remove the `ALL_FEATURES.jsonl` S0559 record if the feature is rolled back.
