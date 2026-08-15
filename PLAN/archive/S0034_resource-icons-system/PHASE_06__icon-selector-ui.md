# Phase 06 — Icon Selector UI

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 07
**Steps done:** 6 / 6
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Add a tappable icon button to the right edge of the add-resource and edit-resource screen toolbars. Tapping opens a bottom-sheet picker grouped by themed set, with the current icon pre-selected. Picking commits to the in-memory resource model immediately and dismisses the sheet.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] Phase 04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/picker/IconPickerBottomSheet.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/picker/IconPickerAdapter.kt` | New | ≤ 200 |
| `app_v2/src/main/res/layout/bottom_sheet_icon_picker.xml` | New | — |
| `app_v2/src/main/res/layout/item_icon_picker.xml` | New | — |
| `app_v2/src/main/res/layout/toolbar_icon_action.xml` | New | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 06.1 — Toolbar icon button layout

**Files:** `app_v2/src/main/res/layout/toolbar_icon_action.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Define a 40×40dp `ImageButton` with id `btnPickIcon`, transparent background using `?attr/selectableItemBackgroundBorderless`, content description bound to `@string/cd_pick_resource_icon`. The button is included via `<include layout="@layout/toolbar_icon_action" .. />` from the add-resource and edit-resource screen layouts at the right edge of the toolbar / form header.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout/toolbar_icon_action.xml` exists.
- `Grep` — `android:id="@\+id/btnPickIcon"` matches once.
- `Grep` — `@string/cd_pick_resource_icon` matches once in this file.

**Status:** `[x]` done

---

### Step 06.2 — Bottom-sheet layouts

**Files:**
`app_v2/src/main/res/layout/bottom_sheet_icon_picker.xml`,
`app_v2/src/main/res/layout/item_icon_picker.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> `bottom_sheet_icon_picker.xml`: vertical container with a header `TextView` showing the current set name (`@string/icon_picker_title`), a horizontal `TabLayout` (`tabsIconSet`) for the five sets MUSIC/VIDEO/IMAGE/DOCS/OTHER, and a `RecyclerView` (`rvIcons`, `GridLayoutManager` columns=5). `item_icon_picker.xml`: square 56dp `FrameLayout` containing a centred 40dp `ImageView` (`ivIcon`) and a selection ring `View` (`vSelected`, `android:visibility="gone"`). Both files in `layout/` only — no `layout-land` mirrors required (the sheet rotates to fit screen).

**Verification:**

- `Glob` — both layout files exist.
- `Grep` — `tabsIconSet` matches once.
- `Grep` — `rvIcons` matches once.
- `Grep` — `ivIcon` matches once in `item_icon_picker.xml`.

**Status:** `[x]` done

---

### Step 06.3 — `IconPickerAdapter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/picker/IconPickerAdapter.kt`
**Depends on:** Phase 03

**Prompt for developer:**

> `RecyclerView.Adapter<IconViewHolder>` driven by `List<String>` (icon ids). Constructor takes `currentIconId: String?` for initial selection and a `onPick: (String) -> Unit` lambda. `onBindViewHolder` resolves the drawable via `ResourceIconRegistry.resolveDrawable(id)` and toggles the selection ring view. Use `submitList` semantics via a private `var items: List<String>` + manual `notifyDataSetChanged` on `setItems(set: ResourceIconSet)` — list is fully replaced when the user switches tabs.

**Verification:**

- `Grep` — `class IconPickerAdapter` matches once.
- `Grep` — `fun setItems\(set: ResourceIconSet\)` matches once.
- `Grep` — `ResourceIconRegistry\.resolveDrawable` matches at least once in `IconPickerAdapter.kt`.

**Status:** `[x]` done

---

### Step 06.4 — `IconPickerBottomSheet`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/picker/IconPickerBottomSheet.kt`
**Depends on:** Steps 06.2, 06.3

**Prompt for developer:**

> Subclass `BottomSheetDialogFragment`. Public companion factory:
>
> ```kotlin
> fun newInstance(currentIconId: String?, defaultSet: ResourceIconSet): IconPickerBottomSheet
> ```
>
> Inflate `bottom_sheet_icon_picker.xml`. On view-created: build tabs from `ResourceIconSet.values()`; preselect `defaultSet`; populate the adapter with `ResourceIconRegistry.idsFor(currentSet)`. On tab change, swap the adapter list. On icon click, set `Fragment.setFragmentResult(KEY, bundleOf(RESULT_ICON_ID to id))` and `dismiss()`. Use Timber. Single-line KDoc only.

**Verification:**

- `Grep` — `class IconPickerBottomSheet : BottomSheetDialogFragment` matches once.
- `Grep` — `fun newInstance\(currentIconId: String\?, defaultSet: ResourceIconSet\)` matches once.
- `Grep` — `setFragmentResult` matches at least once in `IconPickerBottomSheet.kt`.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 06.5 — Wire into Add Resource and Resource Editor

**Files:**
`app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`,
`app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`
**Depends on:** Steps 06.1, 06.4

**Prompt for developer:**

> In both screens: after the existing toolbar / header is inflated, locate the right-edge container and `include` `toolbar_icon_action`. Wire `btnPickIcon`:
>
> 1. Display the current preview via `ResourceIconRegistry.resolveDrawable(currentIconId)` falling back to `ResourceIconDefaults.firstIdFor(set)` when null.
> 2. On click, call `IconPickerBottomSheet.newInstance(currentIconId, defaultSet).show(supportFragmentManager / childFragmentManager, "icon_picker")`.
> 3. Listen via `setFragmentResultListener(IconPickerBottomSheet.KEY)` and update both the in-memory model AND the preview button drawable. In `ResourceEditorViewModel`, flip `userPickedIconThisSession = true` (Phase 05.3) so subsequent profile changes do not overwrite the user's choice.
> 4. Pass the selected icon id into `AddResourceFinalizer` as `userPickedIconId` (Phase 05.2).

**Verification:**

- `Grep` — `IconPickerBottomSheet\.newInstance` matches at least twice (one per screen).
- `Grep` — `btnPickIcon` matches at least four times across both files (find + click listener wire + result listener update + preview update).
- `Grep` — `userPickedIconThisSession = true` matches in `ResourceEditorFragment.kt` or its ViewModel.

**Status:** `[x]` done

---

### Step 06.6 — Trilingual strings

**Files:**
`app_v2/src/main/res/values/strings.xml`,
`app_v2/src/main/res/values-ru/strings.xml`,
`app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Steps 06.1, 06.4

**Prompt for developer:**

> Add the same three keys to all three locale files:
>
> | key | EN | RU | UK |
> | --- | --- | --- | --- |
> | `cd_pick_resource_icon` | Pick resource icon | Выбрать иконку ресурса | Вибрати іконку ресурсу |
> | `icon_picker_title` | Choose an icon | Выбор иконки | Вибір іконки |
> | `icon_set_music` / `_video` / `_image` / `_docs` / `_other` | Music/Video/Image/Documents/Other | Музыка/Видео/Изображения/Документы/Другое | Музика/Відео/Зображення/Документи/Інше |
>
> Use `..` not `...` and always `ё`/`Ё` in Russian where required.

**Verification:**

- `Grep` — `cd_pick_resource_icon` matches exactly three times across the three `strings.xml` files (one per locale).
- `Grep` — `icon_picker_title` matches exactly three times.
- `Grep` — `icon_set_music` matches exactly three times.
- `Grep` — `icon_set_other` matches exactly three times.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — three new public classes (sheet, adapter, plus modified screens).

---

## Handoff Notes to Next Phase

The user can now pick icons but the main screen still shows the old static drawables. Phase 07 wires the composer into the resource list/grid so the new icons become user-visible.

---

## Rollback Plan

Revert phase commit(s). The selector is opt-in via the toolbar button — disabling the include in Add/Edit layouts neutralises the feature without removing the selector code.
