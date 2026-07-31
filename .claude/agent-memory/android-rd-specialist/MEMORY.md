# Memory Index

- [About Me](about_me.md) - solo owner, data engineer, no Kotlin · [Audience](feedback_target_audience_non_technical.md) - zero jargon
- [Argue then obey](feedback_argue_then_obey.md) + [decision after pushback](feedback_owner_decision_after_pushback.md)
- [Don't ask when architecture answers](feedback_no_owner_questions_when_architecture_already_answers.md) · [research forks](feedback_research_over_owner_question.md) · [clarify unclear framing](feedback_clarify_task_when_framing_unclear.md)
- [Timestamp every message](feedback_timestamp_in_chat.md) · [Writing style](feedback_writing_style.md) `..`/hyphen/ё, docs+UI only
- [Finish mechanical follow-ups](feedback_finish_mechanical_followups_in_context.md) · [Skill aliases](feedback_skill_aliases.md)
- [Universal Agent Kit](reference_universal_agent_kit.md) - public distillation
- [No paid/key services](feedback_no_paid_or_key_services.md) · [Weather on Open-Meteo](project_weather_gadget_open_meteo.md) S0426
- [fms_companion](project_fms_companion_subproject.md) - Go+Wails, out of repo · [FMS Windows rebrand](project_fms_windows_rebrand.md) display-only
- [.fmscfg v2 forward-compat](project_fmscfg_contract_v2_forward_compat.md) - S0988

## Devices & release
- [Test devices](reference_test_device_galaxy_s21.md) - S21+ ok, S20 FE lent, owner's phone = S25 FE · [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md)
- [Never grant system roles on owner phone](feedback_never_grant_system_roles_on_owner_phone.md)
- [setup_test_media](reference_setup_test_media.md) · [gh CLI path](reference_gh_cli_location.md) - not on PATH
- [R8 mapping is per-build](project_r8_mapping_is_per_build.md) - wrong mapping = plausible nonsense
- [Play API read-only](reference_play_console_api_access.md) - no review verdicts · [FGS precedent](project_play_release_in_review.md)
- [No coverage regression](feedback_release_no_coverage_regression.md) · [check OCR/translate versions](feedback_release_check_ocr_translate_versions.md)
- [Device reach implies screen.*](project_play_device_reach_screen_portrait.md) · [Android XR](project_android_xr_play_distribution.md)
- [/skill-release gotchas](project_skill_release_gotchas.md) · [Archive after every release](feedback_archive_after_every_release.md)
- [prerelease emulator-only](feedback_prerelease_emulator_only.md) + [Maestro flaky](project_prerelease_maestro_harness_flaky.md)
- [Emulator capture](reference_emulator_capture_family_testing.md) - never rotate the AVD, reshape it + [MediaProjection](reference_emulator_mediaprojection_capture.md)
- [Owner tests in a car head unit](project_owner_runs_app_on_car_head_unit.md) - 1024x600 @160dpi is a real target
- [Store screenshot traps](project_store_screenshot_capture_traps.md) - sibling session owns AVD geometry; test media is not store-safe
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md)
- [Emulator acceptance ceiling](feedback_emulator_acceptance_ceiling.md) - classes a sweep can never prove; ~19 of 67 blocked tickets are automatable
- [Onboarding device-test gotchas](feedback_onboarding_device_test_gotchas.md) · [Widget-only on AVD](reference_trigger_widget_only_features_on_emulator.md)
- [Emulator too fast for transfer UI](feedback_emulator_too_fast_for_transfer_ui.md) - 700 MB copies in <2 s; read datastore, not screenshots
- [Check animator scale first](feedback_check_animator_scale_before_diagnosing.md) - AVDs run scale=0; animations look broken when they aren't
- [Voice-note transcription](reference_voice_note_transcription.md) - offline faster-whisper in .venv, no key
- [Mouse-wheel injection](reference_emulator_mouse_wheel_injection.md) - API35 real ACTION_SCROLL · [Theme switch](feedback_color_theme_device_switch.md)
- [Dialogs invisible under wm override](feedback_dialogs_invisible_under_wm_override.md) S1264 - verify dialogs at native geometry; settings-pb transplant trick
- [Black screenshot = FLAG_SECURE](feedback_black_screenshot_means_flag_secure.md) S1284 - not a rendering bug; grep `SECURE` in dumpsys window flags first

## Build, flavors, gates
- [Don't release someone else's CODE.LOCK](feedback_code_lock_release_ownership.md)
- [Fast checks](feedback_fast_checks_during_dev.md) + [no redundant flavor compile](feedback_no_redundant_flavor_compile.md) · [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md)
- [Flavor isolation](feedback_flavor_isolation_strict.md) · [Don't infer arch from BuildConfig](feedback_dont_infer_from_buildconfig_names.md)
- [Push features to lowest flavor](feedback_push_features_to_lowest_flavor.md) · [legacy+photos HAVE cloud](project_flavor_matrix_cloud_correction.md)
- [Third-party branding ok](feedback_third_party_branding_not_a_blocker.md) · [photos/lite OCR src sets](project_photos_flavor_ocr_break.md)
- [S0386 native-attach broken API36](project_s0386_native_attach_broken_api36.md) · [.so bundle vs on-demand](project_native_so_bundle_standard_vs_ondemand_nolegal.md)
- [screenCapture standard vs noLegal](project_screencapture_nolegal_only.md) · [gates gesture](project_screencapture_gates_gesture_capability.md)
- [manifest.srcFile overrides flavor](project_agp_manifest_srcfile_overrides_flavor_manifest.md) · [MSAL hash per keystore](project_msal_signing_hash_per_keystore.md)
- [material-icons-extended stays](project_material_icons_extended_not_removable.md) · [Incremental phantom ref](project_incremental_build_phantom_unresolved.md) - clean build
- [Build gotchas](project_build_gotchas.md) + [output trunc](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md)
- [Gradle via PowerShell, never Bash](feedback_gradle_via_powershell_not_bash.md) - Bash JAVA_HOME points at a missing JDK; gradlew aborts pre-config
- [Unmask kapt NPE](project_kapt_npe_unmask.md) + [subagent claims](feedback_verify_subagent_build_failures.md)
- [Remove dead config too](feedback_remove_dead_applications_too.md) · [Ctor change -> compile tests](feedback_constructor_change_compile_tests.md) + [pre-existing fails](feedback_build_pre_existing_test_failures.md)
- [fk does NOT check the Hilt graph](feedback_fk_does_not_validate_hilt_graph.md) - MissingBinding hides behind two green compiles; injected collections need `@JvmSuppressWildcards`
- [Check binding field types](feedback_check_generated_binding_types.md) - Button vs MaterialButton crashes
- [No glob path in KDoc](feedback_kdoc_nested_comment_glob_path.md) - `/*` nests; error points at the class header, not the comment

## detekt / gates / logging
- [detekt gate](project_detekt_gate_in_post_change.md) + [dirty tree](feedback_detekt_gate_dirty_tree.md) + [-ScopeToFile](feedback_closure_on_dirty_tree.md)
- [lint detector tests enforce FQN resolution](project_lint_test_modes_enforce_resolution.md) - import-alias/parens modes; baselines match on message text
- [lint baseline matches messages fuzzily](project_lint_baseline_matching_and_runner.md) S1195 - exact-key pruning unhides warnings; no a.ps1 target for full lint
- [detekt-clean authoring](feedback_write_detekt_clean_first_time.md) + [baseline resurface](feedback_detekt_baseline_signature_resurface.md) + [ktlint imports](project_detekt_ktlint_import_layout.md)
- [Scoped gate flags shifted findings](feedback_detekt_scoped_gate_line_shift.md) · [surfaces untouched debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md)
- [post-change -ScopeToFile checks ONE file](feedback_post_change_scopes_detekt_to_one_file.md) - run assert-detekt -ChangedFiles over all touched files
- [Hand-edited baseline ignored](project_detekt_baseline_hand_edit_daemon_stale.md) · [stale detekt.txt](feedback_post_change_detekt_stale_report.md) - --rerun-tasks
- [assert-detekt exits 0 without -Gate](feedback_assert_detekt_exit_zero_without_gate.md) - read the verdict line, not $?
- [Stale test-results XML](feedback_stale_test_results_xml.md) - check BUILD verdict + mtime
- [fu OOMs mid-run, hides it](project_unit_suite_oom_truncation.md) S1244 - ui/domain/util never execute; verify per class
- [Prevent at source](feedback_prevent_at_source_not_just_detect.md) · [listener-symmetry per-file](project_listener_symmetry_gate_per_file.md)
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) + [rule](feedback_persistent_logs_no_ticket_id.md) · [Timber.e for real errors](feedback_log_levels.md)
- [Settings docs sync Rule 22](feedback_settings_manifest_regen.md) · [Doc-pin tooling ownership](feedback_doc_pin_tooling_ownership_split.md)

## Long-run / background correctness (audit 2026-07-30, S1291-S1310)
- [Notification id registry](project_notification_id_registry.md) - never hardcode an id; uniqueness test guards it
- [Network idle-disconnect contract](project_network_idle_disconnect_contract.md) - 30s timers killed live SMB/SFTP/FTP; heartbeat or defer

## UI conventions
- [Focus indicator](project_focus_frame_infra.md) - in-place per-view · [No wrapper focus on compound rows](feedback_compound_row_no_wrapper_focus.md)
- [Bounded UI](feedback_no_edge_to_edge_ui_elements.md) + [insets](feedback_respect_system_insets_safe_bounds.md)
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) + [multi-column](feedback_landscape_multicolumn_settings.md)
- [configChanges no recreate](project_streams_activity_config_changes_rotation.md) · [BaseActivity posts setupViews()](feedback_baseactivity_setupviews_posted_ordering.md)
- [SettingsInputRow greedy width](feedback_settingsinputrow_greedy_width.md) · [Canonical settings pickers](feedback_canonical_settings_value_pickers.md)
- [Category != icon monochrome](feedback_category_is_not_icon_monochrome_proxy.md) · [Reuse existing settings](feedback_reuse_existing_settings.md)
- [Settings deep-link](project_settings_section_deeplink.md) · [Settings-search gate axes](project_settings_search_gate_axes.md)
- [Resource vs Folder terms](feedback_resource_vs_folder_terminology.md) · [Write-permission gating](project_write_permission_gating.md)
- [MainActivity LOC ceiling](feedback_mainactivity_loc_ceiling.md) ~1500 · [Players are a family](feedback_player_family_glue_mirroring.md)
- [Welcome layout variants](project_welcome_layout_variants.md) - pickers live in page_welcome_enhanced (layout + -land), not activity_welcome · [sw beats -land](project_res_sw_qualifier_beats_land.md)
- [Main top panels width grid](project_main_top_panels_width_grid.md) · [Sync docs/site on visible change](feedback_sync_docs_on_visible_change.md)
- [Probe-measure poisons text centering](project_probe_measure_poisons_text_centering.md) S1258 - heal via posted forceLayout; mh!=h = smoking gun
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md)
- [Play edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md) · [Land player bottom-band](project_land_player_bottom_band_stacking.md)
- [Material inflate needs themed ctx](feedback_material_inflate_needs_themed_context.md)
- [Shared-layout dialogs: fix the shared layer](feedback_shared_layout_fix_at_shared_layer.md) S1095 patched one picker, S1286 paid for it

## Streams / VR / players
- [Link-download present() dead](project_link_download_present_suppressed.md) - S0980
- [Ship every live channel](feedback_stream_catalog_all_live_channels.md) + [publish](reference_stream_catalog_publish.md)
- [Favicon atlas](project_stream_favicon_atlas_delivery.md) + [publish](project_stream_catalog_atlas_publish.md) - no atlas.png wipes favicons
- [Streams device-test gate](project_streams_device_test_gate.md) · [radio vs video player](project_stream_radio_vs_video_player_split.md)
- [VR inclusion hierarchy](project_vr_inclusion_hierarchy.md) - `src/vr` ships in TWO flavors (vr owns it, noLegal borrows) · [supportsVrPlayer noLegal-only](project_supportsvrplayer_nolegal_only.md)
- ["VR" = device or flavor?](project_xr_device_guard_lives_in_main.md) - ask first; guard on `XrDeviceProbe` in src/main, Quest sideloads standard too
- [VR immersive re-entry](project_vr_immersive_reentry_hotspot.md) + [logcat trap](reference_vr_immersive_logcat_capture_trap.md)
- [Quest panel opaque](reference_quest_panel_not_introspectable.md) + [HUD pitfalls](project_vr_hud_quirks.md) · [2 texture channels](project_vr_native_two_texture_channels.md)
- [CameraCaptureSessionManager at 40-function ceiling](project_camera_session_manager_function_ceiling.md)
- [Player progressBar owner](project_player_progressbar_single_owner.md) · [Glide listener before view bind](project_glide_requestlistener_fires_before_view_bind.md)
- [Shared-state audit tool](reference_shared_state_audit_tool.md) · [Camera capture permission-free](project_camera_capture_permission_constraint.md)
- [Headless capture + noHistory trap](project_headless_camera_capture_trampoline.md)
- [Radio stutter toolkit](project_live_radio_loadcontrol_min_eq_max.md) S1148 · [Samsung Dolby eac3-joc](project_samsung_dolby_eac3_joc_glitch.md)

## Spec lifecycle & catalog
- [Launcher family state](project_launcher_roadmap_greenlit.md) - owner-driven, not /spec-next
- [Probe tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) · [Working tree is truth](feedback_dirty_tree_is_normal_wip.md)
- [Drift-check misses untagged impl](feedback_driftcheck_misses_untagged_impl.md) · [Verify spec id first](feedback_verify_spec_id_before_pipeline.md)
- [IDE Draft finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) · [Draft style gate](feedback_draft_style_gate.md) · [Status auto-syncs](feedback_spec_header_autosync.md)
- [Strategic spec owner gate](feedback_strategic_spec_owner_gate.md) · [spec-tech plan quality](feedback_spec_tech_plan_quality.md)
- [/spec-dev verify code first](feedback_spec_dev_continue_verify_code_first.md) · [Phase-boundary audit](feedback_phase_boundary_audit.md)
- [Plan file lists can be wrong](feedback_tactical_plan_file_list_may_be_wrong.md) - locate every named file before editing; amend the plan
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) · [no fake autopilot blocker](feedback_no_safety_blocker_gating_autopilot.md)
- [Search dups by symptom](feedback_search_duplicates_by_symptom.md) · [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md)
- [Block status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) · [close.ps1 two-step](project_close_ps1_two_step_unblock.md)
- [Capability inventory](project_functionality_log.md) · [flavors from the gate](feedback_feature_record_flavors_from_gate.md) · [noLegal features](feedback_features_nolegal.md)
- [spec_catalog exit-code](project_spec_catalog_exit_code_contract.md) · [insert -File](project_insert_ps1_file_validation.md)
- [Catalog scan roots](project_catalog_scan_source_sets.md) · [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) · [-Search coverage](reference_catalog_search_coverage.md)
- [Big-file decomposition](project_s0002_decomposition_toolkit.md)

## PowerShell / shell traps
- [Tool-bypass discipline](feedback_tool_bypass_discipline.md) · [Script param cheatsheet](reference_script_help_cheatsheet.md) - help.ps1 -Name
- [PowerShell efficiency](feedback_pwsh_efficiency.md) · [LOC undercounts](feedback_pwsh_loc_measure_object.md) - use (Get-Content).Count
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) · [Check existing tooling](feedback_check_existing_tooling.md)
- [Cyrillic bash->pwsh boundary](feedback_cyrillic_bash_pwsh_boundary.md) · [pwsh shim](reference_pwsh_shim.md)
- [pwsh byte traps](feedback_pwsh_authoring_byte_traps.md) + [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) · [param/local collision](feedback_pwsh_param_local_case_collision.md)
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) · [strings tool](reference_strings_tool.md) + [main/res only](feedback_string_tools_main_res_only.md)
- [bash rg skips CATALOG](feedback_rg_gitignore_catalog.md) · [BG task exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md)
- [Workflow journal recovery](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md)

## Subagents & process
- [/spec-test-device subagent commits tree](feedback_spec_test_device_subagent_commits_tree.md) - runs a.ps1 c despite "don't touch git"
- [Verify every variant of a screen](feedback_verify_all_variants_of_the_screen.md)
- [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md) · [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) + [no git/build](feedback_parallel_agents_no_git_build.md)
- [Concurrent /spec-all red tree](project_spec_all_concurrent_tree_red.md) - sibling sessions also take CODE.LOCK/BUILD.LOCK · [Frozen app? TracerPid](feedback_frozen_app_check_tracerpid.md)
- [Welcome process consolidation](feedback_welcome_process_consolidation.md) · [Workflow vs 5h limit](feedback_workflow_session_limit_budget.md)
- [Don't stop a loop on a context guess](feedback_dont_stop_loop_on_context_guess.md)
- [Verify with full evidence](feedback_verify_full_evidence.md) · [No ellipsis edits in code spans](feedback_no_ellipsis_edits_in_verbatim_code_spans.md)
- [javap the android.jar](feedback_verify_platform_api_with_javap.md) - confirm a platform API exists before speccing around it
- [Verify owner's proposed mechanism](feedback_verify_owner_proposed_remedy_mechanism.md) - symptom is fact, his fix is a hypothesis; read the full transcript
- [Signed-off values still need widget validation](feedback_owner_signed_values_still_need_widget_validation.md) - slider step / options list before writing preset data
- [Never pass my inference off as the owner's complaint](feedback_never_attribute_agent_inference_to_owner.md) - it propagates across specs
- [Edit line-delete splice](feedback_edit_line_delete_splice.md) - match full next line
