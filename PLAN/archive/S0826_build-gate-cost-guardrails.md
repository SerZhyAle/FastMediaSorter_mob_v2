# Тактическая спецификация: S0826 - Механические build/gate guardrail'ы для дешёвых dev-циклов

**Ticket:** S0826
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-30
**Tier:** 3 - Moderate (ad-hoc)
**Parent:** S0816 (agent-session-cost-optimization)

> Child тикет S0816: playbook задал *политику* (spawn/context/MCP/skill tiers), здесь - *механические* guardrail'ы, снижающие wall-clock и трение dev-цикла. Закрывает топ-риск S0816 §7 («оптимизация сведётся к советам без механических guardrail'ов»).

---

## Goal

Снизить главные источники трения dev-цикла, замеренные в реальной сессии 2026-06-30 (3 тикета S0822/S0821/S0823): сборки ≈40% wall-clock, project-wide gates падают на чужом WIP (грязное дерево всегда), повторные detekt-итерации из-за устранимых находок. Каждая фаза независима и проверяема. Без понижения качества gate'ов - режимы opt-in, полный project-wide gate остаётся дефолтом для release.

---

## Контекст / эвиденс

- `post-change.ps1` гоняет project-wide `assert-detekt`/`assert-listener-symmetry`/`assert-neuroslop` - на «working tree is truth» дереве они падают на WIP других тикетов, поэтому фасад не используется как closure-шаг (закрывал руками 3×).
- `a.ps1 fk` есть только для standard; для noLegal compile-check приходится гонять полный `testNoLegalDebugUnitTest` (52s-1m29s). Per-variant `compile<Flavor>DebugKotlin` напрямую с CLI не вызывается (AGP task-addressability quirk).
- detekt находил устранимые вещи в свежем коде (лог-строка >120, magic numbers) - 3 повторных прогона на S0822.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0816 (parent, playbook), S0825 (skill model: tiers).
- **Sensitive scope:** правит общий dev-тулинг (`post-change.ps1`, `assert-*.ps1`, `a.ps1`) - Rule 13 (чинить скрипты правильно, не обходить).

---

## Phases

### Phase 01 - Diff-scoped gate mode (A, наибольший leverage)

- Добавить в `assert-detekt.ps1`, `assert-listener-symmetry.ps1`, `assert-neuroslop.ps1` опциональный параметр `-ChangedFiles <paths>` (или `-ScopeToFiles`), сужающий проверку только до переданных файлов; без параметра поведение не меняется (project-wide по умолчанию).
- Прокинуть его из `post-change.ps1` (`-File` уже известен) в дочерние gate-вызовы, чтобы фасад проверял только файлы изменения.
- detekt: парсить отчёт и фильтровать findings по `-ChangedFiles` вместо общего exit-кода (baseline-сравнение остаётся, но fail только если новая находка в *этих* файлах).
- **Verification:** на текущем грязном дереве `post-change.ps1 -File <чистый .kt> -ChangedFiles <он же>` завершается exit 0, тогда как без флага падает на чужом WIP. Release-путь (без флага) по-прежнему project-wide.

### Phase 02 - Fast per-flavor compile wrapper (B)

- Добавить `a.ps1 fkn` (noLegal fast Kotlin compile) по образцу `fk`/`check-standard-fast.ps1`; вызывать invocable-прокси (assemble/test-точку, тянущую `compileNoLegalDebugKotlin`), т.к. compile-задача напрямую не адресуется.
- Опционально `fkl`/`fkp`/`fkg` (lite/photos/legacy) если дёшево.
- **Verification:** `a.ps1 fkn` компилирует noLegal быстрее полного `testNoLegalDebugUnitTest` и ловит ошибку в намеренно битом noLegal-файле (exit != 0).

### Phase 03 - Batch gate invocation (E)

- Обёртка/режим, запускающий neuroslop + deprecated-pm + listener-symmetry (+ detekt опционально) в одном pwsh-процессе с агрегированным результатом, вместо N отдельных запусков.
- **Verification:** один вызов отрабатывает все быстрые Kotlin-gate'ы и печатает per-gate PASS/FAIL + единый exit-код.

### Phase 04 - detekt-clean-first checklist в код-ген скилах (C)

- Добавить в `/spec-dev` и `/spec-all` (и кратким пунктом в `CLAUDE.md` рядом с Rule 19) пред-сборочный self-check touched-строк: лог-вызовы ≤120 символов; нет голых числовых литералов (`TimeUnit`/companion `const`/reuse существующей константы); не вешать `@Suppress` на baselined-метод (сдвигает baseline-сигнатуру).
- **Verification:** grep подтверждает наличие чеклиста в обоих скилах + ссылку в CLAUDE.md.

### Phase 05 - Compact-bugfix scaffolding в /spec-draft (D)

- Для явного однофайлового бага из лог-анализа `/spec-draft` скаффолдит компактную форму (проблема + root cause + fix + verification) вместо полного стратегического скелета §1-§9.
- **Verification:** `/spec-draft` с флагом/эвристикой «явный баг» создаёт компактный спек; полный скелет остаётся дефолтом для стратегических идей.

---

## Риски

- Diff-scoped режим может скрыть регрессию в незатронутом файле -> митигация: opt-in, release/CI гоняет полный project-wide gate; флаг только для per-change dev-closure.
- Новые `a.ps1` verb'ы расходятся с реальными именами gradle-задач -> митигация: Phase 02 verification ловит битый файл, т.е. прокси реально компилирует.

---

## Критерии готовности

1. `post-change.ps1` применим как closure-шаг на грязном дереве (diff-scoped), не падая на чужом WIP.
2. Есть быстрый per-flavor compile-check минимум для noLegal.
3. Быстрые Kotlin-gate'ы запускаются одним процессом.
4. detekt-clean-first чеклист зафиксирован в код-ген скилах.
5. `/spec-draft` умеет компактную форму для явных багов.

---

## Implementation (2026-06-30)

- **Phase 01** - `assert-detekt.ps1` получил `-ChangedFiles`: после провала detekt пересуживает по Checkstyle XML-репорту (`<module>/build/reports/detekt/detekt.xml` - надёжный task-output, не stdout, который пропадает при build-cache hit) и PASS, если ни одна новая находка не в changed-файлах. `post-change.ps1` получил `-ScopeToFile`: detekt diff-scoped по `-File`, а project-wide count-ratchet gate'ы (neuroslop/listener-symmetry/flavor-flag/deprecated-pm) через новый `Invoke-AdvisoryStep` становятся advisory (warn, не валят фасад). **Дизайн-выбор:** count-baseline gate'ы сделаны advisory, а не per-file diff-scoped - чище и реально закрывает criterion #1 (detekt был связывающим блокером; остальные растут на чужом WIP). Targeted gate'ы (ticket-log, dialog-cancel и т.д.) остаются fatal.
- **Phase 02** - `check-standard-fast.ps1` параметризован `-Flavor`; noLegal держит Chaquopy включённым и пропускает `--configuration-cache` (Chaquopy не сериализуется в config-cache). `a.ps1 fkn`.
- **Phase 03** - `scripts/quality/assert-fast-gates.ps1` гоняет 5 быстрых gate'ов одним процессом + агрегированный exit; detekt opt-in `-IncludeDetekt`. `a.ps1 fg`.
- **Phase 04** - detekt-clean-first чеклист в `.claude/commands/spec-dev.md` + `spec-all.md` + CLAUDE.md (Rule 19); `fkn`/`fg` в §9; `-ScopeToFile` в §12.
- **Phase 05** - `spec-draft.md`: выбор Compact bugfix template (проблема/root cause/фикс/проверка + §3.3) для bug-intent (`bugfix-`/`hotfix-` slug) вместо полного §1-§12.

**Verification:** fkn compiles noLegal (exit 0); fg runs all 5 + aggregates; assert-detekt `-ChangedFiles` PASS на чистом / FAIL на BrowseFileTransferWorker; `post-change -ScopeToFile` exit 0 end-to-end на грязном дереве (detekt PASS [scoped], listener advisory SKIP); все скрипты AST-валидны; чеклисты/шаблон присутствуют (grep). Чисто dev-тулинг - device-test не нужен.

**Deferred (follow-up, не блокер):**

- Mirror-sync (done 2026-06-30): `fkn`/`fg`, `post-change -ScopeToFile` и detekt-clean-first проброшены в `AGENTS.md`, `.github/copilot-instructions.md`, `.github/prompts/spec-dev.prompt.md`, `.github/prompts/spec-all.prompt.md` per «shared rules» правилу.
- Полный per-file diff-scope для neuroslop-детей/listener (вместо advisory) - только если advisory-режим окажется недостаточным.

---

## Last Audit

**Date:** 2026-06-30 | **Verdict:** Verified | **Mode:** inline (tooling/docs; criteria verified by direct script tests)

Все 5 критериев выполнены и проверены прямыми прогонами (см. Verification). Criterion #1 доказан end-to-end: `post-change -ScopeToFile` завершается exit 0 на грязном дереве, где project-wide detekt падает. Реализация трогает только dev-тулинг/скилы/CLAUDE.md - no app code, no build/device-test/Timber tags/ALL_FEATURES.
