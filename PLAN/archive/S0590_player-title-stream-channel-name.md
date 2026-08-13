# S0590 - Имя канала в заголовке плеера для видеотрансляций

**Ticket:** S0590
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-21
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-21 («быстрый фикс»)
**Complexity:** Simple (compact spec - strategic + tactical inline)

<!-- auto-approved by /spec-all - 2026-06-21 -->

---

## Goal

При воспроизведении интернет-трансляции в полноэкранном плеере в заголовке (там, где для обычных файлов показывается имя видеофайла) сейчас отображается «имя файла», вычисленное из голого URL потока (напр. `live_web.m3u8`). Нужно показывать человекочитаемое имя канала, которое уже сохранено в списке трансляций. Для обычных локальных/сетевых файлов поведение не меняется - заголовок по-прежнему берётся из имени файла. Имя канала разрешается по URL потока через уже существующий `GetStreamSourceByUrlUseCase`, поэтому правка работает для любой точки запуска трансляции и не требует изменения сигнатуры `PlayerActivity.createIntent`.

---

## Approach

- Поле `MediaFile.title` (`String?`) уже существует и уже используется аудио-хелперами по паттерну `file.title?.takeIf { it.isNotBlank() } ?: file.name`.
- На сборке синтетического одноэлементного `MediaFile` для stream-URL в `PlayerMediaFilesLoader` разрешаем имя канала через `GetStreamSourceByUrlUseCase(streamPath)?.title` и кладём в `MediaFile.title`.
- При отрисовке в `PlayerUiStateCoordinator` заголовок и оверлей имени берут `title ?: name`. Если канал не найден (запуск не из списка) - graceful fallback на имя из URL (текущее поведение).
- Формат индекса (`1/1 - <name>`) не меняем: подменяем только компонент имени.

**Non-goals:**

- Коллизия `SYNTHETIC_STREAM_RESOURCE_ID` / `FAVORITES_RESOURCE_ID` (вынесено в S0591).
- Аудиотрансляции с `MediaType.VIDEO` (вынесено в S0592).
- Любая правка для не-stream источников.

---

## Phase 01 - Plumb channel name into synthetic stream MediaFile.title

Goal: the synthetic one-item `MediaFile` built for a stream URL carries the human-readable channel name in `title`.

Steps:

1. Add constructor parameter `private val getStreamSourceByUrlUseCase: GetStreamSourceByUrlUseCase` to `PlayerMediaFilesLoader` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`).
   - Verification: file compiles after the param is referenced in step 3.
2. Pass the already-injected `getStreamSourceByUrlUseCase` from the loader construction in `PlayerViewModel` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt:330`).
   - Verification: the `PlayerMediaFilesLoader` constructor call passes `getStreamSourceByUrlUseCase = getStreamSourceByUrlUseCase`.
3. In the stream-path branch (`PlayerMediaFilesLoader.kt:244-253`), before building `streamFile`, resolve `val channelTitle = getStreamSourceByUrlUseCase(streamPath)?.title?.takeIf { it.isNotBlank() }` and set `title = channelTitle` on the synthetic `MediaFile`.
   - The branch already runs inside the loading `launch` coroutine, so the suspend call is legal.
   - Verification: the synthetic `MediaFile` in that branch sets `title = channelTitle`; Grep confirms `getStreamSourceByUrlUseCase(streamPath)` present.

## Phase 02 - Render channel name as player title for streams

Goal: toolbar title and filename overlay show `title ?: name`, so stream sources display the channel name and all other files are unchanged.

Steps:

1. In `PlayerUiStateCoordinator.updateUI()` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt`), inside `state.currentFile?.let { file -> .. }`, compute `val displayName = file.title?.takeIf { it.isNotBlank() } ?: file.name`.
   - Verification: `displayName` declared once inside the `let` block.
2. Use `displayName` in the toolbar title (line 207): `"${state.currentIndex + 1}/${state.files.size} - $displayName"`.
   - Verification: Grep shows no remaining `- ${file.name}"` on the toolbar title line.
3. Use `displayName` in both overlay branches (lines 223 and 226), replacing `file.name`.
   - Verification: `tvFileNameOverlay.text` uses `displayName` in both AUDIO and non-AUDIO branches.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565, S0589, S0591, S0592
- **UI scope:** заголовок плеера и оверлей имени для источника-трансляции; новых строк/лейаутов нет, изменение чисто data-driven (имя канала вместо имени из URL).

---

## Acceptance criteria

1. Запуск трансляции из списка -> в заголовке плеера и оверлее имени видно имя канала, а не сегмент URL.
2. Обычный локальный/сетевой файл -> заголовок без изменений (имя файла).
3. Трансляция, для которой имя канала недоступно, -> заголовок остаётся именем из URL (без пустого/«null»).

---

## 12. Ссылка на тактическую спецификацию

Compact spec - фазы инлайн выше; отдельная тактическая папка не создаётся.
