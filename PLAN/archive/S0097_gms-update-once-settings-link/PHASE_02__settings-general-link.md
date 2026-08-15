# S0097 Phase 02 — Settings General GMS link

## Goal

Show a persistent text-link at the top of "Settings → General" (before any settings group)
when `GmsAvailabilityChecker.isOk == false`. Tapping it opens Play Store on the GMS page,
identical to the snackbar "Update" action.

## Steps

### 1. Add string resources

**EN** `app_v2/src/main/res/values/strings.xml`:
```xml
<string name="gms_settings_link">Google Play Services requires an update for full functionality. Tap to update.</string>
```

**RU** `app_v2/src/main/res/values-ru/strings.xml`:
```xml
<string name="gms_settings_link">Для полноценной работы приложения необходимо обновить Google Play Services. Нажмите для обновления.</string>
```

**UK** `app_v2/src/main/res/values-uk/strings.xml`:
```xml
<string name="gms_settings_link">Для повноцінної роботи додатка необхідно оновити Google Play Services. Натисніть для оновлення.</string>
```

> **Verification:** `check_strings_localized.ps1 -KeyPrefix "gms_settings_link"` exits 0.

### 2. Add banner view to portrait layout

File: `app_v2/src/main/res/layout/fragment_settings_general.xml`

Insert immediately after the LinearLayout opening tag (before the first child view — which is
the `headerInterface` or first settings group), add:

```xml
<com.google.android.material.textview.MaterialTextView
    android:id="@+id/tvGmsSettingsLink"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/colorSurfaceVariant"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    android:padding="16dp"
    android:text="@string/gms_settings_link"
    android:textAppearance="?attr/textAppearanceBodySmall"
    android:textColor="?attr/colorError"
    android:visibility="gone"
    tools:visibility="visible" />
```

> The view defaults to `gone`; shown only when GMS not OK.

### 3. Add banner view to landscape layout

File: `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

Apply equivalent change — same `MaterialTextView` with `id="@+id/tvGmsSettingsLink"`, same
attributes, inserted as the first child of the content scroll container (before the first
settings section).

> **Verification:** Both portrait and landscape layouts compile and show the view in layout
> inspector with `tools:visibility="visible"`.

### 4. Wire up in `GeneralSettingsFragment`

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`

- Add a private `setupGmsBanner()` method called from `onViewCreated` (after binding inflation).
- Inside `setupGmsBanner()`:
  - If `GmsAvailabilityChecker.isOk` → `binding.tvGmsSettingsLink.visibility = View.GONE` and return.
  - Otherwise: set `visibility = View.VISIBLE`.
  - Set `setOnClickListener` to open Play Store:
    ```kotlin
    try {
        startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.google.android.gms")))
    } catch (e: Exception) {
        startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")))
    }
    ```

> **Verification:** With `GmsAvailabilityChecker.status = Status.UPDATE_REQUIRED` (manually set
> in debug), the banner is visible in General settings and tapping it attempts to open Play Store.
> With `Status.OK`, banner is gone.
