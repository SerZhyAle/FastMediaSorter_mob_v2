---
name: ocr-hard-gated-at-3gb-ram
description: The OCR enable switch is programmatically disabled below 3 GB device RAM, so the default emulator can never reach Tesseract - no menu item, no init, no log line
metadata:
  type: project
---

The app refuses OCR on a device with less than 3 GB of RAM, and it refuses it *before* any engine code
runs. The settings row `rowEnableOcr` arrives with `enabled="false"` and its switch with
`clickable="false"`; `tvOcrSummary` beneath it reads "OCR недоступен на этом устройстве. Недостаточно ОЗУ
устройства (API: NN). Требуется API 26+ и 3 ГБ+ ОЗУ." Tapping the switch does nothing - the UI dump before
and after is byte-identical.

Measured 2026-08-21 on `emulator-5554`: `/proc/meminfo` reports `MemTotal: 2532432 kB` (2.53 GB) against
the 3 GB floor. The API half of the requirement was satisfied at 35; only the RAM half failed.

**Why:** this looks like several other failures and is none of them. On that emulator the OCR action is
absent from every menu, the engine never initialises, and logcat carries **zero** Tesseract-tagged lines -
which reads exactly like a missing native library, a wrong flavor, a wrong file type, or a broken probe. It
is none of those. Three device passes and two wrong hypotheses (wrong flavor, then `enableOcr` merely
defaulting off) were spent before the summary line was read.

**How to apply:**

- Before planning any on-device OCR work, check the target's RAM. Below 3 GB the path is unreachable and no
  amount of navigation, flavor choice or file type will change that.
- Also true and separately confusing: `AppSettings.enableOcr` defaults to `false` even on a capable device,
  so the OCR menu item is absent until the setting is turned on. `CommandPanelLayoutPlanner` adds it only
  `if (isPdf && state.enableOcr)` / `if (isImage && state.enableOcr)`.
- A PDF that already carries a text layer is useless as an OCR trigger: the `TXT` button
  (`btnSelectTextPdf`) extracts the embedded text and returns real output without touching the recogniser.
  `test_doc_scanned.pdf` behaves this way. Use an image from `Download/FastMediaSorter_Test/OCR/`.
- The language data is not the obstacle - `files/tesseract/tessdata/eng.traineddata` is already on device.

**The remedy is local - do NOT conclude "this needs the owner's phone" (2026-08-21).** The 3 GB floor is
a property of the *running* AVD, not of emulators. Two AVDs already defined on this machine clear it:

    ~/.android/avd/Pixel_6.avd/config.ini            hw.ramSize=4096
    ~/.android/avd/Pixel_10_Pro_Fold.avd/config.ini  hw.ramSize=8192

`Pixel_9` - the one usually running as `emulator-5554` - is `hw.ramSize=2048`, which is why OCR is dead
there. Boot `Pixel_6` instead and the whole OCR chain becomes drainable with no owner hardware involved.
Verify the threshold in code rather than from this note: `core/util/DeviceCapabilities.kt` holds
`MIN_OCR_RAM_GB = 3.0` and compares it against `memoryInfo.totalMem`.

Check free host RAM before booting a second AVD alongside the first - a 4 GB guest plus overhead wants
~5-6 GB, and a concurrent gradle build in this repo is itself memory-hungry.
