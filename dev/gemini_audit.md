# Gemini Audit: Optimizing Research and Development in FastMediaSorter v2

*Date:* 2026-05-20
*Author:* Antigravity (Gemini)

This document outlines my perspective on how to optimize research and development speed and quality within this specific Windows-based, heavily-regulated VScode project, leveraging my native capabilities.

## 1. Research Speed and Quality

### Native Tooling vs. PowerShell Overhead
The project correctly identifies that `pwsh` (PowerShell) cold starts are expensive on Windows (200-500ms). While the project provides rules for batching PowerShell calls and using wrappers (like `scripts/catalog_sync.ps1`), I can bypass this overhead entirely for many tasks.
- **Optimization**: I have native, shell-free tools (`list_dir`, `view_file`, `grep_search`). For reading files, exploring directories, and searching code, I will use these native tools exclusively. This guarantees instant execution and avoids shell serialization/deserialization.
- **Rule Alignment**: I will respect the rule to use `dev/CATALOG/scripts/query.ps1` for structural Kotlin searches (as it understands semantics better than regex). When invoking it, I will strictly use `pwsh -NoProfile -Command "& { ... }"` as dictated to minimize cold start time. 

### Parallel Execution
The project encourages "Parallel Sub-Agents".
- **Optimization**: I have native, concurrent tool-calling capabilities. I do not need to spawn separate sub-agents to read multiple files or run a query and a build simultaneously. I can invoke `view_file` on five different architectural documents in a single turn, instantly absorbing the context in parallel.

### Fast Research Routing
- **Optimization**: I will strictly adhere to the `PROJECT_OPERATIONS_INDEX.md` checklist. By first checking `ACTIVITY_CATALOG/`, then the specific domain doc, and finally the exact `.kt` files, I avoid irrelevant context. When encountering `Sxxxx` tickets, I will query `scripts/spec_catalog/select.ps1` rather than guessing paths.

## 2. Development Speed and Quality

### Native Planning Mode vs. 5-Step Agent Workflow
The project dictates a strict 5-Step Workflow (`dev/AGENT_WORKFLOW.md`): Task Definition -> Research -> Design -> Planning -> Implementation.
- **Optimization**: This perfectly aligns with my internal **Planning Mode**. 
    - I can map the *Design Phase* to my native `implementation_plan.md` artifact (incorporating the C4 model and UI/UX decision tables).
    - I can map the *Planning Phase* to my native `task.md` checklist. 
    - The user can review and approve my `implementation_plan.md` before I execute, natively enforcing the "Wait for human REVIEW" gate.
    - I must ensure that any spec documents (`PLAN/Sxxxx_*.md`) required by the project are also generated and registered in `PLAN/spec-catalog.jsonl` via `scripts/spec_catalog/insert.ps1`.

### Asynchronous Operations for Slow Tasks
Android builds and test executions are slow.
- **Optimization**: My `run_command` tool supports asynchronous execution via `WaitMsBeforeAsync`. Instead of blocking on `./gradlew.bat assembleStandardDebug` or a lengthy test suite, I can send it to the background, immediately update documentation (`CHANGELOG.md`, `FEATURES.md`), sync the catalog, or plan the next steps, and then use `command_status` to check the build results. This drastically reduces idle time.

### Precise Code Modification
- **Optimization**: Instead of relying on `sed`, shell scripts, or replacing entire files, I will use my native `multi_replace_file_content` and `replace_file_content` tools. They allow exact, multi-block patching of Kotlin and XML files. This prevents file corruption, avoids the 1500 LOC full-file rewrite problem, and integrates seamlessly with the project's rule against rewriting massive components.

### Enforcing Strict Validation Gates
- **Quality Assurance**: The project requires specific closure criteria per change type (e.g., target variant build for config, catalog sync + compile + test for Kotlin). I will bake these explicit validation steps into my `task.md` checklist. I will not consider an implementation step "done" until the asynchronous build command returns an exit code `0`. I will log the `expected` vs `actual` results directly into the `logs/dev_progress.log` step entries as mandated.

## Conclusion & Suggestions for the Project Tooling

1. **Leverage Native Tools Where Possible**: While `CLAUDE.md` emphasizes shell efficiency, it can be explicitly noted that agents with native file system/grep APIs should prefer them over `pwsh` for read-only operations to achieve maximum speed.
2. **Expose Catalog Data as JSON/API**: `query.ps1` is great, but invoking a shell script for semantic queries still incurs process overhead. If the `dev/CATALOG/*.jsonl` schemas were standard and easily readable, I could natively parse them without needing PowerShell wrappers, making semantic lookups instant.
3. **Async Build Workflows**: The project workflow could explicitly acknowledge async builds. For example, logging a step as "Build Started", performing other doc updates, and then logging "Build Verified", rather than enforcing strict sequential blocking on slow Gradle tasks.

## 3. Analysis of Conversation History & Workflow Patterns

A review of the last 10 interactions reveals a strong pattern in the types of requests I receive and how my capabilities can be optimized for them:

### Core Patterns in Requests
1. **Pre-Implementation Auditing**: The vast majority of tasks (e.g., S0125, S0244, S0194, S0195, S0192, S0193) involve auditing, refining, and optimizing detailed Markdown specifications (`PLAN/Sxxxx_*.md`) *before* any code is written.
2. **Deep Architectural Focus**: Requests consistently center on complex architectural constraints, such as lazy dependency injection, thread safety during initialization, modularization, build variant isolation (`STANDART`, `VR`, `noLegal`), and migration tasks (KAPT to KSP).
3. **UX Blueprint Verification**: Tasks frequently involve validating UX redesigns against edge cases (logical errors, ambiguities, edge state behaviors).

### Optimization Strategies Based on History

- **Context Prefetching**: Since tasks are almost entirely specification-driven, my primary bottleneck is loading the full context of the project's strict rules. When asked to audit a spec (e.g., S0195), I should proactively use concurrent `view_file` calls to fetch not only the target spec but also its dependencies (e.g., S0194), related domain docs (`dev/NETWORK_SPECS.md`), and the flavor rules (`dev/FLAVOR_DEVELOPMENT_RULES.md`). This eliminates back-and-forth and ensures audits are immediately accurate.
- **Native Planning as "Spec Diffing"**: Because so much work involves "Spec Refining", I can use my native `implementation_plan.md` artifact as a "diff/review proposal" layer. Instead of directly overwriting a complex `Sxxxx` markdown file, I can present the proposed refinements in `implementation_plan.md` for user approval. Once approved, I can safely apply the multi-block changes to the actual `Sxxxx` file using `multi_replace_file_content`.
- **Architectural Guardrails**: The history shows a zero-tolerance policy for variant leakage and initialization race conditions. I must automatically cross-reference any proposed architecture against `dev/FLAVOR_DEVELOPMENT_RULES.md` (to ensure `src/main` remains free of flavor guards) and the `CLAUDE.md` isolation rules before outputting an audit result.

## 4. Synergy with Codex Audit Findings

I have reviewed the concurrent `dev/codex_audit.md` and integrated its findings with my native capabilities. The combination of Codex's environment insights and my native tools yields a highly optimized workflow:

1. **Tooling Synergy and Bootstrapping**: 
   - Codex recommends creating `scripts/agent_bootstrap.ps1` and fixing `scripts/post-change.ps1` to reduce PowerShell overhead. While these are excellent fixes for CLI-bound agents, I can bypass the need for a bootstrap shell script entirely by natively pre-fetching `dev/PROJECT_OPERATIONS_INDEX.md` and `dev/CATALOG/*.md` concurrently. However, when I *must* run `post-change.ps1`, fixing it to use `-NoProfile` is universally beneficial for me as well.
2. **File Exclusions and Search Pollution**: 
   - Codex correctly notes that `mcp/**/node_modules` pollutes search results. I will explicitly respect `**/node_modules/**` exclusions when using my native `grep_search` tool (using the `Includes` parameter) to maintain instant, noiseless search results.
3. **Validation Facade**: 
   - Codex suggests building an `agent_validate.ps1` facade. Until that is built, I will natively replicate this logic in my `task.md` validation steps, asynchronously running the exact Gradle commands required for the specific change type and monitoring them via my `command_status` tool.
4. **Code Refactoring of Massive Files**: 
   - Codex flagged `VideoPlayerManager.kt` (>1500 LOC) as a compiler pressure point. While rewriting massive files is risky for standard agents, I can use my `multi_replace_file_content` tool to safely and precisely refactor this file block-by-block without risking the file corruption that usually occurs during full-file rewrites.
