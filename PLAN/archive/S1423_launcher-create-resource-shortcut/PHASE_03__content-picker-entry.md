# Phase 03 - Content-picker entry point

**Strategic spec:** [`../S1423_launcher-create-resource-shortcut.md`](../S1423_launcher-create-resource-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Put a "Create new.." item on the resource-selection step of the launcher content picker, opted into by the launcher host alone, routed through the Phase 02 shared call.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `LauncherResourceCreateManager` exists and is injectable (Phase 02).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt` | Modified | ≤ 40 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 20 |

> `LauncherHomeActivity.kt` is 833 LOC - over 500, so it carries a Rule 5 backup sub-step. It stays well under the Rule 2 1500-LOC ceiling.
>
> **Flavor placement.** `ResourcePickerDialogFragment` is in `src/main` because the app-launch-panel editor (`EditAppLaunchPanelActivity`, all flavors) uses it too. It is not moved and gets no `BuildConfig` guard: the new item is behind an argument that defaults to off, and only the `launcherEnabled` host turns it on. This is the Rule 14 shape - shared contract in `src/main`, the decision in the flavor-mounted source set.
>
> **Landscape parity (Rule 11).** No `res/layout*` file is edited in this phase; the picker reuses `dialog_searchable_option_picker.xml` unchanged.

---

## Steps

### Step 03.1 - Add the picker item string in EN/RU/UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add key `launcher_create_resource_picker_item` across all three locales in one lockstep call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_create_resource_picker_item -En "Create new.." -Ru "Создать новый.." -Uk "Створити новий.."
> ```
>
> The Russian text is the owner's own wording in strategic §3.3 and is not to be reworded. Two dots, not an ellipsis character - house text style. Check EN and UK against `docs/COMMUNICATION_POLICY.md` §2 for the message type and §6 for the tone checklist before running the command.

**Why:**

Strategic §3.2 makes EN/RU/UK mandatory for the entry-point label, and §3.3 fixes the Russian wording as the owner wrote it, so the key must be created in all three locales at once rather than translated later.

**Verification:**

- `Grep` - `launcher_create_resource_picker_item` matches exactly once in each of the three `strings.xml` files.
- `Grep` - the RU value is exactly `Создать новый..` (two dots, no `…`).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_create_resource"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 03.2 - Give the resource picker an opt-in "Create new.." row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add an argument `ARG_ALLOW_CREATE_NEW` read in `onCreate` into a private `allowCreateNew: Boolean`, defaulting to `false`, and a new `newInstance(requestKey: String, allowCreateNew: Boolean)` factory. Leave the three existing factories behaving exactly as they do now, so `EditAppLaunchPanelActivity` and every other caller see no change.
>
> When `allowCreateNew` is true, prepend one `Option` to the list `buildOptions` returns: id `OPTION_CREATE_NEW`, label `getString(R.string.launcher_create_resource_picker_item)`, leading `LeadingVisual.IconRes(R.drawable.ic_add)`. First position, so it is the first thing D-pad focus reaches.
>
> In `onResourcePicked`, branch on that id before the `toLong()` conversion - `OPTION_CREATE_NEW` is not a row id and would crash it. For that id set the fragment result with `RESULT_CREATE_NEW` = true and no `RESULT_RESOURCE_ID`, then dismiss; everything else keeps today's bundle unchanged. Expose `RESULT_CREATE_NEW` as a public const so the host can read it.
>
> Keep the dialog free of any knowledge of what creating a resource means - it reports the choice and stops there.

**Why:**

Strategic §3.3 places one entry point on the resource-selection step of the content picker, while §3.2 confines the entry point to the flavors that carry the home screen; this dialog is shared with the all-flavor app-launch-panel editor, so the item has to be an opt-in the launcher host asks for rather than a row the dialog always shows.

**Verification:**

- `Grep` - `ARG_ALLOW_CREATE_NEW` and `RESULT_CREATE_NEW` each match in `ResourcePickerDialogFragment.kt`.
- `Grep` - `allowCreateNew: Boolean = false` or an equivalent `getBoolean(ARG_ALLOW_CREATE_NEW, false)` matches, proving the default is off.
- `Grep` - `R.string.launcher_create_resource_picker_item` matches exactly once in that file.
- `Grep` - `BuildConfig` returns zero hits in that file (Rule 14: no flavor guard in `src/main`).
- `Grep` - `AddResourceActivity` returns zero hits in that file (the dialog does not launch creation itself).
- `Grep` - `allowCreateNew` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt` - the panel editor is untouched.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 03.3 - Opt the launcher host in and route the result

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Back up `LauncherHomeActivity.kt` to `temp/S1423/` first (Rule 5, 833 LOC).
>
> In `registerAddFlowListeners`, the `CATEGORY_RESOURCE` branch opens `ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_SHORTCUT)`. Switch it to the new factory with `allowCreateNew = true`. Change nothing about the two other picker openings in this file: `REQ_RESOURCE_GADGET` picks the resource a gadget will read, and it is not a placement step.
>
> In `registerResourceListeners`, the `REQ_RESOURCE_SHORTCUT` listener currently reads `RESULT_RESOURCE_ID` and opens the mode picker. Read `RESULT_CREATE_NEW` first: when true, call the injected `LauncherResourceCreateManager` and return without opening the mode picker. Inject the manager with `@Inject lateinit var`.
>
> Do not touch `LauncherStartMenuFragment.openResourcePicker` - that picker opens a resource for browsing rather than placing a cell, and the owner's ruling puts the item on the content picker's resource step, not on every resource list.

**Why:**

Strategic §3.3 names the content picker's resource-selection step as an entry point and §5.1.1 requires it to reach the same shared launch as the menu row, so the host opts the dialog in and hands the answer straight to the one call rather than building a second path into creation.

**Verification:**

- `Grep` - `allowCreateNew = true` matches exactly once in `LauncherHomeActivity.kt`.
- `Grep` - `RESULT_CREATE_NEW` matches in that file.
- `Grep` - `LauncherResourceCreateManager` matches in that file.
- `Grep` - `AddResourceActivity` returns zero hits in that file - creation is launched only through the shared manager.
- `Grep` - `allowCreateNew` returns zero hits in `LauncherStartMenuFragment.kt`.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0 and `.\a.ps1 fkn` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). **UNPROVEN** - no gradle was run in this session (run-level deviation). Not a failure; not looked at.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`. **DEFERRED** - single batched closure is the owner's.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` if a public signature changed. **DEFERRED** - part of the same batched closure; `ResourcePickerDialogFragment` did gain a public factory and a public const.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`). Layer trigger that fires here: a shared UI component gains a branch used by one host only - check the untouched hosts still behave identically.

---

## Handoff Notes to Next Phase

Both in-scope entry points now reach `LauncherResourceCreateManager`. The third (long-press on an empty desktop, S1466) adds a caller and needs no further change here. The strategic acceptance criteria that only a device can settle are listed in `INDEX.md` under "Device-only acceptance criteria" - Phase 04 does not turn them into Greps.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no schema change. The `ResourcePickerDialogFragment` change is additive and defaults to off, so reverting cannot affect the app-launch-panel editor.

---

## Step Log

- 2026-08-07 - Step 03.1 - Verification 4\4 PASS (key present exactly once in each of the three `strings.xml`; RU value is literally `Создать новый..` with two dots and no `…`; `check_strings_localized.ps1 -KeyPrefix launcher_create_resource` exit 0 over both keys; COMMUNICATION_POLICY §6 clean). Added via `set-android-string.ps1 -Action add`. Files: `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`. Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 03.2 - Verification 7\8 PASS (`ARG_ALLOW_CREATE_NEW` and `RESULT_CREATE_NEW` present; `getBoolean(ARG_ALLOW_CREATE_NEW, false)` proves the default is off; `R.string.launcher_create_resource_picker_item` exactly once; `BuildConfig` zero hits; `AddResourceActivity` zero hits; `allowCreateNew` zero hits in `EditAppLaunchPanelActivity.kt`; `Log.d(` zero hits). `.\a.ps1 fk` NOT RUN (run-level deviation). The new `newInstance(requestKey: String, allowCreateNew: Boolean)` does not clash with `newInstance(requestKey: String, mediaTypeFilter: MediaType?)` - different second parameter type. The existing `Timber.d("S1413: ..")` probe was left in place: `select.ps1 -Id S1413` reports `BlockNeedUserTest`, so its tag is live, not stale. Files: `ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt` (+37 LOC). Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 03.3 - Verification 5\6 PASS (`allowCreateNew = true` exactly once; `RESULT_CREATE_NEW` present; `LauncherResourceCreateManager` present; `AddResourceActivity` zero hits - creation is launched only through the shared manager; `allowCreateNew` zero hits in `LauncherStartMenuFragment.kt`; `Log.d(` zero hits). `.\a.ps1 fk` / `fkn` NOT RUN (run-level deviation). `REQ_RESOURCE_GADGET` and `LauncherStartMenuFragment.openResourcePicker` were left on the old factories, as the prompt requires. Files: `src/launcherEnabled/.../ui/launcher/LauncherHomeActivity.kt` (+12 LOC). Backup: `temp/S1423/LauncherHomeActivity.kt.20260807_200622.backup`. Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Phase 03 boundary audit (CLAUDE.md §13). The trigger - a shared component gains a one-host branch - checked against every untouched host: `EditAppLaunchPanelActivity` uses `newInstance(slotIndex: Int)`, `LauncherStartMenuFragment.openResourcePicker` uses `newInstance(requestKey)`, and the gadget flow uses `newInstance(requestKey, mediaTypeFilter)`; none sets `ARG_ALLOW_CREATE_NEW`, so `getBoolean(.., false)` gives them the list they had, and `onResourcePicked` cannot reach the new branch because no option carries `OPTION_CREATE_NEW`. Layer 1: the dialog still reports a choice and nothing more - it neither knows nor launches creation. Layer 2/3: the new `LauncherHomeActivity` branch runs inside the existing `setFragmentResultListener` registered with `this` as `LifecycleOwner`, so no new listener lifetime is introduced. Flavor isolation: the only `src/main` change is behind an argument defaulting to off, with no `BuildConfig` read; the opt-in itself is in `src/launcherEnabled`. **No P0/P1 findings.** P3 noted, not acted on: a create-new result reaching a host that does not expect it would be ignored - both untouched readers require `RESULT_RESOURCE_ID > 0`.
- 2026-08-07 - Static source gates re-run after the last edit: `scripts/quality/assert-neuroslop.ps1` exit 0, "PASS (all rules at or below baseline)" over 3623 files. No gradle involved.
- 2026-08-07 - S1338 UI-phase gate: placement decision IS on record - strategic §3.3, owner's ruling of 2026-08-07 naming the content picker's resource-selection step as entry point 1. Screenshot deferred: no build was permitted this run. Phase left `🚧 In Progress` for that plus the compile / dev-log / catalog criteria.

---

## Step Log - phase closure

- 2026-08-07 - Compile criterion PROVEN, by the owner session rather than the implementation run: `.\a.ps1 fk` (standard) exit 0, `check-standard-fast.ps1 -Mode Code -Flavor Lite` exit 0 - which is the one that matters here, because `ResourcePickerDialogFragment` lives in `src/main` and compiles into the four flavors that mount `src/launcherDisabled` - and `.\a.ps1 dq` exit 0, a full debug build whose kapt pass is what actually validates the Hilt graph the new `@Singleton CreatedResourcePinManager` joins (a Kotlin compile alone would not have).
- 2026-08-07 - Two detekt findings introduced by this phase were fixed before closure: `SpacingBetweenDeclarationsWithAnnotations` on the new `@Inject` field and `SpacingBetweenDeclarationsWithComments` on the new event declaration. Scoped gate afterwards: `assert-detekt -Gate -ChangedFiles <all seven Kotlin files>` -> `PASS [scoped]`.
- 2026-08-07 - Dev log, catalog regen and the `ALL_FEATURES` record ran as one batched closure in Phase 04: `post-change: PASS WITH ADVISORIES (1)`, the single advisory being detekt-preflight attributing findings to files outside this change (verified by the scoped run above).
- 2026-08-07 - **UI-gate screenshot: DEFERRED, and here is exactly why.** An emulator run was attempted against the fresh APK. It never reached the launcher desktop: the walk toggled "Use as home screen" and worked through the resulting cascade of system permission dialogs, but the system "Home app" chooser still offered only Pixel Launcher - our app was absent, meaning `LauncherHomeActivity` was still disabled and the in-app path that enables the component had not actually run. The emulator then dropped offline mid-walk and did not return. The placement decision itself IS on record (strategic 3.3, owner ruling of 2026-08-07), so the first half of the S1338 gate holds; the visual half moves to the device test this ticket is now parked for, and its status note carries the setup sequence.