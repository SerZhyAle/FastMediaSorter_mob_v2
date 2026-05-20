# Development Audit: Consolidated Research and Development Speed/Quality

Generated: 2026-05-20
Observed branch: `DEBUG-v004`
Sources merged: `dev/claude_audit.md`, `dev/codex_audit.md`, `dev/gemini_audit.md`, `dev/gpt_audit.md`
Purpose: practical synthesis of the strongest repeated evidence, the most useful unique findings, and the smallest action set with the highest daily payoff.

This file is intentionally not a union of all four audits. It keeps what is most actionable and discards repeated prose.

## Executive Summary

The workstation is already strong enough. CPU, RAM, Android tooling, Gradle wrappers, catalogs, scripts, and prompt routing are not the bottleneck. The repeated losses come from workflow friction: noisy search and watcher scope, expensive startup and resume reconstruction, fragmented post-change closure, documentation drifting from Gradle truth, narrow knowledge search surfaces, and broad concurrent worktree activity.

The clearest meta-finding is that the repo does not need more audit prose. The same fixes were identified repeatedly by four agents. The next gain comes from landing the small automation patches that improve the first minute, the last minute, and the resume path of every task. A fifth audit would be malpractice.

## Baseline Numbers Worth Remembering

- Workstation: i5-13600K, 20 logical processors, ~64 GB RAM, Win 11 Pro, PowerShell 7.6.1, Java 21.0.10, Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10.
- Repo scale: ~9,251 files total · 1,352 Kotlin · 571 XML · 456 Markdown · 108 PowerShell scripts.
- Worktree dirtiness on `DEBUG-v004`: 30..125 modified files across the four audit snapshots. Treat as **steady state**, not emergency.
- File-enumeration cost: `rg --files` returns ~9,251; excluding `node_modules`/`build`/`.gradle` returns ~2,633. About 3.5x noise reduction is free.
- PowerShell cold start: `pwsh -NoProfile` ~200..400 ms; `pwsh` with profile ~440 ms.
- Catalog vs `rg` for exact-class lookup: catalog query ~423 ms, `rg` exact-name ~44 ms. Order of magnitude.
- Spec catalog: 267 specs total. Velocity ~30/week. `BlockNeedUserTest → Verified` typically same-day.
- FUNCTIONALITY.log: ~150 entries / 7 days, ~20 user-visible changes / day.

## What Is Already Strong

- `dev/PROJECT_OPERATIONS_INDEX.md` is the correct routing entrypoint.
- `dev/CATALOG/` and `dev/ACTIVITY_CATALOG/` provide useful structural knowledge.
- Prompt routing is explicit: `/catalog`, `/build`, `/log-reader`, `/spec-*`, `/ui-clarify`, `/research`, `/doc-update`, `/quick`.
- `a.ps1` and the build/script wrappers reduce command entropy.
- `scripts/catalog_sync.ps1` already encodes the correct one-process catalog ritual.
- The repo has a strong validation culture and explicit post-change rules.
- Agent-native read/search tools eliminate many shell calls for read-only work.
- Persistent agent memory at `.claude/agent-memory/android-rd-specialist/` is a real compound advantage and should be the model for other agents, not a one-off.

## High-Confidence Shared Findings

### 1. Workspace noise is the cheapest high-payoff fix

Broad enumeration is polluted by local dependency and artifact trees. Offenders: `node_modules`, `temp`, `.kotlin`, `.venv`, `DOWNLOADS`, `logs`. The measured 3.5x reduction will move over time, but the direction is fixed.

Best action: tighten `.vscode/settings.json` `files.exclude`, `search.exclude`, `files.watcherExclude`; mirror the same exclusions in default `rg` patterns.

### 2. Startup context and resume context should be automatic

Every audit independently landed on the same missing primitive: one short startup packet with branch, dirty-tree summary, active `Sxxxx`, module/flavor hints, mandatory prompt route, recommended first files, and doc-vs-Gradle warnings.

Best action: add `scripts/agent_bootstrap.ps1`; add `scripts/resume-context.ps1` or a `temp/sessions/*_state.md` convention; make continuity reconstruction a first-class path.

### 3. Post-change closure is fragmented and over-engineered

The ingredients exist (`scripts/post-change.ps1`, `scripts/catalog_sync.ps1`, dev log, string checks, spec rituals). The problem is orchestration: the agent reconstructs the safe closure path every conversation. CLAUDE.md §"Post-Change Steps" prescribes 7 numbered fail-closed steps; for a typical Kotlin refactor steps 2/3/4 are skips, but the agent reads every step to decide.

Concrete defects in `scripts/post-change.ps1`: child `pwsh` calls without `-NoProfile`, bypasses `scripts/catalog_sync.ps1`, no explicit exit codes, no change-type routing.

Best action: patch `scripts/post-change.ps1` and widen it to `-ChangeType Doc|Script|Config|Kotlin|Xml|Mixed` so it internally picks the minimal safe validation path. CLAUDE.md §"Post-Change Steps" then collapses to "run `scripts/post-change.ps1 -ChangeType X`". Add `scripts/agent_validate.ps1` only if structure is still insufficient.

### 4. Documentation truth drifts from implementation truth

`docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `CLAUDE.md` carry stale AGP / Kotlin / KSP / Hilt / Room / compileSdk values relative to Gradle. Agents must reopen Gradle files instead of trusting docs.

Best action: add a docs-vs-Gradle drift checker; treat drift as an engineering-speed defect, not housekeeping.

### 5. Repo knowledge search is too narrow

Searchable knowledge should not stop at `docs/`. Agents need fast access to `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`, `PLAN/spec-catalog.jsonl`, `dev/CATALOG/*`, `dev/ACTIVITY_CATALOG/*`.

Best action: broaden the current `docs-search` MCP scope, or add a read-only `repo-knowledge` MCP.

### 6. Dirty-tree awareness must be first-class

Broad in-flight changes are normal in this repo, not a blocker. They are a real editing risk for shared infra (`CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`) unless surfaced before work starts.

Best action: dirty-tree guard that classifies the target area as `clean`, `same area`, `same file`, or `high-risk overlap`. One-line `git status --short | wc -l` at session start; full enumeration only when planned edits land in modified shared infra.

### 7. Lookup strategy: semantic vs exact-match

Catalog-first is overstated as a universal rule. Measured: catalog ~423 ms vs `rg` exact-name ~44 ms. Catalog wins on semantic questions, `rg` wins on exact-match.

- Catalog/semantic sources: injected types, side effects, role/status, decomposition candidates, cross-file ownership.
- Targeted `rg` or native reads: exact class/file/token/resource-key/log-tag, exact string lookup.

Best action: update the research rule from "catalog first always" to "catalog first for semantic lookup"; explicitly bless direct `dev/CATALOG/<module>.jsonl` reads for narrow lookups when no semantic step is needed.

## High-Value Unique Findings Worth Preserving

### A. Continuity beats blank-slate discovery

Transcript-backed analysis: 21 transcript files, 17 sessions with explicit prompts, 81 user messages (45 Cyrillic, 36 Latin-only). Session-entry prompt classification: `terminal_notification` 5, `continue_prompt` 4, `natural_language_ru` 3, `ticket_directive` 3, `natural_language_other` 1, `slash_command` 1. Real slash-command usage: `/catalog` 8, `/build` 7, `/spec` 4, `/spec-dev` 1. Most-referenced spec tickets: `S0241` 56, `S0125` 25, `S0260` 23.

The workflow is spec-first and resume-heavy, not blank-slate. The right optimisation target is continuity reconstruction: request logging, state snapshots for substantial tasks, automatic `Sxxxx` resolution, fast recovery after interruption. Filter `terminal_notification` and `continue_prompt` out of intent classification - they overstate fresh-start work.

### B. Settings/UI churn costs more than shell startup

`S0125` (revised settings host) ran ten `CHANGE → FIX` cycles in 36 hours before being archived. That dwarfs any 200..500 ms PowerShell cold start. Code scaffolding that anticipates "the final shape" tends to be wrong on volatile settings surfaces. Defensive memory rule already exists (`feedback_no_scaffolding_as_done.md`); the offensive rule is: apply `/ui-clarify` aggressively on settings UI requests, even when the prompt reads like a directive.

### C. Persistent agent memory compounds; expand it

`.claude/agent-memory/android-rd-specialist/` has 23 files, each earned from a specific past failure or confirmed pattern. Over three months it is the only artefact that improves per-session for the agent specifically. Most current memory is process (PowerShell, tags, sub-agents); the operator's domain (SMB / SFTP / Credential Manager / VR session / Room migrations) is under-represented. Write domain memory **in the moment**, not as a batch.

Other agent profiles (`android-kotlin-developer`, `android-solution-researcher`, `friendly-android-doc-writer`, `claude-code-guide`) have no memory directory. Seed `.claude/agent-memory/<name>/MEMORY.md` with starter feedback files when they next handle non-trivial work.

### D. Native read/search tools should be preferred for read-only work

When the harness exposes direct reads, parallel reads, or targeted search natively, that is cheaper and safer than spawning PowerShell. Reserve `pwsh -NoProfile -File ...` for repo scripts that add semantic or validation value (catalog sync, post-change, builders, spec-catalog mutators).

One caveat: claude's audit initially proposed a `scripts/pwsh` git-bash shim, then retracted it after discovering its harness already exposed a native `PowerShell` tool. Verify your harness before recommending PATH workarounds.

### E. Sub-agent spawning is the wrong default

Every sub-agent burns ~2,000 input tokens re-reading `CLAUDE.md` before doing the actual work. For lookup-class questions (where is class X, which file holds feature Y) a direct `Read` of `dev/CATALOG/<module>.jsonl` is faster, cheaper, and keeps the result in the main thread.

Heuristic: spawn sub-agent only when (a) genuinely parallel to main thread, (b) >=10 tool calls of work, (c) the sub-agent's output is a digest, not raw data the caller must re-process. For class lookup / single-file read: inline. For build validation while writing code: sub-agent (background) is correct.

### F. Async builds are under-used

`./gradlew.bat assembleStandardDebug` can run in background while the agent updates `CHANGELOG.md`, syncs catalog, plans next steps. Memory rule: never block synchronously on a build that exceeds ~20 s unless the next step strictly depends on its result.

### G. Two small correctness fixes should not be lost

- `CLAUDE.md` §"Post-Change Steps" step 5 still tells agents to commit `dev/CATALOG/<module>.jsonl` + `<module>.md`, but both files are `.gitignored`. Replace with "run `scripts/catalog_sync.ps1 -Module <m>`; the outputs are local indexes - regenerate, do not commit."
- Build-failure investigation must not rely on `tail -N`; the `FAILURE` block sits in the middle of the log. Add a small `scripts/builders/get-last-build-failure.ps1` that scans the most recent `temp/build-debug-*.log` and prints the failure section.

### H. Large Kotlin files are a build-speed cost, not a style debt

Observed at or above ~1000 LOC: `PlayerActivity.kt`, `PdfViewerManager.kt`, `TextViewerManager.kt`, `ImageLoadingManager.kt`, `CommandPanelController.kt`, `PlayerManagerInitializer.kt`, `GoogleDriveRestClient.kt`, `MediaFileAdapter.kt`, `SmbConnectionManager.kt`, `DropboxClient.kt`. `gradle.properties` explicitly calls out `VideoPlayerManager.kt` as a compiler-pressure hotspot. Treat decomposition as speed work; start with the largest. `testStandardDebugUnitTest` carries ~26 pre-existing broken tests - triage or quarantine into a tagged suite, otherwise every "did I break tests" check is contaminated.

### I. The version-format commit subject is unreadable

Commits land as `2605192322` (Y.YM.MDDH.Hmm) with no description. `git log --oneline -50` becomes useless for non-`feat`/`fix`/`release` commits. `scripts/add_to_dev_log.ps1` already takes a description; surfacing the same string into the commit subject is a one-line change in whatever commit wrapper is in use. Daily-readable history is worth it.

### J. More audits have low value until the current action set lands

The repeated recommendations are clear. The next useful artefact is a tracked follow-up list with status, owner, and commit reference (`dev/AUDIT_FOLLOWUPS.md`), not a fifth audit. New audits should edit the follow-up list, not restate the same recommendations.

## Recommended Implementation Order

### Fast wins (one focused session)

1. Patch `.vscode/settings.json` exclusions (`node_modules`, `.venv`, `temp`, `DOWNLOADS`, `logs`, `.kotlin`) and document matching `rg` exclusions.
2. Fix `scripts/post-change.ps1`: `-NoProfile` on child calls, route through `scripts/catalog_sync.ps1`, emit exact exit codes, accept `-ChangeType`.
3. Add `scripts/agent_bootstrap.ps1` (branch, dirty, ticket, route, doc-vs-Gradle warnings, recent UI churn).
4. Add `scripts/resume-context.ps1` and `temp/sessions/<ts>_<agent>_state.md` convention.
5. Fix CLAUDE.md §"Post-Change Steps" step 5 (catalog files are gitignored).
6. Refine the lookup rule in `CLAUDE.md` and `dev/CATALOG/README.md`: "catalog first for semantic; `rg` first for exact-match".

### Next layer

7. Add a docs-vs-Gradle drift checker.
8. Expand searchable repo knowledge (broaden `docs-search` MCP or add `repo-knowledge`).
9. Add `scripts/builders/get-last-build-failure.ps1` to eliminate the `tail -N` truncation trap.
10. Repair / replace `scripts/log-ai-request.ps1`; add `scripts/agent_request_digest.ps1` so future audits use evidence, not memory.
11. Add a dirty-tree guard that classifies target areas as `clean / same area / same file / high-risk overlap`.
12. Seed `.claude/agent-memory/<name>/MEMORY.md` for the other agent profiles.

### Own spec tickets

13. Split `VideoPlayerManager.kt` and other >=1000 LOC hotspots.
14. Triage `testStandardDebugUnitTest` (~26 pre-existing broken tests).
15. Investigate configuration cache for standard/lite/photos while noLegal keeps `--no-configuration-cache`.

## Anti-Patterns To Avoid

- Do not write a fifth audit. Edit a follow-ups list instead.
- Do not rewrite `CLAUDE.md` to be shorter. It is long because it must be; shrink the post-change part instead.
- Do not add new MCP servers before utilising the current four.
- Do not bulk-fix the ~169 `BuildConfig.IS_*` flavor-guard violations in `src/main/`; `CLAUDE.md` §15 explicitly calls this out as incremental refactor.
- Do not propose a `pwsh` PATH shim without verifying your harness lacks a native PowerShell tool.
- Do not block synchronously on builds >20 s unless the next step strictly depends on the result.
- Do not force semantic tooling on trivial exact-match searches.
- Do not silently fix items spotted during other work when the worktree is busy; surface them and let the operator decide.
- Do not call scaffolding "done" - a milestone is not a deliverable.

## Practical Operating Rules

- Start substantial tasks with branch, dirty-tree, ticket, route, and module/flavor context.
- Prefer the narrowest prompt route early.
- Use semantic tooling for semantic questions; use exact search for exact questions.
- Surface dirty-tree overlap before editing shared infra.
- Close changes through one repeatable ritual, not ad-hoc command chains.
- Preserve resume state for long tasks.
- Treat documentation drift as a real engineering-speed defect.
- Apply `/ui-clarify` aggressively on settings and other volatile UI surfaces.
- Prefer native read/search tools for read-only work when available.
- Write domain memory in the moment, not as a batch.
- Timestamp every chat message (`[HH:MM:SS]`).
- Russian in chat, English in code/docs/commits. `..` not `...`. `ё`/`Ё` always.

## What To Do Next

If only one small batch of work is approved from this audit, it should be:

1. Patch workspace exclusions.
2. Fix `scripts/post-change.ps1` (including `-ChangeType` dispatch).
3. Add `scripts/agent_bootstrap.ps1`.
4. Add resume-context plus dirty-tree guard.
5. Fix the two small correctness items in §G (CLAUDE.md catalog-commit line, build-failure log wrapper).

That batch addresses the most repeated bottlenecks across all four audits, improves both research speed and implementation reliability, and changes no product behaviour. The next planned artefact after the batch lands should be `dev/AUDIT_FOLLOWUPS.md`, not a new audit.
