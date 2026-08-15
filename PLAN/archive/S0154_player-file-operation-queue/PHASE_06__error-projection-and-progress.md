# Phase 06 — Error Projection, Progress Reuse, Optional Queue Indicator

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 03, Phase 05
**Blocks:** —
**Steps done:** 2 / 4
**Started:** 2026-05-11
**Completed:** —

---

## Objective

Finish result projection: replace placeholder copy with calm per-file error messages (EN/RU/UK) carrying a retry affordance, reuse the existing S0074 operation-progress mechanism to show the in-flight queued operation, restore the started/success toasts on queue events, and add the optional "N in queue" indicator (TalkBack-readable). Clear all `TODO(phase-06)` tags.

---

## Prerequisites

- [ ] Phase 03 ✅ Done, Phase 05 ✅ Done.
- [ ] Review `docs/COMMUNICATION_POLICY.md` §2 (error / progress / next-step formulas) and §6 (tone checklist) before writing any string.
- [ ] Locate the S0074 progress surface (`FileOperationProgressDialog` / the progress mechanism it uses) and confirm how a long-lived background op feeds it — the queue must drive it without opening a modal blocking dialog (strategic §5.1.E).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 950 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperationQueue.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Modified | ≤ 640 |
| `app_v2/src/main/res/layout/activity_player.xml` | Modified (only if a new indicator view is added) | — |
| `app_v2/src/main/res/layout-land/activity_player.xml` | Modified (mirror of the above) | — |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> If Step 06.4 (indicator) is skipped (it is optional), the two `activity_player.xml` rows do not apply. If it is implemented and a landscape variant of `activity_player.xml` exists, the landscape file **must** get the equivalent change in the same step — never portrait-only.

---

## Steps

### Step 06.1 — Per-file error strings (EN/RU/UK) + retry affordance

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a per-operation failure string family keyed `error_queued_operation_*` with a `%1$s` file-name placeholder, one variant per operation kind if the wording differs (move / delete / rename) or a single `error_queued_operation_failed` with the file name if one calm sentence covers all — prefer the single key unless the policy reviewer wants per-kind. Add a retry action label (`action_retry` if not already present — reuse if it exists). Wording per `COMMUNICATION_POLICY.md` §2 error formula + next-step: e.g. «Не удалось переместить файл «%1$s». Попробовать ещё раз?» — calm, names the file, no stack-trace tone. Apply Author Style. Add the same keys to all three `strings.xml`.

**Verification:**

- `Grep` — `error_queued_operation` keys match in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (same set in each).
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_queued_operation"` exits 0.
- Strings pass `COMMUNICATION_POLICY.md` §6 checklist (manual gate before commit).

**Status:** `[x]` done

---

### Step 06.2 — Project `Failed` events into a per-file message + retry

**Files:** `PlayerManagerInitializer.kt`, `FileOperationsHandler.kt` (if retry re-enqueue lives there)
**Depends on:** Step 06.1

**Prompt for developer:**

> In the queue event listener: on `Failed(op, _, retryable)` show the `error_queued_operation_*` message with `op.displayName`; if `retryable`, offer the retry action (snackbar action or a short dialog) that re-enqueues a fresh `PlayerFileOperation` built from the same `op` (do **not** re-add the file to the list — per ADR-3 the optimistic removal stands; the retry just re-runs the operation). Keep the S0094 "deliberately interrupted flow" cancellations silent — if the queue ever surfaces a `CancellationException`-class failure, do not toast it (the queue should classify these as non-`retryable` and the listener skips the message). One operation's failure must not stop the queue (already guaranteed in Phase 02) — the listener just shows the message and the next op proceeds. Remove the `TODO(phase-06)` markers in this listener.

**Verification:**

- `Grep` — `error_queued_operation` key referenced from `PlayerManagerInitializer.kt`.
- `Grep` — `TODO(phase-06)` returns zero hits in `PlayerManagerInitializer.kt`.
- `Grep` — a retry path re-enqueues: `enqueue(` appears in the `Failed` handling code path.

**Status:** `[x]` done

---

### Step 06.3 — Started/success toasts + S0074 progress reuse

**Files:** `PlayerManagerInitializer.kt`, `PlayerFileOperationQueue.kt`, `FileOperationsHandler.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Move the started toast (`msg_move_started` / `msg_copy_started` analogues) and success toast (`msg_move_success` / `msg_delete_success`) from the old inline `FileOperationsHandler` bodies and the Phase 03 placeholder onto the queue events: `Started` → started toast (or fold into the indicator), `Succeeded` → success toast (gated by "activity not gone", like today). Remove the temporary `TODO(phase-06)` enqueue toast added in Phase 03 Step 03.2. Feed the in-flight operation into the existing S0074 progress mechanism — surface progress non-modally (the existing progress UI for player operations), never a blocking dialog on enqueue. Reuse existing `msg_*` string keys where they fit; only add new ones if a queued operation needs distinct wording — if so, all three `strings.xml`.

**Verification:**

- `Grep` — `msg_move_started` / `msg_move_success` (or their queued equivalents) are referenced from the queue event listener, not from `FileOperationsHandler.performMove`.
- `Grep` — `TODO(phase-06)` returns zero hits across `app_v2/`.
- `Grep` — `FileOperationsHandler.kt` no longer contains `Toast.makeText` calls inside `performMove` / `performMoveToPath` / `performDelete`.
- Project compiles — run `/build`.

**Status:** `[~]` in progress — queue toasts and retry snackbar are wired; non-modal S0074 progress reuse still remains.

---

### Step 06.4 — (Optional) "N in queue" indicator

**Files:** `activity_player.xml` + `layout-land/activity_player.xml`, `PlayerManagerInitializer.kt`, `values/strings.xml` + `_ru` + `_uk`
**Depends on:** Step 06.3

**Prompt for developer:**

> Optional per strategic §3.1 — implement only if time permits in this iteration; otherwise mark the step `⏭️ Skipped` in INDEX with a note. If implemented: add a small, unobtrusive count badge ("N в очереди") bound to `playerFileOperationQueue.pendingCount` — visible only when `count > 0`. Place it where it does not collide with existing player chrome (resolve placement via `/ui-clarify` if ambiguous — do not guess). Add a `contentDescription` (plural-aware) so TalkBack reads it. Add the badge string (`label_queue_pending_count` with `%1$d`) to all three `strings.xml`. If `activity_player.xml` has a `layout-land/` counterpart, apply the equivalent change there in the same step.

**Verification:**

- If implemented: `Grep` — `pendingCount` referenced from `PlayerManagerInitializer.kt`; the badge id appears in both `activity_player.xml` and `layout-land/activity_player.xml` (if the land variant exists); `label_queue_pending_count` in all three `strings.xml`; `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "label_queue_pending_count"` exits 0.
- If skipped: INDEX row for this step shows `⏭️ Skipped` with a one-line reason; no `activity_player.xml` change.

**Status:** `⏭️` skipped — optional queue-count indicator deferred for this iteration.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done` or `⏭️ Skipped` (06.4 only).
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits anywhere under `app_v2/`.
- [x] `Grep` for `TODO(phase-05)` and `TODO(phase-04)` and `TODO(phase-03)` all return zero hits.
- [x] New strings pass `COMMUNICATION_POLICY.md` §6 checklist.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_queued_operation"` exits 0 (and `label_queue_pending_count` if 06.4 implemented).
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Behaviour-complete. Phase 07 is docs + catalog + changelog only — no code.

---

## Rollback Plan

Revert phase commit(s). The queued flow falls back to Phase 05 state (functional, placeholder copy). No data migration.
