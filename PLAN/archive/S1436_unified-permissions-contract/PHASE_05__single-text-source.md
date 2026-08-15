# Phase 05 - Single text source

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Move every in-feature permission explanation to the registry: each call site keeps its own shell - a blocking dialog, a snackbar, a button label - and stops carrying its own wording, with a per-task addendum declared next to the main description where one task genuinely needs more than the others.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRegistryRepository.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt` | Modified | ≤ 390 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleHelper.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | ≤ 570 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/CompanionQrScanActivity.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderLaunchManager.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainScreenRecordingManager.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsScheduledManager.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleText.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ScreenRecordingLaunchActivity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `ResourceEditorFragment.kt` (908 LOC) and `AddResourceConnectionManager.kt` (560 LOC) are over 500 LOC - steps 05.3 and 05.4 carry the backup sub-step.

---

## Steps

### Step 05.1 - Give the registry a rationale API

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRegistryRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `enum class PermissionTask` naming the tasks that need their own addendum - start with `FOLDER_PICKING`, `LOCAL_RESOURCE_CREATION`, `QR_PAIRING`, `SCREEN_RECORDING`, `SCHEDULED_OPERATIONS` - and a `taskAddenda: Map<PermissionTask, Int> = emptyMap()` field on `PermissionEntry` holding string resource ids.
>
> Add a `PermissionRationale` data class in the same model file, carrying the title, the description and the optional task addendum as string resource ids. Add `fun getRationale(manifestName: String, task: PermissionTask?): PermissionRationale?` to `PermissionRegistryRepository` and its implementation, returning the entry's title, its description and the addendum for that task when one is declared. Return `null` when no entry matches, so a caller for a permission outside the registry is a visible bug rather than a blank dialog.

**Why:**

Strategic §5.1 requires the registry to become the source not only for the list row but for any explanation shown during work, and states that a permission needed for several different tasks may carry a per-task addendum declared in the registry beside the main description rather than at the call site.

**Verification:**

- `Grep` - `enum class PermissionTask` and `data class PermissionRationale` each match exactly once under `app_v2/src/main`.
- `Grep` - `fun getRationale` matches in `PermissionRegistryRepository.kt` and `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `taskAddenda` matches in `PermissionEntry.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Merge the six all-files-access wordings into one

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleHelper.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Rewrite `perm_desc_manage_external_storage` so it carries the meaning of all six existing wordings combined rather than picking one of them, then declare a `FOLDER_PICKING` addendum on the entry for the part that is specific to choosing a folder. Check both against `docs/COMMUNICATION_POLICY.md` §2 and §6 before writing them.
>
> Point the startup gate in `MainStoragePermissionsHelper`, the folder-picker dialog duplicated in `AddResourceScanManager` and `BrowseLifecycleHelper`, and the preceding toast in `AddResourceScanManager` at `getRationale` instead of their own keys. Keep each shell exactly as it is - the blocking dialog stays a blocking dialog, the toast stays a toast, and the existing button labels stay.

**Why:**

Strategic §1 records six different explanations of this one permission, strategic §11 criterion 4 requires it to become one, and strategic §7 mitigates the risk of an impoverished explanation by requiring the merge to combine meaning rather than choose a winner, with contested cases resolved by a per-task addendum.

**Verification:**

- `Grep` - `all_files_access_required`, `all_files_access_explanation`, `permission_storage_rationale`, `permission_storage_rationale_r`, `permissions_required_for_local_resource` and `android_media_requires_permission` return zero hits under `app_v2/src/main/java`.
- `Grep` - `getRationale` matches in all three modified call-site files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_desc"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

---

### Step 05.3 - Point local-resource creation at the same text

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Back up `ResourceEditorFragment.kt` to `temp/S1436/` with a timestamped name first (Rule 5, the file is 908 LOC).
>
> Replace its own storage-permission wording and its denial warning with `getRationale` for the same permission, using the `LOCAL_RESOURCE_CREATION` task so the creation-specific sentence comes from the registry addendum rather than from this file.

**Why:**

Research artifact 01 counts local resource creation among the six divergent all-files-access wordings, and strategic §5.1 requires the call site to keep its shell while the wording comes from the registry.

**Verification:**

- `Grep` - `permissions_denied_warning` returns zero hits in `ResourceEditorFragment.kt`.
- `Grep` - `PermissionTask.LOCAL_RESOURCE_CREATION` matches in that file.
- `Glob` - a timestamped `ResourceEditorFragment.kt` backup exists under `temp/S1436/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.4 - Make the network description name one protocol set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Back up `AddResourceConnectionManager.kt` to `temp/S1436/` with a timestamped name first (Rule 5, the file is 560 LOC).
>
> Rewrite the network permission description to name the union of both current lists - SMB, SFTP, FTP, DLNA and Chromecast - after confirming against `docs/FLAVOR_MATRIX.md` which of them the flavor actually ships, and never restating the grid from memory. Point the rationale dialog duplicated in `AddResourceConnectionManager` and `BrowseEventHandler` at `getRationale`, deleting both local copies. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §1 records that this is a content mismatch rather than only a wording one - the registry names SMB and DLNA while the dialog names SMB, SFTP, FTP and Chromecast, each omitting what the other states - and strategic §11 criterion 5 requires the same protocol set everywhere the description appears.

**Verification:**

- `Grep` - the two former rationale keys return zero hits under `app_v2/src/main/java`.
- `Grep` - `getRationale` matches in both modified call-site files.
- `Glob` - a timestamped `AddResourceConnectionManager.kt` backup exists under `temp/S1436/`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_desc"` exits 0.

**Status:** `[x]` done

---

### Step 05.5 - Route the camera and microphone call sites through the registry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/CompanionQrScanActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderLaunchManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainScreenRecordingManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Extend the camera description to cover QR pairing, which it never mentioned, and point `CompanionQrScanActivity` at `getRationale` with the `QR_PAIRING` task.
>
> Point the four microphone call sites at `getRationale`. Split the screen-recording message, which currently bundles microphone and notifications into one sentence, into the two permissions it actually concerns, using the `SCREEN_RECORDING` addendum for the recording-specific part. Leave `camera_capture_microphone_muted` alone - it reports that recording continues without sound and is not a permission rationale.
>
> Add the missing rationale to the silent location request in `OperationsCaptureManager`, which today requests on toggle with no explanation at all.

**Why:**

Research artifact 01 records three camera wordings with one undocumented use, five microphone call sites carrying four wordings, and a location request made silently on toggle; strategic §2 goal 2 requires one permission to be explained in the same words wherever it is asked for.

**Verification:**

- `Grep` - `companion_qr_camera_denied`, `mic_recording_permission_denied`, `quick_recorder_permission_needed` and `screen_recording_permission_denied` return zero hits under `app_v2/src/main/java`.
- `Grep` - `camera_capture_microphone_muted` still matches.
- `Grep` - `getRationale` matches in all five modified call-site files.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 05.6 - Give the scheduled-operations prompts a description

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsScheduledManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 05.5

**Prompt for developer:**

> Replace the bare notifications button label with the registry description under the `SCHEDULED_OPERATIONS` task, and give the battery-optimization intent the registry rationale it currently launches without.
>
> Guard both on `BuildConfig.DECLARES_BATTERY_OPTIMIZATION` where the battery permission is concerned, so the release build does not prompt for a permission its manifest no longer declares.

**Why:**

Research artifact 01 records the notifications prompt as a bare button label with no description and the battery intent as launched with no rationale at all, and strategic §11 criterion 3 requires the release build to carry no battery-optimization affordance.

**Verification:**

- `Grep` - `grant_notifications_permission` returns zero hits under `app_v2/src/main/java`.
- `Grep` - `DECLARES_BATTERY_OPTIMIZATION` matches in `OperationsScheduledManager.kt`.
- `.\a.ps1 fk` exits 0.
- `.\a.ps1 fu` runs `PermissionRegistryManifestParityTest` green (record `expected: PASS | actual: <result>`).

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 05.6 DONE. The two scheduled-operations prompts stopped being wordless.
  - **The button label was never in Kotlin - it was in both layouts**, so the step's own predicate was green before any work was done. The label is now set from the registry at the moment the button is shown, and the design-time `android:text` in `layout/` and `layout-land/` points at the registry title instead of the hand-written key, which leaves `grant_notifications_permission` with zero references anywhere in `app_v2/src` - phase 06 removes it.
  - **The battery-optimization guard was a correctness fix, not only a wording one.** The release build strips `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` from the manifest, and this code still sent the user to the system exclusion screen there - strategic §11 criterion 3. Guarded on `BuildConfig.DECLARES_BATTERY_OPTIMIZATION`, which phase 01 made readable.
  - The system screen is the longest jump any request here makes, so it now opens from a dialog carrying the registry paragraph, with a cancel that keeps the user on the settings screen. The bare `catch (_: Exception) { }` around the intent went with it: it is now `ActivityNotFoundException` with a reason, which is the one thing that can actually happen (vendor builds without a handler) and the one case where there is nothing to recover.
  - Verification 4/4 PASS: `grant_notifications_permission` `expected: 0 hits under src/main/java | actual: 0` (and 0 across `app_v2/src` outside `values`); `DECLARES_BATTERY_OPTIMIZATION` present in `OperationsScheduledManager.kt:333`; `.\a.ps1 fk` `expected: exit 0 | actual: exit 0` with 10 tasks executed; the unit run was scoped to `-Tests "*PermissionRegistry*"` rather than the whole suite (S1244 - `fu` has truncated mid-run before, and a per-class result is the one that can be read): `PermissionRegistryManifestParityTest` `expected: PASS | actual: 3 tests, 0 failures`, `PermissionRegistryRepositoryImplTest` 9 tests, 0 failures, both stamped from this run.

- 2026-08-06 - Step 05.5 DONE. Camera, microphone and location now explain themselves from the registry at every place that asks.
  - **The plan named five call-site files and the keys live in eight.** `BrowseActivity`, `OperationsSettingsFragment` and `ScreenRecordingLaunchActivity` also read the very keys the step's own predicate demands be gone, so migrating only the listed five would have left the predicate red and the wording split. All eight were migrated; `Files Touched` above lists the three additions.
  - **A denial that names two permissions names neither.** `screen_recording_permission_denied` said "Microphone and notification permissions are required", and both callbacks - in `MainScreenRecordingManager` and in the widget trampoline - showed it whichever one was refused. `showPermissionDenied` / `denyAndFinish` now take the permission that was actually denied, and `POST_NOTIFICATIONS` gained its own `SCREEN_RECORDING` addendum saying what the notification is for (the stop control).
  - **A toast has room for one line, so `permissionRationaleShort` gained the task parameter**: with a task it returns that task's sentence, without one the row's line. The paragraph-plus-addendum form stays for the snackbars and dialogs, which have the room. Policy §2.1 is the reason, not brevity for its own sake.
  - Two row descriptions were rewritten to stay true in both roles, the same way `perm_desc_manage_external_storage` was in step 05.2: camera now reads "Shooting, text recognition and QR codes" (pairing scans were a camera use the list never admitted to) and microphone "Voice notes and sound in recordings".
  - The geotag toggle requested location with nothing said at all. It now explains first and requests on the snackbar's action, and the row returns to off until the grant lands - here the switch and the permission are the same state, unlike the gesture overlay of strategic §6 item 4, so leaving it on while ungranted would be a lie.
  - Verification 4/4 PASS: the four displaced keys `expected: 0 hits under src/main/java | actual: 0`; `camera_capture_microphone_muted` still present in `CameraCaptureActivity` (it reports silent recording, not a permission); the registry rationale reached from all eight call-site files - through the phase's own `permissionRationale*` helpers, which are the only callers of `getRationale` by design; `.\a.ps1 fc` `expected: exit 0 | actual: exit 0`, with the compiled `standardDebug` classes stamped 22:00:31 against a newest source edit of 21:59:04, so the green covers these edits rather than a stale build.

- 2026-08-06 - Step 05.4 DONE. The two lists that disagreed - "SMB and DLNA servers" on the row and "SMB, SFTP, and FTP servers, and Chromecast devices" in the dialog - became one: **SMB, SFTP, FTP and DLNA**.
  - **Chromecast is deliberately not named**, and the plan's own instruction is why. `docs/FLAVOR_MATRIX.md` was read rather than recalled: `SUPPORT_CAST` is `[-]` in `vr`, while `SUPPORT_LOCAL_NETWORK` is `[+]` there - so the `vr` build shows this row and has no Chromecast. Naming it would have made the sentence false in exactly one shipped flavor, which is the class of defect this ticket exists to remove.
  - A third reader turned up that the plan did not list: `NetworkErrorMessageMapper` maps `LocalNetworkPermissionDeniedException` to the old rationale string. Pointed at the new one - an error about a missing permission and the request for it must not word it differently - rather than left as a seventh wording.
  - `PermissionHelper.getLocalNetworkPermissionMessage` was the last other reader and had zero callers; deleted with the keys, like the two storage ones in step 05.2.
  - The scoped detekt gate failed once on `ImportOrdering` in `AddResourceConnectionManager`. Pre-existing disorder - `utils.collectOnLifecycle` sat second and `core.error.ErrorSeverity` sat among the `domain.*` imports - which the added import resurfaced by shifting lines. Sorted the block rather than working around it.
  - Verification 4/4 PASS: both former keys `expected: 0 hits under src/main/java | actual: 0`; the rationale reached from both call sites; timestamped backup of the 560-LOC file under `temp/S1436/`; `check_strings_localized.ps1 -KeyPrefix "perm_desc"` `expected: exit 0 | actual: exit 0`; `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`; `post-change: PASS (Mixed)`.

- 2026-08-06 - Steps 05.2 and 05.3 DONE together, because 05.2's own predicate lists `permissions_required_for_local_resource`, whose only call site the plan assigns to 05.3 - the two could not both be green apart.
  - **The merge combined meaning rather than picking a winner**, as strategic §7 requires. The six wordings between them said: any folder rather than the three standard ones; DCIM, Camera and app folders such as Android/media; scanning and showing media; moving and deleting; needed to create a local resource; and a privacy line. The new paragraph carries the first four and the privacy line, folder-picking becomes the `FOLDER_PICKING` addendum, and local-resource creation the `LOCAL_RESOURCE_CREATION` one. The old emoji-and-bullet block is gone with them: `docs/COMMUNICATION_POLICY.md` §6 bans emoji images outright, and that string had four.
  - **A row label and a dialog paragraph are not the same string**, which the plan treated as one. `PermissionEntry` gained `rationaleRes` and `PermissionRationale` splits into `summaryRes` and `detailRes`: the list row and the toast get the short line policy §2.1 allows a toast, the blocking dialog gets the paragraph. `perm_desc_manage_external_storage` was rewritten from "Move and delete files" to "Access to any folder, including app folders" so it is true in both roles.
  - **The mechanism cost a full revert, and the gate was right.** The three storage call sites are hand-constructed helpers, so the first attempt threaded the repository down from `MainActivity`, `AddResourceActivity` and `BrowseActivity`. `assert-source-gates` refused it: `+1 new domain-layer field injection` in each of the three, which is CLAUDE.md Rule 3. Reverted entirely - including the extra `BrowseManagerInitializer` parameter, which would have fed S1269 - in favour of a Hilt `@EntryPoint` over the application component, the escape hatch `UriPathResolver` and the Glide model loaders already use here. The helpers now ask a `Context` and no host learned a new dependency.
  - Two dead functions went with the strings: `PermissionHelper.getStoragePermissionMessage` and `getAllFilesAccessPermissionMessage` had zero callers project-wide - the phase-02 audit had already listed them - and were the last readers of three of the six keys.
  - Verification 05.2 4/4 and 05.3 4/4 PASS: all six keys `expected: 0 hits under src/main/java | actual: 0`; `getRationale` reached from all three call sites; `permissions_denied_warning` `expected: 0 in ResourceEditorFragment | actual: 0`; `PermissionTask.LOCAL_RESOURCE_CREATION` present there; timestamped backup of that 908-LOC file under `temp/S1436/`; `check_strings_localized.ps1 -KeyPrefix "perm_"` `expected: exit 0 | actual: exit 0` (70 keys); `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`; `post-change: PASS (Mixed)`.

- 2026-08-06 - Step 05.1 DONE. `PermissionTask`, `PermissionRationale` and the `taskAddenda` map added to the model; `getRationale(manifestName, task)` added to the repository interface and its implementation. The task parameter defaults to null, so a call site with nothing task-specific to say asks for the plain description and does not have to invent a task value.
  - One decision the prompt left open, recorded because it is not obvious: `getRationale` matches against the **raw** entry list, not `getEntries()`. An explanation is asked for at the moment work needs the permission, and a row filtered out by an SDK window or a build gate would otherwise return null there - a blank dialog exactly when the user most needs the sentence. The null return is reserved for its intended meaning: a call site asking about a permission the registry has never heard of.
  - Verification 4/4 PASS: `enum class PermissionTask` `expected: 1 | actual: 1` and `data class PermissionRationale` `expected: 1 | actual: 1` under `src/main`; `fun getRationale` present in both interface and implementation; `taskAddenda` present in `PermissionEntry.kt`; `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`. `post-change: PASS (Kotlin)`, exit 0.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` `expected: exit 0 | actual: exit 0`, APK `v2.60.8041.533-DEBUG` produced 2026-08-06 22:14.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added - two entries, one per step, per CLAUDE.md "one entry per logical change, not per touched file"; both written by the `post-change` closure.
- [x] Catalog regenerated - `catalog_sync` ran inside step 05.5's closure (2501 records rescanned); `permissionRationaleShort` gaining a parameter is the only public-API change.
- [x] Phase-boundary audit run - see below. No P0/P1. One P2 parked as `S1447`.

### Phase-boundary audit (2026-08-06)

- **Layer 1 - architecture.** PASS. Every call site kept its shell and lost only its wording, which is ADR-5. No host learned a new dependency: the helpers reach the registry through the same application-scoped `@EntryPoint` step 05.2 introduced, so the three-injection rejection recorded there is not re-earned.
- **Layer 2 - lifecycle and coroutines.** PASS. Nothing added collects a Flow or starts a coroutine. The snackbars are attached to a view and die with it.
- **Layer 3 - memory and ownership.** One P2. `OperationsScheduledManager.explainThenOpenBatteryOptimizationScreen` shows a dialog it does not hold, so a rotation while it is up keeps the destroyed Fragment alive - the exact defect S1197 fixed in `MainStoragePermissionsHelper`. Not fixed here: the class already has that shape at every other dialog, and the settings-helper family has 34 such call sites and no host dismiss hook to attach one to, which makes it a family fix, not a one-line one. Parked as `S1447`.
- **Layer 4 - Room.** Not applicable, no persistence touched.
- **File sizes.** `OperationsScheduledManager.kt` is 375 LOC against this plan's own ≤ 350 budget for it - over the plan, far under the 1500-LOC rule. The overrun is the extracted `openBatteryOptimizationScreen`, which the alternative (leaving the intent inline inside the dialog callback) would have hidden rather than removed.

### UI evidence (S1338)

- Placement decision on record: strategic §3.3 "UI placement contract" plus the §12 quiz answers of 2026-08-06.
- **One shell was chosen here, not by the owner, and should be confirmed on device:** the geotag toggle now explains first and grants from a snackbar action, where before it opened the system dialog immediately. The plan asked for the missing rationale and named no shell, because this request had none to keep. It costs one extra tap.
- Screenshot deferred (no device) - `device-ready.ps1` reports `no-device`. Phase Done Criteria do not demand the shot; the on-device pass is the ticket's own `BlockNeedUserTest` step.

---

## Handoff Notes to Next Phase

Every in-feature permission explanation now comes from the registry, so a wording change is one edit. The string keys the call sites stopped using are still present in all three locale files - phase 06 removes them.

---

## Rollback Plan

Revert phase commit(s) - no data migration. The displaced string keys are still in place until phase 06, so a revert of this phase alone leaves every call site able to compile against its former key.
