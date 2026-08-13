# Спецификация (compact bugfix): S1462 - Ожидание очереди BUILD.LOCK падает на turnGrantedAt

**Ticket:** S1462
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Текст:**

agent-lock.ps1 queue-head reservation throws "The property 'turnGrantedAt' cannot be found on this object" from Set-AgentTicketTurnGranted when a caller waits on a busy BUILD.LOCK; the wait aborts and the calling gate reports a spurious FAIL (child exit 1). Observed 2026-08-07 during a post-change.ps1 detekt gate that had queued at BUILD.LOCK position 3. Unrelated to the ticket that hit it (S1401). Not reproducible on demand - the queue was empty minutes later - so it needs its own repro harness. Area: scripts/utils/agent-lock.ps1, S1448 queue-head reservation path.

**Захвачено во время:** S1401

---

## 1. Проблема / симптом

Вывод `post-change.ps1` (гейт detekt) 2026-08-07:

```
BUILD.LOCK busy - queued at position 3 (ticket #4). Waiting up to 900s. holder pid 47052, age 47s, reason 'assert-detekt.ps1 -Module app_v2'
Set-AgentTicketTurnGranted: P:\ANDROID\FastMediaSorter_mob_v2\scripts\utils\agent-lock.ps1:503
Line |
 503 |      [void](Set-AgentTicketTurnGranted -Ticket $head)
     |             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | The property 'turnGrantedAt' cannot be found on this object. Verify that the property exists.
  [detekt-gate] FAIL (15 ms) - child exit code 1
```

Опасность не в самом исключении, а в том, во что оно превращается: гейт возвращает exit 1, то есть «нашёл дефект в коде», хотя на деле он даже не запускался. Читатель вердикта не может отличить это от настоящего провала detekt.

Место: `scripts/utils/agent-lock.ps1`, путь резервирования головы очереди (введён S1448). Функция `Set-AgentTicketTurnGranted` объявляет, что зонд свойства сделан именно ради `Set-StrictMode`, и всё же падает ровно этой ошибкой.

---

## 2. Корневая причина

### 2.1 Механизм

`Set-AgentTicketTurnGranted` читает `turnGrantedAt` у тикета, которого этого поля может не быть: тикеты, выписанные до S1448, имеют старую форму. Обычное чтение отсутствующего свойства в PowerShell возвращает `$null` и никого не тревожит - **кроме** случая `Set-StrictMode`, где оно становится терминирующей ошибкой. Вызывающие, которые ждут на этой очереди (`post-change.ps1` и гейты, которые он запускает), StrictMode включают. Отсюда и наблюдённое: очередь падала не у всех и не всегда, а ровно у строгих вызывающих, и только когда головой очереди оказывался старый тикет.

### 2.2 Код уже исправен - проверено, а не предположено

На 2026-08-07 оба чтения защищены пробой по мешку свойств: `agent-lock.ps1:402` в `Set-AgentTicketTurnGranted` и `:507` в `Test-AgentLockTurn`. Измерение, а не чтение комментария: тикет старой формы прогнан через `Set-AgentTicketTurnGranted` под `Set-StrictMode -Version Latest` - штамп проставлен, повторный вызов идемпотентен, исключения нет.

Значит исправлять в этом тикете нечего. Опасность в другом.

### 2.3 Чего не хватало - способа заметить возврат дефекта

Стенд `test-agent-lock-queue.ps1` уже содержал случай про совместимость со старыми тикетами («a pre-S1448 ticket is read, ordered by seq and kept»), и он **проходит независимо от наличия защиты**: он проверяет чтение очереди, а не путь штампования, и работает без StrictMode - то есть в режиме, где отсутствующее свойство ошибкой не является.

Проверено мутацией 2026-08-07: защита на строке 402 заменена на прямое чтение, стенд прогнан.

- Случай про совместимость - **PASS** (то есть он этот дефект не ловит).
- Новый строгий случай - **FAIL** с дословно тем сообщением из §1: `The property 'turnGrantedAt' cannot be found on this object.`

После возврата защиты - 6 из 6 PASS, файл побайтно совпадает с бэкапом до мутации.

То есть настоящий дефект этого тикета - не строка кода, а незакрытая брешь в проверке: правка, снимающая защиту, прошла бы весь стенд зелёной.

### 2.4 О подмене вердикта

Спурьозный `exit 1` из §1 - следствие обрыва, а не отдельный дефект: исключение убивало ожидание, и гейт отчитывался кодом «нашёл дефект» о проверке, которая не запускалась. С устранённым обрывом исчезает и подмена. Тем и отличается от [S1464], где подмена живёт в самой логике гейта и лечится отдельно.

---

## 3. Исправление

Кода не трогаем - он верен. Добавляем шестой случай в `scripts/utils/test-agent-lock-queue.ps1`:

- голова очереди - тикет старой формы (`New-SyntheticTicket -OldShape`), за ней тикет вызывающего;
- `Test-AgentLockTurn` вызывается внутри дочерней области с `Set-StrictMode -Version Latest` - строгость здесь и есть весь смысл случая, без неё он не проверяет ничего;
- утверждение: исключения нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1448 (введён механизм резервирования головы очереди), S1432 (очередь BUILD.LOCK)

---

## 4. Проверка

Занятый `BUILD.LOCK` и очередь из трёх живых сессий воспроизводить не нужно и незачем: дефект не в состязании, а в форме тикета, и она подделывается напрямую. Стенд песочный (`$Script:AgentLockRepoRoot` подменяется), поэтому безопасен при живых локах соседей.

- **Стенд падает без защиты.** `pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1` после замены пробы на строке 402 прямым чтением - FAIL нового случая с тем самым сообщением `The property 'turnGrantedAt' cannot be found on this object`.
- **Старый случай при этом зелёный** - доказательство, что брешь была настоящей, а не воображаемой.
- **Стенд зелёный с защитой.** Тот же прогон на неизменённом файле - 6 из 6 PASS, exit 0.
- **Мутация полностью откачена** - `Get-FileHash` файла совпадает с бэкапом `temp/S1462/agent-lock.ps1.*.bak`, снятым до правки.

---

## Last Audit

**Date:** 2026-08-07. **Verdict:** Verified.

- Дефект в коде отсутствует: оба чтения `turnGrantedAt` защищены пробой по мешку свойств (`agent-lock.ps1:402` и `:507`). Проверено исполнением, а не чтением комментария - тикет старой формы под `Set-StrictMode -Version Latest` штампуется без исключения, повторный вызов идемпотентен.
- Настоящий пробел был в проверке: существовавший случай про совместимость со старыми тикетами проходит и без защиты, потому что читает очередь, а не путь штампования, и работает вне StrictMode.
- Доказано мутацией: проба на строке 402 заменена прямым чтением -> новый случай FAIL с дословным сообщением из §1 (`The property 'turnGrantedAt' cannot be found on this object`), старый случай при этом PASS.
- Защита возвращена, `test-agent-lock-queue.ps1` -> **6 из 6 PASS, exit 0**; `Get-FileHash` совпадает с бэкапом `temp/S1462/agent-lock.ps1.20260807-144016.bak`, снятым до мутации.
- Спурьозный `exit 1` был следствием обрыва, а не отдельным дефектом (§2.4) - в отличие от [S1464], где подмена живёт в логике гейта.
