# Fullscreen Image Crop Setting Specification

## Overview

This specification describes the implementation of a new setting that allows users to control how images and animations are displayed in fullscreen mode and during slideshow playback. The setting enables smart cropping that respects the orientation match between the media content and the device.

## Problem Statement

**Current Behavior:**
- All images and animations are displayed using `FIT_CENTER` scale type
- This creates black bars around images that could fill the screen
- Users cannot choose to maximize screen usage in fullscreen/slideshow mode

**Desired Behavior:**
- Users can enable a setting to crop images to fill the screen in fullscreen/slideshow mode
- Cropping only applies when the media orientation matches device orientation
- Prevents distortion of portrait images on landscape displays and vice versa
- Maintains backward compatibility with default `FIT_CENTER` behavior

## Use Case

When viewing photos or animations in fullscreen mode or during a slideshow, users may want to maximize screen real estate by cropping images to fill the entire screen, but only when it makes sense (i.e., when orientations match). This prevents:
- Wasted black bars on matching orientations
- Distortion of mismatched orientations
- Loss of important image content when cropping would be inappropriate

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
    → Use FIT_CENTER for all images/animations

IF setting is ENABLED:
    IF context is NOT (fullscreen OR slideshow):
        → Use FIT_CENTER
    
    IF context is (fullscreen OR slideshow):
        IF orientation_match(image, device):
            → Use CENTER_CROP
        ELSE:
            → Use FIT_CENTER
```

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

## Development Stages & Phases

⚠️ **CRITICAL INSTRUCTION FOR DEVELOPER:**
For every phase and sub-task below, you MUST:
1.  **Build the project** to ensure no compilation errors.
2.  **Verify the specific change** works as expected (run the app or unit tests).
3.  **COMMIT your changes** to Git with a descriptive message.
**DO NOT accumulate changes.** Commit after each logical step.

### Phase 1: Database & Data Layer

1.  **Database Schema Update**
    - Add migration script to create the new `cropImagesToFullscreen` column
    - Set default value to `false` (0)
    - Update database version number
    - *Deliverable:* Migration file, updated schema
    - **STEP:** Build & Commit with message: `feat(db): add cropImagesToFullscreen setting column`

2.  **Data Model Update**
    - Add `cropImagesToFullscreen: Boolean` property to `MediaSettings` data class (or equivalent)
    - Ensure constructor includes default value `= false`
    - Update DAO queries to include new field
    - **STEP:** Build & Commit with message: `feat(model): add cropImagesToFullscreen to MediaSettings`

3.  **Repository Layer**
    - Add getter/setter methods for the new setting in `SettingsRepository`
    - Ensure proper Flow/LiveData emission on changes
    - **STEP:** Build & Commit with message: `feat(repo): add cropImagesToFullscreen accessor methods`

### Phase 2: Settings UI

4.  **String Resources**
    - Add string keys to `strings.xml` for English
    - Add translations to `strings.xml` for Russian (if applicable)
    - Add translations to `strings.xml` for Ukrainian (if applicable)
    - **STEP:** Build & Commit with message: `feat(i18n): add strings for fullscreen crop setting`

5.  **Settings Screen XML**
    - Add SwitchPreference to Media Settings → Images section
    - Set `android:key`, `android:title`, `android:summary`, `android:defaultValue="false"`
    - **STEP:** Build & Commit with message: `feat(ui): add fullscreen crop toggle to settings`

6.  **Settings ViewModel/Fragment**
    - Bind the preference to the repository
    - Ensure changes are saved to database
    - **STEP:** Build & Commit with message: `feat(settings): bind fullscreen crop setting to data layer`

### Phase 3: Core Logic Implementation

7.  **Orientation Match Utility**
    - Create utility function `isOrientationMatch(imageWidth, imageHeight, deviceWidth, deviceHeight): Boolean`
    - Place in appropriate utility class (e.g., `ImageUtils` or `DisplayUtils`)
    - Write **Unit Tests** covering all cases (landscape-landscape, portrait-portrait, mismatches, square)
    - **STEP:** Build & Commit with message: `feat(util): add orientation match detection logic`

8.  **Image Display Mode Decision Logic**
    - Create function `determineScaleType(context, isFullscreen, isSlideshow, setting, imageSize, deviceSize): ScaleType`
    - Implement decision tree from Section 3.3
    - Write **Unit Tests** for all branches
    - **STEP:** Build & Commit with message: `feat(image): add scale type decision logic`

### Phase 4: Integration

9.  **Fullscreen Image Viewer Integration**
    - Identify the image viewer component used for fullscreen display
    - Integrate the `determineScaleType` function
    - Pass current setting value from repository
    - Apply scale type to ImageView
    - **STEP:** Build & Commit with message: `feat(viewer): integrate fullscreen crop logic`

10. **Slideshow Integration**
    - Identify the slideshow display component
    - Integrate the `determineScaleType` function
    - Apply scale type to ImageView
    - **STEP:** Build & Commit with message: `feat(slideshow): integrate fullscreen crop logic`

11. **Animation Support**
    - Verify GIF/WebP/APNG animations use the same ImageView
    - Ensure scale type applies to animated formats
    - Test with Glide/Coil libraries if used
    - **STEP:** Build & Commit with message: `feat(animation): apply crop logic to animated images`

### Phase 5: Dynamic Behavior

12. **Configuration Change Handling**
    - Listen for device orientation changes (portrait ↔ landscape)
    - Re-evaluate scale type when device rotates
    - Update ImageView.scaleType dynamically
    - **STEP:** Build & Commit with message: `feat(rotation): handle scale type on device rotation`

13. **Setting Change Reactivity**
    - Listen for setting changes while in fullscreen/slideshow mode
    - Update scale type immediately when user toggles setting
    - **STEP:** Build & Commit with message: `feat(reactive): update display when setting changes`

### Phase 6: Testing & Verification

14. **Manual Testing Checklist**
    - Test with setting OFF: verify all images use FIT_CENTER
    - Test with setting ON + matching orientations: verify CENTER_CROP
    - Test with setting ON + mismatched orientations: verify FIT_CENTER
    - Test device rotation during fullscreen display
    - Test during slideshow playback
    - Test with GIF/WebP animations
    - Test with square images
    - Test setting toggle while in fullscreen/slideshow
    - **STEP:** Build & Commit with message: `test(manual): verify fullscreen crop behavior`

15. **Edge Case Testing**
    - Test with very tall images (e.g., 1080×5000)
    - Test with very wide images (e.g., 5000×1080)
    - Test with different device aspect ratios (16:9, 18:9, 20:9)
    - Test on tablets vs phones
    - **STEP:** Build & Commit with message: `test(edge): verify edge cases for crop logic`

### Phase 7: Documentation

16. **Code Documentation**
    - Add KDoc/JavaDoc comments to utility functions
    - Document the setting in architecture diagrams (if applicable)
    - Update changelog/release notes
    - **STEP:** Build & Commit with message: `docs: add documentation for fullscreen crop feature`

## Success Criteria

The implementation is considered successful when:

1. ✅ Database migration adds `cropImagesToFullscreen` field with default `false`
2. ✅ Settings UI displays toggle in correct location with proper labels
3. ✅ Setting value is persisted to database correctly
4. ✅ With setting OFF: all images use `FIT_CENTER` (no behavior change)
5. ✅ With setting ON: matching orientations in fullscreen/slideshow use `CENTER_CROP`
6. ✅ With setting ON: mismatched orientations still use `FIT_CENTER`
7. ✅ Normal browse mode always uses `FIT_CENTER` regardless of setting
8. ✅ Device rotation updates scale type dynamically
9. ✅ Setting toggle updates display in real-time
10. ✅ Animated images (GIF/WebP) behave correctly
11. ✅ All unit tests pass
12. ✅ Manual testing confirms expected behavior

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

## Technical Notes

### ScaleType Reference
- **FIT_CENTER:** Scale image to fit inside view, maintaining aspect ratio, centered
- **CENTER_CROP:** Scale image to fill entire view, maintaining aspect ratio, cropping excess

### Affected Components
- Image Viewer (fullscreen display)
- Slideshow Player
- Settings UI (Media → Images)
- Database schema
- Settings Repository
- Orientation utilities

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

**Document Version:** 1.0  
**Created:** 2026-02-10  
**Status:** Specification - Ready for Implementation  
**Feature Category:** Media Display Enhancement
