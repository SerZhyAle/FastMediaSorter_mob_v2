# Phase 02 — PlayerBigButtonsModeManager

**Strategic spec:** [`../S0158_player-large-buttons.md`](../S0158_player-large-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Create `PlayerBigButtonsModeManager` — a new helper class that applies Big Buttons Mode layout changes to the top command panel and the bottom playback button row. No UI wiring in this phase; the class is self-contained and testable in isolation.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt` | New | ≤ 400 |

---

## Steps

### Step 02.1 — Create `PlayerBigButtonsModeManager` — top panel

**Files:** `PlayerBigButtonsModeManager.kt` (New)
**Depends on:** Phase 01 done

**Prompt for developer:**

> Create `PlayerBigButtonsModeManager` in `ui/player/helpers/`. The class receives `context: Context` in its constructor. All methods are stateless (no stored view references).
>
> **Top panel — `applyToTopCommandPanel`**
>
> Signature:
> ```kotlin
> fun applyToTopCommandPanel(
>     topCommandPanel: LinearLayout,
>     visibleButtons: List<View>,       // ordered list of buttons currently visible on panel
>     overflowButton: View?,            // the overflow (three-dots / ⋯) button, if present
>     bigButtonsMode: Boolean
> )
> ```
>
> When `bigButtonsMode = true`:
> - Set `topCommandPanel.layoutParams.height` to 2× its current measured height (in px). If measured height is 0 (not yet laid out), use `topCommandPanel.resources.getDimensionPixelSize(R.dimen.player_cmd_button_size) * 2` as fallback.
> - For each button in `visibleButtons` (+ `overflowButton` if non-null): set `layoutParams.width = 0` and `layoutParams.weight = 1f` (distributes 100% width equally across all buttons). Set `layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT` so buttons fill the doubled-height container.
> - On each ImageButton: scale its padding-based icon area by calling `button.setPadding(0, 4.dp, 0, 4.dp)` (minimal vertical padding so icon fills the button). 4.dp = `(4 * context.resources.displayMetrics.density).toInt()`.
> - On each button that is or has a `TextView` child: set text size to `18sp` via `button.textSize = 18f` or layout param; this applies to `MaterialButton` instances (`btnPrevious`, `btnNext`, `btnPlayPause`).
> - On ImageButton children: call `button.scaleType = ImageView.ScaleType.CENTER_INSIDE` (already default, ensures icon scales to fill the doubled area).
> - On each visible ImageButton in `visibleButtons`: set its `tag = button.contentDescription` (preserve for label in Step 02.1 label overlay if implemented). **Short label display:** add a `TextView` label below each ImageButton programmatically by wrapping each button in a vertical `LinearLayout`, OR set the button's `tooltipText` to the short label (simpler, avoids layout mutation). Preferred: use a custom approach that works in the existing `topCommandPanel` `LinearLayout` without restructuring the XML. **Implementation decision**: for ImageButton targets, set `button.contentDescription` is already used for a11y; for visible short text, the simplest approach in an existing `LinearLayout` is to use `button.tooltipText` (API 26+) — this is below minSdk 26, so it is safe. However, `tooltipText` only shows on long-press. For always-visible text, the developer must wrap each visible ImageButton in a dynamically-created vertical `LinearLayout` containing the `ImageButton` + a small `TextView` with short label text. Keep this wrapper inside `topCommandPanel` replacing the original `ImageButton` position. Store original button parents in a map so `restoreTopCommandPanel` can undo the wrapping.
>
>   **Alternative (simpler):** Store the list of `(view, shortLabel)` pairs and set `view.tooltipText = shortLabel` for all API 26+ (our minSdk is 26). This does NOT show always-visible text — it only shows on long-press. If always-visible short labels are required, the wrapper approach is needed.
>
>   **Decision**: use the wrapper approach. Each `visibleButton` in `visibleButtons` is replaced inside `topCommandPanel` with a vertical `LinearLayout(context)` containing: `ImageButton` (the original view, `layout_width=match_parent`, `layout_height=0dp`, `weight=1`) + `TextView` (`layout_width=match_parent`, `layout_height=wrap_content`, text = short label, textSize = 12sp, gravity = center, textColor = white).
>
>   Short label source: `PlayerCommand.values().find { context.getString(it.titleResId) == button.contentDescription }?.let { cmd -> if (cmd.shortTitleResId != 0) context.getString(cmd.shortTitleResId) else context.getString(cmd.titleResId) } ?: button.contentDescription.toString()`.
>
>   For the Back button (`R.id.btnBack`), use `context.getString(R.string.back)` as short label (no `PlayerCommand` entry).
>
> - Call `topCommandPanel.requestLayout()` after all modifications.
>
> When `bigButtonsMode = false`: do nothing (standard mode — no changes applied by this manager).
>
> **Restore — `restoreTopCommandPanel`**
>
> Signature:
> ```kotlin
> fun restoreTopCommandPanel(topCommandPanel: LinearLayout)
> ```
>
> Unwraps any dynamically inserted `LinearLayout` wrappers, restoring original `ImageButton` positions and `LayoutParams` from stored originals. Clears the internal state map.
>
> **Store originals** at first call to `applyToTopCommandPanel` in a `MutableMap<Int, SavedButtonState>` (keyed by `view.id`), where `SavedButtonState` holds width, height, weight, padding. If the map is already populated, skip saving (idempotent).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBigButtonsModeManager.kt` exists.
- `Grep` — `class PlayerBigButtonsModeManager` matches exactly once.
- `Grep` — `fun applyToTopCommandPanel` present.
- `Grep` — `fun restoreTopCommandPanel` present.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerBigButtonsModeManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 5/5 PASS. Files: PlayerBigButtonsModeManager.kt (new, 279 LOC). Dev log recorded.

---

### Step 02.2 — Add bottom playback panel support

**Files:** `PlayerBigButtonsModeManager.kt` (Modified)
**Depends on:** Step 02.1

**Prompt for developer:**

> Add two methods to `PlayerBigButtonsModeManager` for the bottom playback button row.
>
> **`applyToBottomPlaybackRow`**
>
> Signature:
> ```kotlin
> fun applyToBottomPlaybackRow(
>     buttonRow: LinearLayout,    // the horizontal LinearLayout containing btnPrevious, btnPlayPause/etc., btnNext
>     bigButtonsMode: Boolean
> )
> ```
>
> When `bigButtonsMode = true`:
> - Save original `layoutParams.height` for `buttonRow` and each direct child `View` that `isVisible`.
> - Set `buttonRow.layoutParams.height` to 2× original measured height (px). Fallback: `resources.getDimensionPixelSize(R.dimen.player_cmd_button_size) * 2` when measured height = 0.
> - For each visible direct child of `buttonRow`: set `layoutParams.width = 0`, `layoutParams.weight = 1f`, `layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT`. If child is a `MaterialButton` (has text), set `child.textSize = 18f`.
> - Set `buttonRow.weightSum = visibleChildren.size.toFloat()`.
> - Call `buttonRow.requestLayout()`.
>
> When `bigButtonsMode = false`: do nothing.
>
> **`restoreBottomPlaybackRow`**
>
> Signature:
> ```kotlin
> fun restoreBottomPlaybackRow(buttonRow: LinearLayout)
> ```
>
> Restore all saved `LayoutParams` from the internal state map. Clear state. Call `buttonRow.requestLayout()`.
>
> **Overflow menu — `buildBigButtonsOverflowMenu`**
>
> Signature:
> ```kotlin
> fun buildBigButtonsOverflowMenu(
>     anchor: View,
>     commands: List<CommandPanelLayoutPlanner.PlayerCommand>,
>     bigButtonsMode: Boolean,
>     onItemSelected: (CommandPanelLayoutPlanner.PlayerCommand) -> Unit
> )
> ```
>
> When `bigButtonsMode = true`: use `ListPopupWindow` with a custom `ArrayAdapter` that inflates a row layout providing 2× row height and 18sp text. Each row shows the command icon (from `cmd.iconResId`, tinted dark gray if > 0) + full label (`context.getString(cmd.titleResId)`) in a horizontal `LinearLayout`. Row height: `(resources.getDimensionPixelSize(R.dimen.player_cmd_button_size) * 2)`.
>
> When `bigButtonsMode = false`: caller uses the existing `PopupMenu` path — this method does nothing (returns immediately).
>
> The `ListPopupWindow` must call `onItemSelected(commands[position])` on item click and dismiss itself.

**Verification:**

- `Grep` — `fun applyToBottomPlaybackRow` present in `PlayerBigButtonsModeManager.kt`.
- `Grep` — `fun restoreBottomPlaybackRow` present in `PlayerBigButtonsModeManager.kt`.
- `Grep` — `fun buildBigButtonsOverflowMenu` present in `PlayerBigButtonsModeManager.kt`.
- `Grep` — `ListPopupWindow` referenced in `PlayerBigButtonsModeManager.kt`.
- File LOC ≤ 400 (verify with line count).

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 5/5 PASS. All bottom panel + overflow methods verified. LOC=279 ≤ 400. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `PlayerBigButtonsModeManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 delivers a self-contained manager with apply/restore for both panels and a big-buttons overflow menu builder. Phase 04 wires it into `CommandPanelController` and `PlayerManagerInitializer`.

---

## Rollback Plan

Revert phase commit(s) — new class, no existing code changed.
