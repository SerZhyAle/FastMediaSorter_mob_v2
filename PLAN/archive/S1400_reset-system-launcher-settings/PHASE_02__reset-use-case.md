# Phase 02 - Reset use case

**Strategic spec:** [`../S1400_reset-system-launcher-settings.md`](../S1400_reset-system-launcher-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Introduce `ResetLauncherToDefaultsUseCase` - the single place that knows the full list of launcher-owned state, wipes it, restores the launcher settings fields, drops the imported wallpaper, and re-seeds the starter set. No UI yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired for the multi-file source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` | New | ≤ 160 |

> Single new file, no file over 500 LOC, so no backup sub-step is required.
>
> No flavor-specific placement applies: the use case and every dependency it takes live in `src/main`.

---

## Steps

### Step 02.1 - Create the use case and wipe every launcher store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ResetLauncherToDefaultsUseCase` with an `@Inject constructor` taking `LauncherDesktopRepository`, `LauncherPinsRepository`, `LauncherJournalRepository`, `InstalledAppsRepository`, `SettingsRepository`, `StoreLauncherWallpaperUseCase` and `SeedLauncherDesktopUseCase`. Give it `suspend operator fun invoke(): Boolean` running on `Dispatchers.IO`. In this step implement only the wipe: read the desktop state first and keep its two column widths in local values, then call `clearAll`, `clearPins`, `clearJournal` and `clearLaunchStats`. Return `true` at the end of the happy path. Wrap the body in `runCatching`, log a failure with `Timber.e`, and return `false` so the caller can tell the user the reset did not happen. Write a KDoc listing every state family this use case owns and stating that a ticket introducing a new launcher store must extend that list.

**Why:**

Strategic §5.1 requires a single place that knows the full inventory of launcher-owned state, because the failure this ticket is written against is exactly a state that gets forgotten in one of several scattered call sites, and §5.3 makes that list the documented extension point.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` exists.
- `Grep` - `class ResetLauncherToDefaultsUseCase` matches exactly once in that file.
- `Grep` - `suspend operator fun invoke(): Boolean` matches once in that file.
- `Grep` - each of `clearAll()`, `clearPins()`, `clearJournal()`, `clearLaunchStats()` appears at least once in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 7\7 PASS. File: domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt (New, 104 LOC). The whole class was authored in one `Write`, so steps 02.1-02.3 landed in the same file creation; each step's predicates were still evaluated separately against the written file. KDoc carries the six-entry state inventory and names what is deliberately excluded.

---

### Step 02.2 - Restore the launcher settings fields and drop the wallpaper copy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend `invoke()` to restore the launcher settings. Use `SettingsRepository.updateSettings { current -> .. }` and `AppSettings()` as the source of defaults, copying back exactly these fields and no others: `launcherDensityFactor`, `launcherTaskbarShowRecents`, `launcherTaskbarShowPinned`, `launcherTaskbarShowTray`, `launcherReplaceSystemStatusArea`, `launcherRotationHintShown`, `launcherDesktopLocked`, `launcherWallpaperMode`, `launcherWallpaperImagePath`. Then call `StoreLauncherWallpaperUseCase.clear()` to delete the private copy of the imported image.

**Why:**

Strategic §2 goal 5 forbids the reset from touching anything outside the launcher, so the settings object is edited field by field rather than replaced wholesale, and §2 goal 3 puts the wallpaper back on its default, which leaves the copied image file on disk unless it is deleted explicitly.

**Verification:**

- `Grep` - `updateSettings` matches at least once in `ResetLauncherToDefaultsUseCase.kt`.
- `Grep` - each of the nine field names listed above appears at least once in that file.
- `Grep` - `storeLauncherWallpaperUseCase.clear()` matches once in that file.
- `Grep` - `resetToDefaults()` returns zero hits in that file, proving the wholesale settings reset was not used.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS. All nine launcher fields copied from `AppSettings()` through `updateSettings { .. }`; `resetToDefaults()` absent, so nothing outside the launcher is written.

---

### Step 02.3 - Re-seed the starter set from the stored column widths

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Finish `invoke()` by re-seeding: after the wipe and the settings restore, call `SeedLauncherDesktopUseCase(portraitColumns, landscapeColumns)` with the two widths captured in step 02.1, but only when both are greater than zero. When either width is zero or negative, skip the seed entirely and comment that the launcher has not rendered yet, so the next entry into it seeds both orientations on the install path. Do not derive a width from the current screen.

**Why:**

Strategic ADR-2 rules that a reset performed from inside the launcher must repaint a full desktop rather than an empty one, and that a guessed width would lay the tiles out for the wrong geometry, so the only two acceptable outcomes are a seed at the stored width or no seed at all.

**Verification:**

- `Grep` - `seedLauncherDesktopUseCase(` matches once in `ResetLauncherToDefaultsUseCase.kt`.
- `Grep` - a guard of the form `> 0` guarding that call is present in the same file.
- `Grep` - `resources.displayMetrics` and `windowManager` return zero hits in that file, proving no screen-derived width is used.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS. Widths are captured before `clearAll()` and the seed runs only behind `portraitColumns > 0 && landscapeColumns > 0`; the else branch logs the deferral at `Timber.i`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL in 1m 54s, exit 0. Compile-only rung is the right one here: nothing injects the class yet, so the Hilt graph node it creates is first exercised by Phase 03's full build.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1` (verdict `post-change: PASS`).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: the use case reads only through repositories, no DAO reaches the domain layer, and the class name follows `VerbNounUseCase`. Layer 2: the whole body is one `withContext(Dispatchers.IO)` and the only `runCatching` wraps a failure into a `false` the caller renders, so it is a recovery, not a swallow. Layer 4: no Room type is referenced here at all.

---

## Handoff Notes to Next Phase

`ResetLauncherToDefaultsUseCase()` returns `true` on success and `false` on failure, and it is safe to call from any dispatcher. Phase 03 surfaces exactly that boolean as the success or failure message; it must not re-implement any part of the reset.

---

## Rollback Plan

Revert phase commit(s) - one new unreferenced class, no data migration and no user-facing surface changed.
