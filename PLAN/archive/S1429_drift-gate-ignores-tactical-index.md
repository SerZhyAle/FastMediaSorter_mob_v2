# Стратегическая спецификация: S1429 - Дрейф-гейт /spec-next не смотрит в тактический INDEX

**Ticket:** S1429
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-06
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка 2026-08-06
**Tactical spec:** `PLAN/S1429_drift-gate-ignores-tactical-index/` (будет создан через `/spec-tech`)

<!-- auto-approved by /spec-all - 2026-08-09 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-06

**Захвачено во время:** S1401 (раунд 2 сессии `/spec-do`)

**Текст:**

Stage 3 of /spec-next (the drift gate) defers any In Progress ticket that has a tactical folder as soon as drift-check reports code markers, because the gate accepts only two proofs that the landed work is accounted for: a `## Last Audit` block or an `Implementation State` block in the STRATEGIC spec. It never looks at the tactical INDEX, which is where a Tier-3 ticket actually records phase-level progress ("Phases: 1 / 7 done", per-phase status and step counters). Observed 2026-08-06 on S1401 launcher-all-apps-screen: drift verdict DRIFT from 1 commit (6d298a36) and 3 code markers in AndroidManifest.xml, dimens.xml and DeferredStartupWorker.kt - all of which the INDEX already accounts for as Phase 01 Done 6/6 and Phase 02 In Progress 5/6. The gate's own stated purpose is "how much is already in code is unknown", which is false whenever a live tactical INDEX exists and /spec-dev recomputes its cursor from the phase files (and skips PRE-RESOLVED steps) rather than from memory. Consequence: the gate parks queue-line-3 of the current release package for a 3-day TTL on a condition that does not hold, and it will keep firing on every in-flight tactical ticket that has a commit since spec creation - i.e. on exactly the tickets that are being worked on. Candidate fix: let Stage 3 accept a tactical INDEX whose phase table is present and whose "Last updated" is not older than the newest in-window commit, as a third proof alongside Last Audit and Implementation State.

**Доказательство из того же раунда (preflight JSON, S1401):**

```json
"drift":{"verdict":"DRIFT","commits_count":1,"markers_count":3,
  "commits":[{"sha":"6d298a36","date":"2026-08-05 21:29:00 +0200"}],
  "code_markers":[
    {"file":"app_v2/src/main/AndroidManifest.xml","line":447},
    {"file":"app_v2/src/main/res/values/dimens.xml","line":69},
    {"file":"app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt","line":57}]}
"last_audit_present":false, "tactical_folder":true
```

`PLAN/S1401_launcher-all-apps-screen/INDEX.md` в тот же момент: `**Phases:** 1 / 7 done`, `01 app-cache-schema ✅ Done 6/6`, `02 app-cache-sync 🚧 In Progress 5/6`, `**Last updated:** 2026-08-05`.

**Второе наблюдение, следующий же раунд той же сессии (S1178 launcher-system-status-widgets):**

```json
"drift":{"verdict":"DRIFT","commits_count":1,"markers_count":0,
  "commits":[{"sha":"6d298a36","date":"2026-08-05 21:29:00 +0200","subject":"2608052128"}],
  "code_markers":[]}
"last_audit_present":false, "tactical_folder":true
```

Это уже не «часть работы лежит в коде неучтённой», а ровно та форма, которую предыдущая сессия сама назвала ложноположительной в записи skip-cache по S1036 от 2026-07-31: «DRIFT from 1 in-window commit with markers_count=0 - the known false-positive shape». Гейт всё равно откладывает. Два тикета подряд из верхушки очереди текущего релизного пакета (строки 3 и 4 пакета 31) отложены за один прогон, и обе записи в skip-cache живут 7 дней, хотя правило Stage 3 предписывает 3 - у `skip-cache.ps1 -Action add` нет параметра TTL, срок зашит. То есть цена ложного срабатывания вдвое выше заявленной.

Отсюда вопрос шире исходной формулировки: гейт должен различать две разные ситуации, которые сегодня дают один вердикт - «есть незачтённые inline-маркеры в коде» (реальный риск дублирования работы) и «в окно попал коммит, помеченный этим id» (само по себе ожидаемо для любого тикета, над которым уже работали).

---

## 1. Проблема

- `drift-check.ps1` выдаёт один вердикт `DRIFT` на два разных факта: «в коде лежат неучтённые inline-маркеры» и «в окно попал коммит, упоминающий этот id». Строка решения буквально `if ($commits.Count -gt 0 -or $markers.Count -gt 0)`.
- Второй факт ожидаем для любого тикета, над которым уже работали, поэтому гейт откладывает именно те билеты, которые двигаются.
- Stage 3 принимает только два доказательства учтённости - `## Last Audit` и блок `Implementation State`, оба в стратегическом спеке, - и не смотрит в тактический INDEX, где Tier-3 тикет и ведёт учёт фаз.
- Цена измерена на живой очереди: на 2026-08-09 в skip-cache лежат шесть записей `drift-needs-review`, и каждая своими словами объясняет, что находка ложная.
- В том же прогоне `/spec-do` 2026-08-09 гейт подряд отложил S1459 и предложил к тому же отложить S1465 - обе с `markers_count=0`, обе из пакета 31.

---

## 2. Цели

1. Вердикт различает «есть неучтённые маркеры» и «есть только коммит с id», и Stage 3 перестаёт откладывать второе.
2. Живой тактический INDEX засчитывается как доказательство учтённости наравне с `Last Audit` и `Implementation State`.
3. Число ложных `drift-needs-review` в skip-cache падает до нуля на тех формах, которые сессии уже вручную признали ложными.

**Non-goals:**

- Не менять сам поиск маркеров и окно по датам в `drift-check.ps1` - они работают.
- Не трогать `skip-cache.ps1`: параметр `-Ttl` там уже есть, и жалоба §0 на зашитый срок устарела.
- Не ослаблять гейт там, где маркеры действительно есть и учесть их нечем.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Вердикт остаётся читаемым человеком в одну строку - его печатают в round verdict.
2. Существующие вызывающие не должны менять код выхода без нужды.

### 3.2 Жёсткие ограничения

- **Flavor:** не затрагивается - изменение целиком в `scripts/` и в тексте команды.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** чтение одного `INDEX.md` на выбранный билет, в пределах уже выполняемого прохода preflight.
- **Совместимость данных:** JSON `drift` расширяется полями, существующие поля не переименовываются.
- **Локализация:** не применимо - вывод инструмента, EN.
- **Доступность:** не применимо.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1401 (тикет, на котором находка наблюдалась), S1036 (запись в skip-cache, поставленная тем же гейтом 2026-07-31 и до сих пор не разобранная), S1394 (другой дефект `/spec-next`)
- **UI placement:** не требуется - изменение не имеет пользовательской поверхности.
- **Sensitive scope:** нет.

---

## 4. Контекст текущей архитектуры

- Вердикт вычисляет `scripts/spec_catalog/drift-check.ps1`: собирает коммиты с id в окне от даты создания спека и inline-маркеры `// Sxxxx:` в `app_v2/src`, затем ставит `DRIFT`, если непусто хоть одно из двух.
- `spec-next-preflight.ps1` не считает дрейф сам - вызывает `drift-check.ps1 -Format json` и вкладывает результат в `selected.drift`; поля `last_audit_present` и `tactical_folder` приходят из `preview.ps1`.
- Решение принимает Stage 3 команды `/spec-next`, то есть текст драйвера, а не скрипт: скрипты дают факты, команда даёт вердикт.
- Поэтому починка живёт в трёх местах: факт (`drift-check.ps1`), недостающий факт о тактическом плане (`spec-next-preflight.ps1`) и правило (`.claude/commands/spec-next.md`, Stage 3).

---

## 5. Предлагаемый подход

- Третий вердикт для формы «коммит есть, маркеров нет». Он не равен `CLEAN` - факт коммита сохраняется в отчёте, - но и не равен `DRIFT`, потому что в дереве нечего учитывать.
- Проверка свежести тактического плана как третье доказательство: план присутствует, таблица фаз читается, дата его последнего обновления не старше самого свежего коммита окна.
- Stage 3 переписывается на три ветки: нет неучтённых маркеров -> идти работать; маркеры есть и учтены любым из трёх доказательств -> идти работать; маркеры есть и учесть нечем -> откладывать, как сейчас.
- Коды выхода: форма «только коммит» не считается стоп-условием, поэтому наружу отдаёт тот же код, что и чистый случай, и `/spec-all` перестаёт зря переключаться в review-режим.

### 5.1 Основные столпы / модули

1. **Факт о дрейфе** - `drift-check.ps1` возвращает вердикт из трёх значений и сохраняет обе метрики.
2. **Факт о плане** - `spec-next-preflight.ps1` добавляет в payload блок о тактическом INDEX: есть ли он, что говорит счётчик фаз, когда обновлён и свежее ли это новейшего коммита окна.
3. **Правило** - Stage 3 в `.claude/commands/spec-next.md` принимает решение по этим фактам и называет в round verdict, какое доказательство сработало.

### 5.2 Потоки данных и событий

`drift-check.ps1` -> вердикт и метрики -> `spec-next-preflight.ps1` (плюс чтение тактического INDEX) -> `selected.drift` и `selected.tactical_index` -> Stage 3 -> работать или отложить.

### 5.3 Точки расширяемости

- Список доказательств учтённости - открытый: четвёртое доказательство добавляется одной веткой в Stage 3 и одним полем в payload.

---

## 6. Открытые вопросы / Research items

- Нет. Обе точки решения прочитаны в коде: строка вердикта в `drift-check.ps1` и текст Stage 3.

---

## 7. Риски

- Ослабление гейта: билет с реальными неучтёнными маркерами и свежим, но враньём INDEX будет взят в работу. Смягчение - свежесть считается по дате коммита, а не по факту наличия файла.
- Смена кода выхода `drift-check.ps1` затрагивает `/spec-all` 0a-drift; изменение направлено в сторону меньшего числа ложных переключений, но его надо назвать в тактическом плане явно.
- Гейт правит текст команды, который читают все сессии сразу - формулировка Stage 3 должна остаться однозначной.

---

## 8. Влияние на пользователя (docs/FEATURES)

- Без изменений. Инструмент разработки, пользовательской поверхности нет.

---

## 9. Архитектурные решения (ADR)

- Факты считают скрипты, вердикт выносит команда - существующее разделение сохраняется, новое поле не тянет решение в скрипт.
- «Только коммит» не приравнивается к «чисто»: факт печатается, но не останавливает работу.
- Свежесть тактического плана меряется датой относительно коммита, а не наличием файла - иначе доказательством станет любой давно брошенный план.

---

## 10. Связи с другими спеками

- S1401 - тикет, на котором находка наблюдалась.
- S1036, S1178, S1206, S1431, S1459 - записи в skip-cache, поставленные этим гейтом; после починки их стоит перепроверить.
- S1394 - другой дефект `/spec-next`, независимый.

---

## 11. Критерии готовности (strategic-level)

- `drift-check.ps1` различает три состояния и печатает их одной строкой.
- Payload preflight несёт факт о тактическом INDEX и его свежести.
- Stage 3 принимает три доказательства учтённости и называет сработавшее в round verdict.
- Прогон на билете с `markers_count=0` и одним коммитом не даёт `drift-needs-review`.
- Прогон на билете с неучтёнными маркерами и без всех трёх доказательств по-прежнему откладывает.

---

## Revision History

- 2026-08-06 - захват через `/spec-draft` во время S1401.
- 2026-08-09 - стратегический спек написан по прочтению `drift-check.ps1` и Stage 3; жалоба §0 на зашитый TTL снята как устаревшая (`skip-cache.ps1 -Ttl` существует). Статус Draft -> Approved.
