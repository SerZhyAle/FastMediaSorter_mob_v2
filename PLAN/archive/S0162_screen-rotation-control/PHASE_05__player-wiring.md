# S0162 Phase 05 — PlayerActivity Wiring

## Files

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`

---

## PlayerViewModel.kt — PlayerState

Add to `data class PlayerState`:

```kotlin
// S0162: rotation toggle visibility and current sensor state
val showRotationToggle: Boolean = false,   // true when followSystemRotation=false && hasAccelerometer
val playerRotationSensorEnabled: Boolean = true,
```

Add a new event (inside `sealed class PlayerEvent` or equivalent):

```kotlin
data class RotationSensorToggled(val sensorEnabled: Boolean) : PlayerEvent()
```

Add a new ViewModel function:

```kotlin
fun toggleRotationSensor() {
    val current = _state.value
    val newEnabled = !current.playerRotationSensorEnabled
    viewModelScope.launch {
        // Persist the new state
        val settings = settingsRepository.getSettings().first()
        settingsRepository.saveSettings(settings.copy(playerRotationSensorEnabled = newEnabled))
        // Emit event so Activity can apply the orientation change immediately
        emitEvent(PlayerEvent.RotationSensorToggled(newEnabled))
    }
    updateState { copy(playerRotationSensorEnabled = newEnabled) }
}
```

### Settings observer in ViewModel (existing pattern)

In the ViewModel init block (where settings are loaded and mapped to PlayerState), add:

```kotlin
// S0162: map rotation settings from AppSettings → PlayerState
followSystemRotation = settings.followSystemRotation,
playerRotationSensorEnabled = settings.playerRotationSensorEnabled,
showRotationToggle = !settings.followSystemRotation && hasAccelerometer,
```

`hasAccelerometer` must be passed into the ViewModel (or read via an injected context).
**Preferred**: pass it as a constructor parameter `private val hasAccelerometer: Boolean`
from the Activity (which calls `ScreenRotationManager.isAccelerometerPresent(this)`).

---

## PlayerActivity.kt

### Initialization (onCreate)

```kotlin
private val screenRotationManager = ScreenRotationManager()
private val hasAccelerometer: Boolean by lazy {
    screenRotationManager.isAccelerometerPresent(this)
}
```

Pass `hasAccelerometer` to `PlayerViewModel` via `@AssistedInject` or a one-time call:

```kotlin
// After viewModel is available:
viewModel.initRotationCapability(hasAccelerometer)
```

Or, simpler — read it inside `observeSettings()` without changing ViewModel API:

```kotlin
// Inside the settings observer:
screenRotationManager.apply(
    this,
    settings.followSystemRotation,
    settings.playerRotationSensorEnabled,
    hasAccelerometer
)
```

### onResume()

Add re-application of rotation on resume (reads OS auto-rotate at resume time — ADR-1):

```kotlin
val currentSettings = viewModel.currentSettings() // or read synchronously from cached state
screenRotationManager.apply(
    this,
    currentSettings.followSystemRotation,
    currentSettings.playerRotationSensorEnabled,
    hasAccelerometer
)
```

### CommandPanelCallback.onRotationToggleClicked()

```kotlin
override fun onRotationToggleClicked() {
    viewModel.toggleRotationSensor()
}
```

### PlayerEvent observer

Handle `PlayerEvent.RotationSensorToggled`:

```kotlin
is PlayerEvent.RotationSensorToggled -> {
    screenRotationManager.apply(
        this,
        followSystem = false,   // event only fires when followSystem=false
        sensorEnabled = event.sensorEnabled,
        hasAccelerometer = hasAccelerometer
    )
    commandPanelController.updateRotationToggleIcon(event.sensorEnabled)
}
```

### Settings Flow observer (existing live-settings observation)

In the existing settings observer that feeds `CommandPanelController.updateCommandAvailability()`:

```kotlin
// S0162: re-apply orientation whenever followSystemRotation changes
screenRotationManager.apply(
    this,
    settings.followSystemRotation,
    settings.playerRotationSensorEnabled,
    hasAccelerometer
)
commandPanelController.updateRotationToggleIcon(settings.playerRotationSensorEnabled)
```

---

## ImageDrawOverlayManager.kt — stopDraw()

**Current code** (line 135):
```kotlin
activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
```

**Replace with** (ADR-4):
```kotlin
// S0162: restore rotation manager state instead of unconditional UNSPECIFIED
screenRotationManager.reapply(activity, hasAccelerometer)
```

`ScreenRotationManager` instance and `hasAccelerometer` must be injected into
`ImageDrawOverlayManager` (add as constructor parameters; the manager is already
constructed inside PlayerActivity so passing them is straightforward).

---

## PlayerState update — CommandPanelController.updateCommandAvailability()

`buildActiveCommands()` already receives `state`. The `showRotationToggle` field added to
`PlayerState` is consumed by the condition in `CommandPanelLayoutPlanner.buildActiveCommands()`
(Phase 04). No additional change needed in `CommandPanelController` for visibility logic.

---

## Acceptance

- `onResume()` with `followSystem=true, OS autoRotate=ON` → screen rotates on tilt.
- `onResume()` with `followSystem=true, OS autoRotate=OFF` → screen stays fixed.
- `onResume()` with `followSystem=false, sensor=true` → screen rotates on tilt.
- `onResume()` with `followSystem=false, sensor=false` → screen stays fixed.
- Toggling the player command button flips sensor state; icon updates immediately; state
  persists across PlayerActivity restarts.
- Changing `followSystemRotation` in Settings while PlayerActivity is open:
  orientation changes without activity restart; toggle button appears/disappears.
- On device without accelerometer: no toggle button, no settings row, no crash.
- After exiting Draw Mode: rotation manager state is restored (not `UNSPECIFIED`).
