# S1184 - Гейт detekt даёт ложный PASS при нескольких файлах в области

**Status:** Archived
**Priority:** 80
**Created:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)

## 0. Исходный материал (verbatim)

Обнаружено при реализации S1181, 2026-07-24. Один и тот же отчёт detekt, один и тот же baseline, разница только в составе `-ChangedFiles`:

```
# один файл - FAIL, файл назван
pwsh -File scripts/quality/assert-detekt.ps1 -Module app_v2 `
    -ChangedFiles 'app_v2/.../CameraCaptureSessionManager.kt'
assert-detekt: NEW findings in changed file(s):
  p:/android/fastmediasorter_mob_v2/app_v2/.../cameracapturesessionmanager.kt
assert-detekt: FAIL [scoped]
exit=1

# тот же файл плюс ещё один - PASS
pwsh -File scripts/quality/assert-detekt.ps1 -Module app_v2 `
    -ChangedFiles 'app_v2/.../CameraCaptureSessionManager.kt,app_v2/.../CameraCaptureActivity.kt'
assert-detekt: PASS [scoped] - 202 file(s) with new findings project-wide, none among changed files.
exit=0
```

Проверено, что второй файл сам по себе чист: с `-ChangedFiles` из одного `CameraCaptureActivity.kt` гейт даёт PASS. То есть расширение области не могло убрать находки первого файла.

## 1. Симптом

Добавление файла в `-ChangedFiles` убирает находки другого файла из вердикта. Расширение области проверки не может уменьшать число найденного - значит при списке из нескольких элементов сопоставление путей не срабатывает вовсе, и гейт рапортует «none among changed files», ничего на самом деле не сверив.

Это ровно тот режим отказа, от которого гейт и должен защищать: молчаливый зелёный вердикт вместо проверки.

## 2. Почему это важно

`assert-detekt.ps1` вызывается из `scripts/post-change.ps1` - обязательной механической закрывающей процедуры для любого изменения Kotlin. При `-ScopeToFile` post-change передаёт ровно один файл, поэтому его собственный путь пока корректен. Но:

- ручные и скиптовые вызовы с CSV из нескольких файлов - обычная практика при закрытии тикета, тронувшего несколько классов, и они получают ложный зелёный;
- вердикт «PASS [scoped] .. none among changed files» неотличим от честного, поэтому ошибка не видна ни в логе, ни в отчёте тикета;
- уже есть смежная записанная ловушка: `PASS [scoped] - 0 file(s)` означает, что гейт вообще ничего не смотрел (S1077). Это её родственник, но опаснее - счётчик файлов ненулевой и выглядит убедительно.

## 3. Корневая причина (подтверждено)

`-ChangedFiles` объявлен `[string[]]`, но `pwsh -File .. -ChangedFiles 'a.kt,b.kt'` связывает CSV одним элементом массива - `@('a.kt,b.kt')`, - не расщепляя по запятой. Ни `assert-detekt.ps1`, ни общая либа count-ratchet гейтов запятую не режут:

- `assert-detekt.ps1` (строки 121-129): нормализует каждый элемент и матчит через `$ff -like "*$cf*"`. При CSV `$cf` = `"a.kt,b.kt"`, а путь находки (`.../a.kt`) подстроки `"a.kt,b.kt"` (с запятой) не содержит - совпадений ноль, вердикт «none among changed files», ложный PASS.
- `scripts/quality/lib/changed-files-delta.ps1` `Measure-ChangedFileGrowth` (`foreach ($cf in $ChangedFiles)`): CSV-строка проходит `GetExtension` (даёт `.kt`), но `Test-Path` по «a.kt,b.kt» промахивается -> `workText=''` -> рост 0 -> ложный PASS. Через эту либу баг у всех дельта-гейтов: `assert-neuroslop`, `assert-listener-symmetry`, `assert-flavor-flags-not-growing`, `assert-deprecated-pm-flags`, `assert-unsafe-collect`, `assert-globalscope` и др.

`post-change.ps1` при `-ScopeToFile` передаёт ровно один файл (`-ChangedFiles $File`), поэтому фасадный путь не задет - страдают только ручные и скриптовые мультифайловые CSV-вызовы (как в эвиденсе S1181).

## 4. Исправление

Единый нормализатор `-ChangedFiles`, режущий запятую, - обе формы (PS-массив `@('a','b')` и CSV `'a,b'`) дают один список. Одиночный путь без запятой возвращается собой -> для существующих массивных вызовов поведение не меняется.

**4.1** Новый `scripts/quality/lib/changed-files.ps1`:
- `Expand-ChangedFiles` - расплющивает `-ChangedFiles` в отдельные обрезанные пути (split по `,`).
- `Select-ChangedFileFindings` - подмножество путей находок, совпавших с любым changed-file (нормализация + `-like`), для `assert-detekt`.

**4.2** `assert-detekt.ps1` - заменить inline нормализацию/матч на `Select-ChangedFileFindings`.

**4.3** `changed-files-delta.ps1` - `Measure-ChangedFileGrowth` итерирует `Expand-ChangedFiles $ChangedFiles`, чиня разом все дельта-гейты.

**4.4** Регрессионный тест `scripts/quality.tests/Run-Tests.ps1` (герметичный, temp-only, exit 0/1): вердикт для CSV равен вердикту для массива для `Expand-ChangedFiles`, `Select-ChangedFileFindings` и `Measure-ChangedFileGrowth`.

### 4.1 Вне области

- Отказ (не PASS) при «ни один путь не совпал»: после расщепления совпадение работает, а changed-file без находок легитимно не матчится - fail-closed по несовпадению давал бы ложные FAIL на чистых файлах. Не вводим.
- `S1077` (`PASS [scoped] - 0 file(s)`) - смежная, но отдельная ловушка про пустой отчёт; не трогаем.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1181, S1077

## 5. Проверка

- `scripts/quality.tests/Run-Tests.ps1` - exit 0, все кейсы PASS.
- Репро §0 после фикса: `assert-detekt -ChangedFiles 'dirty.kt,clean.kt'` даёт тот же вердикт, что одиночный вызов по грязному файлу (совпадение находки, FAIL при `-Gate`).
- `assert-exit-contract` на изменённых скриптах - PASS.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Static checks

- Regression `scripts/quality.tests/Run-Tests.ps1`: 12/12 PASS, exit 0 - `Expand-ChangedFiles`, `Select-ChangedFileFindings`, and `Measure-ChangedFileGrowth` all give the same verdict for a CSV `-ChangedFiles` as for the equivalent array.
- End-to-end (the §0 repro): `assert-detekt -Gate -ChangedFiles '<dirty>,<clean>'` now returns `FAIL [scoped]` (real exit 1) naming the dirty `CameraCaptureSessionManager.kt` - the same two-file call that previously returned a false `PASS`. Evidence: `temp/S1184/e2e.txt`.
- Control: `assert-detekt -Gate -ChangedFiles '<clean>'` returns `PASS [scoped]` (exit 0) - a clean changed-file set legitimately matches nothing, so no fail-closed false positive was introduced. Evidence: `temp/S1184/e2e_clean.txt`.
- `Select-ChangedFileFindings` used by `assert-detekt.ps1`; `Expand-ChangedFiles` used by `Measure-ChangedFileGrowth`, so every count-ratchet gate (neuroslop / listener-symmetry / flavor-flag / deprecated-pm / unsafe-collect / globalscope) is fixed through the shared lib.
- `assert-exit-contract`: PASS (no unreachable exit sites in the changed scripts).
- No behavior change for existing array callers: a single path with no comma splits to itself; `post-change.ps1 -ScopeToFile` (one `-File`) is unaffected.

### FEATURES / inventory

- EXEMPT - internal quality-gate tooling, no user-visible capability; `docs/ALL_FEATURES.jsonl` not touched.
