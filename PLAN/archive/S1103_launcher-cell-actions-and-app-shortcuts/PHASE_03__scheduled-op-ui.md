# Phase 03 - Scheduled-Op UI

**Strategic spec:** [`../S1103_launcher-cell-actions-and-app-shortcuts.md`](../S1103_launcher-cell-actions-and-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-22
**Completed:** 2026-07-22

**Step Log:**

- 2026-07-22 - 03.1-03.4 grep-verified (9 strings EN/RU/UK; ViewModel ConfirmScheduledOp event + intercept + executeScheduledOp; new LauncherScheduledOpPickerDialogFragment reusing the label resolver + empty-state; CATEGORY_SCHEDULED_OP; Activity add-flow wiring + confirm dialog). `.\a.ps1 fc` BUILD SUCCESSFUL. Audit: destructive tap gated by confirm, op runs off main thread, no P0/P1.

---

## Objective

Let the user assign a saved scheduled operation to a desktop cell (new category + picker), and on tap confirm-then-run it in the background with a result toast.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` (+ values-ru, values-uk) | Modified | - |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 420 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherScheduledOpPickerDialogFragment.kt` | New | ≤ 150 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 760 |

---

## Steps

### Step 03.1 - Strings for the category, confirm dialog and toasts

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these keys EN/RU/UK via `set-android-string.ps1 -Action add` (one call per key), COMMUNICATION_POLICY §6:
> - `launcher_edit_kind_scheduled_op` = "Scheduled operation" / "Операция по расписанию" / "Операція за розкладом"
> - `launcher_cell_scheduled_op_label` = "%1$s: %2$s -> %3$s" / same / same (format: type, source, target - a positional format string, keep it identical across locales)
> - `launcher_scheduled_op_pick_title` = "Pick a scheduled operation" / "Выберите операцию" / "Виберіть операцію"
> - `launcher_scheduled_op_none` = "No scheduled operations yet" / "Пока нет операций по расписанию" / "Поки немає операцій за розкладом"
> - `launcher_scheduled_op_confirm_title` = "Run this operation?" / "Запустить операцию?" / "Запустити операцію?"
> - `launcher_scheduled_op_confirm_message` = "It may copy, move or delete files as configured." / "Она может копировать, перемещать или удалять файлы согласно настройке." / "Вона може копіювати, переміщати або видаляти файли згідно з налаштуванням."
> - `launcher_scheduled_op_started` = "Operation started.." / "Операция запущена.." / "Операцію запущено.."
> - `launcher_scheduled_op_done` = "Operation finished" / "Операция завершена" / "Операцію завершено"
> - `launcher_scheduled_op_failed` = "Operation failed" / "Операция не выполнена" / "Операцію не виконано"

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_scheduled_op"` - exit 0.
- `Grep` - `launcher_edit_kind_scheduled_op` in all three `values*/strings.xml`.

**Status:** `[x]` done

---

### Step 03.2 - ViewModel: intercept, confirm event, run in background

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend `sealed interface LauncherHomeEvent` with `data class ConfirmScheduledOp(val operationId: Long) : LauncherHomeEvent`. Inject `private val executeScheduledOperation: ExecuteScheduledOperationUseCase`. In `onCellTapped`, before the generic `run(command)`, decode the command and when it is `LauncherCellCommand.ScheduledOp` send `_events.send(LauncherHomeEvent.ConfirmScheduledOp(command.operationId))` and return (do NOT call `run`, so the `launchInFlight` activity guard is untouched). Add `fun executeScheduledOp(operationId: Long)` that launches in `viewModelScope`: send `Message(R.string.launcher_scheduled_op_started)`, then `val result = executeScheduledOperation(operationId)`, then `Message(if (result.isSuccess) R.string.launcher_scheduled_op_done else R.string.launcher_scheduled_op_failed)`. The use case runs its own IO work; do not add a dispatcher here.

**Verification:**

- `Grep` - `data class ConfirmScheduledOp(val operationId: Long)` present.
- `Grep` - `executeScheduledOperation: ExecuteScheduledOperationUseCase` in the constructor.
- `Grep` - `fun executeScheduledOp(operationId: Long)` sends started then done/failed messages.
- `Grep` - `is LauncherCellCommand.ScheduledOp` handled inside `onCellTapped`.

**Status:** `[x]` done

---

### Step 03.3 - Scheduled-op picker + content-picker category

**Files:** `LauncherScheduledOpPickerDialogFragment.kt` (new), `LauncherCellContentPickerDialogFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `LauncherScheduledOpPickerDialogFragment` mirroring `LauncherStreamPickerDialogFragment`: inject `ScheduledOperationRepository` + `ResourceRepository`, snapshot `getAll().first()`, build `Option`s (id = `operationId.toString()`, label = type + source/target resource names resolved via `getResourceById`, `leading = IconRes(R.drawable.ic_schedule)`), title `R.string.launcher_scheduled_op_pick_title`; when the list is empty set `binding.tvOptionsEmpty.text = getString(R.string.launcher_scheduled_op_none)` and `isVisible = true`. On pick, `setFragmentResult(RESULT_KEY, bundleOf(RESULT_OPERATION_ID to id.toLong()))`. Add `CATEGORY_SCHEDULED_OP = "scheduled_op"` to `LauncherCellContentPickerDialogFragment.categoryOptions()` (label `R.string.launcher_edit_kind_scheduled_op`, icon `R.drawable.ic_schedule`).

**Verification:**

- `Glob` - `LauncherScheduledOpPickerDialogFragment.kt` exists.
- `Grep` - `CATEGORY_SCHEDULED_OP` in `LauncherCellContentPickerDialogFragment.kt` `categoryOptions`.
- `Grep` - `RESULT_OPERATION_ID` in the new picker.

**Status:** `[x]` done

---

### Step 03.4 - Activity: add-flow wiring + confirm dialog

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `registerAddFlowListeners`, add a `CATEGORY_SCHEDULED_OP` branch that `openPicker(LauncherScheduledOpPickerDialogFragment.newInstance(), TAG)`, and a result listener on its `RESULT_KEY` that reads `RESULT_OPERATION_ID` and `addShortcut(LauncherCellCommand.ScheduledOp(operationId))`. In the `viewModel.events` collector, add `is LauncherHomeEvent.ConfirmScheduledOp ->` showing a `MaterialAlertDialogBuilder` (title `launcher_scheduled_op_confirm_title`, message `launcher_scheduled_op_confirm_message`, positive -> `viewModel.executeScheduledOp(event.operationId)`, negative `R.string.cancel`; do not style buttons per-call - the theme maps them per S0538). Keep the `when` exhaustive.

**Verification:**

- `Grep` - `CATEGORY_SCHEDULED_OP ->` and `LauncherScheduledOpPickerDialogFragment` in `LauncherHomeActivity.kt`.
- `Grep` - `is LauncherHomeEvent.ConfirmScheduledOp ->` and `viewModel.executeScheduledOp(` present.
- `Grep` - `addShortcut(LauncherCellCommand.ScheduledOp(` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new picker class).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (destructive-op tap gated by a confirm dialog; op runs off the main thread; the generic launch guard is untouched).

---

## Handoff Notes to Next Phase

Scheduled operations are now assignable to cells and run confirm-then-background. Phase 04 records + regenerates.

---

## Rollback Plan

Revert the phase commit(s) - additive category, picker, event and confirm dialog; existing cell kinds untouched.
