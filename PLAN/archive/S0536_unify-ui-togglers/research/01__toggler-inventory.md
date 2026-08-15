# Research 01 - On/off toggler inventory (S0536)

Source: exhaustive parallel sweep + adversarial completeness critic over `app_v2/src/**/res/layout*/` (all source sets). Critic verdict: `confirmedComplete: true` - 9 switch widgets + 105 checkbox widgets enumerated, every file:line matched. Only layout dirs in the module: `debug/res/layout`, `main/res/{layout,layout-land,layout-sw480dp,layout-sw720dp}`, `noLegal/res/layout`, `vr/res/{layout,layout-land}`. All other source sets carry no layouts.

This artifact is a planning input - phases are ordered from it.

## Canonical component (the target form)

- `SettingsToggleRow` at `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt`, backed by `view_settings_toggle_row.xml`.
- Internal switch currently `com.google.android.material.switchmaterial.SwitchMaterial` (line 14 import, line 39 field; xml line 10).
- Public API is CompoundButton-level: `isChecked`, `setCheckedSilently`, `setOnCheckedChangeListener`. No consumer references `R.id.str_switch` directly.
- Used (already-converted, NOT migration targets) in 15 `fragment_settings_*` screens + `page_welcome_*` + `vr/fragment_vr_settings_block` (portrait + land).

## In scope - raw switches to wrap in the component

- `item_scheduled_operation.xml:26` `switchEnabled` (SwitchMaterial) - per-list-item enable toggle. No layout-land (orientation-agnostic item). Adapter `ScheduledOperationsAdapter.kt`: listener-null-then-rebind pattern (lines 42-43), toggle delegates to `ScheduledOperationsViewModel.toggleEnabled`. MEDIUM migration risk (RecyclerView recycling - preserve the silent rebind).
- `dialog_scheduled_operation.xml:210` `switchOverwrite` + `:385` `switchSilentMode` (SwitchMaterial), land `218`/`393`. Binding `ScheduledOperationDialog.kt` field `b`: no listener, populate-write (374/375) + read-on-save (443/444) into `ScheduledOperation.overwrite/silentMode`. LOW migration risk.
- `dialog_playback_control.xml:298` `switchVrOverrideFormatType` (MaterialSwitch - already the canonical class), land `306`. Binding `PlaybackControlDialogFragment.kt`: active listener (414) with `isUpdatingStereoControls` re-entry guard, programmatic reset (425), ephemeral non-persisted state. HIGH migration risk - the component must host a transient value; `setCheckedSilently` provides the re-entry guard equivalent.

## In scope - on/off checkboxes to convert to a switch row

- `dialog_player_settings.xml:124` `cbRepeatVideo` + `:163` `cbShowSubtitles` (MaterialCheckBox), land `126`/`165`. Title+desc rows.
- `dialog_slideshow_settings.xml:52` `cbPlayToEnd` (MaterialCheckBox), land `59`.
- `dialog_translation_settings.xml:132` `switchLensStyle` (MaterialCheckBox despite `switch*` id), land `134`.
- `dialog_camera_ocr_settings.xml:71` `cbOcrOnly` (MaterialCheckBox). No layout-land. Note: surrounding TextViews use hardcoded `#FFFFFF` (Rule 19) - incidental, not this ticket's target.

## Out of scope - deferred to S0537 (selection / multiselect / consent)

- Media-type filter multiselect: `dialog_filter.xml` (8 + 8 land).
- Scheduled-op file-type mask multiselect: `dialog_scheduled_operation.xml` cbFileType* (5 + 5 land). Note: the same dialog's two single on/off switches ARE in scope - this dialog is partially migrated by S0536 (switches), the masks stay for S0537.
- Add-resource form options + supported-media-type masks: `activity_add_resource.xml` (31).
- Resource-editor options + media-type mask: `fragment_resource_editor.xml` (16).
- Cloud folder-picker option checkboxes: OneDrive/GoogleDrive/Dropbox pickers (6).
- Resource-to-add list rows: `item_resource_to_add.xml` (5).
- Per-item media selection overlays: `item_media_file*` main (3) + noLegal (3), `item_duplicate_file.xml` (1).
- Consent: `dialog_network_delete_confirmation.xml` cbDontShowAgain (2) - "do not show again" acknowledgement.

## Excluded by scope decision (not S0537 either)

- `debug/res/layout/activity_debug.xml:28` `switchLeakCanary` - developer-only debug source set, not a shipped user-facing surface. S0536 is user-facing unification (§8: no FEATURES change). Left as-is; harmless SwitchMaterial in a debug screen.

## Widget-class divergence (goal #2)

- SwitchMaterial: the component itself + switchOverwrite/switchSilentMode/switchEnabled (+ debug, excluded).
- MaterialSwitch: only switchVrOverrideFormatType (already canonical).
- Decision: standardize on Material3 `MaterialSwitch`. The component swap converts its own switch; the SwitchMaterial dialog switches are removed by wrapping them in the (now MaterialSwitch-based) component.

## Component swap mechanics

- 3 edits: `SettingsToggleRow.kt` import line 14, field line 39; `view_settings_toggle_row.xml` element line 10. Target `com.google.android.material.materialswitch.MaterialSwitch`.
- MaterialSwitch already on classpath (live in dialog_playback_control). No Gradle change.
- `attrs.xml` SettingsToggleRow styleable unaffected (only `str_` data attrs).
- VISUAL watch: MaterialSwitch track is wider than SwitchMaterial; re-verify title spacing/alignment - `settings_switch_margin_end` (10dp / 8dp land, `values/dimens.xml` 302-303, `values-land/dimens.xml` 33) may need re-tuning. Re-check the `setHugsTextContent` compact path (VR settings).

## Theme style

- Theme `Theme.FastMediaSorter.App` parent `Theme.Material3.DayNight.NoActionBar` (`values/themes.xml` line 3). No `materialSwitchStyle`/`switchStyle` present.
- Add `<item name="materialSwitchStyle">@style/Widget.FastMediaSorter.Switch</item>` inside the app theme (before `</style>` ~line 32) and a sibling `<style name="Widget.FastMediaSorter.Switch" parent="Widget.Material3.CompoundButton.MaterialSwitch" />` near the other `Widget.FastMediaSorter.*` styles (~line 127).
- `materialSwitchStyle` targets only MaterialSwitch (not the legacy `switchStyle`/SwitchMaterial) - do not conflate.
