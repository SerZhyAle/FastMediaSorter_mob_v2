# Тактический план: Категория A — Топ-улучшения (ROI)

## Охват

Документ детализирует выполнение инициатив `A1`, `A2`, `A3`, `A4`, `A5` на уровне задач для постановки в backlog.

---

## Порядок реализации

1. `A4` CI Quality Gate (создать каркас контроля качества поставки).
2. `A1` Multi-account (функциональная цель с максимальным риском регрессий).
3. `A3` Maestro регрессии (покрыть критические пользовательские сценарии).
4. `A2` Контрактные тесты ResourceEditor (закрепить доменный контракт формы).
5. `A5` Оптимизация сканирования (перфоманс-слой после стабилизации базового качества).

---

## Task Backlog (готово к постановке)

| Task ID | Инициатива | Задача | Входы | Выходы |
|---------|------------|--------|-------|--------|
| A4-T1 | A4 | Создать/обновить CI workflow (lint, unit, contract, maestro, build) | Текущее CI | Workflow YAML |
| A4-T2 | A4 | Включить жёсткие правила lint (`abortOnError`, baseline policy) | Gradle config | Стабильный lint gate |
| A4-T3 | A4 | Настроить branch protection и required checks | Репозиторий | Защищённый merge в main |
| A4-T4 | A4 | Подключить артефакты падений (логи/скриншоты) | CI runner | Диагностические артефакты |
| A1-T1 | A1 | Расширить data layer для multi-account credentials | Room schema | Миграция + DAO |
| A1-T2 | A1 | Реализовать auth-варианты для выбора/повторной авторизации аккаунта | Auth managers/providers | Корректный OAuth flow |
| A1-T3 | A1 | Реализовать account picker и индикацию аккаунта в UI | UI слой | Выбор аккаунта при создании ресурса |
| A1-T4 | A1 | Добавить миграцию single-account → multi-account + тесты миграции | Legacy state | Обратная совместимость |
| A3-T1 | A3 | Подготовить Maestro инфраструктуру и helper flows | `maestro/` | Базовый раннер и общие flow |
| A3-T2 | A3 | Реализовать core UI flows (add/auth/scan/edit) | UI сценарии | Набор core regression tests |
| A3-T3 | A3 | Реализовать edge-case flows (cancel/retry/empty/copy/welcome) | UI сценарии | Набор edge regression tests |
| A3-T4 | A3 | Интегрировать Maestro в CI как блокирующий check | CI workflow | Merge-blocking UI gate |
| A2-T1 | A2 | Создать test infra для ResourceEditor contract tests | Test framework | Base contract test suite |
| A2-T2 | A2 | Покрыть field schema по всем типам ресурсов | Domain schema | Набор schema-тестов |
| A2-T3 | A2 | Покрыть валидации и коллизии имен | Validation rules | Validation-тесты |
| A2-T4 | A2 | Покрыть CRUD + collapse-state сценарии | Editor state | CRUD/UX contract tests |
| A5-T1 | A5 | Внедрить delta scan (added/modified/deleted detection) | Scanner cache | Incremental scanning |
| A5-T2 | A5 | Добавить metadata cache (entity/dao/invalidation) | Room | Ускоренное повторное чтение |
| A5-T3 | A5 | Внедрить лимиты параллелизма по типам источников | Scanner execution | Контролируемая нагрузка |
| A5-T4 | A5 | Добавить benchmark/integration verification | Test media set | Подтверждённый прирост производительности |

---

## Контроль полноты

- [ ] По каждой инициативе есть минимум один кодовый артефакт и один тестовый артефакт.
- [ ] Все `A*-T*` задачи имеют owner, status, ссылку на PR.
- [ ] Для `A1/A3/A4` подключены блокирующие проверки в CI.
- [ ] Для `A5` зафиксирован baseline и результат после оптимизации.

---

## Риски и митигации

| Риск | Затрагивает | Митигация |
|------|-------------|-----------|
| OAuth тестирование без реальных аккаунтов | A1, A3 | Mock OAuth, sandbox credentials |
| Flaky UI тесты | A3, A4 | Retry policy + детерминированный emulator profile |
| Нерепрезентативный benchmark scan | A5 | Фиксированный набор `test_media` и единый сценарий измерений |
