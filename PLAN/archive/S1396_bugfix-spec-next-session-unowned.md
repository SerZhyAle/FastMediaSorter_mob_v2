# Спецификация (compact bugfix): S1396 - состояние сессии /spec-next не имеет владельца

**Ticket:** S1396
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-05
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-05

**Захвачено во время:** S1371 (пакет 5, раунд 2 цикла /spec-next)

**Текст:**

Two concurrent /spec-next sessions share one session-state file with no ownership check.

Symptom: scripts/spec_catalog/spec-next-session.ps1 writes a single fixed path, temp/spec-next-session.json. On 2026-08-05 a sibling /spec-next session was already running (its preflight at 01:10 wrote the S1366 skip-cache entry, and it had advanced S1329 to In Progress by 01:15). A second /spec-next session started at 01:57 and ran -Verb Init, which reported "spec-next-session: initialized" and overwrote the file. The sibling's processed set, its per-round outcome tally and its DEVICE_ONLINE flag were silently discarded; the second session then recorded its own round (S1371) into the same file.

Consequences: the sibling's Stage 6 final report and its -Verb Report output now describe the wrong session; a threshold-triggered /clear + --resume in either session restores a merged or truncated state; and -Verb CheckContext measures a token count that belongs to whichever session wrote last.

Evidence: temp/spec-next-session.json is a fixed path in spec-next-session.ps1; PLAN/spec-catalog.jsonl skip-cache carries S1366 skipped_at 2026-08-05T01:10:24 from the sibling; BUILD.LOCK was held 01:57-02:03 by pid 22748 running build-nolegal-debug.ps1 from that sibling.

Note the contrast: BUILD.LOCK and CODE.LOCK already refuse or warn on a second holder and report the holder's PID, age and reason. The session-state file has no such guard - it neither refuses nor warns.

Direction to research: give the session file an owner stamp (PID plus host, like the lock files) and make -Verb Init refuse or fork to a per-session path when a live foreign owner is present; decide what --resume should do when it finds a foreign owner.

**Проверено при захвате:** `scripts/spec_catalog/spec-next-session.ps1:76` строит путь как `Join-Path $tempDir 'spec-next-session.json'` - фиксированный, без суффикса сессии. Поиск по `pid`/`owner` в файле не даёт ни одного попадания, то есть штампа владельца нет ни в записи, ни в чтении.

**Второй симптом того же файла (наблюдён 2026-08-05, 02:47).** `-Verb Record` не идемпотентен по тикету: он дописывает строку, а не обновляет существующую. В той же сессии S1266 был записан сначала как `advanced` (доведён до `Implemented`), затем, после подтверждения владельцем и `/spec-check`, как `verified`. `-Verb Report` показал обе строки и вывел `processed: 3` при двух реально обработанных тикетах. Тикет, статус которого меняется дважды за сессию - обычный случай, а не редкость, поэтому счётчик завышается систематически, и именно он попадает в итоговый отчёт и в handoff. Разумная семантика - схлопывать по `id`, оставляя последний исход. Захвачено в S1396, а не отдельным тикетом, потому что это тот же скрипт и та же тема - записи сессии не имеют нормальной семантики (ни владельца, ни ключа).

---

## 1. Проблема / симптом

**Дефект A - состояние сеанса без владельца.** Два параллельных сеанса `/spec-next` делят один файл `temp/spec-next-session.json`, и `-Verb Init` второго сеанса молча стирает состояние первого.

- 2026-08-05 sibling-сеанс уже шёл: его preflight записал skip-cache запись `S1366` в 01:10:24, к 01:15 он провёл `S1329` в `In Progress`.
- Второй сеанс в 01:57 выполнил `-Verb Init`, получил `spec-next-session: initialized` и перезаписал файл; `processed`, tally и `deviceOnline` первого сеанса исчезли без единого предупреждения.
- Итоговый отчёт Stage 6 и `-Verb Report` после этого описывают чужой сеанс.
- `--resume` в любом из двух сеансов восстанавливает слитое или усечённое состояние.
- `-Verb CheckContext` меряет число токенов того сеанса, который писал в файл последним.
- Контраст, который делает дефект очевидным: `BUILD.LOCK` и `CODE.LOCK` при втором держателе отказывают или предупреждают и печатают его pid, возраст и причину. У файла состояния нет ни отказа, ни предупреждения.

**Дефект B - `-Verb Record` не идемпотентен по тикету.** Он дописывает строку вместо обновления существующей.

- В одном сеансе `S1266` записан сначала как `advanced` (доведён до `Implemented`), затем, после `/spec-check`, как `verified`.
- `-Verb Report` показал обе строки и вывел `processed: 3` при двух реально обработанных тикетах.
- Тикет, меняющий статус дважды за сеанс, - обычный ход пайплайна, а не редкость, поэтому счётчик завышается систематически и в таком виде попадает в итоговый отчёт и в handoff.

---

## 2. Корневая причина

- `$statePath` собирается как фиксированный `Join-Path $tempDir 'spec-next-session.json'` - без суффикса сеанса и без права на второго держателя.
- В схеме записи нет ни одного поля владельца, и ни одна ветка `switch` его не читает и не пишет.
- Ветка `Init` безусловно конструирует свежий объект и вызывает `Write-State`; ветки "файл уже существует" у неё нет.
- Ветка `Record` выполняет `$state.processed = @($state.processed) + $entry` и инкрементирует `tally` - обе операции append-only, ключа `id` у записи нет.
- Владелец здесь - не OS-процесс: каждый вызов скрипта это отдельный короткоживущий `pwsh.exe`, между вызовами не живёт ничего. Поэтому pid-liveness (модель `BUILD.LOCK`) неприменима, а применима модель `CODE.LOCK` - владение по стабильному идентификатору агентского сеанса.

---

## 3. Исправление

### 3.1 Штамп владельца в схеме

- Добавить в запись поле `owner` с `sessionId`, `host`, `pid`, `stampedAt`, `lastSeenAt`.
- `sessionId` берётся из `$env:CLAUDE_CODE_SESSION_ID` - единственного стабильного идентификатора агентского сеанса, уже используемого этим же скриптом в `Get-ContextCheck`.
- Переменная окружения пуста - владение не определено, все проверки владельца становятся no-op, поведение остаётся сегодняшним.
- Файл без поля `owner` (написан до этого исправления) считается ничейным, и первый же verb его штампует.

### 3.2 Живость владельца

- Владелец жив, если его транскрипт `~/.claude/projects/**/<sessionId>.jsonl` изменялся свежее окна `-StaleMinutes` (по умолчанию 45).
- Транскрипт не найден - откат на `owner.lastSeenAt` с тем же окном.
- Окно намеренно широкое: сеанс, ждущий релизной сборки, не пишет в транскрипт десятками минут, а ложный вердикт "мёртв" возвращает ровно тот дефект, который чинится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1394 (spec-next-handoff-false-threshold-claim - тот же модуль сессии/порога), S1369 (прецедент записи owned-by-sibling-session в skip-cache).

### 3.4 `Init` отказывает живому чужому владельцу

- Живой чужой владелец - выход 4, файл не тронут, сообщение называет `sessionId`, host, возраст последней активности и оба выхода: `--resume` для своего же сеанса после `/clear` и `-Force` для намеренного вытеснения.
- `-Force` - вытеснить, напечатав вытесненного владельца.
- Свой, отсутствующий или устаревший владелец - обычный fresh-init со штампом.

### 3.5 `Resume` перенимает владение

- `Resume` штампует текущего владельца всегда и печатает прежнего в своём JSON (`previousOwner`), не отказывая никогда.
- Причина: после `/clear` у сеанса новый `sessionId`, а транскрипт прежнего был записан секунды назад и по любому критерию выглядит живым. Отказ здесь сломал бы штатный путь resume-after-clear, ради которого файл состояния и существует.

### 3.6 `Record` и `Device` предупреждают, но не отказывают

- Чужой живой владелец - `Write-Warning` с его `sessionId`, затем обычная работа.
- Это мягкая модель `CODE.LOCK`: отказ в середине раунда оставил бы цикл без записи только что закрытого тикета, что дороже предупреждения.

### 3.7 `Record` идемпотентен по `id`

- Запись с уже существующим `id` обновляет её на месте: `outcome`, `note` и `at` перезаписываются, `firstAt` и позиция в списке сохраняются.
- `tally` пересчитывается из схлопнутого списка целиком, а не инкрементируется, - именно инкремент и завышал счётчик.
- Вывод различает `recorded` и `updated`, чтобы раунд-вердикт цикла не выглядел как новая обработка.

### 3.8 Контракт кодов выхода

- Заголовок скрипта пополняется кодом 4 - "refused: live foreign owner (Init only)".
- Коды 0, 1, 2, 3 сохраняют текущее значение.

---

## 4. Проверка

- `-Verb Init` дважды подряд в одном сеансе - второй проходит как свой владелец, exit 0.
- `-Verb Init` поверх живого чужого владельца - exit 4, файл байт в байт прежний.
- `-Verb Init -Force` в той же ситуации - exit 0, вытесненный владелец назван в выводе.
- `-Verb Resume` поверх чужого владельца - exit 0, `excludeCsv` сохранён, `previousOwner` в выводе.
- `-Verb Record` одного `id` дважды - одна строка, последний `outcome`, `tally.processed` равен 1.
- `-Verb Report` и `-Verb Handoff` печатают счётчики, совпадающие с числом уникальных тикетов.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

---

## Last Audit

**Date:** 2026-08-05
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Эвиденс по §4 - `temp/S1396/test-session-ownership.ps1`, 28 проверок, exit 0: отказ Init при живом чужом владельце (exit 4, файл байт в байт прежний), `-Force` с именем вытесненного, `Resume` с `previousOwner`, повторный `Record` одного id как `updated` при `tally.processed = 1`, предупреждение вместо отказа на чужом владельце, `Init` на отсутствующем файле (exit 0). Шлюзы: `assert-exit-contract` exit 0, `assert-script-cheatsheet-sync` exit 0, `document_registry/validate` PASS 27, `post-change -ScopeToFile` PASS.

### Manual / on-device

- [ ] Два реально параллельных сеанса `/spec-next`: в проверке чужой владелец подделан в файле состояния, а не создан вторым живым сеансом. Первый же настоящий параллельный запуск должен показать exit 4 с чужим `sessionId` в сообщении.
