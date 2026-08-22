# S1207 - CastContext.getSharedInstance() blocks PlayerActivity.onCreate on the main thread

**Status:** Archived

## 0. Raw capture

Found during the `/spec-prerelease` sweep of 2026-07-27 (emulator-5554, Pixel_9, API 35, standard-debug). Out of scope for that sweep; parked per CLAUDE.md 3.1.

Every player open runs a synchronous Play Services Dynamite module load on the main thread. StrictMode reports a `DiskReadViolation` of up to 148 ms attributed directly to `PlayerActivity.onCreate`.

Verbatim stack (longest of the cluster, `temp/S0484/run_20260727_010917.log:28562`):

```
07-27 01:15:53.216  4010  4010 D StrictMode: StrictMode policy violation; ~duration=148 ms: android.os.strictmode.DiskReadViolation
	at java.io.File.isDirectory(File.java:859)
	at dalvik.system.DexPathList$Element.<init>(DexPathList.java:681)
	at dalvik.system.BaseDexClassLoader.<init>(BaseDexClassLoader.java:160)
	at dalvik.system.DelegateLastClassLoader.<init>(DelegateLastClassLoader.java:56)
	at com.google.android.gms.dynamite.DynamiteModule.zza(play-services-basement@@18.3.0:17)
	at com.google.android.gms.dynamite.DynamiteModule.load(play-services-basement@@18.3.0:7)
	at com.google.android.gms.internal.cast.zzag.zzf(play-services-cast-framework@@21.4.0:2)
	at com.google.android.gms.cast.framework.CastContext.<init>(play-services-cast-framework@@21.4.0:6)
	at com.google.android.gms.cast.framework.CastContext.getSharedInstance(play-services-cast-framework@@21.4.0:10)
	at com.sza.fastmediasorter.core.cast.CastMediaManagerImpl.init(CastMediaManagerImpl.kt:135)
	at com.sza.fastmediasorter.ui.player.PlayerManagerInitializer.initAudioAndMediaServices(PlayerManagerInitializer.kt:730)
	at com.sza.fastmediasorter.ui.player.PlayerManagerInitializer.initialize(PlayerManagerInitializer.kt:75)
	at com.sza.fastmediasorter.ui.player.PlayerActivity.initializeManagers(PlayerActivity.kt:613)
	at com.sza.fastmediasorter.ui.player.PlayerActivity.onCreate(PlayerActivity.kt:565)
```

Measured impact in the same run: player open (`ActivityTaskManager START` -> `onRenderedFirstFrame`) took 1824 ms for a local 2.6 MB mp4. The threshold is 4000 ms so the checkpoint still passed, but roughly a tenth of that budget is spent loading a Cast module the user may never use.

Supporting evidence: the sweep's log audit reported a whole `DiskReadViolation` cluster on this path (durations 0..148 ms), all on tid == pid (main thread).

Severity: P2 by the taxonomy in `docs/CODE_AUDIT_PROTOCOL.md` (main-thread disk I/O in a hot path, over-eager initialization). Not a crash, so it does not block the release it was found in.

## 1. Symptom

Opening any media in `PlayerActivity` performs a blocking Play Services Dynamite class-loader construction on the main thread before the first frame can render.

## 2. Why this needs its own ticket

- Cast availability is optional and flavor-gated; initialising it eagerly for every player open pays a cost that only Cast users benefit from.
- The emulator has no real Cast receiver, so the 148 ms measured here is close to a floor - a device with Play Services actually resolving the module can spend longer.
- Fixing it means moving Cast initialisation off the `onCreate` critical path (lazy on first Cast-button interaction, or a background dispatcher with a UI-side await), which touches `PlayerManagerInitializer` ordering and needs its own regression pass across the player family.

## 3. Open questions

- Can `CastMediaManagerImpl.init` be deferred until the Cast button is actually shown/pressed, or does the media-route button need `CastContext` to decide its own visibility?
  - **Resolved (2026-08-14).** Deferring to the tap is impossible: `CommandPanelAvailabilityUpdater` decides the button's visibility from `getCastMediaManager()?.isCastAvailable`, and that getter is `castContext != null`. A button that only appears after it is pressed is not a design. Deferring is unnecessary, though - the reactive half is already built and wired: `CastController.castAvailableState` is a `StateFlow`, and `CommandPanelController:153` already collects it and refreshes availability. So the initialization can move off the main thread and simply publish through the flow it already publishes through; the button appears when Cast is genuinely ready instead of after a blocking module load.
- Does the same eager init run in the other player hosts, or only `PlayerActivity`?
  - **Resolved (2026-08-14).** Only `PlayerActivity`. `castMediaManager.init()` has exactly one call site, `PlayerManagerInitializer:731`, and `PlayerManagerInitializer` is referenced only by `PlayerActivity` and its own helpers. The standalone hosts never construct it, so the player family needs no mirroring here.

## 4. Not investigated here

No fix attempted. The sweep only recorded the evidence and moved on.

## 5. Решение

Заменить блокирующий `CastContext.getSharedInstance(context)` на перегрузку
`getSharedInstance(Context, Executor)`, которая возвращает `Task<CastContext>` и выполняет загрузку
Dynamite-модуля на переданном исполнителе. Регистрация слушателя сессий и публикация доступности
остаются на главном потоке, потому что этого требует Cast SDK, но к моменту их выполнения тяжёлая
часть уже сделана. Кнопка Cast появляется через уже существующий `castAvailableState`, который панель
команд и так собирает, поэтому ни один файл интерфейса не меняется.

Наличие перегрузки проверено на самом артефакте, который собирается в приложение:
`javap` по `play-services-cast-framework-21.4.0.aar` показывает
`getSharedInstance(android.content.Context, java.util.concurrent.Executor)` с типом
`Task<CastContext>`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0403 (сид `castEnabled` / `castDisabled`), S0484 (прогон `/spec-prerelease`, в котором находка сделана), S1558 (стерео-кроп в том же менеджере).
- **Flavor scope:** правка целиком внутри source set `castEnabled`, то есть `standard`, `noLegal`, `lite`, `photos`, `legacy`; `vr` монтирует `castDisabled` и не затрагивается.
- **Performance budget:** цель - убрать с главного потока загрузку модуля длительностью до 148 мс при каждом открытии плеера; новых бюджетов не вводится.
- **Validation level:** компиляция `standard` плюс grep-предикаты шагов; на устройстве без Chromecast разница не наблюдаема.
- **Owner sign-off:** отсутствует. Объём проставлен пайплайном `/spec-do` автоматически и владельцем не просматривался.

### 5.4 Почему колбэки Task заменены на корутину

Первая редакция вешала на `Task` пару `addOnSuccessListener` / `addOnFailureListener`. Гейт
`assert-listener-symmetry` посчитал это двумя регистрациями без парного снятия и упал (+2). Формально
он прав по форме, хотя разовые колбэки `Task` ничего не удерживают, поэтому вместо ослабления гейта
вызов переписан на `await()` из `kotlinx-coroutines-play-services` внутри `lifecycleScope.launch` -
это ещё и идиома, которой класс уже пользуется. `CancellationException` перевыбрасывается явно, иначе
`catch (e: Exception)` внутри корутины проглотил бы отмену.

### 5.3 Измерение на устройстве после правки

Устройство RFCR110NBQJ (Galaxy S21, Android 15), сборка standard-debug `v2.60.8112.319` с
окончательной редакцией кода, файл открыт обзором внутри приложения (не интентом). Захват -
2365 строк; несущий вердикт извлечённый фрагмент сохранён рядом со спекой:
[`device-capture-extract.txt`](S1207_cast_context_init_blocks_player_onCreate/device-capture-extract.txt).
Главный поток процесса - `pid/tid 28079`.

- Ни один из 11 стеков StrictMode в захвате не содержит `DynamiteModule`, `CastContext` или
  `getSharedInstance`; сама подстрока `getSharedInstance` в логе не встречается ни разу. Нарушение,
  ради которого заведён тикет, исчезло.
- Загрузка Dynamite-модуля Cast (4 строки, 17:42:48.349-.376) выполнилась на `tid 28353`, а не на
  главном потоке.
- `CastContext.<init>` виден в стеке строки `ConnectivityManager: StackLog:` в 17:42:48.385 - тоже на
  `tid 28353`, и кадры стека заканчиваются `CoroutineScheduler$Worker.run`, то есть работу выполнил
  диспетчер корутин.
- `CastMediaManagerImpl: initialized, isCasting=false` записан в 17:42:48.427 на `tid 28079`, то есть
  корутина возобновилась на главном потоке и слушатель сессий зарегистрирован там, где этого требует
  Cast SDK.
- Строка инициализации встречается в захвате ровно один раз - повторный вход в `init()` второй
  загрузки не запускает.
- `FATAL EXCEPTION`, `AndroidRuntime` и `Cast SDK not available` в захвате отсутствуют: обходной путь
  отказа не сработал, Cast инициализировался штатно.
- `ActivityTaskManager: START` в 17:42:48.102, `onRenderedFirstFrame` в 17:42:48.862 - 760 мс.
  Сравнивать с 1824 мс из §0 нельзя: то измерение сделано на эмуляторе, это на телефоне.

Более ранний прогон измерял промежуточную редакцию с колбэками `Task`; для доказательства
отгружаемого кода он не используется и потому не сохранялся - см. §5.4.

### 5.2 Почему кнопка не пропадает ни при каком порядке событий

`bindCastManager` подписывается на `castAvailableState`, но обновляет панель только при непустом
`cachedState`, а сама видимость вычисляется из живого геттера `isCastAvailable`. Поток здесь - сигнал
инвалидации, а не источник значения, поэтому оба порядка сходятся:

- Загрузка завершилась раньше, чем приехало состояние плеера: `cachedState` заполняется позже, и
  первый же `updateCommandAvailability` читает `isCastAvailable` уже истинным.
- Состояние приехало раньше, чем завершилась загрузка: первый расчёт скрывает кнопку, но эмиссия
  `true` приходит при уже непустом `cachedState` и пересчитывает панель.

Плохого порядка, при котором кнопка осталась бы скрытой на всю сессию, нет.

### 5.1 Ограничения

- `init()` по контракту `CastController` безопасен к повторному вызову - асинхронная версия обязана
  сохранить это свойство и не запускать вторую загрузку поверх незавершённой.
- `release()` может случиться раньше, чем загрузка завершится: колбэк не должен регистрировать
  слушателя на уже освобождённом менеджере.
- Флейворы без Cast SDK (`castDisabled`) не затрагиваются - там `NoOpCastController`.

---

## 5.5 Закрытие при красном репозиторном гейте

`post-change.ps1` не доведён до конца: гейт `assert-no-ticket-logs` репозиторный, его нельзя сузить до
своего набора файлов, и он падает на четырёх строках `Timber.d("S1202: ..)` в
`StandaloneFullscreenManager.kt`. Тикет S1202 в этот момент удерживает живая сессия
`67e401ac-2900-43e9-b9c9-e0a1b18e32d2`, то есть это работающая инструментовка соседа, а не
просроченный тег - удалять её нельзя. Гейт позеленеет сам, когда сосед переведёт S1202 в
`BlockNeedUserTest`.

Поэтому остальные гейты запущены напрямую по своему файлу, все зелёные:

- `detekt-scoped.ps1 -ChangedFiles <файл>` - exit 0, новых находок нет.
- `assert-listener-symmetry.ps1 -Gate -ChangedFiles <файл>` - new imbalance 0.
- `assert-neuroslop.ps1 -Gate -ChangedFiles <файл>` - все измерения не выше базовой линии,
  в том числе `swallowed-cancellation: 0`.
- `a.ps1 fk` - BUILD SUCCESSFUL.
- `catalog_sync.ps1 -Module app_v2` - выполнен отдельно.

## 6. Фазы

### Phase 01 - Move the Dynamite load off the main thread

**Objective:** `PlayerActivity.onCreate` no longer performs a synchronous Play Services module load;
Cast availability is published asynchronously through the flow the command panel already collects.

#### Step 01.1 - Make `init()` asynchronous and re-entrant

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`

**Prompt for developer:**

> Replace the blocking `CastContext.getSharedInstance(context)` call in `init()` with
> `CastContext.getSharedInstance(context, Dispatchers.IO.asExecutor())`, and move the session-listener
> registration, the pre-existing-session resume and the `castAvailableState` publication into the
> task's success callback, leaving the failure callback to clear `castContext` and publish `false`.
> Keep `init()` re-entrant: a second call while a load is in flight, or after one succeeded, must not
> start another. Make `release()` mark the manager released so a callback that lands afterwards
> registers nothing. Keep the number of `return` statements in `init()` at two or fewer - collapse the
> capability and permission guards into a single early exit that names which one fired.

**Why:**

The stack captured in section 0 attributes a 148 ms main-thread `DiskReadViolation` directly to
`PlayerActivity.onCreate` through this exact call, and section 2 records that every player open pays
that cost while only Cast users benefit from it.

**Verification:**

- `Grep` - `getSharedInstance(context, ` present in the file; no bare `getSharedInstance(context)` call remains.
- `Grep` - `asExecutor` imported from `kotlinx.coroutines`.
- `Grep` - `castAvailableState.value = true` appears inside the success path, not at statement level in `init()`.
- Build - `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

#### Step 01.2 - Prove the panel still learns about Cast

**Files:** none - verification only

**Prompt for developer:**

> Confirm that the only consumer deciding Cast-button visibility reacts to the flow rather than to a
> one-shot read at panel construction, so the button still appears once the asynchronous
> initialization completes.

**Why:**

The fix is only safe because `CommandPanelController` already collects `castAvailableState`; if that
collection did not exist, moving initialization off the critical path would silently hide the Cast
button on every launch.

**Verification:**

- `Grep` - `castAvailableState.collect` present in `CommandPanelController.kt`.
- `Grep` - `isCastAvailable` still read by `CommandPanelAvailabilityUpdater.kt` (the collector triggers the re-read).

**Status:** `[x]` done

---

## Last Audit

**Date:** 2026-08-14
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Проверенные инварианты

- Оба открытых вопроса §3 закрыты по коду, а не предположением; `check-open-items-carried.ps1` - exit 0.
- Тегов `Timber.d("S1207:` в дереве нет - статус не `BlockNeedUserTest`. Упоминание тикета в KDoc
  комментарием остаётся: это не лог.
- Доказательство лежит в `PLAN/S1207_cast_context_init_blocks_player_onCreate/`, а не в `temp/`, -
  гейт долговечности доказательств пройден.
- EXEMPT: `docs/FEATURES*` и `docs/ALL_FEATURES.jsonl` не трогаются - перфоманс-правка без новой
  пользовательской способности (§8 шаблона: перфоманс всегда «Без изменений»).

### Внешнее, не относящееся к качеству этой спеки

`post-change.ps1` целиком пройти нельзя, пока живая соседняя сессия держит S1202 с её пробами в
`StandaloneFullscreenManager.kt`: гейт `assert-no-ticket-logs` репозиторный и не сужается до своего
набора файлов. Остальные гейты запущены напрямую и зелёные - перечень в §5.5. Повторный прогон
`post-change.ps1` уместен после того, как сосед переведёт S1202 в `BlockNeedUserTest`.
