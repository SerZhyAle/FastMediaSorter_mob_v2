# Стратегическая спецификация: S0591 - Коллизия SYNTHETIC_STREAM_RESOURCE_ID с FAVORITES_RESOURCE_ID

**Ticket:** S0591
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-21
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - auto-captured during S0590 research (2026-06-21)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-21

**Источник:** авто-захват из research-отчёта по S0590 (out-of-scope находка).

**Симптом:** синтетический resource id для запуска трансляции в плеере (`SYNTHETIC_STREAM_RESOURCE_ID = -100L`) численно совпадает с `FAVORITES_RESOURCE_ID = -100L`. Сейчас безопасно по совпадению: ветка обработки stream-пути в загрузчике медиафайлов плеера срабатывает раньше, чем логика Favorites, и перехватывает запуск. Но это латентная ошибка - любое изменение порядка веток запуска может молча активировать режим Favorites для URL трансляции (плеер загрузит список избранного вместо одноэлементного синтетического списка трансляции).

**Evidence:**

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt:364` - объявление `SYNTHETIC_STREAM_RESOURCE_ID = -100L`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/MainViewModel.kt:140` - объявление `FAVORITES_RESOURCE_ID = -100L`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt:167-264` - ветка Favorites создаётся для `-100L` раньше, чем проверка stream-пути (строка 241) короткозамыкает поток.

**Вложения:** нет.

---

## 1. Проблема

- Синтетический resource id запуска трансляции в плеере численно совпадал с id виртуального ресурса Favorites.
- Из-за совпадения трансляция материализовалась как ресурс Favorites и наследовала его поведение во всех ветках `resource.id == Favorites` (кнопка избранного, разрешение учётных данных по файлу).
- Корректность держалась только на порядке веток загрузчика: stream-ветка перехватывала запуск раньше логики Favorites. Любое изменение порядка молча подменило бы трансляцию списком избранного.

---

## 2. Цели

- Дать синтетическим ресурсам без записи в БД непересекающиеся sentinel-идентификаторы.
- Запускать трансляцию как самостоятельный одноэлементный ресурс, не опознаваемый как Favorites ни в одной ветке.
- Свести sentinel-идентификаторы синтетических ресурсов к единому источнику правды, исключающему повторение коллизии.

---

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565, S0590

---

## 11. Критерии готовности (strategic-level)

- id трансляции и id Favorites гарантированно различны.
- Воспроизведение трансляции (VIDEO/RTSP) из экрана Трансляций открывается как одноэлементный список.
- Ни одна ветка `resource.id == Favorites` не срабатывает для запущенной трансляции.

---

## Last Audit

### Manual / on-device - 2026-06-26 (emulator-5554, Android 17 x86_64, standard debug `com.sza.fastmediasorter.debug` v2.60.6261.106)

**Outcome: PASS.** Imported the stream catalog, played a VIDEO/HLS channel ("1+1 Marafon", `https://dash2.antik.sk/live/1plus1_marathon/playlist.m3u8`) by tapping its row. Spec is `BlockNeedUserTest`; the `S0591:` probe fired as quoted. Evidence: `temp/streams_sweep_20260626/S0591_D01_player_no_favorite.png`.

- **Distinct sentinel id (criterion 1) - PASS.** Probe `S0591: stream launch resourceId=-200 (STREAM=-200 FAVORITES=-100)` - the stream launch uses the dedicated STREAM sentinel `-200`, never the FAVORITES `-100`.
- **Not treated as Favorites / no favorite button (criterion 3) - PASS.** The stream player's top command panel had NO `btnFavorite` (Back, File Information, Fullscreen, Rotation, Cast, Edit image, Save Frame, More actions, Previous, Next) - contrast with a normal file/Favorites player which renders `btnFavorite`. No Favorites branch logged.
- **One-item list (criterion 2) - SUPERSEDED by S0640.** The player now intentionally loads the full stream catalog for prev/next navigation, not a one-item list: probe `S0640: stream prev/next catalog size=2031 startIndex=1861 url=..` and the file-name overlay "1+1 Marafon (1+1 Marafon) (1862/2031)". This is the deliberate S0640 design (stream prev/next across the catalog), which post-dates S0591; the original "one-item list" sub-criterion is stale. The S0591 core invariant (id != Favorites, no Favorites behavior) holds regardless.
