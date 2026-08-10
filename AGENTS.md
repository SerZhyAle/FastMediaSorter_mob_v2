# FastMediaSorter v2 Agent Protocol

## 1. Source of Truth
- Universal conventions (canon): SZA Unified Rules, shipped as the `sza` Claude Code plugin (`/plugin marketplace add SerZhyAle/sza-unified-rules`; `/plugin install sza@sza-unified-rules`). Consumption model: REFERENCE, not mirrored. This repo is overlay B and the reference the core was extracted from; per-repo overlay facts + channel matrix live in the canon at `rules/contrib/fastmediasorter_mob_v2.md`, adoption stamp `.sza-canon.json`. Canon wins for universal principles; fix them in a canon session.
- Rules: `CLAUDE.md`, `.github/copilot-instructions.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`.
- Stricter rules override. Import order: `CLAUDE.md` -> `.github/copilot-instructions.md` -> prompt/agent file.

## 2. Communication
- Language and tone: one home, canon `rules/AUTHOR.md` "Language" + "Working style". Nothing about it is repo-specific here.
- House text style and its scope: one home, canon `rules/DOCUMENTATION_CONCEPT.md` section 5 "House text style". Repo extension past that scope: long dashes are banned in `.kt` too (CLAUDE.md Rule 19, gate `scripts/quality/assert-neuroslop.ps1`).
- Timestamps: never prefix a reply with a clock time - see `CLAUDE.md` §1.

## 3. Core Rules
- Stack: Android, Kotlin 1.9+, Java 17, Hilt, Room, Media3, Timber.
- Directories: `app_v2/`, `wear/`, `dev/`, `docs/`, `scripts/`, `temp/` (scratch/logs). Read-only: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Temp layout (CLAUDE.md Rule 10.1): ticket-bound scratch/artifacts -> `temp/Sxxxx/` (per-ticket subdir; replaces flat `temp/Sxxxx_*`); no active ticket -> `temp/scratch/`. Fixed infra stays at `temp/` root, never nested: `temp/BUILD.LOCK`, `temp/CODE.LOCK`, `temp/spec-all-queue.lock`, `temp/done/`, `temp/spec-next-skip-cache.json`, raw logcat sinks `temp/current.log` + `temp/fastmediasorter_*.log`, stream-catalog files.
- No Activity logic (delegate to `helpers/*Manager.kt`).
- What to work on next comes from `PLAN/RELEASE_QUEUE.md` (CLAUDE.md 4): release package ascending, then the owner's line order inside it; then `Implemented` rows in `PLAN/RELEASE_READY.md`, then `--` parked lines, then anything unlisted. Catalog `priority` is a tiebreak for unlisted tickets only. Recommend from that file, quote the package and line, state any deviation. `spec-next-preflight.ps1` and `release-plan.ps1` rank from it.
- Bash `find` safety: the rule has one home, canon `rules/GITHUB_INTERACTION.md` section 6. This repo owns only the enforcement: global PreToolUse hook `~/.claude/hooks/guard-find-command.ps1` (blocks the tool call before bash spawns, exit 2), and file/class lookup goes through the `Glob`/`Grep` tools or `dev/CATALOG/scripts/query.ps1`. Detail: CLAUDE.md Rule 24.
- No `.ps1` as a Bash command head (CLAUDE.md Rule 25): never run a PowerShell script directly in Bash (`./a.ps1 fk`, `.\a.ps1 d`, `scripts/foo.ps1`); Bash cannot execute a `.ps1` and the failure returns **exit 0**, so a broken build/check looks like it passed. Always `pwsh -NoProfile -File ./a.ps1 <cmd>` from the repo root (bare `pwsh` via the Git Bash shim). Enforced by the global PreToolUse hook `~/.claude/hooks/guard-ps1-in-bash.ps1`.
- No fire-and-forget verdict (CLAUDE.md Rule 26): never background a gate, a closure facade or a catalog mutator (`post-change.ps1`, `assert-*.ps1`, the `spec_catalog`/`document_registry`/`all_features` CLIs, `add_to_dev_log.ps1`, `catalog_sync.ps1`, `a.ps1 fk/fkn/fc/fr/fg/dq/ch/ss/bf`). It costs an extra turn rather than saving one, and a verdict nobody reads in the same turn is not a verdict. A real long job on the same line still backgrounds normally. Enforced by the global PreToolUse hook `~/.claude/hooks/guard-fire-and-forget.ps1`.
- No slash-command name as an argument value in a Bash-tool `pwsh` call (CLAUDE.md Rule 27): `-Reason "/spec-dev .."` reaches the script as `C:/Program Files/Git/spec-dev ..` because MSYS reads the leading slash as a POSIX path, and the corruption is silent - exit code 0, mangled text in the lock/queue/lease diagnostics (S1458). Use `-Reason "//spec-dev .."`, or prefix `MSYS2_ARG_CONV_EXCL='*'`, or call from the PowerShell tool. Enforced by the **project** PreToolUse hook `.claude/hooks/guard-bash-slash-arg.ps1`, registered in `.claude/settings.json` - unlike the Rule 24-26 guards this one is not global and travels with the checkout.
- Timber only (no `Log.d()`). `Sxxxx` ticket ids only in `BlockNeedUserTest` temporary debug logs.
- Strings: prefer `scripts/utils/set-android-string.ps1`.
- Layouts: portrait edit requires landscape (`res/layout-land/`) edit.
- UI changes: run `/ui-clarify` before implementation.
- WindowInsets: systemBars + displayCutout safe bounds (fitsSystemWindows not enough).
- Dialog action pair (S0538/S0684): confirm/cancel in any dialog/bottom-sheet/custom layout uses the named styles - confirm = `DialogConfirm` (green, wide), cancel = `DialogCancel` (soft-pink tonal, shorter/narrower), destructive = `DialogDestructive` (red). Never a one-off cancel button. Gate: `scripts/quality/assert-dialog-cancel-style.ps1`.
- Settings docs sync (CLAUDE.md Rule 22): any change to a setting, including one hosted in a dialog/bottom-sheet/wizard page, must regenerate `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` and update `docs/settings/settings-annotations.json`. A non-screen surface registers in `SettingsDocScopeCatalog`, never in `SettingsSearchLayoutCatalog` (S1035/S1313). Gate: `scripts/quality/assert-settings-doc-sync.ps1`.

## 4. Research Order
1. `dev/PROJECT_OPERATIONS_INDEX.md`
2. Specs: `scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`
3. Kotlin classes: `dev/CATALOG/scripts/query.ps1` before global grep.
4. Docs: `docs/ARCHITECTURE.md`, `docs/DEV_OPS.md`, `dev/TECH_REQUIREMENTS.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md`.
   Flavors are standard, noLegal, lite, photos, legacy, vr. Which capability is live in which flavor: `docs/FLAVOR_MATRIX.md` - generated from `productFlavors` by `scripts/docs/generate-flavor-matrix.ps1`, enforced against the docs by `scripts/quality/assert-flavor-matrix-docs.ps1`. Never restate the grid from memory (S1392).
5. Document-registry loop: mandatory at task start, material scope change, phase boundary, and before final response - see `.claude/skills/document-registry/SKILL.md`.

## 5. Skill Routing (Load `.github/prompts/*.prompt.md`)
- `/quick`: tiny fix (design, typo, 1 string), skip spec/build.
- `/skill-fix`: fast bug/UI fix, and the doc/config/script tweak that needs no gradle; skip doc/git/build/dev-log.
- **Reach for the two above first.** Measured 2026-08-05 across the whole transcript corpus: `/quick` 0 invocations and `/skill-fix` 2, against `/spec-next` 91 and `/spec-all` 44, in 434 total - the tier ordering was an ungated sentence and never fired once. Claude Code now gets a `UserPromptSubmit` nudge (`.claude/hooks/nudge-small-task-tier.ps1`, advisory, always exit 0); agents outside Claude Code do not, so for them this line is the whole mechanism.
- `/spec*`: spec lifecycle (`/spec`, `/spec-all`, `/spec-tech`, `/spec-update`, `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`, `/spec-test-device`, `/spec-sweep`).
- `/ui-clarify`: resolve UI ambiguity before coding.
- `/catalog`: class/feature queries, sync catalog.
- `/doc-update`: docs sync.
- `/log-reader`: logcat/log analysis.
- `/release`: full release-campaign runbook (assess -> finish work -> `/spec-prerelease` -> docs incl. "What's New" -> `/skill-release` -> distribute -> verify); `/skill-release` is one step. Terms: `docs/BUILD_VS_RELEASE.md`.
- `/build`: build checklist + reference (local "build" flow). `/git`, `/caveman`, `/caveman-commit`, `/caveman-review`.

## 6. Workflow & PowerShell
- Confirm branch before edit (`git branch --show-current`).
- Multi-step: follow `dev/AGENT_WORKFLOW.md` (5 steps).
- After changes (except `/skill-fix`): run `.\scripts\add_to_dev_log.ps1`.
- Kotlin changes: sync catalog via `scripts/catalog_sync.ps1 -Module <app_v2|wear>`.
- Mechanical closure: prefer `scripts/post-change.ps1 -File <p> -Target <t> -Description <d> -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed>` (chains dev-log + catalog-sync + quality gates). On the always-dirty tree add `-ScopeToFile` (diff-scoped detekt + advisory project-wide ratchets); omit for release/CI.
- Concurrent-agent locks and their queue (CLAUDE.md Rule 23, S1432): every script invoking `gradlew`/`gradlew.bat` acquires `temp/BUILD.LOCK` via `scripts/utils/agent-lock.ps1`; before a multi-file source edit acquire `temp/CODE.LOCK` (`scripts/utils/enter-code-lock.ps1 -Reason "..."`). A busy lock no longer refuses - it **queues** you in order of asking: the build path waits by default (`-NoWait` / `FMS_LOCK_NO_WAIT=1` opts out), and `enter-code-lock.ps1` exits **4** for "queued, not yet your turn - do not edit sources yet". Waiting contract: run `scripts/utils/wait-for-lock-turn.ps1 -Name <Build|Code> -Reason "..."` as a **background** task (its exit is the "your turn" signal) and meanwhile do work needing no lock - reading, research, specs, catalog, docs, log analysis. Only sources, resources, build files and repository scripts need the lock. Take `CODE.LOCK` immediately before an edit, release right after, never for a whole ticket. Order and holder: `lock-status.ps1 -Name <Build|Code> -Queue`. Lock staleness follows the holder's liveness (`BUILD.LOCK` by process, `CODE.LOCK` by its owning session); `post-change.ps1` auto-releases `CODE.LOCK`, owner-checked so it never removes another session's lock. A build still only warns on a live `CODE.LOCK`, never refuses.
- Detekt-clean-first: author touched `.kt` to pass detekt on the first build - log/probe lines `<=120` chars, no bare numeric literals (`TimeUnit`/companion `const`/reuse a const), never `@Suppress` a method with a baselined finding (shifts baseline). CLAUDE.md Rule 19.
- PowerShell: Always `-NoProfile`. Batch: `& { cmd1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; cmd2 }`. Use literal `$LASTEXITCODE`. Use project wrappers.
- PowerShell exit codes (S1070): under `$ErrorActionPreference = 'Stop'` a bare `Write-Error` throws and the `exit N` after it never runs, so the code collapses to 1 while the message still prints. Use `Write-Error $msg -ErrorAction Continue` before `exit N` (N != 1); a header must list the codes actually returned. Gate: `scripts/quality/assert-exit-contract.ps1`.
- Cost discipline: follow `docs/AGENT_COST_PLAYBOOK.md` - inline over subagent for single-fact lookups (<=3 tool calls), `/compact` at task boundaries, `/clear` on task switch, offload raw artifacts to `temp/Sxxxx/` (or `temp/scratch/`), restrict `mobile-mcp` to exploratory UI walks (`adb.ps1`/Maestro first).
- Subagent MCP isolation: When defining a subagent, always give it an explicit `tools:` frontmatter allowlist that omits every MCP tool name, unless the subagent strictly requires driving the UI/emulator (exploratory walkthroughs) - omitting `tools:` entirely grants the full set the parent session has, MCP-registered tools included. This prevents duplicate Node/MCP server instances. (S1348: `enable_mcp_tools` is not a real Claude Code option - do not use it.)

## 7. Validation
- Follow `CLAUDE.md` validation ladder. Record `expected: X | actual: Y`.
- No completion claim without fresh evidence: run the proving command, read its exit code/output, cite it. Prior runs and self-reports (incl. a subagent's "build passed") are not evidence. Red-flag words - "should", "probably", "seems", "looks fixed" - mean stop and run the check first.
- Prefer the cheapest proof that matches the change: `.\a.ps1 fk` for Kotlin symbol edits (`fkn` for noLegal), `.\a.ps1 fr` for resources/manifests, `.\a.ps1 fc` for mixed small changes, `.\a.ps1 fg` to batch the fast static gates in one process, and full debug APK builds only when packaging/install behavior matters.

## 8. Code Audit Protocol
- Full protocol: `docs/CODE_AUDIT_PROTOCOL.md`. Apply it on any **audit trigger**: new screen/manager/worker/repository/long-lived helper; lifecycle/coroutine/Flow/listener/observer change; shared-state/dispatcher change; Room entity/DAO/query/migration/transaction change; player/image/cache/network change; startup change; DI scope/singleton change; build/manifest/R8/keep-rule change affecting profiling/startup/minification; reported crash/ANR/OOM/jank/leak; **phase boundary in a multi-phase task** - audit the phase just finished before starting the next.
- Phase-boundary audits are mandatory, not deferred to pipeline end: a defect costs one phase's rework when caught at the next boundary, versus every later phase's rework when it surfaces only at the final audit. `/spec-dev` runs it automatically per phase; apply the same discipline for multi-phase work driven outside `/spec-dev`.
- Tag every finding by severity P0-P3 (P0 crash/leak/data-loss blocks release; P1 race / main-thread I/O / unbounded cache / unreleased resource; P2 hot-path churn / over-eager startup; P3 readability/style). P0/P1 need evidence, not opinion.
- Listener symmetry: every `register*`/`addListener`/`addObserver` has a matching removal on the symmetric lifecycle edge.
- No main-thread Room (`allowMainThreadQueries()` banned); DAO `suspend`/`Flow`; atomic multi-step writes in `@Transaction`/`withTransaction`.
- One owner per `ExoPlayer` with a full release contract (`release()` + `setVideoSurface(null)` + remove listeners + abandon focus); mirror across player hosts. Glide decode-at-size + `clear(target)` on detach.
- Reflection/serialization/DI/manifest/dependency changes are proven on the minified release/target variant, not only debug (extends dead-weight rule).
