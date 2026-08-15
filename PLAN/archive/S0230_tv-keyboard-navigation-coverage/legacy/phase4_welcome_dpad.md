# Phase 4 — WelcomeActivity D-pad fix

**Status:** DONE

## Goal

WelcomeActivity overrides `onTvNavigation` to handle D-pad/keyboard for the
ViewPager2 slider and bottom navigation buttons.

## Changes to WelcomeActivity

- Override `onTvNavigation(action: TvNavAction): Boolean`:
  - `TvNavAction.Next` → `binding.viewPager.currentItem = (currentPage + 1).coerceAtMost(pagerAdapter.itemCount - 1); true`
  - `TvNavAction.Prev` → `binding.viewPager.currentItem = (currentPage - 1).coerceAtLeast(0); true`
  - `TvNavAction.Select` → activate the visible action button (Next/Finish if present and visible); return true
  - `TvNavAction.Back` → delegate to `onBackPressedDispatcher.onBackPressed(); true`
  - other → `false` (let base handle)
- Override `getInitialFocusView()`: return `binding.btnNext` so TV has a focused button on open.
- Layout: add `focusable="true"`, `clickable="true"` to all nav buttons in `activity_welcome.xml`
  (btnPrevious, btnSkip, btnNext, btnFinish). Also add to landscape counterpart if exists.
  Note: no `activity_welcome.xml` in `layout-land/` — only page-level layouts have landscape variants.
  So landscape counterpart for `activity_welcome.xml` does not exist; no action needed.

## Steps

- [x] Override `onTvNavigation` in WelcomeActivity
- [x] Override `getInitialFocusView` in WelcomeActivity — returns `binding.btnNext`
- [x] Add `focusable="true"` / `clickable="true"` to all 4 nav buttons in `activity_welcome.xml`
- [x] No landscape counterpart for `activity_welcome.xml` confirmed (layout-land/ has page-level layouts only)
