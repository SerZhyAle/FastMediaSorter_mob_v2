# Phase 02 - Docs and catalog cleanup

**Strategic spec:** [`../S1289_launcher-resource-cells-composed-logo.md`](../S1289_launcher-resource-cells-composed-logo.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Register the new public types in the class catalog and record the delivered capability in the feature inventory.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree carries the phase 01 changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified (script-written) | n/a |

---

## Steps

### Step 02.1 - Register the new classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for the three new types with `dev/CATALOG/scripts/set.ps1`: `ResourceIconProvider` and `ResourceIcon` as the domain-side contract, `ResourceIconProviderImpl` as its UI implementation. Do not commit the generated index - it is gitignored and regenerated on demand.

**Why:**

A new class that carries no role in the catalog is invisible to the catalog-first lookup every later ticket starts from, which is how the same composed-logo mechanism went unnoticed by the launcher resolver in the first place (strategic §1).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ResourceIconProvider*"` returns both the interface and the implementation.
- The returned rows carry a non-empty `role`.

**Status:** `[x]` done

---

### Step 02.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add one record with `scripts/all_features/add.ps1` describing, in English, that launcher desktop and taskbar resource shortcuts show the resource's composed logo including its user-assigned icon and connection badge. Attribute the record to `S1289`. Do not edit `docs/FEATURES*.md` - that showcase is written by `/skill-release` from the inventory diff.

**Why:**

Strategic §8 states this ticket changes what the user sees, and the inventory is the only per-ticket home for that statement.

**Verification:**

- `Grep` - `S1289` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the inventory record; the catalog index is generated and needs no rollback.
