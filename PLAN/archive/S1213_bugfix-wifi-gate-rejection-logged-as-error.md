# Спецификация (compact bugfix): S1213 - штатный отказ Wi-Fi-гейта пишется как ошибка со стеком

**Ticket:** S1213
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-27
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-27

**Текст:**

Найдено при разборе логов через `/log-reader` (пять сессий noLegal-debug на SM-S731B, 2026-07-26..27). Не связано с задачей анализа логов, требует отдельного расследования.

`NetworkReachabilityGate` штатно отклоняет SMB-операцию, когда включено ограничение «только Wi-Fi», а устройство на мобильной сети. Отказ уже логируется дважды на нужных уровнях:

```
2026-07-26 12:22:24.589 W/App: NetworkReachabilityGate: no-wifi for SMB
2026-07-26 12:22:24.590 D/App: SMB connection failed with non-retriable error: WifiRequiredException
```

После этого `SmbClient` пишет то же самое третий раз - уровнем E и с полным стеком:

```
2026-07-26 12:22:24.591 E/App: SMB testConnection failed
com.sza.fastmediasorter.data.network.exceptions.WifiRequiredException: Wi-Fi required
	at com.sza.fastmediasorter.core.network.NetworkReachabilityGate.requireWifi(NetworkReachabilityGate.kt:46)
	.. (полный стек корутины, ~30 строк)
```

Код-эвиденс, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClient.kt:126-135` - `Timber.e(lastException, finalMessage)` вызывается безусловно, без разбора класса исключения, хотя сам код строкой выше уже классифицировал ошибку как non-retriable.

Каскад повторяется и выше по стеку: `ResourceNavigationCoordinator.kt:134` пишет ещё один `Timber.e(error, "Connection test failed for ${resource.name}")` с тем же `WifiRequiredException`.

Масштаб по логам: за сутки в `logs/fastmediasorter_20260726_035956.log` - 8 строк уровня E, все до одной от этого сценария; это 100% всех ошибок в сессии. Настоящих ошибок в логе нет, но лог выглядит красным, и любой автоматический разбор E-уровня даёт ложные срабатывания.

Ожидаемое поведение: предусловие, заданное настройкой пользователя, - не ошибка. Уровень должен быть W (или I), стек для него не нужен, и одна запись на событие вместо трёх-четырёх.

Смежная запись в памяти агента: «Timber.e только для настоящих ошибок, штатные fallback-и - на Timber.i».

---

## 1. Проблема / симптом

Штатный отказ по ограничению «только Wi-Fi» логируется как ошибка уровня E с полным стеком, причём трижды-четырежды на одно событие. Это единственный источник E-строк в дневных логах, из-за чего разбор логов и любой автоматический E-порог дают ложные срабатывания. Наблюдается на всех flavor'ах, где включён SMB, при мобильной сети и включённом ограничении Wi-Fi.

---

## 2. Корневая причина

**2.1 Каскад из четырёх записей на одно событие.**

Одно срабатывание Wi-Fi-гейта проходит через четыре точки логирования:

- `NetworkReachabilityGate.requireWifi:43` - `Timber.w("NetworkReachabilityGate: no-wifi for SMB")`. Единственная запись, которая здесь уместна: уровень W, без стека.
- `SmbClient.testConnection:118` - `Timber.d("SMB connection failed with non-retriable error: ..")`. Уровень D, без стека, вреда нет.
- `SmbClient.testConnection:130` - `Timber.e(lastException, finalMessage)`. Вызывается безусловно, хотя строкой выше исключение уже классифицировано как non-retriable.
- `ResourceNavigationCoordinator.testConnectionAndNavigate:134` - `Timber.e(error, "Connection test failed for ..")`. Тоже безусловно, для любого исхода `onFailure`.

Красными в логе делают ровно две последние точки, и они же тянут по ~30 строк стека каждая.

**2.2 Классификатор уже умеет отличать политику от сбоя, но интерактивный путь его не использует.**

- `WifiRequiredException` - подкласс `NetworkConnectionLostException`, то есть `NetworkException`; `NetworkErrorClassifier.classifyDetailedSilently` возвращает его как есть с `usedFallback = false`.
- В проекте уже есть `HandledNetworkOutcomeLogger` (`data/network/exceptions/`), созданный ровно под эту задачу: `isPolicyOutcome` относит `WifiRequiredException` и `LocalNetworkPermissionDeniedException` к политике и логирует их через `Timber.i` без стека.
- Помощник применён только на фоновых путях синхронизации - `SyncNetworkResourcesUseCase` и `NetworkFilesSyncWorker`. Интерактивный путь «пользователь нажал на ресурс» (`SmbClient` -> `ResourceNavigationCoordinator`) на него так и не перевели.

Значит, дефект - не отсутствие механизма, а неполная миграция на уже существующий.

**2.3 Третья точка в том же методе.**

`ResourceNavigationCoordinator:152` - `catch (e: Exception) { Timber.e(e, "Exception testing connection for ..") }`. Тот же безусловный `Timber.e` для случая, когда исключение прилетает броском, а не через `Result.onFailure`. Правится вместе с §2.1, иначе дефект просто переезжает на соседнюю ветку.

---

## 3. Исправление

**Расширить `HandledNetworkOutcomeLogger`.**

- Добавить `logConnectionTestFailure(scope, resourceLabel, throwable, message)` для интерактивных проверок соединения.
- Исход, заданный настройкой пользователя (`isPolicyOutcome`), пишется как `Timber.d` без стека: авторитетная запись уровня W уже сделана самим гейтом, дублировать её уровнем выше незачем.
- Любой другой исход сохраняет прежнее поведение - `Timber.e(throwable, ..)` с полным стеком.
- Существующий `logHandledSyncFailure` не трогается: у фоновой синхронизации своя семантика уровней (`Timber.i` для политики).

**Перевести интерактивный путь на помощник.**

- `SmbClient.testConnection` - заменить безусловный `Timber.e(lastException, finalMessage)` вызовом помощника; `finalMessage` со счётчиком попыток сохраняется как `message`.
- `ResourceNavigationCoordinator.testConnectionAndNavigate` - обе точки (`onFailure` и `catch`) переводятся на тот же вызов.

**Регрессионный тест.**

- `HandledNetworkOutcomeLoggerTest` в `app_v2/src/test` - подсаживает `Timber.Tree`, ловит приоритеты и проверяет, что `WifiRequiredException` не даёт `ERROR`, а произвольное исключение - даёт.
- Захвата Timber в тестах ещё не было; `Timber.plant` на JVM работает без Android, а `NetworkErrorClassifierTest` уже доказывает, что классификатор JVM-безопасен.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- Компиляция: `.\a.ps1 fk` - exit 0.
- Юнит-тест: `HandledNetworkOutcomeLoggerTest` зелёный.
- Ни одного безусловного `Timber.e` в правленых точках: grep по `SmbClient.kt` и `ResourceNavigationCoordinator.kt` не даёт `Timber.e(` в блоках проверки соединения.
- Гейты качества: `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - exit 0.
- Проверка на устройстве не требуется: изменение доказуемо юнит-тестом на уровень логирования. Живой лог даёт вторичное подтверждение при следующем прогоне с включённым ограничением Wi-Fi на мобильной сети.

---

## Last Audit

**Дата:** 2026-07-27 · **Вердикт:** Verified

Затронуты четыре файла:

- `data/network/exceptions/HandledNetworkOutcomeLogger.kt` - добавлен `logConnectionTestFailure`; общий префикс вынесен в `prefixOf`, чтобы новый метод и существующий `logHandledSyncFailure` формировали его одинаково.
- `data/network/SmbClient.kt:133` - безусловный `Timber.e(lastException, finalMessage)` заменён вызовом помощника со `scope = "smb-test-connection"` и меткой `server/share`.
- `ui/main/helpers/ResourceNavigationCoordinator.kt:135,162` - обе точки (`onFailure` и `catch`) переведены на тот же вызов.
- `test/.. /HandledNetworkOutcomeLoggerTest.kt` - новый регрессионный тест на уровень логирования.

Что теперь пишется на одно срабатывание Wi-Fi-гейта: одна запись W от `NetworkReachabilityGate`, одна D от `SmbClient` про non-retriable класс, одна D от помощника вместо `Timber.e` со стеком, одна D от координатора вместо второго `Timber.e` со стеком. Записей уровня E и стеков - ноль.

Доказательства:

- `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*HandledNetworkOutcomeLoggerTest*"` - `BUILD SUCCESSFUL`, `Fast check passed.`, exit 0. `TEST-..HandledNetworkOutcomeLoggerTest.xml` от 21:08:00 - tests 4, failures 0, errors 0, skipped 0.
- `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - `post-change: PASS (Kotlin, 38070 ms)`, exit 0.
- `scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles <все четыре файла>` - `PASS [scoped] - 198 file(s) with new findings project-wide, none among changed files`, exit 0.
- `.\a.ps1 fg` - `assert-fast-gates: PASS (all fast gates green)`, 9 гейтов, 0 падений.
- Grep-предикат §4: `logConnectionTestFailure` присутствует в `SmbClient.kt:133` и `ResourceNavigationCoordinator.kt:135,162`; безусловных `Timber.e` в этих блоках не осталось.

Побочная правка, вызванная гейтом:

- Первый прогон detekt по всем изменённым файлам дал FAIL: `ImportOrdering` (L3 обоих файлов) и `ArgumentListWrapping` (чужой вызов `NetworkErrorMessageMapper.toContextAwareMessage`). Это не новые дефекты, а сдвиг сигнатур baseline - добавленный импорт и добавленные строки сместили уже существовавшие находки.
- Устранено по существу, а не переигрыванием baseline: импорты в обоих файлах приведены к ktlint-раскладке (`*`, затем `java`, `javax`), аргументы `toContextAwareMessage` разложены по строкам. Общий счётчик файлов с находками по проекту упал с 200 до 198.

Остаточные замечания:

- Запись в `ALL_FEATURES` не создаётся: изменение не даёт пользователю новой возможности, оно меняет только гигиену лога.
- `Timber.i` для политики в `logHandledSyncFailure` и `Timber.d` в `logConnectionTestFailure` расходятся намеренно: фоновая синхронизация - единственная запись о своём исходе, интерактивный путь уже прикрыт записью W от самого гейта.
- Клиенты FTP и SFTP проверены: они зовут `requireAnyNetwork`, а тот бросает `NetworkConnectionLostException`, который `isPolicyOutcome` намеренно не относит к политике - полное отсутствие сети остаётся ошибкой. Тот же дефект там не воспроизводится, отдельный тикет не нужен.
