# Phase 02 - Memory Persistence

**Strategic spec:** [`../S0331_calculator-memory-and-modulo.md`](../S0331_calculator-memory-and-modulo.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** `CalculatorMemoryStore` (SharedPreferences: memory value + row-expanded flag) + engine `restoreMemory`. Main build PASS.

---

## Objective

Persist the memory value and the memory-row collapsed/expanded flag across sessions in a small scalar store (SharedPreferences-backed), off the main thread. No Room.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorMemoryStore.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt` | Modified | ≤ 690 |

---

## Steps

### Step 02.1 - Introduce the scalar memory store

**Files:** `app_v2/.../helpers/CalculatorMemoryStore.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Define `CalculatorMemoryStore` with `loadMemory(): String?`, `saveMemory(value: String)`, `loadRowExpanded(): Boolean` (default `false` - collapsed), `saveRowExpanded(expanded: Boolean)`. Provide a SharedPreferences-backed implementation taking a `Context` (use a dedicated prefs file name). Store the memory value as its plain string form so it round-trips through `BigDecimal`. Keep it tiny - two scalar keys. No Timber ticket-id logging in permanent code.

**Verification:**

- `Glob` - `CalculatorMemoryStore.kt` exists.
- `Grep` - `class CalculatorMemoryStore` (or interface + impl) present.
- `Grep` - `loadRowExpanded` and `saveMemory` present.

**Status:** `[x] done`

---

### Step 02.2 - Let the engine restore a memory value

**Files:** `app_v2/.../helpers/CalculatorEngine.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `restoreMemory(value: BigDecimal)` to seed the memory register from a persisted value on open (no display/accumulator change). Ensure `memory` mutations (M+/M−/MC) go through a single choke point the manager can mirror to the store.

**Verification:**

- `Grep` - `fun restoreMemory` present in `CalculatorEngine.kt`.

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

Memory value and row-expanded flag have a persistent home. Phase 04 loads them on open and writes on change.

---

## Rollback Plan

Revert phase commit(s). Only on-disk artifact is a small SharedPreferences file; clearing it returns to empty memory + collapsed row. No schema migration.
