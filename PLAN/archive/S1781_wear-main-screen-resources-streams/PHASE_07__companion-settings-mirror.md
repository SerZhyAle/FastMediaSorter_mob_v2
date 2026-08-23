# Phase 07 - Companion settings mirror

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 06
**Blocks:** none directly - Phase 08's "depends on all" covers it
**Steps done:** 3 / 3
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Both new watch settings - view mode and keep-awake - are settable from the phone's Wear Companion sheet, without resetting a newer watch value when an older phone pushes a payload that omits them.

---

## Prerequisites

- [ ] Phase 01 and Phase 06 are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt` | Modified | ≤ 30 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt` | Modified | ≤ 25 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCase.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt` | Modified | ≤ 470 (was ≤ 360, which the file already exceeded at 411 after Phase 04's entry-point button; landed at 465) |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 25 |
| `wear/src/test/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCaseTest.kt` | Modified | ≤ 150 |

---

## Steps

### Step 07.1 - Carry the two settings in the payload, backward compatibly

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `viewMode: String? = null` and `keepScreenAwakeOutsidePlayers: Boolean? = null` to both `WearSettingsPayload` data classes - nullable with a `null` default on both sides, not the non-null-with-default shape the other seven fields use, because Gson deserializes a JSON object missing a key as `null` regardless of a Kotlin default when the target type is nullable, but silently produces an unsafe `null` on a non-null type with no default. In `ApplyWearSettingsUseCase`, apply each new field only when non-null - `payload.viewMode?.let { preferencesRepository.setViewMode(WearViewMode.fromNameOrDefault(it)) }` and similarly for the keep-awake flag - leaving the stored watch value untouched when the phone omits it.

**Why:**

Strategic §3.2 "Совместимость данных" requires this payload change stay compatible with an older phone; the explicit failure mode named in the phase brief - "an older phone that omits the fields must not reset them on the watch" - is only avoidable if the applying side can distinguish "not sent" from "sent as false/List", which a non-nullable field with a default cannot do once Gson has deserialized it.

**Verification:**

- `Grep` - `viewMode: String?` present in both `WearSettingsPayload.kt` files.
- `Grep` - `keepScreenAwakeOutsidePlayers: Boolean?` present in both `WearSettingsPayload.kt` files.
- `Grep` - `?.let` or an equivalent null-guard present in `ApplyWearSettingsUseCase.kt` around both new fields.
- `.\a.ps1 fk` - exit 0.
- `.\a.ps1 fkn` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 07.1: both WearSettingsPayload classes carry viewMode: String? = null and keepScreenAwakeOutsidePlayers: Boolean? = null - nullable on purpose, unlike the seven fields beside them, so the watch can tell an omitted key from a sent value. ApplyWearSettingsUseCase applies each only behind ?.let, leaving the watch's own choice alone when an older phone omits the keys. The phone side pins both wire names with @SerializedName, matching that file's S1631 contract; the wear side has no such annotations on any field and stays that way. Verified: viewMode: String? and keepScreenAwakeOutsidePlayers: Boolean? each present once in both payload files; ?.let appears twice in the use case; a.ps1 fw exit 0; a.ps1 fk exit 0; a.ps1 fkn exit 0.

---

### Step 07.2 - Add the two controls to the Watch Settings block

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 07.1

**Prompt for developer:**

> In `WearSyncScreen`'s expanded "Watch Settings" block, add a three-way selector for view mode (List/Grid 2/Grid 3) and a `SwitchRow` for keep-awake, following the existing `SwitchRow` pattern the five other rows already use, and include both new fields in `buildPayload()`. Add the two labels through `set-android-string.ps1 -Action add`, prefixed `wear_settings_view_mode` and `wear_settings_keep_awake`.

**Why:**

Strategic §5.1 "Зеркало настроек на телефоне" - "Каждая настройка часов имеет копию в Wear Companion. Это требование к составу... настройка часов, добавленная без копии на телефоне, считается незавершённой работой" - and §0 owner text verbatim, "All the setting of WEAR must be duplicated on the phone", are the reason this step exists; strategic §7 names a watch setting shipped without its Companion copy as a high-probability risk specifically because it makes the watch unconfigurable without the watch.

**Verification:**

- `Grep` - `viewMode` and `keepScreenAwakeOutsidePlayers` both present in `buildPayload(` call inside `WearSyncSettingsFragment.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_settings_view_mode"` - exit 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_settings_keep_awake"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 07.2: the Watch Settings block gains a Keep watch screen on switch beside the five existing ones and a three-chip view-mode selector (List / Grid 2 / Grid 3); both feed buildPayload, which now carries viewMode and keepScreenAwakeOutsidePlayers. The three values are the wear enum's own names sent as strings - app_v2 does not depend on the wear module, and the watch resolves an unknown name back to its list default, so the two sides cannot drift into an ordinal mismatch. Strings wear_settings_view_mode plus its three option labels and wear_settings_keep_awake added EN/RU/UK; the option labels share the view-mode prefix so one parity check covers them. Verified: buildPayload names both new fields; check_strings_localized -KeyPrefix wear_settings_view_mode exit 0 (4 keys) and -KeyPrefix wear_settings_keep_awake exit 0; a.ps1 fc exit 0.

---

### Step 07.3 - Unit-test the omitted-fields case

**Files:** `wear/src/test/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCaseTest.kt`
**Depends on:** Step 07.1

**Correction applied during execution:** the step guessed `app_v2/src/test` and told the developer to follow the repository's precedent for cross-module Wear use-case tests. There is no such precedent to follow: the `wear` module has its own test source set, and `ApplyWearSettingsUseCaseTest` already lives in it with a `FakeWearPreferencesRepository` that already implements `viewMode` and `keepScreenAwakeOutsidePlayers`. The case is added to that file rather than starting a second convention in `app_v2` - which is what the step's own note asked for, against the path it named.

**Prompt for developer:**

> Add a test that applies a `WearSettingsPayload` with `viewMode = null` and `keepScreenAwakeOutsidePlayers = null` against a fake `WearPreferencesRepository` pre-seeded with `GRID_2` and `keepScreenAwakeOutsidePlayers = true`, then asserts both stored values are unchanged after `ApplyWearSettingsUseCase` runs. Note this test lives under `app_v2/src/test` even though the class under test is in the `wear` module - place it wherever this repository's existing cross-module Wear use-case tests already live, matching that precedent rather than introducing a second convention.

**Why:**

This is the one behaviour Step 07.1's backward-compatibility change exists to guarantee, and strategic §3.2 flags data compatibility as a hard constraint rather than a preference - a test is the only durable proof a future payload-field addition does not regress the null-means-unchanged contract.

**Verification:**

- `Glob` - `ApplyWearSettingsUseCaseTest.kt` exists.
- `Grep` - a test asserting the pre-seeded `viewMode` is unchanged after applying a payload with `viewMode = null`.
- `.\a.ps1 fu` - the new test class passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 07.3: two cases added to the existing wear-module ApplyWearSettingsUseCaseTest - a payload omitting both new keys leaves a pre-seeded GRID_2 and keepScreenAwake=true untouched, and a payload carrying them applies GRID_3 and true. The file already had a FakeWearPreferencesRepository implementing both properties, so nothing was duplicated. Path correction: the step named app_v2/src/test and told the developer to follow the repository's precedent for cross-module Wear tests - the precedent is the wear module's own test source set, where this very class was already tested, so the cases went there rather than starting a second convention. Verification correction: run the wear unit set, not a.ps1 fu, which runs app_v2's suite and would never see this class. Verified: check-standard-fast -Mode Unit -Module wear -Tests *ApplyWearSettingsUseCaseTest* exit 0; testDebugUnitTest XML tests=3 failures=0 errors=0 at 2026-08-18T19:40:24Z (was 1 test before).

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/wear.jsonl` and `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module <wear|app_v2>`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every watch setting introduced by this tactical plan now has a Companion-side control, and the payload stays backward compatible. Phase 08 is the closing documentation and catalog pass over the whole ticket - no functional work remains after this phase.

---

## Rollback Plan

Revert phase commit(s) - the two payload fields are additive and nullable, so an older watch or phone binary ignores them; the new Companion controls are additive UI with no migration.
