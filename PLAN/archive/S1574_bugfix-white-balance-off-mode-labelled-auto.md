# Спецификация (compact bugfix): S1574 - Режим баланса белого OFF подписан как «Auto» и дублирует его в списке

**Ticket:** S1574
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Текст:**

The camera White balance dropdown lists "Auto" twice - as the first and as the last entry. Observed on a real device (SM-G996U1, Android 15, standard debug v2.60.8082.309, 2026-08-11) via Programs -> Camera -> Camera settings -> White balance. Full list as rendered: Auto, Incandescent, Fluorescent, Warm fluorescent, Daylight, Cloudy, Twilight, Shade, Auto - nine rows for the nine Camera2 AWB modes.

Root cause is visible in CameraSettingsDialogFragment.whiteBalanceLabel (CameraSettingsDialogFragment.kt:350-360): the `when` maps AUTO, INCANDESCENT, FLUORESCENT, WARM_FLUORESCENT, DAYLIGHT, CLOUDY_DAYLIGHT, TWILIGHT and SHADE, and then `else -> getString(R.string.camera_setting_wb_auto)`. CONTROL_AWB_MODE_OFF (value 0) has no branch, so it falls through to the else and is labelled "Auto" - the same string as CONTROL_AWB_MODE_AUTO (value 1). The sort at CameraSettingsDialogFragment.kt:199 puts AUTO first and everything else after, so OFF lands at the end of the list, which is why the duplicate reads as a trailing "Auto".

The two rows do opposite things: the first enables auto white balance, the last disables it. Nothing in the UI distinguishes them.

Interaction with S1418: the profile button reads "Shooting profile: Manual" whenever white balance is off automatic. Picking the trailing "Auto" therefore flips the button to Manual while the chosen label claims Auto - the user sees a contradiction with no way to explain it.

**Захвачено во время:** device-sweep камеры, круг 1 (проверка S1418)

---

## 1. Проблема / симптом

В выпадающем списке «White balance» пункт «Auto» присутствует дважды - первым и последним. Устройство SM-G996U1, Android 15 (SDK 35), flavor standard, debug-сборка v2.60.8082.309, путь Programs -> Camera -> Camera settings -> White balance.

Список рендерится из режимов AWB, которые заявляет камера. У Camera2 их девять, и восемь имеют собственную подпись. Девятый - режим OFF - своей ветки не имеет и получает подпись автоматического режима через ветку `else`.

Последствие не косметическое: два одинаково подписанных пункта делают противоположное. Первый включает автоматический баланс белого, последний - выключает его.

Сопряжение с S1418: кнопка профиля переходит в «Manual», когда баланс белого уведён с автоматического. Выбор последнего пункта «Auto» переводит кнопку в «Manual», хотя выбранная подпись говорит «Auto».

---

## 2. Корневая причина

Два независимых списка режимов, между которыми нет связи:

- **что показать** - `CameraSettingsDialogFragment` (строка 198) берёт `capabilities.awbModes` целиком, то есть всё, что заявила камера через `CONTROL_AWB_AVAILABLE_MODES`, и только сортирует, ставя AUTO первым (строка 199);
- **как подписать** - `whiteBalanceLabel` (строки 350-360) знает восемь режимов, а девятый уводит в `else -> getString(R.string.camera_setting_wb_auto)`.

Пока эти списки расходятся, любой незнакомый режим не просто остаётся без подписи, а получает чужую - и именно подпись автоматического режима. `CONTROL_AWB_MODE_OFF` (значение 0) - первый и пока единственный такой режим.

Почему OFF в этом списке вообще быть не должен, а не просто получить свою подпись: по контракту Camera2 при `CONTROL_AWB_MODE_OFF` баланс белого задаёт приложение через `COLOR_CORRECTION_TRANSFORM` и `COLOR_CORRECTION_GAINS`. Их в дереве нет вовсе - grep по `app_v2/src/main/java` не находит ни одного упоминания. `CameraCaptureSessionManager` (строки 429-430, 867-868) просто прокладывает выбранное значение в `CaptureRequest.CONTROL_AWB_MODE`. То есть выбор OFF отдаёт баланс белого приложению, которое им не управляет: результат не «ручной режим», а неопределённый.

Наблюдаемое положение пункта - следствие сортировки: `sortedBy { if (it == AUTO) 0 else 1 }` устойчива, поэтому OFF сохраняет своё место в исходном порядке камеры и оказывается последним. Отсюда и «Auto» в конце списка.

Состояние сессионное, не сохраняемое: `whiteBalanceMode` живёт в `CameraCaptureSessionManager` и сбрасывается в `null` (строка 276), а `CameraSettingsCallbackHandler` (строка 47) читает его как `?: CONTROL_AWB_MODE_AUTO`. Миграция сохранённого значения не нужна.

---

## 3. Исправление

Свести оба списка в один источник истины - упорядоченную карту «режим -> строковый ресурс» в `companion object` фрагмента:

- Список для показа строится как пересечение ключей карты с тем, что заявила камера, поэтому порядок отображения задаёт карта (AUTO первым) и отдельная сортировка больше не нужна.
- `whiteBalanceLabel` читает подпись из той же карты. Ветки `else` не остаётся - режим, которого нет в карте, не может попасть ни в список, ни в подпись. Это и есть структурная часть: следующий незнакомый режим теперь просто не показывается, вместо того чтобы выдать себя за Auto.
- `CONTROL_AWB_MODE_OFF` в карту не входит - по причине из раздела 2.
- Если черновик диалога держит режим, которого в списке уже нет (сессия, оставленная на OFF до этой правки), он приводится к AUTO явно. Иначе строка показывала бы Auto, а подтверждение диалога вернуло бы в сессию OFF.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1418 (кнопка профиля читает состояние баланса белого) - потребитель того же состояния, зависимости нет

---

## 4. Проверка

- Юнит-тест на чистую функцию отбора: из набора режимов, включающего OFF и вымышленный неизвестный, остаются только знакомые, AUTO первым, дубликатов подписей нет. Проверяется без Android-контекста - отбор оперирует ключами карты, а не строками.
- `.\a.ps1 fk` - компиляция Kotlin, `expected: exit 0`.
- `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - все гейты зелёные.
- На устройстве (перенесено, не гейт): Programs -> Camera -> Camera settings -> White balance показывает восемь пунктов, «Auto» ровно один раз и первым. Требует сборки и установки debug-APK.
- Строковые ключи не добавляются и не удаляются, поэтому аудит локализации не затрагивается.

---

## Last Audit

**Date:** 2026-08-11
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Programs -> Camera -> Camera settings -> White balance: восемь пунктов, «Auto» один раз и первым. Не гейт: инвариант закреплён юнит-тестами, а проверка требует сборки и установки debug-APK.

### Evidence

- Три новых теста `CameraSettingsWhiteBalanceModesTest`: OFF не предлагается, неизвестный вендорский режим не предлагается, AUTO первый и без повторов.
- Тесты действительно исполнились, а не были пропущены: `TEST-com.sza.fastmediasorter.ui.cameracapture.CameraSettingsWhiteBalanceModesTest.xml`, `tests=3 failures=0 errors=0 skipped=0`, отметка времени за 18 секунд до чтения. Проверено по XML, а не по `BUILD SUCCESSFUL` - вердикт сборки за 2 секунды сам по себе не отличает «прошло» от «не запускалось».
- Компиляция Kotlin доказана тем же прогоном (`compileStandardDebugUnitTestKotlin` собирает main и тестовый source set).
- `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` -> `PASS WITH ADVISORIES (1)`, exit 0; сам `detekt-gate` зелёный. Единственная advisory - лексический `detekt-preflight` на `MagicNumber (literal 1.0)` в `CameraSettingsDialogFragment.kt:377`: это форматирование выдержки, кода я там не менял, строка лишь сместилась вверх из-за сокращения `whiteBalanceLabel`.
- Структурная часть, а не только точечная: фильтр и подписи - одна карта, поэтому режим без подписи физически не может попасть в список. Прежний `else -> camera_setting_wb_auto` выдавал бы за Auto и любой будущий незнакомый режим, не только OFF.
- Черновик диалога приводится к AUTO, если держит режим вне списка, - иначе строка показывала бы Auto, а подтверждение вернуло бы OFF в сессию.
- Строковые ключи не тронуты: изменилось только то, какие из существующих используются, поэтому паритет локалей не затрагивается.
- Инвариант отладочных меток: `Timber.d("S1574:` - 0 вхождений.
- `docs/ALL_FEATURES.jsonl` - EXEMPT: убран сломанный пункт существующей настройки, новой возможности не поставлено.

### Замечание для S1418

Кнопка профиля («Shooting profile: Manual») читает `whiteBalanceMode != AUTO`. Противоречие «подпись Auto, кнопка Manual» исчезает вместе с самим пунктом - отдельной правки в S1418 не требуется.
