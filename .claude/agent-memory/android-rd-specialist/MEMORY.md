# Memory Index

- [About Me](about_me.md) - Serhii, solo owner; data engineer, no Kotlin; explain via SQL/.NET · [Target audience non-technical](feedback_target_audience_non_technical.md) - for grandma & gym-goer, zero jargon/setup
- [Argue then obey](feedback_argue_then_obey.md) + [decision after pushback](feedback_owner_decision_after_pushback.md) - push back, then execute owner's call cleanly
- [Don't ask when architecture answers](feedback_no_owner_questions_when_architecture_already_answers.md) + [research forks](feedback_research_over_owner_question.md) · [Clarify unclear framing](feedback_clarify_task_when_framing_unclear.md) - contracts decide; forks research+recommend; unclear scope -> ask/park
- [Timestamp every message](feedback_timestamp_in_chat.md) [HH:MM:SS] · [Writing style](feedback_writing_style.md) `..`/hyphen/ё (docs+UI only)
- [Finish mechanical follow-ups](feedback_finish_mechanical_followups_in_context.md) - don't park a scoped mechanical tail · [Skill aliases](feedback_skill_aliases.md) - explicit table, no invented letters
- [Universal Agent Kit](reference_universal_agent_kit.md) - public distillation; portability+leanness
- [No paid/key third-party services](feedback_no_paid_or_key_services.md) - keyless first, provider behind a seam · [Weather gadget on Open-Meteo](project_weather_gadget_open_meteo.md) - S0426; non-commercial risk owner-accepted
- [fms_companion subproject](project_fms_companion_subproject.md) - S0421 Go+Wails, OUT of repo P:\windows\fms_companion · [FMS Windows rebrand](project_fms_windows_rebrand.md) display-only; URLs/Store stay LITE
- [.fmscfg contract v2 forward-compat](project_fmscfg_contract_v2_forward_compat.md) - S0988 producer-frozen + consumer-tolerant; accept schemaVersion 2

## Devices & release
- [Test devices](reference_test_device_galaxy_s21.md) - S21+ blanket-authorized, S20 FE is the owner's working phone; check serial · [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md) - traps: `launch` sends no ACTION_MAIN, `clear` = pm clear (wipes data)
- [Never grant system roles on owner phone](feedback_never_grant_system_roles_on_owner_phone.md) - decline role dialogs; re-read bounds before tapping an animating dialog
- [setup_test_media.ps1](reference_setup_test_media.md) - seeds from c:\Common\test_media · [gh CLI location](reference_gh_cli_location.md) - C:\Program Files\GitHub CLI, not on PATH
- [R8 mapping is per-build](project_r8_mapping_is_per_build.md) - Play-reported `ev.y` resolves to plausible nonsense against another build's mapping
- [Play Console API read-only](reference_play_console_api_access.md) - CANNOT see review verdicts · [Play FGS precedent](project_play_release_in_review.md) - specialUse+mediaProjection ACCEPTED
- [Release gate: no coverage regression](feedback_release_no_coverage_regression.md) - STOP if countries/age/device reach shrinks · [check OCR/translate versions](feedback_release_check_ocr_translate_versions.md)
- [Device-reach implied screen.portrait](project_play_device_reach_screen_portrait.md) - orientation lock implies screen.* (S0918/S0934) · [Android XR distribution](project_android_xr_play_distribution.md) - standard covers XR; vr=dedicated track
- [/skill-release gotchas](project_skill_release_gotchas.md) - version skew; gitignored PLAN/ empties Step 12a diff · [Archive after every release](feedback_archive_after_every_release.md) - always archive all Verified+Implemented post-release (Step 12c)
- [prerelease emulator-only](feedback_prerelease_emulator_only.md) + [Maestro flaky](project_prerelease_maestro_harness_flaky.md) - real device wipes config; FAIL=harness
- [Emulator capture](reference_emulator_capture_family_testing.md) + [MediaProjection](reference_emulator_mediaprojection_capture.md) - cmd statusbar+aapt2; overlay not drivable
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md) - touch wedge; force scan; sheet taps skip
- [Onboarding device-test gotchas](feedback_onboarding_device_test_gotchas.md) - pre-grant perms before walk; coords shift per recreate; HOME role dialog absent on AVD; S1136 storm gone (Phase A)
- [Trigger widget-only on AVD](reference_trigger_widget_only_features_on_emulator.md) - am start blocked; self-uid widget tap
- [Emulator mouse-wheel injection](reference_emulator_mouse_wheel_injection.md) - API35 `input mouse scroll --axis VSCROLL` = real ACTION_SCROLL (spec notes claiming "can't" are stale) · [Color theme switch](feedback_color_theme_device_switch.md) - via Settings UI+restart, not pref-swap

## Build, flavors, gates
- [Don't release someone else's CODE.LOCK](feedback_code_lock_release_ownership.md) - post-change already freed yours; check lock-status reason first
- [Fast checks](feedback_fast_checks_during_dev.md) + [no redundant flavor compile](feedback_no_redundant_flavor_compile.md) - src/main-only -> fc; fkn only on flavor touch · [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md)
- [Flavor isolation](feedback_flavor_isolation_strict.md) - flavor code in src/<flavor>/; no BuildConfig guards in src/main · [Don't infer arch from BuildConfig](feedback_dont_infer_from_buildconfig_names.md) - grep usage
- [Push features to lowest flavor](feedback_push_features_to_lowest_flavor.md) - broadest legal; unclear->ask · [legacy+photos HAVE cloud](project_flavor_matrix_cloud_correction.md) - legacy=full, photos=cloud+net, lite=no-cloud
- [Third-party branding not a blocker](feedback_third_party_branding_not_a_blocker.md) - producer logo ok · [photos/lite OCR src sets](project_photos_flavor_ocr_break.md) - Tesseract src/ocrEnabled, NoOp src/ocrDisabled
- [S0386 native-attach broken API36](project_s0386_native_attach_broken_api36.md) - de-bundled .so fails arm64/API36; S0923 guards · [Native .so bundle standard vs on-demand noLegal](project_native_so_bundle_standard_vs_ondemand_nolegal.md) - Play bans .so download (S0971)
- [screenCapture standard vs noLegal](project_screencapture_nolegal_only.md) - capture+edge in standard; noLegal silent · [screenCapture gates gesture](project_screencapture_gates_gesture_capability.md) - fms.screenCapture=on default
- [manifest.srcFile overrides flavor](project_agp_manifest_srcfile_overrides_flavor_manifest.md) - use addStaticManifestFile · [MSAL signing-hash per keystore](project_msal_signing_hash_per_keystore.md) - each config = distinct hash
- [material-icons-extended stays](project_material_icons_extended_not_removable.md) - Pause/SkipNext extended-only · [Incremental phantom ref](project_incremental_build_phantom_unresolved.md) - dq phantom -> clean build (cd)
- [Build gotchas](project_build_gotchas.md) + [output trunc](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md) - daemon retry; grep BUILD SUCCESSFUL
- [Unmask kapt NPE](project_kapt_npe_unmask.md) + [subagent claims](feedback_verify_subagent_build_failures.md) - correctErrorTypes=false; agent stale -> dq first; its root cause = lead, not evidence
- [Remove dead config too](feedback_remove_dead_applications_too.md) - kill dead plugin/classpath · [Ctor change -> compile tests](feedback_constructor_change_compile_tests.md) + [pre-existing fails](feedback_build_pre_existing_test_failures.md) - ~26 broken
- [Check binding field types](feedback_check_generated_binding_types.md) - .bind(root) downcasts; Button vs MaterialButton crashes

## detekt / gates / logging
- [detekt gate](project_detekt_gate_in_post_change.md) + [dirty tree](feedback_detekt_gate_dirty_tree.md) + [-ScopeToFile](feedback_closure_on_dirty_tree.md) - ratchet; -ScopeToFile diff-scopes
- [detekt-clean authoring](feedback_write_detekt_clean_first_time.md) + [baseline resurface](feedback_detekt_baseline_signature_resurface.md) + [ktlint imports](project_detekt_ktlint_import_layout.md) - log<=120; +1 ctor=NEW
- [Hand-edited baseline ignored](project_detekt_baseline_hand_edit_daemon_stale.md) + [scoped debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) - warm daemon stale; "PASS [scoped] 0 file(s)"=blind (S1077)
- [Stale test-results XML](feedback_stale_test_results_xml.md) - survives failed/killed runs; check BUILD verdict + mtime, never "not in FAILED list"
- [post-change detekt stale report](feedback_post_change_detekt_stale_report.md) - gate FAILs on cached detekt.txt; force :app_v2:detekt --rerun-tasks, then re-run
- [Prevent at source](feedback_prevent_at_source_not_just_detect.md) - after gate, add DON'T rule · [listener-symmetry gate per-file](project_listener_symmetry_gate_per_file.md) - co-locate remove token same-file
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) + [rule](feedback_persistent_logs_no_ticket_id.md) - fail-closed; Sxxxx only in BNUT probes · [Timber.e for real errors](feedback_log_levels.md) - fallbacks at Timber.i
- [Settings docs sync Rule 22](feedback_settings_manifest_regen.md) - regen manifest+annotations+reference · [Doc-pin tooling ownership](feedback_doc_pin_tooling_ownership_split.md) - toolchain-pins vs check-doc-vs-gradle (S1075)

## UI conventions
- [Focus indicator + S0943](project_focus_frame_infra.md) - TV/D-pad focus = in-place per-view; overlay S0819 archived · [No wrapper focus compound rows](feedback_compound_row_no_wrapper_focus.md) - inner field is D-pad stop
- [Bounded UI](feedback_no_edge_to_edge_ui_elements.md) + [insets](feedback_respect_system_insets_safe_bounds.md) - bounded W+H; systemBars+cutout safe rect
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) + [multi-column](feedback_landscape_multicolumn_settings.md) - toggles 2-up, buttons 3-4+ Flow
- [configChanges no recreate](project_streams_activity_config_changes_rotation.md) - recompute spans in onConfigurationChanged · [BaseActivity posts setupViews()](feedback_baseactivity_setupviews_posted_ordering.md) - restore in attach() (S0910)
- [SettingsInputRow greedy width](feedback_settingsinputrow_greedy_width.md) - internally match_parent · [Canonical settings pickers](feedback_canonical_settings_value_pickers.md) - reuse ListSelectionDialog<T>+SettingsSelectionRow
- [Category != icon monochrome](feedback_category_is_not_icon_monochrome_proxy.md) - tint by icon source; device-verify (S1124) · [Reuse existing settings](feedback_reuse_existing_settings.md) - grep AppSettings+fragment first
- [Settings section deep-link](project_settings_section_deeplink.md) - EXTRA_INITIAL_TAB+EXTRA_EXPAND_SECTION · [Settings-search gate axes](project_settings_search_gate_axes.md) - 3 ANDed: section/CapabilityGate/DeviceFeatureGate
- [Resource vs Folder terms](feedback_resource_vs_folder_terminology.md) - resource=registered, folder=FS dir · [Write-permission gating](project_write_permission_gating.md) - allowsWriteOperations() (S1019)
- [MainActivity LOC ceiling](feedback_mainactivity_loc_ceiling.md) - ~1500; fold into Main*Manager · [Players are a family](feedback_player_family_glue_mirroring.md) - shared engine propagates; per-host mirrored
- [activity_welcome 3 widths](project_welcome_layout_variants.md) - layout/ + sw480dp/ + sw720dp/ · [sw qualifier beats -land](project_res_sw_qualifier_beats_land.md) - values-swNNNdp shadows values-land
- [Main top panels width grid](project_main_top_panels_width_grid.md) - S1037/S1049/S1068; S1068=portrait flush x=0 · [Sync docs/site on visible change](feedback_sync_docs_on_visible_change.md)
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md) - S0558 validates vs manifest; U+2192 needs EN/RU/UK
- [Play edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md) - #2 fixed Material 1.14.0 · [Land player bottom-band stacking](project_land_player_bottom_band_stacking.md) - anchor above bottomPanelsContainer
- [Material inflate needs themed ctx](feedback_material_inflate_needs_themed_context.md) - MaterialButton from app ctx crashes; use ContextThemeWrapper

## Streams / VR / players
- [Link-download present() dead](project_link_download_present_suppressed.md) - worker notificationShown=true suppresses (S0980)
- [Stream catalog all channels](feedback_stream_catalog_all_live_channels.md) + [publish](reference_stream_catalog_publish.md) - ship EVERY reachable; -WithFavicons -Publish
- [Favicon atlas delivery](project_stream_favicon_atlas_delivery.md) + [publish](project_stream_catalog_atlas_publish.md) - publish w/o atlas.png wipes favicons; S0925 guards
- [Streams device-test gate](project_streams_device_test_gate.md) - enableStreams gates menu; net kill via svc wifi/data · [Stream radio vs video](project_stream_radio_vs_video_player_split.md) - radio->InlineAudio; video/RTSP->BandwidthAdaptive
- [VR inclusion hierarchy](project_vr_inclusion_hierarchy.md) - noLegal all-inclusive sideload-VR · [supportsVrPlayer noLegal-only](project_supportsvrplayer_nolegal_only.md) - gate on VrMediaSectionContract.isAvailable
- [VR immersive re-entry](project_vr_immersive_reentry_hotspot.md) + [logcat trap](reference_vr_immersive_logcat_capture_trap.md) - recreate XrInstance per entry; adb logcat -b all
- [Quest panel opaque](reference_quest_panel_not_introspectable.md) + [HUD pitfalls](project_vr_hud_quirks.md) - uiautomator sees vrshell only · [VR native 2 texture channels](project_vr_native_two_texture_channels.md) - queueFrame(main)+queueHud
- [CameraCaptureSessionManager at 40-function ceiling](project_camera_session_manager_function_ceiling.md) - any new helper fails detekt; inline or go top-level
- [Player progressBar owner](project_player_progressbar_single_owner.md) - PlayerLoadingIndicatorCoordinator · [Glide listener fires before view bind](project_glide_requestlistener_fires_before_view_bind.md) - use view.post{} (S1041)
- [Shared-state audit tool](reference_shared_state_audit_tool.md) - audit-shared-state-writers.ps1 · [Camera capture permission-free](project_camera_capture_permission_constraint.md) - declaring CAMERA breaks ACTION_IMAGE_CAPTURE
- [Headless capture + noHistory trap](project_headless_camera_capture_trampoline.md) - ImageCapture-only; noHistory loses result
- [Radio stutter toolkit S1148](project_live_radio_loadcontrol_min_eq_max.md) - smart-buffering toggle; read telemetry before LoadControl · [Samsung Dolby eac3-joc glitch](project_samsung_dolby_eac3_joc_glitch.md) - software-preferred selector

## Spec lifecycle & catalog
- [Launcher family state](project_launcher_roadmap_greenlit.md) - S0404/S1088 archived, children live; quizzes 07-18 + 07-27 locked decisions; owner asks, not /spec-next
- [Probe tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) - grep `"Sxxxx:` too · [Working tree is truth](feedback_dirty_tree_is_normal_wip.md) - never log/blame/diff/forensic WIP; git only on explicit ask/release flow
- [Drift-check misses untagged impl](feedback_driftcheck_misses_untagged_impl.md) - In Progress+CLEAN drift may be coded; grep before greenfield · [Verify spec id before pipeline](feedback_verify_spec_id_before_pipeline.md) - select.ps1 first
- [IDE Draft finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) - re-read · [Draft style is approval-gate](feedback_draft_style_gate.md) - sanitation only Draft->Approved · [Status auto-syncs](feedback_spec_header_autosync.md)
- [Strategic spec owner gate](feedback_strategic_spec_owner_gate.md) - §3.3 needs Related tickets · [spec-tech plan quality](feedback_spec_tech_plan_quality.md) - keep 3.1-3.4/5.5+research/
- [/spec-dev verify code first](feedback_spec_dev_continue_verify_code_first.md) - In-Progress code may be done · [Phase-boundary audit](feedback_phase_boundary_audit.md) - audit just-finished phase before next
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) + [no fake autopilot blocker](feedback_no_safety_blocker_gating_autopilot.md) - headline broken->not Done; safe cleanup auto-chains
- [Search dup tickets by symptom](feedback_search_duplicates_by_symptom.md) - errorCode/class first · [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md) - grep PLAN/+Partial before delete
- [Block status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) - flip status BEFORE audit · [close.ps1 two-step unblock](project_close_ps1_two_step_unblock.md) - Block->Implemented->Verified
- [Capability inventory](project_functionality_log.md) - docs/ALL_FEATURES.jsonl via add.ps1 · [Feature-record flavors from the gate](feedback_feature_record_flavors_from_gate.md) - read record back · [noLegal features doc](feedback_features_nolegal.md)
- [spec_catalog exit-code](project_spec_catalog_exit_code_contract.md) + [insert -File](project_insert_ps1_file_validation.md) - trap{exit 1}+exit 0; next-id.ps1 first
- [Catalog scan roots](project_catalog_scan_source_sets.md) + [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) · [Catalog -Search coverage](reference_catalog_search_coverage.md) - query.ps1 -Search first; role 100%
- [Big-file decomposition](project_s0002_decomposition_toolkit.md) - compression tactics; temp/ scripts wiped

## PowerShell / shell traps
- [Tool-bypass discipline](feedback_tool_bypass_discipline.md) - no cd-prefix, no hand-rolled adb path, no manual device probe · [Script param cheatsheet](reference_script_help_cheatsheet.md) - help.ps1 -Name
- [PowerShell efficiency](feedback_pwsh_efficiency.md) - never plain pwsh -File; chain in one process · [LOC undercounts](feedback_pwsh_loc_measure_object.md) - use (Get-Content).Count
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) - repo scripts/temp .ps1 over nested quoting · [Check existing tooling first](feedback_check_existing_tooling.md) - grep scripts/+utils/+skills
- [Cyrillic bash->pwsh boundary](feedback_cyrillic_bash_pwsh_boundary.md) - never pass RU/UK as pwsh CLI args from Bash · [pwsh shim in Git Bash](reference_pwsh_shim.md)
- [pwsh byte traps](feedback_pwsh_authoring_byte_traps.md) + [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) · [param/local collision](feedback_pwsh_param_local_case_collision.md)
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) - close-and-log takes JSON-array · [set-android-string.ps1](reference_strings_tool.md) + [main/res only](feedback_string_tools_main_res_only.md)
- [bash rg skips CATALOG](feedback_rg_gitignore_catalog.md) - use Grep tool/--no-ignore · [BG task exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md) - read log
- [Workflow journal recovery](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md) - recover from journal.jsonl

## Subagents & process
- [/spec-test-device subagent commits tree](feedback_spec_test_device_subagent_commits_tree.md) - device-run subagent runs a.ps1 c (whole-tree commit+push) despite "don't touch git"
- [Verify every variant of a screen](feedback_verify_all_variants_of_the_screen.md) - both media kinds/modes/filters, incl. the no-data case
- [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md) - confirm NEW build installed; use dav · [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) + [no git/build](feedback_parallel_agents_no_git_build.md)
- [Concurrent /spec-all red tree](project_spec_all_concurrent_tree_red.md) - whole-tree fail may be sibling WIP · [Frozen app? check TracerPid](feedback_frozen_app_check_tracerpid.md) - GC ProfileSaver stall = LLDB ptrace
- [Welcome process consolidation](feedback_welcome_process_consolidation.md) - owner wants ceremony cut · [Workflow vs 5h limit](feedback_workflow_session_limit_budget.md) - LOW parallelism, cap ~6-8; owner GO above
- [Don't stop a loop on a context guess](feedback_dont_stop_loop_on_context_guess.md) - no meter exists; cut per-ticket cost, not the session
- [Verify with full evidence](feedback_verify_full_evidence.md) - skeptics read verbatim; split vote -> read code · [No ellipsis edits in code spans](feedback_no_ellipsis_edits_in_verbatim_code_spans.md)
- [Edit line-delete splice](feedback_edit_line_delete_splice.md) - old_string="\n..X" glues CRLF; match full next line
