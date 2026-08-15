# S0504 - App-wide D-pad focus-on-container guard

**Status:** Archived
**Priority:** 55
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - owner request 2026-06-18 (generalise the S0289 WelcomeActivity fix app-wide)

## Goal (RU)

Убрать весь класс бага «D-pad/клавиатурный фокус паркуется на скролл/пейдж-контейнере (ViewPager2, RecyclerView, ScrollView/NestedScrollView, или фокусируемый root) вместо реального контрола, из-за чего элементы недостижимы или первая клавиша теряется» — системно, одним общим механизмом, а не точечно. S0289 закрыл это в `WelcomeActivity`; здесь то же самое выносится в общий слой `BaseActivity`, чтобы покрыть все in-house Activity сразу. По решению владельца проверка - без теста на устройстве: сборка + Robolectric-юнит-тесты резолвера + статический разбор отсутствия регрессий.

## Context

- S0289 (Verified) fixed the bug in `WelcomeActivity` with a per-screen `dispatchKeyEvent` override (`isOnPagerContainer`/`enterPageFromContainer`).
- Research (`android-solution-researcher`, 2026-06-18) mapped the surface: `BaseActivity.getInitialFocusView()?.requestFocus()` (`BaseActivity.kt:149`) is the single shared hook. Screens that return a raw container from `getInitialFocusView()` and exhibit the trap: `SettingsActivity` (viewPager), `KeybindingRemapActivity` (recyclerView), `ResourceEditorActivity` (fragment host FrameLayout), `DuplicatesActivity` / `AuthSessionsActivity` (RV last-fallback).
- Already-safe screens (MainActivity, BrowseActivity, PlayerActivity, WelcomeActivity) return a leaf or a `RecyclerView` with `descendantFocusability=afterDescendants` that legitimately delegates focus to bound items - the guard must NOT change them.

## Design

A pure, unit-testable resolver, applied once at the initial-focus call site.

- New `ui/common/input/FocusTargetResolver.kt` with `fun resolveToLeafFocusable(view: View?): View?`:
  - `null` or non-`ViewGroup` (a leaf) -> return as-is.
  - `RecyclerView` with `descendantFocusability == FOCUS_AFTER_DESCENDANTS` and `childCount > 0` -> return as-is (it legitimately delegates to its items; preserves list scrolling - Browse/cloud-picker safety).
  - Otherwise (ViewPager2 / ScrollView / NestedScrollView / fragment-host ViewGroup / focusable layout) -> `addFocusables(FOCUS_FORWARD)` and pick the first candidate that is shown + focusable and is **neither a scrollable container** (RecyclerView/ViewPager2/ScrollView/NestedScrollView) **nor an `EditText`** (EditText is skipped first so the soft keyboard does not pop on form screens); fall back to first non-container, then first focusable, then the original view.
- `BaseActivity.onCreate` initial-focus block becomes:
  `getInitialFocusView()?.let { FocusTargetResolver.resolveToLeafFocusable(it) ?: it }?.requestFocus()`.
- No per-screen edits required - the shared guard wraps whatever each screen returns. `WelcomeActivity` keeps its own ongoing `dispatchKeyEvent` guard (returns a leaf from `getInitialFocusView`, so the resolver is a no-op there).

## Out of scope

- `ReceiveShareActivity` extends `AppCompatActivity` directly (not `BaseActivity`) - the shared hook does not reach it. Parked separately.
- On-the-fly redirect for lists that load asynchronously AFTER `setupViews()` (the resolver runs once at startup). Existing per-screen handling stands; revisit only if a screen reports the trap post-load.
- Touch-mode "first key exits touch mode" is framework behaviour, not addressed here (documented in S0289).

## Phases

### Phase 01 - Resolver + wiring

- [ ] Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/FocusTargetResolver.kt` per Design. KDoc explains the bug class + the afterDescendants exception. EN-only.
  - Verification: file exists; `Grep` finds `fun resolveToLeafFocusable`.
- [ ] Wire it into `BaseActivity.kt:149` initial-focus call site; add imports.
  - Verification: `Grep` finds `FocusTargetResolver.resolveToLeafFocusable` in `BaseActivity.kt`; `compileStandardDebugKotlin` GREEN.

### Phase 02 - Unit tests (device-waived verification)

- [ ] Create `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/input/FocusTargetResolverTest.kt` (Robolectric, mirrors `FocusManagerTest` style). Cases: leaf passthrough; null; ScrollView wrapping buttons -> first Button; FrameLayout host with EditText + Button -> Button (EditText skipped); RecyclerView afterDescendants + child -> returned as-is; ViewGroup whose only focusable is a nested scroll container -> skips the container.
  - Verification: `:app_v2:testStandardDebugUnitTest --tests "*FocusTargetResolverTest"` GREEN.

### Phase 03 - Build gate

- [ ] `assembleStandardDebug` GREEN (shared base class touched - confirm packaging).
  - Verification: BUILD SUCCESSFUL.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0289 (origin; Verified).
- **UI placement contract:** no visual change; only which control holds initial focus on non-touch input devices - moves from a container to the first real control inside it.
- **Accessibility:** improves D-pad/keyboard reachability; focus lands on an actionable control, never on a bare scroll container; EditText de-prioritised so the IME does not auto-open.
- **Validation level:** owner waived on-device test for this round - build + Robolectric unit tests + static regression review.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

Owner waived on-device verification for this round (code-only detect + fix). Verified by build + unit tests + static regression review:

- [x] `FocusTargetResolver.resolveToLeafFocusable` created; `BaseActivity` initial-focus call site wraps `getInitialFocusView()` through it (Grep-confirmed in both files).
- [x] Robolectric unit tests GREEN (6/6, `:app_v2:testStandardDebugUnitTest --tests *FocusTargetResolverTest`): leaf passthrough, null, ScrollView->first Button (EditText skipped), FrameLayout host->Button, RecyclerView afterDescendants+child returned as-is, nested scroll container skipped for the real leaf.
- [x] `assembleStandardDebug` GREEN (shared base class packaged).
- [x] Resolver uses `FOCUSABLES_ALL` (not the touch-mode-filtered default) - correct for the non-touch initial-focus path; buttons are included.
- [x] No regression to already-safe screens: leaf-returning `getInitialFocusView()` (Main/Player/Welcome) -> resolver no-op; `RecyclerView` + `afterDescendants` + bound child (Browse/cloud pickers) -> returned as-is by the explicit exception.
- [ ] MANUAL (waived): on-device D-pad confirmation that Settings / KeybindingRemap / ResourceEditor / Duplicates / AuthSessions now land initial focus on a real control. Run `/spec-sweep` or a targeted device pass if desired later.

Parked out-of-scope (CLAUDE.md 3.1): `ReceiveShareActivity` is not a `BaseActivity` subclass, so the shared hook does not reach it - see the parked ticket.

**Evidence:** build output + `app_v2/build/test-results/testStandardDebugUnitTest/*FocusTargetResolverTest.xml`.
