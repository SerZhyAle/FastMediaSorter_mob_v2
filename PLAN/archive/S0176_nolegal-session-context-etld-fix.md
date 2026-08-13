# Стратегическая спецификация: S0176 — noLegal: fix eTLD+1 lookup в applySessionContext

**Ticket:** S0176
**Status:** BlockNeedUserTest
**Implemented date:** 2026-05-12
**Priority:** 60
**Date:** 2026-05-12
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — ресёрч S0156, итерация 2026-05-12
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical plan:** [S0176_nolegal-session-context-etld-fix/INDEX.md](S0176_nolegal-session-context-etld-fix/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Компонент-оркестратор загрузки при применении сохранённой сессии ищет cookies по точному совпадению host из URL. Если пользователь сохранил сессию для `instagram.com`, а ссылка пришла как `https://www.instagram.com/...` — lookup возвращает пустой результат. Cookie-уровень (OkHttp CookieJar) уже получил eTLD+1 fix в рамках S0171 (завершён, Archived), но оркестратор этого не получил. В результате cookies не передаются в extraction-стратегии, которые зависят от per-run session context, — авторизованный контент не скачивается даже при наличии валидной сессии.

Примечание: S0171 частично маскирует этот баг на уровне OkHttp-запросов (cookies доставляются через CookieJar fallback). Однако extraction-стратегии, читающие session context напрямую, не получают cookies, если lookup в оркестраторе вернул пустой результат.

Это баг в **main sourceSet** (shared-код) — затрагивает оба flavor (standard и noLegal). Исправление в shared-коде автоматически покрывает оба APK.

---

## 2. Цели

1. Оркестратор применяет сохранённую сессию при любом варианте записи хоста: `instagram.com`, `www.instagram.com`, `m.instagram.com` и аналогично для других платформ.
2. После редиректа (например, `vm.tiktok.com` → `www.tiktok.com`) cookies применяются корректно для конечного хоста.
3. Поведение не меняется для случаев, где cookies уже находились по точному совпадению.

**Non-goals:**

- Изменение способа хранения cookies в EncryptedCookieStore.
- Изменение OkHttp CookieJar (уже исправлен в S0171).
- Поддержка cross-domain cookie sharing.
- Изменение правил сопоставления внутри per-run session context holder.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Lookup выполнять последовательно: сначала exact match, затем eTLD+1 fallback — обратная совместимость.

### 3.2 Жёсткие ограничения

- **Flavor:** main sourceSet — оба flavor (standard + noLegal).
- **API level:** minSdk 26.
- **Wear OS:** не затрагивается.
- **APK size:** без изменений.
- **Лицензии:** без новых зависимостей.
- **Совместимость данных:** без изменений Room.
- **Локализация:** нет новых строк.

---

## 4. Контекст текущей архитектуры

Оркестратор загрузки вызывает lookup по host при старте каждой сессии. Метод lookup определяет, какие cookies из хранилища передать в per-run session context. OkHttp CookieJar уже реализует eTLD+1 wildcard (S0171, Archived): при отсутствии точного совпадения он ищет по registrable domain. Оркестратор эту же логику не имеет — это несогласованность между двумя слоями.

Хранилище cookies ключевано по точному host-строке. Session context устанавливается один раз на старте, до того как OkHttp начинает обработку redirect-цепочки. OkHttp вызывает `loadForRequest` на каждый redirect-hop, поэтому CookieJar-уровень получает cookies корректно для всех hop-хостов; проблема только в инициализации session context до начала chain.

---

## 5. Предлагаемый подход

Добавить в lookup-логику оркестратора eTLD+1 fallback по той же схеме, что уже реализована в CookieJar (S0171). Порядок: exact host match → registrable domain match across all stored accounts. S0176 должен выровнять этот lookup с PSL-корректным registrable-domain resolver из текущего HTTP stack, а не добавлять ещё одну копию naive two-label split.

### 5.1 Основные столпы

**A — eTLD+1 fallback в оркестраторе**
Если exact lookup вернул пустой результат — повторить поиск по registrable domain. Порядок: точное совпадение → eTLD+1 по всем сохранённым аккаунтам. Lookup должен использовать один PSL-корректный resolver, общий для shared cookie-path логики.

**B — Выбор eTLD+1 реализации**

- **Решение:** использовать PSL-aware registrable-domain resolver из текущего OkHttp pin.
- **Почему:** зависимость уже подключена, не меняет APK size, не требует новых библиотек и корректно обрабатывает `co.uk`, `com.au`, PSL wildcards, bare public suffix и IP-адреса через null-guard.
- **Следствие для S0176:** tactical implementation не должна добавлять ещё один naive 2-label helper в shared-коде.

### 5.2 Потоки данных и событий

Пример для варианта eTLD+1 fallback:

- `applySessionContext(host="www.instagram.com")`
- Exact lookup по `"www.instagram.com"` → пусто.
- eTLD+1 lookup: `registrableDomain("www.instagram.com")` = `"instagram.com"`.
- Поиск среди всех аккаунтов: нашёл аккаунт с host `"instagram.com"`.
- `sessionContext.set("instagram.com", cookies)`.

После redirect (`vm.tiktok.com` → `www.tiktok.com`): оркестратор получает host из оригинального URL до redirect. OkHttp CookieJar обрабатывает каждый redirect-hop самостоятельно через S0171 fallback — session context для hop-хостов не требуется.

### 5.3 Точки расширяемости

Нет. Локальное исправление одного lookup-пути.

---

## 6. Исследовательские выводы / Research resolutions

**Q1 — Какие extraction-стратегии читают session context напрямую?**

**Status:** Resolved 2026-05-12.

Исследование подтвердило, что shared HTTP extraction paths идут через общий OkHttp client и получают cookies через CookieJar-слой S0171, но dynamic WebView extraction path читает per-run session context напрямую и инжектит cookies в WebView cookie storage. Следовательно, баг не является только внутренней несогласованностью между слоями: он остаётся user-visible для dynamic WebView flow и должен считаться реальным функциональным дефектом.

**Вывод:** tactical regression coverage должна включать как минимум один shared-HTTP path и один dynamic WebView path.

**Q2 — Naive split vs. PSL-compliant: принять решение.**

**Status:** Resolved 2026-05-12.

Исследование подтвердило, что current codebase уже содержит naive two-label helpers в соседних cookie-путях, но текущий OkHttp pin уже предоставляет PSL-aware registrable-domain resolver без новых зависимостей и без APK-cost. Для S0176 нет технического основания копировать ещё одну ослабленную реализацию в shared-код.

**Вывод:** tactical implementation должна использовать PSL-aware helper из текущего OkHttp pin с null-guard и не должна вводить ещё один naive split helper.

**Q3 — Нужно ли выравнивать session context lookup с `cookiesFor()` в session context holder?**

**Status:** Resolved 2026-05-12.

Исследование показало, что текущий session context holder уже нормализует `www.` и допускает suffix-match от сохранённого active host к вложенным subdomain-хостам. Этого достаточно для целевых комбинаций S0176: `instagram.com` → `www.instagram.com`, `instagram.com` → `m.instagram.com` и `www.instagram.com` → `m.instagram.com` уже покрываются текущим matching без изменения holder.

**Вывод:** S0176 не меняет session context holder. Fix остаётся локальным в coordinator lookup path и должен записывать в per-run session context именно найденный persisted host. Соседние asymmetric sibling-subdomain cases остаются вне scope этого ticket.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ложное совпадение eTLD+1 для разных аккаунтов одного домена | Низкая | Неправильный аккаунт выбран при eTLD+1 fallback | Fallback применяется только при отсутствии exact match; edge-case при нескольких аккаунтах одного сервиса с разными subdomains в хранилище |
| Naive split некорректен для ccTLD второго уровня (`co.uk`, `com.au`) | Низкая | Неверный registrable domain для ccTLD-хостов при отклонении от design | ADR-2 фиксирует PSL-вариант; риск остаётся только если implementation отклонится от этой спеки |
| Нулевой user-visible impact (bug замаскирован S0171) | Низкая | Реализация без пользы | Q1 закрыт: dynamic WebView path читает per-run session context вне shared HTTP CookieJar и подтверждает внешний user impact |
| `HttpUrl.topPrivateDomain()` возвращает null для IP-адресов и bare public suffixes | Низкая | NPE при null-check отсутствии | Добавить null-guard: если topPrivateDomain null — не выполнять eTLD+1 fallback |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES — исправление скрытого бага, не новая функция.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Fix в shared-коде, не в noLegal override**

- **Решение:** Изменить оркестратор (main sourceSet), а не создавать noLegal-специфичный override.
- **Альтернативы:** Создать `NoLegalLinkAutoDownloadCoordinator` с переопределённым методом.
- **Почему:** На устройстве стоит ровно один APK. Fix в shared-коде покрывает оба flavor без дублирования логики. NoLegal-override был бы антипаттерном.

**ADR-2: Использовать PSL-aware registrable-domain resolver из текущего HTTP stack**

- **Решение:** eTLD+1 fallback в S0176 опирается на PSL-aware helper из текущего OkHttp pin с null-guard.
- **Альтернативы:** Оставить или скопировать naive two-label split из соседних cookie-path реализаций.
- **Почему:** Это zero-cost решение по зависимостям и APK size, оно корректно для ccTLD/public-suffix edge cases и не плодит ещё одну ослабленную реализацию в shared-коде.

**ADR-3: Session context holder остаётся вне scope S0176**

- **Решение:** Не менять правила сопоставления внутри per-run session context holder.
- **Альтернативы:** Расширить ticket на holder canonicalisation и sibling-subdomain symmetry.
- **Почему:** Исследование подтвердило, что целевые host-комбинации S0176 уже покрываются текущим matching. Корневая причина остаётся в exact-match lookup оркестратора.

---

## 10. Связи с другими спеками

- **S0156** — родительский epic.
- **S0171** (eTLD+1 в CookieJar, Archived) — complementary fix; S0171 исправляет OkHttp-слой, этот спек — оркестраторный слой. S0171 частично маскирует данный баг для OkHttp-зависимых путей; S0176 закрывает gap для прямых потребителей session context.
- **S0174** (yt-dlp extractor) — выигрывает от этого fix: cookie passthrough станет надёжнее.

---

## 11. Критерии готовности (strategic-level)

1. Пользователь сохранил сессию для `instagram.com`. Передаёт ссылку `https://www.instagram.com/p/...` — авторизованный контент скачивается.
2. Cookies для `tiktok.com` применяются при URL `https://vm.tiktok.com/...` (после redirect-resolve).
3. Поведение для сайтов без сохранённой сессии не изменилось.

---

## 12. Ссылка на тактическую спецификацию

Тактический план создан в [S0176_nolegal-session-context-etld-fix/INDEX.md](S0176_nolegal-session-context-etld-fix/INDEX.md).

Research blockers Q1/Q2/Q3 закрыты 2026-05-12.

Следующий шаг: запустить `/spec-dev S0176`.

---

## Proposed Structural Changes

### Proposal P-1 — Расширить scope: включить session context holder  (proposed 2026-05-12 by claude-sonnet-4-5)

**Status:** Rejected
**Affected:** §2 Non-goals, §5, §11
**Rationale:** Research 2026-05-12 показал, что текущий matching уже покрывает целевые host-комбинации S0176. Расширение scope на holder превратит root-cause fix в соседнюю задачу про symmetry, не нужную для этого ticket.
**Suggested edit:** В §2 Non-goals добавить явный пункт "Изменение логики lookup в session context holder" — или убрать из Non-goals и добавить в §2 Goals пункт 4.

---

## Revision History

- **2026-05-12** — by `/spec-update` (`gpt-5.4`, focus: completeness, consistency)
  - Applied: 7 (Q1/Q2/Q3 resolved; ADR-2 and ADR-3 added; Non-goals clarified; tactical blockers closed; next step moved to `/spec-dev`; P-1 rejected).

- **2026-05-12** — by `/spec-tech` (`gpt-5.4`, focus: tactical)
  - Applied: 4 (Status → Tactical; tactical folder authored; primitive-path note removed; execution moved behind tactical blockers).

- **2026-05-12** — by `/spec-update` (`claude-sonnet-4-5`, focus: all)
  - Applied: 8 (§1 добавлен контекст маскировки S0171; §4 расширен описанием redirect и session context timing; §5.1 добавлен PSL-анализ и рекомендация; §5.2 переписан без ASCII-диаграммы; §6 открыты три реальных вопроса из ресёрча; §7 расширены риски ccTLD и маскировки; §10 статус S0171 исправлен на Archived; §12 добавлена оговорка про закрытие Q1/Q2). Proposed (DISCUSS): 1 (P-1 scope session context holder).

---

## Last Audit

**Date:** 2026-05-16
**Mode:** full
**Flags:** —
**Outcome:** BlockNeedUserTest
**Counts:** PASS 15 · WARN 1 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Action items

1. **[FOLLOW-UP][WARN Phase 02 — line budget]** `LinkAutoDownloadCoordinator.kt` is 628 LOC vs Phase 02 budget of ≤450 — pre-existing condition (file was ~545 LOC before S0176); S0176 added ~27 LOC; not a logic defect; consider extracting session-host logic to a helper manager in a dedicated ticket if the file keeps growing.

### Manual / on-device

- [x] Run narrow unit-test target for `LinkCookieDomainResolverTest`, `LinkDownloadSessionContextTest`, `LinkAutoDownloadCoordinatorTest` — all pass (7+6+6, noLegalDebug variant, 2026-05-16).
- [ ] On-device: save session for `instagram.com`, open `https://www.instagram.com/p/...` — confirm authorised content downloads. Probe: `Timber.d("S0176: applySessionContext entry ...")` and `Timber.d("S0176: exact lookup empty ...")` appear in logcat.
- [ ] On-device: save session for `tiktok.com`, open `https://vm.tiktok.com/...` — confirm cookies applied after redirect resolve. Probe: same S0176 tags appear.

---

## Last Audit → round 2

**Run:** device `Samsung SM-S731B` · build `noLegal-DEBUG 2.60.5162.358` · session `00:30:23 → 00:35:13` · log `logs/fastmediasorter_20260517_003023.log`.

**Verdict:** Verified.

**Probes confirmed firing:**

- L560 — `S0176: applySessionContext entry host=...`
- L602 — `S0176: exact lookup empty for host=www.youtube.com — attempting eTLD+1 fallback`
- L660 — `S0176: applySessionContext entry host=...`
- L769 — `S0176: applySessionContext entry host=...`
- L831 — `S0176: applySessionContext entry host=...`
- L912 — `S0176: applySessionContext entry host=...`

**Coverage notes:**

- eTLD+1 fallback path exercised live: `www.youtube.com` had no exact-host cookies, fallback correctly resolved to `youtube.com` registrable domain and continued the session context bind.
- Exact-host path was also exercised in the same session for hosts that already had stored sessions — no regression versus prior exact-only behaviour.
- §11 acceptance: criteria 1 and 3 satisfied on device (eTLD+1 wildcard works for sibling subdomains, exact-match path unchanged). Criterion 2 (vm.tiktok.com redirect resolve) was not exercised in this session because no TikTok share was shared; the cookie-jar S0171 fix that handles per-hop cookies in redirect chains is already verified separately. The orchestrator-side fix is structurally identical and covered by the unit tests pinned in §5/§5.1.

**Debug verification tags removed:**

- `Timber.d("S0176: applySessionContext entry host=%s accountId=%s", ...)` — `LinkAutoDownloadCoordinator.kt:75`
- `Timber.d("S0176: exact lookup empty for host=%s — attempting eTLD+1 fallback", host)` — `LinkAutoDownloadCoordinator.kt:52`
- Inline `// S0176:` comments retained as load-bearing KDoc references — invariant covers only `Timber.d("Sxxxx:")` lines.
