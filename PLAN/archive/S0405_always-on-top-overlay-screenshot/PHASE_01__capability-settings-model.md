# Phase 01 - Capability contract + settings model

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Introduce the flavor-agnostic capability contract (`ScreenGestureOverlayController`) as an injectable multibinding set, plus the three new persisted settings fields and their DataStore store. No overlay, capture, or UI yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] Strategic §6 items blocking this phase: none.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenGestureOverlayController.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/ScreenGestureOverlayModule.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 760 |

> No `BuildConfig.IS_*` / `SUPPORT_*` flavor guards in any `src/main` file (CLAUDE.md Rule 14). The contract is flavor-agnostic; only the binding (Phase 04) is flavor-specific.

---

## Steps

### Step 01.1 - Define the capability contract interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenGestureOverlayController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `ScreenGestureOverlayController` in `src/main`. Methods: `fun isOverlayPermissionGranted(context: Context): Boolean`, `fun setEnabled(enabled: Boolean)`, `fun isEnabled(): Boolean`. This is the flavor-agnostic seam; the real implementation lands in the `noLegal` source set in Phase 04. No flavor guards, no Android overlay code here - declaration only.

**Verification:**

- `Glob` - file exists.
- `Grep` - `interface ScreenGestureOverlayController` matches exactly once.
- `Grep` - `fun setEnabled` and `fun isEnabled` both present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenGestureOverlayController.kt (+8 LOC). Dev log recorded.

---

### Step 01.2 - Declare the multibinding set in a src/main Hilt module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/ScreenGestureOverlayModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a Hilt `@Module @InstallIn(SingletonComponent::class)` named `ScreenGestureOverlayModule` in `src/main`. Declare `@Multibinds abstract fun controllers(): Set<ScreenGestureOverlayController>`. This guarantees an injectable (possibly empty) `Set<ScreenGestureOverlayController>` on every flavor; flavors with no contributor (standard/lite/photos/legacy/vr) get an empty set. The `noLegal` contributor is added in Phase 04. Consumers (Phase 05) take `.firstOrNull()`; non-empty = capability available.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@Multibinds` present.
- `Grep` - `Set<ScreenGestureOverlayController>` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/di/ScreenGestureOverlayModule.kt (+14 LOC). Dev log recorded.

---

### Step 01.3 - Add the three settings fields to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three fields to the production `AppSettings` data class (the one in `domain/model`, NOT the legacy `data/local/preferences/SettingsManager.kt` shape): `gestureOverlayEnabled: Boolean = false`, `screenshotGestureDownEnabled: Boolean = true`, `screenshotDestinationResourceId: String? = null`. Mirror the existing `cameraPhotosDestinationResourceId: String?` field for the nullable resource id (resource id stored as String).

**Verification:**

- `Grep` - `gestureOverlayEnabled` present in `AppSettings.kt`.
- `Grep` - `screenshotGestureDownEnabled` present.
- `Grep` - `screenshotDestinationResourceId` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt (+3 LOC). Dev log recorded.

---

### Step 01.4 - Add ScreenshotSettingsStore (read/write of new keys)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `ScreenshotSettingsStore` mirroring `CaptureSettingsStore` in the same package: a `Values` data class for the three new fields, `read(prefs: Preferences): Values` and `write(prefs: MutablePreferences, settings: AppSettings)`. Use `booleanPreferencesKey` for the two toggles and `stringPreferencesKey` for the destination id; persist the nullable id with the existing `setOrRemove` helper from `SettingsPrefExtensions.kt`. Defaults must match Step 01.3.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenshotSettingsStore` matches once.
- `Grep` - `fun read` and `fun write` both present.
- `Grep` - `setOrRemove` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt (+39 LOC). Dev log recorded.

---

### Step 01.5 - Wire ScreenshotSettingsStore into SettingsRepositoryImpl

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `SettingsRepositoryImpl`, call `ScreenshotSettingsStore.read(...)` inside `getSettings()` mapping and `ScreenshotSettingsStore.write(...)` inside the `updateSettings()` `dataStore.edit { }` block, exactly as `CaptureSettingsStore` is wired. Map the three new fields into the constructed `AppSettings`. Do not inline the keys - keep them in the store to hold `SettingsRepositoryImpl` under the 1500 LOC limit.

**Verification:**

- `Grep` - `ScreenshotSettingsStore` referenced in `SettingsRepositoryImpl.kt` at least twice (read + write).
- `Grep -n "Log\.d\("` in `SettingsRepositoryImpl.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (standard + noLegal debug).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Injecting `Set<ScreenGestureOverlayController>` resolves to an empty set on `standard` (no contributor yet).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`ScreenGestureOverlayController` interface and `Set<...>` injection point exist. `AppSettings.screenshotDestinationResourceId` is persisted - Phase 02 consumes it. No binding contributes to the set yet (Phase 04 adds the noLegal one).

---

## Rollback Plan

Revert phase commit(s) - no data migration (DataStore keys are additive with defaults) and no user-facing surface changed.
