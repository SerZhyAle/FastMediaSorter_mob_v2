---
name: setup-test-media
description: How to seed a structured test-media tree onto connected devices/emulators for pre-release manual + device testing
type: reference
---

`scripts/utils/setup_test_media.ps1` provisions every connected device/emulator with a
structured media tree for manual + device testing. Pairs with the [[maestro-device-test-engine]]
S0420 flow: run this first to give a flow real content to drive.

Run: `pwsh -NoProfile -File scripts/utils/setup_test_media.ps1` (no args; runs on **all**
connected devices at once).

Key non-obvious facts:
- **Source media lives at `c:\Common\test_media`** - a machine-local path **outside the repo**
  (not version-controlled). Missing source files are SKIPped with a warning, not fatal.
- adb auto-resolves to `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`, else PATH.
- It **wipes and recreates** `/sdcard/Download/FastMediaSorter_Test/` each run (DCIM, Audio,
  Docs, OCR, Ops/src+dst, Edge, Empty, S0029, S0048) + `/sdcard/Android/media/com.test.prerelease`.
- Layout maps 1:1 to the blocks in `dev/PRE_RELEASE_MANUAL_TESTS.md`; each folder is meant to be
  added as a `LOCAL - <name>` resource in the app.
- Creates `.lrc` lyrics + `.txt` inline on device, and triggers a MediaStore scan at the end.
- `/sdcard/Android/media/...` push fails on Android 11+ (restriction) - the script warns and
  suggests using a real Telegram/WhatsApp folder for Block 1.8.
