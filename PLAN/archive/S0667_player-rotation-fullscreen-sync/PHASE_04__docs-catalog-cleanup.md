# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0667_player-rotation-fullscreen-sync.md`](../S0667_player-rotation-fullscreen-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Record the new capability and regenerate the class catalog for the new resolver class. No source behaviour changes here.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 04.1 - Record capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing: the player auto-switches to fullscreen when the device rotates to landscape and shows the command panel when it rotates to portrait, without interrupting playback, in both the standalone player and the stream player. EN-only text. Do not edit `docs/FEATURES*.md` - those are owned by `/skill-release`.

**Verification:**

- `Grep` - new record in `docs/ALL_FEATURES.jsonl` mentions rotation + fullscreen.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Capability recorded via close-and-log -FuncOp ADD (Video Player area).

---

### Step 04.2 - Regenerate catalog and set role/status for the new class

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the catalog via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then fill `role` and `status` for `PlayerOrientationModeManager` via `dev/CATALOG/scripts/set.ps1`. The class is standard player code (all flavors), no flavor exclusion needed.

**Verification:**

- `Grep` - `PlayerOrientationModeManager` present in `dev/CATALOG/app_v2.jsonl` with a non-empty `role`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Catalog regenerated; role/status set via set.ps1 (2 records).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for the ticket's logical change.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation/catalog only; no runtime impact.
