# Спецификация (compact bugfix): S1139 - LinkDownloadWriter: таймауты при загрузке с threads.com

**Ticket:** S1139
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-21
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-21

**Текст:**

Автозахват при анализе логов (/log-reader). Загрузка медиа по ссылке с
threads.com обрывается таймаутом: `InterruptedIOException: timeout`, причина -
`okhttp3 StreamResetException: stream was reset: CANCEL` (HTTP/2). Повтор 4 раза
подряд. При этом ранее в той же сессии одна загрузка с Threads прошла успешно
(fallback-сохранение в Downloads), последующие попытки падают - похоже на
троттлинг/сброс со стороны Threads либо слишком агрессивный OkHttp-таймаут для
крупных медиа Threads.

Низкая уверенность в том, что дефект app-side (может быть внешняя причина) -
приоритет занижен, владелец решает судьбу тикета.

**Эвиденс (лог `logs/fastmediasorter_20260720_053446.log`):**

```
[1464] 16:40:16 I/App: LinkDownloadWriter: saved '747613204_...n.jpg' to Downloads
[1465] 16:40:16 I/App: real media saved via fallback: file=... reason=NoResourceConfigured
[1466] 16:40:48 E/App: LinkDownloadWriter: write failed
[1467] java.io.InterruptedIOException: timeout
       at okhttp3.internal.connection.RealCall.timeoutExit(RealCall.kt:398)
       ...
[1508] Caused by: okhttp3.internal.http2.StreamResetException: stream was reset: CANCEL
```

Повторы write failed: 16:40:48, 16:41:30, 16:42:21, 16:42:54 (тот же стек,
host=www.threads.com).

---

## 1. Проблема / симптом

Загрузка медиа по ссылке с threads.com периодически падает
`InterruptedIOException: timeout` (HTTP/2 stream reset CANCEL). В одной сессии
~4 подряд после одной успешной. Возможен троттлинг Threads или неподходящий
таймаут/стратегия загрузки для этого хоста. Флейвор noLegal.

---

## 2. Корневая причина

Общий клиент линк-загрузки `@Named("linkDownload")`
(`di/LinkDownloadModule.kt`) сконфигурирован с `callTimeout(30, SECONDS)`.
`callTimeout` в OkHttp ограничивает **весь** вызов целиком - от `execute()` до
полного вычитывания и закрытия тела ответа. Медиа-тело Threads стримится
(`DirectFileExtractionStrategy.open` -> `body.byteStream()`) и дочитывается в
`LinkDownloadWriter.writeFromStream`; весь трансфер обязан уложиться в 30с, иначе
OkHttp отменяет вызов.

Совпадение с эвиденсом однозначное:

- Стек `okhttp3.internal.connection.RealCall.timeoutExit` - это механизм именно
  `callTimeout` (idle-таймаут сокета дал бы `SocketTimeoutException`, не
  `timeoutExit`).
- HTTP/2 код сброса `CANCEL` - клиент-инициированная отмена (наш `callTimeout`),
  а не сброс со стороны сервера (тот дал бы `REFUSED_STREAM`/`INTERNAL_ERROR`/
  `GOAWAY`).
- Тайминги: провалы через ~30-32с после старта, повторы каждые ~30-50с - каждая
  попытка доходит до потолка 30с и падает.

Крупное/медленное медиа Threads легитимно качается дольше 30с (троттлинг, большой
файл), поэтому фиксированный общий потолок рвёт живую передачу. Первая успешная
загрузка в сессии - мелкий JPEG, уложившийся в 30с; последующие крупнее и
стабильно упираются в потолок. Это app-side дефект конфигурации, не внешний
троттлинг (троттлинг лишь увеличивает время и делает потолок заметным).

`readTimeout(30, SECONDS)` (idle между чтениями) и `connectTimeout(15, SECONDS)`
уже защищают от реально зависшего/мёртвого соединения независимо от `callTimeout`.

---

## 3. Исправление

Отключить общий потолок `callTimeout` для клиента `@Named("linkDownload")`:
`callTimeout(30, SECONDS)` -> `callTimeout(0, TimeUnit.SECONDS)` (0 = без общего
лимита, идиоматично для загрузок произвольного размера). Защита от зависаний
сохраняется через idle-таймауты `readTimeout(30с)` + `connectTimeout(15с)`:
мёртвый поток (30с без байтов) по-прежнему падает, но легитимная длинная передача
больше не отменяется искусственно.

Охват: одна строка в общей фабрике клиента (`di/LinkDownloadModule.kt`). Клиент
единственный на все флейворы (Dagger-квалификатор `@Named("linkDownload")`,
`SingletonComponent` - двойной bind невозможен); его используют прямой файловый
стрим (`DirectFileExtractionStrategy`), NewPipe-загрузчик (noLegal) и HTML/WebView
стратегии. Дефект воспроизводился на noLegal (Threads), код общий.

Retry/backoff намеренно не добавляется: корневая причина - собственный потолок, а
не потеря соединения; повтор в тот же 30с-потолок лишь воспроизводил бы отказ (что
и видно в логе - 4 повтора подряд). Снятие потолка устраняет причину.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003 (link-download strategic pillar) - смежная область; блокирующих нет
- **UI/поведение:** без изменений UI; крупные/медленные загрузки по ссылке перестают обрываться ложным таймаутом.
- **Flavor:** src/main - все флейворы (дефект воспроизводился на noLegal).

---

## 4. Проверка

- Unit (device-free): `LinkDownloadClientTimeoutTest` - `callTimeoutMillis == 0`
  на клиенте из провайдера, `readTimeoutMillis == 30000`,
  `connectTimeoutMillis == 15000`. Фиксирует инвариант: никакой будущий правкой
  общий потолок не вернётся.
- Compile: `testStandardDebugUnitTest` компилирует src/main (изменённый провайдер) + test - BUILD SUCCESSFUL. Правка в src/main - компилируется идентично во всех флейворах.
- On-device (device-gated, BlockNeedUserTest): несколько последовательных
  загрузок медиа с threads.com (включая крупное видео/изображение с временем >30с)
  проходят без `LinkDownloadWriter: write failed` / `InterruptedIOException:
  timeout`. Probe `S1139:` в логе подтверждает прохождение изменённого пути
  передачи.
- Регресс: быстрые загрузки по прямым ссылкам работают; реально мёртвый хост
  по-прежнему падает по read/connect-таймауту (не виснет бесконечно).
