# S0506 - On-the-fly D-pad focus-container guard (runtime, app-wide)

**Status:** Archived
**Priority:** 55
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Origin:** owner request 2026-06-18 (multi-device input improvements); continuation of S0504/S0289

<!-- auto-approved by /spec-all - 2026-06-18 -->

---

## Goal (RU)

S0504 убрал «фокус паркуется на скролл/пейдж-контейнере» только на initial-focus (один раз при старте экрана). S0289 закрыл runtime-случай лишь точечно в `WelcomeActivity`. На остальных `BaseActivity`-экранах, если данные грузятся асинхронно после `setupViews()` или фокус позже оказывается на контейнере, первая направленная клавиша теряется. Цель — вынести dispatch-time guard в общий слой `BaseActivity`: когда приходит направленная клавиша на non-touch устройстве, а `currentFocus` — скролл/пейдж-контейнер (или null), сначала войти в первый реальный контрол (переиспользуя `FocusTargetResolver`), и только потом — обычная обработка. Экраны, где контейнер легитимно держит фокус (RecyclerView `afterDescendants`, плеер-транспорт), не затрагиваются.

## Scope decisions

- `WelcomeActivity` page+bar слайдерная модель (`isOnPagerContainer`/`enterPageFromContainer`) сохраняется как есть: она зависит от fallback на bottom bar, который общий helper не воспроизводит; экран Verified (S0289) и device-tested, регресс без device-теста недопустим. Base-guard покрывает его permissions-fragment fall-through и остальные `BaseActivity`-экраны - «свести к общему хуку» из §0 выполнено в пределах «если возможно».
- `PlayerActivity` opt-out: транспорт плеера управляет фокусом сам, guard выключается через `shouldGuardContainerFocus()`.

---

## Phase 01 - FocusTargetResolver: container-trap predicate

1. In `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/FocusTargetResolver.kt`, add a public predicate:
   - `fun isFocusTrapContainer(view: View?): Boolean` returning `true` when a directional key landing on `view` would be wasted.
   - Contract: `view == null` -> `true` (nothing focused yet); otherwise resolve via the existing `resolveToLeafFocusable(view)` and return `true` only when the resolved leaf is non-null and `!== view` (i.e. the resolver would redirect off a trapping container). A leaf control and a legitimately focus-delegating `RecyclerView` (already returned as-is by `resolveToLeafFocusable`) yield `false`.
2. Add a KDoc line explaining the predicate mirrors `resolveToLeafFocusable`'s redirect decision so the two never diverge.

**Verification:**
- `.\a.ps1 fk` -> exit 0 (Kotlin compiles).
- Grep: `isFocusTrapContainer` present in `FocusTargetResolver.kt`.

## Phase 02 - BaseActivity dispatch-time guard

1. In `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`:
   - Extract a private `isNonTouchInputActive(): Boolean` carrying the existing non-touch detection (`isTvDevice()` OR decor not in touch mode OR hardware keyboard present). Route the default `shouldRequestInitialFocus()` through it so the two checks cannot drift.
   - Add `protected open fun shouldGuardContainerFocus(): Boolean = true` - opt-out hook for screens that own their focus (player transport).
2. At the top of `dispatchKeyEvent(event)`, before `tvKeyRouter.route(event)`, insert the guard:
   - Run only when `event.action == KeyEvent.ACTION_DOWN`, the keycode is one of `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT`, `shouldGuardContainerFocus()` is true, `isNonTouchInputActive()` is true, and `FocusTargetResolver.isFocusTrapContainer(currentFocus)` is true.
   - Source view = `currentFocus ?: getInitialFocusView() ?: _binding?.root`. Resolve `FocusTargetResolver.resolveToLeafFocusable(source)`; if the leaf is non-null, `!== currentFocus`, and `requestFocus()` succeeds, `return true` (consume the first directional key; subsequent keys follow normal traversal).
   - Otherwise fall through to the existing `tvKeyRouter` routing unchanged.
3. In `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, override `shouldGuardContainerFocus(): Boolean = false` with a one-line WHY comment (transport owns its own key/focus routing via `inputDispatcher`).

**Verification:**
- `.\a.ps1 fk` -> exit 0.
- Grep: `shouldGuardContainerFocus` present in both `BaseActivity.kt` and `PlayerActivity.kt`.
- Grep: `isNonTouchInputActive` present in `BaseActivity.kt`.

## Phase 03 - Unit tests + packaging build

1. Extend `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/input/FocusTargetResolverTest.kt` with `isFocusTrapContainer` cases:
   - `null` -> `true`.
   - leaf `Button` -> `false`.
   - `RecyclerView` with `FOCUS_AFTER_DESCENDANTS` and a child -> `false`.
   - `ScrollView` wrapping controls -> `true`.
   - `LinearLayout` whose only real leaf is past a nested scroll container -> `true`.
2. Keep the new tests in the same Robolectric class/style (`@Config(sdk = [34])`).

**Verification:**
- `.\gradlew.bat testStandardDebugUnitTest --tests "*FocusTargetResolverTest*"` -> BUILD SUCCESSFUL, all cases green (read the per-class XML report).
- `.\a.ps1 d` -> standard debug APK builds (packaging proof for the BaseActivity dispatch change).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0504 (initial-focus guard, Verified), S0289 (multimodal parity, Verified).
- **UI behaviour:** no new visible UI; change is dispatch-time focus redirection on non-touch input only. Touch interaction unaffected (focus redirect no-ops in touch mode). Device verification owner-waived as for S0504.

## Связь

- S0504 (initial-focus guard, Verified), S0289 (multimodal parity, Verified).

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

- P01 `isFocusTrapContainer` declared in `FocusTargetResolver.kt`.
- P02 guard `guardContainerFocusOnDirectionalKey` runs before `tvKeyRouter.route` (line 311 < 312); `isNonTouchInputActive` + `shouldGuardContainerFocus` present; `PlayerActivity` opts out.
- P03 5 `isFocusTrapContainer` cases added; `FocusTargetResolverTest` 11 tests, 0 failures; `assembleStandardDebug` BUILD SUCCESSFUL.
- Debug-tag invariant: zero `Timber.d("S0506:` tags (status not BlockNeedUserTest).
- Neuroslop gate: delta 0 across all categories.

### Manual / on-device

- [ ] Device verification owner-waived (as for S0504). Optional spot-check on a TV/D-pad or Quest3: open Settings/Duplicates after data loads, press a D-pad direction, confirm focus jumps to the first real control rather than parking on the list container.
