# Стратегическая спецификация: S0186 — noLegal: устойчивость extraction-каскада к yt-dlp PyException

**Ticket:** S0186
**Status:** BlockNeedUserTest
**Priority:** 75

<!-- auto-approved by /spec-all — 2026-05-14 -->

**Date:** 2026-05-14
**Tier:** TBD
**Roadmap entry:** Ad-hoc — device test 2026-05-14
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical spec:** [`PLAN/S0186_nolegal-pipeline-cascade-resilience/INDEX.md`](S0186_nolegal-pipeline-cascade-resilience/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

В noLegal flavor `YtDlpExtractionStrategy.open()` запускает Chaquopy/Python yt-dlp. Любой `DownloadError` от yt-dlp поднимается как `com.chaquo.python.PyException` через JNI и обрывает цикл extraction-стратегий: следующие стратегии (`site/NewPipe`, `direct`, `html`, `dynamic`) даже не получают шанс probe.

Device-тест 2026-05-14 (`fastmediasorter_20260514_004916.log`) показал:
- YouTube `Requested format is not available` → PyException → `S0170: result=Other` без попытки NewPipe.
- Цепочка остановлена на первой стратегии, которая выдала исключение, а не `NotApplicable`.

При этом дизайн S0174 (§5.1 пункт C, §11.6) явно говорит: "при недоступности yt-dlp (simulate timeout, extractor error) — управление передаётся NewPipeExtractor и WebView-fallback без ошибки для пользователя." Это поведение не реализовано.

Дополнительный наблюдаемый эффект: для YouTube/YTMusic это означает 100%-ный отказ share-flow (см. S0185); для других сайтов yt-dlp transient errors (rate limit, временная блокировка extractor) также превращаются в `Other` вместо graceful degradation.

---

## 2. Цели

1. Любое исключение, поднятое из `YtDlpExtractionStrategy.probe()` или `.open()` — Chaquopy PyException, IOException, любые другие — конвертируется в `NotApplicable` или `Failed-but-continue` так, чтобы следующая стратегия в registry получила управление.
2. Поведение симметрично для всех стратегий: ни одна не должна обрывать каскад через uncaught exception; `LinkExtractionRegistry` гарантирует продолжение fallback chain.
3. Логирование сохраняется: каждое исключение из стратегии логируется с тегом стратегии и URL, но не прерывает chain.
4. Финальный исход `result=Other` возможен только если **ни одна** стратегия не вернула positive result — а не если первая обрушилась.

**Non-goals:**

- Платформо-специфичные исправления (YouTube fix покрывает S0185).
- Изменение порядка стратегий в registry.
- Performance optimization каскада.

---

## 3. Ограничения

- **Flavor:** main sourceSet — общий фикс, применяется к обеим cascadable flavor (standard и noLegal).
- **API level:** minSdk 26.
- **Wear OS:** не затрагивается.
- **APK size:** без изменений.
- **Локализация:** без новых строк.

---

## 4. Контекст текущей архитектуры

`LinkExtractionRegistry` хранит `Set<UrlExtractionStrategy>` через Hilt `@IntoSet` multibinding и сортирует по `CANONICAL_ORDER`. `LinkAutoDownloadCoordinator.handleUrl()` (или его helper) итерирует стратегии: для каждой вызывает `probe()`, при `Applicable` — `open()`. Если стратегия бросает exception вместо возврата `NotApplicable` / `Failed`, цикл прерывается.

`YtDlpExtractionStrategy.open` пробрасывает `PyException` дальше вверх — coroutine падает, coordinator ловит generic `Throwable` (если ловит вообще), и share-flow заканчивается на `result=Other` (см. `S0170` тег в логах).

Контракт `UrlExtractionStrategy` не специфицирует "должна ли стратегия catch'ить свои исключения": это implicit — на ревью разные стратегии ведут себя по-разному (`HtmlPageExtractionStrategy` ловит, `YtDlpExtractionStrategy` — нет).

---

## 5. Предлагаемый подход

Сделать catch-all реализацией в caller, а не в каждой стратегии:

**A — try/catch wrapper в registry / coordinator**

`LinkAutoDownloadCoordinator` (или helper, итерирующий стратегии) оборачивает каждый `strategy.probe()` и `strategy.open()` в try-catch. На любой `Throwable` (кроме `CancellationException`):
- логировать `Timber.w(t, "strategy=$strategyId failed, continuing chain")`;
- считать стратегию `NotApplicable` для этого URL;
- продолжить со следующей стратегии в порядке регистра.

**B — Контракт `UrlExtractionStrategy` дополнить документацией**

В KDoc интерфейса зафиксировать: "Реализация может бросать исключения; coordinator гарантированно их перехватывает. Возврат `NotApplicable` предпочтительнее throw'а для известных платформо-специфичных отказов (extractor mismatch, unsupported URL)."

**C — `CancellationException` пробрасывать наружу**

Структурированная concurrency: пользователь отменил share-flow → cancellation должен прерывать каскад. `CancellationException` (kotlinx.coroutines) **не** ловится wrapper'ом.

### Потоки

`coordinator → strategy.probe()` → try/catch → если throw: log + `NotApplicable` → next strategy.
`coordinator → strategy.open()` → try/catch → если throw: log + `Failed(strategyId, t)` → next strategy.

---

## 6. Открытые вопросы

1. **Где разместить wrapper** — `LinkAutoDownloadCoordinator` (где сейчас итерация) или helper `StrategyChainExecutor`? Решить в `/spec-tech` исходя из LOC budget координатора (S0176 audit показал 572 LOC vs ≤450 budget).
2. **Какие throwable различать** — `PyException`, `IOException`, `TimeoutException` логировать с разными severity (warn/info)? Унифицировать единым обработчиком или per-class?
3. **Тесты** — какие минимальные unit-тесты на cascade resilience: mock strategy throw + verify next strategy probe вызван?

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Catch-all маскирует реальные баги в реализациях стратегий | Средняя | Сложнее диагностировать regressions | Обязательное `Timber.w(t, ...)` логирование на каждый caught exception; severity warn (не debug) |
| `CancellationException` случайно пойман — share не отменяется | Низкая | Тесты + явная проверка типа исключения | |
| `Failed(strategyId, t)` коммитируется в audit log, race с success стратегии после fail | Низкая | Mixed result в S0170 | Audit-tag только при финальном исходе, не при per-strategy fail |

---

## 8. Влияние на пользователя

Без новых видимых функций. Косвенно: YouTube/YTMusic share становится надёжнее (NewPipe fallback после yt-dlp PyException); другие сайты с transient yt-dlp errors переходят на html/dynamic вместо мгновенного `Other`. Документация — не требуется (skip docs/FEATURES update; см. CLAUDE.md правило "feature docs only для new user-visible capability").

---

## 9. ADR

ADR-1 (предварительно): wrapper в coordinator-уровне, не per-strategy — окончательно в `/spec-tech`.

---

## 10. Связи с другими спеками

- **S0156** — родительский epic noLegal.
- **S0174** (Broken) — yt-dlp; S0186 даёт graceful path при yt-dlp failures.
- **S0175** (Verified) — NewPipe; становится фактически достижимым fallback'ом.
- **S0185** (NEW Draft) — YouTube recovery; complementary, оба требуются для полного фикса YouTube share-flow.

---

## 11. Критерии готовности (strategic-level)

1. Mock strategy, бросающая `RuntimeException` в `probe()` или `open()` → следующая стратегия в chain получает probe.
2. Реальный YouTube share с устаревшим yt-dlp pin → после PyException вызывается NewPipe → если NewPipe тоже не справился → html/dynamic.
3. Cancellation share-flow прерывает каскад немедленно (CancellationException пробрасывается).
4. Каждое caught исключение видно в логе как `Timber.w` с указанием стратегии и URL.

---

## 12. Следующий шаг

Spec catalog status auto-progressed by `/spec-all`: Draft → Approved → Tactical → In Progress → Implemented → BlockNeedUserTest. On-device verification pending.

---

## Last Audit

**Date:** 2026-05-14
**Mode:** full (strategic + tactical phases 01-02)
**Flags:** —
**Outcome:** Verified (static) — status held at `BlockNeedUserTest` pending device test §11.2
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 3

### Verification matrix

- §11.1 cascade fall-through unit test — PASS (`open_throwing_strategy_does_not_abort_cascade` at `LinkAutoDownloadCoordinatorTest.kt:147`).
- §11.3 cancellation propagation unit test — PASS (`cancellation_in_open_propagates_immediately:179`).
- §11.4 caught exception logged with strategy id — PASS (`Timber.w(throwable, "LinkAutoDownloadCoordinator: open threw for %s", strategy.id)` at coordinator:197; mirrors existing probe wrapper convention at :173).
- Phase 02 wrap shape — PASS (`LinkAutoDownloadCoordinator.kt:186-200` matches tactical step 2 verbatim; `Throwable` catch, `CancellationException` re-throw, `Timber.w` + `Timber.d("S0186:..")` + `continue`).
- Phase 02 debug tag count — PASS (exactly one `Timber.d("S0186:` hit across `.kt` at coordinator:198).
- Phase 01 test set — PASS (3 new `@Test` methods present; regression test `probe_throwing_already_handled_does_not_regress:213` guards the pre-existing probe wrapper).
- Phase 01 production code touch — PASS (test-only).
- Test signature compatibility — PASS (`ProbeResult.Applicable(tentativeMime, tentativeSizeBytes)` and `OpenResult.NotFound(reason)` match production sealed interfaces in `UrlExtractionStrategy.kt:17,46`).
- Debug-tag invariant for `BlockNeedUserTest` — PASS (1 tag present, expected ≥1).
- Dev log coverage — PASS (`S0186` recorded in `dev/CHANGELOG.md` lines 9516-9521; implementation entry at 9521).
- Build gates (cached from `/spec-all` 2026-05-14) — PASS (`assembleStandardDebug` 37 s; `assembleNoLegalDebug` 50 s; `:app_v2:testNoLegalDebugUnitTest` 6/6 green).
- Catalog presence — PASS (`LinkAutoDownloadCoordinator` listed in `dev/CATALOG/app_v2.jsonl`; gitignored, local-only).
- FEATURES update — EXEMPT (§8 explicitly states "Без новых видимых функций"; no public-facing capability added).
- §3 file-size budget — EXEMPT (coordinator 604 LOC vs 450 budget noted as out-of-scope per tactical INDEX, separate refactor ticket).
- Flavor gating — EXEMPT (main sourceSet fix, shared across `standard` and `noLegal`; no `BuildConfig` flag).

### Manual / on-device (gates `BlockNeedUserTest`)

- [ ] Share a YouTube link in noLegal flavor. In logcat expect: yt-dlp `PyException` → `S0186: open() threw for ytdlp, continuing cascade` → NewPipe (or subsequent strategy) probe attempted. The final `S0170: link share result` must not be `result=Other` purely because of the PyException — the cascade must have continued.

### Status decision

Per operator: device test not yet performed (2026-05-14). Verdict is `Verified` against static checks, but journal status is **held** at `BlockNeedUserTest` to preserve the device-test probe (`Timber.d("S0186:..")` tag retained). After successful logcat verification, run `/spec-check S0186` again — it will then flip to `Verified` and strip the tag.
