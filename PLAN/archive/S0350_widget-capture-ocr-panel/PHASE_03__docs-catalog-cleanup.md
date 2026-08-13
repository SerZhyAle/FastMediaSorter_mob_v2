# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0350_widget-capture-ocr-panel.md`](../S0350_widget-capture-ocr-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Close mechanical project hygiene for S0350.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0350_widget-capture-ocr-panel.md` | Modified | <= 120 |
| `docs/FEATURES.md` | Modified | <= 160 |
| `docs/FEATURES_RU.md` | Modified | <= 160 |
| `docs/FEATURES_UK.md` | Modified | <= 160 |
| `dev/CATALOG/app_v2.jsonl` | Generated | n/a |

---

## Steps

### Step 03.1 - Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Prompt for developer:**

> Run catalog sync for `app_v2` after the new provider class is added.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `CaptureOcrPanelWidgetProvider` appears in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. `scripts/catalog_sync.ps1 -Module app_v2` exit 0; catalog contains `CaptureOcrPanelWidgetProvider`.

### Step 03.2 - Mark implementation ready for audit

**Files:** `PLAN/S0350_widget-capture-ocr-panel.md`

**Prompt for developer:**

> Set the strategic spec status to `Implemented` after phases complete. Keep S0349 audio as a manual/deferred item in audit.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0350 -Status Implemented` exits 0.
- `Grep` - first `**Status:**` line is `Implemented`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. `scripts/spec_catalog/update.ps1 -Id S0350 -Status Implemented` exit 0; strategic header status is `Implemented`. Feature inventories updated in EN/RU/UK.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Standard debug build passes.

---

## Rollback Plan

Reopen the spec to `Tactical` and fix the failing step.
