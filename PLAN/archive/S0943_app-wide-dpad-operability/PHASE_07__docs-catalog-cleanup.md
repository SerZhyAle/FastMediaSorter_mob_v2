# Phase 07 - Docs & catalog cleanup

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog, record the shipped capability, and close journaling for the whole feature.

---

## Prerequisites

- [ ] All implementation phases (01, and any of 02-06 executed) ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `wear.jsonl` if Phase 06 ran) | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |

---

## Steps

### Step 07.1 - Regenerate catalog

**Prompt for developer:**

> Regenerate the class catalog for every module touched: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (and `-Module wear` if Phase 06 ran). Fill `role` + `status` for any new focus classes via `set.ps1`.

**Verification:**

- Catalog sync exits 0.
- `query.ps1 -ClassMatches` finds the new focus classes with a role set.

**Status:** `[ ]` not done

### Step 07.2 - Record capability

**Prompt for developer:**

> Record the shipped capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only): full app-wide non-touch operability with an accurate focus indicator. Do not edit `docs/FEATURES*.md` - that is `/skill-release`-owned from the ALL_FEATURES diff.

**Verification:**

- `scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new record is present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] Catalog regenerated for all touched modules.
- [ ] Capability recorded in `docs/ALL_FEATURES.jsonl`.
- [ ] Dev log entries complete for the whole feature.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Docs/catalog only - regenerate from source if needed. No runtime rollback.
