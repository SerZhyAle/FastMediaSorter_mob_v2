# Phase 05 - Docs, catalog, settings-sync cleanup

**Strategic spec:** [`../S0646_settings-simple-list-dialog-unification.md`](../S0646_settings-simple-list-dialog-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-24
**Completed:** 2026-06-24

> Step Log (batched): catalog re-scanned (SimpleValueChoiceDialog indexed, 2004 records); settings manifest regenerated (OCR spinner ids -> row ids, new `rowAudioEmptyStateMode`), annotations renamed + 3 new entries, SETTINGS_REFERENCE*.md re-rendered, `assert-settings-doc-sync.ps1` exits 0; feature-inventory CHANGE record + dev log handled in close-and-log.

---

## Objective

Close the ticket: regenerate the class catalog for the new dialog class, regenerate the settings manifest/reference for the OCR + audio rows whose presentation changed, capture the delivered capability in the feature inventory, and batch the dev-log entries.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | - |
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified (if a row changed naming/behavior) | - |
| `docs/ALL_FEATURES.jsonl` | Appended (CHANGE record) | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

---

## Steps

### Step 05.1 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the new `SimpleValueChoiceDialog`. Then set its role/status via `dev/CATALOG/scripts/set.ps1` (role: reusable settings value-choice dialog wrapper; status: active).

**Verification:**

- `Grep` - `SimpleValueChoiceDialog` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

---

### Step 05.2 - Settings doc-sync (Rule 22)

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** start of phase

**Prompt for developer:**

> The OCR selectors and the audio empty-state selector changed presentation (spinner/dropdown -> trigger row + dialog). Regenerate the settings manifest and reference per the project's settings-doc tooling, and update annotations for any row whose id changed (`rowOcrFontSize`, `rowOcrFontFamily`, `rowOcrEngineType`, `rowPaddleOcrModel`, `rowAudioEmptyStateMode`). Let `assert-settings-doc-sync.ps1` (in `post-change.ps1`) confirm parity.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x] done`

---

### Step 05.3 - Capture delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** start of phase

**Prompt for developer:**

> Record the unification as a CHANGE record via `scripts/all_features/add.ps1` (EN-only), `spec: S0646`: simple value-selection across settings (gesture action, destinations, import method, widget type, document type, OCR font/engine/model, audio visualizer) now uses one minimalistic tap-row + list-dialog pattern. Validate with `scripts/all_features/validate.ps1`. No `docs/FEATURES*.md` showcase edit (strategic spec has no §8 FEATURES sentence).

**Verification:**

- `Grep` - `S0646` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

---

### Step 05.4 - Dev changelog

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 05.1-05.3

**Prompt for developer:**

> Add one ticket-level dev-log entry per logical change (foundation + each migration phase) via `.\scripts\add_to_dev_log.ps1`, batching where sensible. Never edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - `S0646` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `assert-settings-doc-sync.ps1` exits 0.
- [ ] `docs/ALL_FEATURES.jsonl` has an `S0646` record.
- [ ] `dev/CHANGELOG.md` has an `S0646` entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-check S0646`.

---

## Rollback Plan

Docs/catalog/log only - revert the generated artifacts; no runtime impact.
