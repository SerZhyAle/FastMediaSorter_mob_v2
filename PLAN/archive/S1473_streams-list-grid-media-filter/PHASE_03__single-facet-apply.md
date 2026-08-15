# Phase 03 - Single-facet apply

**Strategic spec:** [`../S1473_streams-list-grid-media-filter.md`](../S1473_streams-list-grid-media-filter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Give the ViewModel an entry point that changes only the media-kind facet, keeping session persistence and the video display switch, so the inline trigger cannot reset the other facets.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] `research/03__facet-application-and-focus-order.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 700 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> `StreamsViewModel.kt` is 675 LOC - over the 500-LOC backup threshold. Step 03.1 carries the backup sub-step.

---

## Steps

### Step 03.1 - Extract the shared post-apply tail

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `StreamsViewModel.kt` to `temp/S1473/` with a timestamped name before editing (Rule 5). Extract the tail that `onFilter` runs after mutating the filter state - the `applyVideoFilterDisplayMode(previousKind, newKind)` call followed by `persistSession()` - into one private method taking the previous and new media kind. Call it from `onFilter` in place of the two inlined calls, leaving `onFilter`'s signature and observable behaviour unchanged.

**Why:**

Research artifact 03 records that the video display switch and the session write are the two behaviours any facet change must keep, and a single implementation of them is what stops the second entry point in step 03.2 from drifting away from the dialog path.

**Verification:**

- `Grep` - `applyVideoFilterDisplayMode(` matches exactly twice in `StreamsViewModel.kt` (declaration plus the single call site in the new tail method).
- `Grep` - `persistSession()` no longer appears inside `fun onFilter(`'s body.
- `Glob` - a timestamped `temp/S1473/StreamsViewModel*.kt` backup exists.

**Status:** `[x]` done

---

### Step 03.2 - Add `onMediaKindFilter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `fun onMediaKindFilter(mediaKind: MediaKindFilter)`. Read the previous media kind, update the filter state with `copy(mediaKind = mediaKind)` so every other facet is carried over untouched, then call the tail method from step 03.1. Do not give the parameter a default value - a defaulted single-facet setter is how the existing `onFilter` became unsafe to call partially.

**Why:**

Strategic ADR-2 requires the inline trigger to write through its own entry point because `onFilter` defaults every unpassed facet, which would silently clear rubric, language, country and the pinned-only flag - the failure §11 criterion 9 tests for.

**Verification:**

- `Grep` - `fun onMediaKindFilter(mediaKind: MediaKindFilter)` matches exactly once, with no `=` default in the parameter list.
- `Grep` - `copy(mediaKind = mediaKind)` present in `StreamsViewModel.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `StreamsViewModel.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - public API of `StreamsViewModel` changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").

---

## Handoff Notes to Next Phase

`onMediaKindFilter` is the only entry point Phase 04's trigger may call; the filter dialog keeps using `onFilter`, and both share one persistence and display-switch tail.

---

## Rollback Plan

Revert the phase commit - the change is additive and no persisted format changed.

---

## Step Log

- 2026-08-08 - Step 03.1 done. Shared tail extracted as afterFilterApplied(previousKind, newKind); onFilter now calls it instead of the two inlined calls. All predicates PASS.
- 2026-08-08 - Step 03.2 done. onMediaKindFilter(mediaKind) added with no default, copying the filter state and reusing the shared tail. A same-value early return was added so a repeat render cannot write the session twice. All predicates PASS.
- 2026-08-08 - Phase build: fc BUILD SUCCESSFUL, exit 0 (validated together with Phase 04).
- 2026-08-08 - Phase-boundary audit (Layers 1 and 2): the new entry point mutates the same StateFlow through update{} as the existing one and runs no new coroutine; persistence stays on the existing viewModelScope launch inside persistSession. No P0/P1 findings.
