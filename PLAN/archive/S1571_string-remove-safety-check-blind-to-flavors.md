# Спецификация (compact bugfix): S1571 - Проверка перед удалением строки не видит флейворные исходники

**Ticket:** S1571
**Status:** Archived
**Priority:** 70
**Date:** 2026-08-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Захвачено во время:** S1568 (исследование мёртвых строковых ключей)

**Текст:**

`Report-References` in `scripts/utils/set-android-string.ps1` (line 267) is the safety check `-Action remove`
prints before deleting a string key, and it is blind in two ways. First, `$srcRoot = Join-Path $resDir '..'`
resolves to `<module>/src/main`, so none of the 39 source-set directories under `app_v2/src` are scanned -
`launcherEnabled`, `castEnabled`, `screenCapture`, `noLegal`, `vr`, `standard` and the rest. Measured during
S1568 research on 2026-08-11: 216 keys of `values/strings.xml` are referenced ONLY from a flavor source set,
and for every one of those the check prints `none`, which reads as "safe to remove". Second, the three
patterns it greps are `R.string.<key>` in .kt/.java and `@string/<key>` in .xml - there is no
`R.plurals.`/`R.array.`/`@plurals/`/`@array/` counterpart, so the 6 plurals and 1 string-array in that file
have no safety check at all. Verified by hand: reading the function confirms both the `src/main` root and the
three-pattern list. The failure is silent and one-way - the key and its ten locale copies are gone, the build
then fails at the flavor that used it, or worse compiles because the reference was in XML and fails at
runtime. Also worth deciding in the same change: `Report-References` only prints, it never refuses, so a
caller who does not read the output gets no gate at all.

---

## 1. Проблема / симптом

`scripts/utils/set-android-string.ps1 -Action remove` удаляет ключ из всех локалей и печатает список ссылок на него в коде. Список систематически пустой там, где ссылки есть, поэтому вывод читается как «удалять безопасно» ровно в тех случаях, когда удалять нельзя. Отказа нет ни при каком исходе - только печать.

Четыре независимых слепых пятна, все подтверждены чтением кода 2026-08-11:

- **Корень сканирования - только `src/main`.** `$resDir` = `<repo>/<module>/src/main/res` (строка 134), `Report-References` берёт `$srcRoot = Join-Path $resDir '..'` (строка 270), то есть `<module>/src/main`. Под `app_v2/src` лежит 41 каталог исходников; 40 из них не сканируется вовсе - `standard`, `noLegal`, `vr`, `lite`, `photos`, `legacy`, `launcherEnabled`, `castEnabled`, `screenCapture`, все девять тестовых и остальные.
- **Нет шаблонов для `plurals` и `string-array`.** Ищутся ровно три: `R.string.<key>` в `.kt`/`.java` и `@string/<key>` в `.xml`. `R.plurals.` / `R.array.` / `@plurals/` / `@array/` не ищутся, поэтому у 6 `<plurals>` и 1 `<string-array>` в `values/strings.xml` проверки нет вообще.
- **Проверка запускается ПОСЛЕ удаления.** `Report-References $Key` стоит на строке 596 - после цикла удаления по всем локалям (строки 583-594) и непосредственно перед `exit 0`. То же на строке 625 для `rename`. Это не предохранитель, а вскрытие: к моменту печати ключ и все его локальные копии уже стёрты с диска. Захват описывал её как «печатает перед удалением» - по коду это не так, и дефект от этого тяжелее.
- **`-DryRun` не спасает.** Для `remove`/`rename` он влияет только на запись файла; порядок вызова тот же, поэтому предпросмотр тоже показывает ссылки последними.

Цена ошибки односторонняя: ключ и его 12 локальных копий удалены, а сборка падает на том флейворе, который им пользовался, - либо, если ссылка была из XML, собирается и падает уже в рантайме.

Масштаб взят из исследования S1568 (`PLAN/S1568_unreferenced-string-keys-audit/research/01__deadness-method-and-risk-subsets.md`): 216 ключей `values/strings.xml` живы только через флейворный source set. Для каждого из них проверка печатает `none`.

---

## 2. Корневая причина

Проверка писалась под однофлейворное дерево: один корень `src/main`, один вид ресурса (`<string>`), и отчёт как удобство, а не как гейт. Дерево с тех пор выросло до шести флейворов и десятков feature-source-set'ов, а `Report-References` не менялась - ни корень, ни список шаблонов, ни позиция вызова.

Позиция вызова - отдельная причина, а не следствие: функция называется отчётом, поэтому её и поставили туда, где отчитываются, - в конец. Пока она печатает и не возвращает результат, вызвать её раньше нечем: у неё нет возвращаемого значения, по которому можно было бы принять решение.

---

## 3. Исправление

- **Расширить корень сканирования** с `<module>/src/main` до `<module>/src` - все source set'ы модуля, включая флейворные, feature- и тестовые. Ресурсы (`src/main/res`) остаются внутри этого корня, поэтому ссылки вида `@string/<key>` из `<style>`/`<string-array>` по-прежнему ловятся; собственное объявление ключа (`name="<key>"`) под шаблоны не подпадает и ложной ссылкой не станет.
- **Расширить список шаблонов** до шести: `R.string.` / `R.plurals.` / `R.array.` в `.kt`/`.java` и `@string/` / `@plurals/` / `@array/` в `.xml`. Одно объединённое регулярное выражение с границей справа, один проход по дереву - вместо трёх рекурсивных обходов.
- **Перенести проверку перед мутацией** в обоих действиях. Функция начинает возвращать найденные ссылки, а не только печатать.
- **Сделать её гейтом для `remove`.** Есть хоть одна ссылка - действие отклоняется с ненулевым кодом выхода и списком мест; ключ остаётся на диске. Обойти можно явным `-Force`: сама проверка при этом всё равно выполняется и печатается, обходится только отказ.
- **Для `rename` оставить предупредительной.** Переименование ключа, на который ссылаются, - это нормальный сценарий, а не ошибка, поэтому отказ здесь сломал бы штатную работу. Ссылки печатаются до мутации с явным указанием, что их нужно переписать на новое имя; код выхода не меняется.
- **Задекларировать коды выхода** в заголовке скрипта - требование правила 7 «reachable exit codes», проверяется `scripts/quality/assert-exit-contract.ps1`.

### 3.1 Вне охвата

- Сама чистка мёртвых ключей - это S1568, он же первым и пострадает от текущего дефекта.
- Автоматическая перезапись ссылок при `rename` - отдельная задача, здесь только предупреждение.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1568 (нашёл дефект и первым пострадает), S1420 (массово правит локали тем же инструментом)

---

## 4. Проверка

- Ключ, на который ссылается только флейворный source set, не удаляется: `-Action remove -Key <flavor-only-key> -DryRun` печатает найденные ссылки и выходит ненулевым кодом, ключ остаётся во всех локалях. `expected: exit 3, key present | actual: ..`. Контрольный ключ берётся из списка 216 в исследовании S1568.
- Тот же вызов с `-Force` проходит: печать ссылок остаётся, отказа нет. `expected: exit 0`.
- Ключ без единой ссылки удаляется как раньше. `expected: exit 0`.
- `plurals`/`string-array` больше не без присмотра: `-Action remove` по имени `<plurals>` из `values/strings.xml` находит ссылку `R.plurals.<name>` и отказывает.
- `rename` печатает ссылки до мутации и не отказывает. `expected: exit 0`.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` проходит для этого скрипта. `expected: exit 0`.
- `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Script` - все гейты зелёные.

---

## Last Audit

**Date:** 2026-08-11
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Evidence

- **Флейворная слепота закрыта, на живом примере.** `-Action remove -Key screenshot_accessibility_service_description -DryRun`: `exit 3`, найдена ссылка `app_v2/src/noLegal/res/xml/screenshot_accessibility_service_config.xml:12`, ключ остался в `values/strings.xml` (`expected: exit 3, key present | actual: exit 3, present`). Старый код на этом же ключе печатал бы `none` дважды слепо - и по корню (`src/main`), и по расположению ссылки (`res/xml`, а не `.kt`).
- Второй флейворный пример: `vr_hud_prev` -> `app_v2/src/vr/java/../DiagnosticXrActivity.kt:345`, `exit 3`.
- **`plurals` больше не без присмотра:** `-Action remove -Key selected_n_files -DryRun` находит `R.plurals.selected_n_files` в `BrowseManagerInitializer.kt:755` и отказывает, `exit 3`; `<plurals name="selected_n_files">` на месте.
- **Проверка выполняется до мутации:** во всех трёх отказах ключ остался на диске, то есть ни одна локаль не была тронута до вердикта.
- **`-Force` снимает только отказ:** тот же вызов с `-Force` печатает тот же список ссылок и идёт дальше, `exit 0`.
- **Путь «ссылок нет» не сломан:** `-Key s1571_no_such_key_probe -DryRun` -> `No references .. under app_v2/src`, затем штатное `not found in any locale`, `exit 0`.
- **`rename` остался предупредительным:** ссылки печатаются до мутации с указанием нового имени, `exit 0`.
- **Регрессий в остальных действиях нет:** `-Action get -Key app_name` печатает все 13 локалей, `exit 0`.
- **Контракт кодов выхода:** блок `.NOTES Exit codes` добавлен в заголовок, `scripts/quality/assert-exit-contract.ps1` -> `0 unreachable exit site(s), 0 silent script(s), 0 reasonless exit(s)`, `exit 0`.
- **Сторонних вызовов `-Action remove` в репозитории нет** (grep по дереву даёт только несвязанную строку справки в `scripts/spec_catalog/skip-cache.ps1`), поэтому новый отказ по умолчанию ничего существующего не ломает.
- **`scripts/post-change.ps1 -ChangeType Script -ScopeToFile`:** `PASS WITH ADVISORIES (1)`, `exit 0`. Единственная advisory - устаревший `docs/SCRIPT_CHEATSHEET.md`; она относится именно к этой правке (появился параметр `-Force`), поэтому файл перегенерирован `help.ps1 -Generate`, после чего `assert-script-cheatsheet-sync.ps1` -> `in sync`, `exit 0`.
- Инвариант отладочных меток: `.kt` не менялись, `Timber.d("S1571:` - 0 вхождений.
- FEATURES trilingual - EXEMPT: правка внутреннего инструмента, пользовательской поверхности не касается, записи в `docs/ALL_FEATURES.jsonl` не требует.

### Отклонение от плана

- §4 предполагал контрольный ключ из списка 216 в исследовании S1568. Взят `screenshot_accessibility_service_description` - он строже: ссылка на него живёт в XML флейворного source set, то есть промахивались оба измерения старой проверки сразу, а не одно.
