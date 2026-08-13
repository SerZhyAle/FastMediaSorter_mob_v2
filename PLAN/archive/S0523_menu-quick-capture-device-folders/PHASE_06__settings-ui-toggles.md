# Phase 06 - Settings UI toggles

> **SUPERSEDED 2026-06-19 (owner feedback).** The 3 new toggles built here were redundant with the existing capture toggles (mic recording / video capture / photo capture). They were fully removed - settings fields, DataStore keys, CSV rows, both layout orientations, fragment wiring, and the 6 strings. The main-menu quick-capture entries are now gated directly by the existing toggles (`micRecordingEnabled` / `!disableVideoCapture` / `!disableCameraCapture`). Net effect of this phase on the final codebase: none.

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (superseded - see note above)
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Add three capability-gated toggle rows to the operations settings screen so the user enables each quick-capture entry independently. A toggle is hidden when its media capability is unsupported on the flavor.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (settings fields persist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | +3 rows |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | +3 rows |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1080 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +6 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +6 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +6 keys |

> `OperationsSettingsFragment.kt` is >500 LOC - create a timestamped backup in `temp/` before editing.
> Landscape parity is MANDATORY: edit both `layout/` and `layout-land/` copies of `fragment_settings_destinations.xml` (both exist).

---

## Steps

### Step 6.1 - Add three toggle rows to both layout orientations

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In both orientation copies, add three `SettingsToggleRow` views with ids `rowQuickVoiceMenu`, `rowQuickVideoMenu`, `rowQuickPhotoMenu` next to the existing `rowEmbeddedGame` / `rowCameraOcrTranslationEnabled` in the "other features" group. Reuse `?attr`/`@color`/`@string` references only - no hardcoded hex. Title/summary point to the Step 6.3 string keys. Keep the two files structurally identical (same ids, same order).

**Verification:**

- `Grep` - `rowQuickVoiceMenu`, `rowQuickVideoMenu`, `rowQuickPhotoMenu` each present in `layout/fragment_settings_destinations.xml`.
- `Grep` - the same three ids present in `layout-land/fragment_settings_destinations.xml`.
- `Grep` - no new `="#` hardcoded color in the added rows.

**Status:** `[x]` done

---

### Step 6.2 - Wire the toggles with capability gating

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 6.1, Phase 01

**Prompt for developer:**

> Mirror the existing `rowEnableCalculator` wiring (plain flag flip - no permission request here). Gate visibility: hide `rowQuickVoiceMenu` when `!mediaCapabilities.supportsMicRecording`, `rowQuickVideoMenu` when `!mediaCapabilities.supportsVideo`, `rowQuickPhotoMenu` when `!mediaCapabilities.supportsImages`. For each visible row set an `setOnCheckedChangeListener` (respecting `isUpdatingFromSettings`) that calls `viewModel.updateSettings(current.copy(quick*MenuEnabled = isChecked))`. In the settings sync block add `setCheckedSilently` for each row (guarded by the same capability check). Do NOT request `RECORD_AUDIO` here - `MainVoiceCaptureManager.start()` requests it lazily at capture time (Phase 03), and the existing `recordAudioPermissionLauncher` is bound to `micRecordingEnabled` and must not be repurposed.

**Verification:**

- `Grep` - `mediaCapabilities.supportsVideo` and `mediaCapabilities.supportsImages` referenced in `OperationsSettingsFragment.kt`.
- `Grep` - `quickVoiceMenuEnabled = isChecked`, `quickVideoMenuEnabled = isChecked`, `quickPhotoMenuEnabled = isChecked` present.
- `Grep` - `binding.rowQuickVoiceMenu.setCheckedSilently(` present in the sync block.
- `Grep` - the voice toggle listener does NOT reference `recordAudioPermissionLauncher` (permission stays in the capture path).

**Status:** `[x]` done

---

### Step 6.3 - Toggle title/summary strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add six keys across all locales in lockstep with `scripts/utils/set-android-string.ps1 -Action add`: `setting_quick_voice_menu_title`/`_summary`, `setting_quick_video_menu_title`/`_summary`, `setting_quick_photo_menu_title`/`_summary`. Titles match the menu labels; summaries state the capture lands in the phone's standard folder (recordings / video / photos) from the main menu. Strings must pass `docs/COMMUNICATION_POLICY.md` §2/§6.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_quick_voice_menu"` exits 0; same for video and photo prefixes.
- `Grep` - each `setting_quick_*_menu_title` referenced in `fragment_settings_destinations.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 6.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Build `assemblePhotosDebug` and `assembleLiteDebug` succeed (capability-hidden rows do not break flavor builds).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Users can enable each quick-capture entry; capability-unsupported toggles are hidden. Phase 07 finalizes docs/catalog/features.

---

## Step Log

- 2026-06-19 - Step 6.1 Verification PASS. 3 SettingsToggleRow rows (rowQuickVoiceMenu/Video/Photo) added to layout/ + layout-land/ fragment_settings_destinations.xml; no hardcoded hex.
- 2026-06-19 - Step 6.2 Verification PASS. OperationsSettingsFragment: capability-gated visibility + listeners + sync block (setCheckedSilently). Permission stays in capture path (not repurposing recordAudioPermissionLauncher).
- 2026-06-19 - Step 6.3 Verification PASS. 6 title/summary strings added EN/RU/UK (parity OK).
- 2026-06-19 - Build: standard `a.ps1 fc` PASS; photos+lite compile PASS on clean re-run (first failure was a stale incremental cache, no source change between runs).

---

## Rollback Plan

Revert phase commit(s) - layout rows + fragment wiring + strings only; settings fields default off so the absence of toggles is invisible.
