# Спецификация (compact bugfix): S1107 - Онбординг: выбор «запускать как домашний экран» не делает приложение лаунчером

**Ticket:** S1107
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-18
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-18

**Текст:**

во время инсталяции выбрал "запускать как домашний экран", но потом попал в настройки - это ок, но из настроек вышел в обычное приложение. А по логике - должно уже быть лаунчером

---

## 1. Проблема / симптом

В онбординге пользователь выбрал опцию «запускать как домашний экран» (запрос роли HOME/лаунчера). После выбора его перекинуло в системные настройки - это ожидаемо (выбор роли лаунчера идёт через системный экран). Но при выходе из настроек приложение вернулось в обычный режим, а не стало активным лаунчером - хотя по логике роль HOME на этот момент должна быть уже назначена.

**Где наблюдалось:**
- Экран: онбординг (WelcomeActivity), опция «запускать как домашний экран».
- Flavor/устройство: <уточнить при воспроизведении>.

**Эвиденс:** пока только словесный отчёт владельца (см. §0). On-device лог/repro - собрать при расследовании.

---

## 2. Корневая причина

**Расследовано 2026-07-18 (по коду).** Роль HOME в онбординге не запрашивается вообще - приложение лишь делается кандидатом.

Цепочка:
1. Тумблер «запускать как домашний экран» на первой странице Welcome ставит только флаг намерения: `viewModel.setLauncherModeRequested(true)` (WelcomeActivity.kt:253-255).
2. На завершении онбординга `completeWelcomeFlow()` при `launcherModeRequested` вызывает **только** `launcherRoleManager.markAsHomeCandidate()` (WelcomeActivity.kt:527-530). Этот метод (LauncherRoleManager.kt:83-86) включает HOME-компонент и НЕ запускает ни диалог роли, ни системный экран выбора - по решению ADR-2 (LauncherRoleManager.kt:77-82, аудит 2026-07-17): диалог роли, запущенный из завершающегося кадра онбординга, погребается под стеком MainActivity+SettingsActivity.
3. ADR-2 рассчитывает, что «системный chooser „Сделать домашним" всплывёт при следующем нажатии Home». **Это неверно, когда на устройстве уже есть лаунчер по умолчанию** (обычный случай на любом реальном устройстве - штатный лаунчер уже дефолтный): нажатие Home просто возвращает в штатный лаунчер, chooser не появляется, и у пользователя нет рабочего пути стать лаунчером. Приложение остаётся лишь кандидатом (`isModeEnabled()==true`, но `isHomeRoleHeld()==false`).

Что владелец принял за «экран запроса роли» - это штатная навигация первого запуска в SettingsActivity (WelcomeActivity.kt:544-550, `goToMainActivity()` первого запуска строит стек MainActivity+SettingsActivity и показывает тост). Она срабатывает на любом первом запуске независимо от launcher-режима. По выходу из настроек -> MainActivity (обычное приложение). Роль так и не назначена.

Итог: кандидат №2 из исходного черновика (роль запрашивается, но не подтверждается) неверен - роль **не запрашивается**. Верна комбинация №1+№4: намерение записано, но пользователя не приводят к рабочей точке назначения роли, а допущение ADR-2 про «chooser при нажатии Home» не выполняется при наличии дефолтного лаунчера.

---

## 3. Исправление

Общий принцип для всех вариантов: диалог роли нельзя запускать из завершающегося кадра онбординга (ограничение ADR-2). Значит намерение `launcherModeRequested` надо донести до **не завершающегося** контекста (SettingsActivity первого запуска или MainActivity), где рабочий путь запроса роли уже есть (тот же `enableMode(host, roleLauncher)`, что у тумблера в настройках).

Варианты (нужно решение владельца - см. §3.3):

- **A - минимальный, deep-link (низкий риск, в рамках ADR-2).** При `launcherModeRequested` и первом запуске нацелить штатную навигацию в SettingsActivity на секцию лаунчера (`EXTRA_INITIAL_TAB=TAB_OPERATIONS`, `EXTRA_EXPAND_SECTION=SECTION_LAUNCHER_MODE` - инфраструктура уже есть, SettingsActivity.kt:158-166). Пользователь оказывается прямо на тумблере «включить» и «открыть системные настройки Home» и завершает назначение сам. Не реверсит ADR-2 (диалог не из онбординга). Но всё ещё требует ручного действия - не полностью совпадает с ожиданием «уже должен быть лаунчером».
- **B - отложенный запрос роли (ближе всего к ожиданию, рекомендуемый).** Донести намерение до SettingsActivity/MainActivity и оттуда авто-запустить диалог роли `enableMode(host, roleLauncher)` (не завершающийся контекст - как у рабочего тумблера настроек). По возврату перечитать `isModeEnabled()`/`isHomeRoleHeld()` и дать фидбэк. Амендит стратегию ADR-2 («chooser при нажатии Home» -> «активный запрос из Settings»), но не нарушает его ядро (диалог не из завершающегося кадра).
- **C - объяснялка + кнопка.** Явный экран/карточка «Чтобы завершить, выберите FastMediaSorter как приложение „Home"» с кнопкой `openHomeChooser()`. Максимально прозрачно, но добавляет UI-поверхность в онбординг.

**Рекомендация:** B как основной (совпадает с ожиданием владельца, переиспользует рабочий путь настроек), A как быстрый низкорисковый fallback, если B решат отложить.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (родительский epic launcher-mode; ADR-2 - его решение, owner-gated). Онбординг-тумблер трогает область S1104 (launcher-welcome-toggle-layout). Пользовательская документация режима - S1102 (launcher-mode-user-docs).
- **Owner decision (блокирует Draft -> Approved):** выбрать вариант исправления A / B / C (рекомендация B). Решение амендит ADR-2 внутри owner-gated epic S0404, поэтому требует явного согласия владельца, а не автономного реверса. Готово к `/spec-quiz` - вопрос одноходовый.
- **UI-поверхность:** варианты B/C добавляют фидбэк/экран в онбординг - согласовать размещение и текст перед реализацией (UI Ambiguity Gate).

### Quiz decisions (2026-07-18)
- Выбран вариант B: отложенный запрос роли HOME из Settings/Main (переиспользует рабочий путь тумблера настроек). Амендит стратегию ADR-2 «chooser при нажатии Home» -> «активный запрос из Settings»; ядро ADR-2 (диалог не из завершающегося кадра онбординга) сохранено (S1107-1: B).
- Вариант B добавляет фидбэк в онбординг/Settings - разместить и согласовать текст на UI Ambiguity Gate до реализации.

### Реализация (2026-07-26) - попытка 6, вердикт читается из состояния, а не из колбэка

RE-TEST #5 показал ровно одну оставшуюся дыру: `clearRoleRequestPending()` висел на `ActivityResult`-колбэке `GeneralSettingsFragment`, а диалог роли перестраивает задачу и уносит фрагмент раньше, чем результат дойдёт. Отказ пользователя не гасил запрос.

Гашение перенесено в `LauncherRoleManager.isRoleRequestPending()` и опирается только на устойчивое состояние:

- Роль удерживается -> согласие, флаг снимается (было и раньше).
- Запрос уже был выпущен (`attempts > 0`), а роль не наша -> отказ, флаг снимается. Больше не спрашиваем.
- Бюджет - один выпущенный запрос. Прежние три попытки существовали ради запроса, умиравшего до появления диалога; дебаунс `STORM_SETTLE_DELAY_MS` эту причину уже закрыл (подтверждено RE-TEST #5), а с этой стороны повтор неотличим от повторного вопроса тому, кто отказался. Константа `MAX_ROLE_REQUEST_ATTEMPTS` удалена как мёртвая.

Колбэк оставлен как быстрый путь: когда результат всё-таки доходит, флаг гаснет сразу, а не на следующем открытии настроек. Гарантией он больше не является, и KDoc это фиксирует.

**Проверяемость на эмуляторе.** В отличие от головного дефекта, эта половина проверяется без диалога роли: посеять `launcher_role_prefs.xml` (`pending=true`, `attempts=1`), открыть настройки и убедиться, что флаг снят и `RequestRoleActivity` не запускался.

### Реализация (2026-07-25) - попытка 5, устойчивый флаг вместо intent-extra

Четыре прогона на устройстве дали четыре разные точки потери, и все четыре - в одном и том же месте архитектуры: намерение пользователя ехало в `Intent`-экстре активити, которую первый запуск пересоздаёт по ходу применения темы и языка. Кто бы ни съел экстру, дальше её нет. Дебаунс на 600 мс лечил симптом «стреляет обречённый инстанс», но не отвечал на вопрос, доживёт ли сам сигнал.

Сигнал перенесён в `LauncherRoleManager` как долговременный флаг (`markRoleRequestPending` / `isRoleRequestPending` / `recordRoleRequestAttempt` / `clearRoleRequestPending`). Следствия:

- Пересоздание активити, смерть процесса и выход пользователя из настроек с возвратом позже больше не теряют опцию.
- Флаг гасится **вердиктом диалога**, а не фактом его запуска. Попытка, умершая до появления диалога, повторится на следующем устойчивом экране, а не исчезнет молча.
- Повтор ограничен тремя попытками: на устройствах, где диалог роли не появляется вообще (часть эмуляторов), бесконечный повтор на каждом `onResume` был бы хуже исходного бага.
- Отказ пользователя тоже считается вердиктом - повторно не спрашиваем.
- Дебаунс 600 мс сохранён: он решает вторую, отдельную задачу - не дать выстрелить инстансу, который система разрушит до того, как прочтёт имя вызывающего пакета.

Удалены как мёртвые: `SettingsActivity.EXTRA_REQUEST_LAUNCHER_ROLE` и параметр `requestRole` у `openLauncherSectionIntent` - экстру больше никто не читает.

**Проверяемость.** По наблюдению 2026-07-24 диалог роли HOME на AVD не появляется в принципе (ни одной строки `RequestRole`), поэтому подтвердить исправление можно только на реальном аппарате. Эмулятор здесь даёт ложный отрицательный результат.

### Реализация (2026-07-20)
- Вариант B доработан против caller-null гонки re-test #3: запрос роли из первого запуска Settings теперь откладывается на view-lifecycle дебаунс (600 мс) в `GeneralSettingsLauncherHelper.handleLauncherRoleDeepLink()`. Обречённый штормом инстанс отменяет свой viewLifecycleScope и не запускает `enableMode()`; срабатывает только устоявшийся выживший инстанс, который переживает резолвинг caller в RequestRoleActivity. Extra потребляется лишь в момент фактического запуска - пересоздаваемые инстансы перевзводят запрос, пока один не победит.
- Корневой шторм пересоздания SettingsActivity (onCreate/onDestroy x3) не устраняется здесь (это обход) - вынесен отдельным тикетом **S1136** (bugfix-first-run-recreation-storm).
- Статус: BlockNeedUserTest - нужен on-device прогон онбординг-пути (диалог роли HOME должен ПРЕЗЕНТОВАТЬСЯ без null-package bail на устройстве с дефолтным лаунчером).

---

## 4. Проверка

Финальные предикаты зависят от выбранного варианта (§3), но repro и негатив-кейс общие. Важно: устройство/эмулятор должно иметь **дефолтный лаунчер** (штатный) - именно этот случай и ломается; на «голой» системе без дефолта баг не воспроизведётся.

- Repro (текущий баг): чистая установка -> онбординг -> «запускать как домашний экран» -> дойти до конца -> выйти из настроек первого запуска -> нажать Home. Ожидаемо сейчас: остаётся штатный лаунчер, приложение НЕ стало Home (баг подтверждён).
- После фикса (вариант B/A): пользователь получает рабочий путь назначить роль (диалог роли из Settings / deep-link на секцию лаунчера); по назначении `isHomeRoleHeld()==true` и нажатие Home открывает приложение как лаунчер.
- Негатив-кейс: пользователь вышел, не назначив роль, -> понятный фидбэк/состояние (не молчаливый откат в обычный режим).
- Матрица API: RoleManager доступен с API 29 (диалог роли); ниже - системный экран Home settings. Проверить оба пути (`createRoleRequestIntent` != null на API 29+, иначе `openHomeChooser`).

---

## Last Audit

### RE-TEST #6 - 2026-07-26, emulator-5554 (Android 15 / SDK 35), standard-debug v2.60.7220.314-DEBUG

**Вердикт: VERIFIED.** Оставшаяся половина - гашение запроса при отказе - исправлена и проверена двумя встречными прогонами. Эмулятор держал штатный лаунчер (`nexuslauncher`) по умолчанию, то есть условие воспроизведения соблюдено.

**PASS - отказ гасит запрос.** Посеяно состояние отказавшегося пользователя (`pending=true`, `attempts=1`), приложение перезапущено, открыты настройки, вкладка «Общие»:

```text
D/LauncherRoleManager: S1107: onboarding role request settled by state - dropping pending flag
prefs после: <map />
RequestRoleActivity: не запускался
```

Стек подтверждает исполнение нового пути: `LauncherRoleManager.isRoleRequestPending(:124-125)` <- `GeneralSettingsLauncherHelper.handleLauncherRoleDeepLink(:87)`. Повторного вопроса нет - контракт «отказался - больше не спрашиваем» выполняется.

**PASS - свежий запрос не проглатывается.** Встречный прогон с `attempts=0`: probe гашения НЕ сработал, диалог роли поднялся и отрисовался:

```text
Displayed com.google.android.permissioncontroller/..RequestRoleActivity for user 0: +305ms
RequestRoleFragment: requestingUid=10210 requestingPackageName=com.sza.fastmediasorter.debug
                     roleName=android.app.role.HOME result=6
```

То есть гашение по состоянию не съедает первый, законный запрос, и гонка caller-lifetime (null-пакет, убивавшая попытки 1 и 3) здесь тоже не воспроизводится.

**Побочная находка - оценка проверяемости устарела.** Замечание попытки 5 «диалог роли HOME на AVD не появляется в принципе, подтвердить можно только на реальном аппарате» неверно для этого образа: на Android 15 диалог поднимается за ~305 мс с корректной атрибуцией вызывающего пакета. Будущие прогоны этого тикета эмулятором закрываются.

Артефакты: `temp/S1107/retest6_summary.md`, `temp/S1107/retest6_positive_control.log`, харнесс `temp/S1107/seed_role_prefs.ps1` + `run_probe.ps1`.

### RE-TEST #5 - 2026-07-26, РЕАЛЬНЫЙ аппарат SM-G781B (Galaxy S20 FE), Android 13 / SDK 33, standard-debug v2.60.7252.333

**Вердикт: PARTIAL.** Головной дефект, переживший четыре попытки, исправлен и подтверждён. Сломанной осталась вторая половина - обработка отказа.

Условия соответствуют требуемым: чистая установка (`pm clear`), разрешения выданы заранее, **штатный лаунчер Samsung One UI Home был назначен по умолчанию** - то самое условие, при котором баг воспроизводился. Проход: русский язык + тёмная тема (смена обеих корзин) -> тумблер «Использовать как домашний экран» ON -> шесть страниц -> «Готово».

**PASS - диалог роли появляется.** Впервые за пять попыток:

```text
00:03:50  S1136: onWelcomeThemeSelected mode=DARK nightMode -1->2
00:06:43  S1136: goToMainActivity requestLauncherRole=true
00:06:45  S1107: pending HOME-role request fires from settled Settings instance
00:06:45  RequestRoleActivity -> в фокусе, отрисован, кнопки «Cancel» / «Set as default»
```

- Строки `Package name cannot be null or empty: null` в захвате **нет** - на ней падали попытки 1 и 3.
- `SettingsActivity.onCreate` ровно **один раз**, а не три: шторм пересоздания не воспроизводится (это же закрывает S1136 на реальном аппарате).
- Стек StrictMode подтверждает исполнение именно нового пути: `GeneralSettingsLauncherHelper$handleLauncherRoleDeepLink$1.invokeSuspend(:96)` -> `revealEnableToggle(:104)`.

**PASS - согласие доводит роль до конца.** Проверено непреднамеренно и потому убедительно: касание попало в «Set as default» вместо «Cancel» (диалог доанимировался между снятием координат и нажатием), и приложение действительно стало держателем роли - `cmd role get-role-holders android.app.role.HOME` -> `com.sza.fastmediasorter.debug`, `get-default-launcher` -> `LauncherHomeActivity`. То есть цепочка «онбординг -> отложенный запрос -> диалог -> назначение роли» работает целиком. Состояние аппарата восстановлено сразу: роль возвращена `com.sec.android.app.launcher`, нажатие Home открывает Samsung One UI Home.

**FAIL - отказ не гасит запрос.** После возврата из диалога флаг остался взведённым:

```text
launcher_role_prefs.xml:
  onboarding_role_request_pending = true
  onboarding_role_request_attempts = 1
```

`clearRoleRequestPending()` из колбэка `launcherRoleLauncher` не отработал. Причина - того же класса, что и исходный баг, и это главный урок прогона: **починив долговечность сигнала, я оставил его гашение на носителе, который так же не переживает пересоздание.** Экран настроек уходит из-под ног, пока диалог сверху (после назначения роли задача перестраивается), и `ActivityResult`-колбэк до фрагмента не доходит.

Последствия ограничены, но контракт нарушен:

- Пользователь, отказавшийся от роли, будет спрошен повторно при следующем заходе в настройки - до трёх раз. Предохранитель `MAX_ROLE_REQUEST_ATTEMPTS` отработал как задумано и превратил потенциально бесконечный цикл в ограниченный, но «отказался - больше не спрашиваем» не выполняется.
- На пути согласия дефекта нет: `isRoleRequestPending()` проверяет `isHomeRoleHeld()` и гасит флаг сам, поэтому получивший роль пользователь повторного запроса не увидит.

**Что чинить:** гашение не должно зависеть от колбэка. Опрос состояния при следующем показе экрана настроек (попытка была, роль не получена, экран открыт заново -> считать отказом) не зависит ни от одного объекта с жизненным циклом. Артефакты прогона: `temp/S1107/`.

## Last Audit (предыдущие)

**Manual / on-device (RE-TEST #4 after 600ms view-lifecycle debounce fix)** - `/spec-test-device` (claude-opus-4-8), 2026-07-24, emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35), standard-debug v2.60.7220.314-DEBUG (Build 260722031). Clean install (app data cleared); default launcher present = nexuslauncher (the failing precondition). Supersedes re-test #3 below.

Verdict: **FAIL** (via onboarding path) - the failure MOVED AGAIN. The debounce fix silences the doomed instances but the settled survivor never issues the request either. From the full onboarding storm (opt-in ON -> 6-page walk -> Finish @ 00:44), `markAsHomeCandidate()` ran (LauncherHomeActivity ENABLED) and the first-run SettingsActivity storm fired (3 instances: onCreate 00:44:43.806 / 44.626 / 45.226; survivor #3 ready 45.539, still resumed at 00:46:21). BUT: the `S1107:` D-probe (GeneralSettingsLauncherHelper.kt:98) NEVER fires from any instance; RequestRoleActivity is NEVER launched (grep across all pids = 0, unlike re-test #3 which launched it then bailed null-caller); the Interface section is NOT auto-expanded (revealEnableToggle never ran); HOME role holder stays nexuslauncher. Survivor #3's GeneralSettingsFragment is resumed and rendered, so onResume ran and called `handleLauncherRoleDeepLink()`, yet no probe -> the `EXTRA_REQUEST_LAUNCHER_ROLE` check (line 92) evidently returned false on the surviving instance (the deep-link extra was lost/consumed across the config-change storm before the settled instance could act), so the deferred request is dropped entirely. Negative case reproduces the exact original symptom: silent fallback to plain Settings, no dialog, no feedback. No app crash. Evidence: temp/S1107/mobile_test_scenario_20260724_0044.md, temp/S1107/run_20260724_retest4.log.

**Manual / on-device (RE-TEST #3 after onResume-SYNCHRONOUS fix)** - `/spec-test-device` (claude-opus-4-8), 2026-07-18, emulator-5554 (Pixel 4, Android 17 emulator), standard-debug v2.60.7181.927 (code 260718192). Emulator had a default launcher (nexuslauncher/Pixel Launcher) - the failing precondition. Supersedes the v..857 run below.

Verdict: **FAIL** (via onboarding path) - net still broken, but the failure point MOVED. The synchronous onResume consumption fixed attempt 2's "handler never fires": from the full onboarding storm the `S1107:` D-probe NOW fires (19:32:30.663) and the launcher section auto-expands. BUT the HOME role dialog still does NOT present - RequestRoleActivity launches from the app uid (19:32:30.672) then bails with `Package name cannot be null or empty: null` (19:32:30.849, result=1). `requestingPackageName=null` RECURS (same as attempt 1). Root cause is a caller-lifetime race, NOT consumption timing: the first-run recreation storm destroys the launching SettingsActivity instance (19:32:30.758, 86ms after launch) before RequestRoleActivity resolves its caller (91ms later) -> null. Attempt 3's premise ("resumed at launch => correct attribution") is disproven; being resumed is insufficient when the caller is torn down mid-resolution. Direct-launch isolation still works only because it has no storm. Evidence: temp/S1107/retest3_evidence.txt, temp/S1107/mobile_test_scenario_20260718_1927.md.

### Manual / on-device
- [x] Opt-in "use as home screen" on first Welcome page - verified on-device 2026-07-24 (re-test #4; toggle ON, LauncherHomeActivity ENABLED in enabledComponents)
- [!] Complete onboarding -> land on first-run Settings General tab, launcher section auto-expanded - failed on-device 2026-07-24 (re-test #4; Settings on General tab but ALL sections collapsed, Interface NOT expanded - revealEnableToggle never ran; PASSED in re-test #3, now regressed); see temp/S1107/mobile_test_scenario_20260724_0044.md
- [!] Deep-link consumed -> `S1107:` probe fires (D-level) - failed on-device 2026-07-24 (re-test #4; probe ABSENT from all instances - deferred request never fired; PASSED in re-test #3, now regressed); see temp/S1107/mobile_test_scenario_20260724_0044.md
- [!] Auto-requested HOME role dialog presents (request NOT silently dropped) - failed on-device 2026-07-24 (re-test #4) via onboarding; RequestRoleActivity NEVER launched (request never issued from the settled survivor); see temp/S1107/mobile_test_scenario_20260724_0044.md
- [ ] After grant, launcher enable toggle reflects held role - not reached (dialog never presented; HOME role holder still nexuslauncher)

**Evidence / root cause (re-test #3):**
- Onboarding (storm scenario): opt-in "use as home" ON -> 6-page walk -> Finish @ 19:32:24 -> first-run SettingsActivity storm (onCreate x3 / onDestroy x2, 19:32:30.074 .. 19:32:31.851).
- (a) `S1107:` D-probe FIRES from onboarding: 19:32:30.663 `D/GeneralSettingsLauncherHelper: S1107: onboarding opt-in -> auto-request HOME role from Settings`. Fixes attempt 2's "handler never fires".
- (b) Dialog does NOT present: RequestRoleActivity START 19:32:30.672 from uid 10233, bail 19:32:30.849 `Package name cannot be null or empty: null`, result=1.
- (c) `requestingPackageName=null` RECURS (identical to attempt 1).
- (d) Storm no longer drops the handler, but destroys the launching instance #1 at 19:32:30.758 (86ms after launch, 91ms before RequestRoleActivity resolves the caller) -> caller-identity race -> null package.
- Opt-in genuinely captured (not a test artifact): `com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity` ENABLED in `enabledComponents` -> `markAsHomeCandidate()` ran -> `completeWelcomeFlow()` took the `requestLauncherRole=true` branch.
- Fix direction: launch the role request from a storm-surviving context, not the first (doomed) instance. Options: defer `enableMode` to the final settled instance (debounce past the storm, re-arm the extra across instances instead of consume-once); own the auto-request at MainActivity (outlives RequestRoleActivity resolution); or eliminate the first-run SettingsActivity recreation storm at source (first-run locale/theme re-application). Direct-launch isolation still presents correctly (no storm).
- No app crash/exception in the run window.

## Revision History
- **2026-07-24** - by `/spec-test-device` (`claude-opus-4-8`, device: emulator-5554 Android 15) - RE-TEST #4 after 600ms view-lifecycle debounce fix (v2.60.7220.314)
  - Scenario: temp/S1107/mobile_test_scenario_20260724_0044.md · Manual checklist PASS/FAIL/SKIPPED 1/3/0 (After-grant not reached) · App errors in log: 0 · Failure moved again: the debounce silences the doomed instances but the settled survivor never issues the request - `S1107:` probe ABSENT, RequestRoleActivity NEVER launched, Interface section not expanded, HOME role stays nexuslauncher. Deep-link extra evidently lost across the config-change storm before survivor #3 acts. Recommend MainActivity-owned request or block on S1136 (storm at source).
- **2026-07-18** - by `/spec-test-device` (`claude-opus-4-8`, device: emulator-5554 Android 17) - RE-TEST #3 after onResume-SYNCHRONOUS fix (v..927)
  - Scenario: temp/S1107/mobile_test_scenario_20260718_1927.md · Manual checklist PASS/FAIL/SKIPPED 3/1/1 · App errors in log: 0 · Handler now fires from onboarding (fixes attempt 2), but HOME role dialog still fails: `requestingPackageName=null` recurs - caller-lifetime race, the first-run storm destroys the launching SettingsActivity instance before RequestRoleActivity resolves the caller.
- **2026-07-18** - by `/spec-test-device` (`claude-opus-4-8`, device: emulator-5554 Android 17) - RE-TEST after onResume/post fix (v..857)
  - Scenario: temp/S1107/mobile_test_scenario_20260718_1858.md · Manual checklist PASS/FAIL/SKIPPED 1/3/1 · App errors in log: 0 · Null-package bail FIXED (dialog presents in direct-launch isolation, correct attribution), but onboarding path regressed to "handler never fires" - first-run SettingsActivity recreation storm drops the deferred `view.post` before it consumes `EXTRA_REQUEST_LAUNCHER_ROLE`.
- **2026-07-18** - by `/spec-test-device` (`claude-opus-4-8`, device: emulator-5554 Android 17)
  - Scenario: temp/S1107/mobile_test_scenario_20260718_1836.md · Manual checklist PASS/FAIL/SKIPPED 3/1/1 · App errors in log: 0 · HOME role dialog fails to present on the auto-request (null requesting package); same path works from the resumed Settings toggle.
