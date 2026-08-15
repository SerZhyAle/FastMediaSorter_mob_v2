# S0162 Phase 03 — Settings UI

## Files

- `app_v2/src/main/res/layout/fragment_settings_playback.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
- `app_v2/src/main/res/values/strings.xml` (+ `strings_ru.xml`, `strings_uk.xml`)

---

## Layout: fragment_settings_playback.xml

Add inside the "Player UI" collapsible section, after `layoutHideSystemUiInFullscreen`
(the `switchHideSystemUiInFullscreen` row):

```xml
<!-- S0162: Screen rotation control -->
<LinearLayout
    android:id="@+id/layoutFollowSystemRotation"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="8dp"
    android:paddingBottom="8dp">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/setting_follow_system_rotation_title"
        style="@style/SettingLabel" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchFollowSystemRotation"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />

</LinearLayout>
```

- The `layoutFollowSystemRotation` view is hidden (`isVisible = false`) on devices without
  `PackageManager.FEATURE_SENSOR_ACCELEROMETER` (guard applied in `setupViews()`).

---

## PlaybackSettingsFragment.kt — setupViews()

Add after the `switchHideSystemUiInFullscreen` listener block:

```kotlin
// S0162: Screen rotation control — hide entire row on non-sensor devices
val hasAccelerometer = requireContext().packageManager
    .hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
binding.layoutFollowSystemRotation.isVisible = hasAccelerometer

if (hasAccelerometer) {
    binding.switchFollowSystemRotation.setOnCheckedChangeListener { _, isChecked ->
        if (isUpdatingFromSettings) return@setOnCheckedChangeListener
        val current = viewModel.settings.value
        viewModel.updateSettings(current.copy(followSystemRotation = isChecked))
    }
}
```

## PlaybackSettingsFragment.kt — observeData()

Add inside the `collectOnLifecycle(viewModel.settings)` block after the
`switchHideSystemUiInFullscreen` sync:

```kotlin
if (binding.switchFollowSystemRotation.isChecked != settings.followSystemRotation) {
    binding.switchFollowSystemRotation.isChecked = settings.followSystemRotation
}
```

---

## Strings

### strings.xml (EN)

```xml
<string name="setting_follow_system_rotation_title">Rotate screen with OS auto-rotate</string>
<string name="setting_follow_system_rotation_summary">When on, the app follows the system auto-rotate setting. When off, rotation is controlled manually from the player.</string>
```

### strings_ru.xml

```xml
<string name="setting_follow_system_rotation_title">Поворачивать экран вслед за ОС</string>
<string name="setting_follow_system_rotation_summary">Включено — приложение следует системной настройке авторотации. Выключено — управление поворотом вручную из плеера.</string>
```

### strings_uk.xml

```xml
<string name="setting_follow_system_rotation_title">Повертати екран разом із ОС</string>
<string name="setting_follow_system_rotation_summary">Увімкнено — застосунок слідує системному налаштуванню авторотації. Вимкнено — керування поворотом вручну з плеєра.</string>
```

---

## Acceptance

- Switch appears in "Player UI" section on real phone; hidden on non-sensor device/emulator.
- Toggle persists across app restarts (DataStore round-trip via Phase 01).
- Switch programmatic update (`isUpdatingFromSettings` guard) does not fire the listener.
- Strings pass `check_strings_localized.ps1 -KeyPrefix setting_follow_system_rotation`.
