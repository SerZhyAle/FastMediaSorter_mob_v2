# Research 01 - Inventory of simple value-selection dialogs in settings

**Spec:** S0646 - settings-simple-list-dialog-unification
**Date:** 2026-06-23
**Method:** class-catalog + grep sweep over `app_v2/src/main` and flavor source sets; read of S0567/S0595 archived tactical plans.

---

## Key finding - the canonical component already exists

S0567 Phase 04 (`temp/done/S0567_ui-settings-forms-dialogs-unification/PHASE_04__list-selection-dialog.md`, status Done) delivered a generic minimalistic list-selection dialog and migrated two call sites onto it:

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ListSelectionDialog.kt` - `open class ListSelectionDialog<T>(context, config: ListSelectionConfig<T>)`. Width 85% of screen, WRAP_CONTENT height, RecyclerView + `LinearLayoutManager`, themed item views, no runtime colors. Single-tap to select + dismiss; `isSelected` lambda draws a leading `ic_check` on the current value; `allowClear` shows a Clear button.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ListSelectionAdapter.kt` - `ListSelectionAdapter<T>` + `interface ItemFormatter<T> { getDisplayName(item): String; getIcon(item): Drawable? }`.
- `app_v2/src/main/res/layout/item_list_selection.xml`, `dialog_list_selection.xml`.
- Migrated: `ResourcePickerDialog`, `DestinationPickerDialog` (reduced to thin `ListSelectionDialog<MediaResource>` subclasses).

`ListSelectionConfig<T>` fields: `title`, `lifecycleOwner`, `loader: suspend () -> List<T>`, `formatter`, `hasSelection`, `isSelected`, `allowClear`, `emptyMessageRes`, `errorMessageRes`, `onSelected: (T?) -> Unit`.

S0567 was archived after phases 01-04; the remainder S0595 carried only phases 05-07 (resource-form-primitives, action-help-row-audit, docs). The broad "migrate every remaining ad-hoc value dialog onto `ListSelectionDialog`" sweep was never scheduled - that is exactly S0646.

Implication: S0646 is a MIGRATION ticket onto the existing `ListSelectionDialog<T>`, not a new-component ticket.

---

## SIMPLE value-selection sites (candidate scope)

| ID | Setting | File:line | Current mechanism |
|----|---------|-----------|-------------------|
| A | Screenshot gesture action (x3) | `ui/settings/helpers/ScreenshotGestureActionPickerManager.kt:34` | `MaterialAlertDialogBuilder.setSingleChoiceItems` |
| B | Add destination (resource list) | `ui/settings/helpers/OperationsDestinationsManager.kt:97` | appcompat `AlertDialog.Builder.setItems` |
| C | Destination picker (screenshot/link dl) | `ui/settings/fragments/OperationsSettingsFragment.kt:564` | appcompat `AlertDialog.Builder.setSingleChoiceItems` (+ leading Clear) |
| D | Import method (auto/browse) | `ui/settings/helpers/GeneralSettingsImportExportHelper.kt:90` | appcompat `AlertDialog.Builder.setItems` |
| E | Widget type | `ui/settings/helpers/GeneralSettingsWidgetHelper.kt:52` | `MaterialAlertDialogBuilder.setItems` |
| F | Document viewer type | `ui/settings/helpers/DefaultPlayerHelper.kt:161` | `MaterialAlertDialogBuilder.setItems` |
| G | OCR font size | `ui/settings/fragments/OtherMediaSettingsFragment.kt:288` | raw `android.widget.Spinner` |
| H | OCR font family | `ui/settings/fragments/OtherMediaSettingsFragment.kt:321` | raw `android.widget.Spinner` |
| I | OCR engine type | `ui/settings/fragments/OtherMediaSettingsFragment.kt:348` | raw `android.widget.Spinner` (noLegal) |
| J | PaddleOCR model | `ui/settings/fragments/OtherMediaSettingsFragment.kt:383` | raw `android.widget.Spinner` (noLegal) |
| K | Visualizer when cover art absent | `ui/settings/fragments/AudioSettingsFragment.kt:151` | raw `AutoCompleteTextView` (+ delivery-gate revert) |

Owner's three named examples map to: K (visualizer), G (OCR font size), and "network parallelism".

## Network parallelism - stale example

Network parallelism was migrated by S0567 Phase 03 to a numeric free-form `SettingsInputRow` (`ui/settings/helpers/GeneralSettingsViewSetupHelper.kt:327`). It is no longer a list. Owner's example is stale; treat as out of scope unless owner wants it reverted to a list.

## COMPLEX / out-of-scope (do NOT touch)

- `ui/profile/DeviceProfilePickerDialogFragment.kt` - graphical grid tiles, recommended badge, overwrite-warning.
- `ui/dialog/SearchableLanguagePickerDialog.kt` - searchable RecyclerView, flag glyphs, per-language capability labels.
- `ui/settings/auth/AuthSessionsListFragment.kt:147` - cloud provider choice, provider-specific navigation.
- `ui/resourceeditor/ResourceEditorFragment.kt:414` - resource-editor profile selector, outside settings tabs.

## Existing canonical patterns (S0567)

- `ui/common/widget/SettingsSelectionRow.kt` - clickable row (title + optional icon + value + `>` chevron). The S0644 эталон trigger row.
- `ui/common/widget/SettingsDropdownRow.kt` - inline Material exposed-dropdown (language/theme/sort). Already consistent; not "done differently".
- `ui/common/widget/SettingsInputRow.kt` - numeric/text free-form (network parallelism).
- `ui/dialog/ListSelectionDialog.kt` - tap-to-open minimalistic list dialog (S0646 canonical target).

## Risks / gotchas

- `OtherMediaSettingsFragment` raw Spinners (G-J) carry downstream side-effects: OCR engine choice toggles `layoutPaddleOcrModel` visibility; must be preserved.
- Visualizer (K) runs a `DeliveryEnableInterceptor` check on selecting VISUALIZATION and reverts on refusal; must be preserved through any migration.
- Landscape counterparts: inline spinners (G-J) have no `layout-land` adapter; converting to a tap-row removes that concern, converting to `SettingsDropdownRow` reuses `sdr_inline`.
- Flavor gating: gesture-action (A) standard+noLegal only; OCR engine/model (I/J) noLegal only.
- No unit tests cover any of these dialog paths - safe from test regression, but no automated net.

## /spec-draft candidates surfaced (out of scope for S0646)

1. Raw `android.widget.Spinner` in `OtherMediaSettingsFragment` not migrated after S0567 (G-J) - if S0646 does not absorb them.
2. Mixed appcompat `AlertDialog.Builder` vs `MaterialAlertDialogBuilder` across settings helpers (B/C/D appcompat) - visual inconsistency independent of the list-dialog migration.
