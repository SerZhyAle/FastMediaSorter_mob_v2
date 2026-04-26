# Phase 05 — Settings UI

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-04-25
**Completed:** 2026-04-25
**Depends on:** Phase 01
**Blocks:** Phase 06

---

## Objective

Add two toggle switches to the Behaviour section of `PlaybackSettingsFragment` (where
`allowRename` / `allowDelete` live), wire observers and ViewModel calls, and index both
settings in `SettingsSearchIndex`.

---

## Files Touched

| File | New/Mod | Budget |
| ---- | :-----: | -----: |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Mod | — |
| `ui/settings/fragments/PlaybackSettingsFragment.kt` | Mod | ≤ 800 |
| `ui/settings/SettingsSearchIndex.kt` | Mod | ≤ 500 |

---

## Steps

### Step 05.1 — Add two switch rows to fragment_settings_playback.xml

**Status:** `[x] done`
**File:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** Phase 01

Locate `switchAllowDelete` row. After it, insert:

**Row 1 — Disable camera capture:**

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="@dimen/settings_item_padding">
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/setting_disable_camera_capture"
            style="@style/SettingsItemTitle" />
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/setting_disable_camera_capture_summary"
            style="@style/SettingsItemSummary" />
    </LinearLayout>
    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchDisableCameraCapture"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/settings_switch_margin_end"
        android:contentDescription="@string/setting_disable_camera_capture" />
</LinearLayout>
```

**Row 2 — Skip filename dialog (subordinate; hidden via observer when disableCameraCapture=true):**

```xml
<LinearLayout
    android:id="@+id/rowSkipCameraFilename"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="@dimen/settings_item_padding">
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/setting_skip_camera_filename_dialog"
            style="@style/SettingsItemTitle" />
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/setting_skip_camera_filename_dialog_summary"
            style="@style/SettingsItemSummary" />
    </LinearLayout>
    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchSkipCameraFilenameDialog"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/settings_switch_margin_end"
        android:contentDescription="@string/setting_skip_camera_filename_dialog" />
</LinearLayout>
```

**Verification:** `Grep "switchDisableCameraCapture" app_v2/src/main/res/layout/fragment_settings_playback.xml` → 1 hit.

---

### Step 05.2 — Wire listeners in PlaybackSettingsFragment

**Status:** `[x] done`
**File:** `ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 05.1

In the settings-view setup method (same place `switchAllowRename` / `switchAllowDelete` listeners
are added):

```kotlin
binding.switchDisableCameraCapture.setOnCheckedChangeListener { _, isChecked ->
    viewModel.updateSettings(viewModel.settings.value.copy(disableCameraCapture = isChecked))
}
binding.switchSkipCameraFilenameDialog.setOnCheckedChangeListener { _, isChecked ->
    viewModel.updateSettings(viewModel.settings.value.copy(skipCameraFilenameDialog = isChecked))
}
```

In the settings-observer method (where `switchAllowRename.isChecked = settings.allowRename`
is set):

```kotlin
if (binding.switchDisableCameraCapture.isChecked != settings.disableCameraCapture)
    binding.switchDisableCameraCapture.isChecked = settings.disableCameraCapture
if (binding.switchSkipCameraFilenameDialog.isChecked != settings.skipCameraFilenameDialog)
    binding.switchSkipCameraFilenameDialog.isChecked = settings.skipCameraFilenameDialog
// Subordination: row 2 only visible when camera capture is not disabled
binding.rowSkipCameraFilename.isVisible = !settings.disableCameraCapture
```

**Verification:**

- `Grep "switchDisableCameraCapture" ui/settings/fragments/PlaybackSettingsFragment.kt` → ≥ 2 hits
- `Grep "rowSkipCameraFilename" ui/settings/fragments/PlaybackSettingsFragment.kt` → ≥ 1 hit

---

### Step 05.3 — Add to SettingsSearchIndex

**Status:** `[x] done`
**File:** `ui/settings/SettingsSearchIndex.kt`
**Depends on:** Steps 05.1, 05.2

Confirm the Playback tab index by checking the existing `allowRename` entry. Then add two
entries following the same pattern:

```kotlin
SettingsSearchEntry(
    titleResId = R.string.setting_disable_camera_capture,
    summaryResId = R.string.setting_disable_camera_capture_summary,
    tabIndex = <PLAYBACK_TAB_INDEX>,
    sectionScrollKey = "camera_capture"
),
SettingsSearchEntry(
    titleResId = R.string.setting_skip_camera_filename_dialog,
    summaryResId = R.string.setting_skip_camera_filename_dialog_summary,
    tabIndex = <PLAYBACK_TAB_INDEX>,
    sectionScrollKey = "camera_capture"
),
```

**Verification:** `Grep "setting_disable_camera_capture" ui/settings/SettingsSearchIndex.kt` → 1 hit.

---

## Phase Done Criteria

- [x] `Grep "switchDisableCameraCapture" app_v2/src/main/res/layout/fragment_settings_playback.xml` → 1 hit
- [x] `Grep "switchSkipCameraFilenameDialog" app_v2/src/main/res/layout/fragment_settings_playback.xml` → 1 hit
- [x] `Grep "switchDisableCameraCapture" ui/settings/fragments/PlaybackSettingsFragment.kt` → ≥ 2 hits (3)
- [x] `Grep "rowSkipCameraFilename" ui/settings/fragments/PlaybackSettingsFragment.kt` → ≥ 1 hit (1)
- [x] `Grep "setting_disable_camera_capture" ui/settings/SettingsSearchIndex.kt` → 1 hit

**Phase Step Log:**

- 2026-04-25 — Steps 05.1-05.3 done. XML: 2 switch rows after containerDeleteConfirm; Fragment: listeners + observer + row visibility; SettingsSearchIndex: 2 PLAYBACK entries using key-based pattern (actual class uses String title, not @StringRes). All Phase Done Criteria PASS.
