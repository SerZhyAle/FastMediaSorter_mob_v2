# S0162 Phase 01 — Domain: AppSettings + SettingsRepositoryImpl

## Files

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`

---

## Changes

### AppSettings.kt

Add two fields at end of class body (before closing `}`):

```kotlin
// S0162: Screen rotation control
// true = delegate to OS auto-rotate; false = own control via playerRotationSensorEnabled
val followSystemRotation: Boolean = true,

// S0162: Per-session sensor state (persisted; restored on next player launch).
// Active only when followSystemRotation = false.
// true = screen follows physical rotation; false = screen locked to current orientation
val playerRotationSensorEnabled: Boolean = true,
```

### SettingsRepositoryImpl.kt — companion object (keys)

Add after the `KEY_ALLOW_SEPARATE_WINDOW` block:

```kotlin
// S0162: Screen rotation control
private val KEY_FOLLOW_SYSTEM_ROTATION = booleanPreferencesKey("follow_system_rotation")
private val KEY_PLAYER_ROTATION_SENSOR_ENABLED = booleanPreferencesKey("player_rotation_sensor_enabled")
```

### SettingsRepositoryImpl.kt — read block (inside `dataStore.data.map`)

Add after the `allowSeparateWindow` line:

```kotlin
// S0162: Screen rotation control — absent key → default true (no behaviour change on upgrade)
followSystemRotation = preferences[KEY_FOLLOW_SYSTEM_ROTATION] ?: true,
playerRotationSensorEnabled = preferences[KEY_PLAYER_ROTATION_SENSOR_ENABLED] ?: true,
```

### SettingsRepositoryImpl.kt — write block (inside `dataStore.edit`)

Add after the `allowSeparateWindow` line:

```kotlin
// S0162
preferences[KEY_FOLLOW_SYSTEM_ROTATION] = settings.followSystemRotation
preferences[KEY_PLAYER_ROTATION_SENSOR_ENABLED] = settings.playerRotationSensorEnabled
```

---

## Acceptance

- `AppSettings` compiles cleanly; default values preserve current runtime behaviour.
- Round-trip write → read via `SettingsRepositoryImpl` returns the written values.
- Missing key on first run resolves to `true` for both fields.
