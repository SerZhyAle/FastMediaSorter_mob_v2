# Memory Index

- [About Me](about_me.md) - Serhii, solo owner; data engineer, no Kotlin (vibecoded); explain via SQL/.NET
- [Argue then obey](feedback_argue_then_obey.md) - push back with reasons, defer to owner's final call
- [Owner decision after pushback](feedback_owner_decision_after_pushback.md) - once owner decides, execute cleanly
- [Don't ask when architecture answers](feedback_no_owner_questions_when_architecture_already_answers.md) - contracts decide it -> no fake question
- [Research over owner-question on forks](feedback_research_over_owner_question.md) - best-practice forks: research + recommend
- [Timestamp every chat message](feedback_timestamp_in_chat.md) - prefix [HH:MM:SS]
- [Writing style: hyphen / ё / ..](feedback_writing_style.md) - `..` not `...`, hyphen, ё; docs+UI only
- [Finish mechanical follow-ups](feedback_finish_mechanical_followups_in_context.md) - don't park a well-scoped mechanical tail
- [Skill aliases](feedback_skill_aliases.md) - short slash aliases (a=spec-all, n=spec-next, d=spec-dev, ..); explicit table, no invented letters
- [Universal Agent Kit](reference_universal_agent_kit.md) - public distillation; bar = portability+leanness

## Devices & release
- [Test device Galaxy S21+](reference_test_device_galaxy_s21.md) - SM-G996U1, Android 15; full access; not Wear
- [adb CLI](reference_adb_swiss_army.md) + [.debug pkg](reference_adb_and_debug_package.md) - adb.ps1 / .\a.ps1 adb; adb not on PATH; pkg *.debug
- [setup_test_media.ps1](reference_setup_test_media.md) - seeds test-media; source c:\Common\test_media
- [Play Console API read-only](reference_play_console_api_access.md) - read states; CANNOT see review verdicts
- [gh CLI location](reference_gh_cli_location.md) - gh.exe at C:\Program Files\GitHub CLI, not on PATH; prepend for publish scripts
- [Play FGS precedent](project_play_release_in_review.md) - specialUse+mediaProjection ACCEPTED by review; capture family live 2.60.6270.802
- [Release gate: no coverage regression](feedback_release_no_coverage_regression.md) - STOP if countries/age/device reach shrinks
- [Device-reach: implied screen.portrait](project_play_device_reach_screen_portrait.md) - orientation lock implies required screen.*; diagnose via aapt2 badging (S0918); Android 16/17 large-screen override + targetSdk-36 opt-out (S0934)
- [/skill-release gotchas](project_skill_release_gotchas.md) - version skew; gitignored PLAN/ empties Step 12a diff
- [prerelease emulator-only](feedback_prerelease_emulator_only.md) + [Maestro flaky](project_prerelease_maestro_harness_flaky.md) - real device wipes config; FAIL = harness, trust toastCount
- [Emulator capture testing](reference_emulator_capture_family_testing.md) + [MediaProjection](reference_emulator_mediaprojection_capture.md) - cmd statusbar+aapt2; overlay not drivable; non-VR AVD verifies menu-screenshot
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md) - touch wedge/mcp coords; force MediaStore scan; bottom-sheet taps ignored -> INCONCLUSIVE
- [Color theme device switch](feedback_color_theme_device_switch.md) - pref-file swap doesn't stick; via Settings UI+restart

## Build, flavors, gates
- [Fast checks](feedback_fast_checks_during_dev.md) + [no redundant flavor compile](feedback_no_redundant_flavor_compile.md) - fk/fr/fc/fu default, d/dav packaging proof; src/main-only -> fc, fkn only on flavor touch
- [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md) - never >1 build; post-change static, safe
- [Flavor isolation](feedback_flavor_isolation_strict.md) - flavor code in src/<flavor>/; no BuildConfig guards in src/main
- [Don't infer arch from BuildConfig](feedback_dont_infer_from_buildconfig_names.md) - grep usage; PLAYER_ACTIVITY_CLASS dead
- [Push features to lowest flavor](feedback_push_features_to_lowest_flavor.md) - broadest legal flavor; unclear -> ask
- [legacy+photos HAVE cloud](project_flavor_matrix_cloud_correction.md) - legacy=full, photos=cloud+net, lite=no-cloud
- [Third-party branding not a blocker](feedback_third_party_branding_not_a_blocker.md) - producer logo by its content = ok
- [photos/lite OCR src sets](project_photos_flavor_ocr_break.md) - Tesseract src/ocrEnabled, NoOp src/ocrDisabled
- [S0386 native-attach broken API36](project_s0386_native_attach_broken_api36.md) - de-bundled .so injection fails on real arm64/API36 -> OCR crash; S0923 guards it, Layer 2 pending device-log
- [screenCapture standard vs noLegal](project_screencapture_nolegal_only.md) - BOTH flags on: capture+edge gestures ship in standard; noLegal adds silent path
- [screenCapture gates gesture](project_screencapture_gates_gesture_capability.md) - fms.screenCapture=on default; plain standard build covers twin, no -P
- [manifest.srcFile overrides flavor](project_agp_manifest_srcfile_overrides_flavor_manifest.md) - use addStaticManifestFile
- [MSAL signing-hash per keystore](project_msal_signing_hash_per_keystore.md) - each config = distinct BrowserTabActivity hash
- [material-icons-extended stays](project_material_icons_extended_not_removable.md) - Pause/SkipNext extended-only; audit broke build
- [Incremental phantom ref](project_incremental_build_phantom_unresolved.md) - dq phantom after multi-edit -> clean build (cd)
- [Build gotchas](project_build_gotchas.md) + [output truncation](feedback_build_output_pipe_truncation.md) + [a.ps1](feedback_aps1_launcher_pwsh_cwd.md) - daemon flake retry; never tail -N failures; pwsh -File from root, grep BUILD SUCCESSFUL
- [Unmask kapt NPE](project_kapt_npe_unmask.md) + [subagent build fails](feedback_verify_subagent_build_failures.md) - OmitStackTraceInFastThrow off, correctErrorTypes=false; agent kapt stale -> re-run dq first
- [Remove dead config too](feedback_remove_dead_applications_too.md) - kill dead plugin/classpath; "is it dead" not "saves bytes"
- [Constructor change -> test compile](feedback_constructor_change_compile_tests.md) + [pre-existing fails](feedback_build_pre_existing_test_failures.md) - run testStandardDebugUnitTest; suite ~26 broken, verify per-class XML

## detekt / gates / logging
- [detekt gate](project_detekt_gate_in_post_change.md) + [dirty tree](feedback_detekt_gate_dirty_tree.md) + [-ScopeToFile](feedback_closure_on_dirty_tree.md) - full detekt slow, ratchet; filter own files; post-change.ps1 -ScopeToFile diff-scopes
- [detekt-clean authoring](feedback_write_detekt_clean_first_time.md) + [baseline resurface](feedback_detekt_baseline_signature_resurface.md) + [ktlint imports](project_detekt_ktlint_import_layout.md) - log<=120, const nums; +1 ctor param = NEW; ASCII order, --rerun-tasks
- [Hand-edited baseline ignored](project_detekt_baseline_hand_edit_daemon_stale.md) - warm daemon serves stale baseline; `gradlew --stop` fixes it, --rerun-tasks --no-config-cache makes it worse
- [Scoped gate surfaces untouched debt](feedback_detekt_scoped_gate_surfaces_untouched_debt.md) - editing a never-baselined file fails scoped gate on its old findings; report stale -> `:app_v2:detekt --rerun-tasks`
- [Prevent at source](feedback_prevent_at_source_not_just_detect.md) - after a gate, add DON'T rule to CLAUDE.md + skills
- [No Sxxxx in permanent logs](reference_ticket_log_gate.md) + [rule](feedback_persistent_logs_no_ticket_id.md) - fail-closed gate; Sxxxx only in BNUT probes
- [Timber.e for real errors](feedback_log_levels.md) - expected capability fallbacks log at Timber.i
- [Settings docs sync (Rule 22)](feedback_settings_manifest_regen.md) - regen manifest (quote -D!) + annotations + reference
- [Check binding field types](feedback_check_generated_binding_types.md) - .bind(root) downcasts; Button vs MaterialButton crashes

## UI conventions
- [Focus indicator + S0943 umbrella](project_focus_frame_infra.md) - TV/D-pad focus = in-place per-view decoration (FocusDecorationController); overlay S0819 archived; never coordinate-compute a focus overlay
- [Bounded UI](feedback_no_edge_to_edge_ui_elements.md) + [insets](feedback_respect_system_insets_safe_bounds.md) - bounded W+H, dropdowns 240/280; systemBars+cutout safe rect
- [Landscape buttons](feedback_no_fullwidth_buttons_landscape.md) + [multi-column](feedback_landscape_multicolumn_settings.md) - wrap_content+gravity; toggles 2-up, buttons 3-4+ Flow
- [configChanges no recreate](project_streams_activity_config_changes_rotation.md) - recompute spans in onConfigurationChanged
- [SettingsInputRow greedy width](feedback_settingsinputrow_greedy_width.md) - internally match_parent; fix width in weighted rows
- [Canonical settings pickers](feedback_canonical_settings_value_pickers.md) - reuse ListSelectionDialog<T>+SettingsSelectionRow
- [No wrapper focus compound rows](feedback_compound_row_no_wrapper_focus.md) - wrapper NOT focusable; inner field is D-pad stop
- [Settings section deep-link](project_settings_section_deeplink.md) - EXTRA_INITIAL_TAB+EXTRA_EXPAND_SECTION; self-expands
- [Settings-search gate axes](project_settings_search_gate_axes.md) - 3 ANDed: section / CapabilityGate / DeviceFeatureGate
- [Reuse existing settings toggles](feedback_reuse_existing_settings.md) - grep AppSettings+fragment before adding
- [Resource vs Folder terms](feedback_resource_vs_folder_terminology.md) - resource=registered entity; folder=FS dir
- [MainActivity LOC ceiling](feedback_mainactivity_loc_ceiling.md) - ~1500; fold wiring into Main*Manager, not new field
- [Players are a family](feedback_player_family_glue_mirroring.md) - shared engine propagates; per-host mirrored manually
- [activity_welcome 3 widths](project_welcome_layout_variants.md) - layout/ + sw480dp/ + sw720dp/; new id in all three
- [Sync docs/site on visible change](feedback_sync_docs_on_visible_change.md) - revisit affected docs + site copy
- [HOW_TO path gate](reference_howto_settings_path_gate.md) + [parity](feedback_howto_settings_path_parity.md) - S0558 validates vs manifest; only U+2192+anchor lines need EN/RU/UK
- [Play edge-to-edge warnings](project_play_setstatusbarcolor_false_positive.md) - #2 fixed by Material 1.14.0; #1 informational
- [Land player bottom-band stacking](project_land_player_bottom_band_stacking.md) - anchor above bottomPanelsContainer, not parent bottom (S0368/S0852)

## Streams / VR / players
- [Stream catalog: all channels](feedback_stream_catalog_all_live_channels.md) + [publish](reference_stream_catalog_publish.md) - ship EVERY reachable; collect-stream-candidates.ps1 -WithFavicons -Publish
- [Favicon atlas delivery](project_stream_favicon_atlas_delivery.md) - streams all-flags-no-icons = zip missing favicon-atlas.png; re-publish; S0925 guards silent CSV-only
- [Favicon atlas publish](project_stream_catalog_atlas_publish.md) - atlas ships in stream-catalog.zip; publish w/o it wipes all favicons (S0925 guard); re-bundle -CatalogOnly -SkipLiveness -Publish
- [Streams device-test gate](project_streams_device_test_gate.md) - enableStreams gates menu; net kill via svc wifi/data
- [Stream radio vs video](project_stream_radio_vs_video_player_split.md) - radio(AUDIO)->InlineAudio; video/RTSP->BandwidthAdaptive; never shared
- [VR inclusion hierarchy](project_vr_inclusion_hierarchy.md) - noLegal all-inclusive sideload-VR; vrUnlicensed archived
- [supportsVrPlayer noLegal-only](project_supportsvrplayer_nolegal_only.md) - gate on VrMediaSectionContract.isAvailable
- [VR immersive re-entry](project_vr_immersive_reentry_hotspot.md) + [logcat trap](reference_vr_immersive_logcat_capture_trap.md) - recreate XrInstance per entry; raw adb logcat -b all
- [Quest panel opaque](reference_quest_panel_not_introspectable.md) + [HUD pitfalls](project_vr_hud_quirks.md) - uiautomator sees vrshell only; column-major, no per-frame queueHud, Skia RGBA
- [Player progressBar owner](project_player_progressbar_single_owner.md) - PlayerLoadingIndicatorCoordinator; PdfViewer rogue writer
- [Shared-state audit tool](reference_shared_state_audit_tool.md) - audit-shared-state-writers.ps1
- [Camera capture permission-free](project_camera_capture_permission_constraint.md) - declaring CAMERA breaks ACTION_IMAGE_CAPTURE
- [Headless capture + noHistory trap](project_headless_camera_capture_trampoline.md) - take-photo gestures use ImageCapture-only (no Preview); noHistory+startActivityForResult loses result (S0790-S0794)

## Spec lifecycle & catalog
- [Release scope 2026-07](project_release_scope_2026_07.md) - 11 gating tickets S0846..S0891; S0878 may inflate scope; verify statuses live
- [BNUT sweep plan 2026-07-02](project_bnut_sweep_plan.md) - 65 tickets triaged, 0 stale; plan temp/spec_sweep_batch_plan.md, 24 batches
- [Probe tags may be line-wrapped](feedback_probe_tag_multiline_grep.md) - grep `"Sxxxx:` too; single-line Timber.d pattern misses wrapped probes
- [Working tree is truth](feedback_dirty_tree_is_normal_wip.md) - never log/blame/diff for WIP; git only on explicit ask
- [Verify spec id before pipeline](feedback_verify_spec_id_before_pipeline.md) - select.ps1 first; match IDE-open Sxxxx
- [IDE Draft finalizes mid-task](feedback_ide_open_spec_may_finalize_midtask.md) - /spec-all rewrites Draft->Tactical; re-read
- [Draft style is approval-gate](feedback_draft_style_gate.md) - sanitation only at Draft->Approved; drafts stay rough
- [Spec Status auto-syncs](feedback_spec_header_autosync.md) - update.ps1 rewrites first **Status:** line
- [Strategic spec owner gate](feedback_strategic_spec_owner_gate.md) - §3.3 needs Related tickets (check-owner-inputs)
- [spec-tech plan quality](feedback_spec_tech_plan_quality.md) - keep 3.1-3.4/5.5 + research/; no doc-shuffling
- [/spec-dev verify code first](feedback_spec_dev_continue_verify_code_first.md) - In-Progress code may be done, tracking 0/N
- [Never call scaffolding done](feedback_no_scaffolding_as_done.md) - headline broken -> don't mark Done
- [Don't gate autopilot fake blocker](feedback_no_safety_blocker_gating_autopilot.md) - safe cleanup auto-chains /spec-dev
- [Search dup tickets by symptom](feedback_search_duplicates_by_symptom.md) - search catalog by errorCode/class first
- [Dead code may be scaffolding](feedback_dead_code_vs_active_tickets.md) - grep PLAN/ + Partial/In-Progress before delete
- [Block status before gate](feedback_blockneedusertest_status_before_gate.md) + [tags](feedback_timber_tags_before_test.md) + [phases](feedback_per_phase_debug_tags_break_gate.md) - flip status BEFORE audit; remove tags only on leaving Block; no mid-phase tags
- [close.ps1 two-step unblock](project_close_ps1_two_step_unblock.md) - no direct Block->Verified; via Implemented
- [Capability inventory](project_functionality_log.md) - docs/ALL_FEATURES.jsonl via all_features/add.ps1
- [noLegal features doc](feedback_features_nolegal.md) - docs/FEATURES* standard/VR; noLegal gitignored
- [spec_catalog exit-code](project_spec_catalog_exit_code_contract.md) + [insert -File](project_insert_ps1_file_validation.md) - trap{exit 1}+exit 0; next-id.ps1 first, real PLAN/Sxxxx_<slug>.md
- [Catalog scan roots](project_catalog_scan_source_sets.md) + [set.ps1 stops](project_catalog_set_ps1_stops_on_error.md) - scan.ps1 hard-codes $srcRoots; set.ps1 aborts on missing path, wrap try/catch
- [Big-file decomposition know-how](project_s0002_decomposition_toolkit.md) - compression tactics + extraction patterns; temp/ scripts wiped

## PowerShell / shell traps
- [PowerShell efficiency](feedback_pwsh_efficiency.md) - never plain pwsh -File; chain scripts in one process
- [LOC: Measure-Object -Line undercounts](feedback_pwsh_loc_measure_object.md) - use (Get-Content).Count; -Line gave 1330 for a 1483-line file (misdiagnosis)
- [CLI wrappers first](feedback_cli_project_wrappers_first.md) - prefer repo scripts / temp .ps1 over nested quoting
- [Check existing tooling first](feedback_check_existing_tooling.md) - grep scripts/+utils/+skills before authoring new
- [Cyrillic bash->pwsh boundary](feedback_cyrillic_bash_pwsh_boundary.md) - never pass RU/UK as pwsh CLI args from Bash; author .ps1
- [pwsh byte traps](feedback_pwsh_authoring_byte_traps.md) + [$-escape](feedback_pwsh_bash_dollar_escape_trap.md) + [backticks](feedback_no_backticks_in_bash_args.md) - Write escapes = control bytes; \$ collapses in bash -Command; bash substitutes backticks even quoted
- [pwsh param/local collision](feedback_pwsh_param_local_case_collision.md) - lowercase loop-local same as param corrupts it
- [string[] CSV via -File](feedback_string_array_param_csv_via_file.md) + [-DevLogs](feedback_devlogs_array_binding.md) - -File binds ONE element; close-and-log takes JSON-array string (fixed 2026-07-03); no wrapper scripts
- [pwsh shim in Git Bash](reference_pwsh_shim.md) - bare pwsh works via /c/Users/serzh/bin/pwsh
- [set-android-string.ps1](reference_strings_tool.md) - byte-preserving set/add/get across EN/RU/UK
- [String tools main/res only](feedback_string_tools_main_res_only.md) - ignore src/<flavor>/res; hand-edit + grep-verify
- [bash rg skips CATALOG](feedback_rg_gitignore_catalog.md) - use Grep tool / --no-ignore / Read
- [BG task exit = the echo](feedback_background_task_exit_code_is_echo.md) + [no probe echo](feedback_no_flush_echo_commands.md) - notification exit reflects trailing echo, read log; results arrive
- [Workflow journal recovery](reference_workflow_journal_recovery.md) + [args trap](reference_workflow_args_trap.md) - recover from journal.jsonl; args arrives string, resume drops args

## Subagents & process
- [Subagent skips final phase](feedback_subagent_impl_skips_final_phase.md) - impl agents truncate final cleanup; finish centrally
- [Parallel agents: no git/build](feedback_parallel_agents_no_git_build.md) - git stash clobbers; disjoint files, central build
- [Concurrent /spec-all reds tree](project_spec_all_concurrent_tree_red.md) - whole-tree fail may be sibling ticket's WIP; don't fix their files
- [Frozen app? check TracerPid](feedback_frozen_app_check_tracerpid.md) - GC ProfileSaver stall = native LLDB via ptrace
- [Welcome process consolidation](feedback_welcome_process_consolidation.md) - owner wants ceremony cut; may edit CLAUDE.md/skills
- [Workflow vs 5h limit](feedback_workflow_session_limit_budget.md) - LOW parallelism default, cap ~6-8 agents; ultracode doesn't lift it; owner GO above, never silent-resume
- [Verify with full evidence](feedback_verify_full_evidence.md) - skeptics read verbatim finding, address every mechanism; split vote -> tie-break by reading code myself
- [No ellipsis edits in code spans](feedback_no_ellipsis_edits_in_verbatim_code_spans.md) - `...` rule exempts code/specs; gate script fixed to skip backticks
- [Edit line-delete splice](feedback_edit_line_delete_splice.md) - old_string="\n..X" glues CRLF neighbours; match full next line, splice-sweep before compile
