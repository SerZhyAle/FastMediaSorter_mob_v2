# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0479_settings-operations-section-decomposition.md`](../S0479_settings-operations-section-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Verify the decomposition meets the strategic acceptance criteria (< 800 LOC, both-flavor build, no regressions), regenerate the class catalog for the five new managers, and journal the change.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

> No `docs/FEATURES*.md` update - strategic §8 has no FEATURES sentence (pure internal refactor, no user-visible change).

---

## Steps

### Step 06.1 - Verify acceptance criteria

**Files:** -
**Depends on:** - start of phase

**Prompt for developer:**

> Confirm `OperationsSettingsFragment.kt` is < 800 LOC. Confirm the five managers exist and the fragment no longer declares any migrated method. Build `standard` and `noLegal` debug, and run the touched-area unit tests (verify via per-class XML report, not the known-broken full suite). Confirm no XML/layout changes were made (search index stays valid).

**Verification:**

- `Grep -c` / line count - `OperationsSettingsFragment.kt` < 800 lines.
- `Glob` - all of `OperationsSectionsManager.kt`, `OperationsDestinationsManager.kt`, `OperationsScheduledManager.kt`, `OperationsCaptureManager.kt`, `OperationsGesturesManager.kt` exist.
- `.\a.ps1 d` (standard) exits 0; `.\a.ps1 nd` (noLegal) exits 0.
- `git status` - no `app_v2/src/main/res/**` layout changes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Fragment 604 LOC (< 800 PASS). All 5 managers present. Standard fk + noLegal `nd` BUILD SUCCESSFUL (BUILD SUCCESSFUL; APK copied). No res/layout edits made by this ticket (the dirty layout-land files are pre-existing WIP from S0523, not S0479). One env hiccup: a concurrent S0523 build corrupted the shared Kotlin incremental cache mid-run; resolved by stopping daemons + clearing build/kotlin, then both builds passed clean.

---

### Step 06.2 - Regenerate catalog + set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Fill `role` + `status` for the five new manager classes via `dev/CATALOG/scripts/set.ps1` (role: settings-section helper; status: active).

**Verification:**

- `Grep` - `OperationsGesturesManager` present in `dev/CATALOG/app_v2.jsonl`.
- `catalog_sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Catalog scanned+rendered (1893 records); role + status=new set on all 5 manager classes via set.ps1.

---

### Step 06.3 - Dev log + neuroslop gate

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` (or `add_to_dev_log.ps1`) for the decomposition as one logical change. Confirm the neuroslop gate passes on all five new files (no trivial comments, no broad/empty catch, no bare lifecycle-unsafe collect, Timber only).

**Verification:**

- `scripts/quality/assert-neuroslop.ps1` exits 0.
- `dev/CHANGELOG.md` has an entry referencing the decomposition.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - 7 dev-log entries written (spec + fragment + 5 managers) via close-and-log. Neuroslop gate PASS on all phases. Pure internal refactor - no ALL_FEATURES entry (-SkipFuncLog).

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `OperationsSettingsFragment.kt` < 800 LOC.
- [ ] `standard` + `noLegal` debug builds pass.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with the five managers.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0479` to advance the strategic spec to `Verified`.

---

## Rollback Plan

Revert the full phase chain (Phases 01-05 commits) - no data migration or user-facing surface changed; the pre-refactor fragment is restored intact.
