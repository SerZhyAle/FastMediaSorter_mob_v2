# S1241 - Live result preview while typing in the calculator

**Ticket:** S1241
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-28
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - request 2026-07-28

<!-- auto-approved by /spec-all - 2026-07-29 -->

---

## 0. Captured material (inbox)

**Captured:** 2026-07-28

> /spec-draft наш калькятор - во время набора ццр показыва предварительный результат, если он расчетный
> Оганиение - например когда пооззоватаель набирает
> "1000 / 0".. для полной фразы "1000 / 0.1" - мы  не вызываем нашу "закладку" - ждем действительного деления  на ноль поо кнопкее "="

---

## 1. Problem

The calculator shows only what has been typed. With `1000 ÷` entered and `5` being typed, nothing
says the answer is 200 until "=" is pressed. Every other calculator the owner uses shows it live, and
the absence reads as the app being a step behind.

The interesting half is the constraint the owner attached in the same breath: a preview must not
*pre-judge*. Typing `1000 ÷ 0` on the way to `1000 ÷ 0.1` must not flash a division-by-zero verdict -
that verdict belongs to "=", where the user actually asked for an answer.

---

## 2. Goals

1. While a right operand is being typed, the pending operation's result is visible without pressing "=".
2. The preview never appears when the operation cannot be computed - it stays silent instead of showing an error.
3. Pressing "=" behaves exactly as it does today, error paths included.

**Non-goals:**

- Previewing anything other than a pending binary operation. A bare number needs no preview, and the unary functions (sin, √, x², ..) already apply instantly.
- Previewing inside the expression evaluator used for pasted text - that path computes on paste, not on keystrokes.
- Any change to history, memory, or the widget.

---

## 3. Wishes and constraints

### 3.2 Hard constraints

- **Flavor:** all - the calculator is in `src/main` and has no flavor gate.
- **API level:** no API-specific work.
- **Wear OS:** not affected.
- **Performance:** the preview is recomputed per keystroke on `BigDecimal`, which is the same arithmetic "=" already runs once. No new allocation path worth budgeting.
- **Data compatibility:** none - the preview is derived, never stored.
- **Localization:** the preview shows a number, so no new user-visible string is needed unless a prefix is added; if one is, EN/RU/UK together.
- **Accessibility:** the preview is decorative and duplicated by the result on "=" - it must not steal focus or announce on every keystroke.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI placement:** on the calculator's existing secondary line, beside the operation history, rather than as a new row - the keypad's vertical budget is what makes this calculator usable one-handed.
- **Silence rule:** confirmed by the owner in the captured text - an uncomputable in-progress expression shows nothing at all, not a placeholder and not an error.

---

## 4. Current architecture

`CalculatorEngine` is a classic state machine, not an expression editor: `accumulator`,
`pendingOperator`, `display` and `startNewInput`. Typing a digit mutates `display`; pressing an
operator folds `display` into `accumulator`; "=" applies the pending operation.

The reason a preview cannot simply be read today is that the only code that computes an operation -
`applyOperation` - is also the code that *commits* it: it writes `display`, `accumulator`,
`startNewInput`, and on failure sets `error` and resets the machine. Calling it to look would change
what the user sees.

The pasted-text path (`CalculatorExpressionEvaluator`) is a separate, already-pure evaluator and is
not involved.

---

## 5. Proposed approach

Split the arithmetic away from the commit, then read it twice.

### 5.1 Pillars

- **A pure operation computation.** One private function that maps (left, right, operator) to a value or nothing. Nothing else - no state, no error assignment.
- **A preview reader.** A public, side-effect-free query that answers "what would = show right now", or nothing.
- **The existing commit path, rewritten on top of the pure computation.** `applyOperation` keeps owning the error state and the machine reset; it stops owning the maths.

### 5.2 Data and event flow

Keystroke -> engine mutates `display` -> host asks the engine for a preview -> host renders it or
clears it. The engine never pushes; the host pulls after each input, exactly as it already re-reads
`display` and `operationHistory`.

### 5.3 The silence rule, precisely

The preview returns nothing when any of these hold, and each has a reason:

- There is no pending operator, or no accumulator - there is no operation to preview.
- `startNewInput` is set - the right operand has not been typed yet, so a preview would be of the left operand alone.
- An error is already showing - the machine is waiting to be cleared.
- The computation fails: a zero right operand under `÷`, `DIV` or `mod`, or a power that leaves the real domain.

The fourth is the owner's constraint. It falls out of the design rather than needing a special case:
the pure computation reports failure by returning nothing, and the preview renders nothing, while
"=" turns that same nothing into the error it always did.

---

## 6. Open questions

None. The one product decision - what to do about an uncomputable in-progress expression - is
answered in the captured text.

---

## 7. Risks

| Risk | Likelihood | Consequence | Mitigation |
|------|:----------:|-------------|------------|
| The commit path changes behaviour while being rewritten onto the shared computation | Medium | Wrong results or a lost error state in the shipped calculator | `CalculatorEngineTest` already carries 47 tests, 6 of them on the error paths; a behaviour change turns them red before anything ships |
| Preview and "=" disagree because they round differently | Low | The number visibly changes when "=" is pressed | Both format through the engine's single `format`, and both compute through the one shared function |
| The preview announces on every keystroke under TalkBack | Low | Unusable with a screen reader | Constraint recorded in 3.2; the preview view must not be an announcement target |

---

## 8. User impact (docs/FEATURES)

New capability: the calculator shows the running result of a pending operation while the second
number is still being typed.

---

## 9. ADR

**ADR-1: the preview is a pull, not a push.** The engine exposes a query and the host reads it after
each input, rather than the engine emitting a preview event. The host already re-reads `display` and
`operationHistory` on every keystroke, so a third read costs nothing and keeps the engine free of
observers - it has none today and this feature does not justify the first.

**ADR-2: silence is modelled as absence, not as a state.** An uncomputable preview returns nothing
rather than a "cannot compute" value. Anything else invites a caller to render the failure, which is
exactly what the owner asked not to happen.

---

## 10. Related specs

None.

---

## 11. Completion criteria

1. With `1000 ÷` entered, typing `5` shows 200 before "=" is pressed.
2. Typing `1000 ÷ 0` shows no preview and no error; continuing to `0.1` shows 10000.
3. Pressing "=" on `1000 ÷ 0` still produces the division-by-zero error exactly as before.
4. The preview disappears when the operation is committed, cleared, or replaced.
5. Every existing calculator test still passes.
