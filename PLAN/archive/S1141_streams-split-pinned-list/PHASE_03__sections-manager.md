# Phase 03 - Sections manager, second adapters, data split

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-07-23
**Completed:** 2026-07-23

**Step Log:**

- 2026-07-23 - Step 03.1 PASS: temp/S1141/StreamsActivity.20260723-004618.kt.bak created.
- 2026-07-23 - Step 03.2 PASS: StreamsSectionsManager created (applyMode/submitList/onConfigurationChanged/stop, partition split, no Log.d).
- 2026-07-23 - Step 03.3 PASS: pinnedAdapter + pinnedGridAdapter + pinnedSnapshotManager (streamCaptureHostPinned) + pinnedGridModeManager wired; `.\a.ps1 fk` exit 0.
- 2026-07-23 - Step 03.4 PASS: sectionsManager wired into observeData/onConfigurationChanged/onStop; `.\a.ps1 dq` Build Successful exit 0. post-change -ScopeToFile PASS (detekt scoped clean) on both files.
- 2026-07-23 - Phase-boundary audit (L1-L3): no P0/P1. Snapshot engines cancelled symmetrically via sectionsManager.stop(); applicationContext capture host; cache keyed by url (pinned XOR unpinned, no collision).

---

## Objective

Introduce `StreamsSectionsManager`, which owns the two section pipelines (each a `StreamGridModeManager` over its own adapters + snapshot host), splits the ViewModel's ordered `sources` into pinned / unpinned, submits each stream to its section, applies the shared display mode to both, and shows the pinned section only when ≥1 pinned channel exists. Delivers strategic pillars P1/P2, goals G1-G4, ADR-1/ADR-2/ADR-3.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (new view ids exist in all three layout variants).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsSectionsManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1050 |

> `StreamsActivity.kt` is >500 LOC - take a timestamped backup into `temp/S1141/` before editing (Rule 5).

---

## Steps

### Step 03.1 - Back up StreamsActivity

**Files:** `temp/S1141/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` to `temp/S1141/StreamsActivity.<yyyyMMdd-HHmmss>.kt.bak` before any edit (Rule 5, file >500 LOC).

**Verification:**

- `Glob` - a `temp/S1141/StreamsActivity.*.kt.bak` file exists.

**Status:** `[ ]` not done

---

### Step 03.2 - Create StreamsSectionsManager

**Files:** `ui/streams/helpers/StreamsSectionsManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `StreamsSectionsManager` (Rule 6 `NounVerbManager` shape - a coordination manager). Constructor takes `pinnedSection: View` (for the auto-hide) and the two `StreamGridModeManager` instances (`pinnedGridMode`, `mainGridMode`). The section-header / chevron views + collapse logic are added in Phase 04 (unused constructor params here would trip detekt, Rule 19). Expose:
> - `applyMode(mode: DisplayMode, sources: List<StreamSourceEntity>)` - split `sources` via `partition { it.pinned }`, set `pinnedSection` visibility from `pinned.isNotEmpty()`, call `pinnedGridMode.applyMode(mode, pinned)` and `mainGridMode.applyMode(mode, unpinned)`.
> - `submitList(sources: List<StreamSourceEntity>)` - same split; toggle pinned-section visibility; `pinnedGridMode.submitCurrentList(pinned)`, `mainGridMode.submitCurrentList(unpinned)`.
> - `onConfigurationChanged()` - forward to both grid managers.
> - `stop()` - forward to both grid managers.
>
> The split (pinned / unpinned) is the single source of truth for both sections - no data duplication beyond the two filtered sublists (perf constraint §3.2). Collapse behavior is added in Phase 04; this step leaves both sections expanded. Timber only; no trivial comments (KDoc the WHY: why two independent grid managers rather than one RecyclerView).

**Verification:**

- `Glob` - `StreamsSectionsManager.kt` exists.
- `Grep` - `class StreamsSectionsManager` matches once.
- `Grep` - `fun applyMode`, `fun submitList`, `fun onConfigurationChanged`, `fun stop` each present.
- `Grep` - `partition` present (the pinned/unpinned split).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 03.3 - Build the pinned adapter set + second grid manager in StreamsActivity

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a second `StreamSourceAdapter` (`pinnedAdapter`) and `StreamGridAdapter` (`pinnedGridAdapter`) with the same callback wiring as the existing `adapter` / `gridAdapter` (play/pin/remove/move/shortcut/edit/share/favorite + favicon plumbing). Add a second `StreamFrameSnapshotManager` (`pinnedSnapshotManager`) bound to `binding.streamCaptureHostPinned`, and a second `StreamGridModeManager` (`pinnedGridModeManager`) over `binding.rvStreamsPinned` / `binding.swipeStreamsPinned` / `pinnedAdapter` / `pinnedGridAdapter` / `pinnedSnapshotManager`. Set `binding.rvStreamsPinned.layoutManager = LinearLayoutManager(this)` and `adapter = pinnedAdapter` in `setupViews`. Rename the existing `gridModeManager` wiring to be the main section's manager (keep the field, it already targets `rvStreams`). Keep construction only - no split logic in the Activity (that lives in the manager, Rule 3).

**Verification:**

- `Grep` - `pinnedAdapter`, `pinnedGridAdapter`, `pinnedSnapshotManager`, `pinnedGridModeManager` each present in `StreamsActivity.kt`.
- `Grep` - `streamCaptureHostPinned` referenced (second host bound).
- `.\a.ps1 fk` (Kotlin compile, standard) exits 0.

**Status:** `[ ]` not done

---

### Step 03.4 - Wire StreamsSectionsManager into observeData

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Instantiate `sectionsManager = StreamsSectionsManager(...)` in `setupViews` after both grid managers are built. In `observeData`'s `state` collector, replace the direct `gridModeManager.applyMode(...)` / `adapter.submitList(...)` / `gridModeManager.submitCurrentList(...)` calls with the equivalent `sectionsManager.applyMode(state.displayMode, state.sources)` on a mode change and `sectionsManager.submitList(state.sources)` otherwise; keep the scroll-button `updateVisibility()` refresh on the main section only. Forward `onConfigurationChanged()` and `onStop`/`onDestroy` teardown (`sectionsManager.stop()`, release `pinnedSnapshotManager`) alongside the existing calls. The `emptyStateView` visibility still keys off `state.isEmpty` (whole-catalog empty).

**Verification:**

- `Grep` - `sectionsManager` present; `StreamsSectionsManager(` construction present.
- `Grep` - `sectionsManager.applyMode` and `sectionsManager.submitList` present in `observeData`.
- `Grep` - `sectionsManager.stop()` present in `onStop`.
- `.\a.ps1 dq` (standard debug) - `BUILD SUCCESSFUL`, exit 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `/build` (`.\a.ps1 dq`) `BUILD SUCCESSFUL`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `StreamsSectionsManager.kt` and `StreamsActivity.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class) - deferred to Phase 06 batch is acceptable.
- [ ] Phase-boundary audit run - no unresolved P0/P1 (listener symmetry: the second snapshot manager is released on teardown; two grid managers each own one RecyclerView; single frame cache keyed by url so pinned/unpinned never collide).

---

## Handoff Notes to Next Phase

Both sections render and scroll independently; pinned section auto-hides when empty. Phase 04 adds collapse; Phase 05 wires single-playback indicator + probe across both sections. `sectionsManager` is the delegation point for both.

---

## Rollback Plan

Restore `StreamsActivity.kt` from the `temp/S1141/` backup and delete `StreamsSectionsManager.kt`; revert the phase commit. No data migration or persisted surface changed.
