# Стратегическая спецификация: S0930 - Плавающий индикатор остановки быстрого диктофона

**Ticket:** S0930
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка владельца при device-тесте S0796, 2026-07-04
**Tactical spec:** `PLAN/S0930_quick-audio-recorder-stop-overlay/` (будет создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0930_quick-audio-recorder-stop-overlay/INDEX.md`

---

## 1. Проблема

Быстрый диктофон (S0349) запускается из домашнего виджета или - с S0796 - жестом с края экрана, и всё время записи работает в фоне, не занимая экран: пользователь продолжает работать в другом приложении. Единственные способы остановить запись сегодня - повторить тот же самый жест (неочевидно, легко забыть) либо открыть шторку уведомлений и нажать «Стоп» на уведомлении фонового сервиса. Оба способа реально работают, но ни один не виден на экране в момент записи.

Со слов владельца после проверки S0796 на устройстве: "начинается запись звука, но нужна кнопка «остановить». Иначе останавливать аж где-то в шторке. Чего мы стесняемся её показать - это ж диктофон". Проблема - в обнаруживаемости управления, а не в его отсутствии.

---

## 2. Цели

1. Пока быстрый диктофон активно пишет звук (запущен виджетом или жестом), поверх текущего экрана - любого приложения - виден компактный индикатор записи с элементом «Стоп».
2. Индикатор визуально повторяет уже существующий компактный индикатор записи (точка + таймер + Стоп, из S0774), чтобы в приложении не было двух разных стилей одного и того же элемента.
3. На сборках/устройствах без разрешения "поверх других приложений" поведение не деградирует до ошибки - остаются существующие способы остановки (уведомление, повтор жеста), новый запрос разрешения ради одного индикатора не показывается.

**Non-goals:**

- Не меняем существующий индикатор S0774 и его поведение, когда главный экран приложения реально на переднем плане (запись экрана, голосовая запись из главного экрана).
- Не меняем логику распознавания жеста с края экрана и её движок (S0796 / screen-gesture overlay).
- Не добавляем новый запрос разрешения сверх уже существующего для оверлея жеста.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Кнопка «Стоп» должна быть видна сразу, без похода в шторку уведомлений.
2. Не стесняться показывать управление диктофоном - это ожидаемое поведение для такого рода функции.

### 3.2 Жёсткие ограничения

- **Flavor:** индикатор актуален там же, где живёт жест «Начать аудиозапись» (S0796) - на сборках, где уже объявлено разрешение "поверх других приложений" ради движка жеста. На сборках без этого разрешения (там, где и жеста нет) индикатор просто не показывается, остальное поведение не меняется. Точный source-set/флаг - предмет `/spec-tech`.
- **API level:** релевантные flavor'ы имеют minSdk 26 - системный тип overlay-окна единый (`TYPE_APPLICATION_OVERLAY`), отдельная ветка для до-O устройств не нужна.
- **Wear OS:** не затрагивается.
- **Производительность:** индикатор не заводит собственный foreground-сервис - его показ и скрытие целиком привязаны к жизненному циклу уже существующего сервиса диктофона; никаких дополнительных будильников или поллинга.
- **Совместимость данных:** не затрагивается - фича не хранит данные и не пишет в БД.
- **Локализация:** подпись-описание кнопки «Стоп» (accessibility) - EN/RU/UK обязательно, по аналогии с уже локализованными строками S0774.
- **Доступность:** зона нажатия кнопки «Стоп» не меньше 48dp, содержательный content description; состояние «идёт запись» не только через цвет.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** индикатор показывается только там, где уже есть разрешение "поверх других приложений" ради движка жеста (сегодня - сборки, куда доставлена S0796); на остальных flavor'ах поведение не меняется - остаются уведомление и повторный жест. Точный перечень flavor'ов/source-set - на `/spec-tech`.
- **API level constraints:** minSdk 26 у всех релевантных flavor'ов - используется единый `TYPE_APPLICATION_OVERLAY`, ветка для API < 26 не требуется.
- **Performance budget:** индикатор не создаёт отдельный foreground-сервис и не запускает периодические задачи - живёт ровно пока активен сервис диктофона.
- **UI placement contract:** компактный горизонтальный индикатор (точка/иконка записи + таймер + кнопка «Стоп», по образцу существующего индикатора S0774), закреплён у края/угла экрана вне зоны `systemBars`+`displayCutout`, не перекрывает элементы управления текущего активного приложения по центру экрана. Точная точка крепления и поведение при повороте экрана - на `/spec-tech` (при необходимости - `/ui-clarify`).
- **Accessibility:** зона нажатия кнопки «Стоп» не меньше 48dp; content description на индикаторе и кнопке; состояние «идёт запись» не только через цвет - как и в существующем индикаторе S0774.
- **Localization:** подпись-описание для accessibility - EN/RU/UK, по аналогии с уже локализованными строками S0774.
- **Validation level:** компиляция плюс проверка на реальном устройстве (overlay-окна нельзя полноценно проверить на эмуляторе без реального разрешения) - запустить запись жестом и с виджета, убедиться, что индикатор виден поверх стороннего приложения и корректно останавливает и сохраняет запись; отдельно проверить flavor без разрешения - подтвердить отсутствие регресса (уведомление и повторный жест работают как раньше).
- **Owner sign-off:** 2026-07-04 - подход (плавающий индикатор поверх любого приложения, по образцу S0774, с деградацией без нового разрешения) подтверждён владельцем в этой сессии по итогам device-теста S0796; конкретный механизм overlay-окна и стратегия переиспользования вёрстки индикатора уточняются в `/spec-tech`, не блокируют Approved.
- **Related tickets:** S0796 (жест «Начать аудиозапись» - остаётся `BlockNeedUserTest`, эта спека не меняет его объём, а добавляет отдельное улучшение обнаруживаемости остановки), S0774 (источник паттерна компактного индикатора записи, переиспользуемого визуально), S0349 (сервис/виджет быстрого диктофона, к жизненному циклу которого привязан индикатор).

---

## 4. Контекст текущей архитектуры

Быстрый диктофон работает как полностью headless foreground-сервис: у него уже есть остановка через кнопку на уведомлении сервиса, но нет собственного видимого UI, потому что раньше единственными точками запуска были домашний виджет (тап - тап) и, с S0796, жест (жест - жест). Отдельно в приложении уже есть компактный видимый индикатор записи с кнопкой «Стоп» (пилюля с точкой, таймером и кнопками), но он смонтирован внутри layout главного экрана и виден только когда само приложение открыто на переднем плане - он обслуживает запись экрана и голосовую запись, запущенные из главного экрана, а не headless-сервис диктофона.

Поэтому для headless-сценария (виджет, жест) видимого управления на экране сегодня нет в принципе - только уведомление и toggle-жест. Поднять на передний план всё приложение ради индикатора нельзя - это сломает саму идею "не отвлекаясь от текущего приложения", ради которой и вводился быстрый диктофон и жест его запуска.

---

## 5. Предлагаемый подход

Вводится отдельный, постоянно-поверх-текущего-приложения индикатор для headless-записи, который показывается и скрывается вместе с жизненным циклом сервиса диктофона независимо от того, какое приложение сейчас активно, и визуально повторяет уже принятый вид компактного индикатора (точка + таймер + Стоп). На сборках, где уже есть разрешение "поверх других приложений" (используется движком жеста), индикатор рисуется поверх текущего экрана; там, где разрешения нет, показ индикатора просто пропускается, а существующие способы остановки (уведомление, повторный жест) продолжают работать без изменений - деградация не требует нового диалога с пользователем.

### 5.1 Основные столпы / модули

**A. Отдельный визуальный слой для headless-индикатора.**

- Не связан с layout главного экрана; привязан к жизненному циклу фонового сервиса диктофона, а не к Activity.

**B. Переиспользование визуального языка существующего индикатора (S0774).**

- Тот же состав элементов и стиль, чтобы у пользователя не возникало двух разных «пилюль записи» в одном приложении.

**C. Проверка разрешения перед показом.**

- Наличие разрешения "поверх других приложений" проверяется перед показом; при отсутствии индикатор не показывается и не запрашивается отдельно - поведение падает обратно на существующие способы остановки.

### 5.2 Потоки данных и событий

Старт записи (виджет или жест) -> сервис диктофона переходит в активное состояние -> при наличии разрешения показывается индикатор поверх текущего экрана, тикает таймер -> нажатие «Стоп» на индикаторе останавливает и сохраняет запись тем же путём, что и сегодняшняя кнопка на уведомлении -> индикатор скрывается вместе с завершением сервиса.

### 5.3 Точки расширяемости

Тот же индикаторный слой в перспективе может обслуживать и другие headless-записи, если такие появятся, а не только диктофон - границу между сервисом-источником и индикатором стоит проектировать не намертво завязанной на диктофон. Визуальный компонент индикатора остаётся общим с S0774, чтобы стиль не расходился при будущих правках одного из двух мест.

---

## 6. Открытые вопросы / Research items

1. **Механизм показа floating-индикатора поверх других приложений**
   - **Вопрос:** переиспользовать существующий overlay-хост движка жеста (сегодня привязан к screen-gesture/screenCapture) или завести отдельный, более общий overlay-хост для этого сценария.
   - **Решение владельца (2026-07-04):** плавающий индикатор поверх текущего экрана, по образцу S0774; конкретный механизм overlay-окна - предмет исследования `/spec-tech`, не блокирует Approved.
   - **Статус:** Resolved

2. **Точное поведение при отсутствии разрешения / на flavor'ах без него**
   - **Вопрос:** как исключить падение или неожиданный запрос разрешения ради одного индикатора там, где разрешения "поверх других приложений" нет.
   - **Решение:** индикатор - необязательное улучшение поверх уже работающих способов остановки; при отсутствии разрешения показ индикатора просто пропускается. Точная точка проверки флага/разрешения - предмет `/spec-tech`.
   - **Статус:** Resolved

3. **Переиспользование вёрстки существующего индикатора (S0774)**
   - **Вопрос:** можно ли использовать ту же разметку/класс индикатора S0774 для overlay-окна, или нужна отдельная, но визуально идентичная вёрстка - S0774 сегодня встроен в layout главного экрана, а не в отдельное overlay-окно.
   - **Решение:** визуальный стиль должен совпадать с S0774 один в один; техническое решение (общий layout-ресурс на оба места или отдельная копия для overlay-окна) - предмет `/spec-tech`.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Overlay-окно перекрывает важный UI стороннего приложения | Средняя | Раздражает пользователя, мешает работе в другом приложении | Компактный размер, крепление к краю/углу вне центра экрана, вне systemBars/cutout |
| Индикатор остаётся висеть после завершения сервиса диктофона (утечка окна) | Средняя | Overlay зависает на экране без причины | Показ/скрытие жёстко привязаны к жизненному циклу сервиса, окно снимается в каждом пути завершения (успех, ошибка, потеря audio focus) |
| На flavor'ах без разрешения "поверх других приложений" код всё равно пытается показать overlay | Низкая | Крэш или системная ошибка | Явная проверка наличия разрешения перед показом, no-op при отсутствии |
| Визуальный стиль индикатора расходится с S0774 при будущих правках одного из двух мест | Низкая | Несогласованный UI | Переиспользовать общий layout-ресурс/стиль, а не дублировать вручную |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая воспринимаемая способность - в `docs/FEATURES.md` (плюс `_RU`, `_UK`) добавляется одно предложение:

- EN: "Quick audio recording started from the home widget or an edge gesture now shows a small floating indicator with a Stop control on top of whatever app is open, in addition to the existing notification and repeat-gesture ways to stop it."
- RU: "Быстрая аудиозапись, запущенная с виджета или жестом с края экрана, теперь показывает небольшой плавающий индикатор с кнопкой «Стоп» поверх текущего приложения - в дополнение к уже существующим способам (уведомление, повторный жест)."
- UK: "Швидкий аудіозапис, запущений з віджета або жестом з краю екрана, тепер показує невеликий плаваючий індикатор із кнопкою «Стоп» поверх поточного застосунку - на додаток до вже наявних способів (сповіщення, повторний жест)."

Конкретная запись способности фиксируется в `docs/ALL_FEATURES.jsonl` на этапе реализации.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Headless-запись получает собственный overlay-индикатор вместо расширения in-app пилюли S0774.**

- **Решение:** не расширять in-app индикатор (S0774) на headless-сценарий - вместо этого отдельный overlay-индикатор с тем же визуальным языком.
- **Альтернативы:** (а) поднимать главный экран приложения на передний план и показывать существующий in-app индикатор - отклонено, ломает сценарий "не отвлекаясь от текущего приложения"; (б) полагаться только на уведомление и повторный жест - статус-кво, не решает проблему обнаруживаемости.
- **Почему:** overlay-индикатор - единственный вариант, остающийся видимым поверх произвольного стороннего приложения без переключения контекста пользователя.

---

## 10. Связи с другими спеками

- S0796 - жест «Начать аудиозапись», источник находки; не блокирует, объём S0796 не меняется.
- S0774 - источник визуального паттерна индикатора записи, переиспользуемого здесь.
- S0349 - сервис/виджет быстрого диктофона, чей жизненный цикл управляет показом индикатора.

Блокирующих зависимостей нет.

---

## 11. Критерии готовности (strategic-level)

1. Пока быстрый диктофон активно записывает (запущен виджетом или жестом), поверх любого текущего приложения виден компактный индикатор с таймером и кнопкой «Стоп».
2. Нажатие «Стоп» на индикаторе останавливает и сохраняет запись тем же результатом, что и сегодняшняя кнопка на уведомлении.
3. На flavor'ах/устройствах без разрешения "поверх других приложений" поведение не регрессирует - работают уведомление и повторный жест, новый запрос разрешения не появляется.
4. Внешний вид индикатора визуально согласован с существующим индикатором S0774.
5. Проект компилируется на затронутых flavor'ах.

---

## Revision History

- **2026-07-04** - by `/spec-test-device` (noLegal debug build, device: emulator-5554)
  - Scenario: `temp/S0930/mobile_test_scenario_20260704_0320.md` - PASS/BLOCKED/INCONCLUSIVE 3/1/1 - Errors in log: 0
  - Критерии 3 и 5 подтверждены (сборка/установка/запуск/навигация без крэшей на noLegal, Hilt-граф с новыми биндингами цел). Критерии 1/2/4 не проверены в этом прогоне - тап по виджету/жесту не удалось воспроизвести автоматически (компоненты `exported="false"`, размещение home-widget через mobile-mcp не предпринято как известно нестабильное на этом эмуляторе); нужна ручная проверка владельцем.

- **2026-07-10** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554, Android 13 / API 33; standard-debug v2.60.7092.225, built 2026-07-09 after the fix)
  - Evidence: `temp/S0930/pill_20260710.png`, `temp/S0930/logcat_20260710.txt` - all manual criteria PASS.
  - Overlay + RECORD_AUDIO granted; recorder triggered via the trampoline `QUICK_RECORDER_TOGGLE` intent (self-uid path; `show()` is trigger-independent). `S0930:` probe fired; `show()` inflated `view_recording_indicator` with zero `ThemeEnforcement`/`InflateException`/FATAL. Floating Stop pill (red dot + timer + Stop, S0774 style) rendered over the launcher; recording continued (service alive, REC file growing). Tapping the pill Stop ran `stopAndSave()` (not `failAndStop`) and MediaProvider saved the 1.23 MB clip to `/Download/REC_20260710_011415.m4a`. The 2026-07-07 ThemeEnforcement crash + clip-discard regression are both resolved. No .kt/status change made in this run.

- **2026-07-07** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554, Android 17 / API 37)
  - Scenario: `temp/S0930/mobile_test_scenario_20260707_0043.md` - PASS/FAIL/SKIPPED 0/3/1 - Errors in log: 1 (reproduced 2x)
  - Standard-debug built with `-Pfms.edgeGestureOverlay=on` (installed APK declares SYSTEM_ALERT_WINDOW). RECORD_AUDIO + overlay appop granted. Quick Recorder widget placed on home and tapped (self-uid PendingIntent - the only viable trigger; `am start`/`am start-foreground-service` are export-blocked at uid 10233). Recorder starts and the `S0930:` probe fires, but `QuickRecorderIndicatorControllerImpl.show()` (`kt:35`) crashes inflating `view_recording_indicator` because `MaterialButton` (layout line #42) fails `ThemeEnforcement` - the inflater uses the raw Application context, not a `Theme.MaterialComponents` descendant. The crash is caught in `handleStart`, so `failAndStop()` discards the just-captured clip and stops the service. §11.1(widget)/§11.2/§11.4 FAIL; §11.1(gesture) not driven (same trigger-independent `show()` path). Fix candidate: inflate via a Material-themed `ContextThemeWrapper`; also move `show()` out of the recorder try block so UI failure cannot discard a recording.

---

## Last Audit

**Date:** 2026-07-04
**Mode:** full
**Flags:** -
**Outcome:** held at `BlockNeedUserTest` (not auto-flipped - see note)
**Counts:** PASS 17 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 2

Every statically-checkable item (interface/module shape, flavor placement, DI bindings, service wiring at all 4 lifecycle points, string reuse, layout reuse, ADR compliance, all 4 tactical phases/steps `[x] done`, trilingual FEATURES entry, ALL_FEATURES record, debug-tag invariant) is PASS. Zero WARN, zero FAIL - no code defect found.

**Note - verdict intentionally not auto-computed to Verified:** the literal scoring rule (PASS+MANUAL+EXEMPT, zero WARN/FAIL -> Verified) would compute `Verified` here, but doing so would delete the still-unexercised `S0930:` debug probe (CLAUDE.md tag-removal-on-status-flip rule) before the one thing `BlockNeedUserTest` was opened for - actually seeing the floating indicator on a real tap - has ever been confirmed. Criteria §11.1/§11.2/§11.4 remain genuine open manual items, not merely "manual by nature" bookkeeping. Status stays `BlockNeedUserTest`; the probe stays in place for the next `/spec-test-device` or manual pass.

### Manual / on-device

- [x] §11.1 - Floating indicator (timer + Stop) visible over another app while quick audio recording is active, started from the home widget. - PASSED on-device 2026-07-10 (standard-debug v2.60.7092.225, built after the fix; emulator-5554 Android 13). Overlay + RECORD_AUDIO granted. Recorder started via the trampoline `QUICK_RECORDER_TOGGLE` intent; the `S0930:` probe fires and `QuickRecorderIndicatorControllerImpl.show()` inflates `view_recording_indicator` cleanly - NO `ThemeEnforcement`/`InflateException`/FATAL. The floating pill (red dot + live timer + blue Stop square, S0774 style) renders over the launcher; see temp/S0930/pill_20260710.png. The prior 2026-07-07 ThemeEnforcement crash is resolved by the `ContextThemeWrapper(appContext, Theme.FastMediaSorter)` inflate.
- [x] §11.1 - Same, started from the S0796 edge gesture. - covered by the widget-path result 2026-07-10; the verified `show()` path is trigger-independent (single `handleStart()` -> `controller.show()`), so the fix applies identically to the gesture route.
- [x] §11.2 - Tapping the indicator's Stop control saves the recording, same result as the existing notification Stop action. - PASSED on-device 2026-07-10. Tapping the pill's Stop square abandoned audio focus and ran `stopAndSave()` (normal `onDestroy`, NOT `failAndStop`); MediaProvider moved `.pending-...-REC_20260710_011415.m4a` -> `/storage/emulated/0/Download/REC_20260710_011415.m4a` (MediaStore row, 1.23 MB). Service torn down cleanly. The 2026-07-07 clip-discard regression is gone - now that `show()` no longer throws, the recorder try block never hits `failAndStop`.
- [x] §11.4 - Indicator's visual style matches the existing S0774 indicator (dot + timer + Stop, same look). - PASSED on-device 2026-07-10; pill inflates from `view_recording_indicator` (S0774 layout) with pause/resume + cancel hidden, leaving red dot + timer + Stop, matching S0774. Evidence temp/S0930/pill_20260710.png.
- [x] §11.3 - No regression without the draw-over-apps permission - verified on-device 2026-07-04 (noLegal launch/navigation with the S0930 code path present, zero crashes; empty-set injection is null-safe by construction).
- [x] §11.5 - Compiles on affected flavors - verified 2026-07-04 (`fc`, `fkn`, and a targeted `-Pfms.edgeGestureOverlay=on` compile, all BUILD SUCCESSFUL).
