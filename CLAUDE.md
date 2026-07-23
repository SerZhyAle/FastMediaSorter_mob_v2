# CLAUDE.md - Rules for Claude Code

> **Universal conventions (canon):** the portfolio-wide rules live in the SZA Unified Rules at `P:\WEB\sites.google.comsiteszaodua\Unified_Rules` (consumption model: **REFERENCE** - not mirrored here). This repo is the **reference the core was extracted from**, so the rules below are not a re-authoring of the canon - they are this repo's Android operational layer (exact `a.ps1` targets, gate names, the flavor matrix, the Sxxxx spec/probe lifecycle) plus the deltas and channel matrix recorded in `Unified_Rules/contrib/fastmediasorter_mob_v2.md`. When a rule here restates a universal principle, the canon is the source of truth; fix universal rules in a canon session, not here.

> Parallel rule set for non-Claude agents: `AGENTS.md` (import order `CLAUDE.md` -> `.github/copilot-instructions.md` -> prompt; stricter wins). When changing shared rules, sync `AGENTS.md` too.

## 1. Communication & Style
- **Chat**: RU. **Code/Docs/Logs/Commits**: EN. **Tone**: dry, concise. Ask if ambiguous.
- **Ellipsis / Dash / Ё (documentation prose & user-visible UI text ONLY)**: In docs prose and UI strings use `..` (never `...`), plain hyphen `-` (never em-dash `-`, en-dash `–`, or horizontal bar `―`), and Russian Ё/ё where grammatically correct. NEVER applies to code, technical/tactical specs, commands, logs, or chat.
- **Timestamps**: Always accompany replies with a timestamp (HH:mm:ss based on the current local time provided in prompt metadata).
- **Caveman Mode**: Trigger via `/caveman` / `be brief`. Keep RU in chat, EN in code. Drop filler.
- **Spec Writing**: Lists over tables (tables only for 3+ columns). No pseudographics. No self-evident links. One idea per bullet. No section summaries. Draft specs exempt.

## 2. Debug Verification Tags (Sxxxx)
- Invariant: `Timber.d("Sxxxx: <desc>")` exists in `.kt` **if and only if** spec `Sxxxx` is in `BlockNeedUserTest`.
- **Into BlockNeedUserTest**: insert one tag at entry point of changed flows before final build.
- **Out of BlockNeedUserTest**: delete all matching `Timber.d("Sxxxx:` lines immediately.
- Permanent logs must not contain `Sxxxx`.

## 3. Skill Routing (Auto-trigger prompts)
- `/quick`: minor fix (design, typo, 1 string), no spec/build. Update `dev/CHANGELOG.md`.
- `/skill-fix`: fast bug/UI fix, skip doc/git/build/dev-log/spec. Local validation only.
- `/spec`: create/update strategic spec `PLAN/Sxxxx_*.md`.
- `/spec-draft`: side-task idea inbox - scaffold `Draft` spec skeleton, capture raw text verbatim + persist attachments in §0; no research/approval/spec-tech chaining, non-disruptive.
- `/spec-all`: full spec pipeline, idea to verified implementation.
- `/spec-tech`: break approved strategic spec into tactical plan.
- `/spec-update`: review/refine spec file.
- `/spec-dev`: execute tactical spec step-by-step.
- `/spec-quiz`: unstick one spec via multiple-choice Q&A - ask owner only the decisions blocking the next transition, write answers into the spec, advance exactly one lifecycle level (Draft -> Approved, BlockQuestions -> restore).
- `/spec-check`: audit spec against codebase; sets status Verified/Partial/Broken; writes to `## Last Audit`.
- `/spec-fix`: mechanical fixes after spec-check.
- `/spec-arc`: archive one or more specs (move files to `temp/done/`, set status `Archived`).
- `/arc`: alias for `/spec-arc`.
- `/spec-test-device`: on-device verification, UI drive, logcat harvest.
- `/spec-sweep`: batch device-test sweep over BlockNeedUserTest tickets.
- `/spec-prerelease`: end-to-end pre-release emulator sweep (clean install, resources, settings, scenario, perf, verdict) gating `/skill-release`.
- `/release`: full release-campaign runbook (assess -> finish in-flight work -> `/spec-prerelease` -> evaluate -> ready docs incl. "What's New" -> `/skill-release` -> distribute everywhere -> verify). Checklist + work order; `/skill-release` is one step. Terms in `docs/BUILD_VS_RELEASE.md`.
- `/ui-clarify`: resolve layout/UX ambiguity before design/impl.
- `/catalog`: class/feature queries, run `scripts/catalog_sync.ps1`.
- `/doc-update`: docs sync.
- `/log-reader`: logcat/log analysis.
- `/build`: build checklist (work order) + build-system reference. Local "build" flow; terms in `docs/BUILD_VS_RELEASE.md`.
- `/git`, `/caveman`, `/caveman-commit`, `/caveman-review`.

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

## 5. Research Order
1. `dev/PROJECT_OPERATIONS_INDEX.md` (Workspace routing & Feature-to-Path Map).
2. Specs: `scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`.
3. Kotlin classes: `dev/CATALOG/scripts/query.ps1` before global grep.
4. Docs: `docs/ARCHITECTURE.md`, `docs/DEV_OPS.md`, `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md` (mandatory for non-standard flavors).
5. Every agent iteration: at task start, material scope change, phase boundary, and before final response, query `docs/DOCUMENT_REGISTRY.jsonl` through `scripts/document_registry/query.ps1` by product area and change trigger, then read the returned records. State affected records and reasons for unchanged matches. Run `validate.ps1` and `generate.ps1 -Check` when a registered document, page, or registry record changes.
6. Multi-step: read `dev/AGENT_WORKFLOW.md`.

## 6. Proactive Research & Parallelism
- **Web Search**: Use developer.android.com, kotlinlang.org, GitHub issues, StackOverflow without asking.
- **Parallel Subagents**: Concurrently run independent tasks (e.g. build + search).
- **Background long jobs**: Run full/release builds, full test suites, device sweeps via `run_in_background` (harness re-invokes on completion) and continue other work meanwhile. Never foreground-wait a slow build. Still never run >1 gradle build at once - enforced by `temp/BUILD.LOCK` (Rule 23), not just convention.
- **Initiative**: Do not ask permission for searches, builds, queries. Flag blockers at start.
- **Cost discipline**: Default to the playbook in `docs/AGENT_COST_PLAYBOOK.md` - prefer inline over a subagent for single-fact lookups (<=3 targeted tool calls), `/compact` at task boundaries and `/clear` on task switch, offload raw artifacts to `temp/Sxxxx/` (or `temp/scratch/` when no ticket) instead of holding them in chat, and restrict `mobile-mcp` to exploratory UI walks (`adb.ps1`/Maestro first).
- **Subagent MCP isolation**: When defining a subagent, always set `enable_mcp_tools` to `false` unless the subagent strictly requires driving the UI/emulator (exploratory walkthroughs). This prevents duplicate Node/MCP server instances.

## 7. PowerShell Efficiency
- **Always pass `-NoProfile`** (e.g. `pwsh -NoProfile -File ...`).
- **Batch commands** in one process: `& { cmd1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; cmd2 }`.
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
- Tests: `.\a.ps1 fu` (full unit suite) or `.\gradlew.bat testStandardDebugUnitTest`. kapt recovery: `scripts/utils/recover-kapt-stall.ps1`.
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
19. Neuroslop avoidance: No trivial comments, no broad/empty catch-blocks, no hex colors in XML layout (use `?attr` or `@color`), no lifecycle-unsafe Flow collection (use `collectOnLifecycle`), no `GlobalScope` (use `viewModelScope`/lifecycle scope/injected `CoroutineScope`), no non-Timber logging (`android.util.Log.*`/`System.out` -> `Timber.*`), no shipped runtime stubs (`TODO()`/`NotImplementedError`), no typographic long dashes in `.kt` (em-dash `-`/en-dash `–`/horizontal bar `―` -> plain hyphen `-`). Mechanical gate: `scripts/quality/assert-neuroslop.ps1` (ratchet baselines, in `post-change.ps1`). **Detekt-clean-first (S0826):** write touched `.kt` to pass the detekt gate on the first build - keep log/probe lines `<=120` chars (wrap args or shorten), avoid bare numeric literals (use `TimeUnit`/companion `const`/reuse an existing const; `ignoreNumbers` is only -1/0/1/2), and never add `@Suppress` to a method that already has a baselined finding (it shifts the baseline signature and surfaces e.g. `FunctionNaming`).
20. Dead-weight hygiene: Delete orphaned classes, resources, string keys, and keep rules in the same change. Verify on release/target-variant builds.
21. Deprecated PackageManager flags: No raw-int `getPackageInfo`/`getApplicationInfo`/`queryIntentActivities`/`resolveActivity` overloads in `src/main` (deprecated API 33). Use the `*Compat` helpers in `util/PackageManagerCompat.kt`. Mechanical gate: `scripts/quality/assert-deprecated-pm-flags.ps1` (in `post-change.ps1`).
22. Settings docs sync: Any change to a setting - its presence, behavior, position, or naming - must regenerate the settings manifest (`docs/settings/settings-manifest.json`) and reference (`docs/SETTINGS_REFERENCE*.md`) and update its annotation (`docs/settings/settings-annotations.json`). Mechanical gate: `scripts/quality/assert-settings-doc-sync.ps1` (in `post-change.ps1`).
23. Concurrent-agent build/code locks: every script that invokes `gradlew`/`gradlew.bat` directly acquires `temp/BUILD.LOCK` before the call and releases it after (success or failure) via `scripts/utils/agent-lock.ps1` (`Enter-BuildLockOrExit` / `Exit-AgentLock`) - a second gradle-backed invocation refuses, reporting the holder's PID/age/reason. Staleness is judged by PID liveness (never by a guessed timeout while the holder process is actually alive), with a generous safety-net ceiling for edge cases. Before a multi-file source edit (Kotlin/XML/build-file), acquire `temp/CODE.LOCK` via `scripts/utils/enter-code-lock.ps1 -Reason "<ticket/skill>"` and check `scripts/utils/lock-status.ps1 -Name Build` first - if a build is live, do non-code work or wait rather than editing. `CODE.LOCK` auto-releases from `post-change.ps1`'s closure; skills that skip it (`/skill-fix`) must call `scripts/utils/exit-code-lock.ps1` themselves when the edit is done. `CODE.LOCK` is advisory only (Tier 2, no hook enforcement - the agent owns the decision, same model as `dirty-tree-guard.ps1`) - a build script that finds it fresh warns but does not refuse.
24. Bash `find` safety: in the Bash tool never run `find` with a disk-wide root start path (`/`, `~`, drive root, `/c/`, `//host`) or without `-maxdepth`. Prefer the `Glob`/`Grep` tools or `dev/CATALOG/scripts/query.ps1` for file/class lookup - raw `find` is almost never needed here. Reason: on Windows/MSYS an orphaned `find.exe` from a dropped/timed-out session keeps scanning the whole disk and floods handles. Hard gate: global PreToolUse hook `~/.claude/hooks/guard-find-command.ps1` (blocks the tool call before bash spawns; exit 2). This is a hook, not a mechanical `assert-*` gate - editing project scripts does not change it.

## 11. Feature & UI Policies
- **Feature inventory**: `docs/ALL_FEATURES.jsonl` is the EN-only developer inventory of every shipped capability (one JSONL record each), written via `scripts/all_features/add.ps1` and validated by `scripts/all_features/validate.ps1`. It replaced `dev/FUNCTIONALITY.log` (retired); chronology comes from git history + release diffs. `noLegal`-only records go to gitignored `docs/ALL_FEATURES_noLegal.jsonl`. Specs record their delivered capability here.
- **Features (showcase)**: `docs/FEATURES*.md` (EN/RU/UK) is the curated public showcase published to the site, populated ONLY by `/skill-release` from the `ALL_FEATURES` diff since the previous release - never edited per-spec. `noLegal` showcase items go to gitignored `docs/FEATURES_noLegal*.md`.
- **UI Comm**: `docs/COMMUNICATION_POLICY*.md` (EN/RU/UK). Read before modifying user-visible strings.
- **Dialog action pair (S0538/S0684)**: any confirm/cancel pair in a dialog, bottom sheet, or custom layout uses the named styles - confirm = `Widget.FastMediaSorter.Button.DialogConfirm` (green, wide), cancel = `Widget.FastMediaSorter.Button.DialogCancel` (soft-pink tonal, shorter + narrower), destructive confirm = `Widget.FastMediaSorter.Button.DialogDestructive` (red). Never a one-off cancel button (icon-only/selection/scan dialogs are exempt). Standard: `docs/ARCHITECTURE.md` "Button Taxonomy". Gate: `scripts/quality/assert-dialog-cancel-style.ps1` (in `post-change.ps1`).

## 12. Validation & Post-Change
- Record `expected: X | actual: Y` for all checks.
- No completion claim without fresh evidence: before stating anything is done/fixed/passing, run the command that proves it, read its exit code and output, and cite that. Prior runs, assumptions, and self-reports (including a subagent's "build passed") are not evidence - re-run and read it yourself. Red-flag words that mean you are about to claim without proof: "should", "probably", "seems", "looks fixed", "I think it passes" - stop and run the check first.
- Mechanical closure: `pwsh -NoProfile -File scripts/post-change.ps1 -File "<path>" -Target "<target>" -Description "<desc>" -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed> [-Module <app_v2|wear>]`. Skills SHOULD route mechanical closure through this facade instead of hand-rolling each step.
- Dirty-tree closure (S0826): add `-ScopeToFile` to diff-scope the detekt gate to `-File` (fails only on findings in this change) and downgrade the project-wide count-ratchet gates (neuroslop / listener-symmetry / flavor-flag / deprecated-pm) to advisory warnings, so the facade closes a clean change without failing on other tickets' in-flight WIP. Omit it for release/CI to get the strict full-project gate.
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
- **Severity taxonomy** - tag every finding (review comments + `## Last Audit`): P0 crash/ANR/OOM/retained Activity-Fragment-View/deadlock/data-loss (blocks release); P1 data race on shared state, main-thread disk/network I/O, unbounded cache, unreleased heavy resource (fix before merge); P2 hot-path allocation churn, repeated expensive lookups, missing lifecycle-awareness, over-eager startup (fix or ticket); P3 readability/naming/`!!`/style (fix inline or note). P0/P1 require evidence at the matching evidence-ladder rung, not opinion.
- **Listener symmetry**: every `register*`/`addListener`/`addCallback`/`addObserver` (esp. `Player.Listener`, `ContentObserver`, `BroadcastReceiver`) has a matching removal on the **symmetric** lifecycle edge (`onStart`/`onStop`, `onResume`/`onPause`, `onCreate`/`onDestroy`) - never split across asymmetric ones.
- **Room main-safety**: no main-thread Room - `allowMainThreadQueries()` stays banned; DAO methods are `suspend` or return `Flow`; atomic multi-step writes wrapped in `@Transaction`/`withTransaction`; `Flow` queries `distinctUntilChanged` so an unrelated-row write does not re-render.
- **Concurrency correctness**: shared mutable state confined to one thread or guarded (`Mutex`/`synchronized`/`@Volatile`, no read-modify-write race); `withContext(Dispatchers.IO)` sits at the Repository/DataSource boundary, not buried in business logic or pushed onto callers; no `runBlocking`/blocking I/O/`Thread.sleep` on a UI or single-threaded dispatcher.
- **Player/Glide ownership**: one owner per `ExoPlayer`; `release()` on real teardown with `setVideoSurface(null)` + remove every listener + abandon audio focus first; apply the same release contract to every player host, not just the edited one (extends Rule 18). Glide decode at display size (`override(w,h)`), `clear(target)` on detach.
- **R8 / minified proof**: a P0/P1 change touching reflection, serialization, DI graphs, manifests, or dependencies is not done until proven on the **minified release/target variant** (keep rules cover reflective/serialized types; no new `R8: missing class`/`unresolved`), not only on debug. Extends Rule 20.
- Recurring finding -> convert to a mechanical gate (`scripts/quality/assert-*.ps1`), per Rule 19/20 and Layer 8. Do not duplicate the protocol text in rules - link to it.
- Authoring a new rule / gate / command / skill: follow `dev/RULE_AND_SKILL_AUTHORING.md` (observe the failure first, write minimal, close rationalizations, promote recurring rules to gates; trigger-focused `description` fields).
