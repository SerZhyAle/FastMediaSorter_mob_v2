# FastMediaSorter v2 — Complete Feature List

*Last updated: 2026-03-27*

This document is the canonical, up-to-date inventory of all user-facing features implemented in the application. It serves as a comprehensive guide to what the application can do, how each feature works, and why it is useful for the user.

**Platform requirements:** Android 8.0+ (API 26) for Standard, Lite, and Photos flavors. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same feature set but without cloud integrations.

## Table of Contents

- [1. Resource / Source Management](#1-resource--source-management)
- [2. Media Browsing](#2-media-browsing)
- [3. File Operations](#3-file-operations)
- [4. Destination Management](#4-destination-management)
- [5. Image Viewer](#5-image-viewer)
- [6. GIF Viewer](#6-gif-viewer)
- [7. Video Player](#7-video-player)
- [8. Audio Player](#8-audio-player)
- [9. Slideshow](#9-slideshow)
- [10. PDF Viewer](#10-pdf-viewer)
- [11. EPUB Viewer](#11-epub-viewer)
- [12. Text Viewer / Editor](#12-text-viewer--editor)
- [13. Translation & OCR (cross-viewer feature)](#13-translation--ocr-cross-viewer-feature)
- [14. Network Sources](#14-network-sources)
- [15. Cloud Integration](#15-cloud-integration)
- [16. Favorites](#16-favorites)
- [17. Home Screen Widgets](#17-home-screen-widgets)
- [18. Settings](#18-settings)
- [19. Settings Search](#19-settings-search)
- [20. Wear OS Companion App](#20-wear-os-companion-app)
- [21. Background & System Services](#21-background--system-services)
- [22. Scheduled File Operations](#22-scheduled-file-operations)
- [23. Apps FMS Can Replace — Competitor Comparison](#23-apps-fms-can-replace--competitor-comparison)

---

## 1. Resource / Source Management

- **Add multiple resource types**: Connect and manage various storage types including Local folders, SMB (Windows share/NAS), FTP, SFTP, Google Drive, Dropbox, and OneDrive. This unifies all your local, network, and cloud files into a single accessible interface.
- **Edit resource settings after creation**: Readjust settings, update network credentials, or change display preferences for any existing resource at any time without needing to recreate it.
- **Delete a resource**: Easily remove any connected resource from your library when it is no longer needed, keeping your workspace clean and relevant.
- **Resource profiles (quick-setup presets)**: Effortlessly set up new folders using tailored presets like Audio Library, Video Library, Photo Storage, Documents, or All Files. These presets automatically apply optimal sorting, filtering, and display settings for the chosen media type.
- **Camera Photos Virtual Folder**: Instantly access and browse all photos and videos taken by your device's camera through a dedicated, automatically configured virtual folder.
- **Per-resource settings**: Customize how each individual folder behaves with options like supported media types, default sort mode, display mode, thumbnail loading, PIN access, and whether subdirectories are scanned. This allows fine-grained control over how different types of content are presented.
- **Resource ordering**: Rearrange your connected resources on the main screen using a simple drag-and-drop gesture. This lets you position your most frequently accessed folders at the top for quicker access.
- **Filter resources**: Quickly find a specific folder using the search and filter bar on the main screen. This is especially useful for users managing dozens of different network and local directories.
- **List / Grid view switch**: Toggle between a detailed list view and a visual grid view directly on the main screen. This adapts the interface either for reading lengthy folder names or scanning through folder types.
- **Connection test**: Instantly verify if a network or cloud resource is accessible before trying to open it. This helps diagnose connectivity or credential issues on the spot without waiting for timeouts.
- **Read-only mode**: Protect critical folders from accidental modifications by enabling read-only mode for specific resources. When active, all file editing, deleting, and moving operations are completely disabled.
- **PIN protection**: Add an extra layer of privacy by requiring a PIN code whenever a specific resource is opened. This keeps sensitive photos or confidential documents safe from prying eyes.
- **Network credential management**: Securely store passwords and keys for your network shares in an encrypted vault. The built-in credential audit tracks when a login was last used, helping you eventually clean up obsolete credentials.
- **Last browse position save & restore**: Never lose your place when switching between folders or closing the app. The system automatically saves and restores your exact scroll position and the last viewed file for every resource.
- **File list caching**: Experience near-instant load times when reopening large network folders. The app persists the file index in a local database to bypass slow network fetching on subsequent visits.

## 2. Media Browsing

- **List and Grid display modes**: Choose how you want to browse your files. Use List mode for detailed file strings and metadata, or Grid mode to visually navigate through image and video thumbnails.
- **Extensive sort modes**: Organize files exactly how you need them using sorting options like Name, Date, Size, Type, Artist, Title, Duration, Date Taken, Random, or Manual order. Every sort mode supports ascending and descending directions.
- **Advanced filter panel**: Narrow down massive folders by filtering items using a filename substring, date ranges, size limits, or specific media types. This is essential for quickly locating a particular file in a cluttered directory.
- **Multi-select**: Perform actions on multiple files at once to save time. Select continuous ranges or individual files to batch copy, move, delete, or share them simultaneously.
- **Subfolder navigation**: Seamlessly dive deep into nested folder structures while maintaining a clear back-stack. This allows straightforward navigation back to higher-level directories without losing context.
- **Show subfolders as items**: Treat subdirectories as clickable entries mixed directly into your file list. Unchecking this option flattens the view, which is useful when you only care about the media files inside.
- **Subfolder operations**: Select, copy, move, rename, and delete subfolders directly in Browse when 'Show subfolders as items' is enabled; works for local, network (SMB/SFTP/FTP), and cloud resources.
- **Create subfolder from Browse menu**: Create a new subfolder directly from the Browse resource menu (local, network, cloud); available when "Show subfolders as items" is enabled and the resource is writable. The new folder appears in the list immediately without navigating into it.
- **Show hidden files**: Toggle the visibility of system-hidden files and folders (those starting with a dot) depending on whether you need a clean view or administrative access to all data.
- **Show all files mode**: Temporarily bypass all media-type filters to view every single file in a directory. This ensures you can manage and organize binary files or archives alongside your media.
- **Recursive directory scan**: Automatically delve into all underlying subfolders and compile their contents into a single unified list. This allows you to view all files within a complex directory tree simultaneously.
- **Intelligent thumbnail loading**: Enjoy rich visual previews for photos and videos. To maintain performance, thumbnail generation can be manually disabled per resource, which is recommended for extremely large directories.
- **Video thumbnails**: Identify video files quickly by previewing a generated thumbnail of their first frame. This feature can be disabled for network folders to heavily conserve bandwidth and improve loading times.
- **File metadata overlay**: See crucial file details at a glance without opening the properties dialog. Information like EXIF data, video duration, image resolution, and file size is overlaid directly on the list items.
- **Scan progress indicator**: Track the progress of large folder scans with a non-intrusive indicator that appears after 5 seconds. You can securely cancel long-running operations using the built-in STOP button.
- **Pagination**: Navigate incredibly large catalogs smoothly without memory crashes. The app automatically switches to paged loading to ensure the interface remains highly responsive regardless of folder size.
- **Inline audio mini-player**: Start playing music tracks seamlessly directly from the file browser. This avoids disrupting your navigation flow and lets you preview audio files instantly.
- **Keyboard navigation**: Use external or hardware keyboards to navigate lists and trigger actions. This facilitates a rapid, desktop-like browsing experience on tablets or devices with physical keyboards.

## 3. File Operations

- **Robust copying**: Duplicate files to any pre-configured destination, whether it's local storage, an SMB share, or an FTP/SFTP server.
- **Effortless moving**: Transfer files easily from their current location to a new configured destination, cleanly organizing your media across different storage protocols.
- **Flexible deleting**: Choose between permanently erasing a file or moving it to a recoverable Trash bin. This safety net prevents accidental data loss and can be customized in settings.
- **In-place renaming**: Quickly alter the name of any file without needing to move it. This makes correcting typos or reorganizing naming conventions incredibly fast.
- **Standalone player renaming**: Rename a file directly from the "Open with" standalone player (supported for SAF documents with write access and MediaStore files). A simple dialog pre-fills the current filename; changes are applied via DocumentsContract or ContentResolver without reloading the content already in memory.
- **Trash recovery**: Instantly restore erroneously deleted files back to their original folder from the in-app Trash bin, providing peace of mind during massive cleanups.
- **Operation undo**: Revert your last copy, move, or delete action with a single tap. This undo stack acts as an immediate failsafe if you realize you made a mistake managing your files.
- **Batch processing**: Apply copy, move, or delete actions to multiple selected files simultaneously. This drastically reduces the tedious manual work involved in managing large collections.
- **System sharing**: Open or send files to external applications installed on your device. This standard Android intent allows for quick sharing via email, messaging, or specialized editors.
- **Safe Mode**: Prevent disastrous accidents by enabling mandatory confirmation dialogs before moving or deleting files. This global master toggle gives you tight control over sensitive file operations.
- **Overwrite policies**: Specify on a per-direction basis how the app should handle copying or moving files that already exist in the destination. This helps automate conflict resolution without stalling your progress.
- **Detailed progress dialogs**: Monitor lengthy transfers with precision. The dialog displays byte-level progress, real-time transfer speeds, and time estimates, allowing you to track heavy network operations.
- **Cross-protocol transfers**: Seamlessly copy or move files between entirely different connection types. Directly transfer data between Local, SMB, FTP, and SFTP endpoints effortlessly, using the app as a robust intermediary.
- **Duplicate file detection**: Locate and permanently remove identical files scattered across your massive local, network, or cloud storage. The highly optimized 3-phase scanning engine (Size -> Partial Hash -> Full SHA-256) guarantees perfect byte-for-byte matches while minimizing slow network reads. Two menu modes available: **Find Duplicates** — scan and review results, then manually select files for deletion via the FAB; **Find and Delete Duplicates** — scan and automatically delete all detected duplicates (keeping the oldest copy per group) without additional confirmation. Both modes pre-select the current resource and move it to the top of the resource picker.
- **ZIP archiving**: Pack any number of selected local files into a single ZIP archive directly from the file browser. Choose a custom name and destination folder; the archive is created asynchronously in background with a real-time file-by-file progress indicator. Duplicate archive names are resolved automatically (e.g., `archive_1.zip`).
- **ZIP extraction on click**: Tap a ZIP archive in Browse to extract it in one flow: confirmation dialog, real-time percentage progress, and a one-tap action to open the extracted folder. Extraction supports local and SD-card SAF resources, applies secure path validation, and auto-resolves destination folder conflicts via `_1.._99` suffixes.
- **Select folder for copy/move**: Tap the "Select folder" button in the copy/move dialog to pick any local directory with the system folder picker, bypassing the pre-configured destination list. The last chosen folder is remembered per resource type. Per-item copy/move buttons are always shown regardless of whether destinations are configured.

## 4. Destination Management

- **Color-coded destination buttons**: Configure up to 10 distinct, color-coded shortcut buttons displayed directly inside the player. These buttons represent your favorite folders, drastically speeding up the organization process.
- **Universal compatibility**: Assign any writable local, network, or cloud resource as a target destination. This unified approach removes restrictions on where you can route your files.
- **Auto-advance after copy/move**: Enable the option to automatically jump to the next file as soon as a copy or move operation completes. This creates an incredibly fast, uninterrupted workflow when sorting through a queue of media.
- **Collapsible command panel**: Keep your screen uncluttered by collapsing the copy/move destination panel when it's not needed. This maximizes viewing space while keeping routing tools just a tap away.
- **Quick Favorites toggle**: Immediately mark or unmark the currently viewed file as a Favorite directly from the player screen, allowing you to curate a collection without returning to the file browser.

## 5. Image Viewer

- **Pinch-to-zoom**: Examine fine details in your photos with smooth, responsive full-screen zooming capabilities, supporting high-resolution images natively.
- **Crop to fullscreen**: Optimize standard photos for widescreen displays. This feature automatically fills the screen by cropping out black bars when the image and device orientations are matched.
- **Dynamic background effect**: Enjoy a highly immersive viewing experience. The viewer mathematically analyzes the current image to generate a beautifully blurred ambient color background that seamlessly matches the photo's tone.
- **In-place rotation**: Easily fix misoriented photos by rotating them clockwise or counter-clockwise. The rotation is permanently saved to the file without requiring a heavy external photo editor.
- **Instant flipping**: Horizontally or vertically mirror an image with a single tap. Just like rotation, these changes are destructively applied and saved straight to the original file.
- **Image adjustments**: Fine-tune the brightness, contrast, and saturation of your photos natively. These tweaks are saved directly, allowing minor touchups during your organizing sessions.
- **Color filters**: Quickly stylize images with simple filters like Grayscale, Sepia, or Negative. This provides quick visual variations without launching external apps.
- **Integrated OCR**: Effortlessly extract typed or handwritten text directly from any image. Powered by ML Kit and Tesseract, you can digitize text on photos, receipts, or screenshots instantly.
- **Augmented Reality translation**: Read signs, menus, or documents in foreign languages using a Google Lens-style overlay. Translated text blocks are synthetically drawn precisely over their original positions on the image.
- **Send to Google Lens**: Quickly bridge any photo to the external Google Lens app with a dedicated button for deeper AI analysis, shopping, or advanced visual search.

## 6. GIF Viewer

- **Native animated playback**: Loop and view animated GIF files perfectly with full hardware acceleration and correct timing.
- **Speed adjustment**: Dynamically slow down (0.25×) or speed up (4×) GIF playback on the fly. This adjusted speed setting is uniquely saved per file for future viewings.
- **First frame extraction**: Easily convert a distracting animated GIF into a static image by extracting and saving just its very first frame.
- **Complete frame extraction**: Deconstruct complex GIFs by extracting every single frame and saving them all as individual static images, allowing you to inspect specific moments.

## 7. Video Player

- **ExoPlayer integration**: Benefit from a robust, state-of-the-art playback engine based on ExoPlayer, capable of flawlessly handling various codecs and network stream formats.
- **Full-screen mode**: Immerse yourself completely as the system UI automatically hides, dedicating every pixel of your screen to the video content.
- **Playback position save & restore**: Stop watching securely in the knowledge that your exact playback position is saved per file. You will perfectly resume from where you left off the next time you open the video.
- **Resume Next Time**: Return to your media instantly. Upon a cold app start, the app automatically navigates through network and cloud resources to restore your absolute last active video dynamically.
- **Picture-in-Picture (PiP)**: Multitask without pausing. On Android 12+ devices, the video automatically shrinks into a floating window when you press the home button, letting you use other apps.
- **Configurable touch zones**: Personalize your player controls. Define exactly which invisible tap regions of the screen trigger previous, next, play/pause, or skip/seek commands.
- **Touch zones hint overlay**: Avoid confusion with a visual overlay that subtly highlights your configured touch zones during the very first launch, teaching you the control scheme intuitively.
- **Video Control dialog**: Open a single bottom-bar `Control` button placed immediately after `Next track` to adjust volume, audio track, subtitles, HUE rotation, GPU brightness, and playback speed from one tabbed dialog with reset actions. The same dialog model now works in both the main player and the standalone `Open with` video player.
- **Sleep timer**: Safely fall asleep while watching videos. Set a timer (from 15 up to 120 minutes) with an onscreen countdown badge that automatically halts playback when time expires.


## 8. Audio Player

- **Robust engine**: Rely on ExoPlayer for high-fidelity audio decoding, perfectly parsing both local and high-latency network music tracks.
- **Background playback**: Keep the music going even when you leave the app or lock the screen. A persistent foreground service with rich notification controls ensures uninterrupted listening.
- **Configurable exit behavior**: Choose what happens when you press Back while audio plays in background — ask every time (default), always stop, or always keep playing. Set once in Settings → Audio and the choice is remembered permanently.
- **Notification media controls**: Quickly play, pause, or skip tracks directly from your system's notification shade or lock screen without continuously reopening the app.
- **Intelligent album cover art**: Enjoy a visually rich music player that automatically retrieves album art from embedded ID3 metadata tags, or falls back to an online search (with an optional Wi-Fi-only restriction to save mobile data).
- **Local audio metadata cache**: downloaded covers and track info saved to `audio_metadata_cache/` folder; reused on subsequent plays without network requests; included in cache size display and "Clear Cache" action
- **Lyrics search and display**: View synchronized lyrics in a full-screen, distraction-free overlay. The app conducts a smart online search to provide read-along text for your current song.
- **Search in YouTube Music**: Instantly open YouTube Music with a pre-filled search query for the current track. Available as a dedicated button in landscape mode and as a menu item in portrait mode. Uses cached artist/title metadata when available, falls back to the filename.
- **Cast to Chromecast**: Send images, GIFs, audio, and local video to any Chromecast or Google Cast-compatible device directly from the player overflow menu (requires Wi-Fi). Local files are served via an in-process HTTP proxy; network and cloud files are downloaded to the device cache first. Available on all flavors (Standard, Lite, Photos, Legacy).
- **Random photos during playback**: Turn your music into a visual journey by automatically cycling random photos from a selected internal or network resource as a dynamic visual background.
- **Rich empty state animations**: Prevent a boring black screen if no cover art is found. Choose from mesmerizing audio visualizers including Canvas bars, Canvas waves, AVD pulses, and spectrum analyzers that react to the music. Canvas Waves now re-rolls a full 360-degree flow direction on each fresh start, with particles biased to match the scene while staying lightweight for Android 8.1+ devices.
- **Vinyl record indicator**: Identify active playback instantly with a stylish, animated rotating vinyl record icon positioned in the corner of your screen.
- **Sleep timer**: Drift off to music peacefully. This shared sleep timer will automatically pause playback when the defined countdown finishes, ensuring your battery—and your sleep—aren't drained.
- **Track metadata display**: Clearly read essential track information natively pulled from the file, properly displaying the artist, title, album, and track duration.
- **Resume Next Time**: Seamlessly pickup your listening session. Upon a cold start, the app restores your last active audio track and perfectly reconstructs your entire historical playlist and queue.
- **Now Playing UI**: While audio plays in the background, a persistent mini bar at the bottom of the player shows the current track title and play/pause button. Tapping it opens a full bottom sheet with album art, seek bar, prev/next controls, and a scrollable queue panel where you can tap any track to jump directly to it.

## 9. Slideshow

- **Time-based advancement**: Automate your viewing experience by having images and GIFs advance on their own. The transition interval can be configured anywhere from a rapid 1 second to a lingering 3600 seconds.
- **Random sequence order**: Ensure a fresh experience every time by shuffling your photos and GIFs randomly rather than following strict alphabetical or date sorting.
- **Integrated background music**: Elevate your visual slideshows by assigning a dedicated folder to play random background music continuously while images transition.
- **Play video/audio to end**: Intelligently mix static photos with videos and audio tracks. Enabling this forces the slideshow to wait until a playing video or song finishes entirely before moving to the next file, overriding the strict timer.
- **Countdown display**: Anticipate transitions easily with a subtle, non-intrusive "3 – 2 – 1" countdown badge that appears just before advancing to the slide.
- **Per-resource interval configuration**: Tailor slideshows to individual folders. Easily set a unique transition interval for a specific directory that safely overrides the app's global default settings.

## 10. PDF Viewer

- **Render and display multi-page PDF documents**: Open and read your PDF files natively within the app without needing third-party viewers. The built-in engine guarantees smooth scrolling and sharp rendering even for graphics-heavy documents.
- **Page mode (flip) and vertical scroll mode**: Choose the reading style that suits your content best. Flip through pages horizontally just like a physical book, or use continuous vertical scrolling for reports and articles.
- **Navigation panel with PDF page thumbnails**: Instantly jump to any specific section of a large document. The expandable side panel shows visual previews of all pages, allowing you to visually navigate long files.
- **Color modes (Normal, Night, Sepia)**: Reduce eye strain and adapt to your environment's lighting. Switch to Night mode in dark rooms or choose Sepia for a warmer, paper-like reading experience.
- **Zoom**: Magnify documents to comfortably inspect fine print, detailed diagrams, or high-resolution images. Standard two-finger pinch gestures adjust the zoom level precisely.
- **OCR + Translation (Google Lens style overlay)**: Read foreign-language documents without constantly switching to a dictionary app. The app recognizes the text on the page and neatly overlays the translation directly on top of the original words.
- **Text selection mode**: Tap the "T" button in the PDF controls bar to extract the current page's text (via OCR on most devices; natively on Android 15+) into a selectable overlay. Long-press any word to get selection handles, then choose **Translate** (sends only your selection to the translator), **Read Aloud** (speaks the selected fragment via TTS), or **Search in Google** from the floating action menu.
- **Read Aloud (TTS)**: Tap Read Aloud in the command panel to have the current PDF page spoken through the system text-to-speech engine. Works natively on Android 15+ (no OCR delay) and falls back to ML Kit OCR on earlier versions. TTS pauses automatically when you navigate to another page.
- **Large PDF thumbnail support for network files**: Identify your PDFs by their cover before fully downloading them from your remote server or cloud. This optional setting avoids unnecessary network usage when looking for a specific document.

## 11. EPUB Viewer

- **Comprehensive EPUB rendering**: Read standard EPUB e-books beautifully. The epub4j-powered engine deeply parses metadata, chapters, and styling to provide a highly polished native reading application.
- **Chapter navigation**: Swiftly move between narrative breaks using dedicated previous and next chapter gestures or buttons, avoiding tedious scrolling.
- **Table of contents navigation panel**: Understand the structure of your book instantly. A fully interactive table of contents panel lets you jump to any specific chapter or section immediately.
- **Advanced search**: Locate specific character mentions, quotes, or keywords efficiently. The engine can return up to 500 results found both within your current chapter or across the entire book.
- **Font size adjustment**: Tailor the text readability perfectly to your vision. Granular font size controls ensure you never have to strain your eyes or squint.
- **Font family selection**: Match the typeface to the genre of your book. Toggle effortlessly between standard Default, classic Serif, or typewriter-style Monospace fonts.
- **Reader themes**: Set the perfect reading ambiance with carefully designed Light, Dark, Sepia, or OLED Black themes. Choose the mode that best suits your environment and reduces eye strain.
- **Line height multiplier**: Control the breathing room of the text. Increase or decrease the line-height multiplier (from 1.0× to 3.0×) to make dense blocks of text much easier to digest.
- **Horizontal margins**: Give your text proper bordering. Adjust horizontal margins natively to bring text closer to the center, heavily improving readability on ultra-wide devices and tablets.
- **Position persistence**: The app precisely saves your exact scroll location and last read chapter, guaranteeing your book opens strictly where you abandoned it last time.
- **In-place translation**: Break language barriers natively. Select the text of a foreign EPUB chapter and have it translated seamlessly directly on your screen.
- **Selection action menu**: Long-press any word in an EPUB chapter to reveal the standard selection handles. The floating action menu includes **Translate** (sends the selected fragment to the translator), **Read Aloud** (speaks the selected fragment via TTS), and **Search in Google** alongside the platform's built-in Copy / Share / Select All items.
- **Read Aloud (TTS)**: Tap Read Aloud in the command panel to hear the entire current chapter spoken by the system text-to-speech engine. TTS pauses automatically when you move to another chapter.

## 12. Text Viewer / Editor

- **Universal text and code viewer**: Read plain text files, logs, and programming code natively. An intelligent automatic charset detection system guarantees files decode properly without garbled symbols.
- **Markdown rendering**: View documentation and readme files exactly how they were intended. The powerful Markwon library parses markdown syntax and beautifully renders headers, lists, and tables natively.
- **Syntax highlighting**: Analyze scripts and configuration files effortlessly. Native code highlighting colorizes distinct syntax elements, heavily improving code legibility directly from your phone.
- **Line numbers**: Identify specific points in tall scripts quickly by toggling the sequential line numbers display down the left side of the editor.
- **Reader themes**: Adjust the interface to your lighting. Choose from Light, Dark, Sepia, or System themes to drastically reduce eye fatigue when studying text files.
- **Gesture font sizing**: Intuitively resize text without entering menus. Simply execute a horizontal swipe gesture anywhere across the screen to rapidly scale font size up or down.
- **In-place text editing and saving**: Seamlessly alter text, fix typos, or rewrite code. Changes are saved back strictly to the source file, fully working on both local storage and remote network servers.
- **Auto-save and undo history**: Experiment with edits safely. The editor automatically saves states and features a deep Undo/Redo stack, ensuring a typo never destroys your work.
- **Full translation**: Convert whole text files or partial selections from their native language into your target language smoothly within the viewer interface.
- **Selection action menu**: Long-press any word to get selection handles. The floating action menu includes **Translate** (translates only the highlighted fragment) and **Search in Google**, in addition to the system's Copy / Share / Select All actions.
- **Copy all text**: Extract contents rapidly with a single-tap button tailored to pull the entirety of a heavy document onto your system clipboard.
- **Inline search panel (standalone parity)**: Tap-to-search with Next/Prev navigation works in both the internal file browser and the standalone "Open with" mode for PDF, EPUB (current chapter), and TXT files.

## 13. Translation & OCR (cross-viewer feature)

- **ML Kit OCR (Latin script)**: Extract pure unselectable text natively from images and flattened PDFs using Google's rapid ML Kit framework, bypassing the need for typing out data manually.
- **Expanded Tesseract support**: When standard Latin characters aren't enough, fallback onto the heavy-duty Tesseract engine to pull text from a broader variety of scripts and challenging fonts.
- **Automatic Language Identification**: Skip configuring toggles manually. The app automatically discerns the source language of a document or image block via advanced ML Models before triggering translations.
- **On-device offline translation**: Translate passages without internet. Specific language models are downloaded on demand and process translations locally for extreme privacy and speed.
- **Augmented Reality overlay**: Experience sci-fi level immersion natively. Translated text string blocks are mathematically superimposed into the precise layout coordinates of the original foreign text within PDFs and images.
- **Broad availability**: Utilize this powerful translation tech seamlessly across all relevant views. The exact same translation interactions function uniformly across the Image Viewer, PDF Viewer, Text Editor, and the EPUB Viewer.
- **Standalone mode parity**: Translation toggle (portrait/landscape-aware) works in both the internal file browser and the standalone "Open with" mode — opening a PDF or EPUB from an external app gives the same translator access as opening it from within FastMediaSorter.
- **Explicit target configurations**: Override automated selections securely by forcing specific source and target languages within the settings menu when parsing heavily distorted or mixed-language texts.
- **Result typography styling**: Choose exactly how your OCR results and overlapping translation blocks look by configuring their native font size and dedicated font family.
- **Text copying**: Effortlessly lift the deeply recognized text, or its resultant translation, directly into the system clipboard for immediate usage in emails, notes, or messages.

## 14. Network Sources

- **SMB (Windows Share / NAS)**: Deeply interface with your local network storage safely utilizing standard SMB protocol, unlocking the ability to browse, manage, stream, and edit massive remote collections effortlessly.
- **FTP integration**: Access traditional web servers and legacy systems via rigid File Transfer Protocol, empowering complete and compliant browse and file management pipelines.
- **Secure SFTP**: Connect to highly protected servers leveraging SSH protocols. This guarantees every browse, copy, and streaming operation is cryptographically ciphered.
- **Network auto-discovery**: Bypass frustrating IP typing. The app dynamically scans your current Local Subnet specifically checking ports 445, 21, and 22, streaming discovered Network Attached Storage endpoints to your screen natively in real time.
- **Built-in Speed test**: Eliminate network guesswork definitively. This tool fires synthetic payloads across the connection to accurately measure read/write speeds, ultimately recommending the optimal parallel thread count to maximize data throughput safely.
- **Configurable parallelism**: Harness the full capabilities of wide bandwidth limits. Specifically override single-thread operations by dividing copy jobs into 1, 2, 4, 8, 12, or 24 simultaneous synchronous connections.
- **SMB Connection pooling**: Enhance network agility drastically. The app efficiently caches authenticated SMB sessions, completely eliminating the latency incurred during repeated queries or file copies.
- **Connection throttling**: Protect aging servers from crippling under heavy load. The network layer actively limits requests appropriately to prevent stalling or crashing congested or weak NAS hardware.
- **Periodic background sync**: Prevent navigating outdated file structures. Utilizing Android's WorkManager framework, the app wakes periodically (from 1 to 24 hours) strictly in the background to update the local database with remote changes.

## 15. Cloud Integration

- **Google Drive access**: Break out of phone storage limits completely. Authenticate to utilize Google Drive, unlocking natively integrated folder picking, high-speed streaming, direct downloads, and rigorous file modifications remotely.
- **Dropbox connectivity**: Integrate seamlessly with your Dropbox vaults. Browse nested hierarchies elegantly, stream media live, and copy files directly between disparate locations.
- **OneDrive support**: Harness Microsoft’s ecosystem deeply. Leverage fluid streaming protocols, thorough directory exploration, and intensive file management right from the app.
- **Unified OAuth authentication**: Log in securely and officially within the app utilizing safe browser OAuth flows. Security tokens are rigidly encrypted and persisted locally, meaning you only ever have to log in exactly once.
- **Rigorous state backups**: Guard heavily against data loss. You can serialize your customized application settings, connection profiles, directories, and favorites into a strict JSON payload format tightly vaulted straight into your Google Drive.
- **Seamless cloud restoration**: Recover instantly upon installing on a new device. Connect to Google Drive specifically to download your backup JSON, magically reconstructing your settings, endpoints, and favorites effortlessly.

## 16. Favorites

- **One-tap marking**: Save important media files rapidly. Utilize the dedicated favorite star icon situated within the player and viewer interfaces to instantly flag or un-flag files as Favorites.
- **Dedicated accessible list**: Revisit essential files dynamically. Navigate seamlessly to the distinct Favorites List anchored on the app's main screen, compiling flagged files universally from all connected directories and protocols.
- **Interactive home screen widget**: Bring crucial media directly to your home launcher. Deploy an actively scrollable widget that elegantly lists exclusively favorite files heavily prioritizing immediate launching.

## 17. Home Screen Widgets

- **Favorites interactive list**: Deploy a rapidly accessible panel right on your Android home screen. This scrollable widget exclusively displays your flagged favorite files, allowing you to bypass menus completely and launch media instantly.
- **Resource Launch shortcut**: Condense navigation strictly down to a single tap. Set an actionable widget uniquely mapped to a specific folder or NAS drive, triggering the application to instantly open that specific browser or player view.
- **Continue Reading beacon**: Resume your place profoundly immediately. This clever widget mathematically identifies your absolute last viewed document or video alongside its parent resource, rocketing you strictly back into your last session with one press.
- **Random Music shortcut**: Instantly start shuffled playback from your "All Music" virtual resource with a single home screen tap. The widget launches the audio player in shuffle mode with autoplay — no menus required. Available on flavors with audio support.
- **Camera Photos shortcut**: Open your camera roll directly in grid view from the home screen with one tap. The widget targets the built-in Camera Photos virtual resource and launches the browser in grid mode. Available on flavors with image support.
- **App Shortcuts (long-press)**: Long-press the app icon on your launcher to access static shortcuts (Favorites, Slideshow) and up to 3 dynamic shortcuts for your most recently browsed resources. Jump straight into any folder or NAS share without opening the main screen.
- **Quick Settings Audio Tile**: Control background audio playback directly from the Android notification shade. Add the "FMS Audio" tile to your Quick Settings panel to play, pause, or start shuffled music in a single tap — no need to unlock and navigate to the player. Available on flavors with audio support.

## 18. Settings

The Settings module provides deeply comprehensive control over nearly every facet of the application:

| Area | Key features explained |
|---|---|
| **General** | Configure the overall UX. Explicitly set your preferred UI Language regardless of system default. Trigger settings that keep the screen aggressively awake during app operation, toggle condensed mini-controls securely, or specify a default local network login parameter. |
| **Media Types** | Tailor visibility rigidly. Independently toggle on or off entire categories of files (Images, GIFs, Videos, Audio, Text, PDF, EPUB). Furthermore, enforce strict minimum and maximum file size filters dynamically applied per discrete category. |
| **Images** | Maximize photo fidelity. Force the engine to parse memory-intensive files in complete full resolution (enabling extreme infinite zoom capabilities) or command the view specifically to crop photos forcibly to fill the entirety of your widescreen display. |
| **Audio** | Modulate music behavior explicitly. Toggle querying online for absent album art, explicitly lock those queries specifically to Wi-Fi to shield mobile data, dictate random dynamic photo backgrounds during play, and configure background persistence services. |
| **Text / PDF / EPUB** | Configure optimal reading environments deeply. Activate strict code line numbers, alternate immersive reader themes, enable explicit syntax highlighting parsing or Markdown rendering, dictate precise PDF scrolling dynamics, or rigidly adjust spatial EPUB margins and line spacing variables. |
| **Translation** | Regulate the translation engine firmly. Toggle universal translation capabilities, lock down distinct static Source and Target languages overriding automation, activate physical Google Lens augmentation overlays, or modify literal typography sizing and family rules regarding OCR readouts. |
| **Playback** | Oversee the player environment rigidly. Dictate the global Default Sort schema. Strictly configure automated Slide-show advancing timers, assign concurrent background music tracks, dictate behavior when combined video/image slideshows run to completion, establish the visual size for thumbprint lists, configure picture-in-picture activation, or trigger player warmups. Configure system-association behavior through playback toggles that enable or disable primary media player mode (default open intents + hardware media key hook) and shared media intake from Android Share sheets. |
| **Destinations** | Command the file transfer pipelines heavily. Globally sanction or revoke explicit Copy or Move permissions entirely. Set stringent auto-overwrite policies regarding file conflicts, toggle auto-advancement routines upon transfer completion, explicitly configure up to exactly 10 accessible destination fast-buttons, or sanction the operational undo failsafe system. |
| **Safe Mode** | Govern application risk deeply. Utilize master toggles to forcibly institute strict manual confirmation dialog barriers intercepting every single requested Delete or Move transaction globally. |
| **Trash** | Configure file permanence explicitly. Toggle thoroughly regarding whether deleted objects dynamically route to a recoverable Trash holding zone, or mandate hard, immediate permanent file deletion. Configure prompt requests surrounding trash operations precisely. |
| **Network / Sync** | Dictate connectivity performance completely. Empower automated background WorkManager sync routines prioritizing fresh directory trees, lock the sync timing interval, and deeply dictate the optimal parallel synchronous thread count for maximizing data flow securely. |
| **Cache** | Control graphical memory strictness. Definitively allot specific storage minimums and maximums (from minor 512 MB up to massive 16 GB pools) expressly dedicated solely for caching Image and Video thumbnail outputs. |
| **Density** | Maximize information density across the app. Enable **Compact Elements** mode to reduce the size of thumbnails, fonts, and paddings in the Main resource list, Browse file list, and Player command panels (applying a ~0.8× scaling factor). Perfect for small screens or users who prefer a high-density "professional" interface layout. |
| **Backup** | Manage holistic state persistence securely. Produce strict JSON payloads encapsulating your precise application state ready for local export, facilitate JSON data importing, or automate Backup and Restoration operations utilizing rigorous Google Drive integration. |

- **Landscape-adaptive dialogs**: All dialogs across the app include dedicated landscape layout variants (`layout-land/`). In landscape orientation every dialog is constrained to 320 dp maximum height and made scrollable, with action buttons and close controls pinned at the top of content so they are always immediately reachable regardless of how far the user has scrolled. Applies to all product flavors (Standard, Lite, Photos, Legacy).

## 19. Settings Search

- **Comprehensive full-text indexing**: Stop navigating dense nested configuration menus pointlessly. Swiftly execute a full-text query search that aggressively scans identically across every single settings entry, toggle, and section instantly.
- **Direct highlighting navigation**: Resolve settings adjustments securely and rapidly. Engaging a search result completely bypasses menus, rocketing you strictly to the accurate page while dynamically highlighting the specific sought parameter directly on screen.

## 20. Wear OS Companion App

- **SMB network access**: Liberate your wrist natively. Browse your massive home network storage drives and Windows Shares dynamically using strictly your Wear OS smart watch entirely independently.
- **Remote media list**: Discover your content swiftly specifically on a tiny screen. Accurately browse filtered, comprehensive lists of available media items directly on your smartwatch display.
- **Automated image slideshows**: Convert your watch into a vibrant digital frame. Trigger deeply integrated automated image cycling natively right on your wrist for ambient viewing.
- **Audio player integration**: Control your music library natively. Command the playback, pause, and skipping mechanism of your configured audio natively utilizing the watch interface.
- **Video player capabilities**: View media uniquely natively. Command and render playable video files explicitly directly over your smartwatch's display hardware.
- **Tailored setting configuration**: Regulate the companion strictly. Access deeply localized setting panels native to the smartwatch application enforcing discrete behavior distinct from the primary Android phone.
- **Appropriate permission flows**: Handle security protocols correctly natively. Ensure all deep file and network capability permissions orchestrate gracefully, clearly querying the exact permission dialogues over the wrist interface correctly.
- **One-tap network source sync**: Push all configured SMB/FTP/SFTP sources from your phone to your watch in a single tap via Wearable Data Layer. The watch displays an animated transfer screen and vibrates on completion with a detailed summary (added/updated counts).
- **On-watch source management**: Add SMB, FTP, and SFTP sources directly on the watch with a protocol-aware setup flow, and remove misconfigured sources from the watch list with a long press and confirmation dialog.
- **Polished on-watch controls and localization**: The watch companion now uses localized loading, empty-state, error, and retry labels, replaces emoji-only home shortcuts with native icons, and provides an on-watch slideshow interval stepper for quick tuning.
- **Reactive source list refresh**: Network sources on the watch now refresh automatically after sync, import, and delete operations, and the error screen retries in place instead of forcing navigation away.

## 21. Background & System Services

- **Automated Trash cleanup**: Maintain storage hygiene effortlessly without thinking. Scheduled WorkManager jobs execute periodically precisely identifying and permanently purging any trash files rigidly exceeding their configured retention timeframe.
- **Orphan temp file cleanup**: Recover dead storage seamlessly natively. Boot routines rigidly scan internal temporary repositories identifying and aggressively eliminating abandoned download artifacts and failed upload segments immediately.
- **Pending credential revocation**: Handle network security changes intelligently internally. The application's deferred processing system meticulously queues and permanently purges strictly expired network session instances, safeguarding system stability gracefully.
- **MediaStore sync**: Secure device-wide uniformity inherently. Successive to every file deletion, copy, or move operation, the system rigorously pings central Android MediaStore endpoints, heavily assuring system Gallery and similar third-party apps remain completely accurate visually.
- **Playback DB persistence**: Cement resume operations permanently locally. Database transactions record and rigorously cache definitive progress milestones regarding Audio, Video, and EPUB files strictly.
- **Thumbnail cache**: Rapidly reduce re-rendering friction aggressively. Pre-rendered Glide thumbnails permanently route into customized scalable hardware pools sized mathematically up to 16 Gigabytes, ensuring subsequent loads function instantaneously securely.
- **File metadata cache**: Discard repetitive read iterations natively. Crucial embedded file intelligence, involving heavy EXIF image data or layered ID3 tags, perfectly replicate securely into rapid flat databases, systematically sidestepping excruciating redundant read requests on repeated viewing entirely.
- **Credential audit**: Manage network keys securely definitively. The audit mechanism logs precise timestamp metrics corresponding to remote login validations, automatically drawing specific attention regarding completely stagnant, unused password profiles ready for strict manual deletion explicitly.
- **Default player system hooks**: Integrate with Android intent routing when user-enabled. Runtime component toggles control ACTION_VIEW aliases (audio/video/image/document groups), ACTION_SEND aliases for share-sheet intake, and media-button wake-up wiring through the audio playback service path.
- **Standalone player file operations**: Perform file actions directly when opening media from external apps via "Open with" or Share. Delete the file (with confirmation), share it to other apps, toggle favorite status, or navigate to its folder in the main FMS browser — all from the standalone player toolbar.
- **Rename file from standalone player**: Rename files directly from the standalone "Open with" player, including audio files (MP3, FLAC, OGG, WAV, M4A). Button is shown only for SAF documents with `FLAG_SUPPORTS_RENAME` and MediaStore files with write access. For audio, playback continues uninterrupted after rename — the ExoPlayer media source is hot-swapped to the new URI without restarting the service.
- **Standalone player lifecycle & reliability**: Playback pauses automatically when leaving the app and resumes on return; screen stays awake during video and audio playback; playback errors (codec not supported, file not found, network failure) show a clear localized message instead of silently stopping.
- **Standalone player Picture-in-Picture (Android 12+)**: Press Home while watching a video to keep it in a floating overlay window with play/pause remote actions. Video continues playing without interruption. PiP button visible in player controls on supported devices.
- **Standalone player audio focus management**: Video and audio automatically pause on incoming phone calls, voice assistant activation, and other transient interruptions; permanently stops on call answer. Audio focus is released on exit so other apps (e.g. Spotify) resume immediately.
- **Standalone player video UX parity**: Command buttons (close, share, delete, open-in-FMS) are fully visible in both portrait and landscape on all devices — padded away from status bar and navigation bar. Screen rotates with physical device sensor even when OS auto-rotate is off.
- **Standalone player video controls**: The standalone `Open with` video player now mirrors the main player with the same bottom-bar `Control` dialog, the same HUE + GPU brightness pipeline, the same reset actions, and the same touch gestures. This parity works in both portrait and landscape because both orientations use the same custom ExoPlayer controller layout.

## 22. Scheduled File Operations

- **Scheduled copy / move / delete**: Automate recurring file management tasks by creating scheduled operations that run in the background at a set time and repeat on a chosen interval (minimum 15 minutes). Ideal for automatically moving camera photos to a NAS every night or clearing a downloads folder daily.
- **Multi-flag file-type filter**: Choose any combination of file categories — All files (including non-media), Images, Audio, Video, and Documents — for each scheduled operation. Selecting "All files" processes every file in the resource (respecting the scan-subdirectories setting), while media flags filter by extension. Multiple categories can be active simultaneously.
- **Time window filter**: Further restrict which files are processed: all files, only files created since the last run, files from the last hour, or files from the last day. This prevents reprocessing files that were already handled.
- **Remote and cloud destinations**: Scheduled operations fully support all resource types — local folders, SMB/NAS, FTP, SFTP, Google Drive, OneDrive, and Dropbox — as both source and destination. Target reachability is verified before any file is touched; if the destination is offline the run is logged as an error and retried on the next schedule cycle.
- **Safe atomic MOVE**: Moving files is performed per-file: the source file is copied first, the copy is verified, and only then the original is deleted. A failed copy never removes the source.
- **Enable / disable per operation**: Each scheduled job has its own on/off toggle in the Operations settings tab. Disabling a job cancels its pending WorkManager task without deleting the configuration.
- **Run now**: Trigger any scheduled operation immediately from the settings table without waiting for its next scheduled time.
- **Operations log**: Every run writes a per-file log entry to a persistent on-device log file (rotated at 1 MB). Entries include timestamp, operation type, source, destination, file name, and success or error status. The log is accessible from the Operations settings screen and can be cleared at any time.
- **Error badge**: If the last run of an operation produced any error, a warning badge (⚠) is shown next to that operation in the table, prompting review of the log.
- **Silent mode**: Optionally suppress all notifications for a scheduled operation so background tasks do not disturb the user.
- **Boot persistence**: Scheduled operations are automatically rescheduled after a device reboot so your automation survives restarts without manual intervention.
- **Battery optimization prompt**: On first use, the app offers to disable battery optimization for itself, ensuring operations run reliably on OEM devices (Xiaomi, Huawei, Samsung) that aggressively kill background processes. The same setting is accessible at any time from the General settings screen.

## 23. Apps FMS Can Replace — Competitor Comparison

FastMediaSorter consolidates functionality that typically requires 5–10 separate apps into a single, unified tool. Below is a category-by-category breakdown showing which popular apps FMS can replace and what advantages it offers over each.

### Photo Gallery

**Popular apps:** Google Photos, Samsung Gallery, Simple Gallery Pro, F-Stop Gallery, Piktures, QuickPic, 1Gallery

| Capability | Typical gallery app | FMS |
|---|---|---|
| Browse & view photos | Yes | Yes |
| Grid / list display with thumbnails | Yes | Yes |
| EXIF metadata display | Some | Yes |
| Slideshow | Basic (local only) | Advanced (any source + background music) |
| NAS / SMB browsing | No | Yes (native, with connection pooling) |
| Cloud browsing (Drive, Dropbox, OneDrive) | Google Photos only (own cloud) | Yes (all three + NAS + FTP/SFTP) |
| File operations (copy/move/rename/delete) | Limited | Full cross-protocol |
| PIN protection per folder | Rare | Yes |
| Scheduled auto-transfer | No | Yes |

**Bottom line:** Gallery apps handle local photos well but ignore network storage entirely. FMS provides the same visual browsing experience plus full NAS/cloud access, scheduled automation, and cross-protocol file operations.

### Video Player

**Popular apps:** VLC for Android, MX Player, mpv-android, Nova Video Player, Just (Video) Player

| Capability | Typical video player | FMS |
|---|---|---|
| Codec support | Excellent (VLC/MX) | ExoPlayer (Media3) — broad format coverage |
| Background playback | Some | Yes |
| Subtitle support | Yes | Yes (with app font styling) |
| Gesture controls (brightness, volume, seek) | Yes (MX/VLC) | Yes |
| Picture-in-Picture | Some | Yes (Android 12+) |
| Sleep timer | Rare | Yes |
| Play from SMB/NAS | VLC: basic; MX: plugin | Native, with pooling + 24 threads |
| Play from SFTP/FTP | VLC: basic | Native |
| Play from Google Drive / Dropbox / OneDrive | No (must download) | Yes (direct streaming) |
| File management during playback | No | Yes (copy/move/delete/rename/share) |
| Duplicate finder | No | Yes (SHA-256) |
| Scheduled file operations | No | Yes |

**Bottom line:** VLC and MX Player are excellent standalone players, but they treat network files as a secondary feature and offer zero file management. FMS combines a capable player with full NAS/cloud integration and file operations — no more switching between a player and a file manager.

### Audio / Music Player

**Popular apps:** Poweramp, Musicolet, BlackPlayer, Pulsar Music Player, Vanilla Music, Oto Music

| Capability | Typical music player | FMS |
|---|---|---|
| Local playback | Yes | Yes |
| Background playback | Yes | Yes |
| Sleep timer | Some | Yes |
| Lock screen / notification controls | Yes | Yes |
| Play from SMB/NAS | No (must copy locally first) | Yes (direct streaming) |
| Play from SFTP/FTP | No | Yes |
| Play from cloud (Drive/Dropbox/OneDrive) | No | Yes |
| Inline mini-player in file browser | No (separate app) | Yes |
| File copy/move while browsing | No | Yes (cross-protocol) |
| Equalizer | Yes (Poweramp) | Relies on system EQ |
| Tag editor | Some | No |

**Bottom line:** Dedicated music players offer richer audio features (EQ, gapless, tag editing), but none of them can stream directly from a NAS or cloud drive. If your music library lives on a home server or in cloud storage, FMS eliminates the need to sync files locally first.

### File Manager (with network support)

**Popular apps:** Solid Explorer, MiXplorer, X-plore File Manager, Total Commander, ES File Explorer, Material Files

| Capability | Typical file manager | FMS |
|---|---|---|
| Local file browsing | Yes | Yes |
| SMB / NAS support | Yes (Solid, MiX, X-plore) | Yes (SMBJ, connection pooling, 24 threads) |
| FTP / SFTP | Yes | Yes |
| Cloud (Drive/Dropbox/OneDrive) | Yes (as plugins or built-in) | Yes (built-in, no plugins) |
| Cross-protocol copy/move | Yes | Yes |
| Built-in video player | Basic (system intent) | Full ExoPlayer with gestures, PiP, subs |
| Built-in audio player | Basic or none | Full with background playback, sleep timer |
| Built-in image viewer | Basic | Full with OCR + AR translation overlay |
| Built-in PDF reader | No (opens external) | Yes (themes, translation, OCR) |
| Built-in EPUB reader | No (opens external) | Yes (themes, translation) |
| Duplicate finder (SHA-256) | Rare (X-plore: CRC) | Yes (cross-source, byte-level) |
| Scheduled file operations | No | Yes (cron-style, background) |
| Wear OS companion | No | Yes |
| PIN per resource | Solid: yes; others: no | Yes |

**Bottom line:** Power-user file managers like Solid Explorer and MiXplorer are the closest competitors. They handle file operations and network well, but delegate all media playback and document reading to external apps. FMS keeps everything in-house — you never leave the app to play a video, read a PDF, or view a photo.

### NAS / SMB Dedicated Client

**Popular apps:** Cx File Explorer, FE File Explorer, File Manager +, Owlfiles, nPlayer

| Capability | Typical NAS client | FMS |
|---|---|---|
| SMB browsing | Yes | Yes |
| FTP / SFTP | Some | Yes |
| Cloud integration | Some (FE, Cx) | Yes (Drive, Dropbox, OneDrive) |
| Connection pooling | No | Yes |
| Multi-threaded transfer | No | Yes (up to 24 threads) |
| File list caching | No | Yes (Room DB) |
| Credential vault with audit | No | Yes (encrypted + last-used tracking) |
| Built-in media player | Basic | Full (ExoPlayer, background, PiP) |
| Scheduled automation | No | Yes |
| Wear OS | No | Yes |

**Bottom line:** Dedicated NAS clients provide basic browse-and-download functionality but lack high-performance transfer, credential management, and media playback. FMS is built NAS-first with connection pooling and parallel threads, making it noticeably faster on large transfers.

### Cloud Storage Client

**Popular apps:** Google Drive (official), Dropbox (official), OneDrive (official)

| Capability | Official cloud apps | FMS |
|---|---|---|
| Browse own cloud | Yes (one cloud per app) | Yes (all three in one interface) |
| Cross-cloud transfer | No (must download → re-upload) | Yes (direct, e.g., Drive → Dropbox) |
| NAS integration | No | Yes (cloud ↔ NAS transfers) |
| FTP/SFTP integration | No | Yes |
| Built-in media player | Basic preview | Full ExoPlayer |
| PDF/EPUB reader | Basic preview (Drive) | Full reader with themes + OCR |
| Scheduled sync/backup | Drive: limited auto-backup | Full cron-style operations |
| PIN protection | No | Yes (per resource) |

**Bottom line:** Each official cloud app only accesses its own service. FMS unifies Google Drive, Dropbox, and OneDrive into a single browsing interface and enables direct cross-cloud and cloud-to-NAS transfers without downloading files locally.

### PDF / EPUB Reader

**Popular apps:** Adobe Acrobat Reader, Moon+ Reader, ReadEra, Librera Reader, Google PDF Viewer

| Capability | Typical reader | FMS |
|---|---|---|
| PDF rendering | Yes | Yes |
| EPUB rendering | Moon+, ReadEra, Librera | Yes |
| Reading themes (light/dark/sepia) | Yes | Yes |
| Open from local storage | Yes | Yes |
| Open directly from NAS (SMB/SFTP/FTP) | No (must download first) | Yes (streams from source) |
| Open from cloud (Drive/Dropbox/OneDrive) | Limited (Drive only via some) | Yes (all clouds) |
| OCR overlay | No (separate app needed) | Yes (built-in, offline-capable) |
| AR translation overlay | No | Yes ("Google Lens"-style) |
| In-app text translation | Some (Moon+) | Yes |
| File management (copy/move/delete) | No | Yes |

**Bottom line:** Dedicated readers offer a polished reading experience but require files to be downloaded first. FMS reads PDFs and EPUBs directly from any network or cloud source and adds OCR + translation on top — no extra apps needed.

### FTP / SFTP Client

**Popular apps:** AndFTP, Admin Hands, Termius (file transfer), Turbo FTP

| Capability | Typical FTP client | FMS |
|---|---|---|
| FTP connection | Yes | Yes |
| SFTP connection | Yes | Yes |
| SMB / NAS | No (FTP/SFTP only) | Yes |
| Cloud storage | No | Yes (Drive, Dropbox, OneDrive) |
| Media playback | No (download → open externally) | Yes (stream directly) |
| Batch operations | Basic | Yes (cross-protocol) |
| Scheduled transfers | No | Yes (cron-style) |
| Credential vault | Basic | Encrypted + audit trail |

**Bottom line:** FTP/SFTP clients are single-purpose tools. FMS handles FTP and SFTP alongside SMB and three cloud providers, with inline media playback and scheduled automation on top.

### OCR & Translation

**Popular apps:** Google Lens, Google Translate (camera), Text Scanner (OCR), Microsoft Lens

| Capability | Typical OCR app | FMS |
|---|---|---|
| OCR from camera | Yes | No (file-based only) |
| OCR from image file | Google Lens: yes | Yes (built-in) |
| OCR from PDF | No (separate app) | Yes (directly in PDF viewer) |
| AR translation overlay | Google Lens: yes | Yes (same "Lens-style" UX) |
| Works on files from NAS / cloud | No (must download first) | Yes (processes in-place) |
| Offline OCR | Some | Yes (ML Kit on-device) |
| Integrated with file browser | No (standalone app) | Yes (accessible from any viewer) |

**Bottom line:** Google Lens is powerful but operates as a standalone camera app. FMS embeds the same OCR + AR translation concept directly into its image and PDF viewers, working on files from any source without downloading them first.

### Duplicate Finder

**Popular apps:** Files by Google, Duplicate Files Fixer, SD Maid, Duplicate File Remover

| Capability | Typical duplicate finder | FMS |
|---|---|---|
| Find duplicates on local storage | Yes | Yes |
| Find duplicates on NAS (SMB) | No | Yes |
| Find duplicates on FTP / SFTP | No | Yes |
| Find duplicates on cloud | No | Yes |
| Cross-source matching (e.g., local vs. NAS) | No | Yes |
| Matching algorithm | Usually file name or size | SHA-256 byte-for-byte |
| Integrated file operations (delete/move) | Some | Yes (full cross-protocol) |

**Bottom line:** Most duplicate finders only scan local storage and use weak matching (name/size). FMS uses SHA-256 checksums and can compare files across local, NAS, and cloud sources — finding true duplicates regardless of where they are stored.

### Slideshow / Digital Photo Frame

**Popular apps:** Fotoo, Photo Slides, Frameo, Simple Gallery (slideshow mode)

| Capability | Typical slideshow app | FMS |
|---|---|---|
| Local photo slideshow | Yes | Yes |
| Slideshow from NAS / SMB | Fotoo: yes (limited) | Yes (native, fast) |
| Slideshow from cloud | No | Yes (Drive, Dropbox, OneDrive) |
| Background music from any source | No (local only or none) | Yes (any connected source) |
| Configurable transitions & timing | Some | Yes |
| Double-duty as file manager | No | Yes |

**Bottom line:** FMS turns any Android device into a digital photo frame that pulls images from local storage, NAS, or cloud — with background music from any source. No need for a dedicated slideshow app.

---

### Summary: What FMS Replaces

| App category | Example apps you can uninstall | FMS equivalent |
|---|---|---|
| Photo gallery | Google Photos, Simple Gallery, F-Stop, Piktures | Media Browsing + Image Viewer + Slideshow |
| Video player | VLC, MX Player, mpv, Nova | Video Player (ExoPlayer, PiP, gestures) |
| Music player | Musicolet, Pulsar, BlackPlayer | Audio Player (background, sleep timer) |
| File manager | Solid Explorer, MiXplorer, X-plore | Full file ops + all protocols |
| NAS client | Cx File Explorer, FE File Explorer, Owlfiles | Native SMB/FTP/SFTP with pooling |
| Cloud client | Drive / Dropbox / OneDrive official apps | All three clouds unified |
| PDF reader | Adobe Reader, ReadEra | PDF Viewer + OCR + translation |
| EPUB reader | Moon+ Reader, Librera | EPUB Viewer + themes + translation |
| FTP client | AndFTP, Turbo FTP | FTP/SFTP built-in |
| OCR tool | Google Lens, Text Scanner | OCR + AR translation in viewers |
| Duplicate finder | Files by Google, Duplicate Files Fixer | SHA-256 cross-source finder |
| Slideshow / frame | Fotoo, Photo Slides | Slideshow + background music from any source |

**One app instead of twelve.** FMS does not aim to be the absolute best in every single category — a dedicated equalizer app will always have more audio DSP options, and a dedicated photo editor will always have more filters. But for users who manage media across local storage, NAS, and cloud, FMS eliminates the constant app-switching and provides a consistent, unified experience with a depth of integration no single-purpose app can match.
