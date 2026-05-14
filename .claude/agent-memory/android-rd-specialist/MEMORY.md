# Memory Index

- [Never remove Timber.d tags while spec is BlockNeedUserTest](feedback_timber_tags_before_test.md) — tags bound to BlockNeedUserTest; removal is a side effect of leaving that status, never speculative
- [Build gotchas](project_build_gotchas.md) — build-debug.PS1 flaky "daemon stopped" → retry; dev/CATALOG/*.jsonl+.md are gitignored
- [noLegal features go to FEATURES_noLegal.md only](feedback_features_nolegal.md) — docs/FEATURES*.md are for standard/VR published builds; noLegal docs live in gitignored docs/FEATURES_noLegal.md
- [Timestamp every chat message](feedback_timestamp_in_chat.md) — prefix each response with [HH:MM:SS] so user can track time spent per step
- [Flavor isolation: strict source-set discipline](feedback_flavor_isolation_strict.md) — VR/noLegal/lite/photos/legacy code lives in src/<flavor>/java/; BuildConfig flavor guards in src/main forbidden (CLAUDE.md Rule 15)
- [AGP manifest.srcFile replaces flavor manifest](project_agp_manifest_srcfile_overrides_flavor_manifest.md) — noLegal flavor's srcFile(vr-manifest) silently drops src/noLegal/AndroidManifest.xml; use addStaticManifestFile in onVariants
- [Functionality log](project_functionality_log.md) — dev/FUNCTIONALITY.log: developer-facing ADD/CHANGE/DELETE/FIX history of user-visible capability lifecycle; written via scripts/add_to_functionality_log.ps1
- [No backticks in Bash-tool args](feedback_no_backticks_in_bash_args.md) — bash performs command substitution on `text` even in quoted strings; descriptions with backticks lose words silently
