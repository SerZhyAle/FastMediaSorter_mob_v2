# Спецификация (fix): S0731 - Release-безопасность БД: destructive-migration и broad-catch delete

**Ticket:** S0731
**Status:** Archived
**Priority:** 65
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0717 (Layer 4, кластер P1/P2 потери данных)
**Umbrella:** S0714

> **Scope:** Латентный риск тихой потери ВСЕХ пользовательских данных при будущей миграции. Найдено статически (S0717). Высокий приоритет.

---

## 0. Источник

Пять находок аудита S0717 (`PLAN/S0717_room-database-audit/AUDIT_FINDINGS.md`, #1/#2/#4/#5/#7), сводящихся к настройке `AppDatabase`/`DatabaseModule`. Нарушает инвариант протокола Layer 4 («каждая смена схемы несёт миграцию И тест; в release нет destructive fallback») и собственное правило проекта.

## 1. Проблема

`AppDatabase` - `@Database(version=36, exportSchema=false)`, 17 сущностей, включая невосстановимые пользовательские данные (`ResourceEntity`, `NetworkCredentialsEntity` - сервер/логин/шифр-пароль/SSH-ключи, `FavoritesEntity`, `ScheduledOperationEntity`, `StreamSourceEntity`, `AppLaunchPanelTileEntity`).

Два независимых деструктивных пути, оба в release без гварда:

1. **`DatabaseModule.kt:110`** - `fallbackToDestructiveMigration(dropAllTables=true)` безусловно. Цепочка миграций 1→36 сейчас полна (активной потери нет), но любая будущая смена схемы без корректной `Migration` → тихий wipe всех таблиц в release.
2. **`DatabaseModule.kt:50`** - `provideAppDatabase` ловит `catch (e: Exception)` на открытии и зовёт `context.deleteDatabase(DB_NAME)` + rebuild с одним Toast. Срабатывает не только на corruption, но и на транзиентных `SQLiteFullException`/`SQLiteDiskIOException`/locked - где retry бы восстановил. Бэкапа перед удалением нет.
3. **`AppDatabase.kt:33`** - `exportSchema=false`, нет `schemaLocation`, нет ни одного `MigrationTestHelper`-теста (единственный `*MigrationTest` - несвязанный UI). Гейта, который поймал бы битую миграцию до release, нет.

Замечание: `exportSchema=false` не отключает runtime-проверку identityHash (mismatch падает громко) - но это не спасает от destructive fallback и broad-catch.

## 2. Решение

**Решение владельца (2026-06-26):** wipe сохраняется (приложение продолжает работать), но вместо тихого Toast - информативный диалог с причиной и местом бэкапа; пользователь информирован.

**Part 1 - поведение (реализовано):**

- Убран `fallbackToDestructiveMigration(dropAllTables=true)`: битая/отсутствующая миграция теперь бросает исключение и идёт через единый recovery-путь `provideAppDatabase`, а не через тихий Room-internal drop.
- В recovery-пути перед `deleteDatabase` - бэкап `.db`(+`-wal`/`-shm`) в device-dir (`getExternalFilesDir/db-backups/<ts>`), best-effort, не бросает.
- Вместо Toast - запись `DatabaseResetNotice` (причина = класс+message исключения, путь бэкапа), показывается `AlertDialog` на первом Activity (`MainActivity.onCreate`).
- Новый `core/db/DatabaseResetNotice.kt`; строки `database_reset_dialog_title/_message/_backup_note` (EN/RU/UK); осиротевший `database_reset_message` удалён.

**Part 2 - гейт от будущих битых миграций (реализовано):**

- `exportSchema=true` (`AppDatabase`) + `room.schemaLocation=$projectDir/schemas` (kapt-arg в `build.gradle.kts`); схема `36.json` сгенерирована и закоммичена под `app_v2/schemas/`. Любая будущая смена схемы теперь даёт review-diff и валидируемый снимок.
- JVM-тест `AppDatabaseSchemaExportTest` (`src/test`, гоняется в существующем `testStandardDebugUnitTest` job CI): для текущей `@Database(version)` экспортированная схема существует и её `version` совпадает. Ловит «забыл закоммитить схему / выключил export» до release - без эмулятора.

**Ограничение (зафиксировано, не дефект):**

- Полноценный `MigrationTestHelper`-прогон миграций 1→36 невозможен: схемы версий 1-35 никогда не экспортировались, стартовую БД ранней версии создать не из чего. Защита идёт вперёд: с каждым новым bump'ом экспортируется N.json и добавляется тест миграции (N-1)→N.
- Инструментальный (androidTest) прогон миграций требует emulator-job в `android-ci.yml`, которого в проекте намеренно нет (CI без эмулятора). Добавление такого job - инфра-решение владельца (стоимость CI на каждый PR), вынесено в manual.

Не реализовано намеренно (вне решения владельца): сужение `catch` до corruption-типов с retry на транзиентных - оставлено как опциональное улучшение (сейчас любой open-fail → backup+reset+диалог).

## 3. Критерии приёмки

- [x] В release нет безусловного `fallbackToDestructiveMigration(dropAllTables=true)`.
- [x] Перед `deleteDatabase` - бэкап `.db`; пользователь информирован диалогом (причина + путь бэкапа), приложение работает.
- [x] `fc` зелёный; паритет строк EN/RU/UK; neuroslop delta 0.
- [x] Part 2: `exportSchema=true`, `36.json` закоммичена, JVM-инвариант экспорта схемы зелёный в CI (`testStandardDebugUnitTest`).
- [ ] (deferred, owner/infra) Инструментальный тест миграций (N-1)→N в CI - требует emulator-job в `android-ci.yml`.
- [ ] Сборки всех флейворов зелёные (release).

## 4. Связанные тикеты

- S0717 (аудит-источник), S0714 (зонтик), S0719 (release/R8 - смежная release-проверка).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Manual / on-device

- [ ] (owner/infra) Instrumented `MigrationTestHelper` (N-1)->N in CI - needs an emulator job in `android-ci.yml` (project CI is emulator-free).
- [ ] All-flavor release builds green - validated by `/spec-prerelease` + `/skill-release` (release pipeline), not this session.
