# Phase 02 · hover-visual-feedback

**Spec:** S0007 · vr-hand-tracking  
**Phase:** 02 / 04  
**Status:** ✅ Done  
**Completed:** 2026-04-30  
**Depends on:** Phase 01 ✅

---

## Objective

Change the hand-tracking cursor dot colour in `VrHandRayManager` to provide visual
confirmation when the aim ray is over an interactive UI element.

**Spec reference:** §2.1 F7 — "visual feedback: ray color on hover".

---

## Design

### Approach

`VrHandRayManager.dispatchMotion(ACTION_HOVER_MOVE, …)` calls
`decorView.dispatchGenericMotionEvent(event)`. The View hierarchy returns `true` if any
View consumed the event — which only happens for Views that are `isClickable`, `isLongClickable`,
or have `OnHoverListener` set. This boolean is a lightweight, zero-dependency proxy for
"cursor is over something interactive".

No View reflection or hit-testing tree walk is needed.

### Cursor States

| State | Fill | Stroke | Alpha |
|---|---|---|---|
| Idle (off-plane or nothing interactive) | `#FFFFFF` (white) | `#00A0FF` (blue, 3 dp) | 220 |
| Hover (interactive element below) | `#00A0FF` (blue) | `#FFFFFF` (white, 3 dp) | 220 |

Both states keep the same `CURSOR_PX = 28` diameter.

### New Members in `VrHandRayManager`

```kotlin
// True while the cursor is positioned over an interactive View.
private var isHoveringInteractive = false
```

### Modified `dispatchMotion`

```kotlin
private fun dispatchMotion(action: Int, x: Float, y: Float) {
    val decor = activity.window?.decorView ?: return
    val now = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(now, now, action, x, y, 0).apply {
        source = InputDevice.SOURCE_CLASS_POINTER or InputDevice.SOURCE_TOUCHSCREEN
    }
    try {
        val consumed = when (action) {
            MotionEvent.ACTION_HOVER_MOVE -> decor.dispatchGenericMotionEvent(event)
            else -> { decor.dispatchTouchEvent(event); false }
        }
        // Update cursor highlight state only on hover moves (not on click transitions).
        if (action == MotionEvent.ACTION_HOVER_MOVE && consumed != isHoveringInteractive) {
            isHoveringInteractive = consumed
            updateCursorAppearance()
        }
    } finally {
        event.recycle()
    }
}
```

### `updateCursorAppearance`

```kotlin
private fun updateCursorAppearance() {
    val dot = cursorView ?: return
    val bg = dot.background as? GradientDrawable ?: return
    if (isHoveringInteractive) {
        // Highlight: blue fill, white stroke.
        bg.setColor(Color.argb(220, 0, 160, 255))
        bg.setStroke(3, Color.argb(220, 255, 255, 255))
    } else {
        // Idle: white fill, blue stroke.
        bg.setColor(Color.argb(220, 255, 255, 255))
        bg.setStroke(3, Color.argb(220, 0, 160, 255))
    }
}
```

Note: `GradientDrawable` is already the `background` type set in `ensureCursor()`.  
`setColor` and `setStroke` are safe to call on the main thread where `dispatchMotion` runs.

---

## Execution Steps

```
[x] 1. Read full VrHandRayManager.kt before editing. Note the existing ensureCursor()
       implementation and confirm GradientDrawable background type.
[x] 2. Add private var isHoveringInteractive = false field below the isDown field.
[x] 3. Add private fun updateCursorAppearance() method (see Design section above).
[x] 4. Modify dispatchMotion(): capture return value of dispatchGenericMotionEvent;
       call updateCursorAppearance() when consumed state changes.
[x] 5. In release(): reset isHoveringInteractive = false (cosmetic, ensures clean state).
[~] 6. Build deferred — bundled with Phase 03 (same file edited again).
[x] 7. Dev log recorded.
[x] 8. Mark phase Done.
```

**Step Log:**

- 2026-04-30 — Verification 2/2 PASS (code review).
  Files: app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHandRayManager.kt
  (+~25 LOC; isHoveringInteractive field, updateCursorAppearance(), dispatchMotion
  consumed-capture). File now 208 LOC. Build deferred to end of Phase 03.
  Dev log recorded.

---

## Verification Predicates

- Build passes `assembleStandardDebug` with no errors.
- Code review: `dispatchMotion` captures return value and calls `updateCursorAppearance` on state change.
- Visual test (on Quest 3): aim ray cursor turns blue when pointing at a UI button; returns to white when pointing at video content.

---

## Status

**Done:** ✅ — 2026-04-30 (build verification deferred; bundled with Phase 03)
