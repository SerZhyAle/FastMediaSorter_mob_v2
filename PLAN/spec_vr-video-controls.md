# Specification: VR Video Controls — Enhanced Playback Dialog

**Status:** Draft  
**Date:** 2026-04-17  
**Tier:** 1 — Medium (UI improvement, fixes existing bugs)  
**Scope:** `PlayerActivity`, player control UI, video playback settings dialog. Kotlin code + layout XML. Affects `standard`, `photos`, `legacy` flavors.

---

## 1. Problem Statement

The current video player in fullscreen mode has scattered playback controls:

- Audio/subtitle selection is hidden in an old edit dialog that doesn't show the current selection.
- Subtitle selection in that dialog is broken (doesn't work at all).
- Volume, brightness, and HUE adjustments require opening additional dialogs or aren't available at all.
- Speed adjustment is buried in the existing controls and hard to find.
- In VR helmet usage, all these controls are cumbersome to access with a pointer/joystick.

Users in VR mode need a unified, touch-friendly dialog with logical tabs that allows quick adjustment of all playback parameters without exiting fullscreen or using multiple dialogues.

---

## 2. Goals

1. **Create a new unified playback settings dialog** with multiple tabs, accessible from a new button in the player controls (positioned before PiP button).
2. **Migrate existing controls** (audio selection, subtitle selection, speed selection) into the new dialog as tabs.
3. **Add new adjustment controls** (volume, brightness, HUE) as tabs in the dialog.
4. **Fix audio selection UX**: display currently active audio track clearly; update UI when selection changes.
5. **Fix subtitle selection**: make it fully functional (currently broken); display active subtitle.
6. **Ensure VR-friendly UX**: large touch targets, minimal nested menus, quick confirmation.

**Non-goals for this spec:**

- No changes to Player core logic (ExoPlayer, Media3).
- No new player features beyond unified controls dialog.
- No offline subtitle editing or format conversion.
- 3D video support deferred to separate Research spec (`spec_vr-video-3d.md`).

---

## 3. Requirements

### 3.1 New Dialog Structure

**Dialog name**: `PlaybackSettingsDialog` (Kotlin class extending `DialogFragment`)

**Tabs** (in order):

1. **Audio** — select active audio track; show current selection with icon/highlight
2. **Subtitles** — select active subtitle; show "None", "Auto-generated list", or current file name
3. **Speed** — slider/buttons for playback speed (0.25x to 2.0x, with presets: 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
4. **Volume** — slider for device audio volume (0–100%)
5. **Brightness** — slider for screen brightness (0–100%; affects system brightness or app-level override)
6. **HUE** — slider for colour hue adjustment (0–360°; or simplified: Cool, Neutral, Warm presets)

### 3.2 Dialog Behaviour

- **Trigger**: New button in player control bar (top-right area, before PiP icon)
- **Icon**: Settings gear icon 🔧 (or similar)
- **Size**: Full-width dialog in landscape; centred dialogue in portrait
- **Dismissal**: Tapping outside the dialog or pressing Back closes it without applying (settings apply immediately on change)
- **Persistence**: Remembers user's last tab opened (store in SharedPreferences or ViewModel state)
- **Orientation**: Dialog remains visible and functional during device rotation (handle ViewModel lifecycle correctly)

### 3.3 Audio Tab

**Current state:**

- Existing dialog shows audio tracks but doesn't visually indicate which is active.
- Selection sometimes works, but the UI feedback is unclear.

**New behaviour:**

- List of available audio tracks with radio-button or highlight effect
- Visually selected (highlighted) track shows as currently playing
- Tapping a track changes it immediately + closes the list item detail view (if any)
- If only one audio track, show "Mono" or track name with checkmark
- Layout: vertical list or horizontal carousel depending on device width

**Crash/bug fixes:**

- Ensure no crashes when switching tracks during playback
- Handle edge case: no audio tracks available → show "No audio" message
- Handle edge case: switching tracks very rapidly → debounce or queue changes

### 3.4 Subtitles Tab

**Current state:**

- Existing subtitle selection in edit dialog is broken (doesn't apply).
- No visual feedback of active subtitle.

**New behaviour:**

- List of available subtitles (loaded from media or external SRT/ASS files)
- First item: "None" (no subtitles)
- Remaining items: subtitle track names or "External: filename.srt"
- Visually selected (highlighted) subtitle shows as currently active
- Tapping a subtitle applies it immediately
- If subtitle file encoding is wrong, show a warning + attempt auto-detect (UTF-8, CP-1252, ISO-8859-1)

**Crash/bug fixes:**

- Test all subtitle formats: SRT, ASS/SSA, VTT, WebVTT
- Handle: subtitle file not found → fallback to "None"
- Handle: subtitle out of sync → show small "Sync offset" slider (optional Phase 2)

### 3.5 Speed Tab

**Current state:**

- Speed control exists but is buried in control bar.

**New behaviour:**

- Horizontal slider (0.25x to 2.0x) with tick marks
- Preset buttons: 0.5x | 0.75x | **1.0x** (bold/highlighted) | 1.25x | 1.5x | 1.75x | 2.0x
- Current speed displayed as number (e.g., "1.25x")
- Slider and presets apply immediately
- Pitch correction toggle: "Maintain pitch" ON/OFF (if supported by ExoPlayer)

### 3.6 Volume Tab

**Current state:**

- Device volume can be adjusted via hardware keys; no in-app slider.

**New behaviour:**

- Vertical or horizontal slider: 0–100%
- Display current volume percentage
- Slider applies immediately to device audio output
- Icon feedback: volume icon changes (mute, low, medium, loud)
- Mute button (checkbox): quick on/off toggle

**Implementation note:**

- Use `AudioManager.setStreamVolume(STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)`
- Respect "Do Not Disturb" mode (no forced unmute)

### 3.7 Brightness Tab

**Current state:**

- No in-app brightness adjustment; users rely on device settings.

**New behaviour:**

- **Option A (recommended)**: Slider applies brightness to the video view only (app-level) — safer, doesn't interfere with system settings
- **Option B (advanced)**: Slider adjusts system screen brightness (requires `WRITE_SETTINGS` permission) — more global but risky

**For Phase 1, implement Option A** (app-level video view brightness override):

- Add `ColorMatrix` filter to video surface or use `ColorOverlay` in layout
- Slider: 0–100% (50% = normal, 0% = black, 100% = full brightness)
- Apply immediately via `setVideoProcessor()` or similar Media3 API

### 3.8 HUE Tab

**Simplified implementation for Phase 1:**

- Three preset buttons: **Cool** (blue tint) | **Neutral** (no adjustment) | **Warm** (orange tint)
- Cool = ColorMatrix hue shift –30°
- Warm = ColorMatrix hue shift +30°
- Or: slider 0–360° if user wants fine control

**Implementation:**

- Use Android `ColorMatrix` with `setSaturation()` and `setScale()` for hue rotation
- Apply via video processor or as overlay filter

---

## 4. Architecture & Data Flow

```
PlayerActivity.kt
  ↓
PlayerViewModel (owns playback state)
  ↓
PlaybackSettingsDialog (reads/writes settings)
  ↓
MediaPlayerManager / UseCase
  ↓
ExoPlayer / Media3 Player
```

**ViewModel responsibilities:**

- Expose `LiveData<List<AudioTrack>>`, `LiveData<List<Subtitle>>`, `LiveData<Float> playbackSpeed`, etc.
- Listen for changes and apply to player
- Persist user's last selected tab and last values (SharedPreferences)

**Dialog responsibilities:**

- UI only — no business logic
- Read from ViewModel, observe changes
- Emit user actions back to ViewModel via callback or interface

**No changes to**:

- ExoPlayer core configuration
- Existing PlayerActivity fullscreen logic
- Media3 codec support

---

## 5. File Changes

### Create

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackSettingsDialog.kt` (~300 lines)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackSettingsManager.kt` (~200 lines) — handles state logic
- `app_v2/src/main/res/layout/dialog_playback_settings.xml` (~400 lines)
- `app_v2/src/main/res/layout/tab_audio_settings.xml` (~150 lines)
- `app_v2/src/main/res/layout/tab_subtitles_settings.xml` (~150 lines)
- `app_v2/src/main/res/layout/tab_speed_settings.xml` (~150 lines)
- `app_v2/src/main/res/layout/tab_volume_settings.xml` (~100 lines)
- `app_v2/src/main/res/layout/tab_brightness_settings.xml` (~100 lines)
- `app_v2/src/main/res/layout/tab_hue_settings.xml` (~100 lines)
- `app_v2/src/main/res/values/strings.xml` — add new string keys for UI labels (EN)
- `app_v2/src/main/res/values-ru/strings.xml` — Russian labels
- `app_v2/src/main/res/values-uk/strings.xml` — Ukrainian labels

### Modify

- `PlayerActivity.kt` — add button + launch dialog (minimal ~20 lines)
- `PlayerViewModel.kt` — expose LiveData for audio/subtitle/speed/volume/brightness/hue (minimal ~30 lines)
- `app_v2/src/main/res/layout/activity_player_unified.xml` — add new button to control bar (~5 lines)
- `app_v2/src/main/res/layout-land/activity_player_unified.xml` — same button for landscape (~5 lines)

---

## 6. Testing Plan

### Unit Tests

- `PlaybackSettingsDialogTest.kt`: verify tab switching, state persistence
- `PlaybackSettingsManagerTest.kt`: verify audio/subtitle/speed/volume changes apply correctly

### Integration Tests (Maestro E2E)

- Open fullscreen video → tap Settings button → verify all tabs visible
- Switch audio track → verify playback changes, UI updates
- Switch subtitle → verify subtitle displays or disappears
- Adjust speed, volume, brightness, HUE → verify immediate feedback
- Rotate device → verify dialog persists state and remains visible
- Crash scenario: switch audio/subtitle very rapidly → no ANR/crash

### Manual Testing (Critical)

- Test on real device in landscape mode (VR-relevant scenario)
- Test with multiple audio tracks (e.g., multi-language MKV)
- Test with SRT + embedded subtitles simultaneously
- Test with broken subtitle file → graceful fallback
- Test with single audio track → ensure UI doesn't break
- Test in low-light condition → brightness adjustment usefulness

---

## 7. Acceptance Criteria

- [x] New "Playback Settings" button visible in player controls (before PiP)
- [x] Dialog opens/closes correctly without crashes or layout shifts
- [x] All six tabs are accessible and display correct content
- [x] Audio selection shows current track, switching works, no crash
- [x] Subtitle selection works (currently broken), displays current choice
- [x] Speed adjustment applies immediately, slider + presets work
- [x] Volume slider adjusts audio output correctly
- [x] Brightness slider adjusts video appearance without affecting system settings
- [x] HUE adjustment (presets or slider) works as expected
- [x] Dialog state persists across orientation changes
- [x] Dialog settings apply immediately (no "OK" button needed)
- [x] No lint warnings, no memory leaks, proper lifecycle handling
- [x] Strings are localized (EN/RU/UK)
- [x] VR UX: touch targets ≥48dp, no tiny buttons

---

## 8. Release Notes (EN/RU/UK)

**EN:**
> New unified **Playback Settings** dialog in fullscreen mode. Adjust volume, brightness, colour, speed, and switch audio/subtitles from one convenient menu — especially useful in VR helmets.

**RU:**
> Новый единый диалог **Настройки воспроизведения** в полноэкранном режиме. Регулируйте громкость, яркость, цвет, скорость и переключайте аудиодорожки/субтитры из одного удобного меню — особенно полезно в VR-очках.

**UK:**
> Новий уніфікований діалог **Налаштування відтворення** у повноекранному режимі. Регулюйте гучність, яскравість, колір, швидкість і перемикайте аудіодоріжки/субтитри з одного зручного меню — особливо корисно в VR-очках.

---

## 9. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Subtitle file encoding issues | Medium | Test with UTF-8, CP-1252, ISO-8859-1; implement fallback auto-detect |
| Audio/subtitle switching crash | High | Add debounce, queue changes, test on low-end devices |
| Brightness filter performance | Medium | Use native ColorMatrix (GPU-accelerated); test on devices with <2GB RAM |
| Dialog state loss on app kill | Low | Persist to ViewModel + SharedPreferences, restore on resume |
| Landscape fullscreen rotation | Low | Use ViewModel + Fragment lifecycle; test with device rotation enabled |

---

## 10. Definition of Done

1. Code reviewed and approved (no lint warnings)
2. All unit + integration tests pass
3. Manual testing on device ≥ Android 8 (minSdk 26) in landscape mode
4. VR UX checklist passed (touch targets, contrast, no menu nesting)
5. Strings added to all three language files (EN/RU/UK)
6. `dev/CHANGELOG.md` updated via `add_to_dev_log.ps1`
7. `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` updated with new feature
8. APK tested on standard + photos + legacy flavors

---

## 11. Effort Estimate

- Development: **8–12 story points** (3–4 days for one engineer)
  - Dialog framework + tab switching: 2 days
  - Audio/subtitle tabs + fixes: 1.5 days
  - Speed/volume/brightness/HUE tabs: 1 day
  - Testing + bug fixes: 1–2 days
- QA/Testing: **2–3 days** (smoke + manual + Maestro)
- Total: **5–7 days** (one person, assuming no blockers)

---

## 12. Flavour & Build Scope

**All flavors**: `standard`, `lite` (no audio/cloud, but has video), `photos` (video+images), `legacy` (no cloud).

**Build targets**: `assembleStandardDebug`, `assembleLiteDebug`, `assemblePhotosDebug`, `assembleLegacyDebug` must all pass lint + unit tests.

---

## 13. Future Enhancements (Phase 2+)

- Subtitle sync offset slider (advanced users)
- HUE slider instead of presets (fine control for colour-blind users)
- Equalizer tab (bass, treble, balance)
- Video aspect ratio adjustment (crop, zoom, 4:3 → 16:9, etc.)
- PiP size adjustment
- Gesture-based controls (swipe to adjust brightness/volume)
