---
name: Build gotchas — Gradle daemon stop, catalog gitignored
description: build-debug.PS1 sometimes dies on "build daemon has been stopped" mid-Kotlin-compile (retry succeeds); dev/CATALOG/*.jsonl + *.md are gitignored
type: project
---

Two build/tooling gotchas observed while running `/spec-dev` on S0143 (2026-05-10).

1. **`build-debug.PS1` flaky daemon stop.** The Gradle build sometimes fails with `FAILURE: ... Gradle build daemon has been stopped: stop command received` (or a Windows file-lock `NoSuchFileException` in `temp/gradle-tmp/kotlin-backups/*.backup`) at the `:app_v2:compileStandardDebugKotlin` step. Likely cause: the IDE (VSCode/Android Studio with the project open) runs its own Gradle sync after file changes and stops daemons. `build-debug.PS1` only auto-retries for `Failed to store cache entry` and `kapt3/incrementalData` lock errors — **not** for daemon-stop. **Fix:** run `./gradlew --stop`, ensure `temp/gradle-tmp` exists (`mkdir -p temp/gradle-tmp`), then re-run `build-debug.PS1` — it succeeds on retry. A `e: Daemon compilation failed: null` / `Backend Internal error: IR fake override builder` line can appear and the build still succeeds (falls back to in-process compile). Don't treat a single daemon-stop as a real build failure — retry first.
   **How to apply:** When a build fails with daemon-stop / kotlin-backups file-lock, retry once before reporting a build failure.

2. **`dev/CATALOG/app_v2.jsonl` and `app_v2.md` are gitignored** (confirmed via `git check-ignore`). CLAUDE.md says "commit updated dev/CATALOG/<module>.jsonl + <module>.md together with the code change" — but they're not tracked, so there's nothing to commit. Still run `scan.ps1` + `render.ps1` after `.kt` changes (the catalog is a local index used by `query.ps1`), just don't expect/require a git diff for them.
   **How to apply:** After `.kt` edits, regenerate the catalog but skip the "commit catalog files" step for app_v2 — they're gitignored.
