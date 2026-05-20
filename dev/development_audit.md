# Development Audit: Research and Development Speed/Quality

Generated: 2026-05-20
Branch observed: `DEBUG-v004`
Sources merged: `dev/claude_audit.md`, `dev/codex_audit.md`, `dev/gemini_audit.md`, `dev/gpt_audit.md`.
Scope: workstation, OS, repo rules, scripts, catalogues, MCP, VS Code config, agent harnesses, transcript-backed request history.

This file is the consolidated, opinionated synthesis of four independent agent audits. Findings that appeared in only one audit are kept if they were measurement-grounded or operator-actionable. Findings duplicated across audits are kept once, with the strongest evidence attached.

## 0. Headline Findings (read these first)

1. **The audit format itself is failing.** Three of four audits (codex, gemini, claude) converged on the same handful of unfixed recommendations (`-NoProfile` in `scripts/post-change.ps1`, `node_modules` in `.vscode/settings.json` exclusions, `repo-knowledge` MCP, doc/Gradle drift). None landed between 2026-05-20 morning and the time this synthesis was written. A fifth audit would be malpractice - what this repo needs is **one tracked followups list**, not more recommendations.
2. **The catalog-first lookup rule is overstated.** Measured: catalog query about 423 ms; targeted `rg` for an exact class name about 44 ms. Catalog-first is correct for *semantic* queries (role / injection / side effects / decomposition candidates). For exact-class, exact-file, exact-token, exact-string lookups, `rg` is an order of magnitude faster and equally precise.
3. **CLAUDE.md §"Post-Change Steps" step 5 is self-contradictory.** It tells agents to commit `dev/CATALOG/<module>.jsonl` + `<module>.md`, but both files are `.gitignored`. Every fresh conversation pays the discovery cost.
4. **The biggest hidden cost is operator iteration thrash, not tool cold start.** S0125 (revised settings host) ran ten CHANGE/FIX cycles in 36 hours before being archived. That dwarfs any 200..500 ms PowerShell spawn. The right defence is `/ui-clarify` used **harder** on settings UI requests, not more script wrappers.
5. **Workspace noise is the cheapest fix with the highest payoff.** `rg --files` returns about 9,251 files; with `**/node_modules/**`, `**/build/**`, `**/.gradle/**` excluded it returns about 2,633. A two-line `.vscode/settings.json` patch removes ~3.5x of file-enumeration cost.
6. **The current worktree carries 125 modified files.** This is the operator's steady state on a `DEBUG-vNNN` branch, not an emergency. Dirty-tree awareness is still mandatory before edits to shared infra files (`CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`).
7. **Native agent tools beat shelling out for read-only work.** Agents with `PowerShell` tool, `Read`/`Glob`/`Grep`, or native file-system APIs should use them; reserve `pwsh -NoProfile -File ...` for repo scripts that add semantic value (catalog sync, post-change ritual, build wrappers).

## 1. Observed Environment

Hardware and OS are not the bottleneck. Verified during the audits, not assumed.

- OS: Windows 11 Pro, 64-bit. HAL `10.0.26100.1`. ADB reports `10.0.26200`. PowerShell reports `Microsoft Windows NT 10.0.26200.0`.
- CPU: 13th Gen Intel Core i5-13600K, 20 logical processors.
- RAM: about 63.7..64 GB.
- Java on PATH: Oracle Java 21.0.10. Gradle pinned to Android Studio JBR via `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr`.
- PowerShell: 7.6.1 (`pwsh.exe` at `C:\Program Files\PowerShell\7\pwsh.exe`).
- Gradle wrapper: 9.4.1. AGP 9.2.1. Kotlin 2.2.10. KSP 2.3.8. Hilt 2.59.
- Daemon on, parallel on, worker cap 8, Kotlin/kapt incremental on, configuration cache disabled (Chaquopy 17 / noLegal constraint - documented in `gradle.properties`).
- CLI tools: ripgrep 15.1.0, Git 2.53.0.windows.2, Node 24.14.0, Python 3.10.11, ADB 1.0.41.
- PowerShell cold-start (measured): `pwsh -NoProfile` ~200..400 ms; `pwsh` with profile ~440 ms. Smaller than the 500 ms rule estimate, but repeated invocations still dominate.

### Repository scale (measured)

- ~9,251 files total · 1,352 Kotlin · 571 XML · 456 Markdown · 108 PowerShell scripts.
- 125 modified files on the current `DEBUG-v004` branch.
- Spec catalog: 267 specs (S0001..S0267).
- FUNCTIONALITY.log: ~150 entries in the last 7 days.

### High-noise directories observed

- `temp/`: ~8,320 files, ~2.2 GB
- `DOWNLOADS/`: 18 files, ~2.1 GB
- `.git/`: ~9,060 files, ~914 MB
- `.gradle/`: ~706 files, ~511 MB
- `.venv/`: ~1,285 files, ~27 MB
- `scripts/mcp/**/node_modules`: present, pushes `rg --files` from ~2,633 to ~9,251.

## 2. What Is Already Working Well

- `dev/PROJECT_OPERATIONS_INDEX.md` - correct first-routing entrypoint (module routing, feature-to-path hints, read-only zones).
- `dev/CATALOG/<module>.{jsonl,md}` - semantically richer than raw grep for class ownership, DI, tests, side effects, layer awareness.
- `dev/ACTIVITY_CATALOG/` - small and fast enough to use early for Activity/navigation questions.
- Prompt routing is explicit: `/catalog`, `/build`, `/log-reader`, `/spec-*`, `/ui-clarify`, `/doc-update`, `/research`, `/quick`.
- `a.ps1` two-letter facade (21 aliases, `dq`, `ss`, etc.) saves typing.
- `scripts/catalog_sync.ps1` - correctly chains `scan.ps1` + `render.ps1` in one PowerShell process.
- `scripts/utils/build-research-dossier.ps1` - topic-scoped dossier under `temp/`.
- `scripts/utils/recover-kapt-stall.ps1` - documented kapt-hang recovery path.
- Spec catalog operator facade under `scripts/spec_catalog/` (`select.ps1`, `next-id.ps1`, `update.ps1`, `complete.ps1`, `archive.ps1`).
- `.vscode/mcp.json` exposes `docs-search`, `filesystem_rw`, `filesystem_ro`, `gradle_safe`.
- Persistent agent memory at `.claude/agent-memory/android-rd-specialist/` (23 files + `MEMORY.md`) - the only artifact that compounds per-session for the agent.

## 3. Research Speed: Friction and Fixes

### Friction

1. **Workspace noise.** `node_modules`, `temp`, `DOWNLOADS`, `.venv`, `logs` not excluded from VS Code or default `rg`.
2. **Documentation drifts from Gradle truth.** `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `CLAUDE.md` carry stale AGP / KSP / Hilt / Compose / Room values. Agents must re-open Gradle files to trust answers.
3. **"Catalog-first always" rule is overstated.** Catalog query ~423 ms vs `rg` exact-name lookup ~44 ms. The right rule is narrower (see §3 recommendations).
4. **`docs-search` MCP scoped to `docs/` only.** Richer knowledge sits in `dev/`, `PLAN/`, root rule files, spec catalog, class catalog, activity catalog - none searchable via MCP.
5. **CLAUDE.md catalog-commit instruction is stale.** `dev/CATALOG/<module>.{jsonl,md}` are `.gitignored`; the step-5 "commit alongside code" line contradicts reality.
6. **VS Code Java setup is fast but flavor-blind.** `java.project.sourcePaths` is narrow; does not reflect the real `sourceSets` wiring in `app_v2/build.gradle.kts`.
7. **`scripts/log-ai-request.ps1` is unused.** Request history is currently recoverable only from `FUNCTIONALITY.log`, `git log`, and `temp/sessions/`.

### Recommendations

1. **Patch `.vscode/settings.json` exclusions** (5 minutes, ~3.5x reduction in file enumeration):
   - `**/node_modules/**`
   - `**/.venv/**`
   - `**/temp/**`
   - `**/DOWNLOADS/**`
   - `**/logs/**`
   - `**/.kotlin/**`
   Apply to `files.exclude`, `search.exclude`, `files.watcherExclude`.
2. **Default `rg` invocation pattern for agents:**
   - `rg --hidden -g '!**/node_modules/**' -g '!**/build/**' -g '!**/.gradle/**' -g '!**/temp/**' ...`
3. **Refine the lookup rule:**
   - Catalog first: injected type, side effects, test coverage, class role/status, decomposition candidates.
   - `rg` first: exact class name, exact file name, exact resource key, exact log tag/token, exact string literal.
4. **Fix CLAUDE.md §"Post-Change Steps" step 5** - replace "commit updated `dev/CATALOG/<module>.{jsonl,md}` together with the code change" with "run `scripts/catalog_sync.ps1 -Module <m>` after every `.kt` change; the resulting `*.jsonl` and `*.md` are local indexes (gitignored) - regenerate, do not commit."
5. **Allow direct JSONL reads for narrow catalog queries.** One-line note in `dev/CATALOG/README.md`: "Single-class lookup may read `<module>.jsonl` directly; multi-filter / role-cross-reference queries use `query.ps1`."
6. **Expand `docs-search` MCP** (or add `repo-knowledge` MCP) to include:
   - `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`
   - `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`
   - `PLAN/spec-catalog.jsonl`, `dev/CATALOG/*`, `dev/ACTIVITY_CATALOG/*`
7. **Docs-vs-Gradle drift checker** (`scripts/check-doc-vs-gradle.ps1`):
   - AGP, Kotlin, KSP, Hilt, Room, Glide
   - compileSdk / targetSdk / minSdk
   - Gradle wrapper version, Room schema version
   - Fail PR check on mismatch.
8. **`scripts/agent_bootstrap.ps1`** - single startup packet printed at session start (<40 lines):
   - branch, last commit, dirty-tree count grouped by area
   - flag if likely-touched infra files are already modified
   - active `Sxxxx` ticket (from request or recent commits)
   - PowerShell path, mandatory prompt route, recommended first files
   - warning when docs and Gradle disagree
   - recent UI churn probe: grep `FUNCTIONALITY.log` for the screen named in the request and surface the last 5 entries
9. **Pre-populate per-agent memory directories.** Other agents listed in prompts (`android-kotlin-developer`, `android-solution-researcher`, `friendly-android-doc-writer`, `claude-code-guide`) have no memory directory. Seed `.claude/agent-memory/<name>/MEMORY.md` with starter feedback files when they next handle non-trivial work.

## 4. Development Speed: Friction and Fixes

### Friction

1. **`scripts/post-change.ps1` is close to right but broken.** Invokes `pwsh -File` **without `-NoProfile`** (3..4 wasted profile loads ~ 600..800 ms per ritual). Runs `scan.ps1` + `render.ps1` as two separate child processes instead of using `scripts/catalog_sync.ps1`.
2. **Post-change ritual is fragmented.** Dev log, catalog sync, string audit, feature/functionality log, spec status, validation ladder are documented but not represented as one task-aware closure command.
3. **Configuration cache disabled for all builds** because of noLegal/Chaquopy constraints. Standard/lite/photos pay the cost too.
4. **Several Kotlin files are at or above the 1500 LOC limit.** `gradle.properties` explicitly calls out `VideoPlayerManager.kt` as a compiler-pressure hotspot. Observed >=~1000 LOC: `PlayerActivity.kt`, `PdfViewerManager.kt`, `TextViewerManager.kt`, `ImageLoadingManager.kt`, `CommandPanelController.kt`, `PlayerManagerInitializer.kt`, `GoogleDriveRestClient.kt`, `MediaFileAdapter.kt`, `SmbConnectionManager.kt`, `DropboxClient.kt`.
5. **`testStandardDebugUnitTest` carries ~26 pre-existing broken tests.** They contaminate every "did I break tests" check. Memory `feedback_build_pre_existing_test_failures.md` documents this; the fix is triage, not memory.
6. **Gradle MCP allowlist is narrow.** Missing common targeted validation commands (`:app_v2:compileStandardDebugKotlin`, noLegal/wear siblings, single-test wrappers).
7. **Build-output truncation trap.** `./gradlew.bat <task> 2>&1 | tail -30` silently swallows the FAILURE block (sits in the middle of output). Memory `feedback_build_output_pipe_truncation.md` documents the trap; the wrapper that prevents it does not exist yet.
8. **Builds are slow but treated as blocking.** `./gradlew.bat assembleStandardDebug` can run in the background while the agent updates docs, catalog, or plans next steps - this is under-used.
9. **The post-change ritual prescribes 7 numbered steps with fail-closed gating, but the agent reads every step every conversation** to decide which apply. Pure cognitive overhead for a typical Kotlin refactor where steps 2 (feature docs), 3 (functionality log), 4 (string audit) are skips.

### Recommendations

1. **Fix `scripts/post-change.ps1`** (5 minutes):
   - `pwsh -NoProfile -File` for all child invocations
   - replace separate scan/render with `scripts/catalog_sync.ps1 -Module <module>`
   - emit explicit exit codes for each step
2. **Widen `scripts/post-change.ps1` to a `-ChangeType` aware dispatcher:**
   - `-ChangeType Doc|Script|Config|Kotlin|Xml|Mixed`
   - internally selects the minimal safe validation path
   - replaces the agent's manual step-selection
3. **`scripts/agent_validate.ps1`** as a task-aware validation facade:
   - `-ChangeType`, `-Module`, `-Flavor`, `-Tests <pattern>`
   - output in the repo-required `expected: X | actual: Y` format
4. **`scripts/builders/get-last-build-failure.ps1`** - read the most recent `temp/build-debug-*.log`, find the FAILURE block, print it. Eliminates the tail-truncation trap.
5. **Expand Gradle MCP allowlist:**
   - `:app_v2:compileStandardDebugKotlin`
   - `:app_v2:compileNoLegalDebugKotlin`
   - `:wear:assembleDebug`
   - safe `testStandardDebugUnitTest --tests <pattern>` wrapper
   - `a.ps1 dq` (quiet debug build)
   - `scripts/catalog_sync.ps1` for both modules
6. **`a.ps1` aliases for missing common cases:**
   - `a.ps1 cs` → `catalog_sync.ps1 -Module app_v2`
   - `a.ps1 cw` → `catalog_sync.ps1 -Module wear`
   - `a.ps1 ab` → `agent_bootstrap.ps1`
   - `a.ps1 sl <id>` → `select.ps1 -Id <id>`
7. **Investigate configuration cache for non-noLegal flavors.** If `assembleStandardDebug` / `assembleLiteDebug` / `assemblePhotosDebug` can re-enable config cache while noLegal keeps `--no-configuration-cache`, route flavors through dedicated wrappers.
8. **Split `VideoPlayerManager.kt`** as its own spec ticket. Use the established `helpers/*Manager.kt` pattern. Treat as build-speed work, not cosmetics.
9. **Triage `testStandardDebugUnitTest`** - either fix the 26 broken tests or quarantine into a tagged suite. Own spec ticket.
10. **Adopt async-build workflow explicitly.** While `assembleStandardDebug` runs in background, the agent updates `CHANGELOG.md`, catalog, plans next steps. Memory rule: never block synchronously on a build that exceeds 20 s unless the next step strictly depends on its result.
11. **Dirty-tree guard.** Lightweight classifier before edits: file is `clean`, `already modified by user`, `adjacent to modified files`, or `high-risk overlap`. One-line `git status --short | wc -l` at session start; full enumeration only when the planned edit lands in a modified area.

## 5. VS Code, MCP, and Permissions

### `.vscode/settings.json`

- Add exclusions listed in §3.1 to `files.exclude`, `search.exclude`, `files.watcherExclude`.
- Keep `java.import.gradle.enabled=false` and `java.autobuild.enabled=false` - Android Studio / Gradle remains the source of truth.
- Add `chat.tools.terminal.autoApprove` patterns for read-only diagnostics: `pwsh -NoProfile -File scripts/*`, `git status --short`, `git branch --show-current`, `git log --oneline -*`.

### `.vscode/tasks.json` (new)

Add tasks for repeatedly-needed commands: `agent bootstrap`, `research dossier`, `catalog sync app_v2`, `compile standard debug`, `test standard debug`, `build debug quiet`, `extract logs`.

### MCP

- Expand `docs-search` (or add `repo-knowledge`) per §3.6.
- Expand `gradle_safe` allowlist per §4.5.
- Ensure MCP `node_modules` stays installed locally - avoid `npx -y` for frequently-used filesystem MCP if it resolves packages on startup. Prefer pinned local installs.
- Consider a Codex-specific MCP config if the Codex harness cannot consume the VS Code `mcp.json`.

### `.claude/settings.json` (allowlist)

Currently 14 lines, narrow. Notable absences:
- `Bash("/c/Program Files/PowerShell/7/pwsh.exe" *)` for direct pwsh invocation from git-bash.
- `Bash(./a.ps1 *)` for the operator-facing two-letter facade.
- `Bash(./gradlew *)` (only `./gradlew.bat *`) - cross-shell pain from git-bash.

Use the `fewer-permission-prompts` skill to scan transcripts and propose a tighter, more useful allowlist.

## 6. Conversation and Request-History Analysis

### Sources

No persistent multi-agent chat log exists. Findings below combine:
- 21 transcript files (GitHub Copilot session storage) - 17 sessions with explicit prompts, 81 user messages.
- `dev/FUNCTIONALITY.log` - 150 entries from 2026-05-14 to 2026-05-20.
- `PLAN/spec-catalog.jsonl` - 267 specs, last 30 read in detail.
- `git log` for the last 50 commits across `DEBUG-v001..v004`.
- `logs/dev_progress.log` - structured step log from S0251 work on 2026-05-19.
- `temp/sessions/` - 4 captured session traces.
- 23 memory files in `.claude/agent-memory/android-rd-specialist/`.

### Volume and cadence

- Spec velocity: ~30 new specs in the last 7 days (S0238..S0267). ~4..5 per day.
- User-visible changes: ~150 in 7 days. ~20 per day. Sustained.
- Commit cadence: 25+ commits in the last 4 days on `DEBUG-v004` plus release-merge commits to `main`.
- Spec status mix (last 30): ~15 `Verified`, ~10 `BlockNeedUserTest`, 2 `Implemented`, 2 `Draft`, 1 `Archived`, 1 `BlockQuestions` (S0267 - active blocker).
- Same-day `BlockNeedUserTest → Verified` is typical.

### Language mix

- 45 user messages with Cyrillic; 36 Latin-only.
- Russian intent / English technical tokens (file paths, commands, tickets, vocabulary) - preserve this bilingual shape.

### Session-entry prompt classification

Raw session counts overstate fresh-start work. Real classification:
- `terminal_notification`: 5
- `continue_prompt`: 4
- `natural_language_ru`: 3
- `ticket_directive`: 3
- `natural_language_other`: 1
- `slash_command`: 1

**Implication:** the workflow is **resume-heavy**, not blank-slate. Continuity tooling is the right optimisation target.

### Real slash-command usage

- `/catalog`: 8 · `/build`: 7 · `/spec`: 4 · `/spec-dev`: 1

### Most-referenced spec tickets in prompt history

- `S0241`: 56 references · `S0125`: 25 · `S0260`: 23 · long tail beyond that.

### Topic distribution (FUNCTIONALITY.log, last 7 days)

1. **Settings UI iteration** (~35 entries: S0125 / S0254 / S0255 / S0258 / S0259 / S0261 / S0263 + unscoped tweaks). S0125 alone went `CHANGE → CHANGE → FIX → CHANGE → FIX → FIX → FIX → CHANGE → DELETE` in 36 hours. **Dominant theme.**
2. **Network/IO resilience** (~20 entries: S0202 / S0205 / S0206 / S0212 / S0213 / S0219 / S0228 / S0237 / S0246..S0248 / S0252 / S0262 / S0265). SMB/SFTP/FTP/cloud round-tripping.
3. **Cloud auth** (~15 entries: S0200 / S0232..S0236 / S0239 / S0243 / S0257 / S0266 / S0267). Google Credential Manager + OneDrive + Dropbox + GMS version handling.
4. **VR** (~10 entries: S0240 / S0241 / S0244 / S0245 / S0249..S0251). Stack removed (S0241, 2026-05-18) then re-introduced via `noLegal` unification (S0250, 2026-05-19) within 36 hours.
5. **Memory/playback** (S0207 / S0213). Low-memory gating tuned three times.
6. **Documents/notes** (S0189 / S0191 / S0192).
7. **TV/keyboard input** (S0230). Universal input router.
8. **Distribution** (S0214 / S0215). GitHub Releases + IzzyOnDroid.
9. **Code decomposition** (S0002 Waves 40, 41). `BaseFileOperationHandler.kt` 939 → 404 LOC; `FtpFileOperationHandler.kt` 938 → 567 LOC. The 1500 LOC cap is actively enforced.

### Observed patterns

1. **Spec-driven near-100%.** Every non-`release:`/`chore:` commit carries an `Sxxxx`. The skill router is the load-bearing workflow.
2. **High [FIX] ratio.** Roughly half of `FUNCTIONALITY.log` entries are `[FIX]`. Many apply to features that landed in the same week. The `BlockNeedUserTest` gate is doing real work.
3. **Iterative UI churn on settings.** S0125's 10-cycle thrash is normal here. Code scaffolding that anticipates "the final shape" tends to be wrong.
4. **VR is strategically volatile.** Removed in S0241, reborn in S0250. New architecture: `standard ⊂ vr ⊂ noLegal`. Do not re-litigate.
5. **The operator uses interruption as a tool.** Tasks must be resumable from any cut point.
6. **Quick fixes bypass the spec workflow.** ~25 of 150 entries are `[------]` (no `Sxxxx`). The `/quick` skill exists for this case.
7. **Owner gives direct execution orders, not negotiations.** Make reasonable assumptions, act, document scope limits in the artefact.
8. **Owner prefers durable repo-local artefacts** (`dev/`, `PLAN/`, `temp/`), not chat-only analysis.
9. **Owner values agent self-improvement.** Audits and meta-work are first-class requests.

### Behavioural implications

- **Settings UI requests: design-lock before code.** On any request mentioning settings placement/visibility, run `/ui-clarify` before writing code. Even when the request reads like a directive.
- **Network/IO requests: assume device validation is required.** Insert Timber tags eagerly; do not collapse phases.
- **VR requests: respect the settled hierarchy** (`standard ⊂ vr ⊂ noLegal`). Bind interfaces in `src/<flavor>/java/`, not `BuildConfig` guards in `src/main`.
- **Cloud auth requests: read recent S023x chain first.** Decisions are layered (S0232 shared applicationId, S0233 credential chooser fallback, S0235 Dropbox scope, S0239 GMS guard, S0243 auth-result channel).
- **Quick UI fixes: route to `/quick`, not `/spec*`.** Typo / colour shift / single-line repair → no spec lifecycle.
- **Resume-heavy workflow → continuity tooling beats discovery tooling.** Build `scripts/resume-context.ps1` (branch, last failing build, latest log exceptions, nearby modified files, next expected action). Pair with `temp/sessions/<timestamp>_<agent>_state.md` writing for substantial tasks.
- **Memory expansion target: domain, not process.** Most agent memory is process (PowerShell, tags, sub-agents, scaffolding). The operator's domain (SMB / SFTP / Credential Manager / VR session / Glide loaders / Room migrations) is under-represented. Write domain memory **in the moment**, not in batch.
- **Filter terminal notifications out of request-intent analytics.** They are useful operationally but bad signals for intent classification.
- **Repair `scripts/log-ai-request.ps1`** so it is actually used at the start and end of substantial agent tasks. Minimum fields: timestamp, raw user request, inferred route, module/flavor, `Sxxxx`, files touched, validation run, interruption/resume marker, outcome.

## 7. Strategic Insights (Synthesis)

### 7.1 Native agent tools change the calculus

Agents with native `PowerShell` tool, native `Read`/`Glob`/`Grep`, or native concurrent-tool-call APIs **do not pay** the 200..500 ms PowerShell cold-start cost for inline operations. Inline PowerShell in a native PS tool: parse `dev/CATALOG/app_v2.jsonl`, query `$PSVersionTable`, call `Get-Content` - free of spawn.

**Implications:**
- Reserve `pwsh -NoProfile -File ...` for repo scripts that add semantic value (`catalog_sync`, `post-change`, builders, `spec_catalog/*`).
- For inline file reads, grep, JSON parse, env probe - use native tools.
- The `scripts/pwsh` git-bash shim proposed by some audits is **unnecessary** for harnesses that already expose a native PowerShell tool. Verify your harness before recommending PATH workarounds.

### 7.2 The biggest hidden cost is operator iteration thrash

S0125 ran 10 CHANGE/FIX cycles in 36 hours. Total agent share of that cost: scaffolding a multi-tab settings host from an incomplete brief, refactoring three times as the brief moved.

The lesson is in memory already (`feedback_no_scaffolding_as_done.md`): "do not call scaffolding done". The **offensive** version: **on settings UI requests, refuse to scaffold without a locked placement contract.** Use `/ui-clarify` harder than elsewhere.

### 7.3 The memory layer is the only thing that compounds per-session

23 files in `.claude/agent-memory/android-rd-specialist/`, all earned from a specific past failure or confirmed pattern. Over three months of conversation, that is the only artefact that meaningfully improves per-session **for the agent specifically**. Everything else (catalog, spec journal, dev log) compounds equally for operator and all agents.

But most of that memory is **process** (PowerShell, tags, sub-agents, scaffolding). The operator's **domain** (SMB / SFTP / Credential Manager / VR / Room) is barely represented. Write domain memory when next touching the area, not as a batch.

### 7.4 Sub-agent spawning is the wrong default

Every sub-agent burns ~2000 input tokens re-reading `CLAUDE.md` before doing the actual work. For lookup-class questions ("where is class X", "which file holds feature Y"), a direct `Read` of `dev/CATALOG/app_v2.jsonl` is faster, cheaper, and keeps the result in the main thread.

**Heuristic to follow:**
- Spawn sub-agent only when (a) genuinely parallel to main thread, (b) >=10 tool calls of work, (c) the sub-agent's output is a digest, not raw data the caller must re-process.
- For class lookup / file location / single-file read: inline.
- For build validation while writing code: sub-agent (background) is correct.
- For "audit this 2000-line file for X": inline if you have the context; sub-agent if you'd otherwise lose 4000 tokens to the read.

### 7.5 The post-change ritual is over-engineered

`CLAUDE.md` §"Post-Change Steps" prescribes 7 numbered fail-closed steps. For a typical Kotlin refactor (no string change, no new feature), steps 2/3/4 are skips. But the agent reads every step every conversation to decide which apply.

The right shape is a single `scripts/post_change.ps1 -ChangeType <kotlin|doc|config|xml|mixed>` that internally decides which sub-steps apply. The current script exists; widening it from 4 hardcoded steps to a type-aware dispatcher is the right next iteration (and, separately, fix the missing `-NoProfile` per §4.1).

### 7.6 Dirty worktree is normal, not an alarm

125 modified files on `DEBUG-v004` is the operator's steady state, not an emergency. `DEBUG-vNNN` branches are workspaces; they are dirty until release-merge to `main`. A one-line `git status --short | wc -l` at session start is enough situational awareness; full enumeration only when planned edits land in shared infra (`CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`).

### 7.7 The version commit-message format is unreadable

Commits land as `2605192322` (Y.YM.MDDH.Hmm) with no description. `git log --oneline -50` becomes useless for non-`feat`/`fix`/`release` commits - one must read `FUNCTIONALITY.log` or open the commit body to learn what changed.

**Suggestion:** keep the version tag in the commit body (or trailing line); lead the subject with the actual change: `feat(S0125): revised settings host - first cut`, not `2605192322`. `scripts/add_to_dev_log.ps1` already takes a description; surfacing it into the commit subject is a one-line change in whatever commit wrapper the operator uses.

### 7.8 One followups list, not more audits

This is audit number four (claude, codex, gemini, gpt). All four converge on the same handful of fixes that have not landed. **Stop accreting recommendations.** Replace future audits with a single `dev/AUDIT_FOLLOWUPS.md` (or a section in this file) that tracks open items by id with one-line status: `open / in flight / done <sha>`. New audits **edit the followups list** rather than restating the same recommendations.

## 8. Priority Plan

Ordered by `(impact × frequency) / effort`. Items 1..6 are end-of-week candidates. Items 7..10 fit a focused session. Items 11..14 are own spec tickets.

| # | Action | Effort | Effect |
|---|---|---|---|
| 1 | `.vscode/settings.json` exclusions for `node_modules`, `.venv`, `temp`, `DOWNLOADS`, `logs`, `.kotlin` | 5 min | ~3.5x reduction in file enumeration |
| 2 | Fix `scripts/post-change.ps1` (`-NoProfile`, `catalog_sync.ps1`, explicit exit codes) | 5 min | ~600..800 ms saved per ritual step |
| 3 | Fix `CLAUDE.md` §"Post-Change Steps" step 5 (catalog files are gitignored) | 2 min | removes a contradiction every fresh agent reads |
| 4 | Refine catalog-first rule in `CLAUDE.md` and `dev/CATALOG/README.md` to "semantic queries only; `rg` first for exact-name" | 10 min | ~10x faster exact-class lookups |
| 5 | `scripts/agent_bootstrap.ps1` (branch, dirty, S-ticket, route, doc-vs-Gradle warnings, recent UI churn) | 30..45 min | 3..5 redundant tool calls removed per session |
| 6 | `scripts/resume-context.ps1` + `temp/sessions/<ts>_<agent>_state.md` convention | 30 min | resume-heavy workflow needs continuity, not discovery |
| 7 | Widen `scripts/post-change.ps1` to `-ChangeType` aware dispatcher | 60..90 min | collapses 7-step ritual to one call |
| 8 | `scripts/builders/get-last-build-failure.ps1` | 20 min | eliminates `tail -N` truncation trap |
| 9 | Extend `.claude/settings.json` allowlist via `fewer-permission-prompts` skill | 15 min | fewer permission prompts on read-only diagnostics |
| 10 | Allow direct `dev/CATALOG/<module>.jsonl` reads for narrow queries (doc note) | 2 min | skips ~400 ms PS spawn for class lookup |
| 11 | Docs-vs-Gradle drift checker (`scripts/check-doc-vs-gradle.ps1`) | 1..2 hrs | research no longer distrusts docs |
| 12 | Expand `docs-search` MCP (or add `repo-knowledge`) to cover `dev/`, `PLAN/`, root rule files, catalogs | 2..3 hrs | one search surface for repo knowledge |
| 13 | Repair / replace `scripts/log-ai-request.ps1`; add `scripts/agent_request_digest.ps1` | 2..3 hrs | evidence-based future audits |
| 14 | Split `VideoPlayerManager.kt` (and other >=1000 LOC hotspots) | own spec | compile pressure relief |
| 15 | Triage `testStandardDebugUnitTest` (~26 pre-existing broken tests) | own spec | clean "did I break tests" signal |
| 16 | Investigate configuration cache for standard/lite/photos (noLegal stays opted-out) | own spec | normal-loop build-speed win if feasible |

## 9. What NOT To Do

Anti-recommendations - explicit guardrails to stop scope creep.

- **Do not write a fifth audit.** Edit `dev/AUDIT_FOLLOWUPS.md` (once it exists) instead.
- **Do not rewrite `CLAUDE.md` to be shorter.** It is long because it has to be; the cost is read-once per session, the benefit is durable. Make the post-change part shorter (§7.5), not the whole file.
- **Do not add new MCP servers** before utilising the existing four. Expansion before utilisation is overhead, not capability.
- **Do not bulk-fix the ~169 `BuildConfig.IS_*` flavor-guard violations in `src/main/`.** `CLAUDE.md` §15 already calls this out as incremental refactor. A multi-hundred-file PR locks the operator's worktree for days.
- **Do not invest in a "request classifier" or per-agent ML routing** until the simpler followups land. Premature scaffolding has the same shape as S0125.
- **Do not propose a `pwsh` PATH shim** without verifying your harness lacks a native PowerShell tool. Several audits proposed this; one discovered the native tool already exists.
- **Do not block synchronously on builds >20 s** unless the next step strictly depends on the result. Build async; update docs / catalog / plan in parallel.
- **Do not silently fix items spotted during other work** when the worktree is busy and the fix is out of scope. Surface them in the chat reply; let the operator decide.

## 10. Operating Notes For Agents

Distilled from all four audits. Read these before any non-trivial task.

- Start with: branch + dirty status + active `Sxxxx` + skill route. Bundle as `agent_bootstrap.ps1` once it exists; until then run the four commands manually.
- Use `Read` on `dev/CATALOG/<module>.jsonl` for single-class lookups. Use `query.ps1` only when filtering by role / injection / path-glob.
- Use `rg` for exact-token / exact-string / exact-file / exact-class lookups - faster than catalog query for those cases.
- Never `tail -N` a build. Either let the harness show full output, or `> temp/build.log 2>&1` then `Grep -n "FAILURE\|What went wrong\|^e:" temp/build.log`.
- Before editing any `.kt` larger than 500 LOC, `git stash list` and `git status` for that file. Backup to `temp/` first per `CLAUDE.md` §5.
- Russian in chat, English in code/docs/commits. `..` not `...`. `ё`/`Ё` always.
- Timestamp every chat message (`[HH:MM:SS]`).
- Sub-agent only when (a) genuinely parallel and (b) >=10 tool calls of work. Otherwise inline.
- If the answer is "look in `dev/CATALOG/<module>.md`", do not spawn a sub-agent. Read the file directly.
- Treat dirty-tree as **first-class context but not a blocker.** One-line `git status --short | wc -l` at session start; enumerate only when planning to edit shared infra.
- On settings UI work: `/ui-clarify` before code, every time.
- On network/IO work: insert `Timber.d("Sxxxx: ...")` tags eagerly; expect `BlockNeedUserTest` round.
- On VR work: `src/<flavor>/java/`, not `BuildConfig` guards in `src/main`.
- Write domain memory in the moment, not in batch. Next time you touch SMB, Credential Manager, VR session, or Room migration - save what you actually learn.
- For substantial tasks (>few minutes), write `temp/sessions/<timestamp>_<agent>_state.md` with objective, files read, decisions, next command. Resume from the newest matching file before fresh exploration.
- Filter terminal-notification continuation prompts out of intent classification.
- When proposing a recommendation already raised in a prior audit and still unfixed, **link to it instead of restating** - and append a status (open / in flight / done <sha>) to `dev/AUDIT_FOLLOWUPS.md`.

## 11. Evidence Collected

- `git status --short`: 30..125 modified files across audits (current snapshot: 125), including `CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`, 25..30+ `.kt` files.
- `git branch --show-current`: `DEBUG-v004`.
- `git log -5`: most recent commits dated `2605...` (Y.YM.MDDH.Hmm).
- PowerShell probe: confirmed Win NT 10.0.26200.0, 20 logical processors, ~63.7..64 GB RAM, i5-13600K, pwsh 7.6.1.
- `.claude/settings.json`: 14 lines, `defaultMode: acceptEdits`, narrow allowlist.
- `.claude/agent-memory/android-rd-specialist/`: 23 memory files + `MEMORY.md` (25 lines).
- `.vscode/settings.json`: java/gradle import disabled (correct); `search.exclude` missing `node_modules`/`temp`/`DOWNLOADS`/`.venv`/`logs`.
- `.vscode/mcp.json`: 4 servers; `docs-search` scoped to `docs/` only.
- `scripts/post-change.ps1`: confirmed `-NoProfile` missing on child `pwsh` invocations; uses separate `scan.ps1` + `render.ps1` instead of `scripts/catalog_sync.ps1`.
- `scripts/catalog_sync.ps1`: correct.
- `a.ps1`: 326 lines, 21 aliases.
- File-enumeration measurement: `rg --files` ~9,251 → ~2,633 with `node_modules`/`build`/`.gradle` excluded.
- Catalog vs `rg` measurement: catalog query ~423 ms; `rg` exact-name ~44 ms.
- PowerShell cold start: `pwsh -NoProfile` ~200..400 ms (varies by audit); `pwsh` with profile ~440 ms.
- Transcript history (GPT/Copilot): 21 files, 17 sessions with explicit prompts, 81 user messages (45 Cyrillic, 36 Latin).
- Spec-catalog: 267 specs; spec velocity ~30/week; `BlockNeedUserTest` typically cleared same day.
- FUNCTIONALITY.log: ~150 entries / 7 days; ~20 user-visible changes / day.
- Largest active hotspots (>=~1000 LOC): `PlayerActivity.kt`, `PdfViewerManager.kt`, `TextViewerManager.kt`, `ImageLoadingManager.kt`, `CommandPanelController.kt`, `PlayerManagerInitializer.kt`, `GoogleDriveRestClient.kt`, `MediaFileAdapter.kt`, `SmbConnectionManager.kt`, `DropboxClient.kt`. `VideoPlayerManager.kt` flagged in `gradle.properties` as compiler-pressure hotspot.

## 12. Provenance

Each consolidated finding traces to its source audit(s). Where two audits disagreed (e.g., PowerShell cold-start range, catalog-first universality), this file records the measured value with the wider tolerance and surfaces the disagreement in §7.

- Strategic synthesis, audit-format meta-critique, anti-recommendations, native-tool discovery, settings-UI thrash insight, memory-layer analysis, post-change ritual collapse, sub-agent default heuristic, dirty-worktree-as-normal: derived from `claude_audit.md` §§3, 8, 12, 13.
- Environment baseline, file-enumeration measurement, `scripts/post-change.ps1` defect, `repo-knowledge` MCP proposal, docs-vs-Gradle drift checker, request-logger repair: derived from `codex_audit.md` §§"Observed Environment", "Research", "Direct Codex Recommendations".
- Async-build workflow, native-tools-over-shell principle, spec-as-diff-review pattern: derived from `gemini_audit.md` §§2, "Conclusion".
- Workspace-noise inventory, catalog-vs-`rg` measurement, transcript-backed request history (session classification, slash-command counts, S-ticket reference counts), resume-heavy workflow finding, large-Kotlin-file inventory, `scripts/resume-context.ps1` proposal: derived from `gpt_audit.md` §§"Observed Environment", "Research", "Conversation and Request-History Analysis".

The four source audits are preserved at:
- `dev/claude_audit.md` (436 lines, sonnet, VS Code Claude Code extension)
- `dev/codex_audit.md` (372 lines, Codex CLI)
- `dev/gemini_audit.md` (75 lines, Antigravity Gemini)
- `dev/gpt_audit.md` (414 lines, GPT in GitHub Copilot)
