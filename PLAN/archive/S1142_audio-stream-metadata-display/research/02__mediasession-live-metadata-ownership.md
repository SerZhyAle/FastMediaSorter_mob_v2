# S1142 Research 02 - MediaSession live-metadata ownership (service vs controller)

**Захвачено:** 2026-07-23 (feeds strategic §4/§5.2/§9 ADR-1; авторизует тактику F2)
**Статус:** Resolved (from codebase read of AudioPlaybackService / AudioServiceController / NetworkAwareMediaSourceFactory)

---

## Вопрос

Где физически выполнять проброс live-ICY-трека в системную нотификацию: в inline-менеджере (через `MediaController`, как читалось из §4) или внутри фон-сервиса?

## Находки

1. **Сервисный ExoPlayer получает ICY.** Background HTTP-путь `NetworkAwareMediaSourceFactory` (`ui/player/helpers/NetworkAwareMediaSourceFactory.kt:124`) для `http/https` использует ту же `StreamDataSourceFactoryProvider.create(context)` с `Icy-MetaData`, что и in-app-плеер. Значит сервисный `ExoPlayer` (`AudioPlaybackService.kt:346-352`, поле `player`) получает ICY-кадры и сворачивает их в `player.mediaMetadata`, вызывая `onMediaMetadataChanged` (`AudioPlaybackService.kt:403`).

2. **Статичный title MediaItem перекрывает динамический ICY-трек.** `AudioServiceController.playAudioWithMetadata` (`ui/player/helpers/AudioServiceController.kt:187-205`) ставит `MediaItem.mediaMetadata.setTitle(station)` один раз. В media3 при построении комбинированных метаданных значения уровня `MediaItem` имеют приоритет над динамическими (stream) метаданными, поэтому итоговый `title` остаётся именем станции, а живой ICY-трек не виден в нотификации. Это причина «застывания» из strategic §1 / research 01.

3. **`MediaController` не получает сырой `onMetadata`.** Inline-менеджер в service-режиме держит `MediaController` как `player` (`StreamInlineAudioManager.kt:169-179`). `MediaController` синхронизирует только `PlayerInfo` (комбинированный `onMediaMetadataChanged`), но не сырой timed `onMetadata(Metadata)` / `IcyInfo`. Поэтому текущий `onMetadata`-override менеджера (`StreamInlineAudioManager.kt:104-111`) в фоновом режиме **не срабатывает** - живой трек в inline виден только в OFF/local-режиме (реальный `ExoPlayer`).

4. **Прецедент - видеопуть.** `StreamPlaybackHelper.updateNowPlayingTitle` (`ui/player/helpers/StreamPlaybackHelper.kt:449-466`) уже делает `player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(..setTitle(track)..).build())` на прямом `ExoPlayer` - media3 трактует тот же URI как metadata-only update без ре-буферизации. Активный `replaceMediaItem` нужен именно потому, что MediaItem-метаданные имеют приоритет (иначе видеопуть был бы избыточен).

## Решение (авторизует ADR-1)

- **Владелец операции - `AudioPlaybackService`.** Проброс делается там, где есть сырой ICY: в слушателе сервисного `ExoPlayer` добавить `onMetadata`, разобрать `IcyInfo.title`, и `player.replaceMediaItem(currentIndex, item.buildUpon().setMediaMetadata(current.buildUpon().setTitle(track.title).setArtist(track.artist).setStation(stationName)..).build())`. Обновлять только при реальном изменении ICY-строки.
- Обновление MediaItem-метаданных в сервисе также чинит проброс к `MediaController`: после `replaceMediaItem` комбинированный `onMediaMetadataChanged` у контроллера начинает нести живой трек, что позволяет inline-контролу показать трек и в фоновом режиме (единый формат, criterion 3).
- Разбор ICY - shared pure-роль (`NowPlayingMetadata.parse`), используется и сервисом (раздельные Artist/Title), и inline-форматтером (единая строка, ADR-5).

## Ограничения / риски

- `replaceMediaItem` на живом radio-timeline должен сохранять URI + extras (креды) + mimeType - строить через `currentMediaItem.buildUpon()`, не пересоздавать.
- Обязательна проверка на реальное изменение строки, иначе мерцание нотификации (strategic §7).
- Верификация headline-критерия (нотификация/lock-screen обновляются) - **только на устройстве** (BlockNeedUserTest).
