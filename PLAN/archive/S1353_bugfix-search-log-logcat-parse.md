# Спецификация (compact bugfix): S1353 - search-log.ps1 не структурирует реальный logcat-файл

**Ticket:** S1353
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-08-02 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

search-log.ps1 fails to structure a real captured logcat file (0 structured + 26812 continuation/separator lines out of 26812 raw lines), so -Pattern/-Errors/-Summary modes silently report "No matches found" even when the pattern is present (verified via direct grep - 3 real hits). Discovered 2026-08-02 during S1350's /spec-test-device run against temp/S1350/run_20260802_1030.log, captured via `adb logcat -v time` by scripts/builders/build-standard-device.ps1's background capture. Sample raw line format: "08-02 10:30:19.919   448   611 D CompositionEngine: Layer: ..". Suspect the parser expects a different logcat format (e.g. threadtime with a different column layout) than what -v time actually produces, or than what the build script's background capture writes. Impact: any device-test log analysis via search-log.ps1 against a build-script-captured log currently returns false negatives (reports clean when errors/tags are actually present) - a silent-blindness pattern, not just an inconvenience. Not investigated further - out of scope for S1350 (pure BrowseViewModel DI refactor), and S1350's own evidence was gathered via direct grep instead as a workaround.

---

## 1. Проблема / симптом

`scripts/utils/search-log.ps1` заявляет поддержку 3 форматов лога (LOGCAT/JSON/TIMBER), но ни один
из них не совпадает с тем, что реально пишут скрипты захвата в этом репозитории. На реальном файле
`temp/S1350/run_20260802_1030.log` (26812 строк, `adb logcat -v threadtime` от
`build-standard-device.ps1`) парсер даёт 0 структурированных строк - `-Pattern`/`-Errors`/`-Summary`
молча репортят "No matches found" даже когда совпадения реально есть (проверено прямым grep).
При регрессионной проверке той же природы найдены ещё 3 реальных файла (`temp/S0671_run_*.log`,
`temp/S0704_run_*.log`, `temp/S0686_run_*.log`) в третьем нераспознаваемом формате - сыром выводе
`adb logcat -v time` - тоже 0 структурированных строк из ~8000-10000 каждый. Итог: любой лог,
захваченный напрямую через `adb logcat` (а не через AS copy-paste или Timber-экспорт приложения),
парсер тихо считает пустым - false negative, не просто неудобство.

---

## 2. Корневая причина

Единственный поддерживаемый "живой" формат (FORMAT 1 LOGCAT) написан под обогащённый Android
Studio copy-paste: 4-значный год, объединённое поле `PID-TID`, отдельное поле пакета между тегом и
уровнем. Реальный вывод `adb logcat` в двух распространённых вербозити-режимах структурно другой:
- `-v threadtime` (использует `build-standard-device.ps1`): `MM-DD HH:MM:SS.mmm PID TID LVL TAG: MSG`
  - без года, PID и TID - раздельные числовые поля, без поля пакета вовсе.
- `-v time`: `MM-DD HH:MM:SS.mmm LVL/TAG(PID): MSG` - без года, без TID, PID в скобках после тега.

Оба формата проваливают единственный `$LineRegex` (ожидает `\d{4}-...` год) и падают в TIMBER-ветку
тоже мимо (та ожидает `LVL/TAG:` сразу после отметки времени без PID/TID вовсе). `Get-LogFormat`
молча откатывается к `"LOGCAT"` по умолчанию, парсер не находит совпадений - каждая строка становится
"continuation" предыдущей несуществующей записи.

---

## 3. Исправление

Добавлены 2 новых формата (`THREADTIME`, `TIME`) со своими regex-парсерами и веткой в
format-aware loader, детекция вставлена между TIMBER и LOGCAT-fallback (структурно
непересекающаяся с 4-значным годом LOGCAT/TIMBER - коллизий по построению нет, проверено на всех 4
реальных файлах). `Get-LogFormat` также теперь пропускает `--------- beginning of <buffer>`
разделители при снифинге формата (раньше только пустые строки), иначе первая строка файла ложно
уводила детекцию в fallback. Добавлен диспетчер `Parse-AnyLine`, используемый в `-Exceptions`/
`-Context` режимах вместо жёстко зашитого `Parse-Line` - без него уровень-граница блока крэша в
`-Exceptions` никогда не срабатывала для THREADTIME/TIMBER входа (не только цвет в выводе, реальная
логика). `-AppOnly` для обоих новых форматов намеренно возвращает 0 (Pkg="") вместо угадывания
пакета - оба формата не несут поля пакета вообще, честный "fails closed" лучше тихого
false-positive совпадения всего подряд.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1350 (discovered here)
- **Scope:** `scripts/utils/search-log.ps1` (единственный тронутый файл).
- **Flavors:** N/A - dev-tooling скрипт, не часть приложения.

---

## 4. Проверка

Regex для обоих новых форматов провалидирован построчно на 4 реальных файлах перед правкой кода
(`pwsh`-скрипт против `-match`, не догадка):

- `temp/S1350/run_20260802_1030.log` (THREADTIME): 26810/26812 матчей, 2 непойманных - ожидаемые
  `beginning of main/system` разделители.
- `temp/S0704_run_20260626_1020.log` (TIME): 8157/8159.
- `temp/S0671_run_20260626_1054.log` (TIME): 4984/4986.
- `temp/S0686_run_20260626_1436.log` (TIME): 10644/10646.

После правки - функциональная проверка тем же CLI, что использует владелец:

- `-Summary` на всех 4 файлах: `[FORMAT: THREADTIME]`/`[FORMAT: TIME]` вместо `[FORMAT: LOGCAT]` с
  0 structured, level distribution и top tags заполнены корректно.
- `-Pattern "fastmediasorter"` на `run_20260802_1030.log`: 1340 совпадений (было 0).
- `-Errors -Count` на `run_20260802_1030.log`: 736 (было 0, воспроизводит §1 симптом дословно).
- `-Tag "CompositionEngine" -Count`: 58 - подтверждает тег с паддингом парсится верно.
- Строка с встроенным двоеточием в самом теге (`binder:717_C: type=1400 ...`) парсится как один тег
  `binder:717_C`, не рвётся на первом двоеточии - видно в `-Summary` warnings-выводе.
- `-AppOnly -Count` на THREADTIME-файле: 0 (документированное ограничение - формат не несёт поля
  пакета, честный fail-closed, не тихий false-positive).
- Регрессия: синтетические Format 1 LOGCAT и Format 3 TIMBER сэмплы (`temp/S1353/sample_*.log`)
  по-прежнему определяются как `[FORMAT: LOGCAT]`/`[FORMAT: TIMBER]` и парсятся 2/2 строк каждый -
  новые форматы не перехватывают старые (структурно непересекаются по длине года).

Debug verification tags: не применимо - `.ps1`-инструмент, не `.kt`, вне Sxxxx-tag lifecycle
(CLAUDE.md "Debug Verification Tags" область - `.kt` only).

---

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact bugfix - no tactical folder)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 2

### Manual / on-device

None - `.ps1` dev-tooling fix, not app code; no build/device gate applies. FEATURES trilingual and
debug-tag invariant both EXEMPT - internal developer tool, no `.kt` touched, no user-visible capability.
