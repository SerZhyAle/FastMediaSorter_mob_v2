# Phase 02 — Command Panel Button

**Strategic spec:** [`../S0050_player-black-screen-mode.md`](../S0050_player-black-screen-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Completed:** 2026-05-02
**Started:** —
**Completed:** —

---

## Objective

Add a `BLACK_SCREEN` adaptive command to the player command panel: icon drawable, overflow menu item, `CommandPanelLayoutPlanner` entry, `PlayerState` flag, and click-handler wiring. The button appears in the center group immediately after Back for audio/video files when the setting is enabled.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`showBlackScreenButton` field exists in `AppSettings`).
- [ ] Understand the existing `CommandPanelLayoutPlanner.PlayerCommand` enum and `buildActiveCommands()` in `CommandPanelLayoutPlanner.kt`.
- [ ] Read `activity_player_unified.xml` center-group section to understand the ImageButton pattern (`android:visibility="gone"` default, `app:tint="@color/selector_player_button_tint"`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_black_screen.xml` | New | ≤ 20 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | existing file |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | existing layout |
| `app_v2/src/main/res/values/strings.xml` | Modified | existing file |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 700 |

---

## Steps

### Step 2.1 — Create adaptive icon drawable

**Files:** `app_v2/src/main/res/drawable/ic_black_screen.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new vector drawable `ic_black_screen.xml`. It should be a filled square (viewportWidth/Height 24, rectangle from ~3,3 to ~21,21). Use `fillColor="?attr/colorOnSurface"` so it renders as white on dark theme and black on light theme — matching the specification requirement without needing two separate drawables. No stroke.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ic_black_screen.xml` exists.
- `Grep` — `colorOnSurface` in `ic_black_screen.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: drawable/ic_black_screen.xml (new, 10 LOC). Dev log recorded.

---

### Step 2.2 — Add overflow menu item

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`
**Depends on:** Step 2.1

**Prompt for developer:**

> Add a new `<item>` to `overflow_menu_player.xml`:
> ```xml
> <item
>     android:id="@+id/menu_black_screen"
>     android:icon="@drawable/ic_black_screen"
>     android:title="@string/black_screen_button_title"
>     app:showAsAction="never" />
> ```
> Place it near the top of the file (high-priority commands appear first). Also add the string key `black_screen_button_title` = `"Black screen"` to `values/strings.xml`, and equivalents `"Чёрный экран"` / `"Чорний екран"` to `values-ru/` and `values-uk/`.

**Verification:**

- `Grep` — `menu_black_screen` in `overflow_menu_player.xml`.
- `Grep` — `black_screen_button_title` in `values/strings.xml`.
- `Grep` — `black_screen_button_title` in `values-ru/strings.xml`.
- `Grep` — `black_screen_button_title` in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. Files: overflow_menu_player.xml (+5 LOC), strings.xml×3 (+1 each). Dev log recorded.

---

### Step 2.3 — Add ImageButton to player layout center group

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`
**Depends on:** Step 2.2

**Prompt for developer:**

> Inside the center-group `LinearLayout` (the one with `android:layout_weight="1"` in `topCommandPanel`), add a new `ImageButton` as the **first child**:
> ```xml
> <ImageButton
>     android:id="@+id/btnBlackScreenCmd"
>     android:layout_width="@dimen/player_cmd_button_size"
>     android:layout_height="@dimen/player_cmd_button_size"
>     android:background="?attr/selectableItemBackgroundBorderless"
>     android:contentDescription="@string/black_screen_button_title"
>     android:src="@drawable/ic_black_screen"
>     android:visibility="gone"
>     app:tint="@color/selector_player_button_tint"
>     android:scaleType="centerInside"
>     android:padding="@dimen/player_button_padding" />
> ```
> Placing it first ensures it appears immediately after `btnBack` when visible, satisfying the "right after Back" position requirement.

**Verification:**

- `Grep` — `btnBlackScreenCmd` in `activity_player_unified.xml`.
- `Grep` — `ic_black_screen` in `activity_player_unified.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: activity_player_unified.xml (+3 LOC). Dev log recorded.

---

### Step 2.4 — Add BLACK_SCREEN to CommandPanelLayoutPlanner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 2.3

**Prompt for developer:**

> 1. In the `PlayerCommand` enum, add a new entry at the **beginning of Group 2** with priority **195** (before `RENAME` at 200):
>    ```kotlin
>    BLACK_SCREEN(195, R.id.menu_black_screen, true,
>        R.string.black_screen_button_title, R.drawable.ic_black_screen),
>    ```
>    Priority 195 makes it the highest-priority adaptive command, so it stays on the bar longest and overflows only when all other commands have been pushed to overflow first.
>
> 2. In `buildActiveCommands()`, add the condition at the top of the Group 2 block:
>    ```kotlin
>    if ((isAudio || isVideo) && state.showBlackScreenButton) add(PlayerCommand.BLACK_SCREEN)
>    ```

**Verification:**

- `Grep` — `BLACK_SCREEN(195` in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `showBlackScreenButton` in `CommandPanelLayoutPlanner.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: CommandPanelLayoutPlanner.kt (+2 LOC enum, +1 LOC buildActiveCommands). Dev log recorded.

---

### Step 2.5 — Add showBlackScreenButton to PlayerState

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 2.4

**Prompt for developer:**

> 1. Add `val showBlackScreenButton: Boolean = false` to `PlayerViewModel.PlayerState` data class (alongside existing flags like `enableTranslation`, `enableOcr`).
> 2. In the state-building block where `AppSettings` fields are mapped into `PlayerState`, assign `showBlackScreenButton = settings.showBlackScreenButton`.

**Verification:**

- `Grep` — `showBlackScreenButton` in `PlayerViewModel.kt` (declaration in PlayerState).
- `Grep` — `showBlackScreenButton` in `PlayerMediaFilesLoader.kt` (assignment from settings — loadSettings() was extracted there).

> _Spec patch: assignment lives in `PlayerMediaFilesLoader.kt` (Wave 4.1 extraction), not `PlayerViewModel.kt`._

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS (patched predicates). Files: PlayerViewModel.kt (+1 LOC), PlayerMediaFilesLoader.kt (+1 LOC). Dev log recorded.

---

### Step 2.6 — Wire click handler for BLACK_SCREEN button

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt`
**Depends on:** Step 2.5

**Prompt for developer:**

> In `PlayerControlsSetupManager.setupCommandButtons()` (or the equivalent method that wires command-bar button click listeners), add a click listener for `binding.btnBlackScreenCmd` that calls the overlay activation method. The exact call target will be determined in Phase 03; for now, add a stub:
> ```kotlin
> binding.btnBlackScreenCmd.setOnClickListener {
>     // TODO(phase-03): activity.showBlackScreenOverlay()
> }
> ```
> Also handle `R.id.menu_black_screen` in the overflow menu item selection handler (same stub comment).

**Verification:**

- `Grep` — `btnBlackScreenCmd` in `PlayerControlsSetupManager.kt`.
- `Grep` — `menu_black_screen` in `CommandPanelController.kt` (overflow dispatch lives there, not in SetupManager).

> _Spec patch: overflow click dispatch follows existing architecture in `CommandPanelController.kt` via `CommandPanelCallback`._

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS (patched predicates). Files: PlayerControlsSetupManager.kt (+4 LOC), CommandPanelController.kt (+2 LOC), PlayerCommandPanelCallbackImpl.kt (+4 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 2.* above is `[x] done`.
- [x] Project compiles — run `/build`. (auto-build — PASS)
- [x] `Grep` for `TODO(phase-01)` returns zero hits (no leftover phase-01 markers).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `R.id.menu_black_screen` and `btnBlackScreenCmd` are declared and wired with stub `TODO(phase-03)` markers.
- `CommandPanelLayoutPlanner.BLACK_SCREEN` at priority 195 activates for audio/video when `showBlackScreenButton = true`.
- Phase 03 must replace the `TODO(phase-03)` stub with a real call to `BlackScreenOverlayManager`.

---

## Rollback Plan

Revert phase commit(s). The drawable, menu item, layout button, and planner entry are all additive — no existing logic modified beyond ViewModel state.
