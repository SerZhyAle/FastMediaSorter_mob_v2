---
name: index-build-flavors
description: Second-level pointer list for build, flavor and packaging memories - gradle locks, flavor capability grids, BuildConfig gates, native .so packaging, R8 output, kapt recovery and test-set traps. Open when building, changing a flavor, or reading a build failure.
metadata:
  type: reference
---

# Build, flavors, packaging - pointers

Split out of `MEMORY.md` (2026-08-21): the top-level index is billed on every turn of every session,
and these are needed only when the work actually builds, packages or changes a flavor. The hooks here
are each memory's own `description`, restored in full - the top-level index had squeezed several of
them mid-word, which left the pointer costing its bytes while saying nothing.

- [Not your CODE.LOCK](feedback_code_lock_release_ownership.md) - Never call exit-code-lock.ps1 blindly - post-change already released yours and a parallel session may now hold CODE.LOCK
- [No concurrent gradle](feedback_no_concurrent_gradle_invocations.md) - Never run several gradle-backed builds at once - multiple Kotlin daemons OOM/corrupt the machine; post-change.ps1 is mostly static BUT its settings-doc-sync gate runs gradle
- [Don't idle on a lock](feedback_do_not_idle_on_a_lock.md) - Owner interrupts with "не жди" when a turn is spent waiting on CODE.LOCK/BUILD.LOCK - keep the ticket moving on lock-free work instead of blocking
- [r/nl/vr build the release worktree](feedback_release_targets_build_the_worktree_not_your_tree.md) - a.ps1 r/nl/vr delegate into the release worktree and build ITS tree - your uncommitted dev-checkout edits are invisible to them, and they copy artifacts to DOWNLOADS/Google Drive.
- [Lock per step](feedback_code_lock_is_per_step_not_per_ticket.md) - CODE.LOCK is released by post-change.ps1's closure, so it must be re-acquired before every step; and a queue ticket is owned by the SESSION, so waiting silently in the background gets you evicted from your own slot
- [Never batch lock](feedback_never_batch_code_lock_with_the_edit.md) - Never put enter-code-lock.ps1 and the edits it guards in the same message - a queued refusal still lets the edits land
- [Lock denial](feedback_code_lock_denial_does_not_stop_the_batch.md) - enter-code-lock.ps1 exit 4 prints a friendly "queued" message; a multi-line PowerShell tool call sails past it and the source edits land unlocked
- [agent-lock has no CLI](project_agent_lock_release_lies.md) - agent-lock.ps1 is a dot-source library with no CLI; running it as a script now fails with exit 2 (was a silent exit 0) - exit-code-lock.ps1 is the releaser (S1505, fixed 2026-08-08)
- [Installable artifact = stamped version](feedback_every_installable_artifact_carries_its_build_timestamp.md) - Any artifact the owner can install or test must carry its real build date-time in versionName/versionCode - a frozen checked-in version on an installable APK is a defect, not a trade-off
- [No redundant flavor compile](feedback_no_redundant_flavor_compile.md) - Don't run noLegal/other-flavor compile (fkn) for src/main-only changes; standard fc already proves it
- [BuildConfig names](feedback_dont_infer_from_buildconfig_names.md) - BuildConfig field names can be misleading - some are dead/legacy; grep usage before treating as gate
- [Lowest flavor wins](feedback_push_features_to_lowest_flavor.md) - When drafting/breaking down specs, place new functionality at the lowest (broadest) flavor level it can legally live in - don't default to noLegal; if the level isn't obvious, ASK the owner
- [Capability != Availability](project_flavor_flags_ratchet_blocks_capability_availability.md) - A new flavor capability cannot be added as a CapabilityAvailability accessor - the flavor-flags gate is a down-only ratchet that refuses to raise its baseline
- [Flavor grid](project_flavor_matrix_cloud_correction.md) - never answer a flavor-capability question from memory - docs/FLAVOR_MATRIX.md is generated from productFlavors and gated; binary file types are the one thing not capability-gated
- [photos/lite OCR](project_photos_flavor_ocr_break.md) - RESOLVED 2026-06-10 - Tesseract OCR moved to ocrEnabled/ocrDisabled source buckets so photos/lite build
- [S0386 native-attach API36](project_s0386_native_attach_broken_api36.md) - S0386 runtime native-lib attach (injectNativeLibraryDirectory) fails on real arm64/API36; OCR/DTS crash guarded by S0923, Layer 2 pending
- [.so bundle vs on-demand](project_native_so_bundle_standard_vs_ondemand_nolegal.md) - Play forbids downloading executable .so - native decoders must be BUNDLED in standard APK; only noLegal can deliver on-demand (S0971)
- [screenCapture standard too](project_screencapture_nolegal_only.md) - Two flags - fms.screenCapture=on ships menu-capture in standard; fms.edgeGestureOverlay=on (since ~2026-06-26, confirmed 2026-07-01) NOW ships left-edge gesture actions in standard too
- [gates gesture](project_screencapture_gates_gesture_capability.md) - fms.screenCapture=on in gradle.properties (default), so a normal standard build DOES compile the standardScreenCapture twin/gesture-overlay; fms.edgeGestureOverlay/Tile are the off flags
- [manifest.srcFile wins](project_agp_manifest_srcfile_overrides_flavor_manifest.md) - AGP manifest.srcFile() in a productFlavor sourceSet REPLACES the auto-detected src/<flavor>/AndroidManifest.xml - to add an extra manifest source use androidComponents.onVariants { variant.sources.manifests.addStaticManifestFile(...) }
- [MSAL hash](project_msal_signing_hash_per_keystore.md) - Each signing config (debug/release/debugCustom/etc) produces a distinct MSAL BrowserTabActivity signature hash - manifest must register all of them
- [material-icons](project_material_icons_extended_not_removable.md) - material-icons-extended is NOT dead - Pause/SkipNext/SkipPrevious media icons are extended-only, not in material-icons-core
- [Deleted resource still ships](project_stale_merged_resource_outlives_its_source.md) - A deleted res/ file keeps shipping - its .flat artifact stays in merged_res forever, so an old layout variant can win over the current one at runtime
- [R8 mapping is 174 MB](project_r8_mapping_is_174mb.md) - The standard-release R8 mapping.txt is ~174 MB - any script that inspects it must stream, never load it into an array
- [fc after resource not fast](feedback_fc_after_a_resource_change_is_not_a_fast_check.md) - The 14-21 s figure for fk/fc holds only for code-only edits; adding a layout or editing a menu regenerates R and forces a full module recompile, so run it in the background
- [JAVA_HOME stale in a running session](project_java_home_stale_in_running_session.md) - gradlew dies naming a JDK that no longer exists, but the machine setting is already right; only this session's inherited copy is stale - fix inline, never rewrite machine config
- [Build gotchas](project_build_gotchas.md) - build-debug.PS1 daemon stop (retry); dev/CATALOG/*.jsonl + *.md gitignored; Chaquopy gate disables all non-noLegal variants; :app_v2:dependencies exits 0 on a FAILED edge; configurations.all also hits test classpaths
- [pipes hide exits](feedback_build_output_pipe_truncation.md) - Piping gradle to `tail -N` hides the FAILURE block and can hang a background task; redirect to a file from Bash, but run plain in the foreground from the PowerShell tool
- [a.ps1](feedback_aps1_launcher_pwsh_cwd.md) - a.ps1 build launcher must be run via pwsh from the repo root; bash invocation or wrong cwd silently no-ops with exit 0
- [Gradle via pwsh](feedback_gradle_via_powershell_not_bash.md) - Run gradle-backed scripts (a.ps1 builds, checks, lint) through the PowerShell tool - the Bash tool's JAVA_HOME points at a JDK that does not exist
- [Unmask kapt NPE](project_kapt_npe_unmask.md) - How to unmask a stackless kapt NullPointerException ("Cannot read field tree") on this AGP9/legacy-kapt toolchain
- [subagent claims](feedback_verify_subagent_build_failures.md) - Never trust a delegated sub-agent's build claim (pass OR fail) or its root-cause diagnosis - self-verify with your own `.\a.ps1 dq` / your own read of the code before acting on it.
- [Remove dead config](feedback_remove_dead_applications_too.md) - Dead-code cleanup includes dead build-config (unused plugins, buildscript classpath, config) even when it does not change APK/AAB size
- [Ctor change -> tests](feedback_constructor_change_compile_tests.md) - assembleStandardDebug does NOT compile test sources; after changing a class constructor/signature also run testStandardDebugUnitTest or compileStandardDebugUnitTestKotlin or unit tests break silently
- [pre-existing](feedback_build_pre_existing_test_failures.md) - When testStandardDebugUnitTest fails due to pre-existing failures unrelated to current spec, do not hard-stop - verify own changes via XML reports and use assembleStandardDebug for Phase Done compile checks
- [Flavor test set](feedback_flavor_only_code_needs_its_own_test_set.md) - A unit test for a class in a flavor-mounted source set (launcherEnabled, networkMonitor, vr, ocrEnabled..) must live in the matching test source set, never in the shared src/test.
- [`$stable` reflection](feedback_compose_stable_field_in_reflection_tests.md) - A test that walks declaredFields in this project must skip static fields - the Compose compiler adds a $stable static to classes, and it fails any "every field is annotated" assertion
- [Sandbox deps](feedback_sandbox_tests_carry_a_dependency_manifest.md) - Adding a dot-source to a script silently breaks its sandbox-based Pester tests until the sandbox's copy list names the new library
- [fk misses Hilt](feedback_fk_does_not_validate_hilt_graph.md) - a.ps1 fk/fkn compile Kotlin only - a broken or variance-mismatched Dagger binding passes them and fails later at hiltJavaCompile
