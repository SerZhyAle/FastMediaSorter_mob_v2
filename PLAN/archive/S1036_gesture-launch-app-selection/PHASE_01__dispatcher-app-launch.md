# Phase 01 - Dispatcher launches the slot's chosen app

**Strategic spec:** [`../S1036_gesture-launch-app-selection.md`](../S1036_gesture-launch-app-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none (Phases 02-04 are independent of it; Phase 05 records the capability it delivers)
**Steps done:** 2 / 2
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Make `OPEN_APP` launch the package stored in the triggering slot's payload, falling back to today's bring-FastMediaSorter-to-front behaviour when the payload is empty or the package cannot be resolved. No UI and no settings-schema change.

---

## Prerequisites

- [ ] Strategic §6 items 1 and 2 are `Resolved` - they are, as of 2026-08-09.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired before the source edit and released right after it (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | Modified | ≤ 450 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcherOpenAppTest.kt` | New | ≤ 160 |

> The dispatcher is 419 LOC before this phase, so Rule 5's backup threshold (>500 LOC) is not reached and Rule 2's 1500-LOC ceiling is far away. Keep the added branch small enough that the file stays under 500 - a later edit crossing that line acquires a backup step.

---

## Steps

### Step 01.1 - Read the slot payload in the `OPEN_APP` branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the `ScreenshotGestureAction.OPEN_APP` branch of `handlePreCaptureAction` so it resolves the slot's stored package before launching. Read it with the existing `payloadFor(zone, direction)` helper, exactly as the `OPEN_URL` branch already does. A blank payload keeps the current behaviour: call the existing `launchApp(context)` self-launch. A non-blank payload resolves a launch intent for that package the way `LaunchActionHandler.launchPackage` already does - `getLaunchIntentForPackage`, `FLAG_ACTIVITY_NEW_TASK`, `runCatching` around `startActivity` - and when the intent is null or the start throws, it falls back to the same `launchApp(context)` rather than doing nothing. Keep the branch's `true` return. Do not add a `PackageManager` constructor parameter; reach it through the `Context` the function already receives. Watch detekt: keep the new helper's returns at two or fewer, no line past 120 characters, braces on every `if`.

**Why:**

Strategic §2 goal 3 requires the gesture to launch the app stored for that slot, and §3.2 "Совместимость данных" requires a deleted or missing app to be treated as "not selected" so the gesture degrades to the old behaviour instead of silently doing nothing - the risk table names exactly this failure ("Жест «молча» ничего не делает").

**Verification:**

- `Grep` - `payloadFor(zone, direction)` appears at least twice in the file (the pre-existing `OPEN_URL` use plus the new one).
- `Grep` - `getLaunchIntentForPackage` appears in the file.
- `Grep` - `launchApp(context)` still present, reachable from the `OPEN_APP` branch as the fallback.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 5\5 PASS. `payloadFor(zone, direction)` now at lines 109 and 169; `getLaunchIntentForPackage` at 211; `launchApp(context)` reachable as the fallback from all three failure paths (208, 214, 221); zero `Log.d(` hits; `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 38s). Files: `core/screencapture/ScreenshotGestureActionDispatcher.kt` (+24 LOC, 419 -> 443). Two KDoc blocks that described the old bring-to-front meaning were corrected in the same edit, since Rule 8 makes them requirements rather than decoration. Dev log recorded.

---

### Step 01.2 - Cover the three outcomes with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcherOpenAppTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a unit test class covering the `OPEN_APP` branch's three outcomes: a slot whose payload is blank starts the app's own package; a slot whose payload names a package with a resolvable launch intent starts that package; a slot whose payload names a package with no resolvable launch intent falls back to the app's own package. Follow the construction style of `ScreenshotSettingsStoreTest` for the settings side and fake the `PackageManager`/`Context` rather than touching a real one. Assert on the package carried by the started intent, not on a log line.

**Why:**

Research artifact 01 §9 establishes that the dispatcher has no test of any kind today, and this branch is the one part of the ticket that changes behaviour with no screen to look at - a regression here is invisible until a user reports that a gesture stopped working.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcherOpenAppTest.kt` exists.
- `Grep` - three `@Test` annotations in that file.
- `.\a.ps1 fu` reports this class passing; record `expected: 3 passed | actual: <n> passed`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. File exists, three `@Test` annotations, `expected: 3 passed | actual: 3 passed` read from `TEST-..ScreenshotGestureActionDispatcherOpenAppTest.xml` (tests 3, failures 0, errors 0, skipped 0, fresh timestamp). Ran through `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*ScreenshotGestureActionDispatcherOpenAppTest"` rather than the whole suite - the same sanctioned script `a.ps1 fu` calls, with the filter it already supports, which avoids the known full-suite worker-death truncation. Files: `core/screencapture/ScreenshotGestureActionDispatcherOpenAppTest.kt` (New, 104 LOC). Kept off Robolectric by mocking the intents: the dispatcher only forwards what the PackageManager returns, so identity is the whole assertion. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 57s, APK `v2.60.8082.309-DEBUG` produced. `.\a.ps1 fk` also exit 0 after step 01.1, and the targeted unit run compiled the test source set.
- [x] `Grep` for `TODO(phase-01)` returns zero hits (0 occurrences across 0 files).
- [x] Dev log entry added for every file in "Files Touched" - both through `post-change.ps1`, each returning `post-change: PASS`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Phase-boundary audit (2026-08-09)

Scope: Layer 1 always; Layer 2 because the changed function is `suspend` and reads settings. Layers 3 and 4 not applicable - no listener, observer or Room surface was touched.

- **Layer 1 - architecture and readability: clean.** The new helper is private, has one responsibility, sits in the same `core` layer as its caller and introduces no UI or repository knowledge. Two explicit returns, inside detekt's ceiling. Its KDoc states why the two fallbacks exist rather than what the code does.
- **Layer 2 - coroutines: clean.** No coroutine is created, no dispatcher is chosen and no scope is captured; the helper is an ordinary function called from an already-suspending branch. The settings read reuses the established `payloadFor` path rather than adding a second read.
- **P3 (noted, not fixed):** `OPEN_APP` now performs one DataStore read on trigger where it previously performed none. This matches what `OPEN_URL` has always done on the same path, and the alternative - caching the payload - would trade a correct value for a stale one on a gesture the user may have just reconfigured. No action.
- **Verified against the strategic risk table:** the "gesture silently does nothing" risk is closed by construction - every failure path ends in `launchApp`, and the unit test asserts the fallback for both the blank and the removed-package cases.

---

## Handoff Notes to Next Phase

The invariant this phase establishes: a slot payload is a package name, and an unresolvable one is indistinguishable from an empty one. Everything downstream may write a package into the payload without checking whether it is still installed, because the dispatcher already degrades safely.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed. Existing users have empty payloads, so reverting restores byte-identical behaviour.
