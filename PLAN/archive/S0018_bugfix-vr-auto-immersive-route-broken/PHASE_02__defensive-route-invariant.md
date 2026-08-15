# Phase 02 — Defensive Route Invariant in Player Coordinator

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

Make `VrPlayerActivity.resolvePlaybackRoute` impossible to flip the route after the helper decision: the `when (routeDecision.route)` arm that says `STANDARD_PANEL_FALLBACK` MUST always launch the standard player fallback, and any code reading `routeDecision` does so via a single accessor that exposes route and reason as one inseparable pair.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] All matrix tests in `VrRouteDecisionHelperTest` pass against current source.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1500 (current ~1700; this phase removes lines, does not add) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt` | Modified | ≤ 200 |

> File `VrPlayerActivity.kt` is currently above the 1000-LOC soft cap. Step 02.3 includes a timestamped backup in `temp/` before edits.

---

## Steps

### Step 02.1 — Audit and document all assignments to `VrLaunchRoute` outside the helper

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Run a project-wide search for assignments to a `VrLaunchRoute` value outside `VrRouteDecisionHelper` (e.g. `route = VrLaunchRoute.`, `VrLaunchRoute.CINEMA_IMMERSIVE` literals). For each such site, either (a) confirm it is a fallback that does not depend on `routeDecision.route` (e.g. error path), or (b) remove it and route the decision back through the helper. Add a `// invariant: route assignments live only inside VrRouteDecisionHelper.decide` comment above the helper's class declaration.

**Verification:**

- `Grep` — `route\s*=\s*VrLaunchRoute\.` outside the helper file and outside `*Test.kt` returns zero hits in `app_v2/src/`. (Reads of the enum in `when (routeDecision.route)` arms inside `VrPlayerActivity.kt` are permitted.)
- `Grep` — `invariant: route assignments live only inside` matches exactly once in `VrRouteDecisionHelper.kt`.

**Status:** `[x]` done

---

### Step 02.2 — Make `VrRouteDecision` carry an immutable (route, reason) pair only constructible by the helper

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mark the `VrRouteDecision` data class constructor `internal` and ensure all its fields are `val`. Move the `VrRouteDecision` declaration into the same Kotlin file as `VrRouteDecisionHelper` (it is already there — verify, do not duplicate). Confirm by inspection that no test or production code constructs a `VrRouteDecision` outside the helper.

**Verification:**

- `Grep` — `internal data class VrRouteDecision` matches exactly once in the helper file.
- `Grep` — `VrRouteDecision\(` outside the helper file returns zero hits in `app_v2/src/main/` and `app_v2/src/vr/`.

**Status:** `[x]` done

---

### Step 02.3 — Add runtime guard inside `resolvePlaybackRoute` that catches accidental panel→immersive flips

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Before edits: copy `VrPlayerActivity.kt` to `temp/VrPlayerActivity.kt.<UTC-timestamp>.backup`. Then in `resolvePlaybackRoute`, immediately after `val routeDecision = buildRouteDecision(..)`, insert a `check(..)` block: when `routeDecision.logReason` ∈ {"auto-immersive-disabled", "user-forced-panel", "disable-3d-vr", "plain-2d-content"}, the route MUST be `STANDARD_PANEL_FALLBACK` — otherwise call `Timber.e` with the (route, reason) pair and force-launch standard player fallback as a defensive recovery. The check is the runtime mirror of the unit-test invariant from Phase 01 step 01.4. Use `Timber.e`, never `Log.e`.

**Verification:**

- `Glob` — file `temp/VrPlayerActivity.kt.*.backup` exists.
- `Grep` — `auto-immersive-disabled` matches in `VrPlayerActivity.kt` at least 1 time (the new check).
- `Grep` — `Timber.e\(.*route` matches at least 1 time near the new check.
- `Grep` — `Log\.d\(` returns zero hits in `VrPlayerActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `vr debug`.
- [ ] All Phase 01 matrix tests still green.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `VrPlayerActivity.kt` and `VrRouteDecisionHelper.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 03 is independent of this phase (different subsystem) and can run in parallel; Phase 04 depends on the route+reason being a single inseparable pair (established here).

---

## Rollback Plan

Revert phase commit. Restore `temp/VrPlayerActivity.kt.<UTC-timestamp>.backup` if rollback after merge requires it.
