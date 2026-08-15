# Спецификация (compact bugfix): S1015 - Не добавляется RTSP-трансляция вручную

**Ticket:** S1015
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-12
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

Не удаётся добавить ртсп трансляцю вручную
http://admin:3333@192.168.1.82/media/?action=stream

---

## 1. Проблема / симптом

Пользователь пытается вручную добавить трансляцию по URL вида
`http://admin:3333@192.168.1.82/media/?action=stream` (userinfo с логином/паролем в URL,
нестандартный порт, схема `http`, без расширения в пути - типичный mjpg-streamer/IP-камера
эндпойнт) через экран "Трансляции" - воспроизведение не запускается, канал сразу помечается
недоступным и предлагается к удалению. Субъективно это читается как "не удаётся добавить".

Расследование на эмуляторе показало: добавление в список (`AddStreamSourceUseCase`) отрабатывает
корректно - запись создаётся, отображается в списке с правильным URL. Отказ происходит на
воспроизведении: без расширения в пути URL классифицируется как AUDIO (радио), играется через
локальный in-app плеер, который сразу получает 401 от источника (сервер, требующий HTTP Basic
Auth через userinfo в URL) и показывает диалог "Трансляция недоступна. Удалить?" - если
пользователь жмёт "Удалить", субъективно выглядит так, будто трансляция вообще не добавилась.

---

## 2. Корневая причина

`DefaultHttpDataSource` (обёртка над `HttpURLConnection`) никогда не читает credentials из
userinfo части URI (`user:pass@host`) и не отправляет их как `Authorization: Basic` - в отличие
от браузера/curl. Многие HTTP-эндпойнты IP-камер/DVR (mjpg-streamer и аналоги) публикуются именно
в форме `http://user:pass@host/path` и ожидают Basic Auth. Раздача всегда отвечает 401,
приложение трактует это как "источник недоступен".

Отдельно обнаружено архитектурное расхождение: локальный OFF-mode путь воспроизведения радио
(`StreamInlineAudioManager.play()`, ветка `useBackgroundService == false`) строил собственную
`DefaultHttpDataSource.Factory()` вместо переиспользования общей `StreamDataSourceFactoryProvider`
- в отличие от video-плеера (`playStreamVideo`) и background-service пути
(`NetworkAwareMediaSourceFactory`), которые уже её переиспользуют. Фикс auth-заголовка в одном
только `StreamDataSourceFactoryProvider` не покрыл бы именно тот путь, где реально
воспроизводится URL без расширения (радио/AUDIO, OFF-mode) - что и было репродуцировано.

---

## 3. Исправление

- `StreamDataSourceFactoryProvider.create()` (`ui/player/helpers/StreamDataSourceFactoryProvider.kt`):
  оборачивает `DefaultHttpDataSource` в приватный `UserInfoBasicAuthDataSource`, который при
  `open()` читает `uri.userInfo`, кодирует его в `Authorization: Basic <base64>` и открывает
  соединение по URI с вырезанным userinfo (чтобы credentials не утекали в сам HTTP-запрос как
  часть URL).
- `StreamInlineAudioManager.play()` (`ui/streams/helpers/StreamInlineAudioManager.kt`, OFF-mode
  ветка): убрана дублирующая локальная `DefaultHttpDataSource.Factory()`, вместо неё
  переиспользуется `StreamDataSourceFactoryProvider.create()` - тот же фабричный код, что уже
  использует video-плеер и background-service путь. Это одновременно чинит Basic Auth для этой
  ветки и убирает архитектурное расхождение (Rule 3 - одна общая фабрика вместо трёх независимых).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

Command: `.\a.ps1 fk` (fast Kotlin compile, standard) - `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`.

On-device (emulator-5554, standard-debug): поднят локальный HTTP-сервер с Basic Auth
(`temp/S1015/basic_auth_server.py`, слушает `10.0.2.2:8899` с хоста), в приложении добавлена
трансляция `http://admin:3333@10.0.2.2:8899/test.mp3` (та же форма URL, что в репорте) и
воспроизведена:
- До фикса: сервер логировал `Authorization=None`, приложение показывало "Трансляция
  недоступна. Удалить?".
- После фикса: сервер логировал `Authorization='Basic YWRtaW46MzMzMw=='` (верный `admin:3333`),
  приложение показало зелёный статус и "Сейчас играет" в мини-плеере.

Оригинальный URL из репорта (`192.168.1.82`, LAN) не проверялся напрямую - эмулятор изолирован от
LAN хоста (известное ограничение NAT), но воспроизводит ту же форму userinfo-in-URL, что и
верифицированный тестовый URL.
