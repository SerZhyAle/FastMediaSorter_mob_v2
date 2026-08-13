# Phase 02 - Program-wide orientation applier

**Strategic spec:** [`../S0439_screen-rotation-follow-os.md`](../S0439_screen-rotation-follow-os.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Introduce one shared orientation policy point that applies the program flag to every non-player window via `Application.ActivityLifecycleCallbacks`, excluding player-family activities that self-manage orientation.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/SelfManagedScreenOrientation.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/orientation/AppOrientationManager.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 02.1 - Marker interface for self-managed activities

**Files:** `core/ui/SelfManagedScreenOrientation.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Add an empty marker interface `SelfManagedScreenOrientation`. Activities implementing it opt out of the program-wide orientation applier because they drive `requestedOrientation` themselves. KDoc one line: why the marker exists (player family manages orientation via `ScreenRotationManager`).

**Verification:**

- `Glob` - `core/ui/SelfManagedScreenOrientation.kt` exists.
- `Grep` - `interface SelfManagedScreenOrientation` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. Created core/ui/SelfManagedScreenOrientation.kt (marker interface).

---

### Step 02.2 - Mark the player-family activities

**Files:** `ui/player/PlayerActivity.kt`, `ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Make `PlayerActivity` and `PhotoVideoStandaloneActivity` implement `SelfManagedScreenOrientation` (add to the class header alongside existing supertypes). No behaviour change - these already manage orientation via `ScreenRotationManager`.

**Verification:**

- `Grep` - `SelfManagedScreenOrientation` present in `PlayerActivity.kt`.
- `Grep` - `SelfManagedScreenOrientation` present in `PhotoVideoStandaloneActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. PlayerActivity + PhotoVideoStandaloneActivity now implement SelfManagedScreenOrientation (the only two ScreenRotationManager owners; backups in temp/). Other standalone viewers intentionally fall under the program policy.

---

### Step 02.3 - The applier

**Files:** `core/orientation/AppOrientationManager.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `AppOrientationManager` - a singleton (`@Singleton`, `@Inject constructor`) implementing `Application.ActivityLifecycleCallbacks`. Inject `SettingsRepository` and the application-scope `CoroutineScope` (`@ApplicationScope`) and `@ApplicationContext Context`. On construction, collect `settingsRepository.getSettings()` in the injected scope and cache the latest `programFollowSystemRotation`; when it changes, re-apply to the currently resumed non-self-managed activity. Compute `hasAccelerometer` once from the package manager. In `onActivityResumed` (and `onActivityCreated`), if the activity is `SelfManagedScreenOrientation`, return; if `hasAccelerometer` is false, return (leave manifest default); else set `activity.requestedOrientation = if (programFollowSystemRotation) SCREEN_ORIENTATION_UNSPECIFIED else SCREEN_ORIENTATION_LOCKED`. Track the currently-resumed activity in a `WeakReference`. Log decisions at `Timber.d` without a ticket id. Leave the other `ActivityLifecycleCallbacks` overrides empty-bodied (interface contract, not swallowed errors).

**Verification:**

- `Glob` - `core/orientation/AppOrientationManager.kt` exists.
- `Grep` - `class AppOrientationManager` matches once.
- `Grep` - `Application.ActivityLifecycleCallbacks` present.
- `Grep` - `SCREEN_ORIENTATION_UNSPECIFIED` and `SCREEN_ORIENTATION_LOCKED` both present.
- `Grep` - `is SelfManagedScreenOrientation` present (player-family exclusion).
- `Grep` - `programFollowSystemRotation` present.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 7/7 PASS. Created core/orientation/AppOrientationManager.kt (@Singleton ActivityLifecycleCallbacks; program on -> UNSPECIFIED, off -> LOCKED; skips SelfManagedScreenOrientation; no-accelerometer -> no-op; settings re-apply dispatched to Main). Timber.d only.

---

### Step 02.4 - Register the applier in the Application

**Files:** `FastMediaSorterApp.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Inject `AppOrientationManager` into `FastMediaSorterApp` and call `registerActivityLifecycleCallbacks(appOrientationManager)` in `onCreate()` after the Hilt graph is available. Build to confirm wiring.

**Verification:**

- `Grep` - `registerActivityLifecycleCallbacks(` present in `FastMediaSorterApp.kt`.
- `Grep` - `AppOrientationManager` injected (field or constructor) in `FastMediaSorterApp.kt`.
- `/build` - standard debug compiles, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. FastMediaSorterApp injects appOrientationManager (field 142) and calls registerActivityLifecycleCallbacks(appOrientationManager) (205). `a.ps1 fk` PASS after kapt-stall recovery (initial fail was a stuck Gradle daemon, not a code error). Also closed the AppSettings<->CSV invariant: appended empty preset rows for playerFollowSystemRotation + 2 pre-existing S0452 Set fields via check_device_profile_presets.ps1 -AddMissing.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (handled in Phase 05).

---

## Handoff Notes to Next Phase

- Non-player windows now consult `programFollowSystemRotation`: on → follow OS (UNSPECIFIED), off → locked. Player-family activities are excluded via `SelfManagedScreenOrientation`.
- Player precedence (program OR player) is still pending - Phase 03.

---

## Rollback Plan

Revert phase commit(s). Removing the `registerActivityLifecycleCallbacks` call restores the prior implicit per-activity orientation. No data surface touched.
