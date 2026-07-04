---
name: focus-frame-infra
description: App-wide TV/D-pad focus indicator = in-place per-view decoration (S0943); overlay-frame S0819 archived
metadata:
  type: project
---

Non-touch (TV remote / D-pad / keyboard / gamepad) operability is owned by **S0943** (umbrella, In Progress): app-wide reachability, focus order, initial focus, scroll-follows-focus, custom-surface D-pad, and the focus indicator.

**Focus indicator (delivered, Phase 01, device-verified 2026-07-04):** the focused view is decorated IN PLACE - one window-level `FocusDecorationController` (in `core/ui/focus/`) registers an `OnGlobalFocusChangeListener` + touch-mode listener and sets `R.drawable.focus_decoration_outline` (accent `?attr/colorPrimary` stroke) as the focused view's `foreground`, restoring the prior foreground on blur; non-touch only. Wired app-wide by `FocusDecorationActivityCallbacks` (+ `FocusDecorationFragmentCallbacks` for dialog/bottom-sheet windows, `FocusDecorationExcluded` opt-out), registered once in `FastMediaSorterApp`. It **skips views that already have a foreground** (buttons keep their own focus stroke).

**Why in-place, not an overlay:** the platform standard (Android TV Focus system, Leanback, Compose-for-TV) is that the focused element draws its OWN affordance (outline/scale/glow via `state_focused`), never a global overlay that computes coordinates. Because the outline is drawn by the view in its own bounds, it can never be offset.

**S0819 is ARCHIVED** (temp/done): it drew one travelling frame in `decorView.overlay` at coordinates from `offsetDescendantRectToMyCoords`, which ignores ancestor transforms/scroll and left the frame offset (-153,-126 on the welcome bottom bar; wrong on the right column). All `FocusFrame*` classes + `focusFrame*`/`focus_frame_*` res tokens were deleted. Do NOT reintroduce a coordinate-computing focus overlay.

**How to apply:** any TV/D-pad focus-visibility work uses the in-place decorator + S0943's plan. Reachability/order (initial focus, no-trap) is a separate S0943 concern (window-level focus listener + `nextFocus`/`FocusFinder`), not the indicator.

**Gotcha:** `offsetDescendantRectToMyCoords` silently drops ancestor `translationX/Y`/scale - never trust it for positioning an overlay against a transformed hierarchy; use `getLocationInWindow` deltas, or better, decorate the view itself.
