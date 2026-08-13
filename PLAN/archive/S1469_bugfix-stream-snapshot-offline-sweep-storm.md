# Спецификация (compact bugfix): S1469 - Без сети сетка потоков перебирает весь список превью впустую

**Ticket:** S1469
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-07
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Текст:**

```
"P:\ANDROID\FastMediaSorter_mob_v2\logs\FastMediaSorter Debug Logs (1)"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\FastMediaSorter Debug Logs (2)"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\fastmediasorter_logs (1).zip"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\fastmediasorter_logs.zip"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\FastMediaSorter Debug Logs"

изучи логи - вдруг найдешь что то полезное или сможешь закрытьобновить /создать какие то задачи?
Что можно сделать с приёмом телесигнала, он не полностью стабильный (последний лог)
```

**Вложения:**
- Лог-эвиденс общий с S1467 - `PLAN/S1467_bugfix-stream-stall-watchdog-reanchor-loop/attachments/01__device-log-2026-08-07-car-head-unit.log`

---

## 1. Проблема / симптом

Автомобильная магнитола стартует раньше, чем поднимается сеть. Приложение открывает сетку потоков и начинает генерировать превью для каждого видимого тайла, хотя DNS не резолвится вообще ни для одного хоста.

Эвиденс - лог 2026-08-07, старт в 10:42:00:

- 10:42:14 .. 10:43:25 - 30+ попыток подряд, примерно по 3 с каждая, все падают в `Stream snapshot error - falling back to favicon: <url>`.
- Причина у всех одна: `java.net.UnknownHostException` / `android.system.GaiException` - 46 стектрейсов в файле. Среди неразрешимых хостов и `github.com` (импорт каталога), то есть сети нет целиком, а не «канал недоступен».
- 10:44:01 - **весь список проходится заново** с начала. Это ожидаемое поведение per-url backoff S1169 (`BACKOFF_BASE_MS = 60_000`), но по факту оно даёт второй полный проход по мёртвой сети.
- В том же окне дважды `Stream catalog import failed: download/unzip` - тот же корень.

Итого около 3 минут работы декодера и сети впустую на устройстве, у которого в этом же логе `native heap low before playback - free=15MB`. Пользователь всё это время видит сетку без превью.

Существующая защита (S1169) - экспоненциальный backoff **на url**. Её не хватает именно в этом сценарии: когда сети нет, отказ приходит одновременно всем url, и каждый оплачивает полный таймаут захвата отдельно. Нужен уровень выше - общий для менеджера, а не для тайла.

---

## 2. Корневая причина

У `StreamFrameSnapshotManager.request()` нет предохранителя уровня менеджера: перед постановкой в очередь не проверяется ни связность, ни число подряд идущих отказов по всем url сразу. Единственная защита - per-url backoff S1169, а он по построению не может погасить отказ, который приходит всем url одновременно: каждый url оплачивает свой полный таймаут захвата отдельно (`CAPTURE_TIMEOUT_MS = 12_000`, `StreamFrameSnapshotManager.kt:267`).

Три факта из кода, которые объясняют, почему в логе получилось именно два полных прохода, а не один.

- **Период повторного обхода численно равен базе backoff.** `REFRESH_INTERVAL_MS = 60_000L` (`StreamGridModeManager.kt:268`) и `BACKOFF_BASE_MS = 60_000L` (`StreamFrameSnapshotManager.kt:292`). После первого отказа url становится снова доступен ровно к моменту, когда срабатывает периодический обход, поэтому floor S1169 второй проход не задерживает вообще. Это и есть повторный обход в 10:44:01.
- **Менеджеров два, а не один.** `StreamsActivity` строит независимую пару для основной секции (`StreamsActivity.kt:230-240`) и для закреплённой (`:272-280`), у каждой свои `queue`, `pending`, `semaphore` и свои карты `nextEligibleAt` / `consecutiveFailures`. Один url, присутствующий в обеих секциях, имеет два независимых состояния backoff. В логе это видно как два `Stream snapshot error` с интервалом 92 мс (10:42:14.467 и 10:42:14.559) при `MAX_CONCURRENT_CAPTURES = 1` - одна очередь такого дать не может.
- **Отказ из-за отсутствия сети записывается как отказ этого url.** `recordCaptureOutcome` (`StreamFrameSnapshotManager.kt:122-133`) не различает «канал мёртв» и «сети нет вообще», поэтому после восстановления связи живые каналы сидят в чужом штрафе до пяти минут (`BACKOFF_CAP_MS`).

Связность в дереве уже есть и её не нужно строить заново: `NetworkContextAnalyzer` (`core/network/NetworkContextAnalyzer.kt`) - `@Singleton` с Hilt-конструктором и синхронным `hasAnyNetwork()` без ввода-вывода. `StreamsViewModel` его уже инжектит, но вниз, в менеджер снапшотов, не передаёт.

Импорт каталога проверен, как требовал §3: предохранителя нет ни одного. `ImportStreamCatalogUseCase.invoke()` идёт сразу в `downloadCatalog()` (`ImportStreamCatalogUseCase.kt:31-38`), связность не спрашивает, и так ведут себя все четыре его вызывателя.

---

## 3. Исправление

Приняты первый и третий кандидаты; второй отклонён.

- **Гейт по связности в `request()`.** Не ставить захват в очередь, пока `hasAnyNetwork()` ложно.
- **Не штрафовать url за отсутствие сети.** Отказ, случившийся при отсутствии связи, не увеличивает `consecutiveFailures` и не двигает `nextEligibleAt`: он ничего не сообщает об этом канале.
- **Глобальный circuit breaker отклонён.** С гейтом по связности шторм не начинается вовсе, поэтому счётчик подряд идущих отказов гасил бы то, чего уже нет. Он же дороже по конструкции: менеджера два и состояние пришлось бы поднимать над обоими, плюс нужен явный сброс, иначе одиночный всплеск отказов глушит сетку до следующего действия пользователя.

Как возвращаются превью после появления сети, без ручного обновления и без нового колбэка: гейт - дешёвая синхронная проверка, поэтому ближайший обход, который и так происходит, проходит его и запускает захваты. Обходы уже инициируются скроллом и периодическим таймером в 60 с (`StreamGridModeManager.kt:214-225`), а штраф backoff к этому моменту не начислен - это прямое следствие третьего пункта. Отдельно подписываться на `NetworkStateMonitor` не нужно и было бы хуже: на пути Streams он вообще не запущен, так как поднимается лениво при первом обращении к SMB/FTP/облаку.

Зависимость на S1433 не берётся: у него в дереве пока только интерфейс флага возможностей, связности там нет, и его собственная фаза 02 планирует опираться ровно на те же `NetworkContextAnalyzer` и `NetworkStateMonitor`. Ждать его нечего.

Гейт импорта каталога ставится в самом `ImportStreamCatalogUseCase`, а не в вызывателях - тогда он покрывает все четыре точки входа сразу. Локализации это не стоит: текст `CatalogImportResult.Failure` в UI отбрасывается, показывается фиксированная `R.string.streams_error_network` (`StreamsViewModel.kt:325-326`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1169 (существующий per-url backoff, который дополняется, а не заменяется), S1433 network-monitor (проверен - опираться пока не на что, зависимость не берётся)

---

## 4. Проверка

- Юнит-тест правила штрафа: отказ без сети не начисляется, отказ при наличии сети начисляется, успех штраф снимает в обоих случаях.
- `.\a.ps1 fk` - компиляция standard проходит.
- Сценарий на устройстве: открыть сетку потоков в авиарежиме - ожидается ни одной попытки захвата вместо трёх десятков; после включения сети превью появляются на ближайшем обходе без ручного обновления.

---

## 5. Фазы

### Phase 01 - Предохранитель связности для обхода превью

**Objective:** при отсутствии сети сетка потоков не запускает ни одного захвата и не штрафует каналы, а после восстановления связи возвращается к превью сама.

#### Step 01.1 - Правило штрафа как чистая функция

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt`
**Depends on:** - начало фазы

**Prompt for developer:**

> Add a top-level `internal fun shouldPenaliseCaptureFailure(ok: Boolean, hasNetwork: Boolean): Boolean` next to `backoffDelayMs`, returning true only for a failure that happened while the device had a network. Give it a KDoc naming the case it exists for.

**Why:**

Отказ, случившийся при отсутствии связи, ничего не сообщает об этом канале, а по текущему коду загоняет живой url в общий с мёртвыми штраф до пяти минут; вынесение наружу повторяет уже принятый в этом файле приём с `backoffDelayMs` и делает правило проверяемым без создания менеджера.

**Verification:**

- `Grep` - `internal fun shouldPenaliseCaptureFailure` встречается ровно один раз.

**Status:** `[x]` done

#### Step 01.2 - Гейт связности в менеджере снапшотов

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a constructor parameter `hasNetwork: () -> Boolean = { true }`, mirroring the existing `hostProvider` lambda style. In `request()` return before any queueing or backoff bookkeeping when it reports no network, and log that the sweep is gated once per offline spell rather than once per url. In `recordCaptureOutcome()` consult `shouldPenaliseCaptureFailure` so an offline failure records nothing. Keep the default so any other construction site behaves exactly as before.

**Why:**

`request()` сегодня не имеет предохранителя уровня менеджера, поэтому мёртвая сеть оплачивается полным таймаутом захвата на каждый видимый тайл, а per-url backoff по построению не гасит отказ, приходящий всем url одновременно.

**Verification:**

- `Grep` - `hasNetwork: () -> Boolean = { true }` присутствует в списке параметров конструктора.
- `Grep` - `shouldPenaliseCaptureFailure` вызывается в `recordCaptureOutcome`.

**Status:** `[x]` done

#### Step 01.3 - Передать связность обоим менеджерам

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inject `NetworkContextAnalyzer` into `StreamsActivity` and pass `hasNetwork = { networkContextAnalyzer.hasAnyNetwork() }` to both `snapshotManager` and `pinnedSnapshotManager`. Both are required: each instance owns its own queue and backoff state.

**Why:**

Секций две, и каждая строит собственный `StreamFrameSnapshotManager` со своими картами `nextEligibleAt` и `consecutiveFailures`, поэтому гейт, поставленный только основной секции, оставил бы половину шторма нетронутой.

**Verification:**

- `Grep` - `hasNetwork = ` встречается в файле ровно дважды.

**Status:** `[x]` done

#### Step 01.4 - Гейт импорта каталога

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt`
**Depends on:** - независим от 01.1-01.3

**Prompt for developer:**

> Inject `NetworkContextAnalyzer` and return `CatalogImportResult.Failure` at the top of `invoke()` when there is no network, before the download is attempted. Log it at WARN alongside the existing import failures.

**Why:**

В том же логе импорт каталога упал дважды по той же мёртвой сети, а предохранителя нет ни в самом use case, ни в одном из четырёх его вызывателей; гейт в use case покрывает все четыре сразу.

**Verification:**

- `Grep` - `hasAnyNetwork` присутствует в файле.

**Status:** `[x]` done

#### Step 01.5 - Юнит-тест правила штрафа

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotPenaltyTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a plain JUnit test next to `StreamFrameSnapshotBackoffTest` covering all four combinations of outcome and connectivity.

**Why:**

Вся подсистема снапшотов, кроме одной чистой функции backoff, тестами не покрыта, а именно это правило отделяет мёртвый канал от отсутствующей сети - регрессия в нём вернула бы штраф живым каналам незаметно.

**Verification:**

- `.\a.ps1 fu -Tests "*StreamFrameSnapshotPenaltyTest*"` - класс проходит.

**Status:** `[x]` done

#### Phase Done Criteria

- [x] Все шаги `[x] done`.
- [x] `.\a.ps1 fk` - компиляция standard проходит.
- [x] `post-change.ps1` закрывает изменение без падения гейтов.

---

## Last Audit

**Дата:** 2026-08-07. **Проведён:** `/spec-all`, Simple path, S4.

Изменённый код:

- `ui/streams/helpers/StreamFrameSnapshotManager.kt` - параметр `hasNetwork`, гейт в `request()`, правило штрафа в `recordCaptureOutcome`, top-level `shouldPenaliseCaptureFailure`.
- `ui/streams/StreamsActivity.kt` - инжекция `NetworkContextAnalyzer` и передача связности обоим менеджерам.
- `domain/usecase/streams/ImportStreamCatalogUseCase.kt` - гейт связности до загрузки.
- `ui/streams/helpers/StreamFrameSnapshotPenaltyTest.kt` - новый, 4 теста.
- `data/repository/streams/FaviconAtlasStoreTest.kt` - дополнен новым аргументом конструктора.

Эвиденс:

- `.\a.ps1 fk` - exit 0.
- `.\a.ps1 fu -Tests "*StreamFrameSnapshot*"` - exit 0; оба XML от 13:14:05: `StreamFrameSnapshotBackoffTest` 3/0/0, `StreamFrameSnapshotPenaltyTest` 4/0/0.
- `post-change.ps1 -ScopeToFile -ChangeType Kotlin` - exit 0, `PASS WITH ADVISORIES (1)`.

Разобранное при аудите:

- Единственный advisory - `device-profile-matrix-gate`, ругается на поля `screenshotGesture*` в матрице профилей устройства. Это работа соседней сессии, а не эта: `CODE.LOCK` в тот момент принадлежал S1470 с причиной «fold screenshotGesture fields into a nested settings class». Затронутые здесь файлы лежат в `ui/streams` и `domain/usecase/streams` и профилей устройства не касаются.
- Изменение конструктора поймало реального потребителя: `FaviconAtlasStoreTest` конструирует `ImportStreamCatalogUseCase` напрямую, компиляция тестов упала, аргумент добавлен. Мок relaxed безопасен: эти случаи вызывают только `extractCatalog()`, до гейта связности не доходят.
- Гейт не задевает уже реализованные пути: значение `hasNetwork` по умолчанию `{ true }`, поэтому любая другая точка конструирования ведёт себя как до тикета.
- Отказ без сети теперь не проходит ни по одной ветке `when` в `recordCaptureOutcome`, то есть не пишет ни `consecutiveFailures`, ни `nextEligibleAt` - это и есть условие того, что после возврата сети ближайший обход стартует без штрафа.

Остаточный разрыв:

- Третий критерий §4 - сценарий с авиарежимом на устройстве - не снят. Эмулятор был доступен, но им одновременно пользовалась соседняя сессия (её `CODE.LOCK` активен), а параллельные прогоны на одном `emulator-5554` портят друг другу ввод. Проверка отложена в `/spec-sweep`; пробы `S1469:` стоят на обоих изменённых входах.

**Вердикт:** `BlockNeedUserTest` - код доказан компиляцией и юнит-тестами, остался сценарий на устройстве.
