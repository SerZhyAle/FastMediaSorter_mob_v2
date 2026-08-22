# Стратегическая спецификация: S1732 - Проверка полноты тестов не понимает модуль без вариантов сборки

**Ticket:** S1732
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-16
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - находка 2026-08-16

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-16

**Захвачено во время:** S1554

**Текст:** текста владельца нет - находка при прогоне модульных тестов часов.

**Симптом:** `scripts/builders/check-standard-fast.ps1 -Mode Unit -Module wear` собирается и прогоняет тесты успешно, но следом печатает:

`assert-test-suite-complete: cannot read a flavor out of -TaskDir 'testDebugUnitTest' (known flavors: standard, noLegal, lite, photos, legacy, vr)`
`Suite-completeness check could not run; coverage is unverified.`

**Что установлено:**

- Модуль часов (`wear`) не объявляет `productFlavors`, поэтому его gradle-задача называется `testDebugUnitTest`, без сегмента варианта.
- Библиотека `scripts/quality/lib/flavor-source-map.ps1` считает отсутствие `productFlavors` ошибкой, а `assert-test-suite-complete.ps1` не умеет извлекать пустой/отсутствующий flavor для flavorless-модулей.
- Скрипт `check-standard-fast.ps1` вызывал `assert-test-suite-complete.ps1` без передачи `-Module $Module`, всегда подставляя значение по умолчанию (`app_v2`).

---

## 1. Проблема

Скрипт проверки полноты тестов `scripts/quality/assert-test-suite-complete.ps1` не поддерживает модули без `productFlavors` (такие как `wear`), а раннер `check-standard-fast.ps1` не передаёт текущий модуль в гейт полноты. В итоге проверка покрытия для модуля `wear` тихо пропускается с предупреждением `Suite-completeness check could not run; coverage is unverified`, оставляя полноту тестов часов непроверенной.

---

## 2. Цели

1. Поддержать модули без `productFlavors` в `scripts/quality/lib/flavor-source-map.ps1` и `scripts/quality/assert-test-suite-complete.ps1`: для таких модулей задачей является `test(Debug|Release)UnitTest`, а тестовым каталогом - `<Module>/src/test`.
2. Передавать `-Module $Module` из `check-standard-fast.ps1` в вызов `assert-test-suite-complete.ps1`.
3. Убедиться, что `assert-test-suite-complete.ps1` корректно проверяет полноту тестов для `:wear` и сохраняет обратную совместимость для `:app_v2`.

**Non-goals:**

- Не заводить у модуля часов ось вариантов сборки ради удобства проверки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Сохранять строгость проверки: если тесты пройдены, полнота должна проверяться механически, а не пропускаться.

### 3.2 Жёсткие ограничения

- **Flavor:** все (затрагивается инфраструктура тестов)
- **Wear OS:** затрагивается модуль часов (`wear`) и скрипты качества.
- **Производительность:** без оверхеда.
- **Локализация / UI:** без изменений.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1554, S1453

---

## 4. Контекст текущей архитектуры

`assert-test-suite-complete.ps1` опирается на `Get-FlavorSourceMap` для определения набора тестовых корней, входящих в конкретный вариант сборки. Для телефонного приложения (`app_v2`) этот набор формируется комбинацией общего каталога `src/test` и каталогов конкретных flavors (`src/testStandard`, `src/testLite` и т.д.). Для модуля `:wear` вариантов нет, и единственным тестовым корнем является `wear/src/test`.

---

## 5. Предлагаемый подход

1. В `scripts/quality/lib/flavor-source-map.ps1`:
   - Если блок `productFlavors` отсутствует в build-файле, возвращать пустой список `$flavorNames = @()`, пустые отображения `$flavors` и `$testSets`, не выбрасывая исключение.
   - В `Get-EffectiveTestSourceRoots` при пустом списке flavors или `$Flavor = ""` возвращать `@("$Module/src/test")`.
2. В `scripts/quality/assert-test-suite-complete.ps1`:
   - Если `$sourceMap.FlavorNames.Count -eq 0`, проверять соответствие `-TaskDir` паттерну `^test(Debug|Release)UnitTest$` и использовать flavor `""`.
3. В `scripts/builders/check-standard-fast.ps1`:
   - Передавать `-Module $Module` в вызов `assert-test-suite-complete.ps1`.

---

## 6. Открытые вопросы / Research items

1. **Что считать полным покрытием у модуля без вариантов**
   - **Вопрос:** от чего считать знаменатель, если исходных наборов у модуля один?
   - **Решение:** Все тестовые классы под `<Module>/src/test` (включая `src/test/java` и `src/test/kotlin`).
   - **Статус:** Resolved

2. **Предупреждение или отказ**
   - **Вопрос:** должна ли проверка, которая не смогла отработать, ронять быстрый прогон вместо печати строки?
   - **Решение:** Если модуль поддержан, проверка отрабатывает штатно. Если XML-отчёты отсутствуют или повреждены, возвращается exit 2 ("could not check"), а при неполном покрытии - exit 1 (FAIL).
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Регрессия на `:app_v2` с его сложной картой flavors | Низкая | Ошибки на телефонных тестах | Прогон `assert-shared-test-flavor-scope.tests/Run-Tests.ps1` |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Поддержка flavorless-модулей в карте исходников тестов**

- **Решение:** Сделать блок `productFlavors` опциональным в `Get-FlavorSourceMap`. Модули без вариантов сборки обрабатываются как имеющие единственный корневой набор `src/test`.
- **Альтернативы:** Создание отдельного скрипта проверки полноты для flavorless-модулей (дублирование логики).
- **Почему:** Единый гейт для всех модулей проекта.

---

## 10. Связи с другими спеками

- S1554 - закрытие выявило дефект.
- S1453 - введение карты flavors и проверки полноты.

---

## 11. Критерии готовности (strategic-level)

1. `Get-FlavorSourceMap -Module wear` возвращает объект карты без исключения.
2. `assert-test-suite-complete.ps1 -Module wear -TaskDir testDebugUnitTest` успешно выполняет проверку.
3. `assert-shared-test-flavor-scope.tests/Run-Tests.ps1` проходит 100% тестов.
4. `check-standard-fast.ps1 -Mode Unit -Module wear` выполняет проверку полноты без предупреждения `could not run`.

---

## Last Audit

- **Date**: 2026-08-17
- **Verdict**: Verified ✅
- **Summary**: `flavor-source-map.ps1` and `assert-test-suite-complete.ps1` updated to support flavorless modules like `:wear`. `check-standard-fast.ps1` updated to pass `-Module $Module`. Added regression test cases D3 and D4 in `Run-Tests.ps1` (19/19 passed).
- **Findings**: None (P0-P3: 0).
