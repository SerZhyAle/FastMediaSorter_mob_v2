# Phase 05 - docs-catalog-cleanup

**Strategic spec:** [`../S0439_screen-rotation-follow-os.md`](../S0439_screen-rotation-follow-os.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Document the user-facing change trilingually, regenerate the class catalog for the new classes, and close out the dev log.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 05.1 - FEATURES trilingual update

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence per locale describing the change: the "follow OS rotation" setting now applies to the whole program and is renamed accordingly, and a separate player-only "follow OS rotation" setting is available, shown when the program setting is off. Place it under the existing settings/orientation section. Do not duplicate an existing FEATURES entry.

**Verification:**

- `Grep` - a rotation/orientation sentence mentioning program and player scope present in `docs/FEATURES.md`.
- `Grep` - the corresponding sentence present in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS. Added "Follow OS auto-rotate, program-wide or player-only" bullet to FEATURES.md/_RU/_UK (section 16, after the keep-screen-on bullet). noLegal FEATURES untouched (core feature).

---

### Step 05.2 - Catalog regen and role/status for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the catalog: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role` + `status` for the two new classes via `dev/CATALOG/scripts/set.ps1`: `AppOrientationManager` (role: program-wide screen-orientation applier) and `SelfManagedScreenOrientation` (role: marker opting an activity out of the applier).

**Verification:**

- `Grep` - `AppOrientationManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `SelfManagedScreenOrientation` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - catalog_sync app_v2 regenerated (1814 records). set.ps1 matched + updated role/status=new for both AppOrientationManager and SelfManagedScreenOrientation.

---

### Step 05.3 - Dev log closure

**Files:** -
**Depends on:** Step 05.1, 05.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every file modified across Phases 01-05 via `.\scripts\add_to_dev_log.ps1`. Add any missing entries.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains entries referencing `AppOrientationManager.kt` and `PlaybackSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - CHANGELOG carries entries for all modified files across P01-P05 (incl. AppOrientationManager.kt, PlaybackSettingsFragment.kt). FEATURES trilingual logged.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` each carry the new sentence.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with both new classes.
- [ ] `dev/CHANGELOG.md` complete for all modified files.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0439`.

---

## Rollback Plan

Docs and catalog only - revert the FEATURES edits and regenerate the catalog. No code or data surface touched in this phase.
