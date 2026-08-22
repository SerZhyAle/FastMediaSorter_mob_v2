
# Спецификация (compact bugfix): S1702 - Жёлтая отладочная рамка и перекрестье рисуются поверх Lens-оверлея в релизе

**Ticket:** S1702
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-15
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-08-16 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Текст:**

Найдено при исследовании точности OCR-оверлея (перенос знаний из соседнего проекта doc-html-translate,
`docs/OCR_OVERLAY_ACCURACY.md`). Вне области той задачи - там пишется документ, а это дефект в коде.

`TranslationOverlayView.onDraw` безусловно обводит прямоугольник области отображения картинки жёлтым
штрихом шириной 5 px и дорисовывает перекрестье в его левом верхнем углу. Комментарий над блоком говорит
`// DEBUG: Draw the image display rect boundary to verify alignment with PhotoView`, но никакого гейта нет:
ни `BuildConfig.DEBUG`, ни настройки, ни флага. Значит это видит каждый пользователь каждого флейвора при
каждом включении Lens-перевода картинки.

Verbatim, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt:569-582`:

```kotlin
        // DEBUG: Draw the image display rect boundary to verify alignment with PhotoView
        // This helps diagnostics if the overlay appears shifted relative to the image
        imageDisplayRect?.let { rect ->
            val debugPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.YELLOW
                strokeWidth = 5f
            }
            canvas.drawRect(rect, debugPaint)

            // Draw a crosshair at the top-left of the rect to verify origin
            canvas.drawLine(rect.left - 20, rect.top, rect.left + 20, rect.top, debugPaint)
            canvas.drawLine(rect.left, rect.top - 20, rect.left, rect.top + 20, debugPaint)
        }
```

Побочно в том же файле: `Paint` аллоцируется внутри `onDraw` на каждый кадр - отдельная мелочь, но чинится
тем же движением, если рамку решат оставить под гейтом.

Сопутствующий вопрос для §3: рамка ставилась как диагностика выравнивания оверлея относительно PhotoView.
Если её убрать совсем, диагностика теряется; в `docs/OCR_OVERLAY_ACCURACY.md` §9 записано, что нам вообще
нужен диагностический канал для оверлея. Возможно, правильный ответ - не удалить, а завести за настройку
разработчика/`BuildConfig.DEBUG`, а не просто вырезать.

---

## 1. Проблема / симптом

Жёлтая рамка + перекрестье поверх изображения при включённом Lens-оверлее перевода. Воспроизводится по
построению во всех флейворах с `ENABLE_TRANSLATION`, в release-сборке в том числе. Пользовательский эффект:
посторонняя разметка поверх контента.

---

## 2. Корневая причина

Подтверждено чтением кода: в `TranslationOverlayView.onDraw` (строки 589-602) блок обводки
`imageDisplayRect` жёлтой рамкой с перекрестьем выполняется безусловно - нет ни `BuildConfig.DEBUG`,
ни настройки разработчика, ни флага. Отладочная диагностика выравнивания оверлея относительно
PhotoView дошла до релиза без гейта. Побочно: `debugPaint` аллоцируется внутри `onDraw` на каждый
кадр. Комплекс решения по диагностическому каналу для оверлея - отдельная работа, описанная в
`docs/OCR_OVERLAY_ACCURACY.md` §9; она не блокирует этот багфикс.

---

## 3. Исправление

Решение (принято в рамках `/spec-all`, поскольку сессия не интерактивна): **сохранить рамку под
гейтом `BuildConfig.DEBUG`**, не удалять. Обоснование: блок - единственная диагностика выравнивания
оверлея относительно PhotoView, а `docs/OCR_OVERLAY_ACCURACY.md` §9 прямо фиксирует потребность в
диагностическом канале для оверлея; удаление потеряло бы её без замены. Под гейтом релизные сборки
чисты, а отладочная ценность сохраняется. Попутно `debugPaint` выносится в поле класса, чтобы не
аллоцировать `Paint` на каждый кадр `onDraw` (в debug-путях это было бы каждокадровой аллокацией).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0451 (translation-overlay-size-to-original, Archived) - тот же файл, тот же оверлей.
- **Owner decision required:** удалить рамку совсем или сохранить её под отладочным гейтом.
  **Принято в `/spec-all` (2026-08-16): сохранить под `BuildConfig.DEBUG`** - см. обоснование выше;
  оператор может пересмотреть решение и удалить блок позже.

---

## Phase 01 - Gate the debug rect behind BuildConfig.DEBUG

### Step 01.1 - Gate the debug rect block and hoist debugPaint

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap the yellow debug-rect/crosshair block at the end of `onDraw` in `if (com.sza.fastmediasorter.BuildConfig.DEBUG) { ... }`. Hoist the `debugPaint` allocation out of `onDraw` into a private class field (initialized once with the same style/color/strokeWidth). Add the `com.sza.fastmediasorter.BuildConfig` import.

**Why:**

Every user of every flavor with `ENABLE_TRANSLATION` currently sees a yellow debug frame and crosshair drawn over the content in release builds; gating behind `BuildConfig.DEBUG` removes the stray markup from release while keeping the only existing overlay-alignment diagnostic, whose need is recorded in `docs/OCR_OVERLAY_ACCURACY.md` §9.

**Verification:**

- `Grep` - `BuildConfig.DEBUG` present exactly once in `TranslationOverlayView.kt`.
- `Grep` - `val debugPaint = Paint().apply` matches zero times inside `onDraw`; the field `private val debugPaint` exists at class level.
- `Grep` - `import com.sza.fastmediasorter.BuildConfig` present.
- Project compiles - `a.ps1 dq` (standard debug) PASS.

**Status:** `[x]` done

### Phase Done Criteria

- [ ] Step 01.1 done, standard debug build PASS.
- [ ] No `Timber.d("S1702:` tags introduced (verification by grep below, not by device).

---

## 4. Проверка

- `Grep` в `TranslationOverlayView.kt`: блок рамки/перекрестья находится внутри `if (BuildConfig.DEBUG)`,
  `debugPaint` - поле класса.
- Сборка `standard debug` (а значит, и release-путь по `BuildConfig.DEBUG == false`) проходит.
- На устройстве (ручная проверка оператором, отложено): включить Lens-перевод картинки в release-сборке -
  жёлтой рамки и перекрестья нет; в debug-сборке рамка по-прежнему видна.

---

## Last Audit

**Date:** 2026-08-16
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] Визуально на устройстве: в release-сборке жёлтой рамки и перекрестья над Lens-оверлеем нет; в debug-сборке рамка видна. (Гарантировано компилятором: блок внутри `if (BuildConfig.DEBUG)` - релизная ветка статически недостижима; визуальная проверка - подтверждение.)

### Checks

1. PASS - `if (BuildConfig.DEBUG)` оборачивает блок рамки/перекрестья в `TranslationOverlayView.kt` (единственное вхождение в коде; второй grep-хит - упоминание в комментарии).
2. PASS - `debugPaint` вынесен в поле класса (строка 110), аллокация `Paint()` из `onDraw` удалена (`android.graphics.Paint()` - 0 вхождений).
3. PASS - `import com.sza.fastmediasorter.BuildConfig` присутствует.
4. PASS - `standard debug` сборка (`a.ps1 dq`) - BUILD SUCCESSFUL.
5. PASS - `Timber.d("S1702:` тегов нет (0 вхождений) - статус не `BlockNeedUserTest`.
6. PASS - open-items gate (`check-open-items-carried.ps1`) - exit 0, переносить нечего.
7. PASS - dev log содержит запись по S1702.

### Out-of-scope discoveries

- При аудите обнаружено, что соседний тикет S1704 (plate colour sampled from wrong bitmap) уже реализован в этом же файле (`ocrPointToSource`, помечено S1704) - покрыто его собственным тикетом, не этим.
