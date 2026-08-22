# Phase 05 - Per-lens settings memory

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Introduce the store that remembers one capture set per lens id and survives a restart. No session behaviour changes in this phase - nothing reads the memory yet.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensSettingsMemory.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/CaptureSettingsStore.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 5 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensSettingsMemoryTest.kt` | New | ≤ 180 |

---

## Steps

### Step 05.1 - Add `CameraLensSettingsMemory` with its own codec

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensSettingsMemory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a class holding `Map<String, LensSettings>` keyed by lens id, where `data class LensSettings(val profile: PhotoProfile, val whiteBalanceMode: Int?, val manualIso: Int?, val manualShutterNs: Long?, val exposureCompensationIndex: Int)`. Give it `remember(lensId: String, settings: LensSettings)`, `recall(lensId: String): LensSettings?`, and `retainOnly(lensIds: Set<String>)` dropping every entry whose lens the current enumeration does not offer. Add `encode(): String` and a companion `decode(raw: String): CameraLensSettingsMemory` using a flat text form - records separated by `;`, fields inside a record by `,`, an absent nullable field written as an empty field - and make `decode` skip a malformed record rather than throwing, so one bad entry cannot cost the whole memory. Hold no Android types: this class must be unit-testable without Robolectric.

**Why:**

Strategic §3.2 requires the memory to be keyed by the lens identity the subsystem already has and to be ignored - not migrated - for a lens id the current enumeration does not list, because the lens may have left with an external camera.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class CameraLensSettingsMemory` matches exactly once.
- `Grep` - `fun recall(`, `fun remember(`, `fun retainOnly(`, `fun encode(`, `fun decode(` all present.
- `Grep` - `import android\.` returns zero hits in the file.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraLensSettingsMemory added with a flat-text codec that drops a malformed record without costing its neighbours; persisted through CaptureSettingsStore as opaque state; 5 JUnit cases pin the round trip; the new AppSettings field is registered as non-presettable rather than given an empty preset row

---

### Step 05.2 - Persist the memory through `CaptureSettingsStore`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/CaptureSettingsStore.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add `cameraLensSettings: String = ""` to `AppSettings`, a `stringPreferencesKey("camera_lens_settings")` to `CaptureSettingsStore` with the field in both `Values` and `write`, and the mapping line in `SettingsRepositoryImpl`. KDoc the field as opaque per-lens capture memory encoded by `CameraLensSettingsMemory`, not a user-editable setting - it has no settings row and must not gain one.

**Why:**

Strategic §3.2 names the existing `CaptureSettingsStore` as the home for this memory and forbids introducing a new storage layer, and requires the memory to survive an application restart.

**Verification:**

- `Grep` - `camera_lens_settings` present in `CaptureSettingsStore.kt`.
- `Grep` - `cameraLensSettings` present in all three files.
- `Grep` - `cameraLensSettings` returns zero hits under `app_v2/src/main/res/` - it is not a settings row.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraLensSettingsMemory added with a flat-text codec that drops a malformed record without costing its neighbours; persisted through CaptureSettingsStore as opaque state; 5 JUnit cases pin the round trip; the new AppSettings field is registered as non-presettable rather than given an empty preset row

---

### Step 05.3 - Round-trip test the codec

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensSettingsMemoryTest.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Test five claims: a memory holding two lenses survives `encode` then `decode` field for field; a null manual field decodes back as null and not as zero; `decode("")` yields an empty memory; a record with the wrong field count is skipped while its neighbours survive; and `retainOnly` drops exactly the lens ids not in the given set. Use plain JUnit like the sibling tests in this package.

**Why:**

The encoded string is read back from user preferences after a restart, so a codec defect would silently return the wrong capture set to a lens rather than failing visibly - and strategic §3.2 makes surviving a restart the point of this store.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `@Test` matches at least five times.
- `.\a.ps1 fu` reports `CameraLensSettingsMemoryTest` passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraLensSettingsMemory added with a flat-text codec that drops a malformed record without costing its neighbours; persisted through CaptureSettingsStore as opaque state; 5 JUnit cases pin the round trip; the new AppSettings field is registered as non-presettable rather than given an empty preset row

---

### Step 05.4 - Re-render the device-profile matrix if the new field enters it

**Files:** `docs/settings/device-profile-nonpresettable.json`
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1` for this phase's file set and read whether the device-profile-matrix gate reports the new `AppSettings` field. It does enumerate every field, and it offers two homes: a CSV row, or an entry in `docs/settings/device-profile-nonpresettable.json`. Register the field there rather than adding an empty CSV row - an empty row reads as "presettable, no value chosen yet", which is the opposite of what this field is. Do not hand-author a preset value for it in the CSV.

**Why:**

`cameraLensSettings` is opaque state rather than a user setting (step 05.2), so a device profile must never carry a value for it - but the matrix gate is repo-wide and would otherwise report the field as missing coverage.

**Verification:**

- `Grep` - `cameraLensSettings` present in `docs/settings/device-profile-nonpresettable.json` with a stated reason.
- `Grep` - `cameraLensSettings` returns zero hits in `app_v2/src/main/assets/device_profile_presets.csv`.
- `pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - CameraLensSettingsMemory added with a flat-text codec that drops a malformed record without costing its neighbours; persisted through CaptureSettingsStore as opaque state; 5 JUnit cases pin the round trip; the new AppSettings field is registered as non-presettable rather than given an empty preset row

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - a new class ships.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

The memory exists, persists and is tested, and nothing reads it yet - a lens switch still clears every intent exactly as it does today. Phase 06 is the behavioural change.

---

## Rollback Plan

Revert phase commit(s). The preference key is left behind on devices that ran the build; it is ignored by the reverted code and overwritten by the next write, so no cleanup is needed.
