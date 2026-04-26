# Phase 02 — Measurement Run

**Strategic spec:** [`../spec_vr-xr-cold-start.md`](../spec_vr-xr-cold-start.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Run the instrumented VR debug build on Quest 3, capture logcat for a cold start and one warm re-entry, and document the per-stage durations in this file. Output of this phase: a filled-in measurement table and a preliminary optimize-vs-backlog recommendation.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Quest 3 connected via ADB (`adb devices` shows the headset).
- [ ] Debug APK built from the `vr` flavor (standard debug or equivalent — see `/build`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/spec_vr-xr-cold-start/PHASE_02__measurement-run.md` | Modified (fill in table) | ≤ 200 |

> This is a documentation-only update after data collection. No source files changed.

---

## Steps

### Step 02.1 — Install instrumented debug build on Quest 3

**Files:** device only
**Depends on:** — start of phase

**Prompt for developer:**

> Build and install the VR debug APK. Use `/build` to trigger `standard debug` (or `vr debug` if a separate VR flavor target exists). Sideload to the connected Quest 3 via `adb install -r <path-to-apk>`.

**Verification:**

- `[manual]` APK installed; app launches on Quest 3 without immediate crash.

**Status:** `[ ]` not done

---

### Step 02.2 — Capture cold-start logcat

**Files:** device only
**Depends on:** Step 02.1

**Prompt for developer:**

> With the Quest 3 connected, start a logcat capture filtered to the relevant tags:
>
> ```
> adb logcat -c && adb logcat -s VR_PERF:I VrPlayerActivity:I OpenXrSessionManager:I VR_BOOT:E *:S
> ```
>
> Force-stop the app to ensure a cold process start, then open a VR media file from the browse screen. Let the XR session reach the first rendered frame. Stop the logcat capture.

**Verification:**

- `[manual]` Logcat contains at least one `VR_PERF: [xr-thread] egl_create=` line.
- `[manual]` Logcat contains a `VR_PERF: [gl-thread] first_frame_ready` line.

**Status:** `[ ]` not done

---

### Step 02.3 — Capture warm re-entry logcat

**Files:** device only
**Depends on:** Step 02.2

**Prompt for developer:**

> Without force-stopping the app, navigate back to browse, then open a VR file again to trigger a warm re-entry (process already alive, XR session re-initialised). Capture the same tags. Note all `VR_PERF` elapsed values.

**Verification:**

- `[manual]` Logcat contains a second `VR_PERF: [gl-thread] first_frame_ready` line with a significantly lower `abs_from_init` than the cold run.

**Status:** `[ ]` not done

---

### Step 02.4 — Fill in measurement table

**Files:** `PLAN/spec_vr-xr-cold-start/PHASE_02__measurement-run.md` (this file)
**Depends on:** Steps 02.2 and 02.3

**Prompt for developer:**

> Copy the elapsed values from the logcat and fill in the table below. Use representative values from a single cold run and a single warm run.

**Verification:**

- `Grep "MEASURED"` in this file returns at least 5 matches (each filled table row replaces the placeholder).

**Status:** `[ ]` not done

---

### Step 02.5 — Record optimize-vs-backlog recommendation

**Files:** `PLAN/spec_vr-xr-cold-start/PHASE_02__measurement-run.md` (this file)
**Depends on:** Step 02.4

**Prompt for developer:**

> Based on the filled table, fill in the "Recommendation" section below. Choose one of:
>
> - `OPTIMIZE_NOW` — at least one stage shows > 200 ms and has a realistic low-risk optimization (see strategic spec §5.2, Столп Б).
> - `BACKLOG` — total cold-start is ≤ ~300 ms more than warm, or optimizations require native changes that exceed the risk budget.

**Verification:**

- `Grep "OPTIMIZE_NOW\|BACKLOG"` in this file returns exactly 1 match in the Recommendation section.

**Status:** `[ ]` not done

---

## Measurement Table

> Replace `MEASURED: Xms` with actual values after Step 02.4.

### Cold start (process killed before run)

| Stage | Tag | Cold (ms) | Warm (ms) | Notes |
|-------|-----|----------:|----------:|-------|
| Android first frame wait (before setupViews) | `BaseActivity` Timber log | MEASURED: ?ms | MEASURED: ?ms | Already logged by BaseActivity |
| xr_init_requested → thread start | `VR_PERF [main]` → `[xr-thread] render thread started` | MEASURED: ?ms | MEASURED: ?ms | Thread spin-up |
| EGL create | `VR_PERF [xr-thread] egl_create` | MEASURED: ?ms | MEASURED: ?ms | |
| nativeInitialize | `VR_PERF [xr-thread] native_init` | MEASURED: ?ms | MEASURED: ?ms | xrCreateInstance + session |
| onSessionReady callback | `VR_PERF [xr-thread] session_ready_cb` | MEASURED: ?ms | MEASURED: ?ms | GL bridge + renderers |
| bridge_init | `VR_PERF [gl-thread] bridge_init` | MEASURED: ?ms | MEASURED: ?ms | |
| renderers_init | `VR_PERF [gl-thread] renderers_init` | MEASURED: ?ms | MEASURED: ?ms | initGl for stereo + photo |
| hud_swapchain | `VR_PERF [gl-thread] hud_swapchain` | MEASURED: ?ms | MEASURED: ?ms | |
| panel_swapchain | `VR_PERF [gl-thread] panel_swapchain` | MEASURED: ?ms | MEASURED: ?ms | |
| pipeline_total | `VR_PERF [gl-thread] pipeline_total` | MEASURED: ?ms | MEASURED: ?ms | |
| first_frame_ready | `VR_PERF [gl-thread] first_frame_ready` | MEASURED: ?ms | MEASURED: ?ms | abs from xr_init_requested |

---

## Recommendation

> Fill in after Step 02.5.

`RECOMMENDATION: [OPTIMIZE_NOW | BACKLOG]`

Justification: *(fill in — which stage dominates and whether it has a viable fix)*

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done` or `[manual — deferred to human]`.
- [ ] Measurement table filled in (all `MEASURED: ?ms` replaced with actual values).
- [ ] Recommendation field populated.
- [ ] Dev log entry added for this file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 03 reads the Recommendation field from this file. If `OPTIMIZE_NOW`, proceed to the implementation branch. If `BACKLOG`, proceed to the won't-fix-now branch.

---

## Rollback Plan

Documentation-only phase. No code changes.
