# Phase 05 - "Camera-to-Resource" settings section (next to the dictaphone)

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Add a "Camera-to-Resource" settings section in `AudioSettingsFragment`, next to the dictaphone rows, with three `SettingsToggleRow`s (enable, ask filename, open for editing) and a short note that capture requires the CAMERA permission. Mirror portrait + landscape layouts.

---

## Prerequisites

- [ ] Phase 04 ✅ Done (`cameraCaptureOpenForEditing` exists).
- [ ] `/ui-clarify` confirms placement and row order (strategic §3.3 UI placement contract).
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_audio.xml` | Modified | ≤ +60 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ +60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt` | Modified | ≤ +60 |
| `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`) | Modified | ≤ +10 each |

> Landscape parity (Strict Rule 12): `fragment_settings_audio.xml` has a `layout-land` counterpart - both edited in this phase with matching ids.

---

## Steps

### Step 05.1 - Add section strings (EN/RU/UK lockstep)

**Files:** `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`)
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `set-android-string.ps1 -Action add` (EN/RU/UK parity): `setting_camera_to_resource_section_title`, `setting_camera_to_resource_enabled_title` + `_desc`, `setting_camera_ask_filename_title` + `_desc`, `setting_camera_open_for_editing_title` + `_desc`, and `setting_camera_permission_note` (explains capture needs the camera permission). Author Style + COMMUNICATION_POLICY §2/§6.

**Verification:**

- `Grep` - `setting_camera_to_resource_section_title` in all three `strings.xml` (3 hits).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_camera_"` exit 0. expected: exit 0 | actual: <fill>.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 05.2 - Portrait layout rows

**Files:** `app_v2/src/main/res/layout/fragment_settings_audio.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> After the dictaphone rows (`rowMicRecordingEnabled` ~line 226), add a section header + three `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` rows: `rowCameraToResourceEnabled`, `rowCameraAskFilename`, `rowCameraOpenForEditing`, plus a note `TextView` bound to `setting_camera_permission_note`. Use the same widget, spacing, and focus order as the mic rows. Ensure focusable + logical focus chain (Rule 17).

**Verification:**

- `Grep` - `rowCameraToResourceEnabled`, `rowCameraAskFilename`, `rowCameraOpenForEditing` each present once in `layout/fragment_settings_audio.xml`.
- `Grep` - `SettingsToggleRow` count increased by 3.

**Status:** `[x] done`

---

### Step 05.3 - Landscape layout rows (parity)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_audio.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Mirror the three rows + section header + note into the landscape layout with identical ids and order. Strict Rule 12.

**Verification:**

- `Grep` - `rowCameraToResourceEnabled`, `rowCameraAskFilename`, `rowCameraOpenForEditing` each present once in `layout-land/fragment_settings_audio.xml`.
- Id parity portrait vs landscape (manual check; record expected ids | actual ids).

**Status:** `[x] done`

---

### Step 05.4 - Bind rows in AudioSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Bind the three rows to settings: `rowCameraToResourceEnabled` checked = `!settings.disableCameraCapture`, writes `disableCameraCapture = !isChecked`; `rowCameraAskFilename` checked = `!settings.skipCameraFilenameDialog`, writes `skipCameraFilenameDialog = !isChecked`; `rowCameraOpenForEditing` checked = `settings.cameraCaptureOpenForEditing`, writes that flag. Follow the mic rows' binding pattern (observe settings flow, persist via SettingsViewModel/SettingsRepository). WHY-comment on the two inverted bindings (reuse existing negative flags - no duplicate keys, S0359).

**Verification:**

- `Grep` - `disableCameraCapture` and `skipCameraFilenameDialog` and `cameraCaptureOpenForEditing` all referenced in `AudioSettingsFragment.kt`.
- `Grep -n "Log\.d\("` returns zero hits.
- `/build` standardDebug compiles.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Landscape + portrait row id parity confirmed.
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The three toggles persist. Phase 06 consumes `cameraCaptureOpenForEditing` (and the reused flags) in the Browse capture flow.

---

## Rollback Plan

Revert phase commit(s) - layout + binding only; the reused flags retain their prior single source of truth.
