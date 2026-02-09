# FastMediaSorter v2 - User Documentation Plan

**Document Version**: 1.0  
**Target Audience**: End Users (non-technical)  
**Languages**: English, Russian, Ukrainian  
**Format**: Markdown with screenshots

---

## 📋 Documentation Structure

### 1. Quick Start Guide (Quickstart.md)
**Goal**: Get user from installation to first successful use in 5 minutes

#### 1.1 Installation
- Minimum requirements (Android 9+)
- Download and install APK
- First launch and permissions

#### 1.2 Essential Setup (3 steps)
- **Step 1**: Add first resource (local folder or network share)
  - Local folder: Tap "+" → Select folder
  - Network SMB: Server address, credentials, test connection
  - Limitations: Need network access for SMB
- **Step 2**: Configure at least 2 destinations
  - Open Settings → Destinations tab
  - Add folders for sorted files (e.g., "Family", "Work", "Trash")
  - Assign colors for quick recognition
  - Limitation: Max 10 destinations
- **Step 3**: Browse and play media
  - Select resource from main screen
  - Tap to open Browse view
  - Tap file to open Player
  - Use touch zones (see Touch Zones Guide)

#### 1.3 Common Issues
- Empty resource list → Need to add resources first
- Cannot connect to SMB → Check server address, credentials, network
- No destinations → Need at least 1 destination for copy/move operations

---

### 2. Complete Feature Guide (Features.md)

#### 2.1 Main Screen (MainActivity)
**Purpose**: Manage resources, navigate to Browse/Settings

**Features**:
- **Resource List**: Shows all added folders/shares with metadata
  - File count (">10000" for large folders)
  - Last sync time (network resources)
  - Availability status (green/red indicator, "N/A" for unavailable)
  - Scrollbar for long lists
- **Actions**:
  - Tap resource → Open Browse screen
  - Long press → Context menu (Edit, Copy, Delete)
  - "+" button → Add new resource
  - Refresh button → Update all resources
  - Settings button (gear icon) → Open Settings
- **Limitations**:
  - Network resources require active connection
  - File count updates only after browsing or manual refresh

#### 2.2 Browse Screen (BrowseActivity)
**Purpose**: View media files list, filter, sort, navigate to Player

**Features**:
- **Display Modes**:
  - List view: Filename, size, date, type icon
  - Grid view: Thumbnails with configurable size (via Settings)
  - Toggle via button in toolbar
- **Sorting**:
  - By Name ↑/↓, Date ↑/↓, Size ↑/↓, Type ↑/↓
  - Current sort mode shown in info bar
  - Sort persists when reopening resource
- **Filtering**:
  - Media types: Images, Videos, Audio, GIFs (via Settings)
  - Size limits: Set min/max per type (via Settings)
- **Pagination**:
  - Automatic for >1000 files
  - Smooth scrolling with lazy loading
- **Actions**:
  - Tap file → Open Player
  - Info bar shows: Resource name, file count, path, sort mode, selection count
  - Back button → Return to Main screen (saves metadata)
- **Limitations**:
  - Network resources: Loading depends on connection speed
  - FTP: Size/date may show "—" if server doesn't provide metadata
  - Pagination threshold: 1000 files (not configurable)

#### 2.3 Player Screen (PlayerActivity)
**Purpose**: View/play media, perform file operations (copy/move/delete/rename)

##### 2.3.1 Player Modes
**Fullscreen Mode** (default):
- No visible controls
- Touch zones for navigation (9 zones, see Touch Zones Guide)
- Swipe down from top → Show command panel
- Back button → Exit to Browse screen

**Command Panel Mode**:
- Top panel: Navigation, rename, delete, edit, info, share, undo, fullscreen, slideshow buttons
- Bottom panels: Copy To (green), Move To (blue) with destination buttons
- Touch zones: 2-zone (left=Previous, right=Next) or 3-zone (left 25%=Previous, center 50%=Gestures, right 25%=Next) if "Load full resolution" enabled
- Toggle panels: Tap panel header (▼/▶ arrow)
- Fullscreen button → Return to fullscreen mode

##### 2.3.2 Media Types Support
**Static Images** (JPEG, PNG, WebP, BMP, HEIF):
- Displays at 1920px by default
- "Load full resolution" setting → Original size + pinch-to-zoom/rotation gestures (command panel mode only)
- Touch zones for navigation (2-zone or 3-zone based on setting)

**GIFs**:
- Plays as animation
- No zoom/rotation support

**Videos** (MP4, WebM, MOV, MKV):
- ExoPlayer controls in fullscreen mode
- Thumbnail extraction optional (via Settings, may take 2+ seconds for network files)
- Progress bar, play/pause, seek
- Audio track support

**Audio** (MP3, WAV, AAC):
- Displays file info overlay (name, duration, bitrate)
- Audio waveform visualization (if available)
- Play/pause, seek controls

##### 2.3.3 File Operations
**Copy**:
- Select destination from Copy To panel or via dialog
- Settings: Overwrite on copy (default: OFF), Go to next after copy (default: ON)
- Progress notification for large files
- Supports: Local↔Local, Local↔Network, Network↔Network, Network↔Cloud

**Move**:
- Same as Copy, but source file deleted after successful copy
- Settings: Overwrite on move (default: OFF)
- Undo available if enabled in Settings

**Delete**:
- Soft delete: Moves to `.trash/` folder (can be undone)
- Hard delete: Permanent removal (if trash disabled in Settings)
- Confirmation dialog (if enabled in Settings)
- Trash cleanup: Auto-delete after 24 hours

**Rename**:
- Dialog with current name pre-filled
- Validates: No illegal characters, extension preserved
- Undo available (stores original name until next operation)

**Edit** (Images only):
- Rotate 90° clockwise/counterclockwise
- Flip horizontal/vertical
- Network images: Downloads → Edits temp file → Uploads back
- Saves JPEG with quality 90%

**Undo**:
- Last operation reversal (if enabled in Settings)
- Copy → Deletes copied file
- Move → Moves file back
- Delete → Restores from trash
- Rename → Restores original name
- Limitation: Single-level undo (no undo history)

##### 2.3.4 Slideshow Mode
- Auto-advance to next file after interval (1-60 seconds, configurable)
- "Play to end" option: Videos/audio play completely before advancing
- Countdown timer in top-right corner
- Pause: Touch zone 5 (center) or pause button
- Exit: Back button or Stop slideshow button

##### 2.3.5 Touch Zones (Fullscreen Mode)
**9-Zone Grid**:
1. Top-left: **Back** (exit to Browse screen)
2. Top-center: **Copy** (show copy dialog)
3. Top-right: **Rename** (show rename dialog)
4. Middle-left: **Previous** file
5. Middle-center: **Move** (show move dialog)
6. Middle-right: **Next** file
7. Bottom-left: **Command Panel** (show/hide panels)
8. Bottom-center: **Delete** (delete file)
9. Bottom-right: **Slideshow** (start/stop slideshow)

**Hint Overlay**:
- Shows on first Player launch (if enabled in Settings)
- Auto-dismisses after 5 seconds or on tap
- Re-show via Settings → Playback → "Show Hint Now" button

**Limitations**:
- Touch zones work in fullscreen mode only
- Command panel mode: Uses 2-zone or 3-zone layout (no 9-zone grid)
- 3-zone mode requires "Load full resolution" setting enabled

#### 2.4 Settings Screen (SettingsActivity)
**Purpose**: Configure app behavior, permissions, destinations

##### 2.4.1 General Tab
**Language**:
- English, Russian, Ukrainian
- Requires app restart after change

**Display**:
- Keep screen on: Prevents device sleep (default: ON)
- Show small controls: Reduces button size in command panel (default: OFF)

**Network Sync** (for SMB/SFTP/FTP resources):
- Enable background sync: Auto-update file counts (default: ON)
- Sync interval: 1-24 hours (default: 4 hours)
- Sync Now button: Manual sync trigger
- Status indicator: Shows last sync time

**Default Credentials**:
- Username and Password for network resources
- Applied when resource credentials empty

**Permissions**:
- Local files access: Required for Camera, Downloads, etc.
- Network access: Granted automatically

**Logs**:
- Show full log: Debug info for troubleshooting
- Show last 100 lines: Quick error check
- Copy to clipboard button

**User Guide**:
- Opens Welcome screen with setup instructions

**Backup/Restore**:
- Export settings: Creates `FastMediaSorter_export.xml` in Downloads
- Import settings: Reads from Downloads (restores all settings + resources + destinations)

##### 2.4.2 Media Tab
**Supported Media Types** (toggle ON/OFF):
- Support images (JPEG, PNG, WebP, BMP, HEIF) - default: ON
- Support GIFs - default: ON
- Support videos (MP4, WebM, MOV, MKV) - default: ON
- Support audio (MP3, WAV, AAC) - default: OFF

**Size Filters** (per type, exponential scale):
- Images: 1 KB - 100 MB (default: 1 KB - 100 MB)
- GIFs: 1 KB - 100 MB (default: 1 KB - 100 MB)
- Videos: 1 KB - 10 GB (default: 1 KB - 10 GB)
- Audio: 1 KB - 1 GB (default: 1 KB - 1 GB)
- Slider shows: "<1 KB", "10 KB", "100 KB", "1 MB", "10 MB", ">100 MB"

**Display**:
- Load full resolution: Loads original size (enables zoom/rotation) - default: OFF
  - When OFF: Loads 1920px for faster loading
  - When ON: Enables pinch-to-zoom and rotation gestures in command panel mode
- Show video thumbnails: Extracts first frame (may take 2+ seconds for network) - default: OFF

##### 2.4.3 Playback Tab
**Default Sort Mode**:
- By Name ↑, By Name ↓, By Date ↑, By Date ↓, By Size ↑, By Size ↓, By Type ↑, By Type ↓
- Default: By Name ↑

**Slideshow**:
- Interval: 1-60 seconds (default: 5 seconds)
- Play to end: Videos/audio play completely (default: OFF)

**Display Mode**:
- Default view: List or Grid (default: List)
- Grid icon size: 80-300px (default: 120px)

**Touch Zones**:
- Show hint on first run: Displays overlay on first Player launch (default: ON)
- Show Hint Now button: Re-triggers hint display

**File Operations**:
- Enable delete: Shows delete button/zone (default: ON)
- Enable rename: Shows rename button/zone (default: ON)
- Enable edit: Shows edit button (images only) (default: ON)
- Enable undo: Shows undo button + stores operation history (default: ON)
- Show detailed errors: Verbose error messages (default: OFF)

##### 2.4.4 Destinations Tab
**Copy Operations**:
- Enable copy to destinations: Shows Copy To panel (default: ON)
  - Overwrite on copy: Replaces existing files (default: OFF)
  - Go to next after copy: Auto-advances to next file (default: ON)
  - Show copy confirmation: Dialog before copying (default: OFF)

**Move Operations**:
- Enable move to destinations: Shows Move To panel (default: ON)
  - Overwrite on move: Replaces existing files (default: OFF)
  - Show move confirmation: Dialog before moving (default: ON)

**Destinations List**:
- RecyclerView with drag-to-reorder handles
- Delete button per destination
- Add button: Select from registered resources
- Order determines button layout in Player panels
- Limitation: Max 10 destinations

---

### 3. Resource Types Guide (Resources.md)

#### 3.1 Local Resources
**Type**: Android device folders (Camera, Downloads, DCIM, custom folders)

**Setup**:
- Tap "+" → Select "Local Folder"
- Choose folder via system picker
- Grant storage permissions if prompted

**Features**:
- Fast access (no network required)
- Automatic file count update after browse
- Supports all file operations (copy/move/delete/rename/edit)

**Limitations**:
- Requires storage permissions
- Cannot access folders outside granted paths (Android 10+ scoped storage)

**Metadata**:
- File count, last browse date, sort mode, display mode

#### 3.2 SMB Network Resources
**Type**: Windows/NAS network shares (e.g., `\\192.168.1.100\Photos`)

**Setup**:
- Tap "+" → Select "SMB Network Share"
- Enter: Server IP/hostname, Share name, Port (default: 445)
- Optional: Username, Password, Domain
- Test Connection → Verify access
- Add Resource

**Features**:
- Supports all file operations
- Background sync updates file count
- Credentials encrypted in local database
- Default credentials from Settings applied if empty

**Limitations**:
- Requires local network access
- SMB protocol: Basic auth only (no Kerberos)
- Port must be accessible (firewall check)
- Connection timeout: 10 seconds

**Troubleshooting**:
- "Connection failed" → Check server IP, share name, port
- "Authentication failed" → Verify username/password, try domain
- "Network unavailable" → Check Wi-Fi connection, same network as server

**Metadata**:
- Last sync time, file count, availability status

#### 3.3 SFTP Resources
**Type**: SSH File Transfer Protocol servers (Linux, Unix, SSH-enabled devices)

**Setup**:
- Tap "+" → Select "SFTP Server"
- Enter: Host IP/hostname, Port (default: 22), Remote path
- Authentication method:
  - Password: Enter username + password
  - SSH Key: Load private key file + optional passphrase
- Test Connection
- Add Resource

**Features**:
- Supports all file operations
- SSH key authentication (RSA, Ed25519, encrypted keys)
- Keyboard-interactive auth support
- Background sync

**Limitations**:
- Requires SFTP server access
- SSH key must be in PEM format
- Encrypted keys: Passphrase required
- Path must be absolute (e.g., `/home/user/photos`)

**Troubleshooting**:
- "Authentication failed" → Check username/password or key format
- "Host key verification failed" → Server fingerprint mismatch (security)
- "Connection refused" → Port 22 blocked or SFTP disabled

#### 3.4 FTP Resources
**Type**: File Transfer Protocol servers

**Setup**:
- Tap "+" → Select "FTP Server"
- Enter: Host IP/hostname, Port (default: 21), Remote path
- Optional: Username, Password (anonymous if empty)
- Test Connection
- Add Resource

**Features**:
- Supports all file operations
- PASV (passive) mode with active fallback
- Background sync

**Limitations**:
- Metadata may be unavailable (size=0, date=1970-01-01 shown as "—")
- FTP protocol: Unencrypted (not recommended for sensitive data)
- Some servers don't support MLSD command (fallback to LIST)
- Path must start with `/` (e.g., `/public/photos`)

**Troubleshooting**:
- "PASV timeout" → Firewall blocking passive mode, trying active mode
- "Login failed" → Check username/password
- "Path not found" → Verify remote path exists

#### 3.5 Cloud Resources (Google Drive)
**Type**: Google Drive folders

**Setup**:
- Tap "+" → Select "Google Drive"
- Sign In → Google OAuth authorization
- Select folder from picker
- Add Resource

**Features**:
- OAuth 2.0 authentication
- Supports copy/move/delete operations
- Trash API for soft delete
- Thumbnail URLs for fast loading

**Limitations**:
- Requires Google account
- OAuth client setup needed (SHA-1 fingerprint)
- Network required (no offline mode yet)
- Rename operation not implemented (use Google Drive app)
- Edit operation not implemented (download + re-upload manual)

**Troubleshooting**:
- "Sign-in failed" → Check Google account, OAuth client config
- "Permission denied" → Re-authorize app in Google account settings

#### 3.6 Cloud Resources (OneDrive) - Phase 4 (Implementation Complete)
**Type**: Microsoft OneDrive folders

**Setup**:
- Tap "+" → Select "OneDrive"
- Sign In → Microsoft OAuth authorization
- Select folder from picker
- Add Resource

**Status**: Backend ready, UI pending
- MSAL 6.0.1 authentication implemented
- REST API client complete
- Requires Azure AD application registration

#### 3.7 Cloud Resources (Dropbox) - Phase 4 (Implementation Complete)
**Type**: Dropbox folders

**Setup**:
- Tap "+" → Select "Dropbox"
- Sign In → Dropbox OAuth PKCE authorization
- Select folder from picker
- Add Resource

**Status**: Backend ready, UI pending
- OAuth 2.0 PKCE flow implemented
- Dropbox SDK 5.4.5 integrated
- Requires Dropbox App Console registration

---

### 4. Advanced Topics (Advanced.md)

#### 4.1 Network Image Editing
**How it works**:
1. User taps Edit button (image file on network resource)
2. App downloads full file to temp storage
3. User selects operation (Rotate/Flip)
4. App applies transformation
5. App uploads modified file back to server
6. Original file replaced

**Performance**:
- Large images (>10 MB): May take 10+ seconds over slow network
- Progress not shown (limitation, planned improvement)

**Limitations**:
- Network interruption → Operation fails, temp file deleted
- JPEG only (PNG support planned)
- Quality loss: Re-saved at 90% JPEG quality

#### 4.2 Pagination
**When activated**: Automatically for folders with >1000 files

**How it works**:
- Loads files in batches of 50
- Scroll to bottom → Loads next batch
- Seamless (user doesn't notice)

**Performance**:
- Reduces initial load time (1-2 seconds vs 10+ seconds for 10000 files)
- Memory efficient (only loaded files in RAM)

**Limitations**:
- Threshold not configurable (always 1000)
- FTP: May be slower due to protocol overhead

#### 4.3 Undo System
**How it works**:
- Stores last operation in memory (single-level)
- Copy → Saves copied file path
- Move → Saves original path
- Delete → Moves to `.trash/` folder (soft delete)
- Rename → Saves original filename

**Undo actions**:
- Copy → Deletes copied file from destination
- Move → Moves file back to original path
- Delete → Moves file from `.trash/` back to original folder
- Rename → Renames file back to original name

**Limitations**:
- Single-level undo (no multi-step history)
- Undo lost on: App restart, resource change, 10+ file navigation
- Network operations: May fail if connection lost

#### 4.4 Background Sync (Network Resources)
**How it works**:
- Worker runs every N hours (1-24, configurable)
- Scans network resources (SMB/SFTP/FTP)
- Compares file count with database
- Updates metadata if changed

**Settings**:
- Enable/disable in Settings → General → Network Sync
- Interval slider: 1-24 hours (default: 4 hours)
- Sync Now button: Manual trigger

**Battery optimization**:
- Android may delay sync to save battery
- Ensure app not battery-optimized (Settings → Battery → Unrestricted)

**Limitations**:
- Only updates file count (not full file list)
- Requires network access during sync
- Sync status not real-time (check Main screen indicator)

#### 4.5 Trash Cleanup
**How it works**:
- Deleted files moved to `.trash/` folder in resource
- Cleanup job runs every 24 hours
- Deletes files older than 24 hours from trash

**Manual cleanup**:
- Not implemented (planned feature)
- Workaround: Delete `.trash/` folder manually via file manager

**Limitations**:
- No trash folder browsing in app
- Cleanup interval not configurable (always 24 hours)

#### 4.6 ProGuard/R8 Obfuscation (Release Builds)
**What it does**:
- Shrinks APK size (removes unused code)
- Obfuscates class/method names (security)
- Optimizes bytecode (performance)

**Keep rules**:
- Network libraries: SMB (smbj), SFTP (JSch), FTP (Commons Net)
- Cloud SDKs: Google Drive, Dropbox, OneDrive (MSAL)
- Logging: Timber removed in release

**Testing**:
- Always test release APK before publishing
- Check: All features work, no crashes, ProGuard mapping uploaded

---

### 5. Troubleshooting (Troubleshooting.md)

#### 5.1 Common Issues

**App crashes on startup**:
- Clear app data: Settings → Apps → FastMediaSorter → Clear Data
- Reinstall app
- Check Android version (minimum Android 9)

**Empty resource list**:
- No resources added yet → Tap "+" to add first resource
- Resources deleted → Restore from backup or re-add

**Network connection failed**:
- Check server IP/hostname
- Verify credentials (username/password)
- Test network connectivity (ping server)
- Check firewall rules (allow SMB port 445, SFTP port 22, FTP port 21)
- Try different port if custom
- For SMB: Verify share name, check domain requirement

**Slow browsing**:
- Large file counts (>10000) → Pagination helps, but still slow
- Network latency → Check connection speed
- FTP: Use SMB/SFTP if available (faster)

**Images not loading**:
- Check "Support images" enabled in Settings → Media
- Verify file size within limits (Settings → Media → Size Filters)
- Network images: Check connection
- Local images: Verify storage permissions granted

**Videos not playing**:
- Check "Support videos" enabled in Settings → Media
- Verify codec support (MP4/H.264 recommended, MKV/HEVC may fail)
- Network videos: Check connection speed (buffering)
- ExoPlayer errors: Check detailed errors in Settings → Playback

**Copy/Move operations fail**:
- Check destination has write permissions
- Network destinations: Verify credentials allow write access
- Check disk space on destination
- For SMB: Share must allow write (not read-only)

**Undo doesn't work**:
- Check "Enable undo" in Settings → Playback
- Undo only available for last operation
- Undo lost after: App restart, resource change, 10+ file navigation

**Touch zones not working**:
- Touch zones only work in fullscreen mode
- Command panel mode: Uses 2-zone or 3-zone layout
- Check hint overlay (Settings → Playback → Show Hint Now)

**Pinch-to-zoom not working**:
- Requires "Load full resolution" enabled (Settings → Media)
- Only works in command panel mode
- Only for static images (not GIFs/videos)

#### 5.2 Logs and Debugging

**Access logs**:
- Settings → General → Show Full Log
- Or: Show Last 100 Lines (quick check)
- Copy to Clipboard button for sharing

**Log format**:
```
2025-11-18 03:56:12.345 D/PlayerActivity: Loading network image: /Photos/vacation.jpg from SMB
2025-11-18 03:56:13.123 E/SmbClient: Connection failed: java.net.SocketTimeoutException
```

**Log levels**:
- D (Debug): Normal operation info
- I (Info): Important events
- W (Warning): Potential issues
- E (Error): Failures

**Debug mode**:
- Settings → Playback → Show detailed errors
- Shows verbose error messages in dialogs

---

### 6. FAQ (FAQ.md)

**Q: What's the difference between Copy and Move?**  
A: Copy creates a duplicate file in destination (original stays). Move transfers file to destination (original deleted). Both respect "Overwrite" setting.

**Q: How many destinations can I have?**  
A: Maximum 10 destinations. This limit ensures UI remains usable with large destination button panels.

**Q: Can I use the app offline?**  
A: Local resources work offline. Network resources require active connection. Cloud resources require internet. Offline mode for cloud planned (Phase 5).

**Q: Why are some file sizes/dates showing "—"?**  
A: FTP servers may not provide metadata. SMB/SFTP provide accurate metadata. This is FTP protocol limitation.

**Q: How do I delete resources?**  
A: Long press resource in Main screen → Delete. Or: Tap resource → Edit (pencil icon) → Delete button at bottom.

**Q: Can I edit videos?**  
A: No, only images support rotate/flip operations. Video editing not planned.

**Q: Why does background sync drain battery?**  
A: Sync runs every N hours (configurable). Android may optimize battery by delaying sync. Ensure app not battery-optimized in Android Settings.

**Q: How do I backup my settings?**  
A: Settings → General → Export Settings. Creates XML file in Downloads folder. Import via "Import Settings" button.

**Q: What happens to trash files?**  
A: Deleted files moved to `.trash/` folder. Cleanup job runs every 24 hours, deletes files older than 24 hours. Trash not browsable in app.

**Q: Can I sort files during slideshow?**  
A: No, sort mode must be set before opening Player. Change sort mode in Browse screen → Refresh Player.

**Q: Why can't I zoom images in fullscreen mode?**  
A: Zoom requires command panel mode + "Load full resolution" setting enabled. Tap zone 7 (bottom-left) to show command panel.

**Q: How do I reset all settings?**  
A: Settings → Apps → FastMediaSorter → Clear Data. Warning: This deletes all resources and destinations. Backup first (Export Settings).

---

## 📸 Screenshots Plan

### Required Screenshots (per language: en/ru/uk)

1. **Main Screen**:
   - Empty state (no resources)
   - Populated list (5+ resources with metadata)
   - Resource context menu (Edit/Copy/Delete)

2. **Browse Screen**:
   - List view with files
   - Grid view with thumbnails
   - Info bar showing sort mode

3. **Player Screen**:
   - Fullscreen mode (image)
   - Command panel mode (with Copy/Move panels)
   - Touch zones hint overlay
   - Video playback with controls

4. **Settings**:
   - All 4 tabs (General/Media/Playback/Destinations)
   - Size filter sliders
   - Destinations list with reorder handles

5. **Dialogs**:
   - Add Resource (SMB/SFTP/FTP/Cloud options)
   - Copy/Move confirmation
   - Rename dialog
   - Permissions request

6. **Welcome Screen**:
   - Touch zones guide page
   - Resource types page
   - Destinations page

---

## 🌍 Localization Strategy

### Translation Workflow
1. Write English version first (master)
2. Translate to Russian (native speaker)
3. Translate to Ukrainian (native speaker or AI with review)
4. Screenshots: Capture in all 3 languages

### Terminology Consistency
- Use glossary from `V2_TERMS.md`
- Resource (ресурс, ресурс)
- Destination (получатель, призначення)
- Touch zone (тач-зона, сенсорна зона)
- Command panel (командная панель, командна панель)

---

## 📝 Documentation Maintenance

### Update Triggers
- New feature added → Update Features.md + Screenshots
- Bug fixed → Update Troubleshooting.md
- Settings changed → Update Settings section
- New resource type → Update Resources.md

### Version Control
- Documentation version matches app version
- Change log in each document header
- Git commits per documentation update

---

## 🎯 Success Metrics

**User can**:
- Install and add first resource in <5 minutes (Quickstart)
- Find feature documentation without contacting support (Features)
- Solve 80% of issues via Troubleshooting
- Understand touch zones from guide (Touch Zones)
- Configure all settings from Settings guide

---

## 📦 Deliverables

1. ✅ **USER_DOCUMENTATION_PLAN.md** (this file)
2. ⏳ **Quickstart.md** (en/ru/uk)
3. ⏳ **Features.md** (en/ru/uk)
4. ⏳ **Resources.md** (en/ru/uk)
5. ⏳ **Advanced.md** (en/ru/uk)
6. ⏳ **Troubleshooting.md** (en/ru/uk)
7. ⏳ **FAQ.md** (en/ru/uk)
8. ⏳ **Screenshots** (40+ images, 3 languages)

---

## 🚀 Implementation Roadmap

### Phase 1: Core Documentation (Week 1)
- [ ] Write Quickstart.md (en)
- [ ] Write Features.md sections 2.1-2.3 (en)
- [ ] Capture main screenshots (en)

### Phase 2: Complete Features (Week 2)
- [ ] Write Features.md section 2.4 (Settings)
- [ ] Write Resources.md (all types)
- [ ] Capture settings screenshots

### Phase 3: Advanced & Troubleshooting (Week 3)
- [ ] Write Advanced.md
- [ ] Write Troubleshooting.md
- [ ] Write FAQ.md

### Phase 4: Localization (Week 4)
- [ ] Translate all docs to Russian
- [ ] Translate all docs to Ukrainian
- [ ] Capture localized screenshots
- [ ] Review and proofread

### Phase 5: Publication (Week 5)
- [ ] Host docs on GitHub Pages
- [ ] Add links to app (Help button → web docs)
- [ ] Create PDF versions (optional)
- [ ] Announce to users

---

**End of Documentation Plan**
