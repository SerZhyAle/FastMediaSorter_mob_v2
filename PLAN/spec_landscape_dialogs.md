# Specification: VIII.4 — Landscape-Adaptive Dialog Layouts

**Status:** Draft
**Date:** 2026-03-29
**Tier:** 4 — Substantial (8–16h, notable risk)
**Roadmap entry:** Landscape-adaptive layouts for 25+ dialogs — Large regression surface

---

## 1. Problem Statement

All dialogs in the app are authored with `wrap_content` height and a portrait-first vertical layout. On a phone in landscape orientation, the usable screen height drops to ~360–400 dp (roughly half of portrait). As a result:

- **Tall dialogs are clipped** — content below the visible area is inaccessible, with no scroll affordance.
- **Action buttons disappear** — dialogs like `dialog_slideshow_settings` and `dialog_player_settings` have their Confirm/Cancel buttons rendered off-screen.
- **Wasted horizontal space** — landscape phones have ample horizontal room, but all dialogs remain single-column, leaving 40–60% of the width empty.
- **Player dialogs are worst** — the Player screen forces landscape on most videos; every dialog triggered during playback is broken.

Only 4 of 32 dialogs have a `layout-land/` variant today. The remaining 28 have no landscape handling. Additionally, 5 dialog Kotlin classes call `window?.setLayout(width, WRAP_CONTENT)` with hardcoded width calculations that do not adapt to orientation — these must be audited and fixed alongside the layout changes.

---

## 2. Goals

1. All 28 dialogs listed in §4.2 are visible and fully usable in landscape on a 360×640 dp device (Pixel 4a emulator, API 30).
2. All action buttons (OK, Cancel, Apply, Close) remain visible without scrolling in every dialog on that device configuration.
3. Portrait layout of all 28 dialogs is pixel-identical to the pre-change baseline.
4. Dialogs containing `RecyclerView` scroll their list correctly in both landscape and portrait.
5. Device rotation mid-dialog does not crash or lose entered data (state survives `savedInstanceState`).
6. `window?.setLayout()` calls that override height with hardcoded values are corrected to `WRAP_CONTENT`.
7. Two new dimension resources (`dialog_landscape_max_height`, `dialog_landscape_list_max_height`) are added to `dimens.xml`.
8. `lintStandardDebug` passes with zero new warnings.

**Non-goals for this spec:**
- Tablet / large-screen two-pane layouts (roadmap item II.3)
- Foldable outer-screen optimization
- Migrating dialogs to `BottomSheetDialogFragment`
- Converting any layout to Jetpack Compose (roadmap item II.2)
- Changing dialog content, logic, or adding new fields

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | All 28 dialogs present |
| `lite`     | ✅ | Shares dialog layouts; player and slideshow dialogs present |
| `photos`   | ✅ | Shares dialog layouts; slideshow and image-edit dialogs present |
| `legacy`   | ✅ | Same layouts compiled; `minSdk 23` requires API 23-safe XML only |

No `BuildConfig` flag gates this feature — landscape layout is a pure resource qualifier mechanism that applies to all flavors automatically. No new flag is required.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | `layout-land/` qualifier is supported from API 1. No difference in behavior. Ensure no XML attributes require API > 23 in new landscape files (e.g., `app:cornerRadius` on `ShapeableImageView` is API-agnostic; avoid `android:windowLayoutInDisplayCutoutMode` which is API 28+). |
| 26+ (standard minSdk) | Default path — all new landscape layouts must be verified on API 26 AVD. |
| 34+ (Android 14) | Predictive back gesture: rotation mid-dialog must not trigger predictive-back incorrectly. No layout change needed; handled by existing `DialogFragment` back-press plumbing. |

### 3.3 Wear OS Impact

No Wear OS changes required. Dialog layouts reside only in the `app_v2/` module; the `wear/` module has no dialogs affected by this spec.

---

## 4. Current Architecture (Relevant Parts)

### 4.1 Dialogs with existing `layout-land/` variants (skip)

| File | Status |
|------|--------|
| `dialog_filter.xml` | Done — ScrollView wrapper |
| `dialog_rename.xml` | Done |
| `dialog_rename_multiple.xml` | Done |
| `dialog_file_info.xml` | Done |

### 4.2 Dialogs requiring landscape treatment

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
| 25 | `dialog_scrollable_text.xml` | A – ScrollView | Has ScrollView but dialog maxHeight unconstrained |
| 26 | `dialog_slideshow_settings.xml` | B – Two-column | Most content clipped (ConstraintLayout, no scroll) |
| 27 | `dialog_sort.xml` | D – Trivial | Short radio-button list |
| 28 | `dialog_translation_settings.xml` | B – Two-column | Settings rows clipped |

**Total: 28 dialogs** (7 trivial D, 4 ScrollView A, 8 constrained-list C, 9 two-column B).

### 4.3 Dialog Kotlin classes with `setLayout` calls

| Class | Location | setLayout call | Problem |
|-------|----------|---------------|---------|
| `DialogUtils` | `ui/common/DialogUtils.kt:79` | width=90%, height=WRAP_CONTENT | Width-only; height fine |
| `FileOperationProgressDialog` | `ui/dialog/FileOperationProgressDialog.kt:149` | width=90%, height=WRAP_CONTENT | Width-only; height fine |
| `ScheduledOperationDialog` | `ui/dialog/ScheduledOperationDialog.kt:41` | width=93%, height=WRAP_CONTENT | Width-only; height fine |
| `FileInfoDialog` | `ui/dialog/FileInfoDialog.kt:47` | Unknown — read before impl | Must audit |
| `PlayerSettingsDialog` | `ui/dialog/PlayerSettingsDialog.kt:61` | width=90%, height=WRAP_CONTENT | Width-only; height fine |
| `FileOperationDestinationDialog` | `ui/dialog/FileOperationDestinationDialog.kt:69` | width=?, height=WRAP_CONTENT | Width-only; height fine |
| `ResourcePickerDialog` | `ui/dialog/ResourcePickerDialog.kt:47` | width=?, height=WRAP_CONTENT | Width-only; height fine |
| `ScheduledLogDialog` | `ui/dialog/ScheduledLogDialog.kt:24` | width=93%, height=WRAP_CONTENT | Width-only; height fine |
| `SlideshowSettingsDialogFragment` | `ui/player/SlideshowSettingsDialogFragment.kt:192` | width=MATCH_PARENT, height=WRAP_CONTENT | Landscape: MATCH_PARENT width is correct; height fine |

**Key finding:** All 9 `setLayout` calls use `WRAP_CONTENT` for height — none hardcode a fixed pixel height. No Kotlin changes are required for the height dimension. The width calculations (percentage of `displayMetrics.widthPixels`) are acceptable in landscape. No Kotlin modifications needed unless a specific dialog is found during manual testing to override height.

### 4.4 Architecture gap

The core gap is purely in the XML resource layer: 28 portrait-only layout files in `res/layout/` with no `res/layout-land/` counterpart. Android's resource qualifier system would automatically select landscape variants if they existed — no Java/Kotlin code change is needed to activate them.

---

## 5. Proposed Architecture

### 5.1 Resource Qualifier Strategy

Create `layout-land/<dialog>.xml` files for the 24 dialogs that need a landscape file (categories A, B, C). Android's resource system will automatically inflate the correct variant based on orientation. Categories use three complementary patterns:

**Category A — ScrollView wrap:**
Wrap the root content in a `NestedScrollView` with `android:maxHeight="@dimen/dialog_landscape_max_height"` and `android:fillViewport="true"`. No structural change to the inner view tree.

**Category B — Two-column layout:**
Replace the vertical `LinearLayout` or `ConstraintLayout` root with a horizontal `LinearLayout` splitting content 50/50. Both columns individually wrapped in `NestedScrollView` if column content can still overflow on 320 dp height screens. Action buttons pinned at the bottom of the right column or in a full-width row below both columns.

Column assignment guidelines:
- Left column: labels, primary inputs, read-only display fields, section headers
- Right column: secondary inputs, checkboxes, toggles, action buttons

**Category C — Constrained list:**
Replace `wrap_content` on the list container with `layout_height="0dp"` + `layout_weight="1"` (in `LinearLayout` parent) or `app:layout_constraintHeight_max="@dimen/dialog_landscape_list_max_height"` (in `ConstraintLayout` parent). Action buttons kept in a fixed `LinearLayout` below the list, outside any scroll area.

**Category D — Trivial verification (no new file):**
Manually verify each dialog on Pixel 4a landscape emulator. If clipping is observed, add `android:maxHeight="@dimen/dialog_landscape_max_height"` to the root — otherwise no file created.

### 5.2 New dimension resources

Add to `app_v2/src/main/res/values/dimens.xml`:

```xml
<!-- Landscape dialog constraints — referenced only from layout-land/ files -->
<dimen name="dialog_landscape_max_height">320dp</dimen>
<dimen name="dialog_landscape_list_max_height">240dp</dimen>
```

These are referenced **only** from `layout-land/` files. Portrait dialogs remain `wrap_content`.

### 5.3 New files

| File | Location | Est. lines |
|------|----------|-----------|
| `layout-land/dialog_file_copy_progress.xml` | `app_v2/src/main/res/` | ≤ 80 |
| `layout-land/dialog_file_operation_progress.xml` | `app_v2/src/main/res/` | ≤ 100 |
| `layout-land/dialog_filter_resource.xml` | `app_v2/src/main/res/` | ≤ 120 |
| `layout-land/dialog_scrollable_text.xml` | `app_v2/src/main/res/` | ≤ 40 |
| `layout-land/dialog_copy_to.xml` | `app_v2/src/main/res/` | ≤ 80 |
| `layout-land/dialog_folder_browser.xml` | `app_v2/src/main/res/` | ≤ 80 |
| `layout-land/dialog_folder_selection.xml` | `app_v2/src/main/res/` | ≤ 80 |
| `layout-land/dialog_import_favorites_preview.xml` | `app_v2/src/main/res/` | ≤ 100 |
| `layout-land/dialog_log_view.xml` | `app_v2/src/main/res/` | ≤ 60 |
| `layout-land/dialog_network_discovery.xml` | `app_v2/src/main/res/` | ≤ 80 |
| `layout-land/dialog_resource_picker.xml` | `app_v2/src/main/res/` | ≤ 80 |
| `layout-land/dialog_scheduled_log.xml` | `app_v2/src/main/res/` | ≤ 60 |
| `layout-land/dialog_slideshow_settings.xml` | `app_v2/src/main/res/` | ≤ 200 |
| `layout-land/dialog_player_settings.xml` | `app_v2/src/main/res/` | ≤ 180 |
| `layout-land/dialog_translation_settings.xml` | `app_v2/src/main/res/` | ≤ 150 |
| `layout-land/dialog_epub_reader_settings.xml` | `app_v2/src/main/res/` | ≤ 150 |
| `layout-land/dialog_image_edit.xml` | `app_v2/src/main/res/` | ≤ 160 |
| `layout-land/dialog_gif_editor.xml` | `app_v2/src/main/res/` | ≤ 160 |
| `layout-land/dialog_color_picker.xml` | `app_v2/src/main/res/` | ≤ 120 |
| `layout-land/dialog_access_password.xml` | `app_v2/src/main/res/` | ≤ 100 |
| `layout-land/dialog_scheduled_operation.xml` | `app_v2/src/main/res/` | ≤ 180 |

Total: 21 new layout-land files (24 dialogs that need a file; 3 Category D may not require a file after verification).

### 5.4 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Pure XML resource changes; no Kotlin touched |
| New classes follow naming conventions | ✅ | No new Kotlin classes |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | No data flow changes |
| No `Log.d()` — Timber only | ✅ | No Kotlin changes |
| Room schema version incremented | N/A | No DB changes |
| `StateFlow` for state, `SharedFlow` for one-shot events | N/A | No new state |
| Hilt DI: new bindings declared in module file | N/A | No new classes |

### 5.5 Two-column split details per Category B dialog

| Dialog | Left column content | Right column content | Action buttons location |
|--------|--------------------|--------------------|------------------------|
| `dialog_slideshow_settings` | Interval slider + play-to-end checkbox + music label | Music path + select/clear buttons + randomize toggle | Below right column |
| `dialog_player_settings` | Speed chips + repeat checkbox | Subtitles section + audio track spinner | Top of layout (title bar with Cancel/Apply stays full-width) |
| `dialog_translation_settings` | Source/target language selectors | Provider options + cache toggle | Full-width row below both columns |
| `dialog_epub_reader_settings` | Font family + font size | Theme + margins | Full-width row below both columns |
| `dialog_image_edit` | Preview (ImageView) | Brightness/contrast/saturation seekbars | Full-width row below both columns |
| `dialog_gif_editor` | Preview | Frame controls + speed | Full-width row below both columns |
| `dialog_color_picker` | Hue/saturation/value sliders | Preview swatch + hex input | Full-width row below both columns |
| `dialog_access_password` | Password input field | Confirm button + show-password toggle | Confirm in right column |
| `dialog_scheduled_operation` | Name + operation type | Schedule time/date + recurrence | Full-width row below both columns |

---

## 6. Data Flow

This feature is purely a presentation-layer change. No data flow is affected.

```
Device rotates to landscape
        │
        ▼
Android resource qualifier system
        │  selects res/layout-land/<dialog>.xml  (new file)
        │  vs res/layout/<dialog>.xml            (existing portrait)
        ▼
Dialog inflates landscape variant automatically
        │
        ▼
Existing Kotlin dialog class unchanged
(binds to same view IDs — IDs must be identical in both portrait and landscape layouts)
```

**Critical constraint:** Every view ID used by the Kotlin dialog class (`binding.tvTitle`, `binding.btnApply`, etc.) must be present with the same ID in both the portrait and the new landscape layout. Missing IDs in the landscape layout will cause a `NullPointerException` at runtime when the binding is accessed after rotation.

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `app_v2/src/main/res/values/dimens.xml` | Add 2 landscape dimen entries | ~130 lines |

All other changes are **new files** in `layout-land/`. No existing `layout/` portrait files are modified.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| View ID missing in landscape layout — `NullPointerException` at runtime | High | Before creating each landscape file, grep the Kotlin dialog class for all `binding.*` references and ensure every ID is present in the new layout |
| Two-column layout breaks on dialogs with dynamically-populated containers (views added at runtime) | Medium | Read the Kotlin dialog helper before creating the layout-land file; identify dynamic containers and keep them in a single full-width column |
| Portrait regression from accidentally editing portrait files | Low | All landscape files created in `layout-land/`; never edit files in `layout/` during this work |
| `setLayout` window call overrides XML height (hardcoded pixel height) | Low | Audit confirmed: all 9 calls use `WRAP_CONTENT` for height — no risk |
| Dialog height dimen 320dp too small on minSdk 23/26 devices with visible nav bar | Low | Test on API 26 AVD; if nav bar is 48dp, effective dialog area is ~312dp — adjust `dialog_landscape_max_height` to 280dp if needed |
| `dialog_slideshow_settings` uses `ConstraintLayout` — no `layout_weight` available; must use `app:layout_constraintWidth_percent` for 50/50 split | Medium | Use `Guideline` at 50% or wrap in a horizontal `LinearLayout` root; read the full portrait XML first |
| `SlideshowSettingsDialogFragment.onStart()` sets `width=MATCH_PARENT` — this is correct but means the two-column layout must fill the full width | Low | No change needed; MATCH_PARENT width is fine for two-column |

---

## 9. Testing Plan

### 9.1 Unit Tests

No unit tests required. This feature consists entirely of XML layout resources with no business logic. The correctness criteria are visual (no clipping, buttons visible) and are verified through manual testing and emulator screenshot comparison.

### 9.2 Manual Test Cases

For **each** of the 28 dialogs, execute in order:

1. **Portrait baseline** — open dialog in portrait on Pixel 4a AVD (API 30); take screenshot for regression baseline.
2. **Landscape happy path** — rotate device to landscape; verify all content is visible, action buttons are accessible without scrolling.
3. **Landscape scroll** — for Category A and C dialogs: verify that long content scrolls correctly and buttons remain pinned outside the scroll area.
4. **Rotation mid-dialog** — open dialog in portrait, enter data (text, check a checkbox, move a slider), rotate to landscape; verify data is preserved and the dialog layout switches to landscape variant.
5. **Rotate back** — from landscape rotate back to portrait; verify portrait layout is restored and data still preserved.
6. **Error state — very small screen** — test on API 26 AVD at 320×533 dp resolution; verify no crash and at minimum action buttons are reachable via scroll.
7. **Back/Cancel** — in landscape orientation, press Back or Cancel; verify dialog dismisses cleanly.

**Priority order for testing:** Player-triggered dialogs first (`slideshow_settings`, `player_settings`, `image_edit`, `gif_editor`), then Browse-triggered (`folder_browser`, `copy_to`, `filter_resource`), then Settings-triggered (remaining).

### 9.3 Maestro E2E

No Maestro tests needed. Landscape layout correctness requires visual inspection; automated UI tests would require screenshot diffing infrastructure not currently in the project. The manual test matrix in §9.2 is sufficient for this tier.

---

## 10. Accessibility

This feature modifies layout structure (column arrangement, scroll container insertion) but does not add or remove interactive elements, change content descriptions, or alter touch targets. The following rules must be verified during manual testing for each landscape layout file:

- All interactive elements (buttons, checkboxes, spinners, sliders) retain their `android:contentDescription` or labelled-by relationships from the portrait version — do not remove these when copying into the landscape layout.
- Touch target minimum height 48 dp must be preserved in the two-column layout. Do not reduce padding when splitting columns.
- No colour-only affordances are introduced. The two-column layout uses the same views with the same styles — no new visual indicators.
- TalkBack traversal order: in two-column layouts, verify that the focus order is logical (left column top-to-bottom, then right column top-to-bottom) by setting `android:accessibilityTraversalBefore`/`After` if the default order reads incorrectly.

---

## 11. User-Facing Feature Update

This fix eliminates broken UI in landscape orientation, which is a material improvement to usability. Update all three FEATURES docs after the final Phase is complete:

- `docs/FEATURES.md` (EN): Under **Settings / UI Polish** — "All 28+ dialogs now have landscape-optimized layouts; buttons and form fields remain accessible when the phone is rotated."
- `docs/FEATURES_RU.md` (RU): В разделе **Настройки / Полировка интерфейса** — «Все 28+ диалогов теперь имеют оптимизированные макеты для альбомной ориентации; кнопки и поля форм доступны при повороте телефона.»
- `docs/FEATURES_UK.md` (UK): У розділі **Налаштування / Полірування інтерфейсу** — «Усі 28+ діалогів тепер мають оптимізовані макети для альбомної орієнтації; кнопки та поля форм доступні при обертанні телефону.»

---

## 12. Architecture Decision Records

**ADR-1: XML-only approach — no Kotlin/Java changes**
- **Decision:** All landscape adaptations are implemented as `layout-land/` resource files only. No Kotlin dialog code is modified unless a `setLayout` call with a hardcoded pixel height is discovered during the audit (none found as of writing).
- **Alternatives considered:** (a) Programmatic orientation detection in `onStart()` + runtime layout adjustment; (b) Single layout with `ConstraintLayout` percent-based constraints that adapt both orientations.
- **Reason:** The resource qualifier system is the idiomatic Android approach. It has zero runtime overhead, requires no code changes, and is fully reversible. Programmatic detection adds coupling and maintenance burden. Single-layout ConstraintLayout percent approaches require extensive refactoring of every dialog and don't guarantee correct behavior on edge-case screen sizes.

**ADR-2: Two independent dimension resources rather than a single `dialog_landscape_max_height`**
- **Decision:** Two values: `dialog_landscape_max_height = 320dp` for full-dialog cap, `dialog_landscape_list_max_height = 240dp` for list areas within dialogs that also have a header and action buttons.
- **Alternatives considered:** Single value used everywhere.
- **Reason:** A list capped at 320dp inside a dialog that also has a 40dp header and 48dp button row would leave no room for the list on a 360 dp height screen. The 240dp list cap reserves 80dp for surrounding chrome.

**ADR-3: View IDs must be identical in portrait and landscape layouts**
- **Decision:** All view IDs in `layout-land/` files must match exactly the IDs in the corresponding `layout/` file. No ID may be added or removed.
- **Alternatives considered:** Null-safe binding access with `?` operator in Kotlin; separate binding classes per orientation.
- **Reason:** ViewBinding generates a single binding class per layout file name, shared across qualifiers. Any ID present in one variant but absent in another causes a crash. Keeping IDs identical is the only safe approach without changing Kotlin code.

---

## 13. Implementation Steps

### Phase 1: Infrastructure (0.5h)

1. **Add dimension resources** — open `app_v2/src/main/res/values/dimens.xml`, add before the closing `</resources>` tag:
   ```xml
   <!-- Landscape dialog constraints — referenced only from layout-land/ files -->
   <dimen name="dialog_landscape_max_height">320dp</dimen>
   <dimen name="dialog_landscape_list_max_height">240dp</dimen>
   ```
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/dimens.xml" "dimens" "Add landscape dialog dimension resources"`

2. **Audit setLayout calls** — confirm all 9 identified `setLayout` calls use `WRAP_CONTENT` for height. If any use a hardcoded pixel height, fix to `WRAP_CONTENT` and add to dev log. (Audit already performed in §4.3 — all use WRAP_CONTENT; no changes expected.)

### Phase 2: Category D — Trivial verification (1h)

3. Launch each of the 7 trivial dialogs (`dialog_delete`, `dialog_integration_test`, `dialog_material_progress_horizontal`, `dialog_material_progress_spinner`, `dialog_network_delete_confirmation`, `dialog_resource_type_selector`, `dialog_sort`) on Pixel 4a emulator in landscape.
4. If clipping is observed on any: add `android:maxHeight="@dimen/dialog_landscape_max_height"` to its root and create `layout-land/<dialog>.xml`. Run dev log for each file created.

### Phase 3: Category A — ScrollView wraps (2h)

For each file: read the portrait version, then create `layout-land/` with a `NestedScrollView` wrapper.

5. Create `layout-land/dialog_file_copy_progress.xml`
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_file_copy_progress.xml" "layout" "Add landscape ScrollView variant"`

6. Create `layout-land/dialog_file_operation_progress.xml`
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml" "layout" "Add landscape ScrollView variant"`

7. Create `layout-land/dialog_filter_resource.xml`
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_filter_resource.xml" "layout" "Add landscape ScrollView variant"`

8. Create `layout-land/dialog_scrollable_text.xml`
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_scrollable_text.xml" "layout" "Add landscape ScrollView variant"`

### Phase 4: Category C — Constrained list (3h)

For each file: read the portrait version, identify the list container and button row, then create `layout-land/` with constrained list height.

9. Create `layout-land/dialog_copy_to.xml`
10. Create `layout-land/dialog_folder_browser.xml`
11. Create `layout-land/dialog_folder_selection.xml`
12. Create `layout-land/dialog_import_favorites_preview.xml`
13. Create `layout-land/dialog_log_view.xml`
14. Create `layout-land/dialog_network_discovery.xml`
15. Create `layout-land/dialog_resource_picker.xml`
16. Create `layout-land/dialog_scheduled_log.xml`

Run dev log for each of steps 9–16:
`.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/<name>.xml" "layout" "Add landscape constrained-list variant"`

### Phase 5: Category B — Two-column (6h)

Order by user-visible priority (most frequently triggered first). For each:
1. Read the full portrait XML to identify all view IDs and dynamic containers.
2. Create `layout-land/` file with horizontal two-column split per §5.5 assignment table.
3. Verify all view IDs from the Kotlin binding class are present.

17. Read `dialog_slideshow_settings.xml` fully → create `layout-land/dialog_slideshow_settings.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_slideshow_settings.xml" "layout" "Add landscape two-column variant"`

18. Read `dialog_player_settings.xml` fully + `PlayerSettingsDialog.kt` binding refs → create `layout-land/dialog_player_settings.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_player_settings.xml" "layout" "Add landscape two-column variant"`

19. Create `layout-land/dialog_translation_settings.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_translation_settings.xml" "layout" "Add landscape two-column variant"`

20. Create `layout-land/dialog_epub_reader_settings.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_epub_reader_settings.xml" "layout" "Add landscape two-column variant"`

21. Create `layout-land/dialog_image_edit.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_image_edit.xml" "layout" "Add landscape two-column variant"`

22. Create `layout-land/dialog_gif_editor.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_gif_editor.xml" "layout" "Add landscape two-column variant"`

23. Create `layout-land/dialog_color_picker.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_color_picker.xml" "layout" "Add landscape two-column variant"`

24. Create `layout-land/dialog_access_password.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_access_password.xml" "layout" "Add landscape two-column variant"`

25. Create `layout-land/dialog_scheduled_operation.xml`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml" "layout" "Add landscape two-column variant"`

### Phase 6: Regression testing (2h)

26. Run `.\gradlew.bat lintStandardDebug` — fix any new warnings.
27. Execute full manual test matrix from §9.2 for all 28 dialogs.
28. Update FEATURES docs:
    - `docs/FEATURES.md`
    - `docs/FEATURES_RU.md`
    - `docs/FEATURES_UK.md`
    Run dev log for each: `.\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Add landscape dialogs feature entry"`

---

**Mandatory step checklist:**
- [ ] String resources: No new string resources required — all text in landscape layouts references the same `@string/` IDs as portrait versions.
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated (step 28)
- [ ] Room DB migration: N/A — no schema changes
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified/created file (steps 1, 5–25, 28)

---

## 14. Out of Scope (future items)

- Tablet / large-screen two-pane dialog layouts (roadmap II.3)
- Foldable device outer-screen optimization
- `BottomSheetDialogFragment` migration for list-heavy dialogs
- Jetpack Compose migration for any dialog (roadmap II.2)
- Screenshot regression test automation (would require Paparazzi or similar)
- Dynamic locale switching mid-dialog in landscape
