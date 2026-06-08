# Skill Fix

> **GLOBAL EXECUTION DIRECTIVES (FAST PATCH MODE):**
> 1. **PATCH FIRST:** Find the nearest owning code path, form one local hypothesis, then make the smallest plausible fix.
> 2. **NO BUREAUCRACY:** Do not create specs, update docs, write dev/functionality logs, run `post-change.ps1`, inspect git history/status, or build the project.
> 3. **NARROW VALIDATION ONLY:** After the first substantive edit, run the cheapest focused check available for the touched slice. No full builds.
> 4. **TERSE REPORTING:** End with a short technical summary of the changed files and the local validation result.

Быстрый путь для исправления **существующей** ошибки, регрессии, краша или локальной UI/logic-проблемы, когда задачу можно закрыть узким патчем и идти дальше. Подходит для кода и UI. Не подходит для новой функциональности и широких архитектурных изменений.

## Usage

```
/skill-fix <короткое описание проблемы> [--verify-device]
```

Examples:
- `/skill-fix кнопка Delete в details screen иногда не реагирует на первый клик`
- `/skill-fix crash при открытии папки без превью`
- `/skill-fix в landscape плавает toolbar title на экране browse`
- `/skill-fix sorting toggle не сохраняет состояние после возврата из settings`
- `/skill-fix crash при открытии папки без превью --verify-device` (после патча прогнать минимальный smoke на устройстве)

Флаг `--verify-device` выключен по умолчанию - философия fast-patch не меняется. Включай для crash-/UI-defect фиксов, где локальная валидация (юнит, get_errors) не доказывает, что баг закрыт.

---

## When NOT to use

`/skill-fix` не подходит для:

- Новой фичи, нового пользовательского сценария, новой кнопки/экрана/навигации.
- Рефакторинга шире одного узкого defect-slice, архитектурной перестройки, переноса кода «на будущее».
- Room schema/migration, `build.gradle`, manifest/flavor topology, release pipeline, git workflow.
- Неясных UI/UX решений (placement, visibility, overflow, confirmation, empty/error states) - это `/ui-clarify`.
- Чисто косметической микроправки без поведения (опечатка, один color/padding/string) - это `/quick`.
- Любой задачи, где без спеки теряется договорённость о новом поведении.

При срабатывании любого из этих признаков - остановиться и перенаправить:
- `/quick` для совсем мелкой косметики.
- `/ui-clarify` для неясного UI.
- `/spec` или `/spec-all` для новой функциональности или широкого изменения.

---

## Process

**Step 1 - Take the nearest concrete anchor.**
- Start from the file, stack trace, log tag, screen, symbol, or failing behaviour named in `$ARGUMENTS`.
- For Kotlin class lookup, use `dev/CATALOG/scripts/query.ps1` first; do not broad-grep the repo unless the class/path is still unknown after that one hop.
- Read only enough nearby code to name one falsifiable local hypothesis and one cheap check that can disprove it.
- If the issue is still diffuse after one local routing hop, stop and say `/skill-fix не подходит - нужен более широкий разбор.`

**Step 2 - Make the smallest grounded edit.**
- Read inline comments / KDoc in the touched area first and treat them as requirements.
- If new logic genuinely needs a comment, keep it English-only and WHY-focused: only non-obvious business logic, a handled edge-case, or a workaround - never restate what the code plainly does.
- Patch the narrowest code path that directly controls the bug.
- Preserve existing architecture and style; do not opportunistically refactor adjacent code.
- If editing `res/layout/*.xml`, immediately check the `res/layout-land/*.xml` counterpart and patch it too when it exists.
- If touching user-visible Russian text, preserve author style manually: `..`, `ё`/`Ё`.

**Step 3 - Validate locally, not bureaucratically.**
- Immediately after the first substantive edit, run one focused validation step for the touched slice.
- Allowed examples: `get_errors` on touched files, a narrow unit test, a targeted feature-specific script/test, or another cheap behaviour-scoped check.
- Forbidden here: full app build, release scripts, git status/diff/history review as a substitute for validation.

**Step 4 - Iterate once if needed.**
- If the focused validation exposes a local defect in the same slice, patch it and rerun the same focused check.
- If the result shows the bug is controlled one hop away, step to that nearest owner and continue narrowly.
- If the task expands beyond a fast patch, stop and redirect to `/spec`, `/spec-all`, or `/ui-clarify`.

**Step 4a - Optional on-device verification (only when `--verify-device`).**
After the local validation in Step 3 PASSes, additionally:

1. Run pre-flight: `pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -Json`. If exit ≠ 0 → log the reason in chat and skip device verification (do not block the fix). Common cases: no device online, mobile-mcp missing, package mismatch.
2. Decide build need:
   - Kotlin/Java/manifest/build-config edit → device must run the new code. Call `/verify --build` (the underlying skill will pick the right `build-*-device.ps1` automatically).
   - Pure XML/resource edit that the running APK won't pick up without reinstall → call `/verify --build`.
   - Otherwise (debug-only assertion, log-level change, dead-code removal) → `/verify` without `--build`.
3. Read the `/verify` verdict line. PASS, zero errors → append to the closeout. FAIL or crash → name the failing step in the closeout; do not auto-rollback - the user decides.

This step is the only place `/skill-fix` ever interacts with the device. It does not write dev log, functionality log, or any spec/journal field - all of that remains intentionally skipped per Step 5.

**Step 5 - Skip bureaucracy explicitly.**
- Do **not** create or update `PLAN/` specs.
- Do **not** update `docs/FEATURES*`, other docs, `dev/CHANGELOG.md`, or `dev/FUNCTIONALITY.log`.
- Do **not** run `scripts/post-change.ps1`, `scripts/add_to_dev_log.ps1`, `scripts/add_to_functionality_log.ps1`, or spec-catalog scripts.
- Do **not** inspect git history/status/diff unless the user explicitly asks.
- Do **not** run `/build` or any full compile pipeline.

**Step 6 - Report.**
- Return a short technical closeout: what was fixed, where, and which focused local validation passed or failed.
- No plan sections, no process recap, no changelog prose.

---

## Safety floor

- Read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) remain forbidden.
- No destructive git commands.
- No new flavor-gating in `src/main/java/**`; respect `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- No broad search drift once a local hypothesis and cheap check exist.
