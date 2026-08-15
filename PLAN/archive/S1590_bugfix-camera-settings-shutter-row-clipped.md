# Спецификация (compact bugfix): S1590 - ряд выдержки обрезается краем карточки диалога настроек камеры

**Ticket:** S1590
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-12
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-12

**Захвачено во время:** device-тест S1583 на RFCR110NBQJ (Galaxy S21+, Android 15 / SDK 35), standard debug
`v2.60.8112.319-DEBUG`. К S1583 отношения не имеет - запарковано без переключения активной задачи.

**Текст:**

Camera settings dialog: when "Manual ISO and shutter" is switched on while the dialog is in its rotated
(landscape-bucket) presentation, the Shutter row is drawn past the dialog card's left edge - its slider and
label are clipped by the card boundary. Found during the S1583 device test on RFCR110NBQJ (Galaxy S21+,
Android 15), build v2.60.8112.319-DEBUG. Evidence: temp/S1583/screens/step_04_manual_sensor_on.png and
step_05_after_slider_taps.png. Belongs to the S0924 rotate-and-swap-measure layout
(CameraSettingsDialogRotationManager), not to the S1583 value-snapping fix. Dedup: search.ps1 -Query
"shutter" -> no records; -Query "overflow" -> S1278 (VR HUD), S1407 (player overflow menu), neither related.

**Вложения:**
- скриншот: диалог с включённым ручным режимом, ряд выдержки уходит за край карточки - `temp/S1583/screens/step_04_manual_sensor_on.png`
- скриншот: то же после касаний слайдера экспозиции - `temp/S1583/screens/step_05_after_slider_taps.png`

---

## 1. Проблема / симптом

- Экран: диалог настроек камеры (`dialog_camera_settings.xml`) внутри `CameraCaptureActivity`, standard debug
  `v2.60.8112.319-DEBUG`, RFCR110NBQJ (Galaxy S21+, Android 15 / SDK 35).
- Repro: открыть настройки камеры, держать телефон боком (landscape-корзина поворота, содержимое диалога
  повёрнуто менеджером S0924), включить переключатель «Ручные ISO и выдержка».
- Наблюдается: ряд выдержки уходит за край карточки диалога - слайдер и подпись обрезаны границей карточки.
  Прокрутить содержимое обратно нечем.
- Эвиденс: `temp/S1583/screens/step_04_manual_sensor_on.png`, `temp/S1583/screens/step_05_after_slider_taps.png`.

---

## 2. Корневая причина

- В повёрнутой landscape-подаче физическая высота содержимого - это короткая экранная ось: окно диалога
  получает фиксированный размер из `CameraSettingsDialogRotationManager.reshapeWindow`, и содержимое меряется
  по перевёрнутым осям в `RotatingContentContainer.onMeasure`.
- Два столбца `ColumnFlowLayout` в landscape уже выбирают этот запас целиком. Включение ручного режима
  добавляет сразу два слайдерных ряда (ISO и выдержка), и хвост содержимого - ряд выдержки, а за ним и сама
  строка Cancel/Apply - выходит за пределы карточки.
- Прокрутки в разметке не было вовсе: весь корневой `LinearLayout` был нескроллируемым, поэтому переполнение
  не поглощалось ничем и просто обрезалось границей карточки.

---

## 3. Исправление

- `app_v2/src/main/res/layout/dialog_camera_settings.xml`: `ColumnFlowLayout` с рядами настроек обёрнут в
  `ScrollView`; заголовок и строка действий (Cancel/Apply) остаются закреплёнными снаружи прокрутки, поэтому
  кнопки видны всегда.
- У `ScrollView` намеренно `layout_height="wrap_content"` + `layout_weight="1"`, а не `0dp` + вес: под
  `AT_MOST`-спецификацией высоты этого диалога взвешенный `0dp`-ребёнок схлопывается в ноль, тогда как
  `wrap_content` ужимается ровно на величину переполнения и в портрете, где переполнения нет, остаётся
  нетронутым.
- Kotlin-код не менялся: `CameraSettingsDialogRotationManager` и `ColumnFlowLayout` работают как есть,
  область прокрутки поглощает переполнение внутри уже выданных им размеров.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0924 - поворот и перемер содержимого диалога настроек камеры; S1583 - тикет, во время
  device-теста которого находка всплыла, пересечений по коду нет.
- **UI-решение:** прокручивается только область настроек; заголовок и пара Cancel/Apply не скроллятся - это
  сохраняет доступ к кнопкам при любом объёме содержимого.

---

## 4. Проверка

- `pwsh -NoProfile -File ./a.ps1 fr` - ресурсы и манифест собираются: PASS (exit 0).
- `pwsh -NoProfile -File ./a.ps1 d` - standard debug APK `v2.60.8112.319-DEBUG` собран: PASS (exit 0);
  копия для установки - `DOWNLOADS/FastMediaSorter_standard_debug.apk`.
- `post-change.ps1 -ScopeToFile` по обоим изменённым файлам: PASS.
- Device-тест (нужен физический поворот телефона, эмуляцией недостижим): открыть настройки камеры, повернуть
  устройство в landscape, включить «Ручные ISO и выдержка».
  - expected: ряды ISO и выдержки видны целиком, содержимое прокручивается, Cancel/Apply остаются на месте.
  - actual: заполнить по итогам теста.
- Портретная подача не изменилась: содержимое не прокручивается, потому что переполнения нет.

---

## Last Audit

- Дата: 2026-08-12, `/spec-all`.
- Правка разметки уже присутствует в рабочем дереве и описана комментарием `S1590:` в шапке
  `dialog_camera_settings.xml`. `drift-check.ps1` этого не увидел - он сканирует только `.kt`.
- Проверено: ресурсная сборка standard debug проходит (все задачи UP-TO-DATE, exit 0).
- Остаточный пробел: визуальное подтверждение на устройстве. Требует физического поворота телефона -
  `CameraCaptureActivity` заблокирована в портрете (S0754), корзина поворота приходит с датчика, adb её
  не подделывает. Отсюда `BlockNeedUserTest`.
