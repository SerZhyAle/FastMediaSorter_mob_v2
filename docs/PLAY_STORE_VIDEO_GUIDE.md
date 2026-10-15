<sub class="doc-stamp">26.10.15 13:39</sub>

# Google Play Console: Foreground Service Video Demonstration Guide

To comply with Google Play policies for `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, you must provide a video showing exactly why the app needs to run in the background.

## Video 1: DATA_SYNC (Folder Sharing, Background Transfers, Scheduled Operations & Sync)

This video should demonstrate that the app performs data-heavy tasks that must not be interrupted when the app is minimized.
Record it on a `standard` build made with `-Pfms.dataSyncFgsStandard=on` (S4271); a default `standard` release carries no `dataSync` type. Declaration text: `store_assets/PLAY_FGS_DECLARATIONS.md`.

### Scenario S: Folder Sharing (SFTP server)
1.  **Start Recording** on the device.
2.  Open **FastMediaSorter**, go to **Settings** and turn on folder sharing; pick a folder created for the recording.
3.  Show the ongoing notification with the address and its **Stop** button.
4.  **Minimize the app** (press the Home button).
5.  From another device, connect over SFTP and list the shared folder, so the reviewer sees the server answering with the app in the background.
6.  **Pull down the notification shade** to show the notification is still there.
7.  Press **Stop** in the notification and show it disappear.
8.  **Stop Recording**.

### Scenario T: Background File Transfer
1.  Start a **Copy** of several large files to a network folder from the browse screen.
2.  **Minimize the app** and **pull down the notification shade** to show the transfer progress notification.
3.  Re-open the app and show the finished copy.

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
