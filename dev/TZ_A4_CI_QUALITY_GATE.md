# ТЗ A4: CI Quality Gate

## Статус: 📋 Запланировано
## Приоритет: 🔴 Критический
## Зависимости: A2 (Contract Tests), A3 (Maestro)

---

## Описание проблемы

В текущем CI отсутствует жёсткий quality gate: merge возможен даже при падении lint, unit-тестов или UI-тестов. Это пропускает регрессии в `main`.

## Цель

Настроить CI pipeline с обязательными проверками, блокирующими merge при любом падении обязательного шага.

---

## Требования

### Обязательные шаги pipeline

| Шаг | Блокирует merge |
|-----|-----------------|
| Android Lint | ✅ Да |
| Unit Tests | ✅ Да |
| Contract Tests | ✅ Да |
| Maestro Smoke UI | ✅ Да |
| Сборка всех flavor'ов | ✅ Да |
| Code Coverage Report | ⚠️ Warning |

### Настройки

1. **Lint**
   - `lintOptions.abortOnError = true`
   - baseline file для известных issues
   - запрет новых warning без suppression

2. **Unit/Contract Tests**
   - запуск `testStandardDebugUnitTest` и contract suite
   - fail-fast при падении обязательных тестов

3. **Smoke UI (Maestro)**
   - минимальный набор critical flows
   - запуск на эмуляторе в CI
   - скриншоты/логи при падении как артефакты

4. **Flavor Builds**
   - `assembleStandardDebug`
   - `assembleStandardRelease` (проверка ProGuard/R8)

5. **Branch Protection Rules**
   - required checks обязательны для merge в `main`
   - минимум 1 approve на PR
   - запрет force-push в `main`

---

## Task Backlog (уровень постановки)

### Pipeline
- [ ] A4-T1: Актуализировать workflow с последовательностью `lint -> unit -> contract -> maestro -> build`.
- [ ] A4-T2: Включить кэширование Gradle и стабильные cache keys.
- [ ] A4-T3: Настроить передачу секретов/переменных среды для тестов.

### Lint/Test Hardening
- [ ] A4-T4: Включить `abortOnError = true` и политику baseline.
- [ ] A4-T5: Подключить `testStandardDebugUnitTest` + contract suite в required checks.
- [ ] A4-T6: Подключить Maestro smoke subset как required check.

### Branch Governance
- [ ] A4-T7: Настроить required status checks для `main`.
- [ ] A4-T8: Настроить ограничение force-push и minimum approvals.
- [ ] A4-T9: Настроить auto-cancel устаревших CI прогонов.

### Diagnostics
- [ ] A4-T10: Публиковать артефакты падений (test reports, screenshots, logs).
- [ ] A4-T11: Добавить уведомления о падениях обязательных проверок.

## Артефакты

- CI workflow конфигурация.
- Политика required checks в репозитории.
- Набор CI артефактов диагностики.

---

## Критерии приёмки

- [ ] PR не может быть влит при падении любого обязательного шага.
- [ ] При падении формируется чёткий отчёт и артефакты диагностики.
- [ ] Кэш Gradle работает стабильно и ускоряет повторные прогоны.

## Проверка полноты

- [ ] В `main` невозможно merge без прохождения всех обязательных шагов.
- [ ] Любое падение даёт диагностический артефакт, достаточный для triage.
