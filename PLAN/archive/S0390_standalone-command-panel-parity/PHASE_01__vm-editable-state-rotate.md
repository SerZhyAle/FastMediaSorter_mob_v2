# Phase 01 - VM editable-image state + screen-rotate toggle

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** 02, 03, 04

## Objective

Give `StandalonePlayerViewModel` the state needed to gate Group A: a `StateFlow<MediaFile?>` of the current file resolved to a writable local image path (null when not a static-bitmap image or not local), plus a screen-rotation sensor toggle mirroring in-app `ROTATION_TOGGLE`.

## Steps

### Step 01.1 - editableImageFile state

**Files:** `app_v2/.../ui/player/StandalonePlayerViewModel.kt`

- Add `editableImageFile: StateFlow<MediaFile?>` backed by a `MutableStateFlow`.
- Set it when the current file changes: a static-bitmap image (`MediaType.IMAGE`) that resolves to a local path. For folder-neighbour files (path already starts with `/`) use the file directly; for the initially-opened content URI reuse the `ResolveLocalPathFromUriUseCase` result already computed in `initFolderPaging` (Local → `MediaFile.copy(path = absolutePath)`); else null.
- GIF excluded (separate `MediaType.GIF`).

**Verification:**

- `Grep` - `editableImageFile` present; set in both the URI-resolution path and `publishCurrentFile`.
- `Grep -n "Log\.d\("` zero hits in the file (Timber only).

### Step 01.2 - rotation sensor toggle

**Files:** `app_v2/.../ui/player/StandalonePlayerViewModel.kt`

- Add `rotationSensorEnabled: StateFlow<Boolean>` (default false) + `fun toggleRotationSensor()` flipping it.
- VM holds no Activity ref; the activity observes the flag and applies it via `ScreenRotationManager` (Phase 04).

**Verification:**

- `Grep` - `toggleRotationSensor` + `rotationSensorEnabled` present.

## Phase Done Criteria

- [ ] Compiles (`/build`).
- [ ] No `Log.d`; Timber only.
