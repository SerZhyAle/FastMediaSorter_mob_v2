# Phase 02 — VR Manifest + Hilt Binding

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Declare `horizonos.permission.HEADSET_CAMERA` in the VR AndroidManifest; create `VrBrowsePassthroughCaptureManager` skeleton (Camera2 availability detection only — no capture yet); bind it as `BrowsePassthroughCaptureProvider` via Hilt in `VrModule`. After this phase the camera button appears on Quest 3 (Horizon OS v74+) with a valid `isAvailable()` result; tapping it is a no-op until Phase 03.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/AndroidManifest.xml` | Modified | current + 6 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt` | New | ≤ 80 (skeleton) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt` | Modified | current + 8 |

---

## Steps

### Step 02.1 — Declare `HEADSET_CAMERA` permission in VR manifest

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/vr/AndroidManifest.xml`, add the following two entries **after** the existing `com.oculus.permission.HAND_TRACKING` `<uses-permission>` line:
>
> ```xml
> <!-- S0058: Meta passthrough camera — user-grantable dangerous permission (Horizon OS v74+) -->
> <uses-permission android:name="horizonos.permission.HEADSET_CAMERA" />
>
> <!-- Declare camera feature as optional so the app installs on non-Quest Android devices -->
> <uses-feature
>     android:name="android.hardware.camera2"
>     android:required="false" />
> ```

**Verification:**

- `Grep` — `horizonos.permission.HEADSET_CAMERA` present in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — `android.hardware.camera2` present in `app_v2/src/vr/AndroidManifest.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: vr/AndroidManifest.xml (modified). Dev log recorded.

---

### Step 02.2 — Create `VrBrowsePassthroughCaptureManager` skeleton with `isAvailable()`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `VrBrowsePassthroughCaptureManager.kt` in the `vr/capture` package. The class must:
>
> 1. Have `@Inject constructor(@ApplicationContext private val appContext: Context)`.
> 2. Implement `BrowsePassthroughCaptureProvider`.
> 3. Implement `isAvailable(context: Context): Boolean` using Camera2 metadata detection:
>
> ```kotlin
> override fun isAvailable(context: Context): Boolean = try {
>     val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
>     val sourceKey = CameraCharacteristics.Key(
>         "com.meta.extra_metadata.camera_source",
>         Int::class.javaObjectType,
>     )
>     cameraManager.cameraIdList.any { id ->
>         cameraManager.getCameraCharacteristics(id).get(sourceKey) == 0
>     }
> } catch (_: Exception) {
>     false
> }
> ```
>
> 4. Stub `launch()` body: `Timber.w("S0058: passthrough launch not yet implemented")` (Phase 03 fills this in).
>
> Imports needed: `android.content.Context`, `android.hardware.camera2.CameraCharacteristics`, `android.hardware.camera2.CameraManager`, `androidx.fragment.app.FragmentActivity`, `com.sza.fastmediasorter.domain.model.MediaResource`, `com.sza.fastmediasorter.ui.browse.managers.BrowsePassthroughCaptureProvider`, `dagger.hilt.android.qualifiers.ApplicationContext`, `timber.log.Timber`, `javax.inject.Inject`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt` exists.
- `Grep` — `class VrBrowsePassthroughCaptureManager` present in that file.
- `Grep` — `com.meta.extra_metadata.camera_source` present in that file.
- `Grep` — `fun isAvailable` present in that file.
- `Grep` — `Log\.d\(` — zero hits in that file (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 5/5 PASS. Files: vr/capture/VrBrowsePassthroughCaptureManager.kt (new, 40 LOC). Dev log recorded.

---

### Step 02.3 — Bind `VrBrowsePassthroughCaptureManager` in `VrModule`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `VrModule`, add a `@Singleton @Binds` method that binds `VrBrowsePassthroughCaptureManager` as `BrowsePassthroughCaptureProvider`:
>
> ```kotlin
> @Singleton
> @Binds
> abstract fun bindPassthroughCaptureProvider(
>     impl: VrBrowsePassthroughCaptureManager,
> ): BrowsePassthroughCaptureProvider
> ```
>
> Add the two imports: `com.sza.fastmediasorter.ui.browse.managers.BrowsePassthroughCaptureProvider` and `com.sza.fastmediasorter.vr.capture.VrBrowsePassthroughCaptureManager`.

**Verification:**

- `Grep` — `bindPassthroughCaptureProvider` present in `VrModule.kt`.
- `Grep` — `BrowsePassthroughCaptureProvider` present in `VrModule.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: vr/di/VrModule.kt (modified). Dev log recorded.

---

### Step 02.4 — Verify VR build: button visible on availability probe

**Files:** _(no code change — build-verification step)_
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `/build` to confirm the VR debug build compiles cleanly. Verify:
>
> - No duplicate Hilt binding errors for `BrowsePassthroughCaptureProvider`.
> - No `@BindsOptionalOf` resolution errors.
>
> On-device verification (if Quest 3 hardware available): camera button appears in Browse resource ops menu when passthrough camera IDs are enumerable. Tapping the button logs `S0058: passthrough launch not yet implemented`.

**Verification:**

- VR build compiles without Hilt binding errors (`/build` exits 0 for `vrDebug`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Build exit code 0. No Hilt duplicate binding errors.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `horizonos.permission.HEADSET_CAMERA` declared in VR manifest.
- `VrBrowsePassthroughCaptureManager.isAvailable()` functional; `launch()` is a stub.
- Hilt graph compiles cleanly for all flavors (Optional empty on non-VR).
- Phase 03 implements the Camera2 permission flow + JPEG capture.

---

## Rollback Plan

Revert phase commit(s) — no data migration. Non-VR builds are unaffected.
