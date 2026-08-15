# Стратегическая спецификация: S0823 - Remove stale archived ticket instrumentation from auth/link-download logs

**Ticket:** S0823
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-30
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - log analysis 2026-06-30

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-30

В свежих логах продолжают жить archived ticket probes:

- `S0211: webview save host=www.instagram.com reused=false ..` в `logs/fastmediasorter_20260630_022427.log:1059`
- множественные `S0260: canonical ..` и `S0260: session context state ..` в тех же debug sessions, например lines 708, 713, 1116, 1121, 1240, 1245

Параллельно поиск по текущему коду показывает, что эти строки до сих пор жёстко зашиты в source:

- `data/repository/AuthSessionRepositoryImpl.kt`
- `domain/usecase/link/LinkAutoDownloadCoordinator.kt`

При этом оба тикета в каталоге уже `Archived`: `S0211`, `S0260`.

---

## 1. Проблема

В production/debug logs остались ticket-bound debug markers от уже закрытых/archived spec'ов. Это ломает проектный инвариант по `Sxxxx` probes: такие id должны жить только как временная verification instrumentation, пока тикет находится в `BlockNeedUserTest`.

Проблема не только эстетическая. Stale probes засоряют дальнейший log analysis, создают ложное ощущение активного незакрытого тикета и, в случае `S0211`, уже пишутся на `I` level вместо временного debug-only verification path.

---

## 2. Цели

1. Убрать из логов stale `S0211:` и `S0260:` markers как ticket-bound instrumentation.
2. Сохранить полезную operational диагностику в auth/link-download domain, но уже без привязки к archived ticket ids.
3. Вернуть соответствие правилам проекта для `Sxxxx` probes и permanent logs.

**Non-goals:**

- Не удалять полезную диагностику только ради тишины.
- Не менять функциональное поведение auth/link-download flows.
- Не переписывать всю observability strategy проекта.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Оставить достаточно signal для расследования Instagram/Threads auth issues после удаления stale probes.
2. По возможности унифицировать формулировки diagnostic logs в plain English without ticket ids.

### 3.2 Жёсткие ограничения

- **Flavor:** общий shared path, без divergence по flavor.
- **API level:** без API-specific веток.
- **Wear OS:** не затрагивается.
- **Производительность:** логирование не должно разрастись по объёму после cleanup.
- **Совместимость данных:** не относится.
- **Локализация:** developer logs only, EN.
- **Доступность:** не относится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0211, S0260, S0749, S0822.

---

## 4. Контекст текущей архитектуры

Auth save и link-download/session-application path historically использовали ticket-id based diagnostics. После закрытия исходных тикетов сами diagnostics остались, но их `Sxxxx` form больше не соответствует политике проекта.

Нужен cleanup, который отделит временные verification probes от постоянной технической телеметрии. В этой зоне это особенно важно, потому что auth/extractor regressions уже расследуются по логам очень активно.

---

## 5. Предлагаемый подход

Заменить stale ticket-bound messages на plain diagnostic logs без `Sxxxx` id, сохранив ключевые поля: host, account reuse, session applied state, canonical URL/result.

### 5.1 Основные столпы / модули

- Remove archived ticket ids from permanent logs.
- Preserve useful structured diagnostics.
- Re-grep nearby code for any other stale `Sxxxx` strings in the same domain.

### 5.2 Потоки данных и событий

- Auth save / session apply / canonicalization still log.
- Ticket-bound probe syntax disappears from permanent paths.

### 5.3 Точки расширяемости

- Future verification probes for this domain can still be reintroduced temporarily when a live ticket reaches `BlockNeedUserTest`.

---

## 6. Открытые вопросы / Research items

1. ~~Нужно ли ввести structured prefix вместо `Sxxxx`.~~ **Resolved (2026-06-30):** не нужно. Сообщения уже самоописательны (`ytdlp route=..`, `session context state ..`, `ClassName.method ..`), достаточно убрать ticket-id префикс; новый формальный префикс не вводим.
2. ~~Есть ли в зоне ещё скрытые stale probes от других archived tickets.~~ **Resolved (2026-06-30):** да - `S0223:` (Archived) в `HtmlPageExtractionStrategy` (link-домен) очищен inline как тривиальный one-liner в той же зоне. `S0288:` в OCR/extractor path проверен - он `BlockNeedUserTest` (живой verification probe), оставлен нетронутым. Repo-wide grep подтверждает 0 оставшихся `S0211`/`S0260`/`S0223` log strings.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Уберём слишком много signal из логов | Средняя | Сложнее расследовать auth bugs | Сохранять plain-English diagnostics и поля host/account/result |
| Cleanup заденет живой verification path | Низкая | Потеря полезного device-test probe | Проверить catalog status каждого `Sxxxx` перед удалением/переименованием |

---

## 8. Связи с другими спеками

- S0211 - archived auth account dedup ticket.
- S0260 - archived link/session-context ticket.
- S0822 - новый follow-up на actual authenticated extraction defect; cleanup этой instrumentation улучшит его расследование.

---

## 9. Критерии готовности (strategic-level)

1. В permanent auth/link-download logs больше нет archived `S0211:` и `S0260:` strings.
2. Полезная диагностика по save/session/canonicalization остаётся доступной без ticket ids.
3. Repo-wide grep в затронутой зоне не находит оставшихся stale probes этих archived tickets.

---

## Implementation (2026-06-30)

**Fix:** убран ticket-id префикс из 14 permanent log strings archived-тикетов, тело сообщения (host/account/session/route/result поля) сохранено:

- `YtDlpExtractionStrategy.kt` (noLegal) - 7× `S0260:` route/pick/result диагностика.
- `LinkAutoDownloadCoordinator.kt` - 4× `S0260:` session-context/canonical.
- `AuthSessionRepositoryImpl.kt` - 1× `S0211:` webview save.
- `ReceiveShareActivity.kt` - 1× `S0211:` auth-dialog resolve.
- `HtmlPageExtractionStrategy.kt` - 1× `S0223:` ig-api failure (другой archived тикет, тот же link-домен, §6 q2).
- `ytdlp_utils.py` (noLegal Python) - 1× `S0260:` selected-format print.

**Out of scope / preserved:** провенанс-комментарии (`// S0211:`, `// S0260:` объясняющие WHY) оставлены - это не log probes. `S0288:` probes (BlockNeedUserTest, живые) не тронуты.

**Verification:** standard `a.ps1 fk` PASS + noLegal compile PASS; repo-wide grep `"S0(211|260|223):` = 0; neuroslop + ticket-log-audit PASS. Только developer-log текст, device-test не требуется.

**Last Audit (2026-06-30):** Verified. Все 3 strategic-критерия выполнены (0 stale strings, диагностика сохранена, grep чист).
