# Спецификация (compact bugfix): S1110 - Каталог стримов показывает «0 MB» в экране Extensions

**Ticket:** S1110
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-19
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-19

**Захвачено во время:** ad-hoc задача документирования подсистемы Streams (source-spec для FastMediaSorter for Windows). Находка §3.1, запаркована без переключения активной задачи.

**Текст:**

UI defect surfaced by the entry-points research subagent (E).

The Extensions screen row for the downloadable stream catalog (`ExtensionItem.Catalog`) reports its size from a hardcoded ~200,000-byte constant in `DeliverableInventoryImpl.kt` (~lines 143-154 and 274-285). The size label is rendered with a `%.0f` MB formatter, so 200,000 bytes rounds to and displays as literal "0 MB". The real `stream-catalog.zip` asset is ~1-2.5 MB and is not fixed size (not SHA-pinned; grows as the bank grows - currently ~2.31 MiB atlas + ~0.95 MB CSV). The user sees a misleading "0 MB" for a multi-megabyte download.

Proposed scope (for later, not now): show a truthful size for the catalog row - either read the actual `Content-Length` of the GitHub release asset, or render sub-MB sizes with more precision (KB / one decimal MB), or omit the size for this dynamically-sized remote deliverable. Decide which and apply.

**Эвиденс (в репозитории, по путям):**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt:143-154,274-285` - hardcoded 200000-byte size + `%.0f` MB render.
- `temp/scratch/streams-src-doc/E_entrypoints_gating.md` - headline fact 8.

**Дедуп:** открытых тикетов по симптому нет.

---

## 1. Проблема / симптом

В экране Extensions строка загрузки каталога стримов показывает размер «0 MB», потому что размер зашит константой ~200000 байт и форматируется как `%.0f MB` (0.2 -> 0). Фактический `stream-catalog.zip` - несколько мегабайт и переменного размера. Пользователю показывается заведомо неверная величина.

---

## 2. Корневая причина

Две связанные ошибки в `DeliverableInventoryImpl.kt`:

1. Константа `STREAM_CATALOG_SIZE = 200_000L` - устаревшая оценка эпохи S0386 (staging `temp/S0386_B3_so_staging.md`), когда банк стримов был крошечным. Живой опубликованный `stream-catalog.zip` = 2 561 714 байт (2.44 MB) - атлас фавиконок 2.31 MiB + `streams.csv` 0.92 MB - и растёт вместе с банком (не SHA-pinned).
2. Общий `formatBytes` рендерит `%.0f MB`: 200000 / 1024 / 1024 = 0.19 -> округляется в «0 MB». `%.0f MB` схлопывает любой sub-MB размер в «0», так что баг латентен и для будущих мелких deliverable.

Итог: строка каталога показывает «0 MB» вместо реальных ~2.5 MB. Остальные строки (модули 6-17 MB) форматируются верно - дефект бьёт только по единственному sub-MB значению.

---

## 3. Исправление

Правка в `DeliverableInventoryImpl.kt` (display-only, `STREAM_CATALOG_SIZE` используется лишь для `sizeLabel`):

1. `STREAM_CATALOG_SIZE`: `200_000L` -> `2_500_000L` - реалистичная оценка растущего, не-pinned каталога (замер 2.44 MB на 2026-07-19; округлено, чтобы не создавать ложную точность на движущейся цели). Обновить комментарий: значение не из S0386-staging, а приблизительный размер каталога.
2. `formatBytes`: добавить ветку «< 1 MB -> KB», чтобы ни один sub-MB deliverable не схлопывался в «0 MB»; для >= 1 MB оставить целые MB (строки закреплённых дескрипторов модулей не меняются).

Обёртка `R.string.ext_estimated_size` («Estimated size: %s» / «Оценочный размер: %s» / «Оціночний розмір: %s») уже подаёт метку как оценочную во всех трёх локалях - отдельный «~» или правка строк не нужны.

Отклонённые варианты (owner оставил решение на исполнителя):
- Live `Content-Length` GitHub-ассета - `getExtensions()` синхронно строит `List<ExtensionItem>`; сетевой HEAD в этом пути архитектурно неуместен (блокировка, режимы отказа) для рендера списка.
- Убрать размер у строки каталога - каждая соседняя строка показывает размер; честная оценка полезнее пустого поля.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565 (Streams entry-point), S0575 (catalog download в Extensions).

---

## 4. Проверка

- Сборка standard debug (`a.ps1 dq`) PASS.
- Grep: `STREAM_CATALOG_SIZE = 2_500_000L` в `DeliverableInventoryImpl.kt`; `formatBytes` содержит ветку `KB`.
- Статически: `2_500_000` -> ветка `>= 1 MB` -> «2 MB»; любое sub-MB значение -> «NNN KB», никогда «0 MB».
- Строки модулей не изменились (все >= 1 MB -> прежние целые MB: 7/14/17 MB).
- Устройство (отложено, device offline): Settings -> Extensions, строка каталога стримов показывает «Estimated size: 2 MB» (не «0 MB»).

---

## Last Audit

### Manual (device)

**Date:** 2026-07-19. **Device:** emulator-5554 (Android 15, SDK 35). **Build:** standard debug `com.sza.fastmediasorter.debug` v2.60.7182.317-DEBUG (installed, not rebuilt).

**Verdict:** PASS.

- Path: onboarding -> "All downloadable (optional) elements" -> Downloadable Extensions -> STREAMS -> Stream sources catalog (same screen as Settings -> Extensions; Streams capability enabled so the catalog row is present).
- Stream catalog row - expected: "Estimated size: 2 MB" (not "0 MB") | actual: "Estimated size: 2 MB". PASS.
- Other extension rows keep non-zero, plausible whole-MB sizes (no regression):
  - Translation Module - "Estimated size: 17 MB".
  - Audio Visualizations - "Estimated size: 20 MB".
  - FFmpeg DTS Decoder - "Estimated size: 7 MB".
- No separate OCR engine/language rows in the standard flavor (bundled ML Kit); nothing to regress there.
- Logcat marker: `D DeliverableInventoryImpl: S1110: stream catalog size label=2 MB` - the computed label matches the rendered row.
- Evidence: `temp/S1110/06_extensions.png`, `temp/S1110/07_ext_top.png`, `temp/S1110/08_relaunch.png`, `temp/S1110/logcat_S1110.txt`.
