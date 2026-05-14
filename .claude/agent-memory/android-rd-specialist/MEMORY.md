# Memory Index

- [Never remove Timber.d tags while spec is BlockNeedUserTest](feedback_timber_tags_before_test.md) — tags bound to BlockNeedUserTest; removal is a side effect of leaving that status, never speculative
- [Build gotchas](project_build_gotchas.md) — build-debug.PS1 flaky "daemon stopped" → retry; dev/CATALOG/*.jsonl+.md are gitignored
- [noLegal features go to FEATURES_noLegal.md only](feedback_features_nolegal.md) — docs/FEATURES*.md are for standard/VR published builds; noLegal docs live in gitignored docs/FEATURES_noLegal.md
- [Timestamp every chat message](feedback_timestamp_in_chat.md) — prefix each response with [HH:MM:SS] so user can track time spent per step
- [Flavor isolation: strict source-set discipline](feedback_flavor_isolation_strict.md) — VR/noLegal/lite/photos/legacy code lives in src/<flavor>/java/; BuildConfig flavor guards in src/main forbidden (CLAUDE.md Rule 15)
