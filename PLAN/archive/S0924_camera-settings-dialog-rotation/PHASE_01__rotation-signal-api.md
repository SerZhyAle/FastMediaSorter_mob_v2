# Phase 01 - Rotation signal API

**Strategic spec:** [`../S0924_camera-settings-dialog-rotation.md`](../S0924_camera-settings-dialog-rotation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** -
**Blocks:** Phase 02
**Steps done:** 0 / 2

---

## Objective

Give `CameraOrientationManager` a subscription API so a late/dynamic consumer (the settings dialog, created on demand) can observe the current `Surface.ROTATION_*` bucket. Today the manager takes two fixed lambdas in its constructor (`onIconRotationChanged`, `onTargetRotationChanged`) and exposes no getter or listener registration - a third consumer cannot subscribe. This phase adds that seam without disturbing the two existing consumers or introducing a second `OrientationEventListener`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Device/emulator attached (strategic §6) - this phase is compile-verifiable blind, but do not land it alone; it has no standalone value until Phase 02 consumes the API.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOrientationManager.kt` | Modified | ≤ 120 |

> Reference: research `research/01__rotation-mechanism.md` "Rotation infra" - manager at `CameraOrientationManager.kt:11-74`, owned once in `CameraCaptureActivity.initializeHelperManagers()` (`:205-209`), enabled/disabled symmetrically in `onResumeWithViews()`/`onPause()` (`:265-275`).

---

## Steps

### Step 01.1 - Expose current bucket as observable state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOrientationManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `StateFlow<Int>` (backed by a private `MutableStateFlow<Int>`) exposing the current `Surface.ROTATION_*` bucket - seed it with the manager's initial bucket value. In the same place the manager currently invokes the two fixed lambdas on a bucket change, also update the `MutableStateFlow`. Keep the two existing lambdas working unchanged (do not replace them - the dialog is a third consumer, not a substitute). Do not add a second `OrientationEventListener`; reuse the single existing one. No business logic beyond publishing the bucket. If any log is needed use Timber, not `android.util.Log`.

**Verification:**

- `Grep` - `StateFlow<Int>` (or the chosen public property) present in `CameraOrientationManager.kt`.
- `Grep` - both `onIconRotationChanged` and `onTargetRotationChanged` still invoked (existing consumers intact).
- `Grep -n "Log\.d\("` - zero hits in `CameraOrientationManager.kt`.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 01.2 - Compile-verify the API seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOrientationManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Confirm the added state property compiles and the manager's construction site in `CameraCaptureActivity.initializeHelperManagers()` still builds unchanged (no constructor-signature break for the two existing lambdas). Compile-only gate; no packaging needed.

**Verification:**

- Project compiles - `.\a.ps1 fk` (standard Kotlin compile) BUILD SUCCESSFUL.
- `Grep` - `CameraOrientationManager(` construction in `CameraCaptureActivity.kt` unchanged (still passes the two lambdas).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `a.ps1 fk` BUILD SUCCESSFUL.
- [ ] Two existing consumers (`onIconRotationChanged`, `onTargetRotationChanged`) unchanged.
- [ ] No second `OrientationEventListener` introduced.

---

## Handoff Notes to Next Phase

`CameraOrientationManager` now publishes the current `Surface.ROTATION_*` bucket as observable state. Phase 02's `CameraSettingsDialogRotationManager` subscribes to it. No behaviour change yet - the new state has no consumer until Phase 02.

---

## Rollback Plan

Revert the single-file change. The two original lambda consumers are untouched, so the camera overlay/target-rotation behaviour is unaffected by a rollback.
