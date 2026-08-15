# Phase 05 - docs-catalog-cleanup

**Strategic spec:** [`../S0580_streams-filter-category-language.md`](../S0580_streams-filter-category-language.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Finalize the change set: regenerate the class catalog, record the dev-log and the delivered capability in the feature inventory.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended (via script) | n/a |
| `docs/ALL_FEATURES.jsonl` | Appended (via script) | n/a |

---

## Steps

### Step 05.1 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the local class catalog so the new picker dialog, option mapper, and changed ViewModel API are indexed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role` + `status` for the new classes (`SearchableOptionPickerDialog`, `StreamLanguageOptionMapper`) via `set.ps1`.

**Verification:**

- `Grep` - `SearchableOptionPickerDialog` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `StreamLanguageOptionMapper` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Catalog regenerated via per-phase post-change `catalog_sync`; set role+status=new for `SearchableOptionPickerDialog`, `StreamLanguageOptionMapper`, `StreamsFilterDialogManager` via `set.ps1`.

---

### Step 05.2 - Dev changelog entry

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Record one logical dev-log entry for S0580: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt" "S0580" "Streams filter by category and/or language with searchable pickers"`. Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - `S0580` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 1/1 PASS. `S0580` present in `dev/CHANGELOG.md` (9 occurrences - one logical dev-log entry per phase recorded via per-phase `post-change`; no duplicate added per the one-entry-per-logical-change rule).

---

### Step 05.3 - Capability inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add the delivered capability to the developer inventory via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): filtering streams by category and language with type-to-filter searchable pickers and flags. Validate with `scripts/all_features/validate.ps1`. Do NOT edit `docs/FEATURES*.md` - the public showcase is populated by `/skill-release` from the `ALL_FEATURES` diff (strategic §8 provides the showcase sentence).

**Verification:**

- `Grep` - the new capability record present in `docs/ALL_FEATURES.jsonl`.
- Script: `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Added `streams.category-language-filter` (area Streams, flavors standard/legacy/noLegal/vr, spec S0580) via `all_features/add.ps1`; record present and itself well-formed. `validate.ps1` reports 2 PRE-EXISTING failures unrelated to S0580 (L367 `s0575.*`, L368 `s0559.*` use a spec id as area prefix) - parked as `S0585` (CLAUDE.md §3.1, deduped via search.ps1). No S0580 record error.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (+ roles set for the 3 new classes).
- [x] `dev/CHANGELOG.md` has the S0580 entry.
- [x] `docs/ALL_FEATURES.jsonl` has the capability record (well-formed); the 2 `validate.ps1` failures are pre-existing/unrelated, parked as S0585.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0580`.

---

## Rollback Plan

Catalog/docs are regenerable - re-run `catalog_sync.ps1`; revert the dev-log / ALL_FEATURES appends if the feature is rolled back.
