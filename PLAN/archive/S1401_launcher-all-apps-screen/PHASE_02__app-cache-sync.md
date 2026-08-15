# Phase 02 - Cache fill, icon store and invalidation

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 5 / 6
**Started:** -
**Completed:** -

---

## Objective

Fill the cache from the system, persist one icon file per app, keep both current through package and locale events, and switch the two existing app-list readers onto the cache.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1401 phase 02"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/InstalledAppIconStore.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/RefreshInstalledAppsUseCase.kt` | New | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/apps/InstalledAppsChangeReceiver.kt` | New | ≤ 120 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/QueryLaunchableAppsUseCase.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt` | Modified | ≤ 90 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | - |

> `AndroidManifest.xml` is 894 LOC - back it up to `temp/S1401/` before editing (CLAUDE.md Rule 5), covered by Step 02.3.
>
> Everything stays in `src/main`: the receiver must fire in flavors without launcher mode too, because `AppPickerDialogFragment` reads the same cache (research artifact 03).

---

## Steps

### Step 02.1 - Add the icon store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/InstalledAppIconStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class InstalledAppIconStore` owning a dedicated subdirectory of `context.cacheDir`. Give it `write(packageName: String, icon: Drawable): String?` returning the stored file name, `fileFor(fileName: String?): File?`, `delete(packageName: String)` and `retainOnly(packageNames: Set<String>)`. Rasterise the drawable at the grid cell size read from a dimension resource, not at its native size, and compress to PNG. Every filesystem call runs on `Dispatchers.IO`. A failed write returns null and logs at `Timber.i` - a missing icon is a recoverable state, not an error.

**Why:**

Research artifact 02 rejected storing icon bytes in the database because a hundred apps would put megabytes of binary into the file that every unrelated query, backup and migration then carries; files in the cache directory keep the database text-and-integers and let the system reclaim them under storage pressure. Rasterising at cell size rather than native size is what keeps the strategic §7 "icon cache grows" risk mitigated.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/InstalledAppIconStore.kt` exists.
- `Grep` - `class InstalledAppIconStore` matches exactly once.
- `Grep` - `retainOnly` present.
- `Grep` - `cacheDir` present.

**Status:** `[x]` done

---

### Step 02.2 - Add the refresh use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/RefreshInstalledAppsUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `RefreshInstalledAppsUseCase` with two entry points: `refreshAll()` and `refreshPackage(packageName: String)`. `refreshAll` resolves every launchable activity the way `QueryLaunchableAppsUseCase` does today, excludes this app's own package, reads label, first-install time, last-update time, category and the system flag for each, writes the icon through `InstalledAppIconStore`, replaces the cache contents and calls `retainOnly` so files of removed apps go with them. `refreshPackage` updates or removes one row and its icon. Add `refreshLabelsOnly()` that re-reads labels and keeps every existing icon file. Everything runs on `Dispatchers.IO`.

**Why:**

Strategic §2 goal 6 requires the list to follow reality without a manual refresh, and §3.2 forbids the refresh from blocking the desktop; splitting whole-list, single-package and labels-only work is what lets a package event cost one row instead of a full sweep. `refreshLabelsOnly` exists because app labels are locale-dependent while icons are not (research artifact 03).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/RefreshInstalledAppsUseCase.kt` exists.
- `Grep` - `class RefreshInstalledAppsUseCase` matches exactly once.
- `Grep` - `refreshAll`, `refreshPackage` and `refreshLabelsOnly` each present.
- `Grep` - `queryIntentActivitiesCompat` present - the deprecated raw-int overload is banned by CLAUDE.md Rule 21.

**Status:** `[x]` done

---

### Step 02.3 - Add the package and locale receiver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/apps/InstalledAppsChangeReceiver.kt`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Copy `AndroidManifest.xml` to `temp/S1401/` with a timestamp before editing. Create `InstalledAppsChangeReceiver` as a `@AndroidEntryPoint`-style Hilt `BroadcastReceiver` that handles `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED` and `ACTION_LOCALE_CHANGED`. A package action routes to `refreshPackage` with the package parsed from the intent data; ignore the replace half of an update that arrives with `EXTRA_REPLACING` so an update is handled once. A locale change routes to `refreshLabelsOnly`. Do the work inside `goAsync()` on an injected application scope. Declare the receiver in the manifest with an intent filter carrying `<data android:scheme="package" />` for the package actions and a second filter for the locale action.

**Why:**

Research artifact 03 rejected `LauncherApps.Callback` because it only works while this app holds the home role and would therefore go silent in every flavor without launcher mode and the moment the user hands the role back - which is when a stale list is hardest to notice. The package broadcasts are exempt from the implicit-broadcast restrictions, so a manifest receiver still fires, which is what makes strategic §2 goal 6 hold without the app running.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/apps/InstalledAppsChangeReceiver.kt` exists.
- `Grep` - `class InstalledAppsChangeReceiver` matches exactly once.
- `Grep` - `InstalledAppsChangeReceiver` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` - `android:scheme="package"` present in the manifest.
- `Glob` - a timestamped `AndroidManifest.xml` copy exists under `temp/S1401/`.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 02.4 - Seed the cache at startup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a background step to the deferred-startup chain that calls `refreshAll()` when the cache is empty or its stored `cacheFormatVersion` differs from the current one, and otherwise does nothing. The call must not be awaited on any startup-critical path.
>
> Host corrected during implementation: `DeferredStartupWorker`, not `AppStartupInitializer`. See the Step Log entry of 2026-08-05 - adding two constructor parameters to `AppStartupInitializer` broke two frozen detekt baseline signatures on debt that predates this ticket, and the worker is the actual host of the deferred chain.

**Why:**

Strategic §11 criterion 3 requires the screen to open without a wait on the first entry after a reboot, which only holds if something fills the cache before the user asks for the list. Rebuilding on a format-version mismatch is the recovery path the §3.2 data-compatibility constraint calls for, since every row is recoverable from the system and needs no migration.

**Verification:**

- `Grep` - `RefreshInstalledAppsUseCase` present in `DeferredStartupWorker.kt`.
- `Grep` - `cacheFormatVersion` present in `DeferredStartupWorker.kt` or in the use case it calls.
- `Grep -n "runBlocking"` returns zero hits in the added code.

**Status:** `[x]` done

---

### Step 02.5 - Read the existing app-list use case from the cache

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/QueryLaunchableAppsUseCase.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Rewrite `QueryLaunchableAppsUseCase` to read the cache instead of the package manager, keeping its existing signature and its `LaunchableApp(packageName, label, icon: Drawable)` return type so both current callers keep compiling untouched. Decode the cached PNG into a drawable on `Dispatchers.IO`. If the cache is empty, run `refreshAll()` once and then read - so a first call still returns a correct list rather than nothing.

**Why:**

Strategic ADR-1 puts the cache in the shared layer precisely so the app-launch panel picker gets the same speed-up as the launcher, and keeping the return type means that benefit lands without touching `AppPickerDialogFragment` at all. The empty-cache fallback keeps the pre-existing contract honest: a caller that asks for the list must never get an empty one just because startup seeding has not finished.

**Verification:**

- `Grep` - `InstalledAppsRepository` present in `QueryLaunchableAppsUseCase.kt`.
- `Grep -n "queryIntentActivitiesCompat"` returns zero hits in `QueryLaunchableAppsUseCase.kt` - enumeration now belongs to the refresh use case only.
- `Grep` - `data class LaunchableApp` still declares `icon` in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.6 - Prove the speed-up on a device

**Files:** none - measurement step against the built app
**Depends on:** Step 02.5

**Prompt for developer:**

> Install the standard debug build on the emulator or device, open the Start-menu "All apps" row twice - once on a cold start, once after - and record the time to first painted grid for both. Capture the numbers in `temp/S1401/` rather than in the plan. If the cold-start open still shows a visible wait, the cache is not being seeded and the phase is not done.

**Why:**

Strategic §11 criterion 3 is stated as an observable outcome - no visible delay, including the first open after a reboot - and CLAUDE.md section 12 forbids claiming it without a fresh run to cite. Every earlier step in this phase is invisible from the outside, so this is the only place the phase's whole purpose can be checked.

**Verification:**

- `Glob` - a measurement note exists under `temp/S1401/`.
- Recorded value: second open shows no loading state; cold open under one second to first painted grid.

**Status:** `[ ]` not done - MANUAL-REQUIRED (see Step Log 2026-08-05)

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every file listed in "Files Touched".
- [ ] Dev log entry added for the phase.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings. This phase trips several audit triggers at once (new receiver, new long-lived singleton, background work, cache path), so do not skip it.
- [ ] `temp/CODE.LOCK` released.

---

## Step Log

- 2026-08-05 - Step 02.1 done. `InstalledAppIconStore` added; it takes `INSTALLED_APP_ICON_DIR` from the repository rather than redeclaring the directory name. Files Touched extended by `app_v2/src/main/res/values/dimens.xml`: the prompt asks for the raster size to come from a dimension resource and no launcher cell dimen existed in `src/main` (the cell edge is computed at runtime from `BASE_CELL_DP`), so `installed_app_icon_cache_size` (96dp) was added. Verification: 4/4 PASS.
- 2026-08-05 - Step 02.2 done. `RefreshInstalledAppsUseCase` added with the three entry points. `ApplicationInfo.category` is guarded by an API 26 check because `src/main` also ships in the `legacy` flavor at minSdk 23, where the field does not exist; uncategorised is the documented fallback. Verification: 4/4 PASS, `.\a.ps1 fk` exit 0.
- 2026-08-05 - Step 02.3 done. Receiver + two manifest filters; manifest backed up to `temp/S1401/AndroidManifest.xml.20260805_192900.bak`. The `goAsync` body catches `CancellationException` first and then `Exception`, because a throw from a system-broadcast coroutine would crash the app during someone else's install; the swallowed-cancellation gate requires exactly that arm order. Verification: 6/6 PASS, `.\a.ps1 fr` exit 0.
- 2026-08-05 - Step 02.4 done. Seeding runs as a deferred startup task, so it is off every startup-critical path by construction and its failure is already logged by `runDeferredTask`. Verification: 3/3 PASS.
- 2026-08-05 - Step 02.5 done. `QueryLaunchableAppsUseCase` now reads the cache; the icon falls back to `PackageManager.defaultActivityIcon` because the cache directory is reclaimable and the return type promises a non-null drawable. Verification: 4/4 PASS.
- 2026-08-05 - Step 02.4 relocated, plan corrected. Seeding first went into `AppStartupInitializer` as the prompt said; its two new constructor parameters and two new imports changed the signature strings of two frozen detekt baseline entries (`LongParameterList` on a 12-parameter constructor, `ImportOrdering` on an already mis-sorted import block), so the scoped gate reported pre-existing debt as this ticket's. Re-baselining would have quietly accepted it, and `@Suppress` is forbidden on an already-baselined member. Moved instead to `DeferredStartupWorker` - the actual host of the deferred chain, a 6-parameter constructor with no baseline entry - and the staleness decision moved into `RefreshInstalledAppsUseCase.refreshIfStale()`, which already holds the repository. `AppStartupInitializer` is back to its original bytes.
- 2026-08-05 - detekt scoped gate FAILed on five findings and all five were fixed, not suppressed away: two `ReturnCount` (the receiver's `onReceive` became a `when` plus a small `refreshChangedPackage`; the use case's `toInstalledApp` moved its catch into `packageInfoOrNull`), the two baseline resurfaces above, and one `TooGenericExceptionCaught` - the only `@Suppress`, on the broad catch that stops a failed refresh from crashing the app during someone else's install; the repo already carries 41 of these.
- 2026-08-05 - Step 02.6 MANUAL-REQUIRED, not done. The half the machine can prove is proved and written to `temp/S1401/02_6-measurement.md`: on a cleared install the cold cache fill took ~502 ms as a background startup task (logcat, `seed-installed-app-cache` at 19:46:51.957 against the previous task at 19:46:51.455), with no crash on the path. The other half - "no visible wait when the list opens" - is a judgement by eye, and two device passes did not reach a list of installed apps: the launcher "All apps" row needs the home role (deliberately not granted on this device) and the app-launch picker sits several taps deep. Left for a device pass by a human; the phase stays In Progress because of it.
- 2026-08-05 - Audit finding fixed inside the phase (P1, correctness): `refreshAll` fed an empty enumeration straight into `replaceAll` + `retainOnly`. SQLite reads `packageName NOT IN ()` as always true, so a single failed or empty package-manager read would have deleted every cached row and then every icon file, turning a transient read failure into a full cache wipe. Guarded: an empty result keeps the previous cache and logs at info.
- 2026-08-05 - `super.onReceive` was removed from the receiver: `BroadcastReceiver.onReceive` is abstract, so the Kotlin frontend refuses the call ("Abstract member cannot be accessed directly"). The repo's two shipping Hilt receivers (`ScheduledOperationsBootReceiver`, `MediaButtonRestartReceiver`) omit it as well - the Hilt plugin injects into the override itself.

---

## Handoff Notes to Next Phase

The cache is filled, self-maintaining and already serving both existing readers. Nothing yet sorts or filters it, and the launch-statistics table from Phase 01 is still empty - Phase 03 starts writing it.

---

## Rollback Plan

Revert the phase commit. Cached icon files under the app cache directory are orphaned but harmless and are reclaimed by the system; clear app data if a clean state is wanted.
