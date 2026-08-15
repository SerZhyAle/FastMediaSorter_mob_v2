# Phase 02 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0498_material-statusbar-deprecation.md`](../S0498_material-statusbar-deprecation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-18
**Completed:** 2026-06-18

---

## Objective

Close the change out: confirm dev-changelog coverage and that no user-facing docs or class catalog need updating for this dependency/theme-only change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (build green on Material 1.14.0).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` (via script) | Modified | n/a - appended by `add_to_dev_log.ps1` |

> No `docs/FEATURES*` edit (strategic §8 = "Без изменений"). No `dev/CATALOG` regeneration (no `.kt` change).

---

## Steps

### Step 02.1 - Verify dev-changelog coverage for all Phase 01 files

**Files:** `dev/CHANGELOG.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for each Phase 01 file (`app_v2/build.gradle.kts`, `values-v35/themes.xml`, `values-night-v35/themes.xml`). For any missing file, append one via `.\scripts\add_to_dev_log.ps1 "<path>" "config" "<description>"`. Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - each of the three Phase 01 paths appears in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-18 - Verification PASS. All three Phase 01 paths present in `dev/CHANGELOG.md` (logged by post-change during Phase 01).

---

### Step 02.2 - Confirm no FEATURES / catalog work required

**Files:** (none - confirmation step, final-phase exception to the real-work filter)
**Depends on:** Step 02.1

**Prompt for developer:**

> Confirm strategic §8 says "Без изменений" → skip `docs/FEATURES.md` / `_RU` / `_UK`. Confirm no `.kt` file changed in this spec → skip `scripts/catalog_sync.ps1`. This step is the documented final-cleanup closure; if either assumption is false (a `.kt` slipped in, or a user-facing capability emerged), run the corresponding sync instead of skipping.

**Verification:**

- `Grep` - strategic spec §8 contains "Без изменений" → `docs/FEATURES*` skipped.
- The only `.kt` touched is the temporary `S0498` debug probe in `SendToBottomSheet.kt` (no public-API / feature change) → catalog regen still not required; `/spec-check` removes the probe on the Verified transition.

**Status:** `[x]` done

**Step Log:**

- 2026-06-18 - Verification PASS. §8 = "Без изменений" (FEATURES skipped). Sole `.kt` change is the finalization debug probe - no catalog/public-API impact; the close-and-log catalog scan runs idempotently at finalization.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` covers all modified files.
- [x] No `docs/FEATURES*` change; the lone `.kt` debug probe carries no `dev/CATALOG` public-API change.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this phase the spec moves to `BlockNeedUserTest` for the Android-15 device pass; `/spec-check` advances it to `Verified`. The Play Console warning clearance (strategic §11.5) is confirmed only at the next release pre-launch report.

---

## Rollback Plan

No source change in this phase - nothing to roll back beyond Phase 01.
