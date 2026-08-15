# Phase 02 - Host wiring

**Strategic spec:** [`../S1223_vr-immersive-controls-discoverability.md`](../S1223_vr-immersive-controls-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04
**Steps done:** 4 / 4
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Make the legend appear on the first immersive entry after install, dismiss on a controller input without that input also acting, and hand the HUD channel back to the media strip intact.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `CODE.LOCK` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | <= 1300 |

The host is at 1199 lines against the 1500 ceiling. Every piece of legend behaviour that can live in `HudLegendController` already does (Phase 01); what lands here is construction, one trigger, two guards and one release.

---

## Steps

### Step 02.1 - Construct the legend stack in `proceedWithInitialization`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Inject lateinit var legendPreferences: VrLegendPreferences` beside the other injected fields.
>
> Add a lazy `legendController` built from a `HudLegendRenderer` whose `title`, `footer` and `rows` are filled from string resources in `proceedWithInitialization`, next to the existing `hudRenderer.*Label` assignments. The eight rows, in this order, are the wired mapping from the strategic spec section 2: trigger-click, trigger-summon, thumbstick X seek, grip plus thumbstick X file step, thumbstick Y zoom, grip drag, A/X exit, and the strip's HIDE/EXIT pair. String keys are created in Phase 04; use them now and let that phase add the values.
>
> `onDismissed` is the single restore point and must do exactly three things in order: re-assert the strip geometry with `runtime.setHudQuadSize(PANEL_QUAD_WIDTH_M, PANEL_QUAD_HEIGHT_M, PANEL_QUAD_OFFSET_Y_M)`, set `hudVisible = true`, and call `renderPanelHud()` directly rather than `scheduleHudPanelRepaint()` - the debounce would leave the legend's texture on a strip-sized quad for up to 100 ms.

**Verification:**

- `Grep` - `legendPreferences` matches as an `@Inject lateinit var` exactly once.
- `Grep` - `setHudQuadSize(PANEL_QUAD_WIDTH_M` matches exactly twice in the file (session-ready plus the restore point).
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

### Step 02.2 - Show it on the first immersive entry

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> At the end of the `isPanelHudMode()` branch of `onRenderThreadSessionReady`, after `refreshTrackRows()` and `renderPanelHud()`, launch a one-shot `lifecycleScope.launch { }` that reads `legendPreferences.isShown()`; when false, call `legendController.show()` and then `legendPreferences.markShown()`. Order matters - marking before showing would lose the legend if the read wins a race with a crash, and marking after showing is the behaviour the acceptance criterion describes.
>
> Guard it with `isPanelHudMode()`: the diagnostic playlist keeps the S0291 banner and must never see a legend.
>
> Add the first debug probe: `Timber.d("S1223: legend shown on first immersive entry")` immediately before `show()`.

**Verification:**

- `Grep` - `legendController.show()` matches exactly twice in the file after Phase 03 (first entry plus the HELP button); exactly once at this step.
- `Grep` - `S1223: legend shown` matches exactly once.
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

### Step 02.3 - Dismiss on input, and consume that input

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Two guards, both inside the existing `runOnUiThread` blocks and both after the `isFinishing || isDestroyed` check.
>
> In `onNativeInputEvent`, before the `INPUT_EVENT_HUD_SUMMON` branch: `if (legendController.dismiss()) return@runOnUiThread`. Placing it first is what stops a dismissing thumbstick deflection from also seeking or stepping a file, and stops a dismissing trigger from also summoning.
>
> In `onNativeRayInteraction`, dismiss only on a real press - `if (isClick && legendController.dismiss()) return@runOnUiThread` - so that merely pointing the ray at the legend does not close it before it is read.
>
> Add the second debug probe inside the guard, once: `Timber.d("S1223: legend dismissed by controller input")`. Put it in `HudLegendController.dismiss()` rather than at both call sites so it fires exactly once per dismissal.
>
> Comment the WHY, not the WHAT: the press is swallowed because a legend that closes and simultaneously acts teaches the user that the binding did something unrelated to what they read.

**Verification:**

- `Grep` - `legendController.dismiss()` matches exactly twice in `DiagnosticXrActivity.kt`.
- `Grep` - `S1223: legend dismissed` matches exactly once repo-wide.
- Read `onNativeInputEvent` and confirm the dismiss guard precedes the `INPUT_EVENT_HUD_SUMMON` check.
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

### Step 02.4 - Release the legend buffers on teardown

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `onDestroy`, next to `hudBanner.releaseBuffers()` and the `reusablePanelHudBuffer = null` lines, call `legendController.release()`. Use the same `::isInitialized`-style guard the neighbouring lazy helpers use if the field is `lateinit`; a lazy `by lazy` field must not be touched here unless it was already initialised, otherwise the preflight-failure path allocates a controller purely to release it.

**Verification:**

- `Grep` - `legendController.release()` matches exactly once, inside `onDestroy`.
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `DiagnosticXrActivity.kt` is under 1500 lines - `(Get-Content <path>).Count`.
- [x] `.\a.ps1 fkn` passes.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Phase-boundary audit run, with the listener-symmetry and lifecycle checks from `docs/CODE_AUDIT_PROTOCOL.md` applied to the new `onDestroy` release path.

---

## Handoff Notes to Next Phase

After this phase the legend is reachable exactly once per install. Phase 03 adds the only way to see it again.

---

## Rollback Plan

Revert the host edits; the Phase 01 classes become unreferenced again but still compile.
