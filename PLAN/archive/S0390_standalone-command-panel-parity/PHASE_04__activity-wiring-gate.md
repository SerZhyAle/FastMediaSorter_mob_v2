# Phase 04 - Activity wiring + capability gate

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** 01, 02, 03
**Blocks:** 05

## Objective

Wire `PhotoVideoStandaloneActivity` to the controller + VM state; gate Group A visibility on `editableImageFile` × `supportsTypeSpecificActions` × accelerometer (rotate only).

## Steps

### Step 04.1 - reloadImage on StandaloneViewManager

**Files:** `app_v2/.../ui/player/helpers/StandaloneViewManager.kt`

- Add `fun reloadImage(mediaFile: MediaFile)`: Glide load with `skipMemoryCache(true)` + `diskCacheStrategy(NONE)` + a fresh `signature` so an in-place-cropped file re-decodes. Used only by the crop in-place re-render.

**Verification:** `Grep` - `fun reloadImage` present with `skipMemoryCache`.

### Step 04.2 - Activity wiring

**Files:** `app_v2/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt`

- Inject `FileOperationUseCase`; construct `ImageCropManager(this, lifecycleScope, fileOperationUseCase)` + `StandaloneImageEditController` (lazy).
- Add `ScreenRotationManager`; cache `hasAccelerometer`.
- Bar buttons: `btnEditCrop` → `controller.enterCropMode(CROP)`; `btnEditRotate` → `viewModel.toggleRotationSensor()`.
- Overflow: handle `menu_edit_crop_to_file` → `enterCropMode(CROP_TO_FILE)`; `menu_edit_compress` → `controller.startCompressedCopy()`. Show those two items only when editable.
- `observeData`: collect `editableImageFile` → toggle `btnEditCrop` (and editable flag for overflow) visible only when non-null AND `supportsTypeSpecificActions`; `btnEditRotate` visible when editable AND `hasAccelerometer`.
- Collect `rotationSensorEnabled` → `screenRotationManager.apply(this, followSystem=false, sensorEnabled=it, hasAccelerometer)`; swap icon `ic_rotation_unlocked`/`ic_rotation_locked` + contentDescription.
- `onInPlaceCropSaved` → `viewManager.reloadImage(currentFile)`.
- One debug tag: `Timber.d("S0390: standalone Group A gate editable=..")` at the gate entry.

**Verification:**

- `Grep` - `enterCropMode`, `toggleRotationSensor`, `reloadImage`, `screenRotationManager` wired.
- `Grep` - exactly one `Timber.d("S0390:` in the activity.
- No `BuildConfig.IS_*` flavor guard added (Rule 14).

## Phase Done Criteria

- [ ] `standard` + `photos` debug compile.
