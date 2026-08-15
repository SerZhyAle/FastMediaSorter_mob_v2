# Phase 07 - Docs + catalog cleanup

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Document the new user-facing capability in FEATURES (trilingual) and regenerate the class catalog for the new public classes. Final phase.

---

## Prerequisites

- [ ] Phases 01-06 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

> `dev/CATALOG/app_v2.jsonl` + `.md` are gitignored local indexes - regenerated, not committed.

---

## Steps

### Step 07.1 - FEATURES trilingual sentence

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence to each FEATURES locale, under the screen-gesture/screenshot capability, stating that each directional screenshot gesture (down / right / up) can be assigned an action: silent screenshot, open in player, open for editing, OCR-translate, share, or disabled. Keep parity across EN/RU/UK. Tone per `docs/COMMUNICATION_POLICY.md`.

**Verification:**

- `Grep` - the new sentence present in each of the three files.

**Status:** `[ ]` not done

---

### Step 07.2 - Regenerate class catalog

**Files:** (generated indexes - not committed)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the new public classes via `set.ps1`: `ScreenshotGestureActionDispatcher`, `ScreenshotGestureActionPickerManager`, `ScreenshotGestureDirection`, `ScreenshotGestureAction`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ScreenshotGestureAction*"` lists the new classes.

**Status:** `[ ]` not done

---

### Step 07.3 - Dev + functionality log

**Files:** (logs)
**Depends on:** Step 07.1

**Prompt for developer:**

> Ensure a `dev/CHANGELOG.md` entry exists for every file modified across phases (via `add_to_dev_log.ps1`). `/spec-dev` records the functionality-log ADD/CHANGE entry on the `Implemented` transition; confirm `dev/FUNCTIONALITY.log` has an S0425 entry, otherwise add one via `scripts/add_to_functionality_log.ps1`.

**Verification:**

- `Grep` - `S0425` present in `dev/FUNCTIONALITY.log`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] FEATURES updated in all three locales.
- [ ] Catalog regenerated; new classes queryable.
- [ ] `dev/FUNCTIONALITY.log` has an S0425 entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0425`.

---

## Rollback Plan

Docs-only - revert the FEATURES edits if needed. Catalog regenerates from source.
