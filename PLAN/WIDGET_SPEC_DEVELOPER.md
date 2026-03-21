# Widget Tasks — Developer Specification

**Status:** Ready for implementation  
**Date:** 2026-03-21  
**Author:** Research pass based on actual codebase  
**Flavors:** see per-task sections  

---

## Table of Contents

1. [Task 1 — Fix ResourceLaunch widget (size + dynamic icon)](#task-1)
2. [Task 2 — New widget: Random Play Music](#task-2)
3. [Task 3 — New widget: Camera Photos](#task-3)
4. [Task 4 — Pin-to-home-screen button in ResourceEditorFragment](#task-4)
5. [Shared: new string resources](#shared-strings)
6. [Shared: new drawable resources](#shared-drawables)

---

## Overview: Current widget infrastructure

| File | Type | Notes |
|---|---|---|
| `widget/ResourceLaunchWidgetProvider.kt` | AppWidgetProvider | BroadcastReceiver; reads `widget_prefs` SP |
| `widget/ResourceLaunchWidgetConfigActivity.kt` | ComponentActivity (Compose) | Queries `ResourceDao.getAllResources()`, saves `resource_id_$id` + `resource_name_$id` |
| `widget/FavoritesWidgetProvider.kt` | AppWidgetProvider | — |
| `widget/FavoritesWidgetService.kt` | RemoteViewsService | — |
| `widget/ContinueReadingWidgetProvider.kt` | AppWidgetProvider | — |
| `res/xml/widget_resource_launch_info.xml` | AppWidgetProviderInfo | Currently `resizeMode="horizontal|vertical"` — NOT locked |
| `res/layout/widget_resource_launch.xml` | RemoteViews layout | Has `ImageView` (hardcoded `ic_folder`) + `TextView id=widget_resource_name` |
| `AndroidManifest.xml` [L104–L135] | — | All receivers registered; config activity registered |

**SharedPreferences file:** `"widget_prefs"` (MODE_PRIVATE)  
**Current saved keys per widget ID:** `resource_id_$id` (Long), `resource_name_$id` (String)  
**Missing saved keys (must add):** `resource_path_$id`, `resource_type_$id`

---

<a name="task-1"></a>
## Task 1 — Fix ResourceLaunch widget: lock size + dynamic icon

### Problem

1. `widget_resource_launch_info.xml` has `resizeMode="horizontal|vertical"` → widget can be stretched.
2. `widget_resource_launch.xml` hardcodes `android:src="@drawable/ic_folder"` in the ImageView — icon never changes regardless of resource type.
3. `ResourceLaunchWidgetConfigActivity.saveWidgetConfig()` only saves `resource_id` and `resource_name`; path and type are not persisted, so icon resolution is impossible.

### Files to modify

| File | Change |
|---|---|
| `res/xml/widget_resource_launch_info.xml` | Lock to 1×1, disable resize |
| `res/layout/widget_resource_launch.xml` | Give ImageView a proper ID: `widget_resource_icon` |
| `widget/ResourceLaunchWidgetConfigActivity.kt` | `saveWidgetConfig()` — also save `resource_path_$id` and `resource_type_$id` |
| `widget/ResourceLaunchWidgetProvider.kt` | `updateAppWidget()` — read path+type from SP, resolve icon, call `setImageViewResource()` |

### 1.1 — `widget_resource_launch_info.xml` changes

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:maxResizeWidth="40dp"
    android:maxResizeHeight="40dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_resource_launch"
    android:configure="com.sza.fastmediasorter.widget.ResourceLaunchWidgetConfigActivity"
    android:description="@string/widget_resource_launch_description"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/ic_folder" />
```

> `maxResizeWidth/Height` require API 31+ — add them unconditionally (older launchers ignore them). `targetCellWidth/Height="1"` is already present.

### 1.2 — `widget_resource_launch.xml` changes

Add `android:id="@+id/widget_resource_icon"` to the existing `ImageView`. No other layout changes needed (icon size `@dimen/item_icon_size_small`, gravity center, tint white — keep as-is).

```xml
<ImageView
    android:id="@+id/widget_resource_icon"
    android:layout_width="@dimen/item_icon_size_small"
    android:layout_height="@dimen/item_icon_size_small"
    android:src="@drawable/ic_folder"
    android:contentDescription="@string/resource"
    app:tint="@color/white" />
```

### 1.3 — `ResourceLaunchWidgetConfigActivity.saveWidgetConfig()` changes

```kotlin
private fun saveWidgetConfig(resource: ResourceEntity) {
    val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
    prefs.edit()
        .putLong("resource_id_$appWidgetId", resource.id)
        .putString("resource_name_$appWidgetId", resource.name)
        .putString("resource_path_$appWidgetId", resource.path)        // NEW
        .putString("resource_type_$appWidgetId", resource.type.name)   // NEW
        .apply()
}
```

Also update `onDeleted` in `ResourceLaunchWidgetProvider` to clean up the two new keys:

```kotlin
override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    for (id in appWidgetIds) {
        editor.remove("resource_id_$id")
        editor.remove("resource_name_$id")
        editor.remove("resource_path_$id")     // NEW
        editor.remove("resource_type_$id")     // NEW
    }
    editor.apply()
}
```

### 1.4 — `ResourceLaunchWidgetProvider.updateAppWidget()` — icon resolution

Add a private helper function `widgetIconRes()` to the companion object:

```kotlin
@DrawableRes
private fun widgetIconRes(path: String?, typeName: String?): Int {
    if (path != null) {
        when (path) {
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO  -> return R.drawable.ic_virtual_music
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO  -> return R.drawable.ic_virtual_video
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES -> return R.drawable.ic_image
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS   -> return R.drawable.ic_virtual_docs
            LocalMediaScanner.VIRTUAL_PATH_RECENT     -> return R.drawable.ic_virtual_recent
        }
    }
    return when (typeName?.let { runCatching { ResourceType.valueOf(it) }.getOrNull() }) {
        ResourceType.SMB   -> R.drawable.ic_resource_smb
        ResourceType.SFTP  -> R.drawable.ic_resource_sftp
        ResourceType.FTP   -> R.drawable.ic_resource_ftp
        ResourceType.CLOUD -> R.drawable.ic_resource_cloud
        else               -> R.drawable.ic_folder   // LOCAL or unknown
    }
}
```

> Drawable mapping notes:
> - `ic_virtual_music.xml` ✓ exists
> - `ic_virtual_video.xml` ✓ exists
> - `ic_virtual_recent.xml` ✓ exists
> - `ic_virtual_docs.xml` ✓ exists
> - `ic_image.xml` ✓ exists (reused for all_images)
> - `ic_resource_smb/sftp/ftp/cloud.xml` ✓ all exist
> - `ic_folder.xml` ✓ exists (default fallback)
> - **No dedicated `ic_virtual_images.xml`** — use `ic_image.xml`.

In `updateAppWidget()`, after reading `resourceName`, also read path and type, then call `setImageViewResource()`:

```kotlin
val resourcePath = prefs.getString("resource_path_$appWidgetId", null)
val resourceType = prefs.getString("resource_type_$appWidgetId", null)
// ...
views.setImageViewResource(
    R.id.widget_resource_icon,
    widgetIconRes(resourcePath, resourceType)
)
```

This applies in **both** the "configured" branch and the "not configured" branch (in the fallback branch keep `ic_folder` as default via `widgetIconRes(null, null)`).

### 1.5 — Backward compatibility for existing widgets

Existing widget instances have no `resource_path_$id` / `resource_type_$id` in SP. `widgetIconRes(null, null)` returns `ic_folder` — acceptable fallback. No migration needed.

---

<a name="task-2"></a>
## Task 2 — New widget: Random Play Music

### Flavors

Only `standard` and `legacy` where `BuildConfig.SUPPORT_AUDIO == true`.  
Widget receiver **must** be wrapped in a `tools:ignore` or, better, placed in a flavor-specific `src/standard/AndroidManifest.xml` overlay, or guarded at runtime via `BuildConfig.SUPPORT_AUDIO`.  
**Recommended approach:** declare receiver unconditionally in `AndroidManifest.xml` but add a `BuildConfig.SUPPORT_AUDIO` guard at the top of `onUpdate()` (simpler, no flavor manifest split needed).

### New files to create

| File | Notes |
|---|---|
| `widget/RandomMusicWidgetProvider.kt` | AppWidgetProvider |
| `res/xml/widget_random_music_info.xml` | 1×1, no configure, no resize |
| `res/layout/widget_random_music.xml` | icon + label layout (reuse widget_resource_launch.xml structure) |

### 2.1 — `res/xml/widget_random_music_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:maxResizeWidth="40dp"
    android:maxResizeHeight="40dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_random_music"
    android:description="@string/widget_random_music_description"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/ic_virtual_music" />
```

### 2.2 — `res/layout/widget_random_music.xml`

Copy of `widget_resource_launch.xml` with the label changed to `@string/widget_random_music_label` and icon hardcoded to `@drawable/ic_virtual_music` (no ID needed — icon never changes).

### 2.3 — Launching PlayerActivity with SortMode.RANDOM

**Existing `PlayerActivity.createIntent()` signature** (as of this research):

```kotlin
fun createIntent(
    context: Context,
    resourceId: Long,
    initialIndex: Int = 0,
    skipAvailabilityCheck: Boolean = false,
    initialFilePath: String? = null,
    isPlaying: Boolean? = null,
    isSlideshowEnabled: Boolean = false
): Intent
```

There is **no `shuffleOnStart` / `sortMode` extra** in the current signature.

**`PlayerViewModel`** reads `sortMode` from `resource.sortMode` (line 678, 941) — it does **not** read a sort mode from the Intent.

**Solution:** Add a new optional Intent extra `"initialSortMode"` to `PlayerActivity.createIntent()` that overrides the resource's stored `sortMode` for this launch only. `PlayerViewModel` must read and apply it.

#### Changes to `PlayerActivity.createIntent()`:

```kotlin
fun createIntent(
    context: Context,
    resourceId: Long,
    initialIndex: Int = 0,
    skipAvailabilityCheck: Boolean = false,
    initialFilePath: String? = null,
    isPlaying: Boolean? = null,
    isSlideshowEnabled: Boolean = false,
    initialSortMode: SortMode? = null        // NEW
): Intent {
    return Intent(context, PlayerActivity::class.java).apply {
        putExtra("resourceId", resourceId)
        putExtra("initialIndex", initialIndex)
        putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
        initialFilePath?.let { putExtra("initialFilePath", it) }
        isPlaying?.let { putExtra("resumeIsPlaying", it) }
        if (isSlideshowEnabled) putExtra("resumeSlideshowEnabled", true)
        initialSortMode?.let { putExtra("initialSortMode", it.name) }   // NEW
    }
}
```

#### Changes to `PlayerViewModel`:

```kotlin
private val initialSortModeOverride: SortMode? =
    savedStateHandle.get<String>("initialSortMode")
        ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
```

Then in the place where `sortMode = resource.sortMode` is assigned (lines 678, 941), change to:

```kotlin
sortMode = initialSortModeOverride ?: resource.sortMode,
```

> This is a minimal, backward-compatible change. The override is one-shot per launch and does not mutate the DB.

### 2.4 — `widget/RandomMusicWidgetProvider.kt`

```kotlin
package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.player.PlayerActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class RandomMusicWidgetProvider : AppWidgetProvider() {

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface RandomMusicEntryPoint {
        fun database(): AppDatabase
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (!BuildConfig.SUPPORT_AUDIO) return

        val views = RemoteViews(context.packageName, R.layout.widget_random_music)

        val intent = Intent(context, RandomMusicWidgetProvider::class.java).apply {
            action = ACTION_PLAY_RANDOM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_random_music_container, pendingIntent)

        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_PLAY_RANDOM) {
            if (!BuildConfig.SUPPORT_AUDIO) return
            launchRandomMusic(context)
        }
    }

    private fun launchRandomMusic(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    RandomMusicEntryPoint::class.java
                ).database()

                val resource = db.resourceDao()
                    .getAllResources()
                    .first()
                    .firstOrNull { it.path == LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO }

                if (resource == null) {
                    Timber.w("RandomMusicWidget: resource 'All Music' not found")
                    // Show Toast on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            context.getString(R.string.widget_random_music_resource_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val playerIntent = PlayerActivity.createIntent(
                    context = context,
                    resourceId = resource.id,
                    isPlaying = true,
                    initialSortMode = SortMode.RANDOM
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(playerIntent)
            } catch (e: Exception) {
                Timber.e(e, "RandomMusicWidget: failed to launch")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_RANDOM = "com.sza.fastmediasorter.widget.ACTION_PLAY_RANDOM"
    }
}
```

> **Why `goAsync()`:** `onReceive` runs on the main thread with a 10-second BroadcastReceiver timeout. Room query uses `Dispatchers.IO` — `goAsync()` prevents ANR.

> **Why not Hilt injection via `@AndroidEntryPoint`:** `AppWidgetProvider` (a `BroadcastReceiver`) cannot be Hilt-injected with `@AndroidEntryPoint` in the standard way. Use `EntryPointAccessors` pattern (same as `ResourceLaunchWidgetConfigActivity`).

### 2.5 — `AndroidManifest.xml` addition

```xml
<!-- Random Music Widget (standard + legacy flavors with SUPPORT_AUDIO) -->
<receiver android:name=".widget.RandomMusicWidgetProvider" android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.sza.fastmediasorter.widget.ACTION_PLAY_RANDOM" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/widget_random_music_info" />
</receiver>
```

---

<a name="task-3"></a>
## Task 3 — New widget: Camera Photos

### Flavors

`standard`, `photos`, `legacy` — anywhere `BuildConfig.SUPPORT_IMAGES == true`.  
Same guard pattern as Task 2.

### Approach: Variant A (real local path, no new virtual path)

Create a local resource pointing to `/storage/emulated/0/DCIM/Camera` during provisioning. Simpler than a new virtual path; `LocalMediaScanner.scanFolder()` handles real local paths fine.

### 3.1 — Add Camera resource to `ProvisionDefaultResourcesUseCase`

Add after the existing "All Images" block (after line 94 approx.):

```kotlin
// 6. Camera (local DCIM/Camera folder)
if (BuildConfig.SUPPORT_IMAGES) {
    val cameraPath = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DCIM
    ).absolutePath + "/Camera"
    val imageTypes = buildSet {
        if (settings.supportImages) add(MediaType.IMAGE)
        if (settings.supportGifs) add(MediaType.GIF)
        add(MediaType.VIDEO)  // Camera can produce videos
    }
    createVirtualResource(
        name = context.getString(R.string.resource_camera),
        path = cameraPath,
        supportedMediaTypes = imageTypes,
        profile = ResourceProfile.PHOTO_STORAGE,
        displayOrder = displayOrder++,
        displayMode = DisplayMode.GRID   // Camera grid by default
    )
}
```

> This requires `createVirtualResource()` to accept a `displayMode` parameter and pass it to `MediaResource`. Currently it does not — add an optional `displayMode: DisplayMode = DisplayMode.LIST` parameter to `createVirtualResource()`.

> **`MediaResource`** has no `displayMode` field — this is stored in `ResourceEntity.displayMode`. The provisioning code calls `resourceRepository.createResource(resource: MediaResource)`. Check that the domain→entity mapper honours `displayMode` properly.

> **If DCIM/Camera does not exist** at provisioning time: the directory will not exist yet (fresh device or no photos taken). This is fine — the resource is still created; `BrowseActivity` will simply show an empty folder. Do NOT skip provisioning if the folder is absent.

### 3.2 — Widget identification strategy

The widget finds the camera resource **dynamically by path** at click time (not by stored ID), because:
- The DB record may not exist yet when the widget is first placed (user places widget before opening app).
- Stored IDs change on app reinstall.

Search query: `db.resourceDao().getAllResources().first().firstOrNull { it.path == cameraPath }`.

### 3.3 — Forcing GRID mode in BrowseActivity

**Current state:** `BrowseActivity.createIntent()` has no `forceDisplayMode` extra. Display mode comes from `resource.displayMode` (stored in DB).

**Recommended approach:** Since the Camera resource is provisioned with `displayMode = DisplayMode.GRID`, no Intent extra is needed — the resource will always open in grid by default. This avoids adding an extra intent parameter.

**Alternative** (if you want the widget to *always* force grid even after the user changes it): add `EXTRA_OVERRIDE_DISPLAY_MODE` to `BrowseActivity.createIntent()`. This is **not required** for MVP; defer unless UX demands it.

### 3.4 — New files to create

| File | Notes |
|---|---|
| `widget/CameraPhotosWidgetProvider.kt` | AppWidgetProvider |
| `res/xml/widget_camera_photos_info.xml` | 1×1, no configure, no resize |
| `res/layout/widget_camera_photos.xml` | icon `@drawable/ic_camera` + label |

> `ic_camera.xml` does **not** exist in the repo. Must be created (see [Shared Drawables](#shared-drawables)).  
> `ic_image.xml` exists but represents "image file", not a camera — do not reuse.

### 3.5 — `res/xml/widget_camera_photos_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:maxResizeWidth="40dp"
    android:maxResizeHeight="40dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_camera_photos"
    android:description="@string/widget_camera_photos_description"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:previewImage="@drawable/ic_camera" />
```

### 3.6 — `widget/CameraPhotosWidgetProvider.kt`

Same pattern as `RandomMusicWidgetProvider`. Key differences:
- `ACTION_OPEN_CAMERA = "com.sza.fastmediasorter.widget.ACTION_OPEN_CAMERA"`
- Path constant: `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Camera"`
- On click: look up resource by path, launch `BrowseActivity.createIntent(context, resource.id)` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`
- Not-found toast: `R.string.widget_camera_resource_not_found`
- Guard: `if (!BuildConfig.SUPPORT_IMAGES) return`

**Fallback if `/DCIM/Camera` resource not found in DB:**  
Show toast and bail. Do NOT hardcode fallback to `/DCIM` — that would bypass resource management. User can manually create a resource for `/DCIM` if needed.

### 3.7 — `AndroidManifest.xml` addition

```xml
<!-- Camera Photos Widget (standard + photos + legacy flavors with SUPPORT_IMAGES) -->
<receiver android:name=".widget.CameraPhotosWidgetProvider" android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.sza.fastmediasorter.widget.ACTION_OPEN_CAMERA" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/widget_camera_photos_info" />
</receiver>
```

---

<a name="task-4"></a>
## Task 4 — Pin-to-home-screen button in ResourceEditorFragment

### Where the button lives

`ResourceEditorFragment.renderStatistics()` (line 911) already checks `mode == ResourceEditorMode.EDIT` before showing the statistics card. The new button goes inside `groupStatistics` (the collapsible content of the Statistics section), as the last element.

**Visibility rule:** show button only in `EDIT` mode, only when `statistics != null`. This is already guaranteed by `renderStatistics()`.

### API constraint

`AppWidgetManager.requestPinAppWidget()` — **API 26+**.  
`minSdk 26` for all flavors except `legacy` (`minSdk 23`).  
Add `@RequiresApi(Build.VERSION_CODES.O)` and a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` runtime guard. On legacy/API < 26: hide the button (set `isVisible = false`).

### 4.1 — Layout change: fragment_resource_editor.xml

Inside `groupStatistics` (ConstraintLayout or LinearLayout), add after the last statistics `TextView`:

```xml
<Button
    android:id="@+id/btnAddWidgetToHomeScreen"
    style="@style/Widget.Material3.Button.OutlinedButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/margin_small"
    android:text="@string/action_add_widget_to_home_screen"
    android:drawableStart="@drawable/ic_add_widget"
    android:drawablePadding="8dp"
    android:visibility="gone" />
```

> `ic_add_widget` drawable does not exist. Use `ic_folder_open_24.xml` temporarily OR add a proper icon (see [Shared Drawables](#shared-drawables)).

### 4.2 — `ResourceEditorFragment.renderStatistics()` changes

```kotlin
private fun renderStatistics(statistics: ResourceStatistics?) {
    // ... existing code ...

    // Pin-to-home-screen button (EDIT mode only, API 26+)
    val canPin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        AppWidgetManager.getInstance(requireContext()).isRequestPinAppWidgetSupported
    binding.btnAddWidgetToHomeScreen.isVisible = show && canPin

    if (show && canPin) {
        val currentResourceId = viewModel.uiState.value.formData.id  // Long
        binding.btnAddWidgetToHomeScreen.setOnClickListener {
            pinResourceLaunchWidget(currentResourceId)
        }
    }
}
```

### 4.3 — New helper: `pinResourceLaunchWidget()`

Extract to `ResourceEditorFragment` (not an Activity method, not a Manager — it's a single-purpose UI action that belongs here).

```kotlin
@RequiresApi(Build.VERSION_CODES.O)
private fun pinResourceLaunchWidget(resourceId: Long) {
    val context = requireContext()
    val manager = AppWidgetManager.getInstance(context)

    // Guard: launcher doesn't support pinning
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_SHORT).show()
        return
    }

    // Guard: keyguard locked — requestPinAppWidget silently fails
    val km = context.getSystemService(KeyguardManager::class.java)
    if (km.isKeyguardLocked) {
        Toast.makeText(context, R.string.widget_pin_unlock_screen, Toast.LENGTH_SHORT).show()
        return
    }

    // Guard: duplicate check
    val provider = ComponentName(context, ResourceLaunchWidgetProvider::class.java)
    val existingIds = manager.getAppWidgetIds(provider)
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    val alreadyExists = existingIds.any { id ->
        prefs.getLong("resource_id_$id", -1L) == resourceId
    }
    if (alreadyExists) {
        Toast.makeText(context, R.string.widget_already_exists, Toast.LENGTH_SHORT).show()
        return
    }

    // Build success callback carrying resource data
    val callbackIntent = Intent(context, WidgetPinCallbackReceiver::class.java).apply {
        action = WidgetPinCallbackReceiver.ACTION_WIDGET_PINNED
        // Pass resource data so the callback can configure the widget without
        // opening ResourceLaunchWidgetConfigActivity
        putExtra("resource_id", resourceId)
        // name, path, type will be read from DB in the receiver
    }
    val successCallback = PendingIntent.getBroadcast(
        context, resourceId.toInt(),
        callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    manager.requestPinAppWidget(provider, null, successCallback)
}
```

### 4.4 — New file: `widget/WidgetPinCallbackReceiver.kt`

This receiver fires after the user confirms widget placement in the system dialog. It reads the widgetId from the callback intent, saves the config to SharedPreferences, and calls `updateAppWidget`.

```kotlin
package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.data.local.db.AppDatabase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class WidgetPinCallbackReceiver : BroadcastReceiver() {

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface PinCallbackEntryPoint {
        fun database(): AppDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_PINNED) return

        val resourceId = intent.getLongExtra("resource_id", -1L)
        if (resourceId == -1L) return

        // The system appends EXTRA_APPWIDGET_ID to the callback intent
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PinCallbackEntryPoint::class.java
                ).database()

                val resource = db.resourceDao()
                    .getResourceById(resourceId)
                    .first()
                    ?: return@launch.also {
                        Timber.w("WidgetPinCallback: resource $resourceId not found")
                    }

                // Save config to SharedPreferences
                context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("resource_id_$appWidgetId", resource.id)
                    .putString("resource_name_$appWidgetId", resource.name)
                    .putString("resource_path_$appWidgetId", resource.path)
                    .putString("resource_type_$appWidgetId", resource.type.name)
                    .apply()

                // Update widget view
                val manager = AppWidgetManager.getInstance(context)
                ResourceLaunchWidgetProvider.updateAppWidget(context, manager, appWidgetId)

                Timber.i("WidgetPinCallback: widget $appWidgetId configured for resource ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "WidgetPinCallback: failed")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_PINNED = "com.sza.fastmediasorter.widget.ACTION_WIDGET_PINNED"
    }
}
```

> **Critical note on `requestPinAppWidget` + `configure` attribute:**  
> `ResourceLaunchWidgetProvider` has `android:configure="...ResourceLaunchWidgetConfigActivity"` in its `appwidget-provider`. When a widget is pinned via `requestPinAppWidget`, Android **does NOT** auto-launch the configure activity if a `successCallback` PendingIntent is provided — instead it fires the callback. However, some launchers may still launch the configure activity. To prevent double-configuration: in `ResourceLaunchWidgetConfigActivity.onCreate()`, check if the widget already has a config in SharedPreferences before showing the picker:
> ```kotlin
> val alreadyConfigured = prefs.getLong("resource_id_$appWidgetId", -1L) != -1L
> if (alreadyConfigured) {
>     updateWidgetAndFinish()  // just refresh and close
>     return
> }
> ```

### 4.5 — Register `WidgetPinCallbackReceiver` in `AndroidManifest.xml`

```xml
<receiver android:name=".widget.WidgetPinCallbackReceiver" android:exported="false">
    <intent-filter>
        <action android:name="com.sza.fastmediasorter.widget.ACTION_WIDGET_PINNED" />
    </intent-filter>
</receiver>
```

`android:exported="false"` — this action must only be receivable internally (the system appends the widget ID to our own PendingIntent).

---

<a name="shared-strings"></a>
## Shared: new string resources

Add to `res/values/strings.xml` (and mirror to `values-ru/` and `values-uk/`):

```xml
<!-- Widget: Random Music -->
<string name="widget_random_music_description">Play all music in shuffle mode</string>
<string name="widget_random_music_label">Random Music</string>
<string name="widget_random_music_resource_not_found">Resource "All Music" not found</string>

<!-- Widget: Camera Photos -->
<string name="widget_camera_photos_description">Open camera photo folder</string>
<string name="widget_camera_photos_label">Camera</string>
<string name="widget_camera_resource_not_found">Resource "Camera" not found</string>

<!-- Resource provisioning -->
<string name="resource_camera">Camera</string>

<!-- Pin to home screen (Task 4) -->
<string name="action_add_widget_to_home_screen">Add to home screen</string>
<string name="widget_pin_not_supported">Your launcher does not support widget pinning</string>
<string name="widget_pin_unlock_screen">Unlock the screen and try again</string>
<string name="widget_already_exists">Widget for this resource already exists</string>
```

**Russian (`values-ru/strings.xml`):**
```xml
<string name="widget_random_music_description">Воспроизвести всю музыку в случайном порядке</string>
<string name="widget_random_music_label">Случайная музыка</string>
<string name="widget_random_music_resource_not_found">Ресурс «Вся музыка» не найден</string>
<string name="widget_camera_photos_description">Открыть папку фото с камеры</string>
<string name="widget_camera_photos_label">Камера</string>
<string name="widget_camera_resource_not_found">Ресурс «Камера» не найден</string>
<string name="resource_camera">Камера</string>
<string name="action_add_widget_to_home_screen">Добавить на главный экран</string>
<string name="widget_pin_not_supported">Ваш лаунчер не поддерживает добавление виджетов</string>
<string name="widget_pin_unlock_screen">Разблокируйте экран и повторите попытку</string>
<string name="widget_already_exists">Виджет для этого ресурса уже добавлен</string>
```

**Ukrainian (`values-uk/strings.xml`):**
```xml
<string name="widget_random_music_description">Відтворити всю музику в довільному порядку</string>
<string name="widget_random_music_label">Випадкова музика</string>
<string name="widget_random_music_resource_not_found">Ресурс «Вся музика» не знайдено</string>
<string name="widget_camera_photos_description">Відкрити папку фото з камери</string>
<string name="widget_camera_photos_label">Камера</string>
<string name="widget_camera_resource_not_found">Ресурс «Камера» не знайдено</string>
<string name="resource_camera">Камера</string>
<string name="action_add_widget_to_home_screen">Додати на головний екран</string>
<string name="widget_pin_not_supported">Ваш лаунчер не підтримує додавання віджетів</string>
<string name="widget_pin_unlock_screen">Розблокуйте екран і спробуйте ще раз</string>
<string name="widget_already_exists">Віджет для цього ресурсу вже додано</string>
```

---

<a name="shared-drawables"></a>
## Shared: new drawable resources

Two new vector drawables are needed. Both should be 24×24dp, follow Material Design style, tint-compatible (single color path, no hardcoded fill).

### `ic_camera.xml` — for Camera Photos widget

Standard Material Design camera icon (camera body outline with lens circle):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" />
    <path
        android:fillColor="@android:color/white"
        android:pathData="M9,2L7.17,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" />
</vector>
```

> Or reference Material Design icons library: `@drawable/ic_photo_camera` if Compose Material Icons is in scope — but for `RemoteViews` a local XML drawable is required.

### `ic_add_widget.xml` — for "Add to home screen" button in Task 4

Can reuse an existing drawable rather than create new. Options:
- **Reuse** `ic_folder_open_24.xml` (temporarily acceptable)
- **Preferred**: Material Icon `add_to_home_screen` (24dp outline)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="?attr/colorOnSurface"
        android:pathData="M18,1.01L8,1C6.9,1 6,1.9 6,3v3H8V3h10v18H8v-3H6v3c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3C20,1.9 19.1,1.01 18,1.01zM10,16l4,-4 -4,-4v3H2v2h8v3z" />
</vector>
```

---

## Implementation order (recommended)

1. **Task 1** — pure fix, no new files (except optional drawable clarification). Low risk.
2. **Task 4** — add button to existing fragment. Requires one new receiver class.
3. **Task 2** — new widget + PlayerActivity/ViewModel change (small, backward-compatible).
4. **Task 3** — new widget + ProvisionDefaultResourcesUseCase change. Slightly more impact (provisioning runs only on first launch, so existing users are unaffected).

---

## Risk notes

| Risk | Mitigation |
|---|---|
| Existing `ResourceLaunchWidgetConfigActivity` launches after `requestPinAppWidget` on some launchers | Add "already configured" guard in `onCreate` (see Task 4.4) |
| `goAsync()` + Kotlin coroutine leak if scope isn't cancelled | Both providers use fire-and-forget `CoroutineScope(Dispatchers.IO)`, not a retained scope — acceptable for short-lived BroadcastReceiver work |
| `ProvisionDefaultResourcesUseCase` only runs once (DB empty) | Camera resource won't be added for existing users. Acceptable — they can create it manually. If needed, add a separate migration use case keyed on a version flag in Settings |
| `SortMode` written as extra String name — typo-safe? | Use `SortMode.RANDOM.name` / `SortMode.valueOf(it)` with `runCatching` fallback as shown above |
| `requestPinAppWidget` silently fails on locked screen | Guarded with `KeyguardManager.isKeyguardLocked` check (Task 4.3) |
| Legacy flavor `minSdk 23`, API 26 required for pinning | Button hidden via `Build.VERSION.SDK_INT >= O` check — no crash |
