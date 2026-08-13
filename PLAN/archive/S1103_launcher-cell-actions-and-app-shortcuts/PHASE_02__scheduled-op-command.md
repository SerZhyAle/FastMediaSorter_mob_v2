# Phase 02 - Scheduled-Op Command

**Strategic spec:** [`../S1103_launcher-cell-actions-and-app-shortcuts.md`](../S1103_launcher-cell-actions-and-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-22
**Completed:** 2026-07-22

**Step Log:**

- 2026-07-22 - 02.1-02.3 grep-verified (ScheduledOp variant + op: codec + tolerant decode; live label synth via ScheduledOperationRepository + resource names; generic executor no-op branch). Compiled.

---

## Objective

Introduce a `LauncherCellCommand.ScheduledOp(operationId)` cell command (its own `op:` namespace, no Room migration), synthesize its label live, and keep the generic executor exhaustive without launching it there.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 140 |

> No Room schema change - the new command encodes into the existing single TEXT `target` column.

---

## Steps

### Step 02.1 - ScheduledOp command variant + codec

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `data class ScheduledOp(val operationId: Long) : LauncherCellCommand` with `encode() = "$PREFIX_SCHEDULED_OP$operationId"` and a companion `const val PREFIX_SCHEDULED_OP = "op:"`. In `decode`, add a `value.startsWith(PREFIX_SCHEDULED_OP) -> value.removePrefix(PREFIX_SCHEDULED_OP).toLongOrNull()?.let { ScheduledOp(it) }` branch (a non-numeric payload yields null, matching the tolerant decode contract). Update the KDoc prefix list with `- op:<id> - trigger a saved scheduled operation.`.

**Verification:**

- `Grep` - `data class ScheduledOp(val operationId: Long)` and `PREFIX_SCHEDULED_OP = "op:"` present.
- `Grep` - `startsWith(PREFIX_SCHEDULED_OP)` in `decode`.

**Status:** `[x]` done

---

### Step 02.2 - Live label for a scheduled-op cell

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `private val scheduledOperationRepository: ScheduledOperationRepository`. In the `invoke` `when`, add `is LauncherCellCommand.ScheduledOp -> scheduledOpVisual(command.operationId)`. Implement `private suspend fun scheduledOpVisual(operationId: Long): LauncherCommandVisual?`: get the op via `scheduledOperationRepository.getById(operationId)` (return null if gone - the grid renders it unavailable); resolve source and (nullable) target resource names via `resourceRepository.getResourceById`; build a label like `"<TYPE>: <source> -> <target>"` (use the target only when non-null; `->` is fine in a runtime string, this is not doc prose) using `context.getString(R.string.launcher_cell_scheduled_op_label, ...)` with the op type name and resource names; `iconRes = R.drawable.ic_schedule`. Keep it null-tolerant (a missing resource name falls back to its id or a dash).

**Verification:**

- `Grep` - `is LauncherCellCommand.ScheduledOp ->` in the `invoke` `when`.
- `Grep` - `scheduledOperationRepository: ScheduledOperationRepository` in the constructor.
- `Grep` - `R.drawable.ic_schedule` referenced in the scheduled-op path.

**Status:** `[x]` done

---

### Step 02.3 - Keep the generic executor exhaustive

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `launch`'s `when(command)`, add `is LauncherCellCommand.ScheduledOp -> false`. Add a one-line WHY comment: a scheduled op needs a confirmation before running (it may modify or delete files), so it is handled in the launcher UI path (ViewModel), never launched generically here.

**Verification:**

- `Grep` - `is LauncherCellCommand.ScheduledOp -> false` in `ExecuteLauncherCommandUseCase.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (command model + label use case changed).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (Layer 2: label resolution stays on the use case's IO dispatcher).

---

## Handoff Notes to Next Phase

`ScheduledOp` cells encode/decode and label themselves; Phase 03 adds the picker, the confirm-and-run flow, and the add-flow wiring.

---

## Rollback Plan

Revert the phase commit(s) - one sealed-interface case + codec + label branch; tolerant decode means an old desktop with no such cell is unaffected.
