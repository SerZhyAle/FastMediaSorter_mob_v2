# Стратегическая спецификация: S1589 - Per-flavor быстрая проверка есть, но не документирована

**Ticket:** S1589
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-12
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при исполнении S1568 фазы 03, 2026-08-12

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

<!-- Draft only (/spec-draft): keep this section. /spec omits it entirely. -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-12

**Текст:**

`a.ps1` exposes debug build targets for exactly two of the six flavors: `d`/`db`/`dav`/`dq`/`cd` build standard, `nd` builds noLegal. There is no debug target for `lite`, `photos`, `legacy` or `vr` - `vr` and `nl` are release builders, and `r` is the release AAB. Found while executing S1568 phase 03, whose strategic §3.3 sign-off demanded "сборка всех вариантов, где живут флейворные ключи" after deleting 390 shipped string resources. That validation level is not reachable through the sanctioned tooling: `/spec-dev` and `/spec-all` both forbid invoking `gradlew` directly (CLAUDE.md section 9 and Rule 23 route every build through `a.ps1`), so a spec that asks for per-flavor build proof can only be closed by deferring the check or by breaking the rule. S1568 closed it by deferring, on the strength of a source-level argument: its scan reads every file under `app_v2/src` including the `lite`, `photos`, `legacy`, `vr` and every `*Disabled` source set, so no deleted key was referenced by anything those flavors compile. That reasoning is sound for S1568 and is not a substitute for the missing targets - the next ticket that touches flavor-visible resources will hit the same wall. Four flavors covering the `*Disabled` no-op source sets and `legacy`'s minSdk 23 have no cheap compile path at all today.

**Захвачено во время:** S1568

---

## 1. Проблема

**Исходная посылка тикета опровергнута проверкой 2026-08-13.** Быстрая проверка для всех шести флейворов уже существует: `scripts/builders/check-standard-fast.ps1` принимает `-Flavor` со значениями `Standard|NoLegal|Lite|Photos|Legacy|Vr` во всех режимах (`Code`, `Resources`, `CodeAndResources`, `Unit`, `Assemble`), берёт `temp/BUILD.LOCK` и вызывается через `a.ps1`, который пробрасывает хвост аргументов вербатимно (`& $scriptPath @scriptArgs @Rest`).

Доказательство - прогоны на прогретом демоне:

- `.\a.ps1 fc -Flavor Lite` - exit 0 (`compileLiteDebugKotlin` + `processLiteDebugResources`).
- `.\a.ps1 fc -Flavor Legacy` - exit 0, включая minSdk 23.
- `.\a.ps1 fc -Flavor Vr` - exit 0, компилирует `src/vr`.

Настоящий дефект - не отсутствие возможности, а её невидимость. `-Flavor` не упомянут ни в справке `a.ps1` (блок `.PARAMETER Command` перечисляет `fk`/`fkn` как две отдельные буквы и молчит про параметр), ни в `docs/DEV_OPS.md`, ни в `docs/BUILD_TEST_FAST_PATH.md`. Единственное упоминание - строка 583 автогенерируемого `docs/SCRIPT_CHEATSHEET.md`, то есть машинный дамп параметров, а не то место, куда смотрит исполнитель. Поэтому S1568 закрыл требование §3.3 рассуждением: команда была доступна, но её никто не мог найти.

Цена не в этой задаче, а в следующей: любой тикет, который трогает ресурсы или флейворные исходники, снова закроется рассуждением вместо сборки - не потому, что сборка невозможна, а потому, что она не документирована там, где её ищут.

## 2. Цели

1. Сделать существующий per-flavor путь видимым в трёх местах, куда исполнитель смотрит фактически: справка `a.ps1`, `docs/DEV_OPS.md`, `docs/BUILD_TEST_FAST_PATH.md`.
2. Не заставлять исполнителя выбирать между нарушением правила и недоказанной задачей.

**Non-goals:**

- Не менять релизные сборщики.
- Не заводить сборку под каждую комбинацию feature source set: интерес представляют шесть флейворов, а не их произведение.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Не выяснялось: тикет припаркован по ходу другой работы.

### 3.2 Жёсткие ограничения

- `gradlew` вызывается только из скриптов, которые берут `temp/BUILD.LOCK` (CLAUDE.md Rule 23).
- Одновременно идёт не больше одной сборки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1568 (где найдено), S1432 (очередь блокировки сборки).
- **Объём (владелец, 2026-08-13):** только документация. Код `a.ps1` и `check-standard-fast.ps1` не меняем - возможность уже есть и доказана. Новые буквенные таргеты и механический гейт на справку отклонены.
- **Затрагиваемые поверхности:** справка `a.ps1`, `docs/DEV_OPS.md`, `docs/BUILD_TEST_FAST_PATH.md`. `docs/SCRIPT_CHEATSHEET.md` не трогаем руками - он генерируемый.
- **UI:** не затрагивается.

## 6. Открытые вопросы / Research items

1. **Полная сборка или быстрая компиляция**
   - **Статус:** Resolved (2026-08-13, проверкой кода и прогонами).
   - **Ответ:** быстрой компиляции достаточно, и выбор уже сделан за нас: `-Mode CodeAndResources` линкует ресурсы и компилирует Kotlin, что и есть формулировка §11. Для случая, когда нужен именно APK, тот же скрипт даёт `-Mode Assemble` - отдельная работа не требуется.

2. **Один параметризованный таргет или четыре новых**
   - **Статус:** Resolved (владелец, 2026-08-13).
   - **Ответ:** только документирование параметра. Новые буквенные таргеты не заводим - `-Flavor` уже покрывает все шесть флейворов, а лишние буквы плодят алфавит ради доступного одним флагом.

## 11. Критерии готовности (strategic-level)

1. Для каждого из шести флейворов есть команда `a.ps1`, доказывающая, что ресурсы линкуются и Kotlin компилируется. **Выполнено до начала работ** - подтверждено прогонами Lite / Legacy / Vr с exit 0.
2. Команда берёт `BUILD.LOCK` и не требует прямого вызова `gradlew`. **Выполнено до начала работ** - `Enter-BuildLockOrExit` в `check-standard-fast.ps1`.
3. Справка `a.ps1` называет `-Flavor` и его допустимые значения.
4. `docs/DEV_OPS.md` и `docs/BUILD_TEST_FAST_PATH.md` показывают per-flavor вызов в разделе быстрых проверок.
5. Требование «собрать все затронутые варианты» разрешается командой из документации, без чтения исходника скрипта.

## Last Audit

**Дата:** 2026-08-13. **Вердикт:** Verified. PASS 5 / WARN 0 / FAIL 0.

Тикет закрыт не реализацией заявленного, а опровержением его посылки: заявленная «отсутствующая» возможность существовала до начала работ. Поставлена видимость.

**Критерий 1 - PASS.** Прогоны на прогретом демоне, все exit 0: `fc -Flavor Lite`, `fc -Flavor Legacy` (minSdk 23), `fc -Flavor Vr` (`src/vr`), `fc -Flavor Photos`. Standard и NoLegal покрыты штатными `fc`/`fkn`. Шесть из шести.

**Критерий 2 - PASS.** `Enter-BuildLockOrExit -Reason "check-standard-fast.ps1"` в начале скрипта; `gradlew` вызывается только изнутри лока.

**Критерий 3 - PASS.** `a.ps1` правлен в двух справках: comment-based блок (строки 19-25) и консольный вывод `Show-Usage` (строки 151-156). Вторая существеннее: `Get-Help` не читает первую, потому что шебанг `#!/usr/bin/env pwsh` в строке 1 лишает блок `<#` позиции первого элемента файла - предсуществующий дефект, не введённый этим тикетом и не входящий в его объём. Синтаксис проверен: `Parser::ParseFile` -> `parse-errors=0`; `a.ps1` без аргументов печатает справку.

**Критерий 4 - PASS.** `docs/DEV_OPS.md` - таблица таргетов, блок «PER-FLAVOR PROOF», пункт 5 лестницы валидации. `docs/BUILD_TEST_FAST_PATH.md` - новый случай 12 (KAPT сдвинут на 13) плюс строка в таблице дефолтов.

**Критерий 5 - PASS.** Путь замкнут: и лестница валидации в `DEV_OPS.md`, и случай 12 прямо называют требование «every affected variant» и разрешают его командой, отмечая, что `gradlew` напрямую не нужен.

**Сверх объёма:** `docs/BUILD_VS_RELEASE.md` дополнен строкой про `-Flavor` - это sibling записи реестра `developer-operations`, выявленный гейтом `document-registry`. `docs/SCRIPT_CHEATSHEET.md` не трогался: генерируемый.

**Закрытие:** `post-change.ps1 -Files <5> -ScopeToFile -ChangeType Tooling -RegistryAck 'developer-operations,quality-assurance'` -> `post-change: PASS`, exit 0. Реестр: `validate.ps1` PASS (29 записей), `generate.ps1 -Check` - актуально.

**Устройство не требуется:** изменения не затрагивают исполняемый код приложения, только справку лаунчера и документацию. Отладочные теги `Timber.d("S1589:` не вводились.
