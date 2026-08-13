# Стратегическая спецификация: S1047 - OCR crop frame bottom edge unreachable + border transparency

**Ticket:** S1047
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-14
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-14
**Tactical spec:** inline (compact) - см. раздел «Фазы реализации»

<!-- auto-approved by /spec-all - 2026-07-15 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-14

**Текст:**

жест скриншот - ocr - перевод.  На следующем этапе рамка красная чтобы вырезать
1. нижнюю границу рамки не выхватить и не сдвинуть - вероятно она уезжает под панель "Переснять", языки, "ОК". нижний край нужно поднять
2. Саму яркокрасную границу нужно оставить но сделать чуть-чуть прозрачной (на 25%)
проверить что так же рабюотает для фотография- OCR - перевод

---

## 1. Проблема

На шаге обрезки Camera-OCR-Translate (жест-скриншот → OCR → перевод и фото → OCR → перевод) яркокрасная рамка выбора рисуется по всей площади фотографии. Нижняя граница рамки и её ручка перетаскивания оказываются вплотную к нижней командной панели («Переснять», языки, «ОК») и практически не захватываются пальцем - пользователь не может поднять или сдвинуть нижний край. Дополнительно сплошная непрозрачная красная линия перекрывает текст под собой, мешая прицелиться по нижней строке.

---

## 2. Цели

1. Нижний край рамки всегда отстоит от нижней командной панели настолько, что его ручка гарантированно захватывается и двигается пальцем.
2. Начальное (default) положение рамки ставит нижнюю границу в захватываемую зону, а не вплотную к панели.
3. Красная граница становится на 25% прозрачной (75% непрозрачности), сохраняя яркий красный цвет.
4. Поведение идентично для обоих входов: жест-скриншот и съёмка фото.

**Non-goals:**

- Переработка командной панели или её раскладки.
- Изменение логики OCR / перевода.
- Изменение поведения верхней и боковых границ рамки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Сохранить возможность выбирать почти всю площадь фото - жертвуем только узкой нижней полосой ради захватываемости края.

### 3.2 Жёсткие ограничения

- **Flavor:** все (standard, lite, photos, legacy) - код в `src/main`, шаг OCR-crop общий.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** без изменений - рисуется то же число примитивов.
- **Совместимость данных:** нет.
- **Локализация:** не затрагивается - новых строк нет.
- **Доступность:** контур остаётся отличимым по положению и затемняющему скриму, не только по цвету; `contentDescription` без изменений.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **Нижний край рамки:** поднять так, чтобы он не уходил под панель и захватывался пальцем.
- **Красная граница:** оставить цвет, снизить непрозрачность на 25% (до 75%).
- **Проверка путей:** оба входа - жест-скриншот и фото.

---

## 4. Контекст текущей архитектуры

Шаг обрезки - это состояние `layoutCropState` тонкой activity-оболочки Camera-OCR; вся геометрия рамки инкапсулирована в кастомном `View`-оверлее. Оверлей и превью-`ImageView` лежат в одном `FrameLayout` (weight=1) над `wrap_content` командной панелью, поэтому визуально не пересекаются - но оверлей вычисляет допустимую область рамки по всей своей высоте вплоть до нижнего пикселя, который вплотную примыкает к панели. Захват ручки нижнего края ограничен узкой полосой у самой границы с панелью - отсюда «не выхватить». Цвет границы берётся из общего цветового ресурса на полную непрозрачность.

---

## 5. Предлагаемый подход

Ввести в оверлее зарезервированную нижнюю «безопасную» полосу: эффективная нижняя граница допустимой области рамки поднимается на фиксированный отступ от низа контента. Отступ применяется единообразно в трёх точках геометрии: расчёт default-рамки, ограничение перетаскивания нижнего края, ограничение сдвига всей рамки вниз. Прозрачность границы задаётся альфой кисти контура (75%); скрим и цветовой ресурс не трогаются.

### 5.1 Основные столпы / модули

- Кастомный crop-оверлей - единственный затронутый класс.

### 5.2 Потоки данных и событий

- Показ шага обрезки → привязка размера изображения → пересчёт контентного прямоугольника с учётом нижнего safe-inset → отрисовка полупрозрачного контура.

### 5.3 Точки расширяемости

- Величина нижнего safe-inset и альфа контура - именованные константы, легко подстроить.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Safe-inset «съедает» нижнюю область на очень низком фото | Низкая | Нельзя выбрать самый низ изображения | Умеренный отступ (48dp) + существующий min-side фолбэк |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - исправление UX существующей фичи Camera-OCR-crop.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. На шаге обрезки нижнюю ручку рамки можно захватить и подвинуть пальцем, не задевая панель.
2. Default-рамка появляется с нижним краем в захватываемой зоне.
3. Красная граница видимо полупрозрачна (75%), текст под линией просматривается.
4. Поведение одинаково для жеста-скриншота и для фото.

---

## Фазы реализации (compact tactical)

### Phase 1 - Bottom safe-inset + border alpha in the crop overlay

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CropOverlayView.kt`

1. Add companion constants `BOTTOM_SAFE_INSET_DP = 48f` and `BORDER_ALPHA = 191` (75% opaque = 25% transparent).
   - Verify: both constants present in `companion object`.
2. Add field `private val bottomSafeInsetPx = BOTTOM_SAFE_INSET_DP * density`.
   - Verify: field compiles, used by the geometry helpers.
3. In the `borderPaint` initializer, after `color = ...`, set `alpha = BORDER_ALPHA`.
   - Verify: `alpha = BORDER_ALPHA` present in the `borderPaint` apply block.
4. Add `private fun effectiveBottom(): Float` returning `contentRect.bottom - bottomSafeInsetPx`, floored at `contentRect.top + minSidePx`.
   - Verify: never returns above `contentRect.bottom`, never below `contentRect.top + minSidePx`.
5. In `applyDefaultFrame`, set the default bottom to `effectiveBottom()` instead of `contentRect.bottom - defaultInsetPx`; keep the existing min-side fallback.
   - Verify: for a tall photo the default `selection.bottom == effectiveBottom()`.
6. In `clampBottom`, coerce the upper bound to `effectiveBottom()` (guarded so `min <= max`) instead of `contentRect.bottom`.
   - Verify: dragging the bottom edge cannot pass `effectiveBottom()`.
7. In `moveBy`, cap the downward move so `selection.bottom` never passes `effectiveBottom()`.
   - Verify: moving the whole frame down stops at `effectiveBottom()`.
8. Debug tag (BlockNeedUserTest): add `Timber.d("S1047: ...")` at the start of `setImageSize` (add the Timber import).
   - Verify: exactly one `Timber.d("S1047:` line across `.kt`.
9. Build `standard debug` (`a.ps1 dq`).
   - Verify: BUILD SUCCESSFUL.

**Device verification (owner):** on the crop step, confirm the bottom handle is grabbable and movable clear of the command bar, and the red border is visibly ~75% opaque - for both the gesture-screenshot and the photo capture entry.

---

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (compact bugfix)
**Outcome:** Verified
**Counts:** PASS 2 · WARN 0 · FAIL 0

Device-verified on emulator-5554: photo OCR crop bottom handle grabbable, clamps ~48dp clear of the RETRY/AUTO/OK bar, red border renders all four sides. Gesture-screenshot entry code-confirmed (same `CropOverlayView.setImageSize` path, no per-entry branching). `S1047:` probe removed on Verified flip.

### Manual (device test) - 2026-07-15, emulator-5554 (Android 17 / API 37), standard-debug v2.60.7151.516

**Verdict: PASS** (photo OCR entry verified live; gesture-screenshot entry code-confirmed).

Setup note: the "Camera OCR translation" program is gated by `cameraOcrTranslationEnabled`, whose settings toggle only appears when `isTranslationAvailable() && isOcrSupported()`. On this emulator the toggle was hidden (translation capability reported unavailable), so the flag was enabled directly in the app DataStore (`files/datastore/settings.preferences_pb`: `camera_ocr_translation_enabled`, `camera_ocr_only`, `enable_ocr` -> true) to reach the flow. Crop-step geometry is independent of that flag.

Photo OCR entry (main -> overflow -> Camera OCR translation -> in-app camera -> shutter -> crop step):
- Probe fired: `D CropOverlayView: S1047: crop overlay bound w=1280 h=960` - the S1047-modified `setImageSize` path executed.
- Bottom handle grabbable / movable: expected = draggable clear of the bar; actual = dragging the bottom-center edge up shrank the frame cleanly (evidence 03). PASS.
- Bottom clamp clear of command bar: expected = bottom edge stops in a grabbable zone above the RETRY/AUTO/OK bar, not under it; actual = dragging the bottom edge hard downward stops ~48dp above the image content bottom (the reserved safe-inset), with a large gap to the bar (evidence 04). Never reaches or hides behind the bar. PASS.
- Red border render: expected = full 4-side border, ~75% opaque (25% transparent); actual = all four sides render, reading as a translucent bright pink-red (not solid opaque), consistent with `BORDER_ALPHA = 191`. PASS.

Gesture-screenshot entry: not reachable on the emulator (requires the screenshot-gesture / screen-capture overlay service plus a staged source image via `EXTRA_SOURCE_IMAGE_PATH`). Code-confirmed equivalent: both entries converge on the same `CameraOcrTranslateActivity.showCropStep -> CropOverlayView.setImageSize`; the only difference is image origin (camera capture vs staged file), which does not affect crop geometry. `effectiveBottom()`, `BOTTOM_SAFE_INSET_DP = 48f` and `BORDER_ALPHA = 191` live in `src/main` with no per-entry branching, so the verified photo-path behavior applies identically.

Evidence (`temp/S1047/`): `02_photo_crop_initial.png` (default frame, bottom edge lifted off content bottom), `03_bottom_handle_dragged_up.png` (bottom edge moved up), `04_bottom_clamp_downward.png` (downward drag clamps at safe-inset, clear of bar).
