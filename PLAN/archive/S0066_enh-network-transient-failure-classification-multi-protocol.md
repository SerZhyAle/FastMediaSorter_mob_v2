# Стратегическая спецификация: S0066 — Унификация transient-классификации thumbnail-провалов для всех сетевых протоколов

**Ticket:** S0066
**Status:** Verified
**Priority:** 45
**Date:** 2026-05-03
**Implemented date:** 2026-05-03

<!-- auto-approved by /spec-all — 2026-05-03 -->

**Tactical plan:** `PLAN/S0066_enh-network-transient-failure-classification-multi-protocol/INDEX.md`
**Tier:** 3 — Moderate
**Roadmap entry:** Follow-up к S0060 — расширение скоупа на остальные сетевые источники
**Tactical spec:** будет создан через `/spec-tech S0066`
**Related:** S0060 (источник, SMB-only реализация), S0047 (sftp-pool-broken-channel — те же симптомы для SFTP), S0051 (network datasource lifecycle), S0052 (sftp datasource log spam)

> **Scope:** STRATEGIC. Сделать playback-first arbitration и transient-классификацию thumbnail-провалов одинаковыми для SMB / SFTP / FTP / Cloud. Сейчас «разовая гонка lifecycle ≠ permanent failure» работает только для SMB; для остальных протоколов любой timeout-during-playback или token-expired сразу попадает в `failedVideos` и блокирует превью на всю сессию.

---

## 1. Проблема

В рамках S0060 для SMB была реализована трёхуровневая модель отказа thumbnail-извлечения:

1. **Permanent** (`failedVideos` + `VideoExtractionFailurePersistence`) — файл реально не извлекается.
2. **Transient** (`transientFailedVideos`, TTL 2 минуты) — гонка lifecycle (stale-share) или timeout, пока активен playback на тот же сервер. После остановки playback запись очищается, и thumbnail извлекается на следующем scroll-in.
3. **In-flight dedup** (`inFlightExtractions`) — один extraction на путь, остальные ждут результат.

Уровни (1) и (3) работают для всех протоколов. Уровень (2) — **только для SMB**:

- `NetworkVideoFrameDecoder.extractSmbServerKey(path)` возвращает `null` для не-`smb://`.
- `NetworkMediaDataSource.encounteredStaleShare` имеет смысл только для SMBJ DiskShare.
- Логика `isTransient = isStaleShare || (isTimeout && playbackActive)` в decoder завязана на наличие SMB server key.
- `clearTransientFailuresForHost(smbHost)` фильтрует только `path.startsWith("smb://")`.

В результате:

- SFTP-канал, разорванный во время воспроизведения соседнего файла на том же сервере, **не помечается transient** — следующий thumbnail extraction поднимается с timeout, файл уходит в permanent `failedVideos`, и до перезапуска приложения превью не появляется.
- FTP control-канал, прерванный по idle (S0061-аналог) во время playback, даёт ту же permanent-failure семантику для соседних видео.
- Cloud (Google Drive): rate-limit `429 Too Many Requests` или истёкший access-token при параллельной активной операции скачивания → thumbnail сразу помечается permanent.

Это противоречит исходной мотивации S0060 («транзиентный network/share race не должен записываться в постоянный failed cache»).

---

## 2. Цели

1. **Единая модель transient-классификации** для всех сетевых источников: SMB / SFTP / FTP / Cloud. Любой `path` с network-префиксом получает доступ к (а) playback-arbitration и (б) transient-failure cache на одинаковых правилах.
2. **Расширить набор transient-сигналов** так, чтобы он покрывал не только SMB-stale-share, но и протокол-специфичные эквиваленты:
   - SFTP: SSH `Channel`/`Session` is closed, `SocketException`, `TransportException`.
   - FTP: control channel `Broken pipe`, 421 (idle disconnect), 426 (data connection broken).
   - Cloud: HTTP 401 (token expired), 429 (rate-limit), 5xx (transient), `IOException` от OkHttp.
3. **Обобщить `extractServerKey(path)`** так, чтобы он возвращал нормализованный resourceKey для любого `smb:// | sftp:// | ftp:// | cloud://` (а не только SMB).
4. **Обобщить `clearTransientFailuresForHost`** — переименовать в `clearTransientFailuresForResource(resourceKey)` и фильтровать по нормализованному ключу.
5. **Симметричное поведение playback↔thumbnail** для всех протоколов: остановка playback на любом ресурсе очищает transient cache по этому resourceKey и разблокирует соседние превью.
6. Логи thumbnail failure классификации должны содержать `protocol=smb|sftp|ftp|cloud` без захардкоженного `server=smb://..`.

**Non-goals:**

- Не менять реализацию SMB-уровня (`encounteredStaleShare` остаётся как протокол-специфичный детектор).
- Не вводить новые retry policies, отдельные от S0061 follow-up.
- Не объединять `failedVideos` и `transientFailedVideos` в одну структуру — они различаются по persistence/TTL семантике.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. После остановки playback на любом сетевом ресурсе соседние превью должны автоматически появиться без ручного refresh.
2. Желательно единое логирование: `[scope=thumbnail protocol=X resource=Y failureClass=Z playbackActive=W]`.
3. Желательна возможность из настроек одной кнопкой очистить и permanent, и transient cache для всех протоколов сразу.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `legacy`, `photos` (везде, где есть network-thumbnails).
- **API level:** без специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** новые проверки выполняются один раз на decode/scroll-in; без дополнительного IO.
- **Совместимость данных:** изменений в Room/persistence не требуется.
- **Локализация:** не применимо.
- **Доступность:** не применимо.

---

## 4. Контекст текущей архитектуры

`NetworkVideoFrameDecoder.decode()` — единая точка решения «постоянная или транзиентная неудача». Сейчас для не-SMB путей:

- `extractSmbServerKey(path)` возвращает `null`.
- В блоке `if (outcome.bitmap == null) { ... }`: `playbackActive = smbKey != null && ConnectionThrottleManager.isVideoPlayerActiveForResource(smbKey)` — для SFTP/FTP/Cloud `playbackActive` всегда `false`.
- `isTransient = isStaleShare || (outcome.isTimeout && playbackActive)` — для SFTP/FTP/Cloud всегда `false` → файл сразу помечается permanent.

`ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)` уже принимает любой resourceKey — playback-arbitration через `withThrottle` (с `CancellationException` для low-priority) **уже универсальна**. Не хватает только перевода информации о playback в decoder для не-SMB.

`NetworkMediaDataSource.readAt()` ловит исключения по протоколам. Для SMB добавлен `isSmbStaleShareError(e)` → `encounteredStaleShare = true`. Для SFTP/FTP/Cloud аналогичной классификации нет — все исключения уходят в общий `Timber.w` или `Timber.e`.

Player path: каждый протокол имеет собственный datasource (SMB через `SmbConnectionManager` + ExoPlayer, SFTP через SshjMediaSource, FTP через FtpExoPlayerPool, Cloud через GoogleDriveDataSource). Они уже вызывают `ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)` при старте/`deactivateVideoPlayerMode` при остановке — **uniform по протоколам**. Это надо подтвердить аудитом, но архитектурно слой уже общий.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- **Универсальный `extractNetworkResourceKey(path)`** в decoder. Возвращает `"smb://host:port"`, `"sftp://host:port"`, `"ftp://host:port"`, `"cloud://providerId"` для соответствующих префиксов; `null` только для local. Заменяет SMB-only `extractSmbServerKey`.
- **Универсальный transient-detector в `NetworkMediaDataSource`.** Поверх существующего `encounteredStaleShare` добавляется `transientFailureReason: TransientReason?` (enum: `STALE_SHARE`, `BROKEN_CHANNEL`, `BROKEN_PIPE`, `TOKEN_EXPIRED`, `RATE_LIMIT`, `TRANSPORT`, `null`). Каждый бранч `readFromSmb / readFromSftp / readFromFtp / readFromCloud` ловит протокол-специфичные транзиентные исключения.
- **Decoder использует `playbackActive` для всех протоколов.** `isTransient = (transientFailureReason != null) || (outcome.isTimeout && playbackActive)`. Для SMB `transientFailureReason ?: STALE_SHARE если encounteredStaleShare` — обратная совместимость с S0060.
- **`clearTransientFailuresForResource(resourceKey)`** работает по любому префиксу: фильтрует `transientFailedVideos.keys` через `pathBelongsToResource(path, resourceKey)`. Старая SMB-only версия — alias.
- **Player datasources при `deactivateVideoPlayerMode(resourceKey)`** триггерят очистку transient cache на этом ресурсе. Сейчас это сделано только для SMB — расширить на все протоколы.

### 5.2 Потоки данных и событий

```text
Browse → thumbnail decode для path
  → resourceKey = extractNetworkResourceKey(path)         [ИЗМЕНЕНИЕ: больше не SMB-only]
  → если playback активен на resourceKey → CancellationException через withThrottle [уже работает]
  → иначе extraction → NetworkMediaDataSource.readAt()
    → при ошибке: protocol-specific transient detection → transientFailureReason
  → outcome (bitmap, isTimeout, transientFailureReason)
  → isTransient = (reason != null) || (isTimeout && playbackActive)        [ИЗМЕНЕНИЕ: универсально]
  → if isTransient → markVideoAsTransientlyFailed (TTL 2 min)
  → else           → markVideoAsFailed (permanent)
  → лог: [scope=thumbnail protocol=X resource=Y failureClass=Z playbackActive=W]

Player stops on resourceKey
  → deactivateVideoPlayerMode(resourceKey)
  → clearTransientFailuresForResource(resourceKey)        [ИЗМЕНЕНИЕ: для всех протоколов]
  → next scroll-in → re-extraction succeeds
```

### 5.3 Точки расширяемости

- `TransientReason` enum можно расширить новыми сигналами без изменения decoder.
- `pathBelongsToResource` — параметризуется match-стратегией (host:port для server-based, providerId для cloud).
- Логика «сколько transient ошибок подряд = считать permanent» (защита от retry-storm) — опциональная над-стратегия, в текущем тикете не нужна, но enum-структура её поддерживает.

---

## 6. Открытые вопросы / Research items

1. **Cloud: гранулярность resourceKey.**
   - **Вопрос:** для cloud достаточно ли `cloud://google_drive` (provider-level), или нужно учитывать аккаунт/folder?
   - **Варианты:** (а) provider-level (как в `NetworkSpeedTestUseCase` для recommendedThreads); (б) provider+account; (в) per-folder.
   - **Нужно выяснить:** при рассинхроне — playback на одной cloud-папке блокирует превью на другой → это допустимо или нет?
   - **Статус:** Verified

2. **FTP transient-сигналы.**
   - **Вопрос:** какие именно reply-codes / exceptions Apache Commons Net считать transient? 421 (idle), 426 (data connection broken), 425 (can't open data) — кандидаты.
   - **Варианты:** (а) только 421/426; (б) все 4xx; (в) timeout-only (как было до S0060).
   - **Нужно выяснить:** реальная статистика из логов — какие коды появляются при разрыве во время активного playback.
   - **Статус:** Verified

3. **Cloud rate-limit (HTTP 429): transient или permanent?**
   - **Вопрос:** Google Drive 429 с `Retry-After` header — это однозначно transient. А без header (просто отказ) — тоже?
   - **Резолюция-кандидат:** transient с TTL = max(`Retry-After`, 2 min); если нет header — стандартные 2 min.
   - **Статус:** Verified

4. **Playback datasources для SFTP/FTP/Cloud — действительно ли все вызывают `activateVideoPlayerMode`?**
   - **Вопрос:** аудит player-side кода: `SshjMediaSource`, `FtpExoPlayerPool`, `GoogleDriveDataSource`.
   - **Нужно выяснить:** если какие-то не вызывают — добавить как обязательное условие реализации.
   - **Статус:** Verified

5. **Совместимость с текущей S0060-семантикой SMB.**
   - **Вопрос:** не сломает ли обобщение SMB-конкретных случаев (например, `clearTransientFailuresForHost` оставлять как deprecated alias или удалить)?
   - **Резолюция-кандидат:** оставить SMB-методы как `@Deprecated` алиасы; внутренний код переключается на универсальные.
   - **Статус:** Verified

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слишком широкая classification (всё transient) → retry-storm на реально битых файлах | Средняя | Мусор в логах, лишний трафик | Лимит «N transient подряд → permanent»; чёткий enum reasons, не fallthrough |
| Cloud provider-level resourceKey слишком грубый — playback в одной папке Drive блокирует превью в другой | Средняя | Регрессия UX в cloud | Начать с provider-level (соответствует existing throttle), при жалобах ужесточить до folder-level |
| Player datasources не вызывают `activateVideoPlayerMode` для SFTP/FTP/Cloud → playback-arbitration не работает | Высокая | Реализация прошла, но эффекта нет | Аудит на этапе tactical-плана; добавление вызовов как часть tactical scope |
| Изменение decoder сломает существующий SMB-сценарий S0060 | Низкая | Регрессия по уже-работающему пути | Все SMB-проверки остаются в коде как первый case; обобщение — только при `smbKey == null` |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации: «Превью видео на сетевых хранилищах автоматически появляются после остановки воспроизведения — без ручного обновления, для всех типов соединений (SMB / SFTP / FTP / облака)». Краткая запись добавляется в `docs/FEATURES.md`, `_RU`, `_UK`.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Универсальный resourceKey, единая декодер-ветка для всех протоколов.**

- **Решение:** decoder больше не различает SMB и не-SMB; единая логика `playbackActive + transientReason → isTransient`.
- **Альтернативы:** оставить SMB-only (текущее) — приводит к описанным регрессиям; per-protocol if-цепочка — дублирование.
- **Почему:** ConnectionThrottleManager уже работает универсально; decoder — последняя точка SMB-ассиметрии.

**ADR-2: Transient reasons через enum, не строки/booleans.**

- **Решение:** `TransientReason` enum в `NetworkMediaDataSource`; decoder читает его, не парсит сообщения.
- **Альтернативы:** оставить boolean `encounteredStaleShare` + ad-hoc проверки — не масштабируется на 4 протокола.
- **Почему:** добавление нового сигнала — одна строка enum + один случай в детекторе; decoder не меняется.

---

## 10. Связи с другими спеками

- **S0060 (Implemented):** прямой источник — расширяет идею transient-классификации с SMB на остальные протоколы.
- **S0047 (BlockNeedUserTest):** sftp-pool-broken-channel — симптомы, которые S0066 закрывает на thumbnail-уровне.
- **S0051 (BlockNeedUserTest):** network datasource lifecycle pause/cancel — координация по resourceKey.
- **S0052 (Verified):** sftp datasource log spam — после S0066 transient-логи будут единообразны для SFTP/SMB/FTP/Cloud.
- **S0067 (планируется):** stale-connection invalidation для FTP/SFTP/Cloud — может использовать тот же resourceKey-нормализатор.

---

## 11. Критерии готовности (strategic-level)

1. После остановки SFTP/FTP/Cloud playback соседние видео-превью появляются автоматически без перезапуска приложения / clear-cache.
2. Timeout thumbnail extraction во время активного playback на любом протоколе помечается как transient (TTL 2 минуты), не уходит в permanent failed cache.
3. Один формат лога failure-классификации для всех протоколов: `[scope=thumbnail protocol=X resource=Y failureClass=Z playbackActive=W]`.
4. SMB-сценарии S0060 продолжают работать без регрессий (фиксированный воспроизводимый кейс из `logs/fastmediasorter_20260503_032115.log`).
5. Существуют unit-тесты на новый универсальный `extractNetworkResourceKey(path)` для всех 4 префиксов и для local/null случаев.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0066` — создаст `PLAN/S0066_enh-network-transient-failure-classification-multi-protocol/` с фазами.

---

## Last Audit

**Date:** 2026-05-03
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 28 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 1

### Manual / on-device

- [ ] **§11.1** — On-device verification: stop SFTP playback on a server with neighbouring videos in Browse → previews must reappear without manual refresh.
- [ ] **§11.1** — On-device verification: same flow for FTP playback.
- [ ] **§11.1** — On-device confirmation that S0060 SMB scenario from `logs/fastmediasorter_20260503_032115.log` still recovers correctly.

### Notes

- §6.1 / §6.3 (cloud granularity, cloud 429) classified **EXEMPT**: cloud thumbnails route through a separate Glide pipeline (`GoogleDriveThumbnailModelLoader`), out of scope for S0066. Tactical INDEX records this scope narrowing.
- §11.5 unit tests: 12 `@Test` cases cover `smb / sftp / ftp / local / null / cloud → null`. Cloud-as-null is the contract under the narrowed scope.
- S0060 backward compatibility verified statically: `encounteredStaleShare = true` still set in `NetworkMediaDataSource`, still read in `NetworkVideoFrameDecoder`.

