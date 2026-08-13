# Phase 06 - Docs, catalog, and inventory cleanup

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new dialog/manager/schema classes, record the shipped capability in the feature inventory, and finalize the dev log.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.
- [ ] Project compiles on `standard debug`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated (gitignored) | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

---

## Steps

### Step 06.1 - Regenerate catalog + set roles for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the three new classes via `dev/CATALOG/scripts/set.ps1`: `EdgeGestureConfigDialogFragment` (UI/dialog), `EdgeGestureConfigManager` (UI helper/manager), `EdgeGestureSchemaView` (custom view). They live in `src/main` (all flavors with the capability), so no `-NoFlavors` restriction.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*EdgeGesture*"` lists all three classes with a non-empty role.

**Status:** `[ ]` not done

---

### Step 06.2 - Record shipped capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the extracted edge-gesture configuration dialog (interactive zone/direction map, four zone tabs, general group), `-Spec S1035`, flavors where the gesture overlay capability exists (standard + noLegal). EN-only. Do NOT edit `docs/FEATURES*.md` (that is `/skill-release`-owned).

**Verification:**

- `Grep` - `S1035` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[ ]` not done

---

### Step 06.3 - Finalize dev log

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Ensure one dev-log entry exists per logical change (batch via `close-and-log.ps1 -DevLogs` or `add_to_dev_log.ps1`): schema view, dialog layout (portrait+land), dialog fragment + manager, settings-tab slim, settings-search + docs sync, catalog/inventory. Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S1035` or `edge-gesture` appears in `dev/CHANGELOG.md` for the session's changes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/ALL_FEATURES.jsonl` has the S1035 record; `validate.ps1` exit 0.
- [ ] Settings-doc-sync gate (Phase 05) still green.
- [ ] Ready for `/spec-check S1035` (or device verification via `BlockNeedUserTest`).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Feature is visual + interactive + orientation-sensitive; expect a `BlockNeedUserTest` device pass (open dialog, verify tabs, interactive schema grey/red, tap-to-assign, landscape two-column, button disabled while master off, capability gate) before `/spec-check` marks `Verified`.

---

## Rollback Plan

Docs/catalog/inventory only - revert the generated files; no runtime impact.
