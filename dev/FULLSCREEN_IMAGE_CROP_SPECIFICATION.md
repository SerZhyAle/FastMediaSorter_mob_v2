# Fullscreen Image Crop Setting Specification

## Overview

This specification describes the implementation of a new setting that allows users to control how images and animations are displayed in fullscreen mode, during regular slideshow playback, and during audio slideshow with background photos. The setting enables smart cropping that respects the orientation match between the media content and the device.

## Problem Statement

**Current Behavior:**

- All images and animations are displayed using `FIT_CENTER` scale type
- This creates black bars around images that could fill the screen
- Users cannot choose to maximize screen usage in fullscreen/slideshow mode
- Audio slideshow (showing photos while playing music) also uses `FIT_CENTER` regardless of orientation match

**Desired Behavior:**

- Users can enable a setting to crop images to fill the screen in all image display contexts
- Cropping only applies when the media orientation matches device orientation
- Prevents distortion of portrait images on landscape displays and vice versa
- Maintains backward compatibility with default `FIT_CENTER` behavior
- Applies consistently across:
  - **Single image fullscreen viewing**
  - **Regular slideshow** (playing images one after another)
  - **Audio slideshow with background photos** (displaying images while music plays)
  - **Background slideshow for music** (same as audio slideshow - alias for clarity)

## Use Cases

### Use Case 1: Regular Image Slideshow

When viewing photos or animations in fullscreen mode or during a slideshow, users may want to maximize screen real estate by cropping images to fill the entire screen, but only when it makes sense (i.e., when orientations match). This prevents:

- Wasted black bars on matching orientations
- Distortion of mismatched orientations
- Loss of important image content when cropping would be inappropriate

### Use Case 2: Audio Slideshow with Background Photos

When playing music with background photos cycling through, users want:

- Immersive fullscreen photo experience without black bars
- Photos that match device orientation to fill the screen (CENTER_CROP)
- Portrait photos on landscape display to retain full visibility (FIT_CENTER)
- Consistent behavior with regular slideshow mode
- Seamless transitions between photos while music plays

### Use Case 3: Music Player with Single Background Photo

When playing audio files with a selected background photo displayed:

- Apply same cropping rules as slideshow for consistency
- Fill screen when photo orientation matches device
- Preserve full photo when orientations mismatch

## Technical Requirements

### 1. Database Schema Changes

**Table:** `MediaSettings` (or equivalent settings table)

**New Field:**

- **Name:** `cropImagesToFullscreen` (or `fullscreenImageCrop`)
- **Type:** `BOOLEAN`
- **Default:** `true`
- **Description:** When enabled, images and animations use CENTER_CROP in fullscreen/slideshow mode if orientations match

**Migration:**

```sql
ALTER TABLE MediaSettings ADD COLUMN cropImagesToFullscreen BOOLEAN NOT NULL DEFAULT 1;
```

### 2. Settings UI Location

**Path:** Settings → Media → Images

**UI Component:**

- **Type:** Switch/Toggle
- **Label (English):** "Crop images to fullscreen"
- **Description/Subtitle:** "Fill screen with images when orientations match (fullscreen & slideshow only)"
- **Default State:** ON (checked)

**String Resources Required:**

```xml
<string name="pref_crop_images_fullscreen_title">Crop images to fullscreen</string>
<string name="pref_crop_images_fullscreen_summary">Fill screen when image and device orientations match (fullscreen &amp; slideshow)</string>
```

### 3. Scale Type Logic

**Decision Tree:**

```
IF setting is DISABLED:
    → Use FIT_CENTER for all images/animations in all contexts

IF setting is ENABLED:
    IF context is NOT (fullscreen OR regular_slideshow OR audio_slideshow):
        → Use FIT_CENTER
    
    IF context is (fullscreen OR regular_slideshow OR audio_slideshow):
        IF orientation_match(image, device):
            → Use CENTER_CROP
        ELSE:
            → Use FIT_CENTER
```

**Context Definitions:**

- **fullscreen**: Single image displayed in fullscreen mode (command panel hidden)
- **regular_slideshow**: Automated image playback, advancing through image files
- **audio_slideshow**: Background photos displayed while playing audio files (enters when slideshow activated while on audio file with photo resource configured)

**Orientation Match Function:**

```kotlin
fun isOrientationMatch(imageWidth: Int, imageHeight: Int, deviceWidth: Int, deviceHeight: Int): Boolean {
    val isImageLandscape = imageWidth > imageHeight
    val isDeviceLandscape = deviceWidth > deviceHeight
    
    return isImageLandscape == isDeviceLandscape
}
```

**Examples:**

- ✅ Image: 1920×1080 (landscape), Device: 2400×1080 (landscape) → **MATCH** → CENTER_CROP
- ✅ Image: 1080×1920 (portrait), Device: 1080×2400 (portrait) → **MATCH** → CENTER_CROP
- ❌ Image: 1080×1920 (portrait), Device: 2400×1080 (landscape) → **NO MATCH** → FIT_CENTER
- ❌ Image: 1920×1080 (landscape), Device: 1080×2400 (portrait) → **NO MATCH** → FIT_CENTER
- ✅ Image: 1000×1000 (square), Device: 1080×1080 (square) → **MATCH** → CENTER_CROP

**Edge Cases:**

- Square images (width == height): Considered as matching any device orientation
- Very close aspect ratios: Use exact comparison (width > height vs width < height)
- Device rotation during playback: Re-evaluate on configuration change
- Audio slideshow photo transitions: Evaluate scale type for each new photo independently

## Development Stages & Phases

⚠️ **CRITICAL INSTRUCTION FOR DEVELOPER:**
For every phase and sub-task below, you MUST:

1. **Build the project** to ensure no compilation errors.
2. **Verify the specific change** works as expected (run the app or unit tests).
3. **COMMIT your changes** to Git with a descriptive message.
**DO NOT accumulate changes.** Commit after each logical step.

### Phase 1: Database & Data Layer

1. **Database Schema Update**
    - Add migration script to create the new `cropImagesToFullscreen` column
    - Set default value to `false` (0)
    - Update database version number
    - *Deliverable:* Migration file, updated schema
    - **STEP:** Build & Commit with message: `feat(db): add cropImagesToFullscreen setting column`

2. **Data Model Update**
    - Add `cropImagesToFullscreen: Boolean` property to `MediaSettings` data class (or equivalent)
    - Ensure constructor includes default value `= false`
    - Update DAO queries to include new field
    - **STEP:** Build & Commit with message: `feat(model): add cropImagesToFullscreen to MediaSettings`

3. **Repository Layer**
    - Add getter/setter methods for the new setting in `SettingsRepository`
    - Ensure proper Flow/LiveData emission on changes
    - **STEP:** Build & Commit with message: `feat(repo): add cropImagesToFullscreen accessor methods`

### Phase 2: Settings UI

1. **String Resources**
    - Add string keys to `strings.xml` for English
    - Add translations to `strings.xml` for Russian (if applicable)
    - Add translations to `strings.xml` for Ukrainian (if applicable)
    - **STEP:** Build & Commit with message: `feat(i18n): add strings for fullscreen crop setting`

2. **Settings Screen XML**
    - Add SwitchPreference to Media Settings → Images section
    - Set `android:key`, `android:title`, `android:summary`, `android:defaultValue="false"`
    - **STEP:** Build & Commit with message: `feat(ui): add fullscreen crop toggle to settings`

3. **Settings ViewModel/Fragment**
    - Bind the preference to the repository
    - Ensure changes are saved to database
    - **STEP:** Build & Commit with message: `feat(settings): bind fullscreen crop setting to data layer`

### Phase 3: Core Logic Implementation

1. **Orientation Match Utility**
    - Create utility function `isOrientationMatch(imageWidth, imageHeight, deviceWidth, deviceHeight): Boolean`
    - Place in appropriate utility class (e.g., `ImageUtils` or `DisplayUtils`)
    - Write **Unit Tests** covering all cases (landscape-landscape, portrait-portrait, mismatches, square)
    - **STEP:** Build & Commit with message: `feat(util): add orientation match detection logic`

2. **Image Display Mode Decision Logic**
    - Create function `determineScaleType(context, isFullscreen, isSlideshow, setting, imageSize, deviceSize): ScaleType`
    - Implement decision tree from Section 3.3
    - Write **Unit Tests** for all branches
    - **STEP:** Build & Commit with message: `feat(image): add scale type decision logic`

### Phase 4: Integration

1. **Fullscreen Image Viewer Integration**
    - Identify the image viewer component used for fullscreen display
    - Integrate the `determineScaleType` function
    - Pass current setting value from repository
    - Apply scale type to ImageView
    - **STEP:** Build & Commit with message: `feat(viewer): integrate fullscreen crop logic`

2. **Slideshow Integration**
    - Identify the slideshow display component
    - Integrate the `determineScaleType` function
    - Apply scale type to ImageView
    - **STEP:** Build & Commit with message: `feat(slideshow): integrate fullscreen crop logic`

3. **Audio Slideshow Integration**
    - Identify audio slideshow photo loading (PlayerActivity.loadBackgroundPhotoIntoImageView)
    - Replace hardcoded `FIT_CENTER` with dynamic scale type determination
    - Apply orientation matching logic when loading background photos
    - Ensure scale type updates on each photo transition
    - **STEP:** Build & Commit with message: `feat(audio-slideshow): integrate fullscreen crop logic`

4. **Animation Support**
    - Verify GIF/WebP/APNG animations use the same ImageView
    - Ensure scale type applies to animated formats
    - Test with Glide/Coil libraries if used
    - **STEP:** Build & Commit with message: `feat(animation): apply crop logic to animated images`

### Phase 5: Dynamic Behavior

1. **Configuration Change Handling**
    - Listen for device orientation changes (portrait ↔ landscape)
    - Re-evaluate scale type when device rotates
    - Update ImageView.scaleType dynamically
    - Apply to all contexts: fullscreen, regular slideshow, audio slideshow
    - **STEP:** Build & Commit with message: `feat(rotation): handle scale type on device rotation`

2. **Setting Change Reactivity**
    - Listen for setting changes while in fullscreen/slideshow mode
    - Update scale type immediately when user toggles setting
    - Apply to currently displayed image (regular or audio slideshow)
    - **STEP:** Build & Commit with message: `feat(reactive): update display when setting changes`

### Phase 6: Testing & Verification

1. **Manual Testing Checklist**
    - Test with setting OFF: verify all images use FIT_CENTER in all contexts
    - Test with setting ON + matching orientations: verify CENTER_CROP
    - Test with setting ON + mismatched orientations: verify FIT_CENTER
    - Test device rotation during fullscreen display
    - Test during regular slideshow playback
    - **Test during audio slideshow with background photos**
    - **Test background photo transitions maintain correct scale type**
    - Test with GIF/WebP animations
    - Test with square images
    - Test setting toggle while in fullscreen/slideshow/audio-slideshow
    - **STEP:** Build & Commit with message: `test(manual): verify fullscreen crop behavior`

2. **Edge Case Testing**
    - Test with very tall images (e.g., 1080×5000)
    - Test with very wide images (e.g., 5000×1080)
    - Test with different device aspect ratios (16:9, 18:9, 20:9)
    - Test on tablets vs phones
    - **Test audio slideshow with mixed orientation photos**
    - **Test audio slideshow on device rotation**
    - **Test switching between regular slideshow and audio slideshow**
    - **STEP:** Build & Commit with message: `test(edge): verify edge cases for crop logic`

### Phase 7: Documentation

1. **Code Documentation**
    - Add KDoc/JavaDoc comments to utility functions
    - Document the setting in architecture diagrams (if applicable)
    - Update changelog/release notes
    - **STEP:** Build & Commit with message: `docs: add documentation for fullscreen crop feature`

## Success Criteria

The implementation is considered successful when:

1. ✅ Database migration adds `cropImagesToFullscreen` field with default `false`
2. ✅ Settings UI displays toggle in correct location with proper labels
3. ✅ Setting value is persisted to database correctly
4. ✅ With setting OFF: all images use `FIT_CENTER` (no behavior change) in all contexts
5. ✅ With setting ON: matching orientations in fullscreen/slideshow use `CENTER_CROP`
6. ✅ With setting ON: mismatched orientations still use `FIT_CENTER`
7. ✅ **With setting ON: audio slideshow photos with matching orientations use `CENTER_CROP`**
8. ✅ **With setting ON: audio slideshow photos with mismatched orientations use `FIT_CENTER`**
9. ✅ Normal browse mode always uses `FIT_CENTER` regardless of setting
10. ✅ Device rotation updates scale type dynamically in all contexts
11. ✅ Setting toggle updates display in real-time
12. ✅ Animated images (GIF/WebP) behave correctly
13. ✅ **Audio slideshow photo transitions maintain correct scale type**
14. ✅ All unit tests pass
15. ✅ Manual testing confirms expected behavior across all contexts

## Potential Risks and Mitigation

### Risk 1: Content Loss from Aggressive Cropping

**Mitigation:** Only crop when orientations match. Provide clear setting description. Consider showing preview in settings.

### Risk 2: Performance Impact from Recalculation

**Mitigation:** Cache calculated orientation match for current image. Only recalculate on image change or device rotation.

### Risk 3: User Confusion About When Cropping Applies

**Mitigation:** Use descriptive setting label and help text. Consider visual indicator in fullscreen mode.

### Risk 4: Library Compatibility (Glide/Coil)

**Mitigation:** Test with current image loading library. Ensure `scaleType` property is respected.

### Risk 5: Breaking Existing Behavior

**Mitigation:** Default to OFF. Existing users see no change unless they enable it.

## Future Enhancements

Potential improvements for future iterations:

1. **Per-Resource Setting:** Allow different crop preferences for different media resources
2. **Crop Sensitivity:** Add "Aggressive/Moderate/Conservative" crop modes
3. **Preview in Settings:** Show before/after comparison when enabling
4. **Pan Control:** Allow user to pan cropped image to see hidden areas
5. **Smart Crop:** Use AI to detect subject and crop around important content

## Audio Slideshow Implementation Details

### Overview

Audio slideshow mode displays background photos while playing audio files. This feature must respect the `cropImagesToFullscreen` setting with the same orientation matching logic as regular slideshow.

### Key Implementation Points

**File Location:** `PlayerActivity.kt`

**Critical Functions:**

1. **`loadBackgroundPhotoIntoImageView(photo: MediaFile)`** (line ~2697)
   - Currently hardcodes `FIT_CENTER` on line ~2824
   - **FIX REQUIRED:** Replace with dynamic scale type determination
   - Must call `ImageDisplayUtils.determineImageScaleType()` with:
     - `cropImagesToFullscreen` from settings
     - `isFullscreenOrSlideshow = true` (always in fullscreen during audio slideshow)
     - Image dimensions from loaded drawable (in `onResourceReady` callback)
     - Device dimensions from WindowMetrics

2. **`enterAudioSlideshowPhotoMode()`** (line ~2812)
   - Sets initial `scaleType = FIT_CENTER` on line ~2824
   - **FIX REQUIRED:** Remove hardcoded scale type, let `loadBackgroundPhotoIntoImageView` handle it

3. **Glide `onResourceReady` callback** (line ~2742)
   - Receives loaded drawable with intrinsic dimensions
   - **ADD:** Scale type determination logic here once image dimensions are known
   - Apply `binding.imageView.scaleType = determinedScaleType`

### Implementation Code Example

```kotlin
// In loadBackgroundPhotoIntoImageView, add to onResourceReady callback:
override fun onResourceReady(
    resource: android.graphics.drawable.Drawable,
    model: Any,
    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
    dataSource: com.bumptech.glide.load.DataSource,
    isFirstResource: Boolean
): Boolean {
    // Get image dimensions
    val imageWidth = resource.intrinsicWidth
    val imageHeight = resource.intrinsicHeight
    
    // Get device dimensions
    val bounds = windowManager.currentWindowMetrics.bounds
    val deviceWidth = bounds.width()
    val deviceHeight = bounds.height()
    
    // Get setting from repository
    lifecycleScope.launch {
        val settings = settingsRepository.getSettings().first()
        
        // Determine scale type
        val scaleType = ImageDisplayUtils.determineImageScaleType(
            cropImagesToFullscreen = settings.cropImagesToFullscreen,
            isFullscreenOrSlideshow = true, // Always fullscreen in audio slideshow
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            deviceWidth = deviceWidth,
            deviceHeight = deviceHeight
        )
        
        // Apply scale type
        binding.imageView.scaleType = scaleType
        Timber.d("AudioSlideshow: Applied scale type $scaleType (${imageWidth}x${imageHeight} on ${deviceWidth}x${deviceHeight})")
    }
    
    // Preload next photo
    preloadNextAudioSlideshowPhoto()
    return false
}
```

### Integration Checklist

- [ ] Remove hardcoded `FIT_CENTER` from `enterAudioSlideshowPhotoMode()`
- [ ] Add scale type determination in `loadBackgroundPhotoIntoImageView` Glide callback
- [ ] Test with landscape photos on landscape device (should CENTER_CROP)
- [ ] Test with portrait photos on landscape device (should FIT_CENTER)
- [ ] Test with portrait photos on portrait device (should CENTER_CROP)
- [ ] Test device rotation during audio slideshow
- [ ] Test setting toggle while audio slideshow is active
- [ ] Verify preloaded photos don't interfere with scale type
- [ ] Test with network photos (SMB/SFTP/FTP)
- [ ] Test photo transitions maintain correct scale type

### Performance Considerations

1. **Async Scale Type Calculation:** Done in coroutine to avoid blocking UI
2. **Cache Device Dimensions:** Avoid repeated WindowMetrics calls
3. **No Re-downloading:** Scale type change doesn't trigger image reload
4. **Preload Compatibility:** Preloaded images will have scale type applied when displayed

## Technical Notes

### ScaleType Reference

- **FIT_CENTER:** Scale image to fit inside view, maintaining aspect ratio, centered
- **CENTER_CROP:** Scale image to fill entire view, maintaining aspect ratio, cropping excess

### Affected Components

- Image Viewer (fullscreen display)
- Slideshow Player (regular image slideshow)
- **Audio Slideshow (PlayerActivity.loadBackgroundPhotoIntoImageView)**
- **Audio Background Photos Manager**
- Settings UI (Media → Images)
- Database schema
- Settings Repository
- Orientation utilities
- **ImageDisplayUtils** (scale type determination)

### Dependencies

- Android Room (database)
- AndroidX Preferences (settings UI)
- Glide/Coil (image loading library)
- Kotlin Flow/LiveData (reactive updates)

## References

- Android ImageView ScaleType documentation
- Android Configuration Changes documentation
- Room Database Migration guide
- Project architecture documents

---

**Document Version:** 2.0  
**Created:** 2026-02-10  
**Updated:** 2026-02-11  
**Status:** Extended Specification - Audio Slideshow Integration Added  
**Feature Category:** Media Display Enhancement
