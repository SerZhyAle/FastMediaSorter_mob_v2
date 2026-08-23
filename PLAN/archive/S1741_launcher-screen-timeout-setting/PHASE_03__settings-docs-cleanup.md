# Phase 03 - Settings Docs Cleanup

**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Completed:** 2026-08-17

## Objective

Expose the timeout in launcher settings and regenerate the settings documentation.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 1,500 |
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1,500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1,500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1,500 |

## Steps

### Step 03.1 - Add timeout selector and manual entry

**Files:** `LauncherSettingsDialogFragment.kt`, both launcher-settings layouts, EN/RU/UK strings

**Prompt for developer:**

> Add a Desktop-section selector for Off, 5, 15, 30, 60, 300 seconds and Custom. Custom accepts positive whole seconds. Check new copy against `docs/COMMUNICATION_POLICY.md` §§2 and 6.

**Why:**

The fixed presets and manual input are explicit owner requirements; both orientations must keep the same view-binding ids.

**Verification:**

- Each layout declares the same selector id. (PASS)
- EN/RU/UK contain every new string key. (PASS)
- Strings pass COMMUNICATION_POLICY §6 checklist. (PASS)

**Status:** `[x]` done

### Step 03.2 - Regenerate settings reference

**Files:** generated settings reference artifacts

**Prompt for developer:**

> Regenerate the settings manifest and EN/RU/UK references; update annotations for the dialog-hosted launcher row.

**Why:**

Settings documentation must describe every persisted setting, including dialog-hosted rows.

**Verification:**

- `assert-settings-doc-sync.ps1` returns exit 0. (PASS)

**Status:** `[x]` done

### Step 03.3 - Run closure and static checks

**Files:** all modified files

**Prompt for developer:**

> Run post-change closure, catalog sync, fast checks, and the standard debug build.

**Why:**

The user-visible launcher flow requires fresh build evidence and catalog/documentation synchronization.

**Verification:**

- Standard debug build returns exit 0. (PASS)

**Status:** `[x]` done
