# Phase 01 — Pin Route Decision Contract

**Strategic spec:** [`../S0018_bugfix-vr-auto-immersive-route-broken.md`](../S0018_bugfix-vr-auto-immersive-route-broken.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Lock the `VrRouteDecisionHelper.decide` contract with a full coverage matrix of unit tests so that any future regression in the route/reason coupling is detected at CI time, not on device.

---

## Prerequisites

- [ ] None — foundation phase.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt` exists and compiles in `testVrUnitTest` flavor task.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt` | Modified | ≤ 350 |

---

## Steps

### Step 01.1 — Add test cases for the `vrAutoImmersive=false` × video matrix

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add JUnit `@Test` methods to `VrRouteDecisionHelperTest` that exercise every video stereo mode against `AppSettings(vrAutoImmersive = false)`. Each test asserts route equals `STANDARD_PANEL_FALLBACK` and `logReason` equals either `auto-immersive-disabled` (for stereoscopic/spherical inputs) or `plain-2d-video` (for `MONO`). Cover at minimum: `MONO`, `SBS_FULL`, `OU_FULL`, `EQUIRECT_360_MONO`, `EQUIRECT_360_SBS`, `VR180_FISHEYE_SBS`. Use the existing `mediaFile(..)` helper.

**Verification:**

- `Grep` — `vrAutoImmersive = false` matches at least 6 times in this file.
- `Grep` — `STANDARD_PANEL_FALLBACK, decision.route` matches at least 6 times in this file.
- `Grep` — `auto-immersive-disabled` matches at least 4 times in this file (stereo/spherical cases).
- `Grep` — `plain-2d-video` matches at least 1 time in this file (MONO case).

**Status:** `[x]` done

---

### Step 01.2 — Add test cases for the `vrAutoImmersive=false` × image matrix

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add JUnit `@Test` methods that assert image content with `AppSettings(vrAutoImmersive = false)` follows the same panel-fallback contract: `MONO` image → `STANDARD_PANEL_FALLBACK` with reason `plain-2d-content`; `SBS_FULL` / `OU_FULL` / `EQUIRECT_360_MONO` images → `STANDARD_PANEL_FALLBACK` with reason `auto-immersive-disabled`. This resolves strategic §6.2 — VR photo follows the same rule as VR video stereo content.

**Verification:**

- `Grep` — `MediaType.IMAGE` matches at least 4 times in this file.
- `Grep` — `plain-2d-content` matches at least 1 time in this file.
- `Grep` — `vrAutoImmersive = false` matches at least 10 times in this file (combined Step 01.1 + 01.2).

**Status:** `[x]` done

---

### Step 01.3 — Add test cases for the `vrAutoImmersive=true` × all-stereo matrix

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add JUnit `@Test` methods that pin the legacy behaviour with `AppSettings(vrAutoImmersive = true)`: every stereoscopic / spherical video → `IMMERSIVE_VIDEO` with reason `immersive-video`; every stereoscopic / spherical image → `IMMERSIVE_STATIC_IMAGE` with reason `immersive-static-image`; `MONO` video → `CINEMA_IMMERSIVE` with reason `plain-2d-video`. These tests must pass against the current implementation without source changes — they pin the existing successful behaviour.

**Verification:**

- `Grep` — `vrAutoImmersive = true` matches at least 6 times in this file.
- `Grep` — `IMMERSIVE_VIDEO, decision.route` matches at least 3 times.
- `Grep` — `IMMERSIVE_STATIC_IMAGE, decision.route` matches at least 2 times.
- `Grep` — `CINEMA_IMMERSIVE, decision.route` matches at least 1 time.

**Status:** `[x]` done

---

### Step 01.4 — Add invariant assertion: route and reason are coupled

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a single parameterised-style `@Test` (or one private helper plus several callers) that, for every reason string the helper currently emits (`disable-3d-vr`, `user-forced-panel`, `user-forced-immersive`, `auto-immersive-disabled`, `plain-2d-video`, `plain-2d-content`, `immersive-video`, `immersive-static-image`, `unsupported-immersive-media-type`), asserts the route is allowed for that reason — i.e. `auto-immersive-disabled`, `plain-2d-content`, `disable-3d-vr`, `user-forced-panel` MUST yield `STANDARD_PANEL_FALLBACK`; `plain-2d-video` is allowed `STANDARD_PANEL_FALLBACK` or `CINEMA_IMMERSIVE`; `unsupported-immersive-media-type` must yield `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE`. The test runs the matrix that produces each reason and inspects the returned `VrRouteDecision` — asserting the (route, reason) pair is in the allowed set.

**Verification:**

- `Grep` — `(route, reason)` or `route.*reason` invariant comment matches at least 1 time in this file.
- `Grep` — function name like `route and reason are coupled` or `route reason coupling` matches in this file.
- `Grep` — `assertTrue` or `assertEquals` referencing `decision.logReason` matches at least 5 times.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `vr debug` (test sources are in `testVr` source set).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `VrRouteDecisionHelperTest.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API of `VrRouteDecisionHelper` changed (new method, new field): `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 may rely on the new tests as a regression net for any defensive change made in `VrPlayerActivity.resolvePlaybackRoute`. If the matrix tests fail against the current source, Phase 02 must investigate the helper itself before adding the defensive invariant.

---

## Rollback Plan

Revert phase commit — only test sources are added, no production behaviour changes.
