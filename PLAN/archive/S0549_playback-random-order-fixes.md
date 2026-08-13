# S0549 - Playback random-order fixes (shuffle auto-advance + random-seed at launch)

**Status:** Archived
**Priority:** 60
**Date:** 2026-06-19
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** owner bug report 2026-06-19 (audio player shuffle + random resource order)

> **Scope:** PRIMITIVE-ish bugfix, two related defects in playback ordering. Direct implementation; needs on-device verification.

## 0. Захват (verbatim)

Owner:
1. В аудиоплеере кнопкой выставил рандом. Ручной переход к следующему треку играет рандом. Но если трек меняется сам по окончании - он не меняется, играет один по кругу.
2. Ресурс с порядком RANDOM, но всегда начинается с одного и того же трека при запуске проигрывателя по списку.
3. Намёк: BASIC `RANDOMIZE TIMER` (засев ГСЧ временем; без него RND даёт ту же последовательность).

## 1. Root cause (diagnosed 2026-06-19)

### Bug 1 - shuffle auto-advance crutch
- Две независимые системы "next": приложенческая `PlayerNavigationCoordinator.nextFile()` (свой `shuffleIndices`, `kotlin.random.Random`) для РУЧНОГО next, и нативный ExoPlayer сервиса (`shuffleModeEnabled` + `repeatMode`) для авто-перехода.
- `AudioServiceController.applyPlaybackOrderMode(SHUFFLE)` ставит `shuffleModeEnabled=true` + `REPEAT_MODE_ALL`.
- Аудио часто грузится одним `MediaItem` (`playAudioWithMetadata`: `StandaloneViewManager`, fallback `PlayerMediaLoaderManager`). На одно-элементном таймлайне `REPEAT_MODE_ALL` = повтор одного трека; `STATE_ENDED` не приходит (идёт `REASON_REPEAT`), приложенческий `nextFile()` для авто-перехода не вызывается. Отсюда "один по кругу". Ручной next зовёт `nextFile()` напрямую -> рандом.

### Bug 2 - random seed not re-rolled at playback launch
- `BrowseSortFilterManager.randomShuffleSeed = System.nanoTime()` инициализируется один раз; перероллится `refreshRandomShuffleSeed()` (= `System.nanoTime() xor Random.nextLong()`) ТОЛЬКО при явном reshuffle / смене сорт-режима.
- RANDOM-список сортируется этим seed и кэшируется (`MediaFilesCacheManager`). Плеер берёт кэш -> запуск плеера по списку seed не переролит -> тот же первый трек.
- Доп: 4 несогласованные реализации RANDOM; одна сломана - `ResourceRepositoryImpl` `Comparator { _,_ -> Random.nextInt(-1,2) }` нарушает контракт Comparator.

## 2. Affected components

- `core/.../ui/player/helpers/PlayerNavigationCoordinator.kt` - next/prev + shuffleIndices.
- `core/.../ui/player/helpers/PlayerMediaLoaderManager.kt` - audio load (single vs playlist), auto-advance listener (`onMediaItemTransition`, `onAudioServicePlaybackEnded`).
- `core/.../ui/player/helpers/AudioServiceController.kt` - `applyPlaybackOrderMode`, playlist/single load, repeat/shuffle.
- `core/.../ui/browse/managers/BrowseSortFilterManager.kt` - `randomShuffleSeed`, `refreshRandomShuffleSeed`.
- `data/repository/ResourceRepositoryImpl.kt` - broken RANDOM comparator.
- (verify) `domain/usecase/GetMediaFilesUseCase.kt`, `data/paging/MediaFilesPagingSource.kt`, `ui/browse/filelist/BrowseFileListManager.kt` - other RANDOM impls.

## 3. Fix plan

- **Bug 1:** make auto-advance respect the same order model as manual next - either feed the full playlist into the service and rely on native shuffle consistently, or on track-end route through `nextFile(manual=false)` (honours SHUFFLE/LOOP/PLAY_THROUGH). Single source of truth for "next".
- **Bug 2:** re-roll the random order (call the seed refresh, or use an unseeded shuffle) when a fresh playback-by-list session starts, not only on explicit reshuffle. Keep seed stable within a browsing session for scroll stability. Unify the random implementations; remove the broken comparator in `ResourceRepositoryImpl`.

## 4. Verification

- Build: `.\a.ps1 fc`.
- On device: (a) SHUFFLE on -> let a track end naturally -> next track differs (not repeat-one); manual next still random. (b) Open a RANDOM-sorted resource in the player by list multiple times -> first track varies. (c) LOOP_LIST / PLAY_THROUGH / REPEAT_ONE unchanged.
