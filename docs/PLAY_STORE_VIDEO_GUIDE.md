# Google Play Console: Foreground Service Video Demonstration Guide

To comply with Google Play policies for `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, you must provide a video showing exactly why the app needs to run in the background.

## Video 1: DATA_SYNC (Scheduled Operations & Sync)

This video should demonstrate that the app performs data-heavy tasks that must not be interrupted when the app is minimized.

### Scenario A: Scheduled File Operations
1.  **Start Recording** on the device.
2.  Open **FastMediaSorter**.
3.  Go to **Settings** -> **File Operations** -> **Scheduled Operations**.
4.  Create a new operation (e.g., **Copy** from a local folder to a Network SMB share).
5.  Tap **"Run Now"** (Запустить сейчас).
6.  **Minimize the app** (press the Home button).
7.  **Pull down the notification shade** to show the persistent notification: *"Scheduled operation in progress.."*.
8.  Wait a few seconds, then **re-open the app** to show the operation completed or still progressing.
9.  **Stop Recording**.

### Scenario B: Background Network Sync (Optional but recommended)
1.  Go to **Network Settings**.
2.  Enable **"Background Sync"**.
3.  Trigger a sync manually or wait for it to start.
4.  Show the notification: *"Syncing resources.."*.
5.  Minimize the app to prove it continues in the background.

---

## Video 2: MEDIA_PLAYBACK (Audio & PiP)

This video demonstrates background audio and Picture-in-Picture.

### Scenario A: Background Audio
1.  **Start Recording**.
2.  Open an audio file (MP3/FLAC) in the **FastMediaSorter Player**.
3.  **Minimize the app**.
4.  The music **must continue playing**.
5.  **Pull down the notification shade** to show the Media Control notification (Play/Pause/Skip).
6.  **Turn off the screen** (optional, if your recorder supports it) and show that audio still plays.

### Scenario B: Picture-in-Picture (PiP)
1.  Open a **Video file** in the player.
2.  Press the **Home button**.
3.  The video should shrink into a **small floating window** (PiP).
4.  Interact with the PiP window (move it, pause/play).
5.  Return to the app.

---

## Technical Tips for Recording
*   **Show the Notification Shade**: This is the most important part for Google. They need to see the "Foreground Service" notification.
*   **Resolution**: At least 720p.
*   **Duration**: Keep it under 2 minutes per video.
*   **No Voiceover Needed**: Just clear visual actions.
*   **Include Device Frames**: Google prefers seeing the whole screen including the status bar.
