# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0363_drawing-command-image-resources.md`](../S0363_drawing-command-image-resources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Extend the existing drawing entry in the trilingual feature docs, regenerate the class catalog, and record dev-log entries for all changed files.

---

## Prerequisites

- [ ] Phase 01, Phase 02, Phase 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

---

## Steps

### Step 04.1 - Extend the drawing feature entry (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the existing "Create drawing" entry (§6 image area) with one clause stating the command is now available for the "All images", "Camera", and "Downloads" resources, and that a drawing created from "All images" is saved to Downloads. Do not add a new feature section. Apply the same clause in all three locales (EN / RU / UK), preserving wording and structure parity. Use the `..` ellipsis and `ё`/`Ё` conventions in the RU text.

**Verification:**

- `Grep` - the new clause string is present in each of `FEATURES.md`, `FEATURES_RU.md`, `FEATURES_UK.md` (one hit each).
- No new top-level feature heading added (manual confirm - existing entry extended only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS (clause present in EN/RU/UK; existing "Blank canvas creation" entry extended, no new heading). Dev log recorded.

---

### Step 04.2 - Regenerate class catalog

**Files:** (generated) `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the catalog so the new `DrawingTargetPolicy` class is indexed. Set its role/status via `set.ps1` if the scan leaves them blank.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - exit 0.
- `Grep` - `DrawingTargetPolicy` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS (catalog sync exit 0; DrawingTargetPolicy indexed). Role/status set via set.ps1 (role filled, status=tested).

---

### Step 04.3 - Dev log + functionality log

**Files:** (logs) `dev/CHANGELOG.md`, `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.2

**Prompt for developer:**

> Confirm a `dev/CHANGELOG.md` entry exists for every file changed across Phases 01-04 (add any missing via `.\scripts\add_to_dev_log.ps1`). Append one functionality-log line: `.\scripts\add_to_functionality_log.ps1 -Id S0363 -Op CHANGE -Description "Create drawing command available for All images / Camera / Downloads resources; All images saves to Downloads"`.

**Verification:**

- `Grep` - `S0363` present in `dev/FUNCTIONALITY.log`.
- `Grep` - `CreateDrawingUseCase`, `DrawingTargetPolicy`, `ResourceOpsMenuManager`, `BrowseStateUiUpdater` each appear in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS (S0363 in FUNCTIONALITY.log; all four class names in CHANGELOG.md). Functionality log CHANGE entry recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] INDEX.md Completion Gate items checked.
- [x] Ready for `/spec-check S0363`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, the ticket moves to `BlockNeedUserTest` (debug tags inserted by `/spec-dev`) for on-device verification on the three resources.

---

## Rollback Plan

Docs and generated catalog only - revert the docs commit; catalog is regenerated, not committed.
