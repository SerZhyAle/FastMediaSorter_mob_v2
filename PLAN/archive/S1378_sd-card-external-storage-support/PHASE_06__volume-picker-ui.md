# Phase 06 - Volume picker UI

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Surface removable volumes where the user adds a source, mark resources that live on one, and refresh both when a medium is connected or ejected.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Strategic §3.4 `/ui-clarify` record is present - every placement decision in this phase comes from it verbatim.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_resource_removable.xml` | New | ≤ 30 |
| `app_v2/src/main/res/layout/dialog_folder_selection.xml` | Modified | ≤ 40 added |
| `app_v2/src/main/res/layout-land/dialog_folder_selection.xml` | Modified | ≤ 40 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconComposer.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStorageVolumeWatchManager.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1440 |
| `app_v2/src/main/res/values/strings.xml`, `values-ru`, `values-uk` | Modified | 3 keys added |

> `MainActivity.kt` sits at 1425 LOC against a 1500-LOC ceiling - this phase adds only the manager wiring, and the manager itself is a new file. It landed at 1436: a lazy field, one `addObserver` line, one import. An earlier attempt with `onStart`/`onStop` overrides reached 1445 and was replaced (see the Step 06.5 log).

---

## Steps

### Step 06.1 - Add the removable-source drawable

**Files:** `app_v2/src/main/res/drawable/ic_resource_removable.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a vector drawable for a removable medium in the visual language of the existing `ic_resource_*` icons, using theme attributes for tint - no hardcoded hex colour.

**Why:**

Strategic §3.4 records the owner's decision that a resource on a removable medium carries its own source icon instead of the ordinary local-folder icon.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `#` returns zero colour literals in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS (file present, zero `#` literals). Follows `ic_storage`: `android:tint="?attr/colorControlNormal"` over a white path, 24dp like every `ic_resource_*`. Dev log recorded.

---

### Step 06.2 - Add the removable section to both dialog orientations

**Files:** `app_v2/src/main/res/layout/dialog_folder_selection.xml`, `app_v2/src/main/res/layout-land/dialog_folder_selection.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a `CollapsibleSectionHeader` plus an empty container for removable volumes immediately after the existing quick-folders section, in both the portrait and the landscape layout, reusing the ids and the styling shape of the quick-folders block. The container starts empty - buttons are added at runtime. Give the section `android:visibility="gone"` by default.
>
> Amended 2026-08-05 during execution: Step 06.6 adds the section strings, but this step's own `.\a.ps1 fr` predicate cannot pass while it references a key that does not exist yet. Each step therefore creates the keys it is the first to reference - `removable_volumes_section` here - and Step 06.6 becomes the policy and parity pass over all of them.

**Why:**

Strategic §3.4 fixes the placement as a separate collapsible section right after the quick-select block and requires the section to be hidden entirely when nothing is connected; CLAUDE.md Rule 11 makes the landscape counterpart part of the same edit.

**Verification:**

- `Grep` - `headerRemovableVolumes` and `containerRemovableVolumes` match in both layout files.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Ids present in portrait (69, 71) and landscape (242, 250); `fr` exit 0. Section sits directly after `containerQuickFolders` in both, both parts `visibility="gone"` until the registry reports a volume. Key `removable_volumes_section` created here (see the amendment above). Dev log recorded.

---

### Step 06.3 - Populate the section from the volume registry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 06.2

**Prompt for developer:**

> When the folder-selection dialog is built, ask `GetStorageVolumesUseCase` for removable volumes and add one outlined button per volume carrying its display name and free space, using `ic_resource_removable` as the button icon. Tapping a button selects that volume's root as the folder, going through the system folder-access request when the app has no direct path access. Hide the whole section - header included - when the list is empty. Set a `contentDescription` on every button naming the volume, and keep the buttons reachable by keyboard and D-pad like the neighbouring quick-folder buttons. Collect the volume list with the lifecycle-aware collector used elsewhere in the module, never a bare `launch { collect { } }`.

**Why:**

Strategic §2 goal 1 requires the user to pick a removable volume without typing a path, and §3.2 requires TalkBack support, D-pad reachability and off-main-thread enumeration for the new list.

Amended 2026-08-05 during execution, three points:

- The registry is reached through `AddResourceViewModel.getRemovableVolumes()`, not by the manager holding the use case. The first attempt passed `GetStorageVolumesUseCase` into the hand-constructed manager via an `@Inject` field on `AddResourceActivity`, and `assert-neuroslop` refused it: domain-layer field injection in an Activity breaks CLAUDE.md Rule 3 and the `UI -> ViewModel -> UseCase` layering. The verification below moved with the code - the use case is grepped in the ViewModel, the manager is grepped for the accessor it calls.
- Three keys are created here, the ones this step is the first to reference: `removable_volume_button_label`, `removable_volume_button_description`, `removable_volume_access_request`. Step 06.6 is the policy and parity pass over all four.
- The explanation before the system request is shown as a rationale dialog with a confirm button, the platform-standard shape for "explain, then ask". Strategic §3.4 does not cover this surface - it is derived from §3.1's requirement that the explanation appear *before* the system prompt, not from an owner ruling, and is the one placement in this phase that was not pre-decided.

**Verification:**

- `Grep` - `GetStorageVolumesUseCase` matches in `AddResourceViewModel.kt`, and `getRemovableVolumes` matches in `AddResourceScanManager.kt`.
- `Grep` - `contentDescription` set for the generated buttons.
- `Grep` - `lifecycleScope.launch {` followed by a bare `collect` returns zero hits in the file.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. `GetStorageVolumesUseCase` in `AddResourceViewModel.kt` (2 hits), `getRemovableVolumes` in the manager, `contentDescription` set per button, zero bare `collect` after a `launch` - the registry is a suspend call returning a list, so nothing is collected at all. `fc` exit 0, manager 379 LOC against a 420 budget. Buttons are built through a `ContextThemeWrapper` carrying `Widget.FastMediaSorter.Button.Outlined`, so they match the quick-folder buttons beside them rather than the bare Material default.
- 2026-08-05 - Three reworks before the step closed, all caught by gates rather than by review: `assert-neuroslop` refused the domain use case as an Activity field (Rule 3) and the dependency moved to the ViewModel; detekt flagged `ImportOrdering` in the manager, whose import block was sorted properly rather than re-baselined; detekt flagged `LongParameterList` on the ViewModel constructor, already baselined at twelve parameters and resurfaced by the thirteenth - carried as an explicit `@Suppress` with the reason written next to it, the pattern five other classes in the module already use. Dev log recorded.

---

### Step 06.4 - Render the removable icon for bound resources

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconComposer.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> In the theme-drawable resolution, return `ic_resource_removable` when the resource carries a non-null `storageVolumeId` and no custom icon id is assigned. A user-assigned icon keeps winning, and the connection badge overlay stays unchanged.

**Why:**

Strategic §3.4 records that the removable marker replaces the default local-folder icon only, leaving a user-chosen icon in place, and it must work in both the list and the grid - which composing at this single point delivers.

**Verification:**

- `Grep` - `ic_resource_removable` matches in `ResourceIconComposer.kt`.
- `Grep` - `storageVolumeId` matches in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS (`ic_resource_removable` 1 hit, `storageVolumeId` 1 hit, `fk` exit 0). The branch sits after the custom-icon lookup and before the set-first fallback, so a user-assigned icon still wins and every default loses to the removable glyph; the badge overlay is untouched. File 96 LOC against a 130 budget. Dev log recorded.

---

### Step 06.5 - Refresh on connect and eject

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStorageVolumeWatchManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 06.4

**Prompt for developer:**

> Add a manager that registers for the media mounted, unmounted, ejected and removed broadcasts while the screen is started, and asks the resource list to reload when one arrives. Register and unregister it from `MainActivity` with a single wiring call each - all logic lives in the manager. Unregister in the matching lifecycle callback so no receiver outlives the screen.

**Why:**

Strategic §2 goal 5 requires ejecting a medium to produce a clear state instead of a stale list, and §3.1 asks for the volume to be picked up on connection without a manual rescan; CLAUDE.md Rule 3 keeps that logic out of the Activity.

**Verification:**

- `Grep` - `class MainStorageVolumeWatchManager` matches exactly once.
- `Grep` - `registerReceiver` and `unregisterReceiver` both present in the manager and absent from `MainActivity.kt` for this feature.
- `MainActivity.kt` stays under 1500 LOC.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. `class MainStorageVolumeWatchManager` declared once, `registerReceiver`/`unregisterReceiver` both inside it and zero of either in `MainActivity.kt`, which sits at 1436 LOC (budget 1440, ceiling 1500), `fk` exit 0. The filter carries `addDataScheme("file")` - the media broadcasts are `file://`-scoped and a filter without it never fires, which is the quiet way this kind of receiver fails. Manager 79 LOC against a 160 budget.
- 2026-08-05 - The wiring was reshaped twice, both times by a gate rather than by taste, and both times toward a smaller host surface. First: overriding `onStart`/`onStop` in `MainActivity` hit detekt's `TooManyFunctions`, 40 against a threshold of 40, so the manager became a `DefaultLifecycleObserver`. Then the listener-symmetry gate refused the observer subscription taken in the Activity and released nowhere, so both halves moved into the manager - it takes the subscription in `attach()` and drops it in `onDestroy`. The step asked for "a single wiring call each"; what shipped is one call for both, and `MainActivity` carries no lifecycle code for this feature at all.
- 2026-08-05 - One wasted cycle worth recording: the symmetry gate is lexical over the whole file text, and my own KDoc contained the literal word `addObserver`. It counted as a second subscription and failed a file whose code was already balanced. Prose naming a paired API counts as a use of it.
- 2026-08-05 - Rule 5 miss recorded honestly: `MainActivity.kt` (1428 LOC) was edited before its timestamped backup was taken; the copy under `temp/S1378/` was made straight after, and the pre-edit state was recoverable from git the whole time. Dev log recorded.

---

### Step 06.6 - Add the section strings in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 06.5

**Prompt for developer:**

> Add the section title, the per-volume button content description and the access-request explanation in EN, RU and UK with one lockstep `set-android-string.ps1 -Action add` call per key. The explanation shown before the system access request must satisfy the message formula in `docs/COMMUNICATION_POLICY.md` §2, the next-step rule in §3 and the tone checklist in §6.
>
> Amended 2026-08-05 during execution: a Kotlin or layout reference to a key that does not exist yet fails its own step's build predicate, so each key was created by the step that first referenced it - `removable_volumes_section` in 06.2, the label, the description and the access explanation in 06.3. Four keys rather than three: naming the volume *and* its free space on the button needs a format string of its own. This step is what it always was in substance - the policy and parity pass over all of them.

**Why:**

Strategic §3.1 asks for a short explanation of why the app needs the folder before the system prompt appears, and §3.2 makes EN/RU/UK mandatory for every new string.

**Verification:**

- `Grep` - each new key is present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. Four `removable_*` keys, all present in en/ru/uk, `check_strings_localized -KeyPrefix "removable_"` exit 0. §6 checklist: no raw exception text, no "Are you sure?", no "completed successfully", the access request carries its next step ("pick this volume in the system dialog that opens next"), no emoji, longest string ~120 characters and shown in a dialog body where it wraps. The empty-state line of the checklist does not apply - §3.4 rules the section away entirely when nothing is connected, rather than showing an empty one. RU section title is the owner's own wording, «Съёмные носители». Dev log recorded.

---

## Phase-boundary audit (2026-08-05)

Scope: this phase's `Files Touched`. Layer 1 always; Layer 2 for the receiver's lifecycle window; Layer 3 for the subscription's ownership. Layer 4 not applicable - no Room surface.

- **screenshot deferred (no device).** The UI-phase gate wants a shot of the changed screen taken during the phase. The emulator that was attached when this session started (`emulator-5554`, probed ready at 15:00) was gone by the time the phase closed - `device-ready.ps1` reports `no-device`. The phase's own Done Criteria do not demand the shot, so the deferral is recorded here and the phase closes; the screen still has to be seen before this ticket is called verified, which the `BlockNeedUserTest` hand-off at the end of the plan covers.
- **Layer 2 and 3, clean by construction.** The receiver is registered on `onStart` and released on `onStop`, and the observer subscription is taken and dropped inside the manager itself, so nothing outlives the screen and the host holds no reference to release. Both transitions are idempotent, so a repeated lifecycle edge cannot double-register.
- **Layer 1, one accepted asymmetry.** The registry is read on the main thread through a `lifecycleScope.launch` in the dialog builder; `StorageVolumeRepositoryImpl` moves the actual `StatFs` work to `Dispatchers.IO`, so the main thread only awaits. Accepted rather than moved because the dialog needs the list before it can show the section.
- **Not a finding.** The buttons are added to the container on every dialog build, and the dialog view is inflated fresh each time, so nothing accumulates across openings.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Portrait and landscape dialog layouts both carry the section (portrait 69/71, landscape 242/250).
- [x] Dev log entry added for the phase (one per step closure).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` OK on every Kotlin closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings; the screenshot is deferred with a reason, see the audit block above.

---

## Handoff Notes to Next Phase

Every user-visible surface of the feature is in place. Phase 07 records the shipped capability and regenerates the indexes; the on-device sweep in strategic §3.3 "Validation level" runs against this build.

---

## Rollback Plan

Revert the phase commit and remove the added string keys via `set-android-string.ps1 -Action remove` in all three locales. No schema change is involved.
