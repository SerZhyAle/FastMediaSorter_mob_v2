# Спецификация (fix): S0732 - Атомарность многошаговых записей Room (@Transaction)

**Ticket:** S0732
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0717 (Layer 4, P2/P3 atomicity)
**Umbrella:** S0714

> **Scope:** Обернуть многошаговые записи Room в транзакции. Найдено статически (S0717).

---

## 0. Источник

Четыре находки аудита S0717 (`PLAN/S0717_room-database-audit/AUDIT_FINDINGS.md`, #6/#8/#9/#10). Корень общий: во всём `app_v2/src` нет ни одного `withTransaction`/`runInTransaction`, поэтому любая последовательность из нескольких DAO-записей неатомарна. (DAO-внутренние мульти-шаги в `ResourceDao` уже под `@Transaction` - не трогать.)

## 1. Находки и правки

1. **P2 - `data/repository/AppLaunchPanelRepositoryImpl.kt:25` `moveTile`.** Read-modify-write из 4 раздельных записей (`deleteBySlot`×2 + `upsert`×2) без транзакции; краш/kill между delete и upsert навсегда теряет конфиг плитки (источник только в in-memory `source`). Сетка 0..14, потеря ограничена одной плиткой, восстановима вручную. **Fix (внесён):** `db.withTransaction {}` на все 4 операции. Sibling `replaceAll` (`clearAll`+N upserts, тот же неатомарный паттерн в том же файле) обёрнут тем же образом.
2. **P3 - `data/repository/StreamSourceRepository.kt:64` `mergeCatalog`.** N `update`/`insertIgnore` + финальный `deleteCatalogNotIn` без транзакции. Self-healing кэш (CATALOG), пользовательские строки не трогаются. **Fix:** обернуть merge в одну транзакцию.
3. **P3 - `domain/usecase/ApplyBackupPayloadUseCase.kt:43` `invoke`.** 6 секций без общей транзакции; Room-подмножество (credentials/resources/favorites/ops) можно обернуть (settings в DataStore, sessions в EncryptedCookieStore - вне транзакции архитектурно). Merge-only, re-runnable. **Fix:** обернуть Room-секции в `appDatabase.withTransaction {}`.
4. **P3 - `domain/usecase/ImportSettingsUseCase.kt` (legacy XML).** Та же неатомарная мульти-секционная последовательность, что и JSON-путь (#3) - это намеренный единый дизайн backup/restore, не legacy-промах. Re-runnable, FK `onDelete=CASCADE`. **Fix (внесён):** apply-блок вынесен из `invoke` в отдельный suspend-метод `applyLegacyXmlSections`; Room-секции (credentials/resources/scheduled-op rows) обёрнуты в `appDatabase.withTransaction {}`; settings (DataStore) - вне транзакции; `WorkManager.scheduleOperation` отложен на после commit (собираются id включённых операций, планируются после транзакции - как в #3). Метод разбит на `mergeCredentials`/`mergeResources`/`upsertScheduledOps`/`buildScheduledOp` (detekt LongMethod/Complexity/ReturnCount в норме). Чтения ресурсов внутри транзакции переведены с Flow `.first()` на `getAllResourcesSync()` во избежание дедлока.

## 2. Критерии приёмки

- [x] `moveTile` атомарен (одна транзакция); kill между шагами не теряет плитку. (+ sibling `replaceAll`.)
- [x] `mergeCatalog`, `ApplyBackupPayloadUseCase` (Room-секции 2-5, scheduling после commit) и `ImportSettingsUseCase` legacy-XML обёрнуты в `withTransaction`; планирование WorkManager - после commit.
- [x] Поведение сохранено; unit-тесты `RestoreFromGoogleDriveUseCaseTest` (8) + `ConnectionGatesTest` зелёные; main компилируется.

## 3. Связанные тикеты

- S0717 (аудит-источник), S0714 (зонтик).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0717, S0714
- **Data:** оборачивает существующие мульти-записи Room в транзакции; схема и формат данных не меняются, поведение идентично (атомарность вместо частичных коммитов).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0

Все четыре находки реализованы через `withTransaction`:

- #1 `moveTile` + sibling `replaceAll` (P2, `AppLaunchPanelRepositoryImpl`).
- #2 `mergeCatalog` (P3, `StreamSourceRepository`).
- #3 `ApplyBackupPayloadUseCase` Room-секции 2-5 + scheduling после commit (P3).
- #4 `ImportSettingsUseCase` legacy-XML: apply-блок вынесен в `applyLegacyXmlSections` (+ `mergeCredentials`/`mergeResources`/`upsertScheduledOps`/`buildScheduledOp`), Room-секции в `appDatabase.withTransaction {}`, settings вне транзакции, scheduling после commit, чтения ресурсов внутри транзакции через `getAllResourcesSync()` (не Flow `.first()`).

`compileStandardDebugKotlin` зелёный; detekt-гейт PASS (новых findings нет, pre-existing ImportOrdering в файле устранён). Правка #4 изолирована в `ImportSettingsUseCase`, который не конструируется ни одним тестом, поэтому `RestoreFromGoogleDriveUseCaseTest` (8) и `ConnectionGatesTest` не затронуты.

### Регрессия (побочно исправлено)

- `ConnectionGatesTest` конструировал `SftpConnectionGate` со старой 3-арг сигнатурой (сломано добавлением `@ApplicationScope CoroutineScope` в S0727; build-gate S0727 не компилировал test source set). Добавлен `CoroutineScope(Dispatchers.Unconfined)` в 3 конструкции.
