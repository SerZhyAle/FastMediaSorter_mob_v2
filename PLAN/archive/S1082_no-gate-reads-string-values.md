# S1082 - Ни один гейт не читает значения строк

**Status:** Archived
**Priority:** 50
**Tier:** 4 - Strategic (ad-hoc)
**Created:** 2026-07-17

---

## 0. Как найдено (verbatim)

Вытекает из P0, отгруженного в S0404 фазе 05 и найденного её аудитом. Паркуется как **out-of-scope находка** (CLAUDE.md 3.1): к лаунчеру отношения не имеет, тривиальной правкой не чинится, требует своего исследования. Дедуп по каталогу (`search.ps1 -Query "format specifier strings"`, `-Query "strings.xml gate"`) - совпадений нет.

Запись из аудита S0404 фазы 05 (`PLAN/S0404_android-launcher-mode-profiles/INDEX.md`):

> **One P0, and it was shipped by me, not inherited:** all nine `launcher_tray_battery_*` strings carried a PowerShell escape backtick inside the format specifier (`%1``$d`), so `getString(id, percent)` threw `UnknownFormatConversionException` on the tray's seed path during `setupViews()` - killing the declared HOME activity into a **crash loop with no usable home screen**.
>
> The lesson is about gates, not about the typo. `check_strings_localized.ps1` proves keys exist, not that values format; aapt2 only rejects multi-arg non-positional strings; detekt never reads string values. **Nothing static could have caught it** - only the device pass in step 05.6, which was ticked without running.

Проверено при парковке: в `scripts/quality/` 28 гейтов `assert-*.ps1`, ни один не разбирает значение `<string>`.

---

## 1. Проблема

Строка может быть синтаксически валидным XML, пройти паритет EN/RU/UK, пройти aapt2, пройти detekt - и **гарантированно уронить приложение** при первом же `getString(id, arg)`. Между «строка добавлена» и «строка отформатируется» в проекте нет ни одной автоматической проверки.

Цена промаха несимметрична: битый спецификатор в строке, которая рисуется на старте объявленной HOME-активити, - это не косметика, а краш-луп без выхода. Ровно это и произошло.

Класс шире одной обратной кавычки:

- мусор внутри спецификатора (`%1`$d`, `%1 $s`);
- рассинхрон типов между локалями (EN `%1$d`, RU `%1$s`) - падает только на русской локали;
- рассинхрон **количества** аргументов между локалями - падает только там, где аргументов больше;
- позиционные индексы с дырами (`%1$s` + `%3$s`);
- неэкранированный `%` в тексте.

Отдельно стоит риск инструмента: значения строк в этом проекте пишутся из PowerShell (`set-android-string.ps1`), где `` ` `` - управляющий символ. Именно этот стык и родил P0, и он остаётся заряженным.

## 2. Цели

- Битый или рассинхронизированный спецификатор ловится статически, до устройства.
- Проверка идёт по **всем** локалям и сравнивает их между собой, а не только валидирует каждую отдельно.
- Гейт встроен в `post-change.ps1` так же, как остальные, и имеет ratchet-базлайн, если в проекте уже есть нарушения.

## 3. Известные точки

- `scripts/check_strings_localized.ps1` - проверяет только наличие ключа в EN/RU/UK.
- `scripts/utils/set-android-string.ps1` - точка записи; кандидат на валидацию значения **на входе**, а не только постфактум.
- `scripts/quality/assert-*.ps1` - 28 гейтов, ни один не читает значения.
- `scripts/post-change.ps1` - куда встраивать.

## 4. Выбранное решение

Реализовано в три слоя:

- `scripts/quality/assert-string-format.ps1` - новый ratchet-гейт, который читает значения `<string>`, ловит битый формат и сверяет контракт EN/RU/UK;
- `scripts/utils/set-android-string.ps1` - ранняя валидация значения на входе, чтобы PowerShell-стык не мог снова впрыснуть `%1``$d`;
- `scripts/post-change.ps1` - автоматический прогон нового гейта при изменении `values*/strings*.xml`.

Baseline хранится в `scripts/quality/assert-string-format-baseline.txt`.

## 5. Проверка

- Гейт падает на подсунутом `%1`$d` и на EN `%1$d` против RU `%1$s`.
- `set-android-string.ps1` теперь отказывает раньше записи, если локаль несёт битый спецификатор или если EN/RU/UK расходятся по контракту аргументов.
- Прогон по текущему дереву даёт осмысленный результат (0 или baseline).

## Last Audit

**Static audit - 2026-07-18**

- PASS - `scripts/quality/assert-string-format.ps1 -Gate`: baseline 0, actual 0.
- PASS - `scripts/post-change.ps1` invokes the gate for changed `values*/strings*.xml` resources.
- PASS - `set-android-string.ps1 -Action set -DryRun` rejects an incomplete `%1` format token before writing.
- PASS - `set-android-string.ps1 -Action add -DryRun` rejects an EN/RU format-contract type mismatch before writing.
- P0/P1: none. The gate is read-only; the editing utility validates before its first write.
