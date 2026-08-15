# S1226 - File-transfer progress was published per buffer chunk, throttling the transfer itself

**Status:** Archived
**Priority:** 75

## 0. Raw capture

Owner, 2026-07-27, during a 44 GB SFTP copy on the Quest 3:

> "что со скоростью копирования? почему такая низкая?"
> "и она пересчитывается миллион раз в секунду. можно реже? раз в 5 секеунд например - мы кучу ресурсов тратим на демонстрацию прогресса"

## 1. Problem

`BrowseFileTransferWorker` published a progress update for **every buffer chunk** reported by the copy layer. Measured from `temp/scratch/vr_session_20260727-2224.log`: ~50 updates per second, one per ~128 KB. On the 44.36 GB file under test that is roughly 340 000 publications for a single copy.

Each publication did two expensive things:

- `setForeground(buildForegroundInfo(..))` - rebuilt and re-posted the foreground notification (binder round-trip to NotificationManager).
- `setProgressAsync(..)` - wrote WorkManager's progress row to Room.

Both compete with the copy for the same dispatcher, so the work of *showing* progress was slowing down the work being shown.

## 2. Evidence

Consecutive `WM-WorkProgressUpdater` lines, 29 of them inside 0.6 s:

```
23:06:22.030  browse_transfer_bytes_transferred : 2472871480
23:06:22.625  browse_transfer_bytes_transferred : 2486497560
```

True throughput over that window: 13.6 MB / 0.595 s = **~23 MB/s**. The `browse_transfer_speed` field published alongside ranged from **6 551 000 to 131 020 000** bytes/s in the same second - it is computed over a single chunk interval (~20 ms), so it is noise rather than a measurement.

## 3. Change

`app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`:

- Publication is rate-limited by `PROGRESS_MIN_INTERVAL_MS` (1 s), measured with `SystemClock.elapsedRealtime()`.
- A change of `currentFile` publishes immediately regardless of the timer, so the notification never names a file that is no longer being copied.
- The flow itself is untouched - `FileOperationProgressDialog` and any other in-process consumer still receive every event.

1 s rather than the 5 s the owner suggested: going from ~50/s to 1/s removes ~98% of the cost, and the remaining difference between 1 s and 5 s is within noise, while a 5 s bar on a 44 GB copy reads as frozen. The value is a single named constant with that rationale in its comment.

## 4. Not fixed here

The jittery speed number itself. Smoothing it means owning the measurement window, which belongs to the unified component in **S1225** - fixing it locally would add a fourth private implementation of the thing S1225 exists to remove.

## 5. Verification

- Build: `.\a.ps1 nd` - BUILD SUCCESSFUL, APK `v2.60.7272.309-NoLegal-DEBUG`, installed on Quest 3 `2G0YC5ZG5608DL`.
- Device check still owed: start a large network copy, confirm the notification updates about once per second and that measured throughput (bytes delta over wall time, not the displayed number) is at least as high as before.

## Last Audit

2026-07-28, static (`/spec-next` round 4). The change is present and matches section 3.

- `PROGRESS_MIN_INTERVAL_MS = 1_000L` with its rationale comment, `BrowseFileTransferWorker.kt:504`.
- Throttle state declared per transfer, not per class, so two concurrent transfers cannot starve each other's updates: `lastPublishAtMs` / `lastPublishedFile` are locals of `runTransfer` (98-100).
- The gate itself (117-120) publishes when the file changed **or** the interval elapsed, exactly as specified. `lastPublishAtMs` starts at `0L`, so the first event of a transfer always publishes rather than waiting out an interval.

Section 3's claim that in-process consumers are unaffected was checked rather than taken on trust. `FileOperationProgressDialog.updateProgress` takes the domain `FileOperationProgress` directly, not a `WorkInfo`, so it is fed by an in-process collector; the throttle lives inside the worker's own `collect` and the `return@collect` skips only the worker's publication. The dialog still sees every event.

One edge worth naming because throttling invites it: the last `Processing` event before completion can be dropped, leaving the foreground notification a fraction short. It is immediately superseded - the `Completed` branch only records `terminalResult`, then `persistAndPublish` and `postResultNotification` run after the flow and replace the notification, and the worker's `Result` carries the terminal payload for `WorkInfo` consumers. No stale percentage survives.

**Device check still owed**, unchanged from section 5. A probe tag was added at the publication point for it - `Timber.d("S1226: publish gapMs=.. fileChanged=..")` - which turns the claim into something readable off logcat: successive `gapMs` values should sit around 1000 rather than ~20. A local copy on any device exercises this; the 44 GB SFTP throughput half of section 5 still needs the real network and headset.

## 6. Related

- **S1225** - unify progress/speed computation and presentation; this ticket had to add a private throttle because the shared `ProgressTracker` does not govern the publish path.
- **S1227** - once a transfer is detached from its dialog, there is no in-app place to watch this progress at all.
