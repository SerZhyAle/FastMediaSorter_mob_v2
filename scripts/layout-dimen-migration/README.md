# Layout Dimension Migration Scripts

Automated toolset for extracting, unifying, and managing dimension resources across Android layout files.

## Overview

These scripts help refactor hardcoded dimension values (dp, sp) into centralized `@dimen` resources for consistent styling across all layouts.

## Scripts

### 0️⃣ `0-backup-layouts.ps1` - Create Backups

Creates timestamped backup of all layout files before migration.

**Usage:**

```powershell
.\0-backup-layouts.ps1
```

**Output:** `temp\layout_backups\backup_YYYYMMDD_HHMMSS\`

---

### 1️⃣ `1-extract-all-resources.ps1` - Extract All Resources

Scans all layout files and extracts dimension values into JSON library.

**Usage:**

```powershell
scripts\layout-dimen-migration\1-extract-all-resources.ps1
```

**Output:**

- `temp\dimensions_library.json` - Full dimension mappings
- `temp\dimensions_library_summary.json` - Statistics

**Extracted Attributes:**

- Layout dimensions: `layout_width`, `layout_height`, `minWidth`, `maxWidth`, etc.
- Margins: `layout_margin*`, `padding*`
- Text: `textSize`
- Material: `elevation`, `cardElevation`, `cardCornerRadius`, `cornerRadius`
- Constraints: `layout_constraintWidth_*`, `layout_constraintHeight_*`
- Other: `iconSize`, `strokeWidth`

**Key Format:** `layoutname_viewid_attribute_value`

**Example:**

```json
{
  "activity_add_resource_toolbar_layout_height_56dp": {
    "layout": "activity_add_resource",
    "viewId": "toolbar",
    "attribute": "layout_height",
    "namespace": "android:",
    "value": "56dp",
    "dimenName": "activity_add_resource_toolbar_layout_height_56dp",
    "usages": [...]
  }
}
```

---

### 2️⃣ `2-generate-dimens.ps1` - Generate dimens.xml

Converts JSON library into properly formatted `dimens.xml` file.

**Usage:**

```powershell
# Standard output (alphabetical)
.\2-generate-dimens.ps1

# Group by layout
.\2-generate-dimens.ps1 -GroupByLayout

# Sort by numeric value
.\2-generate-dimens.ps1 -SortByValue
```

**Output:** `temp\generated_dimens.xml`

**Example Output:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ================================ -->
    <!-- Layout: activity_add_resource -->
    <!-- ================================ -->
    <dimen name="activity_add_resource_toolbar_elevation_4dp">4dp</dimen>
    <dimen name="activity_add_resource_card_corner_radius_8dp">8dp</dimen> <!-- Used 4 times -->
    ...
</resources>
```

---

### 3️⃣ `3-replace-with-dimens.ps1` - Replace Values

Replaces hardcoded dimension values with `@dimen` references across all layouts.

**Usage:**

```powershell
# Dry run (preview changes)
.\3-replace-with-dimens.ps1 -DryRun

# Apply changes (with verbose output)
.\3-replace-with-dimens.ps1 -Verbose

# Full replacement
.\3-replace-with-dimens.ps1
```

**Output:** Modified layout files + `temp\replacement_report.json`

**Before:**

```xml
<com.google.android.material.appbar.MaterialToolbar
    android:layout_height="56dp"
    android:elevation="4dp" />
```

**After:**

```xml
<com.google.android.material.appbar.MaterialToolbar
    android:layout_height="@dimen/activity_add_resource_toolbar_layout_height_56dp"
    android:elevation="@dimen/activity_add_resource_toolbar_elevation_4dp" />
```

---

### 🚀 `run-migration.ps1` - Master Script

Executes full migration pipeline with user confirmations.

**Usage:**

```powershell
.\run-migration.ps1
```

**Steps:**

1. ✅ Backup layouts
2. 🔍 Extract dimensions
3. 📝 Generate dimens.xml
4. ⚠️ **PAUSE** - Review and consolidate duplicates
5. 🔄 Replace values (DRY RUN first)
6. ✅ Apply changes

---

## Workflow

### Quick Start

```powershell
cd scripts\layout-dimen-migration
.\run-migration.ps1
```

### Manual Step-by-Step

```powershell
# 1. Backup
.\0-backup-layouts.ps1

# 2. Extract
.\1-extract-dimensions.ps1

# 3. Generate dimens.xml
.\2-generate-dimens.ps1 -GroupByLayout

# 4. Review temp\generated_dimens.xml
# 5. Consolidate duplicates manually

# 6. Test replacement (dry run)
.\3-replace-with-dimens.ps1 -DryRun -Verbose

# 7. Apply replacement
.\3-replace-with-dimens.ps1

# 8. Copy dimens.xml to resources
Copy-Item temp\generated_dimens.xml app_v2\src\main\res\values\dimens.xml

# 9. Build and test
.\scripts\builders\build-debug.PS1
```

---

## Post-Migration Tasks

### 1. Consolidate Duplicate Dimensions

Manually review `temp\generated_dimens.xml` and group common values:

**Before:**

```xml
<dimen name="activity_main_button_height_48dp">48dp</dimen>
<dimen name="activity_settings_icon_size_48dp">48dp</dimen>
<dimen name="fragment_gallery_thumb_size_48dp">48dp</dimen>
```

**After:**

```xml
<dimen name="button_height_standard">48dp</dimen>
<dimen name="icon_size_medium">48dp</dimen>
<dimen name="thumbnail_size_default">48dp</dimen>
```

### 2. Create Semantic Groups

Organize dimensions by purpose:

```xml
<!-- ==================== -->
<!-- Common Dimensions -->
<!-- ==================== -->
<dimen name="margin_tiny">2dp</dimen>
<dimen name="margin_small">8dp</dimen>
<dimen name="margin_medium">16dp</dimen>
<dimen name="margin_large">24dp</dimen>

<!-- ==================== -->
<!-- Text Sizes -->
<!-- ==================== -->
<dimen name="text_size_caption">12sp</dimen>
<dimen name="text_size_body">14sp</dimen>
<dimen name="text_size_title">18sp</dimen>
<dimen name="text_size_headline">24sp</dimen>

<!-- ==================== -->
<!-- Component Sizes -->
<!-- ==================== -->
<dimen name="toolbar_height">56dp</dimen>
<dimen name="button_height">48dp</dimen>
<dimen name="icon_size_small">24dp</dimen>
<dimen name="icon_size_medium">48dp</dimen>
```

### 3. Update Layout References

After consolidation, update layout files to use semantic names:

```powershell
# Find usages
grep -r "activity_main_button_height_48dp" app_v2\src\main\res\layout\

# Replace manually or with script
```

---

## Output Structure

```
temp/
├── layout_backups/
│   └── backup_20260212_143022/
│       ├── manifest.json
│       ├── activity_add_resource.xml
│       ├── activity_main.xml
│       └── ...
├── dimensions_library.json
├── dimensions_library_summary.json
├── generated_dimens.xml
└── replacement_report.json
```

---

## Technical Details

### Dimension Detection

- **Regex Pattern:** `(android:|app:)(attribute)="(value)(dp|sp)"`
- **Skipped Values:** `match_parent`, `wrap_content`, `0dp`, existing `@dimen/*`
- **Context Extraction:** Searches backwards for `android:id` to determine view ID

### Key Generation

- **Format:** `{layout}_{viewid}_{attribute}_{cleanvalue}`
- **Cleaning:** Non-alphanumeric characters replaced with `_`
- **Example:** `activity_player_btnNext_layout_width_48dp`

### Replacement Strategy

- **Pattern Matching:** Exact attribute + value pairs
- **Namespace Preservation:** Maintains `android:` vs `app:` prefixes
- **Safe Replacement:** Only replaces full attribute declarations

---

## Safety Features

- ✅ **Backup Creation:** All layouts backed up with timestamp
- ✅ **Dry Run Mode:** Preview changes before applying
- ✅ **Manifest Generation:** Track all processed files
- ✅ **Report Generation:** Detailed statistics and summaries
- ✅ **UTF-8 Encoding:** Proper handling of all characters
- ✅ **No Data Loss:** Original values preserved in JSON library

---

## Troubleshooting

### Issue: No dimensions extracted

**Solution:** Check if layout files use hardcoded values (not already `@dimen`)

### Issue: Script fails on library load

**Solution:** Run scripts in order: 0 → 1 → 2 → 3

### Issue: Build errors after replacement

**Solution:**

1. Check `temp\replacement_report.json` for issues
2. Restore from `temp\layout_backups\`
3. Ensure `dimens.xml` copied to `app_v2\src\main\res\values\`

### Issue: Duplicate dimen names

**Solution:** Expected behavior - consolidate manually in step 4

---

## Future Enhancements

- [ ] Automatic duplicate consolidation AI
- [ ] Semantic name suggestions based on value/usage
- [ ] Integration with existing `dimens.xml`
- [ ] Support for `colors.xml`, `strings.xml`
- [ ] Git commit integration
- [ ] Reverse migration (dimen → hardcoded)

---

## Credits

**Project:** FastMediaSorter v2  
**Scripts:** PowerShell-based automation  
**Output:** `temp/` (gitignored)

---

## License

Internal development tools - FastMediaSorter project use only.
