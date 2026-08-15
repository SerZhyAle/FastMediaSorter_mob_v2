# Phase 12 - Language Data Into Grouped Sections

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (12.1 + 12.3 done & device-verified; 12.2 N/A - see notes)
**Depends on:** Phase 11
**Blocks:** none
**Steps done:** 2 / 3
**Started:** 2026-06-10
**Completed:** 2026-06-10

> **12.2 (translation language packs) - N/A:** translation languages in this app are runtime source/target *selectors*, and ML Kit downloads its language models on demand internally (strategic §6.1 B1: ML-Kit-managed). There is no discrete per-language translation download to surface, so the Translation section shows the Translation Module; the selectors stay as runtime settings.

---

## Objective

Surface the OCR and translation language data - currently managed inside the "Translation, OCR and Google Lens" settings group - inside the grouped Extensions screen under their sections: OCR language data under OCR, translation language packs under Translation (strategic §5.1 Pillar G, owner request 2026-06-10). The managers already exist; this wires them into the inventory, it does not reimplement download logic.

---

## Prerequisites

- [ ] Phase 11 ✅ Done (sections exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified (OCR languages under OCR; translation languages under Translation) | ≤ +120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractModelManager.kt` | Read/reused (OCR `.traineddata`) | - |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt` | Read/reused (Paddle `.nb`, noLegal) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified (drop the in-group language management now owned by the screen, or leave a read-only summary) | ≤ -80 |

---

## Steps

### Step 12.1 - OCR language data under the OCR section

**Files:** `DeliverableInventoryImpl.kt`, `TesseractModelManager.kt` (+ `PaddleOcrModelManager.kt` on noLegal)
**Depends on:** - start of phase

**Prompt for developer:**

> The inventory already exposes OCR language items (Russian/Ukrainian Tesseract models). Place every OCR language item in the OCR section (after OCR Engines). On noLegal, include the Paddle `.nb` model items via `PaddleOcrModelManager` (flavor-guarded, no `BuildConfig` gate in `src/main` - use the existing contributor/flavor seam). Reuse the managers' download/verify/delete; do not duplicate.

**Verification:**

- `Grep` - OCR language items are emitted in the OCR section.
- Build `standardDebug` + `noLegalDebug`; OCR section lists engines + language data.

**Status:** `[x]` done

---

### Step 12.2 - Translation language packs under the Translation section

**Files:** `DeliverableInventoryImpl.kt`
**Depends on:** Step 11.1

**Prompt for developer:**

> Surface the translation language packs (ML Kit downloadable language models) under the Translation section. ML Kit manages its own model store, so these items reflect ML-Kit-managed state (downloaded/available) and route download/delete through the existing translation language manager - the Extensions screen is a view over it, not a second downloader (strategic §6.1 B1: ML Kit language packs are ML-Kit-managed).

**Verification:**

- `Grep` - translation language items are emitted in the Translation section.
- On-device: Translation section lists the module + language packs with status.

**Status:** `[~]` N/A (see header note)

---

### Step 12.3 - Reconcile the Translation/OCR settings group

**Files:** `OtherMediaSettingsFragment.kt` (+ `fragment_settings_other.xml`)
**Depends on:** Step 12.1, 12.2

**Prompt for developer:**

> Now that language data is managed in the grouped screen, remove the redundant in-group language management from the Translation/OCR settings group (keep the source/target language selectors that drive recognition/translation behavior - those are runtime settings, not downloads). The group keeps only the contextual shortcut (Phase 10.2) for downloads. Verify no orphaned strings/handlers remain (Rule 20 dead-weight hygiene).

**Verification:**

- `Grep` - no duplicated language-download UI remains in the Translation/OCR group.
- `assert-neuroslop.ps1 -Gate` PASS; orphaned strings removed.
- Build `standardDebug` + `noLegalDebug`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Removed the inline "High-Quality Offline Models" block (Russian/Ukrainian Tesseract download UI) from `OtherMediaSettingsFragment` + `fragment_settings_other.xml` (portrait + land): deleted the `layoutOcrBestModels` layout block (146 lines each file), the `modelManager`/`rusDownloadJob`/`ukrDownloadJob` fields, the four download/delete click handlers, and the `updateModelStates`/`startDownload`/`deleteModel`/`formatSizeProgress` methods (+ now-unused imports). Removed 7 orphaned `ocr_best_*` strings across EN/RU/UK (kept `ocr_best_model_download`/`_delete` - still used by the Extensions screen). Runtime OCR toggle + font/engine selectors stay. standardDebug + noLegalDebug green; neuroslop PASS; device-verified the group no longer shows the inline models while the Extensions screen OCR section still lists rus/ukr.

---

## Phase Done Criteria

- [ ] Every `Step 12.*` is `[x] done`.
- [ ] OCR section = engines + OCR languages; Translation section = module + translation languages; Media Playback = Set C + Set D.
- [ ] No duplicated language-download surface in the Translation/OCR settings group.
- [ ] `standardDebug` + `noLegalDebug` build; `check_strings_localized.ps1` passes.
- [ ] Dev log entry per touched file; catalog re-synced; FEATURES updated if the user-visible management location changed.

---

## Rollback Plan

Revert the phase commit - language data returns to the Translation/OCR group. No data migration (managers and stored files are unchanged).
