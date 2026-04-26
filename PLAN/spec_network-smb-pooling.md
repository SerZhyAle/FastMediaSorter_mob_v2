# Стратегическая спецификация: Ad-hoc — SMB Playback Reliability: Pooling, Watchdog Recovery, Retry Semantics

**Status:** Verified
<!-- auto-approved by /spec-all — 2026-04-26 -->
**Audit:** see `PLAN/spec_network-smb-pooling__audit_2026-04-26.md`
**Date:** 2026-04-26
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-26 (из Quest 3 VR problem list: P2-5 SMB playback fails with watchdog timeout / errorCode=2000, но причина лежит в сетевом слое и должна быть вынесена в отдельную спецификацию).
**Tactical spec:** `PLAN/spec_network-smb-pooling/` (будет создан через `/spec-tech`)

> **Scope of this document:** STRATEGIC. Цели, ограничения, риски и направление решения. Без детальной разбивки по классам/строкам/фазам реализации — это будет в тактической спецификации.

---

## 1. Проблема

SMB-воспроизведение в ExoPlayer периодически зависает на открытии файла и затем падает с `errorCode=2000`, хотя пользователь ожидает либо нормальный старт потока, либо быстрый и понятный recovery. Проблема проявилась в VR-сессии Quest 3, но сама по себе не относится к VR-рендерингу: симптом рождается в слое SMB pooling + ExoPlayer data source.

Текущая реализация уже содержит защиту в виде watchdog в `SmbDataSource.open()` и отдельного PLAYER-pool пути в `SmbConnectionManager`, но этого недостаточно. Протухшее TCP-соединение может пережить проверку валидности, вызвать блокировку на `openFile()`, быть инвалидировано, а затем повторный старт чтения всё равно падает на позиции 0. В результате пользователь получает повторные ошибки вместо одного надёжного self-healing сценария.

---

## 2. Цели

1. ExoPlayer path для SMB использует только свежие или подтверждённо живые PLAYER-соединения.
2. После watchdog timeout выполняется один предсказуемый recovery path: invalidation stale connection, чистый reconnect, повторное открытие файла и понятный fail-fast при повторном сбое.
3. Повторный retry не должен зависать на тех же внутренних SMBJ cache-сущностях, что и первая попытка.
4. Ошибки SMB playback должны классифицироваться так, чтобы UI получал корректное сообщение: retryable vs non-retryable.
5. Логи должны позволять отличить stale pooled connection, dead SMBJ cached connection, network loss и auth/config error.

Non-goals:

- VR stereo routing, XR session lifecycle и любые OpenXR-изменения.
- Оптимизация throughput/скорости SMB-стриминга как таковой, если playback уже стабилен.
- Полный рефактор всего сетевого стека (FTP/SFTP/Dropbox/Drive) — только SMB playback path.
- Изменение UX VR-плеера; максимум допустимы более точные ошибки/retry hints.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Спека должна закрыть именно «подвисшую» проблему P2-5 из [PLAN/spec-list-vr-problems.md](PLAN/spec-list-vr-problems.md), чтобы она не оставалась сиротой между VR и network backlog.
2. Recovery должен быть быстрым: пользователь не должен ждать десятки секунд второго зависания после первого watchdog timeout.
3. При невозможности recovery система должна падать явно и один раз, а не делать серию непрозрачных повторов с одинаковым `errorCode=2000`.

### 3.2 Жёсткие ограничения

- **Flavor:** затрагивает `standard`, `lite`, `legacy`, `vr`; `photos` не затронут, так как в нём нет видео playback path.
- **API level:** без значимых Android API fork'ов; проблема лежит в SMBJ/client/network code, а не в Android framework API.
- **Wear OS:** не затрагивается.
- **Архитектура:** heavy logic остаётся вне Activity; изменения живут в `data/network/` и player helpers.
- **Логирование:** только `Timber`, без `Log.d()`.
- **Совместимость:** существующий browse/scanner SMB path не должен деградировать из-за playback-oriented правок.
- **LOC budget:** `SmbConnectionManager.kt` находится на отметке ровно 1000 строк — любые добавления туда требуют предварительной экстракции или компенсирующего сокращения.

---

## 4. Контекст текущей архитектуры

`SmbConnectionManager` уже разделяет pooled connections по consumer type (`SCANNER` / `PLAYER`) и прямо документирует, что ExoPlayer не должен переиспользовать scanner-соединение, потому что оно может быть тихо убито NAS между scan и playback. Для ExoPlayer существует отдельный синхронный путь `getConnectionForExoPlayer()`, который пытается переиспользовать только PLAYER entry либо создать fresh connection.

`SmbDataSource` оборачивает `open()` и `read()` в watchdog executor. При таймауте `open()` он инвалидирует ExoPlayer pooled connection через `invalidateExoPlayerConnection()` и выбрасывает `IOException`, чтобы ExoPlayer вышел из бесконечного buffering. Внутри `openInternal()` уже есть retry на `openFile()` при broken pipe / transport error.

**Известный пробел (code review 2026-04-26):** `SmbDataSource.reopenConnection()` — метод, вызываемый при EOF / protocol error во время `read()` — создаёт собственный `SMBClient` и устанавливает соединение в обход `SmbConnectionManager`. Это означает, что pool manager не знает об этом соединении, а сам retry может утереться в то же мёртвое TCP-состояние. Исправление входит в scope данной спецификации.

**Подтверждено code review:** `invalidateExoPlayerConnection()` вызывает `share.close()` / `session.close()` / `connection.close()`, что удаляет Connection из SMBJ-внутреннего `connectionTable`. Дополнительный purge или отдельный `SMBClient` для cache-сброса не требуется.

**Также подтверждено:** после watchdog timeout на `open()` или `read()` ExoPlayer создаёт новый `SmbDataSource` instance и снова вызывает `open()`. В этом новом instance нет памяти о предыдущем timeout — механизм fail-fast для «двойного watchdog» отсутствует.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

#### Столп A — Явная модель playback-connection lifecycle

PLAYER path получает собственную, более явную модель состояний: `fresh`, `validated`, `suspect`, `invalidated`. После watchdog timeout или transport error connection не просто удаляется из map, а переводится в состояние, которое исключает любое повторное использование тем же playback session.

#### Столп Б — Чистый reconnect после watchdog

Recovery path после `SmbDataSource.open` timeout должен гарантированно рождать новый TCP socket и новый SMB session/share, а не полагаться только на эвикт локального pool entry. Если для этого потребуется более жёсткий purge SMBJ-internal cache или отдельный playback-only client instance, это должно быть формализовано как часть решения.

#### Столп В — Retry semantics для ExoPlayer

Нужно разделить три сценария:

1. `openFile()` transport failure на первой попытке — silent reconnect и один retry.
2. watchdog timeout на open/read — invalidate + one clean retry.
3. повторный провал после clean retry — немедленный fail-fast с понятной классификацией, без бесконечной карусели `position=0`.

#### Столп Г — Отдельная диагностика причин падения

Сетевой слой должен различать как минимум:

- dead pooled PLAYER connection;
- stale SMBJ internal connection cache;
- реальную потерю сети;
- auth/share/path problems;
- медленный, но живой NAS.

Это позволит не только чинить retry path, но и выдавать адекватные сообщения пользователю и будущим спекам.

### 5.2 Потоки данных и событий

```text
VideoPlayerManager.playSmbVideo
    ↓
SmbDataSource.open
    ↓
SmbConnectionManager.getConnectionForExoPlayer
    ↓
PLAYER pooled connection or fresh connect
    ↓
openFile / fileInformation / read
    ├─ success → playback continues
    ├─ transport error → invalidate stale entry → clean reconnect → retry once
    └─ watchdog timeout → purge playback connection state → retry policy decision → fail-fast if second attempt fails
```

### 5.3 Точки расширяемости

- В будущем тот же recovery contract можно применить к SFTP/FTP streaming, но текущая спека ограничена SMB.
- Диагностическая классификация может стать общей частью network error reporting, если покажет ценность.

---

## 6. Открытые вопросы / Research items

1. **Достаточно ли закрытия `Connection` для purge SMBJ internal cache?**
   - **Ответ:** Да. `SmbConnectionManager.invalidateExoPlayerConnection()` уже вызывает `connection.close()`, который удаляет запись из SMBJ `connectionTable`. Отдельный `SMBClient` не требуется.
   - **Статус:** CLOSED (code review 2026-04-26)

2. **Где должен жить retry budget — в `SmbDataSource` или выше?**
   - **Ответ:** Retry budget живёт в `SmbDataSource.openInternal()` (1 retry при transport error на `openFile`). Watchdog timeout не делает retry — выбрасывает `IOException`. ExoPlayer делает retry на своём уровне. Fail-fast на «двойной watchdog» реализуется через `SmbPlaybackConnectionTracker` (singleton, разделяемый между DataSource instances).
   - **Статус:** CLOSED (code review 2026-04-26)

3. **Нужен ли playback-only client/pool?**
   - **Ответ:** Нет. `ConnectionConsumer.PLAYER` separation уже обеспечивает изоляцию. SMBJ cache purge работает через `connection.close()`. Дополнительный `SMBClient` не нужен.
   - **Статус:** CLOSED (code review 2026-04-26)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
| ---- | :---------: | ----------- | --------- |
| Слишком агрессивный purge будет чаще пересоздавать SMB connections | Средняя | Больше latency на старт playback | Делать purge только на watchdog/transport failures, а не на каждый open |
| Исправление playback path случайно сломает scanner/listing reuse | Средняя | Регресс browse по SMB | Явно изолировать PLAYER contract от SCANNER contract |
| Непрозрачный retry в нескольких слоях приведёт к дублированным попыткам | Высокая | Повторные подвисания и неясные логи | Зафиксировать один owner retry policy в tactical spec |
| Проблема на самом деле в NAS/network loss, а не в pooling | Средняя | Частичное решение | Добавить классификацию причин и error telemetry |

---

## 8. Влияние на пользователя (docs/FEATURES)

No FEATURES doc update required.

Это не новая пользовательская функция, а повышение надёжности уже существующего SMB playback path.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Проблема P2-5 выносится в отдельную network spec, а не остаётся non-goal в VR spec.**

- **Решение:** создать отдельную стратегическую спецификацию для SMB pooling/playback reliability.
- **Альтернативы:** оставить как single-line non-goal в VR-документе; вписать в общий сетевой backlog без отдельной спеки.
- **Почему так:** проблема уже подтверждена логами и влияет на реальное воспроизведение. Без отдельной спеки у неё нет owner'а, scope и критерия готовности.

**ADR-2: Исправление должно быть playback-specific, а не общей «магической» стабилизацией SMB слоя.**

- **Решение:** проектировать решение вокруг ExoPlayer/`SmbDataSource` lifecycle.
- **Альтернативы:** глобально ослабить таймауты или полностью переписать весь SMB pooling слой.
- **Почему так:** проблема проявляется на стыке playback retry + pooled PLAYER connection; слишком общий scope размоет задачу.

---

## 10. Связи с другими спеками

- [PLAN/spec-list-vr-problems.md](PLAN/spec-list-vr-problems.md) — первичный источник дефекта P2-5.
- [PLAN/spec_vr-input-reliability.md](PLAN/spec_vr-input-reliability.md) — явно выводит P2-5 за пределы VR scope; эта спека закрывает образовавшуюся дыру.
- Будущая тактическая спека должна при необходимости связаться с network catalog / connection-pool refactor work, но не зависеть от VR docs.

---

## 11. Критерии готовности (strategic-level)

1. Открытие SMB-видео не застревает навсегда на stale pooled connection.
2. После watchdog timeout выполняется максимум один clean retry с новым playback connection lifecycle.
3. Повторный провал после clean retry даёт однократную и понятную ошибку, а не серию повторных `errorCode=2000`.
4. Логи различают stale pool, dead cached connection и network/auth/path error.
5. Browse/scanner SMB flow не регрессирует.
6. `SmbDataSource.reopenConnection()` использует `SmbConnectionManager.getConnectionForExoPlayer()` вместо прямого создания `SMBClient`.

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — перейти к `/spec-tech network-smb-pooling`, чтобы создать `PLAN/spec_network-smb-pooling/` с фазами реализации, точными файлами и проверками.
