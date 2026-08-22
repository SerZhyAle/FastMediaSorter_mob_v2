# Стратегическая спецификация: S1283 - Исправление structural-проверки lifecycle-safe Flow collection

**Ticket:** S1283
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-14
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - quality-gate defect discovered during S1195 lint triage

---

## 1. Проблема

Быстрая проверка unsafe Flow collection считает блок `lifecycleScope.launch` безопасным, если в нём встречается текст `repeatOnLifecycle` или `flowWithLifecycle`. Она не проверяет, что конкретный вызов `collect` находится внутри lifecycle-aware блока. Поэтому проверка может сообщать зелёный результат для того же нарушения, которое Android lint классифицирует как `RepeatOnLifecycleWrongUsage`.

Такой результат скрывает lifecycle-unsafe collection: поток продолжает доставлять значения после остановки владельца и может удерживать его в памяти. Проверка должна различать безопасный collect, действительно вложенный в lifecycle-aware boundary, и collect, который лишь находится рядом с таким вызовом.

## 2. Цели

1. Проверка unsafe collection структурно определяет, что каждый collect находится внутри корректного lifecycle-aware блока.
2. Наличие lifecycle-aware вызова рядом с небезопасным collect больше не исключает нарушение.
3. Regression-проверки покрывают корректно вложенный collect и ранее пропускаемый ошибочный случай.
4. Существующий baseline продолжает отражать только реальные нарушения, без изменения его порога ради прохождения gate.

**Non-goals:**

- Не исправлять все текущие lint findings, принадлежащие S1195.
- Не изменять остальные lexical quality gates без отдельного доказательства дефекта.
- Не менять функциональность приложения или пользовательские тексты.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление использует существующий brace-aware подход, а не широкий регулярный шаблон по всему файлу.
2. Gate сохраняет быстрый одно-проходный анализ и его текущий contract baseline.

### 3.2 Жёсткие ограничения

- **Scope:** только shared matcher unsafe collection и его изолированные regression fixtures/tests.
- **Совместимость:** существующие безопасные формы collection не должны стать новыми ложными срабатываниями.
- **Производительность:** анализ остаётся линейным по содержимому проверяемого файла и не запускает Gradle.
- **Локализация:** новые пользовательские строки не требуются.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1195, S1280, S1191, S1193.

## 4. Контекст текущей архитектуры

Все lexical source rules определены в общем matcher-наборе и запускаются umbrella gate одним обходом исходного дерева. Unsafe-collect rule уже выполняет brace scan внешнего `lifecycleScope.launch`, но его исключение оценивает весь body как текст. Это создаёт ложное отсутствие нарушения, когда lifecycle-aware вызов относится к другой вложенной ветви.

## 5. Предлагаемый подход

Проверка будет сопоставлять каждый collect в launch-body с его enclosing lambda boundary. Collect считается безопасным только если он структурно расположен внутри `repeatOnLifecycle` или lifecycle-aware flow chain, а не потому что соответствующее имя встречается в соседнем коде. Regression coverage закрепит обе стороны boundary и защитит matcher от возврата к текстовому исключению.

### 5.1 Основные столпы / модули

#### Structural matcher

Общий matcher определяет вложенность collect относительно lifecycle-aware boundaries.

#### Regression coverage

Изолированные fixtures доказывают, что broken shape учитывается, а valid nested shape остаётся исключённой.

### 5.2 Потоки данных и событий

Исходный Kotlin текст -> shared matcher -> unsafe-collect count -> baseline comparison -> gate verdict.

### 5.3 Точки расширяемости

Структурная проверка остаётся в едином matcher-наборе, поэтому wrapper и umbrella gate используют одинаковое определение нарушения.

## 6. Открытые вопросы / Research items

Нет. Исходный defect и нужная structural boundary подтверждены текущим matcher-кодом и lint classification.

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|---|:---:|---|---|
| Matcher неверно распознает вложенную lambda | Низкая | Ложное срабатывание | Добавить valid nested fixture. |
| Исправление скроет иной unsafe shape | Низкая | Gate останется неполным | Добавить fixture для соседнего lifecycle-aware вызова. |
| Анализ станет дорогим | Низкая | Замедление fast gates | Сохранить локальный brace scan без второго project walk. |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

## 9. Архитектурные решения (ADR)

**ADR-1: Безопасность collect определяется вложенностью, а не присутствием имени API**

- **Решение:** Проверять enclosing lifecycle-aware boundary для конкретного collect.
- **Альтернативы:** Исключать весь launch-body по текстовому совпадению или отключить fast gate.
- **Почему:** Только structural relation отличает корректную lifecycle boundary от lint-нарушения рядом с ней.

## 10. Связи с другими спеками

- S1195 владеет общим lint triage и не расширяется этим исправлением.
- S1280, S1191 и S1193 описывают аналогичный класс quality gates с необоснованно зелёным verdict.

## 11. Критерии готовности (strategic-level)

1. Соседний `repeatOnLifecycle` или `flowWithLifecycle` больше не скрывает unsafe collect.
2. Collect, вложенный в корректную lifecycle-aware boundary, не считается нарушением.
3. Focused regression tests и unsafe-collect gate проходят с неизменённым baseline contract.

## Last Audit

**Date:** 2026-08-14
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- EXEMPT: internal quality-gate repair has no device or user-visible acceptance step.
