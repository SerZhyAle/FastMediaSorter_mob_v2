# Phase 04 — Atomic Route + Reason Logging

**Strategic spec:** [`../S0018_bugfix-vr-auto-immersive-route-broken.md`](../S0018_bugfix-vr-auto-immersive-route-broken.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Guarantee that `route=` and `reason=` always come from the same `VrRouteDecision` instance and are never logged separately. Eliminates the structural possibility of the symptom "log says one route, helper returned another".

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt` | Modified | ≤ 200 |

---

## Steps

### Step 04.1 — Move the route-decision log call into the helper

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a public extension or instance function on `VrRouteDecision` named `logTo(prefix: String, currentFile: MediaFile, requestedStereoMode: StereoMode, autoDetect: Boolean)` that emits exactly one Timber.i log line in the format the activity currently uses: `"VrPlayerActivity: route decision file=%s type=%s requested=%s effective=%s autoDetect=%b route=%s reason=%s"`. The function reads `this.route` and `this.logReason` directly — these two fields are guaranteed to come from the same instance.

**Verification:**

- `Grep` — `fun .*logTo` or `fun VrRouteDecision\.logTo` matches exactly once in `VrRouteDecisionHelper.kt`.
- `Grep` — `route decision file=%s` matches exactly once in `VrRouteDecisionHelper.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `VrRouteDecisionHelper.kt`.

**Status:** `[x]` done

---

### Step 04.2 — Replace the activity-side log call with the new function

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `buildRouteDecision(..)`, replace the existing `Timber.i("VrPlayerActivity: route decision file=%s..", ..)` call with a single invocation of the new helper function (`routeDecision.logTo(..)`). Remove the existing format string from the activity. Confirm by re-reading the function that the activity no longer references `routeDecision.logReason` separately.

**Verification:**

- `Grep` — `route decision file=%s` returns zero hits in `VrPlayerActivity.kt` (moved out).
- `Grep` — `routeDecision\.logTo\(` matches at least 1 time in `VrPlayerActivity.kt`.
- `Grep` — `routeDecision\.logReason` only appears inside the Phase 02 defensive recovery block (≤ 3 hits, all within 12 lines of the `// recovery: not a route decision emission` marker). No separate decision-emission log line outside the recovery block.

**Status:** `[x]` done

---

### Step 04.3 — Forbid additional `route=`/`reason=` log lines outside the helper

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Audit `VrPlayerActivity.kt` for any other `Timber.*` call that emits both a `route=` and `reason=` token in the same string. If any are found, either delete them or rewrite them to call `routeDecision.logTo(..)`. The defensive guard added in Phase 02 step 02.3 is permitted to use `Timber.e` with `(route, reason)` — that is a recovery log, distinct from the decision log; mark it with a comment `// recovery: not a route decision emission` to disambiguate.

**Verification:**

- `Grep` — `Timber\..*route=.*reason=` matches at most 2 times in `VrPlayerActivity.kt` (one for the recovery log; one is acceptable).
- `Grep` — `// recovery: not a route decision emission` matches exactly once in `VrPlayerActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `vr debug`.
- [ ] All Phase 01 matrix tests still green.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for `VrPlayerActivity.kt` and `VrRouteDecisionHelper.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, the on-device verification in Phase 05 reads route+reason from a single source — the deployed APK's helper output is the ground truth.

---

## Rollback Plan

Revert phase commit — only logging surface changes, no behavioural change.
