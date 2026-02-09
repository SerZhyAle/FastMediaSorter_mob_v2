# Test Media Workflow - Quick Guide

## Prerequisites

### 1. Upload test media to device

```powershell
.\scripts\utils\setup_test_media.ps1
```

This uploads all files from `test_media/` to `/sdcard/Download/FastMediaSorter_Test` on device.

### 2. Setup AVD (if not done)

```powershell
.\scripts\utils\setup-avd-for-tests.ps1
```

---

## Running the Test

**Run full smoke suite** (includes test_media_workflow):

```powershell
.\maestro\run-tests.ps1 smoke
```

**Run only test media workflow**:

```powershell
maestro test .\maestro\smoke\test_media_workflow.yaml
```

**With debug output**:

```powershell
maestro test --flatten-debug-output .\maestro\smoke\test_media_workflow.yaml
```

---

## What the Test Does

The `test_media_workflow.yaml` test performs a complete workflow:

### Phase 1: Image Viewing

- Navigate to `/sdcard/Download/FastMediaSorter_Test`
- Open image file (jpg/png/webp)
- Swipe left/right between images
- Close viewer

### Phase 2: Video Playback

- Find video file (mp4/webm/mkv)
- Open video player
- Test play/pause controls
- Close player

### Phase 3: Audio Playback

- Find audio file (mp3/flac)
- Open audio player
- Test playback controls
- Try next track
- Close player

### Phase 4: File Operations

- Long press on file
- Test context menu (copy/move/delete)
- Cancel operations

### Phase 5: Filtering & Search

- Test filter buttons (Images/Videos/Audio/ALL)
- Test search functionality

---

## Troubleshooting

### Test fails at "Navigate to Download"

**Cause**: Device might use different storage structure.

**Fix**: Check actual path on device:

```powershell
adb shell ls /sdcard/Download/FastMediaSorter_Test
```

If files are elsewhere, update `$DeviceDestDir` in `setup_test_media.ps1`.

### No media files found

**Cause**: MediaStore not updated.

**Fix**: Rerun media scan:

```powershell
adb shell am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///sdcard/Download/FastMediaSorter_Test
```

### Test times out

**Cause**: Animations still enabled or low memory.

**Fix**:

```powershell
.\scripts\utils\setup-avd-for-tests.ps1  # Re-disable animations
```

---

## Test Files Used

From `test_media/` directory:

- **Images**: jpg, png, webp (100+ files)
- **Videos**: mp4, webm, mkv (Planet Unknown.2016, test videos)
- **Audio**: mp3, flac (300+ music files)
- **Documents**: epub, pdf (for future tests)

Total: ~500+ test files covering all media types supported by FastMediaSorter.

---

## Integration with Stress Tests

The test media can also be used for stress testing:

```powershell
# Run stress tests with real media files
.\scripts\run-maestro-stress.ps1 -Suite all -Monitor -Report
```

Stress tests will interact with test media during:

- Monkey random taps (may open random files)
- Rapid navigation (switching between media types)
- App lifecycle (resuming with media loaded in memory)
