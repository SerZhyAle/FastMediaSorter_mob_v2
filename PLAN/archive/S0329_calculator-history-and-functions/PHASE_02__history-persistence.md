# Phase 02 - History Persistence

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (independent of Phase 01; touches engine + manager)
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** All 3 steps PASS. `FileCalculatorHistoryStore` (filesDir/calculator_history.txt), engine `restoreHistory`, manager load-on-open / append-on-result / clear-on-clear all off the main thread. Debug build PASS.

---

## Objective

Persist the calculator's completed-history entries across sessions in an internal app file, load them on screen open, append each new completed entry, and wipe the file only on "Clear history" - all file IO off the main thread. No Room, no schema migration.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorHistoryStore.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt` | Modified | ≤ 580 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` | Modified | ≤ 320 |

---

## Steps

### Step 02.1 - Introduce the persistent history store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorHistoryStore.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Define a minimal interface `CalculatorHistoryStore` with `load(): List<String>`, `append(entry: String)`, and `clear()`, and a file-based implementation `FileCalculatorHistoryStore(file: File)` that stores one history entry per line (UTF-8). `load()` reads all non-blank lines (empty/missing file → empty list); `append()` adds a single line; `clear()` truncates/deletes the file. Take a `File` (not a `Context`) in the constructor so the impl is unit-testable with a temp file; the caller resolves `File(context.filesDir, "calculator_history.txt")`. No history-count cap - the store keeps every entry (strategic ADR-2). Timber only for any logging; no ticket id in permanent log text.

**Verification:**

- `Glob` - `CalculatorHistoryStore.kt` exists.
- `Grep` - `interface CalculatorHistoryStore` and `class FileCalculatorHistoryStore` both present.
- `Grep -n "Log\.d\("` - zero hits in the new file.

**Status:** `[x] done`

---

### Step 02.2 - Let the engine seed and report completed history

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a way to pre-seed the engine's `completedHistory` with previously persisted entries on construction/open (e.g. `restoreHistory(entries: List<String>)` that replaces the in-memory list without touching the current display/accumulator state). Ensure `appendCompletedHistoryEntry()` remains the single choke point where a new completed entry is produced, so the manager can mirror exactly that string to the store. Keep `clearHistory()` clearing only the in-memory list (file clearing happens in the manager, Step 02.3).

**Verification:**

- `Grep` - `fun restoreHistory` present in `CalculatorEngine.kt`.
- `Grep` - exactly one `appendCompletedHistoryEntry` definition remains the producer of completed entries.

**Status:** `[x] done`

---

### Step 02.3 - Wire load-on-open, append-on-result, clear-on-clear in the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Construct a `FileCalculatorHistoryStore` over `File(context.filesDir, "calculator_history.txt")`. On `bind()`: load persisted entries off the main thread and `restoreHistory(..)` into the engine, then `render()` on the main thread (history scroll already auto-scrolls to bottom). After every `update {}` that produces a NEW completed entry, append that exact entry string to the store off the main thread (diff the engine's `calculationHistory` size, or have the engine expose the last appended entry). In `clearHistory()`, additionally call `store.clear()` off the main thread. Do not block the UI thread for any file IO; reuse the existing background-thread + `mainHandler` pattern already in this manager.

**Verification:**

- `Grep` - `FileCalculatorHistoryStore` referenced in `CalculatorInputManager.kt`.
- `Grep` - `filesDir` referenced in `CalculatorInputManager.kt`.
- `Grep` - `restoreHistory` called in `CalculatorInputManager.kt`.
- `Grep -n "Log\.d\("` - zero hits in `CalculatorInputManager.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class).

---

## Handoff Notes to Next Phase

History now survives process death and screen re-open; "Clear history" wipes both memory and disk. The store reads/writes the same entry strings the engine produces, so any new history-entry format from Phase 01 persists unchanged.

---

## Rollback Plan

Revert phase commit(s). The only on-disk artifact is `filesDir/calculator_history.txt`; deleting it returns to the previous (empty-history) behavior. No schema migration involved.
