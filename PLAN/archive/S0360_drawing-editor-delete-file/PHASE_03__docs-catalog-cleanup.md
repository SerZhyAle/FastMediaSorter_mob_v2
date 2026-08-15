# Phase 03 - docs-catalog-cleanup

**Strategic spec:** [`../S0360_drawing-editor-delete-file.md`](../S0360_drawing-editor-delete-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Record the new user-visible capability in the trilingual feature docs and finalize catalog + changelog.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

---

## Steps

### Step 03.1 - Add the FEATURES bullet (EN / RU / UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the drawing/image-editor feature area of each of the three FEATURES files, add one concise bullet stating that the editable file can be deleted directly from the drawing editor's overflow menu (with confirmation; returns to browse). Keep the three files mirrored. This is a public, non-`noLegal` capability - do not touch `FEATURES_noLegal*`.

**Verification:**

- `Grep` - a delete-from-drawing-editor sentence exists in `docs/FEATURES.md`.
- `Grep` - the mirrored sentence exists in `docs/FEATURES_RU.md`.
- `Grep` - the mirrored sentence exists in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. Added "Delete file" bullet to section 6 of FEATURES.md / _RU / _UK (line 59 each).

---

### Step 03.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (gitignored, local)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and re-render the catalog after the Phase 01/02 public-API changes.

**Verification:**

- Command exits 0.
- `Grep` - `deleteCurrentFileAndFinish` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - catalog_sync OK (1640 records); deleteCurrentFileAndFinish present in app_v2.jsonl (count 1).

---

### Step 03.3 - Dev log entries for the docs

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` once per FEATURES file edited in Step 03.1 (target `spec-dev`, English description "S0360: document delete-file action in drawing editor").

**Verification:**

- `Grep` - a 2026-06-05 dev-log entry mentioning S0360 FEATURES exists in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Dev log recorded for FEATURES.md / _RU / _UK (S0360 document delete-file action).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` carry the mirrored bullet (line 59 each).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Dev log entries recorded.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0360` after on-device verification.

---

## Rollback Plan

Revert the doc edits - documentation-only, no runtime impact.
