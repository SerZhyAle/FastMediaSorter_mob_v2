# Codex Audit: Research and Development Speed

Generated: 2026-05-20
Branch observed: `DEBUG-v004`
Scope: local computer, OS/tooling, VS Code workspace configuration, repository rules, scripts, catalogues, and MCP setup from the perspective of Codex agent speed and output quality.

## Executive Summary

The workstation is strong enough for aggressive local research and Android validation: 13th Gen Intel Core i5-13600K, 20 logical processors, about 64 GB RAM, `rg` 15.1.0, Git 2.53, ADB 37, and Android Studio bundled JBR configured for Gradle. The biggest speed losses are not hardware limits. They are routing and tooling friction:

- Repo-local `scripts/mcp/**/node_modules` expands broad file enumeration from about 2,633 relevant files to about 9,251 files.
- Several rules correctly demand catalog-first research, but not every helper fully encodes that path.
- `scripts/post-change.ps1` exists as a good combined ritual runner, but it invokes child `pwsh` processes without `-NoProfile` and bypasses the existing `scripts/catalog_sync.ps1` wrapper.
- The VS Code MCP setup is useful, but it is narrower than the repo workflow: docs search is limited to `docs/`, and Gradle MCP exposes only a small command allowlist.
- Some project truth sources disagree on stack versions. That slows research because the agent must verify implementation files instead of trusting docs.
- The current worktree is very dirty. That is sometimes normal in this repo, but it raises agent risk unless every task starts with a scoped dirty-tree snapshot.
- Conversation history available to this session points to an owner workflow that values autonomous execution, durable repo-local artifacts, strict rule compliance, and resumability after interruptions. The tooling should make those paths first-class.

## Observed Environment

- OS report: Windows 10 Pro, 64-bit, HAL `10.0.26100.1`; ADB reports Windows `10.0.26200`. Treat this as a Windows 10/11-era host and avoid OS-label assumptions.
- CPU/RAM: 13th Gen Intel Core i5-13600K, 20 logical processors, about 64 GB RAM.
- Java on PATH: Oracle Java 21.0.10. Gradle is pinned to Android Studio JBR via `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr`.
- Gradle wrapper: 9.4.1.
- Root Gradle plugins observed: AGP 9.2.1, Kotlin 2.2.10, KSP 2.3.8, Hilt 2.59.
- `gradle.properties`: Gradle daemon on, parallel on, worker cap 8, Kotlin incremental on, kapt incremental on, configuration cache disabled due Chaquopy 17.x noLegal constraints.
- PowerShell cold start measured in this workspace: `pwsh -NoProfile` about 402 ms, `pwsh` with profile about 438 ms. The measured delta is smaller than the rule estimate, but repeated calls still add up quickly.
- VS Code settings already exclude `build`, `.gradle`, `.idea`, `V1`, `v2_6`, and `spec_v2`, but not `scripts/mcp/**/node_modules`.
- MCP configured in `.vscode/mcp.json`: `docs-search`, `filesystem_rw`, `filesystem_ro`, `gradle_safe`.

## Research Speed and Quality

### What Is Already Good

- `dev/PROJECT_OPERATIONS_INDEX.md` is the right first stop. It gives module routing, feature-to-path hints, build commands, and read-only zones.
- `dev/CATALOG/app_v2.md` and `dev/CATALOG/app_v2.jsonl` are large and valuable. They should remain the first stop for Kotlin class lookup.
- `scripts/utils/build-research-dossier.ps1` is a strong entrypoint. It creates a topic-scoped dossier in `temp/` and points at docs, catalog matches, activity entries, specs, and implementation files.
- Prompt routing is explicit. The agent can choose `/catalog`, `/build`, `/log-reader`, `/spec-*`, `/ui-clarify`, and `/research` without guessing.
- `dev/ACTIVITY_CATALOG/` is small and fast enough to use early for Activity and navigation questions.

### Friction

- Broad file listing is noisy because local MCP dependencies live under the repo. `rg --files` returned about 9,251 files; excluding `node_modules`, `build`, and `.gradle` returned about 2,633 files.
- The dossier script does not fully replace the mental routing stack yet. It helps, but agents still need to manually remember `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, prompt skills, and branch state.
- `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `CLAUDE.md`, and Gradle files are not fully synchronized. Example: Kotlin and Room versions differ across docs and implementation. This directly reduces answer quality because the agent must re-check Gradle files for current truth.
- `.github/copilot-instructions.md` says browser work must use Playwright MCP, while the active Codex harness may expose different web tooling. This creates routing ambiguity for non-local research.
- The current worktree has many modified Kotlin, XML, Gradle, doc, and script files. For research, that means "current code" may include uncommitted experimental state. For implementation, it increases conflict risk.

### Recommendations

1. Add `**/node_modules/**` under both `files.exclude`, `search.exclude`, and `files.watcherExclude` in `.vscode/settings.json`.

2. Add repo-level ignore guidance for agent searches:
   - Preferred broad search: `rg --hidden -g '!**/node_modules/**' -g '!**/build/**' -g '!**/.gradle/**'`.
   - Preferred file list: `rg --files -g '!**/node_modules/**' -g '!**/build/**' -g '!**/.gradle/**'`.

3. Extend `scripts/utils/build-research-dossier.ps1` so each dossier includes:
   - current branch,
   - dirty-tree count,
   - relevant mandatory prompt route,
   - relevant AGENTS/CLAUDE rule excerpts,
   - exact "next command" suggestions for catalog, spec, log, build, and docs search.

4. Expand `docs-search` MCP beyond `docs/` or add a separate `repo-knowledge` MCP:
   - include `dev/PROJECT_OPERATIONS_INDEX.md`,
   - include `dev/AGENT_WORKFLOW.md`,
   - include `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`,
   - include `PLAN/spec-catalog.jsonl`,
   - include `dev/CATALOG/*.jsonl` and `dev/ACTIVITY_CATALOG/*.jsonl`.

5. Add a `scripts/agent_bootstrap.ps1` helper that prints one compact startup packet:
   - branch,
   - dirty status summary,
   - active module/flavor hints,
   - key repo rules hash or timestamp,
   - recommended first docs,
   - warning if truth sources disagree.

6. Create a small "truth drift" check for docs versus Gradle:
   - Kotlin version,
   - AGP version,
   - Gradle wrapper version,
   - Room version,
   - Glide version,
   - compileSdk/targetSdk/minSdk,
   - Room schema version.

Expected effect: fewer global greps, fewer wrong-version assumptions, fewer manual rule lookups, and faster first 60 seconds of any task.

## Development Speed and Quality

### What Is Already Good

- Gradle is tuned for this machine: daemon enabled, parallel enabled, worker cap set to 8, Kotlin incremental enabled, dedicated temp dirs under `temp/gradle-tmp`.
- `a.ps1` is a useful command facade and already handles release worktree delegation and Chaquopy local toggling.
- Build scripts exist for standard, lite, photos, legacy, noLegal, wear, release, device install, and Gradle cache recovery.
- `scripts/catalog_sync.ps1` exists and matches the repo rule to scan and render catalog in one process.
- `scripts/utils/recover-kapt-stall.ps1` documents a targeted recovery path for kapt hangs.
- `scripts/mcp/gradle-mcp/server.js` is allowlist-based and uses `-NoProfile` for PowerShell script commands.

### Friction

- `scripts/post-change.ps1` is close to the right abstraction, but it invokes `pwsh -File` without `-NoProfile`. It also runs `scan.ps1` and `render.ps1` separately instead of using `scripts/catalog_sync.ps1`.
- The Gradle MCP allowlist does not include common targeted validation commands such as `:app_v2:compileStandardDebugKotlin`, `:app_v2:testStandardDebugUnitTest --tests <pattern>`, `:wear:assembleDebug`, noLegal debug, or quiet debug build.
- Configuration cache is disabled for all builds because of noLegal/Chaquopy constraints. That may be necessary globally today, but standard/lite/photos development pays the cost too.
- Some source files are above the local 1500 LOC guidance. `gradle.properties` explicitly calls out `VideoPlayerManager.kt` as a compiler pressure point. This is a real development-speed issue, not just style debt.
- The post-change ritual is fragmented: dev log, catalog sync, string audit, feature/functionality log, and validation are documented, but not represented as one task-aware command that can choose the minimal safe closure.
- The current dirty tree is broad. Agents need a standard way to protect user changes before editing nearby files.

### Recommendations

1. Fix `scripts/post-change.ps1`:
   - use `pwsh -NoProfile -File` for child invocations,
   - call `scripts/catalog_sync.ps1 -Module <module>` instead of separate scan/render,
   - pass `-Module` into string checks where supported,
   - print exact exit codes for each step.

2. Add `scripts/agent_validate.ps1` as a task-aware validation facade:
   - `-ChangeType Doc|Script|Config|Kotlin|Xml|Mixed`,
   - `-Module app_v2|wear`,
   - `-Flavor standard|lite|photos|legacy|noLegal|vr|foss`,
   - `-Tests <pattern>`,
   - output expected vs actual in the repo-required format.

3. Expand Gradle MCP allowlist:
   - `gradle_compile_standard_debug`: `gradlew.bat :app_v2:compileStandardDebugKotlin`,
   - `gradle_compile_nolegal_debug`: `gradlew.bat :app_v2:compileNoLegalDebugKotlin`,
   - `gradle_wear_debug`: `gradlew.bat :wear:assembleDebug`,
   - `gradle_test_single`: fixed safe wrapper script accepting a test class pattern,
   - `script_build_debug_quiet`: `a.ps1 dq`,
   - `script_catalog_sync_app`: `scripts/catalog_sync.ps1 -Module app_v2`,
   - `script_catalog_sync_wear`: `scripts/catalog_sync.ps1 -Module wear`.

4. Investigate whether standard/lite/photos builds can re-enable configuration cache while noLegal keeps `--no-configuration-cache`.
   - If Gradle cannot do this safely in one project, document that conclusion.
   - If possible, route noLegal through dedicated scripts and let normal app builds regain configuration-cache speed.

5. Prioritize splitting very large Kotlin files that stress the compiler.
   - First candidate from existing comments: `VideoPlayerManager.kt`.
   - Use the established `helpers/*Manager.kt` pattern.
   - Treat this as build-speed and reliability work, not cosmetic refactoring.

6. Add a dirty-tree guard script for agents:
   - summarize modified files by area,
   - detect whether the intended edit touches already-modified files,
   - emit "safe to proceed", "read carefully", or "ask user" guidance.

7. Add a narrow "class lookup first" wrapper:
   - `scripts/find-class.ps1 -Name Foo -Module app_v2`,
   - internally calls `dev/CATALOG/scripts/query.ps1`,
   - falls back to `rg` only when the catalog has no match,
   - reports whether catalog may be stale.

Expected effect: fewer repeated ritual commands, fewer missed validations, faster targeted compile/test loops, and lower risk when the worktree is busy.

## VS Code and MCP Suggestions

1. Update `.vscode/settings.json` exclusions:
   - `**/node_modules`: true,
   - `scripts/mcp/**/node_modules`: true,
   - `**/.kotlin`: true if Kotlin generated state does not need IDE search,
   - `DOWNLOADS`: true for search unless release artifact names are being inspected.

2. Keep `java.import.gradle.enabled=false` and `java.autobuild.enabled=false` for this repo unless using VS Code Java analysis intentionally. Android Studio/Gradle remains the source of truth.

3. Add `.vscode/tasks.json` for the commands agents and humans repeatedly need:
   - `agent bootstrap`,
   - `research dossier`,
   - `catalog sync app_v2`,
   - `compile standard debug`,
   - `test standard debug`,
   - `build debug quiet`,
   - `extract logs`.

4. Make MCP server startup cheaper:
   - ensure `node_modules` remains installed locally for MCP servers,
   - avoid `npx -y` for frequently used filesystem MCP if it downloads or resolves packages on startup,
   - prefer pinned local package installs where possible.

5. Consider adding a Codex-specific MCP config if the active Codex environment can consume the same servers as VS Code. The current `.vscode/mcp.json` helps VS Code, but the active agent toolset may not automatically expose those MCP tools.

## Rule and Documentation Quality

1. Consolidate version truth:
   - Gradle files should be source of truth for dependency versions.
   - Docs should either match Gradle or explicitly say "summary, verify in Gradle".
   - The current mismatch between docs and Gradle should be fixed because it slows every technical answer.

2. Reduce duplicate policy surfaces where possible:
   - `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, and prompt files repeat important rules.
   - Repetition is useful, but drift is expensive.
   - Add a small drift checker for the most critical duplicated rules: language, ellipsis, `ё`, branch model, PowerShell `-NoProfile`, catalog-first lookup, read-only zones, and post-change rituals.

3. Keep `dev/PROJECT_OPERATIONS_INDEX.md` short and current.
   - It is the best first-read file.
   - When it grows, split detail elsewhere and keep this file as a routing map.

4. Make every prompt file state its required scripts in one compact command block.
   - Agents are faster when the prompt has a directly runnable path.

## Conversation and Request-History Analysis

### Source Limits

- No persistent Codex conversation log was found in the workspace.
- `temp/ai_prompts.xml`, the file targeted by `scripts/log-ai-request.ps1`, was absent during this audit.
- This section is based on the visible current conversation, local repo artifacts that record user/request patterns, `logs/dev_progress.log`, `PLAN/` activity, `dev/S0258_toggle_row_template_task_definition.md`, and the adjacent `dev/gemini_audit.md`.
- `dev/gemini_audit.md` includes another agent's claimed review of recent interactions. Treat it as a useful signal, not as a verified Codex transcript.

### Patterns Observed

1. The owner gives direct agent-level orders and expects execution, not a long negotiation.
   - Current example: "study my computer, OS, this vscode project" and write conclusions into `dev/codex_audit.md`.
   - Optimization impact: Codex should make reasonable assumptions, act, and document scope limits instead of asking broad clarifying questions.

2. The owner prefers durable artifacts inside the repo.
   - Current example: both the initial audit and this update were requested as edits to `dev/codex_audit.md`, not as chat-only analysis.
   - Optimization impact: important conclusions should land in `dev/`, `PLAN/`, or `temp/` depending on permanence and repo rules.

3. Requests often combine research, process improvement, and implementation-readiness.
   - The audit request was not only "what is slow", but "how to optimize your research and development speed and quality".
   - Optimization impact: research outputs should include concrete script/MCP/rule changes, expected effect, and priority, not only observations.

4. The owner actively uses interruption/resume as part of the workflow.
   - The first audit run was intentionally aborted and then reissued.
   - Optimization impact: Codex should keep enough intermediate state in `temp/` or the target artifact to resume cleanly after interruption without restarting from zero.

5. Local history is strongly spec-driven.
   - `logs/dev_progress.log` shows S0251 phase work with build validation and BlockNeedUserTest handoff.
   - `PLAN/` and `dev/gemini_audit.md` point to recurring S-ticket workflows, audits, tactical phases, and verification gates.
   - Optimization impact: the first research packet should always detect an `Sxxxx` token, resolve it through `scripts/spec_catalog/select.ps1`, and preload related tactical files.

6. The project history emphasizes high-risk Android surfaces.
   - Recent artifacts mention VR/noLegal, OpenXR, settings UI trigger rows, link extraction, cloud/session handling, player flows, and large-file decomposition.
   - Optimization impact: Codex should bias toward architecture and validation depth for these areas, especially flavor isolation, UI orientation parity, and device/log evidence.

7. The owner appears comfortable with strict local conventions and expects them to be followed automatically.
   - Russian chat, English docs/code/logs, `..` ellipsis, `ё`, catalog-first lookup, `Timber`, no root artifacts, and post-change rituals are repeated across rule files.
   - Optimization impact: these conventions should be converted into automated preflight checks where possible.

8. The owner values agent self-improvement.
   - The current request is meta-work: improve Codex itself as an operator in this repo.
   - Optimization impact: create feedback loops: request logging, task classification, validation timing, failed-assumption capture, and recurring bottleneck reports.

### Conversation-Derived Tooling Recommendations

1. Start using `scripts/log-ai-request.ps1` or replace it with a better request logger.
   - Minimum fields: timestamp, raw user request, inferred route, module/flavor, S-ticket, files touched, validation run, interruption/resume marker, outcome.
   - Store durable summaries in `dev/agent_request_patterns.md` and raw session data in `temp/`.

2. Add `scripts/agent_request_digest.ps1`.
   - Read request logs, `logs/dev_progress.log`, recent `PLAN/S*.md`, and `dev/CHANGELOG.md`.
   - Output the top recurring task types, most-touched modules, validation costs, and common blockers.
   - This would make future "analyze our history" requests evidence-based instead of transcript-limited.

3. Add an interruption-resume protocol.
   - On substantial tasks, write `temp/sessions/<timestamp>_codex_state.md` with current objective, files read, decisions made, and next command.
   - On resume, read the newest matching state file before doing fresh exploration.

4. Add a "request classifier" to `agent_bootstrap`.
   - Classify the incoming request as quick/doc/spec/log/build/git/catalog/research/implementation.
   - Print required prompt route and mandatory first files.
   - This directly supports the owner's direct-command style.

5. Keep a short Codex operating memory in `dev/codex_audit.md` or a sibling file.
   - Record stable owner preferences that affect speed and quality.
   - Do not store secrets, personal data, or raw long transcripts.
   - Update only when a pattern is repeated or explicitly requested.

### Operating Adjustments For Codex

- Be decisive when the requested artifact and scope are clear.
- State evidence limits in the artifact, not as a blocker.
- Prefer writing a useful first version, then refining it on request.
- Preserve resumability after interruption.
- Treat dirty-tree awareness as mandatory context because the owner often has broad concurrent work in progress.
- When history is requested, distinguish transcript evidence, repo artifact evidence, and inference.

## Direct Codex Recommendations

These are my highest-confidence suggestions after seeing the rules, tooling, current workspace state, and the way requests are being issued.

1. Treat agent speed as a product feature of the repo.
   - The repo already has many strong rules, but agents still pay a high startup tax to rediscover them.
   - Best next move: implement `scripts/agent_bootstrap.ps1` before adding more prose rules.
   - The bootstrap output should be short enough to read every session and strict enough to block obvious wrong routes.

2. Prefer one maintained agent path over many partially-overlapping rituals.
   - Today the rules mention `add_to_dev_log.ps1`, functionality log, catalog sync, string audit, spec catalog, validation ladder, and progress journal as separate concepts.
   - Best next move: build `scripts/agent_validate.ps1` or expand `scripts/post-change.ps1` into a single task-aware closure command.
   - This would reduce missed rituals more than another reminder in `CLAUDE.md`.

3. Make "dirty tree" visible before every edit.
   - The current workspace has many modified files. That is workable, but only if Codex sees the shape of those changes before touching adjacent code.
   - Best next move: add a lightweight dirty-tree classifier that groups changes by module and risk.
   - Output should say: `unrelated`, `same area`, `same file`, or `unknown risk`.

4. Turn request history into data.
   - The project has a request logger script, but no active request log was present.
   - Best next move: update or replace `scripts/log-ai-request.ps1` so it is actually used at the start and end of substantial agent tasks.
   - This would let future audits identify patterns from evidence instead of memory.

5. Create a local repo knowledge MCP before expanding general MCPs.
   - `docs-search` is useful, but the agent needs `dev/`, `PLAN/`, catalogues, and rules as much as `docs/`.
   - Best next move: add `repo-knowledge` MCP with read-only search/read tools over curated repo knowledge files.
   - Keep it read-only. Write operations should stay explicit through normal tools and scripts.

6. Fix documentation drift as a speed issue, not a paperwork issue.
   - Version mismatches force the agent to distrust docs and re-open Gradle files.
   - Best next move: add a drift check that compares Gradle, wrapper, Room schema, and docs.
   - Run it before release work and after dependency changes.

7. Keep the 5-step process, but add a fast lane for audit-only and meta-work.
   - This audit is not code implementation, yet the repo's process language can make even meta-work feel heavier than needed.
   - Best next move: define a small `analysis artifact` lane: branch check, source scan, artifact write, grep validation.
   - That keeps quality without forcing design/planning ceremony where no code will be written.

8. Make interruption a normal state.
   - The previous interrupted run proved that resumability matters here.
   - Best next move: for any task expected to exceed a few minutes, write `temp/sessions/<timestamp>_codex_state.md`.
   - The state file should record objective, files read, commands run, assumptions, pending edits, and next action.

9. Use the machine more aggressively for parallel read-only work.
   - The hardware can handle parallel local reads and targeted checks easily.
   - Best next move: continue batching independent reads through parallel tool calls and reserve PowerShell processes for repo scripts that add semantic value.

10. Do not optimize away human control at true decision gates.
    - The owner likes direct execution, but UI ambiguity, flavor policy, release branching, and destructive git operations still need explicit alignment.
    - Best next move: make those gates machine-detectable so Codex asks only when the decision is real.

### My Preferred Implementation Order

1. Patch `.vscode/settings.json` exclusions for `node_modules`, `.kotlin`, and generated artifact folders.
2. Patch `scripts/post-change.ps1` to use `-NoProfile`, `scripts/catalog_sync.ps1`, and explicit exit-code reporting.
3. Add `scripts/agent_bootstrap.ps1`.
4. Add request logging and a resumable `temp/sessions/*_codex_state.md` convention.
5. Add a docs-vs-Gradle drift checker.
6. Add `repo-knowledge` MCP over curated read-only project knowledge.
7. Expand Gradle MCP only after the local wrappers are stable.

## Priority Plan

1. Fast win: exclude `node_modules` from VS Code search/watch and document the same exclusion for `rg`.
2. Fast win: patch `scripts/post-change.ps1` to use `-NoProfile` and `scripts/catalog_sync.ps1`.
3. Fast win: add `scripts/agent_bootstrap.ps1` for branch/rules/env startup.
4. Fast win: add request logging and an interruption-resume state file for substantial Codex tasks.
5. Medium: expand `build-research-dossier.ps1` into the standard first-minute research packet.
6. Medium: expand Gradle MCP allowlist and add targeted compile/test wrappers.
7. Medium: add docs-vs-Gradle truth drift check.
8. Larger: split compiler-heavy Kotlin files, starting with `VideoPlayerManager.kt`.
9. Larger: investigate standard-build configuration cache recovery while keeping noLegal safe.

## Personal Operating Notes For Codex

- Start every task by reading the narrowest prompt route and `dev/PROJECT_OPERATIONS_INDEX.md`.
- Use catalog query before global Kotlin lookup.
- Use `rg` with generated/dependency exclusions by default.
- Treat dirty-tree state as first-class context.
- Prefer `a.ps1` and repo wrappers over raw Gradle when the prompt allows it.
- For Kotlin changes, close with catalog sync plus targeted compile/test.
- For UI changes, stop at the ambiguity gate before implementation.
- For docs or process changes, validate by content grep and keep the file in English.

## Evidence Collected

- Research dossier: `temp/research_dossier_codex-audit-speed-quality-environment-tooling_20260520_123525.md`.
- Branch: `DEBUG-v004`.
- File enumeration: `rg --files` about 9,251 files; excluding `node_modules`, `build`, and `.gradle` about 2,633 files.
- PowerShell startup: `pwsh -NoProfile` about 402 ms; `pwsh` about 438 ms in a simple one-shot measurement.
- Conversation/request-history sources: visible current Codex conversation; `temp/ai_prompts.xml` checked and missing; `logs/dev_progress.log`; `dev/gemini_audit.md`; `dev/S0258_toggle_row_template_task_definition.md`; targeted `rg` over `dev`, `PLAN`, `logs`, `temp`, `.github`, and `scripts`.
- Key files read: `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, `.github/prompts/research.prompt.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`, `docs/ARCHITECTURE.md`, `docs/DEV_OPS.md`, `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `.vscode/settings.json`, `.vscode/mcp.json`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `build.gradle.kts`, `app_v2/build.gradle.kts`, `a.ps1`, `scripts/post-change.ps1`, and MCP server files.
