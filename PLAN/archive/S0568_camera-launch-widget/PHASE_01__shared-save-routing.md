# Phase 01 - Shared public-folder save routing

**Strategic spec:** [`../S0568_camera-launch-widget.md`](../S0568_camera-launch-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-20
**Completed:** 2026-06-20 (commit ab3f5d02)

---

## Objective

Extract the "persist a captured file to its public folder by media kind" routing into a reusable `SaveCapturedMediaUseCase` and route the existing main-menu camera manager through it - a single source of truth for public-folder capture saves, with no behavior change.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveCapturedMediaUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt` | Modified | ≤ 230 |

---

## Steps

### Step 01.1 - Create SaveCapturedMediaUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveCapturedMediaUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `SaveCapturedMediaUseCase` with `@Inject constructor(@ApplicationContext context: Context, cameraCaptureSaver: CameraCaptureSaver)` (no new Hilt module - constructor injection only). Expose `suspend operator fun invoke(captured: File, isVideo: Boolean): SaveResult`. Resolve the target exactly as `MainCameraCaptureManager` does today: photo -> `CameraCaptureTarget.CameraFolder`; video -> a `CameraCaptureTarget.Resource(id = -1L, name = Environment.DIRECTORY_MOVIES, path = CaptureDestinationPolicy.resolveVideoDestination(null).absolutePath, type = ResourceType.LOCAL)` (call `.mkdirs()` on the dir first). Call `cameraCaptureSaver.save(captured, captured.name, target) { _, _, _ -> false }` (public-folder save uses no network upload hook) and return its `SaveResult`. No Timber tags - this is permanent shared logic.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveCapturedMediaUseCase.kt` exists.
- `Grep` - `class SaveCapturedMediaUseCase` matches once.
- `Grep` - `suspend operator fun invoke(` present.
- `Grep` - `cameraCaptureSaver.save(` present.

**Status:** `[x]` done

---

### Step 01.2 - Route MainCameraCaptureManager through the use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `saveCapturedMedia: SaveCapturedMediaUseCase` constructor parameter and wire it at the call site (the host activity that constructs the manager). In `handleResult`, replace the inline target resolution + `cameraCaptureSaver.save(...)` block with `saveCapturedMedia(captured, isVideo)`; remove the now-unused private `moviesTarget()` helper and the direct `cameraCaptureSaver` field if nothing else uses it. Keep all existing behavior (session checks, `clearPending`, snackbar messages on each `SaveResult` branch) identical. Do NOT remove or alter the existing `Timber.d("S0523:` and `Timber.d("S0563:` probe lines - both tickets are still `BlockNeedUserTest`.

**Verification:**

- `Grep` - `saveCapturedMedia(captured` present in `MainCameraCaptureManager.kt`.
- `Grep` - `private fun moviesTarget` returns zero hits in `MainCameraCaptureManager.kt`.
- `Grep` - `Timber.d("S0523:` and `Timber.d("S0563:` each still present (probes preserved).
- `/build` - `standard debug` compiles (the manager's constructor call site updated).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - validated in commit ab3f5d02 (`standard debug`).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new `SaveCapturedMediaUseCase` class) - Phase 05 catalog sync.

---

## Handoff Notes to Next Phase

`SaveCapturedMediaUseCase` is the shared public-folder save engine. Phase 02's widget manager injects it instead of duplicating the routing.

---

## Rollback Plan

Revert phase commit(s). No data migration or user-facing surface changed - the use case is behavior-preserving extraction.
