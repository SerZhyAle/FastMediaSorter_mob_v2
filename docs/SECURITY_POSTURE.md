<sub class="doc-stamp">26.10.25 13:32</sub>

# Security posture - what this app can touch and what it can send

**Last reconciled:** 2026-10-15
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
the in-app string shown at the moment of the request; `-` means the user is never asked for it (install-time
permission), and is legal only where "Shown at request" says `no`. Section 4 lists what is still missing around
the request itself.

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
| `android.permission.CAMERA` | `app_v2/src/main`, `app_v2/src/broadcastSource` | all seven | in-app photo and video capture, OCR and translation, QR pairing, camera widgets, live video broadcast | `perm_rationale_camera` | yes |
| `android.permission.CHANGE_NETWORK_STATE` | `wear/src/noLegal` | `wear:noLegal` | wide-band transport request while the watch streams | `-` | no |
| `android.permission.CHANGE_WIFI_MULTICAST_STATE` | `app_v2/src/main` | all seven | mDNS discovery of `_sftp-fms._tcp` companion servers | `-` | no |
| `android.permission.CHANGE_WIFI_STATE` | `app_v2/src/networkMonitor` | standard, noLegal | Network Monitor radio toggles | `-` | no |
| `android.permission.EXPAND_STATUS_BAR` | `app_v2/src/main` | all seven | launcher shade-panel control | `-` | no |
| `android.permission.FOREGROUND_SERVICE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | every foreground service below | `-` | no |
| `android.permission.FOREGROUND_SERVICE_CAMERA` | `app_v2/src/broadcastVideoBackground` | standard, noLegal | video broadcast that survives leaving the screen (S4235 enables for standard; legacy retains foreground-only) | `-` | no |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | `app_v2/src/main` | all seven in debug builds; in a release noLegal, and standard unless built with `fms.dataSyncFgsStandard=off` (S4271) | background file transfers and scheduled file operations; the phone's folder-sharing service (S3041, lifetime S4225) | `-` | no |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | audio playback service | `-` | no |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION` | `app_v2/src/screenCapture`, `app_v2/src/broadcastSource` | noLegal, standard, legacy | screen capture and screen recording (standard: when the capture suite is built); the phone-screen broadcast (S4233) | `-` | no |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | quick voice recorder widget; watch voice recorder | `-` | no |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | `app_v2/src/noLegal`, `app_v2/src/standardScreenCapture` | noLegal, standard when the edge overlay is built | edge-gesture overlay host | `-` | no |
| `android.permission.health.READ_HEART_RATE` | `wear/src/noLegal` | `wear:noLegal` | watch heart-rate readout (Health permission, API 36+) | `-` | no |
| `android.permission.INTERNET` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | every network surface of section 3 | `-` | no |
| `android.permission.MANAGE_EXTERNAL_STORAGE` | `app_v2/src/noLegal` | noLegal | any-folder browsing, including DCIM and `Android/media` | `perm_rationale_manage_external_storage` | yes, on a system screen |
| `android.permission.MANAGE_MEDIA` | `app_v2/src/main` | all seven | media moves and deletions without a per-operation system confirmation | `perm_rationale_manage_media` | yes, on a system screen |
| `android.permission.NEARBY_WIFI_DEVICES` | `app_v2/src/networkMonitor`, `wear/src/noLegal` | standard, noLegal, `wear:noLegal` | Wi-Fi scanning in the Monitor; watch stream transport | `-` | no |
| `android.permission.POST_NOTIFICATIONS` | `app_v2/src/main`, `app_v2/src/screenCapture`, `wear/src/main` | all seven, both watch flavors (`wear:standard`, `wear:noLegal`) | playback, transfer, recording and capture progress notifications with their stop controls; on the watch also the running stopwatch's ongoing activity (S3555) | `perm_rationale_post_notifications` | yes |
| `android.permission.READ_CONTACTS` | `app_v2/src/main`, `app_v2/src/standardNoContacts` (removal overlay) | noLegal, standard unless the Play rollback switch `fms.readContacts=off` strips it (S4030) | pinned-contact name and photo on the launcher and the chat lookup of a new messenger shortcut; declared to Google Play in `store_assets/PLAY_CONTACTS_DECLARATION.md` | `perm_rationale_read_contacts` | yes |
| `android.permission.READ_EXTERNAL_STORAGE` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | media library scan and browse (API <= 32) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_MEDIA_AUDIO` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | the same access split by media type (API 33+) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_MEDIA_IMAGES` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | the same access split by media type (API 33+) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_MEDIA_VIDEO` | `app_v2/src/main`, `wear/src/noLegal` | all seven, `wear:noLegal` | the same access split by media type (API 33+) | `perm_rationale_read_external_storage` | yes |
| `android.permission.READ_PHONE_STATE` | `app_v2/src/launcherEnabled` | standard, noLegal | SIM signal indicator in the launcher status area; per-SIM section of the Network Monitor | `perm_rationale_read_phone_state` | yes |
| `android.permission.RECEIVE_BOOT_COMPLETED` | `app_v2/src/main` | all seven, debug build type only | scheduled operations after a reboot | `-` | no |
| `android.permission.RECORD_AUDIO` | `app_v2/src/main`, `app_v2/src/screenCapture`, `wear/src/noLegal` | standard, noLegal, lite, legacy, vr, foss, `wear:noLegal` | voice notes, screen-recording sound, live audio broadcast, watch voice recorder | `perm_rationale_record_audio` | yes |
| `android.permission.REQUEST_DELETE_PACKAGES` | `app_v2/src/launcherEnabled` | standard, noLegal | hands an app picked in the launcher menu to the system uninstall screen | `-` | no |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | `app_v2/src/main` | all seven, debug build type only | keeps scheduled operations on time | `perm_rationale_battery_optimization` | yes, on a system screen |
| `android.permission.REQUEST_INSTALL_PACKAGES` | `app_v2/src/noLegal` | noLegal | installing an APK opened from a browsed folder | `perm_rationale_request_install_packages` | yes, on a system screen |
| `android.permission.SYSTEM_ALERT_WINDOW` | `app_v2/src/noLegal`, `app_v2/src/standardScreenCapture` | noLegal, standard when the edge overlay is built | edge-gesture strip drawn over other apps | `perm_rationale_system_alert_window` | yes, on a system screen |
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
- Screen-capture consent: `MediaProjection` asks the system every time a recording starts or the phone's screen
  is broadcast (S4233), so it cannot be granted ahead of time.

---

## 3. Network-surface inventory

Every surface that opens a listening port, initiates an outbound connection, or hands a file outward.

| Surface | Where | Variants | On by default | Turned on by | Lifetime | What leaves, to where |
|---|---|---|---|---|---|---|
| Live audio broadcast HTTP server (`/live-audio.aac`, NanoHTTPD bound to `0.0.0.0`) | `app_v2/src/broadcastSource/.../broadcast/BroadcastHttpServer.kt` | standard, noLegal, legacy | no | the user starting a broadcast from the broadcast screen, its tile or its widget | the broadcast session; stopped with it | live AAC audio to any client on the local network that opens the announced URL |
| Camera and microphone RTSP server (RootEncoder `rtsp-server`) | `app_v2/src/broadcastSource/.../broadcast/VideoBroadcastService.kt` | standard, noLegal (also in background, camera foreground-service type, S4235); legacy (on screen only) | no | the user starting a video broadcast, or accepting a watch camera request | the broadcast session; ends with Stop in its notification or in the app | live camera and microphone stream to local-network clients, or through the exchange server sealed (items DL and DM of `DEVICE-EXCHANGE`) |
| Phone-screen broadcast (MediaProjection) | `app_v2/src/broadcastSource/.../broadcast/BroadcastScreenService.kt` | standard, noLegal, legacy | no | the user starting a screen broadcast and accepting the system's projection consent, asked for every session (S4233) | the broadcast session; ends with Stop, or when the user ends the sharing from the system's indicator | the live screen stream, to the same receivers as any broadcast |
| Ready-file broadcast | `app_v2/src/broadcastSource/.../broadcast/BroadcastFileAudioService.kt`, `BroadcastFileVideoService.kt` | standard, noLegal, legacy | no | the user picking a stored file to broadcast (S4234) | the broadcast session | that one file's stream to the same receivers; no camera or microphone is opened |
| Companion discovery (mDNS/NSD client) | `app_v2/src/main/.../data/remote/sftp/CompanionMdnsDiscovery.kt` | all seven | no | opening network discovery while adding a resource | the discovery pass | service queries on the local network; no user content |
| SMB / SFTP / FTP clients | `data/remote/{smb,sftp,ftp}` | all but lite | no | adding a network resource | per browse or transfer session | file listings and file content to and from the hosts the user configured |
| Cloud storage clients (Google Drive, Dropbox, OneDrive) | `ui/cloudfolders/*`, OAuth receivers | standard, noLegal, photos, legacy, vr, xr (`SUPPORT_CLOUD` in `docs/FLAVOR_MATRIX.md`) | no | signing in while adding a cloud resource | the OAuth session and its stored token | file metadata and content over HTTPS to the chosen provider |
| Google Cast | `core/cast` + `src/castEnabled` | standard, noLegal, lite, photos, legacy | discovery on, casting off | the user picking a cast device | the cast session | the media being played, to the chosen device on the local network |
| Wear Data Layer | `src/wearGms/PhoneWearListenerService`, `wear/.../WatchWearListenerService` | standard, noLegal + their watch counterparts | yes, once a watch is paired | pairing a watch | the pairing | resource metadata, favourites, playback state and transferred files, between phone and paired watch only |
| Stream playback (HLS, DASH, RTSP, HTTP) | Media3 ExoPlayer, NewPipe extractor | every flavor with stream support | no | the user opening a stream | the playback session | requests to the stream URL the user supplied |
| Remote image loading | Glide over OkHttp | all seven | no | a remote thumbnail or avatar being shown | per request | image requests to cloud and map endpoints |
| Map gadgets | launcher map tiles | standard, noLegal | no | the user adding a map gadget | while the tile is on screen | the area coordinates, to an online map-tile service, and a reverse-geocode lookup |
| External IP check | Network Monitor | standard, noLegal | no | the user asking for it | the request | the request itself, to a third-party IP-echo service that sees the address; the result is shown, not stored |
| Exchange server client (account, device directory, resource and broadcast records) | `app_v2/src/main/.../data/remote/exchange/DeviceExchangeAccountClient.kt`; watch `wear/src/main/.../data/exchange/WearExchangeAccountClient.kt` | every flavor; both watch flavors | no | the user adding a server from a connection string or QR, or the watch taking it from the paired phone | while the entry is signed in; closed by "Sign out on this device" or "Delete my account" | login, password at sign-in only, device names, roles and public keys, folder and broadcast names and presence, to the one server the user entered, over TLS checked against the certificate fingerprint saved at the first connection |
| Exchange tunnels and relay (folders of another own device, broadcast relay, cast) | `app_v2/src/main/.../data/remote/exchange/`, `.../data/remote/sftp/` | every flavor; both watch flavors | no | the user opening a folder of another own device, or a broadcast, through the server | the tunnel or the broadcast | SFTP inside SSH end to end for folders; broadcast media and tunnel sealed (items DI, DL, DM of `DEVICE-EXCHANGE`); the server joins two outgoing connections and reads no content |
| Clipboard on the exchange server | `app_v2/src/main/.../data/remote/exchange/DeviceExchangeClipboardClient.kt` | every flavor; both watch flavors | no | the user's "send clipboard to server" or "get clipboard from server" action | one transfer; the value lives in the server's memory only, replaced by the next one | one sealed value - text, image or one file, up to the server's limit - to every non-guest account entry the device is signed in to; no preview, history or background read |
| Google Drive channel between own devices (packet queue, records, clipboard via GD) | `app_v2/src/main/.../domain/model/transfer/CrossDevicePacketCodec.kt`, `DriveChannelMessages.kt` | standard, noLegal, photos, legacy, vr, xr (`SUPPORT_CLOUD`) | no | the user switching the Drive channel on after signing in to Google | while on: a listing every 10 s with a waiting screen, every 60 s in the foreground, every 15 min in the background; files expire after 7 days, the clipboard value is replaced by the next | files, requests and replies, the clipboard value and the directory records, in the hidden `drive.appdata` folder of the user's own Google account, over HTTPS; not sealed (item DO); a broadcast is announced there and never streamed |
| Watch microphone broadcast and "Send to.." | `wear/src/main/.../service/`, `.../data/exchange/` | both watch flavors | no | the user's tap on the watch | the broadcast or the one transfer | the watch microphone's sound, and files and recordings handed to "Send to..", over the watch's own Wi-Fi or mobile connection to the same places as the phone; Bluetooth carries only the start and the secrets |

**Born off, dies with its session.** Both servers above ship disabled, start only on a user action, and stop with
the session that started them; neither publishes its loopback address as an external one - the wire-level form of
that last rule for the broadcast family is the `LIVE-BROADCAST` contract in the cross-project catalog. A new
listening surface in this repository is held to the same four answers before it ships.

**Cleartext.** `app_v2/src/main/res/xml/network_security_config.xml` permits cleartext app-wide, deliberately, so
that `http://` internet-radio sources play (S0565). It is a property of what the user chooses to open, not of
anything the app sends on its own.

### 3.1 The exchange server: what is sealed, what it sees, what it keeps (S4241)

The exchange server is a program the person runs or is invited to, never the developer's: no server of the
developer exists and none is offered to the public (S4203 decision 4). Contract: `DEVICE-EXCHANGE` 0.29,
items DE (the honest text), DO (Drive not sealed) and DQ (what the server keeps and sees).

- **Sealed on the device, opened only on the account's own devices or by the people given access:** the
  access record of a folder, a grant's access and credentials, a broadcast's credentials and keys, the media of
  a relayed broadcast, the bytes of a broadcast tunnel, and every clipboard value with its kind, type and name.
  A folder's files cross a tunnel inside SSH, end to end.
- **Seen by the server, because without it nothing connects:** the account's login and kind, each device's
  name, product, platform, roles, capabilities and public key, presence and last-seen time, resource names,
  roots and host-key fingerprints, broadcast titles and modes, grants and the guest logins they name, who
  connects to whom and when, client addresses, and the size and timing of every tunnel, relay chunk and
  clipboard value.
- **Kept on disk:** accounts with salted password hashes, enrolled devices and their public keys, resource and
  broadcast records, the log within its operator's retention setting. **In memory only:** sealed access
  records, sealed clipboard values (the newest only, no expiry, lost on restart), live tunnels and relay
  buffers, pairing codes. **Never:** a password, a token, an invitation or content in the log.
- **Deleting an account** erases the account, its devices, records, tokens, clipboard, pairing codes and
  grants at once; the log is not rewritten. In the app: My devices, server card menu, "Delete my account".
  Without the app, and for a server that is not the person's own: the operator deletes it, and the portal page
  "How to delete an account" says both ways.
- **The Google Drive path is not sealed.** The data lives in the person's own Google account in the hidden
  `drive.appdata` folder, never addressed to another account, and no exchange server takes part; the
  privacy page and the Data safety source say it in the contract's words.
- **Where it is rendered:** `docs/PRIVACY_POLICY.md` (+ `-ru`, `-uk`), "Exchange Between Your Devices";
  `store_assets/PLAY_DATA_SAFETY_EXCHANGE.md`; `store_assets/PLAY_FGS_DECLARATIONS.md`.

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

- None open. A permission granted "on a system screen" gets its `perm_rationale_*` paragraph in an in-app dialog
  just before that screen opens, from the Permissions settings screen and the welcome page alike, one row or a
  whole "Grant all" run.

A row whose "Shown at request" cell says `yes` must name its `perm_rationale_*` key; the gate refuses one that does
not, so a new gap cannot enter this table silently.

---

## 5. How this document is kept true

- A permission added to any manifest without a row here fails the gate, and so does a row naming a permission no
  manifest declares.
- A row naming a `perm_rationale_*` key absent from `app_v2/src/main/res/values/strings.xml` fails the gate.
- While `READ_CONTACTS` is declared, the gate also requires `store_assets/PLAY_CONTACTS_DECLARATION.md` and a valid
  state in the contacts block of `docs/PLAY_PUBLISHING_STATE.md`, shows that state on its last line, and refuses
  `rejected` while `fms.readContacts` is not `off`.
- An analytics, crash-reporting or advertising dependency entering the build fails the gate while section 4
  claims none.
- The consumers, the lifetimes and the "what leaves" column are authored: no mechanism can derive which features
  share a permission, which is why the contract asks for them in writing.
