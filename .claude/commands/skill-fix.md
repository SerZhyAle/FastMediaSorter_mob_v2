# Skill Fix

> **GLOBAL DIRECTIVES (FAST PATCH MODE):**
> 1. Patch first - find nearest owning code path, form one local hypothesis, make smallest plausible fix.
> 2. No bureaucracy - no specs, doc updates, dev/functionality logs, `post-change.ps1`, git history/status inspection, or project build.
> 3. Narrow validation only - after first substantive edit, run cheapest focused check for touched slice. No full builds.
> 4. Terse reporting - end with short technical summary of changed files + local validation result.

Быстрый путь для исправления **существующей** ошибки, регрессии, краша или локальной UI/logic-проблемы, закрываемой узким патчем. Код и UI. Не для новой функциональности и широких архитектурных изменений.

## Usage

```
/skill-fix <короткое описание проблемы> [--verify-device]
```

Examples:
- `/skill-fix кнопка Delete в details screen иногда не реагирует на первый клик`
- `/skill-fix crash при открытии папки без превью`
- `/skill-fix в landscape плавает toolbar title на экране browse`
- `/skill-fix sorting toggle не сохраняет состояние после возврата из settings`
- `/skill-fix crash при открытии папки без превью --verify-device` (минимальный smoke на устройстве)

`--verify-device` off by default - философия fast-patch не меняется. Включай для crash-/UI-defect фиксов, где локальная валидация (юнит, get_errors) не доказывает закрытие бага.

---

## When NOT to use

`/skill-fix` не подходит для:

- Новой фичи, нового сценария, новой кнопки/экрана/навигации.
- Рефакторинга шире одного defect-slice, архитектурной перестройки, переноса кода «на будущее».
- Room schema/migration, `build.gradle`, manifest/flavor topology, release pipeline, git workflow.
- Неясных UI/UX решений (placement, visibility, overflow, confirmation, empty/error states) - это `/ui-clarify`.
- Чисто косметической микроправки без поведения (опечатка, один color/padding/string) - это `/quick`.
- Любой задачи, где без спеки теряется договорённость о новом поведении.

При срабатывании - остановиться и перенаправить:
- `/quick` - совсем мелкая косметика.
- `/ui-clarify` - неясный UI.
- `/spec` или `/spec-all` - новая функциональность или широкое изменение.

---

## Process

**Step 1 - Take nearest concrete anchor.**
- Start from file, stack trace, log tag, screen, symbol, or failing behaviour in `$ARGUMENTS`.
- Kotlin class lookup → `dev/CATALOG/scripts/query.ps1` first; broad-grep only if class/path still unknown after that one hop.
- Read only enough nearby code to name one falsifiable local hypothesis + one cheap disproving check.
- Still diffuse after one routing hop → stop: `/skill-fix не подходит - нужен более широкий разбор.`

**Step 2 - Make smallest grounded edit.**
- **CODE.LOCK (CLAUDE.md Rule 23).** Before the edit, if it touches `app_v2/`/`wear/` source: `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "/skill-fix: <short desc>"`. A warning about a live `BUILD.LOCK` is informational only - editing itself is unaffected. Since Step 5 skips `post-change.ps1` (the usual auto-release point), release it explicitly in Step 6 via `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` - this is the one lock-hygiene call that is NOT bureaucracy to skip.
- Read inline comments / KDoc in touched area first; treat as requirements.
- New logic comment only if genuinely needed: English-only, WHY-focused (non-obvious business logic, handled edge-case, workaround) - never restate what code does.
- Patch narrowest code path that directly controls bug.
- Preserve existing architecture and style; no opportunistic refactor of adjacent code.
- Editing `res/layout/*.xml` → immediately check `res/layout-land/*.xml` counterpart, patch it too when it exists.
- Touching user-visible Russian text → preserve author style manually: `..`, `ё`/`Ё`.

**Step 3 - Validate locally, not bureaucratically.**
- Immediately after first substantive edit, run one focused validation step for touched slice.
- Allowed: `get_errors` on touched files, narrow unit test, targeted feature-specific script/test, another cheap behaviour-scoped check.
- Forbidden: full app build, release scripts, git status/diff/history as validation substitute.

**Step 4 - Iterate once if needed.**
- Focused validation exposes local defect in same slice → patch, rerun same check.
- Bug controlled one hop away → step to nearest owner, continue narrowly.
- Task expands beyond fast patch → stop, redirect to `/spec`, `/spec-all`, or `/ui-clarify`.

**Step 4a - Optional on-device verification (only when `--verify-device`).** After Step 3 PASSes:
1. Pre-flight: `pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -Json`. Exit ≠ 0 → log reason in chat, skip device verification (do not block fix). Common: no device online, mobile-mcp missing, package mismatch.
2. Decide build need:
   - Kotlin/Java/manifest/build-config edit → device must run new code → `/verify --build` (underlying skill picks right `build-*-device.ps1`).
   - Pure XML/resource edit running APK won't pick up without reinstall → `/verify --build`.
   - Otherwise (debug-only assertion, log-level change, dead-code removal) → `/verify` without `--build`.
3. Read `/verify` verdict line. PASS, zero errors → append to closeout. FAIL or crash → name failing step in closeout; do not auto-rollback - user decides.

Only place `/skill-fix` touches device. No dev log, feature inventory, or spec/journal field - all stay skipped per Step 5.

**Step 5 - Skip bureaucracy explicitly.**
- Do **not** create or update `PLAN/` specs.
- Do **not** update `docs/FEATURES*`, other docs, `dev/CHANGELOG.md`, or `docs/ALL_FEATURES.jsonl`.
- Do **not** run `scripts/post-change.ps1`, `scripts/add_to_dev_log.ps1`, `scripts/all_features/add.ps1`, or spec-catalog scripts.
- Do **not** inspect git history/status/diff unless user explicitly asks.
- Do **not** run `/build` or any full compile pipeline.

**Step 6 - Report.** If Step 2 acquired `CODE.LOCK`, release it first: `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1`. Then short technical closeout: what was fixed, where, which focused local validation passed/failed. No plan sections, no process recap, no changelog prose.

---

## Safety floor

- Read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) remain forbidden.
- No destructive git commands.
- No new flavor-gating in `src/main/java/**`; respect `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- No broad search drift once local hypothesis + cheap check exist.
- Neuroslop avoidance (CLAUDE.md Rule 20): even fast patch must not introduce AI-slop - no trivial restating comments, no empty/broad swallowing `catch`, no hardcoded `="#hex"` in `res/layout*`, no bare `lifecycleScope.launch { flow.collect { } }` on view-bound Flows (use `collectOnLifecycle`).
