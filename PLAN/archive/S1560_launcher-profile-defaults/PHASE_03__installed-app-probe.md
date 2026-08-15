# Phase 03 - Conditional third-party app seeding

**Strategic spec:** [`../S1560_launcher-profile-defaults.md`](../S1560_launcher-profile-defaults.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Give the starter-set table a way to place a third-party app cell only when that app is installed, and use it for
the two apps the owner assigned to every profile - YouTube and YouTube Music.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.2, resolved by owner (§12) and by `research/02__third-party-package-visibility.md`.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "//spec-dev S1560 phase 03"` before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/ResolveInstalledPackagesUseCase.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | Modified | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 300 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt` | Modified | ≤ 110 |

> **Flavor placement.** All four files are in `src/main` and carry no flavor guard: the table and the seed use case
> are shared code that only the launcher flavors ever call, and the probe is a plain `PackageManager` query with no
> launcher dependency.

---

## Steps

### Step 03.1 - Add the installed-package probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/ResolveInstalledPackagesUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ResolveInstalledPackagesUseCase` with an `@Inject constructor(@ApplicationContext context: Context)` and
> a single `suspend operator fun invoke(candidates: Set<String>): Set<String>` that returns the subset actually
> installed. Resolve each candidate with `packageManager.getLaunchIntentForPackage(pkg) != null`, wrapped in
> `withContext(Dispatchers.IO)`, exactly as `WeatherGadget.openWeatherApp` already probes stock weather packages.
> Do not read `InstalledAppsRepository`: its Room cache is filled by `DeferredStartupWorker` and is empty on a
> genuine first launch, which is precisely when the desktop is seeded. Do not add any `<queries>` entry - the
> manifest already declares the `MAIN`/`LAUNCHER` intent, which grants visibility of every launchable app on
> API 30+. Add a KDoc sentence recording why the cache is not the source.

**Why:**

Strategic §3.2 forbids turning the seed into a blocking system query on the main thread, and
`research/02__third-party-package-visibility.md` §2 established that the cached list loses the race against
first-run seeding, so the probe must query the package manager directly and off the main thread.

**Verification:**

- `Glob` - `ResolveInstalledPackagesUseCase.kt` exists.
- `Grep` - `class ResolveInstalledPackagesUseCase` matches exactly once.
- `Grep` - `Dispatchers.IO` matches at least once in that file.
- `Grep` - `InstalledAppsRepository` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 03.2 - Thread installed packages into the starter-set table

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Declare the candidate packages as companion constants: `PACKAGE_YOUTUBE = "com.google.android.youtube"`,
> `PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"`, `PACKAGE_MAPS = "com.google.android.apps.maps"`,
> and an ordered `private val FM_RADIO_CANDIDATES: List<String>` holding `com.android.fmradio`, `com.caf.fmradio`,
> `com.miui.fmradio`, `com.sec.android.app.fm`, `com.motorola.fmplayer`, `com.lge.fmradio` - ordered, because the
> first installed one wins. Expose their union as a public `val candidatePackages: Set<String>` so the seed use
> case can hand it to the probe. `FM_RADIO_CANDIDATES` and `PACKAGE_MAPS` are declared here but seeded only in
> Phase 04, which needs them per profile. Add an `installedPackages: Set<String>` parameter to `itemsFor` after
> `routeAvailableInBuild`. Add a private helper `appIfInstalled(pkg, installed)` returning a `StarterItem` app
> shortcut or null, and a private `firstInstalled(candidates, installed)` returning the first installed package of
> an ordered list or null. Use `appIfInstalled` in the common section to seed YouTube and YouTube Music for every
> profile, in that order, after the common feature block. Keep the table pure - no Android import enters this file.

**Why:**

Strategic §5.1 pillar 3 requires a third-party cell to be placed only when its package is installed, and ADR-1
keeps the set a pure code table, so the installed set has to arrive as a parameter rather than be queried inside it.

**Verification:**

- `Grep` - `installedPackages: Set<String>` matches once in the `itemsFor` signature.
- `Grep` - `FM_RADIO_CANDIDATES` and `candidatePackages` each match at least once in the file.
- `Grep` - `com.google.android.youtube` and `com.google.android.apps.youtube.music` each match once in the file.
- `Grep` - `private fun appIfInstalled` matches once. `firstInstalled` moved to Phase 04: declared here it would be an unused private member and detekt refuses one.
- `Grep` - `import android\.` returns zero hits in `LauncherStarterSets.kt`.

**Status:** `[x]` done

---

### Step 03.3 - Resolve installed packages in the seed use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `ResolveInstalledPackagesUseCase`, call it with `LauncherStarterSets.candidatePackages` after the route
> availability map is built, and pass the result into `itemsFor`. Keep the whole call inside the existing
> `runCatching`, and keep the existing early-exit that skips all work when both orientations are already seeded -
> the probe must not run for a desktop that will not be seeded.

**Why:**

Strategic §3.2 requires the seed to stay a one-off first-run action that does not become a blocking system query,
so the probe belongs behind the same early-exit that already guards every other read in this use case.

**Verification:**

- `Grep` - `ResolveInstalledPackagesUseCase` matches at least twice in the file (constructor parameter and call).
- `Grep` - `candidatePackages` matches once in the file.
- Read the file and confirm the probe call sits after the `alreadySeeded` early-exit, not before it.

**Status:** `[x]` done

---

### Step 03.4 - Cover conditional seeding in the table's unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Update every existing `itemsFor` call site for the new parameter, passing an empty set so current assertions keep
> their meaning. Add two tests: one asserting that with an empty installed set no `app:` shortcut for any candidate
> package appears in any profile's set, and one asserting that with `com.google.android.youtube` installed exactly
> one such shortcut appears and YouTube Music's does not. Run the suite with `.\a.ps1 fu`.

**Why:**

Strategic §11 criterion 2 makes "a third-party cell appears only when the app is installed" a completion
criterion, and the table is the only place that decision is expressible as a static test.

**Verification:**

- `Grep` - at least two new `@Test` functions naming `installed` in `LauncherStarterSetsTest.kt`.
- `.\a.ps1 fu` - `LauncherStarterSetsTest` passes; read the per-class result XML rather than the summary line.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - one new class.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings, with the "startup-path change" trigger applied.
- [ ] `CODE.LOCK` released via `scripts/utils/exit-code-lock.ps1`.

---

## Step Log

- 2026-08-11 - 03.1 done. `ResolveInstalledPackagesUseCase` probes `getLaunchIntentForPackage` on `Dispatchers.IO`;
  it does not touch `InstalledAppsRepository`, whose cache is filled by `DeferredStartupWorker` and is therefore
  empty at the one moment the desktop is seeded.
- 2026-08-11 - 03.2 done. `firstInstalled` deliberately NOT added here - unused until Phase 04 seeds maps and FM,
  and detekt refuses an unused private member. `FM_RADIO_CANDIDATES` and `PACKAGE_MAPS` are already referenced by
  `candidatePackages`, so they carry no such problem.
- 2026-08-11 - 03.3 done. The probe call sits after the `seededPortrait && seededLandscape` early-exit, so a
  desktop that will not be seeded never pays for it.
- 2026-08-11 - 03.4 FAIL then done. First `.\a.ps1 fu` failed to compile: the step's `Files Touched` named only
  `LauncherStarterSetsTest.kt`, but `LauncherStarterSetsParityTest.kt` in `src/testLauncherEnabled` calls `itemsFor`
  at four more sites. Plan corrected - the parity test is now listed - and the four sites took the argument.
- 2026-08-11 - 03.4 done. All 13 `itemsFor` call sites in the main test took the fourth argument. `sectionHead` gained
  `act:all_apps` and `act:black_screen`, and the action-count assertion now reads `LauncherActionCatalog.all.size`
  instead of a literal, so a future action does not silently break it. Both new tests added.

---

- 2026-08-11 - Phase close. `.\a.ps1 fk` BUILD SUCCESSFUL; `.\a.ps1 fu` BUILD SUCCESSFUL with
  `assert-test-suite-complete: PASS - 476 report(s) for 476 *Test.kt file(s)`, so the run was not truncated.
  Per-class XML read rather than the summary line: `LauncherStarterSetsTest` 16 tests / 0 failures / 0 errors,
  `LauncherStarterSetsParityTest` 2 tests / 0 failures / 0 errors. `post-change.ps1 -ScopeToFile -ChangeType Kotlin`
  printed `post-change: PASS`.
- 2026-08-11 - Phase-boundary audit, Layer 1 plus the startup-path trigger. No P0/P1. The probe is nine
  `getLaunchIntentForPackage` calls on `Dispatchers.IO`, behind the already-seeded early-exit, so a device whose
  desktop is already seeded pays nothing; the table stayed pure (no `android.` import) and the seed use case keeps
  its whole-body `runCatching`, so a package-manager failure still degrades to an empty desktop rather than a crash
  loop on the device's home surface.

---

## Handoff Notes to Next Phase

`itemsFor` now takes `installedPackages`, and `appIfInstalled` / `firstInstalled` are available to the per-profile
branches Phase 04 writes. The FM candidate list is already in `THIRD_PARTY_CANDIDATES` but is not yet seeded
anywhere - Phase 04 consumes it in the car-head-unit branch.

---

## Rollback Plan

Revert the phase commit. Nothing persisted changes shape - the parameter is compile-time only and no already-seeded
desktop is re-read.
