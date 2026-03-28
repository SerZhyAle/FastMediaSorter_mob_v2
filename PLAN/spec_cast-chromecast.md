# Specification: X.2 — Cast / Chromecast Media Output

**Status:** Implemented (2026-03-28)
**Date:** 2026-03-28
**Tier:** 4 — Strategic (8h+, high risk)
**Roadmap entry:** Cast / Screen Mirror — Chromecast slideshow output — Google Cast SDK; receiver app needed

---

## 1. Problem Statement

There is no way to send any media (images, GIFs, audio, video) from `PlayerActivity` to a Chromecast or Google Cast-compatible device. Users who want to display a photo slideshow on a TV, listen to music through a Cast speaker, or watch a local video on a large screen must use a separate application. The gap spans three layers: no `CastContext` initialisation in `FastMediaSorterApp`, no Cast menu entry in `CommandPanelController`'s overflow popup, and no in-process HTTP proxy to bridge the phone's file system (including SMB/SFTP/FTP/Cloud sources) to the Cast receiver's browser-based renderer.

An additional architectural defect exists in the prior draft of this spec: the proxy URL was incorrectly set to `http://127.0.0.1:{port}/…`, which is the phone's own loopback and unreachable from the Chromecast device. The proxy must bind to all interfaces (`0.0.0.0`) and the URL advertised to the Cast receiver must use the phone's actual Wi-Fi LAN IP.

---

## 2. Goals

1. A **"Cast to Chromecast"** item appears in the player overflow menu for `IMAGE`, `GIF`, `AUDIO`, and `VIDEO` media types — in the same style and position as the existing "Search in YouTube Music" item.
2. The item is visible only when the device is connected to Wi-Fi (Cast requires LAN reachability).
3. Tapping the item opens the standard `MediaRouteChooserDialogFragment` device-picker.
4. Selecting a Cast device starts a session; the current media file is immediately sent to the receiver.
5. **IMAGE / GIF**: served to the receiver via an in-process NanoHTTPD proxy bound to the phone's LAN IP; local files served directly, network/cloud files downloaded to `cacheDir/cast_current.[ext]` first.
6. **AUDIO**: local files served directly; network/cloud files downloaded to `cacheDir/cast_current.[ext]` first (same pattern as images).
7. **VIDEO**: local files served directly; network/cloud files ≤ 50 MB downloaded to `cacheDir/cast_current.[ext]` first; files > 50 MB show a Toast explaining the limit and do not cast.
8. Disconnecting or ending the Cast session cleans up temp files and resets state without affecting local playback.
9. A `CastOptionsProvider` registered in `AndroidManifest.xml` enables automatic Cast device discovery.
10. All four product flavors are supported without any `BuildConfig` guard — Cast SDK works on all supported API levels.

Non-goals for this spec:
- Custom Web Receiver HTML/JS app (MVP uses Google's Default Media Receiver `CC1AD845`).
- Video casting for large (> 50 MB) uncached network/cloud video.
- Cast queue management (sending a full playlist to the receiver).
- Wear OS cast control.
- Background cast (keeping a session alive when `PlayerActivity` is backgrounded — requires a foreground Service).
- Cast state persistence across app restarts.
- PDF, TEXT, or EPUB casting (receiver cannot render these types).

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Full scope: IMAGE, GIF, AUDIO, VIDEO |
| `lite`     | ✅ | IMAGE, GIF, VIDEO (no AUDIO feature flag; Cast item hidden for AUDIO if `BuildConfig.FEATURE_AUDIO = false`) |
| `photos`   | ✅ | IMAGE, GIF only (`BuildConfig.FEATURE_VIDEO = false`, `FEATURE_AUDIO = false`) |
| `legacy`   | ✅ | Full scope: IMAGE, GIF, AUDIO, VIDEO (minSdk 23, Cast SDK supports API 21+) |

No new `BuildConfig` flag is required. Existing flavor feature flags (`FEATURE_AUDIO`, `FEATURE_VIDEO`) already gate which `MediaType` values are reachable in the player, so the Cast menu item's visibility condition (`isImage || isGif || isAudio || isVideo`) is naturally correct per flavor.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | Cast SDK `play-services-cast-framework:21.4.0` requires API 21 minimum — fully compatible. `WifiManager.connectionInfo.ipAddress` is available and not deprecated until API 31. |
| 26+ (standard minSdk) | Default path. |
| 31+ (Android 12) | `WifiManager.connectionInfo` is deprecated in favour of `ConnectivityManager` + `LinkProperties`. Wrap with `Build.VERSION.SDK_INT >= 31` branch using `LinkAddress` to obtain the LAN IP. |
| 34+ (Android 14) | No additional constraints. Cast SDK 21.4.0 is compatible with `compileSdk 35`. |

The `WifiManager` deprecation on API 31+ is the only fork. `CastMediaManager` must use a helper method that dispatches to the correct API.

### 3.3 Wear OS Impact

No Wear OS changes required. Cast control from the watch is an explicit non-goal of this spec.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `CommandPanelController` | `ui/player/CommandPanelController.kt` (896 lines) | Manages all command panel buttons and the overflow popup menu; exposes `CommandPanelCallback` interface |
| `CommandPanelCallback` | `ui/player/CommandPanelController.kt:43` | Interface with one method per player action (back, nav, rename, delete, lyrics, YouTube Music search, etc.) |
| `PlayerCommandPanelCallbackImpl` | `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` (223 lines) | Implements `CommandPanelCallback`; delegates to `PlayerActivity` methods |
| `PlayerActivity` | `ui/player/PlayerActivity.kt` (2400 lines) | Host activity; holds all manager instances; delegates heavy logic |
| `ImageLoadingManager` | `ui/player/ImageLoadingManager.kt` | Loads IMAGE/GIF for local, network, and cloud sources via Glide |
| `PlayerMediaLoaderManager` | `ui/player/helpers/PlayerMediaLoaderManager.kt` | Routes each `MediaType` to the correct renderer (image / video / audio / document) |
| `PlayerViewModel` | `ui/player/PlayerViewModel.kt` | `PlayerState` holds current file, index, slideshow flags |
| `FastMediaSorterApp` | `FastMediaSorterApp.kt` (466 lines) | Application class; SDK initialisation |
| `overflow_menu_player.xml` | `res/menu/overflow_menu_player.xml` | Overflow menu XML; currently ends with EPUB items at line 166 |
| `network_security_config.xml` | `res/xml/network_security_config.xml` | Already permits cleartext HTTP for `192.168.x`, `10.x`, `172.16.x`, and `127.0.0.1` — **no change needed** |

The current architecture has no output pathway beyond the local screen. There is no `CastContext` initialisation, no `SessionManagerListener`, no HTTP proxy, and no Cast menu entry in the overflow popup.

---

## 5. Proposed Architecture

### 5.1 CastOptionsProvider — SDK Entry Point

A minimal class implementing `OptionsProvider`, registered via `AndroidManifest.xml` `<meta-data>`. Returns `CastOptions` pointing at the Default Media Receiver.

```kotlin
// core/cast/CastOptionsProvider.kt
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
```

`CastContext.getSharedInstance(applicationContext)` is called once in `FastMediaSorterApp.onCreate()` wrapped in `try/catch` to degrade gracefully when Google Play Services are unavailable.

### 5.2 LocalCastProxyServer — HTTP Bridge

NanoHTTPD-based in-process HTTP server. **Key fix over prior draft**: binds to `0.0.0.0` (all interfaces); the URL returned by `castUrl()` uses the phone's actual Wi-Fi LAN IP (not `127.0.0.1`), which is the address the Chromecast device can reach over LAN. The `network_security_config.xml` already permits cleartext to all RFC 1918 ranges, so no XML change is needed.

```kotlin
// core/cast/LocalCastProxyServer.kt
class LocalCastProxyServer(private val context: Context, port: Int = 8765) : NanoHTTPD("0.0.0.0", port) {
    private var currentFile: File? = null

    fun serveFile(file: File) { currentFile = file }

    override fun serve(session: IHTTPSession): Response {
        val file = currentFile ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "no file"
        )
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
        return newChunkedResponse(Response.Status.OK, mime, file.inputStream())
    }

    fun castUrl(): String = "http://${getLanIp()}:$listeningPort/cast-media"

    private fun getLanIp(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getLanIpApi31()
        } else {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getLanIpApi31(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "127.0.0.1"
        val props = cm.getLinkProperties(network) ?: return "127.0.0.1"
        return props.linkAddresses
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address?.hostAddress ?: "127.0.0.1"
    }
}
```

Port collision: tries `8765`, then `8766`, `8767` (up to three attempts); logs chosen port via Timber.

### 5.3 CastMediaManager — Session Lifecycle and Media Sending

The primary manager class. Handles all four supported media types. The `sendCurrentMedia(file: MediaFile)` method is the single entry point called from `PlayerActivity.castCurrentMedia()`.

```kotlin
// ui/player/helpers/CastMediaManager.kt
class CastMediaManager(
    private val context: Context,
    private val lifecycleScope: CoroutineScope,
    private val onCastStateChanged: (isCasting: Boolean, deviceName: String?) -> Unit
) {
    val isCasting: Boolean get() = _isCasting
    private var _isCasting = false
    private val proxyServer = LocalCastProxyServer(context)
    private val sessionListener: SessionManagerListener<CastSession> = ...

    fun showCastDialog(activity: FragmentActivity) { /* open MediaRouteChooserDialogFragment */ }
    fun sendCurrentMedia(file: MediaFile) { /* resolve → download if needed → proxy → RemoteMediaClient.load() */ }
    fun release() { /* unregister listener, stop proxy, delete temp file */ }
}
```

**Resolution logic per type:**

| MediaType | Source | Action |
|-----------|--------|--------|
| IMAGE / GIF | LOCAL | Pass `File(file.path)` directly to proxy |
| IMAGE / GIF | NETWORK / CLOUD | Download via `InputStream` to `cacheDir/cast_current.[ext]` then pass to proxy |
| AUDIO | LOCAL | Pass `File(file.path)` directly to proxy |
| AUDIO | NETWORK / CLOUD | Download to `cacheDir/cast_current.[ext]` then pass to proxy |
| VIDEO | LOCAL | Pass `File(file.path)` directly to proxy |
| VIDEO | NETWORK / CLOUD, size ≤ 50 MB | Download to `cacheDir/cast_current.[ext]` then pass to proxy |
| VIDEO | NETWORK / CLOUD, size > 50 MB | Toast `cast_video_too_large`, skip cast |

`MediaInfo` `streamType`:
- IMAGE / GIF → `MediaInfo.STREAM_TYPE_NONE` (static load)
- AUDIO / VIDEO → `MediaInfo.STREAM_TYPE_BUFFERED`

### 5.4 CommandPanelController — Menu Wiring

Two additions to the existing overflow popup code in `CommandPanelController.kt`:

1. **Visibility** (inside `configureOverflowMenuItemsVisibility()`):
```kotlin
val canCast = (isImage || isVideo) &&
    isWifiConnected(context)  // helper using ConnectivityManager
popup.menu.findItem(R.id.menu_cast)?.isVisible = canCast
```
Where `isImage` already covers `IMAGE || GIF` and `isVideo` already covers `VIDEO || AUDIO` (per existing local variables at line 730–731).

2. **Click handler** (inside `setOnMenuItemClickListener` switch):
```kotlin
R.id.menu_cast -> callback.onCastClicked()
```

New method added to `CommandPanelCallback` interface:
```kotlin
fun onCastClicked()
```

### 5.5 PlayerViewModel Cast State Extension

Two fields added to `PlayerState`:

```kotlin
val isCasting: Boolean = false,
val castDeviceName: String? = null,
```

New one-shot event added to `PlayerEvent`:

```kotlin
data class CastStateChanged(val isCasting: Boolean, val deviceName: String?) : PlayerEvent()
```

### 5.6 New Classes / Files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `CastOptionsProvider.kt` | `core/cast/` | ≤ 40 |
| `LocalCastProxyServer.kt` | `core/cast/` | ≤ 200 |
| `CastMediaManager.kt` | `ui/player/helpers/` | ≤ 400 |

### 5.7 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | All Cast logic in `CastMediaManager`; `PlayerActivity` only calls `castCurrentMedia()` (1 line) and `release()` |
| Naming conventions | ✅ | `CastMediaManager` = `NounVerbManager`; `CastOptionsProvider` = `NounVerbProvider` |
| Data flow `UI → ViewModel → UseCase → Repository` | ✅ | Cast state changes emitted as `PlayerEvent.CastStateChanged` via `PlayerViewModel` |
| No `Log.d()` — Timber only | ✅ | All logging uses `Timber.d/w/e` |
| Room schema version incremented | N/A | No DB changes |
| `StateFlow` for state, `SharedFlow` for events | ✅ | Uses existing `PlayerViewModel` `PlayerState` + `PlayerEvent` pattern |
| Hilt DI: new bindings | N/A | `CastMediaManager` is instantiated manually in `PlayerActivity.onCreate()` (same pattern as `SlideshowController`, `PlayerGestureHelper`) — no Hilt module needed |

---

## 6. Data Flow

```
User taps "Cast to Chromecast" overflow item
  │
  ▼
CommandPanelController.popup.onMenuItemClick(R.id.menu_cast)
  │ callback.onCastClicked()
  ▼
PlayerCommandPanelCallbackImpl.onCastClicked()
  │ activity.castCurrentMedia()
  ▼
PlayerActivity.castCurrentMedia()
  │ castMediaManager.showCastDialog(this)
  ▼
CastMediaManager.showCastDialog()
  └─→ MediaRouteChooserDialogFragment.show(...)
        │ user selects device
        ▼
      SessionManagerListener.onSessionStarted()
        │ proxyServer.start()
        │ _isCasting = true
        │ onCastStateChanged(true, deviceName)
        ▼
      CastMediaManager.sendCurrentMedia(currentFile)
        │
        ├─ LOCAL file ──────────────────────────┐
        │                                        │
        ├─ NETWORK/CLOUD IMAGE/GIF/AUDIO         │
        │   download to cacheDir/cast_current.*  │
        │                                        │
        ├─ NETWORK/CLOUD VIDEO ≤50 MB            │
        │   download to cacheDir/cast_current.*  │
        │                                        │
        ├─ NETWORK/CLOUD VIDEO >50 MB            │
        │   Toast(cast_video_too_large) ← return │
        │                                        ▼
        └─ all castable ──────────────────────── LocalCastProxyServer.serveFile(file)
                                                  │
                                                  ▼
                                        RemoteMediaClient.load(
                                          MediaInfo(proxyServer.castUrl(), streamType, mimeType)
                                        )
                                                  │
                                                  ▼
                                        Chromecast receiver fetches
                                        http://[phone-LAN-IP]:8765/cast-media
                                        and renders IMAGE/GIF/AUDIO/VIDEO
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `app_v2/build.gradle.kts` | Add 3 dependencies: Cast SDK, MediaRouter, NanoHTTPD | ~unchanged |
| `app_v2/src/main/AndroidManifest.xml` | Add `<meta-data>` for `CastOptionsProvider`; verify `INTERNET` permission present | ~unchanged |
| `res/xml/network_security_config.xml` | **No change needed** — RFC 1918 cleartext already permitted | 44 lines |
| `res/menu/overflow_menu_player.xml` | Add `menu_cast` item after `menu_search_youtube_music` | 173 lines |
| `res/values/strings.xml` | Add 4 Cast strings (EN) | +4 strings |
| `res/values-ru/strings.xml` | Add 4 Cast strings (RU) | +4 strings |
| `res/values-uk/strings.xml` | Add 4 Cast strings (UK) | +4 strings |
| `FastMediaSorterApp.kt` (466 lines) | Add `CastContext.getSharedInstance(this)` in `onCreate()` | ~468 lines |
| `CommandPanelController.kt` (896 lines) | Add `onCastClicked()` to interface; add visibility + click handler (~15 lines) | ~911 lines |
| `PlayerCommandPanelCallbackImpl.kt` (223 lines) | Add `override fun onCastClicked()` delegating to `activity.castCurrentMedia()` | ~229 lines |
| `PlayerActivity.kt` (2400 lines) **backup required** | Instantiate `CastMediaManager` in `onCreate`; add `internal fun castCurrentMedia()`; call `castMediaManager.release()` in `onDestroy` (~12 lines total) | ~2412 lines |
| `PlayerViewModel.kt` **backup required** | Add `isCasting: Boolean` + `castDeviceName: String?` to `PlayerState`; add `CastStateChanged` event | ~unchanged |
| `proguard-rules.pro` | Add Cast SDK keep rules | +4 lines |

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| `CastContext.getSharedInstance()` crashes on devices without Google Play Services | Med | Wrap in `try/catch` in `FastMediaSorterApp.onCreate()`; set a flag `castAvailable = false`; hide Cast menu item when flag is false |
| `WifiManager.connectionInfo.ipAddress` returns `0` on Android 12+ if Wi-Fi permission is missing | Med | Use `ACCESS_WIFI_STATE` (already in manifest for SMB discovery); fall back to `ConnectivityManager` API 31+ branch |
| Port 8765 already in use | Low | Retry loop: 8765 → 8766 → 8767; log chosen port via Timber |
| Large audio/video download blocks UI before cast starts | Med | Run download in `lifecycleScope` coroutine on `Dispatchers.IO`; show progress Toast "Preparing cast…"; cancel coroutine on `release()` |
| Temp file `cast_current.*` not cleaned up on crash | Low | `CastMediaManager.release()` deletes temp file; also clean on `FastMediaSorterApp.onCreate()` via `cacheDir` sweep |
| Chromecast cannot reach phone's LAN IP (different Wi-Fi bands, AP isolation) | Med | Show Toast `cast_wifi_isolated` if first media load to receiver fails (`RemoteMediaClient.Listener.onStatusUpdated` → `MediaStatus.PLAYER_STATE_IDLE` + `IDLE_REASON_ERROR`) |
| Default Media Receiver shows unwanted UI chrome (title bar, controls) | Low | Acceptable for MVP; Custom Receiver deferred to future spec |
| `PlayerActivity` backup creation failure (file system full) | Low | Backup step in section 13 is a pre-condition; abort implementation if backup fails |

---

## 9. Testing Plan

### 9.1 Unit Tests

`CastMediaManager` download-vs-direct logic and the `LocalCastProxyServer.getLanIp()` branching are testable in isolation. Recommended test class: `CastMediaManagerTest` covering:
- `sendCurrentMedia()` with LOCAL `ResourceType` → no download triggered, `proxyServer.serveFile()` called with original path
- `sendCurrentMedia()` with NETWORK `ResourceType`, VIDEO > 50 MB → `serveFile()` NOT called, Toast event emitted
- `LocalCastProxyServerTest.getLanIp()` API 31 branch returns non-loopback address when `LinkProperties` has a valid IPv4

### 9.2 Manual Test Cases

1. **Happy path — image**: Open a local JPEG, tap overflow → "Cast to Chromecast", select a Chromecast. Image appears on TV.
2. **Happy path — GIF**: Open a local GIF, cast. Animated GIF plays on TV.
3. **Happy path — audio**: Open a local MP3, cast. Audio plays through Cast speaker; phone continues showing audio UI.
4. **Happy path — network image**: Open an SMB JPEG, cast. Image downloads to cache and appears on TV.
5. **Happy path — video**: Open a local MP4 ≤ 50 MB, cast. Video plays on TV.
6. **Large network video**: Open a network video > 50 MB, cast. Toast appears: "Video too large to cast (> 50 MB)".
7. **No Wi-Fi**: Disable Wi-Fi, open any image. Verify "Cast to Chromecast" menu item is hidden.
8. **No Google Play Services** (emulator without GMS): App starts without crash; Cast menu item hidden.
9. **Session disconnect**: While casting, disconnect via Cast dialog. Local playback continues unaffected; temp files deleted.
10. **Orientation change during cast**: Rotate device while casting. Cast session continues; no duplicate proxy instances.
11. **API 31 LAN IP** (Android 12 device): Cast works; LAN IP resolved via `ConnectivityManager`.

### 9.3 Maestro E2E

The Cast flow requires a physical Chromecast device and is not suitable for automated emulator testing. No Maestro tests needed for this spec.

---

## 10. Accessibility

The "Cast to Chromecast" overflow menu item uses a text label (`@string/cast_to_chromecast`) so TalkBack announces it by name. The standard `MediaRouteChooserDialogFragment` is fully accessibility-compliant (Google-supplied). No additional `contentDescription` or touch-target work is required. No colour-only affordances introduced.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): Under **Player** section — "Cast images, GIFs, audio, and local video to Chromecast devices via the player overflow menu"
- `docs/FEATURES_RU.md` (RU): "Трансляция изображений, GIF, аудио и локального видео на Chromecast через меню плеера"
- `docs/FEATURES_UK.md` (UK): "Трансляція зображень, GIF, аудіо та локального відео на Chromecast через меню плеєра"

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Overflow menu item instead of toolbar `MediaRouteButton`**
- **Decision:** Use `showAsAction="never"` overflow item (same as "Search in YouTube Music"); on click, call `MediaRouteChooserDialogFragment.show()` programmatically.
- **Alternatives considered:** Standard `MediaRouteButton` in toolbar with `showAsAction="always"` — this is Google's recommended pattern and provides the built-in spinning cast indicator.
- **Reason:** The player toolbar is already dense. Placing Cast in the overflow is consistent with the existing UX pattern for audio-specific actions and avoids toolbar reflow across all media types. The loss of the spinning indicator is acceptable for MVP.

**ADR-2: Phone LAN IP via `WifiManager` (< API 31) / `ConnectivityManager.getLinkProperties()` (≥ API 31)**
- **Decision:** Two-branch helper inside `LocalCastProxyServer.getLanIp()` guarded by `Build.VERSION.SDK_INT`.
- **Alternatives considered:** Iterating all `NetworkInterface`s on all API levels — works but fragile on devices with VPN interfaces.
- **Reason:** The `WifiManager.connectionInfo.ipAddress` path is battle-tested and sufficient for API 23–30. The `ConnectivityManager` path is the official replacement and directly targets the active network, avoiding VPN confusion.

**ADR-3: NanoHTTPD over Android's `HttpServer`**
- **Decision:** Add `com.nanohttpd:nanohttpd:2.3.1` dependency.
- **Alternatives considered:** Using `com.sun.net.httpserver.HttpServer` (built-in, no dependency) — but it is internal API, not part of the public Android SDK, and may be removed.
- **Reason:** NanoHTTPD is a stable, widely-used, MIT-licensed library with zero transitive dependencies. 2.3.1 is the last stable release and already used by other Cast-oriented projects.

**ADR-4: Skip cast for video > 50 MB (network/cloud sources)**
- **Decision:** Show Toast `cast_video_too_large` and abort for network/cloud video files larger than 50 MB.
- **Alternatives considered:** Stream video through the proxy on-the-fly (chunked NanoHTTPD response from the SMB/SFTP `InputStream`) — would work in theory but risks buffer underrun, connection drops, and OOM on large files.
- **Reason:** ExoPlayer reads network video adaptively in chunks; there is no single cached file to hand to the proxy. A 50 MB threshold allows short clips to be downloaded to `cacheDir` within a reasonable wait time (~10 s on a typical home LAN), while protecting memory and avoiding UX hangs for full-length videos.

---

## 13. Implementation Steps

1. **Backup** `PlayerActivity.kt` → `temp/PlayerActivity_backup_20260328.kt`
2. **Backup** `PlayerViewModel.kt` → `temp/PlayerViewModel_backup_20260328.kt`
3. Add three dependencies to [app_v2/build.gradle.kts](app_v2/build.gradle.kts):
   ```kotlin
   implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
   implementation("androidx.mediarouter:mediarouter:1.7.0")
   implementation("com.nanohttpd:nanohttpd:2.3.1")
   ```
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "build" "Add Cast SDK, MediaRouter, NanoHTTPD dependencies"`
4. Create [core/cast/CastOptionsProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt) implementing `OptionsProvider` (see §5.1).
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt" "CastOptionsProvider" "Add CastOptionsProvider for Cast SDK entry point"`
5. Create [core/cast/LocalCastProxyServer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt) extending `NanoHTTPD` bound to `0.0.0.0`; dual-branch `getLanIp()` for API < 31 and ≥ 31; port retry logic (see §5.2).
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt" "LocalCastProxyServer" "Add NanoHTTPD proxy bound to LAN IP for Cast receiver"`
6. Create [ui/player/helpers/CastMediaManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt) with `showCastDialog()`, `sendCurrentMedia()`, and `release()` (see §5.3).
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt" "CastMediaManager" "Add CastMediaManager for IMAGE/GIF/AUDIO/VIDEO Chromecast output"`
7. Add `<meta-data>` for `CastOptionsProvider` inside `<application>` in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
       android:value="com.sza.fastmediasorter.core.cast.CastOptionsProvider"/>
   ```
   Verify `<uses-permission android:name="android.permission.INTERNET"/>` is present.
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/AndroidManifest.xml" "AndroidManifest" "Register CastOptionsProvider meta-data"`
8. Add `menu_cast` item to [res/menu/overflow_menu_player.xml](app_v2/src/main/res/menu/overflow_menu_player.xml) directly after the `menu_search_youtube_music` item:
   ```xml
   <!-- Cast to Chromecast (IMAGE/GIF/AUDIO/VIDEO, Wi-Fi only) -->
   <item
       android:id="@+id/menu_cast"
       android:icon="@drawable/ic_cast"
       android:title="@string/cast_to_chromecast"
       app:showAsAction="never" />
   ```
   *(Add `ic_cast` vector drawable from Material Icons if not already present.)*
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/menu/overflow_menu_player.xml" "menu" "Add Cast to Chromecast overflow menu item"`
9. Add EN strings to [res/values/strings.xml](app_v2/src/main/res/values/strings.xml):
   - `cast_to_chromecast` = `"Cast to Chromecast"`
   - `cast_connected` = `"Casting to %s"`
   - `cast_disconnected` = `"Cast disconnected"`
   - `cast_video_too_large` = `"Video too large to cast (> 50 MB)"`
   Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add Cast string resources (EN)"`
10. Add RU strings to [res/values-ru/strings.xml](app_v2/src/main/res/values-ru/strings.xml):
    - `cast_to_chromecast` = `"Трансляция на Chromecast"`
    - `cast_connected` = `"Трансляция на %s"`
    - `cast_disconnected` = `"Трансляция завершена"`
    - `cast_video_too_large` = `"Видео слишком большое для трансляции (> 50 МБ)"`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings" "Add Cast string resources (RU)"`
11. Add UK strings to [res/values-uk/strings.xml](app_v2/src/main/res/values-uk/strings.xml):
    - `cast_to_chromecast` = `"Трансляція на Chromecast"`
    - `cast_connected` = `"Трансляція на %s"`
    - `cast_disconnected` = `"Трансляцію завершено"`
    - `cast_video_too_large` = `"Відео завелике для трансляції (> 50 МБ)"`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings" "Add Cast string resources (UK)"`
12. Add `CastContext.getSharedInstance(this)` in `FastMediaSorterApp.onCreate()`, wrapped in `try/catch(Exception)`. Store result as nullable field `castAvailable: Boolean`. Run:
    `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt" "FastMediaSorterApp" "Initialise Cast SDK in Application.onCreate()"`
13. Add `onCastClicked()` to `CommandPanelCallback` interface in [CommandPanelController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt):
    - Interface method: `fun onCastClicked()`
    - Visibility logic (in `configureOverflowMenuItemsVisibility()`): `popup.menu.findItem(R.id.menu_cast)?.isVisible = (isImage || isVideo) && isWifiConnected()` — add private `isWifiConnected()` helper using `ConnectivityManager.getNetworkCapabilities`.
    - Click handler (in `setOnMenuItemClickListener`): `R.id.menu_cast -> callback.onCastClicked()`
    - Tint line: `popup.menu.findItem(R.id.menu_cast)?.icon?.setTint(iconColor)`
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" "CommandPanelController" "Add Cast menu item visibility and click routing"`
14. Add `override fun onCastClicked()` in [PlayerCommandPanelCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt) delegating to `activity.castCurrentMedia()`.
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt" "PlayerCommandPanelCallbackImpl" "Implement onCastClicked delegation"`
15. Add `isCasting: Boolean = false` and `castDeviceName: String? = null` to `PlayerState` in `PlayerViewModel.kt`; add `data class CastStateChanged(val isCasting: Boolean, val deviceName: String?) : PlayerEvent()`.
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "PlayerViewModel" "Add Cast state fields to PlayerState and CastStateChanged event"`
16. In [PlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt):
    - Instantiate `CastMediaManager` lazily in `onCreate()` (after `setContentView`).
    - Add `internal fun castCurrentMedia()` → `castMediaManager.showCastDialog(this)` + `castMediaManager.sendCurrentMedia(currentFile)`.
    - Observe `PlayerEvent.CastStateChanged` in `collectEvents()` to update toolbar subtitle or show Toast.
    - Call `castMediaManager.release()` in `onDestroy()`.
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "PlayerActivity" "Wire CastMediaManager for Chromecast media output"`
17. Add Cast SDK ProGuard keep rules to `app_v2/proguard-rules.pro`:
    ```
    -keep class com.google.android.gms.cast.** { *; }
    -keep class com.google.android.gms.cast.framework.** { *; }
    -keep class androidx.mediarouter.** { *; }
    ```
    Run: `.\scripts\add_to_dev_log.ps1 "app_v2/proguard-rules.pro" "proguard" "Add Cast SDK and MediaRouter keep rules"`
18. Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` with new Cast bullet per §11.
19. Run `.\gradlew.bat assembleStandardDebug` — verify no compilation errors.
20. Smoke test on device: open image → overflow → Cast to Chromecast → device picker appears.
21. Run dev log for spec:
    `.\scripts\add_to_dev_log.ps1 "PLAN/spec_cast-chromecast.md" "spec" "Revise specification for X.2 — expand Cast to IMAGE/GIF/AUDIO/VIDEO; fix LAN IP bug"`

### Mandatory checklist

- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated
- [ ] Room DB migration: N/A (no schema changes)
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- **Large network/cloud video casting** — requires either a reverse-proxy streaming approach or a pre-download pipeline for files > 50 MB.
- **Custom Web Receiver app** — HTML/JS/CSS project; needed for custom UI, branding, or resume-position tracking.
- **Cast queue management** — sending a full playlist to the receiver for autonomous auto-advance.
- **Wear OS cast control** — pause/stop cast from watch.
- **Background cast** — keeping session alive when `PlayerActivity` is backgrounded requires a foreground `Service`.
- **Cast state persistence** — reconnecting to an in-progress session after app restart.
- **PDF / TEXT / EPUB casting** — Default Media Receiver cannot render document types; requires a Custom Web Receiver.
- **Toolbar `MediaRouteButton`** — replacing the overflow item with the standard Cast toolbar button (spinning indicator, mini-controller) once the feature is proven stable.
