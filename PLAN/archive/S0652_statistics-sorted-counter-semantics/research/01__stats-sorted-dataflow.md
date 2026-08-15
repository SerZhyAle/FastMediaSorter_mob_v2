# Research 01: "Отсортировано" metric data flow

**Spec:** S0652
**Date:** 2026-06-23
**Mode:** read-only code investigation (android-solution-researcher)

## Question

Why is the headline statistics metric "Отсортировано" (Sorted) always 0 after copy/move operations, and what does it actually count?

## Key findings (evidence-backed)

### A. Headline metric source

- `StatsSnapshot.sortedFilesCount()` (`domain/stats/StatsModels.kt:99`):
  `count(FILES_COPIED) + count(FILES_MOVED)`.
- The card binds this value: `StatisticsViewModel.kt:133` -> `card_sorted`.
- **The metric is already Copied + Moved, NOT moved-only.** Spec §1 premise ("берёт значение только из счётчика перемещённых файлов") was factually wrong.

### B. Increment call sites - copy vs move

- Single recording path for both: `FileOperationUseCase.recordFileOpStats()` (`domain/usecase/FileOperationUseCase.kt:481-528`), called unconditionally at `:447` after a `Success`/`PartialSuccess`.
- `action` set at `:496-498`: `Copy -> FileOpAction.COPY`, `Move -> FileOpAction.MOVE`.
- `StatsSinkImpl.fileOpDelta()` (`data/stats/StatsSinkImpl.kt:126-136`) maps COPY -> `FILES_COPIED`, MOVE -> `FILES_MOVED`.
- All UI entry points (player sorter, copy/move dialog, browse, scheduled ops) converge on `executeInternal()`, so both copy and move are recorded.

### C. Verdict - no accounting bug

- The write pipeline (operation -> sink -> DataStore) is sound and complete.
- The "always 0" symptom has one primary cause: collection is opt-in and OFF by default (`AppSettings.enableStatistics = false`, `AppSettings.kt:298`). `StatsSinkImpl.record()` early-returns when `enabled == false` (`StatsSinkImpl.kt:57-58`).
- Secondary effects:
  - `SetStatisticsCollectionEnabledUseCase.kt:21-23` calls `wipeDetailed()` on toggle-OFF -> previously recorded data is erased if user toggled off then on.
  - Debounce flush `FLUSH_DEBOUNCE_MS = 2_500L` (`StatsSinkImpl.kt:153`); dashboard reads DataStore once at init (`StatisticsViewModel.kt:73-79`) -> ops completed within the window, then screen opened immediately, show 0.
  - No live refresh (ADR-7, all-time totals, single `load()`); ops completed while screen open are invisible until re-open.

### D. Report parity - card vs TXT

- Identical source. TXT report `BuildStatisticsReportUseCase.kt:54` calls `snapshot.sortedFilesCount()`. Same use case (`GetStatisticsUseCase`) as the card. Full parity.

### E. String keys

- `statistics_card_sorted` - EN "Sorted" / RU "Отсортировано" / UK at `values-uk/strings.xml:2542`.
- `statistics_metric_files_copied` / `_moved` / `_deleted` - operations section labels.

## Implication for the spec

- §1 premise corrected: metric is already copy+move; no counter bug.
- The user-facing complaint is a combination of: opt-in default off (by design), wipe-on-toggle, and a debounce/refresh display gap.
- Owner decisions (2026-06-23):
  - Keep formula = Copied + Moved; clarify the label so it honestly reflects copy AND move.
  - Add `flushNow()` on Statistics screen open to close the debounce gap.
  - Exclude: formula change, live-refresh Flow, opt-in discoverability empty-state.
