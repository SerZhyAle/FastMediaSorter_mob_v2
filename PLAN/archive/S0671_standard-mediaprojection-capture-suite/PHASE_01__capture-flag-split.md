# Phase 01 - Capture flag split

**Strategic spec:** [`../S0671_standard-mediaprojection-capture-suite.md`](../S0671_standard-mediaprojection-capture-suite.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-25

---

## Objective

Decouple the MediaProjection capture suite (`src/screenCapture`, mediaProjection FGS) from the edge-gesture overlay (`src/standardScreenCapture`, SYSTEM_ALERT_WINDOW + SPECIAL_USE) in the `standard` flavor, so the low-risk capture suite can ship while the SPECIAL_USE overlay stays off (deferred to S0672). Enable the capture suite for `standard`; leave the overlay gate off.

---

## Prerequisites

- [x] Strategic §6 research items are Resolved (both are).
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `gradle.properties` | Modified | ≤ 30 |
| `app_v2/build.gradle.kts` | Modified | ≤ 1200 |

> No backup needed for `gradle.properties`. `build.gradle.kts` is large; edit only the two gated blocks (source-set mount ~583-589, manifest injection ~960-969) plus the property reader (~171).

---

## Steps

### Step 01.1 - Split the build property into capture-suite and edge-overlay gates

**Files:** `gradle.properties`, `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> In `gradle.properties`, keep `fms.screenCapture` as the MediaProjection capture-suite gate and set it to `on` (was `off`). Add a new independent property `fms.edgeGestureOverlay=off` for the edge-gesture overlay (SYSTEM_ALERT_WINDOW + SPECIAL_USE). In `app_v2/build.gradle.kts`, next to the existing `screenCaptureStandardEnabled` reader (~line 171), add a second reader `edgeGestureOverlayStandardEnabled` resolving `fms.edgeGestureOverlay` (default `off`). Update the comment block to state the two gates are independent: capture suite = Play-shippable (mediaProjection only); edge overlay = deferred to S0672.

**Verification:**

- `Grep` - `^fms\.screenCapture=on$` matches once in `gradle.properties`.
- `Grep` - `^fms\.edgeGestureOverlay=off$` matches once in `gradle.properties`.
- `Grep` - `edgeGestureOverlayStandardEnabled` matches in `app_v2/build.gradle.kts`.

**Status:** `[x]` done

---

### Step 01.2 - Gate the standard source sets by the two independent flags

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `getByName("standard")` source-set block (~583-589), mount `src/screenCapture/java` + `src/screenCapture/res` under `screenCaptureStandardEnabled`, and mount `src/standardScreenCapture/java` under the NEW `edgeGestureOverlayStandardEnabled` flag instead of the shared one. Result: with `fms.screenCapture=on` and `fms.edgeGestureOverlay=off`, `standard` mounts the capture engine but NOT the edge-gesture overlay controller. Do not touch the `noLegal` block - it mounts `src/screenCapture` unconditionally and must stay unchanged.

**Verification:**

- `Grep` - in `app_v2/build.gradle.kts`, `src/standardScreenCapture/java` appears under an `if (edgeGestureOverlayStandardEnabled)` branch (not under `screenCaptureStandardEnabled`).
- `Grep` - the `getByName("noLegal")` block still adds `src/screenCapture/java` and is not gated by either flag.
- `/build` - `assembleStandardDebug` configures and compiles (capture engine present, overlay absent).

**Status:** `[x]` done

---

### Step 01.3 - Gate the manifest injection by the two independent flags

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.2

**Prompt for developer:**

> In the `androidComponents.onVariants` manifest-injection block (~960-969), inject `src/screenCapture/AndroidManifest.xml` (mediaProjection FGS) for `noLegal` OR (`standard` AND `screenCaptureStandardEnabled`), as today. Inject `src/standardScreenCapture/AndroidManifest.xml` (SYSTEM_ALERT_WINDOW + SPECIAL_USE) only for `standard` AND the NEW `edgeGestureOverlayStandardEnabled` flag. With the shipped defaults the standard merged manifest gains the mediaProjection service but NOT the SPECIAL_USE / SYSTEM_ALERT_WINDOW declarations.

**Verification:**

- `Grep` - `standardScreenCapture/AndroidManifest.xml` injection is guarded by `edgeGestureOverlayStandardEnabled`.
- `Grep` - `screenCapture/AndroidManifest.xml` injection still guarded by `screenCaptureStandardEnabled` / `noLegal`.
- `/build` - `assembleStandardDebug` merged manifest contains `FOREGROUND_SERVICE_MEDIA_PROJECTION` and NOT `FOREGROUND_SERVICE_SPECIAL_USE` (verify via the merged manifest under `build/intermediates`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (`assembleStandardDebug`).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Standard merged manifest has `mediaProjection` FGS, no `specialUse` FGS, no `SYSTEM_ALERT_WINDOW`.
- [x] Dev log entry added for `gradle.properties` and `app_v2/build.gradle.kts`.

---

## Handoff Notes to Next Phase

The capture suite (consent activity + capture service + post-processing) now mounts in `standard`. The shared `ScreenCaptureConsentActivity` is the single entry point for both menu and (future) gesture capture - Phase 02 adds the prominent disclosure there. The edge-gesture overlay remains unmounted in `standard` (S0672 territory).

---

## Rollback Plan

Revert the phase commit: restore `fms.screenCapture=off`, drop `fms.edgeGestureOverlay`, restore the single-flag gating. No data migration or user-facing surface changed.
