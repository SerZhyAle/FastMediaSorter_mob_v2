# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog, record the delivered capability, and add the public FEATURES sentence in all three locales.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Appended (via tool) | n/a |
| `docs/FEATURES.md` + `_RU.md` + `_UK.md` | Modified | n/a |

---

## Steps

### Step 06.1 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to re-scan + render the catalog with the new classes (`StreamFrameCache`, `StreamFrameSnapshotManager`, `StreamGridAdapter`, `StreamGridModeManager`). Set `role`+`status` for any new class flagged unknown via `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `Grep` - `StreamGridModeManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `StreamFrameSnapshotManager` present in `dev/CATALOG/app_v2.jsonl`.
- `catalog_sync.ps1` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Catalog scan+render via close-and-log -CatalogModule app_v2. 4 new classes (StreamFrameCache, StreamFrameSnapshotManager, StreamGridAdapter, StreamGridModeManager) present in app_v2.jsonl.

---

### Step 06.2 - Record delivered capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Append the capability via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): stream browser grid mode showing live channels as tiles with a captured current frame, list/grid toggle persisted across sessions, snapshot-and-release capture bounded by a concurrency limit and TTL cache, gated to flavors with stream support (standard/legacy/noLegal). Reference `spec: S0675`.

**Verification:**

- `Grep` - `S0675` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Recorded via close-and-log -FuncOp ADD (Streams, standard/legacy/noLegal). `S0675` present in `docs/ALL_FEATURES.jsonl` (1 record).

---

### Step 06.3 - FEATURES showcase (deferred to /skill-release)

**Files:** none

**Plan correction (per CLAUDE.md §11):** `docs/FEATURES*.md` is the curated public showcase populated ONLY by `/skill-release` from the `ALL_FEATURES` diff since the previous release - never edited per-spec. The strategic §8 sentence stays in the strategic spec as the source text `/skill-release` will promote. The `ALL_FEATURES.jsonl` record (Step 06.2) is the per-spec deliverable; no direct FEATURES edit here.

**Verification:**

- `Grep` - `S0675` present in `docs/ALL_FEATURES.jsonl` (covered by Step 06.2).

**Status:** `[x]` done - no-op (FEATURES owned by /skill-release).

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for the ticket (via `add_to_dev_log.ps1`).
- [ ] `/spec-check S0675` returns `Verified` (run after this phase).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Docs/catalog only - revert the doc edits; regenerated catalog is gitignored and rebuilds on next sync.
