# Стратегическая спецификация: S0848 - Ускорить post-change без потери gate coverage

**Ticket:** S0848
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-01
**Tactical spec:** `PLAN/S0848_post-change-runtime-optimization/` (будет создан через `/spec-tech`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01

**Текст:**

```text
/spec-draft Реально ускорить post-change можно, не убирая ни одного gate. Главная идея: сохранить coverage, но убрать лишние процессы и full-repo проходы.
Самые выгодные правки
Самый большой non-Gradle тормоз сейчас в [dev/CATALOG/scripts/scan.ps1 (line 149)](P:/ANDROID/FastMediaSorter_mob_v2/dev/CATALOG/scripts/scan.ps1:149): Get-LastTouched() внутри цикла по всем .kt ([line 193 (line 193)](P:/ANDROID/FastMediaSorter_mob_v2/dev/CATALOG/scripts/scan.ps1:193)) делает git log -1 на каждый файл. catalog-sync из [scripts/post-change.ps1 (line 220)](P:/ANDROID/FastMediaSorter_mob_v2/scripts/post-change.ps1:220) стоит перевести в incremental mode: обновлять только изменённые файлы и для остальных брать lastTouched из существующего JSONL. Гарантии не теряются, а каталог остаётся тем же по смыслу.
В [scripts/quality/assert-neuroslop.ps1 (line 30)](P:/ANDROID/FastMediaSorter_mob_v2/scripts/quality/assert-neuroslop.ps1:30) каждый child запускается отдельным pwsh; список детей на [line 56 (line 56)](P:/ANDROID/FastMediaSorter_mob_v2/scripts/quality/assert-neuroslop.ps1:56), запуск на [line 76 (line 76)](P:/ANDROID/FastMediaSorter_mob_v2/scripts/quality/assert-neuroslop.ps1:76). Это надо свести в один процесс: вынести детекторы в функции/модуль и прогонять их in-process. Coverage останется 1:1, а cold-start overhead уйдёт.
Под -ScopeToFile post-change сейчас смягчает verdict, но почти не сокращает работу. Для ratchet-gates надо добавить настоящий -ChangedFiles режим: считать дельту только по изменённым файлам, а не сканировать весь src/main. Для правил вида "count must not grow" это сохраняет гарантию полностью.
detekt из [scripts/post-change.ps1 (line 283)](P:/ANDROID/FastMediaSorter_mob_v2/scripts/post-change.ps1:283) надо не убирать, а запускать параллельно с lexical gates. Сейчас wall-clock = lexical + detekt; после этого станет примерно max(lexical, detekt).
В проекте уже есть [scripts/quality/assert-fast-gates.ps1 (line 17)](P:/ANDROID/FastMediaSorter_mob_v2/scripts/quality/assert-fast-gates.ps1:17) как промежуточный батч. Это хороший быстрый шаг для post-change, но не финальный: он уменьшит orchestration noise, однако сам всё ещё форкает дочерние процессы.
Приоритет внедрения
Incremental catalog_sync/scan и убрать per-file git log.
Параллелить detekt с остальными gates.
Переписать assert-neuroslop.ps1 на one-process execution.
Добавить -ChangedFiles в lexical gates и реально использовать его из post-change -ScopeToFile.
Если делать только одну правку первой, я бы начал с incremental catalog_sync: это самый чистый выигрыш с минимальным риском для гарантий. Если хочешь, могу сразу это реализовать.
```

---

## 1. Проблема

`scripts/post-change.ps1` - механическая точка закрытия каждого тикета - работает дольше необходимого из-за оркестрации, а не из-за самих проверок. Три источника лишней работы: (1) `catalog-sync` пере-сканирует весь модуль и вызывает `git log -1` на каждый `.kt` (сотни процессов git на один изменённый файл); (2) каждый lexical-гейт и каждый нейрослоп-детектор запускается отдельным процессом `pwsh` (cold-start × N); (3) медленный detekt (gradle) выполняется последовательно после lexical-гейтов, хотя от них не зависит. Замедляется каждое закрытие тикета, при этом ни одна проверка не избыточна.

---

## 2. Цели

1. Инкрементальный catalog-sync: при закрытии одного файла обновлять `lastTouched` только для изменённых файлов, переиспользуя значение из существующего JSONL для остальных - без per-file `git log`.
2. Параллельный detekt: запускать gradle-detekt одновременно с lexical-гейтами; wall-clock ≈ max(lexical, detekt) вместо суммы.
3. One-process neuroslop: прогонять детекторы нейрослопа в одном процессе, без форка `pwsh` на каждого ребёнка.
4. Реальный `-ChangedFiles` для ratchet-гейтов: считать дельту только по изменённым файлам вместо полного скана `src/main`, сохраняя гарантию «count must not grow».

**Non-goals:**

- Удаление или ослабление любой проверки - coverage остаётся 1:1.
- Ускорение самой сборки Gradle (`assemble*`).
- Изменение семантики каталога или формата JSONL.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Начать с инкрементального catalog-sync - самый чистый выигрыш при минимальном риске для гарантий.
2. Сохранить `assert-fast-gates.ps1` как промежуточный батч, но довести оркестрацию до in-process.

### 3.2 Жёсткие ограничения

- **Flavor:** без изменений (тулинг, не рантайм).
- **API level:** без API-специфики.
- **Wear OS:** затрагивается косвенно - `catalog_sync -Module wear` использует тот же `scan.ps1`.
- **Производительность:** цель - снизить wall-clock post-change; бюджет памяти не критичен.
- **Совместимость данных:** формат `dev/CATALOG/<module>.jsonl` неизменен; полный ре-скан (`catalog_sync` без `-ChangedFiles`) даёт тот же результат по смыслу, что и раньше.
- **Локализация:** не применимо (нет user-facing строк).
- **Доступность:** не применимо.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0720, S0826, S0844

---

## 4. Контекст текущей архитектуры

`post-change.ps1` - фасад, вызывающий цепочку дочерних скриптов, каждый отдельным процессом `pwsh`. `catalog_sync.ps1` вызывает `scan.ps1` (парсинг + git) и `render.ps1`. `scan.ps1` не знает, какие файлы изменились, поэтому пересчитывает `lastTouched` через `git log -1` для каждого файла. Часть гейтов форкается ещё и внутри (`assert-neuroslop.ps1` запускает 8 детей отдельными процессами - каждый `exit` иначе убил бы хост-оркестратор). detekt дергает gradle и запускается строго после lexical-гейтов. Без сигнала об изменённых файлах и без in-process оркестрации сократить работу нельзя, не трогая покрытие.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- **Сигнал изменённых файлов** (`-ChangedFiles`), протянутый от `post-change.ps1` через `catalog_sync.ps1` в `scan.ps1`, а затем и в ratchet-гейты.
- **In-process оркестрация**: детекторы нейрослопа как функции (dot-source), без форка `pwsh` на ребёнка; `exit` заменён на возврат кода.
- **Параллельное расписание**: detekt как фоновая задача, join в конце прогона lexical-гейтов.

### 5.2 Потоки данных и событий

`post-change` -> (changed-file signal) -> `catalog-sync` (incremental scan: git только для изменённых, остальное из JSONL) ∥ detekt (фоново) ; lexical/ratchet гейты (in-process, дельта по changed-files) -> агрегированный verdict.

### 5.3 Точки расширяемости

- `-ChangedFiles` как общий контракт для всех ratchet-гейтов и scan.
- Реестр детекторов нейрослопа как список функций - добавление нового детектора не требует нового процесса.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Инкрементальный scan оставляет stale `lastTouched` у не-изменённых файлов | Средняя | Дата последнего касания устаревает | Полный `catalog_sync` без `-ChangedFiles` (ручной ритуал, `/catalog`) по-прежнему делает полный refresh; incremental только в fast-path post-change |
| One-process рефактор нейрослопа роняет хост через `exit` ребёнка | Средняя | Оркестратор падает / пропускает детекторы | Конвертировать каждый детектор в функцию, возвращающую код, без `exit`; прогон до/после на том же дереве, вердикт 1:1 |
| Параллельный detekt утекает фоновый gradle при раннем выходе | Низкая | Зависший gradle-процесс | Cleanup задачи в `finally`/`trap` перед `exit` |
| Дельта-режим ratchet-гейта пропускает находку вне changed-set | Средняя | Потеря покрытия | Дельта считает только рост по changed-files; полный скан сохраняется для release/CI (без `-ChangedFiles`) |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (внутренний тулинг).

---

## 9. Архитектурные решения (ADR)

ADR нет - оптимизация по устоявшимся паттернам проекта (facade + отдельные гейты + ratchet-baselines).

---

## 10. Связи с другими спеками

- S0720 (detekt-гейт - параллелизуется).
- S0826 (`-ScopeToFile` / diff-scoped detekt - расширяется реальным `-ChangedFiles` для ratchet-гейтов).
- S0844 (detekt baseline drift - смежный тулинг).

---

## 11. Критерии готовности (strategic-level)

1. post-change для одиночного `.kt` не вызывает `git log` на каждый файл модуля; `lastTouched` изменённого файла свежий, остальных - взят из JSONL.
2. Полный `catalog_sync -Module <m>` без `-ChangedFiles` даёт JSONL, идентичный прежнему по смыслу (semantic parity).
3. detekt выполняется параллельно lexical-гейтам; итоговый verdict и exit-код идентичны последовательному прогону.
4. Нейрослоп-детекторы прогоняются в одном процессе; набор проверок и вердикт 1:1 с форк-версией.
5. ratchet-гейты принимают `-ChangedFiles` и считают дельту по нему; полный скан сохраняется по умолчанию.
6. Ни одна проверка не удалена; coverage 1:1 подтверждён до/после.

---

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all, phases 01-04)
**Verdict:** Implemented (Phase 04 partial - remainder split to S0850)

Delivered + parity-verified:

- **Phase 01** (was already DONE): incremental catalog scan - no per-file `git log` storm; full rebuild byte-identical.
- **Phase 02**: `post-change.ps1` runs the gradle detekt gate as a `Start-ThreadJob` concurrent with the lexical gates, joined at the end; `try/finally` stops the job on a fail-fast `exit` (verified a `finally` runs before `exit` propagates). Mechanism harness proved exit-code propagation (0/3) and zero leftover jobs on a mid-run lexical `exit`; integration run PASS with the detekt join fully overlapped (~3s / ~9ms vs a standalone ~30-50s step).
- **Phase 03**: `assert-neuroslop.ps1` runs the 8 detectors in-process via `& $path` (the call operator isolates a child `exit`; only dot-source / function `exit` kills the host) - ZERO edits to the detector files, so detection logic and coverage are byte-identical. Fork-vs-in-process output `diff` clean (default + `-Gate`); seeded violations caught on exactly the right dimensions; `assert-fast-gates.ps1` still passes neuroslop through. Runtime ~20.7s -> ~8.4s.
- **Phase 04 (partial)**: shared delta helper `scripts/quality/lib/changed-files-delta.ps1` (growth = working-vs-HEAD per changed file; new files fail-closed; unit-tested). Wired into `assert-flavor-flags-not-growing.ps1` and `assert-deprecated-pm-flags.ps1` (+ `post-change.ps1` under `-ScopeToFile` -> FATAL real delta, ~335/323ms vs ~2886/1758ms full scan). Full-scan verdict unchanged when `-ChangedFiles` omitted; clean file PASS; new violation caught; pre-existing violations elsewhere do not fail.

Criterion status: #1-#4 met; #5 (all ratchet gates accept `-ChangedFiles`) partially met - the 8 neuroslop children (heterogeneous count logic) and `assert-listener-symmetry.ps1` (a balance gate, not an occurrence count) are deferred to **S0850** to avoid a rushed bulk edit of 9 critical detectors under #6; they keep today's advisory full-scan under `-ScopeToFile` (no regression). #6 (coverage 1:1) confirmed for every change made.

Validation: all edited `.ps1` parse-clean; parity evidence in `temp/S0848_phase02_integration.log`, `temp/S0848_phase04_integration.log`, and the mechanism/helper harnesses. No `app_v2/src` file touched -> no gradle build applicable.

**Re-audit 2026-07-03 (Verdict: Verified):**

- Claim checks against live tree: `scan.ps1` has `-ChangedFiles` incremental branch (:17,:31) - expected: present | actual: present; `post-change.ps1` runs detekt as `Start-ThreadJob` (:297) - present; `assert-neuroslop.ps1` runs 8 detectors in-process via `& $path` (:71-:81) - present; `scripts/quality/lib/changed-files-delta.ps1` exists and is wired into flavor-flags + deprecated-pm gates - present.
- Functional evidence: `post-change.ps1 -ChangeType Script` ran PASS this session (2026-07-03 00:18) on the live facade.
- Criterion #5 remainder (neuroslop children + listener-symmetry delta mode) is owned by S0850 (Approved) per the 2026-07-01 scope split - not a gap in this ticket.
