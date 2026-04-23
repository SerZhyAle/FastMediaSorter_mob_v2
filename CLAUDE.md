# CLAUDE.md

Guidance for Claude Code in this repo. Load-bearing rules only — detailed
references live in `dev/` and `docs/`.

## Communication

- **Language**: RUSSIAN in chat, ENGLISH in code/docs/logs/commits.
- **Tone**: professional, dry, concise. Ask if ambiguous — do not guess paths or values.

## Author Style (all user-facing text, docs, UI strings)

- Ellipsis: `..` (two dots), never `...`.
- Always use `ё`/`Ё` in Russian where grammatically correct (e.g. `всё`, `ещё`, `приём`).

Non-negotiable — not typos.

## Mandatory Skills (auto-trigger, do not handle manually)

| Situation | Skill |
|-----------|-------|
| Creating/updating `PLAN/spec_*.md` | `/spec` |
| UI/UX change touching layout, menus, visibility, orientation, empty/error states, overflow | `/ui-clarify` (blocks impl until ambiguities resolved) |
| Editing `docs/FEATURES*.md` or other feature docs | `/doc-update` (EN/RU/UK mirrors) |
| Analysing `logs/current.log` or logcat | `/log-reader` |
| Build questions or triggering a build | `/build` (do NOT invoke gradle directly) |
| Git questions (commit/stage/push/diff/history) | `/git` |
| "Where does X happen?" / auditing code / planning a refactor / adding a class | `/catalog` (query first, update after) |

## Research Order (before changes)

1. `dev/PROJECT_OPERATIONS_INDEX.md` — workspace routing + **Feature-to-Path Map** (use before any global search).
2. `dev/CATALOG/<module>.md` (or `query.ps1`) — class-level catalogue with role, status, side effects, DI graph. Query before `Grep`.
3. Domain doc per task type:
   - Architecture → `docs/ARCHITECTURE.md`
   - Build/flags → `docs/DEV_OPS.md` + `app_v2/build.gradle.kts`
   - Dependencies → `docs/TECH_STACK.md` + `dev/TECH_REQUIREMENTS.md`
   - Network → `dev/NETWORK_SPECS.md`
4. Implementation files.

**Multi-step tasks**: read `dev/AGENT_WORKFLOW.md` BEFORE execution (mandatory 5-step process).

## Modules

| Module | Root | Purpose |
|--------|------|---------|
| `app_v2/` | `app_v2/src/main/java/com/sza/fastmediasorter/` | Main Android app |
| `wear/` | `wear/src/main/java/com/sza/fastmediasorter/wear/` | Wear OS companion |

**Architecture**: Clean + MVVM. Flow: `UI → ViewModel → UseCase → Repository → DataSource`.
Layers: `ui/` (zero business logic — delegate to `ui/<feature>/helpers/*Manager.kt`), `domain/`, `data/`, `di/`, `core/`, `utils/`, `worker/`, `widget/`.

## Product Flavors

| Flavor | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM |
|--------|:-----:|:-----:|:------:|:-----:|:----:|:----:|
| `standard` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `lite` | ✓ | — | ✓ | — | — | — |
| `photos` | — | — | ✓ | — | — | ✓ |
| `legacy` | ✓ | ✓ | ✓ | — | — | ✓ |

Gated via `BuildConfig` fields in `app_v2/build.gradle.kts`.

## Tech Stack Pins

Kotlin 1.9+ / Java 17 / `compileSdk 35` / `minSdk 26` (standard), `minSdk 23` (legacy).
Hilt · Room v6 (bump version + migration on every schema change) · ExoPlayer Media3 1.2.1 · Glide 4.15.1 · SMBJ/SSHJ/Apache Commons Net · Google Drive/MSAL/Dropbox SDKs.
**Logging: Timber only** — `Log.d()` is prohibited.

## Strict Rules

1. No writes to project root — use `temp/` for logs, artifacts, backups.
2. File size limit 1000 LOC — extract to `helpers/*Manager.kt`.
3. Activity logic prohibited — delegate to Manager/Helper classes.
4. Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
5. Backup rule: file >500 LOC → timestamped backup in `temp/` before edit.
6. Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.
7. Lint: resolve warnings in files you touch.
8. Ignore `*.backup` files unless user asks for historical comparison.
9. Before editing, read existing inline comments/KDoc in the affected area — treat as requirements.
10. When changing logic, add WHY-comments only when not obvious; remove stale comments.
11. UI ambiguity gate: see `/ui-clarify` — implementation blocked until all placement/visibility/fallback decisions are explicit.

## Feature Inventory

`docs/FEATURES.md` is canonical (21 feature areas). Read before implementing anything to avoid duplication. Mirrors: `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

## Post-Change Steps (mandatory, all agents)

1. **Dev Changelog** after every code/config change — run
   `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"`
   (never edit `dev/CHANGELOG.md` directly).
2. **Feature docs** after any new user-facing feature — update `docs/FEATURES.md` + `_RU` + `_UK` with a concise bullet.
3. **Catalogue sync** after any change to a file's public API (added/removed/renamed classes or functions, changed constructor injection, moved between layers):
   - `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>` — refreshes auto-fields; manual fields are preserved.
   - For new classes, fill `role` + `status` via `set.ps1` (see `dev/CATALOG/README.md`).
   - Commit updated `dev/CATALOG/<module>.jsonl` + `<module>.md` together with the code change.

## Version Format

`Y.YM.MDDH.Hmm` (e.g. `2.60.1102.207` = 2026/01/10 20:07). History: `dev/CHANGELOG.md`.
