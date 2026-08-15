# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0660_stream-card-overflow-actions-menu.md`](../S0660_stream-card-overflow-actions-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Record the delivered capability and regenerate the class catalog after the new use case.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (via script) | - |

> `docs/FEATURES.md` / `_RU` / `_UK` are NOT edited here - the public showcase is `/skill-release`-owned (CLAUDE.md §11). The strategic §8 sentence is captured as the ALL_FEATURES record below.

---

## Steps

### Step 04.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phase 03

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing: each stream card's overflow (`три точки`) menu now groups channel commands - add to home screen, edit (manual channels), send link, remove - so secondary actions are no longer hidden behind gestures. EN-only.

**Verification:**

- `Grep` - a record mentioning the stream overflow menu present in `docs/ALL_FEATURES.jsonl`.
- Run `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS (record `streams.card-overflow-actions-menu` added; validate 404 records). Dev log recorded.

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Phase 01

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so `UpdateStreamSourceUseCase` is indexed, then set its role/status via `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `Grep` - `UpdateStreamSourceUseCase` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS (use case indexed; role/status set). Dev log recorded.

---

## Phase Done Criteria

- [ ] Both steps `[x] done`.
- [ ] `scripts/all_features/validate.ps1` exits 0.
- [ ] `dev/CATALOG/app_v2.jsonl` includes the new use case.
- [ ] Dev log entry added for the capability record.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. After this, `/spec-dev` inserts the `BlockNeedUserTest` debug probe and advances the journal status.

---

## Rollback Plan

Revert the ALL_FEATURES record; the catalog is a gitignored local index and is simply regenerated.
