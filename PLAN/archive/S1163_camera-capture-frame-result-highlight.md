# Стратегическая спецификация: S1163 - Визуальное подтверждение результата фотосъёмки

**Ticket:** S1163
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-24

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-24

**Текст:**

Во время фотографий выводится тост о результате. Но такой тост на экране фотоискателя выглядят не очень особенно когда камера повернута. Убрать тост но показать ярким цветом рамочку на "фрейме последнгей фотографии" на полсекунды - мол вот результат

---

## 1. Проблема

После каждого снимка экран съёмки показывает системный тост с именем сохранённого файла. Тост рисуется системой в её собственной ориентации и в её собственном месте, поэтому на видоискателе он ложится поперёк кадра, а при повёрнутой камере разъезжается с остальным интерфейсом экрана. Подтверждение при этом нужно: без него неясно, попал снимок в память или нет.

---

## 2. Цели

1. Экран съёмки больше не показывает тост об успешно сохранённом снимке.
2. Успешное сохранение подтверждается вспышкой яркой рамки на превью последнего снимка - примерно на полсекунды.
3. Подтверждение живёт внутри экрана съёмки и поворачивается вместе с ним.

**Non-goals:**

- Тосты и снекбары остальных путей съёмки - виджеты быстрого снимка, съёмка из обзора и с главного экрана. Там видоискателя приложения нет, окно закрывается сразу после кадра, и текстовое подтверждение остаётся единственным. Строка подтверждения общая, поэтому она сохраняется.
- Сообщения об ошибке сохранения. Ошибку рамкой не передать - она остаётся текстом.
- Звук и вибрация затвора.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Цвет рамки - яркий, заметный на любом кадре.
2. Длительность - около полусекунды, без затухания и анимации.

### 3.2 Жёсткие ограничения

- **Flavor:** изменение живёт внутри уже существующего экрана съёмки и собственных гейтов не добавляет, поэтому расходится ровно по тем сборкам, где этот экран уже есть.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** ничего не добавляется в кадровый цикл видоискателя - подсветка срабатывает один раз на снимок.
- **Совместимость данных:** новых настроек и миграций нет.
- **Локализация:** новых строк нет; существующая строка подтверждения остаётся ради прочих путей съёмки.
- **Доступность:** подтверждение становится чисто цветовым, и это осознанный размен - см. §7 и ADR-2.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Экран съёмки разложен на менеджеры-помощники; за реакцию на завершённое сохранение отвечает отдельный из них - он же владеет превью последнего снимка, кнопкой отправки и путём к последнему файлу. Тост поднимается прямо оттуда, в ветке успеха.

Превью последнего снимка - это скруглённая картинка Material, у которой уже есть собственная обводка как штатное свойство. Отдельной вью под рамку заводить не нужно, и разметку трогать тоже.

---

## 5. Предлагаемый подход

Заменить в ветке успеха текстовое подтверждение на подсветку того самого превью, которое в этой же ветке уже заполняется снимком.

### 5.1 Основные столпы / модули

- Подсветка результата: короткая вспышка обводки превью последнего снимка.
- Ветка успеха сохранения: перестаёт поднимать тост.

### 5.2 Потоки данных и событий

Снимок сохранён -> превью заполняется файлом -> обводка превью включается ярким цветом -> через полсекунды гаснет.

### 5.3 Точки расширяемости

- Цвет и длительность вспышки остаются одним местом, чтобы их можно было менять, не трогая ветку сохранения.
- Повторный снимок раньше, чем погасла прошлая вспышка, отменяет прошлую и начинает свою - счётчиков и очередей не заводится.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Рамка теряется на пёстром кадре | Средняя | Подтверждение не замечено | Цвет берётся из акцента темы, а не подбирается к кадру; ширина обводки заметная, проверяется на устройстве |
| Подтверждение стало только цветовым | Высокая | Пользователю с нарушением цветовосприятия сигнал слабее | Осознанный размен, ADR-2: превью в этот же момент меняет содержимое на новый снимок, и это второй, нецветовой признак |
| Серия быстрых снимков оставит рамку включённой | Средняя | Рамка горит постоянно | Каждая новая вспышка отменяет предыдущую и сама доводит обводку до нуля |
| Уход с экрана во время вспышки | Низкая | Работа продолжается после разрушения экрана | Вспышка живёт в области жизненного цикла экрана и гасится вместе с ним |

---

## 8. Влияние на пользователя (docs/FEATURES)

Экран съёмки подтверждает сохранённый снимок вспышкой рамки на превью, а не тостом поверх кадра.

---

## 9. Архитектурные решения (ADR)

**ADR-1. Обводка существующего превью вместо отдельной вью.**

Превью последнего снимка - скруглённая картинка Material, у которой обводка уже есть как свойство. Отдельная вью-рамка потребовала бы правки разметки в обеих ориентациях и совмещения со скруглением превью, не давая ничего сверх.

**ADR-2. Подтверждение остаётся только на экране съёмки.**

Прочие пути съёмки закрывают своё окно сразу после кадра, и превью, на котором можно вспыхнуть, там нет. Поэтому общая строка подтверждения не удаляется, а тост убирается ровно на видоискателе.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. Снимок на экране съёмки не вызывает тоста.
2. Сразу после снимка превью последнего снимка на короткое время обводится яркой рамкой.
3. Рамка гаснет сама, и превью возвращается к обычному виду.
4. Серия быстрых снимков не оставляет рамку гореть постоянно.
5. Прочие пути съёмки продолжают подтверждать сохранение текстом.
6. Ошибка сохранения по-прежнему сообщается текстом.

---

## Last Audit

### Manual device test - 2026-07-24 (emulator-5554, standard debug)

Device: `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424, density 420, virtual cameras. Build `2.60.7220.314-DEBUG` (already installed; not rebuilt). Camera screen entered from main menu -> Camera (multi-capture host). Timing measured from `screenrecord` captures decoded frame-by-frame (24-29 fps, ~35-40 ms per frame); the thumbnail stroke band was sampled per frame by saturation. Evidence: `temp/S1163/`.

- **No toast on the capture screen: PASS.** 11 successful shots taken across three recordings; no toast window appears in any decoded frame, and no `Toast` is enqueued in logcat for the success path. Expected: silent success confirmation | Actual: none of the frames carry a toast. Evidence: `s1163_burst.mp4`, `s1163_burst8.mp4`, `rotated_flash_full.png`.
- **Half-second stroke flash on the last-shot thumbnail: PASS.** Two isolated shots measured: lit runs `2.644..3.027 s` (421 ms) and `6.513..6.897 s` (421 ms) at 26.1 fps; the rotated run measured 442 ms at 29.4 fps. With frame quantisation (±1 frame each edge) that brackets the nominal 500 ms. Probe fires once per save: `S1163: saved-result highlight on the last-shot thumbnail`. Expected: ~500 ms bright stroke | Actual: 421-442 ms observed, stroke off afterwards. Evidence: `flash_analysis_portrait.txt`, `flash_analysis_rotated.txt`, `thumb_lit.png` vs `thumb_rest.png`.
- **Burst does not leave the stroke lit: PASS.** 8 shots fired 0.35 s apart produced 8 probes at 359-563 ms spacing (overlapping the 500 ms window, so each flash cancelled the pending reset). The stroke stayed lit `2.445..5.429 s` while shots kept arriving, flashed once more `5.595..5.926 s` for the last save, then stayed dark for the remaining 9.2 s of the recording. Expected: never permanently lit | Actual: extinguished 500 ms after the final save. Evidence: `flash_analysis_burst8.txt`, `logcat_burst8.log`.
- **Rotated camera: PASS.** Virtual accelerometer set to landscape (`adb emu sensor set acceleration 9.81:0:0`); the overlay labels (Camera / PHOTO / VIDEO / Wide) rotate while the window stays portrait-locked (S0754). A shot in that state flashed the same in-screen stroke (442 ms) with no toast, so the confirmation stays inside the app window and turns with the overlay. Evidence: `rotated_flash_full.png`, `flash_analysis_rotated.txt`.
- **Save failure still reports as text: PASS.** `DCIM/Camera` replaced by a regular file to make the destination unwritable; the save failed (`openOutputStream failed for content://media/external/images/media/...`), no highlight probe fired, and the screen showed the toast "Failed to save captured file". Directory restored afterwards. Evidence: `fail1.png`, `logcat_failure.log`.
- **Other capture paths keep their text confirmation: PARTIAL.** Browse-screen "Capture with camera" -> Save-as -> snackbar "Saved: CAP_20260724_170344.jpg" (`browse_snack.png`) - PASS. The quick-capture widget half is **UNVERIFIED**: no widget could be placed on the AVD launcher in this session, so the equivalent in-app route to the same trampoline (Quick launch panel -> Camera -> `CameraQuickCaptureActivity` with `PANEL_APP_WIDGET_ID`) was used instead; there the photo produced neither a toast nor a saved file, because the `noHistory` trampoline (`AndroidManifest.xml` line 503) is destroyed the moment `CameraCaptureActivity` comes to the front, so `onCaptureResult` never runs. Untouched by S1163 (whose change is confined to `CameraCaptureResultManager`) and reproducible only through that trampoline - a `/spec-draft` candidate for the owner, not a finding against this ticket. Evidence: `widget_confirm.png`, `logcat_widget.log`.

Cosmetic note (P3, not gating): `flashSavedHighlight()` resets `strokeWidth` to `0f`, not to the 1dp resting stroke the layout declares for `btnGalleryThumbnail` (`app:strokeWidth="1dp"`, `@color/camera_capture_control_stroke`). After the first capture the thumbnail therefore keeps a stroke-less resting look (`thumb_rest.png`). Strategic criterion 3 ("превью возвращается к обычному виду") is met in the sense that the bright ring disappears.

Overall: 5 of 6 checks PASS on the emulator; check 6 is half-verified (browse PASS, quick-capture widget UNVERIFIED). Status left at `BlockNeedUserTest` and the debug probe retained for the owner's own widget run.

### Re-audit - 2026-07-25 (static, no new device run required)

Both items this ticket was left `Partial` for are closed, and neither needed a fresh capture run.

- **Cosmetic P3 (stroke reset) - FIXED in code.** `CameraCaptureResultManager.flashSavedHighlight()` no longer clears the outline: after the 500 ms flash it restores `RESTING_STROKE_DP = 1f` and `R.color.camera_capture_control_stroke`, matching `activity_camera_capture.xml`'s `app:strokeWidth="1dp"` on `btnGalleryThumbnail`. Expected: preview returns to its normal look | actual: lines 100-104 set both the resting colour and the 1 dp width. The audit note above predates that fix.
- **Check 6, quick-capture half - PASSED under S1174.** The half was UNVERIFIED only because the `noHistory` trampoline was destroyed before `onCaptureResult` could run. That defect is S1174, now `Verified`: its Check 1 drove the same panel route and observed the "Save as CAP_20260724_180114.jpg" confirmation plus the saved file (117400 bytes) on disk - exactly the text confirmation this check looks for. S1174's build under test (`2.60.7241.749-DEBUG`) is newer than this audit's (`2.60.7220.314-DEBUG`) and already contained S1163, so that run also demonstrates S1163 did not disturb the route.
- **No ticket residue.** The only `S1163` occurrence left in `app_v2/src` is the permanent KDoc in `CameraCaptureResultManager` explaining why the toast was replaced; no `Timber.d("S1163:` probe remains.

Overall after re-audit: 6 of 6 checks PASS. Status -> `Verified`.
