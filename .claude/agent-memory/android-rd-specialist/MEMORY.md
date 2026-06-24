# Memory Index

- [About Me](about_me.md) - Serhii, solo owner of FastMediaSorter; data engineer, no Kotlin (code vibecoded), SQL/VB.NET, weak English; explain via SQL/.NET analogues
- [/spec-prerelease Maestro harness flaky on emulator](project_prerelease_maestro_harness_flaky.md) - Maestro FAIL = harness (onboarding-bypass decay [prepare fixed] + player-flow oracles), not app; verify manually, trust toastCount over actionable count, S0666
- [Play Console state: read-only via androidpublisher API](reference_play_console_api_access.md) - can read track/bundle states (temp/play_status.py); CANNOT see review verdicts (ask for screenshot)

- [SettingsInputRow greedy width starves siblings](feedback_settingsinputrow_greedy_width.md) - internally match_parent; use fixed width in weighted rows; layout-not-showing -> aapt2 the installed APK

- [Incremental build phantom unresolved-ref cascade](project_incremental_build_phantom_unresolved.md) - dq after multi-file edits throws phantom unresolved-ref; fix = clean build (cd)
- [fms.screenCapture gates gesture capability out of standard APK](project_screencapture_gates_gesture_capability.md) - default off unmounts standardScreenCapture; device tests need -P fms.screenCapture=on
- [close.ps1 two-step unblock for Verified](project_close_ps1_two_step_unblock.md) - refuses direct BlockNeedUserTest->Verified; go via Implemented

- [Stream catalog: ship all live channels](feedback_stream_catalog_all_live_channels.md) - owner wants EVERY reachable channel w/ signal; legal-scope filter removed (2026-06-22); only dead dropped

- [VR immersive re-entry hotspot (S0607)](project_vr_immersive_reentry_hotspot.md) - 2nd immerse hangs IDLE; reused XrInstance bound to finish()-ed Activity; recreate per entry, loader once w/ App ctx
- [VR immersive logcat capture trap](reference_vr_immersive_logcat_capture_trap.md) - "package:mine" drops immersive session; use raw adb logcat -b all; watch orphaned adb locks
- [Quest 2D panel not introspectable](reference_quest_panel_not_introspectable.md) - uiautomator sees only vrshell, screencap stereo; repro XR-gated UI on phone emulator or on-device Timber
- [No edge-to-edge UI elements](feedback_no_edge_to_edge_ui_elements.md) - nothing stretches full-bleed; bounded W+H; dropdowns sdr_fieldWidth 240/280; row-triggers exempt
- [No full-width buttons in landscape (S0605)](feedback_no_fullwidth_buttons_landscape.md) - landscape buttons wrap_content+gravity; keypad grids, nav rails, full-row items exempt
- [IDE-open Draft spec may finalize mid-task](feedback_ide_open_spec_may_finalize_midtask.md) - /spec-all can rewrite Draft to Tactical while coding; re-read before committing to a design
- [Emulator verifies MediaProjection screenshot](reference_emulator_mediaprojection_capture.md) - standard non-VR AVD verifies menu-screenshot end-to-end (consent+capture+PNG save)
- [Switch color theme on device for tests](feedback_color_theme_device_switch.md) - pref-file swap doesn't stick (DataStore re-syncs); change via Settings UI+restart; adb push needs MSYS_NO_PATHCONV=1
- [HOW_TO settings-path drift gate](reference_howto_settings_path_gate.md) - S0558 gate validates "Settings -> .." recipes vs manifest; extend howto-path-vocab.json on failure
- [HOW_TO settings-path parity gotcha](feedback_howto_settings_path_parity.md) - only U+2192+anchor lines are validated recipes needing EN/RU/UK parity; use ASCII > for quick nav
- [Flavor matrix: legacy+photos HAVE cloud](project_flavor_matrix_cloud_correction.md) - persona table stale; legacy=full set, photos=cloud+network, lite=only no-cloud; verify in build.gradle.kts
- [Play edge-to-edge warnings status](project_play_setstatusbarcolor_false_positive.md) - #2 setStatusBarColor FIXED by Material 1.14.0; #1 may-not-display informational + app-side-complete
- [Release gate: no coverage regression](feedback_release_no_coverage_regression.md) - STOP release if countries/age ratings/device reach (minSdk, ABI, uses-feature, flavor) shrink
- [screenCapture split standard vs noLegal](project_screencapture_nolegal_only.md) - src/screenCapture+MenuScreenshotLauncher standard+noLegal; gesture in standardScreenCapture (S0630); edge strip+a11y stay noLegal
- [Settings-search gate axes (S0597-S0604)](project_settings_search_gate_axes.md) - 3 ANDed gates: section / flavor-DI (CapabilityGate) / device-OS (DeviceFeatureGate); runtime-state = S0604

- [Working tree is truth - don't read git history for state](feedback_dirty_tree_is_normal_wip.md) - never log/blame/diff/status for WIP; single dev, many tickets/file; git only on explicit ask
- [Reuse existing settings toggles](feedback_reuse_existing_settings.md) - grep AppSettings+settings fragment for an existing toggle before adding one (S0523 cut 3 dups)
- [Writing style: hyphen / ё / ..](feedback_writing_style.md) - hyphen not em-dash, ё not е, `..` not `...`; self-check every chat/.md/commit
- [Per-phase debug tags break ticket-log gate](feedback_per_phase_debug_tags_break_gate.md) - no Timber.d("Sxxxx:") in intermediate phases; defer probes to final transition
- [Fast checks during dev](feedback_fast_checks_during_dev.md) - default a.ps1 fk/fr/fc/fu (~2-8s); reserve d/dav for packaging/install proof
- [No concurrent gradle invocations](feedback_no_concurrent_gradle_invocations.md) - never >1 gradle build (Kotlin-daemon OOM); post-change.ps1 is static, safe anytime
- [adb swiss-army CLI](reference_adb_swiss_army.md) - scripts/devtest/adb.ps1 + .\a.ps1 adb <verb> for quick device chores; prefer over raw adb
- [activity_welcome.xml has 3 width variants](project_welcome_layout_variants.md) - layout/ + sw480dp/ + sw720dp/; new view id must go in all three (no layout-land)
- [spec-tech plan quality discipline](feedback_spec_tech_plan_quality.md) - dominant defects: misordered phases + doc-shuffling steps; keep 3.1-3.4/5.5 + research/ convention
- [Permanent-log ticket-id gate](reference_ticket_log_gate.md) - assert-no-ticket-logs.ps1 fail-closed post-change; never embed Sxxxx in Timber.i/w/e
- [photos/lite OCR lives in src/ocrEnabled](project_photos_flavor_ocr_break.md) - Tesseract in src/ocrEnabled, NoOp in src/ocrDisabled for photos/lite; new Tesseract never in src/main
- [Players are a family - mirror per-host glue](feedback_player_family_glue_mirroring.md) - shared engine changes propagate; per-host delegate/layout must be mirrored manually (S0392)

- [Camera capture permission-free; in-app camera forces CAMERA](project_camera_capture_permission_constraint.md) - declaring CAMERA breaks ACTION_IMAGE_CAPTURE (S0359: CameraX sole path)
- [Draft style is approval-gate, not drafting friction](feedback_draft_style_gate.md) - spec `..`/ё+lists sanitation only at Draft->Approved; drafts may stay rough
- [Spec Status header auto-syncs from journal](feedback_spec_header_autosync.md) - update.ps1 rewrites first **Status:** line on every status change (fail-soft, first-match)

- [Bottom-sheet menu items untappable on emulator](feedback_bottomsheet_menu_untappable_emulator.md) - ResourceOperationsMenu ignores AVD taps; after 1+1 tries declare INCONCLUSIVE, keep BlockNeedUserTest
- [AVD media not MediaStore-indexed](feedback_avd_mediastore_not_indexed.md) - seeded media on disk but virtual resources show 0; register a folder resource or force a scan
- [Frozen app? check TracerPid / lldb-server](feedback_frozen_app_check_tracerpid.md) - frozen+no crash+log stops at "blocking GC ProfileSaver" = native LLDB holds VM via ptrace
- [bash rg skips gitignored CATALOG zone](feedback_rg_gitignore_catalog.md) - bare bash rg "no matches" in dev/CATALOG is not proof; use Grep tool / --no-ignore / Read
- [close-and-log -DevLogs array binding](feedback_devlogs_array_binding.md) - multi-element @(...) needs in-process & call, not pwsh -File; bash @(...) is a syntax error
- [string[] param CSV via pwsh -File](feedback_string_array_param_csv_via_file.md) - quoted CSV to [string[]] via pwsh -File binds as ONE element; pass @(..) or split in-script
- [Subagent impl skips final phase](feedback_subagent_impl_skips_final_phase.md) - impl subagents truncate final docs-cleanup; verify files exist, finish last phase centrally
- [Parallel impl agents: no git/build](feedback_parallel_agents_no_git_build.md) - one agent's git stash clobbers another's edits; disjoint files, central build; "IDE reverting" = concurrent stash
- [/spec-dev continue: verify code before checkboxes](feedback_spec_dev_continue_verify_code_first.md) - In-Progress may have code done but tracking 0/N; reconcile by reading live files, not git
- [Search duplicate tickets by symptom](feedback_search_duplicates_by_symptom.md) - before new bugfix spec, search catalog by errorCode/class/subsystem + same-day created
- [Dead code may be active-ticket scaffolding](feedback_dead_code_vs_active_tickets.md) - before deleting "0-ref" code, grep PLAN/ + cross-check Partial/In-Progress tickets
- [Don't gate autopilot on a manufactured safety blocker](feedback_no_safety_blocker_gating_autopilot.md) - safe cleanup plans auto-chain to /spec-dev; owner accepts destructive autopilot (S0383)
- [Prevent at source, not just detect](feedback_prevent_at_source_not_just_detect.md) - after a quality gate, add the DON'T rule to CLAUDE.md + code-gen skills (S0383 Rule 20)

- [Background task exit code is the echo](feedback_background_task_exit_code_is_echo.md) - notification "exit 0" reflects trailing echo, not gradle; read the log for the real verdict
- [No flush/probe echo commands](feedback_no_flush_echo_commands.md) - don't spam empty echo to force output; results arrive on their own

- [spec_catalog exit-code contract](project_spec_catalog_exit_code_contract.md) - mutators need trap{exit 1}+exit 0 (_lib.ps1 Stop pref makes Write-Error terminating)
- [insert.ps1 -File validation](project_insert_ps1_file_validation.md) - rejects skill-doc placeholder; run next-id.ps1 first, pass real PLAN/Sxxxx_<slug>.md
- [pwsh shim in Git Bash](reference_pwsh_shim.md) - bare `pwsh` works via /c/Users/serzh/bin/pwsh (since 2026-05-21)
- [Never remove Timber.d tags while BlockNeedUserTest](feedback_timber_tags_before_test.md) - removal is a side effect of leaving that status, never speculative
- [Build gotchas](project_build_gotchas.md) - build-debug.PS1 flaky "daemon stopped" -> retry; dev/CATALOG/*.jsonl+.md gitignored
- [Unmask kapt stackless NPE](project_kapt_npe_unmask.md) - "Cannot read field tree" NPE masked; -XX:-OmitStackTraceInFastThrow + correctErrorTypes=false reveal real javac error
- [AVD device-sweep gotchas](feedback_avd_device_sweep_gotchas.md) - headless Pixel_4: touch wedge, ACCESS_LOCAL_NETWORK for SMB/SFTP/FTP, logcat death recovery, mcp coords top-left
- [/skill-release gotchas](project_skill_release_gotchas.md) - version skew tag vs artifact; DEBUG-not-rebased merge conflict; gitignored PLAN/ makes Step 12a diff empty
- [noLegal features go to FEATURES_noLegal.md only](feedback_features_nolegal.md) - docs/FEATURES*.md are standard/VR; noLegal docs live in gitignored docs/FEATURES_noLegal.md
- [Timestamp every chat message](feedback_timestamp_in_chat.md) - prefix each response with [HH:MM:SS]
- [Flavor isolation: strict source-set discipline](feedback_flavor_isolation_strict.md) - flavor code in src/<flavor>/java/; no BuildConfig flavor guards in src/main (Rule 15)
- [AGP manifest.srcFile replaces flavor manifest](project_agp_manifest_srcfile_overrides_flavor_manifest.md) - noLegal srcFile drops src/noLegal/AndroidManifest.xml; use addStaticManifestFile in onVariants
- [Capability inventory](project_functionality_log.md) - FUNCTIONALITY.log RETIRED (S0489); capabilities now docs/ALL_FEATURES.jsonl via all_features/add.ps1
- [No backticks in Bash-tool args](feedback_no_backticks_in_bash_args.md) - bash command-substitutes `text` even quoted; descriptions with backticks lose words
- [Pre-existing test failures policy](feedback_build_pre_existing_test_failures.md) - testStandardDebugUnitTest carries ~26 broken tests; verify own work via per-class XML, use assembleStandardDebug
- [Catalog scan source sets](project_catalog_scan_source_sets.md) - scan.ps1 hard-codes source roots; new buckets must be added to $srcRoots
- [set.ps1 stops on error](project_catalog_set_ps1_stops_on_error.md) - throws & aborts batch on missing path; wrap in try/catch for multi-entry fills
- [MSAL signing-hash per keystore](project_msal_signing_hash_per_keystore.md) - each signingConfig = distinct BrowserTabActivity hash; manifest + Azure must declare every variant
- [Don't ask owner questions architecture already answers](feedback_no_owner_questions_when_architecture_already_answers.md) - if flavor hierarchy/contracts already determine it, don't fabricate a choice question
- [Verify sub-agent build failures yourself](feedback_verify_subagent_build_failures.md) - agent's kapt cache may be stale; re-run `.\a.ps1 dq` before treating as hard stop
- [material-icons-extended is NOT removable](project_material_icons_extended_not_removable.md) - Pause/SkipNext/SkipPrevious are extended-only, not core; "replaceable" audit broke build (S0385)
- [Remove dead applications/config too](feedback_remove_dead_applications_too.md) - dead plugin applications + unused buildscript classpath removed; "is it dead" not "does it save bytes"
- [PowerShell efficiency: -NoProfile + batching](feedback_pwsh_efficiency.md) - never plain pwsh -File; chain related scripts in one process; use catalog_sync.ps1 for scan+render
- [Don't infer architecture from BuildConfig names](feedback_dont_infer_from_buildconfig_names.md) - grep usage before treating as gate; PLAYER_ACTIVITY_CLASS is a dead field
- [Build output pipe truncation](feedback_build_output_pipe_truncation.md) - never tail -N to investigate gradle failures; FAILURE block sits in the middle
- [VR inclusion hierarchy: standard subset vr subset noLegal](project_vr_inclusion_hierarchy.md) - S0240; noLegal is all-inclusive sideload-VR; vrUnlicensed archived (S0250)
- [Reserve Timber.e for real errors only](feedback_log_levels.md) - expected device-capability fallbacks log at Timber.i; ERROR is for things the dev must act on
- [Never call scaffolding "done"](feedback_no_scaffolding_as_done.md) - if headline behavior isn't working, don't mark phases Done or invite device-test
- [Check generated binding field types before injecting compat views](feedback_check_generated_binding_types.md) - .bind(root) unchecked downcasts; Button vs MaterialButton crashes silently
- [Strategic spec Draft -> Approved owner gate](feedback_strategic_spec_owner_gate.md) - §3.3 Owner inputs relevance-driven; check-owner-inputs.ps1 validates + always requires Related tickets
- [pwsh-bash dollar-escape trap](feedback_pwsh_bash_dollar_escape_trap.md) - inside bash -Command, \$LASTEXITCODE collapses to empty + silent parse fail in & {}; use single-quoted bash
- [Verify spec id before announcing /spec-* pipeline](feedback_verify_spec_id_before_pipeline.md) - run select.ps1 first; match IDE-open Sxxxx; never narrate "Stage 0" on unresolved id
- [Persistent log lines must not contain Sxxxx](feedback_persistent_logs_no_ticket_id.md) - ticket id in Timber.* is reserved for BlockNeedUserTest probes; permanent logs use plain English
- [Welcome process consolidation](feedback_welcome_process_consolidation.md) - owner wants ceremony cut, authorizes editing CLAUDE.md/agent-defs/skills; keep read-only vs mutation boundaries
- [VR HUD rendering pitfalls](project_vr_hud_quirks.md) - column-major multiply_matrices, no per-frame queueHud from native callbacks, allocateDirect not wrap, Skia is RGBA
- [S0002 decomposition toolkit](project_s0002_decomposition_toolkit.md) - reusable scripts + extraction patterns + Wave 54 backlog (TextViewer/PdfViewer/PlayerActivity over 1000)
- [adb location + .debug package suffix](reference_adb_and_debug_package.md) - adb not on PATH; debug installs as com.sza.fastmediasorter.debug; Quest3 logcat short, prefer app file logs
- [setup_test_media.ps1](reference_setup_test_media.md) - seeds structured test-media tree on connected devices; source c:\Common\test_media; maps to PRE_RELEASE_MANUAL_TESTS.md
- [set-android-string.ps1 editor](reference_strings_tool.md) - canonical byte-preserving set/add/get/remove/rename/list across EN/RU/UK; prefer over hand-editing strings.xml
- [Check existing tooling first](feedback_check_existing_tooling.md) - grep scripts/ + scripts/utils/ + skills for a helper before authoring a new script (I dup'd set-android-string.ps1)
- [Research over owner-question on design forks](feedback_research_over_owner_question.md) - best-practice/granularity forks: research the convention and recommend (S0339)
- [String tools cover src/main/res only](feedback_string_tools_main_res_only.md) - check_strings_localized + set-android-string ignore src/<flavor>/res; hand-edit, grep-verify parity
- [Cyrillic corrupts through bash->pwsh args](feedback_cyrillic_bash_pwsh_boundary.md) - never pass RU/UK literals as pwsh CLI args from Bash (mojibake); author UTF-8 .ps1 via Write
- [pwsh authoring byte traps](feedback_pwsh_authoring_byte_traps.md) - Write-tool hex/Unicode escapes can land as control bytes; array-splat re-parses dash values (use hashtable splat)
