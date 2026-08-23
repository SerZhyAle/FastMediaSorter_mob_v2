# Спецификация (compact bugfix): S1980 - MODULE_SELECTION.md - документ для разработчика, опубликованный как руководство пользователя

**Ticket:** S1980
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-23
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-23

**Захвачено во время:** S1978 (обход трёх wear-документов, ссылающихся на несуществующий скрипт).

**Текст:**

`docs/MODULE_SELECTION.md` описывает выбор модуля в Android Studio: выпадающий список Run
Configuration, файлы `.idea/runConfigurations/*.xml`, gradle-таски, скрипты сборки. Это документ
для того, кто собирает проект, а не для того, кто пользуется приложением.

В `docs/DOCUMENT_REGISTRY.jsonl` он лежит в записи `user-guides`: `"published": true`,
`"indexable": true`, `"audience": "user"`. То есть он уходит в sitemap и предлагается читателю,
который ищет, как добавить папку.

Его собственные соседи классифицированы противоположным образом. `WEAR_OS_QUICK_START.md`,
`WEAR_OS_SETUP.md` и `WEAR_OS_BUILD_CONFIG.md` - те же инструкции по сборке того же модуля -
лежат в записи `wear-dev-docs` с `"published": false`, `"indexable": false` и примечанием
«Deliberately not a public surface (S1801 ADR-2)». `MODULE_SELECTION.md` линкуется из
`WEAR_OS_QUICK_START.md` и линкует обратно, то есть это одна связка документов, разрезанная
между публичной и внутренней записями реестра.

Кроме того, у записи `user-guides` заявлены языки `en`, `ru`, `uk`, а у `MODULE_SELECTION.md`
нет ни `_RU`, ни `_UK` варианта - проверено `ls` 2026-08-23.

Решение здесь такое же по классу, как S1801 ADR-2, и требует владельца: либо документ переезжает
в `wear-dev-docs` (и перестаёт публиковаться), либо остаётся публичным и тогда должен получить
две локализации.

**Соседние тикеты (дедуп при захвате):** поиск по `publication`, `indexable`, `module-selection` в
каталоге не дал ни одной записи, кроме самого S1978.

---

## 1. Проблема / симптом

- `docs/MODULE_SELECTION.md` объявлен публичным пользовательским руководством: он перечислен точным путём в записи реестра `user-guides` (`docs/DOCUMENT_REGISTRY.jsonl`, строка 4) с `"published": true`, `"indexable": true`, `"audience": "user"`.
- Из-за этого его адрес объявлен поисковым системам: `sitemap.xml`, строка 286, `https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/MODULE_SELECTION.html` - одна из 85 объявленных страниц.
- Он предлагается читателю в списке пользовательских руководств «Detailed guides are available in multiple languages» в четырёх опубликованных файлах: `docs/README.md:264`, `docs/README_RU.md:321`, `docs/README_UK.md:319`, `README.md:312` - между «Program Limitations» и «Complete Feature List».
- Содержимое - инструкция для того, кто собирает проект: выпадающий список Run Configuration в Android Studio, панель Build Variants, `.idea/` в `.gitignore`, таргеты `a.ps1 fk/fkn/fr/fc/fu/fw/fwr/fwu`, скрипты `scripts/builders/*.ps1`, вызовы `gradlew.bat :app_v2:assemble*`. Ни один абзац не описывает действие, доступное пользователю приложения.
- Его собственный блок «For more details» ведёт на три документа, снятых с публикации по S1801 ADR-2 (`WEAR_OS_QUICK_START.md`, `WEAR_OS_SETUP.md`, `WEAR_OS_BUILD_CONFIG.md`), и на `../AGENTS.md`, который не публикуется вовсе.
- Запись `user-guides` объявляет языки `en`, `ru`, `uk`, а у файла нет ни `_RU`, ни `_UK` - проверено `ls` 2026-08-23 и повторно при разборе тикета.

---

## 2. Корневая причина

- Файл попал в `user-guides` не глобом, а отдельной точной строкой пути - то есть его туда внесли решением, а не побочным эффектом шаблона вроде `docs/HOW_TO*.md`.
- Классификация в реестре задаётся на уровне записи, а не файла: попав в `user-guides`, документ автоматически получил `audience: user`, публикацию, индексацию и заявку на три языка, ни одна из которых не соответствует его содержимому.
- S1801 ADR-2 уже вынес правило для этого класса документов («Module build instructions .. deliberately not a public surface»), но применил его только к файлам с префиксом `WEAR_OS_`. `MODULE_SELECTION.md` - тот же класс под другим именем, поэтому под правило не попал.

---

## 3. Исправление

### ADR-1. Документ переезжает в `developer-operations` и перестаёт публиковаться

Решение: убрать `docs/MODULE_SELECTION.md` из записи `user-guides` и внести в `developer-operations` (`published: false`, `indexable: false`, `audience: developer`, области `build` / `release` / `workflow`).

Почему именно так:

- Локализация на RU и UK отпадает как вариант: переводить на два языка инструкцию по `gradlew.bat :app_v2:assembleLiteDebug` для человека, который ищет, как добавить папку, - это удвоение объёма ради страницы, которую тот человек всё равно закроет. Причина, по которой запись требует трёх языков, - пользовательская аудитория; правильная правка снимает аудиторию, а не добавляет переводы.
- `sitemap_exclude` (третий механизм реестра, применённый в этой же записи к `HOW_TO_DEVELOP_AND_RELEASE_RU.md`) здесь не нужен: тот файл вынужденно живёт в `user-guides`, потому что его ловит глоб `docs/HOW_TO*.md`, и убрать его иначе нельзя. `MODULE_SELECTION.md` перечислен точным путём, поэтому удаляется строкой - без исключения, которое кому-то придётся перечитывать через год.
- `developer-operations`, а не `wear-dev-docs`: документ описывает сборку обоих модулей, включая пять флейворов `app_v2`, а `wear-dev-docs` объявляет `product_areas: ["wear"]`. Переезд туда исправил бы публикацию и испортил бы область. У `developer-operations` области `build`, `release`, `workflow` и триггер `workflow` - точное попадание.
- Front matter (`layout`, `title`, `permalink`) остаётся на месте: три соседа по S1801 ADR-2 свои сохранили, страница по-прежнему отдаётся по своему адресу и просто перестаёт объявляться. Директива `noindex` не добавляется по той же причине - ни один из четырёх документов этого кластера её не несёт.
- Ссылки из `WEAR_OS_*.md` на `MODULE_SELECTION.md` остаются: после правки обе стороны связки лежат во внутренних записях, и ссылка внутренняя на внутреннюю.

### Фаза 01 - Переклассификация и зачистка публичных ссылок

**Files touched:**

- `docs/DOCUMENT_REGISTRY.jsonl` - изменён
- `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md` - изменены
- `README.md` - изменён
- `docs/DOCS_MAP.md`, `sitemap.xml` - перегенерированы, руками не редактируются

#### Step 01.1 - Move the registry path from `user-guides` to `developer-operations`

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove `"docs/MODULE_SELECTION.md"` from the `paths` array of the `user-guides` record. Add it to the `paths` array of the `developer-operations` record. Give `developer-operations` a `notes` field naming this ticket and S1801 ADR-2 as the rule it follows. Change nothing else in either record - not the globs, not `sitemap_exclude`, not the language lists.

**Why:**

Публикация и аудитория задаются записью, а не файлом, поэтому единственный способ снять с документа заявку «пользовательское руководство на трёх языках» - перенести его в запись, которая ничего этого не объявляет.

**Verification:**

- `Grep` - `MODULE_SELECTION` встречается в `docs/DOCUMENT_REGISTRY.jsonl` ровно один раз.
- Это вхождение - в записи `developer-operations`, у которой `"published": false`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` завершается кодом 0.

**Status:** `[x]` done

#### Step 01.2 - Drop the guide link from the three published docs indexes

**Files:** `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the line `- [Module Selection Guide](MODULE_SELECTION.md)` from the English guide list in each of the three files. Leave the surrounding list untouched.

**Why:**

Эти три файла - опубликованный индекс документации (`/docs/README.html` и его переводы), и они предлагают документ в списке пользовательских руководств; пока ссылка там, переклассификация ничего не меняет для читателя, который приходит на сайт.

**Verification:**

- `Grep` - `MODULE_SELECTION` не встречается ни в одном из трёх файлов.
- `Grep` - строка `- [Complete Feature List](FEATURES.md)` по-прежнему присутствует в каждом.

**Status:** `[x]` done

#### Step 01.3 - Re-home the link in the root README

**Files:** `README.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete `- [Module Selection Guide](docs/MODULE_SELECTION.md)` from the user-guide list under "Detailed guides are available in multiple languages". Add the same link to the `## Build Instructions` section, where it addresses the reader who builds the project.

**Why:**

Корневой README читают и пользователи, и разработчики, поэтому ссылку надо не удалить, а переставить в тот раздел, чью аудиторию документ действительно обслуживает.

**Verification:**

- `Grep` - `MODULE_SELECTION` встречается в `README.md` ровно один раз.
- Номер строки этого вхождения больше номера строки заголовка `## Build Instructions`.

**Status:** `[x]` done

#### Step 01.4 - Regenerate the two derived artifacts

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/document_registry/generate.ps1`, then `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check`. Never hand-edit either output.

**Why:**

`sitemap.xml` - то место, где дефект виден снаружи: пока адрес объявлен в нём, страница остаётся предложенной поисковику независимо от того, что говорит реестр.

**Verification:**

- `Grep` - `MODULE_SELECTION` не встречается в `sitemap.xml`.
- Число `<url>` в `sitemap.xml` равно 84 (было 85).
- `docs/DOCS_MAP.md` показывает `docs/MODULE_SELECTION.md` в строке Developer Operations, а не User Guides.
- `generate.ps1 -Check` завершается кодом 0.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Решение владельца, блокирующее работу:** снято. Вопрос «переносить или локализовать» разрешён существующим правилом проекта, а не догадкой: S1801 ADR-2 постановил, что инструкции по сборке модуля не являются публичной поверхностью, и `MODULE_SELECTION.md` - инструкция по сборке модуля (§2, ADR-1). Владелец может решение отменить, но его ответ не требуется, чтобы работа началась.
- **Затронутые пользовательские строки:** нет.
- **Затронутые разрешения, платные сервисы, сетевые вызовы:** нет.
- **Затронутый UI:** нет.
- **Related tickets:** S1978 (нашёл при обходе), S1801 (ADR-2, которым wear-dev-docs сняты с публикации).

---

## 4. Проверка

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - код 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - код 0, дрейфа нет.
- `Grep` `MODULE_SELECTION` по `sitemap.xml` - ноль совпадений.
- `Grep` `MODULE_SELECTION` по `docs/README*.md` - ноль совпадений.
- `pwsh -NoProfile -File scripts/quality/assert-guide-coverage.ps1 -Gate` - код 0. Проверка обязательна: гейт строит корпус из глобов записи `user-guides`, поэтому изъятие файла уменьшает корпус и может вытолкнуть возможности за baseline. Замер до правки: `0 new undocumented, considered 787, baselined 27, guides 48`.
- Сборка не запускается: изменения только в документации и в реестре, ни одного файла, попадающего в APK.

<!-- auto-approved by /spec-all - 2026-08-23 -->

---

## Last Audit

**Date:** 2026-08-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 2

### Evidence

- `MODULE_SELECTION` встречается в `docs/DOCUMENT_REGISTRY.jsonl` ровно один раз, в записи `developer-operations` с `published=False`, `indexable=False`, `audience=developer`.
- `scripts/document_registry/validate.ps1` - код 0, `Document registry PASS: 36 record(s)`.
- `scripts/document_registry/generate.ps1 -Check` - код 0, `Generated document views are current`.
- `MODULE_SELECTION` в `sitemap.xml` - 0 совпадений; число `<url>` - 84 при 85 до правки.
- `docs/DOCS_MAP.md` показывает файл в строке Developer Operations, публикация `False`.
- `MODULE_SELECTION` в `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md` - 0 совпадений; строка `Complete Feature List` на месте во всех трёх.
- В `README.md` одно вхождение, строка 358, под заголовком `## Build Instructions` (строка 356).
- Ни один из соседей записей `site-landing` и `user-guides` (`index*.html`, `QUICK_START*`, `HOW_TO*`, `FAQ*`, `TROUBLESHOOTING*`, `LIMITATIONS*`, `SMB_SETUP_GUIDE.md`, `howto/*.md`) не ссылается на документ - `grep -l` пуст.
- `scripts/quality/assert-guide-coverage.ps1 -Gate` - код 0: `0 new undocumented, considered 787, baselined 27, guides 47`. Корпус сжался с 48 файлов до 47, ни одна возможность не выпала за baseline.
- `scripts/post-change.ps1 -ScopeToFile -ChangeType Doc` - код 0, `post-change: PASS`, ровно одна строка в `dev/CHANGELOG.md`.
- `scripts/spec_catalog/check-open-items-carried.ps1 -Id S1980` - код 0, открытых вопросов нет.
- `Timber.d("S1980:` в `.kt` - 0 совпадений, что и требуется вне `BlockNeedUserTest`.

**EXEMPT:** сборка (изменения не попадают в APK); трилингвальные `FEATURES*` (пользовательская возможность не менялась - документ и до, и после правки читает только тот, кто собирает проект).
