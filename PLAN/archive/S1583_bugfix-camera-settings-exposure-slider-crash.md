# Спецификация (compact bugfix): S1583 - диалог настроек камеры падает на слайдере экспозиции

**Ticket:** S1583
**Status:** Archived
**Priority:** 85
**Date:** 2026-08-11

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Захвачено во время:** device-дренаж тикетов S1579 / S1569 / S1581 на реальном устройстве RFCR110NBQJ
(Samsung Galaxy S21+, SM-G996U1, Android 15 / SDK 35), standard debug `2.60.8111.809-DEBUG`. К проверявшимся
тикетам отношения не имеет - запарковано без переключения активной задачи.

**Текст:**

Открытие диалога настроек камеры и касание в области слайдера экспозиции валит приложение на экран
`CrashActivity` («Debug Crash Report»). Восстанавливается кнопкой «Restart».

```
Exception: java.lang.IllegalStateException
Message: Value(91.168594) must be equal to valueFrom(0.0) plus a multiple of stepSize(1.0)
         when using stepSize(1.0)
  at com.google.android.material.slider.BaseSlider.validateValues(BaseSlider.java:750)
  ..
  at com.sza.fastmediasorter.ui.cameracapture.helpers
       .CameraSettingsDialogRotationManager$RotatingContentContainer.onLayout(
         CameraSettingsDialogRotationManager.kt:156)
```

Время события на устройстве: 2026-08-11 20:51:26.985.

**Что видно из стека без расследования.** Падение приходит не из обработчика касания, а из прохода
разметки: `RotatingContentContainer.onLayout` вызывает валидацию Material-слайдера, и та отвергает значение
`91.168594` при `stepSize = 1.0` и `valueFrom = 0.0`. То есть слайдеру достаётся дробное значение, не кратное
шагу. Число похоже на координату или размер, попавшие в значение при повороте контейнера, но это догадка -
проверять надо расследованием.

**Дедуп.** `search.ps1 -Query "slider"` и `-Query "exposure"` не дали ни одного тикета.

**Захвачено во время:** device-дренаж `/spec-do` (раунд 6)

---

## 1. Проблема / симптом

Диалог настроек камеры валит приложение на `CrashActivity` с `IllegalStateException` из
`BaseSlider.validateValues()`: `Value(91.168594) must be equal to valueFrom(0.0) plus a multiple of
stepSize(1.0)`. Падение приходит из прохода разметки
(`CameraSettingsDialogRotationManager$RotatingContentContainer.onLayout`), а не из обработчика касания.

Слайдер-виновник - **выдержка** (`sliderCameraShutter`), а не экспозиция:
`valueFrom = SHUTTER_SLIDER_MIN = 0f`, `stepSize = 1f`, и только у него значение может оказаться дробным
в диапазоне `0..100`. У экспозиции `valueFrom = -maxExposureCompensationIndex` и значение целое, у ISO
`valueFrom = isoRange.lower` (тоже не 0 на реальном сенсоре).

Почему сработало по касанию, а не при открытии: блок выдержки скрыт (`isVisible =
draft.manualSensorEnabled`), а Material валидирует значение отложенно - на первом измерении вида. Пока
`GONE`, слайдер не измеряется. Первое же касание, включающее ручной режим (переключатель стоит рядом с
рядами экспозиции/ISO в двухколоночном потоке), делает блок видимым, и следующий проход разметки
падает.

---

## 2. Корневая причина

`CameraSettingsDialogFragment.setupManualSensorControls()` присваивает слайдеру выдержки результат
`shutterNsToSlider()` - непрерывное лог-масштабированное число (`91.168594`), тогда как трек объявлен с
шагом `1f`. Material `BaseSlider` требует, чтобы значение равнялось `valueFrom` плюс целое число шагов,
и бросает `IllegalStateException` при первой валидации.

Тот же класс дефекта латентно присутствует у двух других слайдеров: значение берётся из сохранённого
черновика (`draft.exposureCompensationIndex`, `draft.manualIso`) и присваивается без сверки с живым
диапазоном текущего объектива. После смены объектива сохранённое ISO или индекс экспозиции могут выйти
за `valueFrom..valueTo` - это другой бросок того же валидатора.

Общий корень: значение слайдера присваивается напрямую, без приведения к сетке шага и к границам трека.

---

## 3. Исправление

- Добавить в `CameraSettingsDialogFragment` чистую функцию `snapToSliderStep(rawValue, from, to, step)`:
  округляет до ближайшего узла сетки и зажимает в `from..to`.
- Присваивать значения всех трёх слайдеров диалога (экспозиция, ISO, выдержка) через неё, а не напрямую.
- Покрыть функцию unit-тестами: дробное значение выдержки, значение вне диапазона, точное попадание в
  узел, несимметричный `valueFrom`.

Что сознательно НЕ делается: шаг слайдера выдержки не превращается в непрерывный (`stepSize = 0f`) -
лог-шкала `0..100` с целым шагом даёт ~1% приращения выдержки на узел, чего достаточно, а непрерывный
трек лишил бы слайдер тактильных остановок.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1336 - тот же диалог настроек камеры и его пересоздание; S1581, S1579 - тикеты, во
  время дренажа которых находка всплыла, пересечений по коду нет.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция standard.
- Unit-тесты `CameraSettingsSliderSnapTest` проходят.
- На устройстве: открыть камеру -> настройки -> включить ручной режим (ISO/выдержка). Ожидается:
  блок появляется, приложение не падает, значение выдержки читается меткой.
- На устройстве: повернуть телефон с открытым диалогом при включённом ручном режиме - падения нет.

---

## Revision History

- **2026-08-12** - by `/spec-test-device` (SM-G996U1, device: RFCR110NBQJ Android 15 / SDK 35)
  - Scenario: `temp/S1583/mobile_test_scenario_20260812_0122.md` - PASS/FAIL/SKIPPED 6/0/0 - ошибок приложения
    в логе: 0. Проба `S1583: sliders bound exposure=0.0 iso=1625.0 shutter=91.0` - значение выдержки пришло на
    узел сетки вместо `91.168594`.
  - Побочная находка запаркована как S1590 (ряд выдержки обрезается краем карточки в повёрнутом диалоге).

---

## Last Audit

**Date:** 2026-08-12
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [x] Ручной режим включается, блок ISO/выдержки отрисовывается без падения - verified on-device 2026-08-12
- [x] Касание слайдера экспозиции при видимом блоке выдержки - падения нет - verified on-device 2026-08-12
- [x] Поворот с открытым диалогом - падения нет - verified on-device 2026-08-12

Проверенные предикаты: `snapToSliderStep` объявлена, все три слайдера присваиваются через `setSnappedValue`,
`CameraSettingsSliderSnapTest` 6/6 PASS, `.\a.ps1 fk` exit 0, запись в `dev/CHANGELOG.md`, класс в каталоге,
проба `Timber.d("S1583: ..)` присутствовала на момент `BlockNeedUserTest` (перенесена на несколько строк).
EXEMPT: §8 FEATURES - у compact-bugfix спеки этой секции нет; возможность «Manual shooting mode is named» уже
записана в `docs/ALL_FEATURES.jsonl`, новой capability тикет не добавляет.
