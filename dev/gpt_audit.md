# GPT Audit: Research and Development Speed

Generated: 2026-05-20
Observed branch: DEBUG-v004
Perspective: GPT agent operating inside GitHub Copilot on this workspace
Scope: local computer, Windows and VS Code environment, repository rules and scripts, catalogs, MCP setup, and transcript-backed request history.

## Executive Summary

The workstation is already strong enough for aggressive local Android research and validation: Windows 11 Pro, 13th Gen Intel Core i5-13600K, 20 logical processors, about 64 GB RAM, PowerShell 7.6.1, Git 2.53, Java 21.0.10, `rg` 15.1.0, Node 24.14.0, and a working Android/ADB toolchain. The biggest speed losses are not hardware limits. They are workflow friction:

- workspace noise is higher than necessary,
- source-of-truth docs drift from real Gradle state,
- PowerShell-heavy rituals are still fragmented,
- VS Code is optimized for safety but partially blind to flavor-specific source sets,
- the worktree is highly concurrent,
- transcript history shows a resume-heavy, spec-driven owner workflow that needs continuity tooling more than generic blank-slate discovery.

The highest-return changes are not abstract AI improvements. They are concrete repo-level improvements:

1. shrink search and watcher noise,
2. make resume context first-class,
3. unify post-change validation rituals,
4. keep docs synchronized with implementation truth,
5. route GPT to the right prompt, catalog, log, and build path immediately.

## Observed Environment

- OS: Windows 11 Pro, 64-bit.
- CPU/RAM: Intel i5-13600K, 20 logical processors, about 64 GB RAM.
- Java on PATH: Oracle Java 21.0.10.
- Gradle wrapper: 9.4.1.
- Root plugin versions observed in implementation:
  - AGP 9.2.1
  - Kotlin 2.2.10
  - KSP 2.3.8
  - Hilt 2.59
- CLI tools observed:
  - PowerShell 7.6.1
  - Git 2.53.0.windows.2
  - ripgrep 15.1.0
  - Android Debug Bridge 1.0.41
  - Node 24.14.0
  - Python 3.10.11
- Current repository scale observed during audit:
  - about 9,251 files total
  - 1,352 Kotlin files
  - 571 XML files
  - 456 Markdown files
  - 108 PowerShell scripts
- Current worktree dirtiness: 125 modified files.

### High-noise directories observed

- `temp/`: 8,320 files, about 2.2 GB
- `DOWNLOADS/`: 18 files, about 2.1 GB
- `.git/`: 9,060 files, about 914 MB
- `.gradle/`: 706 files, about 511 MB
- `.venv/`: 1,285 files, about 27 MB

These numbers matter because agent startup and repo search quality are dominated by what the workspace exposes by default.

## Research Speed and Quality

### What is already good

- `dev/PROJECT_OPERATIONS_INDEX.md` is the correct fast-routing entrypoint.
- `dev/CATALOG/` is valuable and already semantically richer than raw grep for class ownership, DI, tests, side effects, and layer awareness.
- Prompt routing is explicit: `/catalog`, `/build`, `/log-reader`, `/spec-*`, `/ui-clarify`, `/doc-update`, and `/research` are all well defined.
- `.vscode/mcp.json` already exposes useful MCP building blocks:
  - `docs-search`
  - `filesystem_rw`
  - `filesystem_ro`
  - `gradle_safe`
- Build wrappers already exist for standard, lite, photos, legacy, noLegal, VR, wear, device install, and Gradle recovery.

### Friction points

1. Search noise is still too high.
   - `.vscode/settings.json` excludes `build`, `.gradle`, `.idea`, `V1`, `v2_6`, and `spec_v2`, but not `temp`, `DOWNLOADS`, `.venv`, `logs`, or local `node_modules` trees.
   - For GPT, this increases retrieval noise and lowers first-pass precision.

2. The repo overstates catalog-first as a universal speed rule.
   - In one measured lookup, catalog query took about 423 ms while a targeted `rg` class search took about 44 ms.
   - The right rule is narrower:
     - use catalog first for semantic lookup,
     - use `rg` first for exact filename, exact class, exact token, and literal string lookup.

3. Documentation truth is drifting from implementation truth.
   - `dev/TECH_REQUIREMENTS.md` still contains stale AGP, KSP, Hilt, Compose, and Room values relative to actual Gradle files.
   - This directly reduces research quality because GPT must distrust docs and reopen implementation files.

4. VS Code Java setup is fast but only partially flavor-aware.
   - `java.import.gradle.enabled=false` and `java.autobuild.enabled=false` are good for speed.
   - But `java.project.sourcePaths` only includes a narrow set of source roots and does not reflect the real `sourceSets` wiring in `app_v2/build.gradle.kts`.
   - GPT can still reason correctly, but editor-assisted navigation and diagnostics are narrower than the real build graph.

5. MCP knowledge coverage is useful but incomplete.
   - `docs-search` is scoped to `docs/`, while GPT also needs fast read-only access to `dev/`, `PLAN/`, root rule files, spec catalog, class catalog, and activity catalog.

### Recommendations for research speed and quality

1. Expand `.vscode/settings.json` exclusions for:
   - `**/node_modules/**`
   - `**/.venv/**`
   - `**/temp/**`
   - `**/DOWNLOADS/**`
   - `**/logs/**`

2. Change the lookup rule from "catalog first always" to "catalog first for semantic lookup".
   - Keep catalog-first for:
     - injected type lookup
     - side effects
     - test coverage
     - class role/status
     - decomposition candidates
   - Prefer `rg` first for:
     - exact class name
     - exact file name
     - exact string/resource key
     - exact log tag/token

3. Add `scripts/agent_bootstrap.ps1` that prints one compact startup packet:
   - branch
   - dirty-tree summary
   - module and flavor hints
   - active `Sxxxx` hints
   - mandatory prompt route
   - recommended first files
   - warnings when docs and Gradle disagree

4. Expand repo knowledge search beyond `docs/`.
   - Either broaden `docs-search` or add a read-only `repo-knowledge` MCP that includes:
     - `CLAUDE.md`
     - `AGENTS.md`
     - `.github/copilot-instructions.md`
     - `dev/PROJECT_OPERATIONS_INDEX.md`
     - `dev/AGENT_WORKFLOW.md`
     - `PLAN/spec-catalog.jsonl`
     - `dev/CATALOG/*`
     - `dev/ACTIVITY_CATALOG/*`

5. Add a docs-vs-Gradle drift check for:
   - AGP
   - Kotlin
   - KSP
   - Hilt
   - Room
   - compileSdk / targetSdk / minSdk
   - Gradle wrapper version

## Development Speed and Quality

### What is already good

- `a.ps1` gives a useful command facade.
- `scripts/catalog_sync.ps1` already encodes the correct scan-plus-render wrapper.
- `scripts/utils/recover-kapt-stall.ps1` provides a documented recovery path.
- Gradle wrappers and builder scripts cover the main flavor matrix.
- The repo has a strong validation culture and explicit post-change rituals.

### Friction points

1. Post-change closure is still too fragmented.
   - Dev log, catalog sync, string audits, feature logs, spec status, and targeted validation are documented well.
   - They are not yet one reliable task-aware closure path.

2. `scripts/post-change.ps1` is not yet the obvious single source of truth.
   - It should be the best abstraction for GPT after edits.
   - Today the repo still pushes the agent toward manually composing several steps.

3. The worktree is highly concurrent.
   - 125 modified files is not a blocker.
   - It is a real quality risk unless GPT sees nearby user changes before touching the same area.

4. Several Kotlin files are already large enough to slow both reasoning and validation.
   - Local hotspots observed at or above roughly 1,000 LOC include:
     - `PlayerActivity.kt`
     - `PdfViewerManager.kt`
     - `TextViewerManager.kt`
     - `ImageLoadingManager.kt`
     - `CommandPanelController.kt`
     - `PlayerManagerInitializer.kt`
     - `GoogleDriveRestClient.kt`
     - `MediaFileAdapter.kt`
     - `SmbConnectionManager.kt`
     - `DropboxClient.kt`
   - The repo rule caps large files around 1,500 LOC, and one debug-side file is already far above that threshold.

5. Configuration-cache constraints still tax the normal loop.
   - noLegal and Chaquopy constraints may justify this globally.
   - But if standard/lite/photos can regain faster startup safely, that is high-value work.

### Recommendations for development speed and quality

1. Turn post-change into one preferred path.
   - Update `scripts/post-change.ps1` so it:
     - always uses `pwsh -NoProfile`
     - calls `scripts/catalog_sync.ps1`
     - emits exact exit codes
     - chooses the minimal safe validation path by change type

2. Add `scripts/agent_validate.ps1`.
   - Inputs:
     - change type
     - module
     - flavor
     - optional test pattern
   - Outputs:
     - exact command run
     - expected vs actual result
     - pass or fail

3. Add a dirty-tree guard.
   - Before edits, classify target files as:
     - clean
     - already modified by user
     - adjacent to modified files
     - high-risk overlap

4. Restore the fastest safe validation path for common loops.
   - Add wrappers for:
     - compile standard debug Kotlin
     - compile noLegal debug Kotlin
     - compile VR debug Kotlin
     - targeted unit test by pattern
     - wear assemble debug

5. Treat large-file decomposition as a speed project, not only a maintainability project.
   - Prioritize very large or compiler-heavy classes that appear repeatedly in builds and logs.

## Conversation and Request-History Analysis

### Evidence base

- Transcript-backed history available: 21 transcript files.
- Sessions with explicit user prompts: 17.
- User messages total: 81.
- Language mix:
  - 45 messages with Cyrillic
  - 36 Latin-only messages
- Session-store metadata also exists, but transcript files are the more reliable source for actual prompt content.

### Important caveat

Transcript history is noisy because some session entry prompts are not true new requests. They are operational artifacts.

Session-entry prompt classification observed:

- `terminal_notification`: 5
- `continue_prompt`: 4
- `natural_language_ru`: 3
- `ticket_directive`: 3
- `natural_language_other`: 1
- `slash_command`: 1

This means raw session counts alone overstate how often work starts from a clean blank prompt. In reality, this workflow is heavily resume-oriented.

### Dominant request themes across user messages

- `spec_work`: 22
- `ui_ux`: 17
- `vr_xr`: 13
- `docs_copy`: 10
- `git_release`: 10
- `review_audit`: 9
- `build_run`: 8
- `logs_debug`: 6
- `search_lookup`: 4

### Real slash-command usage observed

- `/catalog`: 8
- `/build`: 7
- `/spec`: 4
- `/spec-dev`: 1

### Most referenced spec tickets in prompt history

- `S0241`: 56 references
- `S0125`: 25 references
- `S0260`: 23 references
- then a long tail of much smaller counts

### What this says about the owner workflow

1. This is a spec-first workflow.
   - GPT should expect `Sxxxx` continuity, not isolated code snippets.

2. This is a resume-heavy workflow.
   - Many sessions restart from `Continue:` prompts, terminal notifications, or active ticket context.
   - The right optimization target is continuity reconstruction, not just broad search speed.

3. This is a direct-command workflow.
   - The owner gives orders such as:
     - prepare a specific spec
     - continue a specific ticket
     - archive a spec
     - inspect a log
     - write an artifact into `dev/`
   - GPT should default to execution, not open-ended planning, when the artifact is clear.

4. This workflow mixes Russian intent with English technical tokens.
   - Russian natural language for task control
   - English file paths, commands, tickets, and technical vocabulary
   - Tooling should preserve that bilingual shape rather than fight it.

5. This workflow cares about durable repo-local artifacts.
   - The owner repeatedly asks for outputs to land in `dev/`, `PLAN/`, or other repo files, not only in chat.

6. This workflow values non-regression and continuity of existing behavior.
   - One visible prompt explicitly stresses that existing settings must not disappear or be broken while parallel work proceeds.

### Recommendations derived from request history

1. Add a `/resume` prompt or `scripts/resume-context.ps1` that prints:
   - current branch
   - relevant `Sxxxx` status
   - last failing build or test command
   - latest log exceptions
   - nearby modified files
   - next expected action

2. Filter terminal notifications out of request-history analytics.
   - They are useful operationally.
   - They are bad signals for intent classification.

3. Log request intent explicitly.
   - Add or repair a request logger that stores:
     - raw request
     - inferred class: build, log, spec, audit, UI, docs, git, research, implementation
     - module and flavor
     - ticket id if present
     - final validation command

4. Write resumable state snapshots for substantial tasks.
   - For work expected to last more than a few minutes, write:
     - objective
     - files read
     - assumptions
     - blockers
     - next command
   - Store under `temp/sessions/`.

5. Bias first-minute routing toward the dominant real workflow.
   - Because spec, UI, VR, build, and docs work dominate history, GPT should preload the correct path instead of rediscovering it every time.

## Direct GPT Recommendations

1. Treat continuity as the main product feature.
   - The best GPT improvement in this repo is not more generic intelligence.
   - It is near-zero-cost resumption of active spec and implementation tracks.

2. Build one good startup packet.
   - `agent_bootstrap.ps1` should become the standard first command for substantial tasks.

3. Build one good closure packet.
   - `post-change.ps1` plus a task-aware validator should become the standard last command after edits.

4. Make dirty-tree state visible before every edit.
   - With 125 modified files, this is mandatory context, not a nice-to-have.

5. Fix documentation drift as an engineering-speed problem.
   - If GPT cannot trust docs, every answer slows down.

6. Keep the 5-step process, but add an analysis fast lane.
   - Audit-only, meta, and research-artifact tasks should not pay the full ceremony of implementation workflows when no code will be changed.

7. Keep parallel read-only work aggressive.
   - This machine can handle it.
   - Use parallel tool calls for reads and targeted inspection.
   - Reserve PowerShell processes for repo scripts that add semantic value.

## Preferred Implementation Order

1. Patch `.vscode/settings.json` exclusions for `node_modules`, `.venv`, `temp`, `DOWNLOADS`, and `logs`.
2. Improve `scripts/post-change.ps1` to use `-NoProfile`, `scripts/catalog_sync.ps1`, and explicit exit-code reporting.
3. Add `scripts/agent_bootstrap.ps1`.
4. Add `scripts/resume-context.ps1` and a `temp/sessions/*_gpt_state.md` convention.
5. Add request logging and request-digest reporting.
6. Add docs-vs-Gradle truth-drift checks.
7. Expand repo-knowledge MCP coverage.
8. Expand targeted build and test wrappers.
9. Split the highest-cost large Kotlin hotspots.

## Priority Plan

1. Fast win: reduce workspace noise.
2. Fast win: unify post-change rituals.
3. Fast win: make startup and resume context automatic.
4. Fast win: log request intent and filter non-intent transcript artifacts.
5. Medium: fix truth drift between docs and Gradle.
6. Medium: add repo-knowledge search over rules, specs, and catalogs.
7. Medium: restore the fastest safe targeted validation loops.
8. Larger: decompose large Kotlin hotspots.

## Evidence Collected

- Existing sibling audit reviewed: `dev/codex_audit.md`
- Key repo files reviewed:
  - `AGENTS.md`
  - `CLAUDE.md`
  - `.github/copilot-instructions.md`
  - `dev/PROJECT_OPERATIONS_INDEX.md`
  - `dev/AGENT_WORKFLOW.md`
  - `docs/DEV_OPS.md`
  - `dev/TECH_REQUIREMENTS.md`
  - `.vscode/settings.json`
  - `.vscode/mcp.json`
  - `build.gradle.kts`
  - `app_v2/build.gradle.kts`
  - `wear/build.gradle.kts`
- Transcript-backed history used from VS Code Copilot transcript storage under the current workspace profile.
- Current worktree dirtiness rechecked during this audit: 125 modified files.
