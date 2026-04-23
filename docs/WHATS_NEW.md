# What's New in FastMediaSorter v2

**Current release: 2.60.422.246** (April 2026)

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
