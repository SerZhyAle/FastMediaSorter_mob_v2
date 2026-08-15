# Спецификация (compact bugfix): S1505 - Release-верб agent-lock не снимает CODE.LOCK, но рапортует успех

**Ticket:** S1505
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** S1471 (`/spec-do`, фаза 02)

**Текст:**

agent-lock.ps1 -Name Code -Action Release reports success but does not release CODE.LOCK. Observed 2026-08-08 while working S1471: after enter-code-lock.ps1 acquired the lock (pid 33728, reason 'S1471 phase 01 audit fix'), `pwsh -NoProfile -File scripts/utils/agent-lock.ps1 -Name Code -Action Release` printed nothing and exited 0, but lock-status.ps1 still showed Code.LOCK HELD by the same pid; a second Release call also exited 0 and also left it held. The lock stayed held for 479s across a subagent's whole Phase 02 implementation, which printed 'CODE.LOCK present' warnings on both of its builds, and it would have blocked any sibling session that asked for the lock. `scripts/utils/exit-code-lock.ps1` released it immediately. So the Code lock has a working releaser and a lying one, and the lying one returns the success exit code, which is what makes it dangerous - a caller that trusts the exit code leaves the lock wedged until its process dies. Needs: establish whether agent-lock.ps1's Release verb is simply not the right entry point for the Code lock (in which case it must fail loudly or forward to exit-code-lock.ps1 rather than exit 0), or whether it is a real bug in the release path; check the Build lock for the same shape; then make the wrong call impossible rather than merely documented. Related: S1462 fixed a different agent-lock defect (queue turn granted) and is Verified.

---

## 1. Проблема / симптом

`pwsh -NoProfile -File scripts/utils/agent-lock.ps1 -Name Code -Action Release` ничего не печатает, выходит с кодом 0 и оставляет `CODE.LOCK` держаться. Вызывающая сторона, доверяющая коду возврата, считает лок снятым и уходит дальше, а лок остаётся занятым до смерти процесса - в наблюдённом случае 479 секунд, всю фазу реализации подагента, чьи сборки печатали предупреждение «CODE.LOCK present», и любая параллельная сессия, попросившая лок, всё это время ждала бы.

Опасен здесь не сам промах, а его код возврата: молчаливый успех неотличим от настоящего.

---

## 2. Корневая причина

Ошибка не в пути освобождения - его там просто нет. `agent-lock.ps1` не имеет ни верхнеуровневого блока `param()`, ни диспетчера глаголов: это библиотека для dot-source, что прямо сказано в её собственном заголовке («Dot-source this file to get Enter-AgentLock / Exit-AgentLock ..»). Запущенная как скрипт, она получает `-Name Code -Action Release` в необязательный `$args`, молча их игнорирует, выполняет своё тело - то есть определяет функции - и завершается с кодом 0.

Отсюда ровно наблюдённая картина: пусто на выходе, ноль в коде возврата, лок на месте. Рабочий освободитель - `exit-code-lock.ps1`, который подключает библиотеку через dot-source и вызывает функцию `Exit-AgentLock`; он же владеет проверкой владельца, поэтому не снимает чужой лок.

Форма дефекта общая для обоих локов: тот же вызов с `-Name Build` так же ничего не сделал бы, потому что не существует не «ветка Code», а сам интерфейс командной строки.

---

## 3. Исправление

В начало `agent-lock.ps1`, сразу после блока справки, добавлена защита от прямого запуска: если файл вызван не через dot-source, он печатает ошибку, называющую правильные точки входа, и выходит с кодом **2**. Правильный вызов остаётся невозможно спутать с неправильным - последний теперь падает громко, а не выглядит успешным.

Различитель - `$MyInvocation.InvocationName`: при dot-source он равен `.`, при `pwsh -File` - полному пути скрипта, при операторе `&` - `&`. Проверено эмпирически, включая ключевой вложенный случай: dot-source изнутри скрипта, который сам запущен через `pwsh -File`, по-прежнему даёт `.`, а именно так библиотеку загружают все 47 её потребителей.

Полноценный интерфейс командной строки сознательно не вводится: он продублировал бы проверку владельца, которой владеет `exit-code-lock.ps1`, и создал бы второй путь освобождения, расходящийся с первым.

Код возврата 2 внесён в заголовок скрипта, как требует правило о достижимых кодах возврата, и печатается через `Write-Error -ErrorAction Continue`, чтобы `exit 2` был достижим.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1462 - другой дефект того же скрипта (выдача очереди), Verified. S1471 - тикет, во время которого находка сделана.

---

## 4. Проверка

Прогнано 2026-08-08, все результаты наблюдались лично:

1. Ошибочный вызов - `pwsh -NoProfile -File scripts/utils/agent-lock.ps1 -Name Code -Action Release`: печатает ошибку с перечнем правильных точек входа, **exit 2**. До правки - пусто и exit 0.
2. Рабочий освободитель - `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1`: «CODE.LOCK released (or was already free)», **exit 0**, лок действительно снят.
3. `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code`: «Code.LOCK: absent (free)», **exit 0**.
4. Ветка Build через dot-source: `Enter-AgentLock -Name Build` -> статус `Exists=True`, `Exit-AgentLock -Name Build` -> `Exists=False`. Регрессии в ветке Build нет.
5. Потребитель с формой `. (Join-Path ..)`, синтаксически отличной от `. "$PSScriptRoot\.."` - `ticket-lease.ps1 -Verb List`: **exit 0**.
6. Все 47 точек вызова `agent-lock.ps1` в `scripts/` проверены поиском: каждая - dot-source, ни одной через `&` или `pwsh -File`, поэтому защита не может сломать существующего потребителя.
7. `scripts/quality/assert-exit-contract.ps1`: PASS, «0 unreachable exit site(s), 0 silent script(s)» - новый `exit 2` достижим и объявлен в заголовке.
8. `post-change.ps1 -ChangeType Script -ScopeToFile`: PASS with advisories (1), exit 0.

---

## Last Audit

**Date:** 2026-08-08
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Дефект воспроизводился и устранён в наблюдаемой форме: тот же вызов, что раньше молча возвращал 0, теперь возвращает 2 с объяснением. Проверки 1-8 из §4 прогнаны в этой сессии, результаты процитированы там же.

§8 (влияние на пользователя) - EXEMPT: правка внутренняя, инструментальная, наблюдаемого поведения приложения не касается, записи в инвентарь способностей не требует.

Единственная advisory от `post-change` - устаревший `docs/SCRIPT_CHEATSHEET.md` - к этой правке не относится: заголовочный блок `.NOTES` шпаргалкой не рендерится. Файл всё равно перегенерирован штатным `help.ps1 -Generate`, и регенерация подхватила записи чужих скриптов (`streams-perf-seed.ps1`, `archive-temp.ps1`, `archive-vscode-cruft.ps1`, ещё один `Run-Tests.ps1`), находившихся в дереве в момент прогона.

### Manual / on-device

- Проверка на устройстве не требуется: изменение затрагивает только скрипты разработки и в APK не попадает.
