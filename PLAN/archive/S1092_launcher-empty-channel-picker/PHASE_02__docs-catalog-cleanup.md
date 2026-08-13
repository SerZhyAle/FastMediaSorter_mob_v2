# Phase 02 - Docs & Catalog Cleanup

**Strategic spec:** [`../S1092_launcher-empty-channel-picker.md`](../S1092_launcher-empty-channel-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-21
**Completed:** 2026-07-21

---

## Objective

Record the delivered capability and refresh the class catalog; no FEATURES change (strategic §8 = "Без изменений").

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 02.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only capability record via `scripts/all_features/add.ps1` describing that the launcher's "add Channel cell" flow routes the user to Settings > Media > Streams when no channels exist yet, instead of opening an empty picker. Flavors from the gate: standard + noLegal (read the record back to confirm; do not copy a sibling's flavor list).

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl` with `flavors` = standard + noLegal.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-21 - Recorded via close-and-log (FuncOp CHANGE, area Launcher, flavors standard,noLegal).

---

### Step 02.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to pick up the LauncherHomeViewModel API change. Do not hand-edit the generated index.

**Verification:**

- `catalog_sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-21 - catalog_sync app_v2 re-run to completion (close-and-log scan hit the 2-min shell cap; resumed standalone).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for the ticket (via `add_to_dev_log.ps1`).
- [ ] `docs/FEATURES*.md` untouched (release-owned; strategic §8 = no change).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert catalog/dev-log entries; no runtime impact.
