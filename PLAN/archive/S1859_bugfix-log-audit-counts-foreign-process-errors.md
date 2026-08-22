# Спецификация (compact bugfix): S1859 - Аудит лога засчитывает ошибки чужого процесса как actionable

**Ticket:** S1859
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-20
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-20

**Захвачено во время:** /spec-prerelease, шаг 4.1

**Текст:**

`prerelease-log-audit.ps1` reports actionable clusters that belong to another app's process, so the mandatory step 4.1 audit exits 1 on a run where FastMediaSorter logged nothing wrong.

Symptom (sweep 2026-08-20, log `temp/S0484/run_20260820_225641.log`, 137 MB, `-v threadtime`):

```
{"ok":true,"exitCode":1,"actionableCount":2,"benignCount":6,"toastCount":0,
 "actionable":[{"level":"E","tag":"A","count":2,"sample":"com.google.apps.tiktok.tracing.gc:"},
               {"level":"E","tag":"A","count":2,"sample":"(REDACTED) Trace %s timed out after %d ms. Complete trace: %s"}]}
```

Both clusters come from pid 1435, which is `com.google.android.googlequicksearchbox:interactor` (Google app voice-interaction service, confirmed with `ps -A -o PID,NAME` on the device). FastMediaSorter ran as pid 2716 in the same capture. `com.google.apps.tiktok.tracing` is Google's internal tracing framework - the app does not bundle it and cannot emit it.

The audit is documented (`.claude/reference/spec-prerelease.md` §4.1) as keeping app-process lines before clustering, so a foreign process should never have reached the actionable list at all. The likely reason it slipped through is the tag: these lines carry the one-character tag `A`, and the process filter appears not to hold for them.

Impact: step 4.1 is mandatory on every sweep and its exit 1 forces triage of findings that are not the app's. It also trains the reader to discount the audit's actionable list, which is the one signal the coarse verdict cannot supply - `prerelease-verdict.ps1` reported `actionableErrors: 0` for the same capture.

Not the same as the benign allowlist: `.claude/reference/spec-prerelease.md` §4.1 says an emulator-only benign cluster is an allowlist candidate, but that path silences a cluster by identity. Here the defect is attribution - a line from another pid must not be classified at all, whatever it says.

Fix direction (not researched): make the app-process filter authoritative before clustering in `scripts/devtest/prerelease-log-audit.ps1`, keyed on the resolved pid of the package under test rather than on tag or message shape, and prove it by re-running the audit against this same 137 MB capture, which is retained.

---


## 1. Проблема / симптом

Шаг 4.1 `/spec-prerelease` (`scripts/devtest/prerelease-log-audit.ps1`) отдаёт `exitCode: 1` и два actionable-кластера на прогоне, где приложение не залогировало ни одной своей ошибки.

Эвиденс - захват свипа 2026-08-20 (137 MB, `-v threadtime`); вердиктообразующие строки сохранены в тикете, `PLAN/S1859_bugfix-log-audit-counts-foreign-process-errors/evidence/capture-extract.txt`:

- `08-20 22:58:30.115  1435  1699 E A       : com.google.apps.tiktok.tracing.gc:` (строка 17420)
- `08-20 22:58:30.115  1435  1699 E A       : (REDACTED) Trace %s timed out after %d ms. Complete trace: %s`

Оба кластера принадлежат pid 1435 - `com.google.android.googlequicksearchbox:interactor`. Сам захват содержит объявления процессов приложения вида `Start proc <pid>:com.sza.fastmediasorter.debug/u0a263` (в этом прогоне их десятки - свип многократно перезапускал приложение), и ни один из этих pid не равен 1435.

Коарс-вердикт `prerelease-verdict.ps1` на том же захвате дал `actionableErrors: 0`, то есть два инструмента над одним логом разошлись в ответе.

Последствие: шаг 4.1 обязателен на каждом свипе, его `exit 1` заставляет триажить чужие находки и приучает читателя обесценивать actionable-список - единственный сигнал, которого коарс-вердикт не даёт.

## 2. Корневая причина

Аудит вообще не атрибутирует строку процессу. Оба его регекса разбирают колонку pid, но не захватывают её: threadtime-форма матчит `\d+\s+\d+` без групп, `-v time`-форма матчит `\(\s*\d+\)` без группы.

Документированное поведение "keeps only app-process lines" реализовано двумя денилистами тегов (`$systemTagHint`, `$foreignTagPatterns`) и парами tag+signature. Денилист перечислителен: любой тег, которого в нём нет, проходит как приложенческий. Односимвольный тег `A` в списках отсутствует, поэтому строки pid 1435 попали в actionable.

Расхождение с вердиктом объясняется тем же: `prerelease-verdict.ps1` считает ошибки через `search-log.ps1 -AppOnly`, а тот восстанавливает pid приложения из строк `Start proc <pid>:com.sza.fastmediasorter` и фильтрует по ним.

Готовый общий примитив уже есть - `Get-AppPidsFromLog` в `scripts/devtest/lib/adb-log-filter.ps1` (S1332), с префиксным матчем пакета, покрывающим release-id, `.debug` и `:sub`-процессы. Аудит его не использует.

## 3. Исправление

Сделать pid-атрибуцию авторитетным первым фильтром аудита, с явным откатом на текущую эвристику и с честным отчётом о том, какой режим отработал.

### Step 3.1 - Восстановить pid приложения из самого захвата

**Files:** `scripts/devtest/prerelease-log-audit.ps1`

**Prompt for developer:**

> Add a `-Package` parameter defaulting to `com.sza.fastmediasorter`. Dot-source `scripts/devtest/lib/adb-log-filter.ps1` and resolve the app process ids by pre-filtering the log with `Select-String -Pattern 'Start proc \d+:<package>'` and passing only those matched lines to `Get-AppPidsFromLog`. Keep the log read streaming - never load the whole file into an array.

**Why:**

Логи свипа достигают 137 MB, и pid приложения - единственный признак принадлежности строки, доступный в обоих форматах захвата; переиспользование `Get-AppPidsFromLog` не даёт третьей копии правила разбора `Start proc`.

**Verification:**

- `Grep` - `adb-log-filter.ps1` встречается в скрипте ровно один раз (дот-сорс).
- Запуск с `-Json` на `PLAN/S1859_bugfix-log-audit-counts-foreign-process-errors/evidence/capture-extract.txt` печатает ненулевой `appPidCount`.

**Status:** `[x]` done

### Step 3.2 - Отфильтровать строки по pid до кластеризации

**Files:** `scripts/devtest/prerelease-log-audit.ps1`

**Prompt for developer:**

> Capture the pid in both line regexes. When the resolved pid set is non-empty, drop every parsed line whose pid is outside it, before the tag denylists and before clustering. Keep two text-arm exceptions attributed to the app but logged by the system process: `ANR in <package>` and `Process: <package>`. When the set is empty, fall back to the existing tag-heuristic path unchanged.

**Why:**

Дефект в атрибуции, а не в содержании строки: строка чужого pid не должна классифицироваться вообще, чем бы она ни была, а откат нужен потому, что захват может начаться после старта приложения и тогда `Start proc` в нём отсутствует.

**Verification:**

- Запуск на `PLAN/S1859_bugfix-log-audit-counts-foreign-process-errors/evidence/capture-extract.txt` не содержит в `actionable` ни одного кластера с тегом `A`.
- `Grep` - `foreignTagPatterns` и `systemTagHint` остались в скрипте (эвристика сохранена для отката).

**Status:** `[x]` done

### Step 3.3 - Отчитаться о режиме атрибуции

**Files:** `scripts/devtest/prerelease-log-audit.ps1`

**Prompt for developer:**

> Add `attribution` (`pid` or `heuristic`) and `appPidCount` to the JSON object and to the human-readable header. In heuristic mode print a warning line naming the consequence: the actionable list may contain other processes' errors.

**Why:**

Чистый аудит в режиме эвристики и чистый аудит по pid - разные по надёжности утверждения, и без явного поля читатель не отличит одно от другого; `search-log.ps1` предупреждает о том же случае словом `UNFILTERED`.

**Verification:**

- `-Json` на реальном захвате содержит `"attribution":"pid"`.
- `-Json` на фикстуре без `Start proc` содержит `"attribution":"heuristic"`.

**Status:** `[x]` done

### Step 3.4 - Герметичный регресс-набор

**Files:** `scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1`, `scripts/devtest/prerelease-log-audit.tests/fixtures/logcat_foreign_pid_sample.txt`

**Prompt for developer:**

> Add a hermetic suite in the shape of `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1`: a recorded threadtime fixture carrying the app's `Start proc` line, one app-pid error, and the S1859 foreign-pid `E/A` pair. Assert the foreign cluster is absent, the app cluster is present, and a fixture without `Start proc` reports heuristic attribution. Exit 0 on all-pass, 1 otherwise.

**Why:**

Дефект пережил несколько правок аллоулиста (S0976, S1391, S1700), которые каждый раз затыкали симптом по идентичности кластера, - без исполняемого случая с чужим pid следующая правка так же не заметит, что фильтр по процессу отсутствует.

**Verification:**

- `pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1` завершается кодом 0.
- В выводе набора присутствует случай на heuristic-откат.

**Status:** `[x]` done

### Step 3.5 - Синхронизировать документацию шага 4.1

**Files:** `.claude/reference/spec-prerelease.md`

**Prompt for developer:**

> Rewrite the "keeps app-process lines" claim in §4.1 to state the actual rule: pid recovered from the capture's own `Start proc` announcements filters first, the tag heuristic runs only when no app pid could be recovered, and the audit reports which mode it used. Name the fallback's consequence for the reader.

**Why:**

Именно эта строка справочника утверждала, что чужой процесс не может попасть в actionable-список, и читатель шага 4.1 доверял ей при триаже.

**Verification:**

- `Grep` - `attribution` присутствует в §4.1.
- `Grep` - формулировка `keeps app-process lines` больше не встречается.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

## 4. Проверка

- `pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.ps1 -LogFile PLAN/S1859_bugfix-log-audit-counts-foreign-process-errors/evidence/capture-extract.txt -Json` - выжимка из захвата, на котором дефект пойман, сохранена в тикете; ожидаемый результат: `exit 0`, `"attribution":"pid"`, `"actionableCount":0`.
- `pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Script -ScopeToFile ..` - PASS.

Устройство не требуется: тикет не трогает ни одного файла, попадающего в APK.

## Last Audit

**Дата:** 2026-08-21 · **Режим:** /spec-code (устройство не использовалось) · **Вердикт:** Verified

Эвиденс:

- Прогон аудита на полном захвате свипа 2026-08-20 (137 MB, `-v threadtime`, расходный, поэтому в тикете лежит выжимка): `exit 0`, `"attribution":"pid"`, `"appPidCount":44`, `"actionableCount":0`, время 14 с. До правки на том же файле: `exit 1`, два actionable-кластера pid 1435 (JSON приведён дословно в §0).
- Воспроизводимо без него: `prerelease-log-audit.ps1 -LogFile PLAN/S1859_bugfix-log-audit-counts-foreign-process-errors/evidence/capture-extract.txt -Json` даёт `exit 0`, `"attribution":"pid"`, `"appPidCount":3`, `"actionableCount":0` - сохранённый ответ лежит рядом, в `evidence/audit-after-on-extract.json`.
- `scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1` - `passed: 15 | failed: 0`, exit 0. Три фикстуры: threadtime с `Start proc`, threadtime без него (откат), и `-v time`.
- Разбор строк уровня E, принадлежащих pid приложения в том же захвате: четыре тега - `EGL_emulation` (снят `$systemTagHint`), `SurfaceSyncGroup` (снят `$foreignTagPatterns`), `platform: Failed to open rendernode` и `cr_AndroidProtocolHandler` (оба benign). Ни одна реальная ошибка приложения не спрятана новым фильтром: `benignCount` 2 сходится построчно.
- Синтаксический разбор обоих скриптов - 0 ошибок.
- `document_registry/validate.ps1` PASS (36 записей), `generate.ps1 -Check` - виды актуальны.

Осознанные решения:

- Денилисты тегов оставлены и работают после pid-фильтра. Строка приложения под тегом вроде `SurfaceSyncGroup` по-прежнему снимается, как снималась до правки: расширение actionable-списка - отдельный вопрос, и делать его в тикете про атрибуцию значит менять два поведения одной правкой.
- Текстовое плечо (`ANR in <pkg>`, `Process: <pkg>`) освобождает строку только от pid-фильтра, не от денилистов.
- Откат на эвристику не убран: захват может начаться после старта приложения. Он объявлен в отчёте полем `attribution`, и регресс-набор фиксирует, что в этом режиме чужой кластер виден.
