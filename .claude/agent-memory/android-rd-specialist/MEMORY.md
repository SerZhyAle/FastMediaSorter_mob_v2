# Memory Index

Pointers only; billed every turn. `assert-memory-budget.ps1` caps it.

- [About Me](about_me.md) - solo owner · [Audience](feedback_target_audience_non_technical.md) - zero jargon
- [Argue then obey](feedback_argue_then_obey.md) + [after pushback](feedback_owner_decision_after_pushback.md) · [Setting cant stop = defect](feedback_setting_that_cannot_stop_the_behaviour_is_a_defect.md)
- [Architecture answers](feedback_no_owner_questions_when_architecture_already_answers.md) · [research forks](feedback_research_over_owner_question.md) · [clarify framing](feedback_clarify_task_when_framing_unclear.md)
- [Measure throughput before "won't fit"](feedback_measure_throughput_before_calling_a_plan_infeasible.md) - ~16-20 tickets/day
- [Finish follow-ups](feedback_finish_mechanical_followups_in_context.md) · [Skill aliases](feedback_skill_aliases.md) · [/quick+Sxxxx closes](feedback_skill_fix_with_ticket_id_still_closes_ticket.md)
- [Agent Kit](reference_universal_agent_kit.md) · [No paid services](feedback_no_paid_or_key_services.md) · [Open-Meteo](project_weather_gadget_open_meteo.md)
- [fms_companion](project_fms_companion_subproject.md) - Go+Wails · [Windows rebrand](project_fms_windows_rebrand.md)
- [Canon repo is P:\WEB](project_canon_stamp_ahead_and_propagation.md) - canon fix is BlockExternal from here
- [Process audit](project_process_audit_2026_07.md) · [Mining](reference_transcript_cost_mining.md)

## Devices & release
- [Devices](reference_test_device_galaxy_s21.md) - phones + Watch 7 · [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md)
- [No system roles on owner phone](feedback_never_grant_system_roles_on_owner_phone.md) · [test media](reference_setup_test_media.md) · [gh CLI](reference_gh_cli_location.md)
- [Old mapping](reference_play_console_mapping_recovery.md) - Play never returns it; unzip AAB
- [am start refused for non-exported](feedback_am_start_refused_for_non_exported.md) · [logcat wraps in 2 min](feedback_logcat_dump_wraps_before_you_read_it.md)
- Second-level index: [release, Play Console, store listings](INDEX_release.md) - open only in release flow.

## Emulator & device testing
- Second-level index: [emulator, AVD sweeps, Maestro, UI capture](INDEX_emulator_testing.md)

## Build, flavors, gates
- [Not your CODE.LOCK](feedback_code_lock_release_ownership.md) · [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md) · [Don't idle on a lock](feedback_do_not_idle_on_a_lock.md)
- [Lock per step, not ticket](feedback_code_lock_is_per_step_not_per_ticket.md) · [Never batch lock with edit](feedback_never_batch_code_lock_with_the_edit.md) · [Lock denial](feedback_code_lock_denial_does_not_stop_the_batch.md)
- [agent-lock has no CLI](project_agent_lock_release_lies.md) - use `exit-code-lock.ps1`
- [No redundant flavor compile](feedback_no_redundant_flavor_compile.md) · [BuildConfig names](feedback_dont_infer_from_buildconfig_names.md) · [Lowest flavor wins](feedback_push_features_to_lowest_flavor.md)
- [Capability != Availability](project_flavor_flags_ratchet_blocks_capability_availability.md) · [Flavor grid](project_flavor_matrix_cloud_correction.md) · [photos/lite OCR](project_photos_flavor_ocr_break.md)
- [S0386 native-attach API36](project_s0386_native_attach_broken_api36.md) · [.so bundle vs on-demand](project_native_so_bundle_standard_vs_ondemand_nolegal.md)
- [screenCapture standard too](project_screencapture_nolegal_only.md) · [gates gesture](project_screencapture_gates_gesture_capability.md)
- [manifest.srcFile wins](project_agp_manifest_srcfile_overrides_flavor_manifest.md) · [MSAL hash](project_msal_signing_hash_per_keystore.md) · [material-icons](project_material_icons_extended_not_removable.md)
- [Deleted resource keeps shipping](project_stale_merged_resource_outlives_its_source.md) - orphan .flat beats the current layout
- [R8 mapping is 174 MB](project_r8_mapping_is_174mb.md) · [fc after resource not fast](feedback_fc_after_a_resource_change_is_not_a_fast_check.md)
- [Build gotchas](project_build_gotchas.md) + [pipes hide exit](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md) · [Gradle via pwsh](feedback_gradle_via_powershell_not_bash.md)
- [Unmask kapt NPE](project_kapt_npe_unmask.md) · [subagent claims](feedback_verify_subagent_build_failures.md) · [Remove dead config](feedback_remove_dead_applications_too.md)
- [Ctor change -> tests](feedback_constructor_change_compile_tests.md) + [pre-existing](feedback_build_pre_existing_test_failures.md) · [Flavor test set](feedback_flavor_only_code_needs_its_own_test_set.md)
- [`$stable` reflection](feedback_compose_stable_field_in_reflection_tests.md) · [Sandbox tests](feedback_sandbox_tests_carry_a_dependency_manifest.md) · [fk misses Hilt](feedback_fk_does_not_validate_hilt_graph.md)

## detekt / lint / gates / logging
- **OPEN BEFORE WRITING ANY KOTLIN:** [detekt-clean first time](feedback_write_detekt_clean_first_time.md) - ReturnCount>2, MagicNumber, >120-char lines, brace-less if/else.
- detekt, by symptom: [gate](project_detekt_gate_in_post_change.md) · [dirty](feedback_detekt_gate_dirty_tree.md) · [scope](feedback_closure_on_dirty_tree.md) · [resurface](feedback_detekt_baseline_signature_resurface.md) · [imports](project_detekt_ktlint_import_layout.md) · [shift](feedback_detekt_scoped_gate_line_shift.md) · [debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) · [1 file](feedback_post_change_scopes_detekt_to_one_file.md) · [hand-edit](project_detekt_baseline_hand_edit_daemon_stale.md) · [stale](feedback_post_change_detekt_stale_report.md) · [no -Gate](feedback_assert_detekt_exit_zero_without_gate.md)
- [Coroutine wrap trips swallowed-cancellation](feedback_wrapping_code_in_coroutine_trips_swallowed_cancellation.md)
- [Debt premise decays](feedback_detekt_debt_ticket_premise_decays.md) · [lint needs FQN](project_lint_test_modes_enforce_resolution.md) · [lint baseline fuzzy](project_lint_baseline_matching_and_runner.md)
- [Stale test XML](feedback_stale_test_results_xml.md) · [fu OOMs mid-run](project_unit_suite_oom_truncation.md) - verify per class
- [FAIL may mean never ran](feedback_gate_fail_may_mean_never_ran.md) - read the XML · [Ratchets never rise](feedback_ratchet_never_raises.md)
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) · [Timber.e for real errors](feedback_log_levels.md)
- [ticket-log audit stale after a status flip](feedback_ticket_log_audit_stale_right_after_status_flip.md) - re-run, don't add a second probe
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
- [Shrinking card scales, never hides](feedback_shrinking_card_scales_never_hides.md)
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md) · [edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md)
- [Shared layout: fix shared layer](feedback_shared_layout_fix_at_shared_layer.md)

## Wear OS
- Second-level index: [wear build/launch traps, rotation, Data Layer ids, locked watch, Play](INDEX_wear.md) - open when the task touches the watch.

## OCR
- [Overlay accuracy exchange](reference_ocr_overlay_exchange.md) - three-sided; the measured constants live in two repos outside this one

## Brand
- [Waves and particles](project_brand_visual_waves_and_particles.md) - the signature visual, shared with the site; NOT the equalizer bars

## Streams / VR / players
- Second-level index: [streams, VR/XR, camera, player family](INDEX_streams_vr.md) - open it when the task touches any of those; nothing else needs them.

## Spec lifecycle & catalog
- Second-level index: [spec lifecycle, catalog scripts, planning rules](INDEX_spec_lifecycle.md) - open when authoring, updating or closing specs.

## PowerShell / shell traps
- Second-level index: [PowerShell, shell scripting, CLI tools & traps](INDEX_pwsh_traps.md) - open when writing scripts or debugging pwsh/bash commands.

## Parallel sessions
- [Re-claim the lease every phase](feedback_reclaim_ticket_lease_every_phase.md) - it expires mid-run; a free lease + a CODE.LOCK naming that ticket = live sibling

## Subagents & verification discipline
- [Verify every variant](feedback_verify_all_variants_of_the_screen.md) · [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md)
- [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) + [no git/build](feedback_parallel_agents_no_git_build.md)
- [Concurrent red tree](project_spec_all_concurrent_tree_red.md) · [Frozen? TracerPid](feedback_frozen_app_check_tracerpid.md)
- [Never tap a device an agent holds](feedback_never_drive_a_device_an_agent_holds.md) - read-only probes yes, input no
- [Silent != stuck](feedback_silent_subagent_is_not_stuck.md) · [Researcher may lack web tools](feedback_research_subagent_may_lack_web_tools.md)
- [Welcome consolidation](feedback_welcome_process_consolidation.md) · [5h limit](feedback_workflow_session_limit_budget.md) · [Don't stop on a guess](feedback_dont_stop_loop_on_context_guess.md)
- [ScheduleWakeup is /loop-only](feedback_schedulewakeup_loop_mode_only.md) · [Never style-edit a spec](feedback_no_ellipsis_edits_in_verbatim_code_spans.md) · [Edit splice](feedback_edit_line_delete_splice.md)
- [python heredoc eats `\a`/`\b`](feedback_python_heredoc_eats_backslash_escapes.md) - writes BEL/backspace into specs; build from chr(92)
- [An invariant is a claim](feedback_documented_invariant_is_a_claim.md) · [Resolved may be inference](feedback_resolved_research_item_may_be_inference.md)
- [Visibility != action](feedback_visibility_condition_is_not_the_action.md)
- [Audit the fixes too](feedback_audit_fixes_need_their_own_round.md) · [Read the screenshot yourself](feedback_subagent_pixel_measurements_unreliable.md)
- [javap the android.jar](feedback_verify_platform_api_with_javap.md) · [Verify owner's mechanism](feedback_verify_owner_proposed_remedy_mechanism.md)
- [Signed-off needs widget check](feedback_owner_signed_values_still_need_widget_validation.md) · [Never pass inference as owner's](feedback_never_attribute_agent_inference_to_owner.md)
