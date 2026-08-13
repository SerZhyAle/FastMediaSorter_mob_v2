# Phase 03 - Docs / catalog cleanup

**Strategic spec:** [`../S0652_statistics-sorted-counter-semantics.md`](../S0652_statistics-sorted-counter-semantics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Regenerate the class catalog after the `GetStatisticsUseCase` constructor change and record the capability-inventory FIX entry.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (generated) | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` (generated) | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Appended | n/a |

> No `docs/FEATURES*.md` edit (strategic §8 = "Без изменений"). No settings change, so no settings-manifest regen.

---

## Steps

### Step 03.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (generated)
**Depends on:** - start of phase

**Prompt for developer:**

> The `GetStatisticsUseCase` constructor gained a `StatsSink` dependency. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to refresh the local index.

**Verification:**

- `catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` -> OK (`1642 files, 2004 records`); regenerated `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md`. `GetStatisticsUseCase` catalog entry now lists `StatsSink` in constructor deps/injected.

---

### Step 03.2 - Record the capability FIX in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one EN-only record via `scripts/all_features/add.ps1` describing the user-visible result: the Statistics "Sorted" headline now clearly states it counts copied + moved files, and a just-completed copy/move shows immediately on opening the screen (no debounce-induced 0). Reference spec `S0652`. This is a FIX/CHANGE, not a new showcase capability - do not touch `docs/FEATURES*.md`.

**Verification:**

- `Grep` - `S0652` matches in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Added `usage-statistics.sorted-headline-clarified-and-flushed-on-open` for spec `S0652`; `S0652` grep matches once. `all_features/validate.ps1` now exits 0 after normalizing three pre-existing spec-prefixed ids to area-based ids (`video-player.*`, `settings.*`, `general.*`), so the validator is green again.

---

## Phase Done Criteria

- [x] Steps 03.1-03.2 are `[x] done`.
- [x] `dev/CHANGELOG.md` has entries for every modified file across all phases.
- [x] Catalog regenerated.

---

## Handoff Notes to Next Phase

Final phase complete. Next formal ticket step is `/spec-check S0652` if we want to promote the strategic spec past `Implemented`.

---

## Rollback Plan

Catalog is a local gitignored index - regenerate. ALL_FEATURES record removable via edit if the fix is reverted.
