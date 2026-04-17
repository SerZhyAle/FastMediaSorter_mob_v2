# Task: 3D Video Auto-Detection & SBS Support (v2.63 Implementation)

**Status**: Ready for Development  
**Date**: 2026-04-17  
**Sprint**: v2.63  
**Type**: Feature Implementation + Documentation  
**Owner**: [Engineer TBD]  
**Tier**: P1 — Medium (based on research findings: CONDITIONAL GO)  

---

## 1. Executive Summary

Implement automatic detection and rendering of SBS (Side-by-Side) stereoscopic video content in FastMediaSorter v2. Enable users to watch 3D videos (60–70% of real-world 3D content) in proper stereo mode on Android 8+ devices with minimal performance impact.

**Key Deliverables**:

- Auto-detection of SBS format (aspect ratio + Matroska metadata)
- StereoVideoProcessor for crop-based stereo rendering
- "3D" tab in Playback Settings Dialog
- Comprehensive documentation updates (EN/RU/UK)
- CHANGELOG + feature inventory updates
- Unit + E2E test coverage

**Effort**: 5–7 days (1 engineer + QA)  
**Risk**: 🟢 LOW (proven technology, well-researched)  
**User Impact**: 🟡 MEDIUM-HIGH (VR users: significant; casual users: nice-to-have)

---

## 2. Feature Overview

### 2.1 What Gets Built

**Core Feature: SBS (Side-by-Side) Stereo Detection & Rendering**

| Component | Functionality |
|-----------|---------------|
| **Auto-Detection** | Aspect ratio heuristic (94% accuracy) + MKV metadata parsing (100% if present) |
| **Detection Triggers** | On video load; on container metadata change; on manual user selection |
| **Rendering** | VideoProcessor-based crop + multi-viewport rendering (left 50%, right 50%) |
| **Display Modes** | Side-by-side on screen (for phone + cardboard) or toggle view (left/right eye only) |
| **User Control** | "3D" tab in Playback Settings Dialog with Auto/SBS/OU/Mono options |
| **Scope** | Works in fullscreen and embedded player modes |

### 2.2 Scope Boundaries

**Included in v2.63**:

- ✅ SBS horizontal stereo only
- ✅ Auto-detection via heuristics + metadata
- ✅ Side-by-side + toggle display modes
- ✅ Integration with existing PlaybackSettingsDialog
- ✅ Documentation (EN/RU/UK)

**Deferred to v2.64+**:

- ❌ Over-Under (OU) vertical stereo (20–25% coverage, lower priority)
- ❌ Frame-sequential (requires 120 FPS support)
- ❌ Anaglyph (colour-based stereo, niche use case)
- ❌ VR SDK framework integration (Cardboard SDK deprecated; Meta Quest uses proprietary OS)
- ❌ Eye-tracking / head movement features

---

## 3. Architecture & Design

### 3.1 Component Diagram

```
PlaybackSettingsDialog (existing, expanded)
  └─ "3D" Tab (NEW)
     ├─ Radio button: Auto-detect
     ├─ Radio button: Force SBS
     ├─ Radio button: Force OU (display greyed out; link to roadmap doc)
     ├─ Radio button: Mono
     └─ Observer: ViewModel.setStereoMode(StereoMode)

PlayerViewModel (updated)
  └─ LiveData<StereoMode> stereoMode
  └─ Method: setStereoMode(StereoMode)
     └─ Emits to: StereoDetector + StereoVideoProcessor

StereoDetector (NEW class)
  ├─ Function: detectMode(videoWidth, videoHeight, metadata): StereoMode
  ├─ Aspect ratio heuristic
  └─ MKV metadata parser

StereoVideoProcessor (NEW class, extends VideoProcessor)
  ├─ setStereoMode(StereoMode)
  ├─ onOutputFrameAvailable(): crops frame texture
  └─ Renders left 50% + right 50% to separate viewports

ExoPlayer Instance (existing)
  └─ ConfigureVideoEffects() with StereoVideoProcessor
```

### 3.2 Data Flow

```
User loads video
  ↓
ExoPlayer prepares tracks
  ↓
[AUTO-DETECT PATH]
  ├─ StereoDetector.detectFromFormat(Format) — actual API
  ├─ Returns: StereoMode.SBS_FULL | SBS_HALF | OU | MONO | UNKNOWN
  ├─ PlayerPlaybackCallbackImpl.onStereoDetected() → ViewModel.setAutoDetectedStereoMode()
  └─ ViewModel.stereoMode StateFlow emits; UI shows current mode in dialog
  
  [OR USER-SELECT PATH]
  ├─ User opens Playback Settings dialog (gear icon)
  ├─ 3D section with radio buttons: Auto / SBS / OU(disabled) / Mono
  ├─ PlayerSettingsManager.onStereoModeChanged → ViewModel.setStereoMode()
  └─ StateFlow collector → VideoPlayerManager.applyStereoEffect()
  
  ↓
StereoVideoProcessor.setStereoMode(resolved) + buildGlEffect(resolved)
  ↓
Architecture decision (Phase 1): single PlayerView surface cannot split into
two eye viewports without destroying one eye. buildGlEffect() returns null
for all modes — SBS/OU frames are preserved as-is for phone VR optics to split.
Full dual-viewport GL rendering is deferred to Phase 2 (v2.64+).
  ↓
exoPlayer.setVideoEffects(emptyList())  // placeholder; Phase 2 will pass GL effects
  ↓
User sees SBS frame intact — VR headset optics route each half to correct eye
```

### 3.3 Key Classes & Methods

**StereoDetector.kt** (~120 lines) — IMPLEMENTED ✅

```kotlin
// Actual API (differs from original spec):
class StereoDetector {
  // Primary: takes a full Format object (has both dimensions + customData)
  fun detectFromFormat(format: Format): StereoMode
  // Convenience: dimensions only, skips metadata step
  fun detectFromDimensions(width: Int, height: Int): StereoMode
}
// AR bounds: SBS_FULL = 3.2..3.8, SBS_HALF height≥1800, OU = 0.50..0.65
// Note: enum is StereoMode.SBS_HALF (not SBS_CROPPED as originally planned)
```

**StereoVideoProcessor.kt** (~115 lines) — IMPLEMENTED ✅ (Phase 1 pass-through)

```kotlin
// Actual implementation (does NOT extend VideoProcessor — no GL crop in Phase 1):
class StereoVideoProcessor {
  fun setStereoMode(mode: StereoMode)  // resolves AUTO/UNKNOWN to MONO
  fun buildGlEffect(mode: StereoMode): Effect?  // returns null for all modes in Phase 1
  // Why null: single-surface PlayerView can't split into two viewports.
  // SBS/OU frames are preserved intact for phone-VR optics to split.
  // Phase 2 (v2.64+): replace with dual-FBO GL renderer.
  fun getCurrentMode(): StereoMode
  fun release()
}
```

**PlayerSettingsDialog.kt** (`ui/dialog/PlayerSettingsDialog.kt`) — IMPLEMENTED ✅

```kotlin
// Already exists with 3D section (radio buttons: Auto/SBS/OU-disabled/Mono)
// Wired via PlayerSettingsManager → onStereoModeChanged lambda → ViewModel
data class PlayerSettings(
  ...
  val stereoMode: StereoMode = StereoMode.AUTO
)
// setupStereoSection() disables OU radio (future)
// loadCurrentSettings() pre-selects current radio from ViewModel
// collectSettings() maps checked radio → StereoMode enum
```

---

## 4. Implementation Tasks (Execution Checklist)

### Phase 1: Core Implementation (3 days)

**Task 1.1**: Create StereoDetector class — ✅ DONE

- [x] Implement aspect ratio heuristic (SBS_FULL AR≈3.2-3.8, SBS_HALF h≥1800, OU AR≈0.50-0.65)
- [x] Add Matroska metadata parser (Format.customData key "stereo_mode")
- [ ] Unit test coverage: 8+ test cases (edge cases: ultra-wide videos, square, etc.)
- [x] Logging via Timber for debug

**Task 1.2**: Create StereoVideoProcessor class — ✅ DONE (Phase 1 pass-through)

- [x] Tracks StereoMode with @Volatile thread safety
- [x] `buildGlEffect()` returns null in Phase 1 (single surface — no GL crop)
- [x] `release()` resets state on player destroy
- [ ] Phase 2: dual-FBO GL crop rendering (v2.64+)
- [ ] Measure frame-rate impact (<5% overhead) — deferred to Phase 2

**Task 1.3**: Update PlayerViewModel — ✅ DONE

- [x] Add `StateFlow<StereoMode> stereoMode` property
- [x] Add `setStereoMode(StereoMode)` method
- [x] Add `setAutoDetectedStereoMode()` — only applies when user hasn't overridden
- [x] Add `resetStereoModeForNewFile()` — resets to AUTO on each new video
- [ ] Persist user preference to SharedPreferences (currently session-only)

**Task 1.4**: Update PlaybackSettingsDialog — ✅ DONE

- [x] 3D section added to `dialog_player_settings.xml` (portrait)
- [x] ~~`tab_stereo_settings.xml`~~ — integrated inline in existing dialog (no tab needed)
- [x] Radio buttons: Auto / SBS / OU(disabled) / Mono
- [x] Wired via `PlayerSettingsManager` lambdas → ViewModel
- [x] ~~"Auto-detected: SBS" status label~~ — not shown (detected mode pre-selects radio button)
- [x] **Landscape layout fixed** (2026-04-17): `layout-land/dialog_player_settings.xml` now mirrors portrait

### Phase 2: Integration & Testing (2 days)

**Task 2.1**: Integration Testing

- [ ] End-to-end test: load SBS video → auto-detect → render correctly
- [ ] Mid-playback switching: Mono ↔ SBS without crash
- [ ] Rapid toggling (stress test): switch 3D mode 10 times, no ANR
- [ ] Landscape + portrait rotation: stereo mode persists

**Task 2.2**: Unit Tests — ⬜ NOT STARTED

- [ ] `StereoDetectorTest`: 8+ test cases (aspect ratios, metadata parsing)
- [ ] `StereoVideoProcessorTest`: mode tracking, buildGlEffect() null contract, release()
- [ ] `StereoModeTest`: enum transitions, serialization, fromKey() fallback

**Task 2.3**: Maestro E2E Tests

- [ ] `maestro/smoke/3d-video-sbs.yaml`: load SBS video, verify rendering
- [ ] `maestro/smoke/3d-video-switching.yaml`: toggle between 3D modes
- [ ] Coverage: all flavors (standard, lite, photos, legacy)

**Task 2.4**: Manual Device Testing

- [ ] Test on Snapdragon 600 (budget, ~30 FPS, check thermal)
- [ ] Test on Snapdragon 800+ (mid/high-end)
- [ ] Verify no crashes, memory leaks, excessive battery drain
- [ ] Test with real 3D SBS video file (from test assets)

### Phase 3: Documentation Updates (1 day) — **MANDATORY**

#### 3.1 Feature Inventory Documentation — ✅ DONE

**Files updated** (ALL THREE language variants):

- [x] `docs/FEATURES.md` (English) — line 134
- [x] `docs/FEATURES_RU.md` (Russian) — line 134
- [x] `docs/FEATURES_UK.md` (Ukrainian) — line 134

#### 3.2 CHANGELOG Documentation — ✅ DONE

- [x] CHANGELOG auto-updated via `add_to_dev_log.ps1` (multiple entries on 2026-04-17)

#### 3.3 Playback Control Help Documentation — ✅ DONE

- [x] `docs/HOW_TO.md` (EN) — section: "Watching 3D Videos (VR)"
- [x] `docs/HOW_TO_RU.md` (RU) — section: "Просмотр 3D видео (VR)"
- [x] `docs/HOW_TO_UK.md` (UK) — section: "Перегляд 3D відео (VR)"

#### 3.4 In-App Release Notes

**File**: Hardcoded string resources or release notes file

- [ ] Add to release notes for v2.63:

  ```
  EN: "NEW: 3D video support (SBS format) with automatic detection"
  RU: "НОВОЕ: Поддержка 3D видео (формат SBS) с автоматическим определением"
  UK: "НОВЕ: Підтримка 3D відео (формат SBS) з автоматичним визначенням"
  ```

#### 3.5 String Resources — ✅ DONE

- [x] `values/strings.xml` — 7 strings added (EN)
- [x] `values-ru/strings.xml` — 7 strings added (RU)
- [x] `values-uk/strings.xml` — 7 strings added (UK)

---

## 5. Testing & Validation

### 5.1 Unit Test Cases

**StereoDetectorTest.kt**:

```kotlin
@Test fun testDetectSBS_Full_3840x1080() {
  assertEquals(StereoMode.SBS_FULL, detector.detectMode(3840, 1080, null))
}

@Test fun testDetectSBS_2560x720() {
  assertEquals(StereoMode.SBS_FULL, detector.detectMode(2560, 720, null))
}

@Test fun testDetectOU_1920x2160() {
  assertEquals(StereoMode.OU, detector.detectMode(1920, 2160, null))
}

@Test fun testDetectMono_1920x1080() {
  assertEquals(StereoMode.MONO, detector.detectMode(1920, 1080, null))
}

@Test fun testDetectMono_Ultrawide_4096x820() {
  assertEquals(StereoMode.MONO, detector.detectMode(4096, 820, null))
}

@Test fun testMetadata_MKV_StereoMode() {
  val metadata = mockMKVMetadata(stereoMode = 2)  // SBS
  assertEquals(StereoMode.SBS_FULL, detector.detectMode(1920, 1080, metadata))
}

@Test fun testMetadata_Priority_OverAspectRatio() {
  val metadata = mockMKVMetadata(stereoMode = 0)  // Mono metadata
  assertEquals(StereoMode.MONO, detector.detectMode(3840, 1080, metadata))
}

@Test fun testFalsePositive_Rate_AcceptableRange() {
  // Out of 100 random ultrawide videos, <2 should be detected as 3D
  val falsePositives = testRandomVideos(100).count { it.detected3D }
  assertTrue(falsePositives <= 2, "False positive rate >2%")
}
```

### 5.2 Integration Test Scenarios

**Maestro Flow: `maestro/smoke/3d-video-sbs.yaml`**

```yaml
appId: com.sza.fastmediasorter
flows:
  - tapOn:
      point: { x: 100, y: 100 }  # Browse button
  - wait:
      timeout: 2000
  - tapOn:
      text: "3D-SBS-Test.mp4"
  - wait:
      timeout: 2000
  - tapOn:
      point: { x: 600, y: 400 }  # Fullscreen
  - wait:
      timeout: 500
  - tapOn:
      point: { x: 50, y: 50 }    # Settings gear
  - wait:
      timeout: 500
  - swipeLeft:
      x: 500, y: 600
  - tapOn:
      text: "3D"  # Navigate to 3D tab
  - wait:
      timeout: 500
  - assertVisible:
      text: "Auto-detected: SBS"
  - tapOn:
      text: "Force SBS"
  - wait:
      timeout: 1000
  - assertNotVisible:
      text: "Error"
  - assertNotVisible:
      text: "Crash"
```

### 5.3 Device Testing Matrix

| Device | OS | Processor | RAM | SBS Test | Toggle Test | Thermal Test |
|--------|----|-----------|----|----------|-------------|--------------|
| Moto G9 / equiv | Android 11 | Snapdragon 600 | 4GB | ✅ Plan | ✅ Plan | ✅ 1hr playback |
| Pixel 4a / equiv | Android 13 | Snapdragon 765G | 6GB | ✅ Plan | ✅ Plan | ✅ Check throttling |
| Pixel 8 / equiv | Android 14 | Snapdragon 8+ | 12GB | ✅ Plan | ✅ Plan | ✅ Baseline |

**Success Criteria**:

- ✅ No ANR on any device
- ✅ No memory leaks after 30-min playback
- ✅ Frame rate: ≥24 FPS (budget), ≥60 FPS (mid/high-end)
- ✅ Thermal: no excessive heat (device doesn't throttle)
- ✅ Battery: 1 hour playback uses <15% battery

---

## 6. Files to Create/Modify

### Create (NEW)

```
app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt          (~150 lines)
app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt     (~200 lines)
app_v2/src/main/res/layout/tab_stereo_settings.xml                                 (~120 lines)
app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoDetectorTest.kt      (~150 lines)
app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessorTest.kt (~100 lines)
maestro/smoke/3d-video-sbs.yaml                                                    (~50 lines)
maestro/smoke/3d-video-switching.yaml                                              (~80 lines)
```

### Modify (EXISTING)

```
app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt
  └─ Add: LiveData<StereoMode>, setStereoMode() method, SharedPreferences persistence

app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackSettingsDialog.kt
  └─ Add: "3D" tab, setupStereoTab() method, stereoModeObserver

app_v2/src/main/res/values/strings.xml
  └─ Add: 6 new string keys for 3D labels (EN)

app_v2/src/main/res/values-ru/strings.xml
  └─ Add: 6 string translations (RU)

app_v2/src/main/res/values-uk/strings.xml
  └─ Add: 6 string translations (UK)

docs/FEATURES.md
  └─ Add: 1 bullet in "Video Features" section (EN)

docs/FEATURES_RU.md
  └─ Add: 1 bullet in "Видео" section (RU)

docs/FEATURES_UK.md
  └─ Add: 1 bullet in "Відео" section (UK)

docs/HOW_TO.md
  └─ Add: New "Watching 3D Videos (VR)" section (EN)

docs/HOW_TO_RU.md
  └─ Add: New "Просмотр 3D видео (VR)" section (RU)

docs/HOW_TO_UK.md
  └─ Add: New "Перегляд 3D відео (VR)" section (UK)

dev/CHANGELOG.md
  └─ Auto-updated via add_to_dev_log.ps1 (3 entries)
```

---

## 7. Acceptance Criteria

### Build & Lint

- [ ] `./gradlew lintStandardDebug` — no warnings in touched files
- [ ] `./gradlew assembleStandardDebug` — clean build, no errors
- [ ] All flavors: standard, lite, photos, legacy must build successfully

### Code Quality

- [ ] No Android Lint warnings
- [ ] Code review approved (≥2 reviewers)
- [ ] Naming follows project convention (VerbNounClass)
- [ ] No file >1000 lines (logic extracted to helpers if needed)

### Testing

- [ ] Unit test pass: 100% pass rate
- [ ] Maestro E2E tests pass: all scenarios
- [ ] Manual device testing: all 3 device classes pass
- [ ] No ANR, crashes, or memory leaks detected

### Documentation

- [ ] ✅ **ALL THREE** feature inventory files updated (FEATURES.md EN/RU/UK)
- [ ] ✅ **ALL THREE** how-to guides updated (HOW_TO.md EN/RU/UK)
- [ ] ✅ String resources added to all three language files (EN/RU/UK)
- [ ] ✅ CHANGELOG auto-logged via script (3 entries)
- [ ] ✅ Release notes prepared for v2.63
- [ ] ✅ No orphaned or inconsistent documentation

### Localization

- [ ] EN strings use author style: `..` (two dots, NOT `...`)
- [ ] RU strings: Always use `ё`/`Ё` where grammatically correct (не `е`, а `ё`)
  - Examples: `всё`, `ещё`, `чёрный`, `объём`, `тёмный`
- [ ] UK strings: Proper Ukrainian grammar + orthography

### Performance

- [ ] Frame rate ≥24 FPS on Snapdragon 600 (budget)
- [ ] Frame rate ≥60 FPS on Snapdragon 800+ (mid/high)
- [ ] CPU impact <5% vs. mono playback
- [ ] Memory overhead <20 MB
- [ ] No thermal throttling during 1-hour playback

### Regression

- [ ] Existing player features unchanged
- [ ] Mono video playback unaffected
- [ ] All existing tests pass
- [ ] No crashes when 3D disabled

---

## 8. Release Notes Template

### v2.63 Release Notes (EN)

```markdown
## New Features

### 3D Video Support (SBS Format)
- **Automatic Detection**: FastMediaSorter now automatically detects 
  stereoscopic Side-by-Side (SBS) 3D videos
- **Manual Override**: Use "Playback Settings" → "3D" tab to force or disable 3D mode
- **Low Overhead**: Optimized for VR headsets and budget devices
- **Works on Android 8+**: Available for all device configurations

**To watch 3D videos**:
1. Open a SBS 3D video file
2. Tap fullscreen
3. Tap Playback Settings (⚙️)
4. Go to "3D" tab
5. Select "Auto-detect" or "Force SBS"
6. Use with VR viewer (Google Cardboard, etc.) for full effect

**What's supported**: SBS (Side-by-Side) horizontal stereo format
**Coming later**: Over-Under (OU) and other 3D formats
```

### v2.63 Release Notes (RU)

```markdown
## Новые возможности

### Поддержка 3D видео (формат SBS)
- **Автоматическое определение**: FastMediaSorter теперь автоматически 
  распознаёт стереоскопические 3D видео формата Side-by-Side (SBS)
- **Ручное управление**: Используйте "Настройки воспроизведения" → 
  вкладка "3D" для включения или отключения режима
- **Низкие затраты ресурсов**: Оптимизировано для VR-очков и устройств 
  с ограниченными ресурсами
- **Работает на Android 8+**: Доступно для всех конфигураций устройств

**Как смотреть 3D видео**:
1. Откройте 3D видео в формате SBS
2. Нажмите на полноэкранный режим
3. Нажмите "Настройки воспроизведения" (⚙️)
4. Перейдите на вкладку "3D"
5. Выберите "Автоматическое определение" или "Принудительно SBS"
6. Используйте VR-просмотрщик (Google Cardboard и др.) для полного эффекта

**Поддерживаемые форматы**: SBS (Side-by-Side) горизонтальная стерео
**Будет добавлено**: Форматы Over-Under (OU) и другие
```

### v2.63 Release Notes (UK)

```markdown
## Нові можливості

### Підтримка 3D відео (формат SBS)
- **Автоматичне визначення**: FastMediaSorter тепер автоматично розпізнає 
  стереоскопічні 3D відео формату Side-by-Side (SBS)
- **Ручне керування**: Використовуйте "Налаштування відтворення" → 
  вкладка "3D" для включення або відключення режиму
- **Низькі витрати ресурсів**: Оптимізовано для VR-окулярів і пристроїв 
  з обмеженими ресурсами
- **Працює на Android 8+**: Доступно для всіх конфігурацій пристроїв

**Як дивитися 3D відео**:
1. Откройте 3D видео в формате SBS
2. Натисніть кнопку повноекранного режиму
3. Натисніть "Налаштування відтворення" (⚙️)
4. Перейдіть на вкладку "3D"
5. Виберіть "Автоматичне визначення" або "Примусово SBS"
6. Використовуйте VR-переглядач (Google Cardboard та ін.) для повного ефекту

**Підтримувані формати**: SBS (Side-by-Side) горизонтальна стерео
**Буде додано**: Формати Over-Under (OU) та інші
```

---

## 9. Success Metrics

| Metric | Target | Success Threshold |
|--------|--------|-------------------|
| Auto-detection accuracy | 94% | ≥90% |
| Performance (CPU impact) | <5% | <8% on budget device |
| Frame rate (Snapdragon 600) | ≥24 FPS | ≥20 FPS minimum |
| Memory overhead | <20 MB | <30 MB absolute max |
| Thermal throttling time | 0 | <5 min into 1-hour video |
| Test pass rate | 100% | >95% |
| Documentation completeness | 100% (3 languages) | ALL 3 files updated |

---

## 10. Known Limitations & Future Work

### Phase 1 (v2.63) Limitations

- ❌ Over-Under (OU) format not supported (deferred to v2.64)
- ❌ No frame-sequential stereo (requires 120 FPS support)
- ❌ No anaglyph support (niche, deferred to v2.64)
- ❌ Cannot export/convert 3D videos
- ❌ No eye-tracking or head movement features
- ❌ Not compatible with official VR SDKs (Cardboard deprecated, Meta Quest proprietary)

### Workarounds for Users

- For Over-Under videos: rename to force user to select "OU" manually (doc link)
- For non-standard formats: use external VR player recommendations
- For VR viewing: use phone-based viewers (Cardboard, etc.) — app displays crops on screen

### Phase 2+ Roadmap (v2.64+)

- ✅ Over-Under (OU) stereo support
- ✅ Anaglyph stereo rendering
- ✅ Frame-sequential support (if 120 FPS becomes feasible)
- ✅ Better metadata extraction from HEVC stereo extensions
- ✅ GPU-accelerated crop via custom OpenGL shader
- ✅ Optional eye-candy features (eye strain reduction, blue light filter)

---

## 11. Definition of Done

**ALL of the following must be completed**:

### Code

- [ ] StereoDetector class implemented + unit tested
- [ ] StereoVideoProcessor class implemented + tested on device
- [ ] PlayerViewModel updated with stereoMode LiveData
- [ ] PlaybackSettingsDialog "3D" tab added + functional
- [ ] No lint warnings, all code reviewed

### Testing

- [ ] Unit tests: 100% pass rate
- [ ] Maestro E2E: all scenarios pass
- [ ] Manual device testing: 3 device classes pass (budget/mid/high-end)
- [ ] Performance: <5% CPU impact, ≥24 FPS on budget device
- [ ] No crashes, ANRs, or memory leaks

### Documentation (MANDATORY)

- [ ] ✅ `docs/FEATURES.md` updated (EN)
- [ ] ✅ `docs/FEATURES_RU.md` updated (RU) — WITH `ё` where needed
- [ ] ✅ `docs/FEATURES_UK.md` updated (UK)
- [ ] ✅ `docs/HOW_TO.md` updated (EN) — new section added
- [ ] ✅ `docs/HOW_TO_RU.md` updated (RU) — new section added
- [ ] ✅ `docs/HOW_TO_UK.md` updated (UK) — new section added
- [ ] ✅ String resources: `strings.xml` (EN), `strings-ru.xml` (RU), `strings-uk.xml` (UK)
- [ ] ✅ CHANGELOG logged: 3 entries via `add_to_dev_log.ps1`
- [ ] ✅ Release notes drafted (EN/RU/UK)

### Build & Flavors

- [ ] `./gradlew assembleStandardDebug` — passes
- [ ] `./gradlew assembleLiteDebug` — passes
- [ ] `./gradlew assemblePhotosDebug` — passes
- [ ] `./gradlew assembleLegacyDebug` — passes
- [ ] All APKs testable on device via ADB

### Sign-Off

- [ ] Code review approved (2+ engineers)
- [ ] QA sign-off (testing complete)
- [ ] Product owner approval (feature as designed)
- [ ] Ready to merge → v2.63 release

---

## 12. Risks & Mitigation

| Risk | Probability | Severity | Mitigation |
|------|-------------|----------|-----------|
| Crop math error (inverted eyes) | Low (2%) | High | Unit test all edge cases; manual verification |
| Frame drops under load | Low (3%) | High | Profile on Snapdragon 600; set frame-rate limiter if needed |
| Memory leak in texture buffers | Low (5%) | High | Use try-finally + explicit cleanup; Valgrind profiling |
| Metadata parsing crash | Medium (10%) | Medium | Wrap in try-catch; fallback to aspect-ratio heuristic |
| Documentation inconsistency (EN/RU/UK) | Medium (20%) | Medium | Checklist: verify all 3 files updated in ONE commit |
| User confusion (which eye is which) | Medium (25%) | Low | In-app tooltip + HOW_TO guide explains + release notes |
| Thermal throttling on budget device | Low (5%) | Medium | Monitor during 1-hour playback; add throttle detection if needed |

**Overall Risk**: 🟢 **LOW** (well-researched, proven technology, clear testing strategy)

---

## 13. Contact & Escalation

| Role | Responsibility |
|------|-----------------|
| **Engineer** | Implement StereoDetector, VideoProcessor, UI integration |
| **QA Lead** | Device testing, Maestro test writing, thermal/battery validation |
| **Tech Lead** | Code review, architecture validation, performance sign-off |
| **Product Owner** | Feature acceptance, documentation review, release decision |
| **Localization** | Review RU/UK translations; ensure `ё` usage in Russian text |

---

**Task Owner**: [TBD]  
**Created**: 2026-04-17  
**Status**: Ready for Sprint Planning  
**Estimated Effort**: 5–7 days (1 engineer + QA)  
**Next Step**: Assign to sprint, create subtasks in project tracker
