# Phase 03 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0938_pinned-stream-reorder.md`](../S0938_pinned-stream-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-04
**Completed:** 2026-07-04

**Step Log:**

- 2026-07-04 - 03.1 ALL_FEATURES record added via close-and-log -FuncOp ADD (Streams / Reorder pinned streams); validate PASS (490 records). 03.2 catalog scanned/rendered, ReorderPinnedStreamUseCase indexed. 03.3 dev-log batch (9 entries) written. Finalized via close-and-log; status -> BlockNeedUserTest.

---

## Objective

Record the shipped capability, regenerate the class catalog for the new use case, and close the change with a dev-log entry.

---

## Prerequisites

- [ ] Phases 01 and 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (append) | + 1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via script) | + 1 entry |

> `docs/FEATURES*.md` is NOT touched here - it is `/skill-release`-owned and populated from the `ALL_FEATURES` diff (CLAUDE.md §11). No settings changed, so the settings-doc-sync gate does not apply.

---

## Steps

### Step 03.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing: user can reorder pinned streams (move up / down / to top) from the three-dot menu of the streams list and grid; the order is honoured by the main-window streams panel and channel selection. EN-only. Then validate: `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a record mentioning `pinned` + `reorder` (or `move`) present in `docs/ALL_FEATURES.jsonl`.
- `all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 03.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (+ `.md`)
**Depends on:** Step 03.1

**Prompt for developer:**

> Regenerate the catalog so the new `ReorderPinnedStreamUseCase` is indexed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set its role/status via `dev/CATALOG/scripts/set.ps1` if the sync left them blank.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ReorderPinnedStream*"` returns the new class.

**Status:** `[ ]` not done

---

### Step 03.3 - Dev log the change

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> One logical dev-log entry for the ticket: `.\scripts\add_to_dev_log.ps1 "PLAN/S0938_pinned-stream-reorder/INDEX.md" "spec-dev" "S0938: reorder pinned streams (up/down/to-top) from list/grid menu"`. Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S0938` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validated.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with the new use case.
- [ ] `dev/CHANGELOG.md` has the S0938 entry.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. Feature enters `BlockNeedUserTest` for on-device verification (list + grid reorder, panel order, player prev/next order, persistence across restart).

---

## Rollback Plan

Documentation/catalog only - revert the appended records; no code or data impact.
