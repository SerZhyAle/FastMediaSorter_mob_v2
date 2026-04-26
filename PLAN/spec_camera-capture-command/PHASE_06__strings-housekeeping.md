# Phase 06 — Strings & Housekeeping

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-04-25
**Completed:** 2026-04-25
**Depends on:** Phase 04, Phase 05
**Blocks:** —

---

## Objective

Add trilingual strings (EN/RU/UK) for all new UI text, update `docs/FEATURES.md` and mirrors,
run catalog sync and dev changelog.

---

## Files Touched

| File | New/Mod | Budget |
| ---- | :-----: | -----: |
| `app_v2/src/main/res/values/strings.xml` | Mod | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Mod | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Mod | — |
| `docs/FEATURES.md` | Mod | — |
| `docs/FEATURES_RU.md` | Mod | — |
| `docs/FEATURES_UK.md` | Mod | — |

---

## Steps

### Step 06.1 — English strings

**File:** `app_v2/src/main/res/values/strings.xml`

```xml
<!-- Camera Capture Command -->
<string name="cmd_camera_capture">Capture</string>
<string name="camera_capture_filename_title">File name</string>
<string name="camera_capture_saved">Saved: %1$s</string>
<string name="camera_capture_error_temp_file">Could not prepare capture file</string>
<string name="camera_capture_error_save_generic">Error saving captured file</string>
<!-- Camera Capture Settings -->
<string name="setting_disable_camera_capture">Disable camera capture button</string>
<string name="setting_disable_camera_capture_summary">Hides the camera capture command in Browse</string>
<string name="setting_skip_camera_filename_dialog">Don\'t ask for filename</string>
<string name="setting_skip_camera_filename_dialog_summary">Use timestamp filename without prompting after capture</string>
```

**Verification:** `Grep "cmd_camera_capture" app_v2/src/main/res/values/strings.xml` → 1 hit.

---

### Step 06.2 — Russian strings

**File:** `app_v2/src/main/res/values-ru/strings.xml`

```xml
<!-- Camera Capture Command -->
<string name="cmd_camera_capture">Снять</string>
<string name="camera_capture_filename_title">Имя файла</string>
<string name="camera_capture_saved">Сохранено: %1$s</string>
<string name="camera_capture_error_temp_file">Не удалось подготовить файл для съёмки</string>
<string name="camera_capture_error_save_generic">Ошибка сохранения снятого файла</string>
<!-- Camera Capture Settings -->
<string name="setting_disable_camera_capture">Отключить вызов фотоаппарата</string>
<string name="setting_disable_camera_capture_summary">Скрывает команду съёмки в Browse</string>
<string name="setting_skip_camera_filename_dialog">Не спрашивать имя файла</string>
<string name="setting_skip_camera_filename_dialog_summary">Использовать имя по времени съёмки без диалога</string>
```

**Verification:** `Grep "cmd_camera_capture" app_v2/src/main/res/values-ru/strings.xml` → 1 hit.

---

### Step 06.3 — Ukrainian strings

**File:** `app_v2/src/main/res/values-uk/strings.xml`

```xml
<!-- Camera Capture Command -->
<string name="cmd_camera_capture">Зняти</string>
<string name="camera_capture_filename_title">Ім\'я файлу</string>
<string name="camera_capture_saved">Збережено: %1$s</string>
<string name="camera_capture_error_temp_file">Не вдалося підготувати файл для зйомки</string>
<string name="camera_capture_error_save_generic">Помилка збереження знятого файлу</string>
<!-- Camera Capture Settings -->
<string name="setting_disable_camera_capture">Вимкнути кнопку зйомки</string>
<string name="setting_disable_camera_capture_summary">Приховує команду зйомки у Browse</string>
<string name="setting_skip_camera_filename_dialog">Не питати ім\'я файлу</string>
<string name="setting_skip_camera_filename_dialog_summary">Використовувати ім\'я за часом зйомки без діалогу</string>
```

**Verification:** `Grep "cmd_camera_capture" app_v2/src/main/res/values-uk/strings.xml` → 1 hit.

---

### Step 06.4 — Update FEATURES.md (EN/RU/UK)

**File:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 04

In section **3. File Operations**, add:

**EN (`docs/FEATURES.md`):**

```markdown
- **Camera capture**: Tap the camera icon in the Browse command bar to take a new photo or
  video with the device's default camera app. The captured file is saved directly to the
  current resource root (local, SMB, SFTP, FTP, or Cloud) or to the standard DCIM/Camera
  folder when browsing the "All Videos", "All Photos", or "Camera Photos" virtual collections.
  After capture a filename dialog lets you rename the file before saving; this dialog can be
  skipped via Settings → Behaviour → "Don't ask for filename". The entire command can be
  hidden globally via Settings → Behaviour → "Disable camera capture button". After saving
  the file list refreshes automatically and scrolls to the new entry.
```

**RU (`docs/FEATURES_RU.md`):**

```markdown
- **Съёмка с камеры**: Нажмите иконку камеры в командной панели Browse, чтобы сделать
  новое фото или видео стандартным приложением камеры. Снятый файл сохраняется в корневую
  папку текущего ресурса (локального, SMB, SFTP, FTP или облака) или в стандартную папку
  DCIM/Camera для виртуальных коллекций «Все видео», «Все фото» и «Фото камеры».
  После съёмки диалог позволяет переименовать файл; его можно отключить в Настройках →
  Поведение → «Не спрашивать имя файла». Всю команду можно скрыть через Настройки →
  Поведение → «Отключить вызов фотоаппарата». После сохранения список файлов обновляется
  и прокручивается к новому элементу.
```

**UK (`docs/FEATURES_UK.md`):**

```markdown
- **Зйомка з камери**: Натисніть іконку камери в командній панелі Browse, щоб зробити нове
  фото або відео стандартним додатком камери. Знятий файл зберігається до кореневої теки
  поточного ресурсу (локального, SMB, SFTP, FTP або хмари) або до стандартної теки
  DCIM/Camera для віртуальних колекцій «Всі відео», «Всі фото» і «Фото камери».
  Після зйомки діалог дозволяє перейменувати файл; його можна вимкнути у Налаштуваннях →
  Поведінка → «Не питати ім'я файлу». Всю команду можна приховати через Налаштування →
  Поведінка → «Вимкнути кнопку зйомки». Після збереження список файлів оновлюється та
  прокручується до нового елементу.
```

**Verification:** `Grep "Camera capture" docs/FEATURES.md` → 1 hit.

---

### Step 06.5 — Dev changelog

**Depends on:** all above steps complete

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt" "camera-capture-command" "Add BrowseCameraCaptureManager: camera capture button in Browse, saves to resource root or DCIM, filename dialog, 2 settings"
```

**Verification:** `Grep "BrowseCameraCaptureManager" dev/CHANGELOG.md` → 1 hit.

---

### Step 06.6 — Catalog sync

**Depends on:** Phase 04

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
```

Set role for `BrowseCameraCaptureManager`:

```text
role: Manages camera-capture lifecycle in BrowseActivity — temp file creation, camera intent, filename dialog, destination routing (local/network/cloud), post-save list refresh
status: active
```

**Verification:** `Grep "BrowseCameraCaptureManager" dev/CATALOG/app_v2.md` → 1 hit.

---

## Phase Done Criteria

- [x] `Grep "cmd_camera_capture" app_v2/src/main/res/values/strings.xml` → 1 hit
- [x] `Grep "cmd_camera_capture" app_v2/src/main/res/values-ru/strings.xml` → 1 hit
- [x] `Grep "cmd_camera_capture" app_v2/src/main/res/values-uk/strings.xml` → 1 hit
- [x] `Grep "Camera capture" docs/FEATURES.md` → 1 hit
- [x] `Grep "BrowseCameraCaptureManager" dev/CHANGELOG.md` → 1 hit (2 entries: Phase 03 + Phase 06)
- [x] `Grep "BrowseCameraCaptureManager" dev/CATALOG/app_v2.md` → 1 hit

**Phase Step Log:**

- 2026-04-25 — Steps 06.1-06.6 done. Strings: settings keys added to all 3 locales (base strings were already present from Phase 01). FEATURES.md: camera capture bullet added to Section 3 in EN/RU/UK. Changelog entry recorded. Catalog: BrowseCameraCaptureManager role=camera-capture-lifecycle, status=new. OOS-INLINE: added setting_disable/skip strings to RU+UK (not in spec, but required for trilingual consistency). All Phase Done Criteria PASS.
