# Phase 04 - Docs, catalog, cleanup

**Strategic spec:** [../S0298_vr-companion-apk-badge.md](../S0298_vr-companion-apk-badge.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Final verification
**Steps done:** 2 / 2
**Started:** 2026-05-27
**Completed:** 2026-05-27

---

## Objective

Close the feature with documentation, catalog refresh, and spec bookkeeping.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | ≤ 400 |
| `docs/FEATURES_noLegal_RU.md` | Modified | ≤ 400 |
| `docs/FEATURES_noLegal_UK.md` | Modified | ≤ 400 |
| `PLAN/S0298_vr-companion-apk-badge.md` | Modified | ≤ 320 |
| `PLAN/S0298_vr-companion-apk-badge/INDEX.md` | Modified | ≤ 260 |

---

## Steps

### Step 04.1 - Update noLegal feature inventory

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** Phase 03

**Prompt for developer:**

> Add one concise bullet to the noLegal feature inventory in EN/RU/UK describing the VR companion APK badge and its manifest-based detection. Do not touch the public `docs/FEATURES*.md` files.

**Verification:**

- `Grep` - `VR companion APK badge` present in `docs/FEATURES_noLegal.md`.
- `Grep` - `VR` and `APK` present in the RU and UK noLegal mirror files on the new bullet line.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 3/3 PASS. Files: `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`. Dev log recorded for all three mirrors.

---

### Step 04.2 - Refresh app_v2 catalog and finalize tactical tracking

**Files:** `PLAN/S0298_vr-companion-apk-badge.md`, `PLAN/S0298_vr-companion-apk-badge/INDEX.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the required post-change closure for modified files, refresh the `app_v2` catalog, and update the tactical tracking files so Phase 04 reflects the executed state. Leave strategic verification to `/spec-check` after build/device validation.

**Verification:**

- `Grep` - `dev/CATALOG/app_v2.md` contains `BrowseApkTileBadgeBinder` or `VrApkClassifier`.
- `Grep` - `Phases:` updated in `INDEX.md`.
- `Grep` - `UI Clarification Status:` remains `READY` in the strategic spec.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 3/3 PASS. Files: strategic spec and tactical index. `catalog_sync.ps1 -Module app_v2` PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - docs and tracking only.
