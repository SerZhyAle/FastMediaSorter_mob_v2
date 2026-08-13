# S1578 - У adb.ps1 нет verb для вытягивания файлов с устройства

**Status:** Archived
**Priority:** 45
**Created:** 2026-08-11

## 0. Исходный материал (verbatim)

Обнаружено при device-тесте S1457 на RFCR110NBQJ (Galaxy S21+), 2026-08-11.

Сообщение исполнителя:

> **Tooling gap:** `scripts/devtest/adb.ps1` has no `pull` (nor `push`) verb, so any test that has to
> judge a file the app wrote - a captured photo, a recording, an exported log - has to leave the wrapper
> and call `adb.exe` directly. Worse, doing that from the Bash tool silently corrupts the remote path:
> MSYS rewrites `/sdcard/DCIM/Camera/x.jpg` into `C:/Program Files/Git/sdcard/DCIM/Camera/x.jpg` and adb
> answers `failed to stat remote object`. The working form is
> `MSYS2_ARG_CONV_EXCL='*' adb -s <id> pull <remote> <local>`, which nothing in the docs says. A `pull`
> verb inside `adb.ps1` would carry the device selection, the adb discovery and the exit contract that
> already exist there, and would sidestep the MSYS rewrite entirely because the value never passes
> through bash.

## 1. Симптом

`adb.ps1` покрывает почти весь ad-hoc device-цикл (`shot`, `log`, `install`, `shell`, `tap`), но забрать
файл с устройства им нельзя. Как только проверка требует посмотреть на сам файл, а не на UI, агент уходит
на голый `adb.exe` и теряет всё, что даёт обёртка: автопоиск adb вне PATH, `-DeviceId`, стабильные коды
выхода, единый формат сообщений.

## 2. Почему это важно

Правило 13 требует чинить недостаточные скрипты, а не обходить их. Обход здесь не просто неудобен - он
содержит ловушку: из Bash-инструмента путь `/sdcard/...` молча превращается в путь внутри установки Git,
и команда падает с сообщением про несуществующий объект, которое выглядит как отсутствие файла на
устройстве. Это тот же класс ошибки, что описан правилом 27 для значений аргументов со слешем.

## 3. Цель

Два новых verb'а в `adb.ps1` - `pull` и `push`, - которые наследуют выбор устройства, автопоиск adb,
JSON-контракт и таблицу кодов выхода уже существующей обёртки. После них ни один device-тест не обязан
звать `adb.exe` напрямую ради файла.

**Non-goals:**

- Синхронизация каталогов, рекурсивная выкачка дерева - `adb pull` умеет это сам, отдельного контракта тикет не заводит.
- Замена `prefs` и `shot`: у них свои источники (`run-as`, `screencap`) и они остаются как есть.
- Правки правила 27 и MSYS-ловушки как таковой - тикет её обходит, а не чинит.

## 4. Решения

- **Отсутствующий удалённый файл - отдельный код выхода.** `pull` проверяет наличие файла до вызова
  `adb pull` и отдаёт новый код **6**. Через 7 («adb вернул не ноль») этот случай неотличим от сбоя
  связи, а именно его device-тест встречает чаще всего.
- **Локальный путь по умолчанию - `temp/scratch/<имя файла>`** (правило 1 и уже существующий `Get-TempDir`).
  Имя не штампуется временем: тест обычно тут же читает файл, и предсказуемый путь важнее уникального.
- **`-Latest` разворачивает маску на самом устройстве.** `ls -1t` в шелле устройства сортирует по времени,
  поэтому маску раскрывает шелл устройства, а не PowerShell, и «свежий кадр» берётся одной командой.
- **`push` требует существующего локального файла** и падает с кодом 1 (плохие аргументы) - иначе ошибка
  всплывёт на устройстве и будет выглядеть как проблема устройства.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1183 (вывод verb'ов `adb.ps1` не перенаправляется - та же обёртка, другой дефект),
  S1457 (device-тест, на котором пробел обнаружен). Дедуп по `search.ps1` («adb pull», «push», «adb.ps1»)
  других тикетов на эту область не дал.

## 6. Критерии готовности

1. `pull` забирает файл с устройства в `temp/scratch/` без указания локального пути.
2. `pull -Latest` по маске каталога берёт самый свежий файл.
3. `pull` несуществующего пути выходит с кодом 6 и говорит, что именно не найдено.
4. `push` кладёт локальный файл на устройство, а отсутствующий локальный файл отвергает кодом 1.
5. Оба verb'а поддерживают `-Json` и `-DeviceId` наравне с остальными.
6. Заголовок скрипта, встроенная справка и шпаргалка скриптов перечисляют новые verb'ы и код 6.

---

## Implementation State

Реализовано 2026-08-11 в `scripts/devtest/adb.ps1`. Проверено на подключённом RFCR110NBQJ, каждый критерий
с кодом выхода:

1. `pull -Remote /sdcard/_s1578_probe.png` -> `PULLED .. (192900 bytes)`, exit 0, файл в `temp/scratch/`.
2. `pull -Remote "/sdcard/_s1578_*.png" -Latest` -> exit 0; `pull -Remote /sdcard/Download -Latest` -> exit 0.
3. `pull -Remote /sdcard/_no_such_file_S1578` -> exit 6.
4. `push` реального файла -> exit 0; `push -Local <несуществующий>` -> exit 1.
5. `-Json` и `-DeviceId` работают на обоих verb'ах (JSON-объект с `remote`/`file`/`size`).
6. `assert-exit-contract.ps1` exit 0, `assert-script-cheatsheet-sync.ps1` exit 0 после перегенерации.

Три случая, найденные только прогоном на устройстве, а не чтением кода:

- Одноэлементный листинг `ls` при индексации `[0]` отдаёт символ, а не строку - та же ловушка, от которой
  уже защищён `Select-Device`.
- «Самое свежее в каталоге» обязано отбрасывать подкаталоги (`ls -1pt` + фильтр по завершающему слешу),
  иначе `pull` тянет дерево и проверка «файл на месте» падает.
- Путь с пробелами и скобками (`Download/App (1).apk` - ровно то, что находит `-Latest`) обязан быть
  закавычен для шелла устройства, иначе `stat` читает его как несколько аргументов и файл «не существует».

## Phase 01 - pull/push verbs

### Step 01.1 - Add -Remote / -Local / -Latest parameters and the pull verb

**Files:** `scripts/devtest/adb.ps1`

**Prompt for developer:**

> Add `[string]$Remote`, `[string]$Local` and `[switch]$Latest` to the param block. Implement the `pull`
> verb: select the device, refuse a missing `-Remote` with exit 1, resolve the remote path (with `-Latest`,
> run `shell ls -1t <remote>` and take the first line), confirm it exists with `shell stat -c %s`, and exit
> 6 naming the path when it does not. Pull into `-Local` when given, else into `temp/scratch/<basename>` via
> `Get-TempDir`. Fail 7 when the pulled file does not land. Emit `id`, `remote`, `file` and `size` under
> `-Json`, and one `PULLED <local> (<n> bytes)` verdict line otherwise.

**Why:**

Without the verb every check that judges a file the app wrote leaves the wrapper for raw `adb.exe`, which
from the Bash tool silently rewrites `/sdcard/...` into a path inside the Git installation and reports it as
a missing remote object (section 2).

**Verification:**

- `Grep` - `'pull' {` matches once in the verb switch.
- `Grep` - `Fail 6` present in that verb.
- Run `pull` for a file known to exist on the connected device: exit 0, the named file exists locally.
- Run `pull` for `/sdcard/_no_such_file_S1578`: exit 6.

**Status:** `[x]` done

### Step 01.2 - Add the push verb

**Files:** `scripts/devtest/adb.ps1`

**Prompt for developer:**

> Implement `push`: require `-Local` and `-Remote`, refuse with exit 1 when either is missing or the local
> file does not exist, then run `adb push`. Emit `id`, `local`, `remote` under `-Json` and one
> `PUSHED <local> -> <remote>` verdict line otherwise.

**Why:**

The symmetric direction belongs in the same wrapper for the same reason as `pull` - a test that has to place
a fixture on the device otherwise reaches for raw `adb.exe` and meets the same path-rewrite trap (section 2).

**Verification:**

- `Grep` - `'push' {` matches once in the verb switch.
- Run `push` with a missing local path: exit 1.
- Run `push` of a real local file to `/sdcard/`, then `pull` it back: both exit 0.

**Status:** `[x]` done

### Step 01.3 - Document the verbs in the header, the help verb and the cheatsheet

**Files:** `scripts/devtest/adb.ps1`, `docs/SCRIPTS_CHEATSHEET.md`

**Prompt for developer:**

> Add `pull` and `push` to the header verb list, to the exit-code table (code 6 - remote path not found), to
> the `help` verb's printed list and its `-Json` verb string, and add one `.EXAMPLE` for `pull -Latest`.
> Re-render the script cheatsheet through its generator rather than hand-editing it.

**Why:**

A verb absent from the header and from `help` is a verb nobody finds, which is how this gap survived, and the
exit-contract gate requires the header to list every code the script actually returns (section 6.6).

**Verification:**

- `Grep` - `pull` present in the header verb list and in the `help` verb output block.
- `Grep` - `6 - ` present in the header exit-code table.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0.

**Status:** `[x]` done
