---
name: notification-id-registry
description: All app notification ids live in core/notification/NotificationIds.kt with a reflection uniqueness test - never declare a raw id in a service/worker again (S1292)
metadata:
  type: project
---

Every notification id the app posts is declared in `app_v2/src/main/java/com/sza/fastmediasorter/core/notification/NotificationIds.kt`; each service/worker keeps a local `NOTIFICATION_ID` alias pointing at it. `app_v2/src/test/.../NotificationIdsTest.kt` reflects over the object and fails the build on a duplicate value (plus guards the `SAVE_FALLBACK_BASE = 0x5A220000` block, from which SaveFallbackNotifier derives per-file ids).

**Why:** S1292 (2026-07-30) found two live collisions. `0x4054` was shared by `OverlayHostService` and `ScreenVideoRecordingService` - the overlay re-posts on every process foreground, so it replaced the recording notification and the user lost Pause/Stop while a mic+screen recording kept running. `4201` was shared by `NetworkFilesSyncWorker`, `DuplicateDetectionWorker` and the S0710 "grant All-Files access" advisory, so WorkManager deleted the advisory on worker completion. Android keys notifications by (package, tag, id) and none of these use a tag, so last poster wins and either one's teardown cancels it for both.

**How to apply:** adding a notification anywhere -> add a named const to `NotificationIds` and alias it locally; never write a literal id. If the uniqueness test fails, pick a new value rather than renaming - shipped ids are what the OS already has on screen.
