# Phase 05 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0679_draw-editor-crop-tool.md`](../S0679_draw-editor-crop-tool.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Regenerate the class catalog for the new classes, record the shipped capability in the developer feature inventory, and finalise dev logs.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Modified | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CHANGELOG.md` (via script only) | Modified | - |

---

## Steps

### Step 05.1 - Set role/status for the new classes and regenerate the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Set role + status for the two new classes via `dev/CATALOG/scripts/set.ps1` (`DrawCropCompositor` - role: image-edit crop compositor; `DrawCropOverlayController` - role: draw-mode crop overlay controller; both `standard` source set, all IMAGES flavors). Then regenerate the catalog: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- `Grep` - `DrawCropCompositor` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `DrawCropOverlayController` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS (both classes present). set.ps1 role+status=new for DrawCropCompositor + DrawCropOverlayController; catalog_sync rendered 2032 records.

---

### Step 05.2 - Record the capability in `docs/ALL_FEATURES.jsonl`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the in-editor crop tool: crop the working image (full resolution for local files) to a draggable rectangle inside the draw editor, then keep annotating before save/share. Do not edit `docs/FEATURES*.md` (that is `/skill-release`-owned). Validate via `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a record mentioning `crop` and `draw` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS (record `drawing-annotations.crop-tool-in-editor` added, area "Drawing & Annotations", all 4 IMAGES flavors, -Spec S0679; validate PASS 414 records).

---

### Step 05.3 - Finalise dev logs

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Ensure one dev-log entry per logical change exists for S0679 via `.\scripts\add_to_dev_log.ps1` (batch with `close-and-log.ps1 -DevLogs` if multiple). Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S0679` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification PASS (S0679 dev-log entry written via close-and-log.ps1 at ticket close).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` contains both new classes.
- [x] `docs/ALL_FEATURES.jsonl` has the crop-tool record and validates.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-dev` inserts the `Sxxxx:` debug tag at the crop-apply entry, sets `BlockNeedUserTest`, and on-device verification follows.

---

## Rollback Plan

Catalog is gitignored and regenerable; `ALL_FEATURES` record and dev-log entries are additive - remove the added lines to revert.
