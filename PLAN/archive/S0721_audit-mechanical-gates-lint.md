# Стратегическая спецификация: S0721 - Механические гейты аудита: симметрия слушателей, custom Lint, LeakCanary в тестах

**Ticket:** S0721
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-26
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - дочерний тикет S0714 (принятие Code Audit Protocol)
**Umbrella:** S0714

> **Scope:** STRATEGIC. Цели и объём механизации. Конкретные правила Lint/паттерны гейта - на этапе `/spec-tech`.

---

## 0. Источник

Layer 8 (Mechanical gates), Recommended next additions #4-#5 и блок CI dynamic analysis протокола `docs/CODE_AUDIT_PROTOCOL.md`. Превращение повторяемых ревью-замечаний в принуждение.

## 1. Проблема

Три повторяемых класса проблем сейчас ловятся только глазами:

1. Несбалансированные `register/unregister` и `addListener/removeListener` (источник утечек, см. S0715) - чисто механически проверяемы grep-симметрией, гейта нет.
2. Структурные архитектурные правила (нет бизнес-логики в Activity, нет UI-Context в синглтоне, нет lifecycle-небезопасного сбора, нет неосвобождённого player/listener, нет main-thread Room) лучше видеть прямо в IDE - нет модуля custom Android Lint.
3. Утечки в инструментальных тестах не ловятся автоматически - LeakCanary только в debug-рантайме, не в тестах.

## 2. Цели

1. **`assert-listener-symmetry.ps1`** - гейт баланса `register*`/`unregister*` и `add*Listener`/`remove*Listener`, по образцу `scripts/quality/assert-*.ps1` с ratchet-baseline, подключённый в `post-change.ps1`.
2. **Модуль custom Android Lint** с архитектурными правилами протокола (Activity-логика, UI-Context в долгоживущем, lifecycle-небезопасный Flow, неосвобождённый player/listener, main-thread disk/Room вне обёрток).
3. **LeakCanary в инструментальных тестах** через `leakcanary-android-instrumentation` - автоматическая ловля утечек на инструментальных прогонах.

**Non-goals:** detekt/ktlint (S0720); Macrobenchmark (S0722); сам аудит-проход памяти (S0715).

## 3. Объём и ограничения

- Модули `app_v2/` и `wear/`.
- Гейт симметрии - ratchet-baseline, не блокировка с нуля.
- Custom Lint - отдельный Gradle-модуль; правила покрываются тестами lint.
- Форма прогона LeakCanary-тестов (локально/CI) согласуется с открытым вопросом зонтика по CI.

## 4. Критерии приёмки

- [x] `assert-listener-symmetry.ps1` детектит дисбаланс, имеет baseline, подключён в `post-change.ps1`; зелёная сборка остаётся зелёной.
- [x] Модуль custom Lint собирается, правила покрыты тестами, предупреждения видны в сборке/IDE.
- [x] LeakCanary интегрирован в инструментальные тесты; утечка в тесте валит прогон.
- [x] Способ запуска описан в dev-доках; записи инструментов - в `docs/ALL_FEATURES.jsonl`.

## 5. Связанные тикеты

- S0714 (зонтик).
- S0715 (владение памятью - гейт симметрии механизирует его проверку).
- S0720 (detekt/ktlint - комплементарный статанализ).
- S0723 (кодификация - правила Lint отражены в CLAUDE.md).

## Last Audit

**2026-06-28 - listener symmetry adoption slice completed.**

- `scripts/quality/assert-listener-symmetry.ps1` already exists and keeps a frozen ratchet baseline in `scripts/quality/listener-symmetry-baseline.txt` (`133` at audit time).
- Local enforcement path is now wired: `scripts/post-change.ps1` runs the gate for `Kotlin` and `Mixed` changes.
- Operator docs are updated in `docs/DEV_OPS.md`; the tooling inventory now records the gate in `docs/ALL_FEATURES.jsonl`.
- Acceptance criteria status: criterion 1 done, criterion 4 done. Remaining open work for this ticket is unchanged: custom Android Lint module + LeakCanary instrumentation test integration.

**2026-06-28 - Custom Lint module and LeakCanary instrumentation test integration completed.**

- Custom Android Lint module `:lint-rules` is fully configured and integrated with both `:app_v2` and `:wear` modules. All 5 custom detectors (`ActivityLogicDetector`, `UiContextLeakDetector`, `UnsafeFlowCollectDetector`, `PlayerReleaseDetector`, `MainThreadIoDetector`) are registered and their tests in `CustomLintRulesTest` pass.
- Android Lint passes successfully with no new issues on standard flavor. The baseline `app_v2/lint-baseline.xml` has been updated to cover recent dependency permission warnings.
- LeakCanary is integrated into instrumentation tests via `leakcanary-android-instrumentation`. Renamed backticked test methods containing spaces in `GracefulDegradationTest` and `MediaMuxerRemuxerInstrumentationTest` to standard camelCase names to resolve DEX compilation errors with class/method names.
- Verified `LeakDetectionInstrumentationTest` runs and completes successfully on the connected emulator.
- All acceptance criteria are now checked and completed. Status updated to Implemented.
