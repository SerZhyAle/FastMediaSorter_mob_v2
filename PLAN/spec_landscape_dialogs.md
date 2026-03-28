# Specification: VIII.4 — Landscape-Adaptive Dialog Layouts

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 4 — Substantial (8–16h, notable risk)
**Roadmap entry:** Landscape-adaptive layouts for 25+ dialogs — Large regression surface

---

## 1. Problem Statement

All dialogs in the app are authored with `wrap_content` height and a portrait-first vertical layout. On a phone in landscape orientation, the usable screen height drops to ~360–400 dp (roughly half of portrait). As a result:

- **Tall dialogs are clipped** — content below the visible area is inaccessible, with no scroll affordance.
- **Action buttons disappear** — dialogs like `dialog_slideshow_settings` and `dialog_player_settings` have their Confirm/Cancel buttons rendered off-screen.
- **Wasted horizontal space** — landscape phones have ample horizontal room, but all dialogs remain single-column, leaving 40–60% of the width empty.
- **Player dialogs are worst** — the Player screen forces landscape on most videos; every dialog triggered during playback is broken.

Only 4 of 32 dialogs have a `layout-land/` variant today. The remaining 28 have no landscape handling.

---

## 2. Scope

### 2.1 Already have `layout-land/` variants (skip)

| File | Status |
|------|--------|
| `dialog_filter.xml` | Done — ScrollView wrapper |
| `dialog_rename.xml` | Done |
| `dialog_rename_multiple.xml` | Done |
| `dialog_file_info.xml` | Done |

### 2.2 Dialogs requiring landscape treatment

All paths are relative to `app_v2/src/main/res/layout/`.

| # | File | Category | Root issue |
|---|------|----------|------------|
| 1 | `dialog_access_password.xml` | B – Two-column | Input + button clipped |
| 2 | `dialog_color_picker.xml` | B – Two-column | Picker height overflow |
| 3 | `dialog_copy_to.xml` | C – Constrained list | Destination buttons overflow |
| 4 | `dialog_delete.xml` | D – Trivial | Minor vertical clip of button |
| 5 | `dialog_epub_reader_settings.xml` | B – Two-column | Settings rows clipped |
| 6 | `dialog_file_copy_progress.xml` | A – ScrollView | Progress + detail rows clipped |
| 7 | `dialog_file_operation_progress.xml` | A – ScrollView | Same as above |
| 8 | `dialog_filter_resource.xml` | A – ScrollView | Form fields clipped |
| 9 | `dialog_folder_browser.xml` | C – Constrained list | RecyclerView expands to full height |
| 10 | `dialog_folder_selection.xml` | C – Constrained list | Same |
| 11 | `dialog_gif_editor.xml` | B – Two-column | Editor controls clipped |
| 12 | `dialog_image_edit.xml` | B – Two-column | Edit controls clipped |
| 13 | `dialog_import_favorites_preview.xml` | C – Constrained list | Preview list + buttons clipped |
| 14 | `dialog_integration_test.xml` | D – Trivial | Debug-only; low priority |
| 15 | `dialog_log_view.xml` | C – Constrained list | Log area should fill height |
| 16 | `dialog_material_progress_horizontal.xml` | D – Trivial | Fits in landscape as-is |
| 17 | `dialog_material_progress_spinner.xml` | D – Trivial | Fits in landscape as-is |
| 18 | `dialog_network_delete_confirmation.xml` | D – Trivial | Short confirm dialog |
| 19 | `dialog_network_discovery.xml` | C – Constrained list | Discovery list clipped |
| 20 | `dialog_player_settings.xml` | B – Two-column | Complex; many rows clipped |
| 21 | `dialog_resource_picker.xml` | C – Constrained list | Resource list clipped |
| 22 | `dialog_resource_type_selector.xml` | D – Trivial | 3–4 items, short |
| 23 | `dialog_scheduled_log.xml` | C – Constrained list | Log list clipped |
| 24 | `dialog_scheduled_operation.xml` | B – Two-column | Form fields clipped |
| 25 | `dialog_scrollable_text.xml` | A – ScrollView | Already has ScrollView but dialog maxHeight unconstrained |
| 26 | `dialog_slideshow_settings.xml` | B – Two-column | Most content clipped |
| 27 | `dialog_sort.xml` | D – Trivial | Short radio-button list |
| 28 | `dialog_translation_settings.xml` | B – Two-column | Settings rows clipped |

**Total: 28 dialogs** (4 trivial, 6 ScrollView-only, 8 constrained-list, 10 two-column).

---

## 3. Solution Strategy

The fix uses **three complementary Android resource mechanisms**, chosen per dialog category. No Kotlin/Java code changes are required unless a dialog inflates layout programmatically with fixed dimensions.

### 3.1 Category A — ScrollView wrap (simplest)

**When:** Dialog content is inherently linear and single-column. Content exceeds landscape height but adding scroll is sufficient.

**Implementation:** Create `layout-land/<dialog>.xml` that is identical to the portrait version but:
1. Wraps the root in a `ScrollView` (or `NestedScrollView` if the dialog already contains one).
2. Sets `android:fillViewport="true"` on the ScrollView.
3. Adds `android:maxHeight="@dimen/dialog_landscape_max_height"` to the ScrollView (new dimen: `320dp`).

No structural changes to the inner view tree.

**Dialogs:** `dialog_file_copy_progress`, `dialog_file_operation_progress`, `dialog_filter_resource`, `dialog_scrollable_text`.

### 3.2 Category B — Two-column layout

**When:** Dialog has a tall single-column form that can be split left/right.

**Implementation:** Create `layout-land/<dialog>.xml` with a `LinearLayout` or `ConstraintLayout` root in **horizontal** orientation. Split the form sections roughly 50/50. Action buttons always stay at the bottom of the right column or in a common row below both columns.

Key guidelines:
- Left column: labels, primary inputs, read-only display fields.
- Right column: secondary inputs, checkboxes, action buttons.
- Both columns wrapped in `NestedScrollView` if the column content can still overflow on very small landscape screens (e.g., foldables in outer-screen mode).
- Minimum field target size: 48 dp height (unchanged from portrait).
- Text minimum: 12 sp (per VIII.1 requirement).

**Dialogs:** `dialog_access_password`, `dialog_color_picker`, `dialog_epub_reader_settings`, `dialog_gif_editor`, `dialog_image_edit`, `dialog_player_settings`, `dialog_scheduled_operation`, `dialog_slideshow_settings`, `dialog_translation_settings`.

### 3.3 Category C — Constrained list

**When:** Dialog has a `RecyclerView` or `ListView` that normally takes `wrap_content` and expands to fill all content.

**Implementation:** Create `layout-land/<dialog>.xml` where:
1. The list view gets `android:layout_height="0dp"` with `android:layout_weight="1"` (inside `LinearLayout`) or `app:layout_constraintHeight_max="@dimen/dialog_landscape_list_max_height"` (inside `ConstraintLayout`).
2. Action buttons are kept in a `LinearLayout` below the list, outside the scrollable area, so they are always visible.
3. Overall dialog height is capped at `@dimen/dialog_landscape_max_height` (`320dp`).

New dimen: `dialog_landscape_list_max_height = 240dp`.

**Dialogs:** `dialog_copy_to`, `dialog_folder_browser`, `dialog_folder_selection`, `dialog_import_favorites_preview`, `dialog_log_view`, `dialog_network_discovery`, `dialog_resource_picker`, `dialog_scheduled_log`.

### 3.4 Category D — Trivial (no layout-land needed)

**When:** Dialog is short enough to fit landscape without clipping (confirm dialogs, single-choice pickers with 3–4 items, progress spinners).

**Implementation:** Verify manually; add `android:maxHeight` to the root if even slight clipping is observed. Otherwise no new file is created.

**Dialogs:** `dialog_delete`, `dialog_integration_test`, `dialog_material_progress_horizontal`, `dialog_material_progress_spinner`, `dialog_network_delete_confirmation`, `dialog_resource_type_selector`, `dialog_sort`.

---

## 4. New Dimension Resources

Add to `app_v2/src/main/res/values/dimens.xml`:

```xml
<!-- Landscape dialog constraints -->
<dimen name="dialog_landscape_max_height">320dp</dimen>
<dimen name="dialog_landscape_list_max_height">240dp</dimen>
```

These should only be referenced from `layout-land/` files. Portrait dialogs remain `wrap_content`.

---

## 5. Dialog Size Enforcement (AlertDialog / BottomSheetDialog)

All dialogs are shown using `AlertDialog.Builder` or `MaterialAlertDialogBuilder` (wrapping a custom view), or as `BottomSheetDialogFragment`. The landscape max height is enforced by the XML layout alone — no window attribute overrides needed.

**Exception:** If any dialog builder calls `dialog.window?.setLayout(width, height)` with a hardcoded height, that call must be changed to `WindowManager.LayoutParams.WRAP_CONTENT` in landscape. Search for these before implementation:

```bash
grep -rn "setLayout" app_v2/src/main/java/ --include="*.kt"
```

---

## 6. Implementation Steps

### Phase 1: Infrastructure (0.5h)

| # | Task | File |
|---|------|------|
| 1 | Add two new landscape dimens | `res/values/dimens.xml` |
| 2 | Verify no `setLayout` hardcoded heights in dialog builders | — |

### Phase 2: Category D — Trivial verification (1h)

| # | Task |
|---|------|
| 3 | Launch each of the 7 trivial dialogs in emulator at landscape 360×640 dp |
| 4 | If any clip: add `android:maxHeight="@dimen/dialog_landscape_max_height"` to root |
| 5 | No new layout files expected |

### Phase 3: Category A — ScrollView wraps (2h)

| # | Task | Files created |
|---|------|---------------|
| 6 | `dialog_file_copy_progress` landscape | `layout-land/dialog_file_copy_progress.xml` |
| 7 | `dialog_file_operation_progress` landscape | `layout-land/dialog_file_operation_progress.xml` |
| 8 | `dialog_filter_resource` landscape | `layout-land/dialog_filter_resource.xml` |
| 9 | `dialog_scrollable_text` landscape | `layout-land/dialog_scrollable_text.xml` |

### Phase 4: Category C — Constrained list (3h)

| # | Task | Files created |
|---|------|---------------|
| 10 | `dialog_copy_to` landscape | `layout-land/dialog_copy_to.xml` |
| 11 | `dialog_folder_browser` landscape | `layout-land/dialog_folder_browser.xml` |
| 12 | `dialog_folder_selection` landscape | `layout-land/dialog_folder_selection.xml` |
| 13 | `dialog_import_favorites_preview` landscape | `layout-land/dialog_import_favorites_preview.xml` |
| 14 | `dialog_log_view` landscape | `layout-land/dialog_log_view.xml` |
| 15 | `dialog_network_discovery` landscape | `layout-land/dialog_network_discovery.xml` |
| 16 | `dialog_resource_picker` landscape | `layout-land/dialog_resource_picker.xml` |
| 17 | `dialog_scheduled_log` landscape | `layout-land/dialog_scheduled_log.xml` |

### Phase 5: Category B — Two-column (6h)

Order by user-visible priority (most frequently triggered first):

| # | Task | Files created | Notes |
|---|------|---------------|-------|
| 18 | `dialog_slideshow_settings` landscape | `layout-land/dialog_slideshow_settings.xml` | Left: interval + music; Right: toggles + close |
| 19 | `dialog_player_settings` landscape | `layout-land/dialog_player_settings.xml` | Left: playback; Right: display |
| 20 | `dialog_translation_settings` landscape | `layout-land/dialog_translation_settings.xml` | Left: language selectors; Right: options |
| 21 | `dialog_epub_reader_settings` landscape | `layout-land/dialog_epub_reader_settings.xml` | Left: font/size; Right: theme/margin |
| 22 | `dialog_image_edit` landscape | `layout-land/dialog_image_edit.xml` | Left: preview; Right: controls |
| 23 | `dialog_gif_editor` landscape | `layout-land/dialog_gif_editor.xml` | Left: preview; Right: controls |
| 24 | `dialog_color_picker` landscape | `layout-land/dialog_color_picker.xml` | Left: hue slider; Right: preview + hex |
| 25 | `dialog_access_password` landscape | `layout-land/dialog_access_password.xml` | Left: password field; Right: confirm + action |
| 26 | `dialog_scheduled_operation` landscape | `layout-land/dialog_scheduled_operation.xml` | Left: name/type; Right: schedule/action |

### Phase 6: Regression testing (2h)

See §8.

---

## 7. Non-Goals

- Tablet / large-screen two-pane layouts — this is covered by roadmap item II.3.
- Foldable outer-screen optimization — out of scope.
- Migrating dialogs to `BottomSheetDialogFragment` — architectural change, separate concern.
- Converting any layout to Jetpack Compose — covered by II.2.
- Changing dialog content, logic, or adding new fields.

---

## 8. Testing Matrix

For each modified dialog, test the following configurations:

| Device config | Test method |
|---------------|-------------|
| Phone landscape, 360×640 dp (Pixel 4a emulator) | AVD at API 30 |
| Phone landscape, small screen 320×533 dp (legacy) | AVD at API 26 (minSdk) |
| Phone portrait (regression) | Same AVD |
| Tablet portrait + landscape (regression) | Pixel Tablet AVD |

**Per-dialog checklist:**
- [ ] All content (fields, labels, buttons) visible without scrolling in typical use
- [ ] If content overflows: scroll works and action buttons remain pinned (not inside scroll area)
- [ ] Portrait layout unchanged — verify screenshot of portrait config
- [ ] No layout overlap or clipped text
- [ ] Touch targets ≥ 48 dp height in landscape
- [ ] Back-button / Cancel dismisses dialog correctly
- [ ] Dialog reappears correctly after device rotation mid-dialog (savedInstanceState)

**Priority order for testing:** Player-triggered dialogs first (`slideshow_settings`, `player_settings`, `image_edit`, `gif_editor`), then Browse-triggered (`folder_browser`, `copy_to`, `filter_resource`), then Settings-triggered (remaining).

---

## 9. Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| Landscape layout file inflated where portrait is expected (wrong qualifier directory) | High | Android resource qualifier system handles this automatically; no code change needed |
| Two-column layout breaks on dialogs with dynamic content (content added at runtime to a container) | Medium | Read the Kotlin dialog helper before creating the layout-land file; identify dynamically-populated containers and keep them in a single full-width column |
| `setLayout` window calls override XML height on some dialogs | Medium | Audit with grep before implementation (Step 2); fix any found instances |
| Portrait regression from accidentally editing portrait files | Low | All landscape files created in `layout-land/`; never edit files in `layout/` during this work |
| Dialog height dimen too small on minSdk 26 devices with nav bar | Low | Test on API 26 AVD; adjust `dialog_landscape_max_height` if needed |

---

## 10. Acceptance Criteria

- [ ] 0 dialogs with content clipped off-screen in landscape on a 360×640 dp device (Pixel 4a emulator)
- [ ] All action buttons (OK, Cancel, Apply, Close) visible without scrolling in all dialogs on that device
- [ ] Portrait layout of all 28 dialogs is pixel-identical to pre-change baseline (screenshot comparison)
- [ ] Dialogs that show a `RecyclerView` scroll their list correctly in both landscape and portrait
- [ ] Device rotation mid-dialog does not crash or lose entered data
- [ ] Lint check passes: `.\gradlew.bat lintStandardDebug`
- [ ] `CHANGELOG.md` updated after completion
