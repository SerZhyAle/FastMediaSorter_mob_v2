# Спецификация (compact bugfix): S1108 - Небезопасный сниппет публикации в README банка стримов

**Ticket:** S1108
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-19
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-19

**Захвачено во время:** ad-hoc задача документирования подсистемы Streams (source-spec для FastMediaSorter for Windows). Находка §3.1, запаркована без переключения активной задачи.

**Текст:**

Out-of-scope finding discovered during streams-subsystem documentation research.

- `delivery/stream-catalog/README.md:33` contains a packaging snippet `Compress-Archive -Path delivery/stream-catalog/streams.csv ...` that zips ONLY streams.csv WITHOUT favicon-atlas.png. This bypasses the S0925 publish guard and reproduces the exact failure mode that already wiped channel favicons from production twice: S0785 (2026-07-03) and a recurrence on 2026-07-12 when the same raw snippet was copied into a spec-prerelease.md Step 0 publish path. Confirmed in agent memory project_stream_catalog_atlas_publish.md.
- The single safe source of truth for publishing the bank is `Invoke-PublishCatalog` in scripts/streams/collect-stream-candidates.ps1:1052-1115 (carries the S0925 guard that refuses to publish a CSV containing favicon_index without an accompanying favicon-atlas.png, and enforces streams.csv as zip entry 0 + atlas <= 3 MiB).
- Same README also carries a stale inventory snapshot (README.md:177-189 says "426 streams") vs the actual live file: delivery/stream-catalog/streams.csv = 966,495 bytes, 2692 lines (1 header + 2691 channels).

Proposed scope (for later, not now):
- Reduce delivery/stream-catalog/README.md to a single source of truth: delegate publishing to Invoke-PublishCatalog; remove/replace the raw Compress-Archive snippet with a warning + pointer.
- Grep the repo for other unguarded publish sites (raw `gh release upload delivery-so-v1`, `Compress-Archive ... streams.csv`) and neutralize them.
- Refresh the inventory numbers in the README.

**Эвиденс (в репозитории, по путям):**
- `scripts/streams/collect-stream-candidates.ps1` - функция `Invoke-PublishCatalog` (~1052-1115), безопасный путь публикации с guard S0925.
- `delivery/stream-catalog/README.md` - строка 33 (небезопасный сниппет), строки 177-189 (устаревший инвентарь).
- `temp/scratch/streams-src-doc/A_data_catalog_delivery.md` - дамп исследования, где находка зафиксирована с измерениями.
- `.claude/agent-memory/android-rd-specialist/project_stream_catalog_atlas_publish.md` - память о двух инцидентах.

**Дедуп:** открытых тикетов по симптому нет. S0925 (Archived) реализовал только guard в скрипте, но не чистку README-доки. S0785 (Archived) - первый инцидент. Не дубликат.

---

## 1. Проблема / симптом

`delivery/stream-catalog/README.md` документирует ручную упаковку банка стримов сниппетом, который архивирует только `streams.csv` и не кладёт `favicon-atlas.png`. Публикация таким путём отгружает CSV с колонкой `favicon_index`, указывающей в несуществующий атлас, - у всех каналов пропадают фавиконки в проде. Это уже случалось дважды (S0785 2026-07-03; рецидив 2026-07-12). Сниппет обходит guard S0925, живущий в `Invoke-PublishCatalog`. README - живой источник копипасты, поэтому опасный образец продолжает расходиться по другим местам публикации.

---

## 2. Корневая причина

Doc-drift: guard S0925 живёт только в `Invoke-PublishCatalog` (скрипт), но документация публикации осталась с сырым обходом. Два живых источника копипасты документируют CSV-only упаковку:

1. `delivery/stream-catalog/README.md:33` - сниппет `Compress-Archive -Path .. streams.csv` (+ строка 20 «zip contains: streams.csv» без атласа).
2. `.github/prompts/spec-prerelease.prompt.md:76-77` - тот же сырой `Compress-Archive` + `gh release upload delivery-so-v1`. Парный `.claude/commands/spec-prerelease.md` уже переведён на гарда-команду - зеркала разошлись (Copilot-зеркало отстало).

Плюс инвентарь README устарел радикально (426 против живых 2691) и вводит в заблуждение.

---

## 3. Исправление

1. `delivery/stream-catalog/README.md`: заменить сниппет упаковки (секция Hosting) на гарда-команду `collect-stream-candidates.ps1 -CatalogOnly -Publish -SkipLiveness` + жирное предупреждение против ручного `Compress-Archive`; поправить «zip contains» (streams.csv entry 0 + favicon-atlas.png) и убрать устаревшие размеры; обновить инвентарь на живой (snapshot 2026-07-19: 2691; VIDEO 2337 / AUDIO 348 / RTSP 6; Live TV 2299 / Radio 292 / SomaFM 56 / Test 30 / Open movies 14).
2. `.github/prompts/spec-prerelease.prompt.md`: заменить сырой `Compress-Archive` + `gh release upload` на ту же гарда-команду + предупреждение (зеркалит `.claude/commands/spec-prerelease.md`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0925 (guard в скрипте, Archived), S0785 (первый инцидент, Archived), S0668 (favicon-атлас), S0570 (каталог).

---

## 4. Проверка

- Grep: ни в `delivery/stream-catalog/README.md`, ни в `.github/prompts/spec-prerelease.prompt.md` нет `Compress-Archive` над `streams.csv` (сырой обход остаётся только внутри `Invoke-PublishCatalog`).
- Grep: оба файла ссылаются на `collect-stream-candidates.ps1 .. -Publish` как единственный путь публикации.
- Grep: README Inventory показывает 2691 (не 426).
- Doc-only изменение: сборка не требуется (Doc validation = Grep for content).

---

## Last Audit

**2026-07-19 (static, doc-only):** Verified.

- `delivery/stream-catalog/README.md`: unsafe `Compress-Archive` packaging snippet replaced with the guarded `collect-stream-candidates.ps1 -CatalogOnly -Publish -SkipLiveness` command + a favicon-wipe warning; "zip contains" now names the atlas; inventory refreshed to the live snapshot (2691 streams - VIDEO 2337 / AUDIO 348 / RTSP 6; Live TV 2299).
- `.github/prompts/spec-prerelease.prompt.md`: the drifted raw `Compress-Archive` + `gh release upload` block replaced with the same guarded command (now matches the already-safe `.claude/commands/spec-prerelease.md`).
- Verification greps: raw `Compress-Archive -Path` = 0 in both docs (present only inside `Invoke-PublishCatalog`, 2 sites); both docs point at the guarded packer; README shows 2691, not 426.
- No user-visible capability changed (internal publish-safety + maintainer docs) - no `ALL_FEATURES` record.
