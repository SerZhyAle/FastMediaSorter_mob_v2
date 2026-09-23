# Security posture - what this app can touch and what it can send

**Last reconciled:** 2026-09-22
**Contract:** canon `rules/SECURITY_AND_PRIVACY.md` section 7 (the permission and network-surface inventories).
**Checked by:** `scripts/quality/assert-security-posture.ps1`, run in the release scope (`scripts/quality/assert-release-scope-gates.ps1`, step 0.4 of `/spec-prerelease`).

This is the developer-facing source the three public forms of the promise render from: `docs/PRIVACY_POLICY.md`
(+ `-ru`, `-uk`), the in-app `perm_rationale_*` strings shown at request time, and the store-form sources under
`store_assets/`. A row here and a sentence there that disagree is a defect of this document, found before a store
submission rather than by its rejection.

Scope: `app_v2` (seven flavors: `standard`, `noLegal`, `lite`, `photos`, `legacy`, `vr`, `foss`) and `wear`
(`standard`, `noLegal`). The permission table covers `<uses-permission>` only - permissions a component *declares*
to bind a system service are listed separately in section 2, because the user is never asked for them as such.

---

## 1. Permission inventory

**Variants** names the build variants that actually merge the declaration; a flavor absent from the cell removes
the permission through `tools:node="remove"` or never mounts the source set that declares it. **Rationale key** is
the in-app string shown at the moment of the request; `-` means no runtime request exists for it (install-time or
system-screen permission) or the explanation lives only on the public page - section 4 lists the ones where that
gap is worth closing.

| Permission | Declared in | Variants | Consumers | Rationale key | Shown at request |
|---|---|---|---|---|---|
| `android.permission.ACCESS_COARSE_LOCATION` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | photo/video geotag; launcher compass, speed, altitude and map gadgets; Network Monitor GNSS and Wi-Fi sections; watch Tourist dashboard | `perm_rationale_location`, `perm_rationale_location_extended` | yes |
| `android.permission.ACCESS_FINE_LOCATION` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | same five consumers as the coarse permission (S2013) | `perm_rationale_location`, `perm_rationale_location_extended` | yes |
| `android.permission.ACCESS_LOCAL_NETWORK` | `app_v2/src/main` | standard, noLegal, photos, legacy, vr, foss | SMB, SFTP, FTP and DLNA resources and their discovery | `perm_rationale_access_local_network` | yes |
| `android.permission.ACCESS_NETWORK_STATE` | `app_v2/src/main` | all seven | connectivity checks before a network resource or a stream is opened | `-` | no |
| `android.permission.ACCESS_WIFI_STATE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | Network Monitor Wi-Fi section; companion discovery | `-` | no |
| `android.permission.ACTIVITY_RECOGNITION` | `app_v2/src/noLegal`, `wear/src/noLegal` | noLegal, `wear:noLegal` | launcher steps gadget (S1614 moved it off the store flavors) | `perm_rationale_activity_recognition` | yes |
| `android.permission.BLUETOOTH` | `app_v2/src/networkMonitor`, `wear/src/noLegal` | standard, noLegal, `wear:noLegal` | Network Monitor Bluetooth section (API <= 30) | `-` | no |
| `android.permission.BLUETOOTH_ADMIN` | `app_v2/src/networkMonitor`, `wear/src/noLegal` | standard, noLegal, `wear:noLegal` | Network Monitor radio controls (API <= 30) | `-` | no |
| `android.permission.BLUETOOTH_CONNECT` | `app_v2/src/networkMonitor`, `wear/src/noLegal` | standard, noLegal, `wear:noLegal` | Network Monitor Bluetooth section (API 31+) | `-` | no |
| `android.permission.BODY_SENSORS` | `wear/src/noLegal` | `wear:noLegal` | watch heart-rate readout (API <= 35) | `-` | no |
| `android.permission.CAMERA` | `app_v2/src/main`, `app_v2/src/broadcastSource` | all seven | in-app photo and video capture, OCR and translation, QR pairing, camera widgets, live video broadcast | `-` | no |
| `android.permission.CHANGE_NETWORK_STATE` | `wear/src/noLegal` | `wear:noLegal` | wide-band transport request while the watch streams | `-` | no |
| `android.permission.CHANGE_WIFI_MULTICAST_STATE` | `app_v2/src/main` | all seven | mDNS discovery of `_sftp-fms._tcp` companion servers | `-` | no |
| `android.permission.CHANGE_WIFI_STATE` | `app_v2/src/networkMonitor` | standard, noLegal | Network Monitor radio toggles | `-` | no |
| `android.permission.EXPAND_STATUS_BAR` | `app_v2/src/main` | all seven | launcher shade-panel control | `-` | no |
| `android.permission.FOREGROUND_SERVICE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | every foreground service below | `-` | no |
| `android.permission.FOREGROUND_SERVICE_CAMERA` | `app_v2/src/broadcastVideoBackground` | noLegal | video broadcast that survives leaving the screen (S3154 keeps it off the store flavors) | `-` | no |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | `app_v2/src/main` | all seven, debug build type only | scheduled file operations | `-` | no |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | audio playback service | `-` | no |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION` | `app_v2/src/screenCapture` | noLegal, standard when the capture suite is built | screen capture and screen recording | `-` | no |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | quick voice recorder widget; watch voice recorder | `-` | no |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | `app_v2/src/noLegal`, `app_v2/src/standardScreenCapture` | noLegal, standard when the edge overlay is built | edge-gesture overlay host | `-` | no |
| `android.permission.health.READ_HEART_RATE` | `wear/src/noLegal` | `wear:noLegal` | watch heart-rate readout (Health permission, API 36+) | `-` | no |
| `android.permission.INTERNET` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | every network surface of section 3 | `-` | no |
| `android.permission.MANAGE_EXTERNAL_STORAGE` | `app_v2/src/noLegal` | noLegal | any-folder browsing, including DCIM and `Android/media` | `perm_rationale_manage_external_storage` | yes, on a system screen |
| `android.permission.MANAGE_MEDIA` | `app_v2/src/main` | all seven | media moves and deletions without a per-operation system confirmation | `-` | yes, on a system screen |
| `android.permission.NEARBY_WIFI_DEVICES` | `app_v2/src/networkMonitor`, `wear/src/noLegal` | standard, noLegal, `wear:noLegal` | Wi-Fi scanning in the Monitor; watch stream transport | `-` | no |
| `android.permission.POST_NOTIFICATIONS` | `app_v2/src/main`, `app_v2/src/screenCapture`, `wear/src/noLegal` | all seven, `wear:noLegal` | playback, transfer, recording and capture progress notifications with their stop controls | `-` | yes |
| `android.permission.READ_CONTACTS` | `app_v2/src/main` | standard, noLegal | pinned-contact name and photo on the launcher | `perm_rationale_read_contacts` | yes |
| `android.permission.READ_EXTERNAL_STORAGE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | media library scan and browse (API <= 32) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_MEDIA_AUDIO` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | the same access split by media type (API 33+) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_MEDIA_IMAGES` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | the same access split by media type (API 33+) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_MEDIA_VIDEO` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | the same access split by media type (API 33+) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_PHONE_STATE` | `app_v2/src/launcherEnabled` | standard, noLegal | SIM signal indicator in the launcher status area | `-` | yes |
| `android.permission.RECEIVE_BOOT_COMPLETED` | `app_v2/src/main` | all seven, debug build type only | scheduled operations after a reboot | `-` | no |
| `android.permission.RECORD_AUDIO` | `app_v2/src/main`, `app_v2/src/screenCapture`, `wear/src/noLegal` | standard, noLegal, lite, legacy, vr, foss, `wear:noLegal` | voice notes, screen-recording sound, live audio broadcast, watch voice recorder | `perm_rationale_record_audio` | yes |
| `android.permission.REQUEST_DELETE_PACKAGES` | `app_v2/src/launcherEnabled` | standard, noLegal | hands an app picked in the launcher menu to the system uninstall screen | `-` | no |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | `app_v2/src/main` | all seven, debug build type only | keeps scheduled operations on time | `perm_rationale_battery_optimization` | yes, on a system screen |
| `android.permission.REQUEST_INSTALL_PACKAGES` | `app_v2/src/noLegal` | noLegal | installing an APK opened from a browsed folder | `-` | yes, on a system screen |
| `android.permission.SYSTEM_ALERT_WINDOW` | `app_v2/src/noLegal`, `app_v2/src/standardScreenCapture` | noLegal, standard when the edge overlay is built | edge-gesture strip drawn over other apps | `-` | yes, on a system screen |
| `android.permission.USE_FULL_SCREEN_INTENT` | `wear/src/noLegal` | `wear:noLegal` | watch listen-request prompt | `-` | no |
| `android.permission.VIBRATE` | `app_v2/src/main`, `wear/src/main` | all seven, `wear:standard`, `wear:noLegal` | haptic feedback on pairing and beam success | `-` | no |
| `android.permission.WAKE_LOCK` | `app_v2/src/main`, `wear/src/main` | all seven, `wear:standard`, `wear:noLegal` | background work and playback holding the CPU awake | `-` | no |
| `android.permission.WRITE_EXTERNAL_STORAGE` | `app_v2/src/main` | all seven | moving and deleting sorted files (API <= 28) | `perm_rationale_read_external_storage` | yes |
| `com.oculus.permission.HAND_TRACKING` | `app_v2/src/vr` | vr, noLegal | hand tracking in the immersive browse surfaces | `-` | no |

**One permission, several consumers.** The location pair is the standing example and the reason the contract
states it: five independent consumers share it, and narrowing the manifest per flavor was tried, broke three of
them and was reverted (`dev/REFUTED_APPROACHES.md`, S2013). The repair is the wording of `perm_rationale_location_extended`,
never a narrower manifest.

---

## 2. Permissions a component declares, not requests

These are never asked for as permissions; the user turns the component on from a system screen, or the system
prompts per use.

- `android.permission.BIND_ACCESSIBILITY_SERVICE` - `ScreenshotAccessibilityService`, `noLegal` only: the
  dialog-free screenshot path on API 30+.
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` - `MediaSessionAccessService`, `standard` and
  `noLegal`: the Now Playing gadget and the launcher's notification counters. Both features are off until the
  user turns them on; no notification content is read or stored.
- `android.permission.BIND_TILE_PROVIDER` - the watch tile services.
- Screen-capture consent: `MediaProjection` asks the system every time a recording starts, so it cannot be
  granted ahead of time.

---

## 3. Network-surface inventory

Every surface that opens a listening port, initiates an outbound connection, or hands a file outward.

| Surface | Where | Variants | On by default | Turned on by | Lifetime | What leaves, to where |
|---|---|---|---|---|---|---|
| Live audio broadcast HTTP server (`/live-audio.aac`, NanoHTTPD bound to `0.0.0.0`) | `app_v2/src/broadcastSource/.../broadcast/BroadcastHttpServer.kt` | standard, noLegal, legacy | no | the user starting a broadcast from the broadcast screen, its tile or its widget | the broadcast session; stopped with it | live AAC audio to any client on the local network that opens the announced URL |
| Camera and microphone RTSP server (RootEncoder `rtsp-server`) | `app_v2/src/broadcastSource/.../broadcast/VideoBroadcastService.kt` | standard, legacy (on screen only); noLegal (also in background) | no | the user starting a video broadcast, or accepting a watch camera request | the broadcast session | live camera and microphone stream to local-network clients |
| Companion discovery (mDNS/NSD client) | `app_v2/src/main/.../data/remote/sftp/CompanionMdnsDiscovery.kt` | all seven | no | opening network discovery while adding a resource | the discovery pass | service queries on the local network; no user content |
| SMB / SFTP / FTP clients | `data/remote/{smb,sftp,ftp}` | all but lite | no | adding a network resource | per browse or transfer session | file listings and file content to and from the hosts the user configured |
| Cloud storage clients (Google Drive, Dropbox, OneDrive) | `ui/cloudfolders/*`, OAuth receivers | all but foss | no | signing in while adding a cloud resource | the OAuth session and its stored token | file metadata and content over HTTPS to the chosen provider |
| Google Cast | `core/cast` + `src/castEnabled` | standard, noLegal, lite, photos, legacy | discovery on, casting off | the user picking a cast device | the cast session | the media being played, to the chosen device on the local network |
| Wear Data Layer | `src/wearGms/PhoneWearListenerService`, `wear/.../WatchWearListenerService` | standard, noLegal + their watch counterparts | yes, once a watch is paired | pairing a watch | the pairing | resource metadata, favourites, playback state and transferred files, between phone and paired watch only |
| Stream playback (HLS, DASH, RTSP, HTTP) | Media3 ExoPlayer, NewPipe extractor | every flavor with stream support | no | the user opening a stream | the playback session | requests to the stream URL the user supplied |
| Remote image loading | Glide over OkHttp | all seven | no | a remote thumbnail or avatar being shown | per request | image requests to cloud and map endpoints |
| Map gadgets | launcher map tiles | standard, noLegal | no | the user adding a map gadget | while the tile is on screen | the area coordinates, to an online map-tile service, and a reverse-geocode lookup |
| External IP check | Network Monitor | standard, noLegal | no | the user asking for it | the request | the request itself, to a third-party IP-echo service that sees the address; the result is shown, not stored |

**Born off, dies with its session.** Both servers above ship disabled, start only on a user action, and stop with
the session that started them; neither publishes its loopback address as an external one - the wire-level form of
that last rule for the broadcast family is the `LIVE-BROADCAST` contract in the cross-project catalog. A new
listening surface in this repository is held to the same four answers before it ships.

**Cleartext.** `app_v2/src/main/res/xml/network_security_config.xml` permits cleartext app-wide, deliberately, so
that `http://` internet-radio sources play (S0565). It is a property of what the user chooses to open, not of
anything the app sends on its own.

---

## 4. The "no telemetry" claim, and where it is proven

No analytics, crash-reporting or advertising SDK is linked into either module: `gradle/libs.versions.toml`,
`app_v2/build.gradle.kts` and `wear/build.gradle.kts` carry no Firebase, Crashlytics, App Center, Sentry, Adjust,
AppsFlyer, Amplitude, Mixpanel or ads artifact. ML Kit's transitive Firebase data-transport service is actively
removed in `app_v2/src/main/AndroidManifest.xml`, not consumed. The Google Play services artifacts present are
sign-in and Block Store (Drive picker), Cast (Chromecast) and Wearable (the watch Data Layer) - each the transport
of a feature the user starts.

The claim is checked against that dependency set by `assert-security-posture.ps1`, not against the wording of the
privacy page.

**Known gaps in the in-app half of the promise** - each is a permission the public page explains and the app does
not, at the moment it asks:

- `CAMERA` and `READ_PHONE_STATE` are requested at runtime with no `perm_rationale_*` string.
- `POST_NOTIFICATIONS` is requested at runtime with no `perm_rationale_*` string.
- `MANAGE_MEDIA`, `REQUEST_INSTALL_PACKAGES` and `SYSTEM_ALERT_WINDOW` are granted on a system screen with no
  in-app sentence preceding it.

They are recorded rather than fixed here: writing a user-visible string is a change to the product's text and
belongs to its own ticket, and an inventory that hid them would be the thing the contract exists to prevent.

---

## 5. How this document is kept true

- A permission added to any manifest without a row here fails the gate, and so does a row naming a permission no
  manifest declares.
- A row naming a `perm_rationale_*` key absent from `app_v2/src/main/res/values/strings.xml` fails the gate.
- An analytics, crash-reporting or advertising dependency entering the build fails the gate while section 4
  claims none.
- The consumers, the lifetimes and the "what leaves" column are authored: no mechanism can derive which features
  share a permission, which is why the contract asks for them in writing.
