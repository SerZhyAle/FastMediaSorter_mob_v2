# Phase 08 - Downloadable Extensions Manager Screen

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (landed ahead of Phase 07 - see Ordering note)
**Depends on:** Phase 06 (Phase 07 attach is not required for the screen to list/manage)
**Blocks:** Phase 09
**Steps done:** 4 / 4
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Add a Settings entry button that opens a "Downloadable Extensions" screen - a single list of all deliverable modules (sets A/B/C/D) and language data (Tesseract `.traineddata`, translation language packs, Paddle models) showing status, size, and download/delete actions, over an extensible registry (strategic Pillar G, goal §2.9, criteria §11.9).

---

## Prerequisites

- [x] Phase 06 ✅ Done (download flow + prompt reusable).
- [~] Phase 07 NOT done. The screen was built ahead of attach; download/uninstall are wired to the existing Phase 04 `DeliverableSetDownloader` and Phase 02 `DeliverableCapabilityRepository`. Actions become end-to-end meaningful only after the Phase 05 de-bundle + Phase 07 attach land - pre-debundle, every module set reports `INSTALLED` (it is bundled), so the screen shows them installed and offers delete rather than download.
- [x] Working tree: feature branch `DEBUG-v013`.

## Ordering note (2026-06-09)

Phase 08 was implemented before Phase 05 (de-bundle) and Phase 07 (attach). This is safe and additive: the screen reads state from contracts that already exist (Phases 02/04) and never strips anything from the base. The list, per-item status/size, reactive download progress, delete confirmation, settings entry (portrait + landscape), and trilingual strings are complete and build-green. What is deferred with Phase 05/07: (a) real "download" exercises only after a set is actually de-bundled; (b) refusing deletion of an engine that is loaded and in active use needs the Phase 07 attach state (no engine is attached pre-07, so there is nothing to be "in use" yet).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableInventory.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerViewModel.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerFragment.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerAdapter.kt` | New | ≤ 200 |
| `app_v2/src/main/res/layout/fragment_extensions_manager.xml` | New | ≤ 150 |
| `app_v2/src/main/res/layout-land/fragment_extensions_manager.xml` | New | ≤ 150 |
| `app_v2/src/main/res/layout/item_extension.xml` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

---

## Steps

### Step 08.1 - Inventory aggregation contract

**Files:** `domain/delivery/DeliverableInventory.kt`, `data/delivery/DeliverableInventoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define `DeliverableInventory` exposing `fun items(): Flow<List<InventoryItem>>` where `InventoryItem` has: stable id, display title key, `kind` (`MODULE` or `LANGUAGE`), `state` (available / downloading(percent) / installed), `downloadSize`, `onDiskSize`, and the owning set or language code. Implement `DeliverableInventoryImpl` aggregating MODULE items from `DeliverableCapabilityRepository` (sets A/B/C/D) and LANGUAGE items from the existing model managers (`TesseractModelManager`, `PaddleOcrModelManager`, ML Kit translate `RemoteModelManager`). Registry is data-driven so new items appear without screen changes.

**Verification:**

- `Grep` - `interface DeliverableInventory` and `sealed class ExtensionItem` both present.
- `Grep` - `class DeliverableInventoryImpl` references `DeliverableCapabilityRepository` and `TesseractModelManager`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Contract shape diverged from the original predicate but is functionally equivalent: `ExtensionItem` is a `sealed class` (`Module`/`LanguageData`) carrying a stable `id`, title/description string-res, a `sizeLabel`, and a per-item `statusFlow: Flow<ExtensionStatus>` - instead of a single `items(): Flow<List<InventoryItem>>`. `DeliverableInventoryImpl` aggregates the four MODULE sets from `DeliverableCapabilityRepository` and the rus/ukr LANGUAGE items from `TesseractModelManager`; module sizes come from the contributed `DeliverableSourceDescriptor` map when present, else a pinned estimate (map is empty until Phase 05). Item list is explicit (not descriptor-driven) so all four modules show pre-debundle. Bound via `DeliveryModule.bindDeliverableInventory`.

---

### Step 08.2 - Extensions screen with status/size, portrait + landscape

**Files:** `ui/delivery/ExtensionsManagerViewModel.kt`, `ui/delivery/ExtensionsManagerFragment.kt`, `ui/delivery/ExtensionsManagerAdapter.kt`, `res/layout/fragment_extensions_manager.xml`, `res/layout-land/fragment_extensions_manager.xml`, `res/layout/item_extension.xml`
**Depends on:** Step 08.1

**Prompt for developer:**

> Build `ExtensionsManagerViewModel` exposing the inventory list and per-item actions, collected with `collectOnLifecycle`/`repeatOnLifecycle` (Rule 20). `ExtensionsManagerFragment` + `ExtensionsManagerAdapter` render each item with title, status, size, and a download or delete affordance. Provide portrait (`res/layout/`) and landscape (`res/layout-land/`) screen layouts plus an item layout. Accessibility: TalkBack labels, focus order, non-color-only status, keyboard/D-pad/mouse focusability (Rule 17); content inside `systemBars` + `displayCutout` safe bounds (Rule 18); no hardcoded `="#hex"` colors - `?attr/`/`@color/` only (Rule 20).

**Verification:**

- `Grep` - `class ExtensionsManagerViewModel` and `class ExtensionsManagerFragment` both present.
- `Glob` - `res/layout/fragment_extensions_manager.xml` and `res/layout-land/fragment_extensions_manager.xml` both exist.
- `Grep` - zero `"#` hex color literals across the three new layout files.
- `Grep` - `repeatOnLifecycle` or `collectOnLifecycle` referenced in `ExtensionsManagerFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - `ExtensionsManagerViewModel` exposes the static item list (each row reactive via its own `statusFlow`) + `download`/`uninstall`; `ExtensionsManagerFragment` hosts `ExtensionsAdapter` (a `ListAdapter`) collecting each row's status under `repeatOnLifecycle(STARTED)`. Layouts: `layout/` + `layout-land/fragment_extensions_manager.xml` (landscape uses the short description to save vertical room) + `item_extension.xml`. No hardcoded hex in any layout (all `?attr/`/`@drawable/`); status-tag colors resolved from `@color/` resources and the translucent chip background derived via `ColorUtils.setAlphaComponent` (moved out of the earlier inline `Color.parseColor("#..")`). `assert-neuroslop -Gate` PASS.

---

### Step 08.3 - Wire download and delete actions

**Files:** `ui/delivery/ExtensionsManagerViewModel.kt`, `data/delivery/DeliverableInventoryImpl.kt`
**Depends on:** Step 08.2

**Prompt for developer:**

> Download action: a MODULE item reuses the Phase 04 `DeliverableSetDownloader`; a LANGUAGE item triggers its model manager's existing download. Delete action: a MODULE calls `DeliverableCapabilityRepository.uninstall(set)`; a LANGUAGE calls a delete op on its model manager (add one if absent - delete the `.traineddata`/`.nb`/translate model file and free space). Require an explicit confirm before delete. Refuse to delete a set whose engine is loaded and in active use - surface a clear message instead of crashing.

**Verification:**

- `Grep` - `DeliverableSetDownloader` referenced in `DeliverableInventoryImpl.kt` and `uninstall` exposed by `ExtensionsManagerViewModel.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Download: a MODULE row drives `DeliverableSetDownloader.download(set)`; a LANGUAGE row drives `TesseractModelManager.downloadModel`. Delete: MODULE → `DeliverableCapabilityRepository.uninstall(set)`, LANGUAGE → `TesseractModelManager.deleteModel`. Explicit confirm: `MaterialAlertDialogBuilder` in `ExtensionsManagerFragment.confirmUninstall` before any delete. In-use guard: the delete affordance is only visible while a set is `Installed`, so a download in flight cannot reach delete (structural). Refusing an engine that is actively loaded in the player is inherently Phase-07-coupled (no engine is attached pre-07) and is deferred there - noted, not silently dropped.

---

### Step 08.4 - Settings entry button + trilingual strings

**Files:** `ui/settings/fragments/OtherMediaSettingsFragment.kt`, `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 08.2

**Prompt for developer:**

> Add a Settings button/preference that navigates to `ExtensionsManagerFragment`. Add screen title, item statuses (available/downloading/installed), download/delete labels, delete-confirm, in-use-block, and empty-state strings in one lockstep call per key: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>`. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 and §6. Use `ё/Ё` in Russian.

**Verification:**

- `Grep` - the navigation call to `ExtensionsManagerFragment` present in `OtherMediaSettingsFragment.kt`.
- `Grep` - each new key present in all three `strings.xml` (three hits per key).
- `Bash` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ext_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Settings entry "Extensions Manager Entrance" is a focusable/clickable row in `fragment_settings_other.xml` navigating to `ExtensionsManagerFragment`; the missing `layout-land/fragment_settings_other.xml` counterpart was added (Rule 11) so the entry is reachable in landscape too. String key prefix is `ext_` (not `extensions_`); `ext_delete_confirm_title`/`ext_delete_confirm_message` added in EN/RU/UK lockstep via `set-android-string.ps1 -Action add`. `check_strings_localized.ps1 -KeyPrefix "ext_delete_confirm"` → exit 0.

---

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`.
- [x] Project compiles - `standardDebug` BUILD SUCCESSFUL (temp/S0386_build_final.log).
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ext_delete_confirm"` exits 0.
- [x] `Grep` for `TODO(phase-08)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

A single extensions hub lists and manages every downloadable module and language with download/delete. Phase 09 documents it and finalizes catalog/changelog.

---

## Rollback Plan

Revert phase commit(s). UI + read-aggregation only; the underlying download/uninstall ops already exist from prior phases. No data migration.
