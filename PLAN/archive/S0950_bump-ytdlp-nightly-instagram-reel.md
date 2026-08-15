# Спецификация (compact bugfix): S0950 - Бамп yt-dlp nightly ради Instagram reels

**Ticket:** S0950
**Status:** Archived
**Priority:** 85
**Date:** 2026-07-05
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05

**Текст:**

> Бампнуть yt-dlp nightly ещё раз

Продолжение S0935 (Verified): каскадный app-баг починен, но reels всё равно не скачиваются - Instagram отдаёт HTTP 404 на `extract_info` и 68-80-байтные заглушки на `dynamic`. В S0935 §3 это вынесено в upstream-лимит (ref S0822). Владелец решил догнать upstream-фикс новым nightly.

---

## 1. Проблема / симптом

yt-dlp закреплён на nightly `2026.07.02.234458` (пин от 2026-07-03). Device-тест S0935 (лог `fastmediasorter_20260705_002529.log`) показал: reels по-прежнему дают `HTTP 404` в yt-dlp и `DownloadCorrupted` (68/68/80 байт) в `dynamic` - реального .mp4 нет.

Свежее nightly `2026.07.04.221833` (2026-07-04) содержит новые Instagram-фиксы поверх нашего пина:
- `Instagram: Rework extractor` (#17075) - **с follow-up фиксом `8b8e3e3`**, которого в `2026.07.02.234458` ещё не было.
- `Instagram: Detect when cookies are invalidated` (#17126) - прямо про режим протухшей/зачекпойнченной сессии (ref S0822).

---

## 2. Корневая причина

Upstream: экстрактор Instagram в yt-dlp `2026.07.02.234458` отстаёт от актуального ответа Meta -> reel-404. Не app-баг (app-часть закрыта в S0935). Единственный доступный рычаг - обновить бандленный yt-dlp до nightly с более свежим экстрактором.

---

## 3. Исправление

Единственный файл: `app_v2/build.gradle.kts`, Chaquopy pip-блок флейвора noLegal. Пин `2026.07.02.234458` -> `2026.07.04.221833` (GitHub nightly sdist tarball). Комментарий обновлён с обоснованием.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0935 (Verified, app-каскад), S0822 (BlockNeedUserTest, stale-session redirect-loop - #17126 может закрыть его upstream-часть)
- **Flavor:** noLegal (yt-dlp живёт только там)

---

## 4. Проверка

1. Build (dependency touch, нужен pip-resolve + packaging): `.\a.ps1 nd` (noLegal debug) - PASS = tarball скачался и APK собрался.
2. Device (BlockNeedUserTest): переустановить noLegal debug, шарить серию живых Instagram reels. Ожидать: reels скачиваются в реальный .mp4 (`FellBackToDownloads`/в ресурс), а не `DownloadCorrupted`. Проверить хотя бы один протухший-session-кейс (#17126) - должен быть внятный сигнал вместо тихого redirect-loop.
3. Если reels всё ещё дают заглушки на всех попытках - upstream ещё не поспел, зафиксировать и ждать следующий nightly (фикс каскада S0935 всё равно валиден).

## Last Audit

### Manual / on-device

- [x] Build noLegal debug with bumped yt-dlp tarball - PASS - verified on-device 2026-07-09
- [x] Share real public Instagram reel URL -> real .mp4 lands in Downloads/resource (not `DownloadCorrupted` stub) - PASS - verified on-device 2026-07-09 (3/3 reels: 1,561,291 / 2,047,402 / 5,450,350 bytes, all `FellBackToDownloads`, zero `DownloadCorrupted`)
- [ ] Stale/checkpointed-session case (#17126) -> clear re-login signal, not silent redirect-loop - SKIPPED (out-of-scope: no credentialed/stale-session test fixture available under no-login constraint; remains S0822's own open item)

## Revision History

- **2026-07-09** - by `/spec-test-device` (`claude-sonnet-5`, device: emulator-5554 Android 13)
  - Scenario: temp/S0950/mobile_test_scenario_20260709_2225.md · PASS/FAIL/SKIPPED 2/0/1 · Errors in log: 0
