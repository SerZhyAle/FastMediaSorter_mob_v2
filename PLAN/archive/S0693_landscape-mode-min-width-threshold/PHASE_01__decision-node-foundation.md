# Phase 01 - Decision Node Foundation

**Strategic spec:** [`../S0693_landscape-mode-min-width-threshold.md`](../S0693_landscape-mode-min-width-threshold.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce the single landscape-style decision node: a pure primitive rule plus thin `Configuration`/`Context` extensions, with the threshold as one named constant. No call site is migrated yet.

---

## Prerequisites

- [ ] Strategic spec is `Status: Tactical` or later.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/orientation/WideLayout.kt` | New | ≤ 40 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/orientation/WideLayoutTest.kt` | New | ≤ 60 |

> No flavor-specific placement: this is a shared, flavor-agnostic rule under `src/main/`.

---

## Steps

### Step 01.1 - Create the WideLayout decision node

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/orientation/WideLayout.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `WideLayout.kt` in package `com.sza.fastmediasorter.core.orientation`. Declare `const val WIDE_LAYOUT_MIN_WIDTH_DP = 600`. Declare the pure rule `fun isWideLayout(orientation: Int, screenWidthDp: Int): Boolean = orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP`. Add two thin delegating extensions: `fun Configuration.isWideLayout(): Boolean = isWideLayout(orientation, screenWidthDp)` and `fun Context.isWideLayout(): Boolean = resources.configuration.isWideLayout()`. This is the union rule (landscape orientation OR available width >= threshold) and the only place the threshold is defined - the single rollback point. Keep the primitive overload public so it is unit-testable without Android. One KDoc line stating it is the single source of truth for landscape-style layout; no per-function trivial comments.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/orientation/WideLayout.kt` exists.
- `Grep` - `const val WIDE_LAYOUT_MIN_WIDTH_DP = 600` matches exactly once.
- `Grep` - `fun isWideLayout(orientation: Int, screenWidthDp: Int): Boolean` present.
- `Grep` - `fun Configuration.isWideLayout(): Boolean` present.
- `Grep` - `fun Context.isWideLayout(): Boolean` present.

**Status:** `[x]` done

---

### Step 01.2 - Unit-test the union rule on primitives

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/orientation/WideLayoutTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `WideLayoutTest.kt` (JUnit, no Robolectric) targeting the primitive `isWideLayout(orientation, screenWidthDp)`. Cover the four union cases: landscape below threshold (`ORIENTATION_LANDSCAPE`, 400) -> true; portrait at threshold (`ORIENTATION_PORTRAIT`, 600) -> true; portrait above threshold (`ORIENTATION_PORTRAIT`, 800) -> true; portrait below threshold (`ORIENTATION_PORTRAIT`, 599) -> false. Use `Configuration.ORIENTATION_*` int constants directly (compile-time constants, no Android runtime needed).

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/core/orientation/WideLayoutTest.kt` exists.
- `Grep` - `isWideLayout(` matches at least 4 times in the test file.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*WideLayoutTest"` exits 0 (run via `/build` flow, not raw gradle if a wrapper is preferred).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `WideLayoutTest` passes.
- [ ] Dev log entry added for both new files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public file) - or deferred to Phase 05 cleanup.

---

## Handoff Notes to Next Phase

- The node API is `Configuration.isWideLayout()` / `Context.isWideLayout()`; the primitive `isWideLayout(orientation, screenWidthDp)` exists for tests. All migration phases call the extensions, never re-derive the rule.
- Rollback seam: setting the rule body to `orientation == Configuration.ORIENTATION_LANDSCAPE` (drop the width branch) reverts every consumer at once.

---

## Rollback Plan

Revert the phase commit. No data migration, no user-facing surface, no consumer yet.
