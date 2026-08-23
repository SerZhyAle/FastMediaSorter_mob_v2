# Спецификация (compact bugfix): S1978 - MODULE_SELECTION.md предлагает запустить скрипт, которого нет

**Ticket:** S1978
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-23
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-23

**Захвачено во время:** S1951 (сплошной обход документов, заявляющих Wear-компаньон у флейвора `legacy`).

**Текст:**

`docs/MODULE_SELECTION.md` строки 31-35 инструктируют читателя запустить
`.\scripts\select-wear-module.ps1` для интерактивного выбора модуля.

Файла `scripts/select-wear-module.ps1` в репозитории нет - проверено `ls` 2026-08-23, а также
`Glob "**/select-wear-module.ps1"` в ходе того же обхода: ни одного совпадения нигде в дереве.

То есть документ, зарегистрированный в реестре как руководство, даёт команду, которая просто
не выполнится. Нужно выяснить, был ли скрипт переименован, удалён или не существовал никогда,
и затем либо восстановить его, либо заменить блок на реально работающий способ выбора модуля.

**Соседние тикеты (дедуп при захвате):** поиск по `select-wear-module`, `wear-module`,
`module-selection` в каталоге не дал ни одной записи.

---

## 1. Проблема / симптом

Дефект шире, чем одна строка в одном документе. Расследование 2026-08-23:

**1.1 Несуществующий скрипт - 13 вхождений в трёх документах.** `grep -rn "select-wear-module"`:

- `docs/MODULE_SELECTION.md` - строки 34, 61, 172, 181, 214 (5)
- `docs/WEAR_OS_BUILD_CONFIG.md` - строки 43, 49, 60, 151, 172, 226 (6)
- `docs/WEAR_OS_QUICK_START.md` - строки 13, 55 (2)

`ls scripts/select-wear-module.ps1` -> `No such file or directory`. `find . -maxdepth 4 -name
"select-wear-module*"` -> пусто.

**1.2 Run-конфигурации, описанные как артефакты репозитория, в репозитории отсутствуют.**
`MODULE_SELECTION.md` приводит таблицу из 12 файлов (`wear__Debug_.xml`, `wear__Release_.xml`,
`app__standardDebug_.xml` .. `app__noLegalRelease_.xml`), `WEAR_OS_BUILD_CONFIG.md` секция 3 и
`WEAR_OS_QUICK_START.md` «Configuration Files» - те же два wear-файла. Фактически
`ls .idea/runConfigurations/` возвращает **один** файл: `app_v2_standardRelease.xml`. Ни одного из
двенадцати названных нет.

**1.3 Заявленный дефолт модуля не установлен нигде.** Три документа утверждают, что
«Wear OS [Debug] is set as the startup module in `.idea/runConfigurations.xml`» и предлагают
править в нём атрибут `selected`. Фактическое содержимое файла - один компонент
`RunConfigurationProducerService` со списком `ignoredProducers`. Ни компонента `RunManager`, ни
атрибута `selected` в файле нет. Инструкция «Option 3: Edit XML Configuration» правит то, чего не
существует.

**1.4 Пути к скриптам сборки указаны мимо.** `WEAR_OS_BUILD_CONFIG.md` секция 5 объявляет
`build-wear-debug.PS1` доступным в `scripts/`, `MODULE_SELECTION.md` «Best Practices» пункт 2
называет `.\scripts\build-*.ps1`. Реальное расположение - `scripts/builders/`.

**1.5 Сломанная разметка.** В `WEAR_OS_BUILD_CONFIG.md` блок «Wrong module selected» открывает
powershell-фенс и не закрывает его: `grep -c '^```'` возвращает **13** - нечётное число. Всё, что
идёт после этого блока до конца файла, рендерится как код.

**1.6 Четвёртый документ, найденный гейтом реестра.** Гейт `document-registry` в `post-change.ps1`
при первом закрытии назвал соседей по записям `user-guides` и `wear-dev-docs`. Проверка соседей
дала одно попадание того же класса: `docs/WEAR_OS_SETUP.md` строка 60 - «These are defined in
`.idea/runConfigurations/` and should appear in Android Studio's configuration dropdown», плюс
секция «Run Configurations», обещающая две преднастроенные конфигурации. Остальные соседи
(`WEAR_OS_IMPLEMENTATION_STEPS.md`, `WEAR_OS_ROADMAP.md`, `WEAR_OS_STATUS.md`, публичные
руководства) чисты - проверено grep по `select-wear-module`, `runConfigurations`, `startup module`,
`set as default` и по путям `scripts/build-*`.

---

## 2. Корневая причина

Каталог `.idea/` внесён в `.gitignore` (строка 56). Значит `.idea/runConfigurations/*.xml` не
попадают ни в один клон: у любого читателя, кроме автора документов на его собственной машине
в тот день, этих файлов нет и не могло быть.

Три документа - это отчёт «что я только что настроил у себя», записанный 27 января 2026 года
(`**Date**: January 27, 2026` в шапке `WEAR_OS_BUILD_CONFIG.md`) в форме руководства. Его
предмет - локальное, неверсионируемое состояние IDE. Ни один пункт из 1.2 и 1.3 никогда не был
контрактом, на который читатель мог опереться: они описывают машину, а не репозиторий. Скрипт из
1.1, судя по отсутствию любого следа в дереве, относится к той же категории - обещание из
отчёта, а не удалённый позже файл.

Отсюда же и 1.4: отчёт писался до того, как сборочные скрипты переехали в `scripts/builders/`, и
не переписывался ни разу с тех пор.

Направление проверки «документ ссылается на несуществующий скрипт» не покрыто ни одним гейтом -
существующий `scripts/quality/assert-script-references.ps1` (S1872) проверяет обратное. Поэтому
13 неисполнимых команд прожили в трёх зарегистрированных документах до сплошного обхода руками.
Гейт запаркован отдельным тикетом - см. 3.3.

---

## 3. Исправление

Скрипт не восстанавливается: восстанавливать нечего, а интерактивный выбор модуля, переписывающий
gitignore-нутый `.idea/`, боролся бы с самой Android Studio, которая этот файл переписывает сама.
Вместо этого каждый из четырёх документов приводится к тому, что в репозитории действительно есть и
действительно версионируется:

- удалить все 13 ссылок на `select-wear-module.ps1`;
- заменить повествование о дефолтном модуле и о правке `.idea/runConfigurations.xml` на прямое
  утверждение, что `.idea/` в `.gitignore`, репозиторий не поставляет никакой run-конфигурации, а
  выбор в выпадающем списке Android Studio - локальное решение каждого разработчика;
- удалить таблицы несуществующих файлов `.idea/runConfigurations/`;
- указать реальные точки входа: цели `a.ps1` (`fw`, `fwr`, `fwu`), `scripts/builders/build-wear-debug.PS1`,
  `scripts/builders/build-wear-release.PS1`, gradle-таски `:wear:assembleDebug` и `:wear:assembleRelease`;
- поправить пути `scripts/` -> `scripts/builders/`;
- закрыть незакрытый code fence в `WEAR_OS_BUILD_CONFIG.md`.

Содержательные части, проверенные и верные, не трогаются: размеры APK с датой замера (S1679),
`applicationId` и namespace (S1681), парность версий модулей, minSdk 28, KSP вместо KAPT,
группировка настроек часов - всё это подтверждено чтением `wear/build.gradle.kts` 2026-08-23.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1951 (обнаружено при обходе), S0403 (флейворные наборы источников wear),
  S1979 (запаркован здесь: гейта на ссылку «документ -> несуществующий скрипт» нет),
  S1980 (запаркован здесь: `MODULE_SELECTION.md` - документ разработчика, опубликованный как
  руководство пользователя)

---

## 4. Проверка

- `grep -rn "select-wear-module" docs/` -> 0 вхождений.
- `grep -rn "runConfigurations/wear__"` по `docs/` -> 0 вхождений.
- `grep -c '^```' docs/WEAR_OS_BUILD_CONFIG.md` -> чётное число.
- Каждый путь `.ps1`, оставшийся в четырёх документах, резолвится в существующий файл.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` -> exit 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` -> exit 0.
- `post-change.ps1 -ChangeType Doc` -> `post-change: PASS`.

---

## Phase 01 - Привести wear-документы к состоянию репозитория

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Steps done:** 5 / 5

### Objective

Убрать из wear-документов всё, что описывает несуществующий скрипт и неверсионируемое состояние
IDE, заменив реальными точками входа.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/MODULE_SELECTION.md` | Modified | ≤ 250 |
| `docs/WEAR_OS_QUICK_START.md` | Modified | ≤ 250 |
| `docs/WEAR_OS_BUILD_CONFIG.md` | Modified | ≤ 250 |
| `docs/WEAR_OS_SETUP.md` | Modified | ≤ 250 |

### Steps

#### Step 01.1 - Переписать MODULE_SELECTION.md

**Files:** `docs/MODULE_SELECTION.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Удалить пять ссылок на `select-wear-module.ps1` вместе с секциями «Quick Selection (Interactive)»
> и «Option 1: Interactive Script». Удалить «Option 3: Edit XML Configuration» и обе таблицы файлов
> `.idea/runConfigurations/`. Заменить секцию «Default Configuration: Wear OS» абзацем о том,
> что `.idea/` в `.gitignore` и репозиторий не задаёт дефолтный модуль. В «Best Practices» и
> «Command Reference» исправить пути на `scripts/builders/` и добавить цели `a.ps1` `fw`, `fwr`, `fwu`.

**Why:**

Документ зарегистрирован в реестре как руководство и опубликован с `indexable: true`, то есть
предлагается читателю как исполнимая инструкция, тогда как четыре из пяти его команд выбора модуля
не выполнятся ни в одном клоне (пункты 1.1-1.3).

**Verification:**

- `grep -c "select-wear-module" docs/MODULE_SELECTION.md` -> 0.
- `grep -c "runConfigurations/wear__\|app__standardDebug_" docs/MODULE_SELECTION.md` -> 0. Одно
  упоминание `.idea/runConfigurations/` остаётся намеренно - это фраза, объясняющая, что каталог
  в `.gitignore`, а не ссылка на файл.
- `grep -n "scripts/builders/" docs/MODULE_SELECTION.md` -> совпадения есть.

**Status:** `[x]` done - 2026-08-23. Проверено: 0 / 0 / 7 совпадений, фенсы 8 (чётно).

---

#### Step 01.2 - Переписать WEAR_OS_QUICK_START.md

**Files:** `docs/WEAR_OS_QUICK_START.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Удалить «Step 0: Configure Wear Module» и блок «Switch Between Modules» с вызовом скрипта.
> В «Wear OS is Set as Default» снять утверждение о предустановленном дефолте. В таблице
> «Configuration Files» убрать две строки с файлами `.idea/runConfigurations/`.

**Why:**

Документ обещает пятиминутный старт и первым же шагом даёт команду, которая завершится ошибкой,
а затем сообщает, что при её пропуске дефолт всё равно установлен - чего в репозитории нет (1.3).

**Verification:**

- `grep -c "select-wear-module" docs/WEAR_OS_QUICK_START.md` -> 0.
- `grep -c "wear__Debug_" docs/WEAR_OS_QUICK_START.md` -> 0.

**Status:** `[x]` done - 2026-08-23. Проверено: 0 / 0, фенсы 12 (чётно).

---

#### Step 01.3 - Переписать WEAR_OS_BUILD_CONFIG.md

**Files:** `docs/WEAR_OS_BUILD_CONFIG.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Удалить шесть ссылок на скрипт, секцию «4. Module Selection Setup», таблицу run-конфигураций из
> «3. Android Studio Run Configurations» и соответствующие галочки в «Verification Checklist».
> В «5. Build Scripts» исправить `scripts/` на `scripts/builders/`. Закрыть незакрытый code fence
> в блоке «Wrong module selected». Проверенные факты - размеры APK, `applicationId`, парность
> версий, minSdk, KSP - сохранить дословно.

**Why:**

Это первоисточник фикции: секции 3 и 4 объявляют созданными артефакты, которых нет, а незакрытый
fence ломает рендер всей оставшейся части файла (1.5).

**Verification:**

- `grep -c "select-wear-module" docs/WEAR_OS_BUILD_CONFIG.md` -> 0.
- `grep -c '^```' docs/WEAR_OS_BUILD_CONFIG.md` -> чётное.
- `grep -c "10,808,958" docs/WEAR_OS_BUILD_CONFIG.md` -> не ноль (проверенный факт на месте; в
  документе он стоит дважды - в блоке размеров и в таблице метрик, как и до правки).

**Status:** `[x]` done - 2026-08-23. Проверено: 0, фенсы 12 (чётно), факт размера на месте (2).

---

#### Step 01.4 - Закрыть изменения через реестр и фасад

**Files:** `dev/CHANGELOG.md` (через скрипт)
**Depends on:** Step 01.3

**Prompt for developer:**

> Прогнать `scripts/document_registry/validate.ps1` и `generate.ps1 -Check`, затем закрыть весь
> набор из трёх файлов одним вызовом `post-change.ps1` с `-Files`, `-ScopeToFile` и `-ChangeType Doc`.

**Why:**

Все три файла зарегистрированы в `docs/DOCUMENT_REGISTRY.jsonl`, а `MODULE_SELECTION.md` ещё и
публикуется в sitemap, поэтому изменение обязано пройти валидацию реестра перед закрытием.

**Verification:**

- `validate.ps1` -> exit 0.
- `generate.ps1 -Check` -> exit 0.
- `post-change.ps1` -> `post-change: PASS`.

**Status:** `[x]` done - 2026-08-23. `validate.ps1` -> `Document registry PASS: 36 record(s)`,
exit 0. `generate.ps1 -Check` -> `Generated document views are current.`, exit 0. Первый прогон
`post-change.ps1` вернул `PASS WITH ADVISORIES (1)`: гейт `document-registry` потребовал проверить
соседей - см. Step 01.5.

---

#### Step 01.5 - Починить соседа, названного гейтом реестра

**Files:** `docs/WEAR_OS_SETUP.md`
**Depends on:** Step 01.4

**Prompt for developer:**

> Гейт `document-registry` при закрытии назвал соседей по записям `user-guides` и `wear-dev-docs`.
> Проверить их на тот же класс дефекта и починить попадания. В `WEAR_OS_SETUP.md` заменить секцию
> «Run Configurations», обещающую две преднастроенные конфигурации в `.idea/runConfigurations/`, на
> утверждение, что репозиторий не поставляет ни одной, и добавить шаг синхронизации Gradle в
> инструкцию «Via Android Studio».

**Why:**

Починить три документа и оставить четвёртый с тем же обещанием - значит оставить читателю ровно тот
же тупик одним кликом дальше по ссылке; `WEAR_OS_SETUP.md` линкуется из обоих исправленных
wear-документов (пункт 1.6).

**Verification:**

- `grep -c "runConfigurations" docs/WEAR_OS_SETUP.md` -> 0.
- `grep -c "wear \[Debug\]" docs/WEAR_OS_SETUP.md` -> 0.
- `grep -c '^```' docs/WEAR_OS_SETUP.md` -> чётное.

**Status:** `[x]` done - 2026-08-23. Проверено: 0 / 0, фенсы 16 (чётно). Повторный
`post-change.ps1` с набором из четырёх файлов и `-RegistryAck 'user-guides,wear-dev-docs'` ->
`post-change: PASS`, exit 0.

---

### Phase Done Criteria

- [x] Каждый шаг `Step 01.*` помечен `[x] done`.
- [x] Сборка не требуется - изменения только в `docs/*.md`.
- [x] Запись в dev log добавлена через `post-change.ps1`.

### Rollback Plan

Revert phase commit - изменения затрагивают только документацию, ни данных, ни пользовательских
поверхностей.

---

## Last Audit

**Date:** 2026-08-23
**Mode:** strategic (no tactical folder)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 1

Checks run: four target files exist; `select-wear-module` 0 hits across `docs/`; phantom
run-configuration filenames 0 hits across `docs/`; code fences even in all four files (8 / 12 / 12 / 16);
zero `Timber.d("S1978:` tags in `.kt` (status is not `BlockNeedUserTest`); dev-log entry present;
`check-open-items-carried.ps1` exit 0 (no research section); no dangling anchor into a removed
section; `document_registry/validate.ps1` exit 0 and `generate.ps1 -Check` exit 0; no residual
default-module claim anywhere in `docs/`; every internal `.md` link in the four files resolves;
all 16 distinct `.ps1` paths remaining in the four files resolve to real files;
`post-change.ps1 -ChangeType Doc` -> `post-change: PASS`, exit 0.

EXEMPT: FEATURES trilingual - the spec has no section 8 and the change ships no user-visible
capability, so `docs/ALL_FEATURES.jsonl` correctly carries no record for S1978.

### Notes (outside the verification contract)

- `dev/CHANGELOG.md` carries two rows for this ticket, 57 seconds apart. The first closure returned
  `PASS WITH ADVISORIES` from the `document-registry` gate; the re-run after fixing the sibling
  reworded its `-Description` from "three wear docs" to "four wear docs", and the dev-log
  duplicate guard keys on that string. Both rows are truthful records of the two runs.
  `dev/CHANGELOG.md` may not be hand-edited and `add_to_dev_log.ps1` has no remove verb, so this
  is left as-is rather than repaired.

### Parked during this ticket

- `S1979` - no gate covers the direction "a document references a `.ps1` that does not exist";
  the existing `assert-script-references.ps1` (S1872) checks the reverse.
- `S1980` - `docs/MODULE_SELECTION.md` is a developer document published as a public indexable
  user guide, while its three siblings are deliberately unpublished (S1801 ADR-2).
