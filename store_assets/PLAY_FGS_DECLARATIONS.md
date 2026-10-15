<sub class="doc-stamp">26.10.15 10:03</sub>

# Play Console - Foreground service permissions declaration (S4241)

Source of truth for the **Foreground service permissions** form in Play Console
(`App content -> Foreground service permissions`): which types the store build carries, the use-case text
for each, the impact of an interruption, and the video each one needs.

The form lives only in the web console and takes a video link per type, so the text cannot be reviewed once
it is pasted. Write it **here** first. A new `FOREGROUND_SERVICE_*` type in an AAB uploads, but the commit
answers HTTP 403 until the declaration is saved (`docs/HOW_TO_DEVELOP_AND_RELEASE-ru.md`, marker
`[PLAY FGS]`); it is not a pipeline blocker, it is an owner step.

The set that must say the same thing: `docs/PRIVACY_POLICY.md` (+ `-ru`, `-uk`), `docs/SECURITY_POSTURE.md`,
`docs/manifest-risk-registry.jsonl`, the in-app `perm_rationale_*` strings and the listing
(`play/listing/<locale>/full_description.txt`).

---

## What Google asks

Read 2026-10-15 from `https://support.google.com/googleplay/android-developer/answer/13392821`, verbatim:

> When your apps target Android 14 and above, you'll need to declare any foreground service types that you use

For each type declared:

> Provide a description of the app functionality that is using each foreground service type.

> Describe the user impact if: the task is interrupted by the system (paused and/or restarted).

> Include a link to a video demonstrating each foreground service feature.

> The video should demonstrate the steps the user needs to take in your app in order to trigger the feature.

The page states the declaration is made per app, per type, on the App content page, and says nothing about
Wear OS or form factors; treat the watch bundle's types as part of the same app's list (section "Wear OS").

---

## Types of the store build, release 46

Measured against `docs/manifest-risk-registry.jsonl` (rows of kind `foregroundServiceType`) and the manifests
under `app_v2/src/` on 2026-10-15. The store flavor is `standard`; `noLegal` is outside Play.

- **`camera` - NEW in release 46 (S4235).** `VideoBroadcastService`, `camera|microphone`, permission
  `FOREGROUND_SERVICE_CAMERA`, from `src/broadcastVideoBackground`. `standard` and `noLegal`; `legacy` keeps
  the foreground-only service without it. **The owner's step: the video.**
- **`mediaProjection` - the screen broadcast is new (S4233); the screen-recording services already existed.**
  `BroadcastScreenService`, permission declared in `src/broadcastSource`. Standard also reaches the
  screen-recording services of `src/screenCapture` while the `fms.screenCapture` build flag is on. If the
  type is already declared for screen recording, edit its text; if it is not, declare it now.
- **`microphone` - existing, text extended.** Quick audio recorder widget, voice notes, the live audio
  broadcast (`BroadcastCaptureService`), and the camera broadcast which carries the phone's microphone.
- **`mediaPlayback` - existing, text extended.** Audio playback and the siren. The ready-file broadcast
  services (`BroadcastFileAudioService`, `BroadcastFileVideoService`, S4234) claim this type because they
  open neither camera nor microphone.
- **`specialUse` - existing, no change.** `OverlayHostService`, the edge-gesture handle of the screen
  capture strip (`src/standardScreenCapture`, with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` string).
- **`dataSync` - NOT declared in the release build today.** `src/release/AndroidManifest.xml` removes
  `FOREGROUND_SERVICE_DATA_SYNC`, while `SftpServerService` (the phone's folder sharing) and WorkManager's
  `SystemForegroundService` are typed `dataSync`. Open finding **S4263**: either the permission comes back
  with a declaration, or sharing and scheduled operations stay out of store builds. Decide there; this file
  carries the text for the first answer only so it is not re-derived.

Nothing about the exchange server, Google Drive or the clipboard adds a foreground-service type: those run
inside the services above, in the caller's foreground, or in WorkManager.

**The listing half, measured 2026-10-15.** `play/listing/en-US/full_description.txt` does not name live
broadcasting or the camera as a source of it; it says "a camera that shoots straight into your folder". A
declared `camera` type is judged against what the app tells the user, the way the All files access
declaration was judged against the description (`PLAY_PERMISSIONS_DECLARATION.md`). Whether the listing
names live broadcast is the owner's decision; this file does not edit the listing.

---

## Text to paste, per type

Use case of each is chosen from the console's own list; the closest entry is named below.

### `camera`

- **Use case (closest list entry):** continue to access the camera from the background.
- **Description:** "FastMediaSorter can broadcast the phone's camera live to the user's own devices on the
  user's network, or through an exchange server the user chose. The user starts a video broadcast on the
  Broadcast screen, from its Quick Settings tile or from its widget. The camera foreground service keeps the
  broadcast on air when the user leaves the app or turns the screen off, so the phone can serve as a camera
  for another screen. While it runs the app shows an ongoing notification, Broadcasting Video, with a Stop
  button, and Android shows its camera indicator. It never starts without the user's tap, records nothing
  and keeps no copy, and ends when the user presses Stop."
- **Impact of an interruption:** "The live picture on the receiving device stops and the receiver shows the
  broadcast as ended. The user starts it again from the Broadcast screen. Nothing is lost on the phone."
- **Video (owner records it):** 1) open the app, Broadcast; 2) start a video broadcast, allow the camera;
  3) on another device open the announced address and show the picture; 4) press Home and show the
  notification and the camera indicator; 5) turn the screen off and show the picture still running;
  6) pull the notification and press Stop, show the picture end. Use a device and a scene created for the
  recording; no real person's footage.

### `mediaProjection`

- **Use case (closest list entry):** project media to non-primary display or external devices using the
  MediaProjection APIs, including streaming.
- **Description:** "The user can broadcast the phone's screen to the user's own devices on the user's
  network, or through an exchange server the user chose, and can record the screen to a file. Both start from
  the app on the user's tap, and Android asks for the screen-capture consent every time. The service shows
  an ongoing notification, Broadcasting the Screen, with a Stop button, and the system shows its own sharing
  indicator. The broadcast ends when the user presses Stop or ends the sharing from the system indicator."
- **Impact of an interruption:** "The shared screen stops and the receiver shows the broadcast as ended; the
  user starts it again and gives the consent again."
- **Video:** Broadcast, start the screen broadcast, the system consent dialog, the receiver showing the
  screen, the notification with Stop, ending from the system indicator.

### `microphone`

- **Use case (closest list entry):** capture audio input, for example voice recording.
- **Description:** "The user records a voice note from the app or a home-screen widget, or broadcasts the
  phone's microphone live to the user's own devices. Each starts on the user's tap and shows an ongoing
  notification with a Stop button; the service keeps recording or broadcasting when the screen turns off.
  The same type carries the microphone of a camera broadcast."
- **Impact of an interruption:** "The recording ends at the moment of interruption, with what was recorded
  up to then kept; a broadcast ends and the user starts it again."
- **Video:** the voice recorder widget start, a live audio broadcast start, the notification, Stop.

### `mediaPlayback`

- **Use case (closest list entry):** audio or video playback continuing in the background (this wording
  was not read from Google's page; pick the console's entry that says so).
- **Description:** "The app plays the user's music and audio files in the background, with a media
  notification and controls. A ready-file broadcast sends a stored audio or video file to the user's own
  devices; it uses the same type because it opens neither the camera nor the microphone and shows an ongoing
  notification with a Stop button."
- **Impact of an interruption:** "Playback or the file broadcast stops; the user starts it again."
- **Video:** start playback, leave the app, the notification controls; start a file broadcast, the
  notification, Stop.

### `specialUse` and `dataSync`

- `specialUse`: unchanged. Re-read the text the console already holds; edit only if it names the strip in
  words that no longer match `OverlayHostService`.
- `dataSync`: not declared; see S4263. If the first answer wins, the text is: "The user turns on folder
  sharing from the phone's settings. A foreground service runs the phone's SFTP server so the user's other
  devices can browse the chosen folders; it shows an ongoing notification with a Stop button and pauses when
  Android reaches its daily data-sync limit, resuming when the user opens the app. Scheduled file
  operations the user set up run in WorkManager's foreground service with a notification." Impact: "Sharing
  or the operation pauses and resumes the next time the app is in the foreground."

---

## Wear OS

The watch bundle goes to its own track and its types are listed in `PLAY_CONSOLE_CHECKLIST.md` section C2.1:
`microphone` (the voice recorder and the sound broadcast, each started by a tap on the watch) and
`mediaPlayback`. The watch store build declares no camera and no projection type. Re-read the declaration
after the watch bundle lands: because the page names no form factor, the phone's text for `microphone` and
`mediaPlayback` is the text the watch inherits, and it names both devices.

---

## Operator steps

1. Open `App content -> Foreground service permissions` and read what is declared now. Write its state and
   date into `docs/PLAY_PUBLISHING_STATE.md` (no block for it exists there yet).
2. Declare `camera` with the text above and the video the owner recorded. Declare `mediaProjection` if absent.
3. Edit the `microphone` and `mediaPlayback` texts to the versions above.
4. Leave `dataSync` until S4263 is decided.
5. Under a policy enforcement Play refuses automatic review - send the held edit from
   `Publishing overview -> Send changes for review`.

**Timing.** Do not touch the console while a submission is under review; an edit restarts the review from
the beginning.
