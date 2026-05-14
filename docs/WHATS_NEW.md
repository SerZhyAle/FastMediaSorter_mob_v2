# What's New in FastMediaSorter v2

**Current release: 2.60.5142.201** (May 2026) — Fix Release

> Fix: vr-permission-bridge-fragment-public

## What's Fixed

- Fix IllegalStateException crash on VR passthrough camera capture when requesting the headset camera permission (spec S0203)

---

## Previous Release: 2.60.5130.151 (May 2026)

> Changes since version 2.60.5030.230

---

## What's New

- **Adaptive player command bar** — buttons automatically scale to screen width; overflow items move to a "more" menu
- **Sort progress indicator in Browse** — a loading indicator is shown when sorting large libraries (12,000+ files)
- **m2ts / BD-TS playback** — format detected by byte signature; routed through a dedicated decoder
- **Black-frame-free video thumbnails** — up to three retries to find a non-black frame when generating previews
- **SFTP audio position saved** — playback position is remembered and restored between sessions
- **SMB: auto-reconnect** — a stalled connection is restarted automatically; connection closes in background on app minimize
- **"Recents" resource without folder filter** — shows all recent media files regardless of their location
- **Network device discovery 3× faster** — ports probed in parallel; real-time scan progress shown in the UI
- **Link download: broken file detection** — a corrupted download is detected automatically; Instagram and TikTok CDN Referer/User-Agent replayed

## What's Fixed

- AVI files on network resources no longer cause a 10-second stall when loading thumbnails
- "End of String" speed-test error no longer surfaces in the UI
- Race condition generating video thumbnails over SMB during active playback — fixed
- SFTP: ArrayIndexOutOfBoundsException on seek in channel (JSch update 0.2.16 → 0.2.26)
- Volume buttons (Mute / 50% / MAX) did not fit the portrait player command bar — fixed
- Network device discovery dialog showed blank content and crashed on open — fixed
- Player: low-memory toast threshold lowered to 10 MB; repeat toasts suppressed within one session
- Browse: cancelling a background operation no longer invalidates the folder cache or appears as an error in logs
- Log noise removed: cancelled Glide requests, test credentials, and internal copy operations no longer logged

---

## Previous Release: 2.60.5030.230 (May 2026)

> Changes since version 2.60.422.246

---

## What's New

- **Keybinding remapper** — reassign any control in Settings; conflict detection, hierarchical reset; 70 built-in defaults
- **Full keyboard, mouse & gamepad support** — all screens (Browse, Player, Settings, dialogs); F1 help overlay per surface; D-pad list navigation
- **Camera capture in Browse** — take a photo and save it directly to the current resource
- **Play Random in Browse** — one-tap random playback for single-type libraries; Ctrl+P shortcut
- **Quick start from resource icon** — tap the resource type badge to instantly begin playback or slideshow
- **Link auto-download** — share any http(s) URL to the app; the media file is downloaded automatically
- **Single-eye 3D mode** — new setting to crop stereo content to one eye for flat-screen viewing (video + images)
- **Settings restructured** — "Files & Data" split into "Permissions" + "App Data"; "File Operations" → "Copy & Move"; Safe Mode moved to Operations

## What's Fixed

- Video hue/brightness effects not applying on track change (Media3 effects-deferral fix)
- RANDOM sort not reshuffling on repeated taps
- Camera upload failing on network resources (SMB / FTP / SFTP)
- ScheduledOperationsWorker freeze on WAKE_LOCK race condition
- Print crash on Android 8 (API 26–27)
- SMB scan errors now shown as a Snackbar with error count instead of being silently discarded
- WebView for EPUB now created lazily (prevents OOM on low-memory devices)
- ExoPlayer recreated every 4 tracks to prevent native heap OOM in long sessions
- Default player chooser: fixed self-resolution loop (app was probing its own file)

---

## Previous Release: 2.60.422.246 (April 2026)

> Changes since version 2.60.4150.019

---

## Player

- **Control dialog** — new unified dialog (Volume, Brightness, Speed, Subtitles, 3D) for both video and audio. Left-side vertical section selector, changes apply live. Quick 50%/MAX volume presets and highlighted Mute button
- **Playback speed range** extended to 3.0x (added 2.5x and 3.0x steps)
- **Video color and brightness** — Hue and Brightness via Media3 GPU effects, persisted across video files
- **Save Frame** — snapshot of the current video frame to PNG or JPG, saved to any resource (local or network). Format and destination configured in Video Settings
- **Print** — send documents (PDF, TXT, images) to a printer directly from the player; network and cloud files are cached first
- **Random file** — dice button to jump to a random file in the list; shown in audio and photo library profiles
- **Filename overlay auto-hide** — overlay fades out automatically: TEXT 5s, PDF/EPUB 10s, others 15s. Timer resets on pause and zoom
- **File Info enriched** for video: aspect ratio, audio channels, bitrate. For audio: album and sample rate
- **3D content detection** by filename (SBS/OU/360) with a toast notification
- **Back navigation fixed** in the text reader — no blank screen on exit

---

## SMB / Network

- **Adaptive SMBJ timeout** — 3 tiers (5s/10s/20s) based on measured connection speed; far fewer false disconnects on slow NAS
- **Broken pipe fix** — stale pooled TCP connection is invalidated before re-opening a file
- **Watchdog 12s (open) + 15s (read)** in SMB: ExoPlayer no longer hangs in STATE\_BUFFERING on silent network drops
- **Error classification fixed**: wrapped exceptions are now recognised correctly (was "no network" instead of "timeout")
- **Integer overflow fixed** when reading files larger than 2 GB over SMB

---

## Adding Resources

- **Collapsible sections** in the SMB/SFTP add-resource form (Conditions, Media Types, Additional)
- **Quick Setup profile** now saved to the database (was ignored before)
- **Read-only SMB** — adding a read-only resource shows an informational message instead of an error
- **"Remember file list"** option now present in the add form, matching the editor

---

## File Browser

- **Resource drag-to-reorder** — drag and drop to manually sort resources; switches to MANUAL mode automatically
- **RANDOM reshuffle** — tapping RANDOM sort again produces a new random order
- **Dice icon** for the active RANDOM sort mode
- **Filter fix**: changing a filter no longer resets an already loaded SMB file list

---

## Audio

- **Playback notification** — track name and cover art shown in the system notification
- **Tap notification body** to navigate back to the player from the background
- **Now Playing bar** controlled by a dedicated setting; hidden for audio by default
- **Audio metadata** (Artist/Album/Title) cached in the database and shown instantly on open

---

## Settings

- **"Resume on next launch"** — new toggle in Playback Settings
- **Frame snapshot settings** — destination resource and format (PNG/JPG) in Video Settings
- **Back navigation fixed** from Settings to the main screen
- **Language spinner fixed**: no longer fires on screen open

---

## Performance & Stability

- **Adaptive pre-cache** for network files — buffer size calculated automatically from connection speed and file bitrate. Configure in General Settings → Prefetch Cache
- **Stream Offload** — download a network file to local cache with a progress dialog and optional cleanup prompt after playback
- **DTS/DTS-HD support** via a custom FFmpeg build; software-decoded on any device

---

## UI

- **Action buttons in file list** enlarged and aligned to the bottom row — no longer overlap the filename
- **MD3 icons** for all player controls and menus — system `@android:drawable/*` drawables replaced
- **PDF page indicator in landscape** — fixed disappearing indicator
- **PDF thumbnails button** added to the command bar (portrait and landscape)
- **Language badge** on translation buttons (PDF/TXT/EPUB/Images) — restored
- **"How-To Guides" button** in General Settings opens usage scenarios
- **Cold start black screen** — eliminated by removing `windowDisablePreview`

---

## Share / Receive Files

- **"Share to FastMediaSorter"** — the app now receives files via the Android Share sheet and offers to copy them to a selected resource

---

*Previous versions: [Development Changelog](../dev/CHANGELOG.md)*
