# Phase 03 - Enabled Tools Shortcut Addition

**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2

## Objective

Ensure that enabling a program/tool (such as calculator, app launch panel, mini games) in settings automatically creates a desktop shortcut if one does not already exist.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SyncEnabledToolShortcutsUseCase.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1000 |

## Steps

### Step 03.1 - Create SyncEnabledToolShortcutsUseCase

**Files:** `SyncEnabledToolShortcutsUseCase.kt`

**Prompt for developer:**

> Create a use case that checks if enabled tools (calculator, programs panel, games) have corresponding shortcuts on the launcher desktop across orientations, and appends them if missing.

**Verification:**

- Enabling a tool adds the shortcut without displacing existing cells.

**Status:** `[x]` done

### Step 03.2 - Wire tool sync into settings updates

**Files:** `SettingsRepositoryImpl.kt` (or relevant settings manager/viewmodel)

**Prompt for developer:**

> Call `syncEnabledToolShortcuts` when tool settings are updated.

**Verification:**

- Changing settings adds shortcuts.

**Status:** `[x]` done
