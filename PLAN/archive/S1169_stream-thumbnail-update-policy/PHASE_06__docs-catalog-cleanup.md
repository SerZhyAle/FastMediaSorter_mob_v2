# Phase 06 - Docs, catalog, capability

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01-05
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Step 06.1: PASS (ALL_FEATURES validate 572 records, catalog regenerated with StreamAdapterPayloads, role/status set). compileStandardDebugKotlin with S1169 probes EXIT=0.
- 2026-07-24 - Step 06.2: PASS (S1169 x15 in CHANGELOG, StreamSourceDao + StreamFrameCache referenced; capability + probes dev-logged).

---

## Objective

Regenerate the class catalog for the new/changed types, record the delivered capability, and journal every modified file. No behavior change in this phase.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done and building.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | - |
| `dev/CHANGELOG.md` | Appended (via `add_to_dev_log.ps1`) | - |

> No `docs/FEATURES*.md` edit - that is `/skill-release`-owned. No settings change -> no settings-manifest regen.

---

## Steps

### Step 06.1 - Record capability + regenerate catalog

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Record the shippable capability via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): stream thumbnails update without flicker or redundant re-capture; dead channels back off instead of re-probing every ~12 s; status changes repaint only the status bullet. Then regenerate the class catalog: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (picks up the new `StreamAdapterPayloads` type and the changed signatures). Set `role`/`status` for `StreamAdapterPayloads` via `set.ps1` if the sync flags it as unclassified.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new capability text present in `docs/ALL_FEATURES.jsonl`.
- `dev/CATALOG/app_v2.jsonl` contains a record for `StreamAdapterPayloads`.

### Step 06.2 - Dev log every modified file

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.1

**Prompt for developer:**

> Batch a single logical dev-log entry for S1169 covering all modified files across phases 01-05 via `pwsh -NoProfile -File scripts/close-and-log.ps1 -DevLogs` (JSON array), or one `add_to_dev_log.ps1` call per file if closing incrementally. Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S1169` present in `dev/CHANGELOG.md`.
- `Grep` - `StreamSourceDao.kt` and `StreamFrameCache.kt` both referenced under the S1169 entry.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Both steps `[x] done`.
- [ ] `/build` passes (final full check).
- [ ] `scripts/all_features/validate.ps1` exits 0.
- [ ] Catalog regenerated.
- [ ] Phase-boundary audit: none needed (docs/catalog only).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, advance S1169 to `BlockNeedUserTest` (device: open Streams grid with a mix of live + dead channels; confirm no favicon<->thumbnail flashing, no busy re-probing of dead tiles, chips stable on rotation), then `/spec-check S1169`.

---

## Rollback Plan

Docs/catalog only - revert the dev-log/catalog commit; no runtime impact.
