---
name: android-rd-specialist-memory
description: Detailed developer guidelines, project facts, build gotchas, feedback, and AVD test device details for FastMediaSorter v2 Android development.
---

# Android R&D Specialist Memory

This skill provides access to the accumulated developer knowledge, feedback, gotchas, and references for FastMediaSorter v2.

The memory is one set, shared by every runtime. It lives in `.claude/agent-memory/android-rd-specialist/` - a plain directory, Claude-specific in its path only. There is no second copy: `.agents/MEMORY.md` is a pointer to this same place (S2622).

**Open it only when the task needs it** (`docs/NON_CLAUDE_RUNTIME_RULES.md` rule 18). Every line you read stays in your context and is resent on every later request, so loading the index plus three topic files "to be safe" cost the S3103 session about 2.4M tokens for a two-line change (S3177). Trivial path: do not open memory. Simple path: at most the one `INDEX_*.md` for the module you touch, then only the file a matching line names. Full path, or a build/device failure you do not understand: the index first, then only the matching file.

- [MEMORY.md](file:///P:/ANDROID/FastMediaSorter_mob_v2/.claude/agent-memory/android-rd-specialist/MEMORY.md) (Index - read this first; every line points at a file in the same directory)
- [about_me.md](file:///P:/ANDROID/FastMediaSorter_mob_v2/.claude/agent-memory/android-rd-specialist/about_me.md) (Owner persona)
- [project_build_gotchas.md](file:///P:/ANDROID/FastMediaSorter_mob_v2/.claude/agent-memory/android-rd-specialist/project_build_gotchas.md) (Gradle, detekt, and compiler gotchas)
- [reference_test_device_galaxy_s21.md](file:///P:/ANDROID/FastMediaSorter_mob_v2/.claude/agent-memory/android-rd-specialist/reference_test_device_galaxy_s21.md) (Test device setup)
- All feedback, project and reference files live in the [same directory](file:///P:/ANDROID/FastMediaSorter_mob_v2/.claude/agent-memory/android-rd-specialist), grouped by topic through the `INDEX_*.md` second-level indexes that `MEMORY.md` names.
