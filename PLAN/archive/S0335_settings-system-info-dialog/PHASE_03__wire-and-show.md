# Phase 03 - Wire and Show

**Strategic spec:** [`../S0335_settings-system-info-dialog.md`](../S0335_settings-system-info-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Field-inject `GatherSystemInfoUseCase` into the General settings fragment, pass it to the diagnostics helper, and bind `btnSystemInfo` to gather the summary off the main thread and show it in the existing scrollable/copy/share dialog.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt` | Modified | ≤ 300 |

> Both files are <500 lines today; if a touched file projects >500 lines after edit, create a timestamped backup in `temp/` first.

---

## Steps

### Step 03.1 - Inject the use case and extend the diagnostics helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `GeneralSettingsFragment`, add `@Inject lateinit var gatherSystemInfoUseCase: GatherSystemInfoUseCase` (the fragment is already a Hilt entry point). Pass it into the `GeneralSettingsLogHelper` lazy initializer by adding a constructor parameter `gatherSystemInfoUseCase: GatherSystemInfoUseCase` to the helper. In `GeneralSettingsLogHelper`, add a `showSystemInfoDialog()` that mirrors `showLogDialog()`: launch on `viewLifecycleOwner.lifecycleScope`, gather the summary on `Dispatchers.IO` via the use case, guard `fragment.isAdded && fragment.view != null`, then call `DialogUtils.showScrollableDialog(context, getString(R.string.settings_system_info_title), summary, getString(R.string.close))`. Bind `binding.btnSystemInfo?.setOnClickListener { showSystemInfoDialog() }` inside `setupButtons()`.

**Verification:**

- `Grep` - `gatherSystemInfoUseCase: GatherSystemInfoUseCase` present in both the fragment and the helper.
- `Grep` - `fun showSystemInfoDialog` matches once in the helper.
- `Grep` - `btnSystemInfo` click binding present in the helper `setupButtons`.
- `Grep -n "Log\.d\("` on both files returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS (frag field=1, helper param=1, showSystemInfoDialog=1, btnSystemInfo binding=1, Log.d=0). assembleStandardDebug SUCCESSFUL (binding btnSystemInfo + Hilt graph compiled). Dev log recorded.

---

### Step 03.2 - Insert BlockNeedUserTest probe tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Insert one `Timber.d("S0335: system info dialog opened")` at the entry of `showSystemInfoDialog()` (the single changed flow entry). This is the operator's logcat probe for device verification - it stays only while the ticket is `BlockNeedUserTest`. Do not add the tag anywhere else and do not place a ticket id in any retained log line.

**Verification:**

- `Grep` - `Timber.d("S0335:` matches exactly once across all `.kt` files.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. `Timber.d("S0335:` matches exactly once across all .kt (probe at showSystemInfoDialog entry). assembleStandardDebug SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `catalog_sync.ps1 -Module app_v2` run (deferred to Phase 04 batch).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Feature is functional end-to-end: button opens the scrollable dialog with the system summary; copy/share work via the reused dialog. The `S0335:` probe tag is in place for device testing. Phase 04 does catalog/dev-log/FEATURES cleanup and flips the ticket to `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s) - no data migration; UI button reverts to inert state from Phase 02.
