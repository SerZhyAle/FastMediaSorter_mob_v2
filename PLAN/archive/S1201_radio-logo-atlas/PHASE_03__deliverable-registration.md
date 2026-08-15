# Phase 03 - Deliverable registration

**Strategic spec:** [`../S1201_radio-logo-atlas.md`](../S1201_radio-logo-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Register `STREAM_LOGO_ATLAS` as a first-class downloadable set: enum value, pinned descriptor, per-flavor contribution, Extensions Manager row, and the trilingual strings the row and the download notification need.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and its SHA-256 / byte pins are recorded in `temp/S1201/logo-atlas-build.log`.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSet.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeliverableDownloadWorker.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/DeliveryPromptDialogFragment.kt` | Modified | ≤ 120 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/StandardBundledDeliverableSetsModule.kt` | Modified | ≤ 50 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalBundledDeliverableSetsModule.kt` | Modified | ≤ 50 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/di/LegacyBundledDeliverableSetsModule.kt` | Modified | ≤ 50 |
| `app_v2/src/vrOnly/java/com/sza/fastmediasorter/di/VrBundledDeliverableSetsModule.kt` | Modified | ≤ 50 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> The four DI modules are flavor source sets - the contribution is per flavor by design, exactly as `CHANNEL_PREVIEW_ATLAS` is wired. No `BuildConfig.IS_*` guard lands in `src/main`.

---

## Steps

### Step 03.1 - Add the enum value

**Files:** `domain/delivery/DeliverableSet.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `STREAM_LOGO_ATLAS` to `DeliverableSet` with a KDoc bullet naming it as the on-demand stream logo sprite sheet + `url->index` sidecar (S1201). Expect the build to break in `DeliverableDownloadWorker.featureNameRes` and `DeliveryPromptDialogFragment` - both `when` blocks are exhaustive over this enum, which is the intended compile-time contract; Step 03.4 closes them.

**Verification:**

- `Grep` - `STREAM_LOGO_ATLAS` present in `DeliverableSet.kt`.
- `Grep` - the KDoc list carries a `[STREAM_LOGO_ATLAS]` line.

**Status:** `[x]` done

---

### Step 03.2 - Pin the descriptor

**Files:** `data/delivery/DeliverableDescriptorCatalog.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `LOGO_SHEET_SHA256` / `LOGO_SHEET_MIN_SIZE` / `LOGO_COORDS_SHA256` / `LOGO_COORDS_MIN_SIZE` private constants using the exact values recorded in `temp/S1201/logo-atlas-build.log`, with a comment stating the build they came from (tile count, sheet dimensions, date) and that a regenerated atlas means a new element revision plus new pins, never a silent re-upload. Add `fun streamLogoAtlas(): DeliverableSourceDescriptor` returning `resource("stream-logo-atlas.webp", ..)` + `resource("stream-logo-coords.json", ..)`, mirroring `channelPreviewAtlas()`. Do not invent hashes - if the log is missing, stop and re-run Phase 01 Step 01.4.

**Verification:**

- `Grep` - `fun streamLogoAtlas()` matches exactly once.
- `Grep` - `stream-logo-atlas.webp` and `stream-logo-coords.json` both appear in the descriptor.
- Value equality - each pinned SHA-256 string equals the corresponding `sha256 =` line in `temp/S1201/logo-atlas-build.log`; record `expected: <log value> | actual: <code value>` for both.

**Status:** `[x]` done

---

### Step 03.3 - Contribute the descriptor from every streams flavor

**Files:** the four `*BundledDeliverableSetsModule.kt` files
**Depends on:** Step 03.2

**Prompt for developer:**

> In each of the standard, noLegal, legacy and vrOnly modules, add `DeliverableSet.STREAM_LOGO_ATLAS to DeliverableDescriptorCatalog.streamLogoAtlas()` to the same map that already carries `CHANNEL_PREVIEW_ATLAS`. Change nothing else in those files - lite and photos have `SUPPORT_STREAMS=false` and must stay untouched.

**Verification:**

- `Grep` - `STREAM_LOGO_ATLAS` appears exactly once in each of the four flavor modules.
- `Grep` - `STREAM_LOGO_ATLAS` returns zero hits under `app_v2/src/lite/` and `app_v2/src/photos/`.

**Status:** `[x]` done

---

### Step 03.4 - Close the exhaustive mappings and add the Extensions row

**Files:** `worker/DeliverableDownloadWorker.kt`, `ui/delivery/DeliveryPromptDialogFragment.kt`, `data/delivery/DeliverableInventoryImpl.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add the `DeliverableSet.STREAM_LOGO_ATLAS -> R.string.ext_stream_logo_atlas_title` branch to both exhaustive `when` blocks. In `DeliverableInventoryImpl.getExtensions()`, inside the existing `capabilityAvailability.isStreamsAvailable()` block and directly after the `CHANNEL_PREVIEW_ATLAS` row, add an `ExtensionItem.Module` for `STREAM_LOGO_ATLAS` using `R.string.ext_stream_logo_atlas_title` / `..._desc`, `section = ExtensionSection.STREAMS`, and the same `moduleKey` / `moduleSizeLabel` / `moduleStatusFlow` helpers.

**Verification:**

- `Grep` - `STREAM_LOGO_ATLAS ->` present in both `DeliverableDownloadWorker.kt` and `DeliveryPromptDialogFragment.kt`.
- `Grep` - `ext_stream_logo_atlas_title` referenced in `DeliverableInventoryImpl.kt`.
- `.\a.ps1 fk` compiles (proves both `when` blocks are exhaustive again).

**Status:** `[x]` done

---

### Step 03.5 - Add the trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add `ext_stream_logo_atlas_title` and `ext_stream_logo_atlas_desc` across EN/RU/UK in one lockstep call each: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En .. -Ru .. -Uk ..`. The title names the thing the user gets, not the mechanism ("Station logos" / «Логотипы станций» / «Логотипи станцій»); the description says what improves and roughly how big the download is. Check both strings against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist - no jargon ("atlas", "sprite sheet", "sidecar" are all forbidden in user-visible text), house style `..` and `ё`.

**Verification:**

- `Grep` - both keys present in all three `strings.xml` files (six hits total).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ext_stream_logo_atlas"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist - no internal vocabulary in any locale.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`; also `.\a.ps1 fkn` because noLegal is among the touched source sets.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - focus: the descriptor pins actually match the artifacts, and no flavor got the row without also getting the descriptor (a row with no descriptor offers a download that cannot resolve).

---

## Handoff Notes to Next Phase

- The set is downloadable and visible in the Extensions Manager, but nothing renders it yet; Phase 04 adds the grid tier.

---

## Rollback Plan

Revert the phase commit(s). The enum value is the only cross-cutting change - removing it re-breaks the two `when` blocks, so revert them together.
