# Phase 05 - Permission rationale sheet returns its verdict through FragmentResult

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01-04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

`PermissionRationaleBottomSheet` returns grant/skip through `setFragmentResult` instead of an interface set on
the instance, and the settings screen that consumes it registers its listener in `onViewCreated`, so a verdict
given after the screen is recreated still routes the user onward.

The only conversion in the plan where the callback is set through a setter rather than a factory parameter, and
the only one whose callback is currently owned by a `@Singleton` use case that outlives every fragment.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1331 phase 05"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleBottomSheet.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RequestContextualPermissionUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ 265 |

No file here exceeds 500 lines, so no backup step. No landscape layout work: this phase changes no XML.

---

## Steps

### Step 05.1 - Emit the verdict as a FragmentResult

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleBottomSheet.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `callback` field, the `setCallback` function and the `PermissionRationaleCallback` interface. Add
> companion constants `RESULT_KEY = "permission_rationale_result"`, `RESULT_PERMISSION_ID`, `RESULT_GRANTED`,
> and a private `ARG_REQUEST_KEY`; keep the existing private `ARG_PERMISSION_ID`. Add
> `requestKey: String = RESULT_KEY` to `newInstance` and put it into `arguments`. Add an `onCreate` override
> reading it into a `private var requestKey` field. In both button click handlers replace the
> `callback?.onPermissionRationaleResult(permissionId, ..)` call with
> `setFragmentResult(requestKey, bundleOf(RESULT_PERMISSION_ID to permissionId, RESULT_GRANTED to <true|false>))`,
> leaving `markShownUseCase.invoke(permissionId)` and the trailing `dismiss()` in place and in the same order.

**Verification:**

- `Grep` - `PermissionRationaleCallback` returns zero hits in `PermissionRationaleBottomSheet.kt`.
- `Grep` - `setCallback` returns zero hits in `PermissionRationaleBottomSheet.kt`.
- `Grep` - `setFragmentResult(` matches twice in `PermissionRationaleBottomSheet.kt`.
- `Grep` - `ARG_REQUEST_KEY` matches in `PermissionRationaleBottomSheet.kt`.

**Status:** `[x]` done

---

### Step 05.2 - Reduce the use case to showing the sheet

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RequestContextualPermissionUseCase.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Change `invoke(fragment, entry, onResult)` to `invoke(fragment, entry, requestKey)` returning `Boolean`:
> `false` when `markContextualShownUseCase.isShown(entry.id)` short-circuits and the sheet is not shown, `true`
> when the sheet was shown. Drop the anonymous `PermissionRationaleCallback` object entirely and pass
> `requestKey` into `newInstance`. Keep `sheet.show(fragment.parentFragmentManager, "perm_rationale_${entry.id}")`
> unchanged.
>
> This use case is a `@Singleton`: holding a caller lambda made it retain a fragment-scoped closure for the
> life of the process, which is exactly why the verdict could not survive recreation. The return value replaces
> the `onResult(false)` early-exit branch so the caller keeps its "already shown, go straight on" behaviour.

**Verification:**

- `Grep` - `onResult` returns zero hits in `RequestContextualPermissionUseCase.kt`.
- `Grep` - `PermissionRationaleCallback` returns zero hits across `app_v2/src`.
- `Grep` - `requestKey` matches in `RequestContextualPermissionUseCase.kt`.

**Status:** `[x]` done

---

### Step 05.3 - Register the listener on the settings screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> In the helper, add `fun registerRationaleListener()` that calls
> `fragment.parentFragmentManager.setFragmentResultListener(PermissionRationaleBottomSheet.RESULT_KEY, fragment.viewLifecycleOwner) { _, _ -> navigateToPermissionsManagement() }`.
> Update `handleLocalFilesPermissionActionContextual` (the function containing the current
> `requestContextualPermission.invoke(fragment, entry) { navigateToPermissionsManagement() }` line at
> `GeneralSettingsPermissionsHelper.kt:28`) to call the new three-argument `invoke` with the sheet's
> `RESULT_KEY` and, when it returns `false`, call `navigateToPermissionsManagement()` directly - preserving
> today's behaviour for the already-shown case.
>
> In `GeneralSettingsFragment.onViewCreated`, call `permissionsHelper.registerRationaleListener()`. The helper
> is created `by lazy`, so registration must be triggered explicitly from the fragment's lifecycle rather than
> from the helper's construction - a lazily built helper would otherwise register only after the user has
> already tapped, which is the defect this ticket removes.

**Verification:**

- `Grep` - `setFragmentResultListener(` matches in `GeneralSettingsPermissionsHelper.kt`.
- `Grep` - `registerRationaleListener` matches in both `GeneralSettingsPermissionsHelper.kt` and `GeneralSettingsFragment.kt`.
- `Grep` - `invoke(fragment, entry) {` returns zero hits across `app_v2/src`.

**Status:** `[x]` done

---

### Step 05.4 - Delete the unused injection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> `PermissionsManagementFragment` declares `@Inject lateinit var requestContextual: RequestContextualPermissionUseCase`
> at line 40 and never reads it - the only reference in the file is the declaration itself. Delete the field and
> its now-unused import (Rule 20, dead-weight hygiene). Do not change anything else in this fragment.

**Verification:**

- `Grep` - `requestContextual` returns zero hits in `PermissionsManagementFragment.kt`.
- `Grep` - `RequestContextualPermissionUseCase` returns zero hits in `PermissionsManagementFragment.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] No `Timber.d("S1331` probe was added by this phase. The six probes were inserted once, at the final `BlockNeedUserTest` transition.
- [x] `Grep` - `Log.d(` returns zero hits in all five touched files.
- [x] Dev log entry added. One entry for the ticket, not one per touched file - CLAUDE.md journaling granularity.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - a public interface was removed and two signatures changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Last conversion phase. All five dialogs now deliver results through the FragmentManager; the only remaining
field-held dialog callbacks in `app_v2/src` are the two declared out of scope in INDEX.

---

## Rollback Plan

Revert the phase commit. No data migration, no schema change. The permission rationale sheet's on-screen
content and button behaviour are unchanged.
