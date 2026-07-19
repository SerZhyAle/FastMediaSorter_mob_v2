# Memory Index

- [About Me](about_me.md) - Serhii, solo owner; data engineer, no Kotlin; explain via SQL/.NET
- [Argue then obey](feedback_argue_then_obey.md) + [decision after pushback](feedback_owner_decision_after_pushback.md) - push back, then execute owner's call cleanly
- [Don't ask when architecture answers](feedback_no_owner_questions_when_architecture_already_answers.md) + [research forks](feedback_research_over_owner_question.md) - contracts decide; forks: research+recommend
- [Clarify unclear task framing](feedback_clarify_task_when_framing_unclear.md) - unsure what task *means*/scope -> ask or park; impl-detail code answers -> don't
- [Timestamp every chat message](feedback_timestamp_in_chat.md) - prefix [HH:MM:SS]
- [Writing style: hyphen / ё / ..](feedback_writing_style.md) - `..` not `...`, hyphen, ё; docs+UI only
- [Target audience: non-technical](feedback_target_audience_non_technical.md) - for grandma & gym-goer, not nerds; zero jargon, zero mandatory setup
- [Finish mechanical follow-ups](feedback_finish_mechanical_followups_in_context.md) - don't park a scoped mechanical tail
- [Skill aliases](feedback_skill_aliases.md) - short slash aliases; explicit table, no invented letters
- [Universal Agent Kit](reference_universal_agent_kit.md) - public distillation; portability+leanness
- [fms_companion subproject](project_fms_companion_subproject.md) - S0421 Go+Wails, OUT of repo P:\windows\fms_companion
- [FMS Windows rebrand](project_fms_windows_rebrand.md) - "Fast Media Sorter for Windows" display-only; URLs/Store/winget stay FastMediaSorter_Lite/LITE
- [.fmscfg contract v2 forward-compat](project_fmscfg_contract_v2_forward_compat.md) - S0988 producer-frozen-shape + consumer-tolerant; client MUST accept schemaVersion 2

## Devices & release
- [Test device Galaxy S21+](reference_test_device_galaxy_s21.md) - SM-G996U1, Android 15; full access; not Wear
- [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md) - adb.ps1; not on PATH; pkg *.debug
- [setup_test_media.ps1](reference_setup_test_media.md) - seeds test-media; source c:\Common\test_media
- [Play Console API read-only](reference_play_console_api_access.md) - read states; CANNOT see review verdicts
- [gh CLI location](reference_gh_cli_location.md) - C:\Program Files\GitHub CLI, not on PATH
- [Play FGS precedent](project_play_release_in_review.md) - specialUse+mediaProjection ACCEPTED by review
- [Release gate: no coverage regression](feedback_release_no_coverage_regression.md) - STOP if countries/age/device reach shrinks
- [Pre-release: check OCR/translate versions](feedback_release_check_ocr_translate_versions.md) - ML Kit/Tesseract libs + models; upstream-driven quality
- [Device-reach: implied screen.portrait](project_play_device_reach_screen_portrait.md) - orientation lock implies required screen.* (S0918/S0934)
- [Android XR Play distribution](project_android_xr_play_distribution.md) - standard already covers XR; vr=dedicated XR track, same package; never swap for standard
- [/skill-release gotchas](project_skill_release_gotchas.md) - version skew; gitignored PLAN/ empties Step 12a diff
- [prerelease emulator-only](feedback_prerelease_emulator_only.md) + [Maestro flaky](project_prerelease_maestro_harness_flaky.md) - real device wipes config; FAIL=harness
- [Emulator capture](reference_emulator_capture_family_testing.md) + [MediaProjection](reference_emulator_mediaprojection_capture.md) - cmd statusbar+aapt2; overlay not drivable
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md) - touch wedge; force scan; sheet taps skip
- [Trigger widget-only on AVD](reference_trigger_widget_only_features_on_emulator.md) - am start export-blocked; self-uid widget tap
- [Color theme device switch](feedback_color_theme_device_switch.md) - pref-file swap doesn't stick; via Settings UI+restart

## Build, flavors, gates
- [Fast checks](feedback_fast_checks_during_dev.md) + [no redundant flavor compile](feedback_no_redundant_flavor_compile.md) - src/main-only -> fc; fkn only on flavor touch
- [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md) - never >1 build; post-change static, safe
- [Flavor isolation](feedback_flavor_isolation_strict.md) - flavor code in src/<flavor>/; no BuildConfig guards in src/main
- [Don't infer arch from BuildConfig](feedback_dont_infer_from_buildconfig_names.md) - grep usage; PLAYER_ACTIVITY_CLASS dead
- [Push features to lowest flavor](feedback_push_features_to_lowest_flavor.md) - broadest legal flavor; unclear -> ask
- [legacy+photos HAVE cloud](project_flavor_matrix_cloud_correction.md) - legacy=full, photos=cloud+net, lite=no-cloud
- [Third-party branding not a blocker](feedback_third_party_branding_not_a_blocker.md) - producer logo by its content = ok
- [photos/lite OCR src sets](project_photos_flavor_ocr_break.md) - Tesseract src/ocrEnabled, NoOp src/ocrDisabled
- [S0386 native-attach broken API36](project_s0386_native_attach_broken_api36.md) - de-bundled .so fails real arm64/API36; S0923 guards
- [Native .so: bundle for standard, on-demand only noLegal](project_native_so_bundle_standard_vs_ondemand_nolegal.md) - Play bans .so download (S0971); FFmpeg=audio-only
- [screenCapture standard vs noLegal](project_screencapture_nolegal_only.md) - capture+edge gestures in standard; noLegal silent path
- [screenCapture gates gesture](project_screencapture_gates_gesture_capability.md) - fms.screenCapture=on default; standard covers twin
- [manifest.srcFile overrides flavor](project_agp_manifest_srcfile_overrides_flavor_manifest.md) - use addStaticManifestFile
- [MSAL signing-hash per keystore](project_msal_signing_hash_per_keystore.md) - each config = distinct BrowserTabActivity hash
- [material-icons-extended stays](project_material_icons_extended_not_removable.md) - Pause/SkipNext extended-only; audit broke build
- [Incremental phantom ref](project_incremental_build_phantom_unresolved.md) - dq phantom after multi-edit -> clean build (cd)
- [Build gotchas](project_build_gotchas.md) + [output trunc](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md) - daemon retry; grep BUILD SUCCESSFUL
- [Unmask kapt NPE](project_kapt_npe_unmask.md) + [subagent build fails](feedback_verify_subagent_build_failures.md) - correctErrorTypes=false; agent stale -> dq first
- [Remove dead config too](feedback_remove_dead_applications_too.md) - kill dead plugin/classpath; "is it dead" not "saves bytes"
- [Ctor change -> compile tests](feedback_constructor_change_compile_tests.md) + [pre-existing fails](feedback_build_pre_existing_test_failures.md) - ~26 broken, per-class XML
- [Check binding field types](feedback_check_generated_binding_types.md) - .bind(root) downcasts; Button vs MaterialButton crashes

## detekt / gates / logging
- [detekt gate](project_detekt_gate_in_post_change.md) + [dirty tree](feedback_detekt_gate_dirty_tree.md) + [-ScopeToFile](feedback_closure_on_dirty_tree.md) - ratchet; -ScopeToFile diff-scopes
- [detekt-clean authoring](feedback_write_detekt_clean_first_time.md) + [baseline resurface](feedback_detekt_baseline_signature_resurface.md) + [ktlint imports](project_detekt_ktlint_import_layout.md) - log<=120; +1 ctor=NEW
- [Hand-edited baseline ignored](project_detekt_baseline_hand_edit_daemon_stale.md) + [scoped debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) - warm daemon stale; delete detekt.xml, not --rerun-tasks; "PASS [scoped] - 0 file(s)" = blind, not clean (S1077)
- [Prevent at source](feedback_prevent_at_source_not_just_detect.md) - after a gate, add DON'T rule to CLAUDE.md + skills
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) + [rule](feedback_persistent_logs_no_ticket_id.md) - fail-closed; Sxxxx only in BNUT probes
- [Timber.e for real errors](feedback_log_levels.md) - expected capability fallbacks log at Timber.i
- [Settings docs sync (Rule 22)](feedback_settings_manifest_regen.md) - regen manifest + annotations + reference

## UI conventions
- [Focus indicator + S0943 umbrella](project_focus_frame_infra.md) - TV/D-pad focus = in-place per-view decoration; overlay S0819 archived
- [Bounded UI](feedback_no_edge_to_edge_ui_elements.md) + [insets](feedback_respect_system_insets_safe_bounds.md) - bounded W+H; systemBars+cutout safe rect
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) + [multi-column](feedback_landscape_multicolumn_settings.md) - toggles 2-up, buttons 3-4+ Flow
- [configChanges no recreate](project_streams_activity_config_changes_rotation.md) - recompute spans in onConfigurationChanged
- [BaseActivity posts setupViews()](feedback_baseactivity_setupviews_posted_ordering.md) - recreation-restore in attach()/onResumeWithViews (S0910)
- [SettingsInputRow greedy width](feedback_settingsinputrow_greedy_width.md) - internally match_parent; fix width in weighted rows
- [Canonical settings pickers](feedback_canonical_settings_value_pickers.md) - reuse ListSelectionDialog<T>+SettingsSelectionRow
- [No wrapper focus compound rows](feedback_compound_row_no_wrapper_focus.md) - wrapper NOT focusable; inner field is D-pad stop
- [Settings section deep-link](project_settings_section_deeplink.md) - EXTRA_INITIAL_TAB+EXTRA_EXPAND_SECTION; self-expands
- [Settings-search gate axes](project_settings_search_gate_axes.md) - 3 ANDed: section / CapabilityGate / DeviceFeatureGate
- [Reuse existing settings](feedback_reuse_existing_settings.md) - grep AppSettings+fragment before adding
- [Resource vs Folder terms](feedback_resource_vs_folder_terminology.md) - resource=registered entity; folder=FS dir
- [Write-permission gating](project_write_permission_gating.md) - isWritable(probe/connectivity) vs isReadOnly(policy); use allowsWriteOperations() (S1019)
- [MainActivity LOC ceiling](feedback_mainactivity_loc_ceiling.md) - ~1500; fold wiring into Main*Manager
- [Players are a family](feedback_player_family_glue_mirroring.md) - shared engine propagates; per-host mirrored manually
- [activity_welcome 3 widths](project_welcome_layout_variants.md) - layout/ + sw480dp/ + sw720dp/; new id in all three
- [sw qualifier beats -land](project_res_sw_qualifier_beats_land.md) - values-swNNNdp shadows values-land; fix via swNNNdp-land bucket
- [Main top panels width grid](project_main_top_panels_width_grid.md) - S1037/S1049/S1068; S1068=portrait flush x=0 + first-cell accent, land keeps anchor
- [Sync docs/site on visible change](feedback_sync_docs_on_visible_change.md) - revisit affected docs + site copy
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md) - S0558 validates vs manifest; U+2192 lines need EN/RU/UK
- [Play edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md) - #2 fixed by Material 1.14.0; #1 informational
- [Land player bottom-band stacking](project_land_player_bottom_band_stacking.md) - anchor above bottomPanelsContainer, not parent
- [Material inflate needs themed ctx](feedback_material_inflate_needs_themed_context.md) - MaterialButton from app ctx crashes; use ContextThemeWrapper

## Streams / VR / players
- [Link-download present() dead](project_link_download_present_suppressed.md) - worker notificationShown=true suppresses present() (S0980)
- [Stream catalog: all channels](feedback_stream_catalog_all_live_channels.md) + [publish](reference_stream_catalog_publish.md) - ship EVERY reachable; -WithFavicons -Publish
- [Favicon atlas delivery](project_stream_favicon_atlas_delivery.md) + [publish](project_stream_catalog_atlas_publish.md) - publish w/o favicon-atlas.png wipes favicons; S0925 guards; -CatalogOnly -SkipLiveness
- [Streams device-test gate](project_streams_device_test_gate.md) - enableStreams gates menu; net kill via svc wifi/data
- [Stream radio vs video](project_stream_radio_vs_video_player_split.md) - radio(AUDIO)->InlineAudio; video/RTSP->BandwidthAdaptive
- [VR inclusion hierarchy](project_vr_inclusion_hierarchy.md) - noLegal all-inclusive sideload-VR; vrUnlicensed archived
- [supportsVrPlayer noLegal-only](project_supportsvrplayer_nolegal_only.md) - gate on VrMediaSectionContract.isAvailable
- [VR immersive re-entry](project_vr_immersive_reentry_hotspot.md) + [logcat trap](reference_vr_immersive_logcat_capture_trap.md) - recreate XrInstance per entry; adb logcat -b all
- [Quest panel opaque](reference_quest_panel_not_introspectable.md) + [HUD pitfalls](project_vr_hud_quirks.md) - uiautomator sees vrshell only; column-major, Skia RGBA
- [VR native 2 texture channels](project_vr_native_two_texture_channels.md) - queueFrame(main)+queueHud; UI on HUD quad ray UV
- [Player progressBar owner](project_player_progressbar_single_owner.md) - PlayerLoadingIndicatorCoordinator; PdfViewer rogue writer
- [Glide listener fires before view bind](project_glide_requestlistener_fires_before_view_bind.md) - onResourceReady runs before setImageDrawable; view.drawable still null, use view.post{} (S1041)
- [Shared-state audit tool](reference_shared_state_audit_tool.md) - audit-shared-state-writers.ps1
- [Camera capture permission-free](project_camera_capture_permission_constraint.md) - declaring CAMERA breaks ACTION_IMAGE_CAPTURE
- [Headless capture + noHistory trap](project_headless_camera_capture_trampoline.md) - ImageCapture-only; noHistory loses result

## Spec lifecycle & catalog
- [Release scope 2026-07](project_release_scope_2026_07.md) - 11 gating tickets S0846..S0891; verify statuses live
- [Launcher roadmap greenlit 2026-07](project_launcher_roadmap_greenlit.md) - S0404 unfrozen 2026-07-18; build S1088 first; S1098 archived; drive children as Drafts
- [BNUT sweep plan 2026-07-02](project_bnut_sweep_plan.md) - 65 tickets triaged; plan temp/spec_sweep_batch_plan.md
- [Probe tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) - grep `"Sxxxx:` too; single-line misses wrapped
- [Working tree is truth](feedback_dirty_tree_is_normal_wip.md) - never log/blame/diff for WIP; git only on explicit ask
- [Verify spec id before pipeline](feedback_verify_spec_id_before_pipeline.md) - select.ps1 first; match IDE-open Sxxxx
- [IDE Draft finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) - /spec-all rewrites Draft->Tactical; re-read
- [Draft style is approval-gate](feedback_draft_style_gate.md) - sanitation only at Draft->Approved; drafts stay rough
- [Spec Status auto-syncs](feedback_spec_header_autosync.md) - update.ps1 rewrites first **Status:** line
- [Strategic spec owner gate](feedback_strategic_spec_owner_gate.md) - §3.3 needs Related tickets (check-owner-inputs)
- [spec-tech plan quality](feedback_spec_tech_plan_quality.md) - keep 3.1-3.4/5.5 + research/; no doc-shuffling
- [/spec-dev verify code first](feedback_spec_dev_continue_verify_code_first.md) - In-Progress code may be done, tracking 0/N
- [Phase-boundary audit](feedback_phase_boundary_audit.md) - audit+fix just-finished phase before next; cheap now, costly later
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) + [no fake autopilot blocker](feedback_no_safety_blocker_gating_autopilot.md) - headline broken -> not Done; safe cleanup auto-chains
- [Search dup tickets by symptom](feedback_search_duplicates_by_symptom.md) - search catalog by errorCode/class first
- [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md) - grep PLAN/ + Partial/In-Progress before delete
- [Block status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) - flip status BEFORE audit
- [close.ps1 two-step unblock](project_close_ps1_two_step_unblock.md) - no direct Block->Verified; via Implemented
- [Capability inventory](project_functionality_log.md) - docs/ALL_FEATURES.jsonl via all_features/add.ps1
- [Feature-record flavors from the gate](feedback_feature_record_flavors_from_gate.md) - never copy a sibling / accept a default; read the record back
- [noLegal features doc](feedback_features_nolegal.md) - docs/FEATURES* standard/VR; noLegal gitignored
- [spec_catalog exit-code](project_spec_catalog_exit_code_contract.md) + [insert -File](project_insert_ps1_file_validation.md) - trap{exit 1}+exit 0; next-id.ps1 first
- [Catalog scan roots](project_catalog_scan_source_sets.md) + [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) - scan.ps1 hard-codes $srcRoots; set.ps1 aborts
- [Catalog -Search coverage](reference_catalog_search_coverage.md) - query.ps1 -Search first (multi-word fixed); role 100% filled; bulk fill via generate/apply-role-drafts.ps1
- [Big-file decomposition](project_s0002_decomposition_toolkit.md) - compression tactics; temp/ scripts wiped

## PowerShell / shell traps
- [Tool-bypass discipline](feedback_tool_bypass_discipline.md) - measured top waste: no cd-prefix, no hand-rolled adb path, no manual device probe
- [Script param cheatsheet](reference_script_help_cheatsheet.md) - `scripts/utils/help.ps1 -Name <s>` prints params; don't re-read scripts
- [PowerShell efficiency](feedback_pwsh_efficiency.md) - never plain pwsh -File; chain scripts in one process
- [LOC: Measure-Object -Line undercounts](feedback_pwsh_loc_measure_object.md) - use (Get-Content).Count
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) - prefer repo scripts / temp .ps1 over nested quoting
- [Check existing tooling first](feedback_check_existing_tooling.md) - grep scripts/+utils/+skills before authoring new
- [Cyrillic bash->pwsh boundary](feedback_cyrillic_bash_pwsh_boundary.md) - never pass RU/UK as pwsh CLI args from Bash
- [pwsh byte traps](feedback_pwsh_authoring_byte_traps.md) + [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) - Write escapes=control bytes; \$ collapses
- [pwsh param/local collision](feedback_pwsh_param_local_case_collision.md) - lowercase loop-local same as param corrupts it
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) - -File binds ONE; close-and-log takes JSON-array
- [pwsh shim in Git Bash](reference_pwsh_shim.md) - bare pwsh works via /c/Users/serzh/bin/pwsh
- [set-android-string.ps1](reference_strings_tool.md) - byte-preserving set/add/get across EN/RU/UK
- [String tools main/res only](feedback_string_tools_main_res_only.md) - ignore src/<flavor>/res; hand-edit + grep-verify
- [bash rg skips CATALOG](feedback_rg_gitignore_catalog.md) - use Grep tool / --no-ignore / Read
- [BG task exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md) - exit reflects trailing echo, read log
- [Workflow journal recovery](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md) - recover from journal.jsonl; resume drops args

## Subagents & process
- [Verify build on device first](feedback_verify_build_on_device_before_diagnosing.md) - "doesn't work" -> confirm NEW build installed (same-version APK keeps old); use dav, key off S-tag/version
- [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) + [no git/build](feedback_parallel_agents_no_git_build.md) - impl agents truncate cleanup; git stash clobbers; central build
- [Concurrent /spec-all red tree](project_spec_all_concurrent_tree_red.md) - whole-tree fail may be sibling WIP; not their files
- [Frozen app? check TracerPid](feedback_frozen_app_check_tracerpid.md) - GC ProfileSaver stall = native LLDB via ptrace
- [Welcome process consolidation](feedback_welcome_process_consolidation.md) - owner wants ceremony cut; edits CLAUDE.md/skills
- [Workflow vs 5h limit](feedback_workflow_session_limit_budget.md) - LOW parallelism, cap ~6-8 agents; owner GO above
- [Verify with full evidence](feedback_verify_full_evidence.md) - skeptics read verbatim; split vote -> read code myself
- [No ellipsis edits in code spans](feedback_no_ellipsis_edits_in_verbatim_code_spans.md) - `...` rule exempts code/specs; gate skips ticks
- [Edit line-delete splice](feedback_edit_line_delete_splice.md) - old_string="\n..X" glues CRLF; match full next line
