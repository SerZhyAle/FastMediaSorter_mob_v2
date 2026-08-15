# Phase 01 - baseline-unblock

**Strategic spec:** [`../S0275_test_suite_triage.md`](../S0275_test_suite_triage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 1 / 2
**Started:** 2026-05-20
**Completed:** -

---

## Objective

Restore `standard` unit-test execution by removing the known XR flavor-boundary compile blocker and producing the first runnable baseline inventory.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Use the normalized command `:app_v2:testStandardDebugUnitTest -Pchaquopy.enabled=false` when validating `standard` on machines where Chaquopy opt-in may be enabled.
- [ ] Strategic §6 compile-barrier note is understood before editing tests.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt` | Modified | ≤ 250 |
| `app_v2/src/testVr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt` | New | ≤ 250 |
| `temp/S0275_standard_runtime_inventory.md` | New | ≤ 300 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split first.

---

## Steps

### Step 01.1 - Isolate VR-only XR detector coverage from shared standard tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt`, `app_v2/src/testVr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move `XrEnvironmentDetectorImplTest` out of the shared `src/test` surface so `standard` no longer compiles against the `src/vr` implementation. Keep the real detector covered from a VR-only unit-test source set, and keep shared flavors bound to `vrStub` / `NoOpXrEnvironmentDetector` only.

**Verification:**

- `Glob` - `app_v2/src/testVr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt` exists.
- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt` returns zero hits.
- `Grep` - `class XrEnvironmentDetectorImplTest` matches exactly once under `app_v2/src/testVr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImplTest.kt`.

**Status:** `[x]` done

---

### Step 01.2 - Capture the first runnable standard-suite inventory

**Files:** `temp/S0275_standard_runtime_inventory.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `cmd /c ".\gradlew.bat :app_v2:testStandardDebugUnitTest -Pchaquopy.enabled=false"` after the XR test is isolated. Record the result in `temp/S0275_standard_runtime_inventory.md`: command used, whether compile passed, whether XML reports were produced, and the first-failure cause per class. If the suite is green, record zero failures explicitly.

**Verification:**

- `Glob` - `temp/S0275_standard_runtime_inventory.md` exists.
- `Grep` - `Gradle invocation: :app_v2:testStandardDebugUnitTest -Pchaquopy.enabled=false` present.
- `Grep` - `Compile barrier removed: yes` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `cmd /c ".\gradlew.bat :app_v2:testStandardDebugUnitTest -Pchaquopy.enabled=false"` no longer fails in `:app_v2:compileStandardDebugUnitTestKotlin` on `XrEnvironmentDetectorImplTest` unresolved references.
- [ ] `temp/S0275_standard_runtime_inventory.md` lists the current baseline classes or explicitly records `0` failures.
- [ ] `dev/CHANGELOG.md` has an entry for every touched file via `add_to_dev_log.ps1` / `post-change.ps1`.
- [ ] If any `app_v2/**/*.kt` file changed: `scripts/catalog_sync.ps1 -Module app_v2` executed.

---

## Handoff Notes to Next Phase

Blocked on S0274 compile fallout. The XR-specific shared-test barrier is fixed, but fresh `standard` builds still fail in `VideoPlayerManager` / extracted helper integration before XML reports are produced. Resume Step 01.2 only after the S0274 compile state is stable enough for `testStandardDebugUnitTest` to reach runtime.

---

## Rollback Plan

Revert the XR test relocation and delete the temp inventory file. No data migration or user-facing surface changed.