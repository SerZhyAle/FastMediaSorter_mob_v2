# Specification: III.6 — Wear OS Resource Config Export/Import

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 4 — Substantial (8–16h, notable risk)
**Roadmap entry:** Send resource config to watch via Wearable Data Layer

---

## 1. Problem Statement

The Wear OS companion app has its own isolated resource configuration: network sources (SMB/SFTP/FTP) are entered manually on the watch via a tiny keyboard in `AddSmbScreen`. This is painful — the watch has no physical keyboard, on-screen text entry on a round display is awkward, and users who have already configured 5+ network sources on their phone must re-enter all credentials from scratch on the watch.

The phone and watch apps are already signed with the same certificate and share the same application ID prefix (`com.sza.fastmediasorter`), so they satisfy the precondition for using the Wearable Data Layer. The `play-services-wearable:18.1.0` dependency is present in `wear/build.gradle.kts` but not in `app_v2/build.gradle.kts` — the phone side has zero Wearable Data Layer integration today.

---

## 2. Goals

1. **Phone → Watch push**: User selects one or more network resources on the phone and sends their configuration (including credentials) to the watch in one tap.
2. **Watch-initiated pull**: From the watch's Network Sources screen, user can request a full sync of all phone resources with a single button.
3. **Credential transfer**: Credentials stored in `NetworkCredentialsRepository` are resolved on the phone, decrypted, and re-encrypted using the Watch's `EncryptedSharedPreferences` — the plaintext never persists in the Data Layer.
4. **Merge, not replace**: Synced sources are merged into existing watch sources by `(type, server, port, shareName)` — existing watch-only sources are preserved.
5. **No automatic background sync**: This is user-initiated only. No WorkManager, no periodic push. This avoids draining watch battery.

**Non-goals:**
- Watch → Phone sync (watch is a consumer, not a source of truth)
- Cloud resource sync (CLOUD type sources require OAuth tokens tied to the phone account; sending these to the watch would not work)
- Local resource sync (phone `LOCAL` type paths are meaningless on a watch)
- Automatic conflict resolution (last-write wins per `(type, server, port, shareName)` key)
- Real-time status sync (whether a source is online/offline)

---

## 3. Current Architecture

### Phone side

| Component | Location | Role |
|-----------|----------|------|
| `ResourceEntity` | `data/local/db/ResourceEntity.kt` | Full resource model (~35 fields), stored in Room |
| `NetworkCredentialsEntity` | `data/local/db/NetworkCredentialsEntity.kt` | Stores credentials separately; `password` getter decrypts via `CryptoHelper` |
| `NetworkCredentialsRepository` | `domain/repository/NetworkCredentialsRepository.kt` | `getByCredentialId(String)` resolves credentials by UUID |
| `BackupResource` | `domain/usecase/BackupData.kt:139` | Serializable resource subset — nearly the right shape for sync payload |
| `ExportSettingsUseCase` | `domain/usecase/ExportSettingsUseCase.kt` | JSON serialization precedent |
| _No Wearable integration_ | — | `play-services-wearable` NOT in `app_v2/build.gradle.kts` |

### Watch side

| Component | Location | Role |
|-----------|----------|------|
| `NetworkSource` | `wear/domain/model/NetworkSource.kt` | Watch resource model (SMB/FTP/SFTP/GOOGLE_DRIVE) |
| `NetworkSourceRepositoryImpl` | `wear/data/preferences/NetworkSourceRepositoryImpl.kt` | CRUD on `EncryptedSharedPreferences` via Gson JSON |
| `NetworkSourcesViewModel` | `wear/ui/network/viewmodel/NetworkSourcesViewModel.kt` | Drives `NetworkSourcesScreen` |
| `AddSmbScreen` | `wear/ui/network/AddSmbScreen.kt` | Manual entry UI (the painful path we're replacing) |
| `play-services-wearable:18.1.0` | `wear/build.gradle.kts:120` | Already a dependency — Data Layer API available |

### Data Layer API (Wearable Data Layer)

- **`DataClient.putDataItem(PutDataRequest)`** — stores a key-value map on a `/path`; automatically syncs to all paired nodes. Survives app restarts.
- **`MessageClient.sendMessage(nodeId, path, data)`** — fire-and-forget message; requires the receiver app to be running (or a `WearableListenerService`).
- **`WearableListenerService`** — background service that receives Data Layer events on both phone and watch even when the app is not in the foreground.
- Data items are encrypted by the platform; only apps signed with the same certificate can read them.

---

## 4. Data Model

### 4.1 Sync Payload

A new shared payload model lives in a `wear/domain/model/` on the watch side and is serialized to/from JSON bytes on the phone side:

```kotlin
/**
 * Serializable representation of a network source for Wearable Data Layer transfer.
 * Credentials are included as plaintext — the Data Layer encrypts the channel;
 * the payload is stored only in EncryptedSharedPreferences on both ends.
 */
data class WearNetworkSourcePayload(
    val id: String,            // UUID — stable identity for merge/dedup
    val type: String,          // "SMB" | "FTP" | "SFTP"
    val name: String,
    val server: String,
    val port: Int,
    val username: String,
    val password: String,      // Plaintext — resolved from CryptoHelper on phone before sending
    val shareName: String?,    // SMB only
    val basePath: String,
    val domain: String = ""    // SMB domain
)

data class WearSyncPayload(
    val version: Int = 1,
    val sentAt: Long,          // epoch ms — used to detect stale payloads
    val sources: List<WearNetworkSourcePayload>
)
```

**Why plaintext password in the payload?** The Wearable Data Layer uses TLS + Android Keystore-backed encryption at rest. Neither end stores the raw payload outside of EncryptedSharedPreferences. The alternative (sending a phone-side Keystore-encrypted blob) cannot be decrypted on the watch since Keystore keys are device-bound.

### 4.2 Mapping: `ResourceEntity` + `NetworkCredentialsEntity` → `WearNetworkSourcePayload`

| Source field | Target field | Notes |
|---|---|---|
| `ResourceEntity.id.toString()` | `id` | Stable UUID across sync operations |
| `ResourceEntity.type` | `type` | Only SMB/SFTP/FTP pass through; LOCAL/CLOUD filtered out |
| `ResourceEntity.name` | `name` | |
| `NetworkCredentialsEntity.server` | `server` | Resolved via `credentialsId` |
| `NetworkCredentialsEntity.port` | `port` | |
| `NetworkCredentialsEntity.username` | `username` | |
| `NetworkCredentialsEntity.password` | `password` | Decrypted via `.password` getter |
| `NetworkCredentialsEntity.shareName` | `shareName` | SMB only |
| `ResourceEntity.path` | `basePath` | The root browse path within the share |
| `NetworkCredentialsEntity.domain` | `domain` | SMB domain |

### 4.3 Mapping: `WearNetworkSourcePayload` → `NetworkSource` (watch)

| Source field | Target field | Notes |
|---|---|---|
| `id` | `id` | Preserved for future dedup |
| `type` | `type` (enum) | `"SMB"` → `NetworkSourceType.SMB`, etc. |
| `name` | `name` | |
| `server` | `server` | |
| `port` | `port` | |
| `username` | `username` | |
| `password` | `password` | Stored encrypted in EncryptedSharedPreferences by `NetworkSourceRepositoryImpl` |
| `shareName` | `shareName` | |
| `basePath` | `basePath` | |

### 4.4 Data Layer Paths

| Path | Direction | Transport | Payload |
|------|-----------|-----------|---------|
| `/fms/network_sources/push` | Phone → Watch | `DataClient.putDataItem` | `WearSyncPayload` JSON |
| `/fms/network_sources/request` | Watch → Phone | `MessageClient.sendMessage` | Empty or `{"requestedAt": <epoch>}` |
| `/fms/network_sources/ack` | Watch → Phone | `MessageClient.sendMessage` | `{"imported": N, "merged": M}` |

---

## 5. Architecture

### 5.1 Phone side — new components

```
app_v2/
└── domain/usecase/
│   └── SendResourcesToWatchUseCase.kt     — resolves credentials, builds WearSyncPayload,
│                                            calls DataClient.putDataItem
└── data/wear/
│   └── WearableDataLayerRepository.kt    — wraps DataClient + MessageClient (Tasks API)
└── service/
│   └── PhoneWearListenerService.kt       — WearableListenerService: handles
│                                            /fms/network_sources/request from watch
└── ui/settings/fragments/
    └── WearSyncSettingsFragment.kt (or   — "Send to Watch" UI in Settings
        button in ResourceEditorFragment)
```

**`app_v2/build.gradle.kts`** — add:
```
implementation("com.google.android.gms:play-services-wearable:18.1.0")
```

**AndroidManifest** — declare `PhoneWearListenerService` with intent filter:
```xml
<service android:name=".service.PhoneWearListenerService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data android:scheme="wear" android:host="*"
              android:pathPrefix="/fms/network_sources" />
    </intent-filter>
</service>
```

### 5.2 Watch side — new components

```
wear/
└── domain/usecase/
│   └── ImportNetworkSourcesUseCase.kt    — merges incoming WearSyncPayload into
│                                            NetworkSourceRepository
└── data/wear/
│   └── WatchWearListenerService.kt       — WearableListenerService: receives push from phone,
│                                            calls ImportNetworkSourcesUseCase, sends ack
└── ui/network/viewmodel/
    └── NetworkSourcesViewModel.kt        — extend with requestSyncFromPhone() function
```

### 5.3 Sequence diagrams

**Phone → Watch push (user-initiated from phone):**

```
User taps "Send to Watch" in phone Settings
→ SendResourcesToWatchUseCase
    → ResourceRepository.getAllNetworkResources()
    → for each resource: NetworkCredentialsRepository.getByCredentialId(credentialsId)
    → build WearSyncPayload (filter LOCAL + CLOUD out)
    → Gson.toJson(payload) → ByteArray
    → DataClient.putDataItem("/fms/network_sources/push", bytes)
→ WatchWearListenerService.onDataChanged()
    → ImportNetworkSourcesUseCase.merge(payload)
    → NetworkSourceRepository.upsert(sources)
    → MessageClient.sendMessage(phoneNodeId, "/fms/network_sources/ack", stats)
→ PhoneWearListenerService.onMessageReceived("/ack")
    → show Snackbar/Toast on phone: "Synced N sources to watch"
```

**Watch-initiated pull:**

```
User taps "Sync from Phone" in NetworkSourcesScreen
→ MessageClient.sendMessage(phoneNodeId, "/fms/network_sources/request", empty)
→ PhoneWearListenerService.onMessageReceived("/request")
    → SendResourcesToWatchUseCase.sendAll()
    → DataClient.putDataItem (same as push flow)
→ WatchWearListenerService.onDataChanged() (same as push flow)
```

---

## 6. Implementation Steps

| # | Task | Files | Est. |
|---|------|-------|------|
| 1 | Add `play-services-wearable` to `app_v2/build.gradle.kts` | `app_v2/build.gradle.kts` | 10 min |
| 2 | Create `WearNetworkSourcePayload` + `WearSyncPayload` data classes | `wear/domain/model/WearSyncPayload.kt` (new) | 20 min |
| 3 | Create `WearableDataLayerRepository` on phone (thin wrapper: `putDataItem`, `sendMessage`, `getConnectedNodes`) | `app_v2/data/wear/WearableDataLayerRepository.kt` (new) | 45 min |
| 4 | Create `SendResourcesToWatchUseCase` — filters network resources, resolves credentials, serializes, calls repo | `app_v2/domain/usecase/SendResourcesToWatchUseCase.kt` (new) | 1h |
| 5 | Create `PhoneWearListenerService` — handles `/request` message, delegates to use case; handles `/ack` and shows feedback | `app_v2/service/PhoneWearListenerService.kt` (new) | 1h |
| 6 | Register `PhoneWearListenerService` in phone AndroidManifest | `app_v2/src/main/AndroidManifest.xml` | 10 min |
| 7 | Add "Send to Watch" entry point in Settings (e.g., new preference row in `WearSyncSettingsFragment` or existing `SettingsActivity`) | `app_v2/ui/settings/` | 1h |
| 8 | Create `ImportNetworkSourcesUseCase` on watch — merge by `(type, server, port, shareName)`, upsert via `NetworkSourceRepository` | `wear/domain/usecase/ImportNetworkSourcesUseCase.kt` (new) | 1h |
| 9 | Create `WatchWearListenerService` — receives Data Layer push, calls import use case, sends `/ack` | `wear/data/wear/WatchWearListenerService.kt` (new) | 1h |
| 10 | Register `WatchWearListenerService` in wear AndroidManifest | `wear/src/main/AndroidManifest.xml` | 10 min |
| 11 | Add "Sync from Phone" button to `NetworkSourcesScreen` + wire `requestSyncFromPhone()` in `NetworkSourcesViewModel` | `wear/ui/network/NetworkSourcesScreen.kt`, `NetworkSourcesViewModel.kt` | 1h |
| 12 | Handle edge cases: no paired phone, no network resources on phone, phone app not installed, payload too large (Data Layer 100KB limit) | Both sides | 1h |
| 13 | Unit tests for `SendResourcesToWatchUseCase` (credential resolution, CLOUD/LOCAL filtering) and `ImportNetworkSourcesUseCase` (merge logic) | `app_v2/src/test/`, `wear/src/test/` | 1.5h |
| 14 | Manual integration test (see §7) | — | 1.5h |
| 15 | Update `CHANGELOG.md` | `dev/CHANGELOG.md` | 5 min |

**Total estimate: 11–13h**

---

## 7. Edge Cases & Error Handling

| Case | Handling |
|------|----------|
| No paired watch connected | `getConnectedNodes()` returns empty → show "No watch paired" dialog on phone |
| Watch app not installed | Node found but capability not available → show "Install watch app first" |
| Resource has no credentials (`credentialsId == null`) | Skip (LOCAL/anonymous sources have nothing to send) |
| Decryption fails on phone (`CryptoHelper.decrypt` returns null) | Log error, skip that source, continue with others; show warning count in result |
| Payload exceeds 100KB Data Layer limit | Chunk into multiple `putDataItem` calls keyed by `/fms/network_sources/push/0`, `/push/1`, … (unlikely: 100KB holds ~500 typical sources) |
| Watch-side `upsert` conflict (same source, different password) | Last-write wins — incoming sync overwrites stored credentials |
| Watch-initiated pull when phone is not reachable | `MessageClient.sendMessage` fails → show "Phone not reachable" on watch |
| SSH private key sources (SFTP with key auth) | Include `sshPrivateKey` field in `WearNetworkSourcePayload`; watch `NetworkSource` currently lacks this field — add optional `sshPrivateKey: String?` field to `NetworkSource` |
| Stale data item (Data Layer may deliver old item on reconnect) | Check `WearSyncPayload.sentAt`; ignore if older than 24h |

---

## 8. Security Considerations

- **Channel encryption**: Wearable Data Layer is encrypted by Google Play Services (TLS + per-device keys). Eavesdropping requires compromising both the phone and the Data Layer.
- **At-rest storage**: Watch stores received credentials in `EncryptedSharedPreferences` (AES-256-GCM). Phone stores credentials in Room + `CryptoHelper` (also AES-256).
- **Transient plaintext**: Plaintext password lives in memory only for the duration of the `SendResourcesToWatchUseCase.invoke()` call. It is never written to disk in plaintext on either side.
- **Same-app requirement**: Data Layer restricts access to apps with matching signatures. A third-party app cannot receive `/fms/network_sources/push` payloads.
- **No Google account exposure**: Google account tokens (`CLOUD` type) are explicitly filtered out and never sent to the watch.
- **User-initiated only**: There is no automatic sync that could silently transmit credentials in the background.

---

## 9. Testing Matrix

| Scenario | Expected result |
|----------|----------------|
| Phone has 3 SMB + 1 SFTP + 1 LOCAL + 1 CLOUD resource | Watch receives 3 SMB + 1 SFTP; LOCAL and CLOUD filtered out |
| Tap "Send to Watch" — watch not connected | Dialog: "No watch connected" |
| Tap "Sync from Phone" on watch — phone not reachable | Toast: "Phone not reachable" |
| Repeat sync — source already exists on watch | Source updated (merge), not duplicated |
| Source with failed credential decryption | Source skipped; phone shows "1 source could not be sent" |
| Large resource set (20+ sources) | All transferred correctly; no 100KB overflow |
| Watch: newly received source can connect to SMB | Confirm browse works after sync |
| SSH key-based SFTP resource | `sshPrivateKey` field included and stored on watch |
| Stale data item replayed on reconnect (sentAt > 24h ago) | Item ignored |

---

## 10. Acceptance Criteria

- [ ] `play-services-wearable` added to `app_v2/build.gradle.kts`; phone builds cleanly
- [ ] "Send to Watch" action visible in phone Settings; triggers push to watch
- [ ] `SendResourcesToWatchUseCase` filters out `LOCAL` and `CLOUD` resources
- [ ] Credentials are resolved from `NetworkCredentialsRepository`, decrypted, and included in payload
- [ ] Watch receives and stores sources via `WatchWearListenerService` + `ImportNetworkSourcesUseCase`
- [ ] Watch "Sync from Phone" button in `NetworkSourcesScreen` triggers pull
- [ ] Merge logic: existing watch sources with same `(type, server, port, shareName)` are updated, not duplicated
- [ ] No paired watch → graceful error shown on phone
- [ ] `ImportNetworkSourcesUseCase` has unit tests covering merge / dedup logic
- [ ] `SendResourcesToWatchUseCase` has unit tests covering CLOUD/LOCAL filtering and credential resolution
- [ ] SSH private key field supported in `WearNetworkSourcePayload` and `NetworkSource`
- [ ] `CHANGELOG.md` updated
