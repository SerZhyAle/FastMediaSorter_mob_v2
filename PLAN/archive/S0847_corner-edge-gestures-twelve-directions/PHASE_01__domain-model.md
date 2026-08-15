# Phase 01 - domain-model

**Goal:** Introduce the `ScreenshotGestureZone` enum and extend `AppSettings` with 4 zone-enable toggles + 12 zone-scoped action slots, replacing the legacy 3 flat action fields. Keep the field naming symmetric across zones so persistence, dispatch, and UI map mechanically.

**Depends on:** none.
**Source set:** `src/main`.

---

## Steps

### [ ] 01.1 - Add the zone enum

- Create `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureZone.kt`:
  ```kotlin
  package com.sza.fastmediasorter.domain.model

  /** One of the four edge bands a screenshot gesture can start from (2 left, 2 right; 10-40% / 60-90% of safe height). */
  enum class ScreenshotGestureZone {
      LEFT_TOP,
      LEFT_BOTTOM,
      RIGHT_TOP,
      RIGHT_BOTTOM
  }
  ```
- **Verification:** file exists; `ScreenshotGestureZone.entries.size == 4`.

### [ ] 01.2 - Extend AppSettings with toggles + 12 slots

- In `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, replace the 3 legacy fields (`screenshotGestureActionDown/Right/Up`, lines ~197-199) with the symmetric zone-scoped set. Keep `screenshotGestureStripVisible` and `gestureOverlayEnabled` untouched.
  ```kotlin
  // S0847: four independently-toggleable edge bands, each with the DOWN/RIGHT/UP triple (up to 12 gestures).
  val screenshotGestureZoneLeftTopEnabled: Boolean = true,
  val screenshotGestureZoneLeftBottomEnabled: Boolean = false,
  val screenshotGestureZoneRightTopEnabled: Boolean = false,
  val screenshotGestureZoneRightBottomEnabled: Boolean = false,
  val screenshotGestureLeftTopDown: ScreenshotGestureAction = ScreenshotGestureAction.SILENT_SCREENSHOT,
  val screenshotGestureLeftTopRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureLeftTopUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureLeftBottomDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureLeftBottomRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureLeftBottomUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureRightTopDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureRightTopRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureRightTopUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureRightBottomDown: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureRightBottomRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  val screenshotGestureRightBottomUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE,
  ```
- Add a helper on `AppSettings` (or a top-level extension in the same file) to resolve a slot by `(zone, direction)` and to read a zone's enabled flag - dispatch and UI both need it:
  ```kotlin
  fun screenshotGestureAction(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection): ScreenshotGestureAction
  fun screenshotGestureZoneEnabled(zone: ScreenshotGestureZone): Boolean
  ```
- **Verification:** `AppSettings` compiles; the two helpers return every zone/direction combination via an exhaustive `when`.

### [ ] 01.3 - Update the seed use case

- In `SeedDefaultGestureBindingsUseCase.kt`, seed only the LEFT_TOP zone from the legacy triple; leave the other three zones at their disabled/DO_NOT_USE defaults:
  ```kotlin
  current.copy(
      screenshotGestureLeftTopUp = ScreenshotGestureAction.OPEN_PANEL,
      screenshotGestureLeftTopRight = ScreenshotGestureAction.OPEN_IN_DRAW,
      screenshotGestureLeftTopDown = ScreenshotGestureAction.SILENT_SCREENSHOT,
  )
  ```
- Update the KDoc: "Seeds the LEFT_TOP edge-band gesture bindings.." (was "three left-edge").
- **Verification:** file compiles; no reference to the removed `screenshotGestureActionUp/Right/Down` remains.

### [ ] 01.4 - Sweep remaining legacy-field references

- Grep the whole tree for `screenshotGestureActionDown`, `screenshotGestureActionRight`, `screenshotGestureActionUp`. Expected callers: `ScreenshotSettingsStore` (Phase 02), `ScreenshotGestureActionDispatcher` (Phase 03), `DeviceProfilePresetApplier` / `ApplyProfilePresetUseCaseTest`, `OperationsGesturesManager` (Phase 05). Update the preset applier + its test to the new field names now (map any preset that set the legacy triple onto LEFT_TOP slots).
- **Verification:** `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1` not needed; `Grep screenshotGestureActionDown` returns only Phase 02/03/05 files still pending, no `src/main` domain/preset leftovers.

---

## Phase Done Criteria

- [ ] `ScreenshotGestureZone` enum exists with 4 entries.
- [ ] `AppSettings` carries 4 toggles + 12 slots + the two resolver helpers; legacy 3 fields gone.
- [ ] Seed + preset applier reference the new fields only.
- [ ] `.\a.ps1 fk` (standard Kotlin compile) passes for the domain layer once Phase 02/03 land (or compile in isolation deferred to Phase 03 build gate).
