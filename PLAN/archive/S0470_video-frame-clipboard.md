# Стратегическая спецификация: S0470 - Кадры видео в буфер обмена

**Ticket:** S0470
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-17
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-17
**Tactical spec:** `PLAN/S0470_video-frame-clipboard/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

<!-- auto-approved by /spec-all - 2026-06-17 -->

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-17

**Текст:**

новая функция работы с кадрами видео- опция "Сохранять созданные кадры в буфер обмена". То есть помимо операций с этим кадром (взятым из видео)-  содержимое готово чтобы вставить где-то на устройстве.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Извлечение кадра из видео уже умеет сохранять кадр и запускать операции над ним. Но содержимое кадра нельзя сразу вставить в другое приложение на устройстве - чат, заметку, поле ввода: пользователь вручную идёт в галерею и делится файлом.

Не хватает опции «Сохранять созданные кадры в буфер обмена»: помимо обычных операций с кадром его картинка должна оказываться в системном буфере, готовая к вставке где угодно.

Область - подсистема извлечения кадра в видеоплеере и экран её настроек.

---

## 2. Цели

1. В настройках появляется переключатель «Сохранять созданные кадры в буфер обмена».
2. При включённой опции каждый извлечённый кадр дополнительно кладётся в системный буфер обмена как изображение.
3. Содержимое буфера можно вставить в любое стороннее приложение, принимающее картинки.
4. Опция работает совместно с любой назначенной операцией над кадром, не заменяя её.
5. Пользователь получает короткое подтверждение, что кадр скопирован в буфер.

**Non-goals:**

- Не вводится отдельная операция «в буфер» для извлечения кадра - это глобальный модификатор, а не пункт выбора.
- Не меняется поведение существующих операций при выключенной опции.
- Не затрагивается копирование в буфер вне сценария извлечения кадра.
- Не добавляется хранение истории буфера обмена.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Опция по умолчанию выключена, чтобы не менять текущее поведение при обновлении.
2. Подтверждение копирования - ненавязчивое (короткий тост), без модальных диалогов.
3. Картинка в буфере - в том же качестве, что и сохранённый кадр.

### 3.2 Жёсткие ограничения

- **Flavor:** опция следует за подсистемой видео (фича VIDEO); точный набор флейворов подтверждается на `/spec-tech`. Роль записи в буфер - общая и платформенно-нейтральная, переиспользуется из S0468, без новых `BuildConfig`-гейтов в `src/main`.
- **API level:** без новой API-специфики сверх минимума проекта; копирование изображения делается через системный clipboard и content-URI.
- **Wear OS:** не затрагивается.
- **Производительность:** копирование в буфер не должно задерживать показ результата; работа с картинкой (особенно крупным кадром) - вне UI-потока.
- **Совместимость данных:** добавляется один булев флаг настройки со значением по умолчанию «выключено»; существующие пользователи апгрейда не затрагиваются.
- **Локализация:** EN/RU/UK - обязательно для новой подписи переключателя и подтверждающего сообщения.
- **Доступность:** переключатель доступен с клавиатуры/D-pad, имеет текстовую подпись; подтверждение не полагается только на цвет.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** переключатель размещается на экране настроек видео (`VideoSettingsFragment`) рядом с настройками сохранения кадра (формат снимка, ресурс назначения), как самостоятельный пункт-переключатель.
- **Accessibility:** пункт фокусируется с клавиатуры и D-pad, имеет текстовую подпись и описание состояния; подтверждение копирования читается screen reader'ом и не кодируется только цветом.
- **Communication policy:** подпись переключателя и текст подтверждения соответствуют `docs/COMMUNICATION_POLICY.md` (тон-чеклист §6 - обязательный гейт перед интеграцией строк).
- **Localization:** EN/RU/UK для всех новых строк, parity обязателен.
- **Validation level:** ручная проверка на устройстве - включить опцию, извлечь кадр из видео, вставить из буфера в стороннее приложение, принимающее `image/*`; плюс компиляция затронутого варианта.
- **Owner sign-off:** 2026-06-17.
- **Related tickets:** S0468 (screenshot-clipboard) - источник переиспользуемой роли записи в буфер; S0469 (save-photos-to-clipboard) - сосед по семейству «в буфер».

---

## 4. Контекст текущей архитектуры

За извлечение кадра отвечает подсистема видеоплеера: декодирование кадра на текущей позиции, его сохранение и запуск операций над кадром. Настройки плеера хранятся в DataStore и читаются слоем настроек, редактируются на экране настроек.

Сейчас скопировать кадр в системный буфер негде: ни поток извлечения, ни настройки не содержат соответствующего шага/флага. Поэтому опцию нельзя выразить существующими средствами без нового флага настройки и шага «положить картинку в буфер» в момент, когда у извлечения уже есть готовый кадр.

---

## 5. Предлагаемый подход

Ввести глобальный булев параметр настройки «копировать кадр в буфер обмена» и единый шаг записи изображения в системный буфер, выполняемый в точке финализации извлечённого кадра, независимо от назначенной операции над кадром.

### 5.1 Основные столпы / модули

- **Параметр настройки.** Новый булев флаг в модели настроек плеера; по умолчанию выключен; читается там же, где остальные параметры плеера.

- **Запись в буфер.** Переиспользуется общая роль из S0468: картинка кладётся в системный буфер как изображение через app-cache копию и content-URI с правом чтения. Новой низкоуровневой реализации не вводится.

- **Точка вызова в потоке извлечения.** Шаг копирования встраивается в точку финализации кадра - где уже есть декодированный bitmap и до его утилизации - и выполняется при включённом флаге для любого варианта извлечения.

- **UI настройки.** Переключатель на экране настроек плеера, привязанный к новому флагу через слой ViewModel/настроек.

- **Подтверждение пользователю.** Короткое сообщение об успешном копировании, согласованное с коммуникационной политикой.

### 5.2 Потоки данных и событий

- Настройка: UI настроек → слой настроек → хранилище (DataStore) и обратно при чтении.
- Извлечение: позиция видео → декодирование кадра → финализация → при включённом флаге шаг записи в буфер кладёт изображение в системный буфер → параллельно отрабатывает назначенная операция → подтверждение пользователю.

### 5.3 Точки расширяемости

- Роль записи в буфер остаётся общей точкой переиспользования для семейства (S0468/S0469); этот тикет её только потребляет.
- Параметр настройки добавляется по существующему паттерну флагов плеера.
- Конкретная привязка зависимостей (DI) и точное место вызова определяются на этапе `/spec-tech`.

---

## 6. Открытые вопросы / Research items

1. **Источник кадра для буфера**
   - **Вопрос:** доступен ли декодированный bitmap кадра напрямую в точке извлечения, или придётся читать его из сохранённого файла кадра?
   - **Решение:** в `SaveVideoFrameManager.saveCurrentFrame()` живой bitmap снимается с `TextureView`, затем пишется во временный файл `tempFile` (PNG/JPG по настройке `videoSnapshotFormat`). В буфер копируется именно `tempFile` через `ImageClipboardWriter.copyImageFile(File)` - до `tempFile.delete()` - так картинка в буфере байт-в-байт совпадает с сохранённым кадром (формат и качество). Это паттерн семейства из S0469.
   - **Статус:** Resolved.

2. **Стоимость декодирования крупного кадра**
   - **Вопрос:** не создаёт ли копирование крупного кадра (например, 4K) заметной задержки или давления на память?
   - **Статус:** Open (копирование выполняется вне UI-потока; при необходимости оценить пиковое потребление памяти).

3. **Вставка image-клипа в текстовое поле**
   - **Вопрос:** что увидит пользователь, вставляя `image/png`-клип в текстовый приёмник?
   - **Решение (унаследовано от семейства):** текстовое поле коэрсит URI-элемент клипа в строку, а не картинку; для вставки изображения приёмник должен принимать `image/*`. См. S0468, вопрос 4.
   - **Статус:** Open.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Вставка image-клипа в текстовое поле даёт строку URI вместо картинки | Высокая | Пользователь считает, что опция не работает | Тестировать в `image/*`-приёмнике; при необходимости добавить текстовый элемент-спутник в ClipData |
| Декодированный кадр недоступен в точке финализации (только сохранённый файл) | Средняя | Нужен лишний шаг чтения файла | Определить источник на `/spec-tech`; переиспользовать роль S0468 по файлу, если нет bitmap |
| Копирование крупного кадра тормозит показ результата | Низкая | Подлагивание после извлечения | Выполнять копирование вне UI-потока |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая способность: добавляется опция «Сохранять созданные кадры в буфер обмена», после чего извлечённый из видео кадр можно сразу вставить в другое приложение. Требуется новая запись в `docs/FEATURES.md` + `_RU` + `_UK` в блоке про работу с кадрами видео.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Переиспользовать общую роль записи в буфер из S0468**

- **Решение:** копирование картинки в системный буфер выполняется единой переиспользуемой ролью, введённой в S0468, а не собственной реализацией.
- **Альтернативы:** своя реализация записи в буфер в каждом тикете семейства.
- **Почему:** поведение «картинка → системный буфер» одинаково для скриншота, фото и кадра видео; единая роль исключает расхождение и тройное тестирование низкоуровневой части.

**ADR-2: Глобальный флаг-модификатор вместо новой операции извлечения**

- **Решение:** копирование в буфер - отдельный глобальный переключатель, применяемый поверх любой назначенной операции над кадром.
- **Альтернативы:** добавить отдельную операцию извлечения кадра «в буфер».
- **Почему:** пользователь сформулировал поведение как «помимо операций с кадром», то есть дополнение к действию, а не его замена.

---

## 10. Связи с другими спеками

- S0468 (screenshot-clipboard) - источник переиспользуемой роли записи в буфер.
- S0469 (save-photos-to-clipboard) - аналогичная опция копирования в буфер для снятых фотографий.

---

## 11. Критерии готовности (strategic-level)

1. На экране настроек плеера виден переключатель «Сохранять созданные кадры в буфер обмена», по умолчанию выключенный.
2. При включённой опции после извлечения кадра его изображение находится в системном буфере обмена.
3. Скопированный кадр успешно вставляется в стороннее приложение, принимающее картинки.
4. Опция работает в паре с любой назначенной операцией над кадром, не отменяя её.
5. Пользователь видит короткое подтверждение копирования.
6. Подпись переключателя и подтверждение присутствуют на EN/RU/UK.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0470_video-frame-clipboard/INDEX.md` (4 фазы, реализованы).

---

## Last Audit

### Manual (2026-06-19, emulator-5556, standard debug v2.60.6191.257, `/spec-test-device`)

- **Verdict:** PASS (main player gate, standalone player gate, destination file both players); INCONCLUSIVE (cross-app `image/*` paste)
- **Expected:** toggle ON → open video → Save Frame in BOTH main and standalone players → save toast + "Copied to clipboard" toast + log `S0470: ... clipboard gate flag=true`; the configured destination still receives the frame file; clipboard non-empty; paste into an `image/*` receiver shows the frame.
- **Setup:** "Save video frames to clipboard" toggle enabled in Settings → Media → "Video and player settings" (under the PNG/JPG frame-format selector; default OFF, flipped ON, persisted across a later revisit). JPG frame format. Save destination "Not selected (saves to Downloads)". Local folder resource registered over `/storage/emulated/0/Download/FastMediaSorter_Test/DCIM` (21 files); `video_sample.mp4` used.

- **Sub-check 1 - main player gate: PASS.** Main `PlayerActivity` → overflow "More actions" → "Save Frame".
  - `17:37:02.962 D SaveVideoFrameManager$saveCurrentFrame: S0470: video-frame clipboard gate flag=true` - gate open.
  - 2 Toast windows fired from `com.sza.fastmediasorter.debug` (save toast + clipboard confirmation toast).
  - `ClipboardListener: Clipboard overlay suppressed` ×2 - app's clipboard write executed (overlay suppression is emulator system behaviour).

- **Sub-check 2 - standalone player gate: PASS.** `PhotoVideoStandaloneActivity` reached by enabling "Set as default video player" (sets `isPrimaryMediaPlayer=true`), restarting the app so `DefaultPlayerStateBootstrapper` enabled the `.StandaloneVideoPlayer` alias (verified: `enabledComponents` lists `StandaloneVideoPlayer`), then an `ACTION_VIEW` intent on the video. Overflow → "Save Frame".
  - `17:42:28.115 D PhotoVideoStandaloneActivity$saveCurrentFrame: S0470: standalone video-frame clipboard gate flag=true` - gate open.
  - "Copied to clipboard" toast captured on screen (screenshot `22_standalone_save_frame.png`); 2 Toast windows in log.
  - The `ClipboardImageFileProviderUtils` SecurityException in the log is Gboard (`com.google.android.inputmethod.latin`) trying to PREVIEW the clip image, not the app's write failing.

- **Sub-check 3 - destination file (both players): PASS.**
  - Main: `/sdcard/Download/video_sample_00h00m39s.jpg` (35862 bytes, 17:37) - matches save action; `S0528: video-frame save to Downloads via shared writer` logged.
  - Standalone: `/sdcard/Pictures/frame_video_sample_44642287965953.jpg` (51414 bytes, 17:42).

- **Sub-check 4 - cross-app `image/*` paste: INCONCLUSIVE.** Stock AVD has no `image/*` paste-target app; `adb cmd clipboard get-text/has-primary-clip` returns "No shell command implementation"; Gboard's clip-image preview hit a FileProvider Permission Denial. Clipboard write is proven indirectly (the app's second toast fires only when `imageClipboardWriter` returns true; `ClipboardListener` overlay-suppressed events confirm a primary clip was set). Real-device paste into an `image/*` receiver still required to close this sub-check.

- **Evidence:** `temp/S0470_devtest/` - `17_main_save_frame_toast.png`, `21_standalone_opened.png`, `22_standalone_save_frame.png` ("Copied to clipboard" visible), `logcat_main_player.txt`, `logcat_standalone_player.txt`, `logcat_s0470_relevant.txt`.
- **Disposition:** keep `BlockNeedUserTest`. Both player clipboard gates + both destination files confirmed PASS on emulator (standalone path now also confirmed, unlike the prior 5554 run). Only the cross-app `image/*` paste remains for real-device confirmation.

### Manual (2026-06-19, emulator-5554, standard debug v2.60.6191.257-DEBUG, `/spec-test-device` within `/spec-sweep`)

- **Verdict:** PASS (main player path); INCONCLUSIVE (standalone player + cross-app paste)
- **Expected:** toggle ON → open video → extract frame → save toast + toast "Copied to clipboard" + log `S0470: video-frame clipboard gate flag=true`; toggle OFF → save toast only, no clipboard toast, no `S0470:` probe.
- **Actual (toggle ON, main player):**
  - Toggle enabled via `adb input tap 99 1904` in Settings → Media → "Видео, настройки проигрывателя".
  - `test_video.mp4` opened via DCIM local-folder resource in main `PlayerActivity`.
  - Save Frame command invoked via "Ещё" overflow menu → "Сохранить кадр".
  - Logcat `13:30:12.406`: `SaveVideoFrameManager: frame saved to Downloads as test_video_00h00m39s.jpg` — save succeeded.
  - Logcat `13:30:12.411`: `D SaveVideoFrameManager$saveCurrentFrame: S0470: video-frame clipboard gate flag=true` — probe fired, gate correctly open.
  - Logcat `13:30:12.492`: `ClipboardListener: Clipboard overlay suppressed` (×2) — clipboard write executed (suppression is emulator-side system overlay behaviour, not an app failure).
  - Two toasts fired from `com.sza.fastmediasorter.debug` at 13:30:15 and 13:30:17 (save toast + clipboard confirmation toast).
- **Actual (toggle OFF, main player):**
  - Toggle disabled; same Save Frame flow repeated.
  - Logcat `13:37:00.606`: `SaveVideoFrameManager: frame saved` — save succeeded.
  - ONE toast from `com.sza.fastmediasorter.debug` fired (save toast only).
  - No `S0470:` probe line, no clipboard write, no second toast — gate correctly suppressed.
- **Actual (standalone player):** INCONCLUSIVE — `PhotoVideoStandaloneActivity` is not exported; `StandaloneVideoPlayer` alias is `android:enabled="false"`. No in-app navigation path to standalone player was reachable from the emulator-driven flow.
- **Actual (cross-app paste):** Not verified — emulator `ClipboardListener` suppressed the overlay; a real device is needed to confirm paste into an `image/*` receiver.
- **Evidence:** `temp/S0470_device_test_20260619_1405/01_toggle_enabled.png`, `logcat_full.txt`, `logcat_s0470_relevant.txt`
- **Disposition:** keep `BlockNeedUserTest`. Main player clipboard path confirmed PASS on emulator. Remaining gaps: standalone player Save Frame path and cross-app `image/*` paste require a real device (or a device with working clipboard overlay + exportable standalone player entry).

---

## Revision History

- **2026-06-17** - by `/spec-test-device` (emulator-5554, Android emulator, debug v2.60.6171.725-DEBUG)
  - Scenario: temp/S0470_mobile_test_scenario_20260617_2114.md · PASS/INCONCLUSIVE/OUT-OF-SCOPE 4/1/1 · log errors 0
  - Settings toggle verified on-device: present under Media → "Video and player settings", default OFF, flips ON, EN label "Save video frames to clipboard", placed under the frame-format selector. No crash. Runtime extract→clipboard path INCONCLUSIVE (emulator cannot drive video-frame extraction; `S0470:` probe never fired). Keep `BlockNeedUserTest` for real-device confirmation incl. cross-app `image/*` paste.
