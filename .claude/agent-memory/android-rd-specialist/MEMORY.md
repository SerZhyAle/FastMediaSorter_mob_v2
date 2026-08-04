# Memory Index

Pointers only - open a file when its hook matches the work. Size is billed on every turn;
`scripts/quality/assert-memory-budget.ps1` refuses growth.

- [About Me](about_me.md) - solo owner, data engineer, no Kotlin · [Audience](feedback_target_audience_non_technical.md) - zero jargon
- [Argue then obey](feedback_argue_then_obey.md) + [decision after pushback](feedback_owner_decision_after_pushback.md)
- [Don't ask if architecture answers](feedback_no_owner_questions_when_architecture_already_answers.md) · [research forks](feedback_research_over_owner_question.md) · [clarify unclear framing](feedback_clarify_task_when_framing_unclear.md)
- [Timestamp every message](feedback_timestamp_in_chat.md) · [Writing style](feedback_writing_style.md) `..`/hyphen/ё, docs+UI only
- [Finish mechanical follow-ups](feedback_finish_mechanical_followups_in_context.md) · [Skill aliases](feedback_skill_aliases.md)
- [Universal Agent Kit](reference_universal_agent_kit.md) · [No paid/key services](feedback_no_paid_or_key_services.md) · [Weather on Open-Meteo](project_weather_gadget_open_meteo.md)
- [fms_companion](project_fms_companion_subproject.md) - Go+Wails, out of repo · [FMS Windows rebrand](project_fms_windows_rebrand.md)
- [Process audit 2026-07](project_process_audit_2026_07.md) - cost is context×turns · [Transcript cost mining](reference_transcript_cost_mining.md) - dedup by requestId

## Devices & release
- [Devices](reference_test_device_galaxy_s21.md) · [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md)
- [Never grant system roles on owner phone](feedback_never_grant_system_roles_on_owner_phone.md) · [setup_test_media](reference_setup_test_media.md) · [gh CLI path](reference_gh_cli_location.md)
- [R8 mapping is per-build](project_r8_mapping_is_per_build.md) - wrong mapping = plausible nonsense
- [Play API read-only](reference_play_console_api_access.md) · [FGS precedent](project_play_release_in_review.md) · [Device reach implies screen.*](project_play_device_reach_screen_portrait.md)
- [No coverage regression](feedback_release_no_coverage_regression.md) · [check OCR/translate versions](feedback_release_check_ocr_translate_versions.md)
- [Crash scan sees only ANR](project_crash_scan_blind_to_java_crashes.md) S1332 · [Car head unit](project_owner_runs_app_on_car_head_unit.md) · [Store art](project_store_screenshot_capture_traps.md)
- [/skill-release gotchas](project_skill_release_gotchas.md) · [Archive after release](feedback_archive_after_every_release.md) · [prerelease emulator-only](feedback_prerelease_emulator_only.md) + [Maestro flaky](project_prerelease_maestro_harness_flaky.md)

## Emulator & device testing
- [Emulator capture](reference_emulator_capture_family_testing.md) - reshape the AVD, never rotate it + [MediaProjection](reference_emulator_mediaprojection_capture.md)
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md)
- [Emulator acceptance ceiling](feedback_emulator_acceptance_ceiling.md) - what a sweep cannot prove
- [Onboarding device-test](feedback_onboarding_device_test_gotchas.md) · [Widget-only on AVD](reference_trigger_widget_only_features_on_emulator.md) · [Emulator too fast for transfer UI](feedback_emulator_too_fast_for_transfer_ui.md)
- [Check animator scale first](feedback_check_animator_scale_before_diagnosing.md) - AVDs run scale=0 · [Theme switch](feedback_color_theme_device_switch.md)
- [Dialogs invisible under wm override](feedback_dialogs_invisible_under_wm_override.md) S1264 - verify dialogs at native geometry
- [Black screenshot = FLAG_SECURE](feedback_black_screenshot_means_flag_secure.md) S1284 - grep `SECURE` in dumpsys first
- [Popup missing from the dump = modality](feedback_popup_invisible_to_uiautomator_is_modality.md) S1390 - check `mCurrentFocus` first
- [Voice-note transcription](reference_voice_note_transcription.md) - offline faster-whisper

## Build, flavors, gates
- [Don't release someone else's CODE.LOCK](feedback_code_lock_release_ownership.md) · [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md)
- [Fast checks](feedback_fast_checks_during_dev.md) + [no redundant flavor compile](feedback_no_redundant_flavor_compile.md)
- [Flavor isolation](feedback_flavor_isolation_strict.md) · [Don't infer arch from BuildConfig](feedback_dont_infer_from_buildconfig_names.md) · [Push features to lowest flavor](feedback_push_features_to_lowest_flavor.md)
- [Flavor grid is generated](project_flavor_matrix_cloud_correction.md) · [photos/lite OCR src sets](project_photos_flavor_ocr_break.md) · [Third-party branding ok](feedback_third_party_branding_not_a_blocker.md)
- [S0386 native-attach broken API36](project_s0386_native_attach_broken_api36.md) · [.so bundle vs on-demand](project_native_so_bundle_standard_vs_ondemand_nolegal.md)
- [screenCapture noLegal-only](project_screencapture_nolegal_only.md) · [gates gesture](project_screencapture_gates_gesture_capability.md)
- [manifest.srcFile overrides flavor](project_agp_manifest_srcfile_overrides_flavor_manifest.md) · [MSAL hash per keystore](project_msal_signing_hash_per_keystore.md)
- [material-icons-extended stays](project_material_icons_extended_not_removable.md) · [Incremental phantom ref](project_incremental_build_phantom_unresolved.md) - clean build
- [Build gotchas](project_build_gotchas.md) + [output trunc](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md)
- [Gradle via PowerShell, never Bash](feedback_gradle_via_powershell_not_bash.md) - Bash JAVA_HOME points at a missing JDK
- [Unmask kapt NPE](project_kapt_npe_unmask.md) + [subagent claims](feedback_verify_subagent_build_failures.md) · [Remove dead config too](feedback_remove_dead_applications_too.md)
- [Ctor change -> compile tests](feedback_constructor_change_compile_tests.md) + [pre-existing fails](feedback_build_pre_existing_test_failures.md)
- [fk does NOT check the Hilt graph](feedback_fk_does_not_validate_hilt_graph.md) - MissingBinding hides behind two green compiles
- [Check binding field types](feedback_check_generated_binding_types.md) - Button vs MaterialButton crashes · [No glob path in KDoc](feedback_kdoc_nested_comment_glob_path.md)

## detekt / lint / gates / logging
- detekt, by symptom: [in post-change](project_detekt_gate_in_post_change.md) · [dirty tree](feedback_detekt_gate_dirty_tree.md) · [-ScopeToFile](feedback_closure_on_dirty_tree.md) · [clean first time](feedback_write_detekt_clean_first_time.md) · [resurface](feedback_detekt_baseline_signature_resurface.md) · [ktlint imports](project_detekt_ktlint_import_layout.md) · [line shift](feedback_detekt_scoped_gate_line_shift.md) · [untouched debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) · [ONE file](feedback_post_change_scopes_detekt_to_one_file.md) · [hand-edit ignored](project_detekt_baseline_hand_edit_daemon_stale.md) · [stale report](feedback_post_change_detekt_stale_report.md) · [exit 0 sans -Gate](feedback_assert_detekt_exit_zero_without_gate.md)
- [Debt-ticket premise decays](feedback_detekt_debt_ticket_premise_decays.md) S1328 - re-run the gate; a baseline regen voids recorded counts
- [lint tests enforce FQN resolution](project_lint_test_modes_enforce_resolution.md) · [lint baseline matches fuzzily](project_lint_baseline_matching_and_runner.md) S1195
- [Stale test-results XML](feedback_stale_test_results_xml.md) · [fu OOMs mid-run](project_unit_suite_oom_truncation.md) S1244 - verify per class
- [Prevent at source](feedback_prevent_at_source_not_just_detect.md) · [No Sxxxx in permanent logs](reference_ticket_log_gate.md) + [rule](feedback_persistent_logs_no_ticket_id.md) · [Timber.e for real errors](feedback_log_levels.md)
- [Settings docs sync Rule 22](feedback_settings_manifest_regen.md)

## Long-run correctness
- [Notification id registry](project_notification_id_registry.md) - never hardcode an id
- [Network idle-disconnect contract](project_network_idle_disconnect_contract.md) - 30s timers killed live SMB/SFTP/FTP

## UI conventions
- [Focus indicator](project_focus_frame_infra.md) · [No wrapper focus on compound rows](feedback_compound_row_no_wrapper_focus.md)
- [Bounded UI](feedback_no_edge_to_edge_ui_elements.md) + [insets](feedback_respect_system_insets_safe_bounds.md)
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) + [multi-column](feedback_landscape_multicolumn_settings.md) · [Land player bottom-band](project_land_player_bottom_band_stacking.md)
- [configChanges no recreate](project_streams_activity_config_changes_rotation.md) · [SettingsInputRow greedy width](feedback_settingsinputrow_greedy_width.md) · [Canonical settings pickers](feedback_canonical_settings_value_pickers.md)
- [Reuse existing settings](feedback_reuse_existing_settings.md) · [Settings deep-link](project_settings_section_deeplink.md) · [Settings-search gate axes](project_settings_search_gate_axes.md)
- [Resource vs Folder terms](feedback_resource_vs_folder_terminology.md) · [Write-permission gating](project_write_permission_gating.md)
- [MainActivity LOC ceiling](feedback_mainactivity_loc_ceiling.md) ~1500 · [Players are a family](feedback_player_family_glue_mirroring.md)
- [Welcome layout variants](project_welcome_layout_variants.md) - pickers live in page_welcome_enhanced · [sw beats -land](project_res_sw_qualifier_beats_land.md)
- [Main top panels width grid](project_main_top_panels_width_grid.md) · [Sync docs/site on visible change](feedback_sync_docs_on_visible_change.md)
- [Probe-measure poisons text centering](project_probe_measure_poisons_text_centering.md) S1258 - mh!=h is the smoking gun
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md) · [Play edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md)
- [Shared-layout dialogs: fix the shared layer](feedback_shared_layout_fix_at_shared_layer.md) S1095 patched one picker, S1286 paid for it

## Streams / VR / players
- [Link-download present() dead](project_link_download_present_suppressed.md) S0980 · [Ship every live channel](feedback_stream_catalog_all_live_channels.md) + [publish](reference_stream_catalog_publish.md)
- [Favicon atlas](project_stream_favicon_atlas_delivery.md) + [publish](project_stream_catalog_atlas_publish.md) - no atlas.png wipes favicons
- [Streams device-test gate](project_streams_device_test_gate.md) · [radio vs video player](project_stream_radio_vs_video_player_split.md)
- [VR inclusion hierarchy](project_vr_inclusion_hierarchy.md) - `src/vr` ships in TWO flavors · [supportsVrPlayer noLegal-only](project_supportsvrplayer_nolegal_only.md)
- ["VR" = device or flavor?](project_xr_device_guard_lives_in_main.md) - ask first; Quest sideloads standard too
- [VR immersive re-entry](project_vr_immersive_reentry_hotspot.md) + [logcat trap](reference_vr_immersive_logcat_capture_trap.md) · [HUD pitfalls](project_vr_hud_quirks.md)
- [Quest panel opaque](reference_quest_panel_not_introspectable.md) · [2 texture channels](project_vr_native_two_texture_channels.md)
- [CameraCaptureSessionManager at its function ceiling](project_camera_session_manager_function_ceiling.md) · [Camera capture permission-free](project_camera_capture_permission_constraint.md)
- [Headless capture + noHistory trap](project_headless_camera_capture_trampoline.md) · [Player progressBar owner](project_player_progressbar_single_owner.md) · [Shared-state audit tool](reference_shared_state_audit_tool.md)

## Spec lifecycle & catalog
- [Probe tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) · [Working tree is truth](feedback_dirty_tree_is_normal_wip.md) · [Verify spec id first](feedback_verify_spec_id_before_pipeline.md)
- [IDE Draft finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) · [Draft style gate](feedback_draft_style_gate.md) · [Status auto-syncs](feedback_spec_header_autosync.md)
- [Strategic spec owner gate](feedback_strategic_spec_owner_gate.md) · [spec-tech plan quality](feedback_spec_tech_plan_quality.md) · [Phase-boundary audit](feedback_phase_boundary_audit.md)
- [/spec-dev verify code first](feedback_spec_dev_continue_verify_code_first.md) · [Plan file lists can be wrong](feedback_tactical_plan_file_list_may_be_wrong.md) - locate every named file first
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) · [no fake autopilot blocker](feedback_no_safety_blocker_gating_autopilot.md)
- [Search dups by symptom](feedback_search_duplicates_by_symptom.md) · [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md)
- [Block status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) · [close.ps1 two-step](project_close_ps1_two_step_unblock.md)
- [Probe predicates grep the Timber form](feedback_probe_predicate_names_timber_form.md) - bare `Sxxxx` collides with rationale comments
- [Capability inventory](project_functionality_log.md) · [flavors from the gate](feedback_feature_record_flavors_from_gate.md) · [noLegal features](feedback_features_nolegal.md)
- [spec_catalog exit-code](project_spec_catalog_exit_code_contract.md) · [insert -File](project_insert_ps1_file_validation.md) · [Catalog scan roots](project_catalog_scan_source_sets.md)
- [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) · [-Search coverage](reference_catalog_search_coverage.md) · [Big-file decomposition](project_s0002_decomposition_toolkit.md)

## PowerShell / shell traps
- [Tool-bypass discipline](feedback_tool_bypass_discipline.md) · [PowerShell efficiency](feedback_pwsh_efficiency.md) · script params: `scripts/utils/help.ps1 -Name <script>`
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) · [Check existing tooling](feedback_check_existing_tooling.md)
- [Cyrillic bash->pwsh boundary](feedback_cyrillic_bash_pwsh_boundary.md) · [pwsh shim](reference_pwsh_shim.md) · [pwsh byte traps](feedback_pwsh_authoring_byte_traps.md)
- [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) · [param/local collision](feedback_pwsh_param_local_case_collision.md)
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) · [strings tool](reference_strings_tool.md) + [main/res only](feedback_string_tools_main_res_only.md)
- [bash rg skips CATALOG](feedback_rg_gitignore_catalog.md) · [BG task exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md)
- [Workflow journal recovery](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md)

## Subagents & verification discipline
- [Verify every variant of a screen](feedback_verify_all_variants_of_the_screen.md) · [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md)
- [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) + [no git/build](feedback_parallel_agents_no_git_build.md)
- [Concurrent /spec-all red tree](project_spec_all_concurrent_tree_red.md) - siblings take the same locks · [Frozen app? TracerPid](feedback_frozen_app_check_tracerpid.md)
- [Welcome process consolidation](feedback_welcome_process_consolidation.md) · [Workflow vs 5h limit](feedback_workflow_session_limit_budget.md) · [Don't stop a loop on a context guess](feedback_dont_stop_loop_on_context_guess.md)
- [ScheduleWakeup is /loop-only](feedback_schedulewakeup_loop_mode_only.md) - don't call it to wait on an already-tracked background task
- [Verify with full evidence](feedback_verify_full_evidence.md) · [No ellipsis edits in code spans](feedback_no_ellipsis_edits_in_verbatim_code_spans.md) · [Edit line-delete splice](feedback_edit_line_delete_splice.md)
- [Pre-S1332 log evidence is void](project_pre_s1332_log_evidence_untrustworthy.md) · [A documented invariant is a claim](feedback_documented_invariant_is_a_claim.md) S1225
- [javap the android.jar](feedback_verify_platform_api_with_javap.md) · [Verify owner's proposed mechanism](feedback_verify_owner_proposed_remedy_mechanism.md) - his fix is a hypothesis
- [Signed-off values need widget validation](feedback_owner_signed_values_still_need_widget_validation.md) · [Never pass my inference off as the owner's](feedback_never_attribute_agent_inference_to_owner.md)
