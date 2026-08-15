# S0162 Phase 04 — Command Panel: ROTATION_TOGGLE command

## Files

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
- `app_v2/src/main/res/layout/activity_player_unified.xml`
- `app_v2/src/main/res/menu/` — whichever menu XML contains player overflow items
- `app_v2/src/main/res/drawable/ic_rotation_locked.xml` (new vector)
- `app_v2/src/main/res/drawable/ic_rotation_unlocked.xml` (new vector)
- `app_v2/src/main/res/values/strings.xml` (+ mirrors)

---

## CommandPanelLayoutPlanner.kt — PlayerCommand enum

Add after `SEARCH_YOUTUBE_MUSIC(250, …)`, before the PDF group:

```kotlin
// S0162: Rotation toggle — low-priority, shows on bar only when space permits
ROTATION_TOGGLE(490, R.id.menu_rotation_toggle, true,
    R.string.rotation_toggle_title, R.drawable.ic_rotation_unlocked),
```

The icon `ic_rotation_unlocked` is the default (sensor active state). The actual displayed icon
is set dynamically in `CommandPanelController` based on `playerRotationSensorEnabled`.

## CommandPanelLayoutPlanner.kt — buildActiveCommands()

Add in the Group 2 section, after the SEARCH_YOUTUBE_MUSIC block:

```kotlin
// S0162: Rotation toggle — only when global delegation is OFF and device has accelerometer
if (state.showRotationToggle) add(PlayerCommand.ROTATION_TOGGLE)
```

This requires `showRotationToggle: Boolean` in `PlayerViewModel.PlayerState` (Phase 05).

---

## CommandPanelController.kt

### CommandPanelCallback interface

Add:

```kotlin
fun onRotationToggleClicked()
```

### setupCommandPanelControls()

Add after the `btnBlackScreenCmd` block:

```kotlin
safeViews.btnRotationToggleCmd.setOnClickListener {
    callback.onRotationToggleClicked()
}
```

### commandPanelButtons()

Add `safeViews.btnRotationToggleCmd` to the list.

### getOverflowableButtons()

Add `safeViews.btnRotationToggleCmd` to the list.

### barViewForCommand()

Add:

```kotlin
CommandPanelLayoutPlanner.PlayerCommand.ROTATION_TOGGLE -> safeViews.btnRotationToggleCmd
```

### showOverflowMenu() — popup item click

Add:

```kotlin
R.id.menu_rotation_toggle -> callback.onRotationToggleClicked()
```

### New public function: updateRotationToggleIcon(sensorEnabled: Boolean)

```kotlin
fun updateRotationToggleIcon(sensorEnabled: Boolean) {
    val iconRes = if (sensorEnabled) R.drawable.ic_rotation_unlocked
                  else R.drawable.ic_rotation_locked
    safeViews.btnRotationToggleCmd.setImageResource(iconRes)
    safeViews.btnRotationToggleCmd.contentDescription =
        binding.root.context.getString(
            if (sensorEnabled) R.string.rotation_toggle_sensor_on_desc
            else R.string.rotation_toggle_sensor_off_desc
        )
}
```

---

## activity_player_unified.xml

Add `ImageButton` for `btnRotationToggleCmd` in the command bar, grouped with other
low-priority buttons (near `btnBlackScreenCmd`). Apply same style/dimensions as peer buttons.

Also add to `PlayerBindingSafeViews` if it is used there for null-safe access
(check existing pattern — `btnBlackScreenCmd` is `binding.btnBlackScreenCmd` not `safeViews`).

```xml
<ImageButton
    android:id="@+id/btnRotationToggleCmd"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:visibility="gone"
    android:src="@drawable/ic_rotation_unlocked"
    android:contentDescription="@string/rotation_toggle_sensor_on_desc"
    style="@style/PlayerCommandButton" />
```

---

## Menu XML

In the player overflow menu XML, add:

```xml
<item
    android:id="@+id/menu_rotation_toggle"
    android:title="@string/rotation_toggle_title" />
```

---

## Drawables

`ic_rotation_unlocked.xml` — auto-rotate enabled icon (use Material Design `screen_rotation`
24dp vector; tint applied at runtime).

`ic_rotation_locked.xml` — rotation locked icon (use Material Design `screen_lock_rotation`
24dp vector; tint applied at runtime).

Both vectors are color-agnostic (`android:tint` not set in XML; tint applied by the controller).

---

## Strings (additions to Phase 03 string files)

```xml
<!-- EN -->
<string name="rotation_toggle_title">Rotation</string>
<string name="rotation_toggle_sensor_on_desc">Screen rotation: unlocked</string>
<string name="rotation_toggle_sensor_off_desc">Screen rotation: locked</string>

<!-- RU -->
<string name="rotation_toggle_title">Поворот</string>
<string name="rotation_toggle_sensor_on_desc">Поворот экрана: разблокирован</string>
<string name="rotation_toggle_sensor_off_desc">Поворот экрана: заблокирован</string>

<!-- UK -->
<string name="rotation_toggle_title">Поворот</string>
<string name="rotation_toggle_sensor_on_desc">Поворот екрана: розблоковано</string>
<string name="rotation_toggle_sensor_off_desc">Поворот екрана: заблоковано</string>
```

---

## Acceptance

- `PlayerCommand.ROTATION_TOGGLE` has priority 490, barCapable=true.
- Button visible in command bar only when `state.showRotationToggle == true`.
- Overflow menu item `menu_rotation_toggle` routes to `onRotationToggleClicked()`.
- `updateRotationToggleIcon(true)` → unlocked icon; `updateRotationToggleIcon(false)` → locked icon.
- Strings pass locale audit for `rotation_toggle` prefix.
