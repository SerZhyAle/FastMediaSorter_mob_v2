# Research 02 - Dialog construction paths & enforcement seam

Bound to §6 item 2. Read-only investigation; informs §4 context and §5.1 pillar B + §5.2.

## Three parallel dialog construction paths in app_v2

All dialog code lives in `src/main/` (no flavor-specific dialog overrides in lite/photos/legacy/noLegal source sets).

**Path A - inline builders (`AlertDialog.Builder` / `MaterialAlertDialogBuilder`).**
- ~94 Kotlin files, ~281 `setPositiveButton`/`setNegativeButton` call-sites total; ~200 of them inline builder call-sites across ~60 files.
- Heavy concentrations: `ui/browse/managers/BrowseDialogHelper.kt` (774 LOC), `ui/player/PlayerDialogHelper.kt` (676 LOC), `ui/addresource/AddResourceConnectionManager.kt` (~22 builder call-sites), `ui/browse/managers/BrowseArchiveDialogManager.kt`, `ui/keybinding/helpers/ResetConfirmationDialog.kt`.
- `MaterialAlertDialogBuilder` buttons are restyleable globally via a theme `alertDialogButtonBarButtonStyle` override - one seam covers all of them.
- Bare `AlertDialog.Builder` (e.g. `ResetConfirmationDialog`, ~5 files) will NOT pick up that override -> needs migration to the Material builder / shared helper.

**Path B - custom `Dialog` subclasses with inflated layouts.**
- `DeleteDialog`/`dialog_delete.xml`, `RenameDialog`/`dialog_rename.xml`, `FileOperationDestinationDialog`/`dialog_copy_to.xml`, `ScheduledOperationDialog`/`dialog_scheduled_operation.xml`, etc.
- 17 dialog layouts with OK/Cancel buttons: `dialog_color_picker`, `dialog_copy_to`, `dialog_delete`, `dialog_file_operation_progress`, `dialog_filter_resource`, `dialog_filter`, `dialog_folder_browser`, `dialog_gif_editor`, `dialog_image_edit`, `dialog_network_discovery`, `dialog_player_settings`, `dialog_rename_multiple`, `dialog_rename`, `dialog_resource_picker`, `dialog_scheduled_operation`, `dialog_translation_settings`, plus `dialog_network_delete_confirmation` (positive button wired in code, none in XML).
- These need the named style applied per layout (`style=@style/Widget.FastMediaSorter.Button.<Role>`).

**Path C - `DialogFragment` / `BottomSheetDialogFragment`.**
- 7 DialogFragment + 6 BottomSheetDialogFragment (e.g. `PermissionRationaleBottomSheet`, `StreamOffloadOfferDialog`, `SendToBottomSheet`). Inflate own layouts, wire buttons manually. Bottom-sheet scope is an open question (§6 item 3).

## Shared infrastructure that any seam must respect

- `core/ui/DialogAccessibilityHelper.kt` (102 LOC) - TalkBack initial focus, called post-`show()` for both `AlertDialog` and `android.app.AlertDialog`. A button-bar restyle must not break its `getButton(BUTTON_POSITIVE)` assumptions.
- `ui/dialog/DialogKeyboardDelegate.kt` (99 LOC) - Enter=confirm / Escape=dismiss, wired into custom `Dialog` subclasses.
- `dimens.xml` already has `destination_button_min_height 56dp` (touch-friendly intent) and a `dialog_button_inset 0dp` token - zero-inset shrinks visible height while keeping a 48dp touch target; must be reconciled when buttons are made visibly larger.

## Enforcement strategy (informs ADR-1)

- One theme-level override of the dialog button-bar style covers all `MaterialAlertDialogBuilder` dialogs (Path A majority) without per-call edits.
- Custom layouts (Path B) get the named style applied in XML.
- Bare `AlertDialog.Builder` call-sites are migrated to the Material path (small, ~5 files).
- New dialogs inherit automatically via the theme default - self-enforcing.

## Risks surfaced for §7

- `BrowseDialogHelper.kt` (774) mixes both builder types and is near the LOC ceiling - prefer theme-level enforcement over editing it.
- Larger buttons could disturb D-pad/TV focus traversal and the post-show accessibility wiring.
- `dialog_translation_settings.xml` puts Cancel in the title row (not a bottom bar) - cannot take a uniform bottom-bar rule without redesign or exemption.
- `dialog_network_delete_confirmation.xml` uses hardcoded sp/dp values (separate dead-weight/neuroslop concern).

## Parked-candidate findings (out of scope of S0538)

Listed for the caller to capture via `/spec-draft` (researcher is read-only):
1. `BrowseDialogHelper.kt` 774 LOC + mixed builder types - extraction-to-sub-managers refactor before it crosses 1500.
2. `dialog_network_delete_confirmation.xml` hardcoded `24dp`/`20sp`/`16sp`/`14sp` instead of `?dimen` tokens - dimen-token migration.
3. `ResetConfirmationDialog` is the lone bare `androidx.appcompat.app.AlertDialog` builder - inconsistency (will be absorbed by this spec's migration, so may not need a separate ticket).
