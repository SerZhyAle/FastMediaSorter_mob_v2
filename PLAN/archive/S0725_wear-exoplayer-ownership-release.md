# Спецификация (fix): S0725 - Wear: владение и освобождение общего ExoPlayer + отвязка surface

**Ticket:** S0725
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находка аудита S0715 (Layer 3, кластер P2)
**Umbrella:** S0714

> **Scope:** Конкретный fix утечки ресурсов плеера в модуле `wear/`. Найдено статически (S0715), требует подтверждения на устройстве.

---

## 0. Источник

Кластер из 4 подтверждённых P2-находок аудита S0715 (`PLAN/S0715_memory-resource-ownership-audit/AUDIT_FINDINGS.md`), сводящихся к одной корневой причине в `WearAppModule`.

## 1. Проблема

`wear/.../di/WearAppModule.kt:59` `provideExoPlayer` помечен `@Singleton` в `SingletonComponent` - один `ExoPlayer` живёт весь процесс. Оба плеер-VM делят этот инстанс:

- `AudioPlayerViewModel.onCleared()` (`:301`) и `VideoPlayerViewModel.onCleared()` (`:333`) на teardown зовут только `removeListener+stop+clearMediaItems`, но никогда `release()`. Единственный `.release()` в модуле - несвязанный `WatchWearListenerService.kt:60`. `FastMediaSorterWearApp.kt` не имеет `onTerminate`. Нативные ресурсы (playback-HandlerThread, аудио-рендерер, AudioTrack/audio-focus, кодеки) не освобождаются за всё время процесса.
- `VideoPlayerScreen.kt:179` `DisposableEffect onDispose` только логирует и не зовёт `view.player = null`. В Media3 1.2.1 `PlayerView.setPlayer()` регистрирует внутренний ComponentListener и не отвязывается в `onDetachedFromWindow`, поэтому singleton удерживает каждый утилизированный `PlayerView` (-> Context) - накопление по одному на каждый заход на видео-экран.

Серьёзность P2: удержание ограничено одним singleton-инстансом (Wear, одна Activity, без per-screen аллокации плеера), не неограниченный рост. Но ресурсы не освобождаются и disposed-PlayerView накапливаются.

## 2. Решение (выбрать владельца)

Корень - «два владельца одного плеера»: release в per-VM `onCleared` невозможен (убьёт плеер для второго VM). Варианты:

- **(A) Per-VM плеер.** Снять `@Singleton`, давать non-singleton `ExoPlayer` (или фабрику), чтобы каждый VM владел своим инстансом и звал `release()` в `onCleared`.
- **(B) Процессный владелец.** Оставить singleton, освобождать из process/lifecycle-aware владельца (ProcessLifecycleOwner-обзёрвер или MediaSessionService) на остановку приложения.

В обоих случаях: в `VideoPlayerScreen` `DisposableEffect onDispose` отвязывать surface - `playerView.player = null` (захватив PlayerView через `remember`/`AndroidView onRelease`).

Выбор A vs B - на `/spec-tech`/решение владельца (A проще и привычнее для per-screen плеера; B нужен, если общий плеер намеренно непрерывен между аудио/видео).

**Выбрано: Вариант A (per-VM плеер).** Обоснование из кода: `ExoPlayer` инжектят только два VM (`AudioPlayerViewModel`, `VideoPlayerViewModel`) - оба через конструктор, каждый добавляет свой `Player.Listener` и грузит собственный `MediaItem` в `init`/`load`. Аудио и видео - раздельные экраны разных media-типов без кросс-экранной непрерывности воспроизведения (сценарий, оправдывающий B, отсутствует). Снят `@Singleton` с `provideExoPlayer` → каждый VM владеет своим инстансом и зовёт `release()` в `onCleared`. Других потребителей `ExoPlayer` в графе нет (grep подтверждён).

## 3. Критерии приёмки

- [x] `ExoPlayer.release()` вызывается на реальном teardown (Вариант A); нативные ресурсы освобождаются. (`AudioPlayerViewModel.onCleared` + `VideoPlayerViewModel.onCleared` теперь `removeListener` + `release()`.)
- [x] `PlayerView.player` обнуляется в `onDispose` видео-экрана; плеер не удерживает disposed PlayerView/Context. (`VideoPlayerScreen` - `remember { PlayerView(..) }` + `playerView.player = null` в `onDispose`.)
- [~] Сборка `wear` зелёная (`:wear:assembleDebug` BUILD SUCCESSFUL in 1m 7s); LeakCanary-прогон на **Wear**-устройстве - ОТЛОЖЕНО на on-device (attached emulator - телефонный AVD, не Wear). Повторная навигация на видео-экран не должна накапливать удержанные PlayerView.

## 4. Связанные тикеты

- S0715 (аудит-источник), S0714 (зонтик), S0716 (конкурентность wear - смежный модуль).
