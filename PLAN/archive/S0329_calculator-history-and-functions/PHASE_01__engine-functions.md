# Phase 01 - Engine Functions

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** All 4 steps PASS. `CalculatorEngineTest` 39 tests / 0 failures (per-class XML). Note: adding `MATH_DOMAIN` broke the exhaustive `when` in `CalculatorInputManager.render()`, so the render branch + trilingual `calculator_error_math_domain` were pulled forward here (Phase 03 Step 03.1 string / 03.3 render now PRE-RESOLVED). Pre-existing untracked broken tests (`RealDeviceProfileDetectorTest`, stereo tests using absent mockito-kotlin) were quarantined to `temp/` during the run and restored afterward.

---

## Objective

Extend `CalculatorEngine` with scientific unary operations, a binary power operator, the π constant, and a generic math-domain error state - pure computation, fully unit-tested, no UI changes.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt` | Modified | ≤ 560 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngineTest.kt` | Modified | ≤ 420 |

---

## Steps

### Step 01.1 - Add a generic math-domain error state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a second value to the `CalculatorError` enum (e.g. `MATH_DOMAIN`) for operations whose argument is outside the domain (√ of a negative number, `ln`/`log₁₀` of ≤ 0, factorial of a negative or non-integer value). Reuse the existing division-by-zero handling style: on a domain violation set `error`, reset `display` to `ZERO`, clear `accumulator`/repeat state, and set `startNewInput = true`. Do not throw.

**Verification:**

- `Grep` - `MATH_DOMAIN` matches in `CalculatorEngine.kt`.
- `Grep` - `enum class CalculatorError` present with two entries.

**Status:** `[x] done`

---

### Step 01.2 - Add unary scientific functions (degrees) and π

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add public unary functions applied to the current display value, each returning the new `display`: `sine()`, `cosine()`, `tangent()`, `cotangent()` (trigonometry in DEGREES - convert with `Math.toRadians`), `squareRoot()` (√), `cubeRoot()` (∛), `square()` (x²), `reciprocal()` (1/x), `log10()`, `naturalLog()` (ln), `factorial()` (n!). Add `inputPi()` that loads the π constant into the display as a fresh value. Domain rules: √ and `ln`/`log₁₀` of out-of-domain arguments and factorial of negative/non-integer → set `MATH_DOMAIN` (Step 01.1). `tangent`/`cotangent` near their poles (cos/sin ≈ 0 within an epsilon) → `MATH_DOMAIN`. Reuse the existing `format()` (12 significant digits, trailing-zero strip) for every result so irrational outputs are normalized exactly like arithmetic results. Each completed unary op appends one history entry: function form `label(arg)=result` (e.g. `sin(30)=0.5`, `√(9)=3`); postfix form for `square` (`3²=9`) and `factorial` (`5!=120`). `inputPi()` does NOT append a history entry. Reuse the existing `appendCompletedHistoryEntry()`/`operationHistory` plumbing.

**Verification:**

- `Grep` - each of `fun sine`, `fun cosine`, `fun tangent`, `fun cotangent`, `fun squareRoot`, `fun cubeRoot`, `fun square`, `fun reciprocal`, `fun log10`, `fun naturalLog`, `fun factorial`, `fun inputPi` present in `CalculatorEngine.kt`.
- `Grep` - `Math.toRadians` present (degree mode).
- `Grep -n "Log\.d\("` - zero hits in `CalculatorEngine.kt`.

**Status:** `[x] done`

---

### Step 01.3 - Add the binary power operator (xʸ)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Extend the `Operator` enum with a power entry using symbol `^` and integrate it into the existing `inputOperator` / `applyOperation` / repeat-equals pipeline so `x ^ y =` works like the other binary operators (history `x^y=result`). Use `BigDecimal`-friendly exponentiation: integer exponents via `BigDecimal.pow`; non-integer exponents via `Math.pow` then re-`format()`. A result that is non-finite (NaN/Infinity, e.g. negative base with fractional exponent) → `MATH_DOMAIN`. Do NOT add `^` to the pasted-expression parser (`operatorForPastedChar`) in this phase unless trivial - keep paste behavior unchanged.

**Verification:**

- `Grep` - `"^"` power symbol registered in the `Operator` enum.
- `Grep` - `pow` present in `CalculatorEngine.kt`.

**Status:** `[x] done`

---

### Step 01.4 - Unit tests for new operations

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngineTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add JUnit tests covering: `sin(30)=0.5`, `cos(60)=0.5`, `tan(45)=1`, `√(9)=3`, `∛(27)=3`, `square(4)=16`, `reciprocal(4)=0.25`, `log10(1000)=3`, `ln(1)=0`, `factorial(5)=120`, power `2^10=1024`, and at least three domain-error cases (`√(-1)`, `ln(0)`, `factorial(-3)` or a non-integer factorial) asserting `error == CalculatorError.MATH_DOMAIN`. Assert the history entry text for one trig and one postfix case (e.g. `sin(30)=0.5`, `5!=120`).

**Verification:**

- `Grep` - `MATH_DOMAIN` referenced in `CalculatorEngineTest.kt`.
- `Grep` - `factorial` and `sine` (or `sin`) referenced in the test file.
- Run the test class: `app_v2:testStandardDebugUnitTest --tests "*CalculatorEngineTest"` - all assertions pass (read the per-class XML report; do not rely on the aggregate task exit code).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `CalculatorEngineTest` passes (per-class XML report green).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (engine signature changed).

---

## Handoff Notes to Next Phase

Engine now exposes unary functions, `inputPi`, and the `^` operator, plus `CalculatorError.MATH_DOMAIN`. Phase 03 wires these to the "Функция" submenu. The history-entry text format established here is what the persistence store in Phase 02 will read back verbatim.

---

## Rollback Plan

Revert phase commit(s) - pure logic addition, no data migration or user-facing surface changed.
