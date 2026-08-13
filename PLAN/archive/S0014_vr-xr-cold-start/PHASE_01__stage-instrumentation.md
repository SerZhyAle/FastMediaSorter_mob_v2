# Phase 01 — Stage Instrumentation

**Strategic spec:** [`../S0014_vr-xr-cold-start.md`](../S0014_vr-xr-cold-start.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 4 / 4
**Started:** 2026-04-27
**Completed:** 2026-04-27

---

## Objective

Add per-stage wall-clock timing markers to `OpenXrSessionManager.initialize()` and `VrPlayerActivity`'s cold-start path so that a single device logcat run reveals the duration of each stage: EGL create, native XR init, GL bridge/renderer init, HUD swapchain, panel swapchain, and first usable VR frame.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (N/A — foundation phase)
- [ ] Working tree is clean or on a feature branch.
- [ ] Quest 3 device available for Phase 02 (not needed for this phase itself).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 590 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1790 |

> `OpenXrSessionManager.kt` is currently 563 lines (> 500) — backup required before edit.
> `VrPlayerActivity.kt` is currently 1767 lines (> 1000). Per CLAUDE.md, files > 1500OC must be split before editing; the split is tracked in `PLAN/spec_decompose-giant-files.md`. For this phase only, a narrow exception applies: changes are limited to 2 `@Volatile` field declarations and 7 `Timber.i` call-sites — no new business logic. A timestamped backup is mandatory.

---

## Steps

### Step 01.1 — Backup both source files

**Files:** `temp/` directory
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` to
> `temp/OpenXrSessionManager_backup_<YYYYMMDD>.kt` and
> `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` to
> `temp/VrPlayerActivity_backup_<YYYYMMDD>.kt`.

**Verification:**

- `Glob` — `temp/OpenXrSessionManager_backup_*.kt` returns at least one match.
- `Glob` — `temp/VrPlayerActivity_backup_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 2/2 PASS. Files: temp/OpenXrSessionManager_backup_20260427.kt, temp/VrPlayerActivity_backup_20260427.kt.

---

### Step 01.2 — Add elapsed-time markers in OpenXrSessionManager.initialize()

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside the render-thread lambda in `initialize()`, add `SystemClock.uptimeMillis()` checkpoints
> immediately before and after the three main blocking operations, and emit `Timber.i` lines tagged
> `VR_PERF` with the elapsed milliseconds. Exact insertion points:
>
> 1. Before `val egl = XrEglContext()`: add `val tInit = SystemClock.uptimeMillis()`.
> 2. After `Timber.i("OpenXrSessionManager: EGL context created OK")`: add
>    `Timber.i("VR_PERF: [xr-thread] egl_create=%dms", SystemClock.uptimeMillis() - tInit)`.
> 3. Add `val tNative = SystemClock.uptimeMillis()` just before `OpenXrNative.nativeInitialize(activity, callback)`.
> 4. After `Timber.i("OpenXrSessionManager: nativeInitialize returned %b", result)`: add
>    `Timber.i("VR_PERF: [xr-thread] native_init=%dms  cumulative=%dms", SystemClock.uptimeMillis() - tNative, SystemClock.uptimeMillis() - tInit)`.
> 5. Add `val tReady = SystemClock.uptimeMillis()` just before `onSessionReady?.invoke()`.
> 6. After `Timber.i("OpenXrSessionManager: onSessionReady completed OK")`: add
>    `Timber.i("VR_PERF: [xr-thread] session_ready_cb=%dms  cumulative=%dms", SystemClock.uptimeMillis() - tReady, SystemClock.uptimeMillis() - tInit)`.
>
> Import `android.os.SystemClock` if not already present.

**Verification:**

- `Grep "VR_PERF"` in `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` returns exactly 3 matches.
- `Grep "SystemClock.uptimeMillis"` in that file returns at least 4 matches (tInit + tNative + tReady + elapsed calls).
- `Grep "Log\.d\("` in that file returns 0 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 3/3 PASS. Files: OpenXrSessionManager.kt (+7 lines). Dev log pending end of phase.

---

### Step 01.3 — Add timing fields to VrPlayerActivity

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the class body of `VrPlayerActivity`, alongside the existing `@Volatile` fields (e.g. near
> `xrInitializationRequested`), add two new fields:
>
> ```kotlin
> @Volatile private var xrInitStartedAtMs = 0L
> @Volatile private var vrFirstFrameLoggedMs = 0L
> ```
>
> In `startXrInitialization()`, immediately after `xrInitializationRequested = true`, set:
> `xrInitStartedAtMs = SystemClock.uptimeMillis()`.
> Add `Timber.i("VR_PERF: [main] xr_init_requested  t=%d", xrInitStartedAtMs)`.
>
> Import `android.os.SystemClock` if not already present.

**Verification:**

- `Grep "xrInitStartedAtMs"` in `VrPlayerActivity.kt` returns at least 3 matches (declaration + assignment + usage in next step).
- `Grep "vrFirstFrameLoggedMs"` returns at least 2 matches (declaration + usage in next step).
- `Grep "Log\.d\("` in that file returns 0 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 3/3 PASS. Fields declared; xrInitStartedAtMs usage ×3, vrFirstFrameLoggedMs usage completes in Step 01.4. Log.d = 0. Files: VrPlayerActivity.kt (+7 lines).

---

### Step 01.4 — Add timing in initializeVrRenderPipeline and renderVrFrame

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `initializeVrRenderPipeline()` (on the GL thread), add a local `val tPipeline0 = SystemClock.uptimeMillis()` at the very start of the function body, then add the following `Timber.i` calls after each major step. Use `xrInitStartedAtMs` as the absolute reference for "time since XR init was requested". All tag strings use `VR_PERF`:
>
> - After `bridge.initialize()` completes and before `renderer.initGl()`:
>   `Timber.i("VR_PERF: [gl-thread] bridge_init=%dms  abs=%dms", SystemClock.uptimeMillis()-tPipeline0, SystemClock.uptimeMillis()-xrInitStartedAtMs)`
>
> - After `photoRenderer?.initGl()` and before `syncVrPlayerBindingToBridgeSurface(...)`:
>   `Timber.i("VR_PERF: [gl-thread] renderers_init=%dms  abs=%dms", SystemClock.uptimeMillis()-tPipeline0, SystemClock.uptimeMillis()-xrInitStartedAtMs)`
>
> - After the HUD `if (sessionMgr != null)` block (after the `else` branch for fallback too):
>   `Timber.i("VR_PERF: [gl-thread] hud_swapchain=%dms  abs=%dms", SystemClock.uptimeMillis()-tPipeline0, SystemClock.uptimeMillis()-xrInitStartedAtMs)`
>
> - After the panel `if (sessionMgr != null)` block:
>   `Timber.i("VR_PERF: [gl-thread] panel_swapchain=%dms  abs=%dms", SystemClock.uptimeMillis()-tPipeline0, SystemClock.uptimeMillis()-xrInitStartedAtMs)`
>
> The final `Timber.i("VrPlayerActivity: initializeVrRenderPipeline COMPLETE")` already exists — add an absolute elapsed after it:
>   `Timber.i("VR_PERF: [gl-thread] pipeline_total=%dms  abs_from_init=%dms", SystemClock.uptimeMillis()-tPipeline0, SystemClock.uptimeMillis()-xrInitStartedAtMs)`
>
> In `renderVrFrame()`, add a one-shot first-frame marker. Immediately after the existing
> `bridge.isReady()` check (in the main non-photo path), add:
>
> ```kotlin
> if (vrFirstFrameLoggedMs == 0L && bridge.isReady() && xrInitStartedAtMs > 0L) {
>     vrFirstFrameLoggedMs = SystemClock.uptimeMillis()
>     Timber.i("VR_PERF: [gl-thread] first_frame_ready  abs_from_init=%dms", vrFirstFrameLoggedMs - xrInitStartedAtMs)
> }
> ```

**Verification:**

- `Grep "VR_PERF"` in `VrPlayerActivity.kt` returns at least 7 matches.
- `Grep "vrFirstFrameLoggedMs"` returns at least 3 matches (declaration, zero-check, assignment).
- `Grep "Log\.d\("` in that file returns 0 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 3/3 PASS. VR_PERF ×7, vrFirstFrameLoggedMs ×4, Log.d = 0. Files: VrPlayerActivity.kt (+17 lines).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly). `(auto-build — PASS)`
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `OpenXrSessionManager.kt` and `VrPlayerActivity.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and rendered via `render.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 needs an instrumented debug build installed on Quest 3. The timing data visible in logcat will be under the `VR_PERF` tag. To filter: `adb logcat -s VR_PERF VrPlayerActivity OpenXrSessionManager`.

---

## Rollback Plan

Revert phase commit(s). Timing markers are additive — no data migration or user-facing surface changed.
