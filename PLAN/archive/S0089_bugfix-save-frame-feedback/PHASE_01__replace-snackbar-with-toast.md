# PHASE 01 — Replace Snackbar with Toast in SaveVideoFrameManager

**Ticket:** S0089
**Phase:** 01 / 02
**Status:** ✅ Done

**Step Log:**

- 2026-05-05 — Verification PASS: Snackbar import absent (0 matches), showToast count = 6. Files: SaveVideoFrameManager.kt. Dev log recorded.

---

## Context

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt`
Lines: 284. No backup required (<500).

Current behaviour: `showSnackbar()` at line 281 anchors to `activity.activityBinding.root`.
In a full-screen edge-to-edge player this is rendered off-screen or behind the video surface.
Fix: replace with `Toast.makeText()` which renders in a system-level overlay, immune to view hierarchy and insets.

Five call sites for `showSnackbar()` to migrate:
- Line 60: error — `save_frame_no_video` (bitmap null before coroutine)
- Line 101: success — `finalMessage` (resource name or Downloads)
- Line 106: OOM error inside coroutine
- Line 109: generic error inside coroutine — `save_frame_error`
- Line 133: OOM error from `getBitmap()` failure inside `captureFrame()`

Duration convention (matches VR counterpart `VrStereoSnapshotManager`):
- Success → `Toast.LENGTH_SHORT`
- Error / OOM → `Toast.LENGTH_LONG`

---

## Steps

### Step 1.1 — Replace `showSnackbar()` with `showToast()` in `SaveVideoFrameManager.kt`

**Prompt:**

Open `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt`.

**1. Remove the Snackbar import (line 13):**

```kotlin
// DELETE this line:
import com.google.android.material.snackbar.Snackbar
```

**2. Add Toast import after `import android.view.ViewGroup`:**

```kotlin
import android.widget.Toast
```

**3. Update the KDoc on the class (line ~41). Change:**

```
 * 5. Shows a Snackbar with the actual save location or an error.
```

to:

```
 * 5. Shows a Toast with the actual save location or an error.
```

**4. Update all five `showSnackbar(...)` call sites with correct Toast duration:**

| Original call | Replacement |
|---|---|
| `showSnackbar(activity.getString(R.string.save_frame_no_video))` | `showToast(activity.getString(R.string.save_frame_no_video), Toast.LENGTH_LONG)` |
| `showSnackbar(finalMessage)` | `showToast(finalMessage)` |
| `showSnackbar("Not enough memory to capture frame")` *(in coroutine catch)* | `showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)` |
| `showSnackbar(activity.getString(R.string.save_frame_error))` | `showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)` |
| `showSnackbar("Not enough memory to capture frame")` *(in captureFrame catch)* | `showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)` |

Note: both hardcoded OOM strings map to `R.string.save_frame_error` — no new string resource needed.

**5. Replace the `showSnackbar()` method body at the bottom of the file:**

Delete:
```kotlin
    private fun showSnackbar(message: String) {
        Snackbar.make(activity.activityBinding.root, message, Snackbar.LENGTH_LONG).show()
    }
```

Replace with:
```kotlin
    // Toast renders in a system overlay, immune to view hierarchy and edge-to-edge insets —
    // critical for full-screen video player where Snackbar anchor is off-screen.
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(activity, message, duration).show()
    }
```

**Verification:**

```powershell
# No Snackbar reference must remain in the file
Select-String -Path "app_v2\src\main\java\com\sza\fastmediasorter\ui\player\helpers\SaveVideoFrameManager.kt" -Pattern "Snackbar"
# Expected: 0 matches

# showToast must appear 6 times (1 definition + 5 call sites)
(Select-String -Path "app_v2\src\main\java\com\sza\fastmediasorter\ui\player\helpers\SaveVideoFrameManager.kt" -Pattern "showToast").Count
# Expected: 6
```

---

### Step 1.2 — Run lint check

**Prompt:**

```powershell
.\gradlew.bat lintStandardDebug 2>&1 | Select-String -Pattern "SaveVideoFrameManager|Snackbar|UnusedImport"
```

Expected: no matches (zero lint warnings for this file).

**Verification:** Exit code 0 and no `SaveVideoFrameManager`-related lint warnings in output.

---

### Step 1.3 — Dev log entry for source file change

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt" "SaveVideoFrameManager" "S0089: replace Snackbar with Toast for full-screen player visibility"
```

---

## Progress Tracker

- [x] Step 1.1 — Replace showSnackbar with showToast
- [ ] Step 1.2 — Lint check (BUILD-REQUIRED — run `/build` then `lintStandardDebug`)
- [x] Step 1.3 — Dev log
