# S1785 - Link-download notification tap obeys the open-in-player setting

**Status:** Archived

## 1. Symptom

With **Settings -> Operations -> Open downloaded file in player** switched off, a link auto-download still opened the player.

Evidence, device log `logs/fastmediasorter_20260817_053458.log` (SM-S731B, Android 16, noLegal debug):

- Settings dump line 198: `linkAutoDownloadOpenInPlayer : false`.
- 13:43:28, single Instagram download, `FellBackToDownloads` - player did NOT open, app went to background. Foreground auto-open path honoured the flag.
- 19:17:31, Threads batch of 10, `BatchCompleted`, result notification posted. 19:17:32.203 `onCreate: PhotoVideoStandaloneActivity` on `content://media/external/downloads/29642`. The player opened through the notification's content intent.

## 2. Cause

Two "open in player" entry points exist after a download completes and only one read the setting.

- `LinkAutoDownloadResultPresenter` (foreground auto-open) reads `linkAutoDownloadOpenInPlayer` and gates on it.
- `LinkDownloadWorker.postResultNotification` attached `buildOpenInPlayerPendingIntent` unconditionally in all three success branches (`Saved`, `FellBackToDownloads`, `BatchCompleted`) and never read the setting.

The bypass was deliberate under S0257, which treated an explicit notification tap as a manual override of the auto-open preference. In practice that left the setting unable to stop the player from opening at all, while its label promises exactly that.

## 3. Fix

`LinkDownloadWorker` injects `SettingsRepository`, reads the flag once in `doWork` (suspend context, so `postResultNotification` stays non-suspend), and passes it in. Each of the three success branches now attaches the player content intent only while the flag is on. A failed settings read falls back to `false`.

With the flag off the result notification stays informational: it still reports what was saved, and `setAutoCancel(true)` dismisses it on tap.

## 4. Files

- `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`

## 5. Validation

- `.\a.ps1 fk` - BUILD SUCCESSFUL.

## 6. Open questions

None. S0257's override decision is superseded by this ticket, not left open.
