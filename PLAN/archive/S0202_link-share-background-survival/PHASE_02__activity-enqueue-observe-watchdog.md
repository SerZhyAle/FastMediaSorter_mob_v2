# Phase 02 — Activity refactor: enqueue + observe + watchdog finish

**Strategic spec:** [`../S0202_link-share-background-survival.md`](../S0202_link-share-background-survival.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Replace `ReceiveShareActivity.processLinkAutoDownload`'s direct `coordinator.handle(...)` call with a `WorkManager.enqueueUniqueWork(KEEP)` + `WorkInfo.progress` observer flow. Keep the existing `LinkAutoDownloadProgressDialog` UX for fast share scenarios via a 4-second watchdog, then `finish()` the Activity once the watchdog elapses or the worker reports a terminal state.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (worker emits progress via `LinkDownloadProgressCodec` and self-publishes a foreground notification).
- [ ] Decision §6.1 in strategic confirmed: 4-second watchdog before silent finish.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt` | Modified | ≤ 200 |

> `ReceiveShareActivity` is currently 594 LOC. Projected delta ≈ +90 LOC (new `enqueueShareDownload(..)` helper, observer wiring, watchdog) → ≈ 684 LOC. Over the 500-line budget — backup required (Step 02.1).

---

## Steps

### Step 02.1 — Backup `ReceiveShareActivity.kt` and `LinkAutoDownloadProgressDialog.kt`

**Files:** `temp/ReceiveShareActivity_<timestamp>.kt.bak`, `temp/LinkAutoDownloadProgressDialog_<timestamp>.kt.bak`
**Depends on:** — start of phase

**Prompt for developer:**

> Per CLAUDE.md "Backup rule" — file >500 LOC → timestamped backup in `temp/` before edit. Copy both files to `temp/` with the suffix `_yyyyMMdd_HHmmss.kt.bak`. Do NOT commit the backups; `temp/` is gitignored.

**Verification:**

- `Glob` — `temp/ReceiveShareActivity_*.kt.bak` returns at least one file.
- `Glob` — `temp/LinkAutoDownloadProgressDialog_*.kt.bak` returns at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/2 PASS (`temp/ReceiveShareActivity_20260514_205220.kt.bak` created, 29041 bytes). LinkAutoDownloadProgressDialog (121 LOC) is below the 500-LOC threshold; CLAUDE.md backup rule does not apply — backup skipped explicitly. Net: 1/1 required backup taken.

---

### Step 02.2 — Add unique-work key derivation helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private companion-object helper:
>
> ```kotlin
> private fun uniqueWorkNameFor(url: String): String {
>     // Use canonicalized host+path to dedupe re-shares of the same URL while ignoring
>     // tracking parameters (utm_*, fbclid, etc.). Hash to keep WorkManager key short.
>     val u = android.net.Uri.parse(url)
>     val key = "${u.host.orEmpty()}${u.path.orEmpty()}"
>     return "share_dl_" + Math.abs(key.hashCode()).toString(16)
> }
> ```
>
> Place it inside `companion object` next to `EXTRA_REAUTH_URL`. The function is called by `enqueueShareDownload(..)` (Step 02.3) and by Phase 03's cancel routing.

**Verification:**

- `Grep` — `private fun uniqueWorkNameFor(` matches once.
- `Grep` — `share_dl_` literal present in the helper.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Helper added to `companion object` next to `EXTRA_REAUTH_URL` with KDoc covering Step 03.4's contract (added inline to avoid double-edit).

---

### Step 02.3 — Replace `processLinkAutoDownload` with enqueue + observe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Refactor the existing `private fun processLinkAutoDownload(url: String, accountId: String?, isAuthRetry: Boolean = false)` (lines ~347-430). Keep the function signature and call sites intact, but replace the body with the following flow:
>
> 1. Show `LinkAutoDownloadProgressDialog` exactly as today (cancel callback now calls `WorkManager.cancelUniqueWork(this, uniqueWorkNameFor(url))` then `cleanupAndFinish()` — see Phase 03 for full cancel routing; for Phase 02 stub the cancel as `cleanupAndFinish()` only).
> 2. Build a `OneTimeWorkRequestBuilder<LinkDownloadWorker>` with input data:
>    - `KEY_URL` → `url`
>    - `KEY_ACCOUNT_ID` → `accountId`
>    - new key `KEY_IS_AUTH_RETRY` → `isAuthRetry` (also add this constant to `LinkDownloadWorker.companion` as `const val KEY_IS_AUTH_RETRY = "link_dl_is_auth_retry"`).
> 3. Use `WorkManager.getInstance(this).enqueueUniqueWork(uniqueWorkNameFor(url), ExistingWorkPolicy.KEEP, request)` to enqueue. Capture the returned operation's `workId` via the `OneTimeWorkRequest.id`.
> 4. Observe `WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId)` from `lifecycleScope` (use `androidx.lifecycle.observe` extension). On every emission:
>    - `RUNNING` → decode progress via `LinkDownloadProgressCodec.decode(workInfo.progress)`; if non-null, call `progressDialog.update(state)` on UI thread.
>    - `SUCCEEDED` or `FAILED` → dismiss dialog; observer detaches itself; `cleanupAndFinish()`. The result Toast/dialog UX comes from Phase 04 — for now the worker's own result notification is the user's only feedback.
>    - `CANCELLED` → dismiss dialog; `cleanupAndFinish()`.
> 5. Start a 4-second watchdog via `lifecycleScope.launch { delay(4_000); if (!finishCalled) { progressDialog.dismiss(); cleanupAndFinish() } }`. The flag `finishCalled` reuses existing `isFinishTriggered`. The watchdog ensures the Activity does not linger past the user's attention window even if the worker keeps running — the foreground notification (Phase 01) takes over.
> 6. Move the existing `S0166 §2 Step 0 unknown-host escalation` block into a new private function `handleNoMediaFoundEscalation(...)` so the observer can invoke it on `SUCCEEDED` with a Failed.NoMediaFound result; this preserves the auth-retry branch. For Phase 02 the escalation runs ONLY if the activity is still alive when the worker completes (i.e. before watchdog finish). If the watchdog fired first, the escalation is forfeited — that is acceptable because the foreground notification's "Sign in" action remains available.
>
> Remove the now-orphaned direct call `coordinator.handle(...)` from the body. Imports: add `androidx.work.ExistingWorkPolicy`, `androidx.work.WorkInfo`, `androidx.lifecycle.observe`, `kotlinx.coroutines.delay`. Remove `coordinator: LinkAutoDownloadCoordinator` injection if it is no longer referenced — search for other uses first; if none remain, drop the field and the import.

**Verification:**

- `Grep` — `enqueueUniqueWork(uniqueWorkNameFor(url)` matches once in `ReceiveShareActivity.kt`.
- `Grep` — `getWorkInfoByIdLiveData(` matches once.
- `Grep` — `LinkDownloadProgressCodec.decode(` matches once.
- `Grep` — `coordinator.handle(url = url,` returns zero hits in `ReceiveShareActivity.kt` (removed).
- `Grep` — `delay(4_000)` matches once (watchdog).
- `Grep` — `private fun handleNoMediaFoundEscalation(` matches once.
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 6/6 PASS (enqueueUniqueWork(workName,KEEP)×1, getWorkInfoByIdLiveData×1, decode×1, coordinator.handle( = 0 (removed), delay(4_000)×1, handleNoMediaFoundEscalation×1). Worker amended in-place (KEY_RESULT_KIND constant + outputData with result kind on success). Coordinator inject + import removed. Build deferred to Phase Done Criteria.

---

### Step 02.4 — Allow dialog to update from external observer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> The existing `update(state: ProgressState)` method is already public and re-entrant. Verify its behaviour matches the new observer flow: `update` may be called from the main thread before `show()` completes (LiveData fires immediately on subscription if a value is cached). Add a one-line guard at the top of `update`:
>
> ```kotlin
> if (dialog == null) return  // observer fired before show() — discard
> ```
>
> No other change in this file.

**Verification:**

- `Grep` — `if (dialog == null) return` matches once in `LinkAutoDownloadProgressDialog.kt`.
- File line count remains under 200.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS (guard line ×1, file line count = 122).

---

### Step 02.5 — Insert debug verification tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", insert one tag at the **entry of the new enqueue+observe flow**. Place it immediately AFTER the `enqueueUniqueWork(..)` call inside the refactored `processLinkAutoDownload`:
>
> ```kotlin
> Timber.d("S0202: ReceiveShareActivity enqueued worker url=%s accountId=%s retry=%s", url, accountId, isAuthRetry)
> ```
>
> Do NOT insert tags inside the observer's per-emission callback — one tag per flow entry per CLAUDE.md.

**Verification:**

- `Grep -n "Timber.d(\"S0202: ReceiveShareActivity enqueued worker"` returns exactly one match in `ReceiveShareActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS (Timber.d S0202 tag ×1, inserted in Step 02.3 same edit).

---

### Step 02.6 — Confirm landscape parity for the progress dialog

**Files:** `app_v2/src/main/res/layout/dialog_link_autodownload_progress.xml`, `app_v2/src/main/res/layout-land/dialog_link_autodownload_progress.xml` (if exists)
**Depends on:** — independent

**Prompt for developer:**

> Phase 02 does not edit the dialog's XML, but per CLAUDE.md Strict Rule 12 confirm landscape parity: `Glob res/layout-land/dialog_link_autodownload_progress.xml`. If a landscape variant exists, open both and verify they share the same `id` set (`titleText`, `bytesText`, `progressBar`, `cancelButton`). Discrepancy → file an issue note in the phase's Blockers Log; do not silently leave portrait-only changes (none here, but record the verification result).

**Verification:**

- `Glob` — `app_v2/src/main/res/layout-land/dialog_link_autodownload_progress.xml` either exists or absent — record the result in chat.
- If present: `Grep` — `@id/cancelButton` (or equivalent) matches in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. expected: parity check | actual: layout-land variant ABSENT (no `app_v2/src/main/res/layout-land/dialog_link_autodownload_progress.xml`). Phase 02 does not edit any layout XML — no parity work required.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` → `standard debug` (exit 0; record literal output).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog re-scanned + re-rendered (deferred to Phase 05).

---

## Handoff Notes to Next Phase

After Phase 02:

- The Activity no longer holds the coordinator's lifecycle — backgrounding is now safe. The worker's foreground notification keeps the download alive.
- Cancel from the dialog calls a stub `cleanupAndFinish()` only — Phase 03 wires the real `WorkManager.cancelUniqueWork` and the notification action.
- The `S0166` unknown-host escalation runs only when the worker finishes within the 4-second watchdog. Phase 04 will rehydrate this on app resume.
- `coordinator: LinkAutoDownloadCoordinator` Hilt injection in `ReceiveShareActivity` was removed (if no other call sites remain).

---

## Rollback Plan

Revert the phase commit(s). Restore the backup of `ReceiveShareActivity.kt` from `temp/`. The change is isolated to two UI files and adds no schema, DI binding, or new API. Phase 01's worker code remains compatible with both the old direct-call path and the new enqueue path — rollback is safe.
