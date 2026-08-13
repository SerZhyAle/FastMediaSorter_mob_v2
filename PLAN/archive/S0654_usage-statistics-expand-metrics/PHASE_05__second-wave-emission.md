# Phase 05 - Second-Wave Emission (optional)

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (undo + OCR shipped; `TEXT_TRANSLATIONS` deferred to a 2nd wave)
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

> **Conditional phase.** Ships only if strategic §6.1 keeps undo / OCR / translation in scope (strategic ADR-2 allows deferral). If owner defers, mark this phase ⏭️ Skipped in INDEX and omit the corresponding rows in Phase 06.

---

## Objective

Wire emission for the higher-cost behaviors: undo of file operations, OCR scans, and text translations.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] §6.1 owner decision keeps these behaviors in scope.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt` | Modified | ≤ 200 |

> Translation has three separate entry points (EPUB / PDF / image helpers). Step 05.3 picks the unification approach; if no single seam is feasible within budget, defer `TEXT_TRANSLATIONS` and note it in the step.

---

## Steps

### Step 05.1 - Record undo operations

**Files:** `ui/browse/undo/BrowseUndoManager.kt`, `domain/usecase/FileOperationUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink` into `BrowseUndoManager`; emit `StatsEvent.UndoPerformed` when `undoLastOperation()` returns success. Mirror in the player-side undo path (`FileOperationUseCase.undo()`) so both undo surfaces count. Emit once per successful undo, not per restored file.

**Verification:**

- `Grep` - `StatsEvent.UndoPerformed` referenced in `BrowseUndoManager.kt`.
- `Grep` - `StatsEvent.UndoPerformed` referenced in `FileOperationUseCase.kt`.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 05.2 - Record OCR scans

**Files:** `ui/player/helpers/RecognitionBackend.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink` into `RecognitionBackend`. Emit `StatsEvent.OcrScan` once per successful recognition in `recognizeText()` and `recognizeAndTranslateBlocks()` (one scan per call that returns a non-null result). Do not emit the recognized text anywhere in the event.

**Verification:**

- `Grep` - `StatsEvent.OcrScan` referenced in `RecognitionBackend.kt`.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 05.3 - Record text translations

**Files:** translation helper(s) - confirm exact seam during implementation
**Depends on:** - start of phase

**Prompt for developer:**

> Emit `StatsEvent.TextTranslated` once per completed translation across the EPUB chapter, PDF page, and image translation flows. Prefer a single shared seam (e.g. the common translation coordinator/use-case those helpers call) over three duplicated emissions; if no shared seam exists, emit at each of the three completion points. Never include translated text in the event. If unification exceeds this phase's budget, defer `TEXT_TRANSLATIONS` and record the deferral in INDEX Blockers Log.

**Verification:**

- `Grep` - `StatsEvent.TextTranslated` referenced at least once in the translation flow.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done` (or explicitly deferred with a Blockers Log entry).
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`UNDO_OPERATIONS`, `OCR_SCANS`, `TEXT_TRANSLATIONS` (whichever shipped) now accrue. Phase 06 renders only the keys that ship.

---

## Rollback Plan

Revert phase commit(s) - emission-only, no data migration or user-facing surface changed.
