# Спецификация (compact bugfix): S1508 - Счётчик сталлов для понижения качества не затухает со временем

**Ticket:** S1508
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-08
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** аудит внешнего документа `PLAYBACK_RESILIENCE.md` (StreamsPlayer, Windows/.NET/libVLC) по запросу владельца «есть ли тут что-то полезное для нас? может мы в чем то лучше?».

**Текст:**

```
P:\WINDOWS\Streams_Player\docs\PLAYBACK_RESILIENCE.md
есть ли тут что-то полезное для нас? может мы в чем то лучше?
```

Находка аудита (наша сторона): `StreamQualityStepDownController.registerStall()` инкрементит `stallsSinceStep` и никогда его не уменьшает по времени. Счётчик обнуляется только в двух местах - при самом шаге вниз и при `setRenditions()`. Значит два сталла, разнесённые на час нормального просмотра, накапливаются и опускают качество так же, как два сталла подряд. Порог `STALL_STEP_THRESHOLD = 2` задуман как гистерезис против каскада, но без окна времени он превращается в счётчик за всю сессию.

- Эвиденс (наш код): `StreamQualityStepDownController.kt:55-68` (`registerStall`, единственный мутатор), `:74` (порог), `:38-47` (`setRenditions` - единственный сброс).

Находка аудита (их сторона, §5 приложенного документа): у StreamsPlayer то же правило имеет жёсткое окно - `AdaptiveQualityGovernor.StarvationWindow = 120 с`, два голодания **внутри окна** дают шаг вниз. Формулировка их документа: «два, а не одно, чтобы одна икота никогда не стоила качества - это же и удерживает здоровый поток от того, чтобы вообще дойти до правила». Без окна вторая половина этой гарантии у нас не работает: здоровый поток, стоящий часами, до правила дойдёт.

**Вложения:**
- Исходный документ StreamsPlayer «Playback resilience», §5 «Adaptive quality ceiling» - `PLAN/S1508_bugfix-stream-stepdown-no-decay-window/attachments/01__streamsplayer-playback-resilience.md`

---

## 1. Проблема / симптом

На долгой сессии просмотра интернет-канала качество картинки со временем деградирует до нижнего рунга лестницы без реальной причины, и обратно не поднимается.

- Затронуто: полноэкранный видеоплеер интернет-потоков (HLS/DASH с несколькими рендициями), флейворы со `streamingEnabled`.
- Не затронуто: RTSP (контроллер там не создаётся), одноруночные потоки, локальное видео.
- Подъёма качества обратно вверх в приложении нет вообще, поэтому каждое ошибочное понижение необратимо до конца сессии.
- Порог `STALL_STEP_THRESHOLD = 2` был задуман как «две икоты подряд», а работает как «две икоты за всю сессию».

---

## 2. Корневая причина

`StreamQualityStepDownController` хранит сталлы как беззнаковый счётчик `stallsSinceStep`, а не как события во времени, поэтому у накопленного сталла нет срока годности.

- `registerStall()` - единственный мутатор счётчика вверх, и он не знает текущего времени: сигнатура без параметров, чтения окружения внутри тоже нет.
- Сброс счётчика происходит ровно в двух местах: при самом шаге вниз и в `setRenditions()`. Обе точки событийные, ни одна не связана со временем.
- `setRenditions()` вызывается один раз за сессию: `inventoryStreamRenditions` в `StreamPlaybackHelper` намеренно игнорирует повторные `onTracksChanged`, чтобы применение капа не сбрасывало индекс потолка. Значит на практике сброса «по ходу просмотра» не бывает.
- Масштаб детектора сталлов задаёт нижнюю границу разумного окна: поза-фриз объявляется за 3 опроса по 3 с (~9 с), таймаут буферизации - 15 с (`StreamStallWatchdog.kt:142-145`). Два сталла настоящей деградации приходят на этом масштабе, десятками секунд, а не часами.

---

## 3. Исправление

Заменить счётчик на скользящее окно: контроллер хранит метки времени сталлов и учитывает только те, что попали в окно `STALL_DECAY_WINDOW_MS`.

- Окно = 120 000 мс, эталон StreamsPlayer `AdaptiveQualityGovernor.StarvationWindow`. Это ~8 таймаутов буферизации и ~13 интервалов детекта поза-фриза, то есть две настоящие деградации подряд в окно попадают с запасом, а две икоты, разнесённые на минуты просмотра, - нет.
- Время приходит параметром `registerStall(nowMs: Long)`, а не читается внутри. Класс остаётся чистым Kotlin без Android-типов, и существующий `StreamQualityStepDownControllerTest` продолжает гонять правило off-device без Robolectric.
- Источник времени на стороне вызова - `SystemClock.elapsedRealtime()`, монотонный. `System.currentTimeMillis()` не годится: скачок системных часов (NTP, смена часового пояса, ручная правка) либо мгновенно «состарит» живые сталлы, либо заморозит окно.
- Хранилище - `ArrayDeque<Long>` меток вместо `Int`. Это сохраняет смысл именованного порога `STALL_STEP_THRESHOLD`: при пороге 2 хватило бы одной метки последнего сталла, но тогда константа перестала бы что-либо задавать.
- Обе существующие точки сброса сохраняются как есть: очистка при шаге вниз (гистерезис против каскада) и очистка в `setRenditions()` (перевзвод при смене лестницы).
- Настройки пользователю не добавляются: это внутренняя эвристика плеера, у неё нет и не было UI.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1467 (bugfix-stream-stall-watchdog-reanchor-loop) - тот же поток эвиденса, другой слой; сталл, который считает этот контроллер, порождается в том числе переанкоровками watchdog

---

## 4. Проверка

- Юнит-тесты на `StreamQualityStepDownController`: два сталла за пределами окна шага не дают, два внутри - дают.
- Юнит-тест на границу окна: сталл ровно на краю окна ещё учитывается, на миллисекунду дальше - уже нет.
- Юнит-тест на то, что старая метка не «оживает»: сталл, выпавший из окна, не должен вместе со следующим за ним давать шаг.
- Существующие 8 тестов класса проходят после переписывания сигнатуры на `registerStall(nowMs)`.
- `.\a.ps1 fk` - компиляция standard.

---

## Phase 01 - Decay window for the stall counter

**Status:** ✅ Done
**Steps done:** 3 / 3

### Objective

Replace the session-lifetime stall counter in `StreamQualityStepDownController` with a sliding time window, and feed it a monotonic clock from the single call site.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt` | Modified | <= 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | <= 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownControllerTest.kt` | Modified | <= 200 |

### Steps

#### Step 01.1 - Window the stall history in the controller

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `stallsSinceStep: Int` field with an `ArrayDeque<Long>` of stall timestamps. Change the signature to `registerStall(nowMs: Long): Cap?`; on each call drop every recorded timestamp older than `nowMs - STALL_DECAY_WINDOW_MS`, then append `nowMs`, and treat the threshold as reached when the retained count is at least `STALL_STEP_THRESHOLD`. Keep both existing clear points - on a step down, and in `setRenditions`. Add `private const val STALL_DECAY_WINDOW_MS = 120_000L` next to the threshold. Update the class and `registerStall` KDoc to state that the counter now decays and why the caller owns the clock.

**Why:**

Without a window the threshold counts stalls over the whole session, so two hiccups an hour apart lower the quality exactly like two consecutive ones, and the ceiling never rises again because the app has no step-up path (spec sections 1-2).

**Verification:**

- `Grep` - `fun registerStall(nowMs: Long): Cap?` matches exactly once. Actual: 1.
- `Grep` - `STALL_DECAY_WINDOW_MS = 120_000L` present. Actual: 1.
- `Grep` - `stallsSinceStep` returns zero hits in the file. Actual: 0.
- `Grep` - `import android.` returns zero hits in the file (the class stays free of Android types). Actual: 0.

**Status:** `[x]` done

---

#### Step 01.2 - Feed a monotonic clock from the call site

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `applyStreamQualityStepDown`, pass `SystemClock.elapsedRealtime()` into `registerStall`, adding the `android.os.SystemClock` import. Note in the existing KDoc of that function why the clock is monotonic rather than wall-clock.

**Why:**

The window has to survive an NTP correction or a manual clock change mid-session: a backwards jump would freeze the window open and a forwards jump would expire live stalls, both turning the fix from spec section 3 back into the bug it replaces.

**Verification:**

- `Grep` - `registerStall(SystemClock.elapsedRealtime())` matches exactly once. Actual: 1.
- `Grep` - `import android.os.SystemClock` present exactly once. Actual: 1.
- `Grep` - `System.currentTimeMillis` returns zero hits inside `applyStreamQualityStepDown`. Actual: 0.

**Status:** `[x]` done

---

#### Step 01.3 - Cover the window in the unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownControllerTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Thread an explicit virtual clock through the existing eight tests so each `registerStall` call carries a timestamp, keeping their current assertions intact. Add three tests: two stalls further apart than the window do not step; two stalls exactly at the window edge still step; a stall that has already fallen out of the window does not combine with a later one to reach the threshold. Run the class with `.\a.ps1 fu` or a targeted `--tests` filter.

**Why:**

The controller was built as pure Kotlin specifically so this policy is provable off-device (spec section 3), and an untested window is indistinguishable from the counter it replaces.

**Verification:**

- `Grep` - `StreamQualityStepDownControllerTest` contains at least 11 `@Test` annotations. Actual: 11.
- `Grep` - zero occurrences of `registerStall()` with an empty argument list in the test file. Actual: 0.
- Test run for the class reports `expected: all pass | actual: 11 tests, 0 failures, 0 errors, 0 skipped` (`app_v2/build/test-results/testStandardDebugUnitTest`).

**Status:** `[x]` done

---

### Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes - `expected: exit 0 | actual: exit 0`.
- [x] Unit tests for `StreamQualityStepDownControllerTest` pass - 11/11.
- [x] Closure run through `scripts/post-change.ps1 -ScopeToFile`.

---

## Last Audit

**Date:** 2026-08-08
**Verdict:** Verified
**Scope:** the three files listed under Files Touched, audited against `docs/CODE_AUDIT_PROTOCOL.md`. Trigger: shared-mutable-state change (an `Int` counter became a collection) plus the phase boundary.

- Единственный вход в счётчик - `applyStreamQualityStepDown`, а он вызывается только из `streamPlaybackListener.onPlaybackStateChanged`; `setRenditions` - только из `onTracksChanged`. Обе доставки Media3 идут на главный лупер, поэтому `ArrayDeque` живёт в том же однопоточном конфайнменте, что и прежний `Int`, и синхронизации не требует.
- Дек не растёт: каждый вызов либо оставляет не больше одной метки (порог не достигнут), либо чистит его целиком. Максимальный размер - `STALL_STEP_THRESHOLD`, и только внутри одного вызова.
- Цикл отсечения завершается всегда: `elapsedRealtime()` не идёт назад, поэтому дек остаётся отсортированным по возрастанию.
- Владение временем жизни не изменилось: `activeStreamStepDownController` по-прежнему обнуляется в `releaseStreamDiagnostics`, нового ресурса не появилось.
- Регрессии поведения нет: все 8 ранее существовавших тестов сохранили свои проверки без правок и зелёные; изменились только вызовы, получившие метку времени.
- Слои и правила: класс остаётся чистым Kotlin без Android-типов (проверено грепом на `import android.`), логирование не добавлялось, `post-change.ps1 -ScopeToFile` прошёл.

**Action items:** none.
