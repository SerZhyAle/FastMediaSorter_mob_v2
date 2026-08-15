# Спецификация: S1456 - Непривязанные диалоги за пределами хелперов настроек

**Ticket:** S1456
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

**Tactical plan:** `PLAN/S1456_untracked-dialogs-outside-settings-helpers/INDEX.md`

<!-- auto-approved by /spec-all - 2026-08-09 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1447

**Текст:**

S1447 fixed the untracked-dialog leak only inside `ui/settings/helpers/` - 36 builder chains bound to the host lifecycle through the new `AlertDialog.Builder.showBoundTo` in `util/LifecycleDialogExt.kt`, and the convention written into `docs/ARCHITECTURE.md` as "Dialog Lifecycle Binding (MANDATORY)". The same bare `.show()` shape exists elsewhere in the tree - `ui/browse/managers/*`, the player surfaces and the launcher - and nothing stops a new one from being added, because the convention has no mechanical gate behind it. Two pieces of work, both deliberately left out of S1447 so the fix would not spread across half the tree in one change: (1) sweep the remaining call sites onto `showBoundTo`; (2) add `scripts/quality/assert-untracked-dialogs.ps1` as a ratchet gate wired into `post-change.ps1`, so a new bare `MaterialAlertDialogBuilder(..)..show()` outside a `DialogFragment` fails closure. The gate is the reason this ticket is worth having at all - S1447 showed the convention was never picked up in the four years it existed only as a precedent in one helper (S1197).

---

## 1. Проблема / симптом

- Диалог, показанный голым `.show()`, выбрасывает возвращённый `AlertDialog`, поэтому закрыть его потом некому: окно переживает уничтожение хоста и держит мёртвые Fragment и Activity живыми.
- Замер по дереву на 2026-08-09: **144 таких сайта в 88 файлах** вне уже вылеченного `ui/settings/helpers/`. Первый прогон дал 146; два оказались ложными - предикат читал `Toast..show()` внутри лямбды билдера как терминатор цепочки.
- Крупнейшие семейства: `ui/player/helpers` (25), `ui/browse/managers` (22), `ui/addresource` (12), `ui/main/helpers` (9), `ui/player/standalone` (7), `ui/share` (7), `ui/settings/fragments` (6).
- Затронуты не только `src/main`: `src/launcherEnabled` (5), `src/screenCapture` (2), `src/noLegal` (1).
- Симптом воспроизводится тем же способом, что и в S1447: смена системной темы, языка приложения, размера шрифта или "Не сохранять действия" в параметрах разработчика.
- Конвенция `docs/ARCHITECTURE.md` "Dialog Lifecycle Binding (MANDATORY)" существует, но за ней нет механической проверки, поэтому новый голый `.show()` проходит закрытие без единого возражения.

---

## 2. Цели

- Механический храповик, который валит закрытие на новом голом `.show()` у билдера диалога.
- Свод всех существующих сайтов на `showBoundTo`, до baseline 0.
- Конвенция перестаёт зависеть от того, прочитал ли автор `ARCHITECTURE.md`.

---

## 3. Пожелания и ограничения

- Гейт живёт в общей инфраструктуре лексических правил, а не отдельным обходом дерева: правило одно, исполнителей ноль (`scripts/quality/lib/source-matchers.ps1`, исполняется `assert-source-gates.ps1`).
- Имя обёртки задано захватом: `scripts/quality/assert-untracked-dialogs.ps1`.
- Свод не меняет ни одного пользовательского сценария: тот же диалог, те же кнопки, тот же обработчик.
- Правило судит все шипящиеся source sets, а не только `src/main` - нарушения есть в трёх флейворных наборах.
- Сводить семействами, а не одним коммитом на 90 файлов: каждое семейство закрывается своим прогоном гейта и опусканием baseline.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1447 - ввёл `showBoundTo` и закрыл семейство настроек; S1197 - исходный прецедент удержания диалога.
- **UI placement:** не требуется - изменение невидимо, ни одна кнопка, надпись или расположение не двигаются.
- **Sensitive scope:** нет - ни разрешений, ни сети, ни хранилища, ни ключей.

---

## 4. Контекст текущей архитектуры

- `util/LifecycleDialogExt.kt` объявляет `AlertDialog.Builder.showBoundTo(owner: LifecycleOwner)` и перегрузку `showBoundTo(fragment: Fragment)`, которая берёт view-lifecycle фрагмента и откатывается на сам фрагмент, пока view нет.
- Расширение объявлено на `AlertDialog.Builder`, поэтому одинаково работает и для `MaterialAlertDialogBuilder`, который является его наследником.
- `docs/ARCHITECTURE.md` раздел "Dialog Lifecycle Binding (MANDATORY)" уже фиксирует правило словами.
- Семейство храповиков: правило описывается записью в `Get-SourceRules` (`scripts/quality/lib/source-matchers.ps1`), считается одним проходом дерева в `assert-source-gates.ps1`, а исторические `assert-<rule>.ps1` - тонкие обёртки над ним.
- `post-change.ps1` вызывает `assert-neuroslop.ps1 -Gate`, который прогоняет весь набор правил, поэтому новое правило попадает в закрытие без отдельной регистрации.
- Baseline каждого правила - целое число в `scripts/quality/<rule>-baseline.txt`, опускаемое только вниз.
- `docs/SCRIPT_CHEATSHEET.md` генерируется и сверяется `assert-script-cheatsheet-sync.ps1`, поэтому новая обёртка требует перегенерации.

---

## 5. Предлагаемый подход

- Правило `untracked-dialog` в `Get-SourceRules` с собственным предикатом и baseline-файлом.
- Нарушение - конструкция `MaterialAlertDialogBuilder(` или `AlertDialog.Builder(`, цепочка которой заканчивается `.show()` вместо `.showBoundTo(`.
- Два прохода предиката: цепочка в одном выражении и удержанный билдер, присвоенный имени, у которого дальше по файлу вызывается `<name>.show()`.
- Замер разделил сайты на 125 цепочек и 21 удержанный диалог: последние вызывают `.create()`, донастраивают полученный `AlertDialog` и показывают его отдельной строкой, поэтому расширение на билдере им не подходит.
- `util/LifecycleDialogExt.kt` получает перегрузку с приёмником `AlertDialog`, которая вешает того же наблюдателя на уже созданный диалог и возвращает его; отладочные метки `S1447:` в этом файле не трогаются.
- Обёртка `scripts/quality/assert-untracked-dialogs.ps1` по образцу `assert-swallowed-cancellation.ps1`, чтобы гейт можно было звать напрямую и по имени из захвата.
- Baseline стартует с замеренного числа, затем опускается после каждого сведённого семейства.
- Свод по семействам в порядке убывания размера: player, browse, addresource и main, settings и streams и share, длинный хвост и флейворные наборы.
- Владелец жизненного цикла на каждом сайте: `this` в Activity, поле-фрагмент в менеджере, поднятый до вызывающего параметр там, где у хелпера нет ни того, ни другого.

---

## 6. Открытые вопросы / Research items

- Нет. Предикат, имя обёртки и порядок свода определены замером и существующей инфраструктурой.

---

## 7. Риски

- Ложное срабатывание на удержанном билдере, если одно имя переиспользуется под несколько билдеров в одном файле - предикат считает различимые строки, а не совпадения.
- Хелпер без `Fragment` и без `Activity` в области видимости требует прокинуть владельца параметром, что меняет сигнатуру и вызывающий код.
- Диалог, у которого вызывающий сам держит ссылку и сам закрывает её, после свода получит второго закрывателя - двойной `dismiss` безвреден, но лишний код надо снять.
- Объём регрессии: 146 сайтов - это почти каждый диалог приложения, поэтому устройство проверяет выборку по семействам, а не каждый сайт.
- Параллельные сессии сейчас правят `LauncherHomeActivity` и `scripts/quality/lib/`, поэтому перед правкой этих файлов нужно перечитывать их с диска.

---

## 8. Влияние на пользователя (docs/FEATURES)

- Пользователь ничего не выигрывает в функциях и ничего не теряет.
- Единственное видимое следствие: диалог, оставшийся открытым при смене темы или языка, закрывается сам вместо того, чтобы висеть поверх пересозданного экрана.
- Запись в `docs/ALL_FEATURES.jsonl` - типа FIX, без строк локализации.

---

## 9. Архитектурные решения (ADR)

- Распознавание лексическое, а не типовое: гейт живёт в семействе текстовых правил и не поднимает компилятор.
- Хост-`DialogFragment` не освобождается от правила: у него владелец жизненного цикла всегда под рукой, а `.show()` внутри него - такой же брошенный диалог.
- `util/LifecycleDialogExt.kt` не исключается по имени: собственный `show()` расширения не сопровождается конструкцией билдера и предикатом не ловится.
- Baseline опускается только вниз и доводится до нуля этим же тикетом, иначе храповик узаконит существующую течь.

---

## 10. Связи с другими спеками

- S1447 - ввёл `showBoundTo` и закрыл 36 сайтов в `ui/settings/helpers/`; сейчас в `BlockNeedUserTest`, его отладочные метки `S1447:` в `LifecycleDialogExt.kt` трогать нельзя.
- S1197 - исходный прецедент ручного удержания диалога.

---

## 11. Критерии готовности (strategic-level)

- Правило `untracked-dialog` считается в `assert-source-gates.ps1` и валит закрытие при росте.
- Обёртка `scripts/quality/assert-untracked-dialogs.ps1` существует и отвечает теми же кодами выхода, что и соседи по семейству.
- `scripts/quality/untracked-dialog-baseline.txt` равен нулю.
- Ни один шипящийся source set не содержит цепочки билдера, заканчивающейся голым `.show()`.
- `docs/ARCHITECTURE.md` называет гейт рядом с конвенцией, `docs/SCRIPT_CHEATSHEET.md` перегенерирован.

---

## REPRO

**До.** Гейт `assert-untracked-dialogs.ps1` на 2026-08-09 насчитал **144 цепочки билдера, заканчивающиеся голым `.show()`**, в 88 файлах трёх шипящихся source set. Каждая выбрасывает возвращённый `AlertDialog`, поэтому закрыть окно при уничтожении хоста некому - тот же механизм, что S1447 подтвердил на устройстве для четырнадцати хелперов настроек.

**После.** Тот же гейт печатает пустой список, `scripts/quality/untracked-dialog-baseline.txt` равен `0`, `.\a.ps1 fk` и `.\a.ps1 fkn` собираются без ошибок.

**Что осталось проверить на устройстве.** Механическое доказательство показывает, что непривязанных диалогов больше нет; оно не показывает, что привязка срабатывает в рантайме и что ни один диалог не закрывается сам во время нормальной работы. Это и есть предмет device-теста, отмеченного пятью метками `Timber.d("S1456: ..")`.

---

## Revision History

- 2026-08-07 - захват через `/spec-draft` во время S1447.
- 2026-08-09 - стратегический спек написан по замеру дерева (146 сайтов в 90 файлах), статус Draft -> Approved.
