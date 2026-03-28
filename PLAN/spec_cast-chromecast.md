# Specification: X.2 — Cast / Chromecast Slideshow Output

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 4 — Strategic (8h+, high risk)
**Roadmap entry:** Cast / Screen Mirror — Chromecast slideshow output — Google Cast SDK; receiver app needed

---

## 1. Problem Statement

There is no way to mirror or cast a slideshow to a TV or Chromecast device from FastMediaSorter. During a slideshow in `PlayerActivity`, images are rendered only on the local device screen. Users who want to present photos on a TV must use a separate app. The gap exists in `SlideshowController.kt` (no external output path), `PlayerViewModel.kt` (no cast state), and `PlayerActivity.kt` (no `MediaRouteButton`). A local HTTP proxy is also required because Cast receivers (browser-based) cannot fetch SMB/SFTP content directly.

---

## 2. Goals

1. A `MediaRouteButton` (Cast icon) appears in the `PlayerActivity` toolbar for all image-capable flavors (`standard`, `lite`, `photos`, `legacy`) while the current file is an image.
2. Tapping the Cast icon opens the standard device-picker dialog; selecting a Chromecast establishes a Cast session.
3. When a Cast session is active and slideshow is running, each slide advance pushes the current image to the Chromecast screen via `MediaInfo`.
4. Local-file images are served to the Cast receiver via an in-process HTTP proxy (`LocalCastProxyServer`). Network-source images (SMB/SFTP/FTP/Cloud) are downloaded to a temp file first, then served the same way.
5. Manual swipe/navigation during an active Cast session immediately updates the Chromecast display.
6. Disconnecting Cast stops the remote session cleanly without affecting local playback state.
7. A `CastOptionsProvider` is registered in `AndroidManifest.xml` so the Cast framework auto-discovers devices.

Non-goals for this spec:
- Video casting (requires separate `MediaInfo` stream type handling; deferred).
- Custom Web Receiver HTML/JS app (MVP uses Google's Default Media Receiver `CC1AD845`).
- Wear OS cast control.
- Background-service cast (cast only works while `PlayerActivity` is in the foreground).
- Cast for audio-only or document types (PDF/EPUB/TXT).

---

## 3. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `SlideshowController` | `ui/player/SlideshowController.kt` | Timer-based auto-advance; fires `onSlideAdvance()` callback |
| `PlayerActivity` | `ui/player/PlayerActivity.kt` | Host activity (2400 lines); delegates heavy logic to helpers |
| `PlayerViewModel` | `ui/player/PlayerViewModel.kt` | `PlayerState` holds current file list, index, slideshow flags |
| `ImageLoadingManager` | `ui/player/ImageLoadingManager.kt` | Loads images via Glide for local/network/cloud sources |
| `PlayerMediaLoaderManager` | `ui/player/helpers/PlayerMediaLoaderManager.kt` | Routes media type to correct renderer |
| `overflow_menu_player.xml` | `res/menu/overflow_menu_player.xml` | Toolbar overflow items for player screen |
| `FastMediaSorterApp` | `FastMediaSorterApp.kt` | Application class; initialises SDKs at startup |

The current architecture has no output pathway beyond the local `ImageView`. `SlideshowController.onSlideAdvance()` updates only the in-process renderer. There is no `CastContext` initialisation, no `SessionManagerListener`, and no HTTP proxy to serve content to an external browser-based receiver.

---

## 4. Proposed Architecture

### 4.1 CastOptionsProvider — SDK Entry Point

A minimal class implementing `OptionsProvider` from the Cast framework, registered via `AndroidManifest.xml` `<meta-data>`. Returns a `CastOptions` object pointing at the Default Media Receiver App ID.

```kotlin
// core/cast/CastOptionsProvider.kt
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
```

`CastContext.getSharedInstance(applicationContext)` must be called once in `FastMediaSorterApp.onCreate()` so the Cast SDK is ready before any Activity starts.

### 4.2 LocalCastProxyServer — HTTP Bridge for Cast Receiver

The Cast receiver runs in a Chromium-based browser. It cannot access `file://` URIs, SMB shares, or SFTP paths. A minimal in-process HTTP server (NanoHTTPD) serves the current image file over `http://127.0.0.1:{port}/cast-image`.

```kotlin
// core/cast/LocalCastProxyServer.kt
class LocalCastProxyServer(port: Int = 8765) : NanoHTTPD(port) {
    private var currentFile: File? = null

    fun serveFile(file: File) { currentFile = file }

    override fun serve(session: IHTTPSession): Response {
        val file = currentFile ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "no file"
        )
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "image/jpeg"
        return newChunkedResponse(Response.Status.OK, mime, file.inputStream())
    }

    fun castUrl(): String = "http://127.0.0.1:$listeningPort/cast-image"
}
```

For local-storage images, `ImageLoadingManager` provides the `File`. For network/cloud sources, `CastSlideshowManager` triggers a download to `cacheDir/cast_current.jpg` before updating `LocalCastProxyServer`.

### 4.3 CastSlideshowManager — Session Lifecycle and Image Sending

The primary manager class. Holds a reference to `CastContext`, listens for session changes, and exposes a single `sendCurrentImage(file: MediaFile)` method called from `PlayerActivity` on every slide change.

Key responsibilities:
- `init`: register `SessionManagerListener` on the Cast `SessionManager`.
- `onCastSessionStarted`: start `LocalCastProxyServer`, set `_isCasting = true`.
- `onCastSessionEnded` / `onCastSessionSuspended`: stop proxy server, reset state.
- `sendCurrentImage(file)`: resolve local `File` or download to cache → update proxy → call `RemoteMediaClient.load(MediaInfo)`.
- `release()`: unregister listener, stop proxy server (call from `PlayerActivity.onDestroy`).

```kotlin
// ui/player/helpers/CastSlideshowManager.kt
class CastSlideshowManager(
    private val context: Context,
    private val lifecycleScope: CoroutineScope,
    private val onCastStateChanged: (isCasting: Boolean) -> Unit
) {
    val isCasting: Boolean get() = _isCasting
    // ...
    fun sendCurrentImage(file: MediaFile) { /* resolve → proxy → load */ }
    fun release() { /* unregister, stop server */ }
}
```

### 4.4 PlayerViewModel Cast State Extension

Two fields are added to `PlayerState`:

```kotlin
val isCasting: Boolean = false,
val castDeviceName: String? = null
```

A new event is added to `PlayerEvent`:

```kotlin
data class CastStateChanged(val isCasting: Boolean, val deviceName: String?) : PlayerEvent()
```

### 4.5 PlayerActivity Integration

`PlayerActivity` creates a `CastSlideshowManager` instance in `onCreate`. The existing `onSlideAdvanceOrNavigate()` path (already called on swipe, slideshow advance, and keyboard navigation) is extended: if `castSlideshowManager.isCasting`, also call `castSlideshowManager.sendCurrentImage(currentFile)`.

The `onCreateOptionsMenu` path adds a `MediaRouteButton` wired to the system `MediaRouteSelector`. The button is shown only when `currentFile` is an image type.

### 4.6 New Classes / Files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `CastOptionsProvider.kt` | `core/cast/` | ≤ 40 |
| `LocalCastProxyServer.kt` | `core/cast/` | ≤ 200 |
| `CastSlideshowManager.kt` | `ui/player/helpers/` | ≤ 350 |

---

## 5. Data Flow

```
User triggers slide change (swipe / SlideshowController.onSlideAdvance / keyboard)
  │
  ▼
PlayerActivity.onSlideChange(mediaFile)
  │
  ├─→ [local display] ImageLoadingManager.loadImage(mediaFile)   ← unchanged
  │
  └─→ [cast path, if isCasting]
        CastSlideshowManager.sendCurrentImage(mediaFile)
          │
          ├─ LOCAL file ──────────────────────────────────────────┐
          │                                                        │
          ├─ NETWORK (SMB/SFTP/FTP) → coroutine download         │
          │    → cacheDir/cast_current.jpg                        │
          │                                                        ▼
          └─ CLOUD → CloudStorageClient.download()         LocalCastProxyServer.serveFile(file)
               → cacheDir/cast_current.jpg                        │
                                                                   ▼
                                                    RemoteMediaClient.load(
                                                      MediaInfo(proxy.castUrl())
                                                    )
                                                                   │
                                                                   ▼
                                                    Chromecast receiver (Default Media Receiver)
                                                    fetches http://127.0.0.1:8765/cast-image
                                                    and displays the image full-screen
```

---

## 6. Files to Modify

| File | Change |
|------|--------|
| `app_v2/build.gradle.kts` | Add `play-services-cast-framework:21.4.0`, `androidx.mediarouter:mediarouter:1.7.0`, `com.nanohttpd:nanohttpd:2.3.1` |
| `app_v2/src/main/AndroidManifest.xml` | Add `<meta-data>` for `CastOptionsProvider`; add `INTERNET` permission if absent |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Add `menu_cast` item with `app:actionProviderClass="androidx.mediarouter.app.MediaRouteActionProvider"` and `showAsAction="always"` |
| `app_v2/src/main/res/values/strings.xml` | Add EN strings: `cast_to_chromecast`, `cast_connected`, `cast_disconnected` |
| `app_v2/src/main/res/values-ru/strings.xml` | Add RU translations |
| `app_v2/src/main/res/values-uk/strings.xml` | Add UK translations |
| `FastMediaSorterApp.kt` | Call `CastContext.getSharedInstance(this)` in `onCreate()` |
| `PlayerActivity.kt` | Instantiate `CastSlideshowManager`; wire to slide-advance path; add `MediaRouteButton` in `onCreateOptionsMenu`; call `release()` in `onDestroy` (backup required: 2400 lines) |
| `PlayerViewModel.kt` | Add `isCasting: Boolean` + `castDeviceName: String?` to `PlayerState`; add `CastStateChanged` event (backup required: 1434 lines) |
| `app_v2/proguard-rules.pro` | Add Cast SDK keep rules |

---

## 7. Risk Analysis

| Risk | Mitigation |
|------|-----------|
| NanoHTTPD `127.0.0.1` blocked by Android 14+ network security policy | Add `<domain includeSubdomains="false">127.0.0.1</domain>` to `network_security_config.xml`; test on API 34 emulator |
| Cast receiver cannot reach device on same Wi-Fi if Wi-Fi Direct / hotspot active | Detect Wi-Fi connectivity before enabling Cast button; show `Toast` if not on Wi-Fi |
| Large network images time out before Cast session times out (~5 s) | Show progress indicator in Cast button while download is in progress; skip cast for files > 20 MB |
| `CastContext.getSharedInstance()` crashes if called before Google Play Services are available | Wrap in `try/catch` in `FastMediaSorterApp`; disable Cast feature gracefully if unavailable |
| `PlayerActivity` already exceeds 1000-line limit (2400 lines) | Wire Cast via `CastSlideshowManager` without adding logic to `PlayerActivity` — only 2–3 call-sites added |
| Default Media Receiver shows ugly controls or branding | Acceptable for MVP; switch to Custom Receiver (separate spec) when needed |
| Port 8765 already in use | `LocalCastProxyServer` tries port 8765 and falls back to `8766`, `8767`; logs port chosen via Timber |
| Temp file `cast_current.jpg` not cleaned up | `CastSlideshowManager.release()` deletes temp file; also `FileProvider` cleanup on app restart |

---

## 8. Implementation Steps

1. **Backup** `PlayerActivity.kt` → `temp/PlayerActivity_backup_20260328.kt`
2. **Backup** `PlayerViewModel.kt` → `temp/PlayerViewModel_backup_20260328.kt`
3. Add Cast Framework + MediaRouter + NanoHTTPD to [app_v2/build.gradle.kts](app_v2/build.gradle.kts):
   ```kotlin
   implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
   implementation("androidx.mediarouter:mediarouter:1.7.0")
   implementation("com.nanohttpd:nanohttpd:2.3.1")
   ```
4. Create [core/cast/CastOptionsProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt) implementing `OptionsProvider`.
5. Create [core/cast/LocalCastProxyServer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt) extending `NanoHTTPD`.
6. Create [ui/player/helpers/CastSlideshowManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastSlideshowManager.kt).
7. Add `<meta-data>` for `CastOptionsProvider` to `AndroidManifest.xml` (inside `<application>`):
   ```xml
   <meta-data
       android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
       android:value="com.sza.fastmediasorter.core.cast.CastOptionsProvider"/>
   ```
8. Add `INTERNET` permission to `AndroidManifest.xml` if not already present.
9. Add `network_security_config.xml` entry to allow cleartext to `127.0.0.1` (loopback proxy).
10. Add EN Cast strings to [res/values/strings.xml](app_v2/src/main/res/values/strings.xml):
    - `cast_to_chromecast` = "Cast to Chromecast"
    - `cast_connected` = "Casting to %s"
    - `cast_disconnected` = "Cast disconnected"
    - Run dev log: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add Cast string resources (EN)"`
11. Add RU Cast strings to [res/values-ru/strings.xml](app_v2/src/main/res/values-ru/strings.xml).
    - Run dev log for RU file.
12. Add UK Cast strings to [res/values-uk/strings.xml](app_v2/src/main/res/values-uk/strings.xml).
    - Run dev log for UK file.
13. Add `menu_cast` item to [res/menu/overflow_menu_player.xml](app_v2/src/main/res/menu/overflow_menu_player.xml):
    ```xml
    <item
        android:id="@+id/menu_cast"
        android:title="@string/cast_to_chromecast"
        app:actionProviderClass="androidx.mediarouter.app.MediaRouteActionProvider"
        app:showAsAction="always"/>
    ```
    - Run dev log: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/menu/overflow_menu_player.xml" "menu" "Add Cast button to player toolbar"`
14. Add `CastContext.getSharedInstance(this)` call to `FastMediaSorterApp.onCreate()`.
    - Run dev log.
15. Add `isCasting` + `castDeviceName` to `PlayerViewModel.PlayerState`; add `CastStateChanged` event.
    - Run dev log: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "PlayerViewModel" "Add cast state fields"`
16. Wire `CastSlideshowManager` into `PlayerActivity`:
    - Instantiate in `onCreate`.
    - Extend slide-advance path to call `castSlideshowManager.sendCurrentImage(currentFile)` when casting.
    - Bind `MediaRouteSelector` to `menu_cast` in `onCreateOptionsMenu`.
    - Call `castSlideshowManager.release()` in `onDestroy`.
    - Run dev log: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "PlayerActivity" "Wire CastSlideshowManager for Chromecast slideshow output"`
17. Add Cast SDK keep rules to [proguard-rules.pro](app_v2/proguard-rules.pro):
    ```
    -keep class com.google.android.gms.cast.** { *; }
    -keep class com.google.android.gms.cast.framework.** { *; }
    ```
    - Run dev log.
18. Run `.\gradlew.bat assembleStandardDebug` and verify Cast button appears in Player toolbar when viewing an image.
19. Test: start slideshow → connect to Chromecast → confirm images appear on TV screen.
20. Run dev log for spec file:
    ```powershell
    .\scripts\add_to_dev_log.ps1 "PLAN/spec_cast-chromecast.md" "spec" "Add specification for X.2"
    ```

---

## 9. Out of Scope (future items)

- **Custom Web Receiver app** — HTML/JS/CSS project; needed for custom UI, branding, or audio/video casting.
- **Video casting** — requires `MediaInfo` with `STREAM_TYPE_BUFFERED` and direct-URL access; blocked by SMB/SFTP limitations.
- **Audio casting** — Chromecast Audio; different receiver type.
- **Cast queue management** — sending a full playlist to the receiver so it auto-advances without the phone.
- **Wear OS cast control** — pause/stop cast from watch.
- **Background cast** — keeping cast alive when `PlayerActivity` is backgrounded requires a foreground Service.
- **Cast state persistence** — reconnecting to an in-progress session after app restart.
