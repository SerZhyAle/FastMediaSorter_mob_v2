# Phase 05 - Player menu entry

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Offer the same item in the video player's actions menu while a channel is playing, opening the window against the engine already showing it.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] The owner's placement ruling in strategic §3.3 is read: overflow menu only, no button on the command bar.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | ≤ 10 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 15 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 10 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 15 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 5 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 10 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt` | Modified | ≤ 60 added |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlannerTest.kt` | Modified | ≤ 40 added |

> `PlayerActivity.kt` is ~1415 LOC against the 1500 hard limit, so it gains only a one-line delegation, exactly as `showFileInfo` already does. Every file over 500 LOC gets a timestamped backup under `temp/S1474/` before editing.

---

## Steps

### Step 05.1 - Declare the command and put it in the stream-only list

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Declare the new item's id in the player overflow menu resource - that file is never inflated and exists only as an id pool, as its own comment states - then add a `PlayerCommand` entry for it with `barCapable = false`, the info icon and the step 01.2 title, and add it to the list returned by the live-video-stream branch of `buildActiveCommands`, next to the existing info command. Do not add it to any other branch.

**Why:**

The owner ruled on 2026-08-07 "Только в меню", and the live-stream branch is the single allow-list that decides what a playing channel offers - adding the command anywhere else would show it over local files, which strategic §2 does not cover.

**Verification:**

- `Grep` - the new id declared once in `overflow_menu_player.xml`.
- `Grep` - the new `PlayerCommand` entry carries `false` for bar capability.
- `Grep` - the entry appears inside the `isLiveVideoStream` branch and nowhere else in the planner.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `menu_stream_info` declared once in `overflow_menu_player.xml`; `PlayerCommand.STREAM_INFO` carries `false` for bar capability; `PlayerCommand.STREAM_INFO` is referenced exactly once in the planner - the `add()` inside the live-stream branch.
- Its priority is a named top-level constant `STREAM_INFO_PRIORITY = 496`, following the `ROTATE_CONTENT_PRIORITY` precedent already in the file: a bare literal fails the detekt MagicNumber gate for a new entry, while the older entries' literals are baselined. It must be top-level rather than in the enum's companion - an enum entry is constructed before its own companion exists, which the compiler rejects outright.

---

### Step 05.2 - Dispatch the click to a callback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Back up both files first. Add the new id to the overflow command dispatch and a matching callback entry, following the shape `menu_info` already uses: the controller routes the id to a callback method, the callback implementation delegates to the activity. Add no logic in either.

**Why:**

Both the native overflow menu and the big-buttons fallback render off the same command list and dispatch through the same handler, so wiring the id once covers both surfaces the player can present.

**Verification:**

- `Grep` - the new id handled in the controller's overflow dispatch.
- `Grep` - the new callback method declared on the callback interface and implemented once.
- Backup files present under `temp/S1474/`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `R.id.menu_stream_info -> callback.onStreamInfoClicked()` added to the controller's overflow dispatch beside `menu_info`; `fun onStreamInfoClicked()` declared once on the callback interface and implemented once in `PlayerCommandPanelCallbackImpl`, delegating to the activity with no logic. Backup of `CommandPanelController.kt` (959 LOC) at `temp/S1474/CommandPanelController_20260808_0223.kt.bak`; `PlayerCommandPanelCallbackImpl.kt` is 437 LOC, under the 500 threshold, so Rule 5 asks for none.

---

### Step 05.3 - Open the window from the player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Back up both files first. Add a one-line delegation on the activity, mirroring `showFileInfo`, and put the work in the dialog/state manager: resolve the channel by the currently playing url through the existing by-url use case, hand the running engine from the video player manager to the window, and when the use case returns nothing open the window with the url alone. Never release or reconfigure the player's engine.

**Why:**

Strategic §11 criterion 6 requires the playing channel to be reported without a second connection, and the owner ruled on 2026-08-07 that a channel missing from the list opens the window without its stored part rather than losing the menu item; the by-url resolution is the pattern the player already uses three times to recover a channel from its url.

**Verification:**

- `Grep` - the activity's new function is a single-expression delegation.
- `Grep` - the by-url use case is called in the manager.
- `Grep` - the url-only dialog path is taken when the use case yields null.
- `Grep` - `\.release\(\)` is not called on the player in the new code.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `internal fun showStreamInfo() = dialogAndUiStateManager.showStreamInfo()` is a single-expression delegation, mirroring `showFileInfo` one line above it. The manager resolves the channel through the by-url use case, opens the window with `source` - which is nullable, so a channel absent from the list takes the url-only path by construction rather than by a second branch - and hands over `activity._videoPlayerManager?.getPlayer()`. No `release()`, `prepare()` or `stop()` appears in the new code. Backups of both files under `temp/S1474/`.
- **Files Touched extended, recorded here rather than done silently:** `PlayerViewModel.kt` gained a 2-line `suspend fun streamSourceByUrl(url)`. The by-url use case is injected into the view model, not into the dialog manager, so the manager had no way to reach it; the two existing callers of that use case both record an outcome as a side effect, which a read-only window must not do. Adding the plain read is smaller than injecting the use case a second time.
- `PlayerActivity.kt` is 1418 LOC after the change, still under the 1500 limit.

---

### Step 05.4 - Extend the planner test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlannerTest.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add cases asserting that the new command is present in the live-video-stream command list and absent from the non-stream lists.

**Why:**

The stream allow-list is a hand-maintained list with no compiler check, so the one thing that can silently regress here - the item appearing over local files, or vanishing from streams - is exactly what this test pins.

**Verification:**

- Run `.\a.ps1 fu` and confirm `CommandPanelLayoutPlannerTest` reports zero failures.
- `Grep` - both the present-for-stream and absent-for-file assertions exist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. Both live-stream expectation lists gained `STREAM_INFO` after `INFO`, and the non-stream test gained an explicit `assertFalse` that it never appears for an ordinary file - the regression this test exists to catch. Scoped run: `tests="11" skipped="0" failures="0" errors="0"`.
- A blind sed pass first inserted `STREAM_INFO` into two unrelated layout tests that merely happened to list `INFO`; both were removed before the run. Worth the note: the marker used to find an insertion point was not unique to the cases the step named.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, 2026-08-08.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in the files this phase added lines to.
- [x] `PlayerActivity.kt` is 1418 LOC, under 1500.
- [x] Dev log entry added via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `screenshot deferred (no device)` - S1338's UI gate. Placement is on record: strategic §3.3, owner ruling 2026-08-07, «Только в меню» - overflow only, no command-bar button.

## Phase-boundary audit (2026-08-08)

- Layer 1 - architecture and budgets. Every touched file gained only what its role allows: the activity a one-line delegation, the callback a one-line delegation, the controller one dispatch line, the view model a two-line read. `PlayerActivity.kt` 1418 of 1500.
- Layer 2 - coroutines. The one new coroutine runs in the manager's `lifecycleScope`, so it dies with the screen. It performs a single suspend read and then opens a dialog on the main thread.
- Layer 3 - engine ownership. The player's engine is passed to the window and never released, prepared or stopped there - the window's own probe releases only what it opened itself.
- Layer 4 - Room. The by-url read goes through the existing use case; no query, entity or migration changed.
- P2, fixed in this phase: a `MagicNumber` on the new priority (named constant, top-level for the enum-initialisation reason recorded in step 05.1), `ArgumentListWrapping` on the new entry, and a `LongMethod` on `buildActiveCommands`, which my one added line pushed to exactly its 80-line limit. The live-stream branch was extracted to `liveVideoStreamCommands` with its composition unchanged - the test asserting that exact composition still passes, which is what proves the extraction was faithful.
- Closure verdict was `PASS WITH ADVISORIES (1)`, the advisory being `detekt-preflight`. Checked rather than waved through: the over-length lines it reports sit at `PlayerDialogAndUiStateManager.kt:310/340/343/491/494` and `PlayerViewModel.kt:101/222/250/263/267`, all pre-existing; no line this phase added exceeds 120, and the authoritative scoped `assert-detekt` returns PASS.

---

## Handoff Notes to Next Phase

Both entry points are live. What remains is the capability record, the catalog regeneration and the debug tags the device test needs.

---

## Rollback Plan

Revert phase commit(s) - the player menu returns to its previous item set; the card path from Phase 04 is independent and survives.
