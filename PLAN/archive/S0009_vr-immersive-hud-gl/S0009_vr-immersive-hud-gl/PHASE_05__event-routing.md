# Phase 05 — Event Routing (activity wiring + 2 Hz progress ticker)

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06, Phase 07
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Wire the HUD pipeline into `VrPlayerActivity`: create the renderer + composer on session ready, release on session stop, route all existing `VrHudIndicatorManager` indicator events through a new `VrHudSink` abstraction (GL path when XR session is active, Android-view path as fallback), and add a 2 Hz ticker that refreshes the progress bar while the HUD is visible. Remove all debug visibility overrides from Phase 02 and Phase 04.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Phase 04 is `✅ Done` — sample state renders through the full pipeline.
- [ ] `VrPlayerActivity.kt` freshly backed up for this phase.
- [ ] Strategic spec §6.3 start-default (recreate swapchain alongside session) is understood.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudIndicatorManager.kt` | Modified | ≤ 320 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt` | New | ≤ 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` | New | ≤ 250 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified (debug block removal) | ≤ 2900 |

> `VrPlayerActivity.kt` is 1443 LOC — mandatory backup. Net add ≤ 60 LOC (wiring only). If the projected size crosses 1500, extract the HUD wiring block into a new `VrHudHostManager` helper before committing.

---

## Steps

### Step 5.1 — Remove the Phase-02 and Phase-04 debug blocks

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Backup both files to `temp/` first. Then:
>
> - In `OpenXrNative.cpp`, delete the `#ifndef NDEBUG` block marked `TODO(phase-05): remove debug visibility toggle` added in Phase 02 Step 2.5.
> - In `VrPlayerActivity.kt`, delete the `if (BuildConfig.DEBUG) { .. }` block marked `TODO(phase-05): remove debug HUD sample` added in Phase 04 Step 4.4.

**Verification:**

- `Grep` — pattern `TODO(phase-05)` in the repository returns zero hits.
- `Grep` — pattern `HUD: phase-02 debug visibility = true` returns zero hits.
- `/build` skill compiles `vrDebug` without errors.
- Device test: without the real wiring yet, the HUD quad stays hidden (no more dark rectangle, no more sample preview).

**Status:** `[x]` done

---

### Step 5.2 — Introduce the `VrHudSink` abstraction

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> Create `VrHudSink.kt` in the `..vr.ui` package — an interface that mirrors the public methods of the current `VrHudIndicatorManager`:
>
> ```kotlin
> interface VrHudSink {
>     fun showPauseIndicator(paused: Boolean)
>     fun showVolumeIndicator(percent: Int)
>     fun showSeekIndicator(deltaSeconds: Int, positionMs: Long, totalMs: Long)
>     fun showFileIndicator(name: String, index: Int, total: Int)
>     fun showZoomIndicator(factor: Float)
>     fun showRecenterFlash()
>     fun showBoundaryMessage(isFirst: Boolean)
>     fun showErrorMessage(message: String)
>     fun showImmersiveModeChanged(immersive: Boolean)
>     fun showActionBadge(badge: ActionBadge)            // new — Phase 04 added this to VrHudState
>     fun showRepeatMode(mode: RepeatMode?)              // new
>     fun showBannerText(text: String?)                  // new — used by Phase 06 guard
>     fun updateProgress(positionMs: Long, bufferedMs: Long, totalMs: Long)
>     fun hideAll()
> }
> ```
>
> Also move the enums `ActionBadge` and `RepeatMode` from `..vr.render.VrHudState.kt` into this file OR import them back — pick one home that avoids the Kotlin `import` cycle with the render package (the render package must not depend on `..vr.ui`; the ui package may depend on `..vr.render`).

**Verification:**

- `Glob` — file exists.
- `Grep` — pattern `interface VrHudSink` returns exactly one hit.
- `Grep` — pattern `fun updateProgress` returns exactly one hit in `VrHudSink.kt`.
- `/build` skill compiles `vrDebug` without errors.

**Status:** `[x]` done

---

### Step 5.3 — Make `VrHudIndicatorManager` implement `VrHudSink` (Android-view fallback backend)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudIndicatorManager.kt`
**Depends on:** Step 5.2

**Prompt for developer:**

> Make `VrHudIndicatorManager` declare `: VrHudSink` and add the new methods that were introduced in Step 5.2 but do not yet exist on the manager:
>
> - `showActionBadge` — short centre-toast using the same center slot.
> - `showRepeatMode` — small top-right slot line (or fold into the existing `topRightSlot` with a prefix glyph).
> - `showBannerText` — centre banner when non-null; calls `hideAll` on the slot when null.
> - `updateProgress` — no-op on the Android-view path (this backend never showed a live progress bar; keep it no-op to avoid regressing phone-fallback visuals).
>
> Do not delete any existing public methods. Do not remove the `decorView.addView` path — this class remains the phone-fallback backend for VR sessions that never acquire XR (e.g. running on a phone without Meta runtime).

**Verification:**

- `Grep` — pattern `class VrHudIndicatorManager.*: VrHudSink` (allowing whitespace) returns exactly one hit.
- `Grep` — pattern `override fun updateProgress` returns exactly one hit in `VrHudIndicatorManager.kt`.
- `Grep` — pattern `override fun showActionBadge` returns exactly one hit.
- File ≤ 320 LOC.
- `/build` skill compiles `vrDebug`.

**Status:** `[x]` done

---

### Step 5.4 — Implement `VrHudSceneDriver` (GL backend of `VrHudSink`)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`
**Depends on:** Step 5.3

**Prompt for developer:**

> Create `VrHudSceneDriver.kt` in `..vr.render` with class `VrHudSceneDriver(private val renderer: VrHudRenderer, private val composer: VrHudSceneComposer) : VrHudSink`. Responsibilities:
>
> - Owns a private mutable `currentState: VrHudState` (start with an "all-off" default).
> - Every `VrHudSink` override updates the relevant field(s) on `currentState`, sets `visibleUntilMs = now + slotTimeoutMs` for that slot, and calls private `requestRedraw()`.
> - Slot timeouts match the existing `VrHudIndicatorManager` constants (pause 800 ms, volume 1500 ms, seek 1200 ms, file 2000 ms, zoom 1000 ms, recenter 400 ms, boundary 1500 ms, immersive 1200 ms, error 3000 ms, action badge 300 ms, banner 3000 ms).
> - `requestRedraw()` coalesces multiple calls in one frame by posting to a `Handler(Looper.getMainLooper())` only if not already scheduled. In the posted runnable: render via `renderer.submit { composer.draw(currentState, it) }`, then call `renderer.setVisible(anySlotActive())`.
> - A 2 Hz ticker `Runnable` (Phase 05 scope; kicked off by `onSessionReady` and killed by `onSessionStopped`) is responsible only for calling `updateProgress(position, buffered, total)` — it does NOT drive the slot timeouts; those are event-based. Tick interval: 500 ms.
> - `anySlotActive()` returns true if any slot's `expiresAtMs > now` OR progress bar just updated. When false for ≥ one tick, set layer invisible and stop scheduling ticker redraws (but keep the ticker running so it can resume when events arrive).

**Verification:**

- `Glob` — file exists.
- `Grep` — pattern `class VrHudSceneDriver` returns exactly one hit.
- `Grep` — pattern `: VrHudSink` in `VrHudSceneDriver.kt` returns exactly one hit.
- `Grep` — pattern `private fun requestRedraw` returns exactly one hit.
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` returns zero hits.
- File ≤ 250 LOC.
- `/build` skill compiles `vrDebug`.

**Status:** `[x]` done

---

### Step 5.5 — Wire `VrHudSceneDriver` into `VrPlayerActivity` session lifecycle

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 5.4

**Prompt for developer:**

> Backup first. In `VrPlayerActivity.kt`:
>
> 1. Add fields `private var vrHudRenderer: VrHudRenderer? = null` and `private var vrHudSceneDriver: VrHudSceneDriver? = null`.
> 2. Change the existing `vrHudManager` type from `VrHudIndicatorManager?` to `VrHudSink?`. All code paths inside `VrPlayerActivity` that call `vrHudManager?.show*(..)` continue to compile because `VrHudSceneDriver` and `VrHudIndicatorManager` both implement `VrHudSink`.
> 3. Keep `VrHudIndicatorManager(this)` as the fallback allocation in `onCreate` so phone-fallback paths still work.
> 4. In `initializeVrRenderPipeline()` (runs on the GL thread after XR session is ready), construct `VrHudRenderer(xrSessionManager!!)`, `VrHudSceneComposer(this)`, and `VrHudSceneDriver(renderer, composer)`. Call `renderer.ensureSwapchainCreated()`. Assign `vrHudManager = sceneDriver` and store the renderer in `vrHudRenderer` for release.
> 5. In `releaseVrRenderPipeline()` (runs on the GL thread during session stop), call `vrHudRenderer?.release()` and revert `vrHudManager` back to the original `VrHudIndicatorManager` instance (kept for future session restart).
> 6. In `onDestroy()`, ensure both `vrHudRenderer?.release()` and the fallback manager's `release()` are called in order.
> 7. Add the progress ticker: in `onSessionReady` start a coroutine on `lifecycleScope` that every 500 ms reads `videoPlayerManager.exoPlayer?.let { ExoPlayer → position/buffered/duration }` and calls `vrHudManager?.updateProgress(..)`. The coroutine cancels when the session stops.
>
> The 3DVR toggle button wiring, file ops, control dialog, cheatsheet, etc. stay unchanged this phase — they still call their Android-view panels. Phase 06 adds the transitional guard for those.

**Verification:**

- `Grep` — pattern `private var vrHudRenderer: VrHudRenderer\\?` returns exactly one hit.
- `Grep` — pattern `private var vrHudSceneDriver: VrHudSceneDriver\\?` returns exactly one hit.
- `Grep` — pattern `updateProgress\\(` inside `VrPlayerActivity.kt` returns at least one hit (the ticker).
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` returns zero hits in the lines touched by this phase (the pre-existing ones are legacy and out of scope for this step — verify the diff).
- On-device test on Quest 3: launching immersive video shows the HUD pause/progress/seek/volume/file/zoom/recenter indicators exactly when the corresponding control is pressed. Exiting immersive and re-entering does not leak resources (watch for duplicate `HUD swapchain:` log lines without matching `destroyed`).

**Status:** `[x]` done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] Project compiles — `/build` on `vrDebug`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits in the repository.
- [ ] On-device: every indicator (pause, volume, seek, file, zoom, recenter, immersive, boundary, error, action badge, repeat mode, progress bar) is observed at least once during a 3-minute smoke test.
- [ ] Research §6.3 (lifecycle) — verify HUD swapchain is recreated after `onPause` → `onResume` cycle; tick off the INDEX blocker checkbox.
- [ ] Research §6.4 (placement) — verify the HUD quad position is ergonomically acceptable; adjust constants in `OpenXrNative.cpp` within the allowed ±30 % if needed; tick off the INDEX blocker checkbox.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 06 assumes:

- `vrHudManager.showBannerText("..")` is already live and surfaces a centre banner for 3 s — Phase 06 uses it to display the transitional guard message.
- `vrHudManager.hideAll()` clears every slot including the banner.
- The HUD pipeline is stable in its "events-only-plus-2 Hz-ticker" regime; Phase 06 does not alter cadence.

---

## Rollback Plan

Revert the phase commit(s). Phase 04's sample state preview is already gone, so the HUD quad goes back to a never-visible state (Phase 02's debug toggle was removed in Step 5.1 — that removal is part of this phase's revert too, so Phase 02 debug diamond reappears). To fully bisect: revert 5.5 first (wiring), verify Phase 04 preview still works standalone, then revert earlier steps.
