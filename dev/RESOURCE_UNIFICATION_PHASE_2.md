# Resource Unification Phase 2 - TODO

**Created:** 2026-02-12  
**Status:** PENDING  
**Priority:** MEDIUM  

## Context

After successful completion of Phase 1 unification:

- ✅ Colors: 67 → 21 base + 46 aliases (76 replacements, BUILD SUCCESSFUL v2.60.2120.211)
- ✅ Dimensions: 350 → 19 base + 316 aliases + 31 unique (695 replacements, BUILD SUCCESSFUL v2.60.2120.227)
- ✅ Strings: 231 (113 UI strings translated to RU/UK, 170 replacements, BUILD SUCCESSFUL v2.60.2120.254)
- ✅ Alignment: 11 semantic resources (~100+ replacements, BUILD SUCCESSFUL v2.60.2120.305)

**Total Phase 1:** ~1041 replacements across 60+ layout files, 100% build success rate.

---

## Remaining Unification Opportunities

Analysis of `temp/generated_resources/strings.xml` and `temp/generated_resources/integers.xml` revealed 5 additional unification categories with **~69 total replacements** across **17 new semantic resources**.

---

## 1. scaleType for ImageView/ImageButton [MEDIUM PRIORITY]

**Current state:** 12 occurrences with hardcoded values

**Patterns:**

- `fitCenter`: **10 usages**
  - item_media_file_btnCopyItem
  - item_media_file_btnDeleteItem
  - item_media_file_btnFavorite
  - item_media_file_btnMoveItem
  - item_media_file_btnRenameItem
  - item_media_file_grid_btnCopyItem
  - item_media_file_grid_btnDeleteItem
  - item_media_file_grid_btnFavorite
  - item_media_file_grid_btnMoveItem
  - item_media_file_grid_btnRenameItem

- `centerCrop`: **2 usages**
  - item_media_file_grid_ivThumbnail
  - item_media_file_ivThumbnail

**Proposed semantic resources (values/strings.xml):**

```xml
<!-- Image scaling modes -->
<string name="image_scale_fit_center">fitCenter</string>
<string name="image_scale_center_crop">centerCrop</string>
```

**Impact:**

- Change `image_scale_fit_center` from "fitCenter" to "fitStart" → 10 operation buttons adjust alignment
- Change `image_scale_center_crop` from "centerCrop" to "fitCenter" → 2 thumbnails change scaling behavior

**Estimated replacements:** 12 across 2-3 layout files

---

## 2. textColor (System References) [HIGH PRIORITY]

**Current state:** ~23 occurrences with system theme references

**Patterns:**

- `?android:textColorSecondary`: **~15 usages** (most common)
  - item_media_file_tvFileInfo
  - item_network_host_tvIp
  - item_network_host_tvServices (tint)
  - item_resource_to_add_tvFileCount
  - item_resource_to_add_tvPath
  - item_resource_tvAvailabilityIndicator
  - item_resource_tvFileCount
  - item_resource_tvMediaTypes
  - item_resource_tvResourceComment
  - item_resource_tvResourcePath
  - item_resource_tvResourceType
  - item_resource_tvSpeedTestResults
  - item_resource_grid_tvMediaTypes
  - item_resource_grid_tvSpeedTestResults
  - - more

- `?android:textColorPrimary`: **4 usages**
  - item_resource_tvDestinationMark
  - item_resource_tvResourceName
  - item_resource_grid_tvResourceName
  - - more

- `?android:textColorTertiary`: **1 usage**
  - item_resource_tvLastSync

- `?android:attr/textColorSecondary`: **3 usages** (variant syntax)
  - item_resource_grid_tvMediaTypes
  - item_resource_grid_tvSpeedTestResults

**Proposed semantic resources (values/colors.xml):**

```xml
<!-- System text colors (theme-aware) -->
<color name="text_color_primary">?android:textColorPrimary</color>
<color name="text_color_secondary">?android:textColorSecondary</color>
<color name="text_color_tertiary">?android:textColorTertiary</color>
```

**Impact:**

- Critical for theme consistency
- Enables centralized override of system color references
- Example: Override `text_color_secondary` with custom color → affects ~18 text elements

**Estimated replacements:** 23 across 10+ layout files

**Notes:**

- Both `?android:textColorSecondary` and `?android:attr/textColorSecondary` should map to same semantic resource
- Requires handling both attribute syntax variations in replacement script

---

## 3. background Patterns [LOW PRIORITY]

**Current state:** 3 occurrences

**Patterns:**

- `?android:selectableItemBackground`: **2 usages**
  - item_media_file_grid_ivThumbnail
  - item_media_file_ivThumbnail

- `@null`: **1 usage**
  - item_resource_to_add_etName

**Proposed semantic resources (values/strings.xml or drawable/):**

```xml
<!-- Background patterns -->
<drawable name="background_selectable_item">?android:selectableItemBackground</drawable>
<drawable name="background_transparent">@null</drawable>
```

**Alternative:** Use existing material design attributes directly without aliasing

**Impact:** Minimal (only 3 usages), consider skipping

**Estimated replacements:** 3 across 2 layout files

---

## 4. maxLength for EditText [MEDIUM PRIORITY]

**Current state:** 15 occurrences in integers.xml

**Patterns:**

- `maxLength="2"`: **1 usage**
  - fragment_settings_destinations_etMaxRecipients

- `maxLength="4"`: **1 usage**
  - fragment_settings_playback_etIconSize

- `maxLength="5"`: **2 usages**
  - activity_edit_resource_etSlideshowInterval
  - fragment_settings_playback_etSlideshowInterval

- `maxLength="6"`: **3 usages**
  - activity_add_resource_etLocalPinCode
  - activity_add_resource_etSftpPinCode
  - activity_add_resource_etSmbPinCode

- `maxLength="7"`: **8 usages**
  - fragment_settings_audio_etAudioSizeMax
  - fragment_settings_media_etAudioSizeMax
  - fragment_settings_audio_etAudioSizeMin
  - fragment_settings_media_etAudioSizeMin
  - fragment_settings_images_etImageSizeMax
  - fragment_settings_media_etImageSizeMax
  - fragment_settings_images_etImageSizeMin
  - fragment_settings_media_etImageSizeMin

**Proposed semantic resources (values/integers.xml):**

```xml
<!-- Input field length limits -->
<integer name="input_max_length_tiny">2</integer>
<integer name="input_max_length_small">4</integer>
<integer name="input_max_length_medium">5</integer>
<integer name="input_max_length_pin">6</integer>
<integer name="input_max_length_filesize">7</integer>
```

**Impact:**

- Standardize input limits across app
- Example: Change `input_max_length_pin` from 6 to 8 → all PIN fields (3) accept 8 digits

**Estimated replacements:** 15 across 5+ layout files

---

## 5. Grid Layout and SeekBar Patterns [MEDIUM PRIORITY]

**Current state:** 16 occurrences in integers.xml

**Grid patterns:**

- `columnCount="3"`: **2 usages**
  - activity_player_unified_audioTouchZonesOverlay
  - touch_zones_overlay_audioTouchZonesOverlay

- `columnCount="5"`: **2 usages**
  - player_command_panel_mode_copyDestinationsGrid
  - player_command_panel_mode_moveDestinationsGrid

- `rowCount="2"`: **2 usages**
  - player_command_panel_mode_copyDestinationsGrid
  - player_command_panel_mode_moveDestinationsGrid

- `rowCount="3"`: **2 usages**
  - activity_player_unified_audioTouchZonesOverlay
  - touch_zones_overlay_audioTouchZonesOverlay

**SeekBar max values:**

- `max="100"`: **3 usages**
  - dialog_file_operation_progress_progressBar
  - dialog_integration_test_progressBar
  - dialog_material_progress_horizontal_progressBar

- `max="200"`: **3 usages**
  - dialog_image_edit_seekBrightness
  - dialog_image_edit_seekContrast
  - dialog_image_edit_seekSaturation

**Other patterns:**

- `progress="100"`: **2 usages** (seekbar defaults)
- `progress="50"`: **1 usage** (GIF speed default)

**Proposed semantic resources (values/integers.xml):**

```xml
<!-- Grid layout dimensions -->
<integer name="grid_columns_small">3</integer>
<integer name="grid_columns_large">5</integer>
<integer name="grid_rows_small">2</integer>
<integer name="grid_rows_medium">3</integer>

<!-- SeekBar/ProgressBar ranges -->
<integer name="seekbar_max_percent">100</integer>
<integer name="seekbar_max_extended">200</integer>
<integer name="seekbar_default_center">100</integer>
<integer name="seekbar_default_half">50</integer>
```

**Impact:**

- Change `grid_columns_large` from 5 to 6 → destination grids show 6 columns (2 grids affected)
- Change `grid_columns_small` from 3 to 4 → audio touch zones become 4x3 grid (2 overlays affected)
- Change `seekbar_max_extended` from 200 to 300 → image edit sliders get wider range (3 seekbars)

**Estimated replacements:** 16 across 6+ layout files

---

## Summary Statistics

| Category | Occurrences | Semantic Resources | Priority | Files Affected |
|----------|-------------|-------------------|----------|----------------|
| textColor (system) | ~23 | 3 | **HIGH** | 10+ |
| Grid patterns | 16 | 8 | MEDIUM | 6+ |
| maxLength | 15 | 5 | MEDIUM | 5+ |
| scaleType | 12 | 2 | MEDIUM | 2-3 |
| background | 3 | 2 | LOW | 2 |
| **TOTAL** | **~69** | **20** | - | **25+** |

---

## Implementation Plan

### Step 1: Add Semantic Resources

**1.1. Update values/colors.xml:**

```xml
<!-- System text colors (theme-aware) -->
<color name="text_color_primary">?android:textColorPrimary</color>
<color name="text_color_secondary">?android:textColorSecondary</color>
<color name="text_color_tertiary">?android:textColorTertiary</color>
```

**1.2. Update values/strings.xml:**

```xml
<!-- Image scaling modes -->
<string name="image_scale_fit_center">fitCenter</string>
<string name="image_scale_center_crop">centerCrop</string>
```

**1.3. Create/Update values/integers.xml:**

```xml
<!-- Input field length limits -->
<integer name="input_max_length_tiny">2</integer>
<integer name="input_max_length_small">4</integer>
<integer name="input_max_length_medium">5</integer>
<integer name="input_max_length_pin">6</integer>
<integer name="input_max_length_filesize">7</integer>

<!-- Grid layout dimensions -->
<integer name="grid_columns_small">3</integer>
<integer name="grid_columns_large">5</integer>
<integer name="grid_rows_small">2</integer>
<integer name="grid_rows_medium">3</integer>

<!-- SeekBar/ProgressBar ranges -->
<integer name="seekbar_max_percent">100</integer>
<integer name="seekbar_max_extended">200</integer>
<integer name="seekbar_default_center">100</integer>
<integer name="seekbar_default_half">50</integer>
```

### Step 2: Create Mapping Libraries

For each category, create JSON mapping files in `temp/`:

- `temp/string_resources_scaletype.json`
- `temp/color_resources_systemtext.json`
- `temp/integer_resources_maxlength.json`
- `temp/integer_resources_grid.json`

Structure example (scaleType):

```json
{
  "item_media_file_btnCopyItem_scaleType": {
    "resourceName": "image_scale_fit_center",
    "resourceType": "string_literal",
    "value": "fitCenter",
    "attribute": "scaleType",
    "namespace": "android:"
  }
}
```

### Step 3: Execute Replacements

Run replacement script for each type:

```powershell
# High priority: textColor
.\3-replace-with-resources.ps1 -LibraryFile "..\..\temp\color_resources_systemtext.json" -Types @('color_reference')

# Medium priority: integers (maxLength, grid patterns)
.\3-replace-with-resources.ps1 -LibraryFile "..\..\temp\integer_resources_maxlength.json" -Types @('integer')
.\3-replace-with-resources.ps1 -LibraryFile "..\..\temp\integer_resources_grid.json" -Types @('integer')

# Medium priority: scaleType
.\3-replace-with-resources.ps1 -LibraryFile "..\..\temp\string_resources_scaletype.json" -Types @('string_literal')
```

### Step 4: Build and Verify

After each category:

```powershell
.\scripts\builders\build-debug.PS1
```

Expected result: BUILD SUCCESSFUL with version increment

### Step 5: Test Calibration

Manually test semantic resource changes:

1. Change `text_color_secondary` to custom color → verify ~18 text elements update
2. Change `grid_columns_large` from 5 to 6 → verify destination grids show 6 columns
3. Change `input_max_length_pin` from 6 to 8 → verify PIN fields accept 8 digits
4. Revert changes to confirm system works

---

## Technical Notes

### Handling System Color References

The replacement script must handle both syntax variants:

- `?android:textColorSecondary` (direct attribute)
- `?android:attr/textColorSecondary` (explicit attr namespace)

Both should map to same semantic resource: `@color/text_color_secondary`

### textColor vs tint

Some elements use `tint` instead of `textColor` for icons (e.g., `item_network_host_tvServices_tint`). These should also map to color resources but with `tint` attribute in mapping.

### Background as Drawable vs String

Consider whether to define backgrounds in:

- `values/strings.xml` (simple reference passthrough)
- `values/drawables.xml` (proper drawable aliasing)

Recommendation: Skip background unification due to low usage (only 3 occurrences).

---

## Expected Outcomes

### Build Success

- All replacements should maintain BUILD SUCCESSFUL status
- No layout rendering errors
- No resource resolution failures

### Calibration Power

After Phase 2 completion, total centralized control points:

- Phase 1: 50+ semantic resources (colors, dimensions, strings, alignment)
- Phase 2: 20 semantic resources (textColor, scaleType, integers)
- **Total: 70+ centralized UI control points**

### Maintenance Benefits

1. **Consistent theme colors**: Change system text colors app-wide
2. **Flexible grids**: Adjust grid layouts globally (touch zones, destination grids)
3. **Standardized inputs**: Modify input limits by category
4. **Unified scaling**: Control image scaling behavior centrally

---

## Risk Assessment

**LOW RISK:**

- Non-breaking changes (values remain same, only referenced)
- Reversible (can revert to inline values if needed)
- Proven pattern (Phase 1 had 100% success rate)

**Mitigation:**

- Test build after each category
- Keep backups in `temp/` before large changes
- Verify UI appearance unchanged (values same, just referenced)

---

## Files to Modify

### Resource Files (add semantic resources)

- `app_v2/src/main/res/values/colors.xml` (add 3 textColor aliases)
- `app_v2/src/main/res/values/strings.xml` (add 2 scaleType values)
- `app_v2/src/main/res/values/integers.xml` (add 15 semantic integers)

### Layout Files (replacements)

- `item_media_file.xml` (scaleType, textColor)
- `item_media_file_grid.xml` (scaleType, textColor)
- `item_resource.xml` (textColor)
- `item_resource_grid.xml` (textColor)
- `item_resource_to_add.xml` (textColor)
- `activity_player_unified.xml` (grid dimensions)
- `touch_zones_overlay.xml` (grid dimensions)
- `player_command_panel_mode.xml` (grid dimensions)
- `dialog_image_edit.xml` (seekbar max)
- `dialog_gif_editor.xml` (seekbar max)
- `fragment_settings_*.xml` (maxLength)
- `activity_add_resource.xml` (maxLength)
- - ~15 more files

---

## Success Criteria

✅ All semantic resources added to appropriate XML files  
✅ JSON mapping libraries created for all categories  
✅ Replacement script executed successfully for all types  
✅ BUILD SUCCESSFUL after each category deployment  
✅ No layout rendering errors in Android Studio preview  
✅ UI appearance unchanged (values same, only referenced)  
✅ Manual calibration test successful (change resource → verify propagation)  
✅ Version incremented appropriately (v2.60.2120.30X+)

---

## References

- Phase 1 completion: [Alignment unification v2.60.2120.305](../DOWNLOADS/builds_versions.lst)
- Extract script: `scripts/layout-dimen-migration/1-extract-all-resources-v2.ps1`
- Replace script: `scripts/layout-dimen-migration/3-replace-with-resources.ps1`
- Generated resources: `temp/generated_resources/*.xml`
- Semantic naming convention: `${layout}_${viewId}_${attribute}` (no value suffix)

---

**Next Session: Execute Phase 2 unification starting with HIGH priority textColor category**
