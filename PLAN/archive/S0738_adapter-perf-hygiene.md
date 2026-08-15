# Спецификация (fix): S0738 - Перф-гигиена адаптеров: per-bind churn + selection-payload

**Ticket:** S0738
**Status:** Archived
**Priority:** 35
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0719 (Layer 6, P2/P3 perf)
**Umbrella:** S0714

> **Scope:** Микро-аллокации и полные ребайнды в RecyclerView-адаптерах. Найдено статически (S0719). Низкий приоритет (нет измеренного jank).

---

## 0. Источник

~13 perf-находок аудита S0719 (`PLAN/S0719_performance-r8-release-audit/AUDIT_FINDINGS.md`): 1×P2 + остальные P3. Все - viewport-bounded (не масштабируются с библиотекой), измеренного jank нет; сгруппированы в один cleanup-тикет.

## 1. Темы и правки

**A. Per-bind форматирование (churn) - общий хелпер файлового списка.**
- `ui/browse/AdapterFileInfoFormatter.kt` (`buildFileInfo`/`buildLegacyFileInfo`/`buildAudioDetailLine`), `ui/browse/PagingMediaFileAdapter.kt:422` - `android.text.format.DateFormat.format("yy-MM-dd HH:mm", Date(..))` на каждый bind (alloc `Date`+`Calendar.getInstance`+`SpannableStringBuilder`, re-parse паттерна), `formatFileSize`/`formatMediaDuration` (`String.format`/Formatter), `listOfNotNull(..).joinToString`. **Fix:** thread-confined кешированный `SimpleDateFormat` (или предпосчёт строки при маппинге MediaFile), один `StringBuilder`.
- `core/util/MediaFormatUtils.kt:21` `formatMediaDuration`, `core/util/FileSize.kt:16` `formatFileSize` - Formatter на каждый bind. **ОТЛОЖЕНО (locale-safety):** ручной `StringBuilder` сменил бы вывод на comma-decimal / не-ASCII-digit локалях (`%,d`/`%.2f`/`%d` через `Locale.getDefault()` локале-зависимы; `FileSize` зовётся из 10+ мест). Доминирующая per-bind аллокация - android `DateFormat` (Date+Calendar+SpannableStringBuilder), она и устранена в `AdapterFileInfoFormatter`; `String.format` для size/duration на порядки дешевле. Оставлено как есть ради точного вывода по локалям.

**B. Throwaway-аллокации в bind.**
- `ui/browse/MediaFileAdapter.kt:733` - `listOf(6)` (Object[]+wrapper+iterator) на bind для размеров кнопок → развернуть/хойстить.
- `ui/browse/MediaFileAdapter.kt:647` `applyInlineHighlight` - `getDrawable` инфлейтит GradientDrawable на каждый хайлайт → лениво кешировать.
- `ui/main/ResourceAdapter.kt:514` - `Color.parseColor("#1A1A1A")` на bind → константа; **+ удалить per-bind `Timber.d` (:588)** (стрейка debug-спама).
- `ui/main/ResourceAdapter.kt:74` `formatMediaTypes` - SpannableString+spans на bind → мемоизировать per resource.

**C. Selection без payload (полный ребайнд тогглнутых строк).**
- `ui/browse/MediaFileAdapter.kt:359` `setSelectedPaths` (drag-select tick), `ui/main/ResourceAdapter.kt:213` `setSelectedResource`, `ui/addresource/ResourceToAddAdapter.kt:29` `setSelectedPaths` - `notifyItemChanged(index)` без payload → полный `bind()`. **Fix:** `PAYLOAD_SELECTION` + `onBindViewHolder(payloads)`, обновлять только чекбокс/фон (паттерн уже есть в `DuplicateGroupAdapter` и для `FAVORITE_CHANGED`).

**D. Cold-path (низший приоритет).**
- `util/gif/AnimatedGifEncoder.kt:102` - per-pixel автобоксинг `mutableMapOf<Int,Int>` → `SparseIntArray`/`IntArray` (cold export).

## 2. Критерии приёмки

- [x] Per-bind форматирование даты кэшировано (thread-confined `SimpleDateFormat` в `AdapterFileInfoFormatter.formatTimestamp`, разделяется с `PagingMediaFileAdapter`); selection-тоггл - payload-частичный ребайнд (`PAYLOAD_SELECTION`) в `MediaFileAdapter`/`ResourceAdapter`/`ResourceToAddAdapter`. (`formatFileSize`/`formatMediaDuration` отложены ради locale-safety - см. §1.)
- [x] Per-bind `Timber.d` (ResourceAdapter) и `Color.parseColor`-литерал (-> `DARK_TEXT_COLOR`) убраны; `MediaFileAdapter` `listOf(6)` + GradientDrawable хойстнуты в lazy-поля; `formatMediaTypes` мемоизирован; cold-path GIF автобоксинг -> `SparseIntArray`.
- [x] Поведение и вид строк сохранены (адверсариальный review: 3 PASS; locale-замечание разобрано как false-positive - `Locale.US` совпадает с ASCII-выводом android `DateFormat`); `.\a.ps1 fc` зелёный; detekt-гейт зелёный.

## 3. Связанные тикеты

- S0719 (аудит-источник), S0714 (зонтик), S0722 (Macrobenchmark - измеримое подтверждение).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

Реализовано по 7 файлам (themes A-D), behavior-preserving:

- **A (per-bind дата):** `AdapterFileInfoFormatter` - thread-confined `ThreadLocal<SimpleDateFormat>` (override `initialValue`, API23-safe), публичный `formatTimestamp(millis)`; 3 android-`DateFormat.format` заменены. `PagingMediaFileAdapter.buildFileInfo` использует тот же `formatTimestamp` (вывод `"$size • $date"` сохранён). `formatFileSize`/`formatMediaDuration` отложены (locale-safety, §1).
- **B (throwaway-аллокации):** `MediaFileAdapter` - `listOf(6)` кнопок и GradientDrawable хойстнуты в lazy-поля холдера. `ResourceAdapter` - `Color.parseColor("#1A1A1A")` -> const `DARK_TEXT_COLOR`; 2 per-bind `Timber.d` удалены; `formatMediaTypes` мемоизирован (bounded LRU по `(types, allFiles)`).
- **C (selection payload):** `PAYLOAD_SELECTION` + `onBindViewHolder(payloads)` в `MediaFileAdapter` (общий `applySelectionVisual` для bind и payload - не расходятся), `ResourceAdapter` (root.isSelected + grid-фон), `ResourceToAddAdapter` (`bindSelection`, narrow до flipped-only). По эталону `DuplicateGroupAdapter`.
- **D (cold-path):** `AnimatedGifEncoder` per-pixel `mutableMapOf<Int,Int>` -> `SparseIntArray` (sentinel -1, GIF-байты идентичны).

**Verification:** `.\a.ps1 fc` BUILD SUCCESSFUL; detekt-гейт зелёный (0 findings в 7 затронутых файлах; новые findings от рефактора - LongMethod/Complexity/ReturnCount/ImportOrdering/wrapping - исправлены, не ре-фриз). Адверсариальный review (4 агента): 3 PASS; 1 locale-замечание по `Locale.US` разобрано как false-positive (паттерн чисто числовой; android `DateFormat` тоже эмитит ASCII-цифры), комментарий уточнён.

### Manual / on-device

- [ ] Опционально: визуальная проверка списков (browse list/grid, resource list, add-resource) на сохранность вида строк + поведения drag-select; измеримое подтверждение jank - S0722 (Macrobenchmark).
