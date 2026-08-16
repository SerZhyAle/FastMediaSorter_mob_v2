# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1585_incoming-coordinate-navigation-shortcut.md`](../S1585_incoming-coordinate-navigation-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Register the two new classes in the class catalog and record the delivered capability in the feature
inventory.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |

---

## Steps

### Step 03.1 - Sync the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set the role and status of
> the two classes added in Phase 01 via `dev/CATALOG/scripts/set.ps1`. Both live in `src/main`, so no
> `-NoFlavors` hint applies.

**Why:**

CLAUDE.md section "Post-Change" requires new classes to carry role and status in the catalog, which
is the index every later research pass queries before grepping.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*MapsShortLinkResolver*"` returns both classes.

**Status:** `[ ]` not done

---

### Step 03.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` describing, in English, that a place shared from
> Google Maps becomes a launcher shortcut opening navigation from the current position. Set the
> flavors from the gate that actually ships launcher mode rather than from memory, and reference
> `S1585` as the spec.

**Why:**

Strategic §8 states this ticket changes what the user perceives - the shortcut now opens a route
rather than a search - so the capability belongs in the inventory that feeds the public showcase at
release time.

**Verification:**

- `Grep` - `S1585` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Mechanical closure run via `scripts/post-change.ps1` with the whole changed set and `-ScopeToFile`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md.

---

## Rollback Plan

Regenerate the catalog and remove the inventory record - both are derived artifacts with no runtime
effect.
