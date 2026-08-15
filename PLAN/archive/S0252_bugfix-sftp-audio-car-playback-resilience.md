# Стратегическая спецификация: S0252 - Устойчивое прослушивание музыки с домашнего SFTP в машине

**Ticket:** S0252
**Status:** BlockNeedUserTest
**Priority:** 95
**Date:** 2026-05-19
**Tier:** 3 - Moderate (ad-hoc, bugfix)
**Roadmap entry:** Ad-hoc - анализ `logs/fastmediasorter_20260519_101908.log` и `logs/fastmediasorter_20260519_102218.log`
**Tactical spec:** `PLAN/S0252_bugfix-sftp-audio-car-playback-resilience/`
**Tactical plan:** [`PLAN/S0252_bugfix-sftp-audio-car-playback-resilience/INDEX.md`](S0252_bugfix-sftp-audio-car-playback-resilience/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, file paths, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Прослушивание музыки в машине с домашнего SFTP является одним из основных сценариев использования, но текущая сборка ведёт себя как набор независимых fallback'ов, а не как устойчивый audio-flow. В первой сессии SFTP-скан 3980 файлов занял 78,6 s, затем запуск MP3 сопровождался `Connection test failed` с `SocketTimeoutException` и `SFTP [FILE_OPS] IOException attempt 0 .. inputstream is closed`. Во второй сессии несколько MP3 стартовали, но pre-cache дважды ушёл в 20 s timeout, playback откатился в direct streaming, prefetch следующего трека завершился `FAILED`, перед playback free native heap падал до 4 MB, а закрытие SFTP stream логировалось как error `Pipe closed`.

Пользовательская поверхность: трек может стартовать медленно, следующий трек может не подготовиться, соединение может считаться недоступным прямо во время воспроизведения, а диагностика превращает ожидаемое закрытие stream в error. Для автомобильного сценария это критично: пользователь не должен вручную обслуживать сеть, память и pre-cache во время движения.

---

## 2. Цели

1. SFTP audio playback с домашнего ресурса стартует без 20 s блокирующего pre-cache ожидания в нормальном сценарии.
2. Pre-cache failure не ломает воспроизведение текущего трека и не оставляет следующий трек без понятной recovery-политики.
3. Закрытие SFTP stream после отмены, смены трека, потери сети или остановки playback не логируется как error, если это ожидаемый lifecycle outcome.
4. Потеря сети во время SFTP audio playback приводит к предсказуемому состоянию: текущий трек либо продолжает играть из уже считанного буфера, либо playback останавливается с корректной причиной, без каскада `inputstream is closed`/`Pipe closed`.
5. Memory pressure перед audio playback не доходит до `free=4MB` на устройстве с heapMax 512 MB в каноническом сценарии «домашний SFTP -> несколько MP3 подряд».

**Non-goals:**

- Замена JSch или Media3.
- Гарантия playback при полном отсутствии сети и пустом буфере.
- Новый UI для автомобильного режима.
- Переписывание всего SFTP browse/scanner path.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Автомобильный сценарий считать primary acceptance, а не edge-case.
2. Предпочитать быстрое начало playback и graceful degradation более агрессивному pre-cache.
3. Следующий трек должен готовиться best-effort, но не за счёт стабильности текущего.
4. Логи должны отличать настоящую ошибку транспорта от нормального закрытия stream.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `legacy`, `noLegal`, VR-варианты, где доступен SFTP audio playback. `lite` и `photos` вне audio scope, если audio выключен.
- **API level:** без новой Android API-специфики.
- **Wear OS:** не затрагивается, если отдельный Wear SFTP audio path не использует те же компоненты.
- **Производительность:** никаких сетевых probe в горячем пути каждого read; допускаются только bounded timeout, adaptive pre-cache policy и lightweight telemetry.
- **Совместимость данных:** без изменений схемы и сохранённых настроек.
- **Локализация:** пользовательские строки не меняются в первой итерации. Если появятся новые сообщения, EN/RU/UK и `docs/COMMUNICATION_POLICY.md` обязательны.
- **Доступность:** UI не меняется.

---

## 4. Контекст текущей архитектуры

Audio playback идёт через общий player path, который для сетевых источников получает stream из SFTP data source и параллельно может запускать pre-cache/prefetch следующего файла. SFTP browse/scanner, connection tester, file ops и streaming path используют один удалённый ресурс, но имеют разные lifecycle-ожидания: browse хочет полный список, connection test хочет короткий verdict, playback хочет долгоживущий последовательный stream.

Логи показывают, что эти роли конкурируют: большой SFTP scan занимает десятки секунд, connection test падает по socket timeout рядом со стартом playback, pre-cache ждёт полный 20 s budget и затем откатывается, а закрытие stream попадает в error-channel. Существующие SFTP idle/retry фиксы закрывали разовые операции, но не задавали отдельный contract для длинного audio stream.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A - классификация SFTP audio outcomes.** Разделить настоящие transport failures, expected close/cancel outcomes, pre-cache timeout и network-lost transitions. Лог-уровни и recovery выбираются по классу исхода, а не по строке исключения.

**Столп B - bounded pre-cache для автомобильного audio.** Pre-cache SFTP MP3 не должен удерживать playback startup на 20 s, если direct streaming уже может стартовать. Нужна политика раннего перехода в direct streaming и отдельный budget для prefetch next track.

**Столп C - stream lifecycle hardening.** Закрытие stream после смены трека, отмены loader'а, network lost или fallback не считается error само по себе. Реальная ошибка остаётся error только если она прервала активное воспроизведение или исчерпала retry.

**Столп D - memory-pressure coordination.** Audio playback не должен стартовать на `free native heap=4MB` без root-cause анализа. Этот столп не заменяет S0207, но фиксирует SFTP audio acceptance как обязательный сценарий для S0207 и для текущего тикета.

**Столп E - car-use acceptance instrumentation.** Нужен короткий log predicate для одного сценария: открыть домашний SFTP music resource, запустить 3 MP3 подряд, сменить трек, пережить краткий network change, закрыть плеер. По логу должно быть понятно, какой fallback сработал и почему.

### 5.2 Потоки данных и событий

User action запускает SFTP MP3. Player path запрашивает stream и может параллельно запустить pre-cache/prefetch. Если pre-cache не укладывается в короткий budget, playback переходит в direct streaming без пользовательской паузы. Если direct stream закрывается из-за смены трека или отмены loader'а, outcome классифицируется как expected close. Если сеть пропадает, active stream получает bounded retry/reopen только если это безопасно для текущей позиции; иначе playback завершает current item с понятной причиной и не загрязняет очередь следующего трека stale state.

### 5.3 Точки расширяемости

- Outcome-классификация должна быть переиспользуемой для SMB/FTP streaming, но acceptance этой спеки остаётся SFTP audio.
- Pre-cache policy должна различать audio/video и текущий source type без новых BuildConfig guard'ов в common source set.
- Memory evidence связывается с S0207, чтобы root-cause memory work не расползался внутри playback-specific тикета.

---

## 6. Открытые вопросы / Research items

1. **Что означает `Pipe closed` в SFTP DataSource close path?**
   - **Вопрос:** Это expected close после Media3 loader cancel/track switch или реальный premature stream failure?
   - **Нужно выяснить:** какие lifecycle-события были активны в момент `10:28:16`, почему ошибка залогирована через `E/App`, и можно ли безопасно downgrade до debug/warn для expected close.
   - **Решение:** считать `Pipe closed` expected-close candidate, потому что stack trace идёт через Media3 `DataSourceUtil.closeQuietly` / `StatsDataSource.close` и JSch `RequestQueue.cancel`. Phase 02 должна реализовать lifecycle-aware classification; downgrade допустим только для доказанного close/cancel context.
   - **Статус:** Resolved

2. **Почему pre-cache ждёт 20 s и затем откатывается?**
   - **Вопрос:** 20 s timeout является корректным budget для видео, но слишком дорог для автомобильного audio startup?
   - **Нужно выяснить:** какой минимальный early-direct-stream threshold сохраняет стабильность и не ухудшает next-track readiness.
   - **Решение:** полный 20 s startup wait для SFTP audio отвергнут. Phase 03 вводит source-aware audio startup budget короче 20 s и отдельный next-track prefetch budget/recovery path.
   - **Статус:** Resolved

3. **Связан ли `inputstream is closed` с S0219 или это отдельный streaming contract?**
   - **Вопрос:** Ошибка в `logs/fastmediasorter_20260519_101908.log` выглядит похожей на SFTP idle/retry, но возникает в audio file path после connection-test timeout.
   - **Нужно выяснить:** повторяется ли дефект после текущего состояния S0219 и проходит ли он через stream DataSource, file ops wrapper или connection tester.
   - **Решение:** не переоткрывать S0219 по этому evidence. Строка возникает на MP3 path после connection-test timeout и перед network-lost, поэтому Phase 02 S0252 владеет streaming boundary; S0219 остаётся related history для one-shot idle/retry.
   - **Статус:** Resolved

4. **Почему native heap падает до 4 MB перед audio playback?**
   - **Вопрос:** Это остаточное давление от image/cache/scanner path, Media3 allocation, SFTP buffers или общий S0207 root cause?
   - **Нужно выяснить:** какие memory probes нужны в SFTP audio сценарии, чтобы связать `free=4MB` с конкретным transition.
   - **Решение:** root-cause ownership остаётся в S0207; S0252 сохраняет SFTP MP3 playback как acceptance/blocker evidence. Phase 04 добавляет memory evidence в S0207 и не дублирует root-cause memory work.
   - **Статус:** Resolved

5. **Как трактовать network lost в автомобильном сценарии?**
   - **Вопрос:** События `NetworkStateMonitor: Network lost` приходят рядом с playback и S0188 probe. Нужно ли audio player останавливать, продолжать до исчерпания буфера или переводить очередь в paused/unavailable state?
   - **Нужно выяснить:** фактическое состояние playback после `10:27:54` и `10:28:33`.
   - **Решение:** network lost является playback state transition, а не мгновенным fatal outcome. Текущий active stream может продолжать играть из уже считанного буфера; prefetch/next-track path переходит в recoverable degraded state и не оставляет очередь с голым `FAILED`.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Downgrade `Pipe closed` скроет реальную потерю stream | Средняя | Ошибка playback станет менее видимой | Downgrade только при доказанном expected-close context; real active-read failures остаются warning/error |
| Слишком короткий pre-cache budget ухудшит playback на медленном SFTP | Средняя | Direct streaming чаще стартует без запаса | Разделить startup budget и next-track budget; добавить log evidence по fallback |
| Fix начнёт дублировать S0207 или S0219 | Средняя | Размытый scope и конфликт задач | S0252 владеет SFTP audio end-to-end acceptance; S0207 владеет root-cause memory, S0219 - idle/retry для разовых операций |
| Network lost на Android head unit может быть transient при переключении интерфейса | Средняя | Ложная остановка музыки | Классифицировать короткий network change отдельно от подтверждённой недоступности SFTP host |
| Playback path зависит от release-only поведения | Низкая | Debug-сессия не воспроизведёт проблему | Acceptance требует release-equivalent log run на реальном устройстве/головном устройстве |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это bugfix существующей возможности: SFTP already заявлен как источник для streaming, а Audio Player already умеет background playback. Пользователь не получает новую функцию, он получает стабильность primary-сценария.

---

## 9. Архитектурные решения (ADR)

**ADR-1: SFTP audio playback получает отдельный acceptance, не растворённый в browse/transfer тикетах.**

- **Решение:** создать отдельный тикет для end-to-end сценария «домашний SFTP -> музыка в машине».
- **Альтернативы:** переоткрыть S0219 или расширить S0207.
- **Почему:** S0219 не покрывает pre-cache/direct-stream/Media3 close lifecycle, а S0207 не должен владеть transport recovery и playback queue semantics.

**ADR-2: expected stream close не является error.**

- **Решение:** stream close outcome классифицируется по lifecycle context.
- **Альтернативы:** логировать любое исключение из `close()` как error.
- **Почему:** Media3 может закрывать stream при смене трека или cancel; error-level нужен только для пользовательски значимого отказа.

**ADR-3: audio startup важнее полного pre-cache.**

- **Решение:** для SFTP MP3 допускается ранний direct-stream fallback вместо ожидания полного pre-cache budget.
- **Альтернативы:** ждать 20 s для каждого pre-cache failure.
- **Почему:** в машине задержка старта и ручное вмешательство хуже, чем controlled direct streaming с диагностикой.

---

## 10. Связи с другими спеками

- **S0207** (`BlockNeedUserTest`) - root-cause memory reduction. S0252 добавляет свежую SFTP MP3 evidence-сессию с `free=4MB` (`temp/S0252_sftp_audio_memory_acceptance.md`) и делает этот сценарий обязательным acceptance; если headroom после S0252 playback fixes остаётся ниже threshold, root-cause fix остаётся в S0207.
- **S0219** (`Verified` в каталоге, но стратегический файл содержит stale `BlockNeedUserTest`) - SFTP idle/retry race. S0252 не переоткрывает S0219 автоматически, но требует research item на границу `inputstream is closed`.
- **S0168** (`Verified`) - playback stuck-buffering/no-feedback guard; в логах видны его memory guard messages, но root-cause не закрыт.
- **S0213** (`BlockNeedUserTest`) - video playback OOM hardening; related only through player memory telemetry.
- **S0188** (`Verified` в каталоге, но runtime log содержит `S0188:` probe) - network lost while slideshow active; возможно stale probe in shipped build или незавершённая verification cleanup.

Блокирующих зависимостей нет. Phase 01 завершила research-routing: кодовые фазы S0252 могут идти без переоткрытия S0219, а memory root cause остаётся в S0207.

---

## 11. Критерии готовности (strategic-level)

1. В сценарии «домашний SFTP music folder -> запустить 3 MP3 подряд -> сменить трек -> кратко потерять сеть -> закрыть плеер» нет crash/ANR и нет user-visible stuck state.
2. `preCacheNetworkAudio` не удерживает старт MP3 на 20 s без раннего fallback в direct streaming.
3. `SftpDataSource: Error closing InputStream` не появляется для expected close/cancel path; real stream failures остаются диагностируемыми.
4. `inputstream is closed` не появляется как первый видимый symptom при обычном запуске или смене SFTP MP3.
5. `prefetchNextAudio: FAILED` не оставляет очередь в неготовом состоянии без retry/degrade decision.
6. Перед audio playback native free headroom не падает до 4 MB в canonical 512 MB heap scenario, либо лог явно связывает это с открытым S0207 blocker and `temp/S0252_sftp_audio_memory_acceptance.md`.
7. По одному логу можно восстановить sequence: selected track, pre-cache decision, direct-stream decision, stream close reason, network transition, memory probe.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация: `PLAN/S0252_bugfix-sftp-audio-car-playback-resilience/INDEX.md`.

---

## 13. On-device acceptance

**Regression references:**

- `logs/fastmediasorter_20260519_101908.log`
- `logs/fastmediasorter_20260519_102218.log`

**Device scenario:** release-equivalent standard build on the Android head unit / car route, connected to the home SFTP resource `Home MP3`; cold start -> open the SFTP music folder -> start `Eye In The Sky.mp3` or another MP3 from the same resource -> skip to two additional MP3 tracks -> briefly lose/recover network -> close the player.

**Expected log predicates:**

- SFTP MP3 startup no longer waits on the old full startup path: no `preCacheNetworkAudio: timed out after 20000ms` for SFTP audio startup.
- If pre-cache times out, the fallback line includes source and reason: `source=sftp` and `reason=sftp-audio-early-direct-stream`.
- Direct streaming fallback starts without crash/ANR or stuck player UI.
- Expected SFTP close/cancel does not log `SftpDataSource: Error closing InputStream`.
- Next-track prefetch failure, if reproduced, logs a recoverable/degraded decision instead of bare `prefetchNextAudio: FAILED`.
- Memory warning, if reproduced, is tied to open S0207: the log contains the native headroom evidence and not an unexplained playback failure.

**Feature docs decision:** `docs/FEATURES unchanged`; S0252 fixes existing SFTP/audio playback behavior and adds no new user-visible capability.

## Last Audit

**Date:** 2026-05-19
**Mode:** full
**Flags:** -
**Outcome:** BlockNeedUserTest (structural PASS; pending on-device acceptance)
**Counts:** PASS 27 · WARN 0 · FAIL 0 (post-fix) · MANUAL 7 · EXEMPT 1

Structural verdict for the implementation itself is clean: every Files-Touched artifact exists, every required symbol declares (`SftpFailureCategory.EXPECTED_STREAM_CLOSE`, `SFTP_AUDIO_STARTUP_PRECACHE_TIMEOUT_MS`, `audioStartupPolicyFor`, `AudioStartupPreCachePolicy`, `AudioNextTrackPrefetchRecovery`, `AudioPreCacheSourceType`), forbidden `Log.d(` is absent in every modified file, the trilingual `docs/FEATURES*.md` step is EXEMPT (§8 declares no user-visible change), `dev/CHANGELOG.md` carries 41 S0252 entries, and `dev/CATALOG/app_v2.md` exposes every new public class.

Fix applied during this audit: the `BlockNeedUserTest` transition was missing its three `Timber.d("S0252: ..")` device-test probes. Inserted one tag at each changed-flow entry point so the operator can verify each fixed branch in logcat during head-unit acceptance.

### Action items

None - all mechanical predicates now pass.

### Manual / on-device

- [ ] §11.1 - run the home SFTP -> 3 MP3 -> track switch -> brief network loss -> close sequence on the head unit; no crash/ANR and no user-visible stuck state.
- [ ] §11.2 - logcat shows no `preCacheNetworkAudio: timed out after 20000ms` on SFTP audio startup.
- [ ] §11.3 + §11.4 - `SftpDataSource: Error closing InputStream` and `inputstream is closed` do not appear during expected close/cancel; `Timber.d("S0252: SftpDataSource expected-close branch hit")` confirms the new classifier fired.
- [ ] §11.5 - if prefetch fails, the new `prefetchNextAudio: RECOVERABLE failure ..` line appears together with `Timber.d("S0252: prefetchNextAudio recoverable-failure branch hit")`; no bare `prefetchNextAudio: FAILED`.
- [ ] §11.6 - if native free headroom drops, the evidence ties back to S0207 acceptance addendum (`temp/S0252_sftp_audio_memory_acceptance.md`), not to an unexplained playback failure.
- [ ] §11.7 - the captured log lets the reviewer reconstruct: selected track -> pre-cache decision -> direct-stream decision -> stream close reason -> network transition -> memory probe.
- [ ] If pre-cache times out on SFTP audio, the log line includes `source=sftp` and `reason=sftp-audio-early-direct-stream`, paired with `Timber.d("S0252: SFTP audio early direct-stream fallback (source=sftp)")`.
