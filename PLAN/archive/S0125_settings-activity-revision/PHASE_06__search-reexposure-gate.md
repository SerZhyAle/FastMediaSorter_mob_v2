# Phase 06 - Search And Re-Exposure Gate

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Finish full revised search parity, complete page-state restoration, and re-enable a guarded public MainActivity launch path only after owner sign-off while keeping legacy Settings as the fallback route.

---

## Prerequisites

- [x] Phase 05 is ✅ Done.
- [x] Manual review confirms all four revised pages are native and no longer hosted as legacy full-page shells.
- [x] Owner sign-off is recorded before Step 06.3 flips any public re-exposure path.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1080 |
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout-land/activity_main.xml` | Modified | ≤ 520 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 180 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3750 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3350 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3350 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 06.1 - Complete multilingual revised search parity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Finish the revised search corpus across all four tabs with canonical reveal behavior, management-entry targeting, and partial-word matching that works regardless of active locale. Remove any legacy-only section ids that no longer exist in the revised page structure.

**Verification:**

- `Grep` - `sectionId = "permissions_access"` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.
- `Grep` - `sectionId = "remote_gamepad"` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.
- `Grep` - `fun search(query: String)` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`. Evidence: `get_errors` clean, `sectionId = "permissions_access"` present, `sectionId = "remote_gamepad"` present, `fun search(query: String)` present, token-based normalized matching added for multilingual partial-word queries, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

### Step 06.2 - Finish reveal, focus, and restoration contracts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Complete the host-to-page reveal contract so tab switch, section expansion, focus request, highlight, and scroll restoration survive rotation and page recreation. Keep legacy `SettingsActivity` untouched as the public fallback while this revised contract stabilizes.

**Verification:**

- `Grep` - `highlightView` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`.
- `Grep` - `capturePageState` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`.
- `Grep` - `InputAction.SearchRequested` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt`. Evidence: `get_errors` clean, `highlightView` present, `capturePageState` present, `InputAction.SearchRequested` present, host restore now replays pending page-state bundles when tab fragments reattach after recreation, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK, `pwsh -NoProfile -File ./build-debug.PS1` finished with `BUILD SUCCESSFUL`.

---

### Step 06.3 - Re-enable the revised MainActivity route behind the owner gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`, `app_v2/src/main/res/layout/activity_main.xml`, `app_v2/src/main/res/layout-land/activity_main.xml`, `app_v2/src/main/AndroidManifest.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 06.2

**Prompt for developer:**

> After owner sign-off, expose `RevisedSettingsActivity` from a separate MainActivity button, keep the legacy `SettingsActivity` button visible as the fallback route, and switch the incubation title to the localized public `Settings` label only if that sign-off explicitly includes the public rename. Update MainActivity-facing labels in EN, RU, and UK in the same change set so the public route is internally consistent.

**Verification:**

- `Grep` - `RevisedSettingsActivity.createIntent` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`.
- `Grep` - `main_settings_new` referenced from `app_v2/src/main/res/layout/activity_main.xml`.
- `Grep` - `btnSettingsLegacy` present in `app_v2/src/main/res/layout/activity_main.xml` as the legacy fallback path.
- `Grep` - `<string name="main_settings_new">Settings</string>` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `<string name="main_settings_new">Настройки</string>` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `<string name="main_settings_new">Налаштування</string>` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt`, `app_v2/src/main/res/layout/activity_main.xml`, `app_v2/src/main/res/layout-land/activity_main.xml`, `app_v2/src/main/AndroidManifest.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`. Evidence: `RevisedSettingsActivity.createIntent` present in `MainActivity.kt`, explicit `btnSettingsLegacy` fallback present in portrait and landscape layouts, `main_settings_new` now resolves to public `Settings` / `Настройки` / `Налаштування`, `check_strings_localized.ps1 -KeyPrefix main_settings_` OK, revised-route restart return preserved through `LocaleHelper.consumeReturnToSettingsTarget`, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK, `pwsh -NoProfile -File ./build-debug.PS1` finished with `BUILD SUCCESSFUL`.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] No remaining Phase 06 TODO markers are present outside this tactical checklist.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

The revised host now has final search behavior and, if owner-approved, a guarded public MainActivity launch path with legacy fallback preserved. Final cleanup can now sync docs, catalog, and audit state.

---

## Rollback Plan

Revert phase commit(s), remove the separate revised MainActivity launch button, and restore the incubation-only title strings. Legacy `SettingsActivity` remains the safe rollback path.