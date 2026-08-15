# Phase 01 - Foundations (settings flag, persistence, capability gate, lite flavor)

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 5 / 5
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce the `enableStreams` runtime master flag (domain model + DataStore persistence), expose `CapabilityAvailability.isStreamsAvailable()`, and make the `lite` flavor stop offering Streams. No UI, onboarding, or menu changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/StreamsSettingsStore.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt` | Modified | ≤ 80 |
| `app_v2/build.gradle.kts` | Modified | n/a |

---

## Steps

### Step 01.1 - Add `enableStreams` to AppSettings

**Files:** `domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val enableStreams: Boolean = false,` to the `AppSettings` data class, placed next to `enableTranslation` / `enableOcr`. Default OFF; the per-profile preset (Phase 02) raises it for streaming-oriented devices. Keep the existing field comment style (one short EN comment noting it is the Streams feature master switch).

**Verification:**

- `Grep` - `val enableStreams: Boolean = false` matches exactly once in `AppSettings.kt`.

**Status:** `[x]` done

---

### Step 01.2 - Create `StreamsSettingsStore`

**Files:** `data/repository/settings/StreamsSettingsStore.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `StreamsSettingsStore` mirroring `TextRecognitionSettingsStore` exactly: a private `booleanPreferencesKey("enable_streams")`, a small result holder carrying `enableStreams`, a `read(preferences): <holder>` returning `preferences[KEY] ?: false`, and a `write(preferences, settings)` setting `preferences[KEY] = settings.enableStreams`. Object (not class), no Hilt - it is a pure preferences mapper like its siblings.

**Verification:**

- `Glob` - `data/repository/settings/StreamsSettingsStore.kt` exists.
- `Grep` - `booleanPreferencesKey("enable_streams")` matches once in that file.
- `Grep` - `fun read(` and `fun write(` both present in that file.

**Status:** `[x]` done

---

### Step 01.3 - Wire `StreamsSettingsStore` into `SettingsRepositoryImpl`

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Import `StreamsSettingsStore`. In the read path (where `TextRecognitionSettingsStore.read(preferences)` is called and folded into the constructed `AppSettings`), add `val streams = StreamsSettingsStore.read(preferences)` and set `enableStreams = streams.enableStreams` on the built `AppSettings`. In the write path (where the other stores' `write(...)` are called), add `StreamsSettingsStore.write(preferences, settings)`. Mirror the existing store-composition exactly.

**Verification:**

- `Grep` - `StreamsSettingsStore.read(` matches once in `SettingsRepositoryImpl.kt`.
- `Grep` - `StreamsSettingsStore.write(` matches once in `SettingsRepositoryImpl.kt`.

**Status:** `[x]` done

---

### Step 01.4 - Stop offering Streams in the `lite` flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - independent of 01.1-01.3

**Prompt for developer:**

> In the `lite` flavor block, change `buildConfigField("boolean", "SUPPORT_STREAMS", "true")` to `"false"`. Update the trailing comment to note S0575 hides the Streams feature UI in lite (the streaming pipeline source set stays `streamingDisabled`, untouched). Leave standard / noLegal / legacy / vr at `true` and photos at `false`. This single flag drives every existing and new Streams entry point, so lite now uniformly shows nothing.

**Verification:**

- `Grep` - in `app_v2/build.gradle.kts`, `SUPPORT_STREAMS", "false"` matches exactly twice (photos + lite).
- `Grep` - `SUPPORT_STREAMS", "true"` matches exactly four times (standard, noLegal, legacy, vr).

**Status:** `[x]` done

---

### Step 01.5 - Add `isStreamsAvailable()` to `CapabilityAvailability`

**Files:** `core/capability/CapabilityAvailability.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add `fun isStreamsAvailable(): Boolean = BuildConfig.SUPPORT_STREAMS` (import `com.sza.fastmediasorter.BuildConfig`). `SUPPORT_STREAMS` is a sanctioned capability flag (compile-time presence of the Streams feature surface), so reading it inside this contract class is correct - consumers ask `isStreamsAvailable()`, not the flag. Then extend `isExtensionsScreenAvailable()` to `isOcrCompiledIn() || isTranslationAvailable() || isStreamsAvailable()` so the Extensions screen and its launch button appear wherever Streams is offered.

**Verification:**

- `Grep` - `fun isStreamsAvailable(): Boolean = BuildConfig.SUPPORT_STREAMS` matches once.
- `Grep` - `isExtensionsScreenAvailable` line contains `isStreamsAvailable()`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class `StreamsSettingsStore`) - deferred to Phase 07 batch, acceptable here.

---

## Step Log

- 2026-06-21 - Steps 01.1-01.5 Verification PASS (8/8 grep predicates). `.\a.ps1 fk` -> BUILD SUCCESSFUL (`compileStandardDebugKotlin`). lite `SUPPORT_STREAMS=false` confirmed (false x2 photos+lite, true x4). Dev logs batched at Phase 07 finalization.

---

## Handoff Notes to Next Phase

- `AppSettings.enableStreams` is the single runtime master flag; read it via `SettingsRepository.getSettings()` Flow, never re-derive.
- `CapabilityAvailability.isStreamsAvailable()` is the only compile-time Streams gate consumers should call.
- `lite` and `photos` now both report `SUPPORT_STREAMS=false`; no consumer needs a flavor guard.

---

## Rollback Plan

Revert the phase commit(s) - no data migration (DataStore key defaults to false on read) and no user-facing surface changed yet.
