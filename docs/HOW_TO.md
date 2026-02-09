---
layout: default
title: "📖 How-To Guides"
permalink: /docs/HOW_TO.html
---
# 📖 How-To Guides

Step-by-step instructions for common tasks.

[Русский](HOW_TO_RU.md) | [Українська](HOW_TO_UK.md)

---

## Note: Feature Availability by Flavor

Some features are only available in specific flavors. Check the table below:

| Feature | Standard | Lite | Photos | Legacy |
|---------|----------|------|--------|--------|
| Network folders (SMB, SFTP, FTP) | ✓ | ✓ | ✓ | ✗ |
| Cloud storage (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✗ | ✗ |
| Audio playback & lyrics | ✓ | ✗ | ✗ | ✓ |
| Document viewer (PDF, Text) | ✓ | ✓ | ✓ | ✓ |
| EPUB reader | ✓ | ✓ | ✓ | ✓ |
| Translation & OCR | ✓ | ✓ | ✓ | ✓ |
| Image editing | ✓ | ✓ | ✓ | ✓ |

If a feature is marked with "✗", you can download the **Standard** flavor to access all features.

---

## Table of Contents

1. [Connect to Network Drive (SMB)](#how-to-connect-to-network-drive-smb)
2. [Connect to SFTP/FTP Server](#how-to-connect-to-sftpftp-server)
3. [Connect to Cloud Storage](#how-to-connect-to-cloud-storage)
4. [Set Up Quick Sort Folders](#how-to-set-up-quick-sort-folders)
5. [Use Touch Zones](#how-to-use-touch-zones)
6. [Edit Photos](#how-to-edit-photos)
7. [Create Slideshow](#how-to-create-slideshow)
8. [Create Slideshow with Background Music](#how-to-create-slideshow-with-background-music)
9. [Protect Folder with PIN](#how-to-protect-folder-with-pin)
10. [Empty Trash](#how-to-empty-trash)
11. [Backup Settings](#how-to-backup-settings)
12. [View Text and PDF Files](#how-to-view-text-and-pdf-files)
13. [Open Network Files in External Apps](#how-to-open-network-files-in-external-apps)
14. [Read E-Books (EPUB)](#how-to-read-e-books-epub)
15. [View Song Lyrics](#how-to-view-song-lyrics)
16. [Auto-Translation](#auto-translation)

---

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
4. Tap **"Sign in..."** button
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
5. Wait ~15 seconds for "Analyzing speed..."
6. See results:
   - **Read Speed (Mbps)**
   - **Write Speed (Mbps)**
   - **Recommended Threads** (for optimal performance)

---

## How to Set Up Quick Sort Folders

**Method 1: From Settings**

1. **Settings** → **Quick Sort** tab
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

1. **Settings** → **Audio** tab
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

1. **Settings** → **Quick Sort** tab
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

1. **Settings** → **General** tab
2. Tap **"Backup & Restore"**
3. Tap **"Export Settings"**
4. Choose location (e.g., Downloads)
5. File saved as `fastmediasorter_backup.xml`

**Restore Settings:**

1. **Settings** → **General** tab
2. Tap **"Backup & Restore"**
3. Tap **"Import Settings"**
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

1. **Settings** → **Documents**
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

- **Settings** → **Documents** → **Support EPUB** must be enabled (on by default)
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
   - `Previous` / `Next`: navigate chapters
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

1. **Settings** → **Documents**
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

## Need More Help?

- 📖 **Quick Start:** [QUICK_START.md](QUICK_START.md)
- ❓ **FAQ:** [FAQ.md](FAQ.md)
- 🔧 **Troubleshooting:** [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- 🐛 **Report Issue:** [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

