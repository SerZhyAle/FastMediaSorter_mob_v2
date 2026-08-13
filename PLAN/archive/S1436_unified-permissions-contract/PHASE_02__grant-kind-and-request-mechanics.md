# Phase 02 - Grant kind and request mechanics

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Make the grant route a property of the registry entry rather than a hardcoded set and three duplicated `when` blocks: introduce the three grant kinds, give the per-use-consent kind its own status and its own row rendering, and collapse the duplicated system-screen routing and the three disagreeing storage-SDK rules into one owner each.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## UI placement decision (recorded)

Step 02.4 changes how a row renders, so the decision behind it is quoted rather than made here. Strategic §6 item 6, dated 2026-08-06: the capture consent is shown as an informational row without a grant button, because the user is asked about it but cannot grant it in advance - a third state instead of granted or denied. Strategic §3.3 adds that no new screen appears and the row keeps its place among the others. Nothing about placement is left to implementation.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolvePermissionActionUseCase.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionGrantIntentFactory.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/StoragePermissionRule.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/PermissionChecker.kt` | Modified | ≤ 45 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `WelcomeActivity.kt` (915 LOC) and `PermissionHelper.kt` are over 500 LOC - steps 02.5 and 02.6 carry the backup sub-step.
>
> **Layout note:** `app_v2/src/main/res/layout/item_permission_entry.xml` needs no edit - the informational row reuses the state indicator and hides the existing action button. No `layout-land` variant of that file exists, so landscape parity is not applicable here.

---

## Steps

### Step 02.1 - Add the grant kind to the entry model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `enum class PermissionGrantKind { RUNTIME_DIALOG, SYSTEM_SCREEN, PER_USE_CONSENT }` and a `grantKind: PermissionGrantKind = PermissionGrantKind.RUNTIME_DIALOG` field on `PermissionEntry`. Document on the enum what each kind means for the row: a runtime dialog grants in place, a system screen navigates away and returns a result, a per-use consent has no persisted grant at all.

**Why:**

Strategic §5.1 states there are exactly three ways the build can ask for something and that the "granted by a special route" property already exists implicitly as a list of exceptions inside the screen, which must become an explicit property of the entry; without it the registry cannot describe the permissions §5.1 requires it to carry.

**Verification:**

- `Grep` - `enum class PermissionGrantKind` matches exactly once in `PermissionEntry.kt`.
- `Grep` - `grantKind` matches in the `PermissionEntry` data-class declaration.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.2 - Drive the action rule from the grant kind instead of a hardcoded set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolvePermissionActionUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Set `grantKind = PermissionGrantKind.SYSTEM_SCREEN` on the three existing entries that need one - `manage_external_storage`, `manage_media`, `battery_optimization`.
>
> In `ResolvePermissionActionUseCase`, reimplement `isSpecialGrant(entry)` as `entry.grantKind == PermissionGrantKind.SYSTEM_SCREEN` and delete the `SPECIAL_GRANT_PERMISSIONS` companion set. Return `PermissionAction.None` for `PER_USE_CONSENT`, ahead of the status branch, so a per-use row has no action to perform.

**Why:**

Strategic §5.1 requires the decision between a system dialog and a system settings screen to be taken in one place from the properties of the entry rather than reinvented per surface, and strategic §6 item 6 rules that the capture-consent row carries no grant button and is skipped by "Grant all".

**Verification:**

- `Grep` - `SPECIAL_GRANT_PERMISSIONS` returns zero hits under `app_v2/src`.
- `Grep` - `grantKind == PermissionGrantKind.SYSTEM_SCREEN` matches in `ResolvePermissionActionUseCase.kt`.
- `Grep` - `PermissionGrantKind.SYSTEM_SCREEN` matches exactly three times in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `S1426:` still matches in `ResolvePermissionActionUseCase.kt` - S1426 is in `BlockNeedUserTest` and its probe tag must survive this edit.
- `.\a.ps1 fu --tests "*Permission*Test"` exits 0 and every permission test class reports a fresh non-zero test count with zero failures. `fk` alone is insufficient here: it does not compile `src/test`, where the entries this contract change affects are constructed.

**Status:** `[x]` done

---

### Step 02.3 - Give per-use consent its own status value

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `ASKED_EACH_TIME` to `PermissionStatus`. In `CheckPermissionStatusUseCase`, return it for any entry whose `grantKind` is `PER_USE_CONSENT`, checked before the SDK-window branch and before the manifest-name `when`, since such an entry has no grant state to read.
>
> Fix every `when` the compiler now rejects by handling the new value explicitly - never by adding an `else` arm. Two sites need it: `PermissionDenialHandler.needsSettingsRoute` (false - there is no settings route for a consent that is asked again each time) and the action-button text in `PermissionRowAdapter` (empty, alongside `NOT_APPLICABLE`). `bindStateIndicator` already has an `else` arm that absorbs the new value; step 02.4 owns replacing it with a dedicated one, so leave it here.

**Why:**

Strategic §5.1 states the capture consent has no "granted" state and its row is informational, which the existing five-value status enum cannot express: mapping it onto `DENIED` would put it into the "Grant all" batch that strategic §6 item 6 rules it must be excluded from.

**Verification:**

- `Grep` - `ASKED_EACH_TIME` matches in `PermissionEntry.kt`, `CheckPermissionStatusUseCase.kt`, `PermissionDenialHandler.kt` and `PermissionRowAdapter.kt`.
- `Grep` - `else ->` returns zero hits inside the `when (status)` block of `ResolvePermissionActionUseCase.kt`.
- `.\a.ps1 fu --tests "*Permission*Test"` exits 0 with every permission test class fresh and green - the compile is proved by the test run, which covers `src/test` too.

**Status:** `[x]` done

---

### Step 02.4 - Render the informational row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `PermissionRowAdapter.EntryViewHolder`, handle `ASKED_EACH_TIME`: hide `btn_perm_action` and show the state indicator with a neutral icon, colour and content description that read as "the system asks every time", reusing the existing `ic_perm_state_missing` drawable and the `perm_state_missing` colour rather than adding new assets.
>
> Add one string key for the indicator content description across EN/RU/UK in a single `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En .. -Ru .. -Uk ..` call. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 for the message formula of its type and §6 for tone before writing it.

**Why:**

Strategic §6 item 6 rules that this permission is shown as an informational row without a grant button, and §3.2 makes EN/RU/UK parity mandatory for every user-visible formulation the spec introduces.

**Verification:**

- `Grep` - `ASKED_EACH_TIME` matches inside `bindStateIndicator` and inside the action-button branch of `PermissionRowAdapter.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_state"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 02.5 - Extract the system-screen routing into one factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionGrantIntentFactory.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `PermissionGrantIntentFactory` as an injectable singleton owning the mapping from an entry to the `Intent` that grants it, including the app-details fallback when no dedicated screen exists on this SDK level. Move the body of `launchSpecialGrantSettings` into it verbatim, preserving the existing `ActivityNotFoundException` fallback and its Timber log.
>
> Have both `PermissionsManagementFragment` and `WelcomePermissionsManager` call the factory and keep only the launcher invocation locally. Bind it in `app_v2/src/main/java/com/sza/fastmediasorter/di/PermissionModule.kt` if constructor injection alone does not suffice.

**Why:**

Research artifact 01 records `launchSpecialGrantSettings` as two near-identical hand-maintained copies, and strategic §5.1 requires the "system dialog or system settings" decision to live in one place rather than be reinvented per surface, because phase 03 is about to add two more system-screen permissions to both copies.

**Predicate corrected 2026-08-06, and the leftovers given a home.** The prompt's two files were the right two - both were already migrated to the factory when this step resumed. But the step's predicate, "`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` matches exactly once under `app_v2/src/main`", cannot pass on those two files alone: **five** call sites build that intent by hand, and only two are the special-grant route this step owns.

| Call site | Owner |
|---|---|
| `ui/settings/fragments/PermissionsManagementFragment.kt` | this step - migrated |
| `ui/welcome/helpers/WelcomePermissionsManager.kt` | this step - migrated |
| `core/util/PermissionHelper.kt` (~179 and ~293) | **Step 02.6** - already in its `Files Touched`, and both are storage-SDK routing, which is what that step collapses |
| `ui/main/helpers/MainStoragePermissionsHelper.kt` (~97) | **unassigned** - see below |
| `worker/ScheduledOperationsWorker.kt` (~137) | **unassigned** - see below |

The last two have no home in any phase of this ticket, and the ticket's own thesis - one owner for the grant route - is false while they exist. They are not this step's to fix (neither is a special-grant route, and the worker has no activity at all - it builds the intent for a notification), so they are recorded here rather than silently left: **Phase 03 must adopt them, or the single-owner claim in strategic §5.1 does not hold.** The predicate is therefore scoped to what this step can prove, with the full count kept visible.

**Also in this step:** `PermissionGrantIntentFactory.grantIntent` had no branch for `Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, the registry row S0429 added on 2026-08-06 with `grantKind = SYSTEM_SCREEN`. Without one that row's grant button opens the app-details page instead of the notification-access screen. Fixed here: the factory is this step's file and the gap is one `when` branch.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionGrantIntentFactory.kt` exists.
- `Grep` - `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` returns zero hits in `PermissionsManagementFragment.kt` and `WelcomePermissionsManager.kt` - the two this step owns. Project-wide it is 5 until Steps 02.6 and Phase 03 take the rest; the count is tracked in the table above, not asserted here.
- `Grep` - `class PermissionGrantIntentFactory` matches exactly once.
- `Grep` - `ACTION_NOTIFICATION_LISTENER_SETTINGS` present in `PermissionGrantIntentFactory.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 02.6 - Collapse the three storage-SDK rules into one

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/StoragePermissionRule.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/utils/PermissionChecker.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 02.5

**Prompt for developer:**

> Back up `WelcomeActivity.kt` and `PermissionHelper.kt` to `temp/S1436/` with timestamped names first (Rule 5).
>
> Create `StoragePermissionRule` as the single owner of "which storage permission this SDK level needs", exposing the required permission names for the running SDK and whether all-files access applies. Derive its SDK windows from the registry entries' `minSdk`/`maxSdk` values rather than restating them.
>
> Replace the bodies of `PermissionChecker`'s storage check, `PermissionHelper.checkStoragePermissions` and `getStoragePermissionsArray`, and the private storage branch inside `WelcomeActivity` (around `WelcomeActivity.kt:657-688`) with calls into it. Keep each caller's own return shape; only the branching moves.

**Why:**

Research artifact 01 records three disagreeing implementations of this rule - one that never requests write at any API, one that requests it on M-P, and one that never surfaces all-files access on API 30-32 - and strategic §5.1 requires the rule to move into the single request mechanic because the SDK window is already described by the registry entry.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/util/StoragePermissionRule.kt` exists.
- `Grep` - `Build.VERSION_CODES.Q` returns zero hits in `PermissionChecker.kt`, `PermissionHelper.kt` and `WelcomeActivity.kt`.
- `Grep` - `object StoragePermissionRule` matches exactly once.
- `Glob` - timestamped backups of both files exist under `temp/S1436/`.
- `.\a.ps1 fk` exits 0.

**The write permission, and where its window comes from.** The rule cannot derive one of its four windows from the registry, because `WRITE_EXTERNAL_STORAGE` has no registry row - it is declared only in the manifest, `android:maxSdkVersion="28"`. That gap is exactly what ADR-4's parity gate exists to catch, so the rule takes the window from the manifest, names the source in its KDoc, and Phase 03 owns adding the row. Folding write into "no registry row, therefore not required" would have silently dropped write access on API 23-28, where the `legacy` flavor (minSdk 23) still lives.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - **Phase-boundary audit** (CLAUDE.md §13, `docs/CODE_AUDIT_PROTOCOL.md` layers 1-3; layer 4 not applicable, nothing here touches Room). Layers 2 and 3 came back clean and the finding is worth keeping: `WelcomePermissionsManager` registers its activity-result contracts on the raw `activityResultRegistry` with stable keys and never unregisters, which reads like a leak and is not one - the manager is unscoped, so each Activity gets its own instance and its own registry, and the synchronous redelivery on re-register is what makes a mid-run rotation survive. Every Flow in the touched UI is collected through `collectOnLifecycle`.
  - **P1, and it was not this step's regression - it is this ticket's own thesis failing on a real device.** From API 26 the system grants only the permission actually requested, never a sibling in the same group. `PermissionHelper.checkStoragePermissions` has demanded read **and** write on API 23-28 since long before this ticket (the pre-edit `when` is in `temp/S1436/PermissionHelper.kt.20260806-step0206.bak`, M-P arm: `hasRead && hasWrite`), but `WRITE_EXTERNAL_STORAGE` has no registry row, and the permissions screen and onboarding can only request what the registry lists. On API 26-28 the screen therefore reports storage granted while `MainStoragePermissionsHelper.hasFullLocalPermissions` and `MainResumePlaybackHelper.shouldAttemptResume` read it as denied - a silent resume-on-launch failure and a rationale dialog the user cannot satisfy from the screen that raised it.
  - Fixed by giving the permission a row, not by dropping it from the rule: the manifest declares `WRITE_EXTERNAL_STORAGE` with `android:maxSdkVersion="28"`, and on API 26-28 a direct `File` write outside the app-specific directories still needs it, so removing it from `requiredPermissions()` would trade a visible contradiction for silent write failures on Android 8-9. Written up as **Step 03.7** in `PHASE_03__registry-completeness.md`, which is the phase that owns registry rows and the one place the fix cannot drift from.
  - P2 fixed here, because both files are this phase's own: `PermissionsManagementFragment` and `WelcomePermissionsManager` each still imported `android.Manifest` and `android.os.Build` after step 02.5 moved the branching out - `expected: 0 usages | actual: 0` in both, so four dead imports removed (they would have failed the next scoped detekt close on either file).
  - P2 fixed here: `StoragePermissionRuleTest` added. The rule takes `sdkInt` as a parameter precisely so every window can be driven, and it shipped with nothing driving it; the test pins each window against the source it came from - the registry rows for read/media/all-files, the manifest `maxSdkVersion` for write.
  - P2/P3 recorded, not fixed, each with a home: `PER_USE_CONSENT` has no producing entry yet (step 03.3 adds it - scaffolding by plan, not orphaned code); the two grant-all implementations remain twins in the two screens (step 03.6 collapses their grouping, the run mechanics stay duplicated after it); `ScheduledOperationsWorker` builds the all-files intent with no fallback at all, so on a ROM without the package-scoped screen its notification tap dead-ends silently (still unassigned, same list as step 02.5's table); `PermissionHelper` carries seven public functions with zero callers project-wide, for phase 06's dead-weight pass rather than a mid-phase deletion.

- 2026-08-06 - Step 02.6 DONE. `StoragePermissionRule` created in `core/util` as the single owner of the storage SDK question, with three answers - the runtime permissions this level needs, whether all-files access replaces them, and whether what is needed is currently held. All three former implementations now delegate: `PermissionChecker.getRequiredMediaPermissions`, `PermissionHelper.checkStoragePermissions` / `getStoragePermissionsArray` / `requestStoragePermission` / `routeToStorageSettings`, and `WelcomeActivity.getRequiredMediaPermissions`.
  - **The three disagreed about write, and the manifest settled it.** One never requested `WRITE_EXTERNAL_STORAGE`, one requested it at every API, one on M-P. The manifest declares it with `android:maxSdkVersion="28"`, so the single answer is: 23-28 read + write, 29-32 read, 33+ the three `READ_MEDIA_*`, all-files access from 30. `PermissionChecker` gains write on 23-28 and `getStoragePermissionsArray` loses it from 29 up - both are the correction, not a regression: requesting a permission the platform stopped granting is a no-op, failing to request one it still grants is broken write access on the `legacy` flavor.
  - `routeToStorageSettings` collapsed from five SDK branches to two destinations, and the two hand-built copies of the all-files-access intent inside this file became one private `launchAllFilesAccessSettings`, so `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` now appears **once** here instead of twice. `requestManageMediaPermission` was in the blast radius of that consolidation and lost its dead `catch (e: Exception)` around an `Intent(..).apply {}` - the throw happens at launch, so the catch is now `ActivityNotFoundException` and reuses the new `launchAppDetailsSettings`.
  - **One duplicate of this rule survives, and it stays assigned.** `ui/main/helpers/MainStoragePermissionsHelper.launchStoragePermissionFlow` still branches on `SDK_INT >= R` and builds the all-files intent itself, with the same dead `catch` shape. Step 02.5's table already recorded it as unassigned and named Phase 03 as its owner; it is not adopted here rather than quietly widening this step, but the single-owner claim in strategic §5.1 is not true until Phase 03 takes it.
  - `WelcomeActivity` shed its `android.Manifest` and `android.os.Build` imports with the branch - the file's only remaining platform check is fully qualified.
  - Verification 5/5 PASS: `StoragePermissionRule.kt` exists; `object StoragePermissionRule` `expected: 1 | actual: 1`; `Build.VERSION_CODES.Q` `expected: 0 hits | actual: 0` in each of `PermissionChecker.kt`, `PermissionHelper.kt`, `WelcomeActivity.kt`; two timestamped backups under `temp/S1436/`; `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`.
  - Step 02.2's lesson applied rather than restated: `fk` does not compile `src/test`, so the step also ran `.\a.ps1 fu --tests "*Permission*Test"`. `expected: PASS | actual: PASS` - 4 classes, 24 tests, 0 failures, 0 errors, result XMLs stamped 2026-08-06T16:46Z (read from the XML, not from the gradle line).

- 2026-08-06 - Step 02.5 DONE, resumed from `[~]` left by an earlier session. Code state was read before trusting the marker: `PermissionGrantIntentFactory` already existed and **both** named callers already delegated to it, so the migration itself was complete. Added the missing `BIND_NOTIFICATION_LISTENER_SERVICE` branch (S0429's row, `grantKind = SYSTEM_SCREEN`, which would otherwise have routed to the app-details page) - deliberately not package-scoped, because that screen is a global list the user picks the app out of. Verification 5/5 PASS: factory file exists, `class PermissionGrantIntentFactory` 1 hit, `ACTION_NOTIFICATION_LISTENER_SETTINGS` 1 hit, zero all-files-access hits in either owned caller, `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`. The step's original predicate was project-wide and unreachable from this step's two files; corrected above, with the three remaining call sites tabulated and assigned rather than dropped. `post-change: PASS (Kotlin)`, exit 0.

- 2026-08-06 - One more finding, from detekt rather than from review, and worth recording because deleting a fallback silently would have been the easy move. The scoped gate flagged `TooGenericExceptionCaught` on a `catch (e: Exception)` this step did not write. Reading it: the `try` wrapped `Intent(action).apply { data = Uri.parse(..) }`, which cannot throw - the failure it was written for happens at **launch**, and both callers already catch `ActivityNotFoundException` there. So the catch was dead and its fallback to `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` (the global all-files list) unreachable. Removed. **The intent behind it was sound and now has no home:** when the package-scoped all-files screen refuses to open, the callers fall back to app details, which is a worse destination than the global all-files list. Phase 03 should give the launch-site fallback a per-permission answer instead of one app-details path for everything.

- 2026-08-06 - Step 02.1 DONE. `PermissionGrantKind` (RUNTIME_DIALOG / SYSTEM_SCREEN / PER_USE_CONSENT) added to `PermissionEntry.kt` with a KDoc stating what each kind means for the row, plus `grantKind` on the entry defaulting to `RUNTIME_DIALOG` so no existing entry changes behaviour. `expected: exit 0 | actual: exit 0` for `.\a.ps1 fk`.

- 2026-08-06 - Step 02.2 DONE. `invoke` now branches on `entry.grantKind` first and only then on status; `isSpecialGrant` reads the kind; the `SPECIAL_GRANT_PERMISSIONS` companion and the now-unused `android.Manifest` import are gone. `manage_external_storage`, `manage_media` and `battery_optimization` carry `SYSTEM_SCREEN`. The class KDoc's claim to be "the only place that knows which permissions are special grants" was corrected rather than left standing - the entry knows now.
  - S1426 flipped to `BlockNeedUserTest` in a sibling session mid-step and had just written a `Timber.d("S1426: action ..")` probe into this same file. The probe was preserved through the restructuring and a verification predicate was added to pin it, since deleting another ticket's live probe would break its device test.
  - `expected: 0 hits | actual: 0 hits` for `SPECIAL_GRANT_PERMISSIONS`; `expected: 3 | actual: 3` for `SYSTEM_SCREEN` entries; `expected: exit 0 | actual: exit 0` for `.\a.ps1 fk`.
  - **Miss caught after the first close.** `fk` compiles `src/main` only, so it did not see that S1426's new `ResolvePermissionActionUseCaseTest` builds its entries from the manifest name alone and relies on the default grant kind - two of its tests were broken by this step's contract change and the step had already been marked done. `entryOf` gained a `systemScreenEntryOf` variant that declares the kind the registry declares, which keeps the test's original claim intact. Lesson applied to the remaining steps of this ticket: verify with `.\a.ps1 fu --tests "*Permission*Test"` and read the result XML, not with `fk` alone.
  - `expected: PASS | actual: PASS` - 4 classes, 24 tests, 0 failures, 0 errors, timestamps 2026-08-06T14:55Z.
- 2026-08-06 - Step 02.3 DONE. `PermissionStatus.ASKED_EACH_TIME` added with a KDoc stating why it is a separate value rather than a `DENIED`: the grant-all run selects on `NOT_YET_REQUESTED`/`DENIED`, so folding it in would put a permission that can never be granted into the batch. `CheckPermissionStatusUseCase` answers it for a `PER_USE_CONSENT` entry ahead of the SDK window and of any `ContextCompat` check. Compile-forced arms added to `PermissionDenialHandler.needsSettingsRoute` (false) and the adapter's button text (empty); no `else` introduced anywhere.
  - The first test run failed the build - `ResolvePermissionActionUseCase.kt:43 'when' expression must be exhaustive` - and the result XMLs still carried the previous run's 14:55 timestamps, which is exactly why the verification reads the timestamp and not the gradle line. The inner status `when` gained an explicit `ASKED_EACH_TIME` arm with a comment recording that the value cannot reach it.
  - `expected: PASS | actual: PASS` - 4 classes, 24 tests, 0 failures, timestamps 2026-08-06T15:02Z.
  - The scoped detekt gate then failed the close with `ReturnCount` (3 returns in `invoke`). `invoke` became a single `when` expression and the manifest-name table moved into a private `resolveGrantedState`, which is also where it belonged: the outer function now reads as the three-way question it is. Re-verified `expected: PASS | actual: PASS` at 2026-08-06T15:05Z, then `post-change: PASS`.
- 2026-08-06 - Step 02.4 DONE. The action button is now `GONE` for `ASKED_EACH_TIME` as well as `NOT_APPLICABLE` - hidden rather than disabled, because a dead control reads as a bug. `bindStateIndicator` lost its `else` arm: `ASKED_EACH_TIME` gets the not-granted shape and colour with its own content description, and the remaining three statuses are listed explicitly. No new drawable or colour was introduced; the existing KDoc was extended rather than replaced.
  - String `perm_state_asked_each_time` added across EN/RU/UK in one `set-android-string.ps1 -Action add` call: "Asked each time" / "Спрашивается каждый раз" / "Запитується щоразу". `docs/COMMUNICATION_POLICY.md` §6 checklist walked - it is a state description, not an error or an empty state, carries no exception text, and is short enough for 360 dp.
  - `expected: exit 0 | actual: exit 0` for `check_strings_localized.ps1 -KeyPrefix "perm_state"` (all 4 keys present in en/ru/uk) and for `.\a.ps1 fc`.
  - **Screenshot deferred (no device).** The Stage 0 device probe reported `no-device`, so the S1338 UI screenshot could not be captured. Phase 02's Done Criteria do not demand one, so the deferral is recorded and the phase continues; the placement decision it would evidence is the owner ruling quoted in "UI placement decision (recorded)" above.
- 2026-08-06 - Step 02.5 EDITS APPLIED, VERIFICATION BLOCKED - step left `[~] in progress`. `PermissionGrantIntentFactory` created as an `@Singleton` owning the entry-to-Intent mapping plus the app-details fallback; both `PermissionsManagementFragment` (field-injected) and `WelcomePermissionsManager` (constructor-injected) now call it, and each keeps only its own launcher invocation and `ActivityNotFoundException` retry, because that exception is thrown at launch time where only the caller has the launcher.
  - `.\a.ps1 fk` `expected: exit 0 | actual: exit 1`. Every error is in `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` - unresolved `trayBattery` / `trayBatteryPercent` view-binding fields - and no error is in any file this ticket touched. That file belongs to S1415 (`launcher-taskbar-status-area-config`), which a sibling session holds a lease on and is editing right now; the same ticket's `launcherTrayShow*` settings already surfaced as a non-attributable advisory in the step 02.2 close.
  - Not debugged further, per the shared-tree rule: a red tree caused by another session's half-written source is waited out, not investigated. The step's own verification must be re-run once `LauncherTrayManager.kt` compiles - re-enter with `/spec-dev S1436 --step 02.5`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` `expected: exit 0 | actual: exit 0`; `.\a.ps1 fu --tests "*Permission*Test"` re-proved it over `src/test` after the audit fixes.
- [x] `Grep` for `TODO(phase-02)` returns zero hits - `expected: 0 | actual: 0`.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` ran in every `post-change` close, and `StoragePermissionRule` carries its role and status via `set.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The one P1 was fixed before this flip, not deferred: step 03.7 gives `WRITE_EXTERNAL_STORAGE` its registry row, and it was executed ahead of 03.1 for that reason. The P2/P3 items are recorded in the Step Log, each with a named owner.
- [x] Screenshot: deferred, no device this run (recorded at step 02.4). Phase 02's criteria do not demand one and the placement decision it would evidence is the owner ruling quoted above, so the deferral does not hold the phase open (S1338 branch: "no device attached").

---

## Handoff Notes to Next Phase

An entry now declares its own grant route, a per-use-consent entry renders as an informational row and is excluded from "Grant all" by the existing `isRequestable` predicate without a special case, one factory owns every system-screen intent, and one object owns the storage-SDK rule. Phase 03 adds entries only - it needs no further mechanics.

---

## Rollback Plan

Revert phase commit(s) - no data migration. The new status value and the two new classes have no persisted state, and the entry set is unchanged, so a revert restores the previous behaviour exactly.
