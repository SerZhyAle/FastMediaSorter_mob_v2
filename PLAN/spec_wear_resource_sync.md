# Specification: III.6 — Wear OS Resource Config Export/Import

**Status:** Draft
**Date:** 2026-04-14
**Tier:** 4
**Roadmap entry:** Send resource config to watch via Wearable Data Layer

---

## 1. Problem Statement

The Wear OS companion app has its own isolated resource configuration: network sources (SMB/SFTP/FTP) are entered manually on the watch via a tiny keyboard in `AddSmbScreen`. This is painful — the watch has no physical keyboard, on-screen text entry on a round display is awkward, and users who have already configured 5+ network sources on their phone must re-enter all credentials from scratch on the watch.

The phone and watch apps are already signed with the same certificate and share the same `applicationId` prefix (`com.sza.fastmediasorter`), so they satisfy the precondition for using the Wearable Data Layer. `play-services-wearable:18.1.0` is present in `wear/build.gradle.kts:120` but **not** in `app_v2/build.gradle.kts` — the phone side has zero Wearable Data Layer integration today.

There is also no "Wear Companion" section in the phone Settings, and no "Sync from Phone" button on the watch's `NetworkSourcesScreen`.

---

## 2. Goals

1. **Phone → Watch push**: User selects "Push Resources to Watch" in phone Settings and all eligible network resources are sent to the watch in one tap.
2. **Watch-initiated pull**: From the watch's Network Sources screen, user can request a full sync from the phone with a single chip.
3. **Credential transfer**: Credentials stored in `NetworkCredentialsRepository` are resolved on the phone, decrypted, and re-encrypted via `EncryptedSharedPreferences` on the watch — plaintext never persists in the Data Layer.
4. **Merge, not replace**: Synced sources are merged into existing watch sources by `(type, server, port, shareName)` key — existing watch-only sources are preserved.
5. **User-initiated only**: No background sync, no WorkManager, no periodic push — battery-preserving design.
6. **Premium UX ("The Beam")**: Animations, haptic feedback, and audio cues make the transfer feel "magical".
7. **SyncResultScreen on watch**: After sync completes, a dedicated screen shows import stats and a "Browse Now" action.

**Non-goals:**
- Watch → Phone sync (watch is a consumer, not a source of truth)
- Cloud resource sync (OAuth tokens are phone-device-bound)
- Local resource sync (phone LOCAL paths are meaningless on watch)
- Automatic conflict resolution (last-write wins per merge key)
- Real-time connection-status sync

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Primary target. Full network source set (SMB/FTP/SFTP). Wear companion pairs with `com.sza.fastmediasorter`. |
| `lite`     | ✅ | Lite has SMB/FTP/SFTP browsing; `SUPPORT_CLOUD=false` means zero cloud leakage. Same signing cert — Data Layer works. |
| `photos`   | ❌ | Photos flavor has no SMB/FTP/SFTP resources. Cloud sources are filtered out by design. Feature would send zero resources. |
| `legacy`   | ❌ | Legacy targets API 23–25. Android 6/7 devices cannot be Wear OS 2.0+ companions (Wear OS 2.0 requires API 25+ phone *and* Wear-capable BT stack). Data Layer pairing is unsupported at API 23–24. |

No new `BuildConfig` flag is required — the feature is gated by the flavor inclusion above. If future code needs compile-time gating, declare `buildConfigField("boolean", "FEATURE_WEAR_SYNC", "true/false")` in each flavor block in `app_v2/build.gradle.kts`.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 26+ (standard / lite minSdk) | Default path. `EncryptedSharedPreferences` available. |
| 28+ (Wear OS minSdk) | Watch-side code runs here. `WearableListenerService` auto-start behavior uses `BIND_JOB_SERVICE` permission on API 26+. |
| 31+ (Android 12) | `VibrationEffect.createWaveform` works without `VIBRATE` manifest permission on API 26+; no special fork needed. |
| 33+ (Android 13) | `POST_NOTIFICATIONS` permission required before showing sync-result `Notification`. Guard with `ActivityCompat.requestPermissions` check. |

### 3.3 Wear OS Impact

This feature adds new source files to the `wear/` module (`WatchWearListenerService`, `ImportNetworkSourcesUseCase`, `SyncTransferScreen`, `SyncResultScreen`) and extends `NetworkSourcesViewModel` and `NetworkSourcesScreen`. The watch module is the central receiver for all data transfers.

---

## 4. Current Architecture (Relevant Parts)

### Phone side

| Component | Location | Role |
|-----------|----------|------|
| `ResourceEntity` | `data/local/db/ResourceEntity.kt` | Full resource model (Room entity); holds `credentialsId` FK |
| `NetworkCredentialsEntity` | `data/local/db/NetworkCredentialsEntity.kt` | Credential store; `.password` getter decrypts via `CryptoHelper` |
| `NetworkCredentialsRepository` | `domain/repository/NetworkCredentialsRepository.kt` | `getByCredentialId(String)` resolves credentials by UUID |
| `BackupResource` | `domain/usecase/BackupData.kt:139` | Serializable resource subset — close to the right shape for sync payload |
| `ExportSettingsUseCase` | `domain/usecase/ExportSettingsUseCase.kt` | Gson JSON serialization precedent |
| `SettingsPagerAdapter` | `ui/settings/SettingsPagerAdapter.kt` | 4-tab Settings layout (General / Media / Playback / Operations) |
| `GeneralSettingsFragment` | `ui/settings/fragments/GeneralSettingsFragment.kt` | Tab 0 — where "Wear Companion" section will live |
| _No wearable dep_ | `app_v2/build.gradle.kts` | `play-services-wearable` missing — must be added |

### Watch side

| Component | Location | Role |
|-----------|----------|------|
| `NetworkSource` | `wear/domain/model/NetworkSource.kt` | Watch resource model (no `domain` or `sshPrivateKey` fields today) |
| `NetworkSourceRepositoryImpl` | `wear/data/preferences/NetworkSourceRepositoryImpl.kt` | CRUD via Gson + `EncryptedSharedPreferences`; `upsert` method missing |
| `NetworkSourcesViewModel` | `wear/ui/network/viewmodel/NetworkSourcesViewModel.kt` | Drives screen; no sync methods |
| `NetworkSourcesScreen` | `wear/ui/network/NetworkSourcesScreen.kt` | M2-based; hardcoded "⏳ Loading..." text; no Sync chip |
| `WearAppModule` | `wear/di/WearAppModule.kt` | Hilt module; provides `NetworkSourceRepository`, `EncryptedPrefs` |
| `play-services-wearable:18.1.0` | `wear/build.gradle.kts:120` | Already a dependency |

**Key gap**: `NetworkSourceRepositoryImpl` has no `upsertSource(source)` method — only `addSource` and `updateSource` separately. Merge logic requires an atomic upsert. Additionally, `NetworkSource` lacks `domain` (SMB domain) and `sshPrivateKey` (SFTP key-auth) fields that exist on the phone side.

---

## 5. Proposed Architecture

### 5.1 Phone side — new components

**`WearSyncSettingsFragment`** (new, in `app_v2/ui/settings/fragments/`):

A `PreferenceFragmentCompat` added to the General settings tab. It renders a dedicated "Wear Companion" preference group containing:

1. **`WatchStatusPreference`** (custom `Preference` subclass) — shows the live watch connection state:
   - *Connected*: "Watch connected: [watch model name]" with a green indicator
   - *Disconnected*: "No watch paired" with a grey indicator
   - Refreshed on `onResume` via `WearableDataLayerRepository.getConnectedNodes()`
2. **`PushToWatchPreference`** (primary action `Preference`) — title: "Push Resources to Watch"; subtitle: "N eligible resources" (SMB/FTP/SFTP count). Tap launches `BeamAnimationDialog`.
3. **`LastSyncedPreference`** (informational) — "Last synced: [relative timestamp]" read from `SharedPreferences`; hidden if never synced.

**`BeamAnimationDialog`** (new, in `app_v2/ui/settings/helpers/`):

A `DialogFragment` that takes over the full screen during the send operation. Internally it transitions through three states managed by `WearSyncViewModel`:

| State | Visual | Haptic |
|-------|--------|--------|
| `Idle` | Large watch icon + "Ready to send" | — |
| `Sending` | Pulsing concentric rings (Compose Canvas) + "Sending…" | Heartbeat pulses every 500ms |
| `Success` | Animated checkmark + "Synced N resources" | Double heavy-then-light pop |
| `Error(msg)` | Red X + error message + "Retry" button | Three rapid pulses |

The dialog is dismissed automatically after 2 s in `Success` state.

**`WearSyncViewModel`** (new, in `app_v2/ui/settings/`):

Wraps `SendResourcesToWatchUseCase`; exposes `StateFlow<WearSyncUiState>`.

**`SendResourcesToWatchUseCase`** (new, in `app_v2/domain/usecase/`):

Filters `ResourceType.SMB / FTP / SFTP` resources, resolves credentials from `NetworkCredentialsRepository`, builds `WearSyncPayload`, and calls `WearableDataLayerRepository.putDataItem`.

**`WearableDataLayerRepository`** (new, in `app_v2/data/wear/`):

Thin coroutine wrapper around `DataClient` and `MessageClient` Tasks API:
```kotlin
interface WearableDataLayerRepository {
    suspend fun getConnectedNodes(): List<Node>
    suspend fun putDataItem(path: String, payload: ByteArray)
    suspend fun sendMessage(nodeId: String, path: String, data: ByteArray)
}
```

**`PhoneWearListenerService`** (new, in `app_v2/service/`):

`WearableListenerService` subclass:
- `onMessageReceived("/fms/network_sources/request")` → triggers `SendResourcesToWatchUseCase`
- `onMessageReceived("/fms/network_sources/ack")` → updates "last synced" timestamp; notifies any active `WearSyncViewModel` via `SharedFlow`

### 5.2 Watch side — new components

**`NetworkSource`** (extend existing):

Add two optional fields:
```kotlin
val domain: String = ""          // SMB domain
val sshPrivateKey: String? = null // SFTP key-auth
```

**`ImportNetworkSourcesUseCase`** (new, in `wear/domain/usecase/`):

Receives `WearSyncPayload`, maps each `WearNetworkSourcePayload` to `NetworkSource`, and calls `NetworkSourceRepository.upsertSource()`. Merge key: `(type, server, port, shareName)`.

Returns `ImportResult(added: Int, updated: Int, skipped: Int)`.

**`WatchWearListenerService`** (new, in `wear/data/wear/`):

`WearableListenerService` subclass:
- `onDataChanged("/fms/network_sources/push")` → deserializes `WearSyncPayload`, calls `ImportNetworkSourcesUseCase`, sends `/ack` message, broadcasts result to `NetworkSourcesViewModel` via `SharedFlow`.

**`SyncTransferScreen`** (new, in `wear/ui/network/`):

Full-screen composable shown while transfer is in progress. Components:
- Centered phone icon (`Icons.Filled.PhoneAndroid` or similar)
- `CircularProgressIndicator` (M2 Wear) wrapping the icon, incrementing as each source arrives
- Subtitle: "Receiving from [phoneName]…" (populated from `WearSyncPayload.phoneName`)
- Auto-transitions to `SyncResultScreen` on completion

**`SyncResultScreen`** (new, in `wear/ui/network/`):

Full-screen composable shown after successful sync. Components:
- Animated checkmark (Compose Canvas path animation, ~400ms)
- Title: "Sync complete"
- Stats subtitle: "X added · Y updated" (from `ImportResult`)
- `Chip(label = "Browse Now")` → navigates to `NetworkSourcesScreen`, automatically highlighting newly added sources
- `Chip(label = "Done", colors = ChipDefaults.secondaryChipColors())` → pops back to `NetworkSourcesScreen` without highlight
- Auto-dismisses to `NetworkSourcesScreen` after 8 s if no user action

**`NetworkSourcesScreen`** (extend existing):

Add a `Chip` item in `SourcesListContent` beneath the existing source list and above the "Add" chip:
```
[📡 Source 1]
[📡 Source 2]
[⬇ Sync from Phone]   ← new
[+ Add Connection]
```
Shows a `LinearProgressIndicator` (indeterminate) in place of the chip while sync is pending.

**`NetworkSourcesViewModel`** (extend existing):

Add:
```kotlin
fun requestSyncFromPhone()    // sends MessageClient /request
val syncState: StateFlow<SyncState>  // Idle / Pending / Success(ImportResult) / Error
```

### 5.3 Data Model

**Sync payload** (new file: `wear/domain/model/WearSyncPayload.kt`):

```kotlin
data class WearNetworkSourcePayload(
    val id: String,
    val type: String,       // "SMB" | "FTP" | "SFTP"
    val name: String,
    val server: String,
    val port: Int,
    val username: String,
    val password: String,   // Plaintext; see ADR-1
    val shareName: String?,
    val basePath: String,
    val domain: String = "",
    val sshPrivateKey: String? = null
)

data class WearSyncPayload(
    val version: Int = 1,
    val sentAt: Long,       // epoch ms — stale guard
    val phoneName: String,
    val sources: List<WearNetworkSourcePayload>
)
```

**Field mapping: `ResourceEntity` + `NetworkCredentialsEntity` → `WearNetworkSourcePayload`**

| Source field | Target field | Notes |
|---|---|---|
| `ResourceEntity.id.toString()` | `id` | Stable UUID |
| `ResourceEntity.type` | `type` | SMB/FTP/SFTP only; LOCAL/CLOUD filtered |
| `ResourceEntity.name` | `name` | |
| `NetworkCredentialsEntity.server` | `server` | Resolved via `credentialsId` |
| `NetworkCredentialsEntity.port` | `port` | |
| `NetworkCredentialsEntity.username` | `username` | |
| `NetworkCredentialsEntity.password` | `password` | Decrypted via `.password` getter |
| `NetworkCredentialsEntity.shareName` | `shareName` | SMB only |
| `ResourceEntity.path` | `basePath` | |
| `NetworkCredentialsEntity.domain` | `domain` | SMB domain |
| `NetworkCredentialsEntity.sshPrivateKey` | `sshPrivateKey` | SFTP key-auth |

**Data Layer paths**

| Path | Direction | Transport | Payload |
|------|-----------|-----------|---------|
| `/fms/network_sources/push` | Phone → Watch | `DataClient.putDataItem` | `WearSyncPayload` JSON |
| `/fms/network_sources/request` | Watch → Phone | `MessageClient.sendMessage` | `{}` |
| `/fms/network_sources/ack` | Watch → Phone | `MessageClient.sendMessage` | `{"added":N,"updated":M}` |

### 5.4 UX & Motion Design ("The Beam")

**Visual language**

- **Phone — `BeamAnimationDialog`**: Concentric pulsing rings drawn via Compose Canvas (`drawCircle` with animated alpha and radius). Ring wave interval: 600ms, fade-out over 1200ms. Watch icon at center.
- **Watch — `SyncTransferScreen`**: Large phone icon at centre; `CircularProgressIndicator` fills as sources are imported (deterministic if total count is known, indeterminate otherwise). Subtitle personalised with `phoneName` from payload.
- **Watch — `SyncResultScreen`**: Animated checkmark drawn via `Canvas.drawPath` with `PathEffect` stroke reveal (~400ms). Large, centred. Stats below. "Browse Now" primary chip.

**Haptics protocol**

| Event | Device | Pattern |
|---|---|---|
| Start Sending | Phone | `VibrationEffect.createOneShot(300ms, heavy)` |
| Data Beaming | Phone | Repeating `createWaveform([0,100,400], [0,80,0], 0)` |
| Each Source Received | Watch | `HapticFeedbackType.TextHandleMove` (short tick) |
| Sync Success | Both | `createWaveform([0,100,100,200], [0,200,0,255], -1)` (double pop) |
| Sync Failure | Both | `createWaveform([0,300,200,300,200,300], heavy, -1)` (3 error pulses) |

**Audio feedback**

- **Phone**: `MediaActionSound.FOCUS_COMPLETE` (rising swoosh) when beam starts.
- **Watch**: `MediaActionSound` `SHUTTER_CLICK` (high-pitched ding) on success.

### 5.5 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | `BeamAnimationDialog` delegates to `WearSyncViewModel`; all use-case logic in `SendResourcesToWatchUseCase` |
| Naming conventions | ✅ | `SendResourcesToWatchUseCase`, `WearableDataLayerRepository`, `WearSyncViewModel`, `ImportNetworkSourcesUseCase` |
| Data flow `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | `WearSyncSettingsFragment → WearSyncViewModel → SendResourcesToWatchUseCase → WearableDataLayerRepository → DataClient` |
| No `Log.d()` — Timber only | ✅ | |
| Room schema version incremented | N/A | No DB schema changes |
| `StateFlow` for state, `SharedFlow` for events | ✅ | `WearSyncViewModel` uses `StateFlow<WearSyncUiState>`; ack notifications via `SharedFlow` |
| Hilt DI bindings declared in module | ✅ | `WearableDataLayerRepository` binding in `core/di/RepositoryModule.kt`; `WearSyncViewModel` via `@HiltViewModel`; watch-side bindings in `WearAppModule` |
| File size ≤ 1000 lines | ✅ | Largest new file (`WearSyncSettingsFragment`) estimated ~250 lines; `BeamAnimationDialog` ~180 lines |

---

## 6. Data Flow

**Phone → Watch push (user taps "Push to Watch")**

```
WearSyncSettingsFragment.onPushClick()
  → WearSyncViewModel.startPush()
      → SendResourcesToWatchUseCase.invoke()
          → ResourceRepository.getAllNetworkResources()     // SMB/FTP/SFTP only
          → for each: NetworkCredentialsRepository.getByCredentialId()
          → build WearSyncPayload (filter LOCAL, CLOUD)
          → Gson.toJson(payload).toByteArray()
          → WearableDataLayerRepository.putDataItem("/fms/network_sources/push", bytes)
  ←—— WearSyncViewModel.syncState = Sending
  
WatchWearListenerService.onDataChanged()
  → deserialize WearSyncPayload; check sentAt < 24h
  → ImportNetworkSourcesUseCase.import(payload)
      → for each source: NetworkSourceRepository.upsertSource()
  → MessageClient.sendMessage(phoneNodeId, "/fms/network_sources/ack", stats)
  → broadcast ImportResult to NetworkSourcesViewModel via SharedFlow
  → navigate to SyncResultScreen

PhoneWearListenerService.onMessageReceived("/ack")
  → update "last synced" timestamp in SharedPrefs
  → WearSyncViewModel.syncState = Success(N)
  ←—— BeamAnimationDialog shows success state + haptic double-pop
```

**Watch-initiated pull (user taps "Sync from Phone")**

```
NetworkSourcesScreen (Sync chip tapped)
  → NetworkSourcesViewModel.requestSyncFromPhone()
      → WearableDataLayerRepository.sendMessage(phoneNodeId, "/fms/network_sources/request", empty)
  ←—— NetworkSourcesViewModel.syncState = Pending
  → show SyncTransferScreen

PhoneWearListenerService.onMessageReceived("/request")
  → SendResourcesToWatchUseCase.sendAll()
  → (same as push flow above)
  ←—— BeamAnimationDialog appears on phone (if app in foreground)
```

---

## 7. Files to Modify

**Phone (`app_v2/`)**

| File | Change | Est. size after |
|------|--------|-----------------|
| `app_v2/build.gradle.kts` | Add `play-services-wearable:18.1.0` dependency | ~310 lines |
| `app_v2/src/main/AndroidManifest.xml` | Declare `PhoneWearListenerService` with intent filter | ~80 lines |
| `core/di/RepositoryModule.kt` | Add `WearableDataLayerRepository` binding | ~120 lines |
| `ui/settings/fragments/GeneralSettingsFragment.kt` | Add "Wear Companion" preference group and navigate to `WearSyncSettingsFragment` | ~320 lines |
| `ui/settings/SettingsPagerAdapter.kt` | No change needed — "Wear Companion" lives inside General tab | — |
| `app_v2/src/main/res/values/strings.xml` | Add ~12 new strings (EN) | +12 entries |
| `app_v2/src/main/res/values-ru/strings.xml` | Add ~12 new strings (RU) | +12 entries |
| `app_v2/src/main/res/values-uk/strings.xml` | Add ~12 new strings (UK) | +12 entries |

**Watch (`wear/`)**

| File | Change | Est. size after |
|------|--------|-----------------|
| `wear/domain/model/NetworkSource.kt` | Add `domain: String = ""` and `sshPrivateKey: String? = null` fields | ~40 lines |
| `wear/data/preferences/NetworkSourceRepositoryImpl.kt` | Add `upsertSource(source)` method | ~130 lines |
| `wear/domain/repository/NetworkSourceRepository.kt` | Add `suspend fun upsertSource(source: NetworkSource)` to interface | ~20 lines |
| `wear/ui/network/viewmodel/NetworkSourcesViewModel.kt` | Add `requestSyncFromPhone()`, `syncState` | ~90 lines |
| `wear/ui/network/NetworkSourcesScreen.kt` | Add "Sync from Phone" chip + sync state indicator | ~260 lines |
| `wear/src/main/AndroidManifest.xml` | Declare `WatchWearListenerService` | ~70 lines |
| `wear/di/WearAppModule.kt` | Add `ImportNetworkSourcesUseCase`, `WatchWearListenerService` bindings | ~155 lines |
| `wear/src/main/res/values/strings.xml` | Add ~10 new strings (EN) | +10 entries |
| `wear/src/main/res/values-ru/strings.xml` | Add ~10 new strings (RU) | +10 entries |
| `wear/src/main/res/values-uk/strings.xml` | Add ~10 new strings (UK) | +10 entries |

> **Backup rule**: `NetworkSourceRepositoryImpl.kt` (110 lines) is under 500 — no backup required. `GeneralSettingsFragment.kt` — check actual size before modifying; create `temp/GeneralSettingsFragment_<timestamp>.kt` backup if > 500 lines.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| No paired watch | Med | `getConnectedNodes()` returns empty → "No watch connected" dialog; no send attempt |
| Watch app not installed / outdated (no `WatchWearListenerService`) | Low | Node found but `WearableListenerService` capability not declared → show "Install or update watch app" |
| Credential decryption failure (`CryptoHelper.decrypt` returns null) | Low | Skip that source; count skipped; show "N sources could not be sent" warning |
| Payload exceeds 100KB Data Layer limit | Very low | 100KB ≈ 500 typical sources; chunk to `/push/0`, `/push/1`… if needed (step 12 in §13) |
| Stale Data Item replayed on reconnect | Med | Check `WearSyncPayload.sentAt`; ignore if older than 24h |
| SSH key sources (SFTP key-auth) — `sshPrivateKey` field missing on watch | Med | Add field to `NetworkSource`; mark optional; log warning if watch build doesn't have the field |
| `MessageClient.sendMessage` fails (phone unreachable from watch) | Med | Show "Phone not reachable" on watch; let user retry |
| Watch-side Room/prefs write conflict during sync | Low | `upsertSource` runs on `Dispatchers.IO` inside `withContext`; sequential — no race |
| Adding `play-services-wearable` inflates APK size | Low | ~500KB; within Play Store limits for all flavors |
| Google Play Services absent (non-GMS device) | Very low | `DataClient` calls will throw `ApiException`; catch and show "Device not supported" |

---

## 9. Testing Plan

### 9.1 Unit Tests

**`SendResourcesToWatchUseCaseTest`** (`app_v2/src/test/`):
- Resources with `type=LOCAL` are excluded from payload
- Resources with `type=CLOUD` are excluded from payload
- Credential decryption failure → source skipped, `skippedCount` incremented
- SSH key field populated if `sshPrivateKey` not null

**`ImportNetworkSourcesUseCaseTest`** (`wear/src/test/`):
- New source (no match on merge key) → `addSource()` called, `added++`
- Existing source (merge key match) → `upsertSource()` updates, `updated++`
- Payload with `sentAt` > 24h old → import rejected, method returns early
- Empty payload → no repository calls, `ImportResult(0,0,0)`

### 9.2 Manual Test Cases

1. **Happy path — push from phone**: Phone has 3 SMB + 1 SFTP + 1 LOCAL + 1 CLOUD. Tap "Push to Watch". Watch receives 3 SMB + 1 SFTP. LOCAL and CLOUD absent on watch. `SyncResultScreen` shows "4 added".
2. **Happy path — pull from watch**: Tap "Sync from Phone" chip on watch. `SyncTransferScreen` appears. Phone `BeamAnimationDialog` fires. Watch receives sources.
3. **Merge / no duplicate**: Run sync twice. Second sync shows "0 added, 4 updated" (or same count if unchanged). No duplicates in watch source list.
4. **Error — no watch connected**: Remove watch from Bluetooth range. Tap "Push to Watch". Dialog shows "No watch connected".
5. **Error — phone not reachable from watch**: Put phone in airplane mode. Tap "Sync from Phone". Watch shows "Phone not reachable" toast.
6. **Credential decryption failure**: Manually corrupt a credential in Room (dev debug only). Verify skipped count shown, other sources sent successfully.
7. **SSH key SFTP resource**: Add SFTP resource with key auth on phone. Sync. Verify `sshPrivateKey` field populated on watch side.
8. **Stale payload**: Inject a `WearSyncPayload` with `sentAt = now - 25h` via debug adb. Verify watch ignores it.
9. **Large resource set**: Create 20+ network sources on phone. Full sync completes without truncation.
10. **SyncResultScreen "Browse Now"**: After sync, tap "Browse Now". Navigates to `NetworkSourcesScreen` showing newly synced sources.

### 9.3 Maestro E2E

No Maestro tests needed for this feature. The Wearable Data Layer requires a physical paired device; emulator-based E2E is not feasible with the current Maestro setup.

---

## 10. Accessibility

**Phone — `WearSyncSettingsFragment`**: All `Preference` items use standard `PreferenceFragmentCompat` which provides TalkBack accessibility by default. `WatchStatusPreference` must set `contentDescription` on its custom view to announce the connection state (not just display it via color). Minimum touch target of 48dp is satisfied by the Preference item row height.

**Phone — `BeamAnimationDialog`**: The pulsing animation must be suppressed when `reduceMotion` (`Settings.Global.TRANSITION_ANIMATION_SCALE == 0`) is detected — fall back to a static progress spinner. The dialog's close button must have `contentDescription = "Dismiss"`.

**Watch — `SyncTransferScreen` and `SyncResultScreen`**: Both screens are Compose-on-Wear. All `Chip` elements must have `Modifier.semantics { contentDescription = … }` set. The checkmark animation on `SyncResultScreen` should be accompanied by a `LocalAccessibilityManager.current.announceForAccessibility("Sync complete: X added")` call for TalkBack users. Touch targets on round-display chips are automatically ≥ 48dp via `ChipDefaults`.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): Under **Wear OS** section — "Send network resources (SMB/FTP/SFTP) from phone to watch in one tap via the Wear Companion settings; watch can also pull from phone directly from the Network Sources screen."
- `docs/FEATURES_RU.md` (RU): В разделе **Wear OS** — "Передача сетевых ресурсов (SMB/FTP/SFTP) с телефона на часы одним нажатием через раздел «Wear-компаньон» в настройках; часы также могут запрашивать синхронизацию самостоятельно с экрана сетевых источников."
- `docs/FEATURES_UK.md` (UK): У розділі **Wear OS** — "Передача мережевих ресурсів (SMB/FTP/SFTP) з телефону на годинник одним дотиком через розділ «Wear-компаньйон» у налаштуваннях; годинник також може запитувати синхронізацію самостійно з екрана мережевих джерел."

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Plaintext password in `WearSyncPayload`**
- **Decision:** Passwords are decrypted on the phone and transmitted as plaintext in the Wearable Data Layer payload. The watch re-encrypts them into `EncryptedSharedPreferences`.
- **Alternatives considered:** (a) Phone-Keystore-encrypted blob — the watch cannot decrypt it (Keystore keys are device-bound). (b) Diffie-Hellman key exchange over the Data Layer — significant extra complexity with no security gain over the existing TLS channel.
- **Reason:** The Data Layer channel is encrypted (TLS + Google Play Services per-device keys). Plaintext in memory exists only for the duration of `SendResourcesToWatchUseCase.invoke()`. This is the same threat model as any password manager sync.

**ADR-2: `DataClient.putDataItem` over `MessageClient.sendMessage` for the payload**
- **Decision:** Use `putDataItem` (persistent data item) for the resource payload, not `sendMessage` (fire-and-forget).
- **Alternatives considered:** `sendMessage` — simpler but requires watch app to be in foreground or `WearableListenerService` to be running; payload is lost if watch app is killed during delivery.
- **Reason:** `putDataItem` persists the last sent payload in the Data Layer cloud. The watch receives it even after an app restart or delayed connection. Stale-payload guard (24h `sentAt` check) prevents unwanted re-imports.

**ADR-3: Merge key `(type, server, port, shareName)` rather than `id`**
- **Decision:** Upsert uses `(type, server, port, shareName)` as the merge key, not the UUID `id`.
- **Alternatives considered:** UUID-based merge — would cause duplicate entries if the same server is added from scratch on the watch (different UUID).
- **Reason:** The user may have manually added the same server on the watch with a different UUID. Merging on the logical identity (connection coordinates) avoids duplicates and updates credentials in place.

**ADR-4: `WearSyncSettingsFragment` inside General tab, not a new tab**
- **Decision:** "Wear Companion" is a preference section inside `GeneralSettingsFragment`, not a new 5th tab in `SettingsPagerAdapter`.
- **Alternatives considered:** New tab — would require updating `SettingsPagerAdapter.getItemCount()` from 4 → 5 and adding a new tab label.
- **Reason:** The feature is a one-time setup action, not a frequently visited settings category. Adding a dedicated tab for a single action would clutter the tab bar. A collapsible preference group inside General keeps the surface area small.

**ADR-5: `SyncResultScreen` as a dedicated composable, not a `Snackbar`/`Toast`**
- **Decision:** Import result is shown as a full-screen `SyncResultScreen` with a "Browse Now" CTA.
- **Alternatives considered:** `Snackbar` / `Toast` — non-interactive, no way to surface the "Browse Now" action; too brief on a small watch screen.
- **Reason:** The watch display is small and round. A full-screen result state with a large checkmark and a clear action button provides better affordance and matches Wear OS design patterns (see Material 3 Wear confirmation dialogs).

---

## 13. Implementation Steps

> Follow this order. Each step must end with `.\scripts\add_to_dev_log.ps1`.

1. **Add `play-services-wearable:18.1.0`** to `app_v2/build.gradle.kts` (same version already in `wear/`).
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "dependencies" "Add play-services-wearable dependency for phone Wearable Data Layer"
   ```

2. **Create `WearSyncPayload.kt`** at `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSyncPayload.kt` — define `WearNetworkSourcePayload` and `WearSyncPayload` data classes (see §5.3).
   ```
   .\scripts\add_to_dev_log.ps1 "wear/domain/model/WearSyncPayload.kt" "WearSyncPayload" "Add Wearable Data Layer sync payload models"
   ```

3. **Extend `NetworkSource`** — add `domain: String = ""` and `sshPrivateKey: String? = null` fields.
   ```
   .\scripts\add_to_dev_log.ps1 "wear/domain/model/NetworkSource.kt" "NetworkSource" "Add domain and sshPrivateKey fields for sync payload mapping"
   ```

4. **Add `upsertSource` to `NetworkSourceRepository`** interface — `suspend fun upsertSource(source: NetworkSource)`.
   ```
   .\scripts\add_to_dev_log.ps1 "wear/domain/repository/NetworkSourceRepository.kt" "NetworkSourceRepository" "Add upsertSource method for sync merge logic"
   ```

5. **Implement `upsertSource` in `NetworkSourceRepositoryImpl`** — load list, find by merge key `(type, server, port, shareName)`, replace if found else add.
   ```
   .\scripts\add_to_dev_log.ps1 "wear/data/preferences/NetworkSourceRepositoryImpl.kt" "NetworkSourceRepositoryImpl" "Implement upsertSource with merge-key dedup logic"
   ```

6. **Create `WearableDataLayerRepository`** interface + `WearableDataLayerRepositoryImpl` at `app_v2/data/wear/` (see §5.1).
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/data/wear/WearableDataLayerRepositoryImpl.kt" "WearableDataLayerRepository" "Add phone-side Data Layer repository"
   ```

7. **Register `WearableDataLayerRepository` binding** in `core/di/RepositoryModule.kt`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/core/di/RepositoryModule.kt" "RepositoryModule" "Add WearableDataLayerRepository Hilt binding"
   ```

8. **Create `SendResourcesToWatchUseCase`** at `app_v2/domain/usecase/` — filter, credential resolve, serialize, `putDataItem`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/domain/usecase/SendResourcesToWatchUseCase.kt" "SendResourcesToWatchUseCase" "Add use case for sending network resources to watch"
   ```

9. **Create `ImportNetworkSourcesUseCase`** at `wear/domain/usecase/` — parse payload, stale guard, upsert loop, return `ImportResult`.
   ```
   .\scripts\add_to_dev_log.ps1 "wear/domain/usecase/ImportNetworkSourcesUseCase.kt" "ImportNetworkSourcesUseCase" "Add use case for merging sync payload into watch repository"
   ```

10. **Create `PhoneWearListenerService`** at `app_v2/service/` — handles `/request` and `/ack` paths.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/service/PhoneWearListenerService.kt" "PhoneWearListenerService" "Add WearableListenerService for phone-side Data Layer events"
    ```

11. **Register `PhoneWearListenerService`** in `app_v2/src/main/AndroidManifest.xml` with `DATA_CHANGED` + `MESSAGE_RECEIVED` intent filters scoped to `/fms/network_sources`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/AndroidManifest.xml" "PhoneWearListenerService" "Register PhoneWearListenerService in manifest"
    ```

12. **Create `WatchWearListenerService`** at `wear/data/wear/` — `onDataChanged`, calls `ImportNetworkSourcesUseCase`, sends `/ack`.
    ```
    .\scripts\add_to_dev_log.ps1 "wear/data/wear/WatchWearListenerService.kt" "WatchWearListenerService" "Add WearableListenerService for watch-side Data Layer events"
    ```

13. **Register `WatchWearListenerService`** in `wear/src/main/AndroidManifest.xml`.
    ```
    .\scripts\add_to_dev_log.ps1 "wear/src/main/AndroidManifest.xml" "WatchWearListenerService" "Register WatchWearListenerService in wear manifest"
    ```

14. **Add `WearSyncViewModel`** at `app_v2/ui/settings/` — `StateFlow<WearSyncUiState>`, `startPush()`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/ui/settings/WearSyncViewModel.kt" "WearSyncViewModel" "Add ViewModel for Wear sync state management"
    ```

15. **Create `BeamAnimationDialog`** at `app_v2/ui/settings/helpers/` — Compose dialog with pulsing rings animation (§5.4).
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/ui/settings/helpers/BeamAnimationDialog.kt" "BeamAnimationDialog" "Add beam animation dialog for phone-side sync UX"
    ```

16. **Create `WearSyncSettingsFragment`** at `app_v2/ui/settings/fragments/` — `PreferenceFragmentCompat` with watch status, push action, last-synced display (§5.1). Add navigation entry in `GeneralSettingsFragment`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/ui/settings/fragments/WearSyncSettingsFragment.kt" "WearSyncSettingsFragment" "Add Wear Companion settings screen"
    .\scripts\add_to_dev_log.ps1 "app_v2/ui/settings/fragments/GeneralSettingsFragment.kt" "GeneralSettingsFragment" "Add Wear Companion preference group entry point"
    ```

17. **Create `SyncTransferScreen`** at `wear/ui/network/` (§5.2).
    ```
    .\scripts\add_to_dev_log.ps1 "wear/ui/network/SyncTransferScreen.kt" "SyncTransferScreen" "Add full-screen sync transfer animation for watch"
    ```

18. **Create `SyncResultScreen`** at `wear/ui/network/` — checkmark animation, stats, "Browse Now" chip, auto-dismiss (§5.2).
    ```
    .\scripts\add_to_dev_log.ps1 "wear/ui/network/SyncResultScreen.kt" "SyncResultScreen" "Add sync result screen with Browse Now CTA"
    ```

19. **Extend `NetworkSourcesViewModel`** — add `requestSyncFromPhone()`, `syncState: StateFlow<SyncState>`.
    ```
    .\scripts\add_to_dev_log.ps1 "wear/ui/network/viewmodel/NetworkSourcesViewModel.kt" "NetworkSourcesViewModel" "Add requestSyncFromPhone and syncState for watch-initiated pull"
    ```

20. **Extend `NetworkSourcesScreen`** — add "Sync from Phone" chip and conditional `SyncTransferScreen` display (§5.2).
    ```
    .\scripts\add_to_dev_log.ps1 "wear/ui/network/NetworkSourcesScreen.kt" "NetworkSourcesScreen" "Add Sync from Phone chip and sync state indicator"
    ```

21. **Wire navigation** in `wear/MainActivity.kt` (NavHost) — add routes for `sync_transfer` and `sync_result/{added}/{updated}`.
    ```
    .\scripts\add_to_dev_log.ps1 "wear/MainActivity.kt" "MainActivity" "Add NavHost routes for SyncTransferScreen and SyncResultScreen"
    ```

22. **Integrate haptic feedback** in `BeamAnimationDialog` (phone) and `WatchWearListenerService` / `SyncResultScreen` (watch) per the protocol in §5.4.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/ui/settings/helpers/BeamAnimationDialog.kt" "BeamAnimationDialog" "Integrate haptic feedback protocol for sync events"
    ```

23. **Add string resources** (EN/RU/UK) for all new labels. See §7 for count.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add Wear Companion sync UI strings EN"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings" "Add Wear Companion sync UI strings RU"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings" "Add Wear Companion sync UI strings UK"
    .\scripts\add_to_dev_log.ps1 "wear/src/main/res/values/strings.xml" "strings" "Add Wear sync result UI strings EN"
    .\scripts\add_to_dev_log.ps1 "wear/src/main/res/values-ru/strings.xml" "strings" "Add Wear sync result UI strings RU"
    .\scripts\add_to_dev_log.ps1 "wear/src/main/res/values-uk/strings.xml" "strings" "Add Wear sync result UI strings UK"
    ```

24. **Write unit tests** for `SendResourcesToWatchUseCase` and `ImportNetworkSourcesUseCase` (§9.1).
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/test/.../SendResourcesToWatchUseCaseTest.kt" "SendResourcesToWatchUseCaseTest" "Add unit tests for credential filtering and payload build"
    .\scripts\add_to_dev_log.ps1 "wear/src/test/.../ImportNetworkSourcesUseCaseTest.kt" "ImportNetworkSourcesUseCaseTest" "Add unit tests for merge/dedup logic and stale guard"
    ```

25. **Update FEATURES docs** (§11).
    ```
    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Document Wear OS resource sync feature"
    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU" "Document Wear OS resource sync feature RU"
    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK" "Document Wear OS resource sync feature UK"
    ```

**Mandatory checklist:**
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`) for both `app_v2` and `wear`
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated
- [ ] No Room DB migration needed (no schema changes)
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file (steps 1–25)
- [ ] Backup created for any modified file > 500 lines before editing

---

## 14. Out of Scope (future items)

- **Watch → Phone sync**: Watch has no meaningful data the phone doesn't have (watch sources are a subset of phone sources).
- **Cloud resource sync**: Google/OneDrive/Dropbox OAuth tokens are device-bound and cannot be transferred.
- **photos / legacy flavor support**: Photos flavor has no network sources to sync; legacy targets API 23-25 devices incompatible with Wear OS 2.0+ pairing.
- **Incremental / delta sync**: Only changed sources are sent. Current design always sends the full set; delta sync would require a watch-side "last synced" hash.
- **Watch → Watch sync** (multiple paired watches): Data Layer supports multiple nodes; multi-watch delivery is possible but requires UX design not yet scoped.
- **Conflict UI**: Currently last-write wins. A per-source "phone vs watch" conflict picker is deferred.
- **Pair setup wizard**: A guided first-run flow for pairing the watch companion is out of scope for this spec.
