# Phase 01 - Translator foundations

**Strategic spec:** [`../S0508_gamepad-navigation-parity.md`](../S0508_gamepad-navigation-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-18
**Completed:** 2026-06-18

---

## Objective

Introduce a pure, unit-testable analog-stick translator that converts gamepad motion events into discrete navigation intents (focus move / scroll), with dead-zone, repeat-acceleration, and an injectable clock. No Activity wiring, no DI changes.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (all four are).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadNavIntent.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadNavigationTranslator.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/input/GamepadNavigationTranslatorTest.kt` | New | ≤ 220 |

---

## Steps

### Step 01.1 - Define navigation intent type

**Files:** `core/input/GamepadNavIntent.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a sealed interface `GamepadNavIntent` modelling what one analog-stick sample should do. Variants: `FocusMove(direction: Int)` where `direction` is a `View.FOCUS_*` constant (`FOCUS_UP/DOWN/LEFT/RIGHT`); `Scroll(dx: Int, dy: Int)` for continuous container scroll in pixels. No Android Activity or View dependency beyond the `FOCUS_*` int constants. Keep it a plain data model - no behaviour.

**Verification:**

- `Glob` - `core/input/GamepadNavIntent.kt` exists.
- `Grep` - `sealed interface GamepadNavIntent` matches once.
- `Grep` - `data class FocusMove` and `data class Scroll` both present.

**Status:** `[x]` done

---

### Step 01.2 - Implement the translator

**Files:** `core/input/GamepadNavigationTranslator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `class GamepadNavigationTranslator(private val now: () -> Long = { SystemClock.uptimeMillis() })`. Public `fun translate(event: MotionEvent): GamepadNavIntent?`:
> - Return null unless the event is from a gamepad/joystick source and action is `ACTION_MOVE` (mirror `GamepadInputManager.isFromGamepad` source checks).
> - Left stick `AXIS_X`/`AXIS_Y` (invert `AXIS_Y` so up = positive, as in `GamepadInputManager`): if the larger-magnitude axis exceeds `GamepadInputManager.DEADZONE`, produce a `FocusMove` with the matching `View.FOCUS_*` constant. Do NOT read `AXIS_HAT_X/Y` (hat is delivered as DPAD key events - see research 01).
> - Right stick `AXIS_Z`/`AXIS_RZ`: beyond the dead-zone, produce a `Scroll` whose dy/dx scale with deflection-past-dead-zone (reuse the `(abs - DEADZONE)/(1 - DEADZONE)` magnitude formula from `GamepadInputManager.rateLimitedAnalogSeek`).
> - Rate-limit emissions with `now()`: maintain a per-direction repeat counter; below the shared threshold use a base interval, at/above it shorten the interval (acceleration). Reuse the threshold value `FocusManager`'s `DPAD_ACCEL_REPEAT_THRESHOLD` (expose it or re-declare a single shared `const`). Reset the counter and last-direction when the stick returns inside the dead-zone or the direction reverses.
> - Keep all tunables (base interval, accelerated interval, scroll step) as named `companion object` constants with one-line comments.

**Verification:**

- `Glob` - `core/input/GamepadNavigationTranslator.kt` exists.
- `Grep` - `class GamepadNavigationTranslator` matches once.
- `Grep` - `fun translate(` present.
- `Grep -n "Log\.d\("` in the file returns zero hits.
- `.\a.ps1 fk` -> exit 0.

**Status:** `[x]` done

---

### Step 01.3 - Unit-test the translator

**Files:** `core/input/GamepadNavigationTranslatorTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create a Robolectric test class mirroring `GamepadInputManagerTest` (mockk motion events, `@Config(sdk = [34])`). Use an injected `now` lambda returning a controllable virtual time. Cover: below-dead-zone left stick → null; left stick `AXIS_X = +0.8` → `FocusMove(FOCUS_RIGHT)`; left stick `AXIS_Y = +0.8` (raw, before inversion) → the correct vertical `FocusMove`; right stick `AXIS_Z`/`AXIS_RZ` beyond dead-zone → `Scroll` with non-zero delta; held direction past the repeat threshold with advanced virtual time → emissions arrive at the shortened (accelerated) interval; returning inside the dead-zone resets the counter (next deflection starts un-accelerated). Hat axes are not exercised (out of scope).

**Verification:**

- `Glob` - `GamepadNavigationTranslatorTest.kt` exists.
- `Grep` - at least 5 `@Test` annotations.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*GamepadNavigationTranslatorTest*"` -> BUILD SUCCESSFUL, all green (read per-class XML report).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk` minimum; `.\a.ps1 d` not required this phase).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (two new public classes).

---

## Step Log

- 2026-06-18 - Step 01.1 Verification 3/3 PASS. `GamepadNavIntent.kt` (sealed interface + FocusMove/Scroll). Dev log recorded.
- 2026-06-18 - Step 01.2 Verification PASS (`.\a.ps1 fk` via test compile, zero `Log.d`). `GamepadNavigationTranslator.kt`. Fixed a `Long.MIN_VALUE` sentinel overflow surfaced by the scroll test. Dev log recorded.
- 2026-06-18 - Step 01.3 Verification PASS. `GamepadNavigationTranslatorTest.kt` 9 tests, 0 failures (XML report). Dev log recorded.

## Handoff Notes to Next Phase

`GamepadNavigationTranslator.translate(MotionEvent): GamepadNavIntent?` is the single entry point Phase 02 consumes from `BaseActivity`. The translator is stateful (repeat counter) - one instance per Activity, created lazily.

---

## Rollback Plan

Revert phase commit(s) - new files only, no existing code touched, no user-facing surface.
