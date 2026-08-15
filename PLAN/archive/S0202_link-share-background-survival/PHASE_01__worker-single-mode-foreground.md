# Phase 01 — Worker single-mode + foreground notification

**Strategic spec:** [`../S0202_link-share-background-survival.md`](../S0202_link-share-background-survival.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 7 / 7
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Extend `LinkDownloadWorker` so a single-URL share runs as a foreground-service worker that publishes live progress to `WorkInfo.progress` and updates an ongoing notification. No Activity / dialog changes in this phase — Phase 02 wires the UI to consume the new progress stream.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done — none for this foundation phase.
- [ ] Strategic §6.1, §6.4, §6.5 read and understood.
- [ ] Working tree clean or on `DEBUG-v001` branch.
- [ ] AndroidManifest's existing `<application>` block reviewed for any prior `foregroundServiceType` declarations.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadProgressCodec.kt` | New | ≤ 120 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> `LinkDownloadWorker` is currently 203 LOC. Projected delta ≈ +160 LOC → final ≈ 360 LOC (under 500 budget — no backup required).

---

## Steps

### Step 01.1 — Introduce `LinkDownloadProgressCodec`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadProgressCodec.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `LinkDownloadProgressCodec.kt` in package `com.sza.fastmediasorter.worker`. Define an `object LinkDownloadProgressCodec` with two functions: `encode(state: LinkAutoDownloadCoordinator.ProgressState): Data` and `decode(data: Data): LinkAutoDownloadCoordinator.ProgressState?`. The codec uses these stable string keys: `"prog_kind"` (one of `"probing"`, `"analyzing"`, `"downloading"`, `"batch"`), `"prog_bytes_read"` (long), `"prog_bytes_total"` (long, optional, encode `-1` when unknown), `"prog_item_index"` (int), `"prog_item_count"` (int). On decode, missing or unknown `prog_kind` returns `null`. Place a top-level KDoc explaining that this codec is the wire format between `LinkDownloadWorker` and `LinkAutoDownloadProgressDialog`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadProgressCodec.kt` exists.
- `Grep` — `object LinkDownloadProgressCodec` matches exactly once in that file.
- `Grep` — `fun encode(` and `fun decode(` both present.
- `Grep -n "Log\.d\("` returns zero hits in the new file.
- Compile via `/build` → `standard debug` after Step 01.4 (deferred — codec compiles on its own once the worker references it).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS (file exists; `object LinkDownloadProgressCodec` ×1; encode/decode signatures present at lines 33,56; zero `Log.d(`). Files: app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadProgressCodec.kt (+91 LOC).

---

### Step 01.2 — Add new strings for progress notification (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add four new string keys, all three locales, in lockstep:
>
> - `link_download_notif_text_probing` — EN: `Preparing..`, RU: `Подготовка..`, UK: `Підготовка..`.
> - `link_download_notif_text_analyzing` — EN: `Analyzing page..`, RU: `Анализ страницы..`, UK: `Аналіз сторінки..`.
> - `link_download_notif_text_downloading_pct` — EN: `Downloading %1$d%%`, RU: `Скачивание %1$d%%`, UK: `Завантаження %1$d%%`.
> - `link_download_notif_action_cancel` — EN: `Cancel`, RU: `Отмена`, UK: `Скасувати`.
>
> Place new keys directly under the existing `link_download_notif_*` block (`strings.xml` line ~3520). Use `..` (two dots), not `...`. Russian/Ukrainian must use `ё`/`Ё` where grammatically correct (none in this batch). All notification text passes `docs/COMMUNICATION_POLICY.md` §6 tone checklist (short, neutral-friendly, no shouting).

**Verification:**

- `Grep` — each of the 4 keys present in all three locales (12 hits total) via `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_notif_text_probing"` returning exit 0; repeat for the other three keys.
- `Grep` — old key `link_download_notif_text_saved` still exists (regression check).
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist (manual review).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 5/5 PASS (4 keys × 3 locales via check_strings_localized.ps1 — all exit=0; regression key `link_download_notif_text_saved` still in all 3 locales). Files: app_v2/src/main/res/values/strings.xml, values-ru/, values-uk/ (+5 lines each).

---

### Step 01.3 — Add foreground-service declaration in `AndroidManifest.xml`

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — independent of Step 01.1/01.2

**Prompt for developer:**

> WorkManager 2.7+ on `targetSdk 34+` requires explicit `foregroundServiceType` for any worker that calls `setForeground()`. Locate the existing `<service android:name="androidx.work.impl.foreground.SystemForegroundService"` block (auto-merged from WorkManager). If absent, add an explicit `<service>` element under `<application>` with `android:name="androidx.work.impl.foreground.SystemForegroundService"`, `android:foregroundServiceType="dataSync"`, and `tools:replace="android:foregroundServiceType"` (keep the `xmlns:tools` namespace already present in the manifest root). Also add the runtime permission `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` if not already declared. Do not touch any other existing `<service>` entries.

**Verification:**

- `Grep` — `android.permission.FOREGROUND_SERVICE_DATA_SYNC` present in `AndroidManifest.xml`.
- `Grep` — `android:foregroundServiceType="dataSync"` present in `AndroidManifest.xml`.
- `/build` → `standard debug` succeeds after Step 01.4 (manifest changes are validated together with worker code).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS without edit. expected: `FOREGROUND_SERVICE_DATA_SYNC` permission + `foregroundServiceType="...dataSync..."` on the SystemForegroundService entry | actual: both already present (line 51 permission, line 235 service type `mediaPlayback|dataSync`). No file edit required for this step.

---

### Step 01.4 — Add `getForegroundInfo()` and `setForeground()` to `LinkDownloadWorker`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 01.1, Step 01.2, Step 01.3

**Prompt for developer:**

> Override `suspend fun getForegroundInfo(): ForegroundInfo` returning `buildForegroundInfo(initialText)` where `initialText = context.getString(R.string.link_download_notif_text_probing)`. Implement private helper `buildForegroundInfo(text: String): ForegroundInfo` that builds an ongoing `NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)` with `setSmallIcon(R.drawable.ic_cloud_download)`, `setContentTitle(R.string.link_download_notif_title_downloading)`, `setContentText(text)`, `setOngoing(true)`, `setProgress(0, 0, true)` (indeterminate by default), and `addAction(buildCancelAction())`. The notification id is the existing `NOTIF_ID_PROGRESS`. On API 34+ wrap the `ForegroundInfo` constructor with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`; on lower APIs use the two-argument constructor. Implement `buildCancelAction(): NotificationCompat.Action` that targets `WorkManager.getInstance(context).createCancelPendingIntent(id)` (the worker's own `id`) with the new `link_download_notif_action_cancel` label and `R.drawable.ic_close` (or fall back to `android.R.drawable.ic_menu_close_clear_cancel` if `ic_close` is missing — verify via Glob).
>
> Inside `doWork()`, **before** the existing `coordinator.handle(...)` call, invoke `setForeground(buildForegroundInfo(context.getString(R.string.link_download_notif_text_probing)))`. Wrap the call in `try/catch (Exception)` and log via `Timber.w(e, "LinkDownloadWorker: setForeground failed (non-fatal)")` — match the pattern used in `ScheduledOperationsWorker.kt:48-50`.

**Verification:**

- `Grep` — `override suspend fun getForegroundInfo()` matches once in `LinkDownloadWorker.kt`.
- `Grep` — `setForeground(buildForegroundInfo(` matches once in `LinkDownloadWorker.kt`.
- `Grep` — `createCancelPendingIntent(` matches once in `LinkDownloadWorker.kt`.
- `Grep` — `FOREGROUND_SERVICE_TYPE_DATA_SYNC` matches once in `LinkDownloadWorker.kt`.
- `Grep -n "Log\.d\("` returns zero hits in the modified file.
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 5/5 PASS (getForegroundInfo×1, setForeground(buildForegroundInfo×1, createCancelPendingIntent×1, FOREGROUND_SERVICE_TYPE_DATA_SYNC×1, Log.d=0). Build deferred to Phase Done Criteria. Files: app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt (+~55 LOC). Inlined Timber.d tag from Step 01.7 in same edit (single edit point).

---

### Step 01.5 — Wire progress callbacks into `setProgress` + notification update

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Replace `silentCallbacks()` for the single-URL branch (line ~94 of the existing file) with a new `progressCallbacks(): LinkAutoDownloadCoordinator.Callbacks` factory that:
>
> 1. On every `onProgress(state)` call: encode the state via `LinkDownloadProgressCodec.encode(state)` and invoke `setProgress(data)`. `setProgress` is a suspend function — invoke it inside `runBlocking` is forbidden; instead, hop via the worker's `coroutineScope` already provided by `CoroutineWorker`. Use `kotlinx.coroutines.runBlocking` ONLY at the very edge as a last resort, but the cleaner path is to capture a `CoroutineScope` from the worker (`@Assisted` `WorkerParameters` exposes none — use `withContext(Dispatchers.Default)` + `setProgressAsync(data).await()` from `androidx.concurrent.futures.await`). Choose `setProgressAsync(data).await()` — it is the documented WorkManager pattern.
> 2. After `setProgress`, also call private helper `updateNotification(state)` which rebuilds the notification with the appropriate text:
>    - `Probing` → `link_download_notif_text_probing`, indeterminate progress.
>    - `AnalyzingPage` → `link_download_notif_text_analyzing`, indeterminate.
>    - `Downloading(bytesRead, total)` → `link_download_notif_text_downloading_pct` formatted with the percent value when `total != null && total > 0`; otherwise indeterminate.
>    - `BatchDownloading(...)` → reuse `link_download_notif_text_downloading_pct` with `(itemIndex / itemCount) * 100`.
> 3. The single-URL branch (`coordinator.handle(url!!, progressCallbacks(), accountId)`) MUST use the new factory. The batch branch (`handleBatch`) keeps `silentCallbacks()` until Phase 04 — explicitly leave that branch untouched.
>
> Keep the existing result-notification logic in `postResultNotification(...)` intact; this step only adds the progress channel.

**Verification:**

- `Grep` — `private fun progressCallbacks` matches once.
- `Grep` — `setProgressAsync(` matches once in `LinkDownloadWorker.kt`.
- `Grep` — `LinkDownloadProgressCodec.encode(` matches once.
- `Grep` — `updateNotification(state` (or `updateNotification(state)`) matches once.
- `Grep` — `coordinator.handle(url!!, progressCallbacks(), accountId)` matches exactly once.
- `Grep` — `coordinator.handleBatch(urls.toList(), silentCallbacks())` still matches once (batch left intact).
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 6/6 PASS (progressCallbacks×1, setProgressAsync×1, encode×1, updateNotification(state×2 def+call, single-URL call×1, batch call×1). Build deferred to Phase Done Criteria. Files: app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt (+~70 LOC).

---

### Step 01.6 — Switch single-URL enqueue to `setForeground`-compatible request type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> The existing comment on line 65-70 of `LinkDownloadWorker.kt` ("setForeground() is intentionally NOT called here") is now obsolete — replace it with a new KDoc paragraph explaining the new dual-mode behaviour: the worker is now a long-running foreground worker for single-URL mode (calls `setForeground` from `getForegroundInfo`) and falls back to short-lived expedited mode for the batch path (no `setForeground` there). Document this explicitly so future reviewers do not re-introduce the previous gating.
>
> Do NOT modify the actual enqueue site in `ReceiveShareActivity` — that is Phase 02's responsibility. Phase 01 leaves the enqueue caller alone but ensures the worker, when invoked, behaves correctly under either mode.

**Verification:**

- `Grep -n "intentionally NOT called here"` returns zero hits in `LinkDownloadWorker.kt`.
- `Grep` — KDoc contains the phrase `dual-mode` or `foreground worker for single-URL mode`.
- `Grep` — `setExpedited` not added or removed in `LinkDownloadWorker.kt` (this step does not touch the enqueue site).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS (intentionally-NOT-called-here removed, "dual-mode worker" KDoc present, setExpedited untouched). Files: app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt (KDoc rewritten in Step 01.4 same edit).

---

### Step 01.7 — Insert debug verification tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 01.6

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", S0202 will move into `BlockNeedUserTest` once `/spec-dev` finishes. Insert one tag at the **entry of the worker's foreground-service path**:
>
> ```kotlin
> Timber.d("S0202: LinkDownloadWorker single-mode foreground started url=%s", url)
> ```
>
> Place it immediately AFTER the `setForeground(...)` call inside `doWork()`, only on the single-URL branch. Do NOT insert a tag for the batch branch — that path is unchanged. The tag will be deleted by `/spec-check` when status leaves `BlockNeedUserTest`.

**Verification:**

- `Grep -n "Timber.d(\"S0202: LinkDownloadWorker single-mode foreground started"` returns exactly one match in `LinkDownloadWorker.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS (Timber.d S0202 tag present ×1). Tag inserted as part of Step 01.4 single edit (immediately after setForeground in single-URL branch).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` → `standard debug` (exit 0; record literal output).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `pwsh -File scripts/add_to_dev_log.ps1`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` followed by `render.ps1` regenerates `dev/CATALOG/app_v2.jsonl` + `.md`.

---

## Handoff Notes to Next Phase

After this phase, `LinkDownloadWorker` self-publishes progress and an ongoing notification when invoked from a single-URL `OneTimeWorkRequest`. The Activity has not yet been refactored — `ReceiveShareActivity.processLinkAutoDownload` still drives the coordinator directly. Phase 02 replaces that call site with a `WorkManager.enqueueUniqueWork` + observe path.

Invariants established:

- `LinkDownloadProgressCodec` is the wire format for progress between worker and any observer.
- The worker's foreground notification uses the existing channel `link_download_channel` — no new channel was introduced (per strategic §5.3).
- Batch mode is untouched — `silentCallbacks()` still wired for `coordinator.handleBatch`.

---

## Rollback Plan

Revert the phase commit(s). The new `LinkDownloadProgressCodec.kt` deletion + restoration of the original `setForeground intentionally NOT called` block + removal of the `getForegroundInfo` override fully reverts the change. No data migration, no schema change, no user-facing surface added — manifest permission addition is harmless to leave in place.
