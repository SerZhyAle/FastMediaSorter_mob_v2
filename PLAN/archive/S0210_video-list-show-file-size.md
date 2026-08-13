---
Ticket: S0210
Status: Verified
Priority: 50
Date: 2026-05-15
Tier: 1 — Quick Win (ad-hoc)
---

# S0210 — Размер файла в инфо-строке списка просмотра

## Problem

В Browse-списке строка-описание под именем файла для видео показывает `resolution • duration`, для фото — `resolution • dateTaken`, для аудио с тегами — `artist - title • duration`. Размер файла в этих случаях не виден, пока пользователь не откроет дополнительные сведения. Для видео это особенно неудобно — размер важен при выборе, что копировать/перемещать.

## Approach

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterFileInfoFormatter.kt` → в `buildFileInfo` для веток `VIDEO`, `IMAGE`/`GIF`, `AUDIO`-с-метаданными добавить хвостовой сегмент `size`, формируемый через `formatFileSize(file.size)` при `file.size > 0`, иначе сегмент пропускается (через `listOfNotNull`). Порядок: существующие сегменты, затем `size` в конце. Ветка `isDirectory` остаётся без изменений. Ветка `else` (`buildLegacyFileInfo`) уже содержит размер — не дублировать. `AUDIO`-без-метаданных также не трогаем — уходит в `legacyInfo`, размер там уже есть.

## Done criteria

- В Browse-списке для видеофайла с известным `size > 0` инфо-строка содержит `<width>x<height> • <duration> • <size>` (например `1920x1080 • 12:34 • 1.2 GB`).
- Для изображения/GIF с `size > 0` инфо-строка содержит `<width>x<height> • <dateTaken> • <size>`.
- Для аудио с тегами и `size > 0` инфо-строка содержит `<artist - title> • <duration> • <size>`.
- При `size <= 0` (типичный FTP/неполные метаданные) хвостовой сегмент размера отсутствует, остальные сегменты сохраняются.
- Папки (`isDirectory`) и legacy-fallback (`size • date`) визуально без изменений — размер не дублируется.

## Last Audit

**Date:** 2026-05-15
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [x] Browse-список: видео-строка показывает `WxH • duration • size`.
- [x] Browse-список: фото/GIF-строка показывает `WxH • dateTaken • size`.
- [x] Browse-список: аудио с тегами показывает `artist - title • duration • size`.
- [x] `size <= 0` — хвостовой сегмент отсутствует, остальные сохранены.
- [x] Папки и legacy-fallback внешне без изменений.
