# Спецификация (fix): S0734 - Рефакторинг читаемости: extract-helper и дедупликация (S0718)

**Ticket:** S0734
**Status:** Archived
**Priority:** 35
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0718 (Layer 1, P2/P3 рефакторинг)
**Umbrella:** S0714

> **Scope:** Четыре рефактора читаемости (нетривиальные - извлечение хелперов/дедуп). Найдено статически (S0718).

---

## 0. Источник

Четыре находки аудита S0718 (`PLAN/S0718_code-readability-audit/AUDIT_FINDINGS.md`, #2/#3/#5/#6). Не баги корректности - тангл/дублирование, обоснованные доказательствами дрейфа.

## 1. Находки и правки

1. **P2 - `data/network/SmbFileOperationHandler.kt:172` `executeMove`.** `forEachIndexed → when(3 arms) → try → if/else → if/else`, ~6 уровней; bridge-move (download→upload→delete) перемешан с bookkeeping success/partial, дублируемым в каждой ветке (уже есть само-описанный мёртвый код «Reached unreachable code» :320-327). **Fix:** извлечь ветки `when` в per-arm suspend-хелперы (`moveSmbToSmb`/`moveBridgeToSmb`/`moveLocalToSmb`), возвращающие sealed `MoveOutcome`; bookkeeping централизовать в цикле.
2. **P2 - `data/link/InvisibleWebViewExtractionStrategy.kt:411` `onPageFinished`.** Пирамида колбэков ~8 уровней (`postDelayed → runCatching → evaluateJavascript → if → runCatching → evaluateJavascript → launch → mainHandler.post`); `else`-fallback за 26 строк от `if`. **Fix:** извлечь embedded-json-блок в `private fun inspectEmbeddedJson(..)`, свернуть `onPageFinished` до if/else диспетчера.
3. **P3 - `data/link/DirectFileExtractionStrategy.kt:120` `open`.** `mime!!` безопасен только из-за нелокального инварианта `MediaMimeWhitelist.isAllowed` (отвергает null) - читателю не видно локально, хрупко. **Fix:** value-binding early-return (`val mime = ..; if (mime == null || !isAllowed(mime)) { close(); return Blocked }`), `!!` исчезает.
4. **P3 - `domain/usecase/ExecuteScheduledOperationUseCase.kt:125` `invoke`.** COPY (138-178) и MOVE (193-233) - почти идентичные ~40-строчные `when`-блоки; Success и PartialSuccess байт-идентичны; дрейф уже есть (лог 165 vs 220). DELETE-ветка уже мёржит `Success||PartialSuccess`. **Fix:** мёрж `Success||PartialSuccess` + хелпер `handleFileResult(..)`; ветки отличаются только типом FileOperation и меткой.

## 2. Критерии приёмки

- [ ] Каждая правка сохраняет поведение (то же дерево исходов move/extract/scheduled-op).
- [ ] Вложенность executeMove/onPageFinished снижена (хелперы); дублирование COPY/MOVE убрано.
- [ ] `mime!!` устранён value-binding'ом. `.\a.ps1 fc` зелёный; затронутые тесты проходят.

## 3. Связанные тикеты

- S0718 (аудит-источник), S0714 (зонтик).

---

## Last Audit

**Date:** 2026-06-27
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

Review-mode audit: all four refactors already present in working tree (code moved ahead of the stale Draft; spec line-numbers were pre-edit). Verified by static inspection:

- #1 `SmbFileOperationHandler.executeMove` - `sealed interface MoveOutcome { Success; Failure; PermissionRequired }` + per-arm suspend helpers `moveSmbToSmb`/`moveBridgeToSmb`/`moveLocalToSmb`; bookkeeping centralised in `when(outcome)` loop.
- #2 `InvisibleWebViewExtractionStrategy.onPageFinished` - embedded-json block extracted to `private fun inspectEmbeddedJson(..)`; `onPageFinished` dispatches via `shouldInspectEmbeddedJson(..)`.
- #3 `DirectFileExtractionStrategy.open` - value-binding early-return (`if (mime == null || !MediaMimeWhitelist.isAllowed(mime)) { close(); return Blocked }`); zero `mime!!` occurrences.
- #4 `ExecuteScheduledOperationUseCase.invoke` - `private fun handleFileResult(..)` (3 call sites) with merged `Success || PartialSuccess` branch.

Behaviour preserved (helpers return identical outcome trees; merged branch was byte-identical pre-merge). No user-visible change -> FEATURES EXEMPT. No debug tags (status Implemented).

### Manual / on-device

- [x] `compileStandardDebugKotlin` green (ran `a.ps1 fk` -> BUILD SUCCESSFUL, UP-TO-DATE).
- [ ] Affected unit tests (SMB move / scheduled-op) - not run in static audit; covered by existing suite, low risk (behaviour-preserving extracts).
