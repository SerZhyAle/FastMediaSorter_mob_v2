# Phase 05 - Re-seed owned by the launcher

**Strategic spec:** [`../S1400_reset-system-launcher-settings.md`](../S1400_reset-system-launcher-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Move the re-seed out of the reset operation and back into the launcher, which is the only place that knows the real grid geometry, so a reset performed from inside the launcher repaints a full desktop instead of an empty one.

---

## Why this phase exists

Added 2026-08-06 after the on-device run of Phases 01-04. The original ADR-2 had the reset re-seed from the grid widths persisted in the desktop state. Two device facts killed it:

- The launcher persists a column width only for the orientation it has actually rendered. A real emulator desktop with 18 tiles per orientation reads `columnsPortrait=4, columnsLandscape=0` (`temp/S1400/db/`), so the reset's `both widths > 0` guard is false in the ordinary case and the seed never runs.
- The fallback "the next launcher entry will seed it" does not happen either: `LauncherHomeActivity.seedDesktopIfNeeded()` is called only from `onCreate`, and `LauncherHomeViewModel.seedTriggered` is a ViewModel field, so it survives Activity recreation. The desktop would stay empty until the process dies.

Strategic §11 criterion 8 ("a reset performed from the launcher repaints the desktop without a restart") therefore could not hold. The strategic ADR-2 and §6 item 1 were rewritten before this phase was written.

---

## Prerequisites

- [ ] Phases 02 and 03 are ✅ Done.
- [ ] `temp/CODE.LOCK` acquired for the multi-file source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` | Modified | ≤ 95 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 470 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 730 |

> `LauncherHomeActivity.kt` and `LauncherHomeViewModel.kt` are both over 500 LOC, so step 05.2 takes a timestamped backup of each into `temp/S1400/` first (CLAUDE.md Rule 5).
>
> `src/launcherEnabled` is a source set mounted by the two flavors that ship the launcher, not a `BuildConfig` guard in `src/main` - this is the placement Rule 14 asks for, not an exception to it.

---

## Steps

### Step 05.1 - Drop the seeding responsibility from the reset use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove the `SeedLauncherDesktopUseCase` constructor dependency, the captured `portraitColumns`/`landscapeColumns` values and the whole `reseed` helper. `invoke()` keeps the wipe, the settings restore and the wallpaper delete, and nothing else. Update the KDoc: the class no longer re-seeds, it clears the seeded flags and the launcher seeds again from its own geometry. Keep the `S1400` probe tag, dropping the grid arguments it no longer has.

**Why:**

Strategic ADR-2, rewritten 2026-08-06, moves the seed to the launcher because the persisted grid widths are populated only for the orientation that has been rendered, so the reset cannot lay tiles out correctly from them.

**Verification:**

- `Grep` - `SeedLauncherDesktopUseCase` returns zero hits in `ResetLauncherToDefaultsUseCase.kt`.
- `Grep` - `reseed` returns zero hits in that file.
- `Grep` - `columnsPortrait` and `columnsLandscape` return zero hits in that file.
- `Grep` - `Timber.d("S1400:` matches once in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS. File: ResetLauncherToDefaultsUseCase.kt 104 -> 85 LOC. `desktop.state()` is gone too - with the seed removed there was nothing left to read it for.

---

### Step 05.2 - Make the launcher's seed re-armable

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Back the file up to `temp/S1400/LauncherHomeViewModel.<yyyyMMdd-HHmmss>.kt.bak` first. Then delete the `seedTriggered` field and its early return from `seedDesktopIfNeeded`, leaving the coroutine body unchanged. Replace the "Runs once per process" comment with one stating that re-entry is guarded by the persisted seeded flags inside `SeedLauncherDesktopUseCase`, which is what lets a reset seed again while leaving a desktop the user emptied by hand alone.

**Why:**

Strategic ADR-2 requires the seed to run again after a reset within the same process; the persisted flags already make the operation idempotent, so the extra in-memory one-shot only blocks the case this ticket exists to serve.

**Verification:**

- `Glob` - a `temp/S1400/LauncherHomeViewModel.*.kt.bak` file exists.
- `Grep` - `seedTriggered` returns zero hits in `LauncherHomeViewModel.kt`.
- `Grep` - `fun seedDesktopIfNeeded(` still matches once in that file.
- `Grep` - `seedLauncherDesktop(portraitColumns, landscapeColumns)` still matches once in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS. Backup at `temp/S1400/LauncherHomeViewModel.20260806-011500.kt.bak`. The coroutine body is untouched; only the one-shot field and its early return are gone, and the comment above the method now explains which guard replaced it.

---

### Step 05.3 - Re-seed when the desktop is observed empty

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Back the file up to `temp/S1400/LauncherHomeActivity.<yyyyMMdd-HHmmss>.kt.bak` first. Find where the Activity collects the desktop cells and renders the grid, and call `seedDesktopIfNeeded()` whenever the collected list is empty, in addition to the existing `onCreate` call. Add a comment naming the two states this distinguishes: after a reset the persisted seeded flags are down so the seed runs, while a desktop the user emptied by hand still has them up so it stays empty.

**Why:**

Strategic §11 criterion 8 requires a reset performed from the launcher to repaint the desktop without a restart, and the cells Flow is the only signal the launcher gets when a reset wipes the desktop underneath it.

**Verification:**

- `Glob` - a `temp/S1400/LauncherHomeActivity.*.kt.bak` file exists.
- `Grep` - `seedDesktopIfNeeded()` matches at least twice in `LauncherHomeActivity.kt`.
- `Grep` - `isEmpty()` appears in the same collector block as the new call.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS. Backup at `temp/S1400/LauncherHomeActivity.20260806-011500.kt.bak`. File 671 -> 678 LOC. The cells collector now takes its emission as a parameter instead of ignoring it, calls `renderDesktop()` as before, and re-arms the seed when the list is empty. `seedDesktopIfNeeded()` reads 3 times: the `onCreate` call, this new one, and the private method's own declaration.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for the phase via `post-change.ps1`.
- [ ] On-device: a reset performed from the launcher leaves the desktop showing the starter set, and the `launcher_cells` row count returns to its pre-reset value.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

Final phase. The catalog and docs work in Phase 04 is unaffected - this phase adds no class and no string.

---

## Rollback Plan

Revert phase commit(s). The reset still works without this phase; the desktop simply stays empty until the launcher process restarts, which is the defect this phase closes.
