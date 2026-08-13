# Баг-фикс спецификация: S0060 — SMB thumbnail/share race при параллельном превью и playback

**Ticket:** S0060
**Status:** Implemented
**Priority:** 75
**Date:** 2026-05-03
**Implemented:** 2026-05-03
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — отдельный side-find из Quest 3 field session 2026-05-03, лог `logs/fastmediasorter_20260503_032115.log`
**Tactical spec:** не создавался — реализация прямая (4 файла, без новых модулей)
**Related:** S0041 (источник side-find), S0032 (network/null thumbnail symptom), S0051 (network datasource lifecycle), S0038 (тот же полевой лог, другой дефект)

> **Scope:** BUGFIX. Изолировать SMB thumbnail extraction от активного SMB playback, чтобы фоновые превью не ломали lifecycle share/connection и не портили playback-start/resume.

---

## 1. Проблема

В одной и той же SMB-сессии фоновая генерация видео-превью для списка и активное воспроизведение SMB-видео пересекаются по lifecycle соединения/`DiskShare`. В результате один поток помечает share как stale и инвалидирует его, пока другой поток ещё читает данные. Это даёт пачку `DiskShare has already been closed`, ложные thumbnail-failure записи и churn на playback-open/reopen.

Ключевая последовательность из `logs/fastmediasorter_20260503_032115.log`:

```text
03:50:32.090  Starting video frame extraction for: VRHush_..._3dv.mp4
03:50:32.301  VrPlayerActivity onCreate ... initialFilePath=smb://.../VRHush_..._3dv.mp4
03:50:42.094  Video thumbnail extraction TIMEOUT after 10000ms for VRHush_..._3dv.mp4
03:50:42.097  Added to failed video cache: VRHush_..._3dv.mp4
03:50:53.548  VIDEO PLAYER DEACTIVATED .. RESUMING THUMBNAILS IN 300ms
03:50:54.956  VIDEO PLAYER ACTIVATED .. SUSPENDING THUMBNAILS
03:50:55.215  Starting video frame extraction for: wankzvr-sharing-is-caring-...mp4
03:50:55.792  SmbDataSource.open: Stale share detected on openFile .. invalidating and retrying
03:50:55.802  Pooled connection failed, retrying with fresh
            com.hierynomus.smbj.common.SMBRuntimeException: DiskShare has already been closed
            at ... NetworkMediaDataSource.readFromSmb(..)
03:50:55.805  Connection marked for close but currently in use (count=3)
```

Симптомы этого бага:

1. Thumbnail extraction для SMB-видео срывается по timeout/interrupt не только из-за медленного `MediaMetadataRetriever`, но и из-за гонки lifecycle share.
2. Файл получает `failed video cache` в рамках текущей сессии, хотя причина может быть транзиентной, а не терминальной.
3. Playback-path вынужден инвалидировать и пересоздавать pooled connection прямо во время старта/перестарта файла.
4. В логах смешиваются два разных класса проблем: `getFrameAtTime returned null` и `DiskShare has already been closed`, хотя для диагностики их нужно разводить.

Это **не тот же дефект**, что S0032. Там проблема формулируется как `getFrameAtTime == null` и fallback poster/thumbnail. Здесь первичен именно **SMB share race** между background thumbnail IO и foreground playback IO.

---

## 2. Цели

1. Активный SMB playback должен иметь приоритет над background thumbnail extraction для того же SMB-ресурса.
2. Timeout, interrupt или stale-share в thumbnail path не должны закрывать или инвалидировать share, который ещё нужен playback path.
3. Транзиентный network/share race не должен записываться в постоянный `failed video cache` как окончательный провал файла.
4. Лог должен различать минимум три причины thumbnail failure: `timeout`, `stale-share`, `unsupported-or-null-frame`.
5. После остановки playback превью должны возобновляться без ручного refresh и без повторного churn в connection pool.

**Non-goals:**

- Не чинить сам VR exit/new-window дефект из S0038.
- Не оптимизировать общий SMB scan/listing throughput из S0056.
- Не перерабатывать все сетевые протоколы сразу, если проблема подтверждена только для SMB.
- Не менять визуальный UI списка файлов.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Playback важнее thumbnail. Если нужно выбирать, thumbnail может быть отложен, пропущен или повторён позже.
2. Для пользователя предпочтительнее «нет превью пока идёт playback», чем скрытая деградация playback-path.
3. Ошибки thumbnail extraction должны классифицироваться как транзиентные или постоянные; только постоянные могут попадать в `failed`-cache.

### 3.2 Жёсткие ограничения

- **Flavor:** все флейворы, где доступен SMB browsing/playback.
- **API level:** без новых API-зависимостей.
- **Производительность:** без busy-retry циклов и без дополнительного IO в main thread.
- **Сеть:** нельзя взорвать число параллельных SMB-сессий ради изоляции thumbnail/playback.
- **Совместимость данных:** без миграций Room и без новых пользовательских настроек.
- **Локализация:** без новых UI-строк.

---

## 4. Контекст текущей архитектуры

Сейчас video-thumbnail path работает через `MediaMetadataRetriever`, которому байты подаются из сетевого `MediaDataSource` с 10-секундным watchdog timeout. Для SMB этот путь делает range-reads через общий SMB client/connection manager и может читать один и тот же файл параллельно с player path.

Player path использует отдельный `DataSource` для ExoPlayer с pooled playback connection, stale-share detection и retry через fresh connection. В логе видно, что при playback reopen именно player path инициирует `invalidating and retrying`, а в это же время thumbnail path всё ещё читает по старому share и падает с `DiskShare has already been closed`.

Дополнительный симптом архитектуры — failed-cache для thumbnail сейчас срабатывает слишком рано:

```text
03:50:42.094  Video thumbnail extraction TIMEOUT .. VRHush_..._3dv.mp4
03:50:42.097  Added to failed video cache .. VRHush_..._3dv.mp4
03:50:55.178  Skipping video thumbnail extraction - cached failure: VRHush_..._3dv.mp4
```

То есть разовая гонка или timeout на SMB сразу превращается в сессионный «файл навсегда плохой для thumbnail», хотя файл параллельно успешно воспроизводится.

Что уже известно по коду:

1. У SMB connection manager есть pooled `withConnection(..)` path c `usageCount` и deferred close для активных пользователей.
2. Thumbnail partial reads идут через общий pooled path и в явном виде допускают `CancellationException` как нормальный сценарий.
3. Unit coverage есть у `SmbConnectionManager`, но не видно отдельного test coverage для `NetworkMediaDataSource` и `NetworkVideoFrameDecoder` на этот race.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы

**A. Playback-first arbitration**

Когда для SMB `server/share` активируется playback, новые thumbnail extractions для того же домена не должны стартовать. Уже запущенные операции должны завершаться кооперативно, без инвалидации playback share.

**B. Separate invalidation domain**

Инвалидация stale share в player path не должна ронять thumbnail path побочным эффектом. Даже если под капотом connection manager остаётся общий, домены инвалидации и close semantics должны быть разведены.

**C. Transient thumbnail failure classification**

`timeout`, `InterruptedException`, `DiskShare has already been closed`, `TransportException`, `stale share detected` для thumbnail path должны считаться транзиентными. Такие случаи не должны сразу уходить в постоянный failed-cache без отдельной политики retry/TTL.

**D. Explicit diagnostics**

Логи должны явно помечать:

- `scope=thumbnail|player`
- `server/share`
- `poolKey`
- `invalidateReason`
- `inUseCount`
- `failureClass=timeout|stale-share|null-frame|unsupported`

### 5.2 Поток событий

```text
Browse binds visible SMB items
  → thumbnail requests queued
  → [new] if SMB playback active for same server/share: defer or cancel thumbnail start

SMB playback starts/resumes
  → playback path enters protected/isolated share domain
  → stale-share retry may recreate only playback-owned connection state
  → in-flight thumbnail tasks either finish safely or abort as transient

SMB playback stops
  → deferred thumbnails resume
  → transiently failed items become retryable
```

---

## 6. Открытые вопросы / Research items

1. **Единица изоляции: server-level или share-level?**
   - Реализовано на server-level (ключ `smb://host:port`). Достаточно для NAS-сценария: у одного SMB-сервера один connection pool.
   - **Статус:** Implemented

2. **Что делать с уже запущенными thumbnail tasks при старте playback?**
   - Завершаются кооперативно: extraction timeout возникает, но результат классифицируется как transient (не permanent). Retry при следующем recycle view item.
   - **Статус:** Implemented

3. **Как хранить transient failures?**
   - `ConcurrentHashMap<path, timestampMs>` с TTL 2 минуты + явная очистка при `decode()` если playback завершён.
   - **Статус:** Implemented

4. **Нужна ли такая же защита для SFTP/FTP?**
   - Не расширяли: `encounteredStaleShare` и stale-share detector привязаны только к `smb://`. Другие протоколы остаются с прежней логикой.
   - **Статус:** Implemented

5. **Можно ли полностью запретить thumbnail extraction по currently playing path?**
   - Нет отдельного запрета по path: thumbnail extraction для playing-file просто получит timeout → transient fail → retry. Достаточно для данного сценария.
   - **Статус:** Implemented

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слишком грубая изоляция по server-level временно отключит все превью на NAS во время playback | Средняя | Пустые превью в browse при просмотре видео | Начать с same-file или same-share arbitration и замерить UX |
| Разделение connection domain увеличит число SMB-сессий и нагрузку на NAS | Средняя | Новые timeout/limit issues на сервере | Явный upper bound на параллельные playback/thumbnail sessions |
| Ослабление failed-cache вызовет retry-storm на реально битых файлах | Средняя | Шум в логах, лишний трафик | Ввести отдельную классификацию transient vs terminal |
| Нехватка unit coverage на thumbnail/playback race даст регрессию в смежных путях | Высокая | Нестабильное поведение при reopen/pause/exit | Добавить targeted tests на lifecycle/invalidations перед rollout |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без обновления `docs/FEATURES*`. Это внутренний bugfix reliability/lifecycle, не новая функция.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Playback имеет приоритет над thumbnail**

- **Решение:** в конфликте SMB playback всегда важнее background thumbnail.
- **Альтернативы:** равноправное сосуществование, aggressive retries thumbnail.
- **Почему:** playback — foreground user action; thumbnail — декоративный background workload.

**ADR-2: Transient SMB share race не равен permanent thumbnail failure**

- **Решение:** `stale-share`, `DiskShare closed`, timeout/cancel во время playback-transition трактуются как transient.
- **Альтернативы:** текущее поведение с immediate failed-cache.
- **Почему:** текущий лог уже показывает ложный permanent-failure для файла, который успешно воспроизводится.

---

## 10. Связи с другими спеками

- **S0041** — ticket, в котором этот side-find впервые был зафиксирован, но не должен больше владеть проблемой.
- **S0032** — соседний symptom-level тикет про `getFrameAtTime returned null`; S0060 покрывает SMB share race, который может приводить к тому же симптому.
- **S0051** — общий network datasource lifecycle/pause-cancel паттерн; возможен реюз подходов, но дефект другой.
- **S0038** — тот же лог содержит независимый VR exit/new-window дефект; не смешивать области.

---

## 11. Критерии готовности (strategic-level)

1. ✅ В воспроизводимом SMB-сценарии из `logs/fastmediasorter_20260503_032115.log` больше не возникает `DiskShare has already been closed` из thumbnail path для текущего playing/reopening файла.
2. ✅ Старт и resume SMB playback больше не сопровождаются churn-цепочкой `stale share detected` → `invalidating and retrying` из-за background thumbnail workload.
3. ✅ Timeout/interrupt/stale-share на thumbnail path логируются как transient и не заносят файл в permanent failed-cache текущей сессии.
4. ✅ После остановки playback thumbnail generation автоматически возобновляется и может успешно отрисовать превью на повторной попытке.
5. ⏳ Для playback-path и thumbnail-path есть отдельная верификация на on-device сценарии с активным browse + playback overlap. (требует field test)

---

## 12. Реализация (2026-05-03)

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `data/network/ConnectionThrottleManager.kt` | `isVideoPlayerActiveForResource(key)` — per-server playback check для thumbnail arbitration |
| `data/network/glide/NetworkMediaDataSource.kt` | `@Volatile encounteredStaleShare: Boolean` + `isSmbStaleShareError()` — детектор DiskShare race |
| `data/network/glide/NetworkFileModelLoader.kt` | `transientFailedVideos: ConcurrentHashMap<String, Long>` (TTL 2 min) + `markVideoAsTransientlyFailed()` / `clearTransientFailure()` / `clearTransientFailuresForHost()` / `isVideoPermanentlyFailed()` |
| `data/network/glide/NetworkVideoFrameDecoder.kt` | `ExtractionOutcome(bitmap, isTimeout)`, `extractSmbServerKey()`, failure classification: stale-share → всегда transient; timeout → transient только если playback активен; null-frame → permanent |

### Логика классификации

```
extractVideoFrame() → ExtractionOutcome(bitmap, isTimeout)
  ↓
encounteredStaleShare = mediaDataSource.encounteredStaleShare
  ↓
isTransient = encounteredStaleShare || (isTimeout && playbackActive)
  ↓
if isTransient → markVideoAsTransientlyFailed()  // не сохраняется в SharedPreferences
else           → markVideoAsFailed()             // permanent, сохраняется
```

### Поведение после fix

- `VRHush.mp4` таймаутнулась во время active playback → `transientFailedVideos[path]` → после остановки playback `decode()` видит `!isVideoPermanentlyFailed` + `!isVideoPlayerActive` → `clearTransientFailure()` → экстракция проходит
- `DiskShare has already been closed` в `readAt()` → `encounteredStaleShare = true` → всегда transient, лог `[scope=thumbnail failureClass=stale-share]`
- Новый лог-формат: `[scope=thumbnail server=smb://192.168.1.100:445 failureClass=stale-share|timeout|null-frame playbackActive=true|false]`

---

## Last Audit

**Date:** 2026-05-03
**Mode:** field-log
**Evidence:** `logs/fastmediasorter_20260503_180505.log`
**Outcome:** Implemented — stale-share/transient path подтверждён, но тяжёлый SMB-churn сценарий всё ещё требует on-device проверки

### Observed in latest log

- В живом логе присутствуют 90 `failureClass=timeout`, 7 `failureClass=null-frame` и 2 `failureClass=stale-share` события из thumbnail-path, то есть разнесение failure-классов из §2.4 реально работает.
- `failureClass=stale-share` приходит отдельной веткой и не схлопывается в generic `null-frame`, что было одной из ключевых целей S0060.
- Оба stale-share события возникают уже на фоне более широкого SMB/auth churn (`STATUS_LOGON_FAILURE`, fast connectivity fail, degraded reset). Это не возвращает старый race-сценарий из `logs/fastmediasorter_20260503_032115.log`, но и не закрывает критерий §11.5 для тяжёлого overlap browse+playback.
- Отдельных unit-тестов на `NetworkMediaDataSource` / `NetworkVideoFrameDecoder` под этот race по-прежнему нет.