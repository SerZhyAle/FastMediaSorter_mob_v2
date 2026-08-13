# Phase 05 - Single playback across sections

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-23
**Completed:** 2026-07-23

**Step Log:**

- 2026-07-23 - Step 05.1 PASS: onPlayingChanged pushes to adapter + pinnedAdapter (grid adapters carry no indicator).
- 2026-07-23 - Step 05.2 PASS: visibleSources() splits pinned/unpinned and aggregates both RecyclerViews via visibleInSection(); GRID refresh covers both sections. `.\a.ps1 dq` Build Successful exit 0. post-change -ScopeToFile PASS.
- 2026-07-23 - Phase-boundary audit: no P0/P1. Single-playback owner unchanged (ADR-4); fan-out O(visible); probe bounded to visible rows.

---

## Objective

Guarantee exactly one channel plays regardless of section, and that the now-playing indicator + reachability probe act across both sections. Delivers strategic pillar P4, goal G6, ADR-4, criterion §11.6.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (both sections + `pinnedAdapter` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1080 |

---

## Steps

### Step 05.1 - Fan the now-playing indicator to both list sections

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The single `StreamInlineAudioManager` already enforces one active playback (starting a stream stops the previous), so single playback is preserved by construction (ADR-4). Change `onPlayingChanged` to update BOTH list adapters: `{ id -> adapter.setPlayingId(id); pinnedAdapter.setPlayingId(id) }`. A channel is pinned XOR unpinned, so only the section holding it repaints its row; the other no-ops (its list has no matching id). The grid adapters carry no playing indicator (`StreamGridAdapter` has no `setPlayingId`), so no grid fan-out is needed.

**Verification:**

- `Grep` - `onPlayingChanged` wiring calls both `adapter.setPlayingId` and `pinnedAdapter.setPlayingId`.
- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

### Step 05.2 - Extend the reachability probe scope to both sections

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> `visibleSources()` currently reads only `rvStreams` (main). Extend it to aggregate the visible rows of BOTH `rvStreamsPinned` and `rvStreams`, splitting `latestState.sources` into pinned/unpinned the same way the manager does so each RecyclerView's first/last-visible positions index into the correct sublist. Deduplicate the union (a channel appears in exactly one section, so a simple concat of the two visible sublists is correct). `startHealthProbe`/`gridModeManager.refreshVisibleFrames` should also refresh the pinned section's frames in GRID mode (call the pinned grid manager's `refreshVisibleFrames`). Keep it lean - this is a refresh-only path, not hot.

**Verification:**

- `Grep` - `rvStreamsPinned` referenced inside `visibleSources()` (or a helper it calls).
- `.\a.ps1 dq` - `BUILD SUCCESSFUL`, exit 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - `/build` (`.\a.ps1 dq`).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for `StreamsActivity.kt`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 (single player owner unchanged; indicator fan-out is O(visible) row repaints; probe scope bounded to visible rows of both sections).

---

## Handoff Notes to Next Phase

Feature-complete. Phase 06 inserts the S1141 debug tags, records the capability, and runs catalog/docs cleanup before `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit - the indicator reverts to the main list only; single playback (owned by `StreamInlineAudioManager`) is unaffected.
