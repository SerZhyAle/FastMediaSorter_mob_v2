# Phase 04 - ListSelectionDialog

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce the generic `ListSelectionDialog<T>` with a RecyclerView adapter rendering XML item views styled by the Button Taxonomy (ADR-3), and migrate `ResourcePickerDialog` + `DestinationPickerDialog` off runtime-built colored buttons.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_list_selection.xml` | New | ≤ 50 |
| `app_v2/src/main/res/layout/dialog_list_selection.xml` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ListSelectionDialog.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ListSelectionAdapter.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt` | Modified | ≤ 200 |

> `FileOperationDestinationDialog.kt` is NOT migrated here - it stays color-aware/grid-based (strategic §2.2 non-goal); Phase 06 only records whether it can later reuse the adapter contract.

---

## Steps

### Step 04.1 - Author item + dialog layouts

**Files:** `item_list_selection.xml`, `dialog_list_selection.xml`
**Depends on:** - start of phase
**Landscape:** dialog/item layouts size to content - no `layout-land/` counterpart required (note in step).

**Prompt for developer:**

> `item_list_selection.xml`: a single selectable row (optional leading icon `@+id/item_icon` + label `@+id/item_label`) using `?attr/selectableItemBackground` and a project button-taxonomy text style; no hardcoded HEX. `dialog_list_selection.xml`: a title `TextView` + `RecyclerView` (`@+id/list_selection_recycler`).

**Verification:**

- `Glob` - both files exist.
- `Grep` - `@+id/list_selection_recycler` and `@+id/item_label` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits across both files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `item_list_selection.xml` (`MaterialButton` id `item_label`, `Widget.FastMediaSorter.Button.Outlined`, 0 HEX) and `dialog_list_selection.xml` (title + `@+id/list_selection_recycler` + Clear/Cancel). Self-sizing dialog - no land variant needed.

---

### Step 04.2 - Implement `ListSelectionAdapter`

**Files:** `ListSelectionAdapter.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> `class ListSelectionAdapter<T>(private val formatter: ItemFormatter<T>, private val onClick: (T) -> Unit) : RecyclerView.Adapter<..>` inflating `item_list_selection`. Declare `interface ItemFormatter<T> { fun getDisplayName(item: T): String; fun getIcon(item: T): Drawable? }`. No runtime `setBackgroundColor` / `setTextColor` / `Color.*`. Timber only.

**Verification:**

- `Grep` - `class ListSelectionAdapter` once; `interface ItemFormatter` present.
- `Grep -nE "Color\.(WHITE|LTGRAY|BLACK)|setBackgroundColor|setTextColor"` zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS. Created `ListSelectionAdapter<T>` (RecyclerView, inflates `item_list_selection`, `ItemFormatter<T>` with `getDisplayName`/`getIcon`, selected item shows `ic_check`). No runtime color setters.

---

### Step 04.3 - Implement `ListSelectionDialog<T>`

**Files:** `ListSelectionDialog.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> `class ListSelectionDialog<T> private constructor(private val config: SelectionConfig<T>) : DialogFragment()` inflating `dialog_list_selection`, wiring `ListSelectionAdapter<T>`. Provide a `SelectionConfig<T>` data holder (title, items, formatter, onSelected) and a builder/`newInstance`-style factory. Render via the adapter only - no programmatic button construction. Timber only.

**Verification:**

- `Grep` - `class ListSelectionDialog` once; `ListSelectionAdapter` referenced.
- `Grep -nE "AppCompatButton\(|MaterialButton\("` zero hits (no runtime button instantiation).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS. Created `ListSelectionDialog<T>` (`Dialog`, patched from plan's DialogFragment to match existing call-site pattern) + `ListSelectionConfig<T>` (title, async loader, formatter, isSelected, allowClear, empty/error message res, onSelected). Renders via `ListSelectionAdapter` only.

---

### Step 04.4 - Migrate `ResourcePickerDialog` + `DestinationPickerDialog`

**Files:** `ResourcePickerDialog.kt`, `DestinationPickerDialog.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Re-implement both dialogs on top of `ListSelectionDialog<T>` (or replace their runtime `AppCompatButton` loops with a `ListSelectionAdapter`), supplying an `ItemFormatter` for the resource / destination type. Remove all hardcoded `Color.LTGRAY` / `Color.WHITE` / `R.color.blue_500` and manual padding/text-size styling surveyed in strategic §1.1 item 6. Preserve callers' public entry points and selection callbacks.

**Verification:**

- `Grep -nE "Color\.(WHITE|LTGRAY)|R\.color\.blue_500|setBackgroundColor|setTextColor"` over `ResourcePickerDialog.kt` + `DestinationPickerDialog.kt` returns zero hits.
- `Grep` - `ListSelection` referenced in both files.
- `/build` standard debug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS (`a.ps1 fc`). Reduced `ResourcePickerDialog` + `DestinationPickerDialog` to thin `ListSelectionDialog<MediaResource>` subclasses (call sites unchanged; ~145 -> ~40 LOC each). All hardcoded colors (`Color.WHITE`/`LTGRAY`/`R.color.blue_500`/`#FF4CAF50`) + runtime `AppCompatButton` loops removed. Deleted orphaned `dialog_resource_picker.xml` (+land) - dead-weight hygiene; `DialogResourcePickerBinding` no longer referenced.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] `Grep "Color.WHITE|Color.LTGRAY|setBackgroundColor|setTextColor"` over `ui/dialog/ResourcePickerDialog.kt` + `DestinationPickerDialog.kt` = zero.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Generic picker + adapter contract exist; Phase 06 audit decides whether `FileOperationDestinationDialog` adopts the same `ItemFormatter` contract while keeping its color-chip visuals.

---

## Rollback Plan

Revert phase commit(s) - new files are additive; the two migrated dialogs revert to their prior runtime-button implementation.
