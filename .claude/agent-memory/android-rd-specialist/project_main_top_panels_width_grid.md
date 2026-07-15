---
name: main-top-panels-width-grid
description: Main-screen top 4 rows (command bar, programs, streams, resource tabs) width/alignment architecture - S1037+S1049 series
type: project
---

`MainActivity`'s top area stacks 4 independent rows above the resource list: command bar (`layoutControlButtons`), programs panel, streams panel, resource-type tabs (`tabResourceTypes`). S1037 (leading-anchor, `main_top_panel_leading_anchor`=56dp) then S1049 (per-item width grid, `main_panel_item_min_width`=48dp) are a connected pair fixing why these rows don't visually align - check both before touching this area again.

**Why:** before S1037/S1049, each row had its own width strategy: command bar had no min-width floor (icon-hugging), programs panel used a 48dp item module (the reference/best-looking one per owner), streams panel used a *different* 56dp module for its channel chips (silent mismatch, root cause of "second button onward doesn't line up"), resource tabs used Material `TabLayout` default fixed/fill or scrollable by `screenWidthDp` bucket (no relation to the icon-button grid at all).

**How to apply:**
- Shared grid dimens live in `app_v2/src/main/res/values/dimens_main_panels.xml`: `main_panel_item_min_width` (48dp, the reference unit), `main_top_panel_leading_anchor` (56dp, S1037's shared start X), `main_panel_tab_min_width` (96dp = 2x item unit, tabs only, S1049).
- `activity_main.xml` exists in **three** variants, not two: `layout/` (phone portrait, compact icon-only command bar), `layout-land/` (label-mode command bar), `layout-w600dp/` (also label-mode - **wins over `layout-land` by Android's own qualifier precedence** when a device is both wide and landscape, so `w600dp` is the one that actually renders on most landscape phones/tablets, not `-land`). Any edit scoped to "portrait only" must exclude both land and w600dp copies.
- Portrait-only *behavioural* branches (not just static XML) are done via orientation/width-qualified `<bool>` resources read at runtime, e.g. `main_streams_panel_show_labels`, `main_resource_tabs_fixed_grid` - declared in `values/bools.xml` (portrait default) + `values-land/bools.xml`, and (S1049 lesson) **must also get a `values-w600dp/bools.xml` override** or the w600dp bucket silently inherits the portrait default while still inflating the w600dp layout, causing a real behavioural mismatch. `values-w600dp/bools.xml` already existed (unrelated bools) before S1049 - always check file existence before assuming "create new file".
- `MainResourceTabsManager.createTabs()` forces per-tab width via `app:tabMinWidth`/`app:tabMaxWidth` XML attributes (not a custom tab view) - simplest reliable way to fix Material `TabLayout` tab width regardless of `tabMode`.
