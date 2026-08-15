# Стратегическая спецификация: S1167 - Видимость полосок жестов на заблокированном экране

**Ticket:** S1167
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-24
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-24
**Tactical spec:** `PLAN/S1167_lock-screen-gesture-strips-visibility/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-24

**Текст:**

могу ли я спрятать полоски жестов когда экран заблокирован. если в этих конкретно полосках нет акций, которые работают с этого экрана. Ну например фонарик так работает

---

## 1. Проблема

Полоски краевых жестов рисуются оверлейным окном поверх всего экрана и живут ровно столько, сколько живёт хост-сервис: окно добавляется один раз и не пересматривается при смене состояния экрана. Осведомлённости о блокировке в подсистеме нет вообще.

Владелец подтвердил на устройстве: полоски видны поверх заблокированного экрана. При этом состояний у заблокированного устройства два, и ведут они себя по-разному:

- Полная блокировка - экран погашен либо показывает always-on. Не работает ни один жест, а полоски всё равно занимают края.
- Приглашение к разблокировке - экран уже освещён, часть жестов работает, тот же фонарик.

Первое состояние - чистая помеха: полоски видны, но бесполезны.

---

## 2. Цели

1. При полной блокировке - погашенный экран или always-on - полоски не отображаются вовсе.
2. На экране приглашения к разблокировке полоски отображаются и работают как сейчас.
3. Переход между двумя состояниями в обе стороны не требует перезапуска сервиса и не оставляет фантомных зон касания.

**Non-goals:**

- Классификация действий по способности работать под блокировкой. Владелец снял этот пункт: правило опирается на состояние экрана, а не на то, что назначено конкретной полоске.
- Ручной переключатель видимости полосок на заблокированном экране.
- Обход keyguard для действий, которые под ним не работают.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Правило автоматическое - настраивать пользователю нечего.

### 3.2 Жёсткие ограничения

- **Flavor:** noLegal - полоски есть всегда; standard - только при включённом флаге сборки `fms.edgeGestureOverlay`, сегодня выключенном по умолчанию. lite, photos, legacy этот исходный набор не собирают и не затрагиваются.
- **API level:** минимум подсистемы - 26; определение состояния экрана отдельного гейта не требует.
- **Wear OS:** не затрагивается.
- **Производительность:** пересчёт только по событию смены состояния экрана, не по таймеру.
- **Совместимость данных:** новых настроек нет, миграции нет.
- **Локализация:** пользовательских строк не добавляется.
- **Доступность:** скрытая полоска не оставляет активной зоны касания - иначе жест на заблокированном экране съедается без результата.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Полоски рисует общий менеджер оверлея, добавляющий по одному окну на включённую зону. Хостов у него два: сервис поверх окон приложения и служба доступности - какой именно, зависит от флейвора и версии Android. Оба хоста при старте спрашивают у диспетчера жестов, какие зоны включены, и оба одинаково не пересматривают это решение потом.

Событий смены состояния экрана подсистема не слушает: приёмника на погасание и включение экрана в ней нет.

---

## 5. Предлагаемый подход

Сделать состояние экрана входом в тот же расчёт видимости зон, который уже существует.

### 5.1 Основные столпы / модули

- Наблюдение за состоянием экрана: погашен либо always-on против освещённого.
- Расчёт видимости: набор окон пересобирается при каждой смене состояния.
- Реакция симметрично в обоих хостах оверлея, а не в одном.

### 5.2 Потоки данных и событий

Экран гаснет или уходит в always-on -> хост оверлея снимает все окна полосок -> экран освещается -> набор окон пересобирается из включённых зон.

### 5.3 Точки расширяемости

- Признак «полоски сейчас показывать нельзя» остаётся одним предикатом, чтобы к нему можно было добавить условие, не переписывая хосты.
- Пересчёт видимости остаётся общим для обоих хостов.

---

## 6. Открытые вопросы / Research items

1. **Решён.** Видны ли полоски над keyguard сегодня - да, подтверждено владельцем на устройстве 2026-07-24.
2. **Решён.** Классификация действий по работе под блокировкой не нужна: правило строится на состоянии экрана, см. ADR-1 и ADR-2.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| always-on показывается без события погасания экрана | Средняя | Полоски остаются видны на always-on | Признак берётся по интерактивности экрана, а не только по событию погасания; проверяется на устройстве с включённым always-on |
| Фантомная зона касания при скрытии через прозрачность | Средняя | Жест съедается без результата | Скрытие - снятие окна, а не смена цвета (§2.3) |
| Расхождение двух хостов оверлея | Средняя | Поведение зависит от флейвора и версии Android | Пересчёт общий, правка симметрична |
| Подсистема не покрыта тестами | Высокая | Регрессия незаметна | Предикат видимости покрыть юнит-тестом как чистую функцию |

---

## 8. Влияние на пользователя (docs/FEATURES)

Полоски жестов не занимают края погашенного экрана и always-on.

---

## 9. Архитектурные решения (ADR)

**ADR-1. Скрытие привязано к состоянию экрана, а не к keyguard.**

Владелец различил два состояния: при погашенном экране и always-on не работает ни один жест, поэтому полоски там не нужны; на экране приглашения к разблокировке часть жестов работает, поэтому полоски остаются. Это отменяет прежнюю формулировку через keyguard.

**ADR-2. Классификация действий не вводится.**

Прежний замысел - скрывать полоску, если ни одно её действие не работает под блокировкой - снят владельцем. Он избыточен: в состоянии, где действия не работают, скрываются все полоски целиком, а в состоянии, где часть работает, полоски нужны.

### Quiz decisions (2026-07-24)

- Видны ли полоски поверх заблокированного экрана? -> Да, видны (подтверждено на устройстве; проблема реальна, тикет не снимается).
- Как классифицировать действия с неясным поведением под блокировкой? -> Классификация не нужна: при полной блокировке (погашенный экран, always-on) не работает ничего и полоски скрываются целиком, на экране разблокировки работает часть (фонарик) и полоски показываются.

---

## 10. Связи с другими спеками

- S1166 - иконки в диалоге выбора действия жеста; та же подсистема, пересечений по коду нет.

---

## 10.1 Реализация (2026-07-24)

Работа оказалась примитивной по мерке `/spec-tech` 2.5: один существующий файл, новых типов нет, тактический план не создавался.

- Всё изменение живёт в общем менеджере оверлея `ScreenGestureOverlayManager` (набор исходников `screenCapture`, его собирают и standard с включённым флагом, и noLegal). Хосты не тронуты вовсе: они уже зовут `show`/`hide`, а менеджер теперь сам решает, есть ли сейчас окна. Симметрия из §5.3 получается структурной, а не дисциплинарной - разойтись двум хостам больше негде.
- Менеджер запоминает запрошенный набор зон отдельно от живых окон, поэтому между погасанием и включением экрана запрос сохраняется, а окна отсутствуют.
- Приёмник на включение и погасание экрана регистрируется в `show` и снимается в `hide` - на той же паре, что и окна.
- Признак «экран освещён» читается из состояния дисплея, а не из самого события: always-on сообщает `STATE_DOZE`, и `ACTION_SCREEN_OFF` приходит по дороге в него, поэтому одного события мало. Запасной путь - интерактивность через `PowerManager`.
- Скрытие сделано снятием окон, а не прозрачностью: прозрачная полоска всё равно съедает касание и могла бы съесть свайп разблокировки, начатый от края (§3.2, доступность).

Юнит-тест на предикат из §7 не писался: после отмены классификации действий (ADR-2) предикат выродился в одно чтение состояния дисплея у системы - чистой функции, которую стоило бы закрепить тестом, не осталось. Его проверка перешла в устройственный сценарий §11.2.

---

## 11. Критерии готовности (strategic-level)

1. При погашенном экране полосок нет.
2. При включённом always-on полосок нет.
3. На экране приглашения к разблокировке полоски видны и жест по ним срабатывает.
4. На месте скрытой полоски нет активной зоны касания.
5. После разблокировки полоски возвращаются сами.
6. Поведение одинаково на обоих хостах оверлея.

---

## Last Audit

### Manual device test - 2026-07-26 (emulator-5554, standard debug)

Device: `sdk_gphone64_x86_64`, Android 15 (SDK 35), RAM 2G. Package `com.sza.fastmediasorter.debug`, build `2.60.7262.102-DEBUG` (rebuilt + reinstalled from working tree before testing). Setup: Settings > Management > Edge screen gestures - master toggle ON, LEFT_TOP zone enabled, Up action set to `Toggle flashlight`. Evidence: `temp/S1167/mobile_test_scenario_20260726_2205.md`, `temp/S1167/probe_lines_consolidated.txt`, `temp/S1167/dumpsys_*.txt`.

- Blank the panel, bands disappear: PASS. `input keyevent KEYCODE_POWER` -> `dumpsys power` `mWakefulness=Asleep`; `dumpsys window windows` shows no `screen_gesture_overlay_left_top` window; probe `S1167: android.intent.action.SCREEN_OFF handled, bands=0`. Expected: bands removed on full lock. Actual: matched.
- Always-on display keeps bands hidden: NOT EXERCISED. `settings get secure doze_always_on` -> `null`, and `settings list secure` has no `doze_always_on` key at all - this AVD image has no Always-on Display support at the framework level, not just disabled. Reported explicitly rather than guessed; not a spec defect.
- Wake to unlock prompt, bands back, flashlight still fires: PASS (bands + gesture). `dumpsys power` `mWakefulness=Awake`; `dumpsys window windows` shows `screen_gesture_overlay_left_top` again; probe `S1167: android.intent.action.SCREEN_ON handled, bands=1`. Flashlight: inward-up drag on the LEFT_TOP band (`input swipe 20 550 100 380 300`) -> `DeviceActionHandler: S1038: device action TOGGLE_FLASHLIGHT` and `CameraService: Torch for camera id 1 turned on`; repeated to toggle off (cleanup), confirmed by `Torch .. turned off`. Caveat: `dumpsys window policy` showed `KeyguardStateMonitor mIsShowing=false` - this AVD has no PIN/pattern configured, so there is no distinct keyguard "unlock prompt" chrome to inspect; the acceptance-relevant behaviour (bands return once the panel is lit, per ADR-1) was fully exercised regardless.
- Repeat once on the accessibility-hosted strip (noLegal): NOT EXERCISED in this pass. A flavor switch (separate build/install/permission/accessibility setup) was out of budget for this single sweep; not attempted rather than faked. `ScreenGestureOverlayManager` is shared verbatim between both overlay hosts per §10.1, so the mechanism is structurally identical, but this was not independently re-verified on-device for the accessibility host. **Closed in the follow-up pass below.**
- Probes: `S1167: overlay show requested` - 1 occurrence captured (`zones=1 screenOn=true` on cold relaunch with settings already persisted); `S1167: ACTION_SCREEN_ON|OFF handled, bands=N` - 2 occurrences (`SCREEN_OFF .. bands=0`, `SCREEN_ON .. bands=1`), matching the single enabled zone.

Side finding (parked, not part of this verdict): opening the gesture-action picker (Settings > Management > Edge screen gestures > Configure gestures > any direction row) froze the app's main thread solid twice in this session (real ANR, unrecoverable via repeated Wait). Unrelated to this subsystem; parked as `S1203` (Draft).

### Manual device test follow-up - 2026-07-26 22:44 (emulator-5554, noLegal debug, item 4)

Package `com.sza.fastmediasorter.debug`, build `2.60.7262.035-NoLegal-DEBUG` (pre-built APK, installed via `.\a.ps1 ivn`; confirmed via `dumpsys package`). Setup: accessibility service `ScreenshotAccessibilityService` enabled headlessly (`settings put secure enabled_accessibility_services ...`); edge-gesture master toggle enabled via the plain toggle row on `Settings > Management > Edge screen gestures` (not the `Configure gestures` dialog - S1203 avoided by design). LEFT_TOP zone is enabled by default and its `Up` slot carries the `SeedDefaultGestureBindingsUseCase` default (`OPEN_PANEL`), not `Toggle flashlight` - reconfiguring it would require the same ANR-prone picker, so this pass verifies gesture-fire via `OPEN_PANEL` instead, functionally equivalent for this check. Full evidence: `temp/S1167/mobile_test_scenario_20260726_2205.md` (item 4 section).

- 4a - blank the panel: PASS. `input keyevent KEYCODE_POWER` -> `dumpsys power` `mWakefulness=Asleep`; `dumpsys window windows` shows no `screen_gesture_overlay_*` window; probe `S1167: android.intent.action.SCREEN_OFF handled, bands=0`.
- 4b - wake, bands back, gesture fires: PASS. `dumpsys power` `mWakefulness=Awake`; `dumpsys window windows` shows `screen_gesture_overlay_left_top` again with `ty=ACCESSIBILITY_OVERLAY appop=CREATE_ACCESSIBILITY_OVERLAY` (confirms the accessibility host, not the application-overlay host); probe `S1167: android.intent.action.SCREEN_ON handled, bands=1`. Gesture: inward-up drag on the LEFT_TOP band (`input swipe 20 550 100 380 300`) -> `ActivityTaskManager: START ... AppLaunchPanelActivity`, confirmed as `topResumedActivity` - the bound action dispatched from the accessibility-hosted band.
- Self-inflicted incident (disclosed, no effect on the verdict): `adb.ps1 clear` was run intending a logcat-buffer clear but actually performs `pm clear`, wiping app data and revoking the accessibility grant mid-sweep. Recovered by re-enabling the service and clicking through the resulting onboarding/permission cascade; no source, spec, or git action involved. All measurements above are post-recovery.

Overall (updated): 4 of 5 sweep items now PASS (bands hidden on full lock; bands restored + a bound gesture fires on both the standard `TYPE_APPLICATION_OVERLAY` host with flashlight, and the noLegal `TYPE_ACCESSIBILITY_OVERLAY` host with OPEN_PANEL). AOD (item 2) remains NOT EXERCISED - this AVD image has no Always-on Display support at the framework level; no AVD substitute exists. Spec remains `BlockNeedUserTest` pending a real-device pass covering AOD specifically.

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 892 times, including the exact line the status note demands: `S1167: android.intent.action.SCREEN_OFF handled, bands=0` (441 times), paired with `SCREEN_ON handled, bands=4` (442).
- Not covered: the status note also requires that no band window remains in `dumpsys window`, and that the panel be blanked with Always-on Display ENABLED. Neither is establishable from the log, and nothing in the bundle shows AOD was on. The AOD leg remains open.
