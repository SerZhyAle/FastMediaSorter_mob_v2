# Memory Index

Pointers only; billed every turn. `assert-memory-budget.ps1` caps it.
Over budget? Split a section into `INDEX_*.md` - never cut a hook short: [how](feedback_compress_memory_index_by_splitting_not_truncating.md)

- [About Me](about_me.md) - solo owner · [Audience](feedback_target_audience_non_technical.md) - zero jargon
- [Argue then obey](feedback_argue_then_obey.md) + [after pushback](feedback_owner_decision_after_pushback.md) · [Setting cant stop = defect](feedback_setting_that_cannot_stop_the_behaviour_is_a_defect.md)
- [Check the main app before writing a Non-goal](feedback_check_main_app_before_declaring_a_non_goal.md) - a gate on one path is not a product limit
- [Arch answers](feedback_no_owner_questions_when_architecture_already_answers.md) · [research forks](feedback_research_over_owner_question.md) · [clarify framing](feedback_clarify_task_when_framing_unclear.md)
- [Never ask about ticket bookkeeping](feedback_never_ask_owner_about_ticket_bookkeeping.md) - owner cares the work is in the current package, not a status
- [Measure throughput first](feedback_measure_throughput_before_calling_a_plan_infeasible.md) - measure before calling a plan infeasible; ~16-20/day
- [Finish follow-ups](feedback_finish_mechanical_followups_in_context.md) · [Aliases are real files](feedback_skill_aliases.md) - generated, never hand-written · [/quick+Sxxxx closes](feedback_skill_fix_with_ticket_id_still_closes_ticket.md)
- [Cyrillic command names are a bet](project_cyrillic_command_name_bet.md) - undocumented upstream; -Prune -Sets latin if they stop routing
- [Agent Kit](reference_universal_agent_kit.md) · [No paid services](feedback_no_paid_or_key_services.md) · [Open-Meteo](project_weather_gadget_open_meteo.md)
- [fms_companion](project_fms_companion_subproject.md) - Go+Wails desktop companion · [Windows rebrand](project_fms_windows_rebrand.md)
- [Canon repo is P:\WEB](project_canon_stamp_ahead_and_propagation.md) - a canon fix belongs there, not here
- [Process audit](project_process_audit_2026_07.md) · [Mining](reference_transcript_cost_mining.md)

## Devices & release
- [Devices](reference_test_device_galaxy_s21.md) - owner's phones + Galaxy Watch 7
- [No system roles on owner phone](feedback_never_grant_system_roles_on_owner_phone.md) · [test media](reference_setup_test_media.md) · [gh CLI](reference_gh_cli_location.md)
- [Old mapping](reference_play_console_mapping_recovery.md) - Play never returns an uploaded mapping; unzip the AAB
- [am start refused for non-exported](feedback_am_start_refused_for_non_exported.md) - reach it by action via exported MainActivity · [logcat wraps in 2 min](feedback_logcat_dump_wraps_before_you_read_it.md)
- Second-level index: [release, Play Console, store listings](INDEX_release.md) - open in the release flow.

## Emulator & device testing
- Second-level index: [emulator, AVD sweeps, Maestro, UI capture](INDEX_emulator_testing.md)
- [sza_resources.xml missing here](project_sza_resources_absent_in_this_checkout.md) - prerelease configure dies; check the app locale before blaming it

## Build, flavors, gates
- Second-level index: [build, flavors, packaging, gradle locks, kapt, test sets](INDEX_build_flavors.md) - open when the task builds, packages or changes a flavor.
- [A local build never proves a file is committed](feedback_release_worktree_first_catches_untracked_source.md) - the release worktree is what catches an ignored source

## detekt / lint / gates / logging
- **OPEN BEFORE WRITING ANY KOTLIN:** [detekt-clean first time](feedback_write_detekt_clean_first_time.md) - ReturnCount>2, MagicNumber, 120-char lines, brace-less if/else.
- detekt, by symptom: [gate](project_detekt_gate_in_post_change.md) · [dirty](feedback_detekt_gate_dirty_tree.md) · [scope](feedback_closure_on_dirty_tree.md) · [resurface](feedback_detekt_baseline_signature_resurface.md) · [imports](project_detekt_ktlint_import_layout.md) · [sort imports](feedback_ktlint_import_order_sort_dont_guess.md) · [shift](feedback_detekt_scoped_gate_line_shift.md) · [debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) · [1 file](feedback_post_change_scopes_detekt_to_one_file.md) · [hand-edit](project_detekt_baseline_hand_edit_daemon_stale.md) · [stale](feedback_post_change_detekt_stale_report.md) · [no -Gate](feedback_assert_detekt_exit_zero_without_gate.md)
- [Coroutine wrap trips swallowed-cancellation](feedback_wrapping_code_in_coroutine_trips_swallowed_cancellation.md)
- [Moving code re-fires the ratchets](feedback_moving_code_resurfaces_ratchet_findings.md) - an extraction reads as a birth; pass the flavor boolean, don't move the flag
- [Unwired != ungated](feedback_a_gate_can_exist_and_never_be_wired.md) - check the umbrella's dimensions both ways · [Debt premise decays](feedback_detekt_debt_ticket_premise_decays.md) · [lint needs FQN](project_lint_test_modes_enforce_resolution.md) · [lint baseline fuzzy](project_lint_baseline_matching_and_runner.md)
- [Stale test XML](feedback_stale_test_results_xml.md) - a `-Tests` run's report dir varies by module; read the path the runner prints · [fu OOMs mid-run](project_unit_suite_oom_truncation.md)
- [FAIL may mean never ran](feedback_gate_fail_may_mean_never_ran.md) - read the XML before believing it
- [A piped gate reports tail's exit code](feedback_piping_a_gate_through_tail_masks_its_exit_code.md) - a red suite arrives as "exit 0"; redirect, don't pipe
- [Ask what state a green check read](feedback_ask_what_state_a_green_check_read.md) - a check that observed nothing passes like one that observed success
- [A PASS that observed nothing](feedback_a_pass_that_observed_nothing.md) - verify the preconditions existed before believing a green acceptance line
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) · [Timber.e for real errors](feedback_log_levels.md)
- [ticket-log audit stale after a status flip](feedback_ticket_log_audit_stale_right_after_status_flip.md) - re-run it; don't add a second probe
- [Probe must be one line](feedback_debug_probe_must_be_one_line.md) - a wrapped Timber.d( hides it from the removal grep
- [Probe vs 120 chars](feedback_probe_tag_collides_with_detekt_line_length.md) - one line, under 120 chars, never wrapped
- [No-Kotlin ticket can't be BlockNeedUserTest](feedback_no_kotlin_ticket_cannot_end_at_blockneedusertest.md) - no flow entry to host the probe; shoot evidence, close Implemented
- [Settings docs Rule 22](feedback_settings_manifest_regen.md) · [Kotlin skips doc-pin](feedback_post_change_kotlin_skips_doc_pin_gate.md)
- [-Files: whole set](feedback_post_change_dev_log_first_file_only.md) · [Gate cost](reference_gate_cost_mining.md) - detekt dominates; three runners since Rule 33; read the journal, not transcripts
- [-RegistryAck up front](feedback_registry_ack_up_front.md) - `.claude/**` needs it on the FIRST call
- [Never reword -Description on a re-run](feedback_changing_description_between_post_change_reruns_duplicates_the_row.md) - dedup keys on it; two permanent rows
- [Discount shrinks the imbalance, not the add](feedback_discount_must_shrink_not_subtract.md)
- [Never probe with the closure facade](feedback_never_probe_with_the_closure_facade.md) - a dummy -Description writes a permanent changelog row

## Long-run correctness
- [Radio toggles: firmware](project_radio_toggle_restriction_is_firmware_not_targetsdk.md) - not targetSdk; the direct path works
- [EPUB asset-URL error is noise](project_epub_asset_handler_log_noise.md) - interception serves it 2 ms later
- [Notification id registry](project_notification_id_registry.md) · [Idle-disconnect](project_network_idle_disconnect_contract.md) - 30s timers tore down live links

## UI conventions
- ["Send to.." is a registry](project_share_target_registry_is_the_send_to_menu.md) - not the system share sheet; a recipient is two Hilt declarations
- Second-level index: [UI conventions: layouts, focus, settings rows, player glue](INDEX_ui_conventions.md) - open when the task edits a layout or a user-visible surface.

## Wear OS
- Second-level index: [wear build/launch traps, rotation, Data Layer ids, locked watch, Play](INDEX_wear.md) - open when the task touches the watch.

## OCR
- [Wear ALL_FEATURES record: gate or full six](project_all_features_wear_record_needs_companion_gate.md) - phone-bridge vs watch-standalone; never hardcode the row
- [Overlay accuracy exchange](reference_ocr_overlay_exchange.md) - three-sided; the constants live in two other repos

## Brand
- [Waves and particles](project_brand_visual_waves_and_particles.md) - the signature visual, shared with the site; NOT equalizer bars

## Streams / VR / players
- Second-level index: [streams, VR/XR, camera, player family](INDEX_streams_vr.md) - open when the task touches any of those.

## Spec lifecycle & catalog
- Second-level index: [spec lifecycle, catalog scripts, planning rules](INDEX_spec_lifecycle.md) - open when authoring or closing specs.
- [Dedup search is one token vs the slug](feedback_spec_search_matches_slug_tokens_only.md) - a multi-word query falsely reads as "no duplicate"

## PowerShell / shell traps
- Second-level index: [PowerShell, shell scripting, CLI tools & traps](INDEX_pwsh_traps.md) - open when writing scripts or debugging pwsh.

## Parallel sessions
- [Re-claim the lease every phase](feedback_reclaim_ticket_lease_every_phase.md) - it expires mid-run; re-claim every phase
- [Lease before research, not before edit](feedback_claim_ticket_lease_before_research_not_before_edit.md) - a sibling can close it while you read
- [Lock turn is lost between wait and acquire](feedback_lock_turn_lost_between_wait_and_acquire.md) - chain them in one process

## Subagents & verification discipline
- Second-level index: [subagents, delegation limits, verifying their claims](INDEX_subagents.md) - open when delegating work or judging an agent's report.
