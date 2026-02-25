# Execution Board (Master Backlog)

Единый реестр задач для постановки и контроля исполнения.

## Правила статусов

- `TODO` — задача не начата
- `IN_PROGRESS` — задача в работе
- `BLOCKED` — есть блокер
- `REVIEW` — в PR/на ревью
- `DONE` — завершено

## Инициативы

| Initiative | Диапазон задач | Кол-во | Status | Owner | Blocker |
|---|---|---:|---|---|---|
| A1 | A1-T1..A1-T15 | 15 | TODO | - | - |
| A2 | A2-T1..A2-T15 | 15 | TODO | - | - |
| A3 | A3-T1..A3-T16 | 16 | TODO | - | - |
| A4 | A4-T1..A4-T11 | 11 | TODO | - | - |
| A5 | A5-T1..A5-T14 | 14 | TODO | - | - |
| B1 | B1-T1..B1-T9 | 9 | TODO | - | - |
| B2 | B2-T1..B2-T11 | 11 | TODO | - | - |
| B3 | B3-T1..B3-T10 | 10 | TODO | - | - |
| B4 | B4-T1..B4-T8 | 8 | TODO | - | - |
| B5 | B5-T1..B5-T11 | 11 | TODO | - | - |
| C1 | C1-T1..C1-T7 | 7 | TODO | - | - |
| C2 | C2-T1..C2-T8 | 8 | TODO | - | - |

## Task Registry

| Task ID | Initiative | Task | Owner | Status | PR | Blocker | Source |
|---|---|---|---|---|---|---|---|
| A1-T1 | A1 | Актуализировать `CloudCredentialsEntity` (`accountEmail`, `accountDisplayName`, provider-уникальность). | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T2 | A1 | Добавить/проверить индексы для поиска по `provider + accountEmail`. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T3 | A1 | Реализовать репозиторный контракт `getAllCredentialsByProvider(provider)` и выбор active credentials. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T4 | A1 | Добавить миграцию legacy single-account данных в multi-account формат без потери привязок ресурсов. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T5 | A1 | Для Google Drive добавить `login_hint` при re-auth и сценарии выбора аккаунта при добавлении нового. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T6 | A1 | Для OneDrive добавить `prompt=select_account` и обработку возврата существующего email. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T7 | A1 | Реализовать merge-логику: при совпадающем email обновлять токен, не создавать дубль credentials. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T8 | A1 | Добавить unit-тесты auth flow на add/switch/reauth/duplicate. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T9 | A1 | Реализовать account picker в flow создания облачного ресурса. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T10 | A1 | Добавить management-список аккаунтов провайдера в настройках. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T11 | A1 | Добавить визуальную индикацию аккаунта в карточке/редакторе ресурса. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T12 | A1 | Добавить/обновить строки EN/RU/UK для всех новых сценариев. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T13 | A1 | Интеграционные тесты на полный цикл: add 2+ account → bind resources → switch → delete. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T14 | A1 | Smoke-покрытие через Maestro для happy-path и duplicate-account path. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A1-T15 | A1 | Проверить сценарий истечения токена и re-auth с тем же `credentialsId`. | - | TODO | - | - | TZ_A1_MULTI_ACCOUNT.md |
| A2-T1 | A2 | Создать `ResourceEditorContractTestBase` с общим setup/teardown. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T2 | A2 | Настроить in-memory Room + test factories для всех типов ресурсов. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T3 | A2 | Подготовить deterministic test doubles для внешних зависимостей. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T4 | A2 | Добавить тесты видимости/невидимости полей для `LOCAL`. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T5 | A2 | Добавить тесты видимости/невидимости полей для `SMB/SFTP/FTP`. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T6 | A2 | Добавить тесты видимости/невидимости полей для `CLOUD`. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T7 | A2 | Добавить negative tests на отсутствие лишних полей. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T8 | A2 | Проверить обязательные поля и форматы ввода. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T9 | A2 | Проверить диапазон/тип поля `port`. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T10 | A2 | Проверить коллизию имени и выдачу предложений. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T11 | A2 | Проверить условия `Save enabled/disabled`. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T12 | A2 | Проверить create/update/copy/delete lifecycle. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T13 | A2 | Проверить collapse/expand секции и сохранение состояния. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T14 | A2 | Проверить восстановление состояния формы при recreation. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A2-T15 | A2 | Добавить contract test suite как обязательный check в pipeline. | - | TODO | - | - | TZ_A2_CONTRACT_TESTS.md |
| A3-T1 | A3 | Стандартизировать структуру `maestro/` (flows, helpers, env-config). | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T2 | A3 | Подготовить reusable helper flows (navigation, auth-mock, resource-ops). | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T3 | A3 | Настроить стабильный runner профиль для CI эмулятора. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T4 | A3 | Добавить flow: add local resource. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T5 | A3 | Добавить flow: add SMB resource. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T6 | A3 | Добавить flow: add cloud resource (mock OAuth). | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T7 | A3 | Добавить flow: start scan local folder. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T8 | A3 | Добавить flow: edit resource. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T9 | A3 | Добавить flow: OAuth cancel + retry. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T10 | A3 | Добавить flow: empty folder scan result. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T11 | A3 | Добавить flow: cancel scan. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T12 | A3 | Добавить flow: copy resource. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T13 | A3 | Добавить flow: welcome full/skip/permissions. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T14 | A3 | Интегрировать Maestro suite в CI как блокирующий check. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T15 | A3 | Сохранять screenshots/logs/video при падении. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A3-T16 | A3 | Добавить smoke subset для быстрого pre-merge прогона. | - | TODO | - | - | TZ_A3_MAESTRO_REGRESSIONS.md |
| A4-T1 | A4 | Актуализировать workflow с последовательностью `lint -> unit -> contract -> maestro -> build`. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T2 | A4 | Включить кэширование Gradle и стабильные cache keys. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T3 | A4 | Настроить передачу секретов/переменных среды для тестов. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T4 | A4 | Включить `abortOnError = true` и политику baseline. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T5 | A4 | Подключить `testStandardDebugUnitTest` + contract suite в required checks. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T6 | A4 | Подключить Maestro smoke subset как required check. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T7 | A4 | Настроить required status checks для `main`. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T8 | A4 | Настроить ограничение force-push и minimum approvals. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T9 | A4 | Настроить auto-cancel устаревших CI прогонов. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T10 | A4 | Публиковать артефакты падений (test reports, screenshots, logs). | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A4-T11 | A4 | Добавить уведомления о падениях обязательных проверок. | - | TODO | - | - | TZ_A4_CI_QUALITY_GATE.md |
| A5-T1 | A5 | Добавить поля состояния сканирования (`lastScanTimestamp`, `lastModified`, `fileSize`) в модель кэша. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T2 | A5 | Реализовать алгоритм определения `added/modified/deleted`. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T3 | A5 | Реализовать delta-путь обработки без полного повторного прохода. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T4 | A5 | Добавить `forceFullScan` в API/UseCase/UI. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T5 | A5 | Добавить `FileMetadataCache` Entity и DAO запросы с учётом `provider` и `credentialsId`. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T6 | A5 | Реализовать запись кэша в scan pipeline. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T7 | A5 | Реализовать чтение кэша при отображении с fallback. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T8 | A5 | Реализовать TTL policy + cleanup job. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T9 | A5 | Ввести `ScanDispatcher` с лимитами по source type. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T10 | A5 | Реализовать ограничение через `Semaphore`/dispatcher. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T11 | A5 | Вынести лимиты в `ScanSettings`. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T12 | A5 | Unit-тесты для delta decision logic. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T13 | A5 | Integration test `scan -> modify -> rescan`. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| A5-T14 | A5 | Benchmark до/после на стандартизованном тестовом наборе. | - | TODO | - | - | TZ_A5_SCAN_OPTIMIZATION.md |
| B1-T1 | B1 | Создать `CloudAuthState` sealed class. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T2 | B1 | Реализовать `CloudAuthStateMachine` на `StateFlow`. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T3 | B1 | Добавить unit-тесты всех переходов состояний. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T4 | B1 | Вынести логику Google в `GoogleDriveAuthProvider`. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T5 | B1 | Вынести логику OneDrive в `OneDriveAuthProvider`. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T6 | B1 | Вынести логику Dropbox в `DropboxAuthProvider`. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T7 | B1 | Подключить общий `TokenManager` к provider implementations. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T8 | B1 | Реализовать единый callback entrypoint для OAuth redirect. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B1-T9 | B1 | Добавить маршрутизацию по provider type и uniform error handling. | - | TODO | - | - | TZ_B1_CLOUD_AUTH_UNIFICATION.md |
| B2-T1 | B2 | Создать `NetworkError` sealed class. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T2 | B2 | Реализовать `NetworkErrorClassifier` (`Exception -> NetworkError`). | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T3 | B2 | Добавить покрытие для `IOException`, HTTP ошибок и cloud-specific исключений. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T4 | B2 | Создать `RetryPolicy`. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T5 | B2 | Реализовать `withRetry` с backoff/jitter. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T6 | B2 | Встроить respect `Retry-After` и условный retry по classifier. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T7 | B2 | Создать map `NetworkError -> string resource`. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T8 | B2 | Добавить ресурсы EN/RU/UK. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T9 | B2 | Перевести UI на единый `showNetworkError()` контракт. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T10 | B2 | Заменить разрозненные try/catch в SMB/SFTP/FTP/Google/OneDrive/Dropbox. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B2-T11 | B2 | Добавить unit-тесты для всех типов ошибок и retry поведения. | - | TODO | - | - | TZ_B2_NETWORK_ERROR_NORMALIZATION.md |
| B3-T1 | B3 | Реализовать `StructuredLogger`. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T2 | B3 | Реализовать `CorrelationContext` для coroutine propagation. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T3 | B3 | Подключить adapter к Timber. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T4 | B3 | Инструментировать auth state transitions. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T5 | B3 | Инструментировать resource CRUD. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T6 | B3 | Инструментировать scan pipeline. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T7 | B3 | Инструментировать file operations. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T8 | B3 | Реализовать rolling file appender. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T9 | B3 | Реализовать UI export action в debug settings. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B3-T10 | B3 | Добавить маскирование секретов в логах. | - | TODO | - | - | TZ_B3_OBSERVABILITY.md |
| B4-T1 | B4 | Собрать топ DAO запросов по частоте/стоимости. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T2 | B4 | Зафиксировать baseline `EXPLAIN QUERY PLAN`. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T3 | B4 | Добавить/актуализировать `@Index` в Room Entity. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T4 | B4 | Подготовить миграции для создания индексов. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T5 | B4 | Исключить дублирующие/неиспользуемые индексы. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T6 | B4 | Снять `EXPLAIN QUERY PLAN` после внедрения. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T7 | B4 | Провести benchmark до/после. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B4-T8 | B4 | Добавить migration tests. | - | TODO | - | - | TZ_B4_DB_INDEXES.md |
| B5-T1 | B5 | Добавить `revokeToken()` в каждый cloud provider. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T2 | B5 | Вызвать revoke при delete account/logout. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T3 | B5 | Реализовать очередь `pending revocation`. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T4 | B5 | Реализовать `CredentialAuditor`. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T5 | B5 | Помечать expired credentials и прокидывать сигнал в UI. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T6 | B5 | Реализовать policy обработки неиспользуемых credentials. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T7 | B5 | Реализовать `OrphanCleanupJob` для cache/credentials/destinations. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T8 | B5 | Добавить логи очистки с correlation ID. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T9 | B5 | Выполнить аудит hardcoded secrets в коде. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T10 | B5 | Перенести найденные секреты в защищённые источники. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| B5-T11 | B5 | Добавить проверку маскирования токенов/паролей в логах. | - | TODO | - | - | TZ_B5_SECURITY_HARDENING.md |
| C1-T1 | C1 | Зафиксировать release policy и правила sign-off. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C1-T2 | C1 | Подготовить `RELEASE_CHECKLIST.md` как обязательный шаблон. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C1-T3 | C1 | Согласовать ownership и точки контроля по checklist. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C1-T4 | C1 | Реализовать script version bump. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C1-T5 | C1 | Реализовать script generate changelog. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C1-T6 | C1 | Реализовать automation RC build/tag. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C1-T7 | C1 | Добавить уведомления о статусе release pipeline. | - | TODO | - | - | TZ_C1_RELEASE_TRAIN.md |
| C2-T1 | C2 | Добавить timing instrumentation для scan/auth/startup. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T2 | C2 | Добавить success/failure counters для auth/resource save. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T3 | C2 | Определить формат хранения и экспорта метрик. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T4 | C2 | Зафиксировать baseline для всех KPI. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T5 | C2 | Реализовать генератор metrics report. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T6 | C2 | Реализовать сравнение текущих значений с baseline. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T7 | C2 | Проверить интеграцию с Crashlytics dashboard. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
| C2-T8 | C2 | Настроить алерты при деградации KPI. | - | TODO | - | - | TZ_C2_QUALITY_METRICS.md |
