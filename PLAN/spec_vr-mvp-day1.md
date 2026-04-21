# Specification: VR MVP Day 1 — See 3D Content on Quest

**Status:** Draft
**Date:** 2026-04-20
**Tier:** 2 — Easy (2–4h, low risk)
**Goal:** Build, deploy, and validate: regular content plays in panel window + 3D/360° video renders in immersive OpenXR mode on Meta Quest 3.

---

## 1. Problem Statement

Весь VR-код написан, но **ни разу не тестировался на устройстве**. Ресёрч-фаза завершена, спецификации маршрутизации написаны, баги в коде уже исправлены (gfxReqs fix, манифест, routing). Задача: собрать APK, установить на Quest 3 и **увидеть результат**.

Текущее состояние: код собирается (`assembleVrDebug`), все routing-решения в коде — стандартный контент → panel, 3D/360° контент → immersive. Нужна **верификация на железе** и фикс любых runtime-проблем.

---

## 2. Goals

1. VR flavor APK собирается и устанавливается на Quest 3 via ADB.
2. Обычный контент (JPG, PNG, 2D-видео, аудио, PDF) открывается в **panel mode** — как обычное Android-приложение в окне Quest.
3. Стереоскопический видеоконтент (SBS/OU) открывается в **immersive mode** и рендерится через OpenXR с per-eye UV crop.
4. 360°/VR180 видеоконтент открывается в immersive с Equirect2/Cylinder layer.
5. Все существующие функции (browse, file ops, copy/move, settings, favourites) работают в panel mode без деградации.
6. Пользователь может выйти из immersive нажатием кнопки (B / Back / X).

Non-goals for this spec:

- Flat stereo в panel mode (per-eye rendering в окне — невозможно для third-party).
- Passthrough background в immersive (сейчас void black — это OK для MVP).
- XR Action Set для контроллеров (работаем через Android KeyEvent fallback).
- 360° фото (только видео).
- Store submission / сертификация.
- Meta Spatial SDK hybrid apps (cooperative mode — Phase 2).

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ❌ | Не затрагивается |
| `lite`     | ❌ | Не затрагивается |
| `photos`   | ❌ | Не затрагивается |
| `legacy`   | ❌ | Не затрагивается |
| `vr`       | ✅ | Единственный затрагиваемый flavor — `BuildConfig.SUPPORT_VR_PLAYER = true` |

VR flavor gates: `SUPPORT_VR_PLAYER`, `PLAYER_ACTIVITY_CLASS = "com.sza.fastmediasorter.vr.VrPlayerActivity"`.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 26+ (vr minSdk) | Quest 3 runs Android 12-based HorizonOS — API 26+ is safe |
| 32+ (Quest 3 actual) | All modern APIs available; no legacy workarounds needed |

### 3.3 Wear OS Impact

No Wear OS changes required. VR flavor disables `SUPPORT_WEAR_COMPANION`.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `VrPlayerActivity` | `app_v2/src/vr/java/.../vr/VrPlayerActivity.kt` | VR player host, extends PlayerActivity; routes content to panel/immersive |
| `PlayerActivity` | `app_v2/src/main/java/.../ui/player/PlayerActivity.kt` | Standard player — used as panel fallback |
| `OpenXrSessionManager` | `app_v2/src/vr/java/.../vr/openxr/OpenXrSessionManager.kt` | JNI bridge, manages XR session lifecycle |
| `OpenXrNative.cpp` | `app_v2/src/vr/cpp/OpenXrNative.cpp` | Native OpenXR code — session, swapchains, render loop |
| `VrStereoRenderer` | `app_v2/src/vr/java/.../vr/render/VrStereoRenderer.kt` | Per-eye UV crop GLSL shader |
| `VrVideoSurfaceTextureBridge` | `app_v2/src/vr/java/.../vr/render/VrVideoSurfaceTextureBridge.kt` | ExoPlayer → OES texture → VR pipeline |
| `VrLayerFactory` | `app_v2/src/vr/java/.../vr/render/VrLayerFactory.kt` | Chooses Quad/Projection/Equirect2/Cylinder layer |
| `StereoDetector` | `app_v2/src/main/java/.../ui/player/StereoDetector.kt` | Filename + metadata + dimension stereo detection |
| `PlayerEntryCoordinator` | `app_v2/src/main/java/.../ui/player/entry/PlayerEntryCoordinator.kt` | Flavor-aware player entry routing |
| VR manifest | `app_v2/src/vr/AndroidManifest.xml` | VR category on MainActivity, focusaware on VrPlayerActivity |

**Existing data flow (already in code, never tested on device):**

```
User taps file in BrowseActivity
  → PlayerActivity.createPlayerIntent()
  → Class.forName(BuildConfig.PLAYER_ACTIVITY_CLASS)  // → VrPlayerActivity
  → VrPlayerActivity.onCreate()
  → resolvePlaybackRoute()
    ├── shouldUseStandardPlayer() == true  → launchStandardPlayerFallback() → PlayerActivity (panel mode)
    └── shouldUseStandardPlayer() == false → startXrInitialization() → OpenXR session → immersive render
```

**Gap:** This full pipeline has **never executed on Quest 3 hardware**.

---

## 5. What Already Works (in code)

### 5.1 Panel mode routing (code complete)

`VrPlayerActivity.shouldUseStandardPlayer()` returns `true` for:

- Non-video content (images, audio, documents) — line 548
- `disable3dVr` kill-switch enabled — line 564
- Non-stereoscopic, non-spherical video (MONO/UNKNOWN) — line 577

When true → `launchStandardPlayerFallback()` → starts `PlayerActivity` (standard) → finishes `VrPlayerActivity`.

Result: standard PlayerActivity opens as a regular Android window in Quest panel mode.

### 5.2 Immersive mode routing (code complete, untested on device)

When `shouldUseStandardPlayer()` returns `false` (stereoscopic or spherical video):

- `startXrInitialization()` → launches on `Dispatchers.IO`
- `OpenXrSessionManager.initialize()` → calls JNI `nativeInitialize()`
- Native: `createSessionAndSwapchains()` (with gfxReqs fix) → `xrCreateSession` → swapchain creation
- On success: `initializeVrRenderPipeline()` callback → bridge init → ExoPlayer surface redirect
- Render loop: `renderVrFrame()` → bridge.updateFrame() + stereoRenderer.renderEye() per eye per frame

### 5.3 Stereo detection (code complete)

`StereoDetector` covers:

- Filename patterns: `_SBS`, `_OU`, `_TB`, `_LR`, `_3DH`, `_3DV`, `_180x180`, `_360`, `_VR180` etc.
- MP4 spatial metadata: `st3d` box (stereo), `sv3d` box (spherical)
- Matroska `StereoMode` element
- Aspect ratio heuristics: 2:1 → SBS, 1:1 → OU candidates

### 5.4 Controller exit (code complete)

`VrPlayerActivity.dispatchKeyEvent()` handles:

- `KEYCODE_BUTTON_X` → exit immersive
- `KEYCODE_BUTTON_B` → exit immersive
- `KEYCODE_BACK` → exit immersive
- `KEYCODE_MENU` → open PlaybackControlDialog

This relies on Quest passing controller buttons as Android KeyEvents, which works for panel-mode activities with `focusaware=true`.

### 5.5 XR session error recovery (code complete)

If `OpenXrSessionManager.initialize()` returns false:

- `launchVrFailureRecovery()` → stops playback → shows error toast → finishes activity
- User returns to browse list — not stuck in black screen

---

## 6. Proposed Architecture

### 6.1 No new architecture — validate existing

This spec does NOT introduce new architecture. The goal is to **validate the existing implementation on device** and fix any runtime issues discovered.

### 6.2 New classes / files

No new classes needed.

### 6.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | VrPlayerActivity is a thin coordinator; logic in managers |
| Naming conventions | ✅ | All classes follow `NounVerbManager`, `VerbNounUseCase` patterns |
| Data flow `UI → VM → UseCase → Repo → DS` | ✅ | Standard player stack inherited |
| No `Log.d()` — Timber only | ⚠️ | VrPlayerActivity has 3 `Log.e("VR_BOOT", ...)` calls — **intentional** diagnostic breadcrumbs for XR runtime log flooding scenarios |
| Room schema version | N/A | No DB changes |
| StateFlow/SharedFlow | ✅ | ViewModel state observed via collectLatest |
| Hilt DI | ✅ | `VrModule.kt` provides VR-specific bindings |

---

## 7. Data Flow

```
[Quest Library] → tap app icon
  → MainActivity (com.oculus.intent.category.VR + LAUNCHER)
  → BrowseActivity (standard file browser — panel window)
  → user taps a file
  → PlayerActivity.createPlayerIntent()
  → Class.forName("...VrPlayerActivity")  // VR flavor BuildConfig
  → VrPlayerActivity.onCreate()

                       ┌─────────────────────┐
                       │ resolvePlaybackRoute │
                       └────────┬────────────┘
                                │
                   ┌────────────┴──────────────┐
                   │                            │
            shouldUseStandard               shouldUseStandard
               == true                        == false
                   │                            │
        ┌──────────▼──────────┐      ┌──────────▼──────────┐
        │  PlayerActivity     │      │  startXrInit()      │
        │  (panel window)     │      │  ↓                  │
        │  Standard 2D player │      │  OpenXrSessionMgr   │
        │  Full feature set   │      │  ↓                  │
        │  Browse, settings,  │      │  nativeInitialize() │
        │  copy/move — all OK │      │  ↓                  │
        └─────────────────────┘      │  xrCreateSession    │
                                     │  ↓                  │
                                     │  initVrRenderPipe() │
                                     │  ↓                  │
                                     │  ExoPlayer→Bridge   │
                                     │  ↓                  │
                                     │  renderVrFrame()    │
                                     │  per-eye per-frame  │
                                     └─────────────────────┘
```

---

## 8. Pre-flight: Known Issues to Check BEFORE Build

### 8.1 Verify build compiles

```powershell
.\gradlew.bat assembleVrDebug
```

If this fails, check:

- NDK path in `local.properties` (`ndk.dir` or `ANDROID_NDK_HOME`)
- CMake version in `app_v2/build.gradle.kts` (should be 3.22.1+)
- OpenXR loader headers in `app_v2/src/vr/cpp/`

### 8.2 Verify APK contains native libs

```powershell
# After build, check APK contents
$apk = Get-ChildItem "app_v2/build/outputs/apk/vr/debug/*.apk" | Select-Object -First 1
# Verify lib/arm64-v8a/libopenxr_loader.so and lib/arm64-v8a/libopenxr_native.so exist
```

### 8.3 Verify manifest merge

After build, inspect the merged manifest:

```
app_v2/build/intermediates/merged_manifests/vrDebug/AndroidManifest.xml
```

Check:

- `MainActivity` has `com.oculus.intent.category.VR` + `LAUNCHER`
- `VrPlayerActivity` does NOT have `com.oculus.intent.category.VR`
- `VrPlayerActivity` HAS `com.oculus.vr.focusaware = true`
- `com.oculus.supportedDevices` meta-data present

---

## 9. Test Plan: Day 1 Verification

### 9.1 Device Setup

```powershell
# Connect Quest 3 via USB, enable developer mode
adb devices  # Verify device visible

# Install VR debug APK
adb install -r app_v2/build/outputs/apk/vr/debug/app_v2-vr-debug.apk

# Start logcat monitoring (in a separate terminal)
adb logcat -c; adb logcat -s VR_BOOT:* VrPlayerActivity:* OpenXrNative:* OpenXR:* | Tee-Object temp/quest_day1.log
```

### 9.2 Test media files

Prepare these files on the device (push via ADB or open from SMB share):

| # | File | Type | Expected Route | Expected Result |
|---|------|------|----------------|-----------------|
| T1 | `photo.jpg` (regular JPEG) | IMAGE | `PANEL_2D` → PlayerActivity | Opens in panel window as standard image viewer |
| T2 | `video.mp4` (regular 1080p) | VIDEO | `PANEL_2D` → PlayerActivity | Opens in panel window as standard video player |
| T3 | `music.mp3` | AUDIO | `PANEL_2D` → PlayerActivity | Opens in panel window as standard audio player |
| T4 | `test_SBS.mp4` (SBS 3D video) | VIDEO | `IMMERSIVE` → XR session | Opens in immersive; left/right eye see different halves |
| T5 | `test_OU.mp4` (OU 3D video) | VIDEO | `IMMERSIVE` → XR session | Opens in immersive; top/bottom eye separation |
| T6 | `test_360.mp4` (equirect 360°) | VIDEO | `IMMERSIVE` → XR session | Opens in immersive; 360° sphere around user |
| T7 | `test_VR180_SBS.mp4` | VIDEO | `IMMERSIVE` → XR session | Opens in immersive; 180° hemisphere |

**Where to get test files:**

- SBS test: any video named with `_SBS` suffix, or a known SBS video (e.g., YouTube VR downloads)
- 360° test: any equirect 2:1 video with `_360` in name or `sv3d` MP4 metadata
- Regular files: any standard media from the device

### 9.3 Manual Test Cases

#### TC-01: App Launch → Panel Mode

1. Open Quest Library → find FastMediaSorter.
2. Tap to launch.
3. **Expected:** App opens as a panel window in MR space. Passthrough is active. BrowseActivity shows file list.
4. **Verify:** File list renders. Navigation works. Settings accessible.

**Pass criteria:** App visible as floating window in Quest MR. All UI interactive.

#### TC-02: Regular Video → Panel Mode

1. From Browse, tap a regular video file (no SBS/OU/360° markers).
2. **Expected:** Video opens in a panel window. Standard player controls visible. Video plays with audio.
3. **Verify:** Play/pause, seek, volume work. File info accessible.

**Pass criteria:** Video plays in panel window identical to standard flavor on phone.

#### TC-03: Regular Image → Panel Mode

1. From Browse, tap a regular JPEG/PNG.
2. **Expected:** Image opens in standard viewer in panel window.
3. **Verify:** Zoom, pan, rotate work.

**Pass criteria:** Image displayed correctly in panel.

#### TC-04: SBS Video → Immersive Mode (KEY TEST)

1. From Browse, tap an SBS video (`_SBS` in filename).
2. **Expected:** Screen transitions to immersive mode (passthrough may turn off → void black background). Video renders as a floating screen with stereoscopic depth.
3. **Verify in logcat:**
   - `VrPlayerActivity: route decision ... effective=SBS ... standard=false`
   - `VrPlayerActivity: starting XR init`
   - `xrCreateSession: SUCCESS`
   - `VrPlayerActivity: initializeVrRenderPipeline COMPLETE`
   - `VrPlayerActivity: ExoPlayer video redirected to VR bridge surface`
   - `VrPlayerActivity: renderVrFrame #1 eye=LEFT`
4. **Verify visually:** Left and right eyes show different halves of the SBS video.

**Pass criteria:** 3D video visible with stereoscopic depth. Audio plays.

#### TC-05: Controller Exit from Immersive

1. While in immersive (TC-04), press B button on right controller.
2. **Expected:** Immersive mode exits. Return to Browse or previous screen.
3. **Verify in logcat:** `VrPlayerActivity: B button (right controller) → exit immersive`

**Pass criteria:** User can exit immersive without force-killing app.

#### TC-06: XR Session Failure Recovery

1. If TC-04 shows black screen or no video:
2. Check logcat for: `xrCreateSession failed` — if yes, the gfxReqs fix didn't work.
3. Check logcat for: `CANNOT redirect ExoPlayer to VR surface` — bridge init failed.
4. Check logcat for: `renderVrFrame — bridge NOT ready` — bridge texture not initialized.

**Pass criteria for failure case:** Error message shown, activity finishes, user returns to Browse (not stuck).

#### TC-07: Settings Access in Panel Mode

1. From Browse, open Settings.
2. Navigate to Playback → Behaviour.
3. **Expected:** "Disable 3D/VR" toggle visible (VR flavor only).
4. Toggle ON → open an SBS video → should open in panel mode (standard player).

**Pass criteria:** Kill-switch works, routing changes immediately.

#### TC-08: Error state — 360° video (stretch goal)

1. From Browse, tap a 360° video.
2. **Expected:** Opens in immersive with equirect sphere rendering.
3. **Verify in logcat:** Layer type = EQUIRECT2.

**Pass criteria:** 360° sphere visible around user. Head tracking works (view follows head rotation).

---

## 10. Risk Analysis

| Risk | Likelihood | Impact | Mitigation |
|------|:----------:|:------:|-----------|
| `xrCreateSession` still fails despite gfxReqs fix | Low | HIGH — no immersive at all | Check logcat for specific error code; compare with Meta developer forums. Fallback: error recovery path returns user to Browse. |
| ExoPlayer → VR bridge surface redirect fails (null surface) | Medium | HIGH — immersive shows black | Bridge.initialize() depends on GL context from XR session. Log bridge.textureId and bridge.surface. Fix: ensure bridge.initialize() is called after EGL context is current. |
| Standard player fallback doesn't open as panel window | Low | Medium — user stuck in empty VrPlayerActivity | Check manifest merge — VrPlayerActivity must NOT have VR category. PlayerActivity should have no VR categories. |
| Quest doesn't pass controller buttons as KeyEvents in immersive | Medium | Low — user can't exit gracefully | Fallback: Quest system button (long-press Meta button) always works. Future fix: implement XrActionSet. |
| VR flavor APK doesn't install (missing permissions, wrong SDK) | Low | HIGH — can't test | Check compileSdk=35, minSdk=26 match Quest. Verify `com.oculus.supportedDevices` meta-data. |
| Render is upside-down or mirrored | Medium | Low — fixable | UV coordinates in VrStereoRenderer may need Y-flip for OpenXR convention. Quick fix in shader. |
| Video plays but only mono (no stereo separation) | Medium | Medium — defeats purpose | Check VrStereoRenderer.setStereoMode() is called with correct SBS/OU value. Log currentStereoMode in renderVrFrame. |
| Audio plays but video is frozen | Medium | Medium | Check bridge.updateFrame() is called. Verify SurfaceTexture.updateTexImage() runs on GL thread with the correct EGL context. |

---

## 11. Debugging Cheat Sheet

### Logcat filters for Day 1

```bash
# Full VR pipeline
adb logcat -s VR_BOOT:* VrPlayerActivity:* OpenXrNative:* OpenXR:* ActivityManager:*

# Just routing decisions
adb logcat | grep -E "route decision|standard-player fallback|starting XR init|xrCreateSession"

# Render loop health
adb logcat | grep -E "renderVrFrame|bridge NOT ready|bridge null|renderer null"

# XR session lifecycle
adb logcat | grep -E "xrCreateSession|xrDestroySession|initializeVrRenderPipeline|releaseVrRenderPipeline"
```

### Key log markers (healthy pipeline)

```
VR_BOOT     E  VrPlayerActivity.onCreate intent=...
VrPlayer..  I  VrPlayerActivity: isXrRuntimeAvailable=true
VrPlayer..  I  VrPlayerActivity: super.onCreate done
VrPlayer..  I  VrPlayerActivity: route decision file=X requested=AUTO effective=SBS autoDetect=true -> standard=false
VrPlayer..  I  VrPlayerActivity: starting XR init (reason=stereo-mode)
OpenXrNat.. I  OpenGL ES version range: min=3.1 max=3.2
OpenXrNat.. I  xrCreateSession: SUCCESS session=0x...
OpenXrNat.. I  Swapchain created: ...x... format=0x... images=3
VrPlayer..  I  VrPlayerActivity: initializeVrRenderPipeline START
VrPlayer..  I  VrPlayerActivity: bridge.initialize done — textureId=N surface=... isReady=true
VrPlayer..  I  VrPlayerActivity: ExoPlayer video redirected to VR bridge surface (textureId=N)
VrPlayer..  I  VrPlayerActivity: initializeVrRenderPipeline COMPLETE
VrPlayer..  D  VrPlayerActivity: renderVrFrame #1 eye=LEFT layer=QUAD_CINEMA stereo=SBS textureId=N
```

### If xrCreateSession fails

| Error code | Meaning | Fix |
|-----------|---------|-----|
| -50 | `XR_ERROR_GRAPHICS_REQUIREMENTS_CHECK_MISSING` | gfxReqs call not executing. Check native code path. |
| -7 | `XR_ERROR_FUNCTION_UNSUPPORTED` | `com.oculus.supportedDevices` missing or wrong in manifest |
| -1 | `XR_ERROR_VALIDATION_FAILURE` | EGL binding invalid. Check EGL display/context/config in logs |
| -9 | `XR_ERROR_HANDLE_INVALID` | System ID or instance handle corrupted |

---

## 12. Implementation Steps (Day 1 Morning)

### Step 1: Build VR flavor

```powershell
.\gradlew.bat assembleVrDebug 2>&1 | Tee-Object temp/build_vr_day1.log
```

If build fails → analyze `temp/build_vr_day1.log` → fix build errors first.

### Step 2: Verify APK contents

```powershell
$apk = (Get-ChildItem "app_v2/build/outputs/apk/vr/debug/*.apk")[0].FullName
Write-Host "APK: $apk"
Write-Host "Size: $((Get-Item $apk).Length / 1MB) MB"
```

### Step 3: Install on Quest 3

```powershell
adb install -r $apk
```

### Step 4: Start logcat capture

```powershell
adb logcat -c
adb logcat -v time | Tee-Object temp/quest_day1_full.log
```

(In a separate terminal or use `Start-Process`)

### Step 5: Execute test cases TC-01 through TC-08

Follow section 9.3. Record pass/fail for each.

### Step 6: Analyze results

```powershell
# After testing, analyze the captured log
.\scripts\utils\search-log.ps1 -LogFile "temp/quest_day1_full.log" -Errors
.\scripts\utils\search-log.ps1 -LogFile "temp/quest_day1_full.log" -Pattern "VR_BOOT|xrCreateSession|route decision" -Context 3
.\scripts\utils\search-log.ps1 -LogFile "temp/quest_day1_full.log" -Tag "VrPlayerActivity" -Level E
```

### Step 7: Fix and iterate

Based on test results:

- If panel mode works but immersive doesn't → focus on XR session init debugging
- If neither works → check manifest merge, verify app launches at all
- If everything works → celebrate, then add more test content

---

## 13. Potential Quick Fixes (ready to apply if needed)

### Fix A: UV Y-flip if video is upside-down

In `VrStereoRenderer.kt`, if the video appears upside down in immersive:

```kotlin
// OpenXR swapchain images use bottom-left origin; SurfaceTexture uses top-left.
// If image is flipped, invert V coordinates in the UV crop.
val vTop = 1.0f - cropRect.top
val vBottom = 1.0f - cropRect.bottom
```

### Fix B: Force MONO fallback if stereo detection fails

If SBS content is detected but renders as squished (both halves visible in each eye), check that `currentStereoMode` is actually set to `SBS_LEFT_RIGHT` and not `MONO`:

```
adb logcat | grep "stereoMode →"
```

### Fix C: Bridge timing — if surface is null at redirect

If `ExoPlayer video redirected to VR surface` never appears, but `initializeVrRenderPipeline START` does, the ExoPlayer instance may not be ready yet. Fix: add a retry with delay:

```kotlin
// In initializeVrRenderPipeline(), if exoPlayer is null:
if (exoPlayerInstance == null) {
    Timber.w("VrPlayerActivity: ExoPlayer not ready — scheduling retry in 500ms")
    window.decorView.postDelayed({ initializeVrRenderPipeline() }, 500)
    return
}
```

---

## 14. Acceptance Criteria

| # | Criterion | How to verify |
|---|-----------|--------------|
| 1 | VR APK installs on Quest 3 | `adb install` succeeds, app appears in Quest Library |
| 2 | App launches in panel mode | Tapping app icon shows BrowseActivity in floating window |
| 3 | Regular video plays in panel | Tap 2D video → plays in window with controls |
| 4 | Regular image opens in panel | Tap JPEG → image viewer in window |
| 5 | SBS video opens in immersive | Tap SBS video → immersive XR, stereo visible |
| 6 | User can exit immersive | Press B/X/Back → returns to panel |
| 7 | Settings accessible | Settings → Playback → Behaviour → "Disable 3D/VR" toggle visible |
| 8 | Kill-switch works | Enable "Disable 3D/VR" → SBS video opens in panel (standard player) |

**MVP success = criteria 1–3 + 6 pass. Criteria 4–5 are stretch goals for Day 1.**
**Full success = all 8 criteria pass.**

---

## 15. Architecture Decision Records

### ADR-MVP-1: No new code for Day 1 — validate existing

**Decision:** Day 1 focuses on building and testing existing code, not writing new features.

**Alternatives considered:**

- Write new routing/rendering code before testing → rejected (premature optimization; existing code is complete and untested)
- Rewrite VR pipeline using Meta Spatial SDK → rejected (adds dependency; existing OpenXR pipeline should work)

**Reason:** All routing, rendering, and error recovery code is already written. The gap is device validation, not missing code.

### ADR-MVP-2: Accept immersive-only for stereo content in MVP

**Decision:** Flat stereo (SBS/OU) goes to immersive mode, not panel mode.

**Alternatives considered:**

- Panel flat stereo via AI depth (Instagram-style) → rejected (closed Meta API, not applicable to SBS content)
- Panel flat stereo via custom rendering → rejected (per-eye rendering impossible in panel for third-party apps)

**Reason:** True SBS/OU stereo requires per-eye rendering, which is only possible in immersive OpenXR mode. This is acceptable for MVP.

### ADR-MVP-3: Android KeyEvent for controller input

**Decision:** Use `dispatchKeyEvent()` for controller buttons instead of OpenXR XrActionSet.

**Alternatives considered:**

- Full XrActionSet with interaction profiles → deferred (adds native code complexity; KeyEvent may work for basic buttons)

**Reason:** Quest maps controller buttons to Android KeyEvents for activities with `focusaware=true`. This may be sufficient for basic exit/menu operations. If it doesn't work on device, XrActionSet implementation follows in Phase F.

---

## 16. Out of Scope (future items)

- Flat stereo rendering in panel mode (requires Meta Spatial SDK cooperative mode or platform change)
- Passthrough background in immersive (void black is acceptable for MVP cinema experience)
- OpenXR XrActionSet for controller input (fallback to KeyEvent pipeline)
- 360° photo sphere rendering (only video in MVP)
- VR control overlay UI (QuadLayer-based settings panel in immersive)
- Meta Horizon Store submission
- Docs sync (FEATURES EN/RU/UK)

---

## 17. Post-Day 1: What's Next

Based on Day 1 results, the next priorities are:

1. **If immersive works:** Add passthrough toggle, improve cinema screen placement/size controls.
2. **If panel works but immersive doesn't:** Debug XR session with Meta developer tools, try on Quest 2 for comparison.
3. **If both work:** Move to Phase F (Menu button via XrActionSet), Phase G (position sync), then Meta Spatial SDK integration for hybrid app (cooperative mode).
4. **Long-term:** Meta Spatial SDK Hybrid App architecture for proper panel↔immersive transitions with cooperative mode (panel overlay inside immersive).

---

## 18. File Checklist

No code modifications planned. This spec is a validation + debug protocol.

Mandatory step checklist:

- [ ] VR flavor builds: `.\gradlew.bat assembleVrDebug`
- [ ] APK installed on Quest 3 via ADB
- [ ] Test cases TC-01 through TC-08 executed
- [ ] Results logged in `temp/quest_day1_full.log`
- [ ] Pass/fail documented
- [ ] `.\scripts\add_to_dev_log.ps1` run for this spec file
