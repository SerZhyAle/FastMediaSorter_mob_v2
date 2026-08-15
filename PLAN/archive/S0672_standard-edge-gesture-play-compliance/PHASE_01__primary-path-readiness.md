# Phase 01 - Primary-path readiness (strip)

**Strategic spec:** [`../S0672_standard-edge-gesture-play-compliance.md`](../S0672_standard-edge-gesture-play-compliance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-26
**Completed:** 2026-06-26

---

## Objective

Make the existing invisible-strip trigger (`OverlayHostService`, specialUse FGS) Android-15-safe and Play-review-ready, so it can be enabled for the device-test / submission build via `-P fms.edgeGestureOverlay=on` without a runtime crash and with a defensible `specialUse` justification. No behaviour change for the default standard build (gate stays `off`) and none for noLegal.

---

## Prerequisites

- [x] Strategic §6 research items reviewed: `research/02` (Android-15 FGS-start + specialUse review).
- [x] S0671 capture engine confirmed: engine live in production (2.60.6251.711, review passed) + explicit owner "implement anyway" 2026-06-26 (see INDEX Pre-Implementation Blockers).
- [x] Working tree is clean or on a feature branch (DEBUG-v019).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Modified | ≤ 200 |
| `app_v2/src/standardScreenCapture/AndroidManifest.xml` | Modified | ≤ 35 |

> `OverlayHostService.kt` is in the SHARED `src/screenCapture` source set (mounted for both `standard` and `noLegal`). The change is a defensive FGS-start guard only - it must not alter the observable noLegal behaviour (invisible strip + silent path stay intact). Verify a noLegal build still configures.
>
> The strip view is already a real, non-zero-sized `TYPE_APPLICATION_OVERLAY` window (18dp wide, ~65% height, `PixelFormat.TRANSLUCENT`) shown via `ScreenGestureOverlayManager.show()` BEFORE `startForeground()` - this ordering already satisfies the Android-15 SYSTEM_ALERT_WINDOW background-start exemption. Do not invert it.

---

## Steps

### Step 01.1 - Guard the foreground-service start against `ForegroundServiceStartNotAllowedException`

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `OverlayHostService.start(context)` companion function, the call `ContextCompat.startForegroundService(context, intent)` can throw `ForegroundServiceStartNotAllowedException` on API 31+ (and specifically on API 35 when invoked with no visible overlay present, e.g. a future background-context caller). Wrap that single call in a `try`/`catch (e: android.app.ForegroundServiceStartNotAllowedException)` and log at `Timber.w` ("OverlayHostService: FGS start not allowed (no visible overlay / background) - skipping") - do NOT crash, do NOT retry. The catch is a defensive backstop: the only current caller path (`ScreenGestureOverlayStartupCoordinator.restoreIfNeeded` -> `ProcessLifecycleOwner.onStart`) is already a foreground context and will not throw, so this guard exists to keep the contract safe if a background caller is ever added. Add a one-line comment stating the foreground-start invariant. Leave the existing `onStartCommand` try/catch (which already calls `stopOverlayHost()` on failure) unchanged.

**Verification:**

- `Grep` - `catch (e: android.app.ForegroundServiceStartNotAllowedException)` (or an imported `ForegroundServiceStartNotAllowedException`) appears in `OverlayHostService.kt` inside the `start(` companion function.
- `Grep` - `Timber.w(` referencing the FGS-start guard appears once.
- `Grep -n "Log\.d\("` in `OverlayHostService.kt` returns zero hits (Timber only).
- `/build` - `assembleStandardDebug -P fms.edgeGestureOverlay=on` compiles; `assembleNoLegalDebug` still configures (shared file unbroken).

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 4/4 PASS. Grep: FGS-start catch at `OverlayHostService.kt:172`, `Timber.w` guard at `:173`, zero `Log.d`. Build: `compileStandardDebugKotlin` EXIT 0 (catch type compiles); `compileNoLegalDebugKotlin` EXIT 0 (shared file non-breaking - the first run's failure was the `-Pchaquopy.enabled=false` flag, which noLegal's Python path cannot use, not this change). Files: OverlayHostService.kt (+7 LOC). Dev log batched at finalization.

---

### Step 01.2 - Reword the `specialUse` subtype to a user-initiated, user-perceptible justification

**Files:** `app_v2/src/standardScreenCapture/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value is currently `"persistent edge overlay strip host for screenshot gesture trigger"` - which reads as an idle, persistent, deferrable service and is the typical `specialUse` rejection cause (`research/02`). Replace it with an accurate justification that frames the service as user-enabled and user-perceptible WITHOUT misrepresenting it (no claiming the strip is invisible-yet-visible). Suggested value: `"User-enabled on-screen edge handle that the user swipes to start an on-demand screen-capture session; runs only while the user keeps the gesture enabled and shows a persistent foreground notification while active."` This is a Play-reviewer-facing technical descriptor (English only, NOT a user-visible UI string - no EN/RU/UK localisation, no COMMUNICATION_POLICY gate). The exact final wording is owner-tunable for the Play Console submission.

**Verification:**

- `Grep` - the old value `persistent edge overlay strip host for screenshot gesture trigger` no longer appears in `src/standardScreenCapture/AndroidManifest.xml`.
- `Grep` - `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` is still present with a non-empty `android:value`.
- `Grep` - the `<service android:name="com.sza.fastmediasorter.screencapture.OverlayHostService"` declaration and its `android:foregroundServiceType="specialUse"` are unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 3/3 PASS. Old subtype gone; `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` present with the new value; service decl + `foregroundServiceType="specialUse"` unchanged. Merged-manifest proof: overlay=on -> `FOREGROUND_SERVICE_SPECIAL_USE` x2 + the new subtype string present; overlay=off -> SPECIAL_USE absent (MEDIA_PROJECTION still present). Files: standardScreenCapture/AndroidManifest.xml. Dev log batched at finalization.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Merged manifest with `-P fms.edgeGestureOverlay=on` contains `FOREGROUND_SERVICE_SPECIAL_USE` (x2) with the reworded subtype value. Proof via `:app_v2:processStandardDebugMainManifest -Pfms.edgeGestureOverlay=on` (EXIT 0) + grep of the merged main manifest (full `assembleStandardDebug` not needed for a manifest-content check; `compileStandardDebugKotlin` EXIT 0 separately proves compilation).
- [x] Default merged manifest (no `-P`) contains NO `FOREGROUND_SERVICE_SPECIAL_USE` (gate off by default). Grep count 0; `MEDIA_PROJECTION` still present (screenCapture=on intact).
- [x] `Grep` - `gradle.properties` still has `^fms\.edgeGestureOverlay=off$` (committed default deliberately unchanged - strip is opt-in for the device-test / submission build only, because `research/02` rates the `specialUse` declaration likely-rejected and it must not auto-ship).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] noLegal still configures (shared `OverlayHostService` change is non-breaking). `:app_v2:compileNoLegalDebugKotlin` (chaquopy enabled) EXIT 0.
- [x] Dev log entry added for both touched files via `.\scripts\add_to_dev_log.ps1` (2026-06-26 14:07).

---

## Handoff Notes to Next Phase

The strip primary path is Android-15-safe (defensive FGS-start guard) and carries a defensible `specialUse` subtype, enable-ready via `-P fms.edgeGestureOverlay=on`. The committed default stays `off`. Phase 03 records the capability and the external Play obligations; the device tester (BlockNeedUserTest) builds with the flag on. One `Timber.d("S0672: ..")` probe at the FGS-start path is inserted at the BlockNeedUserTest transition, not here.

---

## Rollback Plan

Revert the phase commit: restore the original `specialUse` subtype value and drop the FGS-start try/catch. No data migration, no committed-default change, no user-facing surface touched.
