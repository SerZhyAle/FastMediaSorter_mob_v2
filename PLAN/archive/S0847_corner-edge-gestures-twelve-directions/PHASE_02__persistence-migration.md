# Phase 02 - persistence-migration

**Goal:** Persist the 4 toggles + 12 slots in `ScreenshotSettingsStore`, migrating the 3 legacy DataStore keys into the LEFT_TOP zone on read so existing users keep their bindings without a Room/version bump.

**Depends on:** 01.
**Source set:** `src/main`.

---

## Steps

### [ ] 02.1 - Add preference keys

- In `ScreenshotSettingsStore.kt`, add 4 boolean keys + 12 string keys following the existing `KEY_SCREENSHOT_GESTURE_ACTION_*` naming, e.g. `KEY_GESTURE_ZONE_LEFT_TOP_ENABLED`, `KEY_GESTURE_LEFT_TOP_DOWN`, .. `KEY_GESTURE_RIGHT_BOTTOM_UP`.
- Keep the 3 legacy keys (`KEY_SCREENSHOT_GESTURE_ACTION_DOWN/RIGHT/UP`) declared for migration read only; do not write them going forward.
- **Verification:** 16 new keys declared; legacy 3 keys still declared.

### [ ] 02.2 - Read with legacy migration for LEFT_TOP

- In the read/map block, populate all 4 toggles and 12 slots. For the three LEFT_TOP slots, fall back to the legacy key when the new key is absent:
  ```kotlin
  screenshotGestureLeftTopDown = ScreenshotGestureAction.fromName(
      preferences[KEY_GESTURE_LEFT_TOP_DOWN] ?: preferences[KEY_SCREENSHOT_GESTURE_ACTION_DOWN],
      ScreenshotGestureAction.SILENT_SCREENSHOT
  ),
  // .. same legacy fallback for LEFT_TOP right/up; other 9 slots read their own key only, default DO_NOT_USE
  screenshotGestureZoneLeftTopEnabled = preferences[KEY_GESTURE_ZONE_LEFT_TOP_ENABLED] ?: true,
  screenshotGestureZoneLeftBottomEnabled = preferences[KEY_GESTURE_ZONE_LEFT_BOTTOM_ENABLED] ?: false,
  // .. right top / right bottom default false
  ```
- **Verification:** a preferences map containing only the 3 legacy keys yields those values in the LEFT_TOP slots and LEFT_TOP enabled=true; the other zones default disabled/DO_NOT_USE.

### [ ] 02.3 - Write all new keys

- In the write block, persist the 4 toggles + 12 slots (`.name` for actions). Remove the 3 legacy writes (migration is read-only; once the user saves, new keys own the value).
- **Verification:** `.\a.ps1 fk` compiles the data layer; a save-then-read round-trip preserves every zone toggle + slot (unit test in `SettingsRepositoryImpl`/store test if one exists, else assert via the resolver helpers).

---

## Phase Done Criteria

- [ ] 16 new keys read + written; legacy 3 read-only for LEFT_TOP fallback.
- [ ] Existing-user bindings (legacy keys) surface in LEFT_TOP with the zone enabled.
- [ ] Data layer compiles.
