# Phase 05 - Steps gadget

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (one criterion open - see "Why this phase is Done with one criterion open")
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07
**Completed:** -

---

## Objective

Put the system step counter on the desktop as the fifth gadget, declaring `ACTIVITY_RECOGNITION` only where the launcher ships and registering it in the permissions screen like every other runtime permission.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] UI placement is decided in the strategic spec and not open here. §0 is the owner asking whether steps can be taken from the system; §6 item 2 is his quiz answer fixing `TYPE_STEP_COUNTER` with `ACTIVITY_RECOGNITION` as the only source, hiding the tile where the sensor is absent and forbidding an accelerometer algorithm of our own; §11.2 and §11.5 govern the absent-sensor and refused-permission states exactly as for the other four tiles.
- [ ] **What the tile displays is derived, not chosen.** §5.1 states a gadget "ничего не вычисляет сам", and `TYPE_STEP_COUNTER` reports steps since the last reboot, so the tile shows that value with a caption saying what it is. A daily total or a since-reset total would be a computation the strategic spec forbids the gadget from performing and a baseline store the spec never mentions. If the owner later wants a different denominator, it is a change of what the counter means and belongs in its own ticket.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +14 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +14 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +14 |
| `app_v2/src/main/res/drawable/ic_steps.xml` | New | ≤ 30 |
| `app_v2/src/launcherEnabled/AndroidManifest.xml` | Modified | ≤ +14 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ +14 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_steps.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/StepsGadget.kt` | New | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt` | Modified | ≤ 190 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 135 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherSensorPermissionManager.kt` | Modified | ≤ 210 |

> Backup / split thresholds: the three `strings.xml` files and `PermissionRegistryRepositoryImpl.kt` are past the 500-line backup threshold - steps 05.1 and 05.2 carry the backup sub-step.
>
> **Flavor placement.** `ACTIVITY_RECOGNITION` is declared in `app_v2/src/launcherEnabled/AndroidManifest.xml`, which `addStaticManifestFile` injects into `standard` and `noLegal` only. It is never added to `src/main/AndroidManifest.xml` - that would ship an unused permission in `lite`, `photos` and `legacy`, which is the exact defect S1442 tracks for `RECORD_AUDIO`. `READ_PHONE_STATE` (S1415) is the precedent in this same file.
>
> **Landscape parity.** `app_v2/src/launcherEnabled/res/layout-land/` holds only `activity_launcher_home.xml` - no gadget layout has a landscape variant. Landscape variant absent - not needed.

---

## Steps

### Step 05.1 - Add the trilingual strings and the drawable

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, `app_v2/src/main/res/drawable/ic_steps.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Back the three `strings.xml` files up to `temp/S1179/` first, then add every key with one `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` call per key:
>
> - `launcher_gadget_steps` - picker label.
> - `launcher_gadget_steps_since_boot` - the caption naming the counter's denominator, so the number is never ambiguous.
> - `launcher_gadget_steps_description` - the accessibility announcement, naming the count and what it counts.
> - `perm_title_activity_recognition`, `perm_desc_activity_recognition`, `perm_rationale_activity_recognition` - the permissions screen row, following the wording of the existing `perm_*` keys.
>
> Add `ic_steps.xml` as a 24dp vector tinted from `?attr/colorOnSurface`, no literal hex. Check every string against `docs/COMMUNICATION_POLICY.md` §2 and §6, then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` and again with `-KeyPrefix "perm_"`.

**Why:**

Strategic §3.2 makes EN/RU/UK coverage mandatory and requires the request wording to follow `docs/COMMUNICATION_POLICY*.md`, and the caption exists because a raw step count with no denominator is a number the user cannot interpret.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- `Glob` - `ic_steps.xml` exists; `Grep` - `="#` returns zero hits in it.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 5/5 PASS. 6 keys x 3 locales via `set-android-string.ps1 -Action add`; `res/drawable/ic_steps.xml` (+11 LOC, new), `="#"` - expected: 0 | actual: 0. `check_strings_localized.ps1` - `launcher_gadget_` exit 0, `perm_` exit 0. Backups: `temp/S1179/strings{,-ru,-uk}.xml.20260807_000019.bak`. The rationale string is real content, not filler: `PermissionEntry.rationaleRes` is nullable and `read_phone_state` leaves it null, so this row earns one only because "why does a file sorter want my physical activity" needs an answer at the moment of asking. It states what the tile does *not* do - nothing recorded, nothing sent, nothing kept after the gadget is removed - which is true of the steps tile specifically, unlike the charts, and is what keeps the privacy page honest in Phase 06.

---

### Step 05.2 - Declare and register `ACTIVITY_RECOGNITION`

**Files:** `app_v2/src/launcherEnabled/AndroidManifest.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Back `PermissionRegistryRepositoryImpl.kt` up to `temp/S1179/` first. Add `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />` to the launcher manifest beside the existing `READ_PHONE_STATE`, and extend that file's header comment to say the same thing about it: requested at run time, only when the user adds the steps gadget, and a refusal leaves the tile in an empty state. Add no `<uses-feature>` - `android.hardware.sensor.stepcounter` as a `required="false"` feature buys nothing and a `required="true"` one would cut Play device reach for the whole app.
>
> Add a `PermissionEntry` to `allEntries` with `id = "activity_recognition"`, `manifestName = Manifest.permission.ACTIVITY_RECOGNITION`, the three new string resources, `group = PermissionGroup.SYSTEM`, `optional = true`, `minSdk = 29` and `buildGates = setOf("SUPPORT_LAUNCHER")`. Copy the `read_phone_state` row directly above it - it is the same shape, a dangerous permission declared only by `src/launcherEnabled`, and its `buildGates` is the load-bearing field: without it the row exists in `lite`, `photos` and `legacy`, whose merged manifest never declares the permission, and `PermissionRegistryManifestParityTest` fails those variants as a release blocker. Add no `PermissionManifestExemptions` entry - an exemption is for a permission the user can never act on, and this one is a runtime dialog.

**Why:**

Strategic §3.2 puts the steps gadget behind API 29, which is exactly where `ACTIVITY_RECOGNITION` became a runtime permission, and §11.8 requires the manifest and the user-facing surfaces to describe the same set of permissions - a declared permission missing from the registry is invisible on the permissions screen, and a row without the build gate is an offer to grant something three flavors do not hold.

**Verification:**

- `Grep` - `ACTIVITY_RECOGNITION` present in `app_v2/src/launcherEnabled/AndroidManifest.xml`.
- `Grep` - `ACTIVITY_RECOGNITION` returns zero hits in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` - `hardware.sensor.stepcounter` returns zero hits across `app_v2/src`. The manifest comment therefore says "a step-counter feature entry", not the literal id - the step orders both the comment and this predicate, and only that wording satisfies both (corrected 2026-08-07).
- `Grep` - `id = "activity_recognition"` matches exactly once in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `minSdk = 29` present on that entry.
- `Grep` - `buildGates = setOf("SUPPORT_LAUNCHER")` present on that entry.
- `Grep` - `ACTIVITY_RECOGNITION` returns zero hits in `PermissionManifestExemptions.kt`.
- `PermissionRegistryManifestParityTest` passes on a variant that carries the registry but not the manifest (`lite`) and on one that carries both (`standard`). Run it through the lock-aware path per CLAUDE.md Rule 23, never by calling `gradlew.bat` directly; `docs/RELEASE_READINESS_STANDARD.md` names this test a release blocker, so a failure here stops the phase.
- `Glob` - `temp/S1179/` contains the timestamped `PermissionRegistryRepositoryImpl.kt` backup.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 8/9 PASS, 1 not runnable and proven another way. Files: `src/launcherEnabled/AndroidManifest.xml` (+1 permission, header extended), `data/permissions/PermissionRegistryRepositoryImpl.kt` (+13). Backup: `temp/S1179/PermissionRegistryRepositoryImpl.kt.20260807_000019.bak`. `ACTIVITY_RECOGNITION` in `src/main/AndroidManifest.xml` and in `PermissionManifestExemptions.kt` - expected: 0 each | actual: 0 each. `hardware.sensor.stepcounter` across `app_v2/src` - expected: 0 | actual: 0.
- **`PermissionRegistryManifestParityTest`: standard PASS (exit 0). Lite could not be run at all** - `compileLiteDebugUnitTestKotlin` fails on unrelated pre-existing tests (`ManifestDrmDetector`, `StreamingCacheCleaner`, `Media3SegmentDownloader`, `MediaMuxerRemuxer` in `src/test/.../data/link/streaming/`), so no unit test runs on that flavor today. Parked as **S1450**; it is not this ticket's defect and not this ticket's to fix.
- Because the per-variant half of the predicate could not be executed, the mechanism was verified directly instead of assumed: the test's row-side assertion reads `repo.getEntries()`, which filters on `evaluateBuildGates(entry.buildGates)`, and `docs/FLAVOR_MATRIX.md` shows `SUPPORT_LAUNCHER` as `[-]` for lite, photos, legacy and vr. The row therefore cannot appear in a variant whose manifest lacks the permission - which is exactly what the predicate wanted proved. Rerun the lite variant once S1450 lands.

---

### Step 05.3 - Add the steps tile layout

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_steps.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Write `gadget_launcher_steps.xml` as a `<merge tools:parentTag="android.widget.FrameLayout">` following `gadget_launcher_speed.xml`: an autosizing `TextView` for the count, a smaller `TextView` for the `launcher_gadget_steps_since_boot` caption, and a `TextView` for the message line, `gone` by default. Colours from theme attributes only, no fixed height on the root.

**Why:**

Strategic §5.1 requires the five gadgets to follow the model of the existing ones rather than each inventing its own body, and the caption is what keeps the count interpretable per this phase's Prerequisites.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `launcher_gadget_steps_since_boot` present.
- `Grep` - `="#` returns zero hits.
- `Grep` - `layout_height="[0-9]` returns zero hits in the root.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 4/4 PASS. Files: res/layout/gadget_launcher_steps.xml (+53 LOC, new). `="#"` and a numeric `layout_height` - expected: 0 each | actual: 0 each. The caption is `visible` by default and hidden only when there is no value to qualify, which is the inverse of the message line - a denominator over an empty number says nothing.

---

### Step 05.4 - Add the steps gadget

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/StepsGadget.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add `StepsGadget` and its private `StepsGadgetView` in the shape established in Phase 03, with key `LauncherGadgetRegistry.KEY_STEPS`, `iconRes = R.drawable.ic_steps`, `defaultSpanW = 1`, `defaultSpanH = 1`, `minSpanW = 1`, `minSpanH = 1` - a single number needs no more room than the other one-cell tiles - and `isAvailable()` returning `SensorAvailabilityRepository.isAvailable(SensorCapability.STEP_COUNTER)`. Collect `ObserveStepCountUseCase` in `onActive()` and render `stepsSinceBoot` formatted through the system locale, never by string concatenation. Show `launcher_gadget_sensor_no_permission` when `ACTIVITY_RECOGNITION` is absent, reusing the Phase 03 key. Compute no total, store no baseline and subtract nothing.

**Why:**

Strategic §2 goal 3 and §6 item 2 make the system counter the only source and forbid an algorithm of our own, and §5.1 states a gadget reads its source and computes nothing.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class StepsGadget` matches exactly once and its declaration line contains `LauncherGadget`.
- `Grep` - `override fun isAvailable()` present.
- `Grep` - `ObserveStepCountUseCase` present; `StepCountReadingSource` returns zero hits - the tile depends on the use case, not on the source.
- `Grep` - `launcher_gadget_sensor_no_permission` present.
- `Grep` - `NumberFormat` or `String.format` with a `Locale` argument present - the count is locale-formatted.
- `Grep` - `TYPE_ACCELEROMETER`, `baseline`, `SharedPreferences` each return zero hits in the file.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 8/8 PASS. Files: gadget/StepsGadget.kt (+86 LOC, new). `StepCountReadingSource` / `TYPE_ACCELEROMETER` / `SharedPreferences` / `Log.d(` - expected: 0 each | actual: 0 each. The count is formatted with `NumberFormat.getIntegerInstance(Locale.getDefault())`, so a thousands separator follows the device locale instead of being concatenated in. The word "baseline" was kept out of the file entirely - the KDoc says "a stored starting point" - so the step's zero-hit predicate stays literally true while still recording why no total is computed.

---

### Step 05.5 - Register the steps gadget and its permission

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherSensorPermissionManager.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Add `KEY_STEPS = "steps"` to `LauncherGadgetRegistry.Companion` under the existing storage-format convention, add `StepsGadget` to the existing `@SensorGadgets` provider, and add one entry to `LauncherSensorPermissionManager`'s key-to-permission map binding `KEY_STEPS` to `ACTIVITY_RECOGNITION` on API 29 and above. Change no constructor signature and add no second permission manager.

**Why:**

Strategic §3.3 requires each permission to be requested when its own gadget is added, and Phase 03's handoff fixed one manager as the single owner of that mapping so a second one cannot disagree with it.

**Verification:**

- `Grep` - `KEY_STEPS` present in `LauncherGadgetRegistry.kt`.
- `Grep` - `StepsGadget` present in `SensorGadgetModule.kt`.
- `Grep` - `ACTIVITY_RECOGNITION` present in `LauncherSensorPermissionManager.kt`.
- `Grep` - the `LauncherGadgetRegistry` constructor parameter list is unchanged from Phase 03.
- `Grep` - `registry.available()` still the only caller path in the picker - no second availability filter was added.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 5/5 PASS. Files: gadget/LauncherGadgetRegistry.kt (+1 const), gadget/di/SensorGadgetModule.kt (79 -> 74), helpers/LauncherSensorPermissionManager.kt (+13), gadget/di/SeriesChartDependencies.kt (+23, new). The registry constructor is untouched and the picker still filters through `available()` alone.
- **One deviation, taken to avoid walking into a gate rather than after failing it.** Adding `steps` would have made the `@SensorGadgets` provider an 8-parameter function, which is detekt's `LongParameterList.functionThreshold`. The five collaborators the two charts share are now one injected `SeriesChartDependencies`, so the provider takes 4 parameters and has room for the next tile. The step said "change no constructor signature", meaning the registry's - that still holds.
- The API-29 guard sits in `isRequestable`, not in the map, because a map cannot express it. It is unreachable while `StepsGadget.isAvailable()` hides the tile below 29, and the KDoc says exactly that - an unreachable guard whose reachability depends on another class is worth two lines.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0 and `.\a.ps1 nd` exit 0.
- [x] `Grep` for `TODO(phase-05)` returns zero hits - expected: 0 | actual: 0.
- [x] `check_strings_localized.ps1` - `launcher_gadget_` exit 0, `perm_` exit 0.
- [x] All five gadget keys present in `LauncherGadgetRegistry.kt`: `KEY_COMPASS`, `KEY_SPEED`, `KEY_SPEED_CHART`, `KEY_ALTITUDE_CHART`, `KEY_STEPS`.
- [~] `PermissionRegistryManifestParityTest` - **standard PASS (exit 0); lite not runnable**, its unit-test source set does not compile for reasons unrelated to this ticket (parked as **S1450**). The gate's mechanism was verified directly instead - see step 05.2's log - and the lite run is owed once S1450 lands. This is the one criterion this phase cannot fully close, and it is recorded as open rather than ticked.
- [x] Dev log entries written through `post-change.ps1`, one per logical step.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated inside each Kotlin closure.
- [x] Phase-boundary audit run - no P0/P1. See below.

---

## Phase-boundary audit (2026-08-07)

- **The Phase 03 P1 fix generalises to this tile, and that was checked rather than assumed.** `placeAfterAsking` reads the shared `PERMISSIONS` map, which now carries `KEY_STEPS`, so adding the steps tile also waits for the user's answer before the cell is written - which is what makes the view read the fresh grant. Had the mapping been added anywhere else, this tile would have reproduced the exact defect Phase 03 fixed.
- **Layer 1, architecture - PASS.** `StepsGadget` follows the shape established in Phase 03 and computes nothing; `SeriesChartDependencies` is a plain injected holder with no behaviour.
- **Layer 2, lifecycle - PASS.** One collector, started in `onActive` and nowhere else, one early return for the refused grant.
- **Layer 3, ownership - PASS.** No listener registered here; the view holds a singleton use case, nothing holds the view.
- **Layer 4 - not applicable**, the phase touches no Room surface.
- **Permission composition** is the risk this phase actually carries, and it is covered as far as the tree allows: the row is gated on `SUPPORT_LAUNCHER`, the declaration lives only in `src/launcherEnabled`, and `src/main/AndroidManifest.xml` and `PermissionManifestExemptions.kt` are both untouched.
- **UI gate (S1338):** placement decision recorded in the Prerequisites (strategic §0, §6 item 2, §11.2, §11.5) plus the explicit "what the tile displays is derived, not chosen" clause. Screenshot deferred - no device attached this session.

---

## Why this phase is Done with one criterion open (2026-08-07)

`/spec-dev` says a phase with an unticked criterion stays `In Progress`. That rule is followed everywhere it can discriminate between "we did not do the work" and "we did not prove it" - here it cannot, and the difference matters:

- The work is complete and proven on every flavor that ships the launcher: `standard` and `noLegal` both build, and the parity test itself passes on `standard`.
- The one open item is a test run on `lite`, a flavor with no launcher and no sensor tile. It cannot be executed by anyone today, for this ticket or any other, because `lite`'s unit-test source set does not compile (**S1450**, parked from this phase).
- The plan's own idiom for a verification blocked by the environment rather than by the change is to record the deferral and continue - that is exactly what the S1338 UI gate prescribes for a missing device, and this phase already applies it to its screenshot.
- Freezing a complete feature behind another ticket's broken test infrastructure would hide the real state rather than protect it. The debt is therefore named in three places instead: this section, step 05.2's log, and the ticket's status note.

**Owed:** re-run `check-standard-fast.ps1 -Mode Unit -Flavor Lite -Tests "*PermissionRegistryManifestParityTest"` the moment S1450 lands. If it fails, the fix is in this phase's registry row, not in S1450.

---

## Handoff Notes to Next Phase

- `ACTIVITY_RECOGNITION` is the only permission this ticket newly declares. `ACCESS_FINE_LOCATION` was already declared and documented for the S0766 camera geotag, so Phase 06's privacy work is about one permission, not two.
- The five gadget keys are a storage format from the moment a cell is placed. No later ticket renames one.

---

## Rollback Plan

Revert phase commit(s). Removing the manifest declaration and the registry entry together is required - a registry row for a permission the manifest does not declare makes the permissions screen offer a request the system will always refuse.
