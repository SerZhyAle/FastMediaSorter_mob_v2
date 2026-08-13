# S1142 Research 01 - Audio-stream now-playing display surfaces

**Захвачено:** 2026-07-23 (feeds strategic §4/§5/§6/§9)
**Статус:** Resolved (surfaces mapped from codebase)

---

## Что уже есть

- **Захват ICY-метаданных для radio/AUDIO-потока работает.** `StreamInlineAudioManager.onMetadata` (`ui/streams/helpers/StreamInlineAudioManager.kt:104-111`) читает `IcyInfo.title` → `nowPlaying` StateFlow. ICY отдаёт **одну строку** `StreamTitle` (обычно «Исполнитель - Название», но не гарантированно; станция форматирует как хочет).
- **Единственная поверхность отображения - inline mini-контрол.** `renderTitle()` (строки 270-280) склеивает `«$station - $track»` в `titleView`. Это нижний sticky mini-control в `StreamsActivity`.
- **Фоновый сервис получает только статичное имя станции.** Путь background-playback: `audioController.playAudioWithMetadata(Uri.parse(source.url), source.title)` (строка 169) → `AudioServiceController.playAudioWithMetadata` (`ui/player/helpers/AudioServiceController.kt:172-211`) ставит `MediaMetadata.Builder().setTitle(title).setArtist(artist=null)` **один раз** при старте. Живой ICY-трек в MediaSession **не пробрасывается**.

## Пробелы (study «куда показывать»)

| Поверхность | Сейчас показывает живой трек? | Примечание |
|---|:---:|---|
| Inline mini-контрол (`titleView`) | Да | `«станция - трек»`, одна строка, без разделения исполнитель/название |
| Системное уведомление / lock-screen (MediaSession, фон-сервис) | **Нет** | Показывает статичное имя станции; ожидаемое поведение любого радио-приложения - живая песня. Техника обновления есть: `player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(..).build())` - ровно так делает видеопуть `StreamPlaybackHelper.kt:451-467` |
| Карточка канала в сетке (`StreamGridAdapter`) | Нет | Возможная доп. поверхность (владельческое решение) |
| Выделенный full-screen now-playing экран | n/a | Отсутствует; потоки играют inline, не через отдельный экран плеера |

## Разделение исполнитель/название

ICY `StreamTitle` - одна строка. Раздельные поля `setArtist`/`setTitle` требуют эвристики (сплит по первому `" - "`). Надёжного стандарта нет; часть станций шлёт только название или служебный текст (реклама). Разделение - best-effort, применимо и к inline, и к уведомлению.

## Флейворы / ограничения

- `SUPPORT_STREAMS` = standard/legacy/noLegal/vr (lite/photos - вне объёма).
- Без API-специфики - целиком Media3 1.2.1 (ICY-экстрактор).
- Фоновый путь (`ENABLE_PERSISTENT_AUDIO_PLAYBACK`/`AudioPlaybackService`) - там живёт уведомление.
- Нулевое тестовое покрытие цепочки inline-audio.

## Рекомендация (см. §9 ADR)

- **Однозначная, платформенно-стандартная часть (автономна):** пробросить живой ICY-трек в MediaSession фон-сервиса, чтобы уведомление/lock-screen показывали текущую песню; best-effort разбор исполнитель/название.
- **Владельческое решение (§6):** какие **дополнительные in-app** поверхности заполнять (карточка канала в сетке? отдельный индикатор?) и нужно ли разделять исполнитель/название визуально отдельными строками.
