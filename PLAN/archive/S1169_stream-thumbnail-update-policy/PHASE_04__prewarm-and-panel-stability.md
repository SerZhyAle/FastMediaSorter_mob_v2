# Phase 04 - Prewarm idempotence + panel-chip stability

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Step 04.1: PASS (prewarmAttempted added; stopPrewarm no longer at prewarm-body top; only new urls swept). Dropped dead Timber import. compileStandardDebugKotlin EXIT=0.
- 2026-07-24 - Step 04.2: PASS (notifyDataSetChanged=0, payload override present, image pre-clear removed from bind top). detekt scoped PASS both files.

---

## Objective

Stop the disk-thumbnail prewarm sweep from cancel-restarting on every catalog resubmit, and stop the main-window pinned-channel chips from blanking-then-reloading on every rotation/atlas-load - the two remaining flicker sources.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] `StreamGridModeManager.prewarmPersistedFrames` and `StreamPanelChannelAdapter` unchanged from main.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamGridModeManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/StreamPanelChannelAdapter.kt` | Modified | ≤ 180 |

---

## Steps

### Step 04.1 - Idempotent prewarm (no cancel-restart on resubmit)

**Files:** `ui/streams/helpers/StreamGridModeManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `MutableSet<String> prewarmAttempted` to the manager. In `prewarmPersistedFrames`, do NOT call `stopPrewarm()` at the top when a sweep is already running for the same GRID session; instead only launch a prewarm pass for urls not yet in `prewarmAttempted`, and add each url to the set as it is attempted (found-or-missing on disk). Clear `prewarmAttempted` only on `applyMode(GRID)` entry and in `stop()`/leaving GRID - not on `submitCurrentList`. Result: a catalog resubmit triggered by an unrelated DB write no longer restarts the sweep from index 0; only genuinely new urls are prewarmed. Keep the existing cancellation-on-leave (`stopPrewarm` in `applyMode(LIST)`/`stop`). Preserve the `isActive`/`currentMode == GRID` re-checks.

**Verification:**

- `Grep` - `prewarmAttempted` present in `StreamGridModeManager.kt`.
- `Grep -c "stopPrewarm()"` - `stopPrewarm()` no longer called from the top of `prewarmPersistedFrames` (called only from `applyMode(LIST)`, `stop`, and GRID re-entry).
- `.\a.ps1 fk` compiles.

### Step 04.2 - Panel chips: no blank-swap, DiffUtil-driven label refresh

**Files:** `ui/main/helpers/StreamPanelChannelAdapter.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Two changes: (1) In `bind()`, remove the unconditional `binding.ivChannelFavicon.setImageDrawable(null)` pre-clear - only clear when there is no favicon index to load (the `else`/no-favicon branches already hide the view); when a favicon WILL load, keep the current image until the async tile lands (guard by `boundUrl`, already present), so a rebind of an unchanged chip never flashes blank. (2) Replace `setShowLabels`'s and `refreshFavicons`'s `notifyDataSetChanged()` with a targeted refresh: add `PAYLOAD_LABELS` and drive `setShowLabels`/`refreshFavicons` through `notifyItemRangeChanged(0, itemCount, PAYLOAD_LABELS)` plus an `onBindViewHolder(..., payloads)` that, for `PAYLOAD_LABELS`, only re-applies label visibility + favicon load without resetting click listeners. Keep the full `bind` for real item changes.

**Verification:**

- `Grep -n "setImageDrawable(null)"` in `StreamPanelChannelAdapter.kt` - not present at the unconditional top of `bind` (only inside a no-favicon branch, if at all).
- `Grep -c "notifyDataSetChanged"` in `StreamPanelChannelAdapter.kt` - returns 0.
- `Grep` - `onBindViewHolder(holder: VH, position: Int, payloads:` present.
- `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `/build` passes.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `Grep -c "notifyDataSetChanged"` across both touched files returns 0.
- [ ] Dev log entry for both files.
- [ ] Phase-boundary audit: prewarm coroutine still cancellable on leave; no retained view refs; chip rebind attaches no un-removed listener.

---

## Handoff Notes to Next Phase

Flicker sources closed: prewarm no longer restarts (tiles keep restored frames), chips no longer blank on rotation. Phase 05 removes the last redundancy - visible-tile cache eviction.

---

## Rollback Plan

Revert the phase commit(s) - reverts to full `notifyDataSetChanged` refresh and cancel-restart prewarm; no data or schema change.
