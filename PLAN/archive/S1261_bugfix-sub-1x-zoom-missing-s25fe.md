# Стратегическая спецификация: S1261 - Пропажа значений зума ниже 1x на Galaxy S25 FE

**Ticket:** S1261
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-28
**Tier:** 3 - Moderate (ad-hoc, bugfix)
**Roadmap entry:** Ad-hoc - запрос владельца 2026-07-28
**Tactical spec:** `PLAN/S1261_bugfix-sub-1x-zoom-missing-s25fe/` (будет создан через `/spec-tech`)

---

## 1. Проблема

После работ по определению камер (S1189) на телефоне владельца Galaxy S25 FE из ряда кнопок зума на экране съёмки пропали все значения меньше 1x, хотя системная камера устройства предлагает широкоугольную съёмку 0.5x. Камер в переключателе стало определяться больше, но дотянуться до широкого угла через ряд зума пользователь больше не может. Область - экран съёмки, слой перечисления линз и построения пресетов зума.

---

## 2. Цели

1. На Galaxy S25 FE ряд кнопок зума снова содержит значение ниже 1x, совпадающее с физически достижимым на устройстве.
2. Нажатие кнопки ниже 1x даёт широкоугольную картинку без дополнительных действий - переход на нужную линзу выполняется автоматически, как в системной камере.
3. Поведение подтверждено фактами с устройства: диагностический отчёт о камерах снят до правки и после неё.
4. На устройствах, где значения ниже 1x сегодня работают (POCO из S1189), поведение не ухудшается.
5. Подписи кратностей соответствуют системной камере: широкоугольник читается около 0.5, теле - своей честной кратностью, а не отношением сырых фокусных расстояний (по отчёту §6.1: 0.32 и 1.3 - неверные значения).
6. Экран съёмки открывается на главной задней камере, а не на самой широкой записи расширенного набора.

**Non-goals:**

- Пересмотр набора линз в переключателе камер - это S1189 и его приёмка.
- Изменение правила округления подписей кнопок - это S1260.
- Поддержка проприетарных OEM-механизмов сверх стандартных путей платформы.
- Wear OS.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Ряд зума должен читаться и работать как у системной камеры Samsung: широкий угол доступен одной кнопкой из основного ряда.

### 3.2 Жёсткие ограничения

- **Flavor:** экран съёмки живёт в общих исходниках и компилируется во все варианты сборки; flavor-гейта нет.
- **API level:** расширенные пути включаются по факту доступности на устройстве; деградация до текущего поведения обязательна (как в S1189 §3.2).
- **Wear OS:** не затрагивается.
- **Производительность:** дополнительный опрос характеристик не должен добавлять заметной задержки открытию экрана съёмки.
- **Локализация:** новых видимых строк не ожидается; если появятся - EN/RU/UK обязательно.
- **Доступность:** кнопки ряда зума сохраняют описания и зоны нажатия, введённые S1189.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** значения ниже 1x возвращаются в существующий ряд кнопок зума; новых панелей и элементов не появляется. Кнопка нативного минимума остаётся жёлтой по правилу S1189.
- **Accessibility:** контракт S1189 сохраняется - текстовые описания кнопок, зоны нажатия, отличие нативных границ не только цветом.
- **Validation level:** компиляция и статические проверки - автономно; приёмка - только на Galaxy S25 FE по диагностическому отчёту и наблюдаемой картинке.
- **Owner sign-off:** требуется - владелец подтверждает на своём устройстве появление и работу значения ниже 1x.
- **Related tickets:** S1189 (реализованный конвейер перечисления линз - место дефекта), S1260 (округление подписей в том же ряду).

---

## 4. Контекст текущей архитектуры

Ряд пресетов зума строится от возможностей привязанной линзы: нижняя граница берётся из её собственного диапазона, а эквивалентные кратности ниже 1x появляются либо когда сама привязанная камера объявляет минимум ниже единицы, либо когда широкоугольная линза найдена как отдельная запись расширенного перечисления и пересчитана через множитель кратности. S1189 ввёл расширенное перечисление физических подлинз и понятие «самой широкой достижимой кратности устройства», но ряд кнопок по-прежнему рисуется от диапазона привязанной линзы.

Samsung известен тем, что скрывает физические подлинзы от сторонних приложений: широкоугольная камера доступна не как подлинза логической камеры, а иначе - отдельной логической камерой либо диапазоном зума ниже единицы у основной. Какой из путей действует на S25 FE - вопрос фактов с устройства; без диагностического отчёта любая правка - угадывание (ADR-2 S1189 действует и здесь).

---

## 5. Предлагаемый подход

Починить нижнюю границу ряда зума как «свойство устройства», а не «свойство привязанной линзы»: ряд кнопок должен предлагать самую широкую достижимую кратность устройства независимо от того, какой линзой она достигается, а нажатие такой кнопки - самостоятельно переводить съёмку на нужную линзу.

### 5.1 Основные столпы / модули

- **Диагностика видит и сторону приложения.** Секция «Камеры» системного отчёта дополняется тем, что приложение вывело из платформенных данных: отобранный набор линз, привязанная линза, множитель и подписи ряда зума каждой записи, физический размер сенсора. Устройство владельца недоступно по adb - отчёт остаётся единственным каналом фактов (ADR-2 S1189).
- **Честный множитель кратности.** Эквивалентная кратность считается с нормировкой на размер сенсора (поле зрения), с перекрёстной проверкой по полу зума родительской логической камеры; отношение сырых фокусных остаётся только последним запасным путём.
- **Стартовая линза - главная.** При открытии экрана привязывается главная задняя камера, а не самая широкая запись набора.
- **Кросс-линзовые пресеты.** Ряд зума пополняется кратностями, достижимыми другими линзами того же направления; нажатие такой кнопки выполняет переход на линзу и установку зума одним действием.
- **Правило деградации.** На устройстве, где широкоугольной оптики нет или она не видна ни одним путём, ряд остаётся ровно сегодняшним.

### 5.2 Потоки данных и событий

- Открытие экрана -> перечисление линз -> расчёт самой широкой достижимой кратности устройства -> построение ряда с кросс-линзовыми пресетами -> отрисовка.
- Нажатие пресета ниже 1x -> определение целевой линзы -> перепривязка -> установка зума -> обновление снимка возможностей и подсветки ряда.

### 5.3 Точки расширяемости

- Источник «самой широкой достижимой кратности» должен допускать оба пути Samsung (отдельная логическая камера, диапазон ниже единицы у основной) без правки экрана съёмки.

---

## 6. Открытые вопросы / Research items

1. **Что именно сообщает S25 FE о своих камерах**
   - **Вопрос:** каким путём устройство отдаёт широкий угол - отдельной логической камерой, подлинзой или диапазоном зума ниже 1 у основной, и что из этого доходит до расширенного перечисления S1189.
   - **Решено:** отчёт получен 2026-07-28. Широкий угол отдаётся **всеми тремя путями сразу**: логическая камера 0 с диапазоном 0.57-10.00 (единственный источник значений ниже 1x), подлинза 0/2 и отдельная логическая камера 2 (обе с полом 1.00 в своей системе координат). Вскрыты три дефекта кода: стартовая линза выбирается «самая широкая задняя» вместо главной; эквивалент считается из сырых фокусных мм и врёт при разных сенсорах (0.32 вместо 0.57, 1.3 вместо ~3 у теле); диагностика не показывает, что из платформенных данных дошло до приложения.
   - **Статус:** Resolved.
   - **Артефакт:** `PLAN/S1261_bugfix-sub-1x-zoom-missing-s25fe/research/01__s25fe-camera-report.md`
2. **Было ли значение ниже 1x на этом устройстве до S1189**
   - **Вопрос:** пропажа - регрессия S1189 (сменилась привязываемая камера) или значение отсутствовало на S25 FE всегда.
   - **Решено:** владелец подтвердил (2026-07-28): 0.5 в приложении было видно и работало. Кнопка ниже 1x строится только от честного минимума привязанной камеры, поэтому это была настоящая широкоугольная оптика через логическую камеру (склейка линз платформой). Квалификация - регрессия S1189; приоритетная гипотеза: новое перечисление привязывает физическую основную линзу с полом 1.0 вместо логической камеры с полом 0.5.
   - **Статус:** Resolved.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перепривязка линзы по нажатию пресета даёт видимую паузу превью | Средняя | Ряд зума ощущается медленным | Переход выполняется тем же путём, что кнопка смены линзы; пауза не длиннее существующей смены камеры |
| Правка под Samsung ломает поведение на POCO | Средняя | Регрессия принятого S1189 | Кросс-линзовые пресеты добавляются, только когда кратность недостижима привязанной линзой; приёмка на обоих устройствах |
| Широкоугольная камера на S25 FE вовсе не видна стороннему приложению | Низкая | Значение ниже 1x недостижимо честными путями | Ряд остаётся сегодняшним; факт фиксируется в отчёте и тикет закрывается с обоснованием |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - восстановление заявленной S1189 возможности.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Нижняя граница ряда зума - свойство устройства, а не привязанной линзы**

- **Решение:** ряд кнопок строится от самой широкой достижимой кратности устройства; кнопка сама переводит на нужную линзу.
- **Альтернативы:** оставить ряд по привязанной линзе и требовать от пользователя ручной смены линзы перед широким углом.
- **Почему:** системная камера делает переход прозрачным; владелец явно ждёт того же (§3.1).

---

## 10. Связи с другими спеками

- S1189 - реализованный конвейер перечисления линз; настоящий тикет чинит его пробел на Samsung. Заметка в §10 S1189 о блоке S1191 устарела: S1191 закрыт, все 7 фаз S1189 выполнены.
- S1260 - округление подписей того же ряда кнопок; правки независимы, но проверяются на устройстве вместе.

---

## 11. Критерии готовности (strategic-level)

1. В диагностическом отчёте с S25 FE зафиксировано, каким путём устройство отдаёт широкий угол.
2. На S25 FE в ряду зума видна кнопка со значением ниже 1x, и её нажатие даёт широкоугольную картинку.
3. На POCO (приёмочное устройство S1189) ряд зума не потерял ни одного значения.
4. На устройстве без широкоугольной оптики ряд зума не изменился.

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 7 times: `initial lens 2` (5) and `floor pill tap eq=0,97` (2).
- The screen opened on lens 2 every time, which is the "opens on the main lens" criterion.
- Open question, and the reason this stays blocked: the S1260 label rows logged in the same sessions never contain 0.5 - the lowest label observed is 1.0 or 2.5 - while S1189 reports `native=0,5..10,0`. Either the 0.5 pill is the separate amber native pill and is fine, or it is genuinely missing from the row, which is exactly the defect. A screenshot of the zoom row settles it; the log alone does not. **Answered by the 2026-08-11 device test below: a label row without 0.5 proves nothing.**

---

## Last Audit

### Manual (device test 2026-08-11)

Device: Samsung Galaxy S21+ `RFCR110NBQJ` (SM-G996U1, Android 15 / API 35), standard debug 2.60.8111.809-DEBUG, app pid 23038 from a cold start. A session left on screen under pid 20957 was discarded and re-baselined before any observation. Evidence under `temp/S1261/`.

**This is not the acceptance handset.** The acceptance names a Galaxy S25 FE. Legs 3 and 4 are mechanism checks and carry over; legs 1 and 2 are the defect's symptoms and are hardware-shaped, so they are supporting evidence and not owner sign-off.

**Leg 3 - the camera screen opens on the main lens: PASS, device-independent.**
- Probe: `08-11 19:44:28.596 23038 D CameraCaptureSessionManager: S1261: initial lens 0`, preceded by `S1189: lens set [2, 0, 1, 3]`.
- expected: bind the main back lens rather than the widest entry of the set | actual: bound `[0]` (focal 5.40 mm, mult 1.00); the widest entry `[2]` (focal 2.20 mm, mult 0.52) sat first in the set and was not chosen.
- The app states the criterion itself: the app-side block marks that same lens `<- start`.

**Leg 4 - System info -> Cameras -> app-side block present: PASS, device-independent.**
- Block renders one line per enumerated lens under key `sysinfo_field_camera_app_view` (EN "App view", RU "Со стороны приложения").
- `App view: [2] back, focal 2.20 mm, min zoom 1.00, mult 0.52, presets: 0.5x 1x 3x 4x 5x 10x 17x`
- `App view: [0] back, focal 5.40 mm, min zoom 1.00, mult 1.00, presets: 1x 3x 5x 8x 10x 20x 30x <- start`
- Two front entries `[1]` and `[3]` follow, both `mult 1.00`.

**Leg 1 - the 0.5 pill exists and produces a wide-angle picture: PASS on this hardware.**
- Row at open: `0.5, 1, 3, 5, 8, 10, 20, 30`. The pill carries `content-desc="Zoom 0.5×, optical limit"`; the group is not scrollable and its bounds span every pill, so nothing was clipped out of view.
- This handset reaches 0.5 only cross-lens: camera `0` declares `Zoom range: 1.00 - 8.00`, so the bound lens has no sub-1 floor of its own and the pill can only come from lens `[2]`. The S21+ therefore exercises the ADR-1 cross-lens path in isolation, which the S25 FE does not - there camera 0 declares 0.57 and an easier path exists alongside it.
- Probe on tap: `08-11 19:46:55.945 23038 D CameraCaptureFlowManager: S1261: floor pill tap eq=0.52`.
- Framing measured, not asserted. Phone stationary, one blown-out 7-segment clock in frame as landmark. Its bright-pixel bounding box was `231x478 px` at 1x and `112x228 px` at 0.5x. expected: the same object subtends half the linear angle | actual: linear ratio 2.10x against 2.00 predicted. In the same action the lens label went `Macro` -> `Ultra-wide` and the readout `1x` -> `0.5x`, so this is a lens switch and not a relabelled crop.

**Leg 2 - the telephoto pill reads about 3, not 1.3: NOT TESTABLE here; the mechanism behind it is PASS.**
- The handset exposes no telephoto to the app. The enumerated set is main back `[0]`, ultra-wide back `[2]` and two front lenses; its 3x tele is hidden from third-party apps, so there is no tele multiplier to read. The `3` pill in the row is a digital preset, not an optical limit.
- The formula that produces the tele number is testable, on the wide side. For the ultra-wide the app reports `mult 0.52`. A raw focal ratio would give `2.20 / 5.40 = 0.41`; the FOV-normalized value is `(2.20/5.6) / (5.40/7.3) = 0.53`. expected: sensor-normalized rather than raw focal ratio | actual: 0.52, matching the normalized figure and the system camera's 0.5x. The honest-multiplier path of section 5.1 is live.

**The 2026-08-01/02 open question is now answered.** At open this device logged `S1260: display labels=[1.0, 3.0, 5.0, 8.0, 10.0, 20.0, 30.0]` - no 0.5 - while the live row rendered eight pills including `0.5`. The cross-lens floor pill is prepended outside the S1260 label list, so its absence from that log line is not evidence that the pill is missing. Only the rendered row or the app-side block settles it.

**A row without 0.5 is not automatically the defect.** The pre-existing session showed seven pills starting at `1`; its lens label read `Front`, and `renderLensLabel` names the active lens. A front camera has no wide-angle sibling, so the row correctly stays as it is under the section 5.1 degradation rule.

**Outside these four legs.** At 1x on the main lens the label reads `Macro`, because `macroLensFor` picks the back lens with the largest `minFocusDistanceDiopters` over a threshold and this device's main lens focuses closest (14.3 dpt). Labelling only - it does not change which lens is bound. Belongs to the S1189 lens-naming rule; `/spec-draft` candidate, not captured from this read-only sweep step.
