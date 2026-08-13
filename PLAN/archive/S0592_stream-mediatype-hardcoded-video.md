# Стратегическая спецификация: S0592 - Аудиотрансляции открываются в плеере как VIDEO

**Ticket:** S0592
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

**Симптом:** при запуске трансляции в плеере синтетический медиафайл всегда получает `type = MediaType.VIDEO`, включая аудио-only потоки (HLS-audio, ICY/Shoutcast), у которых в каталоге трансляций `mediaKind = "AUDIO"`. Поле `mediaKind` доступно в модели трансляции, но не прокидывается в плеер и не используется при сборке синтетического медиафайла. Следствие - неверная ветка отрисовки оверлея и неверное «is audio» branching, если аудиопоток открыт в полноэкранном плеере.

**Evidence:**

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt:241-264` - `type = MediaType.VIDEO` захардкожен для всех stream-URL.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt:26` - доступное поле `mediaKind`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt:172-188` - точка запуска, где известен `mediaKind`.

**Заметки для проработки (удалить при доработке):**

- Возможное решение: маппить `mediaKind` -> `MediaType` при сборке синтетического файла (через тот же lookup по URL, что и S0590, либо через Intent extra).
- Пересекается по точкам касания с S0590 (заголовок плеера трансляции) - возможно стоит делать одной волной.

**Вложения:** нет.

---

## 1. Проблема

При запуске трансляции в полноэкранном плеере синтетический медиафайл всегда получал `type = MediaType.VIDEO`, включая аудио-only потоки (HLS-audio, ICY/Shoutcast), у которых в каталоге трансляций `mediaKind = "AUDIO"`. Поле `mediaKind` было доступно в модели трансляции, но не использовалось при сборке синтетического файла, поэтому аудиопоток открывался по видео-ветке (неверный оверлей и «is audio» branching).

---

## 2. Цели

1. Аудио-only трансляция (`mediaKind = "AUDIO"`) открывается в плеере по аудио-ветке (`MediaType.AUDIO`).
2. Видео- и RTSP-трансляции, а также URL без строки в каталоге, сохраняют `MediaType.VIDEO` (без регрессий).

**Non-goals:**

- Заголовок плеера трансляции (S0590) и устойчивость воспроизведения (S0634).

---

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565, S0590

---

## 11. Критерии готовности (strategic-level)

1. Открытие трансляции с `mediaKind = "AUDIO"` запускает плеер по аудио-ветке.
2. Открытие видео/RTSP-трансляции запускает плеер по видео-ветке.
3. URL без строки в каталоге трансляций остаётся `MediaType.VIDEO` (безопасный fallback).

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Notes

- `PlayerMediaFilesLoader.kt` (~272-281): `streamSource = getStreamSourceByUrlUseCase(streamPath)`; `streamType = if (streamSource?.mediaKind == "AUDIO") MediaType.AUDIO else MediaType.VIDEO`; the synthetic `MediaFile` is built with `type = streamType` instead of the previous hardcoded `MediaType.VIDEO`. Audio-only streams open on the audio branch; video/RTSP/unknown-URL keep VIDEO.
- Strategic §1/§2/§11 were capture-stage placeholders - filled from the §0 inbox + verified code during this audit (forward-bias patch).
- Debug-tag invariant PASS: zero `Timber.d("S0592:` tags (the `S0592:` token at ~272 is a code comment, not a probe). The neighbouring `S0591:` probe belongs to S0591 (BlockNeedUserTest) - left in place.
- FEATURES trilingual EXEMPT: behaviour-correctness fix on an existing stream path, no new showcase capability.

### Manual / on-device

- [ ] Open an audio-only catalog stream (`mediaKind = "AUDIO"`): the player uses the audio branch/overlay, not the video layout. A video stream and an unknown URL still open as video.
