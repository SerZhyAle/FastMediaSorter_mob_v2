# Спецификация (draft): S1205 - Хостинг закреплённых ярлыков чужих приложений на десктопе лаунчера

**Ticket:** S1205
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-27
**Tier:** 3 - Moderate (ad-hoc)

**Tactical plan:** `PLAN/S1205_launcher-host-third-party-pinned-shortcuts/INDEX.md`

<!-- parked by /spec-quiz - 2026-07-27, выделено из S1175 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-27

**Захвачено во время:** `/spec-quiz S1175` (интеграция Google Карт в лаунчер)

**Вопрос владельца, из которого выделено:**

> ярлык на человека - но гугл создаёт такой ярлык из карт на Samsung - нельзя его свойства скопировать?

---

## 1. Симптом / чего не хватает

Google Maps (и любое другое приложение) умеет сама положить ярлык на домашний экран - «добавить на главный экран» для места, маршрута или человека из шаринга локации. Технически это запрос на закрепление ярлыка, который система адресует лаунчеру по умолчанию. Наш лаунчер такой запрос не обрабатывает, поэтому в режиме лаунчера этот способ добавления просто не работает: пользователь нажимает «добавить на главный экран» в чужом приложении и не получает ничего.

---

## 2. Что уже есть (исследование 2026-07-27)

- `data/launcher/AppShortcutDataSource.kt` (S0427) - единственный шов на `LauncherApps`. Запрашивает быстрые действия установленных приложений (`FLAG_MATCH_MANIFEST`, `FLAG_MATCH_DYNAMIC`) и запускает их через `startShortcut` по идентификатору, без чтения интента.
- Закреплённые ярлыки (`FLAG_MATCH_PINNED`) не запрашиваются, обработчика `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT` в проекте нет.
- Ячейка десктопа умеет вид «ярлык» и опознаётся строкой `ключ[:параметр]` - место для нового вида цели есть.

**Дополнено исследованием 2026-07-27 (планировочный проход):**

- `LauncherCell.labelOverride` уже есть в модели, в сущности БД и применяется в `ResolveLauncherDesktopUseCase`. Но все места записи на десктопе передают в него `null`: **входа переименования у десктопа нет ни для одной ячейки**. То есть хранение готово, а UI - нет.
- Обработки исчезновения приложения в лаунчере нет вообще: ни `PACKAGE_REMOVED`, ни сверки установленности при отрисовке. Де-факто сегодня ячейка удалённого приложения выглядит живой, а тап по ней просто не срабатывает - `ExecuteLauncherCommandUseCase` проверяет получателя перед запуском и молча возвращает неуспех.
- Следствие для §4.3: чтобы помечать ячейку неактивной, понадобится наблюдение за изменениями ярлыков (`LauncherApps.Callback` - `onShortcutsChanged` / `onPackageRemoved`). Это регистрация слушателя, а значит она обязана иметь парную отписку на симметричном краю жизненного цикла - иначе гейт `assert-listener-symmetry` не пропустит.

---

## 3. Ключевой факт для планирования

Свойства чужого ярлыка копировать не нужно и нельзя: интент закреплённого ярлыка читает только лаунчер по умолчанию, а запускать его можно по идентификатору - интент знать не обязательно. Правильный путь - принять запрос на закрепление от чужого приложения и сохранить его как ячейку, а при нажатии запускать системой. Это работает для любого приложения, не только для Карт.

### 3.3 Owner inputs (Approval gate)

- **Подпись ячейки:** только из запроса на закрепление, переименование не входит (квиз 2026-07-27).
- **Пропавший ярлык:** помечается неактивным, ячейка остаётся до удаления пользователем (квиз 2026-07-27).
- **Приём запроса:** молча, с уведомлением; диалога подтверждения не будет (квиз 2026-07-27).
- **Related tickets:** S1175 (интеграция Google Карт - блокируется этим тикетом целиком), S1170 (модель ячейки десктопа и правило первой свободной клетки), S0427 (существующий шов на `LauncherApps` и запуск ярлыка по идентификатору)

---

## 4. Решения (закрыты 2026-07-27)

1. **Подпись и иконка - только из запроса на закрепление.** Переименования не будет. Поле `labelOverride` в модели остаётся незадействованным, как и у всех прочих ячеек десктопа; вход переименования - отдельная общая работа, а не часть этого тикета.
2. **Размещение - первая свободная клетка построчно**, ровно та механика, что решена в S1170. Владельцу не задавалось: отвечает архитектура.
3. **Пропавший ярлык помечается неактивным, ячейка остаётся.** Удаляет её пользователь. Автоудаление отклонено: ячейка исчезла бы из-за временной недоступности - обновления или сбоя чужого приложения - и вернуть её было бы нечем.
4. **Запрос принимается молча, с уведомлением «добавлено на главный экран».** Диалог подтверждения отклонён владельцем.

**Что означает «молча» на уровне реализации.** Не «обработчика нет»: `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT` доставляется лаунчеру именно как activity, и принять закрепление без неё нельзя. Activity остаётся, но не рисует диалог - принимает запрос, кладёт ячейку и немедленно завершается, оставив после себя только уведомление. При заполненном десктопе сообщение обязано быть об отказе, а не об успехе.

### 4.1 Quiz decisions (2026-07-27)

- Что показывать в ячейке принятого чужого ярлыка? -> Только подпись и иконку из запроса; переименование не добавляем.
- Что делать, когда принятый ярлык перестал существовать? -> Помечать ячейку неактивной и оставлять; удаление - за пользователем.
- Спрашивать ли подтверждение при запросе на закрепление? -> Нет: принимать молча и показывать уведомление. **Отклонение рекомендации** - я предлагал диалог подтверждения, потому что Android даёт лаунчеру отдельное действие подтверждения именно под это и так ведут себя стоковые лаунчеры. Владелец выбрал меньше шагов.

---

## 4.5 Как проверить без чужого приложения (найдено при реализации 2026-08-06)

Приложение само закрепляет ярлыки через `requestPinShortcut`: `widget/ResourceShortcutPinManager.kt`
(ярлык ресурса) и `ui/streams/helpers/StreamShortcutPinManager.kt` (ярлык канала). Значит, когда
FastMediaSorter держит роль домашнего экрана, его собственный запрос на закрепление приходит нашей же
`LauncherPinRequestActivity` - это полный сквозной сценарий, для которого не нужны ни Google Карты, ни
Play-сервисы, ни физический телефон. Годится и для эмулятора.

---

## 5. Связи

- S1175 - интеграция Google Карт; пункт «ярлык на отслеживаемого человека» реализуем только через этот механизм.
- S1170 - виджеты приложения на десктопе лаунчера; та же модель ячейки и правило размещения.

---

## Last Audit

**Date:** 2026-08-06
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 41 · WARN 1 (resolved in-run) · FAIL 0 · MANUAL 1 · EXEMPT 1

Checked: 9 files exist and are within budget (largest 271 LOC); 6 declarations plus the
`CONFIRM_PIN_SHORTCUT` action each match exactly once; catalog carries all three new classes; INDEX rows
agree with all five phase headers and zero steps remain unchecked; both reused strings resolve in
en/ru/uk; no `BuildConfig.IS_*`/`SUPPORT_*` guard in `src/main`, with every flavor-only file under
`src/launcherEnabled`; `.\a.ps1 fk` exit 0 after tag removal.

- **[WARN - Phase 02 Done Criteria]** `LauncherCellCommandTest.kt` carried no `dev/CHANGELOG.md` row: the phase-02 closure passed six files to `post-change.ps1` but it logs only the first. Resolved during this audit with `add_to_dev_log.ps1`; the criterion had been ticked without this check, which is the real defect.
- **EXEMPT:** FEATURES trilingual - the strategic spec carries no §8, and `docs/FEATURES*` is `/skill-release`-owned.
- **Debug tags:** 4 removed on the `Verified` flip (`LauncherPinRequestActivity`, `AcceptPinnedShortcutUseCase`, `ExecuteLauncherCommandUseCase`, `ResolveLauncherCommandLabelUseCase`), plus the two `Timber` imports they orphaned. Zero `Timber.*` calls now name S1205 at any level.

### Manual / on-device

- [x] Pin request accepted silently, notice shown, no dialog - verified on-device 2026-08-06 (`outcome=PLACED`).
- [x] Cell lands in the first free slot with the shortcut's own caption and icon - verified on-device 2026-08-06 (`placed=true`, screenshot).
- [x] Tapping the cell starts the shortcut - verified on-device 2026-08-06 (`started=true`, BrowseActivity resumed).
- [ ] §4.3 vanished shortcut renders inactive while the cell stays - not reproducible on demand: only the publishing app can withdraw its own pinned shortcut. Covered by construction (`pinned()` returning null falls back to the stored caption plus the placeholder glyph) and by the decode round-trip test.
- [ ] A genuinely foreign publisher (Google Maps place / shared location) - the on-device run used the app's own `requestPinShortcut`, which exercises the identical platform path but not a third-party one.

---

## Revision History

- **2026-08-06** - by `/spec-test-device` (emulator-5554, Android emulator, standard debug v2.60.8041.533-DEBUG)
  - Scenario: `temp/S1205/mobile_test_scenario_20260806_1150.md` · PASS/FAIL/SKIPPED 6/0/1 · Errors in log: 0
  - Все четыре отладочных метки `S1205:` сработали: запрос принят (`outcome=PLACED`), ячейка легла в первую свободную клетку (`placed=true`), десктоп её отрисовал (`alive=true`), тап по ней запустил ярлык (`started=true`). Диалога подтверждения не было ни разу.
  - Не проверено на устройстве: §4.3 (пропавший ярлык помечается неактивным) - для этого нужно, чтобы издатель отозвал ярлык, а своё же закрепление приложение по требованию не отзывает.
