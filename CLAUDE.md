# CLAUDE.md - Rules for Claude Code

> **Universal conventions (canon):** the portfolio-wide rules are the SZA Unified Rules, shipped as the `sza` Claude Code plugin (`/plugin marketplace add SerZhyAle/sza-unified-rules`; `/plugin install sza@sza-unified-rules`) - consumption model **REFERENCE**, not mirrored here. This repo is overlay B and the **reference the core was extracted from**, so the rules below are not a re-authoring of the canon - they are this repo's Android operational layer (exact `a.ps1` targets, gate names, the flavor matrix, the Sxxxx spec/probe lifecycle) plus the deltas and channel matrix recorded in the canon at `rules/contrib/fastmediasorter_mob_v2.md` (adoption stamp: `.sza-canon.json`). When a rule here restates a universal principle, the canon is the source of truth; fix universal rules in a canon session, not here.

> Parallel rule set for non-Claude agents: `AGENTS.md` (import order `CLAUDE.md` -> `.github/copilot-instructions.md` -> prompt; stricter wins). When changing shared rules, sync `AGENTS.md` too.

## 1. Communication & Style
- **Language & tone**: one home, canon `rules/AUTHOR.md` "Language" + "Working style". Ask if ambiguous.
- **House text style** and its scope: one home, canon `rules/DOCUMENTATION_CONCEPT.md` section 5 "House text style". Repo extension past that scope: long dashes are banned in `.kt` too (Rule 19, gate `scripts/quality/assert-neuroslop.ps1`).
- **Timestamps**: Always accompany replies with a timestamp (HH:mm:ss based on the current local time provided in prompt metadata).
- **Caveman Mode**: Trigger via `/caveman` / `be brief`. Keep RU in chat, EN in code. Drop filler.
- **Spec Writing**: Lists over tables (tables only for 3+ columns). No pseudographics. No self-evident links. One idea per bullet. No section summaries. Draft specs exempt.

## 2. Debug Verification Tags (Sxxxx)
- Invariant: `Timber.d("Sxxxx: <desc>")` exists in `.kt` **if and only if** spec `Sxxxx` is in `BlockNeedUserTest`.
- **Into BlockNeedUserTest**: insert one tag at entry point of changed flows before final build.
- **Out of BlockNeedUserTest**: delete all matching `Timber.d("Sxxxx:` lines immediately.
- Permanent logs must not contain `Sxxxx`.

## 3. Skill Routing (Auto-trigger prompts)

<!-- S1338 phase 07 owns the CONTENTS of this section - every routable command is named here and nothing named here is missing from `.claude/commands/`. S1340 §3.2 owns COMPRESSING it down to what the harness cannot infer from a command's own `description`. Fix facts here; do not compress here. -->

Every skill's own `description` is injected fresh each turn (see the "Available skills" system reminder) - do not restate it here. This section holds only what that injection cannot supply on its own: the owner's short aliases, and the size-tier ordering across commands whose descriptions overlap.

- **Alias:** `/arc` = `/spec-arc`. Chat alias only - there is no `arc.md` command file to invoke, so expand it here and call `/spec-arc`.
- **Size tier, smallest first:** `/quick` (single string/typo/design tweak, no spec/build) -> `/skill-fix` (fast bug/UI fix, and the doc/config/script tweak that needs no gradle; skips doc/git/build/dev-log/spec) -> `/spec-next`/`/spec-do` (full ticket pipeline; `/spec-do` is the same picker minus the context-threshold stop). Picking too heavy a command for a small fix is the common miss - try the smallest tier first.

**Command file shape.** A command is a driver at `.claude/commands/<name>.md`, injected in full and permanently on invocation. The six largest pipeline commands carry a companion at `.claude/reference/<name>.md` holding their tables, literal formats and edge-case catalogues, read only when the driver names the condition. Verbatim file skeletons live once in `.claude/templates/`. Neither store sits under `.claude/commands/`, because everything there is registered as an invocable command. Follow this shape for a new command.

### 3.1 Auto-capture of out-of-scope findings (`/spec-draft`)
- At **any** stage - research, development, audit, code review, device test, log analysis - when you discover a problem meeting ALL three conditions, invoke the `/spec-draft` procedure to park it, without asking:
  1. Unrelated to the current task/ticket (out of scope).
  2. Not trivial enough to fix on the spot (more than a one-liner, or fixing now would derail current work).
  3. Requires its own research and execution.
- Before creating: dedup-check by symptom (`scripts/spec_catalog/search.ps1`); if an open ticket already covers it, reference that id instead of drafting a duplicate.
- Batch discovery: a general log analysis / audit / review surfacing several qualifying problems → one `/spec-draft` per distinct problem (after dedup).
- Non-disruptive: park it, report `parked: Sxxxx <slug>` in chat, then resume the original task. Never switch the active ticket because of a parked finding.
- Do NOT park: in-scope work, trivial fixes (do them inline), cosmetic nitpicks not worth a ticket, or already-ticketed issues.
- Read-only contexts (e.g. `android-solution-researcher`) cannot mutate the catalog: instead list the qualifying findings as `/spec-draft` candidates in the report for the caller to capture.

## 4. Spec Catalog (Sxxxx tickets)
- Specs: `PLAN/Sxxxx_<slug>.md`. Directory: `PLAN/Sxxxx_<slug>/`.
- Journal: `PLAN/spec-catalog.jsonl` (never hand-edit). CLI: `insert.ps1`, `update.ps1`, `select.ps1`, `delete.ps1`.
- Status header sync: catalog mutators update the first `**Status:**` line in spec file automatically.
- **Block* note (mandatory):** every transition INTO a `Block*` status must supply `-StatusNote '<reason>'` to `update.ps1` / `close-and-log.ps1`. `BlockNeedUserTest` → describe what the user must test on device. `BlockExternal` / `BlockByOtherTask` / `BlockQuestions` → describe the blocker and what resolves it. The note is written to the spec header as `**Status note:**` immediately after `**Status:**` so it is readable at a glance. Leaving a `Block*` status auto-clears the note.
- Statuses: Draft, Approved, Tactical, In Progress, Implemented, Verified, Partial, Broken, Block*, Archived (soft-delete).
- Priority: 0..100 (default 50).
- **Release plan - two files, split by "is there work left".** `PLAN/RELEASE_QUEUE.md` = work still left (every status below `Implemented`, blocked included). `PLAN/RELEASE_READY.md` = finished content (`Implemented`, `Verified`, `BlockNeedUserTest`). Column `rel` = release package number, `--` = not scheduled.
- **The queue decides what gets worked on next - always recommend from it, and obey it.** Package ascending, then the owner's line order inside it; catalog `priority` is only a tiebreak for tickets neither file lists. A deviation needs a stated reason. Catalog owns STATUS, the two files own ASSIGNMENT + ORDER - every catalog write reconciles both automatically, in either direction; no other script may reorder lines or touch `rel`. Enforcing scripts: `scripts/spec_catalog/spec-next-preflight.ps1`, `release-plan.ps1`, `release-queue.ps1` (`-Reconcile`/`-Validate`/`-List`/`-SetCurrent`/`-Ship`).

## 5. Research Order
1. `dev/PROJECT_OPERATIONS_INDEX.md` (Workspace routing & Feature-to-Path Map).
2. Specs: `scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`.
3. Kotlin classes: `dev/CATALOG/scripts/query.ps1` before global grep.
4. Docs: `docs/ARCHITECTURE.md`, `docs/DEV_OPS.md`, `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md` (mandatory for non-standard flavors).
5. Document-registry loop: mandatory at task start, material scope change, phase boundary, and before final response - see `.claude/skills/document-registry/SKILL.md`.
6. Multi-step: read `dev/AGENT_WORKFLOW.md`.

## 6. Proactive Research & Parallelism
- **Web Search**: Use developer.android.com, kotlinlang.org, GitHub issues, StackOverflow without asking.
- **Parallel Subagents**: Concurrently run independent tasks (e.g. a catalog query alongside a docs sweep, or two independent file searches). **Never more than one gradle-backed invocation at a time** - a build, a compile check, detekt, the unit suite. Rule 23 refuses the second one mechanically via `temp/BUILD.LOCK`; parallelise everything that does not touch gradle.
- **Background long jobs - the 120 s threshold**: 120 s is the Bash tool's foreground timeout, so it is the boundary, not a round number. **Above it, backgrounding is required**: full and release builds and the unit suite (`d/db/dav/cd/nd/nl/r/fu`) - a cold gradle daemon exceeds the timeout, and a forced-background handoff loses clean output capture and wastes a turn. **Below it, backgrounding is forbidden**: the fast checks and static gates (`fk/fkn/fc/fr/fg/dq`, `assert-detekt`) measure 14-21 s on a warm daemon (`docs/BUILD_TEST_FAST_PATH.md`, "Foreground or background"), and backgrounding one costs an extra turn plus the hand-polling with `cat`/`sleep` that follows - ~1,297 polling turns and 81 min of literal `sleep` in a month. Wait for a fast check and read its verdict in the same turn. Still never run >1 gradle-backed invocation at once - enforced by `temp/BUILD.LOCK` (Rule 23), not just convention.
- **Initiative**: Do not ask permission for searches, builds, queries. Flag blockers at start.
- **Cost discipline**: Default to the playbook in `docs/AGENT_COST_PLAYBOOK.md` - prefer inline over a subagent for single-fact lookups (<=3 targeted tool calls), `/compact` at task boundaries and `/clear` on task switch, offload raw artifacts to `temp/Sxxxx/` (or `temp/scratch/` when no ticket) instead of holding them in chat, and restrict `mobile-mcp` to exploratory UI walks (`adb.ps1`/Maestro first).
- **Subagent MCP isolation**: When defining a subagent, always give it an explicit `tools:` frontmatter allowlist that omits every MCP tool name, unless the subagent strictly requires driving the UI/emulator (exploratory walkthroughs) - omitting `tools:` entirely grants the full set the parent session has, MCP-registered tools included. This prevents duplicate Node/MCP server instances. (S1348: `enable_mcp_tools` is not a real Claude Code option - do not use it.)

## 7. PowerShell Efficiency
- **Always pass `-NoProfile`** (e.g. `pwsh -NoProfile -File ...`).
- **Batch commands** in one process - **PowerShell tool only**: `& { cmd1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; cmd2 }`. This is PowerShell syntax and it is a syntax error in the Bash tool (`$LASTEXITCODE` is unset, `& { .. }` backgrounds an empty group). To batch the same work from Bash, pass it to the interpreter: `pwsh -NoProfile -Command "& { cmd1; cmd2 }"`, or just call `pwsh -NoProfile -File <script>` per step.
- **Variables**: Write `$` literally, do not escape, avoid double-quoted Command wrappers.
- **Hot Rituals**: Use wrappers (e.g. `scripts/catalog_sync.ps1 -Module <app_v2|wear>` after `.kt` edits).
- **Reachable exit codes (S1070)**: under `$ErrorActionPreference = 'Stop'` a bare `Write-Error` throws, so any `exit N` after it never runs and the process reports 1 - the message still prints, which is why it survives review. Write `Write-Error $msg -ErrorAction Continue` before `exit N` (N != 1). A script's header must list the codes it actually returns. Mechanical gate: `scripts/quality/assert-exit-contract.ps1` (in `assert-fast-gates.ps1` / `.\a.ps1 fg`).

## 8. Project Structure & Tech Stack
- **Modules**: `app_v2/` (main app) and `wear/` (Wear OS) are both active Gradle modules (`settings.gradle.kts`); pass `-Module wear` to `catalog_sync.ps1`/`post-change.ps1` when touching it. Support: `dev/`, `docs/`, `scripts/`, `temp/`. Read-only: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- **Architecture**: UI -> ViewModel -> UseCase -> Repository -> DataSource. UI has zero business logic.
- **Flavors**: standard, lite, photos, legacy. Gated via `BuildConfig` fields.
- **Pins**:
<!-- toolchain-pins:start (generated by scripts/quality/generate-toolchain-pins.ps1 - do not edit by hand) -->

- compileSdk: 36
- targetSdk: 36
- minSdk (standard): 26
- minSdk (legacy): 23
- Kotlin: 2.2.10
- Room: 2.7.0
- Media3 (ExoPlayer): 1.2.1
- Glide: 4.16.0

<!-- toolchain-pins:end -->
- **Logging**: Timber only.

## 9. Common Commands (a.ps1 launcher)
- Build: `.\a.ps1 d` (fast reusable debug APK), `db` (fast debug no-zip), `dav` (debug APK with timestamped app version), `dq` (quiet), `cd` (clean debug), `nd`/`nl` (noLegal debug/release), `r` (release AAB - builds in a dedicated git worktree, not the main checkout; driven by `/skill-release`), `bf` (build failure structured digest).
- Checks: `.\a.ps1 fk` (Kotlin compile, standard), `fkn` (Kotlin compile, noLegal), `fr` (resources/manifest), `fc` (code + resources), `fg` (fast static gates batch - neuroslop+pm+listener+flavor+ticket-log in one process, `-IncludeDetekt` opt-in), `ch` (typos & lint), `ss` (unresolved specs), `ivn` (install noLegal debug APK).
- Tests: `.\a.ps1 fu` (full unit suite - acquires `BUILD.LOCK` per Rule 23; never call `gradlew.bat` directly). kapt recovery: `scripts/utils/recover-kapt-stall.ps1`.
- Device (ad-hoc, ~0 tokens): `scripts/devtest/adb.ps1 <verb>` or `.\a.ps1 adb <verb>` for quick chores against a connected emulator/device - `devices`, `props`, `current`, `launch`, `stop`, `clear`, `install`, `shot`, `log -Tail N -Grep <regex>`, `tap/text/key`, `prefs`, `shell -Cmd`. Auto-discovers adb (not on PATH); supports `-DeviceId`/`-Release`/`-Json` with stable exit codes. Shortcuts: `adb-devices/-shot/-log/-current/-launch/-clear`. Prefer over raw `adb` for one-off work; `mobile-mcp` stays for agent-driven UI walks, Maestro for repeatable flows.

## 10. Strict Rules
1. No root writes; all scratch/artifacts under `temp/`, organized by ticket. **Ticket-bound work -> `temp/Sxxxx/`** (per-ticket subdir; replaces the old flat `temp/Sxxxx_*` prefix). **No active ticket -> `temp/scratch/`**. Fixed infrastructure stays at `temp/` root, never nested: `temp/BUILD.LOCK`, `temp/CODE.LOCK`, `temp/done/` (spec-arc archive), `temp/spec-next-skip-cache.json`, raw logcat sinks `temp/current.log` + `temp/fastmediasorter_*.log`, stream-catalog files (`temp/stream-catalog-liveness.csv`, `temp/stream-catalog.zip`).
2. File size limit: 1500 LOC. Extract logic to `helpers/*Manager.kt`.
3. No Activity logic. Delegate to Manager/Helper classes.
4. Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
5. Backups: Timestamped copy under `temp/Sxxxx/` (or `temp/scratch/` when no ticket) before editing file >500 LOC.
6. Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.
7. Lint: Fix warnings in touched files.
8. Read comments/KDoc in affected area before editing.
9. Comment discipline: EN-only, explain WHY, not WHAT. No trivial comments.
10. UI Ambiguity Gate: Resolve all placement/visibility/fallback decisions before impl (via `/ui-clarify`).
11. Layout orientation: Editing `res/layout/*.xml` requires equivalent edit in `res/layout-land/*.xml` counterpart.
12. Spec catalog: Never edit catalog JSONL directly; never rename Sxxxx_ prefix.
13. Script ownership: Fix buggy/insufficient project scripts rather than working around them.
14. Flavor isolation: No `BuildConfig.IS_*` flavor guards in `src/main/`. Use interfaces + flavor source sets.
15. Non-trivial steps: Record validation command run and its exit code or PASS/FAIL.
16. UI consistency: Support keyboard, D-pad/TV, and mouse inputs. Set focusable, clickable, nextFocus*.
17. System bar safety: Keep UI inside `systemBars` + `displayCutout` safe bounds in portrait/landscape.
18. Lazy optimization: Use Hilt `dagger.Lazy<T>`, `<ViewStub>` for optional layouts, release player/media resources immediately when paused.
19. Neuroslop avoidance: No trivial comments, no broad/empty catch-blocks, no hex colors in XML layout (use `?attr` or `@color`), no lifecycle-unsafe Flow collection (use `collectOnLifecycle`), no `GlobalScope` (use `viewModelScope`/lifecycle scope/injected `CoroutineScope`), no non-Timber logging (`android.util.Log.*`/`System.out` -> `Timber.*`), no shipped runtime stubs (`TODO()`/`NotImplementedError`), no typographic long dashes in `.kt` (em-dash `-`/en-dash `–`/horizontal bar `―` -> plain hyphen `-`). Mechanical gate: `scripts/quality/assert-neuroslop.ps1` (ratchet baselines, in `post-change.ps1`). Detekt-clean-first authoring tips (S0826): `docs/DEV_OPS.md` "Static analysis (detekt + ktlint)". <!-- canon-ok: the canon scopes house text style to prose and user-visible UI only (DOCUMENTATION_CONCEPT.md section 5, its scope list is authoritative); banning long dashes inside .kt SOURCE is this repo's deliberate extension past that scope, enforced mechanically by scripts/quality/assert-neuroslop.ps1. Not a restatement - a delta. -->
20. Dead-weight hygiene: Delete orphaned classes, resources, string keys, and keep rules in the same change. Verify on release/target-variant builds.
21. Deprecated PackageManager flags: No raw-int `getPackageInfo`/`getApplicationInfo`/`queryIntentActivities`/`resolveActivity` overloads in `src/main` (deprecated API 33). Use the `*Compat` helpers in `util/PackageManagerCompat.kt`. Mechanical gate: `scripts/quality/assert-deprecated-pm-flags.ps1` (in `post-change.ps1`).
22. Settings docs sync: Any change to a setting - its presence, behavior, position, or naming, including one hosted in a dialog, bottom sheet, or wizard page rather than a settings screen - must regenerate the settings manifest (`docs/settings/settings-manifest.json`) and reference (`docs/SETTINGS_REFERENCE*.md`) and update its annotation (`docs/settings/settings-annotations.json`). A non-screen surface registers in `SettingsDocScopeCatalog`, never in `SettingsSearchLayoutCatalog` (S1035/S1313). Mechanical gate: `scripts/quality/assert-settings-doc-sync.ps1` (in `post-change.ps1`).
23. Concurrent-agent build/code locks: every script that invokes `gradlew`/`gradlew.bat` directly acquires `temp/BUILD.LOCK` before the call and releases it after (success or failure) via `scripts/utils/agent-lock.ps1` - a second gradle-backed invocation refuses, reporting the holder's PID/age/reason. Before a multi-file source edit (Kotlin/XML/build-file), acquire `temp/CODE.LOCK` via `scripts/utils/enter-code-lock.ps1 -Reason "<ticket/skill>"` and check `scripts/utils/lock-status.ps1 -Name Build` first - if a build is live, do non-code work or wait rather than editing. Staleness judgment, the `CODE.LOCK` advisory-only nuance, and auto-release details: `docs/DEV_OPS.md` "Concurrent-agent locks".
24. Bash `find` safety: the rule itself has one home, canon `rules/GITHUB_INTERACTION.md` section 6. This repo owns only the enforcement, which is the delta worth writing down: the global PreToolUse hook `~/.claude/hooks/guard-find-command.ps1` blocks the tool call before bash spawns (exit 2). It blocks exactly two shapes: a `find` whose arguments carry a disk-wide root start path (`/`, `//`, `~`, a drive root, `/c/`, `//host`), and a `find` with no `-maxdepth` - unbounded depth is what orphans a whole-disk scan on Windows/MSYS. A `find` with a concrete start path **and** `-maxdepth N` passes, as does a `find` inside a quoted string. Prefer the `Glob`/`Grep` tools or `dev/CATALOG/scripts/query.ps1` anyway - raw `find` is almost never needed here. This is a hook, not a mechanical `assert-*` gate - editing project scripts does not change it.
25. No `.ps1` as a Bash command head: in the Bash tool never execute a PowerShell script directly (`./a.ps1 fk`, `.\a.ps1 d`, `scripts/foo.ps1`). Bash cannot run a `.ps1` - it chokes on the BOM + `<#` block ("syntax error near unexpected token newline") and the backgrounded task still reports **exit 0**, so a failed build/check masquerades as passing. Always route through the interpreter: `pwsh -NoProfile -File ./a.ps1 <cmd>` from the repo root (bare `pwsh` resolves via the Git Bash shim). Reading a `.ps1` (Read tool, or `grep`/`head` with a real command head) is fine - only a `.ps1` in *command-head* position is blocked. Hard gate: global PreToolUse hook `~/.claude/hooks/guard-ps1-in-bash.ps1` (quote-aware; allows `pwsh`/`powershell` heads and `.ps1` arguments; blocks before bash spawns; exit 2). Like Rule 24 this is a hook, not a mechanical `assert-*` gate - editing project scripts does not change it.

## 11. Feature & UI Policies
- **Feature inventory**: `docs/ALL_FEATURES.jsonl` is the EN-only developer inventory of every shipped capability (one JSONL record each), written via `scripts/all_features/add.ps1` and validated by `scripts/all_features/validate.ps1`. It replaced `dev/FUNCTIONALITY.log` (retired); chronology comes from git history + release diffs. `noLegal`-only records go to gitignored `docs/ALL_FEATURES_noLegal.jsonl`. Specs record their delivered capability here.
- **Features (showcase)**: `docs/FEATURES*.md` (EN/RU/UK) is the curated public showcase published to the site, populated ONLY by `/skill-release` from the `ALL_FEATURES` diff since the previous release - never edited per-spec. `noLegal` showcase items go to gitignored `docs/FEATURES_noLegal*.md`.
- **UI Comm**: `docs/COMMUNICATION_POLICY*.md` (EN/RU/UK). Read before modifying user-visible strings.
- **Dialog action pair (S0538/S0684)**: any confirm/cancel pair in a dialog, bottom sheet, or custom layout uses the named styles - confirm = `Widget.FastMediaSorter.Button.DialogConfirm` (green, wide), cancel = `Widget.FastMediaSorter.Button.DialogCancel` (soft-pink tonal, shorter + narrower), destructive confirm = `Widget.FastMediaSorter.Button.DialogDestructive` (red). Never a one-off cancel button (icon-only/selection/scan dialogs are exempt). Standard: `docs/ARCHITECTURE.md` "Button Taxonomy". Gate: `scripts/quality/assert-dialog-cancel-style.ps1` (in `post-change.ps1`).

## 12. Validation & Post-Change
- Record `expected: X | actual: Y` for all checks.
- No completion claim without fresh evidence: before stating anything is done/fixed/passing, run the command that proves it, read its exit code and output, and cite that. Prior runs, assumptions, and self-reports (including a subagent's "build passed") are not evidence - re-run and read it yourself. Red-flag words that mean you are about to claim without proof: "should", "probably", "seems", "looks fixed", "I think it passes" - stop and run the check first.
- Mechanical closure: `pwsh -NoProfile -File scripts/post-change.ps1 -File "<path>" -Target "<target>" -Description "<desc>" -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed> [-Module <app_v2|wear>]`. Skills SHOULD route mechanical closure through this facade instead of hand-rolling each step.
- Dirty-tree closure (S0826/S1338): name the **whole** changed set - `-Files "a.kt,b.kt"` for a multi-file change, `-File` for a single one - then add `-ScopeToFile` to judge every scoped gate against that whole set. detekt fails only on findings inside the set, and the count-ratchet gates (neuroslop / listener-symmetry / flavor-flag / deprecated-pm / public-mutable-flow) judge a FATAL per-file delta against HEAD instead of a project-wide count, so other tickets' in-flight WIP does not block the close. Only the repo-wide re-render gates (icon-inventory, script-cheatsheet, device-profile-matrix) stay advisory. Omit `-ScopeToFile` for release/CI to get the strict full-project gate. Naming one file while changing four certifies one file - the verdict covers exactly what you passed.
- Closure verdict and exit codes (S1338): `post-change: PASS` is printed only when every gate passed; an advisory gate that found something prints `PASS WITH ADVISORIES (n)` and names each one - read it, the bare word is the only clean verdict. Exit codes: **0** passed, **1** a gate failed, **2** could not verify (invalid, absent or unexpanded file argument; missing tooling). A caller must distinguish 1 from 2 - "found a defect" and "did not look" are different answers. The gates run before the mutating steps, so a failed closure writes no changelog row and re-running after a fix produces exactly one.
- Journaling granularity: one dev-log entry per logical change/ticket, not per touched file (batch multi-file changes via `close-and-log.ps1 -DevLogs`); run `catalog_sync.ps1` once per ticket, not per `.kt` edit.
- Validation Ladder:
  - Doc: Grep for content.
  - Script: Run, exit 0.
  - Config: Target build passes.
  - Kotlin/Java: prefer `.\a.ps1 fk` for compile-only symbol changes; escalate to `.\a.ps1 fc`, targeted `--tests`, or full `.\a.ps1 d` only when the touched area needs packaging/resource proof.
  - Python: Syntax check + test.
  - Layout/manifest: Target build passes.

## 13. Code Audit Protocol
- Full protocol: `docs/CODE_AUDIT_PROTOCOL.md` (layered audit, evidence ladder, per-layer checklists). Read and apply it whenever an **audit trigger** fires - do not re-derive the checks. Use the cheapest evidence rung that matches the risk.
- **Audit triggers**: new screen/manager/worker/repository/long-lived helper; lifecycle change; coroutine/Flow/callback/listener/observer change; shared-mutable-state/synchronization/dispatcher change; Room entity/DAO/query/migration/transaction change; player/image-loading/caching/network-path change; startup-path change; DI scope or singleton-ownership change; build/manifest/R8/keep-rule change affecting profiling/startup/minification/process; reported crash/ANR/OOM/jank/leak; **phase boundary in a multi-phase task** (tactical spec phase, or any large task split into sequential phases) - audit the phase just finished before starting the next.
- **Phase-boundary audits are mandatory, not deferred to pipeline end**: fixing a defect costs roughly one phase's worth of rework when caught at the next phase boundary, versus every subsequent phase's worth when it surfaces only at the final audit (`/spec-check`) or in a later ticket. `/spec-dev` runs this automatically per phase (see its "Phase-boundary audit" step); apply the same discipline manually for any multi-phase work driven outside `/spec-dev`.
- Per-layer checklists (severity taxonomy, listener symmetry, Room main-safety, concurrency correctness, player/Glide ownership, R8/minified proof) live only in `docs/CODE_AUDIT_PROTOCOL.md` Layers 1-8 - read them there when a trigger fires, do not expect a copy here.
- Recurring finding -> convert to a mechanical gate (`scripts/quality/assert-*.ps1`), per Rule 19/20 and Layer 8. Do not duplicate the protocol text in rules - link to it.
- Authoring a new rule / gate / command / skill: follow `dev/RULE_AND_SKILL_AUTHORING.md` (observe the failure first, write minimal, close rationalizations, promote recurring rules to gates; trigger-focused `description` fields).
