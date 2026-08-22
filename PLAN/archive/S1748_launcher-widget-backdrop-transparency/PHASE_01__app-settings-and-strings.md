# Phase 01 - App Settings and Strings

**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 2 / 2

## Objective

Add `launcherWidgetBackdropAlpha` field to `AppSettings`, sync CSV presets, and define localized strings for widget backdrop transparency setting.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

## Steps

### Step 01.1 - Add launcherWidgetBackdropAlpha to AppSettings

**Files:** `AppSettings.kt`, `device_profile_presets.csv`

**Prompt for developer:**

> Add `val launcherWidgetBackdropAlpha: Float = 0.85f` to `AppSettings` model and `LAUNCHER_WIDGET_BACKDROP_ALPHA_OPTIONS = listOf(0.0f, 0.25f, 0.50f, 0.70f, 0.85f, 1.0f)` to its companion. Scaffold CSV presets row.

**Verification:**

- `check_device_profile_presets.ps1` returns 0.

**Status:** `[x]` done

### Step 01.2 - Add localized UI strings

**Files:** `strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Prompt for developer:**

> Add `launcher_settings_widget_backdrop_alpha_title` and backdrop transparency preset option strings in EN, RU, UK.

**Verification:**

- `list-new-lexemes.ps1` reports no missing translations in EN/RU/UK.

**Status:** `[x]` done
