# Phase 07 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Finalize: regenerate the class catalog, record the delivered capability, and update the settings/feature docs touched by the expanded statistics coverage.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done (Phase 05 may be ⏭️ Skipped).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` | Modified | - |

---

## Steps

### Step 07.1 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once so the new constructor injections and event/key additions are reflected. No new classes were introduced (additive enum/event/row changes only); confirm no role/status gaps remain.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[ ]` not done

---

### Step 07.2 - Record delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `scripts/all_features/add.ps1` describing the expanded usage-statistics coverage (renames, favorites, slideshow, scheduled operations, streams, plus any second-wave metrics that shipped). Do not edit `docs/FEATURES*.md` per-spec for the inventory.

**Verification:**

- `Grep` - a new statistics-coverage record present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 07.3 - FEATURES showcase (deferred to /skill-release)

**Files:** none (policy correction)

**Prompt for developer:**

> Do NOT hand-edit `docs/FEATURES*.md` per-spec. Per CLAUDE.md §11 the public showcase is populated ONLY by `/skill-release` from the `ALL_FEATURES` diff since the previous release. The delivered capability is recorded in `docs/ALL_FEATURES.jsonl` (Step 07.2); the next release picks it up automatically. Strategic §8 already states this. This step is intentionally a no-op edit-wise.

**Verification:**

- `Grep` - `usage-statistics.expanded-metric-coverage` present in `docs/ALL_FEATURES.jsonl` (recorded in 07.2; `/skill-release` will surface it).

**Status:** `[x]` done (no-op - showcase is /skill-release-owned)

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file across all phases.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Ready for `/spec-check S0654`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - docs/catalog only; no code or user-facing surface impact.
