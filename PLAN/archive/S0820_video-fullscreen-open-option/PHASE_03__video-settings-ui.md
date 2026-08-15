# Phase 03 - Video Settings UI

**Strategic spec:** [`../S0820_video-fullscreen-open-option.md`](../S0820_video-fullscreen-open-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none (Phase 05 waits on all)
**Steps done:** 4 / 4
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Surface the toggle "Open video files in fullscreen mode" in the Video settings screen, portrait and landscape, trilingual, wired to `AppSettings.openVideoInFullscreen`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `AppSettings.openVideoInFullscreen` exists.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | - |
| `app_v2/src/main/res/layout/fragment_settings_video.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_video.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` | Modified | ≤ 245 (currently ~236) |

> **Flavor placement.** Not applicable - `VideoSettingsFragment.kt` and its layouts are cross-flavor `src/main/` files; no `BuildConfig` gating is introduced.

---

## Steps

### Step 03.1 - Add trilingual strings

**Files:** `app_v2/src/main/res/values{,-ru,-uk}/strings_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys in lockstep across EN/RU/UK using `scripts/utils/set-android-string.ps1 -Action add -Module app_v2 -File strings_settings.xml`:
>
> - `setting_open_video_in_fullscreen_title` - EN "Open video files in fullscreen mode", RU "Открывать видеофайлы в полноэкранном режиме", UK "Відкривати відеофайли в повноекранному режимі".
> - `setting_open_video_in_fullscreen_desc` - EN "Videos opened from Browse launch straight into fullscreen playback", RU "Видео, открытые из браузера ресурсов, запускаются сразу в полноэкранном режиме", UK "Відео, відкриті з браузера ресурсів, запускаються одразу в повноекранному режимі".
>
> Per project convention, do not pass Cyrillic text as an inline bash-quoted CLI argument to `pwsh` - author a small one-off `.ps1` file with both `-Action add` calls and run it via `pwsh -NoProfile -File`.

**Verification:**

- `Grep` - `setting_open_video_in_fullscreen_title` present in `values/strings_settings.xml`, `values-ru/strings_settings.xml`, `values-uk/strings_settings.xml`.
- `Grep` - `setting_open_video_in_fullscreen_desc` present in the same three files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_open_video_in_fullscreen"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 3/3 PASS (Grep EN/RU/UK all present, check_strings_localized.ps1 OK 2/2). Files: values/strings_settings.xml, values-ru/strings_settings.xml, values-uk/strings_settings.xml.

---

### Step 03.2 - Add the toggle row to the portrait layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_video.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Insert a new `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` with `android:id="@+id/rowOpenVideoInFullscreen"` immediately after the `rowShowVideoThumbnails` row and before the "Video size limit" `LinearLayout` block. Match `rowShowVideoThumbnails`'s `android:layout_marginStart`/`android:layout_marginBottom` values. Attributes: `app:str_title="@string/setting_open_video_in_fullscreen_title"`, `app:str_subtitle="@string/setting_open_video_in_fullscreen_desc"`. No `app:str_checked` attribute - the row's checked state is driven from `observeData()` in Step 03.4, and the Kotlin-side default (`true`) already matches the desired unchecked-baseline behavior before first observe.

**Verification:**

- `Grep` - `rowOpenVideoInFullscreen` in `res/layout/fragment_settings_video.xml` matches once.
- `Grep` - `str_title="@string/setting_open_video_in_fullscreen_title"` in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 2/2 PASS. Files: res/layout/fragment_settings_video.xml (+8 LOC).

---

### Step 03.3 - Add the toggle row to the landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_video.xml`

**Depends on:** Step 03.1

**Prompt for developer:**

> Add the same `SettingsToggleRow` (`android:id="@+id/rowOpenVideoInFullscreen"`, same `app:str_title`/`app:str_subtitle` as Step 03.2) as a new standalone full-width row placed directly after the existing "Support video + Show thumbnails" paired-row `LinearLayout` block's closing tag, and before the "Video size limit" block. Do not disturb the existing 2-up pairings (`rowSupportVideos`/`rowShowVideoThumbnails`, `rowVideoFrameCopyToClipboard`/`rowPlayerShowFps`) - this is a third, unpaired row, matching the `layout_width="match_parent"` single-row style already used for standalone rows in this layout family.

**Verification:**

- `Grep` - `rowOpenVideoInFullscreen` in `res/layout-land/fragment_settings_video.xml` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 1/1 PASS. Files: res/layout-land/fragment_settings_video.xml (+8 LOC).

---

### Step 03.4 - Wire the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt`
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> In `setupViews()`, add a `bindSwitch(binding.rowOpenVideoInFullscreen) { isChecked -> val current = viewModel.settings.value; viewModel.updateSettings(current.copy(openVideoInFullscreen = isChecked)) }` block next to the `rowShowVideoThumbnails` binding, following the same `val current = viewModel.settings.value` pattern used there. In `observeData()`, add `setSwitchChecked(binding.rowOpenVideoInFullscreen, settings.openVideoInFullscreen)` next to the `rowShowVideoThumbnails` line.

**Verification:**

- `Grep` - `bindSwitch(binding.rowOpenVideoInFullscreen)` in `VideoSettingsFragment.kt` matches once.
- `Grep` - `setSwitchChecked(binding.rowOpenVideoInFullscreen, settings.openVideoInFullscreen)` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 2/2 PASS. Files: VideoSettingsFragment.kt (+6 LOC).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` -> BUILD SUCCESSFUL (34s), confirms generated binding field `rowOpenVideoInFullscreen` resolves.
- [x] `Grep` for `Log\.d\(` in `VideoSettingsFragment.kt` returns zero hits.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (batched, see below).

---

## Handoff Notes to Next Phase

The toggle is visible in both orientations, persists via Phase 01's plumbing, and has no effect on player launch behavior yet - Phase 04 wires the actual gate.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - one new layout row per orientation, two new string keys, one fragment wiring block. No data migration, no other phase's files touched.
