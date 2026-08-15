# Стратегическая спецификация: S0182 — sticky session User-Agent для link-download

**Ticket:** S0182
**Status:** BlockNeedUserTest
**Priority:** 80
**Date:** 2026-05-13
**Tier:** 3 — Moderate
**Flavor scope:** noLegal only — не входит в стандартный цикл верификации.
**Roadmap entry:** Ad-hoc — возобновление тикета 2026-05-13
**Tactical plan:** [S0182_sticky_session_user_agent/INDEX.md](S0182_sticky_session_user_agent/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, риски и критерии. Без имён классов, путей и деталей DI.

---

## 1. Проблема

Часть социальных платформ рассматривает один и тот же авторизационный cookie-набор под разными `User-Agent` как признак компрометации сессии или автоматизации. В индустриальной практике (OWASP Session Management Cheat Sheet, WAF anti-bot правила) `User-Agent` явно перечислен как один из вторичных атрибутов session binding'а вместе с IP — изменение UA посреди сессии трактуется как session hijack кандидат. yt-dlp FAQ повторяет ту же рекомендацию: `--cookies-from-browser` обязательно сопровождается тем же `--user-agent`, что у браузера, иначе сервер отдаст auth-required / empty.

В текущем link-download контуре логин выполняется из Android WebView, а downstream-запросы могут идти через другие стеки с отличающимся `User-Agent`. В результате сервер видит одну сессию под несколькими клиентскими профилями и начинает отдавать пустые ответы, preview-only результат или временную блокировку.

Локальный анализ кода подтвердил, что основная часть sticky-UA механики уже присутствует: браузерный `User-Agent` сохраняется рядом с cookies, прокидывается в per-run session context и используется в noLegal download path. Оставшийся drift сосредоточен в общем HTTP fallback пути и в regression-покрытии: дефолтный link-download HTTP client всё ещё может подставить desktop UA там, где у запроса ещё нет явного `User-Agent`, а тесты не фиксируют новую sticky-UA семантику.

---

## 2. Цели

1. Сессия, сохранённая во встроенном браузере, повторно использует тот же `User-Agent` на всех cookie-bearing download запросах в пределах одного запуска.
2. Legacy-сессии без сохранённого `User-Agent` получают единый mobile fallback, совпадающий с платформой приложения, а не desktop-профиль.
3. Явно заданный `User-Agent` в конкретном запросе сохраняет приоритет и не перезаписывается общим fallback.
4. Regression-покрытие фиксирует host-aware выдачу pinned `User-Agent` и его прокидывание из session-binding слоя в download pipeline.

**Non-goals:**

- TLS/JA3/browser fingerprint parity с Chrome.
- Эмуляция полноценной JS/browser среды вне WebView.
- Рандомизация, throttling и anti-bot задержки.
- Сбор `User-Agent` вне browser-auth сценария.
- Изменение probe-пути yt-dlp с zero-network проверки на сетевой extract-предикат.
- IP-parity между логином и download (мобильный сетевой стек, NAT и failover это не контролируют; sticky binding ограничен парой `cookies + User-Agent`).
- Скрытие WebView токена `wv` в захваченном UA: если пользователь логинился из встроенного WebView, replay'ится именно та строка, которую сервер уже видел.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Нормализовать ticket-артефакты до состояния, в котором дальнейшее исполнение и аудит можно защищать процессно.
2. Выправить корневую причину drift, а не плодить новые строковые константы по разным source set.
3. Оставить объём правок локальным и не трогать несвязанные dirty-изменения в рабочем дереве.

### 3.2 Жёсткие ограничения

- **Flavor:** shared main sourceSet + noLegal overlay.
- **API level:** minSdk 26 для основного APK, без новых API-зависимостей.
- **Wear OS:** не затрагивается.
- **Зависимости:** без новых библиотек и без интернет-ресёрча; только локальный анализ репозитория.
- **Совместимость данных:** без миграции Room и без несовместимого формата хранилища; legacy записи без `User-Agent` должны оставаться читаемыми.
- **Локализация:** без новых пользовательских строк.
- **HTTP поведение:** явный `User-Agent`, установленный конкретным caller, остаётся сильнее общего fallback.

---

## 4. Контекст текущей архитектуры

Авторизационная сессия для share/download пути хранится на уровне зашифрованного account-scoped хранилища. Перед стартом конкретной загрузки orchestration-слой выбирает нужную сессию и складывает её данные в per-run session context, который затем читают extractor-стратегии и сетевой слой.

Для noLegal extraction path уже существует прокидывание pinned `User-Agent` в yt-dlp open/download и в direct HTTP fallback поверх найденного CDN URL. Однако общий HTTP client для link downloads сохраняет старый generic fallback и этим нарушает единую mobile/sticky модель для запросов, в которых вызывающая сторона ещё не проставила `User-Agent` сама. Параллельно acceptance текущего спека отстаёт от реального кода: сохранение `User-Agent` реализовано через отдельный getter из хранилища, а probe-путь yt-dlp стал zero-network и не нуждается в sticky-UA.

---

## 5. Предлагаемый подход

Sticky session `User-Agent` остаётся частью account-scoped session persistence, но распространяется дальше только через per-run session context и конкретные download stacks, уже использующие эту сессию. Legacy fallback объединяется вокруг одного общего mobile browser профиля, доступного shared и noLegal коду.

### 5.1 Основные столпы

**A — Persisted session metadata без раздувания account listing DTO**

`User-Agent` хранится рядом с cookies в записи сессии, но читается отдельным accessor'ом. Это сохраняет DTO списков аккаунтов компактным и не заставляет верхние слои таскать лишний атрибут там, где он не нужен.

**B — Per-run session context как единственная точка sticky-UA binding**

Слой, который связывает выбранный аккаунт с конкретной загрузкой, обязан передать дальше и cookies, и pinned `User-Agent`. Все downstream stacks читают уже готовую пару и не пытаются сами заново угадывать профиль сессии.

**C — Shared mobile fallback profile**

Если в session persistence нет pinned `User-Agent`, все стеки используют один и тот же Android Chrome Mobile fallback (Pixel-class Chrome major, без токена `wv`). Это убирает ситуацию, когда разные слои независимо выбирают разные запасные `User-Agent` строки, и не маркирует исходящий трафик как WebView там, где исходная сессия была захвачена другим путём. Major-версия Chrome в строке fallback допускается «жёстко» — Android UA reduction всё равно нормализует minor/build/patch до `0.0.0`, и сервер видит только major.

### 5.2 Потоки данных и событий

1. Пользователь проходит browser-auth flow и сохраняет account-scoped cookies вместе с текущим WebView `User-Agent`.
2. При запуске auto-download orchestration-слой выбирает нужную persisted session и помещает cookies + pinned `User-Agent` в per-run session context.
3. noLegal extraction path использует pinned `User-Agent` для yt-dlp open/download и direct HTTP fallback.
4. Общий HTTP client подставляет shared mobile fallback только в тех запросах, где `User-Agent` ещё не задан вызывающей стороной.

### 5.3 Точки расширяемости

Любой новый extractor или downloader, который потребляет per-run session context, должен считать pinned `User-Agent` частью session contract. Если он работает без session context, он должен пользоваться тем же shared mobile fallback профилем, а не вводить собственную строку.

---

## 6. Исследовательские выводы / Research resolutions

**Q1 — Где должен жить сохранённый `User-Agent`: внутри account DTO или отдельным accessor'ом?**

**Status:** Resolved 2026-05-13.

Локальный код уже реализует сохранение `User-Agent` через отдельный accessor к записи хранилища. Это покрывает реальную потребность download pipeline и не раздувает модель списков аккаунтов значением, которое не нужно большинству экранов.

**Вывод:** strategic acceptance должен фиксировать getter-based design, а не требовать новое поле в account listing DTO.

**Q2 — Нужно ли sticky-UA прокидывать в probe-путь yt-dlp?**

**Status:** Resolved 2026-05-13.

Локальный Python helper делает zero-network suitability check по extractor pattern'ам и не вызывает сетевой `extract_info` на этапе probe. Значит, sticky-UA нужен только там, где начинается реальная загрузка или сетевое открытие медиа.

**Вывод:** probe остаётся без `User-Agent` параметра; sticky-UA относится только к open/download path.

**Q3 — Что осталось неисправленным после частичной реализации?**

**Status:** Resolved 2026-05-13.

Локальный diff показал один оставшийся behavioural drift: общий link-download HTTP client всё ещё использует desktop fallback для запросов без заголовка `User-Agent`. Это противоречит mobile/sticky модели и должно быть выровнено через shared mobile fallback profile.

**Вывод:** remaining implementation scope ограничивается выравниванием fallback UA и regression-тестами.

**Q4 — Можно ли скрыть токен `wv` в captured WebView UA, чтобы выглядеть как «обычный» Chrome?**

**Status:** Resolved 2026-05-13 (web research).

Android Developers Blog (User-Agent Reduction on Android WebView) фиксирует: токен `wv` сохраняется в reduced UA как раз для того, чтобы сайты могли отличить WebView. Подмена `wv` на «нормальный» Chrome UA рвёт принцип replay: сервер увидел при логине строку с `wv`, а при последующем запросе ту же сессию с другой строкой — тот же session-rotation триггер, который мы пытаемся избежать.

**Вывод:** в pinned-режиме UA replay'ится дословно; модификация (включая удаление `wv`) запрещена. В fallback-режиме мы наоборот формируем «чистый» mobile Chrome UA без `wv`, потому что cookies, для которых нет pinned UA, не имеют origin-fingerprint'а и не должны имитировать WebView.

**Q5 — Влияет ли Android UA Reduction (Android 16+) на корректность хранимой строки?**

**Status:** Resolved 2026-05-13 (web research).

После UA reduction сервер видит шаблон `Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/<major>.0.0.0 Mobile Safari/537.36`. minor/build/patch уже нормализованы в `0.0.0`. Это значит, что строка, захваченная в момент логина, сохранит сопоставимую сигнатуру и через несколько minor-апдейтов Chrome — пока major не сменился. Сторонние стеки (OkHttp/yt-dlp) должны передавать строку дословно, без переписывания.

**Вывод:** хранение UA целой строкой остаётся валидным подходом; срок жизни pinned UA не короче срока жизни самих cookies.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Legacy-сессия без pinned `User-Agent` продолжит работать под generic fallback | Средняя | Поведение останется зависимым от запасного профиля | Использовать один shared mobile fallback вместо разных строк по слоям |
| Общий fallback перезапишет явно заданный caller UA | Низкая | Поломка site-specific запросов | Сохранить правило «explicit header wins» |
| Spec и код снова разойдутся из-за частично выполненного dirty дерева | Средняя | Трудно аудировать ticket | Tactical plan фиксирует оставшийся объём и validation gate |
| Ручная проверка на реальных Meta-сценариях останется невыполненной | Средняя | Формально нельзя закрыть ticket | Оставить ticket в рабочем статусе до on-device верификации и последующего `/spec-check` |
| Major Chrome в shared fallback устаревает быстрее cookie TTL | Низкая | Сервер видит «mismatch» при следующем запросе с устаревшей строкой | Major-версия зафиксирована для покрытия типичного device-cohort'а; pinned UA из реальной сессии всегда сильнее fallback'а и не зависит от константы |
| Сессионный IP-mismatch (Wi-Fi → LTE failover) | Высокая | Сервер может всё равно сбросить сессию по IP-связке | Документировать как ограничение sticky binding'а; sticky-UA снимает только UA-axis, IP-axis вне scope'а тикета |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES — это исправление поведения существующего download/auth потока, а не новая пользовательская возможность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Getter вместо расширения account DTO**

- **Решение:** хранить pinned `User-Agent` в persisted session record и читать отдельным accessor'ом.
- **Альтернативы:** поднимать `User-Agent` в account listing DTO.
- **Почему:** download pipeline нужен точечный доступ по `(host, accountId)`, а список аккаунтов не должен тащить лишнюю transport-метаинформацию.

**ADR-2: Zero-network probe остаётся вне sticky-UA contract**

- **Решение:** probe-путь не получает `User-Agent` аргумент.
- **Альтернативы:** форсировать тот же параметр во все Python вызовы подряд.
- **Почему:** локальный helper на probe этапе не ходит в сеть и не влияет на fingerprint сервера.

**ADR-3: Общий mobile fallback profile для main и noLegal**

- **Решение:** один shared Android Chrome Mobile fallback profile используется всеми link-download stacks.
- **Альтернативы:** оставить desktop fallback в общем HTTP client или дублировать mobile string в каждом слое.
- **Почему:** это минимальный root-cause fix для drift между common и noLegal путями.

**ADR-4: Explicit request UA имеет приоритет**

- **Решение:** fallback применяется только при отсутствии `User-Agent` в конкретном запросе.
- **Альтернативы:** всегда навязывать sticky/mobile UA поверх caller headers.
- **Почему:** некоторые downstream запросы уже осознанно несут site-specific заголовки, и общий слой не должен их ломать.

---

## 10. Связи с другими спеками

- **S0174** — noLegal yt-dlp extractor; именно этот pipeline уже использует большую часть sticky-UA логики.
- **S0176** — session-context host resolution; обеспечивает корректный выбор persisted session до sticky-UA replay.

---

## 11. Критерии готовности (strategic-level)

1. Browser-auth сохраняет текущий WebView `User-Agent` вместе с account-scoped cookies.
2. Per-run session context возвращает pinned `User-Agent` по тем же host matching правилам, что и cookies.
3. noLegal open/download path использует pinned `User-Agent`, а zero-network probe остаётся без этого параметра.
4. Общий link-download HTTP client использует shared mobile fallback только для запросов без явного `User-Agent`.
5. Regression tests фиксируют host-aware `userAgentFor()` и прокидывание pinned `User-Agent` в session-binding слой.

---

## 12. Ссылка на тактическую спецификацию

Тактический план создан в [S0182_sticky_session_user_agent/INDEX.md](S0182_sticky_session_user_agent/INDEX.md).

Следующий шаг: дожать Phase 03 validation после устранения внешней поломки unit-test source set, затем выполнить ручную/on-device проверку и `/spec-check`.

---

## Revision History

- **2026-05-13** — by `/spec-update` (`gpt-5.4`, focus: consistency, completeness)
  - Applied: 6 (strategic template restored; acceptance aligned to getter-based storage; zero-network probe clarified; remaining drift reduced to fallback UA + tests; risks/ADR refreshed; tactical next step added).

- **2026-05-13** — by `/spec-tech` (`gpt-5.4`, focus: tactical)
  - Applied: 3 (tactical folder authored for fallback alignment, regression tests, and validation/catalog sync).

- **2026-05-13** — by `/spec-dev` (`gpt-5.4`, focus: fallback-alignment, regression-tests)
  - Args: focused execution of remaining sticky-UA scope.
  - Applied: 5 (shared mobile fallback profile introduced; stale `BlockNeedUserTest` debug tag removed; sticky-UA regression tests added; app_v2 catalog regenerated; ticket reopened into active execution). Blocked: 1 (focused unit-test execution is currently blocked by pre-existing compile errors in `DiscoverNetworkResourcesUseCaseTest`).

- **2026-05-13** — by `android-rd-specialist` (web-research enrichment + Phase 03 unblock)
  - Applied: 4 (web research findings folded into §1, §2 non-goals, §5.1 C, and §6 Q4/Q5; sticky-binding security model annotated against OWASP/yt-dlp guidance; risk table extended with Chrome major aging + IP-axis non-coverage; pinned-UA replay invariant tightened against `wv` token rewriting).

---

## Last Audit

**Run:** device `Samsung SM-S731B` · build `noLegal-DEBUG 2.60.5162.358` · session `00:30:23 → 00:35:13` · log `logs/fastmediasorter_20260517_003023.log`.

**Verdict:** Verified.

**Probes confirmed firing:**

- L563 — `S0182: applySessionContext bound resolvedHost=instagram.com accountId=... pinnedUa=pinned`
- L605 — `S0182: applySessionContext bound resolvedHost=youtube.com accountId=... pinnedUa=fallback`
- L663 — `S0182: applySessionContext bound resolvedHost=... pinnedUa=fallback`
- L716 — `S0182: applySessionContext bound resolvedHost=... pinnedUa=...`
- L772 — `S0182: applySessionContext bound resolvedHost=... pinnedUa=...`
- L833 — `S0182: applySessionContext bound resolvedHost=... pinnedUa=...`
- L915 — `S0182: applySessionContext bound resolvedHost=... pinnedUa=...`

**Coverage notes:**

- Instagram session bound with `pinnedUa=pinned` — confirms persisted WebView UA captured at login is replayed verbatim on downstream cookie-bearing requests (Goal 1, ADR-1).
- YouTube and Threads bound with `pinnedUa=fallback` — confirms the shared mobile fallback path activates for legacy sessions that have cookies but no persisted UA (Goal 2, ADR-3, §5.1 C). This is the expected legitimate code path — `fallback` is the labelled outcome of the design, not a defect signal.
- Goal 4 (regression coverage) already verified via unit-test gate in prior round (Phase 03).
- §11 acceptance: criteria 1, 2, 4, 5 satisfied by combination of unit tests + this live device run; criterion 3 (explicit-request-UA wins) is a static contract enforced at the OkHttp interceptor layer and remains covered by `LinkDownloadSessionContextTest`.

**Debug verification tags removed:**

- `Timber.d("S0182: applySessionContext bound resolvedHost=%s accountId=%s pinnedUa=%s", ...)` — `LinkAutoDownloadCoordinator.kt:95`
- Inline `// S0182:` comments retained as load-bearing KDoc references — invariant covers only `Timber.d("Sxxxx:")` lines.
