# Task 4: Enhanced Lyrics Search Logic

## Objective
Улучшить нахождение текста песни в интернете, применив:
1. Тот же механизм подбора словесных комбинаций из имени файла, что и в `SearchAudioCoverUseCase`.
2. Если по результатам поиска обложки уже есть `trackName` + `artistName` — использовать эти данные как первичный запрос при поиске лирики.

---

## Context & Current Behavior

### SearchLyricsUseCase (`SearchLyricsUseCase.kt`)

- **Функция `execute(mediaFile)`**: последовательно ищет в: AZLyrics → Musixmatch → Genius API → Lyrics.ovh.
- **`buildSearchQueries()`** (стр. ~321–395): уже есть базовый механизм перестановки слов из имени файла, но ему не хватает двух вещей:
  - алгоритм менее продвинут, чем в `SearchAudioCoverUseCase.prepareSearchQuery()`;
  - нет приоритизации уже найденных `trackName`/`artistName`.

### SearchAudioCoverUseCase (`SearchAudioCoverUseCase.kt`)

- **`prepareSearchQuery()`** (стр. ~120–147): убирает расширения, разделители, номера треков, скобкицы. Результат — iTunes Search API term. Даёт `trackName`, `artistName`, `artworkUrl`.

### LyricsManager (`LyricsManager.kt`)

- `searchAndShowLyrics(currentFile: MediaFile?)` (стр. 38) — вызывает `searchLyricsUseCase.execute(currentFile)`. Сейчас не передаёт разрешённые `trackName`/`artistName`.

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchLyricsUseCase.kt` | Главная логика поиска лирики, `buildSearchQueries()` |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchAudioCoverUseCase.kt` | `prepareSearchQuery()`, модель `AudioMetadata` (trackName, artistName) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LyricsManager.kt` | Что передаётся в `SearchLyricsUseCase.execute()` |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Храдит обогащённый `AudioMetadata` после поиска обложки |

---

## Requirements

### R1 — Использовать алгоритм подбора запросов из `SearchAudioCoverUseCase`

- Вынести `prepareSearchQuery(filename)` в общий утилитный метод (например, в `MediaQueryUtils.kt` или в файл `SearchQueryBuilder.kt` в `domain/usecase/`).
- `buildSearchQueries()` в `SearchLyricsUseCase` должен применять ту же логику очистки: убирать расширение, разделители (`_`, `-`, `.`), номера треков (`01 -`), скобкицы.

### R2 — Приоритет уже найденных `trackName` + `artistName`

- **Flow:**
  1. Пользователь запросил лирику (через `LyricsManager.searchAndShowLyrics`).
  2. `PlayerViewModel` проверяет: есть ли `cachedAudioMetadata` для текущего файла с `trackName != null && artistName != null`.
  3. Если есть — передаёт в `SearchLyricsUseCase.execute(mediaFile, resolvedTitle, resolvedArtist)`.
  4. `execute()` первым запросом делает строку `"$artistName $trackName lyrics"` (и варианты без слова «lyrics»).
  5. Только если по обогащённым данным ничего не нашли — перейти к перебору словесных комбинаций из имени файла.

### R3 — Изменение сигнатуры `execute()`

```kotlin
// SearchLyricsUseCase.kt
suspend fun execute(
    mediaFile: MediaFile,
    resolvedTitle: String? = null,
    resolvedArtist: String? = null
): Result<String>
```

Внутри: если `resolvedTitle != null && resolvedArtist != null` — использовать их как первый запрос, остальные — как fallback.

---

## Implementation Plan

1. **Вынести общую логику очистки** запроса из `SearchAudioCoverUseCase.prepareSearchQuery()` в общий `object` или extension-функцию. Оба UseCase её импортируют.
2. **Изменить `SearchLyricsUseCase.execute()`**: принять опциональные `resolvedTitle`, `resolvedArtist`. Построить очередь запросов: сначала `"$artist $title"`, затем `"$title"`, затем перебор из файла.
3. **Изменить `LyricsManager.searchAndShowLyrics()`**: получать из ViewModel `cachedAudioMetadata?.trackName` и `cachedAudioMetadata?.artistName`, передать в `execute()`.
4. **Unit tests:**
   - `resolvedTitle`+`resolvedArtist` есть: первый запрос — `"artist title lyrics"`.
   - Metadata нет: fallback на перебор из имени файла.

---

## Edge Cases & Risks

- **Cached metadata устарела (user сменил файл):** проверять, что `cachedAudioMetadata` связан именно с `currentFile`. Если файл сменился — использовать `null`, не metadata предыдущего.
- **`resolvedArtist` = "Неизвестное" / "Unknown":** отфильтровать значения-плейсхолдеры, не использовать их в запросе.
- **DRY:** Обязательно не дублировать логику `prepareSearchQuery` — единый источник для обоих UseCase.
- **API рейт-лимиты:** перебор запросов уже есть в логике. Дополнительные запросы (resolved metadata) не увеличивают количество API-звонков — они становятся первыми в очереди, заменяя перебор.

