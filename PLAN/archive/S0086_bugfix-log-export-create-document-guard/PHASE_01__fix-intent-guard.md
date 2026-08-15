# Phase 01 — Fix Intent Guard

**Strategic spec:** [`../S0086_bugfix-log-export-create-document-guard.md`](../S0086_bugfix-log-export-create-document-guard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** —
**Completed:** —

---

## Objective

Replace the `try/catch(ActivityNotFoundException)` pattern in `launchSaveLogs()` with an upfront `resolveActivity` guard so that devices without a document-picker never throw an exception.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt` | Modified | ≤ 200 |

> File is currently 141 lines — no backup required.

---

## Steps

### Step 01.1 — Replace catch-and-fallback with resolveActivity guard in launchSaveLogs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `GeneralSettingsLogHelper.kt`, replace the entire `launchSaveLogs()` method body with the following guard-and-select pattern:
>
> ```kotlin
> fun launchSaveLogs() {
>     val testIntent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
>         addCategory(android.content.Intent.CATEGORY_OPENABLE)
>         type = "application/zip"
>     }
>     val canSave = testIntent.resolveActivity(fragment.requireActivity().packageManager) != null
>     if (canSave) {
>         saveLogsLauncher.launch("fastmediasorter_logs.zip")
>     } else {
>         Timber.d("LogExport: CREATE_DOCUMENT not supported on this device — using share fallback")
>         showSaveLogsNotSupportedDialog()
>     }
> }
> ```
>
> Remove the old `try { saveLogsLauncher.launch(...) } catch (e: ActivityNotFoundException) { ... }` block entirely. The `showSaveLogsNotSupportedDialog()` private method and the `Timber` import are already present — do not duplicate them.

**Verification:**

- `Grep` — `ActivityNotFoundException` returns **zero** hits in `GeneralSettingsLogHelper.kt`.
- `Grep` — `resolveActivity(` returns exactly **one** hit in `GeneralSettingsLogHelper.kt`.
- `Grep` — `Timber.d("LogExport: CREATE_DOCUMENT not supported` returns exactly **one** hit in `GeneralSettingsLogHelper.kt`.
- `Grep` — `Log\.d\(` returns **zero** hits in `GeneralSettingsLogHelper.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt" "S0086" "Replace ActivityNotFoundException catch with resolveActivity guard in launchSaveLogs"`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `GeneralSettingsLogHelper.launchSaveLogs()` no longer throws `ActivityNotFoundException` on devices without a document picker.
- On supported devices the launcher is called identically to before.
- Phase 02 (docs-catalog-cleanup) may start.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
