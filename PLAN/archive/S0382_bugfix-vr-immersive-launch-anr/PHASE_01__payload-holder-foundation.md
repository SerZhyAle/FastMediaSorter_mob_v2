# Phase 01 - Payload Holder Foundation

**Strategic spec:** [`../S0382_bugfix-vr-immersive-launch-anr.md`](../S0382_bugfix-vr-immersive-launch-anr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Introduce a process-scoped holder that maps a primitive token to a VR launch/return payload, so Intent extras can carry only the token. No producer or consumer is rewired in this phase.

---

## Prerequisites

- [ ] Strategic §6.1 (transport form) is Resolved - confirmed: key + process-scoped holder (ADR-2).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchPayloadHolder.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/VrLaunchPayloadHolderTest.kt` | New | ≤ 120 |

> Holder is a flavor-neutral passive cache - it lives in `src/main/java/` next to the existing launch contract, carries no `BuildConfig` gate, and is shared by both `vr` and `noLegal` builds via the shared contract.

---

## Steps

### Step 01.1 - Add `VrLaunchPayloadHolder`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchPayloadHolder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `@Singleton` class `VrLaunchPayloadHolder` (Hilt constructor injection, no module needed) backed by a thread-safe map from a primitive `String` token to an opaque payload value. Expose `put(payload): String` returning a freshly generated token, `peek(token): T?` (non-removing read for re-entrant reads), and `consume(token): T?` (removing read). Token generation must not use wall-clock or RNG forbidden in this repo's tooling context - use a monotonic counter held in the holder. Store payloads as `Any` and let callers cast, or use a small sealed wrapper - keep it generic enough to carry both the launch input and the return target. Bound the map so a leaked token cannot grow it without limit (evict oldest beyond a small cap).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchPayloadHolder.kt` exists.
- `Grep` - `class VrLaunchPayloadHolder` matches exactly once.
- `Grep` - `fun put(` and `fun consume(` and `fun peek(` each present.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 4/4 PASS. `class VrLaunchPayloadHolder` ×1; `put`/`peek`/`consume` present; no `Log.d`. File: core/xr/VrLaunchPayloadHolder.kt (+63 LOC). Dev log + catalog sync recorded.

---

### Step 01.2 - Unit-test holder semantics

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/VrLaunchPayloadHolderTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a JVM unit test covering: put returns a unique token; peek returns the same payload twice without removing; consume returns the payload then null on second consume; unknown token returns null; the bound-cap eviction drops the oldest entry. No Robolectric needed - the holder is pure JVM.

**Verification:**

- `Glob` - test file exists.
- `Grep` - `class VrLaunchPayloadHolderTest` matches exactly once.
- Run `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.core.xr.VrLaunchPayloadHolderTest"` - per-class XML report shows all tests pass.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS. `testStandardDebugUnitTest --tests "…VrLaunchPayloadHolderTest"` -> BUILD SUCCESSFUL (5 tests, filter-scoped green). Unblocked a pre-existing test-source-set compile break (GetMediaFilesUseCaseTest missing `mediaCapabilities` ctor arg) to let the suite compile - logged separately, out of S0382 scope. File: test/core/xr/VrLaunchPayloadHolderTest.kt (+50 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`VrLaunchPayloadHolder` exists and is unit-tested but unused. Phases 02 and 03 inject it into the transport producers/consumers and replace the Serializable extras with tokens.

---

## Rollback Plan

Revert phase commit(s) - new isolated file plus test, no user-facing surface or data migration touched.
