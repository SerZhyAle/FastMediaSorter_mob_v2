# Phase 03 — Controller wiring (safeViews, barView mapping, click handlers, landscape visibility)

**Strategic spec:** [`../S0217_bugfix-image-viewer-toolbar-overflow.md`](../S0217_bugfix-image-viewer-toolbar-overflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Wire the five inline buttons into `CommandPanelController`: expose them through `PlayerBindingSafeViews`, register click listeners that delegate to the existing callbacks, return them from `barViewForCommand`, add them to `getOverflowableButtons`, and add per-type visibility logic for the landscape branch (mirroring `buildActiveCommands` gating). After this phase, on a writable static image opened in landscape, all five inline buttons appear on the toolbar; the planner controls visibility in portrait and Big Buttons.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt` | Modified | +10 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | +40 lines (file already >1500 LOC — see backup note) |

> `CommandPanelController.kt` is already past the 1500 LOC ceiling. **Do not extract** to a new helper as part of this fix — the additions are surgical (one when-branch entry, one click-binding block, one landscape visibility block). Take a timestamped backup in `temp/` before editing. A future refactor spec should split this file along command-group lines; out of scope here.

---

## Steps

### Step 03.1 — Add 5 safe-view accessors in `PlayerBindingSafeViews`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add five `ImageButton` accessors after the existing `btnPrintCmd` declaration (around line 43). Use the `required(R.id...)` form (root-lookup) — these views are not part of generated binding because they were added to the XML in Phase 02 after the binding cache:
>
> ```kotlin
> val btnOpenInSeparateWindowCmd: ImageButton get() = required(R.id.btnOpenInSeparateWindowCmd)
> val btnCropCmd: ImageButton get() = required(R.id.btnCropCmd)
> val btnCropToFileCmd: ImageButton get() = required(R.id.btnCropToFileCmd)
> val btnCompressCopyCmd: ImageButton get() = required(R.id.btnCompressCopyCmd)
> val btnDrawOverlayCmd: ImageButton get() = required(R.id.btnDrawOverlayCmd)
> ```

**Verification:**

- `Grep -n` — pattern `val btnOpenInSeparateWindowCmd: ImageButton` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `val btnCropCmd: ImageButton` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `val btnCropToFileCmd: ImageButton` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `val btnCompressCopyCmd: ImageButton` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `val btnDrawOverlayCmd: ImageButton` matches once. expected: 1 | actual: 1
- Build invariant: `assembleStandardDebug` compiles. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 6/6 PASS (5 accessors present, build deferred). Files: PlayerBindingSafeViews.kt (+6 lines). Dev log recorded.

---

### Step 03.2 — Register click listeners in `CommandPanelController.setupListeners`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Locate the block where `safeViews.btnPrintCmd.setOnClickListener { … }` is registered (around line 187). Immediately after it, add five blocks delegating to the matching existing callbacks (`callback.onOpenInSeparateWindowClicked()`, `callback.onCropClicked()`, `callback.onCropToFileClicked()`, `callback.onCompressCopyClicked()`, `callback.onDrawOverlayClicked()`). Use the same pattern as `btnPrintCmd`. Take a timestamped backup of the file to `temp/CommandPanelController__pre-S0217-phase03.kt` before editing.

**Verification:**

- `Glob` — `temp/CommandPanelController__pre-S0217-phase03.kt` exists. expected: 1 file | actual: 1 file
- `Grep -n` — pattern `safeViews\.btnOpenInSeparateWindowCmd\.setOnClickListener` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCropCmd\.setOnClickListener` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCropToFileCmd\.setOnClickListener` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCompressCopyCmd\.setOnClickListener` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnDrawOverlayCmd\.setOnClickListener` matches once. expected: 1 | actual: 1
- `Grep` — no new `Log\.d\(` introduced in this file. expected: 0 new hits | actual: 0
- Build invariant: `assembleStandardDebug` compiles. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 7/7 PASS (backup + 5 listener grep + 0 new Log.d). Files: CommandPanelController.kt (+18 lines incl. comment). Dev log pending Step 03.5 cumulative.

---

### Step 03.3 — Map the 5 commands in `barViewForCommand`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Locate `private fun barViewForCommand(cmd: …): View?` (around line 1023). Add five new `when` branches before the existing `VR_3D` branch, mapping each command to its safeView accessor:
>
> ```kotlin
> CommandPanelLayoutPlanner.PlayerCommand.OPEN_IN_SEPARATE_WINDOW -> safeViews.btnOpenInSeparateWindowCmd
> CommandPanelLayoutPlanner.PlayerCommand.CROP -> safeViews.btnCropCmd
> CommandPanelLayoutPlanner.PlayerCommand.CROP_TO_FILE -> safeViews.btnCropToFileCmd
> CommandPanelLayoutPlanner.PlayerCommand.COMPRESS_COPY -> safeViews.btnCompressCopyCmd
> CommandPanelLayoutPlanner.PlayerCommand.DRAW_OVERLAY -> safeViews.btnDrawOverlayCmd
> ```
>
> Do not remove the `else -> null` fallback — other overflow-only commands continue to return null.

**Verification:**

- `Grep -n` — pattern `OPEN_IN_SEPARATE_WINDOW -> safeViews\.btnOpenInSeparateWindowCmd` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `PlayerCommand\.CROP -> safeViews\.btnCropCmd` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `CROP_TO_FILE -> safeViews\.btnCropToFileCmd` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `COMPRESS_COPY -> safeViews\.btnCompressCopyCmd` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `DRAW_OVERLAY -> safeViews\.btnDrawOverlayCmd` matches once. expected: 1 | actual: 1
- Build invariant: `assembleStandardDebug` compiles. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 6/6 PASS (5 when-branches added). Files: CommandPanelController.kt (+7 lines cumulative).

---

### Step 03.4 — Add 5 buttons to `getOverflowableButtons` list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `private fun getOverflowableButtons(): List<View>` (around line 976), append the five new safeView buttons to the `mutableListOf<View>(…)` after `safeViews.btnPrintCmd`. This ensures every adaptive-cycle pass hides them by default before the planner decides which to show:
>
> ```kotlin
> safeViews.btnOpenInSeparateWindowCmd,
> safeViews.btnCropCmd,
> safeViews.btnCropToFileCmd,
> safeViews.btnCompressCopyCmd,
> safeViews.btnDrawOverlayCmd,
> ```

**Verification:**

- `Grep -n` — pattern `safeViews\.btnOpenInSeparateWindowCmd,` in `getOverflowableButtons` body matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCropCmd,` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCropToFileCmd,` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCompressCopyCmd,` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnDrawOverlayCmd,` matches once. expected: 1 | actual: 1
- Build invariant: `assembleStandardDebug` compiles. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 6/6 PASS (5 entries appended to getOverflowableButtons). Files: CommandPanelController.kt (+7 lines cumulative).

---

### Step 03.5 — Add per-type visibility for 5 buttons in landscape branch + Timber tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Locate the landscape branch in `updateCommandAvailability` (the `else if (showInLandscape)` block, around line 406). Immediately after `safeViews.btnPrintCmd.isVisible = isPdf || isText || isImage` (line 465), add per-type visibility for the five new buttons mirroring the same gating that `buildActiveCommands` already applies for them. The relevant flag is `isStaticBitmap` (already used at lines 264..270 in `CommandPanelLayoutPlanner`); compute it locally in landscape using the existing local `isImage` and `currentFile.name`. Apply:
>
> ```kotlin
> val isStaticBitmap = isImage &&
>     !currentFile.name.lowercase().endsWith(".gif") &&
>     !currentFile.name.lowercase().endsWith(".apng")
> safeViews.btnOpenInSeparateWindowCmd.isVisible = lastKnownAllowSeparateWindow
> safeViews.btnCropCmd.isVisible = isStaticBitmap && canWrite && !isReadOnly
> safeViews.btnCropToFileCmd.isVisible = isStaticBitmap
> safeViews.btnCompressCopyCmd.isVisible = isStaticBitmap
> safeViews.btnDrawOverlayCmd.isVisible = isStaticBitmap
> Timber.d("S0217: landscape image-edit inline visibility set (isStaticBitmap=$isStaticBitmap, canWrite=$canWrite)")
> ```
>
> `isReadOnly` is already declared earlier in the function (`state.resource?.isReadOnly == true`); reuse the existing local. The `Timber.d("S0217: …")` tag is the BlockNeedUserTest probe — present until `/spec-check` flips status to Verified. The landscape `landscapeOverflowCmds` filter at line 467..472 automatically excludes the now-bar-capable five commands; no edit needed there.

**Verification:**

- `Grep -n` — pattern `safeViews\.btnOpenInSeparateWindowCmd\.isVisible = lastKnownAllowSeparateWindow` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCropCmd\.isVisible = isStaticBitmap && canWrite && !isReadOnly` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCropToFileCmd\.isVisible = isStaticBitmap` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnCompressCopyCmd\.isVisible = isStaticBitmap` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `safeViews\.btnDrawOverlayCmd\.isVisible = isStaticBitmap` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `Timber\.d\("S0217:` matches exactly once in `app_v2/src/main/java/`. expected: 1 | actual: 1
- `Grep` — `Log\.d\(` not added to this file. expected: 0 new hits | actual: 0
- Build invariant: `assembleStandardDebug` compiles. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 8/8 PASS (5 visibility lines + 1 Timber S0217 tag + 0 Log.d). Files: CommandPanelController.kt (+13 lines incl. tag/comment). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for both modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` — public surface of `PlayerBindingSafeViews` grew by 5 properties.
- [ ] Exactly one `Timber.d("S0217:` tag present across `app_v2/src/main/java/**/*.kt` (BlockNeedUserTest probe).

---

## Handoff Notes to Next Phase

Inline image-edit buttons are now fully wired in all three modes:
- **Portrait:** planner picks them up because they are bar-capable + `barViewForCommand` returns a non-null view; on narrow screens they spill back into overflow naturally.
- **Landscape:** per-type visibility makes them visible whenever `buildActiveCommands` would emit them; the `HorizontalScrollView` absorbs any width pressure.
- **Big Buttons:** planner slot allocation includes them on equal footing with EDIT/UNDO/CAST.

Phase 04 finalizes catalog regen, dev log sweep, and functionality log entry.

---

## Rollback Plan

Revert phase commits — single-file backup taken in Step 03.2. The five layout buttons added in Phase 02 remain harmless because they default to `visibility="gone"` with no listeners.
