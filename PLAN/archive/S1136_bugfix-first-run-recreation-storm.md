# Спецификация (compact bugfix): S1136 - Первый запуск: шторм пересоздания SettingsActivity при завершении онбординга

**Ticket:** S1136
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-20
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20

**Захвачено во время:** S1107 (bugfix-onboarding-launcher-not-applied)

**Текст:**

First-run SettingsActivity recreation storm (onCreate/onDestroy x3) during onboarding completion. Root cause behind S1107: completing onboarding rebuilds MainActivity+SettingsActivity while first-run theme/locale re-application recreates the top Activity several times within ~1.8s. Beyond the S1107 HOME-role caller-null race (now worked around with a debounce), this causes visible first-run jank and is a latent hazard for any launch/recreate race. Investigate the storm source (likely first-run locale/theme setDefaultNightMode + LocaleHelper.applyLocale re-application) and eliminate it so first-run settles in a single onCreate. Evidence: temp/S1107/retest3_evidence.txt (storm window 19:32:30.074..19:32:31.851, emulator-5554). Related: S1107, S0404.

**Вложения:**
- Лог-эвиденс шторма из re-test #3 S1107 (окно 19:32:30.074..19:32:31.851, emulator-5554) - `PLAN/S1136_bugfix-first-run-recreation-storm/attachments/01__retest3-storm-evidence.txt`

---

## 1. Проблема / симптом

При завершении онбординга (первый запуск) стек MainActivity+SettingsActivity перестраивается, и одновременно первичное применение темы/локали пересоздаёт верхнюю Activity несколько раз (onCreate/onDestroy x3) в окне ~1.8с. Наблюдаемо: видимый джанк первого запуска и гонка жизненного цикла - именно она в S1107 сносила запускающий инстанс SettingsActivity до того, как системная RequestRoleActivity резолвила caller (requestingPackageName=null). В S1107 это обойдено дебаунсом; сам шторм остаётся.

Эвиденс: см. §0 вложение (окно шторма 19:32:30.074..19:32:31.851, emulator-5554, Pixel 4 Android 17 emulator).

---

## 2. Корневая причина

Исследование кода (device-free, автономно) выявило несколько источников `recreate()`,
которые на завершении онбординга складываются в каскад:

- `WelcomeActivity` вызывает `recreate()` при смене локали на API < 33
  (`LocaleManager` недоступен - ручной recreate + `overridePendingTransition(0,0)`).
- `WelcomeActivity` вызывает `recreate()` при применении цветовой темы
  (`ColorThemePrefs.applyMode(mode); recreate()` в обработчике выбора темы).
- `BaseActivity.attachBaseContext` применяет `LocaleHelper.applyLocale(newBase)` на
  каждом создании Activity; если сохранённая локаль отличается от локали процесса на
  первом запуске, первичное присоединение даёт конфиг-mismatch.
- Завершение онбординга перестраивает стек MainActivity + SettingsActivity.

Наложение этих событий в окне ~1.8с (onCreate/onDestroy x3) - шторм. Точную
последовательность в конкретном прогоне даёт эвиденс из §0
(`retest3-storm-evidence.txt`, окно 19:32:30.074..19:32:31.851).

Что подтверждено чтением кода (device-free):

- Двойной recreate в `onWelcomeThemeSelected` (WelcomeActivity.kt:494-502):
  `ColorThemePrefs.applyMode(mode)` вызывает `AppCompatDelegate.setDefaultNightMode()`,
  который сам пересоздаёт запущенную Activity при смене night-mode-константы; идущий
  следом явный `recreate()` (строка 501) - второй пересоздающий проход. При смене
  только акцента (та же night-mode-константа) setDefaultNightMode НЕ пересоздаёт, и
  явный recreate - единственный и необходимый (переприменяет ThemeOverlay из
  `BaseActivity.onCreate`:131). Значит убрать явный recreate можно только условно.
- Night-mode на старте процесса применяется синхронно ДО первой Activity:
  `FastMediaSorterApp.onCreate`:198 -> `ColorThemePrefs.applySavedMode()` (S0328).
  Отложенный `applyMode` (:326, после firstFrame) переприменяет то же нормализованное
  значение - в чистом первом запуске это no-op. Поэтому первая Activity инфлейтится с
  верным night-mode; night-mode-mismatch не является драйвером шторма на чистой установке.
- Завершение (`goToMainActivity`:523-553) в first-run-ветке строит TaskStackBuilder
  MainActivity + SettingsActivity и `finish()` - два свежих onCreate одним всплеском.

Что требует устройства (не выводится из статики): какой именно recreate попадает в уже
созданную SettingsActivity после её создания - тот, что в S1107 давал
requestingPackageName=null. Точную причинно-следственную цепочку в окне
19:32:30.074..19:32:31.851 нужно сверить с эвиденс-логом на устройстве.

---

## 3. Исправление

Цель: первый запуск устаканивается за один `onCreate` верхней Activity без каскада.
Исполнять на подключённом устройстве, итеративно сверяясь с эвиденс-логом (§4) -
правка лежит на startup/first-run-пути с широким blast-radius, единственная
верификация - наблюдение отсутствия каскада recreate на чистой установке. Слепая
правка порядка применения темы/локали рискованна и не проверяема; ниже - готовый к
применению план по фазам, чтобы device-сессия была быстрой и безопасной.

### Фаза A - убрать лишний recreate в выборе темы (низкий риск)

Сделать явный `recreate()` в `onWelcomeThemeSelected` (WelcomeActivity.kt:501)
условным: пересоздавать вручную только когда night-mode-константа не меняется (случай
смены только акцента, где setDefaultNightMode не пересоздаёт сам).

- Шаг: считать `previousNightMode = AppCompatDelegate.getDefaultNightMode()` до
  `applyMode`; вычислить `newNightMode = ColorThemePrefs.toNightMode(mode)`; вызвать
  явный `recreate()` только при `newNightMode == previousNightMode`.
- Проверка (устройство): выбор темы light<->dark в Welcome даёт ровно один recreate;
  выбор темы только-акцент по-прежнему переприменяет ThemeOverlay за один recreate; нет
  мерцания первого кадра и потери акцента.

### Фаза B - устранить recreate самой SettingsActivity (ядро, evidence-driven)

По эвиденс-логу определить, пересоздаётся ли first-run SettingsActivity после создания и
что его триггерит (кандидаты: отложенный `applyMode` с расхождением mirror/DataStore на
первом запуске; повторное применение локали; смена конфигурации). Устранить триггер так,
чтобы first-run SettingsActivity устаканивалась за один `onCreate`.

- Проверка (устройство): в окне завершения онбординга SettingsActivity даёт один
  onCreate без немедленной пары onDestroy/onCreate; роль-запрашивающий инстанс не
  пересоздаётся до резолва RequestRoleActivity (S1107 не регрессирует,
  requestingPackageName != null).

### Фаза C - локаль на завершении (только если эвиденс уличит)

Если лог показывает, что recreate локали (API < 33 в `onWelcomeLanguageSelected`:487 или
переприменение при финальном переходе) складывается во всплеск завершения - отложить
применение локали на единственный финальный переход в Main вместо отдельного recreate.
Если эвиденс не уличает - фазу не трогать.

- Проверка (устройство): смена языка в Welcome не добавляет лишний recreate в окно
  завершения; выбранный язык применён на первом кадре Main/Settings.

Порядок исполнения: A (дёшево, безопасно) -> собрать эвиденс -> B (ядро) -> C по
необходимости. После каждой фазы: `a.ps1 dq`, переустановка debug, чистая установка +
онбординг + Finish, чтение logcat по `onCreate:`/`onDestroy:` (BaseActivity.kt:123
логирует `onCreate: <name>`) для Welcome/Main/Settings, сверка с baseline-окном §0.

Debug-теги (CLAUDE.md "Debug Verification Tags"): при переходе тикета в
`BlockNeedUserTest` в device-сессии вставить по одному `Timber.d("S1136: <точка входа>")`
в изменённые точки (обработчик темы/локали, `goToMainActivity`) как последние правки
перед финальной сборкой; удалить их при выходе из `BlockNeedUserTest`.

**Фаза A уже в коде (2026-07-24).** `onWelcomeThemeSelected` (WelcomeActivity.kt:495-508)
несёт условный recreate: явный `recreate()` вызывается только при
`newNightMode == previousNightMode` (случай смены только акцента), иначе полагается на
пересоздание от `setDefaultNightMode`. Двойного recreate из §2 в текущем дереве нет.

### Device evidence (2026-07-24, emulator-5554, Android 15 / API 35, build v2.60.7220.314)

Чистая установка (`pm clear`) + онбординг (язык Русский, тема Тёмная, опт-ин лаунчера ON)
+ Finish, непрерывный logcat (`temp/S1136/run5_clean.log`).

Окно завершения:

- `onCreate SettingsActivity` #1 в 01:19:43.431, `Displayed` в 01:19:44.915, инстанс жив и
  показан до `onDestroy` в 01:19:53.212 (~10с). **Ровно один onCreate без немедленной пары
  onDestroy/onCreate** - PASS по основному критерию §4.
- Вторая SettingsActivity (`onCreate` 01:19:50.371) - от ОТДЕЛЬНОГО второго START
  (`act=MAIN cat=LAUNCHER flg=0x14000000`, launcher-home путь), в ~7с от первого, а не
  тайтовый каскад <2с. Это не шторм-recreate одного инстанса.

Тайтовый шторм S1107 (инстанс #1 уничтожался через 86мс, 3 инстанса за 1.8с) на текущей
сборке НЕ воспроизводится - Phase A его сняла. Подтверждено также run2 (Settings онеднократно).

Статический разбор всех источников пересоздания верхней Activity на API 33+:
`ColorThemePrefs.applyMode`/`setDefaultNightMode` (process-start = верный night-mode;
отложенный applyMode в `FastMediaSorterApp` :330 = no-op тем же значением на чистом запуске),
условный recreate Phase A, локаль-recreate `onWelcomeLanguageSelected` :488 = только API<33.
Ни один не даёт тайтового каскада на чистом first-run API 33+. Фаза B без обвиняющего
эвиденса не трогается (слепая правка startup-пути рискованна - §3). Фаза C неприменима (API 35).

**Не проверено на эмуляторе:** роль-диалог HOME (S1107 non-regression, requestingPackageName
!= null) не сработал на AVD (известное ограничение эмулятора для role-диалогов). Требуется
подтверждение на реальном устройстве (S21+) per §4.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1107 (дебаунс-обход этой же гонки), S0404 (родительский epic launcher-mode)

---

## 4. Проверка

On-device, чистая установка (единственная валидная верификация):

- `adb uninstall com.sza.fastmediasorter.debug` -> установить свежий debug APK.
- Запустить, пройти онбординг, выбрать не-дефолтный язык и не-дефолтную тему, нажать Finish.
- Снять logcat, отфильтровать `onCreate:` / `onDestroy:` по Welcome/Main/Settings в окне завершения.

PASS:

- first-run SettingsActivity: ровно один `onCreate`, без немедленной пары onDestroy/onCreate.
- Суммарное число recreate в окне завершения меньше baseline x3 из §0.
- Нет видимого мерцания первого кадра.
- Launcher-opt-in: диалог роли HOME появляется, requestingPackageName != null (S1107 не регрессирует).

FAIL: любой лишний recreate SettingsActivity после создания, потеря акцента/языка на первом кадре, регресс S1107.

---

## Last Audit

### 2026-07-26, РЕАЛЬНЫЙ аппарат SM-G781B (Galaxy S20 FE), Android 13 / SDK 33 - PASS

Прогон на аппарате, которого требовала записка статуса (на AVD диалог роли не появлялся, поэтому подтвердить там было нечего). Сборка standard-debug v2.60.7252.333, чистая установка, разрешения выданы заранее, штатный лаунчер Samsung назначен по умолчанию.

Сценарий выполнен ровно по §4: русский язык + тёмная тема (обе корзины меняются), тумблер лаунчера ON, шесть страниц, «Готово».

Все четыре критерия PASS:

- **`SettingsActivity.onCreate` ровно один раз.** Подсчёт по захвату: 1. Исходный шторм из §0 - три `onCreate` за 1.8 с - не воспроизводится. Фаза A (условный `recreate()` только при смене корзины night-mode) удерживает результат и на реальном аппарате, а не только на эмуляторе.
- **Условие срабатывает там, где должно.** Проба `S1136: onWelcomeThemeSelected mode=DARK nightMode -1->2` - корзина действительно меняется, поэтому пересоздание здесь законное и единственное.
- **Передача намерения не теряется:** `S1136: goToMainActivity requestLauncherRole=true`.
- **S1107 не регрессирует - наоборот, впервые проходит.** Диалог роли HOME появился, `RequestRoleActivity` в фокусе и отрисован, строки `Package name cannot be null` в захвате нет. Подробности и оставшийся дефект отказа - в аудите S1107; к предмету этого тикета они не относятся.

Мерцания первого кадра не наблюдалось, язык и тема применены к первому же кадру настроек.
