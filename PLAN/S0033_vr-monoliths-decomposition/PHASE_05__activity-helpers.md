# Phase 05 — `VrPlayerActivity` decomposition into helpers

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01–04 (cpp track) — only to keep diffs reviewable; no semantic dependency.
**Blocks:** Phase 06
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Extract three Manager classes from `VrPlayerActivity.kt` (1956 LOC) so the Activity is left ≤ 1000 LOC and contains only composition + Android lifecycle delegates. Strategic §6.2 (Activity granularity) is finalised below.

### Resolved §6.2 mapping

| Manager (new file) | Scope | Functions migrated | Approx LOC moved |
|--------------------|-------|--------------------|-----------------:|
| `VrRenderPipelineManager` | XR render pipeline lifecycle + frame dispatch | `initializeVrRenderPipeline`, `startVrHudProgressTicker`, `releaseVrRenderPipeline`, `renderVrFrame`, `resolveSourceAspectRatio`, `ensureVrPlayerListenerAttached`, `detachVrPlayerListener`, `cachePlayerAspectRatio`, `syncVrPlayerBindingToBridgeSurface`, `flushPendingVrSurfaceIfReady`, `setVrRenderingActive` | ~400 |
| `VrSessionLifecycleManager` | Route decisions, fallbacks, mode switches, stereo coherence | `resolvePlaybackRoute`, `launchStandardPlayerFallback`, `launchUnsupportedImmersiveFallback`, `startXrInitialization`, `fallbackToFlatCinemaMode`, `forceStopVrPlayback`, `exitVrAndStopPlayback`, `launchVrFailureRecovery`, `switchToPanelPreservingPosition`, `switchToImmersivePreservingPosition`, `applyStereoModeToVrRenderers`, `refreshLayerDescriptor`, `assertStereoCoherence`, `isVrStaticImageActive`, `isCurrentVrStaticImageSession`, `buildVrImageKey` | ~430 |
| `VrPlayerCommandRouter` | Controller/hand command dispatch + feedback helpers | `handleVrCommand`, `traceImmersiveCommand`, `dispatchPanelZoneClick`, `scheduleSeekDrag`, `commitSeekDrag`, `nextPanelSpeed`, `cycleAudioTrackAndUpdatePanel`, `onVolumeStep`, `showVolumeFeedback`, `onZoomGripDelta`, `showSeekFeedback`, `showFileFeedback`, `toggleMute`, `triggerHaptic`, `maybeTriggerHaptic` | ~300 |

Activity retains: `onCreate`, `onResume`, `onPause`, `onDestroy`, `onNewIntent`, `dispatchKeyEvent`, `onGenericMotionEvent`, `saveCurrentFrame`, `exitPlayerWithAudioCheck`, `handle3dVrToggleClicked`, `captureStereoSnapshotFromCommand`, `toggleVrControlOverlayFromCommand`, `isImmersiveUiLocked`, `updatePlayerFpsOverlay`, the `Player.Listener` definition, `buildFileOpsCallbacks` callback object, `humanReadableSize`, `formatDurationMs`, `companion object`. Estimated residual ≤ 950 LOC.

---

## Prerequisites

- [ ] Phase 04 ✅ Done (cpp coordinator ≤ 700 LOC).
- [ ] Backup of `VrPlayerActivity.kt` placed in `temp/` (file > 500 LOC).
- [ ] Working tree clean or on a feature branch.
- [ ] Existing helpers reviewed: `VrControllerInputManager`, `VrZoomManager`, `VrControlOverlayManager`, `VrHudIndicatorManager`, `VrFileOpsOverlayManager`, `VrCheatsheetOverlayManager`, `VrHandRayManager`, `VrControllerRayManager`, `VrToggleButtonManager` — none of these absorb the migrated logic; all three new Managers are genuinely new.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | New | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrSessionLifecycleManager.kt` | New | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrPlayerCommandRouter.kt` | New | ≤ 400 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | starts 1956 → ends ≤ 1000 |

> File `VrPlayerActivity.kt` >500 LOC — backup mandatory before edits in Step 05.1.

---

## Steps

### Step 05.1 — Backup + author `VrRenderPipelineManager.kt`

**Files:** `temp/VrPlayerActivity.kt.<timestamp>.bak`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `VrPlayerActivity.kt` to `temp/VrPlayerActivity.kt.<YYYYMMDD-HHmm>.bak`. Create `VrRenderPipelineManager` in package `com.sza.fastmediasorter.vr.helpers`. Constructor takes the dependencies the migrated functions reference: `xrSessionManager`, `stereoRenderer`, `photoSphereRenderer`, `videoSurfaceBridge`, the volatile fields (`pendingVrBridgeSurface`, `pendingVrBridgeTextureId`, `vrRenderingActive`, `currentLayerDescriptor`, `cachedPlayerSourceAspectRatio`, `cachedDescriptorSourceAspectRatio`, `dbgRenderFrameCount`, `vrFpsFrameCount`, `vrFpsLastUpdateTime`, `vrFpsLastValid`, `vrFirstFrameLoggedMs`), and a callback for `setRenderingActiveOnActivity(active: Boolean, reason: String)` (used to sync the Activity-side flag).
>
> Migrate function bodies verbatim from the Activity. Replace `this.field` (Activity-side) with `field` parameter. Keep Timber logs unchanged. No `Log.d` allowed.

**Verification:**

- `Glob` — `VrRenderPipelineManager.kt` exists.
- `Grep` — `class VrRenderPipelineManager` matches once.
- `Grep` — each of the eleven function names matches in the new file.
- `Grep` — `Log\.d\(` returns zero hits in the file.
- `wc -l` ≤ 500.

**Status:** `[ ]` not done

---

### Step 05.2 — Author `VrSessionLifecycleManager.kt`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrSessionLifecycleManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `VrSessionLifecycleManager` in `helpers/`. Constructor takes the Activity reference (for `Intent` construction, `finish()`, `startActivity` — these stay Android-bound), `viewModel`, `xrSessionManager`, `stereoDetector`, `routeDecisionHelper`, `playbackPrefs`, the volatile session-state flags (`xrInitializationRequested`, `xrInitStartedAtMs`, `standardPlayerFallbackLaunched`), and a callback `onCriticalLifecycleEvent(reason: String)` for cases where the Manager needs the Activity to apply UI changes (e.g. show toast).
>
> Migrate the sixteen function bodies listed in §6.2 mapping. Keep KDoc comments; this Manager owns the route decision policy that previously sprawled across the Activity.

**Verification:**

- `Glob` — `VrSessionLifecycleManager.kt` exists.
- `Grep` — `class VrSessionLifecycleManager` matches once.
- `Grep` — `fun resolvePlaybackRoute`, `fun startXrInitialization`, `fun applyStereoModeToVrRenderers`, `fun refreshLayerDescriptor` each match.
- `Grep` — `Log\.d\(` returns zero hits.
- `wc -l` ≤ 500.

**Status:** `[ ]` not done

---

### Step 05.3 — Author `VrPlayerCommandRouter.kt`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrPlayerCommandRouter.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Create `VrPlayerCommandRouter` in `helpers/`. Constructor takes: `viewModel`, `audioManager`, `vrZoomManager`, `vrCheatsheetManager`, `vrFileOpsManager`, `vrToggleButtonManager`, `vrInteractivePanelDriver`, `xrSessionManager` (for haptics), the volatile `currentPanelSpeed`, `lastSeekDragFraction`, the seek constants (`VR_SEEK_SECONDS`, `VR_SEEK_MICRO`, `VR_SEEK_MACRO`, `SEEK_DEBOUNCE_MS`, `HAPTIC_*`), and a `mainHandler: Handler`.
>
> Migrate the fifteen function bodies listed in §6.2 mapping. Keep `handleVrCommand`'s when-branches verbatim; the `PlaybackCommand` enum dispatch is the largest body and must read the same as the Activity version did (zero behaviour delta).

**Verification:**

- `Glob` — `VrPlayerCommandRouter.kt` exists.
- `Grep` — `class VrPlayerCommandRouter` matches once.
- `Grep` — `fun handleVrCommand`, `fun dispatchPanelZoneClick`, `fun scheduleSeekDrag`, `fun cycleAudioTrackAndUpdatePanel` each match.
- `Grep` — `Log\.d\(` returns zero hits.
- `wc -l` ≤ 400.

**Status:** `[ ]` not done

---

### Step 05.4 — Wire Managers into `VrPlayerActivity` and delete migrated bodies

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> In `VrPlayerActivity`:
>
> 1. Add three private `lateinit var` Manager fields: `vrRenderPipelineManager`, `vrSessionLifecycleManager`, `vrPlayerCommandRouter`.
> 2. In `onCreate`, after the existing `@Inject` field reads and the existing helper constructions, instantiate the three Managers passing the dependencies they declared in their constructors.
> 3. Delete the bodies of all functions migrated in Steps 05.1–05.3. For functions still referenced from elsewhere in the Activity (e.g. `handleVrCommand` is reached from `dispatchKeyEvent` / `onGenericMotionEvent`), keep a one-line forwarder: `private fun handleVrCommand(command: PlaybackCommand, source: VrCommandSource = VrCommandSource.UI) = vrPlayerCommandRouter.handleVrCommand(command, source)`.
> 4. The shared volatile state fields move to the Manager that owns them (passed by reference where Kotlin allows, e.g. `AtomicReference` / shared object). Where shared between Activity and a Manager, expose a getter on the Manager and read from there.
> 5. Confirm `wc -l app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` ≤ 1000.
>
> Forwarder lines are acceptable but should be ≤ 1 line each; do not duplicate logic.

**Verification:**

- `Grep` — `private val vrRenderPipelineManager` or `private lateinit var vrRenderPipelineManager` matches.
- `Grep` — `vrSessionLifecycleManager.resolvePlaybackRoute` matches at least once (replacement of internal call).
- `Grep` — `private fun resolvePlaybackRoute` does NOT match in `VrPlayerActivity.kt` (body removed; only the Manager owns it).
- `Grep` — `private fun handleVrCommand` matches (forwarder kept).
- `wc -l app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` ≤ 1000.

**Status:** `[ ]` not done

---

### Step 05.5 — Build verification (vr + standard)

**Files:** —
**Depends on:** Step 05.4

**Prompt for developer:**

> Run `/build` for `vr debug` and for `standard debug`. The standard flavor does not include `src/vr/` Kotlin sources but the file moves should not break standard either. Resolve any lint warnings introduced.

**Verification:**

- `assembleVrDebug` PASS.
- `assembleStandardDebug` PASS.
- `Grep` — `TODO(phase-05)` returns zero hits.

**Status:** `[ ]` not done

---

### Step 05.6 — Manual on-device smoke (Quest 3)

**Files:** —
**Depends on:** Step 05.5

**Prompt for developer:**

> Install the new VR debug APK on Quest 3. Run a video file end-to-end:
>
> 1. Cold-start enters immersive mode without crash.
> 2. Controller commands work: trigger / grip / thumbstick navigation.
> 3. Hand-tracking commands work (if hand-tracking is enabled).
> 4. Mode switches preserve playback position (panel ↔ immersive).
> 5. File-ops overlay (delete / move / rename) routes correctly.
> 6. Stereo coherence intact: 2D file → 2D, SBS → SBS, 360 → equirect.
>
> Mark this step `[manual — deferred to human]`. If user reports a regression, reopen the relevant Manager file and revert the offending migration.

**Verification:**

- Step status flipped to `[manual — deferred to human]`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done` (Step 05.6 may be `[manual — deferred to human]`; spec status moves to `BlockNeedUserTest` if the manual smoke is the only outstanding item).
- [ ] Project compiles — `/build vr debug` PASS, `/build standard debug` PASS.
- [ ] `VrPlayerActivity.kt` ≤ 1000 LOC.
- [ ] Each new Manager file ≤ its budget (`wc -l`).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Both monoliths are decomposed: `OpenXrNative.cpp` ≤ 700 LOC, `VrPlayerActivity.kt` ≤ 1000 LOC. Phase 06 closes out: catalog regen, dev log housekeeping, S0024 unblock instruction.

---

## Rollback Plan

Revert phase commit(s); the Phase 05 backup of `VrPlayerActivity.kt` provides full restore. Each Manager is independent — partial rollback (revert one Manager only) is possible if needed.
