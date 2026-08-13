# Стратегическая спецификация: S0469 - Снятые фотографии в буфер обмена

**Ticket:** S0469
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-17
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-17
**Tactical spec:** `PLAN/S0469_save-photos-to-clipboard/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

<!-- auto-approved by /spec-all - 2026-06-17 -->

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-17

**Текст:**

новая функция работы с фото- опция "Сохранять созданные фотографии в буфер обмена". То есть помимо операций с этой фотографии-  содержимое готово чтобы вставить где-то на устройстве.

**Связанные тикеты:** S0469 - аналог S0468 (screenshot-clipboard), но для созданных фотографий (камера), а не для жестовых скриншотов. Подсистема другая, паттерн "копировать в буфер" переиспользуем.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Встроенная съёмка фото уже умеет сохранять снимок и запускать операции над ним. Но содержимое снимка нельзя сразу вставить в другое приложение на устройстве - чат, заметку, поле ввода: пользователь вручную идёт в галерею и делится файлом.

Не хватает опции «Сохранять созданные фотографии в буфер обмена»: помимо обычных операций со снимком его картинка должна оказываться в системном буфере, готовая к вставке где угодно.

Область - подсистема съёмки фото и экран её настроек.

---

## 2. Цели

1. В настройках появляется переключатель «Сохранять созданные фотографии в буфер обмена».
2. При включённой опции каждый снятый кадр дополнительно кладётся в системный буфер обмена как изображение.
3. Содержимое буфера можно вставить в любое стороннее приложение, принимающее картинки.
4. Опция работает совместно с любой назначенной операцией над снимком, не заменяя её.
5. Пользователь получает короткое подтверждение, что снимок скопирован в буфер.

**Non-goals:**

- Не вводится отдельный режим съёмки «в буфер» - это глобальный модификатор, а не пункт выбора.
- Не меняется поведение существующих операций при выключенной опции.
- Не затрагивается копирование в буфер из плеера/просмотрщика вне сценария съёмки.
- Не добавляется хранение истории буфера обмена.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Опция по умолчанию выключена, чтобы не менять текущее поведение при обновлении.
2. Подтверждение копирования - ненавязчивое (короткий тост), без модальных диалогов.
3. Картинка в буфере - в том же качестве, что и сохранённый снимок.

### 3.2 Жёсткие ограничения

- **Flavor:** опция следует за подсистемой съёмки фото (фича IMAGES); точный набор флейворов подтверждается на `/spec-tech`. Роль записи в буфер - общая и платформенно-нейтральная, переиспользуется из S0468, без новых `BuildConfig`-гейтов в `src/main`.
- **API level:** без новой API-специфики сверх минимума проекта; копирование изображения делается через системный clipboard и content-URI.
- **Wear OS:** не затрагивается.
- **Производительность:** копирование в буфер не должно задерживать показ результата съёмки; работа с картинкой - вне UI-потока.
- **Совместимость данных:** добавляется один булев флаг настройки со значением по умолчанию «выключено»; существующие пользователи апгрейда не затрагиваются.
- **Локализация:** EN/RU/UK - обязательно для новой подписи переключателя и подтверждающего сообщения.
- **Доступность:** переключатель доступен с клавиатуры/D-pad, имеет текстовую подпись; подтверждение не полагается только на цвет.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** переключатель размещается на экране настроек операций (`OperationsSettingsFragment`) рядом с остальными настройками встроенной съёмки фото, как самостоятельный пункт-переключатель под блоком камеры (там же, где «открывать снимок в редакторе»).
- **Accessibility:** пункт фокусируется с клавиатуры и D-pad, имеет текстовую подпись и описание состояния; подтверждение копирования читается screen reader'ом и не кодируется только цветом.
- **Communication policy:** подпись переключателя и текст подтверждения соответствуют `docs/COMMUNICATION_POLICY.md` (тон-чеклист §6 - обязательный гейт перед интеграцией строк).
- **Localization:** EN/RU/UK для всех новых строк, parity обязателен.
- **Validation level:** ручная проверка на устройстве - включить опцию, снять фото встроенной камерой, вставить из буфера в стороннее приложение, принимающее `image/*`; плюс компиляция затронутого варианта.
- **Owner sign-off:** 2026-06-17.
- **Related tickets:** S0468 (screenshot-clipboard) - источник переиспользуемой роли записи в буфер; S0470 (video-frame-clipboard) - сосед по семейству «в буфер».

---

## 4. Контекст текущей архитектуры

За съёмку отвечает подсистема камеры: захват кадра, его сохранение и запуск операций над снимком. Настройки съёмки хранятся в DataStore и читаются слоем настроек, редактируются на экране настроек.

Сейчас скопировать снимок в системный буфер негде: ни поток съёмки, ни настройки не содержат соответствующего шага/флага. Поэтому опцию нельзя выразить существующими средствами без нового флага настройки и шага «положить картинку в буфер» в момент, когда у съёмки уже есть готовый кадр.

---

## 5. Предлагаемый подход

Ввести глобальный булев параметр настройки «копировать снимок в буфер обмена» и единый шаг записи изображения в системный буфер, выполняемый в точке финализации снятого кадра, независимо от назначенной операции над снимком.

### 5.1 Основные столпы / модули

- **Параметр настройки.** Новый булев флаг в модели настроек съёмки; по умолчанию выключен; читается там же, где остальные параметры съёмки.

- **Запись в буфер.** Переиспользуется общая роль из S0468: картинка кладётся в системный буфер как изображение через app-cache копию и content-URI с правом чтения. Новой низкоуровневой реализации не вводится.

- **Точка вызова в потоке съёмки.** Шаг копирования встраивается в точку финализации кадра - где уже есть живой bitmap/файл и до его утилизации - и выполняется при включённом флаге для любого варианта съёмки, включая авто/тихие сценарии без подтверждающего UI.

- **UI настройки.** Переключатель на экране настроек съёмки, привязанный к новому флагу через слой ViewModel/настроек.

- **Подтверждение пользователю.** Короткое сообщение об успешном копировании, согласованное с коммуникационной политикой.

### 5.2 Потоки данных и событий

- Настройка: UI настроек → слой настроек → хранилище (DataStore) и обратно при чтении.
- Съёмка: кадр → финализация → при включённом флаге шаг записи в буфер кладёт изображение в системный буфер → параллельно отрабатывает назначенная операция → подтверждение пользователю.

### 5.3 Точки расширяемости

- Роль записи в буфер остаётся общей точкой переиспользования для семейства (S0468/S0470); этот тикет её только потребляет.
- Параметр настройки добавляется по существующему паттерну флагов съёмки.
- Конкретная привязка зависимостей (DI) и точное место вызова определяются на этапе `/spec-tech`.

---

## 6. Открытые вопросы / Research items

1. **Источник картинки для буфера**
   - **Вопрос:** что копировать в буфер - живой декодированный bitmap или сохранённый файл снимка - и что из этого доступно в точке финализации?
   - **Решение:** живого bitmap в потоке нет - CameraX (`ImageCapture.takePicture`) пишет JPEG сразу в файл; копируется сохранённый файл-снимок (`tempFile`) в точке `CameraCaptureSaver.save()`, общей для Browse и виджета, до его удаления.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0469_save-photos-to-clipboard/research/01__capture-finalization-source.md`

2. **Вставка image-клипа в текстовое поле**
   - **Вопрос:** что увидит пользователь, вставляя `image/png`-клип в текстовый приёмник?
   - **Решение (унаследовано от семейства):** текстовое поле коэрсит URI-элемент клипа в строку, а не картинку; для вставки изображения приёмник должен принимать `image/*`. См. S0468, вопрос 4.
   - **Статус:** Open.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Вставка image-клипа в текстовое поле даёт строку URI вместо картинки | Высокая | Пользователь считает, что опция не работает | Тестировать в `image/*`-приёмнике; при необходимости добавить текстовый элемент-спутник в ClipData |
| Готовый кадр недоступен в точке финализации (только сохранённый URI) | Низкая | Нечего копировать | Брать живой bitmap до утилизации, как в S0468 |
| Лишняя задержка показа результата из-за работы с большой картинкой | Низкая | Подлагивание после съёмки | Выполнять копирование вне UI-потока |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая способность: добавляется опция «Сохранять созданные фотографии в буфер обмена», после чего снятый кадр можно сразу вставить в другое приложение. Требуется новая запись в `docs/FEATURES.md` + `_RU` + `_UK` в блоке про съёмку фото.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Переиспользовать общую роль записи в буфер из S0468**

- **Решение:** копирование картинки в системный буфер выполняется единой переиспользуемой ролью, введённой в S0468, а не собственной реализацией.
- **Альтернативы:** своя реализация записи в буфер в каждом тикете семейства.
- **Почему:** поведение «картинка → системный буфер» одинаково для скриншота, фото и кадра видео; единая роль исключает расхождение и тройное тестирование низкоуровневой части.

**ADR-2: Глобальный флаг-модификатор вместо нового режима съёмки**

- **Решение:** копирование в буфер - отдельный глобальный переключатель, применяемый поверх любой назначенной операции над снимком.
- **Альтернативы:** добавить отдельный режим/действие съёмки «в буфер».
- **Почему:** пользователь сформулировал поведение как «помимо операций со снимком», то есть дополнение к действию, а не его замена.

---

## 10. Связи с другими спеками

- S0468 (screenshot-clipboard) - источник переиспользуемой роли записи в буфер.
- S0470 (video-frame-clipboard) - аналогичная опция копирования в буфер для кадров видео.

---

## 11. Критерии готовности (strategic-level)

1. На экране настроек съёмки виден переключатель «Сохранять созданные фотографии в буфер обмена», по умолчанию выключенный.
2. При включённой опции после съёмки фото его изображение находится в системном буфере обмена.
3. Скопированный снимок успешно вставляется в стороннее приложение, принимающее картинки.
4. Опция работает в паре с любой назначенной операцией над снимком, не отменяя её.
5. Пользователь видит короткое подтверждение копирования.
6. Подпись переключателя и подтверждение присутствуют на EN/RU/UK.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0469_save-photos-to-clipboard/INDEX.md` (5 фаз, реализованы).

---

## Revision History

- **2026-06-17** - by `/spec-test-device` (emulator-5554, sdk_gphone16k_x86_64, Android 17/SDK 37)
  - Scenario: temp/S0469_mobile_test_scenario_20260617_2050.md · PASS/INCONCLUSIVE/OUT-OF-SCOPE 4/2/1 · log errors 0
  - Settings toggle verified on-device (present, default off, correct EN label, nested under photo-capture). Runtime capture->clipboard path INCONCLUSIVE: the in-app "Capture with camera" menu item could not be triggered via automation (emulator dialog-input wall), so the `S0469:` gate probe never fired. No crashes. Keep `BlockNeedUserTest` for real-device confirmation incl. cross-app `image/*` paste.
- **2026-06-17** - by `/spec-test-device` (emulator-5554, run 2)
  - Scenario: temp/S0469_mobile_test_scenario_20260617_2138.md · `S0469:` probe 0 hits · log errors 0 (no crash)
  - Re-attempt confirmed the same wall: "Capture with camera" bottom-sheet item is not tappable via mobile-mcp or `adb input tap` on this AVD (stale trees, dropped taps). Runtime path remains unverified on emulator. Real-device manual test still required; status unchanged `BlockNeedUserTest`.
- **2026-06-18** - by `/spec-test-device` (emulator-5554, Pixel 6, Android 13, run 3)
  - Evidence: temp/S0469_run3/ (screenshots 01-03, logcat_capture.txt, crash.txt) · `S0469:` probe 0 hits · INCONCLUSIVE
  - Touch input was responsive this run (no AVD tap wedge). Got past the prior wall: toggled "Save photos to clipboard" ON, opened the Resource Operations Menu, tapped "Capture with camera" -> reached the live `CameraCaptureActivity`, pressed the shutter, `ImageCapture.takePictureInternal` issued a capture request.
  - New wall (lower in the stack): the emulator's fake camera (`vendor.qemu.sf.fake_camera=front`; Stream 0 `timestamp not increasing` flood) never returns a still frame, so `ImageCapture.takePicture()` hangs indefinitely. `CameraCaptureSaver.save()` never runs -> the `S0469:` gate probe never fires and no clipboard write happens. Runtime capture->clipboard path still unverified on emulator (camera-HAL limitation, not a code gap).
  - Out-of-scope crash discovered + parked as **S0503** (Draft): closing the stuck camera while `takePicture` was in flight delivered CameraX `onError`, which crashed on `binding.btnCapturePhoto` access after the activity was finishing (`IllegalStateException: Binding is only valid..` at CameraCaptureActivity.kt:142). Unrelated to the clipboard feature.
  - Status unchanged `BlockNeedUserTest`; real-device manual confirmation still required (incl. cross-app `image/*` paste).

---

## Last Audit

**Date:** 2026-06-17
**Mode:** full
**Flags:** -
**Outcome:** BlockNeedUserTest (implementation complete; on-device confirmation pending)
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 0

All code wiring for "save captured photos to clipboard" is present and compiles (standard debug green).

### Checks (PASS)

- Setting `cameraCaptureCopyToClipboard` across model + DataStore store (key/read/write) + repository read + view-model defaults + backup (data + mapper both ways) + import + device-preset applier.
- Reusable role extended: `ImageClipboardWriter.copyImageFile(File)` (verbatim file copy, no decode/re-encode - preserves quality).
- Clipboard step in the single shared backend `CameraCaptureSaver.save()` via `maybeCopyToClipboard`, gated `MediaType.IMAGE` + flag, before temp-file delete; `SaveResult.Success.copiedToClipboard` threaded out.
- Confirmation toast (`camera_capture_copied_to_clipboard`) in both callers: `BrowseCameraCaptureManager` and `CameraQuickCaptureLaunchManager`.
- Settings toggle `rowCameraCopyToClipboard` in `OperationsSettingsFragment` + layout (portrait) + layout-land.
- Strings `setting_camera_copy_to_clipboard_title/_summary` + `camera_capture_copied_to_clipboard` present EN/RU/UK (parity audit PASS).
- Capability recorded in `docs/ALL_FEATURES.jsonl` (validate PASS).
- Debug-tag invariant: single `Timber.d("S0469:` probe at the clipboard gate (required for `BlockNeedUserTest`).
- Standard-debug build (`assembleStandardDebug`) PASS; neuroslop + ticket-log gates PASS.

### Manual / on-device

- [ ] Real device: enable «Сохранять фотографии в буфер обмена», take a photo (Browse "Capture with camera" or Quick Capture widget) -> expect toast «Скопировано в буфер обмена» + log `S0469: captured-photo clipboard gate flag=true`, then paste into an `image/*`-accepting app and confirm the picture appears (not a text field - see §6 Q2).
- [ ] Confirm the assigned save / open-for-editing operation still runs alongside the clipboard copy.

> **Harness note (run 3, 2026-06-18):** automation now drives the full UI path - toggle ON, Resource Operations Menu, "Capture with camera", live `CameraCaptureActivity`, shutter press, `ImageCapture.takePictureInternal` issued. The block is now the emulator camera HAL: the fake camera never returns a still frame (`Stream 0 timestamp not increasing` flood), so `takePicture()` hangs and `CameraCaptureSaver.save()` / the `S0469:` probe never run. This is an AVD camera-backend limitation with no app-side workaround - hence INCONCLUSIVE on emulator and manual device sign-off still required, mirroring S0468. A separate crash on the camera error path was parked as S0503.
