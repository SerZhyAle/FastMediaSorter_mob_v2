# R01 - Dialog identification and the cause of label clipping

Answers strategic spec S1366 §6 research items 1 and 2. Evidence is the working tree on 2026-08-09.

---

## 1. Which dialog the owner is complaining about

`ImageEditDialog` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ImageEditDialog.kt`.

Entry points that open it:

- Embedded player: `overflow_menu_player.xml` item `menu_edit` -> `CommandPanelController.kt:731` `callback.onEditClicked()` -> `PlayerCommandPanelCallbackImpl.kt:109` -> `PlayerDialogAndUiStateManager.showImageEditDialog()` -> `PlayerDialogHelper.kt:387`.
- Standalone player: `overflow_menu_standalone_player.xml` item `menu_edit_image` -> `PhotoVideoStandaloneActivity.kt:760` `openImageEditDialog()`.
- Keyboard shortcut: `PlayerKeyboardCallbackImpl.kt:85`.

Animated images branch to `GifEditorDialog` instead, so an animated file never reaches this layout.

Resolves the S1365 dependency: `menu_edit` is the entry S1365 relabelled to "Adjust", `menu_edit_text` is the unrelated text-file entry. Only `menu_edit` reaches `ImageEditDialog`.

### Layout variants

`Glob app_v2/src/main/res/layout*/dialog_image_edit.xml` returns exactly two:

- `app_v2/src/main/res/layout/dialog_image_edit.xml` - CRLF line endings.
- `app_v2/src/main/res/layout-land/dialog_image_edit.xml` - LF line endings.

No `sw480dp`, `sw720dp` or `w600dp` variant exists. The two files differ only by the landscape `android:maxHeight="@dimen/dialog_landscape_max_height"` (320dp) on the root `ScrollView` plus a header comment. Each file's own line endings must be preserved when editing.

---

## 2. Cause of the clipping - two independent defects

### 2a. The dialog window width is never set

`ImageEditDialog` extends `Dialog(context)` and never calls `window?.setLayout(..)`. The root `ScrollView` is `match_parent`, but inside a `Dialog` window "match_parent" means "match the window", and the window falls back to the platform dialog width from the theme's `dialogTheme`. That is the "зажатый" half of the complaint: the dialog cannot grow regardless of how much screen is free.

Every sibling dialog in the same package does set it:

- `PlayerSettingsDialog.kt:61` - `widthPixels * 0.9`, the closest sibling (same package, same `Dialog` base, same player context).
- `ListSelectionDialog.kt:36` - `widthPixels * 0.85`.
- `FileInfoDialog.kt:70`, `FileOperationDestinationDialog.kt:84`, `ScheduledOperationDialog.kt:67`, `GesturePickerDialog.kt:39` and 12 more.

So the fix has an unambiguous in-repo precedent and needs no new convention. Note `dialog_max_width` exists as a dimen (300dp / 720dp sw600 / 900dp sw720) but the percentage-of-width form is what the sibling dialogs actually use.

### 2b. The button rows are non-wrapping horizontal LinearLayouts

Three rows hold `wrap_content` `MaterialButton`s in a horizontal `LinearLayout` with `gravity="end"`:

- Rotation - `btnRotateLeft`, `btnRotate180`, `btnRotateRight` (lines 55-86).
- Flip - `btnFlipHorizontal`, `btnFlipVertical` (lines 98-119).
- Filters - `btnGrayscale`, `btnSepia`, `btnNegative` (lines 131-162).

A horizontal `LinearLayout` never wraps: when the children's measured width exceeds the row, the excess is clipped. Combined with 2a's narrow window this is what truncates the labels.

### Worst-case labels per locale

Rotation labels are symbol-only (`↺ 90°`, `180°`, `↻ 90°`) and identical in all three locales, so that row is the least affected. The other two rows are where the length varies:

| Row | EN | RU | UK |
| --- | --- | --- | --- |
| Flip | `Flip ↔` / `Flip ↕` | `Отразить ↔` / `Отразить ↕` | `Віддзеркалити ↔` / `Віддзеркалити ↕` |
| Filters | `Grayscale` / `Sepia` / `Negative` | `Оттенки серого` / `Сепия` / `Негатив` | `Відтінки сірого` / `Сепія` / `Негатив` |

UK is the longest locale in both rows, so UK is the layout's worst case and the one acceptance must be checked against. Source: `app_v2/src/main/res/values{,-ru,-uk}/strings_image_viewer.xml`.

The title is also longest in RU/UK (`Редактирование изображения` / `Редагування зображення`) and shares its row with `btnClose`, so the header row is a third overflow candidate.

---

## 3. The fix pattern is already established project-wide

S0605 established the invariant for exactly this shape: a group of 2+ text buttons that overflows must become an `androidx.constraintlayout.helper.widget.Flow` chip group with `app:flow_wrapMode="chain"`, `app:flow_horizontalStyle="packed"`, `app:flow_horizontalBias="0"`. Buttons stay `wrap_content` (text-sized) and wrap to a new line instead of being clipped. Buttons must never be stretched to full or half screen width to solve this.

Verified live instances of the pattern:

- `fragment_settings_general.xml:871` `flowDocLinks`, plus `flowSettingsFile`, `flowSettingsBackup`, `flowSettingsFavorites`, `flowSettingsResources`.
- `dialog_folder_selection.xml:15` `flowVirtualFolders`, `:47` `flowQuickFolders`.
- Landscape counterparts of both files carry the same Flow elements.

So neither defect needs a design decision: 2a copies `PlayerSettingsDialog`, 2b copies the S0605 Flow chip group.

---

## 4. Constraints carried into the plan

- Rule 11 / variant parity - both `layout/` and `layout-land/` must change together; there is no third variant.
- Rule 17 - the widened dialog must stay inside `systemBars` + `displayCutout`. Landscape already caps height at `dialog_landscape_max_height`; widening must not remove that cap.
- Rule 16 - `Flow` is a virtual helper, so the buttons remain real siblings in the `ConstraintLayout` and D-pad traversal order follows XML order. Explicit `nextFocus*` is only needed if traversal order changes.
- Rule 19 - no hex colours, no long dashes; the layout currently uses `@style`/`@dimen` references only and must stay that way.
- Neither gate baseline (`dialog-cancel-style-baseline.txt`, `untracked-dialog-baseline.txt`) lists this file, so the closure gates must stay green without a baseline edit.
