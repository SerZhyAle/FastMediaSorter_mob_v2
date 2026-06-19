---
name: avd-mediastore-not-indexed
description: On the dev AVD the seeded test media exists on disk but is not MediaStore-indexed, so virtual resources show 0 files and save/playback device tests go inconclusive
type: feedback
---

On the standard dev emulator (emulator-5554), the on-disk test tree under `/sdcard/Download/FastMediaSorter_Test/...` (videos, images, audio) is present but NOT indexed in MediaStore: the app's virtual resources (`virtual://all_video`, `all_images`, `all_audio`, `camera_photos`, `recent`) all report `0 файлов`.

**Why:** `/spec-test-device` runs for save-to-Downloads (S0528) and duplicate-group (S0525) flows went INCONCLUSIVE largely because no playable/duplicate media is reachable in-app - the frame-capture, duplicate-scan, and video-playback scenarios cannot be driven without a populated resource. `setup_test_media.ps1` seeds files but does not register a folder resource or force a MediaStore scan.

**How to apply:** before driving any device scenario that needs in-app media (frame save, duplicate finder, slideshow, playback), first make media reachable - either register the test folder as a Local resource in-app, or trigger a MediaStore scan for the seeded files - otherwise expect `0 файлов` and an inconclusive run. For behavioural acceptance that also needs network links or a deliberately-unreachable resource (S0522 fallback), treat as out-of-scope on the emulator and defer to a real-device manual test / `/spec-sweep`. See also [[avd_device_sweep_gotchas]] and [[bottomsheet_menu_untappable_emulator]].
