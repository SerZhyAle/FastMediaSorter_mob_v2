# Phase 05 - Overflow menu entries

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03, Phase 04
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Add the three quick-capture entries to the main-screen overflow menu, each shown only when its settings toggle is on AND its media capability is supported, and keep the menu button hidden when the total enabled-entry count is zero.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (settings fields).
- [ ] Phase 03 ✅ Done (`MainVoiceCaptureManager`).
- [ ] Phase 04 ✅ Done (`MainCameraCaptureManager`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainQuickCaptureMenuManager.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1210 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +3 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +3 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +3 keys |

> `MainActivity.kt` is >500 LOC - create a timestamped backup in `temp/` before editing.
> Menu is a `PopupMenu` (no layout XML) - no landscape-variant file applies.

---

## Steps

### Step 5.1 - Quick-capture menu helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainQuickCaptureMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MainQuickCaptureMenuManager` mirroring `MainMiniGameMenuManager`, handling all three entries. Expose `itemCount(voice: Boolean, video: Boolean, photo: Boolean): Int` (count of true flags), `populate(popup, voice, video, photo, startOrder): Int` (adds `quick_voice_menu_label`, `quick_video_menu_label`, `quick_photo_menu_label` with their icons for each enabled flag), and `handleMenuItem(itemId): Boolean` returning the matched entry to the caller via a small callback set passed in the constructor (`onVoice`, `onVideo`, `onPhoto`). Use menu item ids that do not collide with the existing `MENU_ITEM_CALCULATOR=1`, mini-game `2`, `MENU_ITEM_CAMERA_OCR=9` - use `10` (voice), `11` (video), `12` (photo).

**Verification:**

- `Glob` - `MainQuickCaptureMenuManager.kt` exists.
- `Grep` - `class MainQuickCaptureMenuManager` matches once.
- `Grep` - `fun itemCount(` and `fun populate(` and `fun handleMenuItem(` all present.
- `Grep` - ids `10`, `11`, `12` declared; `Grep` confirms none equal `1`, `2`, or `9`.

**Status:** `[x]` done

---

### Step 5.2 - Wire MainActivity (DI, managers, count, populate, dispatch, lifecycle)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> Add `@Inject lateinit var mediaCapabilities: MediaCapabilities`, plus injected `CameraCaptureSaver`, `LocalDestinationClassifier`, `LocalDestinationWriter`, `StatsSink`. In `setupViews()` construct `MainVoiceCaptureManager`, `MainCameraCaptureManager`, and `MainQuickCaptureMenuManager` (wiring `onVoice -> voiceManager.start()`, `onVideo -> cameraManager.captureVideo()`, `onPhoto -> cameraManager.capturePhoto()`). Add `isQuickVoiceEnabled/isQuickVideoEnabled/isQuickPhotoEnabled` fields. Extend `getMainWindowDropdownMenuItemCount()` to add each entry only when its flag AND its capability hold (voice: `isQuickVoiceEnabled && mediaCapabilities.supportsMicRecording`; video: `&& supportsVideo`; photo: `&& supportsImages`). Add the populate call in `populateMainWindowDropdownMenu` and the dispatch via `quickCaptureMenuManager.handleMenuItem(item.itemId)` in `showMainWindowDropdownMenu`. In the `settingsRepository.getSettings()` collector, update the three flags and include their change in the `refreshMainWindowDropdownMenuVisibility()` trigger. Call `voiceManager.release()` from `onPause`. Keep all logic in helpers - no capture/business logic inline in the Activity (CLAUDE.md Rule 3).

**Verification:**

- `Grep` - `lateinit var mediaCapabilities: MediaCapabilities` present in `MainActivity.kt`.
- `Grep` - `mediaCapabilities.supportsMicRecording`, `mediaCapabilities.supportsVideo`, `mediaCapabilities.supportsImages` all present.
- `Grep` - `MainQuickCaptureMenuManager(` constructed.
- `Grep` - `quickCaptureMenuManager.handleMenuItem(` present in `showMainWindowDropdownMenu`.
- `Grep` - `isQuickVoiceEnabled = settings.quickVoiceMenuEnabled` present in the settings collector.
- `Grep` - `voiceManager.release()` referenced in `onPause`.

**Status:** `[x]` done

---

### Step 5.3 - Menu label strings + icons

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 5.1

**Prompt for developer:**

> Add the three menu labels across all locales in lockstep: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key quick_voice_menu_label -En "Voice recording" -Ru "Запись с диктофона" -Uk "Запис із диктофона"`; `quick_video_menu_label` (En "Record video", Ru "Запись видео", Uk "Запис відео"); `quick_photo_menu_label` (En "Take photo", Ru "Сделать фото", Uk "Зробити фото"). For icons, grep existing capture drawables (e.g. `ic_mic`, `ic_videocam`, `ic_camera`/`ic_photo_camera`) and reuse; add a vector drawable only for a missing one. Strings must pass `docs/COMMUNICATION_POLICY.md` §2/§6.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "quick_voice_menu"` exits 0; same for `quick_video_menu` and `quick_photo_menu`.
- `Grep` - each `R.string.quick_*_menu_label` referenced in `MainQuickCaptureMenuManager.kt`.
- `Glob` - each referenced `R.drawable.ic_*` icon file exists.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] With all three toggles off, `getMainWindowDropdownMenuItemCount()` is unaffected by the new entries (count contribution is zero).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The three entries appear/hide by flag AND capability; the menu button hides at zero total entries via the existing `refreshMainWindowDropdownMenuVisibility()`. Phase 06 adds the settings toggles that flip the flags.

---

## Step Log

- 2026-06-19 - Step 5.1 Verification PASS. `MainQuickCaptureMenuManager.kt` New: itemCount/populate/handleMenuItem, ids 10/11/12 (clear of 1/2/9), icons ic_microphone/ic_video/ic_camera_capture.
- 2026-06-19 - Step 5.2 Verification PASS. `MainActivity.kt`: injected MediaCapabilities/CameraCaptureSaver/classifier/writer/StatsSink; 3 managers constructed in setupViews; count+populate gated by flag AND capability; dispatch chained; settings observer + onPause release. Integration compile `a.ps1 fk` PASS.
- 2026-06-19 - Step 5.3 Verification PASS. Menu strings quick_{voice,video,photo}_menu_label added EN/RU/UK (parity OK); reused existing drawables.

---

## Rollback Plan

Revert phase commit(s); delete `MainQuickCaptureMenuManager.kt`. The capture engines (Phase 03/04) remain dormant without menu callers.
