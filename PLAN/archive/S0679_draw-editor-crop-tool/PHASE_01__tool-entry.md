# Phase 01 - Tool Entry (inert)

**Strategic spec:** [`../S0679_draw-editor-crop-tool.md`](../S0679_draw-editor-crop-tool.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Register a new `CROP` tool in the draw editor tool selector (enum value, menu item, icon, trilingual label). Selecting it sets editor state only - no crop behaviour yet (inert and safe).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 820 |
| `app_v2/src/main/res/menu/menu_draw_tool_selector.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> `ImageDrawOverlayManager.kt` is >500 LOC - create a timestamped backup in `temp/` before editing (Strict Rule 5).

---

## Steps

### Step 01.1 - Add trilingual `draw_tool_crop` string

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add string key `draw_tool_crop` in one lockstep call across EN/RU/UK: EN `Crop`, RU `Обрезка`, UK `Обрізання`. This labels the new tool in the draw tool selector; it is distinct from the overflow file-crop items (`menu_crop`/`menu_crop_to_file`) which stay unchanged. Use `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key draw_tool_crop -En "Crop" -Ru "Обрезка" -Uk "Обрізання"`. Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (short, plain, no jargon).

**Verification:**

- `Grep` - `name="draw_tool_crop"` matches once in each of the three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_tool_crop"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS (grep x3 locales; parity exit 0; tone §6 OK). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml.

---

### Step 01.2 - Add `draw_tool_crop` menu item

**Files:** `res/menu/menu_draw_tool_selector.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a new `<item>` with id `@+id/draw_tool_crop`, icon `@drawable/ic_crop` (already exists, used by the overflow file-crop), `iconTint="#FFFFFF"` (match siblings), and title `@string/draw_tool_crop`. Place it after `draw_tool_text`.

**Verification:**

- `Grep` - `@+id/draw_tool_crop` matches once in `menu_draw_tool_selector.xml`.
- `Grep` - `@drawable/ic_crop` present in that item.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS (draw_tool_crop id x1; ic_crop present). File: menu/menu_draw_tool_selector.xml.

---

### Step 01.3 - Add `CROP` to `DrawTool` enum and tool-selection wiring

**Files:** `ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Back up the file to `temp/` first (>500 LOC). Add `CROP` to `enum class DrawTool`. In the tool-selector `setOnMenuItemClickListener` `when`, map `R.id.draw_tool_crop -> DrawTool.CROP`. In `iconForTool`, map `DrawTool.CROP -> R.drawable.ic_crop`. Do not add crop behaviour yet - selecting CROP only updates `selectedTool` and the selector icon. The canvas `onTouchEvent`/`onDraw` `when (selectedTool)` branches must keep compiling; CROP falls through to no drawing action (inert).

**Verification:**

- `Grep` - `CROP` present inside `enum class DrawTool`.
- `Grep` - `R.id.draw_tool_crop -> DrawTool.CROP` matches once.
- `Grep` - `DrawTool.CROP -> com.sza.fastmediasorter.R.drawable.ic_crop` (or `R.drawable.ic_crop`) matches once in `iconForTool`.
- `.\a.ps1 fk` compiles (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 4/4 PASS (CROP in enum; selector branch x1; iconForTool x1; fk exit 0). Added inert `DrawTool.CROP -> Unit` branch in ACTION_UP `when` for exhaustiveness. File: ui/player/helpers/ImageDrawOverlayManager.kt.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`. (batched at ticket close via `close-and-log.ps1`)

---

## Handoff Notes to Next Phase

`DrawTool.CROP` is selectable but inert. Phase 03 mounts the selection overlay when CROP is active; Phase 04 wires the apply path. The icon `ic_crop` and label are final.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing behaviour beyond an inert menu item.
