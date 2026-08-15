# Phase 01 — Data Layer Envelope

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05, Phase 06
**Steps done:** 8 / 8
**Started:** 2026-05-07
**Completed:** 2026-05-07

---

## Objective

Create `WearEventEnvelope` (versioned transport wrapper) and `WearDataLayerPaths` (path constants) on both phone and watch sides; extend both listener services with dispatch stubs for the new paths; extend `WearableDataLayerRepository` with a helper that serializes and sends envelopes.

---

## Prerequisites

- [ ] All pre-implementation blockers in INDEX.md are checked.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/spec_catalog/update.ps1 -Id S0111 -Status "In Progress"` has been run.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearEventEnvelope.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/service/WearDataLayerPaths.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/WearableDataLayerRepository.kt` | Modified | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/wear/WearableDataLayerRepositoryImpl.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt` | Modified | ≤ 110 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearEventEnvelope.kt` | New | ≤ 30 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WearDataLayerPaths.kt` | New | ≤ 50 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WatchWearListenerService.kt` | Modified | ≤ 140 |

---

## Steps

### Step 1.1 — Create `WearEventEnvelope` on phone side

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearEventEnvelope.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file at the listed path. Declare `data class WearEventEnvelope(val eventType: String, val schemaVersion: Int = 1, val sentAt: Long, val data: ByteArray)`. Add a KDoc note: "Versioned wrapper for all new Wear Data Layer event types. Existing /fms/network_sources/* paths do not use this envelope for backward compatibility."

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearEventEnvelope.kt` exists.
- `Grep` — `data class WearEventEnvelope` matches in that file.
- `Grep` — `val eventType: String` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 3/3 PASS. Files: app_v2/.../domain/model/WearEventEnvelope.kt (+28 LOC). Dev log recorded.

---

### Step 1.2 — Create `WearDataLayerPaths` constants on phone side

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/service/WearDataLayerPaths.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the listed file. Declare `object WearDataLayerPaths` with the following `const val` fields:
> - `SETTINGS_PUSH = "/fms/wear/settings"` — Data Item, phone→watch
> - `SOURCES_EXPORT = "/fms/watch/sources_export"` — Message, watch→phone
> - `PLAYBACK_STATE = "/fms/watch/playback_state"` — Data Item, watch→phone
> - `PLAYBACK_CMD = "/fms/phone/playback_cmd"` — Message, phone→watch
> - `FAVORITES_DELTA = "/fms/watch/favorites_delta"` — Message, watch→phone
> - `EVENT_SETTINGS = "SETTINGS_PUSH"`
> - `EVENT_SOURCES_EXPORT = "SOURCES_EXPORT"`
> - `EVENT_PLAYBACK_STATE = "PLAYBACK_STATE"`
> - `EVENT_PLAYBACK_CMD = "PLAYBACK_CMD"`
> - `EVENT_FAVORITES = "FAVORITES_DELTA"`
>
> Add inline comments noting direction and transport type for each path constant.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/service/WearDataLayerPaths.kt` exists.
- `Grep` — `object WearDataLayerPaths` matches.
- `Grep` — `SETTINGS_PUSH` present.
- `Grep` — `PLAYBACK_STATE` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 4/4 PASS. Files: app_v2/.../service/WearDataLayerPaths.kt (+40 LOC). Dev log recorded.

---

### Step 1.3 — Extend `WearableDataLayerRepository` with envelope helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/WearableDataLayerRepository.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Add one new method to the existing `WearableDataLayerRepository` interface:
> `suspend fun putEnvelopeDataItem(path: String, envelope: WearEventEnvelope)`.
> The existing three methods (`getConnectedNodes`, `putDataItem`, `sendMessage`) are unchanged.

**Verification:**

- `Grep` — `fun putEnvelopeDataItem` present in `WearableDataLayerRepository.kt`.
- `Grep` — `WearEventEnvelope` imported in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 2/2 PASS. Files: app_v2/.../domain/repository/WearableDataLayerRepository.kt (+4 LOC). Dev log recorded.

---

### Step 1.4 — Implement `putEnvelopeDataItem` in `WearableDataLayerRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/wear/WearableDataLayerRepositoryImpl.kt`
**Depends on:** Step 1.3

**Prompt for developer:**

> In the existing `WearableDataLayerRepositoryImpl`, inject `Gson` via `@Inject constructor`. Add the `putEnvelopeDataItem` override: serialize `envelope` with `gson.toJson(envelope).toByteArray(Charsets.UTF_8)`, then call the existing `putDataItem(path, bytes)`.
>
> Add `Timber.d("S0111: putEnvelopeDataItem path=$path type=${envelope.eventType}")` at the start of the new method.

**Verification:**

- `Grep` — `override suspend fun putEnvelopeDataItem` present in `WearableDataLayerRepositoryImpl.kt`.
- `Grep` — `Timber.d("S0111:` present in that file.
- `Grep` — `gson.toJson(envelope)` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 4/4 PASS. Files: app_v2/.../data/wear/WearableDataLayerRepositoryImpl.kt (+8 LOC). Dev log recorded.

---

### Step 1.5 — Update `PhoneWearListenerService` dispatch table

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> In the existing `PhoneWearListenerService.onMessageReceived`, extend the `when(event.path)` block with stub handlers for the three new watch→phone message paths from `WearDataLayerPaths`:
> - `WearDataLayerPaths.SOURCES_EXPORT` → call `handleSourcesExport(event.data)` (stub: `Timber.d("S0111: sources export received — handler not yet implemented")`)
> - `WearDataLayerPaths.FAVORITES_DELTA` → call `handleFavoritesDelta(event.data)` (stub)
>
> Additionally, override `onDataChanged(events: DataEventBuffer)` as a stub dispatcher: check event path against `WearDataLayerPaths.PLAYBACK_STATE` and log receipt. Do not parse yet — stub only.
>
> Both stub methods must be `private fun` in the same class.

**Verification:**

- `Grep` — `WearDataLayerPaths.SOURCES_EXPORT` present in `PhoneWearListenerService.kt`.
- `Grep` — `WearDataLayerPaths.FAVORITES_DELTA` present in that file.
- `Grep` — `WearDataLayerPaths.PLAYBACK_STATE` present in that file.
- `Grep` — `fun handleSourcesExport` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 5/5 PASS. Files: app_v2/.../service/PhoneWearListenerService.kt (+22 LOC). Dev log recorded.

---

### Step 1.6 — Create `WearEventEnvelope` mirror on watch side

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearEventEnvelope.kt`
**Depends on:** — start of phase (parallel with 1.1)

**Prompt for developer:**

> Create the listed file. Declare an identical `data class WearEventEnvelope(val eventType: String, val schemaVersion: Int = 1, val sentAt: Long, val data: ByteArray)` in package `com.sza.fastmediasorter.wear.domain.model`. Add the same KDoc as the phone-side copy regarding backward compatibility with existing paths.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearEventEnvelope.kt` exists.
- `Grep` — `data class WearEventEnvelope` matches in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 2/2 PASS. Files: wear/.../domain/model/WearEventEnvelope.kt (+28 LOC). Dev log recorded.

---

### Step 1.7 — Create `WearDataLayerPaths` mirror on watch side

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WearDataLayerPaths.kt`
**Depends on:** — start of phase (parallel with 1.2)

**Prompt for developer:**

> Create the listed file. Declare `object WearDataLayerPaths` in package `com.sza.fastmediasorter.wear.data.wear` with identical constants to the phone-side `WearDataLayerPaths` in Step 1.2. All ten constants (`SETTINGS_PUSH`, `SOURCES_EXPORT`, `PLAYBACK_STATE`, `PLAYBACK_CMD`, `FAVORITES_DELTA`, plus the five `EVENT_*` constants) must be present with the same string values.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WearDataLayerPaths.kt` exists.
- `Grep` — `object WearDataLayerPaths` matches in that file.
- `Grep` — `PLAYBACK_CMD` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 3/3 PASS. Files: wear/.../data/wear/WearDataLayerPaths.kt (+42 LOC). Dev log recorded.

---

### Step 1.8 — Update `WatchWearListenerService` dispatch table

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WatchWearListenerService.kt`
**Depends on:** Step 1.7

**Prompt for developer:**

> In the existing `WatchWearListenerService`:
>
> 1. Extend `onDataChanged` to also handle `WearDataLayerPaths.SETTINGS_PUSH`: extract `payload` byte array and call `handleSettingsPush(payloadBytes)` (stub: `Timber.d("S0111: settings push received — handler not yet implemented")`).
>
> 2. Override `onMessageReceived(event: MessageEvent)`: handle `WearDataLayerPaths.PLAYBACK_CMD` → call `handlePlaybackCommand(event.data)` (stub). All other paths: log and ignore.
>
> Both stub methods must be `private fun` in the same class.

**Verification:**

- `Grep` — `WearDataLayerPaths.SETTINGS_PUSH` present in `WatchWearListenerService.kt`.
- `Grep` — `WearDataLayerPaths.PLAYBACK_CMD` present in that file.
- `Grep` — `fun handleSettingsPush` present in that file.
- `Grep` — `fun handlePlaybackCommand` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 5/5 PASS. Files: wear/.../data/wear/WatchWearListenerService.kt (+26 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x] done`.
- [x] Project compiles — `:app_v2:assembleStandardDebug` and `:wear:assembleDebug` both BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/wear.jsonl` regenerated — 56 records.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated — 952 records.

---

## Handoff Notes to Next Phase

- `WearEventEnvelope` exists on both sides and is JSON-serializable via Gson.
- `WearDataLayerPaths` constants are identical on both sides (string values guaranteed to match).
- `WearableDataLayerRepository.putEnvelopeDataItem` is ready for use in Phases 02, 05.
- Both listener services have stub dispatch for all new paths — fill each stub in its respective phase.
- Phase 02, 03, 04, 05, 06 may proceed in any order after this phase is done. Phase 04 may start in parallel from the beginning (it has no dependency on Phase 01).

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface changed, all stub methods do nothing.
