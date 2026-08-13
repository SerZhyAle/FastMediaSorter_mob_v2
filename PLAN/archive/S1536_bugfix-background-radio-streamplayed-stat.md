# Спецификация (compact bugfix): S1536 - фоновое радио не попадает в статистику «сыграно потоков»

**Ticket:** S1536
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** аудит фазы S1509 (сужение `RecordStreamPlayOutcomeUseCase` до writer-а только успеха). Сам S1509 этот путь не трогает.

**Текст:**

```
Background-service radio plays are missing from the StreamPlayed statistic.
AudioPlaybackService.recordCurrentStreamSuccess (AudioPlaybackService.kt:813-826) writes the green
bullet and the last-played timestamp straight through StreamSourceRepository.recordPlayOutcome /
markPlayed, bypassing RecordStreamPlayOutcomeUseCase.recordPlaySuccess, which is the only place that
records StatsEvent.StreamPlayed. The service does record StatsEvent.View(AUDIO) (line 384) and
StatsEvent.PlaybackTime(AUDIO) (line 699), but never StreamPlayed. So the same radio channel counts as
a stream play when it runs on the inline OFF-path player and does not when it runs on the background
service - which is the dominant listening mode for radio - and the "streams played" metric undercounts
by that difference. Found during the S1509 audit while narrowing RecordStreamPlayOutcomeUseCase to a
success-only writer; S1509 itself does not touch this path. Needs a decision on whether View and
StreamPlayed are meant to both fire for one play before the call site is changed.
```

---

## 1. Проблема / симптом

Один и тот же радиоканал считается сыгранным потоком, когда играет на встроенном OFF-плеере, и не считается, когда играет через фоновый сервис. Фоновый режим для радио основной, поэтому метрика «сыграно потоков» занижена на эту разницу.

Проверенный эвиденс (код, 2026-08-08):

- `AudioPlaybackService.kt:813-826` - `recordCurrentStreamSuccess` пишет `streamSourceRepository.recordPlayOutcome(source.id, STREAM_OUTCOME_OK)` и `markPlayed`, минуя use case.
- `AudioPlaybackService.kt:384` пишет `StatsEvent.View(ViewKind.AUDIO)`, `:699` - `StatsEvent.PlaybackTime(ViewKind.AUDIO, elapsed)`; `StatsEvent.StreamPlayed` в файле не встречается.
- `RecordStreamPlayOutcomeUseCase.recordPlaySuccess` - единственное место, где пишется `StatsEvent.StreamPlayed`.

---

## 2. Корневая причина

Сервис пишет в репозиторий напрямую, а событие статистики живёт в use case - но это не единственная причина, и вторая важнее первой.

1. **Обход слоя.** `recordCurrentStreamSuccess` (`AudioPlaybackService.kt:813-826`) вызывает `recordPlayOutcome` + `markPlayed` на репозитории. `StatsEvent.StreamPlayed` пишется только в `RecordStreamPlayOutcomeUseCase.recordPlaySuccess`, поэтому фоновый путь его не пишет никогда.
2. **Точка вызова срабатывает многократно.** `recordCurrentStreamSuccess()` вызывается из `Player.STATE_READY` (`AudioPlaybackService.kt:400`), а живой поток входит в READY заново после каждого ребуферинга и переподключения - для радио это норма, а не край. Поэтому «просто вызвать use case вместо репозитория» превратило бы занижение счётчика в завышение: одно прослушивание с пятью ребуферингами дало бы пять `StreamPlayed`.
3. **Готового защёлкивателя нет.** Поле `streamHasSuccessfulPlayback` для этого не годится: `resetStreamRecovery` засеивает его из `lastPlayedAt != null` (`AudioPlaybackService.kt:773`), то есть оно истинно для любой ранее игравшей станции ещё до первого READY этой сессии. Оно отвечает на вопрос «можно ли переподключаться», а не «считали ли мы уже это прослушивание».

Вопрос захвата - не двойной ли это счёт, если для одного прослушивания сработают и `View`, и `StreamPlayed` - снят кодом, решение владельца не требуется. Это два разных счётчика в разных ключах (`StatsSinkImpl.kt:100-133`): `View(AUDIO)` инкрементирует `AUDIO_PLAYED` / `AUDIO_LISTEN_MS` («треков прослушано») и пишется в `STATE_ENDED`, то есть по факту дослушанного трека, а `StreamPlayed(AUDIO)` инкрементирует `STREAMS_AUDIO_PLAYED` («потоков сыграно») и пишется по факту начавшегося воспроизведения. Живой радиопоток до `STATE_ENDED` сам по себе не доходит вовсе, так что для радио они почти никогда даже не совпадают по времени.

---

## 3. Исправление

`recordCurrentStreamSuccess` переводится на `RecordStreamPlayOutcomeUseCase.recordPlaySuccess`, но только для первого READY этой сессии прослушивания; последующие READY того же URL сохраняют текущее поведение (обновляют исход и отметку времени, счётчик не трогают).

- Сервису добавляется `@Inject` на `RecordStreamPlayOutcomeUseCase` - `@AndroidEntryPoint` уже стоит, новых модулей и скоупов не нужно.
- Новое поле хранит URL, для которого счётчик в этой сессии уже записан. Сбрасывается в `resetStreamRecovery` - это существующий хук смены медиа-элемента, то есть ровно граница сессии.
- Ветка «первый READY» вызывает `recordPlaySuccess(source.id)`, который сам пишет исход, отметку времени и `StreamPlayed`; ветка «повторный READY» оставляет две прямые записи репозитория как сейчас.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1509 (сузил use case до `recordPlaySuccess`, чем обнажил обход), S1510 (периодическая диагностика потока - другой слой измерений, не дубликат)
- **Открытый вопрос захвата снят кодом** (§2, последний абзац): `View` и `StreamPlayed` - разные ключи статистики, двойного счёта нет.

---

## 4. Проверка

- Юнит-тест на use case уже есть (`RecordStreamPlayOutcomeUseCaseTest`); он покрывает, что `recordPlaySuccess` пишет ровно один `StreamPlayed`. Логика защёлкивания живёт в `AudioPlaybackService`, для которого в проекте нет тестового дома, поэтому она проверяется на устройстве.
- На устройстве: включить радиоканал в фоновом сервисе, дождаться хотя бы одного ребуферинга, остановить, посмотреть «сыграно потоков» в статистике - +1 за прослушивание, а не +1 за каждый ребуферинг. Затем сыграть тот же канал ещё раз - счётчик снова +1.
- Метка `S1536` в логе пишет URL и то, первый ли это READY, поэтому по экспорту логов видно, сколько раз точка сработала и сколько раз она посчитала.

### 4.1 Repro record (S1338)

- **До исправления (код, 2026-08-08, подтверждено повторно 2026-08-10):** `StatsEvent.StreamPlayed` встречается ровно в двух местах - `RecordStreamPlayOutcomeUseCase.kt:35` (запись) и `StatsSinkImpl.kt:129` (потребление). В `AudioPlaybackService.kt` его нет, а фоновый путь идёт мимо use case (строки 823-824). То есть занижение счётчика доказано трассой вызовов, а не догадкой.
- **После исправления:** наблюдение снимает владелец на устройстве по §4 - воспроизведение на этой машине невозможно, потому что фоновый радиосервис и экран статистики требуют реального прослушивания. Поэтому тикет уходит в `BlockNeedUserTest`, а не в `Implemented`.
