# Phase 05 - Docs, catalog and capability cleanup

**Strategic spec:** [`../S0620_optional-nine-zone-grid.md`](../S0620_optional-nine-zone-grid.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog, sync the settings docs for the new toggle, and record the capability in the developer inventory. Then hand the feature to the on-device gate.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Modified (generated) | - |
| `docs/SETTINGS_REFERENCE.md` (+ locale variants) | Modified (generated) | - |
| `docs/settings/settings-annotations.json` | Modified | - |
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | - |

---

## Steps

### Step 05.1 - Settings docs sync

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Per CLAUDE.md Rule 22, regenerate the settings manifest + reference and add the annotation for the new `disable_nine_zone_tracking` toggle. Run the settings-doc-sync generator (the one `scripts/quality/assert-settings-doc-sync.ps1` validates), then confirm the gate passes.

**Verification:**

- `scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 05.2 - Catalog regen + capability record + debug tag

**Files:** `dev/CATALOG/app_v2.jsonl`, `docs/ALL_FEATURES.jsonl`, `ui/player/helpers/TouchZoneGestureManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2`. Record the capability via `scripts/all_features/add.ps1` (EN-only): "Optional 9-zone touch grid - turn it off for a simpler previous / zoom / next player layout with a left-edge command-panel area." Insert the BlockNeedUserTest debug tag `Timber.d("S0620: fullscreen 3-zone fallback active (grid off) - left-edge command panel")` at the grid-off tap-dispatch entry in `TouchZoneGestureManager` (one tag, the changed-flow entry).

**Verification:**

- `Grep` - `S0620` present in `docs/ALL_FEATURES.jsonl`.
- `Grep` - exactly one `Timber.d("S0620:` in `app_v2/src`.

**Status:** `[ ]` not done

---

### Step 05.3 - Final build + advance to device gate

**Files:** (none - validation + status)
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `/build` -> `standard debug` (validates code + the inserted tag in one pass). Then advance to `BlockNeedUserTest` with a note describing the device checks: toggle on/off; grid-off fullscreen behaves as 3 zones (prev/zoom/next); left-edge band opens the command panel; vertical swipe still zooms (image) / seeks (video); keyboard/D-pad still reach the panel; settings hides 9-zone rows + shows the 3-zone explanation; default (grid on) unchanged. Confirm the 8% left-edge band feels right vs the "previous" target.

**Verification:**

- `/build` standard debug PASS.
- Journal status `BlockNeedUserTest` with a `-StatusNote`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `/build` standard debug PASS.
- [ ] `dev/CHANGELOG.md` entry per modified file (batched per ticket).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] One `Timber.d("S0620:` tag present (removed by `/spec-check` on the verdict out of `BlockNeedUserTest`).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After device test, `/spec-check` audits and removes the debug tag on the verdict.

---

## Rollback Plan

Docs/catalog are generated - re-run the generators on revert. The capability line in `ALL_FEATURES.jsonl` is removed if the feature is reverted before release.
