# S1292 - Foreground-service notification id collisions: 0x4054 (overlay vs screen recording) and 4201 (sync worker vs S0710 permission advisory vs duplicate worker)

**Ticket:** S1292
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): services-2, services-4.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: S0710 (the permission advisory being clobbered was introduced there).

## Finding 1: OverlayHostService and ScreenVideoRecordingService share foreground NOTIFICATION_ID 0x4054 - recording controls vanish or an FGS is left with no notification

- Severity: P1, effort: trivial.
- File: `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt:497`
- Symptom: Two foreground services in the same source set post their startForeground() notification under the same id 0x4054 with no tag: OverlayHostService.kt:186 and ScreenVideoRecordingService.kt:497. Notifications are keyed (package, tag, id), so whichever service posts last replaces the other's notification, and either service's stopForeground(STOP_FOREGROUND_REMOVE) cancels the one shared notification while the other service keeps running in the foreground without any notification.
- Failure scenario: User has the edge-gesture strip enabled (OverlayHostService FGS) and starts a screen+mic recording. Any app re-foreground re-runs OverlayHostService.start() (ScreenGestureOverlayStartupCoordinator.restoreIfNeeded on ProcessLifecycleOwner.onStart) -> onStartCommand -> startForegroundCompat() re-posts id 0x4054, replacing the recording notification: the Pause/Stop actions disappear while the mic+screen recording silently continues, potentially for hours. Conversely, when the recording finishes, its stopForeground(REMOVE) cancels 0x4054 and the still-running overlay FGS is left with no notification at all.
- Fix sketch: Give ScreenVideoRecordingService its own unique notification id (e.g. 0x4055) and keep pause/resume updates (NotificationManagerCompat.notify at lines 238/251) on that same id. Optionally add a comment-level registry of FGS notification ids to prevent future collisions.
- Verifier rationale: Confirmed. OverlayHostService.kt:186 and ScreenVideoRecordingService.kt:497 both declare NOTIFICATION_ID=0x4054; both call startForeground with it (OverlayHostService:151-157, ScreenVideoRecordingService:404-406) with no tag, and the recording service also re-posts on pause/resume via NotificationManagerCompat.notify (lines 238/251). OverlayHostService.stopForegroundCompat() uses STOP_FOREGROUND_REMOVE (line 163), and OverlayHostService.start() is re-invoked on every process foreground (ScreenGestureOverlayStartupCoordinator via ProcessLifecycleOwner.onStart, per the comment at lines 194-196), so re-posting over the recording notification is a routine path, not an edge case. Notifications are keyed (package, tag, id): last poster replaces the other's notification, and either stop cancels the shared one, leaving the other still-running FGS without any notification. An active mic+screen recording losing its Pause/Stop controls (or the overlay FGS running notification-less) is a genuine user-facing defect; fix is a one-character id change.

Evidence excerpt:

```
OverlayHostService.kt:186:  private const val NOTIFICATION_ID = 0x4054
ScreenVideoRecordingService.kt:497:  private const val NOTIFICATION_ID = 0x4054
```

## Finding 2: Notification id 4201 collision: NetworkFilesSyncWorker's foreground notification silently cancels the ScheduledOperations permission advisory

- Severity: P2, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt:190`
- Symptom: NetworkFilesSyncWorker uses NOTIFICATION_ID = 4201 for its ForegroundInfo, the same id ScheduledOperationsWorker uses for its one-shot 'grant All-Files access' advisory (PERMISSION_NOTIFICATION_ID = 4201, ScheduledOperationsWorker.kt:40). Neither uses a tag. When the periodic sync worker runs, its ongoing foreground notification replaces the advisory, and when the worker finishes WorkManager removes id 4201 - deleting the advisory the user never saw.
- Failure scenario: A scheduled file operation halts on missing All-Files access and posts the S0710 advisory (id 4201). Within the next sync interval the periodic resource-sync worker runs, takes over id 4201 as its foreground notification and removes it on completion. The advisory is gone, the user never learns why, and the failing scheduled operation keeps re-running its failing batch every interval indefinitely - exactly the silent-retry loop S0710 was written to prevent.
- Fix sketch: Change one of the two ids (e.g. make the permission advisory 4210) or post the advisory with a distinct tag so the foreground notification cannot clobber it.
- Verifier rationale: Confirmed. NetworkFilesSyncWorker.kt:190 declares NOTIFICATION_ID=4201 and actively uses it: doWork() calls setForeground(createForegroundInfo()) at line 46 on every periodic run, so WorkManager posts id 4201 as an ongoing foreground notification and removes it when the worker finishes. ScheduledOperationsWorker posts its S0710 permission advisory with nm.notify(PERMISSION_NOTIFICATION_ID=4201) at line 129 - the very comment on line 40 says 'separate id so the permission advisory survives', but it is only separate from that worker's own 4200, not from the sync worker. No tags anywhere, so the advisory is replaced and then deleted, recreating the silent-retry blindness S0710 was written to fix. Additional collision: DuplicateDetectionWorker.kt:42 also uses 4201 as its ForegroundInfo id. Real but a notification-visibility defect, no resource/data impact: P2, one-line id change.

Evidence excerpt:

```
NetworkFilesSyncWorker.kt:190:  private const val NOTIFICATION_ID = 4201
ScheduledOperationsWorker.kt:40:  private const val PERMISSION_NOTIFICATION_ID = 4201  // S0710: separate id so the permission advisory survives
```

