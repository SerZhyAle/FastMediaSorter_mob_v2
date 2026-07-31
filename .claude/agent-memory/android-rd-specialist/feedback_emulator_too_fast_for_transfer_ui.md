---
name: emulator-too-fast-for-transfer-ui
description: Local file copies finish in under 2 s on the AVD - backgrounded-transfer UI cannot be device-tested there
metadata:
  type: feedback
---

Do not try to verify UI that is only visible *during* a local copy/move on the emulator. Use a real device with a network source (SMB/FTP/cloud), or accept a partial verdict and say so.

**Why:** measured 2026-07-29 on emulator-5554 (API 36) while testing S1227's backgrounded-transfer strip - 377 MB copied in about 6 s, and a 20-file / 700 MB batch finished in under 2 s. Three attempts to press "В фон" and photograph the strip all lost the race, including one with the tap scripted at t+2 s. The window simply does not exist there.

Two fixture facts that block the obvious workarounds:

- The seeded `Загрузки` resource is capped at 20 indexed files, and its listing is MediaStore-backed, so files created from the shell (`cp` in `/sdcard/...`) never appear no matter how many refreshes you do.
- Making the job bigger does not help much - throughput is roughly 70-140 MB/s, so even a gigabyte buys only seconds.

**How to apply:** when a ticket's observable behaviour lives inside a transfer, plan for a real device from the start. What the emulator *can* still prove is worth harvesting anyway: layout inflation in portrait and landscape, no crash, and any probe tag on the changed code path (a `Timber.d` in an inset/visibility helper fires on every visibility change and proves the new view exists in every layout variant). Report exactly which steps stayed unproven instead of implying a full pass.

Related trap: `adb.ps1 shot` can start returning 0-byte PNGs mid-session; read settings state straight out of `files/datastore/settings.preferences_pb` instead of screenshotting the UI.
