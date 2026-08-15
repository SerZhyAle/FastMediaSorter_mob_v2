# Phase 3 — BaseActivity integration

**Status:** DONE

## Goal

Wire `TvKeyRouter` into `BaseActivity` so all inheritors get D-pad/keyboard
dispatch without per-Activity work.

## Changes to BaseActivity

- Inject `TvKeyRouter` via `@Inject lateinit var tvKeyRouter: TvKeyRouter`.
  BaseActivity is abstract and annotated `@AndroidEntryPoint` (or subclasses are).
  Use field injection; no constructor injection in Activity.
- Override `dispatchKeyEvent(event: KeyEvent): Boolean`:
  1. Call `super.dispatchKeyEvent(event)` FIRST for GAMEPAD sources — let existing routing run.
  2. For all other key events: call `tvKeyRouter.route(event)`.
  3. If non-null `TvNavAction` returned: call `onTvNavigation(action)`.
  4. Return result of `onTvNavigation` or fall through to `super`.
- Add `protected open fun onTvNavigation(action: TvNavAction): Boolean = false`.
- Add `protected open fun getInitialFocusView(): View? = null`.
- In `setupViews()` post-block (after `observeData()`): if `isTvDevice()` and `getInitialFocusView() != null`, call `getInitialFocusView()!!.requestFocus()`.
- Add private `fun isTvDevice(): Boolean` using `Configuration.UI_MODE_TYPE_TELEVISION`.

## Conflict avoidance for PlayerActivity

PlayerActivity already intercepts D-pad in its own `dispatchKeyEvent` and returns
true before calling super. The new `BaseActivity.dispatchKeyEvent` must call
`super.dispatchKeyEvent(event)` at the end after the router check — so if
PlayerActivity returns true from its own override, the base never runs.
BUT: PlayerActivity calls `super.dispatchKeyEvent(event)` itself — that would
re-enter BaseActivity. Solution: check in BaseActivity that the event was not
already consumed by a subclass override:

Standard Android pattern: Activity.dispatchKeyEvent calls onKeyDown/onKeyUp which
fire up the chain. The right insertion point is AFTER super in the base for the
router, or BEFORE super with explicit source check. Given PlayerActivity already
overrides `dispatchKeyEvent` and calls `super` at the end, BaseActivity should
check the source BEFORE delegating to super:

```
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val action = tvKeyRouter.route(event)
    if (action != null && onTvNavigation(action)) return true
    return super.dispatchKeyEvent(event)
}
```

PlayerActivity's own `dispatchKeyEvent` override will shadow this if it returns
true — the base `dispatchKeyEvent` never runs. If PlayerActivity calls super
(returns false from its own router), BaseActivity runs next — and the base router
checks the source filter, so DPAD events from the player's key stream hit `route()`
but PlayerActivity's own hook already consumed them. No double-handling.

## Steps

- [x] Add `@Inject lateinit var tvKeyRouter: TvKeyRouter` to BaseActivity
- [x] Add `override fun dispatchKeyEvent` to BaseActivity
- [x] Add `protected open fun onTvNavigation(action: TvNavAction): Boolean = false`
- [x] Add `protected open fun getInitialFocusView(): View? = null`
- [x] Add `private fun isTvDevice(): Boolean`
- [x] Call `getInitialFocusView()` + `requestFocus()` in setupViews post-block
- [x] Build SUCCESSFUL — PlayerActivity compiles and override chain confirmed by architecture analysis
