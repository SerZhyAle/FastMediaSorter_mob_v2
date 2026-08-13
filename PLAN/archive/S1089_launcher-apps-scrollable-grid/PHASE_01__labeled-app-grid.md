# Phase 01 - Labeled App Grid

**Strategic spec:** [`../S1089_launcher-apps-scrollable-grid.md`](../S1089_launcher-apps-scrollable-grid.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 01.1-01.3 grep-verified (new item_launcher_app_grid_cell.xml with appIcon/appLabel + no hex; LauncherAppGridAdapter with AppItem, DiffUtil excludes icon; fragment wired to LauncherAppGridAdapter, taskbar imports removed, dimen 300dp, tools:listitem updated). `.\a.ps1 fc` BUILD SUCCESSFUL. Audit: Layer 1/3 clean, no P0/P1.

---

## Objective

Render the Start-menu "All apps" grid as icon-over-label cells in its own layout + adapter, tall enough to show about three rows before scrolling, without touching the shared taskbar-strip item.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/item_launcher_app_grid_cell.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAppGridAdapter.kt` | New | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml` | Modified | - |
| `app_v2/src/launcherEnabled/res/values/dimens.xml` | Modified | - |

> `fragment_launcher_start_menu.xml` has no `res/layout-land/` counterpart (Start-menu sheet is orientation-neutral, matching item_launcher_cell_shortcut.xml); no landscape edit required. New item layout is orientation-neutral (square-ish cell), so no land variant.

---

## Steps

### Step 01.1 - New labeled app-grid cell layout

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_app_grid_cell.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `item_launcher_app_grid_cell.xml` mirroring `item_launcher_cell_shortcut.xml` but simpler: a `FocusMaterialCardView` root (`layout_width=match_parent`, `layout_height=wrap_content`, 4dp margin, clickable+focusable, `?attr/colorSurface` background, 16dp corner, 1dp `?attr/colorOutline` stroke, ripple `?attr/colorControlHighlight`) wrapping a vertical center-gravity LinearLayout with a 40dp `@+id/appIcon` ImageView (`importantForAccessibility=no`) over a `@+id/appLabel` TextView (`match_parent`, `ellipsize=end`, `maxLines=1`, `gravity=center`, `textColor=?attr/colorOnSurface`, `textSize=12sp`, 6dp top margin). No hardcoded hex colors (Rule 20). No mode badge.

**Verification:**

- `Glob` - `item_launcher_app_grid_cell.xml` exists.
- `Grep` - `@+id/appIcon` and `@+id/appLabel` both present.
- `Grep` (negative) - no `="#` hardcoded color in the file.

**Status:** `[x]` done

---

### Step 01.2 - New LauncherAppGridAdapter

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAppGridAdapter.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `LauncherAppGridAdapter` in package `com.sza.fastmediasorter.ui.launcher.menu`: a `ListAdapter<LauncherAppGridAdapter.AppItem, VH>` inflating `ItemLauncherAppGridCellBinding`. Declare `data class AppItem(val id: String, val label: String, val icon: Drawable)`. Constructor takes `onAppClick: (AppItem) -> Unit`. In `onBindViewHolder` set `appIcon.setImageDrawable(item.icon)`, `appLabel.text = item.label`, `root.contentDescription = item.label`, and `root.setOnClickListener { onAppClick(item) }`. DiffUtil: `areItemsTheSame` compares `id`; `areContentsTheSame` compares `label` only (exclude the Drawable - PackageManager returns a fresh instance per call and Drawable uses identity equality, which would rebind every time - same reasoning as LauncherTaskbarIconAdapter). Expose `fun submitApps(list: List<AppItem>) = submitList(list)`.

**Verification:**

- `Glob` - `LauncherAppGridAdapter.kt` exists.
- `Grep` - `class LauncherAppGridAdapter` and `data class AppItem` present.
- `Grep` - `areContentsTheSame` compares `label` and does NOT reference `icon`.

**Status:** `[x]` done

---

### Step 01.3 - Wire the grid into the Start menu and give it ~3 rows

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`, `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml`, `app_v2/src/launcherEnabled/res/values/dimens.xml`

**Depends on:** Step 01.2

**Prompt for developer:**

> In `LauncherStartMenuFragment`: replace the `appsAdapter` field with `LauncherAppGridAdapter(onAppClick = { app -> viewModel.run(LauncherCellCommand.App(app.id)); dismiss() })`. In `toggleAllApps`, map `queryLaunchableApps()` results to `LauncherAppGridAdapter.AppItem(id = it.packageName, label = it.label, icon = it.icon)` and call `appsAdapter.submitApps(...)`. Keep the `GridLayoutManager(requireContext(), ALL_APPS_COLUMNS)` and the `allAppsJob` guard. Remove the now-unused `LauncherTaskbarIcon` / `LauncherTaskbarIconAdapter` imports (dead-weight, Rule 21). In `dimens.xml` raise `launcher_start_menu_apps_height` from 240dp to 300dp so ~3 labeled rows show before scrolling. In `fragment_launcher_start_menu.xml` update `tools:listitem` on `rvAllApps` to `@layout/item_launcher_app_grid_cell`.

**Verification:**

- `Grep` - `LauncherAppGridAdapter(` constructed in the fragment; `submitApps(` called.
- `Grep` (negative) - no `LauncherTaskbarIconAdapter` / `LauncherTaskbarIcon` reference left in `LauncherStartMenuFragment.kt`.
- `Grep` - `launcher_start_menu_apps_height">300dp` in dimens.xml.
- `Grep` - `tools:listitem="@layout/item_launcher_app_grid_cell"` in the fragment layout.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new adapter class).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (Layer 3 adapter/Drawable ownership; confirm DiffUtil excludes the Drawable).

---

## Handoff Notes to Next Phase

The all-apps grid now shows labeled cells; the taskbar strips are untouched. Phase 02 records + regenerates.

---

## Rollback Plan

Revert the phase commit(s) - two new files + a fragment/adapter swap; no persistence or schema impact.
