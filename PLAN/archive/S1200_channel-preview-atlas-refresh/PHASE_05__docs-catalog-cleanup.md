# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1200_channel-preview-atlas-refresh.md`](../S1200_channel-preview-atlas-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Record the capability and note in the delivery README that a rebuilt payload now needs fresh pins to reach anyone.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | - |
| `delivery/stream-catalog/README.md` | Modified | - |

---

## Steps

### Step 05.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` in user terms: a downloadable extension whose content has changed now offers itself again instead of staying silently on the old copy. Read `-Flavors` off the actual gate - the mechanism is flavor-agnostic, so it ships wherever downloadable extensions do. EN only.

**Verification:**

- `Grep` - `S1200` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Note the rebuild consequence

**Files:** `delivery/stream-catalog/README.md`

**Prompt for developer:**

> Both atlas sections already say a rebuilt sheet needs a new element revision plus fresh pins. Add the reason and the consequence: the compiled pins are what the app compares against to notice a stale copy (S1200), so a re-upload under the same name reaches nobody - not because the download fails, but because no installed copy ever looks out of date.

**Verification:**

- `Grep` - `S1200` referenced in `delivery/stream-catalog/README.md`.
- `Grep` - the note appears once, not duplicated per atlas section.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `/spec-check S1200` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation and index only - revert the commit; no runtime effect.
