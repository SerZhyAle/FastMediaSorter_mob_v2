# CLAUDE.md

Guidance for Claude Code in this repo. Load-bearing rules only - detailed
references live in `dev/` and `docs/`.

## Communication

- **Language**: RUSSIAN in chat, ENGLISH in code/docs/logs/commits.
- **Tone**: professional, dry, concise. Ask if ambiguous - do not guess paths or values.

## Author Style (all user-facing text, docs, UI strings)

- Ellipsis: `..` (two dots), never `...`.
- Always use `ё`/`Ё` in Russian where grammatically correct (e.g. `всё`, `ещё`, `приём`).

Non-negotiable - not typos.

## Caveman Mode (optional)

- Trigger only on explicit user request: `caveman`, `less tokens`, `be brief`, `ultra-terse`, `/caveman`.
- Keep Russian in chat. Keep code/docs/logs/commits in English.
- Drop filler, pleasantries, and hedging. Keep technical terms exact. Default level: `full`. `lite` keeps full sentences. `ultra` compresses harder.
- Repo workflow and safety rules override brevity. Mandatory skill routing still applies.
- Do not use caveman compression for security warnings, destructive/irreversible confirmations, or multi-step sequences where compression creates ambiguity.
- `stop caveman` or `normal mode` disables it.

## Spec Writing Style

Applied by all `/spec*` skills when writing `.md` artefacts. Reader is a senior developer - convey the idea, not the explanation of the idea.

- **Lists over tables.** Use `- item` for requirements, steps, decisions. Tables only where data has 3+ parallel columns (Modules table, Flavors table, stack pins).
- **No pseudographics.** No ASCII arrows, boxes, or flow diagrams in spec text.
- **No self-evident links.** Skip "ViewModel observes Repository", "§5 feeds §6" - reader knows Clean/MVVM.
- **One idea per bullet.** No elaboration paragraphs inside list items. If it needs WHY, it belongs in ADR, not in the list.
- **No section summaries.** Don't close sections with "this ensures X" or "together these achieve Y".

## Debug Verification Tags (code specs)

Invariant: a `Timber.d("Sxxxx: ...")` tag exists in `.kt` code **if and only if** spec `Sxxxx` is currently in status `BlockNeedUserTest`. The tag lifecycle is bound to that status - nothing else.

```kotlin
Timber.d("Sxxxx: <short description of exercised path>")
```

- **On transition INTO `BlockNeedUserTest`** (by `/spec`, `/spec-tech`, `/spec-dev`, `/spec-all`, or a manual `update.ps1 -Status BlockNeedUserTest`): insert one tag at the entry point of each changed flow - not on every modified line. The skill that moves the ticket into the status owns the insertion.
- During on-device testing: the tag appearing in logcat proves the code path was exercised → the spec may leave `BlockNeedUserTest` (normally `→ Verified` via `/spec-check`).
- **On transition OUT of `BlockNeedUserTest`** (to `Verified` via `/spec-check`; back to `Tactical`/`Approved`/`Draft`/`In Progress` via `/spec-update`; to `Implemented` on `/spec-all` resume; to any other `Block*`; to `Archived`; or a manual `update.ps1 -Status …`): grep for `Timber.d("Sxxxx:` across all `.kt` files and delete every matching line. The skill that moves the ticket out of the status owns the removal; commit the removal together with the status change. A manual status change must be paired with the same grep-and-delete.
- A tag whose `Sxxxx` is **not** currently `BlockNeedUserTest` is stale. Any `/spec-fix`, `/spec-check`, or `/spec-arc` run that notices one removes it.
- Never remove a tag while its spec is still `BlockNeedUserTest` - the tag is the operator's logcat probe for that round of device testing. Removal is a side effect of the status leaving `BlockNeedUserTest`, never a standalone "cleanup".
- Tags are never present in `Verified`, `Implemented`, `Partial`, `Broken`, `Block*` other than `BlockNeedUserTest`, or `Archived` code.
- Ticket ids inside log text are reserved for these temporary verification probes only. Any `Timber.i(...)`, `Timber.w(...)`, `Timber.e(...)`, or long-lived `Timber.d(...)` line that remains after the task is done must describe the subject in plain English and must **not** embed `Sxxxx`, otherwise grep-by-ticket stops being a reliable "spec still open" signal.

## Mandatory Skills (auto-trigger, do not handle manually)

| Situation | Skill |
|-----------|-------|
| **Очень незначительная** правка (коррекция дизайна, опечатка, цвет/отступ, одна строка ресурса) | `/quick` (без спеки/доков/билда; только `dev/CHANGELOG.md`) |
| Точечный фикс бага / UI-неполадки в существующем коде без спеки, журналов, доков, билда и git | `/skill-fix` (anti-bureaucracy: только диагноз → правка → отчёт; всё остальное на пользователе) |
| Creating or updating any `PLAN/Sxxxx_*.md` strategic spec | `/spec` |
| Full spec pipeline from idea to verified implementation, unattended | `/spec-all` |
| Breaking an approved strategic spec into a tactical phase plan | `/spec-tech` |
| Reviewing and refining an existing spec file (strategic or tactical) | `/spec-update` |
| Executing a tactical spec step by step, implementing phases | `/spec-dev` |
| Auditing a spec against the actual codebase state | `/spec-check` |
| Applying mechanical fixes after a spec-check audit | `/spec-fix` |
| Archiving a spec - move `PLAN/Sxxxx_*` files to `temp/done/` and mark the journal record `Archived` | `/spec-arc` |
| End-to-end on-device verification of a spec (build → install → drive UI → harvest logcat → patch spec's Manual block) | `/spec-test-device` |
| Batch device-test sweep over all `BlockNeedUserTest` tickets to drain the manual-verification backlog (operationalizes S0307) | `/spec-sweep` |
| Lightweight on-device smoke ("does it launch / click / not crash?") without touching specs, journal, or dev log | `/verify` (pre-flight via `scripts/devtest/device-ready.ps1`; outputs land in `temp/verify_*` only) |
| Locating a Kotlin class or feature before grepping, planning a refactor/decomposition, auditing who injects a type, or refreshing class-catalog metadata | `/catalog` (query `dev/CATALOG/scripts/query.ps1` before global grep; run `scripts/catalog_sync.ps1` after every `.kt` change) |
| UI/UX change touching layout, menus, visibility, orientation, empty/error states, overflow | `/ui-clarify` (blocks impl until ambiguities resolved) |
| Editing `docs/FEATURES*.md` or other feature docs | `/doc-update` (EN/RU/UK mirrors; `noLegal`-only features → `docs/FEATURES_noLegal*.md`, never public files) |
| Analysing `logs/current.log` or logcat | `/log-reader` |
| Build questions or triggering a build | `/build` (do NOT invoke gradle directly) |
| Git questions (commit/stage/push/diff/history) | `/git` |
| User asks for caveman mode, fewer tokens, ultra-terse replies, or `/caveman` | `/caveman` |
| User asks for a terse commit message, caveman commit message, or `/caveman-commit` | `/caveman-commit` |
| User asks for terse code review comments, caveman review, or `/caveman-review` | `/caveman-review` |

## Spec Catalog (Sxxxx tickets)

Each specification carries a stable ticket id `Sxxxx` (four digits, zero-padded). The id never changes, never gets reused, and is the canonical reference token in chat / commits / `dev/CHANGELOG.md`.

- **Token rule:** any reference of the form `S\d{4}` is a ticket id. Resolve via:
  `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`
- **Filenames:** every spec artefact is `PLAN/Sxxxx_<slug>.md` (no `_spec_` segment - the id already identifies the artefact). Tactical folder: `PLAN/Sxxxx_<slug>/`. **No audit / fix files are written** - `/spec-check` and `/spec-fix` record findings inside the ticket file's `## Last Audit` block (overwritten on each run) and in the journal `updated` timestamp.
- **Journal:** `PLAN/spec-catalog.jsonl` is the source of truth. Schema: `scripts/spec_catalog/SCHEMA.md`.
- **Required fields:** `id`, `name`, `status`, `priority` (0..100), `file`, `created`, `updated`. Optional: `tier`.
- **Priority guide:** 90..100 build/release blocker · 70..89 critical · 40..69 standard (default 50) · 10..39 polish · 0..9 wishlist.
- **Statuses:** active - `Draft`, `Approved`, `Tactical`, `In Progress`, `Implemented`, `Verified`, `Partial`, `Broken`. Block - `BlockByOtherTask`, `BlockNeedUserTest`, `BlockQuestions`, `BlockExternal`. Terminal - `Archived` (soft delete; ids never reused).
- **Stale signal:** `a.ps1 ss` flags any active spec with `updated` ≥ 14 days (`!`) or ≥ 30 days (`!!`); consider `/spec-update <Sxxxx>`.
- **CLI - primitives:** `insert.ps1`, `update.ps1`, `select.ps1`, `delete.ps1`, `validate.ps1` under `scripts/spec_catalog/`. **Never edit `PLAN/spec-catalog.jsonl` by hand.**
- **CLI - operator facade:** `next-id.ps1`, `search.ps1`, `close.ps1`, `stats.ps1`, `bulk-update.ps1`, `complete.ps1`, `archive.ps1` - prefer these for id allocation, lookup, finalization, summary, batch changes, one-shot completion, and archiving (move to `temp/done/` + set Archived).
- **Lifecycle hooks:** `/spec` calls `insert`; `/spec-tech` flips status to `Tactical`; `/spec-dev` flips to `In Progress` then `Implemented`; `/spec-check` flips to `Verified` / `Partial` / `Broken` (writes summary into ticket's `## Last Audit`); `/spec-fix` touches `updated`. Block-states are set explicitly via `update.ps1 -Status Block...`.
- **Soft delete only:** `delete.ps1` sets status `Archived`; record stays in the journal forever.

## Research Order (before changes)

1. `dev/PROJECT_OPERATIONS_INDEX.md` - workspace routing + **Feature-to-Path Map** (use before any global search).
2. For any `Sxxxx`-tagged question - run `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` first to get current status / file path; do not infer from filename alone.
3. **`dev/CATALOG/<module>.md`, `.jsonl`, or `query.ps1` - choose the lookup mode by question type.**
   - **Semantic lookup**: use `query.ps1` first (or direct `.jsonl` reads for narrow semantic checks) for questions like "who injects this type", "what touches disk/network/Room", "which class owns this feature", "which classes exceed N LOC", or "what is this class's role/status".
   - **Exact-match lookup**: use `rg` or direct `.jsonl` reads for a known class/file/token/resource-key/log-tag/string-literal name. Default broad-search excludes: `-g '!temp/' -g '!DOWNLOADS/' -g '!.venv/' -g '!logs/' -g '!.kotlin/' -g '!**/node_modules/'`.
   - Locating a `.kt` file by semantic ownership? → `-PathMatches` or `-Role`. Locating a known class by exact name? → `rg` or direct `.jsonl` read. Who injects a type? → `-Injected <Type>`.
   - **Do not use `find`/`Glob`/blind grep as a substitute for the catalogue when the task is semantic lookup or broad Kotlin class discovery.**
4. Domain doc per task type:
   - Architecture → `docs/ARCHITECTURE.md`
   - Build/flags → `docs/DEV_OPS.md` + `app_v2/build.gradle.kts`
   - Dependencies → `docs/TECH_STACK.md` + `dev/TECH_REQUIREMENTS.md`
   - Network → `dev/NETWORK_SPECS.md`
   - Device profile presets / first-run onboarding → `dev/DEVICE_PROFILE_PRESET_MATRIX.md`
   - Flavor-specific work (`vr`, `noLegal`, `lite`, `photos`, `legacy`) → `dev/FLAVOR_DEVELOPMENT_RULES.md` (MANDATORY before any edit targeting a non-`standard` flavor)
5. Implementation files.

**Multi-step tasks**: read `dev/AGENT_WORKFLOW.md` BEFORE execution (mandatory 5-step process).

## Proactive Research & Parallelism

### Web Search (default ON)
- Use `WebSearch` and `WebFetch` freely for Android API behaviour, library docs, Kotlin patterns, open bugs, changelogs, best practices - no permission needed.
- Preferred sources: developer.android.com, kotlinlang.org, GitHub issue/release pages, library CHANGELOGs, Stack Overflow.
- When a local approach is ambiguous, search before guessing - never rely on stale training data for version-specific behaviour (e.g. Room v6 migrations, Media3 API surface, Hilt qualifier rules).
- Adapt any external solution to this project's stack (Clean+MVVM, Hilt, Timber, Room v6, ExoPlayer Media3) before proposing.

### Parallel Sub-Agents
- Two independent tasks → single message, two `Agent()` calls running concurrently.
- Patterns that **must** parallelise: research + build validation · multiple module lookups · spec draft + catalog query · changelog entry + test run · web search + local grep.
- A data dependency (agent B needs agent A's output) is the only valid reason to serialise - document it.
- Brief each sub-agent fully - it has zero conversation context; terse prompts produce shallow work.
- Foreground for research agents whose output shapes the next step; background for validation/changelog when you can continue with other work.

### Initiative
- Do not stop to ask permission for: web searches, sub-agent spawns, debug builds (`.\a.ps1 bd`), catalog queries, dry-run script executions.
- When multiple approaches exist, rank by fit for this project's architecture; state the concrete trade-off, not just the names.
- If a better alternative to what was asked is visible, name it first: _"You asked for X - Y is cleaner here because Z. Proceeding with Y unless corrected."_
- Surface blockers at the **start** of the task: missing class, undefined interface, unresolved spec decision → flag before writing any code.
- Note adjacent debt spotted during a task (stale Timber tags, missing landscape layout, lint warning, tech-debt guard) as a one-bullet suggestion - no pressure to act immediately.

## PowerShell Efficiency (mandatory for skills and agents)

Every `pwsh` invocation in the `Bash`/`PowerShell` tool is a **fresh process** - shell state, imported modules, variables, and the prompt cache of the OS do not persist between calls. PowerShell 7 cold start on Windows is 200..500 ms per invocation. Multiply by 100+ calls per turn and the overhead dominates the actual work. These rules cut that cost.

### Rule A - Always `-NoProfile`

Every project script invocation uses `pwsh -NoProfile -File <path>` (or `pwsh -NoProfile -Command "..."`). Profile loading and module auto-import add ~200 ms with **no benefit** for our scripts - none of them depend on the user's `$PROFILE`.

Wrong: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`
Right: `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`

Exception: only when the script explicitly documents a dependency on a profile-loaded module (none currently do).

### Rule B - Batch related calls into one process

Two or more PowerShell scripts that always run together must be chained inside one PowerShell process, not split across separate tool calls.

**Harness-safe default (when the shell is already PowerShell):**
```powershell
& { ./dev/CATALOG/scripts/scan.ps1 -Module app_v2; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./dev/CATALOG/scripts/render.ps1 -Module app_v2 }
```

**Fresh-process variant (only when you truly need a new `pwsh` process, e.g. from another shell):**
```powershell
pwsh -NoProfile -Command '& { ./dev/CATALOG/scripts/scan.ps1 -Module app_v2; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./dev/CATALOG/scripts/render.ps1 -Module app_v2 }'
```

**Important quoting rule:** in runnable commands, write `$LASTEXITCODE` literally. Do **not** write `\$LASTEXITCODE` in an actual shell command, and avoid double-quoted `-Command "..."` wrappers around code that contains `$...` variables - outer wrappers may interpolate or strip the dollar sign before PowerShell receives it.

Wrong (two tool calls, two cold starts):
```
pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
pwsh -NoProfile -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

Right (one tool call, one cold start, same PowerShell process):
```
& { ./dev/CATALOG/scripts/scan.ps1 -Module app_v2; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./dev/CATALOG/scripts/render.ps1 -Module app_v2 }
```

Or, when a wrapper exists, use it (see Rule C).

### Rule C - Use the project wrapper for hot ritual chains

| Ritual | Wrapper | Replaces |
|--------|---------|----------|
| Catalogue sync after `.kt` change | `scripts/catalog_sync.ps1 -Module <app_v2\|wear>` | `scan.ps1` + `render.ps1` |

Always prefer the wrapper. If a frequently-chained ritual lacks a wrapper, **create one** in `scripts/` (single-purpose, `-NoProfile`-safe, fail-fast on `$LASTEXITCODE`) and add a row to the table above. Internal script ownership rule (Strict Rules §14) applies: fix or extend project tooling rather than work around it.

### Rule D - Independent commands in the same tool call

When two operations are truly independent (no data dependency, no shared error path), use `;` not `&&`. Compound chains in PowerShell 7 are fine, but keep them short - readability beats squeezing 5 unrelated commands into one line.

### Rule E - Don't reach for a long-running shell

PowerShell-as-REPL via background processes and named pipes is **not** supported in this harness as a first-class option. Do not invent ad-hoc daemon patterns. If the overhead is still painful after Rules A..C are applied, raise it as a tooling task (MCP-server proposal) instead of working around it locally.

## Modules

| Module | Root | Purpose |
|--------|------|---------|
| `app_v2/` | `app_v2/src/main/java/com/sza/fastmediasorter/` | Main Android app |
| `wear/` | `wear/src/main/java/com/sza/fastmediasorter/wear/` | Wear OS companion |

**Architecture**: Clean + MVVM. Flow: `UI → ViewModel → UseCase → Repository → DataSource`.
Layers: `ui/` (zero business logic - delegate to `ui/<feature>/helpers/*Manager.kt`), `domain/`, `data/`, `di/`, `core/`, `utils/`, `worker/`, `widget/`.

## Product Flavors

| Flavor | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM |
|--------|:-----:|:-----:|:------:|:-----:|:----:|:----:|
| `standard` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `lite` | ✓ | - | ✓ | - | - | - |
| `photos` | - | - | ✓ | - | - | ✓ |
| `legacy` | ✓ | ✓ | ✓ | - | - | ✓ |

Gated via `BuildConfig` fields in `app_v2/build.gradle.kts`.

## Tech Stack Pins

Kotlin 1.9+ / Java 17 / `compileSdk 35` / `minSdk 26` (standard), `minSdk 23` (legacy).
Hilt · Room v6 (bump version + migration on every schema change) · ExoPlayer Media3 1.2.1 · Glide 4.15.1 · SMBJ/SSHJ/Apache Commons Net · Google Drive/MSAL/Dropbox SDKs.
**Logging: Timber only** - `Log.d()` is prohibited. Persistent operational logs must not embed `Sxxxx` ticket ids; ticket ids in log text are reserved for `BlockNeedUserTest` probes only.

## Strict Rules

1. No writes to project root - use `temp/` for logs, artifacts, backups.
2. File size limit 1500 LOC - extract to `helpers/*Manager.kt`.
3. Activity logic prohibited - delegate to Manager/Helper classes.
4. Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
5. Backup rule: file >500 LOC → timestamped backup in `temp/` before edit.
6. Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.
7. Lint: resolve warnings in files you touch.
8. Ignore `*.backup` files unless user asks for historical comparison.
9. Before editing, read existing inline comments/KDoc in the affected area - treat as requirements.
10. When changing logic, add WHY-comments only when not obvious; remove stale comments.
11. UI ambiguity gate: see `/ui-clarify` - implementation blocked until all placement/visibility/fallback decisions are explicit.
12. Layout orientation: editing any `res/layout/*.xml` → ALWAYS check `res/layout-land/*.xml` counterpart. If it exists, apply the equivalent change in the same step. If it should exist but doesn't, create it or add a blocker. **Never silently leave portrait-only edits in a layout that has a landscape counterpart.**
13. Spec ticket discipline: never edit `PLAN/spec-catalog.jsonl` directly; never rename a spec file out of its `Sxxxx_` prefix; never re-introduce a `_spec_` segment in PLAN paths; new specs must allocate an id via `scripts/spec_catalog/insert.ps1` **before** the strategic `.md` is written to disk.
14. Internal script ownership: do not work around broken or insufficient repo scripts when the current task depends on them. If a project script is buggy, outdated, or can be materially improved to complete the task safely, fix the script itself and then use it.
15. **Flavor isolation:** writing `BuildConfig.IS_NO_LEGAL_FLAVOR`, `BuildConfig.SUPPORT_VR_PLAYER`, `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED`, or any other `BuildConfig.SUPPORT_*` / `BuildConfig.ENABLE_*` / `BuildConfig.IS_*` flavor guard inside `src/main/java/**` is **forbidden** for new code. Flavor-specific logic lives in `src/<flavor>/java/` (`vr`, `noLegal`, `lite`, `photos`, `legacy`). Pattern: define an interface in `src/main/java/`, ship a No-Op default impl in `src/main/java/` (or `src/standard/java/`), override with the real impl in the target flavor source set, bind via a flavor-specific Hilt `@Module`. Layout/string overrides go to `src/<flavor>/res/`, manifest additions to `src/<flavor>/AndroidManifest.xml`. Source of truth: `dev/FLAVOR_DEVELOPMENT_RULES.md` - read it before any task that targets a non-`standard` flavor or mentions VR / noLegal / lite / photos / legacy capabilities. Existing legacy gates (≈169 occurrences in `src/main/` as of 2026-05-14) are technical debt, not a precedent - never add new ones; refactor incrementally when touching surrounding code.
16. **Non-trivial step evidence:** a step that modifies any executable artifact (`.kt`, `.kts`, `.py`, `.ps1`, `.xml`, `.json` build config) cannot be marked done on narration alone. The step log must include the validation command run and its exit code or explicit PASS/FAIL result.
17. **UI consistency & input coverage:** every new button, menu item, action, dialog, Activity, or Fragment must (a) follow the project's established visual design system (colors, typography, spacing, icon style, corner radii - match surrounding screens); (b) support all three input modes: **keyboard** (`nextFocusDown`/`Up`/`Left`/`Right`, `Enter`/`Space` to activate), **D-pad / TV remote** (focus traversal, `onKey` where needed), **mouse** (hover state, click); (c) be reachable by focus traversal in the same order as analogous controls elsewhere. Verify in layout XML: `focusable="true"`, `clickable="true"`, `nextFocus*` attributes set or logical focus chain exists. Any new screen must pass the same `/ui-clarify` gate as edits to existing screens.
18. **System bar safety:** every new or changed screen, Activity-backed dialog, fullscreen overlay, toolbar, bottom action bar, and empty/loading/error state must keep important text and touch targets inside `WindowInsetsCompat.Type.systemBars()` plus `displayCutout()` safe bounds in portrait and landscape. `android:fitsSystemWindows="true"` alone is not enough for targetSdk 35 / Android 15 edge-to-edge enforcement. Prefer `View.applySystemBarInsetPadding()` for ordinary View-based forms; use equivalent Compose `WindowInsets` padding for Compose surfaces. Immersive/player/game exceptions must explicitly own their insets and document why system bars may overlay non-critical background only.

## Feature Inventory

`docs/FEATURES.md` is canonical (21 feature areas). Read before implementing anything to avoid duplication. Mirrors: `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**noLegal exception:** `noLegal`-only capabilities are NOT listed in `docs/FEATURES*.md`. They are tracked in `docs/FEATURES_noLegal.md` + `_RU` + `_UK` (gitignored, local only - S0156 §6.9). Never add `noLegal` entries to public feature files; never add public features to the `_noLegal` files.

## UI Communication Policy

`docs/COMMUNICATION_POLICY.md` is the canonical source for tone, message formulas, and feedback-channel routing. Mirrors: `docs/COMMUNICATION_POLICY_RU.md`, `docs/COMMUNICATION_POLICY_UK.md`. Origin: S0118.

- **Read before writing or modifying any user-visible string** - applies to toasts, dialogs, empty states, errors, progress, confirmations, and next-step CTAs.
- **Tone checklist** (§6 of the policy) must pass before any string batch is committed.
- **Exceptions:** legal texts, Terms of Service, machine-readable artifacts - keep formal neutral style, do not apply friendly rewrite.
- **Deviations** from the policy are allowed only for the exempted categories above; any other deviation must be justified and noted in the spec or commit message.

## Validation Requirements

Every step closes with the minimum validation that is actually discriminating for the change type. Grep and text checks are structural preflight only - they do not close a non-trivial step alone.

- Failed build-log inspection: do not use `tail -N` or `Select-Object -Last N` on build logs - the real `FAILURE:` block may sit in the middle. Use `a.ps1 bf`.

| Change type | Preflight | Required closure |
|-------------|-----------|-----------------|
| Doc-only (`.md`, `docs/**`, `PLAN/*.md`) | - | Grep for expected content |
| Script (`.ps1`, `.sh`) | - | Dry-run or manual execution, exit 0 |
| Config (`.kts`, `.gradle`, `strings.xml`, `*.json` build config) | Grep | Target variant build passes |
| Kotlin / Java (`.kt`, `.java`) | Catalog sync | Target module compiles + affected unit tests pass |
| Python (`.py`) | - | Syntax check + unit test or targeted import exercise |
| Layout / manifest (`.xml`) | Lint structure | Target variant build passes |
| Mixed (code + doc) | - | Highest applicable level from above |

**Surrogate builds** (e.g. `standardDebug` when the change is in `noLegalDebug`) are acceptable only when explicitly documented as equivalent for the affected change. Otherwise use the target variant.

**Expected vs actual:** every structural check must record the expected value and the actual value explicitly - `expected: X | actual: Y`. A mismatch is a hard failure, not a soft warning. "Verified" or "checked" without a concrete value pair is not a valid closure.

**Shell convention:** repo automation scripts run under **PowerShell** (`pwsh`). Mixing shells inside a mandatory ritual step is not the default and requires an explicit justification. Ad-hoc Bash commands for one-off inspection are fine.

## Post-Change Steps (mandatory, all agents)

**Fail-closed:** mechanical closure after a change goes through one command. If it returns non-zero, stop and treat that as a blocker.

Run:
`pwsh -NoProfile -File scripts/post-change.ps1 -File "<path>" -Target "<target>" -Description "<english description>" -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed> [-Module <app_v2|wear>] [-KeyPrefix "<key_prefix>"]`

`ChangeType` routes the mechanical post-change steps:

- `Doc`, `Script`, `Config` - dev log only.
- `Kotlin` - dev log + `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>`.
- `Xml` - dev log + `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key_prefix>"` when string keys changed.
- `Mixed` - dev log + both applicable checks above.
- Omit `-ChangeType` only for backward compatibility with older callers; new calls must pass it explicitly.

Skill-owned decisions stay outside `post-change.ps1`:

- **Feature docs** only when a genuinely new user-visible capability is introduced - update `docs/FEATURES.md` + `_RU` + `_UK` with a concise bullet. **Skip for:** code improvements, refactors, bug fixes, UX polish, performance, internal architecture, or anything invisible to an end user as a new feature. **Exception:** `noLegal`-only new features go into `docs/FEATURES_noLegal.md` + `_RU` + `_UK` (gitignored) - never into the public files.
- **Functionality log** - when a task completes a user-visible behaviour change, append one line via `\.\scripts\add_to_functionality_log.ps1 -Id Sxxxx -Op <ADD|CHANGE|DELETE|FIX> -Description "<english summary>"` (omit `-Id` for entries without a spec ticket). Skills `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`, `/spec-all`, `/quick`, `/skill-fix-release` invoke this automatically - call the CLI manually only when no skill is in flight.
- **Spec catalog sync** - on every spec status transition run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id Sxxxx -Status <new>` (also `-Priority N` when the urgency changes) unless the active skill already owns it. Direct edits to `PLAN/spec-catalog.jsonl` are forbidden.

Notes:

- `post-change.ps1` covers only the mechanical closure steps. Build, test, and compile gates still follow the `Validation Requirements` table above.
- `/skill-fix` is the explicit fast-patch exception: skip dev log, functionality log, feature docs, spec/catalog writes, full builds, and git inspection; keep only focused local validation and core safety rules.
- `add_to_dev_log.ps1` already records the current branch - no separate branch step is needed.
- `dev/CATALOG/<module>.jsonl` and `<module>.md` are local gitignored indexes. Regenerate them via `scripts/catalog_sync.ps1`; do not expect or require a git commit for them.

## Git Branching Model

- `main` - release-stable only. Release builds are assembled exclusively from `main`.
- Direct push of development changes to `main` is **prohibited**.
- `main` accepts only: merges from a `DEBUG-v00N` branch after plateau verification, and **fix-release commits** (see below). All other direct pushes to `main` are prohibited.
- Development branches: `DEBUG-v001`, `DEBUG-v002`, … - sequential numbering, no gaps, leading zeros (three digits).
- Target: keep at most **2 live** DEBUG branches at a time - current (next-release candidate) + optional "future".
- "Future" branch: created only on explicit owner request for work not intended for the upcoming release. Born from the current DEBUG branch, not from `main`.
- When current DEBUG merges into `main`, the "future" branch (if any) becomes the new "current" - no re-branching required.
- New standard DEBUG branch is always created from a fresh `main` after the previous one merges.
- **Fix-release** - a published release to `standard`/VR flavors that contains **only fixes for previously working features**. No new behavior, no new UI, no new functionality. This is the only legitimate reason for a direct commit to `main` outside a DEBUG merge cycle. Fix-release flow: commit(s) directly to `main` → tag with new version → publish → **rebase all live DEBUG branches onto updated `main`**. `WHATS_NEW.md` updated with a "Fix Release" subsection. The "no new behavior" constraint is enforced by the author.
- **Release worktree:** `P:/ANDROID/FastMediaSorter_release` is a permanent `git worktree` checked out to `main`. All release builds (`.\a.ps1 r`, `.\a.ps1 vr`) are run from there - the development directory (`FastMediaSorter_mob_v2`) is never switched to `main` for a build. After a fix-release is committed to `main`, pull it into the worktree: `cd ../FastMediaSorter_release && git pull`, then rebase DEBUG branches.
- Before starting any task: confirm which branch the session is on (`git branch --show-current`). Tooling works on any branch; release builds require `main`.

## Version Format

`Y.YM.MDDH.Hmm` (e.g. `2.60.1102.207` = 2026/01/10 20:07). History: `dev/CHANGELOG.md`.
