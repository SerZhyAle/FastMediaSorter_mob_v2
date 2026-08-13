# Стратегическая спецификация: S1050 - Устаревшая версия в S0731 schema-export guard

**Ticket:** S1050
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-15
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-находка при исследовании S1009 (2026-07-15)
**Tactical spec:** inline (compact) - см. «Фазы реализации».

<!-- parked by /spec-draft (auto-capture, out-of-scope finding during S1009 research) - 2026-07-15 -->
<!-- auto-approved by /spec-all - 2026-07-15 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-15 (авто-находка во время research S1009)

**Симптом:** Гейт экспорта схемы Room (S0731) не охраняет актуальную версию БД - его константа устарела на 4 версии.

**Evidence:**

- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt:32` - `@Database(version = 40, exportSchema = true)`.
- `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/AppDatabaseSchemaExportTest.kt:62` - `CURRENT_VERSION = 36`.
- Дрейф: тест сверяет наличие/корректность экспортированной схемы только для v36, тогда как БД уже на v40. Версии 37-40 не покрыты guard'ом: если version bump забудет закоммитить `schemas/<db>/<N>.json`, тест не упадёт.
- Экспортированные схемы `36.json`..`40.json` фактически присутствуют (проверено) - регенерация не требуется; дефект только в устаревшей константе guard'а.

---

## 1. Проблема

Guard экспорта схемы Room (`AppDatabaseSchemaExportTest`, S0731) хардкодит охраняемую версию константой `CURRENT_VERSION = 36`, тогда как `@Database(version)` уже 40. Тест проходит (проверяет `36.json`), но перестал охранять актуальную версию: bump без коммита `N.json` для v37..v40 не был бы пойман. Корень - хардкод-константа, дрейфующая от `@Database(version)`.

---

## 2. Цели

1. Guard охраняет фактическую текущую версию БД, а не устаревшую константу.
2. Дрейф исключён конструктивно: охраняемая версия выводится из `@Database(version)` в исходнике `AppDatabase.kt`, а не из отдельной хардкод-константы.

**Non-goals:**

- Валидация корректности миграций (нужен инструментальный `MigrationTestHelper` - вне объёма этого JVM-теста).
- Регенерация схем (все `36.json`..`40.json` уже присутствуют).

---

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** все (тест общий).
- **API level:** без API-специфики (чистый JVM-тест).
- **Wear OS:** не затрагивается.
- **Совместимость данных:** без изменения схемы/БД - меняется только тест.
- **Локализация:** не затрагивается.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0731 (schema-export guard), S1009 (следующая миграция 40->41 - guard подхватит новую версию автоматически).

---

## 4. Контекст текущей архитектуры

`AppDatabaseSchemaExportTest` (S0731) сверяет, что для текущей версии БД закоммичена экспортированная схема. Директорию схем он уже находит обходом рабочего каталога вверх (`resolveSchemaDir`). Текущую версию он берёт из хардкод-константы `CURRENT_VERSION`, которая рассинхронилась с `@Database(version)`.

---

## 5. Предлагаемый подход

Заменить хардкод-константу `CURRENT_VERSION` чтением `@Database(version = N)` из исходника `AppDatabase.kt` тем же обходом рабочего каталога, что и `resolveSchemaDir` (без зависимости от runtime-retention аннотации Room). Тогда guard автоматически трекает актуальную версию, а дрейф невозможен по построению.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Схемы 37-40 присутствуют; правка чисто в тесте.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - внутренняя проверка тестов.

---

## 11. Критерии готовности (strategic-level)

1. `AppDatabaseSchemaExportTest` определяет охраняемую версию из `@Database(version)` в `AppDatabase.kt` (нет хардкод-константы версии).
2. Тест проходит для текущей версии 40 и автоматически подхватит будущие bump'ы (напр. 41 в S1009).
3. `testStandardDebugUnitTest --tests *AppDatabaseSchemaExportTest*` - PASS.

---

## Фазы реализации (compact tactical)

### Phase 1 - Drift-proof guard

1. `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/AppDatabaseSchemaExportTest.kt`: убрать константу `CURRENT_VERSION = 36`; добавить `readDatabaseVersionFromSource()` (обход рабочего каталога вверх к `AppDatabase.kt`, regex `@Database(.. version = N ..)`), использовать его в проверке существования `<N>.json` и сверке версии внутри файла схемы.
   - Verify: `@Database(version)` читается из исходника; `40.json` найден; версии совпадают.
2. Сборка + тест `testStandardDebugUnitTest --tests *AppDatabaseSchemaExportTest*`.
   - Verify: BUILD SUCCESSFUL, тест PASS.

---

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (compact - inline phases)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Все критерии готовности выполнены:

- `AppDatabaseSchemaExportTest` определяет охраняемую версию через `readDatabaseVersionFromSource()` (regex `@Database(.. version = N ..)` по исходнику `AppDatabase.kt`, обход рабочего каталога вверх как в `resolveSchemaDir`); хардкод-константа `CURRENT_VERSION = 36` удалена (§11.1).
- Дрейф исключён по построению: охраняемая версия следует за `@Database(version)`; будущие bump'ы (напр. 41 в S1009) подхватываются автоматически (§11.2).
- `testStandardDebugUnitTest --tests *AppDatabaseSchemaExportTest*` - BUILD SUCCESSFUL, тест PASS: `@Database(version)=40` прочитан, `40.json` найден, версии совпали (§11.3).
- Экспортированные схемы `36.json`..`40.json` присутствуют - регенерация не требовалась.
- Правка чисто в тесте, detekt по затронутому файлу чист (магическое число вынесено в `MAX_WALK_UP_LEVELS`). §8 FEATURES «Без изменений» -> EXEMPT.
