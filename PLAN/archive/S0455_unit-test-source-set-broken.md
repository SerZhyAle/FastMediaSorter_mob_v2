# Стратегическая спецификация: S0455 - Unit-test source set не компилируется (mediaCapabilities)

**Ticket:** S0455
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-16
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - parked by /spec-dev during S0452 (2026-06-16)

> **Scope:** STRATEGIC. Сырой захват. Доработать через `/spec`.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-16 (parked by /spec-dev S0452)

**Симптом:**

`./gradlew testStandardDebugUnitTest` падает на компиляции тест-сорсета `compileStandardDebugUnitTestKotlin` - запустить НИ ОДИН unit-тест нельзя, пока не починить. Не связано с S0452 (production-код собирается, `assembleStandardDebug` зелёный).

**Доказательства (compile errors):**

```
ProvisionDefaultResourcesUseCaseTest.kt:40:101 No value passed for parameter 'mediaCapabilities'.
ResolveOpenInFmsTargetUseCaseTest.kt:37:9 No value passed for parameter 'mediaCapabilities'.
ScanLocalFoldersUseCaseTest.kt:30:9 No value passed for parameter 'mediaCapabilities'.
CommandPanelLayoutPlannerTest.kt:34:44 No value passed for parameter 'mediaCapabilities'.
```

**Гипотеза:** в конструктор/функцию (UseCase + CommandPanelLayoutPlanner) добавлен обязательный параметр `mediaCapabilities`, но 4 тест-файла не обновлены - тест-сорсет не компилируется.

**Область:** `app_v2/src/test/java/.../domain/usecase/`, `.../ui/player/helpers/CommandPanelLayoutPlannerTest.kt`.

**Вложений нет.**

---

## 1. Проблема

См. §0. Тест-сорсет не компилируется → блокирует прогон любых unit-тестов (включая верификацию новых тикетов).

---

## 11. Критерии готовности

1. `./gradlew testStandardDebugUnitTest` компилирует тест-сорсет без ошибок `mediaCapabilities`.
2. 4 затронутых теста передают корректный `mediaCapabilities` (фейк/стаб по образцу production-вызова).
