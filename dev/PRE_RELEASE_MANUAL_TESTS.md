# Pre-Release Manual Tests

> **Automated sweep:** `/spec-prerelease` runs the emulator-scriptable subset of this plan as one
> gated PASS/FAIL pass (clean install, seed, resource reachability, settings, cold-start perf,
> log verdict). This document stays the **source of intent** - the full manual matrix below is
> what a human runs on real devices before a release; the skill is the fast smoke on top of it.

## Setup

Seed the structured test-media tree on every connected device:

```powershell
pwsh -NoProfile -File scripts/utils/setup_test_media.ps1
```

Source lives outside the repo at `c:\Common\test_media`; device target is
`/sdcard/Download/FastMediaSorter_Test`. Add the seeded folders as LOCAL resources in the app
(`DCIM`, `Audio`, `Docs`, `OCR`, `Ops/src`, `Ops/dst`, `Empty`, `Edge`, `S0029`, `S0048`) plus
the `Android/media/com.test.prerelease` protected path.

## Block 1 - Browse, filter, sort

- 1.1 Subfolder navigation - enter `DCIM/_subfolder`, the three `sub_photo_*` images list; back returns to parent.
- 1.2 Filter - filter `IMG` shows exactly the five `IMG_001..IMG_005`; non-IMG photos are hidden. Filter `>1MB` shows `photo_large*`, `video_sample`, `video_large`.
- 1.3 Copy - copy from `Ops/src` to `Ops/dst`; source kept, destination gains the file.
- 1.4 Move - move `IMG_move_*` from `Ops/src`; source loses it, destination gains it.
- 1.5 Rename - rename `ops_rename_me.png`; new name shows, no duplicate.
- 1.6 Delete (soft) - delete `ops_delete_soft.png`; lands in trash when trash is on.
- 1.7 Undo - delete `ops_undo_me.png`, undo, file restored.
- 1.8 Protected path - browse `Android/media/com.test.prerelease` (or a real Telegram/WhatsApp folder when the emulator blocks the write); files list without crash.
- 1.9 Overwrite - copy `ops_overwrite_A.png` from `Ops/src` to `Ops/dst` where it already exists; overwrite dialog appears.

## Block 2 - Player

- 2.5 Video - play `video_sample.mp4` and `video_large.mp4`; render, seek, no crash.
- 2.6 Audio + lyrics - play `Audio/frank_sinatra_My_way.mp3`; the sibling `.lrc` lyrics show synced. Also FLAC (`test_audio_flac.flac`).
- 2.8 Documents - open `Docs/test_doc_romcom.pdf`, `test_doc_scanned.pdf`, `test_book.epub`, `readme.txt`; each renders in its viewer.

## Block 3 - OCR

- Open `OCR/ocr_screenshot_text.png` and `ocr_data_table.jpg`; OCR extracts readable text (when the OCR engine is installed; expected fallback otherwise).

## Block 15 - Edge cases

- 15.3 No extension - `Edge/file_without_extension` appears in Browse and opens a fallback view in Player, no crash.
- 15.4 Empty folder - `Empty/` shows the empty-state placeholder.
- 15.5 Large video - `Edge/video_large_200mb.mkv` opens without OOM.

## S0029 - Resume position

- R1 long.mp4 played to end -> STATE_ENDED, resume offers restart.
- R2 long.mp4 stopped near end (~5 s left) -> resume near end.
- R3 long.mp4 stopped at 50% -> resume mid.
- R4 short.mp4 stopped near end (~5%) -> resume offset.

## S0047 / S0048 - Info dialog metadata

- I1 `test.flac` - artist/album/year tags + embedded cover render.
- I2 `test_cbr.mp3` - CBR, ID3v2, LAME header, exact bitrate shown.
- I3 `test_vbr.mp3` - VBR, no Xing/LAME header, bitrate field hidden.

## Resources

Exercise all resource classes from `app_v2/src/main/res/xml/sza_resources.xml`: LOCAL, SMB, SFTP,
FTP, Cloud. On an emulator behind NAT, LAN-only endpoints (SMB `test_media`) are register-only;
public SFTP is reachable. `/spec-prerelease` enforces this split automatically.
