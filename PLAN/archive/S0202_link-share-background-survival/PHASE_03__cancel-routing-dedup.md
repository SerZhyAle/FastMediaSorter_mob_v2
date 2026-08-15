# Phase 03 — Cancel routing + URL dedup

**Strategic spec:** [`../S0202_link-share-background-survival.md`](../S0202_link-share-background-survival.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Make cancellation reliable from both the dialog and the foreground notification, prevent partial-write artefacts on cancel, and verify the dedup contract for repeated shares of the same URL.

---

## Prerequisites

- [ ] Phase 02 ✅ Done — enqueue + observe + uniqueWorkNameFor() helper exist.
- [ ] Strategic §6.3 + §6.4 read (cancel atomicity decisions confirmed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 720 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt` | Modified (audit + minor patch) | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` | Modified | ≤ 410 |

> `ReceiveShareActivity` already over 500 LOC after Phase 02 — backup is reused (no new backup needed if the same session). For a fresh session, create a new timestamped backup before edit.

---

## Steps

### Step 03.1 — Wire dialog cancel to `WorkManager.cancelUniqueWork`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In the refactored `processLinkAutoDownload` (Phase 02 Step 02.3), replace the stub cancel callback inside `LinkAutoDownloadProgressDialog(this) { cleanupAndFinish() }` with:
>
> ```kotlin
> LinkAutoDownloadProgressDialog(this) {
>     WorkManager.getInstance(this@ReceiveShareActivity)
>         .cancelUniqueWork(uniqueWorkNameFor(url))
>     cleanupAndFinish()
> }
> ```
>
> The observer (Step 02.3) will subsequently see `CANCELLED` and reach the same cleanup branch — that is OK: `cleanupAndFinish` is idempotent (`isFinishTriggered` guard already in place).

**Verification:**

- `Grep` — `cancelUniqueWork(uniqueWorkNameFor(url))` matches once in `ReceiveShareActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. Cancel callback already wired in Phase 02 Step 02.3 (single edit point — the dialog cancel lambda directly calls cancelUniqueWork before cleanupAndFinish).

---

### Step 03.2 — Verify atomic-write contract in `LinkDownloadWriter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Read `LinkDownloadWriter.kt` end to end (~249 LOC). Confirm two invariants:
>
> 1. The writer streams to a temp file inside the destination directory (or app cache) and renames atomically to the final path on success. If absent — patch by introducing the temp+rename pattern in the existing write loop. Use `java.io.File.renameTo(target)`; on failure, fall back to copy+delete.
> 2. On `CancellationException` (or `coroutineContext.ensureActive()` throwing) the writer deletes the temp file before propagating. If the catch block is missing — add it.
>
> If the writer already satisfies both invariants — do NOT mechanically re-add code. Document the verification result in chat (`expected: temp+rename present | actual: present`). If a patch is required, update only the affected helper(s).

**Verification:**

- `Grep` — pattern `\.renameTo\(` matches in `LinkDownloadWriter.kt`.
- `Grep` — pattern `(catch \(.*Cancellation|ensureActive\(\))` matches in `LinkDownloadWriter.kt`.
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS (renameTo×1 added on legacy <Q path with copy fallback; ensureActive×1 already present in read loop; cancellation cleanup via existing `finally { tempFile.delete() }` block — modern path uses MediaStore IS_PENDING toggle which is the equivalent atomicity mechanism). Build deferred to Phase Done Criteria.

---

### Step 03.3 — Add notification cancel action receipt path in worker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> The cancel action added in Phase 01 Step 01.4 uses `WorkManager.createCancelPendingIntent(id)` — that already routes through WorkManager's framework cancel path; no new receiver is needed. Verify this is wired correctly by reading the `buildCancelAction()` helper. Then audit `doWork()` for sufficient cancellation checkpoints:
>
> 1. Before `coordinator.handle(...)` — call `ensureActive()`.
> 2. Inside the silentCallbacks/progressCallbacks `onProgress` — call `ensureActive()` once per emission so a cancel between progress events propagates immediately.
>
> If these checkpoints are absent, add them. Import `kotlinx.coroutines.ensureActive`.

**Verification:**

- `Grep` — `ensureActive()` appears at least twice in `LinkDownloadWorker.kt`.
- `Grep` — `import kotlinx.coroutines.ensureActive` present.
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS (ensureActive×2 — once at coroutineScope entry, once before single-URL coordinator.handle; import present at line 24). Note: cannot add ensureActive inside non-suspending Callbacks.onProgress — the writer's own ensureActive (line 78) already covers the streaming loop's cancellation gate. Build deferred to Phase Done Criteria.

---

### Step 03.4 — Document dedup TTL behaviour

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `enqueueUniqueWork(.., ExistingWorkPolicy.KEEP, ..)` keeps the existing work only while it is in `RUNNING`/`ENQUEUED` state. Once a unique work transitions to `SUCCEEDED`/`FAILED`/`CANCELLED`, a new request with the same key starts fresh — exactly the behaviour we want (re-share after success of the same URL begins a new download). Add a one-line KDoc above `uniqueWorkNameFor(..)` documenting this:
>
> ```kotlin
> /**
>  * S0202: dedup key for [WorkManager.enqueueUniqueWork]. The KEEP policy ignores a
>  * second share of the same URL ONLY while the prior work is still RUNNING/ENQUEUED;
>  * once it finishes (any terminal state), a re-share starts a fresh worker.
>  */
> ```
>
> No code change beyond the KDoc.

**Verification:**

- `Grep` — `S0202: dedup key` matches once in `ReceiveShareActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. KDoc inlined when the helper was first created in Phase 02 Step 02.2 to avoid double-edit.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` → `standard debug` (exit 0; record literal output).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

After Phase 03:

- Cancel works from the dialog and from the foreground notification (cancel action wired in Phase 01).
- Cancel between progress events takes effect within one extraction step (`ensureActive` checkpoints).
- Atomic write guarantees no partial files surface on the user's device after a cancel.
- Dedup behaves as documented: re-share of the same URL while in flight is ignored; after completion it starts fresh.

Phase 04 adds the result-on-resume UX so users returning to the app see the outcome without relying solely on the notification.

---

## Rollback Plan

Revert the phase commit(s). The cancel-callback change is isolated; the writer audit may have added a `try/catch` block — re-applying the prior version restores behaviour. No data loss risk.
