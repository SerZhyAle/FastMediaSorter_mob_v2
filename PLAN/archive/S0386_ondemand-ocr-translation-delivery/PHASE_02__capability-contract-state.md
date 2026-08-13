# Phase 02 - Deliverable Capability Contract & Persistent State

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Introduce the `DeliverableCapability` contract and a persistent installed/available state that survives app update and cache clear, so base code asks the contract instead of touching ML/OCR directly (strategic Pillar A).

> **Revised 2026-06-09:** to stay runtime-safe while everything is still bundled (Phase 05 has not stripped yet), add a flavor-aware `BundledDeliverableSets` seam: `isInstalledBlocking(set) = bundled.contains(set) || payloadDir(set) exists`. Phase 02 ships a default impl returning all four sets as bundled, so the 02.5 gate is wired but inert (every set reads as installed → no behavior change). Phase 05 swaps in flavor impls (store: none bundled except via dynamic-feature; sideload/VR: TRANSLATION bundled). Uses the shared `DataStore<Preferences>` (AppModule) like `ReviewEligibilityDataStore`; blocking check uses payload-dir existence (no `runBlocking`).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSet.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableCapability.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableCapabilityRepository.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/BundledDeliverableSets.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableCapabilityRepositoryImpl.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DefaultBundledDeliverableSets.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/InstalledSetMarkerStore.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DeliveryModule.kt` | New | ≤ 60 |

---

## Steps

### Step 02.1 - Model the deliverable sets and capability states

**Files:** `domain/delivery/DeliverableSet.kt`, `domain/delivery/DeliverableCapability.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define `enum class DeliverableSet { TRANSLATION, OCR_ENGINES, AUDIO_VISUALIZATIONS, FFMPEG_DTS }` (Sets A/B/C/D from strategic §5.4). Define `enum class DeliverableCapability` state per set: `NOT_INSTALLED`, `INSTALLED`, `DISABLED_BY_USER`. No Android imports in either file.

**Verification:**

- `Grep` - `enum class DeliverableSet` matches once; values `TRANSLATION`, `OCR_ENGINES`, `AUDIO_VISUALIZATIONS`, `FFMPEG_DTS` all present.
- `Grep` - `enum class DeliverableCapability` matches once.
- `Grep` - `import android` returns zero hits in both files.

**Status:** `[x]` done

---

### Step 02.2 - Define the repository contract

**Files:** `domain/delivery/DeliverableCapabilityRepository.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Define `interface DeliverableCapabilityRepository` with: `fun stateOf(set: DeliverableSet): Flow<DeliverableCapability>`, `suspend fun markInstalled(set: DeliverableSet)`, `suspend fun markNotInstalled(set: DeliverableSet)`, `suspend fun uninstall(set: DeliverableSet)` (delete the payload directory and mark not-installed, freeing space - consumed by the Phase 08 extensions screen), `fun isInstalledBlocking(set: DeliverableSet): Boolean`. No implementation here.

**Verification:**

- `Grep` - `interface DeliverableCapabilityRepository` matches once.
- `Grep` - `fun stateOf`, `fun uninstall`, and `fun isInstalledBlocking` all present.

**Status:** `[x]` done

---

### Step 02.3 - Persist the install marker outside cache

**Files:** `data/delivery/InstalledSetMarkerStore.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Implement `InstalledSetMarkerStore` that records each set's installed flag in persistent app storage that survives update and is never touched by cache clearing - use `context.filesDir`-based markers plus a DataStore boolean per set as the authoritative flag. The actual payload directory (the `.so`/assets) lives under `context.filesDir/delivery/<set>/` (outside `cacheDir`). Provide `isInstalled(set)` that confirms both the flag and the payload directory presence.

**Verification:**

- `Grep` - `filesDir` referenced in `InstalledSetMarkerStore.kt`.
- `Grep` - `cacheDir` returns zero hits in `InstalledSetMarkerStore.kt` (must not store under cache).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 02.4 - Implement the repository and bind it

**Files:** `data/delivery/DeliverableCapabilityRepositoryImpl.kt`, `di/DeliveryModule.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Implement `DeliverableCapabilityRepositoryImpl` over `InstalledSetMarkerStore`, exposing the per-set state Flow combining install marker and the user `DISABLED_BY_USER` choice. Add Hilt `@Module DeliveryModule` (`@InstallIn(SingletonComponent::class)`) binding the impl to the interface. No flavor `BuildConfig` guards (Rule 15) - the repository is uniform across flavors.

**Verification:**

- `Grep` - `class DeliverableCapabilityRepositoryImpl` and `: DeliverableCapabilityRepository` both present.
- `Grep` - `@Module` present in `DeliveryModule.kt`.
- `Grep` - `BuildConfig.` returns zero hits in both files.

**Status:** `[x]` done

---

### Step 02.5 - Gate the recognize/translate facades behind capability

**Files:** `ui/player/helpers/RecognitionBackend.kt`, `ui/player/helpers/TranslationBackend.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Make the recognize backend consult `DeliverableCapabilityRepository.isInstalledBlocking(OCR_ENGINES)` and the translate backend consult `TRANSLATION` before invoking the heavy engines; when not installed, return a typed "capability unavailable" result instead of loading native libs. Do not show UI here (that is Phase 06) - only return the unavailable signal up to callers.

**Verification:**

- `Grep` - `DeliverableCapabilityRepository` referenced in both backend files.
- `Grep` - `OCR_ENGINES` referenced in `RecognitionBackend.kt`; `TRANSLATION` referenced in `TranslationBackend.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

The capability contract and persistent state exist. Phase 03 flips defaults to OFF; Phase 04 fills the install marker by downloading; Phase 06 reacts to the unavailable signal with the download UX.

---

## Rollback Plan

Revert phase commit(s). New contract and store are additive; backends still function (treat unavailable as a no-op path). No schema migration introduced here.

---

## Step Log

- 2026-06-09 - Steps 02.1-02.5 all PASS. New: `DeliverableSet`, `DeliverableCapability`, `DeliverableCapabilityRepository`, `BundledDeliverableSets` (domain/delivery); `InstalledSetMarkerStore`, `DeliverableCapabilityRepositoryImpl`, `DefaultBundledDeliverableSets` (data/delivery); `DeliveryModule` (di). Modified: `RecognitionBackend`/`TranslationBackend` (gate on `isInstalledBlocking(OCR_ENGINES/TRANSLATION)`), `TranslationManager` (EntryPoint adds `deliverableCapabilityRepository()`, backends now lazy + receive the repo). Design: `isInstalledBlocking = bundled.contains(set) || payloadDir exists` (no `runBlocking`); marker via shared `DataStore<Preferences>`; payload under `filesDir/delivery/<set>/` (not cache). Gate inert now (DefaultBundledDeliverableSets = all bundled) - Phase 05 flavor-refines and relocates the `BundledDeliverableSets` binding. Build: `assembleStandardDebug` BUILD SUCCESSFUL, Hilt graph OK.
