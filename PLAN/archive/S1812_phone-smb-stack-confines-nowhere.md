# Стратегическая спецификация: S1812 - SMB-стек телефона не переключает диспетчер нигде

**Ticket:** S1812
**Status:** Archived
**Priority:** 70
**Date:** 2026-08-19
**Tier:** 3 - Tactical (ad-hoc)
**Roadmap entry:** Ad-hoc - находка замера при исследовании S1810, 2026-08-19

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-19

**Как нашлось.** S1808 починил ту же болезнь на часах: `SmbDataSource` не уходил на `Dispatchers.IO`, тогда
как FTP и SFTP уходили. Чтобы понять, стоит ли расхождение механической проверки (S1810), пришлось замерить
телефонный модуль. Замер показал, что там то же самое, только в пятнадцать раз больше.

**Симптом.** Ни один из пяти файлов SMB-семейства `app_v2` не содержит ни одного `withContext`:

| Файл | строк | `withContext` | использований `Dispatchers.` |
| --- | ---: | ---: | ---: |
| `data/network/SmbClient.kt` | 662 | 0 | 1 |
| `data/network/SmbConnectionManager.kt` | 1015 | 0 | 0 |
| `data/network/SmbFileMutationCoordinator.kt` | 198 | 0 | 0 |
| `data/network/SmbFileOperations.kt` | 636 | 0 | 0 |
| `data/network/SmbShareDiscoveryHelper.kt` | 242 | 0 | 0 |

Единственные два упоминания `Dispatchers.` - объявления полей, и оба ничего не удерживают:

- `SmbFileOperations.kt:41` - `private val smbDispatcher = Dispatchers.IO`, поле **не используется ни разу**
  в своём файле.
- `SmbClient.kt:52` - то же поле, единственное применение - передать его конструктору
  `SmbDirectoryScanner(smbDispatcher)` (строка 55). Сам `SmbClient` им не пользуется.
- `SmbConnectionManager.kt:17` - `import kotlinx.coroutines.Dispatchers` при нулевом использовании, то есть
  мёртвый импорт.

**Где именно теряется переключение.** Все suspend-функции `SmbClient`, `SmbFileOperations` и
`SmbFileMutationCoordinator` заворачивают блокирующие вызовы smbj в лямбду, отданную
`SmbConnectionManager.withConnection(connectionInfo) { share -> .. }`. Эта функция удерживает только
конкурентность - `connectionSemaphore.withPermit { .. }` - и **никакого переключения контекста не делает**.
`block(..)` выполняется на диспетчере вызывающего. Её сосед `createFreshConnection` вызывает блокирующие
`client.connect(..)` и `connection.authenticate(..)` прямо в теле suspend-функции.

`SmbShareDiscoveryHelper.listShares` обходит даже это: `client.connect(server, port)`,
`connection.authenticate(..)`, `session.connectShare(..)` вызываются напрямую, без семафора и без пула.

**Контраст с соседями по тому же модулю.**

- SFTP: `SftpClient.withConnection` делегирует в `SftpConnectionPool.withConnection`, а тот - настоящий
  `withContext(Dispatchers.IO)` (`SftpConnectionPool.kt:115-118`). Форма та же, поведение противоположное.
- FTP: `FtpClient.connect` и `disconnectInternal` оборачиваются литеральным `withContext(Dispatchers.IO)`.

То есть три реализации одного контракта расходятся - ровно как на часах, только здесь расходится целое
семейство, а не одна функция.

**Насколько это опасно сегодня - не измерено.** Вызывающих у SMB-семейства за пределами `data/network/` -
двадцать четыре файла; `Dispatchers.IO` упоминают двенадцать. Из тех, что не упоминают, замечены
`data/transfer/access/SmbFileAccess.kt`, `core/util/NetworkFileDownloader.kt` и
`data/glide/NetworkPdfThumbnailLoader.kt`. Это **не доказывает** ANR: часть из них вызывается из Glide,
у которого свой исполнитель, часть - из репозиториев, которые могут оборачивать выше. Цепочку до входной
точки никто не прослеживал, и это первая работа тикета.

---

## 1. Проблема

Безопасность блокирующих сетевых вызовов SMB на телефоне держится на дисциплине вызывающих, которых
двадцать четыре, и половина из них про диспетчер не упоминает. Проверить это глазами дороже, чем починить.

---

## 2. Цели

1. Известно, есть ли сегодня путь, на котором блокирующий вызов smbj достигается с главного потока.
2. Ответственность за диспетчер лежит на SMB-семействе, как она уже лежит на FTP, SFTP и на часах после S1808.
3. Мёртвые `smbDispatcher` и мёртвый импорт `Dispatchers` либо начинают работать, либо удалены.

**Non-goals:**

- Само правило lint - это S1810, который этого тикета и ждёт.

---

## 3. Решение

**Замер 2026-08-19: почти всё семейство проходит через одну функцию.** `SmbClient`, `SmbFileOperations` и
`SmbFileMutationCoordinator` не делают ни одного блокирующего вызова напрямую - каждый отдаёт лямбду в
`SmbConnectionManager.withConnection`. Значит удержание контекста ставится **в двух местах**, а не в двадцати
пяти:

1. `SmbConnectionManager.withConnection` (строка 245). Тело - выражение
   `= connectionSemaphore.withPermit { .. }`; оно оборачивается в `withContext(Dispatchers.IO)` снаружи
   семафора, чтобы на IO попало и всё, что делается до получения разрешения. Приватная
   `createFreshConnection` вызывается только изнутри и покрывается тем же.
2. `SmbShareDiscoveryHelper.listShares` (строка 22). Единственный найденный обход: `client.connect(..)`,
   `connection.authenticate(..)` и `session.connectShare(..)` вызываются прямо в теле suspend-функции, минуя
   и пул, и семафор.

Это ровно та форма, которая уже работает у соседа по модулю: `SftpConnectionPool.withConnection` удерживает
`withContext(Dispatchers.IO)` за все лямбды `SftpClient`. То есть решение не изобретается, а выравнивается по
живому образцу в том же слое.

**Что сознательно не трогается.**

- `getConnectionForExoPlayer` (строка 845) - **не** suspend и намеренно синхронна; её KDoc говорит: «Used by
  SmbDataSource which runs in ExoPlayer's thread pool». ExoPlayer зовёт её со своего загрузочного потока.
  Обернуть её значило бы сломать контракт, ради которого она написана.
- Не-suspend функции обслуживания (`resetAllConnections`, `forceFullReset`, `clearConnectionPool`,
  `closeUiConnections`), которые внутри закрывают сокеты. Удержать их без смены сигнатуры нельзя, и это
  отдельный разговор - записан открытым вопросом ниже, а не сделан молча.

**Мёртвые остатки, убираемые тем же изменением (правило 20).** `SmbFileOperations.kt:41`
`private val smbDispatcher = Dispatchers.IO` не используется ни разу - удаляется. `SmbConnectionManager.kt:17`
`import kotlinx.coroutines.Dispatchers` сейчас мёртв - после правки он становится живым.
`SmbClient.kt:52` остаётся: его значение уходит в конструктор `SmbDirectoryScanner`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1808 (тот же дефект на часах), S1810 (правило lint, ждёт этого тикета), S1195 (пределы анализа детектора)

---

## 6. Открытые вопросы / Research items

1. **Есть ли живой путь с главного потока**
   - **Вопрос:** достигает ли хоть одна цепочка блокирующий вызов smbj, стартовав с главного потока?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** да, ровно один - и он на часто нажимаемой кнопке.
   - **Трассировка всех двадцати четырёх вызывающих за пределами `data/network/`:** девятнадцать безопасны
     собственным удержанием (`withContext(Dispatchers.IO)` или `flowOn(ioDispatcher)` на входе use case);
     два безопасны по контракту исполнителя - `NetworkPdfThumbnailLoader` и `NetworkEpubCoverLoader` вызывают
     `runBlocking` внутри `DataFetcher.loadData`, который Glide зовёт со своего исполнителя; два оказались
     ложными совпадениями (`AddResourceActivity` - другой `connectionManager`, `PlayerViewModel` - только
     `ConcurrentHashMap` в памяти); **один небезопасен**.
   - **Небезопасная цепочка, целиком без единой suspend-функции:**
     - `ui/main/MainActivity.kt:1072` - `binding.btnRefresh.setOnClickListenerDebounced { .. }`, то есть
       обратный вызов клика, главный поток.
     - -> `SmbClient.kt:605` `fun forceFullReset()` - не suspend.
     - -> `SmbConnectionManager.kt:833` `fun forceFullReset()` - не suspend.
     - -> `SmbConnectionManager.kt:797` `private fun resetClients()` - три `SMBClient.close()` подряд.
   - **Насколько это плохо:** вторая половина `forceFullReset` безопасна - `closeAllConnections()` уходит в
     `pool.closeAll()`, а тот закрывает соединения асинхронно (`SmbConnectionPool.kt:145-151`). Значит на
     главном потоке остаётся именно `resetClients()`: закрытие трёх клиентов smbj, то есть разрыв сокетов.
     Кнопка - «Обновить» на главном списке ресурсов, её жмут постоянно, и на медленном или мёртвом сервере
     это застывший интерфейс.

2. **Где чинить - в семействе или в одной точке**
   - **Вопрос:** достаточно ли обернуть `withConnection`, или каждая публичная функция должна отвечать за себя?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** в двух точках на suspend-пути плюс одна на месте вызова для не-suspend пути.
   - **Замер:** `SmbClient` (8 вызовов), `SmbFileOperations` (12), `SmbFileMutationCoordinator` (2) и
     `SmbMediaScanCoordinator` (4) достигают smbj **только** через `SmbConnectionManager.withConnection`.
     Единственный обход на suspend-пути - `SmbShareDiscoveryHelper.listShares`. Двадцать шесть точек
     закрываются двумя обёртками.

3. **Не-suspend функции обслуживания, закрывающие сокеты**
   - **Вопрос:** кто зовёт `resetAllConnections`, `forceFullReset`, `clearConnectionPool`,
     `closeUiConnections` и с какого потока?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** с главного потока приходит один - `forceFullReset` из обработчика клика. Остальные три
     приходят из мест, которые сами уже вне главного потока или сами не блокируют:
     `resetAllConnections` - только из `ResetSmbConnectionsUseCase`; `clearConnectionPool` - из
     `SmbOperationsUseCase`, который удерживает контекст на входе; `closeUiConnections` - из
     `SmbConnectionGate` и `SmbBackgroundLifecycleManager`, то есть из наблюдателя жизненного цикла.
   - **Почему не стали делать их `suspend`:** сменить сигнатуру пришлось бы у четырёх публичных функций ради
     одного вызывающего. Дешевле и честнее исправить сам вызывающий, оставив сигнатуры в покое.

---

## 7. Фазы

### Phase 01 - The SMB family holds its own dispatcher

**Objective:** every blocking smbj call the phone makes from a suspend function runs on `Dispatchers.IO` because the SMB layer put it there, not because a caller happened to.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | <= 1030 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbShareDiscoveryHelper.kt` | Modified | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt` | Modified | <= 636 |

---

#### Step 01.1 - Confine `withConnection`

**Files:** `SmbConnectionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap the expression body of `withConnection` in `withContext(Dispatchers.IO) { .. }`, outside `connectionSemaphore.withPermit`, so the reachability gate, the pool lookup and `createFreshConnection` all run confined. Leave `getConnectionForExoPlayer` alone - it is deliberately synchronous for ExoPlayer's loader thread.

**Why:**

Section 3 records that `SmbClient`, `SmbFileOperations` and `SmbFileMutationCoordinator` reach smbj only through this one function, so confining it moves the whole family off the caller's dispatcher in a single place - the shape `SftpConnectionPool.withConnection` already uses for SFTP in the same layer.

**Verification:**

- `Grep` - `withContext(Dispatchers.IO)` encloses `connectionSemaphore.withPermit` in `withConnection`.
- `Grep` - `getConnectionForExoPlayer` still declares no `withContext`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

#### Step 01.2 - Confine `listShares`

**Files:** `SmbShareDiscoveryHelper.kt`
**Depends on:** - independent of 01.1

**Prompt for developer:**

> Wrap the body of `listShares` in `withContext(Dispatchers.IO)`. Cancellation is already handled - the outermost catch calls the repo's own `e.rethrowIfCancellation()` helper - so add nothing there.

**Why:**

Section 3 records that this is the one path that bypasses both the pool and the semaphore, calling `client.connect`, `connection.authenticate` and `session.connectShare` directly in a suspend body, so step 01.1 does not cover it.

**Verification:**

- `Grep` - `withContext(Dispatchers.IO)` present in `listShares`.
- `Grep` - `e.rethrowIfCancellation()` still present in the outermost catch.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

#### Step 01.3 - Remove the dead dispatcher field

**Files:** `SmbFileOperations.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete `private val smbDispatcher = Dispatchers.IO` and its now-unused `Dispatchers` import. Leave the identically named field in `SmbClient.kt` - that one is consumed by the `SmbDirectoryScanner` constructor.

**Why:**

Section 0 records the field is never read in its own file, and CLAUDE.md Rule 20 requires orphaned declarations to go in the same change; leaving it would keep asserting a confinement the class does not perform.

**Verification:**

- `Grep` - `smbDispatcher` returns zero hits in `SmbFileOperations.kt`.
- `Grep` - `smbDispatcher` still present twice in `SmbClient.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

#### Step 01.4 - Take the reset off the click callback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - independent of 01.1

**Prompt for developer:**

> Move the `smbClient.forceFullReset()` call in the Refresh button's listener into `lifecycleScope.launch { withContext(Dispatchers.IO) { .. } }`, and move the two following calls inside the same coroutine so the scan still starts after the reset.

**Why:**

Section 6 item 1 records this as the single measured path where a blocking smbj call is reached from the main thread - the listener, `SmbClient.forceFullReset`, `SmbConnectionManager.forceFullReset` and `resetClients` are all non-suspend, so the three `SMBClient.close()` calls share the click callback's stack, on a control that is tapped constantly.

**Verification:**

- `Grep` - `forceFullReset()` inside the Refresh listener sits within `withContext(Dispatchers.IO)`.
- `Grep` - `scanAllResources()` is still called after it, inside the same coroutine.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

**Phase Done Criteria**

- [x] Every `Step 01.*` above is `[x]` done.
- [x] `.\a.ps1 fk` exits 0; `.\a.ps1 fu` run and its 7 failures traced to S1644 and S1649.
- [x] `post-change.ps1` closes with `post-change: PASS`.

---

---

## 8. Implementation State

**2026-08-19 - Phase 01 done.**

- `SmbConnectionManager.withConnection` is now `= withContext(Dispatchers.IO) { connectionSemaphore.withPermit { .. } }`. Its 149-line body moved one indent level; nothing else in it changed, and the three `return@withPermit` labels still resolve to the same lambda.
- `SmbShareDiscoveryHelper.listShares` became `= withContext(Dispatchers.IO) { try { .. } }` with no re-indentation at all: the function's own block and the lambda's block are the same braces, so only the header line, the dropped `return` and two `return@withContext` labels changed.
- `SmbFileOperations` lost `private val smbDispatcher = Dispatchers.IO` and its now-unused import - the field was read nowhere in its own file.
- `MainActivity`'s Refresh listener wraps `smbClient.forceFullReset()` in `lifecycleScope.launch { withContext(Dispatchers.IO) { .. } }`, with the cache clear and `scanAllResources()` moved inside the same coroutine so the reset still happens first.

**The rename that had to be undone.** The first attempt extracted the two bodies into `withPooledConnection` and `listSharesConfined`. It compiled and read better, and it failed the scoped detekt gate with six findings - `LongMethod`, `CyclomaticComplexMethod`, `NestedBlockDepth`, `ReturnCount` and `TooManyFunctions`. None was new debt: renaming a function changes its detekt baseline signature, so four previously baselined findings resurfaced under the new name, and the two extra functions pushed the class from 38 to the 40-function threshold. Wrapping in place keeps every signature and therefore every baseline entry. Three lines crossed 120 characters from the added indent and were wrapped.

**User-visible.** The Refresh button on the main resource list no longer blocks the UI thread while three SMB sockets are torn down. On a healthy LAN that is milliseconds; on a stalled or dead server it was the difference between a pause and an ANR. This is a fix of an existing control, not a new capability, so it takes no `ALL_FEATURES` record.

**Measured 2026-08-19:** `.\a.ps1 fk` exit 0; `detekt-scoped: PASS [app_v2] - 4 file(s), no new finding under the full configured rule set`; every SMB unit test green - 212 tests across 23 result files, `failures="0" errors="0"` in each, counts read from the XML rather than from the gradle line; `post-change: PASS (Kotlin, 51001 ms)`.

**About the full suite.** `.\a.ps1 fu` was run and reported 7 failures out of 3855. None is in an SMB class. They are `LauncherStarterSetsTest` (3) and `CredentialAuditorTest` / `UnusedCredentialPolicyTest` (4), whose subjects map to **S1644** (`launcher-google-apps-starter-section`) and **S1649** (`orphaned-credentials-never-purged`) - both currently sitting in `BlockNeedUserTest`. Neither test file mentions `Smb`, `Dispatchers` or `withContext` at all. Worth the owner's attention on its own: two tickets are parked awaiting a device test while their unit tests are red, so a device pass would close them red.

**Parked during this ticket:** S1813 - `CloudFileOperationHandler.executeCopy` overrides a base method whose body is `withContext(Dispatchers.IO) { .. }` and drops that confinement, safe today only because it has exactly one caller.

## 10. Связи с другими спеками

- S1808 - тот же дефект на часах, починен 2026-08-19; здесь его телефонный масштаб.
- S1810 - правило lint, ловящее этот класс дефектов; ждёт этого тикета, потому что до починки правило со
  степенью `ERROR` уронит `lintStandardDebug`.
- S1195 - привёл `MainThreadIoDetector` к резолву диспетчера; его вывод про пределы анализа тут применим.

---

## Last Audit

**Date:** 2026-08-19
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Concurrency audit (CLAUDE.md section 13 - a dispatcher change on a shared network core):

- **No caller loses a dispatcher it relied on.** All 26 suspend call sites into smbj already went through `withConnection`, and 19 of the 24 external callers were already confining themselves; `withContext(Dispatchers.IO)` from a context that is already IO is a context comparison, not a thread hop, so the hot paths pay nothing.
- **The semaphore is unaffected.** `connectionSemaphore.withPermit` now runs inside the IO context rather than the caller's; acquiring a permit is a non-blocking suspend either way, and the permit is still released on every path because `withPermit` is unchanged.
- **The three `return@withPermit` labels still bind to the same lambda** - the wrapper added an outer scope, it did not relabel the inner one.
- **`getConnectionForExoPlayer` deliberately untouched.** It is not suspend and its KDoc states ExoPlayer calls it from the loader thread; confining it would have broken the contract it exists for.
- **Cancellation semantics unchanged.** `SmbConnectionManager` already caught and rethrew `CancellationException`, and `SmbShareDiscoveryHelper`'s outermost catch already called `e.rethrowIfCancellation()` - the spec's first draft of step 01.2 wrongly said the file handled cancellation nowhere, and that predicate was corrected before the step was ticked.

No P0/P1 finding.

Evidence 2026-08-19: `.\a.ps1 fk` exit 0; `detekt-scoped: PASS [app_v2] - 4 file(s), no new finding`; 212 SMB unit tests across 23 XML result files, all `failures="0" errors="0"`; `post-change: PASS (Kotlin, 51001 ms)`; `SmbConnectionManager.kt` 1033 lines against a 1030 budget - over by three, see the manual item; `check-open-items-carried.ps1 -Id S1812` exit 0; zero `Timber.d("S1812:` hits.

### Manual / on-device

- [ ] Tap Refresh on the main list against a reachable SMB share and confirm the list still rescans, and against an unreachable one that the UI stays responsive. Not run here: the attached device is a phone with no SMB server on this network. The change is proven statically and by the suite; this is the confirmation that the reordering into the coroutine kept the reset-before-scan sequence.

**Budget note:** `SmbConnectionManager.kt` finished at 1033 lines against the 1030 the phase set, because wrapping three over-long lines cost more than the estimate. It is far below the 1500-line rule and the file's own history shows helpers being extracted at 1000 - a future extraction is the right home for that, not this ticket.
