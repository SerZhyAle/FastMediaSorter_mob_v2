---
layout: default
title: "📖 How-To Guides"
permalink: /docs/HOW_TO.html
---
# 📖 How-To Guides

Step-by-step instructions for common tasks.

This guide now has two layers:

- **Scenario Groups** for richer real-life workflows and combinations of features.
- **Core Task Reference** for direct one-feature recipes further below.

[Русский](HOW_TO_RU.md) | [Українська](HOW_TO_UK.md)

---

## Note: Feature Availability by Flavor

Some features are only available in specific flavors. This guide follows the current matrix from [FEATURES.md](FEATURES.md); the XR / noLegal surface is intentionally separate because it depends on headset hardware and sideload build rules.

| Feature | Standard | Lite | Photos | Legacy | XR / noLegal |
|---------|----------|------|--------|--------|--------------|
| Network folders (SMB, SFTP, FTP) | ✓ | ✓ | ✓ | ✗ | ✓ |
| Cloud storage (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✗ | ✗ | ✓ |
| Audio playback & lyrics | ✓ | ✗ | ✗ | ✓ | ✓ |
| Document viewer (PDF, Text) | ✓ | ✓ | ✓ | ✓ | ✓ |
| EPUB reader | ✓ | ✓ | ✓ | ✓ | ✓ |
| Translation & OCR | ✓ | ✓ | ✓ | ✓ | ✓ |
| Image editing | ✓ | ✓ | ✓ | ✓ | ✓ |

If a feature is marked with "✗", choose the **Standard** or **XR / noLegal** build that matches your hardware and distribution path.

---

## Table of Contents

### Scenario Groups

#### Home media, TV and living room flows

1. [Turn a NAS into a living-room media shelf](#turn-a-nas-into-a-living-room-media-shelf)
2. [Run a slideshow with background music for a room display](#run-a-slideshow-with-background-music-for-a-room-display)
3. [Watch SBS 3D videos in VR mode](#watch-sbs-3d-videos-in-vr-mode)
4. [Use FMS on Android TV Box](#how-to-use-fms-on-android-tv-box)

#### Travel, reading and document workflows

5. [Prepare a folder for travel without stable internet](#prepare-a-folder-for-travel-without-stable-internet)
6. [Read cloud documents and EPUBs on the go](#read-cloud-documents-and-epubs-on-the-go)
7. [Translate signs, scans and screenshots with OCR](#translate-signs-scans-and-screenshots-with-ocr)
8. [Hand network files off to specialist apps](#hand-network-files-off-to-specialist-apps)

#### Power-user and mixed media workflows

9. [Sort a family photo archive with Quick Sort](#sort-a-family-photo-archive-with-quick-sort)
10. [Create Slideshow with Background Music](#how-to-create-slideshow-with-background-music)
11. [Read E-Books (EPUB)](#how-to-read-e-books-epub)
12. [Auto-Translation](#auto-translation)

### Core Task Reference

13. [Connect to Network Drive (SMB)](#how-to-connect-to-network-drive-smb)
14. [Connect to SFTP/FTP Server](#how-to-connect-to-sftpftp-server)
15. [Connect to Cloud Storage](#how-to-connect-to-cloud-storage)
16. [Set Up Quick Sort Folders](#how-to-set-up-quick-sort-folders)
17. [Use Touch Zones](#how-to-use-touch-zones)
18. [Edit Photos](#how-to-edit-photos)
19. [Create Slideshow](#how-to-create-slideshow)
20. [Protect Folder with PIN](#how-to-protect-folder-with-pin)
21. [Empty Trash](#how-to-empty-trash)
22. [Backup Settings](#how-to-backup-settings)
23. [View Text and PDF Files](#how-to-view-text-and-pdf-files)
24. [Open Network Files in External Apps](#how-to-open-network-files-in-external-apps)
25. [View Song Lyrics](#how-to-view-song-lyrics)

---

## Scenario Groups

These sections are intentionally more varied than the core reference blocks below. Each scenario mixes a fast path with context, trade-offs, and the situations where FastMediaSorter is especially strong.

## Home media, TV and living room flows

## Turn a NAS into a living-room media shelf

**Available in:** Standard, Lite, Photos

**Quick Path**

1. Add your NAS as an SMB resource.
2. Run **Scan Network** if you do not want to type the IP manually.
3. Open the resource from a TV box, tablet, or phone.
4. Start browsing videos, photos, or documents directly from the NAS.

**Scenario Walkthrough**

- Keep one SMB resource for the whole family library and separate child folders by use: Movies, Family Photos, Scans, Manuals.
- Run **Test Connection** once during setup so the resource is stable before you rely on it from the sofa.
- If the NAS is used from a TV box, pair it with a Bluetooth keyboard or TV remote for fast navigation.
- If browsing feels slow, open the resource settings and run the built-in speed check before changing anything else.

**When It Helps**

- You want one central media source instead of copying the same files to several devices.
- You want the same library to work for slideshow, document reading, and playback.

**Avoid This**

- Do not start with hostname troubleshooting. Use an IP address first, then optimize later.
- Do not expect Legacy flavor to browse SMB shares.

## Run a slideshow with background music for a room display

**Available in:** Standard

**Quick Path**

1. Add one image source and one music source.
2. In **Settings → Media → Audio playback, covers and visuals**, enable background music for slideshows.
3. Pick the music resource.
4. Open a photo folder and press **Play**.

**Scenario Walkthrough**

- Use a local image folder or a fast NAS share for the smoothest transitions.
- Keep a separate music resource for calm background tracks so slideshow audio is predictable.
- If the folder contains both images and videos, remember that music pauses automatically when a video starts.

**When It Helps**

- You want a TV box, tablet, or old phone to act as a digital frame for a room.
- You want one setup that can rotate family photos, event shots, or travel albums without manual queue building.

**Avoid This**

- Do not use a very slow network share for both images and music if smooth playback matters.

## Watch SBS 3D videos in VR mode

**Available in:** Standard, Legacy

**Quick Path**

1. Open an SBS 3D video.
2. Enter fullscreen.
3. Open **Playback Settings**.
4. Switch **3D Video** to **Auto-detect** or **Side-by-Side (SBS)**.

**Scenario Walkthrough**

- Start with Auto-detect for normal SBS files.
- If the picture looks doubled, force **Side-by-Side (SBS)** manually.
- For simple phone VR viewers, confirm the stereo mode first and only then place the phone in the headset.

**When It Helps**

- You have archived SBS vacation videos, concert captures, or hobby footage and want to revisit them without a separate VR media app.

**Avoid This**

- Do not expect Over-Under material to behave like SBS.
- Do not assume every wide video is 3D; verify before saving a playback preset.

## Travel, reading and document workflows

## Prepare a folder for travel without stable internet

**Available in:** Standard, Lite, Photos, Legacy

**Quick Path**

1. Create or choose one local folder for the trip.
2. Copy the media, PDFs, EPUBs, or notes you need into it before leaving Wi-Fi.
3. Open that folder once in FastMediaSorter so thumbnails and last positions are ready.
4. Use the folder offline during the trip.

**Scenario Walkthrough**

- Keep travel media in one local folder even if the originals normally live on NAS or cloud.
- Mix formats on purpose: boarding PDFs, reading EPUBs, screenshots, and offline music can live side by side.
- Use the filter panel if you want to switch between only images, only documents, or only audio while offline.

**When It Helps**

- Flights, trains, hotels, and rural areas where cloud streaming is unreliable.
- Situations where you want one offline pack instead of searching across several apps.

**Avoid This**

- Do not wait until the last minute to test whether the files really open without internet.

## Read cloud documents and EPUBs on the go

**Available in:** Standard for cloud access, Standard/Lite/Photos/Legacy for local reading

**Quick Path**

1. Add your cloud provider in **Cloud Storage**.
2. Open the folder that contains PDFs or EPUBs.
3. Tap the file directly from the cloud resource.
4. Continue reading from your last saved position later.

**Scenario Walkthrough**

- Use this when your working documents already live in Google Drive, OneDrive, or Dropbox and you do not want a separate reader workflow.
- PDFs are best for fixed-layout files like tickets, manuals, and scanned contracts.
- EPUB is better for long-form reading where adjustable font size and chapter navigation matter more than layout fidelity.

**When It Helps**

- You move between work documents and personal reading without leaving the app.
- You keep travel or client files in the cloud but still want a reading-first interface.

**Avoid This**

- Do not expect cloud reading in Lite, Photos, or Legacy.
- Do not treat slow mobile data as a guaranteed reading experience for very large files.

## Translate signs, scans and screenshots with OCR

**Available in:** Standard, Lite, Photos, Legacy

**Quick Path**

1. Open an image, PDF, or text file.
2. Show the command panel.
3. Tap **Translate**.
4. Confirm the model download on first use if needed.

**Scenario Walkthrough**

- For Latin-script text, the app usually starts with ML Kit for speed.
- For Cyrillic-heavy material, the app can switch to Tesseract for better recognition quality.
- Screenshots, receipts, menus, and scanned pages work especially well when the source text is reasonably sharp.

**When It Helps**

- You are travelling, reading foreign manuals, or decoding screenshots from chats and apps.
- You need translation in place instead of copying text into a separate tool first.

**Avoid This**

- Do not judge OCR quality from a blurred night photo or a badly cropped scan.

## Hand network files off to specialist apps

**Available in:** Standard, Lite, Photos

**Quick Path**

1. Open a file from SMB, SFTP, or FTP.
2. Tap **ⓘ Info**.
3. Tap **Download and Open**.
4. Pick the specialist app from the Android chooser.

**Scenario Walkthrough**

- Use this when FastMediaSorter is the best browser for remote storage, but another app is the best editor or viewer for one file type.
- Typical handoff cases are office documents, advanced PDFs, codec-heavy videos, and niche media formats.
- The downloaded copy remains in `Downloads`, so you can reopen it later even if the remote source goes offline.

**When It Helps**

- You want one remote file hub without giving up best-in-class specialist tools.

**Avoid This**

- Do not expect cloud handoff through this exact flow yet.

## Power-user and mixed media workflows

## Sort a family photo archive with Quick Sort

**Available in:** Standard, Lite, Photos, Legacy

**Quick Path**

1. Add your destination folders to **Quick Sort**.
2. Open the source folder with unsorted family photos.
3. Use numbered buttons or touch zones while reviewing images.
4. Send keepers to destination folders immediately.

**Scenario Walkthrough**

- Create destination folders by outcome, not by date alone: `Best`, `Print`, `Send to family`, `Archive`.
- Review in fullscreen so you can decide quickly and move or copy without dropping back to the file list.
- If several people curate the same archive, keep one consistent destination naming scheme before a large sorting session.

**When It Helps**

- You have a backlog from birthdays, trips, school events, or old phone imports.
- You want a fast triage flow instead of dragging files manually in a file manager.

**Avoid This**

- Do not start sorting before destinations are named clearly.
- Do not use Move immediately if you are still unsure which folders should stay as the long-term archive.

## Core Task Reference

## How to Connect to Network Drive (SMB)

**What you need:**

- NAS or Windows PC with shared folder
- Both devices on same Wi-Fi network
- Username and password for the share

**Available in:** Standard, Lite, Photos, Legacy flavors

**Steps:**

1. **Tap "+" button** on main screen
2. Select **"Network folder SMB"**
3. Fill in details:
   - **Auto-Discovery (New):**
     1. Tap **"Scan Network"** button
     2. Wait for devices to appear in the list
     3. Select your device from the list
     4. The IP address will be filled automatically

   - **Manual Input:**

     ```
     Server/Path: \\192.168.1.100\photos
     Username: john
     Password: ****
     Display Name: Home NAS (optional)
     ```

4. Tap **"Test Connection"** to verify
5. Tap **"Save"**

**Server address formats:**

- Windows: `\\192.168.1.100\share`
- Linux/Mac: `smb://192.168.1.100/share`
- With port: `smb://192.168.1.100:445/share`

**Tips:**

- Use IP address (not hostname) for reliability
- Enable SMB v2/v3 on NAS for security
- Default SMB port: 445

**Troubleshooting:**
→ See [TROUBLESHOOTING.md#smb-connection](TROUBLESHOOTING.md)

---

## How to Connect to SFTP/FTP Server

**What you need:**

- Server with SSH (SFTP) or FTP enabled
- Port 22 (SFTP) or 21 (FTP) open
- Username and password (or key for SFTP)

**Steps:**

1. **Tap "+" button** on main screen
2. Select **"SFTP / FTP"**
3. Choose Protocol: **SFTP** or **FTP**
4. Fill in details:

   ```
   Host: 192.168.1.100
   Port: 22 (SFTP) / 21 (FTP)
   Username: username
   Password: ****
   Remote Path: /home/user/photos (optional)
   ```

5. Tap **"Connect"**

**Advanced:**

- **SSH Key authentication:** Currently not supported (password only)
- **Custom port:** Change port number if server uses non-default

**Troubleshooting:**
→ See [TROUBLESHOOTING.md#sftp-timeout](TROUBLESHOOTING.md)

---

## How to Connect to Cloud Storage

**Supported Providers:**

- Google Drive
- OneDrive
- Dropbox

**Steps:**

1. **Tap "+" button** on main screen
2. Select **"Cloud Storage"**
3. Select provider: **Google Drive**, **OneDrive**, or **Dropbox**
4. Tap **"Sign in.."** button
5. Follow the browser/app authentication flow
6. Grant required permissions
7. **Select folders** to sync
8. Tap **"Done"**

**Notes:**

- Files are **streamed**, not downloaded
- Requires internet connection
- Edits sync automatically
- You can disconnect anytime: Edit folder → Remove

**Privacy:**

- No password stored (uses OAuth tokens)
- Tokens can be revoked in your cloud provider's security settings

---

## Check Network Speed

**Supported for:** SMB, SFTP, FTP, Cloud (Google Drive)

**Automatic Check:**
When you add a new network resource, the app automatically runs a speed test in the background. Results (Read/Write speed) are saved to the resource settings.

**Manual Check:**

1. Go to **Manage Resources**
2. Edit a network resource (pencil icon)
3. Scroll down to the bottom
4. Tap **"Speed"** button
5. Wait ~15 seconds for "Analyzing speed.."
6. See results:
   - **Read Speed (Mbps)**
   - **Write Speed (Mbps)**
   - **Recommended Threads** (for optimal performance)

---

## How to Set Up Quick Sort Folders

**Method 1: From Settings**

1. **Settings** → **Operations** tab → **Quick Sort destinations**
2. Tap **"Add to Quick Sort"**
3. Select an existing folder from list
4. Folder gets assigned number (0-9) and color
5. Repeat for up to 30 folders

**Method 2: From Folder Settings**

1. Main screen → **Long-press on folder**
2. Tap **"Edit"** (pencil icon)
3. Enable **"Mark for Quick Sort"**
4. Tap **"Save"**

**Using Quick Sort:**

While viewing files:

- Tap **numbered button** (0-9) on command panel
- OR tap **bottom-left corner** (COPY zone)
- OR tap **bottom-center corner** (MOVE zone)

File is instantly copied/moved to that folder!

---

## How to Use Touch Zones

**What are Touch Zones?**

The screen is divided into 9 invisible areas for quick actions:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Legend:**

1. **BACK** - Return to file list
2. **COPY** - Copy file to destination
3. **RENAME** - Rename current file
4. **PREV** - Go to previous file
5. **MOVE** - Move file to destination
6. **NEXT** - Go to next file
7. **COMMAND** - Open command menu
8. **DELETE** - Delete current file
9. **PLAY** - Start/Stop slideshow

**Enable Overlay (recommended for beginners):**

1. Settings → Playback
2. Enable **"Always show touch zones overlay"**
3. Now you'll see a semi-transparent grid

**Try it:**

1. Open any photo
2. **Tap top-right corner** → Next file
3. **Tap top-left corner** → Previous file
4. **Tap middle-right corner** → Delete file
5. **Tap middle-left corner** → Copy file

**Disable if not needed:**
Settings → Playback → "Always show touch zones overlay" = OFF

Then use **command panel buttons** instead.

---

## How to Watch 3D Videos (VR)

**Flavor**: Standard, Legacy

**What you need**: A 3D video file in SBS (Side-by-Side) format and optionally a phone-based VR viewer (e.g., Google Cardboard).

**Steps:**

1. Browse to and open a 3D video file in FastMediaSorter.
2. Tap the **fullscreen** button to enter fullscreen mode.
3. Tap **Playback Settings** (gear icon) in the player controls.
4. Scroll to the **3D Video** section.
5. Choose a stereo mode:
   - **Auto-detect** - app analyses the aspect ratio and embedded metadata to detect SBS automatically.
   - **Side-by-Side (SBS)** - manually enable side-by-side stereo rendering regardless of detection.
   - **Mono (Disabled)** - disable stereo (standard viewing).
6. Tap **Apply** - the player switches to the selected mode instantly.
7. For VR viewing, place your phone in a VR viewer and enjoy stereoscopic 3D.

**Tips:**

- Auto-detect works reliably for standard SBS files (aspect ratio ≈ 32:9 or wider).
- If the video looks stretched or doubled, switch to **Side-by-Side (SBS)** manually.
- Over-Under (OU) format support is planned for a future release.

---

## How to Edit Photos

**Supported operations:**

- Rotate (90°, 180°, 270°)
- Flip (horizontal, vertical)
- Filters (Grayscale, Sepia, Negative)
- Adjust (Brightness, Contrast, Saturation)

**Steps:**

1. **Open a photo** in full-screen viewer
2. Tap **"Edit"** button (or middle-left touch zone)
3. **Choose operation:**
   - Rotate: Tap rotate icon
   - Flip: Tap flip icon
   - Filter: Select from list
   - Adjust: Use sliders
4. Tap **"Save"**

**Notes:**

- Original file is **overwritten** (no undo!)
- Works for **local and network files**
- Supports: JPG, PNG, WEBP

---

## How to Create Slideshow

**Steps:**

1. **Open any folder** with photos
2. Tap **first photo** to open viewer
3. Tap **"Play"** button (or bottom-right touch zone)
4. Slideshow starts automatically

**Customize speed:**

1. **Edit folder settings:**
   - Main screen → Long-press folder → Edit
2. Change **"Slideshow Interval":**
   - Fast: 2 seconds
   - Normal: 5 seconds
   - Slow: 10 seconds
3. Tap **"Save"**

**Controls during slideshow:**

- **Tap screen** → Pause/Resume
- **Swipe left/right** → Skip files
- **Tap "Stop"** → Exit slideshow

---

## How to Create Slideshow with Background Music

**Requirements:**

- At least one folder/resource with audio files (MP3, FLAC, etc.)
- **Available in:** Standard flavor only

**Setup:**

1. **Settings** → **Media** tab → **Audio playback, covers and visuals**
2. Enable **"Enable Background Music for Slideshows"**
3. Tap **"Select Music Source"** button
4. Choose a resource that contains your music files
5. Tap **"Save"** or close settings

**Playing slideshow with music:**

1. **Open any folder** with photos/images
2. Tap **first photo** to open viewer
3. Tap **"Play"** button (or bottom-right touch zone)
4. Slideshow starts with background music playing

**How it works:**

- Music plays randomly from the selected music resource
- When a track ends, next random track starts automatically
- Music continues playing during image transitions
- Music stops when you exit slideshow or pause

**Notes:**

- Music plays only for **images and GIFs** (not for videos/audio)
- When slideshow shows a video, music automatically pauses
- Music resumes when returning to images
- Works with local and network music sources (SMB, SFTP, FTP)

**Customize music selection:**

- Add multiple music files to your music resource folder
- App will randomly shuffle through all audio files
- Organize music in subfolders if music resource has "Include Subfolders" enabled

**Troubleshooting:**

- If no music plays: Check that music resource contains at least one audio file
- If music stutters on network: Use local folder or faster network connection
- For SMB music: Ensure SMB resource uses `file://` protocol (see TROUBLESHOOTING.md)

---

## How to Protect Folder with PIN

**Steps:**

1. Main screen → **Long-press on folder**
2. Tap **"Edit"** (pencil icon)
3. Scroll to **"PIN Code"** field
4. Enter **4-6 digit PIN** (e.g., 1234)
5. Tap **"Save"**

**Now:**

- Opening this folder requires PIN
- Prevents unauthorized access
- Applies to browsing and editing

**Remove PIN:**

- Edit folder → Clear PIN field → Save

**Forgot PIN?**

- No recovery option (by design for security)
- You'll need to remove and re-add the folder

---

## How to Empty Trash

Deleted files go to `.trash/` folders and stay there until manually emptied.

**Method 1: Clear All Trash**

1. **Settings** → **Operations** tab → **File deletion and trash**
2. Tap **"Clear Trash"**
3. Confirm deletion
4. All `.trash/` folders across all resources are emptied

**Method 2: Per-Folder**

1. Use a file manager app
2. Navigate to folder (e.g., `/storage/emulated/0/DCIM/Camera`)
3. Find `.trash/` subfolder
4. Delete manually

**Warning:** This is **permanent deletion**! Files cannot be recovered.

---

## How to Backup Settings

**Export Settings:**

1. **Settings** → **General** tab → **Backups, restore and settings export**
2. Tap **"Export All Settings to File"**
4. Choose location (e.g., Downloads)
5. File saved as `fastmediasorter_backup.xml`

**Restore Settings:**

1. **Settings** → **General** tab → **Backups, restore and settings export**
2. Tap **"Import Settings from File"**
4. Select backup file
5. Tap **"Restore"**
6. App restarts with restored settings

**What's included:**
✅ Quick Sort folders
✅ Display preferences
✅ Slideshow intervals
✅ Network credentials (encrypted)
✅ Favorites
✅ Safe Mode settings

**NOT included:**
❌ Thumbnail cache
❌ Trash contents  

---

## How to View Text and PDF Files

**1. Enable Support:**

1. **Settings** → **Media** tab → **Text, PDF and EPUB viewing**
2. Enable **"Support Text Files"** and **"Support PDF Files"**
3. **Rescan** your folders to find the new files.

**2. Filter by Media Type:**

1. Tap the **Filter icon** (funnel) on the main screen (top right).
2. Use checkboxes to select media types:
   - Images
   - Videos
   - Audio
   - GIFs
   - **Text** (New)
   - **PDF** (New)
3. Tap **"Apply"** to see only selected files.

**3. Text Viewer:**

- Tap any **.txt, .md, .log, .json, .xml** file.
- **Scroll** to read.
- **Copy text:** Long press to select and copy.

**4. PDF Viewer (New Features):**

- Tap any **.pdf** file.
- **Navigation Control Bar (Bottom):**
  - **Previous/Next:** Large buttons at the edges.
  - **Zoom In (+):** Magnify page.
  - **Zoom Out (-):** Shrink page.
- **Gestures:**
  - **Swipe UP:** Go to Next page.
  - **Swipe DOWN:** Go to Previous page.
  - **Pinch:** Zoom in/out naturally.
  - **Double-tap:** Reset zoom.
- **Pan:** Drag to move around when zoomed in.

---

## How to Read E-Books (EPUB)

**Requirements:**

- **Settings** → **Media** tab → **Text, PDF and EPUB viewing** → **Support EPUB** must be enabled (on by default)
- Supported format: `.epub` (DRM-free)

**Features:**

- **Chapter Navigation:** Swipe left/right or use command panel buttons
- **Table of Contents:** Tap the list icon (📋) to jump to a specific chapter
- **Font Size:** adjustable (14px - 32px)
- **Search:** Find text within the current book
- **Themes:** Automatically adapts to Light/Dark mode

**Controls:**

1. **Open an EPUB file** from the file list
2. **Tap screen** to toggle command panel
3. **Use bottom controls:**
   - `Previous` / `next`: navigate chapters
   - `- A` / `+ A`: decrease/increase font size
   - `Search` (🔍): search text
   - `TOC` (📋): open table of contents
4. **Swipe gesture:** switch chapters naturally

**Note:** Works seamlessly with local files and network streams (SMB/SFTP/Cloud). Large books (>50MB) over slow networks might take a few seconds to load initially.

---

## How to Open Network Files in External Apps

**Available for:** SMB, SFTP, FTP files

**Use case:** You want to open a document, photo, or video from your network drive in a specialized external app (e.g., MS Office, Adobe Acrobat, VLC Player).

**Steps:**

1. **Browse to the file** on your network resource
2. **Tap the file** to open it in the player/viewer
3. **Tap the ⓘ (Info) button** in the top toolbar
4. **Tap "Download and Open"** button
5. **Wait for download** - progress dialog shows percentage
6. **Choose app** from the Android app chooser

**What happens:**

- File is downloaded to your `Downloads` folder
- Progress is shown in a dialog (0-100%)
- After download completes, Android shows app chooser
- You can open the file in any compatible app

**Supported protocols:**

- ✅ SMB/CIFS network shares
- ✅ SFTP servers
- ✅ FTP servers
- ❌ Cloud storage (not yet implemented)

**Tips:**

- Downloaded files remain in `Downloads` folder
- You can delete them manually later via file manager
- Works with all file types (images, videos, documents, etc.)
- For large files, download may take several minutes

**Example use cases:**

- Edit a network document in MS Word
- Play network video in VLC Player
- View network PDF in Adobe Acrobat
- Share network photo via messaging apps

---

---

## How to View Song Lyrics

**Requirements:**

- Audio file (MP3, FLAC, etc.) with Artist and Title metadata.
- **Internet connection** is required (uses api.lyrics.ovh).

**Steps:**

1. **Play an audio file** in the full-screen player.
2. Tap the **"Lyrics"** button in the top command panel (or command menu).
   - *Note: Button is only visible for audio files.*
3. Wait for the search to complete.
4. Lyrics will be displayed in a scrolling dialog.

**Search Logic:**

1. App searches by **Artist + Title** tag.
2. If tags are missing, it tries to parse the **Filename**.

---

## Auto-Translation

Automatically translate text from images, PDF, and text files using a **Hybrid OCR System** (Google ML Kit + Tesseract).

**Key Features:**

- **Hybrid Engine:** Uses Google ML Kit for fast Latin script recognition and **Tesseract** for high-quality Cyrillic (Russian, Ukrainian) recognition.
- **Offline:** Works entirely on-device (after initial model download).
- **Smart Overlay:** Translated text overlays the original text in readable paragraphs.

**Setup:**

1. **Settings** → **Media** tab → **Translation, OCR and Google Lens**
2. Enable **"Enable Translation"**
3. Select **Source Language**:
   - **"Auto" (Recommended):** Automatically selects the best engine (Tesseract for Cyrillic, ML Kit for others).
   - **Specific Language:** Forces a specific model (e.g., "Russian" forces Tesseract).
4. Select **Target Language** (e.g., English).

**How to use:**

1. Open an **Image**, **PDF**, or **Text** file.
2. Tap the screen to show the **Command Panel**.
3. Tap the **"Translate"** button (A→文 icon).
4. **First run:**
   - If using ML Kit: Confirm downloading the language model (~30MB).
   - If using Tesseract (Cyrillic): Confirm downloading OCR data (~15MB).
5. The translated text will appear in an overlay.

**Note:** Tesseract initialization (for Cyrillic) might take 1-2 seconds longer than ML Kit.

---

## How to Use FMS on Android TV Box

FastMediaSorter runs on any Android TV box or set-top box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, generic Android boxes). No touchscreen required - the app is fully operable via TV remote or Bluetooth keyboard.

**What you need:**

- Android TV box running Android 8.0+ (Standard/Lite/Photos) or Android 6.0+ (Legacy flavor)
- TV remote with D-pad, or a Bluetooth keyboard
- Optional: home NAS (SMB), USB drive, or SD card with media

**Navigation with a TV remote:**

| Button | Action |
|--------|--------|
| D-pad Up/Down/Left/Right | Move focus between items |
| OK / Enter | Open item or confirm |
| Back | Go to previous screen |
| Backspace | Navigate one folder up in Browse |
| Red | Delete selected file(s) |
| Green | Copy selected file(s) |
| Yellow | Move selected file(s) |
| Blue | Rename selected file |
| Channel Up / Channel Down | Previous / next file in player |

**Steps:**

1. Install the app from Google Play or sideload an APK. Standard flavor is recommended.
2. On the main screen, press **OK** on the (+) button to add a resource.
3. Choose **Local Folder** for USB/SD storage, or **Network folder** to connect to a NAS via SMB.
4. After adding the resource, navigate into it with D-pad + OK to browse files.
5. Open any video, image, or audio file - the player works fully via remote.
6. To start a slideshow, open an image folder and navigate to the **Slideshow** button in the command bar.
7. To add background music to the slideshow, go to **Settings → Media → Audio playback, covers and visuals** and select your music resource.

**Tips:**

- Hold D-pad Up/Down to accelerate scrolling through long file lists.
- Press **F1** on a Bluetooth keyboard to open a surface-specific shortcut reference on any screen.
- TV remote color keys can be reassigned in **Settings → Playback → Controls & Keybindings**.

---

## Need More Help?

- 📖 **Quick Start:** [QUICK_START.md](QUICK_START.md)
- ❓ **FAQ:** [FAQ.md](FAQ.md)
- 🔧 **Troubleshooting:** [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- 🐛 **Report Issue:** [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)
