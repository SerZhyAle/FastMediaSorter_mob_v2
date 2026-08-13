# Phase 04 - Night mode

**Strategic spec:** [`../S0753_camera-zoom-presets-slider-night.md`](../S0753_camera-zoom-presets-slider-night.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/01__night-exposure-routes.md`](research/01__night-exposure-routes.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Add a photo-only night-mode toggle backed by the CameraX NIGHT extension, device-gated so it appears only where the active lens supports it (strategic ADR-4, §6.1, §6.4).

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `research/01__night-exposure-routes.md` Owner-decision section read (Route 1, photo-only, new `camera-extensions` dependency).
- [ ] `CameraCaptureActivity.kt` > 500 LOC - back up to `temp/` before editing (Step 04.6).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | (+1 line) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 330 |
| `app_v2/src/main/res/drawable/ic_camera_night_on.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_camera_night_off.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 670 |

> Camera capture is in `src/main`, shared by all flavors; the new dependency and toggle ship in standard/lite/photos/legacy uniformly (no flavor split). In photos there is no video, so the photo-only scope is moot there.
> **Landscape parity:** both layout variants edited in Step 04.6.

---

## Steps

### Step 04.1 - Add the CameraX extensions dependency

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> After the `androidx.camera:camera-video:1.5.3` line (around line 1250) add `implementation("androidx.camera:camera-extensions:1.5.3")`, matching the other camera artifacts' version. This is the only route that delivers an OEM night algorithm (research/01, owner-chosen Route 1).

**Verification:**

- `Grep` - `androidx.camera:camera-extensions:1.5.3` matches once in `build.gradle.kts`.
- `.\a.ps1 fk` compiles (exit 0) - dependency resolves.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (dependency line added after camera-video; `a.ps1 fk` resolved camera-extensions 1.5.3, 39s). Files: build.gradle.kts.

---

### Step 04.2 - Add `supportsNightMode` to the capability snapshot

**Files:** `CameraRuntimeCapabilities.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `val supportsNightMode: Boolean = false` to `CameraRuntimeCapabilities`. The session sets it via `.copy(...)` after probing, so the UI hides the toggle on lenses without the NIGHT extension - same capability-gating pattern as `hasFlashUnit` / `supportsZoom`.

**Verification:**

- `Grep` - `supportsNightMode: Boolean` matches once in `CameraRuntimeCapabilities.kt`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (field added; compiles with 04.3 `.copy(supportsNightMode = ..)`). Files: CameraRuntimeCapabilities.kt.

---

### Step 04.3 - Bind the NIGHT extension in the session

**Files:** `CameraCaptureSessionManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Import `androidx.camera.extensions.ExtensionMode` and `androidx.camera.extensions.ExtensionsManager`. Add `private var extensionsManager: ExtensionsManager? = null` and `var nightMode: Boolean = false`. In `bind()`, after the `ProcessCameraProvider` is obtained, obtain the extensions manager via `ExtensionsManager.getInstanceAsync(context, provider)` and store it before calling `bindToLifecycle` (chain the listener so the manager is ready first; on failure leave it null so night stays unsupported). In `bindToLifecycle()`: build `baseSelector` as today; compute `val nightAvailable = !videoMode && extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) == true`; pick `selector = if (nightMode && nightAvailable) extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT) else baseSelector`; after binding set `capabilities = probe.probe(boundCamera, lensFacing, availableLensFacings).copy(supportsNightMode = nightAvailable)`. Add `fun applyNightMode(enabled: Boolean)` mirroring `applyMode`: no-op if unchanged or unbound; set `nightMode = enabled`; rebind. In `applyMode(videoMode)`, when switching to video set `nightMode = false` (NIGHT is photo-only). Keep `@SuppressLint("MissingPermission")` where rebinding.

**Verification:**

- `Grep` - `ExtensionsManager.getInstanceAsync` and `getExtensionEnabledCameraSelector` and `ExtensionMode.NIGHT` each present in `CameraCaptureSessionManager.kt`.
- `Grep` - `fun applyNightMode(` matches once; `.copy(supportsNightMode = nightAvailable)` present.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (ExtensionsManager.getInstanceAsync in bind; isExtensionAvailable + getExtensionEnabledCameraSelector + ExtensionMode.NIGHT in bindToLifecycle; applyNightMode; applyMode resets nightMode in video; `a.ps1 fk` SUCCESSFUL). Files: CameraCaptureSessionManager.kt.

---

### Step 04.4 - Track night-mode intent in the flow manager

**Files:** `CameraCaptureFlowManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add `var nightModeEnabled: Boolean = false  private set`. Add `fun onNightModeToggle(): Boolean`: `if (!currentCapabilities.supportsNightMode) return false`; flip `nightModeEnabled`; `session.applyNightMode(nightModeEnabled)`; return `nightModeEnabled`. In `onCapabilitiesChanged`, after the zoom resets, reconcile the toggle with the rebind result: `nightModeEnabled = session.nightMode && capabilities.supportsNightMode` (a rebind for a night toggle must not silently clear the icon, but a lens that loses the extension must). This requires `session.nightMode` to be readable - it is a public `var` from Step 04.3.

**Verification:**

- `Grep` - `fun onNightModeToggle(` matches once; `nightModeEnabled = session.nightMode` present in `onCapabilitiesChanged`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (nightModeEnabled field; onNightModeToggle; reconcile `session.nightMode && supportsNightMode` in onCapabilitiesChanged). Files: CameraCaptureFlowManager.kt.

---

### Step 04.5 - Add night-mode icons and label

**Files:** `app_v2/src/main/res/drawable/ic_camera_night_on.xml`, `app_v2/src/main/res/drawable/ic_camera_night_off.xml`, `app_v2/src/main/res/values/strings.xml` (+ `values-ru/strings.xml`, `values-uk/strings.xml`)
**Depends on:** Step 04.4

**Prompt for developer:**

> Create two 24dp vector drawables mirroring the flash pair (`ic_camera_flash_on/off`): a moon glyph for `ic_camera_night_on` (filled/active) and `ic_camera_night_off` (outline/inactive), `android:tint` left to the layout `iconTint`. Add the string key `camera_control_night` ("Night mode") across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key camera_control_night -En "Night mode" -Ru "Ночной режим" -Uk "Нічний режим"`. Used for the toggle `contentDescription` and `tooltipText`. No unsupported-device error string is needed - the toggle is hidden when unsupported.

**Verification:**

- `Glob` - both `ic_camera_night_on.xml` and `ic_camera_night_off.xml` exist.
- `Grep` - `camera_control_night` present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_control_night"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (ic_camera_night_on/off created; camera_control_night added via set-android-string.ps1 -Action add EN/RU/UK; check_strings_localized EN/RU/UK = OK, exit 0). Files: ic_camera_night_on.xml, ic_camera_night_off.xml, strings.xml + values-ru/uk.

---

### Step 04.6 - Add the toggle to the top bar and wire it

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`, `app_v2/src/main/res/layout-land/activity_camera_capture.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> In both layouts add a `MaterialButton` `@+id/btnCameraNight` in `cameraTopBar` next to `btnCameraFlash`, cloning the flash button's style/size/stroke/elevation (Phase 03), `app:icon="@drawable/ic_camera_night_off"`, `android:contentDescription="@string/camera_control_night"`, `android:tooltipText="@string/camera_control_night"`, `android:visibility="gone"` (`tools:visibility="visible"`), focusable. Back up `CameraCaptureActivity.kt` to `temp/` (Rule 5), then: in `setupCameraControls()` add `binding.btnCameraNight.setOnClickListener { val on = flowManager.onNightModeToggle(); binding.btnCameraNight.setIconResource(if (on) R.drawable.ic_camera_night_on else R.drawable.ic_camera_night_off) }`. In `renderCapabilities()` set `binding.btnCameraNight.visibility = if (capabilities.supportsNightMode && !flowManager.isVideoMode) View.VISIBLE else View.GONE`, and set its icon from `flowManager.nightModeEnabled` so a reconciled-off state shows the off icon. Ensure `applyCaptureModeUi()` re-hides it when video mode is active.

**Verification:**

- `Grep` - `@+id/btnCameraNight` matches once in each layout file.
- `Grep` - `btnCameraNight.setOnClickListener` and `onNightModeToggle()` present in `CameraCaptureActivity.kt`.
- `Grep` - `supportsNightMode && !flowManager.isVideoMode` present in `renderCapabilities`.
- `Glob` - fresh `temp/CameraCaptureActivity.kt.*.bak` exists.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification PASS (btnCameraNight 1x in each layout cloning flash; setOnClickListener -> onNightModeToggle + icon swap; renderCapabilities gates on supportsNightMode && !isVideoMode; applyCaptureModeUi hides in video; backup CameraCaptureActivity.kt.20260627_232613.bak; full `a.ps1 d` BUILD SUCCESSFUL). Files: layout/ + layout-land/ activity_camera_capture.xml, CameraCaptureActivity.kt.

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] Project compiles - full `a.ps1 d` (assembleStandardDebug) BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `Timber.d("S0753:` tag inserted at the night-toggle flow entry as the last code edit before the final build (per /spec-dev Final-phase debug-tag insertion); ticket flips to `BlockNeedUserTest` in Phase 05. Exactly one tag.
- [~] Dev log entry - batched into Phase 05 finalization per CLAUDE.md.
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to Phase 05 (once per ticket).

---

## Handoff Notes to Next Phase

Night mode is device-gated and photo-only; the toggle inherits Phase 03 styling. Phase 05 records the capability, regenerates the catalog, runs the string audit, and the ticket transitions to `BlockNeedUserTest` (real-device verification - AVD is insufficient for camera, per S0545).

---

## Rollback Plan

Revert the phase commit, restore `CameraCaptureActivity.kt` from the `temp/` backup, and drop the `camera-extensions` dependency line. No persisted state or migration involved.
