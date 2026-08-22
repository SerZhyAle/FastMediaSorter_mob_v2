# Стратегическая спецификация: S1553 - Нет ChangeType для набора «файл сборки + скрипты», а Mixed гоняет котлиновые гейты по набору без единого .kt

**Ticket:** S1553
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-09
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - найдено при работе над S1496 2026-08-09
**Tactical spec:** `PLAN/S1553_post-change-changetype-gate-scope/` (будет создан через `/spec-tech`)

---

<!-- Draft only (/spec-draft): keep this section. /spec omits it entirely. -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-09

**Захвачено во время:** S1496

**Текст:**

post-change.ps1 has no ChangeType covering a changed set that spans a build file and repo scripts, and `Mixed` runs the full Kotlin gate battery even when the set contains zero .kt files. Found during S1496 phase 02, 2026-08-09. The set was wear/build.gradle.kts (Config) + scripts/builders/check-standard-fast.ps1 + a.ps1 (Script). Neither Config nor Script covers both, and `.claude/reference/spec-dev.md` §ChangeType selection says Mixed is only for "code plus strings", so Mixed was the only remaining option and also the wrong one. Under Mixed the run took 42 s and ended exit 2: [detekt-preflight] correctly reported "no .kt file in the changed set - nothing to check" and PASSed, and then [detekt-gate] ran :app_v2:detekt anyway, could not narrow ("the detekt report predates the changed files"), and failed on 15 weighted issues in BrowseDeleteManager.kt and StreamTilePackReader.kt - two files not in the changed set and not touched by that ticket. Two gates inside one facade run disagree about whether Kotlin is in scope, and the one that is right is not the one that decides the exit code. Workaround used: split the closure into two runs, -ChangeType Config for the build file and -ChangeType Script for the scripts, both PASS in ~3 s each. Worth fixing because exit 2 means "could not verify", so the caller cannot tell a real defect from a set that never contained Kotlin, and the cheap correct answer was already computed by detekt-preflight in the same process.

**Симптом:** закрытие набора «build-файл + скрипты» невозможно одним вызовом, а выбранный по остаточному принципу `Mixed` даёт exit 2 из-за чужого незакоммиченного WIP.

**Наблюдения 2026-08-09 (S1496 фаза 02):**

- Набор: `wear/build.gradle.kts`, `scripts/builders/check-standard-fast.ps1`, `a.ps1`. Ни одного `.kt`.
- `-ChangeType Mixed -ScopeToFile`: 42 с, exit 2.
- В одном прогоне: `[detekt-preflight] PASS - no .kt file in the changed set - nothing to check`, затем `[detekt-gate] FAIL - child exit code 2` с `assert-detekt: cannot narrow - the detekt report predates the changed files`.
- Упавшие находки принадлежат `BrowseDeleteManager.kt` и `StreamTilePackReader.kt` - файлам вне набора.
- Обходной путь: два прогона, `Config` для build-файла и `Script` для скриптов, оба PASS примерно за 3 с.

---

## 1. Проблема

`post-change.ps1` принимал набор из build-файла и PowerShell-скриптов, но не имел подходящего ChangeType для единого закрытия такого набора. Выбранный как запасной вариант `Mixed` запускал Kotlin-батарею по отсутствующим в наборе Kotlin-файлам, а `detekt-gate` затем мог упасть на чужом WIP. В результате один фасад одновременно сообщал, что Kotlin проверять нечего, и завершался кодом ошибки из-за несвязанного detekt-отчёта.

---

## 2. Цели

1. Дать одному вызову `post-change.ps1` корректный ChangeType для набора из build/config-файлов и репозиторных скриптов.
2. Определять применимость Kotlin- и ресурсных гейтов по фактическим расширениям файлов в наборе, а не только по метке ChangeType.
3. Сохранить полный набор Kotlin- и XML-проверок, когда соответствующие исходники действительно изменены.
4. Сделать результат закрытия честным: отсутствие Kotlin-файлов не должно запускать detekt и создавать ложный exit 2.

**Non-goals:**

- Не менять правила detekt, его baseline или существующие Kotlin-ошибки вне переданного набора.
- Не добавлять новый Gradle-задачу или отдельный процесс проверки для Tooling.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<не высказаны - находка агента>

### 3.2 Жёсткие ограничения

- Фасад обязан продолжать различать реальную находку (exit 1) и невозможность проверки (exit 2).
- `Mixed` остаётся выбором для действительно смешанного набора кода и строк/ресурсов.
- Кодовые, ресурсные и каталоговые гейты запускаются только при наличии соответствующего типа файла в полном изменённом наборе.
- Изменение относится только к инженерному tooling и не меняет поведение приложения или публичные строки.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S1496 - тикет, при работе над которым находка всплыла.

---

## 4. Контекст текущей архитектуры

`scripts/post-change.ps1` является фасадом механического закрытия: он получает один или несколько путей, выбирает применимые гейты, синхронизирует производные данные и пишет одну строку журнала только после успешного результата. До исправления применимость ряда исходниковых гейтов зависела от `ChangeType`, хотя полный набор путей уже был доступен фасаду.

Нормализованный набор `changedFiles` теперь служит единым источником решения о наличии JVM-исходников, XML-ресурсов и строковых ресурсов. Метка ChangeType задаёт семейство closure, а не выдумывает отсутствующий тип исходника.

---

## 5. Предлагаемый подход

1. Добавить `Tooling` как допустимый ChangeType для набора build/config-файлов и репозиторных скриптов.
2. Вычислять признаки Kotlin/Java и XML по полному нормализованному набору файлов.
3. Запускать source-specific гейты, включая detekt, только когда соответствующий признак истинен; документные pin-проверки остаются применимы к Tooling.
4. Зафиксировать выбор `Tooling` и правило фактического состава набора в справочниках вызывающих workflow.

---

## 6. Открытые вопросы / Research items

1. **Разделять ChangeType или выводить гейты из состава набора**
   - **Вопрос:** добавлять ли комбинированный ChangeType, или перестать выбирать батарею гейтов по метке и выводить её из фактических расширений файлов в наборе?
   - **Статус:** Resolved
   - **Решение:** добавлен `Tooling` для самого closure-семейства, а source-specific гейты выводятся из фактического состава полного набора. Это сохраняет ясный API для вызывающего кода и не запускает проверки без подходящего входа.
   - **Артефакт:** commit `709e634e`; `scripts/post-change.ps1`.

2. **Должен ли detekt-gate доверять detekt-preflight**
   - **Вопрос:** preflight в том же процессе уже вычислил, что котлиновых файлов нет; почему гейт не использует этот ответ?
   - **Статус:** Resolved
   - **Решение:** оба шага теперь используют общий факт наличия JVM-исходника в наборе. `detekt-preflight` может остаться полезной быстрой подсказкой для Kotlin/Mixed, но `detekt-gate` вообще не запускается без `.kt`/`.java`.
   - **Артефакт:** commit `709e634e`; свежий фасадный прогон 2026-08-14.

---

## 7. Риски

- Ошибка классификатора может пропустить гейт для реального исходника. Риск снижен единым нормализованным набором и проверкой полного трёхфайлового сценария.
- Слишком широкий Tooling мог бы отключить документные проверки. Они оставлены в маршрутизации Tooling и прошли свежий запуск.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - внутренняя инструментальная правка.

---

## 9. Архитектурные решения (ADR)

1. ChangeType выбирает семейство closure, а применимость source-specific гейта определяется составом изменённых файлов.
2. `Tooling` покрывает совместный набор build/config-файлов и репозиторных скриптов; `Mixed` не используется как общий запасной тип.

---

## 10. Связи с другими спеками

- S1496 - тикет, при котором находка всплыла.

---

## 11. Критерии готовности (strategic-level)

1. `Tooling` принимается фасадом и документирован для build/config + script набора.
2. В наборе без `.kt`/`.java` `detekt-gate` не запускается.
3. В том же наборе сохраняются применимые Tooling-проверки, включая doc pins, script cheatsheet и OSS notices.
4. Фасадный прогон завершается exit 0 без Kotlin-ложного отказа.

## Implementation State

- Реализация вошла в commit `709e634e` 2026-08-10.
- Фасад получил `Tooling`; вычисления `$isCodeChange` и `$isResourceChange` используют весь нормализованный набор, а `detekt-gate` зависит от `$isCodeChange`.
- Справочники выбора ChangeType обновлены вместе с реализацией.

## Last Audit

- **Дата:** 2026-08-14
- **Объём:** `scripts/post-change.ps1` и связанная документация выбора ChangeType.
- **Проверка реализации:** git blame подтверждает реализацию S1553 в commit `709e634e`; маршрутизация использует полный нормализованный набор и оставляет `Tooling` для документационных/tooling гейтов.
- **Свежая проверка:** `pwsh -NoProfile -File scripts/post-change.ps1 -Files "wear/build.gradle.kts,scripts/builders/check-standard-fast.ps1,a.ps1" -Target "S1553" -Description "reconciled mixed build and tooling gate routing" -ChangeType Tooling -ScopeToFile -RegistryAck "developer-operations"`.
- **expected:** exit 0, Tooling-гейты проходят, `detekt-gate` пропускается без JVM-исходника.
- **actual:** exit 0 за 4.9 s; doc pins, doc-pin drift, script-cheatsheet, OSS notices и device-profile matrix прошли, `detekt-gate` пропущен как неприменимый для Tooling.
- **Вывод:** критерии 1-4 выполнены; P0/P1 находок нет.
