# Phase 02 - HUD Banner Renderer

**Strategic spec:** [`../S0989_vr-diagxr-activity-decompose.md`](../S0989_vr-diagxr-activity-decompose.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Move banner HUD RGBA generation (filename + error banners, projection/layout labels, the reusable HUD buffer) into `VrHudBannerRenderer`, owning `runtime.queueHud` for the banner path.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (enums relocated).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrHudBannerRenderer.kt` | New | ≤ 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1500 |

> Flavor placement: vr-only helper under `src/vr/java/...`.

---

## Steps

### Step 02.1 - Create VrHudBannerRenderer

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrHudBannerRenderer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `VrHudBannerRenderer(private val runtime: DiagnosticXrRuntime)` in `...ui.xr.helpers`. Move verbatim: `generateErrorHudBytes`, `generateFilenameHudBytes`, `queueErrorHud`, `queueFilenameHud`, `projectionLabel`, `layoutLabel`, the `@Synchronized getReusableHudBuffer()` + its `reusableHudBuffer` field, and the `HUD_BANNER_WIDTH` / `HUD_BANNER_HEIGHT` constants (into a `companion object`, expose them as needed). Keep all Canvas drawing, byte-copy, and `Timber.d` diagnostic lines (including the `S0961` / first-pixel dump) byte-for-byte. Expose `queueFilename(filename, projection: ProjectionType, layout: StereoLayout)` and `queueError(filename, errorMsg)` as the public API; add `fun releaseBuffers()` that nulls `reusableHudBuffer`.

**Verification:**

- `Glob` - `VrHudBannerRenderer.kt` exists.
- `Grep` - `class VrHudBannerRenderer` matches exactly once.
- `Grep` - `fun queueFilename(` and `fun queueError(` present.
- `Grep` - `generateFilenameHudBytes` returns zero hits in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Rewire Activity to the banner renderer

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the Activity: instantiate `private val hudBanner = VrHudBannerRenderer(runtime)` (assign in `proceedWithInitialization`, after `runtime` is injected). Replace `queueFilenameHud(a,b,c)` -> `hudBanner.queueFilename(a,b,c)` and `queueErrorHud(a,b)` -> `hudBanner.queueError(a,b)` at every call site. Remove the moved private methods, fields, and `HUD_BANNER_*` constants from the Activity (leave any that other retained code still references - verify none do). In `onDestroy`, call `hudBanner.releaseBuffers()` where `reusableHudBuffer = null` was.

**Verification:**

- `Grep` - `queueFilenameHud(` / `queueErrorHud(` return zero hits in the Activity (only `hudBanner.` calls remain).
- `Grep` - `reusableHudBuffer` returns zero hits in `DiagnosticXrActivity.kt`.
- `/build` - `standard debug` + `vr debug` compile.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build` `standard debug` + `vr debug`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] Phase-boundary audit - no unresolved P0/P1.

---

## Handoff Notes to Next Phase

Banner path fully owned by `VrHudBannerRenderer`; Phase 04 playback calls `hudBanner.queueError` on playback failure.

---

## Rollback Plan

Revert phase commit(s) - pure code move, no user-facing surface changed.
