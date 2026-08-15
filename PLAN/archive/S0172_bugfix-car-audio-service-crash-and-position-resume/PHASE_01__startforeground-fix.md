# PHASE 01 — Immediate `startForeground` on Service Start

**Ticket:** S0172  
**Phase:** 01 of 04  
**Pillar:** A — Immediate `startForeground` on cold start

---

## Goal

Prevent the `RemoteServiceException: startForegroundService did not then call startForeground` crash that kills `AudioPlaybackService` within 5 seconds of cold start via `MediaButtonRestartReceiver`.

---

## Context

`AudioPlaybackService` is a `MediaSessionService`. It currently calls `startForeground` implicitly through Media3's `MediaNotificationManager` — only after a `MediaSession` and an active `Player` with a media item are fully initialised. When launched cold via `MediaButtonRestartReceiver` (no media item, no session yet), the 5-second OS deadline fires before any notification is posted.

Fix: call `startForeground` with a minimal placeholder notification at the very top of `onCreate()`, before any Media3 initialisation.

---

## Steps

### Step 1.1 — Add placeholder notification channel (if not already present)

- [ ] Open `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`.
- [ ] Verify `MediaNotificationManager.createNotificationChannel(this)` is called inside `onCreate()`.
  - If yes — it already exists; this step is a no-op.
  - If the channel is created elsewhere, move the call to the very first line of `onCreate()`.
- [ ] **Verification:** `grep -n "createNotificationChannel" AudioPlaybackService.kt` shows the call is present before any other `onCreate` body.

### Step 1.2 — Build a placeholder `Notification` object

- [ ] In `AudioPlaybackService.onCreate()`, immediately after `createNotificationChannel(this)`, add:

```kotlin
// S0172: call startForeground immediately so the OS foreground-service timeout
// never fires on cold start (e.g. car media-button restart with no track loaded).
// Media3 will replace this placeholder with the real media notification once a
// MediaSession and track are established.
val placeholderNotification = androidx.core.app.NotificationCompat
    .Builder(this, MediaNotificationManager.CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_audio_file)           // app audio icon — already exists
    .setContentTitle(getString(R.string.app_name))
    .setContentText("")
    .setSilent(true)
    .build()
```

- [ ] Import `androidx.core.app.NotificationCompat` and `com.sza.fastmediasorter.R` if not already imported.
- [ ] **Verification:** file compiles without errors; `NotificationCompat` import is present.

### Step 1.3 — Call `startForeground` before Media3 init

- [ ] Immediately after the `placeholderNotification` val, add:

```kotlin
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
    startForeground(
        MediaNotificationManager.NOTIFICATION_ID,
        placeholderNotification,
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    )
} else {
    startForeground(MediaNotificationManager.NOTIFICATION_ID, placeholderNotification)
}
Timber.w("AudioPlaybackService: startForeground called (placeholder) — S0172")
```

- [ ] Verify `MediaNotificationManager.NOTIFICATION_ID` and `MediaNotificationManager.CHANNEL_ID` are accessible (public constants). If not — expose them or inline the same integer value used by the notification manager.
- [ ] **Verification:**
  - `grep -n "startForeground" AudioPlaybackService.kt` shows the call before `mediaSession = MediaSession.Builder(...)`.
  - `grep -n "FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK" AudioPlaybackService.kt` shows the API 29+ branch.

### Step 1.4 — Add `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` to manifest (if absent)

- [ ] Open `app_v2/src/main/AndroidManifest.xml`.
- [ ] Find `<service android:name=".ui.player.AudioPlaybackService"`.
- [ ] Verify `android:foregroundServiceType="mediaPlayback"` is present in the `<service>` element.
  - If absent — add it.
- [ ] **Verification:** `grep -n "foregroundServiceType" AndroidManifest.xml` returns the `mediaPlayback` entry.

### Step 1.5 — Add `FOREGROUND_SERVICE` permission (if absent)

- [ ] In `AndroidManifest.xml`, verify `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` is declared.
- [ ] For API 34+: verify `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />` is declared.
- [ ] **Verification:** `grep -n "FOREGROUND_SERVICE" AndroidManifest.xml` returns both entries (or confirms they already exist).

### Step 1.6 — Add debug Timber tag

- [ ] After the `startForeground` call, add:

```kotlin
Timber.d("S0172: AudioPlaybackService startForeground called in onCreate")
```

This tag must remain until ticket S0172 leaves `BlockNeedUserTest`.

### Step 1.7 — Build and smoke-test

- [ ] Run: `.\scripts\builders\build-debug.PS1`
- [ ] Install on test device: `.\scripts\builders\build-standard-device.ps1`
- [ ] Kill the app, press a hardware media button (or car Play button).
- [ ] **Verification:** `.\scripts\utils\search-log.ps1 -Tag "AudioPlaybackService" -Pattern "startForeground"` shows the log entry. No `RemoteServiceException` in the next session's startup log.

### Step 1.8 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt" "AudioPlaybackService" "S0172 Phase 01: add immediate startForeground in onCreate to prevent cold-start crash"
```

---

## Verification summary

| Check | Command / signal |
|-------|-----------------|
| `startForeground` before MediaSession | `grep -n "startForeground" AudioPlaybackService.kt` — line < MediaSession.Builder line |
| Manifest `foregroundServiceType` | `grep -n "foregroundServiceType" AndroidManifest.xml` |
| No `RemoteServiceException` after cold start | next session log contains no `RemoteServiceException` |
| Timber tag present | `grep -rn "S0172:" *.kt` — exists in `AudioPlaybackService.kt` |
