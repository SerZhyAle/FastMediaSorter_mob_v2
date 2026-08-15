# Phase 03 - Main-menu unified "Camera" entry

**Strategic spec:** [`../S0563_camera-unified-entry-mode-switch.md`](../S0563_camera-unified-entry-mode-switch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4

---

## Objective

Replace the main-activity overflow "Photo" and "Video" actions with one "Camera" action that launches
the host in switchable mode (when both modes are available) and saves the result by the actually
captured media kind. The voice action and all fixed-mode entry points stay as they are.

---

## Files Touched

| File | New / Modified |
|------|:--------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainQuickCaptureMenuManager.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified |
| `app_v2/src/main/res/values/strings.xml` | Modified |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified |

---

## Steps

### Step 3.1 - Single switchable launch + generic result in MainCameraCaptureManager

**File:** `MainCameraCaptureManager.kt`

**Prompt for developer:**

> Replace `capturePhoto()` and `captureVideo()` with one
> `captureCamera(photoAvailable: Boolean, videoAvailable: Boolean)`:
> - Guard: require `FEATURE_CAMERA_ANY`; for video availability also keep the existing
>   `BrowseCameraCaptureManager.hasVideoCaptureHandler` hardware check.
> - Compute `initialMode = if (photoAvailable) PHOTO else VIDEO` and
>   `allowSwitch = photoAvailable && videoAvailable`.
> - Create a scratch dir (e.g. `getExternalFilesDir(null)` + `/Capture`, `mkdirs`) and a basename
>   `CAP_<timestamp>` (no extension). Store them as `pendingDir`/`pendingBaseName` for cleanup.
> - Launch `CameraCaptureContract.createSwitchableIntent(activity, dir, basename, initialMode,
>   allowSwitch)` through the shared launcher (reuse the existing `dispatch` try/catch shape).
> - Drop the `pendingIsVideo` field. In `handleResult`, on `RESULT_OK` read
>   `mediaKind = CameraCaptureContract.readResultMediaKind(result.data)` and
>   `path = CameraCaptureContract.readResultOutputPath(result.data)`; resolve the saved file from
>   `path` (fall back to `pendingDir/pendingBaseName + ext(mediaKind)`), pick target =
>   `if (mediaKind == VIDEO) moviesTarget() else CameraCaptureTarget.CameraFolder`, and save via the
>   existing `cameraCaptureSaver.save`. On cancel/failure, delete any leftover capture file. Replace
>   the `S0523:` Timber tags with a single permanent non-ticket log line if one is needed, or drop
>   them - no `S0563:` permanent logs (CLAUDE.md §2).

**Verification:**

- `Grep` - `fun captureCamera` matches once; `fun capturePhoto`/`fun captureVideo` removed.
- `Grep` - `readResultMediaKind` and `readResultOutputPath` used in `handleResult`.
- `Grep` - `pendingIsVideo` no longer present.
- `Grep` - `createSwitchableIntent` present.

**Status:** `[ ]` not done

---

### Step 3.2 - Merge the menu items in MainQuickCaptureMenuManager

**File:** `MainQuickCaptureMenuManager.kt`

**Prompt for developer:**

> Collapse the photo+video pair into one "Camera" entry while keeping voice separate. Constructor
> becomes `(onVoice, onCamera)`. `itemCount(voice, camera)` and
> `populate(popup, voice, camera, startOrder)` add a `MENU_ITEM_QUICK_CAMERA` item with
> `R.string.quick_camera_menu_label` and `R.drawable.ic_camera_capture` when `camera` is true.
> `handleMenuItem` routes `MENU_ITEM_QUICK_CAMERA -> onCamera()`. Keep ids clear of existing menu ids;
> retire `MENU_ITEM_QUICK_VIDEO`/`MENU_ITEM_QUICK_PHOTO`.

**Verification:**

- `Grep` - `MENU_ITEM_QUICK_CAMERA` present; `MENU_ITEM_QUICK_PHOTO`/`MENU_ITEM_QUICK_VIDEO` removed.
- `Grep` - constructor exposes `onCamera` and not `onPhoto`/`onVideo`.

**Status:** `[ ]` not done

---

### Step 3.3 - Wire the single "Camera" callback and combined gating in MainActivity

**File:** `MainActivity.kt`

**Prompt for developer:**

> Update the `MainQuickCaptureMenuManager` construction to `onVoice` + `onCamera = {
> cameraCaptureManager.captureCamera(isQuickPhotoEnabled && mediaCapabilities.supportsImages,
> isQuickVideoEnabled && mediaCapabilities.supportsVideo) }`. Update `getMainWindowDropdownMenuItemCount`
> and `populateMainWindowDropdownMenu` to pass a single combined `camera` flag =
> `(isQuickPhotoEnabled && supportsImages) || (isQuickVideoEnabled && supportsVideo)` to
> `quickCaptureMenuManager.itemCount(voice, camera)` / `.populate(popup, voice, camera, startOrder)`.
> Keep the voice flag unchanged. Do not add net new lines that push the file over 1500 LOC - the merge
> nets two callbacks into one, so it should be neutral-to-negative; if it trends over budget, extract
> the dropdown-menu block per the parked finding rather than inlining more.

**Verification:**

- `Grep` - `captureCamera(` present in `MainActivity.kt`; `capturePhoto()`/`captureVideo()` callbacks removed.
- `Grep` - `quickCaptureMenuManager.itemCount(` and `.populate(` pass the combined camera flag.
- `MainActivity.kt` line count stays <= 1500.

**Status:** `[ ]` not done

---

### Step 3.4 - Camera menu label string + dead-string cleanup, device-test tags, build

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`, plus the touched Kotlin

**Prompt for developer:**

> Add `quick_camera_menu_label` in EN/RU/UK via `set-android-string.ps1 -Action add` (EN "Camera",
> RU "Камера", UK "Камера"). After the menu merge, grep the codebase for `quick_photo_menu_label` and
> `quick_video_menu_label`; if orphaned, remove them across all three locales (CLAUDE.md Rule 20
> dead-weight hygiene). Insert exactly one `Timber.d("S0563: unified camera entry mode=..")` tag at the
> `captureCamera` entry point (device-test probe, removed when leaving `BlockNeedUserTest`). Build with
> `.\a.ps1 d`.

**Verification:**

- `pwsh scripts/check_strings_localized.ps1 -KeyPrefix "quick_camera_menu_label"` exits 0.
- `Grep` - orphaned `quick_photo_menu_label`/`quick_video_menu_label` removed (or proven still used).
- `Grep` - exactly one `Timber.d("S0563:` tag present in `MainCameraCaptureManager.kt`.
- `.\a.ps1 d` produces a debug APK.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` is `[x] done`.
- [ ] `.\a.ps1 d` builds the standard debug APK.
- [ ] Voice action and all fixed-mode entry points unchanged.
- [ ] Status set to `BlockNeedUserTest` with the device-test note.

---

## Rollback Plan

Revert phase commit(s) - menu/call-site migration only, no schema or storage-format change.
