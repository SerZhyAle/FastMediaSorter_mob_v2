# Memory Index

Pointers only; billed every turn. `assert-memory-budget.ps1` caps it.

- [About Me](about_me.md) - solo owner, no Kotlin · [Audience](feedback_target_audience_non_technical.md) - zero jargon
- [Argue then obey](feedback_argue_then_obey.md) + [after pushback](feedback_owner_decision_after_pushback.md)
- [Architecture answers it](feedback_no_owner_questions_when_architecture_already_answers.md) · [research forks](feedback_research_over_owner_question.md) · [clarify framing](feedback_clarify_task_when_framing_unclear.md)
- [Finish follow-ups](feedback_finish_mechanical_followups_in_context.md) · [Skill aliases](feedback_skill_aliases.md) · [/quick+Sxxxx still closes](feedback_skill_fix_with_ticket_id_still_closes_ticket.md) - skip-list is id-less only
- [Agent Kit](reference_universal_agent_kit.md) · [No paid services](feedback_no_paid_or_key_services.md) · [Open-Meteo](project_weather_gadget_open_meteo.md)
- [fms_companion](project_fms_companion_subproject.md) - Go+Wails, out of repo · [Windows rebrand](project_fms_windows_rebrand.md)
- [Process audit](project_process_audit_2026_07.md) · [Mining](reference_transcript_cost_mining.md)

## Devices & release
- [Devices](reference_test_device_galaxy_s21.md) - phones + Galaxy Watch 7 (Wi-Fi only) · [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md)
- [No system roles on owner phones](feedback_never_grant_system_roles_on_owner_phone.md) · [test media](reference_setup_test_media.md) · [gh CLI](reference_gh_cli_location.md)
- [am start refused for non-exported](feedback_am_start_refused_for_non_exported.md) - drive the real trigger
- [logcat -d wraps in ~2 min](feedback_logcat_dump_wraps_before_you_read_it.md) - stream to a file
- Second-level index: [release, Play Console, store listings](INDEX_release.md) - open it only in a release flow.

## Emulator & device testing
- [Capture](reference_emulator_capture_family_testing.md) - reshape, never rotate + [MediaProjection](reference_emulator_mediaprojection_capture.md)
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md)
- [Stylus overlay eats typed text](feedback_stylus_overlay_eats_typed_text.md) - tree lies
- [Maestro needs ru app locale](feedback_maestro_suite_needs_ru_app_locale.md) - log mojibake is display-only + [flow traps](feedback_maestro_flow_authoring_traps.md) - ASCII-only inputText
- [Acceptance ceiling](feedback_emulator_acceptance_ceiling.md) · [False negatives](feedback_avd_evidence_traps_width_and_logs.md)
- [Onboarding](feedback_onboarding_device_test_gotchas.md) · [Widget-only](reference_trigger_widget_only_features_on_emulator.md) · [Too fast for transfer UI](feedback_emulator_too_fast_for_transfer_ui.md)
- [Reset settings, keep onboarding](feedback_reset_appsettings_without_onboarding.md)
- [Launcher desktop](feedback_launcher_desktop_device_test_setup.md) - enabledComponents; am start needs no HOME role · [Theme switch](feedback_color_theme_device_switch.md)
- [Animator scale first](feedback_check_animator_scale_before_diagnosing.md) - AVDs run scale=0 · [Dialogs under wm](feedback_dialogs_invisible_under_wm_override.md)

## Build, flavors, gates
- [Not your CODE.LOCK](feedback_code_lock_release_ownership.md) · [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md)
- [Don't idle on a lock](feedback_do_not_idle_on_a_lock.md) · [Lock per step, not ticket](feedback_code_lock_is_per_step_not_per_ticket.md)
- [Lock denial doesn't stop the batch](feedback_code_lock_denial_does_not_stop_the_batch.md)
- [agent-lock has no CLI](project_agent_lock_release_lies.md) - use `exit-code-lock.ps1`
- [No redundant flavor compile](feedback_no_redundant_flavor_compile.md) · [BuildConfig names lie](feedback_dont_infer_from_buildconfig_names.md) · [Lowest flavor wins](feedback_push_features_to_lowest_flavor.md)
- [Capability != Availability](project_flavor_flags_ratchet_blocks_capability_availability.md) - copy LauncherModeContract
- [Flavor grid generated](project_flavor_matrix_cloud_correction.md) · [photos/lite OCR](project_photos_flavor_ocr_break.md)
- [S0386 native-attach API36](project_s0386_native_attach_broken_api36.md) · [.so bundle vs on-demand](project_native_so_bundle_standard_vs_ondemand_nolegal.md)
- [screenCapture ships in standard too](project_screencapture_nolegal_only.md) - filename lies · [gates gesture](project_screencapture_gates_gesture_capability.md)
- [manifest.srcFile wins](project_agp_manifest_srcfile_overrides_flavor_manifest.md) · [MSAL hash per keystore](project_msal_signing_hash_per_keystore.md)
- [material-icons stays](project_material_icons_extended_not_removable.md) · [Phantom unresolved ref](project_incremental_build_phantom_unresolved.md)
- [Build gotchas](project_build_gotchas.md) + [no redirect in pwsh tool](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md)
- [Gradle via PowerShell](feedback_gradle_via_powershell_not_bash.md)
- [Unmask kapt NPE](project_kapt_npe_unmask.md) + [subagent claims](feedback_verify_subagent_build_failures.md) · [Remove dead config](feedback_remove_dead_applications_too.md)
- [Ctor change -> compile tests](feedback_constructor_change_compile_tests.md) + [pre-existing fails](feedback_build_pre_existing_test_failures.md)
- [`$stable` breaks field reflection](feedback_compose_stable_field_in_reflection_tests.md)
- [Sandbox tests copy libs by name](feedback_sandbox_tests_carry_a_dependency_manifest.md) - new dot-source = all cases fail at load
- [fk misses the Hilt graph](feedback_fk_does_not_validate_hilt_graph.md) - MissingBinding hides

## detekt / lint / gates / logging
- **OPEN BEFORE WRITING ANY KOTLIN:** [detekt-clean first time](feedback_write_detekt_clean_first_time.md) - ReturnCount>2, MagicNumber, >120-char lines, brace-less if/else.
- detekt, by symptom: [gate](project_detekt_gate_in_post_change.md) · [dirty](feedback_detekt_gate_dirty_tree.md) · [scope](feedback_closure_on_dirty_tree.md) · [resurface](feedback_detekt_baseline_signature_resurface.md) · [imports](project_detekt_ktlint_import_layout.md) · [shift](feedback_detekt_scoped_gate_line_shift.md) · [debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) · [1 file](feedback_post_change_scopes_detekt_to_one_file.md) · [hand-edit](project_detekt_baseline_hand_edit_daemon_stale.md) · [stale](feedback_post_change_detekt_stale_report.md) · [no -Gate](feedback_assert_detekt_exit_zero_without_gate.md)
- [Coroutine wrap trips swallowed-cancellation](feedback_wrapping_code_in_coroutine_trips_swallowed_cancellation.md)
- [Debt premise decays](feedback_detekt_debt_ticket_premise_decays.md) · [lint needs FQN](project_lint_test_modes_enforce_resolution.md) · [lint baseline fuzzy](project_lint_baseline_matching_and_runner.md)
- [Stale test XML](feedback_stale_test_results_xml.md) · [fu OOMs mid-run](project_unit_suite_oom_truncation.md) - verify per class
- [FAIL may mean never ran](feedback_gate_fail_may_mean_never_ran.md) - read the XML
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) · [Timber.e for real errors](feedback_log_levels.md)
- [Settings docs Rule 22](feedback_settings_manifest_regen.md) · [Kotlin skips doc-pin](feedback_post_change_kotlin_skips_doc_pin_gate.md)
- [-Files: whole set](feedback_post_change_dev_log_first_file_only.md) · [Gate cost](reference_gate_cost_mining.md) - detekt 86%; ms includes lock wait
- [-RegistryAck up front](feedback_registry_ack_up_front.md) - `.claude/**` = repository-rules; regen the cheatsheet first
- [Discount shrinks the imbalance, not the add](feedback_discount_must_shrink_not_subtract.md)

## Long-run correctness
- [Radio toggles: firmware](project_radio_toggle_restriction_is_firmware_not_targetsdk.md) - direct path works on 8
- [EPUB asset-URL error is noise](project_epub_asset_handler_log_noise.md) - interception serves it 2 ms later
- [Notification id registry](project_notification_id_registry.md) · [Idle-disconnect](project_network_idle_disconnect_contract.md) - 30s timers killed live links

## UI conventions
- [Focus indicator](project_focus_frame_infra.md) · [No wrapper focus on rows](feedback_compound_row_no_wrapper_focus.md)
- [Trampolines not Rule 3 exempt](feedback_trampolines_are_not_rule3_exempt.md) - only post-change sees it
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) + [multi-column](feedback_landscape_multicolumn_settings.md) · [Land player band](project_land_player_bottom_band_stacking.md)
- [configChanges no recreate](project_streams_activity_config_changes_rotation.md) · [InputRow greedy width](feedback_settingsinputrow_greedy_width.md) · [Canonical pickers](feedback_canonical_settings_value_pickers.md)
- [Reuse settings](feedback_reuse_existing_settings.md) · [Deep-link](project_settings_section_deeplink.md) · [Search gate axes](project_settings_search_gate_axes.md)
- [Resource vs Folder](feedback_resource_vs_folder_terminology.md) · [Write-permission gating](project_write_permission_gating.md)
- [Players are a family](feedback_player_family_glue_mirroring.md) · [App self-pins shortcuts](project_app_self_pin_tests_launcher_pin_host.md)
- [Welcome variants](project_welcome_layout_variants.md) · [sw beats -land](project_res_sw_qualifier_beats_land.md)
- [ALL layout variants](feedback_enumerate_all_layout_variants_not_just_land.md) - miss = null binding
- [Top panels width grid](project_main_top_panels_width_grid.md) · [Sync docs on visible change](feedback_sync_docs_on_visible_change.md)
- [Probe-measure breaks centering](project_probe_measure_poisons_text_centering.md)
- [Shrinking card scales, never hides](feedback_shrinking_card_scales_never_hides.md) - and the format never changes by size
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md) · [edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md)
- [Shared layout: fix shared layer](feedback_shared_layout_fix_at_shared_layer.md)

## Wear OS
- [Data Layer never delivered](project_wear_data_layer_applicationid_mismatch.md) - phone/watch applicationId differ; S1631 untestable until fixed

## Brand
- [Waves and particles](project_brand_visual_waves_and_particles.md) - the signature visual, shared with the site; NOT the equalizer bars

## Streams / VR / players
- Second-level index: [streams, VR/XR, camera, player family](INDEX_streams_vr.md) - open it when the task touches any of those; nothing else needs them.

## Spec lifecycle & catalog
- [Rework = new ticket](feedback_new_ticket_not_reopen_for_rework.md) - never reopen a shipped one; old id is context only
- [Unblocks at BlockNeedUserTest](feedback_blocker_unblocks_at_needusertest.md) - don't wait for Verified
- [Tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) · [Verify spec id first](feedback_verify_spec_id_before_pipeline.md)
- [DRIFT from a commit mention](feedback_drift_check_false_positive_on_commit_mention.md) - 0 markers = not done
- [drift-check reads .kt only](feedback_drift_check_scans_kotlin_only.md) - XML-only fix looks CLEAN; grep layouts
- [IDE finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) · [Draft style gate](feedback_draft_style_gate.md) · [Status auto-syncs](feedback_spec_header_autosync.md)
- [§0 alone fails the gate](feedback_owner_inputs_gate_needs_section_33.md) - check-owner-inputs reads only §3.3; add it alongside
- [Owner gate](feedback_strategic_spec_owner_gate.md) · [§3.3 ≠ owner ruling](feedback_spec_tech_ui_placement_refusal.md) · [plan quality](feedback_spec_tech_plan_quality.md) · [Phase audit](feedback_phase_boundary_audit.md)
- [Verify code first](feedback_spec_dev_continue_verify_code_first.md) · [Plan file lists can be wrong](feedback_tactical_plan_file_list_may_be_wrong.md)
- [Verify a capture's claims](feedback_old_capture_may_be_superseded.md) - age is not the tell · [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md)
- [Superseded phases: delete, don't tick](feedback_delete_superseded_phase_files_not_tick.md) - /spec-dev would undo it
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) · [no fake autopilot blocker](feedback_no_safety_blocker_gating_autopilot.md)
- [No quotes in -StatusNote](feedback_status_note_quotes_corrupt_catalog.md)
- [Status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) · [close.ps1 2-step](project_close_ps1_two_step_unblock.md)
- [Predicates grep Timber form](feedback_probe_predicate_names_timber_form.md) · [Zero-hit predicate](feedback_zero_hit_predicate_cannot_name_the_literal.md)
- [Busy = lease, not status](feedback_ticket_busyness_is_a_lease_not_a_status.md) + [queue driver mute in Stage 0](project_spec_all_queue_driver_stage0_silence.md)
- [Long gap voids the round](feedback_long_gap_invalidates_round_state.md)
- [Capability inventory](project_functionality_log.md) · [flavors from the gate](feedback_feature_record_flavors_from_gate.md) · [noLegal features](feedback_features_nolegal.md)
- [Owner translates in bulk](project_owner_external_translation_route.md) - never hand-translate the ten
- [exit codes](project_spec_catalog_exit_code_contract.md) · [delete is soft](project_spec_catalog_delete_is_soft_and_ids_burn.md) · [insert -File](project_insert_ps1_file_validation.md) · [Scan roots](project_catalog_scan_source_sets.md) · [Dedup: 1 word](feedback_spec_dedup_query_shape.md)
- [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) · [-Search coverage](reference_catalog_search_coverage.md) · [Big-file decomposition](project_s0002_decomposition_toolkit.md)

## PowerShell / shell traps
- [Tool-bypass discipline](feedback_tool_bypass_discipline.md) · script params: `scripts/utils/help.ps1 -Name <script>`
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) · [Check existing tooling](feedback_check_existing_tooling.md)
- [Cyrillic bash->pwsh](feedback_cyrillic_bash_pwsh_boundary.md) · [pwsh shim](reference_pwsh_shim.md) · [byte traps](feedback_pwsh_authoring_byte_traps.md)
- [Bash `cd` leaks CWD](feedback_bash_cd_leaks_into_powershell_cwd.md)
- [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) · [param/local collision](feedback_pwsh_param_local_case_collision.md)
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) · [strings tool](reference_strings_tool.md) + [main/res only](feedback_string_tools_main_res_only.md)
- [Grep mangles `//`](feedback_grep_output_mangles_comment_markers.md)
- [rg skips CATALOG](feedback_rg_gitignore_catalog.md) · [BG exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md)
- [`Select -First N` detaches a running script](feedback_select_first_detaches_running_script.md) - state read next is mid-write
- [$LASTEXITCODE guard after a cmdlet](feedback_lastexitcode_null_after_cmdlet.md)
- [Backgroundable script -> scripts/utils](feedback_background_waiter_must_not_live_in_spec_catalog.md) - the hook matches the directory
- [Workflow journal](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md)

## Subagents & verification discipline
- [Verify every variant](feedback_verify_all_variants_of_the_screen.md) · [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md)
- [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) + [no git/build](feedback_parallel_agents_no_git_build.md)
- [Concurrent red tree](project_spec_all_concurrent_tree_red.md) · [Frozen? TracerPid](feedback_frozen_app_check_tracerpid.md)
- [Silent != stuck](feedback_silent_subagent_is_not_stuck.md) · [Researcher may lack web tools](feedback_research_subagent_may_lack_web_tools.md) - measure instead
- [Welcome consolidation](feedback_welcome_process_consolidation.md) · [5h limit](feedback_workflow_session_limit_budget.md) · [Don't stop on a guess](feedback_dont_stop_loop_on_context_guess.md)
- [ScheduleWakeup is /loop-only](feedback_schedulewakeup_loop_mode_only.md) · [Never style-edit a spec](feedback_no_ellipsis_edits_in_verbatim_code_spans.md) · [Edit splice](feedback_edit_line_delete_splice.md)
- [An invariant is a claim](feedback_documented_invariant_is_a_claim.md) · [Resolved may be inference](feedback_resolved_research_item_may_be_inference.md) - re-measure before planning
- [Audit the fixes too](feedback_audit_fixes_need_their_own_round.md)
- [javap the android.jar](feedback_verify_platform_api_with_javap.md) · [Verify owner's mechanism](feedback_verify_owner_proposed_remedy_mechanism.md)
- [Signed-off needs widget check](feedback_owner_signed_values_still_need_widget_validation.md) · [Never pass inference as owner's](feedback_never_attribute_agent_inference_to_owner.md)
