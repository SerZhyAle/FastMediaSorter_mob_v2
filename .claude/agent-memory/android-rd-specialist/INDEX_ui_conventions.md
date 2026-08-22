---
name: index-ui-conventions
description: Second-level pointer list for UI convention memories - focus frames, landscape and layout variants, settings row widgets, player family glue, welcome variants and doc-sync duties. Open when the task edits a layout, a settings screen or any user-visible surface.
metadata:
  type: reference
---

# UI conventions - pointers

Split out of `MEMORY.md` (2026-08-21): the top-level index is billed on every turn of every session, and these are needed only for this kind of work. The hooks here are each memory's own `description`, restored in full - the top-level index had squeezed several of them mid-word.

- [Focus indicator](project_focus_frame_infra.md) - App-wide TV/D-pad focus indicator = in-place per-view decoration (S0943); overlay-frame S0819 archived
- [No wrapper focus on rows](feedback_compound_row_no_wrapper_focus.md) - Don't apply Rule 16 focusable/clickable/nextFocus to SettingsDropdownRow wrappers; the inner field is the focus stop
- [Trampolines not Rule 3 exempt](feedback_trampolines_are_not_rule3_exempt.md) - The activity-logic gate counts domain-layer field injection in ANY Activity, trampolines included - and a tactical plan may prescribe the shape that fails it
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) - Buttons must never stretch full screen width in landscape; width = text (wrap_content) or fixed by form logic; keypad grids / nav rails / full-row item buttons exempt
- [multi-column](feedback_landscape_multicolumn_settings.md) - Canonical way to pack settings rows into multi-column landscape - weighted horizontal LinearLayout in layout-land only, column-count by element type, S0605 invariant held
- [Land player band](project_land_player_bottom_band_stacking.md) - layout-land player is ConstraintLayout - every bottom band must chain above bottomPanelsContainer, not anchor to parent bottom (S0368, S0852)
- [configChanges](project_streams_activity_config_changes_rotation.md) - StreamsActivity (and key activities) use configChanges=orientation; orientation-dependent layout must recompute in onConfigurationChanged, not only on recreate
- [InputRow greedy width](feedback_settingsinputrow_greedy_width.md) - SettingsInputRow is internally match_parent; wrap_content in a weighted row makes it eat all space and starve siblings to 0px - looks like an unchanged full-width field
- [Canonical pickers](feedback_canonical_settings_value_pickers.md) - Reuse the canonical settings value-picker dialog/row components instead of ad-hoc dialogs/Spinner/AutoCompleteTextView
- [Reuse settings](feedback_reuse_existing_settings.md) - Before adding a new settings toggle for a feature, check for an existing toggle covering the same capability and gate on it instead of duplicating
- [Deep-link](project_settings_section_deeplink.md) - How to deep-link into a specific Settings group/section (open tab + expand collapsible section)
- [Search gate axes](project_settings_search_gate_axes.md) - Settings-search dead-result suppression is split across 3 independent gates by axis; route new dead-result findings to the right one
- [Resource vs Folder](feedback_resource_vs_folder_terminology.md) - Canonical Resource-vs-Folder wording rule (S0799) + two-icon split (S0842) - when to say resource vs folder in UI/docs
- [Write-permission gating](project_write_permission_gating.md) - Resource write-permission gating - isWritable (probe) vs isReadOnly (policy), unified by MediaResource.allowsWriteOperations() (S1019)
- [Players are a family](feedback_player_family_glue_mirroring.md) - Players are a family of activities; per-host glue/layout changes must be mirrored, only shared-layer changes propagate
- [App self-pins shortcuts](project_app_self_pin_tests_launcher_pin_host.md) - FastMediaSorter pins its own shortcuts, so the launcher's CONFIRM_PIN_SHORTCUT host is testable end to end on an emulator with no third-party app
- [Welcome variants](project_welcome_layout_variants.md) - activity_welcome.xml exists in layout/, layout-sw480dp/, layout-sw720dp/ - a new view id must be added to all three or ViewBinding makes the field nullable
- [sw beats -land](project_res_sw_qualifier_beats_land.md) - values-swNNNdp silently overrides values-land for the same key - landscape ints need combined swNNNdp-land buckets
- [ALL layout variants](feedback_enumerate_all_layout_variants_not_just_land.md) - Rule 11 names only the layout-land counterpart, but screens here also have layout-w600dp (and other) variants - enumerate every variant of a layout before editing, or ViewBinding turns the miss into a nullable field
- [Top panels width grid](project_main_top_panels_width_grid.md) - Main-screen top 4 rows (command bar, programs, streams, resource tabs) width/alignment architecture - S1037+S1049 series
- [Sync docs on visible change](feedback_sync_docs_on_visible_change.md) - Whenever visible functionality changes, revisit & edit the affected doc sections and the program's website
- [Probe-measure breaks centering](project_probe_measure_poisons_text_centering.md) - Manual child.measure() on live views desyncs TextView label centering (getMeasuredHeight) from icon centering (real height) - heal via posted forceLayout+requestLayout
- [Shrinking card scales, never hides](feedback_shrinking_card_scales_never_hides.md) - Owner ruling - when a resizable card shrinks, scale every line and keep the format; never hide a secondary line or drop a field by size
- [HOW_TO path gate](reference_howto_settings_path_gate.md) - HOW_TO "Settings -> .." recipe drift gate (S0558) - where it lives and the vocab extension point when it fails
- [parity](feedback_howto_settings_path_parity.md) - HOW_TO settings-path recipes using arrow → are gate-validated AND require EN/RU/UK positional parity; ASCII > is not validated
- [edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md) - Play Console edge-to-edge warnings - #2 setStatusBarColor/setNavigationBarColor is REAL and present (Play flags the guarded invoke statically; runtime SDK guard is NOT enough); #1 is informational
- [Shared layout: fix shared layer](feedback_shared_layout_fix_at_shared_layer.md) - A visual defect in one dialog that shares a layout must be fixed in the shared component, not patched per host - the pickers proved a per-host patch resurfaces
