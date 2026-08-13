# Стратегическая спецификация: S0822 - Instagram authenticated extraction still fails with saved cookies

**Ticket:** S0822
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-30
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - log analysis 2026-06-30

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-30

**Ключевые логи:**

- `logs/fastmediasorter_20260630_022427.log:1059` - успешно сохранён второй Instagram-account (`reused=false`).
- `logs/fastmediasorter_20260630_022427.log:1127-1128` - story extraction падает с `You need to log in to access this content`.
- `logs/fastmediasorter_20260630_022427.log:1189-1190` и `1251-1252` - повторяемый story failure на разных story URLs и даже при выборе другого accountId.
- `logs/fastmediasorter_20260630_022427.log:1087`, `1597`, `1653`, `1958` - reels дают `Instagram sent an empty media response` при `sessionApplied=true`.

Важная деталь: перед падением лог прямо показывает `cookies=9`, `session context applied`, `CookieFileWriter: wrote 9 cookies for host=www.instagram.com`. То есть path уже считает себя аутентифицированным.

---

## 1. Проблема

Исправление регистрации второго Instagram-account сработало, но следующий слой пайплайна остаётся дефектным: authenticated extraction для Instagram links всё ещё ненадёжен даже при сохранённых и применённых cookies. Для stories это воспроизводится как явный auth failure, для части reels - как `empty media response` при уже применённой session context.

Иначе говоря, account registration и account usage больше не одна проблема. Регистрация теперь проходит, а extraction path после применения cookies всё ещё не гарантирует доступ к контенту.

---

## 2. Цели

1. Добиться, чтобы authenticated Instagram links реально открывались после сохранения и выбора account session.
2. Развести в диагностике "cookies не сохранены / не применены" и "provider/extractor path сломан несмотря на applied session".
3. Стабилизировать хотя бы один рабочий extraction route для authenticated stories и private/follow-gated media.

**Non-goals:**

- Не возвращаться к already-fixed second-account WebView redirect bug.
- Не переписывать весь Instagram/Threads extractor stack без evidence-driven scope.
- Не гарантировать обход platform-side restrictions, если контент реально недоступен даже авторизованному пользователю.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Нужно сохранить уже рабочий fix второй регистрации аккаунта и не сломать multi-account selection.
2. Желательно минимизировать повторную авторизацию, если cookies уже валидны.

### 3.2 Жёсткие ограничения

- **Flavor:** минимум noLegal debug, где проблема подтверждена; проверить общий shared path для остальных flavor, если extractor code общий.
- **API level:** без API-specific веток.
- **Wear OS:** не затрагивается.
- **Производительность:** не плодить лишние сетевые retry loops и redirect storms.
- **Совместимость данных:** existing saved sessions/accounts сохраняются.
- **Локализация:** новых пользовательских строк лучше не добавлять без необходимости.
- **Доступность:** не относится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0749 (second-account registration), S0211 (archived account dedup), S0260 (archived session-context instrumentation).

---

## 4. Контекст текущей архитектуры

Auth session сначала сохраняется через browser/WebView login flow, затем при link-share/download выбранный accountId должен загрузить cookies, применить session context и передать её downstream extraction strategies. В логах эта часть выглядит успешной: account chosen, cookies loaded, session applied, cookie file written.

Проблема проявляется дальше, внутри extraction route. Значит дефект либо в том, как cookies/UA доходят до конкретного extractor, либо в том, что выбранный extractor path не умеет корректно использовать уже применённую authenticated session для части Instagram content types.

---

## 5. Предлагаемый подход

Нужно расследовать extraction stack по типам Instagram content и отделить: story auth path, reel/media path, selection among multiple saved accounts, cookie jar/file hand-off, UA pinning и fallback ordering между strategies.

### 5.1 Основные столпы / модули

- Evidence-driven audit of authenticated extraction path.
- Separation of story-specific and reel/media-specific failures.
- Cleanup of stale debug instrumentation while preserving actionable diagnostics.

### 5.2 Потоки данных и событий

- User selects saved Instagram account -> session context applied -> extractor strategy chosen -> authenticated media fetch succeeds.

### 5.3 Точки расширяемости

- Diagnostics should stay useful for future provider regressions without relying on stale `Sxxxx` probes.

---

## 6. Открытые вопросы / Research items

1. ~~Почему story URLs всё ещё получают auth failure при applied cookies.~~ **Resolved (2026-06-30):** баг формата cookie-файла. Netscape HTTP Cookie File ожидает в поле expiry абсолютный Unix-timestamp, а `CookieFileWriter` писал туда `HttpCookie.maxAge`, который `EncryptedCookieStore` восстанавливает как относительную длительность в секундах (`(expires - now) / 1000`). Для `sessionid`/`ds_user_id` (~1 год) yt-dlp читал expiry ≈ 1971 год, считал куку просроченной и отбрасывал её - отсюда `You need to log in` при `cookies=9`.
2. ~~Являются ли `empty media response` для reels тем же root cause.~~ **Resolved (2026-06-30):** да, та же ветка. Без auth-куков yt-dlp обращается к Instagram неаутентифицированным, и reels отдают пустой media response. Оба симптома - следствие одной cookie-expiry ошибки.
3. ~~Не мешает ли multi-account cookie loading.~~ **Resolved (2026-06-30):** нет. `CookieFileWriter` мерджит куки по eTLD+1 с дедупом по имени (first-wins), повторного чтения двух accountId в один extractor path нет. Дефект был чисто в сериализации expiry, не в выборе аккаунта.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Попытка лечить только один extractor path оставит второй сломанным | Высокая | Частичный фикс без реальной пользы | Делить сценарии по content type и фиксировать каждый явно |
| Исправление сломает уже рабочий second-account save flow | Средняя | Возврат S0749 | Держать registration path вне scope и regression-test multi-account save/use |
| Слабая диагностика снова смешает auth save и auth use в одну проблему | Средняя | Трудно проверять future regressions | Явно разделить logging по phases registration vs session application vs extractor fetch |

---

## 8. Связи с другими спеками

- S0749 - исходный blocker второй регистрации аккаунта; теперь verified separately.
- S0211 / S0260 - stale archived instrumentation в той же области, желательно убрать или заменить.

---

## 9. Критерии готовности (strategic-level)

1. Instagram story links открываются при наличии валидной сохранённой session без ложного `You need to log in`.
2. Reels/posts не деградируют по сравнению с текущим состоянием.
3. Multi-account selection использует выбранный account deterministically.
4. Диагностика отделяет registration success от extractor auth failure.

---

## Implementation (2026-06-30)

**Root cause:** `CookieFileWriter` писал относительный `HttpCookie.maxAge` в поле абсолютного expiry Netscape cookie-файла, из-за чего yt-dlp отбрасывал все долгоживущие auth-куки (`sessionid` и пр.) как просроченные.

**Fix:**

- `app_v2/src/noLegal/.../CookieFileWriter.kt` - конвертация `maxAge` в абсолютный epoch-timestamp: `expiry = nowEpochSeconds + maxAge` для `maxAge >= 0`; session-куки (`maxAge < 0`) остаются `0` (Netscape session marker).
- `app_v2/src/testNoLegal/.../CookieFileWriterTest.kt` - регрессионная проверка: поле expiry должно быть будущим абсолютным timestamp, не сырым `maxAge`.

**Out of scope (отдельные тикеты):** очистка stale `S0211`/`S0260` инструментации в этой области - S0823.

**Verification gate:** требует реального устройства с валидной сохранённой Instagram-session - проверить, что authenticated story и reel links реально скачиваются после фикса (эмулятор без живого аккаунта недостаточен).

---

## Device test 2026-06-30 (вечер) - FAILED + diagnostics follow-up

**Лог:** `logs/fastmediasorter_20260630_213840.log` (сборка содержит S0822 cookie-expiry фикс - тег `S0822: cookie file written` присутствовал).

**Результат:** cookie-expiry фикс необходим, но **недостаточен**. Два reel-share (`DaFfM_foxGQ`, `DaK7JhDIOdN`) не скачались даже после ре-логина обоих аккаунтов.

**Что реально происходит (по слоям):**

- yt-dlp (`==2026.6.9`, свежий) с применёнными куками отдаёт `Instagram sent an empty media response` -> `not-found`. Это известный апстрим-лимит yt-dlp (yt-dlp#13551 / #17074): Instagram отдаёт пустой media response для reels даже авторизованному клиенту (флаг IP/датацентра, age-gate, фингерпринт). **Принято как upstream-ограничение - на нашей стороне надёжно не лечится.**
- OkHttp-стратегии (direct/html/webview) падают в `probe()` с `ProtocolException: Too many follow-up requests: 21` - это login/checkpoint wall Instagram, гоняющий запрос по >20 редиректам.
- Этот redirect-loop классифицировался как generic `ProbeResult.TransientError` -> немой `Result.Failed.NoMediaFound`. Пользователь видел «нет медиа» вместо «нужен ре-логин».

**Diagnostics fix (этот заход):**

- `LinkAutoDownloadCoordinator` - okhttp redirect-loop (`Too many follow-up requests`) на curated social-хосте (`KnownAuthResources.matchHost != null`) теперь поднимает `Result.Failed.AuthRequired` вместо `NoMediaFound`. `AuthRequired` уже даёт actionable UX (re-login WebView в foreground share-пути / toast `s0116_toast_auth_required`). Без новых result-типов и строк.
- Детектор `isLoginWallRedirectLoop` ходит по cause-chain (глубина 8) и матчит маркер okhttp.

**Out of scope (upstream):** `empty media response` для reels не закрываем - зависит от yt-dlp/Instagram.

**Verification gate (этот заход):** на реальном устройстве шарить Instagram reel, чей запрос Instagram бросает в login-redirect-loop (протухшая/зачекпойнченная session) - ожидается re-login prompt / auth-required toast вместо тихого «no media». `BlockNeedUserTest` probe: `Timber.d("S0822: login-wall redirect loop reclassified ..")` в координаторе.
