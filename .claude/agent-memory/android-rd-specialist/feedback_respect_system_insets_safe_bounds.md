---
name: respect-system-insets-safe-bounds
description: Every new window/screen/panel/dialog/overlay must stay inside system-bar + display-cutout safe bounds (targetSdk 35 edge-to-edge) in BOTH orientations - we keep shipping UI overlapped by OS chrome
type: feedback
metadata:
  type: feedback
---

Every NEW window, screen, Activity, Fragment, panel, bottom sheet, dialog, popup, floating card, or WindowManager overlay we build MUST stay inside the **safe area** - never assume the full window/display rect is usable. On targetSdk 35 / Android 15 edge-to-edge is **mandatory and enforced**: the status bar, gesture/3-button navigation bar, display cutout, and rounded corners draw OVER your content by default. If you don't apply insets, your UI gets overlapped by OS elements. Hold this in portrait AND landscape (Rule 11 parity).

This is the *safe-area / insets* dimension - distinct from [[no-edge-to-edge-ui-elements]], which is the *aesthetic width/height bounding* dimension. CLAUDE.md Rule 17 already mandates this; the point of this memory is that we **regress on it repeatedly** - treat it as a default reflex on every new surface, not an afterthought.

**Why:** recurring incident the owner keeps catching - new windows/panels "не уважают границы экрана и требования новых андроид и перекрываются элементами операционной системы". We step on this rake again and again. Related: [[no-edge-to-edge-ui-elements]], [[no-fullwidth-buttons-landscape]], [[play-setstatusbarcolor-false-positive]] (the Play-console edge-to-edge warnings were the app-side symptom of the same gap).

**How to apply (reflex on any new surface):**
- New Activity/screen: apply `ViewCompat.setOnApplyWindowInsetsListener(root)` consuming `WindowInsetsCompat.Type.systemBars() or displayCutout()` (add `ime()` when it has text input) -> translate to root padding; or `fitsSystemWindows` where that idiom is already used. Mirror the project's existing edge-to-edge inset pattern (e.g. `applyEdgeToEdgeInsets()` in MainActivity) rather than inventing a new one.
- Panels / floating controls / anything positioned by coordinates or anchored: clamp to the inset-safe rect, **never** to raw `resources.displayMetrics` / `maximumWindowMetrics.bounds`. A WindowManager overlay (`TYPE_APPLICATION_OVERLAY` / accessibility overlay) must subtract system-bar + cutout insets from its geometry, or it lands under the status bar / nav bar.
- Dialogs / bottom sheets: ensure content (esp. the bottom action row) is not clipped by the nav bar - add the bottom system-bars inset as padding.
- Verify in BOTH orientations, with gesture nav AND 3-button nav, and on a display-cutout device. A change that only looks right on one nav mode / no-cutout emulator is not verified.
- Audit trigger: adding a new screen/window/overlay fires the CODE_AUDIO Rule 17 check - confirm insets before calling it done.
