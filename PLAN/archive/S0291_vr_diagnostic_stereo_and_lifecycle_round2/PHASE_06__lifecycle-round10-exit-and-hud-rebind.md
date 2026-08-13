# Phase 06 - Lifecycle Round 10: Exit Handshake and HUD Re-bind

**Strategic spec:** [`../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md`](../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** owner Quest 3 round-10 verification
**Steps done:** 1 / 2 (Step 06.2 formally deferred to successor ticket)
**Started:** 2026-05-30
**Completed:** 2026-06-04

---

## Objective

Close the two round-9 lifecycle deltas recorded in strategic §1.7: blank HUD on same-process re-entry (§6.9 / §11.16) and missing home passthrough after immersive exit (§6.8 / §11.15).

---

## Drift note (read before implementing)

The round-9 strategic text describes the exit path as `moveTaskToBack(true)` keeping `DiagnosticXrActivity` alive. That approach was superseded by **S0295** (`vr-generic-immerse-playback-contract`, Verified 2026-05-25), which reworked exit into `finish()` + Home/`PendingIntent` panel-host handoff (`deliverReturnAndFinish` → `returnToSettingsTaskOrFinish` → `scheduleHostFinish`). Re-entry now creates a fresh Activity (`onCreate` → `proceedWithInitialization`), not a same-instance resume. The §1.7 symptoms were observed on the now-removed code and must be re-confirmed on the current build.

---

## Steps

### Step 06.1 - HUD re-bind on session-ready for all media types

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** start of phase

**Prompt for developer:**

> In `onRenderThreadSessionReady`, re-queue the current playlist item's filename banner for every media type, not only video. A freshly started native session owns a 1x1 placeholder HUD texture and has cleared the prior session's pending HUD bytes, so the banner must be re-queued whenever a session becomes ready.

**Verification:**

- `Grep` - `queueFilenameHud(file.name, config.projection, config.layout)` appears in `onRenderThreadSessionReady` outside the `isVideoFilename` branch.
- `Grep` - `S0291: session-ready HUD re-queue` appears in `DiagnosticXrActivity.kt` (round-10 device probe).
- `Value` - source compilation validated: `buildCMakeDebug[arm64-v8a]`, `kaptNoLegalDebugKotlin`, and `compileNoLegalDebugKotlin` executed successfully across build runs; the change is `.kt` + C++ comment/log-line removal only (no new symbols). A fully packaged APK was NOT produced locally - the noLegal resource-merge / packaging stages were repeatedly killed by environmental Gradle daemon contention (external `--stop` commands and vanishing `stripped.dir` resource intermediates for untouched layouts). Re-run `a.ps1 nd` in a clean environment without IDE Gradle competition to mint the device APK.

**Status:** `[x]` code done; full-APK build env-blocked (see verification note)

### Step 06.2 - Polite OpenXR exit handshake + passthrough restoration

**Files:** `app_v2/src/vr/cpp/xr_session.cpp` (+ extraction target), exit path owned by S0295
**Depends on:** Step 06.1

**Prompt for developer:**

> Drive the OpenXR session down through a graceful handshake (`xrRequestExitSession`, pump `pollEvents` to `XR_SESSION_STATE_STOPPING` → `xrEndSession` → `EXITING`) before `xrDestroySession`, and/or restore home passthrough on exit, so the Quest compositor reverts to the home environment instead of presenting the last opaque frame.

**Blockers (not resolvable inside S0291):**

- `xr_session.cpp` is 1526 LOC, already over the 1500 limit. Strict Rule 2 forbids adding the handshake in place without first extracting the frame-loop / session-state machine into a separate translation unit - a refactor out of scope for this fix.
- The exit path is owned by **S0295** (Verified). Changing exit timing here would modify a verified contract; the work belongs in a dedicated ticket against the S0295 exit path.
- Root cause is `Open` (strategic §6.8) with an unresolved design fork: clean session-end handshake (compositor restores passthrough on its own) vs. compositing an explicit `XR_FB_passthrough` layer (extension not currently enumerated at `xr_session.cpp` instance creation). Requires owner decision plus Quest 3 observation.

**Status:** `[DEFERRED]` - re-routed to a new ticket; see strategic §6.8 and `## Last Audit`.

## Phase Done Criteria

- [x] Step 06.1 is `[x] done` and builds.
- [x] Step 06.2 formally re-routed to successor ticket (see §6.8 + Last Audit 2026-05-30).

## Handoff Notes

Owner Quest 3 round-10 verifies §11.16 (HUD banner on re-entry) directly. §11.15 (passthrough on exit) remains open pending the successor ticket and the design fork in §6.8.

## Rollback Plan

Revert the `onRenderThreadSessionReady` change. No native or persisted-data changes in this phase.
