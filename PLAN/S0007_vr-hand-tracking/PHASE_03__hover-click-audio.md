# Phase 03 · hover-click-audio

**Spec:** S0007 · vr-hand-tracking  
**Phase:** 03 / 04  
**Status:** ⬜ Not started  
**Depends on:** Phase 02 ✅

---

## Objective

Complete the audio-feedback requirements from spec §3.5:

1. **Hover-enter SFX** — play a soft navigation sound when the cursor transitions from
   non-interactive to interactive (complement to Phase 02 cursor highlight).
2. **Pinch-complete (UP) SFX** — play a click sound when the pinch is released, confirming
   the interaction completed.

The spec states: "distinct UI sounds must trigger on hover, pinch begin, and pinch complete".  
Pinch-begin (`FX_KEY_CLICK` on DOWN) already fires; this phase adds the other two.

---

## Design

### 1. Hover-enter SFX (`VrHandRayManager`)

**Problem:** `VrHandRayManager` currently has no reference to `AudioManager`.

**Solution:** Add an optional `audioManager: AudioManager?` constructor parameter (nullable
to preserve non-Quest usage where `AudioManager` may not matter). This matches the existing
pattern in `VrControllerInputManager` which already receives `audioManager`.

**SFX choice:** `AudioManager.FX_FOCUS_NAVIGATION_UP` — the system's standard navigation
confirmation tone, subtle enough not to be annoying at hover rate but audible.

**Guard:** Sound fires only on the `false → true` transition of `isHoveringInteractive`
(i.e. only when the cursor *enters* an interactive element, not on every hover-move over it).
This is already the exact condition checked in `updateCursorAppearance()` — add the
`audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)` call there.

**`VrHandRayManager` constructor change:**

```kotlin
class VrHandRayManager(
    private val activity: Activity,
    private val audioManager: AudioManager? = null,  // NEW — hover SFX
)
```

**`updateCursorAppearance` addition:**

```kotlin
private fun updateCursorAppearance() {
    val dot = cursorView ?: return
    val bg = dot.background as? GradientDrawable ?: return
    if (isHoveringInteractive) {
        bg.setColor(Color.argb(220, 0, 160, 255))
        bg.setStroke(3, Color.argb(220, 255, 255, 255))
        // Hover-enter SFX — fires only once per enter transition.
        audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
    } else {
        bg.setColor(Color.argb(220, 255, 255, 255))
        bg.setStroke(3, Color.argb(220, 0, 160, 255))
    }
}
```

**`VrPlayerActivity` call-site update** (`VrPlayerActivity.kt` ~L305):

```kotlin
val handRay = VrHandRayManager(this, audioManager)   // pass audioManager
```

Note: `audioManager` is already fetched in `VrPlayerActivity` at this point
(`getSystemService(Context.AUDIO_SERVICE)`).

---

### 2. Pinch-complete (UP) SFX (`VrControllerInputManager`)

**Location:** `VrControllerInputManager.handlePointerClick()` — the `else` branch (UP).

**SFX choice:** `AudioManager.FX_KEYPRESS_RETURN` — a slightly higher-pitched click that
distinguishes the "release" sound from the "press" `FX_KEY_CLICK` played on DOWN.

**Current code:**

```kotlin
private fun handlePointerClick(hand: Int, down: Boolean) {
    if (down) {
        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
    }
    onPointerEvent?.invoke(hand, down)
}
```

**New code:**

```kotlin
private fun handlePointerClick(hand: Int, down: Boolean) {
    if (down) {
        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
    } else {
        // Pinch-complete SFX — distinguishes release from press (spec §3.5).
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN)
    }
    onPointerEvent?.invoke(hand, down)
}
```

---

## Execution Steps

```
[ ] 1. VrHandRayManager.kt — add audioManager param (nullable, default null).
[ ] 2. VrHandRayManager.kt — add audioManager?.playSoundEffect(FX_FOCUS_NAVIGATION_UP)
       to updateCursorAppearance() inside the isHoveringInteractive = true branch.
[ ] 3. VrPlayerActivity.kt — update VrHandRayManager constructor call to pass audioManager.
[ ] 4. VrControllerInputManager.kt — add FX_KEYPRESS_RETURN on POINTER_CLICK_UP
       in handlePointerClick().
[ ] 5. Build: .\scripts\builders\build-debug.PS1 — confirm zero errors.
[ ] 6. Run dev log for each modified file:
       .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHandRayManager.kt" "VrHandRayManager" "S0007 Phase 03: hover-enter SFX via AudioManager"
       .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "VrPlayerActivity" "S0007 Phase 03: pass audioManager to VrHandRayManager"
       .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt" "VrControllerInputManager" "S0007 Phase 03: pinch-complete SFX on POINTER_CLICK_UP"
[ ] 7. Mark phase Done.
```

---

## Verification Predicates

- Build passes `assembleStandardDebug` with no errors.
- Code review: `updateCursorAppearance` calls `playSoundEffect(FX_FOCUS_NAVIGATION_UP)`
  only when transitioning to `isHoveringInteractive = true`.
- Code review: `handlePointerClick` plays `FX_KEYPRESS_RETURN` on `down = false`.
- `VrHandRayManager` constructor has `audioManager: AudioManager? = null` — calling code
  not requiring update passes null by default.

---

## Status

**Done:** ⬜
