# Phase 04 — Settings UI

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add the "Microphone Recording" section to `fragment_settings_audio.xml` (portrait + landscape) and wire both switches in `AudioSettingsFragment`, including `RECORD_AUDIO` permission handling when the master toggle is enabled.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`micRecordingEnabled` / `micRecordingAskFilename` in `AppSettings`).
- [ ] Phase 03 is ✅ Done (string resources available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_audio.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt` | Modified | ≤ 500 |

---

## Steps

### Step 4.1 — Add microphone section to portrait audio settings layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_audio.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `fragment_settings_audio.xml`, append a new section **before** the closing `</LinearLayout>` of the root, after the `btnSetDefaultAudioPlayer` button.
>
> Use the canonical **Pattern A** switch row from `docs/ARCHITECTURE.md`:
> ```xml
> <!-- S0100 Microphone Recording section -->
> <TextView
>     android:id="@+id/tvMicRecordingSectionTitle"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:layout_marginTop="@dimen/section_margin_top"
>     android:text="@string/settings_mic_recording_section_title"
>     style="@style/SettingsSectionTitle" />
>
> <!-- Master toggle row -->
> <LinearLayout
>     android:id="@+id/layoutMicRecordingEnable"
>     android:orientation="horizontal"
>     android:gravity="center_vertical"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:minHeight="@dimen/button_height">
>     <com.google.android.material.switchmaterial.SwitchMaterial
>         android:id="@+id/switchMicRecordingEnabled"
>         android:layout_width="wrap_content"
>         android:layout_height="wrap_content"
>         android:layout_marginEnd="@dimen/settings_switch_margin_end" />
>     <LinearLayout
>         android:layout_width="0dp"
>         android:layout_weight="1"
>         android:orientation="vertical">
>         <TextView
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:text="@string/settings_mic_recording_enable_title"
>             android:textSize="@dimen/toggler_title_text_size" />
>         <TextView
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:text="@string/settings_mic_recording_enable_desc"
>             android:textSize="@dimen/toggler_desc_text_size"
>             android:textColor="@color/text_color_secondary" />
>     </LinearLayout>
> </LinearLayout>
>
> <!-- Ask filename sub-row — hidden when master toggle is off -->
> <LinearLayout
>     android:id="@+id/layoutMicRecordingAskFilename"
>     android:orientation="horizontal"
>     android:gravity="center_vertical"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:minHeight="@dimen/button_height"
>     android:visibility="gone">
>     <com.google.android.material.switchmaterial.SwitchMaterial
>         android:id="@+id/switchMicRecordingAskFilename"
>         android:layout_width="wrap_content"
>         android:layout_height="wrap_content"
>         android:layout_marginEnd="@dimen/settings_switch_margin_end" />
>     <LinearLayout
>         android:layout_width="0dp"
>         android:layout_weight="1"
>         android:orientation="vertical">
>         <TextView
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:text="@string/settings_mic_recording_ask_filename_title"
>             android:textSize="@dimen/toggler_title_text_size" />
>         <TextView
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:text="@string/settings_mic_recording_ask_filename_desc"
>             android:textSize="@dimen/toggler_desc_text_size"
>             android:textColor="@color/text_color_secondary" />
>     </LinearLayout>
> </LinearLayout>
> ```
> If `SettingsSectionTitle` style does not exist, check adjacent section titles in the file and reuse the same style/textAppearance.

**Verification:**

- `Grep` — `switchMicRecordingEnabled` present in `layout/fragment_settings_audio.xml`.
- `Grep` — `switchMicRecordingAskFilename` present in `layout/fragment_settings_audio.xml`.
- `Grep` — `layoutMicRecordingAskFilename` present with `android:visibility="gone"`.

**Status:** `[ ]` not done

---

### Step 4.2 — Apply identical mic recording section to landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_audio.xml`
**Depends on:** Step 4.1

**Prompt for developer:**

> Apply the exact same XML additions from Step 4.1 to `layout-land/fragment_settings_audio.xml` in the same position (before the closing root `</LinearLayout>`, after `btnSetDefaultAudioPlayer`). View IDs must match exactly — `switchMicRecordingEnabled`, `layoutMicRecordingAskFilename`, `switchMicRecordingAskFilename`.

**Verification:**

- `Grep` — `switchMicRecordingEnabled` present in `layout-land/fragment_settings_audio.xml`.
- `Grep` — `switchMicRecordingAskFilename` present in `layout-land/fragment_settings_audio.xml`.

**Status:** `[ ]` not done

---

### Step 4.3 — Wire switches and RECORD_AUDIO permission in AudioSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
**Depends on:** Step 4.1, Step 4.2

**Prompt for developer:**

> In `AudioSettingsFragment`:
>
> 1. Register a `registerForActivityResult(ActivityResultContracts.RequestPermission())` launcher for `RECORD_AUDIO`. In the callback: if granted → call `viewModel.updateSettings(current.copy(micRecordingEnabled = true))`; if denied → revert `binding.switchMicRecordingEnabled.isChecked = false` and show a `Snackbar` with `R.string.mic_recording_permission_denied`.
>
> 2. In `setupViews()`, add a `setOnCheckedChangeListener` for `switchMicRecordingEnabled`:
>    - If checked && `ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PERMISSION_GRANTED` → launch the permission request launcher; do NOT update settings yet (the callback does it after grant).
>    - If checked && permission already granted → `viewModel.updateSettings(current.copy(micRecordingEnabled = true))`.
>    - If unchecked → `viewModel.updateSettings(current.copy(micRecordingEnabled = false))`.
>    - Also toggle `binding.layoutMicRecordingAskFilename.isVisible = isChecked`.
>
> 3. Add a `setOnCheckedChangeListener` for `switchMicRecordingAskFilename`:
>    - On change → `viewModel.updateSettings(current.copy(micRecordingAskFilename = isChecked))`.
>
> 4. In `observeData()` (where settings are observed), update the switches from `settings.micRecordingEnabled` and `settings.micRecordingAskFilename` inside the `isUpdatingFromSettings = true … false` guard. Also update `binding.layoutMicRecordingAskFilename.isVisible = settings.micRecordingEnabled`.

**Verification:**

- `Grep` — `micRecordingEnabled` present in `AudioSettingsFragment.kt`.
- `Grep` — `micRecordingAskFilename` present in `AudioSettingsFragment.kt`.
- `Grep` — `RECORD_AUDIO` present (permission check).
- `Grep` — `Log\.d\(` returns zero hits in `AudioSettingsFragment.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for all three files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Settings UI is complete and persists `micRecordingEnabled` / `micRecordingAskFilename` via `SettingsViewModel`. `BrowseActivity` can now observe these to show/hide the mic button (Phase 05).

---

## Rollback Plan

Revert phase commit(s) — layout changes are additive, fragment changes are limited to `setupViews` and `observeData` blocks. No data migration involved.
