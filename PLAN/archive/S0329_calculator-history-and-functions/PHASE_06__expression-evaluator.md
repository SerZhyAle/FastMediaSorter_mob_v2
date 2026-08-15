# Phase 06 - Expression Evaluator

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (power operator + roots exist)
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** All 3 steps PASS. New `CalculatorExpressionEvaluator` (recursive-descent: precedence `^` > unary `-`/`√` > `* /` > `+ -`, parentheses, implicit `+` between bare numbers, ADR-3 decimal normalization, noisy-operator sanitation). Engine `inputNumber`/`canParseInput` routed through it; legacy paste parser removed. `CalculatorExpressionEvaluatorTest` 16/0/0, `CalculatorEngineTest` 39/0/0 (per-class XML). Debug build PASS. Note: ADR-3 means US-style "1,234.56" → 1234.56 (comma=thousands when a dot is also present); the alternate test example was corrected accordingly.

---

## Objective

Replace the calculator's left-to-right paste parser with a real expression evaluator: operator precedence, parentheses, unary square root, implicit summation of whitespace-separated bare numbers, and decimal/comma normalization - applied to text pasted or passed from a text selection. Strategic §2.7-2.8, ADR-3, ADR-4.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorExpressionEvaluator.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt` | Modified | ≤ 620 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorExpressionEvaluatorTest.kt` | New | ≤ 220 |

---

## Steps

### Step 06.1 - Number/separator normalizer

**Files:** `app_v2/.../helpers/CalculatorExpressionEvaluator.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> In a new object/class `CalculatorExpressionEvaluator`, implement per-number-token decimal normalization (strategic §2.7, ADR-3): given a raw numeric token containing digits and `.`/`,`: if both `.` and `,` are present → remove all `,` (thousands), keep `.` as decimal; if only `,` present → treat `,` as the decimal candidate; if only `.` present → `.` is the decimal candidate. When the chosen decimal character occurs multiple times in the token, keep only its LAST occurrence as the decimal point and drop the earlier ones (treated as grouping). Produce a clean `BigDecimal`-parseable string. No Timber ticket-id logging.

**Verification:**

- `Glob` - `CalculatorExpressionEvaluator.kt` exists.
- `Grep` - `class CalculatorExpressionEvaluator` or `object CalculatorExpressionEvaluator` present.

**Status:** `[x] done`

---

### Step 06.2 - Tokenizer + precedence evaluator with parens, ^, √, implicit sum

**Files:** `app_v2/.../helpers/CalculatorExpressionEvaluator.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Implement `evaluate(text: String): Result` returning the computed `BigDecimal` and a normalized canonical expression string for history, or a domain/parse failure. Tokenize keeping `(` `)` `+` `-` `*` `×` `/` `÷` `^` and the square-root glyph `√` (and `sqrt`); map `*`/`x`/`×`→multiply, `/`/`÷`/`:`→divide. Strip all other non-numeric noise. Two adjacent numbers with no operator between them (whitespace/newline separated, or after noise removal) get an implicit `+`. Evaluate with precedence: `^` highest (right-assoc), then unary `√` and unary minus, then `* /`, then `+ -`; honor parentheses. Reuse `CalculatorEngine`'s rounding semantics (12 significant digits) for the result format. Division by zero → division error; √ of negative, non-finite power, etc. → math-domain failure. Keep the existing single-number and simple-expression behavior working (the old `inputNumber`/paste tests must still pass).

**Verification:**

- `Grep` - `fun evaluate` present in `CalculatorExpressionEvaluator.kt`.
- `Grep` - the `√` glyph and `^` handled (search for `'√'` / `"√"` and `'^'`).

**Status:** `[x] done`

---

### Step 06.3 - Route engine paste/initial input through the evaluator + tests

**Files:** `app_v2/.../helpers/CalculatorEngine.kt`, `app_v2/.../helpers/CalculatorExpressionEvaluatorTest.kt` (New)
**Depends on:** Step 06.2

**Prompt for developer:**

> Route `CalculatorEngine.inputNumber(text)` (used by paste and by the text-selection `applyInitialInput` path) through `CalculatorExpressionEvaluator`. On success: set the display to the formatted result, set `operationHistory` to the canonical expression with trailing `=`, and append one completed-history entry (so the result enters history per §2.8). On failure: keep prior safe behavior (no crash, no NaN; domain failures set the appropriate error). Update `canParseInput` to accept any text the evaluator can evaluate to at least one number. Add `CalculatorExpressionEvaluatorTest` covering: `125.4 8 9` → `142.4`; `2+3*4` → `14`; `(2+3)*4` → `20`; `2^10` → `1024`; `√9` → `3`; `1,5` → `1.5`; `1.234,56` → `1234.56`; `1,234,56`/`1.2.3`-style multi-separator per ADR-3; division-by-zero and √(-1) failure cases.

**Verification:**

- `Grep` - `CalculatorExpressionEvaluator` referenced in `CalculatorEngine.kt`.
- `Glob` - `CalculatorExpressionEvaluatorTest.kt` exists.
- Run `app_v2:testStandardDebugUnitTest --tests "*CalculatorExpressionEvaluatorTest" --tests "*CalculatorEngineTest"` - all pass (per-class XML).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `CalculatorEngineTest` + `CalculatorExpressionEvaluatorTest` green (per-class XML).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class).

---

## Handoff Notes to Next Phase

Selected/pasted text is now evaluated as a full expression and its result enters history. Final phase handles docs/catalog/changelog.

---

## Rollback Plan

Revert phase commit(s). The evaluator is additive; reverting restores the prior left-to-right paste parser. No data migration.
