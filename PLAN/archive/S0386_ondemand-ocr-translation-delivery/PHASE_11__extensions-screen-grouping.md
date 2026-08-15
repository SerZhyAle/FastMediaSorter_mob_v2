# Phase 11 - Extensions Screen Grouping (OCR / Translation / Media Playback)

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (11.1/11.2 done & device-verified; 11.3 deferred - see note)
**Depends on:** Phase 08
**Blocks:** Phase 12
**Steps done:** 2 / 3
**Started:** 2026-06-10
**Completed:** 2026-06-10

> **11.3 (manifest-404 not a UI error) deferred:** the optional-manifest 404 is handled at the data-source level (logs at I, falls back to bundled URLs); the red banner originates from the **global** OkHttp error interceptor (shared `AppModule` infra), so suppressing it safely needs a per-request bypass on shared HTTP plumbing - out of scope for this grouping phase, tracked as a separate cosmetic fix. The download itself succeeds regardless.

---

## Objective

Split the flat Extensions list into three labelled sections - OCR, Translation, Media Playback - so the aggregated registry stays readable as it grows (strategic §5.1 Pillar G, owner request 2026-06-10). Also fix the minor UX finding from the 2026-06-10 emulator test: the optional-manifest 404 must not surface as a user-visible red ERROR.

Section membership:
- OCR: OCR Engines (Set B) + OCR language data (added in Phase 12).
- Translation: Translation Module (Set A) + translation language packs (added in Phase 12).
- Media Playback: Audio Visualizations (Set C) + FFmpeg DTS Decoder (Set D).

---

## Prerequisites

- [ ] Phase 08 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/ExtensionItem.kt` | Modified (add a group/section field or a header item type) | ≤ +40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified (tag each item with its section, emit ordered) | ≤ +80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerFragment.kt` | Modified (section headers in the adapter) | ≤ +120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerViewModel.kt` | Modified | ≤ +40 |
| `app_v2/src/main/res/layout/item_extension_section_header.xml` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveryManifestDataSource.kt` | Modified (manifest 404 stays non-error) | ≤ +10 |

---

## Steps

### Step 11.1 - Section model + inventory ordering

**Files:** `ExtensionItem.kt`, `DeliverableInventoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Introduce a section enum (`OCR`, `TRANSLATION`, `MEDIA_PLAYBACK`) and either tag each `ExtensionItem` with its section or interleave header items. `DeliverableInventoryImpl.getExtensions()` returns items grouped + ordered by section: OCR (OCR Engines), Translation (Translation Module), Media Playback (Audio Visualizations, FFmpeg DTS). Keep the existing per-item status flow + size logic intact.

**Verification:**

- `Grep` - a section enum with `OCR`/`TRANSLATION`/`MEDIA_PLAYBACK` exists.
- `Grep` - `DeliverableInventoryImpl` assigns each module to a section.

**Status:** `[x]` done

---

### Step 11.2 - Section headers in the screen

**Files:** `ExtensionsManagerFragment.kt`, `ExtensionsManagerViewModel.kt`, `item_extension_section_header.xml`
**Depends on:** Step 11.1

**Prompt for developer:**

> Render section headers in the RecyclerView (multi-view-type adapter or concat adapter). Header strings localized EN/RU/UK. No hardcoded hex in the header layout - use `?attr`/`@color` (Rule 19). Headers are non-focusable; rows keep keyboard/D-pad/mouse focus.

**Verification:**

- `Grep` - the adapter has a header view type bound to the three section titles.
- `assert-neuroslop.ps1 -Gate` PASS.
- Build `standardDebug`; screen shows three labelled sections.

**Status:** `[x]` done

---

### Step 11.3 - Manifest 404 is not a user error

**Files:** `DeliveryManifestDataSource.kt` (and the OkHttp error surface)
**Depends on:** - start of phase

**Prompt for developer:**

> The version-keyed manifest is optional (B2): a 404 already logs at I and falls back to bundled URLs, but the 2026-06-10 emulator test showed it bubbling to a red "ERROR" banner. Ensure the manifest fetch failure is swallowed at the data-source level (no error propagated to the UI error channel); a real payload `.so` download failure still surfaces normally.

**Verification:**

- `Grep` - manifest fetch catches/handles non-2xx without emitting to the shared error channel.
- On-device: tapping DOWNLOAD does not flash a manifest-404 error banner; a forced bad payload URL still shows an error.

**Status:** `[~]` deferred (see phase header note)

---

## Phase Done Criteria

- [ ] Every `Step 11.*` is `[x] done`.
- [ ] `standardDebug` builds; the screen shows OCR / Translation / Media Playback sections.
- [ ] No manifest-404 error banner on a normal download.
- [ ] `check_strings_localized.ps1` passes for the section titles.
- [ ] Dev log entry per touched file; catalog re-synced.

---

## Rollback Plan

Revert the phase commit - the screen returns to the flat Phase 08 list. No data migration.
