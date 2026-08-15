# Phase 03 - Drag edge auto-scroll

**Strategic spec:** [`../S1209_launcher-scrollable-desktop.md`](../S1209_launcher-scrollable-desktop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Scroll the desktop while a cell is being dragged near the top or bottom edge, so a cell can be moved into a row that was off-screen when the drag began.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` free.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt` | Modified | ≤ 160 |
| `app_v2/src/launcherEnabled/res/values/dimens.xml` | Modified or New | ≤ 4 |

> The manager is 89 LOC today, so neither the backup threshold (500) nor the split threshold (1500) is in play.

---

## Steps

### Step 03.1 - Declare the edge zone as a dimension

**Files:** `app_v2/src/launcherEnabled/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one dimension for the height of the band at the top and bottom of the scroll container inside which a drag triggers auto-scroll. Express it in `dp`. If the launcher source set has no `dimens.xml`, create it with this single value rather than adding the value to `src/main`.

**Why:**

Strategic §5.3 requires the trigger band to be expressed as a size rather than a hardcoded number of cells, so the same band can later be reused by the resize drag without redefining it; keeping it in the launcher source set keeps a launcher-only value out of the shared resources of flavors that do not mount the launcher.

**Verification:**

- `Grep` - the new dimension name matches exactly once under `app_v2/src/launcherEnabled/res/values/`.
- `Grep` - the same name returns zero hits under `app_v2/src/main/res/values/` (it did not leak into the shared set).
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS. Files: `app_v2/src/launcherEnabled/res/values/dimens.xml` (+5 LOC, `launcher_drag_autoscroll_band` = 48dp). 1 hit under `launcherEnabled/res/values/`, 0 under `main/res/values/`. `.\a.ps1 fr` exit 0 (BUILD SUCCESSFUL in 6s). Dev log recorded.
- 2026-08-06 - 48dp chosen against two bounds the prompt leaves to the implementer: not below the platform's minimum touch target, or the band cannot be entered deliberately, and strictly under the smallest cell the desktop renders (~63dp at the highest density factor), or hovering over the last visible row would auto-scroll when the user meant to hold still. The launcher source set already had a `dimens.xml`, so none was created.

---

### Step 03.2 - Scroll the container while the drag sits in the band

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Handle `ACTION_DRAG_LOCATION` in the existing `OnDragListener` - today only `ACTION_DROP` is handled and every other action is accepted blindly. While the drag point is inside the top or bottom band, scroll the enclosing scroll container towards that edge; stop as soon as the point leaves the band, and stop on `ACTION_DRAG_ENDED` and on `ACTION_DROP` so no scrolling survives the gesture. Derive the step size from how deep the point sits inside the band rather than using a constant, so a shallow hover creeps and a deep one moves. Do not change the drop handling.

**Why:**

Strategic §1 records that a drag cannot reach rows past the bottom edge because nothing moves the desktop during the gesture, and §2.3 requires exactly that a drag towards an edge scrolls far enough to drop the cell in a row that was not visible when the drag started; strategic §7 warns that a constant speed makes the target row impossible to hit, which is why the depth of entry drives the step.

**Verification:**

- `Grep` - `ACTION_DRAG_LOCATION` matches at least once in `LauncherEditModeManager.kt`.
- `Grep` - `ACTION_DRAG_ENDED` matches at least once in the same file (the stop path exists, not only the start path).
- `Grep` - `GlobalScope` returns zero hits in the file.
- `Grep` - `Log.d(` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - Verification 5\5 PASS. Files: `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt` (89 -> 156 LOC, budget 160). `ACTION_DRAG_LOCATION` 1 hit, `ACTION_DRAG_ENDED` 1 hit, `GlobalScope` 0, `Log.d(` 0. `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 32s). Dev log recorded.
- 2026-08-06 - The scroll container is reached as `desktop.parent`, not through a new constructor argument, because `activity_launcher_home.xml` makes `launcherDesktop` the single direct child of `launcherGridScroll` in both orientations. Widening the constructor would have pulled `LauncherHomeActivity.kt` into this phase, which its `Files Touched` does not cover.
- 2026-08-06 - `ACTION_DRAG_LOCATION` alone is not enough to keep scrolling: it fires on movement, so a finger held still at the edge would stop the desktop dead. The step is therefore driven by a frame callback that re-posts itself, torn down on DROP, on DRAG_ENDED, and as soon as the point leaves the band - the leak Layer 2 of the phase audit asks about. It also stops itself when the container reports an unchanged offset, so reaching the end of the desktop does not burn a callback per frame for the rest of the gesture.

---

## Step Log

- 2026-08-06 - detekt `ReturnCount` rejected the first shape of `autoScroller.run` (3 returns, limit 2). Rewritten as one combined guard plus a single `if/else` tail, the same correction `LauncherHomeViewModel.run` already carries a comment about. Re-verified: `.\a.ps1 fk` exit 0, `post-change -ScopeToFile` PASS.
- 2026-08-06 - Phase-boundary audit (`docs/CODE_AUDIT_PROTOCOL.md`), scope `LauncherEditModeManager.kt` + `dimens.xml`.
  - Layer 1 - PASS. No business logic added: the manager still only maps a gesture to a scroll of its own container and to `viewModel.moveCell`, which is what its class KDoc claims it does. 156 -> 153 LOC, inside the phase budget of 160 and far under the 1500 ceiling. No `!!`, no new nullability defence, nesting unchanged.
  - Layer 2 - PASS with one hardening applied, below. No coroutine, dispatcher or Flow was touched, so most of the layer does not apply. `autoScrollStepPx` is shared mutable state, but both writers - the drag callback and the frame callback - run on the main thread, and that confinement is stated in its KDoc rather than left implied.
  - Layer 3 - P2 fixed in phase. The frame callback is an anonymous `Runnable`, so it holds the manager, which holds the desktop, the taskbar anchor and the ViewModel. `ACTION_DRAG_ENDED` is guaranteed for a drag that started, so the ordinary path always tears the loop down - but a window destroyed before that event is dispatched would leave a self-re-posting callback on the main-thread choreographer with no edge left to stop it. Guarded by refusing to continue once the desktop is detached from its window: the loop simply stops re-posting, which costs one term in an existing condition and no extra return.
  - Layer 4 - not applicable, no Room surface touched.
  - P3 noted, not acted on: `updateAutoScroll` reads the band dimension on every `ACTION_DRAG_LOCATION` rather than caching it. `Resources.getDimensionPixelSize` is a cached lookup and the event rate is bounded by the frame rate, so a field would trade a measurable cost for none.

- 2026-08-06 - Screenshot deferred (no device): `device-ready.ps1` reported `no-device` at session start. This phase's Done Criteria do not require one, so the deferral is recorded rather than blocking the phase. Strategic §11.3 stays a manual gate either way - that a drag reaches a row which was off-screen is not provable by a static predicate.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, `BUILD SUCCESSFUL`, run after the audit hardening so the verdict covers the shipped code.
- [x] `Grep` for `TODO(phase-03)` returns zero hits (0 occurrences across 0 files).
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1`: `dimens.xml`, then `LauncherEditModeManager.kt` twice (implementation, then the audit hardening).
- [x] Phase-boundary audit run - see Step Log. Layer 2 clean, Layer 3 found one P2 and it was fixed inside this phase.

---

## Handoff Notes to Next Phase

Final implementation phase. All three parts of the owner's request that the shipped desktop did not satisfy are now in code; what remains is recording the capability and closing.

---

## Rollback Plan

Revert the phase commit. Drag-to-move returns to its current behaviour - it still works within the visible area - and no persisted data or resource outside the launcher source set is affected.
