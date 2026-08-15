# Спецификация: S1116 - VR immersive-браузер: миниатюры + читаемость (UX)

**Ticket:** S1116
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-19
**Tier:** 2 - UI (ad-hoc)
**Roadmap entry:** Ad-hoc - выделено из device-теста S0963 на Quest 3 (2026-07-19)

> **Scope:** Draft-инбокс. UX-доводка immersive-браузера. Функционально браузер работает (проверено, S0963 -> Verified); здесь - юзабилити.

---

## 0. Захваченный материал (verbatim)

Владелец на device-тесте S0963 (Quest 3, noLegal), 2026-07-19:

> S0963 - я вижу микробраузер - он отвратителен и шрифт почти не читается, нет миниатюр и неясно как пользоваться, но в целом - работает

Решение владельца: S0963 закрыть как работающий, UX вынести отдельным тикетом (миниатюры + читаемость).

---

## 1. Проблема

Immersive-браузер VR-кинотеатра (`ImmersiveBrowseActivity`) функционально работает - сетка, навигация лучом, выбор, вход в папки, запуск, - но непригоден к использованию:

- **Нет миниатюр**: ячейки сетки - серые заглушки (по скриншоту), хотя ожидается превью видео/3D-изображений.
- **Нечитаемый шрифт**: подписи файлов и бейджи (180/SBS/OU) крошечные.
- **Мелкая панель**: вся сетка мала в поле зрения, трудно попасть/прочитать.
- **Неясные аффордансы**: непонятно, как пользоваться (нет явных подсказок навигации/страниц).

## 2. Доказательства

- Скриншот: `C:\Common\com.sza.fastmediasorter.debug-20260719-010412.jpg` - сетка 2x4 серых ячеек, крошечные подписи, бейджи, «page 1/3».
- Полный лог сессии: `temp/scratch/vr_session_20260719/logcat_full.log`.

## 3. Цели

1. В каждой ячейке - реальная миниатюра (кадр видео / уменьшенное изображение), декод в пределах heap-бюджета (шаблон S0960).
2. Крупный читаемый шрифт подписей и бейджей на HUD-панели браузера.
3. Более крупные ячейки/панель в поле зрения; комфортная зона попадания лучом.
4. Явные аффордансы: индикатор страниц, подсказка навигации/выхода.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0963 (immersive-браузер - этот тикет доводит его UX), S0773 (эпик VR-кинозал), S0964 (HUD плеера - координировать шрифты/стиль), S0960 (идиома heap-budget декода)
- **UI placement:** HUD-квад существующего immersive-браузера - увеличиваем квад + панель и добавляем реальные миниатюры; без нового экрана и точки входа.
- **Visibility / flavor:** только noLegal (VR), runtime XR-gated. Standard/lite/photos/legacy не затронуты.
- **Fallback:** сбой декода миниатюры (бюджет исчерпан / формат не поддержан) оставляет текущую серую заглушку ячейки - сетка не ломается.

## 4. Заметки (для research)

- `ImmersiveBrowseActivity` + `ImmersiveBrowseContentLoader` (`src/vr/.../ui/xr/browse/`) - загрузка контента и рендер сетки.
- Панель-HUD рисуется в native (Canvas -> RGBA -> texture), как у плеерного HUD (`HudCanvasRenderer`); читаемость - вопрос размеров шрифта/ячейки в этом рендере.
- Миниатюры: декод кадра видео (Media metadata retriever / ExoPlayer frame) и картинок - в пределах heap-бюджета (переиспользовать подход S0960 `pickSampleSizeForBudget`).
- Только `noLegal` (VR). Локализация подписей - EN/RU/UK, если появятся новые строки.

## 5. Связи

- **S0963** (immersive-браузер, Verified) - этот тикет доводит его UX.
- **S0773** (эпик VR-кинозал) - S1116 в его орбите.
- **S0964** (HUD плеера) - если HUD «страшненький» тоже трогаем, координировать шрифты/стиль.

---

## 6. Implementation (Simple path)

Research verdict: all four defects are fixable in Kotlin, no C++ edit.

- **Root cause (readability):** native `setHudQuadSize` (S0964) is bound end-to-end (`DiagnosticXrRuntime` -> JNI -> `xr_hud_set_quad_size`). The runtime is a process singleton, so every mode must re-assert its own quad. `DiagnosticXrActivity` asserts a `0.48x0.30 m` panel quad; `ImmersiveBrowseActivity` never asserts, so the browser inherits the `0.30x0.113 m` banner quad -> «микро-браузер».
- **Root cause (no thumbnails):** `ImmersiveThumbnailDecoder` only tries Glide `asBitmap().load(model)`, which yields no frame for a local video file -> grey placeholder for the video-heavy test matrix. The flat browser uses `MediaMetadataRetriever.getFrameAtTime` (`VideoPosterExtractor`) for video posters.

Build/validation target: **noLegal debug** (`a.ps1 nd`) - the flavor that mounts `src/vr` (`SUPPORT_VR_PLAYER=true`).

### Phase A - readability (quad + panel + fonts)

- A1 `ImmersiveBrowseActivity`: add `BROWSE_QUAD_WIDTH_M=0.80f` / `BROWSE_QUAD_HEIGHT_M=0.40f` (2:1, matches panel). Call `runtime.setHudQuadSize(..)` in `onSessionReady()` before `drawAndPushGrid()`, and re-assert in `returnToBrowse()`. Verify: Grep `setHudQuadSize` present in `ImmersiveBrowseActivity.kt`.
- A2 `ImmersiveBrowseGridRenderer`: `PANEL_WIDTH` 1024 -> 1536, `PANEL_HEIGHT` 512 -> 768 (keeps 2:1); scale font + geometry constants up for legibility (label >= 40, badge >= 34, breadcrumb >= 48, page/chevron >= 44). Verify: Grep `PANEL_WIDTH = 1536`.
- A3 `ImmersiveBrowseActivity`: `CELL_THUMB_PX` 256 -> 384 (crisper thumbs in the larger cells). Verify: Grep `CELL_THUMB_PX = 384`.

### Phase B - real thumbnails (video frames)

- B1 `ImmersiveThumbnailDecoder`: add a video branch - `MediaMetadataRetriever.getFrameAtTime(0, OPTION_CLOSEST_SYNC)` with a native-heap guard (mirror `VideoPosterExtractor`), downscale to cell px, keep the byte-budget accounting; images/gif stay on Glide. Signature `decode(model, isVideo, cellW, cellH)`. Verify: Grep `getFrameAtTime` present in `ImmersiveThumbnailDecoder.kt`.
- B2 `ImmersiveBrowseActivity`: `decodeVisibleThumbnails` passes `isVideo = cell.mediaType == VrMediaType.VIDEO`. Verify: Grep `VrMediaType.VIDEO` in `decodeVisibleThumbnails`.

### Phase C - affordances

- C1 `ImmersiveBrowseGridRenderer`: page indicator becomes language-neutral `X / Y` (drop the English word); add a compact glyph hint (paging + back) in the footer. Verify: Grep footer renders `X / Y`.

### Debug tags (BlockNeedUserTest)

- One `Timber.d("S1116: ..")` at the `onSessionReady()` quad-assert flow entry; one at the video-frame decode flow entry.

### Device verification (headset, deferred - no device this session)

- Quad large + comfortable; video cells show real first frames; labels/badges legible; footer hint + page indicator visible.
