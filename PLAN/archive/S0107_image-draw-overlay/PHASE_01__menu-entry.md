# Phase 01 — Menu Entry

**Strategic spec:** [`../S0107_image-draw-overlay.md`](../S0107_image-draw-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-05-09
**Completed:** 2026-05-09
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Add `menu_draw_overlay` to the player overflow menu XML and wire its visibility and click handler in `PlayerControlsSetupManager`, keeping parity with the existing S0106 crop entries.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved. *(none for Phase 01)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | ≤ 290 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +8 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +5 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +5 lines |

> Landscape variant: `overflow_menu_player.xml` is a menu resource, not a layout — no `layout-land/` counterpart applies.

---

## Steps

### Step 1.1 — Add `menu_draw_overlay` item to overflow menu XML

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `overflow_menu_player.xml`, add a new `<item>` entry immediately after the `menu_compress_copy` block (line ~276). Use `android:id="@+id/menu_draw_overlay"`, `android:icon="@drawable/ic_draw_overlay"` (new drawable — mark as New in phase), `android:title="@string/menu_draw_overlay"`, `app:showAsAction="never"`. Add the comment `<!-- S0107: Draw overlay (IMAGE only) -->` above the item. Do not add the drawable file in this step — a placeholder reference is acceptable; the build will fail at resource link time but the XML change is independently committable once the drawable stub exists.

> Also create a minimal 24 dp vector drawable stub at `app_v2/src/main/res/drawable/ic_draw_overlay.xml` — a simple pencil path or copy the shape from `ic_edit_20.xml` renamed. This unblocks resource compilation.

**Verification:**

- `Grep` — `menu_draw_overlay` appears exactly once in `overflow_menu_player.xml`.
- `Glob` — `app_v2/src/main/res/drawable/ic_draw_overlay.xml` exists.
- `Grep` — `ic_draw_overlay` referenced in `overflow_menu_player.xml`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-09 — Verification 3/3 PASS. Files: `res/menu/overflow_menu_player.xml` (+7 lines), `res/drawable/ic_draw_overlay.xml` (new). Dev log recorded.

---

### Step 1.2 — Add string keys for Draw Overlay command (EN / RU / UK)

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 1.1

**Status:** `[x]` done

**Step Log:**
- 2026-05-09 — Verification 3/3 PASS. Files: `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (+7 lines each). Dev log recorded.

**Prompt for developer:**

> In each of the three `strings.xml` files add the following keys in the `<!-- S0107 -->` comment block (place immediately after the S0106 crop block):
>
> - `menu_draw_overlay` — EN: `"Draw"`, RU: `"Рисовать"`, UK: `"Малювати"`
> - `draw_overlay_toolbar_brush` — EN: `"Brush"`, RU: `"Кисть"`, UK: `"Пензель"`
> - `draw_overlay_toolbar_rect` — EN: `"Rectangle"`, RU: `"Прямоугольник"`, UK: `"Прямокутник"`
> - `draw_overlay_toolbar_eraser` — EN: `"Eraser"`, RU: `"Ластик"`, UK: `"Ластик"`
> - `draw_overlay_save_button` — EN: `"Save as new file"`, RU: `"Сохранить как новый файл"`, UK: `"Зберегти як новий файл"`
> - `draw_overlay_cancel_button` — EN: `"Cancel"` (reuse existing `cancel` key if present — skip if duplicate)
> - `draw_overlay_filename_hint` — EN: `"File name"`, RU: `"Имя файла"`, UK: `"Ім'я файлу"`
> - `draw_overlay_saved_to_downloads` — EN: `"Saved to Downloads: %s"`, RU: `"Сохранено в Загрузки: %s"`, UK: `"Збережено у Завантаження: %s"`

**Verification:**

- `Grep` — `menu_draw_overlay` present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (3 files).
- `Grep` — `draw_overlay_save_button` present in all 3 files.
- `Grep` — `draw_overlay_saved_to_downloads` present in all 3 files.

**Status:** `[ ]` not done

---

### Step 1.3 — Wire visibility and click for `menu_draw_overlay` in player menu handler

**Files:** *(read `PlayerControlsSetupManager.kt` first to identify the exact method — approx. 500 LOC; take a timestamped backup before edit)*
**Depends on:** Steps 1.1, 1.2

**Status:** `[x]` done

**Step Log:**
- 2026-05-09 — Verification 4/4 PASS. Files: `CommandPanelLayoutPlanner.kt` (+2 lines), `CommandPanelController.kt` (+2 lines), `PlayerCommandPanelCallbackImpl.kt` (+5 lines), `PlayerActivity.kt` (+4 lines). Dev log recorded.

**Prompt for developer:**

> Locate the method in `PlayerControlsSetupManager` (or the file that calls `menu.findItem(R.id.menu_crop)` / `menu.findItem(R.id.menu_crop_to_file)`) that sets visibility for image-specific overflow items. In the same block, add:
>
> ```kotlin
> menu.findItem(R.id.menu_draw_overlay)?.isVisible =
>     currentFile?.type == MediaType.IMAGE   // static images only; not GIF, not VIDEO
> ```
>
> In `onOptionsItemSelected` (or its equivalent delegate), add a branch:
>
> ```kotlin
> R.id.menu_draw_overlay -> {
>     Timber.d("S0107: menu_draw_overlay selected")
>     imageDrawOverlayManager.enterDrawMode()
>     true
> }
> ```
>
> `imageDrawOverlayManager` is not yet constructed — declare it as a `lateinit var` property in `PlayerActivity` (or in the appropriate manager initializer) and leave a `// TODO S0107 Phase 02` comment. The project must compile after this step with the lateinit property uninitialized (acceptable until Phase 02).

**Verification:**

- `Grep` — `menu_draw_overlay` appears in the Kotlin source file that handles `onOptionsItemSelected`.
- `Grep` — `Timber.d("S0107: menu_draw_overlay selected")` present in that file.
- `Grep` — `imageDrawOverlayManager` declared as `lateinit var` in `PlayerActivity.kt` or its initializer.
- `Grep` — `Log\.d\(` returns zero hits in every file touched by this step.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run (no new .kt files, but the modified manager file needs a scan if its LOC changed).

---

## Handoff Notes to Next Phase

- `menu_draw_overlay` item is in XML and the menu visibility gate is in place.
- `imageDrawOverlayManager.enterDrawMode()` call site exists; Phase 02 fulfils the implementation.
- String keys for all toolbar labels and dialogs are committed and verified across EN/RU/UK.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed beyond menu XML and string resources.
