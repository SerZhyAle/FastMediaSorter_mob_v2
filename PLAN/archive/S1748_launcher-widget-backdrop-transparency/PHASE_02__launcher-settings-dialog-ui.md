# Phase 02 - Launcher Settings Dialog UI

**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2

## Objective

Add widget backdrop transparency setting row to portrait and landscape launcher settings dialogs and wire it in `LauncherSettingsDialogFragment.kt`.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 400 |

## Steps

### Step 02.1 - Add rowLauncherWidgetBackdropAlpha to layouts

**Files:** `layout/dialog_launcher_settings.xml`, `layout-land/dialog_launcher_settings.xml`

**Prompt for developer:**

> Add `SettingsDropdownRow` `rowLauncherWidgetBackdropAlpha` under Desktop group in both portrait and landscape dialog layouts (Rule 11).

**Verification:**

- Both layouts contain identical `rowLauncherWidgetBackdropAlpha` id.

**Status:** `[x]` done

### Step 02.2 - Wire dropdown in LauncherSettingsDialogFragment

**Files:** `LauncherSettingsDialogFragment.kt`

**Prompt for developer:**

> Setup `rowLauncherWidgetBackdropAlpha` with preset options and observe/update `launcherWidgetBackdropAlpha` in `LauncherSettingsDialogFragment`.

**Verification:**

- Selecting an opacity preset updates `AppSettings.launcherWidgetBackdropAlpha`.

**Status:** `[x]` done
