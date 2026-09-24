# Documentation Feature Area Assignments (S2945 - S2967)

This document establishes the canonical assignment of all 32 feature inventory areas across the 22 thematic documentation tickets (S2946 - S2967).

## Summary Matrix

| Ticket | Topic Title | Feature Areas Covered | Partition Rules |
|--------|-------------|-----------------------|-----------------|
| **S2946** | Getting Started & First Steps | Setup & Onboarding, Permissions, Main Screen | Onboarding wizard, initial permissions, workspace introduction |
| **S2947** | App Flavors & System Capabilities | Extensions & On-demand, Localization | 7 flavor matrix, downloadable modules, 13 languages |
| **S2948** | Media Browsing & Sorting | Media Browsing | Grid/list, metadata sorting, multi-select, cache |
| **S2949** | Sources, Destinations & File Operations | Sources & Storage, Destinations, File Operations | Storage providers, target folders, copy/move/delete, encryption, renaming |
| **S2950** | Network Folders & Cloud Storage | Network & Cloud | SMB/Samba, SFTP/FTP, Google Drive, Dropbox, OneDrive, WebDAV, offline sync |
| **S2951** | Video & Media Player | Video Player, Media Player, Broadcast | Video playback, multi-audio/subtitles, PiP, DLNA/Cast |
| **S2952** | Images, Audio & Slideshow | Image & GIF Viewer, Audio Player, Slideshow | Photo viewer, GIF zoom, Slideshow transitions, Music player & playlists |
| **S2953** | Documents & Text Editor | Documents, Text Editor | PDF/EPUB reader, Office formats, Markdown/Text editor |
| **S2954** | Streams Channel Catalog | Streams (Catalog Partition) | IPTV catalog, M3U import, EPG, favorite streams |
| **S2955** | Streams Playback | Streams (Playback Partition) | Live HLS/DASH playback, background radio, weak-connection behavior, shortcuts, widget and streams panel, Chromecast, watch, VR and Live Broadcast reception |
| **S2956** | Camera & Screen Recording | Camera, Screen Capture | Quick snaps, screen recorder with audio, screenshot editor |
| **S2957** | OCR, Translation, Drawing & Sharing | OCR & Translation, Drawing & Annotations, Sharing, Send-to menu, Resource sharing (SFTP QR) | Offline OCR, on-device translation overlays, drawing editor, Send to.. menu and its receivers |
| **S2958** | Launcher: Desktop & Wallpapers | Launcher (Desktop Partition) | Grid layout, icon packs, wallpaper engine, desktop folders |
| **S2959** | Launcher: Gadgets & Widgets | Launcher (Widgets Partition), Widgets | Desktop gadgets, gadget placement, home-screen widgets |
| **S2960** | Launcher: Taskbar, Menus & Gestures | Launcher (Taskbar Partition) | Dock/taskbar, navigation gestures, desktop context menus |
| **S2961** | Programs, Statistics & Diagnostics | Programs & Tools, Usage Statistics, Diagnostics | Built-in programs (flashlights, mirror, SOS, calculator, stopwatch, mini-game), usage statistics, System information & the debug log; Live Broadcast is described on the S2951 casting page |
| **S2962** | Settings & Navigation | Settings & Navigation | Global settings hierarchy, appearance, sorting/playback defaults, privacy |
| **S2963** | General Features, Backup & TV | General | Backup/restore zip, Keyboard & D-Pad navigation, Android TV, Foldables |
| **S2964** | Watch: Setup, Companion & Sync | Wear OS (Setup & Sync Partition) | Watch installation, Bluetooth companion pairing, Tile complications |
| **S2965** | Watch: Media, Streams & Files | Wear OS (Media & Files Partition) | Standalone watch audio, wrist radio streaming, watch file manager |
| **S2966** | Watch: Mini-Apps & Health | Wear OS (Mini-Apps & Health Partition) | Wearable utilities, timers, sensor metrics |
| **S2967** | VR & OpenXR Media Experience | VR & OpenXR | Meta Quest / OpenXR setup, 3D/360 spatial cinema, VR passthrough |

## Partitioning Guidelines for Shared Areas

1. **Streams Area (Streams)**:
   - S2954 owns discovery, channels, M3U loading, and EPG metadata.
   - S2955 owns playback, buffering and weak-connection behavior, one-tap starts (shortcuts, widget, streams panel), and taking a stream to a TV, a watch or a VR headset, including Live Broadcast reception.

2. **Launcher Area (Launcher & Widgets)**:
   - S2958 owns the desktop canvas, icons, and wallpapers.
   - S2959 owns the desktop gadgets, gadget placement and sizing, and the home-screen widgets.
   - S2960 owns the taskbar, the All apps list, gestures, hotkeys, and context menus.

3. **Wear OS Area (Wear OS)**:
   - S2964 owns pairing, sync protocols, and watch face tiles.
   - S2965 owns media playback, streaming, and file browsing on the watch.
   - S2966 owns utility mini-apps, health data display, and standalone watch tools.