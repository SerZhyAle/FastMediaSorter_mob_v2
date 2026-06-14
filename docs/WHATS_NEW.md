# What's New in FastMediaSorter v2

**Current release: 2.60.6141.930** (June 2026)

> Changes since version 2.60.6050.126

---

## What's New

- **Downloadable Extensions** - install OCR, translation, DTS, and audio backgrounds on demand.
- **Smarter setup** - onboarding now includes profiles, functionality toggles, permissions, and Enable All.
- **Remote source toggles** - disable SMB, (S)FTP, or cloud sources without deleting resources.
- **Standalone viewer parity** - shared images now support OCR, translation, drawing, crop, and fullscreen.
- **All Files resource** - first-run file-manager entry opens newest-first full browsing.
- **Quick camera capture** - 1×1 widget saves photos or videos to a chosen target.
- **SFTP key authentication** - connect with private keys, passphrases, and host-key pinning.
- **Audio visualizations** - downloadable background video set with automatic safe fallback.

## What's Fixed

- Broken audio tracks now skip instead of stopping playback.
- Recent Media no longer shows a false cancelled-scan error.
- Too-short microphone recordings no longer save broken files.
- Report-a-problem email now fills the support address.
- Drawing toolbar no longer hides behind the system navigation bar.

---

## Previous Release: 2.60.6050.126 (June 2026)

> Changes since version 2.60.6031.424

---

## What's New

- **Camera OCR languages** - choose OCR and translation language on the crop screen.
- **Re-translate OCR results** - switch target language without re-capturing.
- **Icon-style home widgets** - compact 1×1 widgets look like clean launcher icons.
- **Camera OCR widget** - launch Camera OCR from its own home-screen widget.
- **Add widgets from Settings** - pin a widget to the home screen in-app.
- **Capture & OCR panel** - one home-screen panel for Camera Photos and OCR.
- **Audio Now Playing widget** - control background audio from the home screen.
- **Random Photo Frame widget** - rotating random photo from a chosen source.
- **Quick Recorder widget** - one tap records voice, the next tap saves.
- **Scheduled Tasks widget** - view and run scheduled file operations from home.

## What's Fixed

- Network video playback no longer freezes on buffering.
- Opening network media no longer crashes on bad entries.
- Camera OCR crop frame now renders and aligns correctly.

---

## Previous Release: 2.60.6031.424 (June 2026)

> Changes since version 2.60.6010.151

---

## What's New

- **Device profiles** - first-run setup tailors defaults to your device type.
- **Color theme** - pick Auto, Light, or Dark regardless of system.
- **System info** - view, copy, and share a device and app summary.
- **Calculator upgrade** - scientific functions, memory keys, history, and expressions.
- **Calculator from text** - send selected OCR, translation, or document text in.
- **Text selection & copy** - long-press to select and copy in documents.
- **Richer language picker** - searchable list with flags, native names, and Czech.
- **Office document filter** - filter Office files and recheck all types instantly.
- **Camera OCR crop** - select a region before recognizing and translating text.
- **3D/VR defaults** - tune auto-detection and the default immersive mode (VR).
- **Smoother remote & mouse control** - improved D-pad, wheel scroll, and focus reach.

## What's Fixed

- Mouse left-click now reaches buttons, dialogs, and dropdowns.
- Camera OCR and Game Help controls stay within safe areas.

---

## Previous Release: 2.60.6010.151 (June 2026)

> Changes since version 2.60.5310.007

---

## What's New

- **Camera OCR translation** - capture, recognize, translate, and save text.
- **Text editor calculator** - calculate values without leaving text editing.
- **Mini game and widgets** - launch calculator, camera OCR, and game actions.
- **Release tooling guards** - digest build failures and detect flavor drift.

## What's Fixed

- Android TV launcher title now matches the banner and Play listing.
- Google Drive backup restore uses scoped GMS auth routing.

---

## Previous Release: 2.60.5310.007 (May 2026)

> Changes since version 2.60.5302.057

---

## What's New

- **Password-protected ZIPs** - extract encrypted archives after one-time prompt.
- **Protected document fallback** - locked EPUB and Office files fail clearly.

## What's Fixed

- Protected PDF renderer failures now show a clear message.

---

## Previous Release: 2.60.5302.057 (May 2026)

> Changes since version 2.60.5220.333

---

## What's New

- **Office documents** - open DOC, DOCX, RTF, and ODT with an installed viewer.
- **Send to Telegram** - share selected files to a Telegram client in one tap.
- **MIDI playback** - play MID/MIDI files like regular music tracks.
- **File Manager Mode** - browse and manage all file types, including archives.
- **VR content launch** - start video into immersive VR cinema from the player (S0292, VR flavor).

## What's Fixed

- DeX and multi-window file operations reachable via overflow menu (S0293).
- VR video no longer displays upside-down on Quest 3 (S0290).
- VR pointer rays no longer flicker from stale tracking (S0291).

---

## Previous Release: 2.60.5220.333 (May 2026)

> Changes since version 2.60.5202.303

---

## What's New

- **Cyrillic OCR** - Tesseract `tessdata_best` Cyrillic models with automatic fallback (S0287).
- **Settings Search Expansion** - broader coverage of settings across standard, VR, photos, and noLegal flavors.
- **VR Diagnostics Surface** - DiagnosticXR Activity with HUD canvas, raycast targeting, and input dispatch.
- **TV Navigation Overhaul** - unified D-pad focus and mouse-wheel routing across screens (S0289).

## What's Fixed

- Finger taps unresponsive on Quest 3 and touchpad TVs (S0289).
- OCR action could be re-triggered while recognition was in progress (S0288).

---

## Previous Release: 2.60.5202.303 (May 2026)

> Changes since version 2.60.5180.136

---

## What's New

- **16KB Page Alignment** - aligns native libraries to 16KB boundaries for Android 15+.
- **AI Audit Dashboard** - publishes consensus evaluation results to GitHub Pages.
- **Landscape Layout Tuning** - optimizes spacing and pairs setting rows on wide screens.

## What's Fixed

- Resolves MediaStore local copying regression on Android versions pre-Q (S0280).

---

## Previous Release: 2.60.5180.136 (May 2026)

> Changes since version 2.60.5172.102

---

## What's Changed

- **Unified expandable group headers** - Settings, AddResource, ResourceEditor, Scheduled Operations, Player Copy/Move panels, Duplicates, and Keybinding now share one header style; General Settings > About is now a static divider
- **Simplified player controls** - stereo content type, rendering mode, and IPD removed on non-VR flavors (S0241)
- **OCR on high-RAM low-heap devices** - text recognition restored on Quest 3 (7 GB RAM, 512 MB heap)
- **VR dead code purge** - 16 main-side files cleaned, 2 VR-only permissions removed (S0241 Phase 03)

## What's Fixed

- "Low memory, close player" snackbar no longer false-fires on capable devices (S0213)
- VR-only Browse routing branch removed on non-VR flavors (S0241)
- Google Drive sign-in now navigates to folder picker after Credential Manager flow completes

---

## Previous Release: 2.60.5172.102 (May 2026)

> Changes since version 2.60.5160.425

---

## What's New

- **Create text notes from Browse** - write notes on local, SMB, SFTP, FTP, or cloud resources with a 5-action save panel (S0189)
- **Central Google account** - one primary account via Credential Manager powers Drive and future Google integrations (S0200)
- **Universal input router** - D-pad, car steering, Bluetooth headset, and hardware keys mapped to semantic actions (S0230)
- **VR image and GIF viewer** - 3D/VR toolbar icon opens images in immersive view with auto-detected stereo layout (S0238, VR flavor)
- **IzzyOnDroid distribution** - fastlane metadata, changelog pipeline, and README badge prepared for catalog submission (S0215)

## What's Changed

- **System-managed rotation** - orientation lock removed; reverse-portrait, tablet, and foldable layouts now work (S0222)
- **Adaptive Copy/Move panels** - single-row layout on wide screens; button font scales with measured width (S0227)
- **SMB first-scan speedup** - listing appears before metadata pass; per-file 1500ms budget; concurrency 2 → 8 (S0237)
- **Draw editor toolbar** - collapsed to one row; save actions moved into overflow; filled palette swatch (S0192)
- **Welcome feature tiles** - 4 → 6 entries for symmetric 3-column tablet and VR layouts

## What's Fixed

- Instagram photo posts and carousels download correctly (S0223)
- Player no longer jumps to wrong file after delete-with-advance (S0226)
- SMB idle timeout no longer fires repeat bursts after one window (S0228)
- SFTP browse no longer fails with "inputstream is closed" after idle (S0219)
- Low-memory pre-playback toast suppressed in normal usage (S0207)
- SMB audio metadata noise (Handler-on-dead-thread, EOFException) cleared (S0229)
- YouTube community-post shares get a specific unsupported message (S0225)
- Carousel batch download notifications use accurate slide counts (S0224)
- Create-note and create-folder dialogs no longer trigger Google sign-in prompt
- OneDrive, Google Drive, and Dropbox sign-in restored on noLegal and VR sideload builds (S0232, S0233, S0235)
- Network-to-local copy works in public collections on Android 10+ without all-files access (S0231)
- Google sign-in reports PlayServicesOutdated with an update Play Services CTA
- noLegal and VR flavors no longer show "VR Headset Required" for text, audio, PDF, and EPUB
- noLegal on phones falls back to 2D player when no OpenXR runtime is available
- Legacy `cloud:/` path content visibility restored (S0236)
- Per-file overflow Delete no longer fails with "Files not selected"
- Welcome screen TAB key traverses to language pickers correctly (S0230)

---

## Previous Release: 2.60.5160.425 (May 2026)

> Changes since version 2.60.5150.150

---

## What's New

- **Draw editor v2** - oval and text annotation tools, 16-color palette dialog, in-place save, export to Google Keep
- **File size in browse list** - size shown in media item info row for video, image, and audio files

## What's Fixed

- Image-edit toolbar buttons (crop, compress, draw, open in window) no longer hidden in overflow for image viewer
- Link downloads no longer show a spurious sign-in prompt when a valid session is already active

---

## Previous Release: 2.60.5150.150 (May 2026)

> Changes since version 2.60.5142.201

---

## What's New

- **Big Buttons Mode refined** - 100dp controls, icon-dominant layout, adaptive 5..10 top-panel slots (spec S0208)
- **Slideshow auto-stop on network loss** - slideshow halts on consecutive failures with an informational message and resume via the existing button (spec S0188)
- **Permissions button relocated** - standalone tonal button below all card groups in General settings, before doc links
- **Device summary in settings** - compact device and Android line next to the app version for screenshot-based diagnostics

## What's Fixed

- Toolbar overlap with the system status bar on Android 8.x OEM car head units (spec S0204)
- NetworkLifecycleBootstrapper crash when initialised from a background thread (spec S0195)
- SFTP teardown no longer surfaces as an error in the log on cancel (spec S0205)
- Big Buttons Mode: audio player overflow menu now sized to the widest item

---

## Previous Release: 2.60.5142.201 (May 2026) - Fix Release

> Fix: vr-permission-bridge-fragment-public

## What's Fixed

- Fix IllegalStateException crash on VR passthrough camera capture when requesting the headset camera permission (spec S0203)

---

## Previous Release: 2.60.5130.151 (May 2026)

> Changes since version 2.60.5030.230

---

## What's New

- **Adaptive player command bar** - buttons automatically scale to screen width; overflow items move to a "more" menu
- **Sort progress indicator in Browse** - a loading indicator is shown when sorting large libraries (12,000+ files)
- **m2ts / BD-TS playback** - format detected by byte signature; routed through a dedicated decoder
- **Black-frame-free video thumbnails** - up to three retries to find a non-black frame when generating previews
- **SFTP audio position saved** - playback position is remembered and restored between sessions
- **SMB: auto-reconnect** - a stalled connection is restarted automatically; connection closes in background on app minimize
- **"Recents" resource without folder filter** - shows all recent media files regardless of their location
- **Network device discovery 3× faster** - ports probed in parallel; real-time scan progress shown in the UI
- **Link download: broken file detection** - a corrupted download is detected automatically; Instagram and TikTok CDN Referer/User-Agent replayed

## What's Fixed

- AVI files on network resources no longer cause a 10-second stall when loading thumbnails
- "End of String" speed-test error no longer surfaces in the UI
- Race condition generating video thumbnails over SMB during active playback - fixed
- SFTP: ArrayIndexOutOfBoundsException on seek in channel (JSch update 0.2.16 → 0.2.26)
- Volume buttons (Mute / 50% / MAX) did not fit the portrait player command bar - fixed
- Network device discovery dialog showed blank content and crashed on open - fixed
- Player: low-memory toast threshold lowered to 10 MB; repeat toasts suppressed within one session
- Browse: cancelling a background operation no longer invalidates the folder cache or appears as an error in logs
- Log noise removed: cancelled Glide requests, test credentials, and internal copy operations no longer logged

---

## Previous Release: 2.60.5030.230 (May 2026)

> Changes since version 2.60.422.246

---

## What's New

- **Keybinding remapper** - reassign any control in Settings; conflict detection, hierarchical reset; 70 built-in defaults
- **Full keyboard, mouse & gamepad support** - all screens (Browse, Player, Settings, dialogs); F1 help overlay per surface; D-pad list navigation
- **Camera capture in Browse** - take a photo and save it directly to the current resource
- **Play Random in Browse** - one-tap random playback for single-type libraries; Ctrl+P shortcut
- **Quick start from resource icon** - tap the resource type badge to instantly begin playback or slideshow
- **Link auto-download** - share any http(s) URL to the app; the media file is downloaded automatically
- **Single-eye 3D mode** - new setting to crop stereo content to one eye for flat-screen viewing (video + images)
- **Settings restructured** - "Files & Data" split into "Permissions" + "App Data"; "File Operations" → "Copy & Move"; Safe Mode moved to Operations

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

- **Control dialog** - new unified dialog (Volume, Brightness, Speed, Subtitles, 3D) for both video and audio. Left-side vertical section selector, changes apply live. Quick 50%/MAX volume presets and highlighted Mute button
- **Playback speed range** extended to 3.0x (added 2.5x and 3.0x steps)
- **Video color and brightness** - Hue and Brightness via Media3 GPU effects, persisted across video files
- **Save Frame** - snapshot of the current video frame to PNG or JPG, saved to any resource (local or network). Format and destination resource configured in Video Settings
- **Print** - send documents (PDF, TXT, images) to a printer directly from the player; network and cloud files are cached first
- **Random file** - dice button to jump to a random file in the list; shown in audio and photo library profiles
- **Filename overlay auto-hide** - overlay fades out automatically: TEXT 5s, PDF/EPUB 10s, others 15s. Timer resets on pause and zoom
- **File Info enriched** for video: aspect ratio, audio channels, bitrate. For audio: album and sample rate
- **3D content detection** by filename (SBS/OU/360) with a toast notification
- **Back navigation fixed** in the text reader - no blank screen on exit

---

## SMB / Network

- **Adaptive SMBJ timeout** - 3 tiers (5s/10s/20s) based on measured connection speed; far fewer false disconnects on slow NAS
- **Broken pipe fix** - stale pooled TCP connection is invalidated before re-opening a file
- **Watchdog 12s (open) + 15s (read)** in SMB: ExoPlayer no longer hangs in STATE\_BUFFERING on silent network drops
- **Error classification fixed**: wrapped exceptions are now recognised correctly (was "no network" instead of "timeout")
- **Integer overflow fixed** when reading files larger than 2 GB over SMB

---

## Adding Resources

- **Collapsible sections** in the SMB/SFTP add-resource form (Conditions, Media Types, Additional)
- **Quick Setup profile** now saved to the database (was ignored before)
- **Read-only SMB** - adding a read-only resource shows an informational message instead of an error
- **"Remember file list"** option now present in the add form, matching the editor

---

## File Browser

- **Resource drag-to-reorder** - drag and drop to manually sort resources; switches to MANUAL mode automatically
- **RANDOM reshuffle** - tapping RANDOM sort again produces a new random order
- **Dice icon** for the active RANDOM sort mode
- **Filter fix**: changing a filter no longer resets an already loaded SMB file list

---

## Audio

- **Playback notification** - track name and cover art shown in the system notification
- **Tap notification body** to navigate back to the player from the background
- **Now Playing bar** controlled by a dedicated setting; hidden for audio by default
- **Audio metadata** (Artist/Album/Title) cached in the database and shown instantly on open

---

## Settings

- **"Resume on next launch"** - new toggle in Playback Settings
- **Frame snapshot settings** - destination resource and format (PNG/JPG) in Video Settings
- **Back navigation fixed** from Settings to the main screen
- **Language spinner fixed**: no longer fires on screen open

---

## Performance & Stability

- **Adaptive pre-cache** for network files - buffer size calculated automatically from connection speed and file bitrate. Configure in General Settings → Prefetch Cache
- **Stream Offload** - download a network file to local cache with a progress dialog and optional cleanup prompt after playback
- **DTS/DTS-HD support** via a custom FFmpeg build; software-decoded on any device

---

## UI

- **Action buttons in file list** enlarged and aligned to the bottom row - no longer overlap the filename
- **MD3 icons** for all player controls and menus - system `@android:drawable/*` drawables replaced
- **PDF page indicator in landscape** - fixed disappearing indicator
- **PDF thumbnails button** added to the command bar (portrait and landscape)
- **Language badge** on translation buttons (PDF/TXT/EPUB/Images) - restored
- **"How-To Guides" button** in General Settings opens usage scenarios
- **Cold start black screen** - eliminated by removing `windowDisablePreview`

---

## Share / Receive Files

- **"Share to FastMediaSorter"** - the app now receives files via the Android Share sheet and offers to copy them to a selected resource

---

*Previous versions: [Development Changelog](../dev/CHANGELOG.md)*
