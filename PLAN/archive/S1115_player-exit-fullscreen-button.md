# Стратегическая спецификация: S1115 - Кнопка выхода из полноэкранного режима в командах видеоплеера

**Ticket:** S1115
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-19
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - идея владельца при device-тесте на Quest 3 (2026-07-19)
**Tactical spec:** `PLAN/S1115_player-exit-fullscreen-button/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (verbatim)

> /spec-draft прогрыватель основной или standalone проигрывает видео в полном хэкране. Пользователь не помнит куда нгажать чтобы вернуться в реджим с командным меню (выйти изх полноэкрана). поэтому в команды плеера *видеоплеера) добавить внопку выхода из полноэкранного режима

**Вложения:** нет.

---

## 1. Проблема

В полноэкранном режиме воспроизведения видео (основной плеер и standalone-плеер открытого файла) верхняя командная панель скрывается полностью - вместе с ней исчезает единственная кнопка, которая переключала полноэкранный режим. Возврат в режим с командным меню требует либо жеста тап-по-экрану, либо системного жеста от края экрана, либо кнопки «назад» - ни один из этих способов не показан пользователю явно. На устройстве с контроллерами (Quest 3) и вообще для любого пользователя, не запомнившего скрытый жест, это тупик: видео просмотрено, а как вернуться к меню команд - неочевидно.

Для PDF/EPUB/TXT в проигрывателе такая же проблема уже решена: в полноэкранном режиме поверх контента постоянно показана отдельная закруглённая кнопка выхода в верхнем углу. Для видео аналогичного явного выхода нет.

---

## 2. Цели

1. В полноэкранном режиме воспроизведения видео (оба хоста - основной и standalone) пользователь в любой момент видит явную кнопку выхода из полноэкрана, без необходимости знать жест или пробовать угадать место тапа.
2. Нажатие кнопки возвращает командную панель (выходит из полноэкранного режима) тем же способом, что и существующий переключатель полноэкрана для этого хоста.
3. Поведение не меняется для типов файлов, у которых уже есть выход из полноэкрана (PDF/EPUB/TXT) - для них кнопка продолжает работать как раньше.

**Non-goals:**

- Не меняется механизм входа в полноэкранный режим (кнопка/жест, которым пользователь его включает).
- Не добавляется новая кнопка выхода из полноэкрана для изображений/GIF - в текущей идее владельца речь только о видео; расширение на другие типы можно рассмотреть отдельным тикетом, если появится такой же запрос.
- Аудио не затрагивается - командная панель для аудио всегда видима и полноэкранного режима не имеет.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Кнопка размещена «в командах видеоплеера, рядом с play/pause/PiP» (формулировка из захваченной идеи) - фактическое размещение определено исследованием (см. §4-5): выбран не нижний ряд транспортных кнопок, а уже существующий постоянно видимый оверлей выхода из полноэкрана, расширенный на видео (обоснование - §9 ADR-1).

### 3.2 Жёсткие ограничения

- **Flavor:** без флейвор-специфики - меняется общий код `src/main/`, доступный во всех сборках.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично - изменение видимости одной кнопки по существующему состоянию.
- **Совместимость данных:** нет - изменение чисто UI, без работы с БД/настройками.
- **Локализация:** используется уже существующая, полностью локализованная (EN/RU/UK) строка «выход из полноэкрана» - новых строк не требуется.
- **Доступность:** кнопка уже имеет `contentDescription`; при переносе логики видимости на новый тип файла описание и размер кликабельной области не меняются.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** постоянно видимый (не по тапу) круглый оверлей в верхнем углу поверх видео, пока активен полноэкранный режим - тот же паттерн, что уже используется для PDF/EPUB/TXT.
- **Accessibility:** без изменений - переиспользуется существующие `contentDescription` и размер кнопки; смена только условия видимости.
- **Validation level:** device-тест на реальном устройстве (Quest 3 или Android-планшет/телефон) - войти в полноэкран видео в обоих хостах, убедиться, что кнопка видна и возвращает командную панель.
- **Owner sign-off:** требуется подтверждение владельца после device-теста (см. §11).
- **Related tickets:** S1114 (перенос кнопки входа в VR в нижний ряд команд видеоплеера) - смежная идея с того же device-теста, отдельная реализация; не блокирует и не блокируется этой спекой.

---

## 4. Контекст текущей архитектуры

Основной хост проигрывателя уже имеет постоянно видимую overlay-кнопку выхода из полноэкрана в верхнем углу, которая сейчас показывается только для документных типов (PDF/EPUB/TXT) и скрыта для остальных, включая видео; её видимость уже пересчитывается в единой точке при каждом переключении полноэкрана и смене типа файла, а нажатие уже выполняет выход из полноэкрана (возврат командной панели). Для видео на этом хосте не хватает только одного: включить тип «видео» в список типов, для которых кнопка показывается.

Активный standalone-хост открытого фото/видео - отдельный экран со своим собственным layout, в котором этой overlay-кнопки нет вовсе (есть только верхняя командная панель, скрывающаяся в полноэкране, и системный жест от края экрана для её возврата). Более старый standalone-хост, использующий общий с основным плеером layout, помечен как устаревший и ожидает удаления - на него внешние интенты уже не маршрутизируются, поэтому он в объём не входит. Таким образом для standalone нужно: добавить в его layout ту же overlay-кнопку (портрет и ландшафт) и подключить её - обновление видимости в точках, где меняется видимость командной панели, и обработчик нажатия, вызывающий тот же выход из полноэкрана, что и существующий системный/клавиатурный путь этого хоста.

У видео есть также собственный отдельный ряд транспортных кнопок (play/pause, перемотка, PiP), который показывается только по тапу на экран, - поэтому кнопка внутри него не решала бы проблему обнаруживаемости и в качестве места размещения отвергнута (§9 ADR-1).

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- **Единая точка выхода из полноэкрана.** Постоянно видимый overlay «выход из полноэкрана» перестаёт быть специфичным только для документных типов и становится общей точкой выхода из полноэкрана для контента, у которого есть полноэкранный режим без командной панели. Для основного хоста это готовая расширяемая точка - меняется только условие видимости (добавляется видео). Для активного standalone-хоста тот же overlay-элемент добавляется в его layout (портрет и ландшафт) и подключается: обновление видимости в точках изменения видимости командной панели + обработчик нажатия, выполняющий тот же выход из полноэкрана, что и существующий системный/клавиатурный путь этого хоста.
- **Единообразие с документным паттерном.** Условие видимости остаётся тем же самым (активен полноэкранный режим), расширяется только на тип «видео» - без дублирования механизма.

### 5.2 Потоки данных и событий

Смена состояния «полноэкранный режим включён/выключен» и смена текущего файла уже выбрасывают обновление видимости overlay-кнопки на основном хосте - в этот же обработчик добавляется тип «видео». На standalone-хосте аналогичное обновление видимости подключается к тем же точкам, где уже обновляется иконка/описание существующей кнопки полноэкрана (вход/выход/тоггл/восстановление системных баров через край-жест). Нажатие новой точки входа вызывает тот же переход «выйти из полноэкрана», что и существующий переключатель данного хоста - расхождения в поведении между «явной кнопкой» и «переключателем» не создаётся.

### 5.3 Точки расширяемости

Условие видимости overlay-кнопки выхода из полноэкрана остаётся простым предикатом «тип файла ∈ множество типов, для которых кнопка нужна» - при появлении запроса на изображения/GIF расширение сводится к дополнению этого множества, без новой архитектуры.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Исследование существующего кода (условие видимости overlay-кнопки выхода из полноэкрана, оба хоста, общий layout, независимый ряд транспортных кнопок видео) выполнено на этапе написания этой спеки и оформлено дальше в тактическом плане.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Переименование overlay-элемента (снятие «документной» специфики из имени) заденет что-то незамеченное | Низкая | Ошибка компиляции или пропущенное место | Переименование выполняется как единая механическая правка по всем ссылкам, проверяется сборкой |
| Кнопка выхода из полноэкрана визуально пересечётся с другим overlay-элементом в том же углу экрана (например, VR-бейдж) | Низкая | Наложение элементов друг на друга | Все overlay-элементы в этом углу уже гейтятся видимостью командной панели/полноэкрана и не показываются одновременно - подтверждено на этапе исследования |
| На standalone-хосте новый обработчик разойдётся с существующим переключателем полноэкрана (разное поведение из двух точек входа) | Низкая | Двойное/неконсистентное состояние полноэкрана | Новый обработчик вызывает тот же метод перехода, что и существующая кнопка полноэкрана этого хоста |

---

## 8. Влияние на пользователя (docs/FEATURES)

Пользователь получает новую видимую возможность: явную кнопку выхода из полноэкранного режима видео в обоих плеерах. Одно предложение для `docs/FEATURES.md` + `_RU` + `_UK`: в полноэкранном режиме просмотра видео добавлена постоянно видимая кнопка выхода из полноэкрана.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Расширить существующий overlay «выход из полноэкрана» на видео, а не добавлять новую кнопку в тап-по-экрану ряд транспортных кнопок видео**

- **Решение:** видимая всегда (пока активен полноэкранный режим) overlay-кнопка в углу экрана, уже применяемая для PDF/EPUB/TXT, расширяется на видео вместо добавления новой кнопки в ряд play/pause/перемотка/PiP.
- **Альтернативы:**
  1. Добавить кнопку выхода в ряд транспортных кнопок видео (тот, что содержит play/pause/PiP) - именно так было предложено в исходной идее.
  2. Полагаться на системную кнопку «назад».
  3. Расширить существующий постоянно видимый overlay-паттерн на видео (выбрано).
- **Почему:** ряд транспортных кнопок видео сам показывается только по тапу на экран - то есть требует того же неочевидного жеста, который и является причиной проблемы; кнопка внутри него не решает обнаруживаемость. Кнопка «назад» имеет собственную семантику (закрыть плеер) и не гарантированно воспринимается пользователем как «выйти из полноэкрана». Постоянно видимый оверлей уже проверенно решает ровно эту проблему для документных типов - расширение на видео переиспользует готовый, уже понятный пользователю паттерн без введения второго механизма выхода из полноэкрана в интерфейсе.

---

## 10. Связи с другими спеками

S1114 (перенос кнопки входа в VR в нижний ряд команд видеоплеера) - смежная идея из того же device-теста на Quest 3, тот же ряд транспортных кнопок видео как отправная точка обсуждения. Не блокирует и не блокируется этой спекой; можно реализовывать независимо в любом порядке.

---

## 11. Критерии готовности (strategic-level)

1. При включении полноэкранного режима воспроизведения видео в основном плеере пользователь видит кнопку выхода из полноэкрана и нажатием возвращает командную панель.
2. То же самое выполняется в standalone-плеере открытого видеофайла.
3. Для PDF/EPUB/TXT поведение кнопки выхода из полноэкрана не изменилось.
4. Для изображений/GIF/аудио новых элементов интерфейса не появилось (кнопка ограничена видео).

---

## Last Audit

- **2026-07-26 21:05** - `/spec-test-device` on emulator-5554 (Pixel 9, Android 15 / SDK 35, standard-debug 2.60.7262.102, freshly built and version-confirmed installed). Verdict: PASS for the standalone-host transient-bars fix - the fix under audit works as designed in both standalone hosts (`PhotoVideoStandaloneActivity` and `StandalonePlayerActivity`): video opens in panel-hidden fullscreen when the setting is ON, no spurious immediate restore, exit-button tap restores the panel, and the OFF-setting regression path keeps the panel visible with no exit button. The edge-swipe leg of crit 1b/2 was NOT INJECTABLE on this AVD after 6 attempts (mobile-mcp + raw `adb input swipe`, both edges, multiple durations) - one attempt leaked into SystemUI's notification-shade pull instead of the app's transient-bars callback, confirming the gap is injection tooling, not the app. 0 FATAL in a 24 939-line capture.
- Scenario + evidence: `temp/S1115/mobile_test_scenario_20260726_2105.md`, `temp/S1115/run_20260726_2105.log`.
- **2026-07-26 19:26** - `/spec-test-device` on emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35, standard-debug 2.60.7220.314). Verdict: INCONCLUSIVE overall - main host fully PASS, standalone host (crit 2) not reachable.
- Scenario + evidence: `temp/S1115/mobile_test_scenario_20260726_1926.md`, `temp/S1115/screens/step01_main_fullscreen_button_visible.png`, `temp/S1115/screens/step02_main_panel_restored.png`, `temp/S1115/screens/step03_image_fullscreen_no_exit_button.png`, `temp/S1115/screens/step04_text_fullscreen_overlap.png`, `temp/S1115/run_20260726_1926.log`.
- **2026-07-24** - `/spec-test-device` on emulator-5554 (Pixel 9, Android 15, standard-debug 2.60.7220.314). Verdict: INCONCLUSIVE (visibility PASS, interactive half not drivable on emulator). Its "touch injection wedged" diagnosis is superseded: injection works, only the system top-edge band swallows taps (see crit 1b).
- Scenario + evidence: `temp/S1115/mobile_test_scenario_20260724_0059.md`, `temp/S1115/screens/step_main_fullscreen_exitbtn.png`, `temp/S1115/run_20260724_0059.log`.

### Manual / on-device

- [x] Crit 1a - Main player: entering video fullscreen shows the exit button top-end - verified on-device 2026-07-24, re-confirmed 2026-07-26 (btnDocumentFullscreenExit VISIBLE, clickable, content-desc "Выйти из полноэкранного режима", bounds [922,32][1048,158], command panel hidden).
- [x] Crit 1b - Main player: tapping the exit button restores the command panel - verified on-device 2026-07-26. Probe `S1115: main-host fullscreen-exit tapped` fired twice (19:31:06.564, 19:32:21.229) with `CLICK: DocumentFullscreenExit`; `topCommandPanel` restored and the exit button went GONE both times. Note: the tap must land on the button's lower half - at y=95 the OS top-edge reveal band swallows it and no TOUCH event reaches the app at all; y=150 works. This, not a touch-injection wedge, is what defeated the 2026-07-24 run.
- [x] Crit 2 - Standalone player: exit button visible in video fullscreen and tap restores panel - verified on-device 2026-07-26 21:05 after the transient-bars fix. `PhotoVideoStandaloneActivity` via `.StandaloneVideoPlayer` (explicit `am start`, alias left enabled from a prior run): with `openVideoInFullscreen` ON the video opens straight into panel-hidden fullscreen (probe `S1115: entered fullscreen with command panel hidden` @21:10:28.601, no immediate `S1115: transient bars appeared`), `topCommandPanel` absent from the a11y tree, `btnDocumentFullscreenExit` present+visible at [922,32][1048,158]; tapping it (985,150) fires `CLICK: FullscreenExit (PhotoVideoStandaloneActivity)` @21:13:28.347 and restores the panel, exit button gone. `StandalonePlayerActivity` (exported, launched directly): `btnFullscreenCmd` enters the same panel-hidden fullscreen (probe @21:14:13.647, no spurious restore) - regression confirmed for this host too. Edge-swipe restore (the other half of 1b/2) is NOT INJECTABLE on this AVD - see scenario `temp/S1115/mobile_test_scenario_20260726_2105.md` "Edge-swipe injection limitation"; not a defect in the fix, exit-button path fully substitutes as the verified, discoverable way out.
- [ ] Crit 3 - PDF/EPUB/TXT exit button unchanged - TXT PASSED on-device 2026-07-26 (button present in fullscreen, tap fired the probe at 19:35:13.579, panel restored). PDF/EPUB not exercised: the PDF fullscreen toggle only hides the system bars and then sits at [382,0][487,105], inside the top-edge swallow band. Left unchecked because the criterion covers all three.
- [x] Crit 4 - No exit button for images/GIF/audio - verified on-device 2026-07-26 for images: in image fullscreen `btnDocumentFullscreenExit` is ABSENT and the touch-zones overlay is shown instead. Audio not exercised (no fullscreen mode by design).
- [x] Regression - `openVideoInFullscreen` OFF: opening a video in `PhotoVideoStandaloneActivity` leaves `topCommandPanel` visible and `btnDocumentFullscreenExit` absent - verified on-device 2026-07-26 21:05, no new fullscreen-entry probe fired.

No app crash or exception observed in the 62 892-line capture; feature caused no instability.

Out-of-scope findings surfaced (not parked by this run, see scenario "Recommended follow-ups"): standalone host cannot enter video fullscreen at all; and in TXT fullscreen `btnDocumentFullscreenExit` [922,32][1048,158] overlaps `btnCloseTextViewer` [933,21][1059,147] in the same corner, contradicting the §7 risk row.

## Revision History

- **2026-07-24** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 15)
  - Scenario: temp/S1115/mobile_test_scenario_20260724_0059.md - PASS/FAIL/SKIPPED 1/0/4 (visibility PASS; 4 interactive/regression items blocked by emulator touch-injection wedge + welcome-loop, not by S1115) - Errors in log: 0
- **2026-07-26** - by `/spec-test-device` (`claude-opus-5[1m]`, device: emulator-5554 Android 15 / SDK 35)
  - Scenario: temp/S1115/mobile_test_scenario_20260726_1926.md - PASS/FAIL/SKIPPED 3/0/2 (crit 1a, 1b, 4 PASS; crit 3 PASS for TXT only; crit 2 not reachable in the standalone host) - Errors in log: 0, crashes: 0
