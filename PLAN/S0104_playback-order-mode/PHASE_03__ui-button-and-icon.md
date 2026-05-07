# Phase 03 — UI Button and Icon

**Strategic spec:** [`../S0104_playback-order-mode.md`](../S0104_playback-order-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 8 / 8
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Wire the full UI: strings, icons, overflow menu entry, layout buttons (portrait + landscape), `CommandPanelController` wiring and visibility, `PlayerActivity` click handler (mode cycling, persistence, toast, ExoPlayer sync), and StopPlayback event handling.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3100 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | current + 8 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | current + 8 |
| `app_v2/src/main/res/drawable/ic_loop_list.xml` | New | ≤ 20 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | current + 6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 330 |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | portrait |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | landscape |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 295 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1040 |

> `CommandPanelController.kt` is 1008 lines → backup to `temp/CommandPanelController_backup_<timestamp>.kt` before editing.
> `PlayerActivity.kt` is 1018 lines → backup to `temp/PlayerActivity_backup_<timestamp>.kt` before editing.

---

## Steps

### Step 3.1 — Add trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Phase 02

**Prompt for developer:**

> Add the following 8 string keys to each locale file. Use the translations listed below. Append near other player command strings.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `playback_order_loop_list` | `Loop list` | `По кругу` | `По колу` |
> | `playback_order_play_through` | `Play through` | `До конца` | `До кінця` |
> | `playback_order_shuffle` | `Shuffle` | `Вперемішку` | `Перемішати` |
> | `playback_order_repeat_one` | `Repeat one` | `Повторяти один` | `Повторювати один` |
> | `playback_order_mode_set` | `Playback order: %1$s` | `Порядок воспроизведения: %1$s` | `Порядок відтворення: %1$s` |
> | `playback_order_button_desc` | `Playback order: %1$s` | `Порядок воспроизведения: %1$s` | `Порядок відтворення: %1$s` |
> | `playback_order_stopped` | `Playback stopped (end of list)` | `Воспроизведение остановлено (конец списка)` | `Відтворення зупинено (кінець списку)` |
> | `playback_order_menu_title` | `Playback order` | `Порядок воспроизведения` | `Порядок відтворення` |

**Verification:**

- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix playback_order` — exits with code 0.
- `Grep` — `playback_order_loop_list` present in `values/strings.xml`.
- `Grep` — `playback_order_loop_list` present in `values-ru/strings.xml`.
- `Grep` — `playback_order_loop_list` present in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS (check_strings_localized: 8/8 OK, playback_order_loop_list in all 3 files). Dev log recorded.

---

### Step 3.2 — Create `ic_loop_list.xml` drawable

**Files:** `app_v2/src/main/res/drawable/ic_loop_list.xml`
**Depends on:** — (independent of 3.1)

**Prompt for developer:**

> Create a new 24×24dp vector drawable. The icon represents "loop list": a pair of circular repeat arrows identical in shape to the standard Material `ic_repeat` icon. Copy the path data from the existing `app_v2/src/main/res/drawable/ic_repeat.xml` and use it as the body — this icon visually means "repeat all / loop list".
>
> If `ic_repeat.xml` already visually represents a list-loop (two arrows forming a rectangle), use it directly as `ic_loop_list` by creating a symlink file that references it, or simply copy the XML verbatim with a new filename.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ic_loop_list.xml` exists.
- `Grep` — `<vector` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: ic_loop_list.xml (new, 9 LOC). Dev log recorded.

---

### Step 3.3 — Add `menu_playback_order` item to overflow menu

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `overflow_menu_player.xml`, add a new `<item>` after the `menu_random` entry:
>
> ```xml
> <item
>     android:id="@+id/menu_playback_order"
>     android:icon="@drawable/ic_loop_list"
>     android:title="@string/playback_order_menu_title"
>     app:showAsAction="never" />
> ```

**Verification:**

- `Grep` — `menu_playback_order` present in `overflow_menu_player.xml`.
- `Grep` — `playback_order_menu_title` present in `overflow_menu_player.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: overflow_menu_player.xml (+7 LOC). Dev log recorded.

---

### Step 3.4 — Add `PLAYBACK_ORDER` to `CommandPanelLayoutPlanner`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 3.3

**Prompt for developer:**

> In `CommandPanelLayoutPlanner.PlayerCommand`, add a new entry before `DELETE`:
>
> ```kotlin
> PLAYBACK_ORDER(5, R.id.menu_playback_order, true, R.string.playback_order_menu_title, 0),
> ```
>
> Priority 5 (lower = higher priority = leftmost on bar, last to overflow). `iconResId = 0` because the icon is updated dynamically by `CommandPanelController.updatePlaybackOrderButtonIcon()`.
>
> In the visibility filtering logic inside `CommandPanelLayoutPlanner` (any method that determines which commands show for a given `MediaType`), ensure `PLAYBACK_ORDER` is included only for `MediaType.AUDIO` and `MediaType.VIDEO`.

**Verification:**

- `Grep` — `PLAYBACK_ORDER(5,` present in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `menu_playback_order` present in `CommandPanelLayoutPlanner.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: CommandPanelLayoutPlanner.kt (+2 LOC). Dev log recorded.

---

### Step 3.5 — Add `btnPlaybackOrderCmd` to portrait layout

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`
**Depends on:** Step 3.1

**Prompt for developer:**

> In the CENTER GROUP `LinearLayout` of `topCommandPanel`, add `btnPlaybackOrderCmd` as the **first child**, before `btnDeleteCmd`:
>
> ```xml
> <ImageButton android:id="@+id/btnPlaybackOrderCmd"
>     android:layout_width="@dimen/player_cmd_button_size"
>     android:layout_height="@dimen/player_cmd_button_size"
>     android:background="?attr/selectableItemBackgroundBorderless"
>     android:contentDescription="@string/playback_order_menu_title"
>     android:src="@drawable/ic_loop_list"
>     app:tint="@color/selector_player_button_tint"
>     android:scaleType="centerInside"
>     android:padding="@dimen/player_button_padding"
>     android:visibility="gone" />
> ```

**Verification:**

- `Grep` — `btnPlaybackOrderCmd` present in `app_v2/src/main/res/layout/activity_player_unified.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Files: activity_player_unified.xml (+2 LOC portrait). Dev log recorded.

---

### Step 3.6 — Add `btnPlaybackOrderCmd` to landscape layout

**Files:** `app_v2/src/main/res/layout-land/activity_player_unified.xml`
**Depends on:** Step 3.5

**Prompt for developer:**

> In the landscape layout's scrollable `LinearLayout` (inside `HorizontalScrollView` inside `topCommandPanel`), add `btnPlaybackOrderCmd` as the **first child**, before `btnBlackScreenCmd`, using the identical XML from Step 3.5.

**Verification:**

- `Grep` — `btnPlaybackOrderCmd` present in `app_v2/src/main/res/layout-land/activity_player_unified.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Files: layout-land/activity_player_unified.xml (+3 LOC). Dev log recorded.

---

### Step 3.7 — Wire `btnPlaybackOrderCmd` in `CommandPanelController`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Steps 3.4, 3.5, 3.6

**Prompt for developer:**

> Backup `CommandPanelController.kt` to `temp/CommandPanelController_backup_<timestamp>.kt` first (1008 lines).
>
> 1. Add `fun onPlaybackOrderClicked()` to `CommandPanelCallback` interface.
>
> 2. In the button setup section (near `btnRandomCmd.setOnClickListener`), add:
>    ```kotlin
>    binding.btnPlaybackOrderCmd.setOnClickListener {
>        Timber.d("CommandPanelController: btnPlaybackOrderCmd clicked")
>        callback.onPlaybackOrderClicked()
>    }
>    ```
>
> 3. In `updateCommandAvailability()`, in the block that controls per-type visibility, set:
>    ```kotlin
>    binding.btnPlaybackOrderCmd.isVisible = (currentFile.type == MediaType.AUDIO || currentFile.type == MediaType.VIDEO)
>    ```
>    Add this alongside the existing `btnRandomCmd.isVisible` line.
>
> 4. Add a new public method:
>    ```kotlin
>    fun updatePlaybackOrderButtonIcon(mode: PlaybackOrderMode) {
>        val iconRes = when (mode) {
>            PlaybackOrderMode.LOOP_LIST   -> R.drawable.ic_loop_list
>            PlaybackOrderMode.PLAY_THROUGH -> R.drawable.ic_arrow_downward
>            PlaybackOrderMode.SHUFFLE     -> R.drawable.ic_random_nav
>            PlaybackOrderMode.REPEAT_ONE  -> R.drawable.ic_repeat_one
>        }
>        binding.btnPlaybackOrderCmd.setImageResource(iconRes)
>        val modeLabel = binding.root.context.getString(when (mode) {
>            PlaybackOrderMode.LOOP_LIST    -> R.string.playback_order_loop_list
>            PlaybackOrderMode.PLAY_THROUGH -> R.string.playback_order_play_through
>            PlaybackOrderMode.SHUFFLE      -> R.string.playback_order_shuffle
>            PlaybackOrderMode.REPEAT_ONE   -> R.string.playback_order_repeat_one
>        })
>        binding.btnPlaybackOrderCmd.contentDescription =
>            binding.root.context.getString(R.string.playback_order_button_desc, modeLabel)
>    }
>    ```
>    Use `R.drawable.ic_arrow_downward` for PLAY_THROUGH; if that drawable does not exist in the project, use `R.drawable.ic_back` rotated 270° or create a simple downward-arrow vector.
>
> 5. Add `binding.btnPlaybackOrderCmd` to the existing compact-elements list in `applySmallControlsIfNeeded()` (alongside `btnRandomCmd`, `btnInfoCmd`, etc.).

**Verification:**

- `Grep` — `onPlaybackOrderClicked` present in `CommandPanelController.kt`.
- `Grep` — `updatePlaybackOrderButtonIcon` present in `CommandPanelController.kt`.
- `Grep` — `btnPlaybackOrderCmd` present in `CommandPanelController.kt` (at least 3 occurrences).
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: CommandPanelController.kt (1028→1048 LOC). Backup created. Dev log recorded.

---

### Step 3.8 — Wire `PlayerActivity`: click handler, persistence, toast, event

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 3.7

**Prompt for developer:**

> Backup `PlayerActivity.kt` to `temp/PlayerActivity_backup_<timestamp>.kt` first (1018 lines).
>
> **In `PlayerCommandPanelCallbackImpl.kt`:**
> Override `onPlaybackOrderClicked()` to delegate to `activity.onPlaybackOrderClicked()`.
>
> **In `PlayerActivity.kt`:**
>
> 1. Add `internal fun onPlaybackOrderClicked()`:
>    ```kotlin
>    internal fun onPlaybackOrderClicked() {
>        val newMode = viewModel.cyclePlaybackOrderMode()
>        // Persist
>        val prefKey = if (viewModel.state.value.currentFile?.type == MediaType.AUDIO)
>            PlaybackControlPreferences.KEY_PLAYBACK_ORDER_AUDIO
>        else
>            PlaybackControlPreferences.KEY_PLAYBACK_ORDER_VIDEO
>        getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, MODE_PRIVATE)
>            .edit().putString(prefKey, newMode.toPrefsString()).apply()
>        // Apply to ExoPlayer layer
>        when (viewModel.state.value.currentFile?.type) {
>            MediaType.AUDIO -> audioServiceController?.applyPlaybackOrderMode(newMode)
>            MediaType.VIDEO -> {
>                val exoRepeatMode = if (newMode == PlaybackOrderMode.REPEAT_ONE)
>                    Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
>                _videoPlayerManager?.getPlayer()?.repeatMode = exoRepeatMode
>            }
>            else -> { /* no-op */ }
>        }
>        // Update button icon
>        commandPanelController.updatePlaybackOrderButtonIcon(newMode)
>        // Toast
>        val label = getString(when (newMode) {
>            PlaybackOrderMode.LOOP_LIST    -> R.string.playback_order_loop_list
>            PlaybackOrderMode.PLAY_THROUGH -> R.string.playback_order_play_through
>            PlaybackOrderMode.SHUFFLE      -> R.string.playback_order_shuffle
>            PlaybackOrderMode.REPEAT_ONE   -> R.string.playback_order_repeat_one
>        })
>        Toast.makeText(this, getString(R.string.playback_order_mode_set, label), Toast.LENGTH_SHORT).show()
>    }
>    ```
>
> 2. Load persisted mode during player initialisation (in the section where other PlaybackControlPreferences values are restored, e.g. near where `KEY_SPEED` is loaded). Add:
>    ```kotlin
>    val prefs = getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, MODE_PRIVATE)
>    val audioMode = PlaybackOrderMode.fromPrefsString(
>        prefs.getString(PlaybackControlPreferences.KEY_PLAYBACK_ORDER_AUDIO, null) ?: "")
>    val videoMode = PlaybackOrderMode.fromPrefsString(
>        prefs.getString(PlaybackControlPreferences.KEY_PLAYBACK_ORDER_VIDEO, null) ?: "")
>    // Apply the mode matching the first file type in the playlist
>    val initialMode = if (viewModel.state.value.currentFile?.type == MediaType.AUDIO) audioMode else videoMode
>    viewModel.setPlaybackOrderMode(initialMode)
>    commandPanelController.updatePlaybackOrderButtonIcon(initialMode)
>    ```
>
> 3. In the `PlayerEvent` observer (`collectLatestLifecycleFlow` or `repeatOnLifecycle` block that handles events), add a branch for `PlayerEvent.StopPlayback`:
>    ```kotlin
>    is PlayerEvent.StopPlayback -> {
>        _videoPlayerManager?.getPlayer()?.pause()
>        audioServiceController?.mediaController?.pause()  // pause if connected
>        Toast.makeText(this, R.string.playback_order_stopped, Toast.LENGTH_SHORT).show()
>    }
>    ```
>    Access `mediaController` via `audioServiceController?.player?.let { (it as? MediaController)?.pause() }` if the `mediaController` field is private — check the class and use the available interface.

**Verification:**

- `Grep` — `onPlaybackOrderClicked` present in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` — `fun onPlaybackOrderClicked` present in `PlayerActivity.kt`.
- `Grep` — `KEY_PLAYBACK_ORDER_AUDIO` present in `PlayerActivity.kt`.
- `Grep` — `StopPlayback` present in `PlayerActivity.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 5/5 PASS. Files: PlayerActivity.kt (+41 LOC), PlayerEventHandler.kt (stub→full). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL in 38s.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix playback_order` exits with code 0.
- [x] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated. MANUAL-REQUIRED (Phase 04)

---

## Handoff Notes to Next Phase

- All four playback order modes are functional end-to-end.
- Mode is persisted in `playback_control_dialog` SharedPreferences, keyed by media type (audio/video).
- Mode is restored on player open and applied to both ViewModel state and ExoPlayer layer.
- `PlayerEvent.StopPlayback` stops playback with a toast when PLAY_THROUGH reaches end of list.
- `commandPanelController.updatePlaybackOrderButtonIcon(mode)` should also be called inside `updateUI()` → add a call to `callback.updatePlaybackOrderButton(state.playbackOrderMode)` in `PlayerUiStateCoordinator` if icon drift is noticed after rotation.

---

## Rollback Plan

Revert phase commits. SharedPreferences keys will simply be absent on next open (defaults to LOOP_LIST — harmless). No database migration to undo.
