---
layout: default
title: "⚠️ Program Limitations"
permalink: /docs/LIMITATIONS.html
---

# ⚠️ Program Limitations

This document outlines the current technical constraints, functional limitations, and performance considerations of FastMediaSorter v2.

---

## 📱 System Requirements
- **Android Version:** Android 9.0 (API 28) or newer is required.
- **Hardware:** Performance may vary based on device CPU and RAM, especially for large collections (5000+ files).
- **Storage Access:** Full functionality requires the "All Files Access" (MANAGE_EXTERNAL_STORAGE) permission on newer Android versions.

---

## 🌐 Network & Cloud Limitations

### Protocol Support
- **SMB:** Best performance on local Wi-Fi. Supports SMB v2/v3 (v1 is deprecated but may work in some configurations).
- **Cloud (Google Drive, OneDrive, Dropbox):** Files are streamed/cached on demand. There is no background sync for offline use.
- **Undo Operation:** The "Undo" feature for move/delete operations is **NOT available** for network and cloud resources. Once deleted, they are gone (unless the server provides its own trash bin).

### Internet Requirement
- **Local Files:** Works completely offline.
- **Network/Cloud:** Requires an active connection.
- **OCR/Translation:** Requires a one-time internet connection to download language models (~15-50 MB depending on the language).

---

## 📂 File & Media Support

### Formats & Codecs
- **Images:** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF, AVIF. **RAW formats (CR2, NEF, ARW, DNG, etc.) are NOT supported.**
- **Videos:** MP4, MKV, MOV, WMV, FLV, WEBM, TS, M2TS, VOB, OGV, DIVX, MTS and more. If a file uses an unsupported codec (e.g., HEVC on older devices), you may see a black screen. **Solution:** Use the "Open in External Player" option.
- **Text:** Large text files (>100MB) may load slowly in the internal viewer.
- **EPUB:** Supports DRM-free EPUB files only. Encrypted or DRM-protected books will not open.

### Large Collections
- **Performance:** Folders with 5000+ files may experience scrolling lag while thumbnails are generating.
- **Thumbnail Cache:** Limited to 2GB by default (configurable in settings).

---

## 🛠️ Functional Constraints

### Favorites
- **Local Only:** Favorites are stored in a local database on your device. They are not synced to the actual files or across different devices.

### Trash System
- **Soft Delete:** Local files are moved to a hidden `.trash/` folder. This takes up space until you manually select "Empty Trash" in settings.

### Wear OS App
- **Limited Scope:** The Wear OS companion app currently supports **local media only**. Network and cloud resources are not accessible from the watch.
- **Performance:** Image and video processing on watches is significantly slower than on phones.

### VR Edition

- **Hardware:** Runs only on Meta Quest 2/3/Pro and Android XR headsets (`arm64-v8a` + OpenXR runtime). On a phone without an XR runtime, the app shows a fallback screen and does not start playback.
- **Distribution:** Two channels - Meta Horizon Store / Google Play (`vr` flavor) and ADB sideload (`vrUnlicensed` flavor). They share the same package ID, so only one can be installed at a time.
- **No Wear OS Companion:** headsets have no paired watch.
- See [VR Edition](VR_EDITION.md) for the full constraint list.

---

## 🧩 AI & OCR Models
- **Engines:** The app uses a hybrid engine (ML Kit + Tesseract).
- **Latencies:** Tesseract (for Cyrillic) initialization takes 1-2 seconds longer than ML Kit.
- **Accuracy:** Recognition quality depends heavily on image clarity and lighting.

---

**Last Update:** February 2026
**Version:** v2.26.0224
