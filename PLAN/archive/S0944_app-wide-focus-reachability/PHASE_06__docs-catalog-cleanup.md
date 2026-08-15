# Phase 06 - Docs & catalog cleanup

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Steps done:** 0 / 1

---

## Objective

Regenerate the catalog and record the delivered capability.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |

---

## Steps

### Step 06.1 - Catalog + capability

**Prompt for developer:**

> Regenerate the catalog for touched modules (`catalog_sync.ps1 -Module app_v2`), and record the delivered reachability capability in `docs/ALL_FEATURES.jsonl` via `all_features/add.ps1`.

**Verification:**

- Catalog sync exits 0; `all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step `[x]`; catalog regenerated; capability recorded.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Docs/catalog only. No runtime rollback.
