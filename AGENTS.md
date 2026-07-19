# FastMediaSorter v2 Agent Protocol

## 1. Source of Truth
- Rules: `CLAUDE.md`, `.github/copilot-instructions.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`.
- Stricter rules override. Import order: `CLAUDE.md` -> `.github/copilot-instructions.md` -> prompt/agent file.

## 2. Communication
- Chat: RU. Code, docs, logs, commits, changelog: EN. Dry, concise.
- Ellipsis / Dash / Ё (documentation prose & user-visible UI text ONLY): `..` (never `...`), plain hyphen `-` (never em-dash `-`, en-dash `–`, or horizontal bar `―`), Russian Ё/ё where grammatically correct. Never enforce these typography rules in code, technical/tactical specs, commands, logs, or chat.
- Timestamps: Always accompany replies with a timestamp (HH:mm:ss based on the current local time provided in prompt metadata).

## 3. Core Rules
- Stack: Android, Kotlin 1.9+, Java 17, Hilt, Room, Media3, Timber.
- Directories: `app_v2/`, `wear/`, `dev/`, `docs/`, `scripts/`, `temp/` (scratch/logs). Read-only: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Temp layout (CLAUDE.md Rule 10.1): ticket-bound scratch/artifacts -> `temp/Sxxxx/` (per-ticket subdir; replaces flat `temp/Sxxxx_*`); no active ticket -> `temp/scratch/`. Fixed infra stays at `temp/` root, never nested: `temp/BUILD.LOCK`, `temp/CODE.LOCK`, `temp/done/`, `temp/spec-next-skip-cache.json`, raw logcat sinks `temp/current.log` + `temp/fastmediasorter_*.log`, stream-catalog files.
- No Activity logic (delegate to `helpers/*Manager.kt`).
- Bash `find` safety (CLAUDE.md Rule 24): never run `find` with a disk-wide root path (`/`, `~`, drive root, `/c/`, `//host`) or without `-maxdepth`; prefer Glob/Grep or `dev/CATALOG/scripts/query.ps1`. Reason: on Windows/MSYS an orphaned `find.exe` from a dropped session scans the whole disk and floods handles. Enforced by the global PreToolUse hook `~/.claude/hooks/guard-find-command.ps1`.
- Timber only (no `Log.d()`). `Sxxxx` ticket ids only in `BlockNeedUserTest` temporary debug logs.
- Strings: prefer `scripts/utils/set-android-string.ps1`.
- Layouts: portrait edit requires landscape (`res/layout-land/`) edit.
- UI changes: run `/ui-clarify` before implementation.
- WindowInsets: systemBars + displayCutout safe bounds (fitsSystemWindows not enough).
- Dialog action pair (S0538/S0684): confirm/cancel in any dialog/bottom-sheet/custom layout uses the named styles - confirm = `DialogConfirm` (green, wide), cancel = `DialogCancel` (soft-pink tonal, shorter/narrower), destructive = `DialogDestructive` (red). Never a one-off cancel button. Gate: `scripts/quality/assert-dialog-cancel-style.ps1`.

## 4. Research Order
1. `dev/PROJECT_OPERATIONS_INDEX.md`
2. Specs: `scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`
3. Kotlin classes: `dev/CATALOG/scripts/query.ps1` before global grep.
4. Docs: `docs/ARCHITECTURE.md`, `docs/DEV_OPS.md`, `dev/TECH_REQUIREMENTS.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md`.
5. Every agent iteration: at task start, material scope change, phase boundary, and before final response, query `docs/DOCUMENT_REGISTRY.jsonl` through `scripts/document_registry/query.ps1` by product area and change trigger, then read the returned records. State affected records and reasons for unchanged matches. Run `validate.ps1` and `generate.ps1 -Check` when a registered document, page, or registry record changes.

## 5. Skill Routing (Load `.github/prompts/*.prompt.md`)
- `/quick`: tiny fix (design, typo, 1 string), skip spec/build.
- `/skill-fix`: fast bug/UI fix, skip doc/git/build/dev-log.
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
- Concurrent-agent locks (CLAUDE.md Rule 23): every script invoking `gradlew`/`gradlew.bat` acquires `temp/BUILD.LOCK` via `scripts/utils/agent-lock.ps1` and refuses a second concurrent build (staleness judged by PID liveness, not a timeout). Before a multi-file source edit, acquire `temp/CODE.LOCK` (`scripts/utils/enter-code-lock.ps1 -Reason "..."`) and check `scripts/utils/lock-status.ps1 -Name Build` first; `post-change.ps1` auto-releases `CODE.LOCK`. `CODE.LOCK` is advisory only (no hook enforcement) - a build warns on it, never refuses.
- Detekt-clean-first: author touched `.kt` to pass detekt on the first build - log/probe lines `<=120` chars, no bare numeric literals (`TimeUnit`/companion `const`/reuse a const), never `@Suppress` a method with a baselined finding (shifts baseline). CLAUDE.md Rule 19.
- PowerShell: Always `-NoProfile`. Batch: `& { cmd1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; cmd2 }`. Use literal `$LASTEXITCODE`. Use project wrappers.
- PowerShell exit codes (S1070): under `$ErrorActionPreference = 'Stop'` a bare `Write-Error` throws and the `exit N` after it never runs, so the code collapses to 1 while the message still prints. Use `Write-Error $msg -ErrorAction Continue` before `exit N` (N != 1); a header must list the codes actually returned. Gate: `scripts/quality/assert-exit-contract.ps1`.
- Cost discipline: follow `docs/AGENT_COST_PLAYBOOK.md` - inline over subagent for single-fact lookups (<=3 tool calls), `/compact` at task boundaries, `/clear` on task switch, offload raw artifacts to `temp/Sxxxx/` (or `temp/scratch/`), restrict `mobile-mcp` to exploratory UI walks (`adb.ps1`/Maestro first).
- Subagent MCP isolation: When defining a subagent, always set `enable_mcp_tools` to `false` unless the subagent strictly requires driving the UI/emulator (exploratory walkthroughs). This prevents duplicate Node/MCP server instances.

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
