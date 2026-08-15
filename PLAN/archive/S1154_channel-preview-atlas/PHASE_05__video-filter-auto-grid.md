# Phase 05 - Video-filter auto-grid

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (independent of the atlas payload)
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

When the user switches the media filter to VIDEO, auto-switch the display mode to GRID; when they leave the VIDEO filter, restore the display mode that was active before the auto-switch (Q-A). Reversible - it never permanently overwrites a manual LIST choice.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 560 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsViewModelAutoGridTest.kt` | New | ≤ 160 |

> No `res/layout/*.xml` edits - no landscape-parity obligation.
>
> **Flavor placement.** `src/main`; the ViewModel already exists on every streams flavor. No `BuildConfig.*` guard.

---

## Steps

### Step 05.1 - Auto-switch + restore in the ViewModel

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `onFilter(..)`, detect the `mediaKind` transition. On entering `MediaKindFilter.VIDEO` from a non-VIDEO filter: remember the current `displayMode` in a private field (`modeBeforeVideoFilter`), then set `displayMode = GRID` if not already GRID. On leaving VIDEO (VIDEO -> non-VIDEO): if a remembered mode exists, restore it and clear the field; a manual `onToggleDisplayMode()` while the VIDEO filter is active updates the remembered baseline so restore does not clobber a deliberate in-filter choice. Do not persist the auto mode as the user's default (`writeDisplayMode` stays driven only by `onToggleDisplayMode`). Keep the transition logic in one private helper so it is unit-testable.

**Verification:**

- `Grep` - `modeBeforeVideoFilter` (or the chosen field name) present in `StreamsViewModel.kt`.
- `Grep` - `MediaKindFilter.VIDEO` referenced in the `onFilter` transition logic.
- `Grep` - `writeDisplayMode` is NOT called from the auto-switch path (only from `onToggleDisplayMode`).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 05.2 - Transition unit test

**Files:** `src/test/.../StreamsViewModelAutoGridTest.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Unit-test the reversible transition: (a) filter -> VIDEO while in LIST switches to GRID; (b) filter VIDEO -> ALL restores LIST; (c) a manual toggle to LIST while VIDEO is active is preserved when leaving the filter (restore does not force GRID or an old value); (d) entering VIDEO while already GRID leaves GRID and, on leaving, restores GRID. Drive `onFilter`/`onToggleDisplayMode` and assert `state.displayMode`.

**Verification:**

- `Glob` - `StreamsViewModelAutoGridTest.kt` exists.
- Run `.\gradlew.bat testStandardDebugUnitTest --tests "*StreamsViewModelAutoGridTest"` - passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - no unresolved P0/P1. Focus: state-flow correctness (no lost restore across rapid filter changes; no persisted-default corruption).

---

## Handoff Notes to Next Phase

- Auto-grid is self-contained and unit-verified; no dependency on the atlas payload.

---

## Rollback Plan

Revert the phase commit(s). The transition helper + field are additive - removing them restores independent filter/display-mode behaviour.
