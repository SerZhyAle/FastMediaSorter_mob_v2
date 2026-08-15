# Phase 05 - Name the number in the overwrite warning

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of the matrix work
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Replace the vague "some of your settings" in the existing profile-change confirmation with the actual count of settings the chosen profile will overwrite.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.

> This phase consumes only the CSV preset data source, which already exists - it does not need any earlier phase. It is sequenced after the matrix work because the number is most useful once the matrix is full, not because of a code dependency.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CountProfilePresetOverridesUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/profile/DeviceProfilePickerDialogFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | ≤ +12 |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | ≤ +14 |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | ≤ +14 |

> The confirmation mechanic already exists and is already skipped on first run - `DeviceProfilePickerDialogFragment.onTileClicked` shows it only when `warnOnApply && type != currentType && type != OTHER`, and the Welcome flow passes `warnOnApply = false`. Do not add a second dialog or a new gate; this phase changes what the existing dialog says.
>
> No layout file is touched, so the `res/layout-land/` parity rule does not apply to this phase.

---

## Steps

### Step 05.1 - Add the override-count use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CountProfilePresetOverridesUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CountProfilePresetOverridesUseCase` in `domain/usecase`, constructor-injected with the CSV preset data source. Expose a single `suspend operator fun invoke(profileType: DeviceProfileType): Int` returning the number of non-empty overrides the matrix declares for that profile - the same map `ApplyProfilePresetUseCase.applySettingsOnly` folds, so the number the user sees is by construction the number that will be written. Return 0 when the profile has no entry rather than throwing.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class CountProfilePresetOverridesUseCase` matches exactly once.
- `Grep` - `suspend operator fun invoke` matches in the file.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 05.2 - Author the plural-aware warning strings

**Files:** `app_v2/src/main/res/values/strings_settings.xml`, `app_v2/src/main/res/values-ru/strings_settings.xml`, `app_v2/src/main/res/values-uk/strings_settings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a `plurals` resource named `settings_profile_warning_count` taking one integer argument, in all three locales. Hand-edit the XML: `scripts/utils/set-android-string.ps1` manages `string` keys only, not `plurals`. English needs `one` / `other`; Russian and Ukrainian need `one` / `few` / `many` / `other`. Do not pass the Russian or Ukrainian text as a PowerShell command-line argument from a Bash tool call - the encoding boundary mangles Cyrillic; edit the files directly. Message shape per `docs/COMMUNICATION_POLICY.md` §2: state what will happen and how many settings it affects, then ask for confirmation - do not add alarm words, and keep Cancel an equal choice.

**Verification:**

- `Grep` - `settings_profile_warning_count` matches exactly once in each of the three files.
- `Grep` - `<item quantity="many">` matches in the `values-ru` and `values-uk` files.
- Value equality - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_profile_warning"` returns exit code 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Status:** `[x]` done

---

### Step 05.3 - Show the count in the existing confirmation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/profile/DeviceProfilePickerDialogFragment.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Inject `CountProfilePresetOverridesUseCase` into the fragment - it is already `@AndroidEntryPoint`, so add a second `@Inject lateinit var` beside `deviceProfileAvailability`. In `onTileClicked`, when the warning branch is taken, resolve the count in a lifecycle-scoped coroutine and build the message with `resources.getQuantityString(R.plurals.settings_profile_warning_count, count, count)`. Keep the positive/negative buttons and the dismissal behaviour exactly as they are. If the count resolves to 0, apply directly without a dialog - warning about zero overwrites is noise.

**Verification:**

- `Grep` - `CountProfilePresetOverridesUseCase` matches in the fragment.
- `Grep` - `getQuantityString` matches in the fragment.
- `Grep` - `settings_profile_warning\b` returns zero hits in the fragment (the old singular key is gone).
- `Grep` - `GlobalScope` returns zero hits in the fragment.

**Status:** `[x]` done

---

### Step 05.4 - Remove the superseded string key

**Files:** `app_v2/src/main/res/values/strings_settings.xml`, `app_v2/src/main/res/values-ru/strings_settings.xml`, `app_v2/src/main/res/values-uk/strings_settings.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Confirm `settings_profile_warning` has no remaining reference in Kotlin or XML, then remove the key from all three locales with `scripts/utils/set-android-string.ps1 -Action remove`. Leaving an orphaned string key behind is exactly the dead weight CLAUDE.md Rule 20 forbids.

**Verification:**

- `Grep` - `settings_profile_warning"` returns zero hits across `app_v2/src/main`.
- `Grep` - `R.string.settings_profile_warning` returns zero hits across `app_v2/src`.
- Value equality - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_profile"` returns exit code 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `Grep` for `Log\.d\(` in every touched `.kt` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - a new use case was added.
- [x] `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` used to fill `role` and `status` for the new use case.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The count comes from the same override map that gets applied, so it cannot drift from reality as the matrix grows. Device verification of this dialog belongs to the `/spec-dev` device-test step, not to a plan step.

---

## Rollback Plan

Revert phase commit(s) - restoring the previous singular string and dropping the use case. No persisted state and no schema change.

---

## Implementation notes (2026-07-27)

**The plan's file list was incomplete.** Step 05.4 assumed `settings_profile_warning` had a single consumer. It had two: `DeviceProfilePickerDialogFragment` and `WelcomeActivity.showProfilePresetReapplyWarning`, whose own comment says it mirrors the picker. Deleting the key without touching Welcome would have broken the build, and leaving the key would have failed 05.4's predicate. Welcome was therefore given the same treatment.

**Welcome resolves the count in the ViewModel, not the Activity.** `WelcomeEvent.ConfirmProfilePresetReapply` now carries `overrideCount`, so `WelcomeActivity` renders a number it is handed instead of reaching into a use case (CLAUDE.md Rule 3).

**The count is delegated, not injected twice.** Injecting `CountProfilePresetOverridesUseCase` straight into `WelcomeViewModel` pushed its constructor from 10 to 11 parameters and detekt flagged `LongParameterList` - a real smell, not a baseline artifact. `ApplyProfilePresetUseCase` (already injected there) now exposes `overrideCount()` and delegates to the new use case, so the parameter count is unchanged and both call sites still read one implementation. The applier test constructs the real counter over the same mocked data source, proving `overrideCount()` and `apply()` read one map.

**Zero-override profiles skip the dialog** in both entry points. A confirmation that announces zero overwrites is noise. This affects the Settings picker and the Welcome re-entry path only - first-run behaviour is untouched (strategic non-goal).

**Evidence:**

- `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*ApplyProfilePresetUseCaseTest*"` - `BUILD SUCCESSFUL`, exit 0; results XML 2026-07-27 21:42:18, tests 16, failures 0, errors 0.
- `scripts/check_strings_localized.ps1 -KeyPrefix "settings_profile"` - `OK: all 7 key(s) present in en/ru/uk`, exit 0.
- `scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles <all six touched files>` - `PASS [scoped] .. none among changed files`, exit 0.
- `scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile -KeyPrefix settings_profile` - `post-change: PASS (Mixed, 37824 ms)`.
- `dev/CATALOG/scripts/set.ps1` - role and `status=tested` recorded for the new use case.

**Not covered here:** on-device confirmation that the dialog renders the plural correctly in all three locales. That belongs to the device-test step, and no device was attached this session.
