# Спецификация (compact bugfix): S1878 - Подборщик предлагает BlockByOtherTask, который /spec-all тут же отказывается брать

**Ticket:** S1878
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-21
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21

**Захвачено во время:** цикл `spec-do`, раунды 1-3

**Текст:**

Два живых правила противоречат друг другу, и каждая сессия платит за это несколькими раундами.

`spec-next-preflight.ps1` после S1864 (`bugfix-preflight-skips-blockneedusertest-blocker`, Verified 2026-08-21) считает блокер в статусе `BlockNeedUserTest` **не блокирующим**: он снимает такие тикеты со skip-cache (наблюдалось `skip_cache_overridden_ids: ["S1876","S1728","S1714","S1717"]`) и ставит их первыми в `ranked[]`.

Resume Map команды `/spec-all` требует обратного: для статуса `BlockByOtherTask` разблокировка разрешена, только если блокирующая спека `Verified`. `BlockNeedUserTest` этого не даёт, поэтому делегирование немедленно возвращает тикет неизменным.

Итог: подборщик отдаёт тикет, исполнитель его отказывается брать, раунд тратится на разбор, и в следующей сессии всё повторяется - skip-cache тут не спасает, потому что подборщик переопределяет любую запись с причиной, начинающейся на `blocker-not-verified`.

**Наблюдённая цена в этом прогоне (2026-08-21):** три раунда подряд - S1846 (блокер S1860), S1697 (блокер S1860), S1876 (блокер S1715) - все три вернулись со статусом входа. Тот же список стоял первым и в предыдущих сессиях: S1846 несёт в спеке отдельный раздел «Почему этот тикет всплывает в очереди и всё равно не берётся (2026-08-21)», написанный ровно затем, чтобы очередная сессия не разбирала это заново.

**Дополнительное обстоятельство:** оба блокера (S1860, S1715) снимаются только владельцем на его личном железе - S1860 требует часов, спаренных с личным телефоном владельца, S1715 требует устройства с >= 3 ГБ ОЗУ, которого эмулятор не даёт. То есть в агентской сессии они не закрываются в принципе, и предложение зависимых тикетов гарантированно холостое.

---

## 1. Проблема / симптом

Подборщик (`scripts/spec_catalog/spec-next-preflight.ps1`) и исполнитель (`.claude/commands/spec-all.md`, Resume Map) применяют разные правила к одному и тому же статусу `BlockByOtherTask`. Пользовательский эффект - холостые раунды в каждой автономной сессии и повторный ручной разбор одних и тех же тикетов.

Эвиденс - выдача подборщика 2026-08-21 08:47 (`selected` = S1846 при `depends_on = [{"id":"S1860","status":"BlockNeedUserTest"}]`), и строка Resume Map: `BlockByOtherTask` -> «if blocking spec `Verified` -> unblock and continue from last stage; else -> add to manual list».

---

## 2. Корневая причина

Расхождение не симметрично: одна сторона следует записанному решению владельца, вторая ему противоречит. Уступать должна вторая, и вопрос владельцу здесь не нужен - ответ уже дан и лежит в дереве.

**Решение владельца, дословно** (`PLAN/RELEASE_QUEUE.md`, строка 24, проверено 2026-08-21):

> A ticket dependency releases as soon as the blocker reaches **BlockNeedUserTest** - the code is in the tree by then and only the owner's device pass is left. Never make a dependent wait for Verified (owner ruling 2026-08-07).

**Что сделала S1864.** Она нашла ровно это расхождение на стороне подборщика: `preview.ps1` держал предикат освобождения списком из двух статусов (`Verified`, `Archived`), набранным на месте, и потому пропускала зависимые тикеты вопреки решению владельца. Её исправление привело подборщик к множеству `Implemented` / `Verified` / `BlockNeedUserTest` / `Archived` - `RELEASE_READY` плюс архив - и вынесло определение в общий leaf-файл, чтобы третьей копии не появилось. То есть подборщик после S1864 прав.

**Где осталась старая формулировка.** S1864 обновила справочник (`.claude/reference/spec-next.md` строка 40 уже называет полное множество), но не драйверы команд. Тот же двухстатусный предикат прописью остался в двух местах:

- `.claude/commands/spec-all.md` строка 98, Resume Map: `if blocking spec Verified -> unblock and continue from last stage; else -> add to manual list`;
- `.claude/commands/spec-quiz.md` строка 109: `only if dependency resolved to Verified (re-check via select.ps1)`.

Это третья и четвёртая копии того самого определения, которое S1864 сводила в одну. Подборщик предлагает тикет по новому правилу, исполнитель отказывается по старому - и раунд тратится впустую.

---

## 3. Исправление

Привести оба драйвера к решению владельца и к формулировке, которую S1864 уже записала в справочнике: блокер освобождает зависимый тикет, достигнув `Implemented`, `Verified`, `BlockNeedUserTest` или `Archived`.

- `.claude/commands/spec-all.md` строка 98 - Resume Map для `BlockByOtherTask` проверяет освобождающее множество, а не один `Verified`.
- `.claude/commands/spec-quiz.md` строка 109 - то же множество в условии восстановления.

Пятое место не заводится: обе строки ссылаются на уже существующую формулировку справочника, а не повторяют список статусов третий раз своими словами.

**Вне объёма.** Подборщик не трогается - после S1864 он прав. Отдельный класс «блокер снимается только владельцем на его железе» (S1860 требует пары часы+телефон владельца, S1715 - устройства с 3+ ГБ ОЗУ) здесь не вводится: это отдельное понятие, и заводить его вместе с исправлением расхождения значит смешать два вопроса в одном тикете.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1864 (`bugfix-preflight-skips-blockneedusertest-blocker`, Verified - привёл подборщик к решению владельца, но не тронул драйверы команд), S1775 (`picker-ignores-blockers-unless-status-blockbyothertask`, Verified), S1846 / S1697 / S1876 / S1728 / S1714 (пострадавшие тикеты)
- **Owner decision (derived, not asked):** освобождающее множество - `Implemented`, `Verified`, `BlockNeedUserTest`, `Archived`. Источник - решение владельца от 2026-08-07 в `PLAN/RELEASE_QUEUE.md` строка 24 и разделение файлов очереди в `CLAUDE.md` §4. Оба уже записаны, нового ответа владельца не требуется.

---

## 4. Проверка

1. `.claude/commands/spec-all.md` и `.claude/commands/spec-quiz.md` не содержат условия освобождения по одному лишь `Verified`: `grep -n "blocking spec .Verified\|resolved to .Verified" .claude/commands/` возвращает ноль строк.
2. Обе строки называют то же множество, что и `.claude/reference/spec-next.md` строка 40 (`Implemented`, `Verified`, `BlockNeedUserTest`, `Archived`).
3. Инвентарь хуков не разъезжается: `scripts/quality/assert-hook-inventory.ps1` выходит с нулём (правится `.claude/**`).
4. Регрессия наблюдаемая: тикет `BlockByOtherTask`, чей блокер стоит в `BlockNeedUserTest` (сегодня это S1846, S1697, S1876), перестаёт возвращаться из делегирования с тем же статусом только из-за формулировки Resume Map.

---

## Last Audit

**Date:** 2026-08-21
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Проверено по четырём предикатам §4 плюс инварианты жизненного цикла.

- **§4.1 - старый предикат исчез.** `grep -rn "blocking spec .Verified\|resolved to .Verified" .claude/commands/` -> exit 1, ноль строк. PASS.
- **§4.2 - паритет формулировки.** Все четыре статуса (`Implemented`, `Verified`, `BlockNeedUserTest`, `Archived`) присутствуют в каждой из трёх строк: `.claude/reference/spec-next.md:40`, `.claude/commands/spec-all.md:98`, `.claude/commands/spec-quiz.md:109`. PASS.
- **§4.3 - инвентарь хуков.** `scripts/quality/assert-hook-inventory.ps1` -> `PASS (11 registered hook(s), project + global)`, exit 0. PASS.
- **§4.4 - наблюдаемая регрессия.** MANUAL: предикат исправлен в тексте, но подтверждение делегированием требует прогона `/spec-all` на тикете с блокером в `BlockNeedUserTest`. Ближайшая проверка - следующий заход очереди на S1846 или S1697 после истечения их записи в кэше пропусков (TTL 3 суток, поставлен той же сессией).
- **Отладочных меток нет.** `grep -rn 'Timber.d("S1878:' app_v2/src wear/src` -> 0 строк, что соответствует статусу вне `BlockNeedUserTest`. PASS.
- **Журнал изменений.** `dev/CHANGELOG.md` содержит две записи S1878 - создание скелета и само исправление; это две разные логические правки, а не дубль. PASS.
- **Перенос открытых вопросов.** `check-open-items-carried.ps1 -Id S1878` -> `PASS`, раздела исследования нет. PASS.
- **Витрина возможностей.** EXEMPT: изменение внутреннее, пользователю не видно, §8 в компактном шаблоне отсутствует.
- **Закрытие фасадом.** `post-change.ps1 -ChangeType Doc -ScopeToFile -RegistryAck repository-rules` -> `post-change: PASS (Doc, 12584 ms)`, exit 0, без advisories.
- **Обязательство реестра закрыто по существу.** Запись `repository-rules` называет соседей `CLAUDE.md`, `AGENTS.md`, `GEMINI.md`, `.github/copilot-instructions.md`, `.claude/agents/*.md`, `.claude/reference/*.md`, `.claude/templates/*.md`, `.claude/skills/*/SKILL.md`, `docs/AGENT_HOOKS.md`. Проверены все: единственное упоминание `BlockByOtherTask` вне правленых файлов - `CLAUDE.md:46`, и оно касается обязательной `-StatusNote` и токена `Blocker: Sxxxx`, а не предиката освобождения. Править соседей не потребовалось.

### Manual / on-device

- [ ] Следующее делегирование `BlockByOtherTask`-тикета с блокером в `BlockNeedUserTest` продвигает его, а не возвращает с тем же статусом (§4.4).
