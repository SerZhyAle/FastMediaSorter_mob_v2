<!-- auto-approved by /spec-all — 2026-05-03 -->

# Стратегическая спецификация: S0067 — Унификация health-проб и single-retry для FTP / SFTP / Cloud

**Ticket:** S0067
**Status:** Implemented
**Priority:** 60
**Date:** 2026-05-03
**Implemented:** 2026-05-03
**Tier:** 3 — Moderate
**Roadmap entry:** Follow-up к S0061 — обобщение SMB-only liveness-gate на остальные сетевые/облачные источники
**Tactical spec:** [`PLAN/S0067_enh-network-stale-connection-invalidation-multi-protocol/INDEX.md`](S0067_enh-network-stale-connection-invalidation-multi-protocol/INDEX.md)
**Related:** S0061 (источник, SMB-only реализация), S0047 (sftp-pool-broken-channel — частично пересекается), S0025 (smb-fast-fail), S0046 (sftp-key-auth-hardening)

> **Scope:** STRATEGIC. Распространить «health-проба перед арендой + single-retry на уровне менеджера + закрытие соединений при уходе в фон» с SMB на FTP, SFTP и Cloud (Google Drive / OneDrive / Dropbox). Сейчас перечисленные классы проблем (server-side TCP FIN, idle-disconnect FTP control-channel, истёкший access-token, NAT-table close после паузы) воспроизводятся на каждом протоколе со своей сигнатурой, но восстановление сделано только для SMB.

---

## 1. Проблема

S0061 (Implemented) решил для SMB:

- Health-проба `isAlive()` на pooled connection до session-setup.
- Single-retry в `SmbConnectionManager` через инвалидцию pooled-объекта и открытие свежего.
- Закрытие SMB-сессий при уходе UI в фон (`ProcessLifecycleOwner.onStop()`); фоновый worker сохраняет соединения.
- Структурированный лог `connection marked dead reason=X — recreating` вместо stack-trace.

Аналогичные сценарии воспроизводятся на других протоколах, но восстановление либо отсутствует, либо реализовано ad-hoc внутри каждого клиента:

- **FTP control channel** закрывается сервером после `IDLE_TIMEOUT` (90..300 с в типичной vsftpd/ProFTPD конфигурации). На первой команде после простоя — `Broken pipe` / `425 Can't open data connection` / `421 Service not available`. `FtpClient` сейчас умеет ловить `IOException` и переоткрыть соединение, но проверка живости только реактивная (на ошибке), не превентивная. Аналог `withConnection` обёртки, как у SMB, нет.
- **SFTP/SSH** канал закрывается NAT-таблицей при простое; `ServerAliveInterval` в SSHJ не настроен по умолчанию. На первой операции — `TransportException: Channel is closed` или `SocketException`. `SftpClient` пытается переоткрыть, но S0047 (BlockNeedUserTest) фиксирует, что pool-логика всё ещё ловит broken-channel и каскадирует ошибки.
- **Cloud (Google Drive / OneDrive / Dropbox)** — access-token истекает (Drive/OneDrive 1 ч, Dropbox 4 ч). На первой операции после паузы — HTTP 401 (`invalid_grant` / `token expired`). Сейчас обработка делается ad-hoc внутри каждого `*RestClient`: где-то рефреш сделан, где-то нет. Нет единой политики «один retry на token-refresh».

В итоге пользователь, переключающийся между ресурсами или уходящий в фон на пару минут, может получить:

- На SFTP: «Channel is closed» 1-2 раза подряд, потом восстановление.
- На FTP: «Broken pipe» каскадом для всех соседних файлов в той же сессии (как в исходном S0061-логе для SMB, до фикса).
- На Cloud: 401-ошибку без рефреша → требование повторного логина из настроек, хотя refresh-token валиден.

---

## 2. Цели

1. **Единая liveness/retry-абстракция** поверх per-protocol менеджеров. Ввести интерфейс `NetworkConnectionGate` (или развить существующий `ConnectionConsumer` подход), который:
   - выполняет health-пробу до выдачи соединения;
   - удаляет мёртвый объект из пула атомарно;
   - предоставляет single-retry политику для потребителя;
   - различает UI-сессию и `BACKGROUND_WORKER` для lifecycle-разрыва.
2. **Per-protocol адаптеры**:
   - `FtpConnectionGate` — `noop()` команда вместо записи в socket; на ошибке инвалидцию pooled `FTPClient`.
   - `SftpConnectionGate` — `Session.isOpen` + `Channel.isOpen` + lightweight `OpenSSHClient.isConnected()`; recreate `SshClient` при провале.
   - `CloudConnectionGate` — проверка expiry timestamp access-token; preemptive refresh, если осталось < 60 с; на 401 — single retry с принудительным refresh.
3. **Lifecycle-хук через `ProcessLifecycleOwner`** для FTP/SFTP/Cloud — текущая SMB-реализация переиспользуется (один observer обходит все active gates).
4. **Структурированный лог** `[scope=connection protocol=X resource=Y reason=Z action=recreate|refresh|fail]` — тот же формат, что в S0061.
5. **Сосуществование с существующими per-protocol fast-fail** (S0025) — gate не дублирует timeout-логику, а опирается на неё.
6. **Метрика количества переподключений в сессию** — для всех протоколов; единичный snackbar по порогу 3+ за 5 минут.

**Non-goals:**

- Не реализовывать собственный refresh-token механизм для cloud-провайдеров — использовать SDK-нативные.
- Не вводить keepalive-таймеры для уменьшения частоты idle-disconnect (это отдельный вопрос).
- Не менять политику thumbnail / playback arbitration (S0066).
- Не переделывать аутентификацию SFTP key-based (S0046).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. После 2+ минут простоя любая операция (копирование / playback / листинг / cloud-листинг) восстанавливается прозрачно — максимум один внутренний retry.
2. Один и тот же текст лога для всех протоколов: `connection X marked dead reason=Y — recreating`.
3. Видимый сигнал для пользователя при аномально частых переподключениях (≥ 3 за 5 минут на один ресурс).
4. Cloud token expiry не должен требовать повторного логина из настроек, если refresh-token валиден.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `legacy`. Cloud — только `standard` (флейвор разрешает CLOUD).
- **API level:** без специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** health-проба амортизируется через cache «время последней успешной операции» — выполняется только если простой ≥ 30 секунд.
- **Совместимость данных:** изменений в Room/persistence не требуется.
- **Локализация:** EN/RU/UK для редкого snackbar «сеть нестабильна — проверьте Wi-Fi» (уже есть из S0061).
- **Доступность:** не применимо.

---

## 4. Контекст текущей архитектуры

`SmbConnectionManager` (после S0061) — образцовая реализация:

- pooled `Connection` с `usageCount`, `lastSuccessTimestamp`, `markedForClose`.
- `healthProbe.isAlive(connection)` вызывается до `authenticate()` если `timeSinceLastSuccess > IDLE_HEALTH_RECHECK_MS`.
- `withConnection { .. }` обёртка обеспечивает single-retry с гарантированно свежим объектом.
- `invalidateExoPlayerConnection(info)` — атомарно удаляет из пула.
- `FastMediaSorterApp` подписывается на `ProcessLifecycleOwner.onStop()` → `closeAllConnections()` для UI-теггированных; worker-теггированные не трогает.

Остальные менеджеры:

- `FtpClient` — пул внутренних `FTPClient` объектов через `FtpExoPlayerPool`. Health-проверка реактивная (`isConnected` после ошибки). Lifecycle-хука нет — соединения живут до явной ошибки или `releaseConnection`.
- `SftpClient` — pool через `SshjConnectionPool` (см. S0046). Health-проверка частичная: `session.isOpen` есть, но не вызывается до session-setup. S0047 фиксирует, что broken-channel прорывается в потребителя.
- Cloud (`GoogleDriveRestClient`, `OneDriveRestClient`, `DropboxRestClient`) — каждый держит OkHttp + access-token. Refresh-логика разрозненная, общий gate отсутствует.

`ConnectionThrottleManager` — это slot/concurrency manager, а не connection lifecycle. Его функции (degraded state, recommendedThreads) ортогональны health-пробе. Gate-абстракция должна жить рядом, не внутри ThrottleManager.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- **Интерфейс `NetworkConnectionGate<C>`** в `data/network/lifecycle/`:
  - `acquire(consumerType): C` — возвращает гарантированно живое соединение; внутри: health-проба для pooled, иначе создаёт новое.
  - `release(c, success: Boolean)` — на success — обновляет `lastSuccessTimestamp`; на failure — инвалидирует.
  - `<R> withRetry(consumerType, op: suspend (C) -> R): R` — single-retry политика: первая попытка → если транзиентный сбой → одна повторная с гарантированно новым соединением → иначе финальная ошибка.
  - `closeFor(consumerType: ConsumerType)` — закрывает все соединения данного типа (для lifecycle-хука).
- **Per-protocol реализации:**
  - `SmbConnectionGate` — обёртка над существующим `SmbConnectionManager` (фактически сводится к адаптации API к новому интерфейсу; внутренняя логика S0061 уже корректна).
  - `FtpConnectionGate` — health-проба через `FTPClient.sendCommand("NOOP")` или проверку `isConnected` без обращения к сокету; recreate через `FtpExoPlayerPool.invalidate(key)`.
  - `SftpConnectionGate` — health-проба через `SshClient.isConnected()` + `Session.isOpen()`; на провал — `SshjConnectionPool.invalidate(key)`.
  - `CloudConnectionGate<provider>` — health-проба через `accessTokenExpiresAt - now < margin`; recreate через SDK-native refresh; single-retry на 401.
- **`ConsumerType` enum:** `UI_SCANNER`, `UI_PLAYER`, `UI_OPERATION`, `BACKGROUND_WORKER`. Расширяет существующее SMB-разделение.
- **`NetworkLifecycleObserver`** в `core/lifecycle/` — `LifecycleEventObserver` на `ProcessLifecycleOwner`; на `ON_STOP` обходит все зарегистрированные gates и вызывает `closeFor(UI_*)`. На `ON_START` — ничего не делает (lazy reconnect).
- **`ConnectionDiagnostics`** — единый канал лога с форматом `[scope=connection protocol=X resource=Y reason=Z action=W]` + счётчик переподключений per-resource per-window.

### 5.2 Потоки данных и событий

```text
Consumer requests operation on resource R, protocol P, type T
  → gate = registry.gateFor(P)
  → gate.withRetry(T) { c ->
      → acquire(T): pooled or new connection с health-пробой
      → operation(c)
      → success → release(c, true)
    }
  → если ошибка transient (broken-pipe / channel closed / 401 / NAT-FIN):
    → release(c, false) [инвалидирует]
    → второй вызов acquire(T) → гарантированно новый connection
    → повторный operation
  → если снова ошибка → ConsumerError → потребитель видит «сеть недоступна»

Process lifecycle ON_STOP:
  → NetworkLifecycleObserver обходит все registered gates
  → для каждого gate.closeFor(UI_SCANNER), closeFor(UI_PLAYER), closeFor(UI_OPERATION)
  → BACKGROUND_WORKER не трогается

Process lifecycle ON_START:
  → ничего не делается; consumers сами поднимут соединения по необходимости

Diagnostics:
  → каждый recreate / refresh / fail инкрементит счётчик per-resource (sliding 5-min window)
  → счётчик ≥ 3 → SmbResetCallback (или общий ConnectionInstabilityCallback) → snackbar один раз
```

### 5.3 Точки расширяемости

- Новый протокол → реализация `NetworkConnectionGate<C>` + регистрация в `ConnectionGateRegistry`. Lifecycle-хук и diagnostics получаются автоматически.
- Стратегия health-пробы — параметризуется per-gate (cheap socket check vs heavyweight noop).
- Политика «когда показать snackbar» — централизована в `ConnectionDiagnostics`.

---

## 6. Открытые вопросы / Research items

1. **Унификация vs минимально-инвазивное расширение.**
   - **Вопрос:** ввести единый `NetworkConnectionGate` интерфейс и переписать `SmbConnectionManager` под него? Или оставить SMB как есть, написать только FTP/SFTP/Cloud gates с похожим API без формального общего интерфейса?
   - **Варианты:** (а) полная унификация; (б) duck-typed одинаковый API без интерфейса; (в) Smb как образец, остальные — copy-paste с правками.
   - **Резолюция-кандидат:** (а) — даёт единый lifecycle-хук и единый diagnostics-канал «бесплатно».
   - **Статус:** Verified

2. **Lifecycle для cloud: закрывать ли соединения в фоне?**
   - **Вопрос:** OkHttp-клиент cloud-провайдера — стоит ли его закрывать при `onStop`?
   - **Варианты:** (а) да, как для SMB; (б) нет — OkHttp connection pool сам управляется и timeout идёт от провайдера; (в) только если активной cloud-операции нет.
   - **Резолюция-кандидат:** (б) — OkHttp pool уже эффективен, форсированное закрытие может ломать длинные uploads/downloads. Для cloud lifecycle-хук важен только для `accessToken refresh`, не для сокетов.
   - **Статус:** Verified

3. **FTP NOOP vs пассивная проверка.**
   - **Вопрос:** `FTPClient.sendCommand("NOOP")` — это один round-trip к серверу. Стоит ли его делать или достаточно `isConnected` (проверка только локального state)?
   - **Варианты:** (а) NOOP всегда; (б) NOOP только если простой > 60 с; (в) только `isConnected`.
   - **Резолюция-кандидат:** (б) — компромисс между точностью и стоимостью.
   - **Статус:** Verified

4. **Координация с S0066 transient-classification.**
   - **Вопрос:** transient-сигналы из gate (recreate fired) и из decoder (transientFailureReason) — связаны?
   - **Резолюция-кандидат:** связаны: если gate сделал recreate во время thumbnail-операции, decoder получает сигнал от gate-метаданных, не от exception parsing. Это уменьшает дубль-классификацию.
   - **Статус:** Verified

5. **Gate для local file system?**
   - **Вопрос:** имеет ли смысл local-gate для единообразия?
   - **Резолюция-кандидат:** нет — local не имеет lifecycle/health-семантики; local gate стал бы no-op обёрткой и добавил ничего, кроме индирекции.
   - **Статус:** Verified

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Полная унификация требует рефакторинга существующего работающего SmbConnectionManager → регрессия | Средняя | Поломка S0061 | Поэтапный rollout: новый интерфейс + adapter поверх SMB; SMB-сценарии остаются нетронутыми до момента переключения |
| Cloud-gate сломает существующие OkHttp pool / token refresh механизмы провайдеров | Средняя | Cloud auth-сбои | Cloud-gate работает только на уровне «когда вызвать refresh»; не пересоздаёт OkHttp client целиком |
| FTP NOOP добавляет round-trip и замедляет горячий путь | Низкая | Замеренное падение throughput листинга | Включать NOOP только при `idle > 60s` |
| Lifecycle-хук закрывает соединение в момент длительной cloud-операции (download 100 MB) | Низкая | Прерванная операция | Background tasks всегда тег `BACKGROUND_WORKER` — не трогаются |
| Diagnostics snackbar становится шумным | Низкая | Раздражение пользователей | Sliding 5-минутное окно, один tost на window-violation |
| Скоуп slipped — реализация затронет playback datasources, аутентификацию, retry policies | Высокая | Сроки, риски регрессий в playback | Жёсткий non-goals лист в §2; не объединять с S0066 в один PR |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации: «Соединение к FTP/SFTP/облачным хранилищам автоматически восстанавливается после простоя — копирование, воспроизведение и листинг работают без перезапуска приложения и без повторного логина». Краткая запись добавляется в `docs/FEATURES.md`, `_RU`, `_UK`.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Единый `NetworkConnectionGate` интерфейс vs duck-typed API.**

- **Решение:** ввести интерфейс. Per-protocol реализации регистрируются в `ConnectionGateRegistry`.
- **Альтернативы:** оставить per-protocol менеджеры с похожими методами без общего интерфейса.
- **Почему:** lifecycle-хук и diagnostics получают единый entry-point; добавление нового протокола (например, WebDAV) — одна реализация без правки lifecycle/diagnostics.

**ADR-2: Cloud lifecycle-хук — token-only, не socket-level.**

- **Решение:** для cloud при `onStop` не закрывается OkHttp pool; gate отвечает только за preemptive refresh access-token при необходимости.
- **Альтернативы:** агрессивное закрытие как для SMB.
- **Почему:** OkHttp pool сам управляется; форсированное закрытие ломает длинные uploads. Реальная проблема cloud — token expiry, не socket lifecycle.

**ADR-3: Health-проба активна только после порога простоя.**

- **Решение:** проба выполняется только если `now - lastSuccess > IDLE_HEALTH_RECHECK_MS` (порог 30 с).
- **Альтернативы:** проба на каждой аренде; периодический keepalive.
- **Почему:** на горячем пути проба избыточна; на холодном — её цена амортизируется через одну операцию.

---

## 10. Связи с другими спеками

- **S0061 (Implemented):** прямой источник; SMB-имплементация остаётся образцом.
- **S0066 (планируется):** делит resourceKey-нормализатор и transient-классификацию; gate сообщает decoder'у через метаданные о произошедшем recreate.
- **S0047 (BlockNeedUserTest):** sftp-pool-broken-channel — этот тикет должен закрыть его коренные причины.
- **S0025 (Implemented):** smb-fast-fail — gate не дублирует timeout-логику; gate работает «до» fast-fail.
- **S0046 (Tactical):** sftp-key-auth-hardening — независим, но gate должен корректно вызывать аутентификацию через текущий API.

---

## 11. Критерии готовности (strategic-level)

1. После ≥ 2 минут простоя следующая операция (копирование / playback / листинг) на FTP / SFTP / Cloud восстанавливает соединение в пределах одного внутреннего retry — без UI-ошибки.
2. В логе одинаковый формат записи `[scope=connection protocol=X resource=Y reason=Z action=W]` для всех протоколов; нет каскада идентичных stack-trace.
3. Cloud access-token истекает во время паузы → следующая операция выполняется через preemptive refresh без 401-ошибки в UI.
4. При сворачивании UI и возврате через ≥ 2 минуты первая операция работает без ошибки на любом протоколе; фоновый sync-worker не получает разрыв в середине задачи.
5. Метрика переподключений per-resource с порогом snackbar 3+/5min работает для всех протоколов.
6. Существующие SMB-сценарии S0061 продолжают работать без регрессий.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0067` — создаст `PLAN/S0067_enh-network-stale-connection-invalidation-multi-protocol/` с фазами.

---

## Last Audit

**Date:** 2026-05-03
**Mode:** field-log
**Evidence:** `logs/fastmediasorter_20260503_180505.log`
**Outcome:** Implemented — latest incident remains SMB-only, multi-protocol criteria are not advanced by this log

### Observed

- В последнем логе нет FTP / SFTP / Cloud activity; инцидент почти целиком состоит из SMB auth/precheck/degraded событий.
- Поэтому критерии §11 для FTP / SFTP / Cloud не получают новой полевой валидации от этого лога.
- Текущий log useful only as a boundary check: `S0067` не должен перехватывать владение SMB-auth churn из `S0061/S0025`; без cross-protocol evidence спека остаётся архитектурным follow-up, а не ближайшим bug owner.
