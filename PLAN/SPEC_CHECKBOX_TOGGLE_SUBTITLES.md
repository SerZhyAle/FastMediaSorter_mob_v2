# SPEC: Checkbox & Boolean Toggle Subtitle Descriptions

## Goal

Apply the same visual design used for Settings switches to all other boolean (yes/no) toggle
elements across the app — checkboxes and binary radio buttons.

**Reference implementation** (already done): `fragment_settings_audio.xml` → `switchSaveAudioMetadataLocally`

---

## Visual Pattern

### For checkboxes with **inline text** (`android:text` on the CheckBox itself)

**Current:**
```xml
<com.google.android.material.checkbox.MaterialCheckBox
    android:id="@+id/cbXxx"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/main_label" />
```

**Target:**
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <com.google.android.material.checkbox.MaterialCheckBox
        android:id="@+id/cbXxx"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/main_label" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/checkbox_subtitle_margin_start"
        android:text="@string/cb_xxx_desc"
        android:textSize="@dimen/text_size_small"
        android:textColor="@color/text_color_secondary" />
</LinearLayout>
```

> `checkbox_subtitle_margin_start` = ~48dp (to align subtitle under the label text, past the checkbox thumb)

### For checkboxes with **separate label TextView** (horizontal row pattern)

**Current:**
```xml
<LinearLayout android:orientation="horizontal" android:gravity="center_vertical">
    <com.google.android.material.checkbox.MaterialCheckBox android:id="@+id/cbXxx" ... />
    <TextView android:layout_width="0dp" android:layout_weight="1"
        android:text="@string/main_label" ... />
</LinearLayout>
```

**Target:**
```xml
<LinearLayout android:orientation="horizontal" android:gravity="center_vertical">
    <com.google.android.material.checkbox.MaterialCheckBox android:id="@+id/cbXxx" ... />
    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:orientation="vertical">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/main_label"
            android:textSize="@dimen/resource_card_desc_text_size" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/cb_xxx_desc"
            android:textSize="@dimen/text_size_small"
            android:textColor="@color/text_color_secondary" />
    </LinearLayout>
</LinearLayout>
```

---

## Scope — Files and Elements to Update

### 1. `layout/activity_add_resource.xml`

Pattern type: **inline text** on CheckBox

| Element ID | Label text | String key for desc |
|---|---|---|
| `cbLocalScanSubdirectories` | Scan subdirectories | `cb_local_scan_subdirectories_desc` |
| `cbLocalAddToDestinations` | Add to destinations | `cb_local_add_to_destinations_desc` |
| `cbLocalReadOnlyMode` | Read only mode | `cb_local_read_only_mode_desc` |
| `cbSmbAddToDestinations` | Register as destination | `cb_smb_add_to_destinations_desc` |
| `cbSmbReadOnlyMode` | Read only mode | `cb_smb_read_only_mode_desc` |
| `cbSmbScanSubdirectories` | Scan subdirectories | `cb_smb_scan_subdirectories_desc` |
| `cbSmbAllFiles` | All files | `cb_smb_all_files_desc` |
| `cbSmbDisableThumbnails` | Disable thumbnails | `cb_smb_disable_thumbnails_desc` |
| `cbSmbRememberFileList` | Remember file list | `cb_smb_remember_file_list_desc` |
| `cbSftpAddToDestinations` | Register as destination | `cb_sftp_add_to_destinations_desc` |
| `cbSftpReadOnlyMode` | Read only mode | `cb_sftp_read_only_mode_desc` |
| `cbSftpScanSubdirectories` | Scan subdirectories | `cb_sftp_scan_subdirectories_desc` |
| `cbSftpAllFiles` | All files | `cb_sftp_all_files_desc` |
| `cbSftpDisableThumbnails` | Disable thumbnails | `cb_sftp_disable_thumbnails_desc` |
| `cbSftpRememberFileList` | Remember file list | `cb_sftp_remember_file_list_desc` |
| `cbCloudReadOnlyMode` | Read only mode | `cb_cloud_read_only_mode_desc` |

**Excluded from this file** (multi-select type selectors, not yes/no):
- `cbSmb/SftpSupportImage/Video/Audio/Gif/Text/Pdf/Epub` — 14 media type tiles

### 2. `layout/dialog_player_settings.xml`

Pattern type: **separate label TextView** (horizontal layout already in place)

| Element ID | Label text | String key for desc |
|---|---|---|
| `cbRepeatVideo` | Repeat video | `cb_repeat_video_desc` |
| `cbShowSubtitles` | Show subtitles | `cb_show_subtitles_desc` |

### 3. `layout/dialog_slideshow_settings.xml`

Pattern type: **inline text** on CheckBox (ConstraintLayout parent)

| Element ID | Label text | String key for desc |
|---|---|---|
| `cbPlayToEnd` | Play video/audio to end | `cb_play_to_end_desc` |

### 4. `layout/activity_dropbox_folder_picker.xml`

Pattern type: **inline text**, horizontal row of two checkboxes

| Element ID | Label text | String key for desc |
|---|---|---|
| `cbAddAsDestination` | Add as destination | `cb_cloud_picker_add_as_destination_desc` |
| `cbScanSubdirectories` | Scan subdirectories | `cb_cloud_picker_scan_subdirectories_desc` |

### 5. `layout/activity_google_drive_folder_picker.xml`

Same two elements as Dropbox — reuse same string keys:
- `cb_cloud_picker_add_as_destination_desc`
- `cb_cloud_picker_scan_subdirectories_desc`

### 6. `layout/activity_onedrive_folder_picker.xml`

Same two elements as Dropbox — reuse same string keys.

---

## String Resources — All Three Languages

### EN (`values/strings.xml`)

```xml
<!-- Checkbox subtitle descriptions -->
<string name="cb_local_scan_subdirectories_desc">Recursively includes all nested folders when scanning for media</string>
<string name="cb_local_add_to_destinations_desc">Makes this folder available as a copy/move target in the player</string>
<string name="cb_local_read_only_mode_desc">Files cannot be moved, copied, or deleted from this resource</string>
<string name="cb_smb_add_to_destinations_desc">Makes this network share available as a copy/move target in the player</string>
<string name="cb_smb_read_only_mode_desc">Files cannot be moved, copied, or deleted from this network share</string>
<string name="cb_smb_scan_subdirectories_desc">Recursively includes all nested folders when scanning for media</string>
<string name="cb_smb_all_files_desc">Shows all file types regardless of the configured media filters</string>
<string name="cb_smb_disable_thumbnails_desc">Skips thumbnail generation to reduce network traffic and speed up loading</string>
<string name="cb_smb_remember_file_list_desc">Reopens the resource at the last browsed file position</string>
<string name="cb_sftp_add_to_destinations_desc">Makes this SFTP/FTP folder available as a copy/move target in the player</string>
<string name="cb_sftp_read_only_mode_desc">Files cannot be moved, copied, or deleted from this server</string>
<string name="cb_sftp_scan_subdirectories_desc">Recursively includes all nested folders when scanning for media</string>
<string name="cb_sftp_all_files_desc">Shows all file types regardless of the configured media filters</string>
<string name="cb_sftp_disable_thumbnails_desc">Skips thumbnail generation to reduce network traffic and speed up loading</string>
<string name="cb_sftp_remember_file_list_desc">Reopens the resource at the last browsed file position</string>
<string name="cb_cloud_read_only_mode_desc">Files cannot be moved, copied, or deleted from this cloud storage</string>
<string name="cb_cloud_picker_add_as_destination_desc">Makes this cloud folder available as a copy/move target in the player</string>
<string name="cb_cloud_picker_scan_subdirectories_desc">Recursively includes nested cloud folders when scanning for media</string>
<string name="cb_repeat_video_desc">Loops the current video continuously instead of advancing to next</string>
<string name="cb_show_subtitles_desc">Displays subtitle track when available in the video file</string>
<string name="cb_play_to_end_desc">Waits for each video or audio to finish before advancing in slideshow</string>
```

### RU (`values-ru/strings.xml`)

```xml
<!-- Checkbox subtitle descriptions -->
<string name="cb_local_scan_subdirectories_desc">Рекурсивно включает все вложенные папки при сканировании медиа</string>
<string name="cb_local_add_to_destinations_desc">Делает эту папку доступной как цель копирования/перемещения в плеере</string>
<string name="cb_local_read_only_mode_desc">Файлы нельзя перемещать, копировать или удалять из этого ресурса</string>
<string name="cb_smb_add_to_destinations_desc">Делает эту сетевую папку доступной как цель копирования/перемещения в плеере</string>
<string name="cb_smb_read_only_mode_desc">Файлы нельзя перемещать, копировать или удалять из этой сетевой папки</string>
<string name="cb_smb_scan_subdirectories_desc">Рекурсивно включает все вложенные папки при сканировании медиа</string>
<string name="cb_smb_all_files_desc">Показывает все типы файлов независимо от настроенных медиафильтров</string>
<string name="cb_smb_disable_thumbnails_desc">Отключает генерацию миниатюр для снижения сетевого трафика и ускорения загрузки</string>
<string name="cb_smb_remember_file_list_desc">Открывает ресурс на последней просмотренной позиции файла</string>
<string name="cb_sftp_add_to_destinations_desc">Делает эту папку SFTP/FTP доступной как цель копирования/перемещения в плеере</string>
<string name="cb_sftp_read_only_mode_desc">Файлы нельзя перемещать, копировать или удалять с этого сервера</string>
<string name="cb_sftp_scan_subdirectories_desc">Рекурсивно включает все вложенные папки при сканировании медиа</string>
<string name="cb_sftp_all_files_desc">Показывает все типы файлов независимо от настроенных медиафильтров</string>
<string name="cb_sftp_disable_thumbnails_desc">Отключает генерацию миниатюр для снижения сетевого трафика и ускорения загрузки</string>
<string name="cb_sftp_remember_file_list_desc">Открывает ресурс на последней просмотренной позиции файла</string>
<string name="cb_cloud_read_only_mode_desc">Файлы нельзя перемещать, копировать или удалять из этого облачного хранилища</string>
<string name="cb_cloud_picker_add_as_destination_desc">Делает эту облачную папку доступной как цель копирования/перемещения в плеере</string>
<string name="cb_cloud_picker_scan_subdirectories_desc">Рекурсивно включает вложенные облачные папки при сканировании медиа</string>
<string name="cb_repeat_video_desc">Зацикливает текущее видео вместо перехода к следующему</string>
<string name="cb_show_subtitles_desc">Отображает дорожку субтитров при её наличии в видеофайле</string>
<string name="cb_play_to_end_desc">Ожидает окончания каждого видео или аудио перед переходом в слайдшоу</string>
```

### UK (`values-uk/strings.xml`)

```xml
<!-- Checkbox subtitle descriptions -->
<string name="cb_local_scan_subdirectories_desc">Рекурсивно включає всі вкладені папки під час сканування медіа</string>
<string name="cb_local_add_to_destinations_desc">Робить цю папку доступною як ціль копіювання/переміщення у плеєрі</string>
<string name="cb_local_read_only_mode_desc">Файли не можна переміщати, копіювати або видаляти з цього ресурсу</string>
<string name="cb_smb_add_to_destinations_desc">Робить цю мережеву папку доступною як ціль копіювання/переміщення у плеєрі</string>
<string name="cb_smb_read_only_mode_desc">Файли не можна переміщати, копіювати або видаляти з цієї мережевої папки</string>
<string name="cb_smb_scan_subdirectories_desc">Рекурсивно включає всі вкладені папки під час сканування медіа</string>
<string name="cb_smb_all_files_desc">Показує всі типи файлів незалежно від налаштованих медіафільтрів</string>
<string name="cb_smb_disable_thumbnails_desc">Вимикає генерацію мініатюр для зменшення мережевого трафіку та прискорення завантаження</string>
<string name="cb_smb_remember_file_list_desc">Відкриває ресурс на останній переглянутій позиції файлу</string>
<string name="cb_sftp_add_to_destinations_desc">Робить цю папку SFTP/FTP доступною як ціль копіювання/переміщення у плеєрі</string>
<string name="cb_sftp_read_only_mode_desc">Файли не можна переміщати, копіювати або видаляти з цього сервера</string>
<string name="cb_sftp_scan_subdirectories_desc">Рекурсивно включає всі вкладені папки під час сканування медіа</string>
<string name="cb_sftp_all_files_desc">Показує всі типи файлів незалежно від налаштованих медіафільтрів</string>
<string name="cb_sftp_disable_thumbnails_desc">Вимикає генерацію мініатюр для зменшення мережевого трафіку та прискорення завантаження</string>
<string name="cb_sftp_remember_file_list_desc">Відкриває ресурс на останній переглянутій позиції файлу</string>
<string name="cb_cloud_read_only_mode_desc">Файли не можна переміщати, копіювати або видаляти з цього хмарного сховища</string>
<string name="cb_cloud_picker_add_as_destination_desc">Робить цю хмарну папку доступною як ціль копіювання/переміщення у плеєрі</string>
<string name="cb_cloud_picker_scan_subdirectories_desc">Рекурсивно включає вкладені хмарні папки під час сканування медіа</string>
<string name="cb_repeat_video_desc">Зациклює поточне відео замість переходу до наступного</string>
<string name="cb_show_subtitles_desc">Відображає доріжку субтитрів за наявності у відеофайлі</string>
<string name="cb_play_to_end_desc">Очікує завершення кожного відео або аудіо перед переходом у слайдшоу</string>
```

---

## New Dimension Needed

Add to `dimens.xml`:

```xml
<!-- Indent for subtitle text under a checkbox (checkbox thumb width + padding) -->
<dimen name="checkbox_subtitle_margin_start">48dp</dimen>
```

---

## Elements Excluded (Not Boolean Yes/No)

| Element | Reason |
|---|---|
| `cbSmbSupportImage/Video/Audio/Gif/Text/Pdf/Epub` | Multi-select media type tile grid |
| `cbSftpSupportImage/Video/Audio/Gif/Text/Pdf/Epub` | Multi-select media type tile grid |
| `cbFilterImage/Video/Audio/Gif/Text/Pdf/Epub` | Filter type selector chips |
| `cbVideo/Audio/Image/Gif` in resource editor | Compact type selector row |
| `cbSelect` in `item_media_file.xml` | List item selection, not a setting |

---

## Implementation Steps

1. **Add `checkbox_subtitle_margin_start` dimen** to `dimens.xml`
2. **Add all 21 string keys** to `values/strings.xml` (EN)
3. **Add all 21 string keys** to `values-ru/strings.xml` (RU)
4. **Add all 21 string keys** to `values-uk/strings.xml` (UK)
5. **Update `activity_add_resource.xml`** — 16 checkboxes (inline text → vertical wrapper pattern)
6. **Update `dialog_player_settings.xml`** — 2 checkboxes (separate TextView → wrap label in vertical layout)
7. **Update `dialog_slideshow_settings.xml`** — 1 checkbox (inline text → vertical wrapper)
8. **Update `activity_dropbox_folder_picker.xml`** — 2 checkboxes
9. **Update `activity_google_drive_folder_picker.xml`** — 2 checkboxes
10. **Update `activity_onedrive_folder_picker.xml`** — 2 checkboxes
11. **Run dev log script** for all modified files

---

## Total Scope

| Category | Count |
|---|---|
| Boolean checkboxes to update | 21 unique elements across 6 files |
| New string keys per language | 21 |
| Total new string entries | 63 (21 × 3 languages) |
| New dimen entry | 1 |
| Layout files to modify | 6 |
