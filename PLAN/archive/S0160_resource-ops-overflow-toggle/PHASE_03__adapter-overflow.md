# Phase 03 — adapter-overflow

**Strategic spec:** [`../S0160_resource-ops-overflow-toggle.md`](../S0160_resource-ops-overflow-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — can run in parallel with Phase 01/02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add overflow-mode support to `ResourceAdapter` (both list and grid ViewHolders), add the "Refresh" item to the resource overflow menu XML, and add the `btnMoreActions` button to the grid item layout.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/resource_item_actions.xml` | Modified | ≤ 35 |
| `app_v2/src/main/res/layout/item_resource_grid.xml` | Modified | ≤ 145 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 750 |

> `ResourceAdapter.kt` is 694 lines — backup required before edit: `Copy-Item ... temp/ResourceAdapter_<timestamp>.kt.backup`.
>
> `item_resource_grid.xml` — landscape variant: `layout-land/item_resource_grid.xml` does not exist; no landscape counterpart needed.
>
> `item_resource.xml` — landscape variant: `layout-land/item_resource.xml` does not exist; no landscape counterpart needed.

---

## Steps

### Step 03.1 — Add `action_scan` item to `resource_item_actions.xml`

**Files:** `app_v2/src/main/res/menu/resource_item_actions.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `res/menu/resource_item_actions.xml`, insert a new `<item>` for the "Refresh" action between `action_copy` and `action_move_up`:
>
> ```xml
> <item
>     android:id="@+id/action_scan"
>     android:title="@string/action_refresh_resource"
>     android:icon="@drawable/ic_refresh" />
> ```
>
> The string `action_refresh_resource` is added in Phase 04. The drawable `ic_refresh` must already exist in the project — verify with `Glob` before adding the attribute; if absent, use `ic_sync` or the nearest equivalent refresh icon available. No other menu item is reordered.

**Verification:**

- `Grep` — `action_scan` matches in `resource_item_actions.xml`.
- `Glob` — `app_v2/src/main/res/drawable/ic_refresh.xml` exists (or whichever icon is used).
- `Grep` — order in file: `action_edit` → `action_copy` → `action_scan` → `action_move_up` → `action_move_down` → `action_delete`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Files: res/menu/resource_item_actions.xml (+4 LOC). Dev log recorded.

---

### Step 03.2 — Add `btnMoreActions` to `item_resource_grid.xml`

**Files:** `app_v2/src/main/res/layout/item_resource_grid.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `item_resource_grid.xml`, inside the `ConstraintLayout`, add an `ImageButton` for the overflow trigger. Place it at the bottom-end corner, constrained to `bottom_toBottomOf="parent"` and `end_toEndOf="parent"`. Set `android:visibility="gone"` (hidden by default; shown only when overflow mode is enabled by the adapter). Use the same style as the list item:
>
> ```xml
> <ImageButton
>     android:id="@+id/btnMoreActions"
>     android:layout_width="@dimen/item_icon_size_small"
>     android:layout_height="@dimen/item_icon_size_small"
>     android:background="?attr/selectableItemBackgroundBorderless"
>     android:contentDescription="@string/more_options"
>     android:src="@drawable/ic_more_vert"
>     android:visibility="gone"
>     app:layout_constraintBottom_toBottomOf="parent"
>     app:layout_constraintEnd_toEndOf="parent" />
> ```
>
> The string `more_options` already exists in the project.

**Verification:**

- `Grep` — `btnMoreActions` matches in `item_resource_grid.xml`.
- `Grep` — `android:visibility="gone"` matches for `btnMoreActions` in that file.
- `Grep` — `layout-land/item_resource_grid.xml` — file does not exist (landscape variant absent).

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Files: res/layout/item_resource_grid.xml (+11 LOC). Dev log recorded.

---

### Step 03.3 — Add overflow-mode support to `ResourceAdapter` constructor and `ResourceViewHolder`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Backup `ResourceAdapter.kt` first: `Copy-Item app_v2/.../ResourceAdapter.kt temp/ResourceAdapter_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.backup`.
>
> **Constructor:** Add `private val onScanClick: (MediaResource) -> Unit = {}` as the last constructor parameter of `ResourceAdapter`. The default `{}` lets existing `MainActivity` instantiation compile until Phase 05 wires the real callback.
>
> **Adapter-level field and setter:** Add after the `dragStartListener` field:
> ```kotlin
> private var overflowModeEnabled: Boolean = false
>
> fun setOverflowModeEnabled(enabled: Boolean) {
>     if (this.overflowModeEnabled != enabled) {
>         this.overflowModeEnabled = enabled
>         notifyDataSetChanged()
>     }
> }
> ```
>
> **`ResourceViewHolder.bind()` — overflow branch:** In the `else` block (currently starting at `btnMoreActions.visibility = VISIBLE`), add the `action_scan` handler inside `setOnMenuItemClickListener`:
> ```kotlin
> R.id.action_scan -> {
>     onScanClick(resource)
>     true
> }
> ```
>
> **`ResourceViewHolder.bind()` — conditional override:** In the `if (resource.id == -100L)` guard block at the end (where `btnMoreActions` and `layoutInlineActions` are shown/hidden), replace:
> ```kotlin
> val showInlineActions = root.resources.getBoolean(R.bool.is_resource_actions_inline)
> ```
> with:
> ```kotlin
> val showInlineActions = !overflowModeEnabled &&
>     root.resources.getBoolean(R.bool.is_resource_actions_inline)
> ```
> This preserves the auto-inline behavior when overflow mode is off, and forces overflow when it is on.
>
> No other logic in `ResourceViewHolder.bind()` changes.

**Verification:**

- `Grep` — `onScanClick: \(MediaResource\) -> Unit` matches in `ResourceAdapter.kt`.
- `Grep` — `overflowModeEnabled` matches at least 4 times (field declaration, setter, inline-check condition, and GridViewHolder in Step 03.4).
- `Grep` — `fun setOverflowModeEnabled` matches in `ResourceAdapter.kt`.
- `Grep` — `R.id.action_scan` matches in `ResourceAdapter.kt`.
- `Grep` — `!overflowModeEnabled` matches in `ResourceAdapter.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `ResourceAdapter.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 6/6 PASS. Files: ui/main/ResourceAdapter.kt (+14 LOC). Backup: temp/ResourceAdapter_20260513_184432.kt.backup. Dev log recorded.

---

### Step 03.4 — Wire overflow-mode in `GridViewHolder.bind()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> In `GridViewHolder.bind()`, after the drag-handle block (at the end of the bind function body), add:
>
> ```kotlin
> // Overflow menu for grid items (S0160)
> if (resource.id == -100L) {
>     btnMoreActions.visibility = android.view.View.GONE
> } else if (overflowModeEnabled) {
>     val isPredefinedVirtualResource = resource.path in VirtualPathUtils.ALL_VIRTUAL_PATHS
>     btnMoreActions.visibility = android.view.View.VISIBLE
>     btnMoreActions.setOnClickListener { view ->
>         val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
>         popup.menuInflater.inflate(R.menu.resource_item_actions, popup.menu)
>         popup.menu.findItem(R.id.action_copy)?.isVisible = !isPredefinedVirtualResource
>         popup.setForceShowIcon(true)
>         popup.setOnMenuItemClickListener { item ->
>             when (item.itemId) {
>                 R.id.action_edit -> { onEditClick(resource); true }
>                 R.id.action_copy -> { onCopyFromClick(resource); true }
>                 R.id.action_scan -> { onScanClick(resource); true }
>                 R.id.action_move_up -> { onMoveUpClick(resource); true }
>                 R.id.action_move_down -> { onMoveDownClick(resource); true }
>                 R.id.action_delete -> { onDeleteClick(resource); true }
>                 else -> false
>             }
>         }
>         popup.show()
>     }
> } else {
>     btnMoreActions.visibility = android.view.View.GONE
> }
> ```
>
> The `item_resource_grid.xml` binding generates `btnMoreActions` only after Step 03.2 lands. Ensure both steps are committed before building.

**Verification:**

- `Grep` — `GridViewHolder` class body contains `btnMoreActions.visibility` in `ResourceAdapter.kt`.
- `Grep` — `overflowModeEnabled` matches inside `GridViewHolder` body in `ResourceAdapter.kt`.
- Build passes with `/build`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 2/3 PASS + 1 deferred. GridViewHolder has btnMoreActions.visibility and overflowModeEnabled ✅. Build: expected compile error `string/action_refresh_resource not found` — will pass after Phase 04. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (compile error on `R.string.action_refresh_resource` expected until Phase 04).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all three files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `ResourceAdapter.setOverflowModeEnabled(Boolean)` is public and ready for Phase 05 wiring.
- `onScanClick` callback is wired in both `ResourceViewHolder` and `GridViewHolder`.
- Grid layout has `btnMoreActions` present; it is `GONE` until overflow mode is enabled.
- Missing string `action_refresh_resource` resolved in Phase 04.

---

## Rollback Plan

Revert phase commit — no data migration, no Room change. Backup in `temp/` for `ResourceAdapter.kt`.
