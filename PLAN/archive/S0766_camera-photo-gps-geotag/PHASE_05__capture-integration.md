# Phase 05 - Capture integration & finalization

**Strategic spec:** [`../S0766_camera-photo-gps-geotag.md`](../S0766_camera-photo-gps-geotag.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, 02, 03, 04
**Blocks:** none
**Steps done:** 0 / 4

---

## Objective

Inject the location into the CameraX photo metadata so the captured JPEG carries GPS EXIF when the opt-in setting is on and location permission is granted. The host warms the location source while the camera is open, stamps the freshest fix at shutter (never blocking it), and stops the source on teardown. Digital-zoom crop already preserves GPS (S0765 `PRESERVED_EXIF_TAGS`). Video is excluded. Widget launches inherit the behavior (they route through the same activity).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 700 |

---

## Steps

### Step 05.1 - Accept a location in capture()

**Files:** `ui/cameracapture/helpers/CameraCaptureSessionManager.kt`

**Prompt for developer:**

> Back up the file to `temp/` first (>500 LOC). Change `capture(..)` to take a new nullable parameter `location: android.location.Location? = null` (place it after `outputFile`, keep the callbacks last). When `location != null`, build the output options with metadata: `val metadata = ImageCapture.Metadata().apply { this.location = location }` and `ImageCapture.OutputFileOptions.Builder(outputFile).setMetadata(metadata).build()`; otherwise keep the current bare builder. CameraX then writes the GPS EXIF into the JPEG before any crop. Do not touch the video path. Add a short S0766 comment on the metadata branch noting the crop path preserves GPS via `PRESERVED_EXIF_TAGS`.

**Verification:**

- `Grep` - `location: android.location.Location?` in `capture(` signature.
- `Grep` - `ImageCapture.Metadata()` and `.setMetadata(` present.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 05.2 - Host: read setting, warm location, pass at shutter

**Files:** `ui/cameracapture/CameraCaptureActivity.kt`

**Prompt for developer:**

> Inject the settings repository: `@Inject lateinit var settingsRepository: SettingsRepository`. Add `private val locationProvider = CameraLocationProvider()` and `private var geotagEnabled = false`.
>
> Add a private helper `hasLocationPermission()` returning true when `ACCESS_FINE_LOCATION` OR `ACCESS_COARSE_LOCATION` is granted.
>
> In `setupViews()`, after `flowManager.ensurePermissionAndBind(hasPermission)`, launch a `lifecycleScope` coroutine: `val enabled = settingsRepository.getSettings().first().cameraGeotagEnabled; geotagEnabled = enabled; if (enabled && hasLocationPermission()) locationProvider.start(this@CameraCaptureActivity)`. (Warm as early as possible so a fix is ready by first shutter.)
>
> In `capturePhoto()`, compute `val location = if (geotagEnabled && hasLocationPermission()) locationProvider.lastKnownLocation() else null` and pass it to `sessionManager.capture(previewView = .., outputFile = file, location = location, onSaved = .., onError = ..)`.
>
> In `onDestroy()`, call `locationProvider.stop()` (before `super.onDestroy()` is fine; must be symmetric with `start`).
>
> Add the debug-verification tag at the geotag entry point (final code edit before the phase build): `Timber.d("S0766: geotag enabled=%s perm=%s loc=%s", geotagEnabled, hasLocationPermission(), location != null)` inside `capturePhoto()` right after computing `location`.

**Verification:**

- `Grep` - `settingsRepository` injected; `CameraLocationProvider()` instantiated.
- `Grep` - `location = location` passed in the `sessionManager.capture(` call.
- `Grep` - `locationProvider.stop()` in `onDestroy`.
- `Grep` - exactly one `Timber.d("S0766:` tag present.
- `.\a.ps1 fc` - code + resources compile.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 05.3 - Build gate (standard debug)

**Files:** (verification only)

**Prompt for developer:**

> Build `standard debug` to prove the whole chain compiles and packages with the new permission, setting, provider, and capture wiring (and the inserted S0766 tag). `src/vr/` is untouched, so no VR build needed.

**Verification:**

- `.\a.ps1 dq` - `assembleStandardDebug` BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 05.4 - Capability inventory, catalog, dev log

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Record the shippable capability via `scripts/all_features/add.ps1` (EN-only): optional GPS geotagging of in-app camera photos, opt-in, default off, `spec=S0766`. Set `role`+`status` for the new `CameraLocationProvider` class via the catalog `set.ps1`, then `catalog_sync.ps1 -Module app_v2`. Batch one dev-log entry for the ticket. This is the `Implemented` transition; the device test (real GPS + camera) gates `Verified`.

**Verification:**

- `Grep` - `S0766` present in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` - exit 0.
- Catalog regenerated (`CameraLocationProvider` appears in `dev/CATALOG/app_v2.jsonl`).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Steps 05.1-05.4 are `[x] done`.
- [ ] `.\a.ps1 dq` BUILD SUCCESSFUL.
- [ ] Exactly one `Timber.d("S0766:` tag present (removed on transition out of `BlockNeedUserTest`).
- [ ] Video path untouched; geotag only on photo capture.
- [ ] Capability recorded in `docs/ALL_FEATURES.jsonl`.

---

## Device-test handoff (gates Verified)

On a device with a real GPS + camera:
1. Settings -> Operations -> Camera: enable "Geotag photos"; grant location permission when prompted.
2. Outdoors (or with a recent fix), open the in-app camera, take a photo.
3. Verify the saved JPEG carries GPS EXIF (lat/long) readable by a gallery/EXIF viewer.
4. Repeat with digital zoom (>1x) - GPS still present.
5. Toggle the setting off (or deny permission) - new photo has NO GPS, shutter not blocked.
6. Launch the camera from the quick-capture widget with the setting on - photo carries GPS.

---

## Rollback Plan

Restore the `CameraCaptureSessionManager` backup, revert the activity edits (injection, provider, capture arg, onDestroy, tag), delete `CameraLocationProvider`, and remove the manifest/setting/strings from earlier phases. The `cameraGeotagEnabled` flag is inert without the capture wiring.
