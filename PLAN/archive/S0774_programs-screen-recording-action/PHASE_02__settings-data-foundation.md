# Phase 02 - Settings data foundation

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05, Phase 06, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-06-28
**Completed:** 2026-06-28

> **Phase Step Log (2026-06-28):** 3 AppSettings fields added; 3 DataStore keys + Values + read/write in CaptureSettingsStore; mapping in SettingsRepositoryImpl; `resolveScreenRecordingDestination` → Downloads in CaptureDestinationPolicy. Greps 3/3/1/3/1 PASS. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Objective

Add the three persisted settings fields (`screenRecordingEnabled`, `screenRecordingDestinationResourceId`, `screenRecordingDisclosureAccepted`) and a destination resolver, by analogy with microphone recording. No UI, no capture behaviour yet.

---

## Prerequisites

- [ ] None - first foundation phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | +3 fields |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/CaptureSettingsStore.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | +mapping |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/CaptureDestinationPolicy.kt` | Modified | +1 resolver |

---

## Steps

### Step 02.1 - Add AppSettings fields

**Files:** `AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three fields next to the microphone-recording fields, following the direct-toggle precedent (`micRecordingEnabled`, not the inverted `disableVideoCapture`):
> `val screenRecordingEnabled: Boolean = false`,
> `val screenRecordingDestinationResourceId: String? = null`,
> `val screenRecordingDisclosureAccepted: Boolean = false`.

**Verification:**

- `Grep` - all three property names present in `AppSettings.kt`, each exactly once.

**Status:** `[x] done`

---

### Step 02.2 - Persist in CaptureSettingsStore

**Files:** `CaptureSettingsStore.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add three DataStore keys mirroring the mic-recording keys:
> `screen_recording_enabled` (boolean), `screen_recording_destination_resource_id` (string), `screen_recording_disclosure_accepted` (boolean).
> Add the three fields to `Values`; in `read()` use `?: false` for the booleans and the naked nullable read for the string; in `write()` set the booleans directly and use `setOrRemove(..)` for the nullable destination id.

**Verification:**

- `Grep` - `screen_recording_enabled`, `screen_recording_destination_resource_id`, `screen_recording_disclosure_accepted` each present once.
- `Grep` - `setOrRemove(KEY_SCREEN_RECORDING_DESTINATION_RESOURCE_ID` present in `write()`.

**Status:** `[x] done`

---

### Step 02.3 - Map the fields in SettingsRepositoryImpl

**Files:** `SettingsRepositoryImpl.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In the DataStore→`AppSettings` mapping where `CaptureSettingsStore.read(...)` results are unpacked, assign the three new `AppSettings` fields from the `CaptureSettingsStore.Values`. `saveSettings()` already routes through `CaptureSettingsStore.write(...)`; confirm no extra wiring is needed there.

**Verification:**

- `Grep` - `screenRecordingEnabled = ` and `screenRecordingDestinationResourceId = ` and `screenRecordingDisclosureAccepted = ` present in `SettingsRepositoryImpl.kt`.

**Status:** `[x] done`

---

### Step 02.4 - Add the destination resolver

**Files:** `CaptureDestinationPolicy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `fun resolveScreenRecordingDestination(selectedResource: MediaResource?): File = usableTargetDirectory(selectedResource) ?: publicDownloadsDirectory()`. Empty/invalid selection → public Downloads (strategic §11.5). Update the KDoc bullet list to mention screen recordings → Downloads.

**Verification:**

- `Grep` - `fun resolveScreenRecordingDestination` present once.
- `Grep` - that body references `publicDownloadsDirectory()`.
- `.\a.ps1 fk` compiles.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] All four steps `[x]`.
- [ ] `.\a.ps1 fk` (Kotlin compile) green.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the four files.

---

## Handoff Notes to Next Phase

`AppSettings.screenRecordingEnabled / screenRecordingDestinationResourceId / screenRecordingDisclosureAccepted` are now readable/writable via the settings flow, and `CaptureDestinationPolicy.resolveScreenRecordingDestination(..)` resolves the save folder with the Downloads fallback. Phases 04/05/06 consume these.

---

## Rollback Plan

Revert the phase commit - additive nullable/false-default fields; absent keys read as defaults, no migration.
