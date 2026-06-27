# FastMediaSorter v2 Agent Protocol

## 1. Source of Truth
- Rules: `CLAUDE.md`, `.github/copilot-instructions.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`.
- Stricter rules override. Import order: `CLAUDE.md` -> `.github/copilot-instructions.md` -> prompt/agent file.

## 2. Communication
- Chat: RU. Code, docs, logs, commits, changelog: EN. Dry, concise.
- Ellipsis / Dash / Ё (documentation prose & user-visible UI text ONLY): `..` (never `...`), plain hyphen `-` (never em-dash `—`, en-dash `–`, or horizontal bar `―`), Russian Ё/ё where grammatically correct. Never enforce these typography rules in code, technical/tactical specs, commands, logs, or chat.
- Timestamps: Always accompany replies with a timestamp (HH:mm:ss based on the current local time provided in prompt metadata).

## 3. Core Rules
- Stack: Android, Kotlin 1.9+, Java 17, Hilt, Room, Media3, Timber.
- Directories: `app_v2/`, `wear/`, `dev/`, `docs/`, `scripts/`, `temp/` (scratch/logs). Read-only: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- No Activity logic (delegate to `helpers/*Manager.kt`).
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

## 5. Skill Routing (Load `.github/prompts/*.prompt.md`)
- `/quick`: tiny fix (design, typo, 1 string), skip spec/build.
- `/skill-fix`: fast bug/UI fix, skip doc/git/build/dev-log.
- `/spec*`: spec lifecycle (`/spec`, `/spec-all`, `/spec-tech`, `/spec-update`, `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`, `/spec-test-device`, `/spec-sweep`).
- `/ui-clarify`: resolve UI ambiguity before coding.
- `/catalog`: class/feature queries, sync catalog.
- `/doc-update`: docs sync.
- `/log-reader`: logcat/log analysis.
- `/build`, `/git`, `/caveman`, `/caveman-commit`, `/caveman-review`.

## 6. Workflow & PowerShell
- Confirm branch before edit (`git branch --show-current`).
- Multi-step: follow `dev/AGENT_WORKFLOW.md` (5 steps).
- After changes (except `/skill-fix`): run `.\scripts\add_to_dev_log.ps1`.
- Kotlin changes: sync catalog via `scripts/catalog_sync.ps1 -Module <app_v2|wear>`.
- PowerShell: Always `-NoProfile`. Batch: `& { cmd1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; cmd2 }`. Use literal `$LASTEXITCODE`. Use project wrappers.

## 7. Validation
- Follow `CLAUDE.md` validation ladder. Record `expected: X | actual: Y`.
- Prefer the cheapest proof that matches the change: `.\a.ps1 fk` for Kotlin symbol edits, `.\a.ps1 fr` for resources/manifests, `.\a.ps1 fc` for mixed small changes, and full debug APK builds only when packaging/install behavior matters.

## 8. Code Audit Protocol
- Full protocol: `docs/CODE_AUDIT_PROTOCOL.md`. Apply it on any **audit trigger**: new screen/manager/worker/repository/long-lived helper; lifecycle/coroutine/Flow/listener/observer change; shared-state/dispatcher change; Room entity/DAO/query/migration/transaction change; player/image/cache/network change; startup change; DI scope/singleton change; build/manifest/R8/keep-rule change affecting profiling/startup/minification; reported crash/ANR/OOM/jank/leak.
- Tag every finding by severity P0-P3 (P0 crash/leak/data-loss blocks release; P1 race / main-thread I/O / unbounded cache / unreleased resource; P2 hot-path churn / over-eager startup; P3 readability/style). P0/P1 need evidence, not opinion.
- Listener symmetry: every `register*`/`addListener`/`addObserver` has a matching removal on the symmetric lifecycle edge.
- No main-thread Room (`allowMainThreadQueries()` banned); DAO `suspend`/`Flow`; atomic multi-step writes in `@Transaction`/`withTransaction`.
- One owner per `ExoPlayer` with a full release contract (`release()` + `setVideoSurface(null)` + remove listeners + abandon focus); mirror across player hosts. Glide decode-at-size + `clear(target)` on detach.
- Reflection/serialization/DI/manifest/dependency changes are proven on the minified release/target variant, not only debug (extends dead-weight rule).
